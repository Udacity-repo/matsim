package playground.joel.data;

/**
 * Created by Joel on 28.02.2017.
 */
public class WaitingTimesXML  extends AbstractDataXML<Integer> {
    WaitingTimesXML() {
        super("SimulationResult", "person", "person", "time", "start", "end");
    }
}
