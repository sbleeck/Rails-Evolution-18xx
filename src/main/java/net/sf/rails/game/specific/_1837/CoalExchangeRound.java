package net.sf.rails.game.specific._1837;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.JOptionPane;

import net.sf.rails.common.*;
import net.sf.rails.game.*;
import net.sf.rails.game.financial.Bank;
import net.sf.rails.game.state.*;
import rails.game.action.*;

/**
 * @author Martin Brumm
 * @date 2019-01-26
 */
public class CoalExchangeRound extends StockRound_1837 {

    private ArrayListMultimapState<PublicCompany, PublicCompany> coalCompsPerMajor;
    private ArrayListMultimapState<Player, PublicCompany> coalCompsPerPlayer;

    private ArrayListState<PublicCompany> currentMajorOrder;
    private GenericState<PublicCompany> currentMajor;
    private ArrayListState<Player> currentPlayerOrder;

    private HashMultimapState<TrainType, Train> discardableTrains;
    private ArrayListState<PublicCompany> closedMinors;
    private IntegerState numberOfExcessTrains;

    // Track minors explicitly declined (Legacy/Session support)
    private ArrayListState<PublicCompany> skippedMinors;

    // Persistence for UI Decisions
    private ArrayListState<String> recordedMergeChoices;
    private IntegerState mergeChoiceIndex;

    private boolean reachedPhase5;
    private String cerNumber;

    private IntegerState step;
    private static final int MERGE = 1;
    private static final int DISCARD = 2;

    public CoalExchangeRound(GameManager parent, String id) {
        super(parent, id);
        guiHints.setActivePanel(GuiDef.Panel.STATUS);
        raiseIfSoldOut = false;
    }

    public static CoalExchangeRound create(GameManager parent, String id){
        return new CoalExchangeRound(parent, id);
    }

    public void start() {
        String rawId = getId().replaceFirst("CER_(.+)", "$1");
        cerNumber = rawId.endsWith(".0") ? rawId.substring(0, rawId.length() - 2) : rawId;

        coalCompsPerMajor = ArrayListMultimapState.create(this, "CoalsPerMajor_"+getId());
        coalCompsPerPlayer = ArrayListMultimapState.create(this, "CoalsPerPlayer_"+getId());

        currentMajorOrder = new ArrayListState<> (this, "MajorOrder_"+getId());
        currentPlayerOrder = new ArrayListState<>(this, "PlayerOrder_"+getId());
        currentMajor = new GenericState<>(this, "CurrentMajor_"+getId());

        discardableTrains = HashMultimapState.create(this, "NewTrainsPerMajor_"+getId());
        closedMinors = new ArrayListState<>(this, "ClosedMinorsPerMajor_"+getId());
        numberOfExcessTrains = IntegerState.create(this, "NumberOfExcessTrains");
        
        skippedMinors = new ArrayListState<>(this, "SkippedMinors_" + getId());
        recordedMergeChoices = new ArrayListState<>(this, "recordedMergeChoices_" + getId());
        mergeChoiceIndex = IntegerState.create(this, "mergeChoiceIndex_" + getId());

        reachedPhase5 = getRoot().getPhaseManager().hasReachedPhase("5");

        step = IntegerState.create(this, "CERstep");

        String message = "Start of Coal Exchange Round " + cerNumber;
        ReportBuffer.add(this, message);
        
        init();

        if (currentMajorOrder.isEmpty()) {
            finishRound();
        }
    }

    private void init() {
        List<PublicCompany> comps = companyManager.getPublicCompaniesByType("Coal");
        for (PublicCompany comp : comps) {
            if (!comp.isClosed()) {
                PublicCompany major = companyManager
                        .getPublicCompany(comp.getRelatedPublicCompanyName());
                if (major.hasFloated()) {
                    coalCompsPerMajor.put(major, comp);
                    coalCompsPerPlayer.put (comp.getPresident(), comp);
                }
            }
        }
        for (PublicCompany major : setOperatingCompanies("Major")) {
            if (coalCompsPerMajor.containsKey(major)) {
                currentMajorOrder.add(major);
            }
        }
        step.set(MERGE);
    }

    @Override
    public String getOwnWindowTitle() {
        return "Coal Exchange Round " + cerNumber;
    }

    private boolean majorMustMerge (PublicCompany major) {
        return reachedPhase5
                || (ipo.getShares(major) == 0
                && !GameOption.getValue(this, GameOption.VARIANT).equals("Romoth"));
    }

    @Override
    public boolean process (PossibleAction action) {

        if (action instanceof MergeCompanies) {
            // 1. Record YES decision for replay if not reloading
            if (!gameManager.isReloading()) {
                recordedMergeChoices.add("YES");
            }
            // 2. Advance the Wizard Pointer
            mergeChoiceIndex.add(1);
            
            return executeMerge((MergeCompanies) action);

        } else if (action instanceof DiscardTrain) {
            return discardTrain((DiscardTrain) action);

        } else if (action instanceof NullAction
                && ((NullAction)action).getMode() == NullAction.Mode.DONE) {
            
            // "DONE" here typically means we finished the loop or the phase.
            // If it was a manual Skip, we might have handled it in the loop already.
            // But if the engine generated it, we ensure sync.
            return done((NullAction)action, action.getPlayer(), false);
        } else {
            return super.process(action);
        }
    }

    public boolean executeMerge (MergeCompanies action) {
        PublicCompany minor = action.getMergingCompany();
        PublicCompany major = action.getSelectedTargetCompany();
        
        // Safeguard for manual action creation
        if (major == null) {
            List<PublicCompany> targets = action.getTargetCompanies();
            if (targets != null && !targets.isEmpty()) {
                major = targets.get(0);
            }
        }
        
        return executeMerge(minor, major, false);
    }

    public boolean executeMerge (PublicCompany minor, PublicCompany major, boolean autoMerge) {
        for (Train train : minor.getPortfolioModel().getTrainList()) {
            discardableTrains.put (train.getType(), train);
        }

        boolean result = mergeCompanies(minor, major,false, autoMerge);
        closedMinors.add (minor);
        coalCompsPerMajor.remove (major, minor);
        major.checkPresidency();

        if (result) {
            coalCompsPerPlayer.remove(currentPlayer, minor);
            if (coalCompsPerPlayer.get(currentPlayer).isEmpty()) {
                if (nextPlayer()) {
                    return result;
                } else if (checkForExcessTrains()) {
                    step.set(DISCARD);
                } else if (!nextMajorCompany()) {
                    finishRound();
                } else {
                    step.set(MERGE);
                }
            }
        }
        return result;
    }

    private boolean checkForExcessTrains () {
        PublicCompany major = currentMajor.value();
        int excess = major.getNumberOfTrains() - major.getCurrentTrainLimit();
        int maxExcessFromMerger = discardableTrains.values().size();
        int excessFromMerger = Math.min(excess, maxExcessFromMerger);
        if (excessFromMerger <= 0) {
            step.set(MERGE);
            return false;
        }
        numberOfExcessTrains.set(excessFromMerger);

        if (discardableTrains.keySet().size() > 1) return true;

        List<Train> trains = discardableTrains.values().asList();

        for (int i=0; i<excessFromMerger; i++) {
            Train train = trains.get(i);
            train.discard();
            DisplayBuffer.add (this, LocalText.getText(
                    "CompanyDiscardsTrain", major,
                    train.getType(), pool));
        }
        clearDiscardableTrains();
        numberOfExcessTrains.set(0);
        return false;
    }

    @Override
    public boolean discardTrain (DiscardTrain action) {
        boolean result;
        result = super.discardTrain(action);

        if (action.getDiscardedTrain() != null) {
            discardableTrains.remove(action.getDiscardedTrain().getType(),
                    action.getDiscardedTrain());
            numberOfExcessTrains.add(-1);
        }

        if (numberOfExcessTrains.value() == 0) {
            if (!nextMajorCompany()) {
                finishRound();
            } else {
                step.set(MERGE);
            }
        }
        return result;
    }

    private void clearDiscardableTrains() {
        for (TrainType type : discardableTrains.keySet()) {
            discardableTrains.removeAll(type);
        }
    }

    public boolean done(NullAction action, Player player, boolean hasAutopassed) {
        List<PublicCompany> remainingMinors = coalCompsPerMajor.get(currentMajor.value());
        if (!remainingMinors.isEmpty()) {
            List<PublicCompany> rejectedMinors = new ArrayList<>(2);
            for (PublicCompany minor : remainingMinors) {
                if (player == minor.getPresident()) rejectedMinors.add (minor);
            }
            if (!rejectedMinors.isEmpty()) {
                 // Log if needed
            }
        }

        currentPlayerOrder.remove(currentPlayer);

        if (currentPlayerOrder.isEmpty()) {
            currentMajorOrder.remove(currentMajor.value());
            if (!closedMinors.isEmpty() && checkForExcessTrains()) {
                step.set(DISCARD);
            } else if (!nextMajorCompany()){
                finishRound();
            }
        } else {
            nextPlayer();
        }
        return true;
    }

    @Override
    public boolean setPossibleActions() {
        if (step.value() == MERGE) {
             if (setMinorMergeActions()) return true; 
        } 
        
        if (step.value() == DISCARD
                && checkForExcessTrains()
                && setTrainDiscardActions()) { 
            return true;
        } else {
            return super.setPossibleActions();
        }
    }

    private boolean setMinorMergeActions() {

        // Safety: Ensure index is within bounds
        if (mergeChoiceIndex.value() > recordedMergeChoices.size()) {
            mergeChoiceIndex.set(recordedMergeChoices.size());
        }

        // --- WIZARD LOOP ---
        // Loops until it finds a "YES" (creates action) or runs out of players/options.
        // If "NO", it records locally and continues immediately to avoid extra clicks.
        
        while (nextPlayer()) {
            
            Player player = currentPlayer;
            PublicCompany major = currentMajor.value();
            List<PublicCompany> candidates = coalCompsPerMajor.get(major);
            
            if (candidates != null) {
                for (PublicCompany minor : candidates) {
                    if (player == minor.getPresident() && !minor.isClosed()) {
                        
                        // 1. REPLAY CHECK
                        String choice = null;
                        if (mergeChoiceIndex.value() < recordedMergeChoices.size()) {
                            choice = recordedMergeChoices.get(mergeChoiceIndex.value());
                        }

                        // 2. ASK USER (If no history)
                        if (choice == null) {
                            if (gameManager.isReloading()) return false; 

                            String msg = "<html><b>" + player.getName() + "</b> can merge minor " 
                                       + minor.getId() + " with " + major.getId() + "?</html>";

                            int response = JOptionPane.showConfirmDialog(
                                    null, msg, "Voluntary Exchange", 
                                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE
                            );
                            
                            choice = (response == JOptionPane.YES_OPTION) ? "YES" : "NO";
                        }

                        // 3. HANDLE CHOICE
                        if ("YES".equals(choice)) {
                             // "YES": Return the action. The UI will stop and show the button (or auto-exec).
                             // We wait for process() to advance the index.
                             possibleActions.add(new MergeCompanies(minor, major, false));
                             return true; 
                        } else {
                             // "NO": Skip immediately.
                             // We must record this "NO" now because we are NOT returning an action.
                             // This effectively executes the "Skip" silently.
                             
                             if (!gameManager.isReloading()) {
                                 // Only add if not already there (though the index check above handles iteration)
                                 if (mergeChoiceIndex.value() >= recordedMergeChoices.size()) {
                                     recordedMergeChoices.add("NO");
                                 }
                             }
                             
                             // Advance index locally to skip this entry
                             mergeChoiceIndex.add(1);
                             
                             // Continue inner loop -> next candidate
                             continue;
                        }
                    }
                }
            }
            // If no candidates found for this player (or all skipped), loop continues to nextPlayer()
        }

        // Loop finished = No more candidates/players
        possibleActions.add(new NullAction(getRoot(), NullAction.Mode.DONE));
        return true;
     }

    private boolean nextPlayer() {
        PublicCompany major = currentMajor.value();
        if (currentPlayerOrder.isEmpty()) {
            if (major != null) {
                currentMajorOrder.remove(major);
            }
            return nextMajorCompany();
        } else {
            Player nextPlayer = currentPlayerOrder.get(0);
            setCurrentPlayer(nextPlayer);
            return true;
        }
    }

    private boolean nextMajorCompany () {
        currentPlayerOrder.clear();
        closedMinors.clear();

        while (true) {
            if (currentMajorOrder.isEmpty()) {
                return false;
            } else {
                PublicCompany major = currentMajorOrder.get(0);
                currentMajor.set(major);
                Player president = major.getPresident();
                clearDiscardableTrains();

                if (majorMustMerge(major)) {
                    currentPlayer = null; 
                    for (PublicCompany minor : coalCompsPerMajor.get(major)) {
                        for (Train train : minor.getPortfolioModel().getTrainList()) {
                            discardableTrains.put(train.getType(), train);
                        }
                        DisplayBuffer.add(this,
                                LocalText.getText("AutoMergeMinorLog",
                                        minor, major,
                                        Bank.format(this, minor.getCash()),
                                        minor.getPortfolioModel().getTrainList().size()));
                        mergeCompanies(minor, major, false, true);
                        closedMinors.add(minor);
                    }
                    major.checkPresidency();
                    currentMajorOrder.remove(major);
                    if (!closedMinors.isEmpty() && checkForExcessTrains()) {
                        step.set(DISCARD);
                        return false;
                    } else if (currentMajorOrder.isEmpty()) {
                        if (reachedPhase5) finishRound(); 
                        return false;
                    } else  {
                        continue;
                    }

                } else {
                    List<PublicCompany> coalCompanies = coalCompsPerMajor.get(major);
                    for (Player player : playerManager.getNextPlayersAfter(
                            president, true, false)) {
                        for (PublicCompany coalComp : coalCompanies) {
                            if (!coalComp.isClosed() && player == coalComp.getPresident()) {
                                currentPlayerOrder.add(player);
                                break;
                            }
                        }
                    }
                    return nextPlayer();
                }
            }
        }
    }

    protected boolean setTrainDiscardActions() {
        PublicCompany major = currentMajor.value();
        Set<Train> trains = java.util.Collections.emptySet(); 
        if (!discardableTrains.isEmpty()) {
             java.util.Set<Train> tSet = new java.util.HashSet<>();
             for (TrainType type : discardableTrains.keySet()) {
                tSet.add(discardableTrains.get(type).asList().get(0));
             }
             trains = tSet;
        }
        
        possibleActions.add(new DiscardTrain(major, trains, true));

        discardingTrains.set(true);
        if (discardingCompanies == null) discardingCompanies = new PublicCompany[4];
        discardingCompanies[discardingCompanyIndex.value()] = major;
        discardingCompanyIndex.add(1);

        return true;
    }

    @Override
    protected void initPlayer() {  
        currentPlayer = playerManager.getCurrentPlayer();
        hasActed.set(false);
    }

    @Override
    protected void finishRound() {
        ReportBuffer.add(this, " ");
        ReportBuffer.add(this, LocalText.getText("EndOfCoalExchangeRound", cerNumber));
        gameManager.nextRound(this);
    }

    @Override
    public String toString() {
        return getId();
    }
}