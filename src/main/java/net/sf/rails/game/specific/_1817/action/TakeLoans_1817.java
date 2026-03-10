package net.sf.rails.game.specific._1817.action;

import rails.game.action.PossibleAction;
import net.sf.rails.game.PublicCompany;

public class TakeLoans_1817 extends PossibleAction {
    private static final long serialVersionUID = 1L;

    private final String companyId;
    private final int maxLoansAllowed;
    private int loansToTake = 0;

    public TakeLoans_1817(String companyId, int maxLoansAllowed) {
        // Correcting to RailsRoot as per your environment's requirement
        super((net.sf.rails.game.RailsRoot) null);
        this.companyId = companyId;
        this.maxLoansAllowed = maxLoansAllowed;
    }

    public String getCompanyId() {
        return companyId;
    }

    public int getMaxLoansAllowed() {
        return maxLoansAllowed;
    }

    public void setLoansToTake(int loansToTake) {
        this.loansToTake = loansToTake;
    }

    public int getLoansToTake() {
        return loansToTake;
    }

    @Override
    public String toString() {
        return "Take Loans (" + (companyId != null ? companyId : "Unknown") + ")";
    }
}