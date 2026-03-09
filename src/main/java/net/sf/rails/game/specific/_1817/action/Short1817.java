package net.sf.rails.game.specific._1817.action;

import rails.game.action.PossibleAction;
import net.sf.rails.game.RailsRoot;
import net.sf.rails.game.PublicCompany;

public class Short1817 extends PossibleAction {
    private final String companyId;

    public Short1817(RailsRoot root, String companyId) {
        super(root);
        this.companyId = companyId;
    }

    public String getCompanyId() { return companyId; }

    @Override
    public String toString() {
        return "Short sell 1 share of " + companyId;
    }
}