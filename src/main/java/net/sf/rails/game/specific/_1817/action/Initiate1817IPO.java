package net.sf.rails.game.specific._1817.action;

import rails.game.action.PossibleAction;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.RailsRoot;

/**
 * Action generated when a player initiates an IPO in 1817.
 * Encapsulates the company, the hex ID for the initial station marker,
 * and the opening bid.
 */
public class Initiate1817IPO extends PossibleAction {

    private static final long serialVersionUID = 1L;
    
    private final PublicCompany company;
    private String hexId;
    private int bid;

    public Initiate1817IPO(RailsRoot root, PublicCompany company) {
        super(root);
        this.company = company;
    }

    public PublicCompany getCompany() {
        return company;
    }

    public String getHexId() {
        return hexId;
    }

    public void setHexId(String hexId) {
        this.hexId = hexId;
    }

    public int getBid() {
        return bid;
    }

    public void setBid(int bid) {
        this.bid = bid;
    }

    @Override
    public String toString() {
        return "Initiate IPO for " + (company != null ? company.getId() : "Unknown") 
                + " at " + hexId + " with bid $" + bid;
    }
}