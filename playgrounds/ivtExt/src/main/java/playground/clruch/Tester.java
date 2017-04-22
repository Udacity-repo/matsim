package playground.clruch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.trafficmonitoring.VrpTravelTimeModules;
import org.matsim.contrib.dynagent.run.DynQSimModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;

import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

import playground.joel.analysis.AnalyzeAll;
import playground.clruch.gfx.ReferenceFrame;
import playground.clruch.net.DatabaseModule;
import playground.clruch.net.MatsimStaticDatabase;
import playground.clruch.net.SimulationServer;
import playground.clruch.netdata.VirtualNetwork;
import playground.clruch.netdata.VirtualNetworkIO;
import playground.clruch.prep.TheApocalypse;
import playground.clruch.zurichAV.ZurichGenerator;
import playground.clruch.zurichAV.ZurichPlanStrategyProvider;
import playground.ivt.replanning.BlackListedTimeAllocationMutatorConfigGroup;
import playground.ivt.replanning.BlackListedTimeAllocationMutatorStrategyModule;
import playground.maalbert.analysis.AnalyzeMarc;
import playground.sebhoerl.avtaxi.framework.AVConfigGroup;
import playground.sebhoerl.avtaxi.framework.AVModule;
import playground.sebhoerl.avtaxi.framework.AVQSimProvider;
import playground.sebhoerl.avtaxi.framework.AVUtils;

/**
 * main entry point
 * 
 * only one ScenarioServer can run at one time, since a fixed network port is reserved to serve the
 * simulation status
 * 
 * if you wish to run multiple simulations at the same time use for instance {@link RunAVScenario}
 */
public class Tester {

    public static void main(String[] args) throws MalformedURLException, Exception {

        File configFile = new File("/home/clruch/Simulations/2017_04_06_Sioux_LPFeedback/av_config.xml");
        
        // load Network file
        Config config = ConfigUtils.loadConfig(configFile.toString());
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = scenario.getNetwork();
        
        
        
        VirtualNetwork virtualNetwork = VirtualNetworkIO.fromXML(network, new File("/home/clruch/Simulations/2017_04_06_Sioux_LPFeedback/vN_debug_v0/virtualNetwork.xml"));
        VirtualNetworkIO.toXML("/home/clruch/Simulations/2017_04_21_Zurich_LP/virtualNetwork/virtualNetwork.xml",virtualNetwork);

    }
}

//
//
//
//
//
//
// // BEGIN: CUSTOMIZE -----------------------------------------------
// // set manually depending on the scenario:
// int maxPopulationSize = 142381;
//
//
// // set to true in order to make server wait for at least 1 client, for instance viewer client
// boolean waitForClients = false;
//
// // END: CUSTOMIZE -------------------------------------------------
//
// // open server port for clients to connect to
// SimulationServer.INSTANCE.startAcceptingNonBlocking();
// SimulationServer.INSTANCE.setWaitForClients(waitForClients);
//
// DvrpConfigGroup dvrpConfigGroup = new DvrpConfigGroup();
// dvrpConfigGroup.setTravelTimeEstimationAlpha(0.05);
//
// File configFile = new File(args[0]);
// //Config config = ConfigUtils.loadConfig(configFile.toString(), new AVConfigGroup(),
// dvrpConfigGroup);
// Config config = ConfigUtils.loadConfig(configFile.toString(), new AVConfigGroup(),
// dvrpConfigGroup, new BlackListedTimeAllocationMutatorConfigGroup());
// Scenario scenario = ScenarioUtils.loadScenario(config);
// final Population population = scenario.getPopulation();
// MatsimStaticDatabase.initializeSingletonInstance( //
// scenario.getNetwork(), ReferenceFrame.IDENTITY);
//
//
//// // admissible Nodes sebhoerl
//// final Network network = scenario.getNetwork();
////
//// FileInputStream stream = new FileInputStream(ConfigGroup.getInputFileURL(config.getContext(),
// "nodes.list").getPath());
//// BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
////
//// final Set<Node> permissibleNodes = new HashSet<>();
//// final Set<Link> permissibleLinks = new HashSet<>();
////
//// reader.lines().forEach((String nodeId) ->
// permissibleNodes.add(network.getNodes().get(Id.createNodeId(nodeId))) );
//// permissibleNodes.forEach((Node node) -> permissibleLinks.addAll(node.getOutLinks().values()));
//// permissibleNodes.forEach((Node node) -> permissibleLinks.addAll(node.getInLinks().values()));
//// final Set<Link> filteredPermissibleLinks = permissibleLinks.stream().filter((l) ->
// l.getAllowedModes().contains("car")).collect(Collectors.toSet());
//
//
// TheApocalypse.decimatesThe(population).toNoMoreThan(maxPopulationSize).people();
// Controler controler = new Controler(scenario);
// controler.addOverridingModule(VrpTravelTimeModules.createTravelTimeEstimatorModule(0.05));
// controler.addOverridingModule(new DynQSimModule<>(AVQSimProvider.class));
// controler.addOverridingModule(new AVModule());
// controler.addOverridingModule(new DatabaseModule()); // added only to listen to iteration counter
// controler.addOverridingModule(new BlackListedTimeAllocationMutatorStrategyModule());
//
//
//// controler.addOverridingModule(new AbstractModule() {
//// @Override
//// public void install() {
//// bind(new TypeLiteral<Collection<Link>>()
// {}).annotatedWith(Names.named("zurich")).toInstance(filteredPermissibleLinks);
//// //AVUtils.registerDispatcherFactory(binder(), "ZurichDispatcher",
// ZurichDispatcher.ZurichDispatcherFactory.class);
//// AVUtils.registerGeneratorFactory(binder(), "ZurichGenerator",
// ZurichGenerator.ZurichGeneratorFactory.class);
////
//// addPlanStrategyBinding("ZurichModeChoice").toProvider(ZurichPlanStrategyProvider.class);
//// }
//// });
//
//
// controler.run();
//
// SimulationServer.INSTANCE.stopAccepting(); // close port
//
// AnalyzeAll.analyze(args);
// //AnalyzeMarc.analyze(args);
