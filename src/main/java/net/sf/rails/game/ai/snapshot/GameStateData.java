package net.sf.rails.game.ai.snapshot;

import java.util.List;
import java.util.Map;


/**
 * Root data structure for serializing the complete game state.
 * Contains no logic, only fields for JSON serialization.
 */
public class GameStateData {

    // Game-level info
    public GameManagerData gameManager;
    public PhaseData phase;
    public RoundData currentRound;
    public BankData bank;
    public MapData map;
    public List<TrainData> trains; // <-- ADD THIS MASTER LIST
    
    // Actor info
    public List<PlayerData> players;
    public List<CompanyData> publicCompanies;
    public List<PrivateCompanyData> privateCompanies;

    // ----- NESTED DATA CLASSES -----

    public static class GameManagerData {
        public int absoluteActionCounter;
        public int startRoundNumber;
        public int stockRoundNumber;
        public int absoluteORNumber;
        public int relativeORNumber;
        public String currentPlayerId;
        public String priorityPlayerId;
    }

    public static class PhaseData {
        public String currentPhaseName;
        public int offBoardRevenueStep;
    }

    public static class RoundData {
        public String type; // "StockRound", "OperatingRound", "StartRound"
        public String id;   // e.g., "OR_1.1"
        public String step; // e.g., "LAY_TILE", "BUY_TRAIN"
        public String operatingCompanyId; // Null if not OR
    }

    public static class BankData {
        public int cash;
        public List<CertificateData> poolCertificates;
    }

    public static class MapData {
        public List<MapHexData> hexes;
    }

    public static class MapHexData {
        public String id; // "H12"
        public String tileId; // "57"
        public String rotationName; // e.g., "NORTH"
        // A list of tokens on this hex, e.g., ["id":"BY", "stationIndex":1]
        public List<TokenData> tokens;
    }

    public static class PlayerData {
        public String id;
        public int cash;
        public String fullName;
        public List<CertificateData> certificates;
        public List<String> privateCompanyIds; // IDs of privates owned
        public int timeBankSeconds;
    }

    public static class CompanyData {
        public String id;
        public int cash;
        public String presidentId;
        public boolean hasFloated;
        public int stockPrice; // e.g., 100
        public int stockRow;
        public int stockCol;
        public List<CertificateData> treasuryCertificates;
    }

    public static class PrivateCompanyData {
        public String id;
        public String ownerId; // Player or Company ID
        public List<Integer> revenue;
    }

    public static class CertificateData {
        public String companyId;
        public int percentage;
        public boolean isPresident;
    }

    public static class TrainData {
        public String name; // "3", "4+4"
        public String ownerId; // ID of company that owns it, or "Bank" / "Pool"
    }
    public static class TokenData {
        public String companyId;     // The ID of the company that owns the token (e.g., "BY")
        public int stationIndex; // The station number (1, 2, etc.) it's placed on
    }
}