package playground.joel.analysis;

import static playground.clruch.utils.NetworkLoader.loadNetwork;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;

import ch.ethz.idsc.tensor.Tensors;
import org.matsim.api.core.v01.network.Network;

import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.alg.Dimensions;
import ch.ethz.idsc.tensor.alg.Join;
import ch.ethz.idsc.tensor.alg.Transpose;
import ch.ethz.idsc.tensor.io.CsvFormat;
import ch.ethz.idsc.tensor.io.MathematicaFormat;
import ch.ethz.idsc.tensor.io.MatlabExport;
import playground.clruch.data.ReferenceFrame;
import playground.clruch.net.MatsimStaticDatabase;
import playground.clruch.net.StorageSupplier;
import playground.joel.data.TotalData;

/**
 * Created by Joel on 05.04.2017.
 */
public class AnalyzeAll {
    public static final File RELATIVE_DIRECTORY = new File("output", "data");
    public static final boolean filter = true;  // filter size can be adapted in the diagram creator
    public static final double maxWaitingTime = 20.0; // maximally displayed waiting time in minutes

    public static double waitBinSize = 10.0;

    public static double timeRatio ;
    public static double distance ;
    public static double distanceWithCust;
    public static double distancePickup;
    public static double distanceRebalance;
    public static Tensor totalWaitTimeQuantile;
    public static Tensor totalWaitTimeMean;
    public static double distanceRatio;

    public static void main(String[] args) throws Exception {
        analyze(args);
    }

    static void saveFile(Tensor table, String name) throws Exception {
        Files.write(Paths.get("output/data/" + name + ".csv"), (Iterable<String>) CsvFormat.of(table)::iterator);
        Files.write(Paths.get("output/data/" + name + ".mathematica"), (Iterable<String>) MathematicaFormat.of(table)::iterator);
        Files.write(Paths.get("output/data/" + name + ".m"), (Iterable<String>) MatlabExport.of(table)::iterator);
    }

    static void plot(String csv, String name, String title, int from, int to, Double maxRange) throws Exception {
        Tensor table = CsvFormat.parse(Files.lines(Paths.get("output/data/" + csv + ".csv")));
        table = Transpose.of(table);
        try {
            DiagramCreator.createDiagram(RELATIVE_DIRECTORY, name, title, table.get(0), table.extract(from, to), //
                    maxRange, filter);
        } catch (Exception e) {
            System.out.println("Error creating the diagrams");
        }
    }

    static void plot(String csv, String name, String title, int from, int to) throws Exception {
        plot(csv, name, title, from, to, 1.05);
    }

    static void collectAndPlot(CoreAnalysis coreAnalysis, DistanceAnalysis distanceAnalysis) throws Exception {
        Tensor summary = Join.of(1, coreAnalysis.summary, distanceAnalysis.summary);
        saveFile(summary, "summary");
        System.out.println("Size of data summary: " + Dimensions.of(summary));

        getTotals(summary, coreAnalysis);

        AnalyzeAll.plot("summary", "binnedWaitingTimes", "Waiting Times", 3, 6, maxWaitingTime);
        // maximum waiting time in the plot to have this uniform for all simulations
        AnalyzeAll.plot("summary", "binnedTimeRatios", "Occupancy Ratio", 10, 11);
        AnalyzeAll.plot("summary", "binnedDistanceRatios", "Distance Ratio", 15, 16);
        UniqueDiagrams.distanceStack(RELATIVE_DIRECTORY, "stackedDistance", "Distance Partition", //
                distanceRebalance/distance, distancePickup/distance, distanceWithCust/distance);
        UniqueDiagrams.binCountGraph(RELATIVE_DIRECTORY, "waitBinCounter", //
                "Requests per Waiting Time", coreAnalysis.waitBinCounter, waitBinSize, //
                100.0/coreAnalysis.numRequests, "% of requests", //
                "Waiting Times", " sec");
        UniqueDiagrams.distanceDistribution(RELATIVE_DIRECTORY, "distanceDistribution", //
                "Distance Distribution", true);
    }

    static void getTotals(Tensor summary, CoreAnalysis coreAnalysis) {
        int size = summary.length();
        timeRatio = 0;
        distance = 0;
        distanceWithCust = 0;
        distancePickup = 0;
        distanceRebalance = 0;
        totalWaitTimeMean = coreAnalysis.totalWaitTimeMean;
        totalWaitTimeQuantile = coreAnalysis.totalWaitTimeQuantile;
        for (int j = 0; j < size; j++) {
            timeRatio += summary.Get(j, 10).number().doubleValue();
            distance += summary.Get(j, 11).number().doubleValue();
            distanceWithCust += summary.Get(j, 12).number().doubleValue();
            distancePickup+= summary.Get(j, 13).number().doubleValue();
            distanceRebalance += summary.Get(j, 14).number().doubleValue();
        }
        timeRatio = timeRatio / size;
        distanceRatio = distanceWithCust / distance;

        TotalData totalData = new TotalData();
        totalData.generate(String.valueOf(timeRatio), String.valueOf(distanceRatio), //
                String.valueOf(totalWaitTimeMean.Get().number().doubleValue()), //
                String.valueOf(totalWaitTimeQuantile.Get(1).number().doubleValue()), //
                String.valueOf(totalWaitTimeQuantile.Get(2).number().doubleValue()), //
                new File(RELATIVE_DIRECTORY, "totalData.xml"));
    }

    public static AnalyzeSummary summarize(CoreAnalysis coreAnalysis, DistanceAnalysis distanceAnalysis) {
        AnalyzeSummary analyzeSummary = new AnalyzeSummary();
        // analyzeSummary.coreAnalysis = coreAnalysis;
        // analyzeSummary.distanceAnalysis = distanceAnalysis;
        analyzeSummary.numVehicles = distanceAnalysis.numVehicles;
        analyzeSummary.numRequests = coreAnalysis.numRequests;
        analyzeSummary.occupancyRatio = timeRatio;
        analyzeSummary.distance = distance;
        analyzeSummary.distanceWithCust = distanceWithCust;
        analyzeSummary.distancePickup = distancePickup;
        analyzeSummary.distanceRebalance = distanceRebalance;
        analyzeSummary.distanceRatio = distanceRatio;
        analyzeSummary.totalWaitTimeMean = coreAnalysis.totalWaitTimeMean;
        analyzeSummary.totalWaitTimeQuantile = coreAnalysis.totalWaitTimeQuantile;
        analyzeSummary.maximumWaitTime = coreAnalysis.maximumWaitTime;
        return analyzeSummary;
    }

    public static AnalyzeSummary analyze(String[] args) throws Exception {

        File config = new File(args[0]);
        File data = new File(config.getParent(), "output/data");
        data.mkdir();

        // load system network
        Network network = loadNetwork(args);

        // load coordinate system
        // TODO later remove hard-coded
        MatsimStaticDatabase.initializeSingletonInstance(network, ReferenceFrame.SIOUXFALLS);

        // load simulation data
        StorageSupplier storageSupplier = StorageSupplier.getDefault();
        final int size = storageSupplier.size();
        System.out.println("Found files: " + size);

        // analyze and print files
        CoreAnalysis coreAnalysis = new CoreAnalysis(storageSupplier);
        DistanceAnalysis distanceAnalysis = new DistanceAnalysis(storageSupplier);
        try {
            coreAnalysis.analyze();
            distanceAnalysis.analzye();
        } catch (Exception e) {
            e.printStackTrace();
        }

        collectAndPlot(coreAnalysis, distanceAnalysis);

        return summarize(coreAnalysis, distanceAnalysis);

    }
}
