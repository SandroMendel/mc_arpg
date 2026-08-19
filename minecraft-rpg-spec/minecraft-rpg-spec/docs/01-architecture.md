# 01 · Architektur

## Grundprinzipien

1. **Thread-Trennung ist Gesetz.** Paper-/Bukkit-API ausschließlich im Server-Tick,
   Datenbank und I/O ausschließlich asynchron. Nie umgekehrt.
2. **Datengetrieben statt hartcodiert.** Klassen, Fähigkeiten, Mobs, Zonen, Items
   und Formelparameter kommen aus versionierten Konfigurationsdateien.
3. **Domänenlogik ist Bukkit-frei.** Formeln und Regeln sind reine Java-Klassen und
   ohne laufenden Server unit-testbar.
4. **Abstraktion vor Direktzugriff.** Scheduler, Rendering und Weltzugriff laufen
   über eigene Schnittstellen — nicht über statische Bukkit-Aufrufe.

## Schichtenmodell

```
┌─────────────────────────────────────────────────────────────┐
│ Schicht 3 — Meta & Präsentation                             │
│ B12 Statistiken/Leaderboards · B13 UI/HUD/i18n              │
│ B14 Commands/Permissions/Admin                              │
├─────────────────────────────────────────────────────────────┤
│ Schicht 2 — Welt & Content                                  │
│ B09 Zonen/Regionen · B10 Mobs & Spawning                    │
│ B11 Items/Ausrüstung/Loot                                   │
├─────────────────────────────────────────────────────────────┤
│ Schicht 1 — Regel-Engine (Domäne)                           │
│ B04 Stat-Engine · B05 Kampf-Pipeline · B06 Progression      │
│ B07 Klassen · B08 Fähigkeiten-Framework                     │
├─────────────────────────────────────────────────────────────┤
│ Schicht 0 — Fundament                                       │
│ B01 Core/Plattform · B02 Persistenz · B03 Spieler-Session   │
└─────────────────────────────────────────────────────────────┘

Querschnitt: B15 Performance/Observability · B16 Content-Config
             B17 Test & Deployment
```

## Blockübersicht

| ID | Block | Schicht | Hängt ab von |
|---|---|---|---|
| B01 | Core & Plattform | 0 | — |
| B02 | Persistenz-Layer | 0 | B01 |
| B03 | Spieler-Session & Datenlebenszyklus | 0 | B01, B02 |
| B04 | Attribut- & Stat-Engine | 1 | B01, B03 |
| B05 | Kampf- & Schadens-Pipeline | 1 | B04 |
| B06 | Progression (XP/Level) | 1 | B03, B04 |
| B07 | Klassen-System | 1 | B04, B06 |
| B08 | Fähigkeiten-Framework | 1 | B04, B05, B07 |
| B09 | Zonen & Regionen | 2 | B01 |
| B10 | Mobs & Horden-Spawning | 2 | B04, B05, B09 |
| B11 | Items, Ausrüstung & Loot | 2 | B04, B09, B10 |
| B12 | Statistiken & Leaderboards | 3 | B02, B05, B06 |
| B13 | UI, HUD & Texte | 3 | B04, B08, B09 |
| B14 | Commands, Permissions, Admin | 3 | alle |
| B15 | Performance & Observability | quer | B01 |
| B16 | Content-Konfiguration & Balancing | quer | B01 |
| B17 | Test & Deployment | quer | B01 |

## Modul-/Projektstruktur (Vorschlag)

```
rpg-core        Domänenmodell + Formeln, keine Bukkit-Abhängigkeit, voll testbar
rpg-persistence PostgreSQL, Repositories, Migrationen
rpg-platform    Paper-Adapter: Events, Scheduler, Entities, Rendering
rpg-content     Konfigurations-Ladelogik + Schema-Validierung
rpg-plugin      Bootstrap, Modulverdrahtung, plugin.yml
```

Abhängigkeitsrichtung strikt: `plugin → platform → core`, `core` kennt niemanden.

## Datenfluss Spielerwert (Beispiel)

```
Item angelegt / Level-Up / Buff
        ↓
StatModifier registriert (Quelle, Typ, Wert)
        ↓
StatRecalculation (nur bei Änderung, nie pro Tick)
        ↓
StatSnapshot (unveränderlich, im Session-Cache)
        ↓                       ↓
Kampf-Pipeline (B05)     Vanilla-Attribut-Sync + HUD (B13)
```

## Persistenzstrategie in Kurzform

- **Write-Behind**: Änderungen markieren Session als dirty; Batch-Flush periodisch
  und bei Quit. Kein DB-Zugriff pro Kill, XP-Tick oder Schadensereignis.
- **Cache-Autorität**: Solange ein Spieler online ist, ist der Speicher die
  Wahrheit, nicht die Datenbank.
- Details in `blocks/B02-persistence.md` und `blocks/B03-player-session.md`.
