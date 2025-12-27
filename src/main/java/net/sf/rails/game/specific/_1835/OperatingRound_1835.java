package net.sf.rails.game.specific._1835;

import java.util.*;
import java.util.stream.Collectors;

import net.sf.rails.game.*;
import net.sf.rails.game.state.*;
import net.sf.rails.game.state.Currency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rails.game.action.BuyTrain;
import rails.game.action.DiscardTrain;
import rails.game.action.LayTile;
import rails.game.action.NullAction;
import rails.game.action.PossibleAction;
import rails.game.action.LayBaseToken;

import net.sf.rails.common.GameOption;
import net.sf.rails.common.LocalText;
import net.sf.rails.common.ReportBuffer;
import net.sf.rails.game.financial.Bank;
import net.sf.rails.game.special.ExchangeForShare;
import net.sf.rails.game.special.SpecialProperty;
import net.sf.rails.game.special.SpecialSingleTileLay;
import java.lang.reflect.Field; // REQUIRED IMPORT

import net.sf.rails.game.model.PortfolioModel;
import com.google.common.collect.Iterables;
import rails.game.action.SetDividend;

public class OperatingRound_1835 extends OperatingRound {
    private static final Logger log = LoggerFactory.getLogger(OperatingRound_1835.class);

    private final BooleanState needPrussianFormationCall = new BooleanState(this, "NeedPrussianFormationCall");
    private final BooleanState hasLaidExtraOBBTile = new BooleanState(this, "HasLaidExtraOBBTile");
    private final BooleanState pfrTriggeredThisOR = new BooleanState(this, "PfrTriggeredThisOR");

    private final HashMapState<Player, Integer> deniedIncomeShare = HashMapState.create(this, "deniedIncomeShare");
    private int tokensLaidCount = 0;
    protected final BooleanState mandatoryBadenTokenLaid = new BooleanState(this, "MandatoryBadenTokenLaid");
    protected final GenericState<PublicCompany> interruptedCompany = new GenericState<>(this, "InterruptedCompany");
    protected final BooleanState awaitingBadenHomeToken = new BooleanState(this, "AwaitingBadenHomeToken");

    public OperatingRound_1835(GameManager parent, String id) {
        super(parent, id);
    }

    @Override
    public void resume() {
        super.resume();
        boolean pfrTriggered = this.pfrTriggeredThisOR.value();
        boolean prStarted = companyManager.getPublicCompany(GameDef_1835.PR_ID).hasStarted();
        boolean alreadyOffered = ((GameManager_1835) gameManager).hasPrussianFormationBeenOffered();

        if (!prStarted && !alreadyOffered && !pfrTriggered) {
            this.needPrussianFormationCall.set(false);
        }
    }

    @Override
    public boolean buyTrain(BuyTrain action) {
        // 1. Process the cash transaction for the President (Standard Logic)
        if (action.getAddedCash() > 0) {
            PublicCompany company = (PublicCompany) action.getCompany();
            Player president = company.getPresident();
            Currency.wire(president, action.getAddedCash(), company);
            action.setAddedCash(0);
        }

        // 2. Execute the standard buy logic (updates trainsBoughtThisTurn)
        boolean success = super.buyTrain(action);
        if (!success)
            return false;

        // 3. Rule 4.6 Check: First 4-Train Trigger
        if (!trainsBoughtThisTurn.isEmpty()) {
            TrainCardType boughtType = trainsBoughtThisTurn.get(trainsBoughtThisTurn.size() - 1);
            String id = boughtType.getId();

            // Logging for debugging

            boolean isFirst4 = "4".equals(id) && boughtType.getNumberBoughtFromIPO() == 1;
            boolean isFirst4Plus4 = "4+4".equals(id) && boughtType.getNumberBoughtFromIPO() == 1;

            if (isFirst4 || isFirst4Plus4) {

                GameManager_1835 gm = (GameManager_1835) gameManager;
                PublicCompany pr = companyManager.getPublicCompany(GameDef_1835.PR_ID);

                if (!pr.hasStarted()) {
                    PublicCompany m2 = companyManager.getPublicCompany(GameDef_1835.M2_ID);
                    Player m2Pres = (m2 != null) ? m2.getPresident() : null;

                    if (m2Pres != null) {

                        gm.setPrussianFormationStartingPlayer(m2Pres);
                        gm.startPrussianFormationRound(this);

                        // Prevent the end-of-turn loop from triggering this again
                        pfrTriggeredThisOR.set(true);
                        needPrussianFormationCall.set(false);
                    }
                }
            }
        }
        return true;
    }

    @Override
    protected void initTurn() {
        super.initTurn();
        tokensLaidCount = 0;
        pfrTriggeredThisOR.set(false);
        needPrussianFormationCall.set(false);
        // Reset the OBB extra tile flag at start of turn
        hasLaidExtraOBBTile.set(false);

        Set<SpecialProperty> sps = operatingCompany.value().getSpecialProperties();
        if (sps != null && !sps.isEmpty()) {
            ExchangeForShare efs = (ExchangeForShare) Iterables.get(sps, 0);
            addIncomeDenialShare(operatingCompany.value().getPresident(), efs.getShare());
        }
    }

    @Override
    protected boolean canCompanyOperateThisRound(PublicCompany company) {
        if (!company.hasFloated() || company.isClosed()) {
            return false;
        }
        if (company.hasStockPrice())
            return true;
        if ("Clemens".equalsIgnoreCase(GameOption.getValue(this, GameOption.VARIANT))
                || "yes".equalsIgnoreCase(GameOption.getValue(this, "MinorsRequireFloatedBY"))) {
            return companyManager.getPublicCompany(GameDef_1835.BY_ID).hasFloated();
        }
        return true;
    }

    @Override
    protected void privatesPayOut() {
        int count = 0;
        for (PrivateCompany priv : companyManager.getAllPrivateCompanies()) {
            if (!priv.isClosed()) {
                if (priv.getOwner() instanceof MoneyOwner) {
                    Owner recipient = priv.getOwner();
                    int revenue = priv.getRevenueByPhase(Phase.getCurrent(this));
                    if (count++ == 0)
                        ReportBuffer.add(this, "");
                    String revText = Currency.fromBank(revenue, (MoneyOwner) recipient);
                    ReportBuffer.add(this, LocalText.getText("ReceivesFor",
                            recipient.getId(),
                            revText,
                            priv.getId()));

                    if (recipient instanceof Player && priv.getSpecialProperties() != null
                            && priv.getSpecialProperties().size() > 0) {
                        SpecialProperty sp = Iterables.get(priv.getSpecialProperties(), 0);
                        if (sp instanceof ExchangeForShare) {
                            ExchangeForShare efs = (ExchangeForShare) sp;
                            if (efs.getPublicCompanyName().equalsIgnoreCase(GameDef_1835.PR_ID)) {
                                int share = efs.getShare();
                                Player player = (Player) recipient;
                                addIncomeDenialShare(player, share);
                            }
                        }
                    }
                }
            }
        }
    }

    private void addIncomeDenialShare(Player player, int share) {
        if (!deniedIncomeShare.containsKey(player)) {
            deniedIncomeShare.put(player, share);
        } else {
            deniedIncomeShare.put(player, share + deniedIncomeShare.get(player));
        }
    }

    @Override
    protected Map<MoneyOwner, Integer> countSharesPerRecipient() {
        Map<MoneyOwner, Integer> sharesPerRecipient = super.countSharesPerRecipient();

        if (operatingCompany.value().getId().equalsIgnoreCase(GameDef_1835.PR_ID)) {
            for (Player player : deniedIncomeShare.viewKeySet()) {
                if (!sharesPerRecipient.containsKey(player))
                    continue;
                int share = deniedIncomeShare.get(player);
                int shares = share / operatingCompany.value().getShareUnit();
                if (this.wasInterrupted()) {
                    sharesPerRecipient.put(player, sharesPerRecipient.get(player) - shares);
                    ReportBuffer.add(this, LocalText.getText("NoIncomeForPreviousOperation",
                            player.getId(),
                            share,
                            GameDef_1835.PR_ID));
                }
            }
        }
        return sharesPerRecipient;
    }

    @Override
    protected boolean validateSpecialTileLay(LayTile layTile) {
        if (!super.validateSpecialTileLay(layTile))
            return false;

        // Check matches on the SpecialProperty location string
        SpecialProperty sp = layTile.getSpecialProperty();
        if (sp instanceof SpecialSingleTileLay) {
            String loc = ((SpecialSingleTileLay) sp).getLocationNameString();
            if (loc != null && loc.matches("M1[57]") && hasLaidExtraOBBTile.value()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void prepareRevenueAndDividendAction() {
        PublicCompany company = operatingCompany.value();
        if (company.hasTrains() && company instanceof PublicCompany_1835
                && company.hasBankLoan()) {
            int[] allowedRevenueActions = new int[] { SetDividend.WITHHOLD };
            possibleActions.add(new SetDividend(getRoot(),
                    company.getLastRevenue(), true,
                    allowedRevenueActions));
        } else {
            super.prepareRevenueAndDividendAction();
        }
    }

    @Override
    public boolean checkForExcessTrains() {
        boolean hasExcess = super.checkForExcessTrains();
        if (!hasExcess)
            return false;

        TrainType type2 = null, type3 = null, type4 = null;
        for (TrainType tt : trainManager.getTrainTypes()) {
            if (tt.getName().equals("2"))
                type2 = tt;
            if (tt.getName().equals("3"))
                type3 = tt;
            if (tt.getName().equals("4"))
                type4 = tt;
        }

        Iterator<Map.Entry<Player, List<PublicCompany>>> playerIterator = excessTrainCompanies.entrySet().iterator();
        while (playerIterator.hasNext()) {
            Map.Entry<Player, List<PublicCompany>> playerEntry = playerIterator.next();
            Iterator<PublicCompany> companyIterator = playerEntry.getValue().iterator();

            while (companyIterator.hasNext()) {
                PublicCompany comp = companyIterator.next();
                if (!comp.hasStockPrice()) {
                    PortfolioModel portfolio = comp.getPortfolioModel();
                    int trainCount = portfolio.getNumberOfTrains();

                    if (trainCount == 2) {
                        boolean has2 = (type2 != null) && (portfolio.getTrainOfType(type2) != null);
                        boolean has3 = (type3 != null) && (portfolio.getTrainOfType(type3) != null);
                        boolean has4 = (type4 != null) && (portfolio.getTrainOfType(type4) != null);

                        if (has2 && (has3 || has4)) {
                            companyIterator.remove();
                        }
                    }
                }
            }
            if (playerEntry.getValue().isEmpty()) {
                playerIterator.remove();
            }
        }
        return !excessTrainCompanies.isEmpty();
    }

    @Override
    public boolean discardTrain(DiscardTrain action) {
        if (!action.process(this)) {
            return false;
        }

        boolean moreDiscards = super.checkForExcessTrains();

        if (this.needPrussianFormationCall.value()) {
            if (!moreDiscards) {
                PublicCompany prussian = companyManager.getPublicCompany(GameDef_1835.PR_ID);
                if (prussian.hasStarted()) {
                    if (operatingCompany.value().isClosed()) {
                        operatingCompany.set(prussian);
                        stepObject.set(GameDef.OrStep.INITIAL);
                    } else {
                        stepObject.set(GameDef.OrStep.BUY_TRAIN);
                    }
                    playerManager.setCurrentPlayer(operatingCompany.value().getPresident());
                } else {
                    ((GameManager_1835) gameManager).startPrussianFormationRound(this);
                }
            }
        } else {
            if (!moreDiscards) {
                newPhaseChecks();
                if (gameManager.getInterruptedRound() != null) {
                    return true;
                }
                playerManager.setCurrentPlayer(operatingCompany.value().getPresident());
                stepObject.set(GameDef.OrStep.BUY_TRAIN);
            }
        }
        return true;
    }

    // ... (lines of unchanged context code) ...
    @Override
    protected void newPhaseChecks() {
        PhaseManager phaseManager = getRoot().getPhaseManager();
        Phase currentPhase = phaseManager.getCurrentPhase();

        for (PublicCompany company : operatingCompanies.view()) {
            company.getPortfolioModel().rustObsoleteTrains();
        }

        // DEBUGGING PROBE: Log Phase 5 discard check
        if (currentPhase.getId().startsWith("5")) {
            boolean excess = checkForExcessTrains();
            if (excess) {
                setStep(GameDef.OrStep.DISCARD_TRAINS);
                needPrussianFormationCall.set(true);
                pfrTriggeredThisOR.set(true);
                return;
            }
        }

        boolean excess = checkForExcessTrains();
        if (excess) {
            setStep(GameDef.OrStep.DISCARD_TRAINS);
        }

        PublicCompany pr = companyManager.getPublicCompany(GameDef_1835.PR_ID);
        boolean prNotStarted = !pr.hasStarted();

        boolean pfrTriggered = false;
        boolean isForcedByTrain = false;

        // DEBUGGING PROBE: Train Purchase Scan
        if (prNotStarted) {
            for (TrainCardType tct : trainsBoughtThisTurn) {
                String id = tct.getId();
                // Log every train bought to see what the loop sees

                if ("2+2".equals(id))
                    continue;

                if ("4".equals(id) || "4+4".equals(id) || "5".equals(id)) {
                    pfrTriggered = true;
                    if ("4+4".equals(id) || "5".equals(id)) {
                        isForcedByTrain = true;
                    }
                    break;
                }
            }
        }

        boolean fiveTrainBought = false;
        for (TrainCardType tct : trainsBoughtThisTurn) {
            if ("5".equals(tct.getId())) {
                fiveTrainBought = true;
                break;
            }
        }

        boolean prIsComplete = PrussianFormationRound.prussianIsComplete(gameManager);

        if (fiveTrainBought) {
            // 1. Silent Kill: Immediately close "paper" privates (NF, LD, OBB, PfB).
            // This is safe to do inside the OR as it requires no user input.
            closePhase5Privates();

            // 2. Complex Exchanges: If Minors/BB/HB still exist, trigger PFR.
            // We defer the share exchanges and director swaps to the PFR, which handles
            // UI interactions safely without crashing the OR transaction.
            if (!prIsComplete) {
                pfrTriggeredThisOR.set(true);
                needPrussianFormationCall.set(true);
            }

        }

        boolean phase5Forced = "5".equals(currentPhase.getId()) && !prIsComplete;

        boolean alreadyOffered = ((GameManager_1835) gameManager).hasPrussianFormationBeenOffered();

        if (alreadyOffered && !phase5Forced && !isForcedByTrain) {
            pfrTriggered = false;
        }

        boolean currentPfrTriggeredVal = pfrTriggeredThisOR.value();

        if ((pfrTriggered || phase5Forced) && !currentPfrTriggeredVal) {

            pfrTriggeredThisOR.set(true);
            needPrussianFormationCall.set(true);
        }
    }
    // ... (rest of the method) ...

    public void clearPfrTriggerFlag_AI() {
        this.needPrussianFormationCall.set(false);
    }

    @Override
    protected void finishOR() {
        ReportBuffer.add(this, " ");
        ReportBuffer.add(this, LocalText.getText("EndOfOperatingRound", thisOrNumber));

        int orWorthIncrease;
        for (Player player : getRoot().getPlayerManager().getPlayers()) {
            player.setLastORWorthIncrease();
            orWorthIncrease = player.getLastORWorthIncrease().value();
            ReportBuffer.add(this, LocalText.getText("ORWorthIncrease",
                    player.getId(), thisOrNumber,
                    Bank.format(this, orWorthIncrease)));
        }

        // Double-PFR Prevention:
        // If PFR was triggered and completed during this OR, explicitly tell
        // GameManager
        // to consider it "Handled/Declined" right before we exit the OR.
        // This prevents the GameManager from seeing the "Phase 5 + No Prussian"
        // condition
        // as valid again immediately at the start of the Stock Round.
        if (pfrTriggeredThisOR.value()) {
            ((GameManager_1835) gameManager).setPfrDeclined();
        }

        ((GameManager_1835) gameManager).nextRound(this);
    }

    @Override
    protected boolean finishTurnSpecials() {
        if (needPrussianFormationCall.value()) {
            needPrussianFormationCall.set(false);

            if (!PrussianFormationRound.prussianIsComplete(gameManager)) {
                ((GameManager_1835) gameManager).startPrussianFormationRound(this);
            }
            return false;
        }
        return super.finishTurnSpecials();
    }

    @Override
    public boolean process(PossibleAction action) {
        boolean result = super.process(action);

        if (action instanceof BuyTrain) {
            newPhaseChecks();
            if (needPrussianFormationCall.value()) {
                needPrussianFormationCall.set(false);
                if (!PrussianFormationRound.prussianIsComplete(gameManager)) {
                    ((GameManager_1835) gameManager).startPrussianFormationRound(this);
                }
            }
        }
        return result;
    }

    @Override
    public boolean layTile(LayTile action) {
        // SAFETY NET: Force cleanup of closed private properties immediately before
        // processing.
        // This handles cases where the AI "Skipped" the proper cleanup phase.
        forceCleanupGhosts();

        PublicCompany company = action.getCompany();
        boolean isBAHomeLay = company.getId().equals("BA") &&
                !company.hasOperated() &&
                action.getChosenHex().isHomeFor(company) &&
                !this.mandatoryBadenTokenLaid.value();

        // // DIAGNOSTIC LOGGING: Track marker disappearance
        // // We capture the state of tokens on the hex BEFORE the tile lay logic executes.
        // MapHex hex = action.getChosenHex();
        // List<String> tokensBefore = hex.getStops().stream()
        //         .flatMap(s -> s.getTokens().stream())
        //         // FIX: Owner interface only has getId(), removed getName() call.
        //         .map(t -> "Owner:" + t.getOwner().getId())
        //         .collect(Collectors.toList());

        // log.info("DIAGNOSTIC: Pre-LayTile on Hex {}. Existing Tokens: {}", hex.getId(), tokensBefore);
        // --- END FIX ---

        boolean result = super.layTile(action);

        // // --- START FIX ---
        // // DIAGNOSTIC LOGGING: Check result
        // // We capture the state AFTER the lay. If 'tokensBefore' differs from
        // // 'tokensAfter', we know the engine deleted a token during validation.
        // List<String> tokensAfter = hex.getStops().stream()
        //         .flatMap(s -> s.getTokens().stream())
        //         // FIX: Owner interface only has getId(), removed getName() call.
        //         .map(t -> "Owner:" + t.getOwner().getId())
        //         .collect(Collectors.toList());

        // if (!tokensBefore.equals(tokensAfter)) {
        //     log.warn("DIAGNOSTIC: Token Change Detected! Before: {}, After: {}", tokensBefore, tokensAfter);
        // }
        // --- END FIX ---

        if (result) {
            SpecialProperty sp = action.getSpecialProperty();

            // 1. OBB Extra Tile Limit Check
            if (sp instanceof SpecialSingleTileLay) {
                String loc = ((SpecialSingleTileLay) sp).getLocationNameString();
                if (loc != null && loc.matches("M1[57]")) {
                    hasLaidExtraOBBTile.set(true);
                }
            }

            // 2. OBB Map Check
            checkOBBClosure();
        }

        if (result && isBAHomeLay) {
            setStep(GameDef.OrStep.LAY_TOKEN);
        }

        if (result && action.getChosenHex().getId().equals("L6")) {
            PublicCompany ba = companyManager.getPublicCompany("BA");

            boolean isBrownUpgrade = false;
            if (action.getLaidTile() != null) {
                String color = action.getLaidTile().getColourText();
                if ("Brown".equalsIgnoreCase(color)) {
                    isBrownUpgrade = true;
                }
            }

            if (!company.equals(ba) && ba.hasStarted() && !this.mandatoryBadenTokenLaid.value() && !isBrownUpgrade) {
                interruptedCompany.set(company);
                operatingCompany.set(ba);
                playerManager.setCurrentPlayer(ba.getPresident());
                awaitingBadenHomeToken.set(true);
            }
        }
        return result;
    }

    // Method: forceCleanupGhosts

    private void forceCleanupGhosts() {
        for (PrivateCompany pc : companyManager.getAllPrivateCompanies()) {
            if (pc.isClosed()) {
                // Try to clean regardless of what the getter says,
                // because the getter might return a non-empty immutable view of a stale state.
                removeSpecialProperties(pc);
            }
        }
    }
 
    /**
     * Helper to safely clear special properties from a closed private.
     * STRATEGY:
     * 1. Bypass the getter (which returns an ImmutableSet).
     * 2. Use reflection to get the private 'specialProperties' field
     * (PortfolioSet).
     * 3. Use reflection again to get the inner 'portfolio' field (TreeSetState).
     * 4. Call clear() on the TreeSetState.
     */
    private void removeSpecialProperties(PrivateCompany pc) {
        if (pc == null)
            return;

        try {

            // STEP 1: Get the private 'specialProperties' field from PrivateCompany
            java.lang.reflect.Field portfolioSetField = getFieldInHierarchy(pc.getClass(), "specialProperties");
            if (portfolioSetField == null) {
                return;
            }
            portfolioSetField.setAccessible(true);
            Object portfolioSetObj = portfolioSetField.get(pc);

            if (portfolioSetObj == null) {
                return;
            }

            // STEP 2: Get the private 'portfolio' field from the PortfolioSet object
            // (We confirmed this field exists in your PortfolioSet.java upload)
            java.lang.reflect.Field internalPortfolioField = getFieldInHierarchy(portfolioSetObj.getClass(),
                    "portfolio");
            if (internalPortfolioField == null) {
                log.error("DIAGNOSTIC: Field 'portfolio' not found in {}", portfolioSetObj.getClass().getName());
                return;
            }
            internalPortfolioField.setAccessible(true);
            Object treeSetStateObj = internalPortfolioField.get(portfolioSetObj);

            if (treeSetStateObj == null) {
                return;
            }

            // STEP 3: Invoke clear() on the TreeSetState
            // TreeSetState usually implements Collection or has a clear() method.
            try {
                java.lang.reflect.Method clearMethod = treeSetStateObj.getClass().getMethod("clear");
                clearMethod.invoke(treeSetStateObj);
            } catch (Exception e) {
                // Fallback if it is a Collection
                if (treeSetStateObj instanceof java.util.Collection) {
                    ((java.util.Collection<?>) treeSetStateObj).clear();
                } else {
                    log.error("DIAGNOSTIC: Could not clear final object of type {}",
                            treeSetStateObj.getClass().getName());
                }
            }

        } catch (Exception e) {
            log.error("DIAGNOSTIC: Double-Reflection cleanup CRASHED for {}: {}", pc.getId(), e.toString());
            e.printStackTrace();
        }
    }

    private Field getFieldInHierarchy(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    @Override
    public boolean layBaseToken(LayBaseToken action) {
        PublicCompany company = action.getCompany();

        if (action.getType() == LayBaseToken.HOME_CITY && company.getId().equals("BA")) {
            MapHex hex = action.getChosenHex();
            for (Stop s : hex.getStops()) {
                BaseToken placeholder = null;
                for (BaseToken token : s.getBaseTokens()) {
                    if (token.getParent().equals(company)) {
                        placeholder = token;
                        break;
                    }
                }
                if (placeholder != null) {
                    placeholder.moveTo(company);
                }
            }
        }

        boolean isInterruption = awaitingBadenHomeToken.value();
        boolean isSpecial = (action.getType() != LayBaseToken.GENERIC);

        if (!isSpecial && tokensLaidCount > 0) {
            return false;
        }

        boolean result = super.layBaseToken(action);

        if (result) {
            // Close Private if Special Property Used (NF/PfB Token)
            closePrivateIfSpecial(action.getSpecialProperty());
        }

        if (result && isInterruption) {

            this.mandatoryBadenTokenLaid.set(true);
            awaitingBadenHomeToken.set(false);

            PublicCompany originalCompany = interruptedCompany.value();
            if (originalCompany != null) {
                operatingCompany.set(originalCompany);
                playerManager.setCurrentPlayer(originalCompany.getPresident());
                interruptedCompany.set(null);
            }
            return true;
        }

        if (result && action.getType() == LayBaseToken.HOME_CITY && company.getId().equals("BA")) {
            this.mandatoryBadenTokenLaid.set(true);
            setStep(GameDef.OrStep.LAY_TRACK);
            if (!normalTileLaidThisTurn.value()) {
                initNormalTileLays();
            }
            return true;
        }

        if (result && !isSpecial) {
            tokensLaidCount++;
        }
        return result;
    }

    private void closePrivateIfSpecial(SpecialProperty sp) {
        if (sp == null)
            return;

        PrivateCompany owner = null;
        for (PrivateCompany pc : companyManager.getAllPrivateCompanies()) {
            if (pc.getSpecialProperties().contains(sp)) {
                owner = pc;
                break;
            }
        }

        if (owner != null && !owner.isClosed()) {
            String id = owner.getId();
            // NF: Closes on Token Lay
            // PfB: Closes on Token Lay (not Tile Lay)
            if ("NF".equals(id) || "PfB".equals(id)) {
                owner.close();
                // --- START FIX ---
                // KILL THE ZOMBIE: Explicitly strip properties to prevent ghost blocking
                removeSpecialProperties(owner);
                // --- END FIX ---
                ReportBuffer.add(this, LocalText.getText("CompanyCloses", owner.getId()));
            }
        }
    }

    private void checkOBBClosure() {
        // OBB (Ostbayerische Bahn)
        // Closes when M15 AND M17 have tiles.
        PrivateCompany obb = companyManager.getPrivateCompany("OBB");
        if (obb != null && !obb.isClosed()) {
            MapHex m15 = getRoot().getMapManager().getHex("M15");
            MapHex m17 = getRoot().getMapManager().getHex("M17");

            // Use !isPreprintedTileCurrent() to check if a tile has been laid
            if (m15 != null && !m15.isPreprintedTileCurrent() &&
                    m17 != null && !m17.isPreprintedTileCurrent()) {

                obb.close();
                removeSpecialProperties(obb);
                ReportBuffer.add(this, LocalText.getText("CompanyCloses", obb.getId()));
            }
        }
    }

    /**
     * Closes the "Simple" Privates that disappear in Phase 5 without compensation.
     * Exempts BB and HB, as they must be exchanged for shares in the PFR.
     */
    private void closePhase5Privates() {
        for (PrivateCompany pc : companyManager.getAllPrivateCompanies()) {
            if (pc.isClosed())
                continue;

            String id = pc.getId();

            // BB and HB are exchangeable for PR shares. They must stay open
            // until the PFR processes them.
            // All others (NF, LD, OBB, PfB, etc.) are removed from the game now.
            if (!"BB".equals(id) && !"HB".equals(id)) {
                pc.close();
                removeSpecialProperties(pc);
                ReportBuffer.add(this, LocalText.getText("CompanyCloses", pc.getId()));
            }
        }
    }

    /**
     * * This ensures that NO part of the game round (Action Generation, Map
     * Validation, Token Logic) ever sees a property belonging to a Closed private
     * company.
     * * 1. Prevents creation of "Ghost Actions" (e.g. WT laying token on L14).
     * 2. Prevents "Disappearing Markers" by stopping invalid blocking checks on L6.
     */
    @Override
    public <T extends SpecialProperty> List<T> getSpecialProperties(Class<T> clazz) {
        // 1. Get the raw list from the standard engine (which ignores closed status)
        List<T> properties = super.getSpecialProperties(clazz);

        if (properties != null && !properties.isEmpty()) {
            // --- START FIX ---
            // 2. Strict Filter: Remove anything owned by a Closed Private Company
            // Added logging to detect "Ghost" property suppression
            properties.removeIf(sp -> {
                boolean remove = sp.getOriginalCompany() instanceof PrivateCompany &&
                        ((PrivateCompany) sp.getOriginalCompany()).isClosed();
                if (remove) {
                    log.info("DIAGNOSTIC: Suppressed Ghost Property from Closed Company: {}",
                            sp.getOriginalCompany().getId());
                }
                return remove;
            });
            // --- END FIX ---
        }
        return properties;
    }

    @Override
    public boolean setPossibleActions() {
        // 1. Reverted Debug Logging to clean state

        // 2. Existing 1835 Logic (Baden Home Token Interruption)
        if (awaitingBadenHomeToken.value()) {
            possibleActions.clear();
            PublicCompany ba = companyManager.getPublicCompany("BA");
            MapHex l6 = getRoot().getMapManager().getHex("L6");

            for (Stop stop : l6.getStops()) {
                LayBaseToken action = new LayBaseToken(getRoot(), l6);
                action.setCompany(ba);
                action.setChosenStation(stop.getRelatedStationNumber());
                action.setType(LayBaseToken.HOME_CITY);

                Station station = l6.getStation(stop.getRelatedStationNumber());
                String label = String.format("Place Baden Home Station (%s)",
                        l6.getConnectionString(station));
                action.setButtonLabel(label);

                possibleActions.add(action);
            }
pruneGhostActions();

            return true;
        }

        if (getStep() == GameDef.OrStep.DISCARD_TRAINS) {
            checkForExcessTrains();
        }

        GameDef.OrStep step = getStep();
        PublicCompany company = operatingCompany.value();

        // 3. Existing 1835 Logic (Initial Step)
        if (step == GameDef.OrStep.INITIAL) {
            initTurn();

            if (!company.hasOperated() && company.getId().equals("BA")) {
                MapHex homeHex = null;
                if (company.getHomeHexes() != null && !company.getHomeHexes().isEmpty()) {
                    homeHex = company.getHomeHexes().get(0);
                }
                if (homeHex != null && !homeHex.isPreprintedTileCurrent() && !homeHex.hasTokenOfCompany(company)) {
                    setStep(GameDef.OrStep.LAY_TOKEN);
                    boolean result = setPossibleActions(); // Recursive
pruneGhostActions();
                    return result;
                }
            }
            nextStep();
            boolean result = setPossibleActions(); // Recursive
            pruneGhostActions();

            return result;
        }

        // 4. Existing 1835 Logic (Lay Track)
        if (step == GameDef.OrStep.LAY_TRACK) {
            if (company.getId().equals("BA") && !company.hasOperated()) {
                MapHex homeHex = null;
                if (company.getHomeHexes() != null && !company.getHomeHexes().isEmpty()) {
                    homeHex = company.getHomeHexes().get(0);
                }

                if (homeHex != null && homeHex.isPreprintedTileCurrent()) {
                    possibleActions.clear();
                    possibleActions.addAll(getNormalTileLays(true));
                    possibleActions.addAll(getSpecialTileLays(true));
pruneGhostActions();
                    return true;
                }
            }
        }

        // 5. Existing 1835 Logic (Lay Token)
        if (step == GameDef.OrStep.LAY_TOKEN) {
            if (company.getId().equals("BA") && !company.hasOperated() && !this.mandatoryBadenTokenLaid.value()) {
                possibleActions.clear();
                for (MapHex homeHex : company.getHomeHexes()) {
                    for (Stop stop : homeHex.getStops()) {
                        LayBaseToken stationChoice = new LayBaseToken(getRoot(), homeHex);
                        stationChoice.setCompany(company);
                        stationChoice.setChosenStation(stop.getRelatedStationNumber());
                        stationChoice.setType(LayBaseToken.HOME_CITY);

                        Station station = homeHex.getStation(stop.getRelatedStationNumber());
                        String buttonLabel = String.format("Lay Token on Station %d (%s)",
                                stop.getRelatedStationNumber(),
                                homeHex.getConnectionString(station));
                        stationChoice.setButtonLabel(buttonLabel);

                        possibleActions.add(stationChoice);

                        pruneGhostActions();
                    }
                }

                return true;
            }
        }

        // 6. Standard Logic for all other cases
        boolean result = super.setPossibleActions();

        // CRITICAL FIX: Prune "Ghost Actions" from Closed Privates.
        // The engine may generate actions for properties held by Public Companies
        // even if the original Private Company is closed. Executing these causes state
        // corruption
        // (e.g. disappearing tokens on L14).
        if (result && !possibleActions.isEmpty()) {
            // Fix: Use getList() to iterate over a safe copy, as PossibleActions is not
            // Iterable.
            for (PossibleAction action : possibleActions.getList()) {
                SpecialProperty sp = null;
                if (action instanceof LayBaseToken) {
                    sp = ((LayBaseToken) action).getSpecialProperty();
                } else if (action instanceof LayTile) {
                    sp = ((LayTile) action).getSpecialProperty();
                }

                if (sp != null && sp.getOriginalCompany() instanceof PrivateCompany) {
                    if (((PrivateCompany) sp.getOriginalCompany()).isClosed()) {
                        possibleActions.remove(action);
                    }
                }
            }
        }
        
        if (result && !possibleActions.isEmpty()) {
            pruneGhostActions();
        }
        
        return result;
    }

    /**
     * Centralized method to remove actions generated by Closed Private Companies.
     * This prevents the AI from simulating invalid "Ghost" moves that corrupt the
     * map state.
     */
    private void pruneGhostActions() {
        if (possibleActions.isEmpty())
            return;

        // Create a safe copy to iterate to avoid ConcurrentModification
        List<PossibleAction> actions = new ArrayList<>(possibleActions.getList());
        
        for (PossibleAction action : actions) {
            SpecialProperty sp = null;
            if (action instanceof LayBaseToken) {
                sp = ((LayBaseToken) action).getSpecialProperty();
            } else if (action instanceof LayTile) {
                sp = ((LayTile) action).getSpecialProperty();
            }

            if (sp != null && sp.getOriginalCompany() instanceof PrivateCompany) {
                if (((PrivateCompany) sp.getOriginalCompany()).isClosed()) {
                    possibleActions.remove(action);
                }
            }
        }
    }


}
