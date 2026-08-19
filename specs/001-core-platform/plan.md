# Implementation Plan: B01 · Core & Plattform

**Branch**: `001-core-platform` | **Date**: 2026-08-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-core-platform/spec.md`

## Summary

B01 liefert das technische Fundament, gegen das alle weiteren Blöcke (B02–B17) entwickelt
werden: deterministischer Modul-Bootstrap mit Fail-Fast-Konfigurationsvalidierung, eine
Modul-/Service-Registry ohne statische Kopplung, ein interner Event-Bus mit isolierter
Fehlerbehandlung, eine Scheduler-Abstraktion mit strikter Sync-/Async-Typtrennung und
ausschließlich location-/entity-gebundener Terminierung (ADR-007), sowie eine vorerst
intern gehaltene, aber vorbereitete Plugin-API-Grenze. Technischer Ansatz: ein
Multi-Modul-Gradle-Projekt mit einer eigenen, reflection-freien Service-Registry (statt
eines DI-Frameworks) und YAML als Konfigurationsformat, um Klassenlader-Konflikte in einem
geteilten Bukkit/Paper-Prozess zu vermeiden und die Startreihenfolge explizit und
nachvollziehbar zu halten.

## Technical Context

**Language/Version**: Java 25 (ADR-001), Toolchain über Gradle Java-Toolchains fixiert

**Primary Dependencies**: Paper-API (Minecraft 26.2) für `rpg-platform`/`rpg-plugin`;
`rpg-core` hat **keine** Laufzeitabhängigkeit zu Bukkit/Paper. SnakeYAML (bzw.
Jackson `dataformat-yaml`) für den Konfigurations-Loader; kein DI-Framework.

**Storage**: N/A für B01 selbst (keine Persistenz in diesem Block; Scheduler-Abstraktion
und Konfigurations-Loader sind reine In-Memory-/Dateisystem-Mechanismen). Grenzfläche zu
B02 (Persistenz) wird über die Service-Registry bereitgestellt, aber nicht in B01
implementiert.

**Testing**: JUnit 5 (Jupiter) + AssertJ für `rpg-core` (Registry, Event-Bus,
Abhängigkeits-/Zyklus-Auflösung, Konfigurations-Validierung) — vollständig ohne laufenden
Server (Constitution VII.1). Für `rpg-platform`-Adapter (Scheduler-Bindung an die
Paper-API) ergänzend Tests gegen eine Bukkit/Paper-Testdouble-Bibliothek (z. B.
MockBukkit), damit auch die Plattform-Schicht ohne echten Server prüfbar ist.

**Target Platform**: Linux-VPS, Paper-Server-Prozess (Minecraft 26.2 / Java 25), gleiche
Maschine wie PostgreSQL (siehe `06-open-questions.md`, Abschnitt „Betrieb").

**Project Type**: Server-Plugin (Paper/Bukkit-Plugin), Multi-Modul-Gradle-Projekt.

**Performance Goals**: Bootstrap bis Bereitschaft für ersten Spieler-Join ≤ 30 s (SC-001);
Shutdown je Modul ≤ 10 s vor Zwangsterminierung (SC-007/FR-012); jedes Subsystem ≤ 5 ms
Tick-Budget im Normalbetrieb (Constitution II.1); Registry-Lookup und Event-Dispatch dürfen
im Hot Path keine spürbare Allokation/Reflection-Kosten verursachen, da nachfolgende
Blöcke (z. B. B05 Kampf-Pipeline) den Event-Bus potenziell pro Kampf-Ereignis nutzen.

**Constraints**: `rpg-core` MUSS ohne Bukkit-Abhängigkeit kompilieren und ohne laufenden
Server testbar sein (Constitution III.1). Abhängigkeitsrichtung strikt
`plugin → platform → core` (Constitution III.2). Kein Zugriff auf den globalen
Bukkit-Scheduler, ausschließlich location-/entity-gebundene Terminierung (ADR-007,
Constitution I.5). Kein blockierender Aufruf im Tick-Pfad (Constitution I.1–I.3). 100–200
gleichzeitige Spieler auf einem einzelnen Server (ADR-002).

**Scale/Scope**: 5 Module zum Start (`rpg-core`, `rpg-persistence`, `rpg-platform`,
`rpg-content`, `rpg-plugin`), wachsend auf bis zu 17 Architekturblöcke (B01–B17) als
Konsumenten von Registry, Event-Bus und Scheduler-Abstraktion.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Prinzip | Prüfung | Status |
|---|---|---|
| I. Nebenläufigkeit | Scheduler-Abstraktion erzwingt Sync-/Async-Typtrennung im Typsystem (FR-007), ausschließlich location-/entity-gebundene Terminierung (FR-008), kein globaler Scheduler-Zugriff möglich. Kein blockierender I/O in B01 selbst. | PASS |
| II. Performance | Bootstrap-/Shutdown-Zeitbudgets festgelegt (30 s / 10 s je Modul). Eigene, reflection-freie Registry vermeidet Reflection-Overhead beim Start; Registry-Lookups sind einfache Map-Zugriffe, keine periodischen Pro-Modul-Tasks. Risiko: Event-Bus-Nutzung durch spätere Blöcke im Hot Path — als Constraint dokumentiert, Verantwortung liegt bei den nutzenden Blöcken. | PASS (mit dokumentiertem Risiko) |
| III. Architektur | `rpg-core` bukkit-frei und serverlos testbar (FR-015); Abhängigkeitsrichtung `plugin → platform → core` durch Gradle-Modulgrenzen technisch erzwungen; jedes Modul hat eine explizite Schnittstelle über die Registry. | PASS |
| IV. Datenhaltung | N/A für B01 (keine Persistenz in diesem Block); Registry stellt lediglich die Erweiterungsgrenze für B02 bereit. | PASS (N/A) |
| V. Datengetriebenes Design | Konfigurations-Loader validiert Schema beim Start und bei Reload, Fail-Fast mit Datei/Pfad/Wert (FR-002); YAML als versioniertes Konfigurationsformat. | PASS |
| VI. Korrektheit & Sicherheit | Fehler in einem Modul lokal begrenzt (FR-009); Fehler in einem Event-Bus-Abonnenten stoppt nicht die Zustellung an andere (FR-006a); kein Reflection-/NMS-Zugriff in B01 vorgesehen. | PASS |
| VII. Tests | `rpg-core` (Registry, Event-Bus, Zyklus-Erkennung, Config-Validierung) vollständig unit-testbar ohne Server (JUnit 5); Plattform-Adapter über Testdouble geprüft. | PASS |
| VIII. Sprache | Diese Planungsdokumente auf Deutsch; Code, Identifier, Konfigurationsschlüssel und Commit-Messages auf Englisch. | PASS |

Keine Verstöße → **Complexity Tracking entfällt.**

**Re-Check nach Phase 1 (Design & Contracts)**: Die in `research.md` getroffenen
Entscheidungen (eigene reflection-freie Registry, YAML, Gradle) und die in `contracts/`
festgehaltenen Schnittstellenverträge (insbesondere die harte Typsystem-Grenze gegen
ungebundenes Scheduling in `contracts/scheduler.md`) führen zu keiner neuen Abweichung von
den obigen acht Prinzipien. Alle Gates bleiben **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/001-core-platform/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
rpg-core/                          # Bukkit-frei, ohne laufenden Server testbar
├── src/main/java/rpg/core/
│   ├── module/                    # Module, ModuleRegistry, dependency/cycle resolution
│   ├── event/                     # EventBus, Event, Subscriber-Fehlerisolation
│   ├── scheduler/                 # Scheduler-Abstraktion (Interfaces: TickTask, AsyncTask)
│   └── config/                    # ConfigSchema, ConfigLoader-Interface, Validation
└── src/test/java/rpg/core/        # JUnit 5, keine Bukkit-Abhängigkeit

rpg-persistence/                   # Platzhalter für B02, in B01 nur als leeres Modul angelegt
├── src/main/java/rpg/persistence/
└── src/test/java/rpg/persistence/

rpg-platform/                      # Paper-Adapter: Bukkit-Scheduler-Bindung, Event-Bridge
├── src/main/java/rpg/platform/
│   ├── scheduler/                 # PaperSchedulerAdapter (location-/entity-gebunden)
│   └── config/                    # YamlConfigLoader (SnakeYAML-Implementierung)
└── src/test/java/rpg/platform/    # MockBukkit-gestützte Tests

rpg-content/                       # Platzhalter für B16, in B01 nur als leeres Modul angelegt
└── src/main/java/rpg/content/

rpg-plugin/                        # Bootstrap: plugin.yml, Haupt-Plugin-Klasse, Modulverdrahtung
├── src/main/java/rpg/plugin/
│   └── RpgPlugin.java             # onEnable/onDisable, registriert alle Module in Reihenfolge
└── src/main/resources/
    └── plugin.yml
```

**Structure Decision**: Multi-Modul-Gradle-Projekt mit den fünf in `01-architecture.md`
festgelegten Modulen. `rpg-core` enthält die gesamte B01-Domänenlogik (Registry, Event-Bus,
Scheduler-Interfaces, Config-Schema) ohne Bukkit-Abhängigkeit. `rpg-platform` enthält die
konkreten Paper-Adapter (Scheduler-Implementierung, YAML-Loader). `rpg-plugin` verdrahtet
alles beim Bootstrap. `rpg-persistence` und `rpg-content` werden in B01 nur als leere
Gradle-Module angelegt (Grundlage für B02 bzw. B16), ohne Inhalt in diesem Feature.

## Complexity Tracking

*Keine Verstöße gegen die Constitution Check-Gates — Abschnitt entfällt.*
