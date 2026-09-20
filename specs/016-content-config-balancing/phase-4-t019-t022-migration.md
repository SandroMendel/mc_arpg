# T019–T022 — Explizite v0→v1-Migration

Status: implementiert und serverfrei geprüft am 2026-09-16.

## Migrationsvertrag

`rpg-content` stellt mit `MigrationStep`, `LegacyV0ToV1Step` und `B16MigrationPlan` einen
parserfreien, registrierten Plan für genau die neun B16-Laufzeitdateien bereit. Eine unversionierte
Datei ist die einzige bekannte v0-Signatur. Der Schritt prüft Dateiname, Map-Wurzel, String-Schlüssel
und die erlaubten Legacy-Rootabschnitte. Er erzeugt eine neue geordnete Map mit ausschließlich
`schemaVersion: 1` als zusätzlicher Hülle. IDs, Zahlen, Einheiten und verschachtelte Werte werden
nicht umgerechnet oder ergänzt.

Eine vorhandene `schemaVersion: 1` wird als unveränderter No-op gemeldet. Eine explizite unbekannte
Version, ein unbekannter Dateiname, ein unbekannter Rootabschnitt, ein leerer Root oder eine
mehrdeutige Struktur bricht mit Datei und Dokumentpfad ab. Der Plan liest und schreibt keine Datei
und ist deshalb kein Ersatz für die Runtime-Config-API.

## Separates Werkzeug

`tools/b16-migration/scripts/migrate-b16.ps1` ist der bewusst explizite Bedienpfad. Er erhält
Input-, Output- und optionalen Backup-Pfad, löst sie vorab absolut auf und verweigert gleiche Pfade
sowie vorhandene Ziel-/Backup-Dateien. Die Quelldatei bleibt unverändert; der Backup ist eine
Kopie der Quelle. Die Prüfung verwendet nur die bekannte Root-Allowlist und die neue Datei muss
anschließend weiterhin durch den normalen B16-Parser, Schema- und Cross-Domain-Check laufen.

Der normale `RpgPlugin`-Bootstrap kennt das Tool nicht und schreibt keine B16-Eingabe um. Er kopiert
beim ersten Start ausschließlich fehlende Defaults und lehnt eine unversionierte Betreiberdatei
über das normale `schemaVersion`-Schema ab.

## Verhaltenserhalt und Nachweise

Die neun kleinen Golden-Fixtures in `tools/b16-migration/fixtures/legacy/` und `expected/` decken
repräsentative B07–B11-IDs, Zahlen und Einheiten ab. `B16MigrationFixtureTest` vergleicht die
geparsten Maps und prüft, dass die Version die einzige strukturelle Ergänzung ist. Das PowerShell-
Testskript prüft zusätzlich die byte-identische Quelle, Backup-Schutz, exakte Wiederholung,
vorhandene Zieldateien, unbekannte Rootabschnitte und falsche Versionen.

```text
pwsh -NoProfile -File tools/b16-migration/tests/test-migrate-b16.ps1
gradlew.bat --no-daemon :rpg-content:test --tests rpg.content.migration.B16MigrationPlanTest --tests rpg.content.migration.B16MigrationFixtureTest
gradlew.bat --no-daemon :rpg-plugin:test --tests rpg.plugin.B16MigrationRuntimeBoundaryTest
```
