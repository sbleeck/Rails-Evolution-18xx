package net.sf.rails.game.specific._1870;

import net.sf.rails.game.MapHex;
import net.sf.rails.game.Station;
import net.sf.rails.game.Tile;
import net.sf.rails.game.Track;
import net.sf.rails.game.TrackPoint;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MississippiRiverValidator {

    private static final Logger log = LoggerFactory.getLogger(MississippiRiverValidator.class);

    private static final Map<String, RiverTopology> RIVER_HEXES = new HashMap<>();

    static {
        // Corrected topology for 1870 hexes.
        // Map sides: 0:NE, 1:E, 2:SE, 3:SW, 4:W, 5:NW
        
        // C18 (St. Louis), F19: West bank is sides 4 and 5. East bank is 0, 1, 2, 3.
        addTopology(Arrays.asList("C18", "F19"), Set.of(4, 5), Set.of(0, 1, 2, 3));
        
        // A16, B17, E18, M14, O18
        addTopology(Arrays.asList("A16", "B17", "E18", "M14", "O18"), Set.of(4, 5), Set.of(1, 2));

        // D17, L13
        addTopology(Arrays.asList("D17", "L13"), Set.of(0, 4, 5), Set.of(1, 2, 3));

        // G18, H17, I16, J15, K14, K16 (Memphis)
        addTopology(Arrays.asList("G18", "H17", "I16", "J15", "K14", "K16"), Set.of(4, 5), Set.of(1, 2));

        // N15, N17
        addTopology(Arrays.asList("N15"), Set.of(3, 4, 5), Set.of(1));
        addTopology(Arrays.asList("N17"), Set.of(4, 5), Set.of(0, 1, 2));
    }

    private static void addTopology(List<String> hexes, Set<Integer> west, Set<Integer> east) {
        RiverTopology topo = new RiverTopology(west, east);
        for (String id : hexes) {
            RIVER_HEXES.put(id, topo);
        }
    }

    public static boolean isCrossingRiver(Tile tile, MapHex hex, int orientation) {
        RiverTopology topo = RIVER_HEXES.get(hex.getId());
        if (topo == null) return false;

        log.info(">>> DEBUG: Hex {} river check. West={}, East={}, Orientation={}", 
                 hex.getId(), topo.westBank, topo.eastBank, orientation);

        for (Integer absWest : topo.westBank) {
            for (Integer absEast : topo.eastBank) {
                // Map absolute hex sides to internal tile sides
                int tileWest = (absWest - orientation + 6) % 6;
                int tileEast = (absEast - orientation + 6) % 6;
                
                if (tileConnectsSides(tile, tileWest, tileEast)) {
                    log.info(">>> DEBUG: Crossing detected: Map Side {} <-> Map Side {}", absWest, absEast);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean tileConnectsSides(Tile tile, int ts1, int ts2) {
        String side1 = "side " + ts1;
        String side2 = "side " + ts2;

        // 1. Direct tracks
        for (Track track : tile.getTracks()) {
            String start = track.getStart().toString();
            String end = track.getEnd().toString();
            
            if ((start.equals(side1) && end.equals(side2)) || 
                (start.equals(side2) && end.equals(side1))) {
                return true;
            }
        }

        // 2. Connections through stations
        for (Station station : tile.getStations()) {
            boolean hasS1 = false;
            boolean hasS2 = false;
            String stationId = station.toString();
            
            for (Track track : tile.getTracks()) {
                String start = track.getStart().toString();
                String end = track.getEnd().toString();
                
                if (start.equals(stationId) || end.equals(stationId)) {
                    if (start.equals(side1) || end.equals(side1)) hasS1 = true;
                    if (start.equals(side2) || end.equals(side2)) hasS2 = true;
                }
            }
            if (hasS1 && hasS2) return true;
        }

        return false;
    }

    private static class RiverTopology {
        final Set<Integer> westBank;
        final Set<Integer> eastBank;

        RiverTopology(Set<Integer> west, Set<Integer> east) {
            this.westBank = west;
            this.eastBank = east;
        }
    }
}