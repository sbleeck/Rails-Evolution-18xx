package net.sf.rails.ui.swing.charts;

import net.sf.rails.game.GameManager;
import net.sf.rails.game.Player;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.model.PortfolioModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.stream.Collectors;

/**
 * Creates and displays a graphical chart showing the worth of all players
 * over the history of the game rounds.
 * Features:
 * - "Race" Reveal: Table stays hidden until the winner is announced.
 * - Time Machine: Navigating history updates the table with historical snapshots.
 * - Filter: Major Companies only.
 * - Time Adj Round: A final visual step showing penalty impact.
 */
public class WorthChartWindow extends JDialog {

    private static final Logger log = LoggerFactory.getLogger(WorthChartWindow.class);
    private static WorthChartWindow currentInstance;
    
    private final WorthData data;
    private final RevealController revealController;

    // UI Components
    private WorthChartPanel relativePanel;
    private JTable summaryTable;
    private JScrollPane tableScroll; 
    private JLabel roundLabel;
    private JButton prevButton;
    private JButton nextButton;
    private DefaultTableModel tableModel;
    private JSplitPane splitPane;

    public WorthChartWindow(JFrame parentFrame, GameManager gm) {
        super(parentFrame, "Player Worth History Chart - Reveal Mode", false);
        this.data = new WorthData(gm);
        this.revealController = new RevealController(data.roundKeys.size());
        
        initializeGUI();
        this.setFocusable(true); 
    }

    private void initializeGUI() {
        JPanel contentPanel = new JPanel(new BorderLayout());
        
        // 1. Title
        JLabel titleLabel = new JLabel("Player Relative Worth Analysis", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        contentPanel.add(titleLabel, BorderLayout.NORTH);

        // 2. Split Pane (Chart Top, Table Bottom)
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.95); // Initially give all space to Chart
        
        // Top: Chart
        relativePanel = new WorthChartPanel(data, true, revealController);
        relativePanel.setPreferredSize(new Dimension(1100, 500));
        
        // Bottom: Table
        tableScroll = createSummaryTableSetup();
        tableScroll.setVisible(false); // HIDDEN INITIALLY

        splitPane.setTopComponent(relativePanel);
        splitPane.setBottomComponent(tableScroll);
        
        contentPanel.add(splitPane, BorderLayout.CENTER);

        // 3. Footer
        JPanel footerPanel = new JPanel(new BorderLayout());
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        prevButton = new JButton("< Previous Round");
        nextButton = new JButton("Next Round >");
        roundLabel = new JLabel("Start");
        roundLabel.setFont(new Font("Arial", Font.BOLD, 14));
        roundLabel.setPreferredSize(new Dimension(200, 20)); // Wider for "Time Adjusted" text
        roundLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        prevButton.setVisible(false);
        nextButton.setVisible(false);
        
        navPanel.add(prevButton);
        navPanel.add(roundLabel);
        navPanel.add(nextButton);
        
        prevButton.addActionListener(e -> stepReveal(-1));
        nextButton.addActionListener(e -> stepReveal(1));

        LegendPanel legendPanel = new LegendPanel(data);
        
        footerPanel.add(navPanel, BorderLayout.NORTH);
        footerPanel.add(legendPanel, BorderLayout.SOUTH);
        contentPanel.add(footerPanel, BorderLayout.SOUTH);
        
        this.setContentPane(contentPanel);
        this.setPreferredSize(new Dimension(1200, 900)); 
        this.pack();
        this.setLocationRelativeTo(getOwner());
        
        updateNavState();
        setupHotkeys();
    }

    private JScrollPane createSummaryTableSetup() {
        Vector<String> columns = new Vector<>();
        columns.add("Player");
        columns.add("Cash");
        for (PublicCompany comp : data.majorCompanies) {
            columns.add(comp.getId());
        }
        columns.add("Time (s)");
        columns.add("Worth");     // Changed from "Raw Worth"
        columns.add("Adj. Worth");

        tableModel = new DefaultTableModel(new Vector<>(), columns) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        summaryTable = new JTable(tableModel);
        summaryTable.setFillsViewportHeight(true);
        summaryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); 
        summaryTable.setRowHeight(24);
        summaryTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        summaryTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        
        summaryTable.getColumnModel().getColumn(0).setPreferredWidth(120); 
        summaryTable.getColumnModel().getColumn(1).setPreferredWidth(70);  
        for(int i=2; i<columns.size()-3; i++) {
             summaryTable.getColumnModel().getColumn(i).setPreferredWidth(60); 
        }
        summaryTable.getColumnModel().getColumn(columns.size()-3).setPreferredWidth(70); 
        summaryTable.getColumnModel().getColumn(columns.size()-2).setPreferredWidth(90); 
        summaryTable.getColumnModel().getColumn(columns.size()-1).setPreferredWidth(90); 

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 1; i < summaryTable.getColumnCount(); i++) {
            summaryTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        return new JScrollPane(summaryTable);
    }

    private void updateTableData() {
        tableModel.setRowCount(0); 

        int currentIdx = revealController.getRevealedCount();
        int maxRounds = data.roundKeys.size();
        
        String roundKey = "";
        if (currentIdx > 0 && currentIdx <= maxRounds) {
            roundKey = data.roundKeys.get(currentIdx - 1);
        }
        
        boolean isTimeAdjRound = "Time Adj".equals(roundKey);

        // --- SORTING LOGIC ---
        // If we are in the "Time Adj" round, sort by Adjusted Worth to show the TRUE winner.
        // Otherwise, sort by the Raw Worth at that moment (the Race).
        final String currentRoundKey = roundKey;
        
        List<Player> players = new ArrayList<>(data.gameManager.getPlayers());
        players.sort((p1, p2) -> {
            if (isTimeAdjRound) {
                // Sort by Adjusted
                int w1 = p1.getWorth() + Math.min(0, p1.getTimeBankModel().value());
                int w2 = p2.getWorth() + Math.min(0, p2.getTimeBankModel().value());
                return Integer.compare(w2, w1);
            } else {
                // Sort by Historical Snapshot of Raw Worth
                Double v1 = data.history.get(currentRoundKey).getOrDefault(p1.getId(), 0.0);
                Double v2 = data.history.get(currentRoundKey).getOrDefault(p2.getId(), 0.0);
                return Double.compare(v2, v1);
            }
        });

        for (Player p : players) {
            Vector<Object> row = new Vector<>();
            row.add(p.getName());

            int displayCash = 0;
            Map<String, Integer> displayValues = new java.util.HashMap<>();
            int displayTime = p.getTimeBankModel().value(); 
            int displayRaw = 0;
            
            // --- DATA RETRIEVAL ---
            // Even if roundKey is "Time Adj", we pull assets from "End" (assets don't change, only worth)
            String assetKey = isTimeAdjRound ? "End" : roundKey;
            
            // 1. Worth Snapshot
            // For "Time Adj", the data.history map already contains the penalized worth!
            // But we want to show Raw in the Raw column and Adj in the Adj column.
            
            // Raw Worth is always based on "End" if we are at "Time Adj"
            if (isTimeAdjRound) {
                displayRaw = data.history.get("End").getOrDefault(p.getId(), 0.0).intValue();
            } else {
                displayRaw = data.history.get(roundKey).getOrDefault(p.getId(), 0.0).intValue();
            }

            // 2. Asset Snapshot
            if (data.assetHistory != null && data.assetHistory.containsKey(assetKey)) {
                Map<String, GameManager.PlayerAssetSnapshot> assetSnap = data.assetHistory.get(assetKey);
                GameManager.PlayerAssetSnapshot pSnap = assetSnap.get(p.getName());
                if (pSnap != null) {
                    displayCash = pSnap.cash;
                    // Use the NEW holdingValues map
                    displayValues = pSnap.holdingValues;
                }
            }

            // --- FILL ROW ---
            row.add(displayCash);
            
            for (PublicCompany comp : data.majorCompanies) {
                if (displayValues.containsKey(comp.getId())) {
                    int val = displayValues.get(comp.getId());
                    row.add(val); // Display Value ($)
                } else {
                    row.add("");
                }
            }
            
            int adj = displayRaw + Math.min(0, displayTime);
            
            row.add(displayTime);
            row.add(displayRaw);
            row.add(adj);
            
            tableModel.addRow(row);
        }
        
        if (currentIdx == 0) roundLabel.setText("Start");
        else roundLabel.setText(roundKey);
    }

    private void stepReveal(int direction) {
        if (direction > 0 && revealController.canReveal()) {
            revealController.revealOne();
        } else if (direction < 0 && revealController.canUnreveal()) {
            revealController.unrevealOne();
        }
        
        // Show table only when we reach the end (End or Time Adj)
        // Check if we are at the second-to-last ("End") or last ("Time Adj")
        int current = revealController.getRevealedCount();
        int total = data.roundKeys.size();
        
        // If we are near the end, show the table
        if (current >= total - 1 && !tableScroll.isVisible()) {
            tableScroll.setVisible(true);
            splitPane.setDividerLocation(0.65); 
            prevButton.setVisible(true);
            nextButton.setVisible(true);
        }
        
        relativePanel.repaint();
        if (tableScroll.isVisible()) updateTableData();
        updateNavState();
    }
    
    private void updateNavState() {
        boolean tableVisible = tableScroll.isVisible();
        prevButton.setEnabled(tableVisible && revealController.canUnreveal());
        nextButton.setEnabled(tableVisible && revealController.canReveal());
    }

    private void setupHotkeys() {
        JRootPane rootPane = this.getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SPACE, 0), "next");
        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, 0), "next");
        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, 0), "prev");
        
        actionMap.put("next", new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent e) { stepReveal(1); } });
        actionMap.put("prev", new AbstractAction() { @Override public void actionPerformed(java.awt.event.ActionEvent e) { stepReveal(-1); } });
    }

    public void revealNextRound() { stepReveal(1); }
    public static void showRankingReport(JFrame parentFrame, GameManager gm) { showChart(parentFrame, gm); }
    public static void showChart(JFrame parentFrame, GameManager gm) {
        SwingUtilities.invokeLater(() -> {
            if (currentInstance != null && currentInstance.isVisible()) {
                currentInstance.toFront();
                currentInstance.requestFocus();
                return;
            }
            currentInstance = new WorthChartWindow(parentFrame, gm);
            currentInstance.setVisible(true);
            currentInstance.toFront();
            currentInstance.requestFocus();
        });
    }

    // --- Helpers ---
    private class RevealController {
        private int revealedRounds = 1; 
        private final int totalRounds;
        public RevealController(int totalRounds) { this.totalRounds = totalRounds; }
        public boolean canReveal() { return revealedRounds < totalRounds; }
        public boolean canUnreveal() { return revealedRounds > 1; }
        public void unrevealOne() { if (canUnreveal()) revealedRounds--; }
        public void revealOne() { if (canReveal()) revealedRounds++; }
        public int getRevealedCount() { return revealedRounds; }
    }
    
    private class WorthData {
        public final LinkedHashMap<String, Map<String, Double>> history;
        public final LinkedHashMap<String, Map<String, GameManager.PlayerAssetSnapshot>> assetHistory;
        public final List<String> roundKeys;
        public final List<String> playerNames;
        public final List<PublicCompany> majorCompanies;
        public final double absoluteMin;
        public final double absoluteMax;
        public final GameManager gameManager;
        public final Map<String, Color> colorMap = new java.util.HashMap<>();
        
        private final Color[] PALETTE = { Color.BLUE, Color.RED, new Color(0, 153, 0), Color.ORANGE, Color.MAGENTA, Color.CYAN, Color.PINK, Color.DARK_GRAY };

        public WorthData(GameManager gm) {
            this.gameManager = gm;
            // Copy history so we can modify it locally without affecting GM state
            this.history = new LinkedHashMap<>(gm.getPlayerWorthHistory());
            this.assetHistory = gm.getPlayerAssetHistory();
            
            if (this.history.containsKey("End")) {
                Map<String, Double> endSnapshot = this.history.get("End");
                Map<String, Double> adjSnapshot = new java.util.HashMap<>(endSnapshot);
                
                // Calculate Adjusted Worth for everyone
                for (Player p : gm.getPlayers()) {
                    double raw = endSnapshot.getOrDefault(p.getId(), 0.0);
                    int time = p.getTimeBankModel().value();
                    double penalty = Math.min(0, time);
                    adjSnapshot.put(p.getId(), raw + penalty);
                }
                
                this.history.put("Time Adj", adjSnapshot);
            }
            
            this.majorCompanies = gm.getAllPublicCompanies().stream()
                .filter(c -> c.hasParPrice() && !c.getId().matches("M\\d+"))
                .collect(Collectors.toList());

            Set<String> players = new TreeSet<>();
            double min = 0; double max = 0;
            if (history != null && !history.isEmpty()) {
                for (Map<String, Double> snapshot : history.values()) {
                    players.addAll(snapshot.keySet());
                    for (Double val : snapshot.values()) {
                        if (val < min) min = val;
                        if (val > max) max = val;
                    }
                }
            }
            this.playerNames = new ArrayList<>(players);
            this.roundKeys = history != null ? new ArrayList<>(history.keySet()) : Collections.emptyList();
            this.absoluteMin = min;
            this.absoluteMax = max + (max * 0.1); 
            
            int pIdx = 0;
            for (String p : playerNames) {
                Color c;
                if (p.equalsIgnoreCase("Rainer")) c = new Color(128, 0, 128);
                else if (p.equalsIgnoreCase("Bjoern") || p.equalsIgnoreCase("Björn")) c = new Color(0, 153, 0);
                else { c = PALETTE[pIdx % PALETTE.length]; pIdx++; }
                colorMap.put(p, c);
            }
        }
        public Color getPlayerColor(String name) { return colorMap.getOrDefault(name, Color.BLACK); }
    }

    private class WorthChartPanel extends JPanel {
        // ... (Chart Rendering Code is effectively unchanged, but uses the updated 'history' map) ...
        // To save space in this response, I am omitting the paintComponent details as they are identical
        // to previous versions, simply iterating over data.roundKeys which now includes "Time Adj".
        private final WorthData data;
        private final boolean relativeMode;
        private final RevealController revealController;

        public WorthChartPanel(WorthData data, boolean relativeMode, RevealController rc) {
            this.data = data;
            this.relativeMode = relativeMode;
            this.revealController = rc;
            this.setBackground(Color.WHITE);
            this.setFont(new Font("SansSerif", Font.PLAIN, 10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            if (data.history == null || data.history.isEmpty()) {
                g2d.drawString("No data.", 10, 20); return;
            }

            int w = getWidth(); int h = getHeight();
            int padLeft = 50; int padRight = 30; int padTop = 30; int padBottom = 80; 
            int chartW = w - padLeft - padRight; int chartH = h - padTop - padBottom;
            int roundsToPlot = revealController.getRevealedCount();

            // Scale
            double minVal, maxVal;
            if (relativeMode) {
                double lowestPct = 100.0;
                for (Map<String, Double> snapshot : data.history.values()) {
                    double roundMax = 0;
                    for (Double val : snapshot.values()) if (val > roundMax) roundMax = val;
                    if (roundMax > 0) {
                        for (Double val : snapshot.values()) {
                            double pct = (val / roundMax) * 100.0;
                            if (pct < lowestPct) lowestPct = pct;
                        }
                    }
                }
                minVal = Math.floor(lowestPct / 5.0) * 5.0;
                if (minVal < 0) minVal = 0; if (minVal > 90) minVal = 90;
                maxVal = 100.0;
            } else {
                minVal = data.absoluteMin; maxVal = data.absoluteMax;
            }
            double range = maxVal - minVal; if (range <= 0) range = 100;

            // Grid
            for (int i = 0; i <= 10; i++) {
                int y = padTop + (int)(chartH * i / 10.0);
                double val = maxVal - (range * i / 10.0);
                g2d.setColor(new Color(220, 220, 220)); 
                g2d.drawLine(padLeft, y, w - padRight, y);
                g2d.setColor(Color.BLACK);
                String label = relativeMode ? String.format("%.0f%%", val) : String.format("%,.0f", val);
                g2d.drawString(label, padLeft - g2d.getFontMetrics().stringWidth(label) - 5, y + 5);
            }

            // Axes
            g2d.setStroke(new BasicStroke(2));
            g2d.drawLine(padLeft, padTop, padLeft, h - padBottom);
            g2d.drawLine(padLeft, h - padBottom, w - padRight, h - padBottom);
            
            // X-Labels
            double xStep = (double) chartW / Math.max(1, data.roundKeys.size() - 1);
            g2d.setStroke(new BasicStroke(1));
            for (int i = 0; i < data.roundKeys.size(); i++) {
                double xPos = padLeft + (i * xStep);
                g2d.drawLine((int)xPos, h - padBottom, (int)xPos, h - padBottom + 5);
                String lbl = data.roundKeys.get(i);
                AffineTransform at = g2d.getTransform();
                g2d.translate(xPos, h - padBottom + 20);
                g2d.rotate(Math.PI / 4);
                g2d.drawString(lbl, 0, 0);
                g2d.setTransform(at);
            }

            // Lines
            for (String player : data.playerNames) {
                g2d.setColor(data.getPlayerColor(player));
                g2d.setStroke(new BasicStroke(2.5f)); 
                Path2D path = new Path2D.Double();
                boolean firstPoint = true;
                
                for (int i = 0; i < roundsToPlot; i++) {
                    String roundId = data.roundKeys.get(i);
                    Double val = data.history.get(roundId).getOrDefault(player, 0.0);
                    double plotVal = val;
                    if (relativeMode) {
                        double roundMax = 0;
                        for(Double v : data.history.get(roundId).values()) if (v > roundMax) roundMax = v;
                        plotVal = (roundMax > 0) ? (val / roundMax) * 100.0 : 0;
                    }
                    double x = padLeft + (i * xStep);
                    double y = padTop + chartH - ((plotVal - minVal) / range * chartH);
                    if (firstPoint) { path.moveTo(x, y); firstPoint = false; } 
                    else { path.lineTo(x, y); }
                    g2d.fillOval((int)x - 3, (int)y - 3, 6, 6);
                }
                g2d.draw(path);
            }
        }
    }
    
    private class LegendPanel extends JPanel {
        private final WorthData data;
        public LegendPanel(WorthData data) {
            this.data = data;
            this.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int startX = 10; int startY = 10; int lineSpacing = 20; int pIdx = 0;
            g2d.drawString("Player Legend:", startX, startY);
            for (String player : data.playerNames) {
                g2d.setColor(data.getPlayerColor(player));
                int y = startY + (pIdx + 1) * lineSpacing;
                g2d.fillRect(startX + 10, y - 10, 12, 12);
                g2d.drawRect(startX + 10, y - 10, 12, 12);
                g2d.setColor(Color.BLACK);
                g2d.drawString(player, startX + 35, y);
                pIdx++;
            }
            this.setPreferredSize(new Dimension(getWidth(), startY + (pIdx + 1) * lineSpacing));
        }
    }
}