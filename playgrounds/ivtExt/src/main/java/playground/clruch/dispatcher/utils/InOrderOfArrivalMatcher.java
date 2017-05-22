package playground.clruch.dispatcher.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.QuadTree;

import playground.clruch.utils.GlobalAssert;
import playground.sebhoerl.avtaxi.data.AVVehicle;
import playground.sebhoerl.avtaxi.passenger.AVRequest;

import playground.clruch.dispatcher.core.UniversalDispatcher;

/**
 * if a vehicle is in stay more and there is a request at the link of where the car is the vehicle
 * will pickup the customer. this is repeated for all possible matches until either there are no
 * more available cars at a link, or no more requests. customers who have waited longer are picked
 * up first.
 */
public class InOrderOfArrivalMatcher extends AbstractVehicleRequestMatcher {
    final BiConsumer<AVVehicle, AVRequest> biConsumer;

    public InOrderOfArrivalMatcher(BiConsumer<AVVehicle, AVRequest> biConsumer) {
        this.biConsumer = biConsumer;
    }

    @Override
    public int match(Map<Link, Queue<AVVehicle>> stayVehicles, Map<Link, List<AVRequest>> requestsAtLinks) {
        int num_matchedRequests = 0;
        // match requests with stay vehicles
        for (Entry<Link, List<AVRequest>> entry : requestsAtLinks.entrySet()) {
            final Link link = entry.getKey();
            if (stayVehicles.containsKey(link)) {
                Iterator<AVRequest> requestIterator = entry.getValue().iterator();
                Queue<AVVehicle> vehicleQueue = stayVehicles.get(link);
                Queue<AVVehicle> queueShuffled = shuffleMyQueue(vehicleQueue);
                while (!queueShuffled.isEmpty() && requestIterator.hasNext()) {
                    biConsumer.accept(queueShuffled.poll(), requestIterator.next());
                    ++num_matchedRequests;
                }
            }
        }
        return num_matchedRequests;
    }

    public int matchRecord(Map<Link, Queue<AVVehicle>> stayVehicles, Map<Link, List<AVRequest>> requestsAtLinks, //
            HashMap<AVVehicle, List<AVRequest>> requestsServed, HashSet<AVRequest> openRequests, //
            QuadTree<AVRequest> pendingRequestsTree) {
        int num_matchedRequests = 0;
        // match requests with stay vehicles
        for (Entry<Link, List<AVRequest>> entry : requestsAtLinks.entrySet()) {
            final Link link = entry.getKey();
            if (stayVehicles.containsKey(link)) {
                Iterator<AVRequest> requestIterator = entry.getValue().iterator();
                Queue<AVVehicle> vehicleQueue = stayVehicles.get(link);
                while (!vehicleQueue.isEmpty() && requestIterator.hasNext()) {
                    AVVehicle toMatchVehicle = vehicleQueue.poll();
                    AVRequest toMatchRequest = requestIterator.next();
                    Coord toMatchRequestCoord = toMatchRequest.getFromLink().getFromNode().getCoord();
                    biConsumer.accept(toMatchVehicle, toMatchRequest);
                    requestsServed.get(toMatchVehicle).add(toMatchRequest);
                    boolean orSucc = openRequests.remove(toMatchRequest);
                    boolean qtSucc = pendingRequestsTree.remove(toMatchRequestCoord.getX(), toMatchRequestCoord.getY(), toMatchRequest);
                    GlobalAssert.that(orSucc == qtSucc && orSucc == true);
                    ++num_matchedRequests;
                }
            }
        }
        return num_matchedRequests;
    }
    
    
    private Queue<AVVehicle> shuffleMyQueue(Queue<AVVehicle> vehicleQueue){
        List<AVVehicle> avList = vehicleQueue.stream().collect(Collectors.toList());
        Collections.shuffle(avList);
        Queue<AVVehicle> returnQueue = new LinkedList<>();
        avList.stream().forEach(v->returnQueue.add(v));
        return returnQueue;        
    };

}
