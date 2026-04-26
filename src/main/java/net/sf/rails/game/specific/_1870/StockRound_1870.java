// --- START FIX ---
package net.sf.rails.game.specific._1870;

import net.sf.rails.game.Player;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.state.Owner;
import net.sf.rails.game.financial.StockRound;
import net.sf.rails.game.state.StringState;
import net.sf.rails.common.ReportBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.sf.rails.game.specific._1870.action.ExchangeMKT_1870;
import net.sf.rails.game.specific._1870.action.RedeemShare_1870;
import net.sf.rails.game.specific._1870.action.ReissueShares_1870;
import java.util.List;

public class StockRound_1870 extends StockRound {

    private static final Logger log = LoggerFactory.getLogger(StockRound_1870.class);
    private final StringState jumpedToPlayer = StringState.create(this, "jumpedToPlayer");
    // Track actions per round to enforce 1870 limits. We use StringState to avoid
    // ArrayListState missing symbols.
    private final net.sf.rails.game.state.StringState redeemedThisRound = net.sf.rails.game.state.StringState
            .create(this, "redeemedThisRound", "");
    private final net.sf.rails.game.state.StringState reissuedThisRound = net.sf.rails.game.state.StringState
            .create(this, "reissuedThisRound", "");

    public StockRound_1870(net.sf.rails.game.GameManager parent, String id) {

        super(parent, id);
    }

    public void setNextPlayerAfterProtection(Player protectingPresident) {
        List<Player> players = playerManager.getPlayers();
        int pIndex = players.indexOf(protectingPresident);
        int nextIndex = (pIndex + 1) % players.size();
        Player nextPlayer = players.get(nextIndex);

        jumpedToPlayer.set(nextPlayer.getName());
    }

    public void processPassedProtection(PublicCompany company, int sharesSold) {
        // President declined the protection. Proceed with the normal price drop.
        // We pass 'null' for the seller because the transaction is already complete, we
        // just need the drop.
        super.adjustSharePrice(company, null, sharesSold, false);
    }

    @Override
    public void resume() {
        // Apply the turn order jump BEFORE calling super.resume()
        if (jumpedToPlayer.value() != null && !jumpedToPlayer.value().isEmpty()) {
            Player targetPlayer = playerManager.getPlayerByName(jumpedToPlayer.value());
            if (targetPlayer != null) {
                // Advance the engine's internal pointer
                setCurrentPlayer(targetPlayer);

                // Wipe the seller's turn state so the new player starts completely fresh
                hasActed.set(false);
                companyBoughtThisTurnWrapper.set(null);
                hasSoldThisTurnBeforeBuying.set(false);
                sellPrices.clear();
                numPasses.set(0);

                ReportBuffer.add(this, "=> TURN JUMP: Play resumes with " + targetPlayer.getName()
                        + " (player to the left of the catcher).");
            }
            jumpedToPlayer.set(null);
        }

        super.resume();
    }

    @Override
    protected void adjustSharePrice(PublicCompany company, Owner seller, int sharesSold, boolean soldBefore) {
        Player president = company.getPresident();
        boolean canProtect = false;

        if (president != null && seller != president && !president.hasSoldThisRound(company)) {
            int price = company.getCurrentSpace().getPrice() / company.getShareUnitsForSharePrice();
            int totalCost = sharesSold * price;

            boolean canAfford = president.getCashValue() >= totalCost;
            float currentCerts = president.getPortfolioModel().getCertificateCount();
            int certLimit = getRoot().getGameManager().getPlayerCertificateLimit(president);
            boolean certLimitOk = (currentCerts + sharesSold) <= certLimit;

            if (canAfford && certLimitOk) {
                canProtect = true;
            }
        }

        if (canProtect) {
            if (getRoot().getGameManager() instanceof GameManager_1870) {
                // Trigger the interrupt and explicitly pass the seller
                ((GameManager_1870) getRoot().getGameManager()).startShareProtectionRound(this, company,
                        (Player) seller, sharesSold);
            }
        } else {
            // Normal fallback (No president, president selling, or failed cash/cert checks)
            super.adjustSharePrice(company, seller, sharesSold, soldBefore);
        }
    }

    @Override
    public Player getCurrentPlayer() {
        // 1870 Rule: If we jumped to a specific player after Price Protection, return
        // them.
        String name = jumpedToPlayer.value();
        if (name != null && !name.isEmpty()) {
            for (Player p : playerManager.getPlayers()) {
                if (p.getName().equals(name))
                    return p;
            }
        }
        return super.getCurrentPlayer();
    }

    @Override
    public boolean setPossibleActions() {
        boolean result = super.setPossibleActions();

        // Use our overridden method, with a fallback to the GameManager source of truth
        Player currentPlayer = getCurrentPlayer();
        if (currentPlayer == null) {
            currentPlayer = gameManager.getCurrentPlayer();
        }

        // Fix: Use iterator to remove BuyPrivate actions without removeType() method.
        java.util.List<rails.game.action.PossibleAction> list = possibleActions.getList();
        java.util.Iterator<rails.game.action.PossibleAction> it = list.iterator();
        while (it.hasNext()) {
            if (it.next() instanceof rails.game.action.BuyPrivate) {
                it.remove();
            }
        }

        // Check for MKT Exchange: Player owns MKT private and MKT public isn't floated.
        for (net.sf.rails.game.PrivateCompany priv : currentPlayer.getPortfolioModel().getPrivateCompanies()) {
            if ("MKT".equals(priv.getId())) {
                PublicCompany mktPub = getRoot().getCompanyManager().getPublicCompany("MKT");
                if (mktPub != null && !mktPub.hasFloated()) {
                    possibleActions.add(new ExchangeMKT_1870(priv));
                }
            }
        }

        for (PublicCompany company : getRoot().getCompanyManager().getAllPublicCompanies()) {

            if (company.isClosed() || company.getPresident() != currentPlayer) {
                continue;
            }

            // 1. Reissue Checks: IPO empty, has shares in treasury, once per round.
            // We use getShare() == 0 to ensure absolutely no percentage remains in the IPO.
            if (!reissuedThisRound.value().contains(company.getId() + ",")
                    && ipo.getShare(company) == 0
                    && company.getPortfolioModel().getShare(company) > 0) {

                possibleActions.add(new net.sf.rails.game.specific._1870.action.ReissueShares_1870(company));
            }

            // 1. Reissue Checks: IPO empty, has shares in treasury, once per round.
            if (!reissuedThisRound.value().contains(company.getId() + ",") && ipo.getShares(company) == 0
                    && company.getPortfolioModel().getShares(company) > 0) {
                possibleActions.add(new net.sf.rails.game.specific._1870.action.ReissueShares_1870(company));
            }

            // 2. Redeem Checks: Has operated, max 4 shares in treasury (leaves 6 in
            // market/player), once per round.
            // 2. Redeem Checks: Has operated, max 4 shares in treasury (leaves 6 in
            // market/player), once per round.
            if (company.hasOperated() && !redeemedThisRound.value().contains(company.getId() + ",")
                    && company.getPortfolioModel().getShares(company) < 4) {
                int marketPrice = company.getCurrentSpace().getPrice() / company.getShareUnitsForSharePrice();

                if (company.getCash() >= marketPrice) {
                    // 1870 Rule: Redemption is allowed if there is at least one share in the Bank
                    // Pool.
                    if (pool.getShares(company) > 0) {
                        possibleActions.add(new net.sf.rails.game.specific._1870.action.RedeemShare_1870(company));
                    }
                }
            }

        }
        return result;
    }

    @Override
    public boolean process(rails.game.action.PossibleAction action) {
        if (action instanceof net.sf.rails.game.specific._1870.action.ReissueShares_1870) {
            PublicCompany company = ((net.sf.rails.game.specific._1870.action.ReissueShares_1870) action).getCompany();
            reissuedThisRound.set(reissuedThisRound.value() + company.getId() + ",");

            int sharesToReissue = company.getPortfolioModel().getShares(company);
            for (net.sf.rails.game.financial.PublicCertificate cert : company.getPortfolioModel()
                    .getCertificates(company)) {
                cert.moveTo(ipo);
            }

            int currentPrice = company.getCurrentSpace().getPrice();
            int oldPar = company.getParPrice();
            int newPar = calculateNewPar(oldPar, currentPrice);

            net.sf.rails.game.financial.StockSpace newParSpace = stockMarket.getStartSpace(newPar);
            if (newParSpace != null) {
                company.setParSpace(newParSpace);
            }

            net.sf.rails.common.ReportBuffer.add(this, company.getId() + " reissues " + sharesToReissue
                    + " share(s) to IPO. Par adjusted to " + getRoot().getBank().getCurrency().format(newPar) + ".");

            return true;

        } else if (action instanceof net.sf.rails.game.specific._1870.action.RedeemShare_1870) {
            PublicCompany company = ((net.sf.rails.game.specific._1870.action.RedeemShare_1870) action).getCompany();
            redeemedThisRound.set(redeemedThisRound.value() + company.getId() + ",");

            int marketPrice = company.getCurrentSpace().getPrice() / company.getShareUnitsForSharePrice();

            boolean fromPool = false;
            net.sf.rails.game.financial.PublicCertificate certToMove = null;
            if (pool.getShares(company) > 0) {
                certToMove = pool.getCertificates(company).iterator().next();
                fromPool = true;
            } else {
                for (net.sf.rails.game.financial.PublicCertificate cert : getCurrentPlayer().getPortfolioModel()
                        .getCertificates(company)) {
                    if (!cert.isPresidentShare()) {
                        certToMove = cert;
                        break;
                    }
                }
            }

            if (certToMove != null) {
                if (fromPool) {
                    net.sf.rails.game.state.Currency.wire(company, marketPrice, getRoot().getBank());
                } else {
                    net.sf.rails.game.state.Currency.wire(company, marketPrice, getCurrentPlayer());
                }
                certToMove.moveTo(company.getPortfolioModel());
                net.sf.rails.common.ReportBuffer.add(this, company.getId() + " redeems a share for "
                        + getRoot().getBank().getCurrency().format(marketPrice) + ".");
            }

            return true;
        } else if (action instanceof ExchangeMKT_1870) {
            net.sf.rails.game.PrivateCompany mktPriv = ((ExchangeMKT_1870) action).getMktPrivate();
            PublicCompany mktPub = getRoot().getCompanyManager().getPublicCompany("MKT");

            // 1. Close Private
            mktPriv.close();

            // 2. Set Par to $100
            mktPub.setParSpace(stockMarket.getStartSpace(100));

            // 3. Move President's Share to Player
            mktPub.getPresidentsShare().moveTo(getCurrentPlayer().getPortfolioModel());

            // 4. Record
            net.sf.rails.common.ReportBuffer.add(this, getCurrentPlayer().getName()
                    + " exchanges MKT private for MKT public president's share. Par set at $100.");

            return true;
        }
        return super.process(action);
    }

    private int calculateNewPar(int oldPar, int marketPrice) {
        int target = (int) Math.round(marketPrice * 0.75);
        int[] pars = { 68, 72, 76, 82, 90, 100, 110, 120, 140, 160, 180, 200, 220, 250, 275, 300, 350, 400 };
        int closest = pars[0];
        int minDiff = Integer.MAX_VALUE;
        for (int p : pars) {
            int diff = Math.abs(target - p);
            if (diff < minDiff) {
                closest = p;
                minDiff = diff;
            } else if (diff == minDiff) {
                // Break ties by mimicking the historical printed table in the PDF rules
                if (marketPrice == 140 && p == 110)
                    closest = p;
                if (marketPrice == 200 && p == 140)
                    closest = p;
            }
        }
        return Math.max(oldPar, closest);
    }

}
// --- END FIX ---