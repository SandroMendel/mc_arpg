# B03 · Spieler-Session & Datenlebenszyklus

| | |
|---|---|
| **Schicht** | 0 — Fundament |
| **Status** | Offene Fragen geklärt (2026-08-19) — bereit für `/specify` |
| **Abhängig von** | B01, B02 |
| **Benötigt von** | B04, B06, B07, B08, B11, B12 |

## Zweck

Verwaltet den Übergang zwischen Datenbank und Speicher: Laden beim Join, Halten
während der Sitzung, Schreiben beim Verlassen. Erfahrungsgemäß der Block, an dem
RPG-Plugins Datenverlust produzieren — deshalb eigenständig spezifiziert.

## Umfang

- Ladeablauf: Join → async Load → Session bereitstellen → Spieler freigeben
- Entladeablauf: Quit → Flush → Eviction
- Verhalten, wenn das Laden noch läuft oder fehlschlägt
- Session-Cache als autoritative Quelle für Online-Spieler
- Autosave und Notfall-Flush beim Shutdown
- Datenversionierung und Migration alter Spielerstände

## Architekturvorgaben

- Solange die Sitzung nicht geladen ist, wird der Spieler in einen sicheren
  Zustand versetzt (bewegungsgesperrt und schadensimmun) statt mit
  Standardwerten weiterzuspielen. Inkonsistente Werte sind schlimmer als eine
  kurze Wartezeit.
- Schlägt das Laden fehl, wird der Spieler mit klarer Meldung abgewiesen —
  **niemals** mit einem leeren Profil eingelassen, das anschließend den
  bestehenden Datensatz überschreibt.
- Relogin während eines laufenden Flush wird korrekt sequenziert (kein
  Wettlauf zwischen altem Flush und neuem Load).
- Session-Objekte werden garantiert entfernt; kein Speicherleck bei
  Langzeitbetrieb.
- Der Ladepfad bündelt alle benötigten Daten in möglichst wenigen Abfragen.

## Offene Fragen

- [x] **Charakter-Slots**: 3 pro Account (ein Slot je Klasse: Warrior, Mage,
      Rogue). *(2026-08-19, siehe B07)*
- [x] **Kick/Timeout mitten im Kampf**: Sofortiger Flush bei jedem
      Sitzungsabbruch (Quit, Kick, Timeout), nicht nur bei geplantem Quit.
      *(2026-08-19)*
- [x] **Offline-Zugriff auf Spielerdaten**: Ja — eigener Read-Only-Lesepfad
      ohne Session, benötigt für Leaderboards (B12) und Admin-Tools (B14).
      *(2026-08-19)*
- [x] **Ladezeit-Zielwert beim Join**: < 500 ms. *(2026-08-19)*

## Akzeptanzkriterien (Entwurf)

- Nach `kill -9` des Servers geht höchstens ein Autosave-Intervall verloren.
- 200 gleichzeitige Joins führen zu keinem Timeout und keinem TPS-Einbruch.
- Schneller Relogin (Quit und Join innerhalb 1 s) erzeugt keinen Datenverlust
  und keinen doppelten Session-Eintrag.
- Ein simulierter DB-Fehler beim Login führt zu einem sauberen Kick, nicht zu
  einem überschriebenen Profil.
