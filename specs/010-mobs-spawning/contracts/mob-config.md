# Vertrag · `mobs.yml`

Die eine neue Konfigurationsdatei dieses Blocks. Beim Start gegen ein Schema geprüft; ein Fehler
bricht den Start ab und nennt Datei, Schlüssel und Grund (FR-002, Prinzip V Fail-Fast).

**Kein Bezeichner einer einzelnen Art steht im Code** (FR-003). Was unten `greenfields.rotling`
heißt, ist ein Beispiel und kein Vertrag.

---

## Aufbau

```yaml
# Die vier harten Grenzen. Keine Zielwerte - was hier steht, wird unter keiner
# Bedingung ueberschritten, auch nicht bei ploetzlichem Spieleransturm (FR-013).
#
# server-wide haelt AUCH DANN, wenn die Summe der Zonenbudgets darueber liegt (FR-013a).
# Sechs Zonen zu je 200 waeren 1200 - hier stehen 800, und 800 gelten.
budget:
  server-wide: 800
  per-zone: 130
  per-chunk: 12
  per-player: 25

# Wie schnell sich eine geraeumte Horde wieder fuellt, und wann Leeres weggeraeumt wird.
horde:
  respawn-interval-ms: 2000
  # Dichte je zusaetzlichem Spieler in der Zone. Beruehrt NIE Attribute (FR-026).
  density-per-player: 0.20
  # Nach dem letzten Spieler in der Zone.
  cleanup-after-seconds: 60
  # Wer weiter weg ist als das von jedem Spieler, wird entfernt - nicht schlafen gelegt.
  cleanup-radius: 96
  # Abstand, in dem eine eigene Zielzuweisung fruehestens wieder anfassen darf (FR-035).
  retarget-interval-ms: 500

kinds:
  greenfields.rotling:
    base: ZOMBIE
    level: 3
    display-name-key: 'mob.greenfields.rotling.name'
    attributes: { health: 40.0, defense: 2.0, physical-damage: 6.0 }
    follow-range: 24.0
    xp: 12
    coins: 4

  greenfields.warden-of-the-field:
    base: ZOMBIE
    level: 10
    display-name-key: 'mob.greenfields.warden.name'
    attributes: { health: 600.0, defense: 30.0, physical-damage: 24.0 }
    follow-range: 32.0
    xp: 400
    coins: 250
    boss: true

hordes:
  greenfields:
    areas:
      greenfields-east:
        - { kind: 'greenfields.rotling', weight: 3 }
      greenfields-west:
        - { kind: 'greenfields.rotling', weight: 1 }
    boss:
      kind: 'greenfields.warden-of-the-field'
      area: 'greenfields-east'
      offset: { x: 0, y: 0, z: 0 }
      respawn-minutes: 30
```

---

## Regeln, die das Schema durchsetzt

| Regel | Warum |
|---|---|
| `base` ist ein bekannter Vanilla-Entity-Typ | Sonst entstünde beim ersten Spawn ein Fehler im Tick statt beim Start (FR-002) |
| Mehrere Arten dürfen dieselbe `base` haben | Acht Arten je Region auf wenigen Entities — das ist der Normalfall (FR-004) |
| `display-name-key` ist ein Schlüssel, kein Text | Prinzip V, keine hartcodierten Spielertexte (FR-010) |
| Der Schlüssel ist in `messages.yml` vorhanden | Sonst sieht ein Spieler eine leere Zeile über einem Kopf |
| `hordes.<zone>` ist eine Zone aus `zones.yml` | Eine Horde ohne Zone stünde nirgends |
| `areas.<key>` ist ein `SpawnArea`-Schlüssel dieser Zone | B09 besitzt die Geometrie; B10 verweist nur (FR-011) |
| `kind` verweist auf einen Eintrag unter `kinds` | Ein Tippfehler wäre sonst eine still leere Horde |
| `weight` ≥ 1 | Ein Gewicht von 0 heißt „nie" und ist als Zeile ehrlicher weggelassen |
| Höchstens ein `boss` je Zone | FR-029 |
| Die Bossart trägt `boss: true` | Sonst wäre das Etikett bedeutungslos |
| Alle Budgets > 0 | Ein Budget von 0 heißt „keine Horde" und gehört dann nicht konfiguriert |
| `per-chunk` ≤ `per-zone` | Eine Grenze, die eine engere nie erreicht, ist eine Zahl ohne Wirkung |
| `per-zone` darf `server-wide` **überschreiten** | Und trotzdem gilt `server-wide` (FR-013a). Kein Fehler beim Start: sechs Zonen zu je 200 sind eine legitime Verteilung, solange nie 800 gleichzeitig stehen |

---

## Was **nicht** in dieser Datei steht

- **Geometrie.** Wo ein Bereich liegt, steht in `zones.yml`. Zwei Orte für dieselbe Geometrie hieße,
  dass einer beim nächsten Verschieben vergessen wird (research.md R10).
- **Anzeigetexte.** `messages.yml`.
- **Beute über Coins hinaus.** B11.
- **Ob unterdrückt wird.** Es wird unterdrückt (FR-018c) — es gibt dafür keinen Schalter. Ein
  Schalter wäre ein Weg, das Budget zu umgehen: die ganze Leistungszusage dieses Blocks hängt
  daran, dass nichts anderes Kreaturen erzeugt. Einmal auf `false` gestellt und vergessen, läuft der
  Server voll, und niemand sieht die Ursache. Sollte später eine Welt ohne RPG dazukommen — heute
  gibt es genau eine, und alle sechs Regionen liegen darin —, wird die Ausnahme mit dem Wissen
  gebaut, wofür sie da ist, und nie für eine Zonenwelt.

---

## Nachladen

`mobs.yml` folgt dem Muster der anderen Konfigurationen dieses Projekts: **Neues gilt für Neues,
Laufendes behält, womit es gestartet ist.** Eine bereits stehende Kreatur mitten im Kampf
umzurechnen wäre die einzige Alternative — und sie würde einem Spieler die Zahlen unter den Händen
verändern.

Was ein Nachladen sofort ändert: Budgets, Nachschubrate, Aufräumfristen, und welche Arten *ab jetzt*
gesetzt werden.
