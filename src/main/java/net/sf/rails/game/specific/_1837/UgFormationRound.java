package net.sf.rails.game.specific._1837;

import net.sf.rails.game.GameManager;
import net.sf.rails.game.Player;
import net.sf.rails.game.PublicCompany;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UgFormationRound extends NationalFormationRound {
    private static final Logger log = LoggerFactory.getLogger(UgFormationRound.class);

    public UgFormationRound(GameManager parent, String id) {
        super(parent, id);
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
    }
}