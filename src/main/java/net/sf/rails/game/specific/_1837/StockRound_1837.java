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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author martin
 *
 */
public class StockRound_1837 extends StockRound {
    private static final Logger log = LoggerFactory.getLogger(StockRound_1837.class);

    protected final ArrayListState<PublicCompany> compWithExcessTrains = new ArrayListState<>(this,
            "compWithExcessTrains");
    protected final IntegerState discardingCompanyIndex = IntegerState.create(
            this, "discardingCompanyIndex");
    protected final BooleanState discardingTrains = new BooleanState(this,
            "discardingTrains");
    protected final BooleanState specialActionProcessed = new BooleanState(this, "specialActionProcessed", false);
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

    /**
     * Share price goes down 1 space for any number of shares sold.
     */
    @Override
    protected void adjustSharePrice(PublicCompany company, Owner seller, int sharesSold, boolean soldBefore) {
        // No more changes if it has already dropped *in the same turn*
        if (!soldBefore) {
            super.adjustSharePrice(company, seller, 1, soldBefore);
        }
    }

    /**
     * Identifies potential voluntary mergers for the current player and adds them
     * as actions.
     * Uses NullAction(root, Mode.PASS) for the "No" option.
     */
    protected boolean setMergeActions() {
        boolean actionsAdded = false;

        if (currentPlayer == null)
            return false;

        Set<String> processedCompanies = new HashSet<>();

        // 1. Iterate over Certificates (PublicCertificate), NOT Tokens.
        // The Player object uses getPortfolioModel().getCertificates() to return
        // List<PublicCertificate>
        for (PublicCertificate cert : currentPlayer.getPortfolioModel().getCertificates()) {

            PublicCompany company = cert.getCompany();
            // Avoid nulls or processing the same company twice (if player holds 2 shares)
            if (company == null || processedCompanies.contains(company.getId()))
                continue;

            // 2. Check if it is a Minor or Coal company
            String type = company.getType().getId();
            if ("Minor".equals(type) || "Coal".equals(type)) {

                // 3. Find Target
                PublicCompany target = getMergeTarget(company);

                // 4. Validate Merge Eligibility
                // - Target must exist
                // - Target must have floated (Rule: "Coal companies... if a corporation has
                // floated")
                // - Player must effectively own the minor (be the President)
                if (target != null && target.hasFloated() && company.getPresident() == currentPlayer) {

                    // 5. Use CORRECT Constructor: (source, target, forced=false)
                    possibleActions.add(new MergeCompanies(company, target, false));

                    processedCompanies.add(company.getId());
                    actionsAdded = true;
                }
            }
        }

        // Note: We do NOT add a NullAction (Pass) here because
        // super.setPossibleActions()
        // already adds the standard Stock Round "Pass" action.

        return actionsAdded;
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

        PublicCompany discardingCompany = discardingCompanies[discardingCompanyIndex.value()];
        log.debug("Company {} to discard a train", discardingCompany.getId());
        possibleActions.add(new DiscardTrain(discardingCompany,
                discardingCompany.getPortfolioModel().getUniqueTrains()));
        // We handle one train at at time.
        // We come back here until all excess trains have been discarded.
        return true;
    }

    /**
     * Merge a minor into an already started company.
     * 
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
     *
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

        if (!action.process(this))
            return false;

        finishTurn();

        return true;
    }

    @Override
    protected void finishTurn() {

        if (specialActionProcessed.value()) {
            specialActionProcessed.set(false);
            return;
        }

        super.finishTurn();
    }

    public boolean buyShares(String playerName, BuyCertificate action) {

        boolean result = super.buyShares(playerName, action);

        PublicCompany company = action.getCompany();

        // If the president certificate is bought from the Pool,
        // make sure that it is handled correctly
        if (action.isPresident()) {
            if (!company.hasStarted())
                company.start();
            if (company.getType().getId().equals("National")) {
                if (!company.hasFloated())
                    floatCompany(company);
            } else if (company.getType().getId().equals("Major")) {
                company.checkPresidencyOnBuy(action.getPlayer());
            }
        }

        // Rule: If a Major becomes Sold Out (no shares in IPO),
        // connected Coal/Minors must merge immediately.
        if (result) {
            processSoldOutMergers();
        }

        return result;
    }

    @Override
    protected void gameSpecificChecks(PortfolioModel boughtFrom,
            PublicCompany company,
            boolean justStarted) {
        log.debug("1837_DEBUG: gameSpecificChecks. Company=" + company.getId()
                + ", SourceModel=" + (boughtFrom != null ? boughtFrom.toString() : "null"));

        super.gameSpecificChecks(boughtFrom, company, justStarted);

        if (justStarted) {
            StockSpace parSpace = company.getCurrentSpace();
            if (parSpace != null) {
                ((StockMarket_1837) stockMarket).addParSpaceUser(parSpace);
            }
        }

        if (boughtFrom != null) {
            PortfolioModel ipoModel = net.sf.rails.game.financial.Bank.getIpo(gameManager).getPortfolioModel();
            boolean isIPO = boughtFrom.equals(ipoModel);

            if (!isIPO) {
                if (companyBoughtThisTurnWrapper.value() == null) {
                    log.warn("1837_DEBUG: FORCING companyBoughtThisTurnWrapper for Pool buy");
                    companyBoughtThisTurnWrapper.set(company);
                }
            }
        }
    }

    protected boolean processGameSpecificAction(PossibleAction action) {
        log.debug("GameSpecificAction: {}", action);
        boolean result = false;

        if (action instanceof ExchangeMinorAction) {
            specialActionProcessed.set(true);
            ExchangeMinorAction exc = (ExchangeMinorAction) action;
            result = mergeCompanies(exc.getMinor(), exc.getTargetMajor(), false, false);
            if (result) {
                if (specialActionPhase.value()) {
                    setPossibleActions();
                } else {
                    hasActed.set(true);
                }
            }
            return result;

        }

        if (action instanceof MergeCompanies) {
            result = mergeCompanies((MergeCompanies) action);
        } else if (action instanceof DiscardTrain) {
            result = discardTrain((DiscardTrain) action);
        }

        return result;
    }

    private void processSoldOutMergers() {
        List<PublicCompany> allCompanies = new java.util.ArrayList<>(gameManager.getAllPublicCompanies());

        for (PublicCompany minor : allCompanies) {
            if (minor.isClosed())
                continue;
            String type = minor.getType().getId();
            if (!"Coal".equals(type) && !"Minor".equals(type))
                continue;

            PublicCompany major = getMergeTarget(minor);
            if (major == null || !major.hasFloated() || major.isClosed())
                continue;

            boolean isSoldOut = true;
            for (PublicCertificate cert : net.sf.rails.game.financial.Bank.getIpo(gameManager).getPortfolioModel()
                    .getCertificates()) {
                if (cert.getCompany().equals(major)) {
                    isSoldOut = false;
                    break;
                }
            }

            if (isSoldOut) {
                log.debug("Major " + major.getId() + " is Sold Out. Force merging " + minor.getId());
                ReportBuffer.add(this, LocalText.getText("MergeSoldOut", minor.getId(), major.getId()));
                mergeCompanies(minor, major, false, true);
            }
        }
    }

    @Override
    public boolean setPossibleActions() {

        if (discardingTrains.value()) {
            return setTrainDiscardActions();
        }

        if (specialActionPhase.value() && gameManager.isReloading()) {
            PossibleAction next = gameManager.getNextActionFromLog();
            if (next != null) {
                boolean isSpecialAction = (next instanceof ExchangeMinorAction);
                if (!isSpecialAction && next instanceof NullAction) {
                    if (((NullAction) next).getMode() == NullAction.Mode.DONE) {
                        isSpecialAction = true;
                    }
                }
                if (!isSpecialAction) {
                    specialActionPhase.set(false);
                }
            }
        }

        if (specialActionPhase.value()) {
            return setSpecialPhaseActions();
        }

        return super.setPossibleActions();
    }

    @Override
    public void finishRound() {

        
        super.finishRound();
    }

    @Override
    public void start() {
        // 1. Initialize standard Stock Round FIRST
        super.start();

        boolean exchangePossible = false;
        List<Player> players = gameManager.getPlayers();

        // 2. Check all players for ANY valid exchange
        for (Player p : players) {
            // PASS NULL to check all types
            if (hasExchangeableMinors(p, null)) {
                exchangePossible = true;
            }
        }

        specialActionPhase.set(exchangePossible);

        if (exchangePossible) {
            specialActionPlayerCount.set(0);

            // Start checking from the Priority Player
            Player priority = playerManager.getPriorityPlayer();
            int pdIndex = (priority != null && players.contains(priority)) ? players.indexOf(priority) : 0;

            // 3. Skip players who have nothing to exchange
            int checked = 0;
            while (!hasExchangeableMinors(players.get(pdIndex), null) && checked < players.size()) {
                pdIndex = (pdIndex + 1) % players.size();
                checked++;
            }

            // 4. Set the actual starting player for the Special Phase
            specialActionCurrentIndex.set(pdIndex);
            setCurrentPlayer(players.get(pdIndex));

            // FORCE action regeneration so the UI sees the Special Phase actions
            // immediately.
            setPossibleActions();

        }

        if (discardingTrains.value()) {
            discardingTrains.set(false);
        }
    }


    @Override
    public boolean process(PossibleAction action) {
        // CRITICAL: Always reset the flag at the start of a new action processing
        // cycle.
        // This handles cases where finishTurn() was skipped (e.g., after super.start())
        // and prevents the flag from "leaking" into the standard round.
        specialActionProcessed.set(false);

        if (specialActionPhase.value()) {
            // Mark true here to intercept finishTurn() for any Special Phase action
            specialActionProcessed.set(true);

            if (action instanceof NullAction) {
                advanceSpecialPhase();
                return true;
            }

            if (action instanceof ExchangeMinorAction) {
                ExchangeMinorAction exc = (ExchangeMinorAction) action;

                boolean result = mergeCompanies(exc.getMinor(), exc.getTargetMajor(), false, false);

                if (result) {
                    // Check if the player has ANY remaining exchanges
                    if (hasExchangeableMinors(currentPlayer, null)) {
                        setPossibleActions();
                    } else {
                        advanceSpecialPhase();
                    }
                }
                return result;
            }
        }

        boolean result = super.process(action);
        return result;
    }

    /**
     * Helper to handle the transition to the next player in the Special Phase.
     */
    private void advanceSpecialPhase() {
        List<Player> players = gameManager.getPlayers();

        int nextIdx = (specialActionCurrentIndex.value() + 1) % players.size();
        int newCount = specialActionPlayerCount.value() + 1;

        // Skip players with no exchangeable items
        while (newCount < players.size() && !hasExchangeableMinors(players.get(nextIdx), null)) {
            nextIdx = (nextIdx + 1) % players.size();
            newCount++;
        }

        specialActionPlayerCount.set(newCount);
        specialActionCurrentIndex.set(nextIdx);

        if (newCount >= players.size()) {
            specialActionPhase.set(false);
            setCurrentPlayer(playerManager.getPriorityPlayer());

            // Mark true to prevent the transition from counting as a Pass for the Priority
            // Deal
            specialActionProcessed.set(true);

            super.start();
        } else {
            setCurrentPlayer(players.get(nextIdx));
            setPossibleActions();
        }
    }


    private boolean setSpecialPhaseActions() {
        possibleActions.clear();
        Set<String> processedCompanies = new HashSet<>();


        
        if (currentPlayer != null) {

            for (PublicCertificate cert : currentPlayer.getPortfolioModel().getCertificates()) {
                PublicCompany company = cert.getCompany();
                if (company == null || processedCompanies.contains(company.getId()))
                    continue;

                String type = company.getType().getId();

                if ("Minor".equals(type) || "Coal".equals(type)) {
                    PublicCompany target = getMergeTarget(company);
                    boolean isPres = (company.getPresident() == currentPlayer);
                    boolean hasFloated = (target != null && target.hasFloated());

                  
                    



                    if (target != null && hasFloated && isPres) {

                        possibleActions.add(new ExchangeMinorAction(company, target, false));
                        processedCompanies.add(company.getId());
                    }
                }
            }
        }
        // --- END FIX ---

        possibleActions.add(new NullAction(getRoot(), NullAction.Mode.DONE).setLabel("Done / No Exchanges"));
        return true;
    }


    protected boolean hasExchangeableMinors(Player p, String typeFilter) {
        if (p == null)
            return false;
        boolean found = false;

     
        for (PublicCertificate cert : p.getPortfolioModel().getCertificates()) {
            PublicCompany comp = cert.getCompany();
            if (comp == null)
                continue;

            String type = comp.getType().getId();

            if (!"Minor".equals(type) && !"Coal".equals(type))
                continue;

            PublicCompany target = getMergeTarget(comp);

            boolean isPresident = (comp.getPresident() == p);
            boolean targetExists = (target != null);
            boolean targetFloated = (targetExists && target.hasFloated());
            boolean targetClosed = (targetExists && target.isClosed());
            boolean typeMatch = (typeFilter == null || typeFilter.equals(type));
            boolean compClosed = comp.isClosed();

         
            
            if (compClosed)
                continue;
            if (!typeMatch)
                continue;

            if (targetFloated && isPresident && !targetClosed) {

                found = true;
            }
        }

        return found;
    }


        /**
     * Helper duplicated from StockRound to avoid cross-round dependency issues.
     */
private PublicCompany getMergeTarget(PublicCompany source) {
        String id = source.getId();
        String targetId = null;

        if (id.equals("EPP") || id.equals("RGTE"))
            targetId = "BK";
        else if (id.equals("EOD") || id.equals("EKT"))
            targetId = "MS";
        else if (id.equals("MLB"))
            targetId = "CL";
        else if (id.equals("ZKB") || id.equals("SPB"))
            targetId = "SB";
        else if (id.equals("LRB") || id.equals("EHS"))
            targetId = "TH"; // Corrected from TI to TH
        else if (id.equals("BB"))
            targetId = "BH";

        else if (id.startsWith("S"))
            targetId = "Sd";
        else if (id.startsWith("K"))
            targetId = "KK";
        else if (id.startsWith("U"))
            targetId = "Ug";

        if (targetId != null) {
            return gameManager.getRoot().getCompanyManager().getPublicCompany(targetId);
        }
        return null;
    }



}