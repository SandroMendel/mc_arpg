# Phase 1 — Datenmodell: B04 · Attribut- & Stat-Engine

**Feature**: `specs/004-stat-engine` | **Datum**: 2026-08-20

Alle Typen liegen in `rpg.core.stats` und sind bukkitfrei. Bezeichner auf Englisch (Prinzip VIII).

---

## 1 · Attribute

### `Attribute` (Aufzählungstyp, geschlossen — FR-001, FR-004)

| Konstante | Art | Bedeutung |
|---|---|---|
| `HEALTH` | absolut | maximales Leben |
| `DEFENSE` | absolut | Verteidigung, geht in die Minderungsformel ein |
| `MANA` | absolut | maximales Mana |
| `PHYSICAL_DAMAGE` | absolut | Basis für Waffenschaden |
| `MAGIC_DAMAGE` | absolut | Basis für Fähigkeitsschaden |
| `ATTACK_SPEED` | absolut | Angriffe je Zeiteinheit, gespiegelt zu Vanilla |
| `MOVEMENT_SPEED` | absolut | Laufgeschwindigkeit, gespiegelt zu Vanilla |
| `ABILITY_COOLDOWN` | prozentual | Reduktion der Abklingzeit, hart gedeckelt |

Der Konfigurationsschlüssel ist der Name in `lowerCamelCase` (`health`, `physicalDamage`, …), damit
`stats.yml` genau die Bezeichner aus dem Blocksteckbrief trägt.

`Attribute.byKey(String)` löst einen Schlüssel auf und wirft `UnknownAttributeException` bei einem
unbekannten Namen (FR-004a, FR-009). Kein stilles Anlegen, kein `null`.

### `AttributeKind`

`ABSOLUTE | PERCENT`. Steuert zwei Dinge: die Darstellung (Prozentwerte werden als `40 %` statt
`0.4` ausgegeben) und die Plausibilitätsprüfung der Konfiguration — ein Prozentattribut mit einer
Obergrenze über 1,0 ist ein Konfigurationsfehler und bricht den Start ab.

### `AttributeDefinition` (Record — FR-002)

| Feld | Typ | Regel |
|---|---|---|
| `attribute` | `Attribute` | nicht null |
| `base` | `double` | endlich; muss in `[min, max]` liegen |
| `min` | `double` | endlich |
| `max` | `double` | endlich; **echt größer** als `min` |
| `modifierBand` | `double` | ≥ 0; erlaubte relative Abweichung vom Basiswert, `0` = unbegrenzt |

Zusätzliche Regeln, alle beim Start geprüft (FR-014a):

- `HEALTH.min ≥ 1` — ein Träger mit maximalem Leben null kann nicht entstehen.
- Für `AttributeKind.PERCENT`: `min ≥ -1.0` und `max ≤ 1.0`.
- `ABILITY_COOLDOWN.max` ist der harte Cap aus FR-013 (Auslieferung: `0.40`).
- `ATTACK_SPEED.modifierBand` und `MOVEMENT_SPEED.modifierBand` sind Pflicht und größer null
  (Auslieferung: `0.50` und `0.30`).

### `StatConfig` (Record — FR-003)

`Map<Attribute, AttributeDefinition> definitions`. Der Konstruktor prüft, dass **jedes** der acht
Attribute vertreten ist; ein fehlendes Attribut benennt die Ausnahme namentlich (User Story 7,
Szenario 3). Aufbau und Auslieferungswerte: siehe [contracts/stat-config.md](./contracts/stat-config.md).

---

## 2 · Beiträge und Quellen

### `ModifierOperation`

`FLAT | PERCENT`. `FLAT` addiert zur Basis, `PERCENT` fließt in die Prozentsumme. Keine dritte
Rechenart — ADR-008 kennt keine.

### `SourceKind` (FR-006)

`CLASS, LEVEL, EQUIPMENT, BUFF, AURA, ZONE`. Trägt zusätzlich die Sortierordnung für die
Summierung (E3 in `research.md`): die Reihenfolge der Konstanten ist die Summierreihenfolge.

### `SourceId` (Record — FR-007)

| Feld | Typ | Regel |
|---|---|---|
| `kind` | `SourceKind` | nicht null |
| `key` | `String` | nicht leer; eindeutig innerhalb einer Art |

Gleichheit über beide Felder. Beispiele: `(EQUIPMENT, "slot:CHEST")`, `(BUFF, "berserker:a1f3…")`,
`(LEVEL, "level")`. Der Schlüssel gehört dem beitragenden Block; B04 legt ihn nie selbst fest.

### `StatModifier` (Record — FR-005)

| Feld | Typ | Regel |
|---|---|---|
| `attribute` | `Attribute` | nicht null |
| `operation` | `ModifierOperation` | nicht null |
| `value` | `double` | endlich; `NaN` und `Infinity` werden abgelehnt |

Bewusst **ohne** Quellen-ID: die ID hängt am `ModifierSet`, nicht am einzelnen Beitrag. Sonst müsste
sie bei jedem Beitrag wiederholt werden und könnte innerhalb einer Quelle abweichen.

### `ModifierSet` (Record)

| Feld | Typ | Regel |
|---|---|---|
| `source` | `SourceId` | nicht null |
| `modifiers` | `List<StatModifier>` | unveränderliche Kopie; darf leer sein |

Ein leerer Satz ist zulässig und bedeutet „diese Quelle wirkt gerade nicht" — das erspart
beitragenden Blöcken eine Fallunterscheidung.

---

## 3 · Ergebnis

### `StatSnapshot` (FR-020, FR-021)

| Feld | Typ | Sichtbarkeit |
|---|---|---|
| `values` | `double[8]` | privat, wird nie herausgegeben |
| `revision` | `long` | öffentlich; steigt je Träger streng monoton |

Zugriff ausschließlich über `double get(Attribute)`. Zusätzlich `boolean isNewerThan(StatSnapshot)`
für Verbraucher, die Änderungen erkennen wollen, ohne Werte zu vergleichen (B13).

Der Schnappschuss ist bewusst nicht an einen Träger gebunden: er wird kopiert weitergereicht, etwa
an ein fliegendes Projektil, und muss den Träger überleben können.

**Speicherabschätzung**: 8 × 8 Byte Nutzdaten plus Objektkopf, rund 96 Byte je Schnappschuss. Bei
1000 Trägern rund 96 KB für die jeweils aktuelle Fassung — vernachlässigbar. Ältere Schnappschüsse
leben nur so lange, wie ein laufender Vorgang sie hält.

---

## 4 · Träger

### `StatHolder` (FR-035, FR-036)

| Feld | Typ | Bedeutung |
|---|---|---|
| `holderId` | `UUID` | Spieler-UUID oder Entity-UUID |
| `characterId` | `UUID` (nullbar) | gesetzt für Spielercharaktere, `null` für Mobs |
| `sources` | `LinkedHashMap<SourceId, ModifierSet>` | alle wirkenden Quellen |
| `snapshot` | `volatile StatSnapshot` | zuletzt berechnetes Ergebnis |
| `resources` | `ResourcePool` | aktuelles Leben und Mana |
| `recalcPending` | `AtomicBoolean` | Vormerkung für die Bündelung (FR-019) |
| `revisionCounter` | `long` | Quelle der Schnappschuss-Revision |

Ein Träger ohne `characterId` wird nie persistiert — das ist die einzige Unterscheidung zwischen
Spieler und Mob im gesamten Modell (FR-035).

### Zustandsübergänge eines Trägers

```
        create(holderId, characterId)
                    │
                    ▼
              ┌───────────┐   markDirty()   ┌───────────────┐
              │  CURRENT  │────────────────▶│ RECALC_PENDING │
              │ snapshot  │                 │  Aufgabe läuft │
              │  gültig   │◀────────────────│                │
              └───────────┘   recalculate() └───────────────┘
                    │                              │
                    │ remove()                     │ remove()
                    ▼                              ▼
              ┌───────────────────────────────────────┐
              │ REMOVED — Quellen frei, Vormerkung    │
              │ verfällt folgenlos, Aufgabe rechnet   │
              │ nichts mehr                            │
              └───────────────────────────────────────┘
```

Regeln:

- `markDirty()` auf einem entfernten Träger ist wirkungslos und wirft nicht.
- Eine bereits geplante Aufgabe, deren Träger inzwischen entfernt wurde, kehrt sofort zurück
  (Edge Case „Träger wird entfernt, während eine Vormerkung aussteht").
- `recalculateNow()` überspringt die Bündelung und rechnet sofort; danach ist die Vormerkung
  gelöscht. Genutzt vom Ladepfad (FR-019b) und von Trägern ohne Entität.

---

## 5 · Ressourcen

### `ResourcePool` (FR-025 bis FR-027)

| Feld | Typ | Regel |
|---|---|---|
| `currentHealth` | `double` | in `[0, maxHealth]` |
| `currentMana` | `double` | in `[0, maxMana]` |

Die Maxima stammen aus dem jeweils aktuellen Schnappschuss und werden nicht dupliziert.

Verhalten bei Änderung des Maximums (FR-026):

| Fall | Verhalten |
|---|---|
| Maximum steigt | aktueller Stand bleibt unverändert — kein Auffüllen |
| Maximum sinkt über den Stand | Stand wird auf das neue Maximum geklemmt |
| Maximum sinkt unter den Stand | Stand bleibt unverändert |
| Ergebnis wäre negativ | Stand ist null; **kein** Todesereignis wird ausgelöst |

Der letzte Punkt ist eine Blockgrenze: B04 stellt fest, dass der Stand null ist, und veröffentlicht
das als Ereignis. Was daraus folgt, entscheidet B05.

### `ResourceView` (Record)

Die Lesesicht, die `StatEngine.resources(…)` herausgibt — ein Abbild, kein Zugriff auf den
Behälter.

| Feld | Typ | Herkunft |
|---|---|---|
| `currentHealth` | `double` | `ResourcePool` |
| `maxHealth` | `double` | aktueller Schnappschuss, `Attribute.HEALTH` |
| `currentMana` | `double` | `ResourcePool` |
| `maxMana` | `double` | aktueller Schnappschuss, `Attribute.MANA` |

Beide Maxima kommen aus demselben Schnappschuss, damit ein Aufrufer nie einen aktuellen Stand
gegen ein Maximum aus einer anderen Berechnungsrunde hält.

### `CharacterResources` (Record — FR-028)

Die persistierbare Form. Bewusst getrennt von `ResourcePool`: der Behälter im Speicher ist
veränderlich und maximenabhängig, der Datensatz ist ein unveränderlicher Rohwert.

| Feld | Typ | Regel |
|---|---|---|
| `characterId` | `UUID` | nicht null |
| `currentHealth` | `double` | ≥ 0 |
| `currentMana` | `double` | ≥ 0 |
| `dataVersion` | `int` | ≥ 1; aktuell 1 |
| `revision` | `long` | ≥ 0; steigt bei jedem Schreiben, wie in B02 |

---

## 6 · Tabelle `rpg.character_stats`

Migration `V4_1__character_stats.sql`. Versionsraum nach der in `V3_1` festgehaltenen Regel:
B04 nutzt `V4_x`.

| Spalte | Typ | Regel |
|---|---|---|
| `character_id` | `UUID` | Primärschlüssel, `REFERENCES rpg.character (character_id) ON DELETE CASCADE` |
| `current_health` | `DOUBLE PRECISION` | `NOT NULL`, `CHECK (current_health >= 0)` |
| `current_mana` | `DOUBLE PRECISION` | `NOT NULL`, `CHECK (current_mana >= 0)` |
| `data_version` | `INTEGER` | `NOT NULL DEFAULT 1` |
| `revision` | `BIGINT` | `NOT NULL DEFAULT 0` |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

Bewusst **nicht** enthalten: Maxima, berechnete Endwerte, Modifikatoren. Alles davon ist abgeleitet
und entsteht beim Laden neu (Prinzip IV, ADR-004).

`ON DELETE CASCADE` erledigt die Anonymisierung aus B02 mit: wird ein Charakter gelöscht,
verschwindet sein Ressourcensatz, ohne dass B02s Löschpfad B04 kennen muss.

**Schreibpfad**: neuer Aggregattyp `AggregateType.CHARACTER_STATS`, registriert in `FlushCycle`
**nach** `CHARACTER` (Fremdschlüsselordnung). Vorgemerkt wird über
`WriteBehindCoordinator.markDirty(CHARACTER_STATS, characterId)` — kein direkter Datenbankzugriff
aus dem Spielpfad (FR-028, SC-012).

**Lesepfad**: vierter `SELECT` im Bündelladen von B03, auf derselben Verbindung und in derselben
Transaktion (E6 in `research.md`). `SessionBundle` wird um `List<CharacterResources>` erweitert.

**Fehlender Satz**: Ein Charakter ohne Zeile in `character_stats` ist gültig — er ist neu. Der
Ladepfad legt dann einen Stand auf dem jeweiligen Maximum an (FR-027).

---

## 7 · Schnittstellen für spätere Blöcke

### `BaseStatContributor` (FR-039)

Liefert Basiswertanteile, die nicht aus der Konfiguration stammen: Level (B06) und Klasse (B07).
Ein Beitragslieferant wird beim Start registriert und bei jeder Neuberechnung befragt. Eine
Ausnahme aus einem Lieferanten wird abgefangen, protokolliert und auf den betroffenen Träger
begrenzt (FR-038); die Neuberechnung läuft mit den übrigen Lieferanten weiter.

### `VanillaAttributeBridge` (FR-034)

Die einzige Stelle, an der B04 die Spielwelt berührt. In `rpg-core` als Schnittstelle, in
`rpg-platform` von `PaperVanillaAttributeBridge` implementiert, in Tests durch eine mitschreibende
Attrappe ersetzt. Ohne Registrierung ist sie wirkungslos — genau das macht die Rechenlogik
serverfrei testbar.

Vollständige Signaturen: [contracts/stat-engine.md](./contracts/stat-engine.md).
