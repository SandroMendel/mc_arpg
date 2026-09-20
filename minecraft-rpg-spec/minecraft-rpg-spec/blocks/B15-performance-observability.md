# B15 · Performance & Observability

| | |
|---|---|
| **Schicht** | Querschnitt |
| **Status** | In Umsetzung — Code-/Testbasis vorhanden; echter Paper-/Lastlauf offen |
| **Abhängig von** | B01 |
| **Benötigt von** | alle |

## Zweck

Macht die Performanceziele messbar und durchsetzbar, statt sie zu behaupten.

## Umfang

- Tick-Budget-Messung je Subsystem
- Metrikexport für externes Monitoring
- Integration eines Profilers (Spark o. ä.)
- Lasttest-Aufbau mit simulierten Spielern
- Alarmierung bei Budgetüberschreitung
- Regelmäßiger Performance-Bericht im Log

## Zielwerte

| Kennzahl | Zielwert |
|---|---|
| TPS | ≥ 19,5 im Mittel bei 150 Spielern unter Kampflast |
| MSPT | p95 < 40 ms, p99 < 50 ms |
| Aktive Custom-Mobs | ≥ 800 serverweit |
| Tick-Budget je Subsystem | ≤ 5 ms im Normalbetrieb |
| Login-Ladezeit | p95 < 500 ms |

## Architekturvorgaben

- Jeder Block meldet seine Tick-Zeit an eine zentrale Erfassung. Überschreitungen
  werden geloggt und sind im Betrieb sichtbar.
- Die Messung selbst darf im Normalbetrieb keine messbaren Kosten erzeugen und
  ist abschaltbar.
- **Die Lasttestphase gehört diesem Block** *(ADR-031, 2026-08-23)*. Lasttests sind
  keine Bedingung dafür, dass ein einzelner Block fertig ist — sie laufen
  gebündelt hier, wenn die inhaltlichen Blöcke stehen. Zuvor waren B05 und B10
  namentlich lasttestpflichtig, bevor sie als fertig gelten durften; das war nicht
  einlösbar, weil ein Lasttest Spieler, Mobs und Inhalt braucht, also gerade das,
  was die späteren Blöcke erst liefern.
- Die Phase prüft die Zielwerte oben **im Zusammenspiel**, nicht ein Subsystem in
  einer künstlich leeren Welt. Mitzumessen sind namentlich: B05s Schadenspfad,
  B08bs Coin-Haufen (ein Entity je Kill, SC-006 dort), B09s Zonenzuordnung unter
  Bewegungslast und B10s Horden.
- Ein blockeigenes Leistungsziel, das **ohne** Volllast zu belegen ist, bleibt beim
  Block selbst — als wiederholbare Messung. Diese Phase übernimmt nur, was sich
  erst unter 150 Spielern und 800 Mobs zeigt.

## Festgelegter B15-Stand (2026-09-11)

- Die Core-Messgrenze ist Bukkit-/Paper-frei. Paper liefert nur über die öffentlichen
  `ServerTickStartEvent`-/`ServerTickEndEvent`-Adapter die Gesamt-Tickdaten.
- Fenster sind begrenzt, die Perzentile sind p50/p95/p99. Ab 90 % Budget wird gewarnt; nach
  60 Sekunden kontinuierlicher Verletzung folgt genau ein kritischer Alarm, danach eine einmalige
  Erholung.
- Monitoring besteht aus strukturierten Log-Berichten und einer atomar ersetzten,
  Prometheus-kompatiblen Textdatei. Ein Grafana-Dashboard ist nicht Teil von B15.
- Der Lasttest läuft extern mit dem zum Lauf gepinnten `mc-pilot`-Profil. Die Szenariovorgabe ist
  150 Spieler, 800 Custom-Mobs, sechs Regionen, 15 Minuten Warm-up und 30 Minuten Messung.
- Profiling nutzt nur Papers gebündeltes Spark im kontrollierten Lauf; B15 bringt keine Spark-
  oder Profiler-Laufzeitabhängigkeit mit.
- Jeder Vergleichslauf erfasst das reale Zielhardwareprofil und verwirft Ergebnisse bei fehlender
  Last, veralteten Metriken, Neustart oder fehlenden Artefakten.

## Offene Fragen

- [x] Hardware-Zielprofil: reale Zielmaschine; CPU, Kerne, RAM, Storage, OS und JVM werden je Lauf
  im Manifest erfasst.
- [x] Monitoring-Stack: strukturierte Logs plus Prometheus-kompatibler Export; kein Grafana in B15.
- [x] Werkzeug: externes, für Paper 26.2 gepinntes `mc-pilot`-Profil.
- [x] Alarmgrenzen: Warnung ab 90 %, kritisch nach 60 Sekunden; Lasttestziele 19,5 TPS / 40 ms
  p95 / 50 ms p99.

## Akzeptanzkriterien (Entwurf)

- Ein reproduzierbarer Lasttest mit 150 simulierten Spielern existiert als versioniertes Szenario
  und Runner; der echte 45-Minuten-Lauf bleibt der Betriebsnachweis.
- Tick-Zeiten je Subsystem sind zur Laufzeit abrufbar.
- Eine absichtlich eingebaute Budgetüberschreitung wird erkannt und gemeldet.
