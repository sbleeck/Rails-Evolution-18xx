package net.sf.rails.game.specific._1817;

import net.sf.rails.game.GameManager;
import net.sf.rails.game.OperatingRound;
import net.sf.rails.game.financial.StockRound;
import net.sf.rails.game.Round;
import net.sf.rails.game.round.RoundFacade;

public class GameManager_1817 extends GameManager {

    // Placeholder for the upcoming M&A class
    protected String mergerAndAcquisitionRoundClassName = "net.sf.rails.game.specific._1817.MergerAndAcquisitionRound_1817";

    public GameManager_1817(net.sf.rails.game.RailsRoot parent, String id) {
        super(parent, id);
    }


@Override
    public void nextRound(Round round) {
        
        // 1. ALWAYS resolve interrupted rounds first. 
        RoundFacade roundToResume = getInterruptedRound();
        if (roundToResume != null) {
            setInterruptedRound(null);
            setRound(roundToResume);
            roundToResume.resume();
            return;
        }

        // 2. Isolate the Start Round Phase
        if (stockRoundNumber.value() == 0) {
            super.nextRound(round);
            return;
        }

        // 3. 1817 Static 5-Round Sequence
        if (round instanceof StockRound) {
            // Sequence: SR -> OR 1
            capturePlayerWorthSnapshot(round.getId());
            captureCompanyPayoutSnapshot(round.getId());
            relativeORNumber.set(0); 
            startOperatingRound(true);

        } else if (round instanceof OperatingRound) {
            // Sequence: OR 1 -> M&A 1  OR  OR 2 -> M&A 2
            capturePlayerWorthSnapshot(round.getId());
            captureCompanyPayoutSnapshot(round.getId());
            startMergerAndAcquisitionRound();

        } else if (round instanceof MergerAndAcquisitionRound_1817) {
            // Sequence: M&A 1 -> OR 2  OR  M&A 2 -> SR
            capturePlayerWorthSnapshot(round.getId());
            captureCompanyPayoutSnapshot(round.getId());

            if (relativeORNumber.value() == 1) {
                startOperatingRound(true); 
            } else {
                if (gameOverPending.value() && gameEndWhen == GameEnd.AFTER_SET_OF_ORS) {
                    finishGame();
                } else {
                    startStockRound();
                }
            }
        } else {
            super.nextRound(round);
        }
    }
    
protected void startMergerAndAcquisitionRound() {
        clearStatusMessage();
        // Changed ID prefix to "MR" to help the UI round tracker identify the phase
        String id = "MR_" + stockRoundNumber.value() + "." + relativeORNumber.value();
        RoundFacade maRound = createRound(mergerAndAcquisitionRoundClassName, id);
        setRound(maRound);
        currentSRorOR.set((Round) maRound); 
        ((MergerAndAcquisitionRound_1817) maRound).start();
    }


    @Override
    protected void setGuiParameters() {
        super.setGuiParameters();
guiParameters.put(net.sf.rails.common.GuiDef.Parm.CAN_ANY_COMPANY_HOLD_OWN_SHARES, true);
    }



}