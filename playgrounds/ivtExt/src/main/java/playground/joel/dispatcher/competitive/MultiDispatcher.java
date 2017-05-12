package playground.joel.dispatcher.competitive;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.router.util.TravelTime;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import playground.clruch.dispatcher.utils.*;
import playground.clruch.netdata.VirtualNetwork;
import playground.clruch.netdata.VirtualNetworkIO;
import playground.clruch.traveldata.TravelData;
import playground.clruch.traveldata.TravelDataIO;
import playground.clruch.utils.GlobalAssert;
import playground.clruch.utils.SafeConfig;
import playground.sebhoerl.avtaxi.config.AVDispatcherConfig;
import playground.sebhoerl.avtaxi.config.AVGeneratorConfig;
import playground.sebhoerl.avtaxi.dispatcher.AVDispatcher;
import playground.sebhoerl.avtaxi.framework.AVModule;
import playground.sebhoerl.plcpc.ParallelLeastCostPathCalculator;

public class MultiDispatcher extends AbstractMultiDispatcher {

    private final int dispatchPeriod;
    private final int rebalancingPeriod;
    private HashSet<AVDispatcher> dispatchers = new HashSet<>();
    HashMap<Integer, LPVehicleRebalancing> lpVehicleRebalancings = new HashMap<>();

    private MultiDispatcher( //
                             AVDispatcherConfig avDispatcherConfig, //
                             TravelTime travelTime, //
                             ParallelLeastCostPathCalculator parallelLeastCostPathCalculator, //
                             EventsManager eventsManager, //
                             Network network, AbstractRequestSelector abstractRequestSelector, //
                             VirtualNetwork virtualNetwork, //
                             HashSet<AVDispatcher> dispatchersIn) {
        super(avDispatcherConfig, travelTime, parallelLeastCostPathCalculator, eventsManager, network, //
                abstractRequestSelector, virtualNetwork, dispatchersIn);
        dispatchPeriod = super.dispatchPeriod;
        rebalancingPeriod = super.rebalancingPeriod;
        dispatchers = super.dispatchers;
        lpVehicleRebalancings = super.lpVehicleRebalancings;
    }

    @Override
    public void rebalanceStep(double now, long round_now) {
        MultiDispatcherUtils.rebalanceStep(this, now, round_now, dispatchers);
    }

    @Override
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

            final File virtualnetworkDir = new File(config.getParams().get("virtualNetworkDirectory"));
            GlobalAssert.that(virtualnetworkDir.isDirectory());
            {
                final File virtualnetworkFile = new File(virtualnetworkDir, "virtualNetwork");
                GlobalAssert.that(virtualnetworkFile.isFile());
                try {
                    virtualNetwork = VirtualNetworkIO.fromByte(network, virtualnetworkFile);
                } catch (ClassNotFoundException | DataFormatException | IOException e) {
                    e.printStackTrace();
                }
            }

            TravelData travelData = null;
            try {
                travelData = TravelDataIO.fromByte(network, virtualNetwork, new File(virtualnetworkDir, "travelData"));
            } catch (ClassNotFoundException | DataFormatException | IOException e) {
                System.out.println("problem reading travelData");
                e.printStackTrace();
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
                        abstractVehicleDestMatcher, virtualNetwork, travelData));
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
