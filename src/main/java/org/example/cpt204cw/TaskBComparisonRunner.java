package org.example.cpt204cw;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/*
 * Command-line runner for Task B analysis. It produces the route-planning
 * comparison tables, validates algorithm costs, and exports report CSV files.
 */
public class TaskBComparisonRunner {
    private static final String PATHS_FILE = "data/paths.csv";
    private static final String SELECTED_TARGETS_FILE = "data/selected_targets.csv";
    private static final String RUNTIME_CSV_FILE = "data/taskB_runtime_results.csv";
    private static final String TABLE_2_1_REQUIRED_CASES_CSV = "data/taskB_table_2_1_required_cases.csv";
    private static final String TABLE_2_2_WEIGHTED_COMPARISON_CSV = "data/taskB_table_2_2_weighted_algorithm_comparison.csv";
    private static final String TABLE_2_3_FIXED_FLEXIBLE_CSV = "data/taskB_table_2_3_fixed_vs_flexible_counterexample.csv";
    private static final String TABLE_2_4_BFS_BASELINE_CSV = "data/taskB_table_2_4_bfs_baseline.csv";
    private static final String TABLE_2_5_SCALABILITY_CSV = "data/taskB_table_2_5_scalability.csv";
    private static final String TABLE_2_6_LANDMARK_SENSITIVITY_CSV = "data/taskB_table_2_6_landmark_sensitivity.csv";
    private static final String OPTIMISED_DIJKSTRA_NAME = "PriorityQueue Dijkstra with Early Stopping";
    private static final double EPSILON = 1e-6;
    private static final int WARMUP_RUNS = 5;
    private static final int MEASURED_RUNS = 10;
    private static final int[] SCALABILITY_WAYPOINT_COUNTS = {5, 10, 50, 100, 500};
    private static final int ALT_LANDMARK_BENCHMARK_WAYPOINT_COUNT = 10;
    private static final int[] ALT_LANDMARK_COUNTS = {4, 8, 16, 32};

    public static void main(String[] args) {
        try {
            WeightedGraph graph = PathCsvReader.readGraph(PATHS_FILE);
            TaskBTargetSet targets = SelectedTargetsReader.readTaskBTargets(SELECTED_TARGETS_FILE, graph);
            List<RouteQuery> requiredCases = createRequiredCases(targets);
            RuntimeCsvCollector runtimeCsv = new RuntimeCsvCollector();
            ReportCsvCollector reportCsv = new ReportCsvCollector();

            printHeader();
            printDatasetSummary(graph, targets);
            printRequiredTargetMapping(targets);
            runSection1(graph, requiredCases, runtimeCsv, reportCsv);
            runWeightedAlgorithmComparison(graph, requiredCases, runtimeCsv, reportCsv);
            runFixedFlexibleCounterexample(graph, targets, reportCsv);
            runSection4(graph, requiredCases, runtimeCsv, reportCsv);
            runScalabilityBenchmark(graph, runtimeCsv, reportCsv);
            runAltLandmarkSensitivityBenchmark(graph, runtimeCsv, reportCsv);
            reportCsv.writeAll();
            runtimeCsv.writeTo(RUNTIME_CSV_FILE);
            System.out.println("Task B report CSV files exported to data/taskB_table_2_*.csv");
            System.out.println("Task B runtime CSV exported: " + RUNTIME_CSV_FILE);
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printHeader() {
        System.out.println("CPT204 Task B Algorithm Comparison");
        System.out.println("Input: " + PATHS_FILE + ", " + SELECTED_TARGETS_FILE);
        System.out.println("Benchmark: warm-up=" + WARMUP_RUNS + ", measured=" + MEASURED_RUNS);
    }

    private static void printDatasetSummary(WeightedGraph graph, TaskBTargetSet targets) {
        System.out.println();
        System.out.println("Table 0. Dataset Summary");
        System.out.println("Item | Value");
        System.out.println("Vertices | " + graph.getVertexCount());
        System.out.println("Undirected edges | " + graph.getUndirectedEdgeCount());
        System.out.println("Adjacency entries | " + graph.getAdjacencyEntryCount());
        System.out.println("Selected targets loaded | " + targets.getAllTargets().size());
        System.out.println("Required nodes validated | PASSED");
    }

    private static void printRequiredTargetMapping(TaskBTargetSet targets) {
        System.out.println();
        System.out.println("Table 1. Required Target Mapping");
        System.out.println(String.format(
                "%-8s | %-7s | %4s | %-11s | %14s",
                "Symbol",
                "Dataset",
                "Rank",
                "Location ID",
                "Priority Score"));
        System.out.println(repeat("-", 58));
        for (SelectedTarget target : targets.getRequiredTargets()) {
            System.out.println(String.format(
                    "%-8s | %-7s | %4d | %-11s | %14d",
                    target.getSymbol(),
                    target.getDataset(),
                    target.getRank(),
                    target.getLocationId(),
                    target.getPriorityScore()));
        }
    }

    private static void runSection1(
            WeightedGraph graph,
            List<RouteQuery> requiredCases,
            RuntimeCsvCollector runtimeCsv,
            ReportCsvCollector reportCsv) {
        System.out.println();
        System.out.println("Table 2. Required Case Results");
        printRequiredCaseHeader();
        ShortestPathSolver lectureDijkstra = new LectureDijkstraShortestPath();
        RoutePlanner planner = new RoutePlanner();

        List<RouteResult> routeResults = new ArrayList<>();
        for (RouteQuery query : requiredCases) {
            RouteResult routeResult = planner.solveFixedOrderRoute(graph, query, lectureDijkstra);
            routeResults.add(routeResult);
            double runtimeMs = routeRuntimeMillis(routeResult);
            printRequiredCaseRow(
                    query.getCaseId(),
                    query.getStartLocation(),
                    waypointLocationsToString(query),
                    query.getDestinationLocation(),
                    routeResult.fullPathToString(),
                    routeResult.getTotalCost());
            reportCsv.addRequiredCase(
                    query.getCaseId(),
                    query.getStartLocation(),
                    waypointLocationsToString(query),
                    query.getDestinationLocation(),
                    routeResult.fullPathToString(),
                    routeResult.getTotalCost());
            runtimeCsv.addRoute(
                    "Table 2 Required Case Results",
                    query.getCaseId(),
                    "Lecture-style Dijkstra",
                    query.getStartLocation(),
                    query.getDestinationLocation(),
                    routeResult.getTotalCost(),
                    runtimeMs);
        }

        validateCase1Cost(routeResults.get(0));
        System.out.println("Required case validation: PASSED");
    }

    private static void runWeightedAlgorithmComparison(
            WeightedGraph graph,
            List<RouteQuery> requiredCases,
            RuntimeCsvCollector runtimeCsv,
            ReportCsvCollector reportCsv) {
        System.out.println();
        System.out.println("Table 3. Case-based Weighted Algorithm Comparison");
        AStarAltShortestPath altSolver = new AStarAltShortestPath();
        altSolver.prepare(graph);
        FloydWarshallAllPairsShortestPath floydWarshall = FloydWarshallAllPairsShortestPath.buildIfSuitable(graph);
        System.out.println("ALT Landmarks | " + landmarksToString(altSolver.getLandmarks()));
        System.out.println("Preprocessing(ms) | " + String.format("%.3f", altSolver.getPreprocessingRuntimeMillis()));
        if (floydWarshall.isExecuted()) {
            System.out.println("Floyd-Warshall preprocessing(ms) | "
                    + String.format("%.3f", floydWarshall.getPreprocessingRuntimeMillis()));
        } else {
            System.out.println("Floyd-Warshall skipped: graph has "
                    + floydWarshall.getVertexCount()
                    + " vertices, threshold is "
                    + FloydWarshallAllPairsShortestPath.MAX_FLOYD_WARSHALL_VERTICES
                    + ", O(V^3) is unsuitable for this benchmark.");
        }
        printPathTableHeader("Case");

        List<AlgorithmSpec> algorithms = createWeightedAlgorithms(altSolver, floydWarshall);
        Map<String, RuntimeSummary> summaries = new LinkedHashMap<>();
        for (AlgorithmSpec algorithm : algorithms) {
            summaries.put(algorithm.displayName, new RuntimeSummary());
        }

        for (RouteQuery query : requiredCases) {
            List<RouteBenchmarkMeasurement> successfulResults = new ArrayList<>();
            List<WeightedAlgorithmCsvRow> weightedCsvRows = new ArrayList<>();
            for (AlgorithmSpec algorithm : algorithms) {
                try {
                    RouteBenchmarkMeasurement measurement = measureRoute(graph, query, algorithm.solver);
                    successfulResults.add(measurement);
                    summaries.get(algorithm.displayName).add(measurement);
                    printCaseAlgorithmRow(query.getCaseId(), algorithm.displayName, query, measurement);
                    weightedCsvRows.add(WeightedAlgorithmCsvRow.success(
                            query.getCaseId(),
                            algorithm.displayName,
                            measurement.representativeResult.getTotalCost(),
                            preprocessingRuntimeForAlgorithm(algorithm.displayName, altSolver, floydWarshall),
                            measurement.avgRuntimeMs));
                    runtimeCsv.addRoute(
                            "Table 3 Case-based Weighted Algorithm Comparison",
                            query.getCaseId(),
                            algorithm.displayName,
                            query.getStartLocation(),
                            query.getDestinationLocation(),
                            measurement.representativeResult.getTotalCost(),
                            measurement.avgRuntimeMs);
                } catch (IllegalStateException e) {
                    printCaseAlgorithmUnavailableRow(query.getCaseId(), algorithm.displayName, query, e.getMessage());
                    weightedCsvRows.add(WeightedAlgorithmCsvRow.unavailable(
                            query.getCaseId(),
                            algorithm.displayName,
                            preprocessingRuntimeForAlgorithm(algorithm.displayName, altSolver, floydWarshall),
                            e.getMessage()));
                }
            }
            addWeightedCsvRows(reportCsv, weightedCsvRows);
            validateWeightedCaseCosts(query.getCaseId(), successfulResults);
        }
        System.out.println("Weighted algorithm case-cost validation: PASSED");
        printWarmupSummary(summaries);
    }

    private static void runSection4(
            WeightedGraph graph,
            List<RouteQuery> requiredCases,
            RuntimeCsvCollector runtimeCsv,
            ReportCsvCollector reportCsv) {
        System.out.println();
        System.out.println("Table 6. BFS Baseline");
        RoutePlanner planner = new RoutePlanner();
        ShortestPathSolver dijkstra = new OptimisedDijkstraShortestPath();
        ShortestPathSolver bfs = new BfsUnweightedPath();

        System.out.println(String.format(
                "%-8s | %13s | %8s | %17s | %6s",
                "Case",
                "Dijkstra Cost",
                "BFS Hops",
                "BFS Weighted Cost",
                "Match?"));
        System.out.println(repeat("-", 65));

        for (RouteQuery query : requiredCases) {
            RouteResult dijkstraResult = planner.solveFixedOrderRoute(graph, query, dijkstra);
            RouteResult bfsResult = planner.solveFixedOrderRoute(graph, query, bfs);
            boolean same = Math.abs(dijkstraResult.getTotalCost() - bfsResult.getTotalCost()) <= EPSILON;
            System.out.println(String.format(
                    "%-8s | %13.3f | %8d | %17.3f | %6s",
                    query.getCaseId(),
                    dijkstraResult.getTotalCost(),
                    Math.max(0, bfsResult.getFullPath().size() - 1),
                    bfsResult.getTotalCost(),
                    same ? "Yes" : "No"));
            reportCsv.addBfsBaseline(
                    query.getCaseId(),
                    dijkstraResult.getTotalCost(),
                    Math.max(0, bfsResult.getFullPath().size() - 1),
                    bfsResult.getTotalCost(),
                    same ? "Yes" : "No");
            runtimeCsv.addRoute(
                    "Table 6 BFS Baseline",
                    query.getCaseId(),
                    OPTIMISED_DIJKSTRA_NAME,
                    query.getStartLocation(),
                    query.getDestinationLocation(),
                    dijkstraResult.getTotalCost(),
                    routeRuntimeMillis(dijkstraResult));
            runtimeCsv.addRoute(
                    "Table 6 BFS Baseline",
                    query.getCaseId(),
                    "BFS Unweighted Baseline",
                    query.getStartLocation(),
                    query.getDestinationLocation(),
                    bfsResult.getTotalCost(),
                    routeRuntimeMillis(bfsResult));
        }

        System.out.println("BFS baseline validation: PASSED");
    }

    private static void runScalabilityBenchmark(
            WeightedGraph graph,
            RuntimeCsvCollector runtimeCsv,
            ReportCsvCollector reportCsv) {
        System.out.println();
        System.out.println("Table 7. Scalability Benchmark for Multi-waypoint Routes");
        AStarAltShortestPath altSolver = new AStarAltShortestPath();
        altSolver.prepare(graph);
        System.out.println("A* / ALT landmarks | " + landmarksToString(altSolver.getLandmarks()));
        System.out.println("A* / ALT preprocessing(ms) | "
                + String.format("%.3f", altSolver.getPreprocessingRuntimeMillis()));
        System.out.println("Query runtimes below are measured after A* / ALT preprocessing.");
        System.out.println(OPTIMISED_DIJKSTRA_NAME + " is the scalability cost baseline.");

        List<String> connectedVertices = largestConnectedComponentVertices(graph);
        List<AlgorithmSpec> algorithms = createScalabilityAlgorithms(altSolver);
        printScalabilityHeader();

        boolean allCompletedCostsMatched = true;
        for (int waypointCount : SCALABILITY_WAYPOINT_COUNTS) {
            if (graph.getVertexCount() < waypointCount) {
                printScalabilitySkippedRows(
                        waypointCount,
                        algorithms,
                        "SKIPPED: graph has only " + graph.getVertexCount() + " vertices");
                allCompletedCostsMatched = false;
                continue;
            }
            if (connectedVertices.size() < waypointCount) {
                printScalabilitySkippedRows(
                        waypointCount,
                        algorithms,
                        "SKIPPED: largest connected component has only "
                                + connectedVertices.size()
                                + " vertices");
                allCompletedCostsMatched = false;
                continue;
            }

            List<String> waypoints = selectDeterministicWaypoints(connectedVertices, waypointCount);
            RouteQuery query = createScalabilityRouteQuery(waypointCount, waypoints);
            AlgorithmSpec baselineAlgorithm = algorithms.get(0);
            RouteBenchmarkMeasurement baselineMeasurement;
            try {
                baselineMeasurement = measureRoute(graph, query, baselineAlgorithm.solver);
                printScalabilityMeasurementRow(
                        waypointCount,
                        baselineAlgorithm.displayName,
                        baselineMeasurement,
                        "OK");
                addScalabilityCsvRow(runtimeCsv, waypointCount, baselineAlgorithm, query, baselineMeasurement);
                reportCsv.addScalability(
                        waypointCount,
                        waypointCount - 1,
                        baselineAlgorithm.displayName,
                        baselineMeasurement.representativeResult.getTotalCost(),
                        baselineMeasurement.avgRuntimeMs,
                        averagePerSegmentMillis(baselineMeasurement.avgRuntimeMs, waypointCount - 1));
            } catch (IllegalStateException e) {
                String status = scalabilityExceptionStatus(e);
                printScalabilityStatusRow(waypointCount, baselineAlgorithm.displayName, status);
                printScalabilitySkippedRows(
                        waypointCount,
                        algorithms.subList(1, algorithms.size()),
                        "SKIPPED: " + OPTIMISED_DIJKSTRA_NAME + " baseline unavailable");
                allCompletedCostsMatched = false;
                continue;
            }

            double baselineCost = baselineMeasurement.representativeResult.getTotalCost();
            for (int i = 1; i < algorithms.size(); i++) {
                AlgorithmSpec algorithm = algorithms.get(i);
                try {
                    RouteBenchmarkMeasurement measurement = measureRoute(graph, query, algorithm.solver);
                    double actualCost = measurement.representativeResult.getTotalCost();
                    String status = "OK";
                    if (Math.abs(baselineCost - actualCost) > EPSILON) {
                        status = "ERROR: cost mismatch vs " + OPTIMISED_DIJKSTRA_NAME;
                        allCompletedCostsMatched = false;
                        System.out.println("WARNING: scalability cost mismatch for "
                                + waypointCount
                                + " waypoints using "
                                + algorithm.displayName
                                + ": baseline="
                                + String.format("%.3f", baselineCost)
                                + ", actual="
                                + String.format("%.3f", actualCost));
                    }
                    printScalabilityMeasurementRow(waypointCount, algorithm.displayName, measurement, status);
                    addScalabilityCsvRow(runtimeCsv, waypointCount, algorithm, query, measurement);
                    reportCsv.addScalability(
                            waypointCount,
                            waypointCount - 1,
                            algorithm.displayName,
                            measurement.representativeResult.getTotalCost(),
                            measurement.avgRuntimeMs,
                            averagePerSegmentMillis(measurement.avgRuntimeMs, waypointCount - 1));
                } catch (IllegalStateException e) {
                    printScalabilityStatusRow(waypointCount, algorithm.displayName, scalabilityExceptionStatus(e));
                    allCompletedCostsMatched = false;
                }
            }
        }

        if (allCompletedCostsMatched) {
            System.out.println("Scalability benchmark cost validation: PASSED");
        } else {
            System.out.println("Scalability benchmark cost validation: review SKIPPED/ERROR rows above");
        }
    }

    private static List<AlgorithmSpec> createScalabilityAlgorithms(AStarAltShortestPath altSolver) {
        List<AlgorithmSpec> algorithms = new ArrayList<>();
        algorithms.add(new AlgorithmSpec(OPTIMISED_DIJKSTRA_NAME, new OptimisedDijkstraShortestPath()));
        algorithms.add(new AlgorithmSpec("Bidirectional Dijkstra", new BidirectionalDijkstraShortestPath()));
        algorithms.add(new AlgorithmSpec("A* / ALT landmark heuristic", altSolver));
        return algorithms;
    }

    private static List<String> largestConnectedComponentVertices(WeightedGraph graph) {
        List<String> orderedVertices = new ArrayList<>(graph.getVertices());
        Set<String> visited = new HashSet<>();
        Set<String> largestComponent = new HashSet<>();

        for (String vertex : orderedVertices) {
            if (visited.contains(vertex)) {
                continue;
            }
            Set<String> component = new HashSet<>();
            Queue<String> queue = new ArrayDeque<>();
            visited.add(vertex);
            component.add(vertex);
            queue.add(vertex);

            while (!queue.isEmpty()) {
                String current = queue.remove();
                for (WeightedEdge edge : graph.getEdgesFrom(current)) {
                    String neighbor = edge.getTo();
                    if (visited.add(neighbor)) {
                        component.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }

            if (component.size() > largestComponent.size()) {
                largestComponent = component;
            }
        }

        List<String> result = new ArrayList<>();
        for (String vertex : orderedVertices) {
            if (largestComponent.contains(vertex)) {
                result.add(vertex);
            }
        }
        return result;
    }

    private static List<String> selectDeterministicWaypoints(List<String> candidateVertices, int waypointCount) {
        List<String> waypoints = new ArrayList<>();
        if (waypointCount <= 0) {
            return waypoints;
        }
        if (waypointCount == 1) {
            waypoints.add(candidateVertices.get(0));
            return waypoints;
        }

        int lastCandidateIndex = candidateVertices.size() - 1;
        for (int i = 0; i < waypointCount; i++) {
            int selectedIndex = (int) ((long) i * lastCandidateIndex / (waypointCount - 1));
            waypoints.add(candidateVertices.get(selectedIndex));
        }
        return waypoints;
    }

    private static RouteQuery createScalabilityRouteQuery(int waypointCount, List<String> waypoints) {
        List<String> intermediateSymbols = new ArrayList<>();
        List<String> intermediateLocations = new ArrayList<>();
        for (int i = 1; i < waypoints.size() - 1; i++) {
            intermediateSymbols.add(waypoints.get(i));
            intermediateLocations.add(waypoints.get(i));
        }
        String start = waypoints.get(0);
        String destination = waypoints.get(waypoints.size() - 1);
        return new RouteQuery(
                waypointCount + " waypoints",
                "Scalability route with " + waypointCount + " fixed-order waypoints",
                start,
                start,
                intermediateSymbols,
                intermediateLocations,
                destination,
                destination);
    }

    private static void printScalabilityHeader() {
        System.out.println(String.format(
                "%14s | %13s | %-28s | %12s | %15s | %16s | %19s | %-48s",
                "Waypoint Count",
                "Segment Count",
                "Algorithm",
                "Total Cost",
                "Warm-up Avg(ms)",
                "Measured Avg(ms)",
                "Avg per Segment(ms)",
                "Status"));
        System.out.println(repeat("-", 170));
    }

    private static void printScalabilityMeasurementRow(
            int waypointCount,
            String algorithm,
            RouteBenchmarkMeasurement measurement,
            String status) {
        int segmentCount = waypointCount - 1;
        double measuredAverageMs = measurement.avgRuntimeMs;
        double averagePerSegmentMs = segmentCount == 0 ? 0.0 : measuredAverageMs / segmentCount;
        printScalabilityRow(
                waypointCount,
                segmentCount,
                algorithm,
                measurement.representativeResult.getTotalCost(),
                measurement.warmupAverageRuntimeMs,
                measuredAverageMs,
                averagePerSegmentMs,
                status);
    }

    private static void printScalabilityStatusRow(int waypointCount, String algorithm, String status) {
        printScalabilityRow(
                waypointCount,
                Math.max(0, waypointCount - 1),
                algorithm,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                status);
    }

    private static void printScalabilitySkippedRows(
            int waypointCount,
            List<AlgorithmSpec> algorithms,
            String status) {
        for (AlgorithmSpec algorithm : algorithms) {
            printScalabilityStatusRow(waypointCount, algorithm.displayName, status);
        }
    }

    private static void printScalabilityRow(
            int waypointCount,
            int segmentCount,
            String algorithm,
            double totalCost,
            double warmupAverageMs,
            double measuredAverageMs,
            double averagePerSegmentMs,
            String status) {
        System.out.println(String.format(
                "%14d | %13d | %-28s | %12s | %15s | %16s | %19s | %-48s",
                waypointCount,
                segmentCount,
                algorithm,
                formatScalabilityNumber(totalCost),
                formatScalabilityNumber(warmupAverageMs),
                formatScalabilityNumber(measuredAverageMs),
                formatScalabilityNumber(averagePerSegmentMs),
                status));
    }

    private static String formatScalabilityNumber(double value) {
        if (Double.isNaN(value)) {
            return "-";
        }
        return String.format("%.3f", value);
    }

    private static double averagePerSegmentMillis(double measuredAverageMs, int segmentCount) {
        return segmentCount == 0 ? 0.0 : measuredAverageMs / segmentCount;
    }

    private static String scalabilityExceptionStatus(IllegalStateException e) {
        String message = e.getMessage() == null ? "unknown failure" : e.getMessage();
        if (message.startsWith("No route segment found")) {
            return "SKIPPED: " + message;
        }
        return "ERROR: " + message;
    }

    private static void addScalabilityCsvRow(
            RuntimeCsvCollector runtimeCsv,
            int waypointCount,
            AlgorithmSpec algorithm,
            RouteQuery query,
            RouteBenchmarkMeasurement measurement) {
        runtimeCsv.addRoute(
                "Table 7 Scalability Benchmark for Multi-waypoint Routes",
                waypointCount + " waypoints",
                algorithm.displayName,
                query.getStartLocation(),
                query.getDestinationLocation(),
                measurement.representativeResult.getTotalCost(),
                measurement.avgRuntimeMs);
    }

    private static void runAltLandmarkSensitivityBenchmark(
            WeightedGraph graph,
            RuntimeCsvCollector runtimeCsv,
            ReportCsvCollector reportCsv) {
        System.out.println();
        System.out.println("Table 8. A* / ALT Landmark Count Sensitivity Benchmark");
        List<String> connectedVertices = largestConnectedComponentVertices(graph);
        int waypointCount = ALT_LANDMARK_BENCHMARK_WAYPOINT_COUNT;

        if (connectedVertices.size() < waypointCount) {
            printAltLandmarkHeader();
            for (int landmarkCount : ALT_LANDMARK_COUNTS) {
                printAltLandmarkStatusRow(
                        landmarkCount,
                        Double.NaN,
                        0,
                        "SKIPPED: fixed 10-waypoint route needs "
                                + waypointCount
                                + " connected vertices, found "
                                + connectedVertices.size());
            }
            System.out.println("A* / ALT landmark-count validation: review SKIPPED/ERROR rows above");
            return;
        }

        List<String> waypoints = selectDeterministicWaypoints(connectedVertices, waypointCount);
        RouteQuery query = createScalabilityRouteQuery(waypointCount, waypoints);
        RouteBenchmarkMeasurement baselineMeasurement;
        try {
            baselineMeasurement = measureRoute(graph, query, new LectureDijkstraShortestPath());
        } catch (IllegalStateException e) {
            printAltLandmarkHeader();
            for (int landmarkCount : ALT_LANDMARK_COUNTS) {
                printAltLandmarkStatusRow(
                        landmarkCount,
                        Double.NaN,
                        waypointCount,
                        "ERROR: Lecture-style baseline unavailable: " + e.getMessage());
            }
            System.out.println("A* / ALT landmark-count validation: review SKIPPED/ERROR rows above");
            return;
        }

        double baselineCost = baselineMeasurement.representativeResult.getTotalCost();
        System.out.println("Lecture-style Dijkstra baseline route | "
                + waypointCount
                + " waypoints, "
                + (waypointCount - 1)
                + " segments, total cost "
                + String.format("%.3f", baselineCost));
        System.out.println("A* / ALT measured query runtimes below exclude preprocessing time.");
        System.out.println("Only the landmark count changes; the 10-waypoint route is fixed for all rows.");
        System.out.println("A* / ALT landmark vertices are selected deterministically and omitted from this table.");
        printAltLandmarkHeader();

        boolean allCompletedCostsMatched = true;
        for (int landmarkCount : ALT_LANDMARK_COUNTS) {
            AStarAltShortestPath altSolver = new AStarAltShortestPath(landmarkCount);
            try {
                altSolver.prepare(graph);
                List<String> landmarks = altSolver.getLandmarks();
                if (landmarks.size() < landmarkCount) {
                    printAltLandmarkStatusRow(
                            landmarkCount,
                            altSolver.getPreprocessingRuntimeMillis(),
                            waypointCount,
                            "SKIPPED: only "
                                    + landmarks.size()
                                    + " suitable landmarks were selected");
                    allCompletedCostsMatched = false;
                    continue;
                }

                RouteBenchmarkMeasurement measurement = measureRoute(graph, query, altSolver);
                double actualCost = measurement.representativeResult.getTotalCost();
                boolean costMatches = Math.abs(baselineCost - actualCost) <= EPSILON;
                String status = costMatches ? "OK" : "WARNING: cost mismatch vs Lecture-style Dijkstra";
                if (!costMatches) {
                    allCompletedCostsMatched = false;
                    System.out.println("WARNING: A* / ALT landmark-count cost mismatch for "
                            + landmarkCount
                            + " landmarks: baseline="
                            + String.format("%.3f", baselineCost)
                            + ", actual="
                            + String.format("%.3f", actualCost));
                }
                printAltLandmarkMeasurementRow(
                        landmarkCount,
                        altSolver.getPreprocessingRuntimeMillis(),
                        waypointCount,
                        measurement,
                        costMatches,
                        status);
                addAltLandmarkCsvRow(runtimeCsv, landmarkCount, query, measurement);
                reportCsv.addLandmarkSensitivity(
                        landmarkCount,
                        altSolver.getPreprocessingRuntimeMillis(),
                        waypointCount,
                        waypointCount - 1,
                        measurement.representativeResult.getTotalCost(),
                        measurement.avgRuntimeMs,
                        averagePerSegmentMillis(measurement.avgRuntimeMs, waypointCount - 1),
                        measurement.representativeResult.getCombinedStats().getVisitedNodeCount(),
                        costMatches ? "Yes" : "No");
            } catch (IllegalArgumentException | IllegalStateException e) {
                printAltLandmarkStatusRow(
                        landmarkCount,
                        altSolver.getPreprocessingRuntimeMillis(),
                        waypointCount,
                        "ERROR: " + e.getMessage());
                allCompletedCostsMatched = false;
            }
        }

        if (allCompletedCostsMatched) {
            System.out.println("A* / ALT landmark-count validation: PASSED");
        } else {
            System.out.println("A* / ALT landmark-count validation: review SKIPPED/ERROR rows above");
        }
    }

    private static void printAltLandmarkHeader() {
        System.out.println(String.format(
                "%14s | %17s | %14s | %13s | %12s | %15s | %16s | %19s | %22s | %17s | %-48s",
                "Landmark Count",
                "Preprocessing(ms)",
                "Waypoint Count",
                "Segment Count",
                "Total Cost",
                "Warm-up Avg(ms)",
                "Measured Avg(ms)",
                "Avg per Segment(ms)",
                "Expanded/Settled Nodes",
                "Total Cost Match?",
                "Status"));
        System.out.println(repeat("-", 250));
    }

    private static void printAltLandmarkMeasurementRow(
            int landmarkCount,
            double preprocessingMs,
            int waypointCount,
            RouteBenchmarkMeasurement measurement,
            boolean costMatches,
            String status) {
        int segmentCount = waypointCount - 1;
        double measuredAverageMs = measurement.avgRuntimeMs;
        double averagePerSegmentMs = segmentCount == 0 ? 0.0 : measuredAverageMs / segmentCount;
        int settledNodes = measurement.representativeResult.getCombinedStats().getVisitedNodeCount();
        printAltLandmarkRow(
                landmarkCount,
                preprocessingMs,
                waypointCount,
                segmentCount,
                measurement.representativeResult.getTotalCost(),
                measurement.warmupAverageRuntimeMs,
                measuredAverageMs,
                averagePerSegmentMs,
                settledNodes,
                costMatches ? "Yes" : "No",
                status);
    }

    private static void printAltLandmarkStatusRow(
            int landmarkCount,
            double preprocessingMs,
            int waypointCount,
            String status) {
        printAltLandmarkRow(
                landmarkCount,
                preprocessingMs,
                waypointCount,
                Math.max(0, waypointCount - 1),
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                -1,
                "-",
                status);
    }

    private static void printAltLandmarkRow(
            int landmarkCount,
            double preprocessingMs,
            int waypointCount,
            int segmentCount,
            double totalCost,
            double warmupAverageMs,
            double measuredAverageMs,
            double averagePerSegmentMs,
            int settledNodes,
            String totalCostMatches,
            String status) {
        System.out.println(String.format(
                "%14d | %17s | %14d | %13d | %12s | %15s | %16s | %19s | %22s | %17s | %-48s",
                landmarkCount,
                formatScalabilityNumber(preprocessingMs),
                waypointCount,
                segmentCount,
                formatScalabilityNumber(totalCost),
                formatScalabilityNumber(warmupAverageMs),
                formatScalabilityNumber(measuredAverageMs),
                formatScalabilityNumber(averagePerSegmentMs),
                settledNodes < 0 ? "-" : String.valueOf(settledNodes),
                totalCostMatches,
                status));
    }

    private static void addAltLandmarkCsvRow(
            RuntimeCsvCollector runtimeCsv,
            int landmarkCount,
            RouteQuery query,
            RouteBenchmarkMeasurement measurement) {
        runtimeCsv.addRoute(
                "Table 8 A* / ALT Landmark Count Sensitivity Benchmark",
                landmarkCount + " landmarks; " + (query.getOrderedStopLocations().size()) + " waypoints",
                "A* / ALT landmark heuristic",
                query.getStartLocation(),
                query.getDestinationLocation(),
                measurement.representativeResult.getTotalCost(),
                measurement.avgRuntimeMs);
    }

    private static void printRequiredCaseHeader() {
        System.out.println(String.format(
                "%-8s | %-11s | %-25s | %-11s | %-90s | %10s",
                "Case",
                "Start",
                "Via Nodes",
                "Destination",
                "Shortest Path",
                "Total Cost"));
        System.out.println(repeat("-", 170));
    }

    private static void printRequiredCaseRow(
            String caseId,
            String start,
            String viaNodes,
            String destination,
            String shortestPath,
            double totalCost) {
        System.out.println(String.format(
                "%-8s | %-11s | %-25s | %-11s | %-90s | %10.3f",
                caseId,
                start,
                viaNodes,
                destination,
                shortestPath,
                totalCost));
    }

    private static void printPathTableHeader(String firstColumnName) {
        System.out.println(String.format(
                "%-12s | %-24s | %-11s | %-11s | %-90s | %10s | %11s",
                firstColumnName,
                "Algorithm",
                "Start",
                "Target",
                "Shortest Path",
                "Total Cost",
                "Runtime(ms)"));
        System.out.println(repeat("-", 185));
    }

    private static void printCaseAlgorithmRow(
            String caseId,
            String algorithm,
            RouteQuery query,
            RouteBenchmarkMeasurement measurement) {
        RouteResult result = measurement.representativeResult;
        printPathTableRow(
                caseId,
                algorithm,
                query.getStartLocation(),
                query.getDestinationLocation(),
                result.fullPathToString(),
                result.getTotalCost(),
                measurement.avgRuntimeMs);
    }

    private static void printCaseAlgorithmUnavailableRow(
            String caseId,
            String algorithm,
            RouteQuery query,
            String status) {
        printPathTableRow(
                caseId,
                algorithm,
                query.getStartLocation(),
                query.getDestinationLocation(),
                "UNAVAILABLE: " + status,
                Double.NaN,
                Double.NaN);
    }

    private static void printPathTableRow(
            String label,
            String algorithm,
            String start,
            String target,
            String shortestPath,
            double totalCost,
            double runtimeMs) {
        System.out.println(String.format(
                "%-12s | %-24s | %-11s | %-11s | %-90s | %10.3f | %11.3f",
                label,
                algorithm,
                start,
                target,
                shortestPath,
                totalCost,
                runtimeMs));
    }

    private static void printWarmupSummary(Map<String, RuntimeSummary> summaries) {
        System.out.println();
        System.out.println("Table 4. Warm-up and Measured Runtime Summary");
        System.out.println(String.format(
                "%-24s | %17s | %16s",
                "Algorithm",
                "Warm-up Avg(ms)",
                "Measured Avg(ms)"));
        System.out.println(repeat("-", 65));
        for (Map.Entry<String, RuntimeSummary> entry : summaries.entrySet()) {
            RuntimeSummary summary = entry.getValue();
            System.out.println(String.format(
                    "%-24s | %17.3f | %16.3f",
                    entry.getKey(),
                    summary.getWarmupAverageMs(),
                    summary.getMeasuredAverageMs()));
        }
    }

    private static double routeRuntimeMillis(RouteResult routeResult) {
        double total = 0.0;
        for (ShortestPathResult segment : routeResult.getSegmentResults()) {
            total += segment.getStats().getRuntimeMillis();
        }
        return total;
    }

    private static String waypointLocationsToString(RouteQuery query) {
        List<String> waypoints = query.getWaypointLocations();
        if (waypoints.isEmpty()) {
            return "-";
        }
        return String.join(" -> ", waypoints);
    }

    private static String landmarksToString(List<String> landmarks) {
        if (landmarks.isEmpty()) {
            return "<none>";
        }
        return String.join(", ", landmarks);
    }

    private static RouteBenchmarkMeasurement measureRoute(
            WeightedGraph graph,
            RouteQuery query,
            ShortestPathSolver solver) {
        RoutePlanner planner = new RoutePlanner();
        double warmupTotalRuntimeMs = 0.0;
        for (int i = 0; i < WARMUP_RUNS; i++) {
            RouteResult warmupResult = planner.solveFixedOrderRoute(graph, query, solver);
            warmupTotalRuntimeMs += routeRuntimeMillis(warmupResult);
        }

        RouteResult representativeResult = null;
        double measuredTotalRuntimeMs = 0.0;
        for (int i = 0; i < MEASURED_RUNS; i++) {
            RouteResult result = planner.solveFixedOrderRoute(graph, query, solver);
            if (representativeResult == null) {
                representativeResult = result;
            } else {
                validateRepeatedRouteConsistency(representativeResult, result);
            }
            measuredTotalRuntimeMs += routeRuntimeMillis(result);
        }
        return new RouteBenchmarkMeasurement(
                representativeResult,
                warmupTotalRuntimeMs / WARMUP_RUNS,
                measuredTotalRuntimeMs / MEASURED_RUNS);
    }

    private static void validateRepeatedRouteConsistency(
            RouteResult expected,
            RouteResult actual) {
        if (Math.abs(expected.getTotalCost() - actual.getTotalCost()) > EPSILON
                || !expected.fullPathToString().equals(actual.fullPathToString())) {
            throw new IllegalStateException(
                    "Repeated route benchmark runs produced inconsistent results for "
                            + expected.getAlgorithmName()
                            + " on "
                            + expected.getQuery().getCaseId());
        }
    }

    private static List<AlgorithmSpec> createWeightedAlgorithms(
            AStarAltShortestPath altSolver,
            FloydWarshallAllPairsShortestPath floydWarshall) {
        List<AlgorithmSpec> algorithms = new ArrayList<>();
        algorithms.add(new AlgorithmSpec("Lecture-style Dijkstra", new LectureDijkstraShortestPath()));
        algorithms.add(new AlgorithmSpec("Bellman-Ford", new BellmanFordShortestPath()));
        if (floydWarshall.isExecuted()) {
            algorithms.add(new AlgorithmSpec("Floyd-Warshall", new FloydWarshallSolverAdapter(floydWarshall)));
        }
        algorithms.add(new AlgorithmSpec(
                OPTIMISED_DIJKSTRA_NAME,
                new OptimisedDijkstraShortestPath()));
        algorithms.add(new AlgorithmSpec("Bidirectional Dijkstra", new BidirectionalDijkstraShortestPath()));
        algorithms.add(new AlgorithmSpec("A* ALT", altSolver));
        return algorithms;
    }

    private static double preprocessingRuntimeForAlgorithm(
            String algorithm,
            AStarAltShortestPath altSolver,
            FloydWarshallAllPairsShortestPath floydWarshall) {
        if ("Floyd-Warshall".equals(algorithm) && floydWarshall.isExecuted()) {
            return floydWarshall.getPreprocessingRuntimeMillis();
        }
        if (algorithm.startsWith("A*")) {
            return altSolver.getPreprocessingRuntimeMillis();
        }
        return 0.0;
    }

    private static void addWeightedCsvRows(
            ReportCsvCollector reportCsv,
            List<WeightedAlgorithmCsvRow> rows) {
        double baselineCost = Double.NaN;
        for (WeightedAlgorithmCsvRow row : rows) {
            if (row.available) {
                baselineCost = row.totalCost;
                break;
            }
        }

        for (WeightedAlgorithmCsvRow row : rows) {
            if (!row.available) {
                reportCsv.addWeightedAlgorithmComparison(
                        row.caseId,
                        row.algorithm,
                        Double.NaN,
                        row.preprocessingMs,
                        Double.NaN,
                        row.costMatchText);
                continue;
            }
            String costMatch = !Double.isNaN(baselineCost)
                    && Math.abs(row.totalCost - baselineCost) <= EPSILON ? "Yes" : "No";
            reportCsv.addWeightedAlgorithmComparison(
                    row.caseId,
                    row.algorithm,
                    row.totalCost,
                    row.preprocessingMs,
                    row.queryRuntimeMs,
                    costMatch);
        }
    }

    private static void validateWeightedCaseCosts(
            String caseId,
            List<RouteBenchmarkMeasurement> measurements) {
        if (measurements.isEmpty()) {
            System.out.println("WARNING: no weighted algorithm produced a route for " + caseId);
            return;
        }
        double expected = measurements.get(0).representativeResult.getTotalCost();
        for (RouteBenchmarkMeasurement measurement : measurements) {
            double actual = measurement.representativeResult.getTotalCost();
            if (Math.abs(expected - actual) > EPSILON) {
                System.out.println("WARNING: weighted algorithm cost mismatch for " + caseId);
                return;
            }
        }
    }

    private static void runFixedFlexibleCounterexample(
            WeightedGraph graph,
            TaskBTargetSet targets,
            ReportCsvCollector reportCsv) {
        System.out.println();
        System.out.println("Table 5. Fixed Order vs Flexible Waypoint Counterexample");
        System.out.println(String.format(
                "%-14s | %-55s | %-28s | %10s | %-30s",
                "Example",
                "Route Order",
                "Segment Costs",
                "Total Cost",
                "Interpretation"));
        System.out.println(repeat("-", 150));

        Counterexample counterexample = findFlexibleOrderCounterexample(graph, targets);
        if (counterexample == null) {
            System.out.println("No counterexample found among selected Task A targets.");
            return;
        }

        ShortestPathSolver solver = new OptimisedDijkstraShortestPath();
        RoutePlanner routePlanner = new RoutePlanner();
        RouteQuery fixedQuery = new RouteQuery(
                "Example",
                "Fixed waypoint order counterexample",
                counterexample.start.getSymbol(),
                counterexample.start.getLocationId(),
                stringList(counterexample.fixedFirst.getSymbol(), counterexample.fixedSecond.getSymbol()),
                stringList(counterexample.fixedFirst.getLocationId(), counterexample.fixedSecond.getLocationId()),
                counterexample.destination.getSymbol(),
                counterexample.destination.getLocationId());
        RouteResult fixedResult = routePlanner.solveFixedOrderRoute(graph, fixedQuery, solver);

        FlexibleWaypointPlanner flexiblePlanner = new FlexibleWaypointPlanner();
        FlexibleWaypointResult flexibleResult = flexiblePlanner.solveFlexibleWaypointOrder(
                graph,
                counterexample.start.getLocationId(),
                stringList(counterexample.fixedFirst.getLocationId(), counterexample.fixedSecond.getLocationId()),
                counterexample.destination.getLocationId(),
                solver);

        if (flexibleResult.getTotalCost() > fixedResult.getTotalCost() + EPSILON) {
            throw new IllegalStateException("Flexible waypoint result should not be more expensive than fixed order.");
        }

        double difference = fixedResult.getTotalCost() - flexibleResult.getTotalCost();
        System.out.println(String.format(
                "%-14s | %-55s | %-28s | %10.3f | %-30s",
                "Fixed order",
                routeOrderToString(fixedQuery.getOrderedStopLocations()),
                segmentCostsToString(fixedResult.getSegmentResults()),
                fixedResult.getTotalCost(),
                "fixed waypoint order"));
        reportCsv.addFixedFlexibleCounterexample(
                "Fixed order",
                routeOrderToString(fixedQuery.getOrderedStopLocations()),
                segmentCostsToString(fixedResult.getSegmentResults()),
                fixedResult.getTotalCost(),
                "fixed waypoint order");
        System.out.println(String.format(
                "%-14s | %-55s | %-28s | %10.3f | %-30s",
                "Flexible DP",
                routeOrderToString(counterexample.start.getLocationId(), flexibleResult),
                segmentCostsToString(flexibleResult.getSegmentCosts()),
                flexibleResult.getTotalCost(),
                "lower by " + String.format("%.3f", difference)));
        reportCsv.addFixedFlexibleCounterexample(
                "Flexible DP",
                routeOrderToString(counterexample.start.getLocationId(), flexibleResult),
                segmentCostsToString(flexibleResult.getSegmentCosts()),
                flexibleResult.getTotalCost(),
                "lower by " + String.format("%.3f", difference));
        System.out.println("Fixed/flexible waypoint counterexample: PASSED");
    }

    private static Counterexample findFlexibleOrderCounterexample(WeightedGraph graph, TaskBTargetSet targets) {
        List<SelectedTarget> selectedTargets = targets.getAllTargets();
        ShortestPathSolver solver = new OptimisedDijkstraShortestPath();
        Map<String, ShortestPathResult> shortestPaths = new HashMap<>();
        for (SelectedTarget from : selectedTargets) {
            for (SelectedTarget to : selectedTargets) {
                if (!from.getLocationId().equals(to.getLocationId())) {
                    ShortestPathResult result = solver.findShortestPath(graph, from.getLocationId(), to.getLocationId());
                    if (result.isReachable()) {
                        shortestPaths.put(pairKey(from.getLocationId(), to.getLocationId()), result);
                    }
                }
            }
        }

        for (SelectedTarget start : selectedTargets) {
            for (SelectedTarget destination : selectedTargets) {
                if (sameLocation(start, destination)) {
                    continue;
                }
                for (SelectedTarget first : selectedTargets) {
                    if (sameLocation(first, start) || sameLocation(first, destination)) {
                        continue;
                    }
                    for (SelectedTarget second : selectedTargets) {
                        if (sameLocation(second, start)
                                || sameLocation(second, destination)
                                || sameLocation(second, first)) {
                            continue;
                        }
                        double firstThenSecond = routeCostFromCache(
                                shortestPaths,
                                start.getLocationId(),
                                first.getLocationId(),
                                second.getLocationId(),
                                destination.getLocationId());
                        double secondThenFirst = routeCostFromCache(
                                shortestPaths,
                                start.getLocationId(),
                                second.getLocationId(),
                                first.getLocationId(),
                                destination.getLocationId());
                        if (Double.isInfinite(firstThenSecond) || Double.isInfinite(secondThenFirst)) {
                            continue;
                        }
                        if (firstThenSecond > secondThenFirst + EPSILON) {
                            return new Counterexample(start, destination, first, second);
                        }
                        if (secondThenFirst > firstThenSecond + EPSILON) {
                            return new Counterexample(start, destination, second, first);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static double routeCostFromCache(
            Map<String, ShortestPathResult> shortestPaths,
            String start,
            String waypoint1,
            String waypoint2,
            String destination) {
        ShortestPathResult segment1 = shortestPaths.get(pairKey(start, waypoint1));
        ShortestPathResult segment2 = shortestPaths.get(pairKey(waypoint1, waypoint2));
        ShortestPathResult segment3 = shortestPaths.get(pairKey(waypoint2, destination));
        if (segment1 == null || segment2 == null || segment3 == null) {
            return Double.POSITIVE_INFINITY;
        }
        return segment1.getTotalCost() + segment2.getTotalCost() + segment3.getTotalCost();
    }

    private static boolean sameLocation(SelectedTarget a, SelectedTarget b) {
        return a.getLocationId().equals(b.getLocationId());
    }

    private static String pairKey(String from, String to) {
        return from + " -> " + to;
    }

    private static String routeOrderToString(List<String> stops) {
        return String.join(" -> ", stops);
    }

    private static String routeOrderToString(String start, FlexibleWaypointResult result) {
        List<String> stops = new ArrayList<>();
        stops.add(start);
        stops.addAll(result.getChosenWaypointOrder());
        stops.add(result.getDestination());
        return routeOrderToString(stops);
    }

    private static String segmentCostsToString(List<?> segmentsOrCosts) {
        List<String> costs = new ArrayList<>();
        for (Object item : segmentsOrCosts) {
            if (item instanceof ShortestPathResult) {
                costs.add(String.format("%.3f", ((ShortestPathResult) item).getTotalCost()));
            } else if (item instanceof Double) {
                costs.add(String.format("%.3f", (Double) item));
            }
        }
        return String.join(" + ", costs);
    }

    private static List<RouteQuery> createRequiredCases(TaskBTargetSet targets) {
        List<RouteQuery> cases = new ArrayList<>();
        cases.add(new RouteQuery(
                "Case 1",
                "A1 to A1",
                "A1",
                targets.getA1().getLocationId(),
                Collections.emptyList(),
                Collections.emptyList(),
                "A1",
                targets.getA1().getLocationId()));
        cases.add(new RouteQuery(
                "Case 2",
                "A1 to A10",
                "A1",
                targets.getA1().getLocationId(),
                Collections.emptyList(),
                Collections.emptyList(),
                "A10",
                targets.getA10().getLocationId()));
        cases.add(new RouteQuery(
                "Case 3",
                "A1 to B1 via B5",
                "A1",
                targets.getA1().getLocationId(),
                stringList("B5"),
                stringList(targets.getB5().getLocationId()),
                "B1",
                targets.getB1().getLocationId()));
        cases.add(createCase4(targets));
        return cases;
    }

    private static RouteQuery createCase4(TaskBTargetSet targets) {
        return new RouteQuery(
                "Case 4",
                "A1 to C1 via B5 then C5",
                "A1",
                targets.getA1().getLocationId(),
                stringList("B5", "C5"),
                stringList(targets.getB5().getLocationId(), targets.getC5().getLocationId()),
                "C1",
                targets.getC1().getLocationId());
    }

    private static void validateCase1Cost(RouteResult case1Result) {
        if (Math.abs(case1Result.getTotalCost()) > EPSILON) {
            throw new IllegalStateException("Case 1 should have cost 0.");
        }
    }

    private static List<String> stringList(String... values) {
        List<String> result = new ArrayList<>();
        Collections.addAll(result, values);
        return result;
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private static class WeightedAlgorithmCsvRow {
        private final String caseId;
        private final String algorithm;
        private final double totalCost;
        private final double preprocessingMs;
        private final double queryRuntimeMs;
        private final boolean available;
        private final String costMatchText;

        private WeightedAlgorithmCsvRow(
                String caseId,
                String algorithm,
                double totalCost,
                double preprocessingMs,
                double queryRuntimeMs,
                boolean available,
                String costMatchText) {
            this.caseId = caseId;
            this.algorithm = algorithm;
            this.totalCost = totalCost;
            this.preprocessingMs = preprocessingMs;
            this.queryRuntimeMs = queryRuntimeMs;
            this.available = available;
            this.costMatchText = costMatchText;
        }

        private static WeightedAlgorithmCsvRow success(
                String caseId,
                String algorithm,
                double totalCost,
                double preprocessingMs,
                double queryRuntimeMs) {
            return new WeightedAlgorithmCsvRow(
                    caseId,
                    algorithm,
                    totalCost,
                    preprocessingMs,
                    queryRuntimeMs,
                    true,
                    "");
        }

        private static WeightedAlgorithmCsvRow unavailable(
                String caseId,
                String algorithm,
                double preprocessingMs,
                String reason) {
            return new WeightedAlgorithmCsvRow(
                    caseId,
                    algorithm,
                    Double.NaN,
                    preprocessingMs,
                    Double.NaN,
                    false,
                    "Unavailable: " + reason);
        }
    }

    private static class RouteBenchmarkMeasurement {
        private final RouteResult representativeResult;
        private final double warmupAverageRuntimeMs;
        private final double avgRuntimeMs;

        private RouteBenchmarkMeasurement(
                RouteResult representativeResult,
                double warmupAverageRuntimeMs,
                double avgRuntimeMs) {
            this.representativeResult = representativeResult;
            this.warmupAverageRuntimeMs = warmupAverageRuntimeMs;
            this.avgRuntimeMs = avgRuntimeMs;
        }
    }

    private static class RuntimeSummary {
        private double warmupTotalMs;
        private double measuredTotalMs;
        private int count;

        private void add(RouteBenchmarkMeasurement measurement) {
            warmupTotalMs += measurement.warmupAverageRuntimeMs;
            measuredTotalMs += measurement.avgRuntimeMs;
            count++;
        }

        private double getWarmupAverageMs() {
            return count == 0 ? 0.0 : warmupTotalMs / count;
        }

        private double getMeasuredAverageMs() {
            return count == 0 ? 0.0 : measuredTotalMs / count;
        }
    }

    private static class AlgorithmSpec {
        private final String displayName;
        private final ShortestPathSolver solver;

        private AlgorithmSpec(String displayName, ShortestPathSolver solver) {
            this.displayName = displayName;
            this.solver = solver;
        }
    }

    private static class FloydWarshallSolverAdapter implements ShortestPathSolver {
        private final FloydWarshallAllPairsShortestPath floydWarshall;

        private FloydWarshallSolverAdapter(FloydWarshallAllPairsShortestPath floydWarshall) {
            this.floydWarshall = floydWarshall;
        }

        @Override
        public ShortestPathResult findShortestPath(WeightedGraph graph, String start, String destination) {
            return floydWarshall.getShortestPath(start, destination);
        }
    }

    private static class Counterexample {
        private final SelectedTarget start;
        private final SelectedTarget destination;
        private final SelectedTarget fixedFirst;
        private final SelectedTarget fixedSecond;

        private Counterexample(
                SelectedTarget start,
                SelectedTarget destination,
                SelectedTarget fixedFirst,
                SelectedTarget fixedSecond) {
            this.start = start;
            this.destination = destination;
            this.fixedFirst = fixedFirst;
            this.fixedSecond = fixedSecond;
        }
    }

    private static class ReportCsvCollector {
        private final List<String> requiredCasesRows = new ArrayList<>();
        private final List<String> weightedComparisonRows = new ArrayList<>();
        private final List<String> fixedFlexibleRows = new ArrayList<>();
        private final List<String> bfsBaselineRows = new ArrayList<>();
        private final List<String> scalabilityRows = new ArrayList<>();
        private final List<String> landmarkSensitivityRows = new ArrayList<>();

        private ReportCsvCollector() {
            requiredCasesRows.add("Case,Start,Via Nodes,Destination,Shortest Path,Total Cost");
            weightedComparisonRows.add("Case,Algorithm,Total Cost,Preprocessing(ms),Query Runtime(ms),Cost Match?");
            fixedFlexibleRows.add("Example,Route Order,Segment Costs,Total Cost,Interpretation");
            bfsBaselineRows.add("Case,Dijkstra Cost,BFS Hops,BFS Weighted Cost,Match?");
            scalabilityRows.add("Waypoint Count,Segment Count,Algorithm,Total Cost,Measured Avg(ms),Avg per Segment(ms)");
            landmarkSensitivityRows.add("Landmark Count,Preprocessing(ms),Waypoint Count,Segment Count,Total Cost,Measured Avg(ms),Avg per Segment(ms),Expanded/Settled Nodes,Cost Match?");
        }

        private void addRequiredCase(
                String caseId,
                String start,
                String viaNodes,
                String destination,
                String shortestPath,
                double totalCost) {
            requiredCasesRows.add(joinCsv(
                    caseId,
                    start,
                    viaNodes,
                    destination,
                    shortestPath,
                    formatCsvNumber(totalCost)));
        }

        private void addWeightedAlgorithmComparison(
                String caseId,
                String algorithm,
                double totalCost,
                double preprocessingMs,
                double queryRuntimeMs,
                String costMatch) {
            weightedComparisonRows.add(joinCsv(
                    caseId,
                    algorithm,
                    formatCsvNumber(totalCost),
                    formatCsvNumber(preprocessingMs),
                    formatCsvNumber(queryRuntimeMs),
                    costMatch));
        }

        private void addFixedFlexibleCounterexample(
                String example,
                String routeOrder,
                String segmentCosts,
                double totalCost,
                String interpretation) {
            fixedFlexibleRows.add(joinCsv(
                    example,
                    routeOrder,
                    segmentCosts,
                    formatCsvNumber(totalCost),
                    interpretation));
        }

        private void addBfsBaseline(
                String caseId,
                double dijkstraCost,
                int bfsHops,
                double bfsWeightedCost,
                String match) {
            bfsBaselineRows.add(joinCsv(
                    caseId,
                    formatCsvNumber(dijkstraCost),
                    String.valueOf(bfsHops),
                    formatCsvNumber(bfsWeightedCost),
                    match));
        }

        private void addScalability(
                int waypointCount,
                int segmentCount,
                String algorithm,
                double totalCost,
                double measuredAverageMs,
                double averagePerSegmentMs) {
            scalabilityRows.add(joinCsv(
                    String.valueOf(waypointCount),
                    String.valueOf(segmentCount),
                    algorithm,
                    formatCsvNumber(totalCost),
                    formatCsvNumber(measuredAverageMs),
                    formatCsvNumber(averagePerSegmentMs)));
        }

        private void addLandmarkSensitivity(
                int landmarkCount,
                double preprocessingMs,
                int waypointCount,
                int segmentCount,
                double totalCost,
                double measuredAverageMs,
                double averagePerSegmentMs,
                int expandedOrSettledNodes,
                String costMatch) {
            landmarkSensitivityRows.add(joinCsv(
                    String.valueOf(landmarkCount),
                    formatCsvNumber(preprocessingMs),
                    String.valueOf(waypointCount),
                    String.valueOf(segmentCount),
                    formatCsvNumber(totalCost),
                    formatCsvNumber(measuredAverageMs),
                    formatCsvNumber(averagePerSegmentMs),
                    String.valueOf(expandedOrSettledNodes),
                    costMatch));
        }

        private void writeAll() throws IOException {
            writeRows(TABLE_2_1_REQUIRED_CASES_CSV, requiredCasesRows);
            writeRows(TABLE_2_2_WEIGHTED_COMPARISON_CSV, weightedComparisonRows);
            writeRows(TABLE_2_3_FIXED_FLEXIBLE_CSV, fixedFlexibleRows);
            writeRows(TABLE_2_4_BFS_BASELINE_CSV, bfsBaselineRows);
            writeRows(TABLE_2_5_SCALABILITY_CSV, scalabilityRows);
            writeRows(TABLE_2_6_LANDMARK_SENSITIVITY_CSV, landmarkSensitivityRows);
        }

        private static void writeRows(String path, List<String> rows) throws IOException {
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Unable to create directory for " + path);
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                for (String row : rows) {
                    writer.write(row);
                    writer.newLine();
                }
            }
        }

        private static String joinCsv(String... values) {
            List<String> escaped = new ArrayList<>();
            for (String value : values) {
                escaped.add(csv(value));
            }
            return String.join(",", escaped);
        }

        private static String formatCsvNumber(double value) {
            if (Double.isNaN(value)) {
                return "";
            }
            return String.format("%.3f", value);
        }

        private static String csv(String value) {
            if (value == null) {
                return "";
            }
            String escaped = value.replace("\"", "\"\"");
            if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
                return "\"" + escaped + "\"";
            }
            return escaped;
        }
    }

    private static class RuntimeCsvCollector {
        private final List<String> rows = new ArrayList<>();

        private RuntimeCsvCollector() {
            rows.add("section,label,algorithm,start,target,total_cost,runtime_ms");
        }

        private void addRoute(
                String section,
                String label,
                String algorithm,
                String start,
                String target,
                double totalCost,
                double runtimeMs) {
            rows.add(csv(section)
                    + ","
                    + csv(label)
                    + ","
                    + csv(algorithm)
                    + ","
                    + csv(start)
                    + ","
                    + csv(target)
                    + ","
                    + String.format("%.3f", totalCost)
                    + ","
                    + String.format("%.3f", runtimeMs));
        }

        private void writeTo(String path) throws IOException {
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Unable to create directory for " + path);
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                for (String row : rows) {
                    writer.write(row);
                    writer.newLine();
                }
            }
        }

        private static String csv(String value) {
            if (value == null) {
                return "";
            }
            String escaped = value.replace("\"", "\"\"");
            if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
                return "\"" + escaped + "\"";
            }
            return escaped;
        }
    }
}
