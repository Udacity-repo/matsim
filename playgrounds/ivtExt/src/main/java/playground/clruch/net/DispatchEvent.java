package playground.clruch.net;

import playground.clruch.export.AVStatus;

import java.io.Serializable;

/**
 * Created by Joel on 02.05.2017.
 */
public class DispatchEvent implements Serializable {
    // TODO during evaluation phase, all member are public for easy read/write, maybe change later
    public AVStatus avStatus = null;
    public int vehicleIndex = -1;
    public int requestIndex = -1;
    public int linkIndex = -1;
}
