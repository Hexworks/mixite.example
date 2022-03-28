package org.hexworks.mixite.example.javafx;

import javafx.event.ActionEvent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.hexworks.mixite.core.api.*;
import org.hexworks.mixite.core.vendor.Maybe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExampleController
{
    private static final String GRID_ORIENTATION_POINTY = "Pointy Top";
    private static final String GRID_ORIENTATION_FLAT = "Flat Top";
    private static final HexagonOrientation DEFAULT_ORIENTATION = HexagonOrientation.FLAT_TOP;
    private static final Map<String, HexagonOrientation> orientations = new HashMap<>();
    public ChoiceBox gridOrientationChoiceBox;

    private static final String GRID_LAYOUT_RECTANGLE = "Rectangle";
    private static final String GRID_LAYOUT_HEXAGON = "Hexagon";
    private static final String GRID_LAYOUT_TRIANGLE = "Triangle";
    private static final String GRID_LAYOUT_TRAPEZOID = "Trapezoid";
    private static final HexagonalGridLayout DEFAULT_LAYOUT = HexagonalGridLayout.RECTANGULAR;
    private static final Map<String, HexagonalGridLayout> layouts = new HashMap<>();
    public ChoiceBox layoutChoiceBox;

    private static final int DEFAULT_GRID_WIDTH = 20;
    public Spinner gridWidthSpinner;
    private static final int DEFAULT_GRID_HEIGHT = 20;
    public Spinner gridHeightSpinner;
    private static final int DEFAULT_CELL_RADIUS = 20;
    public Spinner cellRadiusSpinner;
    private static final int DEFAULT_MOVE_RANGE = 2;
    public Spinner moveRangeSpinner;

    private static final Color COLOR_GRID_LINE = Color.BLACK;
    private static final Color COLOR_NEIGHBOR = Color.SLATEGREY;
    private static final Color COLOR_VISIBLE = Color.GREEN;
    private static final Color COLOR_NOT_VISIBLE = Color.DARKRED;
    private static final Color COLOR_PATH = Color.PURPLE;
    private static final Color COLOR_MOVE_RANGE = Color.YELLOW;
    private static final Color COLOR_UNSELECTED_CELL = Color.WHITESMOKE;
    private static final Color COLOR_SELECTED_CELL = Color.BLUE;
    private static final Color COLOR_CALCULATING_DISTANCE = Color.CYAN;

    public CheckBox showNeighborsCheckbox;
    public CheckBox showVisibilityCheckbox;
    public CheckBox showPathingCheckbox;
    public CheckBox showMoveRangeCheckbox;
    public CheckBox showCoordinatesCheckbox;

    private double coordTextSize = DEFAULT_CELL_RADIUS / 3.5;

    public TextField canvasXField;
    Double latestCanvasX = Double.NaN;
    public TextField canvasYField;
    Double latestCanvasY = Double.NaN;

    public TextField lastDistanceField;

    public Canvas gridCanvas;

    private HexagonalGrid<SatelliteDataImpl> hexagonalGrid;
    private HexagonalGridCalculator<SatelliteDataImpl> hexagonalGridCalculator;

    private Hexagon<SatelliteDataImpl> currentSelected = null;
    private Hexagon<SatelliteDataImpl> previousSelected = null;


    static
    {
        // Pre-map string values to their enum equivalents.

        orientations.put(GRID_ORIENTATION_POINTY, HexagonOrientation.POINTY_TOP);
        orientations.put(GRID_ORIENTATION_FLAT, HexagonOrientation.FLAT_TOP);

        layouts.put(GRID_LAYOUT_RECTANGLE, HexagonalGridLayout.RECTANGULAR);
        layouts.put(GRID_LAYOUT_HEXAGON, HexagonalGridLayout.HEXAGONAL);
        layouts.put(GRID_LAYOUT_TRIANGLE, HexagonalGridLayout.TRIANGULAR);
        layouts.put(GRID_LAYOUT_TRAPEZOID, HexagonalGridLayout.TRAPEZOID);
    }

    public void initialize()
    {
        // Add the grid orientation options. The differentiator is whether the top is pointy or flat.
        gridOrientationChoiceBox.getItems().addAll(orientations.keySet());
        gridOrientationChoiceBox.getSelectionModel().selectFirst();

        // Add the grid layout options. Hex grids are stored and manipulated with cubic
        // coordinates, but many applications assume cartesian coordinates, so there are
        // several possible ways to lay out those variations.
        layoutChoiceBox.getItems().addAll(layouts.keySet());
        layoutChoiceBox.getSelectionModel().select(GRID_LAYOUT_RECTANGLE);

        // Set up the spinners with default values and make sure that they only accept numbers.
        gridWidthSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 100, DEFAULT_GRID_WIDTH));
        gridHeightSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 100, DEFAULT_GRID_HEIGHT));
        cellRadiusSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 200, DEFAULT_CELL_RADIUS));
        moveRangeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, DEFAULT_MOVE_RANGE));

        resetGrid(null);
    }

    public void resetGrid(ActionEvent actionEvent)
    {
        // 1. Get vital grid info.
        int gridWidth = validateAndGetGridWidth();
        int gridHeight = validateAndGetGridHeight();
        int cellRadius = validateAndGetCellRadius();
        HexagonOrientation orientation = validateAndGetOrientation();
        HexagonalGridLayout layout = validateAndGetLayout();

        // 2. Create a grid builder and the corresponding calculator.
        HexagonalGridBuilder<SatelliteDataImpl> builder = new HexagonalGridBuilder<SatelliteDataImpl>()
                .setGridWidth(gridWidth)
                .setGridHeight(gridHeight)
                .setRadius(cellRadius)
                .setOrientation(orientation)
                .setGridLayout(layout);

        hexagonalGrid = builder.build();
        hexagonalGridCalculator = builder.buildCalculatorFor(hexagonalGrid);

        // 3. Forget the previously selected cells.
        currentSelected = null;
        previousSelected = null;

        // 4. Reset the coordinate font size.
        coordTextSize = cellRadius / 3.5;

        // 5. Uncheck all boxes.
        showNeighborsCheckbox.setSelected(false);
        showVisibilityCheckbox.setSelected(false);
        showPathingCheckbox.setSelected(false);
        showMoveRangeCheckbox.setSelected(false);
        showCoordinatesCheckbox.setSelected(false);

        // 6. Redraw everything.
        redraw();
    }

    private void redraw()
    {
        // Don't forget to clear first!
        gridCanvas.getGraphicsContext2D().clearRect(0, 0, gridCanvas.getWidth(), gridCanvas.getHeight());

        // Collect neighbors, but don't draw them inline. Doing so would cause cells
        // to redraw over the "neighbor" color. Instead, we'll go over the default color.
        List<Hexagon<SatelliteDataImpl>> neighbors = new ArrayList<>();
        // Same with selected cells. We want to maintain the correct precedence
        // for the fill colors.
        List<Hexagon<SatelliteDataImpl>> selected = new ArrayList<>();
        // And the cells that indicate movement distance.
        List<Hexagon<SatelliteDataImpl>> moveRange = new ArrayList<>();

        // Since we are doing things this way, let's be consistent and pre-calculate
        // the outlines, too. This one is for the two cells used to calculate the
        // latest distance.
        List<Hexagon<SatelliteDataImpl>> distancers = new ArrayList<>();

        if(currentSelected != null)
        {
            distancers.add(currentSelected);
        }
        if(previousSelected != null)
        {
            distancers.add(previousSelected);
        }

        // Make sure our grid lines are visible.
        gridCanvas.getGraphicsContext2D().setLineWidth(2d);

        // Pre-process each of the special cases.
        for (Hexagon<SatelliteDataImpl> hexagon : hexagonalGrid.getHexagons())
        {
            if(hexagon.getSatelliteData().isPresent() && hexagon.getSatelliteData().get().isSelected())
            {
                selected.add(hexagon);
                if(showNeighborsCheckbox.isSelected())
                {
                    neighbors.addAll(hexagonalGrid.getNeighborsOf(hexagon));
                }

                if(showMoveRangeCheckbox.isSelected())
                {
                    moveRange.addAll(hexagonalGridCalculator.calculateMovementRangeFrom(hexagon, validateAndGetMoveRange()));
                }

            }
            // Draw the default cell color.
            fillHexagon(gridCanvas, hexagon, COLOR_UNSELECTED_CELL);
        }

        // Fill in the "move range" cells.
        if(showMoveRangeCheckbox.isSelected())
        {
            for (Hexagon<SatelliteDataImpl> hexagon : moveRange)
            {
                fillHexagon(gridCanvas, hexagon, COLOR_MOVE_RANGE);
            }
        }

        // Fill in neighbor cells next.
        if(showNeighborsCheckbox.isSelected())
        {
            for (Hexagon<SatelliteDataImpl> hexagon : neighbors)
            {
                fillHexagon(gridCanvas, hexagon, COLOR_NEIGHBOR);
            }
        }

        // Fill in selected cells last. This ensures that they are always shown, even if they are also neighbors.
        for (Hexagon<SatelliteDataImpl> hexagon : selected)
        {
            fillHexagon(gridCanvas, hexagon, COLOR_SELECTED_CELL);
        }

        // Draw the grid after the fills. This ensures that the fill does not cover the grid line.
        // This is before the special case fills, though, as those override the default color.
        for (Hexagon<SatelliteDataImpl> hexagon : hexagonalGrid.getHexagons())
        {
            outlineHexagon(gridCanvas, hexagon, COLOR_GRID_LINE);
        }

        // Draw outlines for the (up to) two cells that are currently being used
        // for the distance calculation.
        for (Hexagon<SatelliteDataImpl> hexagon : distancers)
        {
            outlineHexagon(gridCanvas, hexagon, COLOR_CALCULATING_DISTANCE);
        }

        // This is the marker for visible vs non-visible cells. There is only ever one cell
        // to which this applies, so don't try to calculate this for every cell.
        if(currentSelected != null
                && showVisibilityCheckbox.isSelected()
                && !Double.isNaN(latestCanvasX) && !Double.isNaN(latestCanvasY))
        {
            Maybe<Hexagon<SatelliteDataImpl>> hexagonMaybe = hexagonalGrid.getByPixelCoordinate(latestCanvasX, latestCanvasY);
            if(hexagonMaybe.isPresent())
            {
                Hexagon<SatelliteDataImpl> hexagon = hexagonMaybe.get();
                if(hexagonalGridCalculator.isVisible(currentSelected, hexagon))
                {
                    outlineHexagon(gridCanvas, hexagon, COLOR_VISIBLE);
                }
                else
                {
                    outlineHexagon(gridCanvas, hexagon, COLOR_NOT_VISIBLE);
                }
            }
        } // End visibility marker.

        // We also need to mark the path from the last selected cell to the current mouse position.
        // Instead of another outline, let's use a circle in the middle of the hexes. That allows us
        // to show more information on the screen at once, but without significant visual clutter.
        if(currentSelected != null
                && showPathingCheckbox.isSelected()
                && !Double.isNaN(latestCanvasX) && !Double.isNaN(latestCanvasY))
        {
            Maybe<Hexagon<SatelliteDataImpl>> hexagonMaybe = hexagonalGrid.getByPixelCoordinate(latestCanvasX, latestCanvasY);
            if(hexagonMaybe.isPresent())
            {
                Hexagon<SatelliteDataImpl> mouseHexagon = hexagonMaybe.get();
                List<Hexagon<SatelliteDataImpl>> pathHexagons = hexagonalGridCalculator.drawLine(currentSelected, mouseHexagon);
                for (Hexagon<SatelliteDataImpl> pathHexagon : pathHexagons)
                {
                    drawCenterCircle(gridCanvas, pathHexagon, COLOR_PATH);
                }
            }
        } // End path markers.

        // Draw coordinate overlays last so that they are always visible,
        // regardless of any other markers.
        if(showCoordinatesCheckbox.isSelected())
        {
            for (Hexagon<SatelliteDataImpl> hexagon : hexagonalGrid.getHexagons())
            {
                drawCoordinates(gridCanvas, hexagon);
            }
        }
    }

    private void drawCoordinates(Canvas gridCanvas, Hexagon<SatelliteDataImpl> hexagon)
    {
        // Draw toward the left edge of each hex.
        double left = hexagon.getCenterX() - (validateAndGetCellRadius() * 0.5);

        // Don't overlap the text; stack the coords..
        double xTop = hexagon.getCenterY() - (coordTextSize);
        double yTop = hexagon.getCenterY() + (coordTextSize / 2.0);
        double zTop = hexagon.getCenterY() + (coordTextSize * 2);

        // These are the cubic grid coordinates.
        int gridX = hexagon.getGridX();
        int gridY = hexagon.getGridY();
        int gridZ = -(gridX + gridY);

        GraphicsContext context = gridCanvas.getGraphicsContext2D();
        context.setFont(Font.font(coordTextSize));
        context.setLineWidth(1);
        context.setFill(Color.BLACK);
        context.fillText("X: " + gridX, left, xTop);
        context.fillText("Y: " + gridY, left, yTop);
        context.fillText("Z: " + gridZ, left, zTop);
    }

    private void drawCenterCircle(Canvas gridCanvas, Hexagon<SatelliteDataImpl> hexagon, Color circleColor)
    {
        double circleRadius = validateAndGetCellRadius() / 3.0;
        gridCanvas.getGraphicsContext2D().setFill(circleColor);
        gridCanvas.getGraphicsContext2D().fillOval(
                hexagon.getCenterX() - circleRadius,
                hexagon.getCenterY() - circleRadius,
                circleRadius * 2.0,
                circleRadius * 2.0);
    }

    private void fillHexagon(Canvas gridCanvas, Hexagon<SatelliteDataImpl> hexagon, Color fillColor)
    {
        // Default the line color.
        gridCanvas.getGraphicsContext2D().setFill(fillColor);

        HexPoints hexPoints = extractHexPoints(hexagon);
        gridCanvas.getGraphicsContext2D().fillPolygon(hexPoints.xPoints, hexPoints.yPoints, HexPoints.numPoints);
    }

    private void outlineHexagon(Canvas gridCanvas, Hexagon<SatelliteDataImpl> hexagon, Color outlineColor)
    {
        GraphicsContext context = gridCanvas.getGraphicsContext2D();
        context.setLineWidth(3);
        context.setStroke(outlineColor);
        HexPoints hexPoints = extractHexPoints(hexagon);
        context.strokePolygon(hexPoints.xPoints, hexPoints.yPoints, HexPoints.numPoints);

    }

    private int validateAndGetGridWidth()
    {
        int gridWidth;
        gridWidth = DEFAULT_GRID_WIDTH;
        try
        {
            gridWidth = Integer.parseInt(gridWidthSpinner.getValue().toString());
        }
        catch(Exception e)
        {
            System.out.println("Exception while reading grid width; resetting to default.");
            gridWidthSpinner.getValueFactory().setValue(DEFAULT_GRID_WIDTH);
        }

        return gridWidth;
    }

    private int validateAndGetGridHeight()
    {
        int gridHeight = DEFAULT_GRID_HEIGHT;
        try
        {
            gridHeight = Integer.parseInt(gridHeightSpinner.getValue().toString());
        }
        catch(Exception e)
        {
            System.out.println("Exception while reading grid height; resetting to default.");
            gridHeightSpinner.getValueFactory().setValue(DEFAULT_GRID_HEIGHT);
        }

        return gridHeight;
    }

    private int validateAndGetCellRadius()
    {
        int cellRadius = DEFAULT_CELL_RADIUS;
        try
        {
            cellRadius = Integer.parseInt(cellRadiusSpinner.getValue().toString());
        }
        catch(Exception e)
        {
            System.out.println("Exception while reading cell radius; resetting to default.");
            cellRadiusSpinner.getValueFactory().setValue(DEFAULT_CELL_RADIUS);
        }
        return cellRadius;
    }

    private int validateAndGetMoveRange()
    {
        int moveRange = DEFAULT_MOVE_RANGE;
        try
        {
            moveRange = Integer.parseInt(moveRangeSpinner.getValue().toString());
        }
        catch(Exception e)
        {
            System.out.println("Exception while reading movement range; resetting to default.");
            moveRangeSpinner.getValueFactory().setValue(DEFAULT_MOVE_RANGE);
        }
        return moveRange;
    }

    private HexagonOrientation validateAndGetOrientation()
    {
        HexagonOrientation orientation = DEFAULT_ORIENTATION;
        String selectedOrientation = gridOrientationChoiceBox.getSelectionModel().getSelectedItem().toString();
        if(orientations.containsKey(selectedOrientation))
        {
            orientation = orientations.get(selectedOrientation);
        }
        else
        {
            System.out.println("Orientation has an invalid selection. This should be impossible; resetting to default.");
            gridOrientationChoiceBox.getSelectionModel().selectFirst();
        }
        return orientation;
    }

    private HexagonalGridLayout validateAndGetLayout()
    {
        HexagonalGridLayout layout = DEFAULT_LAYOUT;
        String selectedLayout = layoutChoiceBox.getSelectionModel().getSelectedItem().toString();
        if(layouts.containsKey(selectedLayout))
        {
            layout = layouts.get(selectedLayout);
        }
        else
        {
            System.out.println("Layout has an invalid selection. This should be impossible; resetting to default.");
            layoutChoiceBox.getSelectionModel().selectFirst();
        }
        return layout;
    }

    private HexPoints extractHexPoints(Hexagon<SatelliteDataImpl> hexagon)
    {
        HexPoints hexPoints = new HexPoints();

        List<Point> points = hexagon.getPoints();
        for (int index = 0; index < points.size(); index++)
        {
            Point point = points.get(index);
            hexPoints.xPoints[index] = point.getCoordinateX();
            hexPoints.yPoints[index] = point.getCoordinateY();
        }

        return hexPoints;
    }

    public void onCanvasClick(MouseEvent mouseEvent)
    {
        double eventX = mouseEvent.getX();
        double eventY = mouseEvent.getY();

        if(hexagonalGrid != null)
        {
            Maybe<Hexagon<SatelliteDataImpl>> clickedHex = hexagonalGrid.getByPixelCoordinate(eventX, eventY);
            if(clickedHex.isPresent())
            {
                previousSelected = currentSelected;
                currentSelected = clickedHex.get();
                if(clickedHex.get().getSatelliteData().isEmpty())
                {
                    clickedHex.get().setSatelliteData(new SatelliteDataImpl());
                }
                SatelliteDataImpl satelliteData = clickedHex.get().getSatelliteData().get();
                satelliteData.setSelected(!satelliteData.isSelected());
                satelliteData.setOpaque(satelliteData.isSelected());

                if(previousSelected != null && currentSelected != null)
                {
                    lastDistanceField.setText(
                            "" + hexagonalGridCalculator.calculateDistanceBetween(previousSelected, currentSelected));
                }
            }
        }

        redraw();
    }

    public void onCanvasMouseMove(MouseEvent mouseEvent)
    {
        latestCanvasX = mouseEvent.getX();
        latestCanvasY = mouseEvent.getY();
        canvasXField.setText(latestCanvasX + "");
        canvasYField.setText(latestCanvasY + "");
        redraw();
    }

    public void triggerRedraw(MouseEvent mouseEvent)
    {
        redraw();
    }

    public void triggerGridReset(MouseEvent mouseEvent)
    {
        resetGrid(null);
    }

    private static class HexPoints
    {
        final double[] xPoints = new double[6];
        final double[] yPoints = new double[6];
        final static int numPoints = 6;
    }
}
