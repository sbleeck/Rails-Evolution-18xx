package net.sf.rails.game.specific._1817;

import net.sf.rails.common.ReportBuffer;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.RailsRoot;
import net.sf.rails.game.StartRound;
import net.sf.rails.game.financial.StockMarket;
import net.sf.rails.game.financial.StockSpace;
import net.sf.rails.game.state.Owner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StockMarket_1817 extends StockMarket {
    
    private static final Logger log = LoggerFactory.getLogger(StartRound.class);

    public StockMarket_1817(RailsRoot parent, String id) {
        super(parent, id);
    }

    @Override
    public void moveRight(PublicCompany company, int numberOfSpaces) {
        StockSpace oldsquare = company.getCurrentSpace();
        log.debug("1817 Market: Attempting moveRight for " + company.getId() + ". Current space: " + oldsquare.getId() + " by " + numberOfSpaces + " spaces.");
        
        int row = oldsquare.getRow();
        int col = oldsquare.getColumn();
        
        for (int i = 0; i < numberOfSpaces; i++) {
            if (col < numCols - 1 && (getStockSpace(row, col + 1)) != null) {
                col++;
            }
        }
        
        StockSpace newsquare = getStockSpace(row, col);
        prepareMove(company, oldsquare, newsquare);
        log.debug("1817 Market: moveRight complete. New space: " + newsquare.getId());
    }

    @Override
    protected void moveLeft(PublicCompany company, Owner seller, int numberOfSpaces) {
        StockSpace oldsquare = company.getCurrentSpace();
        log.debug("1817 Market: Attempting moveLeft for " + company.getId() + ". Current space: " + oldsquare.getId() + " by " + numberOfSpaces + " spaces.");
        
        int row = oldsquare.getRow();
        int col = oldsquare.getColumn();
        
        for (int i = 0; i < numberOfSpaces; i++) {
            // Check if moving left keeps us within bounds and does NOT enter the A2 null space
            // In 1817, normal leftward movement stops at the left-most acquisition space (A3).
            StockSpace potentialNextSpace = getStockSpace(row, col - 1);
            if (col > 0 && potentialNextSpace != null && !potentialNextSpace.getId().equals("A1")) {
                col--;
            } else {
                log.debug("1817 Market: Leftward movement halted. Reached acquisition boundary or null space.");
                break;
            }
        }
        
        StockSpace newsquare = getStockSpace(row, col);
        prepareMove(company, oldsquare, newsquare);
        log.debug("1817 Market: moveLeft complete. New space: " + newsquare.getId());
    }
}