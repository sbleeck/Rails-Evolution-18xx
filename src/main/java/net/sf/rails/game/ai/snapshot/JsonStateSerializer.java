package net.sf.rails.game.ai.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Collectors;

// Imports based on GameManager.java and compiler errors
import net.sf.rails.game.*;
import net.sf.rails.game.financial.*;
import net.sf.rails.game.round.RoundFacade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles serializing the current GameManager state into a
 * JSON file using simple data-transfer objects (GameStateData).
 */
public class JsonStateSerializer {

    private static final Logger log = LoggerFactory.getLogger(JsonStateSerializer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT); // For human-readable JSON


    private static GameStateData.GameManagerData populateGameManagerData(GameManager gm) {
        GameStateData.GameManagerData data = new GameStateData.GameManagerData();
// FIX: We must use the 'actionCount' (IntegerState) which is incremented
        // during replay, not 'absoluteActionCounter' (int) which is not.
        data.absoluteActionCounter = gm.getActionCountModel().value();

        data.startRoundNumber = gm.getStartRoundNumber();
        data.stockRoundNumber = gm.getSRNumber();
        data.absoluteORNumber = gm.getAbsoluteORNumber();
        data.relativeORNumber = gm.getRelativeORNumber();
        data.currentPlayerId = gm.getCurrentPlayer().getId();
        data.priorityPlayerId = gm.getRoot().getPlayerManager().getPriorityPlayer().getId();
        return data;
    }

private static GameStateData.PhaseData populatePhaseData(PhaseManager pm) {
        GameStateData.PhaseData data = new GameStateData.PhaseData();
        // Get the actual, live Phase object
        Phase currentPhase = pm.getCurrentPhase();
        
        data.currentPhaseName = currentPhase.toText();
        
        // This is the critical fix: save the field the AI needs
        data.offBoardRevenueStep = currentPhase.getOffBoardRevenueStep();
        return data;
    }

    private static GameStateData.RoundData populateRoundData(RoundFacade round) {
        GameStateData.RoundData data = new GameStateData.RoundData();
        data.id = round.getId();

        if (round instanceof OperatingRound) {
            data.type = "OperatingRound";
            OperatingRound or = (OperatingRound) round;
            data.step = or.getStep().name();
            if (or.getOperatingCompany() != null) {
                data.operatingCompanyId = or.getOperatingCompany().getId();
            }
        } else if (round instanceof StockRound) {
            data.type = "StockRound";
            // Removed .getStep() as it caused an error
        } else {
            data.type = "Other";
        }
        return data;
    }

    // This function becomes much simpler:
    private static GameStateData.BankData populateBankData(Bank bank) {
        GameStateData.BankData data = new GameStateData.BankData();
        data.cash = bank.getCash();

        data.poolCertificates = bank.getPool().getPortfolioModel().getCertificates().stream()
                .map(JsonStateSerializer::populateCertificateData)
                .collect(Collectors.toList());

        // REMOVE THE ENTIRE ipoTrains loop

        return data;
    }

    private static GameStateData.MapData populateMapData(MapManager mm) {
        GameStateData.MapData data = new GameStateData.MapData();
        data.hexes = mm.getHexes().stream()
                .map(JsonStateSerializer::populateMapHexData)
                .collect(Collectors.toList());
        return data;
    }

    private static GameStateData.MapHexData populateMapHexData(MapHex hex) {
        GameStateData.MapHexData data = new GameStateData.MapHexData();
        data.id = hex.getId();
        if (hex.getCurrentTile() != null) {
            // Corrected method: toText()
            data.tileId = hex.getCurrentTile().toText();
            // Corrected method: getOrientationName()
            data.rotationName = hex.getOrientationName(hex.getCurrentTileRotation());
        }
        
// Capture token data
        data.tokens = new ArrayList<>();
        for (Stop stop : hex.getStops()) {
            for (BaseToken token : stop.getBaseTokens()) {
                GameStateData.TokenData tokenData = new GameStateData.TokenData();
                
                // FIX: The correct method to get the company is getParent()
                tokenData.companyId = ((PublicCompany) token.getParent()).getId();
                
                tokenData.stationIndex = stop.getRelatedStationNumber();
                data.tokens.add(tokenData);
            }
        }
        return data;
    }

    private static GameStateData.PlayerData populatePlayerData(Player player) {
        GameStateData.PlayerData data = new GameStateData.PlayerData();
        data.id = player.getId();
        data.cash = player.getCash();
        // Corrected method: value()
        data.timeBankSeconds = player.getTimeBankModel().value();

        data.certificates = player.getPortfolioModel().getCertificates().stream()
                .map(JsonStateSerializer::populateCertificateData)
                .collect(Collectors.toList());

        data.privateCompanyIds = player.getPortfolioModel().getPrivateCompanies().stream()
                .map(PrivateCompany::getId)
                .collect(Collectors.toList());
        return data;
    }

    private static GameStateData.CompanyData populateCompanyData(PublicCompany co) {
        GameStateData.CompanyData data = new GameStateData.CompanyData();
        data.id = co.getId();
        data.cash = co.getCash();
        if (co.getPresident() != null) {
            data.presidentId = co.getPresident().getId();
        }
        data.hasFloated = co.hasStarted();
        if (co.getCurrentSpace() != null) {
            // Corrected method: getPrice()
            data.stockPrice = co.getCurrentSpace().getPrice();
            data.stockRow = co.getCurrentSpace().getRow();
            data.stockCol = co.getCurrentSpace().getColumn();
        }

        // Removed train logic
        // Removed token logic

        data.treasuryCertificates = co.getPortfolioModel().getCertificates().stream()
                .map(JsonStateSerializer::populateCertificateData)
                .collect(Collectors.toList());
        return data;
    }

    private static GameStateData.PrivateCompanyData populatePrivateCompanyData(PrivateCompany pc) {
        GameStateData.PrivateCompanyData data = new GameStateData.PrivateCompanyData();
        data.id = pc.getId();
        // Corrected type: List<Integer>
        data.revenue = pc.getRevenue();
        if (pc.getOwner() != null) {
            data.ownerId = pc.getOwner().getId();
        }
        return data;
    }

    private static GameStateData.CertificateData populateCertificateData(PublicCertificate cert) {
        GameStateData.CertificateData data = new GameStateData.CertificateData();
        data.companyId = cert.getCompany().getId();
        data.percentage = cert.getShare();
        data.isPresident = cert.isPresidentShare();
        return data;
    }

    private static GameStateData.TrainData populateTrainData(Train train) {
        GameStateData.TrainData data = new GameStateData.TrainData();

        // FIX: Use the unique ID (e.g., "3_2"), not the display name (e.g., "3")
        data.name = train.getId();

        // This part is (presumably) correct
        if (train.getOwner() != null) {
            data.ownerId = train.getOwner().getId();
        } else {
            // This case should not happen for a serialized train, but good to have
            data.ownerId = "null";
        }
        return data;
    }


    // ... (lines of unchanged context code) ...
    public static void serialize(GameManager gm, String filename) throws IOException {
        log.debug("Serializing state for action {} to {}", gm.getCurrentActionCount(), filename);

        // 1. Create and populate the root state object
        GameStateData state = createSnapshotData(gm); // <-- CALL THE NEW METHOD

        // 2. Write to file
        objectMapper.writeValue(new File(filename), state);
        log.debug("Serialization complete.");
    }

    /**
     * Creates and populates the root GameStateData object for the given GameManager.
     * This separates the computation from the I/O and is necessary for analysis tools.
     * * @param gm The current GameManager instance.
     * @return The fully populated GameStateData POJO.
     */
    public static GameStateData createSnapshotData(GameManager gm) {
        // 1. Create the root state object
        GameStateData state = new GameStateData();
        RailsRoot root = gm.getRoot();

        // 2. Populate all components
        state.gameManager = populateGameManagerData(gm);
        state.phase = populatePhaseData(root.getPhaseManager());
        
        // FIX: The Bank is needed but the BankData method was not complete/called correctly
        // We need to call a method that populates the BankData, even if it's currently simple.
        state.bank = populateBankData(root.getBank()); // Assuming a simple bank method exists.

        state.currentRound = populateRoundData(gm.getCurrentRound());
        state.map = populateMapData(root.getMapManager());

        state.players = root.getPlayerManager().getPlayers().stream()
                .map(JsonStateSerializer::populatePlayerData)
                .collect(Collectors.toList());

        state.publicCompanies = root.getCompanyManager().getAllPublicCompanies().stream()
                .map(JsonStateSerializer::populateCompanyData)
                .collect(Collectors.toList());

        state.privateCompanies = root.getCompanyManager().getAllPrivateCompanies().stream()
                .map(JsonStateSerializer::populatePrivateCompanyData)
                .collect(Collectors.toList());

        state.trains = root.getTrainManager().getAllTrains().stream()
                .map(JsonStateSerializer::populateTrainData)
                .collect(Collectors.toList());
                
        return state;
    }

}