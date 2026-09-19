# B15 Quickstart: Performance & Observability

## Prerequisites

- JDK 25 and the repository Gradle wrapper
- Paper/Minecraft 26.2 using the project server build
- Docker only when running the existing persistence integration tests
- Node.js 20+ and the externally pinned `mc-pilot` CLI for the full-load phase
- A clean copy/backup of the test world before any 150-player run

## 1. Serverless validation

Run the core rules before starting a Paper server:

```powershell
./gradlew.bat :rpg-core:test --tests "rpg.core.performance.*"
./gradlew.bat :rpg-core:test --tests "rpg.core.performance.PerformanceRegistryBenchmarkTest"
```

Expected result:

- registration rejects blank, duplicate and unknown subsystem IDs;
- p50/p95/p99 and missing-source states are deterministic;
- warning, critical, recovery and deduplication transitions are correct;
- the repeatable registry benchmark remains below the B15 budget without a full load test.

## 2. Paper integration and export

Build and run the focused integration tests:

```powershell
./gradlew.bat :rpg-platform:test --tests "rpg.platform.performance.*"
./gradlew.bat :rpg-plugin:test --tests "rpg.plugin.performance.*"
```

Start a test Paper server with the built plugin and verify:

1. the server reaches the normal ready state;
2. the performance module registers before its dependent reporting/export cycle;
3. `performance.yml` is validated at startup;
4. the configured Prometheus text file is replaced as one complete file;
5. log reports appear every 60 seconds;
6. a deliberately slow test subsystem creates one warning/critical episode and one recovery;
7. a deliberately unwritable export path leaves local logs and alarms alive.

## 3. Validation evidence

Validated on 2026-09-12 in the B15 worktree:

```powershell
./gradlew.bat :rpg-core:test --tests "rpg.core.performance.*" :rpg-platform:test --tests "rpg.platform.performance.*" --tests "rpg.platform.session.SessionListenerTest" :rpg-plugin:test --tests "rpg.plugin.performance.*" --tests "rpg.plugin.FullBootstrapTest" --no-daemon
./gradlew.bat test --no-daemon
```

Both runs completed with `BUILD SUCCESSFUL`. The focused run covered the B15 Core,
Platform and Plugin tests, including the repeatable performance benchmark. The full suite
reported 3,282 tests in 619 suites with 0 skipped, 0 failures and 0 errors.

The scenario and manifest validators were also run against the passed, failed and invalid
fixtures. They classified the fixtures as `PASSED`, `FAILED` and `INVALID` respectively;
artifact checks passed for the valid fixture set. The real Paper-server and target-hardware
acceptance remains intentionally open in T053/T054.

The final source scan found no Paper imports in Core, no direct Bukkit scheduler calls, no
unbounded metric labels in the Prometheus exporter, and no blocking B15 file I/O on the tick
path. `HordeSweep` retains one zone-level delayed sweep through the platform scheduler; it is
not a player- or entity-owned recurring task.

## 4. Controlled profiler run

Paper includes Spark for current versions. During a controlled test run, start a ten-minute
profile from the server console:

```text
/spark profiler start --timeout 600
```

Record the returned report URL or artifact path in the B15 run manifest together with the B15
`runId`. Do not enable profiling for the ordinary idle-server comparison run.

The plugin-side value object `ProfilingRunManifest` enforces the same association in tests: a
profiling entry cannot finish before it starts, always carries the B15 run ID, and may omit the
artifact URL when Paper's Spark output is stored only as a local file.

## 5. Full-load run

Install and pin the externally selected `mc-pilot` version in the test environment. Verify that its
26.2 client profile and Java 25 runtime are available before connecting any clients. The runner must
write the hardware profile and tool version before startup.

Use `tools/b15-loadtest/scenarios/b15-full.yaml` with:

- 150 simulated clients;
- six configured regions;
- at least 800 active Custom-Mobs;
- movement, B05 combat, B08b coin drops, B09 zone movement and B10 horde activity;
- 15 minutes warm-up;
- 30 minutes measurement.

Validate the scenario before connecting clients:

```powershell
pwsh -NoProfile -File tools/b15-loadtest/scripts/validate-scenario.ps1 `
  -ScenarioPath tools/b15-loadtest/scenarios/b15-full.yaml
```

The Windows runner captures the real target machine's CPU, cores, memory, storage, OS, Java and
JVM arguments in `run-manifest.json`. The external `mc-pilot` adapter supplies `result.json`; the
runner then copies `metrics.prom`, the structured report and the server log into the immutable
`runId` directory and invokes `validate-manifest.ps1 -CheckArtifacts`.

The server must expose the B15 metrics file and log report throughout the run. Stop and mark the
run `INVALID` if the player or mob target is not reached, the server restarts, or the metrics file
becomes stale.

## 6. Acceptance

The run is `PASSED` only when all conditions hold:

- mean TPS is at least 19.5;
- MSPT p95 is below 40 ms;
- MSPT p99 is below 50 ms;
- at least 800 Custom-Mobs were active;
- the measurement lasted the full 30 minutes;
- no expected subsystem is missing from the report;
- the manifest, raw metrics, report, server log and optional profiler artifact are present.

Otherwise the runner writes `FAILED` or `INVALID` and records the first failed condition. Results
from different Paper, Java, hardware or scenario versions must not be aggregated into one baseline.
