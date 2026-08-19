---

description: "Aufgabenliste für B04 · Attribut- & Stat-Engine"
---

# Tasks: B04 · Attribut- & Stat-Engine

**Input**: Entwurfsdokumente aus `/specs/004-stat-engine/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Enthalten und verpflichtend. Constitution VII verlangt Unit-Tests ohne laufenden Server
für jede Formel und jede Regel der Domänenschicht; SC-005 macht das zum Abnahmekriterium.

**Organisation**: Nach User Stories gruppiert, damit jede Story eigenständig lauffähig und prüfbar
ist.

## Format: `[ID] [P?] [Story] Beschreibung`

- **[P]**: parallelisierbar (andere Datei, keine offene Abhängigkeit)
- **[Story]**: zugehörige User Story aus [spec.md](./spec.md)
- Dateipfade sind vollständig angegeben

## Pfadkonventionen

Multi-Modul-Gradle-Projekt aus B01. Die vier relevanten Wurzeln:

- `rpg-core/src/main/java/rpg/core/stats/` — Regeln, bukkitfrei
- `rpg-platform/src/main/java/rpg/platform/stats/` — Paper-Anbindung
- `rpg-persistence/src/main/java/rpg/persistence/stats/` — Datenzugriff und Modul
- `rpg-plugin/src/main/java/rpg/plugin/` — Zusammenbau

---

## Phase 1: Setup

**Zweck**: Paketgrenzen und Textschlüssel, bevor Code entsteht.

- [ ] T001 Paket `rpg-core/src/main/java/rpg/core/stats/package-info.java` anlegen; Javadoc hält
      die Blockgrenze fest: was B04 besitzt und was ausdrücklich B05, B06, B07, B08, B10, B11 und
      B13 gehört (FR-042)
- [ ] T002 [P] `rpg-core/src/main/java/rpg/core/stats/StatsMessageKeys.java` mit den Textschlüsseln
      für Konfigurationsfehler und abgelehnte Beiträge anlegen, nach dem Muster von
      `SessionMessageKeys`
- [ ] T003 [P] Textschlüssel aus T002 in `rpg-plugin/src/main/resources/messages.yml` ergänzen und
      in `RpgPlugin` in die Liste des `MessageKeyValidator` aufnehmen — ein fehlender Text muss den
      Start abbrechen, nicht später als leere Meldung auftauchen

---

## Phase 2: Foundational (blockierende Voraussetzung)

**Zweck**: Das Attributmodell selbst. Jede User Story baut darauf auf.

**⚠️ KRITISCH**: Keine User-Story-Arbeit vor Abschluss dieser Phase.

**Bewusste Abgrenzung**: Hier entsteht `StatConfig` als **Typ** samt Invarianten und einem in Code
gebauten Auslieferungsstand (`StatConfig.defaults()`). Das Laden aus YAML, das Schema und die
Fail-Fast-Meldungen gehören zu User Story 7 — so bleibt US1 bis US6 ohne Konfigurationsdatei
prüfbar.

- [ ] T004 `rpg-core/src/main/java/rpg/core/stats/Attribute.java`: geschlossener Aufzählungstyp mit
      den acht Konstanten, jeweils mit Konfigurationsschlüssel in `lowerCamelCase` und
      `AttributeKind`; `byKey(String)` wirft bei unbekanntem Schlüssel (FR-001, FR-004, FR-004a)
- [ ] T005 [P] `rpg-core/src/main/java/rpg/core/stats/AttributeKind.java` mit `ABSOLUTE` und
      `PERCENT`
- [ ] T006 [P] `rpg-core/src/main/java/rpg/core/stats/UnknownAttributeException.java`; die Meldung
      nennt den unbekannten Schlüssel **und** die acht erlaubten (FR-009)
- [ ] T007 `rpg-core/src/main/java/rpg/core/stats/AttributeDefinition.java` als Record mit `base`,
      `min`, `max`, `modifierBand`; der kompakte Konstruktor setzt jede Regel aus
      [data-model.md](./data-model.md) §1 durch (FR-002)
- [ ] T008 `rpg-core/src/main/java/rpg/core/stats/StatConfig.java` als Record über
      `Map<Attribute, AttributeDefinition>`; prüft Vollständigkeit aller acht Attribute und bietet
      `defaults()` mit den Werten aus ADR-008 (FR-003)
- [ ] T009 [P] `rpg-core/src/test/java/rpg/core/stats/AttributeDefinitionTest.java`: jede Invariante
      aus T007 mit eigenem Fall, jeweils gegen die **Meldung** geprüft, nicht nur gegen den
      Ausnahmetyp
- [ ] T010 [P] `rpg-core/src/test/java/rpg/core/stats/AttributeTest.java`: acht Konstanten, acht
      eindeutige Schlüssel, `byKey` für jeden gültigen Schlüssel und für einen unbekannten

**Checkpoint**: Das Attributmodell steht und ist geprüft. User Stories können beginnen.

---

## Phase 3: User Story 1 — Ein Spieler hat acht nachvollziehbare Werte (Priority: P1) 🎯 MVP

**Ziel**: Aus Basiswert und Beiträgen entsteht nach einer einzigen Regel ein Endwert je Attribut,
begrenzt auf den erlaubten Bereich.

**Independent Test**: `./gradlew :rpg-core:test --tests "rpg.core.stats.StatCalculatorTest"` — die
gesamte Formel inklusive aller Randfälle, ohne Server, ohne Datenbank, ohne Träger.

### Tests für User Story 1

- [ ] T011 [P] [US1] `rpg-core/src/test/java/rpg/core/stats/StatCalculatorTest.java`: Basis 100 mit
      `+50` flat und `+20 %` ergibt exakt `180.0`; zwei Beiträge von je `+50 %` ergeben Faktor
      `2.0` und nicht `2.25`; ohne Beiträge exakt der Basiswert (FR-011, User Story 1 Szenarien 1
      bis 3)
- [ ] T012 [P] [US1] Im selben Test: Cap-Überschreitung liefert `max`, Prozentsumme unter `−100 %`
      liefert `min` und nie einen negativen Wert, `abilityCooldown` über 40 % liefert genau `0.40`
      (FR-012, FR-013, SC-007, Szenarien 4 bis 6)
- [ ] T013 [P] [US1] Im selben Test: `attackSpeed` mit `+200 %` bei Band `0.50` liefert
      `Basis × 1.5`; `movementSpeed` mit `−90 %` bei Band `0.30` liefert `Basis × 0.7` (FR-014)
- [ ] T014 [P] [US1] `rpg-core/src/test/java/rpg/core/stats/DamageMitigationTest.java`: Verteidigung
      `300` ergibt exakt 75 % Minderung, `0` ergibt exakt 0 %, sehr hohe Werte nähern sich 100 %
      ohne sie zu erreichen, negative Verteidigung bleibt endlich und wechselt kein Vorzeichen
      (FR-015, SC-006)
- [ ] T015 [P] [US1] `rpg-core/src/test/java/rpg/core/stats/StatCalculatorEdgeCaseTest.java`: Wert
      `0` als Basis, als Beitrag und als Grenze; `NaN` und `Infinity` als Beitrag werden abgelehnt
      (SC-005)

### Implementierung für User Story 1

- [ ] T016 [P] [US1] `rpg-core/src/main/java/rpg/core/stats/ModifierOperation.java` mit `FLAT` und
      `PERCENT` (FR-005)
- [ ] T017 [P] [US1] `rpg-core/src/main/java/rpg/core/stats/SourceKind.java` mit `CLASS`, `LEVEL`,
      `EQUIPMENT`, `BUFF`, `AURA`, `ZONE`; die Reihenfolge der Konstanten **ist** die
      Summierreihenfolge (FR-006, research.md E3)
- [ ] T018 [P] [US1] `rpg-core/src/main/java/rpg/core/stats/SourceId.java` als Record aus `kind`
      und `key`, gleichheitsfähig (FR-007)
- [ ] T019 [P] [US1] `rpg-core/src/main/java/rpg/core/stats/StatModifier.java` als Record; lehnt
      `NaN` und `Infinity` im Konstruktor ab (FR-005)
- [ ] T020 [US1] `rpg-core/src/main/java/rpg/core/stats/ModifierSet.java` als Record aus `SourceId`
      und unveränderlicher Beitragsliste; ein leerer Satz ist zulässig
- [ ] T021 [US1] `rpg-core/src/main/java/rpg/core/stats/StatSnapshot.java`: hält ein privates
      `double[]`, gibt Werte ausschließlich über `get(Attribute)` heraus, trägt eine `revision` und
      bietet `isNewerThan` (FR-020, FR-022)
- [ ] T022 [US1] `rpg-core/src/main/java/rpg/core/stats/StatCalculator.java`: die reine Formel
      `clamp((base + Σflat) × (1 + Σpercent), min, max)` samt Band-Klemmung, arbeitet auf
      `double[]`-Zwischenspeichern ohne Allokation je Attribut (FR-011 bis FR-014)
- [ ] T023 [P] [US1] `rpg-core/src/main/java/rpg/core/stats/DamageMitigation.java`: statisch,
      zustandslos, `afterDefense` und `reductionFactor`; der Nenner wird bei `1.0` geklemmt, damit
      negative Verteidigung definiert bleibt (FR-015)
- [ ] T024 [US1] `rpg-core/src/main/java/rpg/core/stats/BaseStatContributor.java` und
      `StatHolderView` als Schnittstellen für B06 und B07; `StatCalculator` befragt registrierte
      Lieferanten vor der Summierung (FR-039)
- [ ] T025 [US1] Fehlerbarriere um jeden Lieferantenaufruf nach dem Muster von B01s
      `ModuleFaultBarrier`: Ausnahme wird mit Lieferanten-ID protokolliert, auf den betroffenen
      Träger begrenzt, die Berechnung läuft mit den übrigen Lieferanten weiter (FR-038)
- [ ] T026 [P] [US1] `rpg-core/src/test/java/rpg/core/stats/BaseStatContributorFaultTest.java`: ein
      werfender Lieferant beeinträchtigt weder das Ergebnis der übrigen noch andere Träger

**Checkpoint**: Die Formel ist vollständig, serverfrei geprüft und für B05 nutzbar. Das ist der
MVP-Kern des Blocks.

---

## Phase 4: User Story 2 — Ausrüstung an- und ablegen verändert Werte verlustfrei (Priority: P1)

**Ziel**: Quellen kommen und gehen; der Ausgangswert wird exakt wiederhergestellt, ohne Drift.

**Independent Test**: `./gradlew :rpg-core:test --tests "rpg.core.stats.ModifierRoundTripTest"` —
1000 Rundläufe, Vergleich mit `isEqualTo`, keine Toleranz.

### Tests für User Story 2

- [ ] T027 [P] [US2] `rpg-core/src/test/java/rpg/core/stats/ModifierRoundTripTest.java`: eine Quelle
      anlegen und ablegen führt bitgleich auf den Ausgangswert zurück; 1000 Wiederholungen
      ebenfalls (FR-017, SC-004)
- [ ] T028 [P] [US2] Im selben Test: dieselbe Quellenmenge in unterschiedlicher Einfügereihenfolge
      liefert bitgleiche Ergebnisse (FR-016)
- [ ] T029 [P] [US2] `rpg-core/src/test/java/rpg/core/stats/ModifierSourceTest.java`: Entfernen
      einer von mehreren Quellen lässt alle übrigen unverändert wirksam; erneutes Registrieren
      derselben Quellen-ID ersetzt vollständig statt zu addieren; Entfernen einer unbekannten
      Quelle ist wirkungslos und löst keine Neuberechnung aus (FR-007, FR-008, User Story 2
      Szenarien 3, 5, 6)
- [ ] T030 [P] [US2] `rpg-core/src/test/java/rpg/core/stats/ContributionQueryTest.java`:
      `contributions(holder, attribute)` nennt jede beteiligte Quelle mit ihrem Beitrag und löst
      dabei keine Neuberechnung aus (FR-010)

### Implementierung für User Story 2

- [ ] T031 [US2] `rpg-core/src/main/java/rpg/core/stats/StatHolder.java`: Träger mit
      `LinkedHashMap<SourceId, ModifierSet>`, `volatile StatSnapshot`, Revisionszähler und
      `characterId` als nullbarem Feld (FR-035)
- [ ] T032 [US2] `rpg-core/src/main/java/rpg/core/stats/StatEngine.java`: die öffentliche
      Schnittstelle vollständig nach [contracts/stat-engine.md](./contracts/stat-engine.md); damit
      stehen die Registrier- und Lesewege für spätere Blöcke fest (FR-040, FR-041)
- [ ] T033 [US2] `rpg-core/src/main/java/rpg/core/stats/DefaultStatEngine.java`: `apply`,
      `applyAll`, `remove`, `removeKind` mit vollständigem Neusummieren aus den verbliebenen
      Quellen — niemals durch Rückrechnen (FR-017, research.md E2)
- [ ] T034 [US2] Summierung in fester Reihenfolge — sortiert nach `SourceKind`, dann nach
      Quellenschlüssel — damit die Einfügereihenfolge das Ergebnis nicht beeinflusst (FR-016,
      research.md E3)
- [ ] T035 [US2] `contributions(UUID, Attribute)` und `AttributeContribution` ergänzen; liest nur
      die Quellenkarte (FR-010)
- [ ] T036 [US2] `createForCharacter`, `createForEntity`, `remove` und `recalculateNow` in
      `DefaultStatEngine`; `remove` ist mehrfach aufrufbar und wirft nicht (FR-036)

**Checkpoint**: Quellen sind vollständig verwaltbar und driftfrei. B11 könnte ab hier gegen die
Schnittstelle entwickeln.

---

## Phase 5: User Story 3 — Werte sofort verfügbar, Server nie blockiert (Priority: P1)

**Ziel**: Genau eine Neuberechnung je Tick und Träger, null Arbeit im Leerlauf, unveränderliche
Schnappschüsse.

**Independent Test**: `./gradlew :rpg-core:test --tests "rpg.core.stats.RecalculationBudgetTest"` —
zählt Neuberechnungen **und** geplante Aufgaben; ein Zeitmesswert allein würde einen billigen, aber
vorhandenen Durchlauf nicht auffallen lassen.

### Tests für User Story 3

- [ ] T037 [P] [US3] `rpg-core/src/test/java/rpg/core/stats/RecalculationBudgetTest.java`: sechs
      Ausrüstungsquellen in einem Tick ergeben genau eine Neuberechnung, ohne dass der Aufrufer sie
      klammert (FR-019, FR-019a, SC-001)
- [ ] T038 [P] [US3] Im selben Test: 200 Träger mit je 20 Quellen über 1200 Ticks ohne Änderung
      ergeben **null** weitere Neuberechnungen und **null** geplante Aufgaben (FR-018, SC-002)
- [ ] T039 [P] [US3] Im selben Test: 100 Träger ändern im selben Tick, Gesamtdauer unter 5 ms
      (SC-003)
- [ ] T040 [P] [US3] `rpg-core/src/test/java/rpg/core/stats/SnapshotImmutabilityTest.java`: ein
      gezogener Schnappschuss bleibt nach weiteren Änderungen unverändert; er überlebt das
      Entfernen seines Trägers; sein internes Array ist von außen nicht erreichbar (FR-020, FR-021)
- [ ] T041 [P] [US3] `rpg-core/src/test/java/rpg/core/stats/RecalcPendingTest.java`: eine Abfrage
      bei ausstehender Vormerkung liefert den zuletzt gültigen Schnappschuss ohne zu rechnen; ein
      entfernter Träger lässt die geplante Aufgabe folgenlos verfallen (FR-022, Edge Cases)

### Implementierung für User Story 3

- [ ] T042 [US3] Vormerkung `recalcPending` als `AtomicBoolean` in `StatHolder`; die erste Änderung
      setzt sie und plant über `Scheduler.runSyncOnEntity` genau eine Aufgabe, jede weitere findet
      sie gesetzt und plant nichts (FR-019, research.md E4)
- [ ] T043 [US3] `recalculateNow` überspringt die Bündelung, rechnet sofort und löscht die
      Vormerkung; genutzt vom Ladepfad und von Trägern ohne Entität (FR-019b)
- [ ] T044 [US3] Die geplante Aufgabe prüft vor dem Rechnen, ob der Träger noch existiert, und
      kehrt sonst folgenlos zurück
- [ ] T045 [P] [US3] `rpg-core/src/main/java/rpg/core/stats/StatsRecalculatedEvent.java` als Record
      mit vorigem und neuem Schnappschuss (FR-023)
- [ ] T046 [US3] Veröffentlichung des Ereignisses über `EventBus` **nach** dem Anstoß der
      Vanilla-Spiegelung, damit kein Abonnent Wert und Anzeige auseinanderlaufen sieht
      ([contracts/events.md](./contracts/events.md))
- [ ] T047 [US3] `rpg-persistence/src/main/java/rpg/persistence/stats/StatsModule.java`: Modul nach
      B01-Vertrag mit `dependencies() == [session]`, baut `DefaultStatEngine` mit
      `StatConfig.defaults()` auf und meldet `StatEngine` als Dienst am Register an
- [ ] T048 [US3] `StatsModule` in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` registrieren
      und starten — ohne diesen Schritt ist der Block auf einem echten Server wirkungslos
      (ADR-012)

**Checkpoint**: Die Engine läuft im Plugin, hält das Tick-Budget ein und meldet Änderungen. Alle
drei P1-Stories sind fertig.

---

## Phase 6: User Story 4 — Die Herzleiste zeigt den Gesundheitsanteil (Priority: P2)

**Ziel**: Vanilla spiegelt, was die Engine berechnet — und arbeitet nicht dagegen.

**Independent Test**: `./gradlew :rpg-platform:test --tests "rpg.platform.stats.*"` gegen
MockBukkit, anschließend Abschnitt 0 des Validierungsleitfadens (übersprungene Tests ausschließen).

### Tests für User Story 4

- [ ] T049 [P] [US4] `rpg-platform/src/test/java/rpg/platform/stats/VanillaAttributeBridgeTest.java`:
      500 von 1000 Leben ergeben 10 angezeigte Punkte bei `GENERIC_MAX_HEALTH` von 20; ein Maximum
      von 2000 ändert daran nichts (FR-030, SC-008, Szenarien 1 und 2)
- [ ] T050 [P] [US4] Im selben Test: 0,4 von 1000 Leben ergeben `0.5` angezeigte Punkte, 0 von 1000
      ergeben `0.0` (FR-031, SC-008, Szenarien 3 und 4)
- [ ] T051 [P] [US4] Im selben Test: eine Änderung an `movementSpeed` oder `attackSpeed` setzt das
      zugehörige Vanilla-Attribut im selben Vorgang; ein Aufruf außerhalb des Ticks wird über den
      Scheduler in den Tick des Trägers geführt (FR-024, FR-032, FR-033)
- [ ] T052 [P] [US4] `rpg-platform/src/test/java/rpg/platform/stats/VanillaRegenerationGuardTest.java`:
      `naturalRegeneration` steht auf `false`, die Sättigung wird festgehalten (FR-030a)
- [ ] T053 [P] [US4] `rpg-platform/src/test/java/rpg/platform/stats/NoDamageInterceptionTest.java`
      als Negativtest: B04 registriert **keinen** Handler auf `EntityDamageEvent` — dieselbe
      Fehlerklasse, die B03s `NoCompetingSessionListenersTest` abdeckt (FR-030b, FR-042)
- [ ] T053a [P] [US4] `rpg-platform/src/test/java/rpg/platform/stats/MirrorBeforeEventTest.java`:
      bei einer Neuberechnung wird die Vanilla-Spiegelung **vor** der Veröffentlichung von
      `StatsRecalculatedEvent` angestoßen; ein Abonnent sieht nie einen Zustand, in dem Wert und
      Anzeige auseinanderlaufen (FR-023, FR-032, contracts/events.md)

### Implementierung für User Story 4

- [ ] T054 [P] [US4] `rpg-core/src/main/java/rpg/core/stats/VanillaAttributeBridge.java` als
      Schnittstelle mit `mirrorHealth`, `mirrorAttackSpeed`, `mirrorMovementSpeed`; ohne
      Registrierung wirkungslos (FR-034)
- [ ] T055 [US4] `rpg-platform/src/main/java/rpg/platform/stats/PaperVanillaAttributeBridge.java`:
      setzt `GENERIC_MAX_HEALTH` einmalig auf 20 und die angezeigte Gesundheit als
      `max(0.5, aktuell / maximal × 20)`; jeder Zugriff läuft über `Scheduler.runSyncOnEntity`
      (FR-030 bis FR-033)
- [ ] T056 [US4] `rpg-platform/src/main/java/rpg/platform/stats/VanillaRegenerationGuard.java`:
      schaltet die Gameregel beim Start ab und hält die Sättigung fest; fasst ausschließlich
      Regenerations- und Sättigungsereignisse an (FR-030a, FR-030b)
- [ ] T057 [US4] Brücke und Wächter in `RpgPlugin` aufbauen, dem `StatsModule` übergeben und den
      Wächter als Listener anmelden — nach `bootstrap.start()`, wie B03 es mit seinen Listenern hält

**Checkpoint**: Ein Spieler sieht seinen tatsächlichen Gesundheitsanteil, und nichts heilt ihn
heimlich wieder hoch.

---

## Phase 7: User Story 5 — Leben und Mana sind belastbare Ressourcen (Priority: P2)

**Ziel**: Ressourcenstände mit Klemmregeln, gespeichert über B02s Write-Behind, geladen im
Vorlade-Pfad von B03.

**Independent Test**: `./gradlew :rpg-core:test --tests "rpg.core.stats.ResourcePoolTest"` für die
Regeln, `./gradlew :rpg-persistence:test --tests "rpg.persistence.stats.*"` für Migration und
Rundlauf gegen echtes PostgreSQL.

### Tests für User Story 5

- [ ] T058 [P] [US5] `rpg-core/src/test/java/rpg/core/stats/ResourcePoolTest.java`: steigendes
      Maximum lässt den Stand unverändert; sinkendes Maximum klemmt ihn ohne Todesfolge; ein
      Verbrauch unter null endet bei null; ein neuer Träger startet auf dem Maximum (FR-025 bis
      FR-027, User Story 5 Szenarien 1 bis 4)
- [ ] T059 [P] [US5] Im selben Test: eine Änderung um null veröffentlicht **kein** Ereignis; das
      Klemmen durch ein gesunkenes Maximum trägt die Ursache `CLAMPED_BY_MAX`
      ([contracts/events.md](./contracts/events.md))
- [ ] T060 [P] [US5] `rpg-persistence/src/test/java/rpg/persistence/stats/CharacterStatsMigrationTest.java`:
      `V4_1` läuft durch, Primär- und Fremdschlüssel samt `ON DELETE CASCADE` und beide
      `CHECK`-Bedingungen sind vorhanden, der Versionsraum bleibt geordnet (`1 < 3.1 < 3.2 < 4.1`)
- [ ] T061 [P] [US5] `rpg-persistence/src/test/java/rpg/persistence/stats/CharacterResourcesRoundTripTest.java`:
      Stand setzen, flushen, neu laden, identischer Stand; das Löschen eines Charakters entfernt
      den Ressourcensatz ohne Zutun von B04; ein Charakter ohne Zeile lädt als neuer Träger mit
      vollem Stand (SC-011)
- [ ] T062 [P] [US5] `rpg-persistence/src/test/java/rpg/persistence/stats/NoWritePerEventTest.java`:
      500 Ressourcenänderungen in einer Sitzung erzeugen genau einen Schreibvorgang je
      Flush-Zyklus, nicht 500 (FR-028, SC-012)
- [ ] T063 [P] [US5] `rpg-core/src/test/java/rpg/core/stats/SessionNotReadyTest.java`: Abfragen und
      Beitragsänderungen für einen Spieler ohne bereite Sitzung werden mit
      `SessionNotReadyException` beantwortet, niemals mit Standardwerten (FR-037)
- [ ] T063a [P] [US5] `rpg-core/src/test/java/rpg/core/stats/EntityHolderBypassesSessionTest.java`:
      ein Träger ohne `characterId` unterliegt der Sitzungsprüfung **nicht** — ein Mob hat keine
      Sitzung und darf daran nicht scheitern (FR-035, FR-037)

### Implementierung für User Story 5

- [ ] T064 [P] [US5] `rpg-core/src/main/java/rpg/core/stats/ResourcePool.java` mit den Klemmregeln
      aus [data-model.md](./data-model.md) §5 (FR-025, FR-026)
- [ ] T065 [P] [US5] `rpg-core/src/main/java/rpg/core/stats/ResourceChangedEvent.java` samt
      `ResourceKind` und `ChangeCause` (FR-029)
- [ ] T066 [US5] `changeHealth`, `changeMana` und `resources` in `DefaultStatEngine`; Rückgabe ist
      der Stand **nach** dem Klemmen, damit der Aufrufer den tatsächlichen Verbrauch erfährt
- [ ] T067 [US5] Das Klemmen bei sinkendem Maximum an die Neuberechnung koppeln, mit Ursache
      `CLAMPED_BY_MAX` — und ausdrücklich **ohne** Todesereignis (Blockgrenze zu B05)
- [ ] T067a [US5] `DefaultStatEngine` erhält die `SessionRegistry` aus B03 als Abhängigkeit und
      prüft für Träger **mit** `characterId` vor jeder Abfrage und jeder Beitragsänderung den
      Bereitschaftszustand; `StatsModule` reicht die Registratur beim Aufbau durch (FR-037)
- [ ] T068 [P] [US5] `rpg-core/src/main/java/rpg/core/stats/CharacterResources.java` als
      persistierbarer Record und
      `rpg-core/src/main/java/rpg/core/stats/CharacterResourcesRepository.java` als Schnittstelle
- [ ] T069 [US5] `AggregateType.CHARACTER_STATS` in
      `rpg-core/src/main/java/rpg/core/persistence/AggregateType.java` ergänzen
- [ ] T070 [US5] `FlushCycle.WRITE_ORDER` in `rpg-persistence/src/main/java/rpg/persistence/FlushCycle.java`
      um `CHARACTER_STATS` **nach** `CHARACTER` erweitern; das Javadoc begründet die Position mit
      der Fremdschlüsselordnung, wie schon bei `CHARACTER`
- [ ] T071 [US5] `rpg-persistence/src/main/resources/db/migration/V4_1__character_stats.sql` nach
      [data-model.md](./data-model.md) §6; der Kopfkommentar hält fest, warum nur Rohwerte und
      niemals berechnete Endwerte gespeichert werden (Prinzip IV, ADR-004)
- [ ] T072 [US5] `rpg-persistence/src/main/java/rpg/persistence/stats/JdbcCharacterResourcesRepository.java`
      nach dem Muster von `JdbcCharacterRepository`, mit Stapelschreiben und Revisionsprüfung
- [ ] T073 [US5] `rpg-core/src/main/java/rpg/core/session/SessionBundle.java` um
      `List<CharacterResources>` erweitern; `empty(...)` und die Prüfungen im kompakten Konstruktor
      mitziehen
- [ ] T074 [US5] `rpg-persistence/src/main/java/rpg/persistence/session/SessionBundleLoader.java` um
      einen vierten `SELECT` auf **derselben** Verbindung und in **derselben** Transaktion
      erweitern — die Eigenschaft „ein Ladevorgang, eine Runde" aus B03 darf nicht verloren gehen
- [ ] T075 [US5] Ladepfad in `StatsModule`: aus dem Bündel einen Träger je Charakter aufbauen,
      `recalculateNow` aufrufen und erst danach freigeben — kein Spieler wird mit ausstehender
      Vormerkung bereit gemeldet (FR-019b)
- [ ] T076 [US5] Entladepfad: beim Sitzungsende Träger entfernen und den Ressourcensatz über
      `WriteBehindCoordinator.markDirty` vormerken; kein eigener Datenbankzugriff (FR-028, FR-036)

**Checkpoint**: Ein Spieler findet seinen Gesundheits- und Manastand nach dem Wiederverbinden vor.

---

## Phase 8: User Story 6 — Dieselbe Engine trägt auch Mobs (Priority: P3)

**Ziel**: Ein Träger ohne Spielerbezug rechnet identisch und hinterlässt nichts.

**Independent Test**: `./gradlew :rpg-core:test --tests "rpg.core.stats.EntityHolderTest"`.

### Tests für User Story 6

- [ ] T077 [P] [US6] `rpg-core/src/test/java/rpg/core/stats/EntityHolderTest.java`: ein Träger ohne
      Spielerbezug liefert bei gleicher Quellenmenge bitgleich dieselben Werte wie ein
      Spielerträger (FR-035, Szenario 1)
- [ ] T078 [P] [US6] Im selben Test: nach `remove` sind Quellen, Schnappschuss und Vormerkung
      freigegeben und die interne Trägerkarte enthält keinen Eintrag mehr (FR-036, Szenario 2,
      SC-010)
- [ ] T079 [P] [US6] Im selben Test: 800 Träger ohne Spielerbezug im Leerlauf erzeugen keine
      geplante Aufgabe und keine Neuberechnung (Szenario 3)
- [ ] T080 [P] [US6] `rpg-core/src/test/java/rpg/core/stats/HolderLifecycleLeakTest.java`: nach 200
      angelegten und wieder entfernten Sitzungsträgern ist die Trägerkarte leer (SC-010)

### Implementierung für User Story 6

- [ ] T081 [US6] `createForEntity` ohne `characterId` in `DefaultStatEngine` vollständig
      ausformulieren; ein Träger ohne `characterId` wird nie persistiert und nie vorgemerkt
      (FR-035)
- [ ] T082 [US6] `remove` gibt Quellenkarte, Schnappschussverweis und Vormerkung frei und ist
      mehrfach aufrufbar (FR-036)

**Checkpoint**: B10 kann Mobs anlegen, ohne ein zweites Wertesystem zu bauen.

---

## Phase 9: User Story 7 — Balancing ohne Codeänderung (Priority: P3)

**Ziel**: Alle Zahlen kommen aus `stats.yml`, geprüft beim Start, mit klarer Meldung bei Fehlern.

**Independent Test**: `./gradlew :rpg-core:test --tests "rpg.core.stats.StatConfigValidationTest"`
plus der manuelle Fehlstart aus Abschnitt 5 des Validierungsleitfadens.

### Tests für User Story 7

- [ ] T083 [P] [US7] `rpg-core/src/test/java/rpg/core/stats/StatConfigValidationTest.java`: je ein
      Fall für jede der neun Prüfregeln aus [contracts/stat-config.md](./contracts/stat-config.md);
      geprüft wird die **Meldung** samt Attribut- und Feldnamen, nicht nur der Ausnahmetyp (FR-003,
      FR-014a, SC-009)
- [ ] T084 [P] [US7] Im selben Test: ein fehlendes Attribut wird namentlich benannt; ein unbekannter
      Schlüssel nennt die acht erlaubten (User Story 7 Szenario 3, FR-004a)
- [ ] T085 [P] [US7] `rpg-core/src/test/java/rpg/core/stats/StatConfigReloadTest.java`: ein
      fehlerhaftes Nachladen lässt den zuletzt gültigen Stand wirksam und meldet den Fehler; ein
      gültiges Nachladen vermerkt alle bekannten Träger zur Neuberechnung (Szenario 4)

### Implementierung für User Story 7

- [ ] T086 [US7] `ConfigSchema<StatConfig>` in `StatConfig` ergänzen, nach dem Muster von
      `SessionConfig`; jede der neun Regeln aus dem Vertrag wird durchgesetzt
- [ ] T087 [US7] `rpg-plugin/src/main/resources/stats.yml` mit den Auslieferungswerten aus
      [contracts/stat-config.md](./contracts/stat-config.md), mit Kommentaren auf Englisch
- [ ] T088 [US7] `stats.yml` in `RpgPlugin.DEFAULT_CONFIG_FILES` aufnehmen, damit die Datei beim
      ersten Start angelegt wird und Betreiber-Änderungen jeden Neustart überleben
- [ ] T089 [US7] `StatsModule` lädt die Konfiguration über `ConfigLoader.register` statt
      `StatConfig.defaults()` zu verwenden; ein Fehler bricht den Start ab
- [ ] T090 [US7] Beim Nachladen alle bekannten Träger vormerken — der einzige Fall, in dem B04 über
      alle Träger läuft, und nur auf ausdrückliche Anweisung eines Betreibers

**Checkpoint**: Eine Balancing-Runde ist eine Dateiänderung, kein Build.

---

## Phase 10: Polish & Querschnitt

- [ ] T091 `rpg-plugin/src/test/java/rpg/plugin/FullBootstrapTest.java` erweitern: `StatsModule` ist
      angemeldet und startet nach `SessionModule`, `StatEngine` ist als Dienst abrufbar, `stats.yml`
      wird angelegt, der Regenerationswächter ist als Listener registriert und
      `CHARACTER_STATS` steht in `FlushCycle` nach `CHARACTER` (ADR-012)
- [ ] T092 Abschnitt 0 des Validierungsleitfadens ausführen: alle XML-Testberichte auf
      `skipped="[1-9]` prüfen — MockBukkit meldet Nicht-Implementiertes als übersprungen, nicht als
      Fehler, und die Konsole sieht dann grün aus
- [ ] T093 `./gradlew spotlessApply build` und den vollständigen Testlauf grün stellen
- [ ] T094 [P] ADR-013 in `02-decisions.md` ergänzen: die Umsetzungsentscheidungen aus
      [research.md](./research.md), insbesondere die entitätsgebundene Bündelung statt eines
      globalen Tick-Durchlaufs, die eigene Ressourcentabelle und die Erweiterung von B03s
      Bündellader (Governance-Regel: Entscheidungen wandern zurück)
- [ ] T095 [P] Status in `minecraft-rpg-spec/minecraft-rpg-spec/blocks/B04-stat-engine.md` auf
      implementiert setzen und auf `specs/004-stat-engine/` verweisen
- [ ] T096 [P] `06-open-questions.md`: den B04-Abschnitt als abgeschlossen markieren und die fünf
      Klärungen aus dieser Runde eintragen
- [ ] T097 Abschnitte 1 bis 7 des Validierungsleitfadens vollständig durchlaufen
- [ ] T098 Abschnitt 8 des Validierungsleitfadens auf einem echten Paper-Server durchlaufen —
      sinnvoll erst gemeinsam mit den offenen Serverprüfungen aus B02 und B03

---

## Dependencies & Execution Order

### Phasenabhängigkeiten

- **Setup (Phase 1)**: keine Abhängigkeit, kann sofort beginnen
- **Foundational (Phase 2)**: nach Setup — **blockiert alle User Stories**
- **US1 (Phase 3)**: nach Foundational
- **US2 (Phase 4)**: nach US1 — braucht `StatCalculator` und `StatSnapshot`
- **US3 (Phase 5)**: nach US2 — braucht den Träger und die Quellenverwaltung
- **US4 (Phase 6)**: nach US3 — die Spiegelung hängt an der Neuberechnung
- **US5 (Phase 7)**: nach US3; unabhängig von US4
- **US6 (Phase 8)**: nach US3 — T079 prüft, dass 800 Träger im Leerlauf keine Aufgabe planen, und
  braucht dafür die Bündelung aus T042; unabhängig von US4 und US5
- **US7 (Phase 9)**: nach US3 — T088 und T089 fassen `RpgPlugin` und `StatsModule` an, die dort
  entstehen; unabhängig von US4, US5 und US6
- **Polish (Phase 10)**: nach allen gewünschten Stories

### Innerhalb einer Story

Tests werden zuerst geschrieben und müssen fehlschlagen, bevor implementiert wird. Danach: Typen →
Berechnung → Engine → Verdrahtung.

### Parallelisierbare Abschnitte

- T002 und T003 im Setup
- T005, T006 sowie T009 und T010 in Foundational
- Alle Tests einer Story untereinander (T011–T015, T027–T030, T037–T041, T049–T053a, T058–T063a,
  T077–T080, T083–T085)
- Die reinen Typen in US1 (T016–T019) und in US5 (T064, T065, T068)
- T094, T095 und T096 in der Polish-Phase

**Nicht parallel**, obwohl es so aussieht: T033, T034, T035, T036, T042, T043, T066, T067a und T081
fassen alle `DefaultStatEngine.java` an.

---

## Parallel Example: User Story 1

```bash
# Zuerst alle Tests gemeinsam schreiben:
Task: "StatCalculatorTest — Formel, Prozentsumme, Caps"
Task: "DamageMitigationTest — Divisor-Modell und Randfälle"
Task: "StatCalculatorEdgeCaseTest — Null, NaN, Infinity"

# Danach die Typen gemeinsam:
Task: "ModifierOperation"
Task: "SourceKind"
Task: "SourceId"
Task: "StatModifier"
```

---

## Implementation Strategy

### MVP zuerst

1. Phase 1 und 2 abschließen
2. Phase 3 (US1) abschließen — **anhalten und prüfen**: die Formel ist vollständig, serverfrei
   geprüft und für B05 nutzbar
3. Phase 4 und 5 (US2, US3) — damit sind alle P1-Stories fertig und die Engine läuft im Plugin

Nach Phase 5 ist B04 aus Sicht von B05 vollständig: Werte, Schnappschüsse und die Minderungsformel
stehen. B05 könnte ab hier beginnen, auch wenn US4 bis US7 noch offen sind.

### Inkrementelle Auslieferung

Jede weitere Phase ist ein eigenständiger Zugewinn: US4 macht die Werte sichtbar, US5 macht sie
dauerhaft, US6 öffnet sie für Mobs, US7 für das Balancing.

---

## Notes

- `[P]` bedeutet: andere Datei, keine offene Abhängigkeit
- Tests vor der Implementierung schreiben und fehlschlagen sehen — sonst prüft der Test die
  Implementierung statt die Anforderung
- Nach jedem Testlauf Abschnitt 0 des Validierungsleitfadens ausführen (übersprungene Tests)
- Der Block gilt erst als fertig, wenn T091 grün ist: ein Modul, das im Plugin nicht verdrahtet
  ist, ist auf einem echten Server wirkungslos, egal wie grün seine eigenen Tests sind (ADR-012)
