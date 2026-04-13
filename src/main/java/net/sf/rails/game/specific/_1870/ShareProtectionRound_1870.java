package net.sf.rails.game.specific._1870;

import net.sf.rails.common.ReportBuffer;
import net.sf.rails.game.GameManager;
import net.sf.rails.game.Player;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.Round;
import net.sf.rails.game.financial.PublicCertificate;
import net.sf.rails.game.state.Currency;
import net.sf.rails.game.state.IntegerState;
import net.sf.rails.game.state.StringState;
import rails.game.action.BuyCertificate;
import rails.game.action.NullAction;
import rails.game.action.PossibleAction;

import java.util.List;

/**
 * Handles the 1870-specific Share Price Protection rule.
 * When a player sells shares, the stock round is interrupted, and the President 
 * of that company is given the opportunity to buy those shares directly from the 
 * pool to prevent the stock price from dropping.
 */
public class ShareProtectionRound_1870 extends Round {

    // Persistent state required for save/load to remember what company is being protected
    private final StringState protectedCompanyName = StringState.create(this, "protectedCompanyName");
    private final IntegerState sharesSold = IntegerState.create(this, "sharesSold");

    public ShareProtectionRound_1870(GameManager gameManager, String id) {
        super(gameManager, id);
    }

    /**
     * Initializes the context for the interruption.
     */
    public void setProtectionContext(PublicCompany company, int sold) {
        this.protectedCompanyName.set(company.getId());
        this.sharesSold.set(sold);
    }

    @Override
    public boolean setPossibleActions() {
        PublicCompany company = companyManager.getPublicCompany(protectedCompanyName.value());
        if (company == null) return false;

        Player president = company.getPresident();
        
        // If the company has no president (e.g., dumped into receivership) or president bankrupt
        if (president == null || president.isBankrupt()) {
            possibleActions.add(new NullAction(getRoot(), NullAction.Mode.PASS));
            return true;
        }

        // Shift the active UI turn to the President
        playerManager.setCurrentPlayer(president);

        int price = company.getCurrentSpace().getPrice() / company.getShareUnitsForSharePrice();
        int totalCost = sharesSold.value() * price;

        boolean canAfford = president.getCashValue() >= totalCost;
        
        // In 1870, share protection allows bypassing the 60% per-company hold limit, 
        // but it still MUST respect the player's absolute total certificate limit.
        float currentCerts = president.getPortfolioModel().getCertificateCount();
        int certLimit = gameManager.getPlayerCertificateLimit(president);
        boolean certLimitOk = (currentCerts + sharesSold.value()) <= certLimit;

        if (canAfford && certLimitOk) {
            // Present a BuyCertificate action specifically matching the shares sold
            possibleActions.add(new BuyCertificate(
                    company, 
                    sharesSold.value() * company.getShareUnit(), 
                    pool.getParent(),
                    price, 
                    sharesSold.value()
            ));
        }

        // The President can always decline to protect
        possibleActions.add(new NullAction(getRoot(), NullAction.Mode.PASS));
        
        return true;
    }

    @Override
    public boolean process(PossibleAction action) {
        PublicCompany company = companyManager.getPublicCompany(protectedCompanyName.value());
        Player president = company.getPresident();

        if (action instanceof NullAction) {
            ReportBuffer.add(this, president.getId() + " declines to protect the share price of " + company.getId());
            
            // The protection failed. Trigger the deferred price drop here.
            // Note: The sell() method below will need to interface with StockMarket_1870 
            // to correctly calculate the "ledge" stopping logic.
            stockMarket.sell(company, null, sharesSold.value());
            
            finishRound();
            return true;
        } 
        else if (action instanceof BuyCertificate) {
            BuyCertificate buyAction = (BuyCertificate) action;
            int totalCost = buyAction.getNumberBought() * buyAction.getPrice();
            
            ReportBuffer.add(this, president.getId() + " protects the share price of " + company.getId() + 
                                   " by buying " + sharesSold.value() + " shares for " + bank.getCurrency().format(totalCost));
            
            // Transfer the shares out of the Bank Pool to the President
            int count = sharesSold.value();
Iterable<PublicCertificate> poolCerts = pool.getCertificates(company);
            for (PublicCertificate cert : poolCerts) {
                if (count > 0 && !cert.isPresidentShare()) {
                    cert.moveTo(president);
                    count--;
                }
            }
            
            // Deduct funds from President and wire to Bank
            String costText = Currency.wire(president, totalCost, bank);
            
            // By bypassing stockMarket.sell(), we have successfully protected the price from dropping.
            finishRound();
            return true;
        }
        
        return false;
    }

    @Override
    public String getRoundName() {
        return "Share Protection";
    }

        public void start() {
        setPossibleActions();
    }

}