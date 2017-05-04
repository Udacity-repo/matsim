package playground.joel.dispatcher.competitive;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

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

public class MultiDispatcher extends UniversalDispatcher {
    public static final File GROUPSIZEFILE =new File("output/groupSize.mdisp.txt"); 

    private final int dispatchPeriod;
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
                             Network network, AbstractRequestSelector abstractRequestSelector, HashSet<AVDispatcher> dispatchersIn) {
        super(avDispatcherConfig, travelTime, parallelLeastCostPathCalculator, eventsManager);
        dispatchers = dispatchersIn;
        SafeConfig safeConfig = SafeConfig.wrap(avDispatcherConfig);
        dispatchPeriod = safeConfig.getInteger("dispatchPeriod", 10);
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

    public Collection<VehicleLinkPair> supplier(int dispatcher) {
        return getDivertableVehicles().stream() //
                .filter(vlp -> groupBoundaries.lowerEntry(getVehicleIndex(vlp.avVehicle) + 1).getValue() == dispatcher) //
                .collect(Collectors.toList());

    }
    
    /*Collection<DispatchAglrotihsm> supplierD (int dispatcher){
        
    }*/

    @Override
    public void redispatch(double now) {

        for (AVVehicle avVehicle : getMaintainedVehicles())
            vehicleIntegerDatabase.getId(avVehicle);

        final long round_now = Math.round(now);

        // TODO stop vehicle from driving there
        new InOrderOfArrivalMatcher(this::setAcceptRequest) //
                .match(getStayVehicles(), getAVRequestsAtLinks()); // TODO prelimiary correct

        if (round_now % dispatchPeriod == 0) {

            redispatchStep(now, round_now);

            /*printVals = Tensors.empty();
            for (int group = 0; group < numberOfDispatchers; ++group) {
                final int final_group = group;
                // TODO try parallel
                
                // EXECUTION OF WHAT IS INSIDE THE REDISPATCH LOOP
                Tensor pv1 = HungarianUtils.globalBipartiteMatching(this, () -> supplier(final_group));
                // EXECUTION END
                
                printVals.append(pv1);
            }*/
        }
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

        @Override
        public AVDispatcher createDispatcher(AVDispatcherConfig config, AVGeneratorConfig generatorConfig) {
            AbstractRequestSelector abstractRequestSelector = new OldestRequestSelector();
            SafeConfig safeConfig = SafeConfig.wrap(config);
            int numberOfDispatchers = safeConfig.getInteger("numberOfDispatchers", 0);
            GlobalAssert.that(numberOfDispatchers != 0);
            final HashSet<AVDispatcher> dispatchers = new HashSet<>();
            for (int dispatcher = 0; dispatcher < numberOfDispatchers; ++dispatcher) {
                String dispatcherName = safeConfig.getStringStrict("dispatcher" + dispatcher);
                dispatchers.add(MultiDispatcherUtils.newDispatcher(dispatcherName, config, generatorConfig, //
                        travelTime, router, eventsManager, network, abstractRequestSelector));
            }
            GlobalAssert.that(!dispatchers.isEmpty());
            return new MultiDispatcher( //
                    config, travelTime, router, eventsManager, network, abstractRequestSelector, dispatchers);
        }

    }
}
