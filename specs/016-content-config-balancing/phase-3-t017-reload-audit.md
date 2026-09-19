# T017 — Reload-Audit und unveränderter Spielzustand

Status: implementiert und serverfrei geprüft am 2026-09-16.

## Vertrag

`/rpg reload` verwendet weiterhin den bestehenden `rpg.admin.reload`-geschützten Befehl und
`RpgPlugin.reloadConfigurationResult()`. Der vorhandene `AuditEntry.details`-Vertrag trägt die
zusätzlichen B16-Metadaten, ohne `AuditEntry` um ein Feld zu erweitern:

| Ergebnis | `result` | `source` | zusätzliche Angaben |
|---|---|---|---|
| erfolgreich | `APPLIED` | `all-registered-sources` | `scope=global` |
| abgelehnt | `REJECTED` | konkrete Quelldatei | `path`, `expected`, `actual`, `reason`, `scope=global` |

Auch ein abgelehnter Versuch wird als `config_reloaded` protokolliert. Die Nutzerantwort benennt
weiterhin Datei, Dokumentpfad, Erwartung und Istwert. Der Logger in `RpgPlugin` wiederholt das
Ergebnis, die Quelle, den Pfad und den konkreten Grund für die serverseitige Diagnose.

## Sicherheits- und Zustandsgrenze

Die Audit-Aufzeichnung läuft ausschließlich im vorhandenen Admin-/Reload-Pfad. Der Reload führt
keine Spieler- oder Entity-Schleife ein und schreibt keinen Spielerzustand. Bei einer Ablehnung
bleibt die vorherige Loader-Generation aktiv; Modul-Hooks werden wie in T016 erst nach einem
erfolgreichen Commit erreicht.

## Nachweis

`rpg-plugin/src/test/java/rpg/plugin/command/admin/ReloadCommandTest.java` prüft die APPLIED- und
REJECTED-Details einschließlich Quelle und Validierungsgrund. `RpgPlugin` verwendet strukturierte
`state/result/source/path/reason`-Logfelder. Die weitergehende MockBukkit-Integration bleibt an
die vorhandene PostgreSQL-/Docker-Testumgebung gebunden.
