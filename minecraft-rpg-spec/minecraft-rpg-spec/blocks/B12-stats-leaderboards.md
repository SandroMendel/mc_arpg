# B12 · Statistiken & Leaderboards

| | |
|---|---|
| **Schicht** | 3 — Meta |
| **Status** | **Gebaut** *(2026-08-30)* — ausspezifiziert in [`specs/012-stats-leaderboards/`](../../../specs/012-stats-leaderboards/spec.md), entschieden in ADR-040 bis ADR-049. Offen ist nur die Serverabnahme |
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
- [x] **Öffentlich vs. privat**: öffentlich sind die *Summen* — Mob-Kills,
      Bosskills, Tode, aktive Spielzeit, höchster Schaden, dazu Level und Coins.
      **Privat sind drei Werte**, und zwar die *Aufschlüsselungen* und die zweite
      Uhr: woran jemand stirbt (Tode je Ursache), wo er sich aufhält (Spielzeit
      je Zone) und wie lange er verbunden ist (Onlinezeit). Sie sind nicht
      geheim, sie sind schlicht nicht die Sache der anderen — ADR-043.
      *(2026-08-29)*
- [x] **Zeiträume**: Allzeit + saisonal (Saisonlänge bei `/specify`
      auszuarbeiten). *(2026-08-19)*
- [x] **Refresh-Intervall**: konfigurierbar, ausgeliefert mit fünf Minuten
      (`statistics.yml`, `leaderboards.refresh-interval-seconds`). Kürzer heißt
      nicht aktueller für den Spieler, sondern nur mehr Last — bei einer
      Rangliste ist fünf Minuten die Spanne, in der sich ohnehin nichts
      Sichtbares ändert. Jede Ansicht nennt das Alter ihres Standes, damit
      „veraltet" nicht als „kaputt" gelesen wird (FR-032). *(2026-08-30)*
- [x] **Anzeigeform**: alle drei, mit klarer Aufgabenteilung. **GUI** ist der
      Hauptweg (`/top` für die Ranglisten, `/stats` für das eigene und ein
      fremdes Profil, `/top score` für die Saisonwertung samt Aufschlüsselung).
      **Chat** trägt nur, was einen Spieler persönlich betrifft — ein offener
      Belohnungsanspruch beim Wiederkommen. Das **Hologramm im Hub** ist
      optional und in `statistics.yml` abschaltbar; es liest denselben
      Speicherstand wie die Fenster und stellt keine eigene Abfrage.
      *(2026-08-30)*
- [x] **Aufbewahrungsdauer der Rohdaten**: unbegrenzt/dauerhaft, siehe B02.
      *(2026-08-19)*

## Akzeptanzkriterien (Entwurf) — und wie sie ausgegangen sind

- Metrikerfassung ist im Lasttest messbar kostenneutral im Tick.
  → **Gemessen, aber nicht im Lasttest.** Der Lasttest gehört seit ADR-031 zu
  B15. Was hier läuft, ist eine wiederholbare Messung der eigenen Rechenarbeit
  ohne Volllast (`CaptureBenchmarkTest`): ein vollständiger Ereignisdurchlauf —
  Empfänger bestimmen, je Empfänger zählen, Tod mit Ursache, Höchstschaden —
  liegt bei rund 2,6 µs gegen ein Budget von 50 µs.
- Ein Leaderboard-Aufruf durch 50 Spieler gleichzeitig erzeugt höchstens eine
  Datenbankabfrage.
  → **Übertroffen: es sind null.** Gelesen wird aus dem Speicherstand, gefüllt
  wird er im Auffrischungstakt. Die Abfrage fällt dort an, wo Zeit ist, und
  nicht dort, wo ein Spieler wartet.
- Statistikwerte bleiben nach Serverneustart konsistent.
  → **Gehalten**, und mehr als das: ein Saisonwechsel löscht keine Rohdaten
  (FR-057, ADR-044), und ein Saisonabschluss wird beim Start nachgeholt, wenn
  der Server über das Enddatum hinweg aus war (FR-058).

## Was dieser Block darüber hinaus geworden ist

Vier Dinge, die im Entwurf oben nicht standen und beim Ausspezifizieren
dazugekommen sind:

- eine **gewichtete Saison-Gesamtwertung** mit sichtbarem Zwischenstand und
  einer Belohnung, die als **Anspruch** wartet statt zu verfallen (ADR-045,
  ADR-046),
- die **Trennung von Summe und Aufschlüsselung** in der Sichtbarkeit — die Summe
  ist öffentlich, die Aufschlüsselung gehört dem Spieler (ADR-043),
- ein **zweiter Schreibweg** auf die Statistiktabelle für Maximum-Metriken, der
  B02s Fundament additiv erweitert (ADR-040),
- und die Zusage, dass eine **neu erfasste Zählermetrik von allein** zur
  Rangliste wird — ohne Code, ohne Konfiguration, ohne Migration. Ihre Grenze
  ist benannt: eine *Maximum*-Metrik braucht sehr wohl eine Migration, und ein
  Test hält das fest, statt es zu verschweigen (ADR-049).
