package net.sf.rails.ui.swing;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Component;
import java.awt.event.KeyEvent;
import javax.swing.text.JTextComponent;
import javax.swing.AbstractButton;

import net.sf.rails.game.GameDef;
import net.sf.rails.game.OperatingRound;
import net.sf.rails.game.StartRound;
import rails.game.action.SetDividend;
import net.sf.rails.game.round.RoundFacade;
import rails.game.action.PossibleAction;
import rails.game.action.SetDividend;

public class GlobalHotkeyManager implements KeyEventDispatcher {

    private final GameUIManager gameUIManager;

    public GlobalHotkeyManager(GameUIManager gameUIManager) {
        this.gameUIManager = gameUIManager;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getID() != KeyEvent.KEY_PRESSED) {
            return false;
        }

        // --- SAFETY CHECK: TYPING ---
        // If the user is typing in a text field (e.g. Chat, Save Name, Bid Amount),
        // we generally do NOT want to intercept keys like 'A', 'T', 'Z', etc.
        // We DO allow Ctrl+keys and maybe Enter/Esc depending on context, 
        // but for safety, we yield if a text component has focus.
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner instanceof JTextComponent && ((JTextComponent) focusOwner).isEditable()) {
            // Allow Ctrl+Z / Ctrl+Y even in text fields (standard Undo/Redo)
            if (!e.isControlDown() && !e.isMetaDown()) {
                return false; 
            }
        }

        boolean isCtrlDown = e.isControlDown() || e.isMetaDown();
        int keyCode = e.getKeyCode();

        // 1. UNDO (Ctrl + Z)
        if (isCtrlDown && keyCode == KeyEvent.VK_Z) {
            triggerUndo();
            return true;
        }

        // 2. REDO (Ctrl + Y)
        if (isCtrlDown && keyCode == KeyEvent.VK_Y) {
            triggerRedo();
            return true;
        }

        // 3. TOGGLE TIME (T)
        if (!isCtrlDown && !e.isAltDown() && !e.isShiftDown() && keyCode == KeyEvent.VK_T) {
            triggerPause();
            return true;
        }

        // 4. AI MOVE (A)
        if (!isCtrlDown && !e.isAltDown() && keyCode == KeyEvent.VK_A) {
            triggerAI();
            return true;
        }

        // 5. ESCAPE ('No' / 'Skip' / 'Pass')
        if (keyCode == KeyEvent.VK_ESCAPE) {
            triggerEscape();
            return true;
        }

        // 6. ENTER ('Yes' / 'Default' / 'Payout')
        if (keyCode == KeyEvent.VK_ENTER) {
            triggerEnter();
            return true;
        }

        return false;
    }

    private void triggerUndo() {
        if (gameUIManager.getStatusWindow() != null 
            && gameUIManager.getStatusWindow().undoItem != null 
            && gameUIManager.getStatusWindow().undoItem.isEnabled()) {
            gameUIManager.getStatusWindow().undoItem.doClick();
        }
    }

    private void triggerRedo() {
        StatusWindow sw = gameUIManager.getStatusWindow();
        if (sw != null) {
            if (sw.redoItem != null && sw.redoItem.isEnabled()) {
                sw.redoItem.doClick();
            } 
        }
    }

    private void triggerPause() {
        if (gameUIManager.getStatusWindow() != null 
            && gameUIManager.getStatusWindow().pauseButton != null 
            && gameUIManager.getStatusWindow().pauseButton.isEnabled()) {
            gameUIManager.getStatusWindow().pauseButton.doClick();
        }
    }

    private void triggerAI() {
        gameUIManager.performAIMove();
    }

    private void triggerEscape() {
        RoundFacade round = gameUIManager.getCurrentRound();
        
        // --- CASE 1: OPERATING ROUND (Skip) ---
        if (round instanceof OperatingRound) {
ORWindow orWindow = gameUIManager.orWindow;
                if (orWindow != null && orWindow.isVisible()) {
                ORPanel panel = orWindow.getORPanel();
                if (panel != null) {
                    // Try to click the context-specific SKIP button
                    if (panel.activePhase == 1 && clickIfEnabled(panel.btnTileSkip)) return;
                    if (panel.activePhase == 2 && clickIfEnabled(panel.btnTokenSkip)) return;
                    if (panel.activePhase == 4 && clickIfEnabled(panel.btnTrainSkip)) return;
                    
                    // Fallback: If "Done" is available (often treated as "No more moves"), click it
                    if (clickIfEnabled(panel.btnDone)) return;
                }
            }
        }

        // --- CASE 2: AUCTION / START ROUND (Pass) ---
        // "Esc" -> "No" -> "Pass"
        if (round instanceof StartRound) {
            StartRoundWindow srw = gameUIManager.getStartRoundWindow();
            // StartRoundWindow doesn't expose passButton publicly, but we can access it via Actions
            // or if we change StartRoundWindow to expose it. 
            // For now, let's rely on the Action mechanism if the window is focused, 
            // OR use the StatusWindow pass button if it's mirrored there.
            // Actually, StartRoundWindow is usually a Dialog or Frame. 
            // We'll try the StatusWindow's pass button which often mirrors the current round's pass action.
            if (gameUIManager.getStatusWindow() != null) {
                if (clickIfEnabled(gameUIManager.getStatusWindow().passButton)) return;
            }
        }

        // --- CASE 3: STOCK ROUND (Pass/Done) ---
        // In Stock Round, StatusWindow is the main view.
        if (gameUIManager.getStatusWindow() != null && gameUIManager.getStatusWindow().isVisible()) {
             if (clickIfEnabled(gameUIManager.getStatusWindow().passButton)) return;
        }
    }

   private void triggerEnter() {
        RoundFacade round = gameUIManager.getCurrentRound();

        // --- CASE 1: OPERATING ROUND ---
        if (round instanceof OperatingRound) {
            OperatingRound or = (OperatingRound) round;
 
            ORWindow orWindow = gameUIManager.orWindow;
    
            
            if (orWindow != null && orWindow.isVisible()) {
                ORPanel panel = orWindow.getORPanel();
                
   
                // Special Rule: Payout for Set Revenue
                // Removed dynamic component loop to fix compilation error (getButtonPanel not found).
                // We rely on standard default button processing below.
                
                // General Default: Current Default Button (Confirm, Done, etc.)
                if (panel != null) {
                    if (clickIfEnabled(panel.currentDefaultButton)) return;
                    
                    // Fallback to ORUIManager confirm if button isn't explicitly set
                    if (orWindow.getORUIManager() != null) {
                        orWindow.getORUIManager().confirmUpgrade();
                    }
                }
            }
        }
        // CASE 2: STOCK ROUND
        // Map Enter to the Status Window "Pass" / "Done" button (Global Key Interference Fix)
        else if (round instanceof net.sf.rails.game.financial.StockRound) {
            if (gameUIManager.getStatusWindow() != null) {
                clickIfEnabled(gameUIManager.getStatusWindow().passButton);
            }
        }

    }
    private boolean clickIfEnabled(AbstractButton btn) {
        if (btn != null && btn.isEnabled() && btn.isVisible()) {
            btn.doClick();
            return true;
        }
        return false;
    }
}