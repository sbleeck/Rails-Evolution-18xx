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
            exportTrain();
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
    
    /**
     * Executes Rule 6.11: Exporting the next available train at the end of the OR.
     */
 
protected void exportTrain() {
        net.sf.rails.game.TrainManager tm = getRoot().getTrainManager();
        net.sf.rails.game.financial.BankPortfolio ipo = net.sf.rails.game.financial.Bank.getIpo(tm);
        net.sf.rails.game.financial.BankPortfolio scrap = getRoot().getBank().getScrapHeap();

        java.util.Set<net.sf.rails.game.Train> available = tm.getAvailableNewTrains();
        if (available == null || available.isEmpty()) {
            return;
        }

        // 1. Identify what is at the top of the stack
        net.sf.rails.game.Train trainToExport = available.iterator().next();
        net.sf.rails.game.TrainCardType type = trainToExport.getCardType();

        net.sf.rails.common.ReportBuffer.add(this, "The Bank exports a " + type.getId() + " train.");

        // 2. Handle special 2-train removal without rusting owned trains
        if ("2".equals(type.getId())) {
            java.util.List<net.sf.rails.game.TrainCard> cardsToTrash = new java.util.ArrayList<>();
            for (net.sf.rails.game.RailsItem item : ipo.getPortfolioModel().getTrainsModel().getPortfolio().items()) {
                if (item instanceof net.sf.rails.game.TrainCard) {
                    net.sf.rails.game.TrainCard card = (net.sf.rails.game.TrainCard) item;
                    if (card.getType().equals(type)) {
                        cardsToTrash.add(card);
                    }
                }
            }
            
            for (net.sf.rails.game.TrainCard card : cardsToTrash) {
                card.moveTo(scrap);
            }
        } else {
            // Standard export
            trainToExport.getCard().moveTo(scrap);
        }

        // 3. Update engine state so PhaseManager detects the change
        type.addToBoughtFromIPO();
        
        // Because the 2-trains are now gone from the IPO, this check automatically releases the 2+ trains
        tm.checkTrainAvailability(trainToExport, ipo);
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