package net.sf.rails.game.specific._1817;

import java.util.List;
import net.sf.rails.game.GameManager;
import net.sf.rails.game.Player;
import net.sf.rails.game.Round;
import net.sf.rails.game.state.ArrayListState;
import net.sf.rails.game.state.GenericState;
import net.sf.rails.game.state.IntegerState;
import net.sf.rails.game.state.Currency;
import net.sf.rails.game.specific._1817.action.Bid1817IPO;
import rails.game.action.PossibleAction;
import rails.game.action.NullAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuctionRound_1817 extends Round {

    private static final Logger log = LoggerFactory.getLogger(AuctionRound_1817.class);

    protected final GenericState<PublicCompany_1817> auctionedCompany;
    protected final GenericState<String> targetHexId;
    protected final IntegerState currentBid;
    protected final GenericState<Player> highestBidder;
    protected final GenericState<Player> initiator;
    protected final ArrayListState<Player> activeBidders;
    protected final IntegerState currentPlayerIndex;

    public int getCurrentBid() {
        return currentBid.value();
    }

    public Player getHighestBidder() {
        return highestBidder.value();
    }

    public PublicCompany_1817 getAuctionedCompany() {
        return auctionedCompany.value();
    }


    public AuctionRound_1817(GameManager parent, String id) {
        super(parent, id);
        this.auctionedCompany = new GenericState<>(this, "auctionedCompany_" + id);
        this.targetHexId = new GenericState<>(this, "targetHexId_" + id);
        this.currentBid = IntegerState.create(this, "currentBid_" + id, 0);
        this.highestBidder = new GenericState<>(this, "highestBidder_" + id);
        this.initiator = new GenericState<>(this, "initiator_" + id);
        this.activeBidders = new ArrayListState<>(this, "activeBidders_" + id);
        this.currentPlayerIndex = IntegerState.create(this, "currentPlayerIndex_" + id, 0);
    }

    public void setupAuction(PublicCompany_1817 company, String hexId, int startingBid, Player startingPlayer, List<Player> allPlayers) {
        log.info("AUCTION_LOG: Setting up auction for {} at Hex {}. Starting Bid: ${}", company.getId(), hexId, startingBid);
        this.auctionedCompany.set(company);
        this.targetHexId.set(hexId);
        this.currentBid.set(startingBid);
        this.initiator.set(startingPlayer);
        this.highestBidder.set(startingPlayer);
        
        this.activeBidders.clear();
        for (Player p : allPlayers) {
            this.activeBidders.add(p);
        }
        
        int startIndex = (allPlayers.indexOf(startingPlayer) + 1) % allPlayers.size();
        this.currentPlayerIndex.set(startIndex);
        log.info("AUCTION_LOG: First acting bidder will be: {}", getActingPlayer().getName());
    }


    public Player getActingPlayer() {
        if (activeBidders == null || activeBidders.isEmpty()) return null;
        int index = currentPlayerIndex.value() % activeBidders.size();
        return activeBidders.get(index);
    }

    @Override
    public boolean setPossibleActions() {
        possibleActions.clear();

        // If resolution already happened or only 1 bidder left, don't generate more actions
        if (activeBidders.size() <= 1) {
            log.info("AUCTION_LOG: Resolution condition met (Bidders: {}).", activeBidders.size());
            resolveAuction();
            return true;
        }

        Player currentPlayer = getActingPlayer();
        
        if (activeBidders.size() <= 1) {
            log.info("AUCTION_LOG: Only one bidder remains. Resolving...");
            resolveAuction();
            return true;
        }

        log.info("AUCTION_LOG: Generating actions for player: {}", (currentPlayer != null ? currentPlayer.getName() : "NULL"));
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
        if (action instanceof NullAction && ((NullAction) action).getMode() == NullAction.Mode.PASS) {
            log.info("AUCTION_LOG: Player {} FOLDED.", actor.getName());

activeBidders.remove(actor);
            
            // After removing the passer, if only one is left, the next UI cycle 
            // calls setPossibleActions() which will trigger resolveAuction().
            // We do NOT increment currentPlayerIndex here because the list size changed.
            return true;
            

        }

        if (action instanceof Bid1817IPO) {
            int amount = ((Bid1817IPO) action).getBidAmount();
            log.info("AUCTION_LOG: Player {} BID ${}.", actor.getName(), amount);
            this.currentBid.set(amount);
            this.highestBidder.set(actor);
            this.currentPlayerIndex.set((currentPlayerIndex.value() + 1) % activeBidders.size());
            return true;
        }
        return super.process(action);
    }

    private void resolveAuction() {
        Player winner = highestBidder.value();
        PublicCompany_1817 comp = auctionedCompany.value();
        int finalBid = currentBid.value();


        // 1817 Rule: Par is exactly half the final bid (round up)
        int parPrice = (finalBid + 1) / 2;

        log.info("AUCTION_LOG: RESOLUTION -> Winner: {}, Bid: ${}, Calculated Par: ${}", winner.getName(), finalBid, parPrice);

        comp.setShareCount(2);
        
        net.sf.rails.game.financial.StockMarket stockMarket = getRoot().getStockMarket();


        // 2. Hardwired snap to valid 1817 StartSpace prices from StockMarket.xml
        int snapPrice;
        if (parPrice >= 200) snapPrice = 200;
        else if (parPrice >= 180) snapPrice = 180;
        else if (parPrice >= 165) snapPrice = 165;
        else if (parPrice >= 150) snapPrice = 150;
        else if (parPrice >= 135) snapPrice = 135;
        else if (parPrice >= 120) snapPrice = 120;
        else if (parPrice >= 110) snapPrice = 110;
        else if (parPrice >= 100) snapPrice = 100;
        else if (parPrice >= 90) snapPrice = 90;
        else if (parPrice >= 80) snapPrice = 80;
        else if (parPrice >= 70) snapPrice = 70;
        else if (parPrice >= 65) snapPrice = 65;
        else if (parPrice >= 60) snapPrice = 60;
        else if (parPrice >= 55) snapPrice = 55;
        else snapPrice = 50;

        // Use getStartSpace(int) to find the space associated with the snapped price
        net.sf.rails.game.financial.StockSpace startSpace = stockMarket.getStartSpace(snapPrice);

        if (startSpace != null) {
            log.info("AUCTION_LOG: Snapping to valid StartSpace at ${}", snapPrice);
            comp.start(startSpace);
        } else {
            log.error("AUCTION_LOG: FATAL - No StartSpace configured for price ${}", snapPrice);
        }

        Currency.wire(winner, finalBid, getRoot().getBank());
        Currency.wire(getRoot().getBank(), finalBid, comp);

        if (comp.getPresidentsShare() != null) {
            comp.getPresidentsShare().moveTo(winner.getPortfolioModel());
        }
        
        net.sf.rails.game.MapHex homeHex = getRoot().getMapManager().getHex(targetHexId.value());


        if (homeHex != null) {
            comp.setHomeHex(homeHex);
            
            // 1817 Rule: Tokens are placed immediately upon floating, not delayed to the OR.
            net.sf.rails.game.Stop targetStop = null;
            if (homeHex.getStops() != null) {
                for (net.sf.rails.game.Stop stop : homeHex.getStops()) {
                    if (stop.hasTokenSlotsLeft()) {
                        targetStop = stop;
                        break; // Grab the first open station slot
                    }
                }
            }
            
            if (targetStop != null) {
                comp.setHomeCityNumber(targetStop.getRelatedStationNumber());
                boolean tokenLaid = homeHex.layBaseToken(comp, targetStop);
                if (tokenLaid) {
                    log.info("AUCTION_LOG: Laid base token for {} on Hex {} (Station {})", 
                             comp.getId(), homeHex.getId(), targetStop.getRelatedStationNumber());
                } else {
                    log.error("AUCTION_LOG: Failed to lay base token for {} on Hex {}", comp.getId(), homeHex.getId());
                }
            } else {
                log.warn("AUCTION_LOG: Could not find free stop on Hex {} for token placement", homeHex.getId());
            }

        }
        comp.setFloated();


        log.info("AUCTION_LOG: Auction finished. Returning to Stock Round.");
        gameManager.finishShareSellingRound(true);
    }
}