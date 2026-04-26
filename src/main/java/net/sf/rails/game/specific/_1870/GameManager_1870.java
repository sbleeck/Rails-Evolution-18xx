package net.sf.rails.game.specific._1870;

import net.sf.rails.common.LocalText;
import net.sf.rails.common.ReportBuffer;
import net.sf.rails.game.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameManager_1870 extends GameManager {

    private static final Logger log = LoggerFactory.getLogger(GameManager_1870.class);

    public GameManager_1870(RailsRoot parent, String id) {
        super(parent, id);
    }

    /**
     * Triggers the 1870-specific Share Protection interaction.
     * When a player sells shares, the President of that company may immediately
     * buy them from the pool to protect the share price from dropping.
     * * @param currentRound The currently active StockRound.
     * 
     * @param company    The company whose shares were just sold.
     * @param sharesSold The number of shares that were sold.
     */
    public void startShareProtectionRound(Round currentRound, PublicCompany company, net.sf.rails.game.Player seller,
            int sharesSold) {

        setInterruptedRound(currentRound);

        String roundName = "ShareProtectionRound_in_" + currentRound.getId() + "_" + System.nanoTime();

        ShareProtectionRound_1870 spr = (ShareProtectionRound_1870) createRound(ShareProtectionRound_1870.class,
                roundName);

        // Pass the required context to the new round, now including the seller
        spr.setProtectionContext(company, seller, sharesSold);

        setRound(spr);
        spr.start();

        net.sf.rails.common.ReportBuffer.add(spr,
                "=> INTERRUPT: " + seller.getName() + " sold " + sharesSold + " share(s) of " + company.getId()
                        + ". President " + company.getPresident().getName() + " may protect the price.");
    }

    @Override
    protected void setGuiParameters() {
        super.setGuiParameters();

        // Dynamically register the Cattle Company modifier for revenue calculation
        if (getRoot().getRevenueManager() != null) {
            getRoot().getRevenueManager()
                    .addDynamicModifier(new net.sf.rails.game.specific._1870.CattleModifier_1870());
            getRoot().getRevenueManager().addDynamicModifier(new net.sf.rails.game.specific._1870.GulfModifier_1870());
            log.info(">>> GulfModifier_1870 dynamically registered via GameManager.");
        }
    }

    @Override
    public void nextRound(net.sf.rails.game.Round round) {
        if (round instanceof net.sf.rails.game.specific._1870.ShareProtectionRound_1870) {
            net.sf.rails.game.Round interrupted = (net.sf.rails.game.Round) getInterruptedRound();
            if (interrupted != null) {
                setInterruptedRound(null);
                setRound(interrupted);
                interrupted.resume();
                return;
            }
        }
        super.nextRound(round);
    }
}