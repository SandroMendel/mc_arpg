# B16 Phase 6 — T028–T032-Nachweise

Stand: **bestätigte Läufe und vorhandene Testartefakte im Worktree**. Diese Datei startet
keinen neuen Lauf.

## Reproduzierbare Läufe

```text
gradlew.bat --no-daemon :rpg-content:test
gradlew.bat --no-daemon :rpg-core:test --tests "rpg.core.config.*"
gradlew.bat --no-daemon :rpg-plugin:serverFreeTest
gradlew.bat --no-daemon :rpg-core:spotlessCheck :rpg-content:spotlessCheck :rpg-platform:spotlessCheck :rpg-plugin:spotlessCheck

pwsh -NoProfile -File tools/b16-migration/tests/test-migrate-b16.ps1
pwsh -NoProfile -File tools/b16-balance/tests/test-analyze-b16.ps1
pwsh -NoProfile -File tools/b16-inventory/tests/test-inventory-b16.ps1
```

Die vorhandenen Gradle-Reports zeigen `rpg-content:test` mit 30 Tests, die sechs
`rpg-core`-Konfigurationssuiten mit 28 Tests und `serverFreeTest` mit 12 Tests — jeweils
ohne Failures, Errors oder Skips. Der fokussierte Lauf mit den vier `spotlessCheck`-Tasks
war ebenfalls erfolgreich.

## Zuordnung

| Task | Testdateien und belegtes Verhalten | Ergebnis |
|---|---|---|
| T028 | `rpg-content/src/test/java/rpg/content/B16SchemaFixtureTest.java` prüft gültig, fehlend, falschen Typ, Bereich, unbekannten festen Schlüssel, Duplicate Key und unbekannte `schemaVersion`; ergänzt durch `rpg-core/src/test/java/rpg/core/config/`. | `rpg-content:test` und Core-Konfigurationssuiten bestanden. |
| T029 | `ConfigLoaderBatchAtomicityTest`, `ConfigLoaderReloadTest`, `ConfigLoaderReloadFailureTest`, `ContentSnapshotTest` und `B16ContentSnapshotTest` belegen gemeinsamen Publish sowie unveränderte Handles/Snapshots bei Einzel-, Parser- und Querverbindungsfehlern. | Serverfrei bestanden. |
| T030 | `B16MigrationPlanTest`/`B16MigrationFixtureTest` prüfen v0→v1, Golden-Fixtures, Quellschutz und Idempotenz; das PowerShell-Skript prüft zusätzlich Pfad-/Backup-Schutz und Fehlervarianten. | `B16 migration script tests: PASS`; die sechs Migrationstests bestanden. |
| T031 | `tools/b16-balance/tests/test-analyze-b16.ps1` prüft gültige Reports, deterministische CSV/JSON/Markdown-Ausgabe, Metadaten, Negativfälle und fehlenden Runtime-Rückimport. | `B16 balance analysis tests: PASS` (2026-09-19). |
| T032 | `ContentResourceOwnershipTest`, `B16ContentSnapshotTest` und `ReloadCommandTest` prüfen alle neun Content-Ressourcen, genau einen Jar-/Classpath-Owner, nicht überschreibendes Kopieren und die geschützte Reload-/Audit-Verdrahtung. | `rpg-plugin:serverFreeTest` bestanden. |

## Inventar-Gate und Grenzen

Der aktuelle Lauf von `tools/b16-inventory/scripts/inventory-b16.ps1` endet nach der fachlichen
Entscheidung mit **Exit 0** und `status=VALID`. Es bleiben keine offenen GAPs. Die drei zuvor
offenen Kandidaten sind als begründete `EXCEPTION`s erfasst:

- `mobs.admin-spawn-limit = 20` als `PROTECTION_BOUNDARY`
- `exception.ability.behind-angle` (`BehindTargetCheck.DEFAULT_ANGLE = 90.0`) als `ALGORITHM_CONSTANT`
- `exception.ability.projectile-speed` (`ProjectileEffect.DEFAULT_SPEED = 1.6`) als `PLATFORM_PHYSICS`

Der Wrapper `test-inventory-b16.ps1` erwartet diesen Exit 0 und meldet `B16 inventory tests: PASS`;
technische Region-/Unmapped-Treffer und begründete `EXCEPTION`s werden nicht als GAPs gezählt.

Die PowerShell-Nachweise sind lokal und benötigen kein Docker, Paper, Bukkit, Netzwerk oder
Spreadsheet. `rpg-plugin:serverFreeTest` schließt den vollständigen Bootstrap bewusst aus;
der vollständige MockBukkit-/Pluginlauf benötigt die vorhandene Docker-/Postgres-Umgebung.
Bekannte Legacy-Tests referenzieren weiterhin die im Worktree entfernten
`rpg-plugin/src/main/resources/{classes,abilities,combat,currency,items,mobs,progression,stats,zones}.yml`;
das ist eine bekannte Testgrenze und kein T028–T032-Fehlschlag.
