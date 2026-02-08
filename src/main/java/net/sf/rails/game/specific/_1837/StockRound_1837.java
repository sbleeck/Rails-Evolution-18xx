package net.sf.rails.game.specific._1837;

import net.sf.rails.game.*;
import net.sf.rails.game.financial.*;
import net.sf.rails.game.model.PortfolioModel;
import net.sf.rails.game.state.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rails.game.action.*;
import net.sf.rails.common.DisplayBuffer;
import net.sf.rails.common.LocalText;
import net.sf.rails.common.ReportBuffer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author martin
 *
 */
public class StockRound_1837 extends StockRound {
    private static final Logger log = LoggerFactory.getLogger(StockRound_1837.class);

    protected final ArrayListState<PublicCompany> compWithExcessTrains =
            new ArrayListState<>(this, "compWithExcessTrains");
    protected final IntegerState discardingCompanyIndex = IntegerState.create(
            this, "discardingCompanyIndex");
    protected final BooleanState discardingTrains = new BooleanState(this,
            "discardingTrains");

    protected PublicCompany[] discardingCompanies;
    
    // States for the Pre-Round Special Action Phase
    protected final BooleanState specialActionPhase = new BooleanState(this, "specialActionPhase", false);
    // Tracks how many players have had their turn in the special phase
    protected final IntegerState specialActionPlayerCount = IntegerState.create(this, "specialActionPlayerCount", 0);
    // Tracks the actual index in the player list we are currently querying
    protected final IntegerState specialActionCurrentIndex = IntegerState.create(this, "specialActionCurrentIndex", 0);


    public StockRound_1837(GameManager parent, String id) {
        super(parent, id);
    }







    @Override
    protected void gameSpecificChecks(PortfolioModel boughtFrom,
                                      PublicCompany company,
                                      boolean justStarted) {
        if (justStarted) {
            StockSpace parSpace = company.getCurrentSpace();
            ((StockMarket_1837) stockMarket).addParSpaceUser(parSpace);
        }
    }

    /**
     * Share price goes down 1 space for any number of shares sold.
     */
    @Override
    protected void adjustSharePrice (PublicCompany company, Owner seller, int sharesSold, boolean soldBefore) {
        // No more changes if it has already dropped *in the same turn*
        if (!soldBefore) {
            super.adjustSharePrice (company, seller,1, soldBefore);
        }
    }

@Override
    public boolean setPossibleActions() {
        if (discardingTrains.value()) {
            return setTrainDiscardActions();
        }
        // If we are reloading an old save, the log might contain "BuyCertificate" right away.
        // We must detect this and skip the Special Phase to match the history.
        if (specialActionPhase.value() && gameManager.isReloading()) {
            PossibleAction next = gameManager.getNextActionFromLog();
            if (next != null) {
                // Check if the log action is a Special Phase action
                boolean isSpecialAction = (next instanceof ExchangeMinorAction);
                if (!isSpecialAction && next instanceof NullAction) {
                    // Check if it is the specific "Done" button for the special phase
                    if (((NullAction) next).getMode() == NullAction.Mode.DONE) {
                         isSpecialAction = true;
                    }
                }
                
                // If the log contains a Standard Action (StartCompany, Buy, Pass), disable special phase
                if (!isSpecialAction) {
                    specialActionPhase.set(false);
                }
            }
        }
        
        // 0. Special Action Phase Check
        if (specialActionPhase.value()) {
            return setSpecialPhaseActions();
        }
        
        // 1. Load standard Stock Round actions (Buy, Sell, Pass)
        boolean actionsAdded = super.setPossibleActions();


        return actionsAdded;
    }

// ... (lines of unchanged context code) ...
    private boolean setSpecialPhaseActions() {
        possibleActions.clear();
        
        Set<String> processedCompanies = new HashSet<>();

        // Iterate certs of the CURRENT player (set in start/process)
        if (currentPlayer != null) {
            for (PublicCertificate cert : currentPlayer.getPortfolioModel().getCertificates()) {
                PublicCompany company = cert.getCompany();
                if (company == null || processedCompanies.contains(company.getId())) continue;
                
                String type = company.getType().getId();
                if ("Minor".equals(type) || "Coal".equals(type)) {
                    
                    PublicCompany target = getMergeTarget(company); 
    
                    if (target != null && target.hasFloated() && company.getPresident() == currentPlayer) {
possibleActions.add(new ExchangeMinorAction(company, target, false));
                         processedCompanies.add(company.getId());
                    }
                }
            }
        }

        // --- START FIX ---
        // Use Mode.DONE and .setLabel()
        possibleActions.add(new NullAction(getRoot(), NullAction.Mode.DONE).setLabel("Done / No Exchanges"));
        // --- END FIX ---
        
        return true;
    }
// ... (rest of the class) ...

    /**
     * Identifies potential voluntary mergers for the current player and adds them as actions.
     * Uses NullAction(root, Mode.PASS) for the "No" option.
     */
    protected boolean setMergeActions() {
        boolean actionsAdded = false;

        if (currentPlayer == null) return false;

        Set<String> processedCompanies = new HashSet<>();

        // 1. Iterate over Certificates (PublicCertificate), NOT Tokens.
        // The Player object uses getPortfolioModel().getCertificates() to return List<PublicCertificate>
        for (PublicCertificate cert : currentPlayer.getPortfolioModel().getCertificates()) {
            
            PublicCompany company = cert.getCompany();
            // Avoid nulls or processing the same company twice (if player holds 2 shares)
            if (company == null || processedCompanies.contains(company.getId())) continue;
            
            // 2. Check if it is a Minor or Coal company
            String type = company.getType().getId();
            if ("Minor".equals(type) || "Coal".equals(type)) {
                
                // 3. Find Target
                PublicCompany target = getMergeTarget(company); 

                // 4. Validate Merge Eligibility
                // - Target must exist
                // - Target must have floated (Rule: "Coal companies... if a corporation has floated")
                // - Player must effectively own the minor (be the President)
                if (target != null && target.hasFloated() && company.getPresident() == currentPlayer) {
                     
                     // 5. Use CORRECT Constructor: (source, target, forced=false)
                     possibleActions.add(new MergeCompanies(company, target, false));
                     
                     processedCompanies.add(company.getId());
                     actionsAdded = true;
                }
            }
        }

        // Note: We do NOT add a NullAction (Pass) here because super.setPossibleActions()
        // already adds the standard Stock Round "Pass" action.
        
        return actionsAdded;
    }
    // ... (rest of the method: getMergeTarget) ...
    /**
     * Maps Coal/Minor companies to their Major/National targets based on 1837 rules.
     */
    protected PublicCompany getMergeTarget(PublicCompany source) {
        String id = source.getId();
        String targetId = null;

        // Coal Mappings (Rules 7.3)
        if (id.equals("EPP") || id.equals("RGTE")) targetId = "BK";
        else if (id.equals("EOD") || id.equals("EKT")) targetId = "MS";
        else if (id.equals("MLB")) targetId = "CL";
        else if (id.equals("ZKB") || id.equals("SPB")) targetId = "TR";
        else if (id.equals("LRB") || id.equals("EHS")) targetId = "TI";
        else if (id.equals("BB"))  targetId = "BH";
        
        // Minor Mappings (Rules 7.2) - Simple prefix matching
        // Sd 1-5 -> Sd, kk 1-3 -> kk, Ug 1-3 -> Ug
        else if (id.startsWith("Sd")) targetId = "Sd"; // Southern Railway
        else if (id.startsWith("kk")) targetId = "kk"; // k.k. National
        else if (id.startsWith("Ug")) targetId = "Ug"; // Hungarian National

        if (targetId != null) {
            // Use getRoot() to access the CompanyManager
            return gameManager.getRoot().getCompanyManager().getPublicCompany(targetId);
        }
        return null; 
    }

    public void setBuyableCerts() {
        super.setBuyableCerts();

        // If minors are for sale, the face value should be shown in the Par column.
        for (PossibleAction possibleAction : possibleActions.getList()) {
            if (possibleAction instanceof BuyCertificate) {
                BuyCertificate buyAction = (BuyCertificate) possibleAction;
                PublicCompany company = buyAction.getCompany();
                if (company.getType().getId().startsWith("Minor")
                        && company.getFixedPrice() > 0) {
                    company.getParPriceModel().setPrice(company.getFixedPrice());

                }
            }
        }
    }

    public boolean buyShares(String playerName, BuyCertificate action) {

        boolean result = super.buyShares(playerName, action);

        PublicCompany company = action.getCompany();

        // If the president certificate is bought from the Pool,
        // make sure that it is handled correctly
        if (action.isPresident()) {
            if (!company.hasStarted()) company.start();
            if (company.getType().getId().equals("National")) {
                if (!company.hasFloated()) floatCompany(company);
            } else if (company.getType().getId().equals("Major")) {
                company.checkPresidencyOnBuy(action.getPlayer());
            }
        }

        return result;
    }

    public boolean startCompany(String playerName, StartCompany action) {

        boolean result = super.startCompany(playerName, action);

        // For minors, reset Par column (now having its fixed price)
        PublicCompany company = action.getCompany();
        if (company.getType().getId().startsWith("Minor")) {
            company.getParPriceModel().setPrice(0);
        }

        return result;
    }

    protected boolean setTrainDiscardActions() {

        PublicCompany discardingCompany =
                discardingCompanies[discardingCompanyIndex.value()];
        log.debug("Company {} to discard a train", discardingCompany.getId());
        possibleActions.add(new DiscardTrain(discardingCompany,
                discardingCompany.getPortfolioModel().getUniqueTrains()));
        // We handle one train at at time.
        // We come back here until all excess trains have been discarded.
        return true;
    }
@Override
    protected boolean processGameSpecificAction(PossibleAction action) {
        log.debug("GameSpecificAction: {}", action);
        boolean result = false;

        if (action instanceof ExchangeMinorAction) {
            ExchangeMinorAction exc = (ExchangeMinorAction) action;
          // Pass 'true' for the last argument (autoMerge) to suppress internal pop-ups.
            // The user has already confirmed via the button click.
            result = mergeCompanies(exc.getMinor(), exc.getTargetMajor(), false, true);

            // If in special phase, refresh actions without ending turn
            if (specialActionPhase.value()) {
                setPossibleActions();
            } else {
                // If this happened outside special phase (shouldn't, but for safety), mark acted
                hasActed.set(true);
            }
            return result;
        }

       if (action instanceof ExchangeMinorAction) {
            ExchangeMinorAction exc = (ExchangeMinorAction) action;
            result = mergeCompanies(exc.getMinor(), exc.getTargetMajor(), false, false);
            if (result) {
                setPossibleActions(); 
            }
            return result;
            
        } else if (action instanceof NullAction && specialActionPhase.value()) {
            // "Done" clicked in special phase
            int count = specialActionPlayerCount.value() + 1;
            specialActionPlayerCount.set(count);
            
            List<Player> players = gameManager.getPlayers();

            if (count >= players.size()) {
                // All players done
                specialActionPhase.set(false);
                // Start the REAL stock round
                // We must manually set the current player back to Priority Deal
                setCurrentPlayer(playerManager.getPriorityPlayer());
                super.start(); 
            } else {
                // Next player in loop
                int nextIndex = (specialActionCurrentIndex.value() + 1) % players.size();
                specialActionCurrentIndex.set(nextIndex);
                setCurrentPlayer(players.get(nextIndex));
                setPossibleActions();
            }
            return true;
        }

        if (action instanceof MergeCompanies) { // Legacy check, can likely be removed if unused
             result = mergeCompanies((MergeCompanies) action); 
        } else if (action instanceof DiscardTrain) {
            result = discardTrain((DiscardTrain) action);
        }

        return result;
    }


    /**
     * Merge a minor into an already started company.
     * @param action The MergeCompanies chosen action
     * @return True if the merge was successful
     */
    protected boolean mergeCompanies(MergeCompanies action) {

        PublicCompany minor = action.getMergingCompany();
        PublicCompany major = action.getSelectedTargetCompany();

        return mergeCompanies(minor, major, false,
                currentPlayer == null);
    }

    /**
     * Complemented by a shorter version in subclass CoalExchangeRound.
     * TODO: to be reconsidered once Nationals formation has been tested.
     *
     * @param minor The minor (or coal company) to be merged...
     * @param major ...into the related major company
     * @return True if the merge was successful
     */

    protected boolean mergeCompanies(PublicCompany minor, PublicCompany major,
                                     boolean majorPresident, boolean autoMerge) {
        Mergers.mergeCompanies(gameManager, minor, major, majorPresident, autoMerge);

        checkFlotation(major);

        hasActed.set(true);

        return true;
    }

    public boolean discardTrain(DiscardTrain action) {

        if (!action.process(this)) return false;

        finishTurn();

        return true;
    }

    @Override
    protected void finishTurn() {

        if (!discardingTrains.value()) {
            super.finishTurn();
        } else {
            PublicCompany comp =
                    discardingCompanies[discardingCompanyIndex.value()];
            if (comp != null && comp.getNumberOfTrains() <= comp.getCurrentTrainLimit()) {
                discardingCompanyIndex.add(1);
                if (discardingCompanyIndex.value() >= discardingCompanies.length) {
                    // All excess trains have been discarded
                    finishRound();
                    return;
                }
            }
            PublicCompany discardingCompany =
                    discardingCompanies[discardingCompanyIndex.value()];
            if (discardingCompany != null) {
                setCurrentPlayer(discardingCompany.getPresident());
            }
        }
    }






@Override
    public void start() {
         boolean exchangePossible = false;
        
        for (Player p : gameManager.getPlayers()) {
            for (PublicCertificate cert : p.getPortfolioModel().getCertificates()) {
                PublicCompany comp = cert.getCompany();
                if (comp == null) continue;
                String type = comp.getType().getId();
                
                if ("Minor".equals(type) || "Coal".equals(type)) {
                    PublicCompany target = getMergeTarget(comp);
                    boolean targetFloated = (target != null && target.hasFloated());
                    boolean isPresident = (comp.getPresident() == p);
                    
               
                    if (targetFloated && isPresident) {
                        exchangePossible = true;
                        break;
                    }
                }
            }
            if (exchangePossible) break;
        }


        if (!exchangePossible) {
            specialActionPhase.set(false);
            super.start();
            return;
        }
        
        // Initialize Special Phase sequence
        specialActionPhase.set(true);
        specialActionPlayerCount.set(0);

        List<Player> players = gameManager.getPlayers();
        Player priority = playerManager.getPriorityPlayer();
        
        int pdIndex = 0;
        if (priority != null && players.contains(priority)) {
            pdIndex = players.indexOf(priority);
        }
        
        specialActionCurrentIndex.set(pdIndex);
        setCurrentPlayer(players.get(pdIndex));
        // --- END FIX ---

        if (discardingTrains.value()) {
            discardingTrains.set(false);
        }
    }











}