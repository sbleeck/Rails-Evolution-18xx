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

/**
 * 1817 IPO Auction State Machine.
 * Intercepts the Stock Round to resolve company presidency and par price.
 */
public class AuctionRound_1817 extends Round {

    private static final Logger log = LoggerFactory.getLogger(AuctionRound_1817.class);

    protected final GenericState<PublicCompany_1817> auctionedCompany;
    protected final GenericState<String> targetHexId;
    protected final IntegerState currentBid;
    protected final GenericState<Player> highestBidder;
    protected final GenericState<Player> initiator;
    protected final ArrayListState<Player> activeBidders;
    protected final IntegerState currentPlayerIndex;

    public AuctionRound_1817(GameManager parent, String id) {
        super(parent, id);
        // Initialize states using standard constructors as seen in StockRound
        this.auctionedCompany = new GenericState<PublicCompany_1817>(this, "auctionedCompany_" + id);
        this.targetHexId = new GenericState<String>(this, "targetHexId_" + id);
        this.currentBid = IntegerState.create(this, "currentBid_" + id, 0);
        this.highestBidder = new GenericState<Player>(this, "highestBidder_" + id);
        this.initiator = new GenericState<Player>(this, "initiator_" + id);
        this.activeBidders = new ArrayListState<Player>(this, "activeBidders_" + id);
        this.currentPlayerIndex = IntegerState.create(this, "currentPlayerIndex_" + id, 0);
    }

    /**
     * Prepares the auction with parameters from the Stock Round initiation.
     */
    public void setupAuction(PublicCompany_1817 company, String hexId, int startingBid, Player startingPlayer, List<Player> allPlayers) {
        this.auctionedCompany.set(company);
        this.targetHexId.set(hexId);
        this.currentBid.set(startingBid);
        this.initiator.set(startingPlayer);
        this.highestBidder.set(startingPlayer);
        
        this.activeBidders.clear();
        for (Player p : allPlayers) {
            this.activeBidders.add(p);
        }
        
        // Start turn clockwise from the initiator
        int startIndex = (allPlayers.indexOf(startingPlayer) + 1) % allPlayers.size();
        this.currentPlayerIndex.set(startIndex);
    }

    public Player getActingPlayer() {
        if (activeBidders.isEmpty()) return null;
        int index = currentPlayerIndex.value() % activeBidders.size();
        return activeBidders.get(index);
    }

    @Override
    public boolean setPossibleActions() {
        // Inherited from Round base class
        possibleActions.clear();
        
        Player currentPlayer = getActingPlayer();
        if (currentPlayer == null) return false;

        // If only one remains, the auction is over
        if (activeBidders.size() <= 1) {
            resolveAuction();
            return true;
        }

        // Generate Pass/Bid actions for the StatusWindow to pick up
        possibleActions.add(new NullAction(gameManager.getRoot(), NullAction.Mode.PASS));
        
        int minNextBid = currentBid.value() + 5;
        if (currentPlayer.getCash() >= minNextBid) {
            possibleActions.add(new Bid1817IPO(gameManager.getRoot(), minNextBid));
        }
        
        return true;
    }

    @Override
    public boolean process(PossibleAction action) {
        if (action instanceof NullAction && ((NullAction) action).getMode() == NullAction.Mode.PASS) {
            Player passingPlayer = getActingPlayer();
            log.info("Player {} passes the auction.", passingPlayer.getName());
            activeBidders.remove(passingPlayer);
            // Turn advances naturally to the next player in the reduced list
            return true;
        }

        if (action instanceof Bid1817IPO) {
            Bid1817IPO bidAction = (Bid1817IPO) action;
            this.currentBid.set(bidAction.getBidAmount());
            this.highestBidder.set(getActingPlayer());
            
            // Turn advances clockwise
            int nextIndex = (currentPlayerIndex.value() + 1) % activeBidders.size();
            this.currentPlayerIndex.set(nextIndex);
            return true;
        }
        
        return super.process(action);
    }

    private void resolveAuction() {
        Player winner = highestBidder.value();
        PublicCompany_1817 comp = auctionedCompany.value();
        int finalBid = currentBid.value();
        int parPrice = finalBid / 2;

        log.info("Auction resolved. Winner: {} at ${}", winner.getName(), finalBid);

        // 1. Set Company State (2-share initialization)
        comp.setShareCount(2);
        net.sf.rails.game.financial.StockSpace startSpace = getRoot().getStockMarket().getStartSpace(parPrice);
        comp.start(startSpace);

        // 2. Financial Transfers
        Currency.wire(winner, finalBid, getRoot().getBank());
        Currency.wire(getRoot().getBank(), finalBid, comp);

        // 3. Ownership and Map placement
        comp.getPresidentsShare().moveTo(winner.getPortfolioModel());
        
        net.sf.rails.game.MapHex homeHex = getRoot().getMapManager().getHex(targetHexId.value());
        if (homeHex != null) comp.setHomeHex(homeHex);
        comp.setFloated();

        // 4. Cleanup and return to Stock Round
        gameManager.finishShareSellingRound(true); 
    }
}