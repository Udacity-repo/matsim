package playground.clruch.dispatcher;

import java.util.Collection;
import java.util.stream.Collectors;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.router.util.TravelTime;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.Tensors;
import playground.clruch.dispatcher.core.UniversalDispatcher;
import playground.clruch.dispatcher.core.VehicleLinkPair;
import playground.clruch.dispatcher.utils.AbstractRequestSelector;
import playground.clruch.dispatcher.utils.InOrderOfArrivalMatcher;
import playground.clruch.dispatcher.utils.OldestRequestSelector;
import playground.clruch.net.VehicleIntegerDatabase;
import playground.clruch.utils.SafeConfig;
import playground.sebhoerl.avtaxi.config.AVDispatcherConfig;
import playground.sebhoerl.avtaxi.config.AVGeneratorConfig;
import playground.sebhoerl.avtaxi.data.AVVehicle;
import playground.sebhoerl.avtaxi.dispatcher.AVDispatcher;
import playground.sebhoerl.avtaxi.framework.AVModule;
import playground.sebhoerl.plcpc.ParallelLeastCostPathCalculator;

public class DoubleHungarianDispatcher extends UniversalDispatcher {

    private final int dispatchPeriod;
    private final int firstGroupSize;
    private final int maxMatchNumber; // implementation may not use this
    private Tensor printVals = Tensors.empty();

    VehicleIntegerDatabase vehicleIntegerDatabase = new VehicleIntegerDatabase();

    private DoubleHungarianDispatcher( //
            AVDispatcherConfig avDispatcherConfig, //
            TravelTime travelTime, //
            ParallelLeastCostPathCalculator parallelLeastCostPathCalculator, //
            EventsManager eventsManager, //
            Network network, AbstractRequestSelector abstractRequestSelector) {
        super(avDispatcherConfig, travelTime, parallelLeastCostPathCalculator, eventsManager);
        SafeConfig safeConfig = SafeConfig.wrap(avDispatcherConfig);
        dispatchPeriod = safeConfig.getInteger("dispatchPeriod", 10);
        // TODO read from file! make fail if not present
        firstGroupSize = safeConfig.getInteger("firstGroupSize", 50);
        maxMatchNumber = safeConfig.getInteger("maxMatchNumber", Integer.MAX_VALUE);
    }

    int getVehicleIndex(AVVehicle avVehicle) {
        return vehicleIntegerDatabase.getId(avVehicle); // map must contain vehicle
    }

    Collection<VehicleLinkPair> supplier1() {
        return getDivertableVehicles().stream() //
                .filter(vlp -> getVehicleIndex(vlp.avVehicle) < firstGroupSize) //
                .collect(Collectors.toList());
    }

    Collection<VehicleLinkPair> supplier2() { // TODO unify logic, merge 2 functions!
        return getDivertableVehicles().stream() //
                .filter(vlp -> getVehicleIndex(vlp.avVehicle) >= firstGroupSize) //
                .collect(Collectors.toList());
    }

    @Override
    public void redispatch(double now) {

        for (AVVehicle avVehicle : getMaintainedVehicles())
            vehicleIntegerDatabase.getId(avVehicle);

        final long round_now = Math.round(now);

        new InOrderOfArrivalMatcher(this::setAcceptRequest) //
                .match(getStayVehicles(), getAVRequestsAtLinks()); // TODO prelimiary correct

        if (round_now % dispatchPeriod == 0) {
            if (round_now == 11890) {
                System.out.println("arrived at problem");
            }

            Tensor pv1 = HungarianUtils.globalBipartiteMatching(this, () -> supplier1());
            Tensor pv2 = HungarianUtils.globalBipartiteMatching(this, () -> supplier2());
            printVals = Tensors.of(pv1, pv2);
        }
    }

    @Override
    public String getInfoLine() {
        return String.format("%s H=%s", //
                super.getInfoLine(), //
                printVals.toString() //
        );
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

        @Override
        public AVDispatcher createDispatcher(AVDispatcherConfig config, AVGeneratorConfig generatorConfig) {
            AbstractRequestSelector abstractRequestSelector = new OldestRequestSelector();
            return new DoubleHungarianDispatcher( //
                    config, travelTime, router, eventsManager, network, abstractRequestSelector);
        }
    }
}
