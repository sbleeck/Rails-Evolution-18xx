package rails.game.correct;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;

import net.sf.rails.common.DisplayBuffer;
import net.sf.rails.common.LocalText;
import net.sf.rails.common.ReportBuffer;
import net.sf.rails.game.GameManager;
import net.sf.rails.game.Player;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.financial.Bank;
import net.sf.rails.game.state.Currency;
import net.sf.rails.game.state.MoneyOwner;

public class CashCorrectionManager extends CorrectionManager {

    private CashCorrectionManager(GameManager parent) {
        super(parent, CorrectionType.CORRECT_CASH);
    }

    public static CashCorrectionManager create(GameManager parent) {
        return new CashCorrectionManager(parent);
    }

    @Override
    public List<CorrectionAction> createCorrections() {
        // Keep existing logic for GameStatus buttons
        List<CorrectionAction> actions = super.createCorrections();

        if (isActive()) {
            List<Player> players = getRoot().getPlayerManager().getPlayers();
            for(Player pl:players){
                actions.add(new CashCorrectionAction(getRoot(), pl));
            }

            List<PublicCompany> publicCompanies = getParent().getAllPublicCompanies();
            for(PublicCompany pc:publicCompanies){
                if (pc.hasFloated() && !pc.isClosed())
                    actions.add(new CashCorrectionAction(pc));
            }
        }

        return actions;
    }

// ... (lines of unchanged context code) ...
    @Override
    public boolean executeCorrection(CorrectionAction action) {
        // 1. Logging Trace
        log.info("DEBUG: CashCorrectionManager executeCorrection action=" + action);

        // 2. Intercept Menu Click (CorrectionModeAction)
        if (action instanceof CorrectionModeAction) {
            // If the mode is NOT active, this is a "Turn On" request.
            if (!isActive()) {
                // FIX: Check isReloading AND run Wizard via invokeLater to prevent
                // "Nested Action" issues (where the Cash Action is lost because the 
                // Menu Action is still running).
                if (!getParent().isReloading()) {
                    javax.swing.SwingUtilities.invokeLater(() -> runWizard());
                }
                // Return true to indicate handled. 
                return true; 
            }
            return super.executeCorrection(action);
        }

        // 3. Handle Direct Action (e.g. from GameStatus button click, Replay, or Wizard completion)
        if (action instanceof CashCorrectionAction) {
            CashCorrectionAction cca = (CashCorrectionAction) action;

            // FIX: Check isReloading. 
            if (cca.getAmount() == 0 && !getParent().isReloading()) {
                log.info("DEBUG: Intercepted 0-amount CashCorrection. Opening Dialog.");
                 String input = (String) javax.swing.JOptionPane.showInputDialog(
                    null,
                    LocalText.getText("CorrectCashDialogMessage", cca.getCashHolder().getId()),
                    LocalText.getText("CorrectCashDialogTitle"),
                    javax.swing.JOptionPane.QUESTION_MESSAGE,
                    null, null, "0"
                );

                if (input == null) return false; 

                if (input.trim().startsWith("+")) input = input.trim().substring(1);

                try {
                    int val = Integer.parseInt(input.trim());
                    if (val == 0) return false;
                    cca.setAmount(val);
                } catch (NumberFormatException e) {
                    DisplayBuffer.add(this, "Invalid Amount");
                    return false;
                }
            }
            return execute(cca);
        }
        
        return super.executeCorrection(action);
    }
// ... (rest of the method) ...

    private void runWizard() {
        log.info("DEBUG: Starting Cash Correction Wizard");

        // Step 1: Gather all MoneyOwners (Players + Floated Companies)
        java.util.List<MoneyOwner> candidates = new java.util.ArrayList<>();
        java.util.List<String> names = new java.util.ArrayList<>();
        
        // Players
        for (Player p : getRoot().getPlayerManager().getPlayers()) {
            candidates.add(p);
            names.add(p.getName());
        }
        
        // Companies
        for (PublicCompany c : getParent().getAllPublicCompanies()) {
            if (c.hasFloated() && !c.isClosed()) {
                candidates.add(c);
                names.add(c.getId());
            }
        }
        
        // Step 2: Show Selection Dialog
        String selectedName = (String) javax.swing.JOptionPane.showInputDialog(
            null, 
            "Select Entity to Correct:", 
            "Cash Correction Wizard",
            javax.swing.JOptionPane.QUESTION_MESSAGE, 
            null, 
            names.toArray(), 
            names.get(0)
        );
        
        if (selectedName == null) return;
        
        // Find the object
        int index = names.indexOf(selectedName);
        MoneyOwner selectedOwner = candidates.get(index);
        
        // Step 3: Ask for Amount
        String input = (String) javax.swing.JOptionPane.showInputDialog(
            null,
            "Enter cash adjustment for " + selectedOwner.getId() + ":",
            "Cash Correction",
            javax.swing.JOptionPane.QUESTION_MESSAGE,
            null, null, "0"
        );
        
        if (input == null) return;
        
        try {
            if (input.trim().startsWith("+")) input = input.trim().substring(1);
            int amount = Integer.parseInt(input.trim());
            if (amount == 0) return;
            
            // Step 4: Create Action
            CashCorrectionAction cca;
            if (selectedOwner instanceof Player) {
                cca = new CashCorrectionAction(getRoot(), (Player)selectedOwner);
            } else {
                cca = new CashCorrectionAction((PublicCompany)selectedOwner);
            }
            cca.setAmount(amount);
            
            // Send to GameManager to record the action, then it calls back to executeCorrection()
            getParent().process(cca); 
            
        } catch (NumberFormatException e) {
             DisplayBuffer.add(this, "Invalid Amount entered");
        }
    }

    private boolean execute(CashCorrectionAction cashAction) {

        boolean result = false;

        MoneyOwner ch = cashAction.getCashHolder();
        int amount = cashAction.getAmount();

        String errMsg = null;

        while (true) {
            if (amount == 0 ) {
                errMsg =
                    LocalText.getText("CorrectCashZero");
                break;
            }
            if ((amount + ch.getCash()) < 0) {
                errMsg =
                    LocalText.getText("NotEnoughMoney",
                            ch.getId(),
                            Bank.format(this, ch.getCash()),
                            Bank.format(this, -amount)
                    );
                break;
            }
            break;
        }

        if (errMsg != null) {
            DisplayBuffer.add(this, LocalText.getText("CorrectCashError",
                    ch.getId(),
                    errMsg));
            result = true;
        } else {
            // no error occured

            String msg;
            if (amount < 0) {
                // negative amounts: remove cash from cashholder
                String text = Currency.toBank(ch, -amount);

                msg = LocalText.getText("CorrectCashSubstractMoney",
                        ch.getId(),
                        text );
            } else {
                // positive amounts: add cash to cashholder
                String text = Currency.fromBank(amount, ch);
                msg = LocalText.getText("CorrectCashAddMoney",
                        ch.getId(),
                        text);
            }
            ReportBuffer.add(this, msg);
            getParent().addToNextPlayerMessages(msg, true);
            result = true;
            
            // Force UI Refresh
            if (getParent().getGameUIManager() != null) {
                getParent().getGameUIManager().forceFullUIRefresh();
            }

            // Force UI Refresh specifically for Status Window
        if (getParent().getGameUIManager() != null) {
            getParent().getGameUIManager().forceFullUIRefresh();
            
            if (getParent().getGameUIManager().getStatusWindow() != null) {
                getParent().getGameUIManager().getStatusWindow().repaint();
            }
        }
        }

       return result;
    }

}