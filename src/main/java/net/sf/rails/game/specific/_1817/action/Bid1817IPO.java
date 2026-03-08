package net.sf.rails.game.specific._1817.action;

import net.sf.rails.game.RailsRoot;
import rails.game.action.PossibleAction;

/**
 * Action representing a bid in the 1817 IPO auction.
 * This class is serialized and sent from the UI to the Game Engine.
 */
public class Bid1817IPO extends PossibleAction {

    private static final long serialVersionUID = 1L;
    
    // The amount the player is bidding
    private final int bidAmount;

    /**
     * Constructor for the bidding action.
     * @param root The RailsRoot reference required by all PossibleActions.
     * @param bidAmount The integer value of the bid.
     */
    public Bid1817IPO(RailsRoot root, int bidAmount) {
        super(root);
        this.bidAmount = bidAmount;
    }

    /**
     * @return The amount bid by the player.
     */
    public int getBidAmount() {
        return bidAmount;
    }

    /**
     * Overriding toString provides the label for the button in the UI 
     * if we use the standard ButtonPanel.
     */
    @Override
    public String toString() {
        return "Bid $" + bidAmount;
    }

    /**
     * Standard Rails equality check for actions.
     */
    @Override
    public boolean equalsAs(PossibleAction pa, boolean asOption) {
        if (!super.equalsAs(pa, asOption)) return false;
        if (!(pa instanceof Bid1817IPO)) return false;
        
        Bid1817IPO other = (Bid1817IPO) pa;
        return this.bidAmount == other.bidAmount;
    }
}