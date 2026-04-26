package net.sf.rails.game.specific._1870;

import net.sf.rails.algorithms.RevenueAdapter;
import net.sf.rails.algorithms.RevenueDynamicModifier;
import net.sf.rails.algorithms.RevenueTrainRun;
import net.sf.rails.algorithms.NetworkEdge;
import net.sf.rails.algorithms.NetworkVertex;
import net.sf.rails.game.MapHex;
import net.sf.rails.game.BonusToken;
import net.sf.rails.game.financial.Bank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class CattleModifier_1870 implements RevenueDynamicModifier {

    private static final Logger log = LoggerFactory.getLogger(CattleModifier_1870.class);
    private int calculatedBonus = 0;

@Override
    public boolean prepareModifier(RevenueAdapter revenueAdapter) {
        net.sf.rails.game.PublicCompany comp = revenueAdapter.getCompany();
        if (comp == null || comp.getPortfolioModel() == null) return false;
        
        for (net.sf.rails.game.PrivateCompany priv : comp.getPortfolioModel().getPrivateCompanies()) {
            if (priv != null) {
                String id = priv.getId();
                if ("SCC".equals(id) || "Cattle".equals(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int predictionValue(List<RevenueTrainRun> runs) {
        return calculateCattleBonus(runs);
    }

    @Override
    public int evaluationValue(List<RevenueTrainRun> runs, boolean optimalRuns) {
        int bonus = calculateCattleBonus(runs);
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
            return "Cattle Company Bonus: $" + calculatedBonus;
        }
        return "";
    }

private int calculateCattleBonus(List<RevenueTrainRun> runs) {
        int totalBonus = 0;
        if (runs == null) {
            return 0;
        }


        for (RevenueTrainRun run : runs) {
            Set<MapHex> visitedHexes = new HashSet<>();

            // 1. Check vertices directly included in the run
            if (run.getRunVertices() != null) {
                for (NetworkVertex v : run.getRunVertices()) {
                    if (v != null && v.getHex() != null) {
                        visitedHexes.add(v.getHex());
                    }
                }
            }

            // 2. Check hexes associated with edges
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
            
            java.util.List<String> hexIds = new java.util.ArrayList<>();
            for (MapHex h : visitedHexes) hexIds.add(h.getId());

            // 3. Award $10 once per hex touched by this specific train run
            for (MapHex hex : visitedHexes) {
                boolean hasCattle = hasCattleToken(hex);
                if (hasCattle) {
                    totalBonus += 10;
                }
            }
        }
        
        return totalBonus;
    }

    private boolean hasCattleToken(MapHex hex) {
        if (hex == null || hex.getBonusTokens() == null) return false;
        for (BonusToken t : hex.getBonusTokens()) {
            if ("Cattle".equals(t.getName())) {
                return true;
            }
        }
        return false;
    }


}