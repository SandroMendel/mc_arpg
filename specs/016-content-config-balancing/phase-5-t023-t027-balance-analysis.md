# B16 Phase 5 · T023–T027 Deterministische Balance-Auswertung

Status: **implementiert und serverfrei geprüft** (2026-09-19).

## Verträge

`tools/b16-balance/fixtures/current-baseline-v1.yml` ist eine versionierte YAML-Fixture mit
`schemaVersion: 1`, `fixtureVersion: 1` und `baselineVersion: 1`. Sie enthält die stabile
Szenario-ID `b16-current-baseline-v1`, die neun B16-Quelldateien und drei reine
Bestandsmetriken:

- eine physische Schadenskurve für die vorhandenen Klassen `WARRIOR`, `ROGUE` und `MAGE` über
  die vorhandenen Level 1, 10, 35 und 60;
- TTK für `WARRIOR` auf Level 10 gegen vorhandene Mob-Kinds;
- direkte Baseline-Werte mit Einheiten für globalen Cooldown, Fallschaden-Cap, Mob-Budget,
  Verteidigungs-Cap und Level-2-XP.

Die Fixture erfindet kein Balanceziel. IDs und Werte stammen ausschließlich aus
`rpg-content/src/main/resources`. Die TTK-Auswertung verwendet die vorhandenen Core-Verträge:
`damage * 100 / (100 + defense)` und Angriffstempo als Angriffe pro Sekunde. Der Ergebnisvertrag
trägt `resultVersion: 1`, `status: VALID`, Szenario-ID, verwendete IDs, Baseline-Werte, Quellen,
`baselineId` und einen deterministischen SHA-256-Source-Hash. Der Hash umfasst Runtime-YAMLs und
Fixture in fester Reihenfolge.

## Werkzeug und Fehlergrenze

`tools/b16-balance/scripts/analyze-b16.ps1` arbeitet lokal und netzwerkfrei mit PowerShell. Es
verwendet weder Minecraft/Paper, Plugin-Classloader noch Spreadsheet-Software. Jeder Lauf erzeugt
bei Erfolg deterministische `b16-balance.csv`, `b16-balance.json` und `b16-balance.md`.

Ungültiges YAML, falsche Schema-/Fixture-/Baseline-Version, fehlende Szenariodaten, unbekannte IDs
und unbekannte Dokumentpfade liefern Nicht-Erfolg mit Eingabepfad und Ursache. Vor vollständiger
Validierung wird kein Ausgabeverzeichnis beziehungsweise kein als `VALID` markierter Report
erzeugt. Bereits vorhandene Report-Dateien werden nicht überschrieben.

CSV/Spreadsheet ist ausschließlich Ausgabe. Es gibt keinen Runtime-Rückimport und keinen
automatischen Config-Schreibpfad nach `rpg-plugin`.

## Nachweis

```text
pwsh -NoProfile -File tools/b16-balance/tests/test-analyze-b16.ps1
```

Der Test prüft gültige Reports, byte-identische Wiederholung für CSV/JSON/Markdown, Versions-,
Szenario-, ID-, Baseline- und Hash-Metadaten, ungültiges YAML, falsche Schema-Version, fehlende
Szenariodaten, unbekannte Mob-IDs sowie den Source-/Dokumentationsscan gegen Netzwerk-,
Server-, Classloader- und Runtime-Rückimportpfade.

Ergebnis am 2026-09-19: `B16 balance analysis tests: PASS`.
