package playground.joel.dispatcher.competitive;

import ch.ethz.idsc.tensor.RealScalar;
import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.alg.Array;
import ch.ethz.idsc.tensor.io.Get;
import ch.ethz.idsc.tensor.io.Put;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.router.util.TravelTime;
import playground.clruch.dispatcher.core.PartitionedDispatcher;
import playground.clruch.dispatcher.core.VehicleLinkPair;
import playground.clruch.dispatcher.utils.AbstractRequestSelector;
import playground.clruch.dispatcher.utils.InOrderOfArrivalMatcher;
import playground.clruch.dispatcher.utils.LPVehicleRebalancing;
import playground.clruch.net.VehicleIntegerDatabase;
import playground.clruch.netdata.VirtualNetwork;
import playground.clruch.netdata.VirtualNode;
import playground.clruch.utils.GlobalAssert;
import playground.clruch.utils.SafeConfig;
import playground.sebhoerl.avtaxi.config.AVDispatcherConfig;
import playground.sebhoerl.avtaxi.data.AVVehicle;
import playground.sebhoerl.avtaxi.dispatcher.AVDispatcher;
import playground.sebhoerl.avtaxi.passenger.AVRequest;
import playground.sebhoerl.plcpc.ParallelLeastCostPathCalculator;

import java.io.File;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Created by Joel on 10.05.2017.
 */
public abstract class AbstractMultiDispatcher extends PartitionedDispatcher {
    public static final File GROUPSIZEFILE =new File("output/groupSize.mdisp.txt");

    final int dispatchPeriod;
    final int rebalancingPeriod;
    final int numberOfDispatchers;
    final Tensor fleetSize;
    final NavigableMap<Integer, Integer> groupBoundaries = new TreeMap<>();
    final int maxMatchNumber; // implementation may not use this
    HashSet<AVDispatcher> dispatchers = new HashSet<>();
    HashMap<Integer, LPVehicleRebalancing> lpVehicleRebalancings = new HashMap<>();
    VehicleIntegerDatabase vehicleIntegerDatabase = new VehicleIntegerDatabase();

    public AbstractMultiDispatcher( //
                             AVDispatcherConfig avDispatcherConfig, //
                             TravelTime travelTime, //
                             ParallelLeastCostPathCalculator parallelLeastCostPathCalculator, //
                             EventsManager eventsManager, //
                             Network network, AbstractRequestSelector abstractRequestSelector, //
                             VirtualNetwork virtualNetwork, //
                             HashSet<AVDispatcher> dispatchersIn) {
        super(avDispatcherConfig, travelTime, parallelLeastCostPathCalculator, eventsManager, virtualNetwork);
        dispatchers = dispatchersIn;
        SafeConfig safeConfig = SafeConfig.wrap(avDispatcherConfig);
        dispatchPeriod = safeConfig.getInteger("dispatchPeriod", 10);
        rebalancingPeriod = safeConfig.getInteger("rebalancingPeriod", Integer.MAX_VALUE);

        numberOfDispatchers = safeConfig.getInteger("numberOfDispatchers", 0);
        fleetSize = Array.zeros(numberOfDispatchers);
        int sum = 0;
        for (int dispatcher = 0; dispatcher < numberOfDispatchers; ++dispatcher) {
            groupBoundaries.put(sum, dispatcher);
            int size = safeConfig.getInteger("fleetSize" + dispatcher, -1);
            GlobalAssert.that(size != -1);
            sum += size;
            fleetSize.set(RealScalar.of(size), dispatcher);
            lpVehicleRebalancings.put(dispatcher, new LPVehicleRebalancing(virtualNetwork));
        }
        maxMatchNumber = safeConfig.getInteger("maxMatchNumber", Integer.MAX_VALUE);
        // TODO check that sum == Total of groupSize == number of vehicles (at this point vehicle count is not available)

        try {
            Put.of(GROUPSIZEFILE, fleetSize);
            Tensor check = Get.of(GROUPSIZEFILE);
            GlobalAssert.that(fleetSize.equals(check));
        } catch (Exception exception) {
            exception.printStackTrace();
            GlobalAssert.that(false);
        }
    }

    public int getVehicleIndex(AVVehicle avVehicle) {
        return vehicleIntegerDatabase.getId(avVehicle); // map must contain vehicle
    }

    public Supplier<Collection<VehicleLinkPair>> supplier(int dispatcher) {
        Supplier<Collection<VehicleLinkPair>> supplier = () -> getDivertableVehicles().stream() //
                .filter(vlp -> groupBoundaries.lowerEntry(getVehicleIndex(vlp.avVehicle) + 1).getValue() == dispatcher) //
                .collect(Collectors.toList());
        return supplier;
    }

    public Supplier<Collection<VehicleLinkPair>> virtualNotRebalancingSupplier(int dispatcher) {
        Supplier<Collection<VehicleLinkPair>> supplier = () -> getVirtualNodeDivertableNotRebalancingVehicles() //
                .values().stream().flatMap(v -> v.stream().filter(vlp -> groupBoundaries.lowerEntry( //
                        getVehicleIndex(vlp.avVehicle) + 1).getValue() == dispatcher)).collect(Collectors.toList());
        return supplier;
    }

    public Map<VirtualNode, List<VehicleLinkPair>> getVirtualNodeDivertableNotRebalancingVehicles(int dispatcher) {
        Map<VirtualNode, List<VehicleLinkPair>> availableVehicles = getVirtualNodeDivertableNotRebalancingVehicles();
        Map<VirtualNode, List<VehicleLinkPair>> returnMap = new HashMap<>();
        for (VirtualNode virtualNode : virtualNetwork.getVirtualNodes()) {
            if (!returnMap.containsKey(virtualNode)) {
                returnMap.put(virtualNode, Collections.emptyList());
            }
        }
        List<VirtualNode> vNodes = getVirtualNodeDivertableNotRebalancingVehicles().keySet().stream().collect(Collectors.toList());
        for (int i = 0; i < returnMap.size(); i++) {
            List<VehicleLinkPair> list = availableVehicles.get(vNodes.get(i)).stream(). //
                    filter(vlp -> groupBoundaries.lowerEntry(getVehicleIndex(vlp.avVehicle) + 1). //
                    getValue() == dispatcher).collect(Collectors.toList());
            returnMap.put(vNodes.get(i), list);
        }
        GlobalAssert.that(!returnMap.isEmpty());
        return returnMap;
    }

    public Map<VirtualNode, List<AVRequest>> getVirtualNodeRequests() {
        return super.getVirtualNodeRequests();
    }

    protected synchronized Map<VirtualNode, Set<AVVehicle>> getVirtualNodeRebalancingToVehicles(int dispatcher) {
        // create set
        Map<VirtualNode, Set<AVVehicle>> returnMap = new HashMap<>();
        for (VirtualNode virtualNode : virtualNetwork.getVirtualNodes()) {
            returnMap.put(virtualNode, new HashSet<>());
        }
        final Map<AVVehicle, Link> rebalancingVehicles = getRebalancingVehicles().entrySet().stream(). //
                filter(vl -> groupBoundaries.lowerEntry(getVehicleIndex(vl.getKey()) + 1). //
                getValue() == dispatcher).collect(Collectors.toMap(vl -> vl.getKey(), vl -> vl.getValue()));
        for (AVVehicle avVehicle : rebalancingVehicles.keySet()) {
            boolean successAdd = returnMap.get(virtualNetwork.getVirtualNode(rebalancingVehicles.get(avVehicle))).add(avVehicle);
            GlobalAssert.that(successAdd);
        }

        // return set
        return Collections.unmodifiableMap(returnMap);
    }

    protected synchronized Map<VirtualNode, Set<AVVehicle>> getVirtualNodeArrivingWCustomerVehicles(int dispatcher) {
        final Map<AVVehicle, Link> customMap = getVehiclesWithCustomer().entrySet().stream(). //
                filter(vl -> groupBoundaries.lowerEntry(getVehicleIndex(vl.getKey()) + 1). //
                getValue() == dispatcher).collect(Collectors.toMap(vl -> vl.getKey(), vl -> vl.getValue()));
        final HashMap<VirtualNode, Set<AVVehicle>> customVehiclesMap = new HashMap<>();
        for (VirtualNode virtualNode : virtualNetwork.getVirtualNodes())
            customVehiclesMap.put(virtualNode, new HashSet<>());
        customMap.entrySet().stream() //
                .forEach(e -> customVehiclesMap.get(virtualNetwork.getVirtualNode(e.getValue())).add(e.getKey()));
        return customVehiclesMap;
    }

    @Override
    public void redispatch(double now) {

        for (AVVehicle avVehicle : getMaintainedVehicles())
            vehicleIntegerDatabase.getId(avVehicle);

        final long round_now = Math.round(now);

        // TODO stop vehicle from driving there
        new InOrderOfArrivalMatcher(this::setAcceptRequest) //
                .match(getStayVehicles(), getAVRequestsAtLinks()); // TODO prelimiary correct

        if (round_now % rebalancingPeriod == 0) {
            rebalanceStep(now, round_now);
        }

        if (round_now % dispatchPeriod == 0) {
            redispatchStep(now, round_now);
        }
    }

    /**
     * functions below are actually implemented in successor classes
     *
     * @param now
     * @param round_now
     */
    public void rebalanceStep(double now, long round_now) {
        // empty
    }

    public void redispatchStep(double now, long round_now) {
        // empty
    }

}
