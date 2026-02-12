package net.sf.rails.javafx.stockchart;

import java.util.List;
import com.google.common.collect.Lists;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.text.Text;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.financial.StockSpace;
import net.sf.rails.game.state.Observer;
import net.sf.rails.game.state.Observable;
import net.sf.rails.javafx.ColorUtils;
import net.sf.rails.util.Util;

// --- START FIX ---
public class FXHexStockField extends StackPane implements Observer {

    private final StockSpace model;
    private final Polygon hexagon;
    private final StackPane tokenPane;
    private final Polyline topLedge;
    private final Polyline rightLedge;

    public FXHexStockField(StockSpace model) {
        this.model = model;
        this.hexagon = new Polygon();
        this.tokenPane = new StackPane();
        this.topLedge = new Polyline();
        this.rightLedge = new Polyline();

        initialize();
        populate();

        // Redraw hex geometry when the component size is set by the Chart
        widthProperty().addListener((o) -> updateHexShape());
        heightProperty().addListener((o) -> updateHexShape());
    }

    private void initialize() {
        hexagon.setFill(ColorUtils.toColor(model.getColour()));
        hexagon.setStroke(Color.BLACK);
        hexagon.setStrokeWidth(1.0);

        if (model.isStart()) {
            hexagon.setStroke(Color.RED);
            hexagon.setStrokeWidth(3.0);
        }

        topLedge.setStroke(Color.RED);
        topLedge.setStrokeWidth(4.0);
        topLedge.setVisible(model.isBelowLedge());

        rightLedge.setStroke(Color.RED);
        rightLedge.setStrokeWidth(4.0);
        rightLedge.setVisible(model.isLeftOfLedge());

        tokenPane.setAlignment(Pos.CENTER);

        getChildren().addAll(hexagon, tokenPane, topLedge, rightLedge);
        model.addObserver(this);
    }

    private void updateHexShape() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Flat-Topped Hexagon Points (Scaled to fit w/h)
        // 0: Top-Left, 1: Top-Right, 2: Right, 3: Bot-Right, 4: Bot-Left, 5: Left
        double p0x = w * 0.25; double p0y = 0;
        double p1x = w * 0.75; double p1y = 0;
        double p2x = w;        double p2y = h / 2.0;
        double p3x = w * 0.75; double p3y = h;
        double p4x = w * 0.25; double p4y = h;
        double p5x = 0;        double p5y = h / 2.0;

        hexagon.getPoints().setAll(
            p0x, p0y, p1x, p1y, p2x, p2y,
            p3x, p3y, p4x, p4y, p5x, p5y
        );

        if (topLedge.isVisible()) topLedge.getPoints().setAll(p0x, p0y, p1x, p1y);
        if (rightLedge.isVisible()) rightLedge.getPoints().setAll(p1x, p1y, p2x, p2y, p3x, p3y);
    }

    public void populate() {
        tokenPane.getChildren().clear();

        // Price Text
        Text priceText = new Text(String.valueOf(model.getPrice()));
        priceText.setFill(Util.isDark(model.getColour()) ? Color.WHITE : Color.BLACK);
        // Fixed font size; scale transform in Chart handles the zoom
        priceText.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        priceText.setTranslateY(-getHeight() * 0.25); // Move text up
        tokenPane.getChildren().add(priceText);

        if (model.hasTokens()) {
            List<PublicCompany> tokens = Lists.reverse(model.getTokens());
            for (int i = 0; i < tokens.size(); i++) {
                PublicCompany comp = tokens.get(i);
                FXStockToken token = new FXStockToken(
                    ColorUtils.toColor(comp.getFgColour()),
                    ColorUtils.toColor(comp.getBgColour()),
                    comp.getId()
                );
                
                // Scale token to 2/3 (0.66) of the hex dimension
                token.prefWidthProperty().bind(widthProperty().multiply(0.66));
                token.prefHeightProperty().bind(heightProperty().multiply(0.66));
                
                // Stacking offset
                token.setTranslateX(i * 3);
                token.setTranslateY(i * 3);
                
                tokenPane.getChildren().add(token);
            }
        }
    }

    @Override
    public void update(String text) {
        Platform.runLater(this::populate);
    }

    @Override
    public Observable getObservable() { return model; }
}
// --- END FIX ---