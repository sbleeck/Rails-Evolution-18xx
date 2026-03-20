package net.sf.rails.game.specific._1817;

import java.util.ArrayList;
import java.util.List;

import net.sf.rails.game.Company;
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

    // Track companies shorted by players during the current stock round turn.
    // In Rails, we map Player -> List of shorted company IDs.
  protected final net.sf.rails.game.state.HashMapState<String, String> shortedThisRound;
  
    public StockRound_1817(GameManager parent, String id) {
        super(parent, id);
        waitingForE22Start = new BooleanState(this, "waitingForE22Start_" + id, false);
        pendingE22Company = new GenericState<>(this, "pendingE22Company_" + id);
        pendingE22Bid = IntegerState.create(this, "pendingE22Bid_" + id, 0);
        shortedThisRound = net.sf.rails.game.state.HashMapState.create(this, "shortedThisRound_" + id);
    }

    @Override
    public void start() {
        super.start();
        shortedThisRound.clear();
        // Initial certificate setup for all companies
        if (gameManager.getSRNumber() == 1) {
            for (PublicCompany comp : gameManager.getAllPublicCompanies()) {
                if (comp instanceof PublicCompany_1817) {
                    ((PublicCompany_1817) comp).adjustCertificates();
                }
            }
        }

        // 1817 Rule 7.4: Bank Market Clearing [cite: 886, 887, 888]
        // At the start of every stock round, the bank closes any short positions
        // in the open market by purchasing stock from the company treasury.
        net.sf.rails.game.model.PortfolioModel openMarket = pool; 
        net.sf.rails.game.financial.BankPortfolio unavailableBank = getRoot().getBank().getUnavailable();
        
        for (PublicCompany comp : gameManager.getAllPublicCompanies()) {
            if (!(comp instanceof PublicCompany_1817)) continue;
            
            boolean keepClearing = true;
            while (keepClearing) {
                net.sf.rails.game.financial.PublicCertificate shortCert = null;
                for (net.sf.rails.game.financial.PublicCertificate c : openMarket.getCertificates()) {
                    if (c instanceof net.sf.rails.game.specific._1817.ShortCertificate && c.getCompany() == comp) {
                        shortCert = c;
                        break;
                    }
                }
                
                if (shortCert != null) {
                    // Search treasury for a non-president share to close the short
                    net.sf.rails.game.financial.PublicCertificate treasuryShare = comp.getPortfolioModel().findCertificate(comp, false);
                    if (treasuryShare != null) {
                        shortCert.moveTo(unavailableBank);
                        treasuryShare.moveTo(unavailableBank);
                        log.info("Bank auto-closed orphaned short share for " + comp.getId() + " using treasury stock.");
                    } else {
                        keepClearing = false;
                    }
                } else {
                    keepClearing = false;
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
                        possibleActions.add(new TakeLoans_1817(getRoot(), comp.getId()));
                    }
                }

                // 1817: Players can short sell companies under strict conditions
                if (comp instanceof PublicCompany_1817 && !comp.isClosed()) {
                    PublicCompany_1817 comp1817 = (PublicCompany_1817) comp;

                    // 1. Must be a 5-share or 10-share company
                    boolean isLargeEnough = comp1817.getShareCount() > 2;

                    // 2. Player must own zero REGULAR shares (Short shares don't block shorting)
                    int regularSharesOwned = 0;
                    for (net.sf.rails.game.financial.PublicCertificate c : currentPlayer.getPortfolioModel().getCertificates()) {
                        if (!(c instanceof net.sf.rails.game.specific._1817.ShortCertificate) && c.getCompany() == comp) {
                            regularSharesOwned++;
                        }
                    }
                    boolean ownsZeroShares = (regularSharesOwned == 0);

                    // 3. Max 5 short shares in play (Rule 5.3)
                    int activeShorts = 0;
                    int availableShorts = 0;

                    // Count Available Shorts (Iterate through the Bank's Unavailable Portfolio)
                    // This is exactly where you moved them in
                    // PublicCompany_1817.finishConfiguration()
                    net.sf.rails.game.model.PortfolioModel unavailablePortfolio = getRoot().getBank().getUnavailable()
                            .getPortfolioModel();
                    for (net.sf.rails.game.financial.PublicCertificate c : unavailablePortfolio.getCertificates()) {
                        if (c instanceof net.sf.rails.game.specific._1817.ShortCertificate
                                && c.getCompany() == comp1817) {
                            availableShorts++;
                        }
                    }

                    // Count Active Shorts (Iterate through all players to find issued short certs)
                    for (net.sf.rails.game.Player p : getRoot().getPlayerManager().getPlayers()) {
                        for (net.sf.rails.game.financial.PublicCertificate c : p.getPortfolioModel()
                                .getCertificates()) {
                            if (c instanceof net.sf.rails.game.specific._1817.ShortCertificate
                                    && c.getCompany() == comp1817) {
                                activeShorts++;
                            }
                        }
                    }

                    // Check pool availability (Iteration approach for robustness)
                    boolean hasPoolShare = false;
                    for (net.sf.rails.game.financial.PublicCertificate c : pool.getCertificates()) {
                        if (c.getCompany() == comp) {
                            hasPoolShare = true;
                            break;
                        }
                    }

                    boolean underShortLimit = (activeShorts < 5 && availableShorts > 0);

                    // 4. Prohibited in acquisition/liquidation zones
                    boolean notInAcquisitionZone = comp.isBuyable();

                    // 5. Prohibited in Phase 8 (Safely handle null phase IDs)
                    String phaseId = (gameManager.getCurrentPhase() != null) ? gameManager.getCurrentPhase().getId()
                            : "";
                    boolean notPhase8 = (phaseId == null || !phaseId.startsWith("8"));

                    if (isLargeEnough && ownsZeroShares && underShortLimit && hasPoolShare && notInAcquisitionZone
                            && notPhase8) {

                        possibleActions.add(new net.sf.rails.game.specific._1817.action.Short1817(gameManager.getRoot(),
                                comp.getId()));
                    }
                }

            }
        }

        // Remove default StartCompany actions generated by parent class (which rely strictly on raw cash)
        List<PossibleAction> actionsToRemove = new ArrayList<>();
        for (PossibleAction action : possibleActions.getList()) {
            if (action instanceof StartCompany) {
                actionsToRemove.add(action);
            }
        }
        for (PossibleAction action : actionsToRemove) {
            possibleActions.remove(action);
        }

        // 1817: IPOs are based on Purchasing Power (Cash + Private Base Prices), not just Cash.
        // Minimum bid for a 2-share at $50 is $100.
        if (currentPlayer != null) {
            int purchasingPower = currentPlayer.getCashValue();
            for (net.sf.rails.game.PrivateCompany pc : currentPlayer.getPortfolioModel().getPrivateCompanies()) {
                purchasingPower += pc.getBasePrice();
            }

            if (purchasingPower >= 100) {
                for (PublicCompany comp : gameManager.getAllPublicCompanies()) {
                    if (!comp.hasFloated() && !comp.isClosed()) {
                        boolean alreadyAdded = false;
                        for (PossibleAction action : possibleActions.getList()) {
                            if (action instanceof Initiate1817IPO && ((Initiate1817IPO) action).getCompanyName().equals(comp.getId())) {
                                alreadyAdded = true;
                                break;
                            }
                        }
                        if (!alreadyAdded) {
                            possibleActions.add(new Initiate1817IPO(gameManager.getRoot(), comp.getId()));
                        }
                    }
                }
            }
        }

        
        // Rule 5.3: Filter out BuyCertificate actions for companies shorted this round by the current player
        if (currentPlayer != null) {
            List<PossibleAction> buyActionsToRemove = new ArrayList<>();
            for (PossibleAction action : possibleActions.getList()) {
                if (action instanceof rails.game.action.BuyCertificate) {
                    rails.game.action.BuyCertificate buyAction = (rails.game.action.BuyCertificate) action;
                    String key = currentPlayer.getId() + "_" + buyAction.getCompany().getId();
                    if (shortedThisRound.containsKey(key)) {
                        buyActionsToRemove.add(action);
                    }
                }
            }
            for (PossibleAction action : buyActionsToRemove) {
                possibleActions.remove(action);
            }
        }
// 1817 Rule 5.6: Allow companies to buy their own stock from the open market
        // provided they are not in the red (liquidation) or gray (acquisition) areas.
        if (currentPlayer != null) {
            for (PublicCompany comp : gameManager.getAllPublicCompanies()) {
                if (comp.getPresident() == currentPlayer && !comp.isClosed() && comp.isBuyable()) {
                    int price = comp.getMarketPrice();
                    // Check if company has enough cash and if a share exists in the pool
                    if (comp.getCash() >= price) {
                        net.sf.rails.game.financial.PublicCertificate poolCert = pool.findCertificate(comp, 1, false);
                        if (poolCert != null) {
                            possibleActions.add(new net.sf.rails.game.specific._1817.action.CompanyBuyOpenMarketShare_1817(gameManager.getRoot(), comp.getId(), price));
                        }
                    }
                }
            }
        }

    }

@Override
public boolean mayPlayerSellShareOfCompany(net.sf.rails.game.PublicCompany company) {
if (!super.mayPlayerSellShareOfCompany(company)) {
return false;
}

    if (company instanceof PublicCompany_1817) {
        // Rule 5.2: Shares of a 2-share company may not be sold.
        if (((PublicCompany_1817) company).getShareCount() == 2) {
            return false;
        }
    }

    // Rule 5.3: Short certificates are liabilities, not sellable assets.
    // Due to Mandatory Reconciliation, if a player holds a short, they hold NO regular shares.
    net.sf.rails.game.Player currentPlayer = gameManager.getCurrentPlayer();
    if (currentPlayer != null) {
        for (net.sf.rails.game.financial.PublicCertificate c : currentPlayer.getPortfolioModel().getCertificates(company)) {
            if (c instanceof net.sf.rails.game.specific._1817.ShortCertificate) {
                return false;
            }
        }
    }

    return true;
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
            PublicCompany comp = companyManager.getPublicCompany(sAction.getCompanyId());

            if (comp != null && comp.hasStockPrice()) {
                // 1. Calculate price (The market price is the per-share value in 1817)
                int price = comp.getCurrentSpace().getPrice();

                // 1. Rule 5.3: Check if this is the "First Short" for this company
                // If no short certificates are in the Unavailable pile, they might already be
                // in Open Short Interest
                // We check if we need to sequester the 5 regular shares.
                int regularSharesInOSI = 0;
                for (net.sf.rails.game.financial.PublicCertificate c : getRoot().getBank().getUnavailable()
                        .getPortfolioModel().getCertificates()) {
                    if (!(c instanceof ShortCertificate) && c.getCompany() == comp) {
                        regularSharesInOSI++;
                    }
                }

                if (regularSharesInOSI == 0) {
                    // Move 5 regular shares from the Pool to Unavailable (OSI)
                    for (int i = 0; i < 5; i++) {
                        net.sf.rails.game.financial.PublicCertificate regCert = pool.findCertificate(comp, 1, false);
                        if (regCert != null) {
                            regCert.moveTo(getRoot().getBank().getUnavailable());
                        }
                    }
                }

                // 2. Find the ShortCertificate to give to the player
                net.sf.rails.game.financial.PublicCertificate shortCert = null;
                for (net.sf.rails.game.financial.PublicCertificate c : getRoot().getBank().getUnavailable()
                        .getPortfolioModel().getCertificates()) {
                    if (c instanceof net.sf.rails.game.specific._1817.ShortCertificate && c.getCompany() == comp) {
                        shortCert = c;
                        break;
                    }
                }

                if (shortCert != null) {
                    // 3. Complete the sale: Player gets the Liability (Short Cert) and the Cash
                    shortCert.moveTo(currentPlayer.getPortfolioModel());
                    net.sf.rails.game.state.Currency.fromBank(price, currentPlayer);

                    // Rule 5.3: Short selling is considered a sale and prevents the player
                    // from purchasing this stock for the remainder of the stock round.
                    shortedThisRound.put(currentPlayer.getId() + "_" + comp.getId(), "shorted");

                    // Update Engine Wrapper to register the active company for the UI/Game Loop
                    companyBoughtThisTurnWrapper.set(comp);

                    // 5. Record the action and report
                    String logMsg = net.sf.rails.common.LocalText.getText("SHORT_SELL_LOG",
                            currentPlayer.getId(), comp.getId(), net.sf.rails.game.financial.Bank.format(this, price));
                    if (logMsg.startsWith("Missing text")) {
                        logMsg = currentPlayer.getId() + " sells short " + comp.getId() + " for " + net.sf.rails.game.financial.Bank.format(this, price);
                    }
                    net.sf.rails.common.ReportBuffer.add(this, logMsg);

                    hasActed.set(true);
                    return true;
                }
            }
        }

        if (action instanceof net.sf.rails.game.specific._1817.action.TakeLoans_1817) {
            net.sf.rails.game.specific._1817.action.TakeLoans_1817 tlAction = (net.sf.rails.game.specific._1817.action.TakeLoans_1817) action;
            PublicCompany comp = companyManager.getPublicCompany(tlAction.getCompanyId());
            if (comp instanceof net.sf.rails.game.specific._1817.PublicCompany_1817) {
                net.sf.rails.game.specific._1817.PublicCompany_1817 comp1817 = (net.sf.rails.game.specific._1817.PublicCompany_1817) comp;

                // 1. Execute the centralized loan logic (adds bond, adds cash, moves stock
                // left)
                comp1817.executeLoan();

                // 2. Register the active company so the player can subsequently buy treasury
                // shares
                companyBoughtThisTurnWrapper.set(comp);

                // 3. Mark that the player has acted
                hasActed.set(true);
                return true;

            }
        }
        if (action instanceof net.sf.rails.game.specific._1817.action.CompanyBuyOpenMarketShare_1817) {
            net.sf.rails.game.specific._1817.action.CompanyBuyOpenMarketShare_1817 cbAction = (net.sf.rails.game.specific._1817.action.CompanyBuyOpenMarketShare_1817) action;
            PublicCompany comp = companyManager.getPublicCompany(cbAction.getCompanyId());
            
            if (comp != null && comp.isBuyable()) {
                int price = comp.getMarketPrice();
                net.sf.rails.game.financial.PublicCertificate poolCert = pool.findCertificate(comp, 1, false);
                
                if (poolCert != null && comp.getCash() >= price) {
                    // Move the certificate to the company treasury
                    poolCert.moveTo(comp.getPortfolioModel());
                    
                    // 1817 Rule 5.6: The company pays the bank for the stock purchase[cite: 1783].
                    // Use static Currency utility to move cash from the owner to the bank.
                    net.sf.rails.game.state.Currency.toBank(comp, price);
                    
                    net.sf.rails.common.ReportBuffer.add(this, comp.getId() + " buys one share from the open market for " + net.sf.rails.game.financial.Bank.format(this, price));
                    
                    hasActed.set(true);
                    companyBoughtThisTurnWrapper.set(comp);
                    return true;
                }
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
// Exempt 1817 2-share companies
            if (((PublicCompany_1817) company).getShareCount() == 2) {
                return true;
            }
            
            // Rule 5.1: A player may close a short position by purchasing a matching share
            // even if he is at or above the certificate limit.
            for (net.sf.rails.game.financial.PublicCertificate c : player.getPortfolioModel().getCertificates()) {
                if (c instanceof net.sf.rails.game.specific._1817.ShortCertificate && c.getCompany() == company) {
                    return true;
                }
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



    @Override
    public boolean process(rails.game.action.PossibleAction action) {
        boolean result = super.process(action);
        
        // Rule 5.1 Mandatory Reconciliation: Check after any successful action that 
        // could create a conflicting long/short state[cite: 451, 452].
        if (result && gameManager.getCurrentPlayer() != null) {
            net.sf.rails.game.PublicCompany comp = null;
            if (action instanceof rails.game.action.BuyCertificate) {
                comp = ((rails.game.action.BuyCertificate) action).getCompany();
            } else if (action instanceof net.sf.rails.game.specific._1817.action.Short1817) {
                comp = companyManager.getPublicCompany(((net.sf.rails.game.specific._1817.action.Short1817) action).getCompanyId());
            }
            
            if (comp != null) {
                reconcileShorts(gameManager.getCurrentPlayer(), comp);
            }
        }
        return result;
    }

    private void reconcileShorts(net.sf.rails.game.Player player, net.sf.rails.game.PublicCompany comp) {
        if (player == null || comp == null) return;
        
        // Use a loop because a player could theoretically acquire multiple pairs (e.g., via merger)[cite: 452, 454].
        boolean foundPair = true;
        while (foundPair) {
            net.sf.rails.game.financial.PublicCertificate shortCert = null;
            net.sf.rails.game.financial.PublicCertificate regularCert = null;
            
            for (net.sf.rails.game.financial.PublicCertificate c : player.getPortfolioModel().getCertificates()) {
                if (c.getCompany() == comp) {
                    if (c instanceof net.sf.rails.game.specific._1817.ShortCertificate) {
                        shortCert = c;
                    } else if (!c.isPresidentShare()) { 
                        // Only common shares are returned to the Open Short Interest section.
                        regularCert = c;
                    }
                }
            }
            
            if (shortCert != null && regularCert != null) {
                net.sf.rails.game.financial.BankPortfolio unavailableBank = getRoot().getBank().getUnavailable();
                shortCert.moveTo(unavailableBank);
                regularCert.moveTo(unavailableBank);
                log.info("Mandatory Reconciliation: " + player.getName() + " short position closed for " + comp.getId());
                net.sf.rails.common.ReportBuffer.add(this, player.getName() + " automatically closes a short position in " + comp.getId());
            } else {
                foundPair = false;
            }
        }
    }

    
}
    