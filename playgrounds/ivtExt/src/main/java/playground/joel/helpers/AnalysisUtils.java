package playground.joel.helpers;

import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.Tensors;
import ch.ethz.idsc.tensor.io.Get;
import playground.clruch.net.DispatchEvent;
import playground.clruch.net.SimulationObject;
import playground.clruch.net.StorageSupplier;
import playground.clruch.utils.GlobalAssert;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;


/**
 * Created by Joel on 01.05.2017.
 */
public abstract class AnalysisUtils {
    final static File GROUPSIZEFILE = new File("output/groupSize.mdisp.txt");

    public static int getNumVehicles(StorageSupplier storageSupplier) throws Exception{
        SimulationObject init = storageSupplier.getSimulationObject(1);
        return init.vehicles.size();
    }

    public static int getNumGroups() {
        if (getGroupSizes() != Tensors.empty())
            return getGroupSizes().length();
        else return 0;
    }

    public static Tensor getGroupSizes() {
        Tensor sizes = Tensors.empty();
        if(GROUPSIZEFILE.exists() && !GROUPSIZEFILE.isDirectory()) {
            try {
                sizes = Get.of(GROUPSIZEFILE);
            } catch (Exception exception) {
                exception.printStackTrace();
                GlobalAssert.that(false);
            }
        }
        return sizes;
    }

    public static int getGroupSize(int group) {
        Tensor sizes = getGroupSizes();
        GlobalAssert.that(group < sizes.length());
        int size = sizes.Get(group).number().intValue();
        return size;
    }

    public static int getGroup(int vehicleIndex) {
        int group;
        int groupBoundary = 0;
        int i = 0;
        while (true) {
            if(vehicleIndex >= groupBoundary && vehicleIndex < groupBoundary + getGroupSize(i)) {
                group = i;
                break;
            }
            groupBoundary += getGroupSize(i);
            i++;
        }
        return group;
    }

    public static int getGroup(int requestIndex, NavigableMap<Integer, Integer> requestVehicleIndices) {
        GlobalAssert.that(requestVehicleIndices.containsKey(requestIndex));
        int vehicleIndex = requestVehicleIndices.get(requestIndex);
        return getGroup(vehicleIndex);
    }

    public static NavigableMap<Integer, Integer> createRequestVehicleIndices(StorageSupplier storageSupplier) throws Exception {
        NavigableMap<Integer, Integer> requestVehicleIndices = new TreeMap<>();
        for (int index = 0; index < storageSupplier.size(); ++index) {
            SimulationObject s = storageSupplier.getSimulationObject(index);
            List<DispatchEvent> list = (List<DispatchEvent>) s.serializable;
            for (DispatchEvent e : list) {
                requestVehicleIndices.put(e.requestIndex,  e.vehicleIndex);
                // System.out.println(e.requestIndex + ", " + e.vehicleIndex);
            }
        }
        return requestVehicleIndices;
    }



}
