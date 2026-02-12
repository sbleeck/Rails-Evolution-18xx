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
    // Define a fixed internal coordinate system
    private final double HEX_RADIUS = 40.0;
    private final double GAP = 2.0;

    public FXHexStockChart(GameUIManager gameUIManager) {
        this.stockMarket = gameUIManager.getRoot().getStockMarket();
        this.contentGroup = new Group();
        
        // 1. Set Background to White
        setStyle("-fx-background-color: white;");

        getChildren().add(contentGroup);
        initializeChart();
        
        // 2. Add resizing listeners to scale the entire group
        widthProperty().addListener((obs, oldVal, newVal) -> scaleContent());
        heightProperty().addListener((obs, oldVal, newVal) -> scaleContent());
    }

    private void initializeChart() {
        int rows = stockMarket.getNumberOfRows();
        int cols = stockMarket.getNumberOfColumns();

        // Flat-Topped Hex Geometry
        double hexWidth = HEX_RADIUS * 2;
        double hexHeight = Math.sqrt(3) * HEX_RADIUS;
        
        // Horizontal spacing: 3/4 of width (for flat-topped)
        double horizSpacing = hexWidth * 0.75;
        // Vertical spacing: Full height
        double vertSpacing = hexHeight;

        for (int r = 0; r <= rows; r++) {
            for (int c = 0; c <= cols; c++) {
                StockSpace space = stockMarket.getStockSpace(r, c);
                
                if (space != null) {
                    FXHexStockField hexField = new FXHexStockField(space);
                    
                    // Calculate positions
                    double xPos = c * (horizSpacing + GAP);
                    double yPos = r * (vertSpacing + GAP);

                    // Offset odd columns vertically
                    if (c % 2 != 0) {
                        yPos += (vertSpacing + GAP) / 2.0;
                    }

                    hexField.setLayoutX(xPos);
                    hexField.setLayoutY(yPos);
                    
                    // Fix the size of the field in the internal coordinate system
                    hexField.setPrefSize(hexWidth, hexHeight);
                    hexField.setMinSize(hexWidth, hexHeight);
                    hexField.setMaxSize(hexWidth, hexHeight);

                    contentGroup.getChildren().add(hexField);
                }
            }
        }
    }

    private void scaleContent() {
        // Guard against zero-size or uninitialized bounds
        if (contentGroup.getBoundsInLocal().getWidth() == 0) return;

        double width = getWidth();
        double height = getHeight();
        double contentWidth = contentGroup.getBoundsInLocal().getWidth();
        double contentHeight = contentGroup.getBoundsInLocal().getHeight();

        // Calculate scale to fit, preserving aspect ratio
        double scaleX = width / contentWidth;
        double scaleY = height / contentHeight;
        double scaleFactor = Math.min(scaleX, scaleY);
        
        // Safety clamp to prevent infinite scaling on minimization
        if (Double.isNaN(scaleFactor) || scaleFactor <= 0) scaleFactor = 1.0;

        // Apply scale
        Scale scale = new Scale(scaleFactor, scaleFactor);
        contentGroup.getTransforms().setAll(scale);
        
        // Center the content
        double scaledWidth = contentWidth * scaleFactor;
        double scaledHeight = contentHeight * scaleFactor;
        contentGroup.setTranslateX((width - scaledWidth) / 2);
        contentGroup.setTranslateY((height - scaledHeight) / 2);
    }
}
// --- END FIX ---