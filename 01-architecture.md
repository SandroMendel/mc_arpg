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

**Stand** heißt: gebaut, verdrahtet und mit grünem `FullBootstrapTest` — nicht „Modultests grün".
Der Unterschied ist in diesem Projekt schon zweimal aufgefallen.

| ID | Block | Schicht | Hängt ab von | Stand |
|---|---|---|---|---|
| B01 | Core & Plattform | 0 | — | gebaut |
| B02 | Persistenz-Layer | 0 | B01 | gebaut |
| B03 | Spieler-Session & Datenlebenszyklus | 0 | B01, B02 | gebaut |
| B04 | Attribut- & Stat-Engine | 1 | B01, B03 | gebaut |
| B05 | Kampf- & Schadens-Pipeline | 1 | B04 | gebaut |
| B06 | Progression (XP/Level) | 1 | B03, B04 | gebaut |
| B07 | Klassen-System | 1 | B04, B06 | gebaut |
| B08 | Fähigkeiten-Framework | 1 | B04, B05, B07 | gebaut |
| B08b | Währung & Kontostand | 1 | B03, B04 | gebaut |
| B09 | Zonen & Regionen | 2 | B01 | gebaut |
| B10 | Mobs & Horden-Spawning | 2 | B04, B05, B09 | gebaut |
| B11 | Items, Ausrüstung & Loot | 2 | B03, B04, B05, B06, B07, B08b, B09, B10 | gebaut, Serverabnahme offen |
| B12 | Statistiken & Leaderboards | 3 | B02, B05, B06 | gebaut, Serverabnahme offen |
| B13 | UI, HUD & Texte | 3 | B04, B08, B09 | offen |
| B14 | Commands, Permissions, Admin | 3 | alle | gebaut, Serverabnahme offen |
| B15 | Performance & Observability | quer | B01 | offen |
| B16 | Content-Konfiguration & Balancing | quer | B01 | offen |
| B17 | Test & Deployment | quer | B01 | offen |

> B08b ist nachträglich entstanden und stand bisher nur in den Abhängigkeiten von B11, nicht als
> eigene Zeile. Er ist hier ergänzt: ein Block, auf den andere verweisen, der aber in der
> Übersicht fehlt, ist beim Lesen ein Tippfehler und keine Entscheidung.

## B14 · Kommandogerüst und Rechtebaum

B14 liegt als dünne, einheitliche Schale über den öffentlichen Schnittstellen der übrigen Blöcke:

- `RpgCommand` beschreibt den Baum aus Wurzel, Unterkommando, Argumenten, Spielerbezug, Recht und
  Sperrzeit. `RpgPlugin` sammelt die sechs Spielerkommandos und die Admin-Gruppen und hängt sie
  unter einer einzigen optionalen `/rpg`-Wurzel zusammen.
- `CommandTree` baut daraus den Brigadier-Baum und registriert ihn über den Paper-Lifecycle. Die
  einzige Rechteprüfung sitzt am Knoten; `requires` steuert damit Ausführbarkeit und Sichtbarkeit
  in der Vervollständigung zugleich.
- `ArgumentType` ist die gemeinsame Quelle für Prüfung und Vorschlag. Vorschläge werden am Präfix
  gefiltert und auf 50 Einträge begrenzt, damit ein leerer Tab-Druck keine vollständige Namens- oder
  Konfigurationsmenge durch den Server schickt.
- Schreibende Adminpfade laufen über vorhandene öffentliche Block-APIs, protokollieren über
  `AdminAudit` und greifen asynchron auf die Persistenz zu. `/rpg inspect` und `/rpg audit` sind
  dagegen reine Lesepfade; die Einsicht erzeugt weder Cache- noch Audit-Seiteneffekte.
- Die ausgelieferten Nachrichtenschlüssel liegen in `messages.yml` und `messages_de.yml`; der
  Bootstrap prüft beide Sprachsätze für die B14-Schlüssel, bevor der Server Spieler annimmt.

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
Stufenaufstieg / Level-Up / Verschleiß        Buff / Trank / Zonenwirkung
        ↓                                              ↓
BaseStatContributor (Klasse, Level, Gear)      StatModifier (Quelle, Typ, Wert)
        ↓                                              ↓
        └──────────────► StatRecalculation ◄───────────┘
                    (nur bei Änderung, nie pro Tick)
                                ↓
                StatSnapshot (unveränderlich, im Session-Cache)
                    ↓                            ↓
        Kampf-Pipeline (B05)          Vanilla-Attribut-Sync + HUD (B13)
```

> **Korrigiert am 2026-08-29.** Hier stand ursprünglich „Item angelegt →
> StatModifier". Das ist seit ADR-017 und ADR-039 in zwei Punkten falsch, und
> beide sind für den nächsten Block wichtig:
>
> 1. **Ausrüstung ist kein Modifikator, sondern ein Grundwert.** Sie kommt über
>    `ClassStatContributor` als `addBase` herein, nicht als `SourceKind.EQUIPMENT`.
>    Der Grund steht in B07: das Modifikatorband aus B04 (±30 %) würde die 1385
>    Lebensenergie der Höchststufe stillschweigend abschneiden.
> 2. **Ein Item legt gar keine Werte an.** Es trägt seine Vorlagen-ID und sonst
>    nichts (ADR-004 in der Fassung von ADR-027); die einzige Stelle, an der B11
>    den Ausrüstungsbeitrag beeinflusst, ist der Verschleißfaktor — er
>    multipliziert den Stufenbeitrag über die Naht `GearConditionFactor`
>    (FR-047, FR-080).
>
> Was weiterhin als Modifikator läuft: Tränke, Fähigkeitsbuffs und
> Zonenwirkungen — alles, was zeitlich begrenzt ist.

## Persistenzstrategie in Kurzform

- **Write-Behind**: Änderungen markieren Session als dirty; Batch-Flush periodisch
  und bei Quit. Kein DB-Zugriff pro Kill, XP-Tick oder Schadensereignis.
- **Cache-Autorität**: Solange ein Spieler online ist, ist der Speicher die
  Wahrheit, nicht die Datenbank.
- Details in `blocks/B02-persistence.md` und `blocks/B03-player-session.md`.
