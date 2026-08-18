package org.example.cpt204cw;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Command-line runner for Task A. It loads candidate datasets, benchmarks the
 * required sorting algorithms and variants, validates matching Top 10 outputs,
 * and exports selected_targets.csv for Task B.
 */
public class TaskASortingBenchmark {
    private static final int WARMUP_RUNS = 5;
    private static final int MEASURED_RUNS = 10;
    private static final int EXPECTED_ROW_COUNT = 1000;
    private static final boolean STRICT_ROW_COUNT_VALIDATION = true;

    private static final String BUBBLE_SORT_NAME = "Bubble Sort";
    private static final String SELECTED_TARGETS_PATH = "data/selected_targets.csv";

    private static final String[] DEFAULT_PATHS = {
            "data/candidates_A.csv",
            "data/candidates_B.csv",
            "data/candidates_C.csv"
    };

    public static void main(String[] args) {
        try {
            printProgramHeader();
            String[] datasetPaths = resolveDatasetPaths(args);
            List<DatasetInput> datasets = readDatasets(datasetPaths);

            List<DatasetProfile> profiles = new ArrayList<>();
            for (DatasetInput dataset : datasets) {
                profiles.add(DatasetProfiler.profile(dataset.name, dataset.originalData));
            }
            printCompactDatasetProfile(profiles);
            validateUniqueLocationIds(profiles);

            System.out.println();
            System.out.println("Table 1. Unified Sorting Benchmark Results");
            printUnifiedBenchmarkHeader();

            Map<String, List<CandidateLocation>> finalTop10ByDataset = new LinkedHashMap<>();
            for (DatasetInput dataset : datasets) {
                List<BenchmarkResult> mainResults = new ArrayList<>();
                for (SortingAlgorithm algorithm : createMainAlgorithms()) {
                    BenchmarkResult result = BenchmarkRunner.runBenchmark(
                            dataset.name,
                            algorithm,
                            dataset.originalData,
                            WARMUP_RUNS,
                            MEASURED_RUNS);
                    mainResults.add(result);
                }
                List<CandidateLocation> baselineTop10 = validateTop10OrThrow(dataset.name, mainResults);
                finalTop10ByDataset.put(dataset.name, baselineTop10);
                printUnifiedBenchmarkRows("Main", baselineTop10, mainResults);

                List<BenchmarkResult> quickVariantResults = new ArrayList<>();
                for (AlgorithmSpec spec : createQuickVariantAlgorithms()) {
                    BenchmarkResult result = BenchmarkRunner.runBenchmark(
                            dataset.name,
                            spec.displayName,
                            spec.algorithm,
                            dataset.originalData,
                            WARMUP_RUNS,
                            MEASURED_RUNS);
                    quickVariantResults.add(result);
                }
                validateResultsAgainstTop10OrThrow(dataset.name, "Quick Variant", baselineTop10, quickVariantResults);
                printUnifiedBenchmarkRows("Quick Variant", baselineTop10, quickVariantResults);

                List<BenchmarkResult> mergeVariantResults = new ArrayList<>();
                for (AlgorithmSpec spec : createMergeVariantAlgorithms()) {
                    BenchmarkResult result = BenchmarkRunner.runBenchmark(
                            dataset.name,
                            spec.displayName,
                            spec.algorithm,
                            dataset.originalData,
                            WARMUP_RUNS,
                            MEASURED_RUNS);
                    mergeVariantResults.add(result);
                }
                validateResultsAgainstTop10OrThrow(dataset.name, "Merge Variant", baselineTop10, mergeVariantResults);
                printUnifiedBenchmarkRows("Merge Variant", baselineTop10, mergeVariantResults);
            }

            exportSelectedTargets(finalTop10ByDataset, SELECTED_TARGETS_PATH);

            printTheoreticalComplexitySummary();
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printProgramHeader() {
        System.out.println("CPT204 Task A Sorting Benchmark");
        System.out.println("Input datasets: data/candidates_A.csv, data/candidates_B.csv, data/candidates_C.csv");
        System.out.println("Benchmark: warm-up=" + WARMUP_RUNS + ", measured=" + MEASURED_RUNS);
        System.out.println();
    }

    private static List<SortingAlgorithm> createMainAlgorithms() {
        List<SortingAlgorithm> algorithms = new ArrayList<>();
        algorithms.add(new BubbleSort());
        algorithms.add(new QuickSort());
        algorithms.add(new MergeSort());
        return algorithms;
    }

    private static List<AlgorithmSpec> createQuickVariantAlgorithms() {
        List<AlgorithmSpec> algorithms = new ArrayList<>();
        algorithms.add(new AlgorithmSpec("First-element pivot Quick Sort", new QuickSort()));
        algorithms.add(new AlgorithmSpec(
                "Middle-element pivot Quick Sort",
                new QuickSortWithPivotStrategy(PivotStrategy.MIDDLE_ELEMENT)));
        algorithms.add(new AlgorithmSpec(
                "Last-element pivot Quick Sort",
                new QuickSortWithPivotStrategy(PivotStrategy.LAST_ELEMENT)));
        algorithms.add(new AlgorithmSpec(
                "Random-element pivot Quick Sort",
                new QuickSortWithPivotStrategy(PivotStrategy.RANDOM_ELEMENT)));
        algorithms.add(new AlgorithmSpec(
                "Median-of-three Quick Sort",
                new QuickSortWithPivotStrategy(PivotStrategy.MEDIAN_OF_THREE)));
        algorithms.add(new AlgorithmSpec("Three-way Quick Sort", new ThreeWayQuickSort()));
        return algorithms;
    }

    private static List<AlgorithmSpec> createMergeVariantAlgorithms() {
        List<AlgorithmSpec> algorithms = new ArrayList<>();
        algorithms.add(new AlgorithmSpec("Merge Sort", new MergeSort()));
        algorithms.add(new AlgorithmSpec("Bottom-up Merge Sort", new BottomUpMergeSort()));
        algorithms.add(new AlgorithmSpec("Natural Merge Sort", new NaturalMergeSort()));
        algorithms.add(new AlgorithmSpec("Hybrid Merge Sort", new HybridMergeSort()));
        return algorithms;
    }

    private static String[] resolveDatasetPaths(String[] args) {
        if (args.length == 0) {
            return DEFAULT_PATHS;
        }
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Please provide exactly three dataset paths, or no arguments to use defaults.");
        }
        return args;
    }

    private static List<DatasetInput> readDatasets(String[] datasetPaths) throws IOException {
        List<DatasetInput> datasets = new ArrayList<>();
        for (String path : datasetPaths) {
            String datasetName = datasetNameFromPath(path);
            List<CandidateLocation> originalData = CandidateCsvReader.readCandidates(path);
            validateRowCount(datasetName, originalData.size());
            datasets.add(new DatasetInput(datasetName, originalData));
        }
        return datasets;
    }

    private static void validateRowCount(String datasetName, int actualRowCount) {
        if (actualRowCount == EXPECTED_ROW_COUNT) {
            return;
        }

        String message = "Dataset " + datasetName + " contains " + actualRowCount
                + " rows, but the coursework dataset is expected to contain "
                + EXPECTED_ROW_COUNT + " rows.";
        if (STRICT_ROW_COUNT_VALIDATION) {
            throw new IllegalStateException(message);
        }
        System.out.println("WARNING: " + message);
    }

    private static void validateUniqueLocationIds(List<DatasetProfile> profiles) {
        for (DatasetProfile profile : profiles) {
            if (profile.getDuplicateLocationIdCount() > 0) {
                throw new IllegalStateException(
                        "Dataset " + profile.getDatasetName() + " contains "
                                + profile.getDuplicateLocationIdCount()
                                + " duplicate location_id rows. location_id values must be unique for reliable tie-breaking.");
            }
        }
    }

    private static String datasetNameFromPath(String path) {
        String fileName = new File(path).getName();
        if (fileName.equalsIgnoreCase("candidates_A.csv")) {
            return "A";
        }
        if (fileName.equalsIgnoreCase("candidates_B.csv")) {
            return "B";
        }
        if (fileName.equalsIgnoreCase("candidates_C.csv")) {
            return "C";
        }
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private static void printCompactDatasetProfile(List<DatasetProfile> profiles) {
        System.out.println("Table 0. Dataset Profile");
        System.out.println(String.format(
                "%-8s | %5s | %13s | %10s | %12s | %14s | %14s | %17s",
                "Dataset",
                "Rows",
                "Unique Scores",
                "Tie Groups",
                "Max Tie Size",
                "Already Sorted",
                "Reverse Sorted",
                "Order Break Ratio"));
        System.out.println(repeat("-", 115));
        for (DatasetProfile profile : profiles) {
            System.out.println(String.format(
                    "%-8s | %5d | %13d | %10d | %12d | %14s | %14s | %17.4f",
                    profile.getDatasetName(),
                    profile.getRowCount(),
                    profile.getUniquePriorityCount(),
                    profile.getNumberOfTieGroups(),
                    profile.getMaxTieGroupSize(),
                    profile.isAlreadySorted(),
                    profile.isReverseSorted(),
                    profile.getOrderBreakRatio()));
        }
    }

    private static void printUnifiedBenchmarkHeader() {
        System.out.println(String.format(
                "%-8s | %-13s | %-34s | %15s | %12s | %11s | %-13s | %13s | %-15s | %s",
                "Dataset",
                "Category",
                "Algorithm / Variant",
                "Warm-up Avg(ms)",
                "Avg Time(ms)",
                "Comparisons",
                "Movement Type",
                "Data Movement",
                "Recursion Depth",
                "Top 10 Match"));
        System.out.println(repeat("-", 170));
    }

    private static void printUnifiedBenchmarkRows(
            String category,
            List<CandidateLocation> baselineTop10,
            List<BenchmarkResult> results) {
        for (BenchmarkResult result : results) {
            boolean top10Passed = sameTop10(baselineTop10, result.getTop10());
            System.out.println(result.toUnifiedTableRow(
                    category,
                    movementTypeFor(result.getAlgorithmName()),
                    recursionDepthTextFor(result),
                    top10Passed));
        }
    }

    private static String movementTypeFor(String algorithmName) {
        if (algorithmName.contains("Merge Sort")) {
            return "Moves";
        }
        return "Swaps";
    }

    private static String recursionDepthTextFor(BenchmarkResult result) {
        String algorithmName = result.getAlgorithmName();
        if (algorithmName.equals(BUBBLE_SORT_NAME)) {
            return "-";
        }
        if (algorithmName.equals("Bottom-up Merge Sort")
                || algorithmName.equals("Bottom-Up Merge Sort")
                || algorithmName.equals("Natural Merge Sort")) {
            return "0";
        }
        return String.format("%.1f", result.getAverageMaxRecursionDepth());
    }

    private static List<CandidateLocation> validateTop10OrThrow(
            String datasetName,
            List<BenchmarkResult> results) {
        if (results.isEmpty()) {
            throw new IllegalStateException("No benchmark results found for Dataset " + datasetName + ".");
        }

        List<CandidateLocation> expected = results.get(0).getTop10();
        for (int i = 1; i < results.size(); i++) {
            if (!sameTop10(expected, results.get(i).getTop10())) {
                System.out.println("Top 10 validation for Dataset " + datasetName + ": FAILED");
                for (BenchmarkResult result : results) {
                    System.out.println(result.getAlgorithmName() + ": " + top10ToString(result.getTop10()));
                }
                throw new IllegalStateException(
                        "Top 10 results are not identical for Dataset " + datasetName + ".");
            }
        }

        return expected;
    }

    private static void validateResultsAgainstTop10OrThrow(
            String datasetName,
            String category,
            List<CandidateLocation> expectedTop10,
            List<BenchmarkResult> results) {
        for (BenchmarkResult result : results) {
            if (!sameTop10(expectedTop10, result.getTop10())) {
                System.out.println(category + " Top 10 validation for Dataset " + datasetName + ": FAILED");
                System.out.println("Baseline: " + top10ToString(expectedTop10));
                System.out.println(result.getAlgorithmName() + ": " + top10ToString(result.getTop10()));
                throw new IllegalStateException(
                        category + " Top 10 result does not match baseline for Dataset " + datasetName + ".");
            }
        }
    }

    private static void exportSelectedTargets(
            Map<String, List<CandidateLocation>> finalTop10ByDataset,
            String outputPath) throws IOException {
        validateSelectedTargetsForExport(finalTop10ByDataset);

        File outputFile = new File(outputPath);
        File parentDirectory = outputFile.getParentFile();
        if (parentDirectory != null && !parentDirectory.isDirectory() && !parentDirectory.mkdirs()) {
            throw new IOException("Could not create output directory: " + parentDirectory.getPath());
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write("dataset,rank,location_id,priority_score");
            writer.newLine();

            for (Map.Entry<String, List<CandidateLocation>> entry : finalTop10ByDataset.entrySet()) {
                List<CandidateLocation> top10 = entry.getValue();
                for (int i = 0; i < top10.size(); i++) {
                    CandidateLocation candidate = top10.get(i);
                    writer.write(entry.getKey());
                    writer.write(",");
                    writer.write(Integer.toString(i + 1));
                    writer.write(",");
                    writer.write(candidate.getLocationId());
                    writer.write(",");
                    writer.write(Integer.toString(candidate.getPriorityScore()));
                    writer.newLine();
                }
            }
        }

        System.out.println("selected_targets.csv exported: " + outputPath);
    }

    private static void validateSelectedTargetsForExport(
            Map<String, List<CandidateLocation>> finalTop10ByDataset) {
        String[] expectedDatasets = {"A", "B", "C"};
        for (String datasetName : expectedDatasets) {
            List<CandidateLocation> top10 = finalTop10ByDataset.get(datasetName);
            if (top10 == null) {
                throw new IllegalStateException(
                        "Cannot export selected targets because Dataset " + datasetName + " is missing.");
            }
            if (top10.size() != 10) {
                throw new IllegalStateException(
                        "Cannot export selected targets because Dataset " + datasetName
                                + " has " + top10.size() + " selected targets instead of 10.");
            }
        }

        if (finalTop10ByDataset.size() != expectedDatasets.length) {
            throw new IllegalStateException(
                    "Cannot export selected targets because the export must contain only datasets A, B, and C.");
        }
    }

    private static void printTheoreticalComplexitySummary() {
        System.out.println();
        System.out.println("Table 2. Algorithm Complexity and Design Summary");
        System.out.println(String.format(
                "%-36s | %-28s | %-18s | %-16s | %-26s | %-10s | %s",
                "Algorithm / Variant",
                "Best Time",
                "Average Time",
                "Worst Time",
                "Extra Space",
                "Stability",
                "Main Purpose"));
        System.out.println(repeat("-", 210));
        printComplexityRow(
                "Bubble Sort with Early Stopping",
                "O(n)",
                "O(n^2)",
                "O(n^2)",
                "O(1)",
                "Stable",
                "Simple baseline; benefits from nearly sorted input");
        printComplexityRow(
                "Quick Sort - First Pivot",
                "O(n log n)",
                "O(n log n)",
                "O(n^2)",
                "O(log n) avg, O(n) worst",
                "Not stable",
                "Lecture-style / baseline Quick Sort");
        printComplexityRow(
                "Quick Sort - Middle Pivot",
                "O(n log n)",
                "O(n log n)",
                "O(n^2)",
                "O(log n) average",
                "Not stable",
                "Reduces risk of poor pivot on ordered input");
        printComplexityRow(
                "Quick Sort - Last Pivot",
                "O(n log n)",
                "O(n log n)",
                "O(n^2)",
                "O(log n) average",
                "Not stable",
                "Pivot strategy comparison");
        printComplexityRow(
                "Quick Sort - Random Pivot",
                "O(n log n)",
                "Expected O(n log n)",
                "O(n^2), unlikely",
                "O(log n) average",
                "Not stable",
                "Reduces input-order sensitivity");
        printComplexityRow(
                "Quick Sort - Median-of-three",
                "O(n log n)",
                "O(n log n)",
                "O(n^2)",
                "O(log n) average",
                "Not stable",
                "Improves pivot quality using first/middle/last");
        printComplexityRow(
                "Three-way Quick Sort",
                "O(n) for many equal keys",
                "O(n log n)",
                "O(n^2)",
                "O(log n) average",
                "Not stable",
                "Duplicate-key partitioning; limited by secondary location_id comparator");
        printComplexityRow(
                "Merge Sort",
                "O(n log n)",
                "O(n log n)",
                "O(n log n)",
                "O(n)",
                "Stable",
                "Reliable divide-and-conquer baseline");
        printComplexityRow(
                "Bottom-up Merge Sort",
                "O(n log n)",
                "O(n log n)",
                "O(n log n)",
                "O(n)",
                "Stable",
                "Iterative Merge Sort without recursion");
        printComplexityRow(
                "Natural Merge Sort",
                "O(n) when long runs exist",
                "O(n log n)",
                "O(n log n)",
                "O(n)",
                "Stable",
                "Exploits existing sorted runs, suitable for nearly sorted Dataset A");
        printComplexityRow(
                "Hybrid Merge Sort",
                "O(n log n)",
                "O(n log n)",
                "O(n log n)",
                "O(n)",
                "Stable",
                "Practical optimisation using insertion sort on small subarrays");
    }

    private static void printComplexityRow(
            String algorithm,
            String bestTime,
            String averageTime,
            String worstTime,
            String extraSpace,
            String stability,
            String purpose) {
        System.out.println(String.format(
                "%-36s | %-28s | %-18s | %-16s | %-26s | %-10s | %s",
                algorithm,
                bestTime,
                averageTime,
                worstTime,
                extraSpace,
                stability,
                purpose));
    }

    private static String top10ToString(List<CandidateLocation> top10) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < top10.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(top10.get(i).toString());
        }
        return builder.toString();
    }

    private static boolean sameTop10(List<CandidateLocation> a, List<CandidateLocation> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            CandidateLocation first = a.get(i);
            CandidateLocation second = b.get(i);
            if (!first.getLocationId().equals(second.getLocationId())
                    || first.getPriorityScore() != second.getPriorityScore()) {
                return false;
            }
        }
        return true;
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private static class DatasetInput {
        private final String name;
        private final List<CandidateLocation> originalData;

        private DatasetInput(String name, List<CandidateLocation> originalData) {
            this.name = name;
            this.originalData = originalData;
        }
    }

    private static class AlgorithmSpec {
        private final String displayName;
        private final SortingAlgorithm algorithm;

        private AlgorithmSpec(String displayName, SortingAlgorithm algorithm) {
            this.displayName = displayName;
            this.algorithm = algorithm;
        }
    }
}
