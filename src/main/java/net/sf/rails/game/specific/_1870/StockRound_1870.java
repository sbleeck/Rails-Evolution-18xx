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

import java.util.List;

public class StockRound_1870 extends StockRound {

    private static final Logger log = LoggerFactory.getLogger(StockRound_1870.class);
    private final StringState jumpedToPlayer = StringState.create(this, "jumpedToPlayer");

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
        // We pass 'null' for the seller because the transaction is already complete, we just need the drop.
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
                
                ReportBuffer.add(this, "=> TURN JUMP: Play resumes with " + targetPlayer.getName() + " (player to the left of the catcher).");
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
                ((GameManager_1870) getRoot().getGameManager()).startShareProtectionRound(this, company, (Player)seller, sharesSold);
            }
        } else {
            // Normal fallback (No president, president selling, or failed cash/cert checks)
            super.adjustSharePrice(company, seller, sharesSold, soldBefore);
        }
    }
}
// --- END FIX ---