package trads.calculate;

import org.matsim.core.utils.misc.Counter;
import routing.TravelAttribute;
import trip.Place;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.vehicles.Vehicle;
import routing.disutility.JibeDisutility;
import trip.Trip;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NetworkIndicatorCalculator implements Runnable {

    private final ConcurrentLinkedQueue<Trip> trips;
    private final Counter counter;
    private final String route;
    private final Vehicle vehicle;

    private final Place origin;
    private final Place destination;

    private final LeastCostPathCalculator pathCalculator;

    private final TravelDisutility travelDisutility;
    private final Network routingNetwork;
    private final Network xy2lNetwork;
    private final LinkedHashMap<String, TravelAttribute> additionalAttributes;
    private final boolean savePath;

    public NetworkIndicatorCalculator(ConcurrentLinkedQueue<Trip> trips, Counter counter, String route,
                                      Place origin, Place destination, Vehicle vehicle,
                                      Network routingNetwork, Network xy2lNetwork,
                                      LeastCostPathCalculator pathCalculator, TravelDisutility travelDisutility,
                                      LinkedHashMap<String, TravelAttribute> additionalAttributes, boolean savePath) {
        this.trips = trips;
        this.counter = counter;
        this.route = route;
        this.origin = origin;
        this.destination = destination;
        this.vehicle = vehicle;
        this.routingNetwork = routingNetwork;
        this.xy2lNetwork = xy2lNetwork;
        this.pathCalculator = pathCalculator;
        this.travelDisutility = travelDisutility;
        this.additionalAttributes = additionalAttributes;
        this.savePath = savePath;
    }

    public void run() {

        while(true) {
            Trip trip = this.trips.poll();
            if(trip == null) {
                return;
            }

            this.counter.incCounter();
            Map<String,Object> results = new LinkedHashMap<>();

            if(trip.routable(origin, destination)) {
                Coord cOrig = trip.getCoord(origin);
                Coord cDest = trip.getCoord(destination);
                Node nOrig = routingNetwork.getNodes().get(NetworkUtils.getNearestLinkExactly(xy2lNetwork, cOrig).getToNode().getId());
                Node nDest = routingNetwork.getNodes().get(NetworkUtils.getNearestLinkExactly(xy2lNetwork, cDest).getToNode().getId());

                // Calculate least cost path
                LeastCostPathCalculator.Path path = pathCalculator.calcLeastCostPath(nOrig, nDest, trip.getStartTime(), null, vehicle);

                // If JibeDisutility, get marginal costs
                if(travelDisutility instanceof JibeDisutility) {
                    results.put("mc_ambience", ((JibeDisutility) travelDisutility).getMarginalCostAmbience_m());
                    results.put("mc_stress", ((JibeDisutility) travelDisutility).getMarginalCostStress_m());
                }

                // Set cost and time
                results.put("cost",path.travelCost);
                results.put("time",path.travelTime);

                // Set distance
                double dist = path.links.stream().mapToDouble(Link::getLength).sum();
                results.put("dist",dist);

                // Set path
                if(savePath) {
                    int[] edgeIDs = path.links.stream().mapToInt(l -> (int) l.getAttributes().getAttribute("edgeID")).toArray();
                    trip.setRoutePath(route,nOrig.getCoord(),edgeIDs,dist,path.travelTime);
                }

                // Additional attributes
                if(additionalAttributes != null) {
                    for (Map.Entry<String,TravelAttribute> e : additionalAttributes.entrySet()) {
                        String name = e.getKey();
                        Double result;
                        try {
                            result = path.links.stream().mapToDouble(l -> e.getValue().getTravelAttribute(l,travelDisutility)).sum();
                        } catch (ClassCastException exception) {
                            result = null;
                        }
                        results.put(name,result);
                    }
                }
                trip.setAttributes(route,results);
            }
        }
    }
}