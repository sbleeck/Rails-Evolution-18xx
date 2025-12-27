package net.sf.rails.game.ai.snapshot;

import net.sf.rails.common.*;
import net.sf.rails.game.*;
// FIX: Import the correct POJO definition file
import net.sf.rails.game.ai.snapshot.GameStateData; 
import net.sf.rails.game.financial.*;
import net.sf.rails.game.model.PortfolioModel;
import net.sf.rails.game.state.Owner;
import net.sf.rails.game.state.PortfolioSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import net.sf.rails.game.model.PortfolioOwner;
import rails.game.action.LayTile;

/**
 * Handles the "re-hydration" of a game state.
 * It takes the POJO data (parsed by Gson) and uses it to populate a
 * new, live RailsRoot object, linking all references.
 */
public class GameStateRestorer {

    private static final Logger log = LoggerFactory.getLogger(GameStateRestorer.class);

    // Registries
    private Map<String, Player> playerRegistry;
    private Map<String, PublicCompany> companyRegistry;
    private Map<String, PrivateCompany> privateRegistry;
    private Map<String, Integer> rotationNameToIntMap;
    private Map<String, Owner> ownerRegistry; // Universal Owner Registry

    private RailsRoot liveGameRoot;

    public GameStateRestorer() {
        this.playerRegistry = new HashMap<>();
        this.companyRegistry = new HashMap<>();
        this.privateRegistry = new HashMap<>();
        this.rotationNameToIntMap = new HashMap<>();
        this.ownerRegistry = new HashMap<>();
    }

    // FIX: Method signature uses GameStateData
    public RailsRoot restoreState(GameStateData pojoState) throws Exception {
        log.debug("--- Starting Game State Re-hydration ---");

        // 1. Initialize ConfigManager
        log.debug("Initializing ConfigManager for test mode...");
        ConfigManager.initConfiguration(true);
        String gameVariant = ConfigManager.getInstance().getValue("default_game", "1835");

        // 2. Create RailsRoot
        log.debug("Creating GameData for variant: {}", gameVariant);
        java.util.List<String> playerNames = pojoState.players.stream()
                .map(p -> p.id)
                .collect(Collectors.toList());
        GameInfo gameInfo = GameInfo.builder().withName(gameVariant).build();
        GameOptionsSet.Builder optionsBuilder = GameOptionsSet.builder();
        optionsBuilder.withNumberOfPlayers(playerNames.size());
        GameOptionsSet gameOptions = optionsBuilder.build();
        GameData gameData = GameData.create(gameInfo, optionsBuilder, playerNames);

        log.debug("Creating new RailsRoot via factory...");
        this.liveGameRoot = RailsRoot.create(gameData);

        buildRotationNameMap();

        // 3. Execute the 3-pass restoration
        pass1_PopulateRegistries(pojoState);
        pass2_PopulateSimpleData(pojoState);
        pass3_LinkReferences(pojoState);

        log.debug("--- Re-hydration Complete. Returning live RailsRoot. ---");
        return this.liveGameRoot;
    }

    private void buildRotationNameMap() {
        MapOrientation orientation = liveGameRoot.getMapManager().getMapOrientation();
        for (int i = 0; i < 6; i++) {
            HexSide side = HexSide.get(i);
            String name = orientation.getORNames(side);
            rotationNameToIntMap.put(name, i);
        }
        log.debug("Built rotation name map for orientation: {}", orientation);
    }

    /**
     * PASS 1: Find all live entities and build registries.
     */
    // FIX: Method signature uses GameStateData
    private void pass1_PopulateRegistries(GameStateData pojoState) {
        log.debug("[Pass 1] Populating entity registries...");

        // Players
        for (Player livePlayer : liveGameRoot.getPlayerManager().getPlayers()) {
            playerRegistry.put(livePlayer.getId(), livePlayer);
            ownerRegistry.put(livePlayer.getId(), livePlayer);
        }
        // Public Companies
        for (PublicCompany liveCompany : liveGameRoot.getCompanyManager().getAllPublicCompanies()) {
            companyRegistry.put(liveCompany.getId(), liveCompany);
            ownerRegistry.put(liveCompany.getId(), liveCompany);
        }
        // Private Companies
        for (PrivateCompany livePrivate : liveGameRoot.getCompanyManager().getAllPrivateCompanies()) {
            privateRegistry.put(livePrivate.getId(), livePrivate);
        }

        // Bank Portfolios (as Owners)
        Bank bank = liveGameRoot.getBank();
        ownerRegistry.put(bank.getIpo().getId(), bank.getIpo());
        ownerRegistry.put(bank.getPool().getId(), bank.getPool());
        ownerRegistry.put(bank.getScrapHeap().getId(), bank.getScrapHeap());
        ownerRegistry.put(bank.getUnavailable().getId(), bank.getUnavailable());
        ownerRegistry.put(bank.getId(), bank); // The Bank itself is an owner

        log.debug("[Pass 1] Found {} players, {} public companies, {} private companies.",
                playerRegistry.size(), companyRegistry.size(), privateRegistry.size());
    }

    /**
     * PASS 2: Populate all simple, non-relational data (cash, phase, map tiles).
     */

     // [REPLACE the entire 'pass2_PopulateSimpleData' method with this]
    private void pass2_PopulateSimpleData(GameStateData pojoState) {
        log.debug("[Pass 2] Populating simple data..."); // Use debug

        GameManager gm = liveGameRoot.getGameManager();
        Bank bank = liveGameRoot.getBank();

        // 2a. GameManager state
        log.debug("  - Setting GameManager state..."); // Use debug
        gm.setAbsoluteActionCounter_AI(pojoState.gameManager.absoluteActionCounter);
        gm.setStartRoundNumber_AI(pojoState.gameManager.startRoundNumber);
        gm.setStockRoundNumber_AI(pojoState.gameManager.stockRoundNumber);
        gm.setAbsoluteORNumber_AI(pojoState.gameManager.absoluteORNumber);
        gm.setRelativeORNumber_AI(pojoState.gameManager.relativeORNumber);

        // 2b. Phase
        log.debug("  - Setting game phase to: {}", pojoState.phase.currentPhaseName); // Use debug
        liveGameRoot.getPhaseManager().setPhase_AI(pojoState.phase.currentPhaseName);

        // 2c. Current Round state
        log.debug("  - Setting current round to: {} (Step: {})", pojoState.currentRound.id, pojoState.currentRound.step); // Use debug
        if (pojoState.currentRound.id != null && pojoState.currentRound.id.startsWith("OR_")) {
            OperatingRound liveOR = gm.createRound(gm.getOperatingRoundClass(), pojoState.currentRound.id);
            gm.setCurrentRound_AI(liveOR);
            if (pojoState.currentRound.step != null) {
                liveOR.setStep_AI(pojoState.currentRound.step);
            }
        } else if (pojoState.currentRound.id != null && pojoState.currentRound.id.startsWith("SR_")) {
            StockRound liveSR = gm.createRound(gm.getStockRoundClass(), pojoState.currentRound.id);
            gm.setCurrentRound_AI(liveSR);
        } else if (pojoState.currentRound.id != null && pojoState.currentRound.id.startsWith("IR_")) {
            // FIX: We must use the game's *specific* concrete StartRound class,
            // not the abstract base class.
            StartRound liveIR = gm.createRound("net.sf.rails.game.specific._1835.StartRound_1835", pojoState.currentRound.id);
//          StartRound liveIR = gm.createRound("net.sf.rails.game.StartRound", pojoState.currentRound.id);

            gm.setCurrentRound_AI(liveIR);
        } else {
            log.warn("Could not set round: Unrecognized round ID '{}'", pojoState.currentRound.id);
        }

        // 2d. Bank
        if (pojoState.bank != null) {
            log.debug("  - Setting Bank cash to: {}", pojoState.bank.cash); // Use debug
            bank.setCash_AI(pojoState.bank.cash);
        } else {
            log.debug("  - Bank data is null in JSON, skipping bank cash set."); // Use debug
        }

        // 2e. Player simple data
        log.debug("  - Setting Player simple data..."); // Use debug
        for (GameStateData.PlayerData playerData : pojoState.players) {
            Player livePlayer = playerRegistry.get(playerData.id);
            if (livePlayer != null) {
                livePlayer.setCash_AI(playerData.cash);
                livePlayer.getTimeBankModel().set(playerData.timeBankSeconds);
            }
        }

        // 2f. PublicCompany simple data
        log.debug("  - Setting PublicCompany simple data..."); // Use debug
        for (GameStateData.CompanyData companyData : pojoState.publicCompanies) {
            PublicCompany liveCompany = companyRegistry.get(companyData.id);
            if (liveCompany != null) {
                liveCompany.setCash_AI(companyData.cash);
                liveCompany.setHasFloated_AI(companyData.hasFloated);
                StockMarket sm = liveGameRoot.getStockMarket();
                if (companyData.stockRow > 0 || companyData.stockCol > 0) {
                    StockSpace space = sm.getStockSpace(companyData.stockRow, companyData.stockCol);
                    if (space != null) {
                        liveCompany.setCurrentSpace(space);
                    } else {
                        log.warn("Could not find StockSpace at [{}, {}] for company {}",
                                companyData.stockRow, companyData.stockCol, companyData.id);
                    }
                } else {
                    log.trace("Skipping stock price for unfloated company: {}", companyData.id);
                }
            }
        }
        
        // 2g. Map state -- MOVED TO PASS 3
    }

    
    /**
     * PASS 3: Relink all object references (Presidents, Owners, Certificates,
     * Trains).
     */
// [REPLACE the entire 'pass3_LinkReferences' method with this]
    private void pass3_LinkReferences(GameStateData pojoState) {
        log.debug("[Pass 3] Relinking object references...");
        GameManager gm = liveGameRoot.getGameManager();
        Bank bank = liveGameRoot.getBank();

        // 3a. GameManager references
        log.debug("  - Linking GameManager refs (CurrentPlayer, PriorityPlayer)...");
        liveGameRoot.getPlayerManager().setCurrentPlayer(playerRegistry.get(pojoState.gameManager.currentPlayerId));
        liveGameRoot.getPlayerManager().setPriorityPlayer(playerRegistry.get(pojoState.gameManager.priorityPlayerId));

        // 3b. CurrentRound operating company
        log.debug("  - Linking CurrentRound OperatingCompany...");
        if (gm.getCurrentRound() instanceof OperatingRound && pojoState.currentRound.operatingCompanyId != null) {
            PublicCompany opCo = companyRegistry.get(pojoState.currentRound.operatingCompanyId);
            if (opCo != null) {
                ((OperatingRound) gm.getCurrentRound()).setOperatingCompany_AI(opCo);
            }
        }

        // 3c. PublicCompany Presidents
        log.debug("  - Linking Company Presidents...");
        for (GameStateData.CompanyData companyData : pojoState.publicCompanies) {
            if (companyData.presidentId != null) {
                PublicCompany liveCompany = companyRegistry.get(companyData.id);
                Player livePresident = playerRegistry.get(companyData.presidentId);
                if (liveCompany != null && livePresident != null) {
                    liveCompany.setPresident(livePresident);
                }
            }
        }

        // 3d. PrivateCompany Owners
        log.debug("  - Linking PrivateCompany Owners...");
        for (GameStateData.PrivateCompanyData privateData : pojoState.privateCompanies) {
            PrivateCompany livePrivate = privateRegistry.get(privateData.id);
            Owner newOwner = ownerRegistry.get(privateData.ownerId);
            if (livePrivate != null && newOwner != null) {
                if (livePrivate.getOwner() != newOwner) {
                    livePrivate.moveTo(newOwner);
                }
            }
        }

        // 3e. Certificates
        log.debug("  - Distributing Certificates...");
        for (GameStateData.PlayerData playerData : pojoState.players) {
            Owner owner = ownerRegistry.get(playerData.id);
            for (GameStateData.CertificateData certData : playerData.certificates) {
                PublicCertificate liveCert = findCertificate(liveGameRoot, certData);
                if (liveCert != null) {
                    if (liveCert.getOwner() != owner) {
                        liveCert.moveTo(owner);
                    }
                }
            }
        }
        for (GameStateData.CompanyData companyData : pojoState.publicCompanies) {
            Owner owner = ownerRegistry.get(companyData.id);
            for (GameStateData.CertificateData certData : companyData.treasuryCertificates) {
                PublicCertificate liveCert = findCertificate(liveGameRoot, certData);
                if (liveCert != null) {
                    if (liveCert.getOwner() != owner) {
                        liveCert.moveTo(owner);
                    }
                }
            }
        }
        
        if (pojoState.bank != null && pojoState.bank.poolCertificates != null) {
            Owner targetOwner = bank.getPool();
            for (GameStateData.CertificateData certData : pojoState.bank.poolCertificates) {
                PublicCertificate liveCert = findCertificate(liveGameRoot, certData);
                if (liveCert != null) {
                    if (liveCert.getOwner() != targetOwner) {
                        liveCert.moveTo(targetOwner);
                    }
                }
            }
        } else {
            log.debug("  - No Bank Pool certificates to restore.");
        }

        // 3f. Trains
        log.debug("  - Distributing Trains...");
        TrainManager trainManager = liveGameRoot.getTrainManager();
        if (pojoState.trains != null) {
            for (GameStateData.TrainData trainData : pojoState.trains) {
                Train liveTrain = trainManager.getTrainByUniqueId(trainData.name);
                if (liveTrain == null) {
                    log.warn("Could not find live train for ID: {}", trainData.name);
                    continue;
                }
                TrainCard liveCard = liveTrain.getCard();
                if (liveCard == null) {
                     log.warn("Train {} has no parent TrainCard!", trainData.name);
                     continue;
                }
                Owner newOwner = ownerRegistry.get(trainData.ownerId); 
                if (newOwner == null) {
                    log.warn("Could not find new owner for train {}: {}", trainData.name, trainData.ownerId);
                    continue;
                }
                if (liveCard.getOwner() != newOwner) {
                    liveCard.moveTo(newOwner);
                }
            }
        } else {
            log.debug("  - No master train list found in JSON.");
        }

// --- START FIX (Map Restore Logic MOVED to Pass 3) ---
        // 3g. Map state (MOVED FROM PASS 2)
        log.debug("  - Restoring Map state ({} hexes)...", pojoState.map.hexes.size());
        MapManager mapManager = liveGameRoot.getMapManager();
        for (GameStateData.MapHexData hexData : pojoState.map.hexes) {
            if (hexData.tileId == null || hexData.tileId.isEmpty())
                continue;
            MapHex liveHex = mapManager.getHex(hexData.id);
            Tile tile = liveGameRoot.getTileManager().getTile(hexData.tileId);

            Integer rotationInt = rotationNameToIntMap.get(hexData.rotationName);
            if (rotationInt == null) {
                 log.warn("Failed to restore hex {}: Rotation name '{}' is invalid for current map orientation.",
                        hexData.id, hexData.rotationName);
                continue;
            }

            if (liveHex != null && tile != null) {
                // We create a dummy LayTile action to call hex.upgrade()
                // This call now works because the OperatingCompany (a dependency)
                // was set in step 3b.
                LayTile dummyAction = new LayTile(liveGameRoot, (Map<String,Integer>) null);
                dummyAction.setLaidTile(tile);
                dummyAction.setChosenHex(liveHex);
                dummyAction.setOrientation(rotationInt);

                // This call initializes the hex AND its Stops
                liveHex.upgrade(dummyAction);
                
                log.trace("Restored Hex {}: Tile={}, Rotation={}({})", hexData.id, tile.getId(),
                        rotationInt, hexData.rotationName);
            } else if (liveHex == null) {
                log.warn("Failed to find hex {}", hexData.id);
            } else if (tile == null) {
                log.warn("Failed to restore hex {}: Tile '{}' not found.", hexData.id, hexData.tileId);
            }
        }

        // 3h. Map Tokens (MOVED FROM 3g)
        log.debug("  - Placing Map Tokens...");
        for (GameStateData.MapHexData hexData : pojoState.map.hexes) {
            if (hexData.tokens == null || hexData.tokens.isEmpty()) {
                continue;
            }
            
            MapHex liveHex = mapManager.getHex(hexData.id);
            if (liveHex == null) continue;

            for (GameStateData.TokenData tokenData : hexData.tokens) {
                PublicCompany tokenCompany = companyRegistry.get(tokenData.companyId);
                if (tokenCompany == null) {
                    log.warn("Could not find company {} for token on hex {}", tokenData.companyId, hexData.id);
                    continue;
                }

                // Find the stop (which was created in 3g)
                Stop targetStop = null;
                for (Stop stop : liveHex.getStops()) {
                    if (stop.getRelatedStationNumber() == tokenData.stationIndex) {
                        targetStop = stop;
                        break;
                    }
                }
                
                if (targetStop == null) {
                    log.warn("Could not find stationIndex {} on hex {} for token", tokenData.stationIndex, hexData.id);
                    continue;
                }
                
                BaseToken liveToken = tokenCompany.getNextBaseToken();
                if (liveToken == null) {
                    log.warn("Company {} has no available tokens left in pool to restore to hex {}", tokenData.companyId, hexData.id);
                    continue;
                }
                
                liveToken.moveTo(targetStop);
            }
        }
    }
    

    /**
     * Helper to find the master PublicCertificate from the main game list.
     */
    // FIX: Method signature uses GameStateData.CertificateData
    private PublicCertificate findCertificate(RailsRoot root, GameStateData.CertificateData certData) {
        PublicCompany company = companyRegistry.get(certData.companyId);
        if (company == null) {
            log.warn("findCertificate: Could not find company {}", certData.companyId);
            return null;
        }

        // Iterate all master certificates for this company
        for (PublicCertificate cert : company.getCertificates()) {
            if (cert.isPresidentShare() == certData.isPresident &&
                    cert.getShare() == certData.percentage) {

                // Find the first matching cert that is still in a default bank portfolio
                Owner owner = cert.getOwner();
                Bank bank = root.getBank();
                if (owner == bank.getScrapHeap() || 
                    owner == bank.getIpo() || 
                    owner == bank.getPool() || 
                    owner == bank.getUnavailable()) 
                {
                     return cert;
                }
            }
        }

        log.error("findCertificate: Could not find an available master cert for {}% of {}", certData.percentage,
                certData.companyId);
        // Fallback: just return the first match, even if it's not in the scrapheap
        // (this may cause errors)
        for (PublicCertificate cert : company.getCertificates()) {
            if (cert.isPresidentShare() == certData.isPresident &&
                    cert.getShare() == certData.percentage) {
                log.warn("findCertificate: Using FALLBACK match for {}% cert of {}", certData.percentage,
                        certData.companyId);
                return cert;
            }
        }

        return null; // No match found at all
    }
}