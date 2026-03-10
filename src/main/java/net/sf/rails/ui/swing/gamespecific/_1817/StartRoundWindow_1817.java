package net.sf.rails.ui.swing.gamespecific._1817;

import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import net.sf.rails.ui.swing.StartRoundWindow;
import net.sf.rails.ui.swing.elements.Field;
import net.sf.rails.game.specific._1817.StartRound_1817;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import javax.swing.Box;

public class StartRoundWindow_1817 extends StartRoundWindow {

    private static final long serialVersionUID = 1L;
    private Field seedMoneyField;
private JLabel seedLabel;
    private boolean seedAdded = false;

    public StartRoundWindow_1817() {
        super();
    }

    @Override
    protected void initCells() {
        super.initCells();
        
        StartRound_1817 sr1817 = (StartRound_1817) round;

        if (!seedAdded) {
            buttonPanel.add(Box.createHorizontalStrut(20)); // Spacing from the last button
            
            seedLabel = new JLabel("Seed Money:");
            seedLabel.setFont(new Font("SansSerif", Font.BOLD, currentFontSize));
            buttonPanel.add(seedLabel);
            
            seedMoneyField = new Field(sr1817.getSeedMoneyModel());
            seedMoneyField.setFont(new Font("SansSerif", Font.BOLD, currentFontSize));
            buttonPanel.add(seedMoneyField);
            
            seedAdded = true;
        } else {
            // Keep font size in sync if initCells is recalled (e.g. zooming)
            seedLabel.setFont(new Font("SansSerif", Font.BOLD, currentFontSize));
            seedMoneyField.setFont(new Font("SansSerif", Font.BOLD, currentFontSize));
        }

        
        
        buttonPanel.revalidate();
        buttonPanel.repaint();
    }
}