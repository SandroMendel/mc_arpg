# T018 — Reload-Berechtigung und einziger Laufzeitpfad

Status: implementiert und serverfrei geprüft am 2026-09-16.

`/rpg reload` bleibt der bestehende Admin-Knoten mit exakt der Berechtigung
`rpg.admin.reload`. Die Prüfung erfolgt zentral über `CommandPermissions`; das Blatt selbst führt
keine zweite Rechteprüfung und verändert keine YAML-Datei. Die B16-Integration erweitert nur das
Laden und Validieren innerhalb des vorhandenen `RpgPlugin.reloadConfigurationResult()`-Pfads.

B16 fügt keinen In-Game-Editor, keinen Schreibbefehl und keinen Spreadsheet-/CSV-Rückimport hinzu.
Der normale Start kopiert nur fehlende Defaults; Betreiberdateien werden nicht überschrieben.
Eine Änderung wird ausschließlich durch den bereits vorhandenen berechtigten Reload-Befehl
wirksam.

## Nachweis

`ReloadCommandTest.reloadUsesTheExistingAdminPermissionGate` prüft den echten Reload-Knoten ohne
Berechtigung und nach expliziter Erteilung. `PermissionTierTest` prüft zusätzlich, dass
`rpg.admin.reload` im Operator-Tier bleibt. Der Reload-Pfad enthält keine Spieler- oder
Entity-Schleife.
