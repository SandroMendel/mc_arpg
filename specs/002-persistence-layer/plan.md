# Implementation Plan: B02 · Persistenz-Layer

**Branch**: `002-persistence-layer` | **Date**: 2026-08-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-persistence-layer/spec.md`

## Summary

B02 liefert die gesamte dauerhafte Datenhaltung des Projekts: Write-Behind mit Vormerkung je
Aggregat, gesammeltes Schreiben im 45-Sekunden-Takt, vollständiger Flush bei Sitzungsende und
Shutdown, Wiederanlauf nach Verbindungsverlust und versionierte Schema-Migrationen. Technischer
Ansatz: direktes JDBC hinter Repository-Schnittstellen in `rpg-core`, HikariCP in **zwei
getrennten Pools** (Schreiben und Anmeldung), Flyway für Migrationen, PostgreSQL-Treiber und die
übrigen Fremdbibliotheken über Papers `libraries:`-Mechanismus statt Shading. Die Persistenzschicht
bleibt bukkitfrei und wird gegen eine echte PostgreSQL-Instanz in Testcontainers geprüft.

## Technical Context

**Language/Version**: Java 25 (ADR-001), Toolchain über Gradle Java-Toolchains fixiert — wie in
B01 bereits eingerichtet.

**Primary Dependencies**: PostgreSQL-JDBC-Treiber 42.7.13, HikariCP 7.1.0, Flyway 13.3.0
(`flyway-core` + `flyway-database-postgresql`). Alle drei werden **nicht** ins Jar geschattet,
sondern über `libraries:` in `plugin.yml` zur Laufzeit von Paper aufgelöst (siehe `research.md`).
`rpg-persistence` hat **keine** Bukkit-/Paper-Abhängigkeit.

Projektweit zusätzlich Lombok 1.18.46 und MapStruct 1.6.3 als **reine Compile-Zeit-Werkzeuge**
(`compileOnly` + `annotationProcessor`, mit `lombok-mapstruct-binding`). Beide hinterlassen keine
Laufzeitabhängigkeit — am erzeugten Bytecode geprüft, siehe `research.md`. Bereits eingerichtet und
gegen einen vollständigen Clean-Build unter Java 25 verifiziert.

**Storage**: PostgreSQL 18 (ADR-003), auf derselben Maschine wie der Spielserver
(`06-open-questions.md`, Abschnitt „Betrieb"). Relationale Spalten, `JSONB` nur für ausdrücklich
schemalose Zusatzdaten.

**Testing**: JUnit 5 (Jupiter 6.1.3) + AssertJ für die Domänenlogik in `rpg-core` ohne laufenden
Server. Integrationstests der Persistenzschicht über Testcontainers 1.21.4 gegen
`postgres:18-alpine` als Singleton-Container, ohne die `@Testcontainers`-Erweiterung (Begründung
in `research.md`). Docker verifiziert am 2026-08-19: Desktop 29.7.2, Linux-Container,
PostgreSQL 18.6 gestartet.

**Target Platform**: Linux-VPS, Paper-Server (Minecraft 26.2 / Java 25), PostgreSQL auf derselben
Maschine.

**Project Type**: Server-Plugin-Modul innerhalb des in B01 angelegten Multi-Modul-Gradle-Projekts.

**Performance Goals**: Kein Datenbankzugriff je Spielereignis (FR-002, SC-005). Autosave-Intervall
45 s (30–60 s konfigurierbar). Shutdown-Flush ≤ 8 s innerhalb des von B01 gewährten
10-Sekunden-Modulbudgets (FR-011/FR-011a, SC-011). Anmeldepfad wartet nie auf eine Verbindung
(FR-008, SC-003) — strukturell durch einen eigenen Login-Pool. Tick-Budget ≤ 5 ms für alles, was
B02 im Tick tut; im Regelfall ist das nur das Setzen einer Vormerkung.

**Constraints**: Kein blockierender Aufruf im Tick-Pfad (Constitution I.1–I.3); einzige erlaubte
Blockade ist der Shutdown-Flush außerhalb des Ticks. Speicher ist autoritativ, solange ein Spieler
online ist (Constitution IV). Puffergrenze 50 000 vorgemerkte Aggregate, Warnung ab 80 %.
PostgreSQL teilt sich die Kerne mit dem Server-Tick — Poolgröße deshalb bewusst klein (12 gesamt).

**Scale/Scope**: 100–200 gleichzeitige Spieler auf einer Instanz (ADR-002). Vier Aggregate zum
Start (Spielerzustand, Tagesstatistik, Item-Instanz, Prüfprotokoll); B03, B06, B11 und B12 sind
die Konsumenten.

### Notwendige Ergänzung an B01

B02 lässt sich nicht umsetzen, ohne B01 um **eine** Methode zu erweitern:
`Scheduler.runAsyncDelayed(Duration, Runnable)`. Der Autosave-Zyklus braucht einen zeitgesteuerten
Auslöser; B01s Scheduler bietet bewusst kein `runRepeating`, und die geprüften Alternativen
(rein lazy auswerten, eigener Thread-Pool in B02) verletzen entweder FR-006 oder Constitution I.
Vollständige Herleitung in `research.md`, Abschnitt „Auslösen des Autosave-Zyklus". Die Ergänzung
ist als eigene Aufgabe in der Foundational-Phase zu führen; die Entscheidung selbst ist in
**ADR-010** (`02-decisions.md`) festgehalten.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Prinzip | Prüfung | Status |
|---|---|---|
| I. Nebenläufigkeit | Alle Datenbankzugriffe laufen über `Scheduler.runAsync`; im Tick geschieht nur das Setzen einer Vormerkung. Kein `join()`/`get()` im Tick-Pfad. Der Shutdown-Flush darf blockieren, liegt aber ausdrücklich außerhalb des Ticks (FR-011) und ist die einzige Ausnahme. Ergebnisse asynchroner Arbeit gelangen über die Scheduler-Abstraktion zurück, nicht über geteilten veränderlichen Zustand. Keine zweite Nebenläufigkeitsquelle: `runAsyncDelayed` wird in B01 ergänzt, statt in B02 einen eigenen Thread-Pool zu öffnen. | PASS |
| II. Performance | Kein Datenbankzugriff je Spielereignis (FR-002); Vormerkung je Aggregat statt je Änderung, dadurch koaleszieren beliebig viele Änderungen zu einem Schreibvorgang. Genau **eine** wiederkehrende Systemaufgabe (Autosave) — keine Aufgabe pro Spieler oder pro Entity, die II.2 verbietet. Schreibvorgänge gebatcht mit Prepared Statements und `ON CONFLICT`. Poolgröße bewusst klein, weil PostgreSQL sich die Kerne mit dem Tick teilt. | PASS |
| III. Architektur | Repository-Schnittstellen und Domänentypen in `rpg-core` (bukkitfrei, serverlos testbar); JDBC-Implementierungen in `rpg-persistence`, ebenfalls ohne Bukkit-Abhängigkeit. Richtung `plugin → persistence → core` durch Gradle-Modulgrenzen erzwungen. Andere Blöcke greifen ausschließlich über die Repository-Schnittstellen zu (FR-015), nie auf Tabellen oder Interna. | PASS |
| IV. Datenhaltung | Schemaänderungen ausschließlich über versionierte Flyway-Migrationen (FR-012). Speicher-Cache ist autoritativ, solange der Spieler online ist. Spielerdaten versioniert mit Migrationspfad (FR-021). Item-Instanzen speichern Vorlagenkennung und gewürfelte Werte, niemals berechnete Endwerte (ADR-004) — durch relationale Spalten statt Blob abgesichert. Kein Datenverlust über das Autosave-Intervall hinaus (FR-006). | PASS |
| V. Datengetriebenes Design | Verbindungsparameter, Pool-Größen, Autosave-Intervall und Puffergrenze liegen in Konfiguration und werden beim Start über B01s `ConfigLoader` gegen ein Schema validiert (FR-022); Fail-Fast bei Fehlern. Keine hartcodierten Spielertexte — die Ablehnungsmeldungen aus FR-005a/FR-009b laufen über Message-Schlüssel. | PASS |
| VI. Korrektheit & Sicherheit | Server bleibt alleinige Autorität. Ein nicht lesbarer Datensatz wird lokal begrenzt behandelt (FR-020) und reißt weder Server noch andere Spieler mit. Kein Spieler erhält je einen erfundenen Standardzustand (FR-005a) — der Fehlerfall führt zur Ablehnung, nicht zu stillem Weiterlaufen. Kein Reflection-/NMS-Zugriff. Zugangsdaten aus Konfiguration, nicht im Code. | PASS |
| VII. Tests | Persistenz wird gegen eine echte PostgreSQL-Instanz via Testcontainers geprüft, nicht gegen Mocks — Docker ist auf der Entwicklungsmaschine verifiziert verfügbar. Die Regeln der Domänenschicht (Vormerkung, Koaleszieren, Puffergrenze, Versionskonflikt) sind in `rpg-core` ohne Server und ohne Datenbank unit-testbar. | PASS |
| VIII. Sprache | Planungsdokumente auf Deutsch; Code, Bezeichner, Tabellen- und Spaltennamen, Konfigurationsschlüssel und Commit-Messages auf Englisch. | PASS |

Keine Verstöße → **Complexity Tracking entfällt.**

**Re-Check nach Phase 1 (Design & Contracts)**: Die in `research.md` getroffenen Entscheidungen
(direktes JDBC, zwei getrennte Pools, Flyway, `libraries:` statt Shading, Spalten statt Blob,
Puffergrenze in Aggregaten, Singleton-Testcontainer) und die in `contracts/` festgehaltenen
Schnittstellenverträge führen zu keiner neuen Abweichung. Zwei Punkte sind ausdrücklich zu
beachten, verletzen aber kein Prinzip:

- Die Erweiterung von B01 um `runAsyncDelayed` ist eine Änderung an einem fertigen Block. Sie ist
  additiv, bricht keinen bestehenden Vertrag und wurde gegenüber den beiden Alternativen begründet
  gewählt (siehe `research.md`). Festgehalten in ADR-010.
- Mit B02 enthält die Auslieferung erstmals Fremdbibliotheken. Der `libraries:`-Mechanismus hält
  sie aus dem Plugin-Jar heraus, sodass die in B01 erreichte Eigenschaft „kein Fremdcode im Jar"
  bestehen bleibt.

Alle Gates bleiben **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/002-persistence-layer/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

Erweitert die in B01 angelegte Struktur; neue Pfade sind mit `+` markiert.

```text
rpg-core/                                   # bukkitfrei, ohne Server testbar
├── src/main/java/rpg/core/
│   ├── scheduler/
│   │   └── Scheduler.java                  # + runAsyncDelayed(Duration, Runnable)
│   └── persistence/                        # + Domänenseite der Persistenz
│       ├── Repository.java                 # + gemeinsame Basisschnittstelle
│       ├── PlayerStateRepository.java      # + Aggregat: Spielerzustand
│       ├── StatisticsRepository.java       # + Aggregat: Tagesstatistik
│       ├── ItemInstanceRepository.java     # + Aggregat: Item-Instanz
│       ├── AuditLogRepository.java         # + Aggregat: Prüfprotokoll
│       ├── DirtyTracker.java               # + Vormerkung je Aggregat, Koaleszieren
│       ├── WriteBehindBuffer.java          # + Puffer inkl. Obergrenze und Warnschwelle
│       ├── PersistenceException.java       # + Fehlertypen
│       └── StaleVersionException.java      # + Versionskonflikt (FR-019b)
└── src/test/java/rpg/core/persistence/     # + Unit-Tests, ohne DB und ohne Server

rpg-persistence/                            # JDBC-Implementierung, KEINE Bukkit-Abhängigkeit
├── src/main/java/rpg/persistence/
│   ├── PersistenceModule.java              # + Modul nach B01-Vertrag, verdrahtet alles
│   ├── ConnectionPools.java                # + getrennter Schreib- und Login-Pool
│   ├── SchemaMigrator.java                 # + Flyway-Anbindung, Fail-Fast
│   ├── FlushCycle.java                     # + Autosave-Schleife, plant sich selbst neu
│   ├── OutageState.java                    # + Ausfallerkennung, Wiederanlauf, Puffergrenze
│   └── jdbc/                               # + je Repository eine JDBC-Implementierung
│       ├── JdbcPlayerStateRepository.java
│       ├── JdbcStatisticsRepository.java
│       ├── JdbcItemInstanceRepository.java
│       └── JdbcAuditLogRepository.java
├── src/main/resources/db/migration/        # + versionierte SQL-Migrationen
│   └── V1__baseline.sql
└── src/test/java/rpg/persistence/          # + Integrationstests gegen Testcontainers
    └── support/PostgresContainer.java      # + Singleton-Container für die ganze Suite

rpg-platform/
└── src/main/java/rpg/platform/scheduler/
    └── PaperSchedulerAdapter.java          # ~ um runAsyncDelayed ergänzt

rpg-plugin/
├── src/main/java/rpg/plugin/RpgPlugin.java # ~ registriert PersistenceModule
└── src/main/resources/plugin.yml           # ~ libraries: Treiber, HikariCP, Flyway
```

**Structure Decision**: Die Zweiteilung folgt Constitution III. `rpg-core/persistence` enthält die
Repository-Schnittstellen und die gesamte Logik, die ohne Datenbank prüfbar ist — Vormerkung,
Koaleszieren, Puffergrenze, Versionsvergleich. `rpg-persistence` enthält ausschließlich das, was
eine Datenbank braucht: Pools, Migrationen, SQL. Diese Trennung ist nicht kosmetisch: Sie ist der
Grund, warum die Regeln aus FR-002, FR-006, FR-009a–c und FR-019b als schnelle Unit-Tests laufen
und nur die tatsächliche SQL-Ausführung einen Container braucht.

Bewusst **nicht** eingeführt wird eine eigene Abstraktion über JDBC hinaus (kein eigenes
Mini-ORM): Die Abfragemenge ist klein und fest, und die präzise Kontrolle über Batch und `UPSERT`
ist hier die Anforderung selbst, nicht ein Implementierungsdetail.

## Complexity Tracking

*Keine Verstöße gegen die Constitution Check-Gates — Abschnitt entfällt.*
