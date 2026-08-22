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
| `sustained` | boolean | ob die Fähigkeit über eine Dauer wirkt und per zweitem Rechtsklick endbar ist (FR-045a) |
| `duration` | Duration \| null | Pflicht bei `sustained` |
| `charges` | int | Vorgabe 1; > 1 heißt, der Cooldown beginnt erst nach der letzten (FR-045i) |
| `chargeWindow` | Duration \| null | Pflicht bei `charges` > 1 — danach springt der Vorrat zurück (FR-045j) |
| `requiresBehindTarget` | boolean | Positionsbedingung, nur bei `PASSIVE` sinnvoll (FR-052a) |
| `openWorldOnly` | boolean | Weltbedingung; **bis B09 ungeprüft** (FR-052b) |
| `playerToggle` | boolean | ob der Spieler die Fähigkeit abschalten darf (FR-052d) |
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
| `type` | `EffectType` | eines der sechzehn Primitives |
| `amount` | double | Wert auf Rang 1 |
| `perRank` | double | Zuwachs je weiterem Rang; ≥ 0 |
| `duration` | Duration \| null | nur bei zeitlich wirkenden Primitives |
| `interval` | Duration \| null | gesetzt heißt: wirkt **wiederholt** über die Dauer statt einmalig (FR-010a) |
| `maxStacks` | int | Vorgabe 1; > 1 nur mit `interval` (FR-010c) |
| `stackCap` | double \| null | Obergrenze der Gesamtwirkung je Intervall über alle Stapel |
| `attribute` | `Attribute` \| null | Pflicht bei `BUFF`, `DEBUFF` und `METER` |
| `damageType` | `DamageType` \| null | Pflicht bei `DAMAGE`; bei `SHIELD` und `EVADE` **optional** als Filter (FR-015a, FR-016a) |
| `statusEffect` | String \| null | Pflicht bei `STATUS_EFFECT` |
| `buildPerHit` | double \| null | Pflicht bei `METER` — wie stark ein Treffer den Zähler hebt |
| `idleBefore` | Duration \| null | Pflicht bei `METER` — Ruhefrist, bevor er zu fallen beginnt |
| `decayPerSecond` | double \| null | Pflicht bei `METER` |

Der Wert auf Rang *r* ist `amount + perRank × (r − 1)`. Eine Multiplikation beim Auslesen, kein
zweiter Satz Definitionen (FR-063).

### `EffectType` — die sechzehn Primitives

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
| `EVADE` | Wahrscheinlichkeit, eingehenden Schaden vollständig zu vermeiden | mit Typfilter — Mages Magic Life weicht nur magischem aus |
| `METER` | Zähler 0–100, steigt bei Schaden, fällt nach Ruhefrist, skaliert Attribute | Warriors Wut. **Lazy** aus letztem Stand plus Zeit; keine Aufgabe, keine Tabelle |
| `SUMMON` | ein Wesen mit den Werten des Auslösers, das nicht angreift und beim Ende einen Effekt auslöst | Rogues Klon. **Aggro-Umlenkung bis B10 wirkungslos** (ADR-025) |
| `INVISIBILITY` | unsichtbar und unverwundbar für eine Dauer, endet bei ausgeteiltem Schaden | **Dass Mobs ihn nicht angreifen und Bosse ihn dennoch sehen, folgt mit B10** |

**Kein eigenes Primitive für Schaden über Zeit.** Das `interval`-Feld macht jeden Effekt periodisch:
`DAMAGE` mit Intervall ist ein DoT, `MANA_RESTORE` mit Intervall ist der Manatrank. Vier Fähigkeiten
brauchen das, und vier Primitives dafür wären vier Wege, dasselbe zu tun.

### `TargetSpec`

| Feld | Typ | Regel |
|---|---|---|
| `mode` | `TargetMode` | eine der neun |
| `range` | double | > 0, außer bei `SELF` |
| `angle` | double \| null | Pflicht bei `CONE` |
| `maxTargets` | int | **Pflichtfeld** bei jedem Modus, der mehr als ein Ziel liefern kann (FR-020) |
| `hopRange` | double \| null | Pflicht bei `CHAIN` — Umkreis, in dem vom zuletzt getroffenen Ziel weitergesprungen wird |
| `areaRadius` | double \| null | Pflicht bei `GROUND_AREA` — Radius der verankerten Fläche |

`TargetMode`: `SELF`, `LOOK_DIRECTION`, `CURSOR`, `RADIUS`, `CONE`, `LINE`, `NEAREST`, **`CHAIN`**,
**`GROUND_AREA`**.

`CHAIN` sucht jedes weitere Ziel im Umkreis des **zuletzt getroffenen**, nicht des Auslösers, und
trifft keines zweimal (FR-019a). `GROUND_AREA` verankert sich an einem Punkt und bleibt dort, auch
wenn der Auslöser weggeht; `range` ist dort die Höchstentfernung vom Auslöser (FR-019b).

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
| `toggleState` | `ToggleState` \| null | nur bei abschaltbaren Fähigkeiten; Vorgabe `ON` (FR-052d) |
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
| `toggle_state` | text | `NULL` = Vorgabe; sonst `ON`, `OFF` oder ein fähigkeitseigener Zwischenwert |
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

### `SustainedState`

Eine laufende haltende Fähigkeit. **Höchstens eine je Spieler** (FR-045b). Sieben der achtzehn
Fähigkeiten erzeugen sie.

| Feld | Typ |
|---|---|
| `characterId` | UUID |
| `abilityId` | String |
| `startedAt` | Instant |
| `endsAt` | Instant |
| `spentMana` | double |
| `endTask` | `TaskHandle` |

**Zustandsübergänge — die zweiphasige Abbruchregel (FR-045d, FR-045e):**

```
                auslösen
kein Zustand ─────────────► VORBEREITUNG (Wirkzeit oder Zielphase)
                                 │
        ┌────────────────────────┼────────────────────────┐
        │ zweiter Rechtsklick    │ Wirkzeit abgelaufen     │
        │ Schaden, Slotwechsel   │                         │
        ▼                        ▼                         │
   abgebrochen              LAUFENDE WIRKUNG ◄──────────────┘
   ├─ Mana zurück                │
   └─ KEIN Cooldown              ├──────────────┬──────────────┐
                                 │ Dauer aus    │ zweiter      │ Tod,
                                 │              │ Rechtsklick  │ Trennung
                                 ▼              ▼              ▼
                              beendet ── Mana verbraucht, Cooldown läuft
```

Ab dem Eintritt in die laufende Wirkung gibt es **keinen Weg zurück** (FR-045f). Der Sprung ist ab
dem Absprung unabbrechbar, Blitz, Blitzsturm und Klon sind es ab der Auslösung — sie haben gar keine
Vorbereitung.

### `MeterState`

Warriors Wut. Sieht aus wie eine dritte Ressource, ist aber keine.

| Feld | Typ | Bedeutung |
|---|---|---|
| `value` | double | Stand bei der letzten Berührung, in `[0, 100]` |
| `lastHitAt` | Instant | letzter aus- oder eingeteilter Schaden |

Der Stand jetzt ergibt sich aus beidem:

```
untätig = max(0, jetzt − lastHitAt − ruhefrist)
stand   = max(0, value − untätig × zerfallProSekunde)
```

**Lazy, ohne Aufgabe.** Der Beitrag zu den Attributen wird bei jedem Schadensereignis neu gesetzt —
das ist ohnehin der einzige Moment, in dem er zählt, und es macht die kontinuierliche Abnahme zu
einer ereignisgesteuerten Neuberechnung (ADR-013). **Wird nicht persistiert**: wer sich abmeldet,
beginnt bei 0.

### `ChargeState`

Rogues Teleport. Je Charakter und Fähigkeit.

| Feld | Typ |
|---|---|
| `remaining` | int |
| `lastUsedAt` | Instant |

Ist `jetzt − lastUsedAt` größer als das Nachfüllfenster, steht der Vorrat wieder auf seinem Maximum,
ohne dass ein Cooldown lief (FR-045j). Der Cooldown startet erst, wenn die letzte Ladung fällt.
Wieder reine Zeitstempelarithmetik.

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
| `SustainedState` | Sekunden | Speicher, nur bei laufender haltender Fähigkeit |
| `MeterState` (Wut) | Sitzung | Speicher — **nicht** persistiert, beginnt beim Anmelden bei 0 |
| `ChargeState` | Sekunden bis Minuten | Speicher; der Cooldown darunter liegt in der Tabelle |
| `RegenerationState` | Sitzung | Speicher |
| Spielereinstellung (Rise & Fall) | dauerhaft | `rpg.character_abilities`, eigene Spalte |
| Globale Sperre | Sekundenbruchteile | Speicher |
| Freischaltung | — | **nirgends**, wird abgeleitet |

**Die Tabelle wächst um eine Spalte, nicht um eine zweite Tabelle.** Mages Rise & Fall ist die
einzige Fähigkeit mit einer Spielereinstellung; `toggle_state` in `rpg.character_abilities` reicht
dafür und bleibt für weitere abschaltbare Fähigkeiten frei.

**Drei laufende Zustände, kein einziger davon eine wiederkehrende Aufgabe.** `CastState` und
`SustainedState` planen je einen einmaligen Ablauf. `MeterState`, `ChargeState`,
`RegenerationState`, Cooldowns und die globale Sperre sind reine Zeitstempelarithmetik. Die
Intervall-Effekte laufen über **eine** gemeinsame serverweite Auswertung (FR-010b) — nicht über eine
je Effekt und schon gar nicht über eine je Ziel.
