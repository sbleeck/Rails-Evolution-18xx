package net.sf.rails.game.specific._1817;

import java.util.List;
import java.util.ArrayList;
import net.sf.rails.game.GameManager;
import net.sf.rails.game.MapHex;
import net.sf.rails.game.Player;
import net.sf.rails.game.Round;
import net.sf.rails.game.state.ArrayListState;
import net.sf.rails.game.state.GenericState;
import net.sf.rails.game.state.IntegerState;
import net.sf.rails.game.state.Currency;
import net.sf.rails.game.state.BooleanState;
import net.sf.rails.game.specific._1817.action.Bid1817IPO;
import net.sf.rails.game.specific._1817.action.LayNYHomeToken;
import net.sf.rails.game.specific._1817.action.SettleIPO_1817;
import rails.game.action.PossibleAction;
import rails.game.action.LayBaseToken;
import rails.game.action.NullAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.sf.rails.game.Stop;

public class AuctionRound_1817 extends Round {

    private static final Logger log = LoggerFactory.getLogger(AuctionRound_1817.class);

    protected final GenericState<PublicCompany_1817> auctionedCompany;
    protected final GenericState<String> targetHexId;
    protected final IntegerState currentBid;
    protected final GenericState<Player> highestBidder;
    protected final GenericState<Player> initiator;
    protected final ArrayListState<Player> activeBidders;
    protected final IntegerState currentPlayerIndex;
    protected final IntegerState targetStationNumber;

    public AuctionRound_1817(GameManager parent, String id) {
        super(parent, id);
        this.auctionedCompany = new GenericState<>(this, "auctionedCompany_" + id);
        this.targetHexId = new GenericState<>(this, "targetHexId_" + id);
        this.currentBid = IntegerState.create(this, "currentBid_" + id, 0);
        this.highestBidder = new GenericState<>(this, "highestBidder_" + id);
        this.initiator = new GenericState<>(this, "initiator_" + id);
        this.activeBidders = new ArrayListState<>(this, "activeBidders_" + id);
        this.currentPlayerIndex = IntegerState.create(this, "currentPlayerIndex_" + id, 0);
        this.targetStationNumber = IntegerState.create(this, "targetStationNumber_" + id, -1);

    }

    public int getCurrentBid() {
        return currentBid.value();
    }

    public Player getHighestBidder() {
        return highestBidder.value();
    }

    public PublicCompany_1817 getAuctionedCompany() {
        return auctionedCompany.value();
    }

    public void setupAuction(PublicCompany_1817 company, String hexId, int stationNumber, int startingBid,
            Player startingPlayer,
            List<Player> allPlayers) {
        log.info("AUCTION_LOG: === NEW IPO AUCTION INITIATED ===");
        log.info("AUCTION_LOG: Company: {} | Target Hex: {} | Initiator: {} | Starting Bid: ${}",
                company.getId(), hexId, startingPlayer.getName(), startingBid);
        this.auctionedCompany.set(company);
        this.targetHexId.set(hexId);
        this.targetStationNumber.set(stationNumber);
        this.currentBid.set(startingBid);
        this.initiator.set(startingPlayer);
        this.highestBidder.set(startingPlayer);

        this.activeBidders.clear();
        for (Player p : allPlayers) {
            this.activeBidders.add(p);
        }

        int startIndex = (allPlayers.indexOf(startingPlayer) + 1) % allPlayers.size();
        this.currentPlayerIndex.set(startIndex);
        advanceToNextValidBidder();
        log.info("AUCTION_LOG: Active bidders post-prune: {}. Next to act: {}",
                this.activeBidders.size(),
                (this.activeBidders.isEmpty() ? "None" : getActingPlayer().getName()));

    }

    public Player getActingPlayer() {
        // 1. If the hex is not yet selected, the initiator must act
        if (activeBidders != null && activeBidders.size() <= 1) {
            return highestBidder.value();
        }
        if (activeBidders == null || activeBidders.isEmpty())
            return null;
        int index = currentPlayerIndex.value() % activeBidders.size();
        return activeBidders.get(index);
    }

    @Override
    public boolean setPossibleActions() {
        possibleActions.clear();
        if (activeBidders.size() <= 1) {
            resolveAuction();

            Player winner = highestBidder.value();

            if (winner != null && auctionedCompany.value() != null) {
                log.info("AUCTION_LOG: Generating Settlement Action for winner {} at ${}", winner.getName(),
                        currentBid.value());
                // Generate the settlement action for the UI to populate
                possibleActions.add(new SettleIPO_1817(
                        gameManager.getRoot(),
                        auctionedCompany.value().getId(),
                        new java.util.ArrayList<String>(),
                        currentBid.value(),
                        2));
            }
            return true;
        }

        // 2. Standard Bidding Phase
        Player currentPlayer = getActingPlayer();
        possibleActions.add(new NullAction(gameManager.getRoot(), NullAction.Mode.PASS));

        int minNextBid = currentBid.value() + 5;
        if (currentPlayer != null && currentPlayer.getCash() >= minNextBid) {
            possibleActions.add(new Bid1817IPO(gameManager.getRoot(), minNextBid));
        }
        return true;
    }

    @Override
    public boolean process(PossibleAction action) {
        Player actor = getActingPlayer();

        // --- Handle Settlement Action ---
        if (action instanceof SettleIPO_1817) {
            SettleIPO_1817 settle = (SettleIPO_1817) action;
            PublicCompany_1817 comp = auctionedCompany.value();
            Player winner = highestBidder.value();

            log.info("AUCTION_LOG: Settling IPO for {}. Size: {}-share. Cash: ${}. Privates: {}",
                    comp.getId(), settle.getShareSize(), settle.getCashAmount(), settle.getPrivateCompanyIds());

            // 1. Set Company Size (updates internal loan limits and certificates)
            comp.setShareCount(settle.getShareSize());
            int cashToTransfer = currentBid.value();

            // 3. Transfer Private Companies from Winner to Company
            for (String pId : settle.getPrivateCompanyIds()) {
                net.sf.rails.game.PrivateCompany pc = gameManager.getRoot().getCompanyManager().getPrivateCompany(pId);
                if (pc != null) {
                    pc.moveTo(comp.getPortfolioModel());
                    cashToTransfer -= pc.getBasePrice();
                }
            }

            // 2. Transfer Cash from Winner to Company Treasury
            if (cashToTransfer > 0) {
                net.sf.rails.game.state.Currency.wire(winner, cashToTransfer, comp);
            }

            // 4. Issue the President's Certificate and set Presidency
            net.sf.rails.game.financial.PublicCertificate presCert = comp.getPresidentsShare();
            if (presCert != null) {
                presCert.moveTo(winner.getPortfolioModel());
            }
            comp.setPresident(winner);

            // 5. Calculate Par Price (Bid / ShareSize) - Integer division naturally rounds
            // down
            int parPrice = currentBid.value() / settle.getShareSize();
            net.sf.rails.game.financial.StockMarket sm = gameManager.getRoot().getStockMarket();
            net.sf.rails.game.financial.StockSpace startSpace = sm.getStartSpace(parPrice);

            if (startSpace == null) {
                // Robust Fallback: Search for the highest price P such that P <= parPrice
                int bestPriceFound = -1;
                for (int r = 0; r < sm.getNumberOfRows(); r++) {
                    for (int c = 0; c < sm.getNumberOfColumns(); c++) {
                        net.sf.rails.game.financial.StockSpace ss = sm.getStockSpace(r, c);
                        if (ss != null) {
                            int spacePrice = ss.getPrice();
                            if (spacePrice <= parPrice && spacePrice > bestPriceFound) {
                                startSpace = ss;
                                bestPriceFound = spacePrice;
                            }
                        }
                    }
                }

                if (startSpace != null) {
                    log.info("AUCTION_LOG: Exact price ${} not found. Starting at {} (${})",
                            parPrice, startSpace.getId(), startSpace.getPrice());
                }
            }

            if (startSpace != null) {
                comp.start(startSpace);
            } else {
                log.error("AUCTION_LOG: No StockSpace found for price ${}", parPrice);
            }

            // 6. Float the company BEFORE laying the token so the engine accepts it
            comp.setFloated();

            // 7. Lay the base token on the map using the specific station number passed
            // from the Stock Round
            net.sf.rails.game.MapHex homeHex = getRoot().getMapManager().getHex(targetHexId.value());
            if (homeHex != null) {
                comp.setHomeHex(homeHex);
                net.sf.rails.game.Stop targetStop = null;

                if (homeHex.getStops() != null) {
                    for (net.sf.rails.game.Stop stop : homeHex.getStops()) {
                        if (stop.getRelatedStationNumber() == targetStationNumber.value()) {
                            targetStop = stop;
                            break;
                        }
                    }
                }

                // Fallback for non-E22 hexes where stationNumber defaults to 0 but might not
                // perfectly match
                if (targetStop == null && homeHex.getStops() != null && !homeHex.getStops().isEmpty()) {
                    for (net.sf.rails.game.Stop stop : homeHex.getStops()) {
                        if (stop.hasTokenSlotsLeft()) {
                            targetStop = stop;
                            break;
                        }
                    }
                }

                if (targetStop != null) {
                    comp.setHomeCityNumber(targetStop.getRelatedStationNumber());
                    homeHex.layBaseToken(comp, targetStop);
                }
            }

            // 8. Close the Auction Round
            gameManager.nextRound(this);
            return true;

        }

        // --- Handle Bidding Actions ---
        if (action instanceof NullAction && ((NullAction) action).getMode() == NullAction.Mode.PASS) {
            log.info("AUCTION_LOG: Player {} passed.", actor.getName());
            int index = activeBidders.indexOf(actor);
            activeBidders.remove(actor);
            if (!activeBidders.isEmpty()) {
                currentPlayerIndex.set(index % activeBidders.size());
            }
            advanceToNextValidBidder();

            return true;
        }

        if (action instanceof Bid1817IPO) {
            int amount = ((Bid1817IPO) action).getBidAmount();
            log.info("AUCTION_LOG: Player {} BID ${}.", actor.getName(), amount);
            this.currentBid.set(amount);
            this.highestBidder.set(actor);

            int index = activeBidders.indexOf(actor);
            this.currentPlayerIndex.set((index + 1) % activeBidders.size());
            advanceToNextValidBidder();
            return true;
        }

        return super.process(action);
    }

    private void resolveAuction() {
        Player winner = highestBidder.value();

        log.info("AUCTION_LOG: Auction finished. Waiting for settlement from {}",
                (winner != null ? winner.getName() : "None"));
    }

    private void advanceToNextValidBidder() {
        int minNextBid = currentBid.value() + 5;

        while (activeBidders.size() > 1) {
            int index = currentPlayerIndex.value() % activeBidders.size();
            Player candidate = activeBidders.get(index);

            if (candidate.getCash() >= minNextBid) {
                return;
            }

            log.info("AUCTION_LOG: Auto-skipping {} (insufficient funds: has ${}, needs ${}).",
                    candidate.getName(), candidate.getCash(), minNextBid);
            activeBidders.remove(index);

            if (!activeBidders.isEmpty()) {
                currentPlayerIndex.set(index % activeBidders.size());
            }
        }
    }

}