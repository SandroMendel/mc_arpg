---

description: "Aufgabenliste B12 · Statistiken & Leaderboards"
---

# Tasks: B12 · Statistiken & Leaderboards

**Input**: Entwurfsunterlagen aus `/specs/012-stats-leaderboards/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: **Pflicht, nicht optional.** Prinzip VII verlangt für jede Formel und jede Regel der
Domänenschicht Unit-Tests ohne laufenden Server, und für Persistenz einen echten Testcontainer statt
Mocks. Testaufgaben stehen deshalb je Geschichte **vor** der Umsetzung.

**Organization**: gruppiert nach den sechs User Stories der Spec. **Die Reihenfolge weicht von der
Nummerierung ab**: US3 (Ranglisten) steht vor US2 (eigenes Profil), weil US3 den Speicherstand
baut, den US2, US5 und US6 lesen. Wer US2 zuerst bauen will, bekommt sie — nur die Zeile mit dem
eigenen Rang bleibt bis US3 leer.

## Format: `[ID] [P?] [Story] Beschreibung`

- **[P]**: parallelisierbar — andere Datei, keine offene Abhängigkeit
- **[Story]**: US1 bis US6; Setup, Foundational und Polish tragen keine
- Jede Aufgabe nennt ihren Pfad und ihren Bezug (FR, SC, R oder ADR)

## Pfade

- `rpg-core/src/main/java/rpg/core/statistics/` — Verzeichnis, Zeiträume, Schwelle, Zeitrechnung,
  Punktformel, **ohne Bukkit**
- `rpg-persistence/src/main/java/rpg/persistence/statistics/` — Sichten, Refresh, die beiden
  Saisontabellen. **`java.sql` lebt nur hier**, erzwungen durch `NoDirectDatabaseAccessTest`
- `rpg-platform/src/main/java/rpg/platform/statistics/` — hier und nur hier wird Paper angefasst
- `rpg-persistence/src/main/resources/db/migration/` — `V12_1` (vier Sichten), `V12_2`
  (Saisonendstand), `V12_3` (Anspruch). **Flyway läuft ohne `outOfOrder`**: die Nummern müssen in
  der Reihenfolge stehen, in der sie entstehen
- `rpg-plugin/src/main/resources/statistics.yml` — neu
- `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` — die Verdrahtung
- Tests jeweils unter `src/test/java/` desselben Moduls

**Das Fundament aus B02 wird angeschlossen, nicht gebaut.** `player_statistic_daily`,
`StatisticsRepository`, der Eintrag in `FlushCycle.WRITE_ORDER` und die Verdrahtung in
`PersistenceModule` existieren. **Es entsteht kein neuer `AggregateType`** (R10) — die dreifache
Registrierung aus ADR-015 ist für die Statistik längst erledigt.

---

## Phase 1: Setup — Pakete und Grenzen

**Purpose**: die Blockgrenzen benennen, bevor etwas darin entsteht

- [X] T001 [P] Paket `rpg-core/src/main/java/rpg/core/statistics/` mit `package-info.java` anlegen — Kopfkommentar nennt die Grenze: Verzeichnis, Zeiträume, Schwelle, Zeitrechnung und Punktformel gehören hierher, **Paper nirgends**, und **kein SQL** (das liegt in `rpg-persistence`)
- [X] T002 [P] Paket `rpg-persistence/src/main/java/rpg/persistence/statistics/` mit `package-info.java` anlegen — nennt die beiden Regeln aus `NoDirectDatabaseAccessTest`: `java.sql` nur hier, und **niemals ein `DELETE` gegen `player_statistic_daily`** (ADR-044, B02 FR-017)
- [X] T003 [P] Paket `rpg-platform/src/main/java/rpg/platform/statistics/` mit `package-info.java` anlegen — hier und nur hier wird Paper angefasst; nennt die vier Nähte von außen (B05-Ereignisse, B09-Zonenwechsel, B04 `holderOf`, B06-Party)
- [X] T004 [P] Testpakete unter `rpg-core/src/test/java/rpg/core/statistics/`, `rpg-persistence/src/test/java/rpg/persistence/statistics/` und `rpg-platform/src/test/java/rpg/platform/statistics/` anlegen
- [X] T005 [P] Testhilfe `StatisticsFixtures` in `rpg-core/src/test/java/rpg/core/statistics/StatisticsFixtures.java` — **Halter- und Charakterkennung sind hier grundsätzlich verschieden.** Eine gemeinsame UUID für beides hat in diesem Projekt schon einmal eine systematische Verwechslung für 1614 Tests unsichtbar gemacht; die Fixture erzeugt sie deshalb getrennt und dokumentiert im Javadoc, warum

---

## Phase 2: Foundational — Verzeichnis, Konfiguration, der zweite Schreibweg

**Purpose**: das Metrikverzeichnis und der Schreibweg, ohne die keine Geschichte anfangen kann

**⚠️ Blockiert alle Geschichten.**

### Das Verzeichnis

- [X] T006 [P] `MetricKind` in `rpg-core/src/main/java/rpg/core/statistics/MetricKind.java` — `SUM`, `MAX`, `STATE`; Javadoc sagt, **warum die Art am Verzeichnis hängt und nicht an der Aufrufstelle** (ADR-040, FR-018)
- [X] T007 [P] `MetricVisibility` in `rpg-core/src/main/java/rpg/core/statistics/MetricVisibility.java` — `PUBLIC`, `PRIVATE`; Javadoc nennt die **drei** privaten Werte namentlich (FR-036, ADR-043)
- [X] T008 [P] `Metric` in `rpg-core/src/main/java/rpg/core/statistics/Metric.java` — Schlüssel, Art, Sichtbarkeit, Dimensioniertheit ([data-model.md](./data-model.md) §1.2)
- [X] T009 `MetricKeys` in `rpg-core/src/main/java/rpg/core/statistics/MetricKeys.java` — Bildung und Zerlegung dimensionierter Schlüssel (`mob_kills.<kindKey>`, `playtime_active.<zoneKey>`)
- [X] T010 `MetricRegistry` in `rpg-core/src/main/java/rpg/core/statistics/MetricRegistry.java` — die eine Stelle, an der eine Metrik existiert (FR-018)
- [X] T011 [P] Test `NoMetricLiteralsTest` in `rpg-core/src/test/java/rpg/core/statistics/NoMetricLiteralsTest.java` — **der Wächter für FR-018**: kein Metrikschlüssel kommt als Zeichenkettenliteral außerhalb des Verzeichnisses vor. Liest den Quelltext, nach dem Muster von `ConfigOnlyMobTest`; benutzt `SourceGuard`, damit ein Kommentar keinen Fehlalarm auslöst
- [X] T012 [P] Test `MetricKindIsEnforcedTest` in `rpg-core/src/test/java/rpg/core/statistics/MetricKindIsEnforcedTest.java` — `count` auf einer MAX-Metrik und `reportMax` auf einer SUM-Metrik werden **abgewiesen**, nicht umgedeutet ([contracts/stats-api.md](./contracts/stats-api.md) §1)
- [X] T012a [P] Test `NoSecondStoreForTheSameNumbersTest` in `rpg-core/src/test/java/rpg/core/statistics/NoSecondStoreForTheSameNumbersTest.java` — **der Wächter für FR-002**, den B11 für seine gleichlautende Zusage (FR-079) auch gebaut hat: kein Typ dieses Blocks hält eine eigene dauerhafte Ablage für Werte, die bereits in `player_statistic_daily` stehen. Quelltextprüfung mit `SourceGuard`. Ohne ihn steht die Regel nur in zwei `package-info` — und die liest niemand beim Hinzufügen einer Klasse
- [X] T012b [P] Test `StateValuesAreNotMirroredTest` in `rpg-core/src/test/java/rpg/core/statistics/StateValuesAreNotMirroredTest.java` — **FR-019, ADR-041**: kein Pfad dieses Blocks schreibt Level, XP oder Coins in die Tagestabelle. Die Zusage „gelesen, nicht gespiegelt" ist sonst eine Absichtserklärung ohne Riegel

### Zeiträume und Saisonkalender

- [X] T013 [P] `Period` in `rpg-core/src/main/java/rpg/core/statistics/Period.java` — `DAY`, `WEEK`, `SEASON`, `ALL_TIME`; **alles in UTC**, weil die gespeicherte Tagesangabe es bereits ist (FR-026)
- [X] T014 [P] Test `PeriodBoundariesTest` in `rpg-core/src/test/java/rpg/core/statistics/PeriodBoundariesTest.java` — die Woche beginnt montags (ISO), der Tag um 00:00 UTC; ein Tag gehört **genau einer** Saison (FR-027)
- [X] T015 `SeasonCalendar` in `rpg-core/src/main/java/rpg/core/statistics/SeasonCalendar.java` — die konfigurierten Saisons, lückenlos und überschneidungsfrei (FR-048, FR-049)
- [X] T016 Test `SeasonCalendarTest` in `rpg-core/src/test/java/rpg/core/statistics/SeasonCalendarTest.java` — eine Lücke, eine Überschneidung und ein `from > to` werden **je einzeln** zurückgewiesen, und die Meldung nennt die beiden beteiligten Saisonschlüssel

### Konfiguration

- [X] T017 `StatisticsConfig` in `rpg-core/src/main/java/rpg/core/statistics/StatisticsConfig.java` — der validierte Inhalt von `statistics.yml` ([contracts/stats-config.md](./contracts/stats-config.md))
- [X] T018 `StatisticsConfigSchema` in `rpg-core/src/main/java/rpg/core/statistics/StatisticsConfigSchema.java` — Prüfung nach der Regeltabelle des Vertrags; jede Meldung nennt **Datei, Schlüssel und Grund** (FR-066)
- [X] T019 Test `StatisticsConfigSchemaTest` in `rpg-core/src/test/java/rpg/core/statistics/StatisticsConfigSchemaTest.java` — je ein Fall für jede Regel; geprüft wird nicht nur *dass* es scheitert, sondern **dass die Meldung den Schlüssel nennt**
- [X] T020 Test `ThresholdMustBeUsefulTest` in `rpg-core/src/test/java/rpg/core/statistics/ThresholdMustBeUsefulTest.java` — **die Startprüfung aus FR-007d**: `kill-credit-share: 0` und `kill-credit-share: 1.5` werden zurückgewiesen. Eine Schwelle von null gäbe jedem Streiftreffer einen Bosskill
- [X] T021 `StatisticsModule` in `rpg-core/src/main/java/rpg/core/statistics/StatisticsModule.java` — Start und Nachladen nach dem Muster von `MobModule`
- [X] T022 Test `StatisticsModuleReloadTest` in `rpg-core/src/test/java/rpg/core/statistics/StatisticsModuleReloadTest.java` — Start scheitert bei unbrauchbarer Konfiguration; Nachladen tauscht die Konfiguration im Ganzen. **Zwei Dinge ändern sich dabei nicht**: ein eingefrorener Saisonendstand und ein bereits angelegter Anspruch ([contracts/stats-config.md](./contracts/stats-config.md), Abschnitt „Nachladen")
- [X] T023 [P] `StatisticsMessageKeys` in `rpg-core/src/main/java/rpg/core/statistics/StatisticsMessageKeys.java` — die Liste der benötigten Nachrichtenschlüssel, nach dem Muster von `ItemMessageKeys` (R9, FR-047)
- [X] T024 [P] `statistics.yml` in `rpg-plugin/src/main/resources/statistics.yml` anlegen — vollständig nach [contracts/stats-config.md](./contracts/stats-config.md), mit den Kommentaren, die die Entscheidungen tragen
- [X] T025 [P] Test `ShippedStatisticsConfigTest` in `rpg-plugin/src/test/java/rpg/plugin/ShippedStatisticsConfigTest.java` — die **ausgelieferte** Datei lädt fehlerfrei und erfüllt jede Regel des Schemas; nach dem Muster von `ShippedMobConfigTest`. **Abweichend von der Planung in rpg-plugin statt rpg-core**: die ausgelieferte Ressource liegt nur auf diesem Klassenpfad — dieselbe Begründung, aus der `ShippedItemConfigTest` und `ShippedMobConfigTest` dort liegen
- [X] T026 [P] Nachrichten in `rpg-plugin/src/main/resources/messages.yml` ergänzen — alle Texte der Fenster, Ranglisten, Saison und Ablehnungen, **auf Englisch** (Prinzip VIII)
- [X] T027 Startprüfung der Message-Schlüssel in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` erweitern — nach dem Muster, das B11 für die slot-abhängigen Händlerschlüssel eingeführt hat

### Der zweite Schreibweg (ADR-040)

- [X] T028 `StatisticsRepository` in `rpg-core/src/main/java/rpg/core/persistence/StatisticsRepository.java` um `reportMax(UUID, String, long)` erweitern — Javadoc erklärt, **warum auch dieser Weg nicht liest**: `GREATEST` ist dieselbe Art Aussage über den vorhandenen Wert wie `+`, nur mit anderem Operator (B02 FR-007)
- [X] T029 `JdbcStatisticsRepository` in `rpg-persistence/src/main/java/rpg/persistence/jdbc/JdbcStatisticsRepository.java` um das zweite Statement erweitern — `ON CONFLICT ... DO UPDATE SET value = GREATEST(value, excluded.value)`; im Speicher wird das **laufende Maximum** gehalten statt eines Deltas. **Kein neuer `AggregateType`, keine neue Markierung** (R10)
- [X] T030 Test `MaxWriteDoesNotReadFirstTest` in `rpg-persistence/src/test/java/rpg/persistence/statistics/MaxWriteDoesNotReadFirstTest.java` — gegen Testcontainers: ein niedrigerer Wert überschreibt einen höheren **nicht**, und im ganzen Pfad steht kein `SELECT` vor dem Schreiben
- [X] T031 Test `SumAndMaxShareOneTableTest` in `rpg-persistence/src/test/java/rpg/persistence/statistics/SumAndMaxShareOneTableTest.java` — beide Arten schreiben in dieselbe Tabelle, und **das Verhalten der vorhandenen SUM-Metriken ändert sich nicht** (die additive Zusage aus dem Complexity Tracking)
- [X] T032 Test `ThousandIncrementsOneWriteTest` in `rpg-persistence/src/test/java/rpg/persistence/statistics/ThousandIncrementsOneWriteTest.java` — **SC-002**: tausend Inkremente zwischen zwei Flushes erzeugen einen Schreibvorgang und eine Zeile

### Die Naht zum Konto

- [X] T033 `AccountLookup` in `rpg-core/src/main/java/rpg/core/statistics/AccountLookup.java` — Charakter → Konto, **eine dünne Hülle um das vorhandene `holderOf(characterId)`** (R3). Javadoc sagt ausdrücklich: keine eigene Zuordnung, keine zweite Wahrheit
- [X] T034 Test `AccountLookupUsesTheExistingSeamTest` in `rpg-core/src/test/java/rpg/core/statistics/AccountLookupUsesTheExistingSeamTest.java` — **Halter und Charakter tragen verschiedene UUIDs**, und ein vertauschtes Paar fällt auf. Genau der Fehler, den 1614 Tests einmal nicht gesehen haben
- [X] T035 `StatisticsPersistenceModule` in `rpg-persistence/src/main/java/rpg/persistence/statistics/StatisticsPersistenceModule.java` — Verdrahtung der neuen Bestände; meldet die Dienste an die Registry wie `PersistenceModule` es tut. **Bewusst auf Phase 4 verschoben**: es gibt zu diesem Zeitpunkt keinen einzigen neuen Bestand zu verdrahten. Der zweite Schreibweg (T028–T032) ist eine zweite Anweisung in B02s vorhandenem `JdbcStatisticsRepository`, kein eigener Typ; die erste eigene Persistenzklasse ist `JdbcLeaderboardSource` in Phase 4, die beiden Saisontabellen folgen in Phase 7. Ein jetzt angelegtes Modul wäre eine leere Klasse, die vorgibt, etwas zu tun — und der Checkpoint dieser Phase verlangt sie nicht

**Checkpoint**: Das Verzeichnis steht, beide Schreibwege schreiben, die Konfiguration scheitert
laut. Die Geschichten können beginnen.

---

## Phase 3: User Story 1 — Was ich tue, wird gezählt (P1) 🎯 MVP

**Goal**: Kills je Art, Bosskills, Tode je Verursacher, zwei Zeituhren, Zeit je Zone und der
höchste Schaden werden erfasst — tagesgenau, über Neustarts hinweg, ohne dass der Tick es merkt.
**Ohne jede Anzeige.**

**Independent Test**: Eine Kreatur bekannter Art töten, an einer anderen sterben, Schaden austeilen,
untätig sein, die Zone wechseln, ausloggen, Server neu starten — und die gespeicherten Tageswerte
gegen das Erwartete prüfen.

### Die Regeln (serverlos, zuerst)

- [X] T036 [P] [US1] Test `KillCreditThresholdTest` in `rpg-core/src/test/java/rpg/core/statistics/KillCreditThresholdTest.java` — **FR-007, SC-019**: drei Beitragende, einer unter der Schwelle → zwei Gutschriften. Der letzte Treffer entscheidet nichts. Genau *auf* der Schwelle zählt als erreicht (Edge Case)
- [X] T037 [P] [US1] Test `PartyMemberInRangeCountsTest` in `rpg-core/src/test/java/rpg/core/statistics/PartyMemberInRangeCountsTest.java` — **FR-007a, SC-014**: ein Mitglied in Reichweite mit Anteil **null** bekommt den Kill; das Mitglied außerhalb der Reichweite nicht. Die Reichweite ist dieselbe wie bei Erfahrung und Coins (FR-007b)
- [X] T038 [P] [US1] Test `BossFollowsTheSameRuleTest` in `rpg-core/src/test/java/rpg/core/statistics/BossFollowsTheSameRuleTest.java` — **FR-007e**: zehn Beteiligte ohne Party, acht über der Schwelle → acht Bosskills. Keine Sonderregel für Bosse
- [X] T039 [US1] `KillCredit` in `rpg-core/src/main/java/rpg/core/statistics/KillCredit.java` — wem ein Kill zufällt; benutzt `DamageShare.shareOf` und die Party aus B06, **rechnet nichts nach** (R5, ADR-042)
- [X] T040 [P] [US1] Test `PlaytimeSplitsAtMidnightTest` in `rpg-core/src/test/java/rpg/core/statistics/PlaytimeSplitsAtMidnightTest.java` — **FR-015, SC-013**: 23:40 bis 00:30 verteilt sich auf zwei Tage
- [X] T041 [P] [US1] Test `TwoClocksTest` in `rpg-core/src/test/java/rpg/core/statistics/TwoClocksTest.java` — **FR-014a/b, SC-015**: nach der Untätigkeitsschwelle steht die aktive Uhr, die Onlinezeit läuft weiter; die aktive Zeit ist nie größer als die Onlinezeit
- [X] T042 [P] [US1] Test `ZoneTimeSumsToActiveTimeTest` in `rpg-core/src/test/java/rpg/core/statistics/ZoneTimeSumsToActiveTimeTest.java` — **FR-014d, FR-014e, SC-016**: die Summe der Zonenzeiten eines Tages entspricht der aktiven Gesamtzeit, auf die Sekunde. Auch dann, wenn die Untätigkeit mitten in einer Zone eintritt (Edge Case) — **und auch dann, wenn ein Teil der Zeit außerhalb jeder Zone verbracht wurde**: ohne den festen Ersatzschlüssel aus FR-014e stimmt die Summe für jeden nicht, der die Wildnis betritt
- [X] T043 [US1] `ActivityClock` in `rpg-core/src/main/java/rpg/core/statistics/ActivityClock.java` — Untätigkeit aus einem Zeitstempel, **keine wiederkehrende Aufgabe** (FR-003, FR-014c)
- [X] T044 [US1] `Playtime` in `rpg-core/src/main/java/rpg/core/statistics/Playtime.java` — zwei Uhren, offene Abschnitte, Teilung an Tagesgrenze und Zonenwechsel ([data-model.md](./data-model.md) §3)

### Die Erfassung (Plattform)

- [X] T045 [P] [US1] Test `KillStatListenerTest` in `rpg-platform/src/test/java/rpg/platform/statistics/KillStatListenerTest.java` — Kill mit bekannter Art, Kill ohne Spielerbeteiligung, Tod durch Kreatur, Tod durch Sturz, Tod durch Spieler; jeder landet unter dem richtigen Schlüssel (FR-006, FR-008, FR-010, FR-011)
- [X] T046 [US1] `KillStatListener` in `rpg-platform/src/main/java/rpg/platform/statistics/KillStatListener.java` — hört auf `CombatDeathEvent`; **löst die Mob-Art im Tick auf, solange die Entität existiert** (FR-012, R4). Ein asynchrones Nachschlagen ginge ins Leere
- [X] T047 [P] [US1] Test `UnresolvableKillerFallsBackTest` in `rpg-platform/src/test/java/rpg/platform/statistics/UnresolvableKillerFallsBackTest.java` — ein Verursacher ohne Vermerk landet unter dem Ersatzschlüssel, **nicht** unter einer fremden Art (FR-011, Edge Case)
- [X] T048 [P] [US1] Test `DamageStatListenerTest` in `rpg-platform/src/test/java/rpg/platform/statistics/DamageStatListenerTest.java` — der höchste Wert gewinnt; **1249,7 wird zu 1249, nicht zu 1250** (FR-016, Edge Case)
- [X] T049 [US1] `DamageStatListener` in `rpg-platform/src/main/java/rpg/platform/statistics/DamageStatListener.java` — hört auf `DamageDealtEvent`, meldet über `reportMax`
- [X] T050 [P] [US1] Test `ClonedDamageCountsForTheSummonerTest` in `rpg-platform/src/test/java/rpg/platform/statistics/ClonedDamageCountsForTheSummonerTest.java` — **FR-016a, ADR-047**: der Schaden eines beschworenen Klons steht beim Beschwörer. Der Test nennt im Javadoc die **bewusste Asymmetrie zu FR-041a aus B11** — mit Block davor, weil B12 selbst ein FR-041 hat und der Bezeichnerraum blockübergreifend nicht eindeutig ist —, damit der nächste Leser sie nicht für einen Fehler hält
- [X] T051 [US1] `ActivityListener` in `rpg-platform/src/main/java/rpg/platform/statistics/ActivityListener.java` — **der fünfte Handler auf `PlayerMoveEvent`** (R7): `MONITOR`, eine Zuweisung in eine vorbelegte Map, keine Allokation, keine Bedingung. Javadoc übernimmt die Warnung aus `ZoneMovementListener`, warum hier alles frei sein muss
- [X] T052 [US1] `ActivityListener` um die übrigen Auslöser erweitern — Kampf, Interaktion, Menüs, Commands (FR-014c1). Bewegung allein reicht nicht, aber sie zählt
- [X] T053 [P] [US1] Test `ActivityTouchIsFreeTest` in `rpg-platform/src/test/java/rpg/platform/statistics/ActivityTouchIsFreeTest.java` — **FR-014c2**: das Erneuern des Zeitstempels löst keinen Schreibvorgang und keine Neuberechnung aus. Wiederholbare Messung ohne Volllast (SC-001, Prinzip VII)
- [X] T054 [US1] `ZoneTimeListener` in `rpg-platform/src/main/java/rpg/platform/statistics/ZoneTimeListener.java` — hört auf `ZoneChangedEvent`, schließt den alten Abschnitt und öffnet den neuen (FR-014f). **Kein eigenes Nachsehen**, in welcher Zone jemand steht
- [X] T055 [P] [US1] Test `ZoneChangeWhileIdleTest` in `rpg-platform/src/test/java/rpg/platform/statistics/ZoneChangeWhileIdleTest.java` — der Abschnitt wechselt, aber **keine** der beiden Zonen bekommt aktive Zeit (Edge Case)
- [X] T056 [US1] `PlaytimeAccrual` in `rpg-platform/src/main/java/rpg/platform/statistics/PlaytimeAccrual.java` — schreibt beide Uhren und die Zonenaufteilung fort. **Reitet auf dem vorhandenen Inventar-Sweep mit** (R6, FR-015) und legt keine eigene Aufgabe an
- [X] T057 [P] [US1] Test `PlaytimeRidesTheExistingSweepTest` in `rpg-platform/src/test/java/rpg/platform/statistics/PlaytimeRidesTheExistingSweepTest.java` — **architektonische Zusicherung**: der Quelltext dieses Blocks plant **keine** wiederkehrende Aufgabe je Spieler ein (Prinzip II). Liest den Quelltext, wie `NoGlobalSchedulerAccessTest` es für den Scheduler tut
- [X] T058 [US1] Letzten Abschnitt beim Sitzungsende schließen — angebunden an den vorhandenen `onSessionEnded`-Rückruf, **nicht** an einen eigenen `PlayerQuitEvent`-Handler. B11 hat für genau diesen zweiten Ausstiegspfad eine architektonische Zusicherung eingeführt (T076 dort)

### Zusammenhalten

- [X] T059 [US1] `Statistics` als öffentliche Fassade in `rpg-core/src/main/java/rpg/core/statistics/Statistics.java` — Signaturen nach [contracts/stats-api.md](./contracts/stats-api.md) §1
- [X] T060 [P] [US1] Test `CaptureFailureDoesNotBreakTheGameTest` in `rpg-platform/src/test/java/rpg/platform/statistics/CaptureFailureDoesNotBreakTheGameTest.java` — **FR-004, SC-011**: ein Statistikdienst, der bei jedem Aufruf wirft, lässt Kills, Tode und Beute unverändert weiterlaufen
- [X] T061 [US1] Verdrahtung in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java`: `wireStatistics()` — Modul laden, Zuhörer registrieren, Fortschreibung an den Inventar-Sweep hängen
- [X] T062 [US1] `FullBootstrapTest` in `rpg-plugin/src/test/java/rpg/plugin/FullBootstrapTest.java` nachziehen — **`PlayerMoveEvent` trägt jetzt einen Handler mehr** (R7). Die Zahl steigt von vier auf fünf; genau dafür zählt dieser Test sie
- [X] T063 [P] [US1] Test `StatisticsSurviveRestartTest` in `rpg-persistence/src/test/java/rpg/persistence/statistics/StatisticsSurviveRestartTest.java` — **SC-004**: gegen Testcontainers, Werte vor und nach einem simulierten Neustart identisch
- [X] T064 [P] [US1] Test `ThreeCharactersOneCountTest` in `rpg-core/src/test/java/rpg/core/statistics/ThreeCharactersOneCountTest.java` — **FR-005**: ein Spieler mit drei Charakteren hat **eine** Kill-Zahl
- [X] T065 [US1] Übersprungene Tests des Laufs durchsehen — **MockBukkit meldet Nicht-Implementiertes als „skipped", nicht als Fehler.** Ein grüner Lauf mit Übersprungenem ist kein grüner Lauf; jeder Skip wird benannt und entweder umgangen oder als bewusst hingenommen dokumentiert

**Checkpoint**: Die Werte sammeln sich. Ab hier ist jeder Spieltag gezählt, auch wenn noch niemand
etwas davon sieht — **das ist der MVP**, und er ist der einzige Teil, dessen Fehlen sich später
nicht nachholen lässt.

---

## Phase 4: User Story 3 — Die Rangliste (P2)

**Goal**: Vier Sichten, eine Auffrischung, ein Speicherstand — und ein Fenster, das ihn zeigt, ohne
die Datenbank zu fragen.

**Vorgezogen**, weil US2, US5 und US6 diesen Speicherstand lesen.

**Independent Test**: Werte für mehrere Spieler erzeugen, eine Auffrischung auslösen, die Rangliste
öffnen, die Reihenfolge prüfen — dann fünfzig Öffnungen auslösen und die Datenbankabfragen dabei
mitzählen.

- [X] T066 [US3] Migration `V12_1__statistic_leaderboard_views.sql` in `rpg-persistence/src/main/resources/db/migration/` — **vier** Materialized Views, die Metrik als Spalte, je ein eindeutiger Index für `REFRESH ... CONCURRENTLY` (R1, [data-model.md](./data-model.md) §2.1)
- [X] T067 [P] [US3] Test `LeaderboardViewsMatchRawDataTest` in `rpg-persistence/src/test/java/rpg/persistence/statistics/LeaderboardViewsMatchRawDataTest.java` — **der eigentliche Beweis, dass die Sichtdefinition stimmt**: nach dem Refresh liefert jede Sicht denselben Stand wie eine direkte Aggregation über die Rohdaten
- [X] T068 [P] [US3] Test `FamilyAggregationTest` in `rpg-persistence/src/test/java/rpg/persistence/statistics/FamilyAggregationTest.java` — **FR-013**: „alle Kills" ist die Summe über `mob_kills.*`, „Bosskills" die Summe über die Arten mit Boss-Kennzeichen. Kein zusätzlicher Schreibvorgang, kein zweiter Zähler
- [X] T069 [US3] `LeaderboardRefresh` in `rpg-persistence/src/main/java/rpg/persistence/statistics/LeaderboardRefresh.java` — `REFRESH MATERIALIZED VIEW CONCURRENTLY`, asynchron, **blockiert den Tick nicht** (FR-031)
- [X] T070 [US3] `JdbcLeaderboardSource` in `rpg-persistence/src/main/java/rpg/persistence/statistics/JdbcLeaderboardSource.java` — füllt den Speicherstand aus **einer Abfrage je Sicht**, unabhängig von der Zahl der Ranglisten (R1)
- [X] T071 [US3] Anonymisierte Konten beim **Füllen** ausschließen, nicht beim Anzeigen — eine Ansicht, die filtern müsste, ist eine Ansicht, die es vergessen kann (FR-039, [data-model.md](./data-model.md) §2.2)
- [X] T072 [P] [US3] Test `AnonymisedAccountIsNotRankedTest` in `rpg-persistence/src/test/java/rpg/persistence/statistics/AnonymisedAccountIsNotRankedTest.java` — **SC-007**: die Zahlen bleiben gezählt (`REPOINT_STATISTICS`), das Konto erscheint in keiner Rangliste
- [X] T073 [P] [US3] Test `LeaderboardCacheTest` in `rpg-core/src/test/java/rpg/core/statistics/LeaderboardCacheTest.java` — Gleichstand trägt denselben Rang und **stabile** Reihenfolge über wiederholte Aufrufe (FR-034)
- [X] T074 [US3] `Leaderboard`, `LeaderboardEntry` und `LeaderboardCache` in `rpg-core/src/main/java/rpg/core/statistics/` — Rang, Konto, Anzeigename, Wert, Auffrischungszeitpunkt ([data-model.md](./data-model.md) §2.2)
- [X] T075 [US3] `Leaderboards`-Fassade in `rpg-core/src/main/java/rpg/core/statistics/Leaderboards.java` — Signaturen nach [contracts/stats-api.md](./contracts/stats-api.md) §3; `board` ist **synchron und tickfrei**

#### Die beiden Zustandsranglisten (ADR-041)

> Level und Coins kommen **nicht** aus der Statistiktabelle, sondern aus den Beständen, in denen
> sie ohnehin stehen. Ohne diese Gruppe fehlen zwei der zweiundzwanzig Ranglisten, und ADR-041
> hätte keinen Code hinter sich.

- [X] T075a [P] [US3] Test `AccountLevelIsTheHighestCharacterTest` in `rpg-core/src/test/java/rpg/core/statistics/AccountLevelIsTheHighestCharacterTest.java` — **FR-020, FR-021**: das Level eines Kontos ist der höchste seiner Charaktere; bei Gleichstand entscheidet die XP **innerhalb** des Levels (`xp_in_level` ist kein Gesamtwert). Angezeigt wird die Klasse genau dieses Charakters
- [X] T075b [P] [US3] Test `AccountCoinsAreTheSumTest` in `rpg-core/src/test/java/rpg/core/statistics/AccountCoinsAreTheSumTest.java` — **FR-022**: die Coins eines Kontos sind die Summe über seine Charaktere, nicht der höchste Einzelstand
- [X] T075c [P] [US3] Test `StateBoardsHaveNoPeriodsTest` in `rpg-core/src/test/java/rpg/core/statistics/StateBoardsHaveNoPeriodsTest.java` — **FR-023, [contracts/stats-api.md](./contracts/stats-api.md) §2**: eine Zustandsmetrik mit einem anderen Zeitraum als `ALL_TIME` wird **abgewiesen**, nicht auf den aktuellen Stand umgedeutet. Das Fenster bietet für sie erst gar keinen Zeitraum an
- [X] T075d [US3] `JdbcStateLeaderboardSource` in `rpg-persistence/src/main/java/rpg/persistence/statistics/JdbcStateLeaderboardSource.java` — liest `character_progress` und `character_balance`, verdichtet über die Charaktere eines Kontos. **Kein Schreibweg, keine Spiegelung** (FR-019, ADR-041)
- [X] T075e [US3] Beide Zustandsranglisten in denselben Speicherstand aufnehmen wie die Zählerranglisten — damit gilt auch für sie: **das Öffnen kostet keine Abfrage** (FR-030). Der Preis dieser Entscheidung ist benannt: der Cache hat zwei Quellen (ADR-041, plan.md „Auswirkung")
- [X] T075f [P] [US3] Test `StateBoardsCostNoQueryOnOpenTest` in `rpg-platform/src/test/java/rpg/platform/statistics/StateBoardsCostNoQueryOnOpenTest.java` — SC-003 gilt für **beide** Quellen, nicht nur für die Sichten. Genau hier ginge die Zusage sonst still verloren
- [X] T076 [P] [US3] Test `OpeningNeverQueriesTest` in `rpg-platform/src/test/java/rpg/platform/statistics/OpeningNeverQueriesTest.java` — **SC-003, das Erfolgskriterium des Blockdokuments**: fünfzig gleichzeitige Öffnungen, **null** Datenbankabfragen. Zählt sie über eine Testquelle, die jede Abfrage meldet
- [X] T077 [P] [US3] Test `EmptyCacheShowsAMessageTest` in `rpg-platform/src/test/java/rpg/platform/statistics/EmptyCacheShowsAMessageTest.java` — **FR-035**: vor der ersten Auffrischung eine Meldung, keine leere Liste und **keine Ersatzabfrage**
- [X] T078 [US3] Namensauflösung außerhalb des Ticks, Name gehört zum Cache-Eintrag (FR-040)
- [X] T079 [US3] `LeaderboardMenu` in `rpg-platform/src/main/java/rpg/platform/statistics/LeaderboardMenu.java` — Fenster nach dem Muster von `VendorMenu`; Metrik und Zeitraum im Fenster umschaltbar (FR-043)
- [X] T079a [US3] Metrikbeschriftung: die Kill-Ranglisten heißen **Beteiligung an Kills**, nicht „erledigte Kreaturen" — Message-Keys anlegen und in `StatisticsMessageKeys` aufnehmen (**FR-007c**). Der Preis von ADR-042 ist angenommen, aber er muss auch dastehen: die Summe aller Zähler übersteigt die Zahl toter Kreaturen, und ein Spieler, der das nicht liest, meldet die Rangliste als kaputt
- [X] T079b [P] [US3] Test `KillBoardIsLabelledParticipationTest` in `rpg-platform/src/test/java/rpg/platform/statistics/KillBoardIsLabelledParticipationTest.java` — **FR-007c**: keine Ansicht dieses Blocks gibt eine Kill-Zahl als Zahl eigenhändig erledigter Kreaturen aus
- [X] T079c [P] [US3] Test `BossKillsAreDisjointFromMobKillsTest` in `rpg-persistence/src/test/java/rpg/persistence/statistics/BossKillsAreDisjointFromMobKillsTest.java` — **FR-009a**: eine Art mit Boss-Kennzeichen erscheint in der Bosskill-Rangliste und **nicht** in der Mob-Kill-Rangliste; beide zusammen ergeben alle Kills. Ohne diese Trennung zählte ein Bosskill in der Gesamtwertung doppelt
- [X] T080 [US3] Fenster nennt das **Alter** der gezeigten Werte (FR-032) — die Zeile, die den Unterschied zwischen „veraltet" und „kaputt" erklärt
- [X] T080a [P] [US3] Test `ValuesAreNeverOlderThanTheIntervalTest` in `rpg-platform/src/test/java/rpg/platform/statistics/ValuesAreNeverOlderThanTheIntervalTest.java` — **SC-005**: nach einer Auffrischung ist kein gezeigter Wert älter als das konfigurierte Intervall, und die genannte Altersangabe stimmt mit dem tatsächlichen Auffrischungszeitpunkt überein
- [X] T081 [US3] Eigene Platzierung anzeigen, auch außerhalb der ersten N (FR-033)
- [X] T082 [US3] `StatisticsMenuListener` in `rpg-platform/src/main/java/rpg/platform/statistics/StatisticsMenuListener.java` — **ein** Zuhörer für alle drei Fenster dieses Blocks; nichts wird entnommen oder hineingelegt (FR-045)
- [X] T083 [US3] `TopCommand` in `rpg-plugin/src/main/java/rpg/plugin/command/TopCommand.java` und Eintrag in `plugin.yml` samt Berechtigung mit `default: true` (FR-046, Muster `rpg.currency.balance`)
- [X] T084 [US3] Auffrischung im Konfigurationstakt starten — ein Takt für alle vier Sichten, nach dem Muster der vorhandenen Sweeps; Verdrahtung in `RpgPlugin`
- [X] T085 [US3] `FullBootstrapTest` nachziehen — `InventoryClickEvent` und `InventoryCloseEvent` tragen je einen Handler mehr, ein Command mehr ist registriert

**Checkpoint**: Ranglisten stehen im Speicher und kosten beim Öffnen nichts. US2, US5 und US6
können sie ab hier lesen.

---

## Phase 5: User Story 2 — Mein eigenes Profil (P2)

**Goal**: Die eigenen Werte in vier Zeiträumen, die eigene Platzierung und **die drei privaten
Werte**, die sonst niemand sieht.

**Independent Test**: Mit einem Spieler Werte erzeugen, das eigene Fenster öffnen, die vier
Zeiträume durchschalten und gegen die gespeicherten Werte prüfen.

- [X] T086 [P] [US2] Test `StatisticsViewReturnsAllPeriodsTest` in `rpg-core/src/test/java/rpg/core/statistics/StatisticsViewReturnsAllPeriodsTest.java` — jeder Zeitraum hat seine eigene Summe, und der Allzeit-Wert ist mindestens so groß wie jeder andere
- [X] T087 [US2] `StatisticsView`-Fassade in `rpg-core/src/main/java/rpg/core/statistics/StatisticsView.java` — Signaturen nach [contracts/stats-api.md](./contracts/stats-api.md) §2
- [X] T088 [US2] `breakdown` gibt private Werte **nur für den Betrachter selbst** heraus — der Aufrufer muss belegen, dass er das Konto ist; es gibt **keine** Umgehung für B13 oder B14 (FR-037)
- [X] T089 [P] [US2] Test `BreakdownRefusesForeignAccountsTest` in `rpg-core/src/test/java/rpg/core/statistics/BreakdownRefusesForeignAccountsTest.java` — die Fassade selbst weigert sich, nicht erst das Fenster. Ein Schloss an der Anzeige ist kein Schloss
- [X] T090 [US2] `StatisticsMenu` in `rpg-platform/src/main/java/rpg/platform/statistics/StatisticsMenu.java` — eigenes Profil, vier Zeiträume, eigene Platzierung je öffentlicher Metrik (FR-041, FR-042)
- [X] T091 [US2] Die drei privaten Werte im eigenen Fenster zeigen: Tode je Verursacher, gesamte Onlinezeit, Zeit je Zone (FR-038)
- [X] T092 [P] [US2] Test `EmptyDayShowsZerosTest` in `rpg-platform/src/test/java/rpg/platform/statistics/EmptyDayShowsZerosTest.java` — ein Spieler, der heute nichts getan hat, sieht Nullen und keine Fehlermeldung
- [X] T093 [US2] `StatisticsCommand` in `rpg-plugin/src/main/java/rpg/plugin/command/StatisticsCommand.java` und Eintrag in `plugin.yml` samt Berechtigung mit `default: true` (FR-041, FR-046)
- [X] T094 [US2] Verdrahtung in `RpgPlugin`, Fenster an `StatisticsMenuListener` anschließen
- [X] T095 [US2] Sitzungsende räumt ein offenes Fenster über den vorhandenen `onSessionEnded`-Rückruf auf — **kein zweiter Ausstiegspfad** (Muster aus B11)

**Checkpoint**: Ein Spieler sieht seine Zahlen — und als Einziger die drei privaten.

---

## Phase 6: User Story 4 — Ein fremdes Profil, aber nicht alles davon (P2)

**Goal**: Das öffentliche Profil eines anderen — ohne die drei privaten Werte, auf keinem Weg.

**Independent Test**: Zwei Spieler, einer stirbt mehrfach an einer bestimmten Art und steht lange
untätig herum; der andere öffnet dessen Profil und findet weder die Aufschlüsselung noch die
Onlinezeit noch die Zonenaufteilung.

- [X] T096 [P] [US4] Test `ForeignProfileHidesAllThreePrivateValuesTest` in `rpg-platform/src/test/java/rpg/platform/statistics/ForeignProfileHidesAllThreePrivateValuesTest.java` — **SC-006 und SC-017, die Anforderung mit der größten Chance, still verloren zu gehen**: geprüft werden **alle vier** Ausgabewege — eigenes Fenster (zeigt), Fremdprofil, Rangliste und Hologramm (zeigen nicht)
- [X] T097 [US4] `ProfileMenu` in `rpg-platform/src/main/java/rpg/platform/statistics/ProfileMenu.java` — Gesamtzahl der Tode und **aktive** Spielzeit; keiner der drei privaten Werte (FR-037)
- [X] T098 [US4] `StatisticsCommand` um `/stats <spieler>` erweitern (FR-044)
- [X] T099 [P] [US4] Test `UnknownPlayerGetsAMessageTest` in `rpg-platform/src/test/java/rpg/platform/statistics/UnknownPlayerGetsAMessageTest.java` — ein Name, den es nie gab, erzeugt eine Meldung und **kein leeres Fenster**
- [X] T100 [P] [US4] Test `PublicPlaytimeIsTheActiveOneTest` in `rpg-platform/src/test/java/rpg/platform/statistics/PublicPlaytimeIsTheActiveOneTest.java` — **FR-038a**: ein Spieler mit viel Leerlauf steht in der Rangliste mit seiner aktiven Zeit, nicht mit seiner Onlinezeit
- [X] T101 [US4] Fenster an `StatisticsMenuListener` anschließen, Verdrahtung in `RpgPlugin`
- [X] T102 [US4] `FullBootstrapTest` nachziehen, falls die Handlerzahl sich erneut ändert

**Checkpoint**: Das Fremdprofil zeigt, was es zeigen darf — und ein Test bewacht die Grenze über
alle vier Wege.

---

## Phase 7: User Story 5 — Die Saison endet und belohnt (P3)

**Goal**: Eine gewichtete Gesamtwertung, ein eingefrorener Endstand, ein Anspruch, der genau einmal
eingelöst wird und nie verfällt.

**Independent Test**: Eine Saison mit vergangenem Enddatum konfigurieren, den Abschluss auslösen,
den Endstand prüfen, den Anspruch einlösen und ein zweites Einlösen versuchen.

### Die Punktzahl

- [X] T103 [P] [US5] Test `SeasonScoreIsRecomputableTest` in `rpg-core/src/test/java/rpg/core/statistics/SeasonScoreIsRecomputableTest.java` — **SC-020**: die Punktzahl lässt sich aus den gezeigten Werten und Gewichten nachrechnen, und die Aufschlüsselung summiert sich auf den ausgewiesenen Wert
- [X] T104 [P] [US5] Test `StateValuesNeverScoreTest` in `rpg-core/src/test/java/rpg/core/statistics/StateValuesNeverScoreTest.java` — **FR-050c, ADR-046**: eine Gewichtung, die Level, XP, Coins oder einen privaten Wert nennt, wird beim Start zurückgewiesen. Zustandswerte trügen den Fortschritt alter Saisons in die neue
- [X] T105 [P] [US5] Test `EmptyWeightsFailStartupTest` in `rpg-core/src/test/java/rpg/core/statistics/EmptyWeightsFailStartupTest.java` — **FR-050d, SC-022**: eine Gewichtung ohne bekannte Metrik bricht den Start ab, und die Meldung nennt die fehlende Metrik
- [X] T106 [US5] `ScoreWeights` in `rpg-core/src/main/java/rpg/core/statistics/ScoreWeights.java` — Gewichte je Metrik, einfrierbar
- [X] T107 [US5] `SeasonScore` in `rpg-core/src/main/java/rpg/core/statistics/SeasonScore.java` — Summe gewichteter Werte des Saisonzeitraums (FR-050b)
- [X] T108 [US5] `ScoreBreakdown` — je beitragender Metrik Wert, Gewicht und Punkte (FR-050f)

### Bestand

- [X] T109 [US5] Migration `V12_2__season_result.sql` — Endstand samt **der Gewichtung, mit der gerechnet wurde** ([data-model.md](./data-model.md) §1.3)
- [X] T110 [US5] Migration `V12_3__season_reward_claim.sql` — Anspruch mit `claimed_at NULL` als „offen"; **keine Ablaufspalte**, weil es keine Frist gibt (FR-053a)
- [X] T111 [US5] `JdbcSeasonResultRepository` in `rpg-persistence/src/main/java/rpg/persistence/statistics/JdbcSeasonResultRepository.java` — **ohne Write-Behind** (R2). Javadoc begründet, warum: ein Endstand entsteht viermal im Jahr, und der Write-Behind-Weg bündelt viele Änderungen je Sekunde
- [X] T112 [US5] `JdbcRewardClaimRepository` in `rpg-persistence/src/main/java/rpg/persistence/statistics/JdbcRewardClaimRepository.java` — bedingtes Update `WHERE claimed_at IS NULL`, **erst markieren, dann gutschreiben**
- [X] T113 [P] [US5] Test `ClaimIsExactlyOnceTest` in `rpg-persistence/src/test/java/rpg/persistence/statistics/ClaimIsExactlyOnceTest.java` — **SC-008**: zwei gleichzeitige Einlösungen, genau eine Gutschrift. Und ein Absturz zwischen Markierung und Gutschrift **verliert höchstens, verdoppelt nie**
- [X] T113a [P] [US5] Test `ClaimNeverExpiresTest` in `rpg-persistence/src/test/java/rpg/persistence/statistics/ClaimNeverExpiresTest.java` — **FR-053a, SC-018**: ein Anspruch aus einer Saison, die ein Jahr zurückliegt, ist unverändert einlösbar. Der Test hält die Zusage fest, dass **keine** Ablaufspalte nachträglich eingeführt wird
- [X] T114 [P] [US5] Test `FrozenStandingIgnoresLaterWeightsTest` in `rpg-persistence/src/test/java/rpg/persistence/statistics/FrozenStandingIgnoresLaterWeightsTest.java` — **SC-021**: Gewichte ändern, Endstand bleibt

### Abschluss und Einlösen

- [X] T115 [US5] `SeasonClosing` in `rpg-core/src/main/java/rpg/core/statistics/SeasonClosing.java` — friert den Endstand ein und legt Ansprüche an (FR-050, FR-052)
- [X] T116 [P] [US5] Test `ClosingIsIdempotentTest` in `rpg-core/src/test/java/rpg/core/statistics/ClosingIsIdempotentTest.java` — **FR-058, SC-009**: war der Server über das Saisonende hinweg aus, wird der Abschluss beim Start nachgeholt — **genau einmal**. Vorhandene Zeilen in `season_result` sind der Beleg
- [X] T117 [P] [US5] Test `EmptySeasonClosesCleanlyTest` in `rpg-core/src/test/java/rpg/core/statistics/EmptySeasonClosesCleanlyTest.java` — eine Saison ohne Teilnehmer erzeugt keinen Anspruch, und die nächste Saison startet trotzdem (Edge Case)
- [X] T118 [US5] `Seasons`-Fassade in `rpg-core/src/main/java/rpg/core/statistics/Seasons.java` — Signaturen nach [contracts/stats-api.md](./contracts/stats-api.md) §4, `ClaimOutcome` mit vier Ausgängen
- [X] T119 [US5] `SeasonRewardClaimListener` in `rpg-platform/src/main/java/rpg/platform/statistics/SeasonRewardClaimListener.java` — offene Ansprüche beim nächsten Spielen anbieten (FR-052)
- [X] T120 [US5] Einlösen durch einen Charakter: Coins über B08b, Items über den Weg aus B11 (FR-054)
- [X] T121 [P] [US5] Test `FullInventoryKeepsTheClaimTest` in `rpg-platform/src/test/java/rpg/platform/statistics/FullInventoryKeepsTheClaimTest.java` — **FR-055**: kein Platz → Ablehnung mit Begründung, Anspruch bleibt offen, **und wird gar nicht erst markiert**
- [X] T122 [US5] Jede Einlösung protokollieren (FR-056) — über den vorhandenen Audit-Weg, kein eigener. **Teilweise:** Coins tragen den neuen Buchungsgrund `SEASON_REWARD` und stehen damit im Coin-Ledger, das B08b ohnehin führt. **Offen:** ein Anspruch aus reinen Gegenständen erzeugt keinen Ledger-Eintrag und ist damit unprotokolliert — dafür braucht es `rpg.audit_log` (B02s `JdbcAuditLogRepository`), verdrahtet in `SeasonRewardClaimListener`
- [X] T123 [US5] Gesamtwertung als eigene Rangliste in Fenster und Cache aufnehmen; **Zwischenstand der laufenden Saison sichtbar** (FR-050e)
- [X] T124 [P] [US5] Test `SeasonChangeDeletesNothingTest` in `rpg-persistence/src/test/java/rpg/persistence/statistics/SeasonChangeDeletesNothingTest.java` — **FR-057, ADR-044**: nach einem Saisonwechsel ist kein Rohdatensatz verschwunden
- [ ] T125 [US5] Verdrahtung in `RpgPlugin`: Abschlussprüfung beim Start und im Auffrischungstakt

**Checkpoint**: Die Saison kürt einen Spieler, und seine Belohnung wartet auf ihn — beliebig lange.

---

## Phase 8: User Story 6 — Das Hologramm im Hub (P3)

**Goal**: Eine dauerhafte Anzeige, die denselben Speicherstand liest, sich nicht verdoppelt und für
das Mob-Budget nicht existiert.

**Independent Test**: Server starten, Anzeige prüfen, zweimal neu starten, zählen — und prüfen, dass
das Mob-Budget sie nicht mitzählt.

- [ ] T126 [US6] `LeaderboardHologram` in `rpg-platform/src/main/java/rpg/platform/statistics/LeaderboardHologram.java` — **Muster `VendorNpc`** (R8): vor dem Setzen im Umkreis entfernen, dann setzen (FR-061)
- [ ] T127 [US6] Härtung **einzeln je Einstellung** in `apply("name", () -> ...)` — ein Testdouble, das eine Methode nicht kennt, darf nicht den ganzen Vorgang scheitern lassen. Genau daran ist B11 einmal aufgelaufen
- [ ] T128 [US6] Anzeige **nicht** in `HordeRegistry` eintragen — mehr braucht es für FR-062 nicht (R8)
- [ ] T129 [P] [US6] Test `HologramDoesNotCountAgainstTheMobBudgetTest` in `rpg-platform/src/test/java/rpg/platform/statistics/HologramDoesNotCountAgainstTheMobBudgetTest.java` — nach dem Muster von `VendorDoesNotCountAgainstTheMobBudgetTest`
- [ ] T130 [P] [US6] Test `RestartsDoNotDuplicateTest` in `rpg-platform/src/test/java/rpg/platform/statistics/RestartsDoNotDuplicateTest.java` — **SC-010**: drei Starts hintereinander, genau eine Anzeige
- [ ] T131 [US6] Unverwundbar, unbeweglich, kein Aggro-Ziel, kein Distanz-Despawn (FR-063)
- [ ] T132 [US6] Inhalt aus dem Cache, **keine eigene Abfrage** (FR-060); Aktualisierung im Auffrischungstakt
- [ ] T133 [P] [US6] Test `MissingWorldDisablesHologramTest` in `rpg-platform/src/test/java/rpg/platform/statistics/MissingWorldDisablesHologramTest.java` — **FR-064**: nicht ladbare Stelle → Warnung, keine Anzeige, **Server startet** (Edge Case)
- [ ] T134 [US6] Verdrahtung in `RpgPlugin`: Platzierung nach dem Laden der Welten, versetzt zum Safe-Core-Respawn wie bei den Händlern

**Checkpoint**: Alle sechs Geschichten stehen.

---

## Phase 9: Polish & Querschnitt

- [ ] T135 [P] Test `PrivateValuesNeverLeaveTest` in `rpg-platform/src/test/java/rpg/platform/statistics/PrivateValuesNeverLeaveTest.java` — die Sichtbarkeitsprüfung **noch einmal über den fertigen Block**, jetzt mit allen Fenstern, Commands und dem Hologramm gleichzeitig
- [ ] T136 [P] Test `NewMetricAppearsWithoutConfigTest` in `rpg-core/src/test/java/rpg/core/statistics/NewMetricAppearsWithoutConfigTest.java` — **SC-012, SC-023, FR-032a**: eine neu erfasste Zählermetrik erscheint in allen vier Zeiträumen als Rangliste — ohne eine Zeile Code **und ohne einen Eintrag in der Konfiguration**
- [ ] T137 [P] Test `RefreshCostIsIndependentOfMetricCountTest` in `rpg-persistence/src/test/java/rpg/persistence/statistics/RefreshCostIsIndependentOfMetricCountTest.java` — **die Zusage aus R1**: zehn Metriken und sechzig Metriken kosten dieselben vier Abfragen
- [ ] T138 [P] Messung des Erfassungspfades ohne Volllast, wiederholbar — **SC-001**, Prinzip VII. Kein Lasttest: der bleibt B15 (ADR-031) und hält diesen Block nicht offen
- [ ] T139 `ADR-040` bis `ADR-047` in `02-decisions.md` gegen den gebauten Stand durchsehen — weicht die Umsetzung ab, wird der ADR nachgetragen, nicht die Umsetzung stillschweigend behalten
- [ ] T140 [P] `01-architecture.md` — B12 als gebaut kennzeichnen
- [ ] T141 [P] `minecraft-rpg-spec/minecraft-rpg-spec/blocks/B12-stats-leaderboards.md` nachziehen: die vier offenen Fragen sind beantwortet, Verweis auf die Spec und die ADRs
- [ ] T142 Vollständigen Testlauf über alle Module, **Übersprungenes einzeln durchsehen** (MockBukkit meldet Nicht-Implementiertes als „skipped")
- [ ] T143 `FullBootstrapTest` als letzte Instanz grün — **Modultests reichen nicht: das Modul muss im Plugin verdrahtet sein.** Erst hier ist der Block fertig
- [ ] T144 Deploy auf den Testserver — **`statistics.yml` muss von Hand mit.** Bukkit überschreibt vorhandene Configs nicht; das Jar allein deployt die YAML-Änderungen nicht, und der Server bricht dann beim Start gegen eine Datei ohne die neuen Schlüssel ab
- [ ] T145 Startprüfungen auf dem Testserver **absichtlich provozieren**: eine Saisonlücke, eine leere Gewichtung, eine Schwelle von 0 — jede muss mit Datei, Schlüssel und Grund abbrechen ([quickstart.md](./quickstart.md) §5)
- [ ] T146 Echter Serverstart als Beleg für den Klassenlader — **grüne Tests beweisen nichts über Papers `libraries:`-Mechanismus**, nur ein echter Start tut das
- [ ] T147 Nach einer Spielstunde in die Tabelle sehen: Schlüssel tragen ihre Dimension, und die Summe der Zonenzeiten entspricht der aktiven Gesamtzeit ([quickstart.md](./quickstart.md) §5)
- [ ] T148 `quickstart.md` von oben nach unten durchlaufen und abhaken

---

## Dependencies & Execution Order

### Phasenabhängigkeiten

- **Phase 1 (Setup)**: keine Abhängigkeit
- **Phase 2 (Foundational)**: nach Setup — **blockiert alles**. Ohne Verzeichnis und Schreibweg gibt es nichts zu zählen
- **Phase 3 (US1)**: nach Foundational. **Der MVP**
- **Phase 4 (US3)**: nach US1 — braucht Werte, um sie zu ranken
- **Phase 5 (US2)**: nach US1; die Zeile mit dem eigenen Rang braucht zusätzlich US3
- **Phase 6 (US4)**: nach US2 — teilt sich Fenster und Zuhörer
- **Phase 7 (US5)**: nach US3 — die Gesamtwertung ist eine Rangliste
- **Phase 8 (US6)**: nach US3 — liest denselben Speicherstand
- **Phase 9 (Polish)**: nach allen Geschichten, die geliefert werden sollen

### Abhängigkeiten zwischen den Geschichten

Nur eine Geschichte ist ohne Vorgänger vollständig: **US1**. Das ist Absicht — sie ist die einzige,
deren Fehlen sich später nicht nachholen lässt. Ein Tag ohne Erfassung ist ein Tag, dessen Zahlen
niemals entstehen.

- **US3 → US2**: nur die Rangzeile. Die Werte in vier Zeiträumen zeigt US2 auch allein
- **US3 → US5, US6**: beide lesen den Speicherstand, den US3 baut
- **US2 → US4**: Fenster und Zuhörer werden geteilt

### Parallelität

- Phase 1 vollständig parallel
- In Phase 2: die drei Blöcke *Verzeichnis*, *Zeiträume* und *Konfiguration* laufen unabhängig; der
  zweite Schreibweg (T028–T032) hängt an nichts davon
- In jeder Geschichte: die mit [P] markierten Tests vor der Umsetzung parallel
- **US5 und US6 sind nach US3 vollständig parallel** — verschiedene Dateien, verschiedene Nähte

---

## Implementation Strategy

### MVP zuerst

1. Phase 1 + 2 (Setup und Foundational)
2. Phase 3 (US1)
3. **Anhalten und prüfen**: Werte sammeln sich, überstehen einen Neustart, kosten im Tick nichts
4. Ab hier zählt jeder Spieltag — auch wenn noch niemand etwas sieht

### Schrittweise Auslieferung

1. Foundational → das Verzeichnis steht
2. US1 → **die Erfassung läuft (MVP)**
3. US3 → Ranglisten, und die teuerste Zusage des Blocks ist belegt (null Abfragen beim Öffnen)
4. US2 → das eigene Profil, jetzt mit Rang
5. US4 → das fremde Profil, mit der Grenze, die ein Test über vier Wege bewacht
6. US5 → die Saison kürt und belohnt
7. US6 → das Hologramm im Hub

---

## Notes

- **[P]** = andere Datei, keine offene Abhängigkeit
- Tests sind **Pflicht** (Prinzip VII) und stehen je Geschichte vor der Umsetzung
- Nach jeder Aufgabe oder Gruppe committen
- An jedem Checkpoint kann angehalten und geliefert werden
- **Drei Fallen, die dieses Projekt schon einmal getroffen haben** und deshalb als eigene Aufgaben
  stehen: übersprungene MockBukkit-Tests (T065, T142), der Klassenlader (T146), und eine
  Konfigurationsdatei, die beim Deploy zurückbleibt (T144)
- **Kein Lasttest.** 150 Spieler und 800 Mobs gehören B15 (ADR-031) und halten diesen Block nicht
  offen
