package net.sf.rails.game.specific._1817;

import net.sf.rails.game.model.BondsModel;
import net.sf.rails.game.model.PortfolioModel;
import net.sf.rails.game.RailsOwner;
import net.sf.rails.game.RailsRoot;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.CompanyManager;

public class BondsModel_1817 extends BondsModel {

    private final RailsRoot root;

public BondsModel_1817(net.sf.rails.game.RailsItem parent, net.sf.rails.game.RailsItem owner, RailsRoot root) {
        super(parent, owner);
        this.root = root;
    }
    
    /**
     * Calculates the interest rate based on the total number of loans in play.
     * Rule 6.1.1: 0-9: $10, 10-19: $15, 20-29: $20, 30-39: $25, 40+: $30.
     */
    public int getInterestRate() {
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
        if (totalLoans >= 65) return 70;
        if (totalLoans >= 60) return 65;
        if (totalLoans >= 55) return 60;
        if (totalLoans >= 50) return 55;
        if (totalLoans >= 45) return 50;
        if (totalLoans >= 40) return 45;
        if (totalLoans >= 35) return 40;
        if (totalLoans >= 30) return 35;
        if (totalLoans >= 25) return 30;
        if (totalLoans >= 20) return 25;
        if (totalLoans >= 15) return 20;
        if (totalLoans >= 10) return 15;
        if (totalLoans >= 5) return 10;
        return 5;
    }
}