package net.sf.rails.game.specific._1817;

import net.sf.rails.game.model.BondsModel;
import net.sf.rails.game.model.PortfolioModel;
import net.sf.rails.game.RailsOwner;
import net.sf.rails.game.RailsRoot;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.CompanyManager;

public class BondsModel_1817 extends BondsModel {

    private final RailsRoot root;
    protected final net.sf.rails.game.state.IntegerState currentInterestRate;

    public BondsModel_1817(net.sf.rails.game.RailsItem parent, net.sf.rails.game.RailsItem owner, RailsRoot root) {
        super(parent, owner);
        this.root = root;
        currentInterestRate = net.sf.rails.game.state.IntegerState.create(this, "currentInterestRate", 5);
    }
    
    /**
     * Calculates and locks in the interest rate based on the total number of loans in play.
     */
    public void updateInterestRate() {
        int totalLoans = 0;
        CompanyManager cm = root.getCompanyManager();
        if (cm != null) {
            for (PublicCompany c : cm.getAllPublicCompanies()) {
                if (c != null && !c.isClosed()) {
                    totalLoans += c.getNumberOfBonds();
                }
            }
        }
        
        // Tiered logic
        int newRate = 5;
        if (totalLoans >= 65) newRate = 70;
        else if (totalLoans >= 60) newRate = 65;
        else if (totalLoans >= 55) newRate = 60;
        else if (totalLoans >= 50) newRate = 55;
        else if (totalLoans >= 45) newRate = 50;
        else if (totalLoans >= 40) newRate = 45;
        else if (totalLoans >= 35) newRate = 40;
        else if (totalLoans >= 30) newRate = 35;
        else if (totalLoans >= 25) newRate = 30;
        else if (totalLoans >= 20) newRate = 25;
        else if (totalLoans >= 15) newRate = 20;
        else if (totalLoans >= 10) newRate = 15;
        else if (totalLoans >= 5) newRate = 10;
        
        currentInterestRate.set(newRate);
    }

    public int getInterestRate() {
        return currentInterestRate.value();
    }
}