# B15 · Performance & Observability

| | |
|---|---|
| **Schicht** | Querschnitt |
| **Status** | Entwurf |
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

## Offene Fragen

- [ ] Hardware-Zielprofil (Kerne, RAM, Speichertyp)?
- [ ] Monitoring-Stack (Prometheus/Grafana, oder nur Logausgabe)?
- [ ] Werkzeug für simulierte Spieler im Lasttest?
- [ ] Ab welcher Abweichung wird alarmiert?

## Akzeptanzkriterien (Entwurf)

- Ein reproduzierbarer Lasttest mit 150 simulierten Spielern existiert und läuft
  automatisiert.
- Tick-Zeiten je Subsystem sind zur Laufzeit abrufbar.
- Eine absichtlich eingebaute Budgetüberschreitung wird erkannt und gemeldet.
