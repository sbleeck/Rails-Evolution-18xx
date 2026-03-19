package net.sf.rails.game.specific._1817;

import net.sf.rails.game.GameManager;
import net.sf.rails.game.OperatingRound;
import net.sf.rails.game.financial.StockRound;
import net.sf.rails.game.model.BondsModel;
import net.sf.rails.game.Round;
import net.sf.rails.game.model.BondsModel;
import net.sf.rails.game.round.RoundFacade;
import net.sf.rails.ui.swing.ORPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameManager_1817 extends GameManager {

    protected BondsModel bondsModel;
    // Placeholder for the upcoming M&A class
    protected String mergerAndAcquisitionRoundClassName = "net.sf.rails.game.specific._1817.MergerAndAcquisitionRound_1817";

    protected static final Logger log = LoggerFactory.getLogger(ORPanel.class);

    public GameManager_1817(net.sf.rails.game.RailsRoot parent, String id) {
        super(parent, id);

        log.info("1817_TRACE: BondsModel initialized in GameManager.");

    }

    public BondsModel getBondsModel() {
if (bondsModel == null) {
            net.sf.rails.game.financial.Bank bank = getRoot().getBank();
            // Explicitly call the 1817 constructor to ensure tiered interest logic is available
            bondsModel = new BondsModel_1817(bank, bank, getRoot());
            log.info("1817_TRACE: BondsModel_1817 (Subclass) lazily initialized for the Bank.");
        }
        return bondsModel;
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
            // Sequence: OR 1 -> M&A 1 OR OR 2 -> M&A 2
            capturePlayerWorthSnapshot(round.getId());
            captureCompanyPayoutSnapshot(round.getId());
            exportTrain();
            startMergerAndAcquisitionRound();

        } else if (round instanceof MergerAndAcquisitionRound_1817) {
            // Sequence: M&A 1 -> OR 2 OR M&A 2 -> SR
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

    @Override
    public void startOperatingRound(boolean increment) {
        super.startOperatingRound(increment);
        payMailContracts();
    }

    /**
     * Regel 6.0: Mail contracts werden zu Beginn der OR an Gesellschaften gezahlt,
     * die einen Mail Contract und mindestens einen Zug besitzen.
     */
private void payMailContracts() {
        for (net.sf.rails.game.PublicCompany comp : getRoot().getCompanyManager().getAllPublicCompanies()) {
            if (comp instanceof PublicCompany_1817) {
                PublicCompany_1817 comp1817 = (PublicCompany_1817) comp;

                if (comp1817.getPortfolioModel().getNumberOfTrains() > 0) {
                    for (net.sf.rails.game.PrivateCompany priv : comp1817.getPortfolioModel().getPrivateCompanies()) {
                        int bonus = 0;
                        if ("MNM60".equals(priv.getId())) bonus = 10;
                        else if ("MLC90".equals(priv.getId())) bonus = 15;
                        else if ("MJM12".equals(priv.getId())) bonus = 20;

                        if (bonus > 0) {
                            net.sf.rails.common.ReportBuffer.add(this, comp1817.getId() + " receives $" + bonus + " from " + priv.getLongName());
                            comp1817.addCashFromBank(bonus, getRoot().getBank());
                        }
                    }
                }
            }
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

        // Because the 2-trains are now gone from the IPO, this check automatically
        // releases the 2+ trains
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

    @Override
    public java.util.List<net.sf.rails.game.PublicCompany> getCompaniesInRunningOrder() {
        java.util.List<net.sf.rails.game.PublicCompany> runningOrder = new java.util.ArrayList<>(super.getCompaniesInRunningOrder());
        
        // 1817 Rule 6.2: "If the stock values of two or more companies are equal, 
        // then the one whose stock marker was placed in that space first operates first."
        runningOrder.sort((c1, c2) -> {
            net.sf.rails.game.financial.StockSpace s1 = c1.getCurrentSpace();
            net.sf.rails.game.financial.StockSpace s2 = c2.getCurrentSpace();
            
            if (s1 != null && s2 != null && s1.equals(s2)) {
                java.util.List<?> stack = s1.getTokens();
                int idx1 = stack.indexOf(c1);
                int idx2 = stack.indexOf(c2);
                return Integer.compare(idx1, idx2);
            }
            return 0; // Maintain superclass order (price descending) for all other comparisons
        });
        
        return runningOrder;
    }
    
}