package net.sf.rails.game.specific._1837;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.rails.common.*;
import net.sf.rails.game.*;
import net.sf.rails.game.financial.Bank;
import net.sf.rails.game.state.*;
import rails.game.action.*;

/**
 * Coal Exchange Round for 1837.
 * Logic Refactored:
 * Uses a Search-Based State Machine rather than maintaining fragile player queues.
 * Iterates Majors (Operating Order) -> Players (President clockwise) -> Available Coal Companies.
 */
public class CoalExchangeRound extends StockRound_1837 {

    private static final Logger log = LoggerFactory.getLogger(CoalExchangeRound.class);

    private String cerNumber;
    private boolean reachedPhase5;

    // Tracks which Coal Companies have been "Passed" by the player for this specific round instance.
    protected final HashMapState<String, String> skippedCoalCompanies = HashMapState.create(this, "skippedCoalCompanies", new HashMap<String, String>());

    // Maps Major Companies to the list of Coal Companies that want to merge into them.
    // NOTE: ArrayListMultimapState has NO keySet() or view() methods.
    private ArrayListMultimapState<PublicCompany, PublicCompany> coalCompsPerMajor;
    
    // The fixed operating order of majors involved in this round.
    private ArrayListState<PublicCompany> majorOrder; 
    
    // The Major currently being processed.
    private GenericState<PublicCompany> currentMajor;

    // Discard Handling States
    // HashMultimapState extends MultimapState, so it HAS keySet().
    private HashMultimapState<TrainType, Train> discardableTrains;
    private IntegerState numberOfExcessTrains;
    
    // Internal Step Tracking
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

    @Override
    public void start() {
        String rawId = getId().replaceFirst("CER_(.+)", "$1");
        cerNumber = rawId.endsWith(".0") ? rawId.substring(0, rawId.length() - 2) : rawId;
        log.info("CER_DEBUG: Starting Coal Exchange Round {}", cerNumber);

        // Initialize State Containers
        coalCompsPerMajor = ArrayListMultimapState.create(this, "CoalsPerMajor_" + getId());
        majorOrder = new ArrayListState<>(this, "MajorOrder_" + getId());
        currentMajor = new GenericState<>(this, "CurrentMajor_" + getId());

        discardableTrains = HashMultimapState.create(this, "NewTrainsPerMajor_" + getId());
        numberOfExcessTrains = IntegerState.create(this, "NumberOfExcessTrains");
        step = IntegerState.create(this, "CERstep");

        reachedPhase5 = getRoot().getPhaseManager().hasReachedPhase("5");
        
        ReportBuffer.add(this, "Start of Coal Exchange Round " + cerNumber);
        
        init();
        
        // If no majors are involved (no coal companies exist/mapped), end immediately
        if (majorOrder.isEmpty()) {
            log.info("CER_DEBUG: No majors found in init(), finishing round immediately.");
            finishRound();
        } else {
            step.set(MERGE);
            setPossibleActions();
        }
    }

    private void init() {
        log.info("CER_DEBUG: Executing init()...");
        step.set(MERGE);
        
        // 1. Clear previous state manually.
        // ArrayListMultimapState does NOT have keySet(). We must scan possible keys.
        // Since the keys are always Major companies, we iterate over all Majors in the game.
        if (coalCompsPerMajor != null) {
            // FIX: Use getPublicCompaniesByType("Major") because getPublicCompanies() does not exist.
            for (PublicCompany major : companyManager.getPublicCompaniesByType("Major")) {
                if (coalCompsPerMajor.containsKey(major)) {
                    // We must copy the list to avoid ConcurrentModificationException while removing
                    List<PublicCompany> values = new ArrayList<PublicCompany>(coalCompsPerMajor.get(major));
                    for (PublicCompany coal : values) {
                        coalCompsPerMajor.remove(major, coal);
                    }
                }
            }
        }
        
        majorOrder.clear(); 

        // 2. Identify all active Coal Companies and map them to their Majors
        List<PublicCompany> coalComps = companyManager.getPublicCompaniesByType("Coal");
        for (PublicCompany comp : coalComps) {
            
            if (comp.isClosed()) continue;
           
            String majorName = comp.getRelatedPublicCompanyName();
            if (majorName == null) continue;
            
            PublicCompany major = companyManager.getPublicCompany(majorName);

            if (major != null && major.hasFloated()) {
                // Check if mapping already exists to avoid duplicates
                boolean alreadyMapped = false;
                if (coalCompsPerMajor.containsKey(major)) {
                    if (coalCompsPerMajor.get(major).contains(comp)) {
                        alreadyMapped = true;
                    }
                }
                
                if (!alreadyMapped) {
                    coalCompsPerMajor.put(major, comp);
                    log.debug("CER_DEBUG: Mapped Coal {} to Major {}", comp.getId(), major.getId());
                }
            }
        }

        // 3. Build the Major Order (Operating Order)
        List<PublicCompany> operatingMajors = setOperatingCompanies("Major");
        for (PublicCompany major : operatingMajors) {
            if (coalCompsPerMajor.containsKey(major)) {
                if (!majorOrder.contains(major)) {
                    majorOrder.add(major);
                }
            }
        }
        
        log.info("CER_DEBUG: Init complete. Major Order Size: {}", majorOrder.size());
    }

    @Override
    public String getOwnWindowTitle() {
        return "Coal Exchange Round " + cerNumber;
    }

    @Override
    public boolean process(PossibleAction action) {
        log.info("CER_DEBUG: Processing Action: {}", action.getClass().getSimpleName());

        if (action instanceof ExchangeCoalAction) {
            ExchangeCoalAction exc = (ExchangeCoalAction) action;
            log.info("CER_DEBUG: Exchanging {} into {}", exc.getCoalCompany().getId(), exc.getTargetMajor().getId());
            
            executeMerge(exc.getCoalCompany(), exc.getTargetMajor(), false);
            setPossibleActions(); 
            return true;
        }

        else if (action instanceof NullAction && ((NullAction)action).getMode() == NullAction.Mode.DONE) {
            
            Player p = action.getPlayer();
            PublicCompany major = currentMajor.value();
            
            log.info("CER_DEBUG: Player {} clicked Done for Major {}", p != null ? p.getName() : "null", major != null ? major.getId() : "null");

            if (major != null && p != null) {
                List<PublicCompany> potentials = coalCompsPerMajor.get(major);
                if (potentials != null) {
                    for (PublicCompany coal : potentials) {
                        if (coal.getPresident() == p && !coal.isClosed()) {
                            log.info("CER_DEBUG: Marking {} as skipped for this round.", coal.getId());
                            skippedCoalCompanies.put(coal.getId(), getId());
                        }
                    }
                }
            }
            
            setPossibleActions();
            return true;
        }
        
        else if (action instanceof DiscardTrain) {
            return discardTrain((DiscardTrain) action);
        } 
        
        else {
            return super.process(action);
        }
    }
    
    @Override
    public boolean setPossibleActions() {
        possibleActions.clear(); 
        
        if (step.value() == DISCARD || numberOfExcessTrains.value() > 0) {
            return setTrainDiscardActions();
        }

        for (PublicCompany major : majorOrder) {
            
            List<Player> players = gameManager.getPlayers();
            Player president = major.getPresident();
            
            if (president == null) continue;

            int presIndex = players.indexOf(president);
            int totalPlayers = players.size();
            
            for (int i = 0; i < totalPlayers; i++) {
                Player p = players.get((presIndex + i) % totalPlayers);
                
                List<PublicCompany> exchangeableCoals = new ArrayList<>();
                List<PublicCompany> candidates = coalCompsPerMajor.get(major);
                
                if (candidates != null) {
                    for (PublicCompany coal : candidates) {
                        boolean isSkipped = getId().equals(skippedCoalCompanies.get(coal.getId()));
                        
                        if (coal.getPresident() == p && !coal.isClosed() && !isSkipped) {
                            exchangeableCoals.add(coal);
                        }
                    }
                }

                if (!exchangeableCoals.isEmpty()) {
                    currentMajor.set(major);
                    this.currentPlayer = p;
                    getRoot().getPlayerManager().setCurrentPlayer(p);
                    
                    log.info("CER_DEBUG: Found Action. Major={}, Player={}, Coals={}", 
                            major.getId(), p.getName(), exchangeableCoals.size());

                    for (PublicCompany c : exchangeableCoals) {
                        possibleActions.add(new ExchangeCoalAction(c, major));
                    }
                    
                    NullAction pass = new NullAction(getRoot(), NullAction.Mode.DONE);
                    pass.setLabel("Done / Pass");
                    possibleActions.add(pass);
                    
                    return true;
                }
            }
        }

        log.info("CER_DEBUG: No more actions found. Finishing round.");
        finishRound();
        return true;
    }

    public boolean executeMerge(PublicCompany minor, PublicCompany major, boolean autoMerge) {
        
        for (Train train : minor.getPortfolioModel().getTrainList()) {
            discardableTrains.put(train.getType(), train);
        }

        boolean result = mergeCompanies(minor, major, false, autoMerge);
        
        major.checkPresidency();
        log.info("CER_DEBUG: Merge complete. Minor {} closed.", minor.getId());

        return result;
    }

    @Override
    public boolean discardTrain(DiscardTrain action) {
        boolean result = super.discardTrain(action);

        if (action.getDiscardedTrain() != null) {
            discardableTrains.remove(action.getDiscardedTrain().getType(),
                    action.getDiscardedTrain());
            numberOfExcessTrains.add(-1);
        }

        if (numberOfExcessTrains.value() <= 0) {
            clearDiscardableTrains(); 
            step.set(MERGE);
            setPossibleActions();
        } else {
             setPossibleActions();
        }
        return result;
    }

    protected boolean setTrainDiscardActions() {
        PublicCompany major = currentMajor.value();
        Set<Train> trains = new HashSet<>(); 
        
        // HashMultimapState usually has keySet().
        if (discardableTrains != null && !discardableTrains.isEmpty()) {
             // Safe copying to avoid CME. Explicit Generic Type added.
             for (TrainType type : new ArrayList<TrainType>(discardableTrains.keySet())) {
                trains.addAll(discardableTrains.get(type));
             }
        }
        
        setCurrentPlayer(major.getPresident());
        
        DiscardTrain dt = new DiscardTrain(major, trains, true);
        possibleActions.add(dt);

        discardingTrains.set(true);
        if (discardingCompanies == null) discardingCompanies = new PublicCompany[4];
        discardingCompanies[discardingCompanyIndex.value()] = major;
        
        return true;
    }
    
    private void clearDiscardableTrains() {
        // HashMultimapState usually has keySet()
        if (discardableTrains != null && !discardableTrains.isEmpty()) {
            // Explicit Generic Types added
            for (TrainType key : new ArrayList<TrainType>(discardableTrains.keySet())) {
                for (Train val : new ArrayList<Train>(discardableTrains.get(key))) {
                    discardableTrains.remove(key, val);
                }
            }
        }
        discardingTrains.set(false);
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