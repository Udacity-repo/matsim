package playground.clruch.dispatcher;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.router.util.TravelTime;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import playground.clruch.dispatcher.core.UniversalDispatcher;
import playground.clruch.dispatcher.core.VehicleLinkPair;
import playground.clruch.dispatcher.utils.AbstractRequestSelector;
import playground.clruch.dispatcher.utils.HungarBiPartVehicleDestMatcher;
import playground.clruch.dispatcher.utils.ImmobilizeVehicleDestMatcher;
import playground.clruch.dispatcher.utils.InOrderOfArrivalMatcher;
import playground.clruch.dispatcher.utils.OldestRequestSelector;
import playground.clruch.utils.SafeConfig;
import playground.sebhoerl.avtaxi.config.AVDispatcherConfig;
import playground.sebhoerl.avtaxi.config.AVGeneratorConfig;
import playground.sebhoerl.avtaxi.dispatcher.AVDispatcher;
import playground.sebhoerl.avtaxi.framework.AVModule;
import playground.sebhoerl.avtaxi.passenger.AVRequest;
import playground.sebhoerl.plcpc.ParallelLeastCostPathCalculator;

public class HungarianDispatcher extends UniversalDispatcher {

    private final int dispatchPeriod;
    private final int maxMatchNumber; // implementation may not use this

    private HungarianDispatcher( //
            AVDispatcherConfig avDispatcherConfig, //
            TravelTime travelTime, //
            ParallelLeastCostPathCalculator parallelLeastCostPathCalculator, //
            EventsManager eventsManager, //
            Network network, AbstractRequestSelector abstractRequestSelector) {
        super(avDispatcherConfig, travelTime, parallelLeastCostPathCalculator, eventsManager);
        SafeConfig safeConfig = SafeConfig.wrap(avDispatcherConfig);
        dispatchPeriod = safeConfig.getInteger("dispatchPeriod", 10);
        maxMatchNumber = safeConfig.getInteger("maxMatchNumber", Integer.MAX_VALUE);
    }

    int ori_sizeV = 0;
    int ori_sizeR = 0;

    int sizeV = 0;
    int sizeR = 0;

    @Override
    public void redispatch(double now) {
        final long round_now = Math.round(now);

        new InOrderOfArrivalMatcher(this::setAcceptRequest) //
                .match(getStayVehicles(), getAVRequestsAtLinks());

        if (round_now % dispatchPeriod == 0) {
            { // assign new destination to vehicles with bipartite matching

                Map<Link, List<AVRequest>> requests = getAVRequestsAtLinks();


                // reduce the number of requests for a smaller running time
                // take out and match request-vehicle pairs with distance zero
                List<Link> requestlocs = requests.values().stream() //
                        .flatMap(List::stream) // all from links of all requests with
                        .map(AVRequest::getFromLink) // multiplicity
                        .collect(Collectors.toList());

                {
                    // call getDivertableVehicles again to get remaining vehicles
                    Collection<VehicleLinkPair> divertableVehicles = getDivertableVehicles();

                    ori_sizeV = divertableVehicles.size();
                    ori_sizeR = requestlocs.size();

                    // this call removes the matched links from requestlocs
                    new ImmobilizeVehicleDestMatcher().match(divertableVehicles, requestlocs) //
                            .entrySet().forEach(this::setVehicleDiversion);
                }

                /*
                 * // only select MAXMATCHNUMBER of oldest requests
                 * Collection<AVRequest> avRequests = requests.values().stream().flatMap(v -> v.stream()).collect(Collectors.toList());
                 * Collection<AVRequest> avRequestsReduced = requestSelector.selectRequests(divertableVehicles, avRequests, MAXMATCHNUMBER);
                 */

                {
                    // call getDivertableVehicles again to get remaining vehicles
                    Collection<VehicleLinkPair> divertableVehicles = getDivertableVehicles();

                    sizeV = divertableVehicles.size();
                    sizeR = requestlocs.size();
                    // find the Euclidean bipartite matching for all vehicles using the Hungarian method
                    new HungarBiPartVehicleDestMatcher().match(divertableVehicles, requestlocs) //
                            .entrySet().forEach(this::setVehicleDiversion);
                }
            }
        }
    }

    @Override
    public String getInfoLine() {
        return String.format("%s H[%d x %d] => H[%d x %d]", //
                super.getInfoLine(), //
                ori_sizeV, ori_sizeR, //
                sizeV, sizeR //
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
            return new HungarianDispatcher( //
                    config, travelTime, router, eventsManager, network, abstractRequestSelector);
        }
    }
}
