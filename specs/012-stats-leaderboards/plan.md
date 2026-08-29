# Implementation Plan: B12 · Statistiken & Leaderboards

**Branch**: `012-stats-leaderboards` | **Date**: 2026-08-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/012-stats-leaderboards/spec.md`

## Summary

B12 zählt, was die Blöcke darunter ohnehin tun, und macht daraus Ranglisten: zweiundzwanzig
Einzellisten als Ehre, eine dreiundzwanzigste — die Saison-Gesamtwertung — mit Preis. Dazu ein
eigenes Profil mit drei privaten Werten, ein Fremdprofil ohne sie, und ein Hologramm im Hub.

**Der Kern des Entwurfs ist, dass an vier Stellen etwas benutzt statt gebaut wird.**

1. **Das Fundament steht seit B02 und hat nie einen Schreiber gesehen** (research.md R10).
   `player_statistic_daily`, `StatisticsRepository`, `JdbcStatisticsRepository`, die Eintragung in
   `FlushCycle.WRITE_ORDER`, die Verdrahtung in `PersistenceModule` — alles vorhanden, ein Index
   trägt sogar den Kommentar *„Leaderboards (B12)"*. Produktionsaufrufe von `increment()`: null.
   B12 legt **keinen neuen Aggregattyp** an; die dreifache Registrierung aus ADR-015 ist für die
   Statistik längst erledigt.
2. **Die Ranglisten brauchen vier Sichten, nicht zweiundzwanzig** (R1). Die Metrik gehört in die
   *Gruppierung*, nicht in den Sichtnamen: eine Materialized View je Zeitraum, jede über alle
   Metriken. Damit wächst die Zahl der Sichten nicht mit der Zahl der Metriken — was FR-032a
   („eine neue Metrik erscheint von allein") überhaupt erst tragbar macht.
3. **Die Zeitfortschreibung reitet auf dem Inventar-Sweep mit** (R6). Der läuft bereits im
   Autosave-Takt und über `inventoryModule.playersInPlay()` — genau die Liste und genau der Takt,
   den FR-015 verlangt. Es entsteht **keine** neue wiederkehrende Aufgabe, dasselbe Vorgehen, mit
   dem B11 `ConsumableBuffs.expire()` untergebracht hat.
4. **Der Schadensanteil existiert bereits** (R5). `DamageShare.shareOf` beantwortet die
   Beteiligungsschwelle aus FR-007 ohne eine zweite Rechnung darüber, wer wie viel beigetragen hat.

**Der eine Punkt, an dem dieser Plan ein Fundament erweitert**, ist der Maximum-Schreibweg
(ADR-040). `JdbcStatisticsRepository` bekommt neben dem addierenden ein zweites Statement mit
`GREATEST` — dieselbe Tabelle, derselbe Schlüssel, dieselbe Markierung, und weiterhin **kein Lesen
vor dem Schreiben**. Welches Statement gilt, entscheidet die Metrikart aus dem Verzeichnis, nicht
die Aufrufstelle.

## Technical Context

**Language/Version**: Java 25 (ADR-001)

**Primary Dependencies**: Paper 26.2 API (nur in `rpg-platform` und `rpg-plugin`), `rpg-core` ohne
Bukkit. Benutzt aus dem Bestand: `StatisticsRepository` (B02), `DamageShare` und
`CombatDeathEvent`/`DamageDealtEvent` (B05), `PartyRegistry` und `ShareCalculator`-Reichweite (B06),
`MobKind.key`/`MobKind.boss` (B10), `ZoneChangedEvent` (B09), `holderOf(characterId)` aus der
Statsschicht (B04, R3), `CharacterProgress` (B06) und `CharacterBalance` (B08b) für die
Zustandsranglisten, `Currency`/`BookingReason` (B08b) und der Item-Weg aus B11 für die
Saisonbelohnung.

**Storage**: PostgreSQL über B02. **Kein neuer Aggregattyp.** Vier Materialized Views (R1) und
**zwei neue Tabellen** für Saisonendstand und Belohnungsanspruch (R2), beide ohne Write-Behind —
sie entstehen viermal im Jahr, nicht tausendmal am Tag. Die Zähler selbst gehen unverändert durch
`StatisticsRepository.increment`, die Maxima durch dessen neuen Zwilling.

**Testing**: JUnit ohne Server für Metrikverzeichnis, Beteiligungsschwelle, Zeitrechnung samt
Tagesgrenze, Zeitraumberechnung, Punktformel der Gesamtwertung und Schemaprüfung; MockBukkit für
Zuhörer, Fenster und Hologramm; Testcontainers gegen echtes PostgreSQL für den Maximum-Schreibweg,
die vier Sichten und die beiden Saisontabellen (Prinzip VII). Eine wiederholbare Messung für den
Aktivitätszeitstempel im Bewegungspfad.

**Target Platform**: Paper-Server, eine Instanz (ADR-002)

**Project Type**: Gradle-Mehrmodulprojekt, `plugin → platform → core`

**Performance Goals**: Das Öffnen einer Ansicht erzeugt **null** Datenbankabfragen (SC-003) — die
Werte stehen im Speicher. Die Auffrischung sind vier Abfragen alle fünf Minuten, unabhängig davon,
wie viele Metriken es gibt. Der Aktivitätszeitstempel ist eine Zuweisung ohne Allokation im
fünften Handler eines Ereignisses, das bereits vier trägt (R7). Der verbindliche Zielwert des
Projekts (150 Spieler, p95 MSPT < 40 ms) bleibt und wird in B15 nachgewiesen (ADR-031).

**Constraints**: Paper-API nur im Tick; **keine** wiederkehrende Aufgabe je Spieler — die
Zeitfortschreibung reitet auf einem vorhandenen Sweep; kein Datenbankzugriff je Spielereignis; die
Art eines Verursachers wird im Tick aufgelöst, solange die Entität existiert (R4); kein `DELETE`
gegen `player_statistic_daily`, mechanisch gesichert durch `NoDirectDatabaseAccessTest`.

**Scale/Scope**: Fünf öffentliche Zähler- und Maximumsmetriken in vier Zeiträumen, zwei
Zustandsranglisten, eine Gesamtwertung; drei private Werte; zwei Dimensionen (Mob-Art, Zone); rund
ein bis vier Millionen Tageszeilen im Jahr, bewusst hingenommen (ADR-044).

**Keine offenen Punkte.** Vierzehn Fragen sind in drei Runden mit dem Auftraggeber geklärt
(ADR-040 bis ADR-047), zehn weitere sind beim Nachsehen im Code entstanden und in
[research.md](./research.md) beantwortet. Die beiden Punkte, die die Spec ausdrücklich hierher
überwiesen hat, sind R1 und R2.

## Constitution Check

*GATE: vor Phase 0 bestanden, nach Phase 1 erneut geprüft. Constitution 1.1.1.*

| Prinzip | Wie dieser Plan es einhält |
|---|---|
| **I · Nebenläufigkeit** | Erfassung ist ein Zählerinkrement im Speicher; geschrieben wird ausschließlich asynchron über B02s Write-Behind. Die Auffrischung der Sichten und das Füllen des Caches laufen async, die Übergabe in den Tick ist explizit. Die Auflösung der Mob-Art passiert **im Tick**, weil sie am Vermerk der Entität hängt (R4) — ADR-035 hat gezeigt, was asynchrone Entitätsauflösung kostet. Kein globaler veränderlicher Zustand: der Aktivitätszeitstempel hängt am Spieler, der offene Zeitabschnitt an seiner Sitzung. |
| **II · Performance** | **Keine neue wiederkehrende Aufgabe je Spieler.** Die Zeitfortschreibung reitet auf dem vorhandenen Inventar-Sweep (R6), die Untätigkeit ergibt sich aus einem Zeitstempel statt aus einem Ticker. Kein Datenbankzugriff je Spielereignis — tausend Kills kosten einen Schreibvorgang je Flush (SC-002). Das Öffnen einer Ansicht kostet null Abfragen (FR-030). Der fünfte Bewegungshandler tut weniger als jeder der vier vorhandenen und alloziert nichts (R7). |
| **III · Architektur** | `rpg-core` ohne Bukkit: Metrikverzeichnis, Zeiträume, Beteiligungsschwelle, Zeitrechnung, Ranglistenmodell, Punktformel, Saisonkalender. `rpg-persistence` hält Sichten, Migrationen und die beiden Saisontabellen — `java.sql` bleibt dort, erzwungen durch `NoDirectDatabaseAccessTest`. `rpg-platform` trägt Zuhörer, Fenster, Commands und Hologramm. B12 liest fremde Blöcke über deren öffentliche Nähte und fasst keinen von ihnen an. |
| **IV · Datenhaltung** | Zwei neue Tabellen, beide über versionierte Migrationen, beide mit `data_version`. Die vier Sichten sind abgeleitet und tragen keine eigene Wahrheit. **Zustandswerte werden gelesen, nicht gespiegelt** (ADR-041) — keine zweite Fassung von Level oder Coins. Rohdaten werden nie gelöscht und nie verdichtet (ADR-044), mechanisch gesichert. Der eingefrorene Saisonendstand ist die einzige Kopie einer abgeleiteten Zahl, und er ist es mit Absicht: eine vergebene Platzierung darf sich nicht mehr ändern. |
| **V · Datengetriebenes Design** | Metrikverzeichnis, Zeiträume, Saisonkalender, Gewichte, Belohnungen, Schwellen und Hologrammstelle in `statistics.yml`, beim Start gegen ein Schema geprüft, Fail-Fast mit Datei/Schlüssel/Grund. **Drei Prüfungen sind Regeln, keine Zahlenwahl**: Saisons lückenlos und überschneidungsfrei (FR-049), Gewichtung nennt mindestens eine bekannte Metrik (FR-050d), Beteiligungsschwelle echt zwischen null und eins (FR-007d). Alle Texte über Message-Schlüssel, geprüft nach dem Muster von `ItemMessageKeys` (R9). |
| **VI · Korrektheit & Sicherheit** | Der Server ist Autorität; die Ansicht zeigt nur, was der Cache hält. **Ein Fehler in der Erfassung darf das Spielereignis nicht scheitern lassen** (FR-004, SC-011) — der Zählpfad ist lokal gefangen. Die drei privaten Werte sind über **alle vier** Ausgabewege gesperrt (FR-037, SC-017), nicht nur im Fremdprofil. Ein anonymisiertes Konto erscheint in keiner Rangliste (FR-039). Der Belohnungsanspruch wird **zuerst markiert, dann gutgeschrieben** (R2), damit ein Absturz höchstens verliert und niemals verdoppelt. Kein Reflection, kein NMS. |
| **VII · Tests** | Jede Regel serverlos: Schwelle, Zeitrechnung, Tagesgrenze, Zeiträume, Punktformel, Schemaprüfung. Persistenz gegen echtes PostgreSQL über Testcontainers, **einschließlich des Maximum-Schreibwegs und der vier Sichten**. Der Lasttest bleibt B15 (ADR-031) und hält diesen Block nicht offen; die Messung ohne Volllast erfüllt Prinzip VII. |
| **VIII · Sprache** | Dokumentation deutsch, Code und Config-Schlüssel englisch, Spielertexte über Message-Schlüssel. |

### Kein Eingriff in einen fremden Block

Anders als B11 fasst dieser Plan **keinen** fertigen Block an. Der Maximum-Schreibweg erweitert
`JdbcStatisticsRepository` additiv: das vorhandene `increment` und sein Verhalten bleiben
unverändert, ein Aufrufer merkt nichts. Siehe [Complexity Tracking](#complexity-tracking) — dort
steht, warum das trotzdem benannt gehört.

## Project Structure

### Documentation (this feature)

```text
specs/012-stats-leaderboards/
├── plan.md              # Diese Datei
├── research.md          # Phase 0 — zehn Fragen an den gebauten Code
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── stats-api.md     # Was B12 nach außen anbietet
│   └── stats-config.md  # Das Schema von statistics.yml
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks — nicht von /speckit-plan erzeugt
```

### Source Code (repository root)

```text
rpg-core/src/main/java/rpg/core/stats/         # neu — bukkit-frei
├── Metric.java                                # Schlüssel, Art, Sichtbarkeit, Dimension
├── MetricKind.java                            # SUM, MAX, STATE
├── MetricVisibility.java                      # PUBLIC, PRIVATE
├── MetricRegistry.java                        # das Verzeichnis, kein Literal im Code (FR-018)
├── MetricKeys.java                            # Bildung dimensionierter Schlüssel
├── StatsConfig.java / StatsConfigSchema.java   # Fail-Fast beim Start (R9)
├── StatsModule.java                           # Start und Nachladen, wie MobModule
├── KillCredit.java                            # die Beteiligungsschwelle (FR-007, R5)
├── Playtime.java                              # zwei Uhren, Abschnitte, Tagesgrenze
├── ActivityClock.java                         # Untätigkeit aus einem Zeitstempel
├── Period.java                                # DAY, WEEK, SEASON, ALL_TIME (UTC, ISO-Woche)
├── SeasonCalendar.java                        # lückenlos, überschneidungsfrei (FR-049)
├── Leaderboard.java / LeaderboardEntry.java   # Rang, Konto, Name, Wert
├── LeaderboardCache.java                      # was die Ansichten lesen (FR-030)
├── SeasonScore.java                           # gewichtete Punktzahl (ADR-046)
├── ScoreWeights.java                          # Gewichtung, einfrierbar
├── SeasonResult.java                          # eingefrorener Endstand
├── RewardClaim.java                           # Anspruch, genau einmal einlösbar
└── StatsMessageKeys.java

rpg-persistence/src/main/java/rpg/persistence/stats/   # neu
├── StatisticsMaxWriter.java                   # der zweite Schreibweg (ADR-040)
├── JdbcLeaderboardSource.java                 # vier Sichten → Cache, eine Abfrage je Sicht
├── JdbcStateLeaderboardSource.java            # Level und Coins gelesen, nicht gespiegelt (ADR-041)
├── LeaderboardRefresh.java                    # REFRESH ... CONCURRENTLY, asynchron
├── JdbcSeasonResultRepository.java            # ohne Write-Behind (R2)
├── JdbcRewardClaimRepository.java             # erst markieren, dann gutschreiben
└── StatsPersistenceModule.java

rpg-persistence/src/main/resources/db/migration/
├── V12_1__statistic_leaderboard_views.sql     # vier Materialized Views + Unique-Indizes
├── V12_2__season_result.sql
└── V12_3__season_reward_claim.sql

rpg-platform/src/main/java/rpg/platform/stats/  # neu
├── KillStatListener.java                      # CombatDeathEvent → Kills, Bosskills, Tode
├── DamageStatListener.java                    # DamageDealtEvent → höchster Schaden (FR-016a)
├── ActivityListener.java                      # der fünfte Bewegungshandler (R7)
├── PlaytimeAccrual.java                       # reitet auf dem Inventar-Sweep (R6)
├── ZoneTimeListener.java                      # ZoneChangedEvent → Abschnitt wechseln
├── StatsMenu.java                             # eigenes Profil, vier Zeiträume
├── ProfileMenu.java                           # fremdes Profil, ohne die drei privaten Werte
├── LeaderboardMenu.java                       # Metrik und Zeitraum umschaltbar
├── StatsMenuListener.java                     # ein Zuhörer für alle drei Fenster
├── SeasonRewardClaimListener.java             # Einlösen beim nächsten Spielen
└── LeaderboardHologram.java                   # Muster VendorNpc (R8)

rpg-plugin/src/main/java/rpg/plugin/command/
├── StatsCommand.java                          # /stats [spieler]
└── TopCommand.java                            # /top

rpg-plugin/src/main/resources/statistics.yml    # neu
```

**Structure Decision**: Dieselbe Dreiteilung wie in jedem Block seit B04, plus ein eigenes Paket in
`rpg-persistence`. Das ist neu für einen Block der dritten Schicht und folgt aus
`NoDirectDatabaseAccessTest`: Sichten, Refresh und die beiden Saisontabellen sind SQL, und SQL lebt
in diesem Projekt ausschließlich in `rpg-persistence`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| **B02 wird erweitert**: `JdbcStatisticsRepository` bekommt ein zweites Statement (`GREATEST` statt `+`) und `StatisticsRepository` eine zweite Methode | FR-016 verlangt den höchsten Schaden. Ein Maximum ist kein Summand und lässt sich im addierenden Statement nicht ausdrücken (ADR-040) | Vor dem Schreiben lesen verletzt B02s FR-007 und macht aus jedem Schadensereignis eine Abfrage. Eine eigene Tabelle für Maxima wäre eine zweite Haltung derselben Sache — mit eigenem Index, eigener Aufbewahrungsregel und einer zweiten Stelle, die bei der Anonymisierung umgezeigt werden muss. Die Erweiterung ist **additiv**: bestehende Metriken ändern ihr Verhalten nicht |
| **Zwei Tabellen ohne Write-Behind**, entgegen dem sonstigen Muster jedes Aggregats seit B02 | Ein Saisonendstand entsteht viermal im Jahr. Der Write-Behind-Weg bündelt viele Änderungen je Sekunde — ein Problem, das hier nicht besteht (R2) | Ein Aggregattyp bedeutete drei Registrierungen (ADR-015), eine Revisionsspalte und einen `BatchWriter` für einen Vorgang pro Quartal. Versionierte Migration, `data_version` und die Kapselung in `rpg-persistence` bleiben unverändert erfüllt — verzichtet wird nur auf die Bündelung |
| **Ein fünfter Handler auf `PlayerMoveEvent`**, dem nachweislich teuersten Ereignis des Servers | FR-014c1 nennt Bewegung ausdrücklich als Aktivität. Ohne sie zählte Erkunden als Leerlauf | Ein Rückruf in `ZoneMovementListener` hätte B09 an B12 gekoppelt und denselben Aufruf gekostet. Der neue Handler tut **weniger als jeder der vier vorhandenen**: eine Zuweisung, keine Allokation, keine Bedingung, `MONITOR`-Priorität. `FullBootstrapTest` zählt diese Handler und muss mitgezogen werden (R7) |
| **Zweiundzwanzig Ranglisten** aus einer Anforderung, die keine Auswahl zulässt (FR-032a) | Eine kuratierte Liste wäre eine Pflegeaufgabe, die niemand pflegt — dieselbe Begründung wie ADR-044 | Der Aufwand skaliert nicht mit der Zahl der Listen: vier Sichten, vier Abfragen je Auffrischung, unabhängig davon, wie viele Metriken es gibt (R1). Die Zahl ist eine Frage der Anzeige, nicht der Last |

**Alle vier Punkte sind additiv.** Kein bestehendes Verhalten ändert sich, keine vorhandene
Zusicherung wird umgedreht. Die einzige Teständerung außerhalb dieses Blocks ist die Handlerzahl in
`FullBootstrapTest` — und die ist genau der Zweck jenes Tests.
