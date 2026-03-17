package net.sf.rails.game.specific._1817;

import net.sf.rails.algorithms.RevenueAdapter;
import net.sf.rails.algorithms.RevenueDynamicModifier;
import net.sf.rails.algorithms.RevenueTrainRun;
import net.sf.rails.algorithms.NetworkEdge;
import net.sf.rails.algorithms.NetworkVertex;
import net.sf.rails.game.MapHex;
import net.sf.rails.game.BonusToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class BridgeModifier_1817 implements RevenueDynamicModifier {

    private static final Logger log = LoggerFactory.getLogger(BridgeModifier_1817.class);
    private int calculatedBonus = 0;

    @Override
    public boolean prepareModifier(RevenueAdapter revenueAdapter) {
        log.info("1817_REVENUE: BridgeModifier prepared.");
        return true;
    }

    @Override
    public int predictionValue(List<RevenueTrainRun> runs) {
        return calculateBridgeBonus(runs);
    }

    @Override
    public int evaluationValue(List<RevenueTrainRun> runs, boolean optimalRuns) {
        int bonus = calculateBridgeBonus(runs);
        if (optimalRuns) {
            calculatedBonus = bonus;
            if (bonus > 0) {
                log.info("1817_REVENUE: Total Bridge Bonus calculated: ${}", bonus);
            }
        }
        return bonus;
    }

    @Override
    public void adjustOptimalRun(List<RevenueTrainRun> optimalRuns) {
    }

    @Override
    public String prettyPrint(RevenueAdapter revenueAdapter) {
        if (calculatedBonus > 0) {
            return " + $" + calculatedBonus + " (Bridge)";
        }
        return null;
    }

    private boolean hasBridgeToken(MapHex hex) {
        if (hex == null || hex.getBonusTokens() == null)
            return false;

        for (BonusToken t : hex.getBonusTokens()) {
            if (t != null) {
                if ("Bridge".equals(t.getName()) || "Bridge".equals(t.getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private int calculateBridgeBonus(List<RevenueTrainRun> runs) {
        int totalBonus = 0;
        if (runs == null) return 0;
        
        for (RevenueTrainRun run : runs) {
            Set<MapHex> visitedHexes = new HashSet<>();
            
            // 1. Check hexes associated with explicit vertices (Cities like Louisville/Cincinnati)
            if (run.getRunVertices() != null) {
                for (NetworkVertex v : run.getRunVertices()) {
                    if (v != null && v.getHex() != null) {
                        visitedHexes.add(v.getHex());
                    }
                }
            }

            // 2. Check edges for hidden path hexes (if any)
            List<NetworkEdge> edges = run.getEdges(); 
            if (edges != null) {
                for (NetworkEdge e : edges) {
                    List<NetworkVertex> path = e.getVertexPath();
                    if (path != null) {
                        for (NetworkVertex v : path) {
                            if (v != null && v.getHex() != null) {
                                visitedHexes.add(v.getHex());
                            }
                        }
                    }
                }
            }
            
            // 3. Award $10 once per bridge hex touched by this train run
            for (MapHex hex : visitedHexes) {
                if (hasBridgeToken(hex)) {
                    totalBonus += 10;
                    log.info("1817_REVENUE: Bridge bonus applied for hex {}", hex.getId());
                }
            }
        }
        return totalBonus;
    }
}