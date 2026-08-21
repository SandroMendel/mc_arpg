# Vertrag: `progression.yml`

**Block**: B06 | **Schema**: `ProgressionConfigSchema`, `SCHEMA_VERSION = 1` | **Stand**: 2026-08-20

Jede Zahl, die Balancing ist, steht hier — keine im Code (Prinzip V, FR-005). Ein Fehler bricht den
Start ab und nennt, was zu korrigieren ist (FR-003).

---

## Aufbau

```yaml
# B06: Erfahrung und Level. Alle Zahlen sind Balancing und dürfen ohne Codeänderung wandern.

# Die Kurve. Eine Zeile je Level, der Schlüssel ist das erreichte Level, der Wert die dafür
# benötigte XP INNERHALB des vorigen Levels. Das höchste Level hier ist das Maximallevel -
# es steht bewusst nicht im Code.
#
# Drei Regeln werden beim Start geprüft: lückenlos von 2 aufwärts, jeder Wert positiv,
# streng monoton steigend. Ein Verstoss nennt das erste beanstandete Level.
xp-curve:
  2: 100
  3: 220
  4: 360
  # ... bis 60

# Zuwachs je Level, über alle acht Attribute. Null ist erlaubt und ausdrücklich gemeint:
# Laufgeschwindigkeit über 60 Level macht die Bewegung unspielbar, und Angriffsgeschwindigkeit
# läuft laut B05 gegen die Vanilla-Unverwundbarkeit - Zuwachs darüber verpufft.
#
# Klassenneutral. B07 ersetzt diese Zahlen je Klasse, ohne dass Code sich ändert.
level-growth:
  health: 8.0
  defense: 2.0
  mana: 4.0
  physical-damage: 1.5
  magic-damage: 1.5
  attack-speed: 0.0
  movement-speed: 0.0
  ability-cooldown: 0.0

# XP je Mob-Art. Aufbau wie `mobs:` in combat.yml, damit sich zwei Dateien nebeneinander nicht
# unterschiedlich lesen. Ein Mob ohne eigenen Eintrag bekommt den Standardbetrag und erzeugt
# EINE Warnung je Art - nicht je Kill. Bis B10 kommt, ist das hier die Wahrheit.
mob-xp:
  default: 10
  by-type:
    ZOMBIE: 12
    SKELETON: 12
    SPIDER: 10
    CREEPER: 18
    ENDERMAN: 40

party:
  # Höchstgrösse einschliesslich Anführer.
  max-size: 5
  # Reichweite in Blöcken, gemessen vom gestorbenen Gegner - dem einzigen Punkt, den alle
  # Mitglieder gemeinsam haben. Eine andere Welt ist nie in Reichweite.
  range-blocks: 50.0
  # Prozentaufschlag je ZUSÄTZLICHES Mitglied in Reichweite. Kein Festbetrag: der wäre auf
  # Level 1 riesig und auf Level 60 belanglos.
  bonus-per-member: 0.10
  # Obergrenze für den Aufschlag insgesamt.
  bonus-cap: 0.40
  # Eine Einladung verfällt danach. Zeitstempelbasiert geprüft, ohne Aufgabe.
  invite-timeout-seconds: 60

progress-event:
  # XP-Gewinne innerhalb dieses Fensters werden zu EINEM Ereignis zusammengefasst - dasselbe
  # Muster wie die Schadenszahlen in combat.yml. Bei 1000 Ereignissen je Sekunde wäre eines
  # je Gewinn tausende Zeichenaufträge für B13.
  window-millis: 500
```

---

## Schema

| Schlüssel | Typ | Regel |
|---|---|---|
| `xp-curve` | `MAP` | Sonderprüfung, siehe unten |
| `level-growth.health` … `.ability-cooldown` | `DOUBLE` | Pflicht, je Attribut ein Feld, ≥ 0, endlich |
| `mob-xp.default` | `INTEGER` | ≥ 1 — Rückfall für jede Art ohne eigene Zeile (FR-060) |
| `mob-xp.by-type` | `MAP` | Schlüssel = Typname in Grossbuchstaben, Werte ≥ 1. Gleiche Form wie `mobs.by-type` in `combat.yml` (FR-009a) |
| `party.max-size` | `INTEGER` | ≥ 1 |
| `party.range-blocks` | `DOUBLE` | > 0, endlich |
| `party.bonus-per-member` | `DOUBLE` | ≥ 0, endlich |
| `party.bonus-cap` | `DOUBLE` | ≥ 0, ≥ `bonus-per-member` |
| `party.invite-timeout-seconds` | `INTEGER` | ≥ 1 |
| `progress-event.window-millis` | `INTEGER` | ≥ 1 |

**Alle acht Wachstumsfelder sind Pflicht**, auch die drei mit Null. Dasselbe Argument, mit dem B05
jede Umgebungsquelle zum Pflichtfeld macht: ein fehlendes Feld soll den Start anhalten, nicht
stillschweigend zu Null werden. Sonst wäre „Laufgeschwindigkeit wächst nicht" nicht von „jemand hat
die Zeile vergessen" zu unterscheiden.

**`bonus-cap` ≥ `bonus-per-member`**: eine Obergrenze unter dem Einzelaufschlag würde den Bonus schon
beim zweiten Mitglied kappen und die Einstellung `bonus-per-member` bedeutungslos machen. Besser ein
Startfehler als eine Zahl, die nichts tut.

**Neue Mob-Arten und neue XP-Beträge brauchen keine Codeänderung** (FR-006): eine Zeile unter
`mob-xp.by-type` genügt, und ein Mob ohne Zeile bekommt den Standardbetrag. Dasselbe gilt für die
Kurve — ein höheres Maximallevel ist eine weitere Zeile unter `xp-curve`, keine Konstante im Code
(FR-004).

---

## Kurvenprüfung

Die drei Regeln aus FR-002, geprüft in der Bindefunktion und mit dem **ersten** Verstoss abbrechend:

| Regel | Meldung bei Verstoss |
|---|---|
| Lückenlos von 2 bis zum höchsten Schlüssel | `progression.xp-curve: level 37 is missing` |
| Jeder Wert ≥ 1 | `progression.xp-curve: level 12 must be positive, but was 0` |
| Streng monoton steigend | `progression.xp-curve: level 20 must be greater than level 19 (450), but was 400` |
| Mindestens Level 2 vorhanden | `progression.xp-curve: must define at least level 2` |
| Schlüssel ist eine Zahl ≥ 2 | `progression.xp-curve: 'zwei' is not a level` |

Warum ein Kartenfeld und nicht 59 Pflichtfelder: 59 Einzelfelder könnten Monotonie nicht ausdrücken
— und genau die ist der Fehler, der Spieler dauerhaft auf einem Level festhalten würde. Begründung
ausführlich in [../research.md](../research.md), Entscheidung 2.

---

## Was hier absichtlich nicht steht

| Nicht hier | Warum |
|---|---|
| Maximallevel als eigener Schlüssel | folgt aus dem höchsten Kurvenschlüssel (FR-004); zwei Quellen könnten sich widersprechen |
| XP-Skalierung nach Levelabstand | in `/clarify` ausgeschlossen — XP hängt nur am Mob (FR-010) |
| Paragon- oder Prestige-Werte | nicht Teil von B06 |
| Klassenspezifisches Wachstum | B07 — ersetzt `level-growth` je Klasse |
| Spielertexte | `messages.yml`, über Message-Schlüssel (FR-038) |
