package net.sf.rails.game.specific._1870;

import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.RailsItem;

public class PublicCompany_1870 extends PublicCompany {

    public PublicCompany_1870(RailsItem parent, String id) {
        super(parent, id);
    }

    public PublicCompany_1870(RailsItem parent, String id, boolean hasStockPrice) {
        super(parent, id, hasStockPrice);
    }

    public String getDestinationHexId() {
        return this.destinationHexName;
    }

    public boolean hasConnected() {
        return this.hasReachedDestination();
    }

    public void setConnected(boolean connected) {
        this.setReachedDestination(connected);
    }
}