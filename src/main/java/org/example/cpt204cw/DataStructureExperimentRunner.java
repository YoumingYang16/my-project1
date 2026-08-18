package org.example.cpt204cw;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

/*
 * Additional experiment runner used to compare Java collection choices and
 * graph representations without changing the main application workflow.
 */
public class DataStructureExperimentRunner {
    private static final int WARMUP_RUNS = 5;
    private static final int MEASURED_RUNS = 10;
    private static final String[] DATASET_PATHS = {
            "data/candidates_A.csv",
            "data/candidates_B.csv",
            "data/candidates_C.csv"
    };
    private static final String PATHS_FILE = "data/paths.csv";
    private static final String SELECTED_TARGETS_FILE = "data/selected_targets.csv";
    private static final double COST_EPSILON = 1e-6;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        List<DatasetInput> datasets = readDatasets();
        printSortingListExperiment(datasets);
        System.out.println();
        printCollectionOrderingExperiment(datasets);
        System.out.println();
        printGraphRepresentationExperiment();
    }

    private static void printSortingListExperiment(List<DatasetInput> datasets) {
        System.out.println("Table DS-1. Runtime comparison of Bubble Sort, Quick Sort and Merge Sort on ArrayList and LinkedList");
        printLine(86);
        System.out.printf("%-10s | %-12s | %-10s | %13s%n",
                "Dataset", "Algorithm", "List Type", "Avg Time (ms)");
        printLine(86);
        for (DatasetInput dataset : datasets) {
            List<CandidateLocation> baseline = sortedBaseline(dataset.originalData);
            for (SortingAlgorithmSpec algorithm : sortingAlgorithms()) {
                for (ListSpec listSpec : listSpecs()) {
                    ExperimentMeasurement<List<CandidateLocation>> measurement = measureSorting(
                            dataset.originalData,
                            algorithm,
                            listSpec);
                    String validationError = validateOrderedOutput(baseline, measurement.output);
                    System.out.printf("%-10s | %-12s | %-10s | %13.3f%n",
                            dataset.name,
                            algorithm.name,
                            listSpec.name,
                            measurement.averageMillis);
                    printValidationFailure(validationError);
                }
            }
        }
    }

    private static void printCollectionOrderingExperiment(List<DatasetInput> datasets) {
        System.out.println("Table DS-2. Alternative Java collection strategies for producing ordered candidate outputs");
        printLine(104);
        System.out.printf("%-10s | %-34s | %-24s | %13s%n",
                "Dataset", "Strategy", "Structure Used", "Avg Time (ms)");
        printLine(104);
        for (DatasetInput dataset : datasets) {
            List<CandidateLocation> baseline = sortedBaseline(dataset.originalData);
            for (OrderingStrategy strategy : orderingStrategies()) {
                ExperimentMeasurement<List<CandidateLocation>> measurement = measureOrderingStrategy(
                        dataset.originalData,
                        strategy);
                String validationError = validateOrderedOutput(baseline, measurement.output);
                System.out.printf("%-10s | %-34s | %-24s | %13.3f%n",
                        dataset.name,
                        strategy.name,
                        strategy.structureUsed,
                        measurement.averageMillis);
                printValidationFailure(validationError);
            }
        }
    }

    private static void printGraphRepresentationExperiment() throws IOException {
        WeightedGraph targetGraph = PathCsvReader.readGraph(PATHS_FILE);
        TaskBTargetSet targets = SelectedTargetsReader.readTaskBTargets(SELECTED_TARGETS_FILE, targetGraph);
        List<DijkstraCase> cases = List.of(createTaskBCase3(targets), createTaskBCase4(targets));
        List<GraphSpec> graphSpecs = graphSpecs();
        Map<String, WeightedGraph> adjacencyListGraphs = new LinkedHashMap<>();
        for (GraphSpec spec : graphSpecs) {
            adjacencyListGraphs.put(spec.name, PathCsvReader.readGraph(PATHS_FILE, spec.outerFactory, spec.innerFactory));
        }
        MatrixGraph matrixGraph = MatrixGraph.fromWeightedGraph(PathCsvReader.readGraph(PATHS_FILE));

        System.out.println("Table DS-3. Graph representation comparison for lecture-style Dijkstra");
        printLine(101);
        System.out.printf("%-6s | %-8s | %-8s | %-28s | %13s | %-10s%n",
                "Case", "Source", "Target", "Graph Representation", "Avg Time (ms)", "Cost");
        printLine(101);
        for (DijkstraCase dijkstraCase : cases) {
            double baselineCost = Double.POSITIVE_INFINITY;
            boolean baselineSet = false;

            for (GraphSpec spec : graphSpecs) {
                ExperimentMeasurement<ShortestPathResult> measurement = measureDijkstra(
                        adjacencyListGraphs.get(spec.name),
                        dijkstraCase.source,
                        dijkstraCase.target);
                double cost = measurement.output.isReachable()
                        ? measurement.output.getTotalCost()
                        : Double.POSITIVE_INFINITY;
                if (!baselineSet) {
                    baselineCost = cost;
                    baselineSet = true;
                }
                String validationError = validateDijkstraCost(baselineCost, cost);
                System.out.printf("%-6s | %-8s | %-8s | %-28s | %13.3f | %-10s%n",
                        dijkstraCase.caseId,
                        dijkstraCase.source,
                        dijkstraCase.target,
                        spec.name,
                        measurement.averageMillis,
                        formatCost(cost));
                printValidationFailure(validationError);
            }

            ExperimentMeasurement<MatrixShortestPathResult> matrixMeasurement = measureMatrixDijkstra(
                    matrixGraph,
                    dijkstraCase.source,
                    dijkstraCase.target);
            double matrixCost = matrixMeasurement.output.reachable
                    ? matrixMeasurement.output.totalCost
                    : Double.POSITIVE_INFINITY;
            String validationError = validateDijkstraCost(baselineCost, matrixCost);
            System.out.printf("%-6s | %-8s | %-8s | %-28s | %13.3f | %-10s%n",
                    dijkstraCase.caseId,
                    dijkstraCase.source,
                    dijkstraCase.target,
                    "Adjacency matrix",
                    matrixMeasurement.averageMillis,
                    formatCost(matrixCost));
            printValidationFailure(validationError);
        }
    }

    private static List<DatasetInput> readDatasets() throws IOException {
        List<DatasetInput> datasets = new ArrayList<>();
        for (String path : DATASET_PATHS) {
            datasets.add(new DatasetInput(datasetNameFromPath(path), CandidateCsvReader.readCandidates(path)));
        }
        return datasets;
    }

    private static ExperimentMeasurement<List<CandidateLocation>> measureSorting(
            List<CandidateLocation> originalData,
            SortingAlgorithmSpec algorithm,
            ListSpec listSpec) {
        for (int run = 0; run < WARMUP_RUNS; run++) {
            List<CandidateLocation> workingData = listSpec.copy(originalData);
            algorithm.newAlgorithm().sort(workingData);
        }
        long totalNanos = 0L;
        List<CandidateLocation> output = Collections.emptyList();
        for (int run = 0; run < MEASURED_RUNS; run++) {
            List<CandidateLocation> workingData = listSpec.copy(originalData);
            SortingAlgorithm sortingAlgorithm = algorithm.newAlgorithm();
            long start = System.nanoTime();
            sortingAlgorithm.sort(workingData);
            totalNanos += System.nanoTime() - start;
            if (run == 0) {
                output = new ArrayList<>(workingData);
            }
        }
        return new ExperimentMeasurement<>(nanosToMillis(totalNanos / (double) MEASURED_RUNS), output);
    }

    private static ExperimentMeasurement<List<CandidateLocation>> measureOrderingStrategy(
            List<CandidateLocation> originalData,
            OrderingStrategy strategy) {
        for (int run = 0; run < WARMUP_RUNS; run++) {
            strategy.order(originalData);
        }
        long totalNanos = 0L;
        List<CandidateLocation> output = Collections.emptyList();
        for (int run = 0; run < MEASURED_RUNS; run++) {
            long start = System.nanoTime();
            List<CandidateLocation> ordered = strategy.order(originalData);
            totalNanos += System.nanoTime() - start;
            if (run == 0) {
                output = ordered;
            }
        }
        return new ExperimentMeasurement<>(nanosToMillis(totalNanos / (double) MEASURED_RUNS), output);
    }

    private static ExperimentMeasurement<ShortestPathResult> measureDijkstra(
            WeightedGraph graph,
            String source,
            String target) {
        for (int run = 0; run < WARMUP_RUNS; run++) {
            new LectureDijkstraShortestPath().findShortestPath(graph, source, target);
        }
        long totalNanos = 0L;
        ShortestPathResult output = null;
        for (int run = 0; run < MEASURED_RUNS; run++) {
            LectureDijkstraShortestPath solver = new LectureDijkstraShortestPath();
            long start = System.nanoTime();
            ShortestPathResult result = solver.findShortestPath(graph, source, target);
            totalNanos += System.nanoTime() - start;
            if (run == 0) {
                output = result;
            }
        }
        return new ExperimentMeasurement<>(nanosToMillis(totalNanos / (double) MEASURED_RUNS), output);
    }

    private static ExperimentMeasurement<MatrixShortestPathResult> measureMatrixDijkstra(
            MatrixGraph graph,
            String source,
            String target) {
        for (int run = 0; run < WARMUP_RUNS; run++) {
            findShortestPathInMatrix(graph, source, target);
        }
        long totalNanos = 0L;
        MatrixShortestPathResult output = null;
        for (int run = 0; run < MEASURED_RUNS; run++) {
            long start = System.nanoTime();
            MatrixShortestPathResult result = findShortestPathInMatrix(graph, source, target);
            totalNanos += System.nanoTime() - start;
            if (run == 0) {
                output = result;
            }
        }
        return new ExperimentMeasurement<>(nanosToMillis(totalNanos / (double) MEASURED_RUNS), output);
    }

    private static MatrixShortestPathResult findShortestPathInMatrix(
            MatrixGraph graph,
            String source,
            String target) {
        int sourceIndex = graph.indexOf(source);
        int targetIndex = graph.indexOf(target);
        int vertexCount = graph.vertexCount();
        double[] cost = new double[vertexCount];
        int[] parent = new int[vertexCount];
        boolean[] isFinalised = new boolean[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            cost[i] = Double.POSITIVE_INFINITY;
            parent[i] = -1;
        }
        cost[sourceIndex] = 0.0;

        for (int settledCount = 0; settledCount < vertexCount; settledCount++) {
            int bestIndex = -1;
            double bestCost = Double.POSITIVE_INFINITY;
            for (int i = 0; i < vertexCount; i++) {
                if (!isFinalised[i] && cost[i] < bestCost) {
                    bestCost = cost[i];
                    bestIndex = i;
                }
            }
            if (bestIndex < 0 || Double.isInfinite(bestCost)) {
                break;
            }
            isFinalised[bestIndex] = true;
            for (int neighborIndex = 0; neighborIndex < vertexCount; neighborIndex++) {
                double weight = graph.weight(bestIndex, neighborIndex);
                if (isFinalised[neighborIndex] || Double.isInfinite(weight)) {
                    continue;
                }
                double candidate = cost[bestIndex] + weight;
                if (candidate < cost[neighborIndex]) {
                    cost[neighborIndex] = candidate;
                    parent[neighborIndex] = bestIndex;
                }
            }
        }
        return new MatrixShortestPathResult(!Double.isInfinite(cost[targetIndex]), cost[targetIndex]);
    }

    private static List<CandidateLocation> sortedBaseline(List<CandidateLocation> originalData) {
        List<CandidateLocation> baseline = new ArrayList<>(originalData);
        baseline.sort(CandidateLocation::compare);
        return baseline;
    }

    private static List<SortingAlgorithmSpec> sortingAlgorithms() {
        List<SortingAlgorithmSpec> algorithms = new ArrayList<>();
        algorithms.add(new SortingAlgorithmSpec("Bubble Sort", BubbleSort::new));
        algorithms.add(new SortingAlgorithmSpec("Quick Sort", QuickSort::new));
        algorithms.add(new SortingAlgorithmSpec("Merge Sort", MergeSort::new));
        return algorithms;
    }

    private static List<ListSpec> listSpecs() {
        List<ListSpec> specs = new ArrayList<>();
        specs.add(new ListSpec("ArrayList", ArrayList::new));
        specs.add(new ListSpec("LinkedList", LinkedList::new));
        return specs;
    }

    private static List<OrderingStrategy> orderingStrategies() {
        List<OrderingStrategy> strategies = new ArrayList<>();
        strategies.add(new OrderingStrategy(
                "ArrayList + MergeSort",
                "ArrayList",
                DataStructureExperimentRunner::orderWithMergeSort));
        strategies.add(new OrderingStrategy(
                "PriorityQueue insertion + poll",
                "PriorityQueue",
                DataStructureExperimentRunner::orderWithPriorityQueue));
        strategies.add(new OrderingStrategy(
                "HashSet + ArrayList + sort",
                "HashSet",
                DataStructureExperimentRunner::orderWithHashSet));
        strategies.add(new OrderingStrategy(
                "LinkedHashSet + ArrayList + sort",
                "LinkedHashSet",
                DataStructureExperimentRunner::orderWithLinkedHashSet));
        strategies.add(new OrderingStrategy(
                "TreeSet insertion + iteration",
                "TreeSet",
                DataStructureExperimentRunner::orderWithTreeSet));
        return strategies;
    }

    private static List<GraphSpec> graphSpecs() {
        List<GraphSpec> specs = new ArrayList<>();
        specs.add(new GraphSpec("LinkedHashMap adjacency list", LinkedHashMap::new, LinkedHashMap::new));
        specs.add(new GraphSpec("HashMap adjacency list", HashMap::new, HashMap::new));
        specs.add(new GraphSpec("TreeMap adjacency list", TreeMap::new, TreeMap::new));
        return specs;
    }

    private static List<CandidateLocation> orderWithMergeSort(List<CandidateLocation> originalData) {
        List<CandidateLocation> data = new ArrayList<>(originalData);
        new MergeSort().sort(data);
        return data;
    }

    private static List<CandidateLocation> orderWithPriorityQueue(List<CandidateLocation> originalData) {
        PriorityQueue<CandidateLocation> queue = new PriorityQueue<>(CandidateLocation::compare);
        queue.addAll(originalData);
        List<CandidateLocation> ordered = new ArrayList<>();
        while (!queue.isEmpty()) {
            ordered.add(queue.poll());
        }
        return ordered;
    }

    private static List<CandidateLocation> orderWithHashSet(List<CandidateLocation> originalData) {
        Set<CandidateLocation> set = new HashSet<>();
        set.addAll(originalData);
        List<CandidateLocation> ordered = new ArrayList<>(set);
        ordered.sort(CandidateLocation::compare);
        return ordered;
    }

    private static List<CandidateLocation> orderWithLinkedHashSet(List<CandidateLocation> originalData) {
        Set<CandidateLocation> set = new LinkedHashSet<>();
        set.addAll(originalData);
        List<CandidateLocation> ordered = new ArrayList<>(set);
        ordered.sort(CandidateLocation::compare);
        return ordered;
    }

    private static List<CandidateLocation> orderWithTreeSet(List<CandidateLocation> originalData) {
        TreeSet<CandidateLocation> set = new TreeSet<>(CandidateLocation::compare);
        set.addAll(originalData);
        return new ArrayList<>(set);
    }

    private static DijkstraCase createTaskBCase4(TaskBTargetSet targets) {
        return new DijkstraCase(
                "Case 4",
                targets.getA1().getLocationId(),
                targets.getC1().getLocationId());
    }

    private static DijkstraCase createTaskBCase3(TaskBTargetSet targets) {
        return new DijkstraCase(
                "Case 3",
                targets.getA1().getLocationId(),
                targets.getB1().getLocationId());
    }

    private static String validateOrderedOutput(
            List<CandidateLocation> expected,
            List<CandidateLocation> actual) {
        if (actual == null) {
            return "ordered output is missing";
        }
        if (expected.size() != actual.size()) {
            return "expected " + expected.size() + " rows but found " + actual.size();
        }
        for (int i = 0; i < expected.size(); i++) {
            CandidateLocation expectedLocation = expected.get(i);
            CandidateLocation actualLocation = actual.get(i);
            if (!expectedLocation.getLocationId().equals(actualLocation.getLocationId())
                    || expectedLocation.getPriorityScore() != actualLocation.getPriorityScore()) {
                return "row " + (i + 1) + " does not match baseline";
            }
            if (i > 0 && CandidateLocation.compare(actual.get(i - 1), actual.get(i)) > 0) {
                return "output is not sorted";
            }
        }
        return null;
    }

    private static String validateDijkstraCost(double expectedCost, double actualCost) {
        if (Double.isInfinite(expectedCost) && Double.isInfinite(actualCost)) {
            return null;
        }
        if (Double.isInfinite(expectedCost) || Double.isInfinite(actualCost)) {
            return "reachability differs from baseline";
        }
        if (Math.abs(expectedCost - actualCost) > COST_EPSILON) {
            return "cost differs from baseline";
        }
        return null;
    }

    private static String datasetNameFromPath(String path) {
        String normalized = path.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (fileName.equalsIgnoreCase("candidates_A.csv")) {
            return "A";
        }
        if (fileName.equalsIgnoreCase("candidates_B.csv")) {
            return "B";
        }
        if (fileName.equalsIgnoreCase("candidates_C.csv")) {
            return "C";
        }
        return fileName;
    }

    private static String formatCost(double cost) {
        if (Double.isInfinite(cost)) {
            return "N/A";
        }
        return String.format("%.3f", cost);
    }

    private static double nanosToMillis(double nanos) {
        return nanos / 1_000_000.0;
    }

    private static void printValidationFailure(String validationError) {
        if (validationError != null) {
            System.out.println("VALIDATION FAILED: " + validationError);
        }
    }

    private static void printLine(int width) {
        System.out.println("-".repeat(width));
    }

    private interface SortingAlgorithmFactory {
        SortingAlgorithm create();
    }

    private interface ListFactory {
        List<CandidateLocation> copy(List<CandidateLocation> originalData);
    }

    private interface OrderingFunction {
        List<CandidateLocation> order(List<CandidateLocation> originalData);
    }

    private static class DatasetInput {
        private final String name;
        private final List<CandidateLocation> originalData;

        private DatasetInput(String name, List<CandidateLocation> originalData) {
            this.name = name;
            this.originalData = originalData;
        }
    }

    private static class SortingAlgorithmSpec {
        private final String name;
        private final SortingAlgorithmFactory factory;

        private SortingAlgorithmSpec(String name, SortingAlgorithmFactory factory) {
            this.name = name;
            this.factory = factory;
        }

        private SortingAlgorithm newAlgorithm() {
            return factory.create();
        }
    }

    private static class ListSpec {
        private final String name;
        private final ListFactory factory;

        private ListSpec(String name, ListFactory factory) {
            this.name = name;
            this.factory = factory;
        }

        private List<CandidateLocation> copy(List<CandidateLocation> originalData) {
            return factory.copy(originalData);
        }
    }

    private static class OrderingStrategy {
        private final String name;
        private final String structureUsed;
        private final OrderingFunction orderingFunction;

        private OrderingStrategy(String name, String structureUsed, OrderingFunction orderingFunction) {
            this.name = name;
            this.structureUsed = structureUsed;
            this.orderingFunction = orderingFunction;
        }

        private List<CandidateLocation> order(List<CandidateLocation> originalData) {
            return orderingFunction.order(originalData);
        }
    }

    private static class GraphSpec {
        private final String name;
        private final Supplier<Map<String, Map<String, WeightedEdge>>> outerFactory;
        private final Supplier<Map<String, WeightedEdge>> innerFactory;

        private GraphSpec(
                String name,
                Supplier<Map<String, Map<String, WeightedEdge>>> outerFactory,
                Supplier<Map<String, WeightedEdge>> innerFactory) {
            this.name = name;
            this.outerFactory = outerFactory;
            this.innerFactory = innerFactory;
        }
    }

    private static class DijkstraCase {
        private final String caseId;
        private final String source;
        private final String target;

        private DijkstraCase(String caseId, String source, String target) {
            this.caseId = caseId;
            this.source = source;
            this.target = target;
        }
    }

    private static class MatrixGraph {
        private final List<String> vertices;
        private final Map<String, Integer> indexByVertex;
        private final double[][] weightMatrix;

        private MatrixGraph(List<String> vertices, Map<String, Integer> indexByVertex, double[][] weightMatrix) {
            this.vertices = vertices;
            this.indexByVertex = indexByVertex;
            this.weightMatrix = weightMatrix;
        }

        private static MatrixGraph fromWeightedGraph(WeightedGraph graph) {
            List<String> vertices = new ArrayList<>(graph.getVertices());
            Map<String, Integer> indexByVertex = new HashMap<>();
            for (int i = 0; i < vertices.size(); i++) {
                indexByVertex.put(vertices.get(i), i);
            }
            double[][] weightMatrix = new double[vertices.size()][vertices.size()];
            for (int row = 0; row < weightMatrix.length; row++) {
                for (int column = 0; column < weightMatrix[row].length; column++) {
                    weightMatrix[row][column] = row == column ? 0.0 : Double.POSITIVE_INFINITY;
                }
            }
            for (WeightedEdge edge : graph.getAllDirectedEdges()) {
                int fromIndex = indexByVertex.get(edge.getFrom());
                int toIndex = indexByVertex.get(edge.getTo());
                if (edge.getWeight() < weightMatrix[fromIndex][toIndex]) {
                    weightMatrix[fromIndex][toIndex] = edge.getWeight();
                }
            }
            return new MatrixGraph(vertices, indexByVertex, weightMatrix);
        }

        private int vertexCount() {
            return vertices.size();
        }

        private int indexOf(String vertex) {
            Integer index = indexByVertex.get(vertex);
            if (index == null) {
                throw new IllegalArgumentException("Required location_id is not present in matrix graph: " + vertex);
            }
            return index;
        }

        private double weight(int fromIndex, int toIndex) {
            return weightMatrix[fromIndex][toIndex];
        }
    }

    private static class MatrixShortestPathResult {
        private final boolean reachable;
        private final double totalCost;

        private MatrixShortestPathResult(boolean reachable, double totalCost) {
            this.reachable = reachable;
            this.totalCost = totalCost;
        }
    }

    private static class ExperimentMeasurement<T> {
        private final double averageMillis;
        private final T output;

        private ExperimentMeasurement(double averageMillis, T output) {
            this.averageMillis = averageMillis;
            this.output = output;
        }
    }
}
