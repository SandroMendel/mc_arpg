# B16 serverfreie Balance-Auswertung

Dieses Werkzeug erzeugt einen reproduzierbaren Nachweis des aktuellen B16-Bestands. Es ist ein
repo-lokales, netzwerkfreies PowerShell-Werkzeug und benötigt weder Minecraft/Paper, den Plugin-
Classloader noch ein Spreadsheet-Programm.

## Nutzung

Aus dem Repository `b15-performance-observability`:

```powershell
pwsh -NoProfile -File tools/b16-balance/scripts/analyze-b16.ps1 `
  -ConfigRoot rpg-content/src/main/resources `
  -ScenarioPath tools/b16-balance/fixtures/current-baseline-v1.yml `
  -OutputDirectory .tmp/b16-balance-report
```

Der Lauf schreibt ausschließlich in `-OutputDirectory` drei Dateien:

- `b16-balance.json` — versionierter Ergebnisvertrag und vollständige Metadaten;
- `b16-balance.csv` — tabellarische Ausgabe;
- `b16-balance.md` — lesbare Zusammenfassung.

Ein vorhandenes Ergebnis gleichen Namens wird nicht überschrieben. Bei einem Fehler werden die
Ausgabedateien erst nach vollständiger Validierung angelegt; ein als `VALID` markierter Bericht
entsteht dann nicht.

Die Veröffentlichung ist als vollständige Verzeichnis-Transaktion umgesetzt: Alle drei Dateien
werden zuerst in einem temporären Geschwisterverzeichnis geschrieben und erst danach gemeinsam
veröffentlicht. Ein bereits vorhandenes Ausgabeziel (auch ein leeres Verzeichnis) wird abgelehnt.
Temporäre Pfade und Zufallsnamen erscheinen niemals im Ergebnis. Ausgabeziele unter `ConfigRoot`,
`rpg-plugin` oder `rpg-content/src/main/resources` werden ebenfalls abgelehnt.

Der eingebaute YAML-Leser unterstützt bewusst nur die für diesen Vertrag benötigte Teilmenge:
Mappings, Listen, skalare Werte sowie Flow-Maps und Flow-Listen. Er ist keine allgemeine YAML-
Implementierung; unter anderem werden komplexe YAML-Konstrukte, Block-Scalars, Anchors, Aliases,
Tags und mehrere Dokumente nicht als allgemeiner YAML-Vertrag garantiert.

## Versionierte Verträge

Die Eingabe ist YAML mit `schemaVersion: 1`. Die Fixture trägt zusätzlich
`fixtureVersion: 1` und `baselineVersion: 1`. Darunter sind eine stabile `scenario.id`, die exakt
neun B16-Quelldateien, typisierte Metriken und die verwendeten Content-IDs festgelegt.

Unterstützte Metrikverträge:

- `damage-curve`: physischer Schadenswert pro Treffer über vorhandene Klassen und Level;
- `ttk`: Trefferzahl und Zeit bis zum Besiegen eines vorhandenen Mob-Kinds. Die Auswertung nutzt
  die bereits im Core definierte Formel `damage * 100 / (100 + defense)` und Angriffstempo als
  Angriffe pro Sekunde;
- `baseline-values`: direkte, einheitenbehaftete Werte aus vorhandenen YAML-Pfaden.

Der Ergebnisvertrag hat `resultVersion: 1`, `status: VALID`, Szenario-ID, verwendete IDs,
Baseline-Werte, Quelldateien sowie einen `sha256:`-Source-Hash. Der Hash umfasst die neun
Runtime-YAMLs und die Fixture in fester Reihenfolge. Der `baselineId` wird daraus deterministisch
abgeleitet; Zeiten, Zufall und externe Dienste gehen nicht ein.

Die Fixture enthält keine neuen Balanceziele. Sie referenziert ausschließlich IDs und aktuelle
Werte aus `rpg-content/src/main/resources`.

## Fehler- und Quellschutzvertrag

Ungültiges YAML, falsche Versionen, fehlende Szenariodaten, unbekannte IDs und unbekannte
Dokumentpfade liefern Nicht-Erfolg mit Eingabepfad und Ursache. Kein gültiger Report wird erzeugt.
Die Analyse schreibt niemals nach `rpg-plugin` oder in Runtime-Konfigurationen.

CSV/Spreadsheet ist ausschließlich Ausgabe. Es gibt keinen Runtime-Rückimport, keinen
automatischen Config-Schreibpfad und keinen Mechanismus, der Analysewerte in YAML zurückschreibt.
