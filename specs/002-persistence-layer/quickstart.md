# Quickstart: B02 · Persistenz-Layer validieren

Diese Anleitung prüft, ob die B02-Implementierung die Anforderungen aus `spec.md` erfüllt.
Schnittstellen: siehe `contracts/`. Entitäten und Tabellen: siehe `data-model.md`.

## Voraussetzungen

- JDK 25 und der Gradle-Wrapper aus B01.
- **Docker mit Linux-Containern** — geprüft am 2026-08-19: Desktop 29.7.2, `postgres:18-alpine`
  gestartet (PostgreSQL 18.6). Ohne laufendes Docker sind die Abschnitte 2 bis 6 nicht ausführbar.
- Netzwerkzugriff auf Maven Central beim **ersten** Serverstart: Paper löst Treiber, HikariCP und
  Flyway über den `libraries:`-Abschnitt auf und legt sie lokal ab.

## 1. Domänenregeln ohne Datenbank prüfen (FR-002, FR-006, FR-009a–c, FR-019b)

```bash
./gradlew :rpg-core:test
```

**Erwartet**: Läuft ohne Docker und ohne Server durch. Muss mindestens abdecken:

- Mehrfaches `markDirty` für dieselbe Kennung erzeugt **eine** Vormerkung (Koaleszieren)
- Der Puffer wächst nicht mit der Zahl der Änderungen, sondern nur mit der Zahl unterschiedlicher
  Aggregate — der Nachweis der zentralen Annahme aus `research.md`
- Warnschwelle bei 80 % wird **einmalig** ausgelöst, nicht bei jedem Durchlauf
- Erreichen der Kapazität setzt `overCapacity`, ohne Vormerkungen zu verwerfen
- Ein Schreibvorgang mit veralteter `revision` wird abgelehnt

## 2. Migrationen gegen eine echte Datenbank (User Story 4, FR-012/FR-013, SC-006)

```bash
./gradlew :rpg-persistence:test --tests '*Migration*'
```

**Erwartet**:

1. Gegen eine leere Datenbank wird das vollständige Schema angelegt.
2. Ein zweiter Lauf gegen dieselbe Datenbank wendet **null** Migrationen an.
3. Gegen eine befüllte Datenbank bleiben die Bestandsdaten unverändert — im Test durch Vergleich
   der Zeilen vor und nach der Migration nachzuweisen, nicht durch Augenschein.
4. Eine nachträglich veränderte Migrationsdatei bricht den Start mit einem Prüfsummenfehler ab.

## 3. Write-Behind und Autosave (User Story 2, FR-002/FR-003, SC-005)

```bash
./gradlew :rpg-persistence:test --tests '*WriteBehind*'
```

**Erwartet**:

- Tausend Änderungen an einem Aggregat erzeugen **einen** Schreibvorgang, nicht tausend. Der
  Nachweis läuft über die Zahl der tatsächlich ausgeführten Statements, nicht über die Laufzeit.
- Zwischen zwei Autosave-Zyklen findet ohne Änderung **kein** Schreibvorgang statt.
- Ein Aggregat, das während eines laufenden Batch-Schreibvorgangs erneut verändert wird, ist im
  **nächsten** Durchlauf enthalten und geht nicht verloren.

## 4. Datenbankausfall im laufenden Betrieb (User Story 3, FR-009/FR-010, SC-004)

Der Container wird im Test angehalten und wieder gestartet — ein echter Ausfall, keine Attrappe.

```bash
./gradlew :rpg-persistence:test --tests '*Outage*'
```

**Erwartet**:

1. Während des Ausfalls bleiben alle Vormerkungen erhalten, kein Schreibvorgang verwirft etwas.
2. Nach der Rückkehr werden alle zwischenzeitlich angefallenen Änderungen vollständig
   nachgeschrieben.
3. Ein Anmeldeversuch während des Ausfalls wird abgelehnt; der Spieler erhält **keinen** leeren
   Standardzustand (FR-005a). Dieser Punkt ist der wichtigste der ganzen Anleitung — ein hier
   durchgelassener Standardzustand überschreibt später echten Fortschritt.
4. Bereits „verbundene" Aggregate bleiben unangetastet (FR-005b).

## 5. Sitzungskonflikt und Versionsprüfung (FR-019a–c, SC-007)

```bash
./gradlew :rpg-persistence:test --tests '*Session*'
```

**Erwartet**:

- Ein Ladevorgang wartet auf den abgeschlossenen Flush der Vorsitzung und liefert danach den
  **aktuellen**, nicht den vorherigen Stand.
- Überschreitet das Warten die Frist, wird abgelehnt statt unbegrenzt gewartet.
- Ein Schreibvorgang mit veralteter `revision` wird abgelehnt und protokolliert; der neuere Stand
  bleibt erhalten.

## 6. Anonymisierung (FR-017a–c, SC-010)

```bash
./gradlew :rpg-persistence:test --tests '*Anonymi*'
```

**Erwartet**:

1. Nach der Anonymisierung enthält **keine** Tabelle mehr die ursprüngliche Spielerkennung — im
   Test über alle betroffenen Tabellen zu prüfen, nicht nur über `player_state`.
2. Die Allzeit-Summen der Statistik sind vorher und nachher identisch.
3. Der Vorgang selbst steht im Prüfprotokoll, jedoch ohne die anonymisierte Kennung.
4. Ein Fehler mitten im Vorgang lässt **keinen** teilweise anonymisierten Zustand zurück.

## 7. Shutdown-Flush im Zeitbudget (FR-011/FR-011a, SC-002, SC-011)

1. Testserver mit verbundenen Spielern und ungeschriebenen Änderungen herunterfahren.
2. **Erwartet**: Alle Änderungen sind geschrieben, der Flush endet innerhalb von 8 Sekunden, und
   das Modul meldet sich bei B01 innerhalb seines 10-Sekunden-Budgets ordentlich ab — es wird
   **nicht** zwangsterminiert.
3. Denselben Vorgang mit angehaltener Datenbank wiederholen.
4. **Erwartet**: Der Flush bricht nach 8 Sekunden ab, protokolliert Zahl und Zuordnung der nicht
   geschriebenen Änderungen, und der Serverstopp hängt nicht.

## 8. Lasttest (User Story 2, SC-003)

1. 200 simulierte Sitzungen unter Änderungslast erzeugen.
2. **Erwartet**: Kein Anmeldevorgang wartet auf eine freie Verbindung — nachzuweisen über die
   Wartezeitmessung des Login-Pools, die durchgehend null zeigen muss. Genau dafür existiert der
   getrennte Pool; ein gemeinsamer Pool würde hier gelegentliche Wartezeiten zeigen.
3. **Erwartet**: Die Tickrate bleibt stabil, und B02 bleibt im Tick-Budget von 5 ms.

## Abnahme

Erfüllt, wenn alle acht Abschnitte ohne Abweichung durchlaufen — das deckt SC-001 bis SC-011 aus
`spec.md` vollständig ab.

Die Abschnitte 1 bis 6 laufen vollständig automatisiert und ohne Minecraft-Server; nur die
Abschnitte 7 und 8 brauchen einen laufenden Paper-Server.
