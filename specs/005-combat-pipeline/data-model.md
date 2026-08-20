# Phase 1 — Datenmodell: B05 · Kampf- & Schadens-Pipeline

**Feature**: `specs/005-combat-pipeline` | **Datum**: 2026-08-20

Alle Typen liegen in `rpg.core.combat` und sind bukkitfrei. Bezeichner auf Englisch (Prinzip VIII).

**Es gibt keine Tabelle und keine Migration.** B05 persistiert nichts — alle drei Zustände sind
bewusst flüchtig.

---

## 1 · Schaden beschreiben

### `DamageType`

`PHYSICAL | MAGIC | ENVIRONMENT`. Bestimmt zwei Dinge: welches Attribut die Basis liefert und ob die
Verteidigung greift.

| Typ | Basis | Verteidigung |
|---|---|---|
| `PHYSICAL` | `physicalDamage` des Angreifers | ja |
| `MAGIC` | `magicDamage` des Angreifers | ja |
| `ENVIRONMENT` | fester Betrag aus `combat.yml` | **nein** (FR-012b) |

### `DamageOrigin`

`MELEE | PROJECTILE | ABILITY | ENVIRONMENT | ADMIN`. Woher der Vorgang kommt — für die
Vanilla-Zuordnung, die Attribution und für spätere Statistiken.

`ADMIN` ist der Weg für `/kill`: sofort tödlich, ohne Formel und ohne Attribution.

### `EnvironmentSource`

Die abgebildeten Umgebungsquellen als eigener Aufzählungstyp, damit die Pipeline nicht Papers
`DamageCause` kennen muss (der gehört `rpg-platform`):

`FALL, FIRE, FIRE_TICK, LAVA, HOT_FLOOR, CAMPFIRE, DROWNING, SUFFOCATION, CONTACT,
BLOCK_EXPLOSION, ENTITY_EXPLOSION, LIGHTNING, FALLING_BLOCK, FLY_INTO_WALL, FREEZE, DRYOUT,
DRAGON_BREATH, SONIC_BOOM, WORLD_BORDER`

Jede Konstante trägt ihren Konfigurationsschlüssel. Die Zuordnung von `DamageCause` auf diesen Typ
liegt in `rpg-platform` — das ist die Grenze, die `rpg-core` bukkitfrei hält.

### `FallDamageConfig` (Record — FR-012c)

| Feld | Regel |
|---|---|
| `safeBlocks` | ≥ 0; darunter kein Schaden |
| `damagePerBlock` | > 0 |
| `maxDamage` | > 0 |

```
schaden = min(maxDamage, max(0, fallenBlocks - safeBlocks) * damagePerBlock)
```

### `DamageResult` (Record) und `RejectReason`

Das Ergebnis eines Vorgangs, wie ihn ein Aufrufer zurückbekommt.

| Feld | Typ | Bedeutung |
|---|---|---|
| `applied` | `boolean` | ob Schaden angewandt wurde |
| `finalDamage` | `double` | 0,0 bei Ablehnung |
| `lethal` | `boolean` | ob dieser Vorgang das Ziel getötet hat |
| `reason` | `RejectReason` | `NONE` bei Erfolg |

`RejectReason`: `NONE, NOT_PERMITTED, ATTACK_TOO_SOON, SESSION_NOT_READY, NO_HOLDER, ALREADY_DEAD`.

Ein Ablehnungsgrund statt eines stillen `false`: Wer einen Angriff auslöst und nichts passiert, muss
unterscheiden können zwischen „zu früh geklickt", „hier ist PvP aus" und „die Sitzung lädt noch" —
sonst landet jede dieser Fragen später als Fehlerbericht.

### `DeathCause`

`COMBAT, ENVIRONMENT, VOID, ADMIN`. Grober als `EnvironmentSource`, weil B06, B11 und B12 die
Ursache nur zur Unterscheidung brauchen, nicht zur Berechnung.

### `DamageContext` (veränderlich, wiederverwendet — research.md E2)

| Feld | Typ | Bedeutung |
|---|---|---|
| `attackerId` | `UUID` (nullbar) | fehlt bei Umgebungsschaden |
| `targetId` | `UUID` | nie null |
| `type` | `DamageType` | |
| `origin` | `DamageOrigin` | |
| `factor` | `double` | Anteil des Basisattributs; 1,0 im Nahkampf (FR-002a) |
| `rawDamage` | `double` | nach Stufe 2 gesetzt |
| `finalDamage` | `double` | nach Stufe 4 gesetzt |
| `attackerSnapshot` | `StatSnapshot` (nullbar) | einmal gezogen (FR-005) |
| `targetSnapshot` | `StatSnapshot` | |
| `stage` | `PipelineStage` | aktueller Stand |
| `cancelled` | `boolean` | von einer Stufe gesetzt (FR-009) |

**Lebensdauer**: genau ein Vorgang. Danach wird der Kontext zurückgesetzt und wiederverwendet.

> **Vertragsregel**: Kein Aufrufer hält einen Kontext über das Ende seines Vorgangs hinaus fest.
> Deshalb bekommen Stufen und Eingriffspunkte nie den Kontext, sondern `DamageView`.

### `DamageView` (unveränderliche Sicht)

Liest alle Felder, ändert nur drei Dinge über gezielte Methoden: `setRawDamage`, `setFinalDamage`,
`cancel`. Gültig nur während des Aufrufs.

---

## 2 · Die Pipeline

### `PipelineStage`

```
SOURCE ──▶ RAW_DAMAGE ──▶ MODIFIERS ──▶ DEFENCE ──▶ APPLICATION ──▶ AFTERMATH
```

| Stufe | Was hier passiert | Wer greift später ein |
|---|---|---|
| `SOURCE` | Erlaubnis (FR-042, FR-042a), Angriffszeitfenster (FR-021), Sitzungsbereitschaft | B09 Zonenregeln |
| `RAW_DAMAGE` | Basisattribut × Faktor, oder fester Umgebungsbetrag | B11 Waffeneffekte |
| `MODIFIERS` | additive und multiplikative Eingriffe | B08 Buffs, B11 Item-Effekte |
| `DEFENCE` | Divisor-Modell aus B04 — bei `ENVIRONMENT` übersprungen | B08 Schilde |
| `APPLICATION` | Leben abziehen, Kampfzustand setzen, Beitrag vermerken | — |
| `AFTERMATH` | Trefferanimation, Rückstoß, Anzeige, Tod | B12 Statistiken |

Ein Abbruch in einer Stufe beendet den Vorgang sofort: kein Schaden, keine Animation, kein Beitrag
(FR-009).

### `DamageInterceptor` (FR-008)

```java
public interface DamageInterceptor {
    String id();
    PipelineStage stage();
    void intercept(DamageView damage);
}
```

Eine Ausnahme wird abgefangen, mit Eingriffspunkt-ID protokolliert und auf diesen Vorgang begrenzt;
die Pipeline läuft weiter (FR-010) — dasselbe Muster wie B01s `ModuleFaultBarrier` und B04s
Beitragslieferanten.

---

## 3 · Die Formel

```
PHYSICAL / MAGIC:
    raw   = attackerSnapshot.get(basis) * factor
    final = DamageMitigation.afterDefense(raw, targetSnapshot.get(DEFENSE))

ENVIRONMENT:
    raw   = konfigurierter Betrag der Quelle   (bei FALL: Funktion der Fallhöhe)
    final = raw                                 (keine Verteidigung, FR-012b)
```

Statisch, zustandslos, ohne Zufall (FR-004).

**Dokumentierte Beispielrechnungen** (SC-002, SC-012):

| Angreifer | Faktor | Ziel-Verteidigung | roh | endgültig |
|---|---|---|---|---|
| 50 physisch | 1,0 | 100 | 50,0 | **25,0** |
| 100 physisch | 1,0 | 300 | 100,0 | **25,0** (75 % Minderung) |
| 100 physisch | 1,0 | 0 | 100,0 | **100,0** |
| 40 magisch | 1,8 | 100 | 72,0 | **36,0** |
| Fall aus 10 Blöcken | — | beliebig | 30,0 | **30,0** |

Die letzte Zeile ist die Designentscheidung in Zahlen: 30 Schaden kosten einen Anfänger mit 100
Leben 30 %, einen ausgerüsteten Spieler mit 2000 Leben 1,5 %.

---

## 4 · Zeitstempel-Zustände (research.md E4)

Drei Fälle, ein Muster: ein `long` je Träger, verglichen gegen die aktuelle Zeit. Kein Timer, keine
Aufgabe, keine Ablaufliste.

### `AttackWindow` (FR-020 bis FR-023)

| Feld | Typ |
|---|---|
| `lastAttackAt` | `long` je Angreifer |

```
minimumGap = 1000 ms / attackSpeed
zählt = (jetzt - lastAttackAt) >= minimumGap
```

`attackSpeed` wird bei jedem Angriff frisch aus dem Schnappschuss gelesen — eine Änderung wirkt
sofort, ohne dass etwas neu geplant wird (FR-023).

### `CombatState` (FR-030c bis FR-030f)

| Feld | Typ |
|---|---|
| `lastCombatAt` | `long` je Träger |

`imKampf = (jetzt - lastCombatAt) < combatTimeout`. Gesetzt beim Geben **und** beim Nehmen von
Schaden. Der Übergang wird beim Auswerten erkannt und als Ereignis veröffentlicht.

### Aufräumen

Beide Karten werden beim Sitzungsende (Spieler) beziehungsweise beim Entfernen des Trägers (Mob)
geleert — an denselben Stellen, an denen B04 seine Träger freigibt. Kein eigener Aufräumdurchlauf.

---

## 5 · `AttributionWindow` (FR-031 bis FR-036, research.md E5)

Je Ziel ein Array fester Größe:

| Feld | Typ | Regel |
|---|---|---|
| `attackerIds` | `UUID[capacity]` | Auslieferung: 16 Plätze |
| `damage` | `double[capacity]` | Summe je Angreifer |
| `lastAt` | `long[capacity]` | für den Verfall |

**Regeln**

- Ein neuer Beitrag sucht linear nach dem Angreifer; bei 16 Plätzen ist das schneller als jede Map,
  weil alles in derselben Cache-Zeile liegt.
- Ist das Array voll, weicht der **kleinste** Beitrag (FR-032).
- Ein Platz, dessen `lastAt` älter als `attributionTimeout` ist, gilt als frei (FR-033) — geprüft
  beim Zugriff, nicht durch einen Durchlauf.
- Selbstschaden erzeugt keinen Beitrag (FR-035).
- Stirbt oder verschwindet das Ziel, wird das Fenster freigegeben (FR-036).

**Speicherabschätzung**: rund 512 Byte je Ziel, bei 800 Mobs etwa 400 KB. Wächst nicht.

### `DamageShare` (FR-034)

| Feld | Typ |
|---|---|
| `shares` | `Map<UUID, Double>` — Anteile, Summe 1,0 |
| `topContributor` | `UUID` (nullbar) |
| `totalDamage` | `double` |

Wird **einmal beim Tod** erzeugt — der einzige Punkt, an dem eine Allokation je Vorgang in Kauf
genommen wird, weil ein Tod selten ist und das Ergebnis den Vorgang überlebt.

---

## 6 · Erlaubnis und Mob-Ausstattung

### `DamagePermission` (FR-042, FR-042a)

Die **eine** Stelle, an der entschieden wird, wer wen treffen darf.

| Angreifer | Ziel | Erlaubt |
|---|---|---|
| Spieler | Mob | ja |
| Mob | Spieler | ja |
| Spieler | Spieler | **nein** (FR-041) |
| Mob | Mob | **nein** (FR-042a) |
| beliebig | sich selbst | ja, aber ohne Attribution (FR-035) |
| Umgebung | beliebig | ja |

B09 ersetzt die dritte Zeile später durch eine Regel je Zone — an dieser Stelle und sonst nirgends.

### `MobStatProvider` (FR-019c)

```java
public interface MobStatProvider {
    Optional<ModifierSet> statsFor(String mobTypeKey);
}
```

B05 bringt eine Umsetzung mit, die `combat.yml` liest; B10 ersetzt sie. Was ein Mob *ist* — Name,
Verhalten, Fähigkeiten, Beute — bleibt vollständig B10. B05 liefert nur Zahlen.

---

## 7 · Ereignisse

Drei, alle unveränderliche Records über B01s `EventBus`. Vollständig in
[contracts/events.md](./contracts/events.md).

| Ereignis | Wann | Für |
|---|---|---|
| `DamageDealtEvent` | zusammengefasst je Angreifer-Ziel-Paar und Fenster (FR-038) | B13 Anzeige, B12 Statistiken |
| `CombatDeathEvent` | genau einmal je Tod (FR-026) | B06 XP, B11 Beute und Ausrüstungsschaden, B12 |
| `CombatStateChangedEvent` | beim Betreten und Verlassen des Kampfes (FR-030e) | B08 Mana-Regeneration, B13 |

---

## 8 · Zustandsübergänge eines Schadensvorgangs

```
        Vanilla-Ereignis oder eigener Aufruf
                       │
                       ▼
                  ┌─────────┐   Erlaubnis verweigert,
                  │ SOURCE  │──▶ Zeitfenster nicht offen,   ──▶ VERWORFEN
                  └─────────┘   Sitzung nicht bereit            (kein Schaden,
                       │                                         keine Animation,
                       ▼                                         kein Beitrag)
              RAW_DAMAGE ─▶ MODIFIERS ─▶ DEFENCE
                       │
                       ▼
                 ┌─────────────┐
                 │ APPLICATION │  Leben abziehen, Kampfzustand,
                 └─────────────┘  Beitrag vermerken
                       │
                       ▼
                 ┌───────────┐   Leben == 0 ──▶ TOD (genau ein Ereignis)
                 │ AFTERMATH │
                 └───────────┘   sonst ──▶ Animation, Rückstoß, Anzeige
```

Ein Abbruch ist an jeder Stufe möglich und führt immer nach VERWORFEN.
