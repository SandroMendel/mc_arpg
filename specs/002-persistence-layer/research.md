# Phase 0 Research: B02 · Persistenz-Layer

Alle offenen technischen Fragen aus dem Technical Context sind hier aufgelöst. Es bleibt keine
`NEEDS CLARIFICATION`-Markierung übrig.

Alle Versionsangaben wurden am 2026-08-19 gegen Maven Central geprüft, nicht aus dem Gedächtnis
gesetzt.

## Datenbank-Zugriffsschicht

**Decision**: Direktes JDBC mit handgeschriebenen Prepared Statements, gekapselt hinter
Repository-Schnittstellen in `rpg-core`. Kein ORM, kein Abfragegenerator.

**Rationale**: Die Abfragemenge von B02 ist klein und fest — Spielerzustand laden/schreiben,
Tagesstatistik hochzählen, Item-Instanzen je Spieler, Prüfprotokoll anhängen. Für diesen Umfang
liefert eine Abstraktionsschicht keinen Gewinn, kostet aber genau die Kontrolle, auf die es hier
ankommt: Batch-Größen, `INSERT ... ON CONFLICT`-Formulierung und der Zeitpunkt jedes
Netzwerk-Roundtrips sind bei B02 keine Implementierungsdetails, sondern die Anforderung selbst
(FR-002, FR-007, SC-005). Das folgt demselben Muster wie die Entscheidung in B01, eine eigene
Registry statt eines DI-Frameworks zu bauen: explizite Mechanik dort, wo das Verhalten die
Anforderung ist.

**Alternatives considered**:

- **jOOQ 3.21.7**: Typsichere SQL-DSL mit exzellenter PostgreSQL-Unterstützung. Verworfen, weil
  die Codegenerierung ein Schema oder eine laufende Datenbank **zur Buildzeit** braucht — das
  koppelt den Build an eine Datenbankinstanz und macht ihn auf einem frischen Rechner
  unbrauchbar, solange kein Container läuft. Zusätzlich eine große Abhängigkeit, die mitgeliefert
  werden müsste.
- **Hibernate/JPA**: Bringt eigenes Dirty-Tracking und eigenes Write-Behind mit, was oberflächlich
  genau nach B02 aussieht. Verworfen, weil das Session-Modell auf kurzlebige Transaktionen
  ausgelegt ist, während hier ein über die gesamte Sitzung lebender, im Speicher autoritativer
  Zustand gehalten wird (Constitution IV). Der Flush-Zeitpunkt wäre dann Hibernate-Mechanik statt
  einer nachvollziehbaren Projektregel — und genau dieser Zeitpunkt ist in FR-003/FR-011
  spezifiziert.
- **JDBI 3**: Leichtgewichtiges Mapping, guter Mittelweg. Verworfen, weil sein Nutzen
  (Reflection-basiertes Zeilen-Mapping) bei einer Handvoll Aggregaten den zusätzlichen
  Abhängigkeits- und Reflection-Aufwand nicht trägt.

## Verbindungspool und Dimensionierung

**Decision**: HikariCP 7.1.0, aufgeteilt in **zwei getrennte Pools**:

| Pool | Standardgröße | Zweck |
|---|---|---|
| `rpg-write` | 8 | Autosave-Batches, Shutdown-Flush, Statistik-Schreibvorgänge |
| `rpg-login` | 4 | Ausschließlich Laden von Spielerzustand beim Verbinden |

**Rationale**: FR-008 verlangt, dass der Anmeldepfad **nie** auf eine freie Verbindung wartet. Mit
einem gemeinsamen Pool ist das eine Hoffnung, die von der Batch-Dauer abhängt: Ein großer
Autosave-Batch kann alle Verbindungen belegen, und der nächste Login wartet. Zwei getrennte Pools
machen die Anforderung strukturell wahr statt statistisch wahrscheinlich — Schreiblast kann den
Login-Pfad nicht mehr aushungern.

Die Gesamtgröße folgt der bekannten Faustregel `Verbindungen ≈ (Kerne × 2) + Datenträgeranzahl`.
Bei dem in `06-open-questions.md` empfohlenen Zielprofil (6–8 dedizierte Kerne, NVMe) landet man
bei 12–17; 12 Verbindungen insgesamt liegen darin. Mehr Verbindungen wären hier kontraproduktiv:
PostgreSQL läuft auf **derselben** Maschine wie der Spielserver und konkurriert mit ihm um
dieselben Kerne — jede zusätzliche aktive Verbindung geht direkt vom Tick-Budget ab (ADR-002).

**Alternatives considered**:

- **Ein gemeinsamer Pool mit Prioritäten**: HikariCP kennt keine Priorisierung; sie müsste über
  eine eigene Warteschlange nachgebaut werden — mehr Code für ein schwächeres Ergebnis.
- **Größerer Pool (30+)**: Bei einer lokalen Datenbank auf geteilten Kernen verschlechtert das
  den Durchsatz, statt ihn zu verbessern, weil PostgreSQL-Prozesse dem Server-Tick Rechenzeit
  wegnehmen.

## Schema-Migrationen

**Decision**: Flyway 13.3.0 (`flyway-core` + `flyway-database-postgresql`) mit versionierten
SQL-Dateien unter `db/migration`.

**Rationale**: Reines SQL in nummerierten Dateien ist die nachvollziehbarste Form einer Migration —
sie ist in einem Code-Review lesbar und im Fehlerfall direkt gegen die Datenbank ausführbar.
Flyway prüft angewendete Migrationen per Prüfsumme und erkennt damit nachträglich veränderte
Migrationsdateien, was FR-013 („bereits angewendete Schritte nicht erneut ausführen") absichert.

**Alternatives considered**:

- **Liquibase 5.0.3**: Mächtiger bei datenbankübergreifender Portabilität durch abstrakte
  Changelogs. Genau dieser Vorteil ist hier wertlos — PostgreSQL ist per ADR-003 fest gesetzt.
  Die abstrakte Changelog-Ebene würde PostgreSQL-spezifische Konstrukte (`JSONB`, partielle
  Indizes, `ON CONFLICT`) hinter einer Übersetzungsschicht verstecken, die niemand braucht.
- **Eigene Migrationstabelle**: Für B01 war „selbst bauen" die richtige Antwort (Registry), hier
  nicht: Prüfsummenprüfung, Sperren gegen parallele Migration und Reparaturpfade sind viel
  Detailarbeit für ein gelöstes Problem.

## Ablage der Fremdbibliotheken (Paketierung)

**Decision**: Treiber, Pool und Flyway werden **nicht** ins Plugin-Jar geschattet, sondern über
den `libraries:`-Abschnitt in `plugin.yml` deklariert. Paper löst sie beim Start aus Maven Central
auf und lädt sie in einen isolierten Klassenlader.

**Rationale**: B01 hat als ausdrückliche Eigenschaft erreicht, dass das Plugin-Jar keine einzige
Fremdklasse enthält — genau wegen des in B01s `research.md` benannten Klassenlader-Konfliktrisikos
in einem geteilten Bukkit-Prozess. B02 braucht zwangsläufig drei Fremdbibliotheken; sie zu
schatten würde diese Eigenschaft aufgeben und hätte insbesondere beim JDBC-Treiber realen
Konfliktcharakter, weil andere Plugins denselben Treiber mitbringen. Der `libraries:`-Mechanismus
ist die dafür vorgesehene Lösung: geprüft in der Paper-API 26.2 über
`PluginDescriptionFile.getLibraries()`.

**Konsequenz**: Der erste Serverstart benötigt Netzwerkzugriff auf Maven Central; danach sind die
Artefakte lokal zwischengespeichert. Das ist auf dem Ziel-VPS unkritisch und wird in
`quickstart.md` als Voraussetzung genannt.

**Alternatives considered**:

- **Shadow-Plugin mit Relocation**: Funktioniert, verdreifacht aber die Jar-Größe und verlagert
  das Konfliktrisiko in eine Relocation-Konfiguration, die bei jeder neuen Abhängigkeit gepflegt
  werden muss.
- **Paper `PluginLoader` mit `MavenLibraryResolver`**: Mächtiger (eigene Repositories möglich),
  erfordert aber den Wechsel auf `paper-plugin.yml`. Alle drei Abhängigkeiten liegen auf Maven
  Central, also bringt der Mehraufwand hier nichts. Bleibt als Rückfallweg dokumentiert, falls
  später eine Abhängigkeit aus einem anderen Repository nötig wird.

## Ablageform des Zustands: Spalten statt Blob

**Decision**: Aggregate werden in **echten relationalen Spalten** abgelegt. `JSONB` ist
ausschließlich für ausdrücklich schemalose Zusatzdaten zulässig und nie für Werte, nach denen
gefiltert, sortiert oder summiert wird.

**Rationale**: Ein serialisierter Blob je Spieler wäre der schnellste Weg zu einer laufenden
Persistenz — und würde B12 (Statistiken & Bestenlisten) unmöglich machen, ohne jeden Datensatz zu
deserialisieren. Ebenso würde ADR-004 (Balancing-Rework ohne Anfassen bestehender Items) an einem
Blob scheitern, in dem berechnete Werte mit eingefroren sind. Spalten halten beides offen.

**Alternatives considered**:

- **Ein `JSONB`-Dokument je Spieler**: Sehr flexibel, keine Migration bei neuen Feldern.
  Verworfen — genau diese Migrationsfreiheit ist der Grund, warum später niemand mehr weiß, welche
  Felder existieren, und Bestenlisten nur noch über Volltabellenscans mit Deserialisierung gingen.

## Puffergrenze bei Datenbankausfall

**Decision**: Obergrenze in **Anzahl vorgemerkter Aggregate**, Standard **50 000**, Warnschwelle
bei 80 %. Nicht in Bytes.

**Rationale**: Der entscheidende Punkt ist, dass Write-Behind mit einer Vormerkung **je Aggregat**
arbeitet, nicht je Änderung. Ein Spieler, der während eines Ausfalls tausend Änderungen erzeugt,
belegt trotzdem genau **eine** Vormerkung — die letzte gewinnt, weil beim Flush ohnehin der
aktuelle Zustand geschrieben wird. Daraus folgt: Der Puffer wächst **nicht mit der Dauer** des
Ausfalls, sondern nur mit der Zahl **unterschiedlicher** Aggregate, die währenddessen berührt
werden.

Rechnerisch: 200 gleichzeitige Spieler erzeugen höchstens 200 Spielerzustands-Vormerkungen, dazu
Statistikeinträge (je Spieler, Kennzahl und Tag) und Item-Instanzen. Selbst bei großzügiger
Schätzung liegt ein mehrstündiger Ausfall bei einigen Tausend Einträgen. Die Grenze von 50 000 ist
damit eine echte Notbremse für den pathologischen Fall (tagelanger Ausfall bei hohem
Spielerdurchsatz) und nicht etwas, das im Normalbetrieb je greift — was genau richtig ist, weil
das Erreichen der Grenze nach FR-009b alle Spieler vom Server trennt.

Die Zählung in Aggregaten statt Bytes ist zudem die einzige Größe, die ein Betreiber
nachvollziehen und im Log sinnvoll lesen kann.

**Alternatives considered**:

- **Grenze in Bytes / Heap-Anteil**: Genauer bezogen auf die eigentliche Gefahr (Speichermangel),
  aber praktisch nicht ermittelbar, ohne die Aggregate zu serialisieren — was den Puffer erst
  teuer machen würde.

## Auslösen des Autosave-Zyklus

**Decision**: Der Flush-Zyklus plant sich nach jedem Durchlauf selbst neu, über eine **neue
Methode `runAsyncDelayed(Duration, Runnable)` in der Scheduler-Abstraktion von B01**.

**Rationale — und hier liegt eine notwendige Änderung an B01**: Die in B01 ausgelieferte
`Scheduler`-Schnittstelle bietet `runSyncAtLocation`, `runSyncOnEntity` und `runAsync`, aber
bewusst **kein** `runRepeating` (siehe `contracts/scheduler.md` in B01). Ein Autosave alle 45
Sekunden braucht jedoch einen zeitgesteuerten Auslöser. Drei Wege wurden geprüft:

1. **Rein zeitstempelbasiert-lazy**, also Flush prüfen, wann immer eine Änderung vorgemerkt wird.
   Verworfen: Bleibt die letzte Änderung die letzte, wird nie wieder geprüft und sie liegt
   beliebig lange ungeschrieben. Ein Absturz danach verletzt FR-006/SC-001.
2. **Eigener `ScheduledExecutorService` in B02**. Verworfen: schafft eine zweite,
   verdeckte Nebenläufigkeitsquelle neben der Scheduler-Abstraktion — genau das, was B01s
   `research.md` beim Event-Bus mit derselben Begründung abgelehnt hat.
3. **`runAsyncDelayed` in B01 ergänzen**, Flush plant sich selbst neu. **Gewählt.**

Das verletzt Constitution II.2 nicht: Die Regel verbietet wiederkehrende Aufgaben **pro Spieler
oder pro Entity**; hier handelt es sich um genau eine Systemaufgabe für den gesamten Server. Und
es verletzt den B01-Contract nicht: Ausgeschlossen war `runRepeating`, nicht eine verzögerte
Ausführung. Paper liefert die Grundlage bereits mit
(`AsyncScheduler.runDelayed(plugin, task, delay, TimeUnit)`), der Adapter ist eine kleine
Ergänzung.

**Konsequenz**: B02 kann nicht begonnen werden, ohne B01 um diese eine Methode zu erweitern. Das
ist als eigene Aufgabe in der Foundational-Phase zu führen, samt Test, und in `02-decisions.md`
als Nachtrag zu ADR-009 festzuhalten.

## Lombok und MapStruct

**Decision**: Beide werden projektweit als **reine Compile-Zeit-Werkzeuge** eingebunden —
`compileOnly` plus `annotationProcessor`, ergänzt um `lombok-mapstruct-binding`. Versionen:
Lombok 1.18.46, MapStruct 1.6.3 (1.7.0 ist noch Beta).

**Rationale**: MapStruct passt sachlich sehr gut zu B02: Es erzeugt zur Compile-Zeit gewöhnlichen
Java-Code für die Abbildung von Datenbankzeilen auf Domänenobjekte — reflection-frei, also genau
das Muster, das B01 mit der reflection-freien Registry bereits gewählt hat. Lombok nimmt
Schreibarbeit bei den klassischen Klassen ab, die kein Record sein können (etwa veränderliche
Puffer- und Zustandsobjekte).

**Empirisch geprüft am 2026-08-19** — nicht angenommen, sondern gegen eine Wegwerf-Sonde im
tatsächlichen Build verifiziert:

- Beide Prozessoren laufen unter **Java 25** gemeinsam durch; Lombok erzeugt Builder und Getter,
  MapStruct erzeugt die Mapper-Implementierung.
- Eine Abbildung **auf einen Record** mit umbenannten Feldern funktioniert; MapStruct nutzt den
  kanonischen Konstruktor.
- Der erzeugte Bytecode enthält **keine** Referenz auf `org.mapstruct` und keine auf `lombok`.
  Damit bleibt die in B01 erreichte Eigenschaft erhalten, dass das ausgelieferte Plugin-Jar keine
  einzige Fremdklasse enthält — nachgeprüft nach dem Bauen des Jars.

**Konsequenz für den Stil**: MapStruct-Mapper werden über ihre erzeugte `*Impl`-Klasse
instanziiert (oder über die Registry verdrahtet), **nie** über `Mappers.getMapper(...)`. Genau
dieser Aufruf wäre die eine API, die das MapStruct-Laufzeit-Jar nachziehen würde — und er wäre
zugleich die reflection-basierte Verdrahtung, gegen die sich B01 bewusst entschieden hat.

**Bekannte Einschränkung**: Lombok greift auf interne javac-Schnittstellen zu und ist damit
strukturell empfindlich gegenüber JDK-Wechseln. Mit Java 25 funktioniert es nachweislich; bei
einem künftigen JDK-Sprung ist Lombok der erste Kandidat für einen Bruch. Da die Domänentypen
dieses Projekts überwiegend Records sind, wäre ein Rückbau jedoch überschaubar und beträfe nur die
wenigen Nicht-Record-Klassen.

**Nebenbefund**: Bei mehreren Annotation-Prozessoren meldet javac unter `-Xlint:all` in der letzten
Runde fälschlich „Annotationen von keinem Prozessor beansprucht", obwohl der Code erzeugt wurde.
Der Build schaltet deshalb gezielt `-Xlint:-processing` ab; alle übrigen Lint-Prüfungen bleiben
aktiv.

## Teststrategie

**Decision**: Testcontainers 1.21.4 mit `postgres:18-alpine`, als **Singleton-Container** über die
gesamte Testsuite, ohne die `@Testcontainers`-JUnit-Erweiterung.

**Rationale**: Constitution VII verlangt Persistenztests gegen eine echte PostgreSQL-Instanz statt
gegen Mocks. Der Singleton-Container (ein Container für alle Testklassen, gestartet beim ersten
Zugriff, vom Betriebssystem beendet) ist deutlich schneller als ein Container je Testklasse und
umgeht zugleich eine offene Frage: Das Projekt nutzt JUnit Jupiter **6.1.3** (von MockBukkit
vorgegeben), während `testcontainers-junit-jupiter` in seinem POM keine JUnit-Version deklariert
und gegen JUnit 5 gebaut ist. Ob die Erweiterung unter JUnit 6 bindungskompatibel ist, ist nicht
aus den Metadaten belegbar — und eine Inkompatibilität würde sich, wie bei MockBukkit in B01
gesehen, womöglich als *übersprungener* statt als fehlgeschlagener Test zeigen. Das Muster ohne
Erweiterung braucht nur `org.testcontainers:postgresql`, das keinerlei JUnit-Abhängigkeit hat, und
umgeht die Frage vollständig.

**Verifiziert am 2026-08-19**: Docker Desktop 29.7.2 (Linux-Container) läuft auf der
Entwicklungsmaschine, `postgres:18-alpine` wurde geladen und gestartet (PostgreSQL 18.6).

**Alternatives considered**:

- **`@Testcontainers`-Erweiterung**: Bequemer, aber mit der oben genannten ungeklärten
  JUnit-6-Kompatibilität und einem Container je Testklasse.
- **Lokal installiertes PostgreSQL**: Widerspricht der Reproduzierbarkeit — Tests würden vom
  Zustand einer manuell gepflegten Instanz abhängen.

## Modulzuordnung

**Decision**: Repository-**Schnittstellen** und Domänentypen liegen in `rpg-core`, die
JDBC-**Implementierungen** in `rpg-persistence`. `rpg-persistence` erhält **keine**
Bukkit-Abhängigkeit.

**Rationale**: Constitution III.2 gibt die Richtung `plugin → platform → core` vor; die in B01
angelegte Gradle-Struktur bildet `plugin → persistence → core` bereits ab. Dass
`rpg-persistence` bukkitfrei bleibt, hat einen konkreten Nutzen: Die Integrationstests brauchen
nur Docker, keinen Serverprozess und kein MockBukkit — die gesamte Persistenzschicht ist damit
gegen eine echte Datenbank prüfbar, ohne Minecraft zu starten.
