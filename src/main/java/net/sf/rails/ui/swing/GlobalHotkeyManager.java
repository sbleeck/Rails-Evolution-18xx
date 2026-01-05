package net.sf.rails.ui.swing;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Component;
import java.awt.event.KeyEvent;
import javax.swing.text.JTextComponent;
import javax.swing.AbstractButton;

import net.sf.rails.game.OperatingRound;
import net.sf.rails.game.StartRound;
import net.sf.rails.game.round.RoundFacade;
import net.sf.rails.game.financial.StockRound;

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
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner instanceof JTextComponent && ((JTextComponent) focusOwner).isEditable()) {
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

        // 2. REDO (Ctrl + Y or Ctrl + Shift + Z)
        // Standard Redo is often Shift+Ctrl+Z, but we keep Ctrl+Y support too
        if (isCtrlDown && (keyCode == KeyEvent.VK_Y || (e.isShiftDown() && keyCode == KeyEvent.VK_Z))) {
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
                    
                    // Phase 1 (Tile) & Phase 2 (Token):
                    // Since we removed specific "Skip" buttons in favor of the Smart Button,
                    // we trigger the underlying Skip logic via the Manager.
                    if (panel.activePhase == 1 || panel.activePhase == 2) {
                        if (orWindow.getORUIManager() != null) {
                            orWindow.getORUIManager().skipUpgrade(); 
                            return;
                        }
                    }

                    // Phase 4 (Trains): "Skip" means "Done Buying" or "Skip Train"
                    if (panel.activePhase == 4 && clickIfEnabled(panel.btnTrainSkip)) return;
                    
                    // Fallback: Done
                    if (clickIfEnabled(panel.btnDone)) return;
                }
            }
        }

        // --- CASE 2: AUCTION / START ROUND (Pass) ---
        if (round instanceof StartRound) {
            if (gameUIManager.getStatusWindow() != null) {
                if (clickIfEnabled(gameUIManager.getStatusWindow().passButton)) return;
            }
        }

        // --- CASE 3: STOCK ROUND (Pass) ---
        if (round instanceof StockRound) {
             if (gameUIManager.getStatusWindow() != null && gameUIManager.getStatusWindow().isVisible()) {
                 if (clickIfEnabled(gameUIManager.getStatusWindow().passButton)) return;
            }
        }
    }

   private void triggerEnter() {
        RoundFacade round = gameUIManager.getCurrentRound();

        // --- CASE 1: OPERATING ROUND ---
        if (round instanceof OperatingRound) {
            ORWindow orWindow = gameUIManager.orWindow;
            
            if (orWindow != null && orWindow.isVisible()) {
                ORPanel panel = orWindow.getORPanel();
                
                if (panel != null) {
                    // 1. Try the calculated "Default Button" (Confirm, Done, etc.)
                    if (clickIfEnabled(panel.currentDefaultButton)) return;
                    
                    // 2. Fallback to Map Confirm (if button logic failed but map has selection)
                    if (orWindow.getORUIManager() != null) {
                        orWindow.getORUIManager().confirmUpgrade();
                    }
                }
            }
        }
        // CASE 2: STOCK ROUND
        // Map Enter to the Status Window "Pass" / "Done"
        else if (round instanceof StockRound) {
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
   

    private void triggerUndo() {
        // 1. Priority: Operating Round Local Undo
        if (gameUIManager.getCurrentRound() instanceof OperatingRound) {
            // --- START FIX ---
            ORWindow win = gameUIManager.orWindow; // Direct field access
            // --- END FIX ---
            if (win != null && win.isVisible() && win.getORPanel() != null) {
                // Try executing on panel first. If successful, we are done.
                if (win.getORPanel().executeUndo()) {
                    return;
                }
            }
        }

        // 2. Fallback: Status Window (Global Undo)
        if (gameUIManager.getStatusWindow() != null 
            && gameUIManager.getStatusWindow().undoItem != null 
            && gameUIManager.getStatusWindow().undoItem.isEnabled()) {
            gameUIManager.getStatusWindow().undoItem.doClick();
        }
    }

    private void triggerRedo() {
        // 1. Priority: Operating Round Local Redo
        if (gameUIManager.getCurrentRound() instanceof OperatingRound) {
            // --- START FIX ---
            ORWindow win = gameUIManager.orWindow; // Direct field access
            // --- END FIX ---
            if (win != null && win.isVisible() && win.getORPanel() != null) {
                if (win.getORPanel().executeRedo()) {
                    return;
                }
            }
        }

        // 2. Fallback: Status Window
        StatusWindow sw = gameUIManager.getStatusWindow();
        if (sw != null) {
            if (sw.redoItem != null && sw.redoItem.isEnabled()) {
                sw.redoItem.doClick();
            } 
        }
    }

}