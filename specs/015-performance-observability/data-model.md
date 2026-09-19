# B15 Data Model

## SubsystemBudget

Represents the stable measurement identity of one RPG subsystem.

| Field | Type | Rules |
|---|---|---|
| `id` | string | non-blank, normalized, globally unique for the running plugin |
| `budgetNanos` | long | greater than zero; defaults to 5 ms only when explicitly configured as the project default |
| `enabled` | boolean | disabled subsystems are reported as disabled, never as zero-cost |
| `owner` | string | stable architecture-block name, for example `B10` |

Relationships: one `SubsystemBudget` owns one rolling `MeasurementWindow` and one `AlertState`.

## MeasurementWindow

Bounded rolling samples for one subsystem or the complete server tick.

| Field | Type | Rules |
|---|---|---|
| `windowStart` / `windowEnd` | instant | monotonic ordering in the production clock projection |
| `sampleCount` | long | zero is valid only for an explicitly inactive source |
| `minimumNanos` | long | derived from samples |
| `maximumNanos` | long | derived from samples |
| `meanNanos` | long | derived from samples without integer overflow |
| `p50Nanos` / `p95Nanos` / `p99Nanos` | long | nearest-rank percentile over the bounded sample set |
| `complete` | boolean | false when the source was missing or interrupted |

The window is implemented as a bounded ring in the core. Export and reporting receive an immutable
copy so asynchronous consumers never read mutable tick state.

## AlertEvent and AlertState

`AlertState` is one of `NORMAL`, `WARNING`, `CRITICAL`, or `RECOVERED`. An `AlertEvent` records:

- stable source ID (`overall` or subsystem ID)
- state transition
- observed value and configured limit
- first violation time and current window
- recovery time when the state returns to normal
- a deduplication key for one uninterrupted episode

Transitions are deterministic:

```text
NORMAL -> WARNING -> CRITICAL -> RECOVERED -> NORMAL
NORMAL -> CRITICAL is allowed for a direct project-target breach
WARNING -> RECOVERED is allowed when the warning clears before 60 seconds
```

## MonitoringMetric

An exportable metric has a stable name, value, optional labels and a scrape timestamp. Labels are
bounded to known subsystem IDs and block names; player names, UUIDs and arbitrary chat input never
become labels.

Required metric groups:

- overall tick duration and TPS/MSPT percentiles
- per-subsystem sample count, duration percentiles and budget ratio
- active players and active Custom-Mobs
- warning/critical/recovery counters and current alert state
- exporter/report success and failure counters

## HardwareProfile

Immutable description of the machine and runtime used for a run:

- CPU model and logical/physical core counts when available
- total and available memory
- storage type/path description
- operating system
- Java runtime and JVM arguments
- Paper build and plugin version

Missing values are recorded as `unknown` and make a full production-comparison run invalid rather
than being replaced with guessed values.

## LoadTestScenario

Versioned, deterministic workload description:

- scenario ID and revision
- client count and action profile
- region distribution
- target Custom-Mob count and composition
- enabled B05/B08b/B09/B10 workload flags
- warm-up and measurement durations
- external tool name/version/profile

The scenario describes what the external runner must do; it does not become a runtime server
configuration.

## RunManifest

The immutable join between scenario, environment, measurements and result.

| Field | Type | Rules |
|---|---|---|
| `runId` | string | unique, sortable, generated before startup |
| `startedAt` / `finishedAt` | instant | both required for a completed run |
| `status` | enum | `RUNNING`, `PASSED`, `FAILED`, `INVALID`, `ABORTED` |
| `scenario` | LoadTestScenario | required |
| `hardware` | HardwareProfile | required for full-load comparison |
| `artifacts` | list of paths/URLs | must identify raw metrics, report and profiler output |
| `firstFailure` | optional failure record | required when status is `FAILED` or `INVALID` |

## PerformanceReport

An immutable summary for one window or run. It includes all expected sources, even when a source is
missing, the target values, aggregate values, per-subsystem values, alert transitions and a final
status. It is the only object shared by the log reporter, Prometheus exporter and run-manifest
writer.
