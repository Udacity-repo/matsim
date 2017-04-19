package playground.joel.dispatcher.single_heuristic;

import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.Tensors;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.dvrp.util.LinkTimePair;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.collections.QuadTree;
import playground.clruch.dispatcher.HungarianDispatcher;
import playground.clruch.dispatcher.core.UniversalDispatcher;
import playground.clruch.dispatcher.core.VehicleLinkPair;
import playground.clruch.dispatcher.utils.*;
import playground.clruch.simonton.Cluster;
import playground.clruch.simonton.EuclideanDistancer;
import playground.clruch.simonton.MyTree;
import playground.clruch.utils.GlobalAssert;
import playground.clruch.utils.SafeConfig;
import playground.sebhoerl.avtaxi.config.AVDispatcherConfig;
import playground.sebhoerl.avtaxi.config.AVGeneratorConfig;
import playground.sebhoerl.avtaxi.data.AVOperator;
import playground.sebhoerl.avtaxi.data.AVVehicle;
import playground.sebhoerl.avtaxi.dispatcher.AVDispatcher;
import playground.sebhoerl.avtaxi.framework.AVModule;
import playground.sebhoerl.avtaxi.passenger.AVRequest;
import playground.sebhoerl.plcpc.ParallelLeastCostPathCalculator;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class NewSingleHeuristicDispatcher extends UniversalDispatcher {

    private final int dispatchPeriod;

    private List<AVVehicle> availableVehicles = new LinkedList<>();
    final private List<AVRequest> matchedRequests = new LinkedList<>();

    /** data structure to find closest vehicles */
    final private QuadTree<AVVehicle> availableVehiclesTree;
    final private HashMap<AVVehicle, Link> vehiclesInTree = new HashMap<>(); // two data structures
                                                                             // are
    // used to enable fast
    // "contains" searching

    private NewSingleHeuristicDispatcher( //
            AVDispatcherConfig avDispatcherConfig, //
            TravelTime travelTime, //
            ParallelLeastCostPathCalculator parallelLeastCostPathCalculator, //
            EventsManager eventsManager, //
            Network network, AbstractRequestSelector abstractRequestSelector) {
        super(avDispatcherConfig, travelTime, parallelLeastCostPathCalculator, eventsManager);
        SafeConfig safeConfig = SafeConfig.wrap(avDispatcherConfig);
        dispatchPeriod = safeConfig.getInteger("dispatchPeriod", 10);
        double[] bounds = NetworkUtils.getBoundingBox(network.getNodes().values()); // minx, miny,
                                                                                    // maxx, maxy
        availableVehiclesTree = new QuadTree<>(bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    @Override
    public void redispatch(double now) {
        final long round_now = Math.round(now);

        // get the staying vehicles and requests
        availableVehicles = getStayVehicles().values().stream().flatMap(Queue::stream).collect(Collectors.toList());
        Set<AVVehicle> assignedVehicles = getAssignedVehicles();
        List<AVVehicle> availableUnassignedVehicles = availableVehicles.stream().filter(v -> !assignedVehicles.contains(v))
                .collect(Collectors.toList());

        // get Map for Matcher
        HashMap<AVVehicle, Link> stayVehiclesAtLinks = new HashMap<>();
        Map<Link, Queue<AVVehicle>> stayVehicles = getStayVehicles();
        for (Link link : stayVehicles.keySet()) {
            Queue<AVVehicle> queue = stayVehicles.get(link);
            for (AVVehicle avVehicle : queue) {
                stayVehiclesAtLinks.put(avVehicle, link);
            }
        }

        // match all matched av/request pairs which are at same link
        new PredefinedMatchingMatcher(this::setAcceptRequest) //
                .matchPredefined(stayVehiclesAtLinks, getMatchings());
        

        if (round_now % dispatchPeriod == 0) {

            Collection<AVRequest> unassignedRequests = new HashSet<>(getUnassignedAVRequests());

            // pick one and send it to customer
            if (!availableUnassignedVehicles.isEmpty() && !unassignedRequests.isEmpty()) {
                AVVehicle pickedVehicle = availableUnassignedVehicles.get(0);
                AVRequest request = unassignedRequests.iterator().next();
                sendStayVehicleCustomer(pickedVehicle, request);
            }
        }

    }

    // private void updateVehiclesTree() {
    // for (AVVehicle car : availableVehicles) {
    // if (!vehiclesInTree.keySet().contains(car)) {
    // Coord linkCoord = getVehicleLocation(car).getCoord();
    // boolean avSucc = vehiclesInTree.put(car, getVehicleLocation(car)) == null;
    // boolean qtSucc = availableVehiclesTree.put(linkCoord.getX(), linkCoord.getY(), car);
    // GlobalAssert.that(avSucc == qtSucc && avSucc == true);
    // // System.out.println("added " + car + " to the quadtree");
    // }
    // }
    //
    // System.out.println("size of availableVehicles tree = " + availableVehiclesTree.size() + " ==
    // " + availableVehicles.size()
    // + " = size of availableVehicles");
    //
    // System.out.println("size of availableVehicles tree = " + availableVehiclesTree.size() + " ==
    // " + vehiclesInTree.size()
    // + " = vehiclesInTree size");
    // }
    //
    // private AVVehicle findClosestVehicle(Link link) {
    // Coord coord = link.getCoord();
    // return availableVehiclesTree.getClosest(coord.getX(), coord.getY());
    // }
    //
    // private void removeVehicle(AVVehicle vehicle) {
    // availableVehicles.remove(vehicle);
    // Coord coord = getVehicleLocation(vehicle).getCoord();
    // boolean avSucc = vehiclesInTree.remove(vehicle, getVehicleLocation(vehicle));
    // boolean qtSucc = availableVehiclesTree.remove(coord.getX(), coord.getY(), vehicle);
    // GlobalAssert.that(avSucc == qtSucc && avSucc == true);
    // }

    public static class Factory implements AVDispatcherFactory {
        @Inject
        @Named(AVModule.AV_MODE)
        private ParallelLeastCostPathCalculator router;

        @Inject
        @Named(AVModule.AV_MODE)
        private TravelTime travelTime;

        @Inject
        private EventsManager eventsManager;

        @Inject
        private Network network;

        @Override
        public AVDispatcher createDispatcher(AVDispatcherConfig config, AVGeneratorConfig generatorConfig) {
            AbstractRequestSelector abstractRequestSelector = new OldestRequestSelector();
            return new NewSingleHeuristicDispatcher( //
                    config, travelTime, router, eventsManager, network, abstractRequestSelector);
        }
    }
}

//
//
//
//
//
// if (round_now % dispatchPeriod == 0) {
//
//
// unmatchedRequests.removeAll(matchedRequests);
//
//// availableVehicles =
//// getStayVehicles().values().stream().flatMap(Queue::stream).collect(Collectors.toList());
// availableVehicles =
//// getDivertableVehicles().stream().map(v->v.avVehicle).collect(Collectors.toList());
// updateVehiclesTree();
//
// for (AVRequest request : unmatchedRequests) {
// if (!availableVehicles.isEmpty()) {
//
// AVVehicle vehicle = findClosestVehicle(request.getFromLink());
//
//
//// Collection<VehicleLinkPair> divVehicles = getDivertableVehicles();
//// if (!divVehicles.stream().anyMatch(vlp -> vlp.avVehicle.equals(vehicle))) { // for
//// debugging purpose
//// System.out.println(vehicle + " is no diverertable vehicle!");
//// System.out.println("diverertable vehicles are:");
//// for (VehicleLinkPair element : divVehicles)
//// System.out.println("\t" + element.avVehicle);
//// System.out.println("available vehicles are:");
//// for (AVVehicle element : availableVehicles)
//// System.out.println("\t" + element);
//// System.out.println("vehicles in quadtree:");
//// for(AVVehicle element : availableVehiclesTree.values()) System.out.println("\t" +
//// element);
//// }
//// VehicleLinkPair pair = divVehicles.stream().filter(vlp ->
//// vlp.avVehicle.equals(vehicle)).findFirst().get();
//
// setAcceptRequest(vehicle, request);
// matchedRequests.add(request);
// removeVehicle(vehicle);
// }
// }
// }
//

// availableVehicles =
// getStayVehicles().values().stream().flatMap(Queue::stream).collect(Collectors.toList());
//
// // updateVehiclesTree();
//
// Collection<AVRequest> unmatchedRequests = new HashSet<>(getAVRequests());
//
// for (AVRequest request : unmatchedRequests) {
//
//
// if (!stayVehicleLinkPairs.isEmpty()) {
//
// VehicleLinkPair somevehicle = stayVehicleLinkPairs.iterator().next();
//
// setVehicleDiversion(somevehicle, request.getFromLink());
// setAcceptRequest(somevehicle.avVehicle, request);
// matchedRequests.add(request);
// unmatchedRequests.removeAll(matchedRequests);
// }
// }
