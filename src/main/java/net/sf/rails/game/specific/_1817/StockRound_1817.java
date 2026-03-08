package net.sf.rails.game.specific._1817;

import java.util.ArrayList;
import java.util.List;

import net.sf.rails.game.GameManager;
import net.sf.rails.game.PublicCompany;
import rails.game.action.PossibleAction;
import rails.game.action.StartCompany;
import net.sf.rails.game.financial.StockRound;
import net.sf.rails.game.specific._1817.action.Initiate1817IPO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.sf.rails.game.specific._1817.action.TakeLoans_1817;

/**
 * 1817 specific Stock Round logic.
 * Triggers certificate adjustment on start and handles the IPO auction
 * shortcut.
 */
public class StockRound_1817 extends StockRound {

    private static final Logger log = LoggerFactory.getLogger(StockRound_1817.class);

    public StockRound_1817(GameManager parent, String id) {
        super(parent, id);
    }

    @Override
    public void start() {
        super.start();
        // Initial certificate setup for all companies
        if (gameManager.getSRNumber() == 1) {
            for (PublicCompany comp : gameManager.getAllPublicCompanies()) {
                if (comp instanceof PublicCompany_1817) {
                    ((PublicCompany_1817) comp).adjustCertificates();
                }
            }
        }
    }

    @Override
    public void setBuyableCerts() {
        super.setBuyableCerts();

        if (possibleActions == null)
            return;

        // 1817: Players can take loans for companies they lead during their Stock Round
        // turn
        net.sf.rails.game.Player currentPlayer = gameManager.getCurrentPlayer();
        if (currentPlayer != null) {
            for (PublicCompany comp : gameManager.getAllPublicCompanies()) {
                // If player is president and company has loan capacity
                if (comp.getPresident() == currentPlayer && !comp.isClosed()) {
                    // Capped by share count (2, 5, or 10)
                    int maxLoans = (comp instanceof PublicCompany_1817) ? ((PublicCompany_1817) comp).getShareCount()
                            : 0;
                    if (comp.getNumberOfBonds() < maxLoans) {
                        possibleActions.add(new TakeLoans_1817(comp, maxLoans));
                    }
                }
            }
        }

        List<PossibleAction> actionsToRemove = new ArrayList<>();
        List<PossibleAction> actionsToAdd = new ArrayList<>();

        for (PossibleAction action : possibleActions.getList()) {
            if (action instanceof StartCompany) {
                StartCompany startAction = (StartCompany) action;
                PublicCompany company = startAction.getCompany();

                actionsToRemove.add(action);

                boolean alreadyAdded = false;
                for (PossibleAction added : actionsToAdd) {
                    if (added instanceof Initiate1817IPO
                            && ((Initiate1817IPO) added).getCompanyName().equals(company.getId())) {
                        alreadyAdded = true;
                        break;
                    }
                }

                if (!alreadyAdded) {
                    actionsToAdd.add(new Initiate1817IPO(gameManager.getRoot(), company.getId()));
                }
            }
        }

        for (PossibleAction action : actionsToRemove) {
            possibleActions.remove(action);
        }
        for (PossibleAction action : actionsToAdd) {
            possibleActions.add(action);
        }
    }

    // ... (lines of unchanged context code) ...
    @Override
    protected boolean processGameSpecificAction(PossibleAction action) {
        if (action instanceof Initiate1817IPO) {
            try {
                Initiate1817IPO ipoAction = (Initiate1817IPO) action;
                PublicCompany_1817 comp = (PublicCompany_1817) ipoAction.getCompany();
                net.sf.rails.game.Player initiator = gameManager.getCurrentPlayer();

                log.info("Starting 1817 Auction for " + comp.getId() + " initiated by " + initiator.getName());

                // 1. Push the new Auction Round onto the stack

                // Explicitly preserve the current Stock Round so it can be resumed later
                gameManager.setInterruptedRound(this);

                // This preserves the Stock Round as the 'interruptedRound'
                AuctionRound_1817 auctionRound = gameManager.createRound(AuctionRound_1817.class,
                        "Auction_" + comp.getId());

                // 2. Initialize the auction state
                auctionRound.setupAuction(
                        comp,
                        ipoAction.getHexId(),
                        ipoAction.getBid(),
                        initiator,
                        gameManager.getPlayers());

                // 3. Mark the initiator as having acted so the SR continues correctly after
                // resolution
                hasActed.set(true);
                companyBoughtThisTurnWrapper.set(comp);

                return true;
            } catch (Exception e) {
                log.error("Failed to transition to 1817 Auction", e);
                return false;
            }

        }

        if (action instanceof net.sf.rails.game.specific._1817.action.TakeLoans_1817) {
            net.sf.rails.game.specific._1817.action.TakeLoans_1817 tlAction = (net.sf.rails.game.specific._1817.action.TakeLoans_1817) action;
            PublicCompany comp = tlAction.getCompany();
            int count = tlAction.getLoansToTake();

            if (count > 0) {
                // Update bond count
                comp.setNumberOfBonds(comp.getNumberOfBonds() + count);

                // Calculate and transfer cash ($100 per bond)
                int loanAmount = count * 100;
                net.sf.rails.game.financial.Bank bank = gameManager.getRoot().getBank();
                comp.addCashFromBank(loanAmount, bank);

                // Mark that the player has taken an action
                hasActed.set(true);
                return true;
            }
        }

        return super.processGameSpecificAction(action);
    }

    /**
     * Exempts 1817 2-share companies from the standard 60% global hold limit.
     * Prevents the engine from deadlocking when a player holds 100% of a new
     * company.
     */
    @Override
    public boolean checkAgainstHoldLimit(net.sf.rails.game.Player player, net.sf.rails.game.PublicCompany company,
            int number) {
        if (company instanceof PublicCompany_1817) {
            if (((PublicCompany_1817) company).getShareCount() == 2) {
                return true;
            }
        }
        return super.checkAgainstHoldLimit(player, company, number);
    }

}