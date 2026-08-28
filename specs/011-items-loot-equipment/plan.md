# Implementation Plan: B11 · Items, Ausrüstung & Loot

**Branch**: `011-items-loot-equipment` | **Date**: 2026-08-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/011-items-loot-equipment/spec.md`

## Summary

B11 baut das Item als Datenobjekt, zwei Kategorien außerhalb der Klassenleiter, die Beute, aus der
sie stammen, sechs NPCs und den Verschleiß, der dem Tod seine Strafe gibt.

**Der Kern des Entwurfs ist, dass an drei Stellen etwas weggenommen statt hinzugefügt wird.**

1. **`item_instance` wird zurückgebaut** (research.md R2). Die Tabelle steht seit B02, B03 hat sie
   auf den Charakter umgehängt, `SessionBundle` lädt sie bei jedem Sitzungsstart — und **niemand
   liest sie**. Sie als Item-Speicher zu benutzen hieße, sie mit B03s Inventar-Blob synchron zu
   halten: zwei Wahrheiten über denselben Gegenstand. Ihre Spalte `rolled_values` ist seit ADR-027
   ohnehin gegenstandslos. B11 entfernt sie, statt sie zu füllen.
2. **Die Eigentumsmechanik wird aus B08b herausgezogen, nicht nachgebaut** (R5). `CoinPile` löst
   „nur für den Eigentümer sichtbar und aufsammelbar" bereits samt sechs Fallen, von denen fünf erst
   im Betrieb auffallen. Sie wandert nach `rpg.platform.drop` und wird von beiden Blöcken benutzt —
   dasselbe Vorgehen, mit dem ADR-029 den `ShareCalculator` aus `XpDistributor` gezogen hat.
3. **Vanillas Beute ist schon unterdrückt** (R3). `CombatDeathListener` räumt `getDrops()` und setzt
   `setDroppedExp(0)`. FR-027 ist ohne Code erfüllt und wird nur noch mit einem Test gesichert.

**Der eine Punkt, an dem dieser Plan einen fremden Block anfasst**, ist der Verschleiß (R1). Die
Stufenwerte sind **Grundwerte, keine Modifikatoren** — B07 hat das ausdrücklich so gebaut, weil
B04s Modifikatorband sonst einen Wert von 1385 auf ein Band um 40 klammern würde. Ein negativer
`EQUIPMENT`-Modifikator wäre genau daran gescheitert, still. Der Verschleiß muss deshalb den
**Grundbeitrag skalieren**, und dafür bekommt `ClassStatContributor` eine additive Naht, die ohne
B11 konstant `1.0` liefert. Das ist das Hausmuster: ADR-027 hat B08 auf demselben Weg eine
Preisprüfung gegeben, ohne die Abhängigkeitsrichtung zu drehen.

## Technical Context

**Language/Version**: Java 25 (ADR-001)

**Primary Dependencies**: Paper 26.2 API (nur in `rpg-platform` und `rpg-plugin`), `rpg-core` ohne
Bukkit. Benutzt aus dem Bestand: `BoundEquipment` und `TierAppearance` (B07),
`CombatDeathEvent.lootRecipient()` und `DamageOrigin` (B05), `PartyRegistry` (B06), `Currency` und
`BookingReason` (B08b), `CharacterInventory` (B03), `SourceKind.BUFF` (B04/B08).

**Storage**: PostgreSQL über B02. **Zwei neue Tabellen** (`character_gear_condition`,
`character_cosmetic`), beide am Charakter (ADR-011), beide nach dem Muster von
`V7_1__character_class_progress.sql`. **Eine rückbauende Migration** entfernt `item_instance`. Das
Item selbst wird **nicht** in der Datenbank geführt: es lebt im PDC innerhalb von B03s
Inventar-Blob (FR-004).

**Testing**: JUnit ohne Server für Vorlagen, Beutetabellen, Verschleißkurve, Beuteanspruch und
Reihum-Verteilung; MockBukkit für die Zuhörer und das PDC; Testcontainers gegen echtes PostgreSQL
für die beiden neuen Aggregate und die Rückbau-Migration (Prinzip VII). Eine wiederholbare Messung
für das Bindungsprädikat im Inventarklick-Pfad.

**Target Platform**: Paper-Server, eine Instanz (ADR-002)

**Project Type**: Gradle-Mehrmodulprojekt, `plugin → platform → core`

**Performance Goals**: Das Bindungsprädikat sitzt im Pfad **jedes** Inventarklicks und allokiert
nichts (SC-016) — `BoundEquipment.isBoundTo` vergleicht bereits eine vorbereitete Zeichenkette. Der
Verschleiß wird bei Bedarf gerechnet, nie periodisch (SC-017). Der verbindliche Zielwert des
Projekts (150 Spieler, p95 MSPT < 40 ms) bleibt und wird in B15 nachgewiesen (ADR-031).

**Constraints**: Paper-API nur im Tick; keine wiederkehrende Aufgabe je Spieler, je Item oder je
liegendem Gegenstand; kein Datenbankzugriff je Spielereignis; das Item speichert die Vorlagen-ID und
sonst nichts.

**Scale/Scope**: Zwei Kategorien, acht Raritätsstufen, sechs Regionen mit je einer Beutetabelle und
einem NPC-Bestand, 48 Mob-Arten und 6 Bosse als mögliche Anker, zwei Zustandswerte je Charakter.

**Keine offenen Punkte.** Elf Fragen sind in drei Runden mit dem Auftraggeber geklärt (ADR-039),
zehn weitere sind beim Nachsehen im Code entstanden und in [research.md](./research.md) beantwortet.

## Constitution Check

*GATE: vor Phase 0 bestanden, nach Phase 1 erneut geprüft. Constitution 1.1.1.*

| Prinzip | Wie dieser Plan es einhält |
|---|---|
| **I · Nebenläufigkeit** | Kein Bukkit-Aufruf außerhalb des Ticks. Das Setzen gefallener Gegenstände läuft **ortsgebunden** (`runSyncAtLocation`) — ADR-035 hat gezeigt, dass `runSyncOnEntity` aus Async-Kontext still scheitert. Datenbankzugriffe ausschließlich asynchron über B02s Write-Behind. Kein globaler veränderlicher Zustand: der Reihum-Zeiger hängt an der Party, der Zustand am Charakter. |
| **II · Performance** | Keine wiederkehrende Aufgabe je Spieler, je Item oder je liegendem Gegenstand. Liegende Beute räumt **Vanillas Verfall** (R5), nicht ein eigener Sweep. Der Verschleißfaktor wird bei der Neuberechnung gelesen, die ereignisgesteuert läuft — nicht periodisch. `EquipmentLadder.contributeTo` bekommt den Faktor als `double`, nicht als Lambda, damit im Neuberechnungspfad nichts alloziert wird (R1). Das Bindungsprädikat vergleicht eine vorbereitete Zeichenkette. |
| **III · Architektur** | `rpg-core` ohne Bukkit: Vorlagen, Beutetabellen, `LootPlanner`, Verschleißkurve, Reihum-Verteilung, Preise. `rpg-platform` setzt Gegenstände, schreibt das PDC und führt die NPCs. Die Eigentumsmechanik wird nach `rpg.platform.drop` gehoben und **geteilt** statt kopiert (R5). Rendering bleibt hinter der B13-Naht (ADR-005). |
| **IV · Datenhaltung** | **Der Kern dieses Blocks, und in 1.1.1 gerade nachgezogen.** Ein Item speichert die Vorlagen-ID und sonst nichts — keine Endwerte, kein gerendertes Lore. Zwei neue Aggregate, beide versioniert mit Migrationspfad. Der Rückbau von `item_instance` ist selbst eine versionierte Migration (R2). Cache autoritativ während der Sitzung. |
| **V · Datengetriebenes Design** | Vorlagen, Beutetabellen, NPC-Bestände, Preise, Verschleißraten und -schwellen in `items.yml`, beim Start gegen ein Schema geprüft, Fail-Fast mit Datei/Schlüssel/Grund. **Auch die Ordnung „ein Tod wiegt schwerer als viele Treffer" ist eine Startprüfung** (FR-044), keine Zahlenwahl. Kein Bezeichner einer einzelnen Vorlage im Code, erzwungen wie `ConfigOnlyMobTest` es für Mob-Arten tut. Alle Texte über Message-Schlüssel. |
| **VI · Korrektheit & Sicherheit** | Der Server ist Autorität. Manipuliertes PDC wird abgelehnt (FR-008). **Unsichtbarkeit ist niemals das einzige Schloss** — das Aufsammelverbot bleibt unabhängig davon bestehen (FR-029, R5). Eine Ausnahme im Beute- oder Verschleißpfad wird lokal gefangen; ein Item mit unbekannter Vorlage bleibt inert, statt zu verschwinden oder den Start abzubrechen (FR-007). Kein Reflection, kein NMS. |
| **VII · Tests** | Jede Regel serverlos: Verschleißkurve, Beuteauswahl, Reihum-Verteilung, Preisbildung, Schemaprüfung. Persistenz gegen echtes PostgreSQL über Testcontainers, **einschließlich der Rückbau-Migration**. Der Lasttest bleibt B15 (ADR-031) und hält diesen Block nicht offen; die Messung ohne Volllast erfüllt Prinzip VII. |
| **VIII · Sprache** | Dokumentation deutsch, Code und Config-Schlüssel englisch, Spielertexte über Message-Schlüssel. |

### Ein Eingriff, der begründet gehört

`ClassStatContributor` und `EquipmentLadder.contributeTo` in **B07** bekommen einen zusätzlichen
Parameter. Das ist kein Verstoß gegen FR-080, sondern dessen Erfüllung — aber es ist eine Änderung
an einem fertigen Block und wird deshalb hier benannt statt beiläufig gemacht. Siehe
[Complexity Tracking](#complexity-tracking).

## Project Structure

### Documentation (this feature)

```text
specs/011-items-loot-equipment/
├── plan.md              # Diese Datei
├── research.md          # Phase 0 — zehn Funde im gebauten Code
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── item-api.md      # Was B11 nach außen anbietet
│   └── item-config.md   # Das Schema von items.yml
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks — nicht von /speckit-plan erzeugt
```

### Source Code (repository root)

```text
rpg-core/src/main/java/rpg/core/item/          # neu — bukkit-frei
├── ItemTemplate.java                          # Kennung, Kategorie, Material, Rarität, Wirkung, Preis
├── ItemCategory.java                          # CONSUMABLE, COSMETIC
├── Rarity.java                                # acht Stufen, reines Etikett
├── ItemConfig.java / ItemConfigSchema.java    # Fail-Fast beim Start
├── ItemModule.java                            # Start und Nachladen, wie MobModule
├── LootTable.java / LootEntry.java            # Wahrscheinlichkeit + Stückzahlspanne
├── LootPlanner.java                           # was fällt und wem es gehört
├── LootClaim.java                             # Anspruch je Charakter
├── PartyLootRotation.java                     # der Reihum-Zeiger (R6)
├── GearCondition.java                         # zwei Werte je Charakter
├── GearConditionRepository.java
├── WearCurve.java                             # Schwelle, Restanteil, drei Raten
├── WearRules.java                             # welcher Schaden welchen Slot trifft
├── RepairPricing.java
├── ConsumableEffect.java                      # Heilung, Mana, zeitlicher Beitrag
├── CosmeticUnlock.java / CosmeticRepository.java
├── VendorStock.java / VendorPricing.java
└── ItemMessageKeys.java

rpg-platform/src/main/java/rpg/platform/drop/  # neu — aus B08b herausgezogen (R5)
├── OwnedDrop.java                             # setzen, sichtbar machen, sperren
├── OwnedDropRegistry.java                     # Wiederherstellen nach Relogin
└── OwnedDropPickupListener.java               # das zweite Schloss

rpg-platform/src/main/java/rpg/platform/item/  # neu
├── ItemStackFactory.java                      # Vorlage → ItemStack, Lore abgeleitet
├── ItemTag.java                               # PDC: Vorlagen-ID + Schema-Version
├── ItemSchemaMigration.java
├── LootDropListener.java                      # hört auf CombatDeathEvent (R4)
├── ConsumableUseListener.java
├── WearListener.java                          # Schaden und Tod → Zustand
├── GearConditionDisplay.java                  # abgeleiteter Anzeigewert
├── VendorNpc.java / VendorMenu.java           # sechs NPCs, ein Fenster je Spieler
├── RepairRouteLockListener.java               # Amboss, Zauberpult, Schleifstein (R10)
├── TrashCommand.java
└── InventoryFullWarning.java

rpg-persistence/src/main/resources/db/migration/
├── V11_1__character_gear_condition.sql
├── V11_2__character_cosmetic.sql
└── V11_3__drop_item_instance.sql              # der Rückbau (R2)

rpg-plugin/src/main/resources/items.yml        # neu
```

**Structure Decision**: Dieselbe Dreiteilung wie in jedem Block seit B04. Neu ist nur
`rpg.platform.drop` — ein Paket, das **keinem** Block allein gehört, sondern von B08b und B11
benutzt wird; es liegt in der Plattformschicht, weil es durch und durch Bukkit ist (`showEntity`,
`setOwner`, `Item`).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| **B07 wird angefasst**: `ClassStatContributor` und `EquipmentLadder.contributeTo` bekommen einen Faktor-Parameter | FR-047 verlangt, dass der Verschleiß den Ausrüstungsbeitrag mindert. Die Stufenwerte sind **Grundwerte**, und nur dort lässt sich skalieren | Ein negativer `SourceKind.EQUIPMENT`-Modifikator wäre an B04s Modifikatorband **still abgeschnitten** worden — genau der Fehler, den B07 mit dem Grundwert-Ansatz vermieden hat (R1). Ein zweiter Contributor aus B11 müsste B07s Leiterlogik nachbauen. Die Naht ist additiv, hat einen neutralen Vorgabewert und dreht die Abhängigkeitsrichtung nicht — das Muster von ADR-027 für B08 |
| **B08b wird angefasst**: `CoinPile` gibt seine Eigentumsmechanik an `rpg.platform.drop` ab | FR-079 verbietet eine zweite Fassung. Die Mechanik enthält sechs Fallen, fünf davon nur im Betrieb sichtbar (R5) | Kopieren wäre der benannte Verstoß. Nach `rpg-core` ziehen geht nicht — die Mechanik ist Bukkit von oben bis unten. Das Herausziehen folgt ADR-029, wo `ShareCalculator` aus demselben Grund aus `XpDistributor` gehoben wurde |
| **B02/B03 werden angefasst**: `item_instance`, `ItemInstance`, `ItemInstanceRepository` und das Feld in `SessionBundle` fallen weg | Eine Tabelle, die geladen und nie geschrieben wird, ist eine Falle für den nächsten Block (R2) | Liegen lassen kostet je Sitzungsstart eine Abfrage für nichts und lädt den nächsten Block ein, eine zweite Wahrheit über Inventare anzulegen. Nur `rolled_values` zu streichen löst die halbe Frage. Der Rückbau kostet heute eine Migration — dasselbe Argument, mit dem `V3_2` seinerzeit begründet wurde |

**Alle drei Eingriffe sind additiv oder rückbauend, keiner ändert bestehendes Verhalten.** Die
vorhandenen Tests der berührten Blöcke müssen unverändert grün bleiben; wo eine Zusicherung
umgedreht werden muss, wird sie **umgedreht statt gelöscht**, damit die Änderung im Diff sichtbar
bleibt — wie ADR-027 es für `AbilityRankTest` gehalten hat.
