package playground.clruch.dispatcher;

class ConsensusDispatcherDFR{
	
}


///**
// * Dispatcher implementing the linear program from
// * Pavone, Marco, Stephen Smith, Emilio Frazzoli, and Daniela Rus. 2011.
// * “Load Balancing for Mobility-on-Demand Systems.” In Robotics: Science and Systems VII. doi:10.15607/rss.2011.vii.034.
// * <p>
// * <p>
// * Implemented by Claudio Ruch on 2017, 02, 25
// */
//
//package playground.clruch.dispatcher;
//
//import java.io.File;
//import java.util.*;
//import java.util.stream.Collectors;
//
//import org.matsim.api.core.v01.network.Link;
//import org.matsim.api.core.v01.network.Network;
//import org.matsim.api.core.v01.population.Population;
//import org.matsim.core.api.experimental.events.EventsManager;
//import org.matsim.core.router.util.TravelTime;
//
//import com.google.inject.Inject;
//import com.google.inject.name.Named;
//
//import ch.ethz.idsc.tensor.RealScalar;
//import ch.ethz.idsc.tensor.Tensor;
//import ch.ethz.idsc.tensor.alg.Array;
//import ch.ethz.idsc.tensor.red.Variance;
//import playground.clruch.dispatcher.core.PartitionedDispatcher;
//import playground.clruch.dispatcher.core.VehicleLinkPair;
//import playground.clruch.dispatcher.utils.AbstractRequestSelector;
//import playground.clruch.dispatcher.utils.AbstractVehicleDestMatcher;
//import playground.clruch.dispatcher.utils.AbstractVirtualNodeDest;
//import playground.clruch.dispatcher.utils.ArrivalInformation;
//import playground.clruch.dispatcher.utils.HungarBiPartVehicleDestMatcher;
//import playground.clruch.dispatcher.utils.InOrderOfArrivalMatcher;
//import playground.clruch.dispatcher.utils.KMeansVirtualNodeDest;
//import playground.clruch.dispatcher.utils.OldestRequestSelector;
//import playground.clruch.netdata.VirtualLink;
//import playground.clruch.netdata.VirtualNetwork;
//import playground.clruch.netdata.VirtualNetworkLoader;
//import playground.clruch.netdata.VirtualNode;
//import playground.clruch.netdata.vLinkDataReader;
//import playground.clruch.utils.GlobalAssert;
//import playground.clruch.utils.SafeConfig;
//import playground.sebhoerl.avtaxi.config.AVDispatcherConfig;
//import playground.sebhoerl.avtaxi.config.AVGeneratorConfig;
//import playground.sebhoerl.avtaxi.data.AVVehicle;
//import playground.sebhoerl.avtaxi.dispatcher.AVDispatcher;
//import playground.sebhoerl.avtaxi.framework.AVModule;
//import playground.sebhoerl.avtaxi.generator.PopulationDensityGenerator;
//import playground.sebhoerl.avtaxi.passenger.AVRequest;
//import playground.sebhoerl.plcpc.ParallelLeastCostPathCalculator;
//
///**
// * {@link PopulationDensityGenerator}
// */
//public class ConsensusDispatcherDFR extends PartitionedDispatcher {
//    public static final String KEY_REBALANCINGPERIOD = "rebalancingPeriod";
//    public static final String KEY_VIRTUALNETWORKDIRECTORY = "virtualNetworkDirectory";
//    public static final String KEY_DTEXTENSION = "dtExtension";
//    public static final String KEY_WEIGHTSEXTENSION = "weightsExtension";
//    public static final String KEY_FEEDBACKTERM = "feedbackTerm";
//
//    private final int rebalancingPeriod;
//    final AbstractVirtualNodeDest virtualNodeDest;
//    final AbstractRequestSelector requestSelector;
//    final AbstractVehicleDestMatcher vehicleDestMatcher;
//    final ArrivalInformation arrivalInformation;
//    private final Map<VirtualLink, Double> rebalanceFloating;
//    private final Map<VirtualLink, Double> vLinkWeights;
//    private int rebCount = 0;
//
//    private final FeedbackTerm feebackTerm;
//
//    @ Deprecated
//    public ConsensusDispatcherDFR( //
//                                   AVDispatcherConfig config, //
//                                   AVGeneratorConfig generatorConfig, //
//                                   TravelTime travelTime, //
//                                   ParallelLeastCostPathCalculator router, //
//                                   EventsManager eventsManager, //
//                                   VirtualNetwork virtualNetwork, //
//                                   AbstractVirtualNodeDest abstractVirtualNodeDest, //
//                                   AbstractRequestSelector abstractRequestSelector, //
//                                   AbstractVehicleDestMatcher abstractVehicleDestMatcher, //
//                                   Map<VirtualLink, Double> linkWeightsIn, //
//                                   ArrivalInformation arrivalInformation) {
//        super(config, travelTime, router, eventsManager, virtualNetwork);
//
//        this.virtualNodeDest = abstractVirtualNodeDest;
//        this.requestSelector = abstractRequestSelector;
//        this.vehicleDestMatcher = abstractVehicleDestMatcher;
//        rebalanceFloating = new HashMap<>();
//        for (VirtualLink virtualLink : virtualNetwork.getVirtualLinks())
//            rebalanceFloating.put(virtualLink, 0.0);
//        vLinkWeights = linkWeightsIn;
//        this.arrivalInformation = arrivalInformation;
//        rebalancingPeriod = Integer.parseInt(config.getParams().get(KEY_REBALANCINGPERIOD));
//
//        feebackTerm = FeedbackTerm.valueOf(
//                config.getParams().get(KEY_FEEDBACKTERM));
//    }
//
//    @Override
//    public void redispatch(double now) {
//        // A: outside rebalancing periods, permanently assign vehicles to requests if they have arrived at a customer
//        // i.e. stay on the same link
//        new InOrderOfArrivalMatcher(this::setAcceptRequest) //
//                .match(getStayVehicles(), getAVRequestsAtLinks());
//        // Map<VirtualNode, List<VehicleLinkPair>> availableVehicles = getVirtualNodeAvailableNotRebalancingVehicles();
//
//        // B: redispatch all vehicles
//        final long round_now = Math.round(now);
//        if (round_now % rebalancingPeriod == 0) {
//            Map<VirtualNode, List<AVRequest>> requests = getVirtualNodeRequests();
//            // II.i compute rebalancing vehicles and send to virtualNodes
//            {
//                // TODO: ensure that a rebalanced vehicle is then under the control of the to-virtualNode and can be dispatched there.
//                Map<VirtualNode, List<VehicleLinkPair>> availableVehicles = getVirtualNodeDivertableNotRebalancingVehicles();
//
//                Tensor vector = Array.zeros(availableVehicles.size());
//
//                availableVehicles.entrySet().stream().forEach(e -> vector.set(RealScalar.of(e.getValue().size()), e.getKey().index));
//                System.out.println(vector.toString());
//                System.out.println("variance=" + Variance.ofVector(vector));
//
//                // Calculate the excess vehicles per virtual Node i, where v_i excess = vi_own - c_i = v_i + sum_j (v_ji) - c_i
//                // TODO check if sum_j (v_ji) also contains the customer vehicles travelling to v_i and add if so.
//                Map<VirtualNode, Integer> vi_excess = new HashMap<>();
//                Map<VirtualNode, Set<AVVehicle>> v_ij_reb = getVirtualNodeRebalancingToVehicles();
//                // Map<VirtualNode, Set<AVVehicle>> v_ij_cust = getVirtualNodeArrivingWCustomerVehicles();
//                for (VirtualNode virtualNode : availableVehicles.keySet()) {
//                    vi_excess.put(virtualNode, //
//                            availableVehicles.get(virtualNode).size() + //
//                                    v_ij_reb.get(virtualNode).size() - //
//                                    requests.get(virtualNode).size());
//                }
//
//                // 1 Calculate the rebalancing action for every virtual link
//                Map<VirtualLink, Integer> rebalanceCount = new HashMap<>();
//                {
//                    for (VirtualLink vLink : virtualNetwork.getVirtualLinks()) {
//                        // compute imbalance on nodes of link
//                        // if(availableVehicles.containsKey(vlink))
//                        int imbalanceFrom = -vi_excess.get(vLink.getFrom());
//                        int imbalanceTo = -vi_excess.get(vLink.getTo());
//                        double vehicles_From_to_To = 0.0;
//                        // compute the rebalancing vehicles
//                        // TODO confirm that correct
//
//                        switch (feebackTerm) {
//                            case LDX: {
//                                double lambdaTo = arrivalInformation.getLambdaforTime((int)now, vLink.getTo().index).number().doubleValue();
//                                double lambdaFrom = arrivalInformation.getLambdaforTime((int)now, vLink.getFrom().index).number().doubleValue();
//                                //long popSize = arrivalInformation.populationSize;
//                                long popSize = 1;
//                                lambdaTo = Math.max(lambdaTo, 1);
//                                lambdaFrom = Math.max(lambdaFrom, 1);
//                                // System.out.println();
//                                // lambda_dummy_to = lambda_dummy_from = 1;
//                                vehicles_From_to_To = //
//                                        rebalancingPeriod * (double) popSize * vLinkWeights.get(vLink) * ( //
//                                                (double) imbalanceTo / lambdaTo - //
//                                                        (double) imbalanceFrom / lambdaFrom) + //
//                                                rebalanceFloating.get(vLink);
//                                break;
//                            }
//                            case LX: {
//
//                                vehicles_From_to_To = //
//                                        rebalancingPeriod * vLinkWeights.get(vLink) * ( //
//                                                (double) imbalanceTo - //
//                                                        (double) imbalanceFrom) + //
//                                                rebalanceFloating.get(vLink);
//                                break;
//                            }
//                        }
//
//                        double lambdaTo = arrivalInformation.getLambdaforTime(now, vLink.getTo().index).number().doubleValue();
//                        double lambdaFrom = arrivalInformation.getLambdaforTime(now, vLink.getFrom().index).number().doubleValue();
//
//                        lambdaTo = Math.max(lambdaTo, 1);
//                        lambdaFrom = Math.max(lambdaFrom, 1);
//                        // System.out.println();
//                        // lambda_dummy_to = lambda_dummy_from = 1;
//                        double vehicles_From_to_To = //
//                                rebalancingPeriod * vLinkWeights.get(vLink) * ( //
//                                        (double) imbalanceTo / lambdaTo - //
//                                                (double) imbalanceFrom / lambdaFrom) + //
//                                        rebalanceFloating.get(vLink);
//
//                        int rebalanceFromTo = (int) Math.round(vehicles_From_to_To);
//                        // GlobalAssert.that(rebalanceFromTo);
//                        double rebalanceRest = vehicles_From_to_To - (double) rebalanceFromTo;
//                        rebalanceCount.put(vLink, rebalanceFromTo);
//                        rebalanceFloating.put(vLink, rebalanceRest);
//                    }
//                }
//
//
//                // ensure that not more vehicles are sent away than available
//                Map<VirtualLink, Integer> feasibleRebalanceCount = rebalanceCount; // new HashMap<>();
//                /*
//                 * feasibleRebalanceCount = returnFeasibleRebalance(rebalanceCount, availableVehicles);
//                 */
//                {
//                    int posReb = feasibleRebalanceCount.values().stream().filter(v -> v > 0).mapToInt(v -> v).sum();
//                    int negReb = feasibleRebalanceCount.values().stream().filter(v -> v < 0).mapToInt(v -> v).sum();
//                    rebCount += posReb - negReb;
//                }
//                // DEBUGGING ENd
//
//                // generate routing instructions for rebalancing vehicles
//                Map<VirtualNode, List<Link>> destinationLinks = createvNodeLinksMap();
//
//                // fill rebalancing destinations
//                for (Map.Entry<VirtualLink, Integer> entry : feasibleRebalanceCount.entrySet()) {
//                    if (feasibleRebalanceCount.get(entry.getKey()) >= 0) {
//                        List<Link> rebalanceTargets = virtualNodeDest.selectLinkSet(entry.getKey().getTo(), entry.getValue());
//                        destinationLinks.get(entry.getKey().getFrom()).addAll(rebalanceTargets);
//                    } else {
//                        List<Link> rebalanceTargets = virtualNodeDest.selectLinkSet(entry.getKey().getFrom(), -entry.getValue());
//                        destinationLinks.get(entry.getKey().getTo()).addAll(rebalanceTargets);
//                    }
//                }
//
//                // consistency check: rebalancing destination links must not exceed available vehicles in virtual node
//                if (virtualNetwork.getVirtualNodes().stream().filter(v -> availableVehicles.get(v).size() < destinationLinks.get(v).size()).findAny().isPresent()) {
//                    System.out.print("too many verhilces sent;");
//                }
//
//                // send rebalancing vehicles using the setVehicleRebalance command
//                for (VirtualNode virtualNode : destinationLinks.keySet()) {
//                    Map<VehicleLinkPair, Link> rebalanceMatching = vehicleDestMatcher.match(availableVehicles.get(virtualNode), destinationLinks.get(virtualNode));
//                    rebalanceMatching.keySet().forEach(v -> setVehicleRebalance(v, rebalanceMatching.get(v)));
//                }
//            }
//
//            // II.ii if vehilces remain in vNode, send to customers
//            {
//                // collect destinations per vNode
//                Map<VirtualNode, List<Link>> destinationLinks = createvNodeLinksMap();
//
//                for (VirtualNode vNode : virtualNetwork.getVirtualNodes()) {
//                    destinationLinks.get(vNode).addAll( // stores from links
//                            requests.get(vNode).stream().map(AVRequest::getFromLink).collect(Collectors.toList()));
//                }
//
//                // collect available vehicles per vNode
//                Map<VirtualNode, List<VehicleLinkPair>> availableVehicles = getVirtualNodeDivertableNotRebalancingVehicles();
//
//                // assign destinations to the available vehicles
//                {
//                    GlobalAssert.that(availableVehicles.keySet().containsAll(virtualNetwork.getVirtualNodes()));
//                    GlobalAssert.that(destinationLinks.keySet().containsAll(virtualNetwork.getVirtualNodes()));
//
//                    // DO NOT PUT PARALLEL anywhere in this loop !
//                    for (VirtualNode virtualNode : virtualNetwork.getVirtualNodes())
//                        vehicleDestMatcher //
//                                .match(availableVehicles.get(virtualNode), destinationLinks.get(virtualNode)) //
//                                .entrySet().stream().forEach(this::setVehicleDiversion);
//                }
//            }
//
//        }
//    }
//
//    @Deprecated
//    private Map<VirtualLink, Integer> returnFeasibleRebalance(Map<VirtualLink, Integer> rebalanceInput, Map<VirtualNode, List<VehicleLinkPair>> availableVehicles) {
//        Map<VirtualLink, Integer> feasibleRebalance = new HashMap<>();
//        feasibleRebalance = rebalanceInput;
//
//        // for every vNode check if enough vehicles are available to rebalance
//        for (VirtualNode virtualNode : virtualNetwork.getVirtualNodes()) {
//
//            // count outgoing rebalancing vehicles from the vNode
//            int totRebVecFromvNode = 0;
//            for (VirtualLink vLink : rebalanceInput.keySet()) {
//                if (vLink.getFrom().equals(virtualNode) || rebalanceInput.get(vLink) >= 0) {
//                    totRebVecFromvNode = totRebVecFromvNode + rebalanceInput.get(vLink);
//                }
//                if (vLink.getTo().equals(virtualNode) || rebalanceInput.get(vLink) < 0) {
//                    totRebVecFromvNode = totRebVecFromvNode - rebalanceInput.get(vLink);
//                }
//            }
//
//            // TODO think if instead of shrinking factor just for some links vehicles should be sent instead (less wait time)
//            // adapt number of vehicles to be sent
//            if (availableVehicles.get(virtualNode).size() < totRebVecFromvNode) {
//                // calculate by how much to shrink
//                double shrinkingFactor = ((double) availableVehicles.get(virtualNode).size()) / ((double) totRebVecFromvNode);
//                // remove rebalancing vehicles
//                for (VirtualLink vLink : rebalanceInput.keySet()) {
//                    if (vLink.getFrom().equals(virtualNode) || rebalanceInput.get(vLink) >= 0) {
//                        int newIntRebCount = (int) Math.floor(rebalanceInput.get(vLink) * shrinkingFactor);
//                        int newLeftOver = rebalanceInput.get(vLink) - newIntRebCount;
//                        feasibleRebalance.put(vLink, newIntRebCount);
//                        double oldRebFloating = rebalanceFloating.get(vLink);
//                        rebalanceFloating.put(vLink, oldRebFloating + (double) newLeftOver);
//                    }
//                    if (vLink.getTo().equals(virtualNode) || rebalanceInput.get(vLink) < 0) {
//                        int newIntRebCount = (int) Math.floor(rebalanceInput.get(vLink) * shrinkingFactor);
//                        int newLeftOver = rebalanceInput.get(vLink) - newIntRebCount;
//                        feasibleRebalance.put(vLink, newIntRebCount);
//                        double oldRebFloating = rebalanceFloating.get(vLink);
//                        rebalanceFloating.put(vLink, oldRebFloating + (double) newLeftOver);
//                    }
//                }
//            }
//        }
//        return feasibleRebalance;
//    }
//
//    @Override
//    public String getInfoLine() {
//        return String.format("%s RE=%5d", //
//                super.getInfoLine(), //
//                rebCount //
//        );
//    }
//
//    /**
//     * FIXME in {@link PopulationDensityGenerator}
//     */
//
//    public static class Factory implements AVDispatcherFactory {
//        @Inject
//        @Named(AVModule.AV_MODE)
//        private ParallelLeastCostPathCalculator router;
//
//        @Inject
//        @Named(AVModule.AV_MODE)
//        private TravelTime travelTime;
//
//        @Inject
//        private EventsManager eventsManager;
//
//        @Inject
//        private Network network;
//
//        @Inject
//        private Population population;
//
//        public static VirtualNetwork virtualNetwork;
//        public static Map<VirtualLink, Double> linkWeights;
//
//        @Override
//        public AVDispatcher createDispatcher(AVDispatcherConfig config, AVGeneratorConfig generatorConfig) {
//
//            AbstractVirtualNodeDest abstractVirtualNodeDest = new KMeansVirtualNodeDest();
//            AbstractRequestSelector abstractRequestSelector = new OldestRequestSelector();
//            AbstractVehicleDestMatcher abstractVehicleDestMatcher = new HungarBiPartVehicleDestMatcher();
//            // ---
//            GlobalAssert.that(config.getParams().containsKey(KEY_VIRTUALNETWORKDIRECTORY));
//            GlobalAssert.that(config.getParams().containsKey(KEY_DTEXTENSION));
//            // ---
//            final File virtualnetworkDir = new File(config.getParams().get(KEY_VIRTUALNETWORKDIRECTORY));
//            GlobalAssert.that(virtualnetworkDir.isDirectory());
//            // ---
//            {
//                final File virtualnetworkFile = new File(virtualnetworkDir, "virtualNetwork_nghbr.xml");
//                GlobalAssert.that(virtualnetworkFile.isFile());
//                virtualNetwork = VirtualNetworkLoader.fromXML(network, virtualnetworkFile);
//            }
//            // ---
//            {
//                final String string = "consensusWeights_" + config.getParams().get(KEY_WEIGHTSEXTENSION) + ".xml";
//                final File linkWeightsXML = new File(virtualnetworkDir, string);
//                GlobalAssert.that(linkWeightsXML.isFile());
//                linkWeights = vLinkDataReader.fillvLinkData(linkWeightsXML, virtualNetwork, "weight");
//            }
//
//            ArrivalInformation arrivalInformation = null;
//            {
//                final String ext = config.getParams().get(KEY_DTEXTENSION);
//                final File lambdaXML = new File(virtualnetworkDir, "poissonParameters_" + ext + ".xml");
//                GlobalAssert.that(lambdaXML.isFile());
//                final File pijFile = new File(virtualnetworkDir, "transitionProbabilities_" + ext + ".xml");
//                GlobalAssert.that(pijFile.isFile());
//                final File alphaijFile = new File(virtualnetworkDir, "rebalancingRates_" + ext + ".xml");
//                GlobalAssert.that(alphaijFile.isFile());
//
//                try {
//                    long populationSize = population.getPersons().size();
//                    int rebalancingPeriod = Integer.parseInt(config.getParams().get("rebalancingPeriod"));
//                    arrivalInformation = new ArrivalInformation(virtualNetwork, lambdaXML, pijFile, alphaijFile, //
//                            populationSize, //
//                            rebalancingPeriod //
//                    );
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    GlobalAssert.that(false);
//                }
//            }
//
//            return new ConsensusDispatcherDFR(config, generatorConfig, travelTime, router, eventsManager, virtualNetwork, //
//                    abstractVirtualNodeDest, //
//                    abstractRequestSelector, //
//                    abstractVehicleDestMatcher, //
//                    linkWeights, //
//                    arrivalInformation);
//        }
//    }
//}
