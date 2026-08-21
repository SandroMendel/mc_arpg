---

description: "Aufgabenliste für B06 · Progression (Erfahrung & Level)"
---

# Tasks: B06 · Progression (Erfahrung & Level)

**Input**: Entwurfsdokumente aus `/specs/006-progression/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Enthalten und verpflichtend. Constitution VII verlangt serverfreie Unit-Tests für jede
Formel und jede Regel der Domänenschicht (SC-014) und **echtes PostgreSQL statt Mocks** für die
Persistenz. B06 ist **nicht** lasttestpflichtig — Prinzip VII nennt B05 und B10 beim Namen, nicht
B06.

**Organisation**: Nach User Stories gruppiert.

## Format: `[ID] [P?] [Story] Beschreibung`

- **[P]**: parallelisierbar (andere Datei, keine offene Abhängigkeit)
- **[Story]**: zugehörige User Story aus [spec.md](./spec.md)

## Pfadkonventionen

- `rpg-core/src/main/java/rpg/core/progression/` — Regeln, bukkitfrei
- `rpg-persistence/src/main/java/rpg/persistence/progression/` — Repository und **Modul** (B06 hat
  eine Datenbank, anders als B05 — siehe plan.md)
- `rpg-persistence/src/main/resources/db/migration/` — Migration
- `rpg-platform/src/main/java/rpg/platform/progression/` — Paper-Anbindung
- `rpg-plugin/` — Zusammenbau und `progression.yml`

---

## Phase 1: Setup

- [X] T001 `rpg-core/src/main/java/rpg/core/progression/package-info.java`: Javadoc hält die
      Blockgrenze fest — was B06 besitzt und was ausdrücklich B07 (Klassenwachstum), B08
      (Freischaltung), B09 (Zonen), B10 (Mob-Definitionen), B11 (Items), B13 (Anzeige) und B14
      (Befehle) gehört. Hält ausdrücklich fest, dass B06 **keine** Befehle und **keine** Anzeige
      enthält (FR-037)
- [X] T002 [P] `rpg-core/src/test/java/rpg/core/progression/ProgressionFixture.java`: gemeinsame
      Testumgebung mit **gesteuerter Uhr**, zählendem Scheduler, Attrappe für `StatEngine` und
      zählendem Fortschritts-Repository. Die Uhr ist keine Bequemlichkeit — Einladungsverfall und
      Bündelungsfenster sind zeitbasiert, und Tests mit echten Wartezeiten wären langsam und
      wackelig
- [X] T003 [P] `rpg-core/src/test/java/rpg/core/progression/CurveFixture.java`: eine gültige
      Kurve plus fünf absichtlich defekte Varianten (Lücke, Null, nicht monoton, kein Level 2,
      nichtnumerischer Schlüssel) als Testdaten für Phase 3

---

## Phase 2: Foundational (blockierende Voraussetzung)

**Zweck**: Typen, Konfiguration und Persistenzgerüst, ohne die keine User Story anfangen kann.

**⚠️ Kritisch**: Keine Story-Arbeit vor Abschluss dieser Phase.

- [X] T004 [P] `rpg-core/src/main/java/rpg/core/persistence/AggregateType.java`: Wert
      `CHARACTER_PROGRESS` ergänzen, damit `DirtyMark` und der Flush-Zyklus den Fortschritt
      adressieren können. Additiv, bricht keinen bestehenden Vertrag (FR-054)
- [X] T005 [P] `rpg-core/src/main/java/rpg/core/progression/ProgressState.java`: Record aus `level`
      und `xpInLevel` (`long`). Startzustand Level 1 / 0 XP; Level ≥ 1, XP ≥ 0 (FR-053a, FR-058)
- [X] T006 [P] `rpg-core/src/main/java/rpg/core/progression/ProgressView.java`: Record mit `level`,
      `xpInLevel`, `xpForNextLevel`, `atMaxLevel` — `atMaxLevel` als **eigenes Feld**, damit kein
      Empfänger die Unterscheidung aus `xpForNextLevel == 0` erfinden muss (FR-028, FR-051)
- [X] T007 [P] `rpg-core/src/main/java/rpg/core/progression/XpSource.java`: `MOB_KILL`,
      `ZONE_OBJECTIVE`, `ADMIN` mit den Eigenschaften „wird geteilt" und „darf senken" (FR-007,
      FR-048)
- [X] T008 [P] `rpg-core/src/main/java/rpg/core/progression/XpRejection.java`: `NONE`,
      `INVALID_AMOUNT`, `SESSION_NOT_READY`, `AT_MAX_LEVEL`, `UNKNOWN_CHARACTER`
- [X] T009 [P] `rpg-core/src/main/java/rpg/core/progression/LevelUp.java`: Record aus
      `previousLevel`, `newLevel`, `xpInLevel`, `discarded` (FR-017 bis FR-019, FR-049)
- [X] T010 [P] `rpg-core/src/main/java/rpg/core/progression/XpResult.java`: Record aus `granted`,
      `discarded`, `levelUp`, `rejection` mit `rejected()` und `leveledUp()`. **Rückgabewert statt
      Ausnahme** — die Vergabe läuft im Kampfpfad, und eine Ausnahme je abgelehntem Betrag wäre eine
      Objekterzeugung samt Stacktrace genau dort (FR-062)
- [X] T011 `rpg-core/src/main/java/rpg/core/progression/XpCurve.java`: Schwellen als `long[]`,
      Index 0 = Level 2. `maxLevel()` = Länge + 1, aus der Tabelle abgeleitet, **nicht** als
      Konstante (FR-001, FR-004). Array statt `Map`, weil die Kurve bei jedem Aufstieg gelesen wird
      und eine Karte ein `Integer` je Abfrage im Kampfpfad wäre
- [X] T012 In derselben Datei: Validierung beim Bauen mit Abbruch beim **ersten** Verstoss und
      benennender Meldung — lückenlos ab 2, jeder Wert ≥ 1, streng monoton steigend, mindestens
      Level 2, Schlüssel numerisch (FR-002, FR-003)
- [X] T013 [P] `rpg-core/src/main/java/rpg/core/progression/LevelGrowth.java`: Zuwachs je Level als
      `double[]` über alle acht Attribute, **Null erlaubt**; nicht endliche und negative Werte
      abgelehnt (FR-022a)
- [X] T014 [P] `rpg-core/src/main/java/rpg/core/progression/MobXpProvider.java`: Schnittstelle
      `OptionalLong xpFor(String mobTypeKey)`, die B10 später ersetzt (FR-009)
- [X] T015 [P] `rpg-core/src/main/java/rpg/core/progression/ProximityCheck.java`: Schnittstelle
      `inRange(WorldPoint origin, …)` — nimmt den Ort als **Wert**, nicht die Id des toten Wesens.
      Schreibt die Treffer in ein übergebenes Feld von mindestens `party.max-size` Plätzen und gibt
      deren Anzahl zurück; kein neues Feld je Aufruf, weil sie im Kampfpfad läuft (FR-044)
- [X] T016 `rpg-core/src/main/java/rpg/core/progression/ProgressionConfig.java`: validierte
      `progression.yml` mit Kurve, Wachstum, Mob-XP, Party- und Bündelungswerten (FR-005)
- [X] T017 `rpg-core/src/main/java/rpg/core/progression/ProgressionConfigSchema.java`: Schema mit
      `xp-curve` als **Kartenfeld** und prüfender Bindefunktion; alle acht Wachstumsfelder als
      Pflicht, auch die drei mit Null; Regel `bonus-cap` ≥ `bonus-per-member` (FR-002, FR-005)
- [X] T018 `rpg-plugin/src/main/resources/progression.yml`: Kurve für Level 2 bis 60,
      klassenneutrales Wachstum mit Null bei Angriffs-, Laufgeschwindigkeit und
      Fähigkeiten-Cooldown, `mob-xp` mit `default` und `by-type`, Party- und Bündelungswerte —
      Aufbau wie `combat.yml`. Eine neue Mob-Art ist eine Zeile, keine Codeänderung; das Wachstum ist
      klassenneutral und von B07 ersetzbar (FR-006, FR-009a, FR-022, FR-022b)
- [X] T019 [P] `rpg-core/src/main/java/rpg/core/progression/ProgressionMessageKeys.java`: alle
      Spielertexte als Schlüssel, kein Text im Code (FR-038)
- [X] T020 `rpg-core/src/main/java/rpg/core/progression/Progression.java`: die öffentliche
      Schnittstelle nach [contracts/progression.md](./contracts/progression.md)
- [X] T021 `rpg-persistence/src/main/resources/db/migration/V6_1__character_progress.sql`: Tabelle
      `rpg.character_progress` mit `character_id` als Primär- und Fremdschlüssel
      (`ON DELETE CASCADE`), `level`, `xp_in_level`, `data_version`, `revision`, `updated_at` und
      den zwei Checks. Kommentar hält fest, **warum** eigene Tabelle und **warum kein**
      Maximallevel-Check (FR-053, FR-057)
- [X] T022 [P] `rpg-core/src/main/java/rpg/core/progression/CharacterProgressRepository.java`:
      Schnittstelle zum Laden und Markieren des Fortschrittsstands, plus `CharacterProgress` als
      gespeicherte Form. Liegt in **core**, nicht in `rpg-persistence` — `DefaultProgression`
      braucht sie, und die Richtung `plugin → persistence → core` erlaubt nichts anderes.
      *Bei der Umsetzung präzisiert:* nicht nach `rpg/core/persistence/`, sondern in das eigene
      Blockpaket — genau wie `CharacterResourcesRepository` in `rpg/core/stats/` (B04). In
      `rpg/core/persistence/` liegen die Aggregate von B02 selbst
- [X] T023 `rpg-persistence/src/main/java/rpg/persistence/progression/JdbcCharacterProgressRepository.java`:
      Schreiber, der sich im Flush-Zyklus registriert; Laden über den Login-Pool (FR-054, FR-056)
- [X] T024 `rpg-core/src/main/java/rpg/core/progression/DefaultProgression.java`: Gerüst mit
      `load`, `release` und der Zustandsablage je Charakter. `release` gibt Stand, offenes Bündel
      und Party-Mitgliedschaft frei — die Zusage gegen Lecks
- [X] T024a [P] `rpg-core/src/main/java/rpg/core/progression/WorldPoint.java`: bukkitfreier Ort aus
      Welt-Id und drei Koordinaten, mit `distanceSquaredTo` ohne Wurzel; verschiedene Welten ergeben
      unendlich statt einer Ausnahme, damit der Reichweitenvergleich die Weltprüfung mit erledigt.
      Nötig, weil `rpg-core` keinen Ortstyp hat und `CombatDeathEvent` **keinen Ort trägt**
      (FR-041a, FR-045)
- [X] T024b `rpg-persistence/src/main/java/rpg/persistence/progression/ProgressionModule.java`:
      Modulgerüst nach B01-Vertrag mit Abhängigkeiten auf Persistenz, Session und Stats. **Hier und
      nicht erst in Phase 12**, weil T067 und T024c es brauchen
- [X] T024c `rpg-persistence/src/main/java/rpg/persistence/progression/ProgressSessionAttachment.java`:
      hängt über `sessions.lifecycle().addAttachment(...)` am Sitzungslebenszyklus — lädt den
      Fortschrittsstand bei `onSessionOpened` und ruft bei `onSessionClosing` `release` sowie
      `PartyRegistry.onSessionEnded`. Muster von `StatSessionAttachment` in `StatsModule` (B04).
      **Ohne diese Aufgabe sind `load` und `release` toter Code**: kein Charakter bekäme je einen
      Stand, und die Leck-Zusage wäre unbelegt (FR-034, FR-058)
- [X] T024d [P] `rpg-persistence/src/test/java/rpg/persistence/progression/ProgressSessionLifecycleTest.java`:
      Sitzungsstart lädt den Stand, Sitzungsende gibt ihn frei und entfernt den Spieler aus seiner
      Party; nach 800 Sitzungszyklen bleibt kein Eintrag zurück (FR-034, FR-058)

**Checkpoint**: Typen, Konfiguration, Tabelle **und die Sitzungsanbindung** stehen. User Stories
können beginnen.

---

## Phase 3: User Story 2 — Die XP-Kurve ist Konfiguration, kein Code (Priority: P1)

**Ziel**: Eine fehlerhafte `progression.yml` hält den Start an und nennt, was zu korrigieren ist.

**Unabhängiger Test**: Fünf defekte Kurven und zwei defekte Party-Werte, je eine benennende Meldung,
kein Start.

**Warum vor User Story 1**: US1 kann ohne validierte Kurve nichts prüfen — jede Aussage über einen
Levelaufstieg setzt eine Kurve voraus, der man trauen kann. Die Reihenfolge der Phasen weicht daher
bewusst von der Prioritätsreihenfolge in der Spezifikation ab, wie in B05 zwischen US5 und US6.

### Tests für User Story 2

- [X] T025 [P] [US2] `rpg-core/src/test/java/rpg/core/progression/XpCurveTest.java`: gültige Kurve
      lädt, `maxLevel()` ergibt sich aus dem höchsten Schlüssel, `thresholdFor` liefert die
      erwarteten Werte
- [X] T026 [P] [US2] `rpg-core/src/test/java/rpg/core/progression/ProgressionConfigSchemaTest.java`:
      Level 37 fehlt → Abbruch, Meldung nennt `level 37 is missing` (SC-003)
- [X] T027 [P] [US2] Im selben Test: Level 12 auf 0 → Abbruch, Meldung nennt
      `level 12 must be positive`
- [X] T028 [P] [US2] Im selben Test: Level 20 kleiner als Level 19 → Abbruch, Meldung nennt beide
      Level und beide Werte
- [X] T029 [P] [US2] Im selben Test: kein Level 2 → Abbruch mit
      `must define at least level 2`
- [X] T030 [P] [US2] Im selben Test: nichtnumerischer Kurvenschlüssel → Abbruch, Meldung nennt den
      Schlüssel
- [X] T031 [P] [US2] Im selben Test: `bonus-cap` unter `bonus-per-member` → Abbruch. Eine Obergrenze
      unter dem Einzelaufschlag würde `bonus-per-member` bedeutungslos machen — besser ein
      Startfehler als eine Zahl, die nichts tut
- [X] T032 [P] [US2] Im selben Test: ein fehlendes Wachstumsfeld → Abbruch, **nicht** stillschweigend
      Null. Sonst wäre „wächst nicht" nicht von „Zeile vergessen" zu unterscheiden
- [X] T033 [US2] `rpg-plugin/src/test/java/rpg/plugin/ShippedProgressionConfigTest.java`: die
      **mitgelieferte** `progression.yml` wird als Ressource geladen und besteht das Schema. Ein
      Block, dessen Standardkonfiguration das eigene Schema verletzt, startet auf keinem Server.
      *Bei der Umsetzung verschoben:* nach `rpg-plugin`, weil die Ressource nur dort im Klassenpfad
      liegt — in `rpg-core` wäre der Test eine leere Zusicherung
- [X] T034 [P] [US2] `rpg-core/src/test/java/rpg/core/progression/NoHardcodedMaxLevelTest.java`:
      Quellenscan schlägt fehl, wenn eine Maximallevel-Konstante im Code auftaucht (FR-004)

**Checkpoint**: Balancing ist Konfiguration, und ein Fehler darin ist ein Startfehler.

---

## Phase 4: User Story 1 — Ein Kill gibt Erfahrung, und irgendwann steigt das Level (Priority: P1) 🎯 MVP

**Ziel**: XP kommt an, das Level steigt, die Attribute wachsen mit — über den B04-Erweiterungspunkt,
nicht durch direktes Schreiben.

**Unabhängiger Test**: Ein Charakter auf Level 1 erhält die Schwelle für Level 2 und steht danach auf
Level 2 mit den erwarteten Attributen und vollen Ressourcen.

### Tests für User Story 1

- [X] T035 [P] [US1] `rpg-core/src/test/java/rpg/core/progression/LevelUpTest.java`: Level 1, 0 XP,
      Schwelle 100 → +100 XP ergibt Level 2 mit 0 Überschuss (SC-001)
- [X] T036 [P] [US1] Im selben Test: Schwellen 100 und 120 → +250 XP ergibt Level 3 mit 30
      Überschuss (FR-018, FR-019)
- [X] T037 [P] [US1] Im selben Test: Aufstieg über drei Level erzeugt **eine** Neuberechnung und
      **ein** `LevelUpEvent(1, 4)` — nicht drei (FR-021, FR-023, SC-009)
- [X] T038 [P] [US1] Im selben Test: der Tod eines Charakters verändert weder Level noch XP
      (FR-024)
- [X] T039 [P] [US1] `rpg-core/src/test/java/rpg/core/progression/XpAmountTest.java`: ein Mob mit 40
      konfigurierten XP gibt auf Level 1 und auf Level 59 denselben Betrag (FR-010, SC-002)
- [X] T040 [P] [US1] Im selben Test: ein Mob ohne eigenen Eintrag bekommt den Standardbetrag und
      erzeugt **eine** Warnung je Mob-Art, nicht je Kill (FR-060)
- [X] T041 [P] [US1] `rpg-core/src/test/java/rpg/core/progression/LevelStatContributorTest.java`:
      der Beitrag ist `perLevel[i] * (level - 1)`; auf Level 1 trägt er nichts bei, weil der
      Level-1-Wert der Basiswert aus B04 **ist** (FR-020)
- [X] T042 [P] [US1] Im selben Test: der Zuwachs läuft gegen die Caps aus B04 statt sie zu
      überschreiten, und B06 prüft sie nicht selbst nach (FR-022c)
- [X] T043 [P] [US1] Im selben Test: ohne Fortschrittsstand trägt der Beitragende nichts bei und
      wirft nicht
- [X] T044 [P] [US1] `rpg-core/src/test/java/rpg/core/progression/LevelUpResourcesTest.java`: 12 von
      100 Leben, Aufstieg hebt das Maximum auf 110 → 110 von 110 (FR-021a, SC-019)
- [X] T045 [P] [US1] Im selben Test: Aufstieg über drei Level füllt **einmal** auf, nicht dreimal
- [X] T046 [P] [US1] Im selben Test: die Reihenfolge wird geprüft — erst Neuberechnung, dann
      Auffüllen. Umgekehrt würde gegen das **alte** Maximum gefüllt, und der Fehler läge bei jedem
      Aufstieg nur um wenige Prozent daneben (FR-021b)
- [X] T047 [P] [US1] `rpg-core/src/test/java/rpg/core/progression/XpRejectionTest.java`: Betrag 0,
      negativ und nicht endlich werden abgelehnt und protokolliert, niemals als Abzug gedeutet
      (FR-015)
- [X] T048 [P] [US1] Im selben Test: ein Charakter ohne bereite Sitzung erhält nichts, still und
      ohne Ausnahme (FR-014)
- [X] T049 [P] [US1] Im selben Test: XP geht an den im Moment des Ereignisses **aktiven** Charakter,
      nie ans Konto und nie an einen inaktiven Charakter (FR-016)
- [X] T050 [P] [US1] `rpg-core/src/test/java/rpg/core/progression/GrantFaultBarrierTest.java`: eine
      Ausnahme in der Vergabe bleibt auf den Charakter begrenzt und lässt den laufenden Vorgang
      unangetastet (FR-059)

### Implementierung für User Story 1

- [X] T051 [US1] `DefaultProgression.grant`: Eingangsprüfung, Sitzungsprüfung, Zuwachs,
      Änderungsmarkierung (FR-007, FR-014, FR-015)
- [X] T052 [US1] In derselben Datei: die Aufstiegsschleife nach der verbindlichen Schrittfolge aus
      [data-model.md](./data-model.md) Abschnitt 3
- [X] T053 [US1] `rpg-core/src/main/java/rpg/core/progression/LevelStatContributor.java`:
      `BaseStatContributor` nach ADR-013. **Nicht** `StatEngine.apply` mit `SourceKind.LEVEL` — der
      Grund steht in [research.md](./research.md) Entscheidung 1 (FR-020)
- [X] T054 [US1] In `DefaultProgression`: Registrierung des Beitragenden bei `StatEngine` beim Start
- [X] T055 [US1] In derselben Datei: Auffüllen über `StatEngine.restoreResources` mit
      `ResourcePool.full`, **nach** der Neuberechnung (FR-021a, FR-021b)
- [X] T056 [US1] `rpg-core/src/main/java/rpg/core/progression/LevelUpEvent.java` plus
      Veröffentlichung genau einmal je Aufstieg, mit `byAdmin` zur Unterscheidung (FR-023)
- [X] T057 [US1] `rpg-core/src/main/java/rpg/core/progression/ConfigMobXpProvider.java`: Beträge aus
      `progression.yml`, Standardbetrag als Rückfall, eine Warnung je Art (FR-009, FR-060)
- [X] T058 [US1] `rpg-platform/src/main/java/rpg/platform/progression/ProgressionDeathListener.java`:
      hängt am `CombatDeathEvent` aus B05 und ruft die Verteilung (FR-008)
- [X] T059 [US1] In `DefaultProgression`: Fehlerbarriere um die Vergabe nach dem Muster von B01s
      `ModuleFaultBarrier` (FR-059)

**Checkpoint**: Der MVP steht — ein Kill gibt XP, das Level steigt, die Attribute wachsen.

---

## Phase 5: User Story 3 — Erfahrung sammeln belastet die Datenbank nicht (Priority: P1)

**Ziel**: 1000 XP-Ereignisse je Sekunde, null Datenbankzugriffe.

**Unabhängiger Test**: Zählendes Repository, 1000 Ereignisse, Zähler bleibt bei 0.

### Tests für User Story 3

- [X] T060 [P] [US3] `rpg-core/src/test/java/rpg/core/progression/NoDatabaseAccessTest.java`: 1000
      XP-Ereignisse in einer Sekunde → Zugriffszähler bleibt 0 (FR-054, SC-004)
- [X] T061 [P] [US3] Im selben Test: der Charakter ist danach als änderungsbedürftig markiert
- [X] T062 [P] [US3] Im selben Test: die Levelanforderungsabfrage erzeugt ebenfalls keinen Zugriff
      (FR-026, SC-011)
- [X] T063 [P] [US3] `rpg-core/src/test/java/rpg/core/progression/MemoryAuthoritativeTest.java`:
      laufen Speicher- und Datenbankstand auseinander, gilt der Speicherstand (FR-055)
- [X] T064 [P] [US3] `rpg-core/src/test/java/rpg/core/progression/NoAllocationTest.java`: 10 000
      aufeinanderfolgende XP-Ereignisse erzeugen kein vermeidbares Objekt je Ereignis (FR-062,
      SC-005)

### Implementierung für User Story 3

- [X] T065 [US3] In `DefaultProgression`: Änderungsmarkierung über den `WriteBehindBuffer` aus B02
      statt eines Schreibvorgangs (FR-054)
- [X] T066 [US3] In `JdbcCharacterProgressRepository`: Registrierung im Flush-Zyklus für
      `AggregateType.CHARACTER_PROGRESS`
- [X] T067 [US3] `rpg-persistence/src/main/java/rpg/persistence/progression/ProgressionModule.java`:
      Anbindung an `SessionHandover` aus B02, damit der Stand geschrieben ist, **bevor** die Sitzung
      als beendet gilt (FR-056)

**Checkpoint**: Fortschritt ist frei von Datenbankzugriffen im Spielpfad.

---

## Phase 6: User Story 4 — Level öffnen und sperren Inhalte (Priority: P2)

**Ziel**: B08, B09 und B11 können Inhalte an ein Level binden, ohne den Fortschritt zu kennen.

**Unabhängiger Test**: Ein Charakter auf Level 12 erfüllt 10 und 12, nicht 13.

### Tests für User Story 4

- [X] T068 [P] [US4] `rpg-core/src/test/java/rpg/core/progression/LevelRequirementTest.java`: Level
      12 erfüllt die Anforderungen 10 und 12, nicht 13 (FR-025)
- [X] T069 [P] [US4] Im selben Test: ein Charakter ohne Fortschrittsstand antwortet „nicht erfüllt"
      plus Protokolleintrag — **keine** Ausnahme. Das ist die Zusage, auf die sich drei Blöcke
      verlassen: eine Abfrage bricht nie einen Ablauf ab (FR-027)
- [X] T070 [P] [US4] `rpg-core/src/test/java/rpg/core/progression/ProgressViewTest.java`: `level`,
      `xpInLevel`, `xpForNextLevel` und `atMaxLevel` stimmen, ohne dass der Empfänger rechnet
      (FR-028)

### Implementierung für User Story 4

- [X] T071 [US4] In `DefaultProgression`: `meetsLevel`, `levelOf`, `progressOf` und `maxLevel` —
      alle ohne Datenbankzugriff und ohne Neuberechnung (FR-025 bis FR-028)

**Checkpoint**: Fünf Folgeblöcke haben ihre Abfrage.

---

## Phase 7: User Story 5 — Spieler können sich zu einer Party zusammentun (Priority: P2)

**Ziel**: Einladen, beitreten, verlassen, auflösen — als reine Zustandsübergänge ohne Speicherung.

**Unabhängiger Test**: Alle Übergänge aus [data-model.md](./data-model.md) Abschnitt 6, serverfrei.

### Tests für User Story 5

- [X] T072 [P] [US5] `rpg-core/src/test/java/rpg/core/progression/PartyRegistryTest.java`:
      Einladung und Annahme machen zwei Spieler zu Mitgliedern derselben Party (FR-030)
- [X] T073 [P] [US5] Im selben Test: Annahme nach Ablauf der Frist → `INVITE_EXPIRED`, und der
      Verfall wird **beim Zugriff** geprüft, nicht von einer Aufgabe (FR-031)
- [X] T074 [P] [US5] Im selben Test: Annahme bei bestehender Mitgliedschaft → `ALREADY_IN_PARTY`
      (FR-032)
- [X] T075 [P] [US5] Im selben Test: Beitritt in eine volle Party → `PARTY_FULL` (FR-033)
- [X] T076 [P] [US5] Im selben Test: Einladen oder Entfernen ohne Anführerrolle → `NOT_LEADER`
      (FR-029b)
- [X] T077 [P] [US5] Im selben Test: Einladung an sich selbst → `SELF_INVITE`; Einladung an einen
      Spieler ohne bereite Sitzung → `TARGET_NOT_READY`
- [X] T078 [P] [US5] Im selben Test: verliert der Anführer einer Dreier-Party die Verbindung, ist
      das **dienstälteste** verbleibende Mitglied Anführer, und die Party ist zu keinem Zeitpunkt
      führungslos (FR-029c)
- [X] T079 [P] [US5] Im selben Test: eine Rollenübergabe erzeugt **zwei** Ereignisse — erst `LEFT`
      oder `REMOVED`, dann `LEADER_CHANGED`
- [X] T080 [P] [US5] Im selben Test: verlässt das letzte Mitglied, ist `partyCount()` gleich 0 und
      kein Restzustand bleibt (FR-035)
- [X] T081 [P] [US5] Im selben Test: eine Party mit einem Mitglied ist zulässig und verhält sich in
      der Verteilung wie keine Party
- [X] T082 [P] [US5] `rpg-core/src/test/java/rpg/core/progression/PartyNoTaskTest.java`: für
      Einladungen und deren Verfall läuft keine geplante Aufgabe (FR-031, FR-061)

### Implementierung für User Story 5

- [X] T083 [US5] `rpg-core/src/main/java/rpg/core/progression/Party.java`: genau **ein** Anführer
      (FR-029a), Mitglieder und **`joinedAt` je Mitglied** — ohne den Zeitpunkt ist „dienstältestes
      verbleibendes Mitglied" nicht entscheidbar. Feste Arrays statt Sammlungen, weil die
      Mitgliederliste bei jedem Kill gelesen wird
- [X] T084 [P] [US5] `rpg-core/src/main/java/rpg/core/progression/PartyInvite.java`: Einlader,
      Eingeladener, Zeitstempel; Verfall lazy abgeleitet (FR-031)
- [X] T085 [P] [US5] `rpg-core/src/main/java/rpg/core/progression/PartyRejection.java`: die acht
      Ablehnungsgründe als Aufzählung, damit B14 sie auf Message-Schlüssel abbildet (FR-038)
- [X] T086 [US5] `rpg-core/src/main/java/rpg/core/progression/PartyRegistry.java`: Gründen,
      Einladen, Annehmen, Ablehnen, Verlassen, Entfernen, Rollenübergabe, Auflösen (FR-029 bis
      FR-035). Reiner Laufzeitzustand — kein Repository, keine Tabelle, keine Migration (FR-029).
      `invite` ohne bestehende Party gründet implizit eine
- [X] T087 [US5] `rpg-core/src/main/java/rpg/core/progression/PartyChangedEvent.java` plus
      Veröffentlichung bei jeder Änderung; bei `DISBANDED` leere Mitgliederliste und der letzte
      Anführer (FR-036)
- [X] T088 [US5] In `PartyRegistry`: `onSessionEnded` als einziger Weg, auf dem eine Party durch
      etwas anderes als eine Spielerhandlung schrumpft (FR-034)

**Checkpoint**: Partys entstehen und vergehen korrekt — noch ohne Wirkung auf XP.

---

## Phase 8: User Story 6 — Gemeinsam spielen lohnt sich (Priority: P2)

**Ziel**: Das Fünf-Schritt-Verfahren, das die B05-Regel „anteilig nach Schadensanteil" klammert
statt ersetzt.

**Unabhängiger Test**: Eine Schadensaufteilung mit drei Beteiligten gegen einen Mob mit bekanntem
Betrag — jedes Ergebnis vollständig vorhersagbar.

### Tests für User Story 6

- [X] T089 [P] [US6] `rpg-core/src/test/java/rpg/core/progression/XpDistributorTest.java`: ein
      Beitragender ohne Party mit 100 % Anteil erhält bei 100 Mob-XP genau 100
- [X] T090 [P] [US6] Im selben Test: A mit 60 % und B mit 40 %, beide ohne Party → 60 und 40
      (FR-046)
- [X] T091 [P] [US6] Im selben Test: A und B in Party mit zusammen 60 %, beide in Reichweite,
      Bonus 10 %, dazu C mit 40 % → A und B je 33, C genau 40 (FR-039 bis FR-043, SC-006)
- [X] T092 [P] [US6] Im selben Test: B ausserhalb der Reichweite → A erhält 60 ohne Bonus, B genau 0
      (FR-042, SC-007)
- [X] T093 [P] [US6] Im selben Test: ein Party-Mitglied ohne Schadensanteil, aber in Reichweite,
      erhält seinen Anteil (FR-041)
- [X] T094 [P] [US6] Im selben Test: ein Mitglied in einer anderen Welt gilt nie als in Reichweite
      (FR-045)
- [X] T095 [P] [US6] Im selben Test: der Anteil eines Mitglieds auf Maximallevel verfällt still und
      wird **nicht** umverteilt (FR-052)
- [X] T096 [P] [US6] Im selben Test: leere Schadensaufteilung (Umgebungstod) → niemand erhält XP,
      und das ist kein Fehler (FR-012)
- [X] T097 [P] [US6] Im selben Test: ein gestorbener **Spieler** erzeugt keine XP (FR-013)
- [X] T098 [P] [US6] Im selben Test: die Schadensanteile werden aus dem Todesereignis übernommen und
      **nie** neu berechnet (FR-011)
- [X] T099 [P] [US6] `rpg-core/src/test/java/rpg/core/progression/DistributionRoundingTest.java`:
      für jede Partygrösse von 1 bis zur Obergrenze übersteigt die Summe der vergebenen XP den
      Mobbetrag höchstens um den Bonus. Abgerundet, Rest bleibt liegen — Aufrunden hätte bei einer
      Fünfer-Party bis zu vier XP je Kill aus dem Nichts erzeugt (FR-047, SC-013)
- [X] T100 [P] [US6] `rpg-core/src/test/java/rpg/core/progression/NoProximityProviderTest.java`: ohne
      registrierten Anbieter gilt allein der Beitragende als in Reichweite — die Teilung fällt auf
      das Verhalten ohne Party zurück, statt XP zu verschenken oder zu verschlucken (FR-044)

### Implementierung für User Story 6

- [X] T101 [US6] `rpg-core/src/main/java/rpg/core/progression/XpDistributor.java`: die fünf Schritte
      aus [data-model.md](./data-model.md) Abschnitt 4. Eine Party gilt dabei als **ein**
      Beitragender, dessen Anteil die Summe der Mitgliederanteile ist — ohne diese Klammer gäbe es
      zwei Verteilungsregeln für dieselbe XP (FR-039, FR-040, FR-041, FR-042, FR-046)
- [X] T102 [US6] In derselben Datei: Bonus als `min(bonusPerMember * (inRange - 1), bonusCap)`, auf
      den Party-Anteil **vor** der Teilung; bei einem Mitglied in Reichweite ist der Bonus 0
      (FR-043)
- [X] T103 [US6] `rpg-platform/src/main/java/rpg/platform/progression/PaperProximityCheck.java`:
      Entfernung zum **gestorbenen Gegner** über `WorldPoint`, andere Welt nie in Reichweite
      (FR-041a, FR-045)
- [X] T104 [US6] In `ProgressionDeathListener`: Verteiler anbinden und den Ort des Gegners **im
      Todesereignis** als `WorldPoint` lesen — dort ist er sicher gültig. Ein späteres Nachschlagen
      über `Bukkit.getEntity` gelingt nur, solange das Wesen nicht entfernt ist

**Checkpoint**: Gemeinsames Spielen ist rechnerisch nie schlechter als allein.

---

## Phase 9: User Story 7 — Level 60 ist das Ende, und es fühlt sich nicht wie ein Fehler an (Priority: P3)

**Ziel**: Auf Maximallevel verfällt XP still — kein Fehler, kein Überlauf, kein Protokoll-Spam.

**Unabhängiger Test**: 10 000 XP-Ereignisse auf Maximallevel, Stand unverändert.

### Tests für User Story 7

- [X] T105 [P] [US7] `rpg-core/src/test/java/rpg/core/progression/MaxLevelTest.java`: 10 000
      XP-Ereignisse auf Maximallevel lassen den Stand unverändert (FR-049)
- [X] T106 [P] [US7] Im selben Test: kein `LevelUpEvent`, kein `ProgressChangedEvent`, keine
      Protokollzeile je Ereignis (FR-050, SC-008)
- [X] T107 [P] [US7] Im selben Test: `progressOf` meldet `atMaxLevel = true` und
      `xpForNextLevel = 0` — nicht „0 % zum nächsten Level" (FR-051)
- [X] T108 [P] [US7] Im selben Test: ein Ereignis, das das Maximallevel überschreitet, endet exakt
      auf Maximallevel; der Rest steht in `discarded` und erzeugt keinen Überlauf

### Implementierung für User Story 7

- [X] T109 [US7] In `DefaultProgression`: Verfallslogik auf Maximallevel und `atMaxLevel` in der
      Lesesicht (FR-049 bis FR-051)

**Checkpoint**: Das Ende der Progression ist ein Zustand, kein Fehlerfall.

---

## Phase 10: User Story 8 — Weitere Erfahrungsquellen kommen ohne neue Vergabelogik dazu (Priority: P3)

**Ziel**: B09 schreibt XP über denselben Eingangspunkt zu wie ein Mob-Kill.

**Unabhängiger Test**: Ein Betrag mit einer anderen Quellenangabe durchläuft dieselben Regeln.

### Tests für User Story 8

- [X] T110 [P] [US8] `rpg-core/src/test/java/rpg/core/progression/XpSourceTest.java`: eine Quelle
      `ZONE_OBJECTIVE` durchläuft Kurve, Maximallevel und Persistenz genau wie ein Mob-Kill
      (FR-007, FR-015)
- [X] T111 [P] [US8] Im selben Test: eine ausdrücklich nicht geteilte Quelle geht allein an den
      genannten Charakter, auch wenn er in einer Party ist (FR-048)
- [X] T112 [P] [US8] `rpg-core/src/test/java/rpg/core/progression/SingleGrantPointTest.java`:
      Quellenscan belegt, dass es genau **einen** Eingangspunkt für XP gibt — keine zweite Stelle,
      an der Fortschritt entsteht (FR-007)

### Implementierung für User Story 8

- [X] T113 [US8] In `XpDistributor` und `DefaultProgression`: die Teilung hängt an der Quelle, nicht
      am Aufrufer (FR-048)

**Checkpoint**: B09 braucht für Zonen-Ziele keine Änderung in B06.

---

## Phase 11: Querschnitt — Fortschrittsmeldung und Verwaltungseingriff

**Zweck**: Zwei Anforderungsgruppen ohne eigene User Story: die Bündelung für B13 (FR-023a bis
FR-023c) und der Verwaltungseingriff für B14 (FR-024a bis FR-024c). Beide betreffen jede Story und
gehören deshalb nicht in eine einzelne.

### Tests

- [X] T114 [P] `rpg-core/src/test/java/rpg/core/progression/ProgressAggregatorTest.java`: 100
      Gewinne innerhalb eines Fensters erzeugen **ein** `ProgressChangedEvent`, dessen `gained` der
      Summe entspricht (FR-023a, SC-018)
- [X] T115 [P] Im selben Test: kreuzt ein Gewinn bei offenem Bündel eine Schwelle, kommt **erst**
      das `ProgressChangedEvent` mit dem alten Level, **dann** das `LevelUpEvent`. Kein Ereignis mit
      dem alten Level erreicht danach einen Empfänger (FR-023c, SC-020)
- [X] T116 [P] Im selben Test: das Sitzungsende **verwirft** ein offenes Bündel — es ist reine
      Anzeige, und der Empfänger ist weg. Die XP ist dennoch angerechnet
- [X] T117 [P] Im selben Test: nach `release` ist die Zahl offener Eimer 0 — kein Leck
- [X] T118 [P] Im selben Test: das Fenster wird nie von einer Aufgabe geschlossen, sondern vom
      nächsten Ereignis, einem Aufstieg oder dem Sitzungsende (FR-061)
- [X] T119 [P] `rpg-core/src/test/java/rpg/core/progression/AdminProgressTest.java`: ein Eingriff,
      der das Level senkt, erscheint im Audit-Log mit ausführendem Betreiber, Charakter, altem und
      neuem Stand (FR-024b, SC-021)
- [X] T120 [P] Im selben Test: ein gesenktes Level füllt Leben und Mana **nicht** auf, und ein über
      dem neuen Maximum liegender Wert wird darauf begrenzt (FR-024c)
- [X] T121 [P] Im selben Test: der Eingriff löst dieselbe Neuberechnung und dieselben Ereignisse aus
      wie ein natürlicher Aufstieg, mit `byAdmin = true` (FR-024c)
- [X] T122 [P] Im selben Test: ein Level ausserhalb von 1 bis `maxLevel()` → `INVALID_AMOUNT`, kein
      Eingriff
- [X] T123 [P] Im selben Test: `ADMIN` ist die **einzige** Quelle, die senken darf; jede andere
      Quelle mit negativer Wirkung wird abgelehnt (FR-024, FR-024a)

### Implementierung

- [X] T124 `rpg-core/src/main/java/rpg/core/progression/ProgressAggregator.java`: ein Eimer je
      Charakter mit Öffnungszeitpunkt und Summe, nach dem Muster von `DamageAggregator` in B05
      (FR-023a, FR-061)
- [X] T125 `rpg-core/src/main/java/rpg/core/progression/ProgressChangedEvent.java` plus
      Veröffentlichung; trägt Level und beide XP-Werte mit, damit B13 nicht zurückfragt (FR-023a,
      FR-023b)
- [X] T126 In `DefaultProgression`: die Reihenfolgezusage — offenes Bündel vor dem
      Aufstiegsereignis ausliefern, Fenster danach zurücksetzen (FR-023c)
- [X] T127 In `DefaultProgression`: `setProgress` mit `actorId`, Senken eingeschlossen, samt
      Begrenzung der Ressourcen über `ResourcePool.clampedTo` (FR-024a, FR-024c)
- [X] T128 In `DefaultProgression`: Audit-Eintrag über `AuditLogRepository` aus B02 bei jedem
      Verwaltungseingriff (FR-024b)

**Checkpoint**: B13 hat seine Meldung, B14 seinen Eingriff.

---

## Phase 12: Polish, Verdrahtungsnachweis und Persistenznachweis

**Zweck**: Der Block ist erst fertig, wenn er im Plugin verdrahtet ist und die Persistenz gegen eine
echte Datenbank belegt ist.

### Verdrahtung

- [X] T129 `rpg-persistence/src/main/java/rpg/persistence/progression/ProgressionModule.java`:
      das in T024b angelegte Modul fertigstellen — Repository aufbauen, Dienste über
      `context.registry().registerService(...)` veröffentlichen, Abschaltreihenfolge
- [X] T130 `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java`: `ProgressionModule` in `modules()`
      ergänzen und `progression.yml` beim Start über `saveResource` schreiben (ADR-012)
- [X] T131 In derselben Datei: `assembleProgressionLayer()` — Beitragenden bei `StatEngine`
      registrieren, Reichweitenprüfung und Mob-XP-Anbieter setzen, Todeslistener anmelden
- [X] T132 `rpg-plugin/src/test/java/rpg/plugin/FullBootstrapTest.java`: `Progression` und
      `PartyRegistry` sind über `plugin.registry().findService(...)` auffindbar, und der Bootstrap
      bleibt im Zeitbudget (ADR-012)

### Persistenz gegen echtes PostgreSQL

- [X] T133 [P] `rpg-persistence/src/test/java/rpg/persistence/progression/CharacterProgressRoundTripTest.java`:
      Schreiben und Laden über Testcontainers, Revisionszähler steigt (Prinzip VII verbietet Mocks)
- [X] T134 [P] `rpg-persistence/src/test/java/rpg/persistence/progression/ProgressMigrationTest.java`:
      ein Stand älterer `data_version` wird ohne Verlust migriert; ein Stand aus einer künftigen
      Version wird **abgelehnt**, nicht falsch gedeutet (FR-057, SC-016)
- [X] T135 [P] In derselben Testklasse: ein Charakter ohne Zeile beginnt auf Level 1 mit 0 XP
      (FR-058)
- [X] T136 [P] `rpg-persistence/src/test/java/rpg/persistence/progression/ProgressCascadeTest.java`:
      der gelöschte Charakter nimmt seine Fortschrittszeile mit (`ON DELETE CASCADE`) — B02 muss
      nicht wissen, dass B06 existiert
- [X] T137 [P] `rpg-persistence/src/test/java/rpg/persistence/progression/CurveRetuningTest.java`:
      eine nachträglich **verdoppelte** Kurve senkt bei keinem bestehenden Charakter das Level
      (FR-053a, SC-017)
- [X] T138 [P] In derselben Testklasse: eine nachträglich **gesenkte** Kurve mit `xp_in_level` über
      der neuen Schwelle wird beim Laden regulär in Aufstiege umgesetzt, nicht als Fehler behandelt
- [X] T139 [P] `rpg-persistence/src/test/java/rpg/persistence/progression/SessionEndFlushTest.java`:
      der Stand ist geschrieben, bevor die Sitzung als beendet gilt (FR-056)

### Querschnittsnachweise

- [X] T140 [P] `rpg-core/src/test/java/rpg/core/progression/NoScheduledTaskTest.java`: die
      Aufgabenanzahl des Schedulers ist bei 1, 50 und 200 Spielern identisch (FR-061, SC-012)
- [X] T141 [P] `rpg-core/src/test/java/rpg/core/progression/NoBukkitInCoreTest.java`: Quellenscan
      über `rpg-core/src/main` schlägt fehl bei jedem Bukkit-Import, Testpfade ausgenommen
      (Prinzip III)
- [X] T142 [P] `rpg-platform/src/test/java/rpg/platform/progression/VanillaXpUntouchedTest.java`: die
      Vanilla-Erfahrung eines Spielers ist nach 1000 eigenen XP-Ereignissen unverändert (FR-063,
      SC-015)
- [X] T143 [P] `rpg-plugin/src/main/resources/messages.yml`: alle Schlüssel aus
      `ProgressionMessageKeys` ergänzen; ein Test belegt, dass jeder Schlüssel auflösbar ist
      (FR-038)
- [X] T144 `specs/006-progression/quickstart.md` Abschnitte 1 bis 10 durchlaufen; Zahl der
      **übersprungenen** Tests muss 0 sein, nicht nur die der fehlgeschlagenen
- [ ] T145 Abschnitt 11 auf einem echten Paper-Server durchlaufen — Start mit gültiger und mit
      defekter Kurve, Kill, Party, Neustart. Der Neustart belegt beides: Level und XP erhalten,
      keine Party mehr (SC-010). Nach dem Treiberfehler aus ADR-010 gilt: grüne Tests belegen die
      Laufzeitumgebung nicht
- [X] T146 `02-decisions.md`: ADR-015 mit den Umsetzungsentscheidungen aus B06 anlegen, insbesondere
      dem unbenutzten `SourceKind.LEVEL` und der Reihenfolge beim Auffüllen
- [X] T147 `minecraft-rpg-spec/minecraft-rpg-spec/blocks/B06-progression.md`: Status auf
      „Implementiert" mit Datum, Aufgaben- und Testzahl

---

## Dependencies & Execution Order

### Phasenabhängigkeiten

- **Phase 1 Setup**: keine Abhängigkeit.
- **Phase 2 Foundational**: nach Setup. **Blockiert alle Stories** — ohne Typen, Konfiguration und
  Tabelle ist keine Story prüfbar. Enthält bewusst auch das **Modulgerüst** (T024b) und die
  **Sitzungsanbindung** (T024c): T067 in Phase 5 bearbeitet das Modul, und ohne die Anbindung wären
  `load` und `release` toter Code — kein Charakter bekäme je einen Fortschrittsstand. Genau die
  Lücke, für die ADR-012 geschrieben wurde.
- **Phase 3 (US2)** vor **Phase 4 (US1)**: bewusste Abweichung von der Prioritätsreihenfolge. Jede
  Aussage über einen Levelaufstieg setzt eine Kurve voraus, der man trauen kann; US2 stellt genau
  das her. Dieselbe Art Umstellung wie in B05 zwischen US5 und US6.
- **Phase 5 (US3)** nach US1: die Zusage „kein Datenbankzugriff" braucht einen Vergabepfad, der
  etwas tut.
- **Phase 6 (US4)** nach US1: die Abfrage braucht einen Fortschrittsstand.
- **Phase 8 (US6)** nach **Phase 7 (US5)**: ohne Party-Modell gibt es keine Party-Verteilung.
- **Phase 9 (US7)** und **Phase 10 (US8)** nach US1, untereinander unabhängig.
- **Phase 11 Querschnitt** nach US1 und US7: die Bündelung muss den Aufstieg und den Verfall auf
  Maximallevel kennen.
- **Phase 12** zuletzt. T130 bis T132 sind die Bedingung dafür, dass der Block überhaupt als fertig
  gilt (ADR-012) — Modultests allein genügen nicht.

### Innerhalb einer Story

- Tests zuerst, und sie müssen fehlschlagen, bevor implementiert wird.
- Typen vor Regeln, Regeln vor Anbindung, Anbindung vor Verdrahtung.
- `rpg-core` vor `rpg-platform` vor `rpg-plugin` — die Abhängigkeitsrichtung erlaubt nichts anderes.

### Parallelisierbare Abschnitte

- T002 und T003 parallel.
- In Phase 2: T004 bis T010 sowie T013 bis T015, T019, T022 und T024a parallel. **Nicht** parallel:
  T011 und T012 (dieselbe Datei), T016 bis T018 (Schema und Ressource hängen an
  `ProgressionConfig`), T023 nach T021 und T022, T024b nach T023, T024c nach T024 und T024b,
  T024d nach T024c.
- Alle Testaufgaben einer Story mit `[P]` parallel — sie liegen in verschiedenen Dateien oder
  bearbeiten disjunkte Testmethoden derselben Klasse.
- **Nicht** parallel: Aufgaben, die dieselbe Datei anfassen. In Phase 4 betreffen T051, T052, T054,
  T055 und T059 alle `DefaultProgression` und laufen deshalb der Reihe nach.
- Phase 12: T133 bis T143 parallel; T129 bis T132 der Reihe nach.

---

## Implementation Strategy

### MVP zuerst

1. Phase 1 und 2 vollständig.
2. Phase 3 (US2) — der Kurve trauen können.
3. Phase 4 (US1) — **MVP**: ein Kill gibt XP, das Level steigt, die Attribute wachsen.
4. **Anhalten und prüfen**: `./gradlew :rpg-core:test` grün, Abschnitte 1 bis 4 des
   Validierungsleitfadens durchlaufen.

Nach dem MVP ist B07 grundsätzlich anschlussfähig: es braucht `LevelGrowth` und die
Levelabfrage, nicht die Party.

### Inkrementelle Auslieferung

1. Setup + Foundational → Grundlage.
2. + US2 + US1 → MVP, alleinspielbar.
3. + US3 → belastbar unter Last.
4. + US4 → B08, B09 und B11 können anschliessen.
5. + US5 + US6 → gemeinsames Spielen lohnt sich.
6. + US7 + US8 → Endgame sauber, B09 anschlussfähig.
7. + Querschnitt → B13 und B14 können anschliessen.
8. + Phase 12 → verdrahtet, gegen echtes PostgreSQL belegt, auf einem Server gesehen.

---

## Notes

- 151 Aufgaben, davon 86 Tests (57 %). Das Verhältnis ist kein Zufall: Prinzip VII verlangt für jede
  Formel und jede Regel der Domänenschicht einen serverfreien Test, und die Persistenz zusätzlich
  gegen echtes PostgreSQL.
- T024a bis T024d wurden nach der Analyse (`/speckit-analyze`, 2026-08-20) eingefügt und tragen
  deshalb Buchstabensuffixe — dieselbe Schreibweise wie bei den nachgetragenen Anforderungen
  FR-022a und FR-029a, statt 128 Aufgaben umzunummerieren.
- `[P]` heisst andere Datei und keine offene Abhängigkeit.
- Nach jeder Aufgabe oder jeder logischen Gruppe committen.
- An jedem Checkpoint kann angehalten und die Story einzeln geprüft werden.
- Zur Zahl der übersprungenen Tests: MockBukkit meldet Nichtimplementiertes als „skipped" statt als
  Fehler, Testcontainers ohne Docker ebenso. Ein grüner Lauf mit übersprungenen Tests ist kein
  grüner Lauf.
