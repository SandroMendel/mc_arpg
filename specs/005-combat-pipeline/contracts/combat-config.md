# Vertrag: `combat.yml` — Kampfkonfiguration

**Feature**: `specs/005-combat-pipeline` | **Datum**: 2026-08-20

Geladen über B01s `ConfigLoader` mit Schemaprüfung. Ein Fehler bricht den Start ab und benennt den
Pfad; ein fehlerhaftes Nachladen lässt den zuletzt gültigen Stand wirksam.

---

## Auslieferungsstand

```yaml
# B05 - combat rules. Every balancing number lives here, never in code (Principle V).
#
# Damage formula (ADR-008 via B04):
#   raw   = attacker attribute * factor
#   final = raw * 100/(100 + defence)      [physical and magic only]
#
# Environment damage is a FIXED amount and ignores defence. That is deliberate: a hazard should
# matter to a beginner with 100 health and become negligible to a geared player with 2000. A
# percentage would stay equally dangerous forever, which is the opposite of the intent.

combat:
  # How long after the last hit given or taken a holder still counts as "in combat".
  # B08 reduces mana regeneration during this window.
  combat-timeout-seconds: 8

  attribution:
    # Attackers tracked per target. A fixed-size array, not a growing list: when it is full the
    # smallest contribution is evicted.
    max-attackers: 16
    # A contribution older than this no longer counts towards loot and XP.
    timeout-seconds: 30

  feedback:
    # Hits from the same attacker on the same target inside this window are summed into ONE
    # display event. At 150 players against 800 mobs, one event per hit would be thousands per
    # second for B13 to draw.
    aggregation-window-millis: 500
    knockback-strength: 0.4

environment:
  # Fixed amounts, applied without defence. Tuned so a fall matters early and not later.
  fall:
    # No damage below this height, then this much per block beyond it.
    safe-blocks: 3.0
    damage-per-block: 4.0
    # Ceiling, so a void-adjacent drop does not produce absurd numbers.
    max-damage: 200.0
  fire: 2.0
  fire-tick: 1.0
  lava: 8.0
  hot-floor: 2.0
  campfire: 2.0
  drowning: 3.0
  suffocation: 3.0
  contact: 1.0
  block-explosion: 25.0
  entity-explosion: 25.0
  lightning: 30.0
  falling-block: 20.0
  fly-into-wall: 6.0
  freeze: 2.0
  dryout: 2.0
  dragon-breath: 6.0
  sonic-boom: 20.0
  world-border: 2.0

mobs:
  # Stat values B05 gives a hostile creature when it appears, until B10 replaces this with real
  # mob definitions. Numbers only - what a mob IS belongs to B10.
  default:
    health: 60.0
    defense: 0.0
    physical-damage: 8.0
  by-type:
    ZOMBIE:
      health: 80.0
      defense: 10.0
      physical-damage: 10.0
    SKELETON:
      health: 60.0
      defense: 0.0
      physical-damage: 9.0
    CREEPER:
      health: 50.0
      defense: 0.0
      physical-damage: 0.0   # damage comes from its explosion
    SPIDER:
      health: 55.0
      defense: 5.0
      physical-damage: 7.0
    ENDERMAN:
      health: 200.0
      defense: 20.0
      physical-damage: 25.0
```

---

## Prüfregeln (alle beim Start, alle mit Startabbruch)

| Regel | Meldung nennt |
|---|---|
| `combat-timeout-seconds` > 0 | den Wert |
| `max-attackers` zwischen 1 und 64 | den Wert und die Grenzen |
| `attribution.timeout-seconds` > 0 | den Wert |
| `aggregation-window-millis` zwischen 0 und 5000 | den Wert |
| `knockback-strength` ≥ 0 | den Wert |
| Jeder Umgebungsbetrag ≥ 0 und endlich | den betroffenen Schlüssel |
| `fall.safe-blocks` ≥ 0, `damage-per-block` > 0, `max-damage` > 0 | das betroffene Feld |
| `mobs.default` vollständig | das fehlende Feld |
| Jeder Eintrag unter `by-type` vollständig | Mob-Art und Feld |
| Jede Mob-Art ist ein bekannter Typ | die unbekannte Art |
| Alle Mobwerte ≥ 0 und endlich | Mob-Art und Feld |

Die Obergrenze bei `max-attackers` ist keine Willkür: Das Beitragsfenster ist ein Array je Ziel, und
bei 800 Mobs wird aus einer großzügigen Zahl schnell echter Speicher.

---

## Wirkung

**Fallschaden**:

```
schaden = min(max-damage, max(0, fallenBlocks - safe-blocks) * damage-per-block)
```

Sturz aus 10 Blöcken bei Auslieferungswerten: `(10 - 3) × 4 = 28`. Für einen Anfänger mit 100 Leben
28 % — für einen ausgerüsteten Spieler mit 2000 Leben 1,4 %. Genau der beabsichtigte Verlauf.

**Mob-Werte** werden als `ModifierSet` mit der Quelle `(CLASS, "mob:<TYPE>")` angelegt. Damit
verwendet ein Mob denselben Rechenweg wie ein Spieler, und B10 kann später denselben Quellenschlüssel
ersetzen, statt einen zweiten einzuführen.

**Eine Mob-Art ohne Eintrag** bekommt `mobs.default`. Eine Art, die gar keinen Träger bekommen soll
— friedliche Wesen — steht in keiner der beiden Listen; die Zuordnung liefert dann nichts, und
FR-019e greift.

---

## Nachladen im Betrieb

Eine gültige neue Konfiguration wirkt sofort für alles Zeitbasierte und für neue Vorgänge. Bereits
ausgestattete Mobs behalten ihre Werte bis zu ihrem Verschwinden — ein laufender Kampf ändert nicht
mitten im Schlag die Regeln.
