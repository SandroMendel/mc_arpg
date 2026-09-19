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
rpg-content     B16-Definitionen, Defaults, Schemas + Querverbindungsprüfung (serverfrei)
rpg-plugin      Bootstrap, Modulverdrahtung, plugin.yml
```

Abhängigkeitsrichtung strikt: `plugin → platform → core`, `core` kennt niemanden.

## B16 Content-Ownership und Ladegrenze

`rpg-content` ist der Owner der ausgelieferten B16-Content-Definitionen, Defaults, domänenspezifischen
Schemas und Querverbindungsprüfungen. Das Modul bleibt serverfrei, hängt nur von `rpg-core` ab und
kennt weder Bukkit noch Paper. Die neun gebündelten B16-Dateien sind:

`classes.yml`, `abilities.yml`, `progression.yml`, `combat.yml`, `zones.yml`, `mobs.yml`,
`items.yml`, `currency.yml` und `stats.yml` (für B16 nur die verwalteten Teilbereiche).

Die versionierten Defaults liegen unter `rpg-content/src/main/resources/` und werden vom Plugin beim
Bootstrap in den Plugin-Datenordner kopiert, ohne vorhandene Betreiberdateien zu überschreiben. Das
Plugin bündelt das Content-Modul in das deploybare Artefakt und verdrahtet den bestehenden Adminpfad
`/rpg reload` mit der gemeinsamen Reload-Transaktion.

Die YAML-Parser-Grenze liegt außerhalb von `rpg-content`: `rpg-platform` stellt den bestehenden
`YamlConfigLoader` und SnakeYAML bereit. `rpg-content` erhält den Parser als `DocumentReader` und führt
danach serverfrei die Versions-/Strukturprüfung, das typisierte Binding sowie die Querverbindungs- und
Domäneninvariantenprüfung für alle neun Quellen durch. `YamlConfigLoader` wird daher nicht in
`rpg-content` implementiert und `rpg-content` führt keinen zweiten YAML-Parser ein.

## B16 Snapshot- und Reload-Lebenszyklus

Eine B16-Generation wird als unveränderlicher `ContentSnapshot` aufgebaut. Die Reihenfolge ist:

```text
neun Dateien lesen und YAML parsen
        ↓
schemaVersion und feste Struktur prüfen
        ↓
Typen und Wertebereiche binden
        ↓
Querverbindungen und Domäneninvarianten prüfen
        ↓ Erfolg                              ↓ Fehler
vollständigen Snapshot bereitstellen         bisherigen Snapshot behalten
        ↓
gemeinsam in ConfigHandle-Generation        keine Teilveröffentlichung
stagen; Reload-Hooks anwenden
        ↓ Hook-Erfolg
eine neue Generation atomar publizieren
```

Der `B16ContentLoader` liefert den vollständig geprüften Snapshot; die Veröffentlichung bleibt in
der bestehenden `ConfigLoader`-/`ConfigHandle`-Transaktion. Alle registrierten Quellen werden zuerst
gemeinsam gestaged. Erst wenn die Reload-Hooks erfolgreich waren, wird die neue Generation sichtbar.
Schlägt Parsing, Schema-, Querverbindungs- oder Hook-Verarbeitung fehl, bleibt die vollständige
vorherige Generation aktiv. Bei einem Hook-Fehler stellt der Loader die vorherige Generation vor dem
Rollback-Hook wieder her; ein gemischter alter/neuer Content-Zustand wird nicht veröffentlicht.

## B15 Performance-/Observability-Fluss

`rpg-core` hält den begrenzten Messring und die Alert-Zustände ohne Bukkit. `rpg-platform` misst
öffentliche Paper-Tick-Events und verdrahtet kurze Scopes an B03, B05, B08b, B09, B10, B12, B13
und B14. `rpg-plugin` besitzt Konfiguration, Report-Zyklus, strukturierte Logs und den atomaren
Prometheus-Text-Export. Der Export läuft in einer einzigen serverweiten asynchronen One-Shot-Kette;
es gibt keine wiederkehrende Aufgabe je Spieler oder Entität.

Lasttest und Spark bleiben außerhalb des Plugin-JARs. Das versionierte Szenario und der Windows-
Runner erfassen das reale Hardwareprofil sowie die Rohartefakte; Paper liefert Spark bereits mit.

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
