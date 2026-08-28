---

description: "Aufgabenliste B11 · Items, Ausrüstung & Loot"
---

# Tasks: B11 · Items, Ausrüstung & Loot

**Input**: Entwurfsunterlagen aus `/specs/011-items-loot-equipment/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: **Pflicht, nicht optional.** Prinzip VII verlangt für jede Formel und jede Regel der
Domänenschicht Unit-Tests ohne laufenden Server, und für Persistenz einen echten Testcontainer statt
Mocks. Testaufgaben stehen deshalb je Geschichte **vor** der Umsetzung.

**Organization**: gruppiert nach den sieben User Stories der Spec, in Abhängigkeitsreihenfolge. Die
drei Eingriffe in fertige Blöcke stehen in einer eigenen Phase, weil sie eine eigene
Abnahmebedingung haben.

## Format: `[ID] [P?] [Story] Beschreibung`

- **[P]**: parallelisierbar — andere Datei, keine offene Abhängigkeit
- **[Story]**: US1 bis US7; Setup, Foundational, Eingriffe und Polish tragen keine
- Jede Aufgabe nennt ihren Pfad und ihren Bezug (FR, SC, R oder Artefakt)

## Pfade

- `rpg-core/src/main/java/rpg/core/item/` — Vorlagen, Beute, Verschleiß, Preise, **ohne Bukkit**
- `rpg-platform/src/main/java/rpg/platform/item/` — hier und nur hier wird Paper angefasst
- `rpg-platform/src/main/java/rpg/platform/drop/` — **neu, geteilt** zwischen B08b und B11 (R5)
- `rpg-persistence/src/main/resources/db/migration/` — `V11_1` (Rückbau), `V11_2` (Zustand),
  `V11_3` (Kosmetik). **Umnummeriert gegenüber dem Plan:** Flyway läuft hier ohne `outOfOrder`, also
  müssen die Nummern in der Reihenfolge stehen, in der sie entstehen — und der Rückbau kam zuerst
- `rpg-plugin/src/main/resources/items.yml` — neu
- `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` — die Verdrahtung
- Tests jeweils unter `src/test/java/` desselben Moduls

**Das Item selbst wird nicht in der Datenbank geführt.** Es lebt im PDC innerhalb von B03s
Inventar-Blob (FR-004, R2). Die beiden neuen Tabellen tragen den **Zustand** und die **Kosmetik**,
nicht die Gegenstände.

---

## Phase 1: Setup — Pakete und Grenzen

**Purpose**: die Blockgrenzen benennen, bevor etwas darin entsteht

- [X] T001 [P] Paket `rpg-core/src/main/java/rpg/core/item/` mit `package-info.java` anlegen — Kopfkommentar nennt die Grenze: Vorlagen, Beute, Verschleiß und Preise gehören hierher, **Paper nirgends**, und **Ausrüstung ausdrücklich nicht** (ADR-017, FR-081)
- [X] T002 [P] Paket `rpg-platform/src/main/java/rpg/platform/item/` mit `package-info.java` anlegen — hier und nur hier wird Paper angefasst; nennt die drei Nähte, die von außen kommen (B05-Ereignisbus, B07-Prädikat, B08b-Währung)
- [X] T003 [P] Paket `rpg-platform/src/main/java/rpg/platform/drop/` mit `package-info.java` anlegen — **das einzige Paket dieses Projekts, das keinem Block allein gehört**: B08b und B11 benutzen es. Der Kopfkommentar übernimmt die sechs Fallen aus `rpg/platform/currency/package-info.java` (R5)
- [X] T004 [P] Testpakete unter `rpg-core/src/test/java/rpg/core/item/`, `rpg-platform/src/test/java/rpg/platform/item/` und `.../drop/` anlegen

---

## Phase 2: Foundational — Vorlagen, Schema, PDC

**Purpose**: das Item als Datenobjekt, ohne das keine Geschichte anfangen kann

**⚠️ Blockiert alle Geschichten.**

- [X] T005 [P] `ItemCategory` in `rpg-core/src/main/java/rpg/core/item/ItemCategory.java` — genau zwei Werte, `CONSUMABLE` und `COSMETIC`; Javadoc sagt, **warum es keine dritte gibt** (ADR-039: Aufstiegsmaterial entfällt, Ausrüstung ist B07)
- [X] T006 [P] `Rarity` in `rpg-core/src/main/java/rpg/core/item/Rarity.java` — acht Stufen mit Farbe; Javadoc: **reines Etikett ohne Wertwirkung** (FR-014, ADR-027). Ein epischer Trank heilt nicht mehr als ein gewöhnlicher
- [X] T007 [P] Test `RarityHasNoEffectTest` in `rpg-core/src/test/java/rpg/core/item/RarityHasNoEffectTest.java` — **der Test, der einen Rückfall auffliegen ließe**: keine Methode von `Rarity` liefert eine Zahl, die in eine Wirkung eingeht; zwei Vorlagen gleicher Wirkung und verschiedener Rarität wirken gleich
- [X] T007a [P] Test `TwoCopiesAreIdenticalTest` in `rpg-core/src/test/java/rpg/core/item/TwoCopiesAreIdenticalTest.java` — **FR-010, der Wächter gegen die Rückkehr des Würfelns**: zwei Exemplare derselben Vorlage tragen dieselben Werte, über viele Erzeugungen. Kein Wertebereich, kein Roll, kein Affix (ADR-027). **Dieses Projekt hat den Roll-Wortlaut sechs Tage in der Constitution überleben lassen** — die Zusage braucht einen Test, der sie hält
- [X] T008 [P] `ConsumableEffect` in `rpg-core/src/main/java/rpg/core/item/ConsumableEffect.java` — Heilung, Mana, zeitlicher Attributbeitrag mit Dauer, Abklingzeit ([data-model.md](./data-model.md) §1)
- [X] T009 `ItemTemplate` in `rpg-core/src/main/java/rpg/core/item/ItemTemplate.java` — die einzige Quelle für alles, was ein Gegenstand ist; Felder nach [data-model.md](./data-model.md) §1
- [X] T010 `ItemConfig` in `rpg-core/src/main/java/rpg/core/item/ItemConfig.java` — der validierte Inhalt von `items.yml`: Vorlagen, Beutetabellen, NPC-Bestände, Verschleiß, Preise
- [X] T011 `ItemConfigSchema` in `rpg-core/src/main/java/rpg/core/item/ItemConfigSchema.java` — Prüfung nach der Regeltabelle in [contracts/item-config.md](./contracts/item-config.md); jede Meldung nennt **Datei, Schlüssel und Grund** (FR-012)
- [X] T012 Test `ItemConfigSchemaTest` in `rpg-core/src/test/java/rpg/core/item/ItemConfigSchemaTest.java` — je ein Fall für jede Regel der Tabelle; geprüft wird nicht nur *dass* es scheitert, sondern **dass die Meldung den Schlüssel nennt**
- [X] T013 Test `DeathOutweighsCombatTest` in `rpg-core/src/test/java/rpg/core/item/DeathOutweighsCombatTest.java` — **die Startprüfung aus FR-044**: eine Konfiguration mit `per-death: 0.5` gegen `per-damage-taken: 0.01` und `death-factor-min: 100` wird zurückgewiesen. Ohne diesen Test könnte ein späteres Balancing die Todesstrafe aus ADR-017 stillschweigend aushebeln
- [X] T014 `ItemModule` in `rpg-core/src/main/java/rpg/core/item/ItemModule.java` — Start und Nachladen nach dem Muster von `MobModule`
- [X] T015 Test `ItemModuleReloadTest` in `rpg-core/src/test/java/rpg/core/item/ItemModuleReloadTest.java` — Start scheitert bei unbrauchbarer Konfiguration; Nachladen tauscht die Konfiguration **im Ganzen**, und ein vorhandenes Exemplar wird dabei nicht angefasst
- [X] T016 `Items` als öffentliche Fassade in `rpg-core/src/main/java/rpg/core/item/Items.java` — Signaturen nach [contracts/item-api.md](./contracts/item-api.md) §1
- [X] T017 Test `ItemApiContractTest` in `rpg-core/src/test/java/rpg/core/item/ItemApiContractTest.java` — unbekannte Vorlagen-ID antwortet **leer**, nicht `null`; ein Stapel ohne B11-Vermerk antwortet leer; nichts wirft
- [X] T018 `ItemTag` in `rpg-platform/src/main/java/rpg/platform/item/ItemTag.java` — schreibt und liest **genau zwei** PDC-Werte: Vorlagen-ID und Schema-Version ([data-model.md](./data-model.md) §4, FR-001, FR-004)
- [X] T019 Test `ItemTagTest` in `rpg-platform/src/test/java/rpg/platform/item/ItemTagTest.java` — Schreiben und Zurücklesen; ein zweites Schreiben **ersetzt** statt zu verdoppeln; `null` ist kein Fehler — nach dem Muster von `MobKindTagTest`. Dazu eine Zusicherung für **FR-006**: das Feld für Custom-Model-Data ist vorhanden und wird im Vanilla-Betrieb nicht gesetzt (ADR-005)
- [X] T020 Test `ItemStoresOnlyTheTemplateIdTest` in `rpg-platform/src/test/java/rpg/platform/item/ItemStoresOnlyTheTemplateIdTest.java` — **die Kernzusage aus ADR-004**: der PDC eines erzeugten Exemplars enthält **keinen** berechneten Endwert und **kein** gerendertes Lore. Der Test liest den Container aus und zählt die Schlüssel
- [X] T021 `ItemStackFactory` in `rpg-platform/src/main/java/rpg/platform/item/ItemStackFactory.java` — Vorlage → `ItemStack`; Name, Lore, Raritätsfarbe und Wirkungstext werden **abgeleitet**, nie gespeichert (FR-002). Texte über Message-Schlüssel (Prinzip V)
- [X] T022 Test `TemplateChangeReachesExistingItemsTest` in `rpg-platform/src/test/java/rpg/platform/item/TemplateChangeReachesExistingItemsTest.java` — **SC-001, der Grund für die ganze Bauweise**: Exemplar erzeugen, Vorlage ändern, neu laden, Exemplar wirkt mit dem neuen Wert. Ohne dass das Inventar angefasst wurde
- [X] T023 `ItemSchemaMigration` in `rpg-platform/src/main/java/rpg/platform/item/ItemSchemaMigration.java` — hebt eine ältere PDC-Version beim Laden an (FR-005). Version 1 ist die erste; **der Pfad existiert ab Tag eins**, damit er nicht nachträglich erfunden werden muss
- [X] T024 Test `ItemSchemaMigrationTest` in `rpg-platform/src/test/java/rpg/platform/item/ItemSchemaMigrationTest.java` — Version 1 → 2 verlustfrei (SC-003)
- [X] T025 Test `UnknownTemplateStaysInertTest` in `rpg-platform/src/test/java/rpg/platform/item/UnknownTemplateStaysInertTest.java` — ein Exemplar, dessen Vorlage verschwunden ist, bleibt erhalten, ist tragbar und vernichtbar, **nicht** benutzbar und **nicht** verkäuflich; der Start bricht nicht ab (FR-007)
- [X] T026 Test `TamperedItemIsRejectedTest` in `rpg-platform/src/test/java/rpg/platform/item/TamperedItemIsRejectedTest.java` — manipulierter PDC wird erkannt und abgelehnt (FR-008, Prinzip VI)
- [X] T027 `items.yml` in `rpg-plugin/src/main/resources/items.yml` anlegen — vollständig nach [contracts/item-config.md](./contracts/item-config.md), mit den Kommentaren, die die Entscheidungen tragen
- [X] T028 Test `ShippedItemConfigTest` in `rpg-core/src/test/java/rpg/core/item/ShippedItemConfigTest.java` — die **ausgelieferte** Datei lädt fehlerfrei und erfüllt jede Regel des Schemas; nach dem Muster von `ShippedMobConfigTest`
- [X] T029 Test `ConfigOnlyItemTest` in `rpg-core/src/test/java/rpg/core/item/ConfigOnlyItemTest.java` — **SC-002 und SC-004**: eine neue Vorlage entsteht aus Konfiguration allein, und **kein Bezeichner einer einzelnen Vorlage kommt im Code vor**. Nach dem Muster von `ConfigOnlyMobTest`

**Checkpoint**: Das Item ist ein Datenobjekt. Die Geschichten können beginnen.

---

## Phase 3: Die drei Eingriffe in fertige Blöcke

**Purpose**: was der Plan im Complexity Tracking begründet hat — vor den Geschichten, die darauf
aufbauen

**⚠️ Gemeinsame Abnahmebedingung für alle drei Gruppen: der bestehende Testbestand der berührten
Blöcke bleibt unverändert grün.** Wo eine Zusicherung umgedreht werden muss, wird sie
**umgedreht statt gelöscht**, damit die Änderung im Diff sichtbar bleibt — wie ADR-027 es für
`AbilityRankTest` gehalten hat.

### Gruppe A · Die Verschleißnaht in B07 (research.md R1)

- [X] T030 `GearConditionFactor` in `rpg-core/src/main/java/rpg/core/classes/GearConditionFactor.java` — funktionale Schnittstelle mit `double factorFor(UUID, LadderSlot)` und der Konstanten `NONE`, die konstant `1.0` liefert ([contracts/item-api.md](./contracts/item-api.md) §2)
- [X] T031 `EquipmentLadder.contributeTo` in `rpg-core/src/main/java/rpg/core/classes/EquipmentLadder.java` um einen `double factor` erweitern — **als Parameter, nicht als umhüllender Sink**, damit im Neuberechnungspfad nichts alloziert wird (Prinzip II, R1)
- [X] T032 `ClassStatContributor` in `rpg-core/src/main/java/rpg/core/classes/ClassStatContributor.java` — nimmt `GearConditionFactor` im Konstruktor und reicht den Faktor je Slot an `contributeTo` weiter. Javadoc ergänzen: **warum kein Modifikator** — die Stufenwerte sind Grundwerte, und ein negativer `EQUIPMENT`-Beitrag wäre an B04s Modifikatorband still abgeschnitten worden (R1)
- [X] T033 Test `ClassContributionIsUnchangedWithoutB11Test` in `rpg-core/src/test/java/rpg/core/classes/ClassContributionIsUnchangedWithoutB11Test.java` — **die Abnahmebedingung dieser Gruppe**: mit `GearConditionFactor.NONE` liefert `ClassStatContributor` bitgenau dieselben Werte wie vor der Änderung
- [X] T034 ~~Bestehende Aufrufer auf die neue Signatur ziehen~~ — **entfällt.** Beide Nähte sind als **Überladung** gebaut statt als Signaturänderung: `ClassStatContributor` behält seinen vierstelligen Konstruktor (er delegiert mit `NONE`), `EquipmentLadder.contributeTo` seine zweistellige Fassung. Kein einziger Aufrufer und kein einziger bestehender Test musste angefasst werden — was die Abnahmebedingung aus T035 von einer Behauptung zu einer buchstäblichen macht
- [X] T035 Vollen Testlauf von B07 fahren: `./gradlew :rpg-core:test --tests 'rpg.core.classes.*'` — **unverändert grün, null Übersprungene**. Kein Test wird angepasst, außer den Konstruktoraufrufen aus T034

### Gruppe B · Die Eigentumsmechanik aus B08b herausziehen (research.md R5)

- [X] T036 `OwnedDrops` in `rpg-platform/src/main/java/rpg/platform/drop/OwnedDrops.java` — Schnittstelle nach [contracts/item-api.md](./contracts/item-api.md) §2
- [X] T037 `OwnedDrop` in `rpg-platform/src/main/java/rpg/platform/drop/OwnedDrop.java` — setzt den Gegenstand, `setVisibleByDefault(false)`, `showEntity` für den Berechtigten, `setOwner`, `setCanMobPickup(false)`, eindeutige Kennung gegen das Verschmelzen. **Aus `CoinPile` gehoben, nicht neu geschrieben**
- [X] T038 `OwnedDropRegistry` in `rpg-platform/src/main/java/rpg/platform/drop/OwnedDropRegistry.java` — stellt die Sichtbarkeit nach Relogin und Charakterwechsel wieder her. Javadoc übernimmt die Begründung aus `CoinPileRegistry`: **`showEntity` ist Zustand der Verbindung**, und „unsichtbar aber aufsammelbar ist das Schlechteste von beidem"
- [X] T039 `OwnedDropPickupListener` in `rpg-platform/src/main/java/rpg/platform/drop/OwnedDropPickupListener.java` — **das zweite Schloss**: `PlayerAttemptPickupItemEvent` prüft den **Charakter**, weil `setOwner` nur Spieler kennt (ADR-011). Unsichtbarkeit ist Darstellung und niemals die Autorität (Prinzip VI)
- [X] T040 [P] Test `OwnedDropVisibilityTest` in `rpg-platform/src/test/java/rpg/platform/drop/OwnedDropVisibilityTest.java` — nur der Eigentümer sieht den Gegenstand; ein zweiter Spieler nicht
- [X] T041 [P] Test `OwnedDropPickupTest` in `rpg-platform/src/test/java/rpg/platform/drop/OwnedDropPickupTest.java` — ein Fremder hebt **nichts** auf, auch wenn er ihn sähe; der zweite Charakter desselben Spielers ebenfalls nicht
- [X] T042 [P] Test `OwnedDropSurvivesReloginTest` in `rpg-platform/src/test/java/rpg/platform/drop/OwnedDropSurvivesReloginTest.java` — nach `restoreVisibility` sieht der Eigentümer wieder, was ihm gehört (FR-030)
- [X] T043 [P] Test `OwnedDropsDoNotMergeTest` in `rpg-platform/src/test/java/rpg/platform/drop/OwnedDropsDoNotMergeTest.java` — zwei gleichartige Gegenstände verschiedener Eigentümer verschmelzen nicht (FR-031)
- [X] T044 `CoinPile` in `rpg-platform/src/main/java/rpg/platform/currency/CoinPile.java` auf `OwnedDrops` umstellen — behält, was ihm eigen ist (Betrag im Datencontainer, Zusammenfassen **vor** dem Ablegen), gibt die Eigentumsmechanik ab
- [X] T045 Vollen Testlauf von B08b fahren: `./gradlew :rpg-platform:test --tests 'rpg.platform.currency.*'` — **unverändert grün**. Die Zusicherungen über Coin-Haufen gelten weiter, nur die Innereien liegen woanders

### Gruppe C · `item_instance` zurückbauen (research.md R2)

- [X] T046 Migration `V11_1__drop_item_instance.sql` in `rpg-persistence/src/main/resources/db/migration/` — **bricht ab, wenn Zeilen vorhanden sind**, statt sie zu löschen; danach `DROP TABLE rpg.item_instance`. Der Kopfkommentar begründet den Rückbau wie `V3_2` seinerzeit den Umzug: eine Tabelle, die geladen und nie geschrieben wird, ist eine Falle für den nächsten Block
- [X] T047 Test `DropItemInstanceMigrationTest` in `rpg-persistence/src/test/java/` — gegen echtes PostgreSQL (Testcontainers, Prinzip VII): leere Tabelle wird entfernt; **eine Tabelle mit einer Zeile bricht die Migration ab**, und die Transaktion rollt zurück
- [X] T048 `ItemInstance`, `ItemInstanceRepository` und `JdbcItemInstanceRepository` entfernen — aus `rpg-core/src/main/java/rpg/core/persistence/` und `rpg-persistence/src/main/java/rpg/persistence/jdbc/`
- [X] T049 Feld `items` aus `SessionBundle` in `rpg-core/src/main/java/rpg/core/session/SessionBundle.java` entfernen und alle Aufrufer nachziehen — es lädt bei jedem Sitzungsstart eine Liste, die niemand liest
- [X] T050 Vollen Testlauf von B02 und B03 fahren — **unverändert grün**; wo ein Test das Feld `items` belegte, wird die Zusicherung **umgedreht statt gelöscht**: der Bundle trägt es nicht mehr

**Checkpoint**: Die Nähte stehen, nichts Fremdes ist kaputt.

---

## Phase 4: User Story 1 — Ein Item übersteht alles (P1) 🎯 MVP

**Goal**: Ein Gegenstand überlebt Relogin, Neustart und Migration, und eine Balancing-Änderung
erreicht jedes vorhandene Exemplar.

**Independent Test**: Exemplar erzeugen, speichern, neu laden, Vorlage ändern — ohne NPC, ohne
Beute, ohne Verbrauch.

Die tragenden Teile sind bereits in Phase 2 entstanden (T018 bis T029). Hier bleibt die Verdrahtung.

- [X] T051 [US1] `ItemModule` in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` verdrahten — Ladereihenfolge nach `ZoneModule` und `MobModule`, weil Beutetabellen Zonen und Arten nennen
- [X] T052 [US1] Test in `FullBootstrapTest` ergänzen — **ein Modul, dessen Modultests grün sind, ist nicht fertig**: `items.yml` wird geladen, die Fassade steht, der Start bleibt grün
- [X] T053 [P] [US1] `ItemMessageKeys` in `rpg-core/src/main/java/rpg/core/item/ItemMessageKeys.java` und die zugehörigen Schlüssel für Item-Namen, Lore-Zeilen und Raritätsbezeichnungen in `rpg-plugin/src/main/resources/messages.yml` — keine hartcodierten Spielertexte (Prinzip V), nach dem Muster von `MobMessageKeys`
- [X] T054 [US1] Test `ItemCarriesNoOwnerTest` in `rpg-platform/src/test/java/rpg/platform/item/ItemCarriesNoOwnerTest.java` — der Trank des ersten Charakters ist beim zweiten nicht da (ADR-011, SC-003). **Anders zugeschnitten als geplant, und schärfer:** dass der Trank nicht mitwandert, garantiert **B03s** `CharacterInventory` — B11 muss dafür nichts bauen, sondern nur nichts *dazwischen*bauen. Der Test prüft deshalb, dass der Vermerk **keine Identität trägt** und auch keine Tabelle daneben eine hält. Eine Kennung am Gegenstand wäre eine zweite Wahrheit darüber, wem er gehört, und zwei Wahrheiten stimmen genau bis zum ersten Charakterwechsel überein

**Checkpoint**: US1 steht allein. Inhalte sind ab hier balancierbar — das ist der MVP.

---

## Phase 5: User Story 2 — Beute, und sie gehört mir allein (P2)

**Goal**: Aus Kreaturen fällt Beute, sie gehört genau einem Charakter, und in einer Party wandert
sie reihum.

**Independent Test**: Zwei Spieler bekämpfen dieselbe Horde; jeder sieht nur seine eigene Ausbeute.

- [X] T055 [P] [US2] `LootEntry` und `LootTable` in `rpg-core/src/main/java/rpg/core/item/` — Wahrscheinlichkeit und Stückzahlspanne; **die Stückzahl ist der einzige Zufall in diesem Block** (FR-021)
- [X] T056 [P] [US2] Test `LootTableTest` in `rpg-core/src/test/java/rpg/core/item/LootTableTest.java` — über viele Ziehungen trifft die Häufigkeit die konfigurierte Wahrscheinlichkeit; Stückzahl bleibt in der Spanne
- [X] T057 [US2] `LootTableLookup` in `rpg-core/src/main/java/rpg/core/item/LootTableLookup.java` — Art vor Region, Boss getrennt (FR-018, FR-023)
- [X] T058 [US2] Test `LootTableLookupTest` in `rpg-core/src/test/java/rpg/core/item/LootTableLookupTest.java` — **zwei Arten auf derselben Vanilla-Basis liefern verschiedene Beute** (FR-020); eine Art ohne eigene Tabelle erbt die der Region; der Boss erbt **nicht**
- [X] T059 [US2] `PartyLootRotation` in `rpg-core/src/main/java/rpg/core/item/PartyLootRotation.java` — die Party gilt als **ein** Beitragender (FR-026a); `partyId → Index`, vorrücken **je gefallenem Gegenstand**, Aufräumen über `PartyChangedEvent` (FR-026b, FR-026d, R6)
- [X] T060 [US2] Test `PartyLootRotationTest` in `rpg-core/src/test/java/rpg/core/item/PartyLootRotationTest.java` — **SC-006a**: drei Mitglieder, neun Gegenstände, jeder genau drei. Zwanzig Kills mit vier Gegenständen verteilen die **vier** reihum, nicht die Kills
- [X] T061 [US2] Test `PartyLootSkipsOutOfRangeTest` in `rpg-core/src/test/java/rpg/core/item/PartyLootSkipsOutOfRangeTest.java` — ein Abwesender wird übersprungen und **behält seine Position**; ist niemand in Reichweite, fällt der Anspruch auf den größten Beitragenden zurück (FR-026c)
- [X] T062 [US2] `LootPlanner` und `LootClaim` in `rpg-core/src/main/java/rpg/core/item/` — der ganze Entscheidungsweg aus [data-model.md](./data-model.md) §2: was fällt, und wem es gehört. `LootClaim` trägt `entityId`, `ownerCharacterId` und `droppedAt`. **Bukkit-frei**, nach dem Muster von `CoinDropPlanner` (R4)
- [X] T063 [US2] Test `LootGoesToTheLargestContributorTest` in `rpg-core/src/test/java/rpg/core/item/LootGoesToTheLargestContributorTest.java` — **SC-006**: die Beute geht an den größten Beitragenden, auch wenn ein anderer den letzten Treffer landete (FR-026)
- [X] T064 [US2] Test `LootIsNotSplitLikeXpTest` in `rpg-core/src/test/java/rpg/core/item/LootIsNotSplitLikeXpTest.java` — **die dokumentierte Abweichung von ADR-029**: Erfahrung und Coins teilen sich nach Anteil, ein Gegenstand nicht. Der Test hält fest, dass das Absicht ist
- [X] T065 [US2] Test `NoLootWithoutAPlayerKillTest` in `rpg-core/src/test/java/rpg/core/item/NoLootWithoutAPlayerKillTest.java` — Tod durch Umgebung, andere Kreatur oder Aufräumen lässt nichts fallen; ein leerer `DamageShare` ergibt keinen Eigentümer (FR-019)
- [X] T066 [US2] Test `NoEquipmentInLootTablesTest` in `rpg-core/src/test/java/rpg/core/item/NoEquipmentInLootTablesTest.java` — eine Tabelle, die eine Ausrüstungskennung nennt, führt zu Fail-Fast (FR-024, ADR-017)
- [X] T067 [US2] `LootDropListener` in `rpg-platform/src/main/java/rpg/platform/item/LootDropListener.java` — abonniert `CombatDeathEvent` auf dem **Kern-Ereignisbus**, nicht Bukkits `EntityDeathEvent` (R4). Setzt jeden Plan über `OwnedDrops`, **ortsgebunden** (`runSyncAtLocation`, ADR-035)
- [X] T068 [US2] Test `LootDropListenerTest` in `rpg-platform/src/test/java/rpg/platform/item/LootDropListenerTest.java` — ein Tod erzeugt die geplanten Gegenstände, jeder mit dem richtigen Eigentümer. Dazu **FR-025**: gefallene Beute berührt B10s Mob-Budget nicht — sie taucht in `HordeRegistry` nicht auf
- [X] T069 [US2] Test `VanillaDropsStaySuppressedTest` in `rpg-platform/src/test/java/rpg/platform/item/VanillaDropsStaySuppressedTest.java` — **R3**: B05s `CombatDeathListener` räumt `getDrops()` und setzt `setDroppedExp(0)`. **FR-022** ist ohne Code erfüllt; dieser Test sichert die Zusage, damit sie nicht unbemerkt wegfällt, wenn jemand B05 anfasst
- [X] T070 [US2] `LootDroppedEvent` in `rpg-core/src/main/java/rpg/core/item/LootDroppedEvent.java` veröffentlichen — für B12 ([contracts/item-api.md](./contracts/item-api.md) §4)
- [X] T071 [US2] `OwnedDropRegistry.restoreVisibility` beim Charaktereintritt aufrufen — in der Sitzungsverdrahtung, wie B08b es für Coin-Haufen tut (FR-030)

**Checkpoint**: Beute fällt, gehört einem, und die Party teilt fair.

---

## Phase 6: User Story 3 — Verbrauchbares (P2)

**Goal**: Tränke wirken sofort, sind danach verbraucht, und ein zweiter direkt hinterher ist nicht
der Weg.

**Independent Test**: Trank per Befehl vergeben, trinken, Wirkung messen — ohne Beute, ohne NPC.

- [X] T072 [P] [US3] `ConsumableCooldown` in `rpg-core/src/main/java/rpg/core/item/ConsumableCooldown.java` — `(characterId, templateKey) → Zeitstempel`; **zwei Zeitstempel, keine laufende Aufgabe** (Prinzip II)
- [X] T073 [P] [US3] Test `ConsumableCooldownTest` in `rpg-core/src/test/java/rpg/core/item/ConsumableCooldownTest.java` — innerhalb der Abklingzeit abgelehnt, danach erlaubt; die Auswertung ist zeitstempelbasiert, nicht periodisch
- [X] T074 [US3] `ConsumableUse` in `rpg-core/src/main/java/rpg/core/item/ConsumableUse.java` — die Regel: Mindestlevel, Klassenbindung, Abklingzeit, Wirkungslosigkeit. **Jede Ablehnung nennt ihren Grund** (FR-035 bis FR-037)
- [X] T075 [US3] Test `ConsumableUseTest` in `rpg-core/src/test/java/rpg/core/item/ConsumableUseTest.java` — je ein Fall: zu niedriges Level, falsche Klasse, Abklingzeit, **volles Leben bei reinem Heiltrank**. In allen vier Fällen wird **nichts verbraucht** (FR-036)
- [X] T076 [US3] `ConsumableUseListener` in `rpg-platform/src/main/java/rpg/platform/item/ConsumableUseListener.java` — verbraucht genau ein Exemplar und wendet die Wirkung an (FR-033)
- [X] T077 [US3] Zeitliche Wirkung über `SourceKind.BUFF` an B04 übergeben — **B11 führt keine eigene Buff-Verwaltung und keine eigene Ablaufprüfung** (FR-034, R9)
- [X] T078 [US3] Test `ConsumableBuffUsesTheExistingSeamTest` in `rpg-platform/src/test/java/rpg/platform/item/ConsumableBuffUsesTheExistingSeamTest.java` — der Beitrag läuft über `SourceKind.BUFF` und läuft zeitstempelbasiert aus; **keine wiederkehrende Aufgabe entsteht** (SC-017)
- [X] T079 [P] [US3] Message-Schlüssel für die vier Ablehnungsgründe in `messages.yml`

**Checkpoint**: Verbrauchbares wirkt und ist begrenzt.

---

## Phase 7: User Story 4 — Der NPC (P2)

**Goal**: Sechs NPCs kaufen an, verkaufen, reparieren und führen den Stufenaufstieg durch.

**Independent Test**: Verkaufen und Aufsteigen gegen einen bekannten Kontostand, ohne dass Beute
existiert.

- [X] T080 [P] [US4] `VendorStock` (in Phase 2 entstanden). **`VendorPricing` entfällt:** die Preise stehen bereits in `VendorStock`, eine zweite Klasse dafür wäre ein Behälter um eine Abbildung. Ursprünglich `VendorStock` und `VendorPricing` in `rpg-core/src/main/java/rpg/core/item/` — je Region ein Bestand; der **Ankaufserlös** steht an der Vorlage, nicht hier (FR-058, FR-016)
- [X] T081 [P] [US4] ~~Test `VendorStockDiffersPerZoneTest`~~ — **abgedeckt durch `ShippedItemConfigTest.everyRegionHasAVendorAndTheyDiffer`**, und dort schärfer: es prüft die AUSGELIEFERTEN sechs Bestände, nicht zwei konstruierte. Ursprünglich `VendorStockDiffersPerZoneTest` in `rpg-core/src/test/java/rpg/core/item/VendorStockDiffersPerZoneTest.java` — zwei Regionen führen unterschiedliche Bestände
- [X] T082 [US4] `VendorTransaction` in `rpg-core/src/main/java/rpg/core/item/VendorTransaction.java` — **die Reihenfolge ist die Anforderung**: jede Bedingung ist geprüft, bevor eine einzige Coin sich bewegt (FR-064). Nach dem Muster von `EquipmentPurchase`
- [X] T083 [US4] Test `NothingIsBookedBeforeEverythingIsCheckedTest` in `rpg-core/src/test/java/rpg/core/item/NothingIsBookedBeforeEverythingIsCheckedTest.java` — **SC-009**: zu wenig Coins, volles Inventar, unverkäufliches Item — in jedem Fall bleibt der Kontostand unverändert
- [X] T084 [US4] `VendorNpc` in `rpg-platform/src/main/java/rpg/platform/item/VendorNpc.java` — sechs NPCs in den Safe-Cores, unverwundbar, über B10s Platzierungstechnik gesetzt (FR-057, FR-059)
- [X] T085 [US4] Test `VendorDoesNotCountAgainstTheMobBudgetTest` in `rpg-platform/src/test/java/rpg/platform/item/VendorDoesNotCountAgainstTheMobBudgetTest.java` — **R7**: der NPC wird nie in `HordeRegistry` eingetragen und zählt damit nicht. Der Test hält fest, dass das kein Zufall ist
- [X] T086 [US4] `VendorMenu` in `rpg-platform/src/main/java/rpg/platform/item/VendorMenu.java` — ein Fenster je Spieler (FR-066), aus Vanilla-Materialien nach dem Muster von `ClassSelectionMenu`; Ausgabe hinter der B13-Naht (ADR-005)
- [X] T087 [US4] Verkauf verdrahten — Erlös unter `BookingReason.VENDOR_SALE` (CREDIT), Kauf unter `VENDOR_PURCHASE` (DEBIT). **Die Spec hatte beide vertauscht:** aus Sicht des Spielers ist Verkaufen eine Gutschrift, und `VENDOR_PURCHASE` ist der Kauf. Beim Nachsehen in `BookingReason` aufgefallen (FR-060)
- [X] T088 [US4] Stufenaufstieg verdrahten — über die **vorhandene** Route `EquipmentPurchase` aus B08b; **kein zweiter Kaufmechanismus** (FR-061)
- [X] T089 [US4] Test `TierPurchaseUsesTheExistingRouteTest` in `rpg-platform/src/test/java/rpg/platform/item/TierPurchaseUsesTheExistingRouteTest.java` — der Aufstieg geht durch `EquipmentPurchase`; ein abgelehnter Aufstieg (Level, Höchststufe, Coins) bewegt **keine** Coins (FR-062)
- [X] T090 [US4] Test `BoundEquipmentIsNotSellableTest` in `rpg-platform/src/test/java/rpg/platform/item/BoundEquipmentIsNotSellableTest.java` — Klassenrüstung und -waffe werden abgewiesen; geprüft über `BoundEquipment.isBoundTo`, **nicht über eine eigene Prüfung** (FR-063, FR-079)
- [X] T091 [US4] Test `VendorWindowClosesCleanlyTest` in `rpg-platform/src/test/java/rpg/platform/item/VendorWindowClosesCleanlyTest.java` — Logout, Zonenwechsel und Serverstopp hinterlassen **nichts halb Gebuchtes** (FR-065)
- [X] T092 [P] [US4] Message-Schlüssel für Fenstertitel, Preise und Ablehnungen in `messages.yml`

**Checkpoint**: Der Kreislauf schließt sich — Beute wird zu Coins, Coins zur nächsten Stufe.

---

## Phase 8: User Story 5 — Verschleiß (P2)

**Goal**: Ausrüstung wird schwächer statt kaputt, und der Tod kostet ein Vielfaches eines Kampfes.

**Independent Test**: Schaden nehmen, austeilen, sterben, Werte messen, reparieren.

**Setzt Gruppe A aus Phase 3 voraus.**

- [X] T093 [P] [US5] `WearCurve` in `rpg-core/src/main/java/rpg/core/item/WearCurve.java` — Schwelle, Restanteil, drei Raten, Warnschwellen, alle konfigurierbar (FR-045, FR-047); die Formel aus [data-model.md](./data-model.md) §1
- [X] T094 [US5] Test `WearCurveTest` in `rpg-core/src/test/java/rpg/core/item/WearCurveTest.java` — **über die ganze Spanne, nicht nur an den Enden** (FR-047, SC-011): Zustand 50 → 1,0 · 25 → 0,60 · 10 → 0,36 · 0 → 0,20. Und **stetig**: zwei verschiedene Zustände ergeben nie denselben Faktor (FR-048)
- [X] T095 [US5] Test `GearNeverBreaksTest` in `rpg-core/src/test/java/rpg/core/item/GearNeverBreaksTest.java` — **kein Maß an Verschleiß zerstört etwas**; bei Zustand 0 bleiben genau 20 % Beitrag (FR-038, SC-011)
- [X] T096 [US5] `WearRules` in `rpg-core/src/main/java/rpg/core/item/WearRules.java` — welcher Schaden welchen Slot trifft (FR-040, FR-041, FR-042), nach der Tabelle in [data-model.md](./data-model.md) §6
- [X] T097 [US5] Test `WearRulesTest` in `rpg-core/src/test/java/rpg/core/item/WearRulesTest.java` — **SC-012**: erlittener Schaden trifft nur die Rüstung (FR-040), `MELEE`/`PROJECTILE` nur die Waffe (FR-041), `ABILITY` keines von beidem, `ADMIN` gar nichts (FR-042)
- [X] T098 [US5] Test `WearIsMeasuredBeforeMitigationTest` in `rpg-core/src/test/java/rpg/core/item/WearIsMeasuredBeforeMitigationTest.java` — **FR-040a, der Test gegen die Abwärtsspirale**: zwei Spieler mit unterschiedlich guter Rüstung verschleißen beim selben Treffer **gleich stark**. Am durchgekommenen Schaden gemessen täten sie das nicht
- [X] T099 [US5] Test `ASummonWearsNothingTest` in `rpg-core/src/test/java/rpg/core/item/ASummonWearsNothingTest.java` — ein Klon nutzt weder Waffe noch Rüstung seines Beschwörers ab (FR-041a)
- [X] T100 [US5] Test `DeathCostsMoreThanAFightTest` in `rpg-core/src/test/java/rpg/core/item/DeathCostsMoreThanAFightTest.java` — **SC-013**: der Todesbetrag übersteigt den Verschleiß eines ganzen gewöhnlichen Kampfes um ein Vielfaches (FR-043)
- [X] T101 [P] [US5] `GearCondition` und `GearConditionRepository` in `rpg-core/src/main/java/rpg/core/item/` — zwei Werte je Charakter, versioniert ([data-model.md](./data-model.md) §3)
- [X] T102 [P] [US5] Migration `V11_2__character_gear_condition.sql` — Muster von `V7_1__character_class_progress.sql`, `ON DELETE CASCADE`
- [X] T103 [US5] `JdbcGearConditionRepository` in `rpg-persistence/src/main/java/rpg/persistence/jdbc/` — Write-Behind wie überall; **kein Datenbankzugriff je Spielereignis** (Prinzip II)
- [X] T104 [US5] Test `JdbcGearConditionRepositoryTest` gegen echtes PostgreSQL (Testcontainers, Prinzip VII) — Schreiben, Lesen, Migration, `ON DELETE CASCADE` beim Löschen eines Charakters
- [X] T105 [US5] `GearConditions` als öffentliche Fassade in `rpg-core/src/main/java/rpg/core/item/GearConditions.java` — `conditionOf` und `factorOf` nach [contracts/item-api.md](./contracts/item-api.md) §1
- [X] T106 [US5] `GearConditions` als `GearConditionFactor` in `RpgPlugin` an `ClassStatContributor` übergeben — **hier wird die Naht aus Gruppe A geschlossen**
- [X] T107 [US5] Test `WearReachesTheStatValueTest` in `rpg-platform/src/test/java/rpg/platform/item/WearReachesTheStatValueTest.java` — verschlissene Rüstung senkt den Rüstungsbeitrag messbar; **Grundwerte der Klasse, Levelwachstum, Buffs und Zonenwirkungen bleiben unberührt**, und der andere Slot auch (FR-049)
- [X] T108 [US5] `WearListener` in `rpg-platform/src/main/java/rpg/platform/item/WearListener.java` — hört auf `DamageDealtEvent` und `CombatDeathEvent`; `playerVictim` ist der Auslöser des Todesverschleißes (B05 hat den Haken bereits gesetzt)
- [X] T109 [US5] Test `NoItemOrXpLostOnDeathTest` in `rpg-platform/src/test/java/rpg/platform/item/NoItemOrXpLostOnDeathTest.java` — **SC-010**: Inventar und Erfahrungsstand vor und nach dem Tod identisch (FR-046, ADR-017)
- [X] T110 [US5] `GearConditionDisplay` in `rpg-platform/src/main/java/rpg/platform/item/GearConditionDisplay.java` — bildet den Zustand auf den Haltbarkeitsbalken ab, **abgeleitet bei jedem Laden** wie Name und Lore; das Item bleibt unzerstörbar (FR-050)
- [X] T111 [US5] `GearConditionChangedEvent` und die Warnschwellen verdrahten — Meldung **höchstens einmal je Schwelle und Ruhezeit**, nicht bei jedem Treffer (FR-051)
- [X] T112 [US5] `RepairPricing` in `rpg-core/src/main/java/rpg/core/item/RepairPricing.java` — Preis steigt mit dem fehlenden Anteil (FR-053)
- [X] T113 [US5] Reparatur je Slot am NPC verdrahten — gebucht unter `BookingReason.REPAIR` (FR-052)
- [X] T114 [US5] Test `RepairTest` in `rpg-platform/src/test/java/rpg/platform/item/RepairTest.java` — nur der bezahlte Slot wird wiederhergestellt (SC-012); eine abgelehnte Reparatur ändert **weder Zustand noch Kontostand** (FR-054); eine Reparatur ohne Verschleiß wird abgelehnt
- [X] T115 [US5] Test `TierAdvanceDoesNotRepairTest` in `rpg-core/src/test/java/rpg/core/item/TierAdvanceDoesNotRepairTest.java` — ein Aufstieg setzt den Zustand **nicht** zurück, sonst wäre er der billigere Weg zur Instandsetzung (FR-055)
- [X] T116 [US5] `RepairRouteLockListener` in `rpg-platform/src/main/java/rpg/platform/item/RepairRouteLockListener.java` — Amboss, Zauberpult und Schleifstein für gebundene Ausrüstung sperren (FR-056, R10)
- [X] T117 [US5] Test `AnvilRouteIsLockedTest` in `rpg-platform/src/test/java/rpg/platform/item/AnvilRouteIsLockedTest.java` — alle drei Wege einzeln nachgewiesen; die Coin-Route bleibt die einzige Instandsetzung

**Checkpoint**: Der Tod hat eine Strafe, und Coins haben eine Senke.

---

## Phase 9: User Story 6 — Kosmetik auf Stufe 60 (P3)

**Goal**: Nach der Höchststufe gibt es noch etwas zu holen — rein optisch.

**Independent Test**: Trimfarbe kaufen und anwenden, ohne dass Beute oder Verbrauchbares existieren.

- [X] T118 [P] [US6] `CosmeticUnlock` und `CosmeticRepository` in `rpg-core/src/main/java/rpg/core/item/` — Besitz und Anwendung je **Charakter** ([data-model.md](./data-model.md) §3)
- [X] T119 [P] [US6] Migration `V11_3__character_cosmetic.sql` — mit **partiellem `UNIQUE`-Index** auf `(character_id) WHERE applied`: FR-071 wird eine Datenbankregel statt einer Absichtserklärung
- [X] T120 [US6] `JdbcCosmeticRepository` und Test gegen echtes PostgreSQL — der Teilindex weist eine zweite angewandte Farbe **auf Datenbankebene** zurück
- [X] T121 [US6] `CosmeticApplication` in `rpg-core/src/main/java/rpg/core/item/CosmeticApplication.java` — **erst auf der Höchststufe anwendbar** (FR-069)
- [X] T122 [US6] Test `CosmeticOnlyAtTopTierTest` in `rpg-core/src/test/java/rpg/core/item/CosmeticOnlyAtTopTierTest.java` — unterhalb abgelehnt, **der Besitz bleibt trotzdem**; Javadoc und Test nennen den Grund: sonst sähen Schurkenstufe 4 und 6 gleich aus (B07s FR-016, ADR-039)
- [X] T123 [US6] Anwendung auf das Aussehen verdrahten — überschreibt den Trim der Stufe, **nur** auf der Höchststufe; benutzt `TierAppearance`, führt **keine zweite Fassung** ein (FR-079)
- [X] T124 [US6] Test `CosmeticChangesNoValueTest` in `rpg-platform/src/test/java/rpg/platform/item/CosmeticChangesNoValueTest.java` — **SC-014**: nach dem Anwenden ist **kein einziger Wert** anders
- [X] T125 [US6] Test `CosmeticSurvivesReloginTest` und `CosmeticIsPerCharacterTest` — die Farbe übersteht einen Neustart und gilt **nicht** für einen anderen Charakter (FR-072, ADR-011)
- [X] T126 [US6] Test `UnknownCosmeticFallsBackTest` in `rpg-core/src/test/java/rpg/core/item/UnknownCosmeticFallsBackTest.java` — eine Farbe, die die Konfiguration nicht mehr kennt, fällt auf das Aussehen der Stufe zurück, **ohne den Besitzvermerk zu verlieren** (FR-073)
- [X] T127 [US6] Kosmetik in den NPC-Bestand aufnehmen und `CosmeticAppliedEvent` veröffentlichen

**Checkpoint**: Stufe 60 ist nicht mehr das Ende.

---

## Phase 10: User Story 7 — Lagern, Warnen, Vernichten (P3)

**Goal**: Ein volles Inventar ist keine Sackgasse.

**Independent Test**: Inventar auffüllen und die drei Wege beobachten.

- [ ] T128 [US7] Enderchest-Route über B03s `CharacterInventory` öffnen — **keine zweite Lagerung** (FR-074, FR-079)
- [ ] T129 [US7] Test `EnderChestIsPerCharacterTest` in `rpg-platform/src/test/java/rpg/platform/item/EnderChestIsPerCharacterTest.java` — nach einem Charakterwechsel sieht der Spieler die Enderchest des **neuen** Charakters
- [ ] T130 [US7] `InventoryFullWarning` in `rpg-platform/src/main/java/rpg/platform/item/InventoryFullWarning.java` — Title plus Sound hinter der B13-Naht, mit Ruhezeit (FR-075 bis FR-077). Der vorhandene `InventoryFullNoticeListener` aus B07 wird **erweitert, nicht verdoppelt**
- [ ] T131 [US7] Test `InventoryFullWarningTest` in `rpg-platform/src/test/java/rpg/platform/item/InventoryFullWarningTest.java` — **SC-015**: zwanzig aufeinanderfolgende Aufsammelversuche, gewarnt wird mit Ruhezeit, **nichts wird still verworfen**
- [ ] T132 [US7] `TrashCommand` in `rpg-platform/src/main/java/rpg/platform/item/TrashCommand.java` — vernichtet den **gehaltenen** Gegenstand nach einer Bestätigung; ohne Bestätigung geschieht nichts (FR-078)
- [ ] T133 [US7] Test `TrashRefusesBoundEquipmentTest` in `rpg-platform/src/test/java/rpg/platform/item/TrashRefusesBoundEquipmentTest.java` — Mülleimer und Enderchest weisen Klassenausrüstung ab, geprüft über `BoundEquipment` (FR-063)
- [ ] T134 [P] [US7] Message-Schlüssel für Warnung, Bestätigung und Ablehnungen in `messages.yml`

**Checkpoint**: Alle sieben Geschichten stehen.

---

## Phase 11: Polish & Querschnitt

**Purpose**: die Zusagen, die keiner einzelnen Geschichte gehören

- [ ] T135 Test `NoSecondVersionTest` in `rpg-platform/src/test/java/rpg/platform/item/NoSecondVersionTest.java` — **FR-079, der wahrscheinlichste Verstoß dieses Blocks**: B11 führt keine zweite Fassung von `BoundEquipment`, `TierAppearance`, `CharacterInventory`, `EquipmentPurchase` oder der Eigentumsmechanik ein. Der Test sieht die Quellen durch, wie `NoRawTypeNameLeftTest` es für B10 tut
- [ ] T135a Test `NoPlayerToPlayerTradeTest` in `rpg-platform/src/test/java/rpg/platform/item/NoPlayerToPlayerTradeTest.java` — **FR-067**: der NPC eröffnet keinen Weg von Spieler zu Spieler, und es gibt keinen zweiten. Ein Nicht-Ziel aus `00-vision-scope.md`, das bisher nur dadurch gilt, dass niemand es gebaut hat — genau die Sorte Zusage, die still verfällt
- [ ] T136 Test `NoRecurringTaskTest` in `rpg-core/src/test/java/rpg/core/item/NoRecurringTaskTest.java` — **SC-017**: keine wiederkehrende Aufgabe je Spieler, je Item oder je liegendem Gegenstand. Verfall macht Vanilla, Buffs und Verschleiß werden bei Bedarf gerechnet
- [ ] T137 Messung `BoundPredicateBenchmarkTest` in `rpg-platform/src/test/java/rpg/platform/item/BoundPredicateBenchmarkTest.java` — **SC-016**: das Bindungsprädikat sitzt im Pfad jedes Inventarklicks und allokiert nichts. Wiederholbar und **ohne Volllast**, nach dem Muster von `HordeBudgetBenchmarkTest` (Prinzip VII, ADR-031)
- [ ] T138 Vollständige Verdrahtung in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` prüfen — alle Zuhörer registriert, alle Module gestartet, Reihenfolge stimmt
- [ ] T139 `FullBootstrapTest` erweitern — **ein Modul, dessen Modultests grün sind, ist nicht fertig**: Handler-Zählungen für jeden neuen Zuhörer, wie B10 es für `DaylightBurnSuppressor` getan hat
- [ ] T140 Vollen Testlauf fahren: `./gradlew test` — **grün und null Übersprungene**. MockBukkit meldet Nicht-Implementiertes als *übersprungen* statt als Fehler; ein grüner Lauf, der nichts angesehen hat, ist der schlechteste Ausgang
- [ ] T141 [P] `01-architecture.md` und den Blocksteckbrief `B11-items-loot-equipment.md` nachziehen — **der Steckbrief sagt bis heute „Durability und Reparatur" auf gebundener Rüstung**, was dem Code widerspricht (ADR-039). Auch „Aufstiegsmaterial" und die Kategorie „Material" gehören gestrichen
- [ ] T142 [P] `06-open-questions.md` nachziehen — der Beuteinhalt je Region ist ab hier eine Balancing-Frage für B16, keine offene Blockfrage
- [ ] T143 [quickstart.md](./quickstart.md) **Abschnitt 1 und 2** durchlaufen — ohne Server
- [ ] T144 [quickstart.md](./quickstart.md) **Abschnitt 3** auf einem echten Paper-Server — die **57 Prüfschritte**, besonders 5 (Balancing erreicht vorhandene Exemplare), 13–17 (fremde Beute ist unsichtbar und gesperrt), **15 (Relogin — die Falle, an der `showEntity` hängt)**, 20–25 (die Party teilt reihum) und 28–29 (Fähigkeit und Klon schonen die Ausrüstung). **Grüne Tests beweisen nichts über Papers Klassenlader, nichts über das PDC im echten Serialisierungsweg und nichts über `showEntity`.** B10 hat hier vier Fehler gefunden, die kein Test gesehen hatte (ADR-035 bis ADR-038)

---

## Dependencies & Execution Order

### Phasenabhängigkeiten

- **Phase 1 (Setup)**: keine Abhängigkeit
- **Phase 2 (Foundational)**: nach Setup — **blockiert alle Geschichten**
- **Phase 3 (Eingriffe)**: nach Foundational. Gruppe A blockiert US5, Gruppe B blockiert US2,
  Gruppe C ist **unabhängig** und kann jederzeit laufen
- **Phase 4–10 (Geschichten)**: nach Phase 2, mit den genannten Ausnahmen
- **Phase 11 (Polish)**: nach allen gewünschten Geschichten

### Abhängigkeiten zwischen den Geschichten

- **US1 (P1)**: keine — der MVP
- **US2 (P2)**: braucht **Gruppe B** (die Eigentumsmechanik)
- **US3 (P2)**: unabhängig; Items lassen sich per Befehl vergeben
- **US4 (P2)**: unabhängig; braucht US2 **nicht**, weil Items per Befehl vergeben werden können
- **US5 (P2)**: braucht **Gruppe A** (die Naht in B07) und **US4** für die Reparaturroute
- **US6 (P3)**: braucht US4 für den Verkauf
- **US7 (P3)**: braucht US4 für den Verkaufsweg

### Parallelität

- T001–T004 vollständig parallel
- T005–T008 parallel (verschiedene Dateien)
- **Die drei Gruppen der Phase 3 sind untereinander parallel** — sie berühren verschiedene Blöcke
- T040–T043 parallel
- US3, US4 und Gruppe C laufen parallel zu US2

---

## Implementation Strategy

### MVP zuerst

1. Phase 1 + 2 → das Item ist ein Datenobjekt
2. Phase 4 (US1) → **anhalten und prüfen**: eine Vorlage ändern, neu laden, das vorhandene Exemplar
   wirkt anders. Damit ist die Zusage aus ADR-004 eingelöst, und sie ist die einzige, die
   nachträglich nicht mehr einzubauen wäre

### Schrittweise Auslieferung

1. Setup + Foundational → Fundament
2. US1 → prüfen → **MVP**
3. Gruppe C (Rückbau) → jederzeit, unabhängig
4. Gruppe B + US2 → prüfen → Beute fällt und gehört einem
5. US3 und US4 (parallel) → prüfen → der Kreislauf schließt sich
6. Gruppe A + US5 → prüfen → der Tod hat eine Strafe
7. US6, US7 → je einzeln prüfen

---

## Notes

- `[P]` heißt: andere Datei, keine offene Abhängigkeit
- Nach jeder Aufgabe oder Gruppe committen
- **Ein Modul, dessen Modultests grün sind, ist nicht fertig.** Fertig ist es, wenn es im Plugin
  verdrahtet ist und `FullBootstrapTest` grün bleibt (T139)
- **Übersprungene Tests prüfen.** MockBukkit meldet Nicht-Implementiertes als *übersprungen* und
  nicht als Fehler (T140)
- **Die drei Eingriffe haben eine harte Abnahmebedingung**: der bestehende Testbestand der
  berührten Blöcke bleibt unverändert grün (T035, T045, T050). Wo eine Zusicherung umgedreht werden
  muss, wird sie **umgedreht statt gelöscht**
- **Und T144 nicht wegdrücken.** Vier Fehler dieses Projekts sind erst auf dem echten Server
  aufgefallen, alle bei vollständig grünen Tests. Abschnitt 3 des Quickstarts existiert genau dafür
- **Der Lasttest gehört zu B15** (ADR-031) und hält diesen Block nicht offen
