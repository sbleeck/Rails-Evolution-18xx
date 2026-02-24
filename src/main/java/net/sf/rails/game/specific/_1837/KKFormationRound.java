package net.sf.rails.game.specific._1837;

import net.sf.rails.game.GameManager;
import net.sf.rails.game.PublicCompany;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KKFormationRound extends NationalFormationRound {
    private static final Logger log = LoggerFactory.getLogger(KKFormationRound.class);

    public KKFormationRound(GameManager parent, String id) {
        super(parent, id);
    }

    @Override
    protected void processExchange(PublicCompany minor, PublicCompany major, ExchangeMinorAction action) {
        log.info("1837_KK_NFR: Processing standard clockwise exchange for " + minor.getId());
        
        // Execute asset transfer and standard individual exchange
        Merger1837.mergeMinor(gameManager, minor, major);

        // Execute standard incumbent-wins directorship resolution 
        Merger1837.fixDirectorship(gameManager, major);
    }
}