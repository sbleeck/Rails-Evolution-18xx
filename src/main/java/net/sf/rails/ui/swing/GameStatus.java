package net.sf.rails.ui.swing;

import com.google.common.collect.Lists;
import net.sf.rails.common.GuiDef;
import net.sf.rails.common.LocalText;
import net.sf.rails.game.CompanyManager;
import net.sf.rails.game.Player;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.financial.Bank;
import net.sf.rails.game.financial.PublicCertificate;
import net.sf.rails.game.financial.StockRound;
import net.sf.rails.game.model.BondsModel;
import net.sf.rails.game.model.PortfolioModel;
import net.sf.rails.game.state.MoneyOwner;
import net.sf.rails.sound.SoundManager;
import net.sf.rails.ui.swing.elements.Caption;
import net.sf.rails.ui.swing.elements.ClickField;
import net.sf.rails.ui.swing.elements.Field;
import net.sf.rails.ui.swing.elements.RadioButtonDialog;
import net.sf.rails.ui.swing.hexmap.HexHighlightMouseListener;
import net.sf.rails.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rails.game.action.*;
import rails.game.correct.CashCorrectionAction;
import rails.game.correct.TrainCorrectionAction;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import net.sf.rails.ui.swing.elements.RailCard;
import java.util.Map;

/**
 * This class is incorporated into StatusWindow and displays the bulk of
 * rails.game status information.
 * 
 * Core Functionality
 * 
 * This class serves as the primary dashboard for the Rails game application. It
 * is a monolithic UI controller responsible for rendering the game state matrix
 * (Players/Companies vs. Assets).
 * 
 * Status Matrix: Dynamically constructs a GridPanel displaying the intersection
 * of Companies (rows) and Players/Assets (columns). Key data includes share
 * distribution (certPerPlayer), treasury holdings, IPO/Pool availability, and
 * company finances (Cash, Revenue, Trains).
 * 
 * Interactive Controls: Acts as the primary input surface for financial
 * actions. It converts UI clicks (on ClickField or RailCard elements) into game
 * commands like BuyFromIPO, Sell, or BuyTrain.
 * 
 * Dynamic Layout: The grid structure is not fixed; it calculates row/column
 * offsets (certPerPlayerXOffset, compTrainsXOffset) at runtime based on game
 * parameters (e.g., whether "Bonds" or "Rights" are enabled in the rules).
 * 
 */
public class GameStatus extends GridPanel {

    // Width Definitions
    // STRUCTURAL CONSTANTS FOR "SLOT" SYSTEM

    // private final Dimension DIM_ARROW = new Dimension(20, 20); // 1. Visible
    // Arrow
    private final Dimension DIM_STD = new Dimension(52, 20); // 5. Fix clipping (was 45, now 52)

    // Width Definitions
    private final Dimension DIM_PLAYER = new Dimension(60, 20);
    private final Dimension DIM_TOKENS = new Dimension(55, 20);

    // Flexible height for privates (Point 4b) - Width 85, Height 0 (auto)
    private final Dimension DIM_TRAIN = new Dimension(40, 20);

    public static final Color BG_BUY_ACTIVE = new Color(144, 238, 144); // Light Green (#90EE90) - Standard "Buy"\
    public static final Color BG_DISCARD_VOLUNTARY = Color.CYAN; // Light Blue (#ADD8E6)
    // for Special Actions
    private final Color BG_SELL_ALERT = new Color(250, 128, 114); // Salmon Pink (#FA8072) - Shares Sell
    public static final Color BG_CARD_PASSIVE = new Color(255, 255, 240); // Beige (Must be static for static method

    // 1. DEFINE MASTER GREY (Standardize 235 vs 225 clash)
    private static final Color BG_UNIFIED_GREY = new Color(225, 225, 225); // The chosen "Spotlight Inactive" Grey

    // 2. APPLY TO CONSTANTS
    private final Color BG_INACTIVE = BG_UNIFIED_GREY; // Fixes Inactive Rows
    private final Color BG_POOL = new Color(230, 240, 255);
    private final Color BG_MAUVE = new Color(235, 230, 255); // Standard Company Data Background
    private final Color BG_OPERATING = Color.WHITE;

    // Spotlight System Constants
    private static final Color BG_SPOTLIGHT_ACTIVE = Color.WHITE;
    private static final Color BG_SPOTLIGHT_INACTIVE = BG_UNIFIED_GREY;
    private static final javax.swing.border.Border BORDER_DEFAULT = BorderFactory.createMatteBorder(0, 0, 1, 1,
            Color.GRAY);

    // Alias for Share Buying to match Trains
    final Color BG_BUY = BG_BUY_ACTIVE; // Use Light Green
    final Color BG_SELL = BG_SELL_ALERT; // Use Muted Rose Red

    final Color BG_SLOT_AVAILABLE = new Color(220, 255, 220);

    // Visual Constants for "Beige + Border" Style
    private static final Color BORDER_COL_BUY = new Color(0, 160, 0); // Strong Green
    private static final Color BORDER_COL_SELL = new Color(220, 20, 60); // Crimson Red
    private static final int BORDER_THICKNESS = 3;
    // 1. HEADER COLOR: Uniform Light Grey for all column headers
    private static final Color BG_HEADER = new Color(224, 224, 224);

    // 2. Larger Train Cards
    private static final Dimension DIM_TRAIN_BTN = new Dimension(32, 18);

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
    // Universal Revenue Split Columns
    protected Field[] compRetained;
    protected int compRetainedXOffset, compRetainedYOffset;
    protected Field[] compTrains;
    protected int compTrainsXOffset, compTrainsYOffset;
    protected JPanel[] compTokens;
    // protected Caption[] compArrowCaption; // Store references to the arrows
    protected Caption[] compNameCaption;
    protected int compTokensXOffset, compTokensYOffset;
    protected JPanel[] compPrivatesPanel;
    protected int compPrivatesXOffset, compPrivatesYOffset;
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
    protected CertLimitGauge[] playerCertCount;
    protected int playerCertCountXOffset, playerCertCountYOffset;
    protected JComponent[] compLoans;
    protected Field bankCash;
    protected Field interestRateField;

    protected int newTrainsXOffset, newTrainsYOffset;
    protected int futureTrainsXOffset, futureTrainsYOffset, futureTrainsWidth;
    protected int rightCompCaptionXOffset;

    protected JPanel specialActionContainer;
    protected JPanel specialActionPanel;
    protected javax.swing.JLabel specialActionLabel;

    private final int MAX_TRAIN_SLOTS = 4; // Max trains to display per company
    private final int MAX_FUTURE_SLOTS = 6; // Max future trains to display

    protected net.sf.rails.ui.swing.elements.BondsHeatbarPanel bondsHeatbarPanel;

    // Track previous times to detect jumps (penalties/undo)
    private int[] lastPlayerTimes;

    protected ClickField poolTrainsButton;
    protected javax.swing.JLabel gameTimeLabel;
    protected javax.swing.Timer uiRefreshTimer;
    private javax.swing.JLabel parentTimerLabel = null;
    private javax.swing.JLabel parentStatusLabel = null;

    // Variable to persist the user's zoom/font setting across component recreations
    private Font stickyFont = null;
    private RoundCounterPanel roundCounterPanel;

    private final List<String> selectedPrivateIds = new ArrayList<>();

    @Override
    public void setFont(Font f) {
        super.setFont(f);
        // Capture the font whenever StatusWindow updates it (e.g. Zoom In/Out)
        if (f != null) {
            this.stickyFont = f;
        }
    }

    /**
     * Scans the parent StatusWindow to find the "Pause" button (which lives in the
     * footer).
     * Injects the RoundCounter into that same button panel.
     */
    private void hijackParentComponents() {
        // Stop if already injected
        if (roundCounterPanel != null && roundCounterPanel.getParent() != null)
            return;
        if (parent == null)
            return;

        // 1. Find the "Pause" button using Breadth-First Search
        // We look for the button to identify the correct panel in the footer.
        java.util.Queue<Component> queue = new java.util.LinkedList<>();
        queue.add(parent);

        Component pauseBtn = null;

        while (!queue.isEmpty()) {
            Component c = queue.poll();
            if (c instanceof javax.swing.AbstractButton) {
                String txt = ((javax.swing.AbstractButton) c).getText();
                // Robust check for "Pause", ignoring case or stray spaces
                if (txt != null && txt.trim().equalsIgnoreCase("Pause")) {
                    pauseBtn = c;
                    break;
                }
            }
            if (c instanceof Container) {
                for (Component child : ((Container) c).getComponents())
                    queue.add(child);
            }
        }

        if (pauseBtn == null)
            return;

        // 2. Identify Structure
        Container buttonPanel = pauseBtn.getParent(); // The panel holding [Pause][Undo]...

        // 2b. UNIFORM STYLING: Force all footer buttons to look like standard buttons
        // This fixes the "dissimilar blue rectangles" issue.
        for (Component comp : buttonPanel.getComponents()) {
            if (comp instanceof javax.swing.AbstractButton) {
                javax.swing.AbstractButton btn = (javax.swing.AbstractButton) comp;

                // A. Strip HTML (Standardize text appearance)
                String cleanText = btn.getText().replaceAll("\\<.*?\\>", "");
                btn.setText(cleanText);

                // B. Apply Standard "Button" Look (Gray + 3D Border)
                btn.setContentAreaFilled(true);
                btn.setOpaque(true);
                btn.setBackground(new Color(238, 238, 238)); // Standard Light Gray
                btn.setForeground(Color.BLACK); // Always Black Text

                // C. Add Depth (Raised Bevel = "Clickable" look)
                btn.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createRaisedBevelBorder(),
                        BorderFactory.createEmptyBorder(3, 12, 3, 12) // Padding
                ));

                // D. Uniform Font
                btn.setFont(new Font("SansSerif", Font.BOLD, 12));

                // E. Fix Focus Painting (Removes ugly dotted lines)
                btn.setFocusPainted(false);
            }
        }
        Container footer = buttonPanel.getParent(); // The strip at the bottom of the window

        if (footer == null)
            return;

        // Prevent recursive injection if run multiple times
        if (roundCounterPanel.getParent() == footer || roundCounterPanel.getParent() == buttonPanel)
            return;

        // 3. The Wrapper Strategy
        // We cannot add directly to 'buttonPanel' because it likely uses GridLayout.
        // Adding a component there would resize all buttons.
        // Instead, we replace 'buttonPanel' in the footer with a 'wrapper' that holds
        // both.

        // A. Capture original constraints/index to put the wrapper back in the exact
        // same spot
        LayoutManager lm = footer.getLayout();
        Object constraints = null;
        if (lm instanceof BorderLayout)
            constraints = ((BorderLayout) lm).getConstraints(buttonPanel);
        else if (lm instanceof GridBagLayout)
            constraints = ((GridBagLayout) lm).getConstraints(buttonPanel);

        int idx = -1;
        Component[] children = footer.getComponents();
        for (int i = 0; i < children.length; i++) {
            if (children[i] == buttonPanel) {
                idx = i;
                break;
            }
        }

        // B. Remove original button panel
        footer.remove(buttonPanel);

        // C. Create Wrapper (GridBagLayout for maximum control)
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();

        // Slot 0: The Original Buttons
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0; // Do not force stretch, let it be natural size
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.anchor = GridBagConstraints.WEST;
        wrapper.add(buttonPanel, gbc);

        // Slot 1: The Round Counter (To the RIGHT of buttons)
        gbc.gridx = 1;
        gbc.insets = new Insets(0, 15, 0, 0); // 15px Gap
        gbc.fill = GridBagConstraints.NONE;
        wrapper.add(roundCounterPanel, gbc);

        // D. Re-inject Wrapper into Footer
        if (constraints != null) {
            footer.add(wrapper, constraints);
        } else {
            if (idx > -1)
                footer.add(wrapper, idx);
            else
                footer.add(wrapper);
        }

        footer.revalidate();
        footer.repaint();
    }

    protected ClickField newTrainsButton;

    // New Containers for Train UI (Pool & IPO)

    // Buttons within those containers
    protected javax.swing.JLabel newTrainQtyLabel; // The "Qty: 2" text below

    protected ClickField futureTrainsButton;
    // Config
    private final int MAX_POOL_SLOTS = 3;
    private final int MAX_IPO_SLOTS = 3; // Create space for 2 IPO trains
    protected javax.swing.JLabel[] newTrainQtyLabels;
    protected RailCard[] newTrainButtons;

    // 1. Restore the missing panel array
    protected JPanel[] compTrainsButtonPanel;

    // 2. Define Train Buttons as RailCards
    protected RailCard[][] compSubTrainButtons;
    protected RailCard[] poolTrainButtons;
    // protected RailCard newTrainButton;
    protected RailCard[] futureTrainButtons;

    // 3. Labels and Panels
    protected JPanel poolTrainsPanel;
    protected JPanel newTrainsPanel;
    protected JPanel futureTrainsPanel;
    protected javax.swing.JLabel[] poolTrainInfoLabels; // New array for Pool text

    protected javax.swing.JLabel newTrainInfoLabel;
    protected javax.swing.JLabel[] futureTrainInfoLabels;

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
    protected JPanel[] treasuryPanels;
    protected RailCard[] treasuryShareCards;

    protected boolean hasOSI = false;
    protected PortfolioModel osi;
    protected JPanel[] osiPanels;
    protected RailCard[] osiShareCards;
    protected javax.swing.JLabel[] osiLabels;
    protected int certInOSIXOffset;

    protected JPanel[] poolPanels;
    protected RailCard[] poolShareCards;
    protected javax.swing.JLabel[] poolPriceLabels;

    protected JPanel[][] playerSharePanels;
    protected RailCard[][] playerShareCards;
    protected javax.swing.JLabel[][] playerSoldDots;
    private LinearRoundTracker linearRoundTracker;

    public GameStatus() {
        super();
    }

    // Near other player-related Field declarations (around line 170)
    protected Field[] playerTimer;
    protected int playerTimerXOffset, playerTimerYOffset;

    private String getAbbreviatedTrainName(String name) {
        if (name == null)
            return "";
        // Remove trailing IDs like _1, _2 if present
        String clean = name.replaceAll("_\\d+$", "");
        if (clean.length() > 3) {
            return clean.substring(0, 3);
        }
        return clean;
    }

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
        boolean is1817 = "1817".equals(gameUIManager.getGameManager().getGameName());
        hasCompanyLoans = gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.HAS_ANY_COMPANY_LOANS) || is1817;

        hasRights = gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.HAS_ANY_RIGHTS);
        hasBonds = gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.HAS_BONDS);
        hasDirectCompanyIncomeInOr = gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.HAS_SPECIAL_COMPANY_INCOME);
        needsNumberOfSharesColumn = gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.HAS_GROWING_NUMBER_OF_SHARES);

        hasOSI = is1817;
        if (hasOSI) {
            try {
                Object osiPort = null;

                // Attempt 1: Standard Rails portfolio lookup
                try {
                    java.lang.reflect.Method getPort = bank.getClass().getMethod("getPortfolio", String.class);
                    osiPort = getPort.invoke(bank, "OSI");
                    if (osiPort == null)
                        osiPort = getPort.invoke(bank, "Open Short Interest");
                } catch (Exception e1) {
                    // Ignore and try fallback
                }

                // Attempt 2: Explicit getOSI() getter
                if (osiPort == null) {
                    try {
                        java.lang.reflect.Method m = bank.getClass().getMethod("getOSI");
                        osiPort = m.invoke(bank);
                    } catch (Exception e2) {
                        // Ignore
                    }
                }

                if (osiPort != null) {
                    java.lang.reflect.Method m2 = osiPort.getClass().getMethod("getPortfolioModel");
                    osi = (PortfolioModel) m2.invoke(osiPort);
                } else {
                    log.warn("OSI Portfolio not found. Rendering empty column for layout testing.");
                }
            } catch (Exception e) {
                log.warn("Error initializing OSI portfolio: " + e.getMessage());
            }
        }

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
        compRevenue = new Field[nc]; // This becomes "Dividend"
        compRetained = new Field[nc]; // New "Retained" column

        compTrains = new Field[nc];
        compTrainsButtonPanel = new JPanel[nc];
        compSubTrainButtons = new RailCard[nc][MAX_TRAIN_SLOTS];
        compTokens = new JPanel[nc];

        if (hasRights)
            rights = new Field[nc];
        compRetained = new Field[nc];

        playerCash = new Field[np];
        playerCashButton = new ClickField[np];
        playerWorth = new Field[np];
        playerORWorthIncrease = new Field[np];

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

        // New Sequence: Treasury -> Trains -> Dividend -> Retained -> Markers
        compCashXOffset = ++lastX;
        compTrainsXOffset = ++lastX;

        compRevenueXOffset = ++lastX;
        compRetainedXOffset = ++lastX;
        compTokensXOffset = ++lastX;

        // Ensure all Y offsets point to the same row
        compCashYOffset = compTrainsYOffset = compRevenueYOffset = compRetainedYOffset = compTokensYOffset = lastY;

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

        // Implement Linear Operating Round Tracker (The Reclamation)
        // Spans columns: Pool -> Tokens
        // Spans rows: Fixed Inc -> Time (Assuming Fixed Inc is immediately above Time)

        newTrainsXOffset = certInIPOXOffset;
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
        playerCertCount = new CertLimitGauge[np];
        // Initialize time tracking array and pull current time values
        lastPlayerTimes = new int[np];
        for (int i = 0; i < np; i++) {
            // Pull the current, potentially already-bonused time from the model
            lastPlayerTimes[i] = players.getPlayerByPosition(i).getTimeBankModel().value();
        }

        // Initialize the visualization panel
        roundCounterPanel = new RoundCounterPanel(gameUIManager);

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

        f = new Caption(" ");
        f.setBackground(Color.WHITE);
        addField(f, futureTrainsXOffset, y, futureTrainsWidth, 1, 0, true);

        f = new Caption("  -bonds");
        f.setForeground(c.getFgColour());
        f.setBackground(c.getBgColour());
        addField(f, rightCompCaptionXOffset, y, 1, 1, WIDE_LEFT, visible);
    }

    public void recreate() {

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

        if (gameUIManager != null && gameUIManager.getGameManager() != null
                && gameUIManager.getGameManager().getCurrentPlayer() != null) {
            currentActor = gameUIManager.getGameManager().getCurrentPlayer().getIndex();
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

        lastPlayerTimes[playerIndex] = newTime; // Update stored time

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
        updatePlayerTime(playerIndex, newTime);
    }

    private boolean allSameBaseAction(List<PossibleAction> actions) {
        if (actions == null || actions.isEmpty())
            return true;
        Class<?> first = actions.get(0).getClass();
        for (PossibleAction a : actions) {
            if (!a.getClass().equals(first)) {
                if (actions.get(0) instanceof BuyCertificate && a instanceof BuyCertificate) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public void actionPerformed(ActionEvent actor) {
        JComponent source = (JComponent) actor.getSource();
        List<PossibleAction> actions;
        PossibleAction chosenAction = null;
        StockRound.manualSwapChoice = null;

        if (source instanceof ClickField || source instanceof RailCard) {
            gbc = gb.getConstraints(source);

            if (source instanceof ClickField) {
                actions = ((ClickField) source).getPossibleActions();
            } else {
                actions = ((RailCard) source).getPossibleActions();
            }

            // SAFETY CHECK: Actions might be null if the button was initialized but no
            // action added
            if (actions == null || actions.isEmpty()) {
                return;
            }

            SoundManager.notifyOfClickFieldSelection(actions.isEmpty() ? null : actions.get(0));

            if (actions.size() == 0) {
                log.warn("No ClickField action found");
            } else if (actions.size() > 1 && !allSameBaseAction(actions)) {
                // Generic disambiguation for heterogeneous actions on a single UI element
                List<String> options = new ArrayList<>();
                for (PossibleAction pa : actions) {

                    String label = pa.toString();
                    if (pa instanceof BuyCertificate) {
                        BuyCertificate bc = (BuyCertificate) pa;
                        label = bc.getPlayerName() + " buys 1 share of " + bc.getCompany().getId();
                    } else if (pa.getClass().getName().endsWith("CompanyBuyOpenMarketShare_1817")) {
                        try {
                            String compId = (String) pa.getClass().getMethod("getCompanyId").invoke(pa);
                            label = compId + " buys share from Open Market";
                        } catch (Exception e) {
                            label = "Company buys share from Open Market";
                        }
                    }
                    options.add(label);

                }

                String sp = (String) JOptionPane.showInputDialog(this,
                        LocalText.getText("PleaseSelect"),
                        "Multiple Actions Available",
                        JOptionPane.QUESTION_MESSAGE,
                        null, options.toArray(new String[0]),
                        options.get(0));

                int index = options.indexOf(sp);
                if (index >= 0) {
                    chosenAction = actions.get(index);
                    // Ensure the number bought is set safely if a standard BuyCertificate is
                    // selected through this shortcut
                    if (chosenAction instanceof BuyCertificate && !(chosenAction instanceof StartCompany)) {
                        ((BuyCertificate) chosenAction).setNumberBought(1);
                    }
                } else {
                    return; // user canceled
                }
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
                    // 1817: If the buyer is a company, resolve the company ID instead of player
                    // name
                    if (buy.getClass().getName().endsWith("CompanyBuyOpenMarketShare_1817")) {
                        try {
                            playerName = (String) buy.getClass().getMethod("getCompanyId").invoke(buy);
                        } catch (Exception e) {
                            playerName = "Company";
                        }
                    }

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
                        options.add(playerName + ": " + LocalText.getText(key,
                                sharePerCert,
                                companyName,
                                buy.getFromPortfolio().getParent().getId(),
                                gameUIManager.format(sharesPerCert * buy.getPrice())));

                        buyActions.add(buy);
                        buyAmounts.add(1);
                        for (int i = 2; i <= buy.getMaximumNumber(); i++) {
                            options.add(playerName + ": " + LocalText.getText("BuyCertificates",
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
                if (options.size() > 1 || actions.get(0).getClass().getSimpleName().equals("StartCompany_18EU")) {
                    if (startCompany) {
                        StartCompany scAction = (StartCompany) actions.get(0);
                        int currentCash = gameUIManager.getCurrentPlayer().getCashValue();

                        // Dynamically fetch the actual float percentage for this specific game/company
                        int floatPct = 60; // Fallback
                        if (scAction.getCompany() != null) {
                            floatPct = scAction.getCompany().getFloatPercentage();
                        }

                        scAction.setUiContext(currentCash, floatPct);

                        net.sf.rails.ui.swing.elements.StartPriceGridDialog dialog = new net.sf.rails.ui.swing.elements.StartPriceGridDialog(
                                GameUIManager.COMPANY_START_PRICE_DIALOG,
                                gameUIManager,
                                parent,
                                LocalText.getText("PleaseSelect"),
                                LocalText.getText("WHICH_START_PRICE",
                                        playerName,
                                        companyName),
                                options.toArray(new String[0]), -1, scAction);

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
                    // // "Un-set" the share size for Pool buys to force StockRound to detect
                    // ambiguity.
                    // // If we leave the default (e.g. 10), StockRound assumes it's a deliberate
                    // choice (Replay) and skips the dialog.
                    // // By setting it to 0, validSizes.contains(0) fails, triggering the
                    // StockRound dialog.
                    // if (((BuyCertificate) chosenAction).getFromPortfolio() == pool) {
                    // ((BuyCertificate) chosenAction).setSharePerCertificate(0);
                    // }
                }
            } else if (actions.get(0) instanceof CashCorrectionAction) {
                // Delegate to GameUIManagers
                chosenAction = actions.get(0);
            } else if (actions.get(0) instanceof BuyPrivate) {
                // Delegate the UI interaction to the ORUIManager to avoid code duplication
                // and ensure consistent modal parenting (ORWindow vs StatusWindow).
                gameUIManager.getORUIManager().processBuyPrivate((BuyPrivate) actions.get(0));
                return; // Interaction handled by ORUIManager, so we return immediately.
            } else if (actions.get(0) instanceof TrainCorrectionAction) {
                chosenAction = actions.get(0);
            } else if (actions.get(0) instanceof BuyTrain) {
                gameUIManager.getORUIManager().processBuyTrain((BuyTrain) actions.get(0));
                return;
            } else if (actions.get(0).getClass().getName().endsWith("Initiate1817IPO")) {
                chosenAction = handle1817IPO(actions.get(0));

            } else if (actions.get(0).getClass().getSimpleName().equals("StartPrussian")) {
                // Explicitly handle StartPrussian to ensure it triggers
                chosenAction = actions.get(0);
                // } else if
                // (actions.get(0).getClass().getSimpleName().equals("ExchangeMinorAction")) {
                // // Explicitly handle ExchangeCoalAction (1837)
                // chosenAction = actions.get(0);

                // Replaces the hardcoded "ExchangeCoalAction" check.
                // Any action implementing GuiTargetedAction will now work automatically.
            } else if (actions.get(0) instanceof GuiTargetedAction) {
                chosenAction = actions.get(0);

            } else if (actions.get(0) instanceof LayTile) {
                // Prevent incomplete LayTile actions (from Private Cards) from being sent to
                // GameManager
                // This prevents the "null tile" crash and prepares for future "Jump to Map"
                // logic.
                System.out.println("Blocked incomplete LayTile action from RailCard: " + actions.get(0));
                chosenAction = null;
            } else {
                chosenAction = processGameSpecificActions(actor, actions.get(0));
            }

        }

        chosenAction = processGameSpecificFollowUpActions(actor, chosenAction);

        if (chosenAction != null) {

            (parent).process(chosenAction);
        }

        repaint();
    }

    /** Stubs for game-specific subclasses like GameStatus_1817 */
    protected PossibleAction handle1817IPO(PossibleAction action) {
        return null;
    }

    protected void setCompanyTrainButton(int i, boolean clickable, PossibleAction action) {
        // 1. Safety Checks
        if (shareRowVisibilityObservers == null || i < 0 || i >= shareRowVisibilityObservers.length
                || shareRowVisibilityObservers[i] == null) {
            return;
        }

        if (compTrains[i] != null) {
            compTrains[i].setVisible(false);
        }

        if (compTrainsButtonPanel == null || compTrainsButtonPanel.length <= i || compTrainsButtonPanel[i] == null)
            return;

        boolean visible = (shareRowVisibilityObservers[i] != null) && shareRowVisibilityObservers[i].lastValue();

        compTrainsButtonPanel[i].setVisible(visible);

        PublicCompany c = companies[i];

        // --- Robust Train List Retrieval ---
        java.util.List<net.sf.rails.game.Train> trainList = new java.util.ArrayList<>();
        if (c.getPortfolioModel() != null && c.getPortfolioModel().getTrainList() != null) {
            trainList.addAll(c.getPortfolioModel().getTrainList());
        }

        // Allow display if (Not Floated AND Has Trains) - Critical for Prussia
        if (!c.hasFloated() && trainList.isEmpty()) {
            if (compSubTrainButtons[i] != null) {
                for (RailCard cf : compSubTrainButtons[i]) {
                    if (cf != null)
                        cf.setVisible(false);
                }
            }
            return;
        }

        java.util.List<BuyTrain> buyActions = clickable ? possibleActions.getType(BuyTrain.class) : null;
        int limit = c.getCurrentTrainLimit();

        // 3. Render Owned Trains using RailCard
        for (int t = 0; t < MAX_TRAIN_SLOTS; t++) {
            RailCard cf = compSubTrainButtons[i][t];
            if (cf == null)
                continue;

            cf.reset();

            if (t < trainList.size()) {
                // EXISTING TRAIN
                net.sf.rails.game.Train train = trainList.get(t);

                // Use RailCard logic to set content
                if (cf != null)
                    cf.setTrain(train);
                // FIX: Explicitly set Component Name as safety for ID matching
                cf.setName(train.getName());

                String cleanName = train.getName().replaceAll("_\\d+$", "");
                // This call previously deleted the train data in RailCard. With the fix, it is
                // safe.
                cf.setCustomLabel(getAbbreviatedTrainName(cleanName));

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

                if (canBuy) {
                    cf.setBackground(BG_CARD_PASSIVE);
                    cf.setBorder(BorderFactory.createLineBorder(BORDER_COL_BUY, 3));
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
                cf.setOpaque(true);
                cf.setVisible(true);

            } else if (t < limit) {
                // EMPTY SLOT (Passive)
                cf.setCustomLabel("");

                cf.setOpaque(false);
                cf.setBackground(new Color(0, 0, 0, 0));
                cf.setBorder(BORDER_DASHED);
                cf.setVisible(true);
            } else {
                // EXCEEDS LIMIT (Hidden)
                cf.setVisible(false);
            }
        }
    }

    private java.util.List<String> previousDashboardSignature = new java.util.ArrayList<>();

    private boolean isMarketOrAuctionRound(net.sf.rails.game.round.RoundFacade round) {
        if (round == null)
            return false;
        return round instanceof net.sf.rails.game.financial.StockRound
                || round instanceof net.sf.rails.game.specific._1817.AuctionRound_1817;
    }

    public void refreshDashboard() {
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
            javax.swing.SwingUtilities.invokeLater(this::refreshDashboard);
            return;
        }

        // Retry injection if it failed during startup (e.g. if timer text wasn't ready)
        if (roundCounterPanel != null && roundCounterPanel.getParent() == null) {
            hijackParentComponents();
        }

        if (gameUIManager == null || gameUIManager.getGameManager() == null)
            return;

        // Identify Operating Company for Signature
        PublicCompany operatingComp = null;
        net.sf.rails.game.round.RoundFacade currentRound = gameUIManager.getGameManager().getCurrentRound();

        String opCompId = null;

        if (currentRound instanceof net.sf.rails.game.OperatingRound) {
            PublicCompany pc = ((net.sf.rails.game.OperatingRound) currentRound).getOperatingCompany();
            if (pc != null)
                opCompId = pc.getId();
        } else if (currentRound instanceof net.sf.rails.game.specific._1817.MergerAndAcquisitionRound_1817) {
            PublicCompany pc = ((net.sf.rails.game.specific._1817.MergerAndAcquisitionRound_1817) currentRound)
                    .getOperatingCompany();
            if (pc != null)
                opCompId = pc.getId();
        }

        else if (gameUIManager.getGameManager().getPossibleActions() != null) {
            for (PossibleAction pa : gameUIManager.getGameManager().getPossibleActions().getList()) {
                if (pa instanceof GuiTargetedAction) {
                    net.sf.rails.game.state.Owner actor = ((GuiTargetedAction) pa).getActor();
                    if (actor instanceof PublicCompany) {
                        opCompId = actor.getId();
                        break;
                    }
                }
            }
        }

        java.util.List<PublicCompany> allCompanies = gameUIManager.getAllPublicCompanies();
        net.sf.rails.game.model.PortfolioModel ipoModel = gameUIManager.getRoot().getBank().getIpo()
                .getPortfolioModel();

        // 1. Capture Order
        final java.util.Map<PublicCompany, Integer> originalOrder = new java.util.HashMap<>();
        int orderIndex = 0;

        java.util.List<PublicCompany> displayList = new java.util.ArrayList<>();
        java.util.List<PublicCompany> inactiveList = new java.util.ArrayList<>();

        net.sf.rails.game.round.RoundFacade currentRoundForDisplay = gameUIManager.getGameManager().getCurrentRound();
        boolean isStockRound = isMarketOrAuctionRound(currentRoundForDisplay);

        for (PublicCompany c : allCompanies) {
            originalOrder.put(c, orderIndex++);
            if (c.isClosed())
                continue;
            boolean isActive = c.hasFloated() || (c.getPresidentsShare() != null
                    && c.getPresidentsShare().getOwner() instanceof net.sf.rails.game.Player);
            if (isStockRound || isActive) {
                displayList.add(c);
            } else {
                inactiveList.add(c);
            }
        }

        // 2. Sort
        compNameCaption = new Caption[nc];
        // Delegate sorting directly to the Engine's authoritative running order to
        // prevent MVC desync.
        final java.util.List<PublicCompany> engineOrder = gameUIManager.getGameManager().getCompaniesInRunningOrder();

        StringBuilder orderLog = new StringBuilder();
        if (engineOrder != null) {
            for (PublicCompany comp : engineOrder) {
                orderLog.append(comp.getId()).append(", ");
            }
        }

        java.util.List<PublicCompany> minorsList = new java.util.ArrayList<>();
        java.util.List<PublicCompany> majorsList = new java.util.ArrayList<>();

        for (PublicCompany c : displayList) {
            if (!c.hasStockPrice()) {
                minorsList.add(c);
            } else {
                majorsList.add(c);
            }
        }

        // Minors maintain static creation order
        java.util.Collections.sort(minorsList, (c1, c2) -> Integer.compare(c1.getPublicNumber(), c2.getPublicNumber()));

        // Majors strictly follow the engine's authoritative order
        java.util.Collections.sort(majorsList, (c1, c2) -> {
            int idx1 = engineOrder != null ? engineOrder.indexOf(c1) : -1;
            int idx2 = engineOrder != null ? engineOrder.indexOf(c2) : -1;

            if (idx1 >= 0 && idx2 >= 0)
                return Integer.compare(idx1, idx2);
            if (idx1 >= 0)
                return -1;
            if (idx2 >= 0)
                return 1;
            return Integer.compare(c1.getPublicNumber(), c2.getPublicNumber());
        });

        displayList.clear();
        displayList.addAll(minorsList);
        displayList.addAll(majorsList);

        // Append Padding (Inactive) Companies to the BOTTOM
        if (!isStockRound && displayList.size() < 10) {
            java.util.List<PublicCompany> inactivePool = new java.util.ArrayList<>();
            for (PublicCompany c : allCompanies) {
                if (c.isClosed() || displayList.contains(c))
                    continue;
                inactivePool.add(c);
            }
            int needed = 10 - displayList.size();
            for (int i = 0; i < Math.min(needed, inactivePool.size()); i++) {
                displayList.add(inactivePool.get(i));
            }
        }

        // 3. Build Signature
        java.util.List<String> currentSignature = new java.util.ArrayList<>();
        for (PublicCompany c : displayList) {
            boolean inIpo = ipoModel.getShare(c) > 0;

            int currentPrice = c.getCurrentSpace() != null ? c.getCurrentSpace().getPrice() : 0;
            if (currentPrice == 0 && c.getStartSpace() != null)
                currentPrice = c.getStartSpace().getPrice();
            // Active if Floated, Owned, or (In IPO and Buyable/Priced)
            boolean isActive = c.hasFloated() ||
                    (c.getPresidentsShare() != null && c.getPresidentsShare().getOwner() instanceof Player) ||
                    (inIpo && currentPrice > 0);

            boolean isOperating = (c == operatingComp);

            // This ensures that transitioning from OR (Operating=True) to SR
            // (Operating=False)
            // is detected as a state change, triggering the required UI rebuild.
            currentSignature.add(c.getId() + ":" + isActive + ":" + isOperating);
        }

        // 4. Compare and Recreate
        // Self-Healing: If component count is 0, the previous render failed/crashed.
        // Force recreation even if signature matches to recover from "Grey Screen".
        if (!currentSignature.equals(previousDashboardSignature) || this.getComponentCount() == 0) {
            previousDashboardSignature = currentSignature;
            recreate();
        }

        int currentActor = -1;
        if (gameUIManager != null && gameUIManager.getGameManager() != null
                && gameUIManager.getGameManager().getCurrentPlayer() != null) {
            currentActor = gameUIManager.getGameManager().getCurrentPlayer().getIndex();
        } else {
            currentActor = this.actorIndex;
        }

        // Always run the turn logic to update button states (Active/Passive) and attach
        // new actions
        initTurn(currentActor, true);
        repaint();
    }

    // Method 1: WITH 'Object o' (Handles Actions & Colors)
    protected void setPlayerCertButton(int i, int j, boolean clickable, Object o) {

        // Safety Check: If the observer for this company row is missing (e.g. during
        // re-init), abort.
        if (shareRowVisibilityObservers[i] == null) {
            return;
        }

        // Fix NPE: Check if the observer exists before accessing lastValue()
        boolean visible = true;

        if (shareRowVisibilityObservers == null || i < 0 || i >= shareRowVisibilityObservers.length) {
            return;
        }

        RowVisibility observer = shareRowVisibilityObservers[i];
        if (observer == null || j < 0) {
            return;
        }

        boolean panelVisible = observer.lastValue();

        // 1. Manage Dot Visibility (Environment)
        if (playerSoldDots != null && playerSoldDots.length > i && playerSoldDots[i] != null
                && playerSoldDots[i].length > j && playerSoldDots[i][j] != null) {
            boolean hasSold = false;
            Player player = players.getPlayerByPosition(j);
            if (player != null && companies[i] != null) {
                hasSold = player.hasSoldThisRound(companies[i]);
            }

            if (hasSold) {
                playerSoldDots[i][j].setVisible(true);
                playerSoldDots[i][j].setForeground(Color.RED);
            } else if (companies[i].hasStockPrice()) {
                playerSoldDots[i][j].setVisible(true);
                playerSoldDots[i][j].setForeground(new Color(0, 0, 0, 0)); // Transparent
            } else {
                playerSoldDots[i][j].setVisible(false);
            }
        }

        if (playerSharePanels != null && playerSharePanels[i][j] != null && panelVisible) {
            playerSharePanels[i][j].setVisible(true);
        }

        // A. MAJOR COMPANIES (Cards)
        if (playerShareCards != null && playerShareCards[i][j] != null) {
            if (!panelVisible) {
                playerShareCards[i][j].setVisible(false);
            }
            // Always set content tooltip
            playerShareCards[i][j].setShareStackTooltip(
                    players.getPlayerByPosition(j).getPortfolioModel().getCertificates(companies[i]));

            if (clickable && o != null) {
                // "Beige + Border" Style Implementation
                if (o instanceof BuyCertificate) {
                    playerShareCards[i][j].setBackground(BG_CARD_PASSIVE); // Keep Beige
                    playerShareCards[i][j].setBorder(BorderFactory.createLineBorder(BORDER_COL_BUY, BORDER_THICKNESS)); // Thick
                                                                                                                        // Green
                                                                                                                        // Border
                    playerShareCards[i][j].addPossibleAction((PossibleAction) o);
                    playerShareCards[i][j].setVisible(true);
                } else if (o instanceof SellShares) {
                    playerShareCards[i][j].setBackground(BG_CARD_PASSIVE); // Keep Beige
                    playerShareCards[i][j].setBorder(BorderFactory.createLineBorder(BORDER_COL_SELL, BORDER_THICKNESS)); // Thick
                                                                                                                         // Red
                                                                                                                         // Border
                    playerShareCards[i][j].addPossibleAction((PossibleAction) o);
                } else {
                    playerShareCards[i][j].setBackground(BG_BUY); // Green
                    playerShareCards[i][j].setBorder(BorderFactory.createLineBorder(Color.BLACK, 2)); // Thick Border

                    if (o instanceof PossibleAction) {
                        playerShareCards[i][j].addPossibleAction((PossibleAction) o);
                    }
                    playerShareCards[i][j].setVisible(true);
                    playerShareCards[i][j].setOpaque(true); // Ensure paint

                }

                playerShareCards[i][j].setEnabled(true);
            } else {
                // PASSIVE
                playerShareCards[i][j].clearPossibleActions();
                playerShareCards[i][j].setBackground(BG_CARD_PASSIVE);
                playerShareCards[i][j].setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color.BLACK, 1),
                        BorderFactory.createEmptyBorder(1, 1, 1, 1)));
                playerShareCards[i][j].setEnabled(true);
                playerShareCards[i][j].setVisible(false); // Hide empty cards by default
            }
            return;
        }

        // B. MINOR COMPANIES (Old Buttons) - Fallback
        // (Recursive call to Method 2 removed to prevent loops, logic inlined below)

        if (certPerPlayerButton[i][j] == null)
            return;

        if (clickable) {
            certPerPlayerButton[i][j].setText(certPerPlayer[i][j].getText());
            syncToolTipText(certPerPlayer[i][j], certPerPlayerButton[i][j]);
            certPerPlayerButton[i][j].setOpaque(true);
            certPerPlayerButton[i][j].setBackground(Color.WHITE);

            if (o instanceof BuyCertificate)
                certPerPlayerButton[i][j].setBackground(BG_BUY);
            else if (o instanceof SellShares)
                certPerPlayerButton[i][j].setBackground(BG_SELL);

            if (o instanceof PossibleAction)
                certPerPlayerButton[i][j].addPossibleAction((PossibleAction) o);
        } else {
            certPerPlayerButton[i][j].clearPossibleActions();
        }

        if (certPerPlayer[i][j] != null)
            certPerPlayer[i][j].setVisible(panelVisible && !clickable);
        certPerPlayerButton[i][j].setVisible(panelVisible && clickable);
    }

    // Method 2: WITHOUT 'Object o' (Base Setup)
    protected void setPlayerCertButton(int i, int j, boolean clickable) {

        // // Fix NPE: Check if the observer exists before accessing lastValue()
        // boolean visible = true;
        // if (shareRowVisibilityObservers != null && i <
        // shareRowVisibilityObservers.length && shareRowVisibilityObservers[i] != null)
        // {
        // visible = shareRowVisibilityObservers[i].lastValue();
        // }

        // if (!visible) return;

        // SAFETY CHECK
        if (shareRowVisibilityObservers == null
                || i < 0
                || i >= shareRowVisibilityObservers.length
                || shareRowVisibilityObservers[i] == null) {
            return;
        }
        if (j < 0)
            return;

        // 1. MAJOR COMPANIES (Redirect)
        if (playerShareCards != null && playerShareCards[i][j] != null) {
            setPlayerCertButton(i, j, clickable, null);
            return;
        }

        // 2. MINOR COMPANIES (Legacy)
        boolean visible = shareRowVisibilityObservers[i].lastValue();

        if (certPerPlayerButton[i][j] == null)
            return;

        if (clickable) {
            certPerPlayerButton[i][j].setText(certPerPlayer[i][j].getText());
            syncToolTipText(certPerPlayer[i][j], certPerPlayerButton[i][j]);
            certPerPlayerButton[i][j].setOpaque(true);
            certPerPlayerButton[i][j].setBackground(Color.WHITE);
            certPerPlayerButton[i][j].setBorder(certPerPlayer[i][j].getBorder());
        } else {
            certPerPlayerButton[i][j].clearPossibleActions();
        }

        if (certPerPlayer[i][j] != null)
            certPerPlayer[i][j].setVisible(visible && !clickable);
        certPerPlayerButton[i][j].setVisible(visible && clickable);
    }

    protected void setIPOCertButton(int i, boolean clickable, Object o) {
        // Capture locally to ensure atomic null-check and prevent race conditions/array
        // shifting
        // during reload or high-speed undo/redo.
        RowVisibility observer = null;
        if (shareRowVisibilityObservers != null && i >= 0 && i < shareRowVisibilityObservers.length) {
            observer = shareRowVisibilityObservers[i];
        }

        if (observer == null) {
            return; // Fail safe if the company row was not initialized (e.g. Closed)
        }

        // Redirect logic to the new ipoShareCards
        if (ipoShareCards == null || ipoShareCards[i] == null)
            return;

        // Enforce font consistency during updates
        if (stickyFont != null) {
            ipoShareCards[i].setFont(stickyFont);
        }
        // Set Tooltip with Stack Details
        ipoShareCards[i].setShareStackTooltip(ipo.getCertificates(companies[i]));

        // Base Visibility Check
        boolean visible = observer.lastValue();
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
            if (o instanceof BuyCertificate || o.getClass().getName().endsWith("Initiate1817IPO")) {
                ipoShareCards[i].setBackground(BG_CARD_PASSIVE);
                ipoShareCards[i].setBorder(BorderFactory.createLineBorder(BORDER_COL_BUY, BORDER_THICKNESS));

                if (o.getClass().getName().endsWith("Initiate1817IPO") && !hasContent) {
                    ipoShareCards[i].setCustomLabel("Start");
                }
            } else if (o instanceof SellShares) {
                ipoShareCards[i].setBackground(BG_CARD_PASSIVE);
                ipoShareCards[i].setBorder(BorderFactory.createLineBorder(BORDER_COL_SELL, BORDER_THICKNESS));
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

    // COMPLETELY REPLACE the setPoolCertButton methods:

    protected void setPoolCertButton(int i, boolean clickable, Object o) {
        RowVisibility observer = null;
        if (shareRowVisibilityObservers != null && i >= 0 && i < shareRowVisibilityObservers.length) {
            observer = shareRowVisibilityObservers[i];
        }

        if (observer == null)
            return;

        if (poolShareCards == null || poolShareCards[i] == null)
            return;
        // Enforce font consistency during updates
        if (stickyFont != null) {
            poolShareCards[i].setFont(stickyFont);
        }

        // Set Tooltip with Stack Details
        poolShareCards[i].setShareStackTooltip(pool.getCertificates(companies[i]));
        boolean visible = observer.lastValue(); // Use local var

        if (!visible) {
            poolShareCards[i].setVisible(false);
            return;
        }

        // Ensure text visibility persists
        String shareTxt = pool.getShareModel(companies[i]).toText();
        poolShareCards[i].setVisible(shareTxt != null && !shareTxt.isEmpty());

        if (clickable && o != null) {
            if (o instanceof BuyCertificate || o.getClass().getName().endsWith("CompanyBuyOpenMarketShare_1817")) {
                poolShareCards[i].setBackground(BG_CARD_PASSIVE);
                poolShareCards[i].setBorder(BorderFactory.createLineBorder(BORDER_COL_BUY, BORDER_THICKNESS));
            } else if (o instanceof SellShares) {
                poolShareCards[i].setBackground(BG_CARD_PASSIVE);
                poolShareCards[i].setBorder(BorderFactory.createLineBorder(BORDER_COL_SELL, BORDER_THICKNESS));
            }

            if (o instanceof PossibleAction)
                poolShareCards[i].addPossibleAction((PossibleAction) o);

            poolShareCards[i].setEnabled(true);

        } else {
            // PASSIVE
            poolShareCards[i].clearPossibleActions();
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

    protected void setTreasuryCertButton(int i, boolean clickable, Object o) {

        if (shareRowVisibilityObservers == null || i < 0 || i >= shareRowVisibilityObservers.length)
            return;

        RowVisibility observer = shareRowVisibilityObservers[i];
        if (observer == null)
            return;

        if (treasuryShareCards == null || treasuryShareCards[i] == null)
            return;

        if (stickyFont != null)
            treasuryShareCards[i].setFont(stickyFont);

        if (compCanHoldOwnShares && companies != null && i < companies.length && companies[i] != null) {
            treasuryShareCards[i].setShareStackTooltip(companies[i].getPortfolioModel().getCertificates(companies[i]));
        }

        boolean visible = observer.lastValue();

        if (!visible) {
            treasuryShareCards[i].setVisible(false);
            return;
        }

        // Text Content
        String shareTxt = companies[i].getPortfolioModel().getShareModel(companies[i]).toText();
        boolean hasContent = (shareTxt != null && !shareTxt.isEmpty() && !shareTxt.equals("0"));

        treasuryShareCards[i].setVisible(hasContent);
        treasuryShareCards[i].setCustomLabel(shareTxt);
        treasuryShareCards[i].clearPossibleActions();

        if (clickable && o != null) {
            if (o instanceof BuyCertificate) {
                treasuryShareCards[i].setBackground(BG_SPOTLIGHT_INACTIVE);
                treasuryShareCards[i].setBorder(BorderFactory.createLineBorder(BORDER_COL_BUY, BORDER_THICKNESS));
            } else if (o instanceof SellShares) {
                treasuryShareCards[i].setBackground(BG_SPOTLIGHT_INACTIVE);
                treasuryShareCards[i].setBorder(BorderFactory.createLineBorder(BORDER_COL_SELL, BORDER_THICKNESS));
            }

            if (o instanceof PossibleAction)
                treasuryShareCards[i].addPossibleAction((PossibleAction) o);

            treasuryShareCards[i].setEnabled(true);
            treasuryShareCards[i].setVisible(true); // Force visible if actionable
        } else {
            // PASSIVE
            treasuryShareCards[i].setBackground(BG_CARD_PASSIVE);
            treasuryShareCards[i].setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.BLACK, 1),
                    BorderFactory.createEmptyBorder(1, 1, 1, 1)));
            treasuryShareCards[i].setEnabled(true);
        }
    }

    /**
     * Overload to handle the "reset" call (clickable=false).
     * We redirect this to the main method with null action.
     */
    protected void setTreasuryCertButton(int i, boolean clickable) {
        setTreasuryCertButton(i, clickable, null);
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

        boolean is1817 = "1817".equals(gameUIManager.getGameManager().getGameName());
        for (int i = 0; i < np; i++) {
            if (playerFixedIncome[i] == null)
                continue;

            int total = 0;
            int privateCount = 0; // Count how many privates we found

            try {
                Player p = players.getPlayerByPosition(i);

                if (p.getPortfolioModel() != null) {

                    if (is1817) {
                        total += p.getCashValue();
                        for (net.sf.rails.game.PrivateCompany pc : p.getPortfolioModel().getPrivateCompanies()) {
                            privateCount++;
                            total += pc.getBasePrice();
                        }
                    } else {
                        for (net.sf.rails.game.PrivateCompany pc : p.getPortfolioModel().getPrivateCompanies()) {
                            privateCount++;
                            int r = (phase != null) ? pc.getRevenueByPhase(phase) : 0;
                            if (r == 0 && pc.getRevenue() != null && !pc.getRevenue().isEmpty()) {
                                r = pc.getRevenue().get(0);
                            }
                            total += r;
                        }
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

        // 1. Identify Active Privates (Scan for Special Actions OR Buy Actions)
        // Map ID -> Action so we can attach it to the card later
        java.util.Map<String, PossibleAction> activePrivateActions = new java.util.HashMap<>();

        // FIX: Access .getList() to check size/empty status
        if (possibleActions != null && possibleActions.getList() != null && !possibleActions.getList().isEmpty()) {

            for (PossibleAction pa : possibleActions.getList()) {
                net.sf.rails.game.PrivateCompany target = null;

                // Check various action types that might use a Special Property
                if (pa instanceof UseSpecialProperty) {
                    net.sf.rails.game.special.SpecialProperty sp = ((UseSpecialProperty) pa).getSpecialProperty();
                    if (sp.getOriginalCompany() instanceof net.sf.rails.game.PrivateCompany) {
                        target = (net.sf.rails.game.PrivateCompany) sp.getOriginalCompany();
                    }
                } else if (pa instanceof LayTile) {
                    net.sf.rails.game.special.SpecialProperty sp = ((LayTile) pa).getSpecialProperty();
                    if (sp != null && sp.getOriginalCompany() instanceof net.sf.rails.game.PrivateCompany) {
                        target = (net.sf.rails.game.PrivateCompany) sp.getOriginalCompany();
                    }
                } else if (pa instanceof LayBaseToken) {
                    net.sf.rails.game.special.SpecialProperty sp = ((LayBaseToken) pa).getSpecialProperty();
                    if (sp != null && sp.getOriginalCompany() instanceof net.sf.rails.game.PrivateCompany) {
                        target = (net.sf.rails.game.PrivateCompany) sp.getOriginalCompany();
                    }
                } else if (pa instanceof BuyPrivate) {
                    // NEW: Explicitly catch BuyPrivate actions
                    target = ((BuyPrivate) pa).getPrivateCompany();
                } else if (pa instanceof GuiTargetedAction && !(pa instanceof DiscardTrain)) {
                    try {
                        // Catch generic Targeted Actions (e.g. Exchange) targeting a Private
                        Object t = ((GuiTargetedAction) pa).getTarget();
                        if (t instanceof net.sf.rails.game.PrivateCompany) {
                            target = (net.sf.rails.game.PrivateCompany) t;
                        }
                    } catch (Exception e) {
                        // Fail silently so we don't break the rest of the UI (e.g. Discard Trains)
                    }
                }

                // If found, link back to the Private Company ID
                if (target != null) {
                    activePrivateActions.put(target.getId(), pa);
                }
            }
        }

        for (int i = 0; i < np; i++) {
            if (playerPrivatesPanel[i] == null)
                continue;

            playerPrivatesPanel[i].removeAll();
            // Add top spacing so cards don't look "stuck" to the top border
            playerPrivatesPanel[i].add(Box.createVerticalStrut(5));

            net.sf.rails.game.Player p = players.getPlayerByPosition(i);
            if (p == null)
                continue;

            java.util.Collection<net.sf.rails.game.PrivateCompany> privates = p.getPortfolioModel()
                    .getPrivateCompanies();

            // Create a styled RailCard for each private
            for (net.sf.rails.game.PrivateCompany pc : privates) {

                // Pass the existing buySellGroup
                RailCard card = new RailCard(pc, buySellGroup);
                card.setCompany(pc); // Tell the card it is Private Company 'pc'

                // Apply Font: Use stickyFont (Zoom) if present, otherwise enforce the Standard
                // Font.
                // This ensures Privates match the height/style of the Share cards (SansSerif,
                // Bold, 12).
                if (stickyFont != null) {
                    card.setFont(stickyFont);
                } else {
                    card.setFont(new Font("SansSerif", Font.BOLD, 12));
                }

                card.addActionListener(this);
                card.setCompactMode(true);

                // 2. Consistent Styling
                // Use setCustomLabel to ensure clean text rendering
                card.setCustomLabel(pc.getId());
                card.setToolTipText(pc.getLongName());

                // 1. Apply the rich tooltip (now using getInfoText)
                card.setPrivateCompanyTooltip(pc);

                // Restore Hex Highlighting (Map lights up when hovering card)
                // The boolean 'false' respects the global "highlightHexes" config option.
                HexHighlightMouseListener.addMouseListener(card, gameUIManager.getORUIManager(), pc, false);

                // 4. State & Background
                // Check against ID set
                // 4. State & Background
                // Check against ID map
                if (activePrivateActions.containsKey(pc.getId())) {
                    PossibleAction action = activePrivateActions.get(pc.getId());
                    // Attach the action to the card so clicking it works!
                    card.addPossibleAction(action);
                    // The card will check its internal logic and paint itself Cyan
                    // (COL_HIGHLIGHT_BG).
                    card.setState(RailCard.State.HIGHLIGHTED);

                    // Force the standard "Special Action" visual style (Cyan + Blue Border)
                    card.setBackground(java.awt.Color.CYAN);
                    card.setBorder(BorderFactory.createLineBorder(Color.BLUE, 3));

                    if (action instanceof BuyPrivate) {
                        card.setToolTipText("Click to Buy " + pc.getId());
                    }
                } else {
                    card.setBackground(BG_CARD_PASSIVE); // Standard Beige
                    card.setState(RailCard.State.PASSIVE);
                    card.clearPossibleActions(); // Ensure no stale actions
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

            // Ensure we only repaint if the panel is still valid
            if (playerPrivatesPanel[i] != null) {
                playerPrivatesPanel[i].revalidate();
                playerPrivatesPanel[i].repaint();
            }

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

        // Calculate dynamic size based on the panel's current font (Ascent approximates
        // Cap Height)
        Font f = getFont();
        if (f == null)
            f = new Font("SansSerif", Font.PLAIN, 12);
        FontMetrics fm = getFontMetrics(f);

        // Size = Height of a capital letter + small padding.
        // This ensures the marker fits within the text line height ("dynamic text
        // height").
        int iconSize = Math.max(10, fm.getAscent() + 2);

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

        // Switch to GridBagLayout for true vertical centering
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        // Add small horizontal gap between tokens
        gbc.insets = new Insets(0, 1, 0, 1);

        for (int k = 0; k < displayCount; k++) {
            // Create a smart label that listens for Font changes (e.g. Zoom)
            // and regenerates the icon with the correct size on the fly.
            JLabel iconLabel = new JLabel() {
                @Override
                public void setFont(Font f) {
                    super.setFont(f);
                    // Recalculate icon size whenever the font updates
                    if (f != null) {
                        FontMetrics fm = getFontMetrics(f);
                        // Ascent approximates the height of a capital letter. +2 for padding.
                        int iconSize = Math.max(10, fm.getAscent() + 2);
                        setIcon(new SmallTokenIcon(company, company.getId(), iconSize));
                    }
                }
            };

            // Force initial setup using the panel's current font
            iconLabel.setFont(panel.getFont());

            String tooltip = "<html><b>" + company.getId() + "</b> Token Available</html>";
            iconLabel.setToolTipText(tooltip);

            panel.add(iconLabel, gbc);
            gbc.gridx++; // Move to next slot
        }
        panel.revalidate();
        panel.repaint();
    }

    private void updateCompanyPrivates(int i, PublicCompany c) {
        if (compPrivatesPanel == null || compPrivatesPanel[i] == null)
            return;

        compPrivatesPanel[i].removeAll();

        java.util.Collection<net.sf.rails.game.PrivateCompany> privates = c.getPortfolioModel().getPrivateCompanies();

        if (privates != null && !privates.isEmpty()) {
            for (net.sf.rails.game.PrivateCompany pc : privates) {
                RailCard card = new RailCard(pc, buySellGroup);
                card.setCompany(pc);

                // Consistency: Use sticky font (Zoom) if present
                if (stickyFont != null) {
                    card.setFont(stickyFont);
                } else {
                    card.setFont(new Font("SansSerif", Font.BOLD, 12));
                }

                card.setCompactMode(true);
                card.setCustomLabel(pc.getId());

                // Tooltip
                card.setPrivateCompanyTooltip(pc);

                // Highlight on Map
                HexHighlightMouseListener.addMouseListener(card, gameUIManager.getORUIManager(), pc, false);

                // Styling: Companies usually just "hold" them; they aren't actionable buttons
                // in the same way Player privates are (players click to buy/act).
                // However, we apply the passive card style for consistency.
                card.setBackground(BG_CARD_PASSIVE);
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color.BLACK, 1),
                        BorderFactory.createEmptyBorder(1, 1, 1, 1)));

                card.setOpaque(true);
                card.setVisible(true);

                compPrivatesPanel[i].add(card);
            }
        }

        compPrivatesPanel[i].revalidate();
        compPrivatesPanel[i].repaint();
    }

    private void updateTrainCosts() {
        try {
            net.sf.rails.game.TrainManager tm = gameUIManager.getRoot().getTrainManager();
            if (tm == null)
                return;

            int currentIndex = tm.getNewTypeIndex().value();
            java.util.List<net.sf.rails.game.TrainCardType> types = tm.getTrainCardTypes();
            java.util.List<net.sf.rails.game.Train> ipoInventory = new java.util.ArrayList<>(ipo.getTrainList());

            // 1. COLLECT & SORT IPO TRAINS
            // We first identify all unique types present in the IPO
            java.util.List<net.sf.rails.game.TrainCardType> presentTypes = new java.util.ArrayList<>();

            for (net.sf.rails.game.TrainCardType tct : types) {
                String tctName = tct.getId().replaceAll("_\\d+$", "");
                for (net.sf.rails.game.Train t : ipoInventory) {
                    String tName = t.getName().replaceAll("_\\d+$", "");
                    if (tName.equals(tctName)) {
                        presentTypes.add(tct);
                        break; // Found this type, move to next type
                    }
                }
            }

            // FIX: Sort by Cost (Ascending) to ensure 2G (Cheaper) is before 4 (Expensive)
            java.util.Collections.sort(presentTypes, (t1, t2) -> {
                int c1 = (!t1.getPotentialTrainTypes().isEmpty()) ? t1.getPotentialTrainTypes().get(0).getCost() : 0;
                int c2 = (!t2.getPotentialTrainTypes().isEmpty()) ? t2.getPotentialTrainTypes().get(0).getCost() : 0;
                return Integer.compare(c1, c2);
            });

            // 2. RENDER IPO BUTTONS
            int ipoSlot = 0;
            for (net.sf.rails.game.TrainCardType tct : presentTypes) {
                if (ipoSlot >= MAX_IPO_SLOTS)
                    break;

                String tctName = tct.getId().replaceAll("_\\d+$", "");
                int count = 0;
                int cost = 0;

                net.sf.rails.game.Train repTrain = null;

                for (net.sf.rails.game.Train t : ipoInventory) {
                    if (t.getName().replaceAll("_\\d+$", "").equals(tctName)) {
                        count++;
                        cost = t.getCost();
                        if (repTrain == null) {
                            repTrain = t;
                        }
                    }
                }

                RailCard btn = newTrainButtons[ipoSlot];
                javax.swing.JLabel lbl = newTrainQtyLabels[ipoSlot];

                if (cost == 0 && !tct.getPotentialTrainTypes().isEmpty()) {
                    cost = tct.getPotentialTrainTypes().get(0).getCost();
                }

                btn.reset();
                if (repTrain != null) {
                    btn.setTrain(repTrain);
                }
                btn.setCustomLabel(getAbbreviatedTrainName(tctName));
                btn.setName(tctName); // Store normalized name

                // Default Passive Style
                btn.setBackground(BG_CARD_PASSIVE);
                btn.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color.BLACK, 1),
                        BorderFactory.createEmptyBorder(1, 1, 1, 1)));
                btn.setVisible(true);

                if (lbl != null) {
                    String qtyStr = tct.hasInfiniteQuantity() ? "\u221E" : "(" + count + ")";
                    lbl.setText("<html><center>" + qtyStr + "<br>" +
                            "<font color='#000080'><b>" + gameUIManager.format(cost) + "</b></font>" +
                            "</center></html>");
                    lbl.setVisible(true);
                }
                ipoSlot++;
            }

            // Clear remaining IPO slots
            for (int i = ipoSlot; i < MAX_IPO_SLOTS; i++) {
                if (newTrainButtons[i] != null) {
                    newTrainButtons[i].setVisible(false);
                    newTrainButtons[i].setName(null);
                }
                if (newTrainQtyLabels[i] != null)
                    newTrainQtyLabels[i].setVisible(false);
            }

            // 3. FUTURE TRAINS (Dynamic Category Lookahead)
            java.util.List<net.sf.rails.game.TrainCardType> allFuture = new java.util.ArrayList<>();
            java.util.Map<String, java.util.List<net.sf.rails.game.TrainCardType>> futureByCategory = new java.util.HashMap<>();

            for (net.sf.rails.game.TrainCardType tct : types) {
                if (tct.isAvailable())
                    continue; // Skip active trains

                String tctName = tct.getId().replaceAll("_\\d+$", "");

                // Skip if already shown in IPO
                boolean alreadyShown = false;
                for (int k = 0; k < ipoSlot; k++) {
                    if (newTrainButtons[k].getName() != null && newTrainButtons[k].getName().equals(tctName)) {
                        alreadyShown = true;
                        break;
                    }
                }
                if (alreadyShown)
                    continue;

                // Group by Category
                String category = "default";
                if (!tct.getPotentialTrainTypes().isEmpty()) {
                    String cat = tct.getPotentialTrainTypes().get(0).getCategory();
                    if (cat != null && !cat.isEmpty()) {
                        category = cat;
                    }
                }

                allFuture.add(tct);
                futureByCategory.computeIfAbsent(category, k -> new java.util.ArrayList<>()).add(tct);
            }

            // Select trains to display
            java.util.List<net.sf.rails.game.TrainCardType> displayFuture = new java.util.ArrayList<>();

            // A. Guarantee the cheapest from each category
            for (java.util.List<net.sf.rails.game.TrainCardType> catList : futureByCategory.values()) {
                catList.sort((t1, t2) -> {
                    int c1 = (!t1.getPotentialTrainTypes().isEmpty()) ? t1.getPotentialTrainTypes().get(0).getCost()
                            : 0;
                    int c2 = (!t2.getPotentialTrainTypes().isEmpty()) ? t2.getPotentialTrainTypes().get(0).getCost()
                            : 0;
                    return Integer.compare(c1, c2);
                });
                if (!catList.isEmpty()) {
                    net.sf.rails.game.TrainCardType guaranteed = catList.get(0);
                    displayFuture.add(guaranteed);
                    allFuture.remove(guaranteed);
                }
            }

            // B. Fill remaining slots with the absolute cheapest remaining
            allFuture.sort((t1, t2) -> {
                int c1 = (!t1.getPotentialTrainTypes().isEmpty()) ? t1.getPotentialTrainTypes().get(0).getCost() : 0;
                int c2 = (!t2.getPotentialTrainTypes().isEmpty()) ? t2.getPotentialTrainTypes().get(0).getCost() : 0;
                return Integer.compare(c1, c2);
            });

            while (displayFuture.size() < MAX_FUTURE_SLOTS && !allFuture.isEmpty()) {
                displayFuture.add(allFuture.remove(0));
            }

            // C. Final sort for the UI (Cheapest first)
            displayFuture.sort((t1, t2) -> {
                int c1 = (!t1.getPotentialTrainTypes().isEmpty()) ? t1.getPotentialTrainTypes().get(0).getCost() : 0;
                int c2 = (!t2.getPotentialTrainTypes().isEmpty()) ? t2.getPotentialTrainTypes().get(0).getCost() : 0;
                return Integer.compare(c1, c2);
            });

            // Render Future Trains
            int futSlot = 0;
            for (net.sf.rails.game.TrainCardType tct : displayFuture) {
                if (futSlot >= MAX_FUTURE_SLOTS)
                    break;

                String tctName = tct.getId().replaceAll("_\\d+$", "");
                RailCard btn = futureTrainButtons[futSlot];
                javax.swing.JLabel lbl = futureTrainInfoLabels[futSlot];
                futSlot++;

                int cost = 0;
                if (!tct.getPotentialTrainTypes().isEmpty())
                    cost = tct.getPotentialTrainTypes().get(0).getCost();

                if (btn != null) {
                    btn.reset();
                    btn.setCustomLabel(getAbbreviatedTrainName(tctName));
                    btn.setName(tctName);
                    btn.setBackground(BG_CARD_PASSIVE);
                    btn.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(Color.BLACK, 1),
                            BorderFactory.createEmptyBorder(1, 1, 1, 1)));
                    btn.setVisible(true);
                }
                if (lbl != null) {
                    String qtyStr = tct.hasInfiniteQuantity() ? "\u221E" : "(" + tct.getQuantity() + ")";
                    lbl.setText("<html><center>" + qtyStr + "<br>" +
                            "<font color='#000080'><b>" + (cost > 0 ? gameUIManager.format(cost) : "") + "</b></font>" +
                            "</center></html>");
                    lbl.setVisible(true);
                }
            }

            // Clear unused future slots
            for (; futSlot < MAX_FUTURE_SLOTS; futSlot++) {
                if (futureTrainButtons[futSlot] != null)
                    futureTrainButtons[futSlot].setVisible(false);
                if (futureTrainInfoLabels[futSlot] != null)
                    futureTrainInfoLabels[futSlot].setVisible(false);
            }

        } catch (Exception e) {
            log.error("Error updating train costs", e);
        }
    }

    private void updateBondsHeatbar() {
        if (bondsHeatbarPanel == null)
            return;

        int totalLoans = 0;

        net.sf.rails.game.GameManager gm = gameUIManager.getGameManager();
        boolean isOR = gm.getCurrentRound() instanceof net.sf.rails.game.OperatingRound;
        String displayText = "Interest: $5";

        if (gm instanceof net.sf.rails.game.specific._1817.GameManager_1817) {
            BondsModel bm = ((net.sf.rails.game.specific._1817.GameManager_1817) gm).getBondsModel();
            if (bm != null) {
                totalLoans = bm.getTotalLoansTaken();
                if (bm instanceof net.sf.rails.game.specific._1817.BondsModel_1817) {
                    net.sf.rails.game.specific._1817.BondsModel_1817 bm1817 = (net.sf.rails.game.specific._1817.BondsModel_1817) bm;
                    if (isOR) {
                        displayText = "Interest: $" + bm1817.getInterestRate();
                    } else {
                        displayText = "Next Interst: $" + bm1817.calculateProjectedInterestRate();
                    }
                }
            }
        }
        bondsHeatbarPanel.setTotalLoansTaken(totalLoans);

        if (interestRateField != null) {
            interestRateField.setText(displayText);
        }

    }

    public void initTurn(int actorIndex, boolean myTurn) {

        // We use the existing gameUIManager field directly.
        // We removed the 'getRoot()' fallback that was causing compilation errors.
        if (gameUIManager != null && gameUIManager.getGameManager() != null) {
            this.possibleActions = gameUIManager.getGameManager().getPossibleActions();
        }

        if (!myTurn) {
            actorIndex = -1;
        }

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

        else if (gameUIManager.getGameManager()
                .getCurrentRound() instanceof net.sf.rails.game.specific._1817.MergerAndAcquisitionRound_1817) {
            PublicCompany pc = ((net.sf.rails.game.specific._1817.MergerAndAcquisitionRound_1817) gameUIManager
                    .getGameManager().getCurrentRound()).getOperatingCompany();
            if (pc != null)
                opCompId = pc.getId();
        }

        // GENERIC HIGHLIGHT LOGIC:
        // If we are not in a standard Operating Round, check the available actions.
        // If any action targets a specific Company (e.g. Formation, Discard, Merger),
        // we highlight that company as if it were "Operating".
        else if (possibleActions != null && !possibleActions.isEmpty()) {
            for (PossibleAction pa : possibleActions.getList()) {
                if (pa instanceof GuiTargetedAction) {
                    net.sf.rails.game.state.Owner actor = ((GuiTargetedAction) pa).getActor();
                    if (actor instanceof PublicCompany) {
                        opCompId = actor.getId();
                        break; // Highlight the first active actor found
                    }
                }
            }
        }
        // Preserve the correct actorIndex passed from StatusWindow during Sales and
        // Auctions.
        // Only override with the company president in standard operating rounds to
        // clear lingering players.
        boolean preserveActor = false;
        net.sf.rails.game.round.RoundFacade currentRoundForHighlight = gameUIManager.getGameManager().getCurrentRound();
        if (currentRoundForHighlight instanceof net.sf.rails.game.specific._1817.AuctionRound_1817) {
            preserveActor = true;
        } else if (currentRoundForHighlight instanceof net.sf.rails.game.specific._1817.MergerAndAcquisitionRound_1817) {
            net.sf.rails.game.specific._1817.MergerAndAcquisitionRound_1817 mar = (net.sf.rails.game.specific._1817.MergerAndAcquisitionRound_1817) currentRoundForHighlight;
            net.sf.rails.game.specific._1817.MergerAndAcquisitionRound_1817.MaAStep step = mar.getCurrentStep();
            if (step == net.sf.rails.game.specific._1817.MergerAndAcquisitionRound_1817.MaAStep.SALES_AUCTION ||
                    step == net.sf.rails.game.specific._1817.MergerAndAcquisitionRound_1817.MaAStep.SALES_SELECT_BUYER
                    ||
                    step == net.sf.rails.game.specific._1817.MergerAndAcquisitionRound_1817.MaAStep.SALES_FRIENDLY) {
                preserveActor = true;
            }
        }

        net.sf.rails.game.round.RoundFacade currentRound = gameUIManager.getGameManager().getCurrentRound();
        boolean isStockRound = isMarketOrAuctionRound(currentRound);

        // 1. ITERATE COMPANIES (Rows)
        for (cIdx = 0; cIdx < nc; cIdx++) {
            PublicCompany c = companies[cIdx];
            int i = c.getPublicNumber(); // CORRECT INDEX for arrays

            // CRITICAL: Skip closed companies. They have no UI rows (skipped in
            // initCompanyRows),
            // so accessing arrays like compNameCaption[i] will throw NullPointerException.
            if (c.isClosed())
                continue;

            // Padding Mask: Determine if this row is just empty padding for the grid
            boolean isEffectivelyActive = c.hasFloated()
                    || (c.getPresidentsShare() != null && c.getPresidentsShare().getOwner() instanceof Player);
            boolean isPadding = !isStockRound && !isEffectivelyActive;

            // FORCE RESET TRAINS: Ensure we are in "Display Mode" (HTML) at the start of
            // every update.
            // This clears any leftover buttons from a previous action and refreshes the
            // HTML list.
            setCompanyTrainButton(i, false, null);

            // Fix: Compare IDs to ensure the horizontal yellow line works
            boolean isOperating = (opCompId != null) && c.getId().equals(opCompId);

            boolean isMinor = !c.hasStockPrice();

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
                bgPool = BG_SPOTLIGHT_INACTIVE;
                bgIpo = BG_SPOTLIGHT_INACTIVE;
                bgCurr = BG_SPOTLIGHT_INACTIVE;

                bgDet = BG_MAUVE;
                bgCurr = BG_POOL;
            }

            setIPOCertButton(i, false);
            setPoolCertButton(i, false);

            if (hasOSI) {
                setOSICertButton(i, false);
            }

            // Update Company Name background (ALWAYS STANDARD, never yellow)
            if (compNameCaption[i] != null) {
                // Ensure XML-defined colors are used for all companies
                compNameCaption[i].setBackground(c.getBgColour());
                compNameCaption[i].setForeground(c.getFgColour());
                compNameCaption[i].setOpaque(true);
            }

            // Update Pool Panel Background
            if (poolPanels[i] != null) {
                poolPanels[i].setBackground(bgPool);
            }

            // Update Pool Share Card Text
            if (poolShareCards[i] != null) {
                if (stickyFont != null)
                    poolShareCards[i].setFont(stickyFont);

                poolShareCards[i].getCertificates().clear();
                poolShareCards[i].getCertificates().addAll(pool.getCertificates(c));

                String shareTxt = pool.getShareModel(c).toText();
                if (shareTxt == null)
                    shareTxt = "";

                // STRICT VISIBILITY: Hide if empty, whitespace, 0, or 0%
                String trimmed = shareTxt.trim();
                boolean hasContent = !trimmed.isEmpty() && !trimmed.equals("0") && !trimmed.equals("0%");
                if (isPadding)
                    hasContent = false;

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
                if (c.hasStockPrice() && c.getCurrentSpace() != null && !isPadding) {
                    poolPriceLabels[i].setText(gameUIManager.format(c.getCurrentSpace().getPrice()));
                    poolPriceLabels[i].setForeground(new Color(0, 0, 128)); // Navy Blue

                    // Use stickyFont if available (Zoom preserved), otherwise current font
                    Font baseFont = (stickyFont != null) ? stickyFont : poolPriceLabels[i].getFont();
                    if (baseFont != null) {
                        poolPriceLabels[i].setFont(baseFont.deriveFont(Font.BOLD));
                    }
                    poolPriceLabels[i].setHorizontalAlignment(SwingConstants.RIGHT);

                    // TRANSPARENCY: Force labels to be transparent so they inherit the row color
                    poolPriceLabels[i].setOpaque(false);
                    // Ensure the label fills the space so alignment works
                    // poolPriceLabels[i].setPreferredSize(new Dimension(30, 20));

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
                if (stickyFont != null)
                    ipoShareCards[i].setFont(stickyFont);

                ipoShareCards[i].getCertificates().clear();
                ipoShareCards[i].getCertificates().addAll(ipo.getCertificates(c));

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

                int price = c.getParPrice();

                // Display if we have a valid price (>0) and shares are available.
                // We intentionally ignore the global 'hasParPrices' flag here because 1837 sets
                // it to false,
                // effectively hiding these valid fixed prices otherwise.
                if (price > 0 && hasSharesInIPO && !isPadding) {
                    ipoParLabels[i].setText(gameUIManager.format(price));
                    ipoParLabels[i].setForeground(new Color(0, 0, 128)); // Navy Blue

                    // FORMATTING: Right Align + Bigger Font
                    // Use stickyFont if available (Zoom preserved)
                    Font baseFont = (stickyFont != null) ? stickyFont : ipoParLabels[i].getFont();
                    if (baseFont != null) {
                        ipoParLabels[i].setFont(baseFont.deriveFont(Font.BOLD));
                    }
                    ipoParLabels[i].setHorizontalAlignment(SwingConstants.RIGHT);
                    // ipoParLabels[i].setPreferredSize(new Dimension(30, 20));
                } else {
                    ipoParLabels[i].setText("");
                }

            }

            if (hasOSI && osiPanels[i] != null) {
                osiPanels[i].setBackground(bgIpo);

                if (osiShareCards[i] != null && osi != null) {
                    if (stickyFont != null)
                        osiShareCards[i].setFont(stickyFont);

                    osiShareCards[i].getCertificates().clear();
                    osiShareCards[i].getCertificates().addAll(osi.getCertificates(c));

                    String shareTxt = osi.getShareModel(c).toText();
                    if (shareTxt == null)
                        shareTxt = "";

                    String trimmed = shareTxt.trim();
                    boolean hasContent = !trimmed.isEmpty() && !trimmed.equals("0") && !trimmed.equals("0%");

                    osiShareCards[i].setCustomLabel(shareTxt);
                    osiShareCards[i].setVisible(hasContent);
                    osiShareCards[i].setForeground(new Color(139, 0, 0)); // Dark Red for Shorts
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
                    certInTreasury[i].setBackground(bgIpo);
                    certInTreasury[i].setOpaque(true);
                    if (treasuryPanels[i] != null) {
                        treasuryPanels[i].setBackground(bgIpo);
                    }

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

            if (compCanBuyPrivates && compPrivatesPanel[i] != null) {
                compPrivatesPanel[i].setBackground(bgRow);
                compPrivatesPanel[i].setOpaque(true);
                updateCompanyPrivates(i, c);
            }

            boolean is1817 = "1817".equals(gameUIManager.getGameManager().getGameName());
            if (hasCompanyLoans && compLoans != null && i < compLoans.length && compLoans[i] != null) {
                compLoans[i].setBackground(bgRow);
                compLoans[i].setOpaque(true);

                if (compLoans[i] instanceof ClickField) {
                    ClickField cf = (ClickField) compLoans[i];
                    cf.setEnabled(true);
                    cf.setPossibleAction(null);
                    cf.setBorder(BORDER_BOX);
                }

                if (c != null && (c.hasFloated() || (is1817 && c.getPresident() != null))) {
                    int currentBonds = c.getNumberOfBonds();
                    int maxBonds = currentBonds;
                    try {
                        java.lang.reflect.Method m = c.getClass().getMethod("getShareCount");
                        maxBonds = (Integer) m.invoke(c);
                    } catch (Exception e) {
                        // Fallback
                    }

                    // Calculate Interest Cost using the BondsModel_1817 rate
                    int interestRate = 0;
                    if (gameUIManager.getGameManager() instanceof net.sf.rails.game.specific._1817.GameManager_1817) {
                        net.sf.rails.game.model.BondsModel bm = ((net.sf.rails.game.specific._1817.GameManager_1817) gameUIManager
                                .getGameManager()).getBondsModel();
                        if (bm instanceof net.sf.rails.game.specific._1817.BondsModel_1817) {
                            interestRate = ((net.sf.rails.game.specific._1817.BondsModel_1817) bm).getInterestRate();
                        }
                    }
                    int totalInterestCost = currentBonds * interestRate;

                    // Build the Visual Dot String
                    StringBuilder sb = new StringBuilder("<html><center>");
                    // Red dots for loans taken
                    for (int b = 0; b < currentBonds; b++) {
                        sb.append("<font color='red'>●</font>");
                    }
                    // Red circles for available slots
                    for (int b = 0; b < (maxBonds - currentBonds); b++) {
                        sb.append("<font color='black'>○</font>");
                    }
                    sb.append("</center></html>");
                    String loanDisplay = sb.toString();

                    if (compLoans[i] instanceof ClickField) {
                        ((ClickField) compLoans[i]).setText(loanDisplay);
                    } else if (compLoans[i] instanceof JLabel) {
                        ((JLabel) compLoans[i]).setText(loanDisplay);
                    }

                    if (possibleActions != null && possibleActions.getList() != null) {
                        for (PossibleAction pa : possibleActions.getList()) {
                            if (pa != null && pa.getClass().getName().endsWith("TakeLoans_1817")) {
                                net.sf.rails.game.specific._1817.action.TakeLoans_1817 tl = (net.sf.rails.game.specific._1817.action.TakeLoans_1817) pa;
                                if (tl.getCompanyId().equals(c.getId())) {
                                    if (compLoans[i] instanceof ClickField) {
                                        ClickField cf = (ClickField) compLoans[i];
                                        cf.setEnabled(true);
                                        cf.setPossibleAction(pa);
                                        // MARK RED: Highlight the field background and border
                                        cf.setBackground(new Color(255, 200, 200));
                                        cf.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                                    }
                                }
                            }
                        }
                    }
                } else if (compLoans[i] instanceof JLabel) {
                    ((JLabel) compLoans[i]).setText("");
                }
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
                // PRIORITY 1: Operating Row (Horizontal White Line)
                // If this company is operating, the whole row MUST be white to create the "cut
                // through" effect.
                // PRIORITY 2: Active Player (Vertical Spotlight)
                // PRIORITY 3: Inactive (Grey)

                Color cellBg;
                if (isOperating) {
                    cellBg = Color.WHITE; // Force continuous white line
                } else if (pIdx == actorIndex) {
                    cellBg = BG_SPOTLIGHT_ACTIVE; // White Spotlight
                } else {
                    cellBg = BG_SPOTLIGHT_INACTIVE; // Grey Dimmed
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

                        card.getCertificates().clear();
                        card.getCertificates().addAll(player.getPortfolioModel().getCertificates(c));

                        String raw = player.getPortfolioModel().getShareModel(c).toText();
                        if (raw == null)
                            raw = "";

                        // Visibility Check
                        String check = raw.replace("%", "").trim();
                        boolean isZero = check.equals("0") || check.isEmpty();

                        card.setVisible(!isZero);

                        if (!isZero) {
                            card.setOpaque(true);

                            String cleanText = raw.replace("PU", "P");

                            // TEXT LOGIC: Only show "Owner" for Minors (no stock price).
                            // Majors should show "100%" (or "100%P" which we clean up)
                            if (cleanText.contains("100%P")) {
                                if (c.hasStockPrice()) {
                                    cleanText = "100%"; // Majors
                                } else {
                                    cleanText = c.getId(); // Minors
                                    card.setCompanyDetailsTooltip(c);
                                }
                            }
                            // 2. BOLD LOGIC: Only bold the President's share ("P") or Owner
                            boolean isPrez = cleanText.contains("P") || cleanText.equals("Owner");

                            // CLEANER LOOK: Revert to Black for double shares.
                            // Only use color if you strictly need it, otherwise Black is cleaner.
                            String textColor = "#000000";
                            // If you want a subtle hint for double shares, you could use a very dark grey,
                            // but let's stick to Black as requested.

                            StringBuilder sb = new StringBuilder("<html><center>");

                            // Apply Color
                            sb.append("<font color='").append(textColor).append("'>");

                            // Apply Bold ONLY if President
                            if (isPrez)
                                sb.append("<b>");

                            sb.append(cleanText);

                            if (isPrez)
                                sb.append("</b>");

                            sb.append("</font></center></html>");

                            card.setCustomLabel(sb.toString());

                        }
                    }

                }
            }

        }

        // 2. PLAYER FOOTERS
        for (int i = 0; i < np; i++) {
            // 1. Spotlight Logic
            boolean isSpotlight = (i == actorIndex);
            Color pBg = isSpotlight ? BG_SPOTLIGHT_ACTIVE : BG_SPOTLIGHT_INACTIVE;

            // 2. Header Logic
            // TIDY UP: Only show "Passed" status if there is an active actor (actorIndex !=
            // -1).
            // If actorIndex is -1 (End of Round), we force a clean slate.
            Player p = players.getPlayerByPosition(i);
            String log = gameUIManager.getGameManager().getPassedPlayersLog();

            boolean passed = (actorIndex != -1) && log != null
                    && java.util.Arrays.asList(log.split(", ")).contains(p.getName());

            // CHANGED: Do NOT change background color for passing. Use standard spotlight
            // color.
            Color headerBg = isSpotlight ? new Color(100, 255, 100) : pBg;

            if (upperPlayerCaption[i] != null) {
                upperPlayerCaption[i].setBackground(headerBg);
                upperPlayerCaption[i].setOpaque(true);
                upperPlayerCaption[i].setBorder(BORDER_DEFAULT);

                // Update the Pass Dot
                if (upperPlayerCaption[i] instanceof PassIndicatorCaption) {
                    // STRICT CLEARED LOGIC:
                    // If actorIndex is -1 (Round End/Transition), force clear (false).
                    // Otherwise, respect the 'passed' flag.
                    boolean showPass = (actorIndex != -1) && passed;
                    ((PassIndicatorCaption) upperPlayerCaption[i]).setPassed(showPass);
                }

                // Keep text color logic
                if (isSpotlight) {
                    upperPlayerCaption[i].setForeground(Color.BLACK);
                } else {
                    upperPlayerCaption[i].setForeground(Color.DARK_GRAY);
                }
            }

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

                // 1. Update Background (Spotlight Logic: White vs Grey)
                playerCertCount[i].setBackground(pBg);

                // 2. Fetch Data Directly (No String Parsing, No ModelObject casting)
                // We use the same logic as StatusWindow to get authoritative numbers.
                try {
                    net.sf.rails.game.Player targetPlayer = players.getPlayerByPosition(i);
                    if (p != null) {
                        // getCertificateCount returns float (for 1835 partials), cast to int for
                        // display
                        int held = (int) p.getPortfolioModel().getCertificateCount();

                        // Access GameManager via the UI Manager to get the dynamic limit
                        int limit = gameUIManager.getGameManager().getPlayerCertificateLimit(p);

                        playerCertCount[i].update(held, limit);
                    }
                } catch (Exception e) {
                    // Fallback to safe defaults to prevent visual glitch
                    playerCertCount[i].update(0, 1);
                }

            }
        }

        // REPLACED: Use the new Panel variables instead of the deleted Field variables
        // Re-apply the synchronized Orange to the market panels
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

        // Update the visual round counter
        if (roundCounterPanel != null) {
            roundCounterPanel.updateState();
        }

        // Trigger the LinearRoundTracker animation
        // We use updateState() to internally fetch the round and trigger the timer
        // This matches the logic of RoundCounterPanel exactly.
        if (linearRoundTracker != null) {
            linearRoundTracker.updateState();
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

        updateFixedIncome();
        updatePlayerPrivates();
        updateTrainCosts();
        updateBondsHeatbar();

        // 3. ENABLE BUTTONS
        if ((pIdx = this.actorIndex) >= -1 && myTurn) {

            // FORCE FOCUS FOR HOTKEYS, BUT DISABLE 'toFront()' TO STOP FLICKER
            // The window must have focus for keys (like Pass) to work, but
            // calling toFront() causes aggressive window popping.
            if (parentFrame != null && parentFrame.isVisible()) {
                // parentFrame.toFront(); // REMOVED: Causes flicker/pop-up
                parentFrame.requestFocusInWindow(); // GENTLE: Requests focus without Z-order change
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
                    else if (hasOSI && osi != null && portfolio == osi)
                        setOSICertButton(index, true, bCert);
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

            if (possibleActions.getList() != null) {
                for (PossibleAction pa : possibleActions.getList()) {
                    if (pa.getClass().getName().endsWith("CompanyBuyOpenMarketShare_1817")) {
                        try {
                            String compId = (String) pa.getClass().getMethod("getCompanyId").invoke(pa);
                            PublicCompany comp = gameUIManager.getRoot().getCompanyManager().getPublicCompany(compId);
                            if (comp != null) {
                                log.info("Binding CompanyBuyOpenMarketShare_1817 to pool cert for: " + comp.getId());
                                setPoolCertButton(comp.getPublicNumber(), true, pa);
                            }
                        } catch (Exception e) {
                            log.error("Failed to bind CompanyBuyOpenMarketShare_1817", e);
                        }
                    }
                }
            }

            // 1817 Short Binding: Map Sell Short actions to the OSI column
            if (possibleActions.getList() != null) {
                for (PossibleAction pa : possibleActions.getList()) {
                    if (pa.getClass().getName().endsWith("Short1817")) {
                        try {
                            String compId = (String) pa.getClass().getMethod("getCompanyId").invoke(pa);
                            PublicCompany comp = gameUIManager.getRoot().getCompanyManager().getPublicCompany(compId);
                            if (comp != null) {
                                setOSICertButton(comp.getPublicNumber(), true, pa);
                            }
                        } catch (Exception e) {
                            log.error("Failed to bind Short1817 action", e);
                        }
                    }
                }
            }

            // 1817 IPO Binding: Map Initiate1817IPO actions to the IPO column
            if (possibleActions.getList() != null) {
                for (PossibleAction pa : possibleActions.getList()) {
                    if (pa.getClass().getName().endsWith("Initiate1817IPO")) {
                        try {
                            PublicCompany comp = null;
                            try {
                                comp = (PublicCompany) pa.getClass().getMethod("getCompany").invoke(pa);
                            } catch (Exception e1) {
                                String compId = (String) pa.getClass().getMethod("getCompanyId").invoke(pa);
                                comp = gameUIManager.getRoot().getCompanyManager().getPublicCompany(compId);
                            }
                            if (comp != null) {
                                setIPOCertButton(comp.getPublicNumber(), true, pa);
                            }
                        } catch (Exception e) {
                            log.error("Failed to bind Initiate1817IPO action", e);
                        }
                    }
                }
            }

            setTrainBuyingActions(possibleActions.getList());
        }

        // Unconditionally sync the Pass/Done button with the engine state
        if (possibleActions != null) {
            java.util.List<NullAction> nullActions = possibleActions.getType(NullAction.class);
            if (nullActions != null) {
                for (NullAction na : nullActions) {
                    (parent).setPassButton(na);
                }
            }
        }

        try {
            initGameSpecificActions();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // The Kill Signal: ORUIManager silently aborts updates during Player-driven
        // phases (like Auctions).
        // We manually broadcast an empty action list to force the ORPanel to trigger
        // its dormancy intercept.
        if (possibleActions != null && possibleActions.getList() != null) {
            boolean isPlayerExclusivePhase = false;
            for (PossibleAction pa : possibleActions.getList()) {
                String paName = pa.getClass().getSimpleName();
                if (paName.contains("Bid") || paName.contains("OfferCompany") || paName.contains("SettleIPO")) {
                    isPlayerExclusivePhase = true;
                    break;
                }
            }
            if (isPlayerExclusivePhase) {
                net.sf.rails.ui.swing.ORPanel.forceUpdateForManager(gameUIManager, new java.util.ArrayList<>());
            }
        }
        repaint();
    }

    protected void setOSICertButton(int i, boolean clickable, Object o) {
        RowVisibility observer = null;
        if (shareRowVisibilityObservers != null && i >= 0 && i < shareRowVisibilityObservers.length) {
            observer = shareRowVisibilityObservers[i];
        }

        if (observer == null || osiShareCards == null || osiShareCards[i] == null || osi == null) {
            return;
        }

        if (companies == null || i < 0 || i >= companies.length || companies[i] == null) {
            return;
        }

        if (stickyFont != null) {
            osiShareCards[i].setFont(stickyFont);
        }

        osiShareCards[i].setShareStackTooltip(osi.getCertificates(companies[i]));

        boolean visible = observer.lastValue();
        if (!visible) {
            osiShareCards[i].setVisible(false);
            return;
        }

        net.sf.rails.game.model.ShareModel sm = osi.getShareModel(companies[i]);
        String shareTxt = (sm != null) ? sm.toText() : "";
        boolean hasContent = (shareTxt != null && !shareTxt.trim().isEmpty() && !shareTxt.trim().equals("0")
                && !shareTxt.trim().equals("0%"));

        osiShareCards[i].setVisible(hasContent);
        osiShareCards[i].clearPossibleActions();
        // House Rule Display: If percentage is 0 but ShortCertificates exist, show the
        // count (e.g., "5")

        if ((shareTxt == null || shareTxt.trim().isEmpty() || shareTxt.equals("0%")) && osi != null) {
            int shortCertCount = 0;
            for (net.sf.rails.game.financial.PublicCertificate cert : osi.getCertificates(companies[i])) {
                if (cert instanceof net.sf.rails.game.specific._1817.ShortCertificate) {
                    shortCertCount++;
                }
            }
            if (shortCertCount > 0) {
                shareTxt = String.valueOf(shortCertCount);
                hasContent = true;
            }
        }
        // Update the card text and visibility
        osiShareCards[i].setCustomLabel(shareTxt);
        osiShareCards[i].setVisible(hasContent);

        if (clickable && o != null) {
            // House Rule: Apply the "Big Green Border" to indicate the short is available
            // to be taken
            osiShareCards[i].setBackground(BG_CARD_PASSIVE);
            osiShareCards[i].setBorder(BorderFactory.createLineBorder(BORDER_COL_BUY, BORDER_THICKNESS));

            if (o instanceof PossibleAction) {
                osiShareCards[i].addPossibleAction((PossibleAction) o);
            }
            osiShareCards[i].setEnabled(true);
            osiShareCards[i].setVisible(true); // Ensure it is visible if actionable
        } else {
            // Standard passive state with dark red text for shorts
            osiShareCards[i].setBackground(BG_CARD_PASSIVE);
            osiShareCards[i].setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.BLACK, 1),
                    BorderFactory.createEmptyBorder(1, 1, 1, 1)));
            osiShareCards[i].setEnabled(true);
        }
    }

    protected void setOSICertButton(int i, boolean clickable) {
        setOSICertButton(i, clickable, null);
    }

    /**
     * A Custom Flat Progress Bar for Certificate Limits.
     * Visualizes "Held vs Limit" with color coding and text overlay.
     */
    private static class CertLimitGauge extends JLabel {
        public CertLimitGauge() {
            setOpaque(true);
            setFont(new Font("SansSerif", Font.BOLD, 12));
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        public void update(int held, int limit) {
            setText(held + "/" + limit);
        }
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
        // Reset ALL IPO buttons by looping through the array index
        for (int i = 0; i < MAX_IPO_SLOTS; i++) {
            setNewTrainButton(i, false, null);
        }
        setFutureTrainButton(false, null);

        for (int i = 0; i < MAX_IPO_SLOTS; i++) {
            setNewTrainButton(i, false, null);
        }

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
                    // Convert Set to List to find the correct slot index for the UI
                    java.util.List<net.sf.rails.game.Train> ipoList = new java.util.ArrayList<>(ipo.getTrainList());
                    int idx = ipoList.indexOf(target);

                    // Only set the button if the train fits in our visible slots
                    if (idx >= 0 && idx < MAX_IPO_SLOTS) {
                        setNewTrainButton(idx, true, a);
                    }
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

    protected void setNewTrainButton(int index, boolean clickable, PossibleAction action) {
        if (index < 0 || index >= MAX_IPO_SLOTS)
            return;

        RailCard btn = newTrainButtons[index];
        if (btn == null)
            return;

        // Reset Logic
        btn.clearPossibleActions();

        // Base Style (Passive)
        btn.setBackground(BG_CARD_PASSIVE);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.BLACK, 1),
                BorderFactory.createEmptyBorder(1, 1, 1, 1)));
        btn.setEnabled(true);

        if (clickable) {
            btn.setVisible(true);
            btn.setEnabled(true);

            // FIX: Use Beige Background + Green Border for "Buyable" state
            btn.setBackground(BG_CARD_PASSIVE);
            btn.setBorder(BorderFactory.createLineBorder(BORDER_COL_BUY, 3));

            if (action != null) {
                btn.addPossibleAction(action);
            }
        }
    }

    protected void setFutureTrainButton(boolean clickable, PossibleAction action) {
        if (futureTrainButtons == null)
            return;

        // If not clickable, we are resetting.
        // We do NOT want to hide them (setVisible(false)) because updateTrainCosts has
        // set them up as passive cards.
        if (!clickable) {
            for (RailCard cf : futureTrainButtons) {
                if (cf != null && cf.isVisible()) {
                    // Restore passive look
                    cf.clearPossibleActions();
                    cf.setBackground(BG_CARD_PASSIVE);
                    cf.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(Color.BLACK, 1),
                            BorderFactory.createEmptyBorder(1, 1, 1, 1)));
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
                if (cf == null)
                    continue;

                String btnName = cf.getName();
                boolean isVis = cf.isVisible();

                // We stored the train Name/ID in Component Name in updateTrainCosts
                if (isVis && targetName.equals(btnName)) {

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

        // --- START FIX ---
        // Generic: The "Double Share" is defined by the size of the President's
        // Certificate.
        // We do not assume 20% (1830) or 10% (1835 Prussia).
        int requiredShare = 20; // Safe default
        if (company.getPresidentsShare() != null) {
            requiredShare = company.getPresidentsShare().getShare();
        }

        // Iterate through all PublicCertificates held by this portfolio for the company
        for (net.sf.rails.game.financial.PublicCertificate cert : portfolio.getCertificates(company)) {

            if (cert.getShare() >= requiredShare) {
                return true;
            }
        }
        return false;
    }

    public void setTrainBuyingActions(List<PossibleAction> actions) {
        updateTrainCosts();

        int nc = companies.length;
        for (int i = 0; i < nc; i++)
            setCompanyTrainButton(i, false, null);
        setPoolTrainButton(false, null);
        for (int i = 0; i < MAX_IPO_SLOTS; i++)
            setNewTrainButton(i, false, null);
        setFutureTrainButton(false, null);

        if (actions == null || actions.isEmpty())
            return;

        for (PossibleAction pa : actions) {
            if (pa instanceof BuyTrain) {
                BuyTrain bt = (BuyTrain) pa;
                net.sf.rails.game.state.Owner source = bt.getFromOwner();
                net.sf.rails.game.Train targetTrain = bt.getTrain();

                if (source == null)
                    continue;

                boolean isBankSource = (source instanceof net.sf.rails.game.financial.Bank) ||
                        (source.getParent() instanceof net.sf.rails.game.financial.Bank);

                if (isBankSource) {
                    String id = source.getId();
                    boolean srcIsIPO = "IPO".equals(id);
                    boolean srcIsPool = "Pool".equals(id);

                    if (!srcIsIPO && !srcIsPool) {
                        srcIsIPO = (targetTrain == null && ipo.getTrainList().size() > 0)
                                || (targetTrain != null && ipo.getTrainList().contains(targetTrain));
                        srcIsPool = (targetTrain != null && pool.getTrainList().contains(targetTrain));
                    }

                    if (srcIsIPO) {
                        if (targetTrain != null) {
                            // Normalize Name ("4_0" -> "4")
                            String tName = targetTrain.getName().replaceAll("_\\d+$", "");
                            boolean matched = false;

                            for (int i = 0; i < MAX_IPO_SLOTS; i++) {
                                // Match against the button's stored name (which is already normalized)
                                if (newTrainButtons[i].getName() != null &&
                                        newTrainButtons[i].getName().equals(tName)) {
                                    setNewTrainButton(i, true, bt);
                                    matched = true;
                                    break;
                                }
                            }

                        } else {
                            setNewTrainButton(0, true, bt);
                        }
                    } else if (srcIsPool) {
                        setPoolTrainButton(true, bt);
                    } else {
                        setFutureTrainButton(true, bt);
                    }
                } else if (source instanceof net.sf.rails.game.PublicCompany) {
                    PublicCompany c = (PublicCompany) source;
                    setCompanyTrainButton(c.getPublicNumber(), true, bt);
                } else if (source.getParent() instanceof net.sf.rails.game.PublicCompany) {
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

        // 1. Apply Persistent Font (Responsiveness)
        if (stickyFont != null) {
            cf.setFont(stickyFont);
        }

        // 2. Enable Compact Mode (Center Alignment, No Table)
        cf.setCompactMode(true);

        // 3. Default Visuals (Passive)
        cf.setBackground(BG_CARD_PASSIVE);

        if (cf != null)
            cf.setVisible(false);
        return cf;
    }

    // Changed to 'public static' to allow ORPanel to reuse this logic
    public static void configureTrainButton(ClickField btn, String text, boolean isBuyable) {
        // 1. Strict Sizing
        // btn.setPreferredSize(DIM_TRAIN_BTN);
        // btn.setMinimumSize(DIM_TRAIN_BTN);
        // btn.setMaximumSize(DIM_TRAIN_BTN);
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

        // Group trains by name (Type) to consolidate display
        java.util.Map<String, java.util.List<net.sf.rails.game.Train>> groups = new java.util.LinkedHashMap<>();
        for (net.sf.rails.game.Train t : pool.getTrainList()) {
            String cleanName = t.getName().replaceAll("_\\d+$", "");
            groups.computeIfAbsent(cleanName, k -> new java.util.ArrayList<>()).add(t);
        }

        java.util.List<BuyTrain> buyActions = clickable ? possibleActions.getType(BuyTrain.class) : null;

        // 3. Populate
        int slotIndex = 0;
        for (java.util.Map.Entry<String, java.util.List<net.sf.rails.game.Train>> entry : groups.entrySet()) {
            if (slotIndex >= MAX_POOL_SLOTS)
                break;

            String cleanName = entry.getKey();
            java.util.List<net.sf.rails.game.Train> group = entry.getValue();
            net.sf.rails.game.Train representative = group.get(0);
            int count = group.size();

            RailCard cf = poolTrainButtons[slotIndex];

            // Explicitly define lbl here to fix "cannot find symbol"
            javax.swing.JLabel lbl = (poolTrainInfoLabels != null) ? poolTrainInfoLabels[slotIndex] : null;
            if (cf != null) {
                cf.setTrain(representative); // Use RailCard logic
                cf.setCustomLabel(getAbbreviatedTrainName(cleanName));
            }

            // Set Label: (Count) / Price
            int cost = representative.getType().getCost();
            if (lbl != null) {
                lbl.setText("<html><center>(" + count + ")<br>" +
                        "<font color='#000080'><b>" + gameUIManager.format(cost) + "</b></font>" +
                        "</center></html>");
            }

            boolean canBuy = false;
            if (clickable && buyActions != null) {
                for (BuyTrain ba : buyActions) {
                    // If action targets ANY train in this group, attach it
                    if (group.contains(ba.getTrain())) {
                        cf.addPossibleAction(ba);
                        canBuy = true;
                        // Attach the first valid action found and stop (UI button represents the group)
                        break;
                    }
                }
            }

            // Apply Styles: Beige + Green Border
            if (canBuy) {
                // OLD: cf.setBackground(BG_BUY_ACTIVE);
                cf.setBackground(BG_CARD_PASSIVE); // Beige
                cf.setBorder(BorderFactory.createLineBorder(BORDER_COL_BUY, 3)); // Thick Green Border

                cf.setToolTipText("Click to Buy " + representative.getName());
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
            slotIndex++;
        }

    }

    // Shared Styling Constants (Promoted from local variables for modular access)
    private static final Color BG_MINOR = Color.BLACK;
    private static final Color FG_MINOR = Color.WHITE;
    // Synchronized with ORPanel.java's getTrainHighlightColor()
    private static final Color BG_TRAINS = new Color(255, 222, 173);
    private static final Color BG_BANK = new Color(176, 224, 230);
    // BG_OPERATING is already a class field (line 78)

    // Borders
    private static final Color OUT = Color.BLACK;
    private static final int THICK = 2;
    private static final int THIN = 1;
    private static final javax.swing.border.Border BORDER_THIN = BorderFactory.createMatteBorder(0, 0, 1, 1,
            Color.GRAY);

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
    // Custom Dashed Border for Empty Train Slots
    private static final javax.swing.border.Border BORDER_DASHED = new javax.swing.border.AbstractBorder() {
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(Color.GRAY);
            // 2px dash, 2px gap
            g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 2 }, 0));
            g2.drawRect(x, y, width - 1, height - 1);
            g2.dispose();
        }

    };
    // A "weaker" background for placeholders (Pale Beige/White)
    private static final Color BG_PLACEHOLDER = new Color(250, 250, 245);

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
        } else if (currentRound instanceof net.sf.rails.game.specific._1817.MergerAndAcquisitionRound_1817) {
            operatingComp = ((net.sf.rails.game.specific._1817.MergerAndAcquisitionRound_1817) currentRound)
                    .getOperatingCompany();
        }
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

        if (compCanBuyPrivates) {
            if (compPrivatesPanel == null || compPrivatesPanel.length != nc)
                compPrivatesPanel = new JPanel[nc];
        }

        if (hasCompanyLoans) {
            if (compLoans == null || compLoans.length != nc) {
                compLoans = new JComponent[nc];
            }
        }

        // Initialize IPO Arrays
        if (ipoPanels == null || ipoPanels.length != nc)
            ipoPanels = new JPanel[nc];
        if (ipoShareCards == null || ipoShareCards.length != nc)
            ipoShareCards = new RailCard[nc];
        if (ipoParLabels == null || ipoParLabels.length != nc)
            ipoParLabels = new javax.swing.JLabel[nc];
        if (hasOSI) {
            if (osiPanels == null || osiPanels.length != nc)
                osiPanels = new JPanel[nc];
            if (osiShareCards == null || osiShareCards.length != nc)
                osiShareCards = new RailCard[nc];
            if (osiLabels == null || osiLabels.length != nc)
                osiLabels = new javax.swing.JLabel[nc];
        }
        if (treasuryPanels == null || treasuryPanels.length != nc)
            treasuryPanels = new JPanel[nc];
        if (treasuryShareCards == null || treasuryShareCards.length != nc)
            treasuryShareCards = new RailCard[nc];

        if (poolPanels == null || poolPanels.length != nc)
            poolPanels = new JPanel[nc];
        if (poolShareCards == null || poolShareCards.length != nc)
            poolShareCards = new RailCard[nc];
        if (poolPriceLabels == null || poolPriceLabels.length != nc)
            poolPriceLabels = new javax.swing.JLabel[nc];

        // --- START FIX: NEW TRAIN (IPO) ARRAYS ---
        // We must ensure the arrays AND the objects inside them exist before
        // initTrainMarket uses them.
        if (newTrainButtons == null || newTrainButtons.length != MAX_IPO_SLOTS) {
            newTrainButtons = new RailCard[MAX_IPO_SLOTS];
            for (int i = 0; i < MAX_IPO_SLOTS; i++) {
                newTrainButtons[i] = createTrainButton();
            }
        }
        if (newTrainQtyLabels == null || newTrainQtyLabels.length != MAX_IPO_SLOTS) {
            newTrainQtyLabels = new javax.swing.JLabel[MAX_IPO_SLOTS];
            for (int i = 0; i < MAX_IPO_SLOTS; i++) {
                newTrainQtyLabels[i] = new javax.swing.JLabel("");
            }
        }

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
        // int arrowCol = col++;
        this.compNameCol = col++;
        certPerPlayerXOffset = col;
        col += np;
        certInPoolXOffset = col++;
        certInIPOXOffset = col++;

        if (compCanHoldOwnShares)
            certInTreasuryXOffset = col++;

        if (hasOSI) {
            certInOSIXOffset = col++;
        }

        compCashXOffset = col++;
        compTrainsXOffset = col++;

        compRevenueXOffset = col++;
        compRetainedXOffset = col++;
        compTokensXOffset = col++;

        if (compCanBuyPrivates)
            compPrivatesXOffset = col++;
        if (hasCompanyLoans)
            compLoansXOffset = col++;
        if (hasRights)
            rightsXOffset = col++;
        rightCompCaptionXOffset = col++;

        int actual_nc = 0;
        int actual_nb = 0;
        java.util.List<PublicCompany> gridList = new java.util.ArrayList<>();
        java.util.List<PublicCompany> inactiveGridList = new java.util.ArrayList<>();

        net.sf.rails.game.round.RoundFacade currentRoundForFields = gameUIManager.getGameManager().getCurrentRound();
        boolean isStockRoundFields = isMarketOrAuctionRound(currentRoundForFields);

        if (companies != null) {
            for (PublicCompany c : companies) {
                if (c.isClosed())
                    continue;
                boolean isActive = c.hasFloated()
                        || (c.getPresidentsShare() != null && c.getPresidentsShare().getOwner() instanceof Player);
                if (isStockRoundFields || isActive) {
                    gridList.add(c);
                } else {
                    inactiveGridList.add(c);
                }
            }
        }

        if (!isStockRoundFields && gridList.size() < 10) {
            int needed = 10 - gridList.size();
            for (int i = 0; i < Math.min(needed, inactiveGridList.size()); i++) {
                gridList.add(inactiveGridList.get(i));
            }
        }

        actual_nc = gridList.size();
        for (PublicCompany c : gridList) {
            if (c.hasBonds())
                actual_nb++;
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

        // We place this in initFields() so it persists after recreate() calls.
        linearRoundTracker = new LinearRoundTracker(gameUIManager, companies);

        // Calculate Grid Spans (using offsets calculated in init)
        int startCol = certInPoolXOffset;
        int endCol = compTokensXOffset;
        int gridWidth = endCol - startCol + 1;

        // Rows: "Fixed Inc" (Timer-1) to "Time" (Timer)
        int startRow = playerTimerYOffset - 1;
        int gridHeight = 2;

        // Reset GBC constraints
        gbc.gridx = startCol;
        gbc.gridy = startRow;
        gbc.gridwidth = gridWidth;
        gbc.gridheight = gridHeight;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;

        gb.setConstraints(linearRoundTracker, gbc);
        add(linearRoundTracker);

        // Implement Linear Operating Round Tracker
        // We add this here because 'recreate()' calls removeAll() then initFields().

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

    protected void initHeaders() {
        for (int i = 0; i < np; i++) {
            f = upperPlayerCaption[i] = new PassIndicatorCaption(players.getPlayerByPosition(i).getName());

            if (i == np - 1) {
                f.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 4, Color.BLACK));
            } else {
                f.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Color.GRAY));
            }
            // Apply Uniform Header Color
            f.setBackground(BG_HEADER);
            f.setOpaque(true);

            f.setPreferredSize(DIM_PLAYER);
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            addField(f, certPerPlayerXOffset + i, 1, 1, 1, 0, true);
            gbc.weightx = 0.0;
        }
        // Calculate dynamic width for Market Columns (Pool/IPO)
        // Logic: Card (~5 chars) + Price (~3 chars) + Padding
        Font sizingFont = (stickyFont != null) ? stickyFont : new Font("SansSerif", Font.BOLD, 12);
        FontMetrics fm = getFontMetrics(sizingFont);
        int charWidth = fm.charWidth('0');
        int height = 20;

        int wCard = (charWidth * 5) + 6;
        // int wPrice = (charWidth * 3) + 6;
        int wPrice = fm.stringWidth("$999") + 10;
        int wTotal = wCard + wPrice + 4; // Card + Price + Gaps

        Dimension dimMarket = new Dimension(wTotal, height);

        f = new Caption(LocalText.getText("POOL"));
        f.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Color.GRAY));
        f.setPreferredSize(dimMarket); // Use dynamic width
        f.setBackground(BG_HEADER);
        f.setOpaque(true);
        addField(f, certInPoolXOffset, 1, 1, 1, 0, true);

        f = new Caption(LocalText.getText("IPO"));
        f.setBorder(BorderFactory.createMatteBorder(0, 0, 1, hasOSI ? 1 : 4, hasOSI ? Color.GRAY : Color.BLACK));
        f.setPreferredSize(dimMarket); // Use dynamic width
        f.setBackground(BG_HEADER);
        f.setOpaque(true);
        addField(f, certInIPOXOffset, 1, 1, 1, 0, true);

        if (compCanHoldOwnShares) {
            f = new Caption(LocalText.getText("TREASURY"));
            f.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Color.GRAY));
            f.setPreferredSize(DIM_STD);
            f.setBackground(BG_HEADER);
            f.setOpaque(true);
            addField(f, certInTreasuryXOffset, 1, 1, 1, 0, true);
        }

        if (hasOSI) {
            f = new Caption("OSI");
            f.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 4, Color.BLACK));
            f.setPreferredSize(DIM_STD);
            f.setBackground(BG_HEADER);
            f.setOpaque(true);
            addField(f, certInOSIXOffset, 1, 1, 1, 0, true);
        }

        String cashHeaderTxt = compCanHoldOwnShares ? LocalText.getText("CASH") : LocalText.getText("TREASURY");

        f = new Caption(cashHeaderTxt);
        f.setBorder(BorderFactory.createCompoundBorder(BORDER_THIN, BorderFactory.createEmptyBorder(0, 0, 0, 5)));
        ((JLabel) f).setHorizontalAlignment(SwingConstants.RIGHT);
        f.setBackground(BG_HEADER);
        f.setOpaque(true);
        f.setPreferredSize(DIM_STD);
        addField(f, compCashXOffset, 1, 1, 1, 0, true);

        // 1. Trains Header
        f = new Caption(LocalText.getText("TRAINS"));
        f.setBorder(BORDER_THIN);
        f.setBackground(BG_HEADER);
        f.setOpaque(true);
        f.setPreferredSize(DIM_TRAIN);
        addField(f, compTrainsXOffset, 1, 1, 1, 0, true);

        // 2. Dividend Header
        f = new Caption("Dividend");
        f.setBorder(BorderFactory.createCompoundBorder(BORDER_THIN, BorderFactory.createEmptyBorder(0, 0, 0, 5)));
        ((JLabel) f).setHorizontalAlignment(SwingConstants.RIGHT);
        f.setBackground(BG_HEADER);
        f.setOpaque(true);
        f.setPreferredSize(DIM_STD);
        addField(f, compRevenueXOffset, 1, 1, 1, 0, true);

        // 3. Retained Header
        f = new Caption("Retained");
        f.setBorder(BorderFactory.createCompoundBorder(BORDER_THIN, BorderFactory.createEmptyBorder(0, 0, 0, 5)));
        ((JLabel) f).setHorizontalAlignment(SwingConstants.RIGHT);
        f.setBackground(BG_HEADER);
        f.setOpaque(true);
        f.setPreferredSize(DIM_STD);
        addField(f, compRetainedXOffset, 1, 1, 1, 0, true);

        // 4. Markers Header
        f = new Caption(LocalText.getText("TOKENS"));
        f.setBorder(BORDER_THIN);
        f.setBackground(BG_HEADER);
        f.setOpaque(true);
        f.setPreferredSize(DIM_TOKENS);
        addField(f, compTokensXOffset, 1, 1, 1, 0, true);

        if (compCanBuyPrivates) {
            f = new Caption(LocalText.getText("PRIVATES"));
            f.setBorder(BORDER_THIN);
            f.setPreferredSize(DIM_STD);
            addField(f, compPrivatesXOffset, 1, 1, 1, 0, true);
        }

        if (hasCompanyLoans) {
            f = new Caption("Loans");
            f.setBorder(BORDER_THIN);
            f.setBackground(BG_HEADER);
            f.setOpaque(true);
            f.setPreferredSize(DIM_STD);
            addField(f, compLoansXOffset, 1, 1, 1, 0, true);
        }

    }

    protected void initCompanyRows(int startY, PublicCompany operatingComp) {
        int y = startY;

        java.util.List<PublicCompany> displayList = new java.util.ArrayList<>();
        java.util.List<PublicCompany> inactiveList = new java.util.ArrayList<>();

        net.sf.rails.game.round.RoundFacade currentRound = gameUIManager.getGameManager().getCurrentRound();
        boolean isStockRound = isMarketOrAuctionRound(currentRound);

        for (PublicCompany c : gameUIManager.getAllPublicCompanies()) {
            if (c.isClosed())
                continue;
            boolean isActive = c.hasFloated() || (c.getPresidentsShare() != null
                    && c.getPresidentsShare().getOwner() instanceof net.sf.rails.game.Player);
            if (isStockRound || isActive) {
                displayList.add(c);
            } else {
                inactiveList.add(c);
            }
        }

        compNameCaption = new Caption[nc];
        // compArrowCaption = new Caption[nc]; // Initialize storage

        // Delegate sorting directly to the Engine's authoritative running order to
        // prevent MVC desync.
        final java.util.List<PublicCompany> engineOrder = gameUIManager.getGameManager().getCompaniesInRunningOrder();

        StringBuilder initOrderLog = new StringBuilder();
        if (engineOrder != null) {
            for (PublicCompany comp : engineOrder) {
                initOrderLog.append(comp.getId()).append(", ");
            }
        }

        java.util.List<PublicCompany> minorsList = new java.util.ArrayList<>();
        java.util.List<PublicCompany> majorsList = new java.util.ArrayList<>();
        java.util.List<PublicCompany> unfloatedList = new java.util.ArrayList<>();

        for (PublicCompany c : displayList) {
            boolean isEffectivelyActive = c.hasFloated()
                    || (c.getPresidentsShare() != null && c.getPresidentsShare().getOwner() instanceof Player);
            if (!isEffectivelyActive) {
                unfloatedList.add(c);
            } else if (!c.hasStockPrice()) {
                minorsList.add(c);
            } else {
                majorsList.add(c);
            }
        }

        // Minors maintain static creation order
        java.util.Collections.sort(minorsList, (c1, c2) -> Integer.compare(c1.getPublicNumber(), c2.getPublicNumber()));
        java.util.Collections.sort(unfloatedList,
                (c1, c2) -> Integer.compare(c1.getPublicNumber(), c2.getPublicNumber()));

        // Majors strictly follow the engine's authoritative order
        java.util.Collections.sort(majorsList, (c1, c2) -> {
            int idx1 = engineOrder != null ? engineOrder.indexOf(c1) : -1;
            int idx2 = engineOrder != null ? engineOrder.indexOf(c2) : -1;

            if (idx1 >= 0 && idx2 >= 0)
                return Integer.compare(idx1, idx2);
            if (idx1 >= 0)
                return -1;
            if (idx2 >= 0)
                return 1;
            return Integer.compare(c1.getPublicNumber(), c2.getPublicNumber());
        });

        displayList.clear();
        displayList.addAll(minorsList);
        displayList.addAll(majorsList);
        displayList.addAll(unfloatedList);

        // Append Padding (Inactive) Companies to the BOTTOM
        if (!isStockRound && displayList.size() < 10) {
            java.util.List<PublicCompany> inactivePool = new java.util.ArrayList<>();
            for (PublicCompany c : gameUIManager.getAllPublicCompanies()) {
                if (c.isClosed() || displayList.contains(c))
                    continue;
                inactivePool.add(c);
            }
            int needed = 10 - displayList.size();
            for (int k = 0; k < Math.min(needed, inactivePool.size()); k++) {
                displayList.add(inactivePool.get(k));
            }
        }

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
            boolean isSR = isMarketOrAuctionRound(gameUIManager.getGameManager().getCurrentRound());
            boolean hasOwner = c.getPresidentsShare() != null && c.getPresidentsShare().getOwner() instanceof Player;

            boolean isActive;
            if (isSR) {
                isActive = c.hasFloated() || hasOwner;
            } else {
                isActive = c.hasFloated();
            }

            // 1. RESTORE LOGOS (Arrow + Name)
            final int B_STD = 1;
            final int B_OP = 2;
            final int B_ZONE = 4;
            boolean isBottomChunk = (c == lastMinor);
            int tHeight = isOperating ? B_OP : 0;
            int bHeight = isBottomChunk ? B_ZONE : (isOperating ? B_OP : B_STD);

            javax.swing.border.Border bDet = BorderFactory.createMatteBorder(tHeight, 0, bHeight, 1, Color.BLACK);
            // javax.swing.border.Border bArrow = BorderFactory.createMatteBorder(tHeight,
            // 2, bHeight, 1, Color.BLACK);

            // compArrowCaption[i] = new Caption(isOperating ? "▶" : "");
            // compArrowCaption[i].setForeground(Color.RED.darker());
            // compArrowCaption[i].setBackground(Color.WHITE);
            // compArrowCaption[i].setOpaque(true);
            // compArrowCaption[i].setBorder(bArrow);
            // compArrowCaption[i].setPreferredSize(DIM_ARROW);

            // addField(compArrowCaption[i], 0, y, 1, 1, 0, visible);
            companyCertRow.put(c, y);

            javax.swing.border.Border bName = BorderFactory.createMatteBorder(tHeight, 0, bHeight, 1, Color.BLACK);

            String displayName = c.getId();
            boolean is1817 = "1817".equals(gameUIManager.getGameManager().getGameName());

            if (is1817 && (c.hasFloated()
                    || (c.getPresidentsShare() != null && c.getPresidentsShare().getOwner() instanceof Player))) {
                int numShares = 100 / Math.max(1, c.getShareUnit());
                displayName += " [" + numShares + "]";
            }

            compNameCaption[i] = new Caption(displayName);
            compNameCaption[i].setForeground(c.getFgColour());
            compNameCaption[i].setBackground(c.getBgColour());
            compNameCaption[i].setBorder(bName);

            HexHighlightMouseListener.addMouseListener(compNameCaption[i], gameUIManager.getORUIManager(), c, false);
            addField(compNameCaption[i], compNameCol, y, 1, 1, 0, visible);

            // 2. RESTORE BORDER HELPERS
            java.util.function.BiFunction<Boolean, Boolean, javax.swing.border.Border> getBorder = (isRightEdge,
                    isIPO) -> {
                int t = tHeight;
                int l = 0;
                int b = bHeight;
                int r = (isRightEdge || isIPO) ? B_ZONE : B_STD;
                return BorderFactory.createMatteBorder(t, l, b, r, Color.BLACK);
            };

            // 3. MASTER DIMENSIONS (Calculated Once)
            Font sizingFont = (stickyFont != null) ? stickyFont : new Font("SansSerif", Font.BOLD, 12);

            // DELEGATE SIZING TO RAILCARD STATIC CALCULATOR
            // compactMode = false (Standard Share Width)
            Dimension refDim = RailCard.calculateBaseSize(sizingFont, false, 1.0);

            int masterHeight = refDim.height;
            int wCard = refDim.width;

            // Accessories (Prices/Dots) still need a calculated width relative to font
            FontMetrics fm = java.awt.Toolkit.getDefaultToolkit().getFontMetrics(sizingFont);
            int charWidth = fm.charWidth('0');
            // int wPrice = (charWidth * 3) + 6;
            // Adjusted to fit "$123" (Currency symbol + 3 digits)
            int wPrice = fm.stringWidth("$999") + 10;

            int wDot = Math.max(8, masterHeight / 2);

            Dimension dimCard = new Dimension(wCard, masterHeight);
            Dimension dimPrice = new Dimension(wPrice, masterHeight);
            Dimension dimDot = new Dimension(wDot, masterHeight);

            // 4. PLAYER COLUMNS
            for (int j = 0; j < np; j++) {
                playerShareCards[i][j] = new RailCard((net.sf.rails.game.Train) null, buySellGroup);
                playerShareCards[i][j].setCompany(c); // Tell the card it belongs to Company 'c'
                playerShareCards[i][j].addActionListener(this);
                HexHighlightMouseListener.addMouseListener(playerShareCards[i][j], gameUIManager.getORUIManager(), c,
                        false);
                if (stickyFont != null)
                    playerShareCards[i][j].setFont(stickyFont);

                JComponent accessory = null;
                if (c.hasStockPrice()) {
                    playerSoldDots[i][j] = new javax.swing.JLabel() {
                        @Override
                        public void setFont(Font f) {
                            super.setFont(f);
                            if (f != null) {
                                FontMetrics m = getFontMetrics(f);
                                int s = Math.max(4, m.getAscent() / 2);
                                setIcon(new SimpleDotIcon(s));
                            }
                        }
                    };
                    Font dotFont = (stickyFont != null) ? stickyFont : new Font("SansSerif", Font.BOLD, 12);
                    playerSoldDots[i][j].setFont(dotFont);
                    playerSoldDots[i][j].setHorizontalAlignment(SwingConstants.CENTER);
                    playerSoldDots[i][j].setVerticalAlignment(SwingConstants.CENTER);
                    accessory = playerSoldDots[i][j];
                }

                // FAT SEPARATOR: Check if this is the last player (j == np - 1)
                // If yes, use width 4 (Fat Line), otherwise 2 (Standard) or 1.
                int rightBorderWidth = (j == np - 1) ? 4 : 2;
                javax.swing.border.Border bPlayer = BorderFactory.createMatteBorder(tHeight, 0, bHeight,
                        rightBorderWidth, Color.BLACK);

                playerSharePanels[i][j] = createShareCell(
                        playerShareCards[i][j], accessory, dimCard, dimDot, bPlayer);

                int wideGapPosition = ((j == 0) ? WIDE_LEFT : 0) + ((j == np - 1) ? WIDE_RIGHT : 0);
                addField(playerSharePanels[i][j], certPerPlayerXOffset + j, y, 1, 1, wideGapPosition, visible);
            }

            // 5. POOL COLUMN
            poolShareCards[i] = new RailCard((net.sf.rails.game.Train) null, buySellGroup);
            poolShareCards[i].addActionListener(this);
            poolShareCards[i].setCompany(c); // Ensure metadata is present for UI Search
            HexHighlightMouseListener.addMouseListener(poolShareCards[i], gameUIManager.getORUIManager(), c, false);
            if (stickyFont != null)
                poolShareCards[i].setFont(stickyFont);

            poolPriceLabels[i] = new Caption("");
            Font baseFont = (stickyFont != null) ? stickyFont : poolPriceLabels[i].getFont();
            poolPriceLabels[i].setFont(baseFont.deriveFont(Font.BOLD));
            poolPriceLabels[i].setForeground(new Color(0, 0, 128));
            poolPriceLabels[i].setHorizontalAlignment(SwingConstants.RIGHT);
            // TRANSPARENCY: Force labels to be transparent so they inherit the row color
            poolPriceLabels[i].setOpaque(false);
            poolPanels[i] = createShareCell(
                    poolShareCards[i], poolPriceLabels[i], dimCard, dimPrice, getBorder.apply(false, false));
            poolPanels[i].setOpaque(true);

            addField(poolPanels[i], certInPoolXOffset, y, 1, 1, 0, visible);

            // 6. IPO COLUMN
            ipoShareCards[i] = new RailCard((net.sf.rails.game.Train) null, buySellGroup);
            ipoShareCards[i].addActionListener(this);
            ipoShareCards[i].setCompany(c); // Ensure metadata is present for UI Search
            HexHighlightMouseListener.addMouseListener(ipoShareCards[i], gameUIManager.getORUIManager(), c, false);
            if (stickyFont != null)
                ipoShareCards[i].setFont(stickyFont);

            ipoParLabels[i] = new Caption("");
            ipoParLabels[i].setFont(baseFont.deriveFont(Font.BOLD));
            ipoParLabels[i].setForeground(Color.BLACK);
            ipoParLabels[i].setHorizontalAlignment(SwingConstants.RIGHT);
            ipoParLabels[i].setOpaque(false);
            ipoPanels[i] = createShareCell(
                    ipoShareCards[i], ipoParLabels[i], dimCard, dimPrice, getBorder.apply(false, !hasOSI));

            ipoPanels[i].setOpaque(true);
            addField(ipoPanels[i], certInIPOXOffset, y, 1, 1, 0, visible);

            if (compCanHoldOwnShares) {
                // Create Treasury RailCard
                treasuryShareCards[i] = new RailCard((net.sf.rails.game.Train) null, buySellGroup);
                treasuryShareCards[i].addActionListener(this);
                treasuryShareCards[i].setCompany(c); // Ensure metadata is present for UI Search
                HexHighlightMouseListener.addMouseListener(treasuryShareCards[i], gameUIManager.getORUIManager(), c,
                        false);
                if (stickyFont != null)
                    treasuryShareCards[i].setFont(stickyFont);

                // Create wrapper panel (using null for accessory since Treasury usually doesn't
                // show price/par next to card)
                treasuryPanels[i] = createShareCell(
                        treasuryShareCards[i], null, dimCard, null, getBorder.apply(true, false));
                treasuryPanels[i]
                        .setBackground(isOperating ? BG_OPERATING
                                : (isMinor || !isActive ? BG_INACTIVE : BG_SPOTLIGHT_INACTIVE));
                treasuryPanels[i].setOpaque(true);

                addField(treasuryPanels[i], certInTreasuryXOffset, y, 1, 1, 0, visible);

            }

            if (hasOSI) {
                osiShareCards[i] = new RailCard((net.sf.rails.game.Train) null, buySellGroup);
                osiShareCards[i].addActionListener(this);
                osiShareCards[i].setCompany(c);
                HexHighlightMouseListener.addMouseListener(osiShareCards[i], gameUIManager.getORUIManager(), c, false);
                if (stickyFont != null)
                    osiShareCards[i].setFont(stickyFont);

                osiLabels[i] = new Caption("");
                osiLabels[i].setFont(baseFont.deriveFont(Font.BOLD));
                osiLabels[i].setForeground(Color.RED);
                osiLabels[i].setHorizontalAlignment(SwingConstants.RIGHT);
                osiLabels[i].setOpaque(false);

                osiPanels[i] = createShareCell(
                        osiShareCards[i], null, dimCard, null, getBorder.apply(false, true));
                osiPanels[i].setBackground(
                        isOperating ? BG_OPERATING : (isMinor || !isActive ? BG_INACTIVE : BG_SPOTLIGHT_INACTIVE));
                osiPanels[i].setOpaque(true);

                addField(osiPanels[i], certInOSIXOffset, y, 1, 1, 0, visible);
            }

            // DETAILS (Cash, Rev, Trains, Tokens)

            f = compCash[i] = new Field(c.getPurseMoneyModel()) {
                @Override
                public void setText(String t) {
                    boolean isSR = gameUIManager.getGameManager()
                            .getCurrentRound() instanceof net.sf.rails.game.financial.StockRound;
                    boolean isEffectivelyActive = c.hasFloated()
                            || (c.getPresidentsShare() != null && c.getPresidentsShare().getOwner() instanceof Player);
                    if (!isSR && !isEffectivelyActive) {
                        super.setText("");
                        return;
                    }
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
            f.setBorder(BorderFactory.createCompoundBorder(bDet, BorderFactory.createEmptyBorder(0, 0, 0, 5)));
            ((JLabel) f).setHorizontalAlignment(SwingConstants.RIGHT);

            f.setPreferredSize(new Dimension(60, 25));
            addField(f, compCashXOffset, y, 1, 1, 0, visible);

            f = compCashButton[i] = new ClickField(compCash[i].getText(), CASH_CORRECT_CMD, "", this, buySellGroup);
            ((ClickField) f).setHorizontalAlignment(SwingConstants.RIGHT);
            ((ClickField) f)
                    .setBorder(BorderFactory.createCompoundBorder(bDet, BorderFactory.createEmptyBorder(0, 0, 0, 5)));
            addField(f, compCashXOffset, y, 1, 1, 0, false);

            // 1. TRAINS PANEL (Now 2nd column)
            compTrainsButtonPanel[i] = new JPanel(new GridBagLayout());
            compTrainsButtonPanel[i].setBorder(bDet);
            compTrainsButtonPanel[i].setBackground(isOperating ? BG_OPERATING : (!isActive ? BG_INACTIVE : BG_MAUVE));
            compTrainsButtonPanel[i].setOpaque(true);

            GridBagConstraints gbcT = new GridBagConstraints();
            gbcT.gridy = 0;
            gbcT.anchor = GridBagConstraints.CENTER;

            compSubTrainButtons[i] = new RailCard[MAX_TRAIN_SLOTS];
            for (int t = 0; t < MAX_TRAIN_SLOTS; t++) {
                RailCard cf = createTrainButton();
                compSubTrainButtons[i][t] = cf;
                gbcT.insets = new Insets(0, 0, 0, (t == MAX_TRAIN_SLOTS - 1) ? 0 : 3);
                compTrainsButtonPanel[i].add(cf, gbcT);
            }
            addField(compTrainsButtonPanel[i], compTrainsXOffset, y, 1, 1, 0, visible);

            // 2. DIVIDEND FIELD
            f = compRevenue[i] = new Field(c.getLastRevenueModel()) {
                @Override
                public void setText(String t) {
                    boolean isSR = isMarketOrAuctionRound(gameUIManager.getGameManager().getCurrentRound());
                    boolean isEffectivelyActive = c.hasFloated()
                            || (c.getPresidentsShare() != null && c.getPresidentsShare().getOwner() instanceof Player);
                    if (!isSR && !isEffectivelyActive) {
                        super.setText("");
                        return;
                    }
                    if (c.getLastRevenue() == 0 && c.getLastDirectIncome() == 0) {
                        super.setText("");
                        return;
                    }
                    super.setText(gameUIManager.format(c.getDividendRevenue()));
                }
            };

            f.setBackground(isOperating ? BG_OPERATING : (!isActive ? BG_INACTIVE : BG_MAUVE));
            f.setOpaque(true);
            f.setBorder(BorderFactory.createCompoundBorder(bDet, BorderFactory.createEmptyBorder(0, 0, 0, 5)));
            ((JLabel) f).setHorizontalAlignment(SwingConstants.RIGHT);
            if (f.getFont() != null)
                f.setFont(f.getFont().deriveFont(Font.BOLD));
            f.setPreferredSize(new Dimension(60, 25));
            addField(f, compRevenueXOffset, y, 1, 1, 0, visible);

            // 3. RETAINED FIELD
            f = compRetained[i] = new Field(c.getLastRevenueModel()) {
                @Override
                public void setText(String t) {
                    boolean isSR = isMarketOrAuctionRound(gameUIManager.getGameManager().getCurrentRound());
                    boolean isEffectivelyActive = c.hasFloated()
                            || (c.getPresidentsShare() != null && c.getPresidentsShare().getOwner() instanceof Player);
                    if (!isSR && !isEffectivelyActive) {
                        super.setText("");
                        return;
                    }
                    if (c.getLastRevenue() == 0 && c.getLastDirectIncome() == 0) {
                        super.setText("");
                        return;
                    }
                    super.setText(gameUIManager.format(c.getRetainedRevenue()));
                }
            };

            f.setBackground(isOperating ? BG_OPERATING : (!isActive ? BG_INACTIVE : BG_MAUVE));
            f.setOpaque(true);
            f.setBorder(BorderFactory.createCompoundBorder(bDet, BorderFactory.createEmptyBorder(0, 0, 0, 5)));
            ((JLabel) f).setHorizontalAlignment(SwingConstants.RIGHT);
            if (f.getFont() != null)
                f.setFont(f.getFont().deriveFont(Font.BOLD));
            f.setPreferredSize(new Dimension(60, 25));

            // Restore Coal Mine tooltip logic for Retained column
            net.sf.rails.game.PrivateCompany coalMine = null;
            if (gameUIManager != null && gameUIManager.getRoot() != null) {
                for (net.sf.rails.game.PrivateCompany pc : gameUIManager.getRoot().getCompanyManager()
                        .getAllPrivateCompanies()) {
                    if (pc.getId().equals(c.getId())) {
                        coalMine = pc;
                        break;
                    }
                }
            }
            if (coalMine != null) {
                String info = coalMine.getInfoText();
                if (info != null) {
                    if (!info.toLowerCase().startsWith("<html>"))
                        info = "<html>" + info + "</html>";
                    f.setToolTipText(info);
                }
            }
            addField(f, compRetainedXOffset, y, 1, 1, 0, visible);

            // 4. MARKERS/TOKENS PANEL (Now 5th column)
            compTokens[i] = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
            compTokens[i].setOpaque(true);
            compTokens[i].setBackground(isOperating ? BG_OPERATING : (!isActive ? BG_INACTIVE : BG_MAUVE));
            compTokens[i].setBorder(bDet);
            compTokens[i].setPreferredSize(DIM_TOKENS);
            updateCompanyTokenDisplay(i, c, compTokens[i]);
            addField(compTokens[i], compTokensXOffset, y, 1, 1, 0, visible);

            if (compCanBuyPrivates) {
                // Use FlowLayout to stack multiple privates horizontally if necessary
                compPrivatesPanel[i] = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
                compPrivatesPanel[i].setOpaque(true);
                compPrivatesPanel[i].setBackground(isOperating ? BG_OPERATING : (!isActive ? BG_INACTIVE : BG_MAUVE));
                compPrivatesPanel[i].setBorder(bDet);
                // No fixed preference size; let it grow with content, but set minimum
                compPrivatesPanel[i].setMinimumSize(DIM_STD);

                addField(compPrivatesPanel[i], compPrivatesXOffset, y, 1, 1, 0, visible);
            }

            if (hasCompanyLoans && compLoans != null && i < compLoans.length) {
                // ClickField requires a ButtonGroup; passing null causes the NPE.
                // We use buySellGroup to maintain UI focus consistency.
                ClickField cf = new ClickField("", "takeLoan_" + c.getId(), "Take Loans", this, buySellGroup);
                compLoans[i] = cf;
                cf.setBackground(isOperating ? BG_OPERATING : (!isActive ? BG_INACTIVE : BG_MAUVE));
                cf.setOpaque(true);
                cf.setBorder(bDet);
                cf.setPreferredSize(DIM_STD);
                cf.setHorizontalAlignment(SwingConstants.CENTER);
                addField(cf, compLoansXOffset, y, 1, 1, 0, visible);
            }

            y++;
        }

    }

    private void initPlayerFooters() {
        // Cash
        f = new Caption(LocalText.getText("CASH"));
        f.setBorder(BORDER_THIN);
        f.setBackground(BG_HEADER);
        f.setOpaque(true);
        addField(f, compNameCol, playerCashYOffset, 1, 1, 0, true);
        for (int i = 0; i < np; i++) {

            final int playerIndex = i;
            f = playerCash[i] = new Field(players.getPlayerByPosition(i).getWallet()) {
                @Override
                public void setText(String t) {
                    boolean isDebt = false;
                    try {
                        if (gameUIManager != null && gameUIManager.getGameManager() != null) {
                            net.sf.rails.game.round.RoundFacade currentRound = gameUIManager.getGameManager()
                                    .getCurrentRound();
                            if (currentRound instanceof net.sf.rails.game.financial.ShareSellingRound) {
                                net.sf.rails.game.financial.ShareSellingRound ssr = (net.sf.rails.game.financial.ShareSellingRound) currentRound;
                                Player thisPlayer = players.getPlayerByPosition(playerIndex);

                                // Fix: Use getCurrentPlayer() instead of getSellingPlayer()
                                if (ssr.getCurrentPlayer() != null && ssr.getCurrentPlayer().equals(thisPlayer)) {
                                    int toRaise = ssr.getRemainingCashToRaise();
                                    if (toRaise > 0) {
                                        this.setForeground(Color.RED);
                                        // Display debt as negative cash for UX
                                        super.setText(gameUIManager.format(-toRaise));
                                        isDebt = true;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Fail gracefully and revert to standard display
                    }

                    if (!isDebt) {
                        this.setForeground(Color.BLACK);
                        super.setText(t);
                    }
                }
            };

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
        f.setBackground(BG_HEADER);
        f.setOpaque(true);
        addField(f, compNameCol, playerCertCountYOffset, 1, 1, 0, true);

        // Initialize the new Gauge Components
        playerCertCount = new CertLimitGauge[np];

        for (int i = 0; i < np; i++) {
            playerCertCount[i] = new CertLimitGauge();
            playerCertCount[i].setBorder(BORDER_THIN);
            playerCertCount[i].setPreferredSize(DIM_PLAYER);

            gbc.weightx = 1.0;
            addField(playerCertCount[i], certPerPlayerXOffset + i, playerCertCountYOffset, 1, 1, 0, true);
            gbc.weightx = 0.0;
        }

        // Privates
        f = new Caption(LocalText.getText("PRIVATES"));
        f.setBorder(BORDER_THIN);
        f.setBackground(BG_HEADER);
        f.setOpaque(true);
        addField(f, compNameCol, playerPrivatesYOffset, 1, 2, 0, true);
        playerPrivatesPanel = new JPanel[np];
        for (int i = 0; i < np; i++) {
            if (playerPrivatesPanel[i] == null) {
                playerPrivatesPanel[i] = new JPanel();
            }
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

        // 1. Calculate Identical Dimensions (Responsive)
        // We replicate the logic from initCompanyRows to ensure exact matching
        Font sizingFont = (stickyFont != null) ? stickyFont : new Font("SansSerif", Font.BOLD, 12);

        // Ask RailCard for the standard height
        Dimension refDim = RailCard.calculateBaseSize(sizingFont, false, 1.0);
        int masterHeight = refDim.height;

        // 4. Train Market Alignment
        int colUsed = certInPoolXOffset; // "Used" -> Pool
        int colCurr = certInIPOXOffset; // "Current" -> IPO
        int colFut = colCurr + 1;

        // Span the rest of the grid
        int spanFut = (rightCompCaptionXOffset - colFut);
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

        // 1. POOL (USED) - RESTRICT TO 1 SLOT
        // We revert to FlowLayout (Center) but strictly limit the loop to 1.
        poolTrainsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 0));
        // Add Padding (EmptyBorder) inside the Line Border (B_BOT_L)
        poolTrainsPanel.setBorder(BorderFactory.createCompoundBorder(
                B_BOT_L,
                BorderFactory.createEmptyBorder(5, 0, 0, 0) // 5px Top Padding
        ));

        poolTrainsPanel.setBackground(BG_TRAINS);
        poolTrainsPanel.setOpaque(true);

        poolTrainButtons = new RailCard[MAX_POOL_SLOTS];
        poolTrainInfoLabels = new javax.swing.JLabel[MAX_POOL_SLOTS];

        for (int i = 0; i < MAX_POOL_SLOTS; i++) {
            // Create a Slot Panel (Vertical Box) to hold Button + Label
            JPanel slot = new JPanel(new BorderLayout());
            slot.setOpaque(false);

            poolTrainButtons[i] = createTrainButton();
            slot.add(poolTrainButtons[i], BorderLayout.NORTH);

            poolTrainInfoLabels[i] = new javax.swing.JLabel("", javax.swing.SwingConstants.CENTER);
            poolTrainInfoLabels[i].setFont(new Font("SansSerif", Font.PLAIN, 10));
            slot.add(poolTrainInfoLabels[i], BorderLayout.CENTER);

            if (i < MAX_POOL_SLOTS)
                poolTrainsPanel.add(slot);
        }

        addField(poolTrainsPanel, colUsed, trainY_Data, 1, 1, 0, true);

        // 2. IPO (CURRENT)

        // Refactor to handle multiple slots (MAX_IPO_SLOTS)
        newTrainsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 0));
        newTrainsPanel.setBorder(BorderFactory.createCompoundBorder(
                B_BOT_M,
                BorderFactory.createEmptyBorder(5, 0, 0, 0)));
        newTrainsPanel.setBackground(BG_TRAINS);
        newTrainsPanel.setOpaque(true);

        for (int i = 0; i < MAX_IPO_SLOTS; i++) {
            // Create a wrapper slot identical to Future/Pool
            JPanel ipoSlot = new JPanel(new BorderLayout());
            ipoSlot.setOpaque(false);

            newTrainButtons[i] = createTrainButton();
            ipoSlot.add(newTrainButtons[i], BorderLayout.NORTH);

            newTrainQtyLabels[i] = new javax.swing.JLabel(" ");
            newTrainQtyLabels[i].setFont(new Font("SansSerif", Font.PLAIN, 10));
            newTrainQtyLabels[i].setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
            newTrainQtyLabels[i].setForeground(Color.BLACK);

            ipoSlot.add(newTrainQtyLabels[i], BorderLayout.CENTER);
            newTrainsPanel.add(ipoSlot);
        }

        addField(newTrainsPanel, colCurr, trainY_Data, 1, 1, 0, true);

        futureTrainsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 0));
        futureTrainsPanel.setBorder(BorderFactory.createCompoundBorder(
                B_BOT_R,
                BorderFactory.createEmptyBorder(5, 0, 0, 0)));
        futureTrainsPanel.setBackground(BG_TRAINS);
        futureTrainsPanel.setOpaque(true);

        futureTrainButtons = new RailCard[MAX_FUTURE_SLOTS];
        futureTrainInfoLabels = new javax.swing.JLabel[MAX_FUTURE_SLOTS];
        for (int i = 0; i < MAX_FUTURE_SLOTS; i++) {
            JPanel slot = new JPanel(new BorderLayout());
            slot.setOpaque(false);

            futureTrainButtons[i] = createTrainButton();
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
        int bankX = certInIPOXOffset; // Matches colUsed

        boolean is1817 = "1817".equals(gameUIManager.getGameManager().getGameName());
        f = new Caption(is1817 ? "Pur. Power" : "Fixed Inc");
        f.setBorder(BORDER_THIN);
        f.setBackground(BG_HEADER);
        f.setOpaque(true);
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

        if (is1817) {

            interestRateField = new Field("Interest: ?");
            interestRateField.setBorder(BORDER_BOX);
            interestRateField.setBackground(BG_BANK);
            interestRateField.setOpaque(true);
            interestRateField.setFont(new Font("SansSerif", Font.BOLD, 12));
            interestRateField.setHorizontalAlignment(SwingConstants.CENTER);
            addField(interestRateField, bankX - 1, bankY - 4, 1, 1, 0, true);

            bondsHeatbarPanel = new net.sf.rails.ui.swing.elements.BondsHeatbarPanel();
            int span = rightCompCaptionXOffset - bankX;
            if (span < 1)
                span = 1;

            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.BOTH;
            addField(bondsHeatbarPanel, bankX, bankY - 4, span, 1, 0, true);
            gbc.weightx = 0.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
        } else {
            f = new Caption("Bank Cash");
            f.setBorder(BORDER_BOX);
            f.setBackground(BG_BANK);
            f.setOpaque(true);
            f.setFont(new Font("SansSerif", Font.BOLD, 12));
            addField(f, bankX - 1, bankY - 4, 1, 1, 0, true);

            bankCash = new Field(bank.getPurse());
            bankCash.setBorder(BORDER_BOX);
            bankCash.setBackground(BG_BANK);
            bankCash.setOpaque(true);
            Font bankFont = bankCash.getFont();
            bankCash.setFont(bankFont.deriveFont(Font.BOLD, bankFont.getSize()));
            addField(bankCash, bankX, bankY - 4, 1, 1, 0, true);
        }

        f = new Caption("Time");
        f.setBackground(BG_HEADER);
        f.setOpaque(true);
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

    /**
     * A compact token icon that scales to the font size.
     * Replaces the fixed-size TokenIcon to reduce row height.
     */
    private static class SmallTokenIcon implements Icon {
        private final PublicCompany company;
        private final String label;
        private final int size;

        public SmallTokenIcon(PublicCompany company, String label, int size) {
            this.company = company;
            this.label = label;
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw Token Background (Circle)
            g2.setColor(company.getBgColour());
            g2.fillOval(x, y, size, size);

            // Draw Border (Optional, for contrast)
            g2.setColor(Color.BLACK);
            g2.drawOval(x, y, size, size);

            // Draw Label
            if (label != null) {
                g2.setColor(company.getFgColour());
                // Scale font to 70% of icon size to fit inside the circle
                g2.setFont(c.getFont().deriveFont(Font.BOLD, size * 0.7f));

                FontMetrics fm = g2.getFontMetrics();
                int tx = x + (size - fm.stringWidth(label)) / 2;
                // Vertically center: y + (height/2) - (ascent/2) + ascent roughly centers it
                int ty = y + (size - fm.getAscent()) / 2 + fm.getAscent();

                g2.drawString(label, tx, ty);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    /**
     * A simple circular dot that uses the component's foreground color.
     * Automatically scales with the font size (handled by the caller).
     */
    private static class SimpleDotIcon implements Icon {
        private final int size;

        public SimpleDotIcon(int size) {
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Use the component's foreground color (allows switching between RED and
            // Transparent)
            g2.setColor(c.getForeground());
            g2.fillOval(x, y, size, size);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    /**
     * UNIFIED CELL FACTORY
     * Creates a strictly formatted panel containing a Share Card [Left] and an
     * Accessory [Right].
     * Used for Players (Card + Dot), Pool (Card + Price), and IPO (Card + Par).
     */
    private JPanel createShareCell(RailCard card, JComponent accessory,
            Dimension dimCard, Dimension dimAccessory,
            javax.swing.border.Border border) {

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(border);
        panel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.weighty = 1.0;

        // We insert an invisible, rigid box into Grid X=0.
        // This forces the "Card Column" to always maintain its width,
        // even if the actual card component is hidden (setVisible(false)).
        gbc.gridx = 0;
        gbc.gridy = 0;

        panel.add(Box.createRigidArea(new Dimension(dimCard.width, dimCard.height)), gbc);
        // 1. The Share Card (Added to same cell X=0)
        if (card != null) {

            card.setCompactMode(true);

            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.BLACK, 1),
                    BorderFactory.createEmptyBorder(0, 0, 0, 0)));

            gbc.insets = new Insets(0, 0, 0, 2); // 2px Gap to right
            panel.add(card, gbc);
        }

        // 2. The Accessory (Price/Dot) (Grid X=1)
        if (accessory != null) {
            accessory.setPreferredSize(dimAccessory);
            accessory.setMinimumSize(dimAccessory);

            gbc.gridx = 1;
            gbc.insets = new Insets(0, 0, 0, 0);
            panel.add(accessory, gbc);
        }

        return panel;
    }

    /**
     * Direct accessor to retrieve a RailCard from the player grid.
     * Used by StatusWindow to force-highlight cards when recursive search fails.
     */
    public net.sf.rails.ui.swing.elements.RailCard getRailCardFor(int companyIndex, int playerIndex) {
        if (playerShareCards != null &&
                companyIndex >= 0 && companyIndex < playerShareCards.length &&
                playerIndex >= 0 && // Ensure valid player index
                playerShareCards[companyIndex] != null &&
                playerIndex < playerShareCards[companyIndex].length) {

            return playerShareCards[companyIndex][playerIndex];
        }
        return null;
    }

    /**
     * Custom Caption to render a Green "Pass" Dot behind the Priority Train icon.
     */
    private static class PassIndicatorCaption extends Caption {

        public PassIndicatorCaption(String text) {
            super(text);
        }

        public void setPassed(boolean passed) {
        }

    }

    protected void initGameSpecificActions() {

        if (possibleActions == null || possibleActions.getList() == null) {
            return;
        }

        for (PossibleAction pa : possibleActions.getList()) {
            // Remove the exclusion for DiscardTrain so we can style it red
            if (pa instanceof GuiTargetedAction && !(pa instanceof BuyTrain)) {
                GuiTargetedAction gta = (GuiTargetedAction) pa;
                Object target = gta.getTarget();

                if (target != null) {
                    // This utility scans the 'compSubTrainButtons' (the owned train railcards)
                    net.sf.rails.ui.swing.elements.RailCard card = findRailCardFor(target);

                    if (card != null) {
                        card.addPossibleAction(pa);

                        // If it's a Discard action or specifically targeting a Train for
                        // removal/selection,
                        // apply the BORDER_COL_SELL (Crimson Red) highlight.
                        if (pa instanceof DiscardTrain || target instanceof net.sf.rails.game.Train) {
                            card.setBackground(BG_CARD_PASSIVE);
                            card.setBorder(BorderFactory.createLineBorder(BORDER_COL_SELL, BORDER_THICKNESS));
                        } else {
                            // Standard highlight for other targeted actions
                            Color bg = gta.getHighlightBackgroundColor();
                            Color border = gta.getHighlightBorderColor();
                            if (bg != null)
                                card.setBackground(bg);
                            if (border != null)
                                card.setBorder(BorderFactory.createLineBorder(border, 3));
                        }

                        card.setEnabled(true);
                        card.setVisible(true);
                    }
                }
            }
        }
        // --- END FIX ---
    }

    private ClickField createSpecialActionButton(PossibleAction action) {
        String label = action.getButtonLabel();
        Color bgColor = Color.LIGHT_GRAY;
        Color borderColor = Color.GRAY;
        Color textColor = Color.BLACK;

        if (action instanceof GuiTargetedAction) {
            GuiTargetedAction gta = (GuiTargetedAction) action;
            label = gta.getButtonLabel();
            bgColor = gta.getHighlightBackgroundColor();
            borderColor = gta.getHighlightBorderColor();
            textColor = gta.getHighlightTextColor();
        } else if (action instanceof NullAction) {
            label = ((NullAction) action).getMode() == NullAction.Mode.PASS ? "Decline" : "Done";
            bgColor = new Color(30, 144, 255); // UITheme.ACTION_SKIP
            borderColor = bgColor.darker();
            textColor = Color.WHITE;
        }

        if (label != null && label.length() > 15 && !label.toLowerCase().startsWith("<html>")) {
            label = label.substring(0, 15) + "...";
        }

        ClickField btn = new ClickField(label, "SpecialAction", "", this, buySellGroup);

        if (label != null && !label.toLowerCase().startsWith("<html>")) {
            btn.setText("<html><center>" + label + "</center></html>");
        }

        btn.setPossibleAction(action);
        btn.setEnabled(true);

        btn.setBackground(bgColor);
        btn.setForeground(textColor);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 2),
                BorderFactory.createEmptyBorder(2, 5, 2, 5)));

        btn.setOpaque(true);
        if (stickyFont != null) {
            btn.setFont(stickyFont.deriveFont(Font.BOLD, 10f));
        } else {
            btn.setFont(new Font("SansSerif", Font.BOLD, 10));
        }

        return btn;
    }

    /**
     * Central Registry: Locates the RailCard UI component for any game object.
     * Scans: Player Shares, IPO, Pool, Treasury, Trains, and Privates.
     */
    private net.sf.rails.ui.swing.elements.RailCard findRailCardFor(Object target) {
        if (target == null)
            return null;

        // A. If Target is a TRAIN
        if (target instanceof net.sf.rails.game.Train) {
            net.sf.rails.game.Train targetTrain = (net.sf.rails.game.Train) target;

            // 1. Scan Company Trains
            if (compSubTrainButtons != null) {
                for (int i = 0; i < nc; i++) {
                    for (int t = 0; t < MAX_TRAIN_SLOTS; t++) {
                        RailCard card = compSubTrainButtons[i][t];
                        if (card != null && card.getTrain() == targetTrain)
                            return card;
                    }
                }
            }
            // 3. Scan IPO Trains
            if (newTrainButtons != null) {
                for (RailCard card : newTrainButtons) {
                    if (card != null && card.getTrain() != null) {
                        // Compare type names, as actions often target the generic type in the IPO
                        if (card.getTrain().getType().getName().equals(targetTrain.getType().getName())) {
                            return card;
                        }
                    }
                }
            }
        }

        // B. If Target is a COMPANY (Public or Private) - usually for Mergers/Exchange
        if (target instanceof net.sf.rails.game.Company) {
            net.sf.rails.game.Company targetComp = (net.sf.rails.game.Company) target;

            // Identify the true owner to target the correct player column
            Player trueOwner = null;
            if (targetComp instanceof net.sf.rails.game.PublicCompany) {
                trueOwner = ((net.sf.rails.game.PublicCompany) targetComp).getPresident();
            } else if (targetComp instanceof net.sf.rails.game.PrivateCompany) {
                net.sf.rails.game.state.Owner o = ((net.sf.rails.game.PrivateCompany) targetComp).getOwner();
                if (o instanceof Player) {
                    trueOwner = (Player) o;
                }
            }

            // 1. Scan Player Shares (The most common target for "Exchange Share")
            if (playerShareCards != null) {
                for (int i = 0; i < nc; i++) {
                    for (int j = 0; j < np; j++) {
                        RailCard card = playerShareCards[i][j];
                        if (card != null && card.getCompany() == targetComp) {
                            if (trueOwner != null) {
                                if (players.getPlayerByPosition(j) == trueOwner)
                                    return card;
                            } else {
                                return card; // Fallback for bank/pool
                            }
                        }
                    }
                }
            }

            // 2. Scan Player Privates
            if (playerPrivatesPanel != null) {
                for (int j = 0; j < np; j++) {
                    if (playerPrivatesPanel[j] != null) {
                        for (Component c : playerPrivatesPanel[j].getComponents()) {
                            if (c instanceof RailCard) {
                                RailCard card = (RailCard) c;
                                if (card.getCompany() == targetComp) {
                                    if (trueOwner != null) {
                                        if (players.getPlayerByPosition(j) == trueOwner)
                                            return card;
                                    } else {
                                        return card;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Scan Company Privates
            if (compPrivatesPanel != null) {
                for (int i = 0; i < nc; i++) {
                    if (compPrivatesPanel[i] != null) {
                        for (Component c : compPrivatesPanel[i].getComponents()) {
                            if (c instanceof RailCard) {
                                RailCard card = (RailCard) c;
                                if (card.getCompany() == targetComp) {
                                    log.info("DEBUG-FRC: Found Match in compPrivatesPanel[{}]", i);
                                    return card;
                                }
                            }
                        }
                    }
                }
            }
            // --- END FIX ---
        }

        return null;
    }

}