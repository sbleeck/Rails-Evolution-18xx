package net.sf.rails.game.specific._1837;

import com.google.common.collect.Lists;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.RailsRoot;
import net.sf.rails.game.financial.StockMarket;
import net.sf.rails.game.financial.StockSpace;
import net.sf.rails.game.state.HashMapState;
import net.sf.rails.game.state.Owner;

import java.util.List;

public class StockMarket_1837 extends StockMarket {

    private HashMapState<StockSpace, Integer> parSpaceUsers
            = HashMapState.create(this, "parSpaceUsers");

    public StockMarket_1837(RailsRoot parent, String id) {
        super(parent, id);
        this.stockChartType = ChartType.HEXAGONAL;
    }

    public List<Integer> getStartPrices() {
        List<Integer> prices = Lists.newArrayList();
        for (StockSpace space : startSpaces) {
            if (canAddParSpaceUser(space)) {
                prices.add(space.getPrice());
            }
        }
        return prices;
    }

    @Override
    public StockSpace getStartSpace(int price) {
        StockSpace space = super.getStartSpace(price);
        return canAddParSpaceUser(space) ? space : null;
    }

    public void addParSpaceUser(StockSpace space) {
        Integer count = parSpaceUsers.get(space);
        parSpaceUsers.put(space, (count == null) ? 1 : count + 1);
    }

    public boolean canAddParSpaceUser(StockSpace space) {
        Integer count = parSpaceUsers.get(space);
        return count == null || count < 2;
    }

    public void payOut(PublicCompany company, boolean split) {
        if (!split) {
            // Rule 10.7.4: 100% payout moves Right or Up-Right [cite: 726, 727]
            moveRightOrUp(company);
        } else {
            // Rule 10.7.4: 50% payout moves Down-Right [cite: 730]
            moveDownRight(company);
        }
    }

    @Override
    public int spacesDownOnSale(int sharesSold, Owner seller) {
        // Rule 9.1: Every sale moves the price marker diagonally down and left [cite: 426]
        return 1;
    }

    @Override
    public void soldOut(PublicCompany company) {
        // Rule 9.5: Rise diagonally up-right if director <= 40%, else up-left [cite: 478]
        if (company.getPresident().getPortfolioModel().getShares(company) > 4) {
            moveUpLeft(company);
        } else {
            moveUpRight(company);
        }
    }

    /* Hexagonal Movement Logic for Even-Row (even-r) Pointy-Topped Grid */

    private void moveDownRight(PublicCompany company) {
        StockSpace old = company.getCurrentSpace();
        int r = old.getRow();
        int c = old.getColumn();
        int nr = r + 1;
        int nc = (r % 2 == 0) ? c : c + 1;
        StockSpace newsquare = getStockSpace(nr, nc);
        if (newsquare != null) prepareMove(company, old, newsquare);
    }

    private void moveDownLeft(PublicCompany company) {
        StockSpace old = company.getCurrentSpace();
        int r = old.getRow();
        int c = old.getColumn();
        int nr = r + 1;
        int nc = (r % 2 == 0) ? c - 1 : c;
        StockSpace newsquare = getStockSpace(nr, nc);
        if (newsquare != null) prepareMove(company, old, newsquare);
    }

    private void moveUpLeft(PublicCompany company) {
        StockSpace old = company.getCurrentSpace();
        int r = old.getRow();
        int c = old.getColumn();
        int nr = r - 1;
        int nc = (r % 2 == 0) ? c - 1 : c;
        StockSpace newsquare = getStockSpace(nr, nc);
        if (newsquare != null) prepareMove(company, old, newsquare);
    }

    private void moveUpRight(PublicCompany company) {
        StockSpace old = company.getCurrentSpace();
        int r = old.getRow();
        int c = old.getColumn();
        int nr = r - 1;
        int nc = (r % 2 == 0) ? c : c + 1;
        StockSpace newsquare = getStockSpace(nr, nc);
        if (newsquare != null) prepareMove(company, old, newsquare);
    }

    public void moveDown(PublicCompany company) {
        // Rule 9.1: Sales move diagonally down and to the left [cite: 426]
        moveDownLeft(company);
    }

    @Override
    public void moveUp(PublicCompany company) {
        // Standard Up redirection to Up-Right for 1837 grid [cite: 727]
        moveUpRight(company);
    }

    protected void moveRightOrUp(PublicCompany company) {
        StockSpace old = company.getCurrentSpace();
        int r = old.getRow();
        int c = old.getColumn();
        // Rule 10.7.4: Try Right first [cite: 726]
        StockSpace newsquare = getStockSpace(r, c + 1);
        // If blocked or ledge reached, move Up-Right [cite: 727]
        if (newsquare == null || old.isLeftOfLedge()) {
            int nr = r - 1;
            int nc = (r % 2 == 0) ? c : c + 1;
            newsquare = getStockSpace(nr, nc);
        }
        if (newsquare != null) prepareMove(company, old, newsquare);
    }

    public void moveLeft(PublicCompany company) {
        StockSpace old = company.getCurrentSpace();
        int r = old.getRow();
        int c = old.getColumn();
        // Rule 10.7.4: Withholding moves Left. If not possible, Down-Left. [cite: 732, 733]
        StockSpace newsquare = getStockSpace(r, c - 1);
        if (newsquare == null) {
            int nr = r + 1;
            int nc = (r % 2 == 0) ? c - 1 : c;
            newsquare = getStockSpace(nr, nc);
        }
        if (newsquare != null) prepareMove(company, old, newsquare);
    }
}