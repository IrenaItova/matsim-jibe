package trads.io;

import com.google.common.math.LongMath;
import org.apache.log4j.Logger;
import trip.Trip;

import java.io.*;
import java.util.*;

import static trip.Place.*;
import static trads.io.TradsAttributes.*;

public class TradsCsvWriter {

    private final static Logger logger = Logger.getLogger(TradsCsvWriter.class);
    private final static String SEP = ",";
    private static final String NL = "\n";

    // Write results to csv file
    public static void write(Set<Trip> trips, String filePath, Map<String, List<String>> attributes) {

        PrintWriter out = openFileForSequentialWriting(new File(filePath));
        assert out != null;

        // Route names
        Set<String> routeNames = attributes.keySet();

        // Route attributes
        Set<String> allAttributes = new LinkedHashSet<>();
        for(List<String> stringList : attributes.values()) {
            allAttributes.addAll(stringList);
        }

        // Create header
        out.println(createHeader(allAttributes));

        int counter = 0;
        for (Trip trip : trips) {
            counter++;
            if (LongMath.isPowerOfTwo(counter)) {
                logger.info(counter + " records written.");
            }
            out.print(createRow(trip,routeNames,allAttributes));
        }
        out.close();
        logger.info("Wrote " + counter + " trips to " + filePath);
    }

    private static String createHeader(Set<String> allAttributes) {
        StringBuilder builder = new StringBuilder();

        // Trip identifiers
        builder.append(HOUSEHOLD_ID).
                append(SEP).append(PERSON_ID).
                append(SEP).append(TRIP_ID).
                append(SEP).append("HomeWithinBoundary").
                append(SEP).append("OriginWithinBoundary").
                append(SEP).append("DestinationWithinBoundary").
                append(SEP).append("SameHomeAndDest").
                append(SEP).append("SameOrigAndDest").
                append(SEP).append("OriginZone").
                append(SEP).append("DestinationZone").
                append(SEP).append("Route").
                append(SEP).append("PathId");

        // Route attributes
        for(String attribute : allAttributes) {
            builder.append(SEP).append(attribute);
        }

        return builder.toString();
    }

    private static String createRow(Trip trip, Set<String> routes, Set<String> allAttributes) {
        StringBuilder builder = new StringBuilder();

        for(String route : routes) {
            // Trip identifiers
            builder.append(trip.getHouseholdId()).
                    append(SEP).append(trip.getPersonId()).
                    append(SEP).append(trip.getTripId()).
                    append(SEP).append(trip.isWithinBoundary(HOME)).
                    append(SEP).append(trip.isWithinBoundary(ORIGIN)).
                    append(SEP).append(trip.isWithinBoundary(DESTINATION)).
                    append(SEP).append(trip.match(HOME, DESTINATION)).
                    append(SEP).append(trip.match(ORIGIN, DESTINATION)).
                    append(SEP).append(trip.getZone(ORIGIN)).
                    append(SEP).append(trip.getZone(DESTINATION)).
                    append(SEP).append(route).
                    append(SEP).append(trip.getPathIndex(route));

            // Attributes
            for(String attribute : allAttributes) {
                builder.append(SEP).append(trip.getAttribute(route, attribute));
            }

            // New line
            builder.append(NL);
        }

        return builder.toString();
    }

    private static PrintWriter openFileForSequentialWriting(File outputFile) {
        if (outputFile.getParent() != null) {
            File parent = outputFile.getParentFile();
            parent.mkdirs();
        }

        try {
            FileWriter fw = new FileWriter(outputFile, false);
            BufferedWriter bw = new BufferedWriter(fw);
            return new PrintWriter(bw);
        } catch (IOException var5) {
            logger.info("Could not open file <" + outputFile.getName() + ">.");
            return null;
        }
    }
}
