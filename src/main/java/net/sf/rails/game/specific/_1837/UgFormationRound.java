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

    // Persistent state memory to survive mid-round save/loads during PBEM
    protected final HashMapState<String, Integer> directorHistory = HashMapState.create(this, "directorHistory");
    protected final HashMapState<String, Integer> ownerHistory = HashMapState.create(this, "ownerHistory");
    protected final StringState formerU1Owner = StringState.create(this, "formerU1Owner");


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
        
        // 1. Mandatory Floating & Capitalization (875K)
        if (action.isFormation() && !major.hasFloated()) {
            net.sf.rails.game.financial.StockMarket market = getRoot().getStockMarket();
            net.sf.rails.game.financial.StockSpace parSpace = null;
            int targetPar = 175; // Rule: Ug starts at 175

            for (net.sf.rails.game.financial.StockSpace ss : market.getStartSpaces()) {
                if (ss.getPrice() == targetPar) {
                    parSpace = ss;
                    break;
                }
            }
            if (parSpace != null) major.setCurrentSpace(parSpace);
            
            // Inject starting capital!
            net.sf.rails.game.state.Currency.fromBank(875, major);
            major.setFloated();
            log.info("1837_UG_NFR: Ug successfully floated and received 875K.");
        }


        if ("U1".equals(minor.getId())) {
            net.sf.rails.game.financial.BankPortfolio unavailable = net.sf.rails.game.financial.Bank.getUnavailable(gameManager.getRoot());
            
            for (Player p : gameManager.getPlayers()) {
                for (Object obj : new java.util.ArrayList<>(p.getPortfolioModel().getCertificates())) {
                    if (obj instanceof net.sf.rails.game.financial.PublicCertificate) {
                        net.sf.rails.game.financial.PublicCertificate cert = (net.sf.rails.game.financial.PublicCertificate) obj;
                        if (cert.getCompany().equals(minor)) {
                            boolean needsPresident = cert.isPresidentShare();
                            cert.moveTo(unavailable);
                            
                            for (net.sf.rails.game.financial.PublicCertificate pc : major.getCertificates()) {
                                if (pc.isPresidentShare() == needsPresident && !(pc.getOwner() instanceof Player)) {
                                    pc.moveTo(p);
                                    log.info("1837_UG_NFR: U1 owner " + p.getName() + " received Ug share (President=" + needsPresident + ").");
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

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
// --- END FIX ---
    }
    


    @Override
    public void finishRound() {
        log.info("1837_UG_NFR: Finalizing Ug directorship after all exchanges.");
        PublicCompany major = getNational();
        if (major != null && major.hasStarted()) {
            Map<Player, Integer> pDirHistory = new HashMap<>();
            Map<Player, Integer> pOwnHistory = new HashMap<>();
            Player pU1Owner = null;

            for (Player p : gameManager.getPlayers()) {
                if (directorHistory.containsKey(p.getName())) pDirHistory.put(p, directorHistory.get(p.getName()));
                if (ownerHistory.containsKey(p.getName())) pOwnHistory.put(p, ownerHistory.get(p.getName()));
                if (p.getName().equals(formerU1Owner.value())) pU1Owner = p;
            }
            Merger1837.fixDirectorship(gameManager, major, pDirHistory, pOwnHistory, pU1Owner);
        }
        super.finishRound();
    }




}