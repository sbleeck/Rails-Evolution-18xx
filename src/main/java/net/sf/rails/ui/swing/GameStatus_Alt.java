package net.sf.rails.ui.swing;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import net.sf.rails.game.*;
import net.sf.rails.game.financial.*;

/**
 * GameStatus_Alt: A Modular 3-Column replacement for the monolithic GameStatus
 * grid.
 * Organized by Entity (Player/Company) rather than by Spreadsheet Coordinate.
 */
public class GameStatus_Alt extends GameStatus {

    // Layout Components
    private JPanel playerColumn; // Col 1: The "Whos"
    private JPanel companyColumn; // Col 2: The "Whats" (Scrollable)
    private JPanel marketColumn; // Col 3: The "Global/Active" context

    protected GameUIManager gameUIManager;
    protected StatusWindow parent;

    // UI Constants
    private static final Color BG_CHARTER = new Color(245, 245, 250);
    private static final Color BG_HIGHLIGHT = new Color(255, 255, 200); // Soft Yellow
    private static final Border CARD_BORDER = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            BorderFactory.createEmptyBorder(5, 5, 5, 5));

    public void init(StatusWindow parent, GameUIManager gameUIManager) {
        this.parent = parent;
        this.gameUIManager = gameUIManager;

        this.setLayout(new BorderLayout(10, 0));

        // Column 1: Players
        playerColumn = new JPanel();
        playerColumn.setLayout(new BoxLayout(playerColumn, BoxLayout.Y_AXIS));
        playerColumn.setPreferredSize(new Dimension(200, 0));
        this.add(playerColumn, BorderLayout.WEST);

        // Column 2: Companies (Scrollable)
        companyColumn = new JPanel();
        companyColumn.setLayout(new BoxLayout(companyColumn, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(companyColumn);
        scroll.setBorder(null);
        this.add(scroll, BorderLayout.CENTER);

        // Column 3: Market/Action Context
        marketColumn = new JPanel();
        marketColumn.setLayout(new BoxLayout(marketColumn, BoxLayout.Y_AXIS));
        marketColumn.setPreferredSize(new Dimension(220, 0));
        this.add(marketColumn, BorderLayout.EAST);

        recreate();
    }

    public void recreate() {
        playerColumn.removeAll();
        companyColumn.removeAll();
        marketColumn.removeAll();

        renderPlayers();
        renderCompanies();
        renderMarket();

        this.revalidate();
        this.repaint();
    }

    @Override
    public boolean initCashCorrectionActions() { return false; }

    @Override
    public boolean initTrainCorrectionActions() { return false; }

    @Override
    public int[] getLastPlayerTimes() {
        if (gameUIManager == null || gameUIManager.getPlayerManager() == null) return new int[0];
        return new int[gameUIManager.getPlayerManager().getNumberOfPlayers()];
    }

    @Override
    public void setLastPlayerTimes(int[] times) { }


private void renderPlayers() {
        PlayerManager pm = gameUIManager.getPlayerManager();
        // Priority deal moves left of the last purchaser
        int pdIndex = gameUIManager.getPriorityPlayer().getIndex();

        for (int i = 0; i < pm.getNumberOfPlayers(); i++) {
            Player p = pm.getPlayerByPosition(i);

// --- DELETE ---
//            // Priority Deal Indicator
//            if (i == pdIndex) {
//                JLabel pdLabel = new JLabel(" [🚂 Priority Deal] ", JLabel.CENTER);
//                pdLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
//                playerColumn.add(pdLabel);
//            }
//
//            PlayerPanel pPanel = new PlayerPanel(p, gameUIManager);
//            pPanel.refresh();
// --- START FIX ---
            PlayerPanel pPanel = new PlayerPanel(p, gameUIManager);
            pPanel.setPriorityDeal(i == pdIndex);
            pPanel.refresh();
// --- END FIX ---

            playerColumn.add(pPanel);
            playerColumn.add(Box.createVerticalStrut(10));
        }
    }
    private void renderCompanies() {
        List<PublicCompany> comps = gameUIManager.getAllPublicCompanies();
        // Sort by operating order or share price [cite: 938, 940]

        for (PublicCompany c : comps) {
            if (c.isClosed())
                continue;

            JPanel cCard = new JPanel(new BorderLayout());
            cCard.setBackground(BG_CHARTER);
            cCard.setBorder(CARD_BORDER);

            // Header: ID and Price
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            JLabel idLabel = new JLabel(c.getId() + " - " + c.getId());
            idLabel.setForeground(c.getFgColour());
            header.setBackground(c.getBgColour());
            header.setOpaque(true);

            int price = (c.getCurrentSpace() != null) ? c.getCurrentSpace().getPrice()
                    : (c.getStartSpace() != null ? c.getStartSpace().getPrice() : 0);
            header.add(idLabel, BorderLayout.WEST);
            header.add(new JLabel(gameUIManager.format(price)), BorderLayout.EAST);

            // Ownership Bar: Consolidated view to assess "Dump Risk" [cite: 917, 922]
            JPanel ownerBar = createOwnershipBar(c);

            // Financials: Treasury and Trains [cite: 834, 1054]
            JPanel stats = new JPanel(new FlowLayout(FlowLayout.LEFT));
            stats.setOpaque(false);
            stats.add(new JLabel("Treasury: " + gameUIManager.format(c.getCash())));
            stats.add(new JLabel("Trains: " + c.getPortfolioModel().getTrainList().toString()));

            cCard.add(header, BorderLayout.NORTH);
            cCard.add(ownerBar, BorderLayout.CENTER);
            cCard.add(stats, BorderLayout.SOUTH);

            companyColumn.add(cCard);
            companyColumn.add(Box.createVerticalStrut(5));
        }
    }

    private JPanel createOwnershipBar(PublicCompany c) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.setOpaque(false);

        // Market availability [cite: 1137]
        int ipoPct = gameUIManager.getRoot().getBank().getIpo().getPortfolioModel().getShare(c);
        int poolPct = gameUIManager.getRoot().getBank().getPool().getPortfolioModel().getShare(c);

        if (ipoPct > 0)
            bar.add(new JLabel("IPO: " + ipoPct + "% |"));
        if (poolPct > 0)
            bar.add(new JLabel("Pool: " + poolPct + "% |"));

        // Shareholder distribution [cite: 912, 917]
        for (Player p : gameUIManager.getPlayerManager().getPlayers()) {
            int pct = p.getPortfolioModel().getShare(c);
            if (pct > 0) {
                boolean isPrez = p.equals(c.getPresident());
                String label = p.getName() + " (" + pct + "%" + (isPrez ? "P" : "") + ")";
                JLabel l = new JLabel(label);
                if (isPrez)
                    l.setFont(l.getFont().deriveFont(Font.BOLD));
                bar.add(l);
            }
        }
        return bar;
    }

    private void renderMarket() {
        // Bank and Upcoming Trains [cite: 821, 1061]
        JPanel bankPanel = new JPanel(new GridLayout(0, 1));
        bankPanel.setBorder(BorderFactory.createTitledBorder("The Bank"));
        bankPanel.add(new JLabel("Cash: " + gameUIManager.format(gameUIManager.getRoot().getBank().getCash())));

        marketColumn.add(bankPanel);
        // Additional Global context like rusted trains or phase info would go here.
    }

    /**
     * Highlights the active entity and updates buttons based on possibleActions.
     */
    public void initTurn(int actorIndex, boolean myTurn) {
        // Here we would scan the components and apply BG_HIGHLIGHT to the
        // Player card or Company card that corresponds to the actorIndex.
        recreate();
    }

    @Override
    public void updatePlayerOrder(List<String> playerNames) {
        recreate();
    }

    @Override
    public void refreshDashboard() {
        recreate();
    }

    @Override
    public void setPriorityPlayer(int index) {
        // Your logic to move the [🚂] icon or [👻🚂] prediction
        recreate();
    }

    @Override
    public void highlightCurrentPlayer(int index) {
        // Optional: Change background of the player card
    }

}