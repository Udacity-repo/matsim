package playground.joel.dispatcher.competitive;

import java.io.File;
import java.util.Collection;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.router.util.TravelTime;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import ch.ethz.idsc.tensor.RealScalar;
import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.Tensors;
import ch.ethz.idsc.tensor.alg.Array;
import ch.ethz.idsc.tensor.io.Get;
import ch.ethz.idsc.tensor.io.Put;
import playground.clruch.dispatcher.HungarianUtils;
import playground.clruch.dispatcher.core.UniversalDispatcher;
import playground.clruch.dispatcher.core.VehicleLinkPair;
import playground.clruch.dispatcher.utils.AbstractRequestSelector;
import playground.clruch.dispatcher.utils.InOrderOfArrivalMatcher;
import playground.clruch.dispatcher.utils.OldestRequestSelector;
import playground.clruch.net.VehicleIntegerDatabase;
import playground.clruch.utils.GlobalAssert;
import playground.clruch.utils.SafeConfig;
import playground.sebhoerl.avtaxi.config.AVDispatcherConfig;
import playground.sebhoerl.avtaxi.config.AVGeneratorConfig;
import playground.sebhoerl.avtaxi.data.AVVehicle;
import playground.sebhoerl.avtaxi.dispatcher.AVDispatcher;
import playground.sebhoerl.avtaxi.framework.AVModule;
import playground.sebhoerl.plcpc.ParallelLeastCostPathCalculator;

public class MultiHungarianDispatcher extends UniversalDispatcher {
    public static final File GROUPSIZEFILE =new File("output/groupSize.mdisp.txt"); 

    private final int dispatchPeriod;
    private final int numberOfGroups;
    private final Tensor groupSize;
    private final NavigableMap<Integer, Integer> groupBoundaries = new TreeMap<>();
    private final int maxMatchNumber; // implementation may not use this
    private Tensor printVals = Tensors.empty();

    VehicleIntegerDatabase vehicleIntegerDatabase = new VehicleIntegerDatabase();

    private MultiHungarianDispatcher( //
                                      AVDispatcherConfig avDispatcherConfig, //
                                      TravelTime travelTime, //
                                      ParallelLeastCostPathCalculator parallelLeastCostPathCalculator, //
                                      EventsManager eventsManager, //
                                      Network network, AbstractRequestSelector abstractRequestSelector) {
        super(avDispatcherConfig, travelTime, parallelLeastCostPathCalculator, eventsManager);
        SafeConfig safeConfig = SafeConfig.wrap(avDispatcherConfig);
        dispatchPeriod = safeConfig.getInteger("dispatchPeriod", 10);
        numberOfGroups = safeConfig.getInteger("numberOfGroups", 0);
        groupSize = Array.zeros(numberOfGroups);
        int sum = 0;
        for (int group = 0; group < numberOfGroups; ++group) {
            groupBoundaries.put(sum, group);
            int size = safeConfig.getInteger("groupSize" + group, -1);
            GlobalAssert.that(size != -1);
            sum += size;
            groupSize.set(RealScalar.of(size), group);
        }
        maxMatchNumber = safeConfig.getInteger("maxMatchNumber", Integer.MAX_VALUE);
        // TODO check that sum == Total of groupSize == number of vehicles (at this point vehicle count is not available)

        try {
            Put.of(GROUPSIZEFILE, groupSize);
            Tensor check = Get.of(GROUPSIZEFILE);
            GlobalAssert.that(groupSize.equals(check));
        } catch (Exception exception) {
            exception.printStackTrace();
            GlobalAssert.that(false);
        }
    }

    int getVehicleIndex(AVVehicle avVehicle) {
        return vehicleIntegerDatabase.getId(avVehicle); // map must contain vehicle
    }

    Collection<VehicleLinkPair> supplier(int dispatcher) {
        return getDivertableVehicles().stream() //
                .filter(vlp -> groupBoundaries.lowerEntry(getVehicleIndex(vlp.avVehicle) + 1).getValue() == dispatcher) //
                .collect(Collectors.toList());

    }

    @Override
    public void redispatch(double now) {

        for (AVVehicle avVehicle : getMaintainedVehicles())
            vehicleIntegerDatabase.getId(avVehicle);

        final long round_now = Math.round(now);

        // TODO stop vehicle from driving there
        new InOrderOfArrivalMatcher(this::setAcceptRequest) //
                .match(getStayVehicles(), getAVRequestsAtLinks()); // TODO prelimiary correct

        if (round_now % dispatchPeriod == 0) {
            printVals = Tensors.empty();
            for (int group = 0; group < numberOfGroups; ++group) {
                final int final_group = group;
                // TODO try parallel
                Tensor pv1 = HungarianUtils.globalBipartiteMatching(this, () -> supplier(final_group));
                printVals.append(pv1);
            }
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
            return new MultiHungarianDispatcher( //
                    config, travelTime, router, eventsManager, network, abstractRequestSelector);
        }
    }
}
