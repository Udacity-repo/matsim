package playground.joel.helpers;

import ch.ethz.idsc.tensor.RealScalar;
import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.Tensors;
import ch.ethz.idsc.tensor.alg.Array;
import ch.ethz.idsc.tensor.io.Get;
import ch.ethz.idsc.tensor.io.Put;
import playground.clruch.net.DispatchEvent;
import playground.clruch.net.SimulationObject;
import playground.clruch.net.StorageSupplier;
import playground.clruch.utils.GlobalAssert;
import playground.joel.analysis.AnalyzeAll;

import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;


/**
 * Created by Joel on 01.05.2017.
 */
public abstract class AnalysisUtils {
    private static int fails = 0;
    private static int maxFails = 50;

    /**
     *
     * @param storageSupplier
     * @return the total number of vehicles
     * @throws Exception
     */
    public static int getNumVehicles(StorageSupplier storageSupplier) throws Exception{
        SimulationObject init = storageSupplier.getSimulationObject(1);
        return init.vehicles.size();
    }

    /**
     *
     * @return the number of dispatcher groups
     */
    public static int getNumGroups() {
        if (getGroupSizes() != Tensors.empty())
            return getGroupSizes().length();
        else return 0;
    }

    /**
     *
     * @return the fleet sizes listed in a tensor
     */
    public static Tensor getGroupSizes() {
        Tensor sizes = Tensors.empty();
        if(AnalyzeAll.GROUPSIZEFILE.exists() && !AnalyzeAll.GROUPSIZEFILE.isDirectory()) {
            try {
                sizes = Get.of(AnalyzeAll.GROUPSIZEFILE);
            } catch (Exception exception) {
                exception.printStackTrace();
                GlobalAssert.that(false);
            }
        }
        return sizes;
    }

    /**
     *
     * @param group
     * @return the fleet size of dispatcher number group
     */
    public static int getGroupSize(int group) {
        Tensor sizes = getGroupSizes();
        GlobalAssert.that(group < sizes.length());
        int size = sizes.Get(group).number().intValue();
        return size;
    }

    /**
     *
     * @param vehicleIndex
     * @param vehicleGroupMap
     * @return the number of the dispatcher the vehicle belongs to
     */
    public static int getGroup(int vehicleIndex, NavigableMap<Integer, Integer> vehicleGroupMap) {
        if (vehicleGroupMap.size() == 0) return 0;
        else return vehicleGroupMap.floorEntry(vehicleIndex).getValue();
    }

    /**
     *
     * @param requestIndex
     * @param requestVehicleIndices
     * @param vehicleGroupMap
     * @return the number of the dispatcher the request is served by
     */
    public static int getGroup(int requestIndex, NavigableMap<Integer, Integer> requestVehicleIndices, //
                               NavigableMap<Integer, Integer> vehicleGroupMap) {
        if (!requestVehicleIndices.containsKey(requestIndex))
            System.out.println("ERROR: No vehicle corresponding to request " + requestIndex + " found!");
        GlobalAssert.that(requestVehicleIndices.containsKey(requestIndex));
        int vehicleIndex = requestVehicleIndices.get(requestIndex);
        return getGroup(vehicleIndex, vehicleGroupMap);
    }

    /**
     *
     * @param storageSupplier
     * @param vehicleGroupMap
     * @return NavigableMap containing the request indices and the vehicle's index it was served by
     * @throws Exception
     */
    public static NavigableMap<Integer, Integer> createRequestVehicleIndices( //
            StorageSupplier storageSupplier, NavigableMap<Integer, Integer> vehicleGroupMap) throws Exception {
        NavigableMap<Integer, Integer> requestVehicleIndices = new TreeMap<>();
        Tensor trips = Array.zeros(getNumGroups() > 0 ? getNumGroups() : 1);
        for (int index = 0; index < storageSupplier.size(); ++index) {
            SimulationObject s = storageSupplier.getSimulationObject(index);
            List<DispatchEvent> list = (List<DispatchEvent>) s.serializable;
            for (DispatchEvent e : list) {
                requestVehicleIndices.put(e.requestIndex,  e.vehicleIndex);
                // System.out.println(e.requestIndex + ", " + e.vehicleIndex);
                int group = getGroup(e.vehicleIndex, vehicleGroupMap);
                int current = trips.Get(group).number().intValue();
                trips.set(RealScalar.of(current + 1), getGroup(e.vehicleIndex, vehicleGroupMap));
            }
        }
        try {
            Put.of(AnalyzeAll.TRIPCOUNTERFILE, trips);
            Tensor check = Get.of(AnalyzeAll.TRIPCOUNTERFILE);
            GlobalAssert.that(trips.equals(check));
        } catch (Exception exception) {
            exception.printStackTrace();
            GlobalAssert.that(false);
        }
        return requestVehicleIndices;
    }

    /**
     *
     * @return NavigableMap containing the index of the first vehicle in the fleet and the respective fleet number
     */
    public static NavigableMap<Integer, Integer> createVehicleGroupMap() {
        NavigableMap<Integer, Integer> vehicleGroupMap = new TreeMap<>();
        int firstGroupIndex = 0;
        for (int group = 0; group < getNumGroups(); group++) {
            vehicleGroupMap.put(firstGroupIndex, group);
            firstGroupIndex += getGroupSize(group);
        }
        return  vehicleGroupMap;
    }

    /**
     *
     * @param vehicleIndex
     * @param from
     * @param to
     * @return check if vehicle index is in group [from, to)
     */
    public static boolean isInGroup(int vehicleIndex, int from , int to) {
        if (vehicleIndex >= from && vehicleIndex < to) return true;
        else return false;
    }

    /**
     *
     * @param requestIndex
     * @param from
     * @param to
     * @param requestVehicleIndices
     * @return check if the request was served by a vehicle contained in group [from, to)
     */
    public static boolean isInGroup(int requestIndex, int from , int to, NavigableMap<Integer, Integer> requestVehicleIndices) {
        if (!requestVehicleIndices.containsKey(requestIndex)) {
            if (fails < maxFails) {
                System.out.println("ATTENTION: No vehicle corresponding to request " + requestIndex + " found!\n" + //
                        "\tThis request is probably never picked up.");
                fails++;
            }
            if (fails == maxFails) {
                System.out.println("ATTENTION: Many requests are never picked up!\n" + //
                        "\tNo further attentions of this type will be displayed.");
                fails++;
            }
            return false;
        } else {
            int vehicleIndex = requestVehicleIndices.get(requestIndex);
            return isInGroup(vehicleIndex, from, to);
        }
    }

}
