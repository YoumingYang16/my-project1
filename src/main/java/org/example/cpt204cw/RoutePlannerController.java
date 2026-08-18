package org.example.cpt204cw;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/*
 * JavaFX controller for the interactive route planner. It coordinates UI
 * selections, graph loading, solver selection, route execution, and canvas
 * rendering while delegating shortest-path work to backend classes.
 */
public class RoutePlannerController {
    private static final String ALGORITHM_LECTURE_DIJKSTRA = "Lecture-Style Dijkstra (ArrayList T + Full Scan)";
    private static final String ALGORITHM_PRIORITY_QUEUE_DIJKSTRA = "PriorityQueue Dijkstra with Early Stopping";
    private static final String ALGORITHM_BIDIRECTIONAL_DIJKSTRA = "Bidirectional Dijkstra";
    private static final String ALGORITHM_BELLMAN_FORD = "Bellman-Ford";
    private static final String ALGORITHM_A_STAR_ALT = "A* with ALT Landmark Heuristic";
    private static final String ALGORITHM_FLOYD_WARSHALL = "Floyd-Warshall";
    private static final double CANVAS_DEFAULT_WIDTH = 550.0;
    private static final double CANVAS_DEFAULT_HEIGHT = 550.0;
    private static final double LAYOUT_MARGIN_X = 64.0;
    private static final double LAYOUT_MARGIN_Y = 58.0;

    @FXML private ComboBox<String> startPointCombo;
    @FXML private ComboBox<String> endPointCombo;
    @FXML private ComboBox<String> algorithmCombo;
    @FXML private ListView<Point> pointsList;
    @FXML private TextArea runtimeInfoArea;
    @FXML private Canvas mapCanvas;

    private ObservableList<String> startPoints;
    private ObservableList<String> endPoints;
    private ObservableList<String> algorithms;
    private ObservableList<Point> intermediatePoints;
    private WeightedGraph graph;
    private final List<String> selectedPointOrder = new ArrayList<>();
    private final Map<String, String> displayNameByLocation = new LinkedHashMap<>();

    /*
     * Reads selected target location IDs for the UI list. The full graph is
     * loaded separately from paths.csv so routing still uses all graph vertices.
     */
    public List<String> readThirdColumnFromCSV(String csvFilePath) {
        List<String> thirdColumnData = new ArrayList<>();
        displayNameByLocation.clear();

        try (FileInputStream fileInputStream = new FileInputStream(csvFilePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream, StandardCharsets.UTF_8))) {

            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                String[] columns = line.split(",", -1);
                if (columns.length >= 3) {
                    String thirdColumnValue = columns[2].trim();
                    if (!thirdColumnValue.isEmpty()) {
                        thirdColumnData.add(thirdColumnValue);
                        if (columns.length >= 4) {
                            String symbol = columns[0].trim() + columns[1].trim();
                            displayNameByLocation.put(thirdColumnValue, symbol + " (" + thirdColumnValue + ")");
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to read CSV file: " + e.getMessage());
            e.printStackTrace();
        }

        return thirdColumnData;
    }

    /*
     * View model for one selectable location in the JavaFX list and schematic
     * canvas.
     */
    public static class Point {
        private final StringProperty name;
        private final BooleanProperty selected;
        private final double x;
        private final double y;

        public Point(String name, double x, double y) {
            this.name = new SimpleStringProperty(name);
            this.selected = new SimpleBooleanProperty(false);
            this.x = x;
            this.y = y;
        }

        public String getName() {
            return name.get();
        }

        public boolean isSelected() {
            return selected.get();
        }

        public void setSelected(boolean value) {
            selected.set(value);
        }

        public BooleanProperty selectedProperty() {
            return selected;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    /*
     * Initialises the selectable target list, algorithm selector, graph data,
     * and first map render after the FXML file is loaded.
     */
    @FXML
    public void initialize() {
        List<String> locations = new ArrayList<>();
        try {
            locations = readThirdColumnFromCSV(resourceFilePath("selected_targets.csv"));
        } catch (IOException e) {
            System.err.println("Failed to locate selected_targets.csv: " + e.getMessage());
        }

        if (locations.isEmpty()) {
            System.err.println("Warning: selected_targets.csv is empty or could not be read. Using fallback points.");
            for (int i = 1; i <= 30; i++) {
                String fallbackPoint = "Point" + i;
                locations.add(fallbackPoint);
                displayNameByLocation.put(fallbackPoint, fallbackPoint);
            }
        }

        startPoints = FXCollections.observableArrayList(locations);
        endPoints = FXCollections.observableArrayList(locations);
        intermediatePoints = FXCollections.observableArrayList();

        for (int i = 0; i < locations.size(); i++) {
            double[] position = calculateSchematicPosition(i, locations.size());
            intermediatePoints.add(new Point(locations.get(i), position[0], position[1]));
        }

        algorithms = FXCollections.observableArrayList(
                ALGORITHM_LECTURE_DIJKSTRA,
                ALGORITHM_PRIORITY_QUEUE_DIJKSTRA,
                ALGORITHM_BIDIRECTIONAL_DIJKSTRA,
                ALGORITHM_BELLMAN_FORD,
                ALGORITHM_A_STAR_ALT,
                ALGORITHM_FLOYD_WARSHALL
        );

        startPointCombo.setItems(startPoints);
        endPointCombo.setItems(endPoints);
        configureLocationComboBox(startPointCombo);
        configureLocationComboBox(endPointCombo);
        if (!locations.isEmpty()) {
            startPointCombo.setValue(locations.get(0));
            endPointCombo.setValue(locations.get(locations.size() - 1));
        }
        startPointCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            pointsList.refresh();
            drawMap();
        });
        endPointCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            pointsList.refresh();
            drawMap();
        });

        algorithmCombo.setItems(algorithms);
        algorithmCombo.setValue(algorithms.get(0));

        pointsList.setItems(intermediatePoints);
        pointsList.setCellFactory(lv -> createPointCell());

        int selectedIndex = Math.min(2, intermediatePoints.size() - 1);
        int selectedIndex2 = Math.min(5, intermediatePoints.size() - 1);
        int selectedIndex3 = Math.min(8, intermediatePoints.size() - 1);

        selectInitialPoint(selectedIndex);
        selectInitialPoint(selectedIndex2);
        selectInitialPoint(selectedIndex3);

        loadGraphData();
        drawMap();
    }

    private void loadGraphData() {
        try {
            String pathsCsvPath = resourceFilePath("paths.csv");
            graph = PathCsvReader.readGraph(pathsCsvPath);
            System.out.println("Loaded graph data: " + graph.getVertexCount() + " vertices, "
                    + graph.getUndirectedEdgeCount() + " edges");
        } catch (IOException e) {
            System.err.println("Failed to load graph data: " + e.getMessage());
            showAlert("Error", "Cannot load path data file paths.csv.");
            graph = new WeightedGraph();
        }
    }

    private String resourceFilePath(String fileName) throws IOException {
        URL resource = RoutePlannerController.class.getResource(fileName);
        if (resource == null) {
            throw new IOException("Resource not found: " + fileName);
        }
        try {
            return Path.of(resource.toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IOException("Invalid resource URI: " + fileName, e);
        }
    }

    /*
     * Maps the JavaFX algorithm label to a backend ShortestPathSolver.
     */
    private ShortestPathSolver createSolver(String algorithmName) {
        if (algorithmName == null) {
            return new LectureDijkstraShortestPath();
        }

        return switch (algorithmName) {
            case ALGORITHM_LECTURE_DIJKSTRA -> new LectureDijkstraShortestPath();
            case ALGORITHM_PRIORITY_QUEUE_DIJKSTRA -> new OptimisedDijkstraShortestPath();
            case ALGORITHM_BIDIRECTIONAL_DIJKSTRA -> new BidirectionalDijkstraShortestPath();
            case ALGORITHM_BELLMAN_FORD -> new BellmanFordShortestPath();
            case ALGORITHM_A_STAR_ALT -> new AStarAltShortestPath();
            case ALGORITHM_FLOYD_WARSHALL -> new FloydWarshallSolverAdapter(graph);
            default -> throw new IllegalArgumentException("Unknown algorithm: " + algorithmName);
        };
    }

    /*
     * Adapts the all-pairs Floyd-Warshall implementation to the per-query
     * ShortestPathSolver interface used by RoutePlanner.
     */
    private static final class FloydWarshallSolverAdapter implements ShortestPathSolver {
        private final FloydWarshallAllPairsShortestPath floydWarshall;

        private FloydWarshallSolverAdapter(WeightedGraph graph) {
            this.floydWarshall = FloydWarshallAllPairsShortestPath.buildIfSuitable(graph);
            if (!floydWarshall.isExecuted()) {
                throw new IllegalStateException(
                        "Floyd-Warshall was skipped for this graph size: "
                                + floydWarshall.getVertexCount()
                                + " vertices. Maximum supported vertices: "
                                + FloydWarshallAllPairsShortestPath.MAX_FLOYD_WARSHALL_VERTICES);
            }
        }

        @Override
        public ShortestPathResult findShortestPath(WeightedGraph graph, String start, String destination) {
            graph.validateVertexExists(start);
            graph.validateVertexExists(destination);
            return floydWarshall.getShortestPath(start, destination);
        }
    }

    private ListCell<Point> createPointCell() {
        return new ListCell<>() {
            private final Label orderLabel = new Label();
            private final StackPane selectionBox = new StackPane(orderLabel);
            private final Label nameLabel = new Label();
            private final HBox hbox = new HBox(10, selectionBox, nameLabel);

            {
                selectionBox.setMinSize(24, 24);
                selectionBox.setPrefSize(24, 24);
                selectionBox.setMaxSize(24, 24);
                orderLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
                hbox.setAlignment(Pos.CENTER_LEFT);
                hbox.setCursor(Cursor.HAND);
                hbox.setOnMouseClicked(event -> toggleWaypointSelection(getItem()));
            }

            @Override
            protected void updateItem(Point item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    int waypointIndex = getSelectedWaypointIndex(item.getName());
                    boolean isWaypoint = waypointIndex > 0;
                    boolean isEndpoint = item.getName().equals(startPointCombo.getValue())
                            || item.getName().equals(endPointCombo.getValue());

                    orderLabel.setText(isWaypoint ? String.valueOf(waypointIndex) : "");
                    orderLabel.setTextFill(isWaypoint ? Color.WHITE : Color.TRANSPARENT);
                    selectionBox.setOpacity(isEndpoint ? 0.45 : 1.0);
                    selectionBox.setStyle(isWaypoint
                            ? "-fx-background-color: #2563eb; -fx-border-color: #1d4ed8; "
                            + "-fx-background-radius: 4; -fx-border-radius: 4;"
                            : "-fx-background-color: #ffffff; -fx-border-color: #94a3b8; "
                            + "-fx-background-radius: 4; -fx-border-radius: 4;");
                    nameLabel.setText(displayLocation(item.getName()));
                    nameLabel.setTextFill(isEndpoint ? Color.rgb(100, 116, 139) : Color.rgb(17, 24, 39));
                    setGraphic(hbox);
                }
            }
        };
    }

    private void configureLocationComboBox(ComboBox<String> comboBox) {
        comboBox.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : displayLocation(item));
            }
        });
        comboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : displayLocation(item));
            }
        });
    }

    private String displayLocation(String locationId) {
        return displayNameByLocation.getOrDefault(locationId, locationId);
    }

    private void toggleWaypointSelection(Point point) {
        if (point == null) {
            return;
        }
        String startPointName = startPointCombo.getValue();
        String endPointName = endPointCombo.getValue();
        if (point.getName().equals(startPointName) || point.getName().equals(endPointName)) {
            return;
        }
        setPointSelected(point, !point.isSelected());
        pointsList.refresh();
        drawMap();
    }

    /*
     * Validates the user request, builds a fixed-order RouteQuery, executes the
     * selected solver, and writes the route summary back to the UI.
     */
    @FXML
    private void onRunButtonClick() {
        String startPoint = startPointCombo.getValue();
        String endPoint = endPointCombo.getValue();
        String algorithm = algorithmCombo.getValue();

        if (startPoint == null || endPoint == null || startPoint.trim().isEmpty() || endPoint.trim().isEmpty()) {
            showAlert("Error", "Please select both a start point and an end point.");
            return;
        }

        if (startPoint.equals(endPoint)) {
            showAlert("Error", "Start point and end point cannot be the same.");
            return;
        }

        if (algorithm == null || algorithm.trim().isEmpty()) {
            showAlert("Error", "Please select an algorithm.");
            return;
        }

        List<String> waypointLocations = getSelectedWaypointNames(startPoint, endPoint);
        StringBuilder runtimeInfo = new StringBuilder();

        if (graph == null || graph.getVertexCount() == 0) {
            runtimeInfo.append("Error: Graph data not loaded\n");
            runtimeInfoArea.setText(runtimeInfo.toString());
            return;
        }

        if (!graph.containsVertex(startPoint)) {
            runtimeInfo.append("Error: Start point '").append(startPoint).append("' not found in graph\n");
            runtimeInfoArea.setText(runtimeInfo.toString());
            return;
        }

        if (!graph.containsVertex(endPoint)) {
            runtimeInfo.append("Error: End point '").append(endPoint).append("' not found in graph\n");
            runtimeInfoArea.setText(runtimeInfo.toString());
            return;
        }

        for (String waypoint : waypointLocations) {
            if (!graph.containsVertex(waypoint)) {
                runtimeInfo.append("Error: Waypoint '").append(waypoint).append("' not found in graph\n");
                runtimeInfoArea.setText(runtimeInfo.toString());
                return;
            }
        }

        try {
            RouteQuery query = new RouteQuery(
                    "JavaFX route",
                    "Route requested from the JavaFX interface",
                    startPoint,
                    startPoint,
                    waypointLocations,
                    waypointLocations,
                    endPoint,
                    endPoint);
            RoutePlanner routePlanner = new RoutePlanner();

            long startTime = System.nanoTime();
            ShortestPathSolver solver = createSolver(algorithm);
            RouteResult result = routePlanner.solveFixedOrderRoute(graph, query, solver);
            long endTime = System.nanoTime();

            double actualRuntimeMs = (endTime - startTime) / 1_000_000.0;

            runtimeInfo.append("Route Order: ").append(String.join(" -> ", query.getOrderedStopLocations())).append("\n");
            runtimeInfo.append("Algorithm: ").append(result.getAlgorithmName()).append("\n");
            runtimeInfo.append("Algorithm Runtime: ").append(String.format("%.3f", actualRuntimeMs)).append(" ms\n");
            runtimeInfo.append("Total Cost: ").append(String.format("%.3f", result.getTotalCost())).append("\n");
        } catch (Exception e) {
            runtimeInfo.append("Error during execution: ").append(e.getMessage()).append("\n");
            e.printStackTrace();
        }

        runtimeInfoArea.setText(runtimeInfo.toString());
        drawMap();
    }

    @FXML
    private void onSelectAllButtonClick() {
        selectedPointOrder.clear();
        intermediatePoints.forEach(p -> setPointSelected(p, true));
        pointsList.refresh();
        drawMap();
    }

    @FXML
    private void onClearAllButtonClick() {
        intermediatePoints.forEach(p -> p.setSelected(false));
        selectedPointOrder.clear();
        pointsList.refresh();
        drawMap();
    }

    private List<String> getSelectedWaypointNames(String startPointName, String endPointName) {
        return selectedPointOrder.stream()
                .filter(name -> findPoint(name) != null && findPoint(name).isSelected())
                .filter(name -> !name.equals(startPointName) && !name.equals(endPointName))
                .toList();
    }

    private void selectInitialPoint(int index) {
        if (index >= 0 && index < intermediatePoints.size()) {
            setPointSelected(intermediatePoints.get(index), true);
        }
    }

    private void setPointSelected(Point point, boolean selected) {
        point.setSelected(selected);
        String name = point.getName();
        if (selected) {
            if (!selectedPointOrder.contains(name)) {
                selectedPointOrder.add(name);
            }
        } else {
            selectedPointOrder.remove(name);
        }
    }

    private String pointDisplayText(Point point) {
        int index = getSelectedWaypointIndex(point.getName());
        if (index > 0) {
            return index + ". " + point.getName();
        }
        return point.getName();
    }

    private int getSelectedWaypointIndex(String pointName) {
        String startPointName = startPointCombo.getValue();
        String endPointName = endPointCombo.getValue();
        List<String> waypointNames = getSelectedWaypointNames(startPointName, endPointName);
        int index = waypointNames.indexOf(pointName);
        return index < 0 ? -1 : index + 1;
    }

    private Point findPoint(String pointName) {
        return intermediatePoints.stream()
                .filter(p -> p.getName().equals(pointName))
                .findFirst()
                .orElse(null);
    }

    private double[] calculateSchematicPosition(int index, int totalCount) {
        int columns = (int) Math.ceil(Math.sqrt(totalCount));
        int rows = (int) Math.ceil((double) totalCount / columns);
        int row = index / columns;
        int column = index % columns;

        if (row % 2 == 1) {
            column = columns - 1 - column;
        }

        double usableWidth = CANVAS_DEFAULT_WIDTH - (2 * LAYOUT_MARGIN_X);
        double usableHeight = CANVAS_DEFAULT_HEIGHT - (2 * LAYOUT_MARGIN_Y);
        double xSpacing = columns <= 1 ? 0.0 : usableWidth / (columns - 1);
        double ySpacing = rows <= 1 ? 0.0 : usableHeight / (rows - 1);

        double x = columns <= 1 ? CANVAS_DEFAULT_WIDTH / 2.0 : LAYOUT_MARGIN_X + (column * xSpacing);
        double y = rows <= 1 ? CANVAS_DEFAULT_HEIGHT / 2.0 : LAYOUT_MARGIN_Y + (row * ySpacing);
        return new double[]{x, y};
    }

    /*
     * Draws a deterministic schematic route view for the current start,
     * waypoint, and destination selections.
     */
    private void drawMap() {
        if (mapCanvas == null || intermediatePoints == null) {
            return;
        }

        GraphicsContext gc = mapCanvas.getGraphicsContext2D();
        double width = mapCanvas.getWidth();
        double height = mapCanvas.getHeight();

        gc.clearRect(0, 0, width, height);
        gc.setFill(Color.rgb(248, 250, 252));
        gc.fillRect(0, 0, width, height);
        drawGridBackground(gc, width, height);

        String startPointName = startPointCombo.getValue();
        String endPointName = endPointCombo.getValue();

        if (startPointName == null || endPointName == null) {
            return;
        }

        List<String> selectedWaypointNames = getSelectedWaypointNames(startPointName, endPointName);
        List<Point> displayedRouteStops = createDisplayedRouteStops(
                startPointName,
                selectedWaypointNames,
                endPointName,
                width,
                height);

        if (displayedRouteStops.size() >= 2) {
            gc.setStroke(Color.rgb(37, 99, 235));
            gc.setLineWidth(4);
            gc.setLineDashes(null);
            gc.beginPath();
            gc.moveTo(displayedRouteStops.get(0).getX(), displayedRouteStops.get(0).getY());
            for (int i = 1; i < displayedRouteStops.size(); i++) {
                gc.lineTo(displayedRouteStops.get(i).getX(), displayedRouteStops.get(i).getY());
            }
            gc.stroke();
        }

        for (int i = 1; i < displayedRouteStops.size() - 1; i++) {
            drawPoint(
                    gc,
                    displayedRouteStops.get(i),
                    Color.rgb(250, 204, 21),
                    Color.rgb(161, 98, 7),
                    Color.rgb(30, 41, 59),
                    String.valueOf(i));
        }

        if (!displayedRouteStops.isEmpty()) {
            Point startPoint = displayedRouteStops.get(0);
            drawPoint(gc, startPoint, Color.rgb(34, 197, 94), Color.rgb(21, 128, 61), Color.rgb(15, 23, 42), "S");
        }

        if (displayedRouteStops.size() >= 2) {
            Point endPoint = displayedRouteStops.get(displayedRouteStops.size() - 1);
            drawPoint(gc, endPoint, Color.rgb(239, 68, 68), Color.rgb(185, 28, 28), Color.rgb(15, 23, 42), "E");
        }

        drawLegend(gc);
    }

    private List<Point> createDisplayedRouteStops(
            String startPointName,
            List<String> selectedWaypointNames,
            String endPointName,
            double canvasWidth,
            double canvasHeight) {
        List<String> routeNames = new ArrayList<>();
        addRouteStopIfPresent(routeNames, startPointName);
        selectedWaypointNames.forEach(name -> addRouteStopIfPresent(routeNames, name));
        addRouteStopIfPresent(routeNames, endPointName);

        List<double[]> positions = calculateRandomRoutePositions(routeNames, canvasWidth, canvasHeight);
        List<Point> displayedStops = new ArrayList<>();
        for (int i = 0; i < routeNames.size(); i++) {
            double[] position = positions.get(i);
            displayedStops.add(new Point(routeNames.get(i), position[0], position[1]));
        }
        return displayedStops;
    }

    private List<double[]> calculateRandomRoutePositions(
            List<String> routeNames,
            double canvasWidth,
            double canvasHeight) {
        double width = canvasWidth > 0 ? canvasWidth : CANVAS_DEFAULT_WIDTH;
        double height = canvasHeight > 0 ? canvasHeight : CANVAS_DEFAULT_HEIGHT;
        double left = LAYOUT_MARGIN_X;
        double right = Math.max(left + 80, width - LAYOUT_MARGIN_X);
        double top = LAYOUT_MARGIN_Y;
        double bottom = Math.max(top + 80, height - LAYOUT_MARGIN_Y);
        double minDistance = minimumRoutePointDistance(routeNames.size(), right - left, bottom - top);
        Random random = new Random(routeLayoutSeed(routeNames));
        List<double[]> positions = new ArrayList<>();

        for (int i = 0; i < routeNames.size(); i++) {
            double[] bestCandidate = null;
            double bestScore = -1.0;
            for (int attempt = 0; attempt < 900; attempt++) {
                double x = left + random.nextDouble() * (right - left);
                double y = top + random.nextDouble() * (bottom - top);
                if (overlapsLegendArea(x, y) && routeNames.size() <= 18) {
                    continue;
                }

                double nearestDistance = nearestDistance(positions, x, y);
                double edgeDistance = Math.min(
                        Math.min(x - left, right - x),
                        Math.min(y - top, bottom - y));
                double score = nearestDistance + (edgeDistance * 0.12) + random.nextDouble();
                if (score > bestScore) {
                    bestScore = score;
                    bestCandidate = new double[]{x, y};
                }
                if (nearestDistance >= minDistance) {
                    positions.add(new double[]{x, y});
                    bestCandidate = null;
                    break;
                }
            }
            if (bestCandidate != null) {
                positions.add(bestCandidate);
            }
        }

        return positions;
    }

    private long routeLayoutSeed(List<String> routeNames) {
        long seed = 1469598103934665603L;
        for (String routeName : routeNames) {
            seed ^= routeName.hashCode();
            seed *= 1099511628211L;
        }
        return seed;
    }

    private double minimumRoutePointDistance(int pointCount, double width, double height) {
        if (pointCount <= 1) {
            return 0.0;
        }
        double areaBasedDistance = Math.sqrt((width * height) / pointCount) * 0.78;
        double upperLimit = pointCount <= 6 ? 145.0 : pointCount <= 12 ? 115.0 : pointCount <= 20 ? 92.0 : 70.0;
        double lowerLimit = pointCount <= 12 ? 86.0 : 58.0;
        return Math.max(lowerLimit, Math.min(upperLimit, areaBasedDistance));
    }

    private double nearestDistance(List<double[]> positions, double x, double y) {
        if (positions.isEmpty()) {
            return Double.MAX_VALUE;
        }
        double nearest = Double.MAX_VALUE;
        for (double[] position : positions) {
            double dx = x - position[0];
            double dy = y - position[1];
            nearest = Math.min(nearest, Math.hypot(dx, dy));
        }
        return nearest;
    }

    private boolean overlapsLegendArea(double x, double y) {
        return x < 175.0 && y < 125.0;
    }

    private void addRouteStopIfPresent(List<String> routeNames, String pointName) {
        if (pointName == null || routeNames.contains(pointName) || findPoint(pointName) == null) {
            return;
        }
        routeNames.add(pointName);
    }

    private void drawGridBackground(GraphicsContext gc, double width, double height) {
        gc.setStroke(Color.rgb(226, 232, 240));
        gc.setLineWidth(1);
        for (double x = LAYOUT_MARGIN_X; x <= width - LAYOUT_MARGIN_X + 1; x += 42) {
            gc.strokeLine(x, LAYOUT_MARGIN_Y / 2, x, height - (LAYOUT_MARGIN_Y / 2));
        }
        for (double y = LAYOUT_MARGIN_Y; y <= height - LAYOUT_MARGIN_Y + 1; y += 42) {
            gc.strokeLine(LAYOUT_MARGIN_X / 2, y, width - (LAYOUT_MARGIN_X / 2), y);
        }
    }

    private void drawPoint(
            GraphicsContext gc,
            Point point,
            Color fill,
            Color stroke,
            Color labelColor,
            String markerText) {
        double radius = markerText == null ? 8.0 : 12.0;
        double x = point.getX();
        double y = point.getY();

        gc.setFill(Color.rgb(255, 255, 255, 0.75));
        gc.fillRoundRect(x - 27, y + 13, 54, 20, 6, 6);

        gc.setFill(fill);
        gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(3);
        gc.strokeOval(x - radius, y - radius, radius * 2, radius * 2);
        gc.setStroke(stroke);
        gc.setLineWidth(1.5);
        gc.strokeOval(x - radius, y - radius, radius * 2, radius * 2);

        if (markerText != null) {
            gc.setFill(Color.WHITE);
            gc.fillText(markerText, x - 4, y + 5);
        }

        gc.setFill(labelColor);
        gc.fillText(point.getName(), x - 18, y + 27);
    }

    private void drawLegend(GraphicsContext gc) {
        gc.setFont(javafx.scene.text.Font.font("Arial", 12));

        gc.setFill(Color.GREEN);
        gc.fillOval(10, 20, 15, 15);
        gc.setFill(Color.BLACK);
        gc.fillText("Start Point", 30, 32);

        gc.setFill(Color.RED);
        gc.fillOval(10, 45, 15, 15);
        gc.setFill(Color.BLACK);
        gc.fillText("End Point", 30, 57);

        gc.setFill(Color.GOLD);
        gc.fillOval(10, 70, 15, 15);
        gc.setFill(Color.BLACK);
        gc.fillText("Waypoint", 30, 82);

        gc.setStroke(Color.DODGERBLUE);
        gc.setLineWidth(3);
        gc.beginPath();
        gc.moveTo(10, 100);
        gc.lineTo(25, 100);
        gc.stroke();
        gc.setFill(Color.BLACK);
        gc.fillText("Selected stop order", 30, 105);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
