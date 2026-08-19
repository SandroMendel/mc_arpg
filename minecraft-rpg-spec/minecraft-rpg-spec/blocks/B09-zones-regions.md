# B09 · Zonen & Regionen

| | |
|---|---|
| **Schicht** | 2 — Welt & Content |
| **Status** | Entwurf — ADR-006 bestätigt (2026-08-19) |
| **Abhängig von** | B01 |
| **Benötigt von** | B10, B11, B13 |

## Zweck

Räumliche Gliederung der Spielwelt in Gebiete mit eigenen Regeln, Levelbereichen
und Inhalten.

## Umfang

- Zonendefinition mit Geometrie und Metadaten
- Räumlich indizierte Zonenerkennung
- Zonenregeln: Levelbereich, PvP, Schwierigkeitsmodifikator, Loot-Zuordnung,
  Respawn-Punkt
- Enter-/Leave-Ereignisse
- Zonenanzeige im HUD (B13)
- Reise-/Portalsystem zwischen Zonen und Welten

## Zentrale Architekturvorgabe

> **Eine `Zone` ist niemals eine `World`.**

Eine Zone wird modelliert als `(worldId, Geometrie)`. Jeder Zugriff läuft über
die Zonen-API. Damit ist „Zone X liegt in der Hauptwelt" gegenüber „Zone X ist
eine eigene Welt" eine Konfigurationszeile und kein Umbau. Diese Vorgabe gilt
unabhängig vom Ausgang von ADR-006.

## Topologie (ADR-006, bestätigt 2026-08-19)

- Eine große, handgebaute und **vorgenerierte** Kontinent-Welt mit hartem World
  Border für offene Level- und Sozialzonen; Richtgröße 6.000×6.000 bis
  10.000×10.000 Blöcke; keine Vanilla-Weltgenerierung zur Laufzeit
- Separate Welten nur für Instanzierbares: Dungeons, Bossräume, Tutorial
- Simulation-Distance 4–6, View-Distance 8–10
- Mehrere gleichwertige Zonen je Levelbereich, um Spieler zu verteilen

**Begründung:** Paper tickt alle Welten in einem Main-Thread. Mehrere Welten
bringen keine CPU-Parallelität — der Nutzen liegt allein in Entladbarkeit,
Instanzierbarkeit und Per-Welt-Regeln.

## Architekturvorgaben

- Zonenerkennung über räumlichen Index (Grid oder R-Tree), **nie** durch
  Iteration über alle Zonen.
- Zonenwechsel erzeugt genau ein Ereignis, auf das andere Blöcke reagieren.
- Zonen sind vollständig konfigurationsdefiniert und zur Laufzeit nachladbar.

## Offene Fragen

- [x] **ADR-006 bestätigt**. *(2026-08-19)*
- [x] **Anzahl Zonen zum Start**: 4–5 Zonen, aufsteigend gestaffelte
      Levelbereiche. *(2026-08-19)*
- [x] **Kartenbau**: Handgebaut. *(2026-08-19)*
- [ ] Zonengeometrie: Quader, Polygon oder Chunk-Menge?
- [ ] Reisesystem: Laufen, Portale, Wegpunkte, Teleport-Kosten?
- [ ] Werden Spieler unterhalb des Levelbereichs blockiert oder nur gewarnt?

## Akzeptanzkriterien (Entwurf)

- Zonenzugehörigkeit von 200 Spielern pro Tick zu ermitteln kostet < 0,5 ms.
- Eine Zone lässt sich per Konfiguration von der Hauptwelt in eine eigene Welt
  verschieben, ohne dass Code angefasst wird.
- Enter-/Leave-Ereignisse feuern zuverlässig, auch bei Teleport und Relogin.
