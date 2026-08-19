# Implementation Plan: B04 · Attribut- & Stat-Engine

**Branch**: `004-stat-engine` | **Date**: 2026-08-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-stat-engine/spec.md`

## Summary

B04 ist das erste Stück Regelwerk über dem Fundament. Es liefert acht Attribute als **eine**
Datenstruktur, ein Beitragsmodell mit Quellenverfolgung, eine einzige Berechnungsvorschrift und
einen unveränderlichen Schnappschuss als Ergebnis.

Drei Entscheidungen prägen die Umsetzung:

1. **Der Attributsatz ist ein geschlossener Aufzählungstyp**, und jeder Träger hält seine Werte in
   einem `double[8]`, indiziert über die Position im Aufzählungstyp. Das ist der Grund, warum acht
   Attribute × 200 Spieler im Leerlauf messbar nichts kosten: keine Map-Abfrage, kein Boxing, keine
   Allokation im heißen Pfad (Constitution II).

2. **Neu berechnet wird immer vollständig aus den verbliebenen Quellen**, nie durch Rückrechnen
   eines entfernten Beitrags. Das ist der einzige Weg, den driftfreien Rundlauf aus SC-004
   strukturell zu garantieren statt ihn zu testen und zu hoffen. Bei ~20 Quellen und acht
   Attributen sind das rund 160 Gleitkomma-Operationen — billiger als die Buchführung, die
   inkrementelles Rechnen bräuchte.

3. **Die Bündelung läuft über eine Vormerkung plus genau eine entitätsgebundene Aufgabe**, nicht
   über einen serverweiten Durchlauf am Tick-Ende. Die erste Änderung an einem Träger plant eine
   Aufgabe auf genau diesem Träger ein; jede weitere Änderung davor findet die Vormerkung bereits
   gesetzt und plant nichts. Ergebnis: N Änderungen in einem Tick ergeben eine Neuberechnung, ohne
   dass irgendwo über alle Träger iteriert wird — und ohne globalen Task, der den Folia-Pfad aus
   ADR-007 verbauen würde. Für den Ladepfad gibt es zusätzlich einen sofortigen, synchronen Weg,
   damit FR-019b erfüllt ist: kein Spieler wird mit ausstehender Vormerkung freigegeben.

Persistiert werden ausschließlich die beiden Ressourcenstände (aktuelles Leben, aktuelles Mana) in
einer eigenen Tabelle über die eigene Migration `V4_1`. Alle übrigen Werte sind abgeleitet und
werden nie gespeichert — sie entstehen beim Laden neu aus Konfiguration und Quellen.

## Technical Context

**Language/Version**: Java 25 (ADR-001), Toolchain aus B01 unverändert.

**Primary Dependencies**: Keine neuen. B04 nutzt ausschließlich Vorhandenes — Paper-API
(compileOnly) in `rpg-platform`, PostgreSQL/HikariCP/Flyway über Papers `libraries:` (ADR-010) in
`rpg-persistence`, Lombok und MapStruct als reine Compile-Zeit-Werkzeuge.

**Storage**: PostgreSQL 18. Eine neue Tabelle `rpg.character_stats` über
`V4_1__character_stats.sql`. `rpg.character` aus B03 bleibt unverändert.

**Testing**: JUnit 5 (Jupiter 6.1.3) + AssertJ für die gesamte Rechenlogik in `rpg-core`, komplett
serverfrei. MockBukkit für die Vanilla-Brücke und den Regenerationsschutz in `rpg-platform`.
Testcontainers 1.21.4 gegen `postgres:18-alpine` (Singleton-Container aus B02) für Migration und
Rundlauf der Ressourcenstände. Der Bootstrap-Nachweis läuft über den bestehenden
`FullBootstrapTest` in `rpg-plugin` (ADR-012).

**Target Platform**: Linux-VPS, Paper-Server (Minecraft 26.2 / Java 25), PostgreSQL auf derselben
Maschine.

**Project Type**: Regel-Engine-Block innerhalb des Multi-Modul-Gradle-Projekts aus B01.

**Performance Goals**: 200 Träger mit je 20 Beiträgen im Leerlauf: null Neuberechnungen, unter
0,1 ms je Tick (SC-002). 100 gleichzeitige Neuberechnungen in einem Tick unter 5 ms (SC-003) — bei
rund 160 Gleitkomma-Operationen je Neuberechnung liegt das rechnerisch drei Größenordnungen unter
dem Budget; gemessen wird trotzdem. Eine einzelne Neuberechnung allokiert genau ein Objekt: den
neuen Schnappschuss.

**Constraints**: Kein Bukkit-Aufruf außerhalb des Ticks des betroffenen Trägers (Constitution I).
Keine wiederkehrende Aufgabe je Träger (Constitution II, FR-018). Keine sequenzielle
Prozentverkettung (ADR-008). Kein eigener Datenbankzugriff je Spielereignis (FR-028, SC-012).
`java.sql` bleibt auf `rpg-persistence` beschränkt — durchgesetzt durch B02s
`NoDirectDatabaseAccessTest`. Kein Bukkit-Import in `rpg-core` — durchgesetzt durch die
Gradle-Modulgrenze.

**Scale/Scope**: 100–200 gleichzeitige Spieler (ADR-002) plus bis zu 800 aktive Mobs (M4-Ziel).
Konsumenten sind B05, B06, B07, B08, B10, B11 und B13.

### Abgrenzung — was B04 ausdrücklich nicht tut

| Gehört zu | Was B04 stattdessen liefert |
|---|---|
| B05 Kampf | Schadensanwendung, Trefferbehandlung, Umlenken von Vanilla-Schadensquellen. B04 liefert die reine Minderungsfunktion und den Ressourcenbehälter. |
| B06 Progression | XP, Levelkurve, Stat-Zuwachs je Level. B04 liefert die Schnittstelle, über die ein Levelzuwachs als Basiswertbeitrag ankommt. |
| B07 Klassen | Klassenbasiswerte je Attribut. B04 liefert dieselbe Schnittstelle. |
| B08 Fähigkeiten | Mana-Regeneration, Cooldown-Verwaltung, Buff-Laufzeiten. B04 liefert `abilityCooldown` als Wert und Mana als Behälter. |
| B10 Mobs | Mob-Definitionen und Spawning. B04 liefert den trägerneutralen Stat-Träger. |
| B11 Items | Itemdefinitionen, Rolls, Ausrüstungsslots. B04 liefert die Beitragsschnittstelle mit Quellen-ID. |
| B13 UI | HUD, Anzeigeflächen, Texte. B04 liefert das Neuberechnungs-Ereignis und die Vanilla-Spiegelung. |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Prinzip | Prüfung | Status |
|---|---|---|
| I. Nebenläufigkeit | Die gesamte Rechenlogik ist reines Java ohne Bukkit und ohne I/O; sie ist thread-neutral, weil ein Träger nur über seinen eigenen Zustand rechnet. Jeder Bukkit-Zugriff (Vanilla-Attribute, angezeigte Gesundheit) läuft über `Scheduler.runSyncOnEntity` im Tick des betroffenen Trägers. Kein globaler Scheduler, kein `join()`/`get()` im Tick. Das Laden der Ressourcenstände hängt am asynchronen Vorlade-Pfad aus B03, nicht an einem eigenen Zugriff. Zustand hängt am Träger, nicht an globalem veränderlichem Zustand. | PASS |
| II. Performance | Keine wiederkehrende Aufgabe je Träger und kein serverweiter Durchlauf: die Bündelung entsteht aus einer Vormerkung je Träger plus einer entitätsgebundenen Einmalaufgabe. Werte liegen in einem `double[8]`, indiziert über den Aufzählungstyp — keine Map, kein Boxing, keine Streams im Rechenpfad. Eine Neuberechnung allokiert genau einen Schnappschuss. Kein Datenbankzugriff je Spielereignis; Ressourcenstände laufen über das Write-Behind aus B02. | PASS |
| III. Architektur | Attributmodell, Beiträge, Berechnung, Schnappschuss, Ressourcenbehälter und Minderungsfunktion liegen vollständig in `rpg-core` und sind ohne Server testbar (FR-034). Die Vanilla-Spiegelung liegt hinter der Schnittstelle `VanillaAttributeBridge`, implementiert in `rpg-platform`. Datenzugriff nur in `rpg-persistence`. Richtung `plugin → platform → persistence → core` bleibt durch den Gradle-Graphen erzwungen. Andere Blöcke greifen über `StatEngine` zu, nie auf Interna. | PASS |
| IV. Datenhaltung | Schemaänderung ausschließlich über die versionierte Migration `V4_1__character_stats.sql`. Solange ein Spieler online ist, ist der Träger im Speicher maßgeblich; geschrieben wird über B02s Write-Behind. Es werden nur die beiden Rohwerte gespeichert, niemals berechnete Endwerte — dieselbe Regel, die ADR-004 für Items zieht, aus demselben Grund: sonst wäre jedes Rebalancing ein Datenmigrationsproblem. Kein Datenverlust über das Autosave-Intervall hinaus. | PASS |
| V. Datengetriebenes Design | Basiswerte, Unter- und Obergrenzen sowie alle Caps stehen in `stats.yml` und werden beim Start gegen ein Schema geprüft; ein Fehler bricht den Start ab (FR-003, FR-014a). Balancing erfordert keine Codeänderung. Meldungen laufen über Message-Keys. | PASS |
| VI. Korrektheit & Sicherheit | Der Server ist alleinige Autorität; kein Wert stammt aus einer Client-Eingabe. Eine Ausnahme in einem Beitragslieferanten wird je Träger begrenzt und protokolliert (FR-038) — dasselbe Muster wie B01s `ModuleFaultBarrier`. Kein Reflection- und kein NMS-Zugriff: `GENERIC_MAX_HEALTH`, `GENERIC_ATTACK_SPEED` und `GENERIC_MOVEMENT_SPEED` sind öffentliche Paper-API. | PASS |
| VII. Tests | Jede Formel und jede Grenze wird serverfrei unit-getestet, einschließlich der in SC-005 genannten Randfälle. Die Persistenz der Ressourcenstände läuft gegen echtes PostgreSQL über Testcontainers, nicht gegen Mocks. B04 ist kein Lasttest-Pflichtblock nach Prinzip VII (das sind B05 und B10), liefert aber die Messung zu SC-002 und SC-003 als Nachweis mit. | PASS |
| VIII. Sprache | Diese Planung und alle Spec-Artefakte auf Deutsch; Paketnamen, Typen, Felder, Config-Keys und Message-Keys auf Englisch. | PASS |

**Ergebnis: 8/8 PASS, keine Ausnahme nötig.** Zwei Entwurfsentscheidungen berühren fremde Blöcke
und sind unter *Complexity Tracking* begründet.

### Nachprüfung nach Phase 1

Nach Ausarbeitung von Datenmodell, Verträgen und Validierungsleitfaden erneut geprüft: unverändert
8/8 PASS. Zwei Punkte wurden im Entwurf nachgeschärft, weil sie sonst gegen Prinzip I bzw. II
gelaufen wären:

- Der ursprünglich naheliegende serverweite Durchlauf am Tick-Ende wurde durch die
  entitätsgebundene Einmalaufgabe ersetzt (Prinzip I: kein globaler Scheduler; Prinzip II: keine
  Iteration über alle Träger).
- Der Schnappschuss gibt sein internes Array nicht heraus, sondern nur Werte über
  `get(Attribute)`. Andernfalls wäre die in FR-020 zugesicherte Unveränderlichkeit eine Zusage ohne
  Durchsetzung.

## Project Structure

### Documentation (this feature)

```text
specs/004-stat-engine/
├── plan.md              # Diese Datei
├── research.md          # Phase 0 — die sieben Entwurfsentscheidungen mit Alternativen
├── data-model.md        # Phase 1 — Typen, Felder, Regeln, Tabelle, Zustandsübergänge
├── quickstart.md        # Phase 1 — Validierungsleitfaden
├── contracts/           # Phase 1 — öffentliche Schnittstellen des Blocks
│   ├── stat-engine.md
│   ├── stat-config.md
│   └── events.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 — erzeugt von /speckit-tasks, nicht von /speckit-plan
```

### Source Code (repository root)

```text
rpg-core/src/main/java/rpg/core/stats/
├── Attribute.java                  # geschlossener Satz der acht Attribute (FR-001, FR-004)
├── AttributeKind.java              # ABSOLUTE | PERCENT
├── AttributeDefinition.java        # Basis, Unter-/Obergrenze, Band (FR-002)
├── StatConfig.java                 # validierte stats.yml samt Schema (FR-003, FR-014a)
├── ModifierOperation.java          # FLAT | PERCENT (FR-005)
├── SourceKind.java                 # CLASS, LEVEL, EQUIPMENT, BUFF, AURA, ZONE (FR-006)
├── SourceId.java                   # Quellenart + Schlüssel, gleichheitsfähig (FR-007)
├── StatModifier.java               # ein Beitrag (FR-005)
├── ModifierSet.java                # alle Beiträge einer Quelle, unveränderlich
├── StatSnapshot.java               # unveränderliches Ergebnis (FR-020, FR-021)
├── StatHolder.java                 # Träger: Quellen, Schnappschuss, Ressourcen (FR-035)
├── ResourcePool.java               # Leben/Mana mit Klemmregeln (FR-025 bis FR-027)
├── StatEngine.java                 # öffentliche Schnittstelle des Blocks
├── DefaultStatEngine.java          # Vormerkung, Bündelung, Neuberechnung (FR-018, FR-019)
├── StatCalculator.java             # die reine Formel (FR-011 bis FR-017)
├── DamageMitigation.java           # Divisor-Modell für B05 (FR-015)
├── BaseStatContributor.java        # Schnittstelle für B06/B07 (FR-039)
├── VanillaAttributeBridge.java     # Schnittstelle für die Spiegelung (FR-034)
├── StatsRecalculatedEvent.java     # Ereignis für B13 und andere (FR-023)
├── ResourceChangedEvent.java       # Ereignis für Ressourcenänderungen (FR-029)
├── CharacterResources.java         # persistierbarer Rohstand (FR-028)
├── CharacterResourcesRepository.java
├── UnknownAttributeException.java  # (FR-004a, FR-009)
└── StatsMessageKeys.java

rpg-platform/src/main/java/rpg/platform/stats/
├── PaperVanillaAttributeBridge.java  # GENERIC_MAX_HEALTH=20, Anzeige, Speed (FR-030 bis FR-033)
└── VanillaRegenerationGuard.java     # Regeneration aus, Sättigung fest (FR-030a)

rpg-persistence/src/main/java/rpg/persistence/stats/
├── StatsModule.java                        # Modulverdrahtung nach B01-Vertrag
└── JdbcCharacterResourcesRepository.java   # Write-Behind-Anbindung (FR-028)

rpg-persistence/src/main/resources/db/migration/
└── V4_1__character_stats.sql

rpg-plugin/src/main/resources/
└── stats.yml                        # Auslieferungswerte aus dem Blocksteckbrief

rpg-core/src/test/java/rpg/core/stats/          # der Großteil der Tests, serverfrei
rpg-platform/src/test/java/rpg/platform/stats/  # Brücke und Regenerationsschutz, MockBukkit
rpg-persistence/src/test/java/rpg/persistence/stats/  # Migration und Rundlauf, Testcontainers
```

**Structure Decision**: B04 folgt exakt dem Zuschnitt, den B02 und B03 etabliert haben — Regeln in
`rpg-core`, Paper-Anbindung in `rpg-platform`, Datenzugriff und Modulverdrahtung in
`rpg-persistence`, Zusammenbau in `rpg-plugin`. Das Modul liegt in `rpg-persistence`, weil es eine
Repository-Instanz aufbauen muss und `rpg-platform` nicht von `rpg-persistence` abhängen darf; die
Paper-Brücke wird von außen hineingereicht, genau wie B03 es mit seinen Listenern hält.

## Complexity Tracking

> Zwei Entwurfsentscheidungen greifen über die Blockgrenze hinaus. Beide sind bewusst und begrenzt.

| Abweichung | Warum nötig | Verworfene einfachere Alternative |
|---|---|---|
| `SessionBundle` und `SessionBundleLoader` aus B03 werden um einen vierten Lesevorgang für die Ressourcenstände erweitert | FR-019b verlangt einen berechneten Träger *vor* der Freigabe des Spielers. Der einzige Ladepfad, der vor der Freigabe liegt und bereits eine Verbindung und eine Transaktion hält, ist B03s Bündelladen. | Ein eigener Ladevorgang in B04, angestoßen nach dem Bereitwerden der Sitzung. Verworfen, weil der Spieler dann für eine Runde mit vollem oder leerem Leben in der Welt stünde — genau der Zustand, den B03 mit dem Vorladen strukturell ausgeschlossen hat. Die Erweiterung ist zudem kein Sonderfall: `SessionBundle` trägt bereits `ItemInstance`, also Daten von B11. |
| Der Regenerationsschutz (`naturalRegeneration` aus, Sättigung fest) liegt in B04 statt in B05 | Geklärt in `/clarify` Frage 2. Ohne ihn heilt Vanilla die Anzeige, die B04 gerade gesetzt hat, sichtbar wieder hoch — die Herzleiste aus FR-030 wäre ab dem ersten Tag falsch, und zwar bis B05 fertig ist. | Alles B05 überlassen. Verworfen, weil B04 damit ein sichtbar defektes Feature ausliefern würde. Die Grenze bleibt eng gezogen: B04 fasst **keine** Schadensereignisse an (FR-030b). |

## Phasen

### Phase 0 — Recherche

Abgeschlossen. Sieben Entwurfsentscheidungen mit Alternativen und Begründung in
[research.md](./research.md). Keine offenen `NEEDS CLARIFICATION` mehr: die fünf Punkte aus
`/clarify` sind in der Spec verankert, die verbleibenden waren Umsetzungsfragen und sind in Phase 0
entschieden.

### Phase 1 — Entwurf & Verträge

Abgeschlossen:

- [data-model.md](./data-model.md) — Typen, Felder, Validierungsregeln, Tabelle `rpg.character_stats`,
  Zustandsübergänge des Trägers, Speicherabschätzung.
- [contracts/stat-engine.md](./contracts/stat-engine.md) — die öffentliche Schnittstelle, gegen die
  B05 bis B13 entwickeln.
- [contracts/stat-config.md](./contracts/stat-config.md) — Aufbau und Schema von `stats.yml` samt
  Auslieferungswerten.
- [contracts/events.md](./contracts/events.md) — die beiden veröffentlichten Ereignisse.
- [quickstart.md](./quickstart.md) — acht Validierungsabschnitte, die jedes Erfolgskriterium
  nachweisen.

### Phase 2 — Aufgaben

Nicht Teil dieses Befehls. Erzeugt durch `/speckit-tasks`.
