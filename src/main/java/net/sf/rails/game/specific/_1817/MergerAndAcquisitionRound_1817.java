package net.sf.rails.game.specific._1817;

import net.sf.rails.game.Round;
import net.sf.rails.game.GameManager;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.state.GenericState;
import net.sf.rails.game.state.ArrayListState;
import net.sf.rails.game.financial.StockSpace;
import net.sf.rails.game.specific._1817.action.TakeLoans_1817;
import net.sf.rails.game.financial.Bank;
import net.sf.rails.game.financial.PublicCertificate;
import net.sf.rails.game.financial.BankPortfolio;
import rails.game.action.PossibleAction;
import rails.game.action.NullAction;
import net.sf.rails.game.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MergerAndAcquisitionRound_1817 extends Round {

    private static final Logger log = LoggerFactory.getLogger(MergerAndAcquisitionRound_1817.class);

    public enum MaAStep {
        START,
        COMPANY_ACTIONS,
        POST_MERGER_PRESIDENT,
        POST_MERGER_PLAYERS,
        LOAN_TAKING, // New: President decides on loans
        TOKEN_PURCHASE, // New: Automatic logic for mandatory tokens
        SALES_LIQUIDATION, // Forced auction for red-zone
        SALES_ACQUISITION, // Forced offering for gray-zone
        SALES_FRIENDLY, // Optional offering for white/green
        SALES_AUCTION, // The bidding process
        SALES_SELECT_BUYER, // President selects which company makes the purchase
        NEXT_COMPANY,
        FINISHED
    }

    private final GenericState<MaAStep> currentStep;
    private final GenericState<PublicCompany> operatingCompany;
    private final ArrayListState<PublicCompany> operatingCompanies;
    private final GenericState<Integer> companyIndex;
    private final GenericState<Integer> currentPlayerIndex;
    private final GenericState<Integer> playersProcessed;
    private final GameManager gameManagerRef;

    private final ArrayListState<String> validMergerPairs;
    private final ArrayListState<String> mergedThisRound;

    private final GenericState<Integer> highestBid;
    private final GenericState<net.sf.rails.game.Player> highestBiddingPlayer;
    private final ArrayListState<net.sf.rails.game.Player> activeBidders;
    private final GenericState<Integer> auctionPlayerIndex;

    public MergerAndAcquisitionRound_1817(GameManager parent, String id) {
        super(parent, id);
        this.gameManagerRef = parent;
        currentStep = new GenericState<>(this, "currentStep", MaAStep.START);
        operatingCompany = new GenericState<>(this, "operatingCompany", null);
        operatingCompanies = new ArrayListState<>(this, "operatingCompanies");
        companyIndex = new GenericState<>(this, "companyIndex", 0);
        currentPlayerIndex = new GenericState<>(this, "currentPlayerIndex", 0);
        playersProcessed = new GenericState<>(this, "playersProcessed", 0);
        validMergerPairs = new ArrayListState<>(this, "validMergerPairs");
        mergedThisRound = new ArrayListState<>(this, "mergedThisRound");
        highestBid = new GenericState<>(this, "highestBid", 0);
        highestBiddingPlayer = new GenericState<>(this, "highestBiddingPlayer", null);
        activeBidders = new ArrayListState<>(this, "activeBidders");
        auctionPlayerIndex = new GenericState<>(this, "auctionPlayerIndex", 0);
    }

    public void start() {
        operatingCompanies.clear();
        log.info("M&A ROUND: start() invoked.");
        List<PublicCompany> allCompanies = gameManagerRef.getRoot().getCompanyManager().getAllPublicCompanies();
        for (PublicCompany comp : allCompanies) {
            if (comp.hasFloated() && !comp.isClosed()) {
                operatingCompanies.add(comp);
                log.info("M&A ROUND: Added operating company: " + comp.getId());
            }
        }

        log.info("M&A ROUND: Total operating companies: " + operatingCompanies.size());
        calculateValidMergerPairs();
        currentStep.set(MaAStep.NEXT_COMPANY);
        log.info("M&A ROUND: Transitioning to NEXT_COMPANY step.");
        processNextCompany();
    }

    private void calculateValidMergerPairs() {
        validMergerPairs.clear();
        List<PublicCompany> companies = operatingCompanies.view();
        log.info("M&A ROUND: Calculating valid merger pairs for " + companies.size() + " companies.");
        for (int i = 0; i < companies.size(); i++) {
            for (int j = 0; j < companies.size(); j++) {
                if (i == j)
                    continue;
                PublicCompany c1 = companies.get(i);
                PublicCompany c2 = companies.get(j);

                if (mergedThisRound.contains(c1.getId()) || mergedThisRound.contains(c2.getId()))
                    continue;

                int shares1 = ((PublicCompany_1817) c1).getShareCount();
                int shares2 = ((PublicCompany_1817) c2).getShareCount();

                boolean isTwoShare = (shares1 == 2 && shares2 == 2);
                boolean isFiveShare = (shares1 == 5 && shares2 == 5);
                boolean samePresident = (c1.getPresident() != null && c1.getPresident().equals(c2.getPresident()));

                if ((isTwoShare || isFiveShare) && samePresident) {
                    validMergerPairs.add(c1.getId() + "," + c2.getId());
                    if (i < j) {
                        log.info("M&A ROUND: Valid pair found - " + c1.getId() + " and " + c2.getId());
                    }
                }

            }
        }
        log.info("M&A ROUND: Total valid pairs found: " + validMergerPairs.size());
    }

    private void processNextCompany() {
        int index = companyIndex.value();
        log.info("M&A ROUND: processNextCompany() invoked. Starting at index " + index);
        while (index < operatingCompanies.size()) {
            PublicCompany comp = operatingCompanies.get(index);
            boolean hasOptions = false;
            for (String pair : validMergerPairs.view()) {
                if (pair.startsWith(comp.getId() + ",")) {
                    hasOptions = true;
                    break;
                }
            }
            if (mergedThisRound.contains(comp.getId()) || !hasOptions) {
                log.info("M&A ROUND: Skipping " + comp.getId() + " (already merged or zero valid pairs).");
                index++;
            } else {
                log.info("M&A ROUND: Selected " + comp.getId() + " as the next operating company.");
                break;
            }
        }
        companyIndex.set(index);

        if (index >= operatingCompanies.size()) {
            log.info("M&A ROUND: Reached end of company list. Finishing round.");
            currentStep.set(MaAStep.FINISHED);
            gameManagerRef.nextRound(this);
            return;
        }

        operatingCompany.set(operatingCompanies.get(index));
        currentStep.set(MaAStep.COMPANY_ACTIONS);
        log.info("M&A ROUND: Setting actions for company " + operatingCompany.value().getId());
        setPossibleActions();
    }

    private void addPassAction(PublicCompany company) {
        if (company.getPresident() != null) {
            possibleActions.add(new NullAction(gameManagerRef.getRoot(), NullAction.Mode.PASS));
        }
    }

    @Override
    public boolean process(PossibleAction action) {
        log.info("M&A ROUND: process() invoked with action: " + action.getClass().getSimpleName());

        if (action instanceof NullAction && ((NullAction) action).getMode() == NullAction.Mode.PASS) {
            if (currentStep.value() == MaAStep.COMPANY_ACTIONS) {
                log.info("M&A ROUND: Processing PASS action for " + operatingCompany.value().getId());
                companyIndex.set(companyIndex.value() + 1);
                currentStep.set(MaAStep.NEXT_COMPANY);
                processNextCompany();
                return true;
            } else if (currentStep.value() == MaAStep.POST_MERGER_PRESIDENT) {
                initPostMergerPlayers();
                return true;
            } else if (currentStep.value() == MaAStep.POST_MERGER_PLAYERS) {
                advancePostMergerPlayer();
                return true;
            } else if (currentStep.value() == MaAStep.SALES_FRIENDLY) {
                log.info("M&A ROUND: President declined friendly sale for " + operatingCompany.value().getId());
                companyIndex.set(companyIndex.value() + 1);
                processNextSale();
                return true;

            } else if (currentStep.value() == MaAStep.SALES_AUCTION) {
                net.sf.rails.game.Player passingPlayer = activeBidders.get(auctionPlayerIndex.value());
                log.info("M&A ROUND: Player " + passingPlayer.getName() + " passed on bidding.");
                activeBidders.remove(auctionPlayerIndex.value());

                if (activeBidders.isEmpty()
                        || (activeBidders.size() == 1 && activeBidders.get(0).equals(highestBiddingPlayer.value()))) {
                    finalizeAuction();
                } else {
                    if (auctionPlayerIndex.value() >= activeBidders.size()) {
                        auctionPlayerIndex.set(0);
                    }
                    setPossibleActions();
                }
                return true;
            }
        } else if (action instanceof NullAction && ((NullAction) action).getMode() == NullAction.Mode.DONE) {
            if (currentStep.value() == MaAStep.POST_MERGER_PRESIDENT) {
                initPostMergerPlayers();
                return true;
            } else if (currentStep.value() == MaAStep.POST_MERGER_PLAYERS) {
                advancePostMergerPlayer();
                return true;
            } else if (currentStep.value() == MaAStep.LOAN_TAKING) {
                setupPostMergerLoanActions();

            } else if (currentStep.value() == MaAStep.SALES_FRIENDLY) {
                log.info("M&A ROUND: President offered " + operatingCompany.value().getId() + " for Friendly Sale.");
                int minBid = operatingCompany.value().getMarketPrice()
                        * ((PublicCompany_1817) operatingCompany.value()).getShareCount();
                startAuction(operatingCompany.value(), minBid);

                return true;
            }

        } else if (action instanceof MergeCompanies_1817) {
            MergeCompanies_1817 mergeAction = (MergeCompanies_1817) action;

            PublicCompany initiator = mergeAction.getInitiatingCompany();
            PublicCompany target = mergeAction.getTargetCompany();
            log.info("M&A ROUND: Processing MERGE action: " + initiator.getId() + " absorbs " + target.getId());

            int shares = ((PublicCompany_1817) initiator).getShareCount();
            if (shares == 2) {
                execute2ShareMerger(initiator, target);
            } else if (shares == 5) {
                execute5ShareMerger(initiator, target);
            }
            return true;

       } else if (action instanceof net.sf.rails.game.specific._1817.action.ConvertCompany_1817) {
            net.sf.rails.game.specific._1817.action.ConvertCompany_1817 convertAction = (net.sf.rails.game.specific._1817.action.ConvertCompany_1817) action;
            PublicCompany comp = convertAction.getCompany();
            log.info("M&A ROUND: Processing CONVERT action for: " + comp.getId());
            executeConversion(comp);
            return true;
        } else if (action instanceof rails.game.action.BuyCertificate) {
            PublicCompany comp = operatingCompany.value();
            net.sf.rails.game.Player player;
            if (currentStep.value() == MaAStep.POST_MERGER_PRESIDENT) {
                player = comp.getPresident();
            } else {
                player = gameManagerRef.getRoot().getPlayerManager().getPlayers().get(currentPlayerIndex.value());
            }

            for (net.sf.rails.game.financial.PublicCertificate cert : comp.getCertificates()) {
                if (cert.getOwner() == comp && !cert.isPresidentShare()) {
                    player.setCash_AI(player.getCash() - comp.getMarketPrice());
                    comp.setCash_AI(comp.getCash() + comp.getMarketPrice());
                    cert.moveTo(player.getPortfolioModel());
                    log.info("M&A ROUND: " + player.getName() + " bought 1 share of " + comp.getId()
                            + " from Treasury.");
                    break;
                }
            }

            if (currentStep.value() == MaAStep.POST_MERGER_PLAYERS) {
                advancePostMergerPlayer();
            } else {
                if (hasValidTreasuryBuy(comp, player)) {
                    setPossibleActions();
                } else {
                    log.info("M&A ROUND: President " + player.getName()
                            + " has insufficient funds or no treasury shares remain. Auto-ending President purchase phase.");
                    initPostMergerPlayers();
                }
            }
            return true;

        } else if (action instanceof rails.game.action.SellShares) {
            rails.game.action.SellShares sellAction = (rails.game.action.SellShares) action;
            PublicCompany comp = operatingCompany.value();
            net.sf.rails.game.Player player = gameManagerRef.getRoot().getPlayerManager().getPlayers()
                    .get(currentPlayerIndex.value());
            int numberToSell = sellAction.getNumber();

            int sold = 0;
            for (net.sf.rails.game.financial.PublicCertificate cert : new java.util.ArrayList<>(
                    player.getPortfolioModel().getCertificates())) {
                if (sold >= numberToSell)
                    break;
                if (cert.getCompany() == comp && !cert.isPresidentShare()) {
                    cert.moveTo(gameManagerRef.getRoot().getBank().getPool());
                    player.setCash_AI(player.getCash() + comp.getMarketPrice());
                    sold++;
                }
            }
            log.info("M&A ROUND: " + player.getName() + " sold " + sold + " shares of " + comp.getId()
                    + " to Open Market.");

            if (currentStep.value() == MaAStep.POST_MERGER_PLAYERS)
                advancePostMergerPlayer();
            return true;
        } else if (action instanceof TakeLoans_1817) {

            TakeLoans_1817 loanAction = (TakeLoans_1817) action;
            PublicCompany comp = operatingCompany.value();
            int amount = loanAction.getLoansToTake();

            if (amount > 0) {

                if (comp instanceof PublicCompany_1817) {
                    PublicCompany_1817 comp1817 = (PublicCompany_1817) comp;
                    comp1817.setNumberOfBonds(comp1817.getNumberOfBonds() + amount);
                    net.sf.rails.game.financial.Bank bank = gameManagerRef.getRoot().getBank();
                    comp1817.addCashFromBank(amount * 100, bank);
                }

                // Move stock one space left per loan
                for (int i = 0; i < amount; i++) {
                    // TODO: Replace with your specific 1817 StockMarket method to move left
                    // gameManagerRef.getRoot().getStockMarket().moveSpace(comp, -1);
                }
            }

            log.info("M&A ROUND: " + comp.getId() + " took " + amount + " loans.");

            // Proceed to mandatory token check
            currentStep.set(MaAStep.TOKEN_PURCHASE);
            executeMandatoryTokenPurchase();
            return true;
        } else if (action instanceof net.sf.rails.game.specific._1817.action.BidOnCompany_1817) {
            net.sf.rails.game.specific._1817.action.BidOnCompany_1817 bidAction = (net.sf.rails.game.specific._1817.action.BidOnCompany_1817) action;
            int bidAmount = bidAction.getBidAmount();
            net.sf.rails.game.Player bidder = activeBidders.get(auctionPlayerIndex.value());

            log.info("M&A ROUND: " + bidder.getName() + " bid $" + bidAmount + " on "
                    + operatingCompany.value().getId());
            highestBid.set(bidAmount);
            highestBiddingPlayer.set(bidder);

            auctionPlayerIndex.set((auctionPlayerIndex.value() + 1) % activeBidders.size());

            if (activeBidders.size() == 1) {
                finalizeAuction();
            } else {
                setPossibleActions();
            }
            return true;
        } else if (action instanceof net.sf.rails.game.specific._1817.action.SelectPurchasingCompany_1817) {
            net.sf.rails.game.specific._1817.action.SelectPurchasingCompany_1817 selectAction = (net.sf.rails.game.specific._1817.action.SelectPurchasingCompany_1817) action;
            PublicCompany target = operatingCompany.value();
            PublicCompany predator = gameManagerRef.getRoot().getCompanyManager()
                    .getPublicCompany(selectAction.getCompanyId());
            int finalBid = highestBid.value();

            log.info("M&A ROUND: President " + highestBiddingPlayer.value().getName() + " selected " + predator.getId()
                    + " to make the purchase.");
            executeSale(target, predator, finalBid);
            return true;
        }

        log.warn("M&A ROUND: Action not processed: " + action.getClass().getSimpleName());
        return false;
    }


    private void executeConversion(PublicCompany comp) {
        log.info(">>> Executing Conversion for: " + comp.getId());
        PublicCompany_1817 comp1817 = (PublicCompany_1817) comp;

        int currentShares = comp1817.getShareCount();
        if (currentShares == 2) {
            comp1817.setShareCount(5);
            log.info("CONVERSION: Upgraded " + comp.getId() + " from 2-share to 5-share. ");
        } else if (currentShares == 5) {
            comp1817.setShareCount(10);
            log.info("CONVERSION: Upgraded " + comp.getId() + " from 5-share to 10-share. ");
        }

        // Mark as merged/converted this round so it cannot merge again [cite: 708, 709]
        mergedThisRound.add(comp.getId());

        // Initiate Post-Merger Phase (same as mergers) [cite: 715]
        calculateValidMergerPairs();
        startPostMergerPhase();
    }

    
    private void execute2ShareMerger(PublicCompany initiator, PublicCompany target) {
        log.info(">>> Executing Merger: " + initiator.getId() + " absorbs " + target.getId());

        int initialCash = initiator.getCash();
        int targetCash = target.getCash();
        int initialTrains = initiator.getTrains().size();
        int targetTrains = target.getTrains().size();

        // 1. Core Asset Consolidation (Cash, Trains, Privates)
        initiator.transferAssetsFrom(target);

        log.info("MERGER ASSETS: " + initiator.getId() + " cash changed from " + initialCash + " to "
                + initiator.getCash() + " (Absorbed: " + targetCash + ")");
        log.info("MERGER ASSETS: " + initiator.getId() + " trains changed from " + initialTrains + " to "
                + initiator.getTrains().size() + " (Absorbed: " + targetTrains + ")");

        // 2. Loan Consolidation
        if (initiator.canLoan() && target.canLoan()) {
            int targetLoans = target.getCurrentNumberOfLoans();
            log.info("MERGER LOANS: Transferring " + targetLoans + " loans from " + target.getId() + " to "
                    + initiator.getId());
            initiator.addLoans(targetLoans);
            target.addLoans(-targetLoans);
        }

        // 3. Bond Consolidation
        int targetBonds = target.getNumberOfBonds();
        log.info("MERGER BONDS: Transferring " + targetBonds + " bonds from " + target.getId() + " to "
                + initiator.getId());
        initiator.setNumberOfBonds(initiator.getNumberOfBonds() + targetBonds);
        target.setNumberOfBonds(0);

        // 4. Station Marker Conversion and Collision Check
        for (net.sf.rails.game.BaseToken targetToken : target.getLaidBaseTokens()) {
            if (targetToken.getOwner() instanceof net.sf.rails.game.Stop) {
                net.sf.rails.game.Stop stop = (net.sf.rails.game.Stop) targetToken.getOwner();

                boolean hexHasInitiatorToken = false;
                for (net.sf.rails.game.Stop s : stop.getHex().getStops()) {
                    if (s.hasTokenOf(initiator)) {
                        hexHasInitiatorToken = true;
                        break;
                    }
                }

                if (!hexHasInitiatorToken) {
                    net.sf.rails.game.BaseToken newToken = initiator.getNextBaseToken();
                    if (newToken != null) {
                        newToken.moveTo(stop);
                    }
                }
            }
        }

        // 5. Market Value Adjustment and UI Marker Move
        StockSpace oldSpace = initiator.getCurrentSpace();
        int newPriceValue = initiator.getMarketPrice() + target.getMarketPrice();
        StockSpace newSpace = gameManagerRef.getRoot().getStockMarket().getStartSpace(newPriceValue);
        log.info("MERGER MARKET: Combined price " + initiator.getMarketPrice() + " + " + target.getMarketPrice() + " = "
                + newPriceValue);

        if (newSpace != null) {
            // Remove token from old UI space
            if (oldSpace != null) {
                oldSpace.removeToken(initiator);
            }
            // Add token to new UI space and update internal state
            newSpace.addToken(initiator);
            initiator.setCurrentSpace(newSpace);
            log.info("MERGER MARKET: Moved " + initiator.getId() + " to space " + newSpace.getId());
        }

        // 6. Share Structure Update
        ((PublicCompany_1817) initiator).setShareCount(5);
        log.info("MERGER SHARES: Upgraded " + initiator.getId()
                + " to a 5-share company. Certificates and tokens automatically adjusted.");

        // The President already holds the Initiator's President's Certificate.
        // Upon upgrading to a 5-share company, this certificate represents 40% (2
        // shares).
        // The 4 original shares (2 from Target, 2 from Initiator) exchange 2-for-1 into
        // these 2 new shares.
        // The remaining 3 shares stay in the Initiator's treasury. No extra shares are
        // transferred.
        log.info("MERGER SHARES: President retains the 40% President's Certificate. 3 shares remain in Treasury.");

        // 7. Remove Disappearing Company
        target.setClosed();

        // 8. Update State tracking
        mergedThisRound.add(initiator.getId());
        mergedThisRound.add(target.getId());

        // 9. Initiate Post-Merger Phase
        calculateValidMergerPairs();
        startPostMergerPhase();

    }

    private void execute5ShareMerger(PublicCompany initiator, PublicCompany target) {
        log.info(">>> Executing 5-Share Merger: " + initiator.getId() + " absorbs " + target.getId());

        // 1. Core Asset Consolidation
        initiator.transferAssetsFrom(target);

        // 2. Loan Consolidation
        if (initiator.canLoan() && target.canLoan()) {
            int targetLoans = target.getCurrentNumberOfLoans();
            initiator.addLoans(targetLoans);
            target.addLoans(-targetLoans);
        }

        // 3. Bond Consolidation
        int targetBonds = target.getNumberOfBonds();
        initiator.setNumberOfBonds(initiator.getNumberOfBonds() + targetBonds);
        target.setNumberOfBonds(0);

        // 4. Station Marker Conversion
        for (net.sf.rails.game.BaseToken targetToken : target.getLaidBaseTokens()) {
            if (targetToken.getOwner() instanceof net.sf.rails.game.Stop) {
                net.sf.rails.game.Stop stop = (net.sf.rails.game.Stop) targetToken.getOwner();
                boolean hexHasInitiatorToken = false;
                for (net.sf.rails.game.Stop s : stop.getHex().getStops()) {
                    if (s.hasTokenOf(initiator)) {
                        hexHasInitiatorToken = true;
                        break;
                    }
                }
                if (!hexHasInitiatorToken) {
                    net.sf.rails.game.BaseToken newToken = initiator.getNextBaseToken();
                    if (newToken != null)
                        newToken.moveTo(stop);
                }
            }
        }

        // 5. Market Value Adjustment
        StockSpace oldSpace = initiator.getCurrentSpace();
        int newPriceValue = initiator.getMarketPrice() + target.getMarketPrice();
        StockSpace newSpace = gameManagerRef.getRoot().getStockMarket().getStartSpace(newPriceValue);

        if (newSpace != null) {
            if (oldSpace != null)
                oldSpace.removeToken(initiator);
            newSpace.addToken(initiator);
            initiator.setCurrentSpace(newSpace);
        }

        // 6. Share Structure Update
        ((PublicCompany_1817) initiator).setShareCount(10);

        // 1-for-1 ownership exchange mapping
        List<PublicCertificate> availableCerts = new java.util.ArrayList<>();
        for (PublicCertificate cert : initiator.getCertificates()) {
            if (cert.getOwner() == initiator && !cert.isPresidentShare()) {
                availableCerts.add(cert);
            }
        }

        int certIndex = 0;
        for (PublicCertificate tCert : target.getCertificates()) {
            net.sf.rails.game.state.Owner owner = tCert.getOwner();
            if (owner != null && owner != target) {
                int shareUnits = tCert.getShare() / target.getShareUnit();
                for (int i = 0; i < shareUnits; i++) {
                    if (certIndex < availableCerts.size()) {
                        PublicCertificate newICert = availableCerts.get(certIndex);
                        if (owner instanceof net.sf.rails.game.Player) {
                            newICert.moveTo(((net.sf.rails.game.Player) owner).getPortfolioModel());
                        } else if (owner instanceof net.sf.rails.game.financial.Bank) {
                            newICert.moveTo(gameManagerRef.getRoot().getBank().getPool());
                        } else if (owner instanceof net.sf.rails.game.PublicCompany) {
                            newICert.moveTo(((net.sf.rails.game.PublicCompany) owner).getPortfolioModel());
                        }
                        certIndex++;
                    }
                }
            } else if (owner == target) {
                // Keep target's treasury shares in initiator's treasury
                int shareUnits = tCert.getShare() / target.getShareUnit();
                certIndex += shareUnits;
            }
        }
        log.info("MERGER SHARES: Upgraded " + initiator.getId() + " to 10-share. 1-for-1 ownership exchange complete.");

        // 7. Remove Disappearing Company
        target.setClosed();

        // 8. Update State tracking
        mergedThisRound.add(initiator.getId());
        mergedThisRound.add(target.getId());

        // 9. Initiate Post-Merger Phase
        calculateValidMergerPairs();
        startPostMergerPhase();
    }

    private void endPostMergerPhase() {
        log.info("M&A ROUND: Post-merger phase completed. Advancing to NEXT_COMPANY.");
        companyIndex.set(companyIndex.value() + 1);
        currentStep.set(MaAStep.NEXT_COMPANY);
        processNextCompany();
    }

    private void initPostMergerPlayers() {
        PublicCompany comp = operatingCompany.value();
        net.sf.rails.game.Player president = comp.getPresident();
        List<net.sf.rails.game.Player> players = gameManagerRef.getRoot().getPlayerManager().getPlayers();

        int presIndex = players.indexOf(president);
        currentPlayerIndex.set((presIndex + 1) % players.size());
        playersProcessed.set(1);

        skipInvalidPostMergerPlayers(comp, players);
    }

    private void advancePostMergerPlayer() {
        PublicCompany comp = operatingCompany.value();
        List<net.sf.rails.game.Player> players = gameManagerRef.getRoot().getPlayerManager().getPlayers();

        currentPlayerIndex.set((currentPlayerIndex.value() + 1) % players.size());
        playersProcessed.set(playersProcessed.value() + 1);

        skipInvalidPostMergerPlayers(comp, players);
    }

    private void skipInvalidPostMergerPlayers(PublicCompany comp, List<net.sf.rails.game.Player> players) {
        while (playersProcessed.value() < players.size()) {
            net.sf.rails.game.Player p = players.get(currentPlayerIndex.value());
            if (hasValidTreasuryBuy(comp, p) || hasValidSell(comp, p)) {
                break;
            }
            log.info("M&A ROUND: Player " + p.getName()
                    + " lacks funds to buy and has no shares to sell. Auto-passing.");
            currentPlayerIndex.set((currentPlayerIndex.value() + 1) % players.size());
            playersProcessed.set(playersProcessed.value() + 1);
        }

        if (playersProcessed.value() >= players.size()) {
            currentStep.set(MaAStep.LOAN_TAKING);
            setPossibleActions();
        } else {
            currentStep.set(MaAStep.POST_MERGER_PLAYERS);
            setPossibleActions();
        }
    }

    private void setupPostMergerPresidentActions() {
        PublicCompany comp = operatingCompany.value();
        net.sf.rails.game.Player president = comp.getPresident();
        if (president != null) {
            log.info("M&A ROUND: Setting up post-merger actions for President " + president.getName());
            addTreasuryBuyActions(comp, president);
            possibleActions.add(new NullAction(gameManagerRef.getRoot(), NullAction.Mode.DONE));
        }
    }

    private void setupPostMergerPlayerActions() {
        PublicCompany comp = operatingCompany.value();
        List<net.sf.rails.game.Player> players = gameManagerRef.getRoot().getPlayerManager().getPlayers();
        int idx = currentPlayerIndex.value();
        if (idx >= 0 && idx < players.size()) {
            net.sf.rails.game.Player player = players.get(idx);
            log.info("M&A ROUND: Setting up post-merger actions for Player " + player.getName());

            addTreasuryBuyActions(comp, player);
            addSellActions(comp, player);

            possibleActions.add(new NullAction(gameManagerRef.getRoot(), NullAction.Mode.PASS));
        }
    }

    private void addSellActions(PublicCompany comp, net.sf.rails.game.Player player) {
        int sharesOwned = 0;
        for (net.sf.rails.game.financial.PublicCertificate cert : player.getPortfolioModel().getCertificates()) {
            if (cert.getCompany() == comp && !cert.isPresidentShare()) {
                sharesOwned += cert.getShare() / comp.getShareUnit();
            }
        }

        if (sharesOwned > 0) {
            int price = comp.getMarketPrice();
            for (int i = 1; i <= sharesOwned; i++) {
                possibleActions.add(new rails.game.action.SellShares(comp, comp.getShareUnit(), i, price));
            }
        }
    }

    private boolean hasValidSell(PublicCompany comp, net.sf.rails.game.Player player) {
        for (net.sf.rails.game.financial.PublicCertificate cert : player.getPortfolioModel().getCertificates()) {
            if (cert.getCompany() == comp && !cert.isPresidentShare()) {
                return true;
            }
        }
        return false;
    }

    private void updateCurrentPlayer() {
        MaAStep step = currentStep.value();
        net.sf.rails.game.Player p = null;
        if (step == MaAStep.COMPANY_ACTIONS || step == MaAStep.POST_MERGER_PRESIDENT || step == MaAStep.LOAN_TAKING) {
            PublicCompany comp = operatingCompany.value();
            if (comp != null)
                p = comp.getPresident();
        } else if (step == MaAStep.POST_MERGER_PLAYERS) {
            List<net.sf.rails.game.Player> players = gameManagerRef.getRoot().getPlayerManager().getPlayers();
            int idx = currentPlayerIndex.value();
            if (idx >= 0 && idx < players.size()) {
                p = players.get(idx);
            }
        }
        if (p != null && !p.equals(gameManagerRef.getRoot().getPlayerManager().getCurrentPlayer())) {
            gameManagerRef.getRoot().getPlayerManager().setCurrentPlayer(p);
            log.info("M&A ROUND: Synchronized UI Current Player to " + p.getName());
        }
    }

    @Override
    public boolean setPossibleActions() {
        updateCurrentPlayer();
        possibleActions.clear();

        if (currentStep.value() == MaAStep.COMPANY_ACTIONS) {
            setupCompanyActions(operatingCompany.value());
        } else if (currentStep.value() == MaAStep.POST_MERGER_PRESIDENT) {
            setupPostMergerPresidentActions();
        } else if (currentStep.value() == MaAStep.POST_MERGER_PLAYERS) {
            setupPostMergerPlayerActions();
        } else if (currentStep.value() == MaAStep.LOAN_TAKING) {
            setupPostMergerLoanActions();
            // Provide escape buttons if the company is not forced into another action
            if (currentStep.value() == MaAStep.LOAN_TAKING) {
                possibleActions.add(new NullAction(gameManagerRef.getRoot(), NullAction.Mode.PASS));
                possibleActions.add(new NullAction(gameManagerRef.getRoot(), NullAction.Mode.DONE));
            }
        } else if (currentStep.value() == MaAStep.SALES_FRIENDLY) {
            // President's decision: DONE to offer for sale, PASS to decline and keep
            updateCurrentPlayer();
            possibleActions.add(new NullAction(gameManagerRef.getRoot(), NullAction.Mode.DONE));
            possibleActions.add(new NullAction(gameManagerRef.getRoot(), NullAction.Mode.PASS));
        } else if (currentStep.value() == MaAStep.SALES_AUCTION) {
            setupAuctionActions();
        } else if (currentStep.value() == MaAStep.SALES_SELECT_BUYER) {
            net.sf.rails.game.Player winner = highestBiddingPlayer.value();
            gameManagerRef.getRoot().getPlayerManager().setCurrentPlayer(winner);
            PublicCompany target = operatingCompany.value();

            // Winning president must choose which of their eligible companies makes the buy
            for (PublicCompany comp : gameManagerRef.getRoot().getCompanyManager().getAllPublicCompanies()) {
                if (comp.getPresident() != null && comp.getPresident().equals(winner) && !comp.equals(target)) {
                    // Predators must not be in the liquidation or acquisition zones ($30 or less)
                    if (comp.getCurrentSpace() != null && comp.getCurrentSpace().getPrice() > 30) {
                        possibleActions.add(new net.sf.rails.game.specific._1817.action.SelectPurchasingCompany_1817(
                                gameManagerRef.getRoot(), comp.getId()));
                    }
                }
            }
        }
        return true;
    }

    private int countPlayerShares(PublicCompany comp, net.sf.rails.game.Player player) {
        int count = 0;
        // Efficiently iterate only through certificates of this specific company
        for (net.sf.rails.game.financial.PublicCertificate cert : player.getPortfolioModel().getCertificates(comp)) {
            // cert.getShare() is the percentage (e.g., 20 or 40)
            // comp.getShareUnit() is the percentage per share (e.g., 20 for 5-share)
            count += cert.getShare() / comp.getShareUnit();
        }
        return count;
    }

    private int getPlayerSharePercentage(PublicCompany comp, net.sf.rails.game.Player player) {
        int percentage = 0;
        for (net.sf.rails.game.financial.PublicCertificate cert : player.getPortfolioModel().getCertificates(comp)) {
            percentage += cert.getShare();
        }
        return percentage;
    }

    private void addTreasuryBuyActions(PublicCompany comp, net.sf.rails.game.Player player) {
        int price = comp.getMarketPrice();

        // 1817 Rule 5.2: 60% Maximum ownership limit
        if (player.getCash() < price || getPlayerSharePercentage(comp, player) >= 60)
            return;

        for (net.sf.rails.game.financial.PublicCertificate cert : comp.getCertificates()) {
            if (cert.getOwner() == comp && !cert.isPresidentShare()) {
                possibleActions.add(new rails.game.action.BuyCertificate(comp, cert.getShare(), comp, price));
                break;
            }
        }
    }

    private boolean hasValidTreasuryBuy(PublicCompany comp, net.sf.rails.game.Player player) {
        int price = comp.getMarketPrice();

        if (player.getCash() >= price && getPlayerSharePercentage(comp, player) < 60) {
            for (net.sf.rails.game.financial.PublicCertificate cert : comp.getCertificates()) {
                if (cert.getOwner() == comp && !cert.isPresidentShare()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void startPostMergerPhase() {
        PublicCompany comp = operatingCompany.value();
        net.sf.rails.game.Player president = comp.getPresident();

        if (president != null && hasValidTreasuryBuy(comp, president)) {
            currentStep.set(MaAStep.POST_MERGER_PRESIDENT);
            setPossibleActions();
        } else {
            if (president != null) {
                log.info("M&A ROUND: President " + president.getName()
                        + " cannot buy treasury shares (insufficient funds). Auto-passing.");
            }
            initPostMergerPlayers();
        }
    }

    private void setupPostMergerLoanActions() {
        PublicCompany comp = operatingCompany.value();
        if (comp instanceof PublicCompany_1817) {
            PublicCompany_1817 comp1817 = (PublicCompany_1817) comp;
            // Limit is based on 2, 5, or 10 shares
            int limit = comp1817.getShareCount();
            int current = comp.getNumberOfBonds();

            if (current < limit) {

                possibleActions.add(new TakeLoans_1817(getRoot(), comp.getId(), limit));
                // Add a DONE action so the player can choose NOT to take a loan
                possibleActions.add(new NullAction(gameManagerRef.getRoot(), NullAction.Mode.DONE));
                log.info("M&A ROUND: Offering loans to " + comp.getId() + " (Current: " + current + ", Max: " + limit
                        + ")");
            } else {
                // If at limit, skip to token purchase automatically
                currentStep.set(MaAStep.TOKEN_PURCHASE);
                executeMandatoryTokenPurchase();
            }
        }
    }

    private void executeMandatoryTokenPurchase() {
        PublicCompany comp = operatingCompany.value();
        PublicCompany_1817 comp1817 = (PublicCompany_1817) comp;

        int cost = 0;

        // Use the built-in helper from BaseTokensModel to get total marker count
        int currentMarkers = comp.getBaseTokens().nbAllTokens();

        // 2 -> 5 Conversion: Basic number is 2 station markers
        // Must buy 1 additional marker for $50 [cite: 520, 717]
        if (comp1817.getShareCount() == 5 && currentMarkers < 2) {
            cost = 50;
        }
        // 5 -> 10 Conversion: Basic number is 4 station markers
        // Must buy up to 3 additional markers for $150 ($50 each) [cite: 524, 718]
        else if (comp1817.getShareCount() == 10) {
            if (currentMarkers == 2)
                cost = 100; // Needs 2 more to reach 4
            else if (currentMarkers == 3)
                cost = 50; // Needs 1 more to reach 4
        }

        if (cost > 0) {
            if (comp.getCash() >= cost) {
                comp.setCash_AI(comp.getCash() - cost);
                log.info("M&A ROUND: " + comp.getId() + " paid $" + cost + " for mandatory station markers.");
                // TODO: Logic to physically add the new BaseToken objects to the model
            } else {
                log.warn("M&A ROUND: " + comp.getId() + " cannot afford mandatory markers. LIQUIDATING.");
                // TODO: Implement the specific 1817 StockMarket liquidation call here [cite:
                // 720]
                // comp.setClosed();
            }
        }

        // Finalize this company and move to the next in the M&A order
        endPostMergerPhase();
    }

    private void setupCompanyActions(PublicCompany company) {
        log.info("M&A ROUND: setupCompanyActions() invoked for " + company.getId());
        for (String pair : validMergerPairs.view()) {
            String[] ids = pair.split(",");
            // --- START FIX ---
            if (ids[0].equals(company.getId())) {
                PublicCompany target = gameManagerRef.getRoot().getCompanyManager().getPublicCompany(ids[1]);
                if (target != null) {
                    possibleActions.add(new MergeCompanies_1817(company, target));
                    log.info("M&A ROUND: Added MergeCompanies_1817 action for " + company.getId() + " -> "
                            + target.getId());
                }
            }
        }

        // Add TakeLoans_1817 action if the company is capable
        if (company instanceof PublicCompany_1817) {
            PublicCompany_1817 comp1817 = (PublicCompany_1817) company;
            int maxLoans = comp1817.getShareCount();
            if (company.getNumberOfBonds() < maxLoans) {
                possibleActions.add(new net.sf.rails.game.specific._1817.action.TakeLoans_1817(getRoot(), company.getId(), maxLoans));
                log.info("M&A ROUND: Added TakeLoans_1817 action for " + company.getId() + " with max loans: "
                        + maxLoans);
            }

            // Check for Conversions (White zone only, > $30) 
            if (company.getCurrentSpace() != null && company.getCurrentSpace().getPrice() > 30) {
                if (comp1817.getShareCount() == 2 || comp1817.getShareCount() == 5) {
                    possibleActions.add(new net.sf.rails.game.specific._1817.action.ConvertCompany_1817(company));
                    log.info("M&A ROUND: Added ConvertCompany_1817 action for " + company.getId());
                }
            }

        }

        
        addPassAction(company);
        log.info("M&A ROUND: Total possible actions generated: " + possibleActions.getList().size());
    }

    /**
     * Initiates the sales sequence in reverse operating order.
     */
    private void startSalesPhase() {
        log.info("M&A ROUND: Starting Sales Phase (Reverse Operating Order).");
        List<PublicCompany> allComps = gameManagerRef.getRoot().getCompanyManager().getAllPublicCompanies();

        // Sort in reverse operating order (lowest price first)
        List<PublicCompany> sortedComps = allComps.stream()
                .filter(c -> c.hasFloated() && !c.isClosed())
                .sorted((c1, c2) -> Integer.compare(c1.getMarketPrice(), c2.getMarketPrice()))
                .collect(java.util.stream.Collectors.toList());

        operatingCompanies.clear();
        for (PublicCompany c : sortedComps) {
            operatingCompanies.add(c);
        }
        companyIndex.set(0);
        currentStep.set(MaAStep.SALES_LIQUIDATION);
        processNextSale();
    }

    private void processNextSale() {
        if (companyIndex.value() >= operatingCompanies.size()) {
            // Move to next logical phase of the round
            currentStep.set(MaAStep.FINISHED);
            gameManagerRef.nextRound(this);
            return;
        }

        PublicCompany comp = operatingCompanies.get(companyIndex.value());
        operatingCompany.set(comp);

        int price = comp.getMarketPrice();

        boolean isRed = false;
        boolean isGray = false;

        if (comp.hasStockPrice() && comp.getCurrentSpace() != null) {
            int currentPrice = comp.getCurrentSpace().getPrice();
            isRed = (currentPrice == 0);
            isGray = (currentPrice > 0 && currentPrice <= 30);
        }

        if (isRed) {
            log.info("M&A ROUND: Forced Liquidation Sale for " + comp.getId());

            startAuction(comp, 0);
        } else if (isGray) {
            log.info("M&A ROUND: Forced Acquisition Offering for " + comp.getId());
            startAuction(comp, 10);

        } else {
            log.info("M&A ROUND: Asking President if " + comp.getId() + " is up for Friendly Sale.");
            currentStep.set(MaAStep.SALES_FRIENDLY);
            setPossibleActions();
        }
    }

    private void startAuction(PublicCompany target, int minBid) {
        log.info("M&A ROUND: Starting auction for " + target.getId() + " with min bid: $" + minBid);
        highestBid.set(minBid);
        highestBiddingPlayer.set(null);

        activeBidders.clear();
        List<net.sf.rails.game.Player> players = gameManagerRef.getRoot().getPlayerManager().getPlayers();
        int presIndex = players.indexOf(target.getPresident());

        for (int i = 1; i <= players.size(); i++) {
            activeBidders.add(players.get((presIndex + i) % players.size()));
        }

        auctionPlayerIndex.set(0);
        currentStep.set(MaAStep.SALES_AUCTION);
        setPossibleActions();
    }

    private void executeSale(PublicCompany target, PublicCompany predator, int finalBid) {
        log.info(">>> Executing Sale: " + (predator != null ? predator.getId() : "Bank") + " buys " + target.getId()
                + " for $" + finalBid);

        int currentPrice = target.getMarketPrice();
        boolean isRed = (target.getCurrentSpace().getPrice() == 0);
        boolean isGray = (target.getCurrentSpace().getPrice() > 0 && target.getCurrentSpace().getPrice() <= 30);

        // 1. Treasury Share Sell-off (Rule 7.2.4 [cite: 827, 829])
        int treasurySharesSold = 0;
        for (PublicCertificate cert : new java.util.ArrayList<>(target.getCertificates())) {
            if (cert.getOwner() == target && !cert.isPresidentShare()) {
                cert.moveTo(gameManagerRef.getRoot().getBank().getPool());
                treasurySharesSold++;
            }
        }

        if (!isRed && !isGray && treasurySharesSold > 0) {
            int cashInfusion = treasurySharesSold * target.getMarketPrice();
            target.setCash_AI(target.getCash() + cashInfusion);
            log.info("M&A ROUND: Treasury shares sold. Infused $" + cashInfusion + " into " + target.getId());
        }

        // 2. Payout Calculation (Rule 7.2.4 [cite: 845])
        int shareCount = ((PublicCompany_1817) target).getShareCount();
        int payoutPerShare = finalBid / shareCount;

        // 3. Execution of Sale Assets (Rule 7.2.4 [cite: 763, 764])
        if (predator != null) {
            // Predator pays the Bank (Rule 7.2.4 [cite: 836])
            predator.setCash_AI(predator.getCash() - finalBid);

            predator.transferAssetsFrom(target);
            if (predator instanceof PublicCompany_1817 && target instanceof PublicCompany_1817) {
                int acquiredBonds = ((PublicCompany_1817) target).getNumberOfBonds();
                ((PublicCompany_1817) predator).setNumberOfBonds(predator.getNumberOfBonds() + acquiredBonds);

                // Loan Penalty (Rule 7.2.4 )
                for (int i = 0; i < acquiredBonds; i++) {
                    // TODO: Replace with your specific 1817 StockMarket method to move left
                    // Example: gameManagerRef.getRoot().getStockMarket().moveSpace(predator, -1);
                }
            }
        }

        // 4. Shareholder Settlement (Rule 7.2.4 [cite: 844, 847])
        settleWithShareholders(target, payoutPerShare);

        log.info("M&A ROUND: Sale complete.");
        target.setClosed();
        companyIndex.set(companyIndex.value() + 1);
        processNextSale();
    }

    private void setupAuctionActions() {
        if (activeBidders.isEmpty())
            return;

        net.sf.rails.game.Player activePlayer = activeBidders.get(auctionPlayerIndex.value());
        gameManagerRef.getRoot().getPlayerManager().setCurrentPlayer(activePlayer);
        log.info("M&A ROUND: Setting up auction actions for " + activePlayer.getName());

        PublicCompany target = operatingCompany.value();
        int currentHighestBid = highestBid.value();
        net.sf.rails.game.Player president = target.getPresident();

        possibleActions.add(new NullAction(gameManagerRef.getRoot(), NullAction.Mode.PASS));

        boolean isPresident = activePlayer.equals(president);
        int minNextBid = (currentHighestBid == 0 && highestBiddingPlayer.value() == null) ? currentHighestBid
                : currentHighestBid + 10;

        if (currentHighestBid > 0 && highestBiddingPlayer.value() == null) {
            minNextBid = currentHighestBid;
        }

        if (isPresident && minNextBid > currentHighestBid + 10 && highestBiddingPlayer.value() != null) {
            return;
        }

        boolean canAfford = false;
        for (PublicCompany comp : gameManagerRef.getRoot().getCompanyManager().getAllPublicCompanies()) {
            if (comp.getPresident() != null && comp.getPresident().equals(activePlayer) && !comp.equals(target)) {
                if (comp.getCurrentSpace() != null && comp.getCurrentSpace().getPrice() > 30) {
                    canAfford = true;
                    break;
                }
            }
        }

        if (canAfford) {
            int actualMaxBid = isPresident ? currentHighestBid + 10 : 9990;
            possibleActions.add(new net.sf.rails.game.specific._1817.action.BidOnCompany_1817(
                    gameManagerRef.getRoot(), target.getId(), minNextBid, actualMaxBid));
        }
    }

    private void finalizeAuction() {
        PublicCompany target = operatingCompany.value();
        net.sf.rails.game.Player winner = highestBiddingPlayer.value();
        int finalBid = highestBid.value();

        if (winner == null) {
            executeSale(target, null, finalBid);
        } else {
            log.info("M&A ROUND: Auction won by " + winner.getName() + " for $" + finalBid
                    + ". Awaiting purchasing company selection.");
            currentStep.set(MaAStep.SALES_SELECT_BUYER);
            setPossibleActions();
        }
    }

    private void settleWithShareholders(PublicCompany target, int payoutPerShare) {
        log.info("M&A ROUND: Settling shareholders for {}. Payout: ${} per share.", target.getId(), payoutPerShare);

        for (Player p : gameManagerRef.getRoot().getPlayerManager().getPlayers()) {
            // Long positions receive cash
            int sharesOwned = countPlayerShares(target, p);
            if (sharesOwned > 0) {
                int totalPayout = sharesOwned * payoutPerShare;
                p.setCash_AI(p.getCash() + totalPayout);
                log.info("M&A ROUND: Paid {} ${} for shares of {}.", p.getName(), totalPayout, target.getId());
            }

            // Short positions pay cash (Rule 7.2.4 [cite: 847])
            // TODO: Settle short positions
            // 1. Iterate over p.getPortfolioModel().getCertificates() to find short shares
            // for 'target'.
            // 2. Calculate debt = shortShares * payoutPerShare.
            // 3. p.setCash_AI(p.getCash() - debt);
            // 4. Move short certificates back to the Bank.
        }
    }

}