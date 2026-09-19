# Implementation Plan: Performance & Observability

**Branch**: `015-performance-observability` | **Date**: 2026-09-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/015-performance-observability/spec.md`

## Summary

B15 liefert einen zentralen, bukkitfreien Messvertrag für RPG-Subsysteme, eine Paper-Anbindung für
Server-Tickwerte, eine begrenzte Perzentil-Auswertung, Zustandsautomaten für Warnungen und Alarme,
strukturierte Berichte sowie einen asynchronen Prometheus-Text-Export. Die Messung wird über die
öffentlichen `ServerTickStartEvent`-/`ServerTickEndEvent`-Ereignisse und explizite Scopes an den
vorhandenen Tick-Einstiegspunkten angebunden. Export und Berichte laufen über den bestehenden
asynchronen One-shot-Scheduler und niemals über den Tick.

Der Profiler bleibt ein kontrollierter Betriebsablauf mit Papers eingebautem Spark und wird nicht
als Laufzeitabhängigkeit in das RPG-JAR gezogen. Für den Volllastnachweis wird `mc-pilot` als
externes, versionsgeprüftes Werkzeug für echte 26.2-Clients dokumentiert; das Plugin bleibt davon
unabhängig. Die Lasttest-Szenarien, Laufmanifeste und Ergebnisartefakte liegen unter
`tools/b15-loadtest/` und werden nicht in den Server-Tick eingebaut.

## Technical Context

**Language/Version**: Java 25, Gradle Kotlin DSL

**Primary Dependencies**: Paper API `26.2.build.112-stable`, bestehende Projektmodule, JUnit 6,
AssertJ und MockBukkit; keine neue Runtime-Abhängigkeit für Messung oder Export

**Storage**: `performance.yml` für Konfiguration; atomar ersetzte Prometheus-Textdatei und
versionierte Laufmanifeste/Reports für Testläufe; keine Datenbankänderung

**Testing**: serverlose JUnit-/AssertJ-Tests in `rpg-core`, MockBukkit-/Paper-Integrationstests in
`rpg-platform` und `rpg-plugin`, wiederholbare Benchmarks ohne Volllast, anschließend echter Paper-
Quickstart und `mc-pilot`-Lauf

**Target Platform**: Paper auf Minecraft 26.2 mit Java 25; Entwicklung und erster Serverabgleich
unter Windows, das Design bleibt plattformneutral bei Pfaden und Prozessaufrufen

**Project Type**: Multi-Modul-Paper-Plugin mit bukkitfreier Domäne und separatem externem
Lasttestwerkzeug

**Performance Goals**: mindestens 19,5 TPS im Mittel bei 150 Spielern unter Kampflast, MSPT p95
unter 40 ms, MSPT p99 unter 50 ms, mindestens 800 aktive Custom-Mobs, höchstens 5 ms pro
Subsystem im Normalbetrieb, Export innerhalb eines 15-Sekunden-Fensters, Bericht standardmäßig je
60 Sekunden

**Constraints**: kein Bukkit-/Paper-Zugriff außerhalb des Server-Ticks, kein blockierender I/O im
Tick-Pfad, kein `join()`/`get()` auf Futures, keine wiederkehrende Aufgabe je Spieler oder Entity,
keine direkte Nutzung des globalen Bukkit-Schedulers, keine Reflection/NMS-Abhängigkeit, bounded
memory für Messfenster, Messung abschaltbar, bestehende Plugin-JAR-Regel ohne fremde Runtime-Klassen

**Scale/Scope**: 150 simulierte Spieler, sechs Regionen, mindestens 800 aktive Custom-Mobs,
15 Minuten Warm-up und 30 Minuten Messung, 60-Sekunden-Alarmfenster, mindestens die vorhandenen
Performance-Einstiegspunkte aus B05, B08b, B09, B10, B13 und B14 sowie der Login-Ladepfad

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Design decision | Status |
|---|---|---|---|
| I. Nebenläufigkeit | Paper/Bukkit nur im Tick; I/O async | `ServerTick*Event` sammelt nur primitive Werte. Export, Dateischreiben und Berichtsaggregation laufen auf unveränderlichen Snapshots über `runAsync`/`runAsyncDelayed`. | PASS |
| II. Performance | ≤5 ms; keine Pro-Spieler-/Pro-Entity-Tasks | Scopes nutzen nur monotone Zeitmessung und bounded buffers. Ein einziger serverweiter One-shot-Zyklus rearmt sich selbst; kein Scope plant Arbeit. | PASS |
| III. Architektur | `plugin → platform → core`; Core ohne Bukkit | Registry, Window, Alert-State und Report liegen in `rpg-core`; Paper-Events und Server-APIs liegen in `rpg-platform`; Verdrahtung und Export liegen im Plugin. | PASS |
| IV. Datenhaltung | keine synchrone Persistenz; Migrationen | B15 führt keine DB-Tabelle ein. Reports werden außerhalb des Ticks atomar geschrieben; ein Exportfehler beeinflusst den Gameplay-Zustand nicht. | PASS |
| V. Datengetrieben | Konfiguration validieren; keine hartcodierten Spielertexte | `performance.yml` enthält Budgets, Fenster, Schalter und Pfade; Konfigurationsfehler führen beim Start zu einer klaren Ablehnung. | PASS |
| VI. Korrektheit & Sicherheit | Fehler lokal begrenzen; Reflection/NMS vermeiden | Fehlende Metriken und Exportfehler werden als Zustand markiert, nicht als Nullwerte. Nur öffentliche Paper-APIs, kein eingebetteter HTTP-Server. | PASS |
| VII. Tests | Regeln serverlos; Lasttest gebündelt in B15 | Core-Regeln serverlos, Paper-Anbindung mit MockBukkit, Vollast erst im B15-Quickstart mit realem Paper und externen Clients. | PASS |
| VIII. Sprache | Doku Deutsch; Code/Keys/Spielertexte Englisch | Dokumentation und Reports werden deutsch beschrieben; Java-Bezeichner, YAML-Keys, Log-/Spielertexte und Commit bleiben Englisch. | PASS |

## Project Structure

### Documentation (this feature)

```text
specs/015-performance-observability/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── contracts/
│   ├── performance-registry.md
│   ├── metrics-prometheus.md
│   └── load-test-manifest.md
├── quickstart.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
rpg-core/src/main/java/rpg/core/performance/
├── AlertEvaluator.java
├── AlertPolicy.java
├── AlertState.java
├── DefaultPerformanceRegistry.java
├── MeasurementScope.java
├── MonotonicClock.java
├── PerformanceBudget.java
├── PerformanceReport.java
├── PerformanceRegistry.java
├── PerformanceSnapshot.java
├── SubsystemId.java
├── PerformanceWindow.java
└── package-info.java

rpg-core/src/test/java/rpg/core/performance/
├── PerformanceRegistryTest.java
├── PerformanceWindowTest.java
├── AlertStateTest.java
├── PerformanceRegistryBenchmarkTest.java
└── MissingSubsystemIsNotZeroTest.java

rpg-platform/src/main/java/rpg/platform/performance/
├── PaperTickMetrics.java
├── PaperPerformanceListener.java
└── PaperPerformanceSource.java

rpg-platform/src/test/java/rpg/platform/performance/
├── PaperPerformanceListenerTest.java
└── PaperPerformanceSourceTest.java

rpg-plugin/src/main/java/rpg/plugin/performance/
├── PerformanceModule.java
├── PerformanceConfig.java
├── PerformanceConfigSchema.java
├── PerformanceReportCycle.java
├── PrometheusTextExporter.java
├── PerformanceLogReporter.java
├── ProfilingRunManifest.java
└── PerformanceScopeWiring.java

rpg-plugin/src/main/resources/
└── performance.yml

rpg-plugin/src/test/java/rpg/plugin/performance/
├── PerformanceModuleTest.java
├── PerformanceConfigSchemaTest.java
├── PrometheusTextExporterTest.java
├── PerformanceReportCycleTest.java
├── PerformanceBootstrapTest.java
└── PerformanceMessagesTest.java

tools/b15-loadtest/
├── README.md
├── scenarios/b15-full.yaml
├── scripts/run-b15.ps1
├── scripts/validate-manifest.ps1
├── scripts/validate-scenario.ps1
├── fixtures/
└── schemas/run-manifest.schema.json
```

**Structure Decision**: B15 ergänzt die bestehende Schichtenarchitektur um ein kleines Domänen-
Messpaket, einen Paper-Adapter und ein Plugin-Modul. Der Kern kennt weder Paper noch Dateien.
Der Export schreibt nur einen Snapshot auf einem asynchronen Pfad. Das externe Lasttestwerkzeug
bleibt in `tools/` und wird weder als Gradle-Subprojekt noch als Plugin-Laufzeitabhängigkeit
eingebettet.

## Design Decisions

### Measurement boundary

`PerformanceRegistry` stellt `register(id, budget)` und einen kurzlebigen Mess-Scope bereit. Ein
Scope nimmt beim Eintritt und Austritt nur monotone Zeitstempel und zählt Samples. Die Samples
werden pro Kennung in einem bounded Ringfenster gehalten; eine unbekannte oder doppelte Kennung
wird nicht stillschweigend angelegt. Ein unveränderlicher `PerformanceSnapshot` ist die einzige
Übergabe an Export, Report und Alarmierung.

Paper liefert Gesamt-Tickwerte über die öffentlichen `ServerTickStartEvent`- und
`ServerTickEndEvent`-Ereignisse. Die vorhandene Paper-API liefert zusätzlich TPS/MSPT-Werte; die
Adapterklasse kapselt sie in `rpg-platform`. Es gibt keinen NMS- oder Reflection-Fallback.

### Alerting and reporting

Die Core-Logik bildet `NORMAL`, `WARNING`, `CRITICAL` und `RECOVERED` ab. `WARNING` beginnt bei
90 % des Budgets. `CRITICAL` entsteht nach 60 Sekunden ununterbrochener Überschreitung. Ein
Recovery-Event beendet den Zustand; wiederholte Meldungen derselben Episode werden unterdrückt.
Der Report-Zyklus sammelt alle 60 Sekunden einen Snapshot, schreibt Logs und rearmt sich als
serverweiter asynchroner One-shot. Kein Report wird aus dem Tick heraus auf Platte geschrieben.

### External export

Der Export erzeugt eine dependency-freie Prometheus-Textdatei. Der Inhalt wird außerhalb des Ticks
aus einem unveränderlichen Snapshot aufgebaut und zunächst in eine temporäre Datei geschrieben,
danach atomar ersetzt. Ein konfigurierter Pfadfehler setzt den Exportstatus auf `ERROR`, stoppt
aber weder Messung noch Alarmierung. Die B15-Konfiguration enthält keinen offenen HTTP-Port und
verkleinert damit die Angriffsfläche des Plugins.

### Profiler and load test

Paper bündelt Spark; B15 dokumentiert den kontrollierten Befehl und verknüpft dessen Ergebnis-URL
oder Bericht mit einer Laufkennung. Der normale Serverbetrieb lädt kein Spark-JAR durch B15.

`mc-pilot` wird als externes Werkzeug mit geprüftem 26.2-Clientprofil dokumentiert. Die
Szenariodatei beschreibt 150 Clients, sechs Regionen, 800 Custom-Mobs, Bewegung, Kampf, Coin-Drops,
Horden, Warm-up und Messdauer. Ein Laufmanifest enthält Hardware-/Softwareprofil, Werkzeugversion,
Konfiguration, Zeitfenster, Rohdatenpfade und Ergebnis. Falls die Version des Werkzeugs bei der
Serverabnahme nicht mehr kompatibel ist, wird nur der externe Adapter/Quickstart angepasst; das
Plugin-Design bleibt unverändert.

### Existing subsystem coverage

Die Verdrahtung misst nur tatsächlich tickgebundene Einstiegspunkte und benennt jeden erwarteten
Pfad explizit. Für B15 werden zunächst die vorhandenen Hotspots aus B05, B08b, B09, B10, B13 und
B14 über den gemeinsamen Scope angebunden. Async-Persistenzarbeit wird separat als Laufzeit-
und Fehlermetrik erfasst, aber nicht fälschlich als Tickzeit ausgegeben. Der Coverage-Test schlägt
fehl, wenn ein erwarteter Block im Bericht fehlt.

## Complexity Tracking

Keine Constitution-Verletzung und daher keine Ausnahme erforderlich.
