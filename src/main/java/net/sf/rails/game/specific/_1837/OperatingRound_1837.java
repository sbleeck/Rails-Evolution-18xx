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
    private Player currentPlayer;
    // Track if the special phase is fully completed for this round to prevent
    // re-entry loops
    protected final BooleanState specialActionPhaseFinished = new BooleanState(this, "specialActionPhaseFinished",
            false);

    protected final BooleanState phase3Triggered = new BooleanState(this, "phase3Triggered", false);
    protected final BooleanState phase4Triggered = new BooleanState(this, "phase4Triggered", false);
    protected final BooleanState phase5Triggered = new BooleanState(this, "phase5Triggered", false);
    protected final ArrayListState<String> declinedNationals = new ArrayListState<>(this, "declinedNationals");
    private static Map<String, String> skippedExchanges = new HashMap<>();
    // Static cache to persist triggers even if State rollback occurs during
    // interrupt
    protected static Set<String> tempTriggeredCache = new HashSet<>();

    // FIX 1: Use primitive types to bypass State constructor visibility issues for
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

        // 1. Get all trains owned by the company
        List<Train> trains = company.getTrains();

        // 2. Add the Voluntary Discard action
        // This allows the user to pick a train to scrap for 50% cost
        possibleActions.add(new DiscardTrainVoluntarily(company, trains));

        // 3. Add the Skip/Pass Action
        // This fulfills the requirement: "the voluntary action must have a skip or
        // pass"
        // If the user clicks this, they decline to scrap and thus decline to buy a new
        // train.
        possibleActions.add(new NullAction(getRoot(), NullAction.Mode.DONE));
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
            this.currentPlayer = nextPlayer;
            getRoot().getPlayerManager().setCurrentPlayer(nextPlayer);

            // --- START FIX ---
            // Remove manual call; returning 'true' from process() triggers the engine to
            // update actions.
            // setPossibleActions();
            // --- END FIX ---
        }
        return true;
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
            targetId = "TR";
        else if (id.equals("LRB") || id.equals("EHS"))
            targetId = "TI";
        else if (id.equals("BB"))
            targetId = "BH";

        else if (id.startsWith("Sd"))
            targetId = "Sd";
        else if (id.startsWith("kk"))
            targetId = "kk";
        else if (id.startsWith("Ug"))
            targetId = "Ug";

        if (targetId != null) {
            return gameManager.getRoot().getCompanyManager().getPublicCompany(targetId);
        }
        return null;
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
            log.info("OR_1837: Waiving cost for hex " + hex.getId() + " due to Mountain Railway rights.");
            return 0;
        }

        // Fallback (shouldn't happen if isTileLayAllowed is checked first)
        return standardCost;
    }

    @Override
    public String getRevenueDisplayString(PublicCompany company) {
        // 1. If it's not a Coal company (or Minor representing one), use default
        // Ensure null safety on getType() if necessary in your engine
        String type = company.getType().getId();
        boolean isCoalOrMinor = type.equals("Coal") || type.equals("Minor") || type.equals("Mine");
        if (!isCoalOrMinor) {
            return super.getRevenueDisplayString(company);
        }

        // 2. Calculate the split
        // CRITICAL: Coal mines might not have trains, but still have fixed income.
        if (!company.hasTrains() && !type.equals("Coal"))
            return Bank.format(this, 0);

        RevenueAdapter ra = RevenueAdapter.createRevenueAdapter(getRoot(), company, Phase.getCurrent(this));
        if (ra == null)
            return super.getRevenueDisplayString(company);

        ra.initRevenueCalculator(true);

        // --- START FIX ---
        // calculateRevenue returns the TOTAL (Base + Special).
        int totalRevenue = ra.calculateRevenue();
        int coalRevenue = ra.getSpecialRevenue();

        // We must subtract coalRevenue from total to isolate the Base (Route) revenue.
        int baseRevenue = totalRevenue - coalRevenue;
        // --- END FIX ---

        // 3. Format the output
        // If there is special coal revenue, show the split e.g. "30 + 20"
        if (coalRevenue > 0) {
            return Bank.format(this, baseRevenue) + " + " + Bank.format(this, coalRevenue);
        }

        // Fallback if no coal income found this turn
        return super.getRevenueDisplayString(company);
    }

    @Override
    public int getBaseRevenueOnly(PublicCompany company) {
        String type = company.getType().getId();
        if (!type.equals("Coal") && !type.equals("Minor") && !type.equals("Mine")) {
            return company.getLastRevenue(); // Fallback to model value
        }

        // Allow trainless mines to calculate
        if (!company.hasTrains() && !type.equals("Coal"))
            return 0;

        RevenueAdapter ra = RevenueAdapter.createRevenueAdapter(getRoot(), company, Phase.getCurrent(this));
        if (ra == null)
            return 0;

        ra.initRevenueCalculator(true);

        // --- START FIX ---
        int total = ra.calculateRevenue();
        // Return only the route portion
        return total - ra.getSpecialRevenue();
        // --- END FIX ---
    }

    @Override
    public int getSpecialRevenueOnly(PublicCompany company) {
        // 1. Create Adapter
        RevenueAdapter ra = RevenueAdapter.createRevenueAdapter(getRoot(), company, Phase.getCurrent(this));
        if (ra == null)
            return 0;

        try {
            // 2. Initialize
            ra.initRevenueCalculator(true);

            // --- START FIX ---
            // CRITICAL: You MUST run the calculation to populate the special revenue!
            // The engine calculates modifiers (like Coal bonuses) as it traverses the
            // graph.
            ra.calculateRevenue();
            // --- END FIX ---

            // 3. Now the special revenue has been computed
            // log.info("specialRevenue for " + company.getId() + ": " +
            // ra.getSpecialRevenue());
            return ra.getSpecialRevenue();

        } catch (Exception e) {
            log.error("Error calc special revenue", e);
            return 0;
        }
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
                // FIX: If the reserved company is CLOSED (e.g. WT absorbed by S2), ignore the
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

        // There is only revenue if there are any trains
        if (companyHasRunningTrains(false)) {

            // 1837 Specific Logic: Coal and Minor companies MUST split.
            PublicCompany company = operatingCompany.value();
            String type = company.getType().getId();

            // Identify Mandatories
            boolean isMandatorySplit = type.equalsIgnoreCase("Coal") || type.equalsIgnoreCase("Minor");

            // --- START FIX ---
            // Check for G-Trains to ensure Majors also separate Mine Revenue
            boolean hasGTrains = false;
            if (company.hasTrains()) {
                for (Train t : company.getPortfolioModel().getTrainList()) {
                    if (t.getName().contains("G")) {
                        hasGTrains = true;
                        break;
                    }
                }
            }
            // --- END FIX ---

            int[] allowedRevenueActions;
            int defaultAllocation;
log.debug("OR_1837 DEBUG:");
            // The AI sees the action generated here. If 'lastRevenue' is 0, the AI sees
            // "Split 0" and aborts.
            // We must calculate the real revenue NOW to populate the action correctly.
            int revenueToUse = company.getLastRevenue();
            int directIncomeToUse = company.getLastDirectIncome();

// Force calculation for G-Trains (Majors) OR Mandatory Splits (Minors)
            // if the values aren't already populated/separated.
            // This ensures Majors with G-trains get the directIncome populated (Mine Revenue).
            if ((isMandatorySplit || hasGTrains) && (revenueToUse == 0 || directIncomeToUse == 0)) {
                try {
                    RevenueAdapter ra = RevenueAdapter.createRevenueAdapter(getRoot(), company,
                            getRoot().getPhaseManager().getCurrentPhase());
                    if (ra != null) {
                        ra.initRevenueCalculator(true); // true = multigraph support
                        revenueToUse = ra.calculateRevenue();
                        directIncomeToUse = ra.getSpecialRevenue(); // Capture 1837 Mine Revenue logic
                        
                        log.debug("OR_1837 DEBUG: Recalculated Revenue for " + company.getId());
                        log.debug("OR_1837 DEBUG: Total=" + revenueToUse + ", Mine(Fixed)=" + directIncomeToUse);
                    }
                } catch (Exception e) {
                    log.error("Failed to calculate revenue for Dividend Action", e);
                }
            }

            if (company.isSplitAlways() || isMandatorySplit) {
                // CASE 1: Mandatory Split (Coal / Minor)
                // Restrict to ONLY Split. This disables Payout/Withhold in the UI.
                allowedRevenueActions = new int[] { SetDividend.SPLIT };
                defaultAllocation = SetDividend.SPLIT;
            } else if (company.isSplitAllowed()) {
                // CASE 2: Optional Split (Some Majors)
                allowedRevenueActions = new int[] { SetDividend.PAYOUT,
                        SetDividend.SPLIT,
                        SetDividend.WITHHOLD };
                defaultAllocation = SetDividend.PAYOUT;
            } else {
                // CASE 3: Standard Major
                allowedRevenueActions = new int[] {
                        SetDividend.PAYOUT,
                        SetDividend.WITHHOLD };
                defaultAllocation = SetDividend.PAYOUT;
            }

            possibleActions.add(new SetDividend(getRoot(),
                    revenueToUse,
                    directIncomeToUse,
                    true, allowedRevenueActions, defaultAllocation));

        } else {
             // No trains running
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
     * * @param company  Operating company
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

            PublicCompany major = getMergeTarget(coal);
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
            // We pass false/false for forced/close because we handle assets manually if
            // needed
            // but the Rails Mergers generic usually handles standard asset transfer.
            Mergers.mergeCompanies(gameManager, coal, major, false, false);

            // Fix for missing LocalText key: Use direct string construction
            ReportBuffer.add(this, "Company " + coal.getId() + " merged into " + major.getId() + " (" + reason + ")");

            // Use shared helper to check limits and generate discard actions if needed
            if (checkAndGenerateDiscardActions(major)) {
                String warning = "WARNING: " + major.getId() + " exceeds train limit after mandatory merger.";
                ReportBuffer.add(this, warning);
            }

        }
    }

    public void nextSpecialActionPlayer() {
        // RELOAD FIX: Strict check. If log does not match Special Phase, force exit.
        if (gameManager.isReloading()) {
            PossibleAction next = gameManager.getNextActionFromLog();

            boolean stayInSpecial = false;

            if (next != null) {
                // We only stay in special phase if the log explicitly contains a Special Action
                // or a "Done" (NullAction) confirmation.
                if (next instanceof ExchangeMinorAction) {
                    stayInSpecial = true;
                } else if (next instanceof NullAction && ((NullAction) next).getMode() == NullAction.Mode.DONE) {
                    stayInSpecial = true;
                }
            }
            // If next is null (end of log/unknown) OR next is LayTile/BuyTrain/etc, we
            // EXIT.

            if (!stayInSpecial) {
                specialActionPhase.set(false);
                specialActionPhaseFinished.set(true);
                super.start();
                return;
            }
        }

        List<PublicCompany> majors = getOperatingCompanies();

        for (PublicCompany major : majors) {
            if (!major.hasFloated() || major.isClosed())
                continue;

            Player director = major.getPresident();
            if (director == null)
                continue;

            List<Player> players = gameManager.getPlayers();
            int dirIdx = players.indexOf(director);

            for (int i = 0; i < players.size(); i++) {
                Player p = players.get((dirIdx + i) % players.size());

                // --- FIX: Portfolio Access Logic ---
                if (p instanceof PortfolioOwner) {
                    PortfolioModel pm = ((PortfolioOwner) p).getPortfolioModel();

                    // TODO: I CANNOT FIX THIS LINE WITHOUT PortfolioModel.java
                    // The error says pm.getPortfolio() does not exist.
                    // DO NOT UNCOMMENT UNTIL METHOD NAME IS VERIFIED:
                    /*
                     * if (pm != null) {
                     * for (PublicCertificate cert : pm.UNKNOWN_METHOD_GET_CERTIFICATES()) {
                     * // ... check logic ...
                     * }
                     * }
                     */
                }
            }
        }

        specialActionPhase.set(false);
        specialActionPhaseFinished.set(true);
        super.start();
    }

    @Override
    protected void newPhaseChecks() {
        super.newPhaseChecks();

        // FIX: Access PhaseManager via the gameManager field
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
                log.info("Phase 4 Transition Detected. Executing One-Time Triggers.");
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
        // log.info("Transferred train " + t.getName() + " from " + minor.getId() + " to
        // " + major.getId());
        // }
        // }

        Merger1837.mergeMinor(gameManager, minor, major);
        Merger1837.fixDirectorship(gameManager, major);

        // Use shared helper to check limits and generate discard actions if needed
        if (checkAndGenerateDiscardActions(major)) {
            String warning = "WARNING: " + major.getId() + " exceeds train limit after voluntary merger.";
            ReportBuffer.add(this, warning);
        }

        return true;
    }

    @Override
    public boolean setPossibleActions() {

        // 1. Special Action Phase (Player-based exchanges before companies run)
        if (specialActionPhase.value() && gameManager.isReloading()) {
            PossibleAction next = gameManager.getNextActionFromLog();
            boolean stayInSpecial = false;
            if (next != null) {
                if (next instanceof ExchangeMinorAction) {
                    stayInSpecial = true;
                } else if (next instanceof NullAction && ((NullAction) next).getMode() == NullAction.Mode.DONE) {
                    stayInSpecial = true;
                }
            }
            if (!stayInSpecial) {
                specialActionPhase.set(false);
            }
        }

        if (specialActionPhase.value()) {
            possibleActions.clear();
            List<Player> players = gameManager.getPlayers();
            int index = specialActionCurrentIndex.value();

            if (index >= 0 && index < players.size()) {
                this.currentPlayer = players.get(index);
                getRoot().getPlayerManager().setCurrentPlayer(this.currentPlayer);
            } else {
                this.currentPlayer = null;
            }

            Set<String> processed = new HashSet<>();
            if (this.currentPlayer != null) {
                for (PublicCertificate cert : this.currentPlayer.getPortfolioModel().getCertificates()) {
                    PublicCompany comp = cert.getCompany();
                    if (comp == null || processed.contains(comp.getId()))
                        continue;
                    if (getId().equals(skippedExchanges.get(comp.getId())))
                        continue;

                    String type = comp.getType().getId();
                    if ("Minor".equals(type) || "Coal".equals(type)) {
                        PublicCompany target = getMergeTarget(comp);
                        if (target != null && target.hasFloated() && comp.getPresident() == this.currentPlayer) {
                            possibleActions.add(new ExchangeMinorAction(comp, target, false));
                            processed.add(comp.getId());
                        }
                    }
                }
            }
            NullAction na = new NullAction(getRoot(), NullAction.Mode.DONE);
            na.setLabel("Done / No Exchanges");
            na.setPlayer(this.currentPlayer);
            possibleActions.add(na);

            return true;
        }

        // 2. Pre-sync Player BEFORE calling super.
        // If we don't do this, super.setPossibleActions() might see the NFR player (Rainer) 
        // as active, find no actions for him in this company (Stefan's), and return false/empty.
        if (operatingCompany.value() != null && !operatingCompany.value().isClosed()) {
            this.currentPlayer = operatingCompany.value().getPresident();
            if (getRoot().getPlayerManager().getCurrentPlayer() != this.currentPlayer) {
                log.info("1837_DEBUG: setPossibleActions - Syncing PlayerManager to {}", this.currentPlayer.getName());
                getRoot().getPlayerManager().setCurrentPlayer(this.currentPlayer);
            }
        }

        // 3. Standard Operating Round Logic
        boolean result = super.setPossibleActions();
        
        // Debug Logging
        if (!result) {
             log.info("1837_DEBUG: super.setPossibleActions returned FALSE. Step: {}", getStep());
        } else if (possibleActions.isEmpty()) {
             log.info("1837_DEBUG: super.setPossibleActions returned TRUE but list is EMPTY. Step: {}", getStep());
        }


        PublicCompany company = operatingCompany.value();
        if (company == null)
            return result;

       

        // S5 / Home Token Logic (Preserved)
        if (GameDef_1837.S5.equalsIgnoreCase(company.getId())
                && (company.getHomeHexes().isEmpty() || !company.hasLaidHomeBaseTokens())) {

            if (gameManager.isReloading()) {
                PossibleAction next = gameManager.getNextActionFromLog();
                if (next instanceof LayTile) {
                    LayTile lay = (LayTile) next;
                    MapHex target = lay.getChosenHex();
                    if (target != null && (target.getId().equals("L8") || target.getId().equals("L2"))) {
                        company.setHomeHex(target);
                        company.layHomeBaseTokens();
                    }
                }
            }
            if (company.getHomeHexes().isEmpty() || !company.hasLaidHomeBaseTokens()) {
                initTurn();
                possibleActions.clear();
                possibleActions.add(new SetHomeHexLocation(getRoot(), company, GameDef_1837.S5homes));
                return true;
            }
            possibleActions.clear();
            return super.setPossibleActions();

        } else if (company.isClosed()) {
            finishTurn();
            possibleActions.clear();
            super.setPossibleActions();
            return true;
        } else {
            // 3. Voluntary Discard Logic (Preserved)
            if (company != null && getStep() == GameDef.OrStep.BUY_TRAIN && !isBelowTrainLimit()) {
                boolean addedOption = false;
                for (Train t : company.getPortfolioModel().getTrainList()) {
                    int cost = t.getType().getCost() / 2;
                    if (company.getCash() >= cost) {
                        List<Train> trainList = new ArrayList<>();
                        trainList.add(t);
                        DiscardTrainVoluntarily dt = new DiscardTrainVoluntarily(company, trainList);
                        dt.setDiscardedTrain(t);
                        String label = t.getName() + " (" + Bank.format(this, cost) + ")";
                        dt.setLabel(label);
                        possibleActions.add(dt);
                        addedOption = true;
                    }
                }
                if (addedOption) {
                    doneAllowed.set(true);
                    boolean hasPass = false;
                    for (PossibleAction pa : possibleActions.getList()) {
                        NullAction.Mode mode = ((NullAction) pa).getMode();
                        if (mode == NullAction.Mode.DONE || mode == NullAction.Mode.PASS) {
                            hasPass = true;
                            break;
                        }
                    }
                    if (!hasPass) {
                        possibleActions.add(new NullAction(getRoot(), NullAction.Mode.PASS));
                    }
                }
            }
            return result;
        }
    }

    @Override
    public boolean process(PossibleAction action) {

        if (action instanceof ExchangeMinorAction) {
            return processExchangeMinor((ExchangeMinorAction) action);
        }

        if (action instanceof NullAction) {
            // The logic for kkFormationState and ugFormationState passing is removed.
            // The NFR now handles "Pass" internally and returns control to the OR.

            // Fix for "Captured in Final Move" loop
            if (getStep() == GameDef.OrStep.BUY_TRAIN) {
                log.info("1837_FIX: NullAction in BUY_TRAIN detected. Forcing finishTurn().");
                finishTurn();
                return true;
            }
        }

        if (specialActionPhase.value() && action instanceof NullAction) {
            skippedMinors.add(currentSpecialCompanyId.value());
            nextSpecialActionPlayer();
            return true;
        }

        if (action instanceof NullAction) {
            if (getStep() == GameDef.OrStep.BUY_TRAIN) {
                log.info("1837_FIX: NullAction in BUY_TRAIN detected. Forcing finishTurn().");
                finishTurn();
                return true;
            }
        }

        return super.process(action);
    }

    private void handlePhase4Trigger() {
        log.info("--- STARTING PHASE 4 LOGIC (Sd Formation) ---");

        PublicCompany sd = getRoot().getCompanyManager().getPublicCompany("Sd");

        // ... (Keep existing Sd merger logic unchanged) ...
        if (sd != null && !sd.hasFloated()) {
            log.info("Phase 4 Trigger: Forming Southern Railway (Sd)");
            net.sf.rails.game.financial.StockMarket market = getRoot().getStockMarket();
            net.sf.rails.game.financial.StockSpace parSpace = null;
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
                Currency.fromBank(710, sd);
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

        // ... (Keep existing Bozen tile logic) ...
        MapManager map = getRoot().getMapManager();
        if (GameDef_1837.ItalyHexes != null) {
            for (String itHex : GameDef_1837.ItalyHexes.split(",")) {
                MapHex hex = map.getHex(itHex);
                if (hex != null) {
                    hex.setOpen(false);
                    hex.clear();
                }
            }
        }
        try {
            LayTile action = new LayTile(getRoot(), LayTile.CORRECTION);
            MapHex hex = map.getHex(GameDef_1837.bozenHex);
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

        // --- START FIX: REMOVED MANUAL KK STATE SETTING ---
        // We removed the lines that manually set kkFormationState.
        // Instead, the NFR trigger mechanism in buyTrain/start will pick this up
        // automatically.
        // --- END FIX ---

        log.info("--- PHASE 4 LOGIC COMPLETE ---");
    }

    @Override
    public boolean buyTrain(BuyTrain action) {
        // 1. Intercept '4' train for Sd Merge logic (Strictly Mandatory Phase 4 Start)
        if (action.getTrain() != null && action.getTrain().getType().getName().equals("4")) {
            if (!phase4Triggered.value()) {
                log.info("1837_FIX: First '4' train bought. Executing Sd Merge.");
                phase4Triggered.set(true);
                handlePhase4Trigger();
            }
        }

        boolean result = super.buyTrain(action);

        // --- START FIX: Restore Immediate Triggers ---
        if (result && action.getTrain() != null) {
            String trainName = action.getTrain().getType().getName();

            // We restore '4' (KK) and '4E' (Ug) here.
            // The memory fix in finishRound() ensures we won't loop if you pass.
            if (trainName.equals("4") || trainName.equals("4E") || trainName.equals("5")) {
                log.info("1837_FIX: Trigger Train (" + trainName + ") bought. Checking National Formation...");
                triggerNationalFormation();
            }

            // Mandatory Coal Exchanges (Phase 5)
            if (trainName.equals("5")) {
                processMandatoryExchanges();
            }
        }
        // --- END FIX ---

        return result;
    }

    @Override
    public void start() {
        processMandatoryExchanges();

        // We clear the trigger list ONLY at the start of a fresh Operating Round.
        // This ensures that if we are resuming from an NFR interrupt, we don't forget
        // what we've already done.
        triggeredNationals.clear();

        // Check for recurring optional formations (Phase 4 offers for KK/Ug)
        triggerNationalFormation();

        if (!specialActionPhase.value()) {
            skippedMinors.clear();
        }
        nextSpecialActionPlayer();
    }

    // --- REPLACEMENT METHOD: triggerNationalFormation ---
    private boolean triggerNationalFormation() {
        net.sf.rails.game.PhaseManager pm = getRoot().getPhaseManager();

        for (PublicCompany company : gameManager.getAllPublicCompanies()) {
            if (company instanceof PublicCompany_1837) {
                PublicCompany_1837 national = (PublicCompany_1837) company;
                if (!"National".equals(national.getType().getId()))
                    continue;

                // --- FIX: Check Declined List ---
                if (declinedNationals.contains(national.getId())) {
                    continue; // Do not ask again this turn
                }

                if (triggeredNationals.contains(national.getId()) || tempTriggeredCache.contains(national.getId())) {
                    continue; // Already handled
                }

                if ("Sd".equals(national.getId()))
                    continue;

                // Check Triggers
                boolean start = !national.hasStarted() && pm.hasReachedPhase(national.getFormationStartPhase());

                // Ug Logic (4E)
                if (national.getId().equals("Ug") && !national.hasStarted()) {
                    boolean has4E = getRoot().getBank().getPool().getPortfolioModel().getTrainList().stream()
                            .anyMatch(t -> t.getType().getName().equals("4E"))
                            || gameManager.getAllPublicCompanies().stream()
                                    .flatMap(c -> c.getPortfolioModel().getTrainList().stream())
                                    .anyMatch(t -> t.getType().getName().equals("4E"));
                    if (has4E)
                        start = true;
                }

                boolean forcedStart = !national.hasStarted() && pm.hasReachedPhase(national.getForcedStartPhase());
                boolean forcedMerge = !national.isComplete() && pm.hasReachedPhase(national.getForcedMergePhase());

                // If valid trigger found:
                if (start || forcedStart || forcedMerge) {
                    log.info("1837_FIX: Triggering NFR for " + national.getId());

                    triggeredNationals.add(national.getId());
                    tempTriggeredCache.add(national.getId());

                    NationalFormationRound nfr = new NationalFormationRound(gameManager, "NFR");
                    gameManager.setInterruptedRound(this);
                    gameManager.setRound(nfr);
                    nfr.start(national, true, "Triggered");
                    return true;
                }
            }
        }
        return false;
    }

    // --- REPLACEMENT METHOD: finishRound ---
    @Override
    public void finishRound() {
        // Reset the declined list at the end of the full Operating Round
        // This allows players to be asked again in the NEXT OR if they changed their
        // mind.
        declinedNationals.clear();
        triggeredNationals.clear();
        tempTriggeredCache.clear();
        super.finishRound();
    }

    public void setNationalFormationDeclined(String nationalId) {
        if (!declinedNationals.contains(nationalId)) {
            declinedNationals.add(nationalId);
        }
    }

    @Override
    public void resume() {
        String coId = (operatingCompany.value() != null) ? operatingCompany.value().getId() : "null";
        log.info("1837_DEBUG: Resuming OperatingRound_1837. OperatingCompany: {} Step: {}", coId, getStep());

        // 1. Critical Player Sync upon Resume
        if (operatingCompany.value() != null && !operatingCompany.value().isClosed()) {
            Player pres = operatingCompany.value().getPresident();
            Player globalCurrent = getRoot().getPlayerManager().getCurrentPlayer();

            if (pres != null && globalCurrent != pres) {
                log.info("1837_DEBUG: Player Mismatch in resume(). Global: {}, Local: {}. Fixing...",
                        globalCurrent.getName(), pres.getName());
                getRoot().getPlayerManager().setCurrentPlayer(pres);
            }
        }

        // --- START FIX ---
        // Do NOT call super.resume(). The standard OperatingRound.resume() implementation 
        // calls resetTransientStateOnLoad(), which forces the operating company back to 
        // the first one in the list (index 0) and the step to INITIAL.
        // This causes the game to jump from the current company (e.g. BK) back to the 
        // first company (e.g. ZKB) after an interrupt like NFR.
        
        // super.resume(); 
        
        // Instead, just refresh the actions for the current state (which is preserved).
        setPossibleActions();
        // --- END FIX ---

        log.info("1837_DEBUG: Post-resume actions: {}", possibleActions.getList());
    }

}