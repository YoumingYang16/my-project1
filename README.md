# Route Planner and Algorithm Benchmark Suite

A Java 21 coursework project that connects algorithm theory with an interactive application and reproducible experiments. The repository contains a JavaFX route-planning interface, sorting benchmarks, weighted shortest-path comparisons, exact multi-waypoint planning, and controlled data-structure experiments.

The project is designed around a simple question: how do algorithm choice, input structure, and data representation affect correctness and observed performance in a real program?

## Highlights

- Interactive JavaFX route planner with selectable algorithms, ordered waypoints, route cost, and runtime display.
- Six weighted shortest-path options in the UI: lecture-style Dijkstra, priority-queue Dijkstra, bidirectional Dijkstra, Bellman–Ford, A* with ALT landmarks, and Floyd–Warshall.
- Sorting implementations covering Bubble Sort, multiple Quick Sort pivot strategies, three-way Quick Sort, and four Merge Sort variants.
- A benchmark harness with warm-up runs, repeated measurements, descriptive statistics, operation counters, and correctness checks.
- Exact flexible waypoint ordering using shortest-path precomputation and bitmask dynamic programming.
- Data-structure experiments comparing lists, sets, priority queues, adjacency-list implementations, and an adjacency matrix.
- CSV result export for later analysis and report preparation.

## Architecture

The code is organised so that the user interface and benchmark runners reuse the same algorithmic components.

```text
CSV input
   │
   ├── strict parsers and dataset validation
   │
   ├── sorting datasets ──> SortingSupport ──> Task A benchmark reports
   │
   └── weighted graph ──> ShortestPathSolver implementations
                            ├── JavaFX RoutePlannerController
                            ├── Task B comparison runner
                            └── flexible-waypoint planner
```

`WeightedGraph` uses a map-of-maps adjacency-list representation. Shortest-path implementations conform to a shared solver interface, allowing the UI and experiments to switch algorithms without duplicating routing logic.

## Implemented Algorithms

### Sorting

- Bubble Sort with early termination
- Quick Sort with first, middle, last, random, and median-of-three pivots
- Three-way Quick Sort
- Top-down Merge Sort
- Bottom-up Merge Sort
- Natural Merge Sort
- Hybrid Merge Sort

The sorting benchmarks can record comparisons, swaps, moves, passes, and maximum recursion depth. Output is checked rather than assumed: the benchmark validates ordering and the expected top-ranked records after each measured run.

### Shortest paths

- Lecture-style Dijkstra using an `ArrayList` unsettled set and a full minimum scan
- Optimised Dijkstra using `PriorityQueue` and early stopping
- Bidirectional Dijkstra
- Bellman–Ford
- A* with an ALT landmark heuristic and explicit preprocessing
- Floyd–Warshall all-pairs shortest paths
- Unweighted BFS as an experimental baseline

The comparison runner separates preprocessing time from query time where appropriate and checks that successful weighted algorithms agree on route cost.

### Multi-waypoint routing

Two route semantics are supported:

- **Fixed order:** visit waypoints in the order supplied by the user.
- **Flexible order:** find the least-cost ordering of the supplied waypoints.

The exact flexible planner first computes shortest paths between relevant locations and then solves waypoint ordering with bitmask dynamic programming. Its time complexity is `O(k² × 2^k)` for `k` waypoints, so the implementation deliberately limits exact planning to 15 waypoints.

Floyd–Warshall is similarly guarded: its `O(V³)` preprocessing is only executed for graphs at or below the implementation threshold of 1,000 vertices.

## Benchmark Methodology

The benchmark code is intended to make comparisons repeatable and auditable.

- Validate CSV structure and required values before running an experiment.
- Profile the input dataset rather than treating all data as equivalent.
- Execute warm-up runs before measured runs.
- Repeat measurements and report average, standard deviation, minimum, and maximum time where supported.
- Record algorithm-specific operation counts in addition to wall-clock time.
- Validate sorted output, route reachability, repeated-run consistency, and weighted path cost.
- Export report tables to CSV.

The Task B runner uses five warm-up runs and ten measured runs for route comparisons. Runtime results remain machine-dependent; meaningful reporting should include the computer, JVM, dataset, and run configuration.

## Project Structure

```text
.
├── data/                                      # input datasets and generated CSV reports
├── src/main/java/org/example/cpt204cw/
│   ├── BenchmarkSupport.java                  # parsing, validation, statistics, metrics
│   ├── SortingSupport.java                    # sorting implementations and counters
│   ├── TaskASortingBenchmark.java             # sorting experiments and tables
│   ├── TaskBSupport.java                      # graphs, route solvers, waypoint planning
│   ├── TaskBComparisonRunner.java             # route benchmarks and CSV export
│   ├── DataStructureExperimentRunner.java     # container and graph-representation tests
│   ├── RoutePlannerController.java            # JavaFX interaction and visualisation
│   └── Run.java                               # application entry point
├── pom.xml
├── mvnw
└── mvnw.cmd
```

## Requirements

- Java Development Kit 21
- The included Maven Wrapper; a separate Maven installation is not required

## Build and Run

From the repository root:

### Windows

```powershell
.\mvnw.cmd clean compile
.\mvnw.cmd javafx:run
```

### macOS or Linux

```bash
./mvnw clean compile
./mvnw javafx:run
```

### Run the experiment programs

Use the Maven Exec Plugin with the required main class:

```bash
./mvnw exec:java -Dexec.mainClass="org.example.cpt204cw.TaskASortingBenchmark"
./mvnw exec:java -Dexec.mainClass="org.example.cpt204cw.TaskBComparisonRunner"
./mvnw exec:java -Dexec.mainClass="org.example.cpt204cw.DataStructureExperimentRunner"
```

On Windows PowerShell, replace `./mvnw` with `.\mvnw.cmd` and quote the complete `-Dexec.mainClass=...` argument if required by the shell.

## Input and Output Data

The programs read CSV files from `data/`. Task B expects the graph and selected-target datasets referenced by the runner. Generated route-comparison tables are written back to `data/` as `taskB_table_2_*.csv`, together with the consolidated runtime CSV.

Input validation reports malformed headers, missing values, invalid numbers, and references to unknown graph vertices. Generated benchmark CSV files should be treated as experimental output tied to a particular dataset and runtime environment.

## What the Project Demonstrates

- Translating textbook algorithms into interchangeable software components
- Connecting asymptotic complexity to practical guardrails and experiment design
- Separating algorithm preprocessing from query execution
- Testing correctness alongside measuring performance
- Building an interactive visual layer over reusable routing logic
- Evaluating how container and graph representations affect behaviour and cost

## Limitations

- Benchmark timings vary across hardware, JVM versions, background load, and datasets.
- The JavaFX canvas uses a deterministic generated layout; it is a route visualisation rather than a geographic map.
- Exact flexible waypoint planning is intentionally limited to 15 waypoints.
- Floyd–Warshall is skipped for graphs above 1,000 vertices.
- The repository is an academic algorithm-engineering project, not a production navigation service.

## Academic Context

This repository was developed as coursework and is presented as a technical portfolio project. Claims about performance should be supported by the exported results and the environment in which they were produced.
