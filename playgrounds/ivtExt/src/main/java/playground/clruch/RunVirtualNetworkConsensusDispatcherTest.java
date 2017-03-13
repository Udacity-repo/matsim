package playground.clruch;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import playground.clruch.dispatcher.ConsensusDispatcher;
import playground.clruch.dispatcher.utils.AbstractRequestSelector;
import playground.clruch.dispatcher.utils.AbstractVehicleDestMatcher;
import playground.clruch.dispatcher.utils.AbstractVirtualNodeDest;
import playground.clruch.netdata.VirtualLink;
import playground.clruch.netdata.VirtualNetwork;
import playground.clruch.netdata.VirtualNetworkLoader;
import playground.clruch.netdata.vLinkDataReader;
import playground.sebhoerl.avtaxi.config.AVDispatcherConfig;
import playground.sebhoerl.avtaxi.framework.AVConfigGroup;
import playground.sebhoerl.plcpc.ParallelLeastCostPathCalculator;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Map;

/**
 * Created by Claudio on 2/9/2017.
 */
@Deprecated
public class RunVirtualNetworkConsensusDispatcherTest {
    public static void main(String[] args) throws MalformedURLException {
        File configFile = new File(args[0]);
        final File dir = configFile.getParentFile();
        File virtualnetworkXML = new File(dir + "/virtualNetwork.xml");
        System.out.println("Looking for virtualNetwork.xml file at " + virtualnetworkXML.getAbsoluteFile());

        Config config = ConfigUtils.loadConfig(configFile.toString(), new AVConfigGroup());
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = scenario.getNetwork();

        if (virtualnetworkXML.isFile()) {
            // instantiate a virtual network for testing
            System.out.println("now creating VirtualNetwork based on XML file.");
            VirtualNetwork virtualNetwork = VirtualNetworkLoader.fromXML(network, virtualnetworkXML);
            virtualNetwork.printForTesting(network);

            //intstatiate a ConsensusDispatcher for testing
            AVDispatcherConfig AVDconfig = null;
            TravelTime travelTime = null;
            ParallelLeastCostPathCalculator router = null;
            EventsManager eventsManager = null;
            AbstractVirtualNodeDest abstractVirtualNodeDest = null;
            AbstractRequestSelector abstractRequestSelector = null;
            AbstractVehicleDestMatcher abstractVehicleDestMatcher = null;
            File linkWeightFile = new File(dir + "/consensusWeights.xml");
            final Map<VirtualLink, Double> linkWeights;
            linkWeights = vLinkDataReader.fillvLinkData(linkWeightFile,virtualNetwork,"weight");

            ConsensusDispatcher consensusDispatcher = new ConsensusDispatcher(
                    AVDconfig,
                    travelTime,
                    router,
                    eventsManager,
                    virtualNetwork,
                    abstractVirtualNodeDest,
                    abstractRequestSelector,
                    abstractVehicleDestMatcher,
                    linkWeights
            );
        } else {
            System.out.println("no virutalNetwork.xml file");
        }


    }

}
