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

import net.sf.rails.common.DisplayBuffer;
import net.sf.rails.common.LocalText;
import net.sf.rails.common.ReportBuffer;
import net.sf.rails.game.financial.Bank;
import net.sf.rails.game.financial.PublicCertificate;
import net.sf.rails.util.SequenceUtil;
import rails.game.action.*;
import net.sf.rails.algorithms.RevenueAdapter;
import rails.game.specific._1837.SetHomeHexLocation;

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
// Track if the special phase is fully completed for this round to prevent re-entry loops
    protected final BooleanState specialActionPhaseFinished = new BooleanState(this, "specialActionPhaseFinished", false);

    protected final BooleanState phase3Triggered = new BooleanState(this, "phase3Triggered", false);
    protected final BooleanState phase4Triggered = new BooleanState(this, "phase4Triggered", false);
    protected final BooleanState phase5Triggered = new BooleanState(this, "phase5Triggered", false);

    private static Map<String, String> skippedExchanges = new HashMap<>();

    
    public OperatingRound_1837(GameManager parent, String id) {
        super(parent, id);
    }


@Override
    protected void newPhaseChecks() {
        Phase phase = Phase.getCurrent(this);

        if (phase.getId().equals("3")) {
            // --- START FIX ---
            // Ensure this logic only runs once when Phase 3 is first triggered
            if (!phase3Triggered.value()) {
                phase3Triggered.set(true);

                // Unblock the hexes blocked by private companies
                for (PrivateCompany comp : gameManager.getAllPrivateCompanies()) {
                    comp.unblockHexes();
                }
                // Open the Bosnian territory
                MapManager map = getRoot().getMapManager();
                for (String bzhHex : GameDef_1837.BzHHexes.split(",")) {
                    map.getHex(bzhHex).setOpen(true);
                }
                // String report = LocalText.getText("TerritoryIsOpened", "Bosnian");
                // ReportBuffer.add(this, report);
                // DisplayBuffer.add(this, report);
            }
            // --- END FIX ---

        } else if (phase.getId().equals("4")) {
            // --- START FIX ---
            // Ensure this logic only runs once when Phase 4 is first triggered
            if (!phase4Triggered.value()) {
                phase4Triggered.set(true);

                // Close the Italian territory
                MapManager map = getRoot().getMapManager();
                for (String itHex : GameDef_1837.ItalyHexes.split(",")) {
                    MapHex hex = map.getHex(itHex);
                    hex.setOpen(false);
                    hex.clear();
                }
                String report = LocalText.getText("TerritoryIsClosed", "Italian");
                ReportBuffer.add(this, report);
                DisplayBuffer.add(this, report);

                // Lay the new Bozen (Bolzano) tile
                LayTile action = new LayTile(getRoot(), LayTile.CORRECTION);
                MapHex hex = map.getHex(GameDef_1837.bozenHex);
                Tile tile = getRoot().getTileManager().getTile(GameDef_1837.newBozenTile);
                int orientation = GameDef_1837.newBozenTileOrientation;
                action.setChosenHex(hex);
                action.setLaidTile(tile);
                action.setOrientation(orientation);
                hex.upgrade(action);
                report = LocalText.getText("LaysTileAt", "Rails",
                        tile.getId(),
                        hex.getId(),
                        hex.getOrientationName(HexSide.get(orientation)));
                ReportBuffer.add(this, report);
                // Note: the Sd and KK formation timings specified in their <Formation> tags
            }
            // --- END FIX ---

        } else if (phase.getId().equals("5")) {
            // --- START FIX ---
            // Ensure this logic only runs once when Phase 5 is first triggered
            if (!phase5Triggered.value()) {
                phase5Triggered.set(true);
                if (((GameManager_1837) gameManager).checkAndRunCER("5", this, this)) {
                    return;
                }
            }
            // --- END FIX ---
            // Note: The Ug formation timing is specified in its <Formation> tag
        }

        PhaseManager phmgr = getRoot().getPhaseManager();
        if (phmgr.hasReachedPhase("4") && !phmgr.hasReachedPhase("5+2")) {
            ((GameManager_1837) gameManager).setNewPhaseId(phase.getId());
            ((GameManager_1837) gameManager).checkAndRunNFR(phase.getId(), null, this);

        }
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

            // RECOVERY: The local 'currentPlayer' field is transient and lost between actions.
            // We MUST reconstruct it from the persisted IntegerState 'specialActionCurrentIndex'
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
                    if (comp == null || processed.contains(comp.getId())) continue;
    
                    // --- FIX: Check for Skips ---
                    if (getId().equals(skippedExchanges.get(comp.getId()))) continue;

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
            // CRITICAL: The UI filters actions based on this field. It must match the client's identity.
            na.setPlayer(this.currentPlayer); 
            possibleActions.add(na);

            return true;
        }

        // 2. Standard Operating Round Logic (Company-based)
        PublicCompany company = operatingCompany.value();
        // Vital: Sync the local 'currentPlayer' field with the company president.
        // Without this, the UI thinks it is nobody's turn during the standard OR, hiding buttons like Discard Train.
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
            return super.setPossibleActions();
        }
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


    @Override
    public boolean process(PossibleAction action) {
        // TRAP: Intercept "Done" (NullAction) during the Special Phase.
        // The base OperatingRound.process() consumes NullAction before calling processGameSpecificAction(),
        // preventing our custom player-rotation logic from ever running.
        if (specialActionPhase.value() && action instanceof NullAction) {
            
            // Execute the Custom "Done" logic directly here
            return processSpecialDone();
        }

        // Delegate everything else to the standard processor
        return super.process(action);
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
            
            setPossibleActions();
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
    protected boolean gameSpecificTileLayAllowed(PublicCompany company,
            MapHex hex, int orientation) {
        RailsRoot root = gameManager.getRoot();
        List<MapHex> italyMapHexes = new ArrayList<>();
        // 1. check Phase

        int phaseIndex = root.getPhaseManager().getCurrentPhase().getIndex();
        if (phaseIndex < 3) {
            // Check if the Hex is blocked by a private ?
            if (hex.isBlockedByPrivateCompany()) {
                if (company.getPresident().getPortfolioModel().getPrivateCompanies()
                        .contains(hex.getBlockingPrivateCompany())) {
                    // Check if the Owner of the PublicCompany is owner of the Private Company that
                    // blocks
                    // the hex (1837)
                    return true;
                }
                return false;
            }
        }
        if (phaseIndex >= 4) {

            // 2. retrieve Italy vertices ...
            String[] italyHexes = GameDef_1837.ItalyHexes.split(",");
            for (String italyHex : italyHexes) {
                italyMapHexes.add(root.getMapManager().getHex(italyHex));
            }
            if (italyMapHexes.contains(hex)) {
                return false;
            }
        }
        return true;
    }

    // ... (lines 405-410)
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
        // 1. If we have already finished the phase for this round, proceed to standard operations immediately.
        if (specialActionPhaseFinished.value()) {
            super.start();
            return;
        }

        // 2. If the phase is already active (e.g. reload or return from action), RESUME it.
        // Do NOT reset the counters (which caused the loop).
        if (specialActionPhase.value()) {
            setPossibleActions();
            return;
        }

        boolean exchangePossible = false;
        
        for (Player p : gameManager.getPlayers()) {
            for (PublicCertificate cert : p.getPortfolioModel().getCertificates()) {
                PublicCompany comp = cert.getCompany();
                if (comp == null) continue;

                if (getId().equals(skippedExchanges.get(comp.getId()))) {
                    continue;
                }


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
            specialActionPhaseFinished.set(true); // Mark finished so we don't check again this round
            super.start();
            return;
        }

        // Initialize Special Phase sequence
        specialActionPhase.set(true);
        specialActionPlayerCount.set(0);

        List<Player> players = gameManager.getPlayers();
        Player priority = getRoot().getPlayerManager().getPriorityPlayer();
        if (priority == null && !players.isEmpty()) priority = players.get(0);
        
        int pdIndex = players.indexOf(priority);
        specialActionCurrentIndex.set(pdIndex);
        
        this.currentPlayer = priority;
        getRoot().getPlayerManager().setCurrentPlayer(priority);
        
        setPossibleActions();
        // --- END FIX ---
    }





}
