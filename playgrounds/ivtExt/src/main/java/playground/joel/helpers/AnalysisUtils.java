package playground.joel.helpers;

import playground.clruch.net.SimulationObject;
import playground.clruch.net.StorageSupplier;


/**
 * Created by Joel on 01.05.2017.
 */
public abstract class AnalysisUtils {

    public static int getNumVehicles(StorageSupplier storageSupplier) throws Exception{
        SimulationObject init = storageSupplier.getSimulationObject(1);
        return init.vehicles.size();
    }

    public static int getFirstGroupSize() {
        int firstGroupSize = 0;

        // TODO: good way to overwrite group size automatically if available?
        // save config?

        return firstGroupSize;
    }

}
