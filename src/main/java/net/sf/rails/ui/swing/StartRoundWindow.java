package net.sf.rails.ui.swing;

import java.awt.*;
import java.awt.event.*;
import java.util.EnumSet;
import java.util.HashSet;
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
import net.sf.rails.ui.swing.hexmap.GUIHex;
import net.sf.rails.ui.swing.hexmap.HexHighlightMouseListener;
import net.sf.rails.ui.swing.hexmap.HexMap;
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
    protected static final int NARROW_GAP = 2;
    protected static final int WIDE_GAP = 5;
    // Bits for specifying where to apply wide gaps
    protected static final int WIDE_LEFT = 1;
    protected static final int WIDE_RIGHT = 2;
    protected static final int WIDE_TOP = 4;
    protected static final int WIDE_BOTTOM = 8;

    protected ActionButton undoButton;

    protected static final String[] itemStatusTextKeys = new String[] { "Status_Unavailable", "Status_Biddable",
            "Status_Buyable",
            "Status_Selectable", "Status_Auctioned",
            "Status_NeedingSharePrice", "Status_Sold" };

    /* Keys of dialogs owned by this class */
    public static final String COMPANY_START_PRICE_DIALOG = "CompanyStartPrice";

    private static final Logger log = LoggerFactory.getLogger(StartRoundWindow.class);

    protected JPanel statusPanel;
    protected JPanel buttonPanel;

    protected GridBagLayout gb;
    protected GridBagConstraints gbc;

    // Grid elements per function
    protected Caption[] itemName;
    protected int[] itemNameXOffset;
    protected int itemNameYOffset;
    protected Field[] basePrice;
    protected int[] basePriceXOffset;
    protected int basePriceYOffset;
    protected Field[] minBid;
    protected int[] minBidXOffset;
    protected int minBidYOffset;

    // Separator Fields
    protected JComponent[] verticalSeparators;
    protected int[] separatorXOffset;

    protected Field[][] bidPerPlayer;
    protected int[] bidPerPlayerXOffset;
    protected int bidPerPlayerYOffset;
    protected Field[] playerBids;
    protected int[] playerBidsXOffset;
    protected int playerBidsYOffset;
    protected Field[] playerFree;
    protected int[] playerFreeCashXOffset;
    protected int playerFreeCashYOffset;

    protected Field[] itemStatus;

    protected int[] playerCaptionXOffset;
    protected int upperPlayerCaptionYOffset, lowerPlayerCaptionYOffset;
    protected Field[][] upperPlayerCaption;
    protected Field[] lowerPlayerCaption;
    protected JComponent[][] fields;
    protected int currentFontSize = 14;

    protected ActionButton bidButton;
    protected ActionButton buyButton;

    protected ActionButton aiIRButton;
    protected JSpinner bidAmount;
    protected SpinnerNumberModel spinnerModel;
    protected ActionButton passButton;

    protected RailCard[] cards;
    protected PlayerManager players;

    protected int[] crossIndex;
    protected StartRound round;
    protected GameUIManager gameUIManager;
    protected StartPacket startPacket;
    protected boolean multipleColumns;
    protected int numberOfColumns;
    protected int numberOfRows;
    protected int columnWidth = 0;

    protected JDialog currentDialog;
    protected PossibleAction currentDialogAction;
    protected SortedSet<StockSpace> startSpaces;

    protected PossibleActions possibleActions;
    protected PossibleAction immediateAction;

    protected final ButtonGroup itemGroup = new ButtonGroup();
    protected ClickField dummyButton;

    protected StartRound.Bidding includeBidding;
    protected boolean includeBuying;
    protected boolean showBasePrices;

    protected ORUIManager orUIManager;
    protected int selectedItemIndex = -1;
    protected JPanel[] cardWrappers;
    protected static final Color COLOR_AVAILABLE = new Color(204, 255, 204);
    protected static final Color COLOR_SOLD = new Color(220, 220, 220);
    protected static final Color COLOR_HIGHLIGHT = new Color(160, 32, 240); // Prominent Purple

    public StartRoundWindow() {
    }

    protected void initCells() {
        int lastX = -1;
        int lastY = 0;

        int np = players.getNumberOfPlayers();
        int ni = round.getNumberOfStartItems();
        cards = new RailCard[ni];
        cardWrappers = new JPanel[ni];
        Font cellFont = new Font("SansSerif", Font.BOLD, currentFontSize);

        basePrice = new Field[ni];
        minBid = new Field[ni];

        verticalSeparators = new JComponent[numberOfColumns];
        separatorXOffset = new int[numberOfColumns];

        bidPerPlayer = new Field[ni][np];
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

            lastX += np;

            if (col == 0) {
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
                upperPlayerCaption[col][i].setFont(cellFont);
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
            // 2. Scale Card
            cards[i].setScale(1.2);

            // --- CENTRALIZED HIGHLIGHTING LOGIC ---
            configureMapHighlighting(cards[i], si);
            // -------------------------------------

            // 3. Create Wrapper Panel
            cardWrappers[i] = new JPanel();
            // Use GridLayout(1,1) to force the card to fill the entire wrapper area
            cardWrappers[i].setLayout(new GridLayout(1, 1));
            cardWrappers[i].setBackground(COLOR_AVAILABLE);
            cardWrappers[i].setBorder(BorderFactory.createEtchedBorder());

            // 4. Add Card to Wrapper
            cardWrappers[i].add(cards[i]);

            // 5. Add MouseListener to the wrapper itself
            // This ensures clicks on the border/background are forwarded to the window
            // logic
            final int cardIndex = i;
            cardWrappers[i].addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    // Forward the click as an ActionEvent from the card
                    actionPerformed(new ActionEvent(cards[cardIndex], ActionEvent.ACTION_PERFORMED, "WrapperClick"));
                }
            });

            // 6. Add Wrapper to Main Grid
            gbc.gridx = itemNameXOffset[col];
            gbc.gridy = itemNameYOffset + row;
            gbc.gridwidth = 1;
            gbc.gridheight = 1;
            gbc.weightx = 0.5;
            gbc.weighty = 0.5;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.insets = new Insets(1, 1, 1, 1);

            statusPanel.add(cardWrappers[i], gbc);

            if (fields != null && gbc.gridx < fields.length && gbc.gridy < fields[0].length) {
                fields[gbc.gridx][gbc.gridy] = cardWrappers[i];
            }

            if (showBasePrices) {
                basePrice[i] = new Field(si.getBasePriceModel());
                basePrice[i].setFont(cellFont);
                addField(basePrice[i], basePriceXOffset[col], basePriceYOffset + row,
                        1, 1, 0);
            }

            if (includeBidding == StartRound.Bidding.ON_ITEMS) {
                minBid[i] = new Field(round.getMinimumBidModel(i));
                minBid[i].setFont(cellFont);
                addField(minBid[i], minBidXOffset[col], minBidYOffset + row,
                        1, 1, WIDE_RIGHT);
            }

            for (int j = 0; j < np; j++) {
                bidPerPlayer[i][j] = new Field(round.getBidModel(i, players.getPlayerByPosition(j)));
                bidPerPlayer[i][j].setFont(cellFont);
                addField(bidPerPlayer[i][j], bidPerPlayerXOffset[col] + j, bidPerPlayerYOffset + row,
                        1, 1, 0);

            }

            itemStatus[i] = new Field(si.getStatusModel());
        }

        // Player money
        boolean firstBelowTable = true;
        if (includeBidding != StartRound.Bidding.NO) {
            addField(new Caption(LocalText.getText("BID")), basePriceXOffset[0], playerBidsYOffset,
                    1, 1, WIDE_TOP + WIDE_RIGHT);

            for (int i = 0; i < np; i++) {
                playerBids[i] = new Field(round.getBlockedCashModel(players.getPlayerByPosition(i)));
                playerBids[i].setFont(cellFont);
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
            playerFree[i].setFont(cellFont);
            addField(playerFree[i], playerFreeCashXOffset[0] + i, playerFreeCashYOffset, 1, 1,
                    firstBelowTable ? WIDE_TOP : 0);
        }

        for (int i = 0; i < np; i++) {
            lowerPlayerCaption[i] = new Field(players.getPlayerByPosition(i).getPlayerNameModel());
            lowerPlayerCaption[i].setFont(cellFont);
            addField(lowerPlayerCaption[i], playerFreeCashXOffset[0] + i, playerFreeCashYOffset + 1, 1, 1, WIDE_TOP);
        }

        dummyButton = new ClickField("", "", "", this, itemGroup);

        updateFonts(currentFontSize);
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

    public void updateFonts(int size) {
        if (size < 8)
            size = 8;
        if (size > 48)
            size = 48;
        currentFontSize = size;

        Font f = new Font("SansSerif", Font.BOLD, currentFontSize);

        // Helper logic to update all arrays if they exist
        if (basePrice != null)
            for (Field c : basePrice)
                if (c != null)
                    c.setFont(f);
        if (minBid != null)
            for (Field c : minBid)
                if (c != null)
                    c.setFont(f);
        if (playerBids != null)
            for (Field c : playerBids)
                if (c != null)
                    c.setFont(f);
        if (playerFree != null)
            for (Field c : playerFree)
                if (c != null)
                    c.setFont(f);
        if (lowerPlayerCaption != null)
            for (Field c : lowerPlayerCaption)
                if (c != null)
                    c.setFont(f);

        if (upperPlayerCaption != null) {
            for (Field[] row : upperPlayerCaption) {
                if (row != null)
                    for (Field c : row)
                        if (c != null)
                            c.setFont(f);
            }
        }
        if (bidPerPlayer != null) {
            for (Field[] row : bidPerPlayer) {
                if (row != null)
                    for (Field c : row)
                        if (c != null)
                            c.setFont(f);
            }
        }

        // Re-pack window to accommodate new size
        if (gameUIManager != null)
            gameUIManager.packAndApplySizing(this);
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

        initCells();

        getContentPane().add(statusPanel, BorderLayout.NORTH);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);
        setLocation(300, 150);
        setSize(275, 325);
        gameUIManager.setMeVisible(this, true);
        requestFocus();

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

    // ... (lines of unchanged context code) ...
    private void setupHotkeys() {
        // --- START FIX ---
        // Bind Command/Ctrl + and - to font size adjustment
        InputMap inputMap = statusPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = statusPanel.getActionMap();

        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        // Increase Font (Cmd = and Cmd +)
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, mask), "increaseFont");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, mask), "increaseFont");
        actionMap.put("increaseFont", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent e) {
                updateFonts(currentFontSize + 2);
            }
        });

        // Decrease Font (Cmd -)
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, mask), "decreaseFont");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, mask), "decreaseFont");
        actionMap.put("decreaseFont", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent e) {
                updateFonts(currentFontSize - 2);
            }
        });

        // Bind Enter Key to Pass Button
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "triggerPass");
        actionMap.put("triggerPass", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent e) {
                if (passButton != null && passButton.isEnabled()) {
                    passButton.doClick();
                }
            }
        });
        // --- END FIX ---
    }

    // We are modifying StartRoundWindow.java

    /**
     * Configures the RailCard with Tooltips and Map Highlighting based on the
     * associated StartItem.
     * Centralized logic ensures that Privates, Minors, and Majors highlight
     * correctly in all games.
     */
    protected void configureMapHighlighting(RailCard card, StartItem si) {
        if (card == null || si == null)
            return;

        // 1. Collect all certificates for highlighting (Primary and Secondary)
        java.util.List<Certificate> certs = new java.util.ArrayList<>();
        if (si.getPrimary() != null)
            certs.add(si.getPrimary());
        if (si.getSecondary() != null)
            certs.add(si.getSecondary());

        log.info("SRW: Configuring highlighting for StartItem {}. Found {} certs.", si.getId(), certs.size());

        // 2. Set Tooltip (based on primary company)
        if (!certs.isEmpty()) {
            Certificate pCert = certs.get(0);
            Company pComp = null;
            if (pCert instanceof PrivateCompany) {
                pComp = (PrivateCompany) pCert;
            } else if (pCert instanceof PublicCertificate) {
                pComp = ((PublicCertificate) pCert).getCompany();
                if (pComp == null && pCert.getParent() instanceof PublicCompany) {
                    pComp = (PublicCompany) pCert.getParent();
                }
            }
            if (pComp != null) {
                card.setCompanyDetailsTooltip(pComp);
                log.info("SRW: Assigned tooltip for Company {}", pComp.getId());
            }
        }

        // 3. Add Map Highlight Listeners
        if (gameUIManager != null && gameUIManager.getORUIManager() != null) {

            // A. Attach the StartItem listener (highlights blocked hexes for contained
            // Privates)
            HexHighlightMouseListener.addMouseListener(card, gameUIManager.getORUIManager(), si);
            log.info("SRW: Attached generic StartItem listener for {}", si.getId());

            // B. Attach explicit listeners for all associated companies
            for (Certificate cert : certs) {
                PublicCompany pubComp = null;
                if (cert instanceof PublicCertificate) {
                    pubComp = ((PublicCertificate) cert).getCompany();
                    if (pubComp == null && cert.getParent() instanceof PublicCompany) {
                        pubComp = (PublicCompany) cert.getParent();
                    }
                }

                if (pubComp != null) {
                    // Log the Home Hexes to verify they exist in the model
                    java.util.List<MapHex> homes = pubComp.getHomeHexes();
                    log.info("SRW: Attaching PublicCompany listener for {}. HomeHexes count: {}",
                            pubComp.getId(), (homes != null ? homes.size() : "NULL"));

                    HexHighlightMouseListener.addMouseListener(card, gameUIManager.getORUIManager(), pubComp, true);
                } else if (cert instanceof PrivateCompany) {
                    PrivateCompany priv = (PrivateCompany) cert;
                    log.info("SRW: Attaching explicit PrivateCompany listener for {}. BlockedHexes count: {}",
                            priv.getId(), (priv.getBlockedHexes() != null ? priv.getBlockedHexes().size() : "NULL"));

                    HexHighlightMouseListener.addMouseListener(card, gameUIManager.getORUIManager(), priv, true);
                }
            }
        } else {
            log.warn("SRW: Skipping highlight attachment - ORUIManager or Map not available.");
        }
    }

    protected void clearMapHighlights() {
        if (gameUIManager != null && gameUIManager.getORUIManager() != null) {
            net.sf.rails.ui.swing.hexmap.HexMap map = gameUIManager.getORUIManager().getMap();
            if (map != null) {
                // --- START FIX ---
                // setOwnerHighlight requires a List, and we must iterate over the map values
                map.setOwnerHighlight(new java.util.ArrayList<net.sf.rails.ui.swing.hexmap.GUIHex>(), null);

                for (net.sf.rails.ui.swing.hexmap.GUIHex guiHex : map.getGuiHexes().values()) {
                    guiHex.setActiveOwnerHighlight(false, null);
                }
                // --- END FIX ---
            }
        }
    }

    protected void updateMapHighlights() {
        if (gameUIManager == null || gameUIManager.getORUIManager() == null)
            return;
        net.sf.rails.ui.swing.hexmap.HexMap map = gameUIManager.getORUIManager().getMap();
        if (map == null)
            return;

        // --- START FIX ---
        java.util.List<net.sf.rails.ui.swing.hexmap.GUIHex> hexesToHighlight = new java.util.ArrayList<>();

        for (int i = 0; i < cards.length; i++) {
            if (cards[i] != null && (cards[i].getState() == RailCard.State.ACTIONABLE
                    || cards[i].getState() == RailCard.State.SELECTED)) {
                StartItem si = round.getStartItem(i);
                Certificate cert = si.getPrimary();
                PublicCompany pubComp = null;

                if (cert instanceof PublicCertificate) {
                    pubComp = ((PublicCertificate) cert).getCompany();
                }

                if (pubComp != null) {
                    for (MapHex hex : pubComp.getHomeHexes()) {
                        net.sf.rails.ui.swing.hexmap.GUIHex guiHex = map.getHex(hex);
                        if (guiHex != null) {
                            guiHex.setActiveOwnerHighlight(true, pubComp.getId());
                            hexesToHighlight.add(guiHex);
                        }
                    }
                }
            }
        }
        map.setOwnerHighlight(hexesToHighlight, null);
        // --- END FIX ---
        map.repaintAll(new Rectangle(map.getSize()));
    }

    @Override
    public void updateStatus(boolean myTurn) {
        // --- START FIX ---
        if (gameUIManager != null && gameUIManager.getGameManager() != null) {
            possibleActions = gameUIManager.getGameManager().getPossibleActions();
        }

        // 1. Reset Map Highlights and Card States
        clearMapHighlights();

        for (int i = 0; i < round.getNumberOfStartItems(); i++) {
            StartItem si = round.getStartItem(i);
            int status = si.getStatus();
            cards[i].clearPossibleActions();

            if (status == StartItem.SOLD) {
                cards[i].setState(RailCard.State.DISABLED);
                if (cardWrappers[i] != null)
                    cardWrappers[i].setBackground(COLOR_SOLD);
            } else {
                cards[i].setState(RailCard.State.PASSIVE);
                if (cardWrappers[i] != null)
                    cardWrappers[i].setBackground(COLOR_AVAILABLE);
                configureMapHighlighting(cards[i], si);
            }
        }

        // 2. Setup Buttons (Default Disabled)
        dummyButton.setSelected(true);
        if (includeBuying && buyButton != null)
            buyButton.setEnabled(false);
        if (includeBidding != StartRound.Bidding.NO) {
            if (bidButton != null)
                bidButton.setEnabled(false);
            if (bidAmount != null)
                bidAmount.setEnabled(false);
        }
        if (passButton != null)
            passButton.setEnabled(false);
        if (undoButton != null)
            undoButton.setEnabled(true); // Always check possibleActions for undo

        RoundFacade currentRound = gameUIManager.getCurrentRound();
        if (!(currentRound instanceof StartRound) || !myTurn || possibleActions == null) {
            return;
        }

        setSRPlayerTurn();

        // 3. Handle Undo Action
        List<GameAction> gameActions = possibleActions.getType(GameAction.class);
        undoButton.setEnabled(false);
        for (GameAction ga : gameActions) {
            if (ga.getMode() == GameAction.Mode.UNDO && undoButton != null) {
                undoButton.setEnabled(true);
                undoButton.setPossibleAction(ga);
                break;
            }
        }

        // 4. Distribute Actions and Apply Prominent Highlighting
        List<StartItemAction> actions = possibleActions.getType(StartItemAction.class);
        if (actions != null) {
            for (StartItemAction action : actions) {
                int j = action.getItemIndex();
                int i = crossIndex[j];
                cards[i].setPossibleAction(action);

                // PROMINENT HIGHLIGHT: Purple background for actionable items
                if (cardWrappers[i] != null) {
                    cardWrappers[i].setBackground(COLOR_HIGHLIGHT);
                }

                if (action instanceof BuyStartItem) {
                    BuyStartItem bsi = (BuyStartItem) action;
                    if (bsi.isSelected() || i == selectedItemIndex) {
                        cards[i].setState(RailCard.State.SELECTED);
                        selectedItemIndex = i;
                        if (cardWrappers[i] != null) {
                            cardWrappers[i].setBackground(Color.YELLOW);
                        }
                        if (buyButton != null && includeBuying) {
                            buyButton.setEnabled(true);
                            buyButton.setPossibleAction(action);
                        }
                    } else {
                        cards[i].setState(RailCard.State.ACTIONABLE);
                    }
                } else if (action instanceof BidStartItem) {
                    BidStartItem bidAction = (BidStartItem) action;
                    if (bidAction.isSelected()) {
                        cards[i].setState(RailCard.State.SELECTED);
                        selectedItemIndex = i;
                        if (bidButton != null) {
                            bidButton.setEnabled(true);
                            bidButton.setPossibleAction(action);
                        }
                        if (bidAmount != null) {
                            bidAmount.setEnabled(true);
                            spinnerModel.setMinimum(bidAction.getMinimumBid());
                            spinnerModel.setValue(bidAction.getMinimumBid());
                        }
                    } else {
                        cards[i].setState(RailCard.State.ACTIONABLE);
                    }
                }
            }
        }

        // 5. Handle Pass Button
        List<NullAction> passes = possibleActions.getType(NullAction.class);
        if (passes != null && !passes.isEmpty() && passButton != null) {
            passButton.setEnabled(true);
            passButton.setPossibleAction(passes.get(0));
        }

        // 6. Final UI and Map Refresh
        updateMapHighlights();
        revalidate();
        repaint();
        // --- END FIX ---
    }


// ... (lines of unchanged context code) ...
    @Override
    public void actionPerformed(ActionEvent actor) {
        // --- START FIX ---
        Object source = actor.getSource();
        int clickedIndex = -1;
        
        // Use hierarchy search to find which card (if any) was clicked
        for (int k = 0; k < cards.length; k++) {
            if (cards[k] == null) continue;
            if (source == cards[k] || (source instanceof Component && SwingUtilities.isDescendingFrom((Component)source, cards[k]))) {
                clickedIndex = k;
                break;
            }
        }

        if (clickedIndex != -1) {
            RailCard card = cards[clickedIndex];
            if (card.getState() == RailCard.State.DISABLED) return;

            List<PossibleAction> acts = card.getPossibleActions();
            if (acts == null || acts.isEmpty()) return;

            StartItemAction action = (StartItemAction) acts.get(0);
            SoundManager.notifyOfClickFieldSelection(action);

            if (action instanceof BuyStartItem) {
                if (clickedIndex == selectedItemIndex) {
                    // Second click confirms purchase
                    BuyStartItem bsi = (BuyStartItem) action;
                    if (bsi.hasSharePriceToSet() && requestStartPrice(bsi))
                        return;
                    process(bsi);
                    selectedItemIndex = -1;
                } else {
                    // First click highlights and enables buttons
                    selectedItemIndex = clickedIndex;
                    updateStatus(true);
                }
            } else {
                // For bidding, select immediately
                selectedItemIndex = clickedIndex;
                updateStatus(true);
            }
            return;
        }

        // Handle ActionButtons (Buy, Bid, Pass, Undo)
        if (source instanceof ActionButton) {
            List<PossibleAction> actions = ((ActionButton) source).getPossibleActions();
            if (actions != null && !actions.isEmpty()) {
                process(actions.get(0));
            }
        }
        // --- END FIX ---
    }
// ... (rest of the method) ...
}