---
description: "Task list for B02 · Persistenz-Layer"
---

# Tasks: B02 · Persistenz-Layer

**Input**: Design documents from `/specs/002-persistence-layer/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md
(alle vorhanden). B01 ist fertiggestellt und auf einem echten Server verifiziert.

**Tests**: Enthalten und nicht optional. Constitution VII verlangt Unit-Tests ohne laufenden
Server für jede Regel der Domänenschicht **und** Persistenztests gegen eine echte
PostgreSQL-Instanz statt gegen Ersatzobjekte.

**Organization**: Aufgaben sind nach User Story aus `spec.md` gruppiert. Besonderheit gegenüber
B01: **zwei P1-Stories**. US1 (Fortschritt überlebt jeden Neustart) und US2 (Datenhaltung belastet
den Spielablauf nicht) sind gleichrangig — eine Persistenz, die nichts verliert, aber den Tick
blockiert, ist als Zwischenstand wertlos. Der MVP umfasst deshalb beide.

**Überarbeitet nach `/speckit-analyze` (2026-08-19)**: Message-Schlüssel-Ablage ergänzt
(Constitution V, war der einzige kritische Befund), User Story 5 aus der Polish-Phase
herausgelöst, vier Abdeckungslücken geschlossen. Details am Ende unter „Änderungen aus der
Analyse".

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Kann parallel laufen (andere Datei, keine offene Abhängigkeit)
- **[Story]**: Zugehörige User Story (US1–US5)
- Exakte Dateipfade in jeder Beschreibung

## Path Conventions

Multi-Modul-Gradle-Projekt aus B01: `rpg-core/`, `rpg-persistence/`, `rpg-platform/`,
`rpg-content/`, `rpg-plugin/`, jeweils mit `src/main/java/...` und `src/test/java/...`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Das in B01 leere Platzhalter-Modul `rpg-persistence` arbeitsfähig machen

- [X] T001 `rpg-persistence/build.gradle.kts` konfigurieren: PostgreSQL-Treiber 42.7.13,
      HikariCP 7.1.0 und Flyway 13.3.0 als `compileOnly` (zur Laufzeit über Papers `libraries:`),
      dieselben Artefakte zusätzlich als `testImplementation`
- [X] T002 [P] Testcontainers 1.21.4 (`org.testcontainers:postgresql`, **ohne**
      `junit-jupiter`) als `testImplementation` in `rpg-persistence/build.gradle.kts` ergänzen
- [X] T003 [P] Versionen für Treiber, HikariCP, Flyway und Testcontainers in
      `gradle/libs.versions.toml` eintragen
- [X] T004 `libraries:`-Abschnitt in `rpg-plugin/src/main/resources/plugin.yml` mit Treiber,
      HikariCP und Flyway ergänzen (ADR-010)
- [X] T005 [P] Nachweis-Test, dass das gebaute Plugin-Jar weiterhin **keine** Fremdklasse enthält,
      in `rpg-plugin/src/test/java/rpg/plugin/JarContainsNoThirdPartyClassesTest.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Alles, was **beide** P1-Stories brauchen — Scheduler-Erweiterung,
Message-Schlüssel-Ablage, Write-Behind-Kern, Schema und Testfundament

**⚠️ CRITICAL**: Keine User-Story-Arbeit beginnt, bevor diese Phase abgeschlossen ist

### Erweiterung von B01: Scheduler (siehe ADR-010)

- [X] T006 `runAsyncDelayed(Duration, Runnable)` zur Schnittstelle
      `rpg-core/src/main/java/rpg/core/scheduler/Scheduler.java` ergänzen — additiv, **kein**
      `runRepeating`
- [X] T007 `runAsyncDelayed` in
      `rpg-platform/src/main/java/rpg/platform/scheduler/PaperSchedulerAdapter.java` über
      `AsyncScheduler.runDelayed` implementieren (benötigt T006)
- [X] T008 MockBukkit-Test für `runAsyncDelayed` inklusive `cancel()` vor Ablauf in
      `rpg-platform/src/test/java/rpg/platform/scheduler/PaperSchedulerAdapterTest.java`
      (benötigt T007) — dabei prüfen, dass der Test **nicht** als übersprungen endet
- [X] T009 ADR-010 in `02-decisions.md` gegen die tatsächliche Umsetzung von T006–T008 prüfen,
      bei Abweichung nachziehen und die Kopie unter
      `minecraft-rpg-spec/minecraft-rpg-spec/docs/02-decisions.md` angleichen (benötigt T008)

### Erweiterung von B01: Message-Schlüssel (FR-023, Constitution V)

- [X] T010 [P] `MessageKey` als typisierter Schlüssel (gepunkteter Bezeichner, nicht leer) in
      `rpg-core/src/main/java/rpg/core/message/MessageKey.java`
- [X] T011 `Messages`-Schnittstelle mit `String get(MessageKey)` und
      `String get(MessageKey, Map<String,String> placeholders)` in
      `rpg-core/src/main/java/rpg/core/message/Messages.java` (benötigt T010) — Rückgabetyp ist
      bewusst `String` und **kein** Adventure-`Component`, weil `rpg-core` bukkitfrei bleibt; die
      Umwandlung in einen `Component` geschieht erst in `rpg-platform`
- [X] T012 `MapMessages` (Auflösung über eine geladene Schlüssel-Text-Ablage, Platzhalterersetzung)
      in `rpg-core/src/main/java/rpg/core/message/MapMessages.java` (benötigt T011)
- [X] T013 Startvalidierung: alle im Code deklarierten Schlüssel müssen hinterlegt sein, sonst
      Abbruch mit Nennung des fehlenden Schlüssels (FR-023a), in
      `rpg-core/src/main/java/rpg/core/message/MessageKeyValidator.java` (benötigt T012)
- [X] T014 [P] Unit-Test: fehlender Schlüssel bricht den Start ab und nennt ihn; Platzhalter
      werden ersetzt, in `rpg-core/src/test/java/rpg/core/message/MapMessagesTest.java`
      (benötigt T013)
- [X] T015 `messages.yml` mit den Texten aus B01 und B02 in
      `rpg-plugin/src/main/resources/messages.yml`, geladen über B01s `YamlConfigLoader` und in
      `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` verdrahtet (benötigt T012)
- [X] T016 Die drei hartcodierten Texte in
      `rpg-platform/src/main/java/rpg/platform/PreJoinGuard.java` auf Message-Schlüssel umstellen
      (behebt den Constitution-V-Verstoß aus B01) (benötigt T015)

### Basistypen der Domänenschicht (`rpg-core`)

- [X] T017 [P] `AggregateType`-Enum (`PLAYER_STATE`, `STATISTICS`, `ITEM_INSTANCE`, `AUDIT_LOG`)
      in `rpg-core/src/main/java/rpg/core/persistence/AggregateType.java`
- [X] T018 [P] `DirtyMark` (aggregateType, aggregateId, markedAt) in
      `rpg-core/src/main/java/rpg/core/persistence/DirtyMark.java`
- [X] T019 [P] Fehlertypen `PersistenceException`, `StaleVersionException`,
      `PersistenceStartupException` in `rpg-core/src/main/java/rpg/core/persistence/`
- [X] T020 [P] `FlushReason`-Enum, `FlushResult` und `BufferStatus` gemäß
      `contracts/write-behind.md` in `rpg-core/src/main/java/rpg/core/persistence/`
- [X] T021 [P] `PersistenceConfig` mit allen Schlüsseln aus `contracts/schema-migration.md` und
      dem harten Deckel von 8 s auf `shutdown-flush-seconds` in
      `rpg-core/src/main/java/rpg/core/persistence/PersistenceConfig.java`
- [X] T022 [P] Unit-Test Konfigurationsvalidierung: `shutdown-flush-seconds: 20` und
      `autosave-seconds: 5` brechen den Start mit klarer Meldung ab (FR-022), in
      `rpg-core/src/test/java/rpg/core/persistence/PersistenceConfigValidationTest.java`
      (benötigt T021)
- [X] T023 `WriteBehindBuffer` mit Koaleszieren je (Typ, Kennung), Kapazität und Warnschwelle in
      `rpg-core/src/main/java/rpg/core/persistence/WriteBehindBuffer.java` (benötigt T017, T018)
- [X] T024 `Repository`-Basisschnittstelle (`load`, `markDirty`) gemäß `contracts/repository.md`
      in `rpg-core/src/main/java/rpg/core/persistence/Repository.java` (benötigt T017)

### Datenbankfundament (`rpg-persistence`)

- [X] T025 Basismigration mit den vier Tabellen aus `data-model.md` in
      `rpg-persistence/src/main/resources/db/migration/V1__baseline.sql`
- [X] T026 Singleton-Testcontainer (ein `postgres:18-alpine` für die gesamte Suite, **ohne**
      `@Testcontainers`-Erweiterung) in
      `rpg-persistence/src/test/java/rpg/persistence/support/PostgresContainer.java`
      (benötigt T002)
- [X] T027 `ConnectionPools` mit **getrenntem** Schreib- und Login-Pool in
      `rpg-persistence/src/main/java/rpg/persistence/ConnectionPools.java` (benötigt T021)
- [X] T028 `PersistenceModule` als Modul nach B01-Vertrag (Registrierung der Repositories,
      Fail-Fast bei nicht erreichbarer Datenbank) in
      `rpg-persistence/src/main/java/rpg/persistence/PersistenceModule.java` (benötigt T027)
- [X] T029 `PersistenceModule` in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` in
      `modules()` eintragen — die bisher leere Liste (benötigt T028)

**Checkpoint**: Foundation bereit — beide P1-Stories können beginnen

---

## Phase 3: User Story 1 - Spielerfortschritt überlebt jeden Serverneustart (Priority: P1) 🎯 MVP

**Goal**: Laden beim Verbinden, Schreiben bei Sitzungsende und Shutdown, kein Verlust über das
Autosave-Intervall hinaus, kein Überschreiben durch eine Vorsitzung (FR-004 bis FR-007, FR-011,
FR-011a, FR-015, FR-019, FR-019a, FR-019b, FR-019c, FR-020, FR-021)

**Independent Test**: Zustand ändern, Server neu starten, Zustand vergleichen — einmal nach
sauberem Shutdown, einmal nach hartem Prozessabbruch (`quickstart.md`, Abschnitte 5 und 7)

### Tests for User Story 1 ⚠️

> Tests zuerst schreiben, sicherstellen dass sie ohne Implementierung fehlschlagen

- [X] T030 [P] [US1] Unit-Test: Versionsvergleich lehnt veralteten Schreibvorgang ab in
      `rpg-core/src/test/java/rpg/core/persistence/StaleVersionTest.java`
- [X] T031 [P] [US1] Integrationstest: Laden und Schreiben eines Spielerzustands über einen
      Neustart hinweg in
      `rpg-persistence/src/test/java/rpg/persistence/PlayerStateRoundTripTest.java`
- [X] T032 [P] [US1] Integrationstest **harter Prozessabbruch** (SC-001): Aggregate vormerken,
      den Puffer ohne Flush verwerfen, neu laden — der zuletzt geschriebene Stand ist vollständig
      vorhanden und der Verlust überschreitet ein Autosave-Intervall nicht, in
      `rpg-persistence/src/test/java/rpg/persistence/HardAbortRecoveryTest.java`
- [X] T033 [P] [US1] Integrationstest: Sitzungsende schreibt sofort, unabhängig vom Intervall, in
      `rpg-persistence/src/test/java/rpg/persistence/SessionEndFlushTest.java`
- [X] T034 [P] [US1] Integrationstest: Shutdown-Flush endet innerhalb von 8 s; bei angehaltener
      Datenbank Abbruch mit protokolliertem Ergebnis, in
      `rpg-persistence/src/test/java/rpg/persistence/ShutdownFlushTest.java`
- [X] T035 [P] [US1] Integrationstest: neue Sitzung wartet auf den Flush der Vorsitzung und
      erhält den aktuellen Stand; Fristüberschreitung führt zur Ablehnung, in
      `rpg-persistence/src/test/java/rpg/persistence/SessionHandoverTest.java`
- [X] T036 [P] [US1] Integrationstest: ein nicht lesbarer Datensatz wird lokal begrenzt behandelt
      und reißt weder Server noch andere Spieler mit, in
      `rpg-persistence/src/test/java/rpg/persistence/CorruptRecordTest.java`
- [X] T037 [P] [US1] Integrationstest: Item-Instanz speichert Vorlagenkennung und gewürfelte
      Werte und liest sie unverändert zurück; berechnete Endwerte tauchen nirgends auf (ADR-004),
      in `rpg-persistence/src/test/java/rpg/persistence/ItemInstanceRoundTripTest.java`

### Implementation for User Story 1

- [X] T038 [US1] `PlayerStateRepository`-Schnittstelle inklusive `awaitPendingWrites` gemäß
      `contracts/repository.md` in
      `rpg-core/src/main/java/rpg/core/persistence/PlayerStateRepository.java` (benötigt T024)
- [X] T039 [P] [US1] `ItemInstanceRepository`-Schnittstelle in
      `rpg-core/src/main/java/rpg/core/persistence/ItemInstanceRepository.java` (benötigt T024)
- [X] T040 [US1] `WriteBehindCoordinator`-Schnittstelle gemäß `contracts/write-behind.md` in
      `rpg-core/src/main/java/rpg/core/persistence/WriteBehindCoordinator.java` (benötigt T020,
      T023)
- [X] T041 [US1] `JdbcPlayerStateRepository` mit `INSERT ... ON CONFLICT` und Versionsprüfung in
      `rpg-persistence/src/main/java/rpg/persistence/jdbc/JdbcPlayerStateRepository.java`
      (benötigt T038)
- [X] T042 [P] [US1] `JdbcItemInstanceRepository` — speichert Vorlagenkennung und gewürfelte
      Werte, **nie** berechnete Endwerte (ADR-004), in
      `rpg-persistence/src/main/java/rpg/persistence/jdbc/JdbcItemInstanceRepository.java`
      (benötigt T039)
- [X] T043 [US1] MapStruct-Mapper Zeile ↔ Domänenobjekt in
      `rpg-persistence/src/main/java/rpg/persistence/jdbc/PlayerStateRowMapper.java` — über die
      erzeugte `*Impl`-Klasse instanziiert, **nie** über `Mappers.getMapper` (benötigt T041)
- [X] T044 [US1] `FlushCycle` mit den Auslösern `SESSION_END` und `SHUTDOWN`, Momentaufnahme der
      Vormerkungen und 8-Sekunden-Frist in
      `rpg-persistence/src/main/java/rpg/persistence/FlushCycle.java` (benötigt T040)
- [X] T045 [US1] Sitzungsübergabe: Ladevorgang stellt zurück, bis der Flush der Vorsitzung fertig
      ist, mit Obergrenze und Ablehnung danach, in
      `rpg-persistence/src/main/java/rpg/persistence/SessionHandover.java` (benötigt T041, T044)
- [X] T046 [US1] Shutdown-Anbindung: `PersistenceModule.stop()` löst den Flush aus und meldet
      sich innerhalb des B01-Modulbudgets ab (FR-011a), in
      `rpg-persistence/src/main/java/rpg/persistence/PersistenceModule.java` (benötigt T044)
- [X] T047 [US1] Behandlung nicht lesbarer Datensätze: protokollieren, lokal begrenzen, Anmeldung
      ablehnen statt Standardzustand, in
      `rpg-persistence/src/main/java/rpg/persistence/jdbc/JdbcPlayerStateRepository.java`
      (benötigt T041)

**Checkpoint**: Fortschritt überlebt Neustart und Absturz — unabhängig testbar

---

## Phase 4: User Story 2 - Datenhaltung belastet den Spielablauf nicht (Priority: P1) 🎯 MVP

**Goal**: Kein Datenbankzugriff je Spielereignis, gesammeltes Schreiben ohne Tick-Blockade,
Anmeldepfad wartet nie auf eine Verbindung (FR-001, FR-002, FR-003, FR-008, FR-016, FR-016a,
FR-016b, FR-016c)

**Independent Test**: Lasttest mit 200 simulierten Sitzungen; Tick-Zeiten und Wartezeit des
Login-Pools messen (`quickstart.md`, Abschnitte 3 und 8)

### Tests for User Story 2 ⚠️

- [X] T048 [P] [US2] Unit-Test: mehrfaches `markDirty` derselben Kennung erzeugt **eine**
      Vormerkung, in
      `rpg-core/src/test/java/rpg/core/persistence/WriteBehindBufferCoalescingTest.java`
- [X] T049 [P] [US2] Unit-Test: der Puffer wächst mit der Zahl **unterschiedlicher** Aggregate,
      nicht mit der Zahl der Änderungen — Nachweis der Kernannahme aus `research.md`, in
      `rpg-core/src/test/java/rpg/core/persistence/WriteBehindBufferGrowthTest.java`
- [X] T050 [P] [US2] Integrationstest: 1000 Änderungen an einem Aggregat erzeugen **einen**
      Schreibvorgang, gemessen an der Zahl ausgeführter Statements, in
      `rpg-persistence/src/test/java/rpg/persistence/BatchingTest.java`
- [X] T051 [P] [US2] Integrationstest: ein während des laufenden Batches erneut verändertes
      Aggregat ist im **nächsten** Durchlauf enthalten, in
      `rpg-persistence/src/test/java/rpg/persistence/ConcurrentModificationDuringFlushTest.java`
- [X] T052 [P] [US2] Integrationstest: Tagesstatistik wird per `ON CONFLICT` hochgezählt, ohne
      vorher zu lesen; Tageswechsel legt einen neuen Datensatz an, in
      `rpg-persistence/src/test/java/rpg/persistence/StatisticsUpsertTest.java`
- [X] T053 [P] [US2] Integrationstest Zeitraumsumme (FR-016b): Werte über mehrere Tage schreiben,
      Teilzeitraum- und Allzeitsumme prüfen — die Zusage an B12, in
      `rpg-persistence/src/test/java/rpg/persistence/StatisticsSumTest.java`
- [X] T054 [P] [US2] Integrationstest: unter Schreiblast wartet der Login-Pool nie — Nachweis
      über die Wartezeitmessung des Pools, in
      `rpg-persistence/src/test/java/rpg/persistence/LoginPoolIsolationTest.java`

### Implementation for User Story 2

- [X] T055 [US2] `DirtyTracker` mit allokationsarmem `markDirty` für den Tick-Pfad in
      `rpg-core/src/main/java/rpg/core/persistence/DirtyTracker.java` (benötigt T023)
- [X] T056 [P] [US2] `StatisticsRepository`-Schnittstelle (`increment`, `sum`) gemäß
      `contracts/repository.md` in
      `rpg-core/src/main/java/rpg/core/persistence/StatisticsRepository.java`
- [X] T057 [US2] `JdbcStatisticsRepository` mit
      `ON CONFLICT DO UPDATE SET value = value + excluded.value` und Zeitraumsumme über
      Tageswerte in
      `rpg-persistence/src/main/java/rpg/persistence/jdbc/JdbcStatisticsRepository.java`
      (benötigt T056)
- [X] T058 [US2] `INTERVAL`-Auslöser: Flush plant sich über `runAsyncDelayed` selbst neu, genau
      ein laufender Flush gleichzeitig, in
      `rpg-persistence/src/main/java/rpg/persistence/FlushCycle.java` (benötigt T006, T044)
- [X] T059 [US2] Batch-Schreiben je Aggregattyp mit Prepared Statements in
      `rpg-persistence/src/main/java/rpg/persistence/jdbc/BatchWriter.java` (benötigt T057)
- [X] T060 [US2] Login-Pfad ausschließlich gegen `loginPool()`, Schreibpfad ausschließlich gegen
      `writePool()` in `rpg-persistence/src/main/java/rpg/persistence/ConnectionPools.java`
      (benötigt T027)
- [X] T061 [US2] Statische Prüfung, dass außerhalb von `rpg-persistence` keine `DataSource` und
      keine `java.sql`-Klasse verwendet wird und dass kein `DELETE` auf
      `player_statistic_daily` existiert (FR-017), in
      `rpg-persistence/src/test/java/rpg/persistence/NoDirectDatabaseAccessTest.java` — nach dem
      Muster von `NoGlobalSchedulerAccessTest` aus B01, inklusive Selbstprüfung, dass der Scan
      Dateien erreicht hat

**Checkpoint**: MVP vollständig — Fortschritt ist sicher **und** der Server bleibt flüssig

---

## Phase 5: User Story 3 - Ein Datenbankausfall kostet keinen Fortschritt (Priority: P2)

**Goal**: Puffern bei Ausfall, Nachschreiben nach Rückkehr, Anmeldung ablehnen statt
Standardzustand, Notbremse bei Erreichen der Puffergrenze (FR-005a, FR-005b, FR-009, FR-009a,
FR-009b, FR-009c, FR-010)

**Independent Test**: Container im laufenden Betrieb 60 s anhalten und wieder starten
(`quickstart.md`, Abschnitt 4)

### Tests for User Story 3 ⚠️

- [X] T062 [P] [US3] Unit-Test: Warnschwelle bei 80 % löst **einmalig** aus, nicht bei jedem
      Durchlauf, in
      `rpg-core/src/test/java/rpg/core/persistence/BufferWarnThresholdTest.java`
- [X] T063 [P] [US3] Unit-Test: Erreichen der Kapazität setzt `overCapacity`, ohne Vormerkungen
      zu verwerfen, in
      `rpg-core/src/test/java/rpg/core/persistence/BufferOverCapacityTest.java`
- [X] T064 [P] [US3] Integrationstest mit echtem Container-Stopp: Vormerkungen überleben den
      Ausfall und werden danach vollständig nachgeschrieben, in
      `rpg-persistence/src/test/java/rpg/persistence/OutageRecoveryTest.java`
- [X] T065 [P] [US3] Integrationstest: Anmeldung während des Ausfalls wird abgelehnt und liefert
      **keinen** Standardzustand, in
      `rpg-persistence/src/test/java/rpg/persistence/LoginDuringOutageTest.java`

### Implementation for User Story 3

- [X] T066 [US3] `OutageState` mit Übergang zu `reachable` erst nach einem **erfolgreichen**
      Schreibvorgang in `rpg-persistence/src/main/java/rpg/persistence/OutageState.java`
      (benötigt T044)
- [X] T067 [US3] Fehlgeschlagener Batch behält alle seine Vormerkungen; `flushNow` schlägt nie
      nach außen fehl, in `rpg-persistence/src/main/java/rpg/persistence/FlushCycle.java`
      (benötigt T066)
- [X] T068 [US3] `RECOVERY`-Auslöser mit wachsendem Wiederholabstand in
      `rpg-persistence/src/main/java/rpg/persistence/FlushCycle.java` (benötigt T066)
- [X] T069 [US3] Anmeldung ablehnen, solange die Datenhaltung nicht erreichbar ist — bereits
      verbundene Spieler bleiben unangetastet, in
      `rpg-persistence/src/main/java/rpg/persistence/PersistenceModule.java` (benötigt T066)
- [X] T070 [US3] Notbremse bei Erreichen der Puffergrenze: alle Spieler trennen, neue Sitzungen
      ablehnen, Vormerkungen behalten, in
      `rpg-persistence/src/main/java/rpg/persistence/OutageState.java` (benötigt T063, T066)
- [X] T071 [US3] Meldungstexte für Ablehnung und Trennung als Message-Schlüssel deklarieren und
      in `messages.yml` hinterlegen (FR-023), in
      `rpg-persistence/src/main/java/rpg/persistence/PersistenceMessageKeys.java` und
      `rpg-plugin/src/main/resources/messages.yml` (benötigt T015, T069, T070)

**Checkpoint**: US1, US2 und US3 funktionieren unabhängig

---

## Phase 6: User Story 4 - Schemaänderungen sind nachvollziehbar und wiederholbar (Priority: P3)

**Goal**: Migrationen laufen auf leerer wie befüllter Datenbank, niemals doppelt, und brechen den
Start bei Fehlern ab (FR-012, FR-013, FR-014, FR-022)

**Independent Test**: Migrationen gegen leere und befüllte Datenbank laufen lassen
(`quickstart.md`, Abschnitt 2)

### Tests for User Story 4 ⚠️

- [X] T072 [P] [US4] Integrationstest: leere Datenbank erhält das vollständige Schema; ein
      zweiter Lauf wendet **null** Migrationen an, in
      `rpg-persistence/src/test/java/rpg/persistence/MigrationIdempotencyTest.java`
- [X] T073 [P] [US4] Integrationstest: befüllte Datenbank behält ihre Bestandsdaten unverändert —
      Zeilenvergleich vor und nach der Migration, in
      `rpg-persistence/src/test/java/rpg/persistence/MigrationPreservesDataTest.java`
- [X] T074 [P] [US4] Integrationstest: nachträglich veränderte Migrationsdatei bricht den Start
      mit Prüfsummenfehler ab, in
      `rpg-persistence/src/test/java/rpg/persistence/MigrationChecksumTest.java`

### Implementation for User Story 4

- [X] T075 [US4] `SchemaMigrator` gemäß `contracts/schema-migration.md` in
      `rpg-persistence/src/main/java/rpg/persistence/SchemaMigrator.java` (benötigt T025, T027)
- [X] T076 [US4] Fail-Fast beim Start: nicht erreichbare Datenbank oder fehlgeschlagene Migration
      bricht den Bootstrap ab; die Meldung nennt Wirt, Port und Datenbank, **nie** das Passwort,
      in `rpg-persistence/src/main/java/rpg/persistence/PersistenceModule.java` (benötigt T075)

**Checkpoint**: Alle Persistenzmechanik steht

---

## Phase 7: User Story 5 - Daten lassen sich vom Personenbezug lösen (Priority: P3)

**Goal**: Anonymisierung in einer Transaktion, unumkehrbar, unter Erhalt der Aggregate; jeder
administrative Eingriff nachvollziehbar (FR-017, FR-017a, FR-017b, FR-017c, FR-018)

**Independent Test**: Spieler mit Zustand, Statistiken und Item-Instanzen anlegen, anonymisieren,
alle Tabellen auf die ursprüngliche Kennung durchsuchen und Allzeit-Summen vergleichen
(`quickstart.md`, Abschnitt 6)

### Tests for User Story 5 ⚠️

- [X] T077 [P] [US5] Integrationstest Anonymisierung: keine Tabelle enthält danach die
      ursprüngliche Kennung, Allzeit-Summen bleiben identisch, ein Fehler mittendrin hinterlässt
      keinen Teilzustand, in
      `rpg-persistence/src/test/java/rpg/persistence/AnonymizationTest.java`
- [X] T078 [P] [US5] Unit-Test: das Ersatzkennzeichen ist zufällig und **nicht** aus der
      ursprünglichen Kennung ableitbar (kein Hash), in
      `rpg-core/src/test/java/rpg/core/persistence/AnonymizedIdTest.java`
- [X] T079 [P] [US5] Integrationstest Prüfprotokoll: Einträge lassen sich anhängen, aber nicht
      ändern oder löschen; die Anonymisierung ist selbst protokolliert, jedoch ohne die
      anonymisierte Kennung, in
      `rpg-persistence/src/test/java/rpg/persistence/AuditLogTest.java`

### Implementation for User Story 5

- [X] T080 [US5] `AnonymizedId` (zufällig erzeugt, keine gespeicherte Zuordnung zur alten
      Kennung) in `rpg-core/src/main/java/rpg/core/persistence/AnonymizedId.java`
- [X] T081 [P] [US5] `AuditLogRepository`-Schnittstelle (`append`, kein Ändern, kein Löschen) in
      `rpg-core/src/main/java/rpg/core/persistence/AuditLogRepository.java` (benötigt T024)
- [X] T082 [US5] `JdbcAuditLogRepository` in
      `rpg-persistence/src/main/java/rpg/persistence/jdbc/JdbcAuditLogRepository.java`
      (benötigt T081)
- [X] T083 [US5] `anonymize` in **einer** Transaktion: Zustand löschen, Kennungen in Statistik und
      Prüfprotokoll ersetzen, Vorgang protokollieren, in
      `rpg-persistence/src/main/java/rpg/persistence/jdbc/JdbcPlayerStateRepository.java`
      (benötigt T080, T082)

**Checkpoint**: Alle fünf User Stories sind unabhängig funktionsfähig

---

## Phase 8: Polish & Cross-Cutting Concerns

> **Stand 2026-08-19**: T084 ist erledigt. T085–T087 bleiben offen, weil sie einen **laufenden
> Paper-Server mit simulierten Spielersitzungen** brauchen — nicht wegen fehlender Implementierung.
> Was ohne Server nachweisbar war, ist nachgewiesen: `BatchingTest` misst am Scheduler, dass
> Spielereignisse **keine** asynchrone Arbeit und damit keinen Datenbankzugriff auslösen (die
> Mechanik hinter SC-005), und `ShutdownFlushTest` belegt das 8-Sekunden-Budget gegen eine echte
> Datenbank. Offen bleibt die Messung unter echter Last.

- [X] T084 [P] Javadoc für die öffentlichen `rpg-core/persistence`- und
      `rpg-core/message`-Schnittstellen gemäß `contracts/`
- [ ] T085 [P] Vollständige `quickstart.md`-Validierung durchführen (alle 8 Abschnitte)
- [ ] T086 Lasttest mit 200 simulierten Sitzungen **einschließlich eines Ausfalls unter Last**:
      Tick-Budget ≤ 5 ms für B02, Wartezeit des Login-Pools durchgehend null, keine messbare
      Verschlechterung der Tickrate während des Ausfalls (SC-003, SC-004)
- [ ] T087 Nachweis, dass kein Spielereignis einen Datenbankzugriff erzeugt — Zugriffe je
      Zeiteinheit gegen die Zahl der Spielereignisse (SC-005)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: keine Abhängigkeiten — kann sofort starten
- **Foundational (Phase 2)**: hängt von Setup ab — blockiert alle User Stories. Enthält mit
  T006–T009 die Scheduler-Erweiterung von B01 und mit T010–T016 die Message-Schlüssel-Ablage,
  ohne die weder US2 noch US3 umsetzbar sind
- **User Stories (Phase 3–7)**: hängen alle von Foundational ab
- **Polish (Phase 8)**: hängt von den gewünschten User Stories ab

### User Story Dependencies

- **US1 (P1)** und **US2 (P1)**: beide starten nach Foundational. Sie teilen sich den
  Write-Behind-Kern, der deshalb bewusst in der Foundational-Phase liegt — danach sind sie
  unabhängig voneinander umsetzbar und testbar
- **US3 (P2)**: startet nach Foundational, baut auf dem `FlushCycle` aus US1/US2 auf, bleibt aber
  eigenständig testbar
- **US4 (P3)**: startet nach Foundational. Die Basismigration liegt in Foundational (ohne Tabellen
  ist nichts testbar); US4 liefert die **Mechanik** — Idempotenz, Prüfsumme, Abbruchverhalten
- **US5 (P3)**: startet nach US1, weil die Anonymisierung auf `JdbcPlayerStateRepository`
  aufsetzt; unabhängig von US2, US3 und US4

### Within Each User Story

- Tests zuerst, müssen ohne Implementierung fehlschlagen
- Schnittstellen vor Implementierungen, Implementierungen vor Verdrahtung
- Story-Checkpoint erreicht, bevor die nächste Priorität begonnen wird

### Parallel Opportunities

- Setup: T002, T003, T005
- Foundational: T010 und T017–T022 (verschiedene Dateien)
- Innerhalb jeder Story: alle mit [P] markierten Tests parallel
- Polish: T084, T085

---

## Parallel Example: User Story 2

```bash
# Tests für User Story 2 parallel starten:
Task: "Unit-Test Koaleszieren in WriteBehindBufferCoalescingTest.java"
Task: "Unit-Test Pufferwachstum in WriteBehindBufferGrowthTest.java"
Task: "Integrationstest Batching in BatchingTest.java"
Task: "Integrationstest Änderung während Flush in ConcurrentModificationDuringFlushTest.java"
Task: "Integrationstest Statistik-Upsert in StatisticsUpsertTest.java"
Task: "Integrationstest Zeitraumsumme in StatisticsSumTest.java"
Task: "Integrationstest Login-Pool-Isolation in LoginPoolIsolationTest.java"
```

---

## Implementation Strategy

### MVP (User Story 1 **und** 2)

Anders als bei B01 umfasst der MVP hier zwei Stories. Der Grund steht in `spec.md`: Eine
Persistenz, die nichts verliert, aber den Tick blockiert, verletzt Constitution I und II und macht
den Zielbetrieb aus ADR-002 unmöglich — sie wäre als Zwischenstand nicht auslieferbar.

1. Phase 1 (Setup) abschließen
2. Phase 2 (Foundational) abschließen — kritisch, enthält beide B01-Erweiterungen
3. Phase 3 (US1) und Phase 4 (US2) abschließen
4. **Stoppen und validieren**: `quickstart.md` Abschnitte 3, 5, 7 und 8 durchlaufen
5. Ein Server, der Fortschritt sicher speichert und dabei flüssig bleibt — die Grundlage für B03,
   B06, B11 und B12

### Incremental Delivery

1. Setup + Foundational → Fundament bereit
2. US1 + US2 → MVP, unabhängig testbar
3. US3 → Ausfallsicherheit für den Betrieb
4. US4 → Migrationsmechanik für die zweite Auslieferung
5. US5 → Datenschutz und Nachvollziehbarkeit
6. Polish → Lastnachweis und Dokumentation

### Vorbedingung

Docker muss laufen — geprüft am 2026-08-19 (Desktop 29.7.2, Linux-Container, PostgreSQL 18.6).
Ohne Container sind alle Aufgaben in `rpg-persistence/src/test/` nicht ausführbar.

---

## Änderungen aus der Analyse (2026-08-19)

`/speckit-analyze` fand einen kritischen und acht weitere Punkte. Alle sind eingearbeitet:

| Befund | Behandlung |
|---|---|
| **D1** (CRITICAL) — T058 alt verlangte Message-Schlüssel, die es im Projekt nicht gab | Message-Schlüssel-Ablage als T010–T016 in Foundational ergänzt; FR-023/FR-023a und SC-012 in `spec.md` nachgezogen |
| **D2** (HIGH) — B01 enthält drei hartcodierte Spielertexte (Constitution V) | T016 stellt `PreJoinGuard` auf Message-Schlüssel um |
| **E1** (HIGH) — SC-001 (harter Prozessabbruch) ohne Test | T032 ergänzt |
| **E2** (MEDIUM) — FR-022 ohne Test der Fail-Fast-Validierung | T022 ergänzt |
| **E3** (MEDIUM) — FR-016b (Zeitraumsumme) ohne Test | T053 ergänzt |
| **F1** (MEDIUM) — Kern-Anforderungen lagen in Polish | User Story 5 gebildet (T077–T083); Item-Repository nach US1 verschoben (T037, T039, T042) |
| **E4** (LOW) — SC-004 ohne Tickrate-Nachweis während eines Ausfalls | T086 um einen Ausfall unter Last erweitert |
| **C1** (LOW) — FR-017 ohne Absicherung gegen späteren Aufräumjob | `DELETE`-Prüfung in T061 aufgenommen |
| **B1** (LOW) — Ersatzkennzeichen fehlte als Entität | In `data-model.md` ergänzt, mit T078 abgesichert |

---

## Notes

- [P] = andere Datei, keine offene Abhängigkeit
- [Story]-Label ordnet jede Aufgabe einer User Story zur Nachverfolgbarkeit zu
- Tests zuerst schreiben und fehlschlagen lassen, dann implementieren
- **Bei MockBukkit- und Testcontainers-Tests immer die Zahl der übersprungenen Tests prüfen, nicht
  nur die der fehlgeschlagenen** — in B01 hat MockBukkit drei Tests still als „skipped" gemeldet,
  was wie Abdeckung aussah und keine war
- MapStruct-Mapper über die erzeugte `*Impl`-Klasse instanziieren, nie über `Mappers.getMapper`
- `rpg-core` bleibt bukkitfrei: `Messages` liefert `String`, die Umwandlung in einen Adventure-
  `Component` geschieht ausschließlich in `rpg-platform`
- Nach jeder Story-Checkpoint-Erreichung: `quickstart.md` für die betroffenen Abschnitte
  gegenprüfen
