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


public class StockRound_1817 extends StockRound {

        private static final Logger log = LoggerFactory.getLogger(StockRound.class);

        
    public StockRound_1817(GameManager parent, String id) {
        super(parent, id);
    }

    @Override
    public void setBuyableCerts() {
        // 1. Let the base engine calculate standard buyable certificates
        super.setBuyableCerts();

        if (possibleActions == null) return;

        List<PossibleAction> actionsToRemove = new ArrayList<>();
        List<PossibleAction> actionsToAdd = new ArrayList<>();

        // 2. 1817 Special Rule: Companies are not started via standard 'StartCompany'.
        // We must find all standard StartCompany actions, remove them, and replace them.
        for (PossibleAction action : possibleActions.getList()) {
            if (action instanceof StartCompany) {
                StartCompany startAction = (StartCompany) action;
                PublicCompany company = startAction.getCompany();

                // Mark the standard 18xx start company action for removal
                actionsToRemove.add(action);

                // Add the 1817 specific initiation action
                // Only add one per company to avoid duplicates if the engine generated multiple start prices
                boolean alreadyAdded = false;
                for (PossibleAction added : actionsToAdd) {
                    if (added instanceof Initiate1817IPO && ((Initiate1817IPO) added).getCompany() == company) {
                        alreadyAdded = true;
                        break;
                    }
                }

                if (!alreadyAdded) {
                    actionsToAdd.add(new Initiate1817IPO(gameManager.getRoot(), company));
                }
            }
        }

        // 3. Apply the modifications to the action list
        for (PossibleAction action : actionsToRemove) {
            possibleActions.remove(action);
        }
        for (PossibleAction action : actionsToAdd) {
            possibleActions.add(action);
        }
    }
    
    @Override
    protected boolean processGameSpecificAction(PossibleAction action) {
        if (action instanceof Initiate1817IPO) {
            Initiate1817IPO ipoAction = (Initiate1817IPO) action;
            
            log.info("Processing 1817 IPO Initiation for " + ipoAction.getCompany().getId() + 
                     " at Hex " + ipoAction.getHexId() + " with opening bid $" + ipoAction.getBid());
            
            // TODO: Here we will notify the GameManager to suspend the current StockRound 
            // and push the new AuctionRound_1817 onto the round stack.
            return true; 
        }
        return super.processGameSpecificAction(action);
    }
}