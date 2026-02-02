package net.sf.rails.ui.swing;

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.*;

import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.OperatingRound;

public class LinearRoundTracker extends JComponent {
    private static final long serialVersionUID = 1L;

    // External Dependencies
    private final GameUIManager gameUIManager;
    private final PublicCompany[] companies;

    // --- STATIC PERSISTENCE (The Fix) ---
    // These must be static to survive the component being destroyed/re-created
    private static double persistentTrainX = -1;
    private static List<PublicCompany> persistentTimeline = new ArrayList<>();
    private static int persistentActiveIndex = -1;

    // Animation State
    private double currentTrainX = -1;
    private double targetTrainX = -1;
    private final Timer animTimer;

    // Cached Data
    private List<PublicCompany> cachedTimeline = new ArrayList<>();
    private int cachedActiveIndex = -1;

    // Geometry
    private final int margin = 40;
    private final int step = 40;

    public LinearRoundTracker(GameUIManager gameUIManager, PublicCompany[] companies) {
        super();
        this.gameUIManager = gameUIManager;
        this.companies = companies;
        
        setOpaque(false);
        
        // 1. Restore Train Position
        if (persistentTrainX != -1) {
            this.currentTrainX = persistentTrainX;
            this.targetTrainX = persistentTrainX;
        }
        
        // Initialize Animation Timer (60 FPS)
        animTimer = new Timer(16, e -> stepAnimation());
    }

    public void updateState() {
        // 1. Rebuild Timeline (Calculates new target)
        rebuildTimeline();

        double newTarget = -1;
        if (cachedActiveIndex != -1) {
            newTarget = margin + (cachedActiveIndex * step);
        }

        // 2. Initial Placement (if brand new and no history)
        if (currentTrainX == -1 && newTarget != -1) {
            currentTrainX = newTarget;
            targetTrainX = newTarget;
            persistentTrainX = newTarget;
        }

        // 3. Trigger Animation
        if (newTarget != -1) {
            if (Math.abs(newTarget - targetTrainX) > 0.1) {
                targetTrainX = newTarget;
                if (!animTimer.isRunning()) {
                    animTimer.start();
                }
            }
        }

        repaint();
    }

    private void stepAnimation() {
        double distance = targetTrainX - currentTrainX;

        if (Math.abs(distance) < 0.5) {
            currentTrainX = targetTrainX;
            animTimer.stop();
        } else {
            double stepMove = distance * 0.1;
            if (Math.abs(stepMove) < 1.0) {
                stepMove = Math.signum(distance) * 1.0;
            }
            currentTrainX += stepMove;
        }

        // Sync Static
        persistentTrainX = currentTrainX;
        
        repaint();
    }

    private void rebuildTimeline() {
        if (gameUIManager == null || gameUIManager.getGameManager() == null) return;

        // --- STEP 0: RESTORE FROM STATIC MEMORY ---
        // If this is a new component (empty list) but we have history, load it.
        // This ensures !cachedTimeline.isEmpty() is TRUE for the freeze check below.
        if (cachedTimeline.isEmpty() && !persistentTimeline.isEmpty()) {
            cachedTimeline.addAll(persistentTimeline);
            cachedActiveIndex = persistentActiveIndex;
        }

        try {
            Object roundObj = gameUIManager.getGameManager().getCurrentRound();
            String roundName = roundObj.getClass().getSimpleName();

            // --- STEP 1: DETECT SPECIAL ROUNDS (FREEZE) ---
            if (roundName.contains("Prussian") || roundName.contains("Formation")) {
                if (!cachedTimeline.isEmpty()) {
                    return; // FREEZE SUCCESSFUL: We kept the restored static data
                }
            }

            // --- STEP 2: STANDARD REBUILD ---
            cachedTimeline.clear();
            cachedActiveIndex = -1;
            PublicCompany activeComp = null;
            
            if (roundObj instanceof OperatingRound) {
                // Operating Round: Use Engine Order
                OperatingRound or = (OperatingRound) roundObj;
                List<PublicCompany> sorted = or.getOperatingCompanies();
                if (sorted != null) cachedTimeline.addAll(sorted);
                activeComp = or.getOperatingCompany();
            } else {
                // Stock Round: Predict Order
                for (PublicCompany c : companies) {
                    if (!c.isClosed() && c.hasFloated()) {
                        cachedTimeline.add(c);
                    }
                }
                Collections.sort(cachedTimeline, (c1, c2) -> {
                    boolean c1Minor = !c1.hasStockPrice();
                    boolean c2Minor = !c2.hasStockPrice();
                    if (c1Minor && !c2Minor) return -1;
                    if (!c1Minor && c2Minor) return 1;
                    if (c1Minor) return Integer.compare(c1.getPublicNumber(), c2.getPublicNumber());

                    int p1 = c1.getCurrentSpace() != null ? c1.getCurrentSpace().getPrice()
                            : (c1.getStartSpace() != null ? c1.getStartSpace().getPrice() : 0);
                    int p2 = c2.getCurrentSpace() != null ? c2.getCurrentSpace().getPrice()
                            : (c2.getStartSpace() != null ? c2.getStartSpace().getPrice() : 0);

                    if (p1 != p2) return Integer.compare(p2, p1);
                    return Integer.compare(c1.getPublicNumber(), c2.getPublicNumber());
                });
            }

            // Find Index
            if (activeComp != null) {
                for (int i = 0; i < cachedTimeline.size(); i++) {
                    if (cachedTimeline.get(i).equals(activeComp)) {
                        cachedActiveIndex = i;
                        break;
                    }
                }
            }
            
            // --- STEP 3: UPDATE STATIC MEMORY ---
            // Save the valid state so the next instance can restore it
            persistentTimeline.clear();
            persistentTimeline.addAll(cachedTimeline);
            persistentActiveIndex = cachedActiveIndex;

        } catch (Exception e) {
            cachedTimeline.clear();
        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (animTimer != null && animTimer.isRunning()) {
            animTimer.stop();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (cachedTimeline.isEmpty()) return;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int r = 13;
        int centerY = getHeight() - 3 - r; 

        // 1. Connector Line
        int lineLength = (cachedTimeline.size() > 1) ? (cachedTimeline.size() - 1) * step : 0;
        g2.setColor(Color.GRAY);
        g2.setStroke(new BasicStroke(2));
        if (cachedTimeline.size() > 1) {
            g2.draw(new Line2D.Double(margin, centerY, margin + lineLength, centerY));
        }

        // 2. Nodes
        for (int i = 0; i < cachedTimeline.size(); i++) {
            PublicCompany c = cachedTimeline.get(i);
            int x = margin + (i * step);

            boolean isPast = (cachedActiveIndex != -1 && i < cachedActiveIndex);
            boolean isActive = (i == cachedActiveIndex);

            Ellipse2D circle = new Ellipse2D.Double(x - r, centerY - r, 2 * r, 2 * r);

            if (isPast) g2.setColor(Color.LIGHT_GRAY);
            else g2.setColor(c.getBgColour());
            g2.fill(circle);

            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(isActive ? 3 : 1));
            g2.draw(circle);

            String code = c.getId();
            g2.setFont(getFont().deriveFont(Font.BOLD, 10f));
            FontMetrics fm = g2.getFontMetrics();

            if (isPast) g2.setColor(Color.DARK_GRAY);
            else g2.setColor(c.getFgColour());

            g2.drawString(code, x - fm.stringWidth(code) / 2, centerY + fm.getAscent() / 2 - 2);
        }

        // 3. Train
        if (currentTrainX != -1 && cachedActiveIndex != -1) {
            drawTrain(g2, (int) currentTrainX, centerY - r - 1);
        }
    }

    private void drawTrain(Graphics2D g2, int x, int y) {
        int w = 32;
        int left = x - (w / 2);
        int bottom = y;

        Color locoColor = new Color(40, 40, 40);
        Color accentColor = new Color(200, 50, 50);

        // Wheels
        g2.setColor(Color.BLACK);
        int wheelSize = 8;
        for (int i = 0; i < 3; i++) {
            g2.fillOval(left + 2 + (i * 9), bottom - wheelSize + 2, wheelSize, wheelSize);
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

        // Cab Window
        g2.setColor(Color.WHITE);
        g2.fillRect(left + 2, bottom - 16, 4, 4);

        // Funnel
        g2.setColor(locoColor);
        g2.fillRect(left + 22, bottom - 16, 4, 6);
        g2.fillRect(left + 21, bottom - 18, 6, 2);

        // Cowcatcher
        g2.setColor(accentColor);
        GeneralPath cow = new GeneralPath();
        cow.moveTo(left + w, bottom - 4);
        cow.lineTo(left + w + 4, bottom);
        cow.lineTo(left + w, bottom);
        cow.closePath();
        g2.fill(cow);

        // Roof Accent
        g2.fillRect(left - 1, bottom - 19, 10, 2);
    }
}