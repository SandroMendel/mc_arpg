---
description: "Task list for B03 · Spieler-Session & Datenlebenszyklus"
---

# Tasks: B03 · Spieler-Session & Datenlebenszyklus

**Input**: Design documents from `/specs/003-player-session/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md (alle
vorhanden). B01 und B02 sind fertig implementiert und verifiziert.

**Tests**: Enthalten und nicht optional. Constitution VII verlangt Unit-Tests ohne laufenden Server
für jede Regel der Domänenschicht und Persistenztests gegen eine echte PostgreSQL-Instanz.

**Organization**: Aufgaben sind nach User Story aus `spec.md` gruppiert. Wie bei B02 gibt es
mehrere P1-Stories — hier sogar drei: Laden (US1), Entladen (US2) und der Fehlerpfad (US3). Der
Fehlerpfad ist P1, weil ein Spieler mit leerem Profil bestehenden Fortschritt überschreibt und das
erst Stunden später auffällt.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Kann parallel laufen (andere Datei, keine offene Abhängigkeit)
- **[Story]**: Zugehörige User Story (US1–US6)
- Exakte Dateipfade in jeder Beschreibung

## Path Conventions

Multi-Modul-Gradle-Projekt aus B01: `rpg-core/`, `rpg-persistence/`, `rpg-platform/`,
`rpg-plugin/`. **Keine neuen Module und keine neuen Abhängigkeiten** — B03 nutzt ausschließlich,
was B01 und B02 bereits mitbringen.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Konfiguration, Meldungstexte und Modulanmeldung vorbereiten

- [X] T001 [P] `SessionConfig` mit Ladefrist (Standard 5 s, Obergrenze 5 s), Abgleichsintervall
      (Standard 30 s) und Verfallszeit der Zwischenablage in
      `rpg-core/src/main/java/rpg/core/session/SessionConfig.java`
- [X] T002 [P] `SessionMessageKeys` mit den Schlüsseln für Abweisung bei Ladefehler,
      Fristüberschreitung und unbekannter Standfassung in
      `rpg-core/src/main/java/rpg/core/session/SessionMessageKeys.java`
- [X] T003 Die Schlüssel aus T002 in `rpg-plugin/src/main/resources/messages.yml` hinterlegen und
      in `RpgPlugin.loadMessages` zur Startvalidierung anmelden (benötigt T002)
- [X] T004 Unit-Test: `SessionConfig` weist eine Ladefrist über 5 s und ein Abgleichsintervall
      unter 5 s mit klarer Meldung ab, in
      `rpg-core/src/test/java/rpg/core/session/SessionConfigValidationTest.java` (benötigt T001)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Alles, was **alle** Stories brauchen — Entitäten, Zustandsautomat, Registry,
Charakter-Tabelle

**⚠️ CRITICAL**: Keine User-Story-Arbeit beginnt, bevor diese Phase abgeschlossen ist

### Domänentypen (`rpg-core/session`)

- [X] T005 [P] `CharacterClass`-Enum (`WARRIOR`, `MAGE`, `ROGUE`) in
      `rpg-core/src/main/java/rpg/core/session/CharacterClass.java`
- [X] T006 [P] `SessionState`-Enum (`LOADING`, `READY`, `UNLOADING`, `FAILED`) mit den erlaubten
      Übergängen aus `data-model.md` in
      `rpg-core/src/main/java/rpg/core/session/SessionState.java`
- [X] T007 [P] `PlayerCharacter` (characterId, playerId, characterClass, dataVersion, revision,
      createdAt, lastPlayedAt) in
      `rpg-core/src/main/java/rpg/core/session/PlayerCharacter.java`
- [X] T008 [P] Fehlertypen `SessionNotReadyException`, `SessionLoadException`,
      `DuplicateSessionException`, `UnknownDataVersionException` in
      `rpg-core/src/main/java/rpg/core/session/`
- [X] T009 `PlayerSession` mit Bereitschaftszustand und **unveränderlichem** aktivem Charakter —
      ohne jede Methode, die ihn austauscht (FR-021b) — in
      `rpg-core/src/main/java/rpg/core/session/PlayerSession.java` (benötigt T005, T006, T007)
- [X] T010 Unit-Test Zustandsautomat: nur die in `data-model.md` genannten Übergänge sind erlaubt;
      jeder andere wird abgelehnt, in
      `rpg-core/src/test/java/rpg/core/session/SessionStateTransitionTest.java` (benötigt T009)

### Registry und Lebenszyklus (`rpg-core/session`)

- [X] T011 `SessionRegistry`-Schnittstelle gemäß `contracts/session-registry.md` — ohne Methoden
      zum Erzeugen, Verändern oder Entfernen — in
      `rpg-core/src/main/java/rpg/core/session/SessionRegistry.java` (benötigt T009)
- [X] T012 `SessionLifecycle`-Schnittstelle gemäß `contracts/session-lifecycle.md` in
      `rpg-core/src/main/java/rpg/core/session/SessionLifecycle.java` (benötigt T009)
- [X] T013 `DefaultSessionRegistry` mit höchstens einer Sitzung je Spieler; ein zweiter Eintrag
      wird **abgelehnt**, nicht überschrieben (FR-014), in
      `rpg-core/src/main/java/rpg/core/session/DefaultSessionRegistry.java` (benötigt T011)
- [X] T014 Unit-Test: eine zweite Sitzung für denselben Spieler wird abgelehnt und die erste bleibt
      unverändert bestehen, in
      `rpg-core/src/test/java/rpg/core/session/SessionRegistryUniquenessTest.java` (benötigt T013)

### Charakter-Datenzugriff

- [X] T015 Migration `V3_1__player_characters.sql` mit `rpg.character` und der Eindeutigkeit über
      `(player_id, character_class)` in
      `rpg-persistence/src/main/resources/db/migration/V3_1__player_characters.sql`
- [X] T016 `CharacterRepository`-Schnittstelle in
      `rpg-core/src/main/java/rpg/core/session/CharacterRepository.java` (benötigt T007)
- [X] T017 `JdbcCharacterRepository` in
      `rpg-persistence/src/main/java/rpg/persistence/jdbc/JdbcCharacterRepository.java`
      (benötigt T015, T016)
- [X] T018 [P] Integrationstest: die Migration legt `rpg.character` an, ohne die Tabellen aus B02
      zu verändern; ein zweiter Lauf wendet null Migrationen an, in
      `rpg-persistence/src/test/java/rpg/persistence/CharacterMigrationTest.java` (benötigt T015)

### Modulanmeldung

- [X] T019 `SessionModule` nach B01-Vertrag mit Abhängigkeit auf `persistence` in
      `rpg-persistence/src/main/java/rpg/persistence/session/SessionModule.java` (benötigt T013)
      — **nicht** in `rpg-platform`: es verdrahtet `JdbcCharacterRepository` und
      `SessionBundleLoader`, und eine Abhängigkeit `platform → persistence` würde die Richtung aus
      Constitution III.2 umkehren
- [X] T020 `SessionModule` in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` in `modules()`
      eintragen, **nach** `PersistenceModule` (benötigt T019)
- [X] T020a Die Session-Listener in `RpgPlugin` **nach** `bootstrap.start()` anmelden und ihre
      Abhängigkeiten über B01s Registry beziehen — nur `rpg-plugin` sieht beide Module. Anders als
      B01s `PreJoinGuard`, der bewusst vorher angemeldet wird, in
      `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` (benötigt T019)

**Checkpoint**: Foundation bereit — die drei P1-Stories können beginnen

---

## Phase 3: User Story 1 - Beim Betreten ist der Fortschritt sofort da (Priority: P1) 🎯 MVP

**Goal**: Laden vor dem Betreten der Welt, sicherer Zustand als Rückfallebene, Freigabe innerhalb
von 500 ms (FR-001 bis FR-006a, FR-016, FR-018)

**Independent Test**: Einen Spieler mit gespeichertem Fortschritt verbinden lassen und prüfen, dass
er sofort freigegeben ist und exakt seinen Stand vorfindet (`quickstart.md`, Abschnitte 3 und 5)

### Tests for User Story 1 ⚠️

> Tests zuerst schreiben, sicherstellen dass sie ohne Implementierung fehlschlagen

- [X] T021 [P] [US1] Unit-Test: eine Sitzung im Zustand `LOADING` liefert über die Registry nichts
      und **keine** Standardwerte (FR-004, SC-002), in
      `rpg-core/src/test/java/rpg/core/session/SessionNotReadyTest.java`
- [X] T022 [P] [US1] Integrationstest: das Sammelladen entnimmt dem Login-Pool **eine** Verbindung
      für Account, Charaktere und Item-Instanzen. Gemessen über eine zählende `DataSource`-Hülle im
      Test, die `getConnection()` mitzählt — nicht über die Laufzeit und nicht über
      HikariCP-Metriken, die dafür nicht konfiguriert sind (FR-005), in
      `rpg-persistence/src/test/java/rpg/persistence/SessionBundleLoaderTest.java`
- [X] T023 [P] [US1] MockBukkit-Test: ein Spieler mit vorgeladener Sitzung ist beim Betreten sofort
      freigegeben, der sichere Zustand wird nie aktiv, in
      `rpg-platform/src/test/java/rpg/platform/session/SessionPreLoadListenerTest.java`
- [X] T024 [P] [US1] MockBukkit-Test: fehlt die vorgeladene Sitzung, greift die Rückfallebene —
      bewegungsgesperrt und schadensimmun bis zum Nachladen, in
      `rpg-platform/src/test/java/rpg/platform/session/SafeStateGuardTest.java`
- [X] T025 [P] [US1] Unit-Test: der `PlayerMoveEvent`-Handler kehrt zurück, ohne Registry oder
      Zwischenablage zu berühren, wenn niemand lädt — nachzuweisen über Attrappen, die bei jedem
      Zugriff fehlschlagen (Constitution II), in
      `rpg-platform/src/test/java/rpg/platform/session/SafeStateGuardHotPathTest.java`

### Implementation for User Story 1

- [X] T026 [US1] `SessionBundle` als Ergebnis des Sammelladens (Account, Charaktere,
      Item-Instanzen) in `rpg-core/src/main/java/rpg/core/session/SessionBundle.java`
- [X] T027 [US1] `SessionBundleLoader` — drei Anweisungen auf **einer** Verbindung in einer
      Transaktion, kein `JOIN` über alle Tabellen (siehe `research.md`), in
      `rpg-persistence/src/main/java/rpg/persistence/session/SessionBundleLoader.java`
      (benötigt T017, T026)
- [X] T028 [US1] `DefaultSessionLifecycle.beginLoad` — nutzt B02s `SessionHandover` für das
      Zurückstellen bis zum Abschluss der Vorsitzung und baut es **nicht** nach (FR-013), in
      `rpg-core/src/main/java/rpg/core/session/DefaultSessionLifecycle.java` (benötigt T012, T027)
- [X] T029 [US1] `PendingSessionStash` mit Verfallszeit je Eintrag in
      `rpg-platform/src/main/java/rpg/platform/session/PendingSessionStash.java` (benötigt T009)
- [X] T030 [US1] `SessionPreLoadListener` auf `AsyncPlayerPreLoginEvent` mit Priorität **`LOW`** —
      lädt vor dem Betreten der Welt, legt das Ergebnis in die Zwischenablage. Die Priorität ist
      nicht beliebig: B01s `PreJoinGuard` läuft auf `LOWEST` und weist ab, solange der Bootstrap
      nicht fertig ist. Bei gleicher Priorität wäre die Reihenfolge undefiniert und B03 könnte vor
      dem Bootstrap-Wächter laden. Der Listener bricht zusätzlich ab, wenn das Ereignis bereits
      abgewiesen wurde. Datei:
      `rpg-platform/src/main/java/rpg/platform/session/SessionPreLoadListener.java`
      (benötigt T028, T029)
- [X] T031 [US1] `SessionJoinListener` auf `PlayerJoinEvent` — holt die vorgeladene Sitzung ab und
      gibt frei; fehlt sie, wird nachgeladen, in
      `rpg-platform/src/main/java/rpg/platform/session/SessionJoinListener.java` (benötigt T029)
- [X] T032 [US1] `SafeStateGuard` (FR-002) — `setInvulnerable(true)` und Abbruch von
      `PlayerMoveEvent` bei Blockwechsel, mit dem Feldvergleich als erstem Schritt, in
      `rpg-platform/src/main/java/rpg/platform/session/SafeStateGuard.java` (benötigt T031)
- [X] T033 [US1] Freigabe (FR-003): Zustand auf `READY`, Immunität und Bewegungssperre aufheben, Zeitpunkt
      für die Messung von SC-001 festhalten, in
      `rpg-platform/src/main/java/rpg/platform/session/SessionJoinListener.java` (benötigt T032)

**Checkpoint**: Ein Spieler betritt den Server und findet seinen Fortschritt vor

---

## Phase 4: User Story 2 - Kein Verlassen kostet Fortschritt (Priority: P1) 🎯 MVP

**Goal**: Sofortiges Schreiben bei jedem Sitzungsende, garantiertes Aufräumen ohne Speicherleck
(FR-007 bis FR-010)

**Independent Test**: Sitzung auf allen drei Wegen beenden und prüfen, dass Fortschritt geschrieben
und das Sitzungsobjekt entfernt ist (`quickstart.md`, Abschnitte 6 und 8)

### Tests for User Story 2 ⚠️

- [X] T034 [P] [US2] Unit-Test: alle drei Sitzungsenden (Quit, Kick, Timeout) lösen genau **einen**
      Entladevorgang aus — kein doppeltes Schreiben (SC-004), in
      `rpg-core/src/test/java/rpg/core/session/SessionEndReasonTest.java`
- [X] T035 [P] [US2] Unit-Test: die Sitzung wird erst nach Abschluss des Schreibvorgangs entfernt
      (FR-008), in `rpg-core/src/test/java/rpg/core/session/SessionUnloadOrderTest.java`
- [X] T036 [P] [US2] Unit-Test: der Abgleich entfernt eine Sitzung ohne verbundenen Spieler und
      lässt Sitzungen verbundener Spieler unangetastet, in
      `rpg-core/src/test/java/rpg/core/session/SessionReconcilerTest.java`
- [X] T037 [P] [US2] Unit-Test: nach 10.000 simulierten Verbindungen und Trennungen entspricht die
      Zahl gehaltener Sitzungen der Zahl der Verbundenen (FR-009, SC-008), in
      `rpg-core/src/test/java/rpg/core/session/SessionRegistryLeakTest.java`
- [X] T038 [P] [US2] MockBukkit-Test: ein Kick löst denselben Entladepfad aus wie ein reguläres
      Verlassen, in `rpg-platform/src/test/java/rpg/platform/session/SessionQuitListenerTest.java`

### Implementation for User Story 2

- [X] T039 [US2] `SessionEndReason`-Enum (`QUIT`, `KICK`, `TIMEOUT`, `SHUTDOWN`, `RECONCILED`) in
      `rpg-core/src/main/java/rpg/core/session/SessionEndReason.java`
- [X] T040 [US2] `DefaultSessionLifecycle.endSession` — ruft B02s vorhandenes `onSessionEnd` auf
      und führt **keine** eigene Schreiblogik aus (FR-007), in
      `rpg-core/src/main/java/rpg/core/session/DefaultSessionLifecycle.java` (benötigt T028, T039)
- [X] T041 [US2] Entfernen der Sitzung erst nach Abschluss des Schreibvorgangs, in
      `rpg-core/src/main/java/rpg/core/session/DefaultSessionLifecycle.java` (benötigt T040)
- [X] T042 [US2] `SessionQuitListener` auf `PlayerQuitEvent` — **einziger** Auslöser für alle drei
      Sitzungsenden; ausdrücklich **kein** zusätzlicher Handler auf `PlayerKickEvent`, weil das ein
      zweites Entladen auslösen würde, in
      `rpg-platform/src/main/java/rpg/platform/session/SessionQuitListener.java` (benötigt T040)
- [X] T043 [US2] `SessionReconciler` — vergleicht die Registry gegen die tatsächlich verbundenen
      Spieler und räumt die Differenz, in
      `rpg-core/src/main/java/rpg/core/session/SessionReconciler.java` (benötigt T013, T040)
- [X] T044 [US2] Abgleichszyklus über `Scheduler.runAsyncDelayed`, der sich selbst neu plant — kein
      eigener Thread-Pool (Constitution I), in
      `rpg-persistence/src/main/java/rpg/persistence/session/SessionModule.java` (benötigt T043)
- [X] T045 [US2] Verfallene Einträge der Zwischenablage im selben Abgleich räumen, in
      `rpg-core/src/main/java/rpg/core/session/SessionReconciler.java` (benötigt T029, T043)
- [X] T046 [US2] Shutdown-Pfad: `SessionModule.stop()` beendet alle aktiven Sitzungen; der Flush
      selbst läuft über B02s Modul-Shutdown (FR-010), in
      `rpg-persistence/src/main/java/rpg/persistence/session/SessionModule.java` (benötigt T040)

**Checkpoint**: Fortschritt überlebt jedes Sitzungsende, kein Speicherleck

---

## Phase 5: User Story 3 - Ein Ladefehler kostet niemals das Profil (Priority: P1) 🎯 MVP

**Goal**: Abweisung statt leerem Profil, korrekte Sequenzierung beim schnellen Wiederverbinden
(FR-011 bis FR-015)

**Independent Test**: Ladefehler erzwingen und prüfen, dass abgewiesen wird und der gespeicherte
Datensatz unverändert bleibt (`quickstart.md`, Abschnitt 8)

### Tests for User Story 3 ⚠️

- [X] T047 [P] [US3] Unit-Test: der Übergang `FAILED → entfernt` löst **keinen** Schreibvorgang aus
      (FR-012) — der wichtigste Test dieses Blocks, in
      `rpg-core/src/test/java/rpg/core/session/FailedSessionWritesNothingTest.java`
- [X] T048 [P] [US3] Unit-Test: der Übergang `LOADING → UNLOADING` (Trennung während des Ladens)
      löst ebenfalls keinen Schreibvorgang aus (FR-015), in
      `rpg-core/src/test/java/rpg/core/session/AbandonedLoadWritesNothingTest.java`
- [X] T049 [P] [US3] Unit-Test: eine Fristüberschreitung führt zu `FAILED` und zur Abweisung, nicht
      zu unbegrenztem Warten (FR-006), in
      `rpg-core/src/test/java/rpg/core/session/LoadTimeoutTest.java` — mit steuerbarer Uhr statt
      Wartezeit
- [X] T050 [P] [US3] Integrationstest: nach einer abgewiesenen Anmeldung ist die `revision` des
      gespeicherten Datensatzes unverändert (SC-007), in
      `rpg-persistence/src/test/java/rpg/persistence/RejectedLoginLeavesRecordUntouchedTest.java`
- [X] T051 [P] [US3] MockBukkit-Test: bei einem Ladefehler wird die Verbindung im Vorlade-Ereignis
      abgewiesen — der Spieler betritt die Welt nie, in
      `rpg-platform/src/test/java/rpg/platform/session/PreLoadRejectionTest.java`

### Implementation for User Story 3

- [X] T052 [US3] `FAILED`-Pfad: Sitzung verwerfen, ohne zu schreiben, in
      `rpg-core/src/main/java/rpg/core/session/DefaultSessionLifecycle.java` (benötigt T040)
- [X] T053 [US3] `abandonLoad`: laufenden Ladevorgang verwerfen, wenn der Spieler vorher trennt, in
      `rpg-core/src/main/java/rpg/core/session/DefaultSessionLifecycle.java` (benötigt T052)
- [X] T054 [US3] Ladefrist von 5 Sekunden durchsetzen und danach abweisen, in
      `rpg-core/src/main/java/rpg/core/session/DefaultSessionLifecycle.java` (benötigt T052)
- [X] T055 [US3] Abweisung im Vorlade-Ereignis über `disallow` mit Meldung aus den
      Message-Schlüsseln, in
      `rpg-platform/src/main/java/rpg/platform/session/SessionPreLoadListener.java`
      (benötigt T003, T030, T054)
- [X] T055a [US3] B02s bereits vorhandene, aber **nirgends aufgerufene** `acceptsLogins()` und
      `loginRefusalReason()` im Vorlade-Ereignis auswerten und die Anmeldung bei nicht erreichbarer
      Datenhaltung oder erschöpftem Schreibpuffer mit deren Message-Schlüssel abweisen, in
      `rpg-platform/src/main/java/rpg/platform/session/SessionPreLoadListener.java`
      (benötigt T030) — schließt eine Lücke in B02: FR-005a/FR-009b sind dort implementiert, aber
      an keinen Auslöser angeschlossen
- [X] T055b [P] [US3] MockBukkit-Test: bei nicht erreichbarer Datenhaltung wird die Anmeldung im
      Vorlade-Ereignis abgewiesen und der Spieler betritt die Welt nie (B02/SC-009), in
      `rpg-platform/src/test/java/rpg/platform/session/LoginRefusedDuringOutageTest.java`
      (benötigt T055a)
- [X] T056 [US3] `PlayerConnectionCloseEvent`-Handler, der die Zwischenablage einer vorgeladenen,
      aber nie abgeholten Sitzung räumt, in
      `rpg-platform/src/main/java/rpg/platform/session/PendingSessionStash.java` (benötigt T029)

**Checkpoint**: MVP vollständig — Laden, Entladen und der Fehlerpfad stehen

---

## Phase 6: User Story 4 - Drei Charaktere je Account (Priority: P2)

**Goal**: Höchstens ein Charakter je Klasse, unabhängiger Fortschritt (FR-017 bis FR-021b)

**Independent Test**: Zwei Charaktere unterschiedlicher Klassen anlegen, mit einem Fortschritt
erzeugen, den anderen prüfen (`quickstart.md`, Abschnitt 2)

### Tests for User Story 4 ⚠️

- [X] T057 [P] [US4] Integrationstest: ein zweiter Charakter derselben Klasse wird von der
      **Datenbank** abgelehnt, nicht erst von Anwendungscode (FR-020), in
      `rpg-persistence/src/test/java/rpg/persistence/CharacterUniquenessTest.java`
- [X] T058 [P] [US4] Integrationstest: Fortschritt auf einem Charakter lässt die übrigen
      unverändert (FR-019), in
      `rpg-persistence/src/test/java/rpg/persistence/CharacterIndependenceTest.java`
- [X] T059 [P] [US4] Unit-Test: `PlayerSession` bietet **keine** Methode zum Wechseln des aktiven
      Charakters (FR-021a, FR-021b) — nachzuweisen über die deklarierten Methoden, in
      `rpg-core/src/test/java/rpg/core/session/ActiveCharacterIsImmutableTest.java`
- [X] T060 [P] [US4] Integrationstest: das Anonymisieren eines Accounts aus B02 entfernt über den
      Fremdschlüssel auch dessen Charaktere, in
      `rpg-persistence/src/test/java/rpg/persistence/CharacterCascadeOnAnonymizeTest.java`

### Implementation for User Story 4

- [X] T061 [US4] `CharacterRepository.create` mit Ablehnung eines zweiten Charakters derselben
      Klasse, in
      `rpg-persistence/src/main/java/rpg/persistence/jdbc/JdbcCharacterRepository.java`
      (benötigt T017)
- [X] T062 [US4] Auswahl des aktiven Charakters beim Erzeugen der Sitzung; ein Spieler ohne
      Charakter erhält eine Sitzung ohne aktiven Charakter (FR-021), in
      `rpg-core/src/main/java/rpg/core/session/DefaultSessionLifecycle.java` (benötigt T028)
- [X] T063 [US4] Meldungstext für die Ablehnung eines doppelten Charakters als Message-Schlüssel,
      in `rpg-core/src/main/java/rpg/core/session/SessionMessageKeys.java` und
      `rpg-plugin/src/main/resources/messages.yml` (benötigt T003)
- [X] T063a [US4] Migration `V3_2__item_instance_owned_by_character.sql`: `rpg.item_instance`
      erhält `character_id` als Fremdschlüssel auf `rpg.character` und verliert
      `owner_player_id` (ADR-011), in
      `rpg-persistence/src/main/resources/db/migration/V3_2__item_instance_owned_by_character.sql`
      (benötigt T015)
- [X] T063b [US4] `ItemInstance` und `ItemInstanceRepository.loadByOwner` auf `characterId`
      umstellen, in `rpg-core/src/main/java/rpg/core/persistence/ItemInstance.java` und
      `rpg-core/src/main/java/rpg/core/persistence/ItemInstanceRepository.java` (benötigt T063a)
- [X] T063c [US4] `JdbcItemInstanceRepository` und den Löschpfad in `JdbcPlayerStateRepository`
      auf `character_id` umstellen; die Kaskade `player_state → character → item_instance`
      erledigt das Aufräumen bei der Anonymisierung, in
      `rpg-persistence/src/main/java/rpg/persistence/jdbc/` (benötigt T063b)
- [X] T063d [P] [US4] Bestehende B02-Tests `ItemInstanceRoundTripTest` und `AnonymizationTest` auf
      die neue Bindung anpassen und prüfen, dass die Anonymisierung weiterhin keine Kennung
      zurücklässt, in `rpg-persistence/src/test/java/rpg/persistence/` (benötigt T063c)
- [X] T063e [P] [US4] Integrationstest: ein Gegenstand gehört genau einem Charakter; die
      Gegenstände der übrigen Charaktere desselben Accounts bleiben unberührt (ADR-011), in
      `rpg-persistence/src/test/java/rpg/persistence/ItemOwnedByCharacterTest.java`
      (benötigt T063c)

**Checkpoint**: US1 bis US4 funktionieren unabhängig

---

## Phase 7: User Story 5 - Spielerdaten ohne Sitzung lesbar (Priority: P2)

**Goal**: Lesepfad für B12 und B14, der keine Sitzung erzeugt und nichts schreibt (FR-022 bis
FR-024)

**Independent Test**: Daten eines nicht verbundenen Spielers lesen und prüfen, dass keine Sitzung
entsteht (`quickstart.md`, Abschnitt 1)

### Tests for User Story 5 ⚠️

- [X] T064 [P] [US5] Integrationstest: ein Lesezugriff auf einen nicht verbundenen Spieler erzeugt
      keine Sitzung und verändert nichts (FR-022, FR-023, SC-010), in
      `rpg-persistence/src/test/java/rpg/persistence/OfflineReadCreatesNoSessionTest.java`
- [X] T065 [P] [US5] Unit-Test: für einen verbundenen Spieler liefert der Lesepfad den aktuellen
      Sitzungszustand und nicht den gespeicherten Stand (FR-024), in
      `rpg-core/src/test/java/rpg/core/session/OfflineReaderPrefersSessionTest.java`

### Implementation for User Story 5

- [X] T066 [US5] `OfflinePlayerReader`-Schnittstelle und `PlayerSnapshot` gemäß
      `contracts/offline-access.md` — ohne jede schreibende Methode — in
      `rpg-core/src/main/java/rpg/core/session/OfflinePlayerReader.java`
- [X] T067 [US5] `DefaultOfflinePlayerReader`, der für verbundene Spieler die Registry und sonst
      das Repository befragt, in
      `rpg-core/src/main/java/rpg/core/session/DefaultOfflinePlayerReader.java`
      (benötigt T016, T066)
- [X] T068 [US5] `OfflinePlayerReader` über die Registry aus B01 veröffentlichen, damit B12 und B14
      ihn beziehen können, in
      `rpg-persistence/src/main/java/rpg/persistence/session/SessionModule.java` (benötigt T067)

**Checkpoint**: US1 bis US5 funktionieren unabhängig

---

## Phase 8: User Story 6 - Alte Spielerstände funktionieren weiter (Priority: P3)

**Goal**: Überführung älterer Standfassungen beim Laden (FR-025 bis FR-027)

**Independent Test**: Datensatz in älterer Fassung ablegen, laden und prüfen (`quickstart.md`,
Abschnitt 4)

### Tests for User Story 6 ⚠️

- [ ] T069 [P] [US6] Integrationstest: ein Charakter in älterer Fassung wird beim Laden überführt,
      der Fortschritt bleibt erhalten (SC-009), in
      `rpg-persistence/src/test/java/rpg/persistence/StateVersionMigrationTest.java`
      — **noch nicht schreibbar.** Fassung 1 ist die erste; ein Datensatz „älterer Fassung" lässt
      sich nicht erzeugen, weil `PlayerCharacter` `dataVersion >= 1` erzwingt. Der Test gehört in
      den Block, der Fassung 2 einführt — das ist auch der erste Moment, in dem er fehlschlagen
      kann. Die Schutzmechanismen um die Überführungsschleife sind in `StateVersionMigratorTest`
      abgedeckt.
- [ ] T070 [P] [US6] Integrationstest: der überführte Stand wird in der aktuellen Fassung
      gespeichert und beim nächsten Laden nicht erneut überführt — nachzuweisen über
      `data_version` in der Zeile (FR-026), in
      `rpg-persistence/src/test/java/rpg/persistence/StateVersionPersistedTest.java`
      — **noch nicht schreibbar**, aus demselben Grund wie T069. Der Rückschreibpfad selbst
      (`markCharactersDirty`) ist über `PersistenceSessionWriter` und `JdbcCharacterRepository.put`
      vorhanden und wird durch `SessionLoadIntegrationTest` beim Schreiben eines Charakters
      mitgeprüft; ausgelöst wird er erst durch eine echte Überführung.
- [X] T071 [P] [US6] Unit-Test: eine unbekannte Fassung führt zur Abweisung, nicht zu einer
      Fehlinterpretation (FR-027), in
      `rpg-core/src/test/java/rpg/core/session/UnknownDataVersionTest.java`

### Implementation for User Story 6

- [X] T072 [US6] `StateVersionMigrator` mit registrierten Überführungsschritten je Fassung in
      `rpg-core/src/main/java/rpg/core/session/StateVersionMigrator.java` (benötigt T007)
- [X] T073 [US6] Überführung in den Ladepfad einhängen und den überführten Stand vormerken, damit
      er in der aktuellen Fassung geschrieben wird, in
      `rpg-core/src/main/java/rpg/core/session/DefaultSessionLifecycle.java` (benötigt T072)
- [X] T074 [US6] Abweisung bei unbekannter Fassung mit Meldung aus den Message-Schlüsseln, in
      `rpg-core/src/main/java/rpg/core/session/DefaultSessionLifecycle.java` (benötigt T003, T072)

**Checkpoint**: Alle sechs User Stories sind unabhängig funktionsfähig

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T075 [P] Javadoc für die öffentlichen `rpg-core/session`-Schnittstellen gemäß `contracts/`
- [X] T076 [P] Statische Prüfung erweitern: kein Block außerhalb von `rpg-platform/session` meldet
      Listener auf `PlayerJoinEvent`, `PlayerQuitEvent` oder `AsyncPlayerPreLoginEvent` an — sonst
      entstehen zweite Lade- oder Entladepfade, in
      `rpg-platform/src/test/java/rpg/platform/session/NoCompetingSessionListenersTest.java`
- [ ] T077 [P] `quickstart.md`-Abschnitte 1 bis 5 vollständig durchlaufen
- [ ] T078 Lasttest: 200 gleichzeitige Anmeldungen ohne Zeitüberschreitung und ohne messbare
      Tickrate-Verschlechterung (SC-005)
- [ ] T079 Messung: Freigabe nach dem Betreten in 95 % der Fälle unter 500 ms (SC-001)
- [ ] T080 `quickstart.md`-Abschnitte 6 bis 8 auf einem echten Paper-Server durchlaufen — sinnvoll
      gemeinsam mit den offenen Punkten T085–T087 aus B02

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: keine Abhängigkeiten — kann sofort starten
- **Foundational (Phase 2)**: hängt von Setup ab — blockiert alle User Stories
- **User Stories (Phase 3–8)**: hängen alle von Foundational ab
- **Polish (Phase 9)**: hängt von den gewünschten User Stories ab

### User Story Dependencies

- **US1, US2, US3 (alle P1)**: alle starten nach Foundational. Sie teilen sich
  `DefaultSessionLifecycle`, weshalb Aufgaben an dieser Datei sequenziell laufen — die Stories
  bleiben dennoch unabhängig testbar, weil jede ihren eigenen Pfad darin besitzt (Laden, Entladen,
  Fehlerbehandlung).
- **US4 (P2)**: startet nach Foundational; baut auf dem Ladepfad aus US1 auf.
- **US5 (P2)**: startet nach Foundational; unabhängig von US1 bis US4 testbar.
- **US6 (P3)**: startet nach US1, weil die Überführung im Ladepfad hängt.

### Within Each User Story

- Tests zuerst, müssen ohne Implementierung fehlschlagen
- Schnittstellen vor Implementierungen, Implementierungen vor Ereignis-Anbindung
- Story-Checkpoint erreicht, bevor die nächste Priorität begonnen wird

### Parallel Opportunities

- Setup: T001, T002
- Foundational: T005–T008, T018
- Innerhalb jeder Story: alle mit [P] markierten Tests parallel
- Polish: T075, T076, T077

---

## Parallel Example: User Story 3

```bash
# Tests für User Story 3 parallel starten:
Task: "Unit-Test FAILED schreibt nichts in FailedSessionWritesNothingTest.java"
Task: "Unit-Test abgebrochenes Laden schreibt nichts in AbandonedLoadWritesNothingTest.java"
Task: "Unit-Test Fristüberschreitung in LoadTimeoutTest.java"
Task: "Integrationstest unveränderte Revision in RejectedLoginLeavesRecordUntouchedTest.java"
Task: "MockBukkit-Test Abweisung im Vorlade-Ereignis in PreLoadRejectionTest.java"
```

---

## Implementation Strategy

### MVP (User Stories 1, 2 **und** 3)

Drei P1-Stories, weil keine ohne die anderen tragfähig ist: Ein Ladepfad ohne Entladepfad verliert
den Fortschritt, den er geladen hat; beide zusammen ohne den Fehlerpfad lassen im Störfall ein
leeres Profil zu, das bestehenden Fortschritt überschreibt.

1. Phase 1 (Setup) abschließen
2. Phase 2 (Foundational) abschließen — kritisch
3. Phasen 3 bis 5 (US1, US2, US3) abschließen
4. **Stoppen und validieren**: `quickstart.md` Abschnitte 1, 3, 5 und 8 durchlaufen
5. Spieler können verbinden, spielen und den Server verlassen, ohne je Fortschritt zu verlieren —
   die Grundlage für B04, B06, B07, B08, B11 und B12

### Incremental Delivery

1. Setup + Foundational → Fundament bereit
2. US1 + US2 + US3 → MVP
3. US4 → drei Charaktere je Account
4. US5 → Lesepfad für Bestenlisten und Verwaltung
5. US6 → Migrationspfad für die zweite Auslieferung
6. Polish → Lastnachweis und Servervalidierung

---

## Notes

- **SC-003** (nach hartem Prozessabbruch höchstens ein Autosave-Intervall Verlust) wird von B02
  garantiert und ist dort mit `HardAbortRecoveryTest` belegt. B03 fügt keinen eigenen Schreibpfad
  hinzu und kann die Zusage daher weder verbessern noch brechen — es sei denn durch genau den
  zweiten Schreibpfad, den diese Liste ausschließt.
- **SC-006** (schnelles Wiederverbinden) ist nur auf einem echten Server aussagekräftig und liegt
  deshalb in `quickstart.md` Abschnitt 7 statt in einem automatisierten Test.
- [P] = andere Datei, keine offene Abhängigkeit
- **Die zentrale Leitplanke**: B02s Mechanik wird genutzt, nicht nachgebaut. T028 nutzt
  `SessionHandover`, T040 nutzt `onSessionEnd`, T046 verlässt sich auf B02s Modul-Shutdown. Ein
  zweiter Schreibpfad in B03 wäre der teuerste Fehler dieses Blocks.
- **Bei MockBukkit- und Testcontainers-Tests immer die Zahl der übersprungenen Tests prüfen**, nicht
  nur die der fehlgeschlagenen — in B01 hat MockBukkit drei Tests still als „skipped" gemeldet
- Zeitverhalten (5-Sekunden-Frist, Abgleichsintervall) über eine steuerbare Uhr prüfen, nicht über
  Wartezeiten im Test
- T047 und T048 sind die wichtigsten Tests des Blocks: Ein Schreibvorgang aus `FAILED` oder aus
  einem abgebrochenen Ladevorgang heraus ist genau der Fehler, der bestehenden Fortschritt zerstört
- Nach jeder Story-Checkpoint-Erreichung: `quickstart.md` für die betroffenen Abschnitte gegenprüfen

---

## Wo die Tests tatsächlich liegen

Die Aufgabenliste hat je Test eine eigene Datei vorgesehen. Umgesetzt wurden sie thematisch
gebündelt: eine Datei je Zusammenhang statt je Aufgabe. Ein Leser, der wissen will, warum ein
fehlgeschlagener Ladevorgang nichts schreibt, findet in `SessionLifecycleTest` alle Fälle
nebeneinander, statt sie aus fünf Dateien zusammenzusuchen. Die Zuordnung:

| Aufgaben | Datei |
|---|---|
| T021, T034, T035, T047, T048, T049 | `rpg-core/.../session/SessionLifecycleTest.java` |
| T036, T037 | `rpg-core/.../session/SessionReconcilerTest.java` |
| T065 | `rpg-core/.../session/OfflinePlayerReaderTest.java` |
| T071 | `rpg-core/.../session/StateVersionMigratorTest.java` |
| T025, T032, T033 | `rpg-platform/.../session/SafeStateGuardTest.java` |
| T023, T024, T038, T051, T055b | `rpg-platform/.../session/SessionListenerTest.java` |
| T029, T045 | `rpg-platform/.../session/PendingSessionStashTest.java` |
| T076 | `rpg-platform/.../session/NoCompetingSessionListenersTest.java` |
| T022, T050, T057, T058 | `rpg-persistence/.../session/SessionLoadIntegrationTest.java` |
| T063e | `rpg-persistence/.../ItemOwnershipTest.java` |
| T060, T063d | `rpg-persistence/.../AnonymizationTest.java`, `ItemInstanceRoundTripTest.java` |
| T059 | `rpg-core/.../session/SessionStateTransitionTest.java` |

Dazu kam eine Datei, die in der Liste nicht vorgesehen war und ohne die der Block auf einem echten
Server nichts getan hätte: `rpg-plugin/.../FullBootstrapTest.java` startet das komplette Plugin
gegen eine echte Datenbank. Sie deckt die Fehlerklasse ab, die jeder Modultest bestehen kann und
die trotzdem ein totes Plugin ergibt — ein Modul, das geschrieben, aber nie registriert wird; ein
Listener, der nie angemeldet wird; eine Konfigurationsdatei ohne Standardfassung. Genau das war der
Zustand, in dem B02 und B03 vor diesem Durchgang waren. `UnreachableDatabaseTest` ist die
Gegenprobe: ohne erreichbare Datenhaltung startet das Plugin nicht und lässt niemanden herein.
