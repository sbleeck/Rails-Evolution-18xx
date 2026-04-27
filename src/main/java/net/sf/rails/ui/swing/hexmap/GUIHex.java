package net.sf.rails.ui.swing.hexmap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.awt.Font; // Fügt den fehlenden Font-Import hinzu
import java.awt.Shape; // --- FIX: ADD THIS IMPORT ---
import rails.game.action.LayBaseToken;
import rails.game.action.LayBonusToken;
import net.sf.rails.algorithms.RevenueBonusTemplate;
import net.sf.rails.game.BaseToken;
import net.sf.rails.game.BonusToken;
import net.sf.rails.game.HexSide;
import net.sf.rails.game.MapHex;
import net.sf.rails.game.MapOrientation;
import net.sf.rails.game.PrivateCompany;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.Station;
import net.sf.rails.game.Stop;
import net.sf.rails.game.Tile;
import net.sf.rails.game.financial.Bank;
import net.sf.rails.game.state.Observable;
import net.sf.rails.game.state.Observer;
import net.sf.rails.ui.swing.GUIGlobals;
import net.sf.rails.ui.swing.GUIToken;

import com.google.common.collect.Lists;
import java.util.Objects; // Fügt den fehlenden Import hinzu
// ... (existing imports)
import java.awt.font.GlyphVector; // Add import
import java.awt.font.FontRenderContext; // Add import

/**
 * Base class that holds common components for GUIHexes of all orientations.
 */

public class GUIHex implements Observer {

    private String customOverlayText = null;

    /**
     * Static class that describes x-y coordinates for GUIHexes
     */
    public static class HexPoint {
        private final Point2D point;

        public HexPoint(double x, double y) {
            this.point = new Point2D.Double(x, y);
        }

        public HexPoint(Point2D point) {
            this.point = point;
        }

        public Point2D get2D() {
            return point;
        }

        public double getX() {
            return point.getX();
        }

        public double getY() {
            return point.getY();
        }

        @Override
        public String toString() {
            return point.toString();
        }

        public HexPoint rotate(double radians) {
            if (radians == 0)
                return this;
            double x = getX() * Math.cos(radians) + getY() * Math.sin(radians);
            double y = getY() * Math.cos(radians) - getX() * Math.sin(radians);
            return new HexPoint(x, y);
        }

        public HexPoint translate(double x, double y) {
            if (x == 0 && y == 0)
                return this;
            return new HexPoint(this.getX() + x, this.getY() + y);
        }

        public static HexPoint middle(HexPoint a, HexPoint b) {
            return new HexPoint((a.getX() + b.getX()) / 2.0, (a.getY() + b.getY()) / 2.0);
        }

        public static HexPoint add(HexPoint a, HexPoint b) {
            return new HexPoint(a.getX() + b.getX(), a.getY() + b.getY());
        }

        public static HexPoint difference(HexPoint a, HexPoint b) {
            return new HexPoint(a.getX() - b.getX(), a.getY() - b.getY());
        }

    }

    public enum State {

        NORMAL(1.0, Color.black),
        // Phase 1: Build Track (Construction Brown)
        SELECTABLE(0.9, Color.RED),
        // Phase 2: Lay Token (Forest Green - Matching OR Panel)
        TOKEN_SELECTABLE(0.8, Color.RED),
        // Selected Hex (Construction red)
        SELECTED(0.8, Color.GREEN),

        // Merged Purple Highlight (Active Owner)
        HIGHLIGHT_PURPLE(0.8, new Color(128, 0, 128)),
        // Dashed Purple Highlight (Portfolio Owner) - Opaque purple
        HIGHLIGHT_PORTFOLIO(0.8, new Color(128, 0, 128)),

        // Thick Gold/Amber highlight for the connection run destination
        HIGHLIGHT_DESTINATION(0.8, new Color(255, 191, 0)),

        INVALIDS(0.9, Color.pink);

        private final double scale;
        private final Color color;

        State(double scale, Color color) {
            this.scale = scale;
            this.color = color;
        }

        private double getScale() {
            return scale;
        }

        private Color getColor() {
            return color;
        }

        private double getHexDrawScale() {
            return 1 - (1 - scale) / 2;
        }

        private GeneralPath getInnerHexagon(GeneralPath hexagon, HexPoint center) {
            // inner hexagons are drawn outlined (not filled)
            // for this draw, the stroke width is half the scale reduction
            // the scale factor is multiplied by the average of hex width / height in order
            // to get a good estimate for which for stroke width the hex borders are touched
            // by the stroke

            AffineTransform at = AffineTransform.getScaleInstance(getHexDrawScale(), getHexDrawScale());
            GeneralPath innerHexagon = (GeneralPath) hexagon.createTransformedShape(at);

            // Translate innerHexagon to make it concentric.
            Rectangle2D innerBounds = innerHexagon.getBounds2D();
            HexPoint innerCenter = new HexPoint(
                    innerBounds.getX() + innerBounds.getWidth() / 2.0,
                    innerBounds.getY() + innerBounds.getHeight() / 2.0);
            HexPoint difference = HexPoint.difference(center, innerCenter);

            at = AffineTransform.getTranslateInstance(difference.getX(), difference.getY());
            innerHexagon.transform(at);

            return innerHexagon;
        }

        private double getStrokeWidth(GeneralPath hexagon) {
            return (1 - getHexDrawScale()) *
                    (hexagon.getBounds().width + hexagon.getBounds().height) / 2;
        }
    }

    /**
     * Static class for GUIHex Dimensions
     */
    private static class Dimensions {
        private final double zoomFactor;
        private final double tokenDiameter;

        private final Map<HexSide, HexPoint> points;
        private final HexPoint center;

        private final GeneralPath hexagon;

        private final Rectangle rectBound;
        // The area which would have to be repainted if any hex marking is changed
        private final Rectangle marksDirtyRectBound;

        private Dimensions(HexMap hexMap, MapHex hex, double scale, double zoomFactor) {
            this.zoomFactor = zoomFactor;

            double cx = hexMap.calcXCoordinates(hex.getCoordinates().getCol(), hexMap.tileXOffset);
            double cy = hexMap.calcYCoordinates(hex.getCoordinates().getRow(), hexMap.tileYOffset);
            points = hexMap.getMapManager().getMapOrientation().setGUIVertices(cx, cy, scale);

            tokenDiameter = NORMAL_TOKEN_SIZE * zoomFactor;

            hexagon = makePolygon();

            center = HexPoint.middle(points.get(HexSide.defaultRotation()),
                    points.get(HexSide.defaultRotation().opposite()));

            rectBound = hexagon.getBounds();
            marksDirtyRectBound = new Rectangle(
                    rectBound.x - MARKS_DIRTY_MARGIN,
                    rectBound.y - MARKS_DIRTY_MARGIN,
                    rectBound.width + MARKS_DIRTY_MARGIN * 2,
                    rectBound.height + MARKS_DIRTY_MARGIN * 2);
        }

        // Replace with Path2D
        private GeneralPath makePolygon() {
            GeneralPath polygon = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 6);

            HexPoint start = points.get(HexSide.defaultRotation());
            polygon.moveTo((float) start.getX(), (float) start.getY());

            for (HexSide side : HexSide.allExceptDefault()) {
                HexPoint point = points.get(side);
                polygon.lineTo((float) point.getX(), (float) point.getY());
            }
            polygon.closePath();
            return polygon;
        }

    }

    // STATIC CONSTANTS
    private static final int NORMAL_TOKEN_SIZE = 15;
    private static final double TILE_GRID_SCALE = 14.0;
    private static final double CITY_SIZE = 16.0;

    private static final Color BAR_COLOUR = Color.BLUE;
    private static final int BAR_WIDTH = 5;
    private static final Color BORDER_COLOUR = Color.RED;
    private static final int BORDER_WIDTH = 2;

    private static final Color highlightedFillColor = new Color(255, 255, 255, 128);
    private static final Color highlightedBorderColor = Color.BLACK;
    private static final Stroke highlightedBorderStroke = new BasicStroke(3);

    // Defines by how much the hex bounds have to be increased in each direction
    // for obtaining the dirty rectangle (markings could got beyond hex limits)
    private static final int MARKS_DIRTY_MARGIN = 4;

    // positions of offStation Tokens
    private static final int[] offStationTokenX = new int[] { -11, 0 };
    private static final int[] offStationTokenY = new int[] { -19, 0 };

    // static fields
    private final HexMap hexMap;
    private final MapHex hex;

    // dynamic fields
    private Dimensions dimensions;

    private State state;

    private HexUpgrade upgrade;

    private List<HexSide> barSides;
    private List<HexSide> borderSides;
    private List<HexSide> riverSides;

    // A counter instead of a boolean is used here in order to be able to correctly
    // handle racing conditions for mouse events.
    private int highlightCounter = 0;

    public GUIHex(HexMap hexMap, MapHex hex, double scale) {
        this.hexMap = hexMap;
        this.hex = hex;
        hex.addObserver(this);
        this.setDimensions(scale, 1.0);
        this.state = State.NORMAL;
    }

    public void setDimensions(double scale, double zoomFactor) {
        dimensions = new Dimensions(hexMap, hex, scale, zoomFactor);
    }

    // TODO: rename to getModel()
    public MapHex getHex() {
        return this.hex;
    }

    public HexMap getHexMap() {
        return hexMap;
    }

    public Point2D getStopPoint2D(Stop stop) {
        return getTokenCenter(0, stop).get2D();
    }

    public Point2D getSidePoint2D(HexSide side) {
        HexPoint middle = HexPoint.middle(dimensions.points.get(side),
                dimensions.points.get(side.next()));
        return middle.get2D();
    }

    public Point2D getCenterPoint2D() {
        return dimensions.center.get2D();
    }

    // TODO: Make this based on MapHex model
    public void addBar(HexSide side) {
        if (barSides == null) {
            barSides = Lists.newArrayListWithCapacity(4);
        }
        barSides.add(side);
    }

    public void addBorder(HexSide side) {
        if (borderSides == null) {
            borderSides = Lists.newArrayListWithCapacity(4);
        }
        borderSides.add(side);
    }

    public void addRiver(HexSide side) {
        if (riverSides == null) {
            riverSides = Lists.newArrayListWithCapacity(4);
        }
        riverSides.add(side);
    }

    public Rectangle getBounds() {
        return dimensions.rectBound;
    }

    public Rectangle getMarksDirtyBounds() {
        return dimensions.marksDirtyRectBound;
    }

    public boolean contains(Point2D.Double point) {
        return (dimensions.hexagon.contains(point));
    }

    public boolean contains(Point point) {
        return (dimensions.hexagon.contains(point));
    }

    public boolean intersects(Rectangle2D r) {
        return (dimensions.hexagon.intersects(r));
    }

    public void setState(State state) {
        if (this.state != state) {
            // trigger hexmap marks repaint if status changes
            hexMap.repaintMarks(getMarksDirtyBounds());
            hexMap.repaintTiles(getBounds()); // tile is drawn smaller if selected
        }
        this.state = state;
    }

    public State getState() {
        return state;
    }

    /**
     * Indicate that this hex should be highlighted
     */
    public void addHighlightRequest() {
        // trigger hexmap marks repaint if hex becomes highlighted
        if (highlightCounter == 0)
            hexMap.repaintMarks(getMarksDirtyBounds());

        highlightCounter++;
    }

    /**
     * Indicate that this hex does not need to be highlighted any more (from the
     * caller's point of view).
     * Note that the hex could still remain highlighted if another entity has
     * requested
     * highlighting.
     */
    public void removeHighlightRequest() {
        highlightCounter--;
        // trigger hexmap marks repaint if hex becomes not highlighted
        if (highlightCounter == 0)
            hexMap.repaintMarks(getMarksDirtyBounds());
    }

    public boolean isHighlighted() {
        return (highlightCounter > 0);
    }

    public void setUpgrade(HexUpgrade upgrade) {
        this.upgrade = upgrade;
        hexMap.repaintTiles(getBounds());
        hexMap.repaintTokens(getBounds()); // needed if new tile has new token placement spot
    }

    public HexUpgrade getUpgrade() {
        return upgrade;
    }

    /**
     * @return the current tile shown on the map (if an upgrade is shown the upgrade
     *         target tile is returned)
     */
    private Tile getVisibleTile() {
        if (upgrade instanceof TileHexUpgrade) {
            return ((TileHexUpgrade) upgrade).getUpgrade().getTargetTile();
        } else {
            return hex.getCurrentTile();
        }
    }

    /**
     * @return the current tile rotation (if an upgrade is shown the rotation of
     *         that tile is returned)
     */
    private HexSide getVisibleRotation() {
        if (upgrade instanceof TileHexUpgrade) {
            return ((TileHexUpgrade) upgrade).getCurrentRotation();
        } else {
            return hex.getCurrentTileRotation();
        }
    }

    private boolean isTilePainted() {
        Tile visibleTile = getVisibleTile();
        return visibleTile != null && hexMap.isTilePainted(visibleTile);
    }

    public void paintTile(Graphics2D g) {
        if (isTilePainted()) {
            GUIGlobals.setRenderingHints(g);
            paintOverlay(g);
        }
    }

    /**
     * Marks are selected / selectable / highlighted
     * 
     * @param g A graphics 2D object
     */
    public void paintMarks(Graphics2D g) {
        GUIGlobals.setRenderingHints(g);

        if (state != State.NORMAL) {
            Stroke oldStroke = g.getStroke();
            float strokeWidth = (float) state.getStrokeWidth(dimensions.hexagon);
            Shape innerHex = state.getInnerHexagon(dimensions.hexagon, dimensions.center);

            // Intercept TOKEN_SELECTABLE during 1817 Auctions to force the purple style
            boolean is1817AuctionSelection = false;
            if (state == State.TOKEN_SELECTABLE && hexMap != null && hexMap.getOrUIManager() != null && hexMap.getOrUIManager().getGameUIManager() != null) {
                net.sf.rails.game.round.RoundFacade currentRound = hexMap.getOrUIManager().getGameUIManager().getCurrentRound();
                if (currentRound instanceof net.sf.rails.game.specific._1817.AuctionRound_1817) {
                    is1817AuctionSelection = true;
                }
            }
            if (state == State.HIGHLIGHT_PORTFOLIO) {
                // 1. Force-clear the stroke path to erase the stale white halo
                // from the persistent MarksLayer buffer.
                java.awt.Composite oldComp = g.getComposite();
                g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.CLEAR));
                g.setStroke(new BasicStroke(strokeWidth + 4.0f));
                g.draw(innerHex);
                g.setComposite(oldComp);

                // 2. Draw strictly dashes. The gaps will now punch through to the map
                // background.
                float[] dashPattern = { 10.0f, 10.0f };
                g.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f,
                        dashPattern, 0.0f));
                g.setColor(state.getColor());
                g.draw(innerHex);
            } else if (state == State.HIGHLIGHT_DESTINATION) {
                // Destination: Thick solid Gold/Amber border
                g.setStroke(new BasicStroke(strokeWidth + 4.0f));
                g.setColor(State.HIGHLIGHT_DESTINATION.getColor());
                g.draw(innerHex);
            } else if (state == State.HIGHLIGHT_PURPLE || is1817AuctionSelection) {
                // Active company: Solid white halo first, then solid purple
                g.setStroke(new BasicStroke(strokeWidth + 3.0f));
                g.setColor(Color.WHITE);
                g.draw(innerHex);

                g.setStroke(new BasicStroke(strokeWidth));
g.setColor(State.HIGHLIGHT_PURPLE.getColor());
                g.draw(innerHex);
            } else {
                g.setStroke(new BasicStroke(strokeWidth));
                g.setColor(state.getColor());
                g.draw(innerHex);
            }
            g.setStroke(oldStroke);
        }

        // highlight on top of tiles
        if (isHighlighted()) {
            g.setColor(highlightedFillColor);
            g.fill(dimensions.hexagon);
            Stroke oldStroke = g.getStroke();
            g.setStroke(highlightedBorderStroke);
            g.setColor(highlightedBorderColor);
            g.draw(dimensions.hexagon);
            g.setStroke(oldStroke);
        }

    }

    public void paintTokensAndText(Graphics2D g) {

        GUIGlobals.setRenderingHints(g);

        try {
            paintStationTokens(g);
            paintOffStationTokens(g);
// 1. Check the toggle state
            boolean displayMarkings = true;
            if (hexMap.getOrUIManager() != null) {
                displayMarkings = hexMap.getOrUIManager().isShowMapMarkings();
            }

            // 2. Draw costs in black, but only if the toggle is ON and no tile has been laid
            if (displayMarkings && getHex().getTileCost() > 0 && getHex().isPreprintedTileCurrent()) {
                Font oldFont = g.getFont();
                // Bumped minimum size to 10 for better legibility at extreme small scales
                int scaledFontSize = Math.max(10, (int) Math.round(12 * dimensions.zoomFactor));
                g.setFont(new Font("SansSerif", Font.PLAIN, scaledFontSize));
                FontMetrics fontMetrics = g.getFontMetrics();
                Color oldColor = g.getColor();
                g.setColor(Color.BLACK);

                String costString = Bank.format(getHex(), getHex().getTileCost());
                int textWidth = fontMetrics.stringWidth(costString);
                int textX = dimensions.rectBound.x + (dimensions.rectBound.width - textWidth) * 3 / 5;
                int textY = dimensions.rectBound.y + ((fontMetrics.getHeight() + dimensions.rectBound.height) * 9 / 15);
                
                g.drawString(costString, textX, textY);

                // Draw terrain symbols based on the XML attribute
                String terrain = getHex().getTerrain();
                if (terrain != null) {
                    Stroke oldStroke = g.getStroke();
                    g.setStroke(new BasicStroke(1.5f * (float)dimensions.zoomFactor, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    
                    double cx = textX + textWidth / 2.0;
                    double cy = textY - fontMetrics.getAscent() - (4 * dimensions.zoomFactor); // Position above text
                    
                    if (terrain.equalsIgnoreCase("river")) {
                        g.setColor(new Color(30, 144, 255)); // Standard river blue
                        double w = 8 * dimensions.zoomFactor;
                        double h = 2.5 * dimensions.zoomFactor;
                        double gap = 4 * dimensions.zoomFactor;
                        
                        for (int i = 0; i < 2; i++) {
                            double yOffset = cy + (i * gap);
                            java.awt.geom.Path2D.Double wave = new java.awt.geom.Path2D.Double();
                            wave.moveTo(cx - w, yOffset);
                            wave.curveTo(cx - w/2, yOffset - h, cx + w/2, yOffset + h, cx + w, yOffset);
                            g.draw(wave);
                        }
                    } else if (terrain.equalsIgnoreCase("mountain") || terrain.equalsIgnoreCase("hill") || terrain.equalsIgnoreCase("hills")) {
                        g.setColor(new Color(139, 69, 19)); // Mountain brown
                        double w = 6 * dimensions.zoomFactor;
                        double h = 8 * dimensions.zoomFactor;
                        
                        java.awt.geom.Path2D.Double mountain = new java.awt.geom.Path2D.Double();
                        // Draw two jagged peaks
                        mountain.moveTo(cx - w - w/2, cy + h/2);
                        mountain.lineTo(cx - w/2, cy - h/2);
                        mountain.lineTo(cx, cy + h/2);
                        mountain.lineTo(cx + w, cy - h/2 + 2*dimensions.zoomFactor);
                        mountain.lineTo(cx + w + w/2, cy + h/2);
                        g.draw(mountain);
                    }
                    g.setStroke(oldStroke);
                }
                g.setColor(oldColor);
                g.setFont(oldFont);
            }

            if (!isTilePainted())
                return;

            // Force label drawing strictly for SVG/preprinted tiles to bypass the GUITile skip
            Tile visibleTile = getVisibleTile();
            if (visibleTile != null && visibleTile.isPrepainted()) {
                String hexLabel = getHex().getLabel();
                if (hexLabel != null && !hexLabel.isEmpty()) {
                    Font oldFontLabel = g.getFont();
                    int scaledLabelSize = Math.max(10, (int) Math.round(14 * dimensions.zoomFactor));
                    g.setFont(new Font("SansSerif", Font.BOLD, scaledLabelSize));
                    
                    FontMetrics fontMetrics = g.getFontMetrics();
                    g.setColor(Color.BLACK);
                    // Scale the offsets so the text doesn't drift outside the hex
                    g.drawString(hexLabel, 
                            dimensions.rectBound.x + (int)(15 * dimensions.zoomFactor), 
                            dimensions.rectBound.y + fontMetrics.getHeight() + (int)(5 * dimensions.zoomFactor));
                    g.setFont(oldFontLabel);
                }
            }
            
            Map<PublicCompany, Stop> homes = getHex().getHomes();

            if (homes != null) {
                for (PublicCompany company : homes.keySet()) {


                    // 1. Permanent Coal Value Rendering (Independent of company status)
                    boolean isCoal = company.getType() != null && "Coal".equals(company.getType().getId());
                    if (isCoal) {
                        Stop homeCity = homes.get(company);
                        if (homeCity != null && homeCity.getRelatedStation() == null) {
                            for (Stop stop : getHex().getStops()) {
                                if (stop.hasTokenSlotsLeft()) {
                                    homeCity = stop;
                                    break;
                                }
                            }
                        }

                        int val = 0;
                        try {
                            if (getHex().hasValuesPerPhase()) {
                                val = getHex().getCurrentValueForPhase(hexMap.getPhase());
                            }
                            if (val == 0 && homeCity != null && homeCity.getRelatedStation() != null) {
                                val = homeCity.getRelatedStation().getValue();
                            }
                            if (val == 0) {
                                net.sf.rails.game.PrivateCompany pc = hexMap.getMapManager().getRoot().getCompanyManager().getPrivateCompany(company.getId());
                                if (pc != null) {
                                    java.util.List<Integer> revList = pc.getRevenue();
                                    if (revList != null && !revList.isEmpty()) {
                                        String phaseName = (hexMap.getPhase() != null) ? hexMap.getPhase().getId() : "null";
                                        if (revList.size() > 1 && !phaseName.equals("null") && phaseName.compareTo("5") >= 0) {
                                            val = revList.get(1);
                                        } else {
                                            val = revList.get(0);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {}

                        if (val > 0 && homeCity != null) {
                            HexPoint origin = getTokenCenter(1, homeCity); 
                            drawCoalMineValue(g, company.getId(), origin, val);
                        }
                    }

                    if (company.isClosed())
                        continue;


                    // Only draw the company name if there isn't yet a token of that company
                    if (hex.hasTokenOfCompany(company))
                        continue;
                    // Do not draw if hex is never blocked for token lays
                    if (hex.getBlockedForTokenLays() == MapHex.BlockedToken.NEVER)
                        continue;

                    Stop homeCity = homes.get(company);
                    if (homeCity.getRelatedStation() == null) { // not yet decided where the token will be
                        // find a free slot
                        Set<Stop> stops = getHex().getStops();
                        for (Stop stop : stops) {
                            if (stop.hasTokenSlotsLeft()) {
                                homeCity = stop;
                                break;
                            }
                        }
                    }

                    if (homeCity != null) {
                        // check the number of tokens laid there already
                        HexPoint p = getTokenCenter(1, homeCity);
                        if (company.isDisplayHomeHex()) {
drawHome(g, company, p, homeCity);
                        }
                    }
                }
            }

        } catch (java.util.ConcurrentModificationException e) {
            // Abort painting for this frame if the map model is being updated concurrently
            // by the main thread during a game load
            return;
        }

        // Fügt die Logik zum Zeichnen des Custom Overlay Textes hinzu, falls vorhanden
        if (customOverlayText != null) {
            drawString(g, customOverlayText, 0, 0); // Zeichnet den Text zentriert
        }

        // If an active owner label is set, draw it using standard text
        // (The purple BORDER is handled by paintMarks via the State enum now)
        if (activeOwnerLabel != null) {
            drawString(g, activeOwnerLabel, 0, 0);
        }
drawDestinationMilestones(g);
    }

    public void setCustomOverlayText(String text) {
        String safeText = (text == null) ? "" : text;

        if (!Objects.equals(this.customOverlayText, safeText)) {
            this.customOverlayText = safeText;
            if (this.hexMap != null) {
                // FÜR DAS FINALE FIX: Wir rufen die spezifische Methode für die
                // TokensTextsLayer auf (TokensLayer)
                // Die Methode repaintTokens löst ein repaint der TokensTextsLayer aus.
                this.hexMap.repaintTokens(this.getBounds());
            }
        }
    }

    public String getCustomOverlayText() {
        return this.customOverlayText;
    }

    private void paintOverlay(Graphics2D g2) {
        Tile visibleTile = this.getVisibleTile();
        HexSide visibleRotation = this.getVisibleRotation();

        GUITile.paintTile(g2, dimensions.center, this, visibleTile, visibleRotation,
                state.getScale(), hexMap.getZoomStep());
    }

    public void paintBars(Graphics2D g) {
        if (barSides != null) {
            for (HexSide startPoint : barSides) {
                drawBar(g, dimensions.points.get(startPoint), dimensions.points.get(startPoint.next()));
            }
        }
        if (borderSides != null) {
            for (HexSide startPoint : borderSides) {
                drawBorder(g, dimensions.points.get(startPoint), dimensions.points.get(startPoint.next()));
            }
        }



// The Mississippi River uses a custom validator.
        try {
            Class<?> validatorClass = Class.forName("net.sf.rails.game.specific._1870.MississippiRiverValidator");
            java.lang.reflect.Field riverHexesField = validatorClass.getDeclaredField("RIVER_HEXES");
            riverHexesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> riverHexes = (Map<String, Object>) riverHexesField.get(null);
            
            if (riverHexes.containsKey(getHex().getId())) {
                List<Integer> flowEdges = new ArrayList<>();
                String currentId = getHex().getId();
                char rowChar = currentId.charAt(0);
                int col = Integer.parseInt(currentId.substring(1));
                int row = rowChar - 'A';
                
                // Logic: Check all 6 standard 18xx grid neighbors. 
                // If the neighbor is also a river hex, the river must cross that edge midpoint!
                for (int i = 0; i < 6; i++) {
                    int r = row;
                    int c = col;
                    switch(i) {
                        case 0: r++; c--; break; // SW
                        case 1: c-=2; break;     // W
                        case 2: r--; c--; break; // NW
                        case 3: r--; c++; break; // NE
                        case 4: c+=2; break;     // E
                        case 5: r++; c++; break; // SE
                    }
                    String neighborId = "" + (char)('A' + r) + c;
                    if (riverHexes.containsKey(neighborId)) {
                        flowEdges.add(i);
                    }
                }
                
                // If it's an end-cap (like A16), it enters from off-map (opposite side)
                if (flowEdges.size() == 1) {
                    flowEdges.add((flowEdges.get(0) + 3) % 6);
                }

                List<Shape> shapes = new ArrayList<>();
                Point2D center = getCenterPoint2D();
                
                if (flowEdges.size() == 2) {
                    Point2D p1 = getSidePoint2D(HexSide.get(flowEdges.get(0)));
                    Point2D p2 = getSidePoint2D(HexSide.get(flowEdges.get(1)));
                    // The center control point perfectly dictates the 0, 60, or -60 degree turns
                    shapes.add(new java.awt.geom.QuadCurve2D.Double(p1.getX(), p1.getY(), center.getX(), center.getY(), p2.getX(), p2.getY()));
                } else if (flowEdges.size() == 3) {
                    // Logic for branching rivers (e.g. at J15): Find the 'trunk' incoming edge
                    int trunk = 0;
                    for (int i=0; i<3; i++) {
                        int e1 = flowEdges.get(i);
                        int e2 = flowEdges.get((i+1)%3);
                        int e3 = flowEdges.get((i+2)%3);
                        int d1 = Math.min(Math.abs(e1-e2), 6-Math.abs(e1-e2));
                        int d2 = Math.min(Math.abs(e1-e3), 6-Math.abs(e1-e3));
                        if (d1 + d2 >= 4) { trunk = i; }
                    }
                    Point2D pTrunk = getSidePoint2D(HexSide.get(flowEdges.get(trunk)));
                    Point2D pB1 = getSidePoint2D(HexSide.get(flowEdges.get((trunk+1)%3)));
                    Point2D pB2 = getSidePoint2D(HexSide.get(flowEdges.get((trunk+2)%3)));
                    shapes.add(new java.awt.geom.QuadCurve2D.Double(pTrunk.getX(), pTrunk.getY(), center.getX(), center.getY(), pB1.getX(), pB1.getY()));
                    shapes.add(new java.awt.geom.QuadCurve2D.Double(pTrunk.getX(), pTrunk.getY(), center.getX(), center.getY(), pB2.getX(), pB2.getY()));
                }

                if (!shapes.isEmpty()) {
                    Color oldColor = g.getColor();
                    Stroke oldStroke = g.getStroke();
                    
                    g.setColor(new Color(64, 164, 223, 160)); 
                    g.setStroke(new BasicStroke(16.0f * (float)dimensions.zoomFactor, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    for(Shape s : shapes) g.draw(s);
                    
                    g.setColor(new Color(20, 100, 180, 200));
                    float dash = 12.0f * (float)dimensions.zoomFactor;
                    float gap = 20.0f * (float)dimensions.zoomFactor;
                    g.setStroke(new BasicStroke(2.0f * (float)dimensions.zoomFactor, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{dash, gap}, 0));
                    for(Shape s : shapes) g.draw(s);
                    
                    g.setColor(oldColor);
                    g.setStroke(oldStroke);
                }
            }
        } catch (Exception e) {
            // Fallback for non-1870 maps
            net.sf.rails.game.HexSidesSet rivers = getHex().getRiverSides();
            if (rivers != null) {
                java.util.Iterator<HexSide> it = rivers.iterator();
                if (it.hasNext()) {
                    Point2D p1 = getSidePoint2D(it.next());
                    if (it.hasNext()) {
                        Point2D p2 = getSidePoint2D(it.next());
                        Point2D center = getCenterPoint2D();
                        
                        Shape riverShape = new java.awt.geom.QuadCurve2D.Double(p1.getX(), p1.getY(), center.getX(), center.getY(), p2.getX(), p2.getY());
                        
                        Color oldColor = g.getColor();
                        Stroke oldStroke = g.getStroke();
                        g.setColor(new Color(64, 164, 223, 160));
                        g.setStroke(new BasicStroke(16.0f * (float)dimensions.zoomFactor, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.draw(riverShape);
                        
                        g.setColor(new Color(20, 100, 180, 200));
                        float dash = 12.0f * (float)dimensions.zoomFactor;
                        float gap = 20.0f * (float)dimensions.zoomFactor;
                        g.setStroke(new BasicStroke(2.0f * (float)dimensions.zoomFactor, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{dash, gap}, 0));
                        g.draw(riverShape);
                        
                        g.setColor(oldColor);
                        g.setStroke(oldStroke);
                    }
                }
            }
        }


        
      
      
        if (riverSides != null) {
            for (HexSide startPoint : riverSides) {
                drawRiver(g, dimensions.points.get(startPoint), dimensions.points.get(startPoint.next()));
            }
        }
    }

    protected void drawBar(Graphics2D g2d, HexPoint start, HexPoint end) {
        Color oldColor = g2d.getColor();
        Stroke oldStroke = g2d.getStroke();

        g2d.setColor(BAR_COLOUR);
        g2d.setStroke(new BasicStroke(BAR_WIDTH));
        g2d.draw(new Line2D.Double(start.get2D(), end.get2D()));

        g2d.setColor(oldColor);
        g2d.setStroke(oldStroke);
    }

    protected void drawBorder(Graphics2D g2d, HexPoint start, HexPoint end) {
        Color oldColor = g2d.getColor();
        Stroke oldStroke = g2d.getStroke();

        g2d.setColor(BORDER_COLOUR);
        g2d.setStroke(new BasicStroke(BORDER_WIDTH));
        g2d.draw(new Line2D.Double(start.get2D(), end.get2D()));

        g2d.setColor(oldColor);
        g2d.setStroke(oldStroke);
    }

    protected void drawRiver(Graphics2D g2d, HexPoint start, HexPoint end) {
        Color oldColor = g2d.getColor();
        Stroke oldStroke = g2d.getStroke();

        g2d.setColor(new Color(64, 164, 223, 180)); // Translucent river blue
        g2d.setStroke(new BasicStroke(12.0f * (float)dimensions.zoomFactor, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.draw(new Line2D.Double(start.get2D(), end.get2D()));

        g2d.setColor(oldColor);
        g2d.setStroke(oldStroke);
    }

private void paintStationTokens(Graphics2D g2) {
        for (Stop stop : getHex().getStops()) {
            int j = 0;
            for (BaseToken token : stop.getBaseTokens()) {
                HexPoint origin = getTokenCenter(j++, stop);
                PublicCompany company = token.getParent();
                drawBaseToken(g2, company, origin, dimensions.tokenDiameter);
            }
// --- START FIX ---
            // check for temporary token
            if (upgrade instanceof TokenHexUpgrade && ((TokenHexUpgrade) upgrade).getAction() instanceof LayBaseToken) {
                TokenHexUpgrade tokenUpgrade = (TokenHexUpgrade) upgrade;
                if (tokenUpgrade.getSelectedStop() == stop) {
                    PublicCompany company = tokenUpgrade.getAction().getCompany();
                    if (!stop.hasTokenOf(company)) {
                        HexPoint origin = getTokenCenter(j++, stop);
                        drawBaseToken(g2, company, origin, dimensions.tokenDiameter);
                    }
                }
            }
// --- END FIX ---
        }
    }

    // FIXME: Where to paint more than one offStationTokens?
    private void paintOffStationTokens(Graphics2D g2) {
        int i = 0;
        for (BonusToken token : hex.getBonusTokens()) {
            HexPoint origin = dimensions.center.translate(offStationTokenX[i], offStationTokenY[i]);
            drawBonusToken(g2, token, origin);
            if (++i > 1)
                return;

        }
        // check for temporary token
        if (upgrade instanceof TokenHexUpgrade && ((TokenHexUpgrade) upgrade).getAction() instanceof LayBonusToken) {
            HexPoint origin = dimensions.center.translate(offStationTokenX[i], offStationTokenY[i]);
            BonusToken token = ((LayBonusToken) ((TokenHexUpgrade) upgrade).getAction()).getToken();
            drawBonusToken(g2, token, origin);
        }
    }


private void drawBaseToken(Graphics2D g2, PublicCompany co, HexPoint center, double diameter) {

        GUIToken token = new GUIToken(
                co.getFgColour(), co.getBgColour(), co.getId(), center, diameter);
        // token.setBounds((int)Math.round(dimensions.center.getX()-0.5*diameter), (int)
        // Math.round(dimensions.center.getY()-0.5*diameter),
        // diameter, diameter);

        token.drawToken(g2);

    }

   private void drawHome(Graphics2D g2, PublicCompany co, HexPoint origin, Stop homeCity) {
        // 1. Branch logic: Check if this is a Major, National, or Coal company
        boolean isMajor = false;
        boolean isCoal = false;
        if (co.getType() != null) {
            String typeName = co.getType().getId();
            if (typeName != null) {
                if (typeName.equals("Major") || typeName.equals("National")) {
                    isMajor = true;
                } else if (typeName.equals("Coal")) {
                    isCoal = true;
                }
            }
        }

        // 2. Keep Minors and Coal companies completely intact for their main text
        if (!isMajor) {
            GUIToken.drawTokenText(co.getId(), g2, Color.BLACK, origin, dimensions.tokenDiameter);

            return;
        }

        // 3. Draw the "empty ring" style for non-active Majors
        double diameter = dimensions.tokenDiameter;
        double radius = diameter / 2.0;
        double x = origin.getX() - radius;
        double y = origin.getY() - radius;

        Color oldColor = g2.getColor();
        Stroke oldStroke = g2.getStroke();
        Font oldFont = g2.getFont();

        java.awt.geom.Ellipse2D.Double circle = new java.awt.geom.Ellipse2D.Double(x, y, diameter, diameter);

        // Black border (outer outline)
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(4.0f));
        g2.draw(circle);

        // Company colored ring inside the black border
        g2.setColor(co.getBgColour());
        g2.setStroke(new BasicStroke(2.0f));
        g2.draw(circle);

        // Setup Text
        g2.setFont(new Font("SansSerif", Font.BOLD, (int) (diameter * 0.45)));
        FontMetrics fm = g2.getFontMetrics();
        String text = co.getId();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getAscent();

        float textX = (float) (origin.getX() - textWidth / 2.0);
        float textY = (float) (origin.getY() + textHeight / 2.0 - fm.getDescent());

        // Draw solid white background box for text (cuts through the ring)
        int padding = 2;
        java.awt.geom.Rectangle2D.Double textBox = new java.awt.geom.Rectangle2D.Double(textX - padding,
                textY - textHeight, textWidth + padding * 2, textHeight + fm.getDescent());
        g2.setColor(Color.WHITE);
        g2.fill(textBox);

        // Draw core text
        g2.setColor(Color.BLACK);
        g2.drawString(text, textX, textY);

        g2.setColor(oldColor);
        g2.setStroke(oldStroke);
        g2.setFont(oldFont);
    }

    private void drawCoalMineValue(Graphics2D g2, String compId, HexPoint origin, int value) {
        double zoom = dimensions.zoomFactor;
        double xOffset = 0;
        double yOffset = 0;
        
        switch (compId) {
            case "RGTE":
            case "EOD":
            case "EKT":
            case "MLB":
                yOffset = -18 * zoom;
                break;
            case "EPP":
            case "ZKB":
            case "SPB":
            case "LRB":
            case "BB":
                yOffset = 18 * zoom;
                break;
            case "EHS":
                xOffset = -22 * zoom;
                break;
            default:
                yOffset = 18 * zoom;
                break;
        }
        
        double centerX = origin.getX() + xOffset;
        double centerY = origin.getY() + yOffset;
        
        String text = String.valueOf(value);
        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();
        Stroke oldStroke = g2.getStroke();
        
        int fontSize = (int) Math.round(11 * zoom);
        if (fontSize < 8) fontSize = 8;
        g2.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        
        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getAscent();
        
        int boxWidth = Math.max((int)(16 * zoom), textWidth + (int)(6 * zoom));
        int boxHeight = textHeight + (int)(4 * zoom);
        
        double boxX = centerX - boxWidth / 2.0;
        double boxY = centerY - boxHeight / 2.0;
        
        // Draw black square
        g2.setColor(Color.BLACK);
        g2.fill(new java.awt.geom.Rectangle2D.Double(boxX, boxY, boxWidth, boxHeight));
        
        // Draw white border
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(1.0f));
        g2.draw(new java.awt.geom.Rectangle2D.Double(boxX, boxY, boxWidth, boxHeight));
        
        // Draw text
        float textX = (float) (centerX - textWidth / 2.0);
        float textY = (float) (centerY + textHeight / 2.0 - fm.getDescent());
        g2.drawString(text, textX, textY);
        
        g2.setFont(oldFont);
        g2.setColor(oldColor);
        g2.setStroke(oldStroke);
    }

    private void drawBonusToken(Graphics2D g2, BonusToken bt, HexPoint origin) {
        GUIToken token = new GUIToken(Color.BLACK, Color.WHITE, "+" + bt.getValue(),
                origin, 15);
        token.drawToken(g2);
    }

    private HexPoint getTokenCenter(int currentToken, Stop stop) {
        // Find the correct position on the tile
        int positionCode = stop.getRelatedStation().getPosition();

        HexPoint tokenCenter;
        if (positionCode != 0) {

            

            double initial = TILE_GRID_SCALE * dimensions.zoomFactor;

            double r = MapOrientation.DEG30 * (positionCode / (double) 50);
            tokenCenter = new HexPoint(0, initial).rotate(r);
        } else {
            tokenCenter = new HexPoint(0.0, 0.0);
        }

        // Correct for the number of base slots and the token number
        double delta_x = 0, delta_y = 0;
        switch (stop.getSlots()) {
            case 2:
            int curveHint = Math.abs(positionCode) % 10;
                if (curveHint >= 6 && curveHint <= 9) {
                    // Use trigonometry to split the tokens diagonally. 
                    double splitAngle = Math.toRadians(120.0);
                    double offset = (-0.5 + currentToken) * CITY_SIZE * dimensions.zoomFactor;
                    delta_x = offset * Math.cos(splitAngle);
                    delta_y = offset * Math.sin(splitAngle);
                } else {
                    delta_x = (-0.5 + currentToken) * CITY_SIZE * dimensions.zoomFactor;
                }

                break;

            case 3:
                if (currentToken < 2) {
                    delta_x = (-0.5 + currentToken) * CITY_SIZE * dimensions.zoomFactor;
                    delta_y = -3 + 0.25 * MapOrientation.SQRT3 * CITY_SIZE * dimensions.zoomFactor;
                } else {
                    delta_y = -(3 + 0.5 * CITY_SIZE * dimensions.zoomFactor);
                }
                break;
            case 4:
                delta_x = (-0.5 + currentToken % 2) * CITY_SIZE * dimensions.zoomFactor;
                delta_y = (0.5 - currentToken / (double) 2) * CITY_SIZE * dimensions.zoomFactor;
                break;
            case 6:
                switch (currentToken) {
                    case 0:
                        delta_x += (-1) * CITY_SIZE * dimensions.zoomFactor;
                        delta_y += (-0.5) * CITY_SIZE * dimensions.zoomFactor;
                        break;
                    case 1:
                        delta_x += (-1) * CITY_SIZE * dimensions.zoomFactor;
                        delta_y += (0.5) * CITY_SIZE * dimensions.zoomFactor;
                        break;
                    case 2:
                        delta_y += (1) * CITY_SIZE * dimensions.zoomFactor;
                        break;
                    case 3:
                        delta_x += (1) * CITY_SIZE * dimensions.zoomFactor;
                        delta_y += (0.5) * CITY_SIZE * dimensions.zoomFactor;
                        break;
                    case 4:
                        delta_x += (1) * CITY_SIZE * dimensions.zoomFactor;
                        delta_y += (-0.5) * CITY_SIZE * dimensions.zoomFactor;
                        break;
                    case 5:
                        delta_y += (-1) * CITY_SIZE * dimensions.zoomFactor;
                        break;
                }

        }
        tokenCenter = tokenCenter.translate(delta_x, delta_y);

        // Correct for the tile base and actual rotations
        HexSide rotation = hex.getCurrentTileRotation();

        double radians = MapOrientation.rotationInRadians(hex, rotation);
        tokenCenter = tokenCenter.rotate(radians);

        tokenCenter = dimensions.center.translate(tokenCenter.getX(), -tokenCenter.getY());
        return tokenCenter;
    }

    public String getToolTip() {
        return null;
        // if (upgrade != null)
        //     return upgrade.getUpgradeToolTip();
        // else
        //     return getDefaultToolTip();
    }

    private String bonusToolTipText(List<RevenueBonusTemplate> bonuses) {
        StringBuilder tt = new StringBuilder();
        if (bonuses != null) {
            Set<String> bonusNames = new HashSet<>();
            for (RevenueBonusTemplate bonus : bonuses) {
                if (bonus.getName() == null) {
                    tt.append("<br>Bonus:");
                    tt.append(bonus.getToolTip());
                } else if (!bonusNames.contains(bonus.getName())) {
                    tt.append("<br>Bonus:").append(bonus.getName());
                    bonusNames.add(bonus.getName());
                }
            }
        }
        return tt.toString();
    }

    private String getDefaultToolTip() {
        Tile currentTile = hex.getCurrentTile();

        StringBuilder tt = new StringBuilder("<html>");
        tt.append("<b>Hex</b>: ").append(hex.toText());
        // For debugging: display x,y-coordinates
        // tt.append("<small> x=" + x + " y="+y+"</small>");

        tt.append("<br><b>Tile</b>: ").append(currentTile.toText());

        // For debugging: display rotation
        // tt.append("<small> rot=" + currentTileOrientation + "</small>");

        if (hex.hasValuesPerPhase()) {
            tt.append("<br>Value ");
            tt.append(hex.getCurrentValueForPhase(hexMap.getPhase())).append(" [");
            List<Integer> values = hex.getValuesPerPhase();
            for (int i = 0; i < values.size(); i++) {
                if (i > 0)
                    tt.append(",");
                tt.append(values.get(i));
            }
            tt.append("]");
        } else if (currentTile.hasStations()) {
            for (Stop stop : hex.getStops()) {
                Station station = stop.getRelatedStation();
                tt.append("<br>  ").append(station.toText())
                        .append(" (").append(hex.getConnectionString(station))
                        .append("): value ");
                tt.append(station.getValue());
                if (station.getBaseSlots() > 0) {
                    tt.append(", ").append(station.getBaseSlots()).append(" slots");
                    Set<BaseToken> tokens = stop.getBaseTokens();
                    if (tokens.size() > 0) {
                        tt.append(" (");
                        int oldsize = tt.length();
                        for (BaseToken token : tokens) {
                            if (tt.length() > oldsize)
                                tt.append(",");
                            tt.append(token.getParent().getId());
                        }
                        tt.append(")");
                    }
                }
                // TEMPORARY
                tt.append(" <small>pos=").append(station.getPosition()).append("</small>");
            }
            tt.append(bonusToolTipText(currentTile.getRevenueBonuses()));
        }

        // revenueBonuses
        tt.append(bonusToolTipText(hex.getRevenueBonuses()));

        String upgrades = currentTile.getUpgradesString(hex);
        if (upgrades.equals("")) {
            tt.append("<br>No upgrades");
        } else {
            tt.append("<br><b>Upgrades</b>: ").append(upgrades);
            if (hex.getTileCost() > 0)
                tt.append("<br>Upgrade cost: ").append(Bank.format(hex, hex.getTileCost()));
        }

        if (getHex().getDestinations() != null) {
            tt.append("<br><b>Destination</b>:");
            for (PublicCompany dest : getHex().getDestinations()) {
                tt.append(" ");
                tt.append(dest.getId());
            }
        }

        tt.append("</html>");
        return tt.toString();
    }

    public void update() {
        hexMap.repaintTiles(getBounds());
        hexMap.repaintTokens(getBounds()); // needed if new tile has new token placement spot
    }

    public String toText() {
        return hex.toText();
    }

    // Observer methods
    @Override
    public void update(String text) {
        update();
    }

    @Override
    public Observable getObservable() {
        return hex;
    }

    // Object methods
    @Override
    public String toString() {
        return toText() + " (" + hex.getCurrentTile().toText() + ")";
    }

    /**
     * Draw a string anywhere on a hex.
     * Modified to handle "High Contrast Overlays" in the top-left corner,
     * while keeping standard text (Private Companies) centered.
     */
    private void drawString(Graphics2D g, String text, int x, int y) {
        if (text == null)
            return;

        String rawText = text;
        Font originalFont = g.getFont();
        Color originalColor = g.getColor();
        boolean restore = false;

        // Detect if this is our "Special Overlay" (passed as HTML)
        boolean isOverlay = text.startsWith("<html>");

        if (isOverlay) {
            // Support multi-line text: Replace <br> with newline BEFORE stripping other
            // tags
            rawText = text.replaceAll("(?i)<br\\s*/?>", "\n");
            // Strip remaining HTML tags
            rawText = rawText.replaceAll("<[^>]*>", "");

// 1. SET FONT: Bold and scaled based on zoomFactor for better fit
            int scaledFontSize = Math.max(8, (int) Math.round(16 * dimensions.zoomFactor));
            Font overlayFont = new Font("SansSerif", Font.BOLD, scaledFontSize);
            g.setFont(overlayFont);
            restore = true;
        }

        FontMetrics fontMetrics = g.getFontMetrics();
        int x_final;
        int y_final;

        if (isOverlay) {
            // --- STRATEGY: MEME TEXT (Top Left) ---

            // Calculate Top-Left Position (Safe zone inside the hex)
            // x: Start at hex x + 20% of width
            // y: Start at hex y + 30% of height (clears the top corner)
            x_final = dimensions.rectBound.x + (int) (dimensions.rectBound.width * 0.20);
            y_final = dimensions.rectBound.y + (int) (dimensions.rectBound.height * 0.30);

            // Split into lines (handling N12 and Cost on separate lines)
            String[] lines = rawText.split("\n");
            int lineHeight = fontMetrics.getHeight();

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int y_pos = y_final + (i * lineHeight); // Shift down for second line
                // Apply different styles for ID vs Price
                if (i == 0) {
                    // Line 0 (ID): White Text with Black Outline (High Contrast)
                    g.setColor(Color.BLACK);
                    g.drawString(line, x_final - 1, y_pos - 1);
                    g.drawString(line, x_final - 1, y_pos + 1);
                    g.drawString(line, x_final + 1, y_pos - 1);
                    g.drawString(line, x_final + 1, y_pos + 1);

                    g.setColor(Color.WHITE);
                    g.drawString(line, x_final, y_pos);
                } else {
                    // Line 1+ (Price): Black Text with White Halo (Standard Map Style)
                    // 1. Draw Halo (Stroke) in WHITE
                    g.setColor(Color.WHITE);
                    g.drawString(line, x_final - 1, y_pos - 1);
                    g.drawString(line, x_final - 1, y_pos + 1);
                    g.drawString(line, x_final + 1, y_pos - 1);
                    g.drawString(line, x_final + 1, y_pos + 1);

                    // 2. Draw Text (Fill) in BLACK
                    g.setColor(Color.BLACK);
                    g.drawString(line, x_final, y_pos);
                }

            }
        } else {
            // --- STRATEGY: STANDARD TEXT (Centered) ---
            // This preserves logic for Private Companies (e.g., "(C&A)"), Reserved Hexes,
            // etc.

            // 1. Horizontal Center
            x_final = dimensions.rectBound.x + x + (dimensions.rectBound.width - fontMetrics.stringWidth(rawText)) / 2;

            // 2. Vertical Center
            int y_center = dimensions.rectBound.y + dimensions.rectBound.height / 2;
            int y_height = fontMetrics.getHeight();
            // Shift down slightly to align baseline
            y_final = y_center + y + (y_height / 3);

            // Draw standard text (uses whatever color was set by the caller)
            g.drawString(rawText, x_final, y_final);
        }

        // Restore original settings so we don't break other drawing operations
        if (restore) {
            g.setFont(originalFont);
            g.setColor(originalColor);
        }
    }
    // ... (inside GUIHex class) ...

    private boolean activeOwnerHighlight = false;
    private String activeOwnerLabel = null;

    /**
     * Bridges the UIManager call to the internal State system.
     * 
     * @param active If true, sets state to HIGHLIGHT_PURPLE. If false, reverts to
     *               NORMAL.
     * @param label  The text to display (or null).
     */
    public void setActiveOwnerHighlight(boolean active, String label, boolean isOperatingCompany) {
        // 1. Update Internal Fields
        this.activeOwnerLabel = label;
        this.activeOwnerHighlight = active;

        // 2. Handle the Highlight State
        if (active) {
            if (isOperatingCompany) {
                setState(State.HIGHLIGHT_PURPLE);
            } else {
                setState(State.HIGHLIGHT_PORTFOLIO);
            }

            // CRITICAL FIX: Force a repaint of the marks (border) layer.
            hexMap.repaintMarks(getMarksDirtyBounds());

        } else {
            // Only revert to normal if we are currently purple or portfolio.
            if (getState() == State.HIGHLIGHT_PURPLE || getState() == State.HIGHLIGHT_PORTFOLIO) {
                setState(State.NORMAL);
            }
        }

        // 3. Trigger Token Layer Repaint (For the Label)
        hexMap.repaintTokens(getBounds());
    }

    /**
     * Toggles the destination highlight state.
     */
    public void setDestinationHighlight(boolean active) {
        if (active) {
            setState(State.HIGHLIGHT_DESTINATION);
            hexMap.repaintMarks(getMarksDirtyBounds());
        } else {
            if (getState() == State.HIGHLIGHT_DESTINATION) {
                setState(State.NORMAL);
            }
        }
    }

 private void drawDestinationMilestones(Graphics2D g2) {


        if (hexMap == null || hexMap.getMapManager() == null || hexMap.getMapManager().getRoot() == null) return;
        
        java.util.List<PublicCompany> destinationMarkers = new java.util.ArrayList<>();
        net.sf.rails.game.CompanyManager cm = hexMap.getMapManager().getRoot().getCompanyManager();
        if (cm != null) {
            for (PublicCompany comp : cm.getAllPublicCompanies()) {
                if (comp.getDestinationHex() != null && comp.getDestinationHex().equals(getHex())) {
                    destinationMarkers.add(comp);
                }
            }
        }

        if (destinationMarkers.isEmpty()) return;

        java.awt.Font originalFont = g2.getFont();
        int scaledFontSize = Math.max(8, (int) Math.round(10 * dimensions.zoomFactor));
        g2.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, scaledFontSize));
        java.awt.FontMetrics fm = g2.getFontMetrics();

        int size = (int) Math.round(dimensions.tokenDiameter); 
        int gap = (int) Math.round(2 * dimensions.zoomFactor);   
        
        int startX = (int) dimensions.rectBound.getX() + (int)(10 * dimensions.zoomFactor);
        int startY = (int) dimensions.rectBound.getY() + (int)(10 * dimensions.zoomFactor);

        for (int i = 0; i < destinationMarkers.size(); i++) {
            PublicCompany comp = destinationMarkers.get(i);
            boolean connected = comp.hasReachedDestination();
            
            int x = startX + (i * (size + gap));
            int y = startY;

            // 1. Draw Background
            java.awt.Color bg = comp.getBgColour();
            if (connected) {
                bg = new java.awt.Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 100);
            }
            g2.setColor(bg);
            g2.fillRect(x, y, size, size);

            // 2. Draw Border
            g2.setColor(java.awt.Color.BLACK);
            g2.setStroke(new java.awt.BasicStroke(1.0f));
            g2.drawRect(x, y, size, size);

            // 3. Draw Company ID
            java.awt.Color fg = comp.getFgColour();
            if (connected) {
                fg = new java.awt.Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 100);
            }
            g2.setColor(fg);
            String id = comp.getId().substring(0, Math.min(comp.getId().length(), 3));
            int tx = x + (size - fm.stringWidth(id)) / 2;
            int ty = y + ((size - fm.getHeight()) / 2) + fm.getAscent();
            g2.drawString(id, tx, ty);

            // 4. Overlay Checkmark if connected
            if (connected) {
                g2.setColor(new java.awt.Color(0, 150, 0)); 
                g2.setStroke(new java.awt.BasicStroke(2.0f));
                g2.drawLine(x + 2, y + size/2, x + size/2, y + size - 2);
                g2.drawLine(x + size/2, y + size - 2, x + size - 2, y + 2);
            }
        }
        g2.setFont(originalFont);
    }
}