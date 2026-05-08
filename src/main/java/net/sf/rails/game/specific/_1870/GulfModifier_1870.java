package net.sf.rails.game.specific._1870;

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

public class GulfModifier_1870 implements RevenueDynamicModifier {

    private static final Logger log = LoggerFactory.getLogger(GulfModifier_1870.class);
    private int calculatedBonus = 0;



    @Override
    public boolean prepareModifier(RevenueAdapter revenueAdapter) {
        net.sf.rails.game.PublicCompany comp = revenueAdapter.getCompany();
        if (comp == null) return false;
        
        for (net.sf.rails.game.PrivateCompany priv : comp.getPrivates()) {
            if (priv != null) {
                String id = priv.getId();
                String name = priv.getName() != null ? priv.getName() : "";
                if ("Gulf".equals(id) || name.contains("Gulf")) {
                    return true;
                }
            }
        }
        return false;
    }




    @Override
    public int predictionValue(List<RevenueTrainRun> runs) {
        return calculateGulfBonus(runs);
    }

    @Override
    public int evaluationValue(List<RevenueTrainRun> runs, boolean optimalRuns) {
        int bonus = calculateGulfBonus(runs);
        if (optimalRuns) {
            calculatedBonus = bonus;
        }
        return bonus;
    }

    @Override
    public void adjustOptimalRun(List<RevenueTrainRun> optimalRuns) {
    }

    @Override
    public String prettyPrint(RevenueAdapter revenueAdapter) {
        if (calculatedBonus > 0) {
            return "Gulf Shipping Bonus: $" + calculatedBonus;
        }
        return "";
    }

    private int calculateGulfBonus(List<RevenueTrainRun> runs) {
        int totalBonus = 0;
        if (runs == null) return 0;

        for (RevenueTrainRun run : runs) {
            Set<MapHex> visitedHexes = new HashSet<>();

            if (run.getRunVertices() != null) {
                for (NetworkVertex v : run.getRunVertices()) {
                    if (v != null && v.getHex() != null) visitedHexes.add(v.getHex());
                }
            }

            List<NetworkEdge> edges = run.getEdges(); 
            if (edges != null) {
                for (NetworkEdge e : edges) {
                    List<NetworkVertex> path = e.getVertexPath();
                    if (path != null) {
                        for (NetworkVertex v : path) {
                            if (v != null && v.getHex() != null) visitedHexes.add(v.getHex());
                        }
                    }
                }
            }
            
            for (MapHex hex : visitedHexes) {
                if (hasGulfToken(hex)) {
                    totalBonus += 20;
                }
            }
        }
        return totalBonus;
    }

private boolean hasGulfToken(MapHex hex) {
        if (hex == null || hex.getBonusTokens() == null) return false;
        for (BonusToken t : hex.getBonusTokens()) {
// --- START FIX ---
// --- DELETE ---            if ("Gulf".equals(t.getName())) return true;
            if (t.getName() != null && t.getName().contains("Gulf")) return true;
// --- END FIX ---
        }
        return false;
    }
    
}