package playground.clruch.net;

import java.util.HashMap;
import java.util.Map;

import playground.clruch.utils.GlobalAssert;
import playground.sebhoerl.avtaxi.data.AVVehicle;

public class VehicleIntegerDatabase {
    private final Map<String, Integer> map = new HashMap<>();

    /**
     * @param string
     *            is vehicle id as string
     * @return
     */
    public int getId(AVVehicle avVehicle) {
        String string = avVehicle.getId().toString();
        GlobalAssert.that(string.startsWith("av_av_op1_"));
        int value = Integer.parseInt(string.substring(10)) - 1;
        if (!map.containsKey(string)) {
            // System.out.println("ID=[" + string + "] -> " + value);
            map.put(string, value);
        }
        return map.get(string);
    }
}
