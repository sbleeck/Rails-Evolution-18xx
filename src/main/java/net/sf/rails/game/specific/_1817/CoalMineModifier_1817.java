package net.sf.rails.game.specific._1817;

import net.sf.rails.algorithms.RevenueAdapter;
import net.sf.rails.algorithms.RevenueDynamicModifier;
import net.sf.rails.algorithms.RevenueTrainRun;
import net.sf.rails.algorithms.NetworkVertex;
import net.sf.rails.game.MapHex;
import net.sf.rails.game.BonusToken;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class CoalMineModifier_1817 implements RevenueDynamicModifier {

    private int calculatedBonus = 0;

    @Override
    public boolean prepareModifier(RevenueAdapter revenueAdapter) {
        // Always active for 1817
        return true; 
    }

    /**
     * Helper to verify if a hex contains a coal mine.
     */
    private boolean hasCoalMineToken(MapHex hex) {
        if (hex == null || hex.getBonusTokens() == null) return false;
        for (BonusToken t : hex.getBonusTokens()) {
            if (t != null && t.getParent() != null) {
                String id = t.getParent().getId();
                if ("MIN30".equals(id) || "COA60".equals(id) || "MAJ90".equals(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int calculateCoalMineBonus(List<RevenueTrainRun> runs) {
        int totalBonus = 0;
        if (runs == null) return 0;
        
        for (RevenueTrainRun run : runs) {
            Set<MapHex> visitedHexes = new HashSet<>();
            
            // Extract the unique hexes visited in this specific train run
            // FIX: Using getRunVertices() instead of getVertices()
            if (run.getRunVertices() != null) {
                for (NetworkVertex v : run.getRunVertices()) {
                    if (v.getHex() != null) {
                        visitedHexes.add(v.getHex());
                    }
                }
            }
            
            // Apply $10 for each unique coal mine hex visited by this train
            for (MapHex hex : visitedHexes) {
                if (hasCoalMineToken(hex)) {
                    totalBonus += 10;
                }
            }
        }
        return totalBonus;
    }

    @Override
    public int predictionValue(List<RevenueTrainRun> runs) {
        return calculateCoalMineBonus(runs);
    }

    @Override
    public int evaluationValue(List<RevenueTrainRun> runs, boolean optimalRuns) {
        int bonus = calculateCoalMineBonus(runs);
        if (optimalRuns) {
            calculatedBonus = bonus;
        }
        return bonus;
    }

    @Override
    public void adjustOptimalRun(List<RevenueTrainRun> optimalRuns) {
        // No structural adjustments to the run are needed
    }

    @Override
    public String prettyPrint(RevenueAdapter revenueAdapter) {
        if (calculatedBonus > 0) {
            return " + $" + calculatedBonus + " (Coal Mine)";
        }
        return null;
    }
}