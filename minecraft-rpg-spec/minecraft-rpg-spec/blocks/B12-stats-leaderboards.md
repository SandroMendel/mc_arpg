# B12 · Statistiken & Leaderboards

| | |
|---|---|
| **Schicht** | 3 — Meta |
| **Status** | Entwurf |
| **Abhängig von** | B02, B05, B06 |
| **Benötigt von** | B13, B14 |

## Zweck

Erfassung von Spielerkennzahlen und deren Aufbereitung zu Ranglisten.

## Umfang

- Metrikerfassung im Gameplay-Pfad (Kills, Tode, Spielzeit, Zonenfortschritt,
  höchster Schaden, Bosskills)
- Aggregation und Speicherung in PostgreSQL
- Leaderboard-Abfragen mit Caching
- Persönliche Statistikansicht
- Zeiträume: allzeit und/oder saisonal

## Architekturvorgaben

- Metrikerfassung ist im Hot Path ein reiner Zählerinkrement im Speicher — kein
  DB-Zugriff, keine Berechnung.
- Aggregation läuft asynchron und periodisch, nicht bei jedem Ereignis.
- Leaderboards werden über **Materialized Views mit periodischem Refresh**
  bereitgestellt und zusätzlich im Speicher gecacht. Keine Live-Abfrage beim
  Öffnen eines Menüs.
- Statistik-Rohdaten werden getrennt vom Spielerzustand modelliert
  (unterschiedliche Schreibfrequenz, unterschiedliche Aufbewahrung).

## Offene Fragen

- [x] **Erfasste Metriken**: Level, Coins, Mob-Kills, Tode, Spielzeit,
      Bosskills. *(2026-08-19)*
- [ ] Welche davon werden öffentlich als Leaderboard gezeigt (vs. nur privat
      sichtbar)? → bei `/specify` B12 festzulegen.
- [x] **Zeiträume**: Allzeit + saisonal (Saisonlänge bei `/specify`
      auszuarbeiten). *(2026-08-19)*
- [ ] Refresh-Intervall der Leaderboards?
- [ ] Anzeigeform: Chat, GUI, Hologramm im Hub?
- [x] **Aufbewahrungsdauer der Rohdaten**: unbegrenzt/dauerhaft, siehe B02.
      *(2026-08-19)*

## Akzeptanzkriterien (Entwurf)

- Metrikerfassung ist im Lasttest messbar kostenneutral im Tick.
- Ein Leaderboard-Aufruf durch 50 Spieler gleichzeitig erzeugt höchstens eine
  Datenbankabfrage.
- Statistikwerte bleiben nach Serverneustart konsistent.
