package net.sf.rails.ui.swing;

import net.sf.rails.algorithms.*;
import net.sf.rails.common.Config;
import net.sf.rails.common.GuiDef;
import net.sf.rails.game.*;
import net.sf.rails.game.round.RoundFacade;
import net.sf.rails.game.state.Owner;
import net.sf.rails.ui.swing.elements.*;
import net.sf.rails.ui.swing.hexmap.GUIHex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rails.game.action.*;
import rails.game.correct.CorrectionModeAction;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.util.Collections;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ORPanel extends GridPanel
        implements RevenueListener {

    private static final long serialVersionUID = 1L;
    protected static final Logger log = LoggerFactory.getLogger(ORPanel.class);

    // --- COMMAND CONSTANTS ---
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
    public static final String SHOW_CMD = "Show";
    public static final String TRAIN_SKIP_CMD = "TrainSkip";

    // --- VISUAL CONSTANTS ---
    private static final Color BG_DETAILS = new Color(235, 230, 255); // Standard Mauve
    private static final Color BG_SPECIAL_HEADER = new Color(255, 220, 220); // Light Red for Special
    private static final Color SYS_BLUE = new Color(30, 144, 255); // DodgerBlue

    // Phase Colors
    private static final Color PH_TILE_DARK = new Color(139, 69, 19);
    private static final Color PH_TOKEN_DARK = new Color(34, 139, 34);
    private static final Color PH_DONE_BG = UIManager.getColor("Panel.background");
    private static final Color BG_NORMAL = UIManager.getColor("Panel.background");
    private static final Font FONT_HEADER = new Font("SansSerif", Font.BOLD, 12);
    private static final int PANEL_ACTION_GAP = 8;

    private static final int BTN_HEIGHT = 28;
    private static final Font BTN_FONT = new Font("SansSerif", Font.PLAIN, 11);
    // --- COMPONENTS ---
    private ORWindow orWindow;
    private ORUIManager orUIManager;
    private JPanel sidebarPanel;

    // Standard Panels
private JPanel phase1Panel, phase2Panel, phase3Panel, phase4Panel, phase5Panel, footerPanel;
    private JPanel cashPanel;
    private JPanel loansPanel;
    private JPanel miscActionPanel;
    private JPanel trainButtonsPanel;
    private JPanel specialActionsButtonPanel;

    

    // Special Mode Panels
    private JPanel specialContainer;
    private JPanel specialPanel;

    // Decoupled Header Components (Replaces single companyLogo)
    private JLabel lblCompanyInfo;
    private JLabel lblPlayerInfo;
    private JLabel lblPhaseInstruction;

    // Legacy/Standard Buttons
    public ActionButton btnRevPayout, btnRevWithhold, btnRevSplit;
    public ActionButton btnDone;
    public ActionButton btnBuildShow;
    public ActionButton btnTrainSkip;
    public ActionButton btnTileSkip, btnTileConfirm;
    public ActionButton btnTokenSkip, btnTokenConfirm;
    public ActionButton buttonOC, button1, button2, button3; // Legacy placeholders
private JLabel focusLight;

    private GameAction currentUndoAction;
    private GameAction currentRedoAction;
    public ActionButton currentDefaultButton;

    public int activePhase = 0; // 1=Build, 2=Token, 3=Revenue, 4=Train, 5=Finalize/Done

    public List<GUIHex> cycleableHexes = new ArrayList<>();
    public int cycleIndex = -1;

    private PublicCompany[] companies;
    private int nc;
    private PublicCompany orComp = null;
    private PublicCompany currentOperatingComp = null;
    private int orCompIndex = -1;

    private boolean specialModeActive = false;
    private boolean isRevenueValueToBeSet = false;
    private boolean showNumbersActive = false;
    private AbstractButton directPassButton;

    // Game Params
    private boolean privatesCanBeBought;
    private boolean hasCompanyLoans;
    private boolean hasDirectCompanyIncomeInOR;
    private boolean bonusTokensExist;
    private boolean hasRights;

    private RevenueAdapter revenueAdapter = null;
    private Thread revenueThread = null;
    private List<JFrame> openWindows = new ArrayList<>();
    private List<BuyTrain> availableTrainActions = new ArrayList<>();

    // Sidebar Elements
    private JLabel companyLogo;
    private JLabel lblCash;
    private JLabel lblFixed;
    private JLabel lblLoans;
    private JLabel lblRoute;

    private TokenDisplayPanel tokenDisplay;
    private TrainDisplayPanel trainDisplay;
    private JPanel legendPanel;
    private JPanel specialNotificationPanel;

    private static final List<ORPanel> activeInstances = new ArrayList<>();

    /**
     * Centralized UI Theme for the Operating Round Sidebar.
     * Organizes colors by logical "Zones": Infrastructure, Capital, and Control.
     */
    public static class UITheme {
        // ZONE: Infrastructure (Map Actions)
        public static final Color TRACK_DARK = new Color(139, 69, 19); // Ochre
        public static final Color TRACK_LIGHT = new Color(255, 245, 235);
        public static final Color TOKEN_DARK = new Color(34, 139, 34); // Forest
        public static final Color TOKEN_LIGHT = new Color(210, 255, 210);

        // ZONE: Capital (Treasury Actions)
        public static final Color REVENUE_DARK = new Color(0, 60, 140); // Royal Blue
        public static final Color REVENUE_LIGHT = new Color(210, 230, 255);
        public static final Color TRAIN_DARK = new Color(204, 102, 0); // Industrial Orange
        public static final Color TRAIN_LIGHT = new Color(255, 235, 205);

        // ZONE: Control (Navigation & State)
        public static final Color ACTION_SKIP = new Color(30, 144, 255); // DodgerBlue
        public static final Color ACTION_DONE = new Color(180, 0, 0); // Warning Red
        public static final Color ACTION_DISCARD = new Color(220, 20, 60); // Crimson

        // General UI Components
        public static final Color BG_SIDEBAR = new Color(235, 230, 255); // Standard Mauve
        public static final Color BG_SPECIAL = new Color(255, 220, 220); // Light Red
        public static final Color BG_CARD = new Color(255, 255, 240); // Beige
        public static final Color READOUT_BG = Color.WHITE;
        public static final Color READOUT_FG = Color.BLACK;
    }

    public ORPanel(ORWindow parent, ORUIManager orUIManager) {
        super();
        activeInstances.add(this);
        setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));

        this.orWindow = parent;
        this.orUIManager = orUIManager;
        GameUIManager gameUIManager = parent.gameUIManager;

        gridPanel = new JPanel();
        parentFrame = parent;
        setFocusable(true);

        round = gameUIManager.getCurrentRound();

        if (round instanceof OperatingRound) {
            companies = ((OperatingRound) round).getOperatingCompanies().toArray(new PublicCompany[0]);
            nc = companies.length;
            this.orComp = ((OperatingRound) round).getOperatingCompany();
        } else {
            // Reflection hook to find BK in CoalExchangeRound
            try {
                java.lang.reflect.Method method = round.getClass().getMethod("getOperatingCompany");
                Object result = method.invoke(round);
                if (result instanceof PublicCompany) {
                    this.orComp = (PublicCompany) result;
                    this.currentOperatingComp = (PublicCompany) result;
                }
            } catch (Exception e) {
                // No company context available
            }
        }

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

        initButtonPanel(); // Legacy init
        setupHotkeys();
        setVisible(true);

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", evt -> {
            if (focusLight != null && orWindow != null) {
                Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                boolean hasFocus = owner != null && javax.swing.SwingUtilities.isDescendingFrom(owner, orWindow);
                focusLight.setForeground(hasFocus ? new java.awt.Color(34, 139, 34) : java.awt.Color.RED);
                focusLight.setText(hasFocus ? "●" : "●");
            }
        });


    }

    /**
     * Determines the current Operating Round phase based on the available actions.
     * 1=Tile, 2=Token, 3=Revenue, 4=Train, 5=Done/Finalize
     */
    private int determineActivePhase(List<PossibleAction> actions) {
        int phase = 0;
        boolean hasDoneAction = false;
boolean hasSpecialAction = false;

        if (actions == null || actions.isEmpty()) {
            return 0;
        }

       
        for (PossibleAction pa : actions) {
// --- START FIX ---
            if (pa instanceof LayTile && (phase == 0 || phase > 1)) {
                phase = 1;
            }
            else if (pa instanceof LayToken && (phase == 0 || phase > 2)) {
                phase = 2;
            }
            else if (pa instanceof SetDividend && (phase == 0 || phase > 3)) {
                phase = 3;
            }
            else if (pa instanceof BuyTrain && (phase == 0 || phase > 4)) {
                phase = 4;
            }
else if (pa instanceof TakeLoans || pa instanceof RepayLoans
                    || pa.getClass().getName().endsWith("TakeLoans_1817")) {
                hasSpecialAction = true;
            }
            else if (pa instanceof NullAction) {
                NullAction.Mode mode = ((NullAction) pa).getMode();
                if (mode == NullAction.Mode.DONE || mode == NullAction.Mode.PASS) {
                    hasDoneAction = true;
                }
            }

        }


if (phase == 0) {
            if (hasDoneAction) {
                phase = 6;
            } else if (hasSpecialAction) {
                phase = 5;
            }
        }

        return phase;
    }

    // ... (lines of unchanged context code) ...
    private void distributeStandardActions(List<PossibleAction> actions) {
        boolean doneActionFound = false;
        PossibleAction donePa = null;

        // --- START FIX ---
        // 1. DEDUPLICATION SET
        java.util.Set<String> addedSpecialLabels = new java.util.HashSet<>();

        // 2. CONSTANTS (Normalized Labels)
        final String LBL_TILE = "EXTRA TILE BUILD";
        final String LBL_TOKEN = "EXTRA TOKEN";

        for (PossibleAction pa : actions) {
            // IGNORE LIST: Structural actions that should never be special buttons
            if (pa instanceof CorrectionModeAction ||
                    pa instanceof GameAction) {
                continue;
            }

            String labelToAdd = null;

            // --- A. UseSpecialProperty (The Menu/Trigger) ---
            if (pa instanceof UseSpecialProperty) {
                String text = pa.getButtonLabel().toLowerCase();
                if (text.contains("tile"))
                    labelToAdd = LBL_TILE;
                else if (text.contains("token"))
                    labelToAdd = LBL_TOKEN;
                else
                    labelToAdd = pa.getButtonLabel().trim();
            }

            // --- B. LayTile (The Execution) ---
            else if (pa instanceof LayTile) {
                LayTile lt = (LayTile) pa;
                // ONLY show if it has a linked SpecialProperty object or explicit extra flag
                if (lt.getSpecialProperty() != null || pa.toString().contains("extra=true")) {
                    labelToAdd = LBL_TILE;
                }
            }

            // --- C. LayBaseToken (The Execution) ---
            else if (pa instanceof LayBaseToken) {
                LayBaseToken lbt = (LayBaseToken) pa;

                // STRICT FILTER BASED ON DEBUG ANALYSIS:
                // 1. Check for attached SpecialProperty (Debug confirmed Type 2 has SP=true)
                boolean hasSpecialProp = (lbt.getSpecialProperty() != null);

                // 2. Check for explicit "extra=true" flag
                boolean isExplicitlyExtra = pa.toString().contains("extra=true");

                // 3. Check for Special Types, BUT EXCLUDE TYPE 1 (Home City/Normal)
                // Type 0 = Generic, Type 1 = Home/Normal. Both are ignored.
                // Any other Type (2+) is considered special.
                boolean isSpecialType = (lbt.getType() != LayBaseToken.GENERIC && lbt.getType() != 1);

                if (hasSpecialProp || isExplicitlyExtra || isSpecialType) {
                    labelToAdd = LBL_TOKEN;
                }
            }

            // --- D. CATCH-ALL (The Safety Net) ---
            else if (!(pa instanceof SetDividend) &&
                    !(pa instanceof BuyTrain) &&
                    !(pa instanceof NullAction) &&
                    !(pa instanceof LayTile) &&
                    !(pa instanceof LayToken) &&
                    !(pa instanceof TakeLoans) &&
                    !(pa instanceof RepayLoans) &&
                    !pa.getClass().getName().endsWith("TakeLoans_1817") &&
                    !(pa instanceof LayBaseToken)) {

                labelToAdd = pa.getButtonLabel().toUpperCase();
            }

            // --- E. ADD BUTTON (Deduplicated) ---
            if (labelToAdd != null) {
                if (!addedSpecialLabels.contains(labelToAdd)) {
                    addSpecialNotificationButton(labelToAdd, pa);
                    addedSpecialLabels.add(labelToAdd);
                }
            }
if (pa instanceof TakeLoans || pa instanceof RepayLoans || pa.getClass().getName().endsWith("TakeLoans_1817")) {
                addSpecialActionButtonToPhase5(pa);
            }

            // Continue with standard distribution...
            if (pa instanceof SetDividend) {
                SetDividend sd = (SetDividend) pa;
                if (sd.isAllocationAllowed(SetDividend.PAYOUT))
                    enableRevenueBtn(btnRevPayout, sd, SetDividend.PAYOUT);
                if (sd.isAllocationAllowed(SetDividend.WITHHOLD))
                    enableRevenueBtn(btnRevWithhold, sd, SetDividend.WITHHOLD);
                if (sd.isAllocationAllowed(SetDividend.SPLIT))
                    enableRevenueBtn(btnRevSplit, sd, SetDividend.SPLIT);
            } else if (pa instanceof BuyTrain) {
                availableTrainActions.add((BuyTrain) pa);
                addTrainBuyButton((BuyTrain) pa);
            } else if (pa instanceof NullAction) {
                NullAction.Mode mode = ((NullAction) pa).getMode();
                if (mode == NullAction.Mode.DONE || mode == NullAction.Mode.PASS) {
                    setupButton(btnDone, pa);
                    bindActionHotkey(btnDone, pa);

                    donePa = pa;
                    doneActionFound = true;
                }
            }
        }
        if (doneActionFound && donePa != null) {
            bindActionHotkey(btnDone, donePa);
        }

    }

    private void updatePhaseSpecifics() {

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
            if (activePhase == 4 && btnTrainSkip != null)
                btnTrainSkip.setEnabled(true);
        }
    }


    private void addSpecialActionButtonToPhase5(PossibleAction action) {
        ActionButton btn = new ActionButton(RailsIcon.OK);
        String text = action.getButtonLabel();
        btn.setText(text);
        btn.setIcon(null);

        btn.setBackground(new Color(255, 255, 240)); 
        btn.setOpaque(true);
        btn.setForeground(Color.BLACK);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.BLACK, 1),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)));

        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(SIDEBAR_WIDTH - 10, 30));
        btn.setPossibleAction(action);
        btn.addActionListener(this);
        
        if (specialActionsButtonPanel != null) {
            specialActionsButtonPanel.add(btn);
            specialActionsButtonPanel.add(Box.createVerticalStrut(4));
        }
    }


    private void addSpecialNotificationButton(String text, PossibleAction sourceAction) {
        if (specialNotificationPanel == null)
            return;

        specialNotificationPanel.setVisible(true);

        ActionButton b = new ActionButton(RailsIcon.INFO); // Use Info icon or null
        b.setText(text);
        b.setIcon(null);
        b.setHorizontalAlignment(SwingConstants.CENTER);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, BTN_HEIGHT));
        b.setMaximumSize(new Dimension(SIDEBAR_WIDTH - 20, BTN_HEIGHT));

        // --- STYLING ---
        // High Visibility Gold/Orange
        Color bg = new Color(255, 193, 7); // Amber/Gold
        Color fg = Color.BLACK;

        // Force UI to ignore "Disabled" greying out
        b.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        b.setBackground(bg);
        b.setForeground(fg);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));

        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorderPainted(true);

        // Thick border to indicate "Special"
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(184, 134, 11), 2), // Dark Goldenrod
                BorderFactory.createEmptyBorder(2, 2, 2, 2)));

        // Functionally disabled (not clickable)
        b.setEnabled(false);

        // Optional: Add tooltip to explain
        if (sourceAction != null) {
            b.setToolTipText(sourceAction.toString());
        }

        specialNotificationPanel.add(b);
        specialNotificationPanel.add(Box.createVerticalStrut(4));
    }

    private void updateSpecialHeader(GuiTargetedAction context) {
        if (lblCompanyInfo == null || context == null)
            return;

        // 1. Extract Data correctly
        Owner actor = context.getActor();

        // TOP: The Company Name (ID)
        String companyName = (actor != null) ? actor.getId() : "Game";

        // MIDDLE: The Player Name (from our new interface method)
        String playerName = context.getPlayerName();

        // BOTTOM: The Action Text
        String actionTitle = context.getGroupLabel();

        // 2. Determine Colors (Company Styling for ALL panels)
        Color bg = BG_SPECIAL_HEADER;
        Color fg = Color.BLACK;

        if (actor instanceof PublicCompany) {
            bg = ((PublicCompany) actor).getBgColour();
            fg = ((PublicCompany) actor).getFgColour();
        }

        // 3. Update Components

        // TOP: Company Name
        lblCompanyInfo.setText("<html><center><font size='6'><b>" + companyName + "</b></font></center></html>");
        lblCompanyInfo.setBackground(bg);
        lblCompanyInfo.setForeground(fg);
        lblCompanyInfo.setVisible(true);

        // MIDDLE: Player Name
        // FIX: Increased font size from '3' to '6' and added bold
        if (lblPlayerInfo != null) {
            lblPlayerInfo.setText("<html><center><font size='6'><b>" + playerName + "</b></font></center></html>");
            lblPlayerInfo.setBackground(bg); // Applied Company BG
            lblPlayerInfo.setForeground(fg); // Applied Company FG
            lblPlayerInfo.setVisible(true);
        }

        // BOTTOM: Action Title
        // FIX: Applied Company Colors here as well
        if (lblPhaseInstruction != null) {
            lblPhaseInstruction
                    .setText("<html><center><font size='4'><b>" + actionTitle + "</b></font></center></html>");
            lblPhaseInstruction.setBackground(bg);
            lblPhaseInstruction.setForeground(fg);
            lblPhaseInstruction.setVisible(true);
        }
    }


    private void setStandardPanelsVisible(boolean visible) {
        if (phase1Panel != null) phase1Panel.setVisible(visible);
        if (phase2Panel != null) phase2Panel.setVisible(visible);
        if (phase3Panel != null) phase3Panel.setVisible(visible);
        if (phase4Panel != null) phase4Panel.setVisible(visible);
        if (phase5Panel != null) phase5Panel.setVisible(visible);
        if (footerPanel != null) footerPanel.setVisible(visible);
        if (cashPanel != null) cashPanel.setVisible(visible);
        if (loansPanel != null) loansPanel.setVisible(visible);
        if (lblCash != null && lblCash.getParent() != null) lblCash.getParent().setVisible(visible);
    }


    private void colorizeActivePhase(Color unused) {
        resetPhasePanel(phase1Panel, btnTileConfirm);
        resetPhasePanel(phase2Panel, btnTokenConfirm);
        resetPhasePanel(phase3Panel, btnRevPayout);

        // Always enforce the "White Box" style for Revenue buttons for consistency
        if (btnRevPayout != null) {
            btnRevPayout.setText("Pay");
            styleRevenueButton(btnRevPayout, false);
        }
        if (btnRevWithhold != null) {
            btnRevWithhold.setText("Hold");
            styleRevenueButton(btnRevWithhold, false);
        }
        if (btnRevSplit != null) {
            btnRevSplit.setText("Split");
            styleRevenueButton(btnRevSplit, false);
        }

        resetPhasePanel(phase4Panel, btnTrainSkip);
resetPhasePanel(null, btnDone);
        resetPhasePanel(null, btnDone);

        // Phase 1: Infrastructure - Track (Matches Ochre/Brown Palette)
        if (activePhase == 1) {
            applyPhaseStyle(phase1Panel, null, UITheme.TRACK_DARK, UITheme.TRAIN_LIGHT, "Confirm Track");
            if (btnTileConfirm != null) {
                btnTileConfirm.setEnabled(true);

                boolean hasSelection = (orUIManager != null && orUIManager.getMap() != null && orUIManager.getMap().getSelectedHex() != null);
                if (hasSelection) {
                    styleButton(btnTileConfirm, SYS_BLUE, "Confirm");
                } else {
                    styleButton(btnTileConfirm, UITheme.ACTION_SKIP, "Skip");
                }

            }

            // Phase 2: Infrastructure - Token (Matches Forest Green Palette)
        } else if (activePhase == 2) {
            applyPhaseStyle(phase2Panel, null, UITheme.TOKEN_DARK, UITheme.TRAIN_LIGHT, "Confirm Token");
            if (btnTokenConfirm != null) {
                btnTokenConfirm.setEnabled(true);

boolean hasSelection = (orUIManager != null && orUIManager.getMap() != null && orUIManager.getMap().getSelectedHex() != null);
                if (hasSelection) {
                    styleButton(btnTokenConfirm, SYS_BLUE, "Confirm");
                } else {
                    styleButton(btnTokenConfirm, UITheme.ACTION_SKIP, "Skip");
                }
                        }

            // Phase 3: Capital - Revenue (Matches Royal Blue palette)
        } else if (activePhase == 3) {
            applyPhaseStyle(phase3Panel, null, UITheme.REVENUE_DARK, UITheme.TRAIN_LIGHT, "Revenue");

            // Preserved complex logic: Identify which button is ACTUALLY enabled and
            // highlight it
            ActionButton primaryBtn = null;
            if (btnRevPayout != null && btnRevPayout.isEnabled()) {
                primaryBtn = btnRevPayout;
            } else if (btnRevSplit != null && btnRevSplit.isEnabled()) {
                primaryBtn = btnRevSplit;
            } else if (btnRevWithhold != null && btnRevWithhold.isEnabled()) {
                primaryBtn = btnRevWithhold;
            }

            // Apply highlighting to the primary option while keeping others in the standard
            // theme
            if (primaryBtn == btnRevSplit) {
                styleRevenueButton(btnRevSplit, true);
                styleRevenueButton(btnRevPayout, false);
                styleRevenueButton(btnRevWithhold, false);
            } else if (primaryBtn == btnRevWithhold) {
                styleRevenueButton(btnRevWithhold, true);
                styleRevenueButton(btnRevPayout, false);
                styleRevenueButton(btnRevSplit, false);
            } else {
                boolean payEnabled = (btnRevPayout != null && btnRevPayout.isEnabled());
                styleRevenueButton(btnRevPayout, payEnabled);
                styleRevenueButton(btnRevSplit, false);
                styleRevenueButton(btnRevWithhold, false);
            }

            // Phase 4: Capital - Trains (Matches Industrial Orange Palette)
        } else if (activePhase == 4) {

            if (btnTrainSkip != null)
                btnTrainSkip.setEnabled(true);

            boolean canBuy = (trainButtonsPanel != null && trainButtonsPanel.getComponentCount() > 0);
            String label = canBuy ? "Skip Buy" : "Done Buying";

            applyPhaseStyle(phase4Panel, null, UITheme.TRAIN_DARK, UITheme.TRAIN_LIGHT, label);

            boolean canSkip = (btnTrainSkip.getPossibleActions() != null && !btnTrainSkip.getPossibleActions().isEmpty());
            btnTrainSkip.setEnabled(canSkip);
            if (canSkip) {
                styleButton(btnTrainSkip, UITheme.ACTION_SKIP, label);
            } else {
                styleButton(btnTrainSkip, UIManager.getColor("Button.background"), label);
                btnTrainSkip.setForeground(Color.GRAY);
            }
        } else if (activePhase == 6) {

            // ACTIVATE: Enable and colorize for the final step
            btnDone.setEnabled(true);
            styleButton(btnDone, UITheme.ACTION_SKIP, "END TURN");
            btnDone.setForeground(Color.WHITE);
            btnDone.setFont(new Font("SansSerif", Font.BOLD, 16));
        } else {
           
        // DIRECT ENGINE SYNC: If the engine natively provides a DONE/PASS action,
            // expose it immediately. Do not hide valid engine actions behind local UI phases.
            if (btnDone.getPossibleActions() != null && !btnDone.getPossibleActions().isEmpty()) {
                btnDone.setEnabled(true);
                styleButton(btnDone, UITheme.ACTION_SKIP, "END TURN");
                btnDone.setForeground(Color.WHITE);
                btnDone.setFont(new Font("SansSerif", Font.BOLD, 14));
            } else {
                // PERSISTENT WAIT: Keep as 'END TURN' but disabled and grey
                btnDone.setEnabled(false);
                styleButton(btnDone, UIManager.getColor("Button.background"), "END TURN");
                btnDone.setForeground(Color.GRAY);
                btnDone.setFont(new Font("SansSerif", Font.BOLD, 14));
            }
        }
        // ALWAYS evaluate Phase 5 (Special Actions) independently!
        boolean hasSpecialActions = specialActionsButtonPanel != null && specialActionsButtonPanel.getComponentCount() > 0;
        if (hasSpecialActions) {
            applyPhaseStyle(phase5Panel, null, UITheme.ACTION_SKIP, UITheme.TRAIN_LIGHT, "Special Actions");
        } else {
            resetPhasePanel(phase5Panel, null);
        }
    }

    // --- BUTTON STYLING HELPERS ---

    private void styleButton(ActionButton btn, Color bg, String text) {
        if (btn == null)
            return;
        btn.setText(text);
        btn.setBackground(bg);
        btn.setOpaque(true);
        if (bg == PH_DONE_BG) {
            btn.setForeground(Color.BLACK);
        } else {
            btn.setForeground(Color.WHITE);
            btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        }
        btn.setBorder(BorderFactory.createRaisedBevelBorder());
    }

    private void resetButtonStyle(ActionButton btn) {
        if (btn == null)
            return;

        // 1. Set the Standard Grey Background
        btn.setBackground(UIManager.getColor("Button.background"));
        btn.setForeground(Color.GRAY); // Text is Grey to indicate inactivity
        btn.setFont(new Font("SansSerif", Font.PLAIN, 11)); // Keep font smaller/plain for inactive

        // 2. ENFORCE SHAPE CONSISTENCY
        // Instead of reverting to UIManager.getBorder("Button.border"), we use the same
        // 3D bevel.
        btn.setBorder(BorderFactory.createRaisedBevelBorder());

        // 3. Ensure Opacity is true so the grey background paints correctly within the
        // border
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
    }

    private void applyPhaseStyle(JPanel p, ActionButton mainBtn, Color dark, Color light, String btnLabel) {
        if (p == null)
            return;
        p.setOpaque(true);
        p.setBackground(light);
        if (p.getBorder() instanceof TitledBorder) {
            ((TitledBorder) p.getBorder()).setTitleColor(dark);
            ((TitledBorder) p.getBorder()).setBorder(BorderFactory.createLineBorder(dark, 2));
        }
        if (mainBtn != null && mainBtn.isEnabled()) {
            styleButton(mainBtn, dark, btnLabel);
        }
        for (Component c : p.getComponents()) {
            if (c instanceof JLabel) {
                c.setBackground(Color.WHITE);
                c.setForeground(Color.BLACK); // --- FIX: Ensure Readouts (Revenue) stay Black ---
            } else if (c instanceof JPanel)
                c.setBackground(light);
        }
        p.repaint();
    }

    private void resetPhasePanel(JPanel p, ActionButton mainBtn) {
        if (p != null) {
            // Visual "Disable" - Gray out, but DO NOT change logical Enabled state
            p.setOpaque(false);
            p.setBackground(BG_NORMAL);

            // Grey out the border title
            if (p.getBorder() instanceof TitledBorder) {
                ((TitledBorder) p.getBorder()).setTitleColor(Color.GRAY);
                ((TitledBorder) p.getBorder()).setBorder(BorderFactory.createLineBorder(Color.GRAY));
            }

            // Grey out labels inside
            for (Component c : p.getComponents()) {
                if (c instanceof JLabel) {
                    c.setForeground(Color.GRAY);
                } else if (c instanceof JPanel) {
                    ((JPanel) c).setOpaque(false);
                }
            }
            p.repaint();

        }
        if (mainBtn != null) {
            resetButtonStyle(mainBtn);
            // mainBtn.setEnabled(false); // <--- DELETE: Do not clobber state set by Engine
        }
    }

    private ActionButton createSmallButton(String text) {
        ActionButton b = new ActionButton(RailsIcon.OK);
        b.setText(text);
        b.setIcon(null);
        b.setFont(new Font("SansSerif", Font.PLAIN, 10));
        b.setMargin(new Insets(2, 2, 2, 2)); // Tight margins
        return b;
    }

    public static void forceGlobalCleanup() {
        SwingUtilities.invokeLater(() -> {
            for (ORPanel panel : activeInstances) {
                if (panel.orUIManager != null && panel.orUIManager.getGameUIManager() != null) {
                    RoundFacade current = panel.orUIManager.getGameUIManager().getCurrentRound();
                    if (current instanceof OperatingRound)
                        continue;
                }
                panel.finish();
            }
        });
    }

    public void initORCompanyTurn(PublicCompany orComp, int orCompIndex) {
        setTileBuildNumbers(false);
        if (orWindow != null && orWindow.getMapPanel() != null)
            orWindow.getMapPanel().clearOverlays();

        this.orComp = orComp;
        this.currentOperatingComp = orComp;

        removeAllHighlights();
        setStandardPanelsVisible(true);
        if (specialContainer != null)
            specialContainer.setVisible(false);

        // Ensure both parts are visible
        if (orComp != null && lblCompanyInfo != null) {
            lblCompanyInfo.setVisible(true);
            lblPhaseInstruction.setVisible(true);
        }

        updateSidebarData();
        updateCurrentRoutes(false);
    }

    public void resetSidebarState() {
        if (btnDone != null) {
            btnDone.setActionCommand(DONE_CMD);
            btnDone.setPossibleAction(null);
            btnDone.setEnabled(false);
        }
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
        if (btnRevSplit != null) {
            btnRevSplit.setEnabled(false);
        }
        if (btnTrainSkip != null)
            btnTrainSkip.setEnabled(false);

        // Critical: Clear the cached action list so we don't buy "Ghost Trains" from
        // previous states
        if (availableTrainActions != null) {
            availableTrainActions.clear();
        }

        if (trainButtonsPanel != null) {
            trainButtonsPanel.removeAll();
            trainButtonsPanel.setVisible(true);
        }
        if (specialActionsButtonPanel != null) {
            specialActionsButtonPanel.removeAll();
            specialActionsButtonPanel.setVisible(true);
        }
        if (miscActionPanel != null) {
            miscActionPanel.removeAll();
        }

        if (specialNotificationPanel != null) {
            specialNotificationPanel.removeAll();
            specialNotificationPanel.setVisible(false);
        }

        if (specialPanel != null) {
            specialPanel.removeAll();
        }
        if (specialContainer != null) {
            specialContainer.setVisible(false);
        }

        activePhase = 0;
    }

    public void finish() {
        this.orComp = null;
        this.orCompIndex = -1;
        setTileBuildNumbers(false);

        if (sidebarPanel != null) {
            sidebarPanel.setBackground(Color.LIGHT_GRAY);
            if (lblCompanyInfo != null) {
                lblCompanyInfo.setText("Stock Round");
                lblCompanyInfo.setBackground(Color.LIGHT_GRAY);
                lblCompanyInfo.setForeground(Color.GRAY);
                lblPhaseInstruction.setVisible(false); // Hide phase part in SR
            }
            if (lblPlayerInfo != null) {
                lblPlayerInfo.setVisible(false);
                lblPlayerInfo.setText("");
                lblPlayerInfo.setBackground(Color.LIGHT_GRAY);
            }

            setStandardPanelsVisible(false);
            if (specialContainer != null)
                specialContainer.setVisible(false);
        }

        disableRoutesDisplay();
        resetActions();
        repaint();
    }

    public void disableButtons() {
        if (button1 != null)
            button1.setEnabled(false);
        if (button2 != null)
            button2.setEnabled(false);
        if (button3 != null)
            button3.setEnabled(false);
        // Do not block special panel here, purely standard buttons
    }

    public static final int SIDEBAR_WIDTH = 200;
    public static final int SIDEBAR_HEIGHT = 800;
    public static final int HEADER_LOGO_HEIGHT = 90;
    public static final int HEADER_INFO_HEIGHT = 60;
    public static final int HEADER_PHASE_HEIGHT = 30;
    public static final int READOUT_PANEL_HEIGHT = 60;
    public static final int MIN_PHASE_PANEL_HEIGHT = 50;
    public static final int TRAIN_CARD_HEIGHT = 25;
    public static final int REVENUE_BUTTON_ROW_HEIGHT = 25;
    public static final int FOOTER_DONE_HEIGHT = 45;

    private void initSidebar() {
        sidebarPanel = new JPanel();
        sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));

        // 1. Force sidebar to fixed width
        // Sidebar strict width, flexible height
        sidebarPanel.setPreferredSize(new Dimension(SIDEBAR_WIDTH, SIDEBAR_HEIGHT));
        sidebarPanel.setMinimumSize(new Dimension(SIDEBAR_WIDTH, SIDEBAR_HEIGHT));
        sidebarPanel.setMaximumSize(new Dimension(SIDEBAR_WIDTH, Short.MAX_VALUE));

        sidebarPanel.setBackground(BG_DETAILS);
        sidebarPanel.setOpaque(true);

        lblCompanyInfo = new JLabel("Stock Round", SwingConstants.CENTER);
        lblCompanyInfo.setOpaque(true); // Critical for background color
        lblCompanyInfo.setBackground(Color.LIGHT_GRAY);
        lblCompanyInfo.setAlignmentX(Component.CENTER_ALIGNMENT);
        // Fixed dimensions ensure it fills the width
        lblCompanyInfo.setPreferredSize(new Dimension(SIDEBAR_WIDTH, HEADER_INFO_HEIGHT));
        lblCompanyInfo.setMaximumSize(new Dimension(SIDEBAR_WIDTH, HEADER_INFO_HEIGHT));

        // 2. Bottom Component: Phase Instruction
        lblPhaseInstruction = new JLabel("", SwingConstants.CENTER);
        lblPhaseInstruction.setOpaque(true); // Critical for background color
        lblPhaseInstruction.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblPhaseInstruction.setPreferredSize(new Dimension(SIDEBAR_WIDTH, HEADER_PHASE_HEIGHT));
        lblPhaseInstruction.setMaximumSize(new Dimension(SIDEBAR_WIDTH, HEADER_PHASE_HEIGHT));
        sidebarPanel.add(lblCompanyInfo);

        lblPlayerInfo = new JLabel("", SwingConstants.CENTER);
        lblPlayerInfo.setOpaque(true);
        lblPlayerInfo.setBackground(Color.LIGHT_GRAY);
        lblPlayerInfo.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblPlayerInfo.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 20)); // Smaller height for player
        lblPlayerInfo.setMaximumSize(new Dimension(SIDEBAR_WIDTH, 20));
        sidebarPanel.add(lblPlayerInfo);

        sidebarPanel.add(lblPhaseInstruction);

        sidebarPanel.add(Box.createVerticalStrut(5));

        lblLoans = new JLabel("0/0", SwingConstants.CENTER);
        loansPanel = createReadoutPanel("Loans", lblLoans);
        if (hasCompanyLoans) {
            sidebarPanel.add(loansPanel);
            sidebarPanel.add(Box.createVerticalStrut(5));
        }

        // 2. Cash (Readout Style)
        lblCash = new JLabel("-", SwingConstants.CENTER);
        cashPanel = createReadoutPanel("Treasury", lblCash);
        sidebarPanel.add(cashPanel);
        sidebarPanel.add(Box.createVerticalStrut(5));

        // 3. SPECIAL CONTAINER
        specialContainer = new JPanel(new BorderLayout());
        specialContainer.setOpaque(false);
        specialContainer.setVisible(false);
        specialContainer.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BG_DETAILS), "",
                TitledBorder.LEFT, TitledBorder.TOP, FONT_HEADER));

        specialPanel = new JPanel();
        specialPanel.setLayout(new BoxLayout(specialPanel, BoxLayout.Y_AXIS));
        specialPanel.setOpaque(false);
        specialContainer.add(specialPanel, BorderLayout.CENTER);
        sidebarPanel.add(specialContainer);
        // No fixed strut here, visibility controls spacing

        // 4. Phase 1 (Tile)
        phase1Panel = createPhasePanel("1. Build Track");
        // Reduce Height: Set a strict maximum height for Phase 1 (Header + Button +
        // Padding)
        btnTileConfirm = createSidebarButton("Skip", CONFIRM_CMD);
        // Add breathing space above and below the button
        phase1Panel.add(Box.createVerticalStrut(PANEL_ACTION_GAP));
        phase1Panel.add(btnTileConfirm);
        phase1Panel.add(Box.createVerticalStrut(PANEL_ACTION_GAP));

        miscActionPanel = new JPanel();
        miscActionPanel.setLayout(new BoxLayout(miscActionPanel, BoxLayout.Y_AXIS));
        miscActionPanel.setOpaque(false);
        phase1Panel.add(Box.createVerticalStrut(2));
        phase1Panel.add(miscActionPanel);

        sidebarPanel.add(phase1Panel);
        sidebarPanel.add(Box.createVerticalStrut(2));

        // 5. Phase 2 (Token)
        phase2Panel = createPhasePanel("2. Place Token");
        tokenDisplay = new TokenDisplayPanel();
        tokenDisplay.setAlignmentX(Component.CENTER_ALIGNMENT);
        phase2Panel.add(tokenDisplay);

        btnTokenConfirm = createSidebarButton("Skip", CONFIRM_CMD);
        // Add breathing space between content and button, and at bottom
        phase2Panel.add(Box.createVerticalStrut(PANEL_ACTION_GAP));
        phase2Panel.add(btnTokenConfirm);
        phase2Panel.add(Box.createVerticalStrut(PANEL_ACTION_GAP));

        sidebarPanel.add(phase2Panel);
        sidebarPanel.add(Box.createVerticalStrut(2));

        // 6. Phase 3 (Revenue)
        phase3Panel = createPhasePanel("3. Revenue");

        JPanel revDisplayPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        revDisplayPanel.setOpaque(false);
        revDisplayPanel.setMaximumSize(new Dimension(SIDEBAR_WIDTH - 10, 45));

        JPanel divBox = new JPanel();
        divBox.setLayout(new BoxLayout(divBox, BoxLayout.Y_AXIS));
        divBox.setOpaque(false);
        JLabel lblDivTitle = new JLabel("Route", SwingConstants.CENTER); // Changed
        lblDivTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblDivTitle.setFont(new Font("SansSerif", Font.PLAIN, 10));
        lblRoute = new JLabel("0", SwingConstants.CENTER);
        lblRoute.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblRoute.setFont(new Font("SansSerif", Font.BOLD, 18));
        divBox.add(lblDivTitle);
        divBox.add(lblRoute);

        JPanel retBox = new JPanel();
        retBox.setLayout(new BoxLayout(retBox, BoxLayout.Y_AXIS));
        retBox.setOpaque(false);
        JLabel lblRetTitle = new JLabel("Fixed", SwingConstants.CENTER); // Changed
        lblRetTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblRetTitle.setFont(new Font("SansSerif", Font.PLAIN, 10));
        lblFixed = new JLabel("0", SwingConstants.CENTER);
        lblFixed.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblFixed.setFont(new Font("SansSerif", Font.BOLD, 18));
        retBox.add(lblRetTitle);
        retBox.add(lblFixed);

        revDisplayPanel.add(divBox);
        revDisplayPanel.add(retBox);

        phase3Panel.add(Box.createVerticalStrut(5));
        phase3Panel.add(revDisplayPanel);

        // Use GridLayout to force exactly equal 1/3 widths for the 3 buttons
        JPanel revBtnRow = new JPanel(new GridLayout(1, 3, 5, 0)); // 1 row, 3 cols, 5px gap
        revBtnRow.setOpaque(false);
        revBtnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28)); // slight height increase

        btnRevPayout = createSmallButton("Pay");
        btnRevWithhold = createSmallButton("Hold");
        btnRevSplit = createSmallButton("Split");

        btnRevPayout.setActionCommand(PAYOUT_CMD);
        btnRevPayout.addActionListener(this);
        btnRevWithhold.setActionCommand(WITHHOLD_CMD);
        btnRevWithhold.addActionListener(this);
        btnRevSplit.setActionCommand(SPLIT_CMD);
        btnRevSplit.addActionListener(this);

        revBtnRow.add(btnRevPayout);
        revBtnRow.add(btnRevWithhold);
        revBtnRow.add(btnRevSplit);

        // Increase spacing around the revenue button row
        phase3Panel.add(Box.createVerticalStrut(PANEL_ACTION_GAP));
        phase3Panel.add(revBtnRow);
        phase3Panel.add(Box.createVerticalStrut(PANEL_ACTION_GAP));
        sidebarPanel.add(phase3Panel);
        sidebarPanel.add(Box.createVerticalStrut(2));

        // 7. Phase 4 (Train)
        phase4Panel = createPhasePanel("4. Buy Trains");

        trainDisplay = new TrainDisplayPanel();
        phase4Panel.add(trainDisplay);
        phase4Panel.add(Box.createVerticalStrut(5));
        JSeparator trainSep = new JSeparator();
        trainSep.setForeground(Color.LIGHT_GRAY);
        trainSep.setMaximumSize(new Dimension(SIDEBAR_WIDTH - 20, 2));
        phase4Panel.add(trainSep);
        phase4Panel.add(Box.createVerticalStrut(5));

        trainButtonsPanel = new JPanel();
        trainButtonsPanel.setLayout(new BoxLayout(trainButtonsPanel, BoxLayout.Y_AXIS));
        trainButtonsPanel.setOpaque(false);
        phase4Panel.add(trainButtonsPanel);

        btnTrainSkip = createSidebarButton("Skip Buy", TRAIN_SKIP_CMD);
        phase4Panel.add(btnTrainSkip);
        sidebarPanel.add(phase4Panel);

        sidebarPanel.add(Box.createVerticalStrut(5));

        // 7.5 Phase 5 (Special Actions)
        phase5Panel = createPhasePanel("5. Special Actions");
        specialActionsButtonPanel = new JPanel();
        specialActionsButtonPanel.setLayout(new BoxLayout(specialActionsButtonPanel, BoxLayout.Y_AXIS));
        specialActionsButtonPanel.setOpaque(false);
        phase5Panel.add(Box.createVerticalStrut(5));
        phase5Panel.add(specialActionsButtonPanel);
        sidebarPanel.add(phase5Panel);
        sidebarPanel.add(Box.createVerticalStrut(5));

        // 8. Footer (Done Button)
        // Change Footer to Vertical Box to hold Done + Notifications tightly together
        footerPanel = new JPanel();
        footerPanel.setLayout(new BoxLayout(footerPanel, BoxLayout.Y_AXIS));
        footerPanel.setOpaque(false);
        footerPanel.setBorder(BorderFactory.createEmptyBorder(PANEL_ACTION_GAP, 0, PANEL_ACTION_GAP, 0));

        // Rename to "END TURN" and set initial state to Disabled/Grey
        btnDone = createSidebarButton("END TURN", DONE_CMD);
        btnDone.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnDone.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 10, 40));
        btnDone.setEnabled(false);
        resetButtonStyle(btnDone); // Forces grey/standard look

        // Add Done Button
        footerPanel.add(btnDone);

        focusLight = new JLabel("● Focus Initializing", SwingConstants.CENTER);
        focusLight.setFont(new Font("SansSerif", Font.BOLD, 11));
        focusLight.setForeground(Color.GRAY);
        focusLight.setAlignmentX(Component.CENTER_ALIGNMENT);
        footerPanel.add(Box.createVerticalStrut(6));
        footerPanel.add(focusLight);

        // 9. Special Notifications (Attached directly below Done)
        specialNotificationPanel = new JPanel();
        specialNotificationPanel.setLayout(new BoxLayout(specialNotificationPanel, BoxLayout.Y_AXIS));
        specialNotificationPanel.setOpaque(false);
        specialNotificationPanel.setVisible(false);

        // Small gap between Done and Notification
        footerPanel.add(Box.createVerticalStrut(4));
        footerPanel.add(specialNotificationPanel);

        sidebarPanel.add(footerPanel);
        sidebarPanel.add(Box.createVerticalStrut(5));

        add(sidebarPanel);
    }

    private JPanel createReadoutPanel(String title, JLabel valueLabel) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_DETAILS);
        p.setOpaque(true);
        p.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), title,
                TitledBorder.LEFT, TitledBorder.TOP, FONT_HEADER));

        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(valueLabel);

        // Strict readout dimensions
        p.setPreferredSize(new Dimension(SIDEBAR_WIDTH, READOUT_PANEL_HEIGHT));
        p.setMinimumSize(new Dimension(SIDEBAR_WIDTH, READOUT_PANEL_HEIGHT));
        p.setMaximumSize(new Dimension(SIDEBAR_WIDTH, READOUT_PANEL_HEIGHT));
        return p;
    }

    private JPanel createPhasePanel(String title) {
        // Use an anonymous class to override sizing behavior dynamically
        JPanel p = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                // FORCE the layout to respect the content height.
                // By making Max Height = Preferred Height, the panel refuses to stretch
                // vertically to fill empty space.
                Dimension pref = getPreferredSize();
                return new Dimension(SIDEBAR_WIDTH, pref.height);
            }

            @Override
            public Dimension getPreferredSize() {
                // Ensure width is always fixed to sidebar width, but height is dynamic
                Dimension superPref = super.getPreferredSize();
                return new Dimension(SIDEBAR_WIDTH, Math.max(superPref.height, MIN_PHASE_PANEL_HEIGHT));
            }
        };

        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), title,
                TitledBorder.LEFT, TitledBorder.TOP, FONT_HEADER));
        p.setOpaque(true);
        p.setBackground(BG_NORMAL);
        p.setAlignmentX(Component.CENTER_ALIGNMENT);

        // We do NOT set setPreferredSize or setMaximumSize manually here anymore.
        // The overrides above handle it dynamically based on content (buttons, labels).

        return p;
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
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, BTN_HEIGHT));
        b.setMaximumSize(new Dimension(SIDEBAR_WIDTH - 20, BTN_HEIGHT));
        return b;
    }

    private void styleRevenueButton(ActionButton btn, boolean isSelected) {
        if (btn == null)
            return;

        // 1. Force BasicUI to prevent LookAndFeel (e.g. Mac Aqua) from overriding
        // disabled styles
        btn.setUI(new javax.swing.plaf.basic.BasicButtonUI());

        // 2. Enforce strict opacity and painting
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
        btn.setBorderPainted(true);
        btn.setFocusPainted(false);

        // 3. Uniform Border and Font
        btn.setBorder(BorderFactory.createRaisedBevelBorder());
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));

        // 4. Handle State Colors
        if (isSelected) {
            btn.setBackground(SYS_BLUE);
            btn.setForeground(Color.WHITE);
        } else {
            // Force White background even if Disabled
            btn.setBackground(Color.WHITE);
            // Optional: Ensure text remains visible/black if disabled
            btn.setForeground(Color.BLACK);
        }
    }

public void recreate(Round or) {
        PublicCompany comp = null;
        if (or instanceof OperatingRound) {
            comp = ((OperatingRound) or).getOperatingCompany();
        } else {
            try {
                java.lang.reflect.Method m = or.getClass().getMethod("getOperatingCompany");
                comp = (PublicCompany) m.invoke(or);
            } catch (Exception e) {}
        }
        initORCompanyTurn(comp, 0);
    }

    public void recreate(OperatingRound or) {
        initORCompanyTurn(or.getOperatingCompany(), 0);
    }

    private void initButtonPanel() {
        buttonOC = new ActionButton(RailsIcon.OPERATING_COST);
        buttonOC.setActionCommand(OPERATING_COST_CMD);
        buttonOC.addActionListener(this);
        button1 = new ActionButton(RailsIcon.BUY_TRAIN);
        button1.addActionListener(this);
        button2 = new ActionButton(RailsIcon.DONE);
        button2.addActionListener(this);
        button3 = new ActionButton(RailsIcon.BUY_PRIVATE);
        button3.addActionListener(this);
    }

    public void setTileBuildNumbers(boolean show) {
        this.showNumbersActive = show;
        if (btnBuildShow != null)
            btnBuildShow.setText(showNumbersActive ? "Hide" : "Show");
        if (orUIManager != null)
            orUIManager.updateHexBuildNumbers(showNumbersActive);
        repaint();
    }

    public void toggleTileBuildNumbers() {
        setTileBuildNumbers(!this.showNumbersActive);
    }

    public String format(int amount) {
        return orUIManager.getGameUIManager().format(amount);
    }

    public void setSpecialMode(boolean enabled) {
        this.specialModeActive = enabled;
    } // Kept for compatibility but driven by updateDynamicActions

    // TrainDisplayPanel, addTrainBuyButton logic as is) ...
    // NOTE: For brevity in this response I am summarizing that the standard methods
    // (getRevenue, setDividend, etc.) remain as they were in the previous version,
    // ensuring standard functionality is not lost. The critical change is in the
    // Update/Render logic above.

    public void enableUndo(GameAction action) {
        if (action != null)
            this.currentUndoAction = action;
    }

    public void enableRedo(GameAction action) {
        if (action != null)
            this.currentRedoAction = action;
    }

    public void enableConfirm(boolean hasSelection) {
        ActionButton targetBtn = (activePhase == 1) ? btnTileConfirm : (activePhase == 2) ? btnTokenConfirm : null;
        if (targetBtn == null)
            return;
        targetBtn.setEnabled(true);
        if (hasSelection) {
            Color phaseColor = (activePhase == 1) ? PH_TILE_DARK : PH_TOKEN_DARK;
            styleButton(targetBtn, SYS_BLUE, "Confirm");
        } else {
            styleButton(targetBtn, SYS_BLUE, "Skip");
        }
        updateDefaultButton();
    }

    private void updateDefaultButton() {
        ActionButton defaultBtn = null;

        if (activePhase == 1)
            defaultBtn = btnTileConfirm;
        else if (activePhase == 2)
            defaultBtn = btnTokenConfirm;
        else if (activePhase == 3) {
            // Prioritize Payout -> Split -> Withhold
            if (btnRevPayout != null && btnRevPayout.isEnabled())
                defaultBtn = btnRevPayout;
            else if (btnRevSplit != null && btnRevSplit.isEnabled())
                defaultBtn = btnRevSplit;
            else
                defaultBtn = btnRevWithhold;
        } else if (activePhase == 4)
            defaultBtn = btnTrainSkip;
            else if (activePhase == 5)
            defaultBtn = btnDone; 
        
        else
            defaultBtn = btnDone;

        // Fallback: If Done is enabled and nothing else is, default to Done
        if (defaultBtn == null || !defaultBtn.isEnabled()) {
            if (btnDone != null && btnDone.isEnabled())
                defaultBtn = btnDone;
        }

        this.currentDefaultButton = defaultBtn;
        if (getRootPane() != null)
            getRootPane().setDefaultButton(defaultBtn);

    }

    private void cycleHexes(int direction) {
        if (orUIManager == null || cycleableHexes.isEmpty())
            return;
        cycleIndex += direction;
        if (cycleIndex >= cycleableHexes.size())
            cycleIndex = 0;
        if (cycleIndex < 0)
            cycleIndex = cycleableHexes.size() - 1;
        orUIManager.hexClicked(cycleableHexes.get(cycleIndex), orUIManager.getMap().getSelectedHex(), false);
        enableConfirm(true);
    }

    // Required Stubs for Compilation (Legacy Interface support)
    public void resetHexCycle() {
        cycleableHexes.clear();
        cycleIndex = -1;
    }

    public void updateCycleableHexes(Collection<GUIHex> hexes) {
        cycleableHexes.clear();
        if (hexes != null)
            for (GUIHex h : hexes)
                if (h.getState() == GUIHex.State.SELECTABLE)
                    cycleableHexes.add(h);
    }

    public void initTileLayingStep() {
        activePhase = 1;
    }

    public void initTokenLayingStep() {
        activePhase = 2;
    }

    public void initTrainBuying(List<BuyTrain> a) {
        activePhase = 4;
    }

    public void initPayoutStep(int i, SetDividend s, boolean w, boolean spl, boolean p) {
        activePhase = 3;
    }

    public void setupConfirm() {
        enableConfirm(false);
    }

    public void enableSkip(NullAction a) {
    }

    public void enableDone(NullAction a) {
    }

    public void initOperatingCosts(boolean b) {
        if (buttonOC != null) {
            buttonOC.setEnabled(b);
            buttonOC.setVisible(b);
        }
    }

    public void initPrivateBuying(boolean b) {
        if (button3 != null) {
            button3.setEnabled(b);
            button3.setVisible(b);
        }
    }

    public void enableLoanTaking(TakeLoans a) {
    } // No-op

    public void enableLoanRepayment(RepayLoans a) {
        if (button1 != null) {
            button1.setEnabled(true);
            button1.setVisible(true);
        }
    }

    public void setDividend(int i, int a) {
        setRevenue(i, a);
    }

    public void revenueUpdate(int best, int special, boolean finalRes) {
        SwingUtilities.invokeLater(() -> {

            if (lblRoute != null) {
                int routeRev = best - special;
                if (routeRev < 0)
                    routeRev = 0;
                lblRoute.setText(format(routeRev));
            }
            if (lblFixed != null) {
                lblFixed.setText(format(special));
            }

            if (isRevenueValueToBeSet) {
                // Pass the 'special' value (Mine Revenue) from the calculator to the button.
                setRevenue(orCompIndex, best, special);
            }

            if (finalRes && isDisplayCurrentRoutes()) {
                revenueAdapter.drawOptimalRunAsPath(orUIManager.getMap());
            }

        });
    }

    /**
     * Overloaded setRevenue to handle Special (Direct/Mine) revenue
     */
    public void setRevenue(int i, int a, int special) {
        updateRevenueButton(btnRevPayout, a, special);
        updateRevenueButton(btnRevWithhold, a, special);
        updateRevenueButton(btnRevSplit, a, special);
    }

    /**
     * Legacy method support
     */
    public void setRevenue(int i, int a) {
        setRevenue(i, a, 0);
    }

    // Revenue Helpers
    private void updateRevenueButton(ActionButton btn, int amount) {
        updateRevenueButton(btn, amount, 0);
    }

    private void updateRevenueButton(ActionButton btn, int amount, int special) {
        if (btn == null || !btn.isEnabled())
            return;
        List<PossibleAction> actions = btn.getPossibleActions();
        if (actions != null && !actions.isEmpty() && actions.get(0) instanceof SetDividend) {
            SetDividend sd = (SetDividend) actions.get(0);

            // Bind the data from the calculator to the Action
            sd.setActualRevenue(amount);
            sd.setActualCompanyTreasuryRevenue(special);

            btn.repaint();
        }
    }

    private void updateCurrentRoutes(boolean isSetRevenueStep) {
        if (orComp != null && !orComp.isClosed()) {
            isRevenueValueToBeSet = isSetRevenueStep;
            RailsRoot root = orUIManager.getGameUIManager().getRoot();
            if (revenueThread != null)
                revenueThread.interrupt();
            if (revenueAdapter != null)
                revenueAdapter.removeRevenueListener();
            revenueAdapter = RevenueAdapter.createRevenueAdapter(root, orComp,
                    root.getPhaseManager().getCurrentPhase());
            revenueAdapter.initRevenueCalculator(true);
            revenueAdapter.addRevenueListener(this);
            revenueThread = new Thread(revenueAdapter);
            revenueThread.start();
        } else {
            disableRoutesDisplay();
        }
    }

    private void disableRoutesDisplay() {
        if (revenueThread != null)
            revenueThread.interrupt();
        if (revenueAdapter != null)
            revenueAdapter.removeRevenueListener();
        orUIManager.getMap().setTrainPaths(null);
    }

    private boolean isDisplayCurrentRoutes() {
        return "yes".equalsIgnoreCase(Config.get("map.displayCurrentRoutes"));
    }

    // ... (lines of unchanged context code) ...
    public void processIPOBuy() {

        if (availableTrainActions != null && !availableTrainActions.isEmpty()) {
            for (BuyTrain action : availableTrainActions) {

                // Debug Logging
                net.sf.rails.game.state.Owner seller = action.getFromOwner();
                String sellerId = (seller != null) ? seller.getId() : "null";
                String parentId = (seller != null && seller.getParent() != null)
                        ? seller.getParent().getClass().getSimpleName()
                        : "null";

                // Strict Filter: Only buy if seller is explicitly IPO or Bank (and NOT Pool)
                boolean isIpo = false;
                if (seller != null) {
                    if ("IPO".equals(seller.getId())) {
                        isIpo = true;
                    } else if (seller.getParent() instanceof net.sf.rails.game.financial.Bank
                            && !"Pool".equals(seller.getId())) {
                        isIpo = true;
                    }
                }

                if (isIpo) {

                    // Fix: Auto-fill price if missing. Engine rejects 0-price buys for standard
                    // trains.
                    if (action.getPricePaid() == 0 && action.getFixedCost() > 0) {
                        action.setPricePaid(action.getFixedCost());
                    }

                    List<PossibleAction> toExec = new ArrayList<>();
                    toExec.add(action);

                    orUIManager.processAction(BUY_TRAIN_CMD, toExec, this);
                    break;
                } else {
                }

            }
        } else {
        }
    }

    public void finishORCompanyTurn(int index) {
        resetActions();
        setTileBuildNumbers(false);
        orUIManager.getMap().setTrainPaths(null);
    }

    private void cleanupUpgradesPanel() {
        if (orUIManager != null && orUIManager.getUpgradePanel() != null)
            orUIManager.getUpgradePanel().setInactive();
    }

    private void enableRevenueBtn(ActionButton btn, SetDividend sd, int allocation) {
        btn.setEnabled(true);
        SetDividend clone = (SetDividend) sd.clone();
        clone.setRevenueAllocation(allocation);
        btn.setPossibleAction(clone);
        if (btn == btnRevPayout)
            btn.setText("Pay");
        else if (btn == btnRevWithhold)
            btn.setText("Hold");
        else if (btn == btnRevSplit)
            btn.setText("Split");
    }

    private void setupButton(ActionButton btn, PossibleAction pa) {
        if (btn != null) {
            btn.setEnabled(true);
            btn.setPossibleAction(pa);
        }
    }

    // Inner Classes
    private class TokenDisplayPanel extends JPanel {
        public void setTokens(int count, PublicCompany c) {
            removeAll();
            if (c != null && count > 0)
                for (int i = 0; i < count; i++)
                    add(new JLabel(new TokenIcon(24, c.getFgColour(), c.getBgColour(), c.getId())));
            revalidate();
            repaint();
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

    private void addTrainBuyButton(BuyTrain action) {
        ActionButton btn = new ActionButton(RailsIcon.BUY_TRAIN);
        String text = action.getButtonLabel().replace("Buy ", "").replace(" train", "").replace(" from ", " - ")
                .replace(" for ", " - ");
        btn.setText(text);
        btn.setIcon(null);

        // Match RailCard Styling
        btn.setBackground(new Color(255, 255, 240)); // BG_CARD_PASSIVE
        btn.setOpaque(true);
        btn.setForeground(Color.BLACK);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.BLACK, 1),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)));

        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(SIDEBAR_WIDTH - 10, 30));
        btn.setPossibleAction(action);
        btn.addActionListener(this);
        if (trainButtonsPanel != null) {
            trainButtonsPanel.add(btn);
            trainButtonsPanel.add(Box.createVerticalStrut(4));
        }
    }

    public static void setGlobalCustomHeader(String t, String s) {
    } // Deprecated/No-op

    public static void releaseSpecialMode(GameUIManager m) {
    } // Deprecated/No-op

    public static void forceUpdateForManager(GameUIManager m, List<PossibleAction> a) {
        // Forward to instance
        for (ORPanel p : activeInstances)
            if (p.orWindow.gameUIManager == m)
                p.updateDynamicActions(a);
    }

    public Color getTrainHighlightColor() {
        return Color.ORANGE;
    }

    // Stub to prevent compilation error if referenced
    private void clearRevenueAdapter() {
    }

    public void dispose() {
        for (JFrame f : openWindows)
            f.dispose();
        openWindows.clear();
    }

    public JMenuBar getMenuBar() {
        // Menus were moved to StatusWindow, so returning null is safe
        // provided ORWindow checks for null (which we can't change easily),
        // or we return a dummy empty menu bar.
        return new JMenuBar();
    }

    public JPanel getSidebarPanel() {
        return sidebarPanel;
    }

    public void resetActions() {
        // Delegate to existing reset logic
        resetSidebarState();
    }

    public void redrawRoutes() {
        // Delegate to existing route update logic
        if (activePhase == 3) { // Revenue phase
            updateCurrentRoutes(true);
        } else {
            updateCurrentRoutes(false);
        }
    }

    public boolean executeUndo() {
        if (currentUndoAction != null && orUIManager != null) {
            orUIManager.processAction(UNDO_CMD, Collections.singletonList(currentUndoAction), this);
            return true;
        }
        return false;
    }

    public int getRevenue(int index) {
        // Return 0 or cached value. 1837 uses this to read spinner values.
        return (orComp != null) ? orComp.getLastRevenue() : 0;
    }

    public int getCompanyTreasuryBonusRevenue(int index) {
        return 0; // Stub
    }

    public void setTreasuryBonusRevenue(int index, int amount) {
        // No-op or log
    }

    public void stopRevenueUpdate() {
        this.isRevenueValueToBeSet = false;
        // Stop threads if needed
        if (revenueThread != null)
            revenueThread.interrupt();
    }

    private void updateSidebarData() {
        if (specialModeActive)
            return;

        if (orComp == null) {
            if (lblCash != null)
                lblCash.setText("-");
            return;
        }

        Color phaseColor = UITheme.READOUT_BG;
        String instruction = "Wait...";

        switch (activePhase) {
            case 1:
                phaseColor = UITheme.TRACK_DARK;
                instruction = "BUILD TRACK";
                break;
            case 2:
                phaseColor = UITheme.TOKEN_DARK;
                instruction = "PLACE TOKEN";
                break;
            case 3:
                phaseColor = UITheme.REVENUE_DARK;
                instruction = "REVENUE";
                break;
            case 4:
                phaseColor = UITheme.TRAIN_DARK;
                instruction = "BUY TRAIN";
                break;
            case 5:
                phaseColor = UITheme.ACTION_SKIP;
                instruction = "SPECIAL ACTIONS";
                break;
            case 6:
                phaseColor = UITheme.ACTION_DONE;
                instruction = "FINALIZE";
                break;

        }

        if (lblCompanyInfo != null) {
            String playerInfo = (orComp.getPresident() != null) ? orComp.getPresident().getName() : "";
            // TOP LABEL: Company Info
            String topText = "<html><center>" +
                    "<font face='SansSerif' size='6'><b>" + orComp.getId() + "</b></font><br>" +
                    "<font face='SansSerif' size='5'>" + playerInfo + "</font>" +
                    "</center></html>";

            lblCompanyInfo.setText(topText);
            lblCompanyInfo.setBackground(orComp.getBgColour());
            lblCompanyInfo.setForeground(orComp.getFgColour());

            // Standard top/side border, open bottom to merge with instruction
            lblCompanyInfo.setBorder(BorderFactory.createMatteBorder(1, 1, 0, 1, Color.DARK_GRAY));
            lblCompanyInfo.setVisible(true);

            // Hide the special mode player label to prevent "hangovers" from previous
            // rounds
            if (lblPlayerInfo != null) {
                lblPlayerInfo.setVisible(false);
            }
            // BOTTOM LABEL: Instruction
            // Reverted to match Company Logo colors (Unified Header)
            String bottomText = "<html><center><font face='SansSerif' size='4'><b>" + instruction
                    + "</b></font></center></html>";

            lblPhaseInstruction.setText(bottomText);

            // Set colors to match the Company ID above
            lblPhaseInstruction.setBackground(orComp.getBgColour());
            lblPhaseInstruction.setForeground(orComp.getFgColour());

            // Remove the 5px gap. Set top inset to 0.
            // Border: 0px Top, 1px Left, 1px Bottom, 1px Right
            lblPhaseInstruction.setBorder(BorderFactory.createMatteBorder(0, 1, 1, 1, Color.DARK_GRAY));
            lblPhaseInstruction.setVisible(true);
        }

        colorizeActivePhase(null);
        if (lblCash != null)
            lblCash.setText(format(orComp.getPurseMoneyModel().value()));

        if (lblLoans != null && orComp != null && hasCompanyLoans) {
            int currentBonds = orComp.getNumberOfBonds();
            int maxBonds = currentBonds;
            try {
                java.lang.reflect.Method m = orComp.getClass().getMethod("getShareCount");
                maxBonds = (Integer) m.invoke(orComp);
            } catch (Exception e) {
                // Fallback
            }
            lblLoans.setText(currentBonds + "/" + maxBonds);
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

        if (lblRoute != null && lblFixed != null) {
            int totalRev = orComp.getLastRevenue();
            int fixedRev = orComp.getLastDirectIncome();

            // Stale Data Guard
            if (!orComp.canHaveFixedIncome())
                fixedRev = 0;
            if (totalRev == 0 || fixedRev > totalRev)
                fixedRev = 0;

            int routeRev = totalRev - fixedRev;

            lblRoute.setText(format(routeRev));
            lblFixed.setText(format(fixedRev));
        }

        if (trainDisplay != null)
            trainDisplay.updateAssets(orComp);

        // Force the UpgradesPanel's mini tile dock to refresh immediately on state
        // changes
        if (orWindow != null && orWindow.getUpgradePanel() != null) {
            orWindow.getUpgradePanel().refreshMiniDock();
        }

    }

    private void setupHotkeys() {
        InputMap inputMap = this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = this.getActionMap();

    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String command = e.getActionCommand();
        JComponent source = (JComponent) e.getSource();

        if (command.equals(REM_TILES_CMD)) {
            new RemainingTilesWindow(orWindow);
        } else if (command.equals(SHOW_CMD)) {
            toggleTileBuildNumbers();
        } else if (command.equals(TRAIN_SKIP_CMD)) {
            activePhase = 6;
            updateSidebarData();
            updateDefaultButton();
            if (btnTrainSkip != null)
                btnTrainSkip.setEnabled(false);
            if (trainButtonsPanel != null) {
                for (Component c : trainButtonsPanel.getComponents())
                    c.setEnabled(false);
            }
        } else if (command.equals(CONFIRM_CMD)) {
            if (orUIManager != null) {
                boolean hasSelection = (orUIManager.getMap().getSelectedHex() != null);
                if (hasSelection)
                    orUIManager.confirmUpgrade();
                else
                    orUIManager.skipUpgrade();
            }
        } else if (command.equals(OPERATING_COST_CMD)) {
            if (orUIManager != null)
                orUIManager.operatingCosts();
        } else if (command.equals(BUY_PRIVATE_CMD)) {
            if (orUIManager != null)
                orUIManager.buyPrivate();
        } else if (source instanceof ActionTaker) {
            // Immediate Feedback: Disable button to prevent double-clicks
            if (source instanceof AbstractButton) {
                ((AbstractButton) source).setEnabled(false);
                source.repaint();
            }
            List<PossibleAction> executedActions = ((ActionTaker) source).getPossibleActions();
            if (executedActions == null || executedActions.isEmpty()) {
            } else if (executedActions.get(0).getClass().getName().endsWith("TakeLoans_1817")) {
                processTakeLoans_1817(executedActions.get(0));
                return;
            } else {
                orUIManager.processAction(command, executedActions, source);
            }
            // REVENUE SYNC: Minor companies often jump from Revenue to Done.
            // We manually force a state pull to ensure the "END TURN" button appears.
            if (PAYOUT_CMD.equals(command) || SPLIT_CMD.equals(command) || WITHHOLD_CMD.equals(command)) {
                SwingUtilities.invokeLater(() -> {
                    // Access the GameManager to get the definitive list of next actions
                    GameManager gm = orUIManager.getGameUIManager().getGameManager();
                    if (gm != null && gm.getPossibleActions() != null) {
                        List<PossibleAction> nextActions = gm.getPossibleActions().getList();

                        updateDynamicActions(nextActions);
                    }
                });
            }

        }
    }


    private void processTakeLoans_1817(PossibleAction action) {
        try {
            String compId = (String) action.getClass().getMethod("getCompanyId").invoke(action);
            int max = (Integer) action.getClass().getMethod("getMaxLoansAllowed").invoke(action);
            net.sf.rails.game.CompanyManager cm = orUIManager.getGameUIManager().getRoot().getCompanyManager();
            net.sf.rails.game.PublicCompany comp = cm.getPublicCompany(compId);
            int current = comp.getNumberOfBonds();
            int available = max - current;

            if (available <= 0) {
                JOptionPane.showMessageDialog(this, comp.getId() + " is at its loan limit (" + max + ").");
                return;
            }

            String[] options = new String[available];
            for (int i = 0; i < available; i++) {
                options[i] = String.valueOf(i + 1);
            }

            String selected = (String) JOptionPane.showInputDialog(this,
                    "Select number of loans for " + comp.getId() + ":\n(Current: " + current + ", Max: " + max + ")",
                    "Take Loans", JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

            if (selected != null) {
                action.getClass().getMethod("setLoansToTake", int.class).invoke(action, Integer.parseInt(selected));
                orUIManager.processAction("TakeLoans", java.util.Collections.singletonList(action), this);
            } else {
                // Re-enable the button if the user cancels the dialog
                if (specialActionsButtonPanel != null) {
                    for (Component c : specialActionsButtonPanel.getComponents()) {
                        if (c instanceof ActionButton) {
                            ((ActionButton) c).setEnabled(true);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Failed to process TakeLoans_1817 in ORPanel", ex);
        }
    }

    private boolean isActionListEmpty(ActionButton btn) {
        return btn.getPossibleActions() == null || btn.getPossibleActions().isEmpty();
    }

    // ... (lines of unchanged context code) ...
    public boolean executeRedo() {
        if (currentRedoAction != null && orUIManager != null) {
            orUIManager.processAction(REDO_CMD, java.util.Collections.singletonList(currentRedoAction), this);
            return true;
        }
        return false;
    }

    // ... (lines of unchanged context code) ...
    private void addSpecialActionButton(PossibleAction action) {
        String label = action.getButtonLabel();

        // Defaults
        Color bgColor = Color.LIGHT_GRAY;
        Color borderColor = Color.GRAY;
        Color textColor = Color.BLACK;
        String cmd = "SpecialAction";

        Company highlightTarget = null;

        // 1. Extract Visual Signature via Interface
        if (action instanceof GuiTargetedAction) {
            GuiTargetedAction gta = (GuiTargetedAction) action;
            label = gta.getButtonLabel();

            if (gta.getTarget() instanceof Company) {
                highlightTarget = (Company) gta.getTarget();
            }

            // CONSUME THE SIGNATURE
            bgColor = gta.getHighlightBackgroundColor();
            borderColor = gta.getHighlightBorderColor();
            textColor = gta.getHighlightTextColor();
        } else if (action instanceof NullAction) {
            label = ((NullAction) action).getMode() == NullAction.Mode.PASS ? "Decline" : "Done";
            bgColor = UITheme.ACTION_SKIP;
            borderColor = bgColor.darker();
            textColor = Color.WHITE;
        } else if (action instanceof LayBaseToken) {
            highlightTarget = ((LayBaseToken) action).getCompany();
        }

        // 2. Create Button
        ActionButton btn = createSidebarButton(label, cmd);

        // HTML Formatting to match RailCard text style if needed
        if (!label.toLowerCase().startsWith("<html>")) {
            btn.setText("<html><center>" + label + "</center></html>");
        } else {
            btn.setText(label);
        }

        btn.setPossibleAction(action);
        btn.setEnabled(true);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        // Allow button to be taller to fit the HTML content
        btn.setMaximumSize(new Dimension(SIDEBAR_WIDTH - 20, 60));
        bindActionHotkey(btn, action);

        // Attach HexHighlightMouseListener based on specific Company type
        if (highlightTarget instanceof PublicCompany) {
            net.sf.rails.ui.swing.hexmap.HexHighlightMouseListener.addMouseListener(
                    btn, orUIManager, (PublicCompany) highlightTarget, false);
        } else if (highlightTarget instanceof PrivateCompany) {
            net.sf.rails.ui.swing.hexmap.HexHighlightMouseListener.addMouseListener(
                    btn, orUIManager, (PrivateCompany) highlightTarget, false);
        }

        // 3. APPLY "RAILCARD" STYLING (Flattened)

        // 3. APPLY "RAILCARD" STYLING (Flattened)

        // A. Background & Text
        btn.setBackground(bgColor);
        btn.setForeground(textColor);

        // B. Border (Thick Line Border to match GameStatus Card)
        // Outer: The colored line (3px)
        // Inner: Padding (5px)
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 3),
                BorderFactory.createEmptyBorder(2, 5, 2, 5)));

        // C. Technical overrides to ensure "Flat" look
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
        btn.setFocusPainted(false); // Remove dotted focus line
        // Force the font to match RailCard
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));

        specialPanel.add(btn);
        specialPanel.add(Box.createVerticalStrut(8));
    }
    // ... (lines of unchanged context code) ...

    /**
     * Replaces the tooltip logic with a formatted log entry.
     * Cleans up UI tooltips and dumps a readable text table to the console.
     */
    private void logFormattedActions(List<PossibleAction> actions) {
        // 1. Revert UI Tooltips (Clean up)
        this.setToolTipText(null);
        if (lblPhaseInstruction != null)
            lblPhaseInstruction.setToolTipText(null);
        if (lblCompanyInfo != null)
            lblCompanyInfo.setToolTipText(null);
        if (phase1Panel != null)
            phase1Panel.setToolTipText(null);
        if (phase2Panel != null)
            phase2Panel.setToolTipText(null);
        if (phase3Panel != null)
            phase3Panel.setToolTipText(null);
        if (phase4Panel != null)
            phase4Panel.setToolTipText(null);
        if (footerPanel != null)
            footerPanel.setToolTipText(null);

        if (actions == null || actions.isEmpty())
            return;

        // 2. Build Readable Log Table
        int moveCount = 0;
        if (orUIManager != null && orUIManager.getGameUIManager().getGameManager() != null) {
            moveCount = orUIManager.getGameUIManager().getGameManager().getCurrentActionCount();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n=== ENGINE ACTION BUFFER (Move #").append(moveCount).append(") ===\n");
        sb.append(String.format("%-20s | %s%n", "TYPE", "DETAILS / INTERNAL STATE"));
        sb.append("---------------------+--------------------------------------------------\n");

        for (PossibleAction pa : actions) {
            // Use full package name to avoid import errors
            if (pa.isCorrection() || pa instanceof rails.game.correct.CorrectionModeAction)
                continue;

            String className = pa.getClass().getSimpleName();
            String rawData = pa.toString();
            String formattedData;

            if (pa instanceof NullAction) {
                formattedData = "Logical " + ((NullAction) pa).getMode() + " (Stopper/Pass)";
            } else if (rawData.contains(",")) {
                // Format comma-separatepublic ActionButton buttonOC, button1, button2, button3; // Legacy placeholdersd lists (like BuyTrain) into a vertical list
                formattedData = "- " + rawData.replace(", ", "\n                     | - ");
            } else {
                formattedData = rawData;
            }

            sb.append(String.format("%-20s | %s%n", className, formattedData));
        }
        sb.append("======================================================================\n");

        log.info(sb.toString());
    }

    public void updateDynamicActions(List<PossibleAction> actions) {

        // // --- START DEBUG INSTRUMENTATION ---
        // log.info("\nORPanel: updateDynamicActions() RECEIVED " + (actions == null ? "null" : actions.size())
        //         + " actions.");
        // if (actions != null) {
        //     for (int i = 0; i < actions.size(); i++) {
        //         PossibleAction pa = actions.get(i);
        //         // Filter out CorrectionModeAction entries from the UI log output
        //         if (pa.toString().contains("CorrectionModeAction")) {
        //             continue;
        //         }
        //         String hash = Integer.toHexString(System.identityHashCode(pa));
        //         log.info(String.format("   UI Action[%d]: Class: %-20s | Hash: %s | Str: %s",
        //                 i, pa.getClass().getSimpleName(), hash, pa.toString()));
        //     }
        // }
        // // --- END DEBUG INSTRUMENTATION ---

        try {

            cleanupUpgradesPanel();
            resetSidebarState();

// ROBUST CONTEXT HANDOVER
            PublicCompany engineActiveComp = null;
            boolean isMaARound = false;

            if (orUIManager != null && orUIManager.getGameUIManager().getGameManager() != null) {
                net.sf.rails.game.round.RoundFacade rf = orUIManager.getGameUIManager().getGameManager()
                        .getCurrentRound();
                if (rf != null) {
                    isMaARound = rf.getClass().getSimpleName().contains("Merger");
                    if (rf instanceof net.sf.rails.game.OperatingRound) {
                        engineActiveComp = ((net.sf.rails.game.OperatingRound) rf).getOperatingCompany();
                    } else {
                        try {
                            java.lang.reflect.Method m = rf.getClass().getMethod("getOperatingCompany");
                            engineActiveComp = (PublicCompany) m.invoke(rf);
                        } catch(Exception e) {}
                    }
                }
            }

            // Sync Context
            if (engineActiveComp != null && !engineActiveComp.isClosed()) {
                this.orComp = engineActiveComp;
                this.currentOperatingComp = engineActiveComp;
            } else if (this.currentOperatingComp != null && !this.currentOperatingComp.isClosed()) {
                this.orComp = this.currentOperatingComp;
            }

            // 3. FILTER & DETECT SPECIAL ACTIONS (Generic "Stupid Panel" Logic)
            List<PossibleAction> specialActions = new ArrayList<>();
            GuiTargetedAction contextProvider = null;

            // Fix: Buffer the NullAction to process it AFTER checking for other special
            // actions.
            // This prevents the "Done" button from being swallowed if it appears
            // before the Discard/Special action in the list.
            PossibleAction deferredNullAction = null;

            for (PossibleAction pa : actions) {
                // If it implements the interface, it is definitely a special UI action
                if (pa instanceof GuiTargetedAction) {
                    specialActions.add(pa);
                    if (contextProvider == null)
                        contextProvider = (GuiTargetedAction) pa;
                }

                // Legacy Fallback for Home Token (if not yet upgraded to GuiTargetedAction)
                else if (pa instanceof LayBaseToken && ((LayBaseToken) pa).getType() == LayBaseToken.HOME_CITY) {
                    specialActions.add(pa);
                }
                // Buffer the NullAction; do not decide yet
                else if (pa instanceof NullAction) {
                    deferredNullAction = pa;
                }
            }

            // Post-Loop: Now we know if special actions exist, so we can safely decide on
            // the Done button
            if (deferredNullAction != null) {
                // If we have special actions (like Discards), enable the Done button.
                // Also enable it if it is an explicit PASS.
                if (!specialActions.isEmpty() || ((NullAction) deferredNullAction).getMode() == NullAction.Mode.PASS) {
                    specialActions.add(deferredNullAction);
                }
            }

            // 4. GENERIC CONTEXT SWITCH
            // If the special action dictates a specific actor (e.g. a Company discarding
            // out of turn),
            // we switch the panel's focus to that actor immediately.
            if (contextProvider != null) {
                Owner actor = contextProvider.getActor();
                if (actor instanceof PublicCompany && actor != this.orComp) {
                    this.orComp = (PublicCompany) actor;
                    updateSidebarData();
                }
            }

            int computedPhase = determineActivePhase(actions);
            boolean hasStandardActions = computedPhase > 0;

     
     // 5. RENDER SPECIAL MODE (Exclusive Interrupts Only)
            if (isMaARound || (!specialActions.isEmpty() && !hasStandardActions)) {
                this.specialModeActive = true;
                this.activePhase = 0;
                setStandardPanelsVisible(false);

                if (contextProvider != null) {
                    updateSpecialHeader(contextProvider);
                } else if (isMaARound && this.orComp != null) {
                    lblCompanyInfo.setText("<html><center><font size='6'><b>" + this.orComp.getId() + "</b></font></center></html>");
                    lblCompanyInfo.setBackground(this.orComp.getBgColour());
                    lblCompanyInfo.setForeground(this.orComp.getFgColour());
                    lblCompanyInfo.setVisible(true);

                    if (lblPhaseInstruction != null) {
                        lblPhaseInstruction.setText("<html><center><font size='4'><b>M&A Actions</b></font></center></html>");
                        lblPhaseInstruction.setBackground(this.orComp.getBgColour());
                        lblPhaseInstruction.setForeground(this.orComp.getFgColour());
                        lblPhaseInstruction.setVisible(true);
                    }
                }

                if (specialPanel != null && specialContainer != null) {
                           specialContainer.setVisible(true);
                    specialPanel.removeAll();
                    for (PossibleAction spa : specialActions) {
                        addSpecialActionButton(spa);
                    }
                    specialPanel.revalidate();
                    specialPanel.repaint();

                    if (sidebarPanel != null) {
                        sidebarPanel.revalidate();
                        sidebarPanel.repaint();
                    }
                }
                return;
            }

            // --- 6. STANDARD MODE (OR MIXED) ---
            this.specialModeActive = false;

            if (!specialActions.isEmpty() && specialPanel != null && specialContainer != null) {
                specialContainer.setVisible(true);
                specialPanel.removeAll();
                for (PossibleAction spa : specialActions) {
                    addSpecialActionButton(spa);
                }
                specialPanel.revalidate();
            } else if (specialContainer != null) {
                specialContainer.setVisible(false);
            }

            activePhase = computedPhase;
            setStandardPanelsVisible(true);

            if (activePhase == 1 || activePhase == 2) {
                boolean hasSelection = (orUIManager != null && orUIManager.getMap() != null && orUIManager.getMap().getSelectedHex() != null);
                enableConfirm(hasSelection);
            }



            distributeStandardActions(actions);
            updateSidebarData();
            updatePhaseSpecifics();

            if (activePhase == 1 || activePhase == 2) {
                boolean hasSelection = (orUIManager != null && orUIManager.getMap() != null && orUIManager.getMap().getSelectedHex() != null);
                enableConfirm(hasSelection);
            }

            if (sidebarPanel != null)
                sidebarPanel.revalidate(); // Ensure standard mode revalidates too
            sidebarPanel.repaint();

        } catch (Exception e) {
            log.error("Error in updateDynamicActions", e);
        }
    }

    private void bindActionHotkey(ActionButton btn, PossibleAction action) {
        int key = 0;
        String actionName = action.getClass().getSimpleName();

        // 1. Check if the Action defines a hotkey (via Interface)
        if (action instanceof GuiTargetedAction) {
            key = ((GuiTargetedAction) action).getHotkey();
        }

        // --- START FIX ---
        // 1. Capture the Pass button for external access (GlobalHotkeyManager)
        if (action instanceof NullAction) {
            this.directPassButton = btn;
        }

        // 2. RESTORE 'ENTER' MAPPING FOR PASS
        // We strictly bind ENTER to the Pass button (NullAction).
        if (action instanceof NullAction) {
            this.directPassButton = btn; // Keep reference for Global Manager
            key = KeyEvent.VK_ENTER;
        }

        if (key != 0) {
            InputMap im = this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            ActionMap am = this.getActionMap();

            // Unique command name using identityHashCode to avoid collisions
            String commandKey = "invoke_" + actionName + "_" + System.identityHashCode(action);

            im.put(KeyStroke.getKeyStroke(key, 0), commandKey);

            // log.info("DEBUG: Mapped KeyCode " + key + " to command " + commandKey);

            am.put(commandKey, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    log.info("DEBUG: Hotkey triggered for " + actionName);
                    if (btn.isEnabled() && btn.isVisible()) {
                        log.info("DEBUG: Button is valid. Clicking...");
                        btn.doClick();
                    } else {
                        log.info("DEBUG: FAILURE - Button disabled or invisible.");
                    }
                }
            });
        }

    }

    /**
     * Helper to safely click the Pass/Done button from the Global Manager.
     * Returns true if the button was clicked, false otherwise.
     */
    public boolean clickPassButton() {
        if (directPassButton != null && directPassButton.isShowing() && directPassButton.isEnabled()) {
            directPassButton.doClick();
            return true;
        }
        return false;
    }

    // We are modifying ORPanel.java to ensure the scanner is robust.

    // ... (lines of unchanged context code) ...

    // --- START FIX ---
    public void handleEnterPress() {

        // 1. Check Special Panel (Discards, etc)
        if (specialContainer != null && specialContainer.isVisible()) {
            if (scanAndClickBestButton(specialPanel))
                return;
        }

        // 2. Check Active Phase Panel
        JPanel activePanel = null;
        if (activePhase == 1)
            activePanel = phase1Panel;
        else if (activePhase == 2)
            activePanel = phase2Panel;
        else if (activePhase == 3)
            activePanel = phase3Panel;
        else if (activePhase == 4)
            activePanel = phase4Panel;
else if (activePhase == 5)
            activePanel = phase5Panel;
        if (activePanel != null && activePanel.isVisible()) {
            if (scanAndClickBestButton(activePanel))
                return;
        }

        // 3. Check Sidebar (Fallback)
        if (sidebarPanel != null && sidebarPanel.isVisible()) {
            // scanAndClickBestButton(sidebarPanel); // Optional: Enable if sidebar has
            // "Pay" buttons
        }

        // 4. Default Fallback
        if (btnDone != null && btnDone.isVisible() && btnDone.isEnabled()) {
            btnDone.doClick();
        } else {
        }
    }

    // We are modifying ORPanel.java
    // Fix: Add 'decline' to the scoring logic to catch the Prussian Pass action.

    // ... (existing imports) ...

    private boolean scanAndClickBestButton(Container container) {
        if (container == null)
            return false;

        AbstractButton bestCandidate = null;
        int bestScore = 0;

        Component[] comps = container.getComponents();
        for (Component comp : comps) {
            // Recursive dive
            if (comp instanceof Container && !(comp instanceof AbstractButton)) {
                if (scanAndClickBestButton((Container) comp))
                    return true;
                continue;
            }

            if (comp instanceof AbstractButton) {
                AbstractButton btn = (AbstractButton) comp;
                if (!btn.isEnabled() || !btn.isVisible())
                    continue;

                String text = btn.getText();
                // Strip HTML tags for cleaner logging/matching if needed,
                // but .contains() usually works fine on the raw string.
                String lowerText = (text != null) ? text.toLowerCase() : "";
                String cmd = (btn.getActionCommand() != null) ? btn.getActionCommand() : "";
                int score = 0;

                // --- SCORING LOGIC ---

                // 1. CRITICAL FLOW (Score 10)
                if (cmd.equals(PAYOUT_CMD) ||
                        lowerText.contains("pay") ||
                        lowerText.contains("confirm") ||
                        lowerText.contains("yes")) {
                    score = 10;
                }

                // 2. EXPLICIT SKIP / REFUSAL (Score 8)
                // ADDED "decline" HERE
                else if (lowerText.contains("skip") ||
                        lowerText.contains("decline")) {
                    score = 8;
                }

                // 3. GENERIC COMPLETION (Score 5)
                else if (cmd.equals(DONE_CMD) ||
                        lowerText.contains("done") ||
                        lowerText.contains("end turn") ||
                        lowerText.contains("pass")) {
                    score = 5;
                }

                // 4. SECONDARY OPTIONS (Score 3)
                else if (cmd.equals(WITHHOLD_CMD) || cmd.equals(SPLIT_CMD) ||
                        lowerText.contains("hold") || lowerText.contains("withhold") ||
                        lowerText.contains("split")) {
                    score = 3;
                }

                // 5. EXPLICITLY IGNORE (Score -1)
                // "Start" is ignored, which correctly handles "Start (Fold M2)"
                else if (lowerText.contains("buy") ||
                        lowerText.contains("undo") ||
                        lowerText.contains("redo") ||
                        lowerText.contains("start")) {
                    score = -1;
                }

                // LOGGING

                if (score > bestScore) {
                    bestScore = score;
                    bestCandidate = btn;
                }
            }
        }

        if (bestCandidate != null) {
            bestCandidate.doClick();
            return true;
        }
        return false;
    }

    @Override
    protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {

            boolean result = super.processKeyBinding(ks, e, condition, pressed);
            return result;
        }
        return super.processKeyBinding(ks, e, condition, pressed);
    }

}