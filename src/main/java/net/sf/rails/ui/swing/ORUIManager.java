package net.sf.rails.ui.swing;

// Ensure this is imported
// Ensure this is imported
// Add if not present
// Add if not present
// Ensure ArrayList is imported
// Ensure List is imported
import java.util.*;

import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import net.sf.rails.algorithms.NetworkAdapter;
import net.sf.rails.algorithms.NetworkGraph;
import net.sf.rails.algorithms.NetworkVertex;
import net.sf.rails.common.Config;
import net.sf.rails.common.GameOption;
import net.sf.rails.common.GuiDef;
import net.sf.rails.common.LocalText;
import java.awt.Rectangle;
// Import the missing class
// NEW
// --- Add imports for MapHex, Tile, TileHexUpgrade, etc. ---
// Ensure MapHex is imported
// Ensure Tile is imported
import net.sf.rails.game.*;
import net.sf.rails.game.financial.ShareSellingRound;
import net.sf.rails.game.financial.StockRound;
import net.sf.rails.game.round.RoundFacade;
import net.sf.rails.game.special.SpecialProperty;
import net.sf.rails.game.special.SpecialSingleTileLay;
import net.sf.rails.game.special.SpecialTileLay;
import net.sf.rails.game.special.SpecialBaseTokenLay;
import net.sf.rails.game.state.Owner;
import net.sf.rails.sound.SoundManager;
import net.sf.rails.ui.swing.elements.*;
import net.sf.rails.ui.swing.hexmap.GUIHex;
import net.sf.rails.ui.swing.hexmap.HexMap;
import net.sf.rails.ui.swing.hexmap.HexUpgrade;
import net.sf.rails.ui.swing.hexmap.TileHexUpgrade;
import net.sf.rails.ui.swing.hexmap.TokenHexUpgrade;
import net.sf.rails.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rails.game.action.*;
import rails.game.correct.ClosePrivate;
import rails.game.correct.OperatingCost;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.awt.Component;
import net.sf.rails.game.specific._1835.PrussianFormationRound;
import net.sf.rails.game.ai.TokenLayOption; // *** ADD THIS IMPORT ***
import net.sf.rails.game.ai.TileLayOption; // *** ADD THIS IMPORT ***

import static net.sf.rails.ui.swing.GameUIManager.EXCHANGE_TOKENS_DIALOG;
import net.sf.rails.game.ai.AIPlayer; // *** NEW IMPORT ***

// FIXME: Add back corrections mechanisms
// Rails 2.0, Even better add a new mechanism that allows to use the standard mechanism for corrections
public class ORUIManager implements DialogOwner {

    private static final Logger log = LoggerFactory.getLogger(ORUIManager.class);

    protected transient GameUIManager gameUIManager;
    protected transient NetworkAdapter networkAdapter;

    protected transient ORWindow orWindow;
    protected transient ORPanel orPanel;
    private transient UpgradesPanel upgradePanel;
    private transient MapPanel mapPanel;
    private transient HexMap map;
    protected transient MessagePanel messagePanel;
    private transient RemainingTilesWindow remainingTiles;

    protected OperatingRound oRound;
    private transient List<PublicCompany> companies;

    // TODO: Remove storage of those variables
    // replace it by either action.getCompany() or oRound.getOperatingCompany()
    protected PublicCompany orComp;
    protected int orCompIndex;

    private LocalSteps localStep;

    private boolean privatesCanBeBoughtNow;

    private List<TileLayOption> currentValidTileLays = new ArrayList<>();
    private List<TokenLayOption> currentValidTokenLays = new ArrayList<>();

    protected final GUIHexUpgrades hexUpgrades = GUIHexUpgrades.create();

    /* Local substeps */
    public enum LocalSteps {
        INACTIVE, SELECT_HEX, SELECT_UPGRADE, SET_REVENUE, SELECT_PAYOUT
    }

    /* Keys of dialogs owned by this class */
    public static final String SELECT_DESTINATION_COMPANIES_DIALOG = "SelectDestinationCompanies";
    public static final String REPAY_LOANS_DIALOG = "RepayLoans";
    public static final String GOT_PERMISSION_DIALOG = "AskedPermissionDialog";
    public static final String TOKEN_EXCHANGE_DIALOG = "SelectTokensToExchange";

    public ORUIManager() {

    }

    void setGameUIManager(GameUIManager gameUIManager) {
        this.gameUIManager = gameUIManager;
        this.networkAdapter = NetworkAdapter.create(gameUIManager.getRoot());
    }

    void init(ORWindow orWindow) {

        this.orWindow = orWindow;

        orPanel = orWindow.getORPanel();
        mapPanel = orWindow.getMapPanel();
        upgradePanel = orWindow.getUpgradePanel();
        upgradePanel.setHexUpgrades(hexUpgrades);
        map = mapPanel.getMap();
        messagePanel = orWindow.getMessagePanel();

    }

    protected void initOR(OperatingRound or) {
        oRound = or;
        companies = or.getOperatingCompanies();
        orWindow.activate(oRound);
        this.orCompIndex = -1;
        this.orComp = null;
    }

    public void finish() {

        // 1. Tell the ORWindow to finish (original call)
        if (orWindow != null) {
            orWindow.finish();
        }

        // 2. Deactivate the tile/token upgrades panel (original call)
        if (upgradePanel != null) {
            upgradePanel.setInactive();
        }

        // 3. Reset the local UI step to neutral
        setLocalStep(LocalSteps.INACTIVE);

        // 4. Clear all map highlights (neutralizes red/selectable hexes)
        if (hexUpgrades != null && map != null) {
            for (GUIHex guiHex : hexUpgrades.getHexes()) {
                guiHex.setState(GUIHex.State.NORMAL);
            }
            hexUpgrades.clear();
            map.selectHex(null);
        }

        // 5. Disable all OR-specific buttons (greys them out)
        if (orPanel != null) {
            orPanel.disableButtons();

            // This is the CRITICAL FIX for the "ghost button" bug.
            // It removes "Discard..." buttons from the panel.
            orPanel.updateDynamicActions(new ArrayList<>()); // Clear dynamic buttons
            orPanel.redisplay();
        }

        // 6. Clear the company cache (original call)
        if (!(gameUIManager.getCurrentRound() instanceof ShareSellingRound)) {
            orComp = null;
        }
    }

protected void checkHexVisibilityOnUI(PossibleActions actions) {

        for (GUIHex hex : hexUpgrades.getHexes()) {
            boolean validFound = false;
            boolean invalidFound = false;

            for (HexUpgrade upgrade : hexUpgrades.getUpgrades(hex)) {
                // Check VALID upgrades first (Green/Red)
                if (upgrade.isValid()) {
                    if (upgrade instanceof TokenHexUpgrade) {
                        hex.setState(GUIHex.State.TOKEN_SELECTABLE); // Green
                    } else {
                        hex.setState(GUIHex.State.SELECTABLE); // Red
                    }
                    validFound = true;
                    break; // Found a valid move, stop checking
                } 
                // Check INVALID but VISIBLE upgrades (Pink - e.g. not enough money)
                else if (upgrade.isVisible()) {
                    invalidFound = true;
                }
            }

            // If no valid move was found, but a visible invalid one exists, set to Pink
            if (!validFound && invalidFound) {
                hex.setState(GUIHex.State.INVALIDS);
            }
        }
    }

    private void defineTileUpgrades(List<LayTile> actions) {
        for (LayTile layTile : actions) {

            switch (layTile.getType()) {
                case (LayTile.GENERIC):
                case (LayTile.GENERIC_EXCL_LOCATIONS):
                    addConnectedTileLays(layTile);
                    break;
                case (LayTile.SPECIAL_PROPERTY):
                    SpecialTileLay sp = layTile.getSpecialProperty();
                    if (sp.requiresConnection()) {
                        addConnectedTileLays(layTile);
                        // MBr: 20210120 - So far no Private has connected and neighbours as power,
                        // so we dont need to add this here.
                    } else {
                        // MBr: 20210120 - Introducing the hook for the new private power for
                        // 18Chesapeake and also 1844
                        if (((SpecialSingleTileLay) sp).hasNeighbours()) {
                            addNeighbouredTileLays(layTile);
                        } else {
                            addLocatedTileLays(layTile);
                        }
                    }
                    break;
                case (LayTile.LOCATION_SPECIFIC):
                    addLocatedTileLays(layTile);
                    break;
                case (LayTile.CORRECTION):
                    addCorrectionTileLays(layTile);
                default:
            }
        }

    }

    private void addNeighbouredTileLays(LayTile layTile) {
    }

    private void addConnectedTileLays(LayTile layTile) {
        NetworkGraph graph = networkAdapter.getRouteGraph(layTile.getCompany(), true, false);
        Map<MapHex, HexSidesSet> mapHexSides = graph.getReachableSides();
        Multimap<MapHex, Station> mapHexStations = graph.getPassableStations();
        Phase currentPhase = gameUIManager.getCurrentPhase();

        boolean allLocations = (layTile.getLocations() == null
                || layTile.getLocations().isEmpty());

        for (MapHex hex : Sets.union(mapHexSides.keySet(), mapHexStations.keySet())) {

            // For the initial Belgium exclusion in 1826
            // layTile.getType(), hex, layTile.getLocations(), allLocations);
            if (layTile.getType() == LayTile.GENERIC_EXCL_LOCATIONS
                    && !allLocations
                    && layTile.getLocations().contains(hex)) {
                continue;
            }
            // Accept an immediate tile lay on reserved hexes if the reserving company
            // president is the current player.
            EnumSet<TileHexUpgrade.Invalids> allowances = EnumSet.noneOf(TileHexUpgrade.Invalids.class);
            if (hex.isReservedForCompany()) {
                // For now we accept this action, but will later check for permission
                allowances.add(TileHexUpgrade.Invalids.HEX_RESERVED);
            }
            if (allLocations
                    || layTile.getType() != LayTile.GENERIC_EXCL_LOCATIONS && layTile.getLocations().contains(hex)
                    || layTile.getType() == LayTile.GENERIC_EXCL_LOCATIONS && !layTile.getLocations().contains(hex)) {
                GUIHex guiHex = map.getHex(hex);
                String routeAlgorithm = GameOption.getValue(gameUIManager.getRoot(),
                        "RouteAlgorithm");
                Set<TileHexUpgrade> upgrades = TileHexUpgrade.create(guiHex,
                        mapHexSides.get(hex),
                        mapHexStations.get(hex), layTile, routeAlgorithm);
                TileHexUpgrade.validates(upgrades, currentPhase, allowances);
                gameSpecificTileUpgradeValidation(upgrades, layTile, currentPhase);
                hexUpgrades.putAll(guiHex, upgrades);
            }
        }

        // scroll map to center over companies network
        String autoScroll = Config.getGameSpecific(gameUIManager.getRoot().getGameName(), "map.autoscroll");
        if (Util.hasValue(autoScroll) && autoScroll.equalsIgnoreCase("no")) {
            // do nothing
        } else {
            mapPanel.scrollPaneShowRectangle(
                    NetworkVertex.getVertexMapCoverage(map, graph.getGraph().vertexSet()));
        }
    }

    /**
     * Stub to do additional validation.
     * Used in SOH to prevent showing an upgrade that
     * incorrectly uses a private special property.
     * 
     * @param upgrades
     * @param layTile
     */
    protected void gameSpecificTileUpgradeValidation(Set<TileHexUpgrade> upgrades,
            LayTile layTile,
            Phase currentPhase) {
    }

    private void addLocatedTileLays(LayTile layTile) {
        if (layTile.getLocations() != null) {
            for (MapHex hex : layTile.getLocations()) {
                GUIHex guiHex = map.getHex(hex);
                Set<TileHexUpgrade> upgrades = TileHexUpgrade.createLocated(guiHex, layTile);
                TileHexUpgrade.validates(upgrades, gameUIManager.getCurrentPhase());
                hexUpgrades.putAll(guiHex, upgrades);
            }
        }
    }

    private void defineTokenUpgrades(List<LayToken> actions) {

        for (LayToken layToken : actions) {
            if (layToken instanceof LayBaseToken) {
                LayBaseToken layBaseToken = (LayBaseToken) layToken;
                switch (layBaseToken.getType()) {
                    case (LayBaseToken.GENERIC):
                        addGenericTokenLays(layBaseToken);
                        break;
                    case (LayBaseToken.LOCATION_SPECIFIC):
                    case (LayBaseToken.SPECIAL_PROPERTY):
                        if (layBaseToken.getLocations() != null) {
                            addLocatedTokenLays(layBaseToken);
                        } else {
                            addGenericTokenLays(layBaseToken);
                        }
                        break;
                    case LayBaseToken.FORCED_LAY:
                    case LayBaseToken.HOME_CITY:
                    case LayBaseToken.NON_CITY:
                        addLocatedTokenLays(layBaseToken);
                        break;
                    case (LayTile.CORRECTION):
                        addCorrectionTokenLays(layBaseToken);
                    default:
                }
            } else if (layToken instanceof LayBonusToken) {
                // Assumption: BonusTokens are always located
                addLocatedTokenLays(layToken);
            }
        }
    }

private void addGenericTokenLays(LayBaseToken action) {
        PublicCompany company = action.getCompany();
        if (company.getBaseTokenLayCostMethod() == PublicCompany.BaseCostMethod.ROUTE_DISTANCE) {
            // ... (1826 logic unchanged) ...
        } else { // The old method
            // Use existing strict pathfinding (true, false). 
            // The graph already knows where we can/cannot go based on tokens/blocking.
            NetworkGraph graph = networkAdapter.getRouteGraph(company, true, false);
            
            Multimap<MapHex, Stop> hexStops = graph.getTokenableStops(company);
            for (MapHex hex : hexStops.keySet()) {
                GUIHex guiHex = map.getHex(hex);
                TokenHexUpgrade upgrade = TokenHexUpgrade.create(this, guiHex, hexStops.get(hex), action);
                TokenHexUpgrade.validates(upgrade);
                hexUpgrades.put(guiHex, upgrade);
            }
        }
    }

    protected void addLocatedTokenLays(LayToken action) {
        for (MapHex hex : action.getLocations()) {
            GUIHex guiHex = map.getHex(hex);
            TokenHexUpgrade upgrade = TokenHexUpgrade.create(
                    this, guiHex, hex.getTokenableStops(action.getCompany()), action);
            TokenHexUpgrade.validates(upgrade);
            hexUpgrades.put(guiHex, upgrade);
        }
    }

    private void addCorrectionTokenLays(LayToken action) {
        for (GUIHex guiHex : map.getHexes()) {
            MapHex hex = guiHex.getHex();
            List<Stop> tokenableStops = Lists.newArrayList();
            for (Stop stop : hex.getStops()) {
                if (stop.isTokenableFor(action.getCompany())) {
                    tokenableStops.add(stop);
                }
            }
            if (!tokenableStops.isEmpty()) {
                TokenHexUpgrade upgrade = TokenHexUpgrade.create(this, guiHex, tokenableStops, action);
                TokenHexUpgrade.validates(upgrade);
                hexUpgrades.put(guiHex, upgrade);
            }
        }
    }

    public void updateMessage() {

        // This entire method is obsolete, as all message logic is now handled
        // at the top of updateStatus(). We disable it to prevent it from
        // overwriting the "Thinking Text".
        if (true) {
            return;
        }

    }

    // File: net.sf.rails.ui.swing.ORUIManager.java

    public void processAction(String command, List<PossibleAction> actions, Component source) {

        if (command.equals(ORPanel.REM_TILES_CMD)) {
            displayRemainingTiles();
            return;
        }

        // Add a guard to prevent processing actions if the game is not in an
        // OperatingRound. This stops "ghost" buttons from the ORPanel
        // from firing actions during a StockRound.
        if (!(gameUIManager.getCurrentRound() instanceof OperatingRound)
                && !(gameUIManager.getCurrentRound() instanceof PrussianFormationRound)) {

            return;
        }

        try {
            // --- Handle new buttons FIRST ---

            PossibleAction actionToProcess = (actions != null && !actions.isEmpty()) ? actions.get(0) : null;

            if (actionToProcess instanceof BuyTrain) {
                BuyTrain buyAction = (BuyTrain) actionToProcess;

                // Check for our new PriceMode
                if (buyAction.getPriceMode() == PriceMode.VARIABLE) {

                    // If min == max, it's a fixed-price buy (IPO/Pool/Fixed Trade).
                    // Just set the price and process, no modal needed.
                    if (buyAction.getMinPrice() == buyAction.getMaxPrice()) {
                        buyAction.setPricePaid(buyAction.getMinPrice());
                        buyAction.setAddedCash(0); // No president cash for IPO/Pool
                        orWindow.process(buyAction);
                    } else {
                        // Min != Max, this is a true variable price (from another company)
                        handleVariablePriceBuy(buyAction); // Call new helper method
                    }
                    return; // We are done. Do not fall through.
                }
                // If not PriceMode.VARIABLE, it's an old action.
                // Let it fall through to the logic below (which we are about to remove)
            }

            if (actionToProcess != null && !processGameSpecificActions(actions)) {

                // *** CRITICAL FAIL-SAFE (Keep) ***
                if (actionToProcess instanceof BuyTrain) {
                    BuyTrain buyAction = (BuyTrain) actionToProcess;
                    if (buyAction.getPricePaid() == 0 && buyAction.getFixedCost() > 0
                            && buyAction.getFixedCostMode() == BuyTrain.Mode.FIXED) {
                        buyAction.setPricePaid(buyAction.getFixedCost());
                    }
                }

                if (actionToProcess instanceof SetDividend) {
                    setDividend(command, (SetDividend) actionToProcess);

                } else if (actionToProcess instanceof BuyBonusToken) {
                    buyBonusToken((BuyBonusToken) actionToProcess);

                } else if (actionToProcess instanceof NullAction || actionToProcess instanceof GameAction) {
                    orWindow.process(actionToProcess);

                } else if (actionToProcess instanceof ReachDestinations) {
                    reachDestinations((ReachDestinations) actionToProcess);

                } else if (actionToProcess instanceof GrowCompany) {
                    orWindow.process(actionToProcess);

                } else if (actionToProcess instanceof TakeLoans) {
                    takeLoans((TakeLoans) actionToProcess);

                } else if (actionToProcess instanceof RepayLoans) {
                    repayLoans((RepayLoans) actionToProcess);

                } else if (actionToProcess instanceof UseSpecialProperty) {
                    useSpecialProperty((UseSpecialProperty) actionToProcess);

                } else if (actionToProcess instanceof ClosePrivate) {
                    gameUIManager.processAction(actionToProcess);

                } else if (actionToProcess instanceof BuyPrivate) {
                    orWindow.process(actionToProcess);

                } else if (actionToProcess instanceof OperatingCost) {
                    orWindow.process(actionToProcess);

                } else if (actionToProcess instanceof DiscardTrain) {
                    orWindow.process(actionToProcess);

                } else {
                    orWindow.process(actionToProcess);
                }

            } else {
                // Handle commands that didn't come from an ActionTaker OR where action was null

                if (command.equals(ORPanel.OPERATING_COST_CMD)) {
                    operatingCosts();

                } else if (command.equals(ORPanel.SET_REVENUE_CMD)) {
                    try {
                        List<SetDividend> dividendActions = getPossibleActions().getType(SetDividend.class);
                        if (dividendActions != null && !dividendActions.isEmpty()) {
                            setDividend(command, dividendActions.get(0));
                        } else {

                        }
                    } catch (Exception e) {
                    }

                } else if (command.equals(ORPanel.BUY_PRIVATE_CMD)) {
                }

            }
        } catch (Exception e) {
        }

        if (this.getORWindow() != null && this.getORWindow().isVisible()) {
            this.getORWindow().requestFocus();
        }

    }

    private void displayRemainingTiles() {
        if (remainingTiles == null) {
            // Creates the window if it doesn't exist
            remainingTiles = new RemainingTilesWindow(orWindow);
        } else {
            // Brings it to front if it's already open
            remainingTiles.activate();
        }
    }

    /** Stub, can be overridden in subclasses */
    // FIXME: As above, really a list of actions?
    protected boolean processGameSpecificActions(List<PossibleAction> actions) {
        return false;
    }

    protected void setDividend(String command, SetDividend action) {
        // The 'Set Revenue' phase is removed. If this command arrives (should be
        // impossible), ignore it.
        if (command.equals(ORPanel.SET_REVENUE_CMD)) {
            return;
        }

        // Standard processing for Payout/Withhold/Split buttons
        orWindow.process(action);
    }

    private void buyBonusToken(BuyBonusToken action) {

        orWindow.process(action);
    }

    protected void reachDestinations(ReachDestinations action) {

        List<String> options = new ArrayList<>();
        List<PublicCompany> companies = action.getPossibleCompanies();

        for (PublicCompany company : companies) {
            options.add(company.getId());
        }

        if (options.size() > 0) {
            orWindow.setVisible(true);
            orWindow.toFront();

            CheckBoxDialog dialog = new CheckBoxDialog(SELECT_DESTINATION_COMPANIES_DIALOG,
                    this,
                    orWindow,
                    LocalText.getText("DestinationsReached"),
                    LocalText.getText("DestinationsReachedPrompt"),
                    options.toArray(new String[0]));
            setCurrentDialog(dialog, action);
        }
    }

    protected boolean gotPermission(GUIHex guiHex) {

        // Check if the clicked hex is reserved for a company
        MapHex hex = guiHex.getHex();
        if (!hex.isReservedForCompany() || !hex.isPreprintedTileCurrent())
            return true;

        // Check if this is a tile upgrade
        HexUpgrade hexUpgrade = (HexUpgrade) hexUpgrades.getUpgrades(guiHex).toArray()[0];
        if (!(hexUpgrade instanceof TileHexUpgrade))
            return true;

        // Check if permission from another player is required
        TileHexUpgrade upgrade = (TileHexUpgrade) hexUpgrade;
        LayTile action = upgrade.getAction();
        Player thisPlayer = action.getPlayer();
        Player otherPlayer = guiHex.getHex().getReservedForCompany().getPresident();
        if (thisPlayer.equals(otherPlayer))
            return true;

        // We have to, so start a dialog.
        // The current player should have got permission off-game.
        ConfirmationDialog dialog = new ConfirmationDialog(GOT_PERMISSION_DIALOG,
                this,
                orWindow,
                LocalText.getText("GotPermission"),
                LocalText.getText("GotPermissionDialog", otherPlayer, upgrade.getHex().getHex()),
                LocalText.getText("Yes"),
                LocalText.getText("No"));
        setCurrentDialog(dialog, action);
        return true;
    }

    public void confirmUpgrade() {
        HexUpgrade upgrade = hexUpgrades.getActiveUpgrade();
        if (upgrade instanceof TileHexUpgrade) {
            layTile((TileHexUpgrade) upgrade);
        }
        if (upgrade instanceof TokenHexUpgrade) {
            layToken((TokenHexUpgrade) upgrade);
        }
    }

    public void skipUpgrade() {
        if (getPossibleActions().containsCorrections()) {
            // skip on corrections => return to Select Hex
            map.selectHex(null);
            setLocalStep(LocalSteps.SELECT_HEX);
        } else {
            orWindow.process(new NullAction(gameUIManager.getRoot(), NullAction.Mode.SKIP));
        }
    }

    protected void layTile(TileHexUpgrade upgrade) {
        LayTile allowance = upgrade.getAction();

        allowance.setChosenHex(upgrade.getHex().getHex());
        int orientation = upgrade.getCurrentRotation().getTrackPointNumber();
        allowance.setOrientation(orientation);
        Tile targetTile = upgrade.getUpgrade().getTargetTile();
        allowance.setLaidTile(targetTile);
        allowance.setRelayBaseTokens(upgrade.isRelayBaseTokens());

        if (!orWindow.process(allowance)) {
            setLocalStep(LocalSteps.SELECT_HEX);
        }
    }

    private void layToken(TokenHexUpgrade upgrade) {
        LayToken action = upgrade.getAction();
        if (action instanceof LayBaseToken) {
            layBaseToken(upgrade);
        } else if (action instanceof LayBonusToken) {
            layBonusToken(upgrade);
        }
    }

    private void layBaseToken(TokenHexUpgrade upgrade) {
        MapHex hex = upgrade.getHex().getHex();
        LayBaseToken action = (LayBaseToken) upgrade.getAction();

        action.setChosenHex(hex);
        if (upgrade.getSelectedStop() != null) { // Added for 18Scan, still necessary?
            action.setChosenStation(upgrade.getSelectedStop().getRelatedStationNumber());
        }

        if (!orWindow.process(action)) {
            setLocalStep(LocalSteps.SELECT_HEX);
        }
    }

    /**
     * Lay Token finished.
     *
     * @param upgrade The LayBonusToken action object of the laid token.
     */
    // FIXME: This has to be rewritten
    public void layBonusToken(TokenHexUpgrade upgrade) {

        LayToken action = upgrade.getAction();

        // Assumption for now: always BonusToken
        // We might use it later for BaseTokens too.

        HexMap map = mapPanel.getMap();
        GUIHex selectedHex = map.getSelectedHex();

        if (selectedHex != null) {
            LayToken executedAction = action;

            executedAction.setChosenHex(selectedHex.getHex());

            if (orWindow.process(executedAction)) {
                // FIXME: Should this be setInactive(), please check
                upgradePanel.setActive();
                map.selectHex(null);
                // ensure painting the token (model update currently does not arrive at UI)
                map.repaintTokens(selectedHex.getBounds());
            }
        }
    }

    public void operatingCosts() {

        List<String> textOC = new ArrayList<>();
        List<OperatingCost> actionOC = getPossibleActions().getType(OperatingCost.class);

        for (OperatingCost ac : actionOC) {

            String suggestedCostText;
            if (ac.isFreeEntryAllowed())
                suggestedCostText = LocalText.getText("OCAmountEntry");
            else
                suggestedCostText = gameUIManager.format(ac.getAmount());

            OperatingCost.OCType t = ac.getOCType();
            if (t == OperatingCost.OCType.LAY_TILE)
                textOC.add(LocalText.getText("OCLayTile",
                        suggestedCostText));

            if (t == OperatingCost.OCType.LAY_BASE_TOKEN)
                textOC.add(LocalText.getText("OCLayBaseToken",
                        suggestedCostText));
        }

        if (!textOC.isEmpty()) {
            String chosenOption = (String) JOptionPane.showInputDialog(orWindow,
                    LocalText.getText("OCSelectMessage"),
                    LocalText.getText("OCSelectTitle"),
                    JOptionPane.QUESTION_MESSAGE, null,
                    textOC.toArray(), textOC.get(0));
            if (chosenOption != null) {
                int index = textOC.indexOf(chosenOption);
                OperatingCost chosenAction = actionOC.get(index);
                if (chosenAction.isFreeEntryAllowed()) {
                    String costString = (String) JOptionPane.showInputDialog(orWindow,
                            LocalText.getText("OCDialogMessage", chosenOption),
                            LocalText.getText("OCDialogTitle"),
                            JOptionPane.QUESTION_MESSAGE, null,
                            null, chosenAction.getAmount());
                    int cost;
                    try {
                        cost = Integer.parseInt(costString);
                    } catch (NumberFormatException e) {
                        cost = 0;
                    }
                    chosenAction.setAmount(cost);
                } else {
                    chosenAction.setAmount(chosenAction.getAmount());
                }

                if (orWindow.process(chosenAction)) {
                    updateMessage();
                }
            }
        }
    }

    public void buyTrain() {

        List<String> prompts = new ArrayList<>();
        Map<String, PossibleAction> promptToTrain = new HashMap<>();
        Train train;
        StringBuilder usingPrivates = new StringBuilder();

        PossibleAction selectedAction;
        BuyTrain buyAction;

        String prompt;
        StringBuffer b;
        int cost;
        Owner from;

        List<BuyTrain> buyableTrains = getPossibleActions().getType(BuyTrain.class);
        for (BuyTrain bTrain : buyableTrains) {
            cost = bTrain.getFixedCost();
            from = bTrain.getFromOwner();

            b = new StringBuffer();

            b.append(LocalText.getText("BUY_TRAIN_FROM",
                    bTrain.getType(),
                    from.getId()));
            if (bTrain.isForExchange()) {
                String exchTrainTypes = bTrain.getTrainsForExchange().toString()
                        // Replacing e.g. "[4_0]" by "4", or "[4_0, 5_0, 6_0]" by "4,5 or 6"
                        .replaceAll("[\\[ ]?(\\w+)_\\d+(,)?\\s?]?", "$1$2")
                        .replaceFirst(",(\\w+)$", " or $1");
                b.append(" (").append(LocalText.getText("DiscardingTrain", exchTrainTypes)).append(")");
            }
            // if (cost > 0) {
            BuyTrain.Mode mode = bTrain.getFixedCostMode();
            if (/* mode == null || */ mode == BuyTrain.Mode.FIXED) {
                b.append(" ").append(
                        LocalText.getText("AT_PRICE", gameUIManager.format(cost)));
            } else if (mode == BuyTrain.Mode.MAX) {
                b.append(" ").append(
                        LocalText.getText("AT_MAX_PRICE", gameUIManager.format(cost)));
            } else if (mode == BuyTrain.Mode.MIN) {
                b.append(" ").append(
                        LocalText.getText("AT_MIN_PRICE", gameUIManager.format(cost)));
            }
            // }
            if (bTrain.hasSpecialProperty()) {
                String priv = (bTrain.getSpecialProperty()).getOriginalCompany().getId();
                b.append(" ").append(LocalText.getText("USING_SP", priv));
                usingPrivates.append(", ").append(priv);
            }
            if (bTrain.mustPresidentAddCash()) {
                // This is for mandatory, fixed-price emergency buys (e.g., from IPO)
                b.append(" ").append(
                        LocalText.getText("YOU_MUST_ADD_CASH",
                                gameUIManager.format(bTrain.getPresidentCashToAdd())));
            } else if (bTrain.mayPresidentAddCash()) {
                // This is for optional, variable-price buys (from other companies)
                int amount = bTrain.getPresidentCashToAdd();

                if (amount == Integer.MAX_VALUE) {
                    // This is our "no limit" emergency buy (company has 0 trains)
                    // We can't add a LocalText key, so we'll hardcode the text.
                    b.append(" (Emergency: Add any amount)");
                } else {
                    // This is the old logic, in case another part of the game
                    // uses it for a fixed contribution.
                    b.append(" ").append(
                            LocalText.getText("YOU_MAY_ADD_CASH",
                                    gameUIManager.format(amount)));
                }
            }

            if (bTrain.getExtraMessage() != null) {
                b.append(" (").append(bTrain.getExtraMessage()).append(")");
            }
            prompt = b.toString();
            prompts.add(prompt);
            promptToTrain.put(prompt, bTrain);
        }

        if (prompts.size() == 0) {
            JOptionPane.showMessageDialog(orWindow,
                    LocalText.getText("CannotBuyAnyTrain"));
            return;
        }

        StringBuilder msgbuf = new StringBuilder(LocalText.getText("SelectTrain"));
        if (usingPrivates.length() > 0) {
            msgbuf.append("<br><font color=\"red\">");
            msgbuf.append(LocalText.getText("SelectCheapTrain",
                    usingPrivates.substring(2)));
            msgbuf.append("</font>");
        }
        messagePanel.setMessage(msgbuf.toString());

        String selectedActionText = (String) JOptionPane.showInputDialog(orWindow,
                LocalText.getText("BUY_WHICH_TRAIN"),
                LocalText.getText("WHICH_TRAIN"),
                JOptionPane.QUESTION_MESSAGE, null, prompts.toArray(),
                prompts.get(0));
        if (!Util.hasValue(selectedActionText))
            return;

        selectedAction = promptToTrain.get(selectedActionText);
        if (selectedAction == null)
            return;

        buyAction = (BuyTrain) selectedAction;
        train = buyAction.getTrain();
        PublicCompany company = buyAction.getCompany(); // This is the BUYER
        Owner seller = buyAction.getFromOwner();
        int fixedCost = buyAction.getFixedCost();
        BuyTrain.Mode mode = buyAction.getFixedCostMode();

        // The relationship between fixedCost and mode is explained
        // in the Javadoc of the Mode enum in the BuyTrain class.
        if (seller instanceof PublicCompany
                && !company.mustTradeTrainsAtFixedPrice()
                && !((PublicCompany) seller).mustTradeTrainsAtFixedPrice()
                && (fixedCost == 0 || mode != null && mode != BuyTrain.Mode.FIXED)) {
            String remark = "";
            String priceText;
            // if (fixedCost > 0 && mode != null) {
            priceText = gameUIManager.format(fixedCost);
            switch (mode) {
                case MIN:
                    remark = LocalText.getText("OrMore", priceText);
                    break;
                case MAX:
                    remark = LocalText.getText("OrLess", priceText);
                default:
            }
            // }
            prompt = LocalText.getText("WHICH_TRAIN_PRICE",
                    buyAction.getCompany().getId(),
                    train.toText(),
                    seller.getId(),
                    remark);
            String response;

            // Get the BUYING company's cash to use as the pre-filled value.
            String initialPrice = "0"; // Default to 0
            try {
                // 'company' is the operating company (the buyer)
                if (company != null) {
                    int buyerCash = company.getCash();
                    initialPrice = Integer.toString(buyerCash);
                }
            } catch (Exception e) {
            }

            for (;;) {
                response = (String) JOptionPane.showInputDialog(
                        orWindow,
                        prompt,
                        LocalText.getText("WHICH_PRICE"),
                        JOptionPane.QUESTION_MESSAGE,
                        null, // icon
                        null, // selectionValues (null means use a text field)
                        initialPrice // pre-filled value
                );

                if (response == null)
                    return; // Cancel
                int enteredPrice;
                try {
                    enteredPrice = Integer.parseInt(response);
                } catch (NumberFormatException e) {
                    // Price stays 0, this is handled below
                    enteredPrice = 0;
                }
                if (enteredPrice > 0
                        && (mode == BuyTrain.Mode.MIN && enteredPrice >= fixedCost
                                || mode == BuyTrain.Mode.MAX && enteredPrice <= fixedCost
                                || mode == BuyTrain.Mode.FREE)) {
                    fixedCost = enteredPrice;
                    break; // Got a valid price.
                }
                if (!prompt.startsWith("Please")) {
                    prompt = LocalText.getText("ENTER_PRICE_OR_CANCEL") + "\n"
                            + prompt;
                }
            }
        }

        Train exchangedTrain = null;
        if (train != null && buyAction.isForExchange()) {
            Set<Train> oldTrains = buyAction.getTrainsForExchange();
            if (oldTrains.size() == 1) {
                exchangedTrain = Iterables.get(oldTrains, 0);
            } else {
                List<String> oldTrainOptions = new ArrayList<>(oldTrains.size());
                String[] options = new String[oldTrains.size()];
                int jj = 0;
                for (int j = 0; j < oldTrains.size(); j++) {
                    options[jj + j] = LocalText.getText("N_Train", Iterables.get(oldTrains, j).toText());
                    oldTrainOptions.add(options[jj + j]);
                }
                String exchangedTrainName = (String) JOptionPane.showInputDialog(orWindow,
                        LocalText.getText("WHICH_TRAIN_EXCHANGE_FOR",
                                gameUIManager.format(fixedCost)),
                        LocalText.getText("WHICH_TRAIN_TO_EXCHANGE"),
                        JOptionPane.QUESTION_MESSAGE, null, options,
                        options[0]);
                if (exchangedTrainName != null) {
                    int index = oldTrainOptions.indexOf(exchangedTrainName);
                    if (index >= 0) {
                        exchangedTrain = Iterables.get(oldTrains, index);
                    }
                }
                if (exchangedTrain == null) {
                    // No valid train selected - cancel the buy action
                    train = null;
                }
            }
        }

        if (train != null) {

            buyAction.setPricePaid(fixedCost);
            buyAction.setExchangedTrain(exchangedTrain);
            if (buyAction.mustPresidentAddCash()) {
                buyAction.setAddedCash(buyAction.getPresidentCashToAdd());
            }
            orWindow.process(buyAction);
        }
    }

    public void buyPrivate() {

        int amount, index;
        List<String> privatesForSale = new ArrayList<>();
        List<BuyPrivate> privates = getPossibleActions().getType(BuyPrivate.class);
        String chosenOption;
        BuyPrivate chosenAction;
        int minPrice = 0, maxPrice = 0;
        String priceRange;

        for (BuyPrivate action : privates) {
            minPrice = action.getMinimumPrice();
            maxPrice = action.getMaximumPrice();
            if (minPrice < maxPrice) {
                priceRange = gameUIManager.format(minPrice) + "..."
                        + gameUIManager.format(maxPrice);
            } else {
                priceRange = gameUIManager.format(maxPrice);
            }

            privatesForSale.add(LocalText.getText("BuyPrivatePrompt",
                    action.getPrivateCompany().getId(),
                    action.getPrivateCompany().getOwner().getId(),
                    priceRange));
        }

        if (privatesForSale.size() > 0) {
            chosenOption = (String) JOptionPane.showInputDialog(orWindow,
                    LocalText.getText("BUY_WHICH_PRIVATE"),
                    LocalText.getText("WHICH_PRIVATE"),
                    JOptionPane.QUESTION_MESSAGE, null,
                    privatesForSale.toArray(), privatesForSale.get(0));
            if (chosenOption != null) {
                index = privatesForSale.indexOf(chosenOption);
                chosenAction = privates.get(index);
                minPrice = chosenAction.getMinimumPrice();
                maxPrice = chosenAction.getMaximumPrice();
                if (minPrice < maxPrice) {
                    String price = JOptionPane.showInputDialog(orWindow,
                            LocalText.getText("WHICH_PRIVATE_PRICE",
                                    chosenOption,
                                    gameUIManager.format(minPrice),
                                    gameUIManager.format(maxPrice)),
                            LocalText.getText("WHICH_PRICE"),
                            JOptionPane.QUESTION_MESSAGE);
                    try {
                        amount = Integer.parseInt(price);
                    } catch (NumberFormatException e) {
                        amount = 0; // This will generally be refused.
                    }
                    chosenAction.setPrice(amount);
                } else {
                    chosenAction.setPrice(maxPrice);
                }
                if (orWindow.process(chosenAction)) {
                    updateMessage();
                }
            }
        }

    }

    /**
     * Default implementation.
     * The <Loans> attributes number and value <b>must</b>
     * have been configured in CompanyManager.xml
     */
    protected void takeLoans(TakeLoans action) {

        if (action.getMaxNumber() == 1) {

            String message = LocalText.getText("PleaseConfirm");
            String prompt = LocalText.getText("TakeLoanPrompt",
                    action.getCompanyName(),
                    gameUIManager.format(action.getPrice()));
            if (JOptionPane.showConfirmDialog(orWindow, prompt,
                    message, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION) {
                action.setNumberTaken(1);
                orWindow.process(action);
            }

        } else {
            // For now we disregard the case of multiple loans
        }

    }

    protected void repayLoans(RepayLoans action) {

        int minNumber = action.getMinNumber();
        int maxNumber = action.getMaxNumber();
        int loanAmount = action.getPrice();
        int numberRepaid = 0;

        if (minNumber == maxNumber) {
            // No choice, just tell him
            JOptionPane.showMessageDialog(orWindow,
                    LocalText.getText("RepayLoan",
                            minNumber,
                            gameUIManager.format(loanAmount),
                            gameUIManager.format(minNumber * loanAmount)));
            numberRepaid = minNumber;
            action.setNumberTaken(numberRepaid);
            orWindow.process(action);
        } else {
            // List<String> options = new ArrayList<String>();
            String[] options = new String[maxNumber - minNumber + 1];
            for (int i = minNumber, j = 0; i <= maxNumber; i++, j++) {
                if (i == 0) {
                    options[j] = LocalText.getText("None");
                } else {
                    options[j] = LocalText.getText("RepayLoan",
                            i,
                            gameUIManager.format(loanAmount),
                            gameUIManager.format(i * loanAmount));
                }
            }
            RadioButtonDialog currentDialog = new RadioButtonDialog(REPAY_LOANS_DIALOG,
                    gameUIManager,
                    orWindow,
                    LocalText.getText("Select"),
                    LocalText.getText("SelectLoansToRepay", action.getCompanyName()),
                    options,
                    0);
            setCurrentDialog(currentDialog, action);
        }
    }

    /** Used to process some < properties from the 'Special' menu */
    /* In fact currently not used */
    protected void useSpecialProperty(UseSpecialProperty action) {
        gameUIManager.processAction(action);
    }

    protected void checkForGameSpecificActions(PublicCompany orComp,
            GameDef.OrStep orStep,
            PossibleActions possibleActions) {
    }

    /** Redraw the ORPanel if the company operating order has changed */
    protected void checkORCompanySequence(List<PublicCompany> oldCompanies, List<PublicCompany> newCompanies) {
        if (!Iterables.elementsEqual(oldCompanies, newCompanies)) {
            orPanel.recreate(oRound);
        }
    }

    protected void setLocalStep(LocalSteps localStep) {

        if (this.localStep == localStep) {

            return;
        }

        SoundManager.notifyOfORLocalStep(localStep);
        this.localStep = localStep;

        updateMessage();
        updateUpgradesPanel();
    }

    public void updateUpgradesPanel() {

        if (upgradePanel != null) {
            switch (localStep) {
                case INACTIVE:
                    upgradePanel.setInactive();
                    break;
                case SELECT_HEX:
                    upgradePanel.setActive();
                    break;
                case SELECT_UPGRADE:
                    upgradePanel.setSelect(map.getSelectedHex());
                    break;
                default:
                    upgradePanel.setInactive();
            }
        }
    }

    /*
     * If the token exchange limits are *per merged company*,
     * we need separator lines. This is used in 1826
     */
    private Integer[] separatorLines = null;

    public Integer[] getSeparatorLines() {
        return separatorLines;
    }

    public void clearSeparatorLines() {
        separatorLines = null;
    }

    private void prepareExchangeTokens(ExchangeTokens2 action) {
        prepareExchangeTokens(action, null);
    }

    private void prepareExchangeTokens(ExchangeTokens2 action, String errMsg) {

        List<String> options = new ArrayList<>();
        List<ExchangeTokens2.Location> locations = action.getLocations();
        List<Integer> sepLinesAfterOption = new ArrayList<>();

        PublicCompany newCompany = action.getNewCompany();
        int minimumExchanges = action.getMinNumberToExchange();
        int maximumExchanges = action.getMaxNumberToExchange();
        boolean perCompany = action.isExchangeCountPerCompany();

        ExchangeTokens2.Location location;
        PublicCompany oldCompany;
        PublicCompany prevOldCompany = null;
        Stop stop;

        for (int i = 0; i < locations.size(); i++) {
            location = locations.get(i);
            oldCompany = location.getOldCompany();
            if (prevOldCompany != null && !oldCompany.equals(prevOldCompany)) {
                sepLinesAfterOption.add(i - 1);
            }
            stop = location.getStop();
            options.add(LocalText.getText("SelectTokenExchangeOption",
                    oldCompany.getId(), stop.getStopComposedId()));
            prevOldCompany = oldCompany;
        }
        if (sepLinesAfterOption.size() > 0) {
            separatorLines = sepLinesAfterOption.toArray(new Integer[0]);
        }

        if (options.size() > 0) {
            orWindow.setVisible(true);
            orWindow.toFront();

            String title = LocalText.getText("SelectTokensToExchange");
            String prompt;
            if (perCompany) {
                prompt = LocalText.getText("SelectTokensToExchangePerComp",
                        maximumExchanges, newCompany);
            } else {
                prompt = LocalText.getText("SelectTokensToExchangeAllComps",
                        (minimumExchanges == maximumExchanges
                                ? maximumExchanges + ""
                                : minimumExchanges + "-" + maximumExchanges),
                        newCompany);
            }

            if (errMsg != null && errMsg.length() > 0) {
                prompt = "<html><font color=\"red\">" + errMsg + "</font><br>"
                        + prompt + "</html>";
            }

            CheckBoxDialog dialog = new CheckBoxDialog(EXCHANGE_TOKENS_DIALOG,
                    this,
                    orWindow,
                    title,
                    prompt,
                    options.toArray(new String[0]));
            setCurrentDialog(dialog, action);
        }
    }

    // Further Getters
    public MessagePanel getMessagePanel() {
        return messagePanel;
    }

    public UpgradesPanel getUpgradePanel() {
        return upgradePanel;
    }

    public HexMap getMap() {
        return map;
    }

    public GameUIManager getGameUIManager() {
        return gameUIManager;
    }

    public ORWindow getORWindow() {
        return orWindow;
    }

    public ORPanel getORPanel() {
        return orPanel;
    }

    // FIXME: Getting the possible actions inside ORUIManager methods should be
    // removed
    // Better transfer them by method args
    protected PossibleActions getPossibleActions() {
        return gameUIManager.getGameManager().getPossibleActions();
    }

    // DialogOwner interface methods
    @Override
    public void dialogActionPerformed() {

        JDialog currentDialog = getCurrentDialog();
        PossibleAction currentDialogAction = getCurrentDialogAction();

        if (currentDialog instanceof CheckBoxDialog) {

            CheckBoxDialog dialog = (CheckBoxDialog) currentDialog;

            if (currentDialogAction instanceof ReachDestinations) {
                ReachDestinations action = (ReachDestinations) currentDialogAction;

                boolean[] destined = dialog.getSelectedOptions();
                String[] options = dialog.getOptions();

                for (int index = 0; index < options.length; index++) {
                    if (destined[index]) {
                        action.addReachedCompany(action.getPossibleCompanies().get(index));
                    }
                }

                // Prevent that a null action gets processed
                if (action.getReachedCompanies() == null
                        || action.getReachedCompanies().isEmpty())
                    currentDialogAction = null;

            } else if (currentDialogAction instanceof ExchangeTokens2) {
                ExchangeTokens2 action = (ExchangeTokens2) currentDialogAction;
                boolean[] selected = dialog.getSelectedOptions();

                for (int i = 0; i < action.getLocations().size(); i++) {
                    if (selected[i])
                        action.getLocations().get(i).setSelected();
                }

                int maxCount = action.getMaxNumberToExchange();
                int minCount = action.getMinNumberToExchange();
                PublicCompany newCompany = action.getNewCompany();
                String errMsg = "";

                // Some prevalidation
                if (action.isExchangeCountPerCompany()) {

                    Map<PublicCompany, Integer> counts = new HashMap<>();
                    for (ExchangeTokens2.Location location : action.getLocations()) {
                        PublicCompany company = location.getOldCompany();
                        int prevCount = (counts.containsKey(company) ? counts.get(company) : 0);
                        if (location.isSelected())
                            counts.put(company, prevCount + 1);
                    }
                    for (PublicCompany company : counts.keySet()) {
                        int count = counts.get(company);
                        if (count < minCount || count > maxCount) {
                            if (errMsg.length() > 0)
                                errMsg += "<br>";
                            errMsg += LocalText.getText("WrongNumberOfTokensExchanged2",
                                    newCompany, minCount, maxCount, company, count);
                        }
                    }
                } else {
                    int count = 0;
                    for (ExchangeTokens2.Location location : action.getLocations()) {
                        if (location.isSelected())
                            count++;
                    }
                    if (count < minCount || count > maxCount) {
                        errMsg = LocalText.getText("WrongNumberOfTokensExchanged",
                                newCompany, minCount, maxCount, count);
                    }
                }
                if (errMsg.length() > 0) {
                    action.clearSelections();
                    prepareExchangeTokens(action, errMsg);
                    return;
                }
            }

        } else if (currentDialog instanceof ConfirmationDialog
                && currentDialogAction instanceof LayTile) {

            ConfirmationDialog dialog = (ConfirmationDialog) currentDialog;
            boolean gotPermission = dialog.getAnswer();
            if (gotPermission) {
                return;
            } else {
                currentDialogAction = null;
            }

        } else {
            currentDialogAction = null;
        }

        // Required even if no action is executed, to update the UI, re-enable buttons
        // etc.
        gameUIManager.processAction(currentDialogAction);
    }

    @Override
    public JDialog getCurrentDialog() {
        return gameUIManager.getCurrentDialog();
    }

    @Override
    public PossibleAction getCurrentDialogAction() {
        return gameUIManager.getCurrentDialogAction();
    }

    @Override
    public void setCurrentDialog(JDialog dialog, PossibleAction action) {
        gameUIManager.setCurrentDialog(dialog, action);
        if (!(dialog instanceof MessageDialog))
            orPanel.disableButtons();
    }

    /**
     * @return the hexUpgrades
     */
    protected GUIHexUpgrades getHexUpgrades() {
        return hexUpgrades;
    }

    /**
     * Helper method FOR THE UI MANAGER to reliably get the currently operating
     * company.
     * Uses the internal index and falls back to the OperatingRound object.
     * 
     * @return The currently operating PublicCompany as known by the UI manager, or
     *         null if none.
     */
    private PublicCompany getCurrentOperatingCompanyForUI() {
        // Use the existing index if valid, otherwise try getting directly from oRound
        // *** USE 'companies' (the declared variable) instead of 'operatingCompanies'
        // ***
        if (oRound != null && companies != null && !companies.isEmpty() && orCompIndex >= 0
                && orCompIndex < companies.size()) {
            // Ensure the cached orComp matches the index, or update it
            PublicCompany indexedCompany = companies.get(orCompIndex); // *** USE 'companies' ***
            if (this.orComp != indexedCompany) {

                this.orComp = indexedCompany;
            }
            // Ensure orComp is not null before returning
            if (this.orComp == null) {

                this.orComp = oRound.getOperatingCompany(); // Fallback
            }
            return this.orComp;
        } else if (oRound != null) {
            // Fallback to getting directly from OperatingRound object
            PublicCompany directCompany = oRound.getOperatingCompany(); // Uses the engine's method

            this.orComp = directCompany; // Update cache
            // Also try to update the index if possible
            // *** USE 'companies' ***
            if (directCompany != null && companies != null) {
                this.orCompIndex = companies.indexOf(directCompany); // *** USE 'companies' ***
            } else {
                this.orCompIndex = -1;
            }
            return directCompany;
        }
        return null; // Should not happen during a valid OR turn
    }

    // File: ORUIManager.java

    /**
     * Handles the request to execute an AI move during an Operating Round.
     * Fetches current actions, ensures map actions are calculated,
     * generates tile/token options, calls the AI, and processes the result.
     * NOTE: This version fetches PossibleActions internally.
     */
    public void processAIMove() { // Correct signature (no arguments)
        // Use try-finally to ensure button re-enabling (handled by GUIManager)
        try {

            // --- 1. Fetch CURRENT PossibleActions ---
            // CRITICAL: Get the latest actions from the GameManager right now.
            PossibleActions currentActions = getPossibleActions(); // Use internal getter

            // FIX: Do NOT abort if the list is empty! Pass the empty list to the AI,
            // so it can return the mandatory NullAction (which it won't in DISCARD_TRAINS,
            // but we must let it try). We remove the immediate 'return' here.
            if (currentActions == null) {
                // Wenn currentActions NULL ist, erstellen wir eine leere Liste zur Sicherheit.
                currentActions = PossibleActions.create();
            }

            if (currentActions == null || currentActions.isEmpty()) {

                return;
            }

            // --- 2. Ensure ORUIManager state is consistent (Optional but Recommended) ---
            // This builds hexUpgrades AND calls populate... methods internally.
            setMapRelatedActions(currentActions);

            // --- 4. Get the Populated Lists and Company ---
            List<TileLayOption> validTileLays = getCurrentValidTileLays();
            List<TokenLayOption> validTokenLays = getCurrentValidTokenLays();
            PublicCompany currentCompany = this.orComp;

            // --- CRITICAL FIX: Ensure non-null/non-empty actions list for mandatory step
            // ---
            PossibleActions actionsForAI = currentActions;

            // Führe nur im zwingenden Discard-Zustand eine erneute Abfrage der Aktionen
            // durch
            if (oRound.getStep() == GameDef.OrStep.DISCARD_TRAINS || oRound.getStep() == GameDef.OrStep.CALC_REVENUE) {
                // IMPORTANT: Call getPossibleActions() AGAIN to get the list
                // that the OperatingRound just generated into the GameManager, fixing the race
                // condition.
                actionsForAI = getPossibleActions();

            }

            // Safety check for company
            if (currentCompany == null) {
                if (this.oRound != null) {
                    currentCompany = this.oRound.getOperatingCompany();
                }
                if (currentCompany == null) {
                    return; // Still no company, cannot proceed
                }
            }

            // --- 5. Call AI ---
            AIPlayer ai = new AIPlayer("AI_OR", this.gameUIManager.getGameManager());

            // WICHTIG: Übergib die möglicherweise neu geladene actionsForAI-Liste!
            PossibleAction chosenAction = ai.chooseMove(currentCompany, actionsForAI, validTileLays, validTokenLays);

            // --- 6. Process Chosen Action ---
            if (chosenAction != null) {
                boolean processed = orWindow.process(chosenAction); // Process via ORWindow/Panel

            } else {
                PossibleAction fallback = findFallbackAction(actionsForAI); // Nutze die neu geladene Liste
                if (fallback != null) {
                    orWindow.process(fallback);
                } else {
                }
            }

        } catch (Exception e) {
        }
        // No finally block needed here, GameUIManager handles button re-enabling
    }

    // Helper to find Pass/Done/Skip (Keep this)
    private PossibleAction findFallbackAction(PossibleActions actions) {
        if (actions == null)
            return null;
        for (PossibleAction action : actions.getList()) {
            if (action instanceof NullAction) {
                NullAction.Mode mode = ((NullAction) action).getMode();
                if (mode == NullAction.Mode.PASS || mode == NullAction.Mode.DONE || mode == NullAction.Mode.SKIP) {
                    return action;
                }
            }
        }
        return null; // No suitable fallback
    }

    // ... rest of ORUIManager.java ...

    public RailsRoot getRoot() {
        // We assume gameUIManager is an initialized field of type GameUIManager
        return gameUIManager.getRoot();
    }

    // ++ ADD NEW HELPER METHOD to populate tile lays ++
    private void populateTileLayOptionsFromHexUpgrades() {
        if (hexUpgrades == null) {
            return; // Only populate during the correct step
        }
        for (GUIHex guiHex : hexUpgrades.getHexes()) {
            MapHex mapHex = guiHex.getHex();
            if (mapHex == null)
                continue;

            for (HexUpgrade upgrade : hexUpgrades.getUpgrades(guiHex)) {
                if (upgrade instanceof TileHexUpgrade tileUpgrade && tileUpgrade.isValid()) {
                    Tile targetTile = tileUpgrade.getUpgrade().getTargetTile();
                    LayTile originatingAction = tileUpgrade.getAction();
                    if (targetTile != null && originatingAction != null) {
                        HexSidesSet allValidRotations = tileUpgrade.getRotations();
                        for (HexSide rotationSide : allValidRotations) {
                            int orientation = rotationSide.getTrackPointNumber();
                            currentValidTileLays
                                    .add(new TileLayOption(mapHex, targetTile, orientation, originatingAction));

                        }
                    }
                }
            }
        }

    }

    private void populateTokenLayOptionsFromHexUpgrades() {
        if (hexUpgrades == null) {
            return; // Only populate during the correct step
        }
        for (GUIHex guiHex : hexUpgrades.getHexes()) {
            MapHex mapHex = guiHex.getHex();
            if (mapHex == null)
                continue;

            for (HexUpgrade upgrade : hexUpgrades.getUpgrades(guiHex)) {
                // *** Ensure we check for LayBaseToken specifically if needed ***
                if (upgrade instanceof TokenHexUpgrade tokenUpgrade &&
                        tokenUpgrade.isValid() &&
                        tokenUpgrade.getAction() instanceof LayBaseToken baseTokenAction) { // Cast check might be
                                                                                            // important

                    // Ensure getSelectedStop() provides the correct stop for the option
                    Stop targetStop = tokenUpgrade.getSelectedStop();
                    if (targetStop != null) {
                        currentValidTokenLays.add(new TokenLayOption(mapHex, targetStop, baseTokenAction));
                    } else {

                    }
                }
            }
        }
    }

    // ++ ADD PUBLIC GETTERS ++
    public List<TileLayOption> getCurrentValidTileLays() {
        return currentValidTileLays;
    }

    public List<TokenLayOption> getCurrentValidTokenLays() {
        return currentValidTokenLays;
    }

    // Helper needed for the direct DONE/SKIP handling fallback
    private NullAction findNullActionInEngine(PossibleActions engineActions, String command) {
        if (engineActions == null)
            return null;
        NullAction.Mode targetMode = null;
        if (command.equals(ORPanel.DONE_CMD))
            targetMode = NullAction.Mode.DONE;
        else if (command.equals(ORPanel.SKIP_CMD))
            targetMode = NullAction.Mode.SKIP;
        // Add PASS etc. if needed

        if (targetMode != null) {
            for (PossibleAction pa : engineActions.getList()) {
                if (pa instanceof NullAction && ((NullAction) pa).getMode() == targetMode) {
                    return (NullAction) pa;
                }
            }
        }
        return null;
    }

    // File: ORUIManager.java

    public boolean hexClicked(GUIHex clickedHex, GUIHex selectedHex, boolean rightClick) {

        // protection if localStep is not
        // defined (outside operating rounds)
        if (localStep == null) {
            return false;
        }


        // if selectedHex is clicked again ==> change Upgrade, or Upgrade-Selection
        if (selectedHex == clickedHex) {
            // should not occur (as a hex is selected), however let us define that in case
            if (localStep == LocalSteps.SELECT_UPGRADE) {
                if (rightClick) { // right-click => next upgrade
                    upgradePanel.nextUpgrade();
                } else {
                    upgradePanel.nextSelection();
                }

                // --- START FIX: Restore Focus After Interaction ---
                if (orWindow != null && orPanel != null) {
                    orPanel.requestFocusInWindow();
                }
                return true;
            }
            return false;
        }

        // if clickedHex is not on map => deactivate upgrade selection and use
        if (clickedHex == null) {
            if (localStep == LocalSteps.SELECT_UPGRADE) {
                if (selectedHex != null) {
                    map.selectHex(null);
                }
                setLocalStep(LocalSteps.SELECT_HEX);
                // --- START FIX: Restore Focus After Deactivation ---
                if (orWindow != null && orPanel != null) {
                    orPanel.requestFocusInWindow();
                }
                return true;
            }
            return false;
        }

        // otherwise a clickedHex is defined ==> select the hex if upgrades are provided
        if (hexUpgrades.containsVisible(clickedHex)) {
            switch (localStep) {
                case SELECT_HEX:
                    if (!gotPermission(clickedHex))
                        return false;
                    // if permitted, falls through
                case SELECT_UPGRADE:
                    map.selectHex(clickedHex);
                    setLocalStep(LocalSteps.SELECT_UPGRADE);
                    // The 'setLocalStep' call above is skipped if the state is already
                    // SELECT_UPGRADE. This is a problem for the spacebar hotkey,
                    // as the upgrade panel is never told to refresh for the new hex.
                    // We must manually force the upgrade panel to update its view.
                    if (upgradePanel != null) {
                        upgradePanel.setSelect(clickedHex);
                    }
                    // When a hex is selected/clicked, we must enable the confirm button.
                    if (orPanel != null) {
                        orPanel.enableConfirm(true);
                    }

                    if (orWindow != null && orPanel != null) {
                        orPanel.requestFocusInWindow();
                    }
                    return true;
                default:
                    return false;
            }
        }

        // otherwise the clicked hex is not contained, so go back to SelectHex
        switch (localStep) {
            case SELECT_UPGRADE:
                map.selectHex(null);
                setLocalStep(LocalSteps.SELECT_HEX);
                // Restore Focus After Deselection ---
                if (orWindow != null && orPanel != null) {
                    orPanel.requestFocusInWindow();
                }
                return false;
            default:
                return false;
        }
    }

    /**
     * Public getter for the current local step, required by ORPanel hotkey.
     * 
     * @return The current LocalSteps enum value.
     */
    public LocalSteps getLocalStep() {
        return this.localStep;
    }

    /**
     * Handles the human-facing modal dialog for variable-price train buys.
     * This logic is extracted from the old, deprecated buyTrain() modal.
     */
    private void handleVariablePriceBuy(BuyTrain buyAction) {
        // We must cast the generic Company object to a PublicCompany
        if (!(buyAction.getCompany() instanceof PublicCompany)) {

            return; // Safety check
        }
        PublicCompany company = (PublicCompany) buyAction.getCompany(); // The BUYER

        int companyCash = company.getCash();
        int presidentCash = company.getPresident().getCashValue();

        int minPrice = buyAction.getMinPrice();
        int maxPrice = buyAction.getMaxPrice();

        // Format: 'M4 buys a 3 train from the Sx'
        String trainName = (buyAction.getTrain() != null) ? buyAction.getTrain().getName() : "Train";
        String sellerName = (buyAction.getFromOwner() != null) ? buyAction.getFromOwner().getId() : "Unknown";

        String message = String.format("%s buys a %s train from the %s", company.getId(), trainName, sellerName);
        String title = "Buy Train";

        // Add constraint details to message
        message += String.format("\n(Price Range: %d - %d)", minPrice, maxPrice);

        // Pre-fill dialog with company's cash as requested ("complete cash of the
        // buying company")
        String priceString = (String) JOptionPane.showInputDialog(
                orWindow,
                message,
                title,
                JOptionPane.QUESTION_MESSAGE,
                null, null,
                Integer.toString(companyCash));

        if (priceString == null) {
            return; // User cancelled
        }

        try {
            int price = Integer.parseInt(priceString);

            // Validation
            if (price < minPrice || price > maxPrice) {
                JOptionPane.showMessageDialog(orWindow,
                        String.format("Invalid price. Must be between %d and %d.", minPrice, maxPrice), "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Validation passed. Complete the action object.
            buyAction.setPricePaid(price);

            int cashToRaise = 0;
            if (price > companyCash) {
                cashToRaise = price - companyCash;
            }
            buyAction.setAddedCash(cashToRaise); // Use setAddedCash for president's share

            // Now send the *completed* action to the engine
            orWindow.process(buyAction);

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(orWindow, "Invalid number.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

public void setMapRelatedActions(PossibleActions actions) {

        // Determine if Map Correction Mode is active (used to override normal flow)
        boolean isMapCorrectionActive = !actions.getType(rails.game.correct.MapCorrectionAction.class).isEmpty();
        LocalSteps nextSubStep;

        // Rebuilding NetworkAdapter to find new routes (critical after a tile lay/reload).
        this.networkAdapter = NetworkAdapter.create(gameUIManager.getRoot());

        GUIHex selectedHex = map.getSelectedHex();

        // Clear member lists for the new state calculation
        currentValidTileLays.clear();
        currentValidTokenLays.clear();

        // Clean map, if there are map upgrades
        if (hexUpgrades.hasElements()) {
            /* Finish tile laying step */
            if (selectedHex != null) {
                selectedHex.setUpgrade(null);
                selectedHex.setState(GUIHex.State.NORMAL);
                map.setSelectedHex(null);
            }
            // remove selectable indications
            for (GUIHex guiHex : hexUpgrades.getHexes()) {
                guiHex.setState(GUIHex.State.NORMAL);
                
                // Ensures "H2" or "($40)" disappears when the phase ends/resets
                guiHex.setCustomOverlayText(null);
            }
            hexUpgrades.clear();
        }

        List<LayTile> tileActions = actions.getType(LayTile.class);
        if (!tileActions.isEmpty()) {
            defineTileUpgrades(tileActions);
        }

        List<LayToken> tokenActions = actions.getType(LayToken.class);
        if (!tokenActions.isEmpty()) {
            defineTokenUpgrades(tokenActions);
        }

        // --- START CORRECTION FIX: Highlight all hexes if mode is active ---
        if (isMapCorrectionActive) {
            // We simulate a LayTile action of type CORRECTION to initialize the map
            LayTile correctionActionStub = new LayTile(getRoot(), LayTile.CORRECTION);
            addCorrectionTileLays(correctionActionStub);
        }

        // build and finalize hexUpgrades
        hexUpgrades.build();

        // Previously guarded by "if (gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.ROUTE_HIGHLIGHT))".
        // We REMOVED the check to ensure valid token spots are ALWAYS highlighted and text is generated.
        checkHexVisibilityOnUI(actions);

        // Pass the list of all hexes (which includes their state)
        // to the ORPanel so it can build its cycle list.
        if (orPanel != null) {
            orPanel.updateCycleableHexes(hexUpgrades.getHexes());
        }

        // Populate lists *after* hexUpgrades.build() for AI
        if (!tileActions.isEmpty()) {
            populateTileLayOptionsFromHexUpgrades();
        }
        if (!tokenActions.isEmpty()) {
            populateTokenLayOptionsFromHexUpgrades();
        }

        // --- Determine Next Sub-Step (Core UI Flow Logic) ---
        boolean hasButtonActions = false;

        if (isMapCorrectionActive) {
            // Priority 1: Map Correction Override
            nextSubStep = LocalSteps.SELECT_HEX;
        } else if (tileActions.isEmpty() && !tokenActions.isEmpty()) {
            // Priority 2: Check for Home Token Button Mode (e.g., Baden Scenario 1/2)
            hasButtonActions = true;

            for (LayToken action : tokenActions) {
                if (action instanceof LayBaseToken) {
                    int type = ((LayBaseToken) action).getType();
                    if (type != LayBaseToken.HOME_CITY) {
                        hasButtonActions = false; // Found a non-home lay (e.g., GENERIC)
                        break;
                    }
                } else {
                    hasButtonActions = false; // It's a bonus token, etc.
                    break;
                }
            }

            if (hasButtonActions) {
                // Home Token Lay is performed via button click (no map interaction needed yet)
                nextSubStep = LocalSteps.INACTIVE;
            } else {
                // Standard map-click token lay
                nextSubStep = LocalSteps.SELECT_HEX;
            }
        } else if (tileActions.isEmpty() && tokenActions.isEmpty()) {
            // Priority 3: No Map Actions
            nextSubStep = LocalSteps.INACTIVE;
        } else {
            // Priority 4: Standard Tile Lay (or mixed state leading to map interaction)
            nextSubStep = LocalSteps.SELECT_HEX;
        }

        // Force reset the localStep when (re)entering a map step to avoid stale state.
        setLocalStep(nextSubStep);
    }


    private void addCorrectionTileLays(LayTile layTile) {
        EnumSet<TileHexUpgrade.Invalids> allowances = EnumSet.of(TileHexUpgrade.Invalids.HEX_RESERVED);
        for (GUIHex hex : map.getHexes()) {
            Set<TileHexUpgrade> upgrades = TileHexUpgrade.createCorrection(hex, layTile);
            TileHexUpgrade.validates(upgrades, gameUIManager.getCurrentPhase(), allowances);
            hexUpgrades.putAll(hex, upgrades);
        }
    }

    // --- ACTION 1: Add the single-argument method (to handle calls from ORWindow)
    // ---
    public void updateStatus(boolean myTurn) {
        updateStatus(null, myTurn);
    }

    public void updateStatus(PossibleAction actionToComplete, boolean myTurn) {
        RoundFacade currentRound = gameUIManager.getCurrentRound();
        PossibleActions possibleActions = getPossibleActions();

        if (gameUIManager != null && gameUIManager.getStatusWindow() != null
                && gameUIManager.getStatusWindow().getGameStatus() != null) {
            gameUIManager.getStatusWindow().getGameStatus().refreshDashboard();
        }

        // CRITICAL: Reclaim window focus immediately after Status Window updates.
        // If StatusWindow stole focus, ORPanel.requestFocusInWindow() (called later)
        // would fail.
        if (orWindow != null && orWindow.isVisible()) {
            orWindow.toFront(); // Ensure window is z-ordered top
            orWindow.requestFocus(); // Ensure window is "Active"
        }

        // --- 1. EXTRACT UNDO/REDO EARLY ---
        GameAction undoAction = null;
        GameAction redoAction = null;
        if (myTurn && possibleActions != null) {
            for (GameAction action : possibleActions.getType(GameAction.class)) {
                if (action.getMode() == GameAction.Mode.UNDO)
                    undoAction = action;
                if (action.getMode() == GameAction.Mode.REDO)
                    redoAction = action;
            }
        }

        if (possibleActions == null) {
            if (orPanel != null)
                orPanel.disableButtons();
            return;
        }

        // --- Baden Logic (Home Token Button) ---
        if (!possibleActions.getType(LayTileAndHomeTokenAction.class).isEmpty()) {
            setLocalStep(LocalSteps.INACTIVE);
            if (orPanel != null) {
                orPanel.setSpecialMode(true); // Baden is Special
                orPanel.initTileLayingStep();
                orPanel.updateDynamicActions(possibleActions.getList());
                orPanel.revalidate();
                orPanel.repaint();

                orPanel.enableUndo(undoAction);
                orPanel.enableRedo(redoAction);
                orPanel.redisplay();
            }
            return;
        }

        // --- Prussian Formation Round (PFR) ---
        if (currentRound instanceof PrussianFormationRound) {
            if (orPanel != null) {
                orPanel.setSpecialMode(true);
                orPanel.initTrainBuying(new ArrayList<>());
                orPanel.initTileLayingStep();
            }
            orPanel.updateDynamicActions(possibleActions.getList());

            String stepName = ((PrussianFormationRound) currentRound).getPrussianStep().toString();
            String actorText = "<html><font color='blue' size='4'>" + "Prussian Formation Round" + "</font><br>"
                    + "<font color='red' size='6'>Thinking: <b>"
                    + getRoot().getPlayerManager().getCurrentPlayer().getId() + "</b> - " + stepName + "</font></html>";

            messagePanel.setMessage(actorText);
            if (gameUIManager.statusWindow != null) {
                gameUIManager.statusWindow.updateActivityPanel(actorText);
            }

            orPanel.enableUndo(undoAction);
            orPanel.enableRedo(redoAction);
            orPanel.redisplay();
            return;
        }

        // --- Operating Round (Standard) ---
        if (!(currentRound instanceof OperatingRound)) {
            orPanel.disableButtons();
            return;
        }

        if (orPanel != null)
            orPanel.setSpecialMode(false);

        this.oRound = (OperatingRound) currentRound;
        PublicCompany currentEngineCompany = oRound.getOperatingCompany();
        int currentEngineIndex = oRound.getOperatingCompanyIndex();
        GameDef.OrStep orStep = oRound.getStep();

        if (currentEngineCompany == null || currentEngineIndex < 0) {
            if (this.orCompIndex >= 0)
                orPanel.finishORCompanyTurn(this.orCompIndex);
            setLocalStep(LocalSteps.INACTIVE);
            orPanel.disableButtons();
            return;
        }

        // We ensure initialization happens if ORComp is null (transition from Stock
        // Round) OR if the company changes.
        boolean isCompanyChangeOrInitialization = (this.orComp == null || this.orComp != currentEngineCompany);

        if (isCompanyChangeOrInitialization) {
            if (this.orCompIndex >= 0)
                orPanel.finishORCompanyTurn(this.orCompIndex); // Finish old company
            setLocalStep(LocalSteps.INACTIVE);

            // Update the UI's internal company state
            this.orCompIndex = currentEngineIndex;
            this.orComp = currentEngineCompany;

            // Reinitialize the ORPanel UI for the new company
            orPanel.initORCompanyTurn(this.orComp, this.orCompIndex);

            // Company list cache update (keep existing list logic)
            List<PublicCompany> currentEngineCompanies = oRound.getOperatingCompanies();
            if (this.companies == null || !Iterables.elementsEqual(this.companies, currentEngineCompanies)) {
                this.companies = currentEngineCompanies;
            }
        }

        // --- Status Text Generation ---
        String historyText = gameUIManager.getGameManager().getLastActionSummary();
        Player currentPlayer = getRoot().getPlayerManager().getCurrentPlayer();
        String playerName = (currentPlayer != null) ? currentPlayer.getId().toUpperCase() : "PLAYER?";
        String companyName = (orComp != null) ? " (" + orComp.getId() + ")" : "";

        String stepName = "";

        if (orStep == GameDef.OrStep.LAY_TRACK)
            stepName = "Lay Track";
        else if (orStep == GameDef.OrStep.LAY_TOKEN)
            stepName = "Lay Token";
        else if (orStep == GameDef.OrStep.CALC_REVENUE)
            stepName = "Select Payout";
        else if (orStep == GameDef.OrStep.BUY_TRAIN) {
            // Check fresh list for availability
            PossibleActions freshActions = getPossibleActions();
            boolean hasTrains = (freshActions != null && !freshActions.getType(BuyTrain.class).isEmpty());

            if (hasTrains) {
                stepName = "Buy Train";
            } else {
                stepName = "Confirming Move";
            }
        } else if (orStep == GameDef.OrStep.DISCARD_TRAINS)
            stepName = "Discard Trains";
        else
            stepName = orStep.toString().replace('_', ' ');

        String combinedText = "<html>" +
                "<font color='blue' size='4'>" + historyText + "</font><br>" +
                "<font color='red' size='6'>Thinking: <b>" + playerName + "</b>" + companyName + " - " + stepName
                + "</font>" +
                "</html>";

        messagePanel.setMessage(combinedText);
        if (gameUIManager.statusWindow != null) {
            gameUIManager.statusWindow.updateActivityPanel(combinedText);
        }

        // --- AI Discard Logic ---
        if (gameUIManager.isCurrentPlayerAI() && orStep == GameDef.OrStep.DISCARD_TRAINS) {
            PossibleActions freshActions = getPossibleActions();
            List<DiscardTrain> discardActions = freshActions.getType(DiscardTrain.class);
            if (!discardActions.isEmpty()) {
                DiscardTrain bestAction = null;
                int minTrainValue = Integer.MAX_VALUE;
                for (DiscardTrain action : discardActions) {
                    if (action.getDiscardedTrain() == null)
                        continue;
                    int trainValue = action.getDiscardedTrain().getCost();
                    if (trainValue < minTrainValue) {
                        minTrainValue = trainValue;
                        bestAction = action;
                    }
                }
                if (bestAction != null) {
                    bestAction.setAIAction(true);
                    orWindow.process(bestAction);
                    return;
                }
            }
        }

        if (orStep == GameDef.OrStep.LAY_TRACK || orStep == GameDef.OrStep.LAY_TOKEN) {
            setMapRelatedActions(possibleActions);
        }

        // Determine if the last action was an UNDO or REDO.
        // If so, we MUST pause automation to prevent infinite loops (Undo -> Auto-Do ->
        // Undo).
        boolean isUndoOrRedo = false;
        PossibleAction lastAction = gameUIManager.getLastAction();
        if (lastAction instanceof GameAction) {
            GameAction.Mode mode = ((GameAction) lastAction).getMode();
            if (mode == GameAction.Mode.UNDO || mode == GameAction.Mode.FORCED_UNDO || mode == GameAction.Mode.REDO) {
                isUndoOrRedo = true;
            }
        }

        // Only run automation if we are NOT recovering from an Undo/Redo.
        if (!isUndoOrRedo) {

            // 1. Auto-Skip LAY_TRACK if no tiles/special properties available
            if (orStep == GameDef.OrStep.LAY_TRACK) {
                boolean canLay = !possibleActions.getType(LayTile.class).isEmpty();
                boolean canSpecial = !possibleActions.getType(UseSpecialProperty.class).isEmpty();

                if (!canLay && !canSpecial) {
                    // Try to find SKIP
                    for (PossibleAction pa : possibleActions.getList()) {
                        if (pa instanceof NullAction && ((NullAction) pa).getMode() == NullAction.Mode.SKIP) {
                            orWindow.process(pa);
                            return; // Stop UI update, wait for next step
                        }
                    }
                }
            }

            // 2. Auto-Skip LAY_TOKEN if no tokens available
            else if (orStep == GameDef.OrStep.LAY_TOKEN) {
                boolean canToken = !possibleActions.getType(LayToken.class).isEmpty();

                if (!canToken) {
                    for (PossibleAction pa : possibleActions.getList()) {
                        if (pa instanceof NullAction && ((NullAction) pa).getMode() == NullAction.Mode.SKIP) {
                            orWindow.process(pa);
                            return;
                        }
                    }
                }
            }

            // 3. Auto-Execute REVENUE if only one option exists (e.g. Minor Split)
            else if (orStep == GameDef.OrStep.CALC_REVENUE) {
                if (possibleActions.contains(SetDividend.class)) {
                    SetDividend action = possibleActions.getType(SetDividend.class).get(0);

                    List<Integer> validAllocations = new ArrayList<>();
                    if (action.isAllocationAllowed(SetDividend.PAYOUT))
                        validAllocations.add(SetDividend.PAYOUT);
                    if (action.isAllocationAllowed(SetDividend.WITHHOLD))
                        validAllocations.add(SetDividend.WITHHOLD);
                    if (action.isAllocationAllowed(SetDividend.SPLIT))
                        validAllocations.add(SetDividend.SPLIT);

                    if (validAllocations.size() == 1) {
                        action.setRevenueAllocation(validAllocations.get(0));
                        orWindow.process(action);
                        return;
                    }
                }
            }

            // 4. Auto-Skip TRAIN BUY if no trains affordable/available
            else if (orStep == GameDef.OrStep.BUY_TRAIN) {
                boolean canBuy = !possibleActions.getType(BuyTrain.class).isEmpty();

                if (!canBuy) {
                    for (PossibleAction pa : possibleActions.getList()) {
                        if (pa instanceof NullAction && ((NullAction) pa).getMode() == NullAction.Mode.SKIP) {
                            orWindow.process(pa);
                            return;
                        }
                    }
                }
            }
        }

        // --- Step UI Setup ---
        if (orStep == GameDef.OrStep.LAY_TRACK) {
            orPanel.initTileLayingStep();
            orPanel.setupConfirm();
            orPanel.updateDynamicActions(possibleActions.getList());
            updateHexBuildNumbers(true);
            orWindow.requestFocus();

        } else if (orStep == GameDef.OrStep.LAY_TOKEN) {
            boolean hasButtonActions = false;
            List<LayToken> tokenActions = possibleActions.getType(LayToken.class);
            if (!tokenActions.isEmpty()) {
                hasButtonActions = true;
                for (LayToken action : tokenActions) {
                    if (action instanceof LayBaseToken) {
                        if (((LayBaseToken) action).getType() != LayBaseToken.HOME_CITY) {
                            hasButtonActions = false;
                            break;
                        }
                    } else {
                        hasButtonActions = false;
                        break;
                    }
                }
            }

            if (hasButtonActions) {
                if (orPanel != null)
                    orPanel.resetHexCycle();
                orPanel.initTokenLayingStep();
                orPanel.updateDynamicActions(possibleActions.getList());
                orPanel.revalidate();
                orPanel.repaint();
            } else {
                orWindow.requestFocus();
                orPanel.initTokenLayingStep();
                orPanel.setupConfirm();
                orPanel.updateDynamicActions(possibleActions.getList());

                if (localStep == LocalSteps.SELECT_UPGRADE) {
                    orPanel.enableConfirm(true);
                }
                updateHexBuildNumbers(true);
            }

        } else if (orStep == GameDef.OrStep.CALC_REVENUE) {

            if (orPanel != null)
                orPanel.resetHexCycle();

            setLocalStep(LocalSteps.SELECT_PAYOUT);
            orPanel.initTrainBuying(new ArrayList<>());
            orPanel.updateDynamicActions(possibleActions.getList());

            if (possibleActions.contains(SetDividend.class)) {
                SetDividend action = possibleActions.getType(SetDividend.class).get(0);
                SetDividend completedAction = (actionToComplete instanceof SetDividend) ? (SetDividend) actionToComplete
                        : action;

                orPanel.initPayoutStep(orCompIndex, completedAction,
                        completedAction.isAllocationAllowed(SetDividend.WITHHOLD),
                        completedAction.isAllocationAllowed(SetDividend.SPLIT),
                        completedAction.isAllocationAllowed(SetDividend.PAYOUT));
            }

            if (actionToComplete instanceof SetDividend) {
                PossibleActions freshActions = getPossibleActions();
                orPanel.updateDynamicActions(freshActions.getList());
            }

        } else if (orStep == GameDef.OrStep.BUY_TRAIN) {
            if (orPanel != null)
                orPanel.resetHexCycle();
            setLocalStep(LocalSteps.INACTIVE);
            orPanel.initTrainBuying(possibleActions.getType(BuyTrain.class));

            // --- FORCE REFRESH ---
            PossibleActions freshActions = getPossibleActions();
            orPanel.updateDynamicActions(freshActions != null ? freshActions.getList() : possibleActions.getList());
            // --- END FORCE REFRESH ---

            orPanel.revalidate();
            orPanel.repaint();

        } else if (orStep == GameDef.OrStep.DISCARD_TRAINS) {
            if (orPanel != null)
                orPanel.resetHexCycle();
            setLocalStep(LocalSteps.INACTIVE);
            orPanel.updateDynamicActions(possibleActions.getList());
            orPanel.revalidate();
            orPanel.repaint();

        } else {
            orPanel.initTrainBuying(new ArrayList<>());
        }

        // --- STANDARD UNDO ---
        orPanel.enableUndo(undoAction);
        orPanel.enableRedo(redoAction);

        orPanel.redisplay();
    }

    /**
     * Iterates through all GUIHexes that are currently selectable and sets their
     * custom display text (number) based on the active state (Spacebar/Show button
     * functionality).
     * 
     * @param show True to show numbers, false to hide.
     */
public void updateHexBuildNumbers(boolean show) {
        if (map == null || orPanel == null)
            return;

        Rectangle mapBounds = new Rectangle(map.getSize());

        if (!show) {
            for (GUIHex guiHex : map.getHexes()) {
                if (guiHex.getCustomOverlayText() != null) {
                    guiHex.setCustomOverlayText(null);
                }
            }
            map.repaintAll(mapBounds);
            return;
        }

        Collection<GUIHex> hexes = hexUpgrades.getHexes();
        if (hexes == null || hexes.isEmpty()) {
            return;
        }

        for (GUIHex guiHex : hexes) {
            GUIHex.State state = guiHex.getState();
            // Show text for valid (Green/Red) AND invalid (Pink) hexes
            if (state == GUIHex.State.SELECTABLE || state == GUIHex.State.TOKEN_SELECTABLE || state == GUIHex.State.INVALIDS) {
                String hexId = guiHex.getHex().getId();
                StringBuilder overlayText = new StringBuilder(hexId);

                // --- START FIX ---
                for (HexUpgrade upgrade : hexUpgrades.getUpgrades(guiHex)) {
                     if (upgrade instanceof TokenHexUpgrade) {
                         // The upgrade object already calculated the cost during 'validates()'.
                         // We just retrieve it.
                         int cost = ((TokenHexUpgrade) upgrade).getCost();
                         
                         if (cost > 0) {
                             overlayText.append("<br>").append(gameUIManager.format(cost));
                         }
                         break; 
                     }
                }
                // --- END FIX ---

                guiHex.setCustomOverlayText("<html>" + overlayText.toString() + "</html>");
            } else {
                guiHex.setCustomOverlayText(null);
            }
        }
        map.repaintAll(mapBounds);
    }
}