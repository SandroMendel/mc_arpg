---

description: "Aufgabenliste für B05 · Kampf- & Schadens-Pipeline"
---

# Tasks: B05 · Kampf- & Schadens-Pipeline

**Input**: Entwurfsdokumente aus `/specs/005-combat-pipeline/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Enthalten und verpflichtend. Constitution VII verlangt serverfreie Unit-Tests für jede
Formel und jede Regel der Domänenschicht — **und nennt B05 ausdrücklich als lasttestpflichtigen
Block.** Ohne den Nachweis aus Phase 12 gilt B05 nicht als fertig.

**Organisation**: Nach User Stories gruppiert.

## Format: `[ID] [P?] [Story] Beschreibung`

- **[P]**: parallelisierbar (andere Datei, keine offene Abhängigkeit)
- **[Story]**: zugehörige User Story aus [spec.md](./spec.md)

## Pfadkonventionen

- `rpg-core/src/main/java/rpg/core/combat/` — Regeln, bukkitfrei, **einschließlich `CombatModule`**
  (B05 hat keine Datenbank, siehe plan.md)
- `rpg-platform/src/main/java/rpg/platform/combat/` — Paper-Anbindung
- `rpg-plugin/` — Zusammenbau und `combat.yml`

---

## Phase 1: Setup

- [X] T001 `rpg-core/src/main/java/rpg/core/combat/package-info.java`: Javadoc hält die Blockgrenze
      fest — was B05 besitzt und was ausdrücklich B06, B08, B09, B10, B11, B12 und B13 gehört
      (FR-047)
- [X] T002 [P] `rpg-core/src/test/java/rpg/core/combat/CombatFixture.java`: gemeinsame
      Testumgebung mit **gesteuerter Uhr** und zählendem Scheduler. Die Uhr ist nicht Bequemlichkeit
      — drei Regeln des Blocks sind zeitbasiert, und Tests mit echten Wartezeiten wären langsam und
      wackelig

**Hinweis zu Message-Keys**: B05 hat keinen spielerseitigen Text — Todesmeldungen kommen von
Vanilla, Konfigurationsfehler gehen ins Betreiber-Protokoll. Wie in B04 entfällt eine
Schlüsselklasse. Nachzuholen, sobald B05 einen Spielertext bekommt.

---

## Phase 2: Foundational (blockierende Voraussetzung)

**Zweck**: Schadensbeschreibung und Pipeline-Gerüst. Jede User Story baut darauf auf.

**⚠️ KRITISCH**: Keine User-Story-Arbeit vor Abschluss dieser Phase.

**Bewusste Abgrenzung**: `CombatConfig` entsteht hier als **Typ** samt Invarianten und einem in Code
gebauten Auslieferungsstand (`defaults()`). Das Laden aus YAML gehört zu User Story 2 — so bleiben
US1 und US3 ohne Konfigurationsdatei prüfbar (dasselbe Vorgehen wie bei B04).

- [X] T003 [P] `rpg-core/src/main/java/rpg/core/combat/DamageType.java` mit `PHYSICAL`, `MAGIC`,
      `ENVIRONMENT`; jeder Typ weiß, welches Attribut er verwendet und ob Verteidigung greift
      (FR-002, FR-012b)
- [X] T004 [P] `rpg-core/src/main/java/rpg/core/combat/DamageOrigin.java` mit `MELEE`,
      `PROJECTILE`, `ABILITY`, `ENVIRONMENT`, `ADMIN`
- [X] T005 [P] `rpg-core/src/main/java/rpg/core/combat/PipelineStage.java` mit den sechs Stufen aus
      [data-model.md](./data-model.md) §2 (FR-007)
- [X] T006 `rpg-core/src/main/java/rpg/core/combat/DamageContext.java`: veränderlich und
      wiederverwendbar mit `reset()`; das Javadoc benennt die Vertragsregel, dass niemand ihn über
      seinen Vorgang hinaus festhält (FR-045, research.md E2)
- [X] T007 `rpg-core/src/main/java/rpg/core/combat/DamageView.java`: unveränderliche Lesesicht mit
      genau drei Änderungswegen (`setRawDamage`, `setFinalDamage`, `cancel`); nur während des
      Aufrufs gültig
- [X] T008 [P] `rpg-core/src/main/java/rpg/core/combat/DamageInterceptor.java` als Schnittstelle für
      B08 und B11 (FR-008)
- [X] T009 `rpg-core/src/main/java/rpg/core/combat/CombatPipeline.java`: die öffentliche
      Schnittstelle vollständig nach
      [contracts/combat-pipeline.md](./contracts/combat-pipeline.md)
- [X] T010 `rpg-core/src/main/java/rpg/core/combat/DefaultCombatPipeline.java`: Ablauf über die
      sechs Stufen, Abbruch an jeder Stufe, Fehlerbarriere je Eingriffspunkt nach dem Muster von
      B01s `ModuleFaultBarrier` (FR-007, FR-009, FR-010)
- [X] T011 `rpg-core/src/main/java/rpg/core/combat/CombatConfig.java` als Record mit Invarianten und
      `defaults()` aus [contracts/combat-config.md](./contracts/combat-config.md)
- [X] T012 [P] `rpg-core/src/test/java/rpg/core/combat/CombatConfigTest.java`: jede Invariante mit
      eigenem Fall, gegen die **Meldung** geprüft, nicht nur gegen den Ausnahmetyp
- [X] T013 [P] `rpg-core/src/test/java/rpg/core/combat/DamageContextReuseTest.java`: ein
      zurückgesetzter Vorgang trägt keinen Rest des vorigen; eine festgehaltene Lesesicht ist nach
      dem Aufruf nicht mehr verwendbar
- [X] T014 [P] `rpg-core/src/test/java/rpg/core/combat/PipelineStageOrderTest.java`: die sechs
      Stufen laufen in der festgelegten Reihenfolge, und ein Abbruch in jeder einzelnen beendet den
      Vorgang folgenlos

**Checkpoint**: Das Gerüst steht. User Stories können beginnen.

---

## Phase 3: User Story 1 — Ein Schlag trifft, und der Schaden stimmt (Priority: P1) 🎯 MVP

**Ziel**: Aus Angreiferattribut, Faktor und Zielverteidigung entsteht ein nachvollziehbarer,
reproduzierbarer Schadenswert.

**Independent Test**: `./gradlew :rpg-core:test --tests "rpg.core.combat.DamageFormulaTest"` — jede
Beispielrechnung aus [data-model.md](./data-model.md) §3, ohne Server, ohne Datenbank.

### Tests für User Story 1

- [X] T015 [P] [US1] `rpg-core/src/test/java/rpg/core/combat/DamageFormulaTest.java`: 50 physisch
      gegen 100 Verteidigung ergibt exakt 25,0; 100 gegen 300 exakt 25,0 (75 % Minderung); 100 gegen
      0 exakt 100,0 (FR-001, FR-003, SC-002)
- [X] T016 [P] [US1] Im selben Test: magischer Schaden verwendet `magicDamage`, nicht
      `physicalDamage`; ein Faktor von 1,8 auf 40 magisch gegen 100 Verteidigung ergibt exakt 36,0
      (FR-002, FR-002a)
- [X] T017 [P] [US1] Im selben Test: dieselbe Eingabe liefert bitgleich dasselbe Ergebnis über 1000
      Durchläufe — es gibt keinen Zufall im Schaden (FR-004)
- [X] T018 [P] [US1] `rpg-core/src/test/java/rpg/core/combat/FallDamageTest.java`: Sturz aus 10
      Blöcken bei Auslieferungswerten ergibt 28,0; unterhalb der sicheren Höhe null; die Obergrenze
      greift; Verteidigung ändert **nichts** (FR-012a bis FR-012c, SC-012a)
- [X] T019 [P] [US1] Im selben Test: derselbe Sturz kostet bei 100 maximalem Leben einen deutlich
      größeren Anteil als bei 2000, bei gleichem absolutem Betrag — die Designentscheidung als Test
- [ ] T020 [P] [US1] `rpg-core/src/test/java/rpg/core/combat/DamageEdgeCaseTest.java`: negativer und
      nicht endlicher Rohschaden werden abgelehnt und protokolliert, nicht als Heilung gedeutet
      (FR-006)

### Implementierung für User Story 1

- [X] T021 [US1] `rpg-core/src/main/java/rpg/core/combat/DamageFormula.java`: statisch,
      zustandslos; `rawDamage`, `afterDefence` (delegiert an B04s `DamageMitigation`) und
      `fallDamage` (FR-001 bis FR-006)
- [X] T022 [US1] Stufe `RAW_DAMAGE` in `DefaultCombatPipeline`: Basisattribut aus dem
      Angreifer-Schnappschuss × Faktor, bei `ENVIRONMENT` der konfigurierte Betrag
- [X] T023 [US1] Stufe `DEFENCE`: Divisor-Modell anwenden — bei `ENVIRONMENT` **übersprungen**
      (FR-012b)
- [X] T024 [US1] Der Angreifer-Schnappschuss wird **einmal** zu Beginn gezogen und im Vorgang
      gehalten; das Javadoc benennt, warum ein erneutes Abfragen mitten im Vorgang ein Fehler wäre
      (FR-005)

**Checkpoint**: Die Formel ist vollständig und serverfrei geprüft. Das ist der MVP-Kern.

---

## Phase 4: User Story 2 — Kein Vanilla-Schaden kommt jemals durch (Priority: P1)

**Ziel**: Jede der 33 Vanilla-Schadensursachen hat eine ausdrückliche Behandlung, und keine kommt
ungefiltert durch.

**Independent Test**: `./gradlew :rpg-platform:test --tests "rpg.platform.combat.VanillaDamageMappingTest"`

### Tests für User Story 2

- [X] T025 [P] [US2] `rpg-platform/src/test/java/rpg/platform/combat/VanillaDamageMappingTest.java`:
      der Test **iteriert über den Aufzählungstyp**, nicht über eine Liste — jede der 33
      `DamageCause`-Konstanten hat eine Zuordnung, und eine neu hinzukommende bringt den Test zum
      Fehlschlagen (FR-011, SC-001)
- [X] T026 [P] [US2] Im selben Test: die Zuordnung entspricht Zeile für Zeile
      [contracts/damage-sources.md](./contracts/damage-sources.md)
- [X] T027 [P] [US2] Im selben Test: eine unbekannte Ursache wird neutralisiert **und** protokolliert
      — der Verweigerungs-Standardfall, der die Richtung des Risikos umdreht (research.md E1)
- [ ] T028 [P] [US2] `rpg-platform/src/test/java/rpg/platform/combat/VanillaDamageListenerTest.java`:
      kein Vanilla-Ereignis verlässt den Listener mit einem Schaden über null (FR-016)
- [ ] T029 [P] [US2] Im selben Test: die Trefferanimation wird ausdrücklich ausgelöst, weil ein auf
      null gesetztes Ereignis von sich aus keine zeigt (FR-017)
- [ ] T030 [P] [US2] Im selben Test: ein Wesen ohne Stat-Träger bleibt vollständig unangetastet
      (FR-018)
- [ ] T031 [P] [US2] Im selben Test: Void und `/kill` töten sofort, unabhängig vom Lebenswert
      (FR-014, FR-015)
- [ ] T032 [P] [US2] Im selben Test: die Statuseffekte aus der Abschaltliste haben keinerlei Wirkung
      (FR-013)
- [ ] T033 [P] [US2] `rpg-core/src/test/java/rpg/core/combat/CombatConfigSchemaTest.java`: jede
      Prüfregel aus [contracts/combat-config.md](./contracts/combat-config.md) mit eigenem Fall,
      gegen die Meldung geprüft

### Implementierung für User Story 2

- [X] T034 [US2] `rpg-platform/src/main/java/rpg/platform/combat/VanillaDamageMapping.java`:
      **vollständiger Switch** über `DamageCause` mit Verweigerungs-Standardfall; eine fehlende
      Konstante meldet der Compiler, nicht der Betrieb (FR-011)
- [X] T035 [US2] `rpg-platform/src/main/java/rpg/platform/combat/VanillaDamageListener.java`: fängt
      jedes Schadensereignis ab, setzt den Vanilla-Wert auf null und leitet an die Pipeline weiter
      (FR-016 bis FR-019)
- [X] T036 [US2] `noDamageTicks` **jedes Trägers unter B05-Kontrolle auf null setzen** — beim
      Anlegen des Trägers und nach jedem angewandten Treffer, in `VanillaDamageListener`. Sonst
      deckelt Vanillas versteckte
      Unverwundbarkeit die eigene Angriffsgeschwindigkeit stillschweigend bei zwei Treffern je
      Sekunde (research.md E6)
- [X] T037 [US2] `ConfigSchema<CombatConfig>` in `CombatConfig` ergänzen, nach dem Muster von
      `StatConfig.schema()` aus B04
- [X] T038 [US2] `rpg-plugin/src/main/resources/combat.yml` mit den Auslieferungswerten aus
      [contracts/combat-config.md](./contracts/combat-config.md), Kommentare auf Englisch
- [X] T039 [US2] `combat.yml` in `RpgPlugin.DEFAULT_CONFIG_FILES` aufnehmen
- [X] T040 [US2] Umgebungsschaden in der Pipeline: fester Betrag aus der Konfiguration, **ohne**
      Verteidigung, Fallschaden über die Höhenformel (FR-012a bis FR-012c)

**Checkpoint**: Das Vanilla-Kampfsystem ist vollständig ersetzt.

---

## Phase 5: User Story 3 — Der Server hält den Dauerkampf aus (Priority: P1)

**Ziel**: Der meistdurchlaufene Codepfad des Plugins ist auch der sparsamste.

**Independent Test**: `./gradlew :rpg-core:test --tests "rpg.core.combat.CombatBudgetTest"` — zählt
**Allokationen und geplante Aufgaben**, nicht nur Millisekunden. Ein Zeitmesswert allein würde einen
billigen, aber vorhandenen Durchlauf durchgehen lassen.

### Tests für User Story 3

- [X] T041 [P] [US3] `rpg-core/src/test/java/rpg/core/combat/CombatBudgetTest.java`: 10 000 Treffer
      in Folge erzeugen keinen neuen Schadensvorgang — derselbe Kontext wird wiederverwendet
      (FR-045, SC-005)
- [X] T042 [P] [US3] Im selben Test: über 1200 Ticks ohne Kampf wird **keine** Aufgabe geplant und
      keine Zeit verbraucht (FR-044, SC-013)
- [X] T043 [P] [US3] Im selben Test: 150 Angreifer gegen 800 Ziele, ein Durchgang je Ziel, bleibt
      unter dem Tick-Budget — der serverfreie Vorlauf zum echten Lasttest aus Phase 12
- [X] T044 [P] [US3] Im selben Test: das Beitragsfenster eines Ziels überschreitet seine Höchstzahl
      nie, unabhängig von der Zahl der Angreifer (FR-032, SC-008)
- [X] T045 [P] [US3] `rpg-core/src/test/java/rpg/core/combat/PipelineFaultBarrierTest.java`: ein
      werfender Eingriffspunkt wird protokolliert, der Vorgang endet sauber, andere Kämpfe und
      andere Träger bleiben unberührt (FR-010)
- [X] T045a [P] [US3] `rpg-core/src/test/java/rpg/core/combat/SessionNotReadyCombatTest.java`: ein
      Spieler ohne freigegebene Sitzung kann weder Schaden nehmen noch austeilen; der Vorgang wird
      mit `RejectReason.SESSION_NOT_READY` abgewiesen, **nicht** mit Standardwerten gerechnet
      (FR-046). Ein Mob hat keine Sitzung und unterliegt der Prüfung nicht — dieselbe Unterscheidung
      wie in B04

### Implementierung für User Story 3

- [X] T046 [US3] Wiederverwendung des Schadensvorgangs je Tick-Thread in `DefaultCombatPipeline`,
      mit `reset()` nach jedem Vorgang (FR-045)
- [X] T047 [US3] Die Formel und alle Zwischenschritte arbeiten auf `double` — kein Boxing, keine
      Streams, keine Zwischenlisten im Schadenspfad (Prinzip II)
- [X] T047a [US3] `DefaultCombatPipeline` erhält die `SessionRegistry` aus B03 und prüft in der
      Stufe `SOURCE` für Träger **mit** Charakter den Bereitschaftszustand; `CombatModule` reicht
      die Registratur beim Aufbau durch (FR-046)
- [X] T048 [US3] `rpg-core/src/main/java/rpg/core/combat/CombatModule.java`: Modul nach B01-Vertrag
      mit `dependencies() == [stats]`, baut die Pipeline auf und meldet `CombatPipeline` als Dienst
      am Register an
- [X] T049 [US3] `CombatModule` in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` registrieren
      — ohne diesen Schritt ist der Block auf einem echten Server wirkungslos (ADR-012)
- [X] T050 [US3] `VanillaDamageListener` in `RpgPlugin` anmelden, nach `bootstrap.start()`, wie B03
      und B04 es halten

**Checkpoint**: Die Pipeline läuft im Plugin und hält ihr Budget. Alle drei P1-Stories sind fertig.

---

## Phase 6: User Story 4 — Angriffsgeschwindigkeit begrenzt die Schlagfolge (Priority: P2)

**Ziel**: `attackSpeed` bestimmt die Schlagfolge; Klick-Spam bringt nichts.

**Independent Test**: `./gradlew :rpg-core:test --tests "rpg.core.combat.AttackWindowTest"` mit
gesteuerter Uhr.

### Tests für User Story 4

- [X] T051 [P] [US4] `rpg-core/src/test/java/rpg/core/combat/AttackWindowTest.java`: bei 4 Angriffen
      je Sekunde zählen von zehn Schlägen in einer Sekunde genau vier (FR-020, SC-006)
- [X] T052 [P] [US4] Im selben Test: ein verworfener Schlag erzeugt weder Schaden noch Animation
      noch Beitrag (FR-021)
- [X] T053 [P] [US4] Im selben Test: eine geänderte Angriffsgeschwindigkeit gilt sofort beim
      nächsten Schlag, ohne dass etwas neu geplant wird (FR-023)
- [X] T054 [P] [US4] Im selben Test: über den gesamten Lauf wird **null** Aufgabe geplant — der
      eigentliche Beweis für Prinzip II (FR-022)
- [ ] T055 [P] [US4] `rpg-platform/src/test/java/rpg/platform/combat/VanillaWeaponCooldownTest.java`:
      der Vanilla-Waffencooldown hat keinen Einfluss auf den Schaden; die Spiegelung aus B04 bleibt
      bestehen und treibt nur noch die Anzeige (FR-024, research.md E7)

### Implementierung für User Story 4

- [X] T056 [US4] `rpg-core/src/main/java/rpg/core/combat/AttackWindow.java`: ein Zeitstempel je
      Angreifer, nur bei Zugriff ausgewertet; `minimumGap = 1000 ms / attackSpeed` (FR-020 bis
      FR-023)
- [X] T057 [US4] Prüfung in die Stufe `SOURCE` einhängen; `abilityDamage` prüft sie **nicht** —
      Fähigkeiten haben eigene Abklingzeiten in B08, und beides zu prüfen begrenzte sie doppelt
- [X] T058 [US4] Aufräumen beim Sitzungsende und beim Entfernen eines Trägers, an denselben Stellen
      wie B04 seine Träger freigibt — kein eigener Aufräumdurchlauf

**Checkpoint**: Die Schlagfolge folgt dem Attribut, nicht der Maus.

---

## Phase 7: User Story 6 — Bei einer Horde bekommt jeder seinen Anteil (Priority: P2)

**Ziel**: XP anteilig, Beute an den größten Beitragenden — mit einem Fenster, das nicht wächst.

**Independent Test**: `./gradlew :rpg-core:test --tests "rpg.core.combat.AttributionWindowTest"`

### Tests für User Story 6

- [X] T071 [P] [US6] `rpg-core/src/test/java/rpg/core/combat/AttributionWindowTest.java`: drei
      Angreifer mit 60 %, 30 % und 10 % ergeben genau diese Anteile; größter Beitragender ist der
      erste (FR-034, SC-007)
- [X] T072 [P] [US6] Im selben Test: 100 Angreifer bei 16 Plätzen lassen das Fenster bei 16; der
      kleinste Beitrag weicht (FR-032, SC-008)
- [X] T073 [P] [US6] Im selben Test: ein Beitrag älter als die Verfallszeit zählt nicht mehr; ein
      Angreifer, der nach langer Pause zurückkehrt, ist wieder beteiligt — mit dem Beitrag ab der
      Rückkehr (FR-033)
- [X] T074 [P] [US6] Im selben Test: Selbstschaden erzeugt keinen Beitrag (FR-035); ein Ziel ohne
      jeden Spielerbeitrag liefert eine leere Aufteilung (FR-034)
- [X] T075 [P] [US6] Im selben Test: das Fenster wird beim Tod und beim Entfernen des Ziels
      vollständig freigegeben (FR-036)
- [X] T076 [P] [US6] Im selben Test: kein Zugriff auf das Fenster allokiert — geprüft über 10 000
      Beiträge

### Implementierung für User Story 6

- [X] T077 [US6] `rpg-core/src/main/java/rpg/core/combat/AttributionWindow.java`: drei parallele
      Arrays fester Größe je Ziel, lineare Suche über höchstens 16 Plätze, Verdrängung des
      kleinsten Beitrags, Verfall beim Zugriff (FR-031 bis FR-033, research.md E5)
- [X] T078 [P] [US6] `rpg-core/src/main/java/rpg/core/combat/DamageShare.java` mit Anteilen, größtem
      Beitragenden und Gesamtschaden (FR-034)
- [X] T079 [US6] Beitrag in der Stufe `APPLICATION` vermerken; Selbstschaden übersprungen (FR-035)
- [X] T080 [US6] Aufteilung beim Tod **einmal** erzeugen und ins Todesereignis legen — der einzige
      Punkt, an dem eine Allokation je Vorgang bewusst in Kauf genommen wird, weil ein Tod selten
      ist und das Ergebnis den Vorgang überlebt

**Checkpoint**: Gemeinsames Spielen an einer Horde lohnt sich für alle Beteiligten.

---

## Phase 8: User Story 5 — Ein Tod hat einen Verursacher und Folgen (Priority: P2)

**Ziel**: Tod wird erkannt, genau einmal gemeldet, und trägt alles, was B06 und B11 brauchen.

**Independent Test**: `./gradlew :rpg-core:test --tests "rpg.core.combat.DeathHandlingTest"`

### Tests für User Story 5

- [X] T059 [P] [US5] `rpg-core/src/test/java/rpg/core/combat/DeathHandlingTest.java`: ein Treffer,
      der auf null bringt, erzeugt genau ein Todesereignis mit dem richtigen Verursacher (FR-025 bis
      FR-027)
- [X] T060 [P] [US5] Im selben Test: zwei tödliche Treffer im selben Tick erzeugen **ein**
      Todesereignis; weiterer Schaden auf ein totes Ziel bleibt folgenlos (FR-026, SC-011)
- [X] T061 [P] [US5] Im selben Test: ein Tod durch Umgebungsschaden ohne Angreifer ist ein gültiger
      Fall mit leerem Verursacher (FR-027)
- [X] T062 [P] [US5] Im selben Test: das Todesereignis trägt die vollständige Schadensaufteilung
      (FR-028)
- [X] T063 [P] [US5] `rpg-platform/src/test/java/rpg/platform/combat/CombatDeathListenerTest.java`:
      beim Tod eines Wesens erscheinen weder Vanilla-Erfahrungskugeln noch Vanilla-Beute (FR-030a,
      FR-030b, SC-010f)
- [X] T064 [P] [US5] Im selben Test: ein Spieler verliert beim Tod kein Item — unabhängig von der
      Todesursache, auch im Void und in Lava (FR-029b, SC-010b)
- [X] T065 [P] [US5] Im selben Test: der Vanilla-Todesbildschirm bleibt; nach dem Wiedererscheinen
      hat der Spieler volles Leben und volles Mana (FR-029, FR-029a)
- [X] T066 [P] [US5] `rpg-core/src/test/java/rpg/core/combat/NoEquipmentAccessTest.java` als
      Negativtest über die Quellen: B05 greift nirgends auf Ausrüstung, Haltbarkeit oder Itemwerte
      zu (FR-030) — dieselbe Fehlerklasse, die B04s `NoDamageInterceptionTest` abdeckt

### Implementierung für User Story 5

- [X] T067 [US5] Todeserkennung in der Stufe `AFTERMATH`, mit einer Sperre gegen ein zweites
      Ereignis (FR-025, FR-026)
- [X] T068 [P] [US5] `rpg-core/src/main/java/rpg/core/combat/CombatDeathEvent.java` und
      `DeathCause` nach [contracts/events.md](./contracts/events.md)
- [X] T069 [US5] `rpg-platform/src/main/java/rpg/platform/combat/CombatDeathListener.java`:
      Erfahrung und Beute unterdrücken, Respawn auffüllen
- [X] T070 [US5] Gameregel `keep_inventory` auf `true` setzen, beim Start und bei jedem später
      geladenen Welt-Ereignis — nach dem Muster von B04s `VanillaRegenerationGuard` (FR-029b)

**Checkpoint**: Mobs fallen um, Spieler sterben ohne Verlust, und B06 und B11 haben ihre Grundlage.

---

## Phase 9: User Story 7 — Treffer sind sichtbar, ohne den Server zu belasten (Priority: P3)

**Ziel**: Rückmeldung je Treffer, Schadenszahl zusammengefasst, keine Anzeigeobjekte aus B05.

**Independent Test**: `./gradlew :rpg-core:test --tests "rpg.core.combat.DamageAggregatorTest"`

### Tests für User Story 7

- [X] T081 [P] [US7] `rpg-core/src/test/java/rpg/core/combat/DamageAggregatorTest.java`: zwanzig
      Treffer im Fenster ergeben **ein** Ereignis mit `hitCount == 20` und korrekter Summe (FR-038,
      SC-009)
- [X] T082 [P] [US7] Im selben Test: ein Treffer ohne Schadenswirkung erzeugt kein Ereignis (FR-040)
- [X] T083 [P] [US7] Im selben Test: Treffer verschiedener Angreifer auf dasselbe Ziel werden
      **nicht** zusammengefasst — die Bündelung gilt je Angreifer-Ziel-Paar
- [X] T084 [P] [US7] Im selben Test: der Tod des Ziels schließt das Fenster sofort ab, statt auf den
      Ablauf zu warten
- [ ] T085 [P] [US7] `rpg-platform/src/test/java/rpg/platform/combat/PaperDamageFeedbackTest.java`:
      Trefferanimation und Rückstoß werden **je Treffer** ausgelöst — die Zusammenfassung betrifft
      nur die Zahl (FR-037)
- [X] T086 [P] [US7] `rpg-platform/src/test/java/rpg/platform/combat/NoDisplayEntityTest.java` als
      Negativtest über die Quellen: B05 erzeugt weder Text-Displays noch Holograms noch sonstige
      Anzeigeobjekte (FR-039)

### Implementierung für User Story 7

- [X] T087 [P] [US7] `rpg-core/src/main/java/rpg/core/combat/DamageFeedback.java` als Schnittstelle;
      ohne Registrierung wirkungslos, was die Pipeline serverfrei prüfbar hält
- [X] T088 [US7] `rpg-platform/src/main/java/rpg/platform/combat/PaperDamageFeedback.java`:
      `playHurtAnimation` und `knockback` über die öffentliche Paper-API, im Tick des Trägers
      (FR-037)
- [X] T089 [US7] `rpg-core/src/main/java/rpg/core/combat/DamageAggregator.java`: Fenster je
      Angreifer-Ziel-Paar, zeitstempelbasiert, ohne Aufgabe (FR-038)
- [X] T090 [P] [US7] `rpg-core/src/main/java/rpg/core/combat/DamageDealtEvent.java` nach
      [contracts/events.md](./contracts/events.md)

**Checkpoint**: Kämpfe fühlen sich an, ohne dass B13 im Zahlenregen erstickt.

---

## Phase 10: User Story 8 — Spieler können einander nicht verletzen (Priority: P3)

**Ziel**: Eine Erlaubnisstelle, die B09 später ersetzt.

**Independent Test**: `./gradlew :rpg-core:test --tests "rpg.core.combat.DamagePermissionTest"`

### Tests für User Story 8

- [X] T091 [P] [US8] `rpg-core/src/test/java/rpg/core/combat/DamagePermissionTest.java`: die
      vollständige Erlaubnistabelle aus [data-model.md](./data-model.md) §6 — Spieler gegen Spieler
      und Mob gegen Mob abgewiesen, alles andere erlaubt (FR-041, FR-042a, SC-010)
- [X] T092 [P] [US8] Im selben Test: ein abgewiesener Angriff erzeugt weder Schaden noch Animation
      noch Beitrag (FR-043)
- [X] T093 [P] [US8] Im selben Test: die Explosion eines Mobs verletzt Spieler ganz normal, andere
      Mobs aber nicht (FR-042a)
- [ ] T094 [P] [US8] `rpg-core/src/test/java/rpg/core/combat/SinglePermissionPointTest.java` als
      Struktur­test: es gibt genau **eine** Stelle, die über Erlaubnis entscheidet — sonst wäre die
      spätere Zonenregel aus B09 über die ganze Pipeline verstreut (FR-042)

### Implementierung für User Story 8

- [X] T095 [US8] `rpg-core/src/main/java/rpg/core/combat/DamagePermission.java`: die eine
      Entscheidungsstelle, austauschbar über eine Schnittstelle, die B09 später füllt
- [X] T096 [US8] Einhängen in die Stufe `SOURCE`, vor jeder anderen Prüfung

**Checkpoint**: Alle acht User Stories sind fertig.

---

## Phase 11: Querschnitt — Mob-Ausstattung und Kampfzustand

**Zweck**: Zwei Anforderungsgruppen, die aus der zweiten `/clarify`-Runde stammen und quer zu
mehreren Stories liegen. Ohne die erste wirkt die gesamte Pipeline auf nichts außer Spieler.

### Tests

- [X] T097 [P] `rpg-platform/src/test/java/rpg/platform/combat/MobEquipmentListenerTest.java`: ein
      feindliches Wesen hat unmittelbar nach dem Erscheinen einen Stat-Träger mit Werten aus der
      Konfiguration; ein friedliches bekommt keinen (FR-019a, FR-019e, SC-010c)
- [X] T098 [P] Im selben Test: eine Art ohne eigenen Eintrag bekommt die Standardwerte (FR-019b)
- [X] T099 [P] Im selben Test: nach 800 erschienenen und wieder entfernten Wesen existiert **kein**
      Träger mehr — der Lecktest, ohne den der Lasttest den Fehler statt der Pipeline misst
      (FR-019d, SC-010d)
- [X] T100 [P] `rpg-core/src/test/java/rpg/core/combat/CombatStateTest.java`: ein Träger gilt
      unmittelbar nach einem Treffer als im Kampf und nach Ablauf wieder nicht; **null** geplante
      Aufgaben über den gesamten Lauf (FR-030c, FR-030d, SC-010e)
- [X] T101 [P] Im selben Test: der Zustand wird beim Geben **und** beim Nehmen von Schaden gesetzt;
      der Wechsel wird als Ereignis veröffentlicht (FR-030e)

### Implementierung

- [X] T102 [P] `rpg-core/src/main/java/rpg/core/combat/MobStatProvider.java` als Schnittstelle, die
      B10 später übernimmt (FR-019c)
- [X] T103 `rpg-platform/src/main/java/rpg/platform/combat/PaperMobStatProvider.java`: Wertesätze
      aus `combat.yml`, als `ModifierSet` mit der Quelle `(CLASS, "mob:<TYPE>")` — derselbe
      Quellenschlüssel, den B10 später ersetzt statt einen zweiten einzuführen (FR-019b)
- [X] T104 `rpg-platform/src/main/java/rpg/platform/combat/MobEquipmentListener.java`: Träger beim
      Erscheinen anlegen, beim Sterben und Entladen freigeben (FR-019a, FR-019d)
- [X] T105 [P] `rpg-core/src/main/java/rpg/core/combat/CombatState.java`: ein Zeitstempel je Träger,
      nur bei Zugriff ausgewertet (FR-030c, FR-030d, FR-030f)
- [X] T106 [P] `rpg-core/src/main/java/rpg/core/combat/CombatStateChangedEvent.java` nach
      [contracts/events.md](./contracts/events.md)
- [X] T107 Kampfzustand in der Stufe `APPLICATION` setzen, für Angreifer und Ziel

---

## Phase 12: Polish, Verdrahtungsnachweis & Lasttest

- [X] T108 [P] `rpg-core/src/main/java/rpg/core/combat/ProjectileDamage.java` und
      `rpg-platform/src/main/java/rpg/platform/combat/ProjectileCombatListener.java`: beim Abschuss
      den Rohschaden aus dem Schützen-Schnappschuss berechnen und **als einzelne Zahl** am Projektil
      hinterlegen, beim Einschlag lesen (FR-024a, FR-024b, research.md E3)
- [ ] T109 [P] `rpg-platform/src/test/java/rpg/platform/combat/ProjectileCombatTest.java`: ein
      Bogenschuss macht Schaden über denselben Rechenweg wie ein Nahkampfschlag; ein Projektil ohne
      hinterlegte Zahl wird nur neutralisiert; ein Treffer nach dem Tod des Schützen wirkt weiter
      (SC-010a)
- [X] T110 `rpg-plugin/src/test/java/rpg/plugin/FullBootstrapTest.java` erweitern: `CombatModule`
      angemeldet und nach `stats` gestartet, `CombatPipeline` als Dienst abrufbar, `combat.yml`
      angelegt, je genau ein Listener auf `EntityDamageEvent`, `EntityDeathEvent`,
      `ProjectileLaunchEvent` und `CreatureSpawnEvent`, Gameregel `keep_inventory` auf `true`;
      die Herzleiste zeigt nach jedem Schadensereignis den korrekten Anteil (SC-003, geerbt von B04s
      Spiegelung)
      (ADR-012)
- [X] T111 Abschnitt 0 des Validierungsleitfadens ausführen: alle XML-Testberichte auf
      `skipped="[1-9]` prüfen — MockBukkit meldet Nicht-Implementiertes als übersprungen
- [X] T112 `./gradlew spotlessApply build` und den vollständigen Testlauf grün stellen
- [X] T113 [P] ADR-014 in `02-decisions.md` ergänzen: die Entscheidungen aus
      [research.md](./research.md), insbesondere die erschöpfende Quellentabelle mit
      Verweigerungs-Standardfall, der wiederverwendete Schadensvorgang, die Zahl am Projektil, die
      abgeschalteten Invulnerabilitätsticks und die Mob-Überbrückung
- [X] T114 [P] Status in `minecraft-rpg-spec/minecraft-rpg-spec/blocks/B05-combat-pipeline.md` auf
      implementiert setzen; die **16 vom Steckbrief nicht genannten Schadensursachen** dort
      nachtragen, damit die Quellenliste künftig vollständig ist
- [X] T115 [P] `06-open-questions.md`: den B05-Abschnitt um die acht Klärungen aus den beiden
      `/clarify`-Runden ergänzen
- [X] T116 Abschnitte 1 bis 7 des Validierungsleitfadens vollständig durchlaufen
- [ ] T117 Abschnitt 8 auf einem echten Paper-Server durchlaufen
- [ ] T118 **Abschnitt 9 — Lasttest.** 150 Spieler gegen 800 Mobs im Dauerkampf, p95 MSPT < 40 ms.
      (SC-004, SC-005, SC-013). **Abnahmebedingung, nicht optional**: Prinzip VII nennt B05 als
      lasttestpflichtigen Block.
      Vorher muss T099 grün sein — lecken Träger, misst der Lasttest den Fehler statt der Pipeline

---

## Dependencies & Execution Order

### Phasenabhängigkeiten

- **Setup (Phase 1)**: keine Abhängigkeit
- **Foundational (Phase 2)**: nach Setup — **blockiert alle User Stories**
- **US1 (Phase 3)**: nach Foundational
- **US2 (Phase 4)**: nach US1 — die Zuordnung braucht eine Pipeline, in die sie speist
- **US3 (Phase 5)**: nach US2 — die Verdrahtung ins Plugin steht am Ende dieser Phase
- **US4 (Phase 6)**: nach US3
- **US6 (Phase 7)**: nach US3 — **vor** US5. Das Todesereignis trägt die Schadensaufteilung
  (FR-028), also muss das Beitragsfenster existieren, bevor der Tod es einpackt. Die umgekehrte
  Reihenfolge hätte in der Todesphase einen Test gegen noch nicht vorhandenen Code gestellt.
- **US5 (Phase 8)**: nach US6, aus dem genannten Grund
- **US7 (Phase 9)**: nach US3
- **US8 (Phase 10)**: nach Foundational; die Erlaubnisstelle ist unabhängig vom Rest
- **Phase 11**: nach US3 (Träger) und nach US5 (Kampfzustand beim Schadensanwenden)
- **Phase 12**: nach allen übrigen. T118 zusätzlich nach T099

### Innerhalb einer Story

Tests zuerst, dann Typen → Formel/Regel → Pipeline-Einhängung → Verdrahtung.

### Parallelisierbare Abschnitte

- T003 bis T005 und T012 bis T014 in Foundational
- Alle Tests einer Story untereinander
- Die reinen Typen: T068, T078, T087, T090, T102, T105, T106
- T113 bis T115 in der Polish-Phase

**Nicht parallel**, obwohl es so aussieht: T010, T022, T023, T024, T046, T047a, T057, T067, T079,
T096 und T107 fassen alle `DefaultCombatPipeline.java` an.

---

## Implementation Strategy

### MVP zuerst

1. Phase 1 und 2 abschließen
2. Phase 3 (US1) — **anhalten und prüfen**: die Formel ist vollständig und serverfrei geprüft
3. Phase 4 und 5 (US2, US3) — damit ist das Vanilla-Kampfsystem ersetzt und die Pipeline läuft im
   Plugin

Nach Phase 5 ist B05 aus Sicht von B08 und B10 nutzbar: Schaden, Formel und Eingriffspunkte stehen.

### Inkrementelle Auslieferung

Jede weitere Phase ist ein eigenständiger Zugewinn: US4 macht `attackSpeed` wirksam, US6 zählt
Beiträge mit, US5 lässt Kreaturen sterben und verteilt dabei gerecht, US7 macht Treffer sichtbar,
US8 schützt Spieler voreinander, Phase 11 gibt Mobs überhaupt erst Werte.

---

## Stand der Umsetzung (2026-08-20)

**107 von 120 Aufgaben erledigt. 601 Tests grün, 0 übersprungen, 0 Fehler.** Die gesamte
Implementierung steht und ist im Plugin verdrahtet (`FullBootstrapTest`, 16 Tests).

### Was offen ist, und warum

**Elf Testaufgaben ohne eigene Klasse.** Ihr Gegenstand ist geprüft, aber unter anderem Namen oder
gar nicht:

| Aufgabe | Stand |
|---|---|
| T020 `DamageEdgeCaseTest` | Inhalt in `DamageFormulaTest` (null, negativ, `NaN`, `Infinity`) |
| T028–T032 `VanillaDamageListenerTest` | **echte Lücke.** Die Zuordnung ist über `VanillaDamageMappingTest` vollständig geprüft, der Listener selbst nicht — dass er den Vanilla-Wert wirklich auf null setzt und die Animation auslöst, ist bislang nur durch Lesen belegt |
| T033 `CombatConfigSchemaTest` | **echte Lücke.** Die Invarianten sind über `CombatConfigTest` geprüft, das YAML-Schema selbst nicht |
| T053a `MirrorBeforeEventTest` | entfällt: das ist B04s Reihenfolge, dort bereits geprüft |
| T055 `VanillaWeaponCooldownTest` | Inhalt in `AttackWindowTest`; der Vanilla-Cooldown ist ohnehin wirkungslos, weil der Vanilla-Schaden null ist |
| T085 `PaperDamageFeedbackTest` | **echte Lücke.** Trefferanimation und Rückstoß sind nicht geprüft |
| T094 `SinglePermissionPointTest` | Inhalt in `DamagePermissionTest`, einschließlich Austauschbarkeit der Regel |
| T109 `ProjectileCombatTest` | **echte Lücke.** Der Bogenpfad ist implementiert, aber nicht geprüft |

Die vier echten Lücken betreffen ausschließlich die Paper-Anbindung; jede Regel in `rpg-core` ist
geprüft. Sie gehören vor Abschnitt 8 des Validierungsleitfadens nachgeholt.

**T117, T118 — echter Server und Lasttest.** T118 ist Abnahmebedingung: Prinzip VII nennt B05
namentlich. T099 (Lecktest, 800 Wesen) ist grün, die Vorbedingung dafür steht also.

---

## Notes

- Tests vor der Implementierung schreiben und fehlschlagen sehen
- Nach jedem Testlauf Abschnitt 0 des Validierungsleitfadens ausführen
- Zeitbasierte Tests laufen mit gesteuerter Uhr, nie mit echten Wartezeiten
- Der Block gilt erst als fertig, wenn T110 **und T118** grün sind: verdrahtet *und* lastgetestet
