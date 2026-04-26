package net.sf.rails.game.specific._1870;

import net.sf.rails.game.OperatingRound;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.Round;

import java.util.List;
import net.sf.rails.game.GameManager;
import net.sf.rails.game.MapHex;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.sf.rails.game.PrivateCompany;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.sf.rails.game.Tile;
import net.sf.rails.game.TrackPoint;
import net.sf.rails.game.state.Owner;
import rails.game.action.BuyPrivate;
import rails.game.action.LayTile;
import net.sf.rails.common.DisplayBuffer;
import net.sf.rails.common.GameOption;
import net.sf.rails.game.Track;
import net.sf.rails.game.Phase;

public class OperatingRound_1870 extends OperatingRound {

    private static final Logger log = LoggerFactory.getLogger(OperatingRound_1870.class);

    public static final List<String> GULF_PORT_HEXES = Arrays.asList("H17", "K16", "M14", "M20", "N7");

    public OperatingRound_1870(GameManager parent, String id) {
        super(parent, id);
    }

    private final net.sf.rails.game.state.BooleanState homeTileLaidThisTurn = new net.sf.rails.game.state.BooleanState(
            this, "homeTileLaidThisTurn", false);
    private final net.sf.rails.game.state.BooleanState bridgeBonusUsedThisTurn = new net.sf.rails.game.state.BooleanState(
            this, "bridgeBonusUsedThisTurn", false);

    @Override
    protected void initTurn() {
        super.initTurn();
        homeTileLaidThisTurn.set(false);
        bridgeBonusUsedThisTurn.set(false);
    }

    @Override
    public boolean layTile(LayTile action) {

        Tile tile = action.getLaidTile();
        MapHex hex = action.getChosenHex();
        int orientation = action.getOrientation();
        PublicCompany opCompany = action.getCompany();

        if (tile != null && hex != null) {
            String hexId = hex.getId();
            String tileName = tile.toText();
            boolean isSuspectedIllegal = false;
            String reason = "";

            // Move the bridge check to the top so we can use it for Memphis and St. Louis
            PrivateCompany bridge = getRoot().getCompanyManager().getPrivateCompany("Brdg");
            Owner bridgeOwner = bridge != null ? bridge.getOwner() : null;
            boolean bridgeBlocks = bridge != null && !bridge.isClosed()
                    && (bridgeOwner instanceof net.sf.rails.game.Player);

            // 1. Check Memphis (K16) Tile and Side restrictions
            if ("K16".equals(hexId) && bridgeBlocks) {
                List<String> allowedMemphis = Arrays.asList("5", "6", "57", "15");
                if (!allowedMemphis.contains(tileName)) {
                    isSuspectedIllegal = true;
                    reason = "violates the Memphis (K16) restriction. Only tiles 5, 6, 57, or 15 are allowed before the bridge is sold.";
                }

                for (Track track : tile.getTracks()) {
                    for (TrackPoint tp : Arrays.asList(track.getStart(), track.getEnd())) {
                        if (tp instanceof net.sf.rails.game.HexSide) {
                            int absSide = (tp.getTrackPointNumber() + orientation) % 6;
                            if (absSide == 1 || absSide == 2) {
                                isSuspectedIllegal = true;
                                reason = "points W/NW from Memphis, which crosses the Mississippi barrier.";
                            }
                        }
                    }
                }
            }

            // 2. Check St. Louis (C18) Tile and Side restrictions
            if ("C18".equals(hexId) && bridgeBlocks) {
                if (!"5".equals(tileName)) {
                    isSuspectedIllegal = true;
                    reason = "violates the St. Louis (C18) restriction. Only tile 5 is allowed before the bridge is sold.";
                }

                for (Track track : tile.getTracks()) {
                    for (TrackPoint tp : Arrays.asList(track.getStart(), track.getEnd())) {
                        if (tp instanceof net.sf.rails.game.HexSide) {
                            int absSide = (tp.getTrackPointNumber() + orientation) % 6;
                            if (absSide != 0 && absSide != 1) {
                                isSuspectedIllegal = true;
                                reason = "does not face W/SW from St. Louis, which crosses the Mississippi barrier.";
                            }
                        }
                    }
                }
            }

            // 3. Check General River Crossing via Validator
            if (bridgeBlocks && !isSuspectedIllegal) {
                if (MississippiRiverValidator.isCrossingRiver(tile, hex, orientation)) {
                    isSuspectedIllegal = true;
                    reason = "This tile appears to cross the Mississippi river barrier.";
                }
            }

            // 4. Trigger Dialog if suspected illegal (and not replaying)
            if (isSuspectedIllegal && !getRoot().getGameManager().isReloading()) {

                int choice = javax.swing.JOptionPane.showOptionDialog(null,
                        "I believe that this tile is illegal, because it "
                                + (reason.isEmpty() ? "crosses the Mississippi" : reason),
                        "1870 Rule Validation",
                        javax.swing.JOptionPane.YES_NO_OPTION,
                        javax.swing.JOptionPane.WARNING_MESSAGE,
                        null,
                        new String[] { "overrule me!", "OK, delete" },
                        "OK, delete");

                if (choice == 1 || choice == javax.swing.JOptionPane.CLOSED_OPTION) {
                    return false;
                }
                DisplayBuffer.add(this, opCompany.getId() + " overruled river validation on " + hexId);
            }
        }

        boolean success = super.layTile(action);

        if (success && tile != null && hex != null && opCompany != null) {
            boolean isStartingCity = false;
            if (hex.getId().equals("C18") && opCompany.getId().equals("MP"))
                isStartingCity = true;
            if (hex.getId().equals("K16") && opCompany.getId().equals("SSW"))
                isStartingCity = true;

            if (isStartingCity) {
                homeTileLaidThisTurn.set(true);
                PrivateCompany bridge = getRoot().getCompanyManager().getPrivateCompany("Brdg");
                boolean ownsBridge = bridge != null && bridge.getOwner() == opCompany;

                if (!opCompany.hasOperated() && ownsBridge && !bridgeBonusUsedThisTurn.value()) {
                    bridgeBonusUsedThisTurn.set(true);
                    if (tileLaysPerColour.containsKey("yellow")) {
                        tileLaysPerColour.put("yellow", tileLaysPerColour.get("yellow") + 1);
                        log.info("Bridge Bonus: extra tile lay applied on starting city.");
                    }
                }
            }
        }


        return success;

    }

    @Override
    public boolean buyTrain(rails.game.action.BuyTrain action) {
        boolean success = super.buyTrain(action);
        
        return success;
    }

    @Override
    protected void initNormalTileLays() {
        // 1. Let the engine load the defaults from XML (usually 1 per available color)
        super.initNormalTileLays();

        // 2. 1870 Rules: A company may lay up to TWO yellow tiles per turn,
        // OR upgrade exactly ONE tile. We overwrite the yellow limit to 2.
        if (tileLaysPerColour.containsKey("yellow")) {
            tileLaysPerColour.put("yellow", 2);
        }
    }

    /**
     * 1870 Rule: MP and SSW can purchase the Bridge Company during Phase 1.
     * Includes a check for the optional 'RestrictPrivateTradingToSameOwner'
     * constraint.
     */
    @Override
    public boolean setPossibleActions() {
        boolean result = super.setPossibleActions();

        Phase phase = Phase.getCurrent(this);
        if (phase != null && "1".equals(phase.toText())) {
            PublicCompany company = getOperatingCompany();

            if (company != null && (company.getId().equals("MP") || company.getId().equals("SSW"))) {
                PrivateCompany bridge = getRoot().getCompanyManager().getPrivateCompany("Brdg");

                if (bridge != null && !bridge.isClosed() && bridge.getOwner() instanceof net.sf.rails.game.Player) {

                    boolean allowPurchase = (bridge.getOwner() == company.getPresident());

                    if (allowPurchase) {
                        // Phase 1 price is restricted to $20 - $40.
                        possibleActions.add(new BuyPrivate(bridge, 20, 40));
                    }
                }
            }
        }

        // 1870 Rule: Cattle Company token placement
        PublicCompany comp = getOperatingCompany();
        if (comp != null) {
            PrivateCompany cattle = getAvailableCattleCompany(comp);
            if (cattle != null) {
                // Add a single action with a null hex. The dialog will open when clicked.
                possibleActions.add(
                        new net.sf.rails.game.specific._1870.action.LayCattleToken_1870(getRoot(), comp.getId(), null));

            }
            // 1870 Rule: Gulf Shipping Company token placement
            PrivateCompany gulf = getOwnedGulfCompany(comp);
            if (gulf != null) {
                net.sf.rails.game.BonusToken gulfToken = getGulfTokenOnMap();
                if (gulfToken == null) {
                    possibleActions.add(new net.sf.rails.game.specific._1870.action.LayGulfToken_1870(getRoot(),
                            comp.getId(), null, true));
                    possibleActions.add(new net.sf.rails.game.specific._1870.action.LayGulfToken_1870(getRoot(),
                            comp.getId(), null, false));
                } else {
                    Phase phaseObj = Phase.getCurrent(this);
                    if (phaseObj != null && ("1".equals(phaseObj.toText()) || "2".equals(phaseObj.toText())
                            || "3".equals(phaseObj.toText()))) {
                        possibleActions.add(new net.sf.rails.game.specific._1870.action.FlipGulfToken_1870(getRoot(),
                                comp.getId()));
                    }
                }
            }
        }

        return result;
    }

    /**
     * 1870 Rule: Owner of Bridge Company gets a $40 discount on river hexes.
     *
     */
    @Override
    public int getTileLayCost(PublicCompany company, MapHex hex, int standardCost) {
        int cost = standardCost;
        PrivateCompany bridge = getRoot().getCompanyManager().getPrivateCompany("Brdg");

        if (bridge != null && !bridge.isClosed() && bridge.getOwner() == company) {
            if (MississippiRiverValidator.isRiverHex(hex)) {
                log.info("Bridge Discount: $40 applied to hex {}", hex.getId());
                cost = Math.max(0, cost - 40);
            }
        }
        return cost;
    }

    /**
     * Handles the specific bonuses granted by the Bridge Company upon purchase.
     */
    @Override
    public boolean buyPrivate(BuyPrivate action) {
        PublicCompany company = (PublicCompany) action.getCompany();
        PrivateCompany privateComp = action.getPrivateCompany();

        boolean result = super.buyPrivate(action);

        if (result && "Brdg".equals(privateComp.getId())) {
            // Rule: Extra tile lay at starting city for no cost if bought in company's 1st
            // OR.

            if (!company.hasOperated()) {
                if (homeTileLaidThisTurn.value() && !bridgeBonusUsedThisTurn.value()) {
                    bridgeBonusUsedThisTurn.set(true);
                    if (tileLaysPerColour.containsKey("yellow")) {
                        tileLaysPerColour.put("yellow", tileLaysPerColour.get("yellow") + 1);
                        log.info("Bridge Bonus: retroactive extra tile lay applied.");
                    }
                }
            }
        }
        return result;
    }

    @Override
    public boolean processGameSpecificAction(rails.game.action.PossibleAction action) {
        if (action instanceof net.sf.rails.game.specific._1870.action.LayCattleToken_1870) {
            net.sf.rails.game.specific._1870.action.LayCattleToken_1870 cattleAction = (net.sf.rails.game.specific._1870.action.LayCattleToken_1870) action;
            PublicCompany comp = getRoot().getCompanyManager().getPublicCompany(cattleAction.getCompanyId());

            if (comp != null) {
                String hexId = cattleAction.getHexId();

                if (hexId == null || hexId.trim().isEmpty()) {
                    if (getRoot().getGameManager().isReloading())
                        return false;

                    java.util.List<String> options = new java.util.ArrayList<>();
                    for (MapHex h : getRoot().getMapManager().getHexes()) {
                        // Rules check: Hex must have a city/stop and cannot contain a portion of the
                        // Mississippi.
                        if (h.getStops() != null && !h.getStops().isEmpty()
                                && !MississippiRiverValidator.isRiverHex(h)) {
                            options.add(h.getId());
                        }
                    }
                    java.util.Collections.sort(options);

                    if (options.isEmpty()) {
                        net.sf.rails.common.DisplayBuffer.add(this,
                                "No valid city hexes available for the Cattle token.");
                        return false;
                    }

                    String chosen = (String) javax.swing.JOptionPane.showInputDialog(
                            null,
                            "Select a city hex west of the Mississippi for the Cattle Token:",
                            "Place Cattle Token",
                            javax.swing.JOptionPane.QUESTION_MESSAGE,
                            null,
                            options.toArray(),
                            options.get(0));

                    if (chosen == null || chosen.isEmpty())
                        return false;

                    cattleAction.setHexId(chosen);
                    hexId = chosen;
                }

                MapHex hex = getRoot().getMapManager().getHex(hexId);
                if (hex != null) {
                    PrivateCompany cattlePriv = getAvailableCattleCompany(comp);
                    if (cattlePriv != null) {
                        net.sf.rails.game.BonusToken cattleToken = net.sf.rails.game.BonusToken.create(cattlePriv);
                        if (cattleToken != null) {
                            cattleToken.setName("Cattle");
                            cattleToken.setValue(10);
                            hex.layBonusToken(cattleToken, getRoot().getPhaseManager());
                            net.sf.rails.common.ReportBuffer.add(this,
                                    comp.getId() + " places the Southern Cattle Company token on " + hex.getId() + ".");
                        }
                    }
                    return true;
                }
            }
        }
        if (action instanceof net.sf.rails.game.specific._1870.action.LayGulfToken_1870) {
            net.sf.rails.game.specific._1870.action.LayGulfToken_1870 gulfAction = (net.sf.rails.game.specific._1870.action.LayGulfToken_1870) action;
            PublicCompany comp = getRoot().getCompanyManager().getPublicCompany(gulfAction.getCompanyId());

            if (comp != null) {
                String hexId = gulfAction.getHexId();

                if (hexId == null || hexId.isEmpty()) {
                    if (getRoot().getGameManager().isReloading())
                        return false;

                    java.util.List<String> options = new java.util.ArrayList<>();
                    for (String hId : GULF_PORT_HEXES) {
                        MapHex h = getRoot().getMapManager().getHex(hId);
                        if (h != null && h.getStops() != null && !h.getStops().isEmpty()) {
                            if (h.getCurrentTile() != null &&
                                    !h.getCurrentTile().getColourText().equalsIgnoreCase("white") &&
                                    !h.getCurrentTile().getColourText().equalsIgnoreCase("none")) {
                                options.add(h.getId());
                            }
                        }
                    }
                    java.util.Collections.sort(options);

                    if (options.isEmpty()) {
                        net.sf.rails.common.DisplayBuffer.add(this, "No valid upgraded port/river hexes available.");
                        return false;
                    }

                    String stateText = gulfAction.isOpen() ? "Open Port" : "Closed Port";
                    String chosen = (String) javax.swing.JOptionPane.showInputDialog(
                            null,
                            "Select an upgraded city to place the " + stateText + ":",
                            "Place Gulf Token",
                            javax.swing.JOptionPane.QUESTION_MESSAGE,
                            null,
                            options.toArray(),
                            options.get(0));

                    if (chosen == null || chosen.isEmpty())
                        return false;

                    gulfAction.setHexId(chosen);
                    hexId = chosen;
                }

                MapHex hex = getRoot().getMapManager().getHex(hexId);
                if (hex != null) {
                    PrivateCompany gulfPriv = getOwnedGulfCompany(comp);
                    if (gulfPriv != null) {
                        net.sf.rails.game.BonusToken gulfToken = net.sf.rails.game.BonusToken.create(gulfPriv);
                        if (gulfToken != null) {
                            String tokenName = gulfAction.isOpen() ? "Gulf_Open" : "Gulf_Closed";
                            gulfToken.setName(tokenName);
                            gulfToken.setValue(20);
                            hex.layBonusToken(gulfToken, getRoot().getPhaseManager());
                            net.sf.rails.common.ReportBuffer.add(this, comp.getId()
                                    + " places the Gulf Shipping token (" + tokenName + ") on " + hex.getId() + ".");
                        }
                    }
                    return true;
                }
            }
        }

        if (action instanceof net.sf.rails.game.specific._1870.action.FlipGulfToken_1870) {
            net.sf.rails.game.specific._1870.action.FlipGulfToken_1870 flipAction = (net.sf.rails.game.specific._1870.action.FlipGulfToken_1870) action;
            PublicCompany comp = getRoot().getCompanyManager().getPublicCompany(flipAction.getCompanyId());
            if (comp != null) {
                net.sf.rails.game.BonusToken existingToken = getGulfTokenOnMap();
                if (existingToken != null) {
                    String newName = "Gulf_Open".equals(existingToken.getName()) ? "Gulf_Closed" : "Gulf_Open";
                    existingToken.setName(newName);
                    net.sf.rails.common.ReportBuffer.add(this,
                            comp.getId() + " flips the Gulf Shipping token to " + newName + ".");
                    return true;
                }
            }
        }

        return super.processGameSpecificAction(action);
    }

    private PrivateCompany getAvailableCattleCompany(PublicCompany comp) {
        if (comp == null || comp.getPortfolioModel() == null)
            return null;
        for (PrivateCompany priv : comp.getPortfolioModel().getPrivateCompanies()) {
            if (priv != null) {
                String id = priv.getId();
                String name = priv.getName() != null ? priv.getName() : "";
                if ("SCC".equals(id) || "Cattle".equals(id) || name.contains("Cattle") || name.contains("SCC")) {
                    int placedCount = 0;
                    for (MapHex hex : getRoot().getMapManager().getHexes()) {
                        if (hex.getBonusTokens() != null) {
                            for (net.sf.rails.game.BonusToken t : hex.getBonusTokens()) {
                                if ("Cattle".equals(t.getName()) && t.getParent() != null
                                        && id.equals(t.getParent().getId())) {
                                    placedCount++;
                                }
                            }
                        }
                    }
                    if (placedCount < 1) {
                        return priv;
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected boolean isPrivateSellingAllowed() {
        // 1870 Rule: Private companies can be sold starting in Phase 2.
        Phase phase = Phase.getCurrent(this);
        // 1870 Rule: Private companies can be sold starting in Phase 2.
        if (phase != null && !"1".equals(phase.toText())) {
            return true;
        }

        if (super.isPrivateSellingAllowed()) {
            return true;
        }

        // Phase 1 Bridge exception
        if (selectedAction instanceof BuyPrivate) {
            BuyPrivate action = (BuyPrivate) selectedAction;
            PublicCompany company = action.getCompany();
            PrivateCompany privateComp = action.getPrivateCompany();

            if (phase != null && "1".equals(phase.toText()) && privateComp != null
                    && "Brdg".equals(privateComp.getId())) {
                if (company != null && (company.getId().equals("MP") || company.getId().equals("SSW"))) {
                    return privateComp.getOwner() == company.getPresident();
                }
            }
        }
        return false;
    }

    @Override
    protected boolean maySellPrivate(net.sf.rails.game.Player player) {
        // In 1870, a public company can only buy privates from its own president.
        PublicCompany company = getOperatingCompany();
        return company != null && player == company.getPresident();
    }

    private PrivateCompany getOwnedGulfCompany(PublicCompany comp) {
        if (comp == null || comp.getPortfolioModel() == null)
            return null;
        for (PrivateCompany priv : comp.getPortfolioModel().getPrivateCompanies()) {
            if (priv != null) {
                String id = priv.getId();
                String name = priv.getName() != null ? priv.getName() : "";
                if ("Gulf".equals(id) || name.contains("Gulf")) {
                    return priv;
                }
            }
        }
        return null;
    }

    private net.sf.rails.game.BonusToken getGulfTokenOnMap() {
        for (MapHex hex : getRoot().getMapManager().getHexes()) {
            if (hex.getBonusTokens() != null) {
                for (net.sf.rails.game.BonusToken t : hex.getBonusTokens()) {
                    if ("Gulf_Open".equals(t.getName()) || "Gulf_Closed".equals(t.getName())) {
                        return t;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public boolean checkAndGenerateDiscardActions(PublicCompany company) {
        // 1870 Rules (Page 21): Voluntary discarding to make space is prohibited.
        // Trains are only discarded automatically due to phase changes causing limit
        // drops.
        // Returning false prevents the UI from offering a manual discard action.
        return false;
    }

    private void applyDestinationBonus(PublicCompany company) {
        company.setReachedDestination(true);
        MapHex destHex = company.getDestinationHex();

        // 1. Check if the company already has a normal station marker in the
        // destination hex
        boolean hasTokenAlready = false;
        if (destHex.getStops() != null) {
            for (net.sf.rails.game.Stop stop : destHex.getStops()) {
                if (stop.getTokens() != null) {
                    for (net.sf.rails.game.BaseToken t : stop.getTokens()) {
                        if (t.getOwner() == company) {
                            hasTokenAlready = true;
                            break;
                        }
                    }
                }
            }
        }

        String message = "Congratulations! " + company.getId() + " has reached its destination: " + destHex.getId()
                + "!\n\n";

        if (hasTokenAlready) {
            // Rules: If already tokened, it automatically becomes an extra $100 station
            // marker
            message += "Since you already have a station marker here, your destination marker becomes an additional $100 station marker.";
            net.sf.rails.common.ReportBuffer.add(this,
                    company.getId() + " reaches destination. Marker becomes an extra $100 token.");

            if (!getRoot().getGameManager().isReloading()) {
                javax.swing.JOptionPane.showMessageDialog(null, message, "Connection Run Achieved",
                        javax.swing.JOptionPane.INFORMATION_MESSAGE);
            }
            addExtraStationMarker(company);
        } else {
            // Rules: Player choice to lay for free or keep as extra $100 marker
            message += "Would you like to place your destination marker on " + destHex.getId() + " for free now?\n" +
                    "(If 'No', it is kept on your charter as an extra $100 station marker for later use).";

            int choice = javax.swing.JOptionPane.YES_OPTION;
            if (!getRoot().getGameManager().isReloading()) {
                choice = javax.swing.JOptionPane.showConfirmDialog(null, message, "Destination Marker Placement",
                        javax.swing.JOptionPane.YES_NO_OPTION);
            }

            if (choice == javax.swing.JOptionPane.YES_OPTION) {
                // Place as a BonusToken to sit on the edge and bypass city slot limits
                net.sf.rails.game.BonusToken destToken = net.sf.rails.game.BonusToken.create(company);
                destToken.setName(company.getId() + "_Dest");
                destToken.setValue(0); // The doubling effect will be handled by the revenue calculator
                destHex.layBonusToken(destToken, getRoot().getPhaseManager());

                net.sf.rails.common.ReportBuffer.add(this,
                        company.getId() + " places its destination marker for free on " + destHex.getId() + ".");
            } else {
                net.sf.rails.common.ReportBuffer.add(this,
                        company.getId() + " chooses to keep its destination marker as an extra $100 token.");
                addExtraStationMarker(company);
            }
        }
    }

    private void addExtraStationMarker(PublicCompany company) {
        // Create a new token for the company
        net.sf.rails.game.BaseToken newToken = net.sf.rails.game.BaseToken.create(company);

        // Add the token to the existing model; 'false' ensures it is added to
        // the free (unlaid) pool on the company charter
        company.getBaseTokensModel().addBaseToken(newToken, false);
    }

    @Override
    public void executeDestinationActions(List<PublicCompany> companies) {
//NOT triggered here
    }

 
    @Override
    protected boolean finishTurnSpecials() {
        checkConnections();
        return super.finishTurnSpecials();
    }

    private void checkConnections() {
        PublicCompany currentCompany = getOperatingCompany();
        java.util.List<PublicCompany> eligibleCompanies = new java.util.ArrayList<>();

        for (PublicCompany comp : getRoot().getCompanyManager().getAllPublicCompanies()) {
            if (comp.isClosed() || comp.getPortfolioModel() == null) {
                continue;
            }
            if (hasReachedDestination(comp)) {
                applyDestinationBonus(comp);
                eligibleCompanies.add(comp);
            }
        }

        if (eligibleCompanies.isEmpty()) return;

        // Sort by share price descending
        eligibleCompanies.sort((c1, c2) -> {
            int price1 = c1.getCurrentSpace() != null ? c1.getCurrentSpace().getPrice() : 0;
            int price2 = c2.getCurrentSpace() != null ? c2.getCurrentSpace().getPrice() : 0;
            return Integer.compare(price2, price1);
        });

        // Separate the current company because it must run first (top of the stack)
        boolean currentEligible = eligibleCompanies.remove(currentCompany);

        if (gameManager instanceof GameManager_1870) {
            // Push to stack in reverse order so the highest share price is near the top
            for (int i = eligibleCompanies.size() - 1; i >= 0; i--) {
                ((GameManager_1870) gameManager).startConnectionRunRound(this, eligibleCompanies.get(i));
            }
            // Finally, push the current company so it is on the absolute top of the stack
            if (currentEligible) {
                ((GameManager_1870) gameManager).startConnectionRunRound(this, currentCompany);
            }
        }
    }

    
    private boolean hasReachedDestination(PublicCompany company) {

        // Re-enable the guard to prevent multiple triggers
        if (company.hasReachedDestination()) {
            return false;
        }

        MapHex destHex = company.getDestinationHex();
        if (destHex == null) {
            return false;
        }

        MapHex homeHex = company.getHomeHexes().isEmpty() ? null : company.getHomeHexes().get(0);
        if (homeHex == null) {
            return false;
        }

        int maxCities = 0;
        for (net.sf.rails.game.Train t : company.getTrains()) {
            net.sf.rails.algorithms.NetworkTrain nt = net.sf.rails.algorithms.NetworkTrain.createFromRailsTrain(t);
            if (nt != null) {
                int len = Math.max(nt.getMajors(), nt.getMinors());
                if (len > maxCities)
                    maxCities = len;
            }
        }

        if (maxCities == 0) {
            return false;
        }

        net.sf.rails.algorithms.NetworkAdapter networkAdapter = net.sf.rails.algorithms.NetworkAdapter
                .create(getRoot());
        net.sf.rails.algorithms.NetworkGraph graph = networkAdapter.getRouteGraphCached(company, false);

        if (graph == null) {
            return false;
        }

        java.util.Set<net.sf.rails.algorithms.NetworkVertex> validStarts = new java.util.HashSet<>();

        // STRICTLY ONLY START FROM THE HOME STATION
        for (net.sf.rails.algorithms.NetworkVertex v : graph.getCompanyBaseTokenVertexes(company)) {
            if (v.getHex().equals(homeHex)) {
                validStarts.add(v);
            }
        }

        if (validStarts.isEmpty()) {
            return false;
        }

        org.jgrapht.graph.SimpleGraph<net.sf.rails.algorithms.NetworkVertex, net.sf.rails.algorithms.NetworkEdge> jGraph = graph
                .getGraph();

        for (net.sf.rails.algorithms.NetworkVertex startNode : validStarts) {
            // Pass the starting path string to trace the route
            if (dfsFindDestination(jGraph, startNode, destHex, maxCities, new java.util.HashSet<>(),
                    new java.util.HashSet<>(), 1, homeHex.getId() + "[C:1]")) {
                return true;
            }
        }

        return false;
    }

    private boolean dfsFindDestination(
            org.jgrapht.graph.SimpleGraph<net.sf.rails.algorithms.NetworkVertex, net.sf.rails.algorithms.NetworkEdge> graph,
            net.sf.rails.algorithms.NetworkVertex current, MapHex destination, int maxCities,
            java.util.Set<net.sf.rails.algorithms.NetworkVertex> visitedVertices,
            java.util.Set<net.sf.rails.algorithms.NetworkEdge> visitedEdges,
            int cityCount, String pathLog) {

        // Only consider the destination reached if we hit the actual city/town/offboard
        // node inside the hex
        if (current.getHex() != null && current.getHex().equals(destination)) {
            if (current.isMajor() || current.isMinor() || current.isStation()) {
                log.info(">>> DEBUG: FINAL PATH: {}", pathLog);
                return true;
            }
        }

        if (current.isStation() && current.isSink() && !visitedVertices.isEmpty()) {
            return false; // Blocked by another company's token
        }

        visitedVertices.add(current);

        for (net.sf.rails.algorithms.NetworkEdge edge : graph.edgesOf(current)) {
            if (visitedEdges.contains(edge))
                continue;

            net.sf.rails.algorithms.NetworkVertex neighbor = org.jgrapht.Graphs.getOppositeVertex(graph, edge, current);
            if (visitedVertices.contains(neighbor))
                continue;

            int newCityCount = cityCount;
            String appendLog = neighbor.getHex() != null ? neighbor.getHex().getId() : "?";

            // Count cities and towns accurately
            if (neighbor.isMajor() || neighbor.isMinor()) {
                newCityCount++;
                appendLog += "[C:" + newCityCount + "]";
            }

            if (newCityCount <= maxCities) {
                visitedEdges.add(edge);
                if (dfsFindDestination(graph, neighbor, destination, maxCities, visitedVertices, visitedEdges,
                        newCityCount, pathLog + " -> " + appendLog)) {
                    return true;
                }
                visitedEdges.remove(edge);
            }
        }

        visitedVertices.remove(current);
        return false;
    }


}