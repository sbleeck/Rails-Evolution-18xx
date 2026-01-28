package net.sf.rails.game.specific._1837;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(CoalExchangeRound.class);

    // Static map to persist skipped companies across round re-starts
    private static Map<String, String> skippedCoalCompanies = new HashMap<>();

    // --- FIX: Custom Action to handle Resets ---
    private static class ResetSkipsAction extends PossibleAction implements GuiTargetedAction {
        private static final long serialVersionUID = 1L;

        public ResetSkipsAction(RailsRoot root) {
            super(root);
        }

        @Override
        public Owner getActor() { return null; }

        @Override
        public String getGroupLabel() { return "Reset"; }

        @Override
        public String getButtonLabel() { return "Reset Skips & Retry"; }

        @Override
        public java.awt.Color getButtonColor() { return new java.awt.Color(255, 228, 225); }

        @Override
        public java.awt.Color getHighlightBackgroundColor() { return new java.awt.Color(255, 228, 225); }

        @Override
        public java.awt.Color getHighlightBorderColor() { return java.awt.Color.RED; }
        
        @Override
        public java.awt.Color getHighlightTextColor() { return java.awt.Color.BLACK; }

        @Override
        public boolean equalsAs(PossibleAction pa, boolean asOption) {
            return pa instanceof ResetSkipsAction;
        }
        
        @Override
        public String toString() { return "Reset Skips & Retry"; }
    }

    private ArrayListMultimapState<PublicCompany, PublicCompany> coalCompsPerMajor;
    private ArrayListMultimapState<Player, PublicCompany> coalCompsPerPlayer;

    private ArrayListState<PublicCompany> currentMajorOrder;
    private GenericState<PublicCompany> currentMajor;
    private ArrayListState<Player> currentPlayerOrder;

    private HashMultimapState<TrainType, Train> discardableTrains;
    private ArrayListState<PublicCompany> closedMinors;
    private IntegerState numberOfExcessTrains;

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
        log.info("CER_DEBUG: Starting Coal Exchange Round {}", cerNumber);

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

        ReportBuffer.add(this, "Start of Coal Exchange Round " + cerNumber);
        
        init();
        
        if (currentMajorOrder.isEmpty()) {
            log.info("CER_DEBUG: No majors found in init(), finishing round immediately.");
            finishRound();
        }
    }

private void init() {
        log.info("CER_DEBUG: Executing init()...");
        step.set(MERGE);

        List<PublicCompany> comps = companyManager.getPublicCompaniesByType("Coal");
        for (PublicCompany comp : comps) {
            if (!comp.isClosed()) {
                
                String skippedInRound = skippedCoalCompanies.get(comp.getId());
                if (getId().equals(skippedInRound)) {
                    // --- FIX: Use getId() instead of getName() ---
                    log.info("CER_DEBUG: Skipping {} (already skipped in this round)", comp.getId());
                    continue;
                }
                
                PublicCompany major = companyManager
                        .getPublicCompany(comp.getRelatedPublicCompanyName());
                
                if (major.hasFloated()) {
                    if (!coalCompsPerMajor.containsEntry(major, comp)) {
                        coalCompsPerMajor.put(major, comp);
                        log.info("CER_DEBUG: Mapped {} to Major {}", comp.getId(), major.getId());
                    } else {
                        log.warn("CER_DEBUG: DUPLICATE DETECTED! {} -> {} was already mapped.", comp.getId(), major.getId());
                    }

                    if (!coalCompsPerPlayer.containsEntry(comp.getPresident(), comp)) {
                        coalCompsPerPlayer.put(comp.getPresident(), comp);
                    }
                }
            }
        }
        
        for (PublicCompany major : setOperatingCompanies("Major")) {
            if (coalCompsPerMajor.containsKey(major)) {
                if (!currentMajorOrder.contains(major)) {
                    currentMajorOrder.add(major);
                    log.info("CER_DEBUG: Added Major {} to Order.", major.getId());
                } else {
                    log.warn("CER_DEBUG: DUPLICATE MAJOR! {} is already in order list.", major.getId());
                }
            }
        }
        log.info("CER_DEBUG: Init complete. Major Order Size: {}", currentMajorOrder.size());
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
        log.info("CER_DEBUG: Processing Action: {}", action.getClass().getSimpleName());

        if (action instanceof ExchangeCoalAction) {
            ExchangeCoalAction exc = (ExchangeCoalAction) action;
            log.info("CER_DEBUG: Exchanging {} into {}", exc.getCoalCompany().getId(), exc.getTargetMajor().getId());
            executeMerge(exc.getCoalCompany(), exc.getTargetMajor(), false);
            setPossibleActions(); 
            return true;
        }

        if (action instanceof MergeCompanies) {
            if (!gameManager.isReloading()) {
                recordedMergeChoices.add("YES");
            }
            mergeChoiceIndex.add(1);
            
            PublicCompany minor = ((MergeCompanies)action).getMergingCompany();
            PublicCompany major = ((MergeCompanies)action).getSelectedTargetCompany();
             if (major == null && ((MergeCompanies)action).getTargetCompanies() != null) {
                major = ((MergeCompanies)action).getTargetCompanies().get(0);
            }
            log.info("CER_DEBUG: MergeCompanies Action {} -> {}", minor.getId(), major.getId());
            executeMerge(minor, major, false);
            setPossibleActions();
            return true;

        } else if (action instanceof DiscardTrain) {
            return discardTrain((DiscardTrain) action);

        } else if (action instanceof ResetSkipsAction) {
            log.info("CER_DEBUG: Resetting Skips requested by user.");
            skippedCoalCompanies.clear();
            skippedMinors.clear();
            
            if (currentMajor.value() != null) {
                populatePlayersForMajor(currentMajor.value());
            }
            
            setPossibleActions();
            return true;
            
        } else if (action instanceof NullAction
                && ((NullAction)action).getMode() == NullAction.Mode.DONE) {
            
            log.info("CER_DEBUG: Player {} clicked Done.", action.getPlayer().getName());
            return done((NullAction)action, action.getPlayer(), false);
        } else {
            return super.process(action);
        }
    }

    public boolean executeMerge (PublicCompany minor, PublicCompany major, boolean autoMerge) {
        
        for (Train train : minor.getPortfolioModel().getTrainList()) {
            discardableTrains.put (train.getType(), train);
        }

        boolean result = mergeCompanies(minor, major, false, autoMerge);
        
        closedMinors.add (minor);
        coalCompsPerMajor.remove (major, minor);
        coalCompsPerPlayer.remove(currentPlayer, minor);
        
        major.checkPresidency();
        log.info("CER_DEBUG: Merge complete. Minor {} closed.", minor.getId());

        return result;
    }

    private boolean checkForExcessTrains () {
        PublicCompany major = currentMajor.value();
        if (major == null) return false;

        int excess = major.getNumberOfTrains() - major.getCurrentTrainLimit();
        int maxExcessFromMerger = discardableTrains.values().size();
        int actualExcess = Math.min(excess, maxExcessFromMerger);
        
        log.info("CER_DEBUG: Checking Trains for {}. Excess={}, FromMerger={}, Result={}", 
                major.getId(), excess, maxExcessFromMerger, actualExcess);

        if (actualExcess <= 0) {
            clearDiscardableTrains(); 
            step.set(MERGE);
            return false;
        }
        
        numberOfExcessTrains.set(actualExcess);
        return true; 
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

        if (numberOfExcessTrains.value() <= 0) {
            clearDiscardableTrains(); 
            step.set(MERGE);
            if (!nextMajorCompany()) {
                finishRound();
            }
        } else {
             setPossibleActions();
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
        
        if (currentMajor != null && currentMajor.value() != null) {
            PublicCompany major = currentMajor.value();
            List<PublicCompany> potentialMinors = coalCompsPerMajor.get(major);
            
            if (potentialMinors != null) {
                for (PublicCompany minor : potentialMinors) {
                    if (minor.getPresident() == p) {
                        log.info("CER_DEBUG: SKIP REGISTERED. Player {} skips minor {}", p.getName(), minor.getId());
                        skippedCoalCompanies.put(minor.getId(), getId());
                        
                        if (!skippedMinors.contains(minor)) {
                            skippedMinors.add(minor);
                        }
                    }
                }
            }
        }

        if (currentPlayerOrder != null) {
            boolean removed = currentPlayerOrder.remove(p);
            log.info("CER_DEBUG: Removed player {} from current queue. Success={}", p.getName(), removed);
        }

        return nextPlayer();
    }



@Override
    public boolean setPossibleActions() {
        possibleActions.clear(); 

        if (currentMajorOrder.isEmpty()) {
             log.info("CER_DEBUG: No majors left. Finishing round.");
             finishRound();
             return true;
        }

        if (step.value() == MERGE) {
             if (setMinorMergeActions()) return true; 
        } 
        
        if (step.value() == DISCARD) {
             log.info("CER_DEBUG: Setting Train Discard Actions.");
             setTrainDiscardActions();
             return true;
        } 
        
        possibleActions.add(new ResetSkipsAction(getRoot()));
        
        // --- FIX: Removed .size() call to prevent compilation error ---
        log.info("CER_DEBUG: Actions have been set."); 

        return super.setPossibleActions();
    }




    private boolean setMinorMergeActions() {
        
        if (currentPlayer == null || !currentPlayerOrder.contains(currentPlayer)) {
            log.info("CER_DEBUG: Current player is null or not in order. Moving to next player.");
            if (!nextPlayer()) {
                return true; 
            }
        }

        PublicCompany major = currentMajor.value();
        List<PublicCompany> candidates = coalCompsPerMajor.get(major);
        boolean actionsAdded = false;

        log.info("CER_DEBUG: Setting actions for Player {} regarding Major {}", 
                currentPlayer.getName(), major.getId());

        if (candidates != null) {
            for (PublicCompany minor : candidates) {
                if (skippedMinors.contains(minor)) {
                    log.info("CER_DEBUG: Ignoring {} (in skippedMinors)", minor.getId());
                    continue;
                }

                if (currentPlayer == minor.getPresident() && !minor.isClosed()) {
                    possibleActions.add(new ExchangeCoalAction(minor, major));
                    log.info("CER_DEBUG: Added exchange action for {}", minor.getId());
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
        } else {
            log.info("CER_DEBUG: No actions added for player. Auto-skipping.");
            done(null, currentPlayer, true);
            return true;
        }
    }

    private boolean nextPlayer() {
        log.info("CER_DEBUG: nextPlayer() called.");
        
        if (currentPlayerOrder != null && !currentPlayerOrder.isEmpty()) {
            Player next = currentPlayerOrder.get(0);
            log.info("CER_DEBUG: Switching to next player: {}", next.getName());
            setCurrentPlayer(next);
            setPossibleActions();
            return true;
        }
        
        log.info("CER_DEBUG: No players left for this major. Checking excess trains.");
        if (checkForExcessTrains()) {
            step.set(DISCARD);
            setPossibleActions(); 
            return true;
        }

        return nextMajorCompany();
    }

    // --- FIX: Helper Method to populate players while respecting skips ---
    private void populatePlayersForMajor(PublicCompany major) {
        currentPlayerOrder.clear();
        Player president = major.getPresident();
        List<PublicCompany> coalCompanies = coalCompsPerMajor.get(major);
        
        log.info("CER_DEBUG: Populating players for Major {}. Candidates total: {}", 
                major.getId(), (coalCompanies == null ? 0 : coalCompanies.size()));
        
        if (coalCompanies != null && !coalCompanies.isEmpty()) {
            for (Player player : playerManager.getNextPlayersAfter(
                    president, true, false)) {
                
                boolean hasEligibleCompany = false;
                
                for (PublicCompany coalComp : coalCompanies) {
                    // FIX: CRITICAL LOOP PREVENTION CHECK
                    boolean isSkipped = skippedMinors.contains(coalComp);
                    
                    if (!isSkipped && !coalComp.isClosed() && player == coalComp.getPresident()) {
                        hasEligibleCompany = true;
                        break; 
                    } else {
                        if (isSkipped && player == coalComp.getPresident()) {
                            log.info("CER_DEBUG: Player {} excluded because {} is skipped.", player.getName(), coalComp.getId());
                        }
                    }
                }

                if (hasEligibleCompany) {
                    if (!currentPlayerOrder.contains(player)) {
                        currentPlayerOrder.add(player);
                        log.info("CER_DEBUG: Player {} added to queue.", player.getName());
                    }
                }
            }
        }
    }

    private boolean nextMajorCompany () {
        currentPlayerOrder.clear();
        closedMinors.clear();
        
        if (currentMajor.value() != null) {
            currentMajorOrder.remove(currentMajor.value());
        }

        while (true) {
            if (currentMajorOrder.isEmpty()) {
                log.info("CER_DEBUG: Major Order Empty. Finishing.");
                finishRound();
                return false;
            } else {
                PublicCompany major = currentMajorOrder.get(0);
                currentMajor.set(major);
                log.info("CER_DEBUG: Selected Next Major: {}", major.getId());
                
                clearDiscardableTrains();
                numberOfExcessTrains.set(0);
                step.set(MERGE);

                if (majorMustMerge(major)) {
                    log.info("CER_DEBUG: Mandatory Merge for {}", major.getId());
                    currentPlayer = null; 
                    List<PublicCompany> minors = coalCompsPerMajor.get(major);
                    
                    if (minors != null) {
                        for (PublicCompany minor : new ArrayList<>(minors)) {
                            executeMerge(minor, major, true);
                        }
                    }
                    
                    if (checkForExcessTrains()) {
                        step.set(DISCARD);
                        setPossibleActions();
                        return true; 
                    } else {
                        currentMajorOrder.remove(major);
                        continue;
                    }

                } else {
                    log.info("CER_DEBUG: Optional Merge. Populating players.");
                    populatePlayersForMajor(major);

                    if (currentPlayerOrder.isEmpty()) {
                        log.info("CER_DEBUG: No players for {}. Skipping.", major.getId());
                        currentMajorOrder.remove(major);
                        continue;
                    } else {
                        return nextPlayer();
                    }
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
                tSet.addAll(discardableTrains.get(type));
             }
             trains = tSet;
        }
        
        setCurrentPlayer(major.getPresident());
        
        DiscardTrain dt = new DiscardTrain(major, trains, true);
        possibleActions.add(dt);

        discardingTrains.set(true);
        if (discardingCompanies == null) discardingCompanies = new PublicCompany[4];
        discardingCompanies[discardingCompanyIndex.value()] = major;
        
        return true;
    }

    @Override
    protected void initPlayer() {  
        // Managed manually
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