# Vertrag · `abilities.yml`

Das Schema und die Prüfungen, die beim Start laufen. Jede verletzte Prüfung **verhindert den Start**
und nennt Fähigkeit und Feld (FR-001, Prinzip V).

---

## Aufbau

```yaml
# Was B08 selbst besitzt. Die Regenerationsraten stehen NICHT hier - sie sind Attribute
# und stehen in classes.yml (ADR-023).
runtime:
  global-cooldown-ms: 750
  regeneration:
    health-combat-factor: 0.20
    mana-combat-factor: 0.35

abilities:

  warrior.rage:
    kind: PASSIVE
    display-name-key: 'ability.warrior.rage.name'
    trigger: ON_DAMAGE_TAKEN
    cooldown-ms: 0
    max-rank: 5
    target: { mode: SELF }
    effects:
      - type: BUFF
        attribute: physicalDamage
        amount: 4.0
        per-rank: 1.5
        duration-ms: 6000

  warrior.whirl:
    kind: ACTIVE
    display-name-key: 'ability.warrior.whirl.name'
    item: 'IRON_AXE'
    mana-cost: 25.0
    cooldown-ms: 9000
    cast-time-ms: 0
    max-rank: 5
    target:
      mode: RADIUS
      range: 4.5
      max-targets: 8          # Pflichtfeld, kein Vorgabewert
    effects:
      - type: DAMAGE
        damage-type: PHYSICAL
        amount: 1.4           # Faktor auf physicalDamage, keine absolute Zahl
        per-rank: 0.2
      - type: KNOCKBACK
        amount: 0.6
        per-rank: 0.0

  rogue.second-life:
    kind: PASSIVE
    display-name-key: 'ability.rogue.second-life.name'
    item: 'TOTEM_OF_UNDYING'  # Marker, keine Eingabe
    trigger: ON_DEATH
    chance: 0.20
    cooldown-ms: 600000
    max-rank: 5
    target: { mode: SELF }
    effects:
      - type: HEAL
        amount: 0.35          # Anteil der Maximalgesundheit
        per-rank: 0.05
```

---

## Prüfungen beim Start

### Aufbau der Datei

| | Prüfung | Meldung enthält |
|---|---|---|
| **V1** | `global-cooldown-ms` ≥ 0 | Feldname |
| **V2** | beide Kampf-Faktoren in `[0, 1]` | Feldname und Wert |
| **V3** | jede Fähigkeits-ID kommt genau einmal vor (FR-005) | die doppelte ID |
| **V4** | jedes Pflichtfeld ist da | Fähigkeit und Feld |

### Definition einer Fähigkeit

| | Prüfung | Begründung |
|---|---|---|
| **V5** | `kind` ist `ACTIVE` oder `PASSIVE` | |
| **V6** | `ACTIVE` trägt **genau ein** `item`, `PASSIVE` trägt mindestens einen `trigger` (FR-003) | ohne Item ist eine aktive Fähigkeit nicht auslösbar; ohne Trigger wirkt eine passive nie. Zwei Items bei einer aktiven wären zwei Slots für eine Fähigkeit |
| **V6a** | `trigger` und `item` nehmen einen Wert **oder** eine Liste; eine leere Liste bricht ab (ADR-026) | Wut braucht zwei Trigger, Aufstieg & Fall zwei Marker. Eine leere Liste liest sich wie eine Entscheidung, ist aber keine - wer nichts will, lässt die Zeile weg |
| **V7** | `ACTIVE` trägt **keinen** `trigger`, `PASSIVE` **keine** `mana-cost` > 0 und keine `cast-time-ms` > 0 | ein Feld, das nie gelesen wird, ist ein Missverständnis und kein harmloser Überschuss |
| **V8** | `mana-cost` ≥ 0, `cooldown-ms` ≥ 0, `cast-time-ms` ≥ 0 (FR-008) | |
| **V9** | `chance` in `[0, 1]` | |
| **V10** | `max-rank` ≥ 1 | |
| **V11** | `display-name-key` ist nicht leer | keine hartkodierten Spielertexte (Prinzip V) |
| **V12** | `item` benennt ein vorhandenes Vanilla-Material (ADR-005) | ein Tippfehler wäre sonst erst im Spiel sichtbar |
| **V13** | `effects` ist nicht leer | eine Fähigkeit ohne Wirkung ist immer ein Versehen |

### Effekte

| | Prüfung | Begründung |
|---|---|---|
| **V14** | `type` benennt eines der zwölf Primitives (FR-004) | Meldung nennt Fähigkeit **und** Primitive |
| **V15** | `per-rank` ≥ 0 | ein Rangaufstieg nimmt nichts weg — dieselbe Regel wie B07s Zuwachsraten |
| **V16** | `BUFF` und `DEBUFF` tragen ein `attribute`, das eines der zehn ist | |
| **V17** | `DAMAGE` trägt einen `damage-type` | |
| **V18** | `STATUS_EFFECT` trägt einen vorhandenen Vanilla-Effekt | |
| **V19** | zeitlich wirkende Primitives tragen `duration-ms` > 0 | eine Dauer von null wäre ein Effekt, der im selben Tick endet |

### Zielbestimmung

| | Prüfung | Begründung |
|---|---|---|
| **V20** | `mode` benennt eine der sieben (FR-004) | |
| **V21** | jeder Modus außer `SELF` trägt `range` > 0 | |
| **V22** | `CONE` trägt `angle` in `(0, 180]` | |
| **V23** | **jeder Modus, der mehr als ein Ziel liefern kann, trägt `max-targets` ≥ 1** (FR-020) | **Pflichtfeld, kein Vorgabewert.** Ein Standardwert machte eine vergessene Zeile von einer bewussten Entscheidung ununterscheidbar — dieselbe Begründung, aus der B07 alle Attributfelder verlangt, auch die mit Null |
| **V24** | `SELF` trägt **kein** `max-targets` | siehe V7 |

### Haltende Fähigkeiten, Ladungen und Bedingungen *(ADR-025)*

| | Prüfung | Begründung |
|---|---|---|
| **V31** | `sustained: true` trägt eine `duration-ms` > 0 | eine haltende Fähigkeit ohne Dauer endet nie |
| **V32** | `sustained` nur bei `ACTIVE` | eine passive Fähigkeit hat keinen Slot, auf dem ein zweiter Rechtsklick sie beenden könnte |
| **V33** | `charges` ≥ 1; bei `charges` > 1 ist `charge-window-ms` > 0 Pflicht | ohne Fenster käme der Vorrat nie zurück |
| **V34** | `requires-behind-target` nur bei `PASSIVE` mit `ON_DAMAGE_DEALT` | die Bedingung ist nur im Moment des Treffers prüfbar |
| **V35** | `player-toggle` nur bei `PASSIVE` | eine aktive Fähigkeit schaltet man ab, indem man sie nicht auslöst |
| **V36** | `open-world-only` lädt, wird aber **nicht durchgesetzt**, solange B09 fehlt | der Start meldet das einmal als Hinweis, nicht als Fehler — sonst wäre eine korrekte Konfiguration ein Startabbruch |

### Intervall, Stapel und Filter *(ADR-025)*

| | Prüfung | Begründung |
|---|---|---|
| **V37** | `interval-ms` > 0 nur zusammen mit `duration-ms` > 0 | ein Intervall ohne Dauer wäre unendlich |
| **V38** | `interval-ms` ≤ `duration-ms` | sonst wirkt der Effekt kein einziges Mal |
| **V39** | `max-stacks` > 1 nur mit `interval-ms` | ein einmaliger Effekt stapelt nicht, er wirkt zweimal |
| **V40** | bei `max-stacks` > 1 ist `stack-cap` Pflicht | ohne Deckel wäre die Vergiftete Klinge bei genug Treffern unbegrenzt |
| **V41** | `damage-type` als **Filter** nur bei `SHIELD`, `EVADE` und `MITIGATE`; bei `DAMAGE` ist er Pflichtangabe und kein Filter | zwei Bedeutungen desselben Feldes gehören auseinandergehalten |
| **V42** | `METER` trägt `build-per-hit`, `idle-before-ms`, `decay-per-second` und ein `attribute` | ein Zähler ohne Aufbau oder ohne Zerfall ist keiner |
| **V43** | `origins` nur bei `SHIELD`, `EVADE` und `MITIGATE` | der Dispatcher liest ihn sonst nie — die Datei behauptete einen Filter, den es nicht gibt |
| **V44** | `MITIGATE` trägt `amount` in (0, 1] und `per-rank` ≤ 1 | ein Anteil über 1 ist keine stärkere Milderung, sondern eine Heilung, die als Schaden ankommt |

### Abgleich mit den Klassenbindungen

Diese vier laufen **nach** dem Laden von `classes.yml` und sind der Grund, warum B07 die IDs als
undurchsichtige Zeichenketten reisen lässt: dort gibt es B08 noch nicht, hier gibt es beide.

| | Prüfung | Begründung |
|---|---|---|
| **V25** | jede in einer Klassenbindung genannte ID ist definiert (FR-006) | Meldung nennt Klasse und ID |
| **V26** | die `kind` der Definition stimmt mit der der Bindung überein (FR-007) | zwei Wahrheiten über dieselbe Fähigkeit sind schlimmer als eine falsche |
| **V27** | jede Klasse hat **genau sechs** Fähigkeiten (FR-006a) | Die Aufteilung aktiv/passiv wird **nicht** geprüft: Warrior und Mage sind 4+2, der Rogue 3+3, und das ist Inhalt statt Struktur (ADR-025) |
| **V28** | genau eine Fähigkeit je Klasse trägt `unique`; ihre `kind` ist **nicht** eingeschränkt (ADR-022) | |

### Hotbar

| | Prüfung | Begründung |
|---|---|---|
| **V29** | die Marker-Items einer Klasse passen in die Slots 5 aufwärts, ohne Slot 0 bis 4 zu berühren | beim Mage sind es zwei; drei würden Slot 7 belegen und wären zulässig, vier nicht |
| **V30** | keine zwei Fähigkeiten einer Klasse fordern denselben Slot | |

---

## Was **nicht** geprüft wird

- **Ob eine Fähigkeit ausgewogen ist.** Ein Faktor von 40.0 lädt und macht den Warrior unspielbar
  stark. Balancing ist Betriebssache; das Schema prüft Form, nicht Maß.
- **Ob ein Primitive zum Trigger passt.** `LIFESTEAL` mit `ON_KILL` ist sinnlos, aber nicht falsch —
  eine Regel dafür wäre eine Liste erlaubter Paare, die bei jedem neuen Primitive mitwachsen müsste
  und beim ersten kreativen Einsatz im Weg stünde.
- **Ob die Freischaltstufen sinnvoll verteilt sind.** Das prüft B07 bereits, soweit es prüfbar ist
  (streng steigend, ab Level 1).
