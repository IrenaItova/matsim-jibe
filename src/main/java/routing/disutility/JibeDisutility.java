package routing.disutility;


import org.apache.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;
import resources.Properties;
import resources.Resources;
import routing.Gradient;
import routing.disutility.components.LinkAttractiveness;
import routing.disutility.components.JctStress;
import routing.disutility.components.LinkComfort;
import routing.disutility.components.LinkStress;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;

/**
 * Custom walk and bicycle disutility for JIBE
 * based on BicycleTravelDisutility by Dominik Ziemke
 */
public class JibeDisutility implements TravelDisutility {

    private final static Logger logger = Logger.getLogger(JibeDisutility.class);
    private final String mode;
    private final double marginalCostOfTime_s;
    private final double marginalCostOfDistance_m;
    private final double marginalCostOfGradient_m_100m;
    private final double marginalCostOfComfort_m;
    private final double marginalCostAttractiveness_m;
    private final double marginalCostStress_m;
    private final double marginalCostJunction;

    private final TravelTime timeCalculator;

    // Default parameters
    public JibeDisutility(String mode, TravelTime tt) {

        if(!mode.equals(TransportMode.bike) && !mode.equals(TransportMode.walk)) {
            throw new RuntimeException("Mode " + mode + " not suported for JIBE disutility.");
        }

        this.mode = mode;
        this.timeCalculator = tt;
        this.marginalCostOfTime_s = Resources.instance.getMarginalCost(mode,Properties.TIME);
        this.marginalCostOfDistance_m = Resources.instance.getMarginalCost(mode,Properties.DISTANCE);
        this.marginalCostOfGradient_m_100m = Resources.instance.getMarginalCost(mode,Properties.GRADIENT);
        this.marginalCostOfComfort_m = Resources.instance.getMarginalCost(mode,Properties.COMFORT);
        this.marginalCostAttractiveness_m = Resources.instance.getMarginalCost(mode,Properties.ATTRACTIVENESS);
        this.marginalCostStress_m = Resources.instance.getMarginalCost(mode,Properties.LINK_STRESS);
        this.marginalCostJunction = Resources.instance.getMarginalCost(mode,Properties.JUNCTION_STRESS);
        printMarginalCosts();
    }

    // Custom parameters
    public JibeDisutility(String mode, TravelTime tt,
                          double marginalCostOfTime_s, double marginalCostOfDistance_m,
                          double marginalCostOfGradient_m_100m, double marginalCostOfComfort_m,
                          double marginalCostAttractiveness_m, double marginalCostStress_m,
                          double marginalCostJunction) {

        if(!mode.equals(TransportMode.bike) && !mode.equals(TransportMode.walk)) {
            throw new RuntimeException("Mode " + mode + " not supported for JIBE disutility.");
        }

        this.mode = mode;
        this.timeCalculator = tt;
        this.marginalCostOfTime_s = marginalCostOfTime_s;
        this.marginalCostOfDistance_m = marginalCostOfDistance_m;
        this.marginalCostOfGradient_m_100m = marginalCostOfGradient_m_100m;
        this.marginalCostOfComfort_m = marginalCostOfComfort_m;
        this.marginalCostAttractiveness_m = marginalCostAttractiveness_m;
        this.marginalCostStress_m = marginalCostStress_m;
        this.marginalCostJunction = marginalCostJunction;
        printMarginalCosts();
    }

    private void printMarginalCosts() {
        logger.info("Initialised JIBE disutility with the following parameters:" +
                "\nMode: " + this.mode +
                "\nMarginal cost of time (/s): " + this.marginalCostOfTime_s +
                "\nMarginal cost of distance (/m): " + this.marginalCostOfDistance_m +
                "\nMarginal cost of gradient (m/100m): " + this.marginalCostOfGradient_m_100m +
                "\nMarginal cost of surface comfort (/m): " + this.marginalCostOfComfort_m +
                "\nMarginal cost of attractiveness (/m): " + this.marginalCostAttractiveness_m +
                "\nMarginal cost of LINK stress (/m): " + this.marginalCostStress_m +
                "\nMarginal cost of JUNCTION stress (/m): " + this.marginalCostJunction);
    }

    @Override
    public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
        double travelTime = timeCalculator.getLinkTravelTime(link, time, person, vehicle);

        double distance = link.getLength();

        // Travel time disutility
        double disutility = marginalCostOfTime_s * travelTime;

        // Distance disutility (0 by default)
        disutility += marginalCostOfDistance_m * distance;

        // Gradient factor
        double gradient = Gradient.getGradient(link);
        if(gradient < 0.) gradient = 0.;
        disutility += marginalCostOfGradient_m_100m * gradient * distance;

        // Comfort of surface
        double comfortFactor = LinkComfort.getComfortFactor(link);
        disutility += marginalCostOfComfort_m * comfortFactor * distance;

        // Attractiveness factors
        double attractiveness = LinkAttractiveness.getDayAttractiveness(link);
        disutility += marginalCostAttractiveness_m * attractiveness * distance;

        // Stress factors
        double stress = LinkStress.getStress(link,mode);
        disutility += marginalCostStress_m * stress * distance;

        // Junction stress factor
        double junctionStress = JctStress.getJunctionStress(link,mode);
        disutility += marginalCostJunction * junctionStress;

        return disutility;

    }

    @Override
    public double getLinkMinimumTravelDisutility(Link link) {
        return 0;
    }

    public double getMarginalCostAttractiveness_m() {
        return marginalCostAttractiveness_m;
    }

    public double getMarginalCostStress_m() {
        return marginalCostStress_m;
    }

    public double getMarginalCostJunction() {
        return marginalCostJunction;
    }

    public double getTimeComponent(Link link, double time, Person person, Vehicle vehicle) {
        double travelTime = timeCalculator.getLinkTravelTime(link, time, person, vehicle);
        return marginalCostOfTime_s * travelTime;
    }

    public double getDistanceComponent(Link link) {
        return marginalCostOfDistance_m * link.getLength();
    }

    public double getGradientComponent(Link link) {
        double gradient = Gradient.getGradient(link);
        if(gradient < 0.) gradient = 0.;
        return marginalCostOfGradient_m_100m * gradient * link.getLength();
    }

    public double getSurfaceComponent(Link link) {
        double comfortFactor = LinkComfort.getComfortFactor(link);
        return marginalCostOfComfort_m * comfortFactor * link.getLength();
    }

    public double getAttractivenessComponent(Link link) {
        double attractiveness = LinkAttractiveness.getDayAttractiveness(link);
        return marginalCostAttractiveness_m * attractiveness * link.getLength();
    }

    public double getStressComponent(Link link) {
        double stress = LinkStress.getStress(link, mode);
        return marginalCostStress_m * stress * link.getLength();
    }

    public double getJunctionComponent(Link link) {
        double jctStress = JctStress.getJunctionStress(link, mode);
        return marginalCostJunction * jctStress;
    }
}
