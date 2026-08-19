# Implementation Plan: B03 · Spieler-Session & Datenlebenszyklus

**Branch**: `003-player-session` | **Date**: 2026-08-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-player-session/spec.md`

## Summary

B03 setzt den Lebenszyklus über die von B02 gelieferte Datenhaltung: Laden vor dem Betreten der
Welt, Halten der Sitzung als maßgebliche Quelle, sofortiges Schreiben bei jedem Sitzungsende,
garantiertes Aufräumen. Technischer Kern der Planung ist eine Verschiebung gegenüber dem in der
Spec skizzierten Ablauf: Der Zustand wird bereits in `AsyncPlayerPreLoginEvent` geladen, also bevor
ein Spielerobjekt existiert. Damit wird der gefährlichste Fehlerpfad — ein Spieler mit leerem
Profil, der bestehenden Fortschritt überschreibt — strukturell unmöglich statt nur sorgfältig
behandelt. Der in FR-002 geforderte sichere Zustand bleibt als Rückfallebene bestehen und hat im
Normalbetrieb die Länge null. Dazu kommen die Charakter-Ebene als eigene Tabelle über eine eigene
Migration, ein Sammelladen in einer Datenbankrunde und ein periodischer Abgleich, der Speicherlecks
nicht vermeidet, sondern ausschließt.

## Technical Context

**Language/Version**: Java 25 (ADR-001), Toolchain aus B01 unverändert.

**Primary Dependencies**: Keine neuen. B03 nutzt ausschließlich das, was B01 und B02 bereits
mitbringen — Paper-API (compileOnly), PostgreSQL-Treiber, HikariCP und Flyway über Papers
`libraries:` (ADR-010), Lombok und MapStruct als reine Compile-Zeit-Werkzeuge.

**Storage**: PostgreSQL 18 (ADR-003). Eine neue Tabelle `rpg.character` über die eigene Migration
`V3_1__player_characters.sql`; `rpg.player_state` aus B02 bleibt unverändert.

**Testing**: JUnit 5 (Jupiter 6.1.3) + AssertJ. Lebenszyklusregeln in `rpg-core` serverfrei;
Paper-Anbindung über MockBukkit; Sammelladen und Charakter-Tabelle über Testcontainers 1.21.4 gegen
`postgres:18-alpine`, Singleton-Container wie in B02.

**Target Platform**: Linux-VPS, Paper-Server (Minecraft 26.2 / Java 25), PostgreSQL auf derselben
Maschine.

**Project Type**: Server-Plugin-Block innerhalb des Multi-Modul-Gradle-Projekts aus B01.

**Performance Goals**: Freigabe nach dem Betreten in 95 % der Fälle unter 500 ms (SC-001) — im
gewählten Entwurf faktisch sofort, weil bereits vorgeladen. 200 gleichzeitige Anmeldungen ohne
Zeitüberschreitung und ohne messbare Tickrate-Verschlechterung (SC-005). Der `PlayerMoveEvent`-
Handler des sicheren Zustands muss im Normalbetrieb ein einzelner Feldvergleich sein
(Constitution II).

**Constraints**: Kein blockierender Aufruf im Tick-Pfad (Constitution I.1–I.3); das Laden liegt im
asynchronen Vorlade-Ereignis. Sitzungszustand ist maßgeblich, solange ein Spieler verbunden ist
(Constitution IV). Ladefrist 5 Sekunden (FR-006). Keine Duplikation der B02-Mechanik. `java.sql`
und `DataSource` bleiben auf `rpg-persistence` beschränkt — durchgesetzt durch B02s
`NoDirectDatabaseAccessTest`.

**Scale/Scope**: 100–200 gleichzeitige Spieler (ADR-002), bis zu drei Charaktere je Account.
Konsumenten sind B04, B06, B07, B08, B11 und B12.

### Abgrenzung zu B02 — die zentrale Leitplanke

B02 liefert bereits und wird **nicht** nachgebaut: asynchrones Laden und Schreiben von
Spielerzustand, Write-Behind mit Vormerkung je Aggregat, Autosave alle 45 Sekunden, sofortiges
Schreiben bei Sitzungsende, Shutdown-Flush mit 8-Sekunden-Budget, Zurückstellen eines Ladevorgangs
bis zum Abschluss der Vorsitzung samt Frist und Ablehnung, Versionsprüfung beim Schreiben,
getrennte Login- und Write-Pools.

B03 trägt dazu bei: den Zeitpunkt des Ladens, den sicheren Zustand als Rückfallebene, den
Sitzungs-Cache mit Bereitschaftszustand, die Charakter-Ebene, das Sammelladen, das garantierte
Aufräumen und die Überführung älterer Standfassungen.

Konkret bedeutet das: FR-007 (sofortiges Schreiben bei jedem Sitzungsende) wird durch einen Aufruf
von B02s vorhandenem `onSessionEnd` erfüllt, nicht durch eine zweite Schreiblogik. FR-013
(Zurückstellen bis zum Abschluss der Vorsitzung) durch B02s `SessionHandover`. FR-010
(Shutdown-Flush) durch B02s Modul-Shutdown, der ohnehin läuft.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Prinzip | Prüfung | Status |
|---|---|---|
| I. Nebenläufigkeit | Das Laden läuft im asynchronen Vorlade-Ereignis, also außerhalb des Ticks; ein blockierender Datenbankzugriff ist dort zulässig. Der Abgleich läuft über `Scheduler.runAsyncDelayed` und plant sich selbst neu — kein eigener Thread-Pool. Kein `join()`/`get()` im Tick. Sitzungszustand hängt am Spieler, nicht an globalem veränderlichem Zustand. | PASS |
| II. Performance | Der einzige Tick-Pfad-Eingriff ist der `PlayerMoveEvent`-Handler; er prüft als erstes ein Feld, das im Normalbetrieb `false` ist, und kehrt dann sofort zurück — keine Allokation, keine Map-Abfrage. Sammelladen in einer Datenbankrunde statt drei Roundtrips. Kein wiederkehrender Task je Spieler; genau ein Abgleich für den ganzen Server. | PASS |
| III. Architektur | Lebenszykluslogik in `rpg-core` (bukkitfrei, serverlos testbar), Paper-Anbindung in `rpg-platform`, Datenzugriff in `rpg-persistence`. Richtung `plugin → platform → persistence → core` durch Gradle erzwungen. Andere Blöcke greifen ausschließlich über die Sitzungs-Schnittstelle zu, nie auf Interna. | PASS |
| IV. Datenhaltung | Der Sitzungszustand ist maßgeblich, solange der Spieler verbunden ist. Schemaänderung ausschließlich über eine versionierte Migration im eigenen Versionsraum (`V3_1`). Spielerstände sind versioniert und besitzen einen Migrationspfad (FR-025 bis FR-027). Kein Datenverlust über das von B02 vorgegebene Autosave-Intervall hinaus. | PASS |
| V. Datengetriebenes Design | Ladefrist, Abgleichsintervall und Charakter-Obergrenze liegen in Konfiguration und werden beim Start über B01s `ConfigLoader` gegen ein Schema validiert. Alle spielersichtbaren Texte laufen über die Message-Schlüssel-Ablage aus B01 — kein Text im Code. | PASS |
| VI. Korrektheit & Sicherheit | Der schwerwiegendste Fehler des Blocks — ein leeres Profil, das echten Fortschritt überschreibt — ist durch das Laden vor dem Betreten der Welt strukturell ausgeschlossen: Zum Zeitpunkt der Abweisung existiert kein Spielerobjekt und nichts, was überschrieben werden könnte. Ein Fehler beim Laden eines Spielers beeinträchtigt keinen anderen. Kein Reflection-/NMS-Zugriff. | PASS |
| VII. Tests | Lebenszyklusregeln ohne Server unit-testbar; Paper-Anbindung über MockBukkit; Persistenz gegen echtes PostgreSQL statt gegen Mocks. Zeitverhalten über eine steuerbare Uhr statt über Wartezeiten. Übersprungene Tests werden ausdrücklich mitgeprüft (Lehre aus B01). | PASS |
| VIII. Sprache | Planungsdokumente auf Deutsch; Code, Bezeichner, Tabellen- und Spaltennamen, Konfigurationsschlüssel und Commit-Messages auf Englisch. | PASS |

Keine Verstöße → **Complexity Tracking entfällt.**

**Re-Check nach Phase 1 (Design & Contracts)**: Die Entscheidungen aus `research.md` (Vorladen im
Vorlade-Ereignis, sicherer Zustand als Rückfallebene, eigener Migrations-Versionsraum,
Datenzugriff in `rpg-persistence`, Sammelladen in einer Runde, periodischer Abgleich) und die
Verträge in `contracts/` führen zu keiner neuen Abweichung. Ein Punkt ist ausdrücklich zu beachten,
verletzt aber kein Prinzip:

- Der Entwurf weicht vom in der Spec skizzierten Ablauf ab (Laden vor dem Join statt beim Join).
  Alle Anforderungen bleiben erfüllt — insbesondere FR-002 und FR-004, weil der sichere Zustand als
  Rückfallebene bestehen bleibt. Die Abweichung betrifft den Zeitpunkt, nicht das zugesicherte
  Verhalten, und ist in `research.md` vollständig hergeleitet.

Alle Gates bleiben **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/003-player-session/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

Erweitert die bestehende Struktur; neue Pfade sind mit `+` markiert.

```text
rpg-core/                                       # bukkitfrei, ohne Server testbar
└── src/main/java/rpg/core/session/             # + Lebenszyklus als Domänenlogik
    ├── PlayerSession.java                      # + Sitzung mit Bereitschaftszustand
    ├── SessionState.java                       # + LOADING, READY, UNLOADING, FAILED
    ├── SessionRegistry.java                    # + Cache, genau eine Sitzung je Spieler
    ├── SessionReconciler.java                  # + Abgleich gegen die Verbundenen
    ├── PlayerCharacter.java                    # + Charakter, an eine Klasse gebunden
    ├── CharacterClass.java                     # + WARRIOR, MAGE, ROGUE
    ├── CharacterRepository.java                # + Zugriffsvertrag, Umsetzung in persistence
    ├── SessionBundle.java                      # + Ergebnis des Sammelladens
    ├── StateVersionMigrator.java               # + Überführung älterer Standfassungen
    ├── SessionConfig.java                      # + Ladefrist, Abgleichsintervall
    └── SessionMessageKeys.java                 # + Message-Schlüssel dieses Blocks

rpg-persistence/                                # JDBC-Schicht des GESAMTEN Projekts
├── src/main/java/rpg/persistence/
│   ├── session/SessionModule.java              # + Modul nach B01-Vertrag, verdrahtet alles
│   ├── session/SessionBundleLoader.java        # + Sammelladen in einer Runde
│   └── jdbc/JdbcCharacterRepository.java       # + Charaktere
└── src/main/resources/db/migration/
    └── V3_1__player_characters.sql             # + eigener Versionsraum je Block

rpg-platform/                                   # NUR die Paper-Anbindung
└── src/main/java/rpg/platform/session/
    ├── SessionPreLoadListener.java             # + laedt im Vorlade-Ereignis
    ├── SessionJoinListener.java                # + holt ab, sonst Rueckfallebene
    ├── SessionQuitListener.java                # + entlaedt bei JEDEM Sitzungsende
    ├── SafeStateGuard.java                     # + Bewegungssperre und Immunitaet
    └── PendingSessionStash.java                # + Zwischenablage Vorladen -> Join

rpg-plugin/
└── src/main/java/rpg/plugin/RpgPlugin.java     # ~ registriert SessionModule und die Listener
```

**Warum das Modul in `rpg-persistence` liegt und nicht in `rpg-platform`**: Es verdrahtet
`JdbcCharacterRepository` und `SessionBundleLoader`, die beide dort liegen. Läge es in
`rpg-platform`, bräuchte dieses Modul eine Gradle-Abhängigkeit auf `rpg-persistence` — und damit
wäre die Richtung `plugin → platform → core` aus Constitution III.2 umgekehrt. `PersistenceModule`
aus B02 löst denselben Fall genauso.

**Wer die Listener zusammensetzt**: Nur `rpg-plugin` sieht beide Module. Es bezieht `SessionLifecycle`
und `SessionRegistry` nach dem Bootstrap über B01s Registry und erzeugt damit die Listener aus
`rpg-platform` — dasselbe Muster, mit dem B01 bereits `PreJoinGuard` zusammensetzt. Die Listener
selbst kennen nur Schnittstellen aus `rpg-core` und Paper, nie eine Klasse aus `rpg-persistence`.

**Reihenfolge beim Start**: Die Session-Listener werden **nach** `bootstrap.start()` angemeldet,
weil sie den vom Modul erzeugten `SessionLifecycle` brauchen. Das unterscheidet sie von B01s
`PreJoinGuard`, der bewusst **vor** dem Bootstrap angemeldet wird — er liest nur einen Zustand und
muss von der ersten Sekunde an Verbindungen abweisen können.

**Structure Decision**: Die Dreiteilung folgt Constitution III und der in B02 mechanisch
abgesicherten Kapselung.

`rpg-core/session` enthält alles, was ohne Server und ohne Datenbank prüfbar ist: den
Bereitschaftszustand und seine erlaubten Übergänge, die Regel „genau eine Sitzung je Spieler", die
Charakter-Obergrenze je Klasse, den Abgleich und die Überführung älterer Standfassungen. Das ist
der Teil, an dem die in der Spec beschriebenen Datenverluste entstehen — und der Teil, der deshalb
in schnellen Unit-Tests liegen muss, nicht hinter einem Serverstart.

`rpg-persistence` bekommt den Charakter-Zugriff und das Sammelladen. Bewusst dort und nicht in
einem eigenen B03-Modul: B02 hat mit `NoDirectDatabaseAccessTest` eine Prüfung ausgeliefert, die
`java.sql` außerhalb dieses Moduls verbietet — für alle Folgeblöcke. `rpg-persistence` ist damit
die JDBC-Schicht des Projekts, nicht das Privatmodul von B02.

`rpg-platform/session` enthält ausschließlich die Ereignis-Anbindung. Dass es vier kleine Listener
statt eines großen sind, ist Absicht: Jeder hat genau einen Auslöser, und der gefährliche
Fehlerpfad (Abweisung beim Vorladen) liegt vollständig in einer Datei, die man am Stück lesen kann.

## Complexity Tracking

*Keine Verstöße gegen die Constitution Check-Gates — Abschnitt entfällt.*
