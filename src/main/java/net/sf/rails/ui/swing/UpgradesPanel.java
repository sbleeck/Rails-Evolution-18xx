package net.sf.rails.ui.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

import net.sf.rails.ui.swing.elements.GUIHexUpgrades;
import net.sf.rails.ui.swing.elements.UpgradeLabel;
import net.sf.rails.ui.swing.elements.RailsIcon;
import net.sf.rails.ui.swing.elements.RailsIconButton;
import net.sf.rails.ui.swing.hexmap.*;

public class UpgradesPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private static final int UPGRADE_TILE_ZOOM_STEP = 10;

    private final ORUIManager orUIManager;
    private final JPanel upgradePanel;
    private final JScrollPane scrollPane;
    private final RailsIconButton confirmButton;
    private final RailsIconButton skipButton;
    private boolean omitButtons;
    private GUIHexUpgrades hexUpgrades;

    public UpgradesPanel(ORUIManager orUIManager, boolean omitButtons) {
        this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        this.orUIManager = orUIManager;
        this.omitButtons = omitButtons;

        Color bgColor = UIManager.getColor("Panel.background");

        // Base width 135: fits text comfortably without being huge.
        // Alignment fix below ensures it sticks to the left regardless of parent size.
        int width = (int) Math.round(100 * (2 + GUIGlobals.getFontsScale()) / 3);
        int height = 200;
        
        this.setPreferredSize(new Dimension(width, height + 50));
        this.setMaximumSize(new Dimension(width, Short.MAX_VALUE));
        setVisible(true);

        upgradePanel = new JPanel();
        upgradePanel.setOpaque(true);
        upgradePanel.setLayout(new BoxLayout(upgradePanel, BoxLayout.PAGE_AXIS));
        upgradePanel.setBackground(bgColor);

        scrollPane = new JScrollPane(upgradePanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setPreferredSize(new Dimension(width, height));
        scrollPane.setMinimumSize(new Dimension(width, height));
        scrollPane.getViewport().setBackground(bgColor);
        
        // --- FIX 1: ALIGN SCROLLPANE LEFT ---
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        Action confirmAction = new AbstractAction() {
            public void actionPerformed(ActionEvent arg0) {
                UpgradesPanel.this.orUIManager.confirmUpgrade();
            }
        };
        confirmAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_D);

        Action skipAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                UpgradesPanel.this.orUIManager.skipUpgrade();
            }
        };
        skipAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_C);
        
        confirmButton = new RailsIconButton(RailsIcon.CONFIRM, confirmAction);
        confirmButton.setEnabled(false);

        skipButton = new RailsIconButton(RailsIcon.SKIP, skipAction);
        skipButton.setEnabled(false);
         
        if (omitButtons) {
            confirmButton.setVisible(false);
            skipButton.setVisible(false);
        } else {
            Dimension buttonDimension = new Dimension(Short.MAX_VALUE, 25);
            confirmButton.setMaximumSize(buttonDimension);
            skipButton.setMaximumSize(buttonDimension);
            
            // --- FIX 2: ALIGN BUTTONS LEFT (Was CENTER) ---
            confirmButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            skipButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            add(confirmButton);
            add(skipButton);
        }
        add(scrollPane);
        
        // --- SHORTCUT LEGEND ---
        add(Box.createVerticalGlue()); 
        
        setButtons();
        revalidate();
    }
    

    private void addLegendItem(JPanel panel, String key, String desc) {
        JLabel lbl = new JLabel("<html><font color='#222222' size='3'><b>[" + key + "]</b></font> " + desc + "</html>");
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 11)); 
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT); // Internal items also Left
        panel.add(lbl);
        panel.add(Box.createVerticalStrut(3));
    }

    // ... (rest of the class is unchanged) ...
    
    public void setHexUpgrades(GUIHexUpgrades hexUpgrades) {
        this.hexUpgrades = hexUpgrades;
    }
    
    private int getZoomStep() {
        if (orUIManager.getORWindow().isDockingFrameworkEnabled()) {
            return orUIManager.getMap().getZoomStep();
        } else {
            return UPGRADE_TILE_ZOOM_STEP;
        }
    }

    public RailsIconButton[] getButtons() {
        return new RailsIconButton[] { confirmButton, skipButton };
    }
    
    private void setButtons() {
        if (omitButtons) {
            boolean isVisible = confirmButton.isEnabled() || skipButton.isEnabled();
            confirmButton.setVisible(isVisible);
            skipButton.setVisible(isVisible);
        }
    }
    
    private void resetUpgrades(boolean skip) {
        hexUpgrades.setActiveHex(null, 0);
        upgradePanel.removeAll();
        scrollPane.getVerticalScrollBar().setValue(0);
        scrollPane.repaint();
        confirmButton.setEnabled(false);
        skipButton.setEnabled(skip);
        setButtons();
    }

    public void setInactive() {
        resetUpgrades(false);
    }
    
    public void setActive() {
        resetUpgrades(true);
    }
    
    public void setSelect(GUIHex hex) {
        hexUpgrades.setActiveHex(hex, getZoomStep());
        showLabels();
        refreshUpgrades();
        HexUpgrade activeUpgrade = hexUpgrades.getActiveUpgrade();
        if (activeUpgrade != null) {
            confirmButton.setEnabled(true);
            orUIManager.orPanel.enableConfirm(true);
        } else {
            confirmButton.setEnabled(false);
            orUIManager.orPanel.enableConfirm(false);
        }
        setButtons();
    }
   
    public void nextSelection() {
        hexUpgrades.nextSelection();
        refreshUpgrades();
    }

    public void nextUpgrade() {
        hexUpgrades.nextUpgrade();
        refreshUpgrades();
    }
    
    private void setActiveUpgrade(HexUpgrade upgrade) {
        hexUpgrades.setUpgrade(upgrade);
        refreshUpgrades();
    }
    
    private void refreshUpgrades() {
        upgradePanel.revalidate();
        upgradePanel.repaint();
        UpgradeLabel active = hexUpgrades.getActiveLabel();
        if (active != null) {
            upgradePanel.scrollRectToVisible(active.getBounds());
        }
    }
    
    private void showLabels() {
        upgradePanel.removeAll();
        for (UpgradeLabel label:hexUpgrades.getUpgradeLabels()) {
            final HexUpgrade upgrade = label.getUpgrade();
            if (upgrade.isValid()) {
                label.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent e) {
                        setActiveUpgrade(upgrade);
                    }
                }); 
            } else { 
                if (upgrade instanceof TileHexUpgrade && ((TileHexUpgrade)upgrade).noTileAvailable()) {
                    HexHighlightMouseListener.addMouseListener(label, orUIManager, 
                            ((TileHexUpgrade)upgrade).getUpgrade().getTargetTile(), true);
                }
            }
            upgradePanel.add(label);
        }
    }
}