package playground.joel.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.IntStream;

import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.Tensors;
import ch.ethz.idsc.tensor.alg.Join;
import ch.ethz.idsc.tensor.sca.InvertUnlessZero;
import playground.clruch.net.SimulationObject;
import playground.clruch.net.StorageSupplier;
import playground.clruch.net.VehicleContainer;
import playground.clruch.net.VehicleStatistic;
import playground.joel.helpers.AnalysisUtils;

/**
 * Created by Joel on 05.04.2017.
 */
class DistanceAnalysis {
    private StorageSupplier storageSupplier;
    private int size;
    private String data;
    Tensor summary = Tensors.empty();
    NavigableMap<Integer, Integer> requestVehicleIndices = new TreeMap<>();
    NavigableMap<Integer, Integer> vehicleGroupMap = new TreeMap<>();

    DistanceAnalysis(StorageSupplier storageSupplierIn, String dataDir, //
                     NavigableMap<Integer, Integer> requestVehicleIndicesIn, //
                     NavigableMap<Integer, Integer> vehicleGroupMapIn) {
        storageSupplier = storageSupplierIn;
        size = storageSupplier.size();
        data = dataDir;
        requestVehicleIndices = requestVehicleIndicesIn;
        vehicleGroupMap = vehicleGroupMapIn;
    }

    public void analyze(int from, int to) throws Exception {

        System.out.println("found vehicles: " + (to - from));

        final int numVehicles = AnalysisUtils.getNumVehicles(storageSupplier);
        List<VehicleStatistic> list = new ArrayList<>();
        IntStream.range(0, numVehicles).forEach(i -> list.add(new VehicleStatistic(size)));

        for (int index = 0; index < size - 1; ++index) {
            SimulationObject s = storageSupplier.getSimulationObject(1 + index);
            for (VehicleContainer vc : s.vehicles)
                if (AnalysisUtils.isInGroup(vc.vehicleIndex, from, to))
                    list.get(vc.vehicleIndex).register(index, vc);

            if (s.now % 10000 == 0)
                System.out.println(s.now);

        }

        list.forEach(VehicleStatistic::consolidate);

        Tensor table1 = list.stream().map(vs -> vs.distanceTotal).reduce(Tensor::add).get();
        Tensor table2 = list.stream().map(vs -> vs.distanceWithCustomer).reduce(Tensor::add).get();
        Tensor table3 = table1.map(InvertUnlessZero.function).pmul(table2);
        summary = Join.of(1, table1, table2, table3);
        {
            AnalyzeAll.saveFile(table1, "distanceTotal", data);
            AnalyzeAll.saveFile(table2, "distanceWithCustomer", data);
            AnalyzeAll.saveFile(table3, "distanceRatio", data);
        }
    }

    public void analyze() throws Exception {
        final int numVehicles = AnalysisUtils.getNumVehicles(storageSupplier);
        analyze(0, numVehicles);
    }
}
