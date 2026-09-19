# B15 Research Notes

**Datum**: 2026-09-11

## R1 · Messgrenze und Paper-API

**Decision**: Gesamt-Tickwerte werden über `com.destroystokyo.paper.event.server.ServerTickStartEvent`
und `ServerTickEndEvent` erfasst. Die API wird ausschließlich im synchronen Paper-Ticklistener
verwendet. Das Core-Modul erhält nur primitive Dauerwerte und kennt keine Bukkit-Klassen.

**Rationale**: Die lokal gecachte Paper-API `26.2.build.112-stable` stellt beide öffentlichen Events
bereit. `ServerTickEndEvent` liefert Ticknummer, Tickdauer und Restzeit; zusätzlich bietet die
öffentliche `org.bukkit.Server`-Fassade TPS-/Tickzeitwerte. Damit ist kein NMS- oder Reflection-
Zugriff nötig. Die Messung kann zugleich mit den Anforderungen aus Constitution I und III getestet
werden.

**Alternatives considered**:

- NMS-Zugriff auf den Minecraft-Server: abgelehnt, weil er versionsgebunden ist und gegen die
  gekapselte-NMS-Regel verstößt.
- Polling aus einem Async-Thread: abgelehnt, weil dadurch Paper-Zustand außerhalb des Server-Ticks
  gelesen würde.
- Nur `/tps` und `/mspt`: abgelehnt, weil diese keine Subsystem-IDs und keine B15-Budgetprüfung
  liefern.

## R2 · Bounded windows and alert state

**Decision**: Jedes Subsystem besitzt ein begrenztes Sample-Fenster. Die Core-Logik berechnet
Minimum, Maximum, Mittelwert und Perzentile aus dem Fenster und hält den Alarmzustand als reine
Zustandsmaschine. Die Produktionsuhr ist monoton; Tests injizieren eine kontrollierte Uhr.

**Rationale**: Die 60-Sekunden-Schwelle bei 20 TPS benötigt höchstens etwa 1.200 Tickproben. Ein
begrenztes Fenster verhindert unendliches Wachstum. Die Zustandsmaschine macht Warnung, kritischen
Zustand, Deduplizierung und Erholung deterministisch und ohne Logik im Exporter.

**Alternatives considered**:

- Unbegrenzte Rohdatenhaltung: abgelehnt wegen unkontrolliertem Speicherwachstum.
- Nur Durchschnittswerte: abgelehnt, weil p95/p99 die Projektziele und Ausreißer nicht abbilden.
- Alarmierung direkt aus jedem Scope: abgelehnt, weil dadurch Log-I/O im Tickpfad und Meldungsfluten
  entstehen würden.

## R3 · Export ohne zusätzliche Runtime-Abhängigkeit

**Decision**: B15 schreibt eine dependency-freie Prometheus-Textdatei aus einem unveränderlichen
Snapshot. Schreiben und atomarer Dateitausch laufen async. Es wird kein HTTP-Port im Plugin geöffnet.

**Rationale**: Die bestehende Plugin-JAR bündelt nur eigene Module und vermeidet Third-Party-
Runtime-Klassen. Ein Textfile-Export erfüllt den vereinbarten Prometheus-kompatiblen Export, kann
von einem externen Collector gelesen werden und hält den Tick frei. Ein kaputter Exportpfad darf
die lokale Messung nicht stoppen.

**Alternatives considered**:

- Prometheus-Java-Client in das Plugin bundlen: abgelehnt, weil es die Runtime-/Classloader-
  Oberfläche vergrößert und für den vereinbarten Export nicht erforderlich ist.
- Eingebetteter HTTP-Server: abgelehnt wegen zusätzlicher Netzwerkfläche, Authentifizierung und
  Lebenszykluskomplexität.
- Nur Logausgabe: abgelehnt, weil B15 ausdrücklich exportierbare Metriken für externes Monitoring
  verlangt.

## R4 · Bericht und Scheduling

**Decision**: Ein einziger serverweiter Report-/Export-Zyklus läuft als asynchroner One-shot über
`Scheduler.runAsyncDelayed` und plant sich nach erfolgreichem Lauf erneut. Die Messung selbst bleibt
im Ticklistener synchron und schreibt nur primitive Werte in den Core.

**Rationale**: Die Scheduler-Abstraktion erlaubt bewusst One-shot-Zyklen, während wiederkehrende
Arbeit je Spieler oder Entity verboten bleibt. Fehler stoppen den Zyklus nicht dauerhaft; der
nächste Lauf wird nach der Fehlerbehandlung erneut eingeplant. Der Report arbeitet auf einem
Snapshot und blockiert keinen Tick.

**Alternatives considered**:

- Direkter globaler Bukkit-Repeating-Task: abgelehnt wegen Constitution I und der bestehenden
  Scheduler-Grenze.
- Bericht pro Spieler oder Entity: abgelehnt wegen Constitution II.
- Datei-/Netzwerkzugriff aus `ServerTickEndEvent`: abgelehnt wegen Constitution I.

## R5 · Profiler

**Decision**: B15 integriert keinen Profiler als Plugin-Abhängigkeit. Für Paper wird der gebündelte
Spark-Profiler in einem kontrollierten Lauf verwendet; B15 dokumentiert Start, Dauer, Laufkennung
und Zuordnung des erzeugten Reports.

**Rationale**: Die offiziellen Paper-Dokumente nennen Spark seit 1.21 als bevorzugten Profiler und
zeigen ` /spark profiler start --timeout 600`. Das deckt Minecraft 26.2 ab, vermeidet eine zweite
Profiler-JAR und hält den normalen Betrieb frei von Profiling-Arbeit.

**Alternatives considered**:

- Eigenen Profiler im RPG-Plugin bauen: abgelehnt, weil Paper/Spark die serverweite Ursachenanalyse
  bereits abdeckt.
- Spark dauerhaft als eigene Plugin-JAR erzwingen: abgelehnt, weil Paper bereits eine gebündelte
  Variante bereitstellt und der normale Betrieb keinen Profiler benötigt.

**Sources**:

- [PaperMC Profiling](https://docs.papermc.io/paper/profiling/)
- [PaperMC Commands](https://docs.papermc.io/paper/reference/commands/)

## R6 · Simulierte Spieler

**Decision**: Das Lasttest-Runbook verwendet `mc-pilot` als externes Werkzeug und pinnt Version,
Clientprofil und die beim Lauf verifizierte Serverversion im Manifest. B15 nimmt keine Bibliothek
des Werkzeugs in das Plugin oder den Gradle-Produktionsbuild auf.

**Rationale**: Das Projektziel ist Paper/Minecraft 26.2 auf Java 25. Die aktuelle Projektseite von
`mc-pilot` nennt 26.2 als verifiziertes Clientprofil, Java 25+ für 26.x, mehrere Clients und
steuerbare Bewegungs-, Kampf-, Chat- und Inventaraktionen. Das ist näher am gewünschten Verhalten
echter Spieler als ein reiner Verbindungs-Stresstest. Das Werkzeug bleibt austauschbar, weil nur
Manifest und Runbook seine externe Bedienung kennen.

**Alternatives considered**:

- `mineflayer`: abgelehnt als Primärwerkzeug, weil die aktuelle Projektbeschreibung nur bis 1.21.11
  ausweist und damit das 26.2-Protokoll nicht sicher abdeckt.
- `mcbenchmark`: abgelehnt als Primärwerkzeug für den ersten Lauf, weil die aktuelle Validierung
  auf Paper 26.1.2 zielt; es bleibt eine mögliche Alternative nach einem separaten 26.2-Port.
- Reiner Fake-Player-Paper-Plugin-Loadtest: abgelehnt als alleiniger Nachweis, weil er keinen
  echten Client-/Protokollpfad abbildet und das Plugin selbst verändern würde.

**Sources**:

- [mc-pilot README](https://github.com/kzheart/mc-pilot)
- [mcbenchmark README](https://github.com/smashyalts/mcbenchmark)
- [mineflayer README](https://github.com/PrismarineJS/mineflayer/blob/master/docs/README.md)

## R7 · Lasttest-Szenario

**Decision**: Das B15-Szenario hat 15 Minuten Warm-up und 30 Minuten Messung, mindestens 150
Clients, sechs Regionen und 800 Custom-Mobs. Bewegung, B05-Kampf, B08b-Coin-Drops, B09-Zonen-
bewegung und B10-Horden laufen gleichzeitig. Ein nicht erreichter Lastpunkt macht den Lauf ungültig.

**Rationale**: Diese Zusammenstellung entspricht dem vorhandenen B15-Architekturblock und ADR-031.
Die getrennte Warm-up-Phase verhindert, dass Chunk-/Login-Aufwand mit der stabilen Messphase
verwechselt wird. Der harte Gültigkeitscheck verhindert einen scheinbar grünen Lauf mit zu wenig
Last.

**Alternatives considered**:

- Kurzer 5-Minuten-Lauf: abgelehnt, weil Start- und Aufwärmspitzen die Aussage dominieren.
- Isolierte Blocktests: abgelehnt, weil B15 gerade das Zusammenspiel der Blöcke nachweisen muss.
- Unbegrenztes Hochskalieren ohne feste Zielgröße: abgelehnt, weil Ergebnisse dann nicht zwischen
  Läufen vergleichbar wären.

## R8 · Hardwareprofil

**Decision**: Jeder vergleichbare Vollastlauf nimmt CPU-Modell/-Kerne, Arbeitsspeicher, Datenträger,
Betriebssystem, Java-Version, Paper-Build, JVM-Argumente und Plugin-Version in das Manifest auf.
Der erste Referenzlauf nutzt die tatsächliche Zielservermaschine; ein generisches Ersatzprofil wird
nicht als Produktionsnachweis ausgegeben.

**Rationale**: MSPT und TPS hängen stark von CPU, JVM und Weltzustand ab. Ein gemessener, realer
Referenzhost ist aussagekräftiger als ein erfundenes Mindestprofil. Abweichende Hosts werden als
separate Profile behandelt, nicht in einer Zeitreihe vermischt.

**Alternatives considered**:

- Nur CPU/RAM dokumentieren: abgelehnt, weil Java-, Paper- und JVM-Änderungen die Vergleichbarkeit
  ebenfalls beeinflussen.
- Einen einzigen abstrakten Hardwarewert als Ziel setzen: abgelehnt, weil er keinen reproduzierbaren
  Servernachweis ermöglicht.
