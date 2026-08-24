# Implementation Plan: B10 · Mobs & Horden-Spawning

**Branch**: `010-mobs-spawning` | **Date**: 2026-08-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-mobs-spawning/spec.md`

## Summary

B10 füllt einen Anker, den B09 leer geliefert hat, und löst drei Zusagen ein, die B05, B06 und B08b
seit Monaten offen halten. Eine Kreatur wird zu einer **Art aus der Konfiguration** statt zu einem
Vanilla-Typ; Horden entstehen in B09s benannten Bereichen unter einem harten Budget; das natürliche
Spawnen von Vanilla wird abgeschaltet.

**Der Kern des technischen Entwurfs ist, dass fast nichts Neues gebaut wird.** Die Schnittstellen
stehen, der Scheduler steht, der Chunk-Schlüssel steht, das Attributmodell steht. Was hinzukommt,
sind vier Dinge: eine Konfigurationsdatei, ein Bestand mit drei Zählungen, ein Durchlauf je
bevölkerter Zone, und zwei Zuhörer.

**Phase 0 hat eine Annahme der Spec präzisiert.** Die Vanilla-Unterdrückung (FR-018c) lässt sich
nicht mit einem Mittel erfüllen: Spielregeln halten den Spawner-Durchlauf an und kosten dann nichts,
decken aber gut ein Dutzend `SpawnReason`-Wege nicht ab — Raids, Portale, Verstärkung, Jockeys.
Ein Ereignis-Riegel deckt alles ab, zahlt aber für jeden Kandidaten. Beide zusammen sind billig
*und* vollständig, und in dieser Reihenfolge: die Regeln sorgen dafür, dass der Riegel fast nie
gefragt wird (research.md R1).

**Phase 0 hat außerdem eine Vorgabe entschärft.** Die Architekturvorgabe nennt „ggf. vereinfachte AI
statt Vanilla-Pathfinding". Der Plan tut das **nicht** — er zieht stattdessen `FOLLOW_RANGE` je Art
herunter, was die Suchkosten quadratisch senkt, und lässt Vanillas Pfadfindung stehen. Eigenen
Java-Code gegen einen bereits stark optimierten Pfadfinder zu setzen ist eine Wette, die erst eine
Messung rechtfertigt — und die gehört nach ADR-031 zu B15 (research.md R6).

## Technical Context

**Language/Version**: Java 25 (ADR-001)

**Primary Dependencies**: Paper 26.2 API (nur in `rpg-platform` und `rpg-plugin`), `rpg-core` ohne
Bukkit. Geprüft gegen die tatsächlich ausgelieferte `paper-api-26.2.build.112-stable.jar`:
`GameRules.SPAWN_MOBS` und die sechs verwandten Regeln, `CreatureSpawnEvent.SpawnReason`,
`Attribute.FOLLOW_RANGE`, `EntityTargetLivingEntityEvent`, `EntityRemoveEvent` existieren dort.

**Storage**: **keiner.** Eine Kreatur überlebt keinen Neustart (FR-023), es gibt also nichts zu
persistieren — kein Aggregattyp, keine Migration, keine Tabelle. Der erste Block seit B03 ohne
Persistenz.

**Testing**: JUnit ohne Server für Budget, Auswahl, Aufräumentscheidung und Konfiguration; MockBukkit
für die Zuhörer; eine wiederholbare Messung nach dem Muster von `ZoneLookupBenchmarkTest`. Der
Nachweis unter Volllast gehört zu B15 (ADR-031).

**Target Platform**: Paper-Server, eine Instanz (ADR-002)

**Project Type**: Gradle-Mehrmodulprojekt, `plugin → platform → core`

**Performance Goals**: Ein Spawn-Durchlauf und ein Aufräum-Durchlauf je Zone bleiben zusammen unter
**1 ms** bei 130 Kreaturen — als wiederholbare Messung ohne Server (FR-038, SC-007). Der verbindliche
Zielwert des Blocks (800 aktive Kreaturen bei 150 Spielern, p95 MSPT < 40 ms) bleibt bestehen und
wird in B15 nachgewiesen.

**Constraints**: Paper-API nur im Tick; keine wiederkehrende Aufgabe je Spieler oder je Entität; kein
Datenbankzugriff je Spielereignis; keine Zuweisung im Spawn-Pfad; räumliche Abfragen über einen
Index, nie über lineare Iteration.

**Scale/Scope**: 48 Mob-Arten, 6 Bosse, 6 Regionen mit je zwei Spawn-Bereichen, Budget 800 serverweit.

**Keine offenen Punkte.** Die fünf an `/plan` übergebenen Fragen sind in
[research.md](./research.md) beantwortet (R1 bis R3, R7, R8); sechs weitere kamen beim Nachsehen
dazu und sind dort ebenfalls beantwortet.

## Constitution Check

*GATE: vor Phase 0 bestanden, nach Phase 1 erneut geprüft.*

| Prinzip | Wie dieser Plan es einhält |
|---|---|
| **I · Nebenläufigkeit** | Kein Bukkit-Aufruf außerhalb des Ticks. Das Setzen läuft orts­gebunden (`runSyncAtLocation`), das Entfernen entitätsgebunden — beides über B01s Abstraktion, nie über den globalen Scheduler. Der Bestand hängt am Block, nicht an einem globalen Feld. |
| **II · Performance** | **Der kritische Punkt dieses Blocks.** Keine wiederkehrende Aufgabe je Spieler oder je Entität — ein selbst neu eingeplanter Einmal-Durchlauf **je bevölkerter Zone**, höchstens sechs (R4). Der Boss-Timer wird lazy aus zwei Zeitstempeln gerechnet, nie als laufende Aufgabe (FR-032). Das Chunk-Budget zählt nur belegte Chunks, ohne Boxing, mit B09s gepacktem Schlüssel (R3). Die Vanilla-Unterdrückung setzt vor dem Spawner-Durchlauf an, damit gar keine Kandidaten entstehen (R1). |
| **III · Architektur** | `rpg-core` ohne Bukkit: Budget, Auswahl, Aufräumentscheidung, Boss-Timer, Konfiguration. `rpg-platform` setzt und entfernt Entitäten. Die drei übernommenen Schnittstellen behalten ihre Form; keine zweite wird eingeführt. Zone bleibt `(worldId, Geometrie)` — B10 verweist auf B09s Bereiche und hält keine eigene Geometrie (R10). |
| **IV · Datenhaltung** | Keine. Nichts zu migrieren, nichts zu versionieren — eine Kreatur überlebt keinen Neustart (FR-023). |
| **V · Datengetriebenes Design** | 48 Arten und 6 Bosse in `mobs.yml`, beim Start gegen ein Schema geprüft, Fail-Fast mit Datei/Schlüssel/Grund. Kein Bezeichner einer einzelnen Art im Code — durch `ConfigOnlyMobTest` erzwungen, wie B08 es für Fähigkeiten tut. Anzeigenamen über Message-Schlüssel. |
| **VI · Korrektheit & Sicherheit** | Der Server ist Autorität für jeden Wert. Eine Ausnahme im Spawn- oder Aufräumpfad wird lokal gefangen und protokolliert; sie darf keinen Spieler in einen inkonsistenten Zustand versetzen. Kein Reflection, kein NMS. |
| **VII · Tests** | Jede Regel serverlos getestet. Keine Persistenz, also kein Testcontainer nötig. Die Messung ohne Volllast erfüllt Prinzip VII in der Fassung von ADR-031; der Lasttest bleibt B15 und hält diesen Block nicht offen. |
| **VIII · Sprache** | Dokumentation deutsch, Code und Konfigurationsschlüssel englisch. |

### Erneute Prüfung nach Phase 1

**Bestanden, ohne Abweichung.** Zwei Stellen wurden dabei nachgeschärft:

- **Prinzip II und der Spawn-Durchlauf.** Der erste Entwurf hätte je Zone *und* je Bereich geplant.
  Das sind bei zwölf Bereichen zwölf Aufgaben statt sechs, ohne Gewinn — der Bereich wird innerhalb
  des Zonendurchlaufs gewählt. Zusammengelegt.
- **Prinzip II und `ChunkTable`.** Die Versuchung war, B09s Tabelle wiederzuverwenden. Sie ist beim
  Laden gebaut und danach ausschließlich gelesen; genau das macht sie zuweisungsfrei. Das
  Chunk-Budget ändert sich dagegen bei jedem Setzen. Dieselbe Klasse für beides hätte die Zusage
  aufgegeben, die die eine trägt. Übernommen wird der gepackte Schlüssel, nicht die Struktur (R3).

## Project Structure

### Documentation (this feature)

```text
specs/010-mobs-spawning/
├── plan.md              # Diese Datei
├── research.md          # Phase 0 — elf Fragen, elf Antworten
├── data-model.md        # Phase 1 — MobKind, HordeSpec, Budget, Bestand, BossState
├── quickstart.md        # Phase 1 — drei Abschnitte, 34 Prüfschritte
├── contracts/
│   ├── mob-api.md       # Was B10 herausgibt und was es übernimmt
│   └── mob-config.md    # mobs.yml
├── checklists/
│   └── requirements.md  # Qualitätsprüfung der Spec
└── tasks.md             # Phase 2 — NICHT von /speckit-plan erzeugt
```

### Source Code (repository root)

```text
rpg-core/src/main/java/rpg/core/mob/
├── MobKind.java                 # Eine Art: Kennung, Basis, Level, Werte, XP, Coins
├── MobKinds.java                # Öffentliche Abfrage (contracts/mob-api.md §2)
├── MobConfig.java               # Der validierte Inhalt von mobs.yml
├── MobConfigSchema.java         # Prüfung beim Start, Fail-Fast
├── MobMessageKeys.java          # Anzeigenamen und Boss-Meldungen
├── HordeSpec.java               # Was in einer Zone steht: Bereiche, Arten, Gewichte, Boss
├── Budget.java                  # Die drei harten Grenzen; die schärfste entscheidet
├── HordeRegistry.java           # Der Bestand mit drei Zählungen
├── ChunkCount.java              # long→int, nur belegte Chunks, ohne Boxing (R3)
├── SpawnPlanner.java            # WAS wo gesetzt werden soll - ohne Bukkit, testbar
├── CleanupRule.java             # WER weg soll - ohne Bukkit, testbar
├── DensityScaling.java          # Zieldichte aus Spielerzahl, gekappt am Budget
├── BossState.java               # Ein Boss je Region, Timer lazy aus Zeitstempeln
├── Hordes.java                  # Öffentliche Abfrage (contracts/mob-api.md §3)
├── MobModule.java               # Start, Konfiguration, Nachladen
└── package-info.java            # Die Grenze dieses Blocks, wie in jedem anderen

rpg-platform/src/main/java/rpg/platform/mob/
├── PaperMobPlacer.java          # Setzt und entfernt Entitäten; die einzige Bukkit-Naht dafür
├── MobKindTag.java              # rpg:mob_kind und rpg:mob_zone im PDC (R2)
├── VanillaSpawnSuppressor.java  # Spielregeln + CreatureSpawnEvent-Riegel (R1)
├── HordeSweep.java              # Der Einmal-Durchlauf je bevölkerter Zone (R4)
├── PaperMobStats.java           # Ersetzt PaperMobStatProvider - Art statt Vanilla-Typ (R5)
├── CloneAggroListener.java      # EntityTargetLivingEntityEvent umlenken (R9)
└── package-info.java

rpg-plugin/src/main/java/rpg/plugin/
└── RpgPlugin.java               # Verdrahtung: Modul, Zuhörer, Durchlauf, Provider-Tausch

rpg-plugin/src/main/resources/
├── mobs.yml                     # NEU - 48 Arten, 6 Bosse, Budgets, Horden
└── messages.yml                 # Anzeigenamen der Arten

Tests:
rpg-core/src/test/java/rpg/core/mob/          # Budget, Auswahl, Aufräumen, Boss, Schema
│   └── HordeBudgetBenchmarkTest.java         # Die Messung ohne Volllast (FR-038)
│   └── ConfigOnlyMobTest.java                # SC-001 und FR-003
rpg-platform/src/test/java/rpg/platform/mob/  # Unterdrückung, Vermerk, Umlenken
rpg-plugin/src/test/java/rpg/plugin/          # FullBootstrapTest: Modul verdrahtet
```

**Structure Decision**: Vier Module, wie jeder Block vor ihm, und die Aufteilung folgt einer Frage —
*braucht es dafür einen laufenden Server?* Budget, Auswahl, Aufräumentscheidung, Dichte, Boss-Timer
und Schema brauchen keinen, also liegen sie in `rpg-core` und sind serverlos geprüft. Setzen,
Entfernen, Vermerken, Unterdrücken und Umlenken brauchen einen, also liegen sie in `rpg-platform`.

Das ist auch die Antwort auf die Größe dieses Blocks: der TPS-kritische Teil ist die
*Entscheidung*, was gesetzt wird — und die ist vollständig ohne Bukkit messbar.

## Complexity Tracking

> Keine Abweichung von der Constitution, also nichts zu rechtfertigen.

Drei Dinge, die wie eine Abweichung aussehen und keine sind:

| Sieht aus wie | Ist aber |
|---|---|
| **Ein Durchlauf je Zone** — Prinzip II verbietet wiederkehrende Aufgaben | Verboten sind sie *je Spieler und je Entität*. Sechs Zonen sind weder. Spawnen ist von Natur aus periodisch: das Ereignis „hier fehlt eine Kreatur" gibt es nicht. Was sich vermeiden lässt, ist die Periodizität je Kreatur — und die vermeidet dieser Zuschnitt (R4). |
| **Ein `CreatureSpawnEvent`-Riegel** — ein Ereignis je Spawn-Versuch | Nur, wenn es Versuche gibt. Die Spielregeln davor halten den Spawner-Durchlauf an; der Riegel sieht danach die Handvoll Wege, die keiner Regel unterliegen (R1). |
| **`FOLLOW_RANGE` statt eigener AI** — die Architekturvorgabe nennt eine vereinfachte AI | Sie nennt sie mit „ggf.". Vanillas Pfadfindung ist bereits optimiert; sie zu ersetzen ist eine Wette, die eine Messung rechtfertigen muss, und die gehört zu B15. FR-035 bis FR-037 werden mit dem kleineren Mittel erfüllt — nicht vertagt (R6). |

**Was dieser Block bewusst nicht baut:** Elite- und Champion-Varianten, den Dungeon-Boss mit Phasen
und Fähigkeiten, Beute über Coins hinaus, eine Persistenz für Kreaturen. Alles vier steht in der
Spec unter *Assumptions* mit Begründung.
