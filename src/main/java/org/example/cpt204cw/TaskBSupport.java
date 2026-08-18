package org.example.cpt204cw;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;

/*
 * Weighted directed edge used inside the adjacency-list graph. Undirected CSV
 * paths are stored as two directed WeightedEdge entries.
 */
class WeightedEdge {
    private final String from;
    private final String to;
    private final double weight;

    public WeightedEdge(String from, String to, double weight) {
        this.from = from;
        this.to = to;
        this.weight = weight;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public double getWeight() {
        return weight;
    }

}

/*
 * Weighted graph backed by a map-of-maps adjacency list. Location IDs remain as
 * strings, matching the CSV data model and avoiding a separate public ID layer.
 */
class WeightedGraph {
    private final Map<String, Map<String, WeightedEdge>> adjacencyList;
    private final Set<String> undirectedEdgeKeys = new HashSet<>();
    private final Supplier<Map<String, WeightedEdge>> neighborMapFactory;

    public WeightedGraph() {
        this(LinkedHashMap::new, LinkedHashMap::new);
    }

    public WeightedGraph(
            Supplier<Map<String, Map<String, WeightedEdge>>> adjacencyListFactory,
            Supplier<Map<String, WeightedEdge>> neighborMapFactory) {
        if (adjacencyListFactory == null || neighborMapFactory == null) {
            throw new IllegalArgumentException("Map factories must not be null.");
        }
        Map<String, Map<String, WeightedEdge>> newAdjacencyList = adjacencyListFactory.get();
        if (newAdjacencyList == null) {
            throw new IllegalArgumentException("Outer map factory must not return null.");
        }
        this.adjacencyList = newAdjacencyList;
        this.neighborMapFactory = neighborMapFactory;
    }

    public void addUndirectedEdge(String from, String to, double weight) {
        validateEdgeInput(from, to, weight);
        String cleanedFrom = from.trim();
        String cleanedTo = to.trim();
        String key = undirectedKey(cleanedFrom, cleanedTo);
        boolean isNewUndirectedEdge = !undirectedEdgeKeys.contains(key);

        addDirectedEdge(cleanedFrom, cleanedTo, weight);
        if (!cleanedFrom.equals(cleanedTo)) {
            addDirectedEdge(cleanedTo, cleanedFrom, weight);
        }
        if (isNewUndirectedEdge) {
            undirectedEdgeKeys.add(key);
        }
    }

    public void addDirectedEdge(String from, String to, double weight) {
        validateEdgeInput(from, to, weight);
        String cleanedFrom = from.trim();
        String cleanedTo = to.trim();
        adjacencyList.computeIfAbsent(cleanedFrom, key -> createNeighborMap());
        adjacencyList.computeIfAbsent(cleanedTo, key -> createNeighborMap());

        Map<String, WeightedEdge> neighbors = adjacencyList.get(cleanedFrom);
        WeightedEdge existing = neighbors.get(cleanedTo);
        if (existing == null || weight < existing.getWeight()) {
            neighbors.put(cleanedTo, new WeightedEdge(cleanedFrom, cleanedTo, weight));
        }
    }

    public boolean containsVertex(String locationId) {
        return adjacencyList.containsKey(locationId);
    }

    public Set<String> getVertices() {
        return new LinkedHashSet<>(adjacencyList.keySet());
    }

    public List<WeightedEdge> getEdgesFrom(String locationId) {
        Map<String, WeightedEdge> neighbors = adjacencyList.get(locationId);
        if (neighbors == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(neighbors.values());
    }

    public int getVertexCount() {
        return adjacencyList.size();
    }

    public int getUndirectedEdgeCount() {
        return undirectedEdgeKeys.size();
    }

    public int getAdjacencyEntryCount() {
        int count = 0;
        for (Map<String, WeightedEdge> neighbors : adjacencyList.values()) {
            count += neighbors.size();
        }
        return count;
    }

    public double getEdgeWeight(String from, String to) {
        Map<String, WeightedEdge> neighbors = adjacencyList.get(from);
        if (neighbors == null || !neighbors.containsKey(to)) {
            return Double.POSITIVE_INFINITY;
        }
        return neighbors.get(to).getWeight();
    }

    public double calculatePathCost(List<String> path) {
        if (path == null || path.size() <= 1) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            double weight = getEdgeWeight(path.get(i), path.get(i + 1));
            if (Double.isInfinite(weight)) {
                throw new IllegalStateException(
                        "Path contains missing edge: " + path.get(i) + " -> " + path.get(i + 1));
            }
            total += weight;
        }
        return total;
    }

    public List<WeightedEdge> getAllDirectedEdges() {
        List<WeightedEdge> edges = new ArrayList<>();
        for (Map<String, WeightedEdge> neighbors : adjacencyList.values()) {
            edges.addAll(neighbors.values());
        }
        return edges;
    }

    public void validateVertexExists(String locationId) {
        if (!containsVertex(locationId)) {
            throw new IllegalStateException("Required location_id is not present in paths.csv graph: " + locationId);
        }
    }

    private void validateEdgeInput(String from, String to, double weight) {
        if (from == null || from.trim().isEmpty()) {
            throw new IllegalArgumentException("from_location must not be empty.");
        }
        if (to == null || to.trim().isEmpty()) {
            throw new IllegalArgumentException("to_location must not be empty.");
        }
        if (weight < 0.0) {
            throw new IllegalArgumentException("Dijkstra requires non-negative edge weights.");
        }
    }

    private String undirectedKey(String first, String second) {
        if (first.compareTo(second) <= 0) {
            return first + "\u0000" + second;
        }
        return second + "\u0000" + first;
    }

    private Map<String, WeightedEdge> createNeighborMap() {
        Map<String, WeightedEdge> neighbors = neighborMapFactory.get();
        if (neighbors == null) {
            throw new IllegalArgumentException("Neighbor map factory must not return null.");
        }
        return neighbors;
    }
}

/*
 * Runtime and search-operation counters shared by shortest-path algorithms and
 * aggregated by multi-segment routes.
 */
class PathSearchStats {
    private int visitedNodeCount;
    private int relaxationCount;
    private int queuePollCount;
    private int scanCount;
    private int containsCheckCount;
    private long runtimeNanos;

    public void incrementVisitedNodes() {
        visitedNodeCount++;
    }

    public void incrementRelaxations() {
        relaxationCount++;
    }

    public void incrementQueuePolls() {
        queuePollCount++;
    }

    public void incrementScanCount() {
        scanCount++;
    }

    public void incrementContainsCheckCount() {
        containsCheckCount++;
    }

    public void addRelaxations(int value) {
        relaxationCount += value;
    }

    public void setRuntimeNanos(long runtimeNanos) {
        this.runtimeNanos = runtimeNanos;
    }

    public int getVisitedNodeCount() {
        return visitedNodeCount;
    }

    public int getRelaxationCount() {
        return relaxationCount;
    }

    public int getQueuePollCount() {
        return queuePollCount;
    }

    public int getScanCount() {
        return scanCount;
    }

    public int getContainsCheckCount() {
        return containsCheckCount;
    }

    public long getRuntimeNanos() {
        return runtimeNanos;
    }

    public double getRuntimeMillis() {
        return runtimeNanos / 1_000_000.0;
    }

    public PathSearchStats add(PathSearchStats other) {
        PathSearchStats merged = new PathSearchStats();
        merged.visitedNodeCount = visitedNodeCount + other.visitedNodeCount;
        merged.relaxationCount = relaxationCount + other.relaxationCount;
        merged.queuePollCount = queuePollCount + other.queuePollCount;
        merged.scanCount = scanCount + other.scanCount;
        merged.containsCheckCount = containsCheckCount + other.containsCheckCount;
        merged.runtimeNanos = runtimeNanos + other.runtimeNanos;
        return merged;
    }

}

/*
 * Immutable result for one shortest-path query, including reachability, path,
 * cost, and algorithm-specific search statistics.
 */
class ShortestPathResult {
    private final String algorithmName;
    private final String start;
    private final String destination;
    private final List<String> path;
    private final double totalCost;
    private final boolean reachable;
    private final PathSearchStats stats;

    public ShortestPathResult(
            String algorithmName,
            String start,
            String destination,
            List<String> path,
            double totalCost,
            boolean reachable,
            PathSearchStats stats) {
        this.algorithmName = algorithmName;
        this.start = start;
        this.destination = destination;
        this.path = new ArrayList<>(path);
        this.totalCost = totalCost;
        this.reachable = reachable;
        this.stats = stats;
    }

    public static ShortestPathResult unreachable(
            String algorithmName,
            String start,
            String destination,
            PathSearchStats stats) {
        return new ShortestPathResult(
                algorithmName,
                start,
                destination,
                Collections.emptyList(),
                Double.POSITIVE_INFINITY,
                false,
                stats);
    }

    public String getAlgorithmName() {
        return algorithmName;
    }

    public String getStart() {
        return start;
    }

    public String getDestination() {
        return destination;
    }

    public List<String> getPath() {
        return new ArrayList<>(path);
    }

    public double getTotalCost() {
        return totalCost;
    }

    public boolean isReachable() {
        return reachable;
    }

    public PathSearchStats getStats() {
        return stats;
    }

    public String pathToString() {
        if (!reachable) {
            return "UNREACHABLE";
        }
        return String.join(" -> ", path);
    }

}

/*
 * Shared abstraction for all shortest-path solvers used by the route planner.
 */
interface ShortestPathSolver {
    ShortestPathResult findShortestPath(WeightedGraph graph, String start, String destination);
}

/*
 * Small path helpers used by multiple shortest-path and route-planning classes.
 */
final class PathResultUtils {
    private PathResultUtils() {
    }

    static List<String> singletonPath(String vertex) {
        List<String> path = new ArrayList<>();
        path.add(vertex);
        return path;
    }

    static List<String> reconstructPath(String start, String destination, Map<String, String> parent) {
        LinkedList<String> path = new LinkedList<>();
        String current = destination;
        path.addFirst(current);
        while (!current.equals(start)) {
            current = parent.get(current);
            if (current == null) {
                return Collections.emptyList();
            }
            path.addFirst(current);
        }
        return path;
    }

    static void appendSegmentPath(List<String> fullPath, List<String> segmentPath) {
        for (int i = 0; i < segmentPath.size(); i++) {
            if (!fullPath.isEmpty() && i == 0 && fullPath.get(fullPath.size() - 1).equals(segmentPath.get(i))) {
                continue;
            }
            fullPath.add(segmentPath.get(i));
        }
    }
}

/*
 * Lecture-style Dijkstra implementation that keeps the full-scan finalisation
 * behaviour used as the required weighted shortest-path baseline.
 */
class LectureDijkstraShortestPath implements ShortestPathSolver {
    private static final String NAME = "Lecture-Style Dijkstra (ArrayList T + Full Scan)";

    /*
     * This implementation follows the W10 lecture version of Dijkstra:
     * ArrayList T stores vertices whose shortest paths are finalised, every
     * iteration scans all vertices to select the unfinalised vertex with the
     * smallest current cost. A boolean mirror of T is used for membership
     * checks so long multi-waypoint benchmarks still complete while preserving
     * the same finalisation order and relaxation rules.
     */
    @Override
    public ShortestPathResult findShortestPath(WeightedGraph graph, String start, String destination) {
        long startTime = System.nanoTime();
        graph.validateVertexExists(start);
        graph.validateVertexExists(destination);
        PathSearchStats stats = new PathSearchStats();

        if (start.equals(destination)) {
            stats.incrementVisitedNodes();
            stats.setRuntimeNanos(System.nanoTime() - startTime);
            return new ShortestPathResult(NAME, start, destination, PathResultUtils.singletonPath(start), 0.0, true, stats);
        }

        List<String> vertices = new ArrayList<>(graph.getVertices());
        Map<String, Integer> indexByVertex = indexVertices(vertices);
        int vertexCount = vertices.size();
        double[] cost = new double[vertexCount];
        int[] parent = new int[vertexCount];
        Arrays.fill(cost, Double.POSITIVE_INFINITY);
        Arrays.fill(parent, -1);

        int sourceIndex = indexByVertex.get(start);
        int destinationIndex = indexByVertex.get(destination);
        cost[sourceIndex] = 0.0;

        List<Integer> finalised = new ArrayList<>();
        boolean[] isFinalised = new boolean[vertexCount];
        while (finalised.size() < vertexCount) {
            int bestIndex = -1;
            double bestCost = Double.POSITIVE_INFINITY;
            for (int i = 0; i < vertexCount; i++) {
                stats.incrementScanCount();
                stats.incrementContainsCheckCount();
                if (!isFinalised[i] && cost[i] < bestCost) {
                    bestCost = cost[i];
                    bestIndex = i;
                }
            }
            if (bestIndex < 0 || Double.isInfinite(bestCost)) {
                break;
            }

            finalised.add(bestIndex);
            isFinalised[bestIndex] = true;
            stats.incrementVisitedNodes();
            String currentVertex = vertices.get(bestIndex);
            for (WeightedEdge edge : graph.getEdgesFrom(currentVertex)) {
                int neighborIndex = indexByVertex.get(edge.getTo());
                stats.incrementContainsCheckCount();
                if (!isFinalised[neighborIndex]
                        && cost[neighborIndex] > cost[bestIndex] + edge.getWeight()) {
                    cost[neighborIndex] = cost[bestIndex] + edge.getWeight();
                    parent[neighborIndex] = bestIndex;
                    stats.incrementRelaxations();
                }
            }
        }

        stats.setRuntimeNanos(System.nanoTime() - startTime);
        if (Double.isInfinite(cost[destinationIndex])) {
            return ShortestPathResult.unreachable(NAME, start, destination, stats);
        }
        return new ShortestPathResult(
                NAME,
                start,
                destination,
                reconstructIndexedPath(vertices, parent, sourceIndex, destinationIndex),
                cost[destinationIndex],
                true,
                stats);
    }

    private Map<String, Integer> indexVertices(List<String> vertices) {
        Map<String, Integer> indexByVertex = new HashMap<>();
        for (int i = 0; i < vertices.size(); i++) {
            indexByVertex.put(vertices.get(i), i);
        }
        return indexByVertex;
    }

    private List<String> reconstructIndexedPath(
            List<String> vertices,
            int[] parent,
            int sourceIndex,
            int destinationIndex) {
        LinkedList<String> path = new LinkedList<>();
        int current = destinationIndex;
        while (current >= 0) {
            path.addFirst(vertices.get(current));
            if (current == sourceIndex) {
                break;
            }
            current = parent[current];
        }
        return path;
    }

}

/*
 * Priority-queue Dijkstra implementation used as the practical weighted
 * shortest-path solver for larger route queries.
 */
class OptimisedDijkstraShortestPath implements ShortestPathSolver {
    private static final String NAME = "PriorityQueue Dijkstra with Early Stopping";

    @Override
    public ShortestPathResult findShortestPath(WeightedGraph graph, String start, String destination) {
        long startTime = System.nanoTime();
        graph.validateVertexExists(start);
        graph.validateVertexExists(destination);
        PathSearchStats stats = new PathSearchStats();

        if (start.equals(destination)) {
            stats.incrementVisitedNodes();
            stats.setRuntimeNanos(System.nanoTime() - startTime);
            return new ShortestPathResult(NAME, start, destination, PathResultUtils.singletonPath(start), 0.0, true, stats);
        }

        Map<String, Double> distance = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        Set<String> finalised = new HashSet<>();
        PriorityQueue<NodeDistance> queue = new PriorityQueue<>();

        distance.put(start, 0.0);
        queue.add(new NodeDistance(start, 0.0));

        while (!queue.isEmpty()) {
            NodeDistance current = queue.poll();
            stats.incrementQueuePolls();
            if (finalised.contains(current.vertex)) {
                continue;
            }
            finalised.add(current.vertex);
            stats.incrementVisitedNodes();
            if (current.vertex.equals(destination)) {
                break;
            }

            for (WeightedEdge edge : graph.getEdgesFrom(current.vertex)) {
                if (finalised.contains(edge.getTo())) {
                    continue;
                }
                double newDistance = current.distance + edge.getWeight();
                double oldDistance = distance.getOrDefault(edge.getTo(), Double.POSITIVE_INFINITY);
                if (newDistance < oldDistance) {
                    distance.put(edge.getTo(), newDistance);
                    parent.put(edge.getTo(), current.vertex);
                    queue.add(new NodeDistance(edge.getTo(), newDistance));
                    stats.incrementRelaxations();
                }
            }
        }

        stats.setRuntimeNanos(System.nanoTime() - startTime);
        if (!distance.containsKey(destination)) {
            return ShortestPathResult.unreachable(NAME, start, destination, stats);
        }
        return new ShortestPathResult(
                NAME,
                start,
                destination,
                PathResultUtils.reconstructPath(start, destination, parent),
                distance.get(destination),
                true,
                stats);
    }

    protected static class NodeDistance implements Comparable<NodeDistance> {
        private final String vertex;
        private final double distance;

        private NodeDistance(String vertex, double distance) {
            this.vertex = vertex;
            this.distance = distance;
        }

        @Override
        public int compareTo(NodeDistance other) {
            int byDistance = Double.compare(distance, other.distance);
            if (byDistance != 0) {
                return byDistance;
            }
            return vertex.compareTo(other.vertex);
        }
    }
}

/*
 * A* implementation using ALT landmark lower bounds instead of coordinates.
 * Landmarks are preprocessed once per graph and reused for query heuristics.
 */
class AStarAltShortestPath implements ShortestPathSolver {
    private static final String NAME = "A* with ALT Landmark Heuristic";
    private static final int DEFAULT_LANDMARK_COUNT = 4;

    private final int landmarkCount;
    private WeightedGraph preprocessedGraph;
    private List<String> landmarks = new ArrayList<>();
    private Map<String, Map<String, Double>> landmarkDistances = new LinkedHashMap<>();
    private long preprocessingRuntimeNanos;

    public AStarAltShortestPath() {
        this(DEFAULT_LANDMARK_COUNT);
    }

    public AStarAltShortestPath(int landmarkCount) {
        if (landmarkCount <= 0) {
            throw new IllegalArgumentException("ALT landmark count must be positive.");
        }
        this.landmarkCount = landmarkCount;
    }

    /*
     * ALT does not use coordinates or Euclidean distance. It uses shortest-path
     * distances from deterministic graph landmarks. In an undirected graph with
     * non-negative weights, |dist(L, target) - dist(L, vertex)| is a lower bound
     * by the triangle inequality, so the A* heuristic remains admissible.
     */
    @Override
    public ShortestPathResult findShortestPath(WeightedGraph graph, String start, String destination) {
        graph.validateVertexExists(start);
        graph.validateVertexExists(destination);
        ensurePreprocessed(graph);

        long startTime = System.nanoTime();
        PathSearchStats stats = new PathSearchStats();

        if (start.equals(destination)) {
            stats.incrementVisitedNodes();
            stats.setRuntimeNanos(System.nanoTime() - startTime);
            return new ShortestPathResult(NAME, start, destination, PathResultUtils.singletonPath(start), 0.0, true, stats);
        }

        Map<String, Double> gScore = new HashMap<>();
        Map<String, String> predecessor = new HashMap<>();
        Set<String> visited = new HashSet<>();
        PriorityQueue<NodeScore> queue = new PriorityQueue<>();

        gScore.put(start, 0.0);
        queue.add(new NodeScore(start, 0.0, heuristic(start, destination)));

        while (!queue.isEmpty()) {
            NodeScore current = queue.poll();
            stats.incrementQueuePolls();
            double bestKnown = gScore.getOrDefault(current.vertex, Double.POSITIVE_INFINITY);
            if (current.gScore > bestKnown) {
                continue;
            }
            if (visited.contains(current.vertex)) {
                continue;
            }

            visited.add(current.vertex);
            stats.incrementVisitedNodes();
            if (current.vertex.equals(destination)) {
                break;
            }

            for (WeightedEdge edge : graph.getEdgesFrom(current.vertex)) {
                if (visited.contains(edge.getTo())) {
                    continue;
                }
                double tentative = gScore.get(current.vertex) + edge.getWeight();
                double oldScore = gScore.getOrDefault(edge.getTo(), Double.POSITIVE_INFINITY);
                if (tentative < oldScore) {
                    gScore.put(edge.getTo(), tentative);
                    predecessor.put(edge.getTo(), current.vertex);
                    queue.add(new NodeScore(
                            edge.getTo(),
                            tentative,
                            tentative + heuristic(edge.getTo(), destination)));
                    stats.incrementRelaxations();
                }
            }
        }

        stats.setRuntimeNanos(System.nanoTime() - startTime);
        if (!visited.contains(destination)) {
            return ShortestPathResult.unreachable(NAME, start, destination, stats);
        }
        return new ShortestPathResult(
                NAME,
                start,
                destination,
                PathResultUtils.reconstructPath(start, destination, predecessor),
                gScore.get(destination),
                true,
                stats);
    }

    public void prepare(WeightedGraph graph) {
        ensurePreprocessed(graph);
    }

    public List<String> getLandmarks() {
        return new ArrayList<>(landmarks);
    }

    public double getPreprocessingRuntimeMillis() {
        return preprocessingRuntimeNanos / 1_000_000.0;
    }

    private void ensurePreprocessed(WeightedGraph graph) {
        if (preprocessedGraph == graph && !landmarkDistances.isEmpty()) {
            return;
        }
        long startTime = System.nanoTime();
        preprocessedGraph = graph;
        landmarks = new ArrayList<>();
        landmarkDistances = new LinkedHashMap<>();

        List<String> vertices = new ArrayList<>(graph.getVertices());
        if (vertices.isEmpty()) {
            preprocessingRuntimeNanos = System.nanoTime() - startTime;
            return;
        }

        String seed = vertices.get(0);
        Map<String, Double> seedDistances = computeAllDistancesFrom(graph, seed);
        String firstLandmark = selectFarthestReachable(seedDistances, new HashSet<String>());
        addLandmarkIfPresent(graph, firstLandmark);

        if (!landmarks.isEmpty() && landmarks.size() < landmarkCount) {
            String secondLandmark = selectFarthestReachable(
                    landmarkDistances.get(landmarks.get(0)),
                    new HashSet<String>(landmarks));
            addLandmarkIfPresent(graph, secondLandmark);
        }

        while (landmarks.size() < landmarkCount) {
            String nextLandmark = selectFarthestFirst(vertices);
            if (nextLandmark == null) {
                break;
            }
            addLandmarkIfPresent(graph, nextLandmark);
        }

        preprocessingRuntimeNanos = System.nanoTime() - startTime;
    }

    private void addLandmarkIfPresent(WeightedGraph graph, String landmark) {
        if (landmark == null || landmarkDistances.containsKey(landmark)) {
            return;
        }
        landmarks.add(landmark);
        landmarkDistances.put(landmark, computeAllDistancesFrom(graph, landmark));
    }

    private String selectFarthestReachable(Map<String, Double> distances, Set<String> excluded) {
        String bestVertex = null;
        double bestDistance = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, Double> entry : distances.entrySet()) {
            String vertex = entry.getKey();
            double distance = entry.getValue();
            if (excluded.contains(vertex) || Double.isInfinite(distance)) {
                continue;
            }
            if (distance > bestDistance
                    || (Double.compare(distance, bestDistance) == 0
                    && (bestVertex == null || vertex.compareTo(bestVertex) < 0))) {
                bestDistance = distance;
                bestVertex = vertex;
            }
        }
        return bestVertex;
    }

    private String selectFarthestFirst(List<String> vertices) {
        String bestVertex = null;
        double bestMinDistance = Double.NEGATIVE_INFINITY;
        Set<String> selected = new HashSet<>(landmarks);
        for (String vertex : vertices) {
            if (selected.contains(vertex)) {
                continue;
            }
            double minDistanceToLandmarks = Double.POSITIVE_INFINITY;
            boolean reachableFromLandmark = false;
            for (String landmark : landmarks) {
                double distance = landmarkDistances.get(landmark).getOrDefault(vertex, Double.POSITIVE_INFINITY);
                if (!Double.isInfinite(distance)) {
                    reachableFromLandmark = true;
                    if (distance < minDistanceToLandmarks) {
                        minDistanceToLandmarks = distance;
                    }
                }
            }
            if (!reachableFromLandmark) {
                continue;
            }
            if (minDistanceToLandmarks > bestMinDistance
                    || (Double.compare(minDistanceToLandmarks, bestMinDistance) == 0
                    && (bestVertex == null || vertex.compareTo(bestVertex) < 0))) {
                bestMinDistance = minDistanceToLandmarks;
                bestVertex = vertex;
            }
        }
        return bestVertex;
    }

    private Map<String, Double> computeAllDistancesFrom(WeightedGraph graph, String source) {
        Map<String, Double> distance = new LinkedHashMap<>();
        for (String vertex : graph.getVertices()) {
            distance.put(vertex, Double.POSITIVE_INFINITY);
        }

        PriorityQueue<DistanceNode> queue = new PriorityQueue<>();
        Set<String> finalised = new HashSet<>();
        distance.put(source, 0.0);
        queue.add(new DistanceNode(source, 0.0));

        while (!queue.isEmpty()) {
            DistanceNode current = queue.poll();
            if (finalised.contains(current.vertex)) {
                continue;
            }
            finalised.add(current.vertex);
            for (WeightedEdge edge : graph.getEdgesFrom(current.vertex)) {
                if (finalised.contains(edge.getTo())) {
                    continue;
                }
                double candidate = current.distance + edge.getWeight();
                if (candidate < distance.getOrDefault(edge.getTo(), Double.POSITIVE_INFINITY)) {
                    distance.put(edge.getTo(), candidate);
                    queue.add(new DistanceNode(edge.getTo(), candidate));
                }
            }
        }
        return distance;
    }

    private double heuristic(String vertex, String target) {
        double best = 0.0;
        for (String landmark : landmarks) {
            Map<String, Double> distances = landmarkDistances.get(landmark);
            double landmarkToVertex = distances.getOrDefault(vertex, Double.POSITIVE_INFINITY);
            double landmarkToTarget = distances.getOrDefault(target, Double.POSITIVE_INFINITY);
            if (Double.isInfinite(landmarkToVertex) || Double.isInfinite(landmarkToTarget)) {
                continue;
            }
            best = Math.max(best, Math.abs(landmarkToTarget - landmarkToVertex));
        }
        return best;
    }

    private static class DistanceNode implements Comparable<DistanceNode> {
        private final String vertex;
        private final double distance;

        private DistanceNode(String vertex, double distance) {
            this.vertex = vertex;
            this.distance = distance;
        }

        @Override
        public int compareTo(DistanceNode other) {
            int byDistance = Double.compare(distance, other.distance);
            if (byDistance != 0) {
                return byDistance;
            }
            return vertex.compareTo(other.vertex);
        }
    }

    private static class NodeScore implements Comparable<NodeScore> {
        private final String vertex;
        private final double gScore;
        private final double fScore;

        private NodeScore(String vertex, double gScore, double fScore) {
            this.vertex = vertex;
            this.gScore = gScore;
            this.fScore = fScore;
        }

        @Override
        public int compareTo(NodeScore other) {
            int byFScore = Double.compare(fScore, other.fScore);
            if (byFScore != 0) {
                return byFScore;
            }
            int byGScore = Double.compare(gScore, other.gScore);
            if (byGScore != 0) {
                return byGScore;
            }
            return vertex.compareTo(other.vertex);
        }
    }
}

/*
 * Bidirectional Dijkstra expands from both endpoints and joins the two search
 * frontiers when a best meeting point is found.
 */
class BidirectionalDijkstraShortestPath implements ShortestPathSolver {
    private static final String NAME = "Bidirectional Dijkstra";

    @Override
    public ShortestPathResult findShortestPath(WeightedGraph graph, String start, String destination) {
        long startTime = System.nanoTime();
        graph.validateVertexExists(start);
        graph.validateVertexExists(destination);
        PathSearchStats stats = new PathSearchStats();

        if (start.equals(destination)) {
            stats.incrementVisitedNodes();
            stats.setRuntimeNanos(System.nanoTime() - startTime);
            return new ShortestPathResult(NAME, start, destination, PathResultUtils.singletonPath(start), 0.0, true, stats);
        }

        Map<String, Double> forwardDistance = new HashMap<>();
        Map<String, Double> backwardDistance = new HashMap<>();
        Map<String, String> forwardParent = new HashMap<>();
        Map<String, String> backwardParent = new HashMap<>();
        Set<String> forwardFinalised = new HashSet<>();
        Set<String> backwardFinalised = new HashSet<>();
        PriorityQueue<QueueNode> forwardQueue = new PriorityQueue<>();
        PriorityQueue<QueueNode> backwardQueue = new PriorityQueue<>();

        forwardDistance.put(start, 0.0);
        backwardDistance.put(destination, 0.0);
        forwardQueue.add(new QueueNode(start, 0.0));
        backwardQueue.add(new QueueNode(destination, 0.0));

        double bestDistance = Double.POSITIVE_INFINITY;
        String bestMeetingVertex = null;

        while (!forwardQueue.isEmpty() && !backwardQueue.isEmpty()) {
            if (forwardQueue.peek().distance + backwardQueue.peek().distance >= bestDistance) {
                break;
            }
            if (forwardQueue.peek().distance <= backwardQueue.peek().distance) {
                SearchStep step = expandFrontier(
                        graph,
                        forwardQueue,
                        forwardDistance,
                        backwardDistance,
                        forwardParent,
                        forwardFinalised,
                        stats);
                if (step.meetingVertex != null && step.meetingDistance < bestDistance) {
                    bestDistance = step.meetingDistance;
                    bestMeetingVertex = step.meetingVertex;
                }
            } else {
                SearchStep step = expandFrontier(
                        graph,
                        backwardQueue,
                        backwardDistance,
                        forwardDistance,
                        backwardParent,
                        backwardFinalised,
                        stats);
                if (step.meetingVertex != null && step.meetingDistance < bestDistance) {
                    bestDistance = step.meetingDistance;
                    bestMeetingVertex = step.meetingVertex;
                }
            }
        }

        stats.setRuntimeNanos(System.nanoTime() - startTime);
        if (bestMeetingVertex == null) {
            return ShortestPathResult.unreachable(NAME, start, destination, stats);
        }

        List<String> path = reconstructBidirectionalPath(
                start,
                destination,
                bestMeetingVertex,
                forwardParent,
                backwardParent);
        return new ShortestPathResult(NAME, start, destination, path, bestDistance, true, stats);
    }

    private SearchStep expandFrontier(
            WeightedGraph graph,
            PriorityQueue<QueueNode> queue,
            Map<String, Double> ownDistance,
            Map<String, Double> oppositeDistance,
            Map<String, String> ownParent,
            Set<String> ownFinalised,
            PathSearchStats stats) {
        QueueNode current = queue.poll();
        stats.incrementQueuePolls();
        if (ownFinalised.contains(current.vertex)) {
            return new SearchStep(null, Double.POSITIVE_INFINITY);
        }

        ownFinalised.add(current.vertex);
        stats.incrementVisitedNodes();
        String meetingVertex = null;
        double meetingDistance = Double.POSITIVE_INFINITY;
        if (oppositeDistance.containsKey(current.vertex)) {
            meetingVertex = current.vertex;
            meetingDistance = ownDistance.get(current.vertex) + oppositeDistance.get(current.vertex);
        }

        for (WeightedEdge edge : graph.getEdgesFrom(current.vertex)) {
            if (ownFinalised.contains(edge.getTo())) {
                continue;
            }
            double newDistance = ownDistance.get(current.vertex) + edge.getWeight();
            double oldDistance = ownDistance.getOrDefault(edge.getTo(), Double.POSITIVE_INFINITY);
            if (newDistance < oldDistance) {
                ownDistance.put(edge.getTo(), newDistance);
                ownParent.put(edge.getTo(), current.vertex);
                queue.add(new QueueNode(edge.getTo(), newDistance));
                stats.incrementRelaxations();
            }
            if (oppositeDistance.containsKey(edge.getTo())) {
                double candidateDistance = newDistance + oppositeDistance.get(edge.getTo());
                if (candidateDistance < meetingDistance) {
                    meetingDistance = candidateDistance;
                    meetingVertex = edge.getTo();
                }
            }
        }
        return new SearchStep(meetingVertex, meetingDistance);
    }

    private List<String> reconstructBidirectionalPath(
            String start,
            String destination,
            String meetingVertex,
            Map<String, String> forwardParent,
            Map<String, String> backwardParent) {
        LinkedList<String> path = new LinkedList<>();
        String current = meetingVertex;
        path.addFirst(current);
        while (!current.equals(start)) {
            current = forwardParent.get(current);
            if (current == null) {
                return Collections.emptyList();
            }
            path.addFirst(current);
        }

        current = meetingVertex;
        while (!current.equals(destination)) {
            current = backwardParent.get(current);
            if (current == null) {
                return Collections.emptyList();
            }
            path.addLast(current);
        }
        return path;
    }

    private static class QueueNode implements Comparable<QueueNode> {
        private final String vertex;
        private final double distance;

        private QueueNode(String vertex, double distance) {
            this.vertex = vertex;
            this.distance = distance;
        }

        @Override
        public int compareTo(QueueNode other) {
            int byDistance = Double.compare(distance, other.distance);
            if (byDistance != 0) {
                return byDistance;
            }
            return vertex.compareTo(other.vertex);
        }
    }

    private static class SearchStep {
        private final String meetingVertex;
        private final double meetingDistance;

        private SearchStep(String meetingVertex, double meetingDistance) {
            this.meetingVertex = meetingVertex;
            this.meetingDistance = meetingDistance;
        }
    }
}

/*
 * Bellman-Ford implementation included for algorithm comparison and validation
 * against Dijkstra on non-negative weighted paths.
 */
class BellmanFordShortestPath implements ShortestPathSolver {
    private static final String NAME = "Bellman-Ford";
    private static final double EPSILON = 1e-12;

    @Override
    public ShortestPathResult findShortestPath(WeightedGraph graph, String start, String destination) {
        long startTime = System.nanoTime();
        graph.validateVertexExists(start);
        graph.validateVertexExists(destination);
        PathSearchStats stats = new PathSearchStats();

        if (start.equals(destination)) {
            stats.incrementVisitedNodes();
            stats.setRuntimeNanos(System.nanoTime() - startTime);
            return new ShortestPathResult(NAME, start, destination, PathResultUtils.singletonPath(start), 0.0, true, stats);
        }

        Set<String> vertices = graph.getVertices();
        Map<String, Double> distance = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        for (String vertex : vertices) {
            distance.put(vertex, Double.POSITIVE_INFINITY);
        }
        distance.put(start, 0.0);

        List<WeightedEdge> edges = graph.getAllDirectedEdges();
        for (int i = 0; i < vertices.size() - 1; i++) {
            boolean updated = false;
            for (WeightedEdge edge : edges) {
                double fromDistance = distance.get(edge.getFrom());
                if (Double.isInfinite(fromDistance)) {
                    continue;
                }
                double candidate = fromDistance + edge.getWeight();
                if (candidate + EPSILON < distance.get(edge.getTo())) {
                    distance.put(edge.getTo(), candidate);
                    parent.put(edge.getTo(), edge.getFrom());
                    stats.incrementRelaxations();
                    updated = true;
                }
            }
            if (!updated) {
                break;
            }
        }

        for (WeightedEdge edge : edges) {
            double fromDistance = distance.get(edge.getFrom());
            if (!Double.isInfinite(fromDistance)
                    && fromDistance + edge.getWeight() + EPSILON < distance.get(edge.getTo())) {
                throw new IllegalStateException("Bellman-Ford detected a negative cycle.");
            }
        }

        stats.setRuntimeNanos(System.nanoTime() - startTime);
        if (Double.isInfinite(distance.get(destination))) {
            return ShortestPathResult.unreachable(NAME, start, destination, stats);
        }
        return new ShortestPathResult(
                NAME,
                start,
                destination,
                PathResultUtils.reconstructPath(start, destination, parent),
                distance.get(destination),
                true,
                stats);
    }

}

/*
 * All-pairs shortest-path implementation. It is only built when the graph is
 * small enough for the O(V^2) memory and O(V^3) preprocessing cost.
 */
class FloydWarshallAllPairsShortestPath {
    public static final int MAX_FLOYD_WARSHALL_VERTICES = 1000;

    private final boolean executed;
    private final int vertexCount;
    private final List<String> vertices;
    private final Map<String, Integer> indexByVertex;
    private final double[][] distance;
    private final int[][] next;
    private final int updateCount;
    private final long preprocessingRuntimeNanos;

    private FloydWarshallAllPairsShortestPath(
            boolean executed,
            int vertexCount,
            List<String> vertices,
            Map<String, Integer> indexByVertex,
            double[][] distance,
            int[][] next,
            int updateCount,
            long preprocessingRuntimeNanos) {
        this.executed = executed;
        this.vertexCount = vertexCount;
        this.vertices = vertices;
        this.indexByVertex = indexByVertex;
        this.distance = distance;
        this.next = next;
        this.updateCount = updateCount;
        this.preprocessingRuntimeNanos = preprocessingRuntimeNanos;
    }

    public static FloydWarshallAllPairsShortestPath buildIfSuitable(WeightedGraph graph) {
        int vertexCount = graph.getVertexCount();
        if (vertexCount > MAX_FLOYD_WARSHALL_VERTICES) {
            return new FloydWarshallAllPairsShortestPath(
                    false,
                    vertexCount,
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    null,
                    null,
                    0,
                    0L);
        }

        long startTime = System.nanoTime();
        List<String> vertices = new ArrayList<>(graph.getVertices());
        Map<String, Integer> indexByVertex = new HashMap<>();
        for (int i = 0; i < vertices.size(); i++) {
            indexByVertex.put(vertices.get(i), i);
        }

        double[][] distance = new double[vertexCount][vertexCount];
        int[][] next = new int[vertexCount][vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            Arrays.fill(distance[i], Double.POSITIVE_INFINITY);
            Arrays.fill(next[i], -1);
            distance[i][i] = 0.0;
            next[i][i] = i;
        }

        for (WeightedEdge edge : graph.getAllDirectedEdges()) {
            int from = indexByVertex.get(edge.getFrom());
            int to = indexByVertex.get(edge.getTo());
            if (edge.getWeight() < distance[from][to]) {
                distance[from][to] = edge.getWeight();
                next[from][to] = to;
            }
        }

        int updates = 0;
        for (int k = 0; k < vertexCount; k++) {
            for (int i = 0; i < vertexCount; i++) {
                if (Double.isInfinite(distance[i][k])) {
                    continue;
                }
                for (int j = 0; j < vertexCount; j++) {
                    double candidate = distance[i][k] + distance[k][j];
                    if (candidate < distance[i][j]) {
                        distance[i][j] = candidate;
                        next[i][j] = next[i][k];
                        updates++;
                    }
                }
            }
        }

        return new FloydWarshallAllPairsShortestPath(
                true,
                vertexCount,
                vertices,
                indexByVertex,
                distance,
                next,
                updates,
                System.nanoTime() - startTime);
    }

    public boolean isExecuted() {
        return executed;
    }

    public int getVertexCount() {
        return vertexCount;
    }

    public int getUpdateCount() {
        return updateCount;
    }

    public double getPreprocessingRuntimeMillis() {
        return preprocessingRuntimeNanos / 1_000_000.0;
    }

    public double getDistance(String start, String destination) {
        ensureExecuted();
        return distance[indexByVertex.get(start)][indexByVertex.get(destination)];
    }

    public ShortestPathResult getShortestPath(String start, String destination) {
        ensureExecuted();
        long queryStart = System.nanoTime();
        PathSearchStats stats = new PathSearchStats();
        stats.addRelaxations(updateCount);
        int startIndex = indexByVertex.get(start);
        int destinationIndex = indexByVertex.get(destination);
        if (next[startIndex][destinationIndex] == -1) {
            stats.setRuntimeNanos(System.nanoTime() - queryStart);
            return ShortestPathResult.unreachable("Floyd-Warshall", start, destination, stats);
        }

        List<String> path = new ArrayList<>();
        int current = startIndex;
        path.add(vertices.get(current));
        while (current != destinationIndex) {
            current = next[current][destinationIndex];
            path.add(vertices.get(current));
        }
        stats.setRuntimeNanos(System.nanoTime() - queryStart);
        return new ShortestPathResult(
                "Floyd-Warshall",
                start,
                destination,
                path,
                distance[startIndex][destinationIndex],
                true,
                stats);
    }

    private void ensureExecuted() {
        if (!executed) {
            throw new IllegalStateException("Floyd-Warshall was skipped for this graph size.");
        }
    }
}

/*
 * Unweighted hop-count baseline used for comparison reports, not for the
 * weighted JavaFX route-planning selector.
 */
class BfsUnweightedPath implements ShortestPathSolver {
    private static final String NAME = "BFS Unweighted Baseline";

    @Override
    public ShortestPathResult findShortestPath(WeightedGraph graph, String start, String destination) {
        long startTime = System.nanoTime();
        graph.validateVertexExists(start);
        graph.validateVertexExists(destination);
        PathSearchStats stats = new PathSearchStats();

        if (start.equals(destination)) {
            stats.incrementVisitedNodes();
            stats.setRuntimeNanos(System.nanoTime() - startTime);
            return new ShortestPathResult(NAME, start, destination, PathResultUtils.singletonPath(start), 0.0, true, stats);
        }

        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        Map<String, String> parent = new HashMap<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            String current = queue.remove();
            stats.incrementVisitedNodes();
            if (current.equals(destination)) {
                break;
            }
            for (WeightedEdge edge : graph.getEdgesFrom(current)) {
                if (!visited.contains(edge.getTo())) {
                    visited.add(edge.getTo());
                    parent.put(edge.getTo(), current);
                    queue.add(edge.getTo());
                    stats.incrementRelaxations();
                }
            }
        }

        stats.setRuntimeNanos(System.nanoTime() - startTime);
        if (!visited.contains(destination)) {
            return ShortestPathResult.unreachable(NAME, start, destination, stats);
        }

        List<String> path = PathResultUtils.reconstructPath(start, destination, parent);
        return new ShortestPathResult(
                NAME,
                start,
                destination,
                path,
                graph.calculatePathCost(path),
                true,
                stats);
    }

}

/*
 * Selected target exported by Task A and consumed by Task B route cases.
 */
class SelectedTarget {
    private final String symbol;
    private final String dataset;
    private final int rank;
    private final String locationId;
    private final int priorityScore;

    public SelectedTarget(String symbol, String dataset, int rank, String locationId, int priorityScore) {
        this.symbol = symbol;
        this.dataset = dataset;
        this.rank = rank;
        this.locationId = locationId;
        this.priorityScore = priorityScore;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getDataset() {
        return dataset;
    }

    public int getRank() {
        return rank;
    }

    public String getLocationId() {
        return locationId;
    }

    public int getPriorityScore() {
        return priorityScore;
    }
}

/*
 * Convenience wrapper around the 30 selected targets plus the required Task B
 * symbols used by benchmark cases.
 */
class TaskBTargetSet {
    private final Map<String, SelectedTarget> allTargetsBySymbol;
    private final SelectedTarget a1;
    private final SelectedTarget a10;
    private final SelectedTarget b1;
    private final SelectedTarget b5;
    private final SelectedTarget c1;
    private final SelectedTarget c5;

    public TaskBTargetSet(Map<String, SelectedTarget> allTargetsBySymbol) {
        this.allTargetsBySymbol = new LinkedHashMap<>(allTargetsBySymbol);
        this.a1 = getBySymbol("A1");
        this.a10 = getBySymbol("A10");
        this.b1 = getBySymbol("B1");
        this.b5 = getBySymbol("B5");
        this.c1 = getBySymbol("C1");
        this.c5 = getBySymbol("C5");
    }

    public SelectedTarget getA1() {
        return a1;
    }

    public SelectedTarget getA10() {
        return a10;
    }

    public SelectedTarget getB1() {
        return b1;
    }

    public SelectedTarget getB5() {
        return b5;
    }

    public SelectedTarget getC1() {
        return c1;
    }

    public SelectedTarget getC5() {
        return c5;
    }

    public Map<String, SelectedTarget> getAllTargetsBySymbol() {
        return new LinkedHashMap<>(allTargetsBySymbol);
    }

    public List<SelectedTarget> getAllTargets() {
        return new ArrayList<>(allTargetsBySymbol.values());
    }

    public SelectedTarget getBySymbol(String symbol) {
        SelectedTarget target = allTargetsBySymbol.get(symbol);
        if (target == null) {
            throw new IllegalArgumentException("Unknown selected target symbol: " + symbol);
        }
        return target;
    }

    public List<SelectedTarget> getRequiredTargets() {
        List<SelectedTarget> targets = new ArrayList<>();
        targets.add(a1);
        targets.add(a10);
        targets.add(b1);
        targets.add(b5);
        targets.add(c1);
        targets.add(c5);
        return targets;
    }
}

/*
 * Route request model for a fixed-order journey from start through waypoints to
 * destination.
 */
class RouteQuery {
    private final String caseId;
    private final String description;
    private final String startSymbol;
    private final String startLocation;
    private final List<String> waypointSymbols;
    private final List<String> waypointLocations;
    private final String destinationSymbol;
    private final String destinationLocation;

    public RouteQuery(
            String caseId,
            String description,
            String startSymbol,
            String startLocation,
            List<String> waypointSymbols,
            List<String> waypointLocations,
            String destinationSymbol,
            String destinationLocation) {
        this.caseId = caseId;
        this.description = description;
        this.startSymbol = startSymbol;
        this.startLocation = startLocation;
        this.waypointSymbols = new ArrayList<>(waypointSymbols);
        this.waypointLocations = new ArrayList<>(waypointLocations);
        this.destinationSymbol = destinationSymbol;
        this.destinationLocation = destinationLocation;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getDescription() {
        return description;
    }

    public String getStartSymbol() {
        return startSymbol;
    }

    public String getStartLocation() {
        return startLocation;
    }

    public List<String> getWaypointSymbols() {
        return new ArrayList<>(waypointSymbols);
    }

    public List<String> getWaypointLocations() {
        return new ArrayList<>(waypointLocations);
    }

    public String getDestinationSymbol() {
        return destinationSymbol;
    }

    public String getDestinationLocation() {
        return destinationLocation;
    }

    public List<String> getOrderedStopSymbols() {
        List<String> stops = new ArrayList<>();
        stops.add(startSymbol);
        stops.addAll(waypointSymbols);
        stops.add(destinationSymbol);
        return stops;
    }

    public List<String> getOrderedStopLocations() {
        List<String> stops = new ArrayList<>();
        stops.add(startLocation);
        stops.addAll(waypointLocations);
        stops.add(destinationLocation);
        return stops;
    }

    public String waypointSymbolsToString() {
        if (waypointSymbols.isEmpty()) {
            return "-";
        }
        return String.join(" -> ", waypointSymbols);
    }
}

/*
 * Combined result for a multi-segment route, derived from the individual
 * ShortestPathResult segments.
 */
class RouteResult {
    private final RouteQuery query;
    private final String algorithmName;
    private final List<ShortestPathResult> segmentResults;
    private final List<String> fullPath;
    private final double totalCost;

    public RouteResult(RouteQuery query, String algorithmName, List<ShortestPathResult> segmentResults) {
        this.query = query;
        this.algorithmName = algorithmName;
        this.segmentResults = new ArrayList<>(segmentResults);
        this.fullPath = buildFullPath(segmentResults);
        this.totalCost = calculateTotalCost(segmentResults);
    }

    public static RouteResult trivial(RouteQuery query, String algorithmName) {
        List<String> path = new ArrayList<>();
        path.add(query.getStartLocation());
        PathSearchStats stats = new PathSearchStats();
        stats.incrementVisitedNodes();
        ShortestPathResult result = new ShortestPathResult(
                algorithmName,
                query.getStartLocation(),
                query.getDestinationLocation(),
                path,
                0.0,
                true,
                stats);
        List<ShortestPathResult> segments = new ArrayList<>();
        segments.add(result);
        return new RouteResult(query, algorithmName, segments);
    }

    public RouteQuery getQuery() {
        return query;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }

    public List<ShortestPathResult> getSegmentResults() {
        return new ArrayList<>(segmentResults);
    }

    public List<String> getFullPath() {
        return new ArrayList<>(fullPath);
    }

    public double getTotalCost() {
        return totalCost;
    }

    public PathSearchStats getCombinedStats() {
        PathSearchStats combined = new PathSearchStats();
        for (ShortestPathResult segment : segmentResults) {
            combined = combined.add(segment.getStats());
        }
        return combined;
    }

    public String fullPathToString() {
        return String.join(" -> ", fullPath);
    }

    private List<String> buildFullPath(List<ShortestPathResult> segmentResults) {
        List<String> path = new ArrayList<>();
        for (ShortestPathResult segment : segmentResults) {
            PathResultUtils.appendSegmentPath(path, segment.getPath());
        }
        return path;
    }

    private double calculateTotalCost(List<ShortestPathResult> segmentResults) {
        double total = 0.0;
        for (ShortestPathResult segment : segmentResults) {
            total += segment.getTotalCost();
        }
        return total;
    }
}

/*
 * Solves fixed-order routes by running the selected shortest-path solver once
 * for each adjacent pair of requested stops.
 */
class RoutePlanner {
    public RouteResult solveFixedOrderRoute(WeightedGraph graph, RouteQuery query, ShortestPathSolver solver) {
        if (query.getStartLocation().equals(query.getDestinationLocation())
                && query.getWaypointLocations().isEmpty()) {
            return RouteResult.trivial(query, solver.getClass().getSimpleName());
        }

        List<String> stops = query.getOrderedStopLocations();
        List<ShortestPathResult> segments = new ArrayList<>();
        for (int i = 0; i < stops.size() - 1; i++) {
            ShortestPathResult segment = solver.findShortestPath(graph, stops.get(i), stops.get(i + 1));
            if (!segment.isReachable()) {
                throw new IllegalStateException(
                        "No route segment found: " + stops.get(i) + " -> " + stops.get(i + 1));
            }
            segments.add(segment);
        }
        String algorithmName = segments.isEmpty() ? solver.getClass().getSimpleName() : segments.get(0).getAlgorithmName();
        return new RouteResult(query, algorithmName, segments);
    }
}

/*
 * Result of exact flexible waypoint planning, including the chosen waypoint
 * order and dynamic-programming state count.
 */
class FlexibleWaypointResult {
    private final String start;
    private final String destination;
    private final List<String> chosenWaypointOrder;
    private final List<String> fullPath;
    private final List<Double> segmentCosts;
    private final double totalCost;
    private final int waypointCount;
    private final long dpStateCount;
    private final long runtimeNanos;

    public FlexibleWaypointResult(
            String start,
            String destination,
            List<String> chosenWaypointOrder,
            List<String> fullPath,
            List<Double> segmentCosts,
            double totalCost,
            int waypointCount,
            long dpStateCount,
            long runtimeNanos) {
        this.start = start;
        this.destination = destination;
        this.chosenWaypointOrder = new ArrayList<>(chosenWaypointOrder);
        this.fullPath = new ArrayList<>(fullPath);
        this.segmentCosts = new ArrayList<>(segmentCosts);
        this.totalCost = totalCost;
        this.waypointCount = waypointCount;
        this.dpStateCount = dpStateCount;
        this.runtimeNanos = runtimeNanos;
    }

    public String getStart() {
        return start;
    }

    public String getDestination() {
        return destination;
    }

    public List<String> getChosenWaypointOrder() {
        return new ArrayList<>(chosenWaypointOrder);
    }

    public List<String> getFullPath() {
        return new ArrayList<>(fullPath);
    }

    public List<Double> getSegmentCosts() {
        return new ArrayList<>(segmentCosts);
    }

    public double getTotalCost() {
        return totalCost;
    }

    public int getWaypointCount() {
        return waypointCount;
    }

    public long getDpStateCount() {
        return dpStateCount;
    }

    public double getRuntimeMillis() {
        return runtimeNanos / 1_000_000.0;
    }
}

/*
 * Exact flexible waypoint solver for benchmark analysis. It precomputes
 * shortest paths between key nodes, then applies bitmask dynamic programming.
 */
class FlexibleWaypointPlanner {
    public static final int MAX_EXACT_FLEXIBLE_WAYPOINTS = 15;

    /*
     * Exact flexible waypoint planning. Dijkstra first computes shortest paths
     * between key nodes, then bitmask DP solves waypoint ordering in
     * O(k^2 * 2^k), where k is the number of flexible waypoints.
     */
    public FlexibleWaypointResult solveFlexibleWaypointOrder(
            WeightedGraph graph,
            String start,
            List<String> waypointLocations,
            String destination,
            ShortestPathSolver solver) {
        long startTime = System.nanoTime();
        int waypointCount = waypointLocations.size();
        if (waypointCount > MAX_EXACT_FLEXIBLE_WAYPOINTS) {
            throw new IllegalArgumentException(
                    "Exact flexible waypoint DP is limited to "
                            + MAX_EXACT_FLEXIBLE_WAYPOINTS + " waypoints.");
        }
        if (waypointCount == 0) {
            ShortestPathResult direct = solver.findShortestPath(graph, start, destination);
            return fromSegmentResults(start, destination, Collections.emptyList(), singletonSegments(direct), startTime, 1);
        }

        List<String> keyNodes = new ArrayList<>();
        keyNodes.add(start);
        keyNodes.addAll(waypointLocations);
        keyNodes.add(destination);

        ShortestPathResult[][] pairResults = new ShortestPathResult[keyNodes.size()][keyNodes.size()];
        for (int i = 0; i < keyNodes.size(); i++) {
            for (int j = 0; j < keyNodes.size(); j++) {
                if (i != j) {
                    pairResults[i][j] = solver.findShortestPath(graph, keyNodes.get(i), keyNodes.get(j));
                    if (!pairResults[i][j].isReachable()) {
                        throw new IllegalStateException(
                                "No shortest path between key nodes: " + keyNodes.get(i) + " -> " + keyNodes.get(j));
                    }
                }
            }
        }

        if (waypointCount == 1) {
            List<ShortestPathResult> segments = new ArrayList<>();
            segments.add(pairResults[0][1]);
            segments.add(pairResults[1][2]);
            return fromSegmentResults(start, destination, waypointLocations, segments, startTime, 2);
        }

        int states = 1 << waypointCount;
        double[][] dp = new double[states][waypointCount];
        int[][] parent = new int[states][waypointCount];
        for (int mask = 0; mask < states; mask++) {
            Arrays.fill(dp[mask], Double.POSITIVE_INFINITY);
            Arrays.fill(parent[mask], -1);
        }

        for (int i = 0; i < waypointCount; i++) {
            dp[1 << i][i] = pairResults[0][i + 1].getTotalCost();
        }

        long stateVisits = 0L;
        for (int mask = 0; mask < states; mask++) {
            for (int last = 0; last < waypointCount; last++) {
                if (Double.isInfinite(dp[mask][last])) {
                    continue;
                }
                stateVisits++;
                for (int next = 0; next < waypointCount; next++) {
                    if ((mask & (1 << next)) != 0) {
                        continue;
                    }
                    int nextMask = mask | (1 << next);
                    double candidate = dp[mask][last] + pairResults[last + 1][next + 1].getTotalCost();
                    if (candidate < dp[nextMask][next]) {
                        dp[nextMask][next] = candidate;
                        parent[nextMask][next] = last;
                    }
                }
            }
        }

        int allVisited = states - 1;
        double bestCost = Double.POSITIVE_INFINITY;
        int bestLast = -1;
        for (int last = 0; last < waypointCount; last++) {
            double candidate = dp[allVisited][last] + pairResults[last + 1][keyNodes.size() - 1].getTotalCost();
            if (candidate < bestCost) {
                bestCost = candidate;
                bestLast = last;
            }
        }

        List<Integer> bestWaypointIndices = reconstructWaypointIndices(parent, allVisited, bestLast);
        List<String> bestWaypointOrder = new ArrayList<>();
        List<ShortestPathResult> bestSegments = new ArrayList<>();
        int previousKeyIndex = 0;
        for (Integer waypointIndex : bestWaypointIndices) {
            int keyIndex = waypointIndex + 1;
            bestWaypointOrder.add(waypointLocations.get(waypointIndex));
            bestSegments.add(pairResults[previousKeyIndex][keyIndex]);
            previousKeyIndex = keyIndex;
        }
        bestSegments.add(pairResults[previousKeyIndex][keyNodes.size() - 1]);
        return fromSegmentResults(start, destination, bestWaypointOrder, bestSegments, startTime, stateVisits);
    }

    private FlexibleWaypointResult fromSegmentResults(
            String start,
            String destination,
            List<String> waypointOrder,
            List<ShortestPathResult> segments,
            long startTime,
            long dpStateCount) {
        List<String> fullPath = new ArrayList<>();
        List<Double> segmentCosts = new ArrayList<>();
        double totalCost = 0.0;
        for (ShortestPathResult segment : segments) {
            PathResultUtils.appendSegmentPath(fullPath, segment.getPath());
            segmentCosts.add(segment.getTotalCost());
            totalCost += segment.getTotalCost();
        }
        return new FlexibleWaypointResult(
                start,
                destination,
                waypointOrder,
                fullPath,
                segmentCosts,
                totalCost,
                waypointOrder.size(),
                dpStateCount,
                System.nanoTime() - startTime);
    }

    private List<ShortestPathResult> singletonSegments(ShortestPathResult segment) {
        List<ShortestPathResult> segments = new ArrayList<>();
        segments.add(segment);
        return segments;
    }

    private List<Integer> reconstructWaypointIndices(int[][] parent, int mask, int last) {
        LinkedList<Integer> order = new LinkedList<>();
        int currentMask = mask;
        int currentLast = last;
        while (currentLast >= 0) {
            order.addFirst(currentLast);
            int previousLast = parent[currentMask][currentLast];
            currentMask = currentMask & ~(1 << currentLast);
            currentLast = previousLast;
        }
        return order;
    }
}

/*
 * Reader for paths.csv. It validates each weighted path row and builds the
 * WeightedGraph used by Task B and the JavaFX application.
 */
class PathCsvReader {
    private static final String EXPECTED_HEADER = "from_location,to_location,weight";

    private PathCsvReader() {
    }

    public static WeightedGraph readGraph(String filePath) throws IOException {
        return readGraph(filePath, LinkedHashMap::new, LinkedHashMap::new);
    }

    public static WeightedGraph readGraph(
            String filePath,
            Supplier<Map<String, Map<String, WeightedEdge>>> adjacencyListFactory,
            Supplier<Map<String, WeightedEdge>> neighborMapFactory) throws IOException {
        File file = new File(filePath);
        if (!file.isFile()) {
            throw new IOException("Path file not found: " + filePath);
        }
        WeightedGraph graph = new WeightedGraph(adjacencyListFactory, neighborMapFactory);
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String header = reader.readLine();
            validateHeader(filePath, header);

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split(",", -1);
                if (parts.length != 3) {
                    throw new IllegalArgumentException(
                            "Invalid paths.csv line " + lineNumber + ": expected 3 columns.");
                }
                String from = parts[0].trim();
                String to = parts[1].trim();
                String weightText = parts[2].trim();
                if (from.isEmpty() || to.isEmpty() || weightText.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Invalid paths.csv line " + lineNumber + ": fields must not be empty.");
                }
                double weight;
                try {
                    weight = Double.parseDouble(weightText);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Invalid paths.csv line " + lineNumber + ": weight is not numeric.",
                            e);
                }
                if (weight < 0.0) {
                    throw new IllegalArgumentException("Dijkstra requires non-negative edge weights.");
                }
                graph.addUndirectedEdge(from, to, weight);
            }
        }
        return graph;
    }

    private static void validateHeader(String filePath, String header) {
        String printableHeader = header == null ? "<empty file>" : header.replace("\uFEFF", "");
        String[] parts = printableHeader.split(",", -1);
        if (parts.length != 3
                || !parts[0].trim().equalsIgnoreCase("from_location")
                || !parts[1].trim().equalsIgnoreCase("to_location")
                || !parts[2].trim().equalsIgnoreCase("weight")) {
            throw new IllegalArgumentException(
                    "Invalid CSV header in " + filePath + ". Expected "
                            + EXPECTED_HEADER + " but found: " + printableHeader);
        }
    }
}

/*
 * Reader for selected_targets.csv used by Task B command-line benchmarks. It
 * validates that all required selected targets exist in the weighted graph.
 */
class SelectedTargetsReader {
    private static final String EXPECTED_HEADER = "dataset,rank,location_id,priority_score";

    private SelectedTargetsReader() {
    }

    public static TaskBTargetSet readTaskBTargets(String filePath, WeightedGraph graph) throws IOException {
        File file = new File(filePath);
        if (!file.isFile()) {
            throw new IOException("Selected targets file not found: " + filePath);
        }

        Map<String, SelectedTarget> selectedTargets = new LinkedHashMap<>();
        int selectedTargetRowCount = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String header = reader.readLine();
            validateHeader(filePath, header);

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split(",", -1);
                if (parts.length != 4) {
                    throw new IllegalArgumentException(
                            "Invalid selected_targets.csv line " + lineNumber + ": expected 4 columns.");
                }
                String dataset = parts[0].trim();
                String rankText = parts[1].trim();
                String locationId = parts[2].trim();
                String priorityScoreText = parts[3].trim();
                if (!dataset.equals("A") && !dataset.equals("B") && !dataset.equals("C")) {
                    throw new IllegalArgumentException("Invalid dataset at line " + lineNumber + ": " + dataset);
                }
                int rank;
                int priorityScore;
                try {
                    rank = Integer.parseInt(rankText);
                    priorityScore = Integer.parseInt(priorityScoreText);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Invalid numeric field in selected_targets.csv line " + lineNumber + ".",
                            e);
                }
                if (rank < 1 || rank > 10) {
                    throw new IllegalArgumentException("Invalid rank at line " + lineNumber + ": " + rank);
                }
                if (locationId.isEmpty()) {
                    throw new IllegalArgumentException("location_id must not be empty at line " + lineNumber + ".");
                }
                String key = dataset + rank;
                if (selectedTargets.containsKey(key)) {
                    throw new IllegalStateException("Duplicate selected target entry found: " + key);
                }
                selectedTargets.put(key, new SelectedTarget(key, dataset, rank, locationId, priorityScore));
                selectedTargetRowCount++;
            }
        }

        validateCompleteSelectedTargets(selectedTargets, selectedTargetRowCount);
        TaskBTargetSet targetSet = new TaskBTargetSet(selectedTargets);
        validateTaskBTargetsInGraph(targetSet, graph);
        return targetSet;
    }

    private static void validateHeader(String filePath, String header) {
        String printableHeader = header == null ? "<empty file>" : header.replace("\uFEFF", "");
        String[] parts = printableHeader.split(",", -1);
        if (parts.length != 4
                || !parts[0].trim().equalsIgnoreCase("dataset")
                || !parts[1].trim().equalsIgnoreCase("rank")
                || !parts[2].trim().equalsIgnoreCase("location_id")
                || !parts[3].trim().equalsIgnoreCase("priority_score")) {
            throw new IllegalArgumentException(
                    "Invalid CSV header in " + filePath + ". Expected "
                            + EXPECTED_HEADER + " but found: " + printableHeader);
        }
    }

    private static void validateCompleteSelectedTargets(
            Map<String, SelectedTarget> selectedTargets,
            int selectedTargetRowCount) {
        if (selectedTargetRowCount != 30 || selectedTargets.size() != 30) {
            throw new IllegalStateException(
                    "selected_targets.csv must contain exactly 30 unique selected targets.");
        }
        String[] datasets = {"A", "B", "C"};
        for (String dataset : datasets) {
            for (int rank = 1; rank <= 10; rank++) {
                String key = dataset + rank;
                if (!selectedTargets.containsKey(key)) {
                    throw new IllegalStateException("selected_targets.csv is missing " + key + ".");
                }
            }
        }
    }

    private static void validateTaskBTargetsInGraph(TaskBTargetSet targetSet, WeightedGraph graph) {
        for (SelectedTarget target : targetSet.getAllTargets()) {
            graph.validateVertexExists(target.getLocationId());
        }
    }
}
