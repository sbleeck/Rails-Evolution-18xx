package net.sf.rails.game.specific._1870;

import net.sf.rails.game.OperatingRound;
import net.sf.rails.game.PublicCompany;

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
import rails.game.action.LayTile;
import net.sf.rails.common.DisplayBuffer;

public class OperatingRound_1870 extends OperatingRound {

    private static final Logger log = LoggerFactory.getLogger(OperatingRound_1870.class);

    public OperatingRound_1870(GameManager parent, String id) {
        super(parent, id);
        System.out.println(">>> DEBUG: OperatingRound_1870 CONSTRUCTOR CALLED!");
    }

    @Override
    public boolean checkAndGenerateDiscardActions(PublicCompany company) {
        // 1870 Rules (Page 21): Voluntary discarding to make space is prohibited.
        // Trains are only discarded automatically due to phase changes causing limit
        // drops.
        // Returning false prevents the UI from offering a manual discard action.
        return false;
    }

    @Override
    public void executeDestinationActions(List<PublicCompany> companies) {
        for (PublicCompany company : companies) {
            if (hasReachedDestination(company)) {
                applyDestinationBonus(company);
            }
        }
    }

    private boolean hasReachedDestination(PublicCompany company) {
        // TODO: Query network graph to verify valid route from Home to Destination
        return false;
    }

    private void applyDestinationBonus(PublicCompany company) {
        // TODO: Place destination station marker for free (bypass normal cost)
        // TODO: Flag the city value to be doubled for this run
    }

    @Override
    public int getTileLayCost(PublicCompany company, MapHex hex, int baseCost) {
        int cost = super.getTileLayCost(company, hex, baseCost);

        return cost;
    }

    @Override
    public boolean layTile(LayTile action) {
        Tile tile = action.getLaidTile();
        MapHex hex = action.getChosenHex();
        int orientation = action.getOrientation();

        System.out.println(">>> DEBUG: layTile intercepted in 1870. Hex=" + 
                 (hex != null ? hex.getId() : "null") + ", Tile=" + 
                 (tile != null ? tile.toText() : "null") + ", Orientation=" + orientation);

        if (tile != null && hex != null) {
            String hexId = hex.getId();
            String tileName = tile.toText();

            // 1. Memphis (A16) Exception
            if ("A16".equals(hexId)) {
                List<String> allowedMemphis = Arrays.asList("5", "6", "57", "15");
                if (!allowedMemphis.contains(tileName)) {
                    System.out.println(">>> DEBUG: Blocked by Memphis exception");
                    DisplayBuffer.add(this, "In Memphis (A16), only tiles 5, 6, 57, or 15 are permitted.");
                    return false;
                }
            }
            
            // 2. St. Louis (E18) Exception
            if ("E18".equals(hexId)) {
                if (!"5".equals(tileName)) {
                    System.out.println(">>> DEBUG: Blocked by St. Louis exception");
                    DisplayBuffer.add(this, "In St. Louis (E18), only tile 5 is permitted.");
                    return false;
                }
            }

            // 3. Mississippi River Crossing Block
            PrivateCompany bridge = getRoot().getCompanyManager().getPrivateCompany("Brdg");
            boolean bridgeBlocks = bridge != null && !bridge.isClosed() && bridge.getOwner() != action.getCompany();

            System.out.println(">>> DEBUG: Bridge blocks? (Not owned/closed): " + bridgeBlocks);
            
            if (bridgeBlocks) {
                boolean crossing = MississippiRiverValidator.isCrossingRiver(tile, hex, orientation);
                System.out.println(">>> DEBUG: isCrossingRiver returned: " + crossing);
                if (crossing) {
                    DisplayBuffer.add(this, "Cannot bridge the Mississippi River without owning the Bridge Company.");
                    return false;
                }
            }
        }
        
        System.out.println(">>> DEBUG: Passing layTile down to superclass");
        return super.layTile(action);
    }

}