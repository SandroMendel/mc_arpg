# B02 · Persistenz-Layer

| | |
|---|---|
| **Schicht** | 0 — Fundament |
| **Status** | Anforderungsfragen geklärt (2026-08-19) — bereit für `/specify`; Zugriffsschicht bleibt `/plan`-Entscheidung |
| **Abhängig von** | B01 |
| **Benötigt von** | B03, B06, B11, B12 |

## Zweck

Sämtliche dauerhafte Datenhaltung in PostgreSQL — asynchron, gebatcht und ohne
Datenverlust.

## Umfang

- PostgreSQL-Anbindung mit Verbindungspool (HikariCP)
- Versionierte Schema-Migrationen (Flyway o. ä.)
- Repository-Schnittstellen je Aggregat (Spieler, Statistik, Item-Instanz …)
- Write-Behind-Mechanik mit Dirty-Tracking und Batch-Flush
- Serialisierung/Deserialisierung der Domänenobjekte
- Wiederanlauf bei Verbindungsverlust

## Festgelegte Anforderungen

- Alle Schreib- und Lesezugriffe erfolgen **asynchron** (ADR/NFR)
- Kein DB-Zugriff pro Spielereignis (Kill, XP-Tick, Schaden)
- Autosave-Intervall 30–60 s, konfigurierbar; zusätzlich bei Quit und Shutdown

## Architekturvorgaben

- **Write-Behind statt Write-Through**: Änderungen markieren das Aggregat als
  dirty; ein periodischer Flush schreibt gesammelt.
- Batch-Writes mit Prepared Statements; `UPSERT` statt Select-dann-Insert.
- Der Pool wird so dimensioniert, dass der Login-Pfad nie auf eine freie
  Verbindung warten muss.
- Bei Verbindungsverlust: Änderungen bleiben im Speicher gepuffert, Flush wird
  wiederholt. Kein stiller Verlust, kein Blockieren des Ticks.
- Beim Shutdown wird ein vollständiger, ggf. blockierender Flush ausgeführt —
  das ist der einzige erlaubte Blockierungspunkt und liegt außerhalb des Ticks.
- Zeitreihen-/Statistikdaten werden getrennt von Spielerzustand modelliert
  (unterschiedliche Schreibfrequenz und Aufbewahrung).

## Offene Fragen

- [ ] Zugriffsschicht: JDBC direkt, jOOQ oder ein leichtes Mapping? →
      /plan-Entscheidung, nicht blockierend für `/specify`.
- [x] **Wo läuft PostgreSQL**: gleiche Maschine wie der Server (Hardware-VPS
      noch offen, siehe `06-open-questions.md`, Abschnitt „Betrieb").
      *(2026-08-19)*
- [x] **Aufbewahrungsdauer Statistik-Rohdaten**: unbegrenzt/dauerhaft (passt zu
      Allzeit-Leaderboards in B12). *(2026-08-19)*
- [x] **Audit-Log für Admin-Eingriffe**: Ja, in derselben PostgreSQL-Instanz,
      eigene Tabelle (z. B. Item gegeben, Bann, Balancing-Änderung).
      *(2026-08-19)*

## Akzeptanzkriterien (Entwurf)

- Integrationstests laufen gegen eine echte PostgreSQL-Instanz (Testcontainers).
- Ein simulierter DB-Ausfall von 60 s führt zu keinem Datenverlust und zu keinem
  TPS-Einbruch.
- Ein Lasttest mit 200 simulierten Sitzungen zeigt keine Verbindungsengpässe.
- Migrationen laufen auf einer leeren und auf einer bestehenden Datenbank
  fehlerfrei durch.
