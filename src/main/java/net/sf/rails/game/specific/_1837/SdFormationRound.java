package net.sf.rails.game.specific._1837;

import net.sf.rails.game.GameManager;
import net.sf.rails.game.Player;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.Round;
import net.sf.rails.game.state.Currency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rails.game.action.LayTile;

public class SdFormationRound extends Round {
    private static final Logger log = LoggerFactory.getLogger(SdFormationRound.class);

    public SdFormationRound(GameManager parent, String id) {
        super(parent, id);
    }

    public void start(PublicCompany_1837 sd) {
        log.info("1837_LOGIC: Commencing Mandatory Sd Formation.");

        // 1. Establish Par Space (142)
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
            sd.setCurrentSpace(parSpace);
            
            // 2. Strict Rule Check: Does a player own S1?
            PublicCompany s1 = getRoot().getCompanyManager().getPublicCompany("S1");
            boolean s1OwnedByPlayer = (s1 != null && s1.getPresident() instanceof Player);

            if (s1OwnedByPlayer) {
                log.info("1837_LOGIC: S1 is player-owned. Sd floats and receives 710K capital.");
                Currency.fromBank(710, sd);
                sd.setFloated();
            } else {
                log.warn("1837_LOGIC: S1 is unowned. Minors will close, but Sd does NOT float.");
            }

            // 3. Mandatory Absorb of S1-S5
            for (PublicCompany minor : gameManager.getAllPublicCompanies()) {
                String id = minor.getId();
                if (id.length() == 2 && id.startsWith("S") && id.charAt(1) >= '1' && id.charAt(1) <= '5') {
                    if (!minor.isClosed()) {
                        Merger1837.mergeMinor(gameManager, minor, sd);
                    }
                }
            }

            sd.setOperated();
            if (sd.hasFloated()) {
                Merger1837.fixDirectorship(gameManager, sd);
            }
        }

        // 4. Handle Bozen tile exception
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
            net.sf.rails.game.Tile tile = getRoot().getTileManager().getTile(GameDef_1837.newBozenTile);
            if (hex != null && tile != null) {
                action.setChosenHex(hex);
                action.setLaidTile(tile);
                action.setOrientation(GameDef_1837.newBozenTileOrientation);
                hex.upgrade(action);
            }
        } catch (Exception e) {
            log.error("Error laying Bozen tile: " + e.getMessage());
        }

        // Round executes instantaneously without UI halting
        finishRound();
    }
    
    @Override
    public boolean setPossibleActions() {
        return false;
    }
}