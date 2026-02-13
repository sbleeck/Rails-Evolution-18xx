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

    protected final ArrayListState<PublicCompany> compWithExcessTrains = new ArrayListState<>(this,
            "compWithExcessTrains");
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

    // ... (rest of the method: getMergeTarget) ...
    /**
     * Maps Coal/Minor companies to their Major/National targets based on 1837
     * rules.
     */
    protected PublicCompany getMergeTarget(PublicCompany source) {
        String id = source.getId();
        String targetId = null;

        // Coal Mappings (Rules 7.3)
        if (id.equals("EPP") || id.equals("RGTE"))
            targetId = "BK";
        else if (id.equals("EOD") || id.equals("EKT"))
            targetId = "MS";
        else if (id.equals("MLB"))
            targetId = "CL";
        else if (id.equals("ZKB") || id.equals("SPB"))
            targetId = "TR";
        else if (id.equals("LRB") || id.equals("EHS"))
            targetId = "TI";
        else if (id.equals("BB"))
            targetId = "BH";

        // Minor Mappings (Rules 7.2) - Simple prefix matching
        // Sd 1-5 -> Sd, kk 1-3 -> kk, Ug 1-3 -> Ug
        else if (id.startsWith("Sd"))
            targetId = "Sd"; // Southern Railway
        else if (id.startsWith("kk"))
            targetId = "kk"; // k.k. National
        else if (id.startsWith("Ug"))
            targetId = "Ug"; // Hungarian National

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

        if (!action.process(this))
            return false;

        finishTurn();

        return true;
    }

    @Override
    protected void finishTurn() {

        if (!discardingTrains.value()) {
            super.finishTurn();
        } else {
            PublicCompany comp = discardingCompanies[discardingCompanyIndex.value()];
            if (comp != null && comp.getNumberOfTrains() <= comp.getCurrentTrainLimit()) {
                discardingCompanyIndex.add(1);
                if (discardingCompanyIndex.value() >= discardingCompanies.length) {
                    // All excess trains have been discarded
                    finishRound();
                    return;
                }
            }
            PublicCompany discardingCompany = discardingCompanies[discardingCompanyIndex.value()];
            if (discardingCompany != null) {
                setCurrentPlayer(discardingCompany.getPresident());
            }
        }
    }

    // ... inside StockRound_1837.java ...

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
        log.info("1837_DEBUG: gameSpecificChecks. Company=" + company.getId()
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
                    if (target != null && target.hasFloated() && company.getPresident() == currentPlayer) {
                        possibleActions.add(new ExchangeMinorAction(company, target, false));
                        processedCompanies.add(company.getId());
                    }
                }
            }
        }

        possibleActions.add(new NullAction(getRoot(), NullAction.Mode.DONE).setLabel("Done / No Exchanges"));
        return true;
    }

    protected boolean processGameSpecificAction(PossibleAction action) {
        log.debug("GameSpecificAction: {}", action);
        boolean result = false;

        // FIXED: Removed Duplicate Code Block
        if (action instanceof ExchangeMinorAction) {
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

        } else if (action instanceof NullAction && specialActionPhase.value()) {
            int count = specialActionPlayerCount.value() + 1;
            specialActionPlayerCount.set(count);
            List<Player> players = gameManager.getPlayers();

            if (count >= players.size()) {
                specialActionPhase.set(false);
                setCurrentPlayer(playerManager.getPriorityPlayer());
                super.start();
            } else {
                int nextIndex = (specialActionCurrentIndex.value() + 1) % players.size();
                specialActionCurrentIndex.set(nextIndex);
                setCurrentPlayer(players.get(nextIndex));
                setPossibleActions();
            }
            return true;
        }

        if (action instanceof MergeCompanies) {
            result = mergeCompanies((MergeCompanies) action);
        } else if (action instanceof DiscardTrain) {
            result = discardTrain((DiscardTrain) action);
        }

        return result;
    }

    @Override
    public void start() {
        boolean exchangePossible = false;

        for (Player p : gameManager.getPlayers()) {
            for (PublicCertificate cert : p.getPortfolioModel().getCertificates()) {
                PublicCompany comp = cert.getCompany();
                if (comp == null)
                    continue;
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
            if (exchangePossible)
                break;
        }

        specialActionPhase.set(exchangePossible);

        if (exchangePossible) {
            specialActionPlayerCount.set(0);
            List<Player> players = gameManager.getPlayers();
            Player priority = playerManager.getPriorityPlayer();
            int pdIndex = 0;
            if (priority != null && players.contains(priority)) {
                pdIndex = players.indexOf(priority);
            }
            specialActionCurrentIndex.set(pdIndex);
        }

        if (discardingTrains.value()) {
            discardingTrains.set(false);
        }

        super.start();
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
                log.info("Major " + major.getId() + " is Sold Out. Force merging " + minor.getId());
                ReportBuffer.add(this, LocalText.getText("MergeSoldOut", minor.getId(), major.getId()));
                mergeCompanies(minor, major, false, true);
            }
        }
    }

}