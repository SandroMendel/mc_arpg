# B16 Phase 3 · T016 Snapshot und Reload-Staging

Status: **implementiert und geprüft** (2026-09-16).

## Gemeinsame Veröffentlichung

`ConfigLoader.BatchLoader` und `AbstractConfigLoader.registerBatch` erlauben einen vollständig
mehrteiligen Wert neben den bestehenden Einzeldatei-Handles. `AbstractConfigLoader` stages alle
registrierten Einträge in einer neuen unveränderlichen Map und tauscht danach genau einen
`AtomicReference`-Generationszeiger aus. Dadurch gilt:

- Ein erfolgreicher Reload macht Einzel-Handles und den B16-Snapshot-Handle gemeinsam sichtbar.
- Ein Parser-, Schema- oder Referenzfehler erreicht den Zeigertausch nicht; jeder vorherige Handle
  bleibt unverändert.
- `ConfigHandle.sources()` benennt bei einem Batch alle beteiligten Dateien, während `source()` den
  ersten diagnostischen Pfad liefert.

`RpgPlugin` registriert den B16-Batch vor dem Modul-Bootstrap. Die vorhandenen
`applyReloadedConfig()`-Hooks laufen nur nach einem erfolgreichen `reloadAll`; bei einer B16-
Ablehnung werden sie nicht aufgerufen. Der B16-Snapshot ist bewusst ein unveränderlicher
Generations-/Validierungsmarker. Die Core- und Persistence-Module behalten ihre Core-typisierten
Handles, weil `rpg-core` nicht von `rpg-content` abhängen darf; die Dokumentation behauptet daher
keine direkte Snapshot-Runtime-Quelle, die im Dependency-Graph nicht existiert.

## Prüfung

```text
gradlew.bat --no-daemon :rpg-core:test --tests rpg.core.config.ConfigLoaderBatchAtomicityTest
gradlew.bat --no-daemon :rpg-content:check
gradlew.bat --no-daemon :rpg-platform:test --tests rpg.platform.config.YamlConfigLoaderTest
gradlew.bat --no-daemon :rpg-plugin:test --tests rpg.plugin.B16ContentSnapshotTest
gradlew.bat --no-daemon :rpg-core:spotlessCheck :rpg-content:spotlessCheck :rpg-platform:spotlessCheck :rpg-plugin:spotlessCheck
```

Diese fokussierten Nachweise waren erfolgreich. Der vollständige Legacy-Testlauf enthält weiterhin
die bereits vor T016 bekannten Prüfungen, die direkt auf die im Worktree entfernten
`rpg-plugin/src/main/resources/{classes,abilities,combat,currency,items,mobs,progression,stats,zones}.yml`
zeigen; der vollständige MockBukkit-Pluginlauf benötigt zusätzlich die vorhandene Docker-/Postgres-
Testumgebung.
