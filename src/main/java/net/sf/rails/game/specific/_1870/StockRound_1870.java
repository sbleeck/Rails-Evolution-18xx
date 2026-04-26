// --- START FIX ---
package net.sf.rails.game.specific._1870;

import net.sf.rails.game.Player;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.state.Owner;
import net.sf.rails.game.financial.StockRound;
import net.sf.rails.game.state.StringState;
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
        log.info(">>> StockRound_1870: Priority jumps to {} following share protection by {}.", 
                 nextPlayer.getName(), protectingPresident.getName());
    }

    @Override
    public void resume() {
        super.resume();
        
        // Apply the turn order jump if a successful protection occurred
        if (jumpedToPlayer.value() != null && !jumpedToPlayer.value().isEmpty()) {
            Player targetPlayer = playerManager.getPlayerByName(jumpedToPlayer.value());
            if (targetPlayer != null) {
                setCurrentPlayer(targetPlayer);
                log.info(">>> StockRound_1870: Turn order jump applied. Active player is now {}", targetPlayer.getName());
            }
            jumpedToPlayer.set(null); 
        }
    }

    @Override
    protected void adjustSharePrice(PublicCompany company, Owner seller, int sharesSold, boolean soldBefore) {
        Player president = company.getPresident();
        boolean canProtect = false;
        
        if (president != null && seller != president && !president.hasSoldThisRound(company)) {
            // Check affordability and certificate limits
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
            log.info(">>> Intercepting price drop. Triggering Share Protection for {}", company.getId());
            if (getRoot().getGameManager() instanceof GameManager_1870) {
                ((GameManager_1870) getRoot().getGameManager()).startShareProtectionRound(this, company, sharesSold);
            }
            // Bypass super.adjustSharePrice() to prevent the token from dropping.
        } else {
            super.adjustSharePrice(company, seller, sharesSold, soldBefore);
        }
    }
}
// --- END FIX ---