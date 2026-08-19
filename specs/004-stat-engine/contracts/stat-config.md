# Vertrag: `stats.yml` — Attributkonfiguration

**Feature**: `specs/004-stat-engine` | **Datum**: 2026-08-20

Geladen über B01s `ConfigLoader` mit Schemaprüfung. Ein Fehler bricht den Start ab und benennt
Attribut und Feld (FR-003, FR-014a, SC-009). Bei einem fehlerhaften Nachladen im laufenden Betrieb
bleibt der zuletzt gültige Stand wirksam (User Story 7, Szenario 4) — dieses Verhalten bringt
`AbstractConfigLoader` aus B01 bereits mit.

---

## Auslieferungsstand

Die Zahlen entsprechen der Tabelle aus `blocks/B04-stat-engine.md`. Sie sind ein Ausgangspunkt für
das Balancing, keine Festlegung.

```yaml
# B04 - attribute definitions. All balancing numbers live here, never in code (Principle V).
#
# Formula (ADR-008):  final = clamp( (base + sum(flat)) * (1 + sum(percent)), min, max )
# Percent modifiers are summed once, never chained.
attributes:
  health:
    base: 100.0
    min: 1.0          # a holder can never have a maximum of zero
    max: 2000.0
  defense:
    base: 0.0
    min: 0.0
    max: 300.0        # 300 -> exactly 75% mitigation via 100/(100+def)
  mana:
    base: 50.0
    min: 0.0
    max: 500.0
  physicalDamage:
    base: 5.0
    min: 0.0
    max: 150.0
  magicDamage:
    base: 5.0
    min: 0.0
    max: 150.0
  attackSpeed:
    base: 4.0         # vanilla base for a player
    min: 0.0
    max: 1024.0       # vanilla ceiling; the effective bound is modifier-band
    modifier-band: 0.50
  movementSpeed:
    base: 0.1         # vanilla base for a player
    min: 0.0
    max: 1.0          # vanilla ceiling; the effective bound is modifier-band
    modifier-band: 0.30
  abilityCooldown:
    kind: percent
    base: 0.0
    min: 0.0
    max: 0.40         # hard cap from ADR-008
```

---

## Felder je Attribut

| Schlüssel | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `kind` | `absolute` \| `percent` | nein, Vorgabe `absolute` | steuert Darstellung und Plausibilitätsprüfung |
| `base` | Dezimalzahl | **ja** | Basiswert ohne jeden Beitrag |
| `min` | Dezimalzahl | **ja** | Untergrenze des Endwerts |
| `max` | Dezimalzahl | **ja** | Obergrenze des Endwerts |
| `modifier-band` | Dezimalzahl ≥ 0 | nur für `attackSpeed` und `movementSpeed` | zulässige relative Abweichung vom Basiswert |

---

## Prüfregeln (alle beim Start, alle mit Startabbruch)

| Regel | Meldung nennt |
|---|---|
| Alle acht Attribute vorhanden | das fehlende Attribut namentlich |
| Unbekannter Attributschlüssel in der Datei | den unbekannten Schlüssel und die acht erlaubten |
| `min < max` | Attribut und beide Werte |
| `base` liegt in `[min, max]` | Attribut und alle drei Werte |
| Alle Werte endlich (kein `NaN`, kein `Infinity`) | Attribut und Feld |
| `health.min ≥ 1` | den unterschrittenen Wert |
| Für `kind: percent`: `min ≥ -1.0` und `max ≤ 1.0` | Attribut und die verletzte Grenze |
| `modifier-band > 0` für `attackSpeed` und `movementSpeed` | das betroffene Attribut |
| `modifier-band` bei einem anderen Attribut gesetzt | das Attribut, bei dem es nichts bewirkt |

Die letzte Regel ist kein Formalismus: ein `modifier-band` an `health` würde stillschweigend
ignoriert und sähe für den Betreiber aus wie eine wirksame Einstellung.

---

## Wirkung auf die Berechnung

```
roh      = (base + Σ flat) × (1 + Σ percent)
gebändigt = für attackSpeed/movementSpeed: clamp(roh, base × (1 − band), base × (1 + band))
endwert  = clamp(gebändigt, min, max)
```

Zwei Punkte, die leicht verwechselt werden:

- **`max` und `modifier-band` sind nicht dasselbe.** `max` ist die absolute Obergrenze des
  Endwerts, `modifier-band` die zulässige Abweichung vom **Basiswert**. Für Geschwindigkeiten ist
  das Band die wirksame Grenze, `max` nur ein Sicherheitsnetz gegen Werte, die Paper nicht mehr
  annimmt.
- **`abilityCooldown.max` ist der Cap aus FR-013.** Er steht in derselben Spalte wie jede andere
  Obergrenze, weil er im Modell auch nichts anderes ist — das ist der Sinn eines generischen
  Attributmodells.

---

## Nachladen im Betrieb

Eine gültige neue Konfiguration wirkt, sobald jeder Träger das nächste Mal neu berechnet wird. Die
Engine vermerkt beim Nachladen alle bekannten Träger — das ist der einzige Fall, in dem B04 über
alle Träger läuft, und er tritt nur auf ausdrückliche Anweisung eines Betreibers ein, nie im
Spielbetrieb.
