package net.sf.rails.game.specific._1817;

import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.RailsRoot;
import net.sf.rails.game.financial.StockMarket;
import net.sf.rails.game.financial.StockSpace;
import net.sf.rails.game.state.Owner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StockMarket_1817 extends StockMarket {
    
    private static final Logger log = LoggerFactory.getLogger(StockMarket_1817.class);

    public StockMarket_1817(RailsRoot parent, String id) {
        super(parent, id);
    }

    @Override
    public void moveRight(PublicCompany company, int numberOfSpaces) {
        moveRightOrUp(company, numberOfSpaces);
    }

    @Override
    protected void moveLeft(PublicCompany company, Owner seller, int numberOfSpaces) {
        moveLeftOrDown(company, numberOfSpaces);
    }

    @Override
    public void moveRightOrUp(PublicCompany company, int numberOfMoves) {
        StockSpace oldsquare = company.getCurrentSpace();
int row = oldsquare.getRow();
        int col = oldsquare.getColumn();
        
        for (int i = 0; i < numberOfMoves; i++) {
            int nextRow = row + 1;
            // Scan for the next valid numeric price space
            while (nextRow < numRows && getStockSpace(nextRow, col) == null) {
                nextRow++;
            }
            if (nextRow < numRows && getStockSpace(nextRow, col) != null) {
                row = nextRow;
            } else {
                break; // Hit the max price ($600)
            }
        }
        
        StockSpace newsquare = getStockSpace(row, col);
        prepareMove(company, oldsquare, newsquare);
    }

    @Override
    public void moveLeftOrDown(PublicCompany company, int numberOfMoves) {
        StockSpace oldsquare = company.getCurrentSpace();
        int row = oldsquare.getRow();
        int col = oldsquare.getColumn();
        
        for (int i = 0; i < numberOfMoves; i++) {
            int nextRow = row - 1;
            // Scan for the next valid numeric price space
            while (nextRow > 0 && getStockSpace(nextRow, col) == null) {
                nextRow--;
            }
            
            StockSpace potential = getStockSpace(nextRow, col);
            // Rule 6.1: Price cannot move left further than the left most gray space[cite: 542].
            // We prevent standard moves from entering the A1 (Price 0) liquidation space.
            if (potential != null && !potential.getId().equalsIgnoreCase("A1")) {
                row = nextRow;
            } else {
                break;
            }
        }
        
        StockSpace newsquare = getStockSpace(row, col);
        prepareMove(company, oldsquare, newsquare);
    }

    
}