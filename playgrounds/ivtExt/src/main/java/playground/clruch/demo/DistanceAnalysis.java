package playground.clruch.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import playground.clruch.net.SimulationObject;
import playground.clruch.net.StorageSupplier;
import playground.clruch.net.VehicleContainer;
import playground.clruch.net.VehicleStatistic;

/**
 * THIS FILE IS A CONCISE DEMO OF FUNCTIONALITY
 * 
 * DO NOT MODIFY THIS FILE (unless you are the primary author),
 * BUT DO NOT RELY ON THIS FILE NOT BEING CHANGED
 * 
 * IF YOU WANT TO MAKE A SIMILAR CLASS OR REPLY ON THIS IMPLEMENTATION
 * THEN DUPLICATE THIS FILE AND MAKE THE CHANGES IN THE NEW FILE
 */
class DistanceAnalysis {
    StorageSupplier storageSupplier;
    int size;
    String dataPath;

    DistanceAnalysis(StorageSupplier storageSupplierIn, String datapath) {
        storageSupplier = storageSupplierIn;
        size = storageSupplier.size();
        dataPath = datapath;
    }

    public void analzye() throws Exception {

        SimulationObject init = storageSupplier.getSimulationObject(1);
        final int numVehicles = init.vehicles.size();
        System.out.println("found vehicles: " + numVehicles);

        List<VehicleStatistic> list = new ArrayList<>();
        IntStream.range(0, numVehicles).forEach(i -> list.add(new VehicleStatistic(size - 1)));

        for (int index = 0; index < size - 1; ++index) {
            SimulationObject s = storageSupplier.getSimulationObject(1 + index);
            for (VehicleContainer vc : s.vehicles)
                list.get(vc.vehicleIndex).register(index, vc);

            if (s.now % 1000 == 0)
                System.out.println(s.now);

        }

        list.forEach(VehicleStatistic::consolidate);

        // Tensor table1 = list.stream().map(vs -> vs.distanceTotal).reduce(Tensor::add).get();
        // Tensor table2 = list.stream().map(vs -> vs.distanceWithCustomer).reduce(Tensor::add).get();
        // Tensor table3 = table1.map(InvertUnlessZero.function).pmul(table2);
        // {
        // AnalyzeMarc.saveFile(table1, "distanceTotal");
        // AnalyzeMarc.saveFile(table2, "distanceWithCustomer");
        // AnalyzeMarc.saveFile(table3, "distanceRatio");
        // }
    }
}
