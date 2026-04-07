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
import java.util.HashSet;
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
 * - Time Machine: Navigating history updates the table with historical
 * snapshots.
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
    private JButton prevRoundButton;
    private JButton nextRoundButton;

    private DefaultTableModel tableModel;
    private JSplitPane splitPane;
    private JSlider timeSlider;
    private LegendPanel legendPanel;
    private String[] macroGroups;

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

        // Pre-compute unified macro groups to aggressively clump "Start" phases 
        // and provide accurate boundary indices for the chart and navigation.
        macroGroups = new String[data.roundKeys.size()];
        String lastMajor = "Start";
        for (int i = 0; i < data.roundKeys.size(); i++) {
            String fullKey = data.roundKeys.get(i);
            String macro = fullKey.contains(":") ? fullKey.split(":")[1] : fullKey;
            boolean isMajor = macro.startsWith("OR") || macro.startsWith("SR")
                    || macro.contains("M&A") || macro.contains("Merger")
                    || macro.startsWith("MR") || macro.equalsIgnoreCase("Start")
                    || macro.equalsIgnoreCase("End") || macro.equalsIgnoreCase("Time Adj");
            if (isMajor) lastMajor = macro;
            macroGroups[i] = lastMajor;
        }

        // 2. Split Pane (Chart Top, Table Bottom)
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.95); // Initially give all space to Chart

        // Top: Chart
        relativePanel = new WorthChartPanel(data, true, revealController);
        relativePanel.setPreferredSize(new Dimension(1000, 250));

        relativePanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                stepReveal(1); // Advance on any mouse click
            }
        });

        // Bottom: Table
        tableScroll = createSummaryTableSetup();
        tableScroll.setVisible(false); // HIDDEN INITIALLY

        splitPane.setTopComponent(relativePanel);
        splitPane.setBottomComponent(tableScroll);

        contentPanel.add(splitPane, BorderLayout.CENTER);

        // 3. Footer
        JPanel footerPanel = new JPanel(new BorderLayout());

        JPanel navPanel = new JPanel(new BorderLayout(10, 0));
        navPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        prevRoundButton = new JButton("<< Round");
        prevButton = new JButton("< Move");
        nextButton = new JButton("Move >");
        nextRoundButton = new JButton("Round >>");

        prevRoundButton.addActionListener(e -> stepRound(-1));
        prevButton.addActionListener(e -> stepReveal(-1));
        nextButton.addActionListener(e -> stepReveal(1));
        nextRoundButton.addActionListener(e -> stepRound(1));

        buttonPanel.add(prevRoundButton);
        buttonPanel.add(prevButton);
        buttonPanel.add(nextButton);
        buttonPanel.add(nextRoundButton);

        int maxSnaps = Math.max(0, data.roundKeys.size() - 1);

        // --- DELETE ---
        // timeSlider = new JSlider(JSlider.HORIZONTAL, 0, maxSnaps, 0);
        // // Disable uniform ticks because they correspond to individual moves, not
        // rounds
        // timeSlider.setPaintTicks(false);
        //
        // String initLabel = data.roundKeys.isEmpty() ? "Start" :
        // formatSliderLabel(data.roundKeys.get(0));
        // roundLabel = new JLabel(initLabel, SwingConstants.CENTER); // FIX CRASH
        //
        // roundLabel = new JLabel(initLabel, SwingConstants.CENTER);
        // --- START FIX ---
        timeSlider = new JSlider(JSlider.HORIZONTAL, 0, maxSnaps, 0);
        timeSlider.setMinorTickSpacing(1);
        timeSlider.setSnapToTicks(true);
        timeSlider.setPaintTicks(false);

        String initLabel = data.roundKeys.isEmpty() ? "Start" : formatSliderLabel(data.roundKeys.get(0));
        roundLabel = new JLabel(initLabel, SwingConstants.CENTER);
        // --- END FIX ---
        roundLabel.setFont(new Font("Arial", Font.BOLD, 14));
        roundLabel.setPreferredSize(new Dimension(150, 20));

        timeSlider.addChangeListener(e -> {
            int snapIndex = timeSlider.getValue();
            if (snapIndex < data.roundKeys.size()) {
                String snapKey = data.roundKeys.get(snapIndex);
                roundLabel.setText(formatSliderLabel(snapKey));

                revealController.setRevealedCount(snapIndex + 1);
                relativePanel.repaint();
                if (legendPanel != null) {
                    legendPanel.updateLegend();
                }
                if (tableScroll.isVisible())
                    updateTableData();

                if (macroGroups != null && snapIndex < macroGroups.length) {
                    String m = macroGroups[snapIndex];
                    if (m.startsWith("OR")) {
                        if (prevButton != null) prevButton.setText("< Company");
                        if (nextButton != null) nextButton.setText("Company >");
                    } else if (m.startsWith("SR")) {
                        if (prevButton != null) prevButton.setText("< Player");
                        if (nextButton != null) nextButton.setText("Player >");
                    } else {
                        if (prevButton != null) prevButton.setText("< Move");
                        if (nextButton != null) nextButton.setText("Move >");
                    }
                }

                if (!timeSlider.getValueIsAdjusting()) {
                    try {
                        int targetMove = -1;
                        if (snapKey.startsWith("Move_")) {
                            targetMove = Integer.parseInt(snapKey.split(":")[0].substring(5));
                        } else if (snapKey.matches("\\d+")) {
                            targetMove = Integer.parseInt(snapKey);
                        }

                        if (targetMove >= 0) {
                            scrubToMove(targetMove);
                        }
                    } catch (NumberFormatException ex) {
                        log.warn("Cannot scrub: invalid move format: " + snapKey);
                    }
                }

            }
        });

        JPanel sliderPanel = new JPanel(new BorderLayout(10, 0));
        sliderPanel.add(new JLabel("Start"), BorderLayout.WEST);
        sliderPanel.add(timeSlider, BorderLayout.CENTER);
        sliderPanel.add(roundLabel, BorderLayout.EAST);

        navPanel.add(sliderPanel, BorderLayout.NORTH);
        navPanel.add(buttonPanel, BorderLayout.SOUTH);

        legendPanel = new LegendPanel(data, revealController);

        footerPanel.add(navPanel, BorderLayout.NORTH);
        footerPanel.add(legendPanel, BorderLayout.SOUTH);
        contentPanel.add(footerPanel, BorderLayout.SOUTH);

        this.setContentPane(contentPanel);
        this.setPreferredSize(new Dimension(1200, 450));
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
        columns.add("Worth"); // Changed from "Raw Worth"
        columns.add("Adj. Worth");

        tableModel = new DefaultTableModel(new Vector<>(), columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        summaryTable = new JTable(tableModel);
        summaryTable.setFillsViewportHeight(true);
        summaryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        summaryTable.setRowHeight(24);
        summaryTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        summaryTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));

        summaryTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        summaryTable.getColumnModel().getColumn(1).setPreferredWidth(70);
        for (int i = 2; i < columns.size() - 3; i++) {
            summaryTable.getColumnModel().getColumn(i).setPreferredWidth(60);
        }
        summaryTable.getColumnModel().getColumn(columns.size() - 3).setPreferredWidth(70);
        summaryTable.getColumnModel().getColumn(columns.size() - 2).setPreferredWidth(90);
        summaryTable.getColumnModel().getColumn(columns.size() - 1).setPreferredWidth(90);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 1; i < summaryTable.getColumnCount(); i++) {
            summaryTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        return new JScrollPane(summaryTable);
    }

    private String formatMacroName(String macro) {
        if (macro == null)
            return "";
        if (macro.startsWith("OR_") || macro.startsWith("SR_") || macro.startsWith("MR_"))
            return macro.replace("_", " ");
        if (macro.contains("M&A") || macro.contains("Merger") || macro.contains("Acquisition"))
            return macro.replace("_", " ");
        if (macro.equalsIgnoreCase("Start") || macro.equalsIgnoreCase("End") || macro.equalsIgnoreCase("Time Adj"))
            return macro;
        return macro.length() > 3 ? macro.substring(0, 3) : macro;
    }

    private String formatSliderLabel(String snapKey) {
        if (!snapKey.contains(":"))
            return snapKey;
        String[] parts = snapKey.split(":");
        String movePart = parts[0].replace("_", " ");
        String macroPart = formatMacroName(parts[1]);
        return movePart + " (" + macroPart + ")";
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
        // If we are in the "Time Adj" round, sort by Adjusted Worth to show the TRUE
        // winner.
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
            // Even if roundKey is "Time Adj", we pull assets from "End" (assets don't
            // change, only worth)
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

        if (currentIdx == 0)
            roundLabel.setText("Start");
        else
            roundLabel.setText(roundKey);
    }

    private void stepReveal(int direction) {
        if (direction > 0 && revealController.canReveal()) {
            revealController.revealOne();
        } else if (direction < 0 && revealController.canUnreveal()) {
            revealController.unrevealOne();
        }

        // Keep slider physical position in sync with arrow key navigation
        if (timeSlider != null && timeSlider.getValue() != revealController.getRevealedCount() - 1) {
            timeSlider.setValue(revealController.getRevealedCount() - 1);
        }

        // Show table only when we reach the end (End or Time Adj)
        // Check if we are at the second-to-last ("End") or last ("Time Adj")
        int current = revealController.getRevealedCount();
        int total = data.roundKeys.size();

        // If we are near the end, show the table
        if (current >= total - 1 && !tableScroll.isVisible()) {
            tableScroll.setVisible(true);
            splitPane.setDividerLocation(0.65);
        }

        relativePanel.repaint();
        if (legendPanel != null) {
            legendPanel.updateLegend();
        }
        if (tableScroll.isVisible())
            updateTableData();
        updateNavState();
    }


    private void stepRound(int direction) {
        if (timeSlider == null || macroGroups == null || macroGroups.length == 0) return;
        
        int currentIdx = timeSlider.getValue();
        String currentMacro = macroGroups[currentIdx];
        int targetIdx = currentIdx;

        if (direction > 0) {
            // Seek start of the next block
            for (int i = currentIdx + 1; i < macroGroups.length; i++) {
                if (!macroGroups[i].equals(currentMacro)) {
                    targetIdx = i;
                    break;
                }
            }
            if (targetIdx == currentIdx) targetIdx = macroGroups.length - 1;
        } else {
            // Find the start of the current block
            int startOfCurrent = currentIdx;
            for (int i = currentIdx; i >= 0; i--) {
                if (macroGroups[i].equals(currentMacro)) startOfCurrent = i;
                else break;
            }

            if (currentIdx > startOfCurrent) {
                // We were mid-round, jump to the beginning of the current round
                targetIdx = startOfCurrent;
            } else if (startOfCurrent > 0) {
                // We were already at the start, jump to the beginning of the previous round
                String prevMacro = macroGroups[startOfCurrent - 1];
                int startOfPrev = startOfCurrent - 1;
                for (int i = startOfCurrent - 1; i >= 0; i--) {
                    if (macroGroups[i].equals(prevMacro)) startOfPrev = i;
                    else break;
                }
                targetIdx = startOfPrev;
            }
        }

        if (targetIdx != currentIdx) {
            revealController.setRevealedCount(targetIdx + 1);
            timeSlider.setValue(targetIdx);
        }
    }

    private void updateNavState() {
        boolean canUnreveal = revealController.canUnreveal();
        boolean canReveal = revealController.canReveal();

        if (prevButton != null)
            prevButton.setEnabled(canUnreveal);
        if (nextButton != null)
            nextButton.setEnabled(canReveal);
        if (prevRoundButton != null)
            prevRoundButton.setEnabled(canUnreveal);
        if (nextRoundButton != null)
            nextRoundButton.setEnabled(canReveal);
    }

    private void setupHotkeys() {
        JRootPane rootPane = this.getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        // Keep specific navigation for Arrows
        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, 0), "next");
        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, 0), "prev");

        actionMap.put("next", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                stepReveal(1);
            }
        });
        actionMap.put("prev", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                stepReveal(-1);
            }
        });

        // Add "Any Key" support using a KeyListener
        this.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                // If it's not the left arrow (which we want for "back"), any key advances
                if (e.getKeyCode() != java.awt.event.KeyEvent.VK_LEFT) {
                    stepReveal(1);
                }
            }
        });
    }

    public void revealNextRound() {
        stepReveal(1);
    }

    public static void showRankingReport(JFrame parentFrame, GameManager gm) {
        showChart(parentFrame, gm);
    }

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
    // --- DELETE ---
    // private class RevealController {
    // private int revealedRounds = 1;
    // private final int totalRounds;
    //
    // public RevealController(int totalRounds) {
    // this.totalRounds = totalRounds;
    // }
    //
    // public boolean canReveal() {
    // return revealedRounds < totalRounds;
    // }
    //
    // public boolean canUnreveal() {
    // return revealedRounds > 1;
    // }
    //
    // public void unrevealOne() {
    // if (canUnreveal())
    // revealedRounds--;
    // }
    //
    // public void revealOne() {
    // if (canReveal())
    // revealedRounds++;
    // }
    //
    // public int getRevealedCount() {
    // return revealedRounds;
    // }
    //
    // public void setRevealedCount(int count) {
    // if (count >= 1 && count <= totalRounds) {
    // this.revealedRounds = count;
    // }
    // }
    // }
    // --- START FIX ---
    private class RevealController {
        private int revealedSnapshots = 1;
        private final int totalSnapshots;

        public RevealController(int totalSnapshots) {
            this.totalSnapshots = totalSnapshots;
        }

        public boolean canReveal() {
            return revealedSnapshots < totalSnapshots;
        }

        public boolean canUnreveal() {
            return revealedSnapshots > 1;
        }

        public void unrevealOne() {
            if (canUnreveal())
                revealedSnapshots--;
        }

        public void revealOne() {
            if (canReveal())
                revealedSnapshots++;
        }

        public int getRevealedCount() {
            return revealedSnapshots;
        }

        public void setRevealedCount(int count) {
            if (count >= 1 && count <= totalSnapshots) {
                this.revealedSnapshots = count;
            }
        }
    }
    // --- END FIX ---

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

        private final Color[] PALETTE = {
                new Color(31, 119, 180), // Blau
                new Color(255, 127, 14), // Orange
                new Color(44, 160, 44), // Grün
                new Color(214, 39, 40), // Rot
                new Color(148, 103, 189), // Violett
                new Color(188, 189, 34), // Oliv
                new Color(23, 190, 207) // Cyan
        };

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
            double min = 0;
            double max = 0;
            if (history != null && !history.isEmpty()) {
                for (Map<String, Double> snapshot : history.values()) {
                    players.addAll(snapshot.keySet());
                    for (Double val : snapshot.values()) {
                        if (val < min)
                            min = val;
                        if (val > max)
                            max = val;
                    }
                }
            }
            this.playerNames = new ArrayList<>(players);
            this.roundKeys = history != null ? new ArrayList<>(history.keySet()) : Collections.emptyList();
            this.absoluteMin = min;
            this.absoluteMax = max + (max * 0.1);

            int pIdx = 0;
            for (String p : playerNames) {
                Color c = PALETTE[pIdx % PALETTE.length];
                colorMap.put(p, c);
                pIdx++;
            }
        }

        public Color getPlayerColor(String name) {
            return colorMap.getOrDefault(name, Color.BLACK);
        }
    }

    private class WorthChartPanel extends JPanel {
        // ... (Chart Rendering Code is effectively unchanged, but uses the updated
        // 'history' map) ...
        // To save space in this response, I am omitting the paintComponent details as
        // they are identical
        // to previous versions, simply iterating over data.roundKeys which now includes
        // "Time Adj".
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
                g2d.drawString("No data.", 10, 20);
                return;
            }

            int w = getWidth();
            int h = getHeight();
            int padLeft = 50;
            int padRight = 30;
            int padTop = 30;
            int padBottom = 80;
            int chartW = w - padLeft - padRight;
            int chartH = h - padTop - padBottom;
            int roundsToPlot = revealController.getRevealedCount();

            // Scale
            double minVal, maxVal;
            if (relativeMode) {
                double lowestPct = 100.0;
                for (Map<String, Double> snapshot : data.history.values()) {
                    double roundMax = 0;
                    for (Double val : snapshot.values())
                        if (val > roundMax)
                            roundMax = val;
                    if (roundMax > 0) {
                        for (Double val : snapshot.values()) {
                            double pct = (val / roundMax) * 100.0;
                            if (pct < lowestPct)
                                lowestPct = pct;
                        }
                    }
                }
                minVal = Math.floor(lowestPct / 5.0) * 5.0;
                if (minVal < 0)
                    minVal = 0;
                if (minVal > 90)
                    minVal = 90;
                maxVal = 100.0;
            } else {
                minVal = data.absoluteMin;
                maxVal = data.absoluteMax;
            }
            double range = maxVal - minVal;
            if (range <= 0)
                range = 100;

            double xStep = (double) chartW / Math.max(1, data.roundKeys.size() - 1);

            // 1. Background Bands and Macro-Round Labels
            class RoundBand {
                String name;
                String originalMacro;
                int startIdx;
                int endIdx;

                RoundBand(String n, String orig, int s, int e) {
                    name = n;
                    originalMacro = orig;
                    startIdx = s;
                    endIdx = e;
                }
            }
            
            java.util.List<RoundBand> bands = new java.util.ArrayList<>();
            if (WorthChartWindow.this.macroGroups != null && WorthChartWindow.this.macroGroups.length > 0) {
                String currentMacro = WorthChartWindow.this.macroGroups[0];
                int startIdx = 0;
                for (int i = 1; i < WorthChartWindow.this.macroGroups.length; i++) {
                    if (!WorthChartWindow.this.macroGroups[i].equals(currentMacro)) {
                        bands.add(new RoundBand(WorthChartWindow.this.formatMacroName(currentMacro), currentMacro, startIdx, i - 1));
                        currentMacro = WorthChartWindow.this.macroGroups[i];
                        startIdx = i;
                    }
                }
                bands.add(new RoundBand(WorthChartWindow.this.formatMacroName(currentMacro), currentMacro, startIdx, WorthChartWindow.this.macroGroups.length - 1));
            }
            
            Set<Integer> boundaryIndices = new HashSet<>();
            for (RoundBand band : bands) {
                boundaryIndices.add(band.startIdx);
                boundaryIndices.add(band.endIdx);
            }

            int globalMaxOrCycle = -1;
            int globalMaxSubRound = 1;
            for (String key : data.roundKeys) {
                String macro = key.contains(":") ? key.split(":")[1] : key;
                if (macro.startsWith("OR_")) {
                    try {
                        String[] parts = macro.substring(3).split("\\.");
                        int cycle = Integer.parseInt(parts[0]);
                        int sub = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                        if (cycle > globalMaxOrCycle)
                            globalMaxOrCycle = cycle;
                        if (sub > globalMaxSubRound)
                            globalMaxSubRound = sub;
                    } catch (Exception ex) {
                    }
                }
            }

            for (RoundBand band : bands) {
                int startX = (int) (padLeft + band.startIdx * xStep);
                int endX = (int) (padLeft + band.endIdx * xStep);
                if (band.endIdx == data.roundKeys.size() - 1)
                    endX = w - padRight;

                Color bgColor = Color.WHITE;
                if (band.originalMacro.startsWith("OR_")) {
                    try {
                        String[] parts = band.originalMacro.substring(3).split("\\.");
                        String orCycleStr = parts[0];
                        int currentOrCycle = Integer.parseInt(orCycleStr);

                        int maxSubRound = 1;
                        String targetMacroPrefix = "OR_" + orCycleStr + ".";
                        for (String key : data.roundKeys) {
                            String macro = key.contains(":") ? key.split(":")[1] : key;
                            if (macro.startsWith(targetMacroPrefix)) {
                                try {
                                    int sub = Integer.parseInt(macro.substring(targetMacroPrefix.length()));
                                    if (sub > maxSubRound)
                                        maxSubRound = sub;
                                } catch (Exception ex) {
                                }
                            }
                        }
                        if (currentOrCycle == globalMaxOrCycle) {
                            maxSubRound = Math.max(maxSubRound, globalMaxSubRound);
                        }

                        if (maxSubRound == 1)
                            bgColor = new Color(255, 255, 230); // Light Yellow
                        else if (maxSubRound == 2)
                            bgColor = new Color(235, 255, 235); // Light Green
                        else if (maxSubRound == 3)
                            bgColor = new Color(245, 235, 220); // Light Brown
                        else
                            bgColor = new Color(230, 230, 230); // Light Gray
                    } catch (Exception e) {
                        bgColor = new Color(245, 245, 245);
                    }
                } else if (band.originalMacro.startsWith("SR_")) {
                    bgColor = new Color(245, 245, 245); // Light Grey
                } else if (band.originalMacro.contains("M&A") || band.originalMacro.contains("Merger")) {
                    bgColor = new Color(230, 240, 255); // Light Blue for M&A
                }

                g2d.setColor(bgColor);
                g2d.fillRect(startX, padTop, endX - startX, chartH);

                g2d.setColor(Color.BLACK);
                int strW = g2d.getFontMetrics().stringWidth(band.name);
                int textX = startX + (endX - startX) / 2 - strW / 2;
                textX = Math.max(padLeft, Math.min(textX, w - padRight - strW)); // Prevent clipping
                g2d.drawString(band.name, textX, h - padBottom + 20);

                g2d.setColor(Color.BLACK);
                g2d.drawLine(startX, h - padBottom, startX, h - padBottom + 5);
            }

            // Grid
            for (int i = 0; i <= 10; i++) {
                int y = padTop + (int) (chartH * i / 10.0);
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
                        for (Double v : data.history.get(roundId).values())
                            if (v > roundMax)
                                roundMax = v;
                        plotVal = (roundMax > 0) ? (val / roundMax) * 100.0 : 0;
                    }
                    double x = padLeft + (i * xStep);
                    double y = padTop + chartH - ((plotVal - minVal) / range * chartH);
                    if (firstPoint) {
                        path.moveTo(x, y);
                        firstPoint = false;
                    } else {
                        path.lineTo(x, y);
                    }

                    // Only draw dots at the macro-round boundaries or the current revealed position
                    if (boundaryIndices.contains(i) || i == roundsToPlot - 1) {
                        g2d.fillOval((int) x - 3, (int) y - 3, 6, 6);
                    }

                }
                g2d.draw(path);
            }
        }
    }

    private class LegendPanel extends JPanel {
        private final WorthData data;
        private final RevealController revealController;
        private final JTable legendTable;
        private final DefaultTableModel tableModel;

        public LegendPanel(WorthData data, RevealController rc) {
            this.data = data;
            this.revealController = rc;
            this.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
            this.setBorder(BorderFactory.createTitledBorder("Player Legend (Current Snapshot)"));

            String[] columns = { "Color", "Player Name", "Worth", "% of Leader" };
            tableModel = new DefaultTableModel(null, columns) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

            legendTable = new JTable(tableModel);
            legendTable.setRowHeight(24);
            legendTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
            legendTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));

            // Custom renderer to draw the color squares inside the cell
            legendTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                        boolean hasFocus, int row, int column) {
                    JLabel label = (JLabel) super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row,
                            column);
                    if (value instanceof Color) {
                        label.setIcon(new javax.swing.Icon() {
                            public void paintIcon(Component c, Graphics g, int x, int y) {
                                g.setColor((Color) value);
                                g.fillRect(x + 5, y + 2, 16, 16);
                                g.setColor(Color.BLACK);
                                g.drawRect(x + 5, y + 2, 16, 16);
                            }

                            public int getIconWidth() {
                                return 26;
                            }

                            public int getIconHeight() {
                                return 20;
                            }
                        });
                    }
                    label.setHorizontalAlignment(SwingConstants.CENTER);
                    return label;
                }
            });

            DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
            centerRenderer.setHorizontalAlignment(JLabel.CENTER);
            for (int i = 1; i < columns.length; i++) {
                legendTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
            }

            legendTable.getColumnModel().getColumn(0).setPreferredWidth(50);
            legendTable.getColumnModel().getColumn(1).setPreferredWidth(120);
            legendTable.getColumnModel().getColumn(2).setPreferredWidth(100);
            legendTable.getColumnModel().getColumn(3).setPreferredWidth(100);

            JScrollPane scrollPane = new JScrollPane(legendTable);
            scrollPane.setPreferredSize(new Dimension(390, 130));
            this.add(scrollPane);

            updateLegend();
        }

        public void updateLegend() {
            tableModel.setRowCount(0);

            int currentIdx = revealController.getRevealedCount();
            String currentRoundKey = "";
            if (currentIdx > 0 && currentIdx <= data.roundKeys.size()) {
                currentRoundKey = data.roundKeys.get(currentIdx - 1);
            }

            class PlayerInfo {
                String name;
                double worth;
                Color color;

                PlayerInfo(String n, double w, Color c) {
                    name = n;
                    worth = w;
                    color = c;
                }
            }

            List<PlayerInfo> players = new ArrayList<>();
            double maxWorth = 0;
            Map<String, Double> snapshot = data.history.get(currentRoundKey);
            if (snapshot == null)
                snapshot = Collections.emptyMap();

            for (String pName : data.playerNames) {
                double worth = snapshot.getOrDefault(pName, 0.0);
                if (worth > maxWorth)
                    maxWorth = worth;
                players.add(new PlayerInfo(pName, worth, data.getPlayerColor(pName)));
            }

            // Sort descending by current snapshot worth
            players.sort((a, b) -> Double.compare(b.worth, a.worth));

            for (PlayerInfo p : players) {
                String pctStr = (maxWorth > 0) ? String.format("%.1f%%", (p.worth / maxWorth) * 100.0) : "0.0%";
                tableModel.addRow(new Object[] { p.color, p.name, String.format("%,.0f", p.worth), pctStr });
            }
        }
    }

    private void scrubToMove(int targetMove) {
        GameManager gm = data.gameManager;
        if (gm == null || gm.getRoot() == null)
            return;

        net.sf.rails.ui.swing.GameUIManager uiMgr = gm.getGameUIManager();

        // --- UI Manager Fallback Resolution ---
        // gm.getGameUIManager() returns null during certain lifecycle states.
        // We actively resolve it by traversing the active window hierarchy.
        if (uiMgr == null) {
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                Class<?> clazz = w.getClass();
                while (clazz != null && clazz != Object.class) {
                    for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                        if (net.sf.rails.ui.swing.GameUIManager.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            try {
                                net.sf.rails.ui.swing.GameUIManager temp = (net.sf.rails.ui.swing.GameUIManager) f
                                        .get(w);
                                if (temp != null && temp.getRoot() == gm.getRoot()) {
                                    uiMgr = temp;
                                    break;
                                }
                            } catch (Exception e) {
                            }
                        }
                    }
                    if (uiMgr != null)
                        break;
                    clazz = clazz.getSuperclass();
                }
                if (uiMgr != null)
                    break;
            }
        }

        if (uiMgr == null) {
            log.warn("Cannot scrub: GameUIManager is null and could not be resolved via reflection.");
            return;
        }

        net.sf.rails.game.state.ChangeStack changeStack = gm.getRoot().getStateManager().getChangeStack();
        int currentMove = gm.getActionCountModel().value();

        if (currentMove == targetMove)
            return;

        log.info("Scrubbing timeline to move {}", targetMove);
        this.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));

        try {
            // 1. Single-Pass Proportional Search
            // We nudge the model directly to the target. No reverting, no triple-execution.
            int loops = 0;
            while (gm.getActionCountModel().value() != targetMove && loops < 100) {
                currentMove = gm.getActionCountModel().value();
                int currentIndex = changeStack.getCurrentIndex();
                int diff = currentMove - targetMove;

                if (diff > 0) {
                    int nextIdx = Math.max(1, currentIndex - diff);
                    if (currentIndex > nextIdx && changeStack.isUndoPossible()) {
                        changeStack.undo(nextIdx);
                    } else
                        break;
                } else if (diff < 0) {
                    int nextIdx = Math.min(changeStack.getMaximumIndex(), currentIndex - diff);
                    if (currentIndex < nextIdx && changeStack.isRedoPossible()) {
                        changeStack.redo(nextIdx);
                    } else
                        break;
                }
                loops++;
            }

            // 2. Synchronize UI Structure
            // changeStack triggers Observer data updates, but updateUI() forces structural
            // rebuilds
            // (e.g., StatusWindow layout, Company properties) bypassing the async
            // processAction queue.
            uiMgr.updateUI();
            if (uiMgr.getStatusWindow() != null) {
                uiMgr.getStatusWindow().setGameActions();
            }

        } catch (Exception e) {
            log.error("Error during timeline scrubbing.", e);
        } finally {
            this.setCursor(java.awt.Cursor.getDefaultCursor());
        }
    }

}