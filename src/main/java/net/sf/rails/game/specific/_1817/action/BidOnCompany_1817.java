package net.sf.rails.game.specific._1817.action;

import rails.game.action.PossibleAction;

public class BidOnCompany_1817 extends PossibleAction {
    private static final long serialVersionUID = 1L;
    private final String companyId;
    private final int minBid;
    private final int maxBid;
    private int bidAmount;

public BidOnCompany_1817(net.sf.rails.game.RailsRoot root, String companyId, int minBid, int maxBid) {
        super(root);
        this.companyId = companyId;
        this.minBid = minBid;
        this.maxBid = maxBid;
        this.bidAmount = minBid;
    }

    public String getCompanyId() { return companyId; }
    public int getMinBid() { return minBid; }
    public int getMaxBid() { return maxBid; }
    public int getBidAmount() { return bidAmount; }
    public void setBidAmount(int amount) { this.bidAmount = amount; }
    
    @Override
public String toString() {
            return "Bid on " + companyId;
    }
}