package net.sf.rails.game.specific._1870;

import net.sf.rails.algorithms.RevenueAdapter;
import net.sf.rails.algorithms.RevenueDynamicModifier;
import net.sf.rails.algorithms.RevenueTrainRun;
import net.sf.rails.algorithms.NetworkVertex;
import net.sf.rails.game.MapHex;
import net.sf.rails.game.BonusToken;
import net.sf.rails.game.Phase;
import net.sf.rails.game.PublicCompany;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DestinationModifier_1870 implements RevenueDynamicModifier {

    private static final Logger log = LoggerFactory.getLogger(DestinationModifier_1870.class);
    private int calculatedBonus = 0;
    private RevenueAdapter revenueAdapter;
    private Phase phase;

    @Override
    public boolean prepareModifier(RevenueAdapter revenueAdapter) {
        PublicCompany comp = revenueAdapter.getCompany();
        if (comp == null) return false;
        
        this.revenueAdapter = revenueAdapter;
        this.phase = revenueAdapter.getPhase();
        
        // Only active if the company has achieved its connection run
        return comp.hasReachedDestination();
    }

    @Override
    public int predictionValue(List<RevenueTrainRun> runs) {
        return calculateDestinationBonus(runs);
    }

    @Override
    public int evaluationValue(List<RevenueTrainRun> runs, boolean isFinal) {
        calculatedBonus = calculateDestinationBonus(runs);
        return calculatedBonus;
    }

@Override
    public String prettyPrint(RevenueAdapter revenueAdapter) {
        if (calculatedBonus > 0) {
            return "Destination Bonus: $" + calculatedBonus;
        }
        return "";
    }

    @Override
    public void adjustOptimalRun(List<RevenueTrainRun> optimalRuns) {
        // No path adjustment is necessary for the destination modifier
    }

    private int calculateDestinationBonus(List<RevenueTrainRun> runs) {
        int totalBonus = 0;
        if (runs == null) return 0;
        
        PublicCompany comp = revenueAdapter.getCompany();
        String destTokenName = comp.getId() + "_Dest";

        for (RevenueTrainRun run : runs) {
            List<NetworkVertex> vertices = run.getRunVertices();
            if (vertices == null || vertices.isEmpty()) continue;

            // The rules require the destination to be at the END of a run.
            // A train run path lists vertices sequentially, so we check index 0 and the last index.
            NetworkVertex startNode = vertices.get(0);
            NetworkVertex endNode = vertices.get(vertices.size() - 1);

            if (checkHexForBonus(startNode, destTokenName)) {
                totalBonus += revenueAdapter.getVertexValue(startNode, run.getTrain(), phase);
            } else if (vertices.size() > 1 && checkHexForBonus(endNode, destTokenName)) {
                totalBonus += revenueAdapter.getVertexValue(endNode, run.getTrain(), phase);
            }
        }
        return totalBonus;
    }

    private boolean checkHexForBonus(NetworkVertex v, String destTokenName) {
        if (v == null || v.getHex() == null) return false;
        MapHex hex = v.getHex();
        if (hex.getBonusTokens() == null) return false;
        
        for (BonusToken t : hex.getBonusTokens()) {
            if (destTokenName.equals(t.getName())) {
                return true;
            }
        }
        return false;
    }
}