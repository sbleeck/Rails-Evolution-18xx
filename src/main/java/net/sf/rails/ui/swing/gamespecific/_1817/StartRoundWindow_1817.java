package net.sf.rails.ui.swing.gamespecific._1817;

import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import net.sf.rails.ui.swing.StartRoundWindow;
import net.sf.rails.ui.swing.elements.Field;
import net.sf.rails.game.specific._1817.StartRound_1817;
import javax.swing.JPanel;
import java.awt.FlowLayout;

public class StartRoundWindow_1817 extends StartRoundWindow {

    private static final long serialVersionUID = 1L;
    private Field seedMoneyField;

    public StartRoundWindow_1817() {
        super();
    }

    @Override
    protected void initCells() {
        super.initCells();
        
        StartRound_1817 sr1817 = (StartRound_1817) round;
        
JPanel seedPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        seedPanel.setBorder(BorderFactory.createTitledBorder("Bank Status"));

        JLabel seedLabel = new JLabel("Seed Money Left:");
        seedLabel.setFont(new Font("SansSerif", Font.PLAIN, currentFontSize));
        seedPanel.add(seedLabel);
        
        seedMoneyField = new Field(sr1817.getSeedMoneyModel());
        seedMoneyField.setFont(new Font("SansSerif", Font.BOLD, currentFontSize));
        seedPanel.add(seedMoneyField);

        buttonPanel.add(seedPanel);
        
        buttonPanel.revalidate();
        buttonPanel.repaint();
    }
}