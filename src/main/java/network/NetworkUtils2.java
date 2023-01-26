package network;

// Additional utils beyond NetworkUtils

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;
import routing.disutility.JibeDisutility;

import java.util.*;
import java.util.function.Predicate;

public class NetworkUtils2 {

    private final static Logger log = Logger.getLogger(NetworkUtils2.class);

    // Extracts mode-specific network  (e.g. walk network, car network, cycle network)
    public static Network extractModeSpecificNetwork(Network network, String transportMode) {
        Network modeSpecificNetwork = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(network).filter(modeSpecificNetwork, Collections.singleton(transportMode));
        NetworkUtils.runNetworkCleaner(modeSpecificNetwork);
        return modeSpecificNetwork;
    }

    public static void extractFromNodes(Network network, Set<Id<Node>> nodeIds) {
        IdSet<Node> nodesToRemove = new IdSet<>(Node.class);
        for (Node node : network.getNodes().values()) {
            if(!nodeIds.contains(node.getId())) nodesToRemove.add(node.getId());
        }
        for (Id<Node> nodeId : nodesToRemove) network.removeNode(nodeId);
    }

    public static void identifyDisconnectedLinks(Network network, String transportMode) {
        Network modeSpecificNetwork = extractModeSpecificNetwork(network, transportMode);
        for(Link link : network.getLinks().values()) {
            boolean disconnected = false;
            if (link.getAllowedModes().contains(transportMode)) {
                disconnected = !modeSpecificNetwork.getLinks().containsKey(link.getId());
            }
            link.getAttributes().putAttribute("disconnected_" + transportMode, disconnected);
        }
    }

    // Extracts network of usable nearest links to start/end journey (e.g. a car trip cannot start on a motorway)
    public static Network extractXy2LinksNetwork(Network network, Predicate<Link> xy2linksPredicate) {
        Network xy2lNetwork = NetworkUtils.createNetwork();
        NetworkFactory nf = xy2lNetwork.getFactory();
        for (Link link : network.getLinks().values()) {
            if (xy2linksPredicate.test(link)) {
                // okay, we need that link
                Node fromNode = link.getFromNode();
                Node xy2lFromNode = xy2lNetwork.getNodes().get(fromNode.getId());
                if (xy2lFromNode == null) {
                    xy2lFromNode = nf.createNode(fromNode.getId(), fromNode.getCoord());
                    xy2lNetwork.addNode(xy2lFromNode);
                }
                Node toNode = link.getToNode();
                Node xy2lToNode = xy2lNetwork.getNodes().get(toNode.getId());
                if (xy2lToNode == null) {
                    xy2lToNode = nf.createNode(toNode.getId(), toNode.getCoord());
                    xy2lNetwork.addNode(xy2lToNode);
                }
                Link xy2lLink = nf.createLink(link.getId(), xy2lFromNode, xy2lToNode);
                xy2lLink.setAllowedModes(link.getAllowedModes());
                xy2lLink.setCapacity(link.getCapacity());
                xy2lLink.setFreespeed(link.getFreespeed());
                xy2lLink.setLength(link.getLength());
                xy2lLink.setNumberOfLanes(link.getNumberOfLanes());
                xy2lNetwork.addLink(xy2lLink);
            }
        }
        return xy2lNetwork;
    }

    public static Map<Link,Double> precalculateLinkMarginalDisutilities(Network network, TravelDisutility disutility, double time, Person person, Vehicle vehicle) {
        log.info("Precalculating marginal disutilities for each link...");
        Map<Link,Double> marginalDisutilities = new HashMap<>(network.getLinks().size());
        Counter counter = new Counter("Processing node "," / " + network.getLinks().size());
        for(Link link : network.getLinks().values()) {
            counter.incCounter();
            double linkDisutility = disutility.getLinkTravelDisutility(link,time,person,vehicle);
            if(disutility instanceof JibeDisutility) {
                linkDisutility -= ((JibeDisutility) disutility).getJunctionComponent(link); // todo: check this is happening
            }
            marginalDisutilities.put(link, linkDisutility / link.getLength());
        }
        return marginalDisutilities;
    }

    public static Map<Node,Double> precalculateNodeMarginalDisutilities(Network network, TravelDisutility disutility, double time, Person person, Vehicle vehicle) {
        Map<Link,Double> linkMarginalDisutilities = precalculateLinkMarginalDisutilities(network, disutility, time, person, vehicle);
        Map<Node,Double> nodeMarginalDisutilities = new HashMap<>();

        for(Node node : network.getNodes().values()) {
            Set<Link> links = new HashSet<>();
            links.addAll(node.getOutLinks().values());
            links.addAll(node.getInLinks().values());
            if (links.size() > 0) {
                nodeMarginalDisutilities.put(node,links.stream().mapToDouble(linkMarginalDisutilities::get).average().orElseThrow());
            } else {
                Link closestLink = NetworkUtils.getNearestLinkExactly(network,node.getCoord());
                nodeMarginalDisutilities.put(node,linkMarginalDisutilities.get(closestLink));
            }
        }
        return nodeMarginalDisutilities;
    }


}
