# Phase 1 · Datenmodell — B11 · Items, Ausrüstung & Loot

**Spec:** [spec.md](./spec.md) · **Research:** [research.md](./research.md)

Drei Arten von Daten, streng getrennt: **Konfiguration** (was es gibt), **Laufzeit** (was gerade
liegt oder gilt) und **Persistenz** (was einen Neustart überlebt). Die Trennung ist die halbe
Zusage dieses Blocks — ein Item gehört in keine der drei Datenbanktabellen.

---

## 1 · Konfiguration (`items.yml`, bukkit-frei geladen)

### `ItemTemplate`

Die einzige Quelle für alles, was ein Gegenstand ist (FR-001 bis FR-003).

| Feld | Typ | Regel |
|---|---|---|
| `key` | String | eindeutig, `bereich.name`, wie Mob-Arten |
| `category` | `ItemCategory` | `CONSUMABLE` oder `COSMETIC` — mehr gibt es nicht (FR-017) |
| `material` | String | Vanilla-Material; unterscheidet Gegenstände, solange kein Resource Pack existiert (FR-009) |
| `rarity` | `Rarity` | eine der acht; **reines Etikett, ohne Wertwirkung** (FR-014) |
| `minLevel` | int, optional | Benutzung darunter wird abgelehnt (FR-015) |
| `boundClass` | `CharacterClass`, optional | dito |
| `sellPrice` | long, optional | fehlt sie, ist der Gegenstand unverkäuflich (FR-016) |
| `modelData` | Integer, optional | reserviert für ein späteres Resource Pack (FR-006, ADR-005) |
| `effect` | `ConsumableEffect`, nur bei `CONSUMABLE` | siehe unten |
| `appearance` | `CosmeticAppearance`, nur bei `COSMETIC` | Trim-Material und -Muster als **benanntes Paar** |

**Validierung beim Start** (FR-012, Fail-Fast mit Datei, Schlüssel und Grund): Material existiert;
Rarität ist eine der acht; `effect` genau dann, wenn `CONSUMABLE`; `appearance` genau dann, wenn
`COSMETIC`; `sellPrice` nicht negativ; `minLevel` in `[1, 60]`.

### `ConsumableEffect`

Drei Wirkungsarten, mehr braucht das Schema nicht (Annahme der Spec):

| Feld | Typ | Regel |
|---|---|---|
| `heal` | double, optional | sofortige Lebensenergie, gedeckelt aufs Maximum (FR-033) |
| `mana` | double, optional | sofortige Mana-Wiederherstellung |
| `buff` | `Map<Attribute, Double>` + `durationMs`, optional | über `SourceKind.BUFF`, **zeitstempelbasiert** (FR-034) |
| `cooldownMs` | long, optional | Benutzung innerhalb wird abgelehnt, ohne zu verbrauchen (FR-035) |

Mindestens eine der drei Wirkungen muss gesetzt sein — ein Verbrauchbares ohne Wirkung wäre eine
Vorlage, die FR-036 bei jeder Benutzung ablehnt.

### `LootTable` und `LootEntry`

| Ebene | Schlüssel | Regel |
|---|---|---|
| je Mob-Art | `mob-kind` → Tabelle | gewinnt, wenn vorhanden (FR-018) |
| je Region | `zone` → Tabelle | greift, wenn die Art keine eigene hat |
| je Boss | `boss` → Tabelle | **getrennt** von der Region (FR-023) |

`LootEntry`: `templateKey`, `chance` in `(0, 1]`, `minCount`/`maxCount` ≥ 1.

**Die Stückzahl ist der einzige Zufall in diesem Block** (FR-021). Die Werte des gefallenen
Gegenstands sind fest, weil sie aus der Vorlage kommen.

**Startprüfung:** jede `templateKey` existiert; **keine Tabelle nennt Ausrüstung** — es gibt keine
Ausrüstungsvorlagen, also ist jede solche Kennung unbekannt und führt zu Fail-Fast (FR-024).

### `VendorStock`

Je Region eine Liste aus `templateKey` und `buyPrice` (FR-051). Der **Ankaufserlös** steht an der
Vorlage, nicht hier — sonst gäbe es sechs Orte für denselben Betrag.

### `WearCurve`

| Feld | Vorgabe | Bedeutung |
|---|---|---|
| `threshold` | 50 | oberhalb: voller Beitrag |
| `floor` | 0.20 | Restanteil bei Zustand 0 (FR-047) |
| `perDamagePoint` | 0.01 | Zustandspunkte je Schadenspunkt (FR-040, FR-041) |
| `perDeath` | 10.0 | Zustandspunkte je Tod (FR-042) |
| `deathFactorMin` | 100 | **Startprüfung**: `perDeath ≥ perDamagePoint × deathFactorMin` (FR-044) |
| `warnAt` | `[50, 25, 10]` | Warnschwellen (FR-051) |
| `warnCooldownMs` | — | Ruhezeit je Schwelle |

**Der Faktor auf den Beitrag**, stetig und ohne Sprung (FR-048):

```
condition ≥ threshold          → 1.0
condition <  threshold         → floor + (1 - floor) × (condition / threshold)
```

Bei den Vorgaben: Zustand 50 → 1,0 · Zustand 25 → 0,60 · Zustand 10 → 0,36 · Zustand 0 → 0,20.
Das erfüllt die Ansage „bei 0/100 volle 80 %, bei 10/100 schon schwächer".

### `RepairPricing`

`basePerTier` × fehlender Anteil (FR-053). Eine Reparatur bei Zustand 99 kostet ein Hundertstel
einer bei Zustand 0.

---

## 2 · Laufzeit (nichts davon überlebt einen Neustart)

### `LootClaim`

Wem ein gefallener Gegenstand gehört (FR-026 bis FR-032).

| Feld | Bedeutung |
|---|---|
| `entityId` | der liegende Gegenstand |
| `ownerCharacterId` | **Charakter**, nicht Spieler (ADR-011, FR-027) |
| `droppedAt` | für Auswertung; das Aufräumen macht Vanillas Verfall |

**Lebensdauer:** entsteht beim Fallen, endet mit Aufheben, Verfall oder Serverstopp. Kein Sweep
(FR-032) — Vanilla räumt.

### `PartyLootRotation`

`partyId → Index`. Zeigt auf das Mitglied, das den **nächsten** Gegenstand bekommt.

**Vorrücken je gefallenem Gegenstand, nicht je Kill** (FR-026b). Mitglieder außer Reichweite werden
übersprungen, ohne ihre Position zu verlieren (FR-026c). Aufgeräumt über `PartyChangedEvent`; die
Party wird laut B06 ohnehin nicht persistiert (FR-026d).

### Wer bekommt einen Gegenstand — der ganze Entscheidungsweg

```
CombatDeathEvent
  └─ lootRecipient()  (größter Beitragender, B05 — nicht der letzte Treffer)
       ├─ in keiner Party  → er selbst                                  FR-026
       └─ in einer Party   → Party gilt als EIN Beitragender            FR-026a
                              └─ Mitglieder in Reichweite zum Gegner    FR-026c
                                   ├─ mindestens eines → reihum, je Gegenstand   FR-026b
                                   └─ keines           → zurück auf lootRecipient FR-026c
```

### `ConsumableCooldown`

`(characterId, templateKey) → letzterZeitpunkt`. **Zwei Zeitstempel, keine laufende Aufgabe**
(Prinzip II).

---

## 3 · Persistenz (zwei neue Aggregate, eines fällt weg)

### `character_gear_condition` — neu (`V11_2`)

| Spalte | Typ | |
|---|---|---|
| `character_id` | UUID | PK, FK auf `character`, `ON DELETE CASCADE` |
| `armor_condition` | NUMERIC | `[0, 100]`, Vorgabe 100 |
| `weapon_condition` | NUMERIC | `[0, 100]`, Vorgabe 100 |
| `data_version` | INT | Migrationspfad (Prinzip IV) |
| `revision` | BIGINT | Write-Behind, wie überall |
| `updated_at` | TIMESTAMPTZ | |

Zwei Werte, getrennt nach `LadderSlot.ARMOR` und `WEAPON` (FR-039). `ON DELETE CASCADE`: ein
gelöschter Charakter nimmt seinen Zustand mit.

### `character_cosmetic` — neu (`V11_3`)

| Spalte | Typ | |
|---|---|---|
| `character_id` | UUID | FK, `ON DELETE CASCADE` |
| `template_key` | TEXT | die gekaufte Trimfarbe |
| `applied` | BOOLEAN | höchstens **eine** je Charakter (FR-064) — Teilindex erzwingt es |
| `acquired_at` | TIMESTAMPTZ | |

Primärschlüssel `(character_id, template_key)`. Ein partieller `UNIQUE`-Index auf
`(character_id) WHERE applied` macht FR-064 zu einer Datenbankregel statt zu einer Absichtserklärung.

**Eine gekaufte Farbe, die die Konfiguration nicht mehr kennt**, bleibt als Zeile stehen; das
Aussehen fällt auf die Stufe zurück (FR-066). Deshalb keine Fremdschlüsselprüfung gegen die
Konfiguration.

### `item_instance` — **fällt weg** (`V11_1`)

Rückbau nach research.md R2. Betroffen sind die Tabelle, `ItemInstance`,
`ItemInstanceRepository`, `JdbcItemInstanceRepository` und das Feld `items` in `SessionBundle`.

**Warum das kein Datenverlust ist:** die Tabelle wird von keinem Spielpfad geschrieben. Ihre einzige
Nutzung ist der Ladevorgang in `SessionBundle`, dessen Ergebnis niemand liest. Ein Bestand aus einer
früheren Testinstallation wäre ohnehin nicht mit den Inventar-Blobs aus B03 abgeglichen — die
Tabelle ist keine zweite Sicht auf dieselben Gegenstände, sondern eine leere.

**Die Migration prüft das nach und bricht ab, wenn Zeilen vorhanden sind**, statt sie stillschweigend
zu löschen — dasselbe Vorgehen, mit dem `V3_2` einen nicht zuordenbaren Gegenstand lieber die
Migration abbrechen lässt, als ihn zu verlieren. Ein Betreiber kann sich das ansehen; ein
klammheimlich geleerter Bestand fällt niemandem auf.

---

## 4 · Das Item selbst — im PDC, nicht in der Datenbank

**`ItemTag`** schreibt genau zwei Werte in den `PersistentDataContainer` (FR-001, FR-004):

| Schlüssel | Typ | |
|---|---|---|
| `rpg:item_template` | STRING | die Vorlagen-ID |
| `rpg:item_schema` | INTEGER | die Schema-Version |

**Alles andere wird abgeleitet** (FR-002): Anzeigename, Lore, Raritätsfarbe, Wirkungstext,
Verkaufserlös. Bei jedem Laden neu, aus der aktuellen Vorlage.

**Was daraus folgt und der eigentliche Zweck ist:** ändert der Betreiber eine Vorlage und lädt neu,
wirkt die Änderung auf jedes Exemplar in jedem Inventar, ohne dass ein Inventar angefasst wird
(FR-003, SC-001).

**Unbekannte Vorlage** (FR-007): das Item bleibt, trägt seinen rohen Namen und ist **inert** —
tragbar und vernichtbar, nicht benutzbar und nicht verkäuflich. Weder stilles Löschen noch
Startabbruch.

**Schema-Migration** (FR-005): steht eine ältere Version im PDC, hebt `ItemSchemaMigration` sie beim
Laden an. Version 1 ist die erste; der Pfad existiert ab Tag eins, damit er nicht nachträglich
erfunden werden muss.

**Der Zustand steht nicht im Item.** Klassenausrüstung bleibt `setUnbreakable(true)` (FR-038); der
angezeigte Haltbarkeitsbalken wird aus `character_gear_condition` **abgeleitet**, wie Name und Lore
auch. Darstellung, nicht Wahrheit.

---

## 5 · Die Naht zu B07 (research.md R1)

```java
// in rpg-core/src/main/java/rpg/core/classes/  — B07s Paket, additiv
@FunctionalInterface
public interface GearConditionFactor {
    GearConditionFactor NONE = (characterId, slot) -> 1.0;
    double factorFor(UUID characterId, LadderSlot slot);
}
```

`ClassStatContributor` nimmt sie im Konstruktor entgegen und reicht den Wert an
`EquipmentLadder.contributeTo(tier, sink, factor)` weiter. **Ohne B11 ist der Faktor `NONE`**, und
B07 verhält sich exakt wie heute — die bestehenden Tests bleiben unverändert grün.

**Als `double`-Parameter, nicht als umhüllender Sink**, damit im Neuberechnungspfad nichts alloziert
wird (Prinzip II).

**Der Faktor gilt je Slot** (FR-049): verschlissene Rüstung mindert die Rüstungswerte, verschlissene
Waffe die Waffenwerte, und keiner den anderen. Grundwerte der Klasse, Levelwachstum, Buffs und
Zonenwirkungen bleiben unberührt — sie laufen nicht über `contributeTier`.

---

## 6 · Welcher Schaden welchen Slot trifft (`WearRules`)

| Ereignis | Rüstung | Waffe | Quelle |
|---|---|---|---|
| Schaden **erlitten**, jede Herkunft außer `ADMIN` | ja, nach **ankommendem** Schaden vor Abwehr | nein | FR-040, FR-040a |
| Schaden **ausgeteilt**, `MELEE` oder `PROJECTILE` | nein | ja | FR-041 |
| Schaden ausgeteilt, `ABILITY` | nein | nein | FR-041 |
| Schaden durch einen **Klon** (B08 `SummonEffect`), ausgeteilt oder erlitten | nein | nein | FR-041a |
| **Tod**, außer `DeathCause.ADMIN` | ja | ja | FR-042 |

**Vor Abwehr gemessen ist die Anforderung, nicht eine Feinheit** (FR-040a): am durchgekommenen
Schaden entstünde eine Abwärtsspirale — verschlissene Rüstung lässt mehr durch, das nutzt sie
schneller ab —, und gute Rüstung wäre doppelt belohnt.
