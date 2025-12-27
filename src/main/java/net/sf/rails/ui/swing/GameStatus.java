package net.sf.rails.ui.swing;

import com.google.common.collect.Lists;
import net.sf.rails.common.GameOption;
import net.sf.rails.common.GuiDef;
import net.sf.rails.common.LocalText;
import net.sf.rails.game.Player;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.financial.Bank;
import net.sf.rails.game.financial.PublicCertificate;
import net.sf.rails.game.financial.StockRound;
import net.sf.rails.game.model.PortfolioModel;
import net.sf.rails.game.state.IntegerState;
import net.sf.rails.game.state.MoneyOwner;
import net.sf.rails.sound.SoundManager;
import net.sf.rails.ui.swing.elements.ActionButton;
import net.sf.rails.ui.swing.elements.Caption;
import net.sf.rails.ui.swing.elements.ClickField;
import net.sf.rails.ui.swing.elements.Field;
import net.sf.rails.ui.swing.elements.RadioButtonDialog;
import net.sf.rails.ui.swing.elements.TimeField;
import net.sf.rails.ui.swing.elements.TokenIcon;
import net.sf.rails.ui.swing.hexmap.HexHighlightMouseListener;
import net.sf.rails.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rails.game.action.*;
import rails.game.correct.CashCorrectionAction;
import rails.game.correct.CorrectionType;
import rails.game.correct.TrainCorrectionAction;
import rails.game.specific._18EU.StartCompany_18EU;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import net.sf.rails.ui.swing.elements.RailCard;

import java.util.Map;

/**
 * This class is incorporated into StatusWindow and displays the bulk of
 * rails.game status information.
 */
public class GameStatus extends GridPanel implements ActionListener {

    // Width Definitions
    // --- START FIX ---
    // STRUCTURAL CONSTANTS FOR "SLOT" SYSTEM
    private final Dimension DIM_STD = new Dimension(45, 25); // The Immutable Card Size
  private final Dimension DIM_MINOR = new Dimension(60, 25); // Full Width Card (Minors)
    private final Dimension DIM_DOT = new Dimension(12, 25); // The Fixed Red Dot Column
    private final Dimension DIM_PRICE = new Dimension(35, 25); // The Fixed Price Column

    // Combined Slot Sizes (Card + Indicator)
    private final Dimension DIM_PLAYER_SLOT = new Dimension(60, 25); // 40 + 12
    private final Dimension DIM_POOL_SLOT = new Dimension(75, 25); // 40 + 35

    // Width Definitions
    private final Dimension DIM_PLAYER = new Dimension(60, 25); // Wider Players (Point 6)
    private final Dimension DIM_MERGED = new Dimension(70, 25);
    private final Dimension DIM_TOKENS = new Dimension(75, 25); // Required for 4x16 dots + 3x3 gap

    // Flexible height for privates (Point 4b) - Width 85, Height 0 (auto)
    private final Dimension DIM_TRAIN = new Dimension(100, 30); // Larger container

    public static final Color BG_BUY_ACTIVE = new Color(144, 238, 144); // Light Green (#90EE90) - Standard "Buy"
    private final Color BG_SELL_ALERT = new Color(250, 128, 114); // Salmon Pink (#FA8072) - Shares Sell
    public static final Color BG_CARD_PASSIVE = new Color(255, 255, 240); // Beige (Must be static for static method
                                                                          // usage)

    private final Color BG_INACTIVE = new Color(235, 235, 235); // Constant Grey (Point 9)
    private final Color BG_POOL = new Color(230, 240, 255);
    private final Color BG_IPO = new Color(255, 235, 235); // Distinct Reddish/Pink (Point 13)

    private final Color BG_MAUVE = new Color(235, 230, 255); // Standard Company Data Background
    private final Color BG_OPERATING = new Color(255, 255, 200); // Yellow

    // Promoted from initTurn to avoid shadowing/reallocation
    private final Color BG_PASSED = new Color(150, 255, 150);
    private final Color BG_CERT_OK = new Color(200, 255, 200);
    private final Color BG_CERT_LIMIT = new Color(255, 200, 200);

    // Alias for Share Buying to match Trains
    final Color BG_BUY = BG_BUY_ACTIVE; // Use Light Green
    final Color BG_SELL = BG_SELL_ALERT; // Use Muted Rose Red

    final Color BG_SLOT_AVAILABLE = new Color(220, 255, 220);

    // 2. Larger Train Cards (60x30)
    private static final Dimension DIM_TRAIN_BTN = new Dimension(42, 22);

    private static final long serialVersionUID = 1L;

    protected static final String BUY_FROM_IPO_CMD = "BuyFromIPO";
    protected static final String BUY_FROM_POOL_CMD = "BuyFromPool";
    protected static final String SELL_CMD = "Sell";
    protected static final String CASH_CORRECT_CMD = "CorrectCash";

    protected StatusWindow parent;

    // Grid elements per function
    protected Field[] currentSharesNumber;
    protected int currentShareNumberXOffset, currentShareNumberYOffset;
    protected Field[][] certPerPlayer;
    protected ClickField[][] certPerPlayerButton;
    protected int certPerPlayerXOffset, certPerPlayerYOffset;
    protected Field[] certInIPO;
    protected ClickField[] certInIPOButton;
    protected int certInIPOXOffset, certInIPOYOffset;
    protected Field[] certInPool;
    protected ClickField[] certInPoolButton;
    protected int certInPoolXOffset, certInPoolYOffset;
    protected Field[] certInTreasury;
    protected ClickField[] certInTreasuryButton;
    protected int certInTreasuryXOffset, certInTreasuryYOffset;
    protected Field[] parPrice;
    protected int parPriceXOffset, parPriceYOffset;
    protected Field[] currPrice;
    protected int currPriceXOffset, currPriceYOffset;
    protected Field[][] bondsPerPlayer;
    protected ClickField[][] bondsPerPlayerButton;
    protected Field[] bondsInIPO;
    protected ClickField[] bondsInIPOButton;
    protected Field[] bondsInPool;
    protected ClickField[] bondsInPoolButton;
    protected Field[] bondsInTreasury;
    protected ClickField[] bondsInTreasuryButton;
    protected Field[] compCash;
    protected ClickField[] compCashButton;
    protected int compCashXOffset, compCashYOffset;
    protected Field[] compRevenue;
    protected int compRevenueXOffset, compRevenueYOffset;
    protected Field[] compTrains;
    protected int compTrainsXOffset, compTrainsYOffset;
    protected JPanel[] compTokens;
    protected Caption[] compArrowCaption; // Store references to the arrows
    protected Caption[] compNameCaption;
    protected int compTokensXOffset, compTokensYOffset;
    protected Field[] compPrivates;
    protected int compPrivatesXOffset, compPrivatesYOffset;
    protected Field[] compLoans;
    protected int compLoansXOffset, compLoansYOffset;
    protected int rightsXOffset, rightsYOffset;
    protected Field[] rights;
    protected Field[] playerCash;
    protected ClickField[] playerCashButton;
    protected int playerCashXOffset, playerCashYOffset;

    protected JPanel[] playerPrivatesPanel;
    protected int playerPrivatesXOffset, playerPrivatesYOffset;
    protected Field[] playerWorth;
    protected int playerWorthXOffset, playerWorthYOffset;
    protected Field[] playerORWorthIncrease;
    protected int playerORWorthIncreaseXOffset, playerORWorthIncreaseYOffset;
    protected Field[] playerCertCount;
    protected int playerCertCountXOffset, playerCertCountYOffset;
    protected int certLimitXOffset, certLimitYOffset;
    protected int phaseXOffset, phaseYOffset;
    protected Field bankCash;
    protected int bankCashXOffset, bankCashYOffset;
    protected int poolTrainsXOffset, poolTrainsYOffset;
    protected int newTrainsXOffset, newTrainsYOffset;
    protected int futureTrainsXOffset, futureTrainsYOffset, futureTrainsWidth;
    protected int rightCompCaptionXOffset;

    private final int MAX_TRAIN_SLOTS = 4; // Max trains to display per company
    private final int MAX_FUTURE_SLOTS = 7; // Max future trains to display

    // Track previous times to detect jumps (penalties/undo)
    private int[] lastPlayerTimes;

    protected ClickField poolTrainsButton;
    protected javax.swing.JLabel gameTimeLabel;
    protected javax.swing.Timer uiRefreshTimer;
    private javax.swing.JLabel parentTimerLabel = null;
    private javax.swing.JLabel parentStatusLabel = null;
    private String lastThinkingText = "";

    /**
     * * Scans the parent StatusWindow to find the "Thinking" label and the Main
     * Timer label.
     */
    private void hijackParentComponents() {
        if (parent == null)
            return;

        // Traverse component tree
        java.util.Queue<Component> queue = new java.util.LinkedList<>();
        queue.add(parent);

        while (!queue.isEmpty()) {
            Component comp = queue.poll();

            if (comp instanceof javax.swing.JLabel) {
                javax.swing.JLabel lbl = (javax.swing.JLabel) comp;
                String text = lbl.getText();
                Color fg = lbl.getForeground();

                // 1. Identify "Thinking" Label (Usually Red)
                if (parentStatusLabel == null
                        && (Color.RED.equals(fg) || (text != null && text.startsWith("Thinking")))) {
                    parentStatusLabel = lbl;
                }

                // 2. Identify Timer Label (Usually Top Right, Black, contains :)
                // We check if it looks like a time string and is NOT the status label
                if (parentTimerLabel == null && lbl != parentStatusLabel && text != null
                        && text.matches(".*\\d\\d:\\d\\d.*")) {
                    parentTimerLabel = lbl;
                }
            }

            if (comp instanceof Container) {
                for (Component child : ((Container) comp).getComponents()) {
                    queue.add(child);
                }
            }
        }
    }

    protected ClickField newTrainsButton;

    // New Containers for Train UI (Pool & IPO)

    // Buttons within those containers
    protected javax.swing.JLabel newTrainQtyLabel; // The "Qty: 2" text below

    protected ClickField futureTrainsButton;
    // Config
    private final int MAX_POOL_SLOTS = 4;

    // 1. Restore the missing panel array
    protected JPanel[] compTrainsButtonPanel;

    // 2. Define Train Buttons as RailCards
    protected RailCard[][] compSubTrainButtons;
    protected RailCard[] poolTrainButtons;
    protected RailCard newTrainButton;
    protected RailCard[] futureTrainButtons;

    // 3. Labels and Panels
    protected JPanel poolTrainsPanel;
    protected JPanel newTrainsPanel;
    protected JPanel futureTrainsPanel;
    protected javax.swing.JLabel newTrainInfoLabel;
    protected javax.swing.JLabel[] futureTrainInfoLabels;

    /**
     * Next Field is needed for the direct payment of Income and display of sum
     * during an OR for a Company, that is not linked to a share.
     */
    private Field[] compDirectRevenue;
    private int compDirectRevXOffset, compDirectRevYOffset;

    protected Caption[] upperPlayerCaption;
    protected Caption treasurySharesCaption;

    protected PortfolioModel ipo, pool;

    protected GameUIManager gameUIManager;
    protected Bank bank;

    protected PossibleActions possibleActions;

    protected boolean hasParPrices = false;
    protected boolean compCanBuyPrivates = false;
    protected boolean compCanHoldOwnShares = false;
    protected boolean compCanHoldForeignShares = false; // NOT YET USED
    protected boolean hasCompanyLoans = false;
    protected boolean hasBonds = false;
    protected boolean hasRights;
    private boolean hasDirectCompanyIncomeInOr = false;
    protected boolean needsNumberOfSharesColumn = false;
    protected int playerFixedIncomeXOffset, playerFixedIncomeYOffset;
    protected int playerStartOrderXOffset, playerStartOrderYOffset;
    protected javax.swing.JLabel nextPlayerLabel; // Green text for next player

    // Current actor.
    // Players: 0, 1, 2, ...
    // Company (from treasury): -1.
    protected int actorIndex = -2;

    protected int compNameCol;

    protected int nc;
    protected PublicCompany[] companies;
    protected int np; // Number of players
    private int nb = 0; // Number of extra Bond lines
    private int y; // Actual number of each company row, including any extra Bond rows
    protected Map<PublicCompany, Integer> companyCertRow = new HashMap<>();
    protected Map<PublicCompany, Integer> companyBondsRow = new HashMap<>();

    protected final ButtonGroup buySellGroup = new ButtonGroup();
    protected ClickField dummyButton; // To be selected if none else is.

    private static final Logger log = LoggerFactory.getLogger(GameStatus.class);
    protected Field[] playerFixedIncome; // Add this to your class variables
    protected Field[] playerStartOrder; // Add this to your class variables

    protected JPanel[] ipoPanels;
    protected RailCard[] ipoShareCards;
    protected javax.swing.JLabel[] ipoParLabels;

    protected JPanel[] poolPanels;
    protected RailCard[] poolShareCards;
    protected javax.swing.JLabel[] poolPriceLabels;

    protected JPanel[][] playerSharePanels;
    protected RailCard[][] playerShareCards;
    protected javax.swing.JLabel[][] playerSoldDots;

    public GameStatus() {
        super();
    }

    // Near other player-related Field declarations (around line 170)
    protected Field[] playerTimer;
    protected int playerTimerXOffset, playerTimerYOffset;

    public void init(StatusWindow parent, GameUIManager gameUIManager) {

        /* Initialise basic data */
        this.parent = parent;
        this.gameUIManager = gameUIManager;
        bank = gameUIManager.getRoot().getBank();
        possibleActions = gameUIManager.getGameManager().getPossibleActions();

        gridPanel = this;
        parentFrame = parent;


        gb = new GridBagLayout();
        this.setLayout(gb);
        UIManager.put("ToggleButton.select", buttonHighlight);

        gbc = new GridBagConstraints();
        setSize(800, 300);
        setLocation(0, 450);
        setBorder(BorderFactory.createEtchedBorder());
        setOpaque(false);

        players = gameUIManager.getPlayerManager();

        companies = gameUIManager.getAllPublicCompanies().toArray(new PublicCompany[0]);
        nc = companies.length;
        // How many Bond rows do we need?
        for (PublicCompany c : companies) {
            if (c.hasBonds())
                nb++;
        }
        np = players.getNumberOfPlayers();

        /* Set game parameters required here */
        hasParPrices = gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.HAS_ANY_PAR_PRICE);
        compCanBuyPrivates = gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.CAN_ANY_COMPANY_BUY_PRIVATES);
        compCanHoldOwnShares = gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.CAN_ANY_COMPANY_HOLD_OWN_SHARES);
        hasCompanyLoans = gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.HAS_ANY_COMPANY_LOANS);
        hasRights = gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.HAS_ANY_RIGHTS);
        hasBonds = gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.HAS_BONDS);
        hasDirectCompanyIncomeInOr = gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.HAS_SPECIAL_COMPANY_INCOME);
        needsNumberOfSharesColumn = gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.HAS_GROWING_NUMBER_OF_SHARES);

        // TODO: Can this be done using ipo and pool directly?
        ipo = bank.getIpo().getPortfolioModel();
        pool = bank.getPool().getPortfolioModel();

        /* Initialise dynamic data displayers */
        if (needsNumberOfSharesColumn)
            currentSharesNumber = new Field[nc];
        certPerPlayer = new Field[nc][np];
        certPerPlayerButton = new ClickField[nc][np];
        certInIPO = new Field[nc];
        certInIPOButton = new ClickField[nc];
        certInPool = new Field[nc];
        certInPoolButton = new ClickField[nc];
        if (compCanHoldOwnShares) {
            certInTreasury = new Field[nc];
            certInTreasuryButton = new ClickField[nc];
        }
        parPrice = new Field[nc];
        currPrice = new Field[nc];
        if (hasBonds) {
            bondsPerPlayer = new Field[nc][np];
            bondsPerPlayerButton = new ClickField[nc][np];
            bondsInIPO = new Field[nc];
            bondsInIPOButton = new ClickField[nc];
            bondsInPool = new Field[nc];
            bondsInPoolButton = new ClickField[nc];
            bondsInTreasury = new Field[nc];
            bondsInTreasuryButton = new ClickField[nc];
        }
        compCash = new Field[nc];
        compCashButton = new ClickField[nc];
        compRevenue = new Field[nc];
        compTrains = new Field[nc];
        compTrainsButtonPanel = new JPanel[nc];
        compSubTrainButtons = new RailCard[nc][MAX_TRAIN_SLOTS];
        compTokens = new JPanel[nc];
        compPrivates = new Field[nc];
        compLoans = new Field[nc];
        if (hasRights)
            rights = new Field[nc];
        if (hasDirectCompanyIncomeInOr)
            compDirectRevenue = new Field[nc];

        playerCash = new Field[np];
        playerCashButton = new ClickField[np];
        playerWorth = new Field[np];
        playerORWorthIncrease = new Field[np];
        playerCertCount = new Field[np];
        playerTimer = new Field[np];
        upperPlayerCaption = new Caption[np];

        /* Set company and player/company field locations */
        int lastX = 0; // Current column number
        int lastY = 1; // Current row number
        if (needsNumberOfSharesColumn) {
            currentShareNumberXOffset = ++lastX;
            currentShareNumberYOffset = lastY + 1;
        }
        certPerPlayerXOffset = ++lastX;
        certPerPlayerYOffset = ++lastY;
        certInIPOXOffset = (lastX += np);
        certInIPOYOffset = lastY;
        certInPoolXOffset = ++lastX;
        certInPoolYOffset = lastY;
        if (compCanHoldOwnShares) {
            certInTreasuryXOffset = ++lastX;
            certInTreasuryYOffset = lastY;
        }
        if (hasParPrices) {
            parPriceXOffset = ++lastX;
            parPriceYOffset = lastY;
        }
        currPriceXOffset = ++lastX;
        currPriceYOffset = lastY;
        compCashXOffset = ++lastX;
        compCashYOffset = lastY;
        compRevenueXOffset = ++lastX;
        compRevenueYOffset = lastY;
        if (hasDirectCompanyIncomeInOr) {
            compDirectRevXOffset = ++lastX;
            compDirectRevYOffset = lastY;
        }
        compTrainsXOffset = ++lastX;
        compTrainsYOffset = lastY;
        compTokensXOffset = ++lastX;
        compTokensYOffset = lastY;
        if (compCanBuyPrivates) {
            compPrivatesXOffset = ++lastX;
            compPrivatesYOffset = lastY;
        }
        if (hasCompanyLoans) {
            compLoansXOffset = ++lastX;
            compLoansYOffset = lastY;
        }
        if (hasRights) {
            rightsXOffset = ++lastX;
            rightsYOffset = lastY;
        }
        rightCompCaptionXOffset = ++lastX;

        /* Set additional player field locations */
        playerCashXOffset = certPerPlayerXOffset;
        playerCashYOffset = lastY += (nc + nb);
        playerPrivatesXOffset = certPerPlayerXOffset;
        playerPrivatesYOffset = ++lastY;
        playerWorthXOffset = certPerPlayerXOffset;
        playerWorthYOffset = ++lastY;
        playerORWorthIncreaseXOffset = certPerPlayerXOffset;
        playerORWorthIncreaseYOffset = ++lastY;
        playerCertCountXOffset = certPerPlayerXOffset;
        playerCertCountYOffset = ++lastY;

        // NEW TIMER ROW LOCATION
        playerTimerXOffset = certPerPlayerXOffset;
        playerTimerYOffset = ++lastY;

        // SHIFTED ROWS (Fields depending on the final Y-offset must be updated to align
        // with the new playerTimerYOffset)
        certLimitXOffset = certInPoolXOffset;
        certLimitYOffset = playerCertCountYOffset; // Shifted
        phaseXOffset = certInPoolXOffset + 2;
        phaseYOffset = playerCertCountYOffset; // Shifted
        bankCashXOffset = certInPoolXOffset;
        bankCashYOffset = playerPrivatesYOffset;
        poolTrainsXOffset = bankCashXOffset + 2;
        poolTrainsYOffset = playerPrivatesYOffset;
        newTrainsXOffset = poolTrainsXOffset + 1;
        newTrainsYOffset = playerPrivatesYOffset;
        futureTrainsXOffset = newTrainsXOffset + 1;
        futureTrainsYOffset = playerPrivatesYOffset;
        futureTrainsWidth = rightCompCaptionXOffset - futureTrainsXOffset;

        fields = new JComponent[1 + lastX][2 + lastY];
        shareRowVisibilityObservers = new RowVisibility[nc];
        bondsRowVisibilityObservers = new RowVisibility[nc];

        playerCash = new Field[np];
        playerCashButton = new ClickField[np];

        playerFixedIncome = new Field[np];
        playerStartOrder = new Field[np];

        playerWorth = new Field[np];
        playerORWorthIncrease = new Field[np];
        playerCertCount = new Field[np];
        // Initialize time tracking array and pull current time values
        lastPlayerTimes = new int[np];
        for (int i = 0; i < np; i++) {
            // Pull the current, potentially already-bonused time from the model
            lastPlayerTimes[i] = players.getPlayerByPosition(i).getTimeBankModel().value();
        }

        initFields();
        javax.swing.SwingUtilities.invokeLater(this::hijackParentComponents);
    }

    /**
     * Bypasses the time jump detection logic. Used by the Undo system
     * to prevent the UI from misinterpreting time restoration as a bonus (Green
     * Flash).
     */
    public void setPlayerTimeWithoutDeltaCheck(final int playerIndex, final int newTime) {
        if (playerTimer == null || playerIndex < 0 || playerIndex >= playerTimer.length) {
            return;
        }

        final Field timerField = playerTimer[playerIndex];
        if (timerField == null) {
            return;
        }

        // Critical: Update stored time BEFORE the UI thread runs
        lastPlayerTimes[playerIndex] = newTime;

        // Pure Text Update (No Flashing)
        SwingUtilities.invokeLater(() -> {
            timerField.setText(String.valueOf(newTime));

            // RED TEXT for negative values (Bottom Grid)
            if (newTime < 0) {
                timerField.setForeground(Color.RED);
            } else {
                timerField.setForeground(Color.BLACK);
            }

            repaint();
        });
    }

    /**
     * Resets the time history for a specific player.
     * Called during Undo to prevent the UI from misinterpreting a state rollback as
     * a time bonus.
     */
    public void resetTimeHistory(int playerIndex) {
        if (lastPlayerTimes != null && playerIndex >= 0 && playerIndex < lastPlayerTimes.length) {
            lastPlayerTimes[playerIndex] = Integer.MIN_VALUE;
        }
    }

    public void initBondsRow(int i, PublicCompany c, boolean visible) {

        companyBondsRow.put(c, y);
        bondsRowVisibilityObservers[i] = new RowVisibility(
                this, y,
                c.getInGameModel());

        f = new Caption("  -" + LocalText.getText("bonds"));
        f.setForeground(c.getFgColour());
        f.setBackground(c.getBgColour());
        addField(f, 0, y, 1, 1, 0, visible);

        if (needsNumberOfSharesColumn) {
            f = new Caption(String.valueOf(c.getNumberOfBonds()));
            addField(f, currentShareNumberXOffset, y,
                    1, 1, WIDE_LEFT, visible);
        }
        for (int j = 0; j < np; j++) {
            Player player = players.getPlayerByPosition(j);

            f = bondsPerPlayer[i][j] = new Field(player.getPortfolioModel().getBondsModel(c));
            ((Field) f).setColorModel(player.getSoldThisRoundModel(c));
            int wideGapPosition = ((j == 0) ? WIDE_LEFT : 0) + ((j == np - 1) ? WIDE_RIGHT : 0);
            addField(f, certPerPlayerXOffset + j, y,
                    1, 1, wideGapPosition, visible);
            // TODO: Simplify the assignment (using f as correct local variable)
            f = bondsPerPlayerButton[i][j] = new ClickField("", SELL_CMD,
                    LocalText.getText("ClickForSell"),
                    this, buySellGroup);
            addField(f, certPerPlayerXOffset + j, y,
                    1, 1, wideGapPosition, false);
        }

        f = bondsInIPO[i] = new Field(ipo.getBondsModel(c));
        addField(f, certInIPOXOffset, y, 1, 1, 0, visible);
        f = bondsInIPOButton[i] = new ClickField(
                bondsInIPO[i].getText(),
                BUY_FROM_IPO_CMD,
                LocalText.getText("ClickToSelectForBuying"),
                this, buySellGroup);
        addField(f, certInIPOXOffset, y, 1, 1, 0, false);

        f = bondsInPool[i] = new Field(pool.getBondsModel(c));
        addField(f, certInPoolXOffset, y, 1, 1,
                WIDE_RIGHT, visible);
        f = bondsInPoolButton[i] = new ClickField(
                bondsInPool[i].getText(),
                BUY_FROM_POOL_CMD,
                LocalText.getText("ClickToSelectForBuying"),
                this, buySellGroup);
        addField(f, certInPoolXOffset, y, 1, 1,
                WIDE_RIGHT, false);

        if (compCanHoldOwnShares) {
            f = bondsInTreasury[i] = new Field(c.getPortfolioModel().getBondsModel(c));
            addField(f, certInTreasuryXOffset, y,
                    1, 1, WIDE_RIGHT, visible);
            f = bondsInTreasuryButton[i] = new ClickField(
                    certInTreasury[i].getText(),
                    BUY_FROM_POOL_CMD,
                    LocalText.getText("ClickForSell"),
                    this, buySellGroup);
            addField(f, certInTreasuryXOffset, y,
                    1, 1, WIDE_RIGHT, false);
            bondsInTreasury[i].setPreferredSize(bondsInTreasuryButton[i].getPreferredSize());
        }

        // if (this.hasParPrices) {
        // f = new Caption(" ");
        // f.setBackground(Color.WHITE);
        // addField(f, parPriceXOffset, y, 1, 1, 0, visible);
        // }

        // f = new Caption(c.getFormattedPriceOfBonds());
        // f.setBackground(Color.WHITE);
        // addField(f, currPriceXOffset, y, 1, 1,
        // WIDE_RIGHT, visible);

        f = new Caption(" ");
        f.setBackground(Color.WHITE);
        addField(f, futureTrainsXOffset, y, futureTrainsWidth, 1, 0, true);

        f = new Caption("  -bonds");
        f.setForeground(c.getFgColour());
        f.setBackground(c.getBgColour());
        addField(f, rightCompCaptionXOffset, y, 1, 1, WIDE_LEFT, visible);
    }

    public void recreate() {
        // log.debug("GameStatus.recreate() called");

        // Refresh Snapshots and Force Layout
        if (gameUIManager != null) {
            players = gameUIManager.getPlayerManager();
            companies = gameUIManager.getAllPublicCompanies().toArray(new PublicCompany[0]);
            nc = companies.length;

            nb = 0;
            for (PublicCompany c : companies) {
                if (c.hasBonds())
                    nb++;
            }
            np = players.getNumberOfPlayers();
        }

        deRegisterObservers();
        removeAll();
        initFields();

// initFields() creates the RailCards but leaves them empty/passive (Beige).
        // We must run initTurn immediately to populate the text/visibility 
        // (e.g. "10%", "Owner") so the user never sees the empty state.
        
        int currentActor = -1;
        boolean isLocal = false;
        
        if (players != null && players.getCurrentPlayer() != null) {
            currentActor = players.getCurrentPlayer().getIndex();
        } else {
             currentActor = this.actorIndex;
        }
        

        // Run the visual update immediately
        initTurn(currentActor, false);

        // Force the layout manager to recalculate constraints
        revalidate();
        repaint();
    }


    public void updatePlayerOrder(List<String> newPlayerNames) {
        List<String> oldPlayerNames = gameUIManager.getCurrentGuiPlayerNames();
        // log.debug("GS: old player list: {}", Util.join(oldPlayerNames.toArray(new
        // String[0]), ","));
        // log.debug("GS: new player list: {}", Util.join(newPlayerNames.toArray(new
        // String[0]), ","));
        /*
         * Currently, the passed new player order is ignored.
         * A call to this method only serves as a signal to rebuild the player columns
         * in the proper order
         * (in fact, the shortcut is taken to rebuild the whole GameStatus panel).
         * For simplicity reasons, the existing reference to the (updated)
         * players list in GameManager is used.
         *
         * In the future (e.g. when implementing a client/server split),
         * newPlayerNames may actually become to be used to reorder the
         * (then internal) UI player list.
         */
        recreate();
        gameUIManager.packAndApplySizing(parent);
    }

    /**
     * Setup a button for buying share(s) to start a new company, usually the
     * President's share.
     * Extracted from actionPerformed() to allow overriding, as required for SOH,
     * where all shares to float a company must be bought as one StartCompany
     * action.
     * 
     * @param buy        A StartCompany action object
     * @param buyActions List of BuyCertificate actions
     * @param buyAmounts Price of BuyCertificate actions
     * @param options    Text to display with each possible initial share price
     */
    protected void setupStartCompany(StartCompany buy, List<BuyCertificate> buyActions,
            List<Integer> buyAmounts, List<String> options) {
        int[] startPrices;
        PublicCompany company = buy.getCompany();
        if (buy.mustSelectAPrice()) {
            startPrices = buy.getStartPrices();
            Arrays.sort(startPrices);
            if (startPrices.length > 1) {
                for (int startPrice : startPrices) {
                    options.add(LocalText.getText("StartCompany",
                            gameUIManager.format(startPrice),
                            buy.getSharePerCertificate(),
                            gameUIManager.format(buy.getSharesPerCertificate() * startPrice)));
                    buyActions.add(buy);
                    buyAmounts.add(startPrice);
                }
            } else {
                options.add(LocalText.getText("StartACompany",
                        company.getId(),
                        company.getPresidentsShare().getShare(),
                        gameUIManager.format(company.getPresidentsShare().getShares() * startPrices[0])));
                buyActions.add(buy);
                buyAmounts.add(startPrices[0]);
            }
        } else {
            startPrices = new int[] { buy.getPrice() };
            options.add(LocalText.getText("StartCompanyFixed",
                    company.getId(),
                    buy.getSharePerCertificate(),
                    gameUIManager.format(startPrices[0])));
            buyActions.add(buy);
            buyAmounts.add(startPrices[0]);
        }

    }

    /** Stub allowing game-specific extensions */
    protected PossibleAction processGameSpecificActions(ActionEvent actor,
            PossibleAction chosenAction) {
        return chosenAction;
    }

    protected PossibleAction processGameSpecificFollowUpActions(
            ActionEvent actor, PossibleAction chosenAction) {
        return chosenAction;
    }

    /** Stub, can be overridden by game-specific subclasses */
    // Overridden by 1826 to add bonds
    protected void initGameSpecificActions() {

    }

    /**
     * Initializes the CashCorrectionActions
     */
    public boolean initCashCorrectionActions() {
        int np = players.getNumberOfPlayers();

        // Clear all buttons
        for (int i = 0; i < nc; i++) {
            setCompanyCashButton(i, false, null);
        }
        for (int j = 0; j < np; j++) {
            setPlayerCashButton(j, false, null);
        }

        List<CashCorrectionAction> actions = possibleActions.getType(CashCorrectionAction.class);

        if (actions != null) {
            for (CashCorrectionAction a : actions) {
                MoneyOwner ch = a.getCashHolder();
                if (ch instanceof PublicCompany) {
                    PublicCompany pc = (PublicCompany) ch;
                    int i = pc.getPublicNumber();
                    setCompanyCashButton(i, true, a);
                }
                if (ch instanceof Player) {
                    Player p = (Player) ch;
                    int i = p.getIndex();
                    setPlayerCashButton(i, true, a);
                }
            }
        }

        return (actions != null && !actions.isEmpty());

    }

    public void highlightCurrentPlayer(int index) {
        int np = players.getNumberOfPlayers();

        for (int j = 0; j < np; j++) {
            upperPlayerCaption[j].setHighlight(j == index);
        }
    }

    public void highlightLocalPlayer(int index) {
        int np = players.getNumberOfPlayers();

        for (int j = 0; j < np; j++) {
            upperPlayerCaption[j].setLocalPlayer(j == index);
        }
    }

    public String getSRPlayer() {
        if (actorIndex >= 0)
            return players.getPlayerByPosition(actorIndex).getId();
        else
            return "";
    }

    protected void setCompanyCashButton(int i, boolean clickable, PossibleAction action) {
        if (shareRowVisibilityObservers[i] == null)
            return;

        boolean visible = shareRowVisibilityObservers[i].lastValue();

        if (clickable) {
            compCashButton[i].setText(compCash[i].getText());
        } else {
            compCashButton[i].clearPossibleActions();
        }
        compCash[i].setVisible(visible && !clickable);
        compCashButton[i].setVisible(visible && clickable);
        if (action != null)
            compCashButton[i].addPossibleAction(action);
    }

    protected void setPlayerCashButton(int i, boolean clickable, PossibleAction action) {

        if (clickable) {
            playerCashButton[i].setText(playerCash[i].getText());
        } else {
            playerCashButton[i].clearPossibleActions();
        }
        playerCash[i].setVisible(!clickable);
        playerCashButton[i].setVisible(clickable);

        if (action != null)
            playerCashButton[i].addPossibleAction(action);
    }

    protected void syncToolTipText(Field field, ClickField clickField) {
        String baseText = field.getToolTipText();
        clickField.setToolTipText(Util.hasValue(baseText) ? baseText : null);
    }

    protected void addToolTipText(ClickField clickField, String addText) {
        if (!Util.hasValue(addText))
            return;
        String baseText = clickField.getToolTipText();
        clickField.setToolTipText(Util.hasValue(baseText) ? baseText + "<br>" + addText : addText);
    }

    public String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public void updatePlayerTime(final int playerIndex, final int newTime) {
        // Ensure the array and index are valid
        if (playerTimer == null || playerIndex < 0 || playerIndex >= playerTimer.length) {
            return;
        }

        final Field timerField = playerTimer[playerIndex];
        if (timerField == null) {
            return;
        }

        // Auto-Detect Time Jumps (Penalties/Bonuses)
        int lastTime = lastPlayerTimes[playerIndex];
        int diff = (lastTime == Integer.MIN_VALUE) ? 0 : (newTime - lastTime);
        // Log every significant update to trace why Green is missing
        if (lastTime != newTime && lastTime != Integer.MIN_VALUE) {
            // log.info("TRACE-TIME-UPDATE: P{} Old={} New={} Diff={}", playerIndex,
            // lastTime, newTime, diff);
        }

        lastPlayerTimes[playerIndex] = newTime; // Update stored time

        // If time changed by more than 1 second (and isn't the init step), trigger
        // flash
        if (Math.abs(diff) > 1) {
            updatePlayerTimeWithFlash(playerIndex, newTime, diff);
            return;
        }

        // 1. Pure Text Update (No Flashing)
        SwingUtilities.invokeLater(() -> {
            timerField.setText(String.valueOf(newTime));

            // RED TEXT for negative values (Bottom Grid)
            if (newTime < 0) {
                timerField.setForeground(Color.RED);
            } else {
                timerField.setForeground(Color.BLACK);
            }

            repaint();
        });

    }

    public int[] getLastPlayerTimes() {
        // Ensure we return a copy, not the internal array directly, though passing the
        // original array here is harmless since we overwrite it in setLastPlayerTimes.
        return lastPlayerTimes;
    }

    public void setLastPlayerTimes(int[] times) {
        // Only restore if the arrays are compatible (same number of players/size)
        if (times != null && this.lastPlayerTimes != null && times.length == this.lastPlayerTimes.length) {
            System.arraycopy(times, 0, this.lastPlayerTimes, 0, times.length);
        }
    }

    /**
     * EVENT UPDATE: Call this explicitly from GameManager/Engine when a bonus
     * occurs.
     * Updates the text AND flashes the background.
     * * @param playerIndex The player's index
     * 
     * @param newTime       The new total time to display
     * @param amountChanged The amount added (+30) or removed (-10).
     *                      Positive = Green Flash, Negative = Red Flash.
     */
    public void updatePlayerTimeWithFlash(final int playerIndex, final int newTime, final int amountChanged) {
        if (playerTimer == null || playerIndex < 0 || playerIndex >= playerTimer.length) {
            return;
        }
        final Field timerField = playerTimer[playerIndex];
        if (timerField == null)
            return;

        //
        SwingUtilities.invokeLater(() -> {
            // 1. Update Text
            timerField.setText(String.valueOf(newTime));

            // 2. Determine Flash Color (Green for Gain, Red for Loss)
            Color flashColor = (amountChanged > 0) ? Color.GREEN : Color.RED;

            // 3. Apply Flash
            timerField.setOpaque(true);
            timerField.setBackground(flashColor);

            // 4. Calculate Revert Color
            // If this player is the current actor, revert to Yellow (BG_OPERATING), else
            // White.
            // BG_OPERATING is typically (255, 255, 200).
            final Color normalColor = (playerIndex == this.actorIndex) ? new Color(255, 255, 200) : Color.WHITE;

            // 5. Reset after 500ms
            javax.swing.Timer resetTimer = new javax.swing.Timer(500, e -> {
                timerField.setBackground(normalColor);
                repaint();
            });
            resetTimer.setRepeats(false);
            resetTimer.start();

            repaint();
        });
    }

    @Override
    public void actionPerformed(ActionEvent actor) {
        JComponent source = (JComponent) actor.getSource();
        List<PossibleAction> actions;
        PossibleAction chosenAction = null;
        StockRound.manualSwapChoice = null;

        if (source instanceof ClickField) {
            gbc = gb.getConstraints(source);
            actions = ((ClickField) source).getPossibleActions();

            // SAFETY CHECK: Actions might be null if the button was initialized but no
            // action added
            if (actions == null || actions.isEmpty()) {
                return;
            }

            SoundManager.notifyOfClickFieldSelection(actions.isEmpty() ? null : actions.get(0));

            if (actions.size() == 0) {
                // log.warn("No ClickField action found");
            } else if (actions.get(0) instanceof SellShares) {

                // INTELLIGENT SELLING LOGIC
                // Rule: If multiple selling options exist (e.g. Sell 1 vs Sell 2), we MUST ask
                // the user.
                // Auto-selecting "Sell 1" when "Sell 2" is available triggers a double price
                // drop penalty.
                // Auto-selecting "Sell 2" might be unwanted.
                // Therefore: Auto-select ONLY if there is exactly one option (safe). Otherwise
                // -> Dialog.

                List<SellShares> sellActions = new ArrayList<>();
                for (PossibleAction pa : actions) {
                    if (pa instanceof SellShares) {
                        sellActions.add((SellShares) pa);
                    }
                }

                chosenAction = null; // Default to Dialog

                if (gameUIManager.isCurrentPlayerAI()) {
                    // AI: Always pick the first valid option (usually the largest/best fit provided
                    // by engine)
                    if (!sellActions.isEmpty())
                        chosenAction = sellActions.get(0);
                } else {
                    // HUMAN:
                    if (sellActions.size() == 1) {
                        // Case A: Only one option exists (e.g. Player only has 10% left).
                        // Safe to auto-execute immediately.
                        chosenAction = sellActions.get(0);
                    }
                    // Case B: Multiple options (Sell 10% vs 20%).
                    // chosenAction remains null -> Dialog triggers below.
                }

                // 3. Dialog Construction (If chosenAction is still null)
                if (chosenAction == null) {
                    List<String> options = Lists.newArrayList();
                    List<SellShares> dialogActions = Lists.newArrayList();

                    for (SellShares sale : sellActions) {
                        int i = sale.getNumber();
                        String label;
                        if (sale.getPresidentExchange() == 0) {
                            label = LocalText.getText("SellShares",
                                    i, sale.getShare(), i * sale.getShare(), sale.getCompanyName(),
                                    gameUIManager.format(i * sale.getShareUnits() * sale.getPrice()));
                        } else {
                            label = LocalText.getText("SellSharesWithSwap",
                                    i * sale.getShare(), sale.getCompanyName(),
                                    gameUIManager.format(i * sale.getShareUnits() * sale.getPrice()));
                        }
                        options.add(label);
                        dialogActions.add(sale);
                    }

                    int index = -1;
                    if (options.size() > 1) {
                        String message = LocalText.getText("PleaseSelect");
                        String sp = (String) JOptionPane.showInputDialog(this, message,
                                message, JOptionPane.QUESTION_MESSAGE,
                                null, options.toArray(new String[0]),
                                options.get(0));
                        index = options.indexOf(sp);
                    } else if (options.size() == 1) {
                        // Fallback: If logic slipped here with 1 option (e.g. ambiguity check was
                        // weird), confirm it.
                        String message = LocalText.getText("PleaseConfirm");
                        int result = JOptionPane.showConfirmDialog(this, options.get(0),
                                message, JOptionPane.OK_CANCEL_OPTION,
                                JOptionPane.QUESTION_MESSAGE);
                        index = (result == JOptionPane.OK_OPTION ? 0 : -1);
                    }

                    if (index >= 0) {
                        chosenAction = dialogActions.get(index);
                    }
                }

            } else if (actions.get(0) instanceof BuyCertificate) {

                boolean startCompany = false;

                List<String> options = Lists.newArrayList();
                List<BuyCertificate> buyActions = Lists.newArrayList();
                List<Integer> buyAmounts = Lists.newArrayList();
                BuyCertificate buy;
                String companyName = "";
                String playerName = "";
                int sharePerCert;
                int sharesPerCert;
                int shareUnit;

                for (PossibleAction action : actions) {
                    buy = (BuyCertificate) action;
                    // cert = buy.getCertificate();
                    playerName = buy.getPlayerName();
                    PublicCompany company = buy.getCompany();
                    companyName = company.getId();
                    sharePerCert = buy.getSharePerCertificate();
                    shareUnit = company.getShareUnit();
                    sharesPerCert = sharePerCert / shareUnit;

                    if (buy instanceof StartCompany) {
                        startCompany = true;
                        setupStartCompany((StartCompany) buy, buyActions, buyAmounts, options);

                    } else {
                        String key = buy.isPresident() ? "BuyPresidentCert" : "BuyCertificate";
                        options.add(LocalText.getText(key,
                                sharePerCert,
                                companyName,
                                buy.getFromPortfolio().getParent().getId(),
                                gameUIManager.format(sharesPerCert * buy.getPrice())));
                        buyActions.add(buy);
                        buyAmounts.add(1);
                        for (int i = 2; i <= buy.getMaximumNumber(); i++) {
                            options.add(LocalText.getText("BuyCertificates",
                                    i,
                                    sharePerCert,
                                    companyName,
                                    buy.getFromPortfolio().getParent().getId(),
                                    gameUIManager.format(i * sharesPerCert
                                            * buy.getPrice())));
                            buyActions.add(buy);
                            buyAmounts.add(i);
                        }
                    }
                }
                int index = 0;
                // check for instanceof StartCompany_18EU allows to continue with selecting the
                // minor
                if (options.size() > 1 || actions.get(0) instanceof StartCompany_18EU) {
                    if (startCompany) {
                        RadioButtonDialog dialog = new RadioButtonDialog(
                                GameUIManager.COMPANY_START_PRICE_DIALOG,
                                gameUIManager,
                                parent,
                                LocalText.getText("PleaseSelect"),
                                LocalText.getText("WHICH_START_PRICE",
                                        playerName,
                                        companyName),
                                options.toArray(new String[0]), -1);
                        gameUIManager.setCurrentDialog(dialog, actions.get(0));
                        parent.disableButtons();
                        return;
                    } else {
                        String sp = (String) JOptionPane.showInputDialog(this,
                                LocalText.getText(
                                        startCompany ? "WHICH_PRICE" : "HOW_MANY_SHARES"),
                                LocalText.getText("PleaseSelect"),
                                JOptionPane.QUESTION_MESSAGE, null,
                                options.toArray(new String[0]),
                                options.get(0));
                        index = options.indexOf(sp);
                    }
                } else if (options.size() == 1) {
                    // This is the "Remove Share BuyNag" fix
                    index = 0;
                }
                if (index < 0) {
                    // cancelled
                } else if (startCompany) {
                    chosenAction = buyActions.get(index);
                    ((StartCompany) chosenAction).setStartPrice(buyAmounts.get(index));
                    ((StartCompany) chosenAction)
                            .setNumberBought(((StartCompany) chosenAction).getSharesPerCertificate());
                } else {
                    chosenAction = buyActions.get(index);
                    ((BuyCertificate) chosenAction).setNumberBought(buyAmounts.get(index));
                }
            } else if (actions.get(0) instanceof CashCorrectionAction) {
                CashCorrectionAction cca = (CashCorrectionAction) actions.get(0);
                String amountString = (String) JOptionPane.showInputDialog(this,
                        LocalText.getText("CorrectCashDialogMessage", cca.getCashHolderName()),
                        LocalText.getText("CorrectCashDialogTitle"),
                        JOptionPane.QUESTION_MESSAGE, null, null, 0);
                if (amountString.charAt(0) == '+')
                    amountString = amountString.substring(1);
                int amount;
                try {
                    amount = Integer.parseInt(amountString);
                } catch (NumberFormatException e) {
                    amount = 0;
                }
                cca.setAmount(amount);
                chosenAction = cca;
            } else if (actions.get(0) instanceof TrainCorrectionAction) {
                chosenAction = actions.get(0);
            } else if (actions.get(0) instanceof BuyTrain) {
                chosenAction = handleBuyTrain((BuyTrain) actions.get(0));
            } else {
                chosenAction = processGameSpecificActions(actor, actions.get(0));
            }

        }

        chosenAction = processGameSpecificFollowUpActions(actor, chosenAction);

        if (chosenAction != null) {
            if (chosenAction instanceof SellShares) {
                SellShares ss = (SellShares) chosenAction;
            }
            (parent).process(chosenAction);
        }

        repaint();
    }

    /**
     * Handles the logic for buying a train, including the UI for negotiating price
     * between companies if applicable.
     */
    private PossibleAction handleBuyTrain(BuyTrain buyTrainAction) {
        try {
            net.sf.rails.game.state.Owner buyingOwner = null;
            net.sf.rails.game.state.Owner sellingOwner = buyTrainAction.getFromOwner();

            // 1. Determine BUYER
            // In an OR, the Buyer is ALWAYS the Operating Company.
            if (gameUIManager.getGameManager().getCurrentRound() instanceof net.sf.rails.game.OperatingRound) {
                buyingOwner = ((net.sf.rails.game.OperatingRound) gameUIManager.getGameManager()
                        .getCurrentRound()).getOperatingCompany();
            }
            // Fallback: Use action owner if not in OR
            if (buyingOwner == null) {
                buyingOwner = buyTrainAction.getOwner();
            }

            // 2. Identify if Seller is Bank (Fixed Price)
            boolean isBankSale = (sellingOwner instanceof net.sf.rails.game.financial.Bank) ||
                    (sellingOwner != null && sellingOwner.getParent() instanceof net.sf.rails.game.financial.Bank);

            if (isBankSale) {
                buyTrainAction.setPricePaid(buyTrainAction.getFixedCost());
                return buyTrainAction;
            }

            // 3. Company-to-Company Negotiation
            if (!(buyingOwner instanceof net.sf.rails.game.PublicCompany)) {
                return null;
            }

            net.sf.rails.game.PublicCompany buyingCompany = (net.sf.rails.game.PublicCompany) buyingOwner;
            String buyerName = buyingCompany.getId();
            String trainName = (buyTrainAction.getTrain() != null) ? buyTrainAction.getTrain().getName() : "?";
            String sellerName = (sellingOwner != null) ? sellingOwner.getId() : "Unknown";
            int maxCash = buyingCompany.getPurseMoneyModel().value();

            int defaultPrice = (maxCash > 0) ? maxCash : 1;

            String message = String.format("%s buys a %s train from %s", buyerName, trainName, sellerName);
            String detail = String.format("(%s Treasury: %s)", buyerName, gameUIManager.format(maxCash));
            String fullMessage = "<html><h3>" + message + "</h3>" + detail + "<br>Enter Purchase Price:</html>";

            String amountString = (String) JOptionPane.showInputDialog(
                    this, fullMessage, "Negotiate Price", JOptionPane.QUESTION_MESSAGE,
                    null, null, String.valueOf(defaultPrice));

            if (amountString == null)
                return null; // Cancelled

            int price = 0;
            try {
                price = Integer.parseInt(amountString.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                price = 0;
            }

            if (price <= 0) {
                JOptionPane.showMessageDialog(this, "Price must be > 0", "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }

            buyTrainAction.setPricePaid(price);
            return buyTrainAction;

        } catch (Exception e) {
            log.error("TRAIN_BUY_CRASH", e);
            return buyTrainAction; // Fallback to raw action if UI fails
        }
    }

    protected void setCompanyTrainButton(int i, boolean clickable, PossibleAction action) {
        // 1. Safety Checks
        if (shareRowVisibilityObservers == null || i < 0 || i >= shareRowVisibilityObservers.length
                || shareRowVisibilityObservers[i] == null) {
            return;
        }

        // Unified Rendering: ALWAYS use the Button Panel, NEVER the text Field.
        // This ensures Company trains look identical to Pool/IPO trains (RailCards).

        // 1. Hide the old static text field
        if (compTrains[i] != null) {
            compTrains[i].setVisible(false);
        }

        // 2. Show the button panel (if the row is visible)
        if (compTrainsButtonPanel == null || compTrainsButtonPanel.length <= i || compTrainsButtonPanel[i] == null)
            return;

        boolean visible = (shareRowVisibilityObservers[i] != null) && shareRowVisibilityObservers[i].lastValue();
        compTrainsButtonPanel[i].setVisible(visible);

        PublicCompany c = companies[i];
        java.util.List<net.sf.rails.game.Train> trainList = new java.util.ArrayList<>(
                c.getPortfolioModel().getTrainList());
        java.util.List<BuyTrain> buyActions = clickable ? possibleActions.getType(BuyTrain.class) : null;
        int limit = c.getCurrentTrainLimit();

        // 3. Render Owned Trains using RailCard
        for (int t = 0; t < MAX_TRAIN_SLOTS; t++) {
            RailCard cf = compSubTrainButtons[i][t];
            if (cf == null)
                continue;

            cf.reset(); // Clear previous state (label, train, actions)

            if (t < trainList.size()) {
                // EXISTING TRAIN
                net.sf.rails.game.Train train = trainList.get(t);

                // Use RailCard logic to set content
                cf.setTrain(train);
                String cleanName = train.getName().replaceAll("_\\d+$", "");
                cf.setCustomLabel(cleanName);

                boolean canBuy = false;
                if (clickable && buyActions != null) {
                    for (BuyTrain ba : buyActions) {
                        if (ba.getTrain() == train) {
                            cf.addPossibleAction(ba);
                            canBuy = true;
                            break;
                        }
                    }
                }

                // Apply Styles manually since we aren't using the static helper anymore
                if (canBuy) {
                    cf.setBackground(BG_BUY_ACTIVE);
                    cf.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
                    cf.setToolTipText("Click to Buy " + train.getName());
                    cf.setEnabled(true);
                } else {
                    cf.setBackground(BG_CARD_PASSIVE);
                    // Compound border to match the 2px thickness of the active button
                    cf.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(Color.BLACK, 1),
                            BorderFactory.createEmptyBorder(1, 1, 1, 1)));
                    cf.setToolTipText(null);
                    cf.setEnabled(true);
                }

                cf.setVisible(true);

            } else if (t < limit) {
                // EMPTY SLOT (Passive)
                cf.setCustomLabel(""); // Render empty space
                cf.setBackground(BG_CARD_PASSIVE);
                cf.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color.BLACK, 1),
                        BorderFactory.createEmptyBorder(1, 1, 1, 1)));
                cf.setVisible(true);
            } else {
                // EXCEEDS LIMIT (Hidden)
                cf.setVisible(false);
            }
        }
    }

    private java.util.List<String> previousDashboardSignature = new java.util.ArrayList<>();

    public void refreshDashboard() {
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
            javax.swing.SwingUtilities.invokeLater(this::refreshDashboard);
            return;
        }

        if (gameUIManager == null || gameUIManager.getGameManager() == null)
            return;

       // Identify Operating Company for Signature
        PublicCompany operatingComp = null;
        net.sf.rails.game.round.RoundFacade currentRound = gameUIManager.getGameManager().getCurrentRound();

        String opCompId = null;


        if (currentRound instanceof net.sf.rails.game.OperatingRound) {
            PublicCompany pc = ((net.sf.rails.game.OperatingRound) currentRound).getOperatingCompany();
            if (pc != null) opCompId = pc.getId();
        }
// 1835 PFR Fix
        else if (currentRound != null && "PrussianFormationRound".equals(currentRound.getClass().getSimpleName())) {
            opCompId = "PR";
        }
        




        java.util.List<PublicCompany> allCompanies = gameUIManager.getAllPublicCompanies();
        net.sf.rails.game.model.PortfolioModel ipoModel = gameUIManager.getRoot().getBank().getIpo()
                .getPortfolioModel();

        // 1. Capture Order
        final java.util.Map<PublicCompany, Integer> originalOrder = new java.util.HashMap<>();
        int orderIndex = 0;
        java.util.List<PublicCompany> displayList = new java.util.ArrayList<>();

        for (PublicCompany c : allCompanies) {
            originalOrder.put(c, orderIndex++);
            if (c.isClosed())
                continue;
            displayList.add(c);
        }

        // 2. Sort
        compNameCaption = new Caption[nc];
        // Synchronized sorting logic with refreshDashboard to ensure UI matches the
        // detected state
        java.util.Collections.sort(displayList, (c1, c2) -> {
            boolean c1Minor = !c1.hasStockPrice();
            boolean c2Minor = !c2.hasStockPrice();
            if (c1Minor && !c2Minor)
                return -1;
            if (!c1Minor && c2Minor)
                return 1;

            if (c1Minor) {
                return Integer.compare(c1.getPublicNumber(), c2.getPublicNumber());
            }

            // Majors: Sort by Price Descending (High to Low)
            int p1 = c1.getCurrentSpace() != null ? c1.getCurrentSpace().getPrice()
                    : (c1.getStartSpace() != null ? c1.getStartSpace().getPrice() : 0);
            int p2 = c2.getCurrentSpace() != null ? c2.getCurrentSpace().getPrice()
                    : (c2.getStartSpace() != null ? c2.getStartSpace().getPrice() : 0);

            // Log strictly to verify correct renderer sorting
            // if ("PR".equals(c1.getId()) || "PR".equals(c2.getId())) {
            // log.info("REAL initFields Sort: {} val={}, {} val={}.", c1.getId(), p1,
            // c2.getId(), p2);
            // }

            if (p1 != p2) {
                return Integer.compare(p2, p1);
            }

            return Integer.compare(c1.getPublicNumber(), c2.getPublicNumber());
        });

        // 3. Build Signature
        java.util.List<String> currentSignature = new java.util.ArrayList<>();
        for (PublicCompany c : displayList) {
            boolean inIpo = ipoModel.getShare(c) > 0;
            boolean isPrussian = "PR".equals(c.getId());

            // Check for President's Share ownership (M5 sync fix)
            boolean hasPresidentCertOwnedByPlayer = false;
            if (c.getPresidentsShare() != null && c.getPresidentsShare().getOwner() instanceof Player) {
                hasPresidentCertOwnedByPlayer = true;
            }

            boolean isActive = c.hasFloated() || hasPresidentCertOwnedByPlayer || (inIpo && !isPrussian);

            boolean isOperating = (c == operatingComp);

            currentSignature.add(c.getId() + ":" + isActive);
        }


        // 4. Compare and Recreate
        if (!currentSignature.equals(previousDashboardSignature)) {
            previousDashboardSignature = currentSignature;
            recreate();
        } else {
            repaint();
        }
    }

    // Method 1: WITH 'Object o' (Handles Actions & Colors)
    protected void setPlayerCertButton(int i, int j, boolean clickable, Object o) {
        if (i < 0 || i >= shareRowVisibilityObservers.length || shareRowVisibilityObservers[i] == null)
            return;
        if (j < 0)
            return;

        // 1. Manage Dot Visibility (Environment)
        // Check if player has sold this specific company in the current round
        boolean hasSold = false;
        Player player = players.getPlayerByPosition(j);
        if (player != null && companies[i] != null) {
            hasSold = player.hasSoldThisRound(companies[i]);
            // Or use: player.getSoldThisRoundModel(companies[i]).booleanValue();
        }

// "WHITE DOT" HACK: Force alignment by keeping the dot visible but transparent
        if (playerSoldDots[i][j] != null) {
            if (hasSold) {
                // Active Sale: Show Red Dot
                playerSoldDots[i][j].setVisible(true);
                playerSoldDots[i][j].setForeground(Color.RED);
            } else if (companies[i].hasStockPrice()) {
                // Major Company (Unsold): Show Transparent Dot (Spacer)
                playerSoldDots[i][j].setVisible(true);
                playerSoldDots[i][j].setForeground(new Color(0, 0, 0, 0)); // Transparent
            } else {
                // Minors: No dot at all
                playerSoldDots[i][j].setVisible(false);
            }
        }
        

        // 2. Manage Card Visibility & Content (Content)
        boolean cardHasContent = (playerShareCards[i][j] != null);

        boolean panelVisible = shareRowVisibilityObservers[i].lastValue();
        if (panelVisible) {
            playerSharePanels[i][j].setVisible(true);
        }

        // 1. MAJOR COMPANIES (Cards)
        if (playerShareCards != null && playerShareCards[i][j] != null) {

            boolean visible = shareRowVisibilityObservers[i].lastValue();
            if (!visible)
                playerShareCards[i][j].setVisible(false);

            if (clickable && o != null) {
                // ACTIVE
                if (o instanceof BuyCertificate) {
                    playerShareCards[i][j].setBackground(BG_BUY);
                    playerShareCards[i][j].setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
                    playerShareCards[i][j].addPossibleAction((PossibleAction) o);
                    playerShareCards[i][j].setVisible(true);
                    playerShareCards[i][j].setToolTipText(LocalText.getText("ClickToSelectForBuying"));
                } else if (o instanceof SellShares) {
                    playerShareCards[i][j].setBackground(BG_SELL);
                    playerShareCards[i][j].addPossibleAction((PossibleAction) o);
                    playerShareCards[i][j].setToolTipText(LocalText.getText("ClickForSell"));
                }
                playerShareCards[i][j].setEnabled(true);
            } else {
                // PASSIVE - Reset actions here instead
                playerShareCards[i][j].clearPossibleActions();

                playerShareCards[i][j].setBackground(BG_CARD_PASSIVE);
                playerShareCards[i][j].setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color.BLACK, 1),
                        BorderFactory.createEmptyBorder(1, 1, 1, 1)));
                playerShareCards[i][j].setEnabled(true);
                // DEFAULT HIDE: Prevent "Ghost Cards" (visible but empty beige rectangles).
                // initTurn will explicitly call setVisible(true) if it finds valid text content.
                playerShareCards[i][j].setVisible(false);
                
            }
            return; // Done for card
        }

        // 2. MINOR COMPANIES (Old Buttons) - FALLBACK
        // Call the base method to set up text/visibility
        setPlayerCertButton(i, j, clickable);

        // Apply Action Styling (Colors)
        if (clickable && o != null) {
            if (o instanceof BuyCertificate)
                certPerPlayerButton[i][j].setBackground(BG_BUY);
            else if (o instanceof SellShares)
                certPerPlayerButton[i][j].setBackground(BG_SELL);

            syncToolTipText(certPerPlayer[i][j], certPerPlayerButton[i][j]);
            if (o instanceof PossibleAction)
                certPerPlayerButton[i][j].addPossibleAction((PossibleAction) o);
        }
    }

    // Method 2: WITHOUT 'Object o' (Base Setup)
    protected void setPlayerCertButton(int i, int j, boolean clickable) {
        if (j < 0)
            return;
        if (i < 0 || i >= shareRowVisibilityObservers.length || shareRowVisibilityObservers[i] == null)
            return;

        // 1. MAJOR COMPANIES (Cards)
        // Redirect to the main method to ensure consistency for Cards
        if (playerShareCards != null && playerShareCards[i][j] != null) {
            setPlayerCertButton(i, j, clickable, null);
            return;
        }

        // 2. MINOR COMPANIES (Old Buttons) - ORIGINAL LOGIC RESTORED
        // This breaks the recursion loop. We do NOT call the other method here.
        boolean visible = shareRowVisibilityObservers[i].lastValue();

        if (clickable) {
            certPerPlayerButton[i][j].setText(certPerPlayer[i][j].getText());
            syncToolTipText(certPerPlayer[i][j], certPerPlayerButton[i][j]);

            certPerPlayerButton[i][j].setOpaque(true);
            certPerPlayerButton[i][j].setBackground(Color.WHITE);
            certPerPlayerButton[i][j].setBorder(certPerPlayer[i][j].getBorder());
        } else {
            certPerPlayerButton[i][j].clearPossibleActions();
        }
        certPerPlayer[i][j].setVisible(visible && !clickable);
        certPerPlayerButton[i][j].setVisible(visible && clickable);
    }

    protected void setIPOCertButton(int i, boolean clickable, Object o) {
        if (i < 0 || i >= shareRowVisibilityObservers.length || shareRowVisibilityObservers[i] == null)
            return;

        // Redirect logic to the new ipoShareCards
        if (ipoShareCards == null || ipoShareCards[i] == null)
            return;

        // Base Visibility Check
        boolean visible = shareRowVisibilityObservers[i].lastValue();
        if (!visible) {
            ipoShareCards[i].setVisible(false);
            return;
        }

        // Ensure text visibility persists (Strict Trim Check)
        String shareTxt = ipo.getShareModel(companies[i]).toText();
        boolean hasContent = (shareTxt != null && !shareTxt.trim().isEmpty() && !shareTxt.trim().equals("0"));
        
        ipoShareCards[i].setVisible(hasContent);

        // Reset Actions
        ipoShareCards[i].clearPossibleActions();

        if (clickable && o != null) {
            // ACTIVE STATE
            if (o instanceof BuyCertificate) {
                ipoShareCards[i].setBackground(BG_BUY);
                ipoShareCards[i].setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
            } else if (o instanceof SellShares) {
                ipoShareCards[i].setBackground(BG_SELL);
            }

            if (o instanceof PossibleAction) {
                ipoShareCards[i].addPossibleAction((PossibleAction) o);
            }
            ipoShareCards[i].setEnabled(true);
            ipoShareCards[i].setVisible(true); // Force visible if actionable

        } else {
            // PASSIVE STATE
            ipoShareCards[i].setBackground(BG_CARD_PASSIVE);
            ipoShareCards[i].setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.BLACK, 1),
                    BorderFactory.createEmptyBorder(1, 1, 1, 1)));
            // Keep enabled so tooltips/text work
            ipoShareCards[i].setEnabled(true);
            
            // Re-apply content visibility (Hide if empty/whitespace)
            ipoShareCards[i].setVisible(hasContent);
        }

    }

    // This overload MUST be present to intercept calls like setIPOCertButton(i,
    // false)
    protected void setIPOCertButton(int i, boolean clickable) {
        // Redirect to the main handler with null action
        setIPOCertButton(i, clickable, null);
    }

    // ... (lines of unchanged context code) ...
    // --- START FIX ---
    // COMPLETELY REPLACE the setPoolCertButton methods:

    protected void setPoolCertButton(int i, boolean clickable, Object o) {
        if (i < 0 || i >= shareRowVisibilityObservers.length || shareRowVisibilityObservers[i] == null)
            return;

        if (poolShareCards == null || poolShareCards[i] == null)
            return;

        boolean visible = shareRowVisibilityObservers[i].lastValue();
        if (!visible) {
            poolShareCards[i].setVisible(false);
            return;
        }

        // Ensure text visibility persists
        String shareTxt = pool.getShareModel(companies[i]).toText();
        poolShareCards[i].setVisible(shareTxt != null && !shareTxt.isEmpty());

        poolShareCards[i].clearPossibleActions();

        if (clickable && o != null) {
            // ACTIVE
            if (o instanceof BuyCertificate) {
                poolShareCards[i].setBackground(BG_BUY);
                poolShareCards[i].setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
            } else if (o instanceof SellShares) {
                poolShareCards[i].setBackground(BG_SELL);
            }

            if (o instanceof PossibleAction)
                poolShareCards[i].addPossibleAction((PossibleAction) o);

            poolShareCards[i].setEnabled(true);

        } else {
            // PASSIVE
            poolShareCards[i].setBackground(BG_CARD_PASSIVE);
            poolShareCards[i].setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.BLACK, 1),
                    BorderFactory.createEmptyBorder(1, 1, 1, 1)));
            poolShareCards[i].setEnabled(true);
        }
    }

    protected void setPoolCertButton(int i, boolean clickable) {
        setPoolCertButton(i, clickable, null);
    }
    // --- END FIX ---
    // ... (rest of class) ...

    protected void setTreasuryCertButton(int i, boolean clickable, Object o) {
        if (i < 0 || i >= shareRowVisibilityObservers.length || shareRowVisibilityObservers[i] == null)
            return;

        setTreasuryCertButton(i, clickable); // Base call

        if (clickable && o != null) {
            if (o instanceof BuyCertificate)
                certInTreasuryButton[i].setBackground(BG_BUY);
            else if (o instanceof SellShares)
                certInTreasuryButton[i].setBackground(BG_SELL);

            syncToolTipText(certInTreasury[i], certInTreasuryButton[i]);
            if (o instanceof PossibleAction)
                certInTreasuryButton[i].addPossibleAction((PossibleAction) o);
        }
    }

    protected void setTreasuryCertButton(int i, boolean clickable) {
        if (i < 0 || i >= shareRowVisibilityObservers.length || shareRowVisibilityObservers[i] == null)
            return;

        boolean visible = shareRowVisibilityObservers[i].lastValue();
        if (clickable) {
            certInTreasuryButton[i].setText(certInTreasury[i].getText());
            syncToolTipText(certInTreasury[i], certInTreasuryButton[i]);

            certInTreasuryButton[i].setBackground(Color.WHITE);
            certInTreasuryButton[i].setOpaque(true);
            certInTreasuryButton[i].setBorder(certInTreasury[i].getBorder());
        } else {
            certInTreasuryButton[i].clearPossibleActions();
        }
        certInTreasury[i].setVisible(visible && !clickable);
        certInTreasuryButton[i].setVisible(clickable);
    }

    public void setPriorityPlayer(int index) {
        int np = players.getNumberOfPlayers();
        for (int j = 0; j < np; j++) {
            // Only update Upper Caption
            if (upperPlayerCaption[j] != null) {
                upperPlayerCaption[j].setText(players.getPlayerByPosition(j).getNameAndPriority());
            }
        }
    }

    private void updateFixedIncome() {

        if (playerFixedIncome == null) {
            return;
        }

        if (playerFixedIncome == null || gameUIManager == null)
            return;

        // Grab Phase
        net.sf.rails.game.Phase phase = null;
        try {
            phase = gameUIManager.getRoot().getPhaseManager().getCurrentPhase();
        } catch (Exception e) {
            // Phase not ready
        }

        for (int i = 0; i < np; i++) {
            if (playerFixedIncome[i] == null)
                continue;

            int total = 0;
            int privateCount = 0; // Count how many privates we found

            try {
                Player p = players.getPlayerByPosition(i);

                if (p.getPortfolioModel() != null) {
                    for (net.sf.rails.game.PrivateCompany pc : p.getPortfolioModel().getPrivateCompanies()) {
                        privateCount++;

                        // 1. Try Phase-based revenue
                        int r = (phase != null) ? pc.getRevenueByPhase(phase) : 0;

                        // 2. Fallback
                        if (r == 0 && pc.getRevenue() != null && !pc.getRevenue().isEmpty()) {
                            r = pc.getRevenue().get(0);
                        }

                        total += r;

                        // DEBUG PRINT: Use 'phase' directly to avoid compilation errors
                        // System.out.println("DEBUG-INC: Player " + p.getName() + " owns " + pc.getId()
                        // +
                        // " | RevList=" + pc.getRevenue() +
                        // " | Phase=" + (phase!=null ? phase : "null") +
                        // " | CalcRev=" + r);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Set text
            if (total > 0) {
                playerFixedIncome[i].setText(String.valueOf(total));
            } else if (privateCount > 0) {
                // If we have privates but 0 total, show "0" or "ERR" to indicate failure
                playerFixedIncome[i].setText("0");
                // System.out.println("DEBUG-INC: Player " + i + " has privates but 0 income!");
            } else {
                playerFixedIncome[i].setText("");
            }

            playerFixedIncome[i].setForeground(Color.BLACK);
        }
    }

    private void updatePlayerPrivates() {
        if (playerPrivatesPanel == null)
            return;

        // 1. Identify Active Privates (Scan for Special Actions)
        // Switch to Set<String> (IDs) to avoid object reference mismatches
        java.util.Set<String> activePrivateIds = new java.util.HashSet<>();

        // FIX: Access .getList() to check size/empty status
        if (possibleActions != null && possibleActions.getList() != null && !possibleActions.getList().isEmpty()) {

            for (PossibleAction pa : possibleActions.getList()) {
                net.sf.rails.game.special.SpecialProperty sp = null;

                // Check various action types that might use a Special Property
                if (pa instanceof UseSpecialProperty) {
                    sp = ((UseSpecialProperty) pa).getSpecialProperty();
                } else if (pa instanceof LayTile) {
                    sp = ((LayTile) pa).getSpecialProperty();

                } else if (pa instanceof LayBaseToken) {
                    sp = ((LayBaseToken) pa).getSpecialProperty();
                }

                // If found, link back to the Private Company ID
                if (sp != null && sp.getOriginalCompany() instanceof net.sf.rails.game.PrivateCompany) {
                    String id = sp.getOriginalCompany().getId();
                    activePrivateIds.add(id);
                }
            }
        }

        for (int i = 0; i < np; i++) {
            if (playerPrivatesPanel[i] == null)
                continue;

            playerPrivatesPanel[i].removeAll();

            net.sf.rails.game.Player p = players.getPlayerByPosition(i);
            if (p == null)
                continue;

            java.util.Collection<net.sf.rails.game.PrivateCompany> privates = p.getPortfolioModel()
                    .getPrivateCompanies();

            // Create a styled RailCard for each private
            for (net.sf.rails.game.PrivateCompany pc : privates) {

                // Pass the existing buySellGroup
                RailCard card = new RailCard(pc, buySellGroup);

                // 1. Strict Dimensions (Match Train Buttons: 42x22)
                card.setPreferredSize(DIM_TRAIN_BTN);
                card.setMaximumSize(DIM_TRAIN_BTN);
                card.setMinimumSize(DIM_TRAIN_BTN);
                card.addActionListener(this);
                card.setCompactMode(true);

                // 2. Consistent Styling
                // Use setCustomLabel to ensure clean text rendering
                card.setCustomLabel(pc.getId());
                card.setToolTipText(pc.getLongName());

                // 3. Match the Train Button Border (Black Line, 1px)
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color.BLACK, 1),
                        BorderFactory.createEmptyBorder(1, 1, 1, 1)));

                // 4. State & Background
                // Check against ID set
                boolean isActive = activePrivateIds.contains(pc.getId());

                if (isActive) {
                    card.setBackground(BG_BUY_ACTIVE); // Green
                    card.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2)); // Thicker border
                    card.setState(RailCard.State.ACTIONABLE); // Make it look clickable/active
                } else {
                    card.setBackground(BG_CARD_PASSIVE); // Standard Beige
                    card.setState(RailCard.State.PASSIVE);
                }
                card.setOpaque(true);

                // 5. Alignment (Center in the column)
                card.setAlignmentX(Component.CENTER_ALIGNMENT);

                // 6. Add to Panel
                playerPrivatesPanel[i].add(card);
                // Small gap between cards
                playerPrivatesPanel[i].add(Box.createVerticalStrut(2));
            }

            // Ensure the panel has height even if empty (prevents layout collapse)
            if (privates.isEmpty()) {
                playerPrivatesPanel[i].add(Box.createVerticalStrut(DIM_TRAIN_BTN.height));
            }

            // Add Glue to push items to the top
            playerPrivatesPanel[i].add(Box.createVerticalGlue());

            playerPrivatesPanel[i].revalidate();
            playerPrivatesPanel[i].repaint();
        }
    }

    /**
     * Rebuilds the token display for a specific company row using TokenIcons.
     */
    private void updateCompanyTokenDisplay(int compIndex, PublicCompany company, JPanel panel) {
        if (panel == null)
            return;

        panel.removeAll();

        boolean hasStarted = company.hasFloated();
        // Hide tokens for Minors or Companies that haven't floated yet
        if (!company.hasStockPrice() || !hasStarted) {
            panel.revalidate();
            panel.repaint();
            return;
        }

        // Calculate Available Tokens
        int availableCount = 0;
        try {
            if (company.getAllBaseTokens() != null) {
                for (net.sf.rails.game.BaseToken token : company.getAllBaseTokens()) {
                    if (!token.isPlaced()) {
                        availableCount++;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error counting tokens for " + company.getId(), e);
        }

        // Limit display to 4 to prevent grid explosion
        int displayCount = Math.min(availableCount, 4);

        for (int k = 0; k < displayCount; k++) {
            // Generate the Icon
            // Diameter 18 fits well within the standard row height
            TokenIcon tokenIcon = new TokenIcon(
                    18,
                    company.getFgColour(),
                    company.getBgColour(),
                    company.getId());

            JLabel iconLabel = new JLabel(tokenIcon);
            String tooltip = "<html><b>" + company.getId() + "</b> Token Available</html>";
            iconLabel.setToolTipText(tooltip);

            panel.add(iconLabel);
        }

        panel.revalidate();
        panel.repaint();
    }

    private void updateTrainCosts() {
        try {
            net.sf.rails.game.TrainManager tm = gameUIManager.getRoot().getTrainManager();
            if (tm == null)
                return;

            int currentIndex = tm.getNewTypeIndex().value();
            java.util.List<net.sf.rails.game.TrainCardType> types = tm.getTrainCardTypes();

            // 1. CURRENT TRAIN (IPO)
            if (newTrainButton != null && currentIndex < types.size()) {
                net.sf.rails.game.TrainCardType currentTct = types.get(currentIndex);
                int cost = 0;
                if (!currentTct.getPotentialTrainTypes().isEmpty()) {
                    cost = currentTct.getPotentialTrainTypes().get(0).getCost();
                }

                String name = currentTct.getId().replaceAll("_\\d+$", "");

                // Calculate Qty String
                String qtyStr = currentTct.hasInfiniteQuantity() ? "\u221E"
                        : String.valueOf(currentTct.getQuantity() - currentTct.getNumberBoughtFromIPO());

                // Use RailCard setters
                newTrainButton.reset();
                newTrainButton.setCustomLabel(name);

                // Standard Passive Style (Green/Active logic handles enablement later)
                newTrainButton.setBackground(BG_CARD_PASSIVE);
                newTrainButton.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color.BLACK, 1),
                        BorderFactory.createEmptyBorder(1, 1, 1, 1)));
                newTrainButton.setVisible(true);

                if (newTrainInfoLabel != null) {
                    newTrainInfoLabel
                            .setText("<html><center>Qty: " + qtyStr + "<br>Price: " + gameUIManager.format(cost)
                                    + "</center></html>");
                }

            } else if (newTrainButton != null) {
                newTrainButton.setVisible(false);
                newTrainInfoLabel.setText("");
            }

            // 2. FUTURE TRAINS
            int buttonIdx = 0;
            // Iterate future types
            for (int i = currentIndex + 1; i < types.size() && buttonIdx < MAX_FUTURE_SLOTS; i++) {
                net.sf.rails.game.TrainCardType tct = types.get(i);
                int cost = 0;
                if (!tct.getPotentialTrainTypes().isEmpty()) {
                    cost = tct.getPotentialTrainTypes().get(0).getCost();
                }

                String qtyStr = tct.hasInfiniteQuantity() ? "\u221E" : "(" + tct.getQuantity() + ")";
                String priceStr = (cost > 0) ? gameUIManager.format(cost) : "";

                // Get Components

                RailCard btn = futureTrainButtons[buttonIdx];
                javax.swing.JLabel lbl = futureTrainInfoLabels[buttonIdx];
                buttonIdx++;

                if (btn != null) {
                    // Configure RailCard
                    btn.reset();
                    String cleanName = tct.getId().replaceAll("_\\d+$", "");
                    btn.setCustomLabel(cleanName);
                    btn.setName(tct.getId()); // Store ID for action mapping

                    // Passive Style
                    btn.setBackground(BG_CARD_PASSIVE);
                    btn.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(Color.BLACK, 1),
                            BorderFactory.createEmptyBorder(1, 1, 1, 1)));
                    btn.setVisible(true);
                }

                // 2. Configure Label (Qty + Price stacked)
                if (lbl != null) {
                    lbl.setText("<html><center>" +
                            qtyStr + "<br>" +
                            "<font color='#000080'><b>" + priceStr + "</b></font>" +
                            "</center></html>");
                    lbl.setVisible(true);
                }

            }

            // Hide unused slots
            for (; buttonIdx < MAX_FUTURE_SLOTS; buttonIdx++) {
                if (futureTrainButtons[buttonIdx] != null)
                    futureTrainButtons[buttonIdx].setVisible(false);
                if (futureTrainInfoLabels[buttonIdx] != null)
                    futureTrainInfoLabels[buttonIdx].setVisible(false);
            }

        } catch (Exception e) {
            log.error("Error updating train costs", e);
        }
    }

    public void initTurn(int actorIndex, boolean myTurn) {
        int cIdx, pIdx;

        // 1. RESET HIGHLIGHTS: If not my turn, or between rounds, clear the actor
        // highlight
        // This prevents the "Yellow" player from sticking around during other players'
        // turns or phase changes.
        if (!myTurn) {
            actorIndex = -1;
        }

        dummyButton.setSelected(true);
        int np = players.getNumberOfPlayers();

        // Identify Operating Company Safely
        String opCompId = null;
        if (gameUIManager.getGameManager().getCurrentRound() instanceof net.sf.rails.game.OperatingRound) {
            PublicCompany pc = ((net.sf.rails.game.OperatingRound) gameUIManager.getGameManager().getCurrentRound())
                    .getOperatingCompany();
            if (pc != null)
                opCompId = pc.getId();
        }

        // 1835 PFR Fix: Force the UI to recognize "PR" as the operating company.
        // This triggers the BG_OPERATING (Yellow) highlight for the PR row.
        else if (gameUIManager.getGameManager().getCurrentRound() != null 
                 && gameUIManager.getGameManager().getCurrentRound().getClass().getSimpleName().equals("PrussianFormationRound")) {
            opCompId = "PR";
        }

        // 1. ITERATE COMPANIES (Rows)
        for (cIdx = 0; cIdx < nc; cIdx++) {
            PublicCompany c = companies[cIdx];
            int i = c.getPublicNumber(); // CORRECT INDEX for arrays

            // FORCE RESET TRAINS: Ensure we are in "Display Mode" (HTML) at the start of
            // every update.
            // This clears any leftover buttons from a previous action and refreshes the
            // HTML list.
            setCompanyTrainButton(i, false, null);

            // Fix: Compare IDs to ensure the horizontal yellow line works
            boolean isOperating = (opCompId != null) && c.getId().equals(opCompId);

            boolean isMinor = !c.hasStockPrice();
            // Update Arrow Logic dynamically
            if (compArrowCaption != null && compArrowCaption[i] != null) {
                compArrowCaption[i].setText(isOperating ? "▶" : "");
            }

            boolean hasOwner = c.getPresidentsShare() != null && c.getPresidentsShare().getOwner() instanceof Player;
            // Check if shares are available in the IPO
            boolean inIpo = ipo.getShare(c) > 0;
            boolean isPrussian = "PR".equals(c.getId()); // Keep existing special handling for PR if needed

            // Mark as active if Floated OR Owned OR (Available in IPO and not Prussian)
            boolean isActive = c.hasFloated() || hasOwner || (inIpo && !isPrussian);

            // --- RIGHT SIDE COLORS (Market & Details) ---
            Color bgPool, bgIpo, bgDet, bgCurr;

            if (isOperating) {
                // 1. Operating -> Horizontal Yellow Line (Overrides everything)
                bgPool = BG_OPERATING;
                bgIpo = BG_OPERATING;
                bgDet = BG_OPERATING;
                bgCurr = BG_OPERATING;
            } else if (!isActive) {
                // 2. Inactive -> Grey
                bgPool = BG_INACTIVE;
                bgIpo = BG_INACTIVE;
                bgDet = BG_INACTIVE;
                bgCurr = BG_INACTIVE;
            } else {
                // 3. Active -> Standard Colors
                bgPool = isMinor ? BG_INACTIVE : BG_POOL;
                bgIpo = isMinor ? BG_INACTIVE : BG_IPO;
                bgDet = BG_MAUVE;
                bgCurr = BG_POOL;
            }

            setIPOCertButton(i, false);
            setPoolCertButton(i, false);

            // Update Company Name background (ALWAYS STANDARD, never yellow)
            if (compNameCaption[i] != null) {
                // Ensure standard colors (Black/White for minors, Company colors for Majors)
                compNameCaption[i].setBackground(isMinor ? Color.BLACK : c.getBgColour());
                compNameCaption[i].setForeground(isMinor ? Color.WHITE : c.getFgColour());
                compNameCaption[i].setOpaque(true);
            }

            // Update Pool Panel Background
            if (poolPanels[i] != null) {
                poolPanels[i].setBackground(bgPool);
            }

            // Update Pool Share Card Text
            if (poolShareCards[i] != null) {
                String shareTxt = pool.getShareModel(c).toText();
                if (shareTxt == null)
                    shareTxt = "";

                // STRICT VISIBILITY: Hide if empty, whitespace, 0, or 0%
                String trimmed = shareTxt.trim();
                boolean hasContent = !trimmed.isEmpty() && !trimmed.equals("0") && !trimmed.equals("0%");

                poolShareCards[i].setCustomLabel(shareTxt);
                // Force visibility off if no content
                poolShareCards[i].setVisible(hasContent);

                // Double Share Highlight
                boolean doubleShare = hasDoubleShare(pool, c);
                if (doubleShare) {
                    poolShareCards[i].setForeground(new Color(104, 35, 139)); // Lilac
                } else {
                    poolShareCards[i].setForeground(Color.BLACK);
                }
            }

            // Update Pool Price Label
            if (poolPriceLabels[i] != null) {
                if (c.hasStockPrice() && c.getCurrentSpace() != null) {
                    poolPriceLabels[i].setText(gameUIManager.format(c.getCurrentSpace().getPrice()));
                    poolPriceLabels[i].setForeground(new Color(0, 0, 128)); // Navy Blue

                    poolPriceLabels[i].setFont(new Font("SansSerif", Font.BOLD, 12));
                    poolPriceLabels[i].setHorizontalAlignment(SwingConstants.RIGHT);
                    // Ensure the label fills the space so alignment works
                    poolPriceLabels[i].setPreferredSize(new Dimension(30, 20));

                } else {
                    poolPriceLabels[i].setText("");
                }
            }

            // Update IPO Panel Background
            if (ipoPanels[i] != null) {
                ipoPanels[i].setBackground(bgIpo);
            }

            // Update IPO Share Card Text
            if (ipoShareCards[i] != null) {
                String shareTxt = ipo.getShareModel(c).toText(); // Get text from model
                if (shareTxt == null)
                    shareTxt = "";

                // STRICT VISIBILITY: Hide if empty, whitespace, 0, or 0%
                String trimmed = shareTxt.trim();
                boolean hasContent = !trimmed.isEmpty() && !trimmed.equals("0") && !trimmed.equals("0%");

                // Clean text (remove '100%P' etc if needed, but usually IPO is simple %)
                ipoShareCards[i].setCustomLabel(shareTxt);
                ipoShareCards[i].setVisible(hasContent);

                // Check for Double Share (Lilac Highlight)
                boolean doubleShare = hasDoubleShare(ipo, c);
                if (doubleShare) {
                    ipoShareCards[i].setForeground(new Color(104, 35, 139)); // Lilac
                } else {
                    ipoShareCards[i].setForeground(Color.BLACK);
                }
            }

            // Update IPO Par Label
            if (ipoParLabels[i] != null) {
                // VISIBILITY: Only show price if shares are actually in the IPO
                boolean hasSharesInIPO = ipo.getShare(c) > 0;

                if (hasParPrices && c.getStartSpace() != null && hasSharesInIPO) {
                    ipoParLabels[i].setText(gameUIManager.format(c.getStartSpace().getPrice()));
                    ipoParLabels[i].setForeground(new Color(0, 0, 128)); // Navy Blue

                    // FORMATTING: Right Align + Bigger Font
                    ipoParLabels[i].setFont(new Font("SansSerif", Font.BOLD, 12));
                    ipoParLabels[i].setHorizontalAlignment(SwingConstants.RIGHT);
                    ipoParLabels[i].setPreferredSize(new Dimension(30, 20));
                } else {
                    ipoParLabels[i].setText("");
                }

            }

            if (hasParPrices && parPrice[i] != null) {
                parPrice[i].setBackground(bgIpo);
                parPrice[i].setOpaque(true);
            }
            if (c.hasStockPrice() && currPrice[i] != null) {
                currPrice[i].setBackground(bgCurr);
                currPrice[i].setOpaque(true);
            }

            if (compCanHoldOwnShares) {
                setTreasuryCertButton(i, false);
                if (certInTreasury[i] != null) {
                    certInTreasury[i].setBackground(bgPool);
                    certInTreasury[i].setOpaque(true);
                }
            }

            // Details
            if (compCash[i] != null) {
                compCash[i].setBackground(bgDet);
                compCash[i].setOpaque(true);
            }
            if (compRevenue[i] != null) {
                compRevenue[i].setBackground(bgDet);
                compRevenue[i].setOpaque(true);
            }
            if (compTrains[i] != null) {
                compTrains[i].setBackground(bgDet);
                compTrains[i].setOpaque(true);
            }
            if (compTokens[i] != null) {
                compTokens[i].setBackground(bgDet);
                compTokens[i].setOpaque(true);
            }

            // This is the correct place to calculate and apply the row background color
            // to the data fields for seamless blending (Mauve/Yellow).

            Color bgRow;
            if (isOperating) {
                bgRow = BG_OPERATING; // Yellow
            } else if (!isActive) {
                bgRow = BG_INACTIVE; // Grey
            } else {
                bgRow = BG_MAUVE; // Mauve (Default for Active)
            }

            // Apply to Details Columns
            if (compCash[i] != null) {
                compCash[i].setBackground(bgRow);
                compCash[i].setOpaque(true);
            }
            if (compRevenue[i] != null) {
                compRevenue[i].setBackground(bgRow);
                compRevenue[i].setOpaque(true);
            }
            if (compTokens[i] != null) {
                compTokens[i].setBackground(bgRow); // Apply Mauve/Yellow/Gray
                updateCompanyTokenDisplay(i, c, compTokens[i]); // Refresh icon count
            }

            // Apply to Train Panel (The Seamless Fix)
            if (compTrainsButtonPanel[i] != null) {
                compTrainsButtonPanel[i].setBackground(bgRow);
                compTrainsButtonPanel[i].setOpaque(true);
            }

            // Note: We need to ensure the compCash/Revenue/Tokens fields use the new bgRow.
            // Assuming your previous code already updated these fields, we will ensure
            // the logic is consolidated here for simplicity.

            for (pIdx = 0; pIdx < np; pIdx++) {
                setPlayerCertButton(i, pIdx, false);

                // 1. Check what exists for this cell
                boolean hasOldField = (certPerPlayer[i][pIdx] != null);
                boolean hasNewPanel = (playerSharePanels != null && playerSharePanels[i] != null
                        && playerSharePanels[i][pIdx] != null);

                // 2. If neither exists, skip
                if (!hasOldField && !hasNewPanel)
                    continue;

                // 3. Determine Background Color
                Color cellBg;
                if (pIdx == actorIndex) {
                    cellBg = BG_OPERATING;
                } else if (isOperating) {
                    cellBg = BG_OPERATING;
                } else if (!isActive) {
                    cellBg = BG_INACTIVE;
                } else {
                    cellBg = Color.WHITE;
                }

                // 4. Update Logic (Branching)
                if (hasNewPanel) {

                    
                    // === PATH A: NEW CARD (Majors AND Minors now) ===
                    JPanel panel = playerSharePanels[i][pIdx];
                    panel.setBackground(cellBg);
                    panel.setOpaque(true);

                    if (playerShareCards[i][pIdx] != null) {
                        RailCard card = playerShareCards[i][pIdx];

                        // Get Content
                        Player player = players.getPlayerByPosition(pIdx);
                        String raw = player.getPortfolioModel().getShareModel(c).toText();
                        if (raw == null)
                            raw = "";

                        // Visibility Check
                        String check = raw.replace("%", "").trim();
                        boolean isZero = check.equals("0") || check.isEmpty();

                        card.setVisible(!isZero);

                        if (!isZero) {
                            card.setOpaque(true);

                            boolean hasSold = player.getSoldThisRoundModel(c).value();
                            boolean doubleShare = hasDoubleShare(player.getPortfolioModel(), c);

                            String cleanText = raw.replace("PU", "P");

                 // TEXT LOGIC: Only show "Owner" for Minors (no stock price).
                            // Majors should show "100%" (or "100%P" which we clean up)
                            if (cleanText.contains("100%P")) {
                                if (c.hasStockPrice()) {
                                    cleanText = "100%"; // Majors
                                } else {
                                    cleanText = "Owner"; // Minors
                                }
                            }


                            // Build HTML
                            StringBuilder sb = new StringBuilder("<html><center>");
                            String textColor = doubleShare ? "#68238B" : "#000000";
                              sb.append("<font color='").append(textColor).append("'><b>");
                            sb.append(cleanText);
                            sb.append("</b></font></center></html>");

                            card.setCustomLabel(sb.toString());
                        }
                    }


            
                }
            }

        }

        // 2. PLAYER FOOTERS
        for (int i = 0; i < np; i++) {
            Color pBg = (i == actorIndex) ? BG_OPERATING : Color.WHITE;
            // 6. Merge Status Logic into Header
            Player p = players.getPlayerByPosition(i);
            String log = gameUIManager.getGameManager().getPassedPlayersLog();
            boolean passed = log != null && java.util.Arrays.asList(log.split(", ")).contains(p.getName());

            // Highlight Name Cell Green if Passed
            Color headerBg = passed ? BG_PASSED : pBg;

            if (playerCash[i] != null) {
                playerCash[i].setBackground(pBg);
                playerCash[i].setOpaque(true);
            }
            if (playerPrivatesPanel[i] != null) {
                playerPrivatesPanel[i].setBackground(pBg);
                playerPrivatesPanel[i].setOpaque(true);
                playerPrivatesPanel[i].setVisible(true); // Force Visible
            }

            if (upperPlayerCaption[i] != null) {
                upperPlayerCaption[i].setBackground(headerBg);
                upperPlayerCaption[i].setOpaque(true);
            }
            if (playerFixedIncome[i] != null) {
                playerFixedIncome[i].setBackground(pBg);
                playerFixedIncome[i].setOpaque(true);
                playerFixedIncome[i].setVisible(true); // Force Visible

                // Ensure non-collapsing text (Field can shrink if empty string)
                if (playerFixedIncome[i].getText().isEmpty()) {
                    playerFixedIncome[i].setText(" ");
                }
            }

            if (playerTimer[i] != null) {
                playerTimer[i].setBackground(pBg);
                playerTimer[i].setOpaque(true);
            }

            if (playerCertCount[i] != null) {
                String t = playerCertCount[i].getText();
                Color certBg = BG_CERT_OK;
                if (t != null && t.contains("/")) {
                    try {
                        String[] parts = t.split("/");
                        int held = Integer.parseInt(parts[0].trim());
                        int limit = Integer.parseInt(parts[1].trim());
                        if (held >= limit)
                            certBg = BG_CERT_LIMIT;
                    } catch (Exception e) {
                    }
                }
                playerCertCount[i].setBackground(certBg);
                playerCertCount[i].setOpaque(true);
            }
        }

        // REPLACED: Use the new Panel variables instead of the deleted Field variables
        if (poolTrainsPanel != null) {
            poolTrainsPanel.setBackground(BG_TRAINS);
            poolTrainsPanel.setOpaque(true);
        }
        if (newTrainsPanel != null) {
            newTrainsPanel.setBackground(BG_TRAINS);
            newTrainsPanel.setOpaque(true);
        }
        if (futureTrainsPanel != null) {
            futureTrainsPanel.setBackground(BG_TRAINS);
            futureTrainsPanel.setOpaque(true);
        }

        if (bankCash != null) {
            bankCash.setBackground(BG_BANK);
            bankCash.setOpaque(true);
        }

        this.actorIndex = actorIndex;
        if (treasurySharesCaption != null)
            treasurySharesCaption.setHighlight(actorIndex == -1);
        // UPDATE HEADER: Green "Next Player" text via HTML injection into Parent Status
        // Label
        if (parentStatusLabel != null) {

            // 1. Determine "Next Player" Text
            String nextText = "";
            net.sf.rails.game.round.RoundFacade round = gameUIManager.getGameManager().getCurrentRound();

            if (round instanceof net.sf.rails.game.OperatingRound) {
                net.sf.rails.game.OperatingRound or = (net.sf.rails.game.OperatingRound) round;
                java.util.List<PublicCompany> ops = or.getOperatingCompanies();
                PublicCompany current = or.getOperatingCompany();

                if (ops != null && !ops.isEmpty() && current != null) {
                    int idx = ops.indexOf(current);
                    PublicCompany nextComp = ops.get((idx + 1) % ops.size());
                    if (nextComp != null && nextComp.getPresident() != null) {
                        nextText = "Next: " + nextComp.getPresident().getName() + " (" + nextComp.getId() + ")";
                    }
                }
            } else {
                if (np > 0 && actorIndex >= 0) {
                    Player nextP = players.getPlayerByPosition((actorIndex + 1) % np);
                    nextText = "Next: " + nextP.getName();
                }
            }

            // 2. Get the base "Thinking" text (strip previous HTML additions)
            String currentText = parentStatusLabel.getText();
            // If we have stored the raw thinking text, use it. Otherwise assume current is
            // raw if no HTML.
            // Simple heuristic: If it starts with <html>, strip it.
            if (currentText.toLowerCase().startsWith("<html>")) {
                // If we already injected, we might need to find the base text.
                // Ideally, StatusWindow updates the text, overwriting our HTML, and we re-apply
                // it here.
                // If StatusWindow hasn't updated, we might simply be appending to old text.
                // Let's rely on StatusWindow usually refreshing the text before initTurn.
            }

            // 3. Inject HTML
            // Format: <html>Thinking: XXX<br><font color='green'>Next: YYY</font></html>
            // We append the Green text to whatever is currently there.
            if (!currentText.contains("Next:")) {
                parentStatusLabel
                        .setText("<html>" + currentText + "<br><font color='#008000'>" + nextText + "</font></html>");
            }
        }

        // Force Update Parent Timer immediately
        if (parentTimerLabel != null && actorIndex >= 0 && actorIndex < np) {
            Player p = players.getPlayerByPosition(actorIndex);
            if (p != null) {
                // Safe cast assuming IntegerState
                int t = p.getTimeBankModel().value();
                parentTimerLabel.setText(p.getName() + ": " + formatTime(t));
            }
        }

        // 3. ENABLE BUTTONS
        if ((pIdx = this.actorIndex) >= -1 && myTurn) {
            // CRITICAL: Force focus to the Status Window during Stock Round (myTurn).
            // This ensures hotkeys (like Pass) work immediately without clicking.
            if (parentFrame != null && parentFrame.isVisible()) {
                parentFrame.toFront();
                parentFrame.requestFocus();
            }
            PublicCompany company;
            int index;
            net.sf.rails.game.model.PortfolioModel portfolio;
            java.util.List<BuyCertificate> buyableCerts = possibleActions.getType(BuyCertificate.class);
            if (buyableCerts != null) {
                for (BuyCertificate bCert : buyableCerts) {
                    company = bCert.getCompany();
                    index = company.getPublicNumber();
                    portfolio = bCert.getFromPortfolio();
                    if (portfolio == ipo)
                        setIPOCertButton(index, true, bCert);
                    else if (portfolio == pool)
                        setPoolCertButton(index, true, bCert);
                    else if ((portfolio.getParent()) instanceof Player)
                        setPlayerCertButton(index, ((Player) portfolio.getParent()).getIndex(), true, bCert);
                    else if (portfolio.getParent() instanceof PublicCompany && compCanHoldOwnShares)
                        setTreasuryCertButton(index, true, bCert);
                }
            }
            java.util.List<SellShares> sellableShares = possibleActions.getType(SellShares.class);
            if (sellableShares != null) {
                for (SellShares share : sellableShares) {
                    company = share.getCompany();
                    index = company.getPublicNumber();
                    if (pIdx >= 0)
                        setPlayerCertButton(index, pIdx, true, share);
                    else if (pIdx == -1 && compCanHoldOwnShares)
                        setTreasuryCertButton(index, true, share);
                }
            }
            initGameSpecificActions();
            java.util.List<NullAction> nullActions = possibleActions.getType(NullAction.class);
            if (nullActions != null) {
                for (NullAction na : nullActions)
                    (parent).setPassButton(na);
            }
        }

        updateFixedIncome();
        updatePlayerPrivates();
        updateTrainCosts();
        repaint();
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g); // Draw all components/backgrounds first

        if (np == 0 || companies == null || companies.length == 0)
            return;
        if (playerCash == null || playerCash[0] == null)
            return;
        if (certPerPlayer == null || certPerPlayer[0][0] == null)
            return;

        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2)); // Thick line

        // Point 1: Horizontal line above Player Cash
        // We use the first player cash field to determine the Y position
        int yPos = playerCash[0].getY();
        int width = getWidth();
        g2.drawLine(0, yPos, width, yPos);

    }

    public boolean initTrainCorrectionActions() {
        // SAFETY CHECK: If the game hasn't initialized the company list or actions yet,
        // abort safely.
        if (companies == null || companies.length == 0 || possibleActions == null) {
            return false;
        }

        int nc = companies.length;

        // 1. Reset all train buttons (Companies and Bank areas)
        for (int i = 0; i < nc; i++) {
            setCompanyTrainButton(i, false, null);
        }
        setPoolTrainButton(false, null);
        setNewTrainButton(false, null);
        setFutureTrainButton(false, null);

        // 2. Fetch available train actions
        java.util.List<rails.game.correct.TrainCorrectionAction> actions = possibleActions
                .getType(rails.game.correct.TrainCorrectionAction.class);

        boolean actionsFound = (actions != null && !actions.isEmpty());

        if (actionsFound) {
            for (rails.game.correct.TrainCorrectionAction a : actions) {
                net.sf.rails.game.Train target = a.getTargetTrain();
                if (target == null)
                    continue;

                boolean found = false;

                // Check Bank Pools (Pool, IPO, Unavailable)
                // We use getTrainList().contains() which is robust against ownership wrapper
                // objects
                if (pool.getTrainList().contains(target)) {
                    setPoolTrainButton(true, a);
                    found = true;
                } else if (ipo.getTrainList().contains(target)) {
                    setNewTrainButton(true, a);
                    found = true;
                } else if (bank.getUnavailable().getPortfolioModel().getTrainList().contains(target)) {
                    setFutureTrainButton(true, a);
                    found = true;
                }

                // Check Companies if not found in Bank
                if (!found) {
                    for (int i = 0; i < nc; i++) {
                        if (companies[i].getPortfolioModel().getTrainList().contains(target)) {
                            setCompanyTrainButton(i, true, a);
                            // Do not break; a train might theoretically trigger actions for multiple
                            // entities
                            // (though unlikely, it's safer to check all valid locations)
                        }
                    }
                }
            }
        }

        return actionsFound;
    }

    protected void setFutureTrainButton(boolean clickable, PossibleAction action) {
        if (futureTrainButtons == null)
            return;

        // If not clickable, we are resetting.
        // We do NOT want to hide them (setVisible(false)) because updateTrainCosts has
        // set them up as passive cards.
        if (!clickable) {
            for (RailCard cf : futureTrainButtons) {
                if (cf.isVisible()) {
                    // Restore passive look
                    if (cf != null && cf.isVisible()) {
                        // Restore passive look
                        cf.clearPossibleActions();
                        cf.setBackground(BG_CARD_PASSIVE);
                        cf.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(Color.BLACK, 1),
                                BorderFactory.createEmptyBorder(1, 1, 1, 1)));
                    }
                }
            }
            return;
        }

        // If clickable, we need to find the specific button matching the action's train
        if (action != null) {
            String targetName = "";
            if (action instanceof BuyTrain && ((BuyTrain) action).getTrain() != null) {
                targetName = ((BuyTrain) action).getTrain().getType().getName();
            } else if (action instanceof rails.game.correct.TrainCorrectionAction
                    && ((rails.game.correct.TrainCorrectionAction) action).getTargetTrain() != null) {
                targetName = ((rails.game.correct.TrainCorrectionAction) action).getTargetTrain().getType().getName();
            }

            for (RailCard cf : futureTrainButtons) {
                // We stored the train Name/ID in Component Name in updateTrainCosts
                if (cf != null && cf.isVisible() && targetName.equals(cf.getName())) {
                    cf.addPossibleAction(action);
                    // Apply Active Style
                    cf.setBackground(BG_BUY);
                    cf.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
                    break;
                }

            }
        }

    }

    // --- Helper: Check for "Double Paper" (Lilac Highlight) ---
    private boolean hasDoubleShare(PortfolioModel portfolio, PublicCompany company) {
        if (portfolio == null || company == null)
            return false;

        boolean isPrussia = "PR".equals(company.getId());
        int requiredShare = isPrussia ? 10 : 20;

        // Iterate through all PublicCertificates held by this portfolio for the company
        for (net.sf.rails.game.financial.PublicCertificate cert : portfolio.getCertificates(company)) {


            if (cert.getShare() >= requiredShare) {
                return true;
            }
        }
        return false;
    }

    public void setTrainBuyingActions(List<PossibleAction> actions) {

        // We must update the train costs (which resets the NewTrainButton to DISABLED)
        // BEFORE we iterate through the actions to ENABLE it.
        updateTrainCosts();

        // 1. Reset all train buttons to non-clickable first
        int nc = companies.length;
        for (int i = 0; i < nc; i++) {
            setCompanyTrainButton(i, false, null);
        }
        setPoolTrainButton(false, null);
        setNewTrainButton(false, null);
        setFutureTrainButton(false, null);

        if (actions == null || actions.isEmpty()) {
            return;
        }

        // 2. Map actions to fields
        for (PossibleAction pa : actions) {
            if (pa instanceof BuyTrain) {
                BuyTrain bt = (BuyTrain) pa;
                net.sf.rails.game.state.Owner source = bt.getFromOwner();
                net.sf.rails.game.Train targetTrain = bt.getTrain();

                if (source == null)
                    continue;

                // : 'source' might be the Bank OR a Bank Portfolio (IPO/Pool).
                // We check if the source itself is the Bank, or if its parent is the Bank.
                boolean isBankSource = (source instanceof net.sf.rails.game.financial.Bank) ||
                        (source.getParent() instanceof net.sf.rails.game.financial.Bank);

                if (isBankSource) {
                    // 1. Identify IPO vs Pool via ID (e.g., source.getId() == "IPO")
                    String id = source.getId();
                    boolean srcIsIPO = "IPO".equals(id);
                    boolean srcIsPool = "Pool".equals(id);

                    // 2. Fallback: Identify via Train Location (if source was generic Bank)
                    if (!srcIsIPO && !srcIsPool) {
                        // : Check list size for null train (unlimited purchase)
                        srcIsIPO = (targetTrain == null && ipo.getTrainList().size() > 0)
                                || (targetTrain != null && ipo.getTrainList().contains(targetTrain));
                        srcIsPool = (targetTrain != null && pool.getTrainList().contains(targetTrain));
                    }

                    if (srcIsIPO) {
                        // Update IPO button style for active state
                        setNewTrainButton(true, bt);
                    } else if (srcIsPool) {
                        setPoolTrainButton(true, bt);
                    } else {
                        // Fallback: likely future/unavailable
                        setFutureTrainButton(true, bt);
                    }
                } else if (source instanceof net.sf.rails.game.PublicCompany) {
                    // Direct ownership by company
                    PublicCompany c = (PublicCompany) source;
                    setCompanyTrainButton(c.getPublicNumber(), true, bt);
                } else if (source.getParent() instanceof net.sf.rails.game.PublicCompany) {
                    // Portfolio ownership (standard for most games)
                    PublicCompany c = (PublicCompany) source.getParent();
                    setCompanyTrainButton(c.getPublicNumber(), true, bt);
                }
            }

        }

        repaint();
    }

    // Helper to create uniform Train Buttons using RailCard
    // Replaces the old configureTrainButton static method
    private RailCard createTrainButton() {
        RailCard cf = new RailCard((net.sf.rails.game.Train) null, buySellGroup);

        // CRITICAL FIX: Register GameStatus as the listener so clicks are processed.
        // Without this, the button lights up Green but does nothing when clicked.
        cf.addActionListener(this);

        // 1. Strict Sizing
        cf.setPreferredSize(DIM_TRAIN_BTN);
        cf.setMinimumSize(DIM_TRAIN_BTN);
        cf.setMaximumSize(DIM_TRAIN_BTN);

        // 2. Enable Compact Mode (Center Alignment, No Table)
        cf.setCompactMode(true);

        // 3. Default Visuals (Passive)
        cf.setBackground(BG_CARD_PASSIVE);
        cf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.BLACK, 1),
                BorderFactory.createEmptyBorder(1, 1, 1, 1)));

        if (cf != null)
            cf.setVisible(false);
        return cf;
    }

    // Changed to 'public static' to allow ORPanel to reuse this logic
    public static void configureTrainButton(ClickField btn, String text, boolean isBuyable) {
        // 1. Strict Sizing
        btn.setPreferredSize(DIM_TRAIN_BTN);
        btn.setMinimumSize(DIM_TRAIN_BTN);
        btn.setMaximumSize(DIM_TRAIN_BTN);
        btn.setMargin(new Insets(0, 0, 0, 0));

        // 2. Visuals
        if (isBuyable) {
            btn.setBackground(BG_BUY_ACTIVE);
            btn.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
            btn.setToolTipText("Click to Buy " + text);
            btn.setEnabled(true);
        } else {
            // Passive / Empty
            btn.clearPossibleActions(); // CRITICAL: Ensure no stale actions remain
            btn.setBackground(BG_CARD_PASSIVE);
            // Compound border to match the 2px thickness of the active button
            btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.BLACK, 1),
                    BorderFactory.createEmptyBorder(1, 1, 1, 1)));
            btn.setToolTipText(null);
            // Keep enabled to ensure HTML text color (Black) is rendered correctly.
            btn.setEnabled(true);
        }

        // 3. HTML Formatting (Force Black Color)
        // Strip existing HTML tags to prevent nested tag issues if text is reused
        String rawText = (text == null) ? "" : text.replaceAll("\\<.*?\\>", "");
        String label = (rawText.trim().isEmpty()) ? "&nbsp;" : rawText;

        btn.setText("<html><center><font size='4' color='black'><b>" + label + "</b></font></center></html>");

        btn.setOpaque(true);
        btn.setVisible(true);
    }

    protected void setPoolTrainButton(boolean clickable, PossibleAction action) {
        if (poolTrainButtons == null)
            return;

        // 1. Reset
        for (ClickField cf : poolTrainButtons) {
            if (cf != null) {
                cf.setVisible(false);
                cf.clearPossibleActions();
            }
            // Reset to default passive state using helper logic manually or just hide
            // We'll let the loop below handle visible ones.
        }

        java.util.List<net.sf.rails.game.Train> trains = new java.util.ArrayList<>(pool.getTrainList());
        java.util.List<BuyTrain> buyActions = clickable ? possibleActions.getType(BuyTrain.class) : null;

        // 3. Populate
        for (int i = 0; i < trains.size() && i < MAX_POOL_SLOTS; i++) {
            net.sf.rails.game.Train train = trains.get(i);
            RailCard cf = poolTrainButtons[i];

            cf.setTrain(train); // Use RailCard logic
            String cleanName = train.getName().replaceAll("_\\d+$", "");
            cf.setCustomLabel(cleanName);

            boolean canBuy = false;
            if (clickable && buyActions != null) {
                for (BuyTrain ba : buyActions) {
                    if (ba.getTrain() == train) {
                        cf.addPossibleAction(ba);
                        canBuy = true;
                        break;
                    }
                }
            }

            // Apply Styling
            if (canBuy) {
                cf.setBackground(BG_BUY_ACTIVE);
                cf.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
                cf.setToolTipText("Click to Buy " + train.getName());
                cf.setEnabled(true);
            } else {
                cf.setBackground(BG_CARD_PASSIVE);
                cf.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color.BLACK, 1),
                        BorderFactory.createEmptyBorder(1, 1, 1, 1)));
                cf.setToolTipText(null);
                cf.setEnabled(true);
            }
            cf.setVisible(true);
        }
    }

    protected void setNewTrainButton(boolean clickable, PossibleAction action) {
        if (newTrainButton == null)
            return;

        // Note: We do NOT reset the label here, as updateTrainCosts set the specific
        // IPO Train Name.
        newTrainButton.clearPossibleActions();

        // Reset Visuals (Passive default)
        newTrainButton.setBackground(BG_CARD_PASSIVE);
        newTrainButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.BLACK, 1),
                BorderFactory.createEmptyBorder(1, 1, 1, 1)));
        newTrainButton.setEnabled(true);

        if (clickable) {
            newTrainButton.setVisible(true);
            newTrainButton.setEnabled(true);
            newTrainButton.setBackground(BG_BUY); // Green
            newTrainButton.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
            if (action != null) {
                newTrainButton.addPossibleAction(action);
            }
        }
    }

    // Shared Styling Constants (Promoted from local variables for modular access)
    private static final Color BG_PLAYER_COL = Color.WHITE;
    private static final Color BG_DETAILS_COL = new Color(235, 230, 255);
    private static final Color BG_MINOR = Color.BLACK;
    private static final Color FG_MINOR = Color.WHITE;
    private static final Color BG_TRAINS = new Color(176, 224, 230);
    private static final Color BG_BANK = new Color(176, 224, 230);
    // BG_OPERATING is already a class field (line 78)

    // Borders
    private static final Color OUT = Color.BLACK;
    private static final int THICK = 2;
    private static final int THIN = 1;
    private static final javax.swing.border.Border BORDER_THIN = BorderFactory.createMatteBorder(0, 0, 1, 1,
            Color.GRAY);
    private static final javax.swing.border.Border BORDER_OP_THIN = BorderFactory.createMatteBorder(2, 0, 2, 1,
            Color.BLACK);
    private static final javax.swing.border.Border BORDER_BOX = BorderFactory.createLineBorder(OUT, THICK);

    // Train Borders
    private static final javax.swing.border.Border B_TOP_L = BorderFactory.createMatteBorder(THICK, THICK, THIN, THIN,
            OUT);
    private static final javax.swing.border.Border B_TOP_M = BorderFactory.createMatteBorder(THICK, 0, THIN, THIN, OUT);
    private static final javax.swing.border.Border B_TOP_R = BorderFactory.createMatteBorder(THICK, 0, THIN, THICK,
            OUT);
    private static final javax.swing.border.Border B_BOT_L = BorderFactory.createMatteBorder(0, THICK, THICK, THIN,
            OUT);
    private static final javax.swing.border.Border B_BOT_M = BorderFactory.createMatteBorder(0, 0, THICK, THIN, OUT);
    private static final javax.swing.border.Border B_BOT_R = BorderFactory.createMatteBorder(0, 0, THICK, THICK, OUT);


    protected void initFields() {

        // 1. Refresh Counts
        if (gameUIManager != null) {
            np = players.getNumberOfPlayers();
            companies = gameUIManager.getAllPublicCompanies().toArray(new PublicCompany[0]);
            nc = companies.length;
        }

        PublicCompany operatingComp = null;
        net.sf.rails.game.round.RoundFacade currentRound = gameUIManager.getGameManager().getCurrentRound();

        if (currentRound instanceof net.sf.rails.game.OperatingRound) {
            operatingComp = ((net.sf.rails.game.OperatingRound) currentRound).getOperatingCompany();
        } 
        // --- START FIX ---
        // 1835 PFR Fix: Safe Lookup
        else if (currentRound != null 
                 && "PrussianFormationRound".equals(currentRound.getClass().getSimpleName())) {
             for (PublicCompany c : gameUIManager.getAllPublicCompanies()) {
                 if ("PR".equals(c.getId())) {
                     operatingComp = c;
                     break;
                 }
             }
        }




        // 2. DEFENSIVE ALLOCATION
        // Ensure arrays exist even if 'recreate()' is called without 'init()'
        if (compTrainsButtonPanel == null || compTrainsButtonPanel.length != nc) {
            compTrainsButtonPanel = new JPanel[nc];
        }
        if (compSubTrainButtons == null || compSubTrainButtons.length != nc) {
            compSubTrainButtons = new RailCard[nc][MAX_TRAIN_SLOTS];
        }

        // Ensure basic fields are ready
        if (compTrains == null || compTrains.length != nc)
            compTrains = new Field[nc];
        if (compTokens == null || compTokens.length != nc)
            compTokens = new JPanel[nc];

        // Initialize IPO Arrays
        if (ipoPanels == null || ipoPanels.length != nc)
            ipoPanels = new JPanel[nc];
        if (ipoShareCards == null || ipoShareCards.length != nc)
            ipoShareCards = new RailCard[nc];
        if (ipoParLabels == null || ipoParLabels.length != nc)
            ipoParLabels = new javax.swing.JLabel[nc];

        if (poolPanels == null || poolPanels.length != nc)
            poolPanels = new JPanel[nc];
        if (poolShareCards == null || poolShareCards.length != nc)
            poolShareCards = new RailCard[nc];
        if (poolPriceLabels == null || poolPriceLabels.length != nc)
            poolPriceLabels = new javax.swing.JLabel[nc];

        if (playerSharePanels == null || playerSharePanels.length != nc)
            playerSharePanels = new JPanel[nc][np];
        if (playerShareCards == null || playerShareCards.length != nc)
            playerShareCards = new RailCard[nc][np];

        // New array for the isolated Red Dot indicators
        if (playerSoldDots == null || playerSoldDots.length != nc) {
            playerSoldDots = new javax.swing.JLabel[nc][np];
        }

        // 3. Setup Columns
        int col = 0;
        int arrowCol = col++;
        this.compNameCol = col++;
        certPerPlayerXOffset = col;
        col += np;
        certInPoolXOffset = col++;
        certInIPOXOffset = col++;

        if (compCanHoldOwnShares)
            certInTreasuryXOffset = col++;
        compCashXOffset = col++;
        compRevenueXOffset = col++;
        if (hasDirectCompanyIncomeInOr)
            compDirectRevXOffset = col++;
        compTrainsXOffset = col++;
        compTokensXOffset = col++;
        if (compCanBuyPrivates)
            compPrivatesXOffset = col++;
        if (hasCompanyLoans)
            compLoansXOffset = col++;
        if (hasRights)
            rightsXOffset = col++;
        rightCompCaptionXOffset = col++;

        // 4. Setup Rows
        int actual_nc = 0;
        int actual_nb = 0;
        if (companies != null) {
            for (PublicCompany c : companies) {
                if (c.isClosed())
                    continue;
                actual_nc++;
                if (c.hasBonds())
                    actual_nb++;
            }
        }

        int startY = 2;
        certPerPlayerYOffset = startY;
        int currentFooterY = certPerPlayerYOffset + actual_nc + actual_nb;

        playerCashYOffset = currentFooterY++;
        playerCertCountYOffset = currentFooterY++;
        playerPrivatesYOffset = currentFooterY++;
        currentFooterY++; // Space
        playerFixedIncomeYOffset = currentFooterY++;
        playerTimerYOffset = currentFooterY++;
        playerStartOrderYOffset = currentFooterY++;

        // 5. Initialize Grid
        fields = new JComponent[col + 5][playerStartOrderYOffset + 5];
        shareRowVisibilityObservers = new RowVisibility[nc];
        bondsRowVisibilityObservers = new RowVisibility[nc];

        // 6. Execute Sub-Initializers
        initHeaders();
        initCompanyRows(certPerPlayerYOffset, operatingComp);
        initPlayerFooters();
        initTrainMarket();
        initBankAndTimer();

        dummyButton = new ClickField("", "", "", this, buySellGroup);
        updateTrainCosts();
    }

    private void initHeaders() {
        for (int i = 0; i < np; i++) {
            f = upperPlayerCaption[i] = new Caption(players.getPlayerByPosition(i).getName());

            if (i == np - 1) {
                f.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 4, Color.BLACK));
            } else {
                f.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Color.GRAY));
            }

            f.setPreferredSize(DIM_PLAYER);
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            addField(f, certPerPlayerXOffset + i, 1, 1, 1, 0, true);
            gbc.weightx = 0.0;
        }
        f = new Caption(LocalText.getText("POOL"));
        f.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Color.GRAY));
        f.setPreferredSize(DIM_MERGED);
        addField(f, certInPoolXOffset, 1, 1, 1, 0, true);

        f = new Caption(LocalText.getText("IPO"));
        f.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 4, Color.BLACK));
        f.setPreferredSize(DIM_MERGED);
        addField(f, certInIPOXOffset, 1, 1, 1, 0, true);

        if (compCanHoldOwnShares) {
            f = new Caption(LocalText.getText("TREASURY"));
            f.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Color.GRAY));
            f.setPreferredSize(DIM_STD);
            addField(f, certInTreasuryXOffset, 1, 1, 1, 0, true);
        }

        f = new Caption(compCanHoldOwnShares ? LocalText.getText("CASH") : LocalText.getText("TREASURY"));
        f.setBorder(BORDER_THIN);
        f.setPreferredSize(DIM_STD);
        addField(f, compCashXOffset, 1, 1, 1, 0, true);

        f = new Caption(LocalText.getText("REVENUE"));
        f.setBorder(BORDER_THIN);
        f.setPreferredSize(DIM_STD);
        addField(f, compRevenueXOffset, 1, 1, 1, 0, true);
        f = new Caption(LocalText.getText("TRAINS"));
        f.setBorder(BORDER_THIN);
        f.setPreferredSize(DIM_TRAIN);
        addField(f, compTrainsXOffset, 1, 1, 1, 0, true);
        f = new Caption(LocalText.getText("TOKENS"));
        f.setBorder(BORDER_THIN);
        f.setPreferredSize(DIM_TOKENS);
        addField(f, compTokensXOffset, 1, 1, 1, 0, true);
    }

    private void initCompanyRows(int startY, PublicCompany operatingComp) {
        int y = startY;
        java.util.List<PublicCompany> displayList = new java.util.ArrayList<>(gameUIManager.getAllPublicCompanies());
        compNameCaption = new Caption[nc];
        compArrowCaption = new Caption[nc]; // Initialize storage

        // 1. Sort the list
        java.util.Collections.sort(displayList, (c1, c2) -> {
            boolean c1IsPR = "PR".equals(c1.getId());
            boolean c2IsPR = "PR".equals(c2.getId());

            int p1 = c1.getCurrentSpace() != null ? c1.getCurrentSpace().getPrice()
                    : (c1.getStartSpace() != null ? c1.getStartSpace().getPrice() : 0);
            int p2 = c2.getCurrentSpace() != null ? c2.getCurrentSpace().getPrice()
                    : (c2.getStartSpace() != null ? c2.getStartSpace().getPrice() : 0);

            boolean c1Minor = c1IsPR ? (p1 == 0) : !c1.hasStockPrice();
            boolean c2Minor = c2IsPR ? (p2 == 0) : !c2.hasStockPrice();

            if (c1Minor && !c2Minor)
                return -1;
            if (!c1Minor && c2Minor)
                return 1;
            if (c1Minor)
                return Integer.compare(c1.getPublicNumber(), c2.getPublicNumber());
            if (p1 != p2)
                return Integer.compare(p2, p1);
            return Integer.compare(c1.getPublicNumber(), c2.getPublicNumber());
        });

        // Determine Chunk Boundary
        PublicCompany lastMinor = null;
        for (int k = displayList.size() - 1; k >= 0; k--) {
            PublicCompany pc = displayList.get(k);
            boolean isPR = "PR".equals(pc.getId());
            int p = pc.getCurrentSpace() != null ? pc.getCurrentSpace().getPrice()
                    : (pc.getStartSpace() != null ? pc.getStartSpace().getPrice() : 0);
            boolean isMin = isPR ? (p == 0) : !pc.hasStockPrice();
            if (isMin) {
                lastMinor = pc;
                break;
            }
        }

        for (PublicCompany c : displayList) {
            if (c.isClosed())
                continue;

            int i = c.getPublicNumber();
            shareRowVisibilityObservers[i] = new RowVisibility(this, y, c.getInGameModel());
            boolean visible = shareRowVisibilityObservers[i].lastValue();

            boolean isMinor = !c.hasStockPrice();
            boolean isOperating = (c == operatingComp);
            boolean isSR = gameUIManager.getGameManager()
                    .getCurrentRound() instanceof net.sf.rails.game.financial.StockRound;
            boolean hasOwner = c.getPresidentsShare() != null && c.getPresidentsShare().getOwner() instanceof Player;

            boolean isActive;
            if (isSR) {
                isActive = c.hasFloated() || hasOwner;
            } else {
                isActive = c.hasFloated();
            }

            final int B_STD = 1;
            final int B_OP = 2;
            final int B_ZONE = 4;
            boolean isBottomChunk = (c == lastMinor);
            int tHeight = isOperating ? B_OP : 0;
            int bHeight = isBottomChunk ? B_ZONE : (isOperating ? B_OP : B_STD);

            javax.swing.border.Border bNormal = isOperating ? BORDER_OP_THIN : BORDER_THIN;
            javax.swing.border.Border bDet = BorderFactory.createMatteBorder(tHeight, 0, bHeight, 1, Color.BLACK);

            // Border Helper
            java.util.function.BiFunction<Boolean, Boolean, javax.swing.border.Border> getBorder = (isRightEdge,
                    isIPO) -> {
                int t = tHeight;
                int l = 0;
                int b = bHeight;
                int r = (isRightEdge || isIPO) ? B_ZONE : B_STD;
                return BorderFactory.createMatteBorder(t, l, b, r, Color.BLACK);
            };

            // Arrow
            javax.swing.border.Border bArrow = BorderFactory.createMatteBorder(tHeight, 2, bHeight, 1, Color.BLACK);
            
// Store the arrow in the array so initTurn can access it
            compArrowCaption[i] = new Caption(isOperating ? "▶" : "");
            compArrowCaption[i].setForeground(Color.RED.darker());
            compArrowCaption[i].setBackground(Color.WHITE);
            compArrowCaption[i].setOpaque(true);
            compArrowCaption[i].setBorder(bArrow);
            addField(compArrowCaption[i], 0, y, 1, 1, 0, visible);
            
            // Name
            javax.swing.border.Border bName = BorderFactory.createMatteBorder(tHeight, 0, bHeight, 1, Color.BLACK);
            compNameCaption[i] = new Caption(c.getId());
            compNameCaption[i].setForeground(isMinor ? FG_MINOR : c.getFgColour());
            compNameCaption[i].setBackground(isMinor ? BG_MINOR : c.getBgColour());
            compNameCaption[i].setBorder(bName);
            compNameCaption[i].setOpaque(true);
            HexHighlightMouseListener.addMouseListener(compNameCaption[i], gameUIManager.getORUIManager(), c, false);
            addField(compNameCaption[i], compNameCol, y, 1, 1, 0, visible);

            // Player Shares
            for (int j = 0; j < np; j++) {

               // 1. Create Container (Player Slot)
                // Use BorderLayout for precise Left/Center/Right alignment
                playerSharePanels[i][j] = new JPanel(new BorderLayout(0, 0));
                playerSharePanels[i][j].setOpaque(false);

                // ENFORCE SLOT WIDTH
                playerSharePanels[i][j].setPreferredSize(DIM_PLAYER_SLOT);
                playerSharePanels[i][j].setMinimumSize(DIM_PLAYER_SLOT);
                playerSharePanels[i][j].setMaximumSize(DIM_PLAYER_SLOT);

                // 2. Create the Card (Fixed Width)
                playerShareCards[i][j] = new RailCard((net.sf.rails.game.Train) null, buySellGroup);
                playerShareCards[i][j].addActionListener(this);


                // DYNAMIC CARD WIDTH: Full Width (60) for Minors, Std (46) for Majors
                Dimension cardDim = c.hasStockPrice() ? DIM_STD : DIM_MINOR;
                
                playerShareCards[i][j].setPreferredSize(cardDim);
                playerShareCards[i][j].setMinimumSize(cardDim);
                playerShareCards[i][j].setMaximumSize(cardDim);
                playerShareCards[i][j].setCompactMode(true);

                // WRAPPER: Centers the card within the remaining space
                JPanel cardWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                cardWrapper.setOpaque(false);
                cardWrapper.add(playerShareCards[i][j]);

                // 3. Create the Dot (Fixed Width)
                playerSoldDots[i][j] = new javax.swing.JLabel("●");
                playerSoldDots[i][j].setForeground(Color.RED);
                playerSoldDots[i][j].setFont(new Font("SansSerif", Font.BOLD, 14)); // Slightly smaller for fit
                playerSoldDots[i][j].setHorizontalAlignment(SwingConstants.CENTER);
                playerSoldDots[i][j].setVerticalAlignment(SwingConstants.CENTER);
                playerSoldDots[i][j].setVisible(false);

                // ENFORCE DOT WIDTH
                playerSoldDots[i][j].setPreferredSize(DIM_DOT);
                playerSoldDots[i][j].setMinimumSize(DIM_DOT);
                playerSoldDots[i][j].setMaximumSize(DIM_DOT);

                // 4. Assemble: [Card (Center)] [Dot (East)]
                playerSharePanels[i][j].add(cardWrapper, BorderLayout.CENTER);

                // Only add the dot slot for Companies that have a stock price (Majors)
                if (c.hasStockPrice()) {
                    playerSharePanels[i][j].add(playerSoldDots[i][j], BorderLayout.EAST);
                }
                // --- END FIX ---

                // 5. Add to Grid
                int wideGapPosition = ((j == 0) ? WIDE_LEFT : 0) + ((j == np - 1) ? WIDE_RIGHT : 0);
                addField(playerSharePanels[i][j], certPerPlayerXOffset + j, y, 1, 1, wideGapPosition, visible);

            }

            // POOL REPLACEMENT
            // --- START FIX ---
            // 1. Create Container (Pool Slot) - Use BorderLayout
            poolPanels[i] = new JPanel(new BorderLayout(0, 0));
            poolPanels[i].setBorder(getBorder.apply(false, false));
            poolPanels[i].setOpaque(true);

            // ENFORCE POOL SLOT WIDTH
            poolPanels[i].setPreferredSize(DIM_POOL_SLOT);
            poolPanels[i].setMinimumSize(DIM_POOL_SLOT);
            poolPanels[i].setMaximumSize(DIM_POOL_SLOT);

            // 2. Create RailCard (Fixed Width)
            poolShareCards[i] = new RailCard((net.sf.rails.game.Train) null, buySellGroup);
            poolShareCards[i].addActionListener(this);

            poolShareCards[i].setPreferredSize(DIM_STD);
            poolShareCards[i].setMinimumSize(DIM_STD);
            poolShareCards[i].setMaximumSize(DIM_STD);
            poolShareCards[i].setCompactMode(true);
            poolShareCards[i].setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.BLACK, 1),
                    BorderFactory.createEmptyBorder(1, 1, 1, 1)));

            // WRAPPER: Centers the card
            JPanel poolCardWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            poolCardWrapper.setOpaque(false);
            poolCardWrapper.add(poolShareCards[i]);

            poolPanels[i].add(poolCardWrapper, BorderLayout.CENTER);

            // 3. Create Price Label (Fixed Width)
            poolPriceLabels[i] = new Caption("");
            Font baseFont = poolPriceLabels[i].getFont();
            poolPriceLabels[i].setFont(baseFont.deriveFont(Font.BOLD));
            poolPriceLabels[i].setForeground(new Color(0, 0, 128));
            poolPriceLabels[i].setHorizontalAlignment(SwingConstants.RIGHT); // Align text to right of its box

            // ENFORCE PRICE WIDTH
            poolPriceLabels[i].setPreferredSize(DIM_PRICE);
            poolPriceLabels[i].setMinimumSize(DIM_PRICE);
            poolPriceLabels[i].setMaximumSize(DIM_PRICE);

            // Place Price on the RIGHT (East)
            poolPanels[i].add(poolPriceLabels[i], BorderLayout.EAST);
            // --- END FIX ---

            // 4. Add to Grid
            addField(poolPanels[i], certInPoolXOffset, y, 1, 1, 0, visible);

            // Bypass old field creation

            // 1. Create Container Panel
            ipoPanels[i] = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
            // Apply Borders (Top, Left, Bottom, Right) - Matching previous style
            // (Right=Thick if end)
            ipoPanels[i].setBorder(getBorder.apply(false, true));
            ipoPanels[i].setOpaque(true);

            // 2. Create RailCard for Share %
            ipoShareCards[i] = new RailCard((net.sf.rails.game.Train) null, buySellGroup);
            ipoShareCards[i].addActionListener(this);
            ipoShareCards[i].setPreferredSize(DIM_TRAIN_BTN);
            ipoShareCards[i].setMinimumSize(DIM_TRAIN_BTN);
            ipoShareCards[i].setMaximumSize(DIM_TRAIN_BTN);
            ipoShareCards[i].setCompactMode(true);
            ipoShareCards[i].setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.BLACK, 1),
                    BorderFactory.createEmptyBorder(1, 1, 1, 1)));

            ipoPanels[i].add(ipoShareCards[i]);

            ipoParLabels[i] = new Caption(""); // Use Caption

            // Apply Distinct Money Font (Bold + Dynamic Size)
            ipoParLabels[i].setFont(ipoParLabels[i].getFont().deriveFont(Font.BOLD));

            ipoParLabels[i].setForeground(Color.BLACK); // Standard Black for Par
            ipoParLabels[i].setHorizontalAlignment(SwingConstants.LEFT);

            ipoPanels[i].add(ipoParLabels[i]);

            // 4. Add Panel to Grid (Replace old certInIPO)
            addField(ipoPanels[i], certInIPOXOffset, y, 1, 1, 0, visible);

            // Note: We DO NOT add certInIPO or certInIPOButton here anymore.

            if (compCanHoldOwnShares) {
                f = certInTreasury[i] = new Field(c.getPortfolioModel().getShareModel(c));
                f.setBackground(isOperating ? BG_OPERATING : (isMinor || !isActive ? BG_INACTIVE : BG_POOL));
                f.setOpaque(true);
                f.setBorder(getBorder.apply(true, false));
                f.setPreferredSize(DIM_STD);
                addField(f, certInTreasuryXOffset, y, 1, 1, 0, visible);
                f = certInTreasuryButton[i] = new ClickField(certInTreasury[i].getText(), BUY_FROM_POOL_CMD, "", this,
                        buySellGroup);
                addField(f, certInTreasuryXOffset, y, 1, 1, 0, false);
            }

            // DETAILS (Cash, Rev, Trains, Tokens)
            f = compCash[i] = new Field(c.getPurseMoneyModel()) {
                @Override
                public void setText(String t) {
                    boolean isMajor = c.hasStockPrice();
                    boolean hasStarted = c.hasFloated();
                    if (isMajor && !hasStarted) {
                        super.setText("");
                    } else {
                        super.setText(t);
                    }
                }
            };
            f.setBackground(isOperating ? BG_OPERATING : (!isActive ? BG_INACTIVE : BG_MAUVE));
            f.setOpaque(true);
            f.setBorder(bDet);
            f.setPreferredSize(new Dimension(60, 25));
            addField(f, compCashXOffset, y, 1, 1, 0, visible);
            f = compCashButton[i] = new ClickField(compCash[i].getText(), CASH_CORRECT_CMD, "", this, buySellGroup);
            addField(f, compCashXOffset, y, 1, 1, 0, false);

            f = compRevenue[i] = new Field(c.getLastRevenueModel()) {
                @Override
                public void setText(String t) {
                    if (t == null || t.trim().length() == 0) {
                        super.setText("");
                        return;
                    }
                    boolean isZero = false;
                    try {
                        if (Integer.parseInt(t.trim()) == 0)
                            isZero = true;
                    } catch (Exception e) {
                    }
                    String suffix = " +";
                    String color = "black";
                    try {
                        Object rawAlloc = c.getLastRevenueAllocation();
                        if (!isZero && rawAlloc != null) {
                            String alloc = rawAlloc.toString();
                            if (alloc.contains("Withhold")) {
                                suffix = " -";
                                color = "#FF0000";
                            } else if (alloc.contains("Split")) {
                                suffix = " \u00B1";
                                color = "#000000ff";
                            }
                        }
                    } catch (Exception e) {
                    }
                    super.setText("<html><div align='right'><font color='" + color + "'><b>" + t + suffix
                            + "</b></font></div></html>");
                }
            };
            f.setBackground(isOperating ? BG_OPERATING : (!isActive ? BG_INACTIVE : BG_MAUVE));
            f.setOpaque(true);
            f.setBorder(bDet);
            f.setPreferredSize(new Dimension(60, 25));
            addField(f, compRevenueXOffset, y, 1, 1, 0, visible);

            f = compTrains[i] = new Field(c.getPortfolioModel().getTrainsModel()) {
                @Override
                public void setText(String t) {
                    if (c.hasStockPrice() && !c.hasFloated()) {
                        super.setText("");
                        return;
                    }
                    java.util.List<net.sf.rails.game.Train> trains = new java.util.ArrayList<>(
                            c.getPortfolioModel().getTrainList());
                    int limit = c instanceof net.sf.rails.game.PublicCompany
                            ? ((net.sf.rails.game.PublicCompany) c).getCurrentTrainLimit()
                            : trains.size();
                    if (limit < trains.size())
                        limit = trains.size();

                    StringBuilder sb = new StringBuilder(
                            "<html><table border='0' cellspacing='3' cellpadding='0'><tr>");
                    for (net.sf.rails.game.Train tr : trains) {
                        sb.append("<td width='60' align='center' bgcolor='#FFFFF0' style='border:1px solid #404040'>")
                                .append("<font size='4' color='black'><b>").append(tr.getType().getName())
                                .append("</b></font></td>");
                    }
                    for (int j = trains.size(); j < limit; j++) {
                        sb.append("<td width='60' align='center' bgcolor='#E0E0E0' style='border:1px dotted #808080'>")
                                .append("<font size='4' color='black'>_</font></td>");
                    }
                    sb.append("</tr></table></html>");
                    super.setText(sb.toString());
                }
            };
            f.setBackground(isOperating ? BG_OPERATING : (!isActive ? BG_INACTIVE : BG_MAUVE));
            f.setOpaque(true);
            f.setBorder(bDet);
            f.setPreferredSize(DIM_TRAIN);
            addField(f, compTrainsXOffset, y, 1, 1, 0, visible);

            compTrainsButtonPanel[i] = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
            compTrainsButtonPanel[i].setBorder(bDet);
            compTrainsButtonPanel[i].setBackground(isOperating ? BG_OPERATING : (!isActive ? BG_INACTIVE : BG_MAUVE));
            compTrainsButtonPanel[i].setOpaque(true);

            compSubTrainButtons[i] = new RailCard[MAX_TRAIN_SLOTS]; // Change Type
            for (int t = 0; t < MAX_TRAIN_SLOTS; t++) {
                RailCard cf = createTrainButton(); // Use new creator
                compSubTrainButtons[i][t] = cf;
                compTrainsButtonPanel[i].add(cf);
            }

            addField(compTrainsButtonPanel[i], compTrainsXOffset, y, 1, 1, 0, false);

            compTokens[i] = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
            compTokens[i].setOpaque(true);
            compTokens[i].setBackground(isOperating ? BG_OPERATING : (!isActive ? BG_INACTIVE : BG_MAUVE));
            compTokens[i].setBorder(bDet);
            compTokens[i].setPreferredSize(DIM_TOKENS);
            updateCompanyTokenDisplay(i, c, compTokens[i]);
            addField(compTokens[i], compTokensXOffset, y, 1, 1, 0, visible);

            y++;
        }
    }

    private void initPlayerFooters() {
        // Cash
        f = new Caption(LocalText.getText("CASH"));
        f.setBorder(BORDER_THIN);
        addField(f, compNameCol, playerCashYOffset, 1, 1, 0, true);
        for (int i = 0; i < np; i++) {
            f = playerCash[i] = new Field(players.getPlayerByPosition(i).getWallet());
            f.setBorder(BORDER_THIN);
            Font currentFont = f.getFont();
            f.setFont(currentFont.deriveFont(Font.BOLD, currentFont.getSize()));
            f.setPreferredSize(DIM_PLAYER);
            gbc.weightx = 1.0;
            addField(f, certPerPlayerXOffset + i, playerCashYOffset, 1, 1, 0, true);
            gbc.weightx = 0.0;
            f = playerCashButton[i] = new ClickField("", CASH_CORRECT_CMD, "", this, buySellGroup);
            addField(f, certPerPlayerXOffset + i, playerCashYOffset, 1, 1, 0, false);
        }

        // Certs
        f = new Caption("Certs");
        f.setBorder(BORDER_THIN);
        addField(f, compNameCol, playerCertCountYOffset, 1, 1, 0, true);
        for (int i = 0; i < np; i++) {
            f = playerCertCount[i] = new Field(players.getPlayerByPosition(i).getCertCountWithLimitModel(), false,
                    true);
            f.setBorder(BORDER_THIN);
            f.setPreferredSize(DIM_PLAYER);
            gbc.weightx = 1.0;
            addField(f, certPerPlayerXOffset + i, playerCertCountYOffset, 1, 1, 0, true);
            gbc.weightx = 0.0;
        }

        // Privates
        f = new Caption(LocalText.getText("PRIVATES"));
        f.setBorder(BORDER_THIN);
        addField(f, compNameCol, playerPrivatesYOffset, 1, 2, 0, true);
        playerPrivatesPanel = new JPanel[np];
        for (int i = 0; i < np; i++) {
            playerPrivatesPanel[i] = new JPanel();
            playerPrivatesPanel[i].setLayout(new BoxLayout(playerPrivatesPanel[i], BoxLayout.Y_AXIS));
            playerPrivatesPanel[i].setBorder(BORDER_THIN);
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.BOTH;
            addField(playerPrivatesPanel[i], certPerPlayerXOffset + i, playerPrivatesYOffset, 1, 2, 0, true);
            gbc.weightx = 0.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
        }
    }

    private void initTrainMarket() {
        int trainY_Header = playerPrivatesYOffset;
        int trainY_Data = playerPrivatesYOffset + 1;
        int colUsed = currPriceXOffset;
        int colCurr = certInIPOXOffset;
        if (colCurr <= colUsed)
            colCurr = colUsed + 1;
        int colFut = colCurr + 1;
        int spanFut = (rightCompCaptionXOffset - colFut) + 1;
        if (spanFut < 1)
            spanFut = 1;

        // Headers
        f = new Caption("Used");
        f.setFont(new Font("SansSerif", Font.BOLD, 11));
        f.setBorder(B_TOP_L);
        f.setBackground(BG_TRAINS);
        f.setOpaque(true);
        addField(f, colUsed, trainY_Header, 1, 1, 0, true);

        f = new Caption("Current");
        f.setFont(new Font("SansSerif", Font.BOLD, 11));
        f.setBorder(B_TOP_M);
        f.setBackground(BG_TRAINS);
        f.setOpaque(true);
        addField(f, colCurr, trainY_Header, 1, 1, 0, true);

        f = new Caption("Future");
        f.setFont(new Font("SansSerif", Font.BOLD, 11));
        f.setBorder(B_TOP_R);
        f.setBackground(BG_TRAINS);
        f.setOpaque(true);
        addField(f, colFut, trainY_Header, spanFut, 1, 0, true);

        // Data Panels
        poolTrainsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 0));
        poolTrainsPanel.setBorder(B_BOT_L);
        poolTrainsPanel.setBackground(BG_TRAINS);
        poolTrainsPanel.setOpaque(true);

        poolTrainButtons = new RailCard[MAX_POOL_SLOTS];
        for (int i = 0; i < MAX_POOL_SLOTS; i++) {
            poolTrainButtons[i] = createTrainButton(); // Use new RailCard helper
            poolTrainsPanel.add(poolTrainButtons[i]);
        }

        addField(poolTrainsPanel, colUsed, trainY_Data, 1, 1, 0, true);

        newTrainsPanel = new JPanel(new GridBagLayout());
        newTrainsPanel.setBorder(B_BOT_M);
        newTrainsPanel.setBackground(BG_TRAINS);
        newTrainsPanel.setOpaque(true);
        newTrainButton = createTrainButton(); // Use new RailCard helper
        newTrainInfoLabel = new javax.swing.JLabel(" ");
        newTrainInfoLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        newTrainInfoLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        newTrainInfoLabel.setForeground(Color.BLACK);
        GridBagConstraints gbcSub = new GridBagConstraints();
        gbcSub.gridx = 0;
        gbcSub.gridy = 0;
        gbcSub.insets = new Insets(2, 0, 0, 0);
        newTrainsPanel.add(newTrainButton, gbcSub);
        gbcSub.gridy = 1;
        gbcSub.insets = new Insets(0, 0, 2, 0);
        newTrainsPanel.add(newTrainInfoLabel, gbcSub);
        addField(newTrainsPanel, colCurr, trainY_Data, 1, 1, 0, true);

        futureTrainsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 0));
        futureTrainsPanel.setBorder(B_BOT_R);
        futureTrainsPanel.setBackground(BG_TRAINS);
        futureTrainsPanel.setOpaque(true);

        futureTrainButtons = new RailCard[MAX_FUTURE_SLOTS];
        futureTrainInfoLabels = new javax.swing.JLabel[MAX_FUTURE_SLOTS];
        for (int i = 0; i < MAX_FUTURE_SLOTS; i++) {
            JPanel slot = new JPanel(new BorderLayout());
            slot.setOpaque(false);
            slot.setPreferredSize(new Dimension(42, 50));
            futureTrainButtons[i] = createTrainButton(); // Use new RailCard helper
            slot.add(futureTrainButtons[i], BorderLayout.NORTH);
            futureTrainInfoLabels[i] = new javax.swing.JLabel("", javax.swing.SwingConstants.CENTER);
            futureTrainInfoLabels[i].setFont(new Font("SansSerif", Font.PLAIN, 10));
            slot.add(futureTrainInfoLabels[i], BorderLayout.CENTER);
            futureTrainsPanel.add(slot);
        }

        gbc.anchor = GridBagConstraints.NORTH;
        gbc.fill = GridBagConstraints.BOTH;
        addField(futureTrainsPanel, colFut, trainY_Data, spanFut, 1, 0, true);
    }

    private void initBankAndTimer() {
        int bankY = playerTimerYOffset;
        int bankX = currPriceXOffset; // Matches colUsed

        f = new Caption("Fixed Inc");
        f.setBorder(BORDER_THIN);
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        addField(f, compNameCol, playerFixedIncomeYOffset, 1, 1, 0, true);
        for (int i = 0; i < np; i++) {
            f = playerFixedIncome[i] = new Field("");
            f.setBorder(BORDER_THIN);
            f.setPreferredSize(DIM_PLAYER);
            gbc.weightx = 1.0;
            addField(f, certPerPlayerXOffset + i, playerFixedIncomeYOffset, 1, 1, 0, true);
            gbc.weightx = 0.0;
        }

        f = new Caption("Bank Cash");
        f.setBorder(BORDER_BOX);
        f.setBackground(BG_BANK);
        f.setOpaque(true);
        f.setFont(new Font("SansSerif", Font.BOLD, 12));
        addField(f, bankX, bankY, 1, 1, 0, true);

        bankCash = new Field(bank.getPurse());
        bankCash.setBorder(BORDER_BOX);
        bankCash.setBackground(BG_BANK);
        bankCash.setOpaque(true);
        Font bankFont = bankCash.getFont();
        bankCash.setFont(bankFont.deriveFont(Font.BOLD, bankFont.getSize()));
        addField(bankCash, bankX + 1, bankY, 1, 1, 0, true);

        f = new Caption("Time");
        f.setBorder(BORDER_THIN);
        addField(f, compNameCol, playerTimerYOffset, 1, 1, 0, true);
        for (int i = 0; i < np; i++) {
            f = playerTimer[i] = new Field(players.getPlayerByPosition(i).getTimeBankModel()) {
                @Override
                public void setText(String t) {
                    try {
                        int val = Integer.parseInt(t);
                        if (val < 0) {
                            this.setForeground(Color.RED);
                        } else {
                            this.setForeground(Color.BLACK);
                        }
                        int absVal = Math.abs(val);
                        int min = absVal / 60;
                        int sec = absVal % 60;
                        super.setText(String.format("%s%02d:%02d", val < 0 ? "-" : "", min, sec));
                    } catch (Exception e) {
                        super.setText(t);
                    }
                }
            };
            f.setBorder(BORDER_THIN);
            gbc.weightx = 1.0;
            addField(f, certPerPlayerXOffset + i, playerTimerYOffset, 1, 1, 0, true);
            gbc.weightx = 0.0;
        }
    }

}