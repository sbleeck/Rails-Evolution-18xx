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


    }

    public BondsModel getBondsModel() {
if (bondsModel == null) {
            net.sf.rails.game.financial.Bank bank = getRoot().getBank();
            // Explicitly call the 1817 constructor to ensure tiered interest logic is available
            bondsModel = new BondsModel_1817(bank, bank, getRoot());
        }
        return bondsModel;
    }


  
    protected final net.sf.rails.game.state.BooleanState endgameTriggered = new net.sf.rails.game.state.BooleanState(this, "endgameTriggered", false);
    protected final net.sf.rails.game.state.IntegerState remainingOperatingRounds = net.sf.rails.game.state.IntegerState.create(this, "remainingOperatingRounds", -1);
    protected final net.sf.rails.game.state.BooleanState finalSetHasThreeORs = new net.sf.rails.game.state.BooleanState(this, "finalSetHasThreeORs", false);

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

            boolean newlyTriggered = !endgameTriggered.value() && getRoot().getPhaseManager().hasReachedPhase("8");
            if (newlyTriggered) {
                endgameTriggered.set(true);
                remainingOperatingRounds.set(3);
                if (relativeORNumber.value() == 2) {
                    finalSetHasThreeORs.set(true);
                } else {
                    finalSetHasThreeORs.set(false);
                }
                net.sf.rails.common.ReportBuffer.add(this, "The first 8-train has been purchased or exported! The game will end after exactly 3 more Operating Rounds.");
            } else if (endgameTriggered.value()) {
                remainingOperatingRounds.set(remainingOperatingRounds.value() - 1);
            }

            startMergerAndAcquisitionRound();

        } else if (round instanceof MergerAndAcquisitionRound_1817) {
            // Sequence: M&A 1 -> OR 2 OR M&A 2 -> SR
            capturePlayerWorthSnapshot(round.getId());
            captureCompanyPayoutSnapshot(round.getId());

            if (endgameTriggered.value() && remainingOperatingRounds.value() == 0) {
                net.sf.rails.common.ReportBuffer.add(this, "The final Operating Round and M&A phase have concluded. Game Over.");
                finishGame();
                return;
            }

            boolean goToSR = false;
            
            if (endgameTriggered.value()) {
                if (finalSetHasThreeORs.value()) {
                    if (remainingOperatingRounds.value() == 3) {
                        goToSR = true;
                    }
                } else {
                    if (remainingOperatingRounds.value() == 2) {
                        goToSR = true;
                    }
                }
            } else {
                if (relativeORNumber.value() >= 2) {
                    goToSR = true;
                }
            }

            if (goToSR) {
                if (gameOverPending.value() && gameEndWhen == GameEnd.AFTER_SET_OF_ORS) {
                    finishGame();
                } else {
                    startStockRound();
                }
            } else {
                startOperatingRound(true);
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


    /**
     * Calculates the true net worth for 1817: Cash + Long Stock - Short Stock.
     * Company assets and private companies count for nothing[cite: 898].
     */
    public int get1817PlayerWorth(net.sf.rails.game.Player p) {
        int worth = p.getCash();
        for (net.sf.rails.game.PublicCompany comp : getAllPublicCompanies()) {
            int price = (comp.getCurrentSpace() != null) ? comp.getCurrentSpace().getPrice() : 0;
            for (net.sf.rails.game.financial.PublicCertificate cert : p.getPortfolioModel().getCertificates(comp)) {
                if (cert instanceof net.sf.rails.game.specific._1817.ShortCertificate) {
                    worth -= price; 
                } else {
                    worth += (cert.getShare() * price) / 10;
                }
            }
        }
        return worth;
    }

    @Override
    public java.util.List<String> getGameReport() {
        java.util.List<String> b = new java.util.ArrayList<>();
        java.util.List<net.sf.rails.game.Player> rankedPlayers = new java.util.ArrayList<>(getRoot().getPlayerManager().getPlayers());

        // Sort Descending: Winner (Highest 1817 Worth) First
        rankedPlayers.sort((p1, p2) -> {
            int result = Integer.compare(get1817PlayerWorth(p2), get1817PlayerWorth(p1));
            if (result == 0) return p1.getId().compareTo(p2.getId());
            return result;
        });

        net.sf.rails.game.Player winner = rankedPlayers.get(0);
        double winnerWorth = get1817PlayerWorth(winner);

        b.add(net.sf.rails.common.LocalText.getText("EoGWinner") + " " + winner.getId() + "!");
        b.add("Final Ranking (1817 Net Worth: Cash + Longs - Shorts):");

        int rank = 1;
        for (net.sf.rails.game.Player p : rankedPlayers) {
            double worth = get1817PlayerWorth(p);
            double percent = (winnerWorth > 0) ? (worth / winnerWorth) * 100.0 : 0.0;
            String line = String.format("%d. %s (%s) - %.1f%%", rank, p.getId(), net.sf.rails.game.financial.Bank.format(this, (int) worth), percent);
            b.add(line);
            rank++;
        }
        return b;
    }

    @Override
    protected void capturePlayerWorthSnapshot(String roundId) {
        java.util.LinkedHashMap<String, java.util.Map<String, Double>> history = playerWorthHistory.value();
        if (history == null) history = new java.util.LinkedHashMap<>();

        java.util.Map<String, Double> snapshot = new java.util.HashMap<>();
        for (net.sf.rails.game.Player player : getRoot().getPlayerManager().getPlayers()) {
            snapshot.put(player.getId(), (double) get1817PlayerWorth(player));
        }

        if (!history.containsKey(roundId)) {
            history.put(roundId, snapshot);
            playerWorthHistory.set(history);
        }
        
        java.util.LinkedHashMap<String, java.util.Map<String, PlayerAssetSnapshot>> assetHistory = playerAssetHistory.value();
        if (assetHistory == null) assetHistory = new java.util.LinkedHashMap<>();
        
        java.util.Map<String, PlayerAssetSnapshot> roundAssets = new java.util.HashMap<>();
        for (net.sf.rails.game.Player p : getRoot().getPlayerManager().getPlayers()) {
            PlayerAssetSnapshot asset = new PlayerAssetSnapshot();
            asset.cash = p.getCash();

            for (net.sf.rails.game.PublicCompany comp : getAllPublicCompanies()) {
                int longShares = 0;
                int shortShares = 0;
                for (net.sf.rails.game.financial.PublicCertificate cert : p.getPortfolioModel().getCertificates(comp)) {
                    if (cert instanceof net.sf.rails.game.specific._1817.ShortCertificate) {
                        shortShares += 1;
                    } else {
                        longShares += cert.getShare() / comp.getShareUnit();
                    }
                }
                
                int netShares = longShares - shortShares;
                if (netShares != 0 || longShares > 0 || shortShares > 0) {
                    asset.sharePercents.put(comp.getId(), netShares * comp.getShareUnit());
                    int price = (comp.getCurrentSpace() != null) ? comp.getCurrentSpace().getPrice() : 0;
                    asset.holdingValues.put(comp.getId(), (netShares * comp.getShareUnit() * price) / 10);
                }
            }
            roundAssets.put(p.getName(), asset);
        }
        assetHistory.put(roundId, roundAssets);
        playerAssetHistory.set(assetHistory);
    }
    
}