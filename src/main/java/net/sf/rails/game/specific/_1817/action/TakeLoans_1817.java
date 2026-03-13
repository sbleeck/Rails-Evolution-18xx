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
    protected boolean equalsAs(PossibleAction pa, boolean asOption) {
        if (pa == this) return true;
        
        // CRITICAL FIX: Bypass super.equalsAs() completely. 
        // Because super((RailsRoot) null) is called in the constructor, the base 
        // PossibleAction fails to resolve the 'actor' object upon deserialization, 
        // causing false negatives during reload validation.
        if (pa == null || this.getClass() != pa.getClass()) return false;

        TakeLoans_1817 other = (TakeLoans_1817) pa;
        
        boolean options = false;
        if (this.companyId != null) {
            options = this.companyId.equals(other.companyId);
        } else {
            options = (other.companyId == null);
        }
        options = options && (this.maxLoansAllowed == other.maxLoansAllowed);

        if (asOption) return options;

        return options && (this.loansToTake == other.loansToTake);
    }

    @Override
    public String toString() {
        return "Take Loans (" + (companyId != null ? companyId : "Unknown") + ")";
    }
}