package org.example.cpt204cw;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/*
 * Common contract for all Task A sorting implementations. BenchmarkRunner uses
 * this interface to time and validate different algorithms uniformly.
 */
interface SortingAlgorithm {
    String getName();

    void sort(List<CandidateLocation> data);

    SortingStats getStats();
}

/*
 * Mutable counters collected during a single sorting run. Algorithms reset the
 * object before sorting and return a defensive copy for reporting.
 */
class SortingStats {
    private long comparisons;
    private long swaps;
    private long moves;
    private long passes;
    private int maxRecursionDepth;

    public SortingStats() {
    }

    private SortingStats(long comparisons, long swaps, long moves, long passes, int maxRecursionDepth) {
        this.comparisons = comparisons;
        this.swaps = swaps;
        this.moves = moves;
        this.passes = passes;
        this.maxRecursionDepth = maxRecursionDepth;
    }

    public void reset() {
        comparisons = 0L;
        swaps = 0L;
        moves = 0L;
        passes = 0L;
        maxRecursionDepth = 0;
    }

    public void incrementComparisons() {
        comparisons++;
    }

    public void incrementSwaps() {
        swaps++;
    }

    public void incrementMoves() {
        moves++;
    }

    public void incrementPasses() {
        passes++;
    }

    public void addPasses(long value) {
        passes += value;
    }

    public void updateMaxRecursionDepth(int depth) {
        if (depth > maxRecursionDepth) {
            maxRecursionDepth = depth;
        }
    }

    public long getComparisons() {
        return comparisons;
    }

    public long getSwaps() {
        return swaps;
    }

    public long getMoves() {
        return moves;
    }

    public long getPasses() {
        return passes;
    }

    public int getMaxRecursionDepth() {
        return maxRecursionDepth;
    }

    public SortingStats copy() {
        return new SortingStats(comparisons, swaps, moves, passes, maxRecursionDepth);
    }
}

/*
 * Supported pivot choices for Quick Sort experiments.
 */
enum PivotStrategy {
    FIRST_ELEMENT,
    MIDDLE_ELEMENT,
    LAST_ELEMENT,
    RANDOM_ELEMENT,
    MEDIAN_OF_THREE
}

/*
 * Baseline Bubble Sort implementation with an early-stop optimisation.
 */
class BubbleSort implements SortingAlgorithm {
    private final SortingStats stats = new SortingStats();

    @Override
    public String getName() {
        return "Bubble Sort";
    }

    @Override
    public void sort(List<CandidateLocation> data) {
        stats.reset();

        /*
         * With early stop, nearly sorted data can finish quickly. Unordered or
         * reverse-ordered data usually remains close to O(n^2).
         */
        for (int pass = 0; pass < data.size() - 1; pass++) {
            boolean swapped = false;
            for (int i = 0; i < data.size() - pass - 1; i++) {
                stats.incrementComparisons();
                if (CandidateLocation.compare(data.get(i), data.get(i + 1)) > 0) {
                    swap(data, i, i + 1);
                    swapped = true;
                }
            }
            stats.incrementPasses();
            if (!swapped) {
                break;
            }
        }
    }

    @Override
    public SortingStats getStats() {
        return stats.copy();
    }

    private void swap(List<CandidateLocation> data, int firstIndex, int secondIndex) {
        CandidateLocation temporary = data.get(firstIndex);
        data.set(firstIndex, data.get(secondIndex));
        data.set(secondIndex, temporary);
        stats.incrementSwaps();
    }
}

/*
 * Required classroom-style Quick Sort using the first element as pivot.
 */
class QuickSort extends QuickSortWithPivotStrategy {
    public QuickSort() {
        /*
         * First-element pivot is used as the main Quick Sort implementation
         * because it matches the taught / classroom version. This pivot choice
         * may perform poorly on already sorted or nearly sorted input because
         * it can create unbalanced partitions. Pivot and partition variants are
         * still benchmarked in the unified Task A benchmark output.
         */
        super(PivotStrategy.FIRST_ELEMENT);
    }

    @Override
    public String getName() {
        return "Quick Sort (First-Element Pivot)";
    }
}

/*
 * Quick Sort implementation parameterised by pivot strategy so variants can be
 * benchmarked under the same partitioning code.
 */
class QuickSortWithPivotStrategy implements SortingAlgorithm {
    private static final long RANDOM_SEED = 42L;

    private final PivotStrategy strategy;
    private final SortingStats stats = new SortingStats();
    private Random random = new Random(RANDOM_SEED);

    public QuickSortWithPivotStrategy(PivotStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("Pivot strategy must not be null.");
        }
        this.strategy = strategy;
    }

    @Override
    public String getName() {
        return "Quick Sort (" + strategy + ")";
    }

    @Override
    public void sort(List<CandidateLocation> data) {
        stats.reset();
        random = new Random(RANDOM_SEED);
        quickSort(data, 0, data.size() - 1, 1);
    }

    @Override
    public SortingStats getStats() {
        return stats.copy();
    }

    private void quickSort(List<CandidateLocation> data, int low, int high, int depth) {
        if (low >= high) {
            return;
        }

        stats.updateMaxRecursionDepth(depth);
        int pivotIndex = partition(data, low, high);
        quickSort(data, low, pivotIndex - 1, depth + 1);
        quickSort(data, pivotIndex + 1, high, depth + 1);
    }

    private int partition(List<CandidateLocation> data, int low, int high) {
        int pivotIndex = choosePivotIndex(data, low, high);
        swap(data, pivotIndex, high);

        CandidateLocation pivot = data.get(high);
        int smallerOrEqualEnd = low - 1;

        for (int current = low; current < high; current++) {
            stats.incrementComparisons();
            if (CandidateLocation.compare(data.get(current), pivot) <= 0) {
                smallerOrEqualEnd++;
                swap(data, smallerOrEqualEnd, current);
            }
        }

        swap(data, smallerOrEqualEnd + 1, high);
        return smallerOrEqualEnd + 1;
    }

    private int choosePivotIndex(List<CandidateLocation> data, int low, int high) {
        switch (strategy) {
            case FIRST_ELEMENT:
                return low;
            case MIDDLE_ELEMENT:
                return low + (high - low) / 2;
            case LAST_ELEMENT:
                return high;
            case RANDOM_ELEMENT:
                return low + random.nextInt(high - low + 1);
            case MEDIAN_OF_THREE:
                return medianOfThreeIndex(data, low, low + (high - low) / 2, high);
            default:
                throw new IllegalStateException("Unsupported pivot strategy: " + strategy);
        }
    }

    private int medianOfThreeIndex(List<CandidateLocation> data, int first, int middle, int last) {
        int a = first;
        int b = middle;
        int c = last;

        if (compare(data.get(a), data.get(b)) > 0) {
            int temporary = a;
            a = b;
            b = temporary;
        }
        if (compare(data.get(a), data.get(c)) > 0) {
            int temporary = a;
            a = c;
            c = temporary;
        }
        if (compare(data.get(b), data.get(c)) > 0) {
            int temporary = b;
            b = c;
            c = temporary;
        }

        return b;
    }

    private int compare(CandidateLocation first, CandidateLocation second) {
        stats.incrementComparisons();
        return CandidateLocation.compare(first, second);
    }

    private void swap(List<CandidateLocation> data, int firstIndex, int secondIndex) {
        if (firstIndex == secondIndex) {
            return;
        }

        CandidateLocation temporary = data.get(firstIndex);
        data.set(firstIndex, data.get(secondIndex));
        data.set(secondIndex, temporary);
        stats.incrementSwaps();
    }
}

/*
 * Quick Sort variant that groups values smaller than, equal to, and greater
 * than the pivot in one pass.
 */
class ThreeWayQuickSort implements SortingAlgorithm {
    private final SortingStats stats = new SortingStats();

    @Override
    public String getName() {
        return "Three-way Quick Sort";
    }

    @Override
    public void sort(List<CandidateLocation> data) {
        stats.reset();
        quickSort(data, 0, data.size() - 1, 1);
    }

    @Override
    public SortingStats getStats() {
        return stats.copy();
    }

    private void quickSort(List<CandidateLocation> data, int low, int high, int depth) {
        if (low >= high) {
            return;
        }

        /*
         * Three-way partitioning can help when many keys are equal. In this
         * coursework, the full comparator also includes location_id, so exact
         * duplicate comparator keys may be limited.
         */
        stats.updateMaxRecursionDepth(depth);
        CandidateLocation pivot = data.get(low);
        int lessThanEnd = low;
        int current = low + 1;
        int greaterThanStart = high;

        while (current <= greaterThanStart) {
            stats.incrementComparisons();
            int comparison = CandidateLocation.compare(data.get(current), pivot);
            if (comparison < 0) {
                swap(data, lessThanEnd, current);
                lessThanEnd++;
                current++;
            } else if (comparison > 0) {
                swap(data, current, greaterThanStart);
                greaterThanStart--;
            } else {
                current++;
            }
        }

        quickSort(data, low, lessThanEnd - 1, depth + 1);
        quickSort(data, greaterThanStart + 1, high, depth + 1);
    }

    private void swap(List<CandidateLocation> data, int firstIndex, int secondIndex) {
        if (firstIndex == secondIndex) {
            return;
        }

        CandidateLocation temporary = data.get(firstIndex);
        data.set(firstIndex, data.get(secondIndex));
        data.set(secondIndex, temporary);
        stats.incrementSwaps();
    }
}

/*
 * Top-down Merge Sort implementation used as the stable divide-and-conquer
 * baseline for Task A.
 */
class MergeSort implements SortingAlgorithm {
    private final SortingStats stats = new SortingStats();

    @Override
    public String getName() {
        return "Merge Sort";
    }

    @Override
    public void sort(List<CandidateLocation> data) {
        stats.reset();
        if (data.size() <= 1) {
            return;
        }

        List<CandidateLocation> auxiliary = new ArrayList<>(data);
        mergeSort(data, auxiliary, 0, data.size() - 1, 1);
    }

    @Override
    public SortingStats getStats() {
        return stats.copy();
    }

    private void mergeSort(
            List<CandidateLocation> data,
            List<CandidateLocation> auxiliary,
            int low,
            int high,
            int depth) {
        if (low >= high) {
            return;
        }

        stats.updateMaxRecursionDepth(depth);
        int middle = low + (high - low) / 2;
        mergeSort(data, auxiliary, low, middle, depth + 1);
        mergeSort(data, auxiliary, middle + 1, high, depth + 1);
        merge(data, auxiliary, low, middle, high);
    }

    private void merge(
            List<CandidateLocation> data,
            List<CandidateLocation> auxiliary,
            int low,
            int middle,
            int high) {
        for (int i = low; i <= high; i++) {
            auxiliary.set(i, data.get(i));
            stats.incrementMoves();
        }

        int left = low;
        int right = middle + 1;
        int destination = low;

        while (left <= middle && right <= high) {
            stats.incrementComparisons();
            if (CandidateLocation.compare(auxiliary.get(left), auxiliary.get(right)) <= 0) {
                data.set(destination, auxiliary.get(left));
                left++;
            } else {
                data.set(destination, auxiliary.get(right));
                right++;
            }
            destination++;
            stats.incrementMoves();
        }

        while (left <= middle) {
            data.set(destination, auxiliary.get(left));
            left++;
            destination++;
            stats.incrementMoves();
        }

        while (right <= high) {
            data.set(destination, auxiliary.get(right));
            right++;
            destination++;
            stats.incrementMoves();
        }
    }
}

/*
 * Iterative Merge Sort variant that merges fixed-width runs from the bottom up.
 */
class BottomUpMergeSort implements SortingAlgorithm {
    private final SortingStats stats = new SortingStats();

    @Override
    public String getName() {
        return "Bottom-Up Merge Sort";
    }

    @Override
    public void sort(List<CandidateLocation> data) {
        stats.reset();
        int size = data.size();
        if (size <= 1) {
            return;
        }

        List<CandidateLocation> auxiliary = new ArrayList<>(data);
        for (int width = 1; width < size; width *= 2) {
            for (int low = 0; low < size; low += 2 * width) {
                int middle = Math.min(low + width - 1, size - 1);
                int high = Math.min(low + 2 * width - 1, size - 1);
                if (middle < high) {
                    merge(data, auxiliary, low, middle, high);
                }
            }
        }
    }

    @Override
    public SortingStats getStats() {
        return stats.copy();
    }

    private void merge(
            List<CandidateLocation> data,
            List<CandidateLocation> auxiliary,
            int low,
            int middle,
            int high) {
        for (int i = low; i <= high; i++) {
            auxiliary.set(i, data.get(i));
            stats.incrementMoves();
        }

        int left = low;
        int right = middle + 1;
        int destination = low;

        while (left <= middle && right <= high) {
            stats.incrementComparisons();
            if (CandidateLocation.compare(auxiliary.get(left), auxiliary.get(right)) <= 0) {
                data.set(destination, auxiliary.get(left));
                left++;
            } else {
                data.set(destination, auxiliary.get(right));
                right++;
            }
            destination++;
            stats.incrementMoves();
        }

        while (left <= middle) {
            data.set(destination, auxiliary.get(left));
            left++;
            destination++;
            stats.incrementMoves();
        }

        while (right <= high) {
            data.set(destination, auxiliary.get(right));
            right++;
            destination++;
            stats.incrementMoves();
        }
    }
}

/*
 * Natural Merge Sort variant that first detects existing ordered runs in the
 * input before merging them.
 */
class NaturalMergeSort implements SortingAlgorithm {
    private final SortingStats stats = new SortingStats();

    @Override
    public String getName() {
        return "Natural Merge Sort";
    }

    @Override
    public void sort(List<CandidateLocation> data) {
        stats.reset();
        if (data.size() <= 1) {
            return;
        }

        /*
         * Natural Merge Sort is useful for nearly sorted input because it first
         * detects existing ordered runs and then merges those runs.
         */
        List<CandidateLocation> auxiliary = new ArrayList<>(data);
        while (true) {
            List<Run> runs = identifyRuns(data);
            if (runs.size() <= 1) {
                break;
            }

            for (int i = 0; i < runs.size(); i += 2) {
                if (i + 1 < runs.size()) {
                    Run left = runs.get(i);
                    Run right = runs.get(i + 1);
                    merge(data, auxiliary, left.start, left.end - 1, right.end - 1);
                }
            }
            stats.incrementPasses();
        }
    }

    @Override
    public SortingStats getStats() {
        return stats.copy();
    }

    private List<Run> identifyRuns(List<CandidateLocation> data) {
        List<Run> runs = new ArrayList<>();
        int start = 0;
        for (int i = 1; i < data.size(); i++) {
            stats.incrementComparisons();
            if (CandidateLocation.compare(data.get(i - 1), data.get(i)) > 0) {
                runs.add(new Run(start, i));
                start = i;
            }
        }
        runs.add(new Run(start, data.size()));
        return runs;
    }

    private void merge(
            List<CandidateLocation> data,
            List<CandidateLocation> auxiliary,
            int low,
            int middle,
            int high) {
        for (int i = low; i <= high; i++) {
            auxiliary.set(i, data.get(i));
            stats.incrementMoves();
        }

        int left = low;
        int right = middle + 1;
        int destination = low;

        while (left <= middle && right <= high) {
            stats.incrementComparisons();
            if (CandidateLocation.compare(auxiliary.get(left), auxiliary.get(right)) <= 0) {
                data.set(destination, auxiliary.get(left));
                left++;
            } else {
                data.set(destination, auxiliary.get(right));
                right++;
            }
            destination++;
            stats.incrementMoves();
        }

        while (left <= middle) {
            data.set(destination, auxiliary.get(left));
            left++;
            destination++;
            stats.incrementMoves();
        }

        while (right <= high) {
            data.set(destination, auxiliary.get(right));
            right++;
            destination++;
            stats.incrementMoves();
        }
    }

    /*
     * Internal representation of one already ordered run.
     */
    private static class Run {
        private final int start;
        private final int end;

        private Run(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
}

/*
 * Merge Sort variant that switches to insertion sort for small subranges.
 */
class HybridMergeSort implements SortingAlgorithm {
    private static final int INSERTION_SORT_THRESHOLD = 16;

    private final SortingStats stats = new SortingStats();

    @Override
    public String getName() {
        return "Hybrid Merge Sort";
    }

    @Override
    public void sort(List<CandidateLocation> data) {
        stats.reset();
        if (data.size() <= 1) {
            return;
        }

        List<CandidateLocation> auxiliary = new ArrayList<>(data);
        hybridMergeSort(data, auxiliary, 0, data.size() - 1, 1);
    }

    @Override
    public SortingStats getStats() {
        return stats.copy();
    }

    private void hybridMergeSort(
            List<CandidateLocation> data,
            List<CandidateLocation> auxiliary,
            int low,
            int high,
            int depth) {
        if (low >= high) {
            return;
        }

        stats.updateMaxRecursionDepth(depth);
        if (high - low + 1 <= INSERTION_SORT_THRESHOLD) {
            insertionSort(data, low, high);
            return;
        }

        /*
         * Hybrid Merge Sort is a practical optimisation: normal top-down merge
         * sort is used for large ranges, and insertion sort handles small ranges.
         */
        int middle = low + (high - low) / 2;
        hybridMergeSort(data, auxiliary, low, middle, depth + 1);
        hybridMergeSort(data, auxiliary, middle + 1, high, depth + 1);
        merge(data, auxiliary, low, middle, high);
    }

    private void insertionSort(List<CandidateLocation> data, int low, int high) {
        for (int i = low + 1; i <= high; i++) {
            CandidateLocation key = data.get(i);
            stats.incrementMoves();
            int j = i - 1;

            while (j >= low) {
                stats.incrementComparisons();
                if (CandidateLocation.compare(data.get(j), key) <= 0) {
                    break;
                }
                data.set(j + 1, data.get(j));
                stats.incrementMoves();
                j--;
            }

            data.set(j + 1, key);
            stats.incrementMoves();
        }
    }

    private void merge(
            List<CandidateLocation> data,
            List<CandidateLocation> auxiliary,
            int low,
            int middle,
            int high) {
        for (int i = low; i <= high; i++) {
            auxiliary.set(i, data.get(i));
            stats.incrementMoves();
        }

        int left = low;
        int right = middle + 1;
        int destination = low;

        while (left <= middle && right <= high) {
            stats.incrementComparisons();
            if (CandidateLocation.compare(auxiliary.get(left), auxiliary.get(right)) <= 0) {
                data.set(destination, auxiliary.get(left));
                left++;
            } else {
                data.set(destination, auxiliary.get(right));
                right++;
            }
            destination++;
            stats.incrementMoves();
        }

        while (left <= middle) {
            data.set(destination, auxiliary.get(left));
            left++;
            destination++;
            stats.incrementMoves();
        }

        while (right <= high) {
            data.set(destination, auxiliary.get(right));
            right++;
            destination++;
            stats.incrementMoves();
        }
    }
}
