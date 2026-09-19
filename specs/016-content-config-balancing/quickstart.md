# Vorläufiger B16-Quickstart und Abnahmekandidat

Dieser Quickstart beschreibt reproduzierbare, serverfreie Abnahmeschritte. T033, T033a und T036
sind abgeschlossen; die Checkliste beschreibt einen `VALID`-Manifestlauf ohne offene GAPs. Die
Befehle sind als Checkliste formuliert; eine Live-Ausführung oder ein Minecraft-Server ist nicht
Teil der Task-Definition.

## Ausgangspfade

Der aktuelle Satz von neun gebündelten Defaults liegt unter
`rpg-content/src/main/resources/`: `classes.yml`, `abilities.yml`, `progression.yml`,
`combat.yml`, `zones.yml`, `mobs.yml`, `items.yml`, `currency.yml` und `stats.yml`.
Die Liste beschreibt den ausgelieferten Artefakt-/Runtime-Umfang, nicht die vollständige
B16-Eigenständigkeit jeder Datei; insbesondere wird `stats.yml` hier nur als gebündelte Datei
aufgeführt und nicht als vollständig B16-eigene Balance-Datei eingeordnet.
Jede Datei beginnt mit `schemaVersion: 1`. Beim Plugin-Start werden sie aus dem
`rpg-content`-Artefakt in den Plugin-Datenordner kopiert, ohne vorhandene Betreiberdateien zu
überschreiben.

## Abnahme-Checkliste

| Kriterium | Reproduzierbarer Schritt | Erwartetes Ergebnis |
|---|---|---|
| SC-001 | `.\gradlew.bat -g C:\Users\Ticoo\.gradle clean :rpg-plugin:serverFreeTest --offline --no-daemon --console=plain` | Das frische Plugin-Artefakt enthält alle neun Laufzeitnamen; ein vorhandener Datenordnerwert bleibt erhalten. |
| SC-002 | `.\gradlew.bat -g C:\Users\Ticoo\.gradle :rpg-content:test --tests rpg.content.B16SchemaFixtureTest --offline --no-daemon --console=plain` | Fehlende Pflichtdaten, Typ-/Bereichsfehler, unbekannte feste Schlüssel, Duplicate Keys und unbekannte Version werden mit Datei/Pfad abgelehnt. |
| SC-003 | `.\gradlew.bat -g C:\Users\Ticoo\.gradle :rpg-core:test --tests rpg.core.config.ConfigLoaderReloadFailureTest --offline --no-daemon --console=plain` | Abgelehnte, fehlende oder nicht parsbare Dokumente behalten die zuvor gültigen Werte der registrierten Config-Handles; ein späterer gültiger Reload ist möglich. |
| SC-004 | `.\gradlew.bat -g C:\Users\Ticoo\.gradle :rpg-plugin:serverFreeTest --offline --no-daemon --console=plain` | `rpg.plugin.B16ContentSnapshotTest` weist die numerische Änderung `WARRIOR.base-stats.health` von `40.0` auf `41.0` in beiden registrierten Handles (`ClassConfig` und `ContentSnapshot`) nach; die alte Generation bleibt bei `40.0`. Außerdem werden ein gültiger Snapshot-Reload und die Ablehnung eines ungültigen Einzel-/Cross-Domain-Reloads geprüft; `rpg.plugin.command.admin.ReloadCommandTest` prüft den Berechtigungspfad `rpg.admin.reload` für `/rpg reload`. |
| SC-005 | 1. `pwsh -NoProfile -File tools/b16-migration/tests/test-migrate-b16.ps1`<br>2. `.\gradlew.bat -g C:\Users\Ticoo\.gradle :rpg-content:test --tests rpg.content.migration.B16MigrationFixtureTest --offline --no-daemon --console=plain` | v0→v1 erhält Werte, Einheiten und IDs, schützt die Quelle, ist idempotent und schreibt atomar in ein neues Ziel. |
| SC-006 | `pwsh -NoProfile -File tools/b16-balance/tests/test-analyze-b16.ps1` | Identische Eingaben erzeugen byte-/zeilenidentische CSV-, JSON- und Markdown-Ergebnisse ohne Server, Netzwerk oder Spreadsheet. |
| SC-007 | 1. `pwsh -NoProfile -File tools/b16-balance/tests/test-analyze-b16.ps1`<br>2. `.\gradlew.bat -g C:\Users\Ticoo\.gradle :rpg-plugin:serverFreeTest --tests rpg.plugin.B16MigrationRuntimeBoundaryTest --offline --no-daemon --console=plain` | Der Balance-Harness belegt für die Analyse-Skripte, dass kein Runtime-/Spreadsheet-Rückimport verwendet wird; `rpg.plugin.B16MigrationRuntimeBoundaryTest` belegt für `RpgPlugin`, dass kein CSV-/Spreadsheet-Konfigurationspfad vorhanden ist. |
| SC-008 | 1. `.\gradlew.bat -g C:\Users\Ticoo\.gradle :rpg-plugin:serverFreeTest --tests rpg.plugin.B16ContentSnapshotTest --offline --no-daemon --console=plain`<br>2. `.\gradlew.bat -g C:\Users\Ticoo\.gradle :rpg-core:test --tests rpg.core.config.ConfigLoaderBatchAtomicityTest --offline --no-daemon --console=plain` | Die Cross-Domain-Abnahme weist die Quelle/Ziel-Kette `mobs/hordes.greenfields.boss -> zones/missing-area` nach; bei dem Fehler bleiben `ContentSnapshot` und beide Handles unverändert. Der generische Batch-Test bestätigt zusätzlich `ConfigValidationException` und den unveränderten Zustand beider Handles. |
| SC-009 | `pwsh -NoProfile -File tools/b16-inventory/tests/test-inventory-b16.ps1` | Das Test-Harness endet erfolgreich mit Exit-Code `0` und `B16 inventory tests: PASS`; der Manifestlauf endet mit Exit-Code `0`, Status `VALID` und ohne offene GAPs. |

## Explizite Migration

Eine Legacy-Datei wird ausschließlich über das Offline-Werkzeug migriert; der normale Bootstrap
schreibt keine unversionierte Datei um:

```powershell
$work = [System.IO.Directory]::CreateTempSubdirectory('b16-quickstart-migration-')
pwsh -NoProfile -File tools/b16-migration/scripts/migrate-b16.ps1 `
  -InputPath .\tools\b16-migration\fixtures\legacy\classes.yml `
  -OutputPath (Join-Path $work.FullName 'classes.yml') `
  -BackupPath (Join-Path $work.FullName 'classes.backup.yml')
```

Die Quelldatei und ein optionales Backup bleiben byte-identisch. Das Ziel wird zunächst als
exklusive temporäre Datei geschrieben und danach atomar verschoben; ein vorhandenes Ziel wird
abgelehnt.

## Reload und Rollback

Die serverfreien Nachweise liegen in `rpg-plugin/src/test/java/rpg/plugin/`:

- `B16ContentSnapshotTest` prüft den erfolgreichen gemeinsamen Publish, den Snapshot und den
  ungültigen Einzel-/Cross-Domain-Fall.
- `ReloadRollbackTest` prüft den Hook-Fehlerpfad mit Wiederherstellung des vorherigen Handles und
  des abgeleiteten Zustands.
- `command/admin/ReloadCommandTest` prüft `rpg.admin.reload`, Audit-Ergebnis und den Befehl
  `/rpg reload`.

Die drei fachlichen Source-Scan-Entscheidungen sind abgeschlossen: `mobs.admin-spawn-limit=20`
gehört zu `mobs.yml` und bedeutet maximal 20 gleichzeitig registrierte `Origin.ADMIN`-Mobs
serverweit; Java-`20` ist nur Kompatibilitäts-Fallback. `BehindTargetCheck.DEFAULT_ANGLE=90.0`
Grad bleibt als globale Core-Regel für die hintere Hemisphäre (genau seitlich zählt nicht), und
`ProjectileEffect.DEFAULT_SPEED=1.6` blocks/tick bleibt technische Core-/Plattform-Physik. Für die
beiden Core-/Physikwerte gibt es vorerst keinen YAML-Owner; `abilities.yml` enthält keinen
`PROJECTILE`-Eintrag.
