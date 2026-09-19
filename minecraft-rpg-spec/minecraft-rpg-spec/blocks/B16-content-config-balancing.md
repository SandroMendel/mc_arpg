# B16 · Content-Konfiguration & Balancing

| | |
|---|---|
| **Schicht** | Querschnitt |
| **Status** | Clarify abgeschlossen (2026-09-12) — bereit für `/specify` |
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

## Offene Fragen — beantwortet am 2026-09-12

Die Antworten sind bindend und bilden die Grundlage für `/specify`. Die konkrete
Datenstruktur, die Dateipfade, die Schemas und die Umsetzungsschritte werden dort
ausgearbeitet.

- [x] **Konfigurationsformat: YAML.** B16 verwendet YAML als einziges
      Laufzeitformat. HOCON und TOML werden nicht als parallele Formate eingeführt.
      *(2026-09-12)*
- [x] **Dateistruktur: gebündelt pro Typ.** Klassen, Fähigkeiten, Mobs, Zonen,
      Items und weitere Domänen erhalten jeweils klar versionierte Dateien statt
      einer Datei pro einzelner Entität. Die genaue Gruppierung wird in `/specify`
      festgelegt. *(2026-09-12)*
- [x] **Externes Balancing-Werkzeug: ja, aber nicht zur Laufzeit.** YAML bleibt
      die versionierte Quelle der Wahrheit. Ein repo-lokales Analysewerkzeug darf
      daraus CSV, JSON und Markdown erzeugen; ein Spreadsheet und das Werkzeug
      selbst werden keine Plugin-Runtime-Abhängigkeit. Ein Rückimport wird nicht
      Teil der ersten B16-Ausbaustufe. *(2026-09-12)*
- [x] **Livebetrieb: versionierte Dateien plus `/rpg reload`.** Es gibt keinen
      In-Game-Editor. Betreiber ändern die versionierten Konfigurationsdateien;
      das Neuladen erfolgt mit `rpg.admin.reload`, wird auditiert und bleibt bei
      einem Fehler beim vorherigen gültigen Zustand. *(2026-09-12)*
- [x] **Umfang: alle spielrelevanten Werte aus B07–B11.** B16 schafft die
      gemeinsame Konfigurations- und Validierungsgrundlage für Klassen,
      Fähigkeiten, Mobs, Zonen, Items, Drops und Formeln. Die Umsetzung erfolgt
      in fachlich getrennten, überprüfbaren Scheiben. *(2026-09-12)*
- [x] **Besitz der Daten: getrennte Verantwortlichkeiten.** `rpg-content`
      besitzt die ausgelieferten Content-Definitionen und Defaults, `rpg-core`
      die generischen Konfigurations- und Validierungsverträge und `rpg-plugin`
      Bootstrap, externe Laufzeitdateien und Reload. Die genaue Paket- und
      Verzeichnisstruktur wird in `/specify` festgelegt. *(2026-09-12)*
- [x] **Migration: zunächst verhaltensneutral.** Bestehende Werte werden ohne
      bewusstes Rebalancing aus dem Code in YAML überführt. Regressionstests
      sichern, dass das Spielverhalten vor der späteren Balancing-Phase erhalten
      bleibt. *(2026-09-12)*
- [x] **Schema-Versionierung: explizit und fail-fast.** Jede gebündelte Datei
      erhält eine `schemaVersion`. Nicht unterstützte Versionen verhindern den
      Start beziehungsweise Reload; Migrationen sind explizite, getestete
      Schritte und verändern Dateien nicht stillschweigend beim Serverstart.
      *(2026-09-12)*
- [x] **Balancing-Baseline: aktuelle Werte zuerst.** B16 definiert zunächst
      keine neuen Zielwerte. Klassenkurven, Cooldowns, Mob-Werte, Drops, Preise
      und weitere Zahlen werden nach Tests sowie der vollständigen Content- und
      Mob-Bestandsaufnahme bewusst angepasst. *(2026-09-12)*
- [x] **Frühere Blockfragen bleiben zunächst erhalten.** Offene Fragen wie
      globale Cooldowns, Casting-Zeiten, Raritäten oder Affixe werden nicht
      stillschweigend durch B16 entschieden. B16 bildet sie erst ab, sobald der
      jeweils zuständige Block sie fachlich festgelegt hat. *(2026-09-12)*

## Akzeptanzkriterien (Entwurf)

- Jede Konfigurationsdatei hat ein Schema; ungültige Werte verhindern den Start.
- Ein Hot-Reload aller Inhalte während laufendem Spielbetrieb erzeugt keinen
  inkonsistenten Zustand und keinen Datenverlust.
- Es existiert keine spielrelevante Zahl im Java-Code (stichprobenartig geprüft).
