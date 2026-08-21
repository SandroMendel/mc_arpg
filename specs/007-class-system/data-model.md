# Phase 1 · Datenmodell — B07 Klassen-System

Zwei Arten von Daten, und die Trennung ist die wichtigste Aussage dieses Dokuments:

- **Klassendefinition** — unveränderlich, aus `classes.yml`, genau **einmal** je Server im Speicher.
  Drei Objekte für 200 Spieler.
- **Klassenstand** — veränderlich, je Charakter, persistent. Zwei Zahlen.

Alles andere ist daraus abgeleitet. Insbesondere sind die getragenen Gegenstände **keine** Daten,
sondern eine Darstellung des Klassenstands (FR-023).

---

## Unveränderliche Definitionen (aus der Konfiguration)

### `CharacterClassDefinition`

Der vollständige Inhalt einer Klasse.

| Feld | Typ | Herkunft in `classes.yml` |
|---|---|---|
| `id` | `CharacterClass` | Schlüssel des Blocks, muss auf das Enum aus B03 abbildbar sein |
| `displayNameKey` | `String` | `display-name-key` — Message-Schlüssel, **kein** Text (Prinzip V) |
| `menuMaterial` | `String` | `menu-material` — Vanilla-Material für die Auswahl |
| `baseStats` | `ClassBaseStats` | `base-stats.*`, alle acht Pflicht |
| `growth` | `ClassGrowth` | `growth.*`, alle acht Pflicht, Null erlaubt |
| `armorLadder` | `EquipmentLadder` | `armor-ladder`, Liste |
| `weaponLadder` | `EquipmentLadder` | `weapon-ladder`, Liste |
| `abilities` | `List<AbilityBinding>` | `abilities`, leer oder genau sechs |

Warum `displayNameKey` und nicht `displayName`: Prinzip V verbietet hartcodierte Spielertexte, und
ADR-005 will Mehrsprachigkeit strukturell vorbereiten. „Berserker" ist damit ein Wert in der
Nachrichtendatei, nicht in der Klassendatei — und der Enum-Wert bleibt `WARRIOR` (ADR-019).

### `ClassBaseStats` und `ClassGrowth`

Beide halten acht `double`-Werte, geschlüsselt über `Attribute` aus B04. Getrennte Typen statt eines
gemeinsamen, weil ihre Bedeutung verschieden ist: Basis ist ein Wert, Wachstum ist ein Wert **je
Level**. Ein gemeinsamer Typ hätte die Verwechslung erlaubt, die genau einmal passieren muss, um
sechzig Level lang falsch zu rechnen.

`ClassGrowth` ersetzt `LevelGrowth` aus B06 je Klasse — es ergänzt sie **nicht**. B06 hat diese
Ersetzbarkeit in FR-022 vorgesehen. Ein Ergänzen hätte die Summe verdoppelt.

### `EquipmentLadder`

| Feld | Typ | Regel |
|---|---|---|
| `slot` | `LadderSlot` | `ARMOR` oder `WEAPON` |
| `tiers` | `List<EquipmentTier>` | mindestens 2, Länge sonst frei (FR-013) |

Abgeleitete Zusagen, geprüft beim Binden:

- **Streng steigend** in jedem getragenen Attribut (FR-017)
- **Levelanforderungen streng steigend**, erste auf 1, letzte höchstens Maximallevel
- **Erscheinungsbild je Stufe unterscheidbar** von der Vorstufe (FR-016)
- **Färbung nur auf färbbarem Material** (FR-016b)

Warum eine Liste und kein Feld je Stufe: dieselbe Begründung, mit der B06 seine XP-Kurve als
Kartenfeld gebunden hat. Eine Liste kann Warrior 5/6, Rogue 6/6 und Mage 7/7 ausdrücken; fünf
Pflichtschlüssel könnten es nicht.

### `EquipmentTier`

| Feld | Typ | Regel |
|---|---|---|
| `index` | `int` | 1-basiert, entspricht der Position in der Liste |
| `values` | `Map<Attribute, Double>` | nur die vier Attribute des Slots, alle Pflicht |
| `appearance` | `TierAppearance` | siehe unten |
| `requiredLevel` | `int` | ≥ 1, ≤ Maximallevel aus B06 |
| `cost` | `Map<String, Object>` | **undurchsichtig**, wird von B07 nicht ausgelegt (FR-021) |

Der `cost`-Block wird beim Laden auf „ist eine Karte" geprüft und danach unangetastet weitergegeben.
B07 kennt keine Coins, keine Materialien und keine Preise — das ist B11 und B16 (Workflow-Regel 5).
Eine Prüfung des Inhalts hätte eine Kopplung erzeugt, die es noch nicht geben darf.

### `TierAppearance`

| Feld | Typ | Regel |
|---|---|---|
| `material` | `String` | Pflicht, Vanilla-Material |
| `color` | `Integer` (RGB) | optional; Pflicht, wenn das Material dem der Vorstufe gleicht und färbbar ist |
| `trimMaterial` | `String` | optional, mit `trimPattern` gemeinsam gesetzt oder gemeinsam leer |
| `trimPattern` | `String` | optional |
| `modelData` | `Integer` | optional, reserviert für ein späteres Resource Pack (ADR-005) |

Die Gleichheitsprüfung für FR-016 vergleicht das **Tripel** aus Material, Farbe und Trim. Zwei Stufen
dürfen sich in zwei von drei Merkmalen gleichen, aber nicht in allen drei.

### `AbilityBinding`

| Feld | Typ | Regel |
|---|---|---|
| `abilityId` | `String` | wird von B07 **nicht** aufgelöst — nur benannt (FR-044) |
| `kind` | `ACTIVE` \| `PASSIVE` | |
| `unique` | `boolean` | höchstens eine je Klasse, muss `ACTIVE` sein |
| `unlockLevel` | `int` | ≥ 1, ≤ Maximallevel (FR-042) |

Genau vier `ACTIVE` und zwei `PASSIVE` je Klasse, die Unique zählt als eine der Aktiven (FR-041).
Eine leere Liste ist erlaubt, solange B08 fehlt; eine **teilweise** gefüllte nicht (FR-045) — sonst
wäre eine vergessene Zeile nicht von einer bewussten Auslassung zu unterscheiden.

---

## Veränderlicher Zustand (persistent)

### `ClassProgress` — das neue Aggregat

| Feld | Typ | Bedeutung |
|---|---|---|
| `characterId` | `UUID` | Schlüssel, verweist auf `rpg.character` aus B03 |
| `armorTier` | `int` | erreichte Rüstungsstufe, 1-basiert |
| `weaponTier` | `int` | erreichte Waffenstufe, 1-basiert |

Das ist alles. Die Klasse selbst steht schon in `rpg.character` aus B03 und wird hier **nicht**
wiederholt — eine zweite Kopie hätte zwei Wahrheiten erzeugt.

Warum eine eigene Tabelle und nicht zwei Spalten in `rpg.character`: dasselbe Argument, mit dem B04
`character_stats` und B06 `character_progress` ausgelagert haben. Eine gemeinsame Zeile heißt ein
gemeinsamer Schreiber und ein gemeinsamer Revisionszähler, und die Blockgrenze aus Prinzip III stünde
nur noch auf dem Papier.

### Migration `V7_1__character_class_progress.sql`

Nach der Versionskonvention aus B03: `V{Block}_{Folge}`. Inhalt:

- Tabelle `rpg.character_class_progress` mit `character_id` als Primärschlüssel und Fremdschlüssel
  auf `rpg.character` mit `ON DELETE CASCADE`
- `armor_tier` und `weapon_tier` als `INTEGER NOT NULL DEFAULT 1`
- `revision BIGINT NOT NULL DEFAULT 0`, wie in jedem anderen Aggregat
- Prüfbedingung `armor_tier >= 1` und `weapon_tier >= 1`

**Ausdrücklich nicht Teil dieser Migration**: das Enum `CharacterClass` und die Prüfbedingung
`chk_character_class` aus `V3_1`. Beide bleiben unangetastet (ADR-019). Eine vierte Klasse ist ein
späteres Upgrade und bekommt dann eine eigene Migration.

**Keine obere Prüfbedingung auf die Stufe.** Die Leiterlänge steht in der Konfiguration, nicht im
Schema; eine Zahl in der Datenbank wäre bei jeder Leiteränderung falsch. Stattdessen prüft der Start,
dass keine gespeicherte Stufe über die konfigurierte Länge hinausgeht, und bricht sonst ab (FR-024) —
damit wird eine verkürzte Konfiguration ein Startfehler statt einer stillen Herabstufung.

### Die drei Registrierungen nach ADR-015

Ein neuer Aggregattyp braucht drei Eintragungen, nicht eine. ADR-015 hat das nach einem Fehler in B06
festgehalten, bei dem eine fehlende Eintragung sich als Datenbankfehler tarnte:

| Ort | Eintrag |
|---|---|
| `AggregateType` | `CHARACTER_CLASS_PROGRESS` |
| `FlushCycle.WRITE_ORDER` | **nach** `CHARACTER`, weil der Fremdschlüssel darauf zeigt |
| Repository-Verdrahtung | `JdbcClassProgressRepository` im Persistenzmodul |

`NoDatabaseAccessPerGameEventTest` prüft seit ADR-016 als Invariante, dass jeder `AggregateType` in
`WRITE_ORDER` steht und jedes Kind nach seinem Elternteil kommt. Ein Vergessen fällt damit sofort auf,
statt erst beim ersten Flush.

---

## Abgeleitetes, nicht Gespeichertes

### Sollzustand der Ausrüstung

Aus `(Klasse, armorTier, weaponTier)` folgt eindeutig, welche Gegenstände ein Charakter trägt. Die
Richtung ist **einseitig**: die Stufe erzeugt das Item, nie das Item die Stufe. Daraus folgen zwei
Eigenschaften, die sonst mühsam zu erzwingen wären:

- Ein fehlendes gebundenes Item heilt sich beim nächsten Laden selbst (FR-023).
- Es gibt keinen Weg, durch Manipulation eines Gegenstands eine Stufe zu gewinnen (Prinzip VI).

### Bindungsauskunft

Die Antwort auf „ist dieses Item Bestandteil des Charakters?" (FR-025). Technisch ein Schlüssel im
`PersistentDataContainer` mit Klassen-ID, Slot und **Charakter-ID** — siehe research.md R6. Die
Charakter-ID macht ein kopiertes Item für einen anderen Charakter wertlos.

Diese Auskunft liegt im Pfad **jedes** Inventarklicks. Sie muss ohne Allokation und ohne
Datenbankzugriff antworten (SC-010, Prinzip II).

### Freigeschaltete Fähigkeiten

Aus `(Klasse, Level)` abgeleitet, nie gespeichert (FR-043). Gespeichert wäre es ein zweiter Ort für
eine Information, die schon im Level steht.

---

## Speicherbedarf

| Was | Größe | Anzahl |
|---|---|---|
| `CharacterClassDefinition` | ~2 KB je Klasse | **3 gesamt**, nicht je Spieler |
| `ClassProgress` | 8 B + Kopf | 1 je aktiver Charakter |
| Bindungsschlüssel | im Gegenstand, kein eigener Speicher | — |

Bei 200 Spielern: drei Definitionsobjekte und 200 mal zwei `int`. Das ist der Grund, warum die
Definition unveränderlich sein muss — geteilt wird sie von allen, und nur Unveränderlichkeit macht das
ohne Sperren sicher (Prinzip I).
