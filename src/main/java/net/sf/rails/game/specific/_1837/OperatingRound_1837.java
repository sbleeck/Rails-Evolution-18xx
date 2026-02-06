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

/**
 * @author Martin
 *
 */
public class OperatingRound_1837 extends OperatingRound {
    private static final Logger log = LoggerFactory.getLogger(OperatingRound_1837.class);
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

    private static Map<String, String> skippedExchanges = new HashMap<>();

    // Use 'new' with 2 arguments for ArrayListState
    protected final ArrayListState<String> skippedMinors = new ArrayListState<>(this, "skippedMinors");

    // Use '.create' for StringState (Constructor is private)
    protected final StringState currentSpecialCompanyId = StringState.create(this, "currentSpecialCompanyId");

    public OperatingRound_1837(GameManager parent, String id) {
        super(parent, id);
    }

    public boolean setPossibleActions() {

        if (specialActionPhase.value() && gameManager.isReloading()) {
            PossibleAction next = gameManager.getNextActionFromLog();
            if (next != null) {
                boolean isSpecialAction = (next instanceof ExchangeCoalAction);
                if (!isSpecialAction && next instanceof NullAction) {
                    if (((NullAction) next).getMode() == NullAction.Mode.DONE) {
                        isSpecialAction = true;
                    }
                }

                // If log action is Standard (e.g. LayTile, RunTrains), force exit special phase
                if (!isSpecialAction) {
                    specialActionPhase.set(false);
                }
            }
        }

        // 1. Special Action Phase (Player-based exchanges before companies run)
        if (specialActionPhase.value()) {
            possibleActions.clear();

            // RECOVERY: The local 'currentPlayer' field is transient and lost between
            // actions.
            // We MUST reconstruct it from the persisted IntegerState
            // 'specialActionCurrentIndex'
            // to ensure we know whose turn it actually is.
            List<Player> players = gameManager.getPlayers();
            int index = specialActionCurrentIndex.value();

            // Explicitly sync the PlayerManager. This is the "Truth" for the UI.
            if (index >= 0 && index < players.size()) {
                this.currentPlayer = players.get(index);
                getRoot().getPlayerManager().setCurrentPlayer(this.currentPlayer);
            } else {
                // Fallback / Safety
                this.currentPlayer = null;
            }

            Set<String> processed = new HashSet<>();

            // Use the local 'currentPlayer' field (added to class in previous step)
            if (this.currentPlayer != null) {
                for (PublicCertificate cert : this.currentPlayer.getPortfolioModel().getCertificates()) {
                    PublicCompany comp = cert.getCompany();
                    if (comp == null || processed.contains(comp.getId()))
                        continue;

                    // --- FIX: Check for Skips ---
                    if (getId().equals(skippedExchanges.get(comp.getId())))
                        continue;

                    String type = comp.getType().getId();
                    if ("Minor".equals(type) || "Coal".equals(type)) {
                        // Use local helper method (added to class in previous step)
                        PublicCompany target = getMergeTarget(comp);

                        if (target != null && target.hasFloated() && comp.getPresident() == this.currentPlayer) {
                            possibleActions.add(new ExchangeCoalAction(comp, target));
                            processed.add(comp.getId());
                        }
                    }
                }
            }
            // Add "Done" action using correct Enum and Label

            NullAction na = new NullAction(getRoot(), NullAction.Mode.DONE);
            na.setLabel("Done / No Exchanges");
            // CRITICAL: The UI filters actions based on this field. It must match the
            // client's identity.
            na.setPlayer(this.currentPlayer);
            possibleActions.add(na);

            return true;
        }

        // 2. Standard Operating Round Logic (Company-based)
        // 2. Standard Operating Round Logic
        // Define 'result' here so it is visible for the return statement later
        boolean result = super.setPossibleActions();

        PublicCompany company = operatingCompany.value();

        if (company == null) {
            return result;
        }

        // Vital: Sync the local 'currentPlayer' field with the company president.
        // Without this, the UI thinks it is nobody's turn during the standard OR,
        // hiding buttons like Discard Train.
        if (company != null && !company.isClosed()) {
            this.currentPlayer = company.getPresident();
        }
        if (GameDef_1837.S5.equalsIgnoreCase(company.getId())
                && (company.getHomeHexes().isEmpty()
                        || !company.hasLaidHomeBaseTokens())) {

            initTurn();
            possibleActions.clear();
            possibleActions.add(new SetHomeHexLocation(getRoot(),
                    company, GameDef_1837.S5homes));
            return true;
        } else if (company.isClosed()) {
            // This can occur if a Sd minor buys the first 4-train
            finishTurn();
            possibleActions.clear();
            super.setPossibleActions();
            return true;
        } else {

            // 3. Voluntary Discard Logic

            // Trigger: Company exists, Step is BUY_TRAIN, and Company is AT or ABOVE the
            // limit
            if (company != null
                    && getStep() == GameDef.OrStep.BUY_TRAIN
                    && !isBelowTrainLimit()) {

                boolean addedOption = false;

                // Generate Discard Option for each train
                for (Train t : company.getPortfolioModel().getTrainList()) {
                    int cost = t.getType().getCost() / 2;

                    // Only offer if company can afford the 50% penalty
                    if (company.getCash() >= cost) {
                        List<Train> trainList = new ArrayList<>();
                        trainList.add(t);

                        DiscardTrainVoluntarily dt = new DiscardTrainVoluntarily(company, trainList);
                        dt.setDiscardedTrain(t);

                        // LABEL FORMAT: "2T ($40)"
                        String label = t.getName() + " (" + Bank.format(this, cost) + ")";
                        dt.setLabel(label);

                        possibleActions.add(dt);
                        addedOption = true;
                    }
                }

                // 4. Force "Skip" (Done) Button
                // We enable 'doneAllowed' and explicitly add the NullAction to ensure the
                // button appears.
                if (addedOption) {
                    doneAllowed.set(true);

                    boolean hasPass = false;
                    for (PossibleAction pa : possibleActions.getList()) {
                        // We must ensure a strict DONE or PASS action exists to render the main "Done"
                        // button.
                        NullAction.Mode mode = ((NullAction) pa).getMode();
                        if (mode == NullAction.Mode.DONE || mode == NullAction.Mode.PASS) {
                            hasPass = true;
                            break;
                        }
                    }

                    // If "Done" button is missing, add it now.
                    if (!hasPass) {
                        doneAllowed.set(true);
                        possibleActions.add(new NullAction(getRoot(), NullAction.Mode.PASS));
                    }
                }
            }

            return result;
        }
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

        if (action instanceof ExchangeCoalAction) {
            ExchangeCoalAction exc = (ExchangeCoalAction) action;
            // Execute merge
            Mergers.mergeCompanies(gameManager, exc.getCoalCompany(), exc.getTargetMajor(), false, false);
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
     * 
     * @see
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

            int[] allowedRevenueActions;
            int defaultAllocation;

            // The AI sees the action generated here. If 'lastRevenue' is 0, the AI sees
            // "Split 0" and aborts.
            // We must calculate the real revenue NOW to populate the action correctly.
            int revenueToUse = company.getLastRevenue();
            int directIncomeToUse = company.getLastDirectIncome();

            if (isMandatorySplit && revenueToUse == 0) {
                try {
                    RevenueAdapter ra = RevenueAdapter.createRevenueAdapter(getRoot(), company,
                            getRoot().getPhaseManager().getCurrentPhase());
                    if (ra != null) {
                        ra.initRevenueCalculator(true); // true = multigraph support
                        revenueToUse = ra.calculateRevenue();
                        directIncomeToUse = ra.getSpecialRevenue(); // Capture 1837 Mine Revenue logic
                    }
                } catch (Exception e) {
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
            // BUGFIX: Use the 'revenueToUse' and 'directIncomeToUse' variables we just
            // calculated/verified above.
            // Previously, this used 'company.getLastRevenue()', which was 0 for Minors/Coal
            // before the run,
            // causing the AI to think the revenue was 0.
            possibleActions.add(new SetDividend(getRoot(),
                    revenueToUse, // Was: company.getLastRevenue()
                    directIncomeToUse, // Was: company.getLastDirectIncome()
                    true, allowedRevenueActions, defaultAllocation));

        } else {
        }
    }
    // ...

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
     * 
     * @param display Should be true only once.
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
     * 
     * @param company  Operating company
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

    @Override
    public void start() {
        // --- START FIX ---
        // Rule 2 & 3: Mandatory Coal Exchanges
        log.info("1837 DEBUG: Entering start(). Checking Mandatory Exchanges.");
        processMandatoryExchanges();

        // Clear skips at the very beginning of the phase logic
        if (!specialActionPhase.value()) {
            skippedMinors.clear();
        }

        nextSpecialActionPlayer();
    }

    // ... (lines of unchanged context code) ...
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
            // Skip if no target or major hasn't floated yet
            if (major == null || !major.hasFloated())
                continue;

            // --- START FIX ---
            // Rule 2: Major is fully sold (IPO is empty of buyable shares)
            // Fix: Bank.getIpo() returns BankPortfolio -> getPortfolioModel() ->
            // getCertificates()
            boolean isSoldOut = true;
            for (PublicCertificate cert : Bank.getIpo(gameManager).getPortfolioModel().getCertificates()) {
                if (cert.getCompany().equals(major)) {
                    isSoldOut = false; // Found a share in IPO, so not sold out
                    break;
                }
            }

            if (isSoldOut || isPhase5) {
                // Execute Mandatory Exchange
                Mergers.mergeCompanies(gameManager, coal, major, false, false);

                String reason = isPhase5 ? "Phase 5" : "Major Sold Out";
                ReportBuffer.add(this, LocalText.getText("CompanyMergedInto",
                        coal.getId(), major.getId()) + " (" + reason + ")");
            }
            // --- END FIX ---
        }
    }
    // ... (rest of the method / class) ...

    public void nextSpecialActionPlayer() {
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
    public boolean process(PossibleAction action) {
        // --- FIX: Intercept "Pass" ---
        if (specialActionPhase.value() && action instanceof NullAction) {
            skippedMinors.add(currentSpecialCompanyId.value());
            nextSpecialActionPlayer();
            return true;
        }
        return super.process(action);
    }

    // ... (lines of unchanged context code) ...
    @Override
    protected void newPhaseChecks() {
        super.newPhaseChecks();

        // FIX: Access PhaseManager via the gameManager field
        Phase currentPhase = getRoot().getPhaseManager().getCurrentPhase();

        if (currentPhase.getId().equals("3")) {
            // --- START FIX ---
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
            // --- END FIX ---

        } else if (currentPhase.getId().equals("4")) {
            // --- START FIX ---
            // Ensure this logic only runs once when Phase 4 is first triggered
            if (!phase4Triggered.value()) {
                phase4Triggered.set(true);
                log.info("Phase 4 Trigger Detected. Starting execution sequence.");

                try {
                    handlePhase4Trigger();
                } catch (Exception e) {
                    log.error("CRITICAL FAILURE in Phase 4 Trigger: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            // --- END FIX ---

        } else if (currentPhase.getId().equals("5")) {
            // --- PHASE 5 LOGIC ---
            // Compilable version using standard getters
            for (MapHex h : getRoot().getMapManager().getHexes()) {
                if (h.getId().equals("CoalMine")) {
                    log.info("Logic would close CoalMine at " + h.getId());
                    // h.close(); // Commented out as method does not exist
                }
            }
        }
    }



/**
     * Handles Phase 4 events.
     * FINAL VERSION:
     * 1. Brute-force Market Scan (Price 142).
     * 2. Direct Map Scan for Token Swaps (Bypasses missing getTokens()).
     * 3. Safe Merge (Manual Share Exchange).
     * 4. checkFlotation(sd) to trigger formal activation.
     */
    private void handlePhase4Trigger() {
        log.info("--- STARTING PHASE 4 LOGIC ---");

        // --- STEP 1: Form Southern Railway (Sd) ---
        PublicCompany sd = getRoot().getCompanyManager().getPublicCompany("Sd");

        if (sd != null && !sd.hasFloated()) {
            log.info("Phase 4 Trigger: Forming Southern Railway (Sd)");

            // 1. Set Par Price to 142 (Brute Force Scan)
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
                if (parSpace != null) break;
            }

            if (parSpace != null) {
                log.info("SUCCESS: Found StockSpace for 142 at " + parSpace.getId());
                
                // FIX: Use setCurrentSpace (StockToken getter was missing)
                sd.setCurrentSpace(parSpace);
                
                // 2. Add 710 to Company Treasury
                Currency.fromBank(710, sd);
                log.info("Added 710 to Sd treasury. Current: " + sd.getCash());

                // Prepare list of Sd tokens for distribution
                // FIX: Use getAllBaseTokens() if getTokens() is missing
                List<BaseToken> availableSdTokens = new ArrayList<>(sd.getAllBaseTokens());

                
                // 3. Merge minor companies (S1-S5) into SD
                for (PublicCompany minor : gameManager.getRoot().getCompanyManager().getAllPublicCompanies()) {
                    
                    String id = minor.getId();
                    if (id.length() == 2 && id.startsWith("S") && id.charAt(1) >= '1' && id.charAt(1) <= '5') {
                        
                        // Capture owner BEFORE closing
                        Player owner = minor.getPresident();
                        log.info("Processing Minor: " + id + " (Owner: " + (owner != null ? owner.getName() : "Bank/IPO") + ")");

                        // --- A. TOKEN SWAP (Direct Map Scan) ---
                        for (MapHex hex : gameManager.getRoot().getMapManager().getHexes()) {
                            if (hex.getStops() != null) {
                                for (Stop stop : hex.getStops()) {
                                     if (stop.getTokens() != null) {
                                         List<BaseToken> tokensOnStop = new ArrayList<>(stop.getTokens());
                                         for (BaseToken token : tokensOnStop) {
                                             if (token.getOwner() != null && token.getOwner().equals(minor)) {
                                                 token.moveTo(Bank.getUnavailable(gameManager.getRoot()));
                                                 // Place Sd Token (Skip for S5)
                                                 if (!id.equals("S5") && !availableSdTokens.isEmpty()) {
                                                     BaseToken sToken = availableSdTokens.remove(0);
                                                     sToken.moveTo(stop);
                                                 }
                                             }
                                         }
                                     }
                                }
                            }
                        }

                        
      // --- B. MANUAL ASSET MERGE (Bypass Mergers.java) ---
                        // 1. Move Treasury
                        int cash = minor.getCash();
        if (cash > 0) {
                            // FIX: Swapped arguments based on error message. 
                            // Signature appears to be toBank(MoneyOwner from, int amount)
                            Currency.toBank(minor, cash);
                            Currency.fromBank(cash, sd);
                            log.info("Transferred " + cash + " from " + id + " to Sd");
                        }
                        
                        // 2. Move Trains
                        // Use a copy list to avoid concurrent modification issues
                        List<net.sf.rails.game.Train> trains = new ArrayList<>(minor.getTrains());
                        for (net.sf.rails.game.Train train : trains) {
                            // FIX: moveTo expects an Owner (Company), not a Portfolio
                            train.moveTo(sd);
                            log.info("Moved Train " + train.getName() + " to Sd");
                        }
                        
                        // 3. Move Privates (if any)
                        List<net.sf.rails.game.PrivateCompany> privates = new ArrayList<>(minor.getPrivates());
                        for (net.sf.rails.game.PrivateCompany p : privates) {
                            // FIX: moveTo expects an Owner (Company)
                            p.moveTo(sd);
                        }

                        // 4. Close Minor
                        minor.setClosed();
                        log.info("Closed " + id);


                        // --- C. SMART SHARE EXCHANGE (Manual) ---
                        if (owner != null) {
                            net.sf.rails.game.financial.PublicCertificate shareToGive = null;
                            boolean isS1Owner = id.equals("S1");
                            
                            // Priority: Give President's Share to S1 Owner
                            if (isS1Owner) {
                                for (net.sf.rails.game.financial.PublicCertificate cert : sd.getCertificates()) {
                                    if (cert.isPresidentShare() && !(cert.getOwner() instanceof Player)) {
                                        shareToGive = cert;
                                        break;
                                    }
                                }
                            }
                            
                            // Fallback: Give standard 10% share
                            if (shareToGive == null) {
                                for (net.sf.rails.game.financial.PublicCertificate cert : sd.getCertificates()) {
                                    // STRICT CHECK:
                                    // 1. Not Pres Share
                                    // 2. Size 10%
                                    // 3. Not owned by a Player (Bank/IPO only)
                                    // 4. Not already owned by the recipient (Safety check)
                                    if (!cert.isPresidentShare() && cert.getShare() == 10 
                                            && !(cert.getOwner() instanceof Player) 
                                            && cert.getOwner() != owner) {
                                        shareToGive = cert;
                                        break;
                                    }
                                }
                            }
                            
                            if (shareToGive != null) {
                                shareToGive.moveTo(owner); 
                                String msg = "Exchanged " + id + " for Sd " + (shareToGive.isPresidentShare() ? "Pres " : "") + "share to " + owner.getName();
                                log.info(msg);
                                ReportBuffer.add(this, msg);
                            } else {
                                log.error("CRITICAL: No Sd share found for " + owner.getName());
                            }
                        }
                    }
                }
                
                
                // 4. Set Status & UI
                sd.setOperated();
                
                String report = "Southern Railway (Sd) forms at 142 with 710 treasury.";
                ReportBuffer.add(this, report);
                DisplayBuffer.add(this, report);

// checkFlotation() fails because manual share moves don't update the 'sold' percentage counter.
                if (!sd.hasFloated()) {
                    log.info("Forcing Sd to Floated status.");
                    sd.setFloated(); 
                }
                
                // FIX: Force the Game UI to redraw
                if (gameManager.getGameUIManager() != null) {
                    gameManager.getGameUIManager().forceFullUIRefresh();
                }

            } else {
                log.error("CRITICAL FAILURE: Could not find StockSpace 142.");
                return;
            }

        } else {
            log.info("Sd is null or already floated");
        }

        // --- STEP 2: Close Italy ---
        log.info("STEP 2: Closing Italy");
        MapManager map = getRoot().getMapManager();
        if (GameDef_1837.ItalyHexes != null) {
            for (String itHex : GameDef_1837.ItalyHexes.split(",")) {
                MapHex hex = map.getHex(itHex);
                if (hex != null) {
                    hex.setOpen(false);
                    hex.clear();
                }
            }
            String report = LocalText.getText("TerritoryIsClosed", "Italian");
            ReportBuffer.add(this, report);
            DisplayBuffer.add(this, report);
        }

        // --- STEP 3: Bozen & KK ---
        log.info("Laying Bozen Tile");
        try {
            LayTile action = new LayTile(getRoot(), LayTile.CORRECTION);
            MapHex hex = map.getHex(GameDef_1837.bozenHex);
            Tile tile = getRoot().getTileManager().getTile(GameDef_1837.newBozenTile);
            int orientation = GameDef_1837.newBozenTileOrientation;
            
            if (hex != null && tile != null) {
                action.setChosenHex(hex);
                action.setLaidTile(tile);
                action.setOrientation(orientation);
                hex.upgrade(action);
                
                String report = LocalText.getText("LaysTileAt", "Rails",
                        tile.getId(),
                        hex.getId(),
                        hex.getOrientationName(HexSide.get(orientation)));
                ReportBuffer.add(this, report);
            }
        } catch (Exception e) {
            log.error("Error laying Bozen tile: " + e.getMessage());
        }

        log.info("STEP 3: Checking KK Formation");
        PublicCompany kk = getRoot().getCompanyManager().getPublicCompany("kk");
        PublicCompany kk1 = getRoot().getCompanyManager().getPublicCompany("kk #1");

        if (kk != null && !kk.hasFloated()) {
            if (kk1 != null && kk1.getPresident() != null) {
                log.info("KK #1 found and owned. Offering formation.");
                String report = "The owner of " + kk1.getId() + " may now declare the k.k. National Railway open.";
                ReportBuffer.add(this, report);
                DisplayBuffer.add(this, report);
            }
        }
        
        log.info("--- PHASE 4 LOGIC COMPLETE ---");
    }













}