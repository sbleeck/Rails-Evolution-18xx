package net.sf.rails.game.specific._1817.action;

import net.sf.rails.game.RailsRoot;
import rails.game.action.SpecialORAction;
import rails.game.action.PossibleORAction;

public class TakeLoans_1817 extends PossibleORAction implements SpecialORAction, LoanAction {
    private static final long serialVersionUID = 1L;
    private final String companyId;
    private final int maxLoans;
    private int loansToTake;

    public TakeLoans_1817(RailsRoot root, String companyId, int maxLoans) {
        super(root);
        this.companyId = companyId;
        this.maxLoans = maxLoans;
    }

    public int getLoansToTake() { 
        return loansToTake;
    }

    @Override public String getCompanyId() { return companyId; }
    @Override public int getMaxLoansAllowed() { return maxLoans; }
    @Override public void setLoansToTake(int count) { this.loansToTake = count; }

    @Override
    public String getButtonLabel() {
        return "Take Loans (" + companyId + ")";
    }
}