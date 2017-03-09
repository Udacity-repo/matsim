package playground.clruch.dispatcher;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.router.util.TravelTime;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import playground.clruch.dispatcher.core.UniversalDispatcher;
import playground.clruch.dispatcher.core.VehicleLinkPair;
import playground.clruch.dispatcher.utils.AbstractVehicleRequestMatcher;
import playground.clruch.dispatcher.utils.DrivebyRequestStopper;
import playground.clruch.dispatcher.utils.InOrderOfArrivalMatcher;
import playground.sebhoerl.avtaxi.config.AVDispatcherConfig;
import playground.sebhoerl.avtaxi.config.AVGeneratorConfig;
import playground.sebhoerl.avtaxi.dispatcher.AVDispatcher;
import playground.sebhoerl.avtaxi.framework.AVModule;
import playground.sebhoerl.avtaxi.passenger.AVRequest;
import playground.sebhoerl.plcpc.ParallelLeastCostPathCalculator;

public class EdgyDispatcher extends UniversalDispatcher {
    /**
     * a large value of DISPATCH_PERIOD helps to quickly test the EdgyDispatcher
     */
    private static final int DISPATCH_PERIOD = 1;

    final Network network; // <- for verifying link references
    final Collection<Link> linkReferences; // <- for verifying link references

    final AbstractVehicleRequestMatcher vehicleRequestMatcher;
    final DrivebyRequestStopper drivebyRequestStopper;

    private EdgyDispatcher( //
            AVDispatcherConfig avDispatcherConfig, //
            TravelTime travelTime, //
            ParallelLeastCostPathCalculator parallelLeastCostPathCalculator, //
            EventsManager eventsManager, //
            Network network) {
        super(avDispatcherConfig, travelTime, parallelLeastCostPathCalculator, eventsManager);
        this.network = network;
        linkReferences = new HashSet<>(network.getLinks().values());
        vehicleRequestMatcher = new InOrderOfArrivalMatcher(this::setAcceptRequest);
        drivebyRequestStopper = new DrivebyRequestStopper(this::setVehicleDiversion);
    }

    /** verify that link references are present in the network */
    @SuppressWarnings("unused") // for verifying link references
    private void verifyLinkReferencesInvariant() {
        List<Link> testset = getDivertableVehicles().stream() //
                .map(VehicleLinkPair::getCurrentDriveDestination) //
                .filter(Objects::nonNull) //
                .collect(Collectors.toList());
        if (!linkReferences.containsAll(testset))
            throw new RuntimeException("network change 1");
        if (!linkReferences.containsAll(network.getLinks().values()))
            throw new RuntimeException("network change 2");
        if (0 < testset.size())
            System.out.println("network " + linkReferences.size() + " contains all " + testset.size());
    }

    int total_matchedRequests = 0;
    int total_abortTrip = 0;
    int total_driveOrder = 0;

    @Override
    public void redispatch(double now) {
        // verifyReferences(); // <- debugging only

        total_matchedRequests += vehicleRequestMatcher.match(getStayVehicles(), getAVRequestsAtLinks());

        final long round_now = Math.round(now);
        if (round_now % DISPATCH_PERIOD == 0) {

            total_abortTrip += drivebyRequestStopper.realize(getAVRequestsAtLinks(), getDivertableVehicles());

            { // TODO this should be replaceable by some naive matcher
                Iterator<AVRequest> requestIterator = getAVRequests().iterator();
                for (VehicleLinkPair vehicleLinkPair : getDivertableVehicles()) {
                    Link dest = vehicleLinkPair.getCurrentDriveDestination();
                    if (dest == null) { // vehicle in stay task
                        if (requestIterator.hasNext()) {
                            Link link = requestIterator.next().getFromLink();
                            setVehicleDiversion(vehicleLinkPair, link);
                            ++total_driveOrder;
                        } else
                            break;
                    }
                }
            }

            System.out.println(String.format("%s @%6d   MR=%6d   at=%6d   do=%6d", //
                    getClass().getSimpleName().substring(0, 8), //
                    round_now, //
                    total_matchedRequests, //
                    total_abortTrip, //
                    total_driveOrder));
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
            return new EdgyDispatcher( //
                    config, travelTime, router, eventsManager, network);
        }
    }
}
