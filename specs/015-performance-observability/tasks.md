# Tasks: Performance & Observability

**Input**: Design documents from `/specs/015-performance-observability/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Required for every core rule and integration seam. Tests are written first and must fail
before the matching implementation task, following the project constitution and the agreed
Speckit/TDD workflow.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the feature-owned configuration and external-runner structure without changing
server behaviour.

- [X] T001 [P] Create the documented B15 configuration skeleton in `rpg-plugin/src/main/resources/performance.yml` with measurement, window, alert, report and export keys.
- [X] T002 [P] Add the deterministic full-load scenario definition for 150 players, six regions, 800 Custom-Mobs, warm-up, measurement and workload flags in `tools/b15-loadtest/scenarios/b15-full.yaml`.
- [X] T003 [P] Add the run-manifest JSON schema and required pass/fail fields in `tools/b15-loadtest/schemas/run-manifest.schema.json`.
- [X] T004 [P] Add the external-tool prerequisites, version pinning policy, hardware capture fields and artifact layout to `tools/b15-loadtest/README.md`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish the shared boundaries that every user story depends on. No external export or
full-load task may bypass this phase.

- [X] T005 [P] Add the bukkit-free performance package documentation and public boundary in `rpg-core/src/main/java/rpg/core/performance/package-info.java`, explicitly prohibiting Paper, I/O and scheduling calls.
- [X] T006 [P] Add deterministic performance test fixtures and an injectable monotonic test clock in `rpg-core/src/test/java/rpg/core/performance/PerformanceTestSupport.java`.
- [X] T007 Define the B15 module registration seam and lifecycle ownership in `rpg-plugin/src/main/java/rpg/plugin/performance/PerformanceModule.java`, without starting report/export work yet.
- [X] T008 Verify the Gradle module dependency direction and runtime-jar rule for B15 in `rpg-core/build.gradle.kts`, `rpg-platform/build.gradle.kts` and `rpg-plugin/build.gradle.kts`; do not add a third-party runtime dependency.

**Checkpoint**: The Core/Platform/Plugin boundaries and the external-runner folders exist; all
following work uses the common contracts.

---

## Phase 3: User Story 1 - Tick-Budgets überwachen (Priority: P1) 🎯 MVP

**Goal**: Provide deterministic, bounded subsystem and overall tick measurements with warning,
critical and recovery transitions.

**Independent Test**: Serverless tests can register synthetic subsystems, feed controlled durations,
read p50/p95/p99 and verify the complete alert state machine without a Paper server or load test.

### Tests for User Story 1 (write first)

- [X] T009 [P] [US1] Test bounded sample windows, nearest-rank p50/p95/p99, empty and incomplete states in `rpg-core/src/test/java/rpg/core/performance/PerformanceWindowTest.java` (FR-003, FR-026, SC-001).
- [X] T010 [P] [US1] Test 90-% warning, 60-second critical threshold, deduplication and recovery transitions in `rpg-core/src/test/java/rpg/core/performance/AlertStateTest.java` (FR-006, FR-007, FR-008, FR-009, SC-004).
- [X] T011 [P] [US1] Test registration, duplicate/unknown IDs, disabled measurement and missing-source reporting in `rpg-core/src/test/java/rpg/core/performance/PerformanceRegistryTest.java` (FR-001, FR-005, FR-024, FR-025).
- [X] T012 [P] [US1] Test Paper tick-start/tick-end forwarding, tick-number pairing and no Paper access from the Core in `rpg-platform/src/test/java/rpg/platform/performance/PaperPerformanceListenerTest.java` (FR-002, FR-004).
- [X] T013 [P] [US1] Add a repeatable registry benchmark that measures scope entry/exit and snapshot creation against the B15 local budget in `rpg-core/src/test/java/rpg/core/performance/PerformanceRegistryBenchmarkTest.java` (SC-003).

### Implementation for User Story 1

- [X] T014 [US1] Implement `SubsystemId`, `PerformanceBudget`, `MonotonicClock`, `PerformanceWindow` and immutable percentile snapshots in `rpg-core/src/main/java/rpg/core/performance/` (FR-001–FR-004).
- [X] T015 [US1] Implement `PerformanceRegistry`, bounded per-subsystem storage and short-lived `MeasurementScope` in `rpg-core/src/main/java/rpg/core/performance/PerformanceRegistry.java` and `DefaultPerformanceRegistry.java` (FR-001–FR-005, FR-024–FR-026).
- [X] T016 [US1] Implement `AlertState`, `AlertPolicy` and pure alert transition evaluation in `rpg-core/src/main/java/rpg/core/performance/AlertState.java`, `AlertPolicy.java` and `AlertEvaluator.java` (FR-006, FR-007, FR-008, FR-009).
- [X] T017 [US1] Implement public-Paper tick capture and overall TPS/MSPT source in `rpg-platform/src/main/java/rpg/platform/performance/PaperTickMetrics.java`, `PaperPerformanceSource.java` and `PaperPerformanceListener.java` using `ServerTickStartEvent`/`ServerTickEndEvent` only (FR-002, FR-004).
- [X] T018 [US1] Complete `PerformanceModule` startup/shutdown and register the core registry plus Paper listener in `rpg-plugin/src/main/java/rpg/plugin/performance/PerformanceModule.java` and `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` (FR-001, FR-002, FR-005).
- [X] T019 [US1] Add MockBukkit bootstrap coverage for B15 registration order, listener lifecycle and clean shutdown in `rpg-plugin/src/test/java/rpg/plugin/performance/PerformanceBootstrapTest.java` (FR-005, SC-001).

**Checkpoint**: US1 is independently demonstrable with synthetic scopes, Paper tick events and
bounded alert transitions; no export or full-load dependency is needed.

---

## Phase 4: User Story 3 - Externes Monitoring und Berichte nutzen (Priority: P2)

**Goal**: Publish immutable performance snapshots as structured local reports and Prometheus-
compatible metrics without blocking the tick.

**Independent Test**: A fixed snapshot renders deterministic metric text, report text and error
states; a failed export leaves measurement and local reporting active.

### Tests for User Story 3 (write first)

- [X] T020 [P] [US3] Test metric names, bounded labels, seconds conversion, stale timestamp and complete-file rendering in `rpg-plugin/src/test/java/rpg/plugin/performance/PrometheusTextExporterTest.java` (FR-010, FR-020, SC-002).
- [X] T021 [P] [US3] Test report contents, 60-second scheduling, retry-after-failure and no tick-thread file access in `rpg-plugin/src/test/java/rpg/plugin/performance/PerformanceReportCycleTest.java` (FR-011, FR-012, SC-008).
- [X] T022 [P] [US3] Test fail-fast validation of measurement window, alert threshold, export path and report interval in `rpg-plugin/src/test/java/rpg/plugin/performance/PerformanceConfigSchemaTest.java` (FR-005, FR-027).
- [X] T023 [P] [US3] Test an unwritable export target and verify local log/alert continuity in `rpg-plugin/src/test/java/rpg/plugin/performance/ExportFailureKeepsLocalReportingTest.java` (FR-011).

### Implementation for User Story 3

- [X] T024 [US3] Implement validated B15 configuration in `rpg-plugin/src/main/java/rpg/plugin/performance/PerformanceConfig.java` and `PerformanceConfigSchema.java`, loading `rpg-plugin/src/main/resources/performance.yml` (FR-005, FR-027).
- [X] T025 [US3] Implement immutable snapshot rendering and atomic dependency-free Prometheus text export in `rpg-plugin/src/main/java/rpg/plugin/performance/PrometheusTextExporter.java` according to `contracts/metrics-prometheus.md` (FR-010, FR-011, FR-020).
- [X] T026 [US3] Implement structured English log reports and first-failure/alert formatting in `rpg-plugin/src/main/java/rpg/plugin/performance/PerformanceLogReporter.java` (FR-008, FR-012, FR-023, SC-008).
- [X] T027 [US3] Implement the async self-rescheduling report/export cycle in `rpg-plugin/src/main/java/rpg/plugin/performance/PerformanceReportCycle.java` using only the project `Scheduler` one-shot API (FR-011, FR-012, FR-027).
- [X] T028 [US3] Add active-player, active-Custom-Mob and exporter/report health sources through `rpg-plugin/src/main/java/rpg/plugin/performance/PerformanceModule.java` and `PerformanceScopeWiring.java` without creating player/entity tasks (FR-010, FR-020, SC-002, SC-003).
- [X] T029 [US3] Add integration assertions for shipped `performance.yml`, metric-file replacement and report registration in `rpg-plugin/src/test/java/rpg/plugin/performance/PerformanceModuleTest.java` (FR-010–FR-012, SC-008).

**Checkpoint**: US1 and US3 work independently with deterministic snapshots; external scrape and
local logs remain functional when file export fails.

---

## Phase 5: User Story 5 - Subsysteme mit einem gemeinsamen Messvertrag anbinden (Priority: P3)

**Goal**: Cover the existing RPG hot paths through one explicit performance contract and reject
missing or duplicate coverage.

**Independent Test**: A synthetic new subsystem appears automatically in the central report/export,
while the coverage test fails for an expected source that is absent.

### Tests for User Story 5 (write first)

- [X] T030 [P] [US5] Test that a newly registered subsystem automatically appears in snapshot, alert evaluation, report and export in `rpg-core/src/test/java/rpg/core/performance/NewSubsystemCoverageTest.java` (FR-024, SC-010).
- [X] T031 [P] [US5] Test that missing expected sources are reported as missing rather than zero in `rpg-core/src/test/java/rpg/core/performance/MissingSubsystemIsNotZeroTest.java` (FR-020, FR-022, SC-001, SC-007).
- [X] T032 [P] [US5] Test duplicate and unknown coverage IDs against the B05/B08b/B09/B10/B13/B14 coverage matrix in `rpg-plugin/src/test/java/rpg/plugin/performance/PerformanceCoverageTest.java` (FR-025, SC-010).

### Implementation for User Story 5

- [X] T033 [US5] Implement the explicit expected-source matrix and registration adapter in `rpg-plugin/src/main/java/rpg/plugin/performance/PerformanceScopeWiring.java` (FR-001, FR-024, FR-025).
- [X] T034 [US5] Instrument B13 HUD work and B10 horde sweep through short scopes in `rpg-platform/src/main/java/rpg/platform/ui/HudTick.java` and `rpg-platform/src/main/java/rpg/platform/mob/HordeSweep.java` (FR-002, FR-016, FR-024).
- [X] T035 [US5] Instrument B05 combat, B08b coin-drop, B09 zone-movement and B12 statistics paths through the common scope in `rpg-platform/src/main/java/rpg/platform/combat/VanillaDamageListener.java`, `rpg-platform/src/main/java/rpg/platform/currency/CoinDropListener.java`, `rpg-platform/src/main/java/rpg/platform/zone/ZoneMovementListener.java` and `rpg-platform/src/main/java/rpg/platform/statistics/DamageStatListener.java` (FR-002, FR-016, FR-024).
- [X] T036 [US5] Instrument B03 login loading and B14 command execution as bounded non-player/entity timing sources in `rpg-platform/src/main/java/rpg/platform/session/SessionPreLoadListener.java` and `rpg-plugin/src/main/java/rpg/plugin/command/framework/CommandTree.java` without measuring async I/O as tick time (FR-016, FR-026, FR-027).
- [X] T037 [US5] Add the live bootstrap coverage assertion for all expected B15 source IDs in `rpg-plugin/src/test/java/rpg/plugin/FullBootstrapTest.java` and `PerformanceBootstrapTest.java` (FR-024, FR-025, SC-001, SC-010).
- [X] T038 [US5] Add per-entrypoint repeatable benchmarks for the common scope overhead and existing B13/B10 paths in `rpg-platform/src/test/java/rpg/platform/performance/PerformanceScopeBenchmarkTest.java` (FR-005, FR-026, SC-003).

**Checkpoint**: Every declared B15 source has one shared registration path, explicit coverage and a
missing-source failure; no block gets a private recurring timer.

---

## Phase 6: User Story 2 - Reproduzierbaren Lasttest ausführen (Priority: P1)

**Goal**: Produce a repeatable 150-player/800-mob run manifest, raw metrics and pass/fail result
for the complete B15 scenario.

**Independent Test**: The external runner can dry-run and validate the scenario/manifest contract
without a production plugin dependency; the real Paper run remains a separate acceptance step.

### Tests for User Story 2 (write first)

- [X] T039 [P] [US2] Add a dry-run validation for scenario counts, six-region distribution, 15/30-minute phases and required workloads in `tools/b15-loadtest/scripts/validate-scenario.ps1`.
- [X] T040 [P] [US2] Add manifest result validation for missing data, insufficient load and threshold failures in `tools/b15-loadtest/scripts/validate-manifest.ps1` against `contracts/load-test-manifest.md` and `run-manifest.schema.json` (FR-019–FR-023, FR-028, SC-005–SC-007).

### Implementation for User Story 2

- [X] T041 [US2] Implement the Windows runner wrapper, hardware/software capture, server readiness checks, artifact directory and status transitions in `tools/b15-loadtest/scripts/run-b15.ps1` (FR-015, FR-018, FR-019, FR-022, FR-028).
- [X] T042 [US2] Implement the `mc-pilot` profile/command mapping for movement, combat, coin-drop, horde and region actions in `tools/b15-loadtest/README.md` and `tools/b15-loadtest/scenarios/b15-full.yaml` (FR-015, FR-016, FR-017, FR-018).
- [X] T043 [US2] Implement pass/fail evaluation and first-failure recording for TPS, MSPT p95/p99, active mobs, duration and missing sources in `tools/b15-loadtest/scripts/validate-manifest.ps1` (FR-021–FR-023, FR-028, SC-006, SC-007).
- [X] T044 [US2] Add a synthetic fixture manifest and dry-run examples under `tools/b15-loadtest/fixtures/` proving `PASSED`, `FAILED` and `INVALID` outcomes without a live server (FR-022, FR-023, SC-007).

**Checkpoint**: The external run contract is executable in dry-run mode. Only the real Paper-server
acceptance remains open and cannot be replaced by unit tests.

---

## Phase 7: User Story 4 - Profiler für Ursachenanalyse einsetzen (Priority: P2)

**Goal**: Link a controlled Spark profiling session to the same run manifest without adding a
permanent profiler dependency.

**Independent Test**: A profiling manifest can be created, validated and associated with a report;
the normal plugin build contains no Spark classes.

### Tests for User Story 4 (write first)

- [X] T045 [P] [US4] Test profiling-session start/end, required run ID and optional artifact URL in `rpg-plugin/src/test/java/rpg/plugin/performance/ProfilingRunManifestTest.java` (FR-013, SC-009).
- [X] T046 [P] [US4] Add a build guard test that the plugin artifact contains no Spark or profiler implementation classes in `rpg-plugin/src/test/java/rpg/plugin/performance/NoRuntimeProfilerDependencyTest.java` (FR-014).

### Implementation for User Story 4

- [X] T047 [US4] Implement immutable profiling manifest creation and artifact association in `rpg-plugin/src/main/java/rpg/plugin/performance/ProfilingRunManifest.java` (FR-013, FR-014, SC-009).
- [X] T048 [US4] Add the Spark command, timeout, evidence capture and report-link workflow to `tools/b15-loadtest/README.md` and `specs/015-performance-observability/quickstart.md` (FR-013, FR-014).

**Checkpoint**: A controlled profiler run is traceable, while the ordinary server build remains
independent of Spark.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Complete documentation, validation and the real operational acceptance.

- [X] T049 [P] Update `minecraft-rpg-spec/minecraft-rpg-spec/blocks/B15-performance-observability.md`, `minecraft-rpg-spec/minecraft-rpg-spec/docs/01-architecture.md`, `minecraft-rpg-spec/minecraft-rpg-spec/docs/02-decisions.md` and `minecraft-rpg-spec/minecraft-rpg-spec/docs/06-open-questions.md` with the final B15 decisions, status and ADR references.
- [X] T050 [P] Add B15 source, configuration-key and bootstrap completeness assertions in `rpg-plugin/src/test/java/rpg/plugin/FullBootstrapTest.java` and `rpg-plugin/src/test/java/rpg/plugin/performance/PerformanceBootstrapTest.java` for every implemented B15 source and configuration path (FR-001–FR-028).
- [X] T051 Run focused Core, Platform and Plugin B15 tests plus the repeatable benchmarks; record results in `specs/015-performance-observability/quickstart.md` and mark only proven tasks `[X]`.
- [X] T052 Run the full `./gradlew.bat test --no-daemon` suite and confirm no skipped tests; record the evidence before marking B15 full-suite validation complete.
- [ ] T053 Perform the real Paper server quickstart with the deployed B15 JAR and changed `performance.yml`; verify startup validation, tick metrics, export file, log report, forced alert and recovery (FR-005–FR-012).
- [ ] T054 Execute the complete 15-minute warm-up plus 30-minute `mc-pilot` run on the documented target hardware; archive manifest, raw metrics, report, server log and optional Spark artifact (FR-015–FR-023, SC-005–SC-009).
- [X] T055 Review the final source scan for direct Bukkit scheduler use, Paper access from Core, blocking tick I/O, per-player/entity recurring work and unbounded metric labels; resolve or document every finding before completion (Constitution I–VIII).

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: independent; creates only feature-owned configuration and runner artifacts.
- **Foundational (Phase 2)**: depends on Setup and blocks all user stories.
- **US1 (Phase 3)**: depends on Foundational; MVP and source of immutable snapshots.
- **US3 (Phase 4)**: depends on US1 snapshots and alert transitions.
- **US5 (Phase 5)**: depends on US1 and US3 contracts so coverage appears in all outputs.
- **US2 (Phase 6)**: depends on US1/US3/US5 output contracts but can develop its dry-run scripts in parallel after Phase 2.
- **US4 (Phase 7)**: depends on the run manifest from US2 and report artifacts from US3.
- **Polish (Phase 8)**: depends on all desired implementation phases; T053/T054 require explicit server access.

### User Story Dependencies

- **US1 (P1)**: after Foundational; no external story dependency.
- **US2 (P1)**: dry-run tooling after Foundational; real acceptance requires US1, US3 and US5 outputs.
- **US3 (P2)**: after US1.
- **US4 (P2)**: after US2 manifest and US3 report contracts.
- **US5 (P3)**: after US1; integration into export/report is checked after US3.

### Parallel Opportunities

- T001–T004 can run in parallel because they touch separate setup files.
- T009–T013, T020–T023, T030–T032, T039–T040 and T045–T046 can run in parallel as independent tests.
- T034 and T035 can run in parallel after T033 if their files remain separate; T036 must follow them before the coverage test is finalized.
- T041–T044 can run in parallel with US3 implementation after the foundational phase, but the real runner must not be declared passing until the plugin export contract is available.

## Parallel Example: User Story 1

```text
Task: T009 PerformanceWindowTest
Task: T010 AlertStateTest
Task: T011 PerformanceRegistryTest
Task: T012 PaperPerformanceListenerTest
Task: T013 PerformanceRegistryBenchmarkTest
```

All five tests are independent and can be written before T014–T018 implement the shared behavior.

## Implementation Strategy

### MVP First (User Story 1 only)

1. Complete Setup and Foundational phases.
2. Implement US1 with serverless tests and Paper event integration.
3. Validate bounded windows, alerts and bootstrap independently.
4. Stop for review before adding external export or full-load tooling.

### Incremental Delivery

1. US1 provides reliable in-process measurement.
2. US3 adds reports and external metrics without changing measurement semantics.
3. US5 instruments the existing block hot paths and makes missing coverage visible.
4. US2 adds the reproducible external full-load runner and pass/fail manifest.
5. US4 adds controlled Spark evidence and diagnosis links.
6. Polish completes real Paper and target-hardware acceptance; unit tests alone cannot close T053/T054.

## Notes

- Every task has a checkbox, sequential ID, optional parallel marker, user-story label where required
  and an exact file path.
- No task commits or pushes automatically; the B15 branch remains reviewable after each logical group.
- The real server and full-load tasks intentionally remain open until the required server, target
  hardware and external client tooling are available.
