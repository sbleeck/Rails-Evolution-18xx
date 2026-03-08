package net.sf.rails.game.specific._1817;

import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.RailsItem;
import net.sf.rails.game.state.IntegerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 1817 specific Public Company logic.
 * Handles dynamic share units (50, 20, 10) and loan limits (2, 5, 10).
 */
public class PublicCompany_1817 extends PublicCompany {

    private static final Logger log = LoggerFactory.getLogger(PublicCompany_1817.class);

    // Persist the number of shares (2, 5, or 10)
    protected final IntegerState shareCount;

    // --- START FIX ---
    public PublicCompany_1817(RailsItem parent, String id) {
        super(parent, id);
        this.shareCount = IntegerState.create(this, "shareCount_" + id, 2);
    }
    // --- END FIX ---

    /**
     * In 1817, the share unit is 100 / number of shares.
     * 2-share = 50%, 5-share = 20%, 10-share = 10%.
     */
    @Override
    public int getShareUnit() {
        int count = shareCount.value();
        if (count == 0) return super.getShareUnit();
        return 100 / count;
    }

    // --- START FIX ---
    /**
     * 1817 Loan Limit is equal to the share size.
     * Overriding the correct base method name.
     */
    @Override
    public int getMaxNumberOfLoans() {
        return shareCount.value();
    }
    // --- END FIX ---

    public int getShareCount() {
        return shareCount.value();
    }

    public void setShareCount(int count) {
        if (count == 2 || count == 5 || count == 10) {
            this.shareCount.set(count);
            log.info("Company " + getId() + " set to " + count + "-share size.");
        }
    }

    @Override
    public int getShareUnitsForSharePrice() {
        return 1; 
    }
}