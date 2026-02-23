package net.sf.rails.javafx.stockchart;

import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.transform.Scale;
import net.sf.rails.game.financial.StockMarket;
import net.sf.rails.game.financial.StockSpace;
import net.sf.rails.ui.swing.GameUIManager;

// --- START FIX ---
public class FXHexStockChart extends Pane {

    private final StockMarket stockMarket;
    private final Group contentGroup;
    private final double HEX_RADIUS = 40.0;
    private final double GAP = 2.0;

    public FXHexStockChart(GameUIManager gameUIManager) {
        this.stockMarket = gameUIManager.getRoot().getStockMarket();
        this.contentGroup = new Group();
        
        setStyle("-fx-background-color: white;");

        getChildren().add(contentGroup);
        initializeChart();
        
        widthProperty().addListener((obs, oldVal, newVal) -> scaleContent());
        heightProperty().addListener((obs, oldVal, newVal) -> scaleContent());
    }

    private void initializeChart() {
        int rows = stockMarket.getNumberOfRows();
        int cols = stockMarket.getNumberOfColumns();

        // Pointy-Topped Hex Geometry
        double hexWidth = Math.sqrt(3) * HEX_RADIUS;
        double hexHeight = HEX_RADIUS * 2;
        
        // Vertical spacing: 3/4 of height (for pointy-topped)
        double vertSpacing = hexHeight * 0.75;
        // Horizontal spacing: Full width
        double horizSpacing = hexWidth;

        for (int r = 0; r <= rows; r++) {
            for (int c = 0; c <= cols; c++) {
                StockSpace space = stockMarket.getStockSpace(r, c);
                
                if (space != null) {
                    FXHexStockField hexField = new FXHexStockField(space);
                    
                    double xPos = c * (horizSpacing + GAP);
                    double yPos = r * (vertSpacing + GAP);

                    // Offset odd rows horizontally (Standard Pointy-Topped / "even-r" or "odd-r")
                    if (r % 2 != 0) {
                        xPos += (horizSpacing + GAP) / 2.0;
                    }

                    hexField.setLayoutX(xPos);
                    hexField.setLayoutY(yPos);
                    
                    hexField.setPrefSize(hexWidth, hexHeight);
                    hexField.setMinSize(hexWidth, hexHeight);
                    hexField.setMaxSize(hexWidth, hexHeight);

                    contentGroup.getChildren().add(hexField);
                }
            }
        }
    }

    private void scaleContent() {
        if (contentGroup.getBoundsInLocal().getWidth() == 0) return;

        double width = getWidth();
        double height = getHeight();
        double contentWidth = contentGroup.getBoundsInLocal().getWidth();
        double contentHeight = contentGroup.getBoundsInLocal().getHeight();

        double scaleX = width / contentWidth;
        double scaleY = height / contentHeight;
        double scaleFactor = Math.min(scaleX, scaleY);
        
        if (Double.isNaN(scaleFactor) || scaleFactor <= 0) scaleFactor = 1.0;

        Scale scale = new Scale(scaleFactor, scaleFactor);
        contentGroup.getTransforms().setAll(scale);
        
        double scaledWidth = contentWidth * scaleFactor;
        double scaledHeight = contentHeight * scaleFactor;
        contentGroup.setTranslateX((width - scaledWidth) / 2);
        contentGroup.setTranslateY((height - scaledHeight) / 2);
    }
}
// --- END FIX ---