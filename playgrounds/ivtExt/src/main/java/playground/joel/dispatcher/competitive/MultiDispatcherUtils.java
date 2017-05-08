package playground.joel.dispatcher.competitive;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.router.util.TravelTime;
import playground.clruch.dispatcher.EdgyDispatcher;
import playground.clruch.dispatcher.HungarianDispatcher;
import playground.clruch.dispatcher.core.UniversalDispatcher;
import playground.clruch.dispatcher.utils.AbstractRequestSelector;
import playground.clruch.utils.GlobalAssert;
import playground.joel.dispatcher.single_heuristic.NewSingleHeuristicDispatcher;
import playground.sebhoerl.avtaxi.config.AVDispatcherConfig;
import playground.sebhoerl.avtaxi.config.AVGeneratorConfig;
import playground.sebhoerl.avtaxi.dispatcher.AVDispatcher;
import playground.sebhoerl.plcpc.ParallelLeastCostPathCalculator;

import java.util.HashSet;
import java.util.Iterator;

/**
 * Created by Joel on 04.05.2017.
 */
public abstract class MultiDispatcherUtils {

    public static void redispatchStep(MultiDispatcher multi, double now, long round_now, HashSet<AVDispatcher> dispatchers) {
        Iterator<AVDispatcher> dispatcher = dispatchers.iterator();
        Integer dispatcherNum = 0;
        while(dispatcher.hasNext())
        {
            boolean knownDispatcher = false;
            AVDispatcher current = dispatcher.next();
            if (current instanceof HungarianDispatcher) {
                ((HungarianDispatcher) current).redispatchStep(round_now, multi, multi.supplier(dispatcherNum));
                knownDispatcher = true;
            }
            /* TODO: adapt other dispatchers
            if (current instanceof EdgyDispatcher) {
                ((EdgyDispatcher) current).redispatchStep(now);
                knownDispatcher = true;
            }
            if(current instanceof LPFeedbackLIPDispatcher) {
                ((LPFeedbackLIPDispatcher) current).redispatchStep();
                knownDispatcher = true;
            }
            if(current instanceof LPFeedforwardDispatcher) {
                ((LPFeedforwardDispatcher) current).redispatchStep(round_now);
                knownDispatcher = true;
            }
            if (current instanceof NewSingleHeuristicDispatcher) {
                ((NewSingleHeuristicDispatcher) current).redispatchStep();
                knownDispatcher = true;
            }
            */
            GlobalAssert.that(knownDispatcher);
            dispatcherNum++;
        }
    }

    public static String getInfoLine(HashSet<AVDispatcher> dispatchers) {
        String infoLine = "";
        Iterator<AVDispatcher> dispatcher = dispatchers.iterator();
        while (dispatcher.hasNext()) {
            boolean knownDispatcher = false;
            AVDispatcher current = dispatcher.next();
            if(current instanceof HungarianDispatcher) {
                infoLine = ((HungarianDispatcher) current).getInfoLine();
                knownDispatcher = true;
            }
            if(current instanceof EdgyDispatcher) {
                infoLine = ((EdgyDispatcher) current).getInfoLine();
                knownDispatcher = true;
            }
            /* TODO: adapt other dispatchers
            if(current instanceof LPFeedbackLIPDispatcher) {
                infoLine = ((LPFeedbackLIPDispatcher) current).getInfoLine();
                knownDispatcher = true;
            }
            if(current instanceof LPFeedforwardDispatcher) {
                infoLine = ((LPFeedforwardDispatcher) current).getInfoLine();
                knownDispatcher = true;
            }
            */
            if(current instanceof NewSingleHeuristicDispatcher) {
                infoLine = ((NewSingleHeuristicDispatcher) current).getInfoLine();
                knownDispatcher = true;
            }
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
                                Network network, AbstractRequestSelector abstractRequestSelector) {
        AVDispatcher dispatcher;
        switch (dispatcherName) {
            case "HungarianDispatcher": dispatcher = new HungarianDispatcher(avDispatcherConfig, travelTime, //
                    parallelLeastCostPathCalculator, eventsManager, network, abstractRequestSelector);
                break;
            case "EdgyDispatcher": dispatcher = new EdgyDispatcher(avDispatcherConfig, travelTime, //
                    parallelLeastCostPathCalculator, eventsManager, network);
                break;
            /* TODO: adapt other dispatchers
            case "LPFeedbackLIPDispatcher": dispatcher = new LPFeedbackLIPDispatcher(avDispatcherConfig, generatorConfig, //
                    travelTime, parallelLeastCostPathCalculator, eventsManager, virtualNetwork, //
                    abstractVirtualNodeDest, abstractVehicleDestMatcher);
                break;
            case "LPFeedforwardDispatcher": dispatcher = new LPFeedforwardDispatcher(avDispatcherConfig, generatorConfig, //
                    travelTime, parallelLeastCostPathCalculator, eventsManager, virtualNetwork, abstractVirtualNodeDest, //
                    abstractRequestSelector, abstractVehicleDestMatcher, arrivalInformationIn);
                break;
            */
            case "NewSingleHeuristicDispatcher": dispatcher = new NewSingleHeuristicDispatcher(avDispatcherConfig, //
                    travelTime, parallelLeastCostPathCalculator, eventsManager, network, abstractRequestSelector);
                break;
            default: dispatcher = null;
                GlobalAssert.that(false);
                break;
        }
        return dispatcher;
    }

}
