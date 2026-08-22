# B04 · Attribut- & Stat-Engine

| | |
|---|---|
| **Schicht** | 1 — Regel-Engine |
| **Status** | Implementiert (2026-08-20) — 101 Aufgaben, 481 Tests grün, Spec unter `specs/004-stat-engine/` |
| **Abhängig von** | B01, B03 |
| **Benötigt von** | B05, B06, B07, B08, B10, B11, B13 |

## Zweck

Das zentrale Vertragswerk des Spiels: Wie entstehen aus Klasse, Level,
Ausrüstung und Effekten die konkreten Werte eines Spielers oder Mobs?

## Die zehn Attribute

| Code-Bezeichner | Deutsch | Kurzbeschreibung |
|---|---|---|
| `health` | Leben | Eigener HP-Wert (siehe ADR-003) |
| `healthRegen` | Lebensregeneration | Leben je Sekunde, im Kampf reduziert (ADR-023) |
| `defense` | Verteidigung | Mindert eingehenden Schaden |
| `mana` | Mana | Ressource für aktive Fähigkeiten |
| `manaRegen` | Manaregeneration | Mana je Sekunde, im Kampf reduziert (ADR-023) |
| `physicalDamage` | Physischer Schaden | Basis für Waffenschaden |
| `magicDamage` | Magischer Schaden | Basis für Fähigkeitsschaden |
| `attackSpeed` | Angriffsgeschwindigkeit | Angriffe pro Zeiteinheit |
| `movementSpeed` | Bewegungsgeschwindigkeit | Laufgeschwindigkeit |
| `abilityCooldown` | Abklingzeit | Reduktion der Fähigkeiten-Cooldowns |

## Umfang

- Generisches Attributmodell — **zehn Instanzen eines Systems, keine zehn
  Sonderfälle**. ADR-023 hat das eingelöst: zwei weitere Attribute kosteten zwei
  Enum-Konstanten und zwei Konfigurationsblöcke, sonst nichts in der Engine.
- Modifier-Modell mit Quellenverfolgung (Klasse, Level, Item, Buff, Aura, Zone)
- Stacking- und Berechnungsreihenfolge inkl. Caps
- Ereignisgesteuerte Neuberechnung mit unveränderlichem `StatSnapshot`
- Brücke zu Vanilla-Attributen für die Werte, die die Engine ohnehin kennt
- Dasselbe Modell gilt für Mobs (B10)

## Festgelegte Anforderungen

- **ADR-003**: HP ist eigenständig, `GENERIC_MAX_HEALTH` fix auf 20, angezeigte
  Health = `currentHP / maxHP * 20`
- **ADR-004**: Ausrüstung ist Stat-Quelle neben Klasse und Level

## Architekturvorgaben

- Neuberechnung **nur bei Änderung** einer Quelle, niemals pro Tick.
- Ergebnis ist ein unveränderlicher Snapshot; laufende Berechnungen (z. B. ein
  fliegendes Projektil) arbeiten mit dem Snapshot vom Zeitpunkt der Auslösung.
- Modifikatoren tragen eine Quellen-ID, damit sie beim Entfernen (Item abgelegt,
  Buff abgelaufen) gezielt entfernt werden können — kein Neuaufbau von Grund auf.
- Änderungen an `movementSpeed`, `attackSpeed` und `maxHealth` werden im selben
  Vorgang zum Vanilla-Attribut und zum HUD gespiegelt.
- Die gesamte Berechnungslogik liegt in `rpg-core` und ist ohne Server testbar.

## Offene Fragen — geklärt (2026-08-19)

- [x] **Stacking-Reihenfolge**: `(Base + Flat) × (1 + ΣPercent)`. Alle Flat-Boni
      addieren sich zur Basis, alle Prozent-Boni werden aufsummiert und einmal
      multipliziert (keine sequenzielle Verkettung).
- [x] **Defense-Formel**: Divisor-Modell `dmg × 100/(100+def)`. Kein separater
      harter Cap nötig, da die Formel asymptotisch gegen 100% Reduktion läuft.
- [x] **Skalierungsverhältnis**: Ausrüstung dominant. Level liefert nur einen
      kleinen festen Stat-Zuwachs pro Level (siehe B06), der Großteil der
      Endpower kommt aus Ausrüstung (konsistent mit ADR-004).
- [x] **attackSpeed / movementSpeed**: Beide über Vanilla-Attribute
      (`GENERIC_ATTACK_SPEED`, `GENERIC_MOVEMENT_SPEED`), durch Modifier aus
      B04 gesteuert.
- [x] **abilityCooldown**: Prozentuale Reduktion mit hartem Cap bei 40%.
- [x] **Wertebereiche und Caps je Attribut** (Start Lvl 1 → Max bei Best-Gear,
      Ausgangspunkt für Balancing, jederzeit über Content-Config änderbar):

  | Attribut | Start (Lvl 1) | Max (Best Gear) |
  |---|---|---|
  | Health | 100 | 2000 |
  | Defense | 0 | 300 (→ 75% Dmg-Reduktion) |
  | Mana | 50 | 500 |
  | Physical Damage | 5 | 150 |
  | Magic Damage | 5 | 150 |
  | Attackspeed | Vanilla-Basis | ±50% durch Modifier |
  | Movement Speed | Vanilla-Basis | ±30% durch Modifier |
  | Ability Cooldown | 0% | 40% (harter Cap) |

- [x] **Sekundärwerte** (Crit-Chance, Crit-Schaden, Lifesteal, Resistenzen):
      Vorerst **nein** — B04 bleibt bei den 8 Basisattributen. Kann später als
      eigener Block/ADR nachgezogen werden, ohne das generische Modifier-Modell
      zu ändern.

## Akzeptanzkriterien (Entwurf)

- Alle Formeln sind ohne laufenden Server unit-getestet, inkl. Randfälle
  (Wert 0, negative Modifikatoren, Cap-Überschreitung).
- Vollständiger Ausrüstungswechsel löst genau **eine** Neuberechnung aus.
- 200 Spieler mit je 8 Attributen und je ~20 Modifikatoren verbrauchen im
  Leerlauf messbar 0 ms Tick-Zeit.
- Ein Rundlauf (Item anlegen → Wert steigt → Item ablegen → Ausgangswert) endet
  exakt beim Ausgangswert, ohne Drift.

## Umsetzung (2026-08-20)

Spezifikation, Plan, Verträge und Aufgaben: `specs/004-stat-engine/`.
Umsetzungsentscheidungen: **ADR-013** in `02-decisions.md`.

**Namenshinweis zur Paper-API:** Die Attributkonstanten heißen inzwischen `MAX_HEALTH`,
`ATTACK_SPEED` und `MOVEMENT_SPEED` — das `GENERIC_`-Präfix in ADR-003 und in diesem Steckbrief
ist mit Minecraft 1.21.3 entfallen. Gleiche Attribute, aktuelle Namen.

**Nachträglich geklärt und hier festgehalten:**

- Aktuelles Leben und Mana gehören zu B04 und werden persistiert (Tabelle `rpg.character_stats`,
  Migration `V4_1`). Wann sich ein Stand ändert, entscheiden B05 und B08.
- B04 schaltet die natürliche Regeneration ab und fixiert die Sättigung. Das Umlenken von
  Vanilla-Schadensquellen bleibt bei B05 und wird durch einen Negativtest ferngehalten.
- Der Attributsatz ist geschlossen; ein neuntes Attribut ist eine Konstante plus ein
  Konfigurationseintrag.
- Die Caps (40 %, ±50 %, ±30 %) stehen als Pflichtfelder in `stats.yml` und sind für das Balancing
  änderbar; ein fehlender oder unplausibler Wert bricht den Start ab.

**Offen:** Der Lasttest-Nachweis auf einem echten Paper-Server (Abschnitt 8 des
Validierungsleitfadens) steht noch aus — sinnvoll gemeinsam mit den offenen Serverprüfungen aus
B02 und B03.
