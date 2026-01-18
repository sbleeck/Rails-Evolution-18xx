package net.sf.rails.game.specific._1835;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rails.game.action.NullAction;
import rails.game.specific._1835.StartPrussian;
import rails.game.specific._1835.ExchangeForPrussianShare;
import rails.game.action.DiscardTrain;
import rails.game.action.PossibleAction;
import net.sf.rails.common.GuiDef;
import net.sf.rails.common.LocalText;
import net.sf.rails.common.ReportBuffer;
import net.sf.rails.game.*;
import net.sf.rails.game.financial.PublicCertificate;
import net.sf.rails.game.round.I_MapRenderableRound;
import net.sf.rails.game.round.RoundFacade;
import net.sf.rails.game.special.ExchangeForShare;
import net.sf.rails.game.special.SpecialProperty;
import net.sf.rails.game.state.*; // Added for StringState, BooleanState
import net.sf.rails.common.DisplayBuffer;
import net.sf.rails.game.financial.Bank;
import java.util.stream.Collectors;
import com.google.common.collect.Iterables;

public class PrussianFormationRound extends Round implements I_MapRenderableRound {

    private static final Logger log = LoggerFactory.getLogger(PrussianFormationRound.class);

    public static final String PENDING_PFR_STATE_KEY = "PENDING_PFR_OFFER";
    public static final int PFR_PRIORITY = 10;

    private PublicCompany prussian;
    private PublicCompany m2;
    private Phase phase;

    // Transient booleans (Recalculated in resume/start)
    private boolean startPr;
    private boolean forcedStart;
    private boolean mergePr;
    private boolean forcedMerge;

    private final StringState swapOldPresName = StringState.create(this, "SwapOldPresName", null);
    private final StringState swapNewPresName = StringState.create(this, "SwapNewPresName", null);

    public enum Step {
        START,
        MERGE,
        DISCARD_TRAINS,
        PRESIDENCY_SWAP
    }

    // Inner Action Class for the Swap Choice
    public static class PresidencySwapChoice extends PossibleAction {
        private static final long serialVersionUID = 1L;
        private final int optionIndex;

        // FIX: Cast null to (RailsRoot)
        public PresidencySwapChoice(int index) {
            super((RailsRoot) null);
            this.optionIndex = index;
        }

        public int getOptionIndex() {
            return optionIndex;
        }

        @Override
        public String toString() {
            return "SwapOption:" + optionIndex;
        }
    }

    private List<Company> foldablePrePrussians;
    private RoundFacade interruptedRound;

    // We replace raw fields with State objects so the Engine's TransactionManager
    // tracks them.
    // StringState uses a static factory (.create)
    private final StringState stepState = StringState.create(this, "StepState", Step.START.name());

    // BooleanState uses a public Constructor (new ...)
    private final BooleanState roundFinishedState = new BooleanState(this, "RoundFinishedState");

    // StringState uses a static factory (.create)
    private final StringState startingPlayerName = StringState.create(this, "StartingPlayerName", null);

    // Kept from previous fix
    private final IntegerState mergeTurnCount = IntegerState.create(this, "MergeTurnCount", 0);

    // Transient reference (re-fetched via startingPlayerName)
    protected Player startingPlayer;
    protected Player currentPlayer;

    private static String PR_ID = GameDef_1835.PR_ID;
    private static String M2_ID = GameDef_1835.M2_ID;

    public PrussianFormationRound(GameManager parent, String id) {
        super(parent, id);
        guiHints.setVisibilityHint(GuiDef.Panel.MAP, true);
        guiHints.setVisibilityHint(GuiDef.Panel.STATUS, true);
        guiHints.setActivePanel(GuiDef.Panel.MAP);
    }

    // --- Helper Methods for State Access ---
    public Step getPrussianStep() {
        try {
            return Step.valueOf(stepState.value());
        } catch (Exception e) {
            return Step.START;
        }
    }

    private void setPrussianStep(Step s) {
        stepState.set(s.name());
    }

    private boolean isRoundFinished() {
        return roundFinishedState.value();
    }

    private void setRoundFinished(boolean val) {
        roundFinishedState.set(val);
    }

    private void setStartingPlayer(Player p) {
        this.startingPlayer = p;
        if (p != null) {
            startingPlayerName.set(p.getName());
        } else {
            startingPlayerName.set(null);
        }
    }

    private Player getStartingPlayer() {
        String name = startingPlayerName.value();
        if (name == null)
            return null;
        for (Player p : playerManager.getPlayers()) {
            if (p.getName().equals(name)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Called by the engine when loading a save OR performing an UNDO.
     * We must reconstruct transient variables here.
     */
    @Override
    public void resume() {
        super.resume();

        this.interruptedRound = gameManager.getInterruptedRound();
        this.prussian = companyManager.getPublicCompany(PR_ID);
        this.m2 = companyManager.getPublicCompany(M2_ID);
        this.phase = Phase.getCurrent(this);

        // Restore transient logic flags
        this.startPr = !prussian.hasStarted();
        this.forcedMerge = phase.getId().equals("5");
        this.forcedStart = phase.getId().equals("4+4") || forcedMerge;
        this.mergePr = !prussianIsComplete(gameManager);

        // Restore Player References
        this.startingPlayer = getStartingPlayer();
        this.currentPlayer = playerManager.getCurrentPlayer(); // Engine handles this one

    }

    private Player getPlayerByName(String name) {
        if (name == null)
            return null;
        for (Player p : playerManager.getPlayers()) {
            if (p.getName().equals(name))
                return p;
        }
        return null;
    }

    public void start() {
        this.interruptedRound = gameManager.getInterruptedRound();

        // 1. Determine Force Status BEFORE checking the 'Already Offered' flag.
        phase = Phase.getCurrent(this);
        boolean prussianStarted = companyManager.getPublicCompany(PR_ID).hasStarted();

        // If we are interrupting an Operating Round, this is the Mandatory Rule 5.5.4
        // Trigger.
        boolean isOrTrigger = (interruptedRound instanceof OperatingRound);

        forcedMerge = phase.getId().startsWith("5");

        // PFR is forced if:
        // a) It interrupted an OR (Train Buy) -> Always Force.
        // b) We are in Phase 5 -> Always Force (until complete).
        // c) We are in Phase 4+4 AND Prussian has NOT started yet -> Force the M2
        // Merger.
        // (Once PR starts, the 4+4 Force condition is satisfied).
        boolean isPhase4Plus4Forced = phase.getId().equals("4+4") && !prussianStarted;

        forcedStart = isOrTrigger || forcedMerge || isPhase4Plus4Forced;

        // 2. LOOP GUARD: Check if PFR was already offered.
// We skip if it has been offered, UNLESS it is a forced merge (Phase 5) which repeats.
        // We remove !forcedStart because checking "Offered" is sufficient to handle the "Once per OR" rule.
        if (!forcedMerge && gameManager instanceof GameManager_1835
                && ((GameManager_1835) gameManager).hasPrussianFormationBeenOffered()) {

            if (interruptedRound != null) {
                gameManager.setInterruptedRound(null);
                gameManager.setRound(interruptedRound);
                interruptedRound.resume();
                return;
            }
        }

        if (PrussianFormationRound.prussianIsComplete(gameManager)) {
            finishRound();
            return;
        }

        // Initialize locals
        this.interruptedRound = gameManager.getInterruptedRound();
        prussian = companyManager.getPublicCompany(PR_ID);
        phase = Phase.getCurrent(this);
        startPr = !prussian.hasStarted();

        // Robust Phase detection (Handles "5", "5+5", "5 (Brown)", etc.)
        forcedMerge = phase.getId().startsWith("5");

        forcedStart = phase.getId().equals("4+4") || forcedMerge;
        mergePr = !prussianIsComplete(gameManager);
        m2 = companyManager.getPublicCompany(M2_ID);

        // FINAL SAFETY NET: Ensure currentPlayer is NEVER null before proceeding
        if (this.currentPlayer == null) {
            this.currentPlayer = playerManager.getCurrentPlayer();
            if (this.currentPlayer == null) {
                if (prussian.getPresident() != null)
                    this.currentPlayer = prussian.getPresident();
                else if (m2 != null && m2.getPresident() != null)
                    this.currentPlayer = m2.getPresident();
                else
                    this.currentPlayer = playerManager.getPriorityPlayer();

                if (this.currentPlayer != null) {
                    playerManager.setCurrentPlayer(this.currentPlayer);
                }
            }
        }
        // 1. DYNAMIC HEADER UPDATE
        // Ensure the sidebar always shows the CURRENT player who is being asked, not
        // the round starter.
        if (this.currentPlayer != null) {
            try {
                Class<?> orPanelClass = Class.forName("net.sf.rails.ui.swing.ORPanel");
                java.lang.reflect.Method setHeader = orPanelClass.getMethod("setGlobalCustomHeader", String.class,
                        String.class);
                setHeader.invoke(null, "Prussian Formation", this.currentPlayer.getName() + " to Act");
            } catch (Throwable t) {
                // Ignore errors
            }
        }

        // --- Only Initialize State if this is a fresh start (not a reload/undo) ---
        // We check if the State object is "dirty" or matches default
        if (getPrussianStep() == Step.START && startingPlayerName.value() == null) {

            ReportBuffer.add(this, LocalText.getText("StartFormationRound", PR_ID));
            setPrussianStep(startPr ? Step.START : Step.MERGE);
            mergeTurnCount.set(0);

            Player m2President = (m2 != null) ? m2.getPresident() : null;

            if (getPrussianStep() == Step.START) {
                if (m2President != null) {
                    setStartingPlayer(m2President);
                    setCurrentPlayer(m2President);
                } else {
                    setStartingPlayer(((GameManager_1835) gameManager).getPrussianFormationStartingPlayer());
                    setCurrentPlayer(this.startingPlayer);
                }

                if (forcedStart) {
                    ReportBuffer.add(this, LocalText.getText("PFR_ForcedStart", phase.getId()));
                    executeStartPrussian(true);
                    setPrussianStep(Step.MERGE);

                    // Rule: The Exchange Phase starts with the Priority Deal player.
                    Player nextPlayer = playerManager.getPriorityPlayer();

                    // Fallback to M2 pres or Triggerer only if Priority is somehow missing
                    if (nextPlayer == null)
                        nextPlayer = ((GameManager_1835) gameManager).getPrussianFormationStartingPlayer();
                    if (nextPlayer == null)
                        nextPlayer = prussian.getPresident();
                    if (nextPlayer == null)
                        nextPlayer = m2President;

                    setStartingPlayer(nextPlayer);
                    setCurrentPlayer(nextPlayer);

                    // If forced start, we need to check if the starting player has any shares
                    // to exchange. If not, auto-advance to the next player.
                    setFoldablePrePrussians();
                    if (foldablePrePrussians.isEmpty()) {
                        finishTurn();
                    }
                }
            } else if (getPrussianStep() == Step.MERGE) {
                // If we resume in Merge phase, ensure we start with Priority Deal.
                Player sp = playerManager.getPriorityPlayer();
                if (sp == null)
                    sp = ((GameManager_1835) gameManager).getPrussianFormationStartingPlayer();

                setStartingPlayer(sp);
                setCurrentPlayer(this.startingPlayer);
                
                // Immediately skip if the first player has nothing to do
                advanceToNextValidPlayer();
            }
        }

        // Always run forced logic if we are in merge and it's forced
        if (getPrussianStep() == Step.MERGE && forcedMerge) {

            // Use a Set to ensure unique processing
            Set<Company> foldablesSet = new LinkedHashSet<>();

            // 1. Scan Privates
            for (PrivateCompany company : gameManager.getAllPrivateCompanies()) {
                // For Privates, we check if they are explicitly NOT closed, OR if they are
                // closed
                // but have the exchange property (e.g. BB might be closed but exchangeable?
                // Usually they are open until exchange).
                // We'll stick to standard !isClosed() + Property, as PFR will close them.
                if (!company.isClosed() && hasExchangeProperty(company)) {
                    foldablesSet.add(company);
                }
            }

            // 2. Scan Publics (Minors)
            for (PublicCompany company : gameManager.getAllPublicCompanies()) {
                // Now that OR_1835 is fixed, M1 should be OPEN (!isClosed).
                if (!company.isClosed() && hasExchangeProperty(company)) {
                    foldablesSet.add(company);
                }
            }

            List<Company> foldables = new ArrayList<>(foldablesSet);

            if (!foldables.isEmpty()) {
                executeExchange(foldables, false, false);
            }

            finishMergeStep();
        }
    }

    private boolean hasExchangeProperty(Company c) {

        // Whitelist Check: Strictly limit candidates to the 6 Minors and 2 Privates defined in 1835 rules.
        // This prevents Majors (like MS) from being selected due to "ghost" properties inherited from closed privates.
        String id = c.getId();
        boolean isPrussianCandidate = 
               id.equals("M1") || id.equals("M2") || id.equals("M3") 
            || id.equals("M4") || id.equals("M5") || id.equals("M6") 
            || id.equals("BB") || id.equals("HB");
        
        if (!isPrussianCandidate) {
            return false;
        }



        Set<SpecialProperty> sps = c.getSpecialProperties();
        if (sps == null || sps.isEmpty())
            return false;
        // Robust check: Search for the specific property type, don't assume index 0
        for (SpecialProperty sp : sps) {
            if (sp instanceof ExchangeForShare)
                // If a Major Company (has stock price) has this property, it's an error/ghost.
                // We log the property details to find out WHERE it came from.
                if (c instanceof PublicCompany && ((PublicCompany)c).hasStockPrice()) {
                    log.warn("!!! GHOST PROPERTY DETECTED ON MAJOR [{}] !!!", c.getId());
                    log.warn("Property Object: {}", sp);
                    log.warn("Property Class: {}", sp.getClass().getName());
                    log.warn("Property Info: {}", sp.getInfo()); // Often contains "Exchangeable for..."
                }
                return true;
        }
        return false;
    }

    private void setFoldablePrePrussians() {
        foldablePrePrussians = new ArrayList<>();

        if (currentPlayer == null) {
            return;
        }

        // WINDOW CHECK: The optional exchange loop is only valid between the 4-train and 5-train.
        
        // 1. Refresh Phase (Field 'phase' might be stale)
        Phase currentPhase = Phase.getCurrent(this);
        String pId = (currentPhase != null) ? currentPhase.getId() : "";

        // 2. Window Opens: When 4-train is sold (Phase "4"/"4+4") or Prussia has started.
        // We check "startsWith(5)" here to ensure the window is logically 'open' during the forced merge transition,
        // though the 'Closed' check below will handle the exclusion.
        boolean windowOpen = (prussian != null && prussian.hasStarted()) 
                           || pId.contains("4") || pId.startsWith("5");

        if (!windowOpen) {
            return;
        }

        // 3. Window Closes: When 5-train is bought (Phase "5" / Brown).
        // The start() method handles the MANDATORY forced merge for Phase 5.
        // We must DISABLE this interactive/optional loop to prevent infinite prompts.
        if (pId.startsWith("5")) {
             return;
        }


        for (PrivateCompany company : currentPlayer.getPortfolioModel().getPrivateCompanies()) {
            if (!company.isClosed() && hasExchangeProperty(company)) {
                foldablePrePrussians.add(company);
            }
        }
        for (PublicCertificate cert : currentPlayer.getPortfolioModel().getCertificates()) {
            if (!cert.isPresidentShare())
                continue;
            PublicCompany company = cert.getCompany();
            // CRITICAL FILTER: Only "Minor" companies can exchange into Prussia.
            // Majors (like MS) might return true for hasExchangeProperty if they own a private,
            // so we MUST explicitly exclude them by checking the company type.
            if (!company.getType().getId().equalsIgnoreCase("Minor")) {
                continue;
            }
            if (!company.isClosed() && hasExchangeProperty(company)) {
                foldablePrePrussians.add(company);
            }
        }
    }

    @Override
    public boolean process(PossibleAction action) {
        boolean result = false;
        Player currentPlayer = playerManager.getCurrentPlayer();
        String playerName = (currentPlayer == null ? "N/A" : currentPlayer.getName());

        if (action instanceof PresidencySwapChoice) {
            executePresidencySwapChoice((PresidencySwapChoice) action);
            return true;
        }

        if (action instanceof StartPrussian) {
            executeStartPrussian(false);
            setPrussianStep(Step.MERGE);

            // Update State
            setStartingPlayer(((GameManager_1835) gameManager).getPrussianFormationStartingPlayer());
            mergeTurnCount.set(0);

// After starting Prussia, check if this player has other papers (e.g. BB/HB)
            // If not, auto-advance to the next valid player immediately.
            advanceToNextValidPlayer();
            return true;

        } else if (action instanceof ExchangeForPrussianShare) {
            ExchangeForPrussianShare a = (ExchangeForPrussianShare) action;
            executeExchange(Arrays.asList(a.getCompanyToExchange()), false, false);

// If the player has no more shares to exchange, automatically finish their turn.
            setFoldablePrePrussians();
            if (foldablePrePrussians.isEmpty()) {
                finishTurn(); 
                // Note: finishTurn() now calls advanceToNextValidPlayer(), so we are safe.
            }
            
            return true;

        } else if (action instanceof DiscardTrain) {
            discardTrain((DiscardTrain) action);
            return true;

        } else if (action instanceof NullAction) {
            NullAction nullAction = (NullAction) action;

            if (nullAction.getMode() == NullAction.Mode.PASS) {
                result = pass(nullAction, playerName, false);
                return result;
            }

            if (nullAction.getMode() == NullAction.Mode.DONE) {
                finishTurn();
                return true;
            }
        }
        return result;
    }

    protected void finishTurn() {
// We increment the count for the CURRENT player who just finished/passed
        mergeTurnCount.add(1);

        if (mergeTurnCount.value() >= playerManager.getNumberOfPlayers()) {
            finishMergeStep();
            return;
        }

        // Move to next player
        Player nextPlayer = playerManager.getNextPlayer();
        setCurrentPlayer(nextPlayer);

        // Recursively skip any subsequent players who have no shares
        advanceToNextValidPlayer();
    }

    private void finishMergeStep() {
        if (prussian.getNumberOfTrains() > prussian.getCurrentTrainLimit()) {
            setPrussianStep(Step.DISCARD_TRAINS);
            setCurrentPlayer(prussian.getPresident());
        } else {
            finishRound();
        }
    }

    protected boolean pass(NullAction action, String playerName, boolean hasAutopassed) {
        PublicCompany m2 = companyManager.getPublicCompany(GameDef_1835.M2_ID);

        if (getPrussianStep() == Step.START && playerManager.getCurrentPlayer() == m2.getPresident()) {
            ReportBuffer.add(this, playerName + " passes the Prussian Formation option.");

            // 1. Tell the Game Manager we declined, so it doesn't ask again IMMEDIATELY.
            // It will reset this flag when the *next* round finishes.
            if (gameManager instanceof GameManager_1835) {
                ((GameManager_1835) gameManager).setPfrDeclined();
            }

            // 2. Finish this PFR round.
            // This will return control to GameManager.nextRound().
            // GameManager will see pfrDeclined=true, skip the hook, and start the actual
            // OR/SR.
            finishRound();

            return true;
        }
        return false;
    }

    public Player getCurrentPlayer() {
        return this.currentPlayer;
    }

    public void setCurrentPlayer(Player player) {
        this.currentPlayer = player;
        playerManager.setCurrentPlayer(player);
    }

    private void executeStartPrussian(boolean display) {
        if (m2 == null) {
            m2 = companyManager.getPublicCompany(M2_ID);
        }

        prussian.start();
        String message = LocalText.getText("START_MERGED_COMPANY",
                PR_ID, Bank.format(this, prussian.getIPOPrice()), prussian.getStartSpace().toText());
        ReportBuffer.add(this, message);

        int capFactor = prussian.getSoldPercentage()
                / (prussian.getShareUnit() * prussian.getShareUnitsForSharePrice());
        int cash = capFactor * prussian.getIPOPrice();
        if (cash > 0) {
            ReportBuffer.add(this,
                    LocalText.getText("FloatsWithCash", prussian.getId(),
                            net.sf.rails.game.state.Currency.fromBank(cash, prussian)));
        } else {
            ReportBuffer.add(this, LocalText.getText("Floats", prussian.getId()));
        }

        executeExchange(Arrays.asList(m2), true, false);

        prussian.setFloated();

        if (interruptedRound instanceof OperatingRound) {
            OperatingRound or = (OperatingRound) interruptedRound;
            PublicCompany triggerCompany = or.getOperatingCompany();
            boolean isM1 = (triggerCompany != null && "M1".equals(triggerCompany.getId()));

            if (isM1) {
                or.insertNewOperatingCompany(prussian);
            }
        }
    }

    /**
     * Expose the step name as a String to avoid Enum visibility issues in generic
     * UI classes.
     */
    public String getPrussianStepName() {
        return getPrussianStep().toString();
    }

    public boolean discardTrain(DiscardTrain action) {
        Train train = action.getDiscardedTrain();
        PublicCompany company = action.getCompany();

        if (company != prussian || train == null || !company.getPortfolioModel().getTrainList().contains(train)) {
            return false;
        }
        train.discard();

        if (prussian.getNumberOfTrains() > prussian.getCurrentTrainLimit()) {
            setPrussianStep(Step.DISCARD_TRAINS);
        } else {
            finishRound();
        }
        return true;
    }

    public static boolean prussianIsComplete(GameManager gameManager) {
        for (PublicCompany company : gameManager.getAllPublicCompanies()) {
            if (!checkForPrussianMinorExchange(company))
                return false;
        }
        for (PrivateCompany company : gameManager.getAllPrivateCompanies()) {
            if (!checkForPrussianPrivateExchange(company))
                return false;
        }
        return true;
    }

    static boolean checkForPrussianMinorExchange(PublicCompany company) {
        if (!company.getType().getId().equalsIgnoreCase("Minor"))
            return true;
        return company.isClosed();
    }

    private static boolean checkForPrussianPrivateExchange(PrivateCompany company) {
        if ((!company.getId().equals("HB")) && (!company.getId().equals("BB")))
            return true;
        return company.isClosed();
    }

    @Override
    protected void finishRound() {
        if (isRoundFinished())
            return;
        setRoundFinished(true);
        // UI CLEANUP: Force ORPanel to clear sticky buttons and reset to "Stock Round"
        // grey state.
        // We use reflection to avoid hard dependency on the UI package.
        try {
            Class<?> orPanelClass = Class.forName("net.sf.rails.ui.swing.ORPanel");
            java.lang.reflect.Method cleanupMethod = orPanelClass.getMethod("forceGlobalCleanup");
            cleanupMethod.invoke(null);
        } catch (Throwable t) {
        }

        if (this.interruptedRound != null) {
            ReportBuffer.add(this, "End of " + GameDef_1835.PR_ID + " formation. Resuming "
                    + this.interruptedRound.getRoundName() + ".");
        } else {
            ReportBuffer.add(this, "End of " + GameDef_1835.PR_ID + " formation.");
        }

        getRoot().getReportManager().getDisplayBuffer().clear();
        PublicCompany prussian = companyManager.getPublicCompany(GameDef_1835.PR_ID);
        if (prussian.hasStarted())
            prussian.checkPresidency();
        prussian.setOperated();

        // Fix for Double-PFR trigger: Ensure GameManager knows PFR has been
        // handled/offered
        // for this phase, so it doesn't trigger again immediately upon resume.
        if (gameManager instanceof GameManager_1835) {
            ((GameManager_1835) gameManager).setPfrDeclined();
        }

        if (this.interruptedRound != null) {
            RoundFacade roundToResume = gameManager.getInterruptedRound();
            gameManager.setInterruptedRound(null);
            gameManager.setRound(roundToResume);

            guiHints.setCurrentRoundType(roundToResume.getClass());
            guiHints.setVisibilityHint(GuiDef.Panel.STOCK_MARKET, false);
            guiHints.setActivePanel(GuiDef.Panel.MAP);

            roundToResume.resume();
        } else {
            gameManager.nextRound(this);
        }
    }

    @Override
    public String toString() {
        return "1835 PrussianFormationRound";
    }

    @Override
    public boolean setPossibleActions() {
        Player m2Pres = (m2 != null) ? m2.getPresident() : null;
        log.info("TRACE_PFR_ACTIONS: Step=[{}], M2_Pres=[{}], CurrentPlayer=[{}], RoundFinished=[{}]",
                getPrussianStep(),
                (m2Pres != null ? m2Pres.getName() : "null"),
                (this.currentPlayer != null ? this.currentPlayer.getName() : "null"),
                isRoundFinished());

        if (isRoundFinished()) {
            possibleActions.clear();
            return false;
        }
        possibleActions.clear();

        log.info("TRACE_PFR_GEN: Generating actions for Step: {}. Acting Company: {}",
                getPrussianStep(), (m2 != null ? m2.getId() : "null"));

        this.currentPlayer = playerManager.getCurrentPlayer();

        // Safety fallback if player is null (happens during reloads/undos if not
        // synced)
        if (this.currentPlayer == null) {
            if (this.startingPlayer != null) {
                this.currentPlayer = this.startingPlayer;
                playerManager.setCurrentPlayer(this.currentPlayer);
            } else {
                PublicCompany p = companyManager.getPublicCompany(GameDef_1835.PR_ID);
                if (p != null && p.getPresident() != null) {
                    this.currentPlayer = p.getPresident();
                    setStartingPlayer(p.getPresident());
                    playerManager.setCurrentPlayer(this.currentPlayer);
                }
            }
        }
        if (getPrussianStep() == Step.PRESIDENCY_SWAP) {
            // Re-calculate options to display buttons
            Player oldPres = getPlayerByName(swapOldPresName.value());
            Player newPres = getPlayerByName(swapNewPresName.value());

            if (oldPres == null || newPres == null) {
                setPrussianStep(Step.MERGE);
                return setPossibleActions();
            }

            // Ensure current player is the OLD president (the one choosing)
            if (this.currentPlayer != oldPres) {
                setCurrentPlayer(oldPres);
            }

            List<List<PublicCertificate>> options = calculateSwapOptions(newPres, oldPres);

            int index = 0;
            for (List<PublicCertificate> opt : options) {
                PresidencySwapChoice action = new PresidencySwapChoice(index++);

                String label = opt.stream()
                        .map(c -> c.getShare() + "%")
                        .collect(Collectors.joining(" + "));

                action.setButtonLabel(
                        "<html><center><b>Receive: " + label + "</b><br>Give Director Cert</center></html>");
                possibleActions.add(action);
            }
            return true;
        }

        PublicCompany m2 = companyManager.getPublicCompany(M2_ID);

        // ROBUST PLAYER ENFORCEMENT
        // If we are in the START step, M2 MUST be the active player.
        // If the engine's cleanup logic (from OperatingRound) reverted the player
        // to the previous Operating Company (e.g. M1), we detect it and FIX it here.
        if (getPrussianStep() == Step.START) {
            Player m2President = (m2 != null) ? m2.getPresident() : null;

            if (m2President != null && this.currentPlayer != m2President) {
                log.info("PFR Context Drift Detected: Player is {}, forcing {}",
                        this.currentPlayer.getName(), m2President.getName());
                setCurrentPlayer(m2President);
                // The currentPlayer field is now updated, so the check below will pass.
            }

            // (The original failure block is effectively removed/bypassed by the fix above)
            if (m2President == null) {
                // Only fail if M2 has no president at all (impossible in active game)
                gameManager.process(new NullAction(getRoot(), NullAction.Mode.DONE));
                return false;
            }

            StartPrussian startAction = new StartPrussian(m2);
            possibleActions.add(startAction);
            // Allow the user to Decline/Pass the formation offer
            NullAction passAction = new NullAction(getRoot(), NullAction.Mode.PASS);
            possibleActions.add(passAction);

            return true;
        } else if (getPrussianStep() == Step.MERGE) {
            setFoldablePrePrussians();

            if (currentPlayer == null) {
                return false;
            }

            String playerName = currentPlayer.getName();

            int index = 1;
            for (Company company : foldablePrePrussians) {
                ExchangeForPrussianShare action = new ExchangeForPrussianShare(company);
                String key = String.valueOf(index++);
                String label = String.format("<html><center><b>%s: %s</b><br>Exchange %s</center></html>",
                        key, playerName, company.getId());
                action.setButtonLabel(label);
                possibleActions.add(action);
            }

            NullAction done = new NullAction(getRoot(), NullAction.Mode.DONE);
            String doneLabel = foldablePrePrussians.isEmpty() ? "Done (Nothing)" : "Pass (Keep)";

            // Added Player Name to the Done Button for clarity
            done.setButtonLabel("<html><center><b>D: " + doneLabel + "</b><br>" + playerName + "</center></html>");
            possibleActions.add(done);

        } else if (getPrussianStep() == Step.DISCARD_TRAINS) {
            int index = 1;
            for (Train train : prussian.getPortfolioModel().getUniqueTrains()) {
                DiscardTrain action = new DiscardTrain(prussian, train);
                String key = String.valueOf(index++);
                action.setButtonLabel("<html><center><b>" + key + ": Discard</b><br>" + train.getType().getName()
                        + "</center></html>");
                possibleActions.add(action);
            }
        }
        return true;
    }

    private List<List<PublicCertificate>> calculateSwapOptions(Player newCandidate, Player currentPres) {
        int needed = prussian.getPresidentsShare().getShares(); // 10% usually (2 shares of 5%)
        List<PublicCertificate> ordinaryCerts = new ArrayList<>();

        for (PublicCertificate c : newCandidate.getPortfolioModel().getCertificates(prussian)) {
            if (!c.isPresidentShare()) {
                ordinaryCerts.add(c);
            }
        }

        List<List<PublicCertificate>> options = new ArrayList<>();

        // Option A: Single certificates matching the size
        for (PublicCertificate c : ordinaryCerts) {
            if (c.getShares() == needed) {
                options.add(Collections.singletonList(c));
            }
        }

        // Option B: Pairs summing to size
        for (int i = 0; i < ordinaryCerts.size(); i++) {
            for (int j = i + 1; j < ordinaryCerts.size(); j++) {
                PublicCertificate c1 = ordinaryCerts.get(i);
                PublicCertificate c2 = ordinaryCerts.get(j);
                if (c1.getShares() + c2.getShares() == needed) {
                    List<PublicCertificate> pair = new ArrayList<>();
                    pair.add(c1);
                    pair.add(c2);
                    options.add(pair);
                }
            }
        }

        // Deduplicate options by signature (to avoid showing "5%+5%" twice if they are
        // identical logic)
        Map<String, List<PublicCertificate>> unique = new HashMap<>();
        for (List<PublicCertificate> opt : options) {
            String sig = opt.stream()
                    .map(PublicCertificate::getShares)
                    .sorted()
                    .map(String::valueOf)
                    .collect(Collectors.joining("+"));
            unique.putIfAbsent(sig, opt);
        }

        return new ArrayList<>(unique.values());
    }

    private void checkAndHandlePresidencySwap(Player newCandidate) {
        Player currentPres = prussian.getPresident();

        // Standard checks
        if (currentPres == null || currentPres == newCandidate) {
            prussian.checkPresidency();
            return;
        }

        int newPct = newCandidate.getPortfolioModel().getShare(prussian);
        int oldPct = currentPres.getPortfolioModel().getShare(prussian);

        if (newPct > oldPct) {

            List<List<PublicCertificate>> options = calculateSwapOptions(newCandidate, currentPres);

            if (options.isEmpty()) {
                // No valid swap found? Fallback to engine default (might be messy but safe)
                prussian.checkPresidency();
            } else if (options.size() == 1) {
                // Only one way to do it, execute immediately
                performSwap(newCandidate, currentPres, options.get(0));
            } else {
                // Ambiguity! Enter SWAP Step.
                swapOldPresName.set(currentPres.getName());
                swapNewPresName.set(newCandidate.getName());
                setPrussianStep(Step.PRESIDENCY_SWAP);
                setCurrentPlayer(currentPres);
                // The next call to setPossibleActions will generate the buttons
            }
        }
    }

    private void executePresidencySwapChoice(PresidencySwapChoice action) {
        Player oldPres = getPlayerByName(swapOldPresName.value());
        Player newPres = getPlayerByName(swapNewPresName.value());

        List<List<PublicCertificate>> options = calculateSwapOptions(newPres, oldPres);
        if (action.getOptionIndex() >= 0 && action.getOptionIndex() < options.size()) {
            performSwap(newPres, oldPres, options.get(action.getOptionIndex()));
        } else {
            prussian.checkPresidency(); // Fallback
        }

        // Reset state
        swapOldPresName.set(null);
        swapNewPresName.set(null);
        setPrussianStep(Step.MERGE);

        // Restore current player to the one who was acting (usually the new
        // president/exchanger)
        // Actually, PFR Merge logic expects 'currentPlayer' to be the one iterating
        // exchanges.
        // We should ensure that flow continues correctly.
        // The loop in Merge uses `currentPlayer`, so we should probably restore it to
        // `newPres`
        // (who triggered the swap by buying/exchanging).
        setCurrentPlayer(newPres);
    }

    private void performSwap(Player newCandidate, Player currentPres, List<PublicCertificate> selectedCertificates) {

        // 1. Move President Cert to New Candidate
        PublicCertificate presCert = prussian.getPresidentsShare();
        presCert.moveTo(newCandidate);

        // 2. Move Chosen Ordinary Certs to Old President
        for (PublicCertificate c : selectedCertificates) {
            c.moveTo(currentPres);
        }

        // 3. Official Engine Check (should now pass without issue)
        // We call setPresident directly or rely on checkPresidency to update the
        // reference
        prussian.setPresident(newCandidate); // Explicit set to ensure engine is in sync

        ReportBuffer.add(this, LocalText.getText("IS_NOW_PRES_OF", newCandidate.getName(), prussian.getId()));
    }

    private void executeExchange(List<Company> companies, boolean president, boolean display) {
        ExchangeForShare efs;
        PublicCertificate cert;
        Owner owner;

        for (Company company : companies) {
            if (company instanceof PrivateCompany) {
                owner = ((PrivateCompany) company).getOwner();
            } else {
                owner = ((PublicCompany) company).getPresident();
                if (owner == null) {
                    owner = ipo.getParent();
                }
            }

            // Robustly find the ExchangeForShare property
            efs = null;
            Set<SpecialProperty> sps = company.getSpecialProperties();
            if (sps != null) {
                for (SpecialProperty sp : sps) {
                    if (sp instanceof ExchangeForShare) {
                        efs = (ExchangeForShare) sp;
                        break;
                    }
                }
            }
            if (efs == null) {
                // FALLBACK for BB/HB if property is missing but hardcoded exchange logic
                // triggered
                if (company.getId().equals("BB") || company.getId().equals("HB")) {
                    // Logic handled below by neededShare fallback
                } else {
                    continue;
                }
            }

            boolean isPresidentShare = president && (owner instanceof Player);

            // Fix for M1 (5%) and integer division issues.
            // New approach: Find by explicit percentage matching
            cert = null;

            int neededShare;
            if (efs != null) {
                neededShare = efs.getShare();
            } else {
                // User defined rules:
                // 10% Shares: BB, HB, M2, M4
                // 5% Shares: M1, M3, M5, M6
                String id = company.getId();
                if ("M1".equals(id) || "M3".equals(id) || "M5".equals(id) || "M6".equals(id)) {
                    neededShare = 5;
                } else {
                    // BB, HB, M2, M4 default to 10%
                    neededShare = 10;
                }
            }

            // 1. Try to find exact match in UNAVAILABLE pile (ID Check + Pres Check)
            for (PublicCertificate c : unavailable.getCertificates()) {
                if (c.getCompany().getId().equals(prussian.getId()) && c.getShare() == neededShare) {
                    if (isPresidentShare == c.isPresidentShare()) {
                        cert = c;
                        break;
                    }
                }
            }

            // 2. Try to find exact match in IPO pile (ID Check + Pres Check)
            // Shares often move to IPO after the company starts, so we MUST check here too.
            if (cert == null) {
                for (PublicCertificate c : ipo.getCertificates()) {
                    if (c.getCompany().getId().equals(prussian.getId()) && c.getShare() == neededShare) {
                        if (isPresidentShare == c.isPresidentShare()) {
                            cert = c;
                            break;
                        }
                    }
                }
            }

            // 3. Fallback: UNAVAILABLE pile - Match size only (ignore President status)
            if (cert == null) {
                for (PublicCertificate c : unavailable.getCertificates()) {
                    if (c.getCompany().getId().equals(prussian.getId()) && c.getShare() == neededShare) {
                        cert = c;
                        break;
                    }
                }
            }

            // 4. Fallback: IPO pile - Match size only (ignore President status)
            if (cert == null) {
                for (PublicCertificate c : ipo.getCertificates()) {
                    if (c.getCompany().getId().equals(prussian.getId()) && c.getShare() == neededShare) {
                        cert = c;
                        break;
                    }
                }
            }

            if (cert != null) {
                cert.moveTo(owner);
            } else {
                // Fallback to original method just in case (though likely to fail if it was 0)
                try {
                    int shareUnits = neededShare / prussian.getShareUnit();
                    if (shareUnits == 0)
                        shareUnits = 1; // Safety for 5% shares

                    cert = unavailable.findCertificate(prussian, shareUnits, isPresidentShare);
                    if (cert != null)
                        cert.moveTo(owner);
                } catch (Exception e) {
                    log.warn("Failed to find certificate for exchange via fallback method.");
                }
            }

            String ownerName = owner.getId();
            ReportBuffer.add(this, LocalText.getText("MERGE_MINOR_LOG",
                    ownerName, company.getId(), PR_ID,
                    company instanceof PrivateCompany ? "no" : Bank.format(this, ((PublicCompany) company).getCash()),
                    company instanceof PrivateCompany ? "no"
                            : ((PublicCompany) company).getPortfolioModel().getTrainList().size()));

            if (company instanceof PublicCompany) {
                PublicCompany minor = (PublicCompany) company;
                boolean isM5 = minor.getId().equals("M5");

                BaseToken minorToken = null;
                for (BaseToken token : minor.getAllBaseTokens()) {
                    if (token.getOwner() instanceof Stop) {
                        minorToken = token;
                        break;
                    }
                }

                if (minorToken != null && minorToken.getOwner() instanceof Stop) {
                    Stop city = (Stop) minorToken.getOwner();
                    MapHex hex = city.getParent();
                    minorToken.moveTo(minor);

                    if (!isM5) {
                        if (hex.layBaseToken(prussian, city)) {
                            String msg = LocalText.getText("ExchangesBaseToken", PR_ID, minor.getId(),
                                    city.getStationComposedId());
                            ReportBuffer.add(this, msg);
                            if (display)
                                DisplayBuffer.add(this, msg);
                            prussian.layBaseToken(hex, 0);
                        } else if (hex.hasTokenOfCompany(prussian)) {
                            ReportBuffer.add(this,
                                    LocalText.getText("ReplacesMinorToken", minor.getId(), prussian.getId()));
                        }
                    }
                }

                if (minor.getCash() > 0)
                    net.sf.rails.game.state.Currency.wireAll(minor, prussian);
                List<Train> trains = new ArrayList<>(minor.getPortfolioModel().getTrainList());
                for (Train train : trains)
                    prussian.getPortfolioModel().addTrain(train);
            }
            company.setClosed();

            // After receiving shares, the player might have overtaken the current PR
            // Director.
            if (owner instanceof Player) {
                checkAndHandlePresidencySwap((Player) owner);
            }
        }
    }

    private void advanceToNextValidPlayer() {
        int count = 0;
        int maxPlayers = playerManager.getNumberOfPlayers();

        // Loop until we find a player with shares OR we cycled through everyone
        while (count < maxPlayers) {
            setFoldablePrePrussians(); // Updates foldablePrePrussians for currentPlayer

            if (!foldablePrePrussians.isEmpty()) {
                return; // Found a player with actions, let them play
            }

            // No actions possible? Log it and move to next immediately (Atomic State
            // Change)
            ReportBuffer.add(this, currentPlayer.getName() + " has no exchangeable shares. Auto-Pass.");

            // Use standard next player logic
            Player nextPlayer = playerManager.getNextPlayer();
            setCurrentPlayer(nextPlayer);

            mergeTurnCount.add(1); // Track turns for end condition
            count++;
        }

        // If we loop through everyone and nobody can do anything:
        finishMergeStep();
    }

}