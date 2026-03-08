package net.sf.rails.game.specific._1817;

import net.sf.rails.game.GameManager;
import net.sf.rails.game.Player;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.Round;
import rails.game.action.PossibleAction;

public class AuctionRound_1817 extends Round {

    private final PublicCompany auctionCompany;
    private final String startingHexId;
    private int currentBid;
    private Player currentHighBidder;
    private final Player initiatingPlayer;

    public AuctionRound_1817(GameManager gameManager, PublicCompany company, String startingHexId, int startingBid,
            Player initiator) {

        super(gameManager, "AuctionRound_1817_" + company.getId());
        this.auctionCompany = company;
        this.startingHexId = startingHexId;
        this.currentBid = startingBid;

        this.initiatingPlayer = initiator;
        this.currentHighBidder = initiator;
    }

    @Override
    public boolean setPossibleActions() {
        // Auction state machine generation.
        // Needs logic to iterate over players in seat order, generating Pass or Raise
        // actions.
        return true;
    }

    @Override
    public boolean process(PossibleAction action) {
        // Handle incoming Raise/Pass actions.
        // Trigger company capitalization math (Price = Bid/2) upon all other players
        // passing.
        return false;
    }

    public String getHelp() {
        return "1817 IPO Auction for " + auctionCompany.getId() + ".";
    }

    @Override
    public String toString() {
        return "1817 Auction: " + auctionCompany.getId();
    }
}