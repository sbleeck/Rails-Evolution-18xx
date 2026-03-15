package net.sf.rails.game.specific._1817;

import java.util.ArrayList;
import java.util.List;

import net.sf.rails.game.GameManager;
import net.sf.rails.game.PublicCompany;
import rails.game.action.PossibleAction;
import rails.game.action.StartCompany;
import net.sf.rails.game.financial.StockRound;
import net.sf.rails.game.specific._1817.action.Initiate1817IPO;
import net.sf.rails.game.specific._1817.action.Short1817;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.sf.rails.game.specific._1817.action.TakeLoans_1817;
import net.sf.rails.game.state.BooleanState;
import net.sf.rails.game.state.GenericState;
import net.sf.rails.game.state.IntegerState;
import net.sf.rails.game.specific._1817.action.LayNYHomeToken;
import net.sf.rails.game.MapHex;
import net.sf.rails.game.Stop;
// --- END FIX ---

/**
 * 1817 specific Stock Round logic.
 * Triggers certificate adjustment on start and handles the IPO auction
 * shortcut.
 */
public class StockRound_1817 extends StockRound {

    private static final Logger log = LoggerFactory.getLogger(StockRound_1817.class);

    protected final BooleanState waitingForE22Start;
    protected final GenericState<PublicCompany> pendingE22Company;
    protected final IntegerState pendingE22Bid;

    public StockRound_1817(GameManager parent, String id) {
        super(parent, id);
        waitingForE22Start = new BooleanState(this, "waitingForE22Start_" + id, false);
        pendingE22Company = new GenericState<>(this, "pendingE22Company_" + id);
        pendingE22Bid = IntegerState.create(this, "pendingE22Bid_" + id, 0);
    }

    @Override
    public void start() {
        super.start();
        // Initial certificate setup for all companies
        if (gameManager.getSRNumber() == 1) {
            for (PublicCompany comp : gameManager.getAllPublicCompanies()) {
                if (comp instanceof PublicCompany_1817) {
                    ((PublicCompany_1817) comp).adjustCertificates();
                }
            }
        }
    }

    @Override
    public void setBuyableCerts() {
        if (waitingForE22Start.value()) {
            possibleActions.clear();
            MapHex nyHex = getRoot().getMapManager().getHex("E22");
            PublicCompany comp = pendingE22Company.value();
            for (Stop stop : nyHex.getStops()) {
                if (stop.hasTokenSlotsLeft()) {
                    possibleActions.add(new LayNYHomeToken(nyHex, comp, stop));
                }
            }
            return;
        }
        super.setBuyableCerts();

        if (possibleActions == null)
            return;

        // 1817: Players can take loans for companies they lead during their Stock Round
        // turn
        net.sf.rails.game.Player currentPlayer = gameManager.getCurrentPlayer();
        if (currentPlayer != null) {
            for (PublicCompany comp : gameManager.getAllPublicCompanies()) {
                // If player is president and company has loan capacity
                if (comp.getPresident() == currentPlayer && !comp.isClosed()) {
                    // Capped by share count (2, 5, or 10)
                    int maxLoans = (comp instanceof PublicCompany_1817) ? ((PublicCompany_1817) comp).getShareCount()
                            : 0;
                    if (comp.getNumberOfBonds() < maxLoans) {
possibleActions.add(new TakeLoans_1817(getRoot(), comp.getId(), maxLoans));
                    }
                }

                // 1817: Players can short sell companies under strict conditions
                if (comp instanceof PublicCompany_1817 && !comp.isClosed()) {
                    PublicCompany_1817 comp1817 = (PublicCompany_1817) comp;

                    // 1. Must be a 5-share or 10-share company
                    boolean isLargeEnough = comp1817.getShareCount() > 2;

                    // 2. Player must own zero shares
                    boolean ownsZeroShares = currentPlayer.getPortfolioModel().getShare(comp) == 0;

                    // 3. Max 5 short shares in play
                    // We check if there are shares available in the pool to short
                    int poolShares = pool.getShare(comp);
                    boolean underShortLimit = poolShares > 0;

                    // 4. Prohibited in acquisition/liquidation zones
                    // Shares are only buyable in the 'white' or 'green' areas
                    boolean notInAcquisitionZone = comp.isBuyable();

                    // 5. Prohibited in Phase 8
                    String phaseId = gameManager.getCurrentPhase().getId();
                    boolean notPhase8 = phaseId != null && !phaseId.startsWith("8");

                    if (isLargeEnough && ownsZeroShares && underShortLimit && notInAcquisitionZone && notPhase8) {
                        possibleActions.add(new net.sf.rails.game.specific._1817.action.Short1817(gameManager.getRoot(),
                                comp.getId()));
                    }
                }

            }
        }

        List<PossibleAction> actionsToRemove = new ArrayList<>();
        List<PossibleAction> actionsToAdd = new ArrayList<>();

        for (PossibleAction action : possibleActions.getList()) {
            if (action instanceof StartCompany) {
                StartCompany startAction = (StartCompany) action;
                PublicCompany company = startAction.getCompany();

                actionsToRemove.add(action);

                boolean alreadyAdded = false;
                for (PossibleAction added : actionsToAdd) {
                    if (added instanceof Initiate1817IPO
                            && ((Initiate1817IPO) added).getCompanyName().equals(company.getId())) {
                        alreadyAdded = true;
                        break;
                    }
                }

                if (!alreadyAdded) {
                    actionsToAdd.add(new Initiate1817IPO(gameManager.getRoot(), company.getId()));
                }
            }
        }

        for (PossibleAction action : actionsToRemove) {
            possibleActions.remove(action);
        }
        for (PossibleAction action : actionsToAdd) {
            possibleActions.add(action);
        }
    }

    // ... (lines of unchanged context code) ...
    @Override
    protected boolean processGameSpecificAction(PossibleAction action) {
        if (action instanceof Initiate1817IPO) {
            try {
                Initiate1817IPO ipoAction = (Initiate1817IPO) action;
                PublicCompany_1817 comp = (PublicCompany_1817) ipoAction.getCompany();

                int bid = ipoAction.getBid();
                String hexId = ipoAction.getHexId();

                if ("E22".equals(hexId)) {
                    log.info("E22 IPO Initiated. Pausing Stock Round to prompt for North/South location.");
                    waitingForE22Start.set(true);
                    pendingE22Company.set(comp);
                    pendingE22Bid.set(bid);
                    return true;
                } else {
                    startAuctionRound(comp, hexId, 0, bid);
                    hasActed.set(true);
                    companyBoughtThisTurnWrapper.set(comp);
                    return true;
                }
            } catch (Exception e) {
                log.error("Failed to transition to 1817 Auction", e);
                return false;
            }
        }

        if (action instanceof LayNYHomeToken) {
            LayNYHomeToken layAction = (LayNYHomeToken) action;
            PublicCompany_1817 comp = (PublicCompany_1817) pendingE22Company.value();
            int bid = pendingE22Bid.value();
            int stationNumber = layAction.getChosenStation();

            log.info("E22 Location selected: Station {}", stationNumber);

            waitingForE22Start.set(false);
            startAuctionRound(comp, "E22", stationNumber, bid);
            hasActed.set(true);
            companyBoughtThisTurnWrapper.set(comp);
            return true;

        }
        if (action instanceof net.sf.rails.game.specific._1817.action.Short1817) {
            net.sf.rails.game.specific._1817.action.Short1817 sAction = (net.sf.rails.game.specific._1817.action.Short1817) action;
            // Use companyManager (inherited from Round) instead of gameManager for company
            // lookups
            PublicCompany comp = companyManager.getPublicCompany(sAction.getCompanyId());

            if (comp != null && comp.hasStockPrice()) {
                // 1. Calculate price and cash transfer
                int price = comp.getCurrentSpace().getPrice() / comp.getShareUnitsForSharePrice();

                // Use the static Currency state utility to transfer cash from the Bank to the
                // player
                net.sf.rails.game.state.Currency.fromBank(price, currentPlayer);

                // 2. Move certificate from Pool to sequestered portfolio
                // 1817 sequestering typically uses the Bank's Unavailable portfolio
                net.sf.rails.game.financial.PublicCertificate cert = pool.findCertificate(comp, 1, false);
                if (cert != null) {
                    cert.moveTo(getRoot().getBank().getUnavailable());
                }

                // 3. Adjust stock price (drop one space)
                stockMarket.sell(comp, currentPlayer, 1);

                // 4. Record action and report
                net.sf.rails.common.ReportBuffer.add(this, net.sf.rails.common.LocalText.getText("SHORT_SELL_LOG",
                        currentPlayer.getId(), comp.getId(), net.sf.rails.game.financial.Bank.format(this, price)));

                hasActed.set(true);
                return true;
            }
        }

        if (action instanceof net.sf.rails.game.specific._1817.action.TakeLoans_1817) {
            net.sf.rails.game.specific._1817.action.TakeLoans_1817 tlAction = (net.sf.rails.game.specific._1817.action.TakeLoans_1817) action;
            PublicCompany comp = companyManager.getPublicCompany(tlAction.getCompanyId());
            int count = tlAction.getLoansToTake();

            if (count > 0) {
                if (comp instanceof net.sf.rails.game.specific._1817.PublicCompany_1817) {
                    net.sf.rails.game.specific._1817.PublicCompany_1817 comp1817 = (net.sf.rails.game.specific._1817.PublicCompany_1817) comp;

                    // Update bond count
                    comp1817.setNumberOfBonds(comp1817.getNumberOfBonds() + count);

                    // Calculate and transfer cash ($100 per bond)
                    int loanAmount = count * 100;
                    net.sf.rails.game.financial.Bank bank = (net.sf.rails.game.financial.Bank) gameManager.getRoot()
                            .getBank();
                    comp1817.addCashFromBank(loanAmount, bank);
                }
                // Mark that the player has taken an action
                hasActed.set(true);
                return true;
            }
        }

        return super.processGameSpecificAction(action);
    }

    /**
     * Exempts 1817 2-share companies from the standard 60% global hold limit.
     * Prevents the engine from deadlocking when a player holds 100% of a new
     * company.
     */
    @Override
    public boolean checkAgainstHoldLimit(net.sf.rails.game.Player player, net.sf.rails.game.PublicCompany company,
            int number) {
        if (company instanceof PublicCompany_1817) {
            if (((PublicCompany_1817) company).getShareCount() == 2) {
                return true;
            }
        }
        return super.checkAgainstHoldLimit(player, company, number);
    }

    // ... (lines of unchanged context code) ...
    private void setupCompanyActions(PublicCompany company) {
        log.info("M&A ROUND: setupCompanyActions() invoked for " + company.getId());

    }

    private void startAuctionRound(PublicCompany_1817 comp, String hexId, int stationNumber, int bid) {
        net.sf.rails.game.Player initiator = gameManager.getCurrentPlayer();
        log.info("IPO INITIATED: Company " + comp.getId() + " by Player " + initiator.getName() +
                " at Hex " + hexId + " Station " + stationNumber + " with starting bid $" + bid);

        gameManager.setInterruptedRound(this);
        AuctionRound_1817 auctionRound = gameManager.createRound(AuctionRound_1817.class, "Auction_" + comp.getId());
        auctionRound.setupAuction(comp, hexId, stationNumber, bid, initiator, gameManager.getPlayers());
    }

}