package net.sf.rails.game.specific._1817;

import net.sf.rails.game.OperatingRound;
import net.sf.rails.game.GameManager;
import net.sf.rails.game.Company;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.MapHex;
import net.sf.rails.game.special.SpecialBaseTokenLay;
import net.sf.rails.game.special.SpecialProperty;
import net.sf.rails.game.special.SpecialTileLay;

// Corrected Action Imports
import rails.game.action.LayTile;
import rails.game.action.PossibleAction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.rails.game.StartRound;
import net.sf.rails.game.StartItem;
import net.sf.rails.game.GameManager;
import net.sf.rails.game.Player;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.RailsItem;
import net.sf.rails.game.financial.Bank;
import net.sf.rails.common.LocalText;
import net.sf.rails.common.DisplayBuffer;
import net.sf.rails.common.ReportBuffer;
import net.sf.rails.common.GameOption;

import rails.game.action.PossibleAction;
import rails.game.action.BuyStartItem;
import rails.game.action.StartItemAction;
import rails.game.action.NullAction;

import java.util.Collection;
import java.util.List;

/**
 * Operating Round specifically for 1830.
 * Handles the "Use it or Lose it" rule for the Delaware & Hudson private
 * company.
 */
public class OperatingRound_1817 extends OperatingRound {

    private static final Logger log = LoggerFactory.getLogger(OperatingRound_1817.class);

    // State to track the loan blackout period (between paying interest and repaying
    // loans)
    protected final net.sf.rails.game.state.BooleanState loanBlackoutPeriod = new net.sf.rails.game.state.BooleanState(
            this, "loanBlackoutPeriod", false);

    public OperatingRound_1817(GameManager gameManager, String roundId) {
        super(gameManager, roundId);
    }

    @Override
    protected void initTurn() {
        super.initTurn();
        // Reset the flag at the start of every company's turn
        loanBlackoutPeriod.set(false);
    }

    @Override
    public boolean process(PossibleAction action) {
        // Intercept the processing to detect if the D&H tile is being laid
        if (action instanceof LayTile) {
            LayTile layTile = (LayTile) action;
            SpecialProperty sp = layTile.getSpecialProperty();

            if (sp != null
                    && sp instanceof SpecialTileLay
                    && sp.getOriginalCompany() != null) {

                Company originalComp = sp.getOriginalCompany();

                // Use getId() because getName() is not guaranteed on the Company interface
                String compName = originalComp.getId();

            }

        }

        // Proceed with normal processing
        return super.process(action);
    }

    @Override
    protected void finishTurn() {
        // This method is called when the player clicks "Done" or the turn is forced to
        // end.

        super.finishTurn();
    }

    /**
     * Helper to check if the current company has a token on the specified hex name.
     */
    private boolean hasTokenOnHex(Company company, String hexName) {
        if (company == null)
            return false;
        // gameManager is protected in the superclass, so we can access it directly
        MapHex hex = gameManager.getRoot().getMapManager().getHex(hexName);
        if (hex == null)
            return false;

        // Use hasTokenOfCompany, not hasStation
        return hex.hasTokenOfCompany((PublicCompany) company);
    }

    /**
     * Finds the SpecialBaseTokenLay property for D&H and marks it as exercised.
     */
    private void expireDhTokenAbility(Company company) {
        Collection<SpecialProperty> specials = company.getSpecialProperties();

        if (specials == null)
            return;

    }

    // ... (lines of unchanged context code) ...
    @Override
    public boolean processGameSpecificAction(PossibleAction action) {
        if (action instanceof net.sf.rails.game.specific._1817.action.TakeLoans_1817) {
            net.sf.rails.game.specific._1817.action.TakeLoans_1817 tlAction = (net.sf.rails.game.specific._1817.action.TakeLoans_1817) action;
            PublicCompany comp = tlAction.getCompany();
            int count = tlAction.getLoansToTake();

            if (count > 0) {
                // --- START FIX ---
                // 1. Verify Liquidation State (Rule 6.10/6.11)
                // Companies in the red area (price 0) are effectively closed/bankrupt
                if (comp.isClosed() || (comp.hasStockPrice() && comp.getCurrentSpace().getPrice() == 0)) {
                    log.error("1817: Company in liquidation cannot take loans.");
                    return false;
                }

                // 2. Verify OR Step Timing (Rule 6.1 Blackout Period)
                // Evaluates the state flag set during the OR flow
                if (loanBlackoutPeriod.value()) {
                    log.error("1817: Cannot take loans between paying interest and repaying loans.");
                    return false;
                }

                // 3. Verify Company Capacity (Rule 1.2.7)
                // 2-share=2, 5-share=5, 10-share=10
                int currentLoans = comp.getNumberOfBonds();
                if (currentLoans + count > tlAction.getMaxLoansAllowed()) {
                    log.error("1817: " + comp.getId() + " loan capacity exceeded.");
                    return false;
                }

                // 4. Verify Global Bank of New York Limit (Rule 1.2.5)
                int globalLoansTaken = 0;
                for (PublicCompany c : gameManager.getAllPublicCompanies()) {
                    globalLoansTaken += c.getNumberOfBonds();
                }

                if (globalLoansTaken + count > 70) {
                    log.error("1817: Bank of New York exhausted (70 loan limit).");
                    return false;
                }

                // 5. Execute Action (State-Safe)
                if (comp instanceof net.sf.rails.game.specific._1817.PublicCompany_1817) {
                    net.sf.rails.game.specific._1817.PublicCompany_1817 comp1817 = (net.sf.rails.game.specific._1817.PublicCompany_1817) comp;

                    // Bonds update via IntegerState in the subclass
                    comp1817.setNumberOfBonds(currentLoans + count);

                    // Cash transfer via subclass method
                    int loanAmount = count * 100;
                    net.sf.rails.game.financial.Bank bank = gameManager.getRoot().getBank();
                    comp1817.addCashFromBank(loanAmount, bank);
                    log.info("1817: " + comp.getId() + " took " + count + " loans.");
                    return true;
                } else {
                    log.error("1817: Active company is not a valid 1817 Public Company.");
                    return false;
                }
            }
        }

        return super.processGameSpecificAction(action);
    }

    @Override

    public boolean setPossibleActions() {
        // 1. Execute base engine logic to populate standard actions (LayTile, BuyTrain,
        // etc.)
        super.setPossibleActions();
        // 1. Execute base engine logic and capture the result
        boolean actionsAdded = super.setPossibleActions();

        PublicCompany comp = operatingCompany.value();
        if (comp == null)
            return actionsAdded;
        // 2. Enforce Subclass Polymorphism
        if (!(comp instanceof net.sf.rails.game.specific._1817.PublicCompany_1817)) {
            return actionsAdded;
        }
        net.sf.rails.game.specific._1817.PublicCompany_1817 comp1817 = (net.sf.rails.game.specific._1817.PublicCompany_1817) comp;

        // 3. Verify Blackout and Liquidation States
        if (loanBlackoutPeriod.value() || comp1817.isClosed()
                || (comp1817.hasStockPrice() && comp1817.getCurrentSpace().getPrice() == 0)) {
            return actionsAdded;
        }

        // 4. Verify Company Capacity Limit
        int currentLoans = comp1817.getNumberOfBonds();
        int maxLoans = comp1817.getShareCount();
        if (currentLoans >= maxLoans) {
            return actionsAdded;
        }

        // 5. Verify Global Bank of New York Limit
        int globalLoansTaken = 0;
        for (PublicCompany c : gameManager.getAllPublicCompanies()) {
            if (c instanceof net.sf.rails.game.specific._1817.PublicCompany_1817) {
                globalLoansTaken += ((net.sf.rails.game.specific._1817.PublicCompany_1817) c).getNumberOfBonds();
            }
        }

        if (globalLoansTaken >= 70) {
            return actionsAdded;
        }

        // 6. Force Injection
        log.info("1817_DEBUG: Injecting TakeLoans_1817 action for " + comp1817.getId());
        possibleActions.add(new net.sf.rails.game.specific._1817.action.TakeLoans_1817(comp1817, maxLoans));
        return true;

    }
}
