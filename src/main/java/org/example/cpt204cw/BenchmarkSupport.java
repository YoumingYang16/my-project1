package org.example.cpt204cw;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/*
 * Immutable candidate record used by Task A sorting. The comparison rule is
 * centralised here so all sorting algorithms use the same ordering.
 */
class CandidateLocation {
    private final String locationId;
    private final int priorityScore;

    public CandidateLocation(String locationId, int priorityScore) {
        if (locationId == null || locationId.trim().isEmpty()) {
            throw new IllegalArgumentException("location_id must not be empty.");
        }
        this.locationId = locationId.trim();
        this.priorityScore = priorityScore;
    }

    public String getLocationId() {
        return locationId;
    }

    public int getPriorityScore() {
        return priorityScore;
    }

    /*
     * Required ranking rule:
     * 1. Higher priority_score comes first.
     * 2. If priority_score is equal, smaller location_id comes first.
     */
    public static int compare(CandidateLocation a, CandidateLocation b) {
        Objects.requireNonNull(a, "First candidate must not be null.");
        Objects.requireNonNull(b, "Second candidate must not be null.");

        int scoreComparison = Integer.compare(b.priorityScore, a.priorityScore);
        if (scoreComparison != 0) {
            return scoreComparison;
        }
        return a.locationId.compareTo(b.locationId);
    }

    public static boolean isBeforeOrEqual(CandidateLocation a, CandidateLocation b) {
        return compare(a, b) <= 0;
    }

    @Override
    public String toString() {
        return locationId + "(" + priorityScore + ")";
    }
}

/*
 * Strict CSV reader for Task A candidate datasets. It validates the expected
 * two-column format before creating CandidateLocation objects.
 */
class CandidateCsvReader {
    private static final String EXPECTED_HEADER = "location_id,priority_score";

    private CandidateCsvReader() {
    }

    public static List<CandidateLocation> readCandidates(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.isFile()) {
            throw new IOException("Dataset file not found: " + filePath);
        }

        List<CandidateLocation> candidates = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
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
                if (parts.length != 2) {
                    throw new IllegalArgumentException(
                            "Invalid CSV format in " + filePath + " at line " + lineNumber
                                    + ": expected 2 columns but found " + parts.length + ".");
                }

                String locationId = parts[0].trim();
                String priorityScoreText = parts[1].trim();
                if (locationId.isEmpty() || priorityScoreText.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Invalid CSV format in " + filePath + " at line " + lineNumber
                                    + ": location_id and priority_score must not be empty.");
                }

                try {
                    int priorityScore = Integer.parseInt(priorityScoreText);
                    candidates.add(new CandidateLocation(locationId, priorityScore));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Invalid priority_score in " + filePath + " at line " + lineNumber
                                    + ": '" + priorityScoreText + "' is not an integer.",
                            e);
                }
            }
        }

        return candidates;
    }

    private static void validateHeader(String filePath, String header) {
        String printableHeader = header == null ? "<empty file>" : header.replace("\uFEFF", "");
        if (header == null || !isExpectedHeader(header)) {
            throw new IllegalArgumentException(
                    "Invalid CSV header in " + filePath + ". Expected " + EXPECTED_HEADER
                            + " but found: " + printableHeader);
        }
    }

    private static boolean isExpectedHeader(String header) {
        String cleanedHeader = header.replace("\uFEFF", "");
        String[] columns = cleanedHeader.split(",", -1);
        if (columns.length != 2) {
            return false;
        }
        return columns[0].trim().equalsIgnoreCase("location_id")
                && columns[1].trim().equalsIgnoreCase("priority_score");
    }
}

/*
 * Stores dataset-level characteristics that explain benchmark behaviour, such
 * as existing order, duplicates, and priority-score distribution.
 */
class DatasetProfile {
    private final String datasetName;
    private final int rowCount;
    private final int uniqueLocationCount;
    private final int duplicateLocationIdCount;
    private final int uniquePriorityCount;
    private final int duplicatePriorityCount;
    private final int numberOfTieGroups;
    private final int maxTieGroupSize;
    private final int minPriorityScore;
    private final int maxPriorityScore;
    private final double averagePriorityScore;
    private final int adjacentOrderBreaks;
    private final double orderBreakRatio;
    private final boolean alreadySorted;
    private final boolean reverseSorted;

    public DatasetProfile(
            String datasetName,
            int rowCount,
            int uniqueLocationCount,
            int duplicateLocationIdCount,
            int uniquePriorityCount,
            int duplicatePriorityCount,
            int numberOfTieGroups,
            int maxTieGroupSize,
            int minPriorityScore,
            int maxPriorityScore,
            double averagePriorityScore,
            int adjacentOrderBreaks,
            double orderBreakRatio,
            boolean alreadySorted,
            boolean reverseSorted) {
        this.datasetName = datasetName;
        this.rowCount = rowCount;
        this.uniqueLocationCount = uniqueLocationCount;
        this.duplicateLocationIdCount = duplicateLocationIdCount;
        this.uniquePriorityCount = uniquePriorityCount;
        this.duplicatePriorityCount = duplicatePriorityCount;
        this.numberOfTieGroups = numberOfTieGroups;
        this.maxTieGroupSize = maxTieGroupSize;
        this.minPriorityScore = minPriorityScore;
        this.maxPriorityScore = maxPriorityScore;
        this.averagePriorityScore = averagePriorityScore;
        this.adjacentOrderBreaks = adjacentOrderBreaks;
        this.orderBreakRatio = orderBreakRatio;
        this.alreadySorted = alreadySorted;
        this.reverseSorted = reverseSorted;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getUniquePriorityCount() {
        return uniquePriorityCount;
    }

    public int getDuplicateLocationIdCount() {
        return duplicateLocationIdCount;
    }

    public int getNumberOfTieGroups() {
        return numberOfTieGroups;
    }

    public int getMaxTieGroupSize() {
        return maxTieGroupSize;
    }

    public double getOrderBreakRatio() {
        return orderBreakRatio;
    }

    public boolean isAlreadySorted() {
        return alreadySorted;
    }

    public boolean isReverseSorted() {
        return reverseSorted;
    }

}

/*
 * Builds DatasetProfile summaries before benchmarking so performance results
 * can be interpreted against the shape of each input dataset.
 */
class DatasetProfiler {
    private DatasetProfiler() {
    }

    public static DatasetProfile profile(String datasetName, List<CandidateLocation> data) {
        Set<String> uniqueLocationIds = new HashSet<>();
        Set<Integer> uniqueScores = new HashSet<>();
        Map<Integer, Integer> scoreFrequencies = new HashMap<>();

        int minPriorityScore = 0;
        int maxPriorityScore = 0;
        long priorityScoreTotal = 0L;

        for (int i = 0; i < data.size(); i++) {
            CandidateLocation candidate = data.get(i);
            int priorityScore = candidate.getPriorityScore();

            uniqueLocationIds.add(candidate.getLocationId());
            uniqueScores.add(priorityScore);
            scoreFrequencies.put(priorityScore, scoreFrequencies.getOrDefault(priorityScore, 0) + 1);

            if (i == 0 || priorityScore < minPriorityScore) {
                minPriorityScore = priorityScore;
            }
            if (i == 0 || priorityScore > maxPriorityScore) {
                maxPriorityScore = priorityScore;
            }
            priorityScoreTotal += priorityScore;
        }

        int adjacentOrderBreaks = 0;
        boolean reverseSorted = true;

        for (int i = 0; i < data.size() - 1; i++) {
            int comparison = CandidateLocation.compare(data.get(i), data.get(i + 1));

            /*
             * orderBreakRatio normalises local ranking violations by row count.
             * A lower ratio means the input is closer to the required ranking.
             */
            if (comparison > 0) {
                adjacentOrderBreaks++;
            }
            if (comparison < 0) {
                reverseSorted = false;
            }
        }

        int numberOfTieGroups = 0;
        int maxTieGroupSize = 0;
        for (Integer frequency : scoreFrequencies.values()) {
            if (frequency >= 2) {
                numberOfTieGroups++;
            }
            if (frequency > maxTieGroupSize) {
                maxTieGroupSize = frequency;
            }
        }

        int rowCount = data.size();
        int uniqueLocationCount = uniqueLocationIds.size();
        int duplicateLocationIdCount = rowCount - uniqueLocationCount;
        int uniquePriorityCount = uniqueScores.size();
        int duplicatePriorityCount = rowCount - uniquePriorityCount;
        double averagePriorityScore = rowCount == 0 ? 0.0 : priorityScoreTotal / (double) rowCount;
        double orderBreakRatio = rowCount <= 1 ? 0.0 : adjacentOrderBreaks / (double) (rowCount - 1);
        boolean alreadySorted = adjacentOrderBreaks == 0;

        return new DatasetProfile(
                datasetName,
                rowCount,
                uniqueLocationCount,
                duplicateLocationIdCount,
                uniquePriorityCount,
                duplicatePriorityCount,
                numberOfTieGroups,
                maxTieGroupSize,
                minPriorityScore,
                maxPriorityScore,
                averagePriorityScore,
                adjacentOrderBreaks,
                orderBreakRatio,
                alreadySorted,
                reverseSorted);
    }

}

/*
 * Executes repeatable warm-up and measured sorting runs on fresh list copies,
 * validates sorted output, and records timing and operation statistics.
 */
class BenchmarkRunner {
    private BenchmarkRunner() {
    }

    public static BenchmarkResult runBenchmark(
            String datasetName,
            SortingAlgorithm algorithm,
            List<CandidateLocation> originalData,
            int warmupRuns,
            int measuredRuns) {
        return runBenchmark(datasetName, algorithm.getName(), algorithm, originalData, warmupRuns, measuredRuns);
    }

    public static BenchmarkResult runBenchmark(
            String datasetName,
            String algorithmName,
            SortingAlgorithm algorithm,
            List<CandidateLocation> originalData,
            int warmupRuns,
            int measuredRuns) {
        if (warmupRuns < 0 || measuredRuns <= 0) {
            throw new IllegalArgumentException("Benchmark requires warm-up runs >= 0 and measured runs > 0.");
        }

        List<Long> warmupElapsedTimes = new ArrayList<>();
        for (int run = 0; run < warmupRuns; run++) {
            List<CandidateLocation> warmupData = copyData(originalData);
            long start = System.nanoTime();
            algorithm.sort(warmupData);
            long elapsed = System.nanoTime() - start;
            warmupElapsedTimes.add(elapsed);
            validateSorted(datasetName, algorithm, warmupData);
        }

        List<Long> measuredElapsedTimes = new ArrayList<>();
        long totalComparisons = 0L;
        long totalSwaps = 0L;
        long totalMoves = 0L;
        long totalPasses = 0L;
        long totalMaxDepth = 0L;
        List<CandidateLocation> top10 = new ArrayList<>();

        for (int run = 0; run < measuredRuns; run++) {
            List<CandidateLocation> workingData = copyData(originalData);
            long start = System.nanoTime();
            algorithm.sort(workingData);
            long elapsed = System.nanoTime() - start;
            measuredElapsedTimes.add(elapsed);

            validateSorted(datasetName, algorithm, workingData);

            SortingStats stats = algorithm.getStats();
            totalComparisons += stats.getComparisons();
            totalSwaps += stats.getSwaps();
            totalMoves += stats.getMoves();
            totalPasses += stats.getPasses();
            totalMaxDepth += stats.getMaxRecursionDepth();

            if (run == 0) {
                top10 = getTop10(workingData);
            }
        }

        return new BenchmarkResult(
                datasetName,
                algorithmName,
                nanosToMillis(average(warmupElapsedTimes)),
                nanosToMillis(average(measuredElapsedTimes)),
                nanosToMillis(standardDeviation(measuredElapsedTimes)),
                nanosToMillis(min(measuredElapsedTimes)),
                nanosToMillis(max(measuredElapsedTimes)),
                totalComparisons / (double) measuredRuns,
                totalSwaps / (double) measuredRuns,
                totalMoves / (double) measuredRuns,
                totalPasses / (double) measuredRuns,
                totalMaxDepth / (double) measuredRuns,
                top10);
    }

    private static List<CandidateLocation> copyData(List<CandidateLocation> data) {
        return new ArrayList<>(data);
    }

    private static void validateSorted(
            String datasetName,
            SortingAlgorithm algorithm,
            List<CandidateLocation> data) {
        if (!isSorted(data)) {
            throw new IllegalStateException(
                    algorithm.getName() + " failed to sort dataset " + datasetName + ".");
        }
    }

    private static boolean isSorted(List<CandidateLocation> data) {
        for (int i = 0; i < data.size() - 1; i++) {
            if (!CandidateLocation.isBeforeOrEqual(data.get(i), data.get(i + 1))) {
                return false;
            }
        }
        return true;
    }

    private static List<CandidateLocation> getTop10(List<CandidateLocation> data) {
        List<CandidateLocation> top10 = new ArrayList<>();
        int limit = Math.min(10, data.size());
        for (int i = 0; i < limit; i++) {
            top10.add(data.get(i));
        }
        return top10;
    }

    private static double average(List<Long> nanos) {
        if (nanos.isEmpty()) {
            return 0.0;
        }

        long total = 0L;
        for (Long value : nanos) {
            total += value;
        }
        return total / (double) nanos.size();
    }

    private static double standardDeviation(List<Long> nanos) {
        if (nanos.isEmpty()) {
            return 0.0;
        }

        double average = average(nanos);
        double squaredDifferenceTotal = 0.0;
        for (Long value : nanos) {
            double difference = value - average;
            squaredDifferenceTotal += difference * difference;
        }
        return Math.sqrt(squaredDifferenceTotal / nanos.size());
    }

    private static double nanosToMillis(double nanos) {
        return nanos / 1_000_000.0;
    }

    private static long min(List<Long> values) {
        if (values.isEmpty()) {
            return 0L;
        }

        long minimum = Long.MAX_VALUE;
        for (Long value : values) {
            if (value < minimum) {
                minimum = value;
            }
        }
        return minimum;
    }

    private static long max(List<Long> values) {
        if (values.isEmpty()) {
            return 0L;
        }

        long maximum = Long.MIN_VALUE;
        for (Long value : values) {
            if (value > maximum) {
                maximum = value;
            }
        }
        return maximum;
    }
}

/*
 * Immutable benchmark summary returned after running one algorithm on one
 * dataset, including timings, operation counts, and the first Top 10 result.
 */
class BenchmarkResult {
    private final String datasetName;
    private final String algorithmName;
    private final double warmupAverageTimeMs;
    private final double averageTimeMs;
    private final double standardDeviationMs;
    private final double minTimeMs;
    private final double maxTimeMs;
    private final double averageComparisons;
    private final double averageSwaps;
    private final double averageMoves;
    private final double averagePasses;
    private final double averageMaxRecursionDepth;
    private final List<CandidateLocation> top10;

    public BenchmarkResult(
            String datasetName,
            String algorithmName,
            double warmupAverageTimeMs,
            double averageTimeMs,
            double standardDeviationMs,
            double minTimeMs,
            double maxTimeMs,
            double averageComparisons,
            double averageSwaps,
            double averageMoves,
            double averagePasses,
            double averageMaxRecursionDepth,
            List<CandidateLocation> top10) {
        this.datasetName = datasetName;
        this.algorithmName = algorithmName;
        this.warmupAverageTimeMs = warmupAverageTimeMs;
        this.averageTimeMs = averageTimeMs;
        this.standardDeviationMs = standardDeviationMs;
        this.minTimeMs = minTimeMs;
        this.maxTimeMs = maxTimeMs;
        this.averageComparisons = averageComparisons;
        this.averageSwaps = averageSwaps;
        this.averageMoves = averageMoves;
        this.averagePasses = averagePasses;
        this.averageMaxRecursionDepth = averageMaxRecursionDepth;
        this.top10 = new ArrayList<>(top10);
    }

    public String getAlgorithmName() {
        return algorithmName;
    }

    public double getWarmupAverageTimeMs() {
        return warmupAverageTimeMs;
    }

    public double getAverageTimeMs() {
        return averageTimeMs;
    }

    public double getStandardDeviationMs() {
        return standardDeviationMs;
    }

    public double getAveragePasses() {
        return averagePasses;
    }

    public double getAverageComparisons() {
        return averageComparisons;
    }

    public double getAverageSwaps() {
        return averageSwaps;
    }

    public double getAverageMoves() {
        return averageMoves;
    }

    public double getAverageMaxRecursionDepth() {
        return averageMaxRecursionDepth;
    }

    public List<CandidateLocation> getTop10() {
        return new ArrayList<>(top10);
    }

    public String toUnifiedTableRow(
            String category,
            String movementType,
            String recursionDepthText,
            boolean top10Passed) {
        double dataMovement = "Moves".equals(movementType) ? averageMoves : averageSwaps;
        return String.format(
                "%-8s | %-13s | %-34s | %15.3f | %12.3f | %11.1f | %-13s | %13.1f | %-15s | %s",
                datasetName,
                category,
                algorithmName,
                warmupAverageTimeMs,
                averageTimeMs,
                averageComparisons,
                movementType,
                dataMovement,
                recursionDepthText,
                top10Passed ? "PASSED" : "FAILED");
    }
}
