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
- Lasttests sind Teil der Definition of Done für B05 und B10.

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
