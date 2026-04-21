package net.sf.rails.game.specific._1870;

import net.sf.rails.game.HexSide;
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
        // A16, B17, E18, M14, O18 (River flows NW to SE)
        addTopology(Arrays.asList("A16", "B17", "E18", "M14", "O18"), Set.of(4, 5), Set.of(1, 2));
        // C18, F19 (River flows NW to SW)
        addTopology(Arrays.asList("C18", "F19"), Set.of(5), Set.of(1, 2, 3));
        // D17, L13 (River flows NE to SE)
        addTopology(Arrays.asList("D17", "L13"), Set.of(0, 4, 5), Set.of(2));
        // G18, H17, I16, J15, K14 (River flows NE to SW)
        addTopology(Arrays.asList("G18", "H17", "I16", "J15", "K14"), Set.of(0, 5), Set.of(2, 3));
        // N15 (River flows NW to E)
        addTopology(Arrays.asList("N15"), Set.of(3, 4, 5), Set.of(1));
        // N17 (River flows W to SE)
        addTopology(Arrays.asList("N17"), Set.of(4), Set.of(0, 1, 2));
    }

    private static void addTopology(List<String> hexes, Set<Integer> west, Set<Integer> east) {
        RiverTopology topo = new RiverTopology(west, east);
        for (String hex : hexes) {
            RIVER_HEXES.put(hex, topo);
        }
    }

    public static boolean isCrossingRiver(Tile tile, MapHex hex, int orientation) {
        RiverTopology topo = RIVER_HEXES.get(hex.getId());
        if (topo == null) {
            System.out.println(">>> DEBUG: isCrossingRiver - Hex " + hex.getId() + " is NOT in RIVER_HEXES.");
            return false; // Not a river hex
        }

        System.out.println(">>> DEBUG: Hex " + hex.getId() + " is a river. Evaluating topology. West=" + 
                 topo.westBank + ", East=" + topo.eastBank + ", Orientation=" + orientation);

        for (Integer absWest : topo.westBank) {
            for (Integer absEast : topo.eastBank) {
                // Adjust map absolute sides to tile relative sides based on orientation
                int tileWest = (absWest - orientation + 6) % 6;
                int tileEast = (absEast - orientation + 6) % 6;

                System.out.println(">>> DEBUG: Checking link between MapWest:" + absWest + " (TileSide:" + tileWest + 
                         ") and MapEast:" + absEast + " (TileSide:" + tileEast + ")");

                if (tileConnectsSides(tile, tileWest, tileEast)) {
                    System.out.println(">>> DEBUG: tileConnectsSides RETURNED TRUE for TileSide " + tileWest + " to TileSide " + tileEast);
                    return true;
                }
            }
        }
        System.out.println(">>> DEBUG: isCrossingRiver evaluated all combinations. RETURNED FALSE.");
        return false;
    }

    private static boolean tileConnectsSides(Tile tile, int side1, int side2) {
        HexSide hs1 = HexSide.get(side1);
        HexSide hs2 = HexSide.get(side2);
        
        System.out.println(">>> DEBUG:    tileConnectsSides check: internal TileSide " + side1 + " and TileSide " + side2);
        
        // 1. Check direct track connections (e.g., standard track segments)
        for (Track track : tile.getTracks()) {
            TrackPoint start = track.getStart();
            TrackPoint end = track.getEnd();
            System.out.println(">>> DEBUG:      Evaluating Track Segment: start=" + start + ", end=" + end);
            if ((start.equals(hs1) && end.equals(hs2)) || 
                (start.equals(hs2) && end.equals(hs1))) {
                return true;
            }
        }

        // 2. Check connections bridged through a central station (City/Town)
        for (Station station : tile.getStations()) {
            boolean westConnected = false;
            boolean eastConnected = false;
            
            for (Track track : tile.getTracks()) {
                TrackPoint start = track.getStart();
                TrackPoint end = track.getEnd();
                
                if (start.equals(station) || end.equals(station)) {
                    if (start.equals(hs1) || end.equals(hs1)) westConnected = true;
                    if (start.equals(hs2) || end.equals(hs2)) eastConnected = true;
                }
            }
            if (westConnected && eastConnected) {
                return true;
            }
        }

        return false;
    }

    private static class RiverTopology {
        Set<Integer> westBank;
        Set<Integer> eastBank;

        RiverTopology(Set<Integer> west, Set<Integer> east) {
            this.westBank = west;
            this.eastBank = east;
        }
    }
}