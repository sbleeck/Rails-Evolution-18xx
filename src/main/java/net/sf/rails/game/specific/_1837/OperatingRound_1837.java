package net.sf.rails.game.specific._1837;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import net.sf.rails.game.*;
import net.sf.rails.game.state.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.sf.rails.game.financial.StockSpace;
import net.sf.rails.common.DisplayBuffer;
import net.sf.rails.common.LocalText;
import net.sf.rails.common.ReportBuffer;
import net.sf.rails.game.financial.Bank;
import net.sf.rails.game.financial.PublicCertificate;
import net.sf.rails.game.financial.StockMarket;
import net.sf.rails.util.SequenceUtil;
import rails.game.action.*;
import net.sf.rails.algorithms.RevenueAdapter;
import rails.game.specific._1837.SetHomeHexLocation;
import net.sf.rails.game.model.PortfolioOwner;
import net.sf.rails.game.model.PortfolioModel;
import net.sf.rails.game.financial.Bank;
import java.util.HashSet;
import java.lang.reflect.Field;

/**
 * @author Martin
 *
 */
public class OperatingRound_1837 extends OperatingRound {
    private static final Logger log = LoggerFactory.getLogger(OperatingRound_1837.class);
    protected final ArrayListState<String> triggeredNationals = new ArrayListState<>(this, "triggeredNationals");
    protected final BooleanState specialActionPhase = new BooleanState(this, "specialActionPhase", false);
    protected final IntegerState specialActionPlayerCount = IntegerState.create(this, "specialActionPlayerCount", 0);
    protected final IntegerState specialActionCurrentIndex = IntegerState.create(this, "specialActionCurrentIndex", 0);
    // Track if the special phase is fully completed for this round to prevent
    // re-entry loops
    protected final BooleanState specialActionPhaseFinished = new BooleanState(this, "specialActionPhaseFinished",
            false);

    protected final BooleanState phase3Triggered = new BooleanState(this, "phase3Triggered", false);
    protected final BooleanState phase4Triggered = new BooleanState(this, "phase4Triggered", false);
    protected final BooleanState phase5Triggered = new BooleanState(this, "phase5Triggered", false);
    protected final ArrayListState<String> declinedNationals = new ArrayListState<>(this, "declinedNationals");
    // Static cache to persist triggers even if State rollback occurs during
    // interrupt

    // 1: Use primitive types to bypass State constructor visibility issues for
    // now

    // The constructor is private; we must use the static factory method .create()
    // protected final IntegerState kkFormationState = IntegerState.create(this,
    // "kkFormationState", 0);
    // private boolean kkFormedThisTurn = false;

    // Use 'new' with 2 arguments for ArrayListState
    protected final ArrayListState<String> skippedMinors = new ArrayListState<>(this, "skippedMinors");

    // protected final IntegerState ugFormationState = IntegerState.create(this,
    // "ugFormationState", 0);

    // Use '.create' for StringState (Constructor is private)
    protected final StringState currentSpecialCompanyId = StringState.create(this, "currentSpecialCompanyId");

    public OperatingRound_1837(GameManager parent, String id) {
        super(parent, id);
    }

    /**
     * Logic to handle Voluntary Discard in 1837.
     * Call this INSTEAD of the standard train buying generation if the company is
     * at the limit.
     */
    protected void setVoluntaryDiscardActions() {
        PublicCompany company = operatingCompany.value();

        // 1. Iterate unique train types
        for (Train train : company.getPortfolioModel().getUniqueTrains()) {
            // 2. Calculate Scrap Price
            int scrapPrice = train.getCost() / 2;

            // Only allow discard if the company has enough cash to pay the scrap price
            if (company.getCash() >= scrapPrice) {
                List<Train> singleTrainList = new ArrayList<>();
                singleTrainList.add(train);

                DiscardTrainVoluntarily action = new DiscardTrainVoluntarily(company, singleTrainList);

                String priceStr = Bank.format(gameManager, scrapPrice);

                // 3. Clean Name: Remove the unique ID suffix (e.g., "3_1" -> "3")
                String cleanName = train.getName().replaceAll("_\\d+$", "");

                // 4. Set final label: "Discard 3 (100)"
                action.setLabel("Discard " + cleanName + " (" + priceStr + ")");

                possibleActions.add(action);
            }
        }

        // Add the Skip/Pass Action
        NullAction done = new NullAction(getRoot(), NullAction.Mode.DONE);
        done.setLabel("Done / Keep Trains");
        possibleActions.add(done);
    }

    /**
     * 1837 nationals may not lay their reserved tokens elsewhere
     * until formation is complete
     * (This I presume, the v2.0 rules are not clear on that matter. EV)
     */
    @Override
    protected boolean canLayAnyTokens(boolean resetTokenLays) {

        PublicCompany_1837 company = (PublicCompany_1837) operatingCompany.value();

        if (company.getType().getId().equals("National") && !company.isComplete()) {
            return false;
        } else {
            return super.canLayAnyTokens(resetTokenLays);
        }
    }

    public boolean processGameSpecificAction(PossibleAction action) {

        if (action instanceof ExchangeMinorAction) {
            ExchangeMinorAction exc = (ExchangeMinorAction) action;
            // Execute merge
            Mergers.mergeCompanies(gameManager, exc.getMinor(), exc.getTargetMajor(), false, false);
            // Refresh actions (stay in special phase)
            setPossibleActions();
            return true;
        }

        if (action instanceof SetHomeHexLocation) {
            SetHomeHexLocation selectHome = (SetHomeHexLocation) action;
            PublicCompany company = selectHome.getCompany();
            MapHex chosenHome = selectHome.getSelectedHomeHex();
            if (chosenHome == null) {
                // If no hex is selected yet, we are likely just initializing the dialog
                // request.
                return true;
            }
            company.setHomeHex(chosenHome);
            company.layHomeBaseTokens();
            return true;
        } else {
            return false;
        }
    }

    private boolean processSpecialDone() {
        // Logic moved from processGameSpecificAction
        int count = specialActionPlayerCount.value() + 1;
        specialActionPlayerCount.set(count);
        List<Player> players = gameManager.getPlayers();

        if (count >= players.size()) {
            specialActionPhase.set(false);
            specialActionPhaseFinished.set(true);

            // We must now trigger the start of the "Real" OR.
            super.start();
        } else {
            int nextIndex = (specialActionCurrentIndex.value() + 1) % players.size();
            specialActionCurrentIndex.set(nextIndex);

            Player nextPlayer = players.get(nextIndex);
            getRoot().getPlayerManager().setCurrentPlayer(nextPlayer);

        }
        return true;
    }

    @Override
    protected String validateSetRevenueAndDividend(SetDividend action) {
        String errMsg = null;
        PublicCompany company;
        String companyName;
        int amount, directAmount;
        int revenueAllocation;

        // Dummy loop to enable a quick jump out.
        while (true) {

            // Checks
            // Must be correct company.
            company = action.getCompany();
            companyName = company.getId();
            if (company != operatingCompany.value()) {

                break;
            }
            // Must be correct step
            if (getStep() != GameDef.OrStep.CALC_REVENUE) {
                break;
            }

            // Amount must be non-negative multiple of 5
            amount = action.getActualRevenue();
            if (amount < 0) {
                break;
            }
            if (amount % 5 != 0) {
                break;
            }

            // Direct revenue must be non-negative multiple of 5,
            // and at least 10 less than the total revenue
            directAmount = action.getActualCompanyTreasuryRevenue();
            if (directAmount < 0) {
                break;
            }
            if (directAmount % 5 != 0) {
                break;
            }
            if (amount > 0 && amount - directAmount < 10) {
                break;
            }

            // Check chosen revenue distribution
            revenueAllocation = action.getRevenueAllocation();
            if (amount > 0) {
                // Check the allocation type index (see SetDividend for values)
                if (revenueAllocation < 0
                        || revenueAllocation >= SetDividend.NUM_OPTIONS) {
                    break;
                }

                // Validate the chosen allocation type
                int[] allowedAllocations = ((SetDividend) selectedAction).getAllowedAllocations();
                boolean valid = false;
                for (int aa : allowedAllocations) {
                    if (revenueAllocation == aa) {
                        valid = true;
                        break;
                    }
                }
                if (!valid) {
                    break;
                }
            } else if (revenueAllocation != SetDividend.NO_ROUTE) {
                // If there is no revenue, use withhold.
                // 1837 Logic: Do NOT force Withhold if the company is mandatory Split
                // (Coal/Minor).
                // This prevents the engine from flipping a "Split 0" (pre-calc) action to
                // "Withhold".
                PublicCompany opComp = operatingCompany.value();
                String type = opComp.getType().getId();
                boolean isMandatorySplit = type.equalsIgnoreCase("Coal") || type.equalsIgnoreCase("Minor");

                if (!isMandatorySplit) {
                    // Standard behavior: If no revenue, default to Withhold.
                    action.setRevenueAllocation(SetDividend.WITHHOLD);
                }
            }

            if (amount == 0 && operatingCompany.value().getNumberOfTrains() == 0) {
                DisplayBuffer.add(this, LocalText.getText("RevenueWithNoTrains",
                        operatingCompany.value().getId(),
                        Bank.format(this, 0)));
            }

            break;
        }

        return errMsg;
    }

    public void splitRevenue(int amount) {
        if (amount > 0) {
            int withheld = calculateCompanyIncomeFromSplit(amount);
            String withheldText = Currency.fromBank(withheld, operatingCompany.value());

            ReportBuffer.add(this, LocalText.getText("Receives",
                    operatingCompany.value().getId(), withheldText));
            // Payout the remainder
            int payed = amount - withheld;
            payout(payed, true);
        }

    }

    @Override
    protected int calculateCompanyIncomeFromSplit(int revenue) {
        return roundIncome(0.5 * revenue, Rounding.UP, ToMultipleOf.ONE);
    }

    /*
     * Rounds up or down the individual payments based on the boolean value
     */
    public void payout(int amount, boolean split) {
        if (amount == 0)
            return;

        int part;
        int shares;

        Map<MoneyOwner, Integer> sharesPerRecipient = countSharesPerRecipient();

        // Calculate, round up or down, report and add the cash

        // Define a precise sequence for the reporting
        Set<MoneyOwner> recipientSet = sharesPerRecipient.keySet();
        for (MoneyOwner recipient : SequenceUtil.sortCashHolders(recipientSet)) {
            if (recipient instanceof Bank)
                continue;

            shares = (sharesPerRecipient.get(recipient));
            if (shares == 0)
                continue;

            double payoutPerShare = amount * operatingCompany.value().getShareUnit() / 100.0;
            part = calculateShareholderPayout(payoutPerShare, shares);

            String partText = Currency.fromBank(part, recipient);
            ReportBuffer.add(this, LocalText.getText("Payout",
                    recipient.getId(),
                    partText,
                    shares,
                    operatingCompany.value().getShareUnit()));
        }

        // Move the token
        ((PublicCompany_1837) operatingCompany.value()).payout(amount, split);
    }

    @Override
    protected int calculateShareholderPayout(double payoutPerShare, int numberOfShares) {
        return roundShareholderPayout(payoutPerShare, numberOfShares,
                Rounding.DOWN, Multiplication.BEFORE_ROUNDING);
    }

    /*
     * (non-Javadoc)
     * * @see
     * net.sf.rails.game.OperatingRound#gameSpecificTileLayAllowed(net.sf.rails.game
     * .PublicCompany, net.sf.rails.game.MapHex, int)
     */
    @Override
    protected int processSpecialRevenue(int earnings, int specialRevenue) {
        int dividend = earnings;
        PublicCompany company = operatingCompany.value();
        if (specialRevenue > 0) {
            dividend -= specialRevenue;
            company.setLastDirectIncome(specialRevenue);
            ReportBuffer.add(this, LocalText.getText("CompanyDividesEarnings",
                    company,
                    Bank.format(this, earnings),
                    Bank.format(this, dividend),
                    Bank.format(this, specialRevenue)));
            Currency.fromBank(specialRevenue, company);
        }
        company.setLastDividend(dividend);
        return dividend;
    }

    @Override
    public int getTileLayCost(PublicCompany company, MapHex hex, int standardCost) {
        // 1. If the hex is NOT blocked, just return the standard cost (paying for
        // rivers, etc.)
        if (!hex.isBlockedByPrivateCompany()) {
            return standardCost;
        }

        // 2. If the hex IS blocked, but isTileLayAllowed returned TRUE,
        // it means we are exercising the "Mountain Railway" exception (Owner/President
        // check).
        // In 1837, exercising this right waives the terrain cost.
        if (isTileLayAllowed(company, hex, -1)) {
            log.debug("OR_1837: Waiving cost for hex " + hex.getId() + " due to Mountain Railway rights.");
            return 0;
        }

        // Fallback (shouldn't happen if isTileLayAllowed is checked first)
        return standardCost;
    }

    @Override
    public boolean gameSpecificTileLayAllowed(PublicCompany company,
            MapHex hex, int orientation) {

        RailsRoot root = gameManager.getRoot();
        int phaseIndex = root.getPhaseManager().getCurrentPhase().getIndex();
        String hexId = hex.getId();

        // -----------------------------------------------------------
        // 1. CHECK PRIVATE BLOCKING
        // -----------------------------------------------------------
        if (phaseIndex < 3) {
            if (hex.isBlockedByPrivateCompany()) {
                PrivateCompany blocker = hex.getBlockingPrivateCompany();
                Owner owner = blocker.getOwner();
                Player president = company.getPresident();

                // Exception 1: Company owns the private
                if (owner != null && (owner == company || owner.equals(company))) {
                }
                // Exception 2: President owns the private
                else if (owner != null && president != null && (owner == president || owner.equals(president))) {
                } else {
                    return false;
                }
            }
        }

        // -----------------------------------------------------------
        // 2. CHECK RESERVATIONS
        // -----------------------------------------------------------
        if (hex.isReservedForCompany()) {
            PublicCompany reservedFor = hex.getReservedForCompany();

            if (reservedFor != null && reservedFor != company) {
                // : If the reserved company is CLOSED (e.g. WT absorbed by S2), ignore the
                // reservation
                if (reservedFor.isClosed()) {
                } else {
                    return false;
                }
            }
        }

        // -----------------------------------------------------------
        // 3. CHECK PHASE RESTRICTIONS
        // -----------------------------------------------------------
        if (phaseIndex >= 4) {
            String[] italyHexes = GameDef_1837.ItalyHexes.split(",");
            for (String italyHex : italyHexes) {
                if (hex.getId().equals(italyHex)) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    protected void prepareRevenueAndDividendAction() {
        PublicCompany company = operatingCompany.value();

        // DEBUG: Unconditional proof that the method is running
        log.debug("OR_1837 DEBUG: prepareRevenueAndDividendAction START for " + company.getId());

        // There is only revenue if there are any trains
        if (companyHasRunningTrains(false)) {

            String type = company.getType().getId();

            // Identify Mandatories (Minors/Coal)
            boolean isMandatorySplit = type.equalsIgnoreCase("Coal") || type.equalsIgnoreCase("Minor");

            // G-Train Detection ---
            boolean hasGTrains = false;
            if (company.hasTrains()) {
                // Use getTrains() directly if available, or via PortfolioModel
                for (Train t : company.getPortfolioModel().getTrainList()) {
                    if (t.getName().contains("G")) {
                        hasGTrains = true;
                        log.debug("OR_1837 DEBUG: G-Train detected: " + t.getName());
                        break;
                    }
                }
            }

            int[] allowedRevenueActions;
            int defaultAllocation;

            int revenueToUse = company.getLastRevenue();
            int directIncomeToUse = company.getLastDirectIncome();

            log.debug(
                    "OR_1837 DEBUG: Initial Values -> Revenue=" + revenueToUse + ", Direct(Mine)=" + directIncomeToUse);

            // We force this if it's a G-Train owner (Major) OR a Mandatory Split (Minor).
            // We removed the checks for "== 0" to ensure we ALWAYS get the correct Mine
            // split.
            if (isMandatorySplit || hasGTrains) {
                log.debug("OR_1837 DEBUG: forcing RevenueAdapter calculation...");
                try {
                    RevenueAdapter ra = RevenueAdapter.createRevenueAdapter(getRoot(), company,
                            getRoot().getPhaseManager().getCurrentPhase());
                    if (ra != null) {
                        ra.initRevenueCalculator(true); // true = multigraph support
                        revenueToUse = ra.calculateRevenue();
                        directIncomeToUse = ra.getSpecialRevenue(); // Capture 1837 Mine Revenue logic

                        log.debug("OR_1837 DEBUG: Recalculation Result -> Total=" + revenueToUse + ", Mine="
                                + directIncomeToUse);
                    } else {
                        log.warn("OR_1837 DEBUG: RevenueAdapter was NULL");
                    }
                } catch (Exception e) {
                    log.error("OR_1837 DEBUG: Failed to calculate revenue", e);
                }
            }

            // Determine Button Options
            if (company.isSplitAlways() || isMandatorySplit) {
                // CASE 1: Mandatory Split (Coal / Minor)
                allowedRevenueActions = new int[] { SetDividend.SPLIT };
                defaultAllocation = SetDividend.SPLIT;
            } else if (company.isSplitAllowed()) {
                // CASE 2: Optional Split (Some Majors)
                allowedRevenueActions = new int[] { SetDividend.PAYOUT,
                        SetDividend.SPLIT,
                        SetDividend.WITHHOLD };
                defaultAllocation = SetDividend.PAYOUT;
            } else {
                // CASE 3: Standard Major (MS, BK, etc.)
                // Even without SPLIT option, SetDividend handles 'directIncomeToUse' correctly
                allowedRevenueActions = new int[] {
                        SetDividend.PAYOUT,
                        SetDividend.WITHHOLD };
                defaultAllocation = SetDividend.PAYOUT;
            }

            // Create the action with the FORCED values
            possibleActions.add(new SetDividend(getRoot(),
                    revenueToUse, // Total Revenue
                    directIncomeToUse, // Fixed/Mine Revenue (goes to Treasury)
                    true, allowedRevenueActions, defaultAllocation));

            log.debug("OR_1837 DEBUG: Action Added. DirectIncome param = " + directIncomeToUse);

        } else {
            log.debug("OR_1837 DEBUG: No running trains for " + company.getId());
        }
    }

    /**
     * Can the operating company buy a train now?
     * In 1837 it is allowed if another (different) train is scrapped.
     *
     * @return True if the company is allowed to buy a train
     */
    protected boolean canBuyTrainNow() {
        return isBelowTrainLimit();
    }

    /**
     * If a train has already run this round for a minor,
     * it may not run again after a merger into a major.
     * * @param display Should be true only once.
     * 
     * @return True if there is at least one train that is allowed to run
     */
    @Override
    protected boolean companyHasRunningTrains(boolean display) {
        boolean hasRunningTrains = false;
        Set<Train> trains = operatingCompany.value().getPortfolioModel().getTrainList();

        for (Train train : trains) {
            if (gameManager.isTrainBlocked(train)) {
                if (display) {
                    String message = LocalText.getText(
                            "TrainInherited", train.getType(), operatingCompany.value());
                    ReportBuffer.add(this, message);
                }
            } else {
                hasRunningTrains = true;
            }
        }
        return hasRunningTrains;
    }

    /**
     * New standard method to allow discarding trains when at the train limit.
     * Note: 18EU has a different procedure for discarding Pullmann trains.
     * * @param company Operating company
     * 
     * @param newTrain Train to get via exchange
     */
    @Override
    protected void addOtherExchangesAtTrainLimit(PublicCompany company, Train newTrain) {
        // May only discard train if at train limit.
        if (isBelowTrainLimit())
            return;

        Set<Train> oldTrains = company.getPortfolioModel().getUniqueTrains();

        for (Train oldTrain : oldTrains) {
            // May not exchange for same type
            if (oldTrain.getType().equals(newTrain.getType()))
                continue;
            // New train cost is raised with half the old train cost
            int price = newTrain.getCost() + oldTrain.getCost() / 2;
            if (price > company.getCash())
                continue;

            BuyTrain buyTrain = new BuyTrain(newTrain, bank.getIpo(), price);
            buyTrain.setTrainForExchange(oldTrain);
            possibleActions.add(buyTrain);
        }
    }

    private void processMandatoryExchanges() {
        boolean isPhase5 = getRoot().getPhaseManager().hasReachedPhase("5");
        // Create a copy to avoid ConcurrentModificationException during merges
        List<PublicCompany> companies = new ArrayList<>(gameManager.getAllPublicCompanies());

        for (PublicCompany coal : companies) {
            // Filter for active Coal companies
            if (coal.isClosed())
                continue;
            // Ensure we only target Coal companies (or Minors acting as such if type is
            // shared)
            if (!"Coal".equals(coal.getType().getId()))
                continue;

            PublicCompany major = Merger1837.getMergeTarget(gameManager, coal);
            if (major == null)
                continue;

            // Trigger Logic:
            // 1. Phase 5: Mandatory Immediate Merge (Even if Major is NOT floated).
            // 2. Sold Out: Mandatory Next-OR Merge (Major MUST be floated).

            boolean triggerMerge = false;
            String reason = "";

            if (isPhase5) {
                triggerMerge = true;
                reason = "Phase 5";
            } else {
                // Not Phase 5. Major must be floated to consider a Sold Out merge.
                if (major.hasFloated()) {
                    boolean isSoldOut = true;
                    // Check if any shares remain in the IPO
                    for (PublicCertificate cert : Bank.getIpo(gameManager).getPortfolioModel().getCertificates()) {
                        if (cert.getCompany().equals(major)) {
                            isSoldOut = false;
                            break;
                        }
                    }
                    if (isSoldOut) {
                        triggerMerge = true;
                        reason = "Major Sold Out";
                    }
                }
            }

            if (!triggerMerge)
                continue;

            // Execute Merge
            // Use 1837-specific merger to ensure exchange shares are transferred correctly
            Merger1837.mergeMinor(gameManager, coal, major);
            Merger1837.fixDirectorship(gameManager, major);

            // missing LocalText key: Use direct string construction
            ReportBuffer.add(this, "Company " + coal.getId() + " merged into " + major.getId() + " (" + reason + ")");

            // Use shared helper to check limits and generate discard actions if needed
            if (checkAndGenerateDiscardActions(major)) {
                String warning = "WARNING: " + major.getId() + " exceeds train limit after mandatory merger.";
                ReportBuffer.add(this, warning);
            }

        }
    }

    @Override
    protected void newPhaseChecks() {
        super.newPhaseChecks();

        // : Access PhaseManager via the gameManager field
        Phase currentPhase = getRoot().getPhaseManager().getCurrentPhase();

        if (currentPhase.getId().equals("3")) {
            if (!phase3Triggered.value()) {
                phase3Triggered.set(true);
                // ... (Existing Phase 3 logic for Bosnia/Private Companies) ...
                for (PrivateCompany comp : gameManager.getAllPrivateCompanies()) {
                    comp.unblockHexes();
                }
                MapManager map = getRoot().getMapManager();
                for (String bzhHex : GameDef_1837.BzHHexes.split(",")) {
                    map.getHex(bzhHex).setOpen(true);
                }
            }

        } else if (currentPhase.getId().equals("4")) {
            // STRICT MEMORY CHECK: Redundant safety for Phase change logic.
            if (!phase4Triggered.value()) {
                log.debug("Phase 4 Transition Detected. Executing One-Time Triggers.");
                phase4Triggered.set(true); // Mark memory: Undoable!

                try {
                    handlePhase4Trigger();
                } catch (Exception e) {
                    log.error("CRITICAL FAILURE in Phase 4 Trigger: " + e.getMessage());
                    e.printStackTrace();
                }
            }

        } else if (currentPhase.getId().equals("5")) {

            // --- PHASE 5 LOGIC ---
            // Compilable version using standard getters

        }
    }

    private boolean processExchangeMinor(ExchangeMinorAction action) {
        PublicCompany minor = action.getMinor();
        PublicCompany major = action.getTargetMajor();
        String minorId = minor.getId();

        // 1. Formation: Par & Float ONLY (No Flush)
        boolean isK1Formation = action.isFormation() || "K1".equals(minorId);
        boolean isU1Formation = action.isFormation() || "U1".equals(minorId);

        if ((isK1Formation || isU1Formation) && !major.hasFloated()) {

            StockMarket market = getRoot().getStockMarket();
            net.sf.rails.game.financial.StockSpace parSpace = null;
            int targetPar = isK1Formation ? 120 : 175;

            for (net.sf.rails.game.financial.StockSpace ss : market.getStartSpaces()) {
                if (ss.getPrice() == targetPar) {
                    parSpace = ss;
                    break;
                }
            }
            if (parSpace == null) {
                for (int r = 0; r < 50; r++) {
                    for (int c = 0; c < 50; c++) {
                        net.sf.rails.game.financial.StockSpace ss = market.getStockSpace(r, c);
                        if (ss != null && ss.getPrice() == targetPar) {
                            parSpace = ss;
                            break;
                        }
                    }
                    if (parSpace != null)
                        break;
                }
            }
            if (parSpace != null)
                major.setCurrentSpace(parSpace);
            Currency.fromBank(isK1Formation ? 840 : 875, major);

            if (!major.hasFloated()) {
                major.setFloated();
            }
            // STOP! Do not flush shares here. Leave them in Reserve.
            // We will fetch them one-by-one as needed below.
        }

        // 2. Identify TRUE Shareholders
        // U1/U3 force all shareholders to exchange. Others (K1-K3) are individual.
        // We scan for anyone holding the minor's paper.
        Set<Player> playersToProcess = new HashSet<>();

        // Use generic Owner check to be safe
        for (Player p : gameManager.getPlayers()) {
            if (p instanceof PortfolioOwner) {
                PortfolioModel pm = ((PortfolioOwner) p).getPortfolioModel();
                for (Object obj : pm.getCertificates()) {
                    if (obj instanceof PublicCertificate) {
                        PublicCertificate pc = (PublicCertificate) obj;
                        if (pc.getCompany().equals(minor)) {
                            playersToProcess.add(p);
                        }
                    }
                }
            }
        }

        // Fallback for Director weirdness
        if (playersToProcess.isEmpty()) {

            playersToProcess.add(action.getPlayer());
        }

        net.sf.rails.game.financial.BankPortfolio ipo = net.sf.rails.game.financial.Bank.getIpo(gameManager);

        // 3. Execute Exchange (Just-In-Time Fetch)
        for (Player p : playersToProcess) {
            PortfolioModel pm = ((PortfolioOwner) p).getPortfolioModel();

            // Collect minor shares to swap
            List<PublicCertificate> toSwap = new ArrayList<>();
            for (Object obj : pm.getCertificates()) {
                if (obj instanceof PublicCertificate) {
                    PublicCertificate cert = (PublicCertificate) obj;
                    if (cert.getCompany().equals(minor)) {
                        toSwap.add(cert);
                    }
                }
            }

            for (PublicCertificate minorCert : toSwap) {
                // Determine needed share type
                boolean targetPresidentShare = false;
                if ("K1".equals(minorId)) {
                    targetPresidentShare = true;
                } else if ("U1".equals(minorId) && minorCert.isPresidentShare()) {
                    targetPresidentShare = true;
                }

                // A. Surrender Minor Share to IPO
                minorCert.moveTo(ipo);

                // B. Find Available Major Share (Anywhere except Player hands)
                PublicCertificate newShare = null;
                List<PublicCertificate> allMajorCerts = major.getCertificates();

                // 1. Try to find exact match that is NOT owned by a player
                for (PublicCertificate pc : allMajorCerts) {
                    if (pc.isPresidentShare() == targetPresidentShare) {
                        if (!(pc.getOwner() instanceof Player)) {
                            newShare = pc;
                            break;
                        }
                    }
                }

                // 2. Fallback: Try to find ANY share not owned by a player
                if (newShare == null) {
                    for (PublicCertificate pc : allMajorCerts) {
                        if (!(pc.getOwner() instanceof Player)) {
                            newShare = pc;
                            break;
                        }
                    }
                }

                if (newShare != null) {

                    newShare.moveTo(p);
                }
            }
        }

        // 4. Cleanup

        // // Rule: Trains must be transferred to the new Major (KK, Ug, etc.) before
        // the minor is closed.
        // if (minor.hasTrains()) {
        // List<Train> trains = new ArrayList<>(minor.getTrains());
        // for (Train t : trains) {
        // t.moveTo(major.getPortfolioModel());
        // log.debug("Transferred train " + t.getName() + " from " + minor.getId() + "
        // to
        // " + major.getId());
        // }
        // }

        Merger1837.mergeMinor(gameManager, minor, major);
        Merger1837.fixDirectorship(gameManager, major);

        if (major.getNumberOfTrains() > major.getCurrentTrainLimit()) {
            log.warn("LIMIT ENFORCEMENT: " + major.getId() + " has "
                    + major.getNumberOfTrains() + "/" + major.getCurrentTrainLimit() + " trains.");

            // Rails uses BUY_TRAIN step for discard resolution when over limit
            setStep(GameDef.OrStep.BUY_TRAIN);

            operatingCompany.set(major);
            setPossibleActions();
        }

        // Use shared helper to check limits and generate discard actions if needed
        if (checkAndGenerateDiscardActions(major)) {
            String warning = "WARNING: " + major.getId() + " exceeds train limit after voluntary merger.";
            ReportBuffer.add(this, warning);
        }

        return true;
    }

public boolean triggerNationalFormation() {
        return triggerNationalFormation(null);
    }

    public boolean triggerNationalFormation(String triggeringTrain) {
        net.sf.rails.game.PhaseManager pm = getRoot().getPhaseManager();

        for (PublicCompany company : gameManager.getAllPublicCompanies()) {
            if (company instanceof PublicCompany_1837) {
                PublicCompany_1837 national = (PublicCompany_1837) company;
                if (!"National".equals(national.getType().getId()))
                    continue;

                // Strict Target Filtering to prevent rogue NFR triggers mid-turn
                if (triggeringTrain != null) {
                    String nId = national.getId();
                    if (triggeringTrain.equals("4") && !nId.equals("KK") && !nId.equals("Sd")) continue;
                    if (triggeringTrain.equals("4E") && !nId.equals("Ug")) continue;
                    if (triggeringTrain.equals("4+1") && !nId.equals("KK")) continue;
                    if (triggeringTrain.equals("5") && !nId.equals("KK") && !nId.equals("Ug")) continue;
                }

                // Reliance on State variable only.
                if (triggeredNationals.contains(national.getId())) {
                    continue; // Already handled
                }

                if ("Sd".equals(national.getId()))
                    continue;

                // Check Triggers
                boolean start = !national.hasStarted() && pm.hasReachedPhase(national.getFormationStartPhase());

                // Ug Logic (4E) - Must continue prompting even after formation until Phase 5
                if (national.getId().equals("Ug")) {
                    // Check Bank Pool
                    boolean has4E = getRoot().getBank().getPool().getPortfolioModel().getTrainList().stream()
                            .anyMatch(t -> t.getType().getName().equals("4E"));

                    // Check All Companies (if not in bank)
                    if (!has4E) {
                        has4E = gameManager.getAllPublicCompanies().stream()
                                .flatMap(c -> c.getPortfolioModel().getTrainList().stream())
                                .anyMatch(t -> t.getType().getName().equals("4E"));
                    }

                    if (has4E)
                        start = true;
                }

                boolean forcedStart = !national.hasStarted() && pm.hasReachedPhase(national.getForcedStartPhase());
                boolean forcedMerge = !national.isComplete() && pm.hasReachedPhase(national.getForcedMergePhase());

                if (national.getId().equals("KK") && !national.isComplete()) {
                    boolean has4Plus1 = getRoot().getBank().getPool().getPortfolioModel().getTrainList().stream()
                            .anyMatch(t -> t.getType().getName().equals("4+1"));
                    if (!has4Plus1) {
                        has4Plus1 = gameManager.getAllPublicCompanies().stream()
                                .flatMap(c -> c.getPortfolioModel().getTrainList().stream())
                                .anyMatch(t -> t.getType().getName().equals("4+1"));
                    }
                    if (has4Plus1) {
                        forcedStart = true;
                        forcedMerge = true;
                    }
                }
// Trigger NFR immediately for all valid formations so it interrupts BEFORE train limits
                boolean shouldTrigger = start || forcedStart || forcedMerge;

                // If valid trigger found:
                if (shouldTrigger) {
                    if (!forcedStart && !forcedMerge && declinedNationals.contains(national.getId())) {
                        continue;
                    }
                    log.debug("1837_FIX: Triggering NFR for " + national.getId());

                    triggeredNationals.add(national.getId());

                    // Generate a unique ID to avoid "Root already contains item" exception
                    String uniqueId = "NFR_" + national.getId() + "_" + System.currentTimeMillis();
                    NationalFormationRound nfr = new NationalFormationRound(gameManager, uniqueId);

                    gameManager.setInterruptedRound(this);
                    gameManager.setRound(nfr);
                    nfr.start(national, true, "Triggered");
                    return true;
                }
            }
        }
        return false;
    }

    // ... existing code ...
    public void setNationalFormationDeclined(String nationalId) {
        if (!declinedNationals.contains(nationalId)) {
            declinedNationals.add(nationalId);
        }
    }

    /**
     * Removes "Zombie" Stop objects from the Root that persist after an interrupted
     * turn.
     * Uses Reflection to bypass ClassCastExceptions since Root.items is a
     * HashMapState, not a Map.
     */
    private void cleanupZombieStops(MapHex hex) {
        if (hex == null)
            return;

        Root root = getRoot();
        String hexUri = "/Map/" + hex.getId() + "/";

        try {
            // 1. Get the 'items' object (It is a HashMapState, not a java.util.Map)
            Field itemsField = Root.class.getDeclaredField("items");
            itemsField.setAccessible(true);
            Object itemsState = itemsField.get(root);

            if (itemsState == null)
                return;

            // 2. Use Reflection to find the 'remove' method on the runtime class.
            // HashMapState typically has remove(Object key).
            java.lang.reflect.Method removeMethod;
            try {
                removeMethod = itemsState.getClass().getMethod("remove", Object.class);
            } catch (NoSuchMethodException e) {
                // Fallback: Try remove(String key) if generic erasure is different
                removeMethod = itemsState.getClass().getMethod("remove", String.class);
            }

            // 3. Clean indices 0 to 6
            for (int i = 0; i <= 6; i++) {
                String suspectUri = hexUri + i;
                try {
                    // Invoke remove(suspectUri) dynamically
                    Object result = removeMethod.invoke(itemsState, suspectUri);

                    // If result is not null/false, we actually removed something
                    if (result != null && !Boolean.FALSE.equals(result)) {
                        log.debug("Auto-Fix: Successfully removed zombie item: " + suspectUri);
                    }
                } catch (Exception innerEx) {
                    // Ignore invocation errors (e.g. key missing)
                }
            }
        } catch (Exception e) {
            log.error("Auto-Fix Failed: Reflection error during cleanup.", e);
        }
    }

    @Override
    public String getRevenueDisplayString(PublicCompany company) {
        try {
            RevenueAdapter ra = RevenueAdapter.createRevenueAdapter(getRoot(), company,
                    getRoot().getPhaseManager().getCurrentPhase());
            if (ra != null) {
                ra.initRevenueCalculator(true);
                int total = ra.calculateRevenue();
                int mine = ra.getSpecialRevenue();
                if (mine > 0) {
                    int route = total - mine;
                    return Bank.format(this, route) + " + " + Bank.format(this, mine);
                }
            }
        } catch (Exception e) {
            log.error("Error generating revenue display string", e);
        }
        return super.getRevenueDisplayString(company);
    }

    @Override
    public int getBaseRevenueOnly(PublicCompany company) {
        String type = company.getType().getId();
        if (!type.equals("Coal") && !type.equals("Minor") && !type.equals("Mine")) {
            return company.getLastRevenue();
        }
        if (!company.hasTrains() && !type.equals("Coal"))
            return 0;
        RevenueAdapter ra = RevenueAdapter.createRevenueAdapter(getRoot(), company, Phase.getCurrent(this));
        if (ra == null)
            return 0;
        ra.initRevenueCalculator(true);
        int total = ra.calculateRevenue();
        return total - ra.getSpecialRevenue();
    }

    @Override
    public int getSpecialRevenueOnly(PublicCompany company) {
        RevenueAdapter ra = RevenueAdapter.createRevenueAdapter(getRoot(), company, Phase.getCurrent(this));
        if (ra == null)
            return 0;
        try {
            ra.initRevenueCalculator(true);
            ra.calculateRevenue();
            return ra.getSpecialRevenue();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void finishRound() {
        declinedNationals.clear();
        triggeredNationals.clear();
        super.finishRound();
    }

    @Override
    public boolean layTile(LayTile action) {
        if (action.getChosenHex() != null && "G17".equals(action.getChosenHex().getId())) {
            cleanupZombieStops(action.getChosenHex());
        }
        return super.layTile(action);
    }

    @Override
    public void start() {
        processMandatoryExchanges();

        // 1. Initial State Cleanup
        triggeredNationals.clear();
        declinedNationals.clear();
        skippedMinors.clear();

        // 2. INJECT MEMORY: Check if we inherited any skips from the previous CER
        if (gameManager instanceof GameManager_1837) {
            java.util.List<String> inherited = ((GameManager_1837) gameManager).popTempSkippedMinors();
            if (!inherited.isEmpty()) {
                log.info("1837_LOGIC: Inherited skipped minors from CER: " + inherited);
                // ArrayListState does not support addAll(); must add individually
                for (String minorId : inherited) {
                    skippedMinors.add(minorId);
                }
            }
        }

        // 3. Check for Exchange Necessity (Now respects the inherited skips!)
        boolean anyExchangePossible = false;
        for (Player p : gameManager.getPlayers()) {
            if (hasExchangeableMinors(p)) {
                anyExchangePossible = true;
                break;
            }
        }

        // 4. Delegate or Start
        if (anyExchangePossible) {
            log.info("1837_LOGIC: Redirecting to CoalExchangeRound.");
            CoalExchangeRound cer = new CoalExchangeRound(gameManager, "CER_" + getId());
            gameManager.setInterruptedRound(this);
            gameManager.setRound(cer);
            cer.start();
        } else {
            log.debug("1837_LOGIC: Starting standard Operating Round.");
            super.start();
        }

    }

    public void nextSpecialActionPlayer() {
        List<Player> players = gameManager.getPlayers();
        int start = specialActionCurrentIndex.value();

        log.debug("1837_DEBUG: nextSpecialActionPlayer starting from index " + start);

        // Start checking from the *next* player
        int checkIndex = (start == -1) ? 0 : (start + 1) % players.size();

        // Loop through all players ONCE
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(checkIndex);

            // --- Strict Eligibility Check ---
            // Only stop if the player HAS an exchange.
            // If they don't, we silently continue to the next player.
            if (hasExchangeableMinors(p)) {

                log.info("1837_LOGIC: Stopping at " + p.getName() + " (Has Actions)");
                specialActionCurrentIndex.set(checkIndex);
                specialActionPhase.set(true);
                getRoot().getPlayerManager().setCurrentPlayer(p);
                setPossibleActions();
                return; // FOUND SOMEONE! Exit.
            }

            // Move to next player
            checkIndex = (checkIndex + 1) % players.size();
        }

        // If we get here, we looped through everyone and found NO ONE with an action.
        log.info("1837_LOGIC: Phase Complete. No players have exchanges.");
        specialActionPhase.set(false);
        specialActionPhaseFinished.set(true);
        super.start();
    }

    @Override
    public boolean buyTrain(BuyTrain action) {
        // 1. Intercept '4' train for Sd Merge logic
        if (action.getTrain() != null && action.getTrain().getType().getName().equals("4")) {
            if (!phase4Triggered.value()) {
                log.debug("1837_FIX: First '4' train bought. Executing Sd Merge.");
                phase4Triggered.set(true);
                handlePhase4Trigger();
            }
        }

        boolean result = super.buyTrain(action);

        // --- START FIX ---
        // Force UI Refresh immediately so the user sees the train they just bought.
        // This prevents the "Invisible Scrap Buttons" bug.
        if (gameManager.getGameUIManager() != null) {
            gameManager.getGameUIManager().forceFullUIRefresh();
        }
        // --- END FIX ---

        // 2. Restore Triggers
        if (result && action.getTrain() != null) {
            String trainName = action.getTrain().getType().getName();

            // We restore '4' (KK), '4E' (Ug), and '4+1' (KK Forced) here.
            if (trainName.equals("4") || trainName.equals("4E") || trainName.equals("5") || trainName.equals("4+1")) {
                log.debug("1837_FIX: Trigger Train (" + trainName + ") bought. Checking National Formation...");
triggerNationalFormation(trainName);
            }

            // Mandatory Coal Exchanges (Phase 5)
            if (trainName.equals("5")) {
                processMandatoryExchanges();
            }
        }

        return result;
    }

    // --- INSERT THIS METHOD ---
    private void handlePhase4Trigger() {
        log.debug("--- STARTING PHASE 4 LOGIC (Sd Formation) ---");

        PublicCompany sd = getRoot().getCompanyManager().getPublicCompany("Sd");

        if (sd != null && !sd.hasFloated()) {
            log.debug("Phase 4 Trigger: Forming Southern Railway (Sd)");
            net.sf.rails.game.financial.StockMarket market = getRoot().getStockMarket();
            net.sf.rails.game.financial.StockSpace parSpace = null;

            // Find the 142 par space (specific to Sd formation rules)
            for (int r = 0; r < 50; r++) {
                for (int c = 0; c < 50; c++) {
                    net.sf.rails.game.financial.StockSpace ss = market.getStockSpace(r, c);
                    if (ss != null && ss.getPrice() == 142) {
                        parSpace = ss;
                        break;
                    }
                }
                if (parSpace != null)
                    break;
            }

            if (parSpace != null) {
                sd.setCurrentSpace(parSpace);
                // Sd receives 710K (Rule 12.1)
                Currency.fromBank(710, sd);

                // Merge all Sd minors (S1-S5)
                for (PublicCompany minor : gameManager.getRoot().getCompanyManager().getAllPublicCompanies()) {
                    String id = minor.getId();
                    if (id.length() == 2 && id.startsWith("S") && id.charAt(1) >= '1' && id.charAt(1) <= '5') {
                        Merger1837.mergeMinor(gameManager, minor, sd);
                    }
                }
                sd.setOperated();
                if (!sd.hasFloated())
                    sd.setFloated();
                Merger1837.fixDirectorship(gameManager, sd);

                if (gameManager.getGameUIManager() != null) {
                    gameManager.getGameUIManager().forceFullUIRefresh();
                }
            }
        }

        // Handle Bozen tile (Rule 12.1 - Italy/Bozen logic)
        net.sf.rails.game.MapManager map = getRoot().getMapManager();
        if (GameDef_1837.ItalyHexes != null) {
            for (String itHex : GameDef_1837.ItalyHexes.split(",")) {
                net.sf.rails.game.MapHex hex = map.getHex(itHex);
                if (hex != null) {
                    hex.setOpen(false);
                    hex.clear();
                }
            }
        }
        try {
            LayTile action = new LayTile(getRoot(), LayTile.CORRECTION);
            net.sf.rails.game.MapHex hex = map.getHex(GameDef_1837.bozenHex);
            Tile tile = getRoot().getTileManager().getTile(GameDef_1837.newBozenTile);
            if (hex != null && tile != null) {
                action.setChosenHex(hex);
                action.setLaidTile(tile);
                action.setOrientation(GameDef_1837.newBozenTileOrientation);
                hex.upgrade(action);
            }
        } catch (Exception e) {
            log.error("Error laying Bozen tile: " + e.getMessage());
        }

        log.debug("--- PHASE 4 LOGIC COMPLETE ---");
    }

    @Override
    public boolean setPossibleActions() {

        // --- START FIX ---
        // Standard railroad logic ONLY. Special phase logic is now isolated in
        // CoalExchangeRound.
        PublicCompany company = operatingCompany.value();

        // 1. Mandatory Discard Logic
        if (company != null && !company.isClosed() && company.getNumberOfTrains() > company.getCurrentTrainLimit()) {
            possibleActions.clear();
            for (Train train : company.getPortfolioModel().getUniqueTrains()) {
                Set<Train> singleTrainSet = new java.util.HashSet<>();
                singleTrainSet.add(train);
                DiscardTrain action = new DiscardTrain(company, singleTrainSet);
                action.setLabel("Discard " + train.getName());
                possibleActions.add(action);
            }
            return true;
        }

        // 2. Standard Operating Round Logic
        boolean result = super.setPossibleActions();
        if (company == null)
            return result;

        // 3. S5 Home Token Logic
        if ("S5".equalsIgnoreCase(company.getId())
                && (company.getHomeHexes().isEmpty() || !company.hasLaidHomeBaseTokens())) {
            initTurn();
            possibleActions.clear();
            possibleActions.add(new SetHomeHexLocation(getRoot(), company, GameDef_1837.S5homes));
            return true;
        }

        // 4. Voluntary Discard Logic
        if (getStep() == GameDef.OrStep.BUY_TRAIN && !isBelowTrainLimit()) {
            setVoluntaryDiscardActions();
            return true;
        }

        return result;
        // --- END FIX ---
    }

    @Override
    public boolean process(PossibleAction action) {
        // 1. Handle Special Phase Exchanges
        if (action instanceof ExchangeMinorAction) {
            return processExchangeMinor((ExchangeMinorAction) action);
        }

        // 2. Handle Voluntary Discard Logic (Payment)
        if (action instanceof DiscardTrainVoluntarily) {
            DiscardTrainVoluntarily dtv = (DiscardTrainVoluntarily) action;

            // The action was created with a single train in the list, so getSelectedTrain()
            // (inherited from DiscardTrain) will return that train.
            Train train = dtv.getSelectedTrain();

            if (train != null) {
                // Ensure the parent action knows which train to discard
                dtv.setDiscardedTrain(train);

                // --- PAYMENT LOGIC ---
                int cost = train.getType().getCost() / 2;
                if (cost > 0) {
                    PublicCompany company = operatingCompany.value();
                    Bank bank = Bank.get(this);

                    // Move cash from Company -> Bank
                    company.getPurse().getCurrency().move(company, cost, bank);

                    ReportBuffer.add(this, LocalText.getText("PaysToBank",
                            company.getId(),
                            Bank.format(this, cost),
                            "voluntary surrender of " + train.getName()));
                }
            }
            // Now delegate to super.process() which handles the actual removing of the
            // train object
            return super.process(action);
        }

        // 3. Handle End of Turn
        if (action instanceof NullAction && getStep() == GameDef.OrStep.BUY_TRAIN) {
            finishTurn();
            return true;
        }

        return super.process(action);
    }

    private boolean hasExchangeableMinors(Player p) {
        if (p == null)
            return false;

        // Use generic Owner check to be safe
        if (p instanceof PortfolioOwner) {
            PortfolioModel pm = ((PortfolioOwner) p).getPortfolioModel();
            for (Object obj : pm.getCertificates()) {
                if (!(obj instanceof PublicCertificate))
                    continue;
                PublicCertificate cert = (PublicCertificate) obj;

                PublicCompany comp = cert.getCompany();
                // Check basic validity
                if (comp == null || comp.isClosed())
                    continue;

                // Check if explicitly skipped
                if (skippedMinors.contains(comp.getId())) {
                    continue;
                }

                PublicCompany target = Merger1837.getMergeTarget(gameManager, comp);

                // If target is null, it's not an exchangeable minor/coal (handles SB, Sd, etc.
                // automatically)
                if (target != null && target.hasFloated() && comp.getPresident() == p) {
                    return true;
                }

            }
        }
        return false;
    }

    /**
     * Called by GameManager when returning from an interrupted round (like CER)
     */
    public void resume() {
        log.info("1837_TRACE: Resuming OperatingRound " + getId());

        // 1. Critical Logic: Check if the company closed during the NFR
        if (operatingCompany.value() != null && operatingCompany.value().isClosed()) {
            log.warn("1837_LOGIC: Resuming OR but operating company " + operatingCompany.value().getId()
                    + " is CLOSED. Advancing turn.");
            finishTurn();
            if (gameManager.getCurrentRound() == this) {
                setPossibleActions();
            }
            return;
        }

        // 2. Transition Logic: If we are returning from an NFR that was triggered by a
        // train buy,
        // the PossibleActions list might still contain the BuyTrain action that started
        // it.
        // We must clear it and force the turn to finish to break the execution loop.
        if (getStep() == GameDef.OrStep.BUY_TRAIN) {
log.info("1837_LOGIC: Resuming from interruption during BuyTrain step. Continuing turn.");
            possibleActions.clear();
            setPossibleActions();
            return;
        }

        setPossibleActions();
    }

}