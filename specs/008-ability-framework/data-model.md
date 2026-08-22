# Phase 1 · Datenmodell B08 · Fähigkeiten-Framework

Drei Gruppen, die sich in ihrer Lebensdauer unterscheiden und deshalb getrennt bleiben:
**Definition** (einmal je Server, unveränderlich), **Charakterstand** (persistiert), **Laufzeit**
(existiert nur, solange etwas läuft).

---

## 1 · Definition — einmal je Server, unveränderlich

### `Ability`

Die geladene Definition einer Fähigkeit. Drei Objekte je Klasse mal sechs, also achtzehn für den
ganzen Server — nicht je Spieler.

| Feld | Typ | Regel |
|---|---|---|
| `id` | String | eindeutig über alle Fähigkeiten, nicht leer |
| `kind` | `AbilityKind` | `ACTIVE` oder `PASSIVE`; muss mit der Klassenbindung übereinstimmen (FR-007) |
| `displayNameKey` | String | Message-Schlüssel, nie ein Text (FR-009) |
| `manaCost` | double | ≥ 0; bei `PASSIVE` zwingend 0 (FR-047) |
| `cooldown` | Duration | ≥ 0 |
| `castTime` | Duration | ≥ 0; bei `PASSIVE` zwingend 0 |
| `interruptOnMove` | boolean | Vorgabe `false` (FR-043) |
| `trigger` | `AbilityTrigger` | Pflicht bei `PASSIVE`, verboten bei `ACTIVE` |
| `chance` | double | in `[0, 1]`, Vorgabe 1.0 (FR-049) |
| `target` | `TargetSpec` | Pflicht |
| `effects` | `List<EffectSpec>` | mindestens ein Eintrag |
| `maxRank` | int | ≥ 1 |
| `item` | String \| null | Vanilla-Material; Pflicht bei `ACTIVE`, optional bei `PASSIVE` (FR-003) |

**Unveränderlich wie `CharacterClassDefinition`**, und aus demselben Grund: achtzehn Objekte, die
jeder Spieler gleichzeitig liest. Listen werden beim Bau kopiert, nicht übernommen.

### `EffectSpec`

Ein Baustein innerhalb einer Fähigkeit.

| Feld | Typ | Regel |
|---|---|---|
| `type` | `EffectType` | eines der zwölf Primitives |
| `amount` | double | Wert auf Rang 1 |
| `perRank` | double | Zuwachs je weiterem Rang; ≥ 0 |
| `duration` | Duration \| null | nur bei zeitlich wirkenden Primitives |
| `attribute` | `Attribute` \| null | Pflicht bei `BUFF` und `DEBUFF` |
| `damageType` | `DamageType` \| null | Pflicht bei `DAMAGE` |
| `statusEffect` | String \| null | Pflicht bei `STATUS_EFFECT` |

Der Wert auf Rang *r* ist `amount + perRank × (r − 1)`. Eine Multiplikation beim Auslesen, kein
zweiter Satz Definitionen (FR-063).

### `EffectType` — die zwölf Primitives

| Primitive | Wirkung | Anmerkung |
|---|---|---|
| `DAMAGE` | Schaden über `CombatPipeline.abilityDamage` | `amount` ist ein **Faktor** auf das Schadensattribut, keine absolute Zahl (FR-013) |
| `HEAL` | Gesundheit anheben | klemmt am Maximum |
| `MANA_RESTORE` | Mana anheben | klemmt am Maximum |
| `LIFESTEAL` | Anteil des zugefügten Schadens als Heilung | nur mit `ON_DAMAGE_DEALT` sinnvoll |
| `SHIELD` | absorbiert Schaden vor der Gesundheit | endet bei Ablauf oder Verbrauch (FR-015) |
| `BUFF` | zeitlich begrenzter Modifikator auf ein Attribut, Selbst | über `StatEngine.apply`, läuft über Zeitstempel ab |
| `DEBUFF` | dasselbe auf ein feindliches Ziel | |
| `STATUS_EFFECT` | ein Vanilla-Statuseffekt für eine Dauer | trägt Slow Fall und Verlangsamung |
| `DASH` | Impuls in Blickrichtung | |
| `KNOCKBACK` | Impuls vom Auslöser weg | |
| `TELEPORT` | augenblickliche Versetzung | Reichweite aus `TargetSpec` |
| `PROJECTILE` | ein Geschoss, das die übrigen Effekte beim Treffer anwendet | trägt die Werte vom Abwurf (wie B05s `projectileDamage`) |

`SUMMON` fehlt und ist als Nachtrag vorgesehen — Beschworenes gehört zu B10 (Workflow-Regel 5).

### `TargetSpec`

| Feld | Typ | Regel |
|---|---|---|
| `mode` | `TargetMode` | eine der sieben |
| `range` | double | > 0, außer bei `SELF` |
| `angle` | double \| null | Pflicht bei `CONE` |
| `maxTargets` | int | **Pflichtfeld** bei jedem Modus, der mehr als ein Ziel liefern kann (FR-020) |

`TargetMode`: `SELF`, `LOOK_DIRECTION`, `CURSOR`, `RADIUS`, `CONE`, `LINE`, `NEAREST`.

### `AbilityConfig`

Die geprüfte Gesamtkonfiguration: alle `Ability`, die globale Sperre und die beiden Kampf-Faktoren
der Regeneration. **Die Regenerationsraten stehen nicht hier** — sie sind Attribute und gehören dem
Charakter (ADR-023).

---

## 2 · Charakterstand — persistiert

### `AbilityState`

Was einem Charakter je Fähigkeit gehört. Gehört dem **Charakter**, nicht dem Konto (ADR-011).

| Feld | Typ | Regel |
|---|---|---|
| `characterId` | UUID | |
| `abilityId` | String | |
| `rank` | int | `1 ≤ rank ≤ ability.maxRank` |
| `cooldownUntil` | Instant \| null | `null`, sobald abgelaufen |
| `dataVersion` | int | Format der Zeile, damit eine alte beim Laden wandern kann |
| `revision` | long | bei jedem Schreiben erhöht, wie in den übrigen Tabellen |

**Kein Freischaltzustand.** Ob eine Fähigkeit verfügbar ist, folgt allein aus dem Level (FR-061) —
dieselbe Regel, die B07 für seine Bindungen gezogen hat. Ein gespeicherter Zustand hätte zwei
Wahrheiten erzeugt, sobald jemand eine Freischaltstufe in der Konfiguration ändert.

### Tabelle `rpg.character_abilities` — Migration `V8_1`

| Spalte | Typ | Anmerkung |
|---|---|---|
| `character_id` | uuid | Fremdschlüssel auf `rpg.character`, kaskadierend |
| `ability_id` | text | |
| `rank` | integer | `NOT NULL`, `CHECK (rank >= 1)` |
| `cooldown_until` | timestamptz | `NULL`, wenn kein Cooldown läuft |
| `data_version` | integer | |
| `revision` | bigint | |

Primärschlüssel `(character_id, ability_id)`. **Keine Zeile für den Normalfall:** eine Fähigkeit auf
Rang 1 ohne laufenden Cooldown wird nicht gespeichert. Die Zeile entsteht beim ersten Rangaufstieg
oder beim ersten Cooldown, der eine Sitzung überdauert — ein frischer Charakter erzeugt damit keine
achtzehn Zeilen für lauter Vorgabewerte.

**Aufräumen beim Laden:** ein `cooldown_until` in der Vergangenheit wird verworfen statt geladen
(FR-031); steht die Zeile dann auf Rang 1, wird sie gelöscht.

### Die drei Registrierungen (ADR-015)

Ein neuer Aggregattyp kostet **drei** Eintragungen, nicht eine:

1. `AggregateType.CHARACTER_ABILITIES`
2. Position in `FlushCycle.WRITE_ORDER` — **nach** `CHARACTER`, wie jedes Kind
3. Verdrahtung des `JdbcAbilityStateRepository` im Persistenzmodul

`NoDatabaseAccessPerGameEventTest` prüft die Vollständigkeit als Invariante.

---

## 3 · Laufzeit — existiert nur, solange etwas läuft

### `CastState`

Ein laufender Cast. **Höchstens einer je Spieler** (FR-040), und ein Spieler ohne laufenden Cast hat
kein Objekt und keine geplante Aufgabe.

| Feld | Typ |
|---|---|
| `characterId` | UUID |
| `abilityId` | String |
| `startedAt` | Instant |
| `effectiveAt` | Instant |
| `reservedMana` | double |
| `interruptOnMove` | boolean |
| `task` | `TaskHandle` |

**Zustandsübergänge:**

```
        auslösen (castTime > 0)
kein Cast ─────────────────────► laufend
                                    │
     ┌──────────────────────────────┼──────────────────────────────┐
     │ effectiveAt erreicht         │ Schaden > 0 nach Mitigation   │
     │                              │ Slotwechsel                   │
     │                              │ Tod, Charakterwechsel,        │
     │                              │ Verbindungsverlust            │
     │                              │ Bewegung (nur wenn gesetzt)   │
     ▼                              ▼                               │
  gewirkt                       abgebrochen ◄────────────────────────┘
  ├─ Effekte laufen              ├─ Mana vollständig erstattet
  ├─ Cooldown beginnt jetzt      ├─ kein Cooldown
  └─ Cast entfällt               └─ Cast entfällt
```

**Die Kosten werden beim Beginn abgebucht** (FR-041). Die Alternative — erst bei Wirkung — hätte
erlaubt, einen Cast ohne ausreichendes Mana zu starten und auf Nachschub zu hoffen, und hätte die
Prüfung zweimal gebraucht.

**Die globale Sperre greift beim Beginn** (FR-029), der Einzel-Cooldown bei der **Wirkung** (FR-030).
Ohne diese Trennung wäre die Sperre durch Fähigkeiten mit Wirkzeit umgehbar.

### `RegenerationState`

Je Charakter zwei Zeitstempel — das ist alles, was die Regeneration braucht.

| Feld | Typ | Bedeutung |
|---|---|---|
| `lastSettledAt` | Instant | bis hierher ist abgerechnet |
| `combatEndsAt` | Instant \| null | Ende des zuletzt beobachteten Kampfes |

**Die Abrechnung** zerlegt `[lastSettledAt, jetzt]` an `combatEndsAt` in höchstens zwei Abschnitte und
wendet auf jeden die passende Rate an:

```
zuwachs = kampfanteil   × rate × kampfFaktor
        + ruheanteil    × rate
```

`combatEndsAt` wird bei jeder Abrechnung aus `remainingCombatTime` nachgeführt, solange der Halter im
Kampf ist. Damit ist die Zerlegung **exakt**, ohne dass ein Ereignis eintreffen muss (research.md R3)
— und sie gilt auch über eine Abwesenheit hinweg (FR-038).

**Wird nicht persistiert.** `lastSettledAt` beim Anmelden ist der Anmeldezeitpunkt; was während der
Abwesenheit auflief, rechnet der Ladepfad einmal aus dem gespeicherten Abmeldezeitpunkt, den B03
ohnehin führt.

### `GlobalLock` und Cooldowns

Beides ist **kein Objekt, sondern ein Zeitstempel**: die globale Sperre ein Instant je Charakter,
der Einzel-Cooldown das `cooldownUntil` aus `AbilityState`. Geprüft wird durch Vergleich, nie durch
Herunterzählen (FR-026).

---

## Was wo lebt — Übersicht

| Was | Lebensdauer | Ort |
|---|---|---|
| `Ability`, `EffectSpec`, `TargetSpec`, `AbilityConfig` | Serverlaufzeit, unveränderlich | Speicher, einmal |
| `AbilityState` (Rang) | dauerhaft | `rpg.character_abilities` |
| `AbilityState` (Cooldown) | bis zum Ablauf, überlebt Neustart | dieselbe Tabelle |
| `CastState` | Sekunden | Speicher, nur bei laufendem Cast |
| `RegenerationState` | Sitzung | Speicher |
| Globale Sperre | Sekundenbruchteile | Speicher |
| Freischaltung | — | **nirgends**, wird abgeleitet |
