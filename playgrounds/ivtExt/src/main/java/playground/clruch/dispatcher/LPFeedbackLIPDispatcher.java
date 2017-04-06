/**
 * Dispatcher implementing the linear program from
 * Pavone, Marco, Stephen Smith, Emilio Frazzoli, and Daniela Rus. 2011.
 * “Load Balancing for Mobility-on-Demand Systems.” In Robotics: Science and Systems VII. doi:10.15607/rss.2011.vii.034.
 * <p>
 * <p>
 * Implemented by Claudio Ruch on 2017, 02, 25
 */


package playground.clruch.dispatcher;

import ch.ethz.idsc.tensor.*;
import ch.ethz.idsc.tensor.alg.Array;
import ch.ethz.idsc.tensor.red.Total;
import ch.ethz.idsc.tensor.sca.Floor;
import ch.ethz.idsc.tensor.sca.Round;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.gnu.glpk.glp_smcp;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.router.util.TravelTime;

import playground.clruch.dispatcher.core.PartitionedDispatcher;
import playground.clruch.dispatcher.core.VehicleLinkPair;
import playground.clruch.dispatcher.utils.*;
import playground.clruch.netdata.*;
import playground.clruch.utils.GlobalAssert;
import playground.sebhoerl.avtaxi.config.AVDispatcherConfig;
import playground.sebhoerl.avtaxi.config.AVGeneratorConfig;
import playground.sebhoerl.avtaxi.data.AVVehicle;
import playground.sebhoerl.avtaxi.dispatcher.AVDispatcher;
import playground.sebhoerl.avtaxi.framework.AVModule;
import playground.sebhoerl.avtaxi.passenger.AVRequest;
import playground.sebhoerl.plcpc.ParallelLeastCostPathCalculator;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LPFeedbackLIPDispatcher extends PartitionedDispatcher {
    public final int rebalancingPeriod;
    public final int redispatchPeriod;
    final AbstractVirtualNodeDest virtualNodeDest;
    final AbstractRequestSelector requestSelector;
    final AbstractVehicleDestMatcher vehicleDestMatcher;
    final Map<VirtualLink, Double> travelTimes;
    final int numberOfAVs;
    private int total_rebalanceCount  = 0;
    Tensor printVals = Tensors.empty();
    LPVehicleRebalancing lpVehicleRebalancing;


    public LPFeedbackLIPDispatcher( //
                                    AVDispatcherConfig config, //
                                    AVGeneratorConfig generatorConfig, //
                                    TravelTime travelTime, //
                                    ParallelLeastCostPathCalculator router, //
                                    EventsManager eventsManager, //
                                    VirtualNetwork virtualNetwork, //
                                    AbstractVirtualNodeDest abstractVirtualNodeDest, //
                                    AbstractRequestSelector abstractRequestSelector, //
                                    AbstractVehicleDestMatcher abstractVehicleDestMatcher, //
                                    Map<VirtualLink, Double> travelTimesIn
    ) {
        super(config, travelTime, router, eventsManager, virtualNetwork);
        this.virtualNodeDest = abstractVirtualNodeDest;
        this.requestSelector = abstractRequestSelector;
        this.vehicleDestMatcher = abstractVehicleDestMatcher;
        travelTimes = travelTimesIn;
        numberOfAVs = (int) generatorConfig.getNumberOfVehicles();
        rebalancingPeriod = Integer.parseInt(config.getParams().get("rebalancingPeriod"));
        redispatchPeriod = Integer.parseInt(config.getParams().get("redispatchPeriod"));
        // setup linear program
        lpVehicleRebalancing = new LPVehicleRebalancing(virtualNetwork,travelTimes);
    }


    @Override
    public void redispatch(double now) {
        // PART 0: match vehicles at a customer link
        new InOrderOfArrivalMatcher(this::setAcceptRequest) //
                .match(getStayVehicles(), getAVRequestsAtLinks());


        // PART I: rebalance all vehicles periodically
        final long round_now = Math.round(now);

        if (round_now % rebalancingPeriod == 0) {




            Map<VirtualNode, List<AVRequest>> requests = getVirtualNodeRequests();
            // II.i compute rebalancing vehicles and send to virtualNodes
            {
                Map<VirtualNode, List<VehicleLinkPair>> availableVehicles = getVirtualNodeDivertableNotRebalancingVehicles();

                // calculate desired vehicles per vNode
                int num_requests = requests.values().stream().mapToInt(List::size).sum();
                int vi_desired_num = (int) ((numberOfAVs - num_requests) / (double) virtualNetwork.getvNodesCount());
                GlobalAssert.that(vi_desired_num * virtualNetwork.getvNodesCount() <= numberOfAVs);
                Tensor vi_desiredT = Tensors.vector(i -> RationalScalar.of(vi_desired_num, 1), virtualNetwork.getvNodesCount());


                // calculate excess vehicles per virtual Node i, where v_i excess = vi_own - c_i = v_i + sum_j (v_ji) - c_i
                Map<VirtualNode, Set<AVVehicle>> v_ij_reb = getVirtualNodeRebalancingToVehicles();
                Map<VirtualNode, Set<AVVehicle>> v_ij_cust = getVirtualNodeArrivingWCustomerVehicles();
                Tensor vi_excessT = Array.zeros(virtualNetwork.getvNodesCount());
                for (VirtualNode virtualNode : availableVehicles.keySet()) {
                    int viExcessVal = availableVehicles.get(virtualNode).size()
                            + v_ij_reb.get(virtualNode).size()
                            + v_ij_cust.get(virtualNode).size()
                            - requests.get(virtualNode).size();
                    vi_excessT.set(RealScalar.of(viExcessVal), virtualNode.index);
                }


                // solve the linear program with updated right-hand side
                // fill right-hand-side
                Tensor rhs = vi_desiredT.subtract(vi_excessT);
                Tensor rebalanceCount2 = lpVehicleRebalancing.solveUpdatedLP(rhs);
                Tensor rebalanceCount = Round.of(rebalanceCount2);
                // TODO this should never become active, can be possibly removed later
                // assert that solution is integer and does not contain negative values
                GlobalAssert.that(!rebalanceCount.flatten(-1).map(Scalar.class::cast).map(s->s.number().doubleValue()).filter(e->e<0).findAny().isPresent());


                // ensure that not more vehicles are sent away than available
                Tensor feasibleRebalanceCount = returnFeasibleRebalance(rebalanceCount.unmodifiable(), availableVehicles);
                total_rebalanceCount += (Integer) ((Scalar) Total.of(Tensor.of(feasibleRebalanceCount.flatten(-1)))).number();

                // generate routing instructions for rebalancing vehicles
                Map<VirtualNode, List<Link>> destinationLinks = createvNodeLinksMap();

                // fill rebalancing destinations
                for (int i = 0; i < virtualNetwork.getvLinksCount(); ++i) {
                    VirtualLink virtualLink = this.virtualNetwork.getVirtualLink(i);
                    VirtualNode toNode = virtualLink.getTo();
                    VirtualNode fromNode = virtualLink.getFrom();
                    int numreb = (Integer) (feasibleRebalanceCount.Get(fromNode.index, toNode.index)).number();
                    List<Link> rebalanceTargets = virtualNodeDest.selectLinkSet(toNode, numreb);
                    destinationLinks.get(fromNode).addAll(rebalanceTargets);
                }


                // consistency check: rebalancing destination links must not exceed available vehicles in virtual node
                Map<VirtualNode, List<VehicleLinkPair>> finalAvailableVehicles = availableVehicles;
                GlobalAssert.that(!virtualNetwork.getVirtualNodes().stream()
                        .filter(v -> finalAvailableVehicles.get(v).size() < destinationLinks.get(v).size())
                        .findAny().isPresent());


                // send rebalancing vehicles using the setVehicleRebalance command
                for (VirtualNode virtualNode : destinationLinks.keySet()) {
                    Map<VehicleLinkPair, Link> rebalanceMatching = vehicleDestMatcher.match(availableVehicles.get(virtualNode), destinationLinks.get(virtualNode));
                    rebalanceMatching.keySet().forEach(v -> setVehicleRebalance(v, rebalanceMatching.get(v)));
                }

            }
            // close the LP to avoid data leaks
            // TODO can this be taken out to not delete the LP in every instance?
            //lpVehicleRebalancing.closeLP();
        }


        // Part II: outside rebalancing periods, permanently assign desitnations to vehicles using bipartite matching
        if (round_now % redispatchPeriod == 0) {
            printVals = HungarianDispatcher.globalBipartiteMatching(this, () -> getVirtualNodeDivertableNotRebalancingVehicles().values()
                    .stream().flatMap(v -> v.stream()).collect(Collectors.toList()));
        }
    }


    /**
     * @param rebalanceInput    entry i,j contains the rebalance input from virtualNode i to virtualNode j
     * @param availableVehicles the available vehicles per virtualNode
     * @return
     */
    private Tensor returnFeasibleRebalance(Tensor rebalanceInput, Map<VirtualNode, List<VehicleLinkPair>> availableVehicles) {
        Tensor feasibleRebalance = rebalanceInput.copy();

        for (int i = 0; i < virtualNetwork.getvNodesCount(); ++i) {
            // count number of outgoing vehicles per vNode
            double outgoingNmrvNode = 0.0;
            Tensor outgoingVehicles = rebalanceInput.get(i);
            for (int j = 0; j < virtualNetwork.getvNodesCount(); ++j) {
                outgoingNmrvNode =  outgoingNmrvNode + (Integer) ((RealScalar) outgoingVehicles.Get(j)).number();
            }
            int outgoingVeh = (int) outgoingNmrvNode;
            int finalI = i;
            int availableVehvNode = availableVehicles.get(availableVehicles.keySet().stream().filter(v -> v.index == finalI).findAny().get()).size();
            // if number of outoing vehicles too small, reduce proportionally
            if (availableVehvNode < outgoingVeh) {
                long shrinkingFactor = ((long) availableVehvNode / ((long) outgoingVeh));
                Tensor newRow = Floor.of(rebalanceInput.get(i).multiply(RealScalar.of(shrinkingFactor)));
                feasibleRebalance.set(newRow, i);
            }
        }
        return feasibleRebalance;
    }


    @Override
    public String getInfoLine() {
        return String.format("%s RV=%s H=%s", //
                super.getInfoLine(), //
                total_rebalanceCount, //
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

        public static VirtualNetwork virtualNetwork;
        public static Map<VirtualLink, Double> travelTimes;

        @Override
        public AVDispatcher createDispatcher(AVDispatcherConfig config, AVGeneratorConfig generatorConfig) {

            AbstractVirtualNodeDest abstractVirtualNodeDest = new KMeansVirtualNodeDest();
            AbstractRequestSelector abstractRequestSelector = new OldestRequestSelector();
            AbstractVehicleDestMatcher abstractVehicleDestMatcher = new HungarBiPartVehicleDestMatcher();

            

			final File virtualnetworkDir = new File(config.getParams().get("virtualNetworkDirectory"));
			GlobalAssert.that(virtualnetworkDir.isDirectory());
			{
				final File virtualnetworkFile = new File(virtualnetworkDir, "virtualNetwork.xml");
	            System.out.println("" + virtualnetworkFile.getAbsoluteFile());
	            virtualNetwork = VirtualNetworkLoader.fromXML(network, virtualnetworkFile);
	            travelTimes = vLinkDataReader.fillvLinkData(virtualnetworkFile, virtualNetwork, "Ttime");
			}



            return new LPFeedbackLIPDispatcher(
                    config,
                    generatorConfig,
                    travelTime,
                    router,
                    eventsManager,
                    virtualNetwork,
                    abstractVirtualNodeDest,
                    abstractRequestSelector,
                    abstractVehicleDestMatcher,
                    travelTimes
            );
        }
    }


}




