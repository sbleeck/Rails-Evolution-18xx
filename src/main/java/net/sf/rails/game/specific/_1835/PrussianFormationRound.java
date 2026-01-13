package net.sf.rails.game.specific._1835;

import java.awt.Window;
import java.util.List;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.rails.common.LocalText;
import net.sf.rails.common.ReportBuffer;
import net.sf.rails.game.GameManager;
import net.sf.rails.game.Player;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.Round;
import net.sf.rails.game.financial.PublicCertificate;
import net.sf.rails.game.round.I_MapRenderableRound;
import net.sf.rails.game.state.StringState;
import net.sf.rails.game.state.IntegerState;

import rails.game.action.NullAction;
import rails.game.action.PossibleAction;
import rails.game.specific._1835.StartPrussian;
import rails.game.specific._1835.ExchangeForPrussianShare;


public class PrussianFormationRound extends Round implements I_MapRenderableRound {

    private static final Logger log = LoggerFactory.getLogger(PrussianFormationRound.class);

    public static final String ID = "PrussianFormationRound";
    public static final String PR_ID = "PR";
    public static final String M2_ID = "M2";

    protected PublicCompany prussian;
    protected PublicCompany m2;
    

protected final StringState firstMergePlayerName = StringState.create(this, "FirstMergePlayerName");
    protected final IntegerState stepState = IntegerState.create(this, "StepState");
        // FIX: Local current player tracking since parent Round/GameManager setters are not visible/existing
    private Player currentPlayer;

    protected enum Step {
        START, MERGE, FORM
    }

    public PrussianFormationRound(GameManager gameManager) {
        super(gameManager, ID);
    }

    // The engine requires a constructor that accepts both the manager and the instance name
    public PrussianFormationRound(GameManager gameManager, String roundName) {
        super(gameManager, roundName);
    }


    private Step getStep() {
        return Step.values()[stepState.value()];
    }

    private void setStep(Step s) {
        stepState.set(s.ordinal());
    }

    // FIX: Added public method required by ORUIManager
    public String getPrussianStep() {
        return getStep().toString();
    }

    // FIX: Local accessor for current player
    public Player getCurrentPlayer() {
        return this.currentPlayer;
    }

    // FIX: Local setter for current player
    public void setCurrentPlayer(Player p) {
        this.currentPlayer = p;
    }

    public static boolean prussianIsComplete(GameManager gameManager) {
        return false; 
    }

    public void start() {
        this.prussian = companyManager.getPublicCompany(PR_ID);
        this.m2 = companyManager.getPublicCompany(M2_ID);
        
        System.out.println("[PFR] start() called.");

        if (stepState.value() == 0 && getStep() != Step.START) {
            setStep(Step.START);
        }

        Player m2Pres = (m2 != null) ? m2.getPresident() : null;
        if (m2Pres != null) {
// Set local reference
            setCurrentPlayer(m2Pres);
            
            // Synchronize the GameManager's actor state.
            // In this version, we must set the player in the PlayerManager 
            // to ensure GameManager.process() validates the correct actor.
            if (gameManager.getRoot() != null && gameManager.getRoot().getPlayerManager() != null) {
                gameManager.getRoot().getPlayerManager().setCurrentPlayer(m2Pres);
                System.out.println("[PFR] Synchronized PlayerManager to: " + m2Pres.getName());
            }
        }

        setPossibleActions();
        
        // CRITICAL: Force UI to wake up
        triggerUIUpdate();

        try {
            ReportBuffer.add(this, "Prussian Formation Round started");
        } catch (Exception e) { }
    }

    @Override
    public boolean setPossibleActions() {
        possibleActions.clear();
        Step currentStep = getStep();
        Player curr = getCurrentPlayer(); // Uses local getter

        System.out.println("[PFR] setPossibleActions called. Step=" + currentStep + ", Player=" + (curr!=null ? curr.getName() : "null"));

        if (currentStep == Step.START) {
            if (m2 != null && m2.getPresident() == curr) {
                possibleActions.add(new StartPrussian(m2));
                possibleActions.add(new NullAction(gameManager.getRoot(), NullAction.Mode.PASS)); 
            }
        } 
        else if (currentStep == Step.MERGE) {
            if (curr != null) {
                // Add Pass/Done action to allow cycling
                possibleActions.add(new NullAction(gameManager.getRoot(), NullAction.Mode.DONE));
                // Add Exchange action (logic handled by action class)
                if (m2 != null) {
                    possibleActions.add(new ExchangeForPrussianShare(m2));
                }
            }
        }
        return !possibleActions.isEmpty();
    }

    @Override
    public boolean process(PossibleAction action) {
        System.out.println("[PFR] Processing action: " + action.getClass().getSimpleName());

        if (action instanceof StartPrussian) {
            executeStartPrussian((StartPrussian) action);
            return true;
        }

        if (getStep() == Step.MERGE) {
            boolean cycle = false;

            if (action instanceof ExchangeForPrussianShare) {
                cycle = true;
            } 
            else if (action instanceof NullAction) {
                System.out.println("[PFR] Player " + getCurrentPlayer().getName() + " passed exchange.");
                cycle = true;
            }

            if (cycle) {
                cycleToNextPlayer();
                return true;
            }
        }

        return false;
    }

    private void executeStartPrussian(StartPrussian action) {
        System.out.println("[PFR] Executing StartPrussian for " + action.getPlayerName());
        
        Player m2Pres = getCurrentPlayer();
        
        firstMergePlayerName.set(m2Pres.getName());
        setStep(Step.MERGE);
        
        System.out.println("[PFR] Transitioning to MERGE. First Player: " + m2Pres.getName());
        setPossibleActions(); 
    }

    // FIX: Robust manual player cycling that doesn't rely on hidden PlayerManager state
    private void cycleToNextPlayer() {
        Player current = getCurrentPlayer();
        Player next = getNextPlayerManual(current); // Calculate next player manually
        
        String startPlayer = firstMergePlayerName.value();

        System.out.println("[PFR] Cycling... Current: " + current.getName() + ", Next: " + next.getName() + ", StartRef: " + startPlayer);

        if (next.getName().equals(startPlayer)) {
            System.out.println("[PFR] Cycle Complete. Moving to FORM step.");
            setStep(Step.FORM);
        } else {
            setCurrentPlayer(next);
            setPossibleActions();
        }
    }

    // Helper to calculate next player from the list
    private Player getNextPlayerManual(Player current) {
        List<Player> players = gameManager.getRoot().getPlayerManager().getPlayers();
        int idx = players.indexOf(current);
        if (idx == -1) return players.get(0); // Fallback
        return players.get((idx + 1) % players.size());
    }

    private void triggerUIUpdate() {
        SwingUtilities.invokeLater(() -> {
            boolean success = false;
            for (Window w : Window.getWindows()) {
                if (w.getClass().getName().contains("StatusWindow")) {
                    try {
                        java.lang.reflect.Method getGS = w.getClass().getMethod("getGameStatus");
                        Object gameStatusObj = getGS.invoke(w);
                        
                        if (gameStatusObj != null) {
                            java.lang.reflect.Method initMethod = null;
                            Class<?> clazz = gameStatusObj.getClass();
                            while (clazz != null && initMethod == null) {
                                try {
                                    initMethod = clazz.getDeclaredMethod("initGameSpecificActions");
                                } catch (NoSuchMethodException e) {
                                    clazz = clazz.getSuperclass();
                                }
                            }
                            
                            if (initMethod != null) {
                                initMethod.setAccessible(true);
                                initMethod.invoke(gameStatusObj);
                                success = true;
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("[PFR-UI] Failed to force update: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    public Class<? extends Round> getRoundType() {
        return this.getClass();
    }
}