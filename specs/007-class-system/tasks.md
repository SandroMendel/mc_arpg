---

description: "Aufgabenliste für B07 · Klassen-System"
---

# Tasks: B07 · Klassen-System

**Input**: Entwurfsdokumente aus `/specs/007-class-system/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Enthalten und verpflichtend. Constitution VII verlangt serverfreie Unit-Tests für jede
Formel und jede Regel der Domänenschicht und **echtes PostgreSQL statt Mocks** für die Persistenz.
B07 ist **nicht** lasttestpflichtig — Prinzip VII nennt B05 und B10 beim Namen, nicht B07. Die
Performancezusagen SC-009 und SC-010 sind trotzdem als gewöhnliche Tests formuliert.

**Organisation**: Nach User Stories gruppiert, in Prioritätsreihenfolge.

## Format: `[ID] [P?] [Story] Beschreibung`

- **[P]**: parallelisierbar (andere Datei, keine offene Abhängigkeit)
- **[Story]**: zugehörige User Story aus [spec.md](./spec.md)

## Pfadkonventionen

- `rpg-core/src/main/java/rpg/core/classes/` — Regeln, bukkitfrei. Das Paket heißt `classes`, weil
  `class` ein Java-Schlüsselwort ist; der Plural folgt dem bestehenden `stats` und `combat`
- `rpg-persistence/src/main/java/rpg/persistence/classes/` — Repository
- `rpg-persistence/src/main/resources/db/migration/` — Migration `V7_1`
- `rpg-platform/src/main/java/rpg/platform/classes/` — Listener, GUI, Itemaufbau
- `rpg-plugin/src/main/resources/` — `classes.yml` und `messages.yml`

## Was B07 ausdrücklich nicht baut

Workflow-Regel 5. Diese Punkte gehören zu späteren Blöcken und dürfen in keiner Aufgabe auftauchen:
Fähigkeitsverhalten und Hotbar-Schema (B08), Items, Beute und Aufstiegskosten (B11), NPC-Händler
(offen, siehe ADR-018), HUD-Darstellung (B13). Der `cost`-Block wird durchgereicht, nie ausgelegt.

---

## Phase 1: Setup

**Zweck**: Pakete, Konfigurationsgerüst und Nachrichtenschlüssel

- [X] T001 [P] `rpg-core/src/main/java/rpg/core/classes/package-info.java`: Blockgrenze und
      Zuständigkeit von B07 dokumentieren, einschließlich der Abgrenzung zu B08 und B11
- [X] T002 [P] `rpg-persistence/src/main/java/rpg/persistence/classes/package-info.java` anlegen
- [X] T003 [P] `rpg-platform/src/main/java/rpg/platform/classes/package-info.java` anlegen
- [X] T004 [P] `rpg-core/src/main/java/rpg/core/classes/ClassMessageKeys.java`: alle Spielertexte
      als Schlüssel — Anzeigenamen der drei Klassen, Ablehnungsgründe der Auswahl, Ablehnungsgründe
      des Stufenaufstiegs, Warnung bei vollem Inventar (Prinzip V, keine hartcodierten Texte)
- [X] T005 `rpg-plugin/src/main/resources/classes.yml`: Gerüst mit den drei Klassenblöcken nach
      [contracts/class-config.md](./contracts/class-config.md), zunächst nur Warrior vollständig
- [X] T006 [P] `rpg-plugin/src/main/resources/messages.yml`: Schlüssel aus `ClassMessageKeys`
      ergänzen, einschließlich `class.warrior.name` = „Berserker" (ADR-019)

---

## Phase 2: Foundational (blockierende Voraussetzung)

**Zweck**: Typen, Konfigurationsbindung, Persistenz. Ohne diese Phase kann keine User Story beginnen.

**⚠️ Kritisch**: `CharacterClass` und die Prüfbedingung `chk_character_class` aus
`V3_1__player_characters.sql` bleiben **unangetastet** (ADR-019). Keine Aufgabe dieser Phase fasst sie
an.

### Werttypen

- [X] T007 [P] `rpg-core/src/main/java/rpg/core/classes/LadderSlot.java`: Enum `ARMOR`, `WEAPON`
- [X] T008 [P] `rpg-core/src/main/java/rpg/core/classes/TierAppearance.java`: Record mit `material`,
      optionaler `color`, optionalem `trimMaterial`/`trimPattern`, optionalem `modelData`. Gleichheit
      vergleicht das **Tripel** Material/Farbe/Trim (FR-016)
- [X] T009 [P] `rpg-core/src/main/java/rpg/core/classes/EquipmentTier.java`: Record mit `index`,
      `values` über die vier Attribute des Slots, `appearance`, `requiredLevel` und dem
      **undurchsichtigen** `cost` als `Map<String,Object>` (FR-021)
- [X] T010 [P] `rpg-core/src/main/java/rpg/core/classes/ClassBaseStats.java`: acht Werte über
      `Attribute` aus B04, alle Pflicht
- [X] T011 [P] `rpg-core/src/main/java/rpg/core/classes/ClassGrowth.java`: acht Zuwachsraten je
      Level. Getrennter Typ von `ClassBaseStats`, weil „Wert" und „Wert je Level" verschiedene Dinge
      sind — ein gemeinsamer Typ hätte die Verwechslung erlaubt (data-model.md)
- [X] T012 [P] `rpg-core/src/main/java/rpg/core/classes/AbilityBinding.java`: Record mit
      `abilityId`, `kind`, `unique`, `unlockLevel`. B07 löst die ID **nicht** auf (FR-044)
- [X] T013 `rpg-core/src/main/java/rpg/core/classes/EquipmentLadder.java`: Liste von Stufen mit
      **variabler Länge** (FR-013), Zugriff auf Stufe `n`, Endstufe und Länge (hängt an T009)
- [X] T014 `rpg-core/src/main/java/rpg/core/classes/CharacterClassDefinition.java`: unveränderliche
      Definition mit `displayNameKey`, `menuMaterial`, Basiswerten, Wachstum, beiden Leitern und
      Fähigkeitsbindung (hängt an T008–T013)

### Tests der Werttypen

- [X] T015 [P] `rpg-core/src/test/java/rpg/core/classes/EquipmentLadderTest.java`: eine Leiter mit
      fünf, sechs und sieben Stufen erreicht denselben Endwert; jede ist streng steigend; eine Leiter
      mit einer Stufe wird abgewiesen (SC-014, FR-013)
- [X] T016 [P] `rpg-core/src/test/java/rpg/core/classes/TierAppearanceTest.java`: zwei Stufen mit
      gleichem Material aber verschiedener Farbe gelten als unterschiedlich; gleiches Material,
      gleiche Farbe **und** gleicher Trim gelten als gleich (FR-016)

### Konfiguration

- [X] T017 `rpg-core/src/main/java/rpg/core/classes/ClassConfig.java`: gebundene Konfiguration, hält
      genau drei `CharacterClassDefinition` — **nicht je Spieler** (data-model.md, Speicherbedarf)
- [X] T018 `rpg-core/src/main/java/rpg/core/classes/ClassConfigSchema.java`: Felddeklaration nach
      [contracts/class-config.md](./contracts/class-config.md), Muster wie
      `ProgressionConfigSchema` aus B06. Leitern als **Listenfeld**, nicht als Feld je Stufe
- [X] T019 `rpg-core/src/main/java/rpg/core/classes/ClassConfigSchema.java`: Bindefunktion für die
      Zusagen V1 bis V18; bricht beim **ersten** Verstoß ab und nennt Klasse, Leiter und Stufe
      (Prinzip V, Fail-Fast)
- [X] T020 `rpg-core/src/main/java/rpg/core/classes/ClassRegistry.java`: Auflösung Klasse →
      Definition, `abilitiesOf`, `unlockedFor` nach [contracts/class-api.md](./contracts/class-api.md)

### Tests der Konfigurationszusagen — je Zusage eine Aufgabe

Jede prüft, dass der Ladevorgang **abbricht** und die Ursache **benennt**. Zusagenummer und Datei
stehen je Aufgabe.

- [X] T021 [P] `rpg-core/src/test/java/rpg/core/classes/ClassConfigValidationTest.java` V1: eine vierte Klassen-ID bricht ab und nennt die ID; eine fehlende der drei
      bekannten bricht ab und nennt die Klasse (FR-005, SC-008)
- [X] T022 [P] `rpg-core/src/test/java/rpg/core/classes/ClassConfigValidationTest.java` V2: ein fehlender Basiswert oder eine fehlende Zuwachsrate bricht ab und nennt das
      Attribut; alle acht sind Pflicht, auch die mit Null (FR-001, FR-002)
- [X] T023 [P] `rpg-core/src/test/java/rpg/core/classes/ClassConfigValidationTest.java` V3: eine Leiter mit weniger als zwei Stufen bricht ab (FR-013)
- [X] T024 [P] `rpg-core/src/test/java/rpg/core/classes/ClassConfigValidationTest.java` V4: ein fehlendes Attribut einer Stufe bricht ab; Rüstung fordert vier, Waffe fordert
      vier (FR-015)
- [X] T025 [P] `rpg-core/src/test/java/rpg/core/classes/ClassConfigValidationTest.java` V5: eine nicht streng steigende Leiter bricht ab und nennt die Stufe (FR-017)
- [X] T026 [P] `rpg-core/src/test/java/rpg/core/classes/ClassConfigValidationTest.java` V6: nicht steigende Levelanforderungen, erste ungleich 1 oder letzte über
      Maximallevel brechen ab (FR-018)
- [X] T027 [P] `rpg-core/src/test/java/rpg/core/classes/ClassConfigValidationTest.java` V7: zwei Stufen mit identischem Material, identischer Farbe und identischem Trim
      brechen ab (FR-016, SC-013)
- [X] T028 [P] `rpg-core/src/test/java/rpg/core/classes/ClassConfigValidationTest.java` V8: eine Stufe mit dem Material der Vorstufe ohne Farbe und ohne Trim bricht ab —
      betrifft Mage-Leder und Rogue-Kettenhemd (FR-016a)
- [X] T029 [P] `rpg-core/src/test/java/rpg/core/classes/ClassConfigValidationTest.java` V9: `color` auf `CHAINMAIL` oder `GOLDEN` bricht ab; auf `LEATHER` ist es erlaubt
      (FR-016b)
- [X] T030 [P] `rpg-core/src/test/java/rpg/core/classes/ClassConfigValidationTest.java` V10: `trim-material` ohne `trim-pattern` oder umgekehrt bricht ab (FR-016)
- [X] T031 [P] `rpg-core/src/test/java/rpg/core/classes/ClassConfigValidationTest.java` V11: derselbe Rüstungssatz in zwei Klassenleitern bricht ab; das gemeinsame
      Einstiegsmaterial `LEATHER` ist ausgenommen; für **Waffen** gilt die Trennung ausdrücklich
      nicht (FR-016c, SC-012)
- [X] T032 [P] **verschoben nach rpg-platform** — `rpg-platform/src/test/java/rpg/platform/classes/MaterialExistenceTest.java` V12: ein Material, das in der laufenden Server-Version nicht existiert, bricht ab. Gehoert nicht in `rpg-core`: die Pruefung braucht Bukkit, und Prinzip III.1 verbietet dort jede Bukkit-Referenz. Der Kern verlangt nur einen nicht leeren Namen
- [X] T033 [P] `rpg-core/src/test/java/rpg/core/classes/ClassConfigValidationTest.java` V13: eine Angriffsgeschwindigkeit auf der Endstufe außerhalb des Modifier-Bands aus
      ADR-008 bricht ab (FR-008)
- [X] T034 [P] `rpg-core/src/test/java/rpg/core/classes/ClassConfigValidationTest.java` V14: ein Endwert über dem Cap aus ADR-008 bricht ab (FR-008)
- [X] T035 [P] `rpg-core/src/test/java/rpg/core/classes/ClassConfigValidationTest.java` V15: fünf oder sieben Fähigkeiten brechen ab; eine **leere** Liste wird angenommen;
      eine teilweise gefüllte bricht ab (FR-041, FR-045)
- [X] T036 [P] `rpg-core/src/test/java/rpg/core/classes/ClassConfigValidationTest.java` V16: mehr als eine `unique`, oder eine `unique` mit `kind: PASSIVE`, bricht ab
      (FR-041)
- [X] T037 [P] `rpg-core/src/test/java/rpg/core/classes/ClassConfigValidationTest.java` V17: `unlock-level` unter 1 oder über Maximallevel bricht ab (FR-042)
- [X] T038 [P] `rpg-core/src/test/java/rpg/core/classes/ClassConfigValidationTest.java` V18: ein `cost`-Block mit unbekanntem Inhalt bricht **nicht** ab — der Gegentest zu
      allen anderen. B07 legt ihn nicht aus (FR-021)
- [X] T039 [P] `rpg-core/src/test/java/rpg/core/classes/ClassConfigOrderTest.java`: die
      Konfiguration wird vollständig geprüft, **bevor** der erste Charakter geladen wird (FR-007)

### Persistenz — die drei Registrierungen nach ADR-015

Ein neuer Aggregattyp braucht drei Eintragungen, nicht eine. In B06 hat eine fehlende sich als
Datenbankfehler getarnt. Deshalb je Registrierung eine eigene Aufgabe.

- [X] T040 `rpg-core/src/main/java/rpg/core/classes/ClassProgress.java`: Aggregat mit
      `characterId`, `armorTier`, `weaponTier`. Die Klasse selbst steht in `rpg.character` aus B03
      und wird **nicht** wiederholt
- [X] T041 `rpg-core/src/main/java/rpg/core/classes/ClassProgressRepository.java`: Vertrag, Umsetzung
      im Persistenzmodul
- [X] T042 `rpg-persistence/src/main/resources/db/migration/V7_1__character_class_progress.sql`:
      Tabelle mit Fremdschlüssel auf `rpg.character` (`ON DELETE CASCADE`), `armor_tier` und
      `weapon_tier` als `INTEGER NOT NULL DEFAULT 1`, `revision`, Prüfbedingungen `>= 1`. **Keine**
      obere Prüfbedingung — die Leiterlänge ist Konfiguration (data-model.md)
- [X] T043 **Registrierung 1 von 3** — `AggregateType`: `CHARACTER_CLASS_PROGRESS` ergänzen
- [X] T044 **Registrierung 2 von 3** — `FlushCycle.WRITE_ORDER`: `CHARACTER_CLASS_PROGRESS`
      **nach** `CHARACTER` einsortieren, weil der Fremdschlüssel darauf zeigt (ADR-015)
- [X] T045 **Registrierung 3 von 3** —
      `rpg-persistence/src/main/java/rpg/persistence/classes/JdbcClassProgressRepository.java`
      schreiben und im Persistenzmodul verdrahten
- [X] T046 `rpg-persistence/src/test/java/rpg/persistence/NoDatabaseAccessPerGameEventTest.java`:
      bestehenden Invariantentest laufen lassen — er prüft, dass jeder `AggregateType` in
      `WRITE_ORDER` steht und jedes Kind nach seinem Elternteil kommt. Er würde ein Vergessen von
      T043 oder T044 sofort zeigen (ADR-016)

**Checkpoint**: Typen, Konfiguration und Persistenz stehen. Die User Stories können beginnen.

---

## Phase 3: User Story 1 — Ein neuer Spieler wählt seine Klasse (Priority: P1) 🎯 MVP

**Ziel**: Nach dem Beitritt öffnet sich eine nicht schließbare Auswahl; nach der Wahl existiert ein
Charakter, und der Spieler steht im Spiel.

**Unabhängig prüfbar**: Beitritt ohne bestehenden Charakter — die Auswahl erscheint, jeder Versuch sie
zu verlassen führt zurück, nach der Wahl existiert ein Charakter der gewählten Klasse.

### Tests für User Story 1

- [X] T047 [P] [US1] `rpg-core/src/test/java/rpg/core/classes/ClassSelectionTest.java`:
      `needsSelection` ist wahr ohne Charakter und falsch mit Charakter (US1.1, US1.6)
- [X] T048 [P] [US1] `rpg-core/src/test/java/rpg/core/classes/ClassSelectionTest.java`: `available`
      liefert nur Klassen ohne bestehenden Charakter des Kontos (FR-035, Edge Case „drei Charaktere")
- [X] T049 [P] [US1] `rpg-core/src/test/java/rpg/core/classes/ClassSelectionConcurrencyTest.java`:
      bei zwei gleichzeitigen Wahlen derselben Klasse gewinnt genau einer, der andere erhält
      `CLASS_ALREADY_TAKEN` — keine Ausnahme (FR-036)
- [X] T050 [P] [US1] `rpg-core/src/test/java/rpg/core/classes/ClassSelectionTest.java`: ein
      Verbindungsabbruch während der Auswahl lässt **keinen** halb angelegten Charakter zurück
      (FR-037)
- [X] T051 [P] [US1] `rpg-platform/src/test/java/rpg/platform/classes/ClassSelectionMenuTest.java`:
      die Auswahl zeigt genau drei Klassen mit Vanilla-Materialien und Anzeigenamen aus der
      Konfiguration (US1.1, FR-040)
- [X] T052 [P] [US1]
      `rpg-platform/src/test/java/rpg/platform/classes/ClassSelectionReopenTest.java`: ein
      Schließversuch führt zurück in die Auswahl — je Route eine Prüfung: Escape/Close, Inventarwechsel,
      Befehl, Weltwechsel (US1.2, FR-033)
- [X] T053 [P] [US1] `rpg-platform/src/test/java/rpg/platform/classes/NoCharacterGuardTest.java`: ein
      Spieler ohne Charakter bewegt sich nicht (US1.5, FR-034)
- [X] T054 [P] [US1] `rpg-platform/src/test/java/rpg/platform/classes/NoCharacterGuardTest.java`: ein
      Spieler ohne Charakter nimmt keinen Schaden (US1.4, FR-034)
- [X] T055 [US1] **Prüfaufgabe aus plan.md — Ergebnis: Ablehnung fällt an `NO_HOLDER`, nicht am Sitzungszustand; keine Änderung an B05 nötig. Festgehalten in `NoCharacterNoCombatTest`.** Feststellen, ob `DefaultCombatPipeline` die Ablehnung
      am fehlenden **aktiven Charakter** festmacht oder nur am Sitzungszustand `READY`. Falls nur am
      Zustand: eine Bedingung ergänzen, **kein** Verhalten von B05 ändern (research.md R4)

### Implementierung für User Story 1

- [X] T056 [US1] `rpg-core/src/main/java/rpg/core/classes/ClassSelectionResult.java`: Annahme oder
      benannte Ablehnung (`CLASS_ALREADY_TAKEN`, `UNKNOWN_CLASS`, `ALREADY_HAS_CHARACTER`)
- [X] T057 [US1] `rpg-core/src/main/java/rpg/core/classes/ClassSelection.java`: `needsSelection`,
      `available`, `choose` nach [contracts/class-api.md](./contracts/class-api.md). `choose` ist der
      **einzige** Weg, einen Charakter anzulegen
- [X] T058 [US1] `rpg-core/src/main/java/rpg/core/classes/ClassChangedEvent.java`: über den
      Ereignisbus aus B01, **nicht** als Bukkit-Ereignis (Prinzip III)
- [X] T059 [US1] `rpg-platform/src/main/java/rpg/platform/classes/ClassSelectionMenu.java`: GUI aus
      Vanilla-Materialien (ADR-005), ein Feld je verfügbarer Klasse mit Rollenprofil und Startwerten
- [X] T060 [US1] `rpg-platform/src/main/java/rpg/platform/classes/ClassSelectionListener.java`:
      Öffnen beim Beitritt ohne Charakter, Klickauswertung, und Wiederöffnen beim Schließversuch
      **einen Tick später** über den entity-gebundenen Scheduler — nie über den globalen
      (research.md R4, Prinzip I)
- [X] T061 [US1] `rpg-platform/src/main/java/rpg/platform/classes/NoCharacterGuardListener.java`:
      `PlayerMoveEvent` abbrechen, solange kein aktiver Charakter existiert. Die Prüfung ist ein
      Test auf `activeCharacter().isEmpty()` und trifft im Normalbetrieb auf niemanden

**Checkpoint**: US1 vollständig — ein neuer Spieler kann eine Klasse wählen und bekommt einen
Charakter. Das ist der MVP.

---

## Phase 4: User Story 2 — Die Klasse bestimmt die Werte (Priority: P1)

**Ziel**: Die acht Attribute unterscheiden sich nach Klasse und wachsen mit dem Level nach der Kurve
der Klasse.

**Unabhängig prüfbar**: Drei Charaktere je Klasse auf Level 1 haben die dokumentierten Basiswerte;
nach einem Aufstieg den dokumentierten Zuwachs — ohne Ausrüstung und ohne Fähigkeiten.

### Tests für User Story 2

- [X] T062 [P] [US2] `rpg-core/src/test/java/rpg/core/classes/ClassStatContributorTest.java`: der
      Beitrag erscheint als **Basiswert**, nicht als Modifikator, und das Modifikatorband liegt um
      den effektiven Basiswert einschließlich Stufenwerten. Prüfbar am Warrior auf Level 60 Endstufe:
      Band um ~2012 Health, nicht um 40 (FR-009, research.md R1)
- [X] T063 [P] [US2] `rpg-core/src/test/java/rpg/core/classes/ClassStatContributorTest.java`:
      `SourceKind.CLASS` bleibt **leer** — B07 belegt die Modifikatorquelle nicht (FR-010a). Dieselbe
      Zusage, die B06 für `SourceKind.LEVEL` hält (ADR-015)
- [X] T064 [P] [US2] `rpg-core/src/test/java/rpg/core/classes/ClassStatContributorTest.java`: ein
      Halter **ohne** Charakter liefert keinen Beitrag und **keine Ausnahme** — B04 rechnet Mobs
      durch denselben Pfad (Muster von `LevelStatContributor`)
- [X] T065 [P] [US2] `rpg-core/src/test/java/rpg/core/classes/ClassGrowthTest.java`: das
      klassenneutrale Levelwachstum aus B06 wird **ersetzt**, nicht ergänzt — die Summe verdoppelt
      sich nicht (FR-003, US2.2)
- [X] T066 [P] [US2] **verschoben nach `rpg-plugin/src/test/java/rpg/plugin/ShippedClassConfigTest.java`** — die ausgelieferte Datei und der YAML-Parser liegen beide ausserhalb von `rpg-core`, das keine einzige Abhaengigkeit hat. Praezedenzfall: `ShippedProgressionConfigTest`. Urspruenglich `rpg-core/.../ClassValueBudgetTest.java`: für alle
      drei Klassen und alle acht Attribute liegt der Wert auf Level 1 Stufe 1 und auf Level 60
      Endstufe innerhalb der Wertebereiche aus ADR-008, Abweichung unter 3 % (SC-004). Der Test
      rechnet aus der **geladenen Konfiguration**, nicht gegen hinterlegte Erwartungswerte — sonst
      prüft er die Tabelle in der Spec statt die Konfiguration
- [X] T067 [P] [US2] **ebenfalls in `ShippedClassConfigTest`** (siehe T066): für die
      fünf Attribute mit Levelwachstum liegt der Leiteranteil zwischen 60 % und 80 %; die drei
      prozentualen sind ausgenommen, weil sie vollständig aus der Leiter kommen (SC-005)
- [X] T068 [P] [US2] `rpg-core/src/test/java/rpg/core/classes/ClassRoleProfileTest.java`: bei
      gleichem Level und gleicher Stufe hat der Warrior das höchste Leben, der Rogue die höchste
      Angriffsgeschwindigkeit, der Mage das höchste Mana (US2.3)
- [X] T069 [P] [US2] **in `ClassRecalculationTest.java`** (statt einer eigenen `ClassCapTest.java` — Cap und Neuberechnung brauchen dieselbe Fixture mit echter Engine): überschreiten die
      Klassenwerte einen Cap, greift der Cap aus B04, und der Startvorgang schlägt **nicht** fehl
      (US2.6)
- [X] T070 [P] [US2] `rpg-core/src/test/java/rpg/core/classes/ClassRecalculationTest.java`: ein
      Levelaufstieg löst **genau eine** Neuberechnung aus, gemessen über alle acht Attribute
      (FR-011, SC-009)
- [X] T071 [P] [US2] `rpg-core/src/test/java/rpg/core/classes/ClassNoDatabaseTest.java`: 10 000
      Abfragen der Klassenwerte lösen **keinen** Datenbankzugriff aus (FR-012, SC-010)

### Implementierung für User Story 2

- [X] T072 [US2] `rpg-core/src/main/java/rpg/core/classes/ClassStatContributor.java`:
      `BaseStatContributor` mit `id() == "class"`. Beisteuert in **einem** Durchgang: Klassenbasis,
      Levelwachstum der Klasse, Werte der erreichten Rüstungsstufe, Werte der erreichten Waffenstufe
- [X] T073 [US2] `ClassStatContributor` an der bestehenden `StatEngine` registrieren. **B04 wird
      nicht geändert** — B07 nutzt die vorhandene Schnittstelle. Erledigt in `ClassesModule.start`
- [X] T074 [US2] **Befund: es genügt, B06s Contributor nicht zu registrieren.** `rpg.character.character_class` ist `NOT NULL`, also hat *jeder* Charakter eine Klasse — B06s klassenneutraler `LevelStatContributor` hat kein Subjekt mehr, sobald B07 aktiv ist. Registriert wird in `ProgressionModule` (Zeile ~132); die Verdrahtung entscheidet, welcher der beiden läuft. Das klassenneutrale `LevelStatContributor` aus B06 für Charaktere mit Klasse
      abschalten oder überschreiben, sodass es sich nicht zum Klassenwachstum addiert (FR-003).
      B06 hat diese Ersetzbarkeit in seinem FR-022 vorgesehen.
      **Umgesetzt, aber nicht wie geplant.** „Nicht registrieren" genügt gerade nicht: `ProgressionModule`
      registriert seinen Contributor selbst und beide Module starten, also liefen beide nebeneinander und
      das Levelwachstum wurde doppelt angewandt. `StatEngine` hat dafür jetzt
      `unregisterBaseStatContributor(id)`; `ClassesModule.start` entfernt `progression-level` und bricht
      den Start ab, wenn nichts zu entfernen war. `FullBootstrapTest.theClassReplacesTheLevelGrowthRatherThanAddingToIt`
      prüft das Ergebnis (gegengeprüft: ohne die Entfernung schlägt er fehl)

**Checkpoint**: US1 und US2 funktionieren unabhängig. Ein Charakter hat klassenspezifische Werte.

---

## Phase 5: User Story 3 — Die Ausrüstung steigt entlang der Leiter auf (Priority: P2)

**Ziel**: Fünf bis sieben Stufen je Leiter, sichtbar an Material, Farbe oder Trim; die erreichte Stufe
übersteht Relogin und Neustart.

**Unabhängig prüfbar**: Eine Stufe wird über die Schnittstelle weitergeschaltet, die Werte steigen um
den dokumentierten Betrag, das Aussehen wechselt, und die Stufe übersteht einen Neustart — ohne Beute
und ohne Kosten.

### Tests für User Story 3

- [X] T075 [P] [US3] `rpg-core/src/test/java/rpg/core/classes/TierAdvanceTest.java`: ein frisch
      angelegter Charakter steht auf Rüstungs- und Waffenstufe 1 (US3.1)
- [X] T076 [P] [US3] `rpg-core/src/test/java/rpg/core/classes/TierAdvanceTest.java`: Weiterschalten
      erhöht die Stufe um eins und die Werte um den Unterschied (US3.2)
- [X] T077 [P] [US3] `rpg-core/src/test/java/rpg/core/classes/TierAdvanceTest.java`: Weiterschalten
      über die Endstufe hinaus wird mit `ALREADY_AT_TOP` abgewiesen (US3.3, FR-020)
- [X] T078 [P] [US3] `rpg-core/src/test/java/rpg/core/classes/TierAdvanceTest.java`: unterhalb der
      Levelanforderung wird mit `BELOW_REQUIRED_LEVEL` abgewiesen und begründet (US3.4, FR-018)
- [X] T079 [P] [US3] `rpg-core/src/test/java/rpg/core/classes/TierAdvanceTest.java`: die beiden
      Leitern sind unabhängig — das Weiterschalten der einen lässt die andere unverändert (US3.6,
      FR-019)
- [X] T080 [P] [US3] `rpg-core/src/test/java/rpg/core/classes/TierAdvanceTest.java`: ein
      Stufenaufstieg löst **genau eine** Neuberechnung aus (US3.7, SC-009)
- [X] T081 [P] [US3] `rpg-core/src/test/java/rpg/core/classes/TierAdvanceTest.java`: eine
      nachträglich **erhöhte** Levelanforderung senkt die Stufe eines Charakters nicht, der sie schon
      trägt — sie gilt nur beim Weiterschalten (Edge Case)
- [X] T082 [P] [US3] `rpg-core/src/test/java/rpg/core/classes/TierCostPassthroughTest.java`:
      `costOf` gibt den Block **unausgelegt** zurück; B07 kennt keine Coins (FR-021)
- [X] T083 [P] [US3] `rpg-core/src/test/java/rpg/core/classes/BoundEquipmentSpecTest.java`: der
      Sollzustand der Ausrüstung folgt eindeutig aus `(Klasse, armorTier, weaponTier)`; die Richtung
      ist einseitig — die Stufe erzeugt das Item, nie umgekehrt (FR-023)
- [X] T084 [P] [US3]
      `rpg-platform/src/test/java/rpg/platform/classes/BoundItemFactoryTest.java`: das gebaute
      Rüstungsteil trägt Material, Farbe und Trim der Stufe. Je Klasse eine Prüfung: Warrior wechselt
      das Material, Mage wechselt die **Farbe** auf gleichem Leder, Rogue trägt ab Stufe 4 einen
      **Trim** auf gleichem Kettenhemd (FR-016a, research.md R3)
- [X] T085 [P] [US3]
      `rpg-platform/src/test/java/rpg/platform/classes/ClassEquipmentApplierTest.java`: ein fehlendes
      gebundenes Item wird beim Laden wiederhergestellt — der Zustand „Stufe erreicht, Item fehlt"
      heilt sich selbst (FR-023, Edge Case)

### Implementierung für User Story 3

- [X] T086 [US3] `rpg-core/src/main/java/rpg/core/classes/TierAdvanceRejection.java`:
      `BELOW_REQUIRED_LEVEL`, `ALREADY_AT_TOP`, `UNKNOWN_CHARACTER`
- [X] T087 [US3] `rpg-core/src/main/java/rpg/core/classes/TierAdvance.java`: `advanceArmor`,
      `advanceWeapon`, `costOf` nach [contracts/class-api.md](./contracts/class-api.md). Prüft Level
      und Endstufe, prüft **keine** Kosten — der Aufrufer hat sie eingezogen
- [X] T088 [US3] `rpg-core/src/main/java/rpg/core/classes/TierAdvancedEvent.java`: über den
      Ereignisbus aus B01
- [X] T089 [US3] `rpg-core/src/main/java/rpg/core/classes/BoundEquipment.java`: Sollzustand aus
      Klasse und Stufen ableiten
- [X] T090 [US3] `rpg-platform/src/main/java/rpg/platform/classes/BoundItemFactory.java`: baut aus
      einer Stufe einen Gegenstand — Material, Färbung über `LeatherArmorMeta`, Trim über `ArmorMeta`
      mit `ArmorTrim` (research.md R3)
- [X] T091 [US3] `rpg-platform/src/main/java/rpg/platform/classes/ClassEquipmentApplier.java`: setzt
      den Sollzustand beim Laden der Sitzung und nach jedem Stufenaufstieg

**Checkpoint**: Die Ausrüstungsleitern funktionieren und sind persistent.

---

## Phase 6: User Story 4 — Gebundene Ausrüstung lässt sich nicht verlieren (Priority: P2)

**Ziel**: Gebundene Items sind unbeweglich, die Wurf-Aktion ist für alle Items ab, ungebundene Items
bleiben frei beweglich, Mob-Loot bleibt unberührt.

**Unabhängig prüfbar**: Zwei Items im Inventar — ein gebundenes und ein ungebundenes. Jede
Bewegungsroute wird für das gebundene abgewiesen und für das ungebundene erlaubt.

### Tests für User Story 4 — je Route eine Aufgabe

> **Befund bei der Umsetzung:** ein `InventoryClickEvent` laesst sich gegen MockBukkit nicht
> konstruieren — `SimpleInventoryViewMock.convertSlot` ist nicht implementiert, und ein Test, der es
> versucht, wird **still uebersprungen** statt zu scheitern. Acht Tests standen zunaechst genau so da:
> gruen in der Zusammenfassung, ohne eine Zusage zu pruefen. Die Routen pruefen deshalb die
> **Entscheidung** (`refusesClick`, `refusesSwap`, `refusesDrag`), und ein eigener Testblock belegt,
> dass es fuer jedes der vier Ereignisse genau einen Handler gibt. Zusammen strenger als eine
> Ereignissimulation.

Eine vergessene Route ist ein Loch in einer Regel, die als absolut gilt. Deshalb je Route eine eigene
Aufgabe statt einer Sammelaufgabe.

- [X] T092 [P] [US4] `rpg-platform/src/test/java/rpg/platform/classes/EquipmentLockTest.java` Route 1: Klick auf einen Rüstungsslot wird abgewiesen (US4.1, FR-026)
- [X] T093 [P] [US4] `rpg-platform/src/test/java/rpg/platform/classes/EquipmentLockTest.java` Route 2: Slot-Tausch eines gebundenen Items wird abgewiesen (US4.2)
- [X] T094 [P] [US4] `rpg-platform/src/test/java/rpg/platform/classes/EquipmentLockTest.java` Route 3: Shift-Klick auf ein gebundenes Item wird abgewiesen (US4.2)
- [X] T095 [P] [US4] `rpg-platform/src/test/java/rpg/platform/classes/EquipmentLockTest.java` Route 4: Hotbar-Tausch mit einem gebundenen Item wird abgewiesen (US4.2)
- [X] T096 [P] [US4] `rpg-platform/src/test/java/rpg/platform/classes/EquipmentLockTest.java` Route 5: Offhand-Tausch eines gebundenen Items wird abgewiesen (US4.2)
- [X] T097 [P] [US4] `rpg-platform/src/test/java/rpg/platform/classes/EquipmentLockTest.java` Route 6: die Wurf-Aktion wird für ein gebundenes Item abgewiesen (US4.4)
- [X] T098 [P] [US4] `rpg-platform/src/test/java/rpg/platform/classes/EquipmentLockTest.java` Route 6b: die Wurf-Aktion wird auch für ein **ungebundenes** Item abgewiesen —
      sie ist für alle Items ab (FR-027)
- [X] T099 [P] [US4] `rpg-platform/src/test/java/rpg/platform/classes/EquipmentLockTest.java` **Gegentest**: ein ungebundenes Item lässt sich innerhalb des Inventars frei
      verschieben — alle Routen 1 bis 5 gelingen (US4.3, FR-028). Ohne diesen Test wäre nicht
      belegt, dass die Sperre nicht einfach alles sperrt
- [X] T100 [P] [US4] `rpg-platform/src/test/java/rpg/platform/classes/EquipmentLockTest.java` **Mob-Loot unberührt**: ein sterbender Mob lässt Items fallen wie vorgesehen;
      die Sperre ist rein spielerseitig (US4.5, FR-029)
- [X] T101 [P] [US4] `rpg-core/src/test/java/rpg/core/classes/BoundEquipmentTest.java`: das Prädikat
      antwortet ja für Klassenrüstung, ja für Klassenwaffe, nein für ein beliebiges anderes Item
      (US4.7, FR-025)
- [X] T102 [P] [US4] `rpg-core/src/test/java/rpg/core/classes/BoundEquipmentTest.java`: ein Item, das
      für einen **anderen** Charakter gebunden ist, gilt für diesen als nicht gebunden — Kopien sind
      wertlos (research.md R6, Prinzip VI)
- [X] T103 [P] [US4] `rpg-core/src/test/java/rpg/core/classes/BoundEquipmentTest.java`: 10 000
      Abfragen des Prädikats ohne Datenbankzugriff und ohne vermeidbare Objekterzeugung — es liegt im
      Pfad jedes Inventarklicks (SC-010, Prinzip II)
- [X] T104 [P] [US4] `rpg-platform/src/test/java/rpg/platform/classes/EquipmentLockTest.java`: eine
      Ausnahme im Listener bricht den Klick ab, nicht die Sitzung (FR-031, Prinzip VI)

### Implementierung für User Story 4

- [X] T105 [US4] `rpg-platform/src/main/java/rpg/platform/classes/BoundItemTag.java`: Schlüssel im
      `PersistentDataContainer` mit Klassen-ID, Slot und **Charakter-ID**. Kein Lore-Parsing
      (research.md R6, ADR-004)
- [X] T106 [US4] `BoundEquipment` in `rpg-core` um `isBound` erweitern; der Kern kennt den Schlüssel
      als **Zeichenkette**, nicht als Bukkit-Objekt (Prinzip III, research.md R6)
- [X] T107 [US4] `rpg-platform/src/main/java/rpg/platform/classes/EquipmentLockListener.java`: alle
      sechs Routen abweisen, `PlayerDropItemEvent` für alle Items
- [X] T108 [US4] `BoundItemFactory` setzt den Bindungsschlüssel beim Bauen; nur B07 baut gebundene
      Gegenstände (contracts/class-api.md)

**Checkpoint**: Die Progression aus US3 ist gegen Verlust und Umgehung geschützt.

---

## Phase 7: User Story 5 — Die Klasse benennt ihre Fähigkeiten (Priority: P3)

**Ziel**: Sechs Fähigkeits-IDs je Klasse mit Art und Freischaltstufe, abgeleitet aus dem Level.

**Unabhängig prüfbar**: Ohne jede Fähigkeitslogik — die Klasse nennt genau sechs IDs, und eine
teilweise gefüllte Bindung wird beim Laden abgewiesen.

### Tests für User Story 5

- [X] T109 [P] [US5] `rpg-core/src/test/java/rpg/core/classes/AbilityBindingTest.java`: der Warrior
      nennt sechs IDs — vier aktive einschließlich der Unique, zwei passive (US5.1, FR-041)
- [X] T110 [P] [US5] `rpg-core/src/test/java/rpg/core/classes/AbilityBindingTest.java`: auf Level 19
      ist eine Fähigkeit mit Freischaltstufe 20 nicht dabei, auf Level 20 ist sie es (US5.3, FR-043)
- [X] T111 [P] [US5] `rpg-core/src/test/java/rpg/core/classes/AbilityBindingTest.java`: die
      Freischaltung wird **abgeleitet**, nicht gespeichert — es gibt keinen persistenten
      Freischaltzustand (FR-043)

### Implementierung für User Story 5

- [X] T112 [US5] `ClassRegistry.abilitiesOf` und `ClassRegistry.unlockedFor` umsetzen; `unlockedFor`
      fragt B06 nach dem Level. B07 löst die IDs **nicht** auf (FR-044)

**Checkpoint**: B08 kann anfangen — die Bindung steht, das Verhalten fehlt absichtlich.

---

## Phase 8: User Story 6 — Der Betreiber justiert Klassen ohne Codeänderung (Priority: P3)

**Ziel**: Basiswerte, Kurven, Stufenwerte, Materialien, Anzeigenamen und Bindungen sind
Konfiguration; eine unbekannte Klasse ist ein Startfehler.

**Unabhängig prüfbar**: Ohne laufenden Server — eine geänderte Konfiguration liefert geänderte Werte.

### Tests für User Story 6

- [X] T113 [P] [US6] `rpg-core/src/test/java/rpg/core/classes/ClassConfigReloadTest.java`: eine
      geänderte Basiswertzahl wirkt nach Neustart auf neue **und bestehende** Charaktere (US6.1)
- [X] T114 [P] [US6] `rpg-core/src/test/java/rpg/core/classes/ClassConfigReloadTest.java`: alle fünf
      Kategorien — Basiswerte, Wachstumskurven, Stufenwerte, Materialien, Anzeigenamen — sind ohne
      eine einzige Codeänderung austauschbar; je Kategorie eine Prüfung (SC-007)
- [X] T115 [P] [US6] `rpg-core/src/test/java/rpg/core/classes/ClassConfigReloadTest.java`: ein
      geänderter Anzeigename erscheint in der Auswahl (US6.5)

### Implementierung für User Story 6

- [X] T116 [US6] `rpg-plugin/src/main/resources/classes.yml`: **erledigt zusammen mit T005** — V1 verlangt alle drei Klassen, eine Datei mit nur dem Warrior wäre nicht ladbar. Rogue- und Mage-Leitern vollständig
      ausschreiben**. Im Vertragsdokument sind sie gekürzt; die Zahlen stehen in
      [spec.md](./spec.md), Abschnitt „Ausgearbeiteter Inhalt". Sieben Mage-Farben als Hex-Werte,
      drei Rogue-Trims (`RIB`/Kupfer, `SILENCE`/Amethyst, `VEX`/Netherite)
- [X] T117 [US6] `rpg-core/src/main/java/rpg/core/classes/ClassConfig.java`: Prüfung V19 gegen die
      Datenbank — keine gespeicherte Stufe darf über der konfigurierten Länge liegen; sonst Abbruch
      statt Herabstufung (FR-024, US6.4)

**Checkpoint**: Alle sechs User Stories funktionieren.

---

## Phase 9: Querschnitt — keine unmodellierte Wertquelle

**Zweck**: FR-046 bis FR-048. Der technische Kern nach plan.md, und der einzige Punkt, an dem B07
Vanilla-Verhalten unterdrückt.

### Tests

- [X] T118 [P] `rpg-platform/src/test/java/rpg/platform/classes/AttributeNeutralizationTest.java`:
      ein gebautes Klassenschwert hat einen **leeren** Modifikatorsatz. Der Test liest
      `getAttributeModifiers()` und erwartet leer — **nicht** das Setzen der Anzeige-Flagge, denn
      `HIDE_ATTRIBUTES` nimmt die Wirkung nicht (FR-046, research.md R2)
- [X] T119 [P] `rpg-platform/src/test/java/rpg/platform/classes/AttributeNeutralizationTest.java`:
      der Unterschied zwischen leerem Multimap und `null` ist geprüft — `null` stellt die Vorgaben
      **wieder her** und ist damit das Gegenteil von neutral (research.md R2, derselbe Fehlertyp wie
      der `Double.NaN`-Sentinel aus ADR-016)
- [X] T120 [P] `rpg-platform/src/test/java/rpg/platform/classes/AttributeNeutralizationTest.java`:
      je Waffentyp eine Prüfung — Schwert, Speer. Der Modifikatorsatz ist in beiden Fällen leer
- [ ] T121 **nur auf einem echten Server nachweisbar — siehe T143.** MockBukkit spiegelt keine Attributmodifikatoren von Items und kann leere Ueberschreibung nicht von keiner Ueberschreibung unterscheiden (nachgemessen: `hasAttributeModifiers()` false, Getter null, Serialisierung identisch, Metas `equals`). Ein Unit-Test darauf waere gruen, auch wenn der Aufruf fehlte. Der Quelltext-Nachweis steht in `AttributeNeutralizationTest`; dieser Punkt bleibt offen. Urspruenglich: zwei
      Charaktere mit identischem Angriffsgeschwindigkeitswert und unterschiedlichem Waffentyp haben
      dieselbe effektive Schlagrate, Abweichung 0 % (SC-011, FR-047). Der Test liest den
      tatsächlichen Attributwert am Spieler, nicht die Konfiguration — sonst prüft er sich selbst
- [X] T122 [P] `rpg-platform/src/test/java/rpg/platform/classes/BoundItemFactoryTest.java`: die Werte
      eines gebundenen Gegenstands stammen **ausschließlich** aus der Konfiguration, nicht aus
      Vanilla-Eigenschaften des Materials — eine Netherite-Stufe ist stark, weil die Konfiguration es
      sagt (FR-048)

### Implementierung

- [X] T123 `BoundItemFactory`: `ItemMeta.setAttributeModifiers` mit einem leeren, **nicht-null**
      Multimap aufrufen. Danach `ItemFlag.HIDE_ATTRIBUTES` setzen — aus Darstellungsgründen, nachdem
      die Modifikatoren tatsächlich entfernt sind (research.md R2)
- [X] T124 `rpg-platform/src/main/java/rpg/platform/classes/InventoryFullNoticeListener.java`:
      Warnung bei vollem Inventar über die Nachrichtenschnittstelle mit einem Schlüssel aus
      `ClassMessageKeys` — **kein** direkter Spieleraufruf. Kein automatisches Aufräumen, keine
      Hintergrundbank, kein stilles Verwerfen (FR-030, ADR-005)
- [X] T125 [P] `rpg-platform/src/test/java/rpg/platform/classes/InventoryFullNoticeTest.java`: bei
      vollem Inventar und anfallender Beute erscheint eine Warnung, und nichts verschwindet (US4.6)

---

## Phase 10: Polish, Verdrahtungsnachweis und Persistenznachweis

### Verdrahtung — ADR-012

Grüne Modultests beweisen nicht, dass das Modul im Plugin verdrahtet ist. Dafür gibt es eine eigene
Aufgabe und einen eigenen Test.

- [X] T126 `rpg-plugin/src/main/java/rpg/plugin/`: **Befund aus US1** — `ClassSelectionListener` darf `PlayerJoinEvent`/`PlayerQuitEvent` NICHT selbst behandeln: `NoCompetingSessionListenersTest` erzwingt genau einen Ein- und Ausgang des Sitzungslebenszyklus (FR-007/FR-014). Die Einstiege sind deshalb `openIfNeeded(player)` und `onSessionEnded(playerId)` und muessen hier aufgerufen werden. Ein Sitzungs-bereit-Ereignis auf dem Bus aus B01 gibt es noch nicht — entweder hier direkt aufrufen oder B03 additiv um ein solches Ereignis erweitern. Klassenmodul in die bestehende Modulverdrahtung
      eintragen — `ClassConfig`, `ClassRegistry`, `ClassStatContributor`, `ClassProgressRepository`
      und alle vier Listener aus `rpg-platform`.
      **Umgesetzt:** `ClassesModule` in `rpg-persistence`, eingetragen in `RpgPlugin.modules()`;
      `assembleClassLayer()` baut die vier Listener und gibt den `SessionObserver` zurück, den
      `registerSessionListeners` trägt. `classes.yml` steht in `DEFAULT_CONFIG_FILES`,
      `ClassMessageKeys.all()` in der Schlüsselprüfung.
      **Vier Nähte, die es dafür noch nicht gab** (jede mit Test, alle additiv):
      `SessionObserver` in `rpg-platform.session` (B03 erlaubt nur einen Join-Handler);
      `PlayerSession.activate` plus `SessionLifecycle.activateCharacter` und
      `SessionAttachment.onCharacterActivated` (die Wahl legte den Charakter an, aber die laufende
      Sitzung konnte ihn nicht annehmen — die Wahl hätte erst beim nächsten Login gewirkt);
      `SessionAttachment.order()` (B04 rechnet und attachte vor seinen Zulieferern B06/B07, wodurch
      `restoreResources` gegen einen Snapshot ohne Level und ohne Klasse geklemmt hätte);
      `PaperClassNotice` als erste Titel-/Ton-Ausgabe des Projekts
- [X] T127 `rpg-plugin/src/test/java/rpg/plugin/FullBootstrapTest.java`: erweitern — das Klassenmodul
      startet, die vier Listener sind registriert, `ClassStatContributor` hängt an der `StatEngine`
      (ADR-012). Sieben Zusicherungen ergänzt: Dienst auflösbar, Migration gelaufen, Attachment
      eingehängt, Attachment-Reihenfolge, Ersetzung des Levelwachstums, geladene Leiterlängen,
      Ereignis-Handler. `PlayerMoveEvent` steht jetzt bei zwei Handlern (B03 hält beim Laden, B07 hält
      bis zur Wahl) — die Lebenszyklus-Invariante Join/Quit je einer bleibt unverändert
- [X] T128 [P] `rpg-plugin/src/test/java/rpg/plugin/MessageKeyResolutionTest.java`: jeder Schlüssel
      aus `ClassMessageKeys` ist in `messages.yml` auflösbar (Prinzip V)

### Persistenz gegen echtes PostgreSQL

- [X] T129 [P]
      `rpg-persistence/src/test/java/rpg/persistence/classes/ClassProgressRepositoryTest.java`:
      Schreiben, Lesen, Revisionszähler. Testcontainers mit echtem PostgreSQL, keine Mocks
      (Prinzip VII)
- [X] T130 [P]
      `rpg-persistence/src/test/java/rpg/persistence/classes/ClassProgressMigrationTest.java`:
      `V7_1` legt die Tabelle an; `V3_1` ist **unverändert** — insbesondere Enum und
      `chk_character_class` (ADR-019)
- [X] T131 [P]
      `rpg-persistence/src/test/java/rpg/persistence/classes/ClassProgressPersistenceTest.java`:
      Klassenwahl und beide Stufen überstehen Relogin und Serverneustart verlustfrei (SC-006,
      US3.5, US1 FR-038)
- [X] T132 [P] `rpg-persistence/src/test/java/rpg/persistence/classes/TierLengthGuardTest.java`:
      V19 — eine Konfiguration mit weniger Stufen als ein gespeicherter Stand bricht den Start ab,
      statt herabzustufen (FR-024). Die einzige Zusage, die die Datenbank braucht

### Querschnittsnachweise

- [X] T133 [P] `rpg-core/src/test/java/rpg/core/classes/NoBukkitDependencyTest.java`: kein Typ in
      `rpg.core.classes` verweist auf Bukkit (Prinzip III.1)
- [X] T134 [P] `rpg-platform/src/test/java/rpg/platform/classes/SchedulerUsageTest.java`: das
      Wiederöffnen der Auswahl nutzt den entity-gebundenen Scheduler, **nie** den globalen
      Bukkit-Scheduler (Prinzip I, ADR-007)
- [X] T135 [P] `rpg-core/src/test/java/rpg/core/classes/ClassDefinitionImmutabilityTest.java`: eine
      geladene Definition ist unveränderlich und wird von allen Spielern geteilt — drei Objekte für
      den ganzen Server (Prinzip I, data-model.md)
- [X] T136 `specs/007-class-system/quickstart.md` Abschnitte 1 bis 10 durchlaufen; die Zahl der
      **übersprungenen** Tests muss 0 sein, nicht nur die der fehlgeschlagenen. MockBukkit meldet
      Nicht-Implementiertes als „skipped"
- [X] T137 [P] `02-decisions.md`: **ADR-021 angelegt** — Auswahl bei jedem Beitritt, Sitzung wählt
      keinen Charakter mehr selbst, `SessionAttachment.order()`, Auswahlfrist, unzerstörbare
      Ausrüstung. ADR-020 entsprechend als erweitert markiert. Ursprünglich: ADR-021 mit den Umsetzungsentscheidungen aus B07 anlegen —
      insbesondere dem unbenutzten `SourceKind.CLASS` und dem leeren-statt-null-Multimap
- [X] T138 [P] `minecraft-rpg-spec/minecraft-rpg-spec/blocks/B07-class-system.md`: Status auf
      „umgesetzt" setzen

### Auf einem echten Paper-Server

Die Abschnitte 1 bis 10 beweisen nichts über Papers `libraries:`-Klassenlader und nichts über
tatsächliches Client-Verhalten. Nach dem Treiberfehler aus ADR-010 gilt: grüne Tests belegen die
Laufzeitumgebung nicht. Diese Aufgaben bleiben offen, bis ein Server läuft.

- [ ] T139 Abschnitt 11 Punkte 1 bis 2 — Start mit gültiger Konfiguration und Start mit vierter
      Klassen-ID; der zweite muss den Start **verhindern** und die ID nennen
- [X] T140 Abschnitt 11 Punkte 3 bis 7 — Auswahl öffnet sich, ist nicht schließbar, keine Bewegung,
      kein Schaden, nach der Wahl Charakter mit Stufe-1-Ausrüstung
      **Von Hand bestätigt (2026-08-22).** Auswahl öffnet bei jedem Beitritt, ist über keine Route
      schließbar, Bewegung gesperrt, nach der Wahl Stufe-1-Ausrüstung angelegt
- [X] **Von Hand bestätigt (2026-08-22).** T141 Abschnitt 11 Punkte 8 bis 10 — Rüstung von Hand über jede Route ausziehen versuchen, Item
      werfen versuchen, dann einen Mob töten. Der Mob-Drop ist der **Gegentest**: er muss fallen
- [ ] T142 Abschnitt 11 Punkte 11 bis 13 — Stufe weiterschalten je Klasse. Der Mage-Punkt belegt die
      Färbung als Stufenmarker, der Rogue-Punkt den Trim; ohne sie ist die Sichtbarkeit unbelegt
- [ ] T143 Abschnitt 11 Punkt 14 — Angriffsgeschwindigkeit von Warrior mit Schwert und Mage mit Speer
      bei künstlich gleichem Attributwert vergleichen. **Der wichtigste Punkt**: der einzige, der die
      Kernentscheidung aus research.md R2 im laufenden Spiel prüft (SC-011)
- [X] T144 Abschnitt 11 Punkte 15 bis 16 — Relogin und Neustart, dann Inventar vollmachen und Beute
      erzeugen
      **Von Hand bestätigt (2026-08-22).** Relogin und Neustart erhalten Klasse, Stufen, Level,
      Leben, Inventar und Enderchest; die Meldung bei vollem Inventar erscheint

---

## Dependencies & Execution Order

### Phasenabhängigkeiten

- **Phase 1 Setup**: keine Abhängigkeit
- **Phase 2 Foundational**: hängt an Phase 1 und **blockiert alle** User Stories
- **Phase 3 US1 (P1, MVP)**: hängt an Phase 2
- **Phase 4 US2 (P1)**: hängt an Phase 2. Unabhängig von US1 prüfbar, aber im Spiel erst nach US1
  sichtbar, weil ohne Wahl kein Charakter existiert
- **Phase 5 US3 (P2)**: hängt an Phase 2 und an US2 — die Stufenwerte laufen durch denselben
  Contributor
- **Phase 6 US4 (P2)**: hängt an US3, weil es die dort gebauten Gegenstände schützt
- **Phase 7 US5 (P3)** und **Phase 8 US6 (P3)**: hängen nur an Phase 2, untereinander unabhängig
- **Phase 9 Querschnitt**: hängt an US3 (`BoundItemFactory` muss existieren)
- **Phase 10 Polish**: hängt an allen gewünschten Stories

### Innerhalb einer Story

Tests zuerst und **fehlschlagend**, dann Werttypen, dann Regeln, dann Plattformanbindung.

### Parallelisierbare Abschnitte

- T001 bis T004 und T006 vollständig parallel
- T007 bis T012 vollständig parallel (verschiedene Dateien, keine Abhängigkeit)
- **T021 bis T039** — die 19 Konfigurationszusagen — vollständig parallel. Der größte Block
- **T092 bis T104** — die Routen der Inventarsperre — vollständig parallel. Der zweitgrößte
- T062 bis T071 parallel
- T075 bis T085 parallel
- T129 bis T135 parallel
- **Nicht parallel**: T043 bis T046 (die drei Registrierungen und ihr Invariantentest laufen in
  Reihe), T139 bis T144 (ein Server, von Hand)

---

## Implementation Strategy

### MVP zuerst

1. Phase 1 Setup
2. Phase 2 Foundational — **blockiert alles**, insbesondere die drei Registrierungen T043 bis T045
3. Phase 3 US1 — Klassenwahl
4. **Anhalten und prüfen**: ein neuer Spieler kann eine Klasse wählen und bekommt einen Charakter

Nach diesem Punkt ist B07 spielbar, auch ohne Ausrüstungsleitern: der Charakter hat noch keine
klassenspezifischen Werte, aber er existiert und ist persistent.

### Inkrementelle Lieferung

1. Setup + Foundational → Fundament steht
2. US1 → Klassenwahl funktioniert → **MVP**
3. US2 → die Wahl wird spürbar, weil die Werte sich unterscheiden
4. US3 → die dominante Stat-Quelle entsteht; ab hier trägt B07 rund 70 % der Endpower
5. US4 → die Progression aus US3 ist gegen Verlust geschützt
6. Phase 9 → der Waffentyp verliert seinen unmodellierten Einfluss. **Vor** einem echten Serverlauf
   nötig, sonst messen die Punkte 11 bis 14 aus Abschnitt 11 Unsinn
7. US5 → B08 kann anfangen
8. US6 → Balancing ohne Codeänderung

### Reihenfolge, von der abzuweichen teuer wird

**Phase 9 vor T143.** Wer den Serverlauf vor der Neutralisierung macht, vergleicht
Angriffsgeschwindigkeiten, die noch vom Waffentyp verschoben werden, und hält das Ergebnis für einen
Balancing-Fehler statt für den bekannten Grund.

**T043 bis T046 gemeinsam.** ADR-015 ist aus genau dem Fehler entstanden, eine der drei
Registrierungen zu vergessen. T046 ist der Test, der es zeigt — er gehört unmittelbar dahinter, nicht
in die Polish-Phase.

---

## Notes

- `[P]` = andere Datei, keine offene Abhängigkeit
- `[Story]` verweist auf die User Story aus [spec.md](./spec.md)
- Jede User Story ist für sich prüfbar; die Checkpoints sind die Haltepunkte
- Tests schlagen fehl, bevor implementiert wird
- Nach jeder Aufgabe oder Gruppe committen
- **Ein Block ist erst fertig, wenn er im Plugin verdrahtet und `FullBootstrapTest` grün ist**
  (ADR-012) — T126 und T127 sind keine Formalie
