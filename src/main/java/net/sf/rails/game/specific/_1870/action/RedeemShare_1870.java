package net.sf.rails.game.specific._1870.action;

import rails.game.action.PossibleAction;
import net.sf.rails.game.PublicCompany;

public class RedeemShare_1870 extends PossibleAction {
    private static final long serialVersionUID = 1L;
    private final PublicCompany company;

    public RedeemShare_1870(PublicCompany company) {
        super(company.getRoot());
        this.company = company;
        setButtonLabel("Redeem " + company.getId());
    }

    public PublicCompany getCompany() {
        return company;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RedeemShare_1870 other = (RedeemShare_1870) obj;
        return company.equals(other.company);
    }

    @Override
    public int hashCode() {
        return company.hashCode();
    }
}