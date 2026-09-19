# Feature Specification: Performance & Observability

**Feature Branch**: `015-performance-observability`

**Created**: 2026-09-11

**Status**: Ready for planning

**Input**: User description: "Make server performance and observability measurable and enforceable, including subsystem tick budgets, runtime metrics export, profiler workflow, a reproducible 150-player/800-mob load test, threshold alerts, and regular performance reporting."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tick-Budgets überwachen (Priority: P1)

Als Betreiber möchte ich die Laufzeit der RPG-Subsysteme und des gesamten Server-Ticks sehen, damit ich Budgetüberschreitungen erkennen kann, bevor sie den Spielbetrieb beeinträchtigen.

**Why this priority**: Ohne verlässliche Messung bleiben die TPS- und MSPT-Ziele Behauptungen. Dieser Weg liefert den kleinsten eigenständig nutzbaren Observability-Kern.

**Independent Test**: Ein Test registriert mehrere künstliche Subsysteme mit bekannten Laufzeiten, liest deren Zeitfenster und prüft Warnung, kritischen Alarm und Erholung ohne einen vollständigen Lasttest.

**Acceptance Scenarios**:

1. **Given** mehrere registrierte Subsysteme mit einem festgelegten Tick-Budget, **When** ein Messfenster abgeschlossen wird, **Then** sind Laufzeit und Perzentile je Subsystem sowie die Gesamtwerte abrufbar.
2. **Given** ein Subsystem überschreitet 90 % seines Budgets, **When** die Überschreitung mindestens 60 Sekunden anhält, **Then** wird genau ein kritischer Alarm mit Subsystem, Messwert, Grenzwert und Zeitfenster ausgegeben.
3. **Given** ein zuvor alarmierendes Subsystem liegt wieder unter dem Grenzwert, **When** ein vollständiges Messfenster abgeschlossen wird, **Then** wird die Erholung nachvollziehbar gemeldet und der Alarm nicht bei jedem Tick wiederholt.

---

### User Story 2 - Reproduzierbaren Lasttest ausführen (Priority: P1)

Als Performance-Verantwortlicher möchte ich einen definierten Lasttest mit Spielern, Mobs und typischen RPG-Aktionen ausführen, damit die Projektziele unter gemeinsamer Last nachgewiesen werden.

**Why this priority**: Die eigentliche Projektzusage betrifft das Zusammenspiel der Blöcke. Einzelne isolierte Benchmarks können den Betrieb mit 150 Spielern und 800 Mobs nicht ersetzen.

**Independent Test**: Ein Testlauf wird auf einem dokumentierten Zielsystem gestartet, erreicht 150 simulierte Spieler und 800 aktive Custom-Mobs, führt das definierte Szenario aus und erzeugt einen pass/fail-Bericht mit Rohdaten.

**Acceptance Scenarios**:

1. **Given** ein dokumentiertes Hardware- und Softwareprofil, **When** der Lasttest gestartet wird, **Then** werden Aufwärmphase, Messphase, Konfiguration und verwendetes Testwerkzeug im Laufmanifest festgehalten.
2. **Given** sechs Regionen mit insgesamt mindestens 800 aktiven Custom-Mobs, **When** 150 simulierte Spieler Bewegung, Kampf und RPG-Aktionen ausführen, **Then** werden B05-Schaden, B08b-Coin-Drops, B09-Zonenbewegung und B10-Horden in derselben Messung berücksichtigt.
3. **Given** 15 Minuten Aufwärmzeit und 30 Minuten Messzeit sind abgeschlossen, **When** die Zielwerte erfüllt sind, **Then** wird der Lauf als bestanden ausgewiesen und die Rohdaten bleiben für eine spätere Prüfung erhalten.
4. **Given** die erforderliche Spieler- oder Mob-Anzahl wird nicht erreicht oder ein Messwert verletzt ein Ziel, **When** der Lauf endet, **Then** wird er als fehlgeschlagen oder ungültig ausgewiesen und nicht stillschweigend als bestanden markiert.

---

### User Story 3 - Externes Monitoring und Berichte nutzen (Priority: P2)

Als Betreiber möchte ich die wichtigsten Performancewerte in einem externen Monitoring-System und regelmäßig im Serverlog sehen, damit ich Trends auch außerhalb einer einzelnen Spielersitzung verfolgen kann.

**Why this priority**: Laufzeitbeobachtung ist für Diagnose und Betrieb wichtig, baut aber auf dem zentralen Messkern aus User Story 1 auf.

**Independent Test**: Ein lokaler Monitoring-Abnehmer liest die exportierten Zeitreihen, während der Server gleichzeitig einen strukturierten periodischen Bericht ausgibt.

**Acceptance Scenarios**:

1. **Given** die Messung ist aktiviert, **When** ein Monitoring-Abnehmer die Metriken abfragt, **Then** erhält er Gesamt-TPS/MSPT, Perzentile, aktive Custom-Mobs, aktive simulierte Spieler, Subsystemwerte und Alarmstatus.
2. **Given** der konfigurierte Berichtszeitraum ist abgelaufen, **When** der nächste Bericht erzeugt wird, **Then** enthält er den Zeitraum, die Zielwerte, die wichtigsten Perzentile und alle aktiven Budgetverletzungen.
3. **Given** der externe Monitoring-Abnehmer ist nicht erreichbar, **When** der Server weiterläuft, **Then** bleiben lokale Messung, Alarme und Logberichte funktionsfähig und der Exportfehler wird sichtbar gemeldet.

---

### User Story 4 - Profiler für Ursachenanalyse einsetzen (Priority: P2)

Als Entwickler möchte ich bei einem kontrollierten Testlauf einen Profiler aktivieren und das Ergebnis einem Messbericht zuordnen, damit ich die Ursache eines Performanceproblems untersuchen kann.

**Why this priority**: Zeitreihen zeigen, dass ein Problem existiert; ein Profiler hilft bei der Ursachenanalyse. Er darf dabei den normalen Spielbetrieb nicht dauerhaft belasten.

**Independent Test**: Ein dokumentierter Profiling-Lauf erzeugt ein eindeutig benanntes Profiling-Artefakt mit Laufmanifest, Start-/Endzeit und Verweis auf die zugehörigen Performancewerte.

**Acceptance Scenarios**:

1. **Given** ein Testlauf ist vorbereitet, **When** der Profiler nur für diesen Lauf aktiviert wird, **Then** wird der Profiling-Zeitraum im Laufmanifest erfasst.
2. **Given** der Profiling-Lauf ist beendet, **When** die Ergebnisse abgelegt werden, **Then** lassen sich Profiling-Artefakt, Hardwareprofil und Performancebericht eindeutig einander zuordnen.
3. **Given** kein Profiling gewünscht ist, **When** der normale Serverbetrieb startet, **Then** entsteht keine zusätzliche Profiling-Arbeit und der Server bleibt ohne externe Profiler-Abhängigkeit betriebsfähig.

---

### User Story 5 - Subsysteme mit einem gemeinsamen Messvertrag anbinden (Priority: P3)

Als Maintainer möchte ich neue und bestehende RPG-Subsysteme über denselben Messvertrag anmelden können, damit Performancewerte vergleichbar bleiben und keine eigene Tick-Messlogik pro Block entsteht.

**Why this priority**: Der gemeinsame Vertrag verhindert Messlücken und parallele, widersprüchliche Lösungen. Der Nutzen wird vor allem bei weiteren Blöcken sichtbar.

**Independent Test**: Ein zusätzliches künstliches Subsystem kann ausschließlich über den gemeinsamen Vertrag registriert werden und erscheint danach automatisch in Messung, Export, Bericht und Alarmprüfung.

**Acceptance Scenarios**:

1. **Given** ein Subsystem meldet Name und Budget über den gemeinsamen Vertrag an, **When** es Messwerte liefert, **Then** erscheint es ohne weitere Sonderverdrahtung in den zentralen Ausgaben.
2. **Given** ein unbekannter oder doppelt verwendeter Subsystemname wird angemeldet, **When** die Konfiguration validiert wird, **Then** wird die Anmeldung eindeutig abgelehnt und kein falscher Messwert erzeugt.

### Edge Cases

- Wird der Messkern deaktiviert, darf kein eigener wiederkehrender Durchlauf je Spieler oder Entity entstehen und es darf kein falscher Bestanden-Status ausgegeben werden.
- Kann der Server die Zielzahl von 150 Spielern oder 800 Custom-Mobs nicht halten, muss der Lasttest ungültig oder fehlgeschlagen sein.
- Fällt der externe Export aus, müssen lokale Logs und Alarmierung weiterlaufen.
- Fehlt ein erwartetes Subsystem im Bericht, muss es als fehlend markiert werden; ein fehlender Wert darf nicht als Nullwert erscheinen.
- Bei einer unterbrochenen Verbindung, einem Neustart oder einer unvollständigen Messphase darf kein passender Lauf behauptet werden.
- Flatternde Grenzwertüberschreitungen müssen dedupliziert und mit einer Erholungsmeldung abgeschlossen werden.
- Unvollständige oder widersprüchliche Hardware-/Softwareangaben machen einen Lauf ungültig, statt ihn vergleichbar erscheinen zu lassen.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Das System MUST jedem messbaren RPG-Subsystem eine stabile Kennung und ein explizites Tick-Budget zuordnen können.
- **FR-002**: Das System MUST die Laufzeit jedes angemeldeten Subsystems und des Gesamtticks in konfigurierten Messfenstern erfassen.
- **FR-003**: Das System MUST pro Subsystem mindestens Anzahl, Minimum, Maximum, Mittelwert, p50, p95 und p99 eines Messfensters bereitstellen.
- **FR-004**: Das System MUST Gesamt-TPS, Gesamt-MSPT und deren Perzentile getrennt von den Subsystemwerten ausweisen.
- **FR-005**: Das System MUST die Messung abschaltbar machen; im abgeschalteten Normalbetrieb darf daraus keine wiederkehrende Arbeit je Spieler oder Entity entstehen.
- **FR-006**: Das System MUST eine Budgetwarnung ab 90 % des konfigurierten Subsystembudgets erkennen.
- **FR-007**: Das System MUST eine kritische Überschreitung melden, wenn ein Projektziel oder Subsystembudget mindestens 60 Sekunden durchgehend verletzt wird.
- **FR-008**: Jeder Alarm MUST Subsystem oder Gesamtwert, Istwert, Grenzwert, Messfenster und Beginn der Verletzung enthalten.
- **FR-009**: Das System MUST wiederholte Meldungen derselben ununterbrochenen Verletzung begrenzen und eine Erholungsmeldung erzeugen.
- **FR-010**: Das System MUST die zentralen Zeitreihen, Zähler, aktiven Mobs, aktiven simulierten Spieler und Alarmzustände für einen externen Monitoring-Abnehmer exportieren.
- **FR-011**: Das System MUST bei einem Exportfehler die lokale Messung, Alarmierung und Berichterstattung fortsetzen und den Fehler sichtbar protokollieren.
- **FR-012**: Das System MUST regelmäßig einen strukturierten Performancebericht mit Zeitraum, Gesamtwerten, Subsystem-Perzentilen, Zielwerten und Alarmen ausgeben.
- **FR-013**: Das System MUST eine kontrollierte Profiling-Phase mit Startzeit, Endzeit, Laufkennung und zugehörigem Performancebericht dokumentieren können.
- **FR-014**: Der normale Serverbetrieb MUST ohne dauerhaft benötigte externe Profiler-Komponente lauffähig bleiben.
- **FR-015**: Der Lasttest MUST bis zu 150 simulierte Spieler mit reproduzierbarem Lebenszyklus und konfigurierbarem Aktionsprofil ausführen können.
- **FR-016**: Das Lasttestszenario MUST Bewegung, Kampf und RPG-Aktionen abbilden und dabei B05-Schaden, B08b-Coin-Drops, B09-Zonenbewegung und B10-Horden gemeinsam ausüben.
- **FR-017**: Der Lasttest MUST mindestens sechs Regionen und mindestens 800 gleichzeitig aktive Custom-Mobs berücksichtigen können.
- **FR-018**: Der Lasttest MUST eine 15-minütige Aufwärmphase und eine anschließende 30-minütige Messphase getrennt ausweisen.
- **FR-019**: Jeder Lasttest MUST Hardwareprofil, Softwareprofil, Szenariokonfiguration, Testwerkzeug, Spielerzahl, Mobzahl und Messzeitraum in einem Laufmanifest festhalten.
- **FR-020**: Jeder Lasttest MUST Rohdaten für TPS, MSPT, p95, p99, aktive Mobs, aktive Spieler, Subsystemwerte und Alarme dauerhaft für die Auswertung ablegen.
- **FR-021**: Ein Lasttest MUST als bestanden gelten, wenn im Messzeitraum der TPS-Mittelwert mindestens 19,5 beträgt, MSPT p95 unter 40 ms bleibt, MSPT p99 unter 50 ms bleibt und mindestens 800 Custom-Mobs aktiv waren.
- **FR-022**: Ein Lasttest MUST als fehlgeschlagen oder ungültig gelten, wenn die erforderliche Last nicht erreicht wird, ein Ziel verletzt wird oder Messdaten fehlen.
- **FR-023**: Ein Lasttest MUST den ersten verletzten Zielwert und die zugehörige Zeit im Abschlussbericht ausweisen.
- **FR-024**: Das System MUST neue Subsysteme über denselben Messvertrag registrieren und automatisch in Messung, Export, Bericht und Alarmprüfung aufnehmen.
- **FR-025**: Das System MUST doppelte oder unbekannte Subsystemkennungen vor einem gültigen Messlauf ablehnen.
- **FR-026**: Das System MUST eine wiederholbare Messung ohne Volllast ermöglichen, damit ein einzelner Subsystempfad separat geprüft werden kann.
- **FR-027**: Das System MUST Bericht- und Alarmintervalle sowie die Exportaktivierung konfigurieren können, ohne die unveränderlichen Projektziele für einen bestandenen Lasttest aufzuweichen.
- **FR-028**: Das System MUST einen unvollständigen, unterbrochenen oder neu gestarteten Lauf als nicht bestanden beziehungsweise ungültig kennzeichnen und darf daraus keinen positiven Nachweis ableiten.

### Key Entities *(include if data involved)*

- **Subsystem-Budget**: Stabile Kennung, Verantwortungsbereich, erlaubte Tickzeit und Aktivierungsstatus eines messbaren RPG-Subsystems.
- **Messfenster**: Zeitbereich mit Rohsamples und abgeleiteten Kennzahlen eines Subsystems oder des Gesamtticks.
- **Alarmereignis**: Beginn, aktueller Zustand, Istwert, Grenzwert, Ursache und Erholungszeit einer Budgetverletzung.
- **Monitoring-Metrik**: Exportierbarer Zeitreihenwert oder Zähler für Tickdauer, Spieler, Mobs, Budget und Alarmzustand.
- **Hardwareprofil**: Dokumentierte Zielmaschine mit CPU-, Speicher-, Datenträger- und Laufzeitangaben für die Vergleichbarkeit eines Laufs.
- **Lasttestszenario**: Versionierte Beschreibung von Spielerzahl, Mobzahl, Regionen, Aktionen, Aufwärm- und Messphase.
- **Laufmanifest**: Unveränderliche Zuordnung von Szenario, Hardware, Software, Testwerkzeug, Messdaten und Ergebnis.
- **Performancebericht**: Zusammenfassung eines Messfensters oder Lasttests mit Kennzahlen, Zielwerten, Warnungen und Pass/Fail-Entscheidung.
- **Profiling-Artefakt**: Ergebnis einer zeitlich begrenzten Ursachenanalyse, das über die Laufkennung mit dem Bericht verknüpft ist.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100 % der als aktiv gemeldeten B15-Subsysteme erscheinen in jedem vollständigen Messbericht mit mindestens p95, p99 und Budgetstatus; fehlende Werte werden ausdrücklich als Fehler ausgewiesen.
- **SC-002**: Bei aktivierter Messung stehen neue Gesamt- und Subsystemwerte innerhalb eines konfigurierten 15-Sekunden-Exportfensters für externe Beobachtung zur Verfügung.
- **SC-003**: Bei deaktivierter Messung entstehen keine wiederkehrenden Mess-, Export- oder Profileraufgaben je Spieler oder Entity.
- **SC-004**: Eine absichtlich erzeugte Budgetüberschreitung löst spätestens nach 60 Sekunden einen eindeutigen kritischen Alarm aus und erzeugt während derselben Verletzung keine unbounded Meldungsflut.
- **SC-005**: Ein vollständiger Lasttest mit 150 simulierten Spielern, mindestens 800 aktiven Custom-Mobs und 30 Minuten Messzeit erzeugt ein reproduzierbares Laufmanifest, Rohdaten und einen eindeutigen Pass/Fail-Bericht.
- **SC-006**: Ein bestandener Vollastlauf erreicht mindestens 19,5 TPS im Mittel, MSPT p95 unter 40 ms und MSPT p99 unter 50 ms.
- **SC-007**: Ein Testlauf, der die geforderte Spieler- oder Mobzahl nicht erreicht oder Messdaten verliert, wird in 100 % der Fälle als ungültig oder fehlgeschlagen statt als bestanden markiert.
- **SC-008**: Der regelmäßige Performancebericht enthält mindestens einmal pro konfiguriertem 60-Sekunden-Intervall die Gesamtwerte, die Subsystem-Perzentile, aktive Mobs, aktive Spieler und offene Alarme.
- **SC-009**: Ein Profiling-Artefakt kann in 100 % der kontrollierten Profiling-Läufe über eine Laufkennung dem passenden Hardwareprofil, Szenario und Performancebericht zugeordnet werden.
- **SC-010**: Ein neu angemeldetes Subsystem erscheint ohne eigene Sonderausgabe automatisch in Messbericht, Export und Budgetprüfung; doppelte Kennungen werden vor dem Lauf abgelehnt.

## Assumptions

- Das Zielprofil wird auf dem tatsächlichen Produktions- beziehungsweise Testserver aufgenommen und vor jedem vergleichbaren Vollastlauf dokumentiert.
- Der B15-Erstumfang liefert zentrale Messung, strukturierte Logs und einen externen, Prometheus-kompatiblen Export. Ein vollständiges Grafana-Dashboard ist nicht Teil dieses Blocks.
- Für simulierte Spieler wird zuerst ein bestehendes, mit der eingesetzten Paper-/Minecraft-Version kompatibles Load-Test- oder Bot-Werkzeug verwendet. Eine eigene Orchestrierung oder ein eigener Harness wird nur ergänzt, wenn das Werkzeug die definierten Aktionen und Messdaten nicht reproduzierbar liefern kann.
- Profiling erfolgt nur in kontrollierten Testläufen mit Spark oder einem gleichwertigen externen Profiler; der normale Serverbetrieb benötigt keine dauerhafte Profiler-Abhängigkeit.
- Die Projektgrenzen bleiben verbindlich: mindestens 19,5 TPS im Mittel bei 150 Spielern unter Kampflast, MSPT p95 unter 40 ms, MSPT p99 unter 50 ms und mindestens 800 aktive Custom-Mobs.
- Der Vollastlauf nutzt sechs Regionen, 15 Minuten Aufwärmen und 30 Minuten Messzeit. Bewegung, Kampf, Coin-Drops und Horden werden gemeinsam aktiviert.
- B15 übernimmt den Volllastnachweis der bereits bestehenden Blöcke. Es ersetzt deren fachliche Logik nicht, darf aber die für den gemeinsamen Messvertrag erforderlichen Registrierungen und Adapter ergänzen.
- Die Messung selbst darf den Tick nicht blockieren, keine Datenbankabfrage je Tick auslösen und keine wiederkehrende Aufgabe je Spieler oder Entity einführen.

## Scope Boundaries

- Nicht Bestandteil des B15-Erstumfangs ist ein vollständiges Grafana-Dashboard.
- Nicht Bestandteil des B15-Erstumfangs ist eine fachliche Optimierung von Combat, Coins, Zonen oder Mobs; B15 misst und bewertet deren Zusammenspiel.
- Nicht Bestandteil des normalen Betriebs ist das dauerhafte Aktivieren eines Profilers.
- Der Lasttest ist ein B15-Nachweis und keine nachträgliche Fertigstellungsbedingung für B05, B08b, B09 oder B10.
