package playground.sebhoerl.avtaxi.framework;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Named;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.dvrp.trafficmonitoring.VrpTravelTimeModules;
import org.matsim.core.config.Config;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.population.routes.RouteFactories;
import org.matsim.core.router.Dijkstra;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;

import playground.clruch.dispatcher.EdgyDispatcher;
import playground.clruch.dispatcher.HungarianDispatcher;
import playground.clruch.dispatcher.LPFeedbackLIPDispatcher;
import playground.clruch.dispatcher.LPFeedforwardDispatcher;
import playground.clruch.dispatcher.NotAsDumbDispatcher;
import playground.clruch.dispatcher.PulseDispatcher;
import playground.clruch.dispatcher.SelfishDispatcher;
import playground.fseccamo.dispatcher.MPCDispatcher_1;
import playground.joel.dispatcher.MultiGBM.MonoMultiGBMDispatcher;
import playground.joel.dispatcher.MultiGBM.PolyMultiGBMDispatcher;
import playground.joel.dispatcher.single_heuristic.NewSingleHeuristicDispatcher; // TODO: delete this or the other
import playground.maalbert.dispatcher.DFRDispatcher;
import playground.sebhoerl.avtaxi.config.AVConfig;
import playground.sebhoerl.avtaxi.config.AVConfigReader;
import playground.sebhoerl.avtaxi.config.AVGeneratorConfig;
import playground.sebhoerl.avtaxi.config.AVOperatorConfig;
import playground.sebhoerl.avtaxi.data.AVData;
import playground.sebhoerl.avtaxi.data.AVLoader;
import playground.sebhoerl.avtaxi.data.AVOperator;
import playground.sebhoerl.avtaxi.data.AVOperatorFactory;
import playground.sebhoerl.avtaxi.data.AVVehicle;
import playground.sebhoerl.avtaxi.dispatcher.multi_od_heuristic.MultiODHeuristic;
import playground.sebhoerl.avtaxi.dispatcher.single_fifo.SingleFIFODispatcher;
import playground.sebhoerl.avtaxi.dispatcher.single_heuristic.SingleHeuristicDispatcher;
import playground.sebhoerl.avtaxi.generator.AVGenerator;
import playground.sebhoerl.avtaxi.generator.PopulationDensityGenerator;
import playground.sebhoerl.avtaxi.generator.RandomDensityGenerator;
import playground.sebhoerl.avtaxi.replanning.AVOperatorChoiceStrategy;
import playground.sebhoerl.avtaxi.routing.AVParallelRouterFactory;
import playground.sebhoerl.avtaxi.routing.AVRoute;
import playground.sebhoerl.avtaxi.routing.AVRouteFactory;
import playground.sebhoerl.avtaxi.routing.AVRoutingModule;
import playground.sebhoerl.avtaxi.scoring.AVScoringFunctionFactory;
import playground.sebhoerl.plcpc.ParallelLeastCostPathCalculator;

public class AVModule extends AbstractModule {
    final static public String AV_MODE = "av";
    final static Logger log = Logger.getLogger(AVModule.class);

	@Override
	public void install() {
        addRoutingModuleBinding(AV_MODE).to(AVRoutingModule.class);
        bind(ScoringFunctionFactory.class).to(AVScoringFunctionFactory.class).asEagerSingleton();
        addControlerListenerBinding().to(AVLoader.class);

        bind(AVOperatorChoiceStrategy.class);
        addPlanStrategyBinding("AVOperatorChoice").to(AVOperatorChoiceStrategy.class);

        // Bind the AV travel time to the DVRP estimated travel time
        // TODO add different travel time to avoid congestions
        bind(TravelTime.class).annotatedWith(Names.named(AVModule.AV_MODE))
                .to(Key.get(TravelTime.class, Names.named(VrpTravelTimeModules.DVRP_ESTIMATED)));

        bind(VehicleType.class).annotatedWith(Names.named(AVModule.AV_MODE)).toInstance(VehicleUtils.getDefaultVehicleType());

        bind(AVOperatorFactory.class);
        bind(AVRouteFactory.class);

        configureDispatchmentStrategies();
        configureGeneratorStrategies();

        bind(AVParallelRouterFactory.class);
        addControlerListenerBinding().to(Key.get(ParallelLeastCostPathCalculator.class, Names.named(AVModule.AV_MODE)));
        addMobsimListenerBinding().to(Key.get(ParallelLeastCostPathCalculator.class, Names.named(AVModule.AV_MODE)));
	}

	@Provides @Singleton @Named(AVModule.AV_MODE)
	private ParallelLeastCostPathCalculator provideParallelLeastCostPathCalculator(AVConfigGroup config, AVParallelRouterFactory factory) {
        return new ParallelLeastCostPathCalculator((int) config.getParallelRouters(), factory);
    }

	private void configureDispatchmentStrategies() {
        /** dispatchers by sebhoerl */
        bind(SingleFIFODispatcher.Factory.class);
        bind(SingleHeuristicDispatcher.Factory.class);
        bind(MultiODHeuristic.Factory.class);
        
        AVUtils.bindDispatcherFactory(binder(), "SingleFIFO").to(SingleFIFODispatcher.Factory.class);
        AVUtils.bindDispatcherFactory(binder(), "SingleHeuristic").to(SingleHeuristicDispatcher.Factory.class);
        AVUtils.bindDispatcherFactory(binder(), "MultiOD").to(MultiODHeuristic.Factory.class);
        
        // ---BIND
        /** dispatchers for UniversalDispatcher */
        bind(PulseDispatcher.Factory.class);
        AVUtils.bindDispatcherFactory(binder(), PulseDispatcher.class.getSimpleName()).to(PulseDispatcher.Factory.class);
        
        bind(EdgyDispatcher.Factory.class);
        AVUtils.bindDispatcherFactory(binder(), EdgyDispatcher.class.getSimpleName()).to(EdgyDispatcher.Factory.class);

        bind(NotAsDumbDispatcher.Factory.class);
        AVUtils.bindDispatcherFactory(binder(),NotAsDumbDispatcher.class.getSimpleName()).to(NotAsDumbDispatcher.Factory.class);

        bind(HungarianDispatcher.Factory.class);
        AVUtils.bindDispatcherFactory(binder(), HungarianDispatcher.class.getSimpleName()).to(HungarianDispatcher.Factory.class);
        
        bind(SelfishDispatcher.Factory.class);
        AVUtils.bindDispatcherFactory(binder(), SelfishDispatcher.class.getSimpleName()).to(SelfishDispatcher.Factory.class);

        bind(NewSingleHeuristicDispatcher.Factory.class);
        AVUtils.bindDispatcherFactory(binder(), NewSingleHeuristicDispatcher.class.getSimpleName()).to(NewSingleHeuristicDispatcher.Factory.class);

        bind(MonoMultiGBMDispatcher.Factory.class);
        AVUtils.bindDispatcherFactory(binder(), MonoMultiGBMDispatcher.class.getSimpleName()).to(MonoMultiGBMDispatcher.Factory.class);

        bind(PolyMultiGBMDispatcher.Factory.class);
        AVUtils.bindDispatcherFactory(binder(), PolyMultiGBMDispatcher.class.getSimpleName()).to(PolyMultiGBMDispatcher.Factory.class);

        /** dispatchers for PartitionedDispatcher */
        //bind(ConsensusDispatcherDFR.Factory.class);
        bind(LPFeedbackLIPDispatcher.Factory.class);
        bind(LPFeedforwardDispatcher.Factory.class);
        bind(DFRDispatcher.Factory.class);
        //AVUtils.bindDispatcherFactory(binder(), ConsensusDispatcherDFR.class.getSimpleName()).to(ConsensusDispatcherDFR.Factory.class);
        AVUtils.bindDispatcherFactory(binder(), LPFeedbackLIPDispatcher.class.getSimpleName()).to(LPFeedbackLIPDispatcher.Factory.class);
        AVUtils.bindDispatcherFactory(binder(), LPFeedforwardDispatcher.class.getSimpleName()).to(LPFeedforwardDispatcher.Factory.class);
        AVUtils.bindDispatcherFactory(binder(), DFRDispatcher.class.getSimpleName()).to(DFRDispatcher.Factory.class);
        
        // MPC dispatcher
        bind(MPCDispatcher_1.Factory.class);
        AVUtils.bindDispatcherFactory(binder(), MPCDispatcher_1.class.getSimpleName()).to(MPCDispatcher_1.Factory.class);
        
    }

    private void configureGeneratorStrategies() {
        bind(PopulationDensityGenerator.Factory.class);
        AVUtils.bindGeneratorFactory(binder(), "PopulationDensity").to(PopulationDensityGenerator.Factory.class);

        bind(RandomDensityGenerator.Factory.class);
        AVUtils.bindGeneratorFactory(binder(), "RandomDensity").to(RandomDensityGenerator.Factory.class);
    }

    @Provides
    RouteFactories provideRouteFactories(AVRouteFactory routeFactory) {
        RouteFactories factories = new RouteFactories();
        factories.setRouteFactory(AVRoute.class, routeFactory);
        return factories;
    }

	@Provides @Named(AVModule.AV_MODE)
    LeastCostPathCalculator provideLeastCostPathCalculator(Network network, @Named(VrpTravelTimeModules.DVRP_ESTIMATED) TravelTime travelTime) {
        return new Dijkstra(network, new OnlyTimeDependentTravelDisutility(travelTime), travelTime);
    }

	@Provides @Singleton
    Map<Id<AVOperator>, AVOperator> provideOperators(AVConfig config, AVOperatorFactory factory) {
        Map<Id<AVOperator>, AVOperator> operators = new HashMap<>();

        for (AVOperatorConfig oc : config.getOperatorConfigs()) {
            operators.put(oc.getId(), factory.createOperator(oc.getId(), oc));
        }

        return operators;
    }

    @Provides @Singleton
    AVConfig provideAVConfig(Config config, AVConfigGroup configGroup) {
        File basePath = new File(config.getContext().getPath()).getParentFile();
        File configPath = new File(basePath, configGroup.getConfigPath());

        AVConfig avConfig = new AVConfig();
        AVConfigReader reader = new AVConfigReader(avConfig);

        reader.readFile(configPath.getAbsolutePath());
        return avConfig;
    }

    @Provides @Singleton
    public AVData provideData(Map<Id<AVOperator>, AVOperator> operators, Map<Id<AVOperator>, List<AVVehicle>> vehicles) {
        AVData data = new AVData();

        for (List<AVVehicle> vehs : vehicles.values()) {
            for (AVVehicle vehicle : vehs) {
                data.addVehicle(vehicle);
            }
        }

        return data;
    }

    @Provides @Singleton
    Map<Id<AVOperator>, AVGenerator> provideGenerators(Map<String, AVGenerator.AVGeneratorFactory> factories, AVConfig config) {
        Map<Id<AVOperator>, AVGenerator> generators = new HashMap<>();

        for (AVOperatorConfig oc : config.getOperatorConfigs()) {
            AVGeneratorConfig gc = oc.getGeneratorConfig();
            String strategy = gc.getStrategyName();

            if (!factories.containsKey(strategy)) {
                throw new IllegalArgumentException("Generator strategy '" + strategy + "' is not registered.");
            }

            AVGenerator.AVGeneratorFactory factory = factories.get(strategy);
            AVGenerator generator = factory.createGenerator(gc);

            generators.put(oc.getId(), generator);
        }

        return generators;
    }

    @Provides @Singleton
    public Map<Id<AVOperator>, List<AVVehicle>> provideVehicles(Map<Id<AVOperator>, AVOperator> operators, Map<Id<AVOperator>, AVGenerator> generators) {
        Map<Id<AVOperator>, List<AVVehicle>> vehicles = new HashMap<>();

        for (AVOperator operator : operators.values()) {
            LinkedList<AVVehicle> operatorList = new LinkedList<>();

            AVGenerator generator = generators.get(operator.getId());

            while (generator.hasNext()) {
                AVVehicle vehicle = generator.next();
                vehicle.setOpeartor(operator);
                operatorList.add(vehicle);
            }

            vehicles.put(operator.getId(), operatorList);
        }

        return vehicles;
    }
}
