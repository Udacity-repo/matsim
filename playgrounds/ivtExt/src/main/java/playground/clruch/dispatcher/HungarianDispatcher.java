package playground.clruch.dispatcher;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.router.util.TravelTime;
import playground.clruch.dispatcher.core.UniversalDispatcher;
import playground.clruch.dispatcher.core.VehicleLinkPair;
import playground.clruch.dispatcher.utils.AbstractVehicleDestMatcher;
import playground.clruch.dispatcher.utils.AbstractVehicleRequestMatcher;
import playground.clruch.dispatcher.utils.DrivebyRequestStopper;
import playground.clruch.dispatcher.utils.HungarBiPartVehicleDestMatcher;
import playground.clruch.dispatcher.utils.InOrderOfArrivalMatcher;
import playground.sebhoerl.avtaxi.config.AVDispatcherConfig;
import playground.sebhoerl.avtaxi.config.AVGeneratorConfig;
import playground.sebhoerl.avtaxi.dispatcher.AVDispatcher;
import playground.sebhoerl.avtaxi.framework.AVModule;
import playground.sebhoerl.avtaxi.passenger.AVRequest;
import playground.sebhoerl.plcpc.ParallelLeastCostPathCalculator;

import java.util.*;
import java.util.stream.Collectors;

public class HungarianDispatcher extends UniversalDispatcher {
    private final int DISPATCH_PERIOD;

    final Network network; // <- for verifying link references
    final Collection<Link> linkReferences; // <- for verifying link references

    final AbstractVehicleRequestMatcher vehicleRequestMatcher;
    final DrivebyRequestStopper drivebyRequestStopper;
    final AbstractVehicleDestMatcher abstractVehicleDestMatcher;

    private HungarianDispatcher( //
                                 AVDispatcherConfig avDispatcherConfig, //
                                 TravelTime travelTime, //
                                 ParallelLeastCostPathCalculator parallelLeastCostPathCalculator, //
                                 EventsManager eventsManager, //
                                 Network network) {
        super(avDispatcherConfig, travelTime, parallelLeastCostPathCalculator, eventsManager);
        this.network = network;
        linkReferences = new HashSet<>(network.getLinks().values());
        vehicleRequestMatcher = new InOrderOfArrivalMatcher(this::setAcceptRequest);
        DISPATCH_PERIOD = Integer.parseInt(avDispatcherConfig.getParams().get("dispatchPeriod"));
        drivebyRequestStopper = new DrivebyRequestStopper(this::setVehicleDiversion);
        abstractVehicleDestMatcher = new HungarBiPartVehicleDestMatcher();
    }

    int total_matchedRequests = 0;

    @Override
    public void redispatch(double now) {
        final long round_now = Math.round(now);
        total_matchedRequests += vehicleRequestMatcher.match(getStayVehicles(), getAVRequestsAtLinks());
        System.out.println(getClass().getSimpleName() + " @" + round_now + " mr = " + total_matchedRequests);

        {
            System.out.println(String.format("%s BEG @%6d   MR=%6d   AT=%6d   do=%6d   um=%6d == %6d", //
                    getClass().getSimpleName().substring(0, 8), //
                    round_now, //
                    total_matchedRequests, //
                    0,0, //
                    getAVRequestsAtLinks().values().stream().flatMap(s->s.stream()).count(), //
                    getAVRequestsAtLinks().values().stream().mapToInt(List::size).sum() //
            ));
        }


        if (round_now % DISPATCH_PERIOD == 0) {
            {   // match a vehicle at a customer


                // for all remaining vehicles and requests, perform a bipartite matching

                Map<Link, List<AVRequest>> requests = getAVRequestsAtLinks();
                // call getDivertableVehicles again to get remaining vehicles
                Collection<VehicleLinkPair> divertableVehicles = getDivertableVehicles();

                // Save request in list which is needed for abstractVehicleDestMatcher
                List<Link> requestlocs =
                        requests.values()
                                .stream()
                                .flatMap(List::stream) // all requests
                                .map(AVRequest::getFromLink) // all from links of all requests with multiplicity
                                .collect(Collectors.toList());

                // find the Euclidean bipartite matching for all vehicles using the Hungarian method
                System.out.println("optimizing over "+divertableVehicles.size()+" vehicles and "+requestlocs.size() + " requests.");
                Map<VehicleLinkPair, Link> hungarianmatches = abstractVehicleDestMatcher.match(divertableVehicles, requestlocs);

                // use the result to setVehicleDiversion
                hungarianmatches.entrySet().forEach(this::setVehicleDiversion);
            }
        }

        {
            System.out.println(String.format("%s END @%6d   MR=%6d   AT=%6d   do=%6d   um=%6d == %6d", //
                    getClass().getSimpleName().substring(0, 8), //
                    round_now, //
                    total_matchedRequests, //
                    0,0,//
                    getAVRequestsAtLinks().values().stream().flatMap(s->s.stream()).count(), //
                    getAVRequestsAtLinks().values().stream().mapToInt(List::size).sum() //
            ));
        }



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
            return new HungarianDispatcher( //
                    config, travelTime, router, eventsManager, network);
        }
    }
}
