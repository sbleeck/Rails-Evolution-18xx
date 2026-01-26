package net.sf.rails.game.specific._1837;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

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

    // --- FIX: Static map to persist skipped companies across round re-starts ---
    private static Map<String, String> skippedCoalCompanies = new HashMap<>();

    // --- FIX: Custom Action to handle Resets without using getLabel() ---
private static class ResetSkipsAction extends PossibleAction implements GuiTargetedAction {
        private static final long serialVersionUID = 1L;

        public ResetSkipsAction(RailsRoot root) {
            super(root);
        }

        @Override
        public Owner getActor() {
            return null; 
        }

        @Override
        public String getGroupLabel() {
            return "Reset";
        }

        @Override
        public String getButtonLabel() {
            return "Reset Skips & Retry";
        }

        // --- START FIX ---
        // UNIFIED "SYSTEM ALERT" SIGNATURE (Misty Rose / Red)
        
        @Override
        public java.awt.Color getButtonColor() {
            return new java.awt.Color(255, 228, 225); // MistyRose
        }

        @Override
        public java.awt.Color getHighlightBackgroundColor() {
            return new java.awt.Color(255, 228, 225); // MistyRose
        }

        @Override
        public java.awt.Color getHighlightBorderColor() {
            return java.awt.Color.RED;
        }
        
        @Override
        public java.awt.Color getHighlightTextColor() {
            return java.awt.Color.BLACK;
        }
        // --- END FIX ---

        @Override
        public boolean equalsAs(PossibleAction pa, boolean asOption) {
            return pa instanceof ResetSkipsAction;
        }
        
        @Override
        public String toString() {
            return "Reset Skips & Retry";
        }
    }
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
        
        // --- FIX: Prevent tight loop freeze ---
        if (currentMajorOrder.isEmpty()) {
            // Check if we have skips that caused this emptiness
            boolean hasSkips = false;
            for (String val : skippedCoalCompanies.values()) {
                if (getId().equals(val)) { hasSkips = true; break; }
            }
            
            if (!hasSkips) {
                finishRound();
            } else {
                System.out.println("CER_DEBUG: Round empty due to skips. Staying open to prevent freeze.");
            }
        }
    }

    private void init() {
        List<PublicCompany> comps = companyManager.getPublicCompaniesByType("Coal");
        for (PublicCompany comp : comps) {
            if (!comp.isClosed()) {
                
                // --- FIX: Check static persistence for skips ---
                String skippedInRound = skippedCoalCompanies.get(comp.getId());
                if (getId().equals(skippedInRound)) {
                    continue;
                }
                
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

        if (action instanceof ExchangeCoalAction) {
            ExchangeCoalAction exc = (ExchangeCoalAction) action;
            return executeMerge(exc.getCoalCompany(), exc.getTargetMajor(), false);
        }

        if (action instanceof MergeCompanies) {
            if (!gameManager.isReloading()) {
                recordedMergeChoices.add("YES");
            }
            mergeChoiceIndex.add(1);
            return executeMerge((MergeCompanies) action);

        } else if (action instanceof DiscardTrain) {
            return discardTrain((DiscardTrain) action);

        // --- FIX: Handle Reset using custom class ---
        } else if (action instanceof ResetSkipsAction) {
            skippedCoalCompanies.clear();
            skippedMinors.clear();
            init(); // Re-initialize to find companies
            setPossibleActions();
            return true;
            
        } else if (action instanceof NullAction
                && ((NullAction)action).getMode() == NullAction.Mode.DONE) {
            
            return done((NullAction)action, action.getPlayer(), false);
        } else {
            return super.process(action);
        }
    }

    public boolean executeMerge (MergeCompanies action) {
        PublicCompany minor = action.getMergingCompany();
        PublicCompany major = action.getSelectedTargetCompany();
        
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
            
            // --- FIX: Logic to check if player has more companies for THIS major ---
            boolean hasMoreForCurrentMajor = false;
            
            List<PublicCompany> coalForThisMajor = coalCompsPerMajor.get(major);
            if (coalForThisMajor != null && coalCompsPerPlayer.containsKey(currentPlayer)) {
                for (PublicCompany c : coalCompsPerPlayer.get(currentPlayer)) {
                    if (coalForThisMajor.contains(c)) {
                        hasMoreForCurrentMajor = true;
                        break;
                    }
                }
            }

            if (!hasMoreForCurrentMajor) {
                boolean removed = currentPlayerOrder.remove(currentPlayer);

                if (nextPlayer()) {
                    return result;
                } else if (checkForExcessTrains()) {
                    step.set(DISCARD);
                } else if (!nextMajorCompany()) {
                    finishRound();
                } else {
                    step.set(MERGE);
                }
            } else {
                setPossibleActions();
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
        
        Player p = (player != null) ? player : currentPlayer;
        
        // --- FIX: Record skips in Static Map ---
        if (currentMajor != null && currentMajor.value() != null) {
            PublicCompany major = currentMajor.value();
            List<PublicCompany> potentialMinors = coalCompsPerMajor.get(major);
            if (potentialMinors != null) {
                for (PublicCompany minor : potentialMinors) {
                    if (minor.getPresident() == p) {
                        skippedCoalCompanies.put(minor.getId(), getId());
                        if (!skippedMinors.contains(minor)) {
                            skippedMinors.add(minor);
                        }
                    }
                }
            }
        }

        if (currentPlayerOrder != null) currentPlayerOrder.remove(currentPlayer);

        if (currentPlayerOrder == null || currentPlayerOrder.isEmpty()) {
            if (currentMajor != null && currentMajor.value() != null) 
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
        possibleActions.clear(); 

        // --- FIX: If Major Order is empty but we are here, it's the Loop Protection ---
        if (currentMajorOrder.isEmpty()) {
            // Add a forced pass to prevent UI freeze
            NullAction na = new NullAction(getRoot(), NullAction.Mode.DONE);
            na.setLabel("Pass (Enforced)");
            na.setPlayer(currentPlayer != null ? currentPlayer : playerManager.getCurrentPlayer());
            possibleActions.add(na);
            
            // Add Escape Hatch using ResetSkipsAction
            possibleActions.add(new ResetSkipsAction(getRoot()));
            return true;
        }

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

        if (currentPlayer == null) {
            if (!nextPlayer()) {
                finishRound(); 
                return false; 
            }
        }

        while (currentPlayer != null) {
            
            PublicCompany major = currentMajor.value();
            List<PublicCompany> candidates = coalCompsPerMajor.get(major);
            boolean actionsAdded = false;

            if (candidates != null) {
                for (PublicCompany minor : candidates) {
                    
                    if (skippedMinors.contains(minor)) continue;

                    if (currentPlayer == minor.getPresident() && !minor.isClosed()) {
                        possibleActions.add(new ExchangeCoalAction(minor, major));
                        actionsAdded = true;
                    }
                }
            }

            if (actionsAdded) {
                NullAction na = new NullAction(getRoot(), NullAction.Mode.DONE);
                na.setLabel("Done / Pass");
                na.setPlayer(currentPlayer); 
                possibleActions.add(na);
                return true; 
            }

            currentPlayerOrder.remove(currentPlayer);
            
            if (!nextPlayer()) {
                finishRound();
                return false; 
            }
            
        }
        return false;
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
                        finishRound(); 
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