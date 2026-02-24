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
        // Coal companies do not have physical tokens and do not trigger token placement
        // for the Major
        boolean isCoal = "Coal".equals(minor.getType().getId());

        if (!isCoal && targetStop != null) {

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
        boolean isPriorityMinor = id.equals("S1") || id.equals("K1") || id.equals("U1");
        net.sf.rails.game.financial.BankPortfolio unavailable = net.sf.rails.game.financial.Bank
                .getUnavailable(gm.getRoot());
        List<PublicCertificate> minorCerts = minor.getCertificates();

        if (minorCerts != null && !minorCerts.isEmpty()) {
            for (PublicCertificate mCert : minorCerts) {
                Player p = null;
                if (mCert.getOwner() instanceof Player) {
                    p = (Player) mCert.getOwner();
                } else if (owner != null) {
                    p = owner;
                }

                if (p != null) {
                    mCert.moveTo(unavailable);
                    PublicCertificate shareToGive = null;
                    boolean givePresident = isPriorityMinor && mCert.isPresidentShare();

                    if (givePresident) {
                    // 1. Prioritize Reserved Shares from the Unavailable Pool ("Thin Air")
                    for (PublicCertificate cert : major.getCertificates()) {
                        if (cert.isPresidentShare() && cert.getOwner() != null && cert.getOwner().equals(unavailable)) {
                            shareToGive = cert;
                            break;
                        }
                    }
                    // 2. Fallback to IPO if missing
                    if (shareToGive == null) {
                        for (PublicCertificate cert : major.getCertificates()) {
                            if (cert.isPresidentShare() && !(cert.getOwner() instanceof Player)) {
                                shareToGive = cert;
                                break;
                            }
                        }
                    }
                }

                if (shareToGive == null) {
                    // 1. Prioritize Reserved 10% Shares from the Unavailable Pool ("Thin Air")
                    for (PublicCertificate cert : major.getCertificates()) {
                        if (!cert.isPresidentShare() && cert.getOwner() != null && cert.getOwner().equals(unavailable)) {
                            shareToGive = cert;
                            break;
                        }
                    }
                    // 2. Fallback to IPO if missing
                    if (shareToGive == null) {
                        for (PublicCertificate cert : major.getCertificates()) {
                            if (!cert.isPresidentShare() && !(cert.getOwner() instanceof Player)) {
                                shareToGive = cert;
                                break;
                            }
                        }
                    }
                }

                    if (shareToGive != null) {
                        shareToGive.moveTo(p);
                        ReportBuffer.add(gm, "Exchanged " + id + " for " + major.getId() + " " + shareToGive.getShare()
                                + "% share to " + p.getName());
                    } else {
                        log.error("CRITICAL: No share available for " + p.getName());
                    }
                }
            }
        } else if (owner != null) {
            log.warn("1837_MERGER: Minor " + id + " has no certificates. Forcing exchange to President.");
            PublicCertificate shareToGive = null;

            if (isPriorityMinor) {
                for (PublicCertificate cert : major.getCertificates()) {
                    if (cert.isPresidentShare() && !(cert.getOwner() instanceof Player)) {
                        shareToGive = cert;
                        break;
                    }
                }
            }
            if (shareToGive == null) {
                for (PublicCertificate cert : major.getCertificates()) {
                    if (!cert.isPresidentShare() && !(cert.getOwner() instanceof Player)) {
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
     * Centralized logic to determine if a company merges into another.
     * STRICTLY checks IDs to avoid false positives (e.g. SB -> Sd).
     */
    public static PublicCompany getMergeTarget(GameManager gameManager, PublicCompany source) {
        if (source == null)
            return null;

        String id = source.getId();
        String targetId = null;

        // 1. Explicit Coal Mappings (Exact Match)
        switch (id) {
            case "EPP":
            case "RGTE":
                targetId = "BK";
                break;

            case "EOD":
            case "EKT":
                targetId = "MS";
                break;

            case "MLB":
                targetId = "CL";
                break;

            case "ZKB":
            case "SPB":
                targetId = "SB";
                break;

            case "LRB":
            case "EHS":
                targetId = "TH";
                break;

            case "BB":
                targetId = "BH";
                break;
        }

        // 2. National Minor Mappings (Strict Regex)
        // Only matches S1-S5, K1-K3, U1-U3.
        // Ignores "SB", "Sd", "KK", "Ug", "KA", etc.
        if (targetId == null) {
            if (id.matches("S[1-5]")) {
                targetId = "Sd";
            } else if (id.matches("K[1-3]")) {
                targetId = "KK";
            } else if (id.matches("U[1-3]")) {
                targetId = "Ug";
            }
        }

        if (targetId != null) {
            return gameManager.getRoot().getCompanyManager().getPublicCompany(targetId);
        }
        return null;
    }

    public static void fixDirectorship(GameManager gm, PublicCompany major) {
        fixDirectorship(gm, major, null, null, null);
    }

    /**
     * Overloaded to handle complex tie-breakers for 1837 national companies (e.g.,
     * Ug).
     */
    public static void fixDirectorship(GameManager gm, PublicCompany major, Map<Player, Integer> directorHistory,
            Map<Player, Integer> ownerHistory, Player formerU1Owner) {

                // 1. Calculate Holdings deterministically based on seat order
        Map<Player, Integer> shareCounts = new java.util.LinkedHashMap<>();
        for (Player p : gm.getPlayers()) {
            shareCounts.put(p, 0);
        }
        for (PublicCertificate cert : major.getCertificates()) {
            if (cert.getOwner() instanceof Player) {
                Player p = (Player) cert.getOwner();
                shareCounts.put(p, shareCounts.get(p) + cert.getShare());
            }
        }

        Player currentPrez = major.getPresident();
        // If currentPrez is null (e.g., just formed), identify who actually holds the president certificate
        if (currentPrez == null) {
            for (PublicCertificate cert : major.getCertificates()) {
                if (cert.isPresidentShare() && cert.getOwner() instanceof Player) {
                    currentPrez = (Player) cert.getOwner();
                    break;
                }
            }
        }

        // If NO ONE holds the President's share, the company has not floated and has no director.
        // We must prevent 10% coal exchange holders from stealing the 20% President's share from the IPO.
        if (currentPrez == null) {
            log.info("1837_DIRECTORSHIP: No player holds the President's share for " + major.getId() + ". Skipping shift.");
            return;
        }

        log.info("1837_DIRECTORSHIP: Evaluating " + major.getId() + ". Current: "
                + (currentPrez != null ? currentPrez.getName() : "None"));
        for (Player p : shareCounts.keySet()) {
            if (shareCounts.get(p) > 0) {
                log.info("1837_DIRECTORSHIP: Player " + p.getName() + " holds " + shareCounts.get(p) + "%");
            }
        }

        // 2. Find Leader
        Player newPrez = null;
        int maxShare = -1;

        

        List<Player> tiedPlayers = new ArrayList<>();

        for (Map.Entry<Player, Integer> entry : shareCounts.entrySet()) {
            int share = entry.getValue();
            if (share > maxShare) {
                maxShare = share;
                tiedPlayers.clear();
                tiedPlayers.add(entry.getKey());
            } else if (share == maxShare) {
                tiedPlayers.add(entry.getKey());
            }
        }

        if (tiedPlayers.size() == 1) {
            newPrez = tiedPlayers.get(0);
        } else if (tiedPlayers.size() > 1) {
            // TIE BREAKER LOGIC
            if ("Ug".equals(major.getId()) && directorHistory != null && ownerHistory != null
                    && formerU1Owner != null) {
                // Rule 1: Director of lowest-numbered minor
                int lowestDir = 999;
                for (Player p : tiedPlayers) {
                    if (directorHistory.containsKey(p) && directorHistory.get(p) < lowestDir) {
                        lowestDir = directorHistory.get(p);
                        newPrez = p;
                    }
                }
                if (newPrez == null) {
                    // Rule 2: Owner of lowest-numbered minor
                    int lowestOwn = 999;
                    for (Player p : tiedPlayers) {
                        if (ownerHistory.containsKey(p) && ownerHistory.get(p) < lowestOwn) {
                            lowestOwn = ownerHistory.get(p);
                            newPrez = p;
                        }
                    }
                }
                if (newPrez == null) {
                    // Rule 3: Closest to left of former U1 owner
                    List<Player> players = gm.getPlayers();
                    int u1Index = players.indexOf(formerU1Owner);
                    int minDistance = 999;
                    for (Player p : tiedPlayers) {
                        int pIndex = players.indexOf(p);
                        int dist = (pIndex > u1Index) ? (pIndex - u1Index) : (pIndex + players.size() - u1Index);
                        if (dist < minDistance) {
                            minDistance = dist;
                            newPrez = p;
                        }
                    }
                }
            } else {
                // Standard tie-breaker: Incumbent wins
                if (currentPrez != null && tiedPlayers.contains(currentPrez)) {
                    newPrez = currentPrez;
                } else {
                    newPrez = tiedPlayers.get(0); // Fallback
                }
            }
        }
        // --- END FIX ---

        // 3. Execute Swap
        if (newPrez != null && !newPrez.equals(currentPrez)) {
            log.info("FixDirectorship: " + (currentPrez != null ? currentPrez.getName() : "None") + " -> "
                    + newPrez.getName());

            log.info("1837_DIRECTORSHIP: SHIFT DETECTED! " + (currentPrez != null ? currentPrez.getName() : "None")
                    + " -> " + newPrez.getName());

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