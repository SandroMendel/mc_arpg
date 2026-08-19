---
description: "Task list for B01 · Core & Plattform"
---

# Tasks: B01 · Core & Plattform

**Input**: Design documents from `/specs/001-core-platform/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md
(alle vorhanden)

**Tests**: Enthalten. Constitution VII.1 verlangt Unit-Tests ohne laufenden Server für
jede Formel/Regel der Domänenschicht — für `rpg-core` daher nicht optional.

**Organization**: Aufgaben sind nach User Story aus `spec.md` gruppiert, damit jede Story
unabhängig implementiert und getestet werden kann.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Kann parallel laufen (andere Datei, keine offene Abhängigkeit)
- **[Story]**: Zugehörige User Story (US1/US2/US3)
- Exakte Dateipfade in jeder Beschreibung

## Path Conventions

Multi-Modul-Gradle-Projekt gemäß `plan.md`: `rpg-core/`, `rpg-persistence/`,
`rpg-platform/`, `rpg-content/`, `rpg-plugin/`, jeweils mit `src/main/java/...` und
`src/test/java/...`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Multi-Modul-Gradle-Projekt gemäß `plan.md`/`research.md` initialisieren

- [X] T001 Multi-Modul-Skeleton anlegen: `settings.gradle.kts` im Repository-Root mit den
      fünf Modulen `rpg-core`, `rpg-persistence`, `rpg-platform`, `rpg-content`,
      `rpg-plugin`
- [X] T002 Root `build.gradle.kts` und `gradle/libs.versions.toml` konfigurieren (Java
      25 Toolchain, gemeinsame Plugin-/Abhängigkeitsversionen)
- [X] T003 [P] `rpg-core/build.gradle.kts` konfigurieren: keine Paper/Bukkit-Abhängigkeit,
      nur JUnit 5 + AssertJ als Test-Dependencies
- [X] T004 [P] `rpg-platform/build.gradle.kts` konfigurieren: Paper-API-Abhängigkeit über
      `paperweight-userdev`, MockBukkit als Test-Dependency
- [X] T005 [P] `rpg-plugin/build.gradle.kts` konfigurieren: Abhängigkeit auf
      `rpg-platform`, `rpg-persistence`, `rpg-content`, `rpg-core` (nur in dieser
      Richtung, `plugin → platform → core`)
- [X] T006 [P] Leere Platzhalter-Module `rpg-persistence/build.gradle.kts` und
      `rpg-content/build.gradle.kts` anlegen (Grundlage für B02 bzw. B16)
- [X] T007 [P] Formatierung/Linting (z. B. Spotless) für alle Module konfigurieren

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Basis-Entitäten aus `data-model.md`, die alle drei User Stories benötigen

**⚠️ CRITICAL**: Keine User-Story-Arbeit beginnt, bevor diese Phase abgeschlossen ist

- [X] T008 [P] `Module`-Entität + Lifecycle-Enum (`INITIALIZING`, `ACTIVE`, `FAILED`,
      `STOPPING`, `STOPPED`) in `rpg-core/src/main/java/rpg/core/module/Module.java`
- [X] T009 [P] `ServiceRegistryEntry` in
      `rpg-core/src/main/java/rpg/core/module/ServiceRegistryEntry.java`
- [X] T010 [P] `ConfigSchema` + `FieldDefinition` in
      `rpg-core/src/main/java/rpg/core/config/ConfigSchema.java`
- [X] T011 [P] `Event`-Basistyp (payload, publishedByModuleId) in
      `rpg-core/src/main/java/rpg/core/event/Event.java`
- [X] T012 [P] `ScheduledTask` + `ExecutionMode`/`Binding`-Enums in
      `rpg-core/src/main/java/rpg/core/scheduler/ScheduledTask.java`
- [X] T013 [P] Fehlertypen `DuplicateModuleIdException`, `CyclicDependencyException`,
      `ServiceNotRegisteredException` in `rpg-core/src/main/java/rpg/core/module/` sowie
      `ConfigValidationException` in `rpg-core/src/main/java/rpg/core/config/`

**Checkpoint**: Foundation bereit — User-Story-Implementierung kann beginnen

---

## Phase 3: User Story 1 - Server startet zuverlässig oder bricht klar erkennbar ab (Priority: P1) 🎯 MVP

**Goal**: Deterministischer, nachvollziehbarer Bootstrap mit Fail-Fast-Konfigurations-
validierung, isolierter Fehlerbehandlung und begrenztem Shutdown (FR-001, FR-001a,
FR-002, FR-009 bis FR-013)

**Independent Test**: Server mit gültiger Konfiguration startet fehlerfrei innerhalb von
30s; Server mit fehlerhafter Konfiguration bricht mit klarer Meldung ab (siehe
`quickstart.md`, Abschnitte 2 und 5)

### Tests for User Story 1 ⚠️

> Tests zuerst schreiben, sicherstellen dass sie ohne Implementierung fehlschlagen

- [X] T014 [P] [US1] Unit-Test: deterministische Startreihenfolge (Kahn-Algorithmus,
      sekundär nach Modul-ID sortiert) in
      `rpg-core/src/test/java/rpg/core/module/ModuleRegistryStartOrderTest.java`
- [X] T015 [P] [US1] Unit-Test: zyklische Abhängigkeit löst `CyclicDependencyException`
      mit benannten Modul-IDs aus in
      `rpg-core/src/test/java/rpg/core/module/ModuleRegistryCycleTest.java`
- [X] T016 [P] [US1] Unit-Test: Fail-Fast bei ungültiger Konfiguration nennt Datei, Pfad
      und erwarteten Wert in
      `rpg-core/src/test/java/rpg/core/config/ConfigLoaderValidationTest.java`

### Implementation for User Story 1

- [X] T017 [US1] `ModuleRegistry.registerModule` + `resolveStartOrder` (Kahn-Algorithmus)
      in `rpg-core/src/main/java/rpg/core/module/ModuleRegistry.java` (benötigt T008,
      T013)
- [X] T018 [US1] `ConfigLoader`-Interface (`loadAndValidate`) in
      `rpg-core/src/main/java/rpg/core/config/ConfigLoader.java` (benötigt T010, T013)
- [X] T019 [US1] `YamlConfigLoader` (SnakeYAML-Implementierung) in
      `rpg-platform/src/main/java/rpg/platform/config/YamlConfigLoader.java` (benötigt
      T018)
- [X] T020 [US1] Strukturiertes Status-Logging je Modul bei Start/Reload/Shutdown in
      `rpg-core/src/main/java/rpg/core/module/ModuleLifecycleLogger.java` (benötigt T008)
- [X] T021 [US1] Bootstrap-Orchestrierung (`resolveStartOrder` → Modul-Init → Join-Block
      bis Bootstrap fertig) in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java`
      (`onEnable`) (benötigt T017, T019, T020)
- [X] T022 [US1] Join-Block-Listener (verweigert Sitzung vor abgeschlossenem Bootstrap) in
      `rpg-platform/src/main/java/rpg/platform/PreJoinGuard.java` (benötigt T021)
- [X] T023 [US1] Shutdown-Orchestrierung mit 10-Sekunden-Zeitlimit je Modul +
      Zwangsterminierung in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java`
      (`onDisable`) (benötigt T021)
- [X] T024 [US1] Fehlerisolation: Ausnahme in einem Modul wird lokal protokolliert, Tick
      und andere Module laufen weiter, in
      `rpg-core/src/main/java/rpg/core/module/ModuleFaultBarrier.java` (benötigt T008)

**Checkpoint**: User Story 1 ist vollständig funktionsfähig und unabhängig testbar

---

## Phase 4: User Story 2 - Neue Module entstehen ohne Kopplung an Interna anderer Module (Priority: P2)

**Goal**: Registry, Event-Bus und Scheduler-Abstraktion ermöglichen neue Module ohne
direkten Zugriff auf Interna anderer Module und ohne globalen Bukkit-Scheduler (FR-005
bis FR-008, FR-006a)

**Independent Test**: Ein Testmodul registriert einen Dienst, abonniert ein Ereignis und
plant eine location-gebundene Aufgabe, ohne Kenntnis der Implementierung eines anderen
Moduls (siehe `quickstart.md`, Abschnitte 1 und 4)

### Tests for User Story 2 ⚠️

- [X] T025 [P] [US2] Unit-Test: Service-Registrierung/-Auflösung ohne statische Kopplung
      in `rpg-core/src/test/java/rpg/core/module/ModuleRegistryServiceTest.java`
- [X] T026 [P] [US2] Unit-Test: doppelte Modul-ID löst `DuplicateModuleIdException` aus in
      `rpg-core/src/test/java/rpg/core/module/ModuleRegistryDuplicateTest.java`
- [X] T027 [P] [US2] Unit-Test: Event-Bus isoliert Abonnenten-Fehler, Zustellung an
      übrige Abonnenten läuft weiter, in
      `rpg-core/src/test/java/rpg/core/event/EventBusFaultIsolationTest.java`
- [X] T028 [P] [US2] MockBukkit-Test: Scheduler bietet nur location-/entity-gebundene
      Terminierung, `TaskHandle.cancel()` verhindert Ausführung, in
      `rpg-platform/src/test/java/rpg/platform/scheduler/PaperSchedulerAdapterTest.java`

### Implementation for User Story 2

- [X] T029 [US2] `ModuleRegistry.registerService`/`getService`/`findService` in
      `rpg-core/src/main/java/rpg/core/module/ModuleRegistry.java` (benötigt T009, T017)
- [X] T030 [P] [US2] `EventBus.publish`/`subscribe` mit isolierter Fehlerbehandlung
      (try/catch je Abonnent) in
      `rpg-core/src/main/java/rpg/core/event/EventBus.java` (benötigt T011)
- [X] T031 [P] [US2] `Scheduler`-Interface (`runSyncAtLocation`, `runSyncOnEntity`,
      `runAsync`, `TaskHandle`) in
      `rpg-core/src/main/java/rpg/core/scheduler/Scheduler.java` (benötigt T012)
- [X] T032 [US2] `PaperSchedulerAdapter` (bindet an Paper Region-/Entity-Scheduler, kein
      globaler Bukkit-Scheduler-Zugriff) in
      `rpg-platform/src/main/java/rpg/platform/scheduler/PaperSchedulerAdapter.java`
      (benötigt T031)
- [X] T033 [US2] Nachweis (Test/statische Prüfung), dass kein direkter
      `Bukkit.getScheduler()`-Aufruf außerhalb von `PaperSchedulerAdapter` existiert, in
      `rpg-platform/src/test/java/rpg/platform/scheduler/NoGlobalSchedulerAccessTest.java`
      (benötigt T032)

**Checkpoint**: User Story 1 UND 2 funktionieren beide unabhängig

---

## Phase 5: User Story 3 - Konfiguration im laufenden Betrieb ändern (Priority: P3)

**Goal**: Globaler Hot-Reload ohne Serverneustart, mit Rollback bei fehlerhafter neuer
Konfiguration (FR-003, FR-004)

**Independent Test**: Konfigurationswert bei laufendem Server ändern und Reload auslösen
→ neuer Wert wirkt für alle Module; fehlerhafter Reload behält alte Konfiguration (siehe
`quickstart.md`, Abschnitt 3)

### Tests for User Story 3 ⚠️

- [X] T034 [P] [US3] Unit-Test: globaler Reload lädt Konfiguration aller Module gleichzeitig
      neu in `rpg-core/src/test/java/rpg/core/config/ConfigLoaderReloadTest.java`
- [X] T035 [P] [US3] Unit-Test: fehlgeschlagener Reload behält die zuvor gültige
      Konfiguration in
      `rpg-core/src/test/java/rpg/core/config/ConfigLoaderReloadFailureTest.java`

### Implementation for User Story 3

- [X] T036 [US3] `ConfigLoader.reloadAll()` (global, alle Module gleichzeitig) in
      `rpg-core/src/main/java/rpg/core/config/ConfigLoader.java` (benötigt T018, T019)
- [X] T037 [US3] Rollback auf vorherige gültige Konfiguration bei Reload-Fehler in
      `rpg-platform/src/main/java/rpg/platform/config/YamlConfigLoader.java` (benötigt
      T036)
- [X] T038 [US3] Interner Reload-Einstiegspunkt (später von B14-Commands aufrufbar) in
      `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` (benötigt T036)

**Checkpoint**: Alle drei User Stories sind unabhängig funktionsfähig

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T039 [P] Vollständige `quickstart.md`-Validierung durchführen (alle 6 Abschnitte)
- [X] T040 [P] Javadoc für die öffentlichen `rpg-core`-Schnittstellen (`ModuleRegistry`,
      `EventBus`, `Scheduler`, `ConfigLoader`) gemäß `contracts/`
- [X] T041 Performance-Nachweis: Bootstrap ≤ 30s, Shutdown je Modul ≤ 10s in einer
      Testumgebung (SC-001, SC-007)
- [X] T042 Statische Prüfung: kein direkter, nicht ortsgebundener globaler
      Scheduling-Aufruf im gesamten Projekt (SC-005)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: keine Abhängigkeiten — kann sofort starten
- **Foundational (Phase 2)**: hängt von Setup ab — blockiert alle User Stories
- **User Stories (Phase 3–5)**: hängen alle von Foundational ab; danach parallel oder in
  Prioritätsreihenfolge (P1 → P2 → P3) möglich
- **Polish (Phase 6)**: hängt von den gewünschten User Stories ab

### User Story Dependencies

- **User Story 1 (P1)**: startet nach Foundational — keine Abhängigkeit zu US2/US3
- **User Story 2 (P2)**: startet nach Foundational — nutzt `ModuleRegistry` aus US1
  (T017), bleibt aber unabhängig testbar (eigene Tests, eigener Verantwortungsbereich)
- **User Story 3 (P3)**: startet nach Foundational — baut auf `ConfigLoader` aus US1
  (T018/T019) auf, bleibt aber unabhängig testbar

### Within Each User Story

- Tests zuerst, müssen ohne Implementierung fehlschlagen
- Entitäten vor Services, Services vor Orchestrierung
- Story-Checkpoint erreicht, bevor die nächste Priorität begonnen wird

### Parallel Opportunities

- Alle mit [P] markierten Setup-Aufgaben (T003–T007)
- Alle mit [P] markierten Foundational-Aufgaben (T008–T013)
- Innerhalb jeder Story: alle mit [P] markierten Tests parallel, danach die mit [P]
  markierten Implementierungsaufgaben

---

## Parallel Example: User Story 1

```bash
# Tests für User Story 1 parallel starten:
Task: "Unit-Test deterministische Startreihenfolge in ModuleRegistryStartOrderTest.java"
Task: "Unit-Test zyklische Abhängigkeit in ModuleRegistryCycleTest.java"
Task: "Unit-Test Fail-Fast-Konfigurationsvalidierung in ConfigLoaderValidationTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 (Setup) abschließen
2. Phase 2 (Foundational) abschließen — kritisch, blockiert alle Stories
3. Phase 3 (User Story 1) abschließen
4. **Stoppen und validieren**: `quickstart.md` Abschnitte 2 und 5 durchlaufen
5. Ein Server mit B01 alleine startet, validiert Config Fail-Fast und terminiert
   kontrolliert — das ist die MVP-Grundlage für alle weiteren Blöcke (B02–B17)

### Incremental Delivery

1. Setup + Foundational → Fundament bereit
2. User Story 1 → unabhängig testen → zuverlässiger Server-Bootstrap steht
3. User Story 2 → unabhängig testen → Registry/Event-Bus/Scheduler für B02–B17 nutzbar
4. User Story 3 → unabhängig testen → Hot-Reload für Betrieb verfügbar

---

## Notes

- [P] = andere Datei, keine offene Abhängigkeit
- [Story]-Label ordnet jede Aufgabe einer User Story zur Nachverfolgbarkeit zu
- Tests zuerst schreiben und fehlschlagen lassen, dann implementieren
- Nach jeder Story-Checkpoint-Erreichung: `quickstart.md` für die betroffenen Abschnitte
  gegenprüfen
