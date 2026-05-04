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
import net.sf.rails.game.GameManager;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DestinationModifier_1870 implements RevenueDynamicModifier {

    private static final Logger log = LoggerFactory.getLogger(DestinationModifier_1870.class);
    private int calculatedBonus = 0;
    private RevenueAdapter revenueAdapter;
    private Phase phase;
    private boolean isConnectionRunRound = false;
    private MapHex homeHex;
    private MapHex destHex;

    @Override
    public boolean prepareModifier(RevenueAdapter revenueAdapter) {
        PublicCompany comp = revenueAdapter.getCompany();
        if (comp == null) return false;
        
        this.revenueAdapter = revenueAdapter;
        this.phase = revenueAdapter.getPhase();
        
GameManager gm = revenueAdapter.getRoot().getGameManager();
        this.isConnectionRunRound = (gm.getCurrentRound() instanceof ConnectionRunRound_1870);

        if (this.isConnectionRunRound) {
            this.destHex = comp.getDestinationHex();
            this.homeHex = comp.getHomeHexes().isEmpty() ? null : comp.getHomeHexes().get(0);
        }

        // Active if it's the Connection Run OR if the destination bonus token is in play
        return comp.hasReachedDestination() || this.isConnectionRunRound;

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

@Override
    public int predictionValue(List<RevenueTrainRun> runs) {
        int actualBonus = calculateDestinationBonus(runs);
        
        // The DFS RevenueCalculator uses predictionValue to aggressively prune branches.
        // Because the connection run requires a specific path that might have lower 
        // base values until the destination is reached, the engine will prematurely 
        // prune the correct path if we do not provide an optimistic prediction.
        if (isConnectionRunRound && actualBonus == 0) {
            // If we haven't secured the bonus yet, add a safe optimistic upper bound
            // to keep the search branch alive so subsequent trains can attempt the connection.
            // 200 is a safe upper bound for a doubled late-game destination city.
            return 200; 
        }
        
        return actualBonus;
    }

    @Override
    public int evaluationValue(List<RevenueTrainRun> runs, boolean isFinal) {
        if (!isValidConnectionRun(runs)) {
            return -999999;
        }
        calculatedBonus = calculateDestinationBonus(runs);
        return calculatedBonus;
    }

    private boolean isValidConnectionRun(List<RevenueTrainRun> runs) {
        if (!isConnectionRunRound) return true;
        if (destHex == null || homeHex == null) return true; // Safety fallback

        for (RevenueTrainRun run : runs) {
            if (!run.hasAValidRun()) continue;

            List<NetworkVertex> vertices = run.getRunVertices();
            if (vertices == null || vertices.isEmpty()) continue;

            String firstId = vertices.get(0).getHex().getId();
            String lastId = vertices.get(vertices.size() - 1).getHex().getId();

            boolean matchesDirect = (firstId.equals(homeHex.getId()) && lastId.equals(destHex.getId()));
            boolean matchesReverse = (firstId.equals(destHex.getId()) && lastId.equals(homeHex.getId()));

            if (matchesDirect || matchesReverse) {
                return true; // Found the required connection train
            }
        }
        
        return false;
    }
    

}