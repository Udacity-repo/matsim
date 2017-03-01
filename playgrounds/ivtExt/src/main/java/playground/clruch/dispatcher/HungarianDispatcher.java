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
import playground.clruch.dispatcher.utils.HungarBiPartVehicleDestMatcher;
import playground.clruch.dispatcher.utils.InOrderOfArrivalMatcher;
import playground.sebhoerl.avtaxi.config.AVDispatcherConfig;
import playground.sebhoerl.avtaxi.dispatcher.AVDispatcher;
import playground.sebhoerl.avtaxi.framework.AVModule;
import playground.sebhoerl.avtaxi.passenger.AVRequest;
import playground.sebhoerl.plcpc.ParallelLeastCostPathCalculator;

import java.util.*;
import java.util.stream.Collectors;

public class HungarianDispatcher extends UniversalDispatcher {
    private static final int DISPATCH_PERIOD = 30;

    final Network network; // <- for verifying link references
    final Collection<Link> linkReferences; // <- for verifying link references

    final AbstractVehicleRequestMatcher vehicleRequestMatcher;

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
    }

    int total_matchedRequests = 0;

    @Override
    public void redispatch(double now) {
        total_matchedRequests += vehicleRequestMatcher.match(getStayVehicles(), getAVRequestsAtLinks());

        final long round_now = Math.round(now);
        if (round_now % DISPATCH_PERIOD == 0) {

            int num_abortTrip = 0;
            int num_driveOrder = 0;

            Map<Link, List<AVRequest>> requests = getAVRequestsAtLinks();

            { // see if any car is driving by a request. if so, then stay there to be matched!
                Collection<VehicleLinkPair> divertableVehicles = getDivertableVehicles();
                for (VehicleLinkPair vehicleLinkPair : divertableVehicles) {
                    Link link = vehicleLinkPair.getDivertableLocation();
                    if (requests.containsKey(link)) {
                        List<AVRequest> requestList = requests.get(link);
                        if (!requestList.isEmpty()) {
                            requestList.remove(0);
                            setVehicleDiversion(vehicleLinkPair, link);
                            ++num_abortTrip;
                        }
                    }
                }
            }


            { // for all remaining vehicles and requests, perform a bipartite matching

                // call getDivertableVehicles again to get remaining vehicles
                Collection<VehicleLinkPair> divertableVehicles = getDivertableVehicles();

                // Save request in list which is needed for abstractVehicleDestMatcher
                AbstractVehicleDestMatcher abstractVehicleDestMatcher = new HungarBiPartVehicleDestMatcher();
                List<Link> requestlocs =
                        requests.values()
                                .stream()
                                .flatMap(List::stream)
                                .map(AVRequest::getFromLink)
                                .collect(Collectors.toList());

                // find the Euclidean bipartite matching for all vehicles using the Hungarian method
                System.out.println("optimizing over "+divertableVehicles.size()+" vehicles and "+requestlocs.size() + " requests.");
                Map<VehicleLinkPair, Link> hungarianmatches = abstractVehicleDestMatcher.match(divertableVehicles, requestlocs);

                // use the result to setVehicleDiversion
                for (VehicleLinkPair vehicleLinkPair : hungarianmatches.keySet()) {
                    setVehicleDiversion(vehicleLinkPair, hungarianmatches.get(vehicleLinkPair));
                }
            }
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
        public AVDispatcher createDispatcher(AVDispatcherConfig config) {
            return new HungarianDispatcher( //
                    config, travelTime, router, eventsManager, network);
        }
    }
}
