package playground.joel.dispatcher.competitive;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.router.util.TravelTime;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import ch.ethz.idsc.tensor.RealScalar;
import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.alg.Array;
import ch.ethz.idsc.tensor.io.Get;
import ch.ethz.idsc.tensor.io.Put;
import playground.clruch.dispatcher.core.PartitionedDispatcher;
import playground.clruch.dispatcher.core.UniversalDispatcher;
import playground.clruch.dispatcher.core.VehicleLinkPair;
import playground.clruch.dispatcher.utils.*;
import playground.clruch.net.VehicleIntegerDatabase;
import playground.clruch.netdata.VirtualNetwork;
import playground.clruch.netdata.VirtualNetworkIO;
import playground.clruch.netdata.VirtualNode;
import playground.clruch.utils.GlobalAssert;
import playground.clruch.utils.SafeConfig;
import playground.sebhoerl.avtaxi.config.AVDispatcherConfig;
import playground.sebhoerl.avtaxi.config.AVGeneratorConfig;
import playground.sebhoerl.avtaxi.data.AVVehicle;
import playground.sebhoerl.avtaxi.dispatcher.AVDispatcher;
import playground.sebhoerl.avtaxi.framework.AVModule;
import playground.sebhoerl.avtaxi.passenger.AVRequest;
import playground.sebhoerl.plcpc.ParallelLeastCostPathCalculator;

public class MultiDispatcher extends PartitionedDispatcher {
    public static final File GROUPSIZEFILE =new File("output/groupSize.mdisp.txt"); 

    private final int dispatchPeriod;
    private final int rebalancingPeriod;
    private final int numberOfDispatchers;
    private final Tensor fleetSize;
    private final NavigableMap<Integer, Integer> groupBoundaries = new TreeMap<>();
    private final int maxMatchNumber; // implementation may not use this
    private HashSet<AVDispatcher> dispatchers = new HashSet<>();

    VehicleIntegerDatabase vehicleIntegerDatabase = new VehicleIntegerDatabase();

    private MultiDispatcher( //
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
        Iterator<VirtualNode> vNode = getVirtualNodeDivertableNotRebalancingVehicles().keySet().iterator();
        while(vNode.hasNext()) {
            List<VehicleLinkPair> list = availableVehicles.get(vNode.next()).stream(). //
                    filter(vlp -> groupBoundaries.lowerEntry(getVehicleIndex(vlp.avVehicle) + 1). //
                    getValue() == dispatcher).collect(Collectors.toList());
            returnMap.put(vNode.next(), list);
        }
        GlobalAssert.that(!returnMap.isEmpty());
        return returnMap;
    }

    public Map<VirtualNode, List<AVRequest>> getVirtualNodeRequests() {
        return super.getVirtualNodeRequests();
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

    public void rebalanceStep(double now, long round_now) {
        MultiDispatcherUtils.rebalanceStep(this, now, round_now, dispatchers);
    }

    public void redispatchStep(double now, long round_now) {
        MultiDispatcherUtils.redispatchStep(this, now, round_now, dispatchers);
    }

    @Override
    public String getInfoLine() {
        // TODO: fix all info lines
        // String infoLine = MultiDispatcherUtils.getInfoLine(dispatchers);
        String infoLine = super.getInfoLine();
        return infoLine;
    }

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

        public static VirtualNetwork virtualNetwork;

        @Override
        public AVDispatcher createDispatcher(AVDispatcherConfig config, AVGeneratorConfig generatorConfig) {
            AbstractRequestSelector abstractRequestSelector = new OldestRequestSelector();
            AbstractVirtualNodeDest abstractVirtualNodeDest = new KMeansVirtualNodeDest();
            AbstractVehicleDestMatcher abstractVehicleDestMatcher = new HungarBiPartVehicleDestMatcher();
            SafeConfig safeConfig = SafeConfig.wrap(config);
            int numberOfDispatchers = safeConfig.getInteger("numberOfDispatchers", 0);
            GlobalAssert.that(numberOfDispatchers != 0);

            try {
                final File virtualnetworkDir = new File(safeConfig.getStringStrict("virtualNetworkDirectory"));
                GlobalAssert.that(virtualnetworkDir.isDirectory());
                {
                    final File virtualnetworkFile = new File(virtualnetworkDir, "virtualNetwork");
                    System.out.println("" + virtualnetworkFile.getAbsoluteFile());
                    try {
                        virtualNetwork = VirtualNetworkIO.fromByte(network, virtualnetworkFile);
                    } catch (ClassNotFoundException | DataFormatException | IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                System.out.println("ATTENTION: No virtual network found!");
            }

            final HashSet<AVDispatcher> dispatchers = new HashSet<>();
            int totalFleetSize = 0;
            for (int dispatcher = 0; dispatcher < numberOfDispatchers; ++dispatcher) {
                String dispatcherName = safeConfig.getStringStrict("dispatcher" + dispatcher);

                // adapt generatorConfig
                AVGeneratorConfig tempGeneratorConfig = //
                        new AVGeneratorConfig(generatorConfig.getParent(), generatorConfig.getStrategyName());
                int fleetSize = safeConfig.getInteger("fleetSize" + dispatcher, -1);
                GlobalAssert.that(fleetSize != -1);
                tempGeneratorConfig.setNumberOfVehicles(fleetSize);
                totalFleetSize += fleetSize;

                dispatchers.add(MultiDispatcherUtils.newDispatcher(dispatcherName, config, tempGeneratorConfig, //
                        travelTime, router, eventsManager, network, abstractRequestSelector, abstractVirtualNodeDest, //
                        abstractVehicleDestMatcher, virtualNetwork));
            }
            GlobalAssert.that(!dispatchers.isEmpty());
            if (totalFleetSize != generatorConfig.getNumberOfVehicles()) System.out.println( //
                    "ERROR: the dispatchers have combined only " + totalFleetSize + //
                            " vehicles, not " + generatorConfig.getNumberOfVehicles() + "!\n" + //
                            "\tcheck that all the values in av.xml make sense!");
            GlobalAssert.that(totalFleetSize == generatorConfig.getNumberOfVehicles());
            return new MultiDispatcher( //
                    config, travelTime, router, eventsManager, network, abstractRequestSelector, virtualNetwork, dispatchers);
        }

    }
}
