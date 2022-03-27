package org.hexworks.mixite.example.javafx;

import javafx.event.ActionEvent;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import org.hexworks.mixite.core.api.*;
import org.hexworks.mixite.core.vendor.Maybe;

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
    private static final Color COLOR_VISIBLE = Color.DARKGREEN;
    private static final Color COLOR_NOT_VISIBLE = Color.DARKRED;
    private static final Color COLOR_PATH_OUTLINE = Color.PURPLE;
    private static final Color COLOR_MOVE_RANGE = Color.YELLOW;
    private static final Color COLOR_UNSELECTED_CELL = Color.TRANSPARENT;
    private static final Color COLOR_SELECTED_CELL = Color.BLUE;
    private static final Color COLOR_CALCULATING_DISTANCE = Color.CYAN;

    public CheckBox showNeighborsCheckbox;
    public CheckBox showVisibilityCheckbox;
    public CheckBox showPathingCheckbox;
    public CheckBox showMoveRangeCheckbox;
    public CheckBox showCoordinatesCheckbox;

    public TextField canvasXField;
    public TextField canvasYField;

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

        redraw();
    }

    private void redraw()
    {
        // Don't forget to clear first!
        gridCanvas.getGraphicsContext2D().clearRect(0, 0, gridCanvas.getWidth(), gridCanvas.getHeight());

        // Draw the full grid first. This takes care of fills, but outlines will be done later.
        for (Hexagon<SatelliteDataImpl> hexagon : hexagonalGrid.getHexagons())
        {
            //Maybe<SatelliteData> satelliteData = hexagon.getSatelliteData();
            drawHexagon(gridCanvas, hexagon);
        }
        for (Hexagon<SatelliteDataImpl> hexagon : hexagonalGrid.getHexagons())
        {
            //Maybe<SatelliteData> satelliteData = hexagon.getSatelliteData();
            outlineHexagon(gridCanvas, hexagon);
        }
    }

    private void drawHexagon(Canvas gridCanvas, Hexagon<SatelliteDataImpl> hexagon)
    {
        // Default the line color.
        gridCanvas.getGraphicsContext2D().setStroke(COLOR_GRID_LINE);

        // Default the color fill to the "unselected" color.
        gridCanvas.getGraphicsContext2D().setFill(COLOR_UNSELECTED_CELL);
        // If the cell is selected, mark it with a different fill
        if(hexagon.getSatelliteData().isPresent() && hexagon.getSatelliteData().get().isSelected())
        {
            gridCanvas.getGraphicsContext2D().setFill(COLOR_SELECTED_CELL);
        }

        HexPoints hexPoints = extractHexPoints(hexagon);
        gridCanvas.getGraphicsContext2D().fillPolygon(hexPoints.xPoints, hexPoints.yPoints, HexPoints.numPoints);
        gridCanvas.getGraphicsContext2D().strokePolygon(hexPoints.xPoints, hexPoints.yPoints, HexPoints.numPoints);
    }

    private void outlineHexagon(Canvas gridCanvas, Hexagon<SatelliteDataImpl> hexagon)
    {
        // If the cell is one of the two being used for distance, mark it with a different outline.
        if(hexagon.getSatelliteData().isPresent()
                && (hexagon.equals(currentSelected) || hexagon.equals(previousSelected)))
        {
            gridCanvas.getGraphicsContext2D().setStroke(COLOR_CALCULATING_DISTANCE);
            HexPoints hexPoints = extractHexPoints(hexagon);
            gridCanvas.getGraphicsContext2D().strokePolygon(hexPoints.xPoints, hexPoints.yPoints, HexPoints.numPoints);
        }

        // Don't redraw default lines.
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
        canvasXField.setText(mouseEvent.getX() + "");
        canvasYField.setText(mouseEvent.getY() + "");
    }

    private class HexPoints
    {
        final double[] xPoints = new double[6];
        final double[] yPoints = new double[6];
        final static int numPoints = 6;
    }
}