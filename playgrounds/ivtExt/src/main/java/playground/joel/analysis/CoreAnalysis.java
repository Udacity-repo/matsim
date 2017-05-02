package playground.joel.analysis;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import ch.ethz.idsc.tensor.RealScalar;
import ch.ethz.idsc.tensor.Scalar;
import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.Tensors;
import ch.ethz.idsc.tensor.alg.Array;
import ch.ethz.idsc.tensor.alg.Join;
import ch.ethz.idsc.tensor.red.Mean;
import ch.ethz.idsc.tensor.red.Quantile;
import playground.clruch.export.AVStatus;
import playground.clruch.net.SimulationObject;
import playground.clruch.net.StorageSupplier;
import playground.clruch.net.VehicleContainer;
import playground.joel.helpers.AnalysisUtils;

/**
 * Created by Joel on 05.04.2017.
 */
class CoreAnalysis {
    private StorageSupplier storageSupplier;
    private int size;
    private String data;
    Tensor summary = Tensors.empty();
    Tensor totalWaitTimeQuantile = Tensors.empty();
    Tensor totalWaitTimeMean = Tensors.empty();
    NavigableMap<Integer, Integer> requestVehicleIndices = new TreeMap<>();
    NavigableMap<Integer, Integer> vehicleGroupMap = new TreeMap<>();

    CoreAnalysis(StorageSupplier storageSupplierIn, String dataDir, //
                NavigableMap<Integer, Integer> requestVehicleIndicesIn, //
                NavigableMap<Integer, Integer> vehicleGroupMapIn) {
            storageSupplier = storageSupplierIn;
            size = storageSupplier.size();
            data = dataDir;
            requestVehicleIndices = requestVehicleIndicesIn;
            vehicleGroupMap = vehicleGroupMapIn;
    }

    private static Tensor quantiles(Tensor submission) {
        if (3 < submission.length()) {
            return Quantile.of(submission, Tensors.vectorDouble(.1, .5, .95));
        } else {
            return Array.zeros(3);
        }
    }

    private static Tensor means(Tensor submission) {
        if (3 < submission.length()) {
            return Mean.of(submission);
        } else {
            return Mean.of(Array.zeros(1));
        }
    }

    public void analyze(int from, int to) throws Exception {

        Tensor table = Tensors.empty();
        Tensor allSubmissions = Tensors.empty();

        Map<Integer, Double> requestWaitTimes = new HashMap<>();

        int group = AnalysisUtils.getGroup(from, vehicleGroupMap);

        for (int index = 0; index < size; ++index) {

            SimulationObject s = storageSupplier.getSimulationObject(index);

            final long now = s.now;
            Scalar time = RealScalar.of(s.now);

            // number of requests
            Scalar requestsSize = RealScalar.of(s.requests.size());

            // wait time Quantiles and mean
            Tensor waitTimeQuantile;
            Tensor waitTimeMean;
            {
                Tensor submission = Tensor.of(s.requests.stream(). //
                        //filter(rc -> AnalysisUtils.getGroup(rc.requestIndex, requestVehicleIndices) == group). //
                        map(rc -> RealScalar.of(now - rc.submissionTime)));
                waitTimeQuantile = quantiles(submission);
                waitTimeMean = means(submission);
                allSubmissions.append(submission);
            }

            s.requests.stream().//filter(rc -> AnalysisUtils.getGroup(rc.requestIndex, requestVehicleIndices) == group). //
                    forEach(rc -> requestWaitTimes.put(rc.requestIndex, now - rc.submissionTime));

            // status of AVs and occupancy ratio
            Tensor numStatus = Array.zeros(AVStatus.values().length);
            Scalar occupancyRatio = RealScalar.of(0.0);
            Integer totVeh = 0;
            {
                Map<AVStatus, List<VehicleContainer>> map = //
                        s.vehicles.stream().filter(vc -> AnalysisUtils.getGroup(vc.vehicleIndex, vehicleGroupMap) == group). //
                        collect(Collectors.groupingBy(vc -> vc.avStatus));
                for (Entry<AVStatus, List<VehicleContainer>> entry : map.entrySet()) {
                    numStatus.set(RealScalar.of(entry.getValue().size()), entry.getKey().ordinal());
                    totVeh += entry.getValue().size();
                }
                if (map.containsKey(AVStatus.DRIVEWITHCUSTOMER)) {
                    occupancyRatio = RealScalar.of(map.get(AVStatus.DRIVEWITHCUSTOMER).size() / (double) totVeh);
                }
            }

            // Distance ratio
            Tensor row = Join.of( //
                    Tensors.of(time, requestsSize), //
                    waitTimeQuantile, //
                    waitTimeMean, //
                    numStatus, //
                    occupancyRatio);

            table.append(row);

            if (s.now % 10000 == 0)
                System.out.println(s.now);

        }

        AnalyzeAll.saveFile(table, "basicDemo", data);

        Tensor uniqueSubmissions = Tensor.of(requestWaitTimes.values().stream().map(RealScalar::of));

        totalWaitTimeQuantile = quantiles(uniqueSubmissions);
        System.out.println("Q = " + totalWaitTimeQuantile);
        totalWaitTimeMean = means(uniqueSubmissions);
        System.out.println("mean = " + totalWaitTimeMean);

        summary = table;
    }

    public void analyze() throws Exception {
        final int numVehicles = AnalysisUtils.getNumVehicles(storageSupplier);
        analyze(0, numVehicles);
    }
}
