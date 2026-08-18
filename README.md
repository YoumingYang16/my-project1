# Route Planner and Algorithm Benchmark Suite

This repository contains a CPT204 coursework project implemented in Java. It combines an interactive JavaFX route-planning interface with experiments for sorting algorithms and data structures.

## Features

- A JavaFX route planner that loads its network data from CSV files.
- Start and destination selection, visual route display, and a Floyd-Warshall-based route solver.
- Sorting-algorithm benchmarking utilities.
- Data-structure experiment and comparison utilities.

## Project Structure

```text
.
|-- data/                         # CSV data used by the application
|-- src/main/java/                # Java source code
|   `-- org/example/cpt204cw/
|       |-- Run.java              # JavaFX application entry point
|       |-- RoutePlannerController.java
|       |-- TaskASortingBenchmark.java
|       `-- TaskBComparisonRunner.java
|-- src/main/resources/           # FXML, CSS, and packaged resources
|-- pom.xml                       # Maven project configuration
`-- mvnw / mvnw.cmd               # Maven Wrapper scripts
```

## Requirements

- Java Development Kit (JDK) 21
- No separate Maven installation is required when using the included Maven Wrapper.

## Run the Application

From the repository root, run one of the following commands:

```bash
# Windows PowerShell or Command Prompt
.\mvnw.cmd clean javafx:run

# macOS or Linux
./mvnw clean javafx:run
```

The command downloads the required JavaFX dependencies and opens the **Route Planner** desktop application.

## Data Files

The `data/` directory contains route and candidate data in CSV format. Keep this directory alongside the project when running the application; it is used by the route-planning and experiment components.

## Notes

This repository was prepared as coursework. The code and supplied data are intended for learning, demonstration, and assessment purposes.
