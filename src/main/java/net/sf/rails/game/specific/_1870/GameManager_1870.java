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
     * @param company The company whose shares were just sold.
     * @param sharesSold The number of shares that were sold.
     */
    public void startShareProtectionRound(Round currentRound, PublicCompany company, int sharesSold) {
        
        // Suspend the current stock round
        setInterruptedRound(currentRound);

        String roundName = "ShareProtectionRound_in_" + currentRound.getId() + "_" + System.nanoTime();

        // Create the special interrupt round (Requires ShareProtectionRound_1870.java to be implemented)
        ShareProtectionRound_1870 spr = (ShareProtectionRound_1870) createRound(ShareProtectionRound_1870.class, roundName);
        
        // Pass the required context to the new round
        spr.setProtectionContext(company, sharesSold);
        
        setRound(spr);
        spr.start();

        // Force UI to acknowledge the round switch
        ReportBuffer.add(spr, company.getId() + " President may protect the share price.");
    }


    
    @Override
    public void nextRound(Round round) {
        // Check if the round that just finished was our custom Share Protection round
        boolean isSPR = (round != null && round.getClass().getSimpleName().equals("ShareProtectionRound_1870"));

        if (isSPR) {
            // The price protection interrupt has finished.
            // Resume the interrupted stock round exactly where it left off.
            Round interrupted = (Round) getInterruptedRound();
            if (interrupted != null) {
                setInterruptedRound(null);
                super.nextRound(interrupted);
            } else {
                super.nextRound(round);
            }
        } else {
            // Normal round transition (OR -> SR, etc.)
            super.nextRound(round);
        }
    }
}