package net.sf.rails.ui.swing;

import com.google.common.collect.Lists;
import net.sf.rails.algorithms.*;
import net.sf.rails.common.Config;
import net.sf.rails.common.GuiDef;
import net.sf.rails.common.LocalText;
import net.sf.rails.game.*;
import net.sf.rails.game.round.RoundFacade;
import net.sf.rails.game.state.IntegerState;
import net.sf.rails.ui.swing.elements.*;
import net.sf.rails.ui.swing.hexmap.GUIHex;
import net.sf.rails.ui.swing.hexmap.HexHighlightMouseListener;
import net.sf.rails.ui.swing.hexmap.HexMap;
import net.sf.rails.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rails.game.action.*;
import rails.game.correct.CorrectionModeAction;
import rails.game.correct.OperatingCost;
import javax.swing.border.BevelBorder;
import javax.swing.*;
import javax.swing.border.TitledBorder; // Add
import rails.game.specific._1835.StartPrussian;
import rails.game.specific._1835.ExchangeForPrussianShare; // Add this!
import javax.swing.border.Border;
import java.util.Collection;
import java.util.Collections;

import net.sf.rails.ui.swing.StatusWindow;

// Add
// Add
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ORPanel extends GridPanel
        implements ActionListener, RevenueListener {

    private static final long serialVersionUID = 1L;

    public static final String OPERATING_COST_CMD = "OperatingCost";
    public static final String BUY_PRIVATE_CMD = "BuyPrivate";

    public static final String UNDO_CMD = "Undo";
    public static final String REDO_CMD = "Redo";
    public static final String REM_TILES_CMD = "RemainingTiles";
    public static final String NETWORK_INFO_CMD = "NetworkInfo";
    public static final String TAKE_LOANS_CMD = "TakeLoans";
    public static final String REPAY_LOANS_CMD = "RepayLoans";

    public static final String BUY_TRAIN_CMD = "BuyTrain";
    public static final String WITHHOLD_CMD = "Withhold";
    public static final String SPLIT_CMD = "Split";
    public static final String PAYOUT_CMD = "Payout";
    public static final String SET_REVENUE_CMD = "SetRevenue";
    public static final String DONE_CMD = "Done";
    public static final String CONFIRM_CMD = "Confirm";
    public static final String SKIP_CMD = "Skip";

    // Sidebar Components ---
    private JPanel sidebarPanel;
    private JPanel phase1Panel, phase2Panel, phase3Panel, phase4Panel, footerPanel;
    private ActionButton btnRevPayout, btnRevWithhold, btnRevSplit;
    public ActionButton btnDone;
    // No buttons, just logic storage for Hotkeys
    private GameAction currentUndoAction;
    private GameAction currentRedoAction;

    public ActionButton currentDefaultButton; // For Enter key
    public int activePhase = 0; // 1=Build, 2=Token, 3=Revenue, 4=Train
    private Phase lastGamePhase = null;

    public JPanel trainButtonsPanel;
    public List<GUIHex> cycleableHexes = new ArrayList<>();
    public int cycleIndex = -1;

    private ORWindow orWindow;
    private ORUIManager orUIManager;

    private JPanel dynamicButtonPanel;

    private JMenu trainsInfoMenu;
    private JMenu phasesInfoMenu;
    private JMenu networkInfoMenu;
    private JMenu specialMenu;
    private ActionMenuItem takeLoans;
    private ActionMenuItem repayLoans;

    // --- VISUAL CONSTANTS ---
    private static final Color BG_DETAILS = new Color(235, 230, 255); // Standard Mauve
    // SYSTEM BLUE (Standard Confirm/Done)
    private static final Color SYS_BLUE = new Color(30, 144, 255); // DodgerBlue

// PHASE 1: BUILD TRACK (Construction Brown - Distinct from Sell Red)
    private static final Color PH_TILE_DARK = new Color(139, 69, 19); // SaddleBrown
    private static final Color PH_TILE_LIGHT = new Color(255, 245, 235); // Pale Sand


    // PHASE 2: TOKEN (Forest Green)
    private static final Color PH_TOKEN_DARK = new Color(34, 139, 34);
    private static final Color PH_TOKEN_LIGHT = new Color(210, 255, 210);

    // PHASE 3: REVENUE (Strong Blue)
    private static final Color PH_REV_DARK = new Color(0, 60, 140);
    private static final Color PH_REV_LIGHT = new Color(210, 230, 255);

    // PHASE 4: TRAIN (Burnt Orange)
    private static final Color PH_TRAIN_DARK = new Color(204, 102, 0);
    private static final Color PH_TRAIN_LIGHT = new Color(255, 235, 205);

    // DONE / PASSIVE
    private static final Color PH_DONE_BG = UIManager.getColor("Panel.background");
    private static final Color PH_DONE = PH_DONE_BG;

    // --- 2. UPDATE STYLE METHOD ---
    private void styleButton(ActionButton btn, Color bg, String text) {
        if (btn == null)
            return;

        btn.setText(text);
        btn.setBackground(bg);
        btn.setOpaque(true);

        // LOGIC: Always White text for Active Phases
        if (bg == PH_DONE) {
            btn.setForeground(Color.BLACK);
        } else {
            btn.setForeground(Color.WHITE);
            btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        }

        // LOGIC: 3D Button Look (Raised Bevel)
        btn.setBorder(BorderFactory.createRaisedBevelBorder());
    }

    // --- 3. UPDATE RESET METHOD ---
    private void resetButtonStyle(ActionButton btn) {
        if (btn == null)
            return;
        btn.setBackground(UIManager.getColor("Button.background"));
        btn.setForeground(Color.BLACK); // Reset text color
        btn.setFont(new Font("SansSerif", Font.PLAIN, 11)); // Reset font
        btn.setBorder(UIManager.getBorder("Button.border")); // Reset 3D border
    }

    private static final Color CARD_BG = new Color(255, 255, 240); // Beige for Cards
    private static final Color BG_HIGHLIGHT = new Color(255, 255, 200);
    private static final Color BG_NORMAL = UIManager.getColor("Panel.background");
    private static final Color FG_READOUT = Color.BLACK;
    private static final Color BG_READOUT = Color.WHITE; // High contrast for data

    private static final Font FONT_READOUT = new Font("SansSerif", Font.BOLD, 18);
    private static final Font FONT_HEADER = new Font("SansSerif", Font.BOLD, 12);

    /**
     * Retrieves the color used by the StatusWindow's train box.
     * This ensures the Sidebar and the Status Window match perfectly.
     */
    private Color getTrainHighlightColor() {
        // We use the standard Orange defined in GuiDef, which GameStatus also uses
        return Color.ORANGE;
    }

    // --- COMPONENTS ---
    private JLabel lblCash;
    private JLabel lblRevenue;
    private TokenDisplayPanel tokenDisplay; // Replaces lblTokens

    private JPanel miscActionPanel; // Container for special property buttons inside Phase 1
    private TrainDisplayPanel trainDisplay; // ADDED
    private boolean privatesCanBeBought;
    private boolean hasCompanyLoans;
    private boolean specialMode = false;
    public static final String SHOW_CMD = "Show";
    public static final String TRAIN_SKIP_CMD = "TrainSkip";

    public ActionButton btnBuildShow;
    public ActionButton btnTrainSkip;
    public ActionButton btnTileSkip, btnTileConfirm;
    public ActionButton btnTokenSkip, btnTokenConfirm;
    private boolean hasDirectCompanyIncomeInOR; // Muss wahrscheinlich auch wiederhergestellt werden, da die Logik sie
                                                // nutzt
    private boolean bonusTokensExist;
    private boolean hasRights;
    // Die Variable 'hasDirectCompanyIncomeInOR' wurde von Ihnen im letzten Schritt
    // doppelt deklariert.
    // Wir lassen sie hier weg und verwenden die bereits existierende Deklaration.
    private boolean showNumbersActive = false; // Zustand für das neue Hotkey-Feature
    // In ORPanel.java (Klassenvariablen)
    public ActionButton buttonOC;
    public ActionButton button1;

    public void setSpecialMode(boolean enabled) {
        this.specialMode = enabled;
    }

    private ActionButton button2;
    private ActionButton button3;

    // Current state
    private int orCompIndex = -1;

    private int nc;
    private PublicCompany[] companies;

    private PublicCompany orComp = null;
    // Track the actual Operating Company to restore context after interruptions
    // (e.g. Discard)
    private PublicCompany currentOperatingComp = null;
    private boolean discardMode = false;

    private boolean isRevenueValueToBeSet = false;
    private RevenueAdapter revenueAdapter = null;
    private Thread revenueThread = null;

    private List<JFrame> openWindows = new ArrayList<>();

    protected static final Logger log = LoggerFactory.getLogger(ORPanel.class);
    private JPanel legendPanel;
    private List<BuyTrain> availableTrainActions = new ArrayList<>();
    private JPanel specialPanel;
    // Static registry to allow remote cleanup from Game Model via Reflection
    private static final List<ORPanel> activeInstances = new ArrayList<>();

    public static void forceGlobalCleanup() {
        SwingUtilities.invokeLater(() -> {
            for (ORPanel panel : activeInstances) {
                // RACE CONDITION FIX:
                // If the engine has already advanced to an Operating Round by the time this
                // delayed task runs, we must NOT wipe the UI.
                if (panel.orUIManager != null && panel.orUIManager.getGameUIManager() != null) {
                    RoundFacade current = panel.orUIManager.getGameUIManager().getCurrentRound();
                    if (current instanceof OperatingRound) {
                        continue;
                    }
                }

                panel.finish();
            }
        });
    }

    private JPanel specialContainer;

    // --- 1. Sizing & Styling Constants ---
    private static final int SIDEBAR_WIDTH = 170;
    private static final int SIDEBAR_HEIGHT = 700;
    private static final int BTN_HEIGHT = 28;
    private static final Font BTN_FONT = new Font("SansSerif", Font.PLAIN, 11);
    private static final Font HEADER_FONT = new Font("SansSerif", Font.BOLD, 11);

    // Colors
    // User requested "Green behind done must go". Setting to INACTIVE_BG (Standard
    // Gray).
    private JLabel companyLogo;
    private JLabel companyCashLabel;
    private JLabel tokenCountLabel;
    private JLabel revenuePreviewLabel;
    private JLabel trainListLabel;
    private static final Color ACTIVE_BG = new Color(180, 220, 255); // Clear Blue
    private static final Color INFO_BG = Color.WHITE; // White for data fields
    private static final Color INACTIVE_BG = UIManager.getColor("Panel.background");
    private static final Color DISABLED_BG = Color.LIGHT_GRAY; // Gray for SR/Inactive
    private JPanel cashPanel;

    public ORWindow getORWindow() {
        return orWindow;
    }

    public ORPanel(ORWindow parent, ORUIManager orUIManager) {
        super();

        activeInstances.add(this);
        // CRITICAL: Set the size of the ORPanel itself to match the desired sidebar
        // width when used as the WEST panel in ORWindow.
        setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));

        orWindow = parent;
        this.orUIManager = orUIManager;
        GameUIManager gameUIManager = parent.gameUIManager;

        // Use a blank JPanel for gridPanel to satisfy GridPanel heritage,
        // ensuring no inherited methods accidentally target the active sidebar layout.
        gridPanel = new JPanel();
        parentFrame = parent;
        // Ensure the ORPanel can receive focus for hotkeys
        setFocusable(true);

        round = gameUIManager.getCurrentRound();
        privatesCanBeBought = gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.CAN_ANY_COMPANY_BUY_PRIVATES);
        bonusTokensExist = gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.DO_BONUS_TOKENS_EXIST);
        hasCompanyLoans = gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.HAS_ANY_COMPANY_LOANS);
        hasRights = gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.HAS_ANY_RIGHTS);
        hasDirectCompanyIncomeInOR = gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.HAS_SPECIAL_COMPANY_INCOME);

        initSidebar();

        gbc = new GridBagConstraints();

        players = gameUIManager.getPlayerManager();

        if (round instanceof OperatingRound) {
            companies = ((OperatingRound) round).getOperatingCompanies().toArray(new PublicCompany[0]);
            nc = companies.length;
        }

        initFields();

        // --- MENU REMOVAL ---
        // All local JMenuBar construction (Info, Special, Loans, Zoom) has been removed
        // as it is now handled by StatusWindow.
        setupHotkeys(); // <--- ADD THIS CALL
        setVisible(true);
    }

    public void recreate(OperatingRound or) {
        companies = or.getOperatingCompanies().toArray(new PublicCompany[0]);
        nc = companies.length;

        deRegisterObservers();

        // AGGRESSIVE WAKE-UP:
        // Ensure the sidebar transitions from "Stock Round Gray" to "Active Mauve"
        // immediately.
        // We unhide all standard panels to ensure the UI is ready for the first
        // company.
        if (sidebarPanel != null) {
            sidebarPanel.setBackground(BG_DETAILS); // Mauve
            sidebarPanel.setOpaque(true);

            if (cashPanel != null)
                cashPanel.setVisible(true);
            if (phase1Panel != null)
                phase1Panel.setVisible(true);
            if (phase2Panel != null)
                phase2Panel.setVisible(true);
            if (phase3Panel != null)
                phase3Panel.setVisible(true);
            if (phase4Panel != null)
                phase4Panel.setVisible(true);
            if (footerPanel != null)
                footerPanel.setVisible(true);

            sidebarPanel.revalidate();
            sidebarPanel.repaint();
        }

        // Force data update immediately after recreate (when Stock Round ends)
        // The ORUIManager initOR method calls this.
        if (orComp != null) { // orComp is set inside initORCompanyTurn
            updateSidebarData();
        }

        repaint();
    }

    private void initButtonPanel() {
        // --- 1. Initialize Buttons (RESTORED) ---
        // This prevents the NullPointerException

        buttonOC = new ActionButton(RailsIcon.OPERATING_COST);
        buttonOC.setActionCommand(OPERATING_COST_CMD);
        buttonOC.setMnemonic(KeyEvent.VK_O);
        buttonOC.addActionListener(this);
        buttonOC.setEnabled(false);
        buttonOC.setVisible(false);

        button1 = new ActionButton(RailsIcon.BUY_TRAIN);
        button1.addActionListener(this);
        button1.setEnabled(false);

        button2 = new ActionButton(RailsIcon.DONE);
        button2.addActionListener(this);
        button2.setEnabled(false);
        button2.setVisible(false);

        button3 = new ActionButton(RailsIcon.BUY_PRIVATE);
        button3.addActionListener(this);
        button3.setEnabled(false);
        button3.setVisible(false);

    }

    public MouseListener getCompanyCaptionMouseClickListener() {
        return new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getComponent() instanceof Caption) {
                    Caption c = (Caption) e.getComponent();
                    executeNetworkInfo(c.getText());
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mousePressed(MouseEvent e) {
            }

            @Override
            public void mouseReleased(MouseEvent e) {
            }
        };
    }

    private void initFields() {
    }

    protected void executeNetworkInfo(String companyName) {
        RailsRoot root = orUIManager.getGameUIManager().getRoot();

        if (companyName.equals("Network")) {
            NetworkAdapter network = NetworkAdapter.create(root);
            NetworkGraph mapGraph = network.getMapGraph();
            mapGraph.optimizeGraph();
            JFrame mapWindow = mapGraph.visualize("Optimized Map Network");
            if (mapWindow != null) {
                openWindows.add(mapWindow);
            }
        } else {
            CompanyManager cm = root.getCompanyManager();
            PublicCompany company = cm.getPublicCompany(companyName);
            // handle the case of invalid parameters
            // could occur if the method is not invoked by the menu (but by the click
            // listener)
            if (company == null)
                return;
            if (Config.getBoolean("map.route.window.display", true)) {
                NetworkAdapter network = NetworkAdapter.create(root);
                NetworkGraph routeGraph = network.getRevenueGraph(company, Lists.newArrayList());
                JFrame mapWindow = routeGraph.visualize("Route Network for " + company);
                if (mapWindow != null) {
                    openWindows.add(mapWindow);
                }
            }
            List<String> addTrainList = new ArrayList<>();
            boolean anotherTrain = true;
            RevenueAdapter ra;
            while (anotherTrain) {
                // multi
                ra = RevenueAdapter.createRevenueAdapter(root, company, root.getPhaseManager().getCurrentPhase());
                for (String addTrain : addTrainList) {
                    ra.addTrainByString(addTrain);
                }
                ra.initRevenueCalculator(true); // true => multigraph, false => simplegraph
                int revenueValue = ra.calculateRevenue();
                // try-catch clause temporary workaround as revenue adapter's
                // convertRcRun might erroneously raise exceptions
                try {
                    ra.drawOptimalRunAsPath(orUIManager.getMap());
                } catch (Exception e) {
                }
                /*
                 * TODO: Here the automatic calculation of the Special Company Income needs to
                 * be implemented
                 * 1837: Coal Mines
                 * 1853: Mail Contract
                 * 1822: Mail Contract
                 * 1822CA: Mail Contract
                 * 1854 old/new : Mail Contract ?
                 */
                int specialRevenue = ra.getSpecialRevenue();

                if (!Config.isDevelop()) {
                    // parent component is ORPanel so that dialog won't hide the routes painted on
                    // the map
                    JOptionPane.showMessageDialog(this,
                            LocalText.getText("NetworkInfoDialogMessage", company.getId(),
                                    orUIManager.getGameUIManager().format(revenueValue)),
                            LocalText.getText("NetworkInfoDialogTitle", company.getId()),
                            JOptionPane.INFORMATION_MESSAGE);
                    // train simulation only for developers
                    break;
                }

                JOptionPane.showMessageDialog(orWindow, "RevenueValue = " + revenueValue +
                        "\nRevenueRun = \n" + ra.getOptimalRunPrettyPrint(true));

                String trainString = JOptionPane.showInputDialog(null,
                        "Enter train string (Examples: 5, 3+3, 4D, 6E, D)",
                        "Add another train to run?",
                        JOptionPane.QUESTION_MESSAGE);
                if (trainString == null || trainString.equals("")) {
                    anotherTrain = false;
                } else {
                    addTrainList.add(trainString);
                }

            }
            // clean up the paths on the map
            orUIManager.getMap().setTrainPaths(null);
            // but retain paths already existing before
            if (revenueAdapter != null) {
                // try-catch clause temporary workaround as revenue adapter's
                // convertRcRun might erroneously raise exceptions
                try {
                    revenueAdapter.drawOptimalRunAsPath(orUIManager.getMap());
                } catch (Exception e) {
                }
            }
        }
    }

    public void redrawRoutes() {
        if (revenueAdapter != null && isDisplayRoutes()) {
            // try-catch clause temporary workaround as revenue adapter's
            // convertRcRun might erroneously raise exceptions
            try {
                revenueAdapter.drawOptimalRunAsPath(orUIManager.getMap());
            } catch (Exception e) {
            }
        }
    }

    public int getRevenue(int orCompIndex) {
        // Return the current value displayed in the button or label if needed,
        // but primarily we track this via the actions.
        return 0;
    }

    public void setDividend(int orCompIndex, int amount) {
        // Similar to setRevenue, usually handled by the generic update
    }

    public void resetActions() {
        // All Dashboard Highlighting logic removed.

        for (JMenuItem item : menuItemsToReset) {
            item.setEnabled(false);
            if (item instanceof ActionMenuItem) {
                ((ActionMenuItem) item).clearPossibleActions();
            }
        }

        removeAllHighlights();
    }

    // *** Apparently not used
    public void resetORCompanyTurn(int orCompIndex) {
        for (int i = 0; i < nc; i++) {
            /*
             * if (hasDirectCompanyIncomeInOr) {
             * setSelect(revenue[i], revenueSelect[i], directIncomeSelect[i],
             * directIncomeRevenue[i], false);
             * } else {
             * setSelect(revenue[i], revenueSelect[i], false);
             * }
             */
            selectRevenueSpinner(false);
        }
    }

    // No longer used?
    public void resetCurrentRevenueDisplay() {

    }

    /**
     *
     * @return True if route should be displayed (at least for the set revenue step)
     */
    private boolean isDisplayRoutes() {
        return (orUIManager.gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.ROUTE_HIGHLIGHT));
    }

    private boolean isSuggestRevenue() {
        return (orUIManager.gameUIManager.getGameParameterAsBoolean(GuiDef.Parm.REVENUE_SUGGEST));
    }

    /**
     *
     * @return True if the routes of the currently active company should be
     *         displayed.
     *         As a prerequisite of this feature, route highlighting has to be
     *         enabled/supported.
     */
    private boolean isDisplayCurrentRoutes() {
        return (isDisplayRoutes()
                && "yes".equalsIgnoreCase(Config.get("map.displayCurrentRoutes")));
    }

    /**
     * any routes currently displayed on the map are removed
     * In addition, revenue adapter and its thread are interrupted / removed.
     */
    private void disableRoutesDisplay() {
        clearRevenueAdapter();
        orUIManager.getMap().setTrainPaths(null);
    }

    private void clearRevenueAdapter() {
        if (revenueThread != null) {
            revenueThread.interrupt();
            revenueThread = null;
        }
        if (revenueAdapter != null) {
            revenueAdapter.removeRevenueListener();
            revenueAdapter = null;
        }
    }

    private void updateCurrentRoutes(boolean isSetRevenueStep) {

        // Always calculate revenue to ensure the Sidebar shows the "Actual" potential
        // revenue.
        // We guard against null/closed companies, but ignore the "Display Routes"
        // preference for the calculation itself.
        if (orComp != null && !orComp.isClosed()) {

            // only consider revenue quantification for the set revenue step and only
            // if suggest option is on
            isRevenueValueToBeSet = isSetRevenueStep && isSuggestRevenue();

            RailsRoot root = orUIManager.getGameUIManager().getRoot();

            // Ensure previous calculation is stopped before starting a new one
            clearRevenueAdapter();

            revenueAdapter = RevenueAdapter.createRevenueAdapter(root, orComp,
                    root.getPhaseManager().getCurrentPhase());
            revenueAdapter.initRevenueCalculator(true);
            revenueAdapter.addRevenueListener(this);
            revenueThread = new Thread(revenueAdapter);
            revenueThread.start();
        } else {

            // remove current routes also if display option is not active
            // (as it could have just been turned off)
            clearRevenueAdapter();
            disableRoutesDisplay();
        }

    }

    public void stopRevenueUpdate() {
        isRevenueValueToBeSet = false;
    }

    public void initOperatingCosts(boolean enabled) {

        buttonOC.setEnabled(enabled);
        buttonOC.setVisible(enabled);

    }

    public void initPrivateBuying(boolean enabled) {

        if (privatesCanBeBought) {
            if (enabled) {
                button3.setRailsIcon(RailsIcon.BUY_PRIVATE);
                button3.setActionCommand(BUY_PRIVATE_CMD);
                button3.setMnemonic(KeyEvent.VK_V);
            }
            button3.setEnabled(enabled);
            button3.setVisible(enabled);
            // privatesCaption.setHighlight(enabled);
            // if (orCompIndex >= 0)
            // setHighlight(privates[orCompIndex], enabled);
        } else {
            button3.setVisible(false);
        }
    }

    public void initSpecialActions() {

        specialMenu.removeAll();
        specialMenu.setEnabled(false);
        specialMenu.setOpaque(false);
    }

    public void addSpecialAction(PossibleAction action, String text) {

        ActionMenuItem item = new ActionMenuItem(text);
        item.addActionListener(this);
        item.addPossibleAction(action);
        specialMenu.add(item);
        specialMenu.setEnabled(true);
        specialMenu.setOpaque(true);
    }

    public boolean hasSpecialActions() {
        return specialMenu.getItemCount() > 0;
    }

    public void enableUndo(GameAction action) {
        // undoButton.setEnabled(action != null);
        if (action != null)
            this.currentUndoAction = action;
    }

    public void enableRedo(GameAction action) {
        // redoButton.setEnabled(action != null);
        if (action != null)
            this.currentRedoAction = action;
    }

    public void enableLoanTaking(TakeLoans action) {
        if (action != null)
            takeLoans.addPossibleAction(action);
        takeLoans.setEnabled(action != null);
    }

    public void dispose() {
        for (JFrame frame : openWindows) {
            frame.dispose();
        }
        openWindows.clear();
    }

    public void enableLoanRepayment(RepayLoans action) {

        repayLoans.setPossibleAction(action);
        repayLoans.setEnabled(true);

        // loansCaption.setHighlight(true);
        // setHighlight(compLoans[orCompIndex], true);

        button1.setRailsIcon(RailsIcon.REPAY_LOANS);
        button1.setActionCommand(REPAY_LOANS_CMD);
        button1.setPossibleAction(action);
        button1.setMnemonic(KeyEvent.VK_R);
        button1.setEnabled(true);
        button1.setVisible(true);
    }

    public void disableButtons() {
        if (button1 != null)
            button1.setEnabled(false);
        if (button2 != null)
            button2.setEnabled(false);
        if (button3 != null)
            button3.setEnabled(false);

        if (dynamicButtonPanel != null) {
            dynamicButtonPanel.removeAll();
            dynamicButtonPanel.revalidate();
            dynamicButtonPanel.repaint();
        }
    }

    public void finishORCompanyTurn(int orCompIndex) {
        // clear all highlighting (president column and beyond)
        resetActions();
        // Ensure map numbers are switched off when the company finishes
        setTileBuildNumbers(false);
        orUIManager.getMap().setTrainPaths(null);
    }

    private void setSelect(JComponent f, JComponent s, boolean active) {
        f.setVisible(!active);
        s.setVisible(active);
    }

    private void setSelect(JComponent f, JComponent s, JComponent s2,
            JComponent f2, boolean active) {
        f.setVisible(!active);
        s.setVisible(active);
        f2.setVisible(!active);
        s2.setVisible(active);
    }

    // EV: to replace the above two methods
    private void selectRevenueSpinner(boolean active) {
        selectRevenueSpinner(orCompIndex, active);
    }

    // In ORPanel.java
    private void selectRevenueSpinner(int compIndex, boolean active) {

    }

    public String format(int amount) {
        return orUIManager.getGameUIManager().format(amount);
    }

    private int parseOldValue(String text) {
        if (Util.hasValue(text)) {
            // 1. Remove non-numeric characters (including currency symbols, commas, etc.)
            String numericString = text.replaceAll("[^0-9-]", "");

            // 2. CRITICAL: Check if the resulting string is actually empty (i.e., it
            // contained only non-numeric chars)
            if (numericString.isEmpty() || numericString.equals("-")) {
                return 0;
            }

            // 3. Parse the clean numeric string
            try {
                return Integer.parseInt(numericString);
            } catch (NumberFormatException e) {
                // Fallback for safety, though check should prevent this
                return 0;
            }
        } else {
            return 0;
        }
    }

    public PublicCompany[] getOperatingCompanies() {
        return companies;
    }

    public JMenuBar getMenuBar() {
        return null;
    }

    /**
     * Control visibility of companies in the ORPanel.
     * 
     * @param showAll True if all active companies must be shown,
     *                false if only the currently operating company is shown.
     */
    private void setCompanyVisibility(boolean showAll) {

    }

    public int getCompanyTreasuryBonusRevenue(int orCompIndex) {
        return 0;
    }

    public void setTreasuryBonusRevenue(int orCompIndex2, int bonusAmount) {

    }

    private ActionButton createDynamicButton(PossibleAction action) {

        // 1. Create the button with a default "Undo" icon, as required
        // by the constructor to prevent a crash.
        ActionButton button = new ActionButton(RailsIcon.OK);

        // 2. Set the visible text from the action's label.
        // (e.g., "Discard 2+2_3")
        button.setText(action.getButtonLabel());

        // 3. NOW, REMOVE THE ICON. This should force the button
        // to respect the text we just set.
        button.setIcon(null);

        // 4. Force the button to recalculate its size based on the new text.
        button.setPreferredSize(null);

        // 5. Set the tooltip (still useful).
        button.setToolTipText(action.getButtonLabel());

        // 6. Associate the action.
        button.setPossibleAction(action);

        // 7. Add listener.
        button.addActionListener(this);
        button.setEnabled(true);
        button.setVisible(true);
        return button;
    }

    public void updateCycleableHexes(Collection<GUIHex> hexes) {
        cycleableHexes.clear();
        cycleIndex = -1;
        if (hexes != null) {
            for (GUIHex hex : hexes) {
                // Only add hexes that are visibly highlighted as options
                if (hex.getState() == GUIHex.State.SELECTABLE) {
                    cycleableHexes.add(hex);
                }
            }
        }
    }

    /**
     * Clears the list of cycleable hexes. Called when non-map
     * steps (like Buy Train) are active.
     */
    public void resetHexCycle() {
        cycleableHexes.clear();
        cycleIndex = -1;
    }

    private void addLegendItem(String key, String desc) {
        JLabel lbl = new JLabel("<html><b>[" + key + "]</b> " + desc + "</html>");
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
        lbl.setForeground(Color.DARK_GRAY);
        legendPanel.add(lbl);
        legendPanel.add(new JLabel("|"));
    }

    // STUB METHOD: Add this to ORPanel class to fix "cannot find symbol" error
    public void enableSkipAllButton(boolean enable) {
        // Button removed, functionality handled by Done/Skip buttons
    }

    // : Sidebar Construction & Logic ---
    public JPanel getSidebarPanel() {
        return sidebarPanel;
    }

    private void applyPanelStyle(JPanel p, boolean active, Color c1, Color c2, Border b1, Border b2) {
        p.setBackground(active ? c1 : c2);
        ((javax.swing.border.TitledBorder) p.getBorder()).setBorder(active ? b1 : b2);
        p.repaint();
    }

    public void initTileLayingStep() {
        activePhase = 1;
        // Don't clear buttons here, let updateDynamicActions do it
    }

    public void initTokenLayingStep() {
        activePhase = 2;
    }

    public void initRevenueEntryStep(int idx, SetDividend action) {
        activePhase = 3; // Fallback
    }

    public void initPayoutStep(int idx, SetDividend action, boolean w, boolean s, boolean p) {
        activePhase = 3;
    }

    public void initTrainBuying(List<BuyTrain> actions) {
        activePhase = 4;
    }

    public void setupConfirm() {
        enableConfirm(false);
    }

    public void enableButton1(PossibleAction a) {
    } // No-op

    public void enableSkip(NullAction a) {
    } // No-op, handled by updateDynamicActions

    public void enableDone(NullAction a) {
    } // No-op

    private void cleanupUpgradesPanel() {
        // Attempt to find and hide the "Ghost" buttons in the UpgradesPanel
        if (orUIManager != null && orUIManager.getUpgradePanel() != null) {
            Component[] comps = orUIManager.getUpgradePanel().getComponents();
            for (Component c : comps) {
                if (c instanceof AbstractButton) {
                    AbstractButton b = (AbstractButton) c;
                    String cmd = b.getActionCommand();
                    // Hide buttons that conflict with our Sidebar
                    if (CONFIRM_CMD.equals(cmd) || SKIP_CMD.equals(cmd)) {
                        b.setVisible(false);
                    }
                }
            }
        }
    }

    private void setupButton(ActionButton btn, PossibleAction pa) {
        if (btn == null)
            return; // Crash Protection
        btn.setEnabled(true);
        btn.setPossibleAction(pa);
    }

    private void enableRevenueBtn(ActionButton btn, SetDividend sd, int allocation) {
        btn.setEnabled(true);
        SetDividend clone = (SetDividend) sd.clone();
        clone.setRevenueAllocation(allocation);
        btn.setPossibleAction(clone);
    }

    // ... (lines 2355-2360)
    private void updateDefaultButton() {
        ActionButton defaultBtn = null;
        // 1. Determine Default Button based on Active Phase
        if (activePhase == 1) {
            // Phase 1: Tile Laying (Confirm or Skip)
            if (btnTileConfirm != null && btnTileConfirm.isEnabled()) {
                defaultBtn = btnTileConfirm;
            }
        } else if (activePhase == 2) {
            // Phase 2: Token Laying (Confirm or Skip)
            if (btnTokenConfirm != null && btnTokenConfirm.isEnabled()) {
                defaultBtn = btnTokenConfirm;
            }
        } else if (activePhase == 3) {
            // Phase 3: Revenue (Intelligent Selection for Majors vs Minors)

            // Case A: Standard Corporation (Major) - Payout is King
            if (btnRevPayout != null && btnRevPayout.isEnabled()) {
                defaultBtn = btnRevPayout;
            }
            // Case B: 1837 Coal/Minor Scenario - Split is King
            // If Payout is disabled (enforced by OperatingRound), but Split is enabled.
            else if (btnRevSplit != null && btnRevSplit.isEnabled()) {
                defaultBtn = btnRevSplit;
            }
            // Case C: Fallbacks (Withhold)
            else if (btnRevWithhold != null && btnRevWithhold.isEnabled()) {
                defaultBtn = btnRevWithhold;
            }
        } else if (activePhase == 4) {
            // Phase 4: Train Buying (Skip Buy / Done Buying)
            if (btnTrainSkip != null && btnTrainSkip.isEnabled()) {
                defaultBtn = btnTrainSkip;
            }
        } else {
            // Phase 5 (Finalize) or Fallback: Done
            if (btnDone != null && btnDone.isEnabled()) {
                defaultBtn = btnDone;
            }
        }

        // 2. Apply Default (The Fix)
        // We MUST update the class field 'currentDefaultButton' because
        // the Game's hotkey/AI manager likely checks this specific field
        // to know what to do when 'Enter' is pressed.
        this.currentDefaultButton = defaultBtn;

        javax.swing.JRootPane rp = getRootPane();
        if (rp != null) {
            rp.setDefaultButton(defaultBtn);
        }
    }
    // ... (lines 2396-2400)

    // Helper to safely extract the action from the button
    private SetDividend getSetDividend(ActionButton btn) {
        if (btn.getPossibleActions() != null && !btn.getPossibleActions().isEmpty()) {
            if (btn.getPossibleActions().get(0) instanceof SetDividend) {
                return (SetDividend) btn.getPossibleActions().get(0);
            }
        }
        return null;
    }

    public void initORCompanyTurn(PublicCompany orComp, int orCompIndex) {
        // Force reset of map numbers to clear artifacts from the previous company.
        // We do this unconditionally (instead of checking showNumbersActive) to ensure
        // the map is scrubbed clean even if the UI state thinks it's already off.
        setTileBuildNumbers(false);
        //
        // log.info("ORPanel: initORCompanyTurn for {}. Clearing Map Overlays.", (orComp
        // != null ? orComp.getId() : "null"));

        // Explicitly call the map panel's clear method to ensure the visual state
        // is reset before the new company's data is calculated.
        if (orWindow != null && orWindow.getMapPanel() != null) {
            orWindow.getMapPanel().clearOverlays();
        }

        try {
            this.orComp = orComp;
            this.currentOperatingComp = orComp; // Store baseline context
            this.discardMode = false;
            this.orCompIndex = orCompIndex;

            if (showNumbersActive) {
                toggleTileBuildNumbers(); // Umschalten auf 'false'
            }

            // if (orCompIndex >= 0 && president != null && orCompIndex < president.length
            // && president[orCompIndex] != null) {
            // president[orCompIndex].setHighlight(true);
            // }
            removeAllHighlights();

            if (sidebarPanel != null) {
                // Reset to Mauve when active
                sidebarPanel.setBackground(BG_DETAILS);

                // Show Cash Panel ---
                if (cashPanel != null)
                    cashPanel.setVisible(true);

                phase1Panel.setVisible(true);
                phase2Panel.setVisible(true);
                phase3Panel.setVisible(true);
                phase4Panel.setVisible(true);
                footerPanel.setVisible(true);
            }

            if (orComp != null && companyLogo != null) {
                companyLogo.setText(orComp.getId());
                companyLogo.setBackground(orComp.getBgColour());
                companyLogo.setForeground(orComp.getFgColour());
                companyLogo.setVisible(true);
                companyLogo.revalidate();
            }

            if (orUIManager != null) {
                PossibleActions paContainer = orUIManager.getPossibleActions();
                if (paContainer != null) {
                    List<PossibleAction> initialActions = paContainer.getList();
                    if (initialActions != null && !initialActions.isEmpty()) {
                        updateDynamicActions(initialActions);
                    }
                }
            }

            updateSidebarData();
            updateCurrentRoutes(false);
            // Trigger MiniDock update when a new company starts
            if (orWindow != null && orWindow.getUpgradePanel() != null) {
                orWindow.getUpgradePanel().refreshMiniDock();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles cycling through selectable hexes on the map based on direction.
     * 
     * @param direction 1 for next (CW), -1 for previous (ACW).
     */
    private void cycleHexes(int direction) {
        if (orUIManager == null || cycleableHexes.isEmpty())
            return;

        cycleIndex += direction;

        // Wrap around logic
        if (cycleIndex >= cycleableHexes.size())
            cycleIndex = 0;
        if (cycleIndex < 0)
            cycleIndex = cycleableHexes.size() - 1;

        GUIHex hexToSelect = cycleableHexes.get(cycleIndex);
        // Select the hex on the map
        orUIManager.hexClicked(hexToSelect, orUIManager.getMap().getSelectedHex(), false);
        enableConfirm(true);
    }

    // Replace the existing revenueUpdate method with this one.
    // It correctly splits the Total (bestRevenue) into Player + Treasury portions
    // for the display.
    @Override
    public void revenueUpdate(int bestRevenue, int specialRevenue, boolean finalResult) {
        // 1. Update the Sidebar Label
        if (lblRevenue != null) {
            if (specialRevenue > 0) {
                // Show "Player + Treasury" (e.g. "20 + 15")
                int playerShare = bestRevenue - specialRevenue;
                lblRevenue.setText(format(playerShare) + " + " + format(specialRevenue));
            } else {
                lblRevenue.setText(orUIManager.getGameUIManager().format(bestRevenue));
            }
        }

        if (isRevenueValueToBeSet) {
            // Send the TOTAL to the backend (The backend logic expects the Total and will
            // do the subtraction itself)
            setRevenue(orCompIndex, bestRevenue);

            if (hasDirectCompanyIncomeInOR) {
                // 1837 Specific: Handle the split
                setDirectRevenue(orCompIndex, specialRevenue);

                // Calculate dividend for the button label (Total - Treasury)
                int dividend = bestRevenue - specialRevenue;

                // FORCE UPDATE BUTTON TEXT WITH BREAKDOWN
                updateRevenueButtonText(btnRevPayout, dividend, specialRevenue);
                updateRevenueButtonText(btnRevWithhold, dividend, specialRevenue);
                updateRevenueButtonText(btnRevSplit, dividend, specialRevenue);
            }
        }

        if (finalResult) {
            // Clear old paths
            orUIManager.getMap().setTrainPaths(null);

            // Draw new path if enabled
            if (isDisplayCurrentRoutes()) {
                try {
                    revenueAdapter.drawOptimalRunAsPath(orUIManager.getMap());
                } catch (Exception e) {
                    log.error("ORPanel: Failed to draw route", e);
                }
            }

            if (isRevenueValueToBeSet) {
                String runDescription = Util.convertToHtml(revenueAdapter.getOptimalRunPrettyPrint(false));
                orUIManager.getMessagePanel().setInformation("Best Run Value = " + bestRevenue +
                        " with " + runDescription);
                orUIManager.getMessagePanel().setDetail(
                        Util.convertToHtml(revenueAdapter.getOptimalRunPrettyPrint(true)));
            }
        }
    }

    // 1. Helper to format the button text nicely (e.g. "Payout 20 + 15")
    private void updateRevenueButtonText(ActionButton btn, int playerPart, int treasuryPart) {
        if (btn == null || !btn.isEnabled())
            return;

        // We only change the text if there is a treasury part (1837 style)
        if (treasuryPart > 0) {
            String baseCmd = "";
            if (btn == btnRevPayout)
                baseCmd = "Payout";
            else if (btn == btnRevWithhold)
                baseCmd = "Withhold";
            else if (btn == btnRevSplit)
                baseCmd = "Split";

            // Format: "Split 20 + 15"
            btn.setText(baseCmd + " " + format(playerPart) + " + " + format(treasuryPart));
            btn.repaint();
        }
    }

    // 2. Helper to update the Action Object IN-PLACE (Fixes the "Revenue 0" bug)
    private void updateRevenueButton(ActionButton btn, int amount) {
        if (btn == null || !btn.isEnabled())
            return;

        List<PossibleAction> actions = btn.getPossibleActions();

        if (actions != null && !actions.isEmpty() && actions.get(0) instanceof SetDividend) {
            SetDividend sd = (SetDividend) actions.get(0);
            // UPDATE IN PLACE: Do not clone.
            sd.setActualRevenue(amount);
            btn.repaint();
        }
    }

    // 3. Helper for 1837 Direct Revenue
    public void setDirectRevenue(int orCompIndex, int amount) {
        updateRevenueButtonDirect(btnRevPayout, amount);
        updateRevenueButtonDirect(btnRevWithhold, amount);
        updateRevenueButtonDirect(btnRevSplit, amount);
    }

    private void updateRevenueButtonDirect(ActionButton btn, int amount) {
        if (btn == null || !btn.isEnabled())
            return;

        List<PossibleAction> actions = btn.getPossibleActions();
        if (actions != null && !actions.isEmpty() && actions.get(0) instanceof SetDividend) {
            SetDividend sd = (SetDividend) actions.get(0);
            sd.setActualCompanyTreasuryRevenue(amount);
        }
    }

    // 4. Update the standard setRevenue to use our new in-place helper
    public void setRevenue(int orCompIndex, int amount) {
        updateRevenueButton(btnRevPayout, amount);
        updateRevenueButton(btnRevWithhold, amount);
        updateRevenueButton(btnRevSplit, amount);
    }

    private JLabel createInfoLabel(String text) {
        JLabel lbl = new JLabel(text, SwingConstants.CENTER);
        lbl.setOpaque(true);
        lbl.setBackground(Color.WHITE); // INFO_BG
        lbl.setForeground(Color.BLACK);
        lbl.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        // Slightly wider to fill panel, minus padding
        lbl.setMaximumSize(new Dimension(SIDEBAR_WIDTH - 20, 24));
        lbl.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, 24));
        return lbl;
    }

    @Override
    public void actionPerformed(ActionEvent actor) {
        // long eventTime = actor.getWhen(); // Unused

        JComponent source = (JComponent) actor.getSource();
        String command = actor.getActionCommand();

        // --- 1. Handle Commands (Zoom logic moved to StatusWindow) ---

        if (command.equals(REM_TILES_CMD)) {
            // Keep: Required for the "Remaining Tiles" command to work
            new RemainingTilesWindow(orWindow);
            return;
        }

        // --- 2. Handle Sidebar Buttons ---
        else if (command.equals(SHOW_CMD)) {
            // Toggle visual help numbers on map
            toggleTileBuildNumbers();
            return;
        } else if (command.equals(TRAIN_SKIP_CMD)) {
            // Manual Skip for Phase 4 -> Advance to Phase 5 (Done)
            activePhase = 5;
            updateSidebarData();
            updateDefaultButton(); // Focus moves to 'Done'

            // visually disable train buttons to indicate the choice is made
            if (btnTrainSkip != null)
                btnTrainSkip.setEnabled(false);
            if (trainButtonsPanel != null) {
                for (Component c : trainButtonsPanel.getComponents()) {
                    c.setEnabled(false);
                }
            }
            // Ensure focus returns to the main panel so the 'Enter' key works immediately
            SwingUtilities.invokeLater(this::requestFocusInWindow);
            return;
        }

        // --- 3. Handle Confirm Button ---
        else if (command.equals(CONFIRM_CMD)) {
            if (orUIManager != null) {
                // Check if we have a map selection.
                // If YES -> Confirm.
                // If NO -> Skip.
                boolean hasSelection = (orUIManager.getMap().getSelectedHex() != null);

                if (hasSelection) {
                    orUIManager.confirmUpgrade();
                } else {
                    // Trigger the Skip logic (Smart Default)
                    orUIManager.skipUpgrade();
                }

            }
            return;
        }

        // --- 4. Handle Operating Cost ---
        else if (command.equals(OPERATING_COST_CMD)) {
            if (orUIManager != null)
                orUIManager.operatingCosts();
            return;
        }

        // --- 5. Handle Buy Private ---
        else if (command.equals(BUY_PRIVATE_CMD)) {
            if (orUIManager != null)
                orUIManager.buyPrivate();
            return;
        }

        // --- 6. Handle ALL GAME ACTIONS (Buttons, ActionMenus) ---
        List<PossibleAction> executedActions = null;

        if (source instanceof ActionTaker) {
            executedActions = ((ActionTaker) source).getPossibleActions();

            if (executedActions != null && !executedActions.isEmpty()) {
                PossibleAction first = executedActions.get(0);

                // Safety checks for map actions
                if ((first instanceof LayToken && ((LayToken) first).getChosenHex() == null) ||
                        (first instanceof LayBaseToken && ((LayBaseToken) first).getChosenHex() == null) ||
                        (first instanceof LayTile && ((LayTile) first).getChosenHex() == null)) {

                    return;
                }
            }
            orUIManager.processAction(command, executedActions, source);
        }
    }

    private JPanel createDataRow(String labelText, JLabel valueField) {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setOpaque(false);
        panel.setMaximumSize(new Dimension(SIDEBAR_WIDTH, 26)); // Fixed height row

        JLabel lbl = new JLabel(labelText);
        lbl.setForeground(Color.DARK_GRAY);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        lbl.setPreferredSize(new Dimension(55, 26)); // Fixed label width for alignment
        lbl.setHorizontalAlignment(SwingConstants.RIGHT);

        valueField.setBackground(INFO_BG); // White
        valueField.setOpaque(true);
        valueField.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        valueField.setHorizontalAlignment(SwingConstants.CENTER);
        valueField.setFont(new Font("SansSerif", Font.PLAIN, 12));

        panel.add(lbl, BorderLayout.WEST);
        panel.add(valueField, BorderLayout.CENTER);

        return panel;
    }

    public void resetSidebarState() {

        // Ensure btnDone is clean before next round of logic, preventing stale commands
        // (like TRAIN_SKIP_CMD) from persisting into other phases.
        if (btnDone != null) {
            btnDone.setActionCommand(DONE_CMD);
            btnDone.setPossibleAction(null);
            btnDone.setEnabled(false);
        }
        discardMode = false;

        if (btnTileSkip != null)
            btnTileSkip.setEnabled(false);
        if (btnTileConfirm != null)
            btnTileConfirm.setEnabled(false);
        if (btnTokenSkip != null)
            btnTokenSkip.setEnabled(false);
        if (btnTokenConfirm != null)
            btnTokenConfirm.setEnabled(false);
        if (btnRevPayout != null)
            btnRevPayout.setEnabled(false);
        if (btnRevWithhold != null)
            btnRevWithhold.setEnabled(false);
        if (btnRevSplit != null)
            btnRevSplit.setEnabled(false);
        if (btnDone != null)
            btnDone.setEnabled(false);
        if (btnTrainSkip != null)
            btnTrainSkip.setEnabled(false); // Fix NPE

// Ensure the panel is visible and ready for new components
        if (trainButtonsPanel != null) {
            trainButtonsPanel.removeAll();
            trainButtonsPanel.setVisible(true); 
            trainButtonsPanel.revalidate(); 
            trainButtonsPanel.repaint();
        }


        if (miscActionPanel != null) {
            miscActionPanel.removeAll();
            miscActionPanel.revalidate(); // Reset layout
            miscActionPanel.repaint();
        }

        if (specialContainer != null)
            specialContainer.setVisible(false);
        if (specialPanel != null)
            specialPanel.removeAll();

        activePhase = 0;
        // highlightActivePhase(); // Optional: clears border highlights
    }

    public void finish() {
        this.orComp = null;
        this.orCompIndex = -1;

        // Ensure map numbers are cleared when the round ends
        setTileBuildNumbers(false);

        if (sidebarPanel != null) {
            sidebarPanel.setBackground(Color.LIGHT_GRAY); // Disabled BG

            // Reset Logo
            if (companyLogo != null) {
                companyLogo.setText("Stock Round");
                companyLogo.setBackground(Color.LIGHT_GRAY);
                companyLogo.setForeground(Color.GRAY);
                companyLogo.setVisible(true);
            }
            // --- Wipe Cockpit Data ---
            if (lblCash != null) {
                lblCash.setText("-");
                lblCash.setBackground(Color.WHITE);
                // Hide the container holding the cash label so the box disappears completely
                if (lblCash.getParent() != null) {
                    lblCash.getParent().setVisible(false);
                }
            }

            if (tokenDisplay != null) {
                tokenDisplay.setTokens(0, Color.GRAY);
            }
            if (lblRevenue != null) {
                lblRevenue.setText("-");
                lblRevenue.setBackground(Color.WHITE);
            }
            if (trainDisplay != null) {
                trainDisplay.updateAssets(orComp);
            }

            // Hide Phase Panels
            if (phase1Panel != null)
                phase1Panel.setVisible(false);
            if (phase2Panel != null)
                phase2Panel.setVisible(false);
            if (phase3Panel != null)
                phase3Panel.setVisible(false);
            if (phase4Panel != null)
                phase4Panel.setVisible(false);
            if (footerPanel != null)
                footerPanel.setVisible(false);

            // Aggressively clear Special Panel to prevent "Sticky Buttons" artifact
            // We must remove components AND force validation to ensure the UI updates
            // immediately.
            if (specialPanel != null) {
                specialPanel.removeAll();
                specialPanel.revalidate();
                specialPanel.repaint();
            }
            if (specialContainer != null) {
                specialContainer.setVisible(false);
                specialContainer.revalidate(); // Force layout update to hide border
                specialContainer.repaint();
            }

            sidebarPanel.revalidate();
            sidebarPanel.repaint();
        }

        if (trainButtonsPanel != null) {
            trainButtonsPanel.removeAll();
            trainButtonsPanel.revalidate(); // Force layout refresh
            trainButtonsPanel.repaint();
        }
        // Disable Legacy Buttons
        if (buttonOC != null)
            buttonOC.setEnabled(false);
        if (button1 != null)
            button1.setEnabled(false);
        if (button2 != null)
            button2.setEnabled(false);
        if (button3 != null)
            button3.setEnabled(false);

        disableRoutesDisplay();
        resetActions();
        repaint();
    }

    private ActionButton createSidebarButton(String text, String cmd) {
        ActionButton b = new ActionButton(RailsIcon.OK);
        b.setText(text);
        b.setIcon(null);
        b.setActionCommand(cmd);
        b.addActionListener(this);
        b.setEnabled(false);
        b.setHorizontalAlignment(SwingConstants.CENTER);
        b.setFont(BTN_FONT);

        // --- FIX: Alignment for BoxLayout ---
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        // ------------------------------------

        b.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, BTN_HEIGHT));
        b.setMaximumSize(new Dimension(SIDEBAR_WIDTH - 20, BTN_HEIGHT)); // Enforce Max Height
        b.setMargin(new Insets(2, 2, 2, 2));
        return b;
    }

    public void setTileBuildNumbers(boolean show) {
        // Always update logic to ensure map numbers refresh when the company changes.
        // We removed the "if (state != show)" check because map data changes between
        // turns.
        this.showNumbersActive = show;

        if (btnBuildShow != null) {
            btnBuildShow.setText(showNumbersActive ? "Hide" : "Show");
        }

        if (orUIManager != null) {
            orUIManager.updateHexBuildNumbers(showNumbersActive);
        }
        repaint();
    }

    public void toggleTileBuildNumbers() {
        setTileBuildNumbers(!this.showNumbersActive);
    }

    // --- Helper: Create Panel with Titled Border ---
    private JPanel createPhasePanel(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), title,
                TitledBorder.LEFT, TitledBorder.TOP, FONT_HEADER));
        p.setOpaque(false);
        p.setBackground(BG_NORMAL);
        p.setAlignmentX(Component.CENTER_ALIGNMENT);
        return p;
    }

    // --- Helper: Smaller Readout ---
    private JLabel createReadoutLabel(String startText) {
        JLabel lbl = new JLabel(startText, SwingConstants.CENTER);
        lbl.setOpaque(true);
        lbl.setBackground(BG_READOUT);
        lbl.setForeground(Color.BLACK);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        lbl.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        lbl.setMaximumSize(new Dimension(1000, 24));
        return lbl;
    }

    private void applyPanelStyle(JPanel p, boolean active) {
        if (p == null)
            return;
        // Change Background: Mauve (Transparent) if inactive, Highlight (Opaque) if
        // active
        if (active) {
            p.setOpaque(true);
            p.setBackground(BG_HIGHLIGHT);
        } else {
            p.setOpaque(false); // Let Mauve show through
        }

        // Thicker border if active
        Border line = active
                ? BorderFactory.createLineBorder(Color.ORANGE, 2)
                : BorderFactory.createLineBorder(Color.GRAY, 1);

        if (p.getBorder() instanceof TitledBorder) {
            ((TitledBorder) p.getBorder()).setBorder(line);
        }
        p.repaint();
    }

    private JLabel createProminentLabel(String startText) {
        JLabel lbl = new JLabel(startText, SwingConstants.CENTER);
        lbl.setOpaque(true);
        lbl.setBackground(BG_DETAILS); // Default to Light Green
        lbl.setForeground(FG_READOUT);
        lbl.setFont(FONT_READOUT);
        lbl.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        // Fixed Height 26px for compactness
        lbl.setMaximumSize(new Dimension(SIDEBAR_WIDTH - 10, 26));
        lbl.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 10, 26));
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        return lbl;
    }

    private void initSidebar() {
        sidebarPanel = new JPanel();
        sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));
        sidebarPanel.setPreferredSize(new Dimension(SIDEBAR_WIDTH, SIDEBAR_HEIGHT));
        sidebarPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        // Set the Main Sidebar Background to Mauve
        sidebarPanel.setOpaque(true);
        sidebarPanel.setBackground(BG_DETAILS);

        // --- 1. HEADER (Logo) ---
        companyLogo = new JLabel("Stock Round", SwingConstants.CENTER);
        companyLogo.setOpaque(true);
        companyLogo.setFont(new Font("SansSerif", Font.BOLD, 14));
        companyLogo.setBackground(Color.LIGHT_GRAY);
        companyLogo.setForeground(Color.DARK_GRAY);

        Dimension logoSize = new Dimension(SIDEBAR_WIDTH, 90);
        companyLogo.setMinimumSize(logoSize);
        companyLogo.setPreferredSize(logoSize);
        companyLogo.setMaximumSize(logoSize);
        companyLogo.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebarPanel.add(companyLogo);

        sidebarPanel.add(Box.createVerticalStrut(2));

        // --- 2. GLOBAL HUD (Cash) ---
        // 1. Treasury in a Panel (Like others, but never active)
        JPanel treasuryPanel = createPhasePanel("Treasury");
        this.cashPanel = treasuryPanel; // Capture reference so we can restore visibility later
        // Force opaque Mauve background for the "content" area if needed,
        // but since we want the seamless look, transparency is usually best unless
        // highlighting.
        // However, user said "just mauve", so we keep it transparent to show sidebar
        // BG.
        treasuryPanel.add(Box.createVerticalStrut(5));

        lblCash = new JLabel("-", SwingConstants.CENTER);
        lblCash.setFont(FONT_READOUT);
        lblCash.setForeground(FG_READOUT);
        lblCash.setOpaque(true);
        lblCash.setBackground(BG_DETAILS); // Mauve background
        lblCash.setBorder(null); // 2. No Black Frame
        lblCash.setAlignmentX(Component.CENTER_ALIGNMENT);
        // Add to panel
        treasuryPanel.add(lblCash);
        sidebarPanel.add(treasuryPanel);

        treasuryPanel.add(Box.createVerticalStrut(5)); // Bottom padding
        // sidebarPanel.add(Box.createVerticalStrut(4));

        // --- 2b. SPECIAL CONTAINER (The Fix) ---
        // Initialize the container for Special Actions (Prussian, Private Auctions)
        // and add it to the stack so it exists in the layout.
        specialContainer = new JPanel(new BorderLayout());
        specialContainer.setOpaque(false);
        specialContainer.setVisible(false); // Hidden by default

        // Optional: Add a border to make it distinct
        specialContainer.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.RED), "Special / Decision",
                TitledBorder.LEFT, TitledBorder.TOP, FONT_HEADER));

        specialPanel = new JPanel();
        specialPanel.setLayout(new BoxLayout(specialPanel, BoxLayout.Y_AXIS));
        specialPanel.setOpaque(false);

        specialContainer.add(specialPanel, BorderLayout.CENTER);

        // Add it to the sidebar stack BEFORE Phase 1
        sidebarPanel.add(specialContainer);
        sidebarPanel.add(Box.createVerticalStrut(2));

        // --- 3. PHASE 1: TRACK ---
phase1Panel = createPhasePanel("1. Build Track");
        // Clean implementation: Single Smart Button
        btnTileConfirm = createSidebarButton("Skip", CONFIRM_CMD); // Default to Skip state
        phase1Panel.add(btnTileConfirm);

        // Initialize the panel for dynamic/special actions
        miscActionPanel = new JPanel();
        miscActionPanel.setLayout(new BoxLayout(miscActionPanel, BoxLayout.Y_AXIS));
        miscActionPanel.setOpaque(false);
        phase1Panel.add(Box.createVerticalStrut(2));
        phase1Panel.add(miscActionPanel);

        sidebarPanel.add(phase1Panel);
        sidebarPanel.add(Box.createVerticalStrut(2));

        // --- 4. PHASE 2: TOKEN ---
        phase2Panel = createPhasePanel("2. Place Token");

        // Token Readout
        tokenDisplay = new TokenDisplayPanel();
        tokenDisplay.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 10, 30));
        tokenDisplay.setMaximumSize(new Dimension(SIDEBAR_WIDTH - 10, 30));
        tokenDisplay.setAlignmentX(Component.CENTER_ALIGNMENT);
        // Ensure BG matches the requested Mauve
        tokenDisplay.setBackground(BG_DETAILS);
        phase2Panel.add(tokenDisplay);

        phase2Panel.add(Box.createVerticalStrut(2));
        btnTokenConfirm = createSidebarButton("Skip", CONFIRM_CMD);
        phase2Panel.add(btnTokenConfirm);
        sidebarPanel.add(phase2Panel);
        sidebarPanel.add(Box.createVerticalStrut(2));

        // --- 5. PHASE 3: REVENUE ---
phase3Panel = createPhasePanel("3. Manage Revenue"); // Renamed from "3. Revenue"

        // Revenue Readout
        lblRevenue = new JLabel("0", SwingConstants.CENTER);
        lblRevenue.setFont(FONT_READOUT); // 4. Bigger Font
        lblRevenue.setOpaque(true);
        lblRevenue.setBackground(BG_DETAILS);
        lblRevenue.setBorder(null); // 4. No Black Frame

        // Fix height to prevent jumping
        lblRevenue.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, 30));
        lblRevenue.setMaximumSize(new Dimension(SIDEBAR_WIDTH - 20, 30));
        lblRevenue.setAlignmentX(Component.CENTER_ALIGNMENT);
        phase3Panel.add(lblRevenue);

        phase3Panel.add(Box.createVerticalStrut(2));
        btnRevPayout = createSidebarButton("Payout", PAYOUT_CMD);
        phase3Panel.add(btnRevPayout);
        phase3Panel.add(Box.createVerticalStrut(2));
        btnRevWithhold = createSidebarButton("Withhold", WITHHOLD_CMD);
        phase3Panel.add(btnRevWithhold);
        phase3Panel.add(Box.createVerticalStrut(2));
        btnRevSplit = createSidebarButton("Split", SPLIT_CMD);
        phase3Panel.add(btnRevSplit);

        sidebarPanel.add(phase3Panel);
        sidebarPanel.add(Box.createVerticalStrut(2));

        // --- 6. PHASE 4: TRAINS ---
// 1. Owned Trains Display (Top)
phase4Panel = createPhasePanel("4. Trains / Buy");
        trainDisplay = new TrainDisplayPanel();
        phase4Panel.add(trainDisplay);

        // 2. Separator
        phase4Panel.add(Box.createVerticalStrut(5));
        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setMaximumSize(new Dimension(SIDEBAR_WIDTH - 10, 2));
        phase4Panel.add(sep);
        phase4Panel.add(Box.createVerticalStrut(5));

        // 3. Container for Buyable Trains (Middle)
        trainButtonsPanel = new JPanel();
        trainButtonsPanel.setLayout(new BoxLayout(trainButtonsPanel, BoxLayout.Y_AXIS));
        trainButtonsPanel.setOpaque(false);
        phase4Panel.add(trainButtonsPanel);

        // 4. Skip Button (Bottom)
        phase4Panel.add(Box.createVerticalStrut(5));
        btnTrainSkip = createSidebarButton("Skip Buy", TRAIN_SKIP_CMD);
        phase4Panel.add(btnTrainSkip);
        
        // Remove fixed height constraint so panel expands naturally with content
        phase4Panel.setMaximumSize(new Dimension(SIDEBAR_WIDTH, 1000));
        phase4Panel.setPreferredSize(null); 

        sidebarPanel.add(phase4Panel);
        sidebarPanel.add(Box.createVerticalStrut(4));


        // --- 7. FOOTER ---
        footerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        footerPanel.setOpaque(false); // Transparent to show Mauve

        btnDone = createSidebarButton("Done", DONE_CMD);
        btnDone.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnDone.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 10, 35));
        footerPanel.add(btnDone);

        // 3. Remove Glue to reduce empty space below
        sidebarPanel.add(Box.createVerticalStrut(10)); // Small gap instead of Glue
        sidebarPanel.add(footerPanel);
        sidebarPanel.add(Box.createVerticalStrut(5));
    }

    private void updateSidebarData() {
        // 1. Check & Clear (Redundant safety)
        if (orComp == null) {
            if (lblCash != null)
                lblCash.setText("-");
            return;
        }

        // 2. Determine Phase Info for Header
        Color phaseColor = PH_DONE_BG;
        String instruction = "Done?";

if (activePhase == 1) {
            phaseColor = PH_TILE_DARK;
            instruction = "BUILD TRACK"; // Renamed from "LAY TRACK"

        } else if (activePhase == 2) {
            phaseColor = PH_TOKEN_DARK;
            instruction = "PLACE TOKEN"; // Renamed from "LAY TOKEN"
        } else if (activePhase == 3) {
            phaseColor = PH_REV_DARK;
            instruction = " MANAGE REVENUE ";
        } else if (activePhase == 4) {
            // Header prompts mouse interaction with cards
            phaseColor = getTrainHighlightColor(); // Returns Color.ORANGE
            instruction = "   BUY TRAIN?   ";
        } else if (activePhase == 5) {
            // Only Phase 5 gets the Crimson Red "FINALIZE MOVE"
            phaseColor = new Color(180, 0, 0);
            instruction = " FINALIZE MOVE ";
        } else if (discardMode) {
            phaseColor = new Color(220, 20, 60); // Crimson/Red
            instruction = " DISCARD TRAIN! ";
        } else if (specialMode) {
            phaseColor = new Color(255, 180, 180);
            instruction = " SPECIAL ACTION ";
        }

      // 3. Build the Split Header (Identity + Action)
        if (companyLogo != null) {
            String hexBg = String.format("#%06x", (0xFFFFFF & orComp.getBgColour().getRGB()));
            String hexFg = String.format("#%06x", (0xFFFFFF & orComp.getFgColour().getRGB()));
            String hexPhase = String.format("#%06x", (0xFFFFFF & phaseColor.getRGB()));

            String playerInfo = (orComp.getPresident() != null) ? orComp.getPresident().getName() : "";

            // HTML Table: Top=Identity, Bottom=Action
            // Added cellpadding='4' to give the text breathing room
            // Added &nbsp; around instruction for extra horizontal padding
            String logoText = "<html><table width='100%' cellpadding='0' cellspacing='0'>" +
                    "<tr bgcolor='" + hexBg + "'>" +
                    "<td align='center' valign='center' height='55'>" +
                    "<font color='" + hexFg + "' size='5'><b>" + orComp.getId() + "</b></font><br>" +
                    "<font color='" + hexFg + "' size='3'>" + playerInfo + "</font>" +
                    "</td></tr>" +
                    "<tr bgcolor='" + hexPhase + "'>" +
                    "<td align='center' valign='center' height='28'>" + // Slightly taller
                    "<font color='white' size='4'><b>&nbsp;&nbsp;" + instruction + "&nbsp;&nbsp;</b></font>" + 
                    "</td></tr>" +
                    "</table></html>";
            companyLogo.setText(logoText);
            companyLogo.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
            companyLogo.setVisible(true);
        }

        // 4. Update Colors (Logic handles the panels internally)
        colorizeActivePhase(null);

        // 5. Update Data Fields (Text Only - Backgrounds handled by
        // colorizeActivePhase)
        Color infoFg = FG_READOUT;

        if (lblCash != null) {
            lblCash.setText(format(orComp.getPurseMoneyModel().value()));
            lblCash.setForeground(infoFg);
        }

        if (tokenDisplay != null) {
            int available = 0;
            if (orComp.getAllBaseTokens() != null) {
                for (net.sf.rails.game.BaseToken t : orComp.getAllBaseTokens()) {
                    if (!t.isPlaced())
                        available++;
                }
            }
            tokenDisplay.setTokens(available, orComp);
        }

        if (lblRevenue != null) {
            int val = orComp.getLastRevenue();
            lblRevenue.setText(format(val));
            lblRevenue.setForeground(infoFg);
        }

        // Restore the logic that passes the text list of trains + limit
        if (trainDisplay != null) {
            trainDisplay.updateAssets(orComp);
        }

        if (sidebarPanel != null) {
            sidebarPanel.revalidate();
            sidebarPanel.repaint();
        }
    }

    private void setupHotkeys() {

        // Use WHEN_IN_FOCUSED_WINDOW so hotkeys work even if the Map has focus
        InputMap inputMap = this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = this.getActionMap();

        // S: Cycle Hexes (Clockwise)
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), "cycleHexCW");
        actionMap.put("cycleHexCW", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cycleHexes(1);
            }
        });

        // D: Cycle Hexes (Anti-Clockwise) OR Done
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "cycleHexACW");
        actionMap.put("cycleHexACW", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 1. Try to cycle hexes first
                if (cycleableHexes != null && !cycleableHexes.isEmpty()) {
                    cycleHexes(-1);
                }
                // 2. If no hexes to cycle, trigger DONE
                else if (btnDone != null && btnDone.isEnabled()) {
                    btnDone.doClick();
                }
            }
        });
        // Space: Explicitly triggers DONE
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "doneAction");
        actionMap.put("doneAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (btnDone != null && btnDone.isEnabled()) {
                    btnDone.doClick();
                }
            }
        });

    }

    /**
     * Public accessor for the IPO Buy logic, triggered by ORWindow hotkeys.
     */
    public void processIPOBuy() {

        if (!availableTrainActions.isEmpty()) {
            for (BuyTrain action : availableTrainActions) {
                String label = action.getButtonLabel();
                // Heuristic: IPO trains usually don't mention "Pool" or "Discard"
                boolean isPool = (label != null
                        && (label.toLowerCase().contains("pool") || label.toLowerCase().contains("discard")));

                if (!isPool) {
                    List<PossibleAction> toExec = new ArrayList<>();
                    toExec.add(action);
                    orUIManager.processAction(BUY_TRAIN_CMD, toExec, ORPanel.this);
                    break; // Buy only one
                }
            }
        }

    }

    private class TrainDisplayPanel extends JPanel {
        // Visual Constants
        private final Dimension DIM_TRAIN_CARD = new Dimension(60, 40);
        private final Color BG_CARD_PASSIVE = new Color(255, 255, 240); // Beige

        // Dummy group to satisfy ClickField constructor constraints
        private final ButtonGroup dummyGroup = new ButtonGroup();

        public TrainDisplayPanel() {
            setOpaque(false);
            setLayout(new GridBagLayout()); // Center the vertical stack
            setBorder(null);
        }

        // Method moved INSIDE the class
        public void updateAssets(PublicCompany comp) {
            removeAll();
            if (comp == null)
                return;

            // 1. Parse Trains
            String trainString = comp.getPortfolioModel().getTrainsModel().toText();
            int limit = comp.getCurrentTrainLimit();

            List<String> trains = new ArrayList<>();
            if (trainString != null && !trainString.isEmpty() && !trainString.equals("None")
                    && !trainString.equals("-")) {
                String[] split = trainString.split("[,\\s]+");
                for (String s : split) {
                    if (!s.trim().isEmpty())
                        trains.add(s.trim());
                }
            }

            // 2. Build Vertical Stack
            JPanel stack = new JPanel(new GridLayout(0, 1, 0, 5)); // 1 Column, 5px Gap
            stack.setOpaque(false);

            int totalSlots = Math.max(limit, Math.max(trains.size(), 1));

            // 3. Render Trains and Empty Slots
            for (int i = 0; i < totalSlots; i++) {
                // Pass 'dummyGroup' to avoid NPE
                RailCard card = new RailCard((net.sf.rails.game.Train) null, dummyGroup);

                // REMOVED: card.setScale(1.3); -> Use Standard Size
                // REMOVED: card.setPreferredSize(null);

                card.setCompactMode(true);
                card.setOpaque(true);

                if (i < trains.size()) {
                    // Active Train Card
                    String text = trains.get(i);
                    card.setCustomLabel(text);
                    card.setBackground(BG_CARD_PASSIVE);
                    card.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(Color.BLACK, 1),
                            BorderFactory.createEmptyBorder(1, 1, 1, 1)));
                } else {
                    // Empty Slot (Placeholder)
                    card.setCustomLabel("_");
                    card.setBackground(new Color(240, 240, 240));
                    card.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(Color.GRAY, 1),
                            BorderFactory.createEmptyBorder(1, 1, 1, 1)));
                    card.setForeground(Color.GRAY);
                }
                stack.add(card);
            }

            // 4. Render Privates
            for (PrivateCompany pc : comp.getPrivates()) {
                RailCard card = new RailCard(pc, dummyGroup);
                // REMOVED: card.setScale(1.3); -> Use Standard Size
                card.setCompactMode(true);
                card.setOpaque(true);
                card.setBackground(new Color(255, 235, 235)); // Pinkish for Privates
                stack.add(card);
            }

            add(stack);
            revalidate();
            repaint();
        }

        // Legacy stub
        public void setTrains(String s, int i) {
        }
    }

    /**
     * * Visual display for Tokens (Updated to use TokenIcon)
     * Replaces the old "paintComponent" dots with actual Game Tokens.
     */
    private class TokenDisplayPanel extends JPanel {
        // Standard Sidebar width is ~190, so we can fit ~5 tokens comfortably
        private final int ICON_DIAMETER = 24;

        public TokenDisplayPanel() {
            setOpaque(true);
            setBackground(BG_DETAILS); // Match Sidebar Mauve
            setBorder(null);
            // FlowLayout allows tokens to center themselves dynamically
            setLayout(new FlowLayout(FlowLayout.CENTER, 5, 2));
        }

        /**
         * The NEW method required to fix the compiler error.
         * Accepts PublicCompany to generate the correct Icon with Text.
         */
        public void setTokens(int count, PublicCompany company) {
            removeAll(); // Clear old components

            if (company != null && count > 0) {
                Color bgColor = company.getBgColour();
                Color fgColor = company.getFgColour();
                String idText = company.getId();

                for (int i = 0; i < count; i++) {
                    // Create the official token look using TokenIcon
                    // Ensure TokenIcon is imported from net.sf.rails.ui.swing.elements
                    TokenIcon tokenIcon = new TokenIcon(
                            ICON_DIAMETER,
                            fgColor,
                            bgColor,
                            idText);

                    JLabel iconLabel = new JLabel(tokenIcon);
                    iconLabel.setToolTipText("Available Token");
                    iconLabel.setText(""); // No text needed outside the icon

                    add(iconLabel);
                }
            }

            revalidate();
            repaint();
        }

        // DEPRECATED Compatibility Method (Fixes legacy calls if any exist)
        // This ensures the compiler finds a match even if old code calls it,
        // though it won't render icons without the company object.
        public void setTokens(int count, Color color) {
            // Fallback: If we have a stored company reference, use it.
            // Otherwise, just clear.
            if (ORPanel.this.orComp != null) {
                setTokens(count, ORPanel.this.orComp);
            } else {
                removeAll();
                revalidate();
                repaint();
            }
        }
    }

    public void setCustomHeader(String title, String subtitle) {
        if (companyLogo != null) {
            String logoText = "<html><center>" +
                    "<font size='5'>" + title + "</font><br>" +
                    "<font size='4'>" + subtitle + "</font>" +
                    "</center></html>";
            companyLogo.setText(logoText);
            companyLogo.setBackground(BG_DETAILS);
            companyLogo.setForeground(Color.BLACK);
            companyLogo.setVisible(true);
            companyLogo.revalidate();
            companyLogo.repaint();
        }
        if (sidebarPanel != null) {
            sidebarPanel.revalidate();
            sidebarPanel.repaint();
        }
    }

    public static void setGlobalCustomHeader(String title, String subtitle) {
        SwingUtilities.invokeLater(() -> {
            for (ORPanel panel : activeInstances) {
                panel.setCustomHeader(title, subtitle);
            }
        });
    }

    private void colorizeActivePhase(Color unusedColor) {
        // Note: We ignore the passed color and use our defined palette to ensure
        // consistency.

        // 1. Reset everything to "Passive Mode" first
        resetPhasePanel(phase1Panel, btnTileConfirm);
        resetPhasePanel(phase2Panel, btnTokenConfirm);

        // Explicitly reset secondary buttons to prevent "Ghost Highlighting"
        // (e.g., Split remaining Blue when switching from Minor -> Major where Split is
        // disabled)
        resetPhasePanel(phase3Panel, btnRevPayout);
        resetButtonStyle(btnRevWithhold);
        resetButtonStyle(btnRevSplit);

        resetPhasePanel(phase4Panel, btnTrainSkip);
        resetPhasePanel(null, btnDone); // Reset Done button specifically

        // 2. Apply "Active Mode" to the current phase
        if (activePhase == 1) {
            // Style the Panel (Pink/Red to indicate Track Phase identity)
            // Pass 'null' for the button so we can style it manually below
            applyPhaseStyle(phase1Panel, null, PH_TILE_DARK, PH_TILE_LIGHT, "Confirm Track (Enter)");
            // Style the Button: SYSTEM BLUE (Uniform Action Color)
            // This replaces the old Red button, solving the "Red = Sell" conflict
            styleButton(btnTileConfirm, SYS_BLUE, "Confirm Track (Enter)");
        } else if (activePhase == 2) {

            // Style Panel: Forest Green Identity, Light Green Background (was BG_DETAILS)
            applyPhaseStyle(phase2Panel, null, PH_TOKEN_DARK, PH_TOKEN_LIGHT, "Confirm Token (Enter)");

            // Style Button: SYSTEM BLUE (Uniform Action Color)
            styleButton(btnTokenConfirm, SYS_BLUE, "Confirm Token (Enter)");
        } else if (activePhase == 3) {

            // Special handling for Revenue:
            // We must determine which button is the ACTUAL primary default.
            // 1837 Coal Companies have Payout DISABLED and Split ENABLED.
            // We must visually highlight Split in that case.

            ActionButton primaryBtn = btnRevPayout; // Default assumption
            String label = "Payout (Enter)";

            // If Payout is disabled but Split is enabled, Split becomes the primary visual.
            if ((btnRevPayout == null || !btnRevPayout.isEnabled())
                    && (btnRevSplit != null && btnRevSplit.isEnabled())) {
                primaryBtn = btnRevSplit;
                label = "Split (Enter)";
            }

            // 1. Style the Panel: Use Revenue Blue (Identity) for Border
            // Pass 'null' for the button so we don't paint it Revenue Blue
            applyPhaseStyle(phase3Panel, null, PH_REV_DARK, PH_REV_LIGHT, label);

            // 2. Style the Buttons: Primary gets SYSTEM BLUE (Action)
            if (primaryBtn == btnRevSplit) {
                // Split is King
                styleButton(btnRevSplit, SYS_BLUE, "Split (Enter)");
                styleSecondaryButton(btnRevPayout, PH_REV_LIGHT); 
            } else {
                // Payout is King (Standard Major)
                styleButton(btnRevPayout, SYS_BLUE, "Payout (Enter)");
                styleSecondaryButton(btnRevSplit, PH_REV_LIGHT);
            }


            // Withhold is always secondary
            styleSecondaryButton(btnRevWithhold, PH_REV_LIGHT);

        } else if (activePhase == 4) {
            Color trainOrange = getTrainHighlightColor();

            // 1. Highlight the Train panel in Orange
            boolean canBuy = (trainButtonsPanel != null && trainButtonsPanel.getComponentCount() > 0);
            String label = canBuy ? "Skip Buy (Enter)" : "Done Buying (Enter)";
            
            // Apply style to the Panel, but NOT the skip button (pass null)
            // This ensures the panel is Orange, but the button doesn't get overwritten
            applyPhaseStyle(phase4Panel, null, trainOrange, PH_TRAIN_LIGHT, label);

            // 2. Style the Skip Button: SYSTEM BLUE (Default Action)
            // This sets the skip button to the standard Blue confirmation color
            styleButton(btnTrainSkip, SYS_BLUE, label);

            // // 3. Keep the final Done button gray/passive during phase 4
            // styleButton(btnDone, Color.GRAY, "Done");
            // btnDone.setForeground(Color.BLACK);
            
        } else if (activePhase == 5) {
            // Turn off highlighting for Phase 4
            resetPhasePanel(phase4Panel, btnTrainSkip);

            // Only now do we show the dramatic Red button
            styleButton(btnDone, new Color(180, 0, 0), "END TURN");
            btnDone.setForeground(Color.WHITE);
            btnDone.setFont(new Font("SansSerif", Font.BOLD, 16));
        } else {
            // General Fallback -> SYSTEM BLUE
            styleButton(btnDone, SYS_BLUE, "Done (Spc)");
            btnDone.setForeground(Color.WHITE);
        }
    }

    // --- HELPER: RESET ---
    private void resetPhasePanel(JPanel p, ActionButton mainBtn) {
        if (p != null) {
            p.setOpaque(false); // Transparent to show main sidebar Mauve
            p.setBackground(BG_NORMAL);
            if (p.getBorder() instanceof TitledBorder) {
                ((TitledBorder) p.getBorder()).setTitleColor(Color.GRAY);
                ((TitledBorder) p.getBorder()).setBorder(BorderFactory.createLineBorder(Color.GRAY));
            }
            // Reset children (Labels) to Mauve
            for (Component c : p.getComponents()) {
                if (c instanceof JLabel) {
                    c.setBackground(BG_DETAILS); // Match Sidebar
                }
            }
        }
        if (mainBtn != null) {
            resetButtonStyle(mainBtn);
        }
    }

    // --- HELPER: APPLY STYLE ---
    private void applyPhaseStyle(JPanel p, ActionButton mainBtn, Color dark, Color light, String btnLabel) {
        if (p == null)
            return;

        // 1. Panel Background & Border
        p.setOpaque(true);
        p.setBackground(light); // The pastel color

        if (p.getBorder() instanceof TitledBorder) {
            ((TitledBorder) p.getBorder()).setTitleColor(dark); // Title matches theme
            ((TitledBorder) p.getBorder()).setBorder(BorderFactory.createLineBorder(dark, 2));
        }

        // 2. Style the Main Action Button (Strong Color)
        if (mainBtn != null && mainBtn.isEnabled()) {
            styleButton(mainBtn, dark, btnLabel);
        }

        // 3. CRITICAL FIX: Update Labels/Children to blend in
        // This prevents the "Gray Box" effect by making children match the light
        // background.
        for (Component c : p.getComponents()) {
            if (c instanceof JLabel) {
                // Readout fields (Cash, Revenue) stay White or Light for contrast
                c.setBackground(Color.WHITE);
            } else if (c instanceof JPanel) {
                c.setBackground(light); // Sub-panels match
                if (c == trainButtonsPanel) {
                     for (Component card : ((JPanel)c).getComponents()) {
                         // Keep the train buttons looking like "White Cards" against the colored phase background
                         if (card instanceof ActionButton) {
                             // Do not change card background here, they have their own style
                         }
                     }
                }
            }
        }

        p.repaint();
    }

    // Helper to make secondary buttons (Withhold/Split) look nice but less
    // prominent
    private void styleSecondaryButton(ActionButton btn, Color bgLight) {
        if (btn == null || !btn.isEnabled())
            return;
        btn.setBackground(Color.WHITE);
        btn.setForeground(Color.BLACK);
        btn.setBorder(BorderFactory.createLineBorder(Color.GRAY));

    }

    /**
     * Updates the Confirm/Skip button based on whether a map selection exists.
     * Called by ORUIManager and HexMap.
     */
    public void enableConfirm(boolean hasSelection) {
        // 1. Determine which button is active
        ActionButton targetBtn = (activePhase == 1) ? btnTileConfirm : (activePhase == 2) ? btnTokenConfirm : null;

        if (targetBtn == null)
            return;

        // 2. Configure the Button State
        targetBtn.setEnabled(true); // Always enabled! (Unless phase is locked)

        if (hasSelection) {
            // STATE: CONFIRM (User has done something on the map)
            // Use the DARK variant for the button background
            Color phaseColor = (activePhase == 1) ? PH_TILE_DARK : PH_TOKEN_DARK;
            styleButton(targetBtn, phaseColor, "Confirm (Enter)");
        } else {
            // STATE: SKIP (User has done nothing, "Enter" means Skip)
            styleButton(targetBtn, UIManager.getColor("Button.background"), "Skip (Enter)");
            targetBtn.setBorder(UIManager.getBorder("Button.border")); // Reset border
        }

        // 3. Focus Logic
        updateDefaultButton();
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    /**
     * Executes the current Undo action. Called by GlobalHotkeyManager.
     */
    public boolean executeUndo() {
        if (currentUndoAction != null && orUIManager != null) {
            orUIManager.processAction(UNDO_CMD, java.util.Collections.singletonList(currentUndoAction), this);
            return true;
        }
        return false;
    }

    /**
     * Executes the current Redo action. Called by GlobalHotkeyManager.
     */
    public boolean executeRedo() {
        if (currentRedoAction != null && orUIManager != null) {
            orUIManager.processAction(REDO_CMD, java.util.Collections.singletonList(currentRedoAction), this);
            return true;
        }
        return false;
    }

    public void updateDynamicActions(List<PossibleAction> actions) {

        // 1. Clean Setup
        cleanupUpgradesPanel();
        resetSidebarState();

        // ROBUST CONTEXT HANDOVER

        // 1. Identify the authoritative Operating Company from the Engine
        PublicCompany engineActiveComp = null;
        if (orUIManager != null && orUIManager.getGameUIManager().getGameManager() != null) {
            net.sf.rails.game.round.RoundFacade rf = orUIManager.getGameUIManager().getGameManager().getCurrentRound();
            if (rf instanceof net.sf.rails.game.OperatingRound) {
                engineActiveComp = ((net.sf.rails.game.OperatingRound) rf).getOperatingCompany();
            }
        }

        // 2. Check for Active Interruption (Discard)
        boolean isDiscardActionPresent = false;
        if (actions != null) {
            for (PossibleAction pa : actions) {
                if (pa instanceof DiscardTrain) {
                    isDiscardActionPresent = true;
                    break;
                }
            }
        }

        // 3. Resolve 'orComp' (The company displayed in the Sidebar)
        // PRIORITY: Discard Action -> Engine Active Company -> Stored Context

        if (isDiscardActionPresent) {
            // In Discard Mode, the action tells us who is acting (usually the company
            // discarding)
            for (PossibleAction pa : actions) {
                if (pa instanceof DiscardTrain) {
                    this.orComp = (PublicCompany) ((DiscardTrain) pa).getCompany();
                    this.discardMode = true;
                    break;
                }
            }
        } else {
            // NORMAL OPERATION

            // A. Try Engine (Source of Truth) - MUST BE OPEN
            if (engineActiveComp != null && !engineActiveComp.isClosed()) {
                this.orComp = engineActiveComp;
                this.currentOperatingComp = engineActiveComp; // Sync tracker

            } else if (this.currentOperatingComp != null && !this.currentOperatingComp.isClosed()) {
                // B. Fallback to Stored Context (if Engine is momentarily null but we have a
                // valid open company)
                this.orComp = this.currentOperatingComp;

            } else {
                // C. Emergency Fallback: If everything is null or closed, try to find ANY
                // operating company
                // This prevents the "Blank Panel" or "Stuck on Closed M1" issue.
                if (engineActiveComp != null) {
                    // If engine says M1 (Closed), we temporarily show it rather than crash,
                    // but we log it as suspicious.
                    this.orComp = engineActiveComp;
                }
            }
        }

        // Update data even if no actions (e.g. just viewing map)
        if (actions == null || actions.isEmpty()) {
            updateSidebarData();
            if (sidebarPanel != null)
                sidebarPanel.repaint();
            SwingUtilities.invokeLater(() -> {
                if (!(orUIManager.getGameUIManager().getCurrentRound() instanceof StartRound)) {
                    this.requestFocusInWindow();
                }
            });
            return;
        }

        // --- 2. FILTER SPECIAL ACTIONS (Restoring Logic from ORPanelOLD) ---
        List<PossibleAction> specialActions = new ArrayList<>();

        boolean isPrussianContext = actions.stream()
                .anyMatch(a -> a instanceof StartPrussian || a instanceof ExchangeForPrussianShare);
        boolean isDiscardContext = actions.stream().anyMatch(a -> a instanceof DiscardTrain);

        for (PossibleAction pa : actions) {
            // Restore original check: StartPrussian, Exchange, Discard, or HomeCity Token
            // (Baden)
            if ((pa instanceof StartPrussian) ||
                    (pa instanceof ExchangeForPrussianShare) ||
                    (pa instanceof DiscardTrain)) {
                specialActions.add(pa);
            }
            // STRICTER CHECK: Only treat "Home City" token lays as Special Actions.
            // Regular token lays (even with labels) must fall through to the normal Phase 2
            // logic.
            else if (pa instanceof LayBaseToken && ((LayBaseToken) pa).getType() == LayBaseToken.HOME_CITY) {
                specialActions.add(pa);
            }

            // Handle Done/Pass in Special Contexts (so they appear in the special menu, not
            // at the bottom)
            else if (pa instanceof NullAction) {
                NullAction na = (NullAction) pa;
                if ((!specialActions.isEmpty() || isPrussianContext || isDiscardContext)
                        && (na.getMode() == NullAction.Mode.DONE || na.getMode() == NullAction.Mode.PASS)) {
                    specialActions.add(pa);
                }
            }
        }
        // 2b. DETECT DISCARD & UPDATE CONTEXT
        // If we are in discard mode, find the company responsible and temporarily point
        // orComp to it
        // so the Sidebar Header displays the correct Company/Owner.
        this.discardMode = false;
        for (PossibleAction spa : specialActions) {
            if (spa instanceof DiscardTrain) {
                this.discardMode = true;
                PublicCompany subject = (PublicCompany) ((DiscardTrain) spa).getCompany();
                if (subject != null && subject != this.orComp) {
                    this.orComp = subject; // Context Switch for Display
                }
                break; // Found the discard context
            }
        }

        // --- 3. RENDER SPECIAL MODE (If applicable) ---
        if (!specialActions.isEmpty()) {
            this.specialMode = true;
            this.activePhase = 0; // Override standard phases

            // HIDE Standard Phase Panels
            if (phase1Panel != null)
                phase1Panel.setVisible(false);
            if (phase2Panel != null)
                phase2Panel.setVisible(false);
            if (phase3Panel != null)
                phase3Panel.setVisible(false);
            if (phase4Panel != null)
                phase4Panel.setVisible(false);
            if (footerPanel != null)
                footerPanel.setVisible(false);

            // Hide Sticky Treasury if it interferes (Optional, kept from OLD)
            if (lblCash != null && lblCash.getParent() != null) {
                // lblCash.getParent().setVisible(false); // Commented out to keep cash visible
            }

            // SHOW Special Panel
            if (specialPanel != null && specialContainer != null) {
                specialContainer.setVisible(true);
                specialPanel.removeAll();

                for (PossibleAction spa : specialActions) {

                    // Hide redundant buttons that are now driven by Status Window Cards.
                    // This forces the "Card UI" design language.
                    if (spa instanceof StartPrussian ||
                            spa instanceof ExchangeForPrussianShare ||
                            spa instanceof DiscardTrain) {
                        continue;
                    }

                    String labelText = spa.getButtonLabel();
                    if (spa instanceof NullAction) {
                        NullAction na = (NullAction) spa;
                        if (na.getMode() == NullAction.Mode.DONE)
                            labelText = "Done";
                        else if (na.getMode() == NullAction.Mode.PASS)
                            labelText = "Decline / Pass";
                    }

                    // Format nicely with HTML center
                    String htmlLabel = "<html><center>"
                            + (labelText != null ? labelText.replace(":", ":<br>") : "Action") + "</center></html>";

                    ActionButton b = createSidebarButton(htmlLabel, "Special");
                    b.setPossibleAction(spa);

                    // Custom Styling for Special Buttons
                    if (spa instanceof DiscardTrain) {
                        b.setActionCommand("Discard");
                        b.setBackground(new Color(255, 200, 200)); // RED for Discard
                        String cleanLabel = spa.getButtonLabel().replace("Company discards ", "").replace("'", "");
                        b.setText("<html><center>Discard<br>" + cleanLabel + "</center></html>");
                    } else if (spa instanceof NullAction) {
                        NullAction.Mode mode = ((NullAction) spa).getMode();
                        if (mode == NullAction.Mode.PASS)
                            b.setActionCommand(SKIP_CMD);
                        else
                            b.setActionCommand(DONE_CMD);
                        b.setBackground(new Color(200, 230, 255)); // Blue
                    } else {
                        b.setBackground(new Color(200, 230, 255)); // Blue
                    }

                    b.setEnabled(true);
                    // Use fixed height for consistency in special menu
                    b.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, 50));
                    b.setMaximumSize(new Dimension(SIDEBAR_WIDTH - 20, 50));

                    specialPanel.add(b);
                    specialPanel.add(Box.createVerticalStrut(5));
                }
                specialPanel.revalidate();
                specialPanel.repaint();
            }

            // Ensure focus returns to the panel for hotkeys
            SwingUtilities.invokeLater(this::requestFocusInWindow);

            // Exit early - do not render standard buttons
            return;
        }

        // --- 4. STANDARD MODE (Normal Gameplay) ---
        this.specialMode = false;

        // Hide Special Panel
        if (specialContainer != null)
            specialContainer.setVisible(false);

        // Restore Standard Panels
        if (phase1Panel != null)
            phase1Panel.setVisible(true);
        if (phase2Panel != null)
            phase2Panel.setVisible(true);
        if (phase3Panel != null)
            phase3Panel.setVisible(true);
        if (phase4Panel != null)
            phase4Panel.setVisible(true);
        if (footerPanel != null)
            footerPanel.setVisible(true);

        // Restore Sticky Treasury
        if (lblCash != null && lblCash.getParent() != null) {
            lblCash.getParent().setVisible(true);
        }

        // --- 5. PHASE DETECTION ---
        activePhase = 0;
        availableTrainActions.clear(); // Reset hotkey list
        boolean hasDoneAction = false; // Add tracking for Done

        for (PossibleAction pa : actions) {
            if (pa instanceof LayTile)
                activePhase = 1;
            else if (pa instanceof LayToken)
                activePhase = 2;
            else if (pa instanceof SetDividend)
                activePhase = 3;
            else if (pa instanceof BuyTrain)
                activePhase = 4;
            else if (pa instanceof NullAction) 
                hasDoneAction = true; // Track Done
        }

        // If we have no active phases (1-4) but we have a Done/Pass action,
        // specifically set Phase 5 to ensure the Big Red Button appears.
        // This fixes the "Stuck" feeling after a Forced Discard where the only
        // remaining action is Done.
        if (activePhase == 0 && actions.stream().anyMatch(a -> a instanceof NullAction)) {
            activePhase = 5;
            log.info("set active phase to 5");
        }

// If no specific phase was triggered (ActivePhase 0) but we have a Done action,
        // it means we are in Phase 5 (Finalize / End Turn).
        if (activePhase == 0 && hasDoneAction) {
            activePhase = 5;
        }
        // Fallback: If we somehow flagged Phase 4 but have no actual buyable trains (and no discard pending),
        // we should also fall through to Phase 5.
        else if (activePhase == 4 && actions.stream().noneMatch(a -> a instanceof BuyTrain) 
            && actions.stream().noneMatch(a -> a instanceof DiscardTrain)) {
            activePhase = 5;
        }
        

        // Map Interaction Defaults
        if (activePhase == 1 || activePhase == 2) {
            enableConfirm(false); // Reset until map click
        }

        // --- 6. POPULATE STANDARD BUTTONS ---
        for (PossibleAction pa : actions) {
            if (pa instanceof CorrectionModeAction)
                continue;

            // Phase 1: Special Properties (Private abilities)
            if (pa instanceof UseSpecialProperty) {
                ActionButton bSpecial = createSidebarButton(pa.getButtonLabel(), "SpecialProperty");
                bSpecial.setPossibleAction(pa);
                bSpecial.setEnabled(true);

                if (miscActionPanel != null) {
                    miscActionPanel.add(bSpecial);
                    miscActionPanel.add(Box.createVerticalStrut(2));
                }

                // Phase 3: Revenue
            } else if (pa instanceof SetDividend) {
                SetDividend sd = (SetDividend) pa;
                if (sd.isAllocationAllowed(SetDividend.PAYOUT))
                    enableRevenueBtn(btnRevPayout, sd, SetDividend.PAYOUT);
                if (sd.isAllocationAllowed(SetDividend.WITHHOLD))
                    enableRevenueBtn(btnRevWithhold, sd, SetDividend.WITHHOLD);
                if (sd.isAllocationAllowed(SetDividend.SPLIT))
                    enableRevenueBtn(btnRevSplit, sd, SetDividend.SPLIT);

                // Phase 4: Buy Train (Collect for Status Window & Hotkeys)
            } else if (pa instanceof BuyTrain) {
                availableTrainActions.add((BuyTrain) pa);
                // Add the visual "Train Card" button to the sidebar
                addTrainBuyButton((BuyTrain) pa);

                // Footer: Done / Skip
            } else if (pa instanceof NullAction) {
                NullAction.Mode mode = ((NullAction) pa).getMode();
                if (mode == NullAction.Mode.SKIP) {
                    // handled by confirm/skip logic mostly, but keep for robustness
                } else if (mode == NullAction.Mode.DONE || mode == NullAction.Mode.PASS) {
                    setupButton(btnDone, pa);
                }
            }
        }

        // --- 7. FINALIZE UI STATE ---
        updateSidebarData(); // Colors and headers

        // Send train actions to Status Window
        if (orUIManager != null && orUIManager.getGameUIManager() != null) {
            StatusWindow sw = orUIManager.getGameUIManager().getStatusWindow();
            if (sw != null && sw.getGameStatus() != null) {
                sw.getGameStatus().setTrainBuyingActions((List<PossibleAction>) (List<?>) availableTrainActions);
            }
        }

        // Manage Map Visuals
        if (activePhase == 1 || activePhase == 2) {
            setTileBuildNumbers(true);
            updateCurrentRoutes(false);
        } else if (activePhase == 3) {
            setTileBuildNumbers(false);
            if (orWindow != null && orWindow.getMapPanel() != null)
                orWindow.getMapPanel().clearOverlays();
            updateCurrentRoutes(true);
        } else {
            setTileBuildNumbers(false);
            if (orWindow != null && orWindow.getMapPanel() != null)
                orWindow.getMapPanel().clearOverlays();
            disableRoutesDisplay();

            // Ensure Skip/Done is enabled in Train Phase
            if (activePhase == 4 && btnTrainSkip != null) {
                btnTrainSkip.setEnabled(true);
            }
        }

        if (sidebarPanel != null)
            sidebarPanel.repaint();

        SwingUtilities.invokeLater(() -> {
            updateDefaultButton();
            this.requestFocusInWindow();
        });
    }

    // ... (inside ORPanel.java, scroll down to the addTrainBuyButton method)

// ... (lines 2660+ at the bottom of the file)

/**
     * Creates a "Railcard" style button: One Line, Light Green, Plain Text.
     */
    private void addTrainBuyButton(BuyTrain action) {
        if (trainButtonsPanel == null) return;

        ActionButton btn = new ActionButton(RailsIcon.BUY_TRAIN);
        
        // 1. Clean Text: "Buy 4 train from IPO for 360" -> "4 Train - IPO - 360"
        String raw = action.getButtonLabel();
        String text = raw;
        
        if (raw.contains(" for ")) {
            // Remove "Buy" and "train", replace prepositions with dashes
            text = raw.replace("Buy ", "")
                      .replace(" train", "")
                      .replace(" from ", " - ")
                      .replace(" for ", " - ");
        }
        
        // 2. Set Text (One Line, Normal Weight)
        btn.setText(text);
        btn.setIcon(null);
        
        // --- START FIX ---
        // 3. Styling: Beige Background + Green Border
        // Color bgGreen = new Color(225, 255, 225); // DELETE (Old Solid Green)
        
        Color borderGreen = new Color(34, 139, 34); // Forest Green
        
        btn.setBackground(CARD_BG); // BEIGE (Defined in ORPanel constants)
        btn.setOpaque(true);
        btn.setForeground(Color.BLACK);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 12)); 
        
        // Thick Green Border to indicate "Buyable"
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderGreen, 2), // 2px Green Border
            BorderFactory.createEmptyBorder(3, 5, 3, 5)     // Padding
        ));
        // --- END FIX ---

        // 4. Layout
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(SIDEBAR_WIDTH - 10, 30)); // Compact height
        btn.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 10, 30));
        
        btn.setPossibleAction(action);
        btn.addActionListener(this);
        
        trainButtonsPanel.add(btn);
        trainButtonsPanel.add(Box.createVerticalStrut(4));
    }
    
}