package net.sf.rails.game.specific._1817.action;

import rails.game.action.PossibleAction;
import net.sf.rails.game.RailsRoot;

public class Bid1817IPO extends PossibleAction {
    
    private static final long serialVersionUID = 1L;
    private int bidAmount;

    public Bid1817IPO(RailsRoot root, int bidAmount) {
        super(root);
        this.bidAmount = bidAmount;
    }

    public int getBidAmount() {
        return bidAmount;
    }

    public void setBidAmount(int bidAmount) {
        this.bidAmount = bidAmount;
    }

    @Override
    public String toString() {
        return "Bid $" + bidAmount;
    }
}