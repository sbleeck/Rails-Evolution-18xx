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
import net.sf.rails.game.financial.PublicCertificate;
import net.sf.rails.game.special.ExchangeForShare;
import net.sf.rails.game.special.SpecialProperty;
import net.sf.rails.game.special.SpecialSingleTileLay;
import net.sf.rails.game.special.SpecialTileLay;
import java.lang.reflect.Field;

import net.sf.rails.game.model.PortfolioModel;
import com.google.common.collect.Iterables;
import rails.game.action.SetDividend;

public class OperatingRound_1835 extends OperatingRound {
    private static final Logger log = LoggerFactory.getLogger(OperatingRound_1835.class);

    private final BooleanState needPrussianFormationCall = new BooleanState(this, "NeedPrussianFormationCall");
    private final BooleanState hasLaidExtraOBBTile = new BooleanState(this, "HasLaidExtraOBBTile");
    private final BooleanState pfrTriggeredThisOR = new BooleanState(this, "PfrTriggeredThisOR");
// Define the map to track income denial per-player-per-company
    private final HashMapState<String, Integer> deniedIncomeMap = HashMapState.create(this, "deniedIncomeMap");

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

        // If the operating company closed while we were away (e.g., merged into Prussia during PFR),
        // we must immediately switch context to the next valid company to prevent NPEs.
        if (handleClosedOperatingCompany()) {
            // Ensure the PlayerManager is synchronized with the new company
            playerManager.setCurrentPlayer(operatingCompany.value().getPresident());
        }
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

// Use the shared helper method to switch companies if needed
                boolean companySwitched = handleClosedOperatingCompany();

                // If the operating company (M1) closed (e.g., merged into Prussia),
                // we must switch context to the NEXT operational company (M5).
                if (operatingCompany.value().isClosed()) {
                    List<PublicCompany> companies = getOperatingCompanies();
                    PublicCompany current = operatingCompany.value();
                    int currentIndex = companies.indexOf(current);
                    
                    // Fallback if not found
                    if (currentIndex == -1) currentIndex = 0;

                    PublicCompany nextComp = null;
                    int nextIndex = currentIndex;
                    int attempts = 0;

                    // Cycle forward to find the next OPEN and FLOATED company
                    while (attempts < companies.size()) {
                        nextIndex = (nextIndex + 1) % companies.size();
                        PublicCompany candidate = companies.get(nextIndex);
                        
                        if (!candidate.isClosed() && candidate.hasFloated()) {
                            nextComp = candidate;
                            break;
                        }
                        attempts++;
                    }

                    if (nextComp != null) {
                        log.info("Operating Company {} is closed. Advancing to next operational company: {}", 
                            current.getId(), nextComp.getId());
                        
                        // 1. Update the State
                        operatingCompany.set(nextComp);
                        
                        // 2. Update the internal index via Reflection (Best Effort)
                        try {
                            java.lang.reflect.Field indexField = null;
                            Class<?> clazz = this.getClass();
                            while (clazz != null && indexField == null) {
                                try { indexField = clazz.getDeclaredField("orCompIndex"); } catch (Exception e) {}
                                if (indexField == null) try { indexField = clazz.getDeclaredField("index"); } catch (Exception e) {}
                                if (indexField == null) clazz = clazz.getSuperclass();
                            }

                            if (indexField != null) {
                                indexField.setAccessible(true);
                                indexField.setInt(this, nextIndex);
                            }
                        } catch (Exception e) {
                            // Ignore failures, state update is usually sufficient
                        }
                        
                        // 3. Reset Step to INITIAL so the new company starts its turn properly
                        setStep(GameDef.OrStep.INITIAL);
                        
                        // 4. Initialize the turn (clears UI, sets up tokens, etc)
                        initTurn();
                        
                        // Mark that we switched so we skip the default logic below
                        companySwitched = true;
                    }
                }
                
                playerManager.setCurrentPlayer(operatingCompany.value().getPresident());
                stepObject.set(GameDef.OrStep.BUY_TRAIN);
            }
        }
        return true;
    }

    @Override
    protected void newPhaseChecks() {
        PhaseManager phaseManager = getRoot().getPhaseManager();
        Phase currentPhase = phaseManager.getCurrentPhase();

        for (PublicCompany company : operatingCompanies.view()) {
            company.getPortfolioModel().rustObsoleteTrains();
        }

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

        if (prNotStarted) {
            for (TrainCardType tct : trainsBoughtThisTurn) {
                String id = tct.getId();
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
            closePhase5Privates();

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
    public boolean layTile(LayTile action) {
        // SAFETY NET: Force cleanup of closed private properties immediately before
        // processing.
        forceCleanupGhosts();

        PublicCompany company = action.getCompany();
        boolean isBAHomeLay = company.getId().equals("BA") &&
                !company.hasOperated() &&
                action.getChosenHex().isHomeFor(company) &&
                !this.mandatoryBadenTokenLaid.value();

        // Snapshot state for potential restoration (PfB Logic)
        boolean wasNormalLaid = normalTileLaidThisTurn.value();
        GameDef.OrStep stepBefore = getStep();
        Map<String, Integer> laysSnapshot = new HashMap<>();
        for (String key : tileLaysPerColour.viewKeySet()) {
            laysSnapshot.put(key, tileLaysPerColour.get(key));
        }

        // Execute the parent action
        boolean result = super.layTile(action);

        // --- PfB/PF State Restoration Logic ---
        // (Handles the specific complexity of PfB/PF multi-step interactions)
        boolean isPfB = false;
        SpecialProperty actionSp = action.getSpecialProperty();
        if (result && actionSp != null &&
                actionSp.getOriginalCompany() != null &&
                ("PfB".equals(actionSp.getOriginalCompany().getId())
                        || "PF".equals(actionSp.getOriginalCompany().getId()))) {
            isPfB = true;
        }

        if (result && isPfB) {
            normalTileLaidThisTurn.set(wasNormalLaid);
            tileLaysPerColour.clear();
            tileLaysPerColour.initFromMap(laysSnapshot);
            if (getStep() != stepBefore && !action.getChosenHex().getId().equals("L6")) {
                setStep(stepBefore);
            }
        }

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

        // --- Baden Interruption Check ---
        if (result && action.getChosenHex().getId().equals("L6")) {
            PublicCompany ba = companyManager.getPublicCompany("BA");
            boolean isBrownUpgrade = false;
            if (action.getLaidTile() != null) {
                String color = action.getLaidTile().getColourText();
                if ("Brown".equalsIgnoreCase(color)) {
                    isBrownUpgrade = true;
                }
            }

            if (!company.equals(ba) && ba.hasFloated() && !this.mandatoryBadenTokenLaid.value() && !isBrownUpgrade) {
                interruptedCompany.set(company);
                operatingCompany.set(ba);
                playerManager.setCurrentPlayer(ba.getPresident());
                awaitingBadenHomeToken.set(true);
            }
        }

        // --- START FIX: WATERTIGHT SEQUENCE ENFORCEMENT ---
        // Goal: Support "Normal Tile -> Special Tile" sequence ROBUSTLY.
        // Logic: If the engine auto-advanced the step, but we still have a usable
        // special property,
        // we FORCE the step back to LAY_TRACK. This compels the user to either use the
        // property
        // or explicitly click "Done", which writes a NullAction to the log.
        // This explicitly recorded "Done" ensures future replays never desync.
        if (result && getStep() != GameDef.OrStep.LAY_TRACK
                && company.equals(operatingCompany.value())
                && !awaitingBadenHomeToken.value()) {

            boolean hasUsableSpecial = false;
            List<SpecialTileLay> specials = getSpecialProperties(SpecialTileLay.class);

            if (specials != null) {
                for (SpecialTileLay sp : specials) {
                    // Filter 1: Already used?
                    if (sp.isExercised())
                        continue;

                    // Filter 2: OBB Limit (already laid M15/M17 this turn?)
                    if (sp instanceof SpecialSingleTileLay) {
                        String loc = ((SpecialSingleTileLay) sp).getLocationNameString();
                        if (loc != null && loc.matches("M1[57]") && hasLaidExtraOBBTile.value()) {
                            continue;
                        }
                    }

                    // Filter 3: Don't count the property we *just* used
                    if (action.getSpecialProperty() == sp) {
                        continue;
                    }

                    hasUsableSpecial = true;
                    break;
                }
            }

            if (hasUsableSpecial) {
                setStep(GameDef.OrStep.LAY_TRACK);
            }
        }
        // --- END FIX ---

        return result;
    }

    private void forceCleanupGhosts() {
        for (PrivateCompany pc : companyManager.getAllPrivateCompanies()) {
            if (pc.isClosed()) {
                removeSpecialProperties(pc);
            }
        }
    }

    private void removeSpecialProperties(PrivateCompany pc) {
        if (pc == null)
            return;

        try {
            java.lang.reflect.Field portfolioSetField = getFieldInHierarchy(pc.getClass(), "specialProperties");
            if (portfolioSetField == null)
                return;
            portfolioSetField.setAccessible(true);
            Object portfolioSetObj = portfolioSetField.get(pc);
            if (portfolioSetObj == null)
                return;

            java.lang.reflect.Field internalPortfolioField = getFieldInHierarchy(portfolioSetObj.getClass(),
                    "portfolio");
            if (internalPortfolioField == null)
                return;
            internalPortfolioField.setAccessible(true);
            Object treeSetStateObj = internalPortfolioField.get(portfolioSetObj);
            if (treeSetStateObj == null)
                return;

            try {
                java.lang.reflect.Method clearMethod = treeSetStateObj.getClass().getMethod("clear");
                clearMethod.invoke(treeSetStateObj);
            } catch (Exception e) {
                if (treeSetStateObj instanceof java.util.Collection) {
                    ((java.util.Collection<?>) treeSetStateObj).clear();
                } else {
                }
            }
        } catch (Exception e) {
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

        // Use the proper State object check. This ensures strict 1-token limits AND
        // supports Undo/Redo.
        // Previous logic using local 'tokensLaidCount' failed on Undo and didn't update
        // the UI state.
        if (!isSpecial && normalTokenLaidThisTurn.value()) {
            return false;
        }

        boolean result = super.layBaseToken(action);

        if (result) {
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

                // CRITICAL FIX: Restoration of Step for Original Company
                // The original company (likely BY) was interrupted during Tile Laying.
                // We must return the game state to LAY_TRACK so they can finish their turn.
                setStep(GameDef.OrStep.LAY_TRACK);

                // We also ensure the transient token list for BY is clear to avoid state
                // pollution
                if (currentNormalTokenLays != null)
                    currentNormalTokenLays.clear();
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
                removeSpecialProperties(owner);
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

            if (m15 != null && !m15.isPreprintedTileCurrent() &&
                    m17 != null && !m17.isPreprintedTileCurrent()) {

                obb.close();
                removeSpecialProperties(obb);
                ReportBuffer.add(this, LocalText.getText("CompanyCloses", obb.getId()));
            }
        }
    }

    private void closePhase5Privates() {
        for (PrivateCompany pc : companyManager.getAllPrivateCompanies()) {
            if (pc.isClosed())
                continue;

            String id = pc.getId();
            if (!"BB".equals(id) && !"HB".equals(id)) {
                pc.close();
                removeSpecialProperties(pc);
                ReportBuffer.add(this, LocalText.getText("CompanyCloses", pc.getId()));
            }
        }
    }

    @Override
    public <T extends SpecialProperty> List<T> getSpecialProperties(Class<T> clazz) {
        List<T> properties = super.getSpecialProperties(clazz);
        if (properties != null && !properties.isEmpty()) {
            properties.removeIf(sp -> {
                boolean remove = sp.getOriginalCompany() instanceof PrivateCompany &&
                        ((PrivateCompany) sp.getOriginalCompany()).isClosed();
                if (remove) {

                }
                return remove;
            });
        }
        return properties;
    }

    @Override
    public boolean setPossibleActions() {
        if (awaitingBadenHomeToken.value()) {
            possibleActions.clear();
            // CRITICAL FIX: Reset the doneAllowed flag.
            // Since we do not call super.setPossibleActions(), this flag retains its
            // 'true' state from the previous player's (BY) LAY_TRACK step.
            // This prevents the generation of a 'SKIP' action which causes an infinite
            // loop.
            doneAllowed.set(false);

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
            // DO NOT PRUNE HERE - BADEN needs these specific actions
            return true;
        }

        if (getStep() == GameDef.OrStep.DISCARD_TRAINS) {
            checkForExcessTrains();
        }

        GameDef.OrStep step = getStep();
        PublicCompany company = operatingCompany.value();

        if (step == GameDef.OrStep.INITIAL) {
            initTurn();

            if (!company.hasOperated() && company.getId().equals("BA")) {
                MapHex homeHex = null;
                if (company.getHomeHexes() != null && !company.getHomeHexes().isEmpty()) {
                    homeHex = company.getHomeHexes().get(0);
                }
                if (homeHex != null && !homeHex.isPreprintedTileCurrent() && !homeHex.hasTokenOfCompany(company)) {
                    setStep(GameDef.OrStep.LAY_TOKEN);
                    boolean result = setPossibleActions();
                    pruneGhostActions();
                    return result;
                }
            }
            nextStep();
            boolean result = setPossibleActions();
            pruneGhostActions();
            return result;
        }

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
                    }
                }
                pruneGhostActions();
                return true;
            }
        }

        boolean result = super.setPossibleActions();

        if (result && !possibleActions.isEmpty()) {
            pruneGhostActions();
        }

        return result;
    }

    private void pruneGhostActions() {
        if (possibleActions.isEmpty())
            return;

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
                }
            }
        }
    }


    /**
     * Called directly by PrussianFormationRound to register an exchange.
     */
    public void registerPrussianExchange(Player player, net.sf.rails.game.Company c) {
        String cId = (c != null) ? c.getId() : "";
        if ("B".equals(cId)) cId = "BB"; // Handle alias

        boolean shouldDeny = false;

        // 1. Private Companies -> Always Deny
        if (c instanceof PrivateCompany || "BB".equals(cId) || "HB".equals(cId)) {
            shouldDeny = true;
        } 
        // 2. Public Companies
        else if (c instanceof PublicCompany) {
            PublicCompany minor = (PublicCompany) c;
            // Deny if it is the current operator OR has already operated
            if (operatingCompany.value() == minor || minor.hasOperated()) {
                shouldDeny = true;
            }
        }
        // 3. Fallback
        else if (c == null) {
            shouldDeny = true;
        }

        if (shouldDeny) {
            int deniedPercent = 10;
            // 5% Shares: M1, M3, M5, M6
            if ("M1".equals(cId) || "M3".equals(cId) || "M5".equals(cId) || "M6".equals(cId)) {
                deniedPercent = 5;
            }
            addIncomeDenialShare(player, deniedPercent);
            log.info("PFR Exchange Registered: {} by {} -> Denied {}%", cId, player.getName(), deniedPercent);
        } else {
            log.info("PFR Exchange Registered: {} by {} -> Allowed (Future Company).", cId, player.getName());
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
    protected Map<MoneyOwner, Integer> countSharesPerRecipient() {

        // 1. Get the standard distribution from the engine
        Map<MoneyOwner, Integer> sharesPerRecipient = super.countSharesPerRecipient();

        // --- START FIX ---
        // CRITICAL CHECK: The Income Denial Rule (4.6) applies ONLY to the Prussian (PR) dividend.
        // If any other company (e.g., BY, SX) is operating, we MUST ignore the denial map entirely.
        if (!operatingCompany.value().getId().equals(GameDef_1835.PR_ID)) {
            return sharesPerRecipient;
        }

        // 2. Apply 1835-Specific Logic: Denied Income (Rule 4.6)
        // We now check specific keys "Player|Company" to avoid global denial bugs.
        if (deniedIncomeMap != null && !deniedIncomeMap.isEmpty()) {
            
            PublicCompany currentComp = operatingCompany.value();
            PublicCompany prussian = companyManager.getPublicCompany(GameDef_1835.PR_ID);

            // Collect adjustments to avoid ConcurrentModificationException
            Map<MoneyOwner, Integer> deductions = new HashMap<>();
            int totalRedirectedShares = 0;

            for (MoneyOwner owner : sharesPerRecipient.keySet()) {
                if (!(owner instanceof Player)) continue;
                
                Player player = (Player) owner;
                
                // Check for the specific denial entry for PR (e.g. "Stefan1|PR")
                String specificKey = player.getName() + "|" + GameDef_1835.PR_ID;
                
                int sharePercentageDenied = 0;
                if (deniedIncomeMap.containsKey(specificKey)) {
                    sharePercentageDenied = deniedIncomeMap.get(specificKey);
                } 

                if (sharePercentageDenied > 0) {
                    int shareUnit = currentComp.getShareUnit();
                    if (shareUnit == 0) shareUnit = 10; // Safety
                    
                    int sharesToDeduct = sharePercentageDenied / shareUnit;
                    int currentShares = sharesPerRecipient.get(player);

                    // Clamp to actual holdings
                    int actualDeduction = Math.min(sharesToDeduct, currentShares);

                    if (actualDeduction > 0) {
                        deductions.put(player, actualDeduction);
                        totalRedirectedShares += actualDeduction;

                        ReportBuffer.add(this, LocalText.getText("NoIncomeForPreviousOperation",
                                player.getId(),
                                actualDeduction * shareUnit,
                                GameDef_1835.PR_ID));
                    }
                }
            }

            // Apply deductions
            for (Map.Entry<MoneyOwner, Integer> entry : deductions.entrySet()) {
                MoneyOwner p = entry.getKey();
                int deduct = entry.getValue();
                int current = sharesPerRecipient.get(p);
                
                if (current == deduct) {
                    sharesPerRecipient.remove(p);
                } else {
                    sharesPerRecipient.put(p, current - deduct);
                }
            }

            // Redirect to PRUSSIAN Treasury (PR)
            if (totalRedirectedShares > 0 && prussian != null) {
                int currentPrShares = sharesPerRecipient.getOrDefault(prussian, 0);
                sharesPerRecipient.put(prussian, currentPrShares + totalRedirectedShares);
            }
        }
        // --- END FIX ---

        return sharesPerRecipient;
    }

    @Override
    public void cleanup() {
        super.cleanup();
        // Rule 4.6: Income denial is valid ONLY for the specific Operating Round 
        // in which the exchange occurred. We must clear this data when the round ends
        // to prevent it from affecting future rounds.
        if (deniedIncomeMap != null) {
            deniedIncomeMap.clear();
        }
    }
    private void addIncomeDenialShare(Player player, String companyId, int share) {
        String key = player.getName() + "|" + companyId;
        if (!deniedIncomeMap.containsKey(key)) {
            deniedIncomeMap.put(key, share);
        } else {
            deniedIncomeMap.put(key, share + deniedIncomeMap.get(key));
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
        // This calculates limits and might set the step to DISCARD_TRAINS
        boolean success = super.buyTrain(action);
        if (!success)
            return false;

        // 3. Rule 4.6 Check: First 4-Train Trigger (Original 1835 Logic)
        if (!trainsBoughtThisTurn.isEmpty()) {
            TrainCardType boughtType = trainsBoughtThisTurn.get(trainsBoughtThisTurn.size() - 1);
            String id = boughtType.getId();

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
                    }
                }
            }
        }

        // 4. Suppress Immediate Discard if PFR Triggered (The New Fix)
        // If the PFR started (via manual trigger above OR super.buyTrain phase triggers),
        // we must prevent the 'DISCARD_TRAINS' step from sticking to this suspended Operating Round.
        if (gameManager.getCurrentRound() instanceof PrussianFormationRound) {
            if (getStep() == GameDef.OrStep.DISCARD_TRAINS) {
                log.info("PFR Triggered: Suppressing immediate train discard step for {}.", 
                         action.getCompany().getId());
                // Revert step to BUY_TRAIN so the flow proceeds naturally after PFR
                setStep(GameDef.OrStep.BUY_TRAIN);
            }
        }

        return true;
    }

    /**
     * Checks if the current operating company is closed. If so, advances to the next
     * operational company in the sequence.
     * @return true if the company was switched, false otherwise.
     */
    private boolean handleClosedOperatingCompany() {
        if (operatingCompany.value() != null && operatingCompany.value().isClosed()) {
            List<PublicCompany> companies = getOperatingCompanies();
            PublicCompany current = operatingCompany.value();
            int currentIndex = companies.indexOf(current);
            
            // Fallback if not found
            if (currentIndex == -1) currentIndex = 0;

            PublicCompany nextComp = null;
            int nextIndex = currentIndex;
            int attempts = 0;

            // Cycle forward to find the next OPEN and FLOATED company
            while (attempts < companies.size()) {
                nextIndex = (nextIndex + 1) % companies.size();
                PublicCompany candidate = companies.get(nextIndex);
                
                if (!candidate.isClosed() && candidate.hasFloated()) {
                    nextComp = candidate;
                    break;
                }
                attempts++;
            }

            if (nextComp != null) {
                log.info("Operating Company {} is closed. Advancing to next operational company: {}", 
                    current.getId(), nextComp.getId());
                
                // 1. Update the State
                operatingCompany.set(nextComp);
                
                // 2. Update the internal index via Reflection (Best Effort)
                try {
                    java.lang.reflect.Field indexField = null;
                    Class<?> clazz = this.getClass();
                    while (clazz != null && indexField == null) {
                        try { indexField = clazz.getDeclaredField("orCompIndex"); } catch (Exception e) {}
                        if (indexField == null) try { indexField = clazz.getDeclaredField("index"); } catch (Exception e) {}
                        if (indexField == null) clazz = clazz.getSuperclass();
                    }

                    if (indexField != null) {
                        indexField.setAccessible(true);
                        indexField.setInt(this, nextIndex);
                    }
                } catch (Exception e) {
                    // Ignore failures, state update is usually sufficient
                }
                
                // 3. Reset Step to INITIAL so the new company starts its turn properly
                setStep(GameDef.OrStep.INITIAL);
                
                // 4. Initialize the turn (clears UI, sets up tokens, etc)
                initTurn();
                
                return true;
            }
        }
        return false;
    }

    
}

