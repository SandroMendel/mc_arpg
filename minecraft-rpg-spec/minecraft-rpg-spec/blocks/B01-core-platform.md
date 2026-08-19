# B01 · Core & Plattform

| | |
|---|---|
| **Schicht** | 0 — Fundament |
| **Status** | Anforderungsfragen geklärt (2026-08-19) — bereit für `/specify`; Build-System/Configformat/DI bleiben `/plan`-Entscheidungen |
| **Abhängig von** | — |
| **Benötigt von** | alle |

## Zweck

Das technische Fundament, gegen das alle anderen Blöcke entwickelt werden.
Definiert, wie Module geladen werden, wie Nebenläufigkeit gehandhabt wird und wie
Konfiguration in den Code kommt.

## Umfang

- Plugin-Bootstrap und Lifecycle mit definierter Start-/Stopp-Reihenfolge
- Modul-/Service-Registry (leichtgewichtige Dependency Injection)
- Konfigurations-Loader mit Schema-Validierung und Hot-Reload
- Interner Event-Bus für die Kommunikation zwischen Blöcken
- **Scheduler-Abstraktion**: Sync und Async sind im Typ unterscheidbar
- Zentrale Fehlerbehandlung und strukturiertes Logging
- Öffentliche Plugin-API für spätere Erweiterungen

## Festgelegte Anforderungen

- Paper-API auf Minecraft 26.2, Java 25
- Modulstruktur: `rpg-core`, `rpg-persistence`, `rpg-platform`, `rpg-content`,
  `rpg-plugin`
- Abhängigkeitsrichtung strikt `plugin → platform → core`

## Architekturvorgaben

- Der globale Bukkit-Scheduler wird nie direkt verwendet. Die Abstraktion bietet
  ausschließlich **location- oder entity-gebundene** Scheduling-Aufrufe. Damit
  bleibt ein späterer Wechsel auf Folia möglich (ADR-007).
- Der Scheduler-Typ macht die Thread-Zugehörigkeit explizit, z. B.
  `TickTask` vs. `AsyncTask` — eine versehentliche Vermischung soll bereits beim
  Kompilieren auffallen.
- Startreihenfolge ist deterministisch und wird aus deklarierten Abhängigkeiten
  abgeleitet, nicht per Konvention.
- Fehlerhafte Konfiguration verhindert den Start (Fail-Fast), statt zur Laufzeit
  still falsch zu wirken.
- Ein Fehler in einem Modul darf nicht den gesamten Server-Tick abbrechen.

## Offene Fragen

- [ ] Build-System: Gradle (Kotlin DSL) oder Maven? → `/plan`-Entscheidung.
- [ ] Konfigurationsformat: YAML, HOCON oder TOML? → `/plan`-Entscheidung.
- [ ] Eigene DI-Lösung oder eine leichte Bibliothek? → `/plan`-Entscheidung.
- [x] **Öffentliche API für Drittplugins**: Vorerst intern, aber die
      Modul-/Service-Registry wird so gebaut, dass eine öffentliche API-Grenze
      später ohne Umbau ergänzbar ist. *(2026-08-19)*

## Akzeptanzkriterien (Entwurf)

- Der Server startet mit dem Plugin und ohne Fehler; alle Module melden ihren
  Status im Log.
- Ein absichtlich fehlerhafter Konfigurationswert verhindert den Start mit einer
  Meldung, die Datei, Pfad und erwarteten Wert nennt.
- Ein `/rpg reload` lädt Konfiguration neu, ohne Spielerdaten zu verlieren.
- Es existiert kein direkter Aufruf des Bukkit-Schedulers im gesamten Projekt
  (per Test oder Lint-Regel nachgewiesen).
