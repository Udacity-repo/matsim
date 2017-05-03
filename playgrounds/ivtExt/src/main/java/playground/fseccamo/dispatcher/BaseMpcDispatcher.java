package playground.fseccamo.dispatcher;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.dvrp.path.VrpPath;
import org.matsim.contrib.dvrp.schedule.Task;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.router.util.TravelTime;

import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.alg.Array;
import ch.ethz.idsc.tensor.sca.Plus;
import playground.clruch.dispatcher.core.PartitionedDispatcher;
import playground.clruch.netdata.VirtualLink;
import playground.clruch.netdata.VirtualNetwork;
import playground.clruch.netdata.VirtualNode;
import playground.sebhoerl.avtaxi.config.AVDispatcherConfig;
import playground.sebhoerl.avtaxi.data.AVVehicle;
import playground.sebhoerl.avtaxi.schedule.AVDriveTask;
import playground.sebhoerl.avtaxi.schedule.AVDropoffTask;
import playground.sebhoerl.avtaxi.schedule.AVPickupTask;
import playground.sebhoerl.plcpc.ParallelLeastCostPathCalculator;

abstract class BaseMpcDispatcher extends PartitionedDispatcher {

    BaseMpcDispatcher( //
            AVDispatcherConfig config, //
            TravelTime travelTime, //
            ParallelLeastCostPathCalculator router, //
            EventsManager eventsManager, //
            VirtualNetwork virtualNetwork) {
        super(config, travelTime, router, eventsManager, virtualNetwork);
    }

    protected Tensor countVehiclesPerVLink(Map<AVVehicle, Link> map) {
        final Tensor vector = Array.zeros(virtualNetwork.getvLinksCount());
        for (Entry<AVVehicle, Link> entry : map.entrySet()) {
            final AVVehicle avVehicle = entry.getKey();
            final Link current = entry.getValue();
            Task task = avVehicle.getSchedule().getCurrentTask();
            int vli = -1;
            if (task instanceof AVPickupTask) {
                List<? extends Task> list = avVehicle.getSchedule().getTasks();
                int taskIndex = list.indexOf(task); //
                task = list.get(taskIndex + 1);
            }
            if (task instanceof AVDriveTask)
                vli = getVirtualLinkOfVehicle((AVDriveTask) task, current);
            if (task instanceof AVDropoffTask) {
                // don't do anything
            }
            if (0 <= vli)
                vector.set(Plus.ONE, vli);
        }
        return vector;
    }

    /**
     * @param driveTask
     * @param current
     * @return virtual link index on which vehicle of drive task is traversing on, or
     *         -1 if such link cannot be identified
     */
    private int getVirtualLinkOfVehicle(AVDriveTask driveTask, Link current) {
        VrpPath vrpPath = driveTask.getPath();
        boolean fused = false;
        VirtualNode fromIn = null;
        VirtualNode toIn = null;

        for (Link link : vrpPath) {
            fused |= link == current;
            if (fused) {
                if (fromIn == null)
                    fromIn = virtualNetwork.getVirtualNode(link);
                else {
                    VirtualNode candidate = virtualNetwork.getVirtualNode(link);
                    if (fromIn != candidate) {
                        toIn = candidate;
                        VirtualLink virtualLink = virtualNetwork.getVirtualLink(fromIn, toIn);
                        return virtualLink.index;
                    }
                }
            }
        }
        // we can reach this point if vehicle is in last virtual node of path
        // System.out.println("failed to find virtual link of transition.");
        return -1;
    }

}
