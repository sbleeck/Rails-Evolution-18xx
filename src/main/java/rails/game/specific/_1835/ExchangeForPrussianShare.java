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
 * A discrete action for exchanging one specific company (Minor or Private)
 * for a share in the Prussian (PR) company.
 */
public class ExchangeForPrussianShare extends PossibleAction {

    private transient Company companyToExchange;
    private String companyToExchangeName;

    public static final long serialVersionUID = 1L;

    public ExchangeForPrussianShare(Company companyToExchange) {
        super(companyToExchange.getRoot());
        this.companyToExchange = companyToExchange;
        this.companyToExchangeName = companyToExchange.getId();
    }

    public Company getCompanyToExchange() {
        return companyToExchange;
    }

    @Override
    protected boolean equalsAs(PossibleAction pa, boolean asOption) {
        if (pa == this) return true;
        if (!super.equalsAs(pa, asOption)) return false;

        ExchangeForPrussianShare action = (ExchangeForPrussianShare) pa;
        // As an option, only the company being exchanged matters
        return Objects.equal(this.companyToExchangeName, action.companyToExchangeName);
    }

    @Override
    public String toString() {
        return super.toString() +
                RailsObjects.stringHelper(this)
                        .addToString("companyToExchange", companyToExchangeName)
                        .toString();
    }

    /** Deserialize */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        CompanyManager cmgr = getCompanyManager();
        companyToExchange = cmgr.getPublicCompany(companyToExchangeName);
        if (companyToExchange == null) {
            companyToExchange = cmgr.getPrivateCompany(companyToExchangeName);
        }
    }
}