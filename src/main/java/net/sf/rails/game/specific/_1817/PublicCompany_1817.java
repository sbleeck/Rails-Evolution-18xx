package net.sf.rails.game.specific._1817;

import net.sf.rails.common.parser.ConfigurationException;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.RailsItem;
import net.sf.rails.game.RailsRoot;
import net.sf.rails.game.financial.PublicCertificate;
import net.sf.rails.game.state.IntegerState;
import net.sf.rails.game.state.Owner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * 1817 specific Public Company logic.
 * Handles dynamic share units (50, 20, 10) and loan limits (2, 5, 10).
 * Manages certificate sequestering for 2-share starts via the Bank's Unavailable portfolio.
 */
public class PublicCompany_1817 extends PublicCompany {

    private static final Logger log = LoggerFactory.getLogger(PublicCompany_1817.class);


    protected final IntegerState tokenCapacity;
    protected final IntegerState shareCount;
protected final IntegerState bondsState;

    public PublicCompany_1817(RailsItem parent, String id) {
        super(parent, id);
this.shareCount = IntegerState.create(this, "shareCount", 2);
        this.bondsState = IntegerState.create(this, "bondsState", 0);
        this.tokenCapacity = IntegerState.create(this, "tokenCapacity", 8);
    }

    @Override
    public void finishConfiguration(RailsRoot root) throws ConfigurationException {
        super.finishConfiguration(root);
        // Initial certificate setup for all companies
        adjustCertificates();
            }

    @Override
    public int getNumberOfBonds() {
        return bondsState.value();
    }

    public void setNumberOfBonds(int bonds) {
        bondsState.set(bonds);
    }



    /**
     * Transfers cash from the Bank to the Company treasury.
     * Uses Currency state movement to ensure the Undo/Redo stack functions correctly.
     */
    public void addCashFromBank(int amount, net.sf.rails.game.financial.Bank bank) {
        net.sf.rails.game.state.Currency.fromBank(amount, this);
    }

    /**
     * Sequesters certificates not belonging to the current share count.
     * In 1817, a 2-share company only uses the President's certificate (100%).
     */
    public void adjustCertificates() {
        int count = shareCount.value();
        // President cert is always 2 units (shares). 
        // 2-share company = 2 units total (0 normal certs).
        // 5-share company = 5 units total (3 normal certs).
        // 10-share company = 10 units total (8 normal certs).
        int targetNormalCerts = count - 2; 

        List<PublicCertificate> allNormal = new ArrayList<>();
        
        // Collect all certificates that aren't the President's share
        for (PublicCertificate cert : getCertificates()) {
            if (!cert.isPresidentShare()) {
                allNormal.add(cert);
            }
        }

        int activeCount = 0;
        Owner unavailable = getRoot().getBank().getUnavailable();

        for (PublicCertificate cert : allNormal) {
            if (activeCount < targetNormalCerts) {
                // Move back to Treasury if it's currently sequestered.
                // The company itself is the Owner when a certificate is in its Treasury.
                if (cert.getOwner() != this) {
                    cert.moveTo(getPortfolioModel());
                }
                activeCount++;
            } else {
                // Sequester into the Bank's Unavailable portfolio.
                if (cert.getOwner() != unavailable) {
                    cert.moveTo(unavailable);
                }
            }
        }
    }

    @Override
    public int getShareUnit() {
        int count = shareCount.value();
        if (count == 0) return super.getShareUnit();
        return 100 / count;
    }

    @Override
    public int getMaxNumberOfLoans() {
        return shareCount.value();
    }

    public int getShareCount() {
        return shareCount.value();
    }

    public void setShareCount(int count) {
        if (count == 2 || count == 5 || count == 10) {
            this.shareCount.set(count);
            log.info("Company " + getId() + " set to " + count + "-share size.");
            adjustCertificates();

            // Set initial station marker capacity according to 1817 rules.
            // 2-share: 1 token, 5-share: 2 tokens, 10-share: 4 tokens.
            if (this.tokenCapacity.value() == 8) {
                int capacity = (count == 2) ? 1 : (count == 5) ? 2 : 4;
                this.tokenCapacity.set(capacity);
            }
        }
    }

    
    public void addTokenCapacity(int amount) {
        this.tokenCapacity.add(amount);
    }


    @Override
    public int getShareUnitsForSharePrice() {
        return 1; 
    }


@Override
    public java.util.Set<net.sf.rails.game.BaseToken> getAllBaseTokens() {
        java.util.Set<net.sf.rails.game.BaseToken> all = super.getAllBaseTokens();
        int limit = tokenCapacity.value();
        if (all.size() <= limit) {
            return all;
        }
        
        java.util.Set<net.sf.rails.game.BaseToken> restricted = new java.util.TreeSet<>();
        int count = 0;
        for (net.sf.rails.game.BaseToken t : all) {
            if (count < limit) {
                restricted.add(t);
            }
            count++;
        }
        return restricted;
    }

    @Override
    public int getNumberOfBaseTokens() {
        return getAllBaseTokens().size();
    }

    @Override
    public int getNumberOfFreeBaseTokens() {
        return Math.max(0, getNumberOfBaseTokens() - getNumberOfLaidBaseTokens());
    }

    @Override
    public net.sf.rails.game.BaseToken getNextBaseToken() {
        if (getNumberOfFreeBaseTokens() <= 0) {
            return null;
        }
        for (net.sf.rails.game.BaseToken t : getAllBaseTokens()) {
            if (!t.isPlaced()) {
                    return t;
            }
        }
        return null;
    }




}