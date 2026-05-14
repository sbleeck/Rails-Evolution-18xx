package net.sf.rails.game.specific._1870;

import net.sf.rails.algorithms.RevenueAdapter;
import net.sf.rails.algorithms.RevenueBonus;
import net.sf.rails.algorithms.RevenueStaticModifier;
import net.sf.rails.algorithms.NetworkVertex;
import net.sf.rails.game.MapHex;
import net.sf.rails.game.BonusToken;

public class GulfModifier_1870 implements RevenueStaticModifier {

    @Override
    public boolean modifyCalculator(RevenueAdapter revenueAdapter) {
        net.sf.rails.game.PublicCompany comp = revenueAdapter.getCompany();
        if (comp == null) return false;

        boolean applied = false;
        String compIdLow = comp.getId() != null ? comp.getId().toLowerCase() : "";

        for (NetworkVertex v : revenueAdapter.getVertices()) {
            MapHex hex = v.getHex();
            if (hex != null && hex.getBonusTokens() != null) {
                for (BonusToken t : hex.getBonusTokens()) {
                    String tName = t.getName();
                    if (tName != null) {
                        String lowerName = tName.toLowerCase();
                        if (lowerName.contains("gulf")) {
                            boolean ownsGulf = !compIdLow.isEmpty() && lowerName.startsWith(compIdLow + "_");
                            if (!ownsGulf) {
                                for (net.sf.rails.game.PrivateCompany priv : comp.getPrivates()) {
                                    String privId = priv.getId();
                                    if ("Gulf".equalsIgnoreCase(privId) || (priv.getName() != null && priv.getName().toLowerCase().contains("gulf"))) {
                                        ownsGulf = true; break;
                                    }
                                }
                            }
                            int bonusVal = 0;
                            if (ownsGulf) {
                                bonusVal = 20;
                            } else if (lowerName.contains("open")) {
                                bonusVal = 10;
                            }
                            
                            if (bonusVal > 0) {
                                RevenueBonus bonus = new RevenueBonus(bonusVal, "Gulf");
                                bonus.addVertex(v);
                                revenueAdapter.addRevenueBonus(bonus);
                                applied = true;
                            }
                        }
                    }
                }
            }
        }
        return applied;
    }

    @Override
    public String prettyPrint(RevenueAdapter revenueAdapter) {
        return null;
    }
}