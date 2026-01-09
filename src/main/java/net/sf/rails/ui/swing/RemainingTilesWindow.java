package net.sf.rails.ui.swing;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import javax.swing.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.rails.common.LocalText;
import net.sf.rails.game.Tile;
import net.sf.rails.game.TileManager;
import net.sf.rails.game.PhaseManager;
import net.sf.rails.game.RailsRoot;
import net.sf.rails.game.state.Observable;
import net.sf.rails.game.state.Observer;
import net.sf.rails.ui.swing.elements.Field;

public class RemainingTilesWindow extends JFrame implements WindowListener, ActionListener {
    private static final long serialVersionUID = 1L;
    private ORWindow orWindow;
    private final AlignedWidthPanel tilePanel;
    private final JScrollPane slider;
    private final Map<Tile, Field> tileLabels = new HashMap<>();
    private final Map<Tile, Observer> observerMap = new HashMap<>();
    private static final Logger log = LoggerFactory.getLogger(RemainingTilesWindow.class);

    public RemainingTilesWindow(ORWindow orWindow) {
        super();
        this.orWindow = orWindow;
        tilePanel = new AlignedWidthPanel();
        slider = new JScrollPane(tilePanel);
        slider.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        slider.setPreferredSize(new Dimension(200,200));
        tilePanel.setParentSlider(slider);
        tilePanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        init(orWindow.getGameUIManager());

        if (!orWindow.isDockingFrameworkEnabled()) {
            setTitle("Rails: Remaining Tiles");
            setVisible(false);
            setContentPane(slider);
            setSize(800, 600);
            addWindowListener(this);
            setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
            setLocationRelativeTo(orWindow);
            setVisible(true);
        }
    }

    private void init(GameUIManager gameUIManager) {
        TileManager tmgr = gameUIManager.getRoot().getTileManager();
        Set<Tile> tiles = tmgr.getTiles();
        for (Tile tile:tiles) {
            if (tile.isPrepainted() || tile.isHidden()) continue;
            String picId = tile.getPictureId();
            BufferedImage hexImage = ImageLoader.getInstance().getTile(picId, 10);
            if (hexImage != null) hexImage = rotateImage(hexImage, 30.0);
            ImageIcon hexIcon = new ImageIcon(hexImage);
            hexIcon.setImage(hexIcon.getImage().getScaledInstance(
                    (int) (hexIcon.getIconWidth() * 0.8),
                    (int) (hexIcon.getIconHeight() * 0.8),
                    Image.SCALE_SMOOTH));
            Field label = new Field(tile.getCountModel(), hexIcon, Field.CENTER);
            label.setVerticalTextPosition(Field.BOTTOM);
            label.setHorizontalTextPosition(Field.CENTER);
            label.setVisible(true);
            tilePanel.add(label);
            tileLabels.put(tile, label);
            Observer watcher = new Observer() {
                @Override public void update(String text) { refreshCounts(); }
                @Override public Observable getObservable() { return null; }
            };
            tile.getTilesLaid().addObserver(watcher);
            observerMap.put(tile, watcher);
        }
    }
    
    public static BufferedImage rotateImage(BufferedImage source, double angleDegrees) {
        if (source == null) return null;
        double rads = Math.toRadians(angleDegrees);
        double sin = Math.abs(Math.sin(rads));
        double cos = Math.abs(Math.cos(rads));
        int w = source.getWidth();
        int h = source.getHeight();
        int newWidth = (int) Math.floor(w * cos + h * sin);
        int newHeight = (int) Math.floor(h * cos + w * sin);
        BufferedImage rotated = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = rotated.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        AffineTransform at = new AffineTransform();
        at.translate((newWidth - w) / 2.0, (newHeight - h) / 2.0);
        at.rotate(rads, w / 2.0, h / 2.0);
        g2d.setTransform(at);
        g2d.drawImage(source, 0, 0, null);
        g2d.dispose();
        return rotated;
    }

    private void refreshCounts() {
        for ( Map.Entry<Tile, Field> entry : tileLabels.entrySet() ) {
            entry.getValue().setText(entry.getKey().getCountModel().toText());
        }
    }

    @Override public void actionPerformed(ActionEvent actor) {}
    @Override public void windowActivated(WindowEvent e) { refreshCounts(); }
    @Override public void windowClosed(WindowEvent e) {}
    @Override public void windowClosing(WindowEvent e) {
        orWindow.getGameUIManager().uncheckMenuItemBox(LocalText.getText("MAP"));
        for ( Map.Entry<Tile, Observer> entry : observerMap.entrySet() ) {
            entry.getKey().getTilesLaid().removeObserver(entry.getValue());
        }
        dispose();
    }
    @Override public void windowDeactivated(WindowEvent e) {}
    @Override public void windowDeiconified(WindowEvent e) {}
    @Override public void windowIconified(WindowEvent e) {}
    @Override public void windowOpened(WindowEvent e) {}
    public void activate() { setVisible(true); requestFocus(); }
    public void finish() {}
    public JScrollPane getScrollPane() { return slider; }

    private static class AlignedWidthPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private JScrollPane parentSlider = null;
        @Override
        public Dimension getPreferredSize() {
            int width = parentSlider.getSize().width - parentSlider.getVerticalScrollBar().getWidth() - 5;
            if (width <= 0) width = 1;
            int height = 1; 
            for (Component c : this.getComponents()) {
                height = Math.max(height, c.getY() + c.getHeight());
            }
            return new Dimension (width , height);
        }
        public void setParentSlider(JScrollPane parentSlider) { this.parentSlider = parentSlider; }
    }

    /**
     * Mini-Visualizer component (MiniDock).
     * Now stable and crash-protected.
     */
    public static class MiniDock extends JPanel {
        private static final long serialVersionUID = 1L;
        private final ORUIManager orUIManager;
        private final List<Tile> activeTiles = new ArrayList<>();
        private Color phaseColor = Color.LIGHT_GRAY; 
        private final Map<Tile, Observer> tileObservers = new HashMap<>();
        
        public MiniDock(ORUIManager uiManager) {
            this.orUIManager = uiManager;
            this.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    new RemainingTilesWindow(orUIManager.getORWindow());
                }
            });
            this.setToolTipText("View Remaining Tiles");
            this.setOpaque(true);
            this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        public void addNotify() {
            super.addNotify();
            setupObservers();
        }

        @Override
        public void removeNotify() {
            teardownObservers();
            super.removeNotify();
        }

        private void setupObservers() {
            teardownObservers(); 
            try {
                if (orUIManager == null || orUIManager.getGameUIManager() == null) return;
                RailsRoot root = orUIManager.getGameUIManager().getRoot();
                if (root == null || root.getTileManager() == null) return;

                Observer refreshTrigger = new Observer() {
                    @Override public void update(String text) { SwingUtilities.invokeLater(MiniDock.this::repaint); }
                    @Override public Observable getObservable() { return null; }
                };

                for (Tile t : root.getTileManager().getTiles()) {
                    t.getTilesLaid().addObserver(refreshTrigger);
                    tileObservers.put(t, refreshTrigger);
                }
            } catch (Exception e) {
                // Ignore observer errors to prevent crash
            }
        }

        private void teardownObservers() {
            try {
                for (Map.Entry<Tile, Observer> entry : tileObservers.entrySet()) {
                    entry.getKey().getTilesLaid().removeObserver(entry.getValue());
                }
                tileObservers.clear();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }

        private void updateData() {
            try {
                activeTiles.clear();
                
                if (orUIManager == null || orUIManager.getGameUIManager() == null) return;
                RailsRoot root = orUIManager.getGameUIManager().getRoot();
                if (root == null) return;
                
                TileManager tm = root.getTileManager();
                PhaseManager pm = root.getPhaseManager();
                
                if (tm == null || pm == null || pm.getCurrentPhase() == null) return;

                List<String> allowedColors = pm.getCurrentPhase().getTileColours();
                if (allowedColors == null) return;
                
                phaseColor = Color.LIGHT_GRAY; 
                boolean yellow = false, green = false, brown = false, gray = false;
                for (String s : allowedColors) {
                    String c = s.toLowerCase();
                    if (c.contains("yellow")) yellow = true;
                    else if (c.contains("green")) green = true;
                    else if (c.contains("brown")) brown = true;
                    else if (c.contains("gray") || c.contains("grey")) gray = true;
                }
                if (gray) phaseColor = Color.LIGHT_GRAY;
                else if (brown) phaseColor = new Color(139, 69, 19); 
                else if (green) phaseColor = new Color(0, 180, 0);   
                else if (yellow) phaseColor = Color.YELLOW;         

                for (Tile t : tm.getTiles()) {
                    if (t.isPrepainted() || t.isHidden()) continue;
                    if (t.getFreeCount() <= 0) continue;
                    if (t.getColour() != null) {
                        String tileColor = t.getColour().name(); 
                        boolean match = false;
                        for (String allowed : allowedColors) {
                            if (allowed.equalsIgnoreCase(tileColor)) {
                                match = true;
                                break;
                            }
                        }
                        if (match) activeTiles.add(t);
                    }
                }
                
                Collections.sort(activeTiles, (t1, t2) -> {
                    try {
                        boolean s1 = t1.hasStations();
                        boolean s2 = t2.hasStations();
                        if (s1 != s2) return s1 ? 1 : -1;
                        int tracks1 = t1.getTracks().size();
                        int tracks2 = t2.getTracks().size();
                        if (tracks1 != tracks2) return Integer.compare(tracks1, tracks2);
                        return compareIds(t1.getId(), t2.getId());
                    } catch (Exception e) { return 0; }
                });
            } catch (Exception e) {
                // Log but continue
                log.error("MiniDock updateData failed", e);
            }
        }
        
        private int compareIds(String id1, String id2) {
            try {
                int i1 = Integer.parseInt(id1);
                int i2 = Integer.parseInt(id2);
                return Integer.compare(i1, i2);
            } catch (NumberFormatException e) {
                return id1.compareTo(id2);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            try {
                updateData(); 
                
                super.paintComponent(g);
                
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, getWidth(), getHeight());

                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                if (activeTiles.isEmpty()) {
                    g.setColor(Color.BLACK);
                    g.setFont(new Font("SansSerif", Font.PLAIN, 10));
                    g.drawString("No Tiles", 10, getHeight() / 2 + 3);
                } else {
                    int n = activeTiles.size();
                    int w = getWidth();
                    int h = getHeight();
                    if (w <= 0) w = 1;
                    if (h <= 0) h = 1;
                    int margin = 2; 
                    
                    int bestSize = 10;
                    for (int s = 50; s >= 10; s--) {
                        int cellSpacing = s; 
                        int availW = w - (2 * margin);
                        int availH = h - (2 * margin);
                        if (availW <= 0) availW = 1;
                        if (availH <= 0) availH = 1;
                        int cols = availW / cellSpacing;
                        if (cols < 1) cols = 1;
                        int rows = (int) Math.ceil((double) n / cols);
                        if (rows * cellSpacing <= availH) {
                            bestSize = s;
                            break;
                        }
                    }
                    
                    int hexSize = bestSize;
                    int cols = (w - (2 * margin)) / hexSize;
                    if (cols < 1) cols = 1;
                    int rows = (int) Math.ceil((double) n / cols);
                    int gridW = cols * hexSize;
                    int gridH = rows * hexSize;
                    int xOffset = margin + (w - 2 * margin - gridW) / 2;
                    int yOffset = margin + (h - 2 * margin - gridH) / 2;

                    int col = 0;
                    int row = 0;

                    for (Tile tile : activeTiles) {
                        String picId = tile.getPictureId();
                        BufferedImage img = ImageLoader.getInstance().getTile(picId, 10);
                        if (img != null) {
                            img = rotateImage(img, 30.0);
                            int drawX = xOffset + (col * hexSize);
                            int drawY = yOffset + (row * hexSize);
                            g2.drawImage(img, drawX, drawY, hexSize, hexSize, null);
                        }
                        col++;
                        if (col >= cols) {
                            col = 0;
                            row++;
                        }
                    }
                }

                g2.setColor(phaseColor);
                g2.setStroke(new BasicStroke(3));
                g2.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                
            } catch (Throwable t) {
                // Silent fail to prevent crash
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Color.RED);
                g.drawString("ERR", 5, 20);
            }
        }
    }
}