package net.sf.rails.game.specific._1870;

import net.sf.rails.game.GameManager;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.GameDef;
import rails.game.action.SetDividend;
import rails.game.action.PossibleAction;

public class ConnectionRunRound_1870 extends OperatingRound_1870 {

    private PublicCompany connectionCompany;

    public ConnectionRunRound_1870(GameManager parent, String id) {
        super(parent, id);
        
        this.steps = new GameDef.OrStep[] {
            GameDef.OrStep.INITIAL, 
            GameDef.OrStep.CALC_REVENUE, 
            GameDef.OrStep.FINAL
        };
    }

    public void setConnectionCompany(PublicCompany company) {
        this.connectionCompany = company;
    }

    @Override
    public void start() {
        if (connectionCompany != null) {
            operatingCompanies.clear();
            operatingCompanies.add(connectionCompany);
        }
        super.start();
    }

    @Override
    protected void privatesPayOut() {
        // Prevent double-dipping: Private companies do NOT pay out again during a connection run.
    }

    @Override
    protected void prepareRevenueAndDividendAction() {
        super.prepareRevenueAndDividendAction();
        
        SetDividend originalAction = null;
        for (PossibleAction pa : possibleActions.getList()) {
            if (pa instanceof SetDividend) {
                originalAction = (SetDividend) pa;
                break;
            }
        }

        if (originalAction != null) {
            possibleActions.remove(originalAction);
            
            // 1870 Rule: Connection runs do not allow Half Dividends
            int[] allowedRevenueActions = new int[] { SetDividend.PAYOUT, SetDividend.WITHHOLD };
            
            SetDividend modifiedAction = new SetDividend(getRoot(), originalAction.getActualRevenue(), false, allowedRevenueActions);
            modifiedAction.setActualRevenue(originalAction.getActualRevenue());
            
            possibleActions.add(modifiedAction);
        }
    }

// --- START FIX ---
    @Override
    public boolean process(PossibleAction action) {
        if (action instanceof SetDividend) {
            SetDividend sd = (SetDividend) action;
            
            // INTERCEPT HERE: We interrupt AFTER the user clicks the UI button, but 
            // BEFORE the engine processes it and writes it to the save file. 
            // This guarantees the entered value is permanently captured.
            if (!getRoot().getGameManager().isReloading()) {
                int maxRev = sd.getActualRevenue();
                String input = (String) javax.swing.JOptionPane.showInputDialog(
                        null,
                        "Connection Run Revenue Override:\n\nThe engine calculated a maximum network revenue of $" + maxRev + ".\nIf the valid Connection Run route is different, enter the exact revenue below:",
                        "Connection Run Revenue",
                        javax.swing.JOptionPane.QUESTION_MESSAGE,
                        null,
                        null,
                        String.valueOf(maxRev)
                );
                
                int entered = maxRev;
                if (input != null && !input.trim().isEmpty()) {
                    try {
                        entered = Integer.parseInt(input.trim());
                    } catch (NumberFormatException e) {
                        // Default back to calculated maximum on invalid input
                    }
                }
                sd.setActualRevenue(entered);
            }
boolean success = super.process(action);
            
            // CRITICAL FIX: The 1870 Connection Run consists solely of a single payout.
            // Bypassing the standard OR steps means the engine searches for a "Buy Train" 
            // phase that doesn't exist and hangs. We explicitly finish the round right here.
            if (success) {
                finishRound();
            }
            
            return success;
        }
        return super.process(action);
    }
    

    @Override
    public String getRoundName() {
        return "Connection Run";
    }
}