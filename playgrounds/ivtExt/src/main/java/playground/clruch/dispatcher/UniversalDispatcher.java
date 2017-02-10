package playground.clruch.dispatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;
import org.matsim.contrib.dvrp.schedule.AbstractTask;
import org.matsim.contrib.dvrp.schedule.DriveTask;
import org.matsim.contrib.dvrp.schedule.Schedule;
import org.matsim.contrib.dvrp.schedule.Schedules;
import org.matsim.contrib.dvrp.schedule.Task;
import org.matsim.contrib.dvrp.tracker.OnlineDriveTaskTracker;
import org.matsim.contrib.dvrp.tracker.TaskTracker;
import org.matsim.contrib.dvrp.util.LinkTimePair;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.router.util.TravelTime;

import playground.clruch.router.SimpleBlockingRouter;
import playground.clruch.utils.VrpPathUtils;
import playground.sebhoerl.avtaxi.config.AVDispatcherConfig;
import playground.sebhoerl.avtaxi.config.AVTimingParameters;
import playground.sebhoerl.avtaxi.data.AVVehicle;
import playground.sebhoerl.avtaxi.dispatcher.AVDispatcher;
import playground.sebhoerl.avtaxi.dispatcher.AVVehicleAssignmentEvent;
import playground.sebhoerl.avtaxi.dispatcher.AbstractDispatcher;
import playground.sebhoerl.avtaxi.passenger.AVRequest;
import playground.sebhoerl.avtaxi.schedule.AVDriveTask;
import playground.sebhoerl.avtaxi.schedule.AVDropoffTask;
import playground.sebhoerl.avtaxi.schedule.AVPickupTask;
import playground.sebhoerl.avtaxi.schedule.AVStayTask;
import playground.sebhoerl.avtaxi.schedule.AVTask;
import playground.sebhoerl.plcpc.ParallelLeastCostPathCalculator;

/**
 * alternative to {@link AbstractDispatcher}
 */
public abstract class UniversalDispatcher implements AVDispatcher {
    protected final AVDispatcherConfig avDispatcherConfig;
    protected final TravelTime travelTime;
    protected final ParallelLeastCostPathCalculator parallelLeastCostPathCalculator;
    protected final EventsManager eventsManager;

    private final List<AVVehicle> vehicles = new ArrayList<>(); // access via function getFunctioningVehicles()
    private final Set<AVRequest> pendingRequests = new HashSet<>(); // access via getAVRequests()
    private final Set<AVRequest> matchedRequests = new HashSet<>();

    public UniversalDispatcher( //
            AVDispatcherConfig avDispatcherConfig, //
            TravelTime travelTime, //
            ParallelLeastCostPathCalculator parallelLeastCostPathCalculator, //
            EventsManager eventsManager //
    ) {
        this.avDispatcherConfig = avDispatcherConfig;
        this.travelTime = travelTime;
        this.parallelLeastCostPathCalculator = parallelLeastCostPathCalculator;
        this.eventsManager = eventsManager;
    }

    private double private_now = -1;

    protected final Collection<AVVehicle> getFunctioningVehicles() {
        if (vehicles.isEmpty() || !vehicles.get(0).getSchedule().getStatus().equals(Schedule.ScheduleStatus.STARTED))
            return Collections.emptyList();
        return Collections.unmodifiableList(vehicles);
    }

    /**
     * function call leaves the state of the {@link UniversalDispatcher} unchanged.
     * successive calls to the function return the identical collection.
     * 
     * @return collection of all requests that have not been matched
     */
    protected final Collection<AVRequest> getAVRequests() {
        pendingRequests.removeAll(matchedRequests);
        matchedRequests.clear();
        return Collections.unmodifiableCollection(pendingRequests);
    }

    /**
     * function call leaves the state of the {@link UniversalDispatcher} unchanged.
     * successive calls to the function return the identical collection.
     * 
     * @return collection of all vehicles that currently are in the last task, which is of type STAY
     */
    protected final Map<Link, Queue<AVVehicle>> getStayVehicles() {
        Map<Link, Queue<AVVehicle>> map = new HashMap<>();
        for (AVVehicle avVehicle : getFunctioningVehicles()) {
            Schedule<AbstractTask> schedule = (Schedule<AbstractTask>) avVehicle.getSchedule();
            AbstractTask abstractTask = Schedules.getLastTask(schedule); // <- last task
            if (abstractTask.getStatus().equals(Task.TaskStatus.STARTED)) // <- task is STARTED
                new AVTaskAdapter(abstractTask) {
                    public void handle(AVStayTask avStayTask) { // <- type of task is STAY
                        final Link link = avStayTask.getLink();
                        if (!map.containsKey(link))
                            map.put(link, new LinkedList<>());
                        map.get(link).add(avVehicle); // <- append vehicle to list of vehicles at link
                    }
                };
        }
        return Collections.unmodifiableMap(map);
    }

    public final String getStatusString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("#requests " + getAVRequests().size());
        stringBuilder.append(", #stay " + getStayVehicles().size());
        stringBuilder.append(", #divert " + getDivertableVehicles().size());
        return stringBuilder.toString();
    }

    protected final void setAcceptRequest(AVVehicle avVehicle, AVRequest avRequest) {
        matchedRequests.add(avRequest);

        // System.out.println(private_now + " @ " + avVehicle.getId() + " picksup " + avRequest.getPassenger().getId());
        AVTimingParameters timing = avDispatcherConfig.getParent().getTimingParameters();
        Schedule<AbstractTask> schedule = (Schedule<AbstractTask>) avVehicle.getSchedule();

        AVStayTask stayTask = (AVStayTask) Schedules.getLastTask(schedule);
        final double scheduleEndTime = schedule.getEndTime();
        stayTask.setEndTime(private_now);

        AVPickupTask pickupTask = new AVPickupTask(private_now, private_now + timing.getPickupDurationPerStop(), avRequest.getFromLink(), Arrays.asList(avRequest));
        schedule.addTask(pickupTask);

        SimpleBlockingRouter simpleBlockingRouter = new SimpleBlockingRouter(parallelLeastCostPathCalculator, travelTime);
        VrpPathWithTravelData dropoffPath = simpleBlockingRouter.getRoute(avRequest.getFromLink(), avRequest.getToLink(), pickupTask.getEndTime());
        AVDriveTask dropoffDriveTask = new AVDriveTask(dropoffPath, Arrays.asList(avRequest));
        schedule.addTask(dropoffDriveTask);

        AVDropoffTask dropoffTask = new AVDropoffTask( //
                dropoffPath.getArrivalTime(), dropoffPath.getArrivalTime() + timing.getDropoffDurationPerStop(), avRequest.getToLink(), Arrays.asList(avRequest));
        schedule.addTask(dropoffTask);

        // jan: following computation is mandatory for the internal scoring function
        final double distance = VrpPathUtils.getDistance(dropoffPath);
        avRequest.getRoute().setDistance(distance);

        if (dropoffTask.getEndTime() < scheduleEndTime)
            schedule.addTask(new AVStayTask(dropoffTask.getEndTime(), scheduleEndTime, dropoffTask.getLink()));
    }

    protected final Collection<VehicleLinkPair> getDivertableVehicles() {
        Collection<VehicleLinkPair> collection = new LinkedList<>();
        for (AVVehicle avVehicle : getFunctioningVehicles()) {
            Schedule<AbstractTask> schedule = (Schedule<AbstractTask>) avVehicle.getSchedule();
            AbstractTask abstractTask = schedule.getCurrentTask();
            new AVTaskAdapter(abstractTask) {
                @Override
                public void handle(AVDriveTask avDriveTask) {
                    // for empty cars the drive task is second to last task
                    if (Schedules.isNextToLastTask(abstractTask)) {
                        TaskTracker taskTracker = avDriveTask.getTaskTracker();
                        OnlineDriveTaskTracker onlineDriveTaskTracker = (OnlineDriveTaskTracker) taskTracker;
                        collection.add(new VehicleLinkPair(avVehicle, onlineDriveTaskTracker.getDiversionPoint()));
                    }
                }

                @Override
                public void handle(AVStayTask avStayTask) {
                    // for empty vehicles the current task has to be the last task
                    if (Schedules.isLastTask(abstractTask))
                        if (avStayTask.getBeginTime() + 5 < private_now) { // TODO magic const
                            LinkTimePair linkTimePair = new LinkTimePair(avStayTask.getLink(), private_now);
                            collection.add(new VehicleLinkPair(avVehicle, linkTimePair));
                        }
                }
            };
        }
        return collection;
    }

    protected final void setVehicleDiversion(final VehicleLinkPair vehicleLinkPair, final Link dest) {
        final Schedule<AbstractTask> schedule = (Schedule<AbstractTask>) vehicleLinkPair.avVehicle.getSchedule();
        AbstractTask abstractTask = schedule.getCurrentTask();
        new AVTaskAdapter(abstractTask) {
            @Override
            public void handle(AVDriveTask avDriveTask) {
                if (!avDriveTask.getPath().getToLink().equals(dest)) {
                    System.out.println("REROUTING [" + vehicleLinkPair.avVehicle.getId() + "]");
                    TaskTracker taskTracker = avDriveTask.getTaskTracker();
                    OnlineDriveTaskTracker onlineDriveTaskTracker = (OnlineDriveTaskTracker) taskTracker;

                    SimpleBlockingRouter simpleBlockingRouter = new SimpleBlockingRouter(parallelLeastCostPathCalculator, travelTime);
                    VrpPathWithTravelData newSubPath = simpleBlockingRouter.getRoute( //
                            vehicleLinkPair.linkTimePair.link, dest, vehicleLinkPair.linkTimePair.time);
                    System.out.println(newSubPath.getFromLink().getId() + " =? " + vehicleLinkPair.linkTimePair.link.getId());
                    System.out.println(newSubPath.getDepartureTime() + " =? " + vehicleLinkPair.linkTimePair.time);

                    if (newSubPath.getFromLink().getId() == vehicleLinkPair.linkTimePair.link.getId())
                        onlineDriveTaskTracker.divertPath(newSubPath);
                    else {
                        new RuntimeException("links no good").printStackTrace();
                        System.out.println("SKIPPED BECAUSE OF MISMATCH!");
                    }
                }
            }

            @Override
            public void handle(AVStayTask avStayTask) {
                if (!avStayTask.getLink().equals(dest)) { // ignore request where location == target
                    final double scheduleEndTime = schedule.getEndTime(); // typically 108000.0
                    if (avStayTask.getStatus() == Task.TaskStatus.STARTED) {
                        avStayTask.setEndTime(vehicleLinkPair.linkTimePair.time);
                    } else {
                        schedule.removeLastTask();
                        System.out.println("The last task was removed for " + vehicleLinkPair.avVehicle.getId());
                    }
                    SimpleBlockingRouter simpleBlockingRouter = new SimpleBlockingRouter(parallelLeastCostPathCalculator, travelTime);
                    VrpPathWithTravelData routePoints = simpleBlockingRouter.getRoute( //
                            vehicleLinkPair.linkTimePair.link, dest, vehicleLinkPair.linkTimePair.time);
                    final AVDriveTask avDriveTask = new AVDriveTask(routePoints);
                    schedule.addTask(avDriveTask);
                    schedule.addTask(new AVStayTask(avDriveTask.getEndTime(), scheduleEndTime, dest));

                }

            }
        };
    }

    public final void onNextLinkEntered(AVVehicle avVehicle, DriveTask driveTask, LinkTimePair linkTimePair) {
        // for now, do nothing
    }

    @Override
    public final void onRequestSubmitted(AVRequest request) {
        pendingRequests.add(request);
    }

    // TODO this will not be necessary!!!
    @Override
    public final void onNextTaskStarted(AVTask task) {
        // intentionally empty
    }

    @Override
    public final void onNextTimestep(double now) {
        private_now = now;
        redispatch(now);
    }

    public abstract void redispatch(double now);

    @Override
    public final void registerVehicle(AVVehicle vehicle) {
        vehicles.add(vehicle);
        eventsManager.processEvent(new AVVehicleAssignmentEvent(vehicle, 0));
    }

}
