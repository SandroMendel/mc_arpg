# B15 Lasttest

Dieser Ordner beschreibt den reproduzierbaren Vollastnachweis für B15. Der Runner bleibt ein
externes Werkzeug und wird nicht als Laufzeitabhängigkeit in das Plugin eingebettet.

## Festgelegtes Vollastszenario

`scenarios/b15-full.yaml` ist die versionierte Quelle für den Lauf:

- 150 simulierte Spieler;
- mindestens 800 aktive Custom-Mobs;
- sechs Regionen;
- Bewegung, B05-Kampf, B08b-Coin-Drops und B10-Horden;
- 15 Minuten Aufwärmphase und 30 Minuten Messphase;
- Pass-Grenzen: durchschnittlich mindestens 19,5 TPS, MSPT p95 unter 40 ms und p99 unter 50 ms.

Die sechs Regionen werden gleichmäßig verteilt. Das Szenario legt die vier Adapter-Aktionen
(`movement`, `combat`, `coin-drops`, `hordes`) als versionierten Vertrag fest. Ein
`mc-pilot`-Adapter darf die konkrete CLI-Syntax abbilden, muss aber dieselben vier Aktionen und
dieselben Zähler im Ergebnis liefern.

## Externer Client

Der bestätigte Erstkandidat ist `mc-pilot`. Vor einem Lauf werden die Version und das für Paper
26.2 passende Clientprofil festgeschrieben. `player-tool-version: pin-at-run` ist ein bewusster
Platzhalter: Der konkrete Release- oder Commitstand wird im Manifest gespeichert, sobald der
Testserver abgenommen wird. Ein veraltetes Clientprofil macht den Lauf `INVALID`, nicht
vergleichbar.

Die Auswahl bleibt außerhalb des Plugin-JARs. Falls `mc-pilot` eine definierte Aktion nicht
reproduzierbar ausführen kann, wird nur der externe Adapter ergänzt; die Plugin-Messgrenze bleibt
unverändert.

## Zielhardware und Laufumgebung

Vor jedem vergleichbaren Lauf werden auf der tatsächlichen Zielmaschine CPU-Modell, Kernzahl,
Arbeitsspeicher, Datenträger, Betriebssystem, Java-Version, JVM-Argumente, Paper-Build und
Plugin-Version in das Manifest geschrieben. Ergebnisse verschiedener Hardware-, Java-, Paper-
oder Szenarioversionen dürfen nicht als eine Baseline zusammengeführt werden.

## Artefakte

Jeder Lauf erhält eine unveränderliche `runId` und schreibt mindestens:

- `metrics.prom` aus B15;
- den strukturierten Performancebericht;
- das Server-Log;
- `run-manifest.json` gemäß `schemas/run-manifest.schema.json`;
- optional das kontrollierte Spark-Artefakt.

Die Skripte im Unterordner `scripts/` sind absichtlich unabhängig vom Plugin:

- `validate-scenario.ps1` prüft vor dem Lauf die Spieler-, Mob-, Regionen-, Phasen- und Workload-Grenzen;
- `run-b15.ps1` erfasst Hardware/Software, wartet auf Paper, legt die `runId` an, sammelt Artefakte
  und schreibt das Manifest;
- `validate-manifest.ps1` unterscheidet fehlende Daten (`INVALID`) von echten Grenzwertverletzungen
  (`FAILED`) und prüft bei `-CheckArtifacts` die drei Pflichtdateien.

Der externe Adapter schreibt vor der Manifestprüfung eine `result.json` in den Run-Ordner. Sie enthält
`meanTps`, `msptP95`, `msptP99`, `peakCustomMobs`, `measurementMinutes`, `activePlayersPeak`,
`missingSources`, `metricsFresh`, `restartDetected`, `warmupCompleted` und `firstFailure`.

Beispiel für die Vorprüfung:

```text
pwsh -NoProfile -File tools/b15-loadtest/scripts/validate-scenario.ps1 `
  -ScenarioPath tools/b15-loadtest/scenarios/b15-full.yaml
```

Ein Profiling-Lauf nutzt ausschließlich die von Paper gebündelte Spark-Integration. Für einen
zehnminütigen kontrollierten Profiling-Lauf wird im Server verwendet:

```text
/spark profiler start --timeout 600
```

Spark wird nicht als dauerhafte Plugin-Abhängigkeit in B15 geladen.

## Gültigkeitsregeln

Das Manifest wird `INVALID`, wenn die Zielzahl nicht erreicht wird, die Metrikdatei veraltet oder
ein Pflichtartefakt fehlt. Ein erkannter Neustart oder eine nicht erreichte Last wird als `FAILED`
mit `firstFailure` markiert. `PASSED` ist nur erlaubt, wenn alle Grenzen aus Szenario und Manifest
erfüllt sind. Die JSON-Schema-Prüfung wird vor der Auswertung des Ergebnisses ausgeführt.
