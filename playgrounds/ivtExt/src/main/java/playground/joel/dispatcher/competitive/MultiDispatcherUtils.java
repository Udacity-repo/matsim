package playground.joel.dispatcher.competitive;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.router.util.TravelTime;
import playground.clruch.dispatcher.EdgyDispatcher;
import playground.clruch.dispatcher.HungarianDispatcher;
import playground.clruch.dispatcher.LPFeedbackLIPDispatcher;
import playground.clruch.dispatcher.LPFeedforwardDispatcher;
import playground.clruch.dispatcher.utils.*;
import playground.clruch.netdata.VirtualNetwork;
import playground.clruch.traveldata.TravelData;
import playground.clruch.utils.GlobalAssert;
import playground.joel.dispatcher.single_heuristic.NewSingleHeuristicDispatcher;
import playground.sebhoerl.avtaxi.config.AVDispatcherConfig;
import playground.sebhoerl.avtaxi.config.AVGeneratorConfig;
import playground.sebhoerl.avtaxi.dispatcher.AVDispatcher;
import playground.sebhoerl.plcpc.ParallelLeastCostPathCalculator;

import java.util.List;

/**
 * Created by Joel on 04.05.2017.
 */
public abstract class MultiDispatcherUtils {

    public static void rebalanceStep(MultiDispatcher multi, double now, long round_now, List<AVDispatcher> dispatchers) {
        for(int dispatcherNum = 0; dispatcherNum < dispatchers.size(); dispatcherNum++) {
            boolean knownDispatcher = false;
            AVDispatcher current = dispatchers.get(dispatcherNum);
            if (current instanceof HungarianDispatcher) {
                // System.out.println("triggered NO rebalance for Hungarian dispatcher " + dispatcherNum);
                knownDispatcher = true;
            }
            if (current instanceof EdgyDispatcher) {
                knownDispatcher = true;
            }
            if(current instanceof LPFeedbackLIPDispatcher) {
                //System.out.println("triggered rebalance for LPFedback dispatcher " + dispatcherNum);
                ((LPFeedbackLIPDispatcher) current).rebalanceStep(multi, //
                        multi.getVirtualNodeDivertableNotRebalancingVehicles(dispatcherNum), //
                        multi.getVirtualNodeRequests(), //
                        multi.getVirtualNodeRebalancingToVehicles(dispatcherNum), //
                        multi.getVirtualNodeArrivingWCustomerVehicles(dispatcherNum), //
                        multi.lpVehicleRebalancings.get(dispatcherNum));
                knownDispatcher = true;
            }
            if(current instanceof LPFeedforwardDispatcher) {
                //System.out.println("triggered rebalance for Feedforward dispatcher " + dispatcherNum);
                ((LPFeedforwardDispatcher) current).rebalanceStep(round_now, multi, //
                        multi.getVirtualNodeDivertableNotRebalancingVehicles(dispatcherNum));
                knownDispatcher = true;
            }
            /* TODO: adapt other dispatchers
            if (current instanceof NewSingleHeuristicDispatcher) {
                ((NewSingleHeuristicDispatcher) current).rebalanceStep();
                knownDispatcher = true;
            }
            */
            GlobalAssert.that(knownDispatcher);
        }
    }

    public static void redispatchStep(MultiDispatcher multi, double now, long round_now, List<AVDispatcher> dispatchers) {
        for(int dispatcherNum = 0; dispatcherNum < dispatchers.size(); dispatcherNum++) {
            boolean knownDispatcher = false;
            AVDispatcher current = dispatchers.get(dispatcherNum);
            if (current instanceof HungarianDispatcher) {
                ((HungarianDispatcher) current).redispatchStep(round_now, multi, multi.supplier(dispatcherNum));
                knownDispatcher = true;
            }
            if (current instanceof EdgyDispatcher) {
                ((EdgyDispatcher) current).redispatchStep(now, multi, multi.supplier(dispatcherNum));
                knownDispatcher = true;
            }
            if(current instanceof LPFeedbackLIPDispatcher) {
                ((LPFeedbackLIPDispatcher) current).redispatchStep(round_now, multi, //
                        multi.virtualNotRebalancingSupplier(dispatcherNum));
                knownDispatcher = true;
            }
            if(current instanceof LPFeedforwardDispatcher) {
                ((LPFeedforwardDispatcher) current).redispatchStep(round_now, multi, //
                        multi.virtualNotRebalancingSupplier(dispatcherNum));
                knownDispatcher = true;
            }
            /* TODO: adapt other dispatchers
            if (current instanceof NewSingleHeuristicDispatcher) {
                ((NewSingleHeuristicDispatcher) current).redispatchStep();
                knownDispatcher = true;
            }
            */
            GlobalAssert.that(knownDispatcher);
        }
    }

    // TODO: needs to be fixed if in use
    public static String getInfoLine(List<AVDispatcher> dispatchers) {
        String infoLine = "";
        for(int dispatcherNum = 0; dispatcherNum < dispatchers.size(); dispatcherNum++) {
            boolean knownDispatcher = false;
            AVDispatcher current = dispatchers.get(dispatcherNum);
            if(current instanceof HungarianDispatcher) {
                infoLine = ((HungarianDispatcher) current).getInfoLine();
                knownDispatcher = true;
            }
            if(current instanceof EdgyDispatcher) {
                infoLine = ((EdgyDispatcher) current).getInfoLine();
                knownDispatcher = true;
            }
            if(current instanceof LPFeedbackLIPDispatcher) {
                infoLine = ((LPFeedbackLIPDispatcher) current).getInfoLine();
                knownDispatcher = true;
            }
            if(current instanceof LPFeedforwardDispatcher) {
                infoLine = ((LPFeedforwardDispatcher) current).getInfoLine();
                knownDispatcher = true;
            }
            /* TODO: adapt other dispatchers
            if(current instanceof NewSingleHeuristicDispatcher) {
                infoLine = ((NewSingleHeuristicDispatcher) current).getInfoLine();
                knownDispatcher = true;
            }
            */
            GlobalAssert.that(knownDispatcher);
        }
        return infoLine;
    }

    public static AVDispatcher newDispatcher (String dispatcherName, //
                                              AVDispatcherConfig avDispatcherConfig, //
                                              AVGeneratorConfig generatorConfig, //
                                              TravelTime travelTime, //
                                              ParallelLeastCostPathCalculator parallelLeastCostPathCalculator, //
                                              EventsManager eventsManager, //
                                              Network network, AbstractRequestSelector abstractRequestSelector, //
                                              AbstractVirtualNodeDest abstractVirtualNodeDest, //
                                              AbstractVehicleDestMatcher abstractVehicleDestMatcher, //
                                              VirtualNetwork virtualNetwork, //
                                              TravelData travelData) {
        AVDispatcher dispatcher;
        switch (dispatcherName) {
            case "HungarianDispatcher": dispatcher = new HungarianDispatcher(avDispatcherConfig, travelTime, //
                    parallelLeastCostPathCalculator, eventsManager, network, abstractRequestSelector);
                break;
            case "EdgyDispatcher": dispatcher = new EdgyDispatcher(avDispatcherConfig, travelTime, //
                    parallelLeastCostPathCalculator, eventsManager, network);
                break;
            case "LPFeedbackLIPDispatcher": dispatcher = new LPFeedbackLIPDispatcher(avDispatcherConfig, generatorConfig, //
                    travelTime, parallelLeastCostPathCalculator, eventsManager, virtualNetwork, //
                    abstractVirtualNodeDest, abstractVehicleDestMatcher);
                break;
            case "LPFeedforwardDispatcher": dispatcher = new LPFeedforwardDispatcher(avDispatcherConfig, generatorConfig, //
                    travelTime, parallelLeastCostPathCalculator, eventsManager, virtualNetwork, //
                    abstractVirtualNodeDest, abstractRequestSelector, abstractVehicleDestMatcher, travelData);
                break;
            /* TODO: adapt other dispatchers
            case "NewSingleHeuristicDispatcher": dispatcher = new NewSingleHeuristicDispatcher(avDispatcherConfig, //
                    travelTime, parallelLeastCostPathCalculator, eventsManager, network, abstractRequestSelector);
                break;
            */
            default: dispatcher = null;
                GlobalAssert.that(false);
                break;
        }
        return dispatcher;
    }

}
