package rails.game.specific._1835; // Or the correct package for your actions

import java.io.IOException;
import java.io.ObjectInputStream;
import net.sf.rails.game.Company;
import net.sf.rails.game.CompanyManager;
import net.sf.rails.game.RailsRoot;
import net.sf.rails.util.RailsObjects;
import com.google.common.base.Objects;
import rails.game.action.PossibleAction;

/**
 * A discrete action for the initial start of the Prussian (PR) company.
 * This action is created for a specific company (e.g., M2) that will
 * be folded to start PR.
 */
public class StartPrussian extends PossibleAction {

    private transient Company companyToFold;
    private String companyToFoldName;

    public static final long serialVersionUID = 1L;

    public StartPrussian(Company companyToFold) {
        super(companyToFold.getRoot());
        this.companyToFold = companyToFold;
        this.companyToFoldName = companyToFold.getId();
    }

    public Company getCompanyToFold() {
        return companyToFold;
    }

    @Override
    protected boolean equalsAs(PossibleAction pa, boolean asOption) {
        if (pa == this) return true;
        if (!super.equalsAs(pa, asOption)) return false;

        StartPrussian action = (StartPrussian) pa;
        // As an option, only the company being folded matters
        return Objects.equal(this.companyToFoldName, action.companyToFoldName);
    }

    @Override
    public String toString() {
        return super.toString() +
                RailsObjects.stringHelper(this)
                        .addToString("companyToFold", companyToFoldName)
                        .toString();
    }

    /** Deserialize */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        CompanyManager cmgr = getCompanyManager();
        companyToFold = cmgr.getPublicCompany(companyToFoldName);
        if (companyToFold == null) {
            companyToFold = cmgr.getPrivateCompany(companyToFoldName);
        }
    }
}