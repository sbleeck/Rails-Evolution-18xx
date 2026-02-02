package net.sf.rails.ui.swing;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;
import net.sf.rails.game.round.RoundFacade;
import net.sf.rails.game.financial.StockRound;
import net.sf.rails.game.OperatingRound;

public class RoundCounterPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int WIDTH = 240;
    private static final int HEIGHT = 45;

    // External Dependency
    private final GameUIManager gameUIManager;

    // Game State
    private int phaseMaxOrs = 1;
    private int currentOrNum = 1;
    private boolean isStockRound = true;

    // Animation State
    private double currentTrainX = -1; // -1 = not set
    private double targetTrainX = -1;
    private final Timer animTimer;

    public RoundCounterPanel(GameUIManager gameUIManager) {
        this.gameUIManager = gameUIManager;
        
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setMinimumSize(new Dimension(WIDTH, HEIGHT));
        setMaximumSize(new Dimension(WIDTH, HEIGHT));
        setOpaque(false);

        // Animation Timer: ~60 FPS
        animTimer = new Timer(16, e -> stepAnimation());
    }

    /**
     * Physics Step: Moves the train towards the target.
     */
    private void stepAnimation() {
        double distance = targetTrainX - currentTrainX;

        // Snap if close enough (0.5 pixel)
        if (Math.abs(distance) < 0.5) {
            currentTrainX = targetTrainX;
            animTimer.stop();
        } else {
            // Ease-Out Logic: Move 5% of remaining distance
            double step = distance * 0.05;

            // Minimum speed to prevent stalling
            if (Math.abs(step) < 0.8) {
                step = Math.signum(distance) * 0.8;
            }

            currentTrainX += step;
        }
        repaint();
    }

    public void updateState() {
        if (gameUIManager == null || gameUIManager.getRoot() == null) return;

        try {
            // 1. Get Phase Limits
            if (gameUIManager.getGameManager() != null) {
                phaseMaxOrs = gameUIManager.getGameManager().getNumberOfOperatingRounds();
            }

            // 2. Determine Current Round & Index
            RoundFacade round = gameUIManager.getGameManager().getCurrentRound();

            // Exclude Prussia/Formation rounds: Maintain previous state (Freeze)
        // We use getClass().getSimpleName() to match the log output you saw (PrussianFormationRound).
        String rName = round.getClass().getSimpleName();
        if (rName.contains("Prussian") || rName.contains("Formation")) {
             return; // Stop here. Do not fall through to "isStockRound = true".
        }
        
            if (round instanceof StockRound) {
                isStockRound = true;
                currentOrNum = 0;
            } else if (round instanceof OperatingRound) {
                isStockRound = false;
                String orId = gameUIManager.getGameManager().getORId();
                currentOrNum = 1;
                if (orId != null && orId.contains(".")) {
                    try {
                        String suffix = orId.substring(orId.lastIndexOf('.') + 1);
                        currentOrNum = Integer.parseInt(suffix);
                    } catch (NumberFormatException e) {
                        currentOrNum = 1;
                    }
                }
            } else {
                isStockRound = true;
            }

            // 3. Calculate Target Position
            int startX = 25;
            int gap = 55;
            int activeIndex;

            if (isStockRound) {
                activeIndex = 3; // SR always last
            } else {
                activeIndex = currentOrNum - 1;
                if (activeIndex < 0) activeIndex = 0;
                if (activeIndex > 2) activeIndex = 2;
            }

            double newTarget = startX + (activeIndex * gap);

            // 4. Trigger Animation
            if (currentTrainX < 0) {
                currentTrainX = newTarget;
                targetTrainX = newTarget;
            } else if (Math.abs(newTarget - targetTrainX) > 0.1) {
                targetTrainX = newTarget;
                if (!animTimer.isRunning()) {
                    animTimer.start();
                }
            }

        } catch (Exception e) {
            // Fail safe
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int startX = 25;
        int gap = 55;
        int trackY = HEIGHT - 12;
        int capW = 34; 
        int capH = 18;

        // --- 1. DRAW TRACK ---
        g2.setStroke(new BasicStroke(3));
        for (int i = 0; i < 3; i++) {
            int x1 = startX + (i * gap);
            int x2 = startX + ((i + 1) * gap);
            boolean isFutureDisabled = ((i + 1) < 3 && (i + 1) >= phaseMaxOrs);

            if (isFutureDisabled) {
                g2.setColor(Color.LIGHT_GRAY);
                g2.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 4}, 0));
            } else {
                g2.setColor(Color.GRAY);
                g2.setStroke(new BasicStroke(3));
            }
            g2.draw(new Line2D.Float(x1, trackY, x2, trackY));
        }

        // --- 2. DRAW STATIONS ---
        g2.setStroke(new BasicStroke(1.5f));
        for (int i = 0; i < 4; i++) {
            int cx = startX + (i * gap);
            String label = (i == 3) ? "SR" : "OR" + (i + 1);
            boolean isDisabled = (i < 3 && i >= phaseMaxOrs);
            
            RoundRectangle2D capsule = new RoundRectangle2D.Float(cx - (capW / 2f), trackY - (capH / 2f), capW, capH, 10, 10);

            if (isDisabled) {
                g2.setColor(new Color(245, 245, 245));
                g2.fill(capsule);
                g2.setColor(Color.LIGHT_GRAY);
                g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{2}, 0));
                g2.draw(capsule);
            } else {
                g2.setColor(Color.WHITE);
                g2.fill(capsule);
                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(capsule);
                
                g2.setColor(Color.BLACK);
                g2.setFont(getFont().deriveFont(Font.BOLD, 10f));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(label, cx - (fm.stringWidth(label) / 2), trackY + (fm.getAscent() / 2) - 2);
            }
        }

        // --- 3. DRAW ANIMATED TRAIN ---
        int drawX = (int) currentTrainX;
        if (drawX < 0) drawX = startX;
        drawTrain(g2, drawX, trackY - 10);
    }

    private void drawTrain(Graphics2D g2, int x, int y) {
        int w = 32;
        int left = x - (w / 2);
        int bottom = y;

        Color locoColor = new Color(40, 40, 40);
        Color accentColor = new Color(200, 50, 50);

        // Wheels
        g2.setColor(Color.BLACK);
        for (int i = 0; i < 3; i++) {
            g2.fillOval(left + 2 + (i * 9), bottom - 6, 8, 8);
        }

        // Chassis
        GeneralPath body = new GeneralPath();
        body.moveTo(left, bottom - 4);
        body.lineTo(left + w, bottom - 4);
        body.lineTo(left + w, bottom - 12);
        body.lineTo(left + 8, bottom - 12);
        body.lineTo(left + 8, bottom - 18);
        body.lineTo(left, bottom - 18);
        body.closePath();

        g2.setColor(locoColor);
        g2.fill(body);

        // Details
        g2.setColor(Color.WHITE);
        g2.fillRect(left + 2, bottom - 16, 4, 4); // Cab Window
        g2.setColor(locoColor);
        g2.fillRect(left + 22, bottom - 16, 4, 6); // Funnel
        g2.fillRect(left + 21, bottom - 18, 6, 2); // Funnel top

        // Cowcatcher
        g2.setColor(accentColor);
        GeneralPath cow = new GeneralPath();
        cow.moveTo(left + w, bottom - 4);
        cow.lineTo(left + w + 4, bottom);
        cow.lineTo(left + w, bottom);
        cow.closePath();
        g2.fill(cow);
        
        g2.fillRect(left - 1, bottom - 19, 10, 2); // Roof
    }
}