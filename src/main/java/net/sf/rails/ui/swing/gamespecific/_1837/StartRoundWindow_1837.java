package net.sf.rails.ui.swing.gamespecific._1837;

import java.awt.*;
import java.util.Arrays;
import javax.swing.*;
import net.sf.rails.common.LocalText;
import net.sf.rails.game.*;
import net.sf.rails.game.financial.*;
import net.sf.rails.ui.swing.StartRoundWindow;
import net.sf.rails.ui.swing.elements.*;
import rails.game.action.*;

public class StartRoundWindow_1837 extends StartRoundWindow {

    private static final long serialVersionUID = 1L;
    private JPanel[] playerInventoryPanels;

    public StartRoundWindow_1837() {
        super();
    }

    @Override
    protected void initCells() {
        int ni = round.getNumberOfStartItems();
        int np = players.getNumberOfPlayers();
        Font cellFont = new Font("SansSerif", Font.BOLD, currentFontSize);

        cards = new RailCard[ni];
        cardWrappers = new JPanel[ni];
        playerInventoryPanels = new JPanel[np];
        
        // Initialize parent arrays
        basePrice = new Field[ni];
        bidPerPlayer = new Field[ni][np];
        upperPlayerCaption = new Field[1][np];
        lowerPlayerCaption = new Field[np];
        playerBids = new Field[np];
        playerFree = new Field[np];
        
        itemNameXOffset = new int[numberOfColumns];
        if (showBasePrices) basePriceXOffset = new int[numberOfColumns];

        // --- STEP 1: SHOP GRID SETUP (LEFT) ---
        int forcedRows = 10;
        int lastX = -1;
        for (int col = 0; col < numberOfColumns; col++) {
            itemNameXOffset[col] = ++lastX;
            if (showBasePrices) basePriceXOffset[col] = ++lastX;
            
            addField(new Caption(LocalText.getText("ITEM")), itemNameXOffset[col], 0, 1, 1, WIDE_LEFT + WIDE_RIGHT + WIDE_BOTTOM);
            if (showBasePrices) {
                addField(new Caption(LocalText.getText("PRICE")), basePriceXOffset[col], 0, 1, 1, WIDE_BOTTOM);
            }
        }

        // --- STEP 2: PLAYER INVENTORY SETUP (RIGHT) ---
        int separatorX = ++lastX;
        addField(new JSeparator(SwingConstants.VERTICAL), separatorX, 0, 1, forcedRows + 4, WIDE_LEFT + WIDE_RIGHT);

        int playerStartX = ++lastX;
        playerCaptionXOffset = new int[]{playerStartX};
        
        for (int i = 0; i < np; i++) {
            upperPlayerCaption[0][i] = new Field(players.getPlayerByPosition(i).getPlayerNameModel());
            upperPlayerCaption[0][i].setFont(cellFont);
            addField(upperPlayerCaption[0][i], playerStartX + i, 0, 1, 1, WIDE_BOTTOM);

            playerInventoryPanels[i] = new JPanel();
            playerInventoryPanels[i].setLayout(new BoxLayout(playerInventoryPanels[i], BoxLayout.Y_AXIS));
            playerInventoryPanels[i].setBackground(Color.WHITE);
            playerInventoryPanels[i].setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            
            gbc.gridx = playerStartX + i;
            gbc.gridy = 1;
            gbc.gridheight = forcedRows + 1;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.weightx = 1.0; 
            gbc.weighty = 1.0; 
            statusPanel.add(playerInventoryPanels[i], gbc);
        }

        // --- STEP 3: ITEM PLACEMENT ---
        for (int i = 0; i < ni; i++) {
            final StartItem si = round.getStartItem(i);
            int col = multipleColumns ? si.getColumn() - 1 : 0;
            int row = multipleColumns ? si.getRow() - 1 : i;
            int yPos = row + 1;

            cards[i] = new RailCard(si, itemGroup);
            cards[i].addActionListener(this);
            cards[i].setScale(1.2); 

            cardWrappers[i] = new JPanel(new GridBagLayout());
            cardWrappers[i].setBackground(COLOR_SOLD); 
            cardWrappers[i].add(cards[i], new GridBagConstraints());
            
            addField(cardWrappers[i], itemNameXOffset[col], yPos, 1, 1, 1);

            if (showBasePrices) {
                basePrice[i] = new Field(si.getBasePriceModel());
                addField(basePrice[i], basePriceXOffset[col], yPos, 1, 1, 0);
            }
        }
        
        // --- STEP 4: FOOTER (Cash) ---
        int footerY = forcedRows + 2;
        addField(new Caption(LocalText.getText("CASH")), playerStartX - 1, footerY + 1, 1, 1, WIDE_RIGHT);

        for (int i = 0; i < np; i++) {
            playerBids[i] = new Field(round.getBlockedCashModel(players.getPlayerByPosition(i)));
            addField(playerBids[i], playerStartX + i, footerY, 1, 1, WIDE_TOP);
            
            playerFree[i] = new Field(round.getFreeCashModel(players.getPlayerByPosition(i)));
            addField(playerFree[i], playerStartX + i, footerY + 1, 1, 1, 0);
            
            lowerPlayerCaption[i] = new Field(players.getPlayerByPosition(i).getPlayerNameModel());
            addField(lowerPlayerCaption[i], playerStartX + i, footerY + 2, 1, 1, WIDE_TOP);
        }

        updateFonts(currentFontSize);
    }

    @Override
    public void updateStatus(boolean myTurn) {
        // 1. IMPORTANT: Let parent set actions/states first to enable buttons & hotkeys
        super.updateStatus(myTurn);

        // 2. Clear Inventory Panels
        for (JPanel panel : playerInventoryPanels) {
            panel.removeAll();
        }

        // 3. Identify Top Row per Column (Logic Enforcement)
        int[] topRowInColumn = new int[numberOfColumns];
        Arrays.fill(topRowInColumn, 999);
        for (int i = 0; i < round.getNumberOfStartItems(); i++) {
            StartItem si = round.getStartItem(i);
            if (!si.isSold()) {
                int col = multipleColumns ? si.getColumn() - 1 : 0;
                int row = multipleColumns ? si.getRow() - 1 : i;
                if (row < topRowInColumn[col]) topRowInColumn[col] = row;
            }
        }

        // 4. Apply Visual Overrides
        for (int i = 0; i < round.getNumberOfStartItems(); i++) {
            StartItem si = round.getStartItem(i);
            RailCard card = cards[i];
            int col = multipleColumns ? si.getColumn() - 1 : 0;
            int row = multipleColumns ? si.getRow() - 1 : i;

            if (si.isSold()) {
                // Remove from Shop
                cardWrappers[i].setVisible(false);
                if (showBasePrices && basePrice[i] != null) basePrice[i].setVisible(false);

                // Add to Inventory
                Player owner = si.getBidder();
                if (owner != null) {
                    // Set to PASSIVE so it looks "Normal/Green" but clear actions so it can't be bought again
                    card.setState(RailCard.State.PASSIVE); 
                    card.clearPossibleActions();
                    
                    card.setVisible(true);
                    playerInventoryPanels[owner.getIndex()].add(card);
                    playerInventoryPanels[owner.getIndex()].add(Box.createVerticalStrut(2));
                }
            } else {
                // Show in Shop
                cardWrappers[i].setVisible(true);
                if (showBasePrices && basePrice[i] != null) basePrice[i].setVisible(true);

                if (row == topRowInColumn[col]) {
                    // TOP ITEM:
                    // Do NOT force state here. Respect the state set by super.updateStatus().
                    // It might be ACTIONABLE (Green) or DISABLED (Gray - if no money).
                    // Just ensure the wrapper matches the active look.
                    cardWrappers[i].setBackground(COLOR_AVAILABLE);
                } else {
                    // BURIED ITEM:
                    // Force Disable visually and functionally
                    card.setState(RailCard.State.DISABLED);
                    card.clearPossibleActions();
                    cardWrappers[i].setBackground(COLOR_SOLD);
                }
            }
        }
        
        // 5. Refresh Layout
        for (JPanel panel : playerInventoryPanels) {
            panel.revalidate();
            panel.repaint();
        }
        statusPanel.revalidate();
        statusPanel.repaint();
    }

    @Override
    public void setSRPlayerTurn() {
        int playerIndex = players.getCurrentPlayer().getIndex();
        int np = players.getNumberOfPlayers();
        for (int i = 0; i < np; i++) {
            if (upperPlayerCaption != null && upperPlayerCaption[0][i] != null) {
                upperPlayerCaption[0][i].setHighlight(i == playerIndex);
            }
            if (lowerPlayerCaption != null && lowerPlayerCaption[i] != null) {
                lowerPlayerCaption[i].setHighlight(i == playerIndex);
            }
        }
    }

    private void addField(JComponent comp, int x, int y, int width, int height, int gaps) {
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = width;
        gbc.gridheight = height;
        gbc.weightx = 0.5;
        gbc.weighty = 0.5;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets((gaps & WIDE_TOP) > 0 ? 5 : 2, (gaps & WIDE_LEFT) > 0 ? 5 : 2, 
                                (gaps & WIDE_BOTTOM) > 0 ? 5 : 2, (gaps & WIDE_RIGHT) > 0 ? 5 : 2);
        statusPanel.add(comp, gbc);
    }
}