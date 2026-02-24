package net.sf.rails.game.specific._1837;

import java.util.HashMap;
import java.util.Map;

import net.sf.rails.game.GameManager;
import net.sf.rails.game.Player;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.financial.PublicCertificate;
import net.sf.rails.game.model.PortfolioModel;
import net.sf.rails.game.model.PortfolioOwner;
import net.sf.rails.game.state.HashMapState;
import net.sf.rails.game.state.StringState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UgFormationRound extends NationalFormationRound {
    private static final Logger log = LoggerFactory.getLogger(UgFormationRound.class);

    // --- START FIX ---
    // Persistent state memory to survive mid-round save/loads during PBEM
    protected final HashMapState<String, Integer> directorHistory = HashMapState.create(this, "directorHistory");
    protected final HashMapState<String, Integer> ownerHistory = HashMapState.create(this, "ownerHistory");
    protected final StringState formerU1Owner = StringState.create(this, "formerU1Owner");
    // --- END FIX ---

    public UgFormationRound(GameManager parent, String id) {
        super(parent, id);
    }

    @Override
    public void start(PublicCompany_1837 national, boolean isTriggered, String reportName) {
        // Capture Historical Data BEFORE any mergers occur to feed tie-breakers
        if (directorHistory.isEmpty()) {
            for (PublicCompany minor : national.getMinors()) {
                if (minor.isClosed()) continue;
                
                String minorId = minor.getId();
                int minorNum = -1;
                try {
                    minorNum = Integer.parseInt(minorId.replace("U", ""));
                } catch (Exception e) { continue; }

                // 1. Capture Directorships
                Player dir = minor.getPresident();
                if (dir != null) {
                    if (!directorHistory.containsKey(dir.getName()) || directorHistory.get(dir.getName()) > minorNum) {
                        directorHistory.put(dir.getName(), minorNum);
                    }
                    if (minorNum == 1) {
                        formerU1Owner.set(dir.getName());
                    }
                }

                // 2. Capture Ownership (including fractional co-owners)
                for (Player p : gameManager.getPlayers()) {
                    if (p instanceof PortfolioOwner) {
                        PortfolioModel pm = ((PortfolioOwner) p).getPortfolioModel();
                        for (Object obj : pm.getCertificates()) {
                            if (obj instanceof PublicCertificate) {
                                PublicCertificate cert = (PublicCertificate) obj;
                                if (cert.getCompany().equals(minor)) {
                                    if (!ownerHistory.containsKey(p.getName()) || ownerHistory.get(p.getName()) > minorNum) {
                                        ownerHistory.put(p.getName(), minorNum);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        super.start(national, isTriggered, reportName);
    }

    @Override
    protected void processExchange(PublicCompany minor, PublicCompany major, ExchangeMinorAction action) {
        log.info("1837_UG_NFR: Processing specialized exchange for " + minor.getId());
        
        // Asset transfer natively executes the U1/U3 forced co-exchange by sweeping all minor certs
        Merger1837.mergeMinor(gameManager, minor, major);

        // Reconstruct maps with Player objects to match the Merger1837 signature
        Map<Player, Integer> pDirHistory = new HashMap<>();
        Map<Player, Integer> pOwnHistory = new HashMap<>();
        Player pU1Owner = null;

        for (Player p : gameManager.getPlayers()) {
            if (directorHistory.containsKey(p.getName())) pDirHistory.put(p, directorHistory.get(p.getName()));
            if (ownerHistory.containsKey(p.getName())) pOwnHistory.put(p, ownerHistory.get(p.getName()));
            if (p.getName().equals(formerU1Owner.value())) pU1Owner = p;
        }

        // Execute rule-compliant tie-breaker
        Merger1837.fixDirectorship(gameManager, major, pDirHistory, pOwnHistory, pU1Owner);
    }
}