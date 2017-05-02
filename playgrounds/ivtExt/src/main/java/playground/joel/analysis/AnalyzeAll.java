package playground.joel.analysis;

import static playground.clruch.utils.NetworkLoader.loadNetwork;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.matsim.api.core.v01.network.Network;
import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.alg.Dimensions;
import ch.ethz.idsc.tensor.alg.Join;
import ch.ethz.idsc.tensor.alg.Transpose;
import ch.ethz.idsc.tensor.io.CsvFormat;
import ch.ethz.idsc.tensor.io.MathematicaFormat;
import ch.ethz.idsc.tensor.io.MatlabExport;
import playground.clruch.gfx.ReferenceFrame;
import playground.clruch.net.MatsimStaticDatabase;
import playground.clruch.net.StorageSupplier;
import playground.clruch.utils.GlobalAssert;
import playground.joel.data.TotalData;
import playground.joel.helpers.AnalysisUtils;

/**
 * Created by Joel on 05.04.2017.
 */
public class AnalyzeAll {
    public static void main(String[] args) throws Exception {
        analyze(args);
    }

    static void saveFile(Tensor table, String name, String data) throws Exception {
        Files.write(Paths.get("output/" + data + "/" + name + ".csv"), (Iterable<String>) CsvFormat.of(table)::iterator);
        Files.write(Paths.get("output/" + data + "/" + name + ".mathematica"), (Iterable<String>) MathematicaFormat.of(table)::iterator);
        Files.write(Paths.get("output/" + data + "/" + name + ".m"), (Iterable<String>) MatlabExport.of(table)::iterator);
    }

    private static void plot(String csv, String name, String title, int from, int to, Double maxRange, String data) throws Exception {
        Tensor table = CsvFormat.parse(Files.lines(Paths.get("output/data/" + csv + ".csv")));
        System.out.println(Dimensions.of(table));

        table = Transpose.of(table);

        try {
            File dir = new File("output/" + data);
            DiagramCreator.createDiagram(dir, name, title, table.get(0), table.extract(from, to), maxRange);
        } catch (Exception e) {
            System.out.println("Error creating the diagrams");
        }
    }

    private static void plot(String csv, String name, String title, int from, int to, String data) throws Exception {
        plot(csv, name, title, from, to, 1.05, data);
    }

    private static void collectAndPlot(CoreAnalysis coreAnalysis, DistanceAnalysis distanceAnalysis, String data) throws Exception {
        Tensor summary = Join.of(1, coreAnalysis.summary, distanceAnalysis.summary);
        saveFile(summary, "summary", data);
        AnalyzeAll.plot("summary", "binnedWaitingTimes", "waiting times", 3, 6, 1200.0, data);
            // maximum waiting time in the plot to have this uniform for all
                                                                                         // simulations
        AnalyzeAll.plot("summary", "binnedTimeRatios", "occupancy ratio", 10, 11, data);
        AnalyzeAll.plot("summary", "binnedDistanceRatios", "distance ratio", 13, 14, data);
        getTotals(summary, coreAnalysis, data);
    }

    private static void analyzeAndPlot(File config, StorageSupplier storageSupplier, String dataDir, int from, int to, //
                                       NavigableMap<Integer, Integer> requestVehicleIndices, //
                                       NavigableMap<Integer, Integer> vehicleGroupMap) throws Exception {

        GlobalAssert.that(to > from);
        File data = new File(config.getParent(), "output/" + dataDir);
        data.mkdir();

        // analyze and print files
        CoreAnalysis coreAnalysis = new CoreAnalysis(storageSupplier, dataDir, //
                requestVehicleIndices, vehicleGroupMap);
        DistanceAnalysis distanceAnalysis = new DistanceAnalysis(storageSupplier, dataDir, //
                requestVehicleIndices, vehicleGroupMap);
        try {
            coreAnalysis.analyze(from, to);
            distanceAnalysis.analyze(from, to);
        } catch (Exception e) {
            e.printStackTrace();
        }

        collectAndPlot(coreAnalysis, distanceAnalysis, dataDir);
    }

    private static void analyzeAndPlot(File config, StorageSupplier storageSupplier, String dataDir, //
                                       NavigableMap<Integer, Integer> requestVehicleIndices, //
                                       NavigableMap<Integer, Integer> vehicleGroupMap) throws Exception {
        final int numVehicles = AnalysisUtils.getNumVehicles(storageSupplier);
        analyzeAndPlot(config, storageSupplier, dataDir, 0, numVehicles, requestVehicleIndices, vehicleGroupMap);
    }

    private static void getTotals(Tensor table, CoreAnalysis coreAnalysis, String data) {
        int size = table.length();
        double timeRatio = 0;
        double distance = 0;
        double distanceWithCust = 0;
        double mean = coreAnalysis.totalWaitTimeMean.Get().number().doubleValue();
        double quantile50 = coreAnalysis.totalWaitTimeQuantile.Get(1).number().doubleValue();
        double quantile95 = coreAnalysis.totalWaitTimeQuantile.Get(2).number().doubleValue();
        for (int j = 0; j < size; j++) {
            timeRatio += table.Get(j, 10).number().doubleValue();
            distance += table.Get(j, 11).number().doubleValue();
            distanceWithCust += table.Get(j, 12).number().doubleValue();
        }
        timeRatio = timeRatio / size;
        double distanceRatio = distanceWithCust / distance;

        TotalData totalData = new TotalData();
        totalData.generate(String.valueOf(timeRatio), String.valueOf(distanceRatio), String.valueOf(mean), String.valueOf(quantile50), String.valueOf(quantile95),
                new File("output/" + data + "/totalData.xml"));
    }


    public static void analyze(String[] args) throws Exception {

        File config = new File(args[0]);

        // load system network
        Network network = loadNetwork(args);

        // load coordinate system
        // TODO later remove hard-coded
        MatsimStaticDatabase.initializeSingletonInstance(network, ReferenceFrame.SIOUXFALLS);

        // load simulation data
        StorageSupplier storageSupplier = StorageSupplier.getDefault();
        final int size = storageSupplier.size();
        System.out.println("found files: " + size);

        NavigableMap<Integer, Integer> requestVehicleIndices = AnalysisUtils.createRequestVehicleIndices(storageSupplier);
        NavigableMap<Integer, Integer> vehicleGroupMap = AnalysisUtils.createVehicleGroupMap();

        analyzeAndPlot(config, storageSupplier, "data", requestVehicleIndices, vehicleGroupMap);
        if (AnalysisUtils.getNumGroups() != 0) {
            int lowerBound = 0;
            for (int i = 0; i < AnalysisUtils.getNumGroups(); i++) {
                System.out.println("Analysis of group " + i);
                analyzeAndPlot(config, storageSupplier, "data_" + i, lowerBound, lowerBound + AnalysisUtils.getGroupSize(i), requestVehicleIndices, vehicleGroupMap);
                lowerBound += AnalysisUtils.getGroupSize(i);
            }
            // TODO: handle possible remaining vehicles
        }
    }
}
