package net.sf.rails.ui.swing;

import java.awt.*;
import java.awt.event.*;
import java.util.EnumSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.*;
import javax.swing.border.BevelBorder;

import net.sf.rails.common.LocalText;
import net.sf.rails.game.*;
import net.sf.rails.game.financial.*;
import net.sf.rails.game.round.RoundFacade;
import net.sf.rails.sound.SoundManager;
import net.sf.rails.ui.swing.elements.*;
import net.sf.rails.ui.swing.hexmap.HexHighlightMouseListener;
import net.sf.rails.util.Util;
import net.sf.rails.ui.swing.ORPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rails.game.action.*;

import com.google.common.collect.Iterables;

/**
 * This displays the Auction Window.
 * Refactored to remove separate Info column/button and integrate details into
 * RailCard tooltips.
 */
public class StartRoundWindow extends JFrame implements ActionListener, KeyListener, ActionPerformer, DialogOwner {

    private static final long serialVersionUID = 1L;

    // Gap sizes between screen cells, in pixels
    private static final int NARROW_GAP = 2;
    private static final int WIDE_GAP = 5;
    // Bits for specifying where to apply wide gaps
    private static final int WIDE_LEFT = 1;
    private static final int WIDE_RIGHT = 2;
    private static final int WIDE_TOP = 4;
    private static final int WIDE_BOTTOM = 8;

    private ActionButton undoButton;

    protected static final String[] itemStatusTextKeys = new String[] { "Status_Unavailable", "Status_Biddable",
            "Status_Buyable",
            "Status_Selectable", "Status_Auctioned",
            "Status_NeedingSharePrice", "Status_Sold" };

    /* Keys of dialogs owned by this class */
    public static final String COMPANY_START_PRICE_DIALOG = "CompanyStartPrice";

    private static final Logger log = LoggerFactory.getLogger(StartRoundWindow.class);

    private JPanel statusPanel;
    private JPanel buttonPanel;

    private GridBagLayout gb;
    private GridBagConstraints gbc;

    // Grid elements per function
    private Caption[] itemName;
    private int[] itemNameXOffset;
    private int itemNameYOffset;
    private Field[] basePrice;
    private int[] basePriceXOffset;
    private int basePriceYOffset;
    private Field[] minBid;
    private int[] minBidXOffset;
    private int minBidYOffset;

    // Separator Fields
    private JComponent[] verticalSeparators;
    private int[] separatorXOffset;

    private Field[][] bidPerPlayer;
    private int[] bidPerPlayerXOffset;
    private int bidPerPlayerYOffset;
    private Field[] playerBids;
    private int[] playerBidsXOffset;
    private int playerBidsYOffset;
    private Field[] playerFree;
    private int[] playerFreeCashXOffset;
    private int playerFreeCashYOffset;

    // REMOVED: Info column arrays (info, infoXOffset, infoYOffset)

    private Field[] itemStatus; // Remains invisible, only used for status tooltip

    private int[] playerCaptionXOffset;
    private int upperPlayerCaptionYOffset, lowerPlayerCaptionYOffset;
    private Field[][] upperPlayerCaption;
    private Field[] lowerPlayerCaption;
    private JComponent[][] fields;

    private ActionButton bidButton;
    private ActionButton buyButton;

    private ActionButton aiIRButton;
    private JSpinner bidAmount;
    private SpinnerNumberModel spinnerModel;
    private ActionButton passButton;

    private RailCard[] cards;

    private PlayerManager players;

    private int[] crossIndex;
    protected StartRound round;
    private GameUIManager gameUIManager;
    protected StartPacket startPacket;
    protected boolean multipleColumns;
    protected int numberOfColumns;
    protected int numberOfRows;
    protected int columnWidth = 0;

    // For the non-modal dialog to ask for a company starting share price.
    protected JDialog currentDialog;
    protected PossibleAction currentDialogAction;
    protected SortedSet<StockSpace> startSpaces;

    protected PossibleActions possibleActions;
    protected PossibleAction immediateAction;

    // We don't use the ItemGroup for the click buttons anymore to avoid "sticky"
    // selection
    private final ButtonGroup itemGroup = new ButtonGroup();
    private ClickField dummyButton;

    private StartRound.Bidding includeBidding;
    private boolean includeBuying;
    private boolean showBasePrices;

    private ORUIManager orUIManager;
    private int selectedItemIndex = -1;

    private void initCells() {
        int lastX = -1;
        int lastY = 0;

        int np = players.getNumberOfPlayers();
        int ni = round.getNumberOfStartItems();
        cards = new RailCard[ni];

        basePrice = new Field[ni];
        minBid = new Field[ni];

        verticalSeparators = new JComponent[numberOfColumns];
        separatorXOffset = new int[numberOfColumns];

        bidPerPlayer = new Field[ni][np];
        // REMOVED: info = new Field[ni];
        itemStatus = new Field[ni];
        upperPlayerCaption = new Field[numberOfColumns][np];
        lowerPlayerCaption = new Field[np];
        playerBids = new Field[np];
        playerFree = new Field[np];

        itemNameXOffset = new int[numberOfColumns];
        if (showBasePrices)
            basePriceXOffset = new int[numberOfColumns];
        if (includeBidding == StartRound.Bidding.ON_ITEMS)
            minBidXOffset = new int[numberOfColumns];
        bidPerPlayerXOffset = new int[numberOfColumns];
        playerCaptionXOffset = new int[numberOfColumns];

        // REMOVED: infoXOffset array allocation

        if (includeBidding != StartRound.Bidding.NO)
            playerBidsXOffset = new int[numberOfColumns];
        playerFreeCashXOffset = new int[numberOfColumns];

        upperPlayerCaptionYOffset = ++lastY;

        for (int col = 0; col < numberOfColumns; col++) {
            itemNameXOffset[col] = ++lastX;
            if (col == 0)
                itemNameYOffset = ++lastY;
            if (showBasePrices) {
                basePriceXOffset[col] = ++lastX;
                if (col == 0)
                    basePriceYOffset = lastY;
            }
            if (includeBidding == StartRound.Bidding.ON_ITEMS) {
                minBidXOffset[col] = ++lastX;
                if (col == 0)
                    minBidYOffset = lastY;
            }

            separatorXOffset[col] = ++lastX;

            bidPerPlayerXOffset[col] = playerCaptionXOffset[col] = ++lastX;
            if (col == 0)
                bidPerPlayerYOffset = lastY;

            // REMOVED: Info column logic
            // infoXOffset[col] = bidPerPlayerXOffset[col] + np;

            lastX += np; // Skip over the player columns

            if (col == 0) {
                // infoYOffset = lastY; // Removed
                columnWidth = lastX + 1;
            }

            // Bottom rows
            lastY += (numberOfRows - 1);
            if (includeBidding != StartRound.Bidding.NO) {
                playerBidsXOffset[col] = bidPerPlayerXOffset[col];
                if (col == 0)
                    playerBidsYOffset = ++lastY;
            }
            playerFreeCashXOffset[col] = bidPerPlayerXOffset[col];

            if (col == 0) {
                playerFreeCashYOffset = ++lastY;
                lowerPlayerCaptionYOffset = ++lastY;

                fields = new JComponent[columnWidth * numberOfColumns][2 + lastY];
                log.debug("Columns={} (width/col={} nbOfCol={}) rows={}", columnWidth * numberOfColumns,
                        columnWidth, numberOfColumns, 2 + lastY);
            }

            addField(new Caption(LocalText.getText("ITEM")),
                    itemNameXOffset[col], 0, 1, 2,
                    WIDE_LEFT + WIDE_RIGHT + WIDE_BOTTOM);

            if (showBasePrices) {
                addField(new Caption(LocalText.getText(includeBidding == StartRound.Bidding.ON_ITEMS
                        ? "BASE_PRICE"
                        : "PRICE")), basePriceXOffset[col], 0, 1, 2,
                        WIDE_BOTTOM);
            }
            if (includeBidding == StartRound.Bidding.ON_ITEMS) {
                addField(new Caption(LocalText.getText("MINIMUM_BID")),
                        minBidXOffset[col], 0, 1, 2, WIDE_BOTTOM + WIDE_RIGHT);
            }

            // Vertical Separator
            JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
            sep.setForeground(Color.GRAY);
            int totalHeight = lastY + 2;
            addField(sep, separatorXOffset[col], 0, 1, totalHeight, 0);

            addField(new Caption(LocalText.getText("PLAYERS")),
                    playerCaptionXOffset[col], 0, np, 1, 0);
            for (int i = 0; i < np; i++) {
                upperPlayerCaption[col][i] = new Field(players.getPlayerByPosition(i).getPlayerNameModel());
                addField(upperPlayerCaption[col][i], playerCaptionXOffset[col] + i,
                        upperPlayerCaptionYOffset, 1, 1, WIDE_BOTTOM);
            }
        }

        int row, col;
        for (int i = 0; i < ni; i++) {
            final StartItem si = round.getStartItem(i);

            if (multipleColumns) {
                row = si.getRow() - 1;
                col = si.getColumn() - 1;
            } else {
                row = i;
                col = 0;
            }

            cards[i] = new RailCard(si, itemGroup);

            // 1. Enable Clicks
            cards[i].addActionListener(this);
            log.error("Added ActionListener to RailCard for StartItem index {}", si.getIndex());

            // Apply scaling
            cards[i].setScale(1.3);

            // Layout
            addField(cards[i], itemNameXOffset[col], itemNameYOffset + row,
                    1, 1, WIDE_LEFT + WIDE_RIGHT);

            if (showBasePrices) {
                basePrice[i] = new Field(si.getBasePriceModel());
                addField(basePrice[i], basePriceXOffset[col], basePriceYOffset + row,
                        1, 1, 0);
            }

            if (includeBidding == StartRound.Bidding.ON_ITEMS) {
                minBid[i] = new Field(round.getMinimumBidModel(i));
                addField(minBid[i], minBidXOffset[col], minBidYOffset + row,
                        1, 1, WIDE_RIGHT);
            }

            for (int j = 0; j < np; j++) {
                bidPerPlayer[i][j] = new Field(round.getBidModel(i, players.getPlayerByPosition(j)));
                addField(bidPerPlayer[i][j], bidPerPlayerXOffset[col] + j, bidPerPlayerYOffset + row,
                        1, 1, 0);
            }

            // --- CONSOLIDATION: Info + Hex Highlighting on Card ---
            Certificate cert = si.getPrimary();
            Company comp = null;
            if (cert instanceof PublicCertificate) {
                comp = (PublicCompany) cert.getParent();
            } else if (cert instanceof PrivateCompany) {
                comp = (PrivateCompany) cert;
            }

            if (comp != null) {
                // 1. Set Rich Tooltip (replaces Info button click) using RailCard helper
                // Ensure the RailCard class has this method from previous updates
                cards[i].setCompanyDetailsTooltip(comp);

                // 2. Add Hex Highlighting (Map lights up when hovering card)
                // Passing 'si' (StartItem) as the token source
                HexHighlightMouseListener.addMouseListener(cards[i], gameUIManager.getORUIManager(), si);
            }
            // --- END CONSOLIDATION ---

            // Invisible field, only used to hold current item status.
            itemStatus[i] = new Field(si.getStatusModel());
        }

        // Player money
        boolean firstBelowTable = true;
        if (includeBidding != StartRound.Bidding.NO) {
            addField(new Caption(LocalText.getText("BID")), basePriceXOffset[0], playerBidsYOffset,
                    1, 1, WIDE_TOP + WIDE_RIGHT);

            for (int i = 0; i < np; i++) {
                playerBids[i] = new Field(round.getBlockedCashModel(players.getPlayerByPosition(i)));
                addField(playerBids[i], playerBidsXOffset[0] + i, playerBidsYOffset,
                        1, 1, WIDE_TOP);
            }

            firstBelowTable = false;
        }

        int cashLabelX = (showBasePrices) ? basePriceXOffset[0] : itemNameXOffset[0];

        addField(new Caption(
                LocalText.getText(includeBidding != StartRound.Bidding.NO ? "FREE" : "CASH")),
                cashLabelX, playerFreeCashYOffset, 1, 1,
                WIDE_RIGHT + (firstBelowTable ? WIDE_TOP : 0));

        for (int i = 0; i < np; i++) {
            playerFree[i] = new Field(includeBidding != StartRound.Bidding.NO
                    ? round.getFreeCashModel(players.getPlayerByPosition(i))
                    : players.getPlayerByPosition(i).getWallet());
            addField(playerFree[i], playerFreeCashXOffset[0] + i, playerFreeCashYOffset, 1, 1,
                    firstBelowTable ? WIDE_TOP : 0);
        }

        for (int i = 0; i < np; i++) {
            lowerPlayerCaption[i] = new Field(players.getPlayerByPosition(i).getPlayerNameModel());
            addField(lowerPlayerCaption[i], playerFreeCashXOffset[0] + i, playerFreeCashYOffset + 1, 1, 1, WIDE_TOP);
        }

        dummyButton = new ClickField("", "", "", this, itemGroup);
    }

    private void addField(JComponent comp, int x, int y, int width, int height, int wideGapPositions) {
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = width;
        gbc.gridheight = height;
        gbc.weightx = gbc.weighty = 0.5;
        gbc.fill = GridBagConstraints.BOTH;

        int padTop = (wideGapPositions & WIDE_TOP) > 0 ? WIDE_GAP : NARROW_GAP;
        int padLeft = (wideGapPositions & WIDE_LEFT) > 0 ? WIDE_GAP : NARROW_GAP;
        int padBottom = (wideGapPositions & WIDE_BOTTOM) > 0 ? WIDE_GAP : NARROW_GAP;
        int padRight = (wideGapPositions & WIDE_RIGHT) > 0 ? WIDE_GAP : NARROW_GAP;

        gbc.insets = new Insets(padTop, padLeft, padBottom, padRight);

        statusPanel.add(comp, gbc);
        if (fields != null && x < fields.length && y < fields[0].length) {
            fields[x][y] = comp;
        }
    }

    @Override
    public boolean processImmediateAction() {
        if (immediateAction != null) {
            log.debug("ImmediateAction = {}", immediateAction);
            // Make a local copy and discard the original,
            // so that it's not going to loop.
            PossibleAction nextAction = immediateAction;
            immediateAction = null;
            if (nextAction instanceof StartItemAction) {
                StartItemAction action = (StartItemAction) nextAction;
                if (action instanceof BuyStartItem) {
                    requestStartPrice((BuyStartItem) action);
                    return false;
                }
            }
        }
        return true;
    }

    protected boolean requestStartPrice(BuyStartItem activeItem) {
        if (activeItem.hasSharePriceToSet()) {
            String compName = activeItem.getCompanyToSetPriceFor();
            StockMarket stockMarket = gameUIManager.getRoot().getStockMarket();

            // Get a sorted prices List
            // TODO: should be included in BuyStartItem

            if (activeItem.containsStartSpaces()) {
                startSpaces = new TreeSet<StockSpace>();
                for (String s : activeItem.startSpaces()) {
                    startSpaces.add(stockMarket.getStockSpace(s));
                }
            } else {
                startSpaces = stockMarket.getStartSpaces();
            }

            String[] options = new String[startSpaces.size()];
            int i = 0;
            for (StockSpace space : startSpaces) {
                options[i++] = gameUIManager.format(space.getPrice());
            }

            RadioButtonDialog dialog = new RadioButtonDialog(
                    COMPANY_START_PRICE_DIALOG,
                    this,
                    this,
                    LocalText.getText("PleaseSelect"),
                    LocalText.getText("WHICH_START_PRICE",
                            players.getCurrentPlayer().getId(),
                            compName),
                    options,
                    -1);

            setCurrentDialog(dialog, activeItem);
        }

        return true;
    }

    @Override
    public JDialog getCurrentDialog() {
        return currentDialog;
    }

    @Override
    public PossibleAction getCurrentDialogAction() {
        return currentDialogAction;
    }

    @Override
    public void setCurrentDialog(JDialog dialog, PossibleAction action) {
        if (currentDialog != null) {
            currentDialog.dispose();
        }

        currentDialog = dialog;
        currentDialogAction = action;

        disableButtons();
    }

    @Override
    public void dialogActionPerformed() {
        if (currentDialog instanceof RadioButtonDialog && currentDialogAction instanceof BuyStartItem) {
            RadioButtonDialog dialog = (RadioButtonDialog) currentDialog;
            BuyStartItem action = (BuyStartItem) currentDialogAction;

            int index = dialog.getSelectedOption();
            if (index >= 0) {
                int price = Iterables.get(startSpaces, index).getPrice();
                action.setAssociatedSharePrice(price);
                process(action);
            } else {
                // No selection done - no action
                return;
            }
        }
    }

    protected void disableButtons() {
        if (includeBidding != StartRound.Bidding.NO) {
            bidButton.setEnabled(false);
        }

        if (includeBuying) {
            buyButton.setEnabled(false);
        }

        passButton.setEnabled(false);
    }

    public void close() {
        this.dispose();
    }

    public void setSRPlayerTurn() {
        int playerIndex = players.getCurrentPlayer().getIndex();
        for (int i = 0; i < players.getNumberOfPlayers(); i++) {
            for (int j = 0; j < numberOfColumns; j++) {
                upperPlayerCaption[j][i].setHighlight(i == playerIndex);
            }
            lowerPlayerCaption[i].setHighlight(i == playerIndex);
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public boolean process(PossibleAction action) {
        return gameUIManager.processAction(action);
    }

    public void updatePlayerOrder(List<String> newPlayerNames) {

        // Multiple columns are here ignored for now.
        // When is this called?
        int np = players.getNumberOfPlayers();

        int[] xref = new int[np];
        List<String> oldPlayerNames = gameUIManager.getCurrentGuiPlayerNames();
        for (int i = 0; i < np; i++) {
            xref[i] = oldPlayerNames.indexOf(newPlayerNames.get(i));
        }
        log.debug("SRW: old player list: {}", Util.join(oldPlayerNames.toArray(new String[0]), ","));
        log.debug("SRW: new player list: {}", Util.join(newPlayerNames.toArray(new String[0]), ","));

        JComponent[] cells = new Cell[np];
        GridBagConstraints[] constraints = new GridBagConstraints[np];
        JComponent f;
        for (int y = upperPlayerCaptionYOffset; y <= lowerPlayerCaptionYOffset; y++) {
            for (int i = 0, x = playerCaptionXOffset[0]; i < np; i++, x++) {
                cells[i] = fields[x][y];
                constraints[i] = gb.getConstraints(cells[i]);
                statusPanel.remove(cells[i]);
            }
            for (int i = 0, x = playerCaptionXOffset[0]; i < np; i++, x++) {
                f = fields[x][y] = cells[xref[i]];
                statusPanel.add(f, constraints[i]);
            }
        }
        for (int i = 0, x = playerCaptionXOffset[0]; i < np; i++, x++) {
            for (int col = 0; col < numberOfColumns; col++) {
                upperPlayerCaption[col][i] = (Field) fields[x][upperPlayerCaptionYOffset];
            }
            lowerPlayerCaption[i] = (Field) fields[x][lowerPlayerCaptionYOffset];
        }

        gameUIManager.packAndApplySizing(this);
    }

    // Method for the Start Round (IR) AI button
    public void enableAIIRButton(boolean enable) {
        // Assuming aiIRbutton is defined in ORPanel.java
        if (aiIRButton != null) {
            aiIRButton.setEnabled(enable);
            aiIRButton.setVisible(enable);
        }
    }

    public void init(StartRound round, GameUIManager parent, ORUIManager orUIManager) {
        this.round = round;
        this.orUIManager = orUIManager;
        startPacket = round.getStartPacket();
        multipleColumns = startPacket.isMultipleColumns();
        if (multipleColumns) {
            numberOfColumns = startPacket.getNumberOfColumns();
            numberOfRows = startPacket.getNumberOfRows();
        } else {
            numberOfRows = round.getNumberOfStartItems();
            numberOfColumns = 1;
        }
        includeBidding = round.hasBidding();
        includeBuying = round.hasBuying();
        showBasePrices = round.hasBasePrices();
        gameUIManager = parent;
        possibleActions = gameUIManager.getGameManager().getPossibleActions();

        setTitle(LocalText.getText("START_ROUND_TITLE",
                String.valueOf(round.getStartRoundNumber())));
        getContentPane().setLayout(new BorderLayout());

        statusPanel = new JPanel();
        gb = new GridBagLayout();
        statusPanel.setLayout(gb);
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.setOpaque(true);

        buttonPanel = new JPanel();

        if (includeBuying) {
            buyButton = new ActionButton(RailsIcon.AUCTION_BUY);
            buyButton.setMnemonic(KeyEvent.VK_B);
            buyButton.addActionListener(this);
            buyButton.setEnabled(false);
            buttonPanel.add(buyButton);
        }

        if (includeBidding != StartRound.Bidding.NO) {
            bidButton = new ActionButton(RailsIcon.BID);
            bidButton.setMnemonic(KeyEvent.VK_D);
            bidButton.addActionListener(this);
            bidButton.setEnabled(false);
            buttonPanel.add(bidButton);

            spinnerModel = new SpinnerNumberModel(999, 0, null, 1);
            bidAmount = new JSpinner(spinnerModel);
            bidAmount.setPreferredSize(new Dimension(50, 28));
            bidAmount.setEnabled(false);
            buttonPanel.add(bidAmount);
        }

        passButton = new ActionButton(RailsIcon.PASS);
        passButton.setMnemonic(KeyEvent.VK_P);
        passButton.addActionListener(this);
        passButton.setEnabled(false);
        buttonPanel.add(passButton);

        // Initialize Undo Button
        undoButton = new ActionButton(RailsIcon.UNDO);
        undoButton.setToolTipText("Undo last action (Z)");
        undoButton.addActionListener(this);
        undoButton.setEnabled(false);
        buttonPanel.add(undoButton);


        buttonPanel.setOpaque(true);

        gbc = new GridBagConstraints();

        players = gameUIManager.getRoot().getPlayerManager();

        crossIndex = new int[round.getStartPacket().getNumberOfItems()];

        for (int i = 0; i < round.getNumberOfStartItems(); i++) {
            final StartItem item = round.getStartItem(i);
            crossIndex[item.getIndex()] = i;
        }

        // REMOVED: infoIcon creation

        initCells();

        getContentPane().add(statusPanel, BorderLayout.NORTH);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);
        setLocation(300, 150);
        setSize(275, 325);
        gameUIManager.setMeVisible(this, true);
        requestFocus();

        // Setup Hotkeys using InputMap/ActionMap (Better than KeyListener)
        setupHotkeys();

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        final JFrame thisFrame = this;
        final GameUIManager guiMgr = gameUIManager;
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (GameUIManager.confirmQuit(thisFrame)) {
                    thisFrame.dispose();
                    guiMgr.terminate();
                }
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                guiMgr.getWindowSettings().set(thisFrame);
            }

            @Override
            public void componentResized(ComponentEvent e) {
                guiMgr.getWindowSettings().set(thisFrame);
            }
        });

        gameUIManager.packAndApplySizing(this);
    }

    private void setupHotkeys() {
        // Hotkeys are now handled globally by GlobalHotkeyManager.
        // This method remains empty to satisfy the call in init().
    }


    @Override
    public void updateStatus(boolean myTurn) {
        log.info("SRW: updateStatus STARTED. MyTurn={}", myTurn);

        // 1. Reset all cards to their default state based on game status
        for (int i = 0; i < round.getNumberOfStartItems(); i++) {
            StartItem si = round.getStartItem(i);
            int status = si.getStatus();
            
            // Clear previous actions
            cards[i].clearPossibleActions();

            if (status == StartItem.SOLD) {
                cards[i].setState(RailCard.State.DISABLED);
                String tooltipKey = itemStatusTextKeys[status];
                cards[i].setToolTipText(LocalText.getText(tooltipKey));
            } else {
                cards[i].setState(RailCard.State.PASSIVE);
                Certificate cert = si.getPrimary();
                Company comp = null;
                if (cert instanceof PublicCertificate) {
                    comp = (PublicCompany) cert.getParent();
                } else if (cert instanceof PrivateCompany) {
                    comp = (PrivateCompany) cert;
                }
                if (comp != null) {
                    cards[i].setCompanyDetailsTooltip(comp);
                }
            }
        }

        dummyButton.setSelected(true);

        if (includeBuying) buyButton.setEnabled(false);
        if (includeBidding != StartRound.Bidding.NO) {
            bidButton.setEnabled(false);
            bidAmount.setEnabled(false);
        }
        passButton.setEnabled(false);
        
        // Disable new buttons initially
        if (undoButton != null) undoButton.setEnabled(false);
        
        // AI Button logic removed as requested

        RoundFacade currentRound = gameUIManager.getCurrentRound();
        if (!(currentRound instanceof StartRound)) {
            log.info("SRW: updateStatus ABORT - Not StartRound.");
            return;
        }

        if (!myTurn) {
            log.info("SRW: updateStatus ABORT - Not My Turn.");
            return;
        }

        List<StartItemAction> actions = possibleActions.getType(StartItemAction.class);

        // --- ENABLE UNDO BUTTON ---
        GameAction undoAction = null;
        List<GameAction> gameActions = possibleActions.getType(GameAction.class);
        for (GameAction ga : gameActions) {
            if (ga.getMode() == GameAction.Mode.UNDO) {
                undoAction = ga;
                break;
            }
        }
        if (undoButton != null && undoAction != null) {
            undoButton.setEnabled(true);
            undoButton.setPossibleAction(undoAction);
        }

        if (actions == null) {
            log.warn("SRW: actions list is NULL");
        } else if (actions.isEmpty()) {
            log.warn("SRW: actions list is EMPTY");
            close();
            return;
        } else {
            log.info("SRW: Found {} StartItemActions to distribute.", actions.size());
        }

        setSRPlayerTurn();

        boolean buyAllowed = false;
        boolean bidAllowed = false;
        boolean selected = false;
        BuyStartItem buyAction;

        // 2. Distribute Actions Loop
        for (StartItemAction action : actions) {
            int j = action.getItemIndex();
            int i = crossIndex[j];
            StartItem item = action.getStartItem();

            // LOGGING ASSIGNMENT
            cards[i].setPossibleAction(action);
            
            if (action instanceof BuyStartItem) {
                buyAction = (BuyStartItem) action;
                
                if (i == selectedItemIndex) {
                    cards[i].setState(RailCard.State.SELECTED);
                    if (buyButton != null && includeBuying) {
                        buyButton.setEnabled(true);
                        buyButton.setPossibleAction(action);
                    }
                } else {
                    cards[i].setState(RailCard.State.ACTIONABLE);
                }

                selected = buyAction.isSelected();
                if (selected) {
                    cards[i].setState(RailCard.State.SELECTED);
                }

                if (includeBidding == StartRound.Bidding.ON_ITEMS && showBasePrices)
                    minBid[i].setText("");
                buyAllowed = true;

            } else if (action instanceof BidStartItem) {
                BidStartItem bidAction = (BidStartItem) action;
                selected = bidAction.isSelected();
                if (selected) {
                    bidButton.addPossibleAction(action);
                    bidButton.setPossibleAction(action);
                    int mb = bidAction.getMinimumBid();
                    spinnerModel.setMinimum(mb);
                    spinnerModel.setStepSize(bidAction.getBidIncrement());
                    spinnerModel.setValue(mb);
                }
                
                if (selected) {
                    cards[i].setState(RailCard.State.SELECTED);
                } else {
                    cards[i].setState(RailCard.State.ACTIONABLE);
                }
                
                bidAllowed = selected;
                if (includeBidding == StartRound.Bidding.ON_ITEMS) {
                    minBid[i].setText(Bank.format(item, item.getMinimumBid()));
                }
            }
        }
        

        // 3. Pass Button Logic
        boolean passAllowed = false;
        List<NullAction> inactiveItems = possibleActions.getType(NullAction.class);
        if (inactiveItems != null && !inactiveItems.isEmpty()) {
            NullAction na = inactiveItems.get(0);
            passButton.setRailsIcon(RailsIcon.getByConfigKey(na.getMode().name()));
            passAllowed = true;
            passButton.setPossibleAction(na);
            passButton.setMnemonic(KeyEvent.VK_P);
        }

        if (includeBuying) buyButton.setEnabled(buyAllowed);
        if (includeBidding != StartRound.Bidding.NO) {
            bidButton.setEnabled(bidAllowed);
            bidAmount.setEnabled(bidAllowed);
        }
        passButton.setEnabled(passAllowed);

        // Replaced pack() with validate/repaint to preserve user-resized window dimensions
        revalidate();
        repaint(); 
        requestFocus();
        log.info("SRW: updateStatus COMPLETED.");
    }


    @Override
    public void actionPerformed(ActionEvent actor) {
        JComponent source = (JComponent) actor.getSource();

      

        // Identify RailCard Clicks
        int clickedIndex = -1;
        for (int k = 0; k < cards.length; k++) {
            if (source == cards[k] || SwingUtilities.isDescendingFrom(source, cards[k])) {
                clickedIndex = k;
                log.info("SRW: Source identified as descending from RailCard index {}", k);
                break;
            }
        }

        if (clickedIndex != -1) {
            RailCard card = cards[clickedIndex];
            java.util.List<PossibleAction> actions = card.getPossibleActions();
            
            if (actions == null || actions.isEmpty()) {
                // Log the state to help debug why actions might be missing
                log.warn("SRW: RailCard index {} clicked, but has NO actions assigned. State: {}", clickedIndex, card.getState());
                return;
            }
            
            StartItemAction currentActiveItem = (StartItemAction) actions.get(0);
            SoundManager.notifyOfClickFieldSelection(currentActiveItem);

            if (currentActiveItem instanceof BuyStartItem) {
                BuyStartItem bsi = (BuyStartItem) currentActiveItem;

                if (clickedIndex == selectedItemIndex) {
                    if (bsi.hasSharePriceToSet()) {
                        if (requestStartPrice(bsi)) return;
                    }
                    process(bsi);
                    selectedItemIndex = -1;
                } else {
                    selectedItemIndex = clickedIndex;
                    for (int k = 0; k < cards.length; k++) {
                        if (cards[k] != null && cards[k].isEnabled()) {
                            cards[k].setState(k == selectedItemIndex ? RailCard.State.SELECTED : RailCard.State.ACTIONABLE);
                        }
                    }
                    if (buyButton != null && includeBuying) {
                        buyButton.setEnabled(true);
                        buyButton.setPossibleAction(bsi);
                    }
                }
            } else if (currentActiveItem instanceof BidStartItem) {
                BidStartItem bidAction = (BidStartItem) currentActiveItem;
                if (includeBuying) buyButton.setEnabled(false);

                if (bidAction.isSelectForAuction()) {
                    passButton.setPossibleAction(currentActiveItem);
                    passButton.setEnabled(true);
                    passButton.setRailsIcon(RailsIcon.SELECT_NO_BID);
                    passButton.setVisible(true);
                    pack();
                }

                if (includeBidding != StartRound.Bidding.NO) {
                    bidButton.setEnabled(true);
                    bidButton.setPossibleAction(currentActiveItem);
                    bidAmount.setEnabled(true);
                    int minBid = bidAction.getMinimumBid();
                    spinnerModel.setMinimum(minBid);
                    spinnerModel.setStepSize(bidAction.getBidIncrement());
                    spinnerModel.setValue(minBid);
                }
                
                for (int k = 0; k < cards.length; k++) {
                    if (cards[k] != null && cards[k].isEnabled()) {
                        cards[k].setState(k == clickedIndex ? RailCard.State.SELECTED : RailCard.State.ACTIONABLE);
                    }
                }
            }
            return;
        }

        if (source instanceof ActionButton) {
            java.util.List<PossibleAction> actions = ((ActionButton) source).getPossibleActions();
            if (actions == null || actions.isEmpty()) {
                log.warn("SRW: ActionButton clicked but actions list is empty.");
                return;
            }
            PossibleAction activeItem = actions.get(0);

            if (source == buyButton) {
                if (activeItem instanceof BuyStartItem && ((BuyStartItem) activeItem).hasSharePriceToSet()) {
                    if (requestStartPrice((BuyStartItem) activeItem)) return;
                } else {
                    process(activeItem);
                }
            } else if (source == bidButton) {
                ((BidStartItem) activeItem).setActualBid(((Integer) spinnerModel.getValue()));
                process(activeItem);
            } else if (source == passButton) {
                if (activeItem instanceof BidStartItem && ((BidStartItem) activeItem).isSelectForAuction()) {
                    ((BidStartItem) activeItem).setActualBid(-1);
                }
                process(activeItem);
            } else {
                process(activeItem);
            }
        }
    }

}