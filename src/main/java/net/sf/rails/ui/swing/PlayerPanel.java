package net.sf.rails.ui.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JLayeredPane;
import javax.swing.SwingConstants;

import net.sf.rails.game.Player;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.ui.swing.elements.RailCard;

/**
 * Visual representation of a Player's portfolio.
 */
public class PlayerPanel extends JPanel {

    private final Player player;
    private final GameUIManager gameUIManager;
    

    // UI Components
    private JLabel nameLabel;
private JLabel cashLabel;
    private JLabel certsLabel;
    private JLabel timeLabel;
    private JLabel worthLabel;
    private JPanel cardArea;
    private JLabel pdLabel;

    // Configuration
    public boolean showWorth = false; // Hidden by default as requested

    public PlayerPanel(Player player, GameUIManager gameUIManager) {
        this.player = player;
        this.gameUIManager = gameUIManager;

        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        setBackground(new Color(245, 245, 250));

        initUI();
    }

    private void initUI() {
        // --- 1. Dedicated Header Panel ---
        JPanel headerPanel = new JPanel(new BorderLayout(5, 5));
        // Solid background to visually group the player's assets
        headerPanel.setBackground(new Color(225, 230, 240));
        headerPanel.setOpaque(true);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Top Row: Name (Left) and Priority Deal (Right - Anchored)
        JPanel nameRow = new JPanel(new BorderLayout());
        nameRow.setOpaque(false);
        
        nameLabel = new JLabel();
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        nameLabel.setForeground(new Color(20, 20, 20));
        
        pdLabel = new JLabel("[🚂 PD]");
        pdLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        pdLabel.setForeground(new Color(200, 50, 50));
        pdLabel.setVisible(false);

        nameRow.add(nameLabel, BorderLayout.WEST);
        nameRow.add(pdLabel, BorderLayout.EAST);

        // Stats: Fixed Grid prevents truncation and aligns numbers perfectly
        JPanel statsPanel = new JPanel(new java.awt.GridLayout(2, 2, 4, 2));
        statsPanel.setOpaque(false);
        
        Font statFont = new Font("Monospaced", Font.BOLD, 12);
        Color statColor = new Color(50, 50, 50);
        
        cashLabel = new JLabel();
        cashLabel.setFont(statFont); cashLabel.setForeground(statColor);
        certsLabel = new JLabel();
        certsLabel.setFont(statFont); certsLabel.setForeground(statColor);
        timeLabel = new JLabel();
        timeLabel.setFont(statFont); timeLabel.setForeground(statColor);
        worthLabel = new JLabel();
        worthLabel.setFont(statFont); worthLabel.setForeground(statColor);
        
        statsPanel.add(cashLabel);
        statsPanel.add(certsLabel);
        statsPanel.add(timeLabel);
        statsPanel.add(worthLabel);

        headerPanel.add(nameRow, BorderLayout.NORTH);
        headerPanel.add(statsPanel, BorderLayout.SOUTH);

        add(headerPanel, BorderLayout.NORTH);

        // --- 2. Card Area ---
        // FlowLayout naturally wraps components without absolute positioning chaos
        cardArea = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 10));
        cardArea.setOpaque(false);
        add(cardArea, BorderLayout.CENTER);
    }

    public void refresh() {
        // Update Name
        nameLabel.setText(player.getName());

        // Update Stats
        int cash = player.getCashValue();
        int certs = (int) player.getPortfolioModel().getCertificateCount();
        int limit = gameUIManager.getGameManager().getPlayerCertificateLimit(player);
        
        // Time logic: pulling from the time bank if available, else placeholder
        int time = player.getTimeBankModel().value();
        String timeStr = String.format("%02d:%02d", Math.abs(time) / 60, Math.abs(time) % 60);

       cashLabel.setText("Cash: " + gameUIManager.format(cash));
        certsLabel.setText("Certs: " + certs + "/" + limit);
        timeLabel.setText("Time: " + (time < 0 ? "-" : "") + timeStr);
        worthLabel.setText(showWorth ? "Worth: " + gameUIManager.format(player.getWorth()) : "");

        layoutCards();
    }

    public void setPriorityDeal(boolean hasPriorityDeal) {
        pdLabel.setVisible(hasPriorityDeal);
    }

    private void layoutCards() {
        cardArea.removeAll();

        // 1. Render Private Companies First
        java.util.Collection<net.sf.rails.game.PrivateCompany> privates = player.getPortfolioModel().getPrivateCompanies();
        for (net.sf.rails.game.PrivateCompany pc : privates) {
            JPanel holdingPanel = new JPanel(new BorderLayout(0, 2));
            holdingPanel.setOpaque(false);
            
            RailCard card = new RailCard(pc, new ButtonGroup());
            card.setCompany(pc);
            card.setCustomLabel(pc.getId());
            card.setPrivateCompanyTooltip(pc);
            
            holdingPanel.add(card, BorderLayout.CENTER);
            cardArea.add(holdingPanel);
        }

        List<PublicCompany> allCompanies = gameUIManager.getAllPublicCompanies();

        for (PublicCompany company : allCompanies) {
            if (company.isClosed()) continue;

            int sharePercentage = player.getPortfolioModel().getShare(company);
            if (sharePercentage == 0) continue;

            // Collect certificates
            java.util.List<net.sf.rails.game.financial.Certificate> certs = new java.util.ArrayList<>();
            for (net.sf.rails.game.financial.PublicCertificate pubCert : player.getPortfolioModel().getCertificates()) {
                if (company.equals(pubCert.getCompany())) {
                    certs.add(pubCert);
                }
            }

            if (certs.isEmpty()) continue;

            boolean isMinor = !company.hasStockPrice();

            // --- HOLDING CONTAINER ---
            JPanel holdingPanel = new JPanel(new BorderLayout(0, 2));
            holdingPanel.setOpaque(false);

            // --- THE PERCENTAGE BADGE (Majors Only) ---
            if (!isMinor) {
                JLabel pctLabel = new JLabel(" " + company.getId() + ": " + sharePercentage + "% ", SwingConstants.CENTER);
                pctLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
                pctLabel.setOpaque(true);
                
                Color bg = company.getBgColour();
                Color fg = company.getFgColour();
                pctLabel.setBackground(bg != null ? bg : Color.LIGHT_GRAY);
                pctLabel.setForeground(fg != null ? fg : Color.BLACK);
                pctLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
                
                holdingPanel.add(pctLabel, BorderLayout.NORTH);
            }

            // --- THE CARD CASCADE ---
            JPanel stackPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, -15, 0)); 
            stackPanel.setOpaque(false);
            
            for (net.sf.rails.game.financial.Certificate cert : certs) {
                ButtonGroup dummyGroup = new ButtonGroup();
                RailCard card = new RailCard(cert, dummyGroup);
                if (isMinor) {
                    card.setCustomLabel(company.getId()); 
                }
                stackPanel.add(card);
            }

            holdingPanel.add(stackPanel, BorderLayout.CENTER);
            cardArea.add(holdingPanel);
        }

        cardArea.revalidate();
        cardArea.repaint();
    }
    public Player getPlayer() {
        return player;
    }
}