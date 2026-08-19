# B16 · Content-Konfiguration & Balancing

| | |
|---|---|
| **Schicht** | Querschnitt |
| **Status** | Entwurf |
| **Abhängig von** | B01 |
| **Benötigt von** | B07, B08, B09, B10, B11 |

## Zweck

Alle Inhalte und Balancing-Zahlen liegen außerhalb des Codes — versioniert,
validiert und ohne Neustart nachladbar.

## Umfang

- Konfigurationsstruktur für Klassen, Fähigkeiten, Mobs, Zonen, Items, Formeln
- Schema-Definition und Validierung beim Start
- Hot-Reload mit Konsistenzprüfung
- Versionierung und Migration von Konfigurationsdateien
- Werkzeuge zur Balancing-Auswertung (Schadenskurven, TTK je Level)

## Architekturvorgaben

- **Fail-Fast**: Ein Schemafehler verhindert den Start und nennt Datei, Pfad und
  erwarteten Wert. Stilles Zurückfallen auf Standardwerte ist unzulässig.
- Ein fehlgeschlagener Hot-Reload lässt den vorherigen, gültigen Zustand aktiv —
  es entsteht nie ein halb geladener Zustand.
- Alle Zahlen, die das Spielgefühl bestimmen, gehören hierher: XP-Kurve,
  Schadensformel-Parameter, Defense-Kurve, Mob-Werte, Drop-Raten, Cooldowns.
- Konfigurationsdateien liegen unter Versionskontrolle und werden wie Code
  behandelt.

## Offene Fragen

- [ ] Format: YAML, HOCON oder TOML? (siehe auch B01)
- [ ] Eine Datei je Entität oder gebündelte Dateien je Typ?
- [ ] Wird ein externes Balancing-Werkzeug (Tabelle) genutzt und importiert?
- [ ] Wer darf Konfiguration im Livebetrieb ändern?

## Akzeptanzkriterien (Entwurf)

- Jede Konfigurationsdatei hat ein Schema; ungültige Werte verhindern den Start.
- Ein Hot-Reload aller Inhalte während laufendem Spielbetrieb erzeugt keinen
  inkonsistenten Zustand und keinen Datenverlust.
- Es existiert keine spielrelevante Zahl im Java-Code (stichprobenartig geprüft).
