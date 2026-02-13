package net.sf.rails.game.specific._1837;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.rails.common.DisplayBuffer;
import net.sf.rails.common.ReportBuffer;
import net.sf.rails.game.GameManager;
import net.sf.rails.game.MapHex;
import net.sf.rails.game.Player;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.Stop;
import net.sf.rails.game.financial.Bank;
import net.sf.rails.game.financial.PublicCertificate;
import net.sf.rails.game.BaseToken;
import net.sf.rails.game.state.Currency;

/**
 * Helper class for 1837-specific merger logic (Sd, KK, Ug).
 * Encapsulates robust asset transfer, token placement, and share exchange
 * logic.
 */
public class Merger1837 {

    private static final Logger log = LoggerFactory.getLogger(Merger1837.class);

    /**
     * Merges a single minor company into a major company.
     * Handles: Assets, Trains, Closing, Token Swap, and Share Exchange.
     */
    public static void mergeMinor(GameManager gm, PublicCompany minor, PublicCompany major) {

String id = minor.getId();
        Player owner = minor.getPresident();
        log.info("Merging Minor: " + id + " into " + major.getId() + " (Owner: "
                + (owner != null ? owner.getName() : "Bank/IPO") + ")");


        // Assets MUST be moved before the minor is modified or closed
        log.info("1837_MERGER: Commencing asset transfer for " + id);
      // Use the standard engine method to move Cash, Trains, and Privates.
        // It bypasses the "getTrains() is empty" bug by handling portfolios internally.
        major.transferAssetsFrom(minor);
        log.info("1837_MERGER: Assets (Trains, Cash, Privates) transferred via transferAssetsFrom().");

        
        // --- 1. LOCATE HOME STOP (For Token Placement) ---
        MapHex homeHex = null;
        Stop targetStop = null;
        if (minor.getHomeHexes() != null && !minor.getHomeHexes().isEmpty()) {
            homeHex = minor.getHomeHexes().get(0);
            if (homeHex != null) {
                // 18xx logic: Get stop by city number
                targetStop = homeHex.getRelatedStop(minor.getHomeCityNumber());
            }
        }

        // --- 2. ASSET TRANSFER ---
        // Move Cash (Using toBank/fromBank swap to avoid API limits)
        int cash = minor.getCash();
        if (cash > 0) {
            Currency.toBank(minor, cash);
            Currency.fromBank(cash, major);
            log.info("Transferred " + cash + " from " + id);
        }

    

        // Move Privates
        List<net.sf.rails.game.PrivateCompany> privates = new ArrayList<>(minor.getPrivates());
        for (net.sf.rails.game.PrivateCompany p : privates) {
            p.moveTo(major);
        }

        // --- 3. CLOSE MINOR ---
        minor.setClosed();

        // --- 4. PLACE MAJOR TOKEN ---
        // Check exclude list (e.g. S5 in Sd formation usually doesn't get a token if
        // it's on the same hex?
        // actually S5 is usually the exception. We pass 'true' to always place unless
        // logic prevents it)
        if (targetStop != null) {
            // Special rule: S5 does not get a token in Sd formation (usually).
            // We can handle this by checking if the major already has a token there, or
            // passing a flag.
            // For general robustness: Don't place if Major already has a token on this
            // stop.

            boolean alreadyHasToken = false;
            if (targetStop.getTokens() != null) {
                for (BaseToken t : targetStop.getTokens()) {
                    if (t.getOwner() != null && t.getOwner().equals(major)) {
                        alreadyHasToken = true;
                        break;
                    }
                }
            }

            // Specific 1837 Rule: S5 usually shouldn't get a token.
            boolean isS5 = id.equals("S5");

            if (!alreadyHasToken && !isS5) {
                BaseToken tokenToPlace = null;
                for (BaseToken t : major.getAllBaseTokens()) {
                    if (!t.isPlaced()) {
                        tokenToPlace = t;
                        break;
                    }
                }

                if (tokenToPlace != null) {
                    tokenToPlace.moveTo(targetStop);
                    String location = (homeHex != null) ? homeHex.getId() : "Unknown";
                    log.info("Placed " + major.getId() + " token on hex " + location);
                }
            }
        }

        // --- 5. SHARE EXCHANGE ---
        if (owner != null) {
            PublicCertificate shareToGive = null;

            // Priority: Give President's Share to specific minors (S1 for Sd, K1 for KK, U1
            // for Ug)
            // Heuristic: If Minor ID ends in "1", try Pres Share first.
            boolean isPriorityMinor = id.endsWith("1");

            if (isPriorityMinor) {
                for (PublicCertificate cert : major.getCertificates()) {
                    if (cert.isPresidentShare() && !(cert.getOwner() instanceof Player)) {
                        shareToGive = cert;
                        break;
                    }
                }
            }

            // Fallback: Standard 10% share
            if (shareToGive == null) {
                for (PublicCertificate cert : major.getCertificates()) {
                    // Strict: 10%, Not owned by Player, Not owned by Recipient
                    if (!cert.isPresidentShare() && cert.getShare() == 10
                            && !(cert.getOwner() instanceof Player)
                            && cert.getOwner() != owner) {
                        shareToGive = cert;
                        break;
                    }
                }
            }

            if (shareToGive != null) {
                shareToGive.moveTo(owner);
                ReportBuffer.add(gm, "Exchanged " + id + " for " + major.getId() + " " + shareToGive.getShare()
                        + "% share to " + owner.getName());
            } else {
                log.error("CRITICAL: No share available for " + owner.getName());
            }
        }
    }

    /**
     * Checks if the President needs to change based on current shareholdings.
     * Swaps the President's Certificate and returns 20% worth of shares if a swap
     * occurs.
     */
    public static void fixDirectorship(GameManager gm, PublicCompany major) {

        // 1. Calculate Holdings
        Map<Player, Integer> shareCounts = new HashMap<>();
        for (PublicCertificate cert : major.getCertificates()) {
            if (cert.getOwner() instanceof Player) {
                Player p = (Player) cert.getOwner();
                shareCounts.put(p, shareCounts.getOrDefault(p, 0) + cert.getShare());
            }
        }

        // 2. Find Leader
        Player newPrez = null;
        int maxShare = -1;
        Player currentPrez = major.getPresident();

        for (Map.Entry<Player, Integer> entry : shareCounts.entrySet()) {
            int share = entry.getValue();
            if (share > maxShare) {
                maxShare = share;
                newPrez = entry.getKey();
            } else if (share == maxShare) {
                // Tie: Incumbent wins
                if (currentPrez != null && entry.getKey().equals(currentPrez)) {
                    newPrez = currentPrez;
                }
            }
        }

        // 3. Execute Swap
        if (newPrez != null && !newPrez.equals(currentPrez)) {
            log.info("FixDirectorship: " + (currentPrez != null ? currentPrez.getName() : "None") + " -> "
                    + newPrez.getName());

            PublicCertificate presCert = null;
            for (PublicCertificate c : major.getCertificates()) {
                if (c.isPresidentShare()) {
                    presCert = c;
                    break;
                }
            }

            if (presCert != null) {
                // Move Pres Cert to New Prez
                presCert.moveTo(newPrez);

                // Return 2x 10% shares
                int valueToReturn = presCert.getShare(); // 20
                int valueReturned = 0;

                // Fetch New Prez's certs again to find return candidates
                List<PublicCertificate> returnCandidates = new ArrayList<>();
                for (PublicCertificate c : major.getCertificates()) {
                    if (c.getOwner().equals(newPrez) && !c.isPresidentShare()) {
                        returnCandidates.add(c);
                    }
                }

                for (PublicCertificate c : returnCandidates) {
                    if (valueReturned < valueToReturn) {
                        if (currentPrez != null) {
                            c.moveTo(currentPrez);
                        } else {
                            c.moveTo(Bank.getUnavailable(gm.getRoot()));
                        }
                        valueReturned += c.getShare();
                    }
                }

                major.setPresident(newPrez);

                // String msg = "Director of " + major.getId() + " changes to " +
                // newPrez.getName();
                // ReportBuffer.add(gm, msg);
                // DisplayBuffer.add(gm, msg);
            }
        }
    }
}