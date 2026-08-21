# Implementation Plan: B07 · Klassen-System

**Branch**: `007-class-system` | **Date**: 2026-08-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-class-system/spec.md`

## Summary

B07 ist der erste Block, der **die dominante Stat-Quelle** stellt (ADR-017) und gleichzeitig
**Vanilla-Verhalten unterdrücken** muss. Sechs Entscheidungen prägen die Umsetzung:

1. **Klassenbasis und Stufenwerte laufen über `BaseStatContributor`, nicht über
   `SourceKind.CLASS`-Modifikatoren.** Das ist keine Stilfrage, sondern rechnerisch zwingend, und es
   ist derselbe Grund, den B06 für das Levelwachstum notiert hat (Plan B06, Punkt 1): das
   Modifikatorband wird um den **effektiven** Basiswert gelegt. Kämen die Stufenwerte als
   FLAT-Modifikatoren, blieb das Band am Level-1-Wert hängen. Bei B06 betraf das ein Drittel der
   Endpower — bei B07 sind es nach ADR-017 rund **70 %**, und das Band wäre auf der Endstufe grob
   falsch. `SourceKind.CLASS` bleibt damit von B07 **unbenutzt** und für spätere, tatsächlich
   modifikatorförmige Klasseneffekte frei. Siehe [research.md](./research.md) R1 — dort steht auch
   die daraus folgende Präzisierung von FR-009.

2. **Die Neutralisierung der Vanilla-Modifikatoren ist der eigentliche technische Kern.** Ein leerer,
   **nicht-null** `Multimap` in `ItemMeta.setAttributeModifiers` schreibt eine ausdrücklich leere
   Modifikatorliste und ersetzt damit die Vorgaben des Materials. Zwei Fallen, beide in research.md
   R2 dokumentiert: `null` stellt die Vorgaben wieder **her**, und `ItemFlag.HIDE_ATTRIBUTES`
   versteckt nur die Anzeige, ohne die Wirkung zu nehmen. Ohne diesen Griff wäre der Waffentyp eine
   neunte Wertquelle (FR-046).

3. **Die Stufe ist Charakterstand, das Item ist Ableitung.** Eine neue Tabelle
   `rpg.character_class_progress` mit zwei Nutzspalten (Rüstungsstufe, Waffenstufe), geschrieben über
   den Write-Behind-Puffer aus B02. Das Item wird beim Laden **aus** der Stufe erzeugt, nie umgekehrt
   — damit heilt der Zustand „Stufe erreicht, Item fehlt" sich selbst (FR-023), und es gibt keinen
   Weg, durch Manipulation eines Items eine Stufe zu gewinnen.

4. **Ein neuer Aggregattyp heißt drei Registrierungen, nicht eine.** ADR-015 hat das nach einem
   Fehler in B06 festgehalten: `AggregateType`, `FlushCycle.WRITE_ORDER` (Kind **nach** Elternteil,
   also nach `CHARACTER`) und die Repository-Verdrahtung. `NoDatabaseAccessPerGameEventTest` prüft die
   Vollständigkeit als Invariante und würde ein Vergessen sofort zeigen.

5. **Die Leiterlänge ist ein Listenfeld, kein Feld je Stufe.** Wie B06 seine XP-Kurve als
   Kartenfeld gebunden hat, statt 59 Pflichtschlüssel zu deklarieren: die Leitern sind Listen, deren
   Bindefunktion Länge, strenge Monotonie, Unterscheidbarkeit des Erscheinungsbilds und Färbbarkeit
   prüft und beim ersten Verstoß mit benannter Ursache abbricht. Eine feste Zahl im Schema könnte
   Warrior 5/6, Rogue 6/6 und Mage 7/7 nicht ausdrücken.

6. **„Kein Spielzustand vor der Wahl" wird durch Abwesenheit erreicht, nicht durch Abfragen.**
   Ein Stat-Halter entsteht über `DefaultStatEngine.createForCharacter`, also **je Charakter**. Ein
   Spieler ohne Charakter hat deshalb keinen Halter, keinen Snapshot und keinen Schadenspfad — die
   Kampf-Pipeline lehnt ihn mit `NO_HOLDER` ab, genauso wie ein gewöhnliches Tier. **B04 und B05
   werden nicht angefasst**, und zwar nicht aus Zurückhaltung, sondern weil es nichts zu ändern gibt.
   B07 ergänzt nur die Bewegungssperre und die nicht schließbare Auswahl.
   *(In T055 nachgewiesen; die Annahme in research.md R4 lautete zunächst `SESSION_NOT_READY` und war
   die schwächere Vermutung — sie hätte am Sitzungszustand gehangen, der nach dem Laden wieder
   `READY` ist.)*

Was wie ein Widerspruch zu ADR-004 aussieht und keiner ist: Items speichern weiterhin Template-ID und
Rolls (Prinzip IV). Die Klassenausrüstung ist kein Gegenbeispiel, weil sie **kein Item mit Werten**
ist — sie ist eine Darstellung des Charakterstands. Ihre Werte stehen in der Klassenconfig, nicht im
Gegenstand, und genau deshalb ist Rebalancing ohne Anfassen von Spielerinventaren möglich.

## Technical Context

**Language/Version**: Java 25 (ADR-001), Toolchain aus B01 unverändert.

**Primary Dependencies**: Keine neuen. B07 nutzt `StatEngine` und `BaseStatContributor` aus B04, den
Write-Behind-Puffer und `AggregateType` aus B02, `PlayerSession` und `CharacterRepository` aus B03,
`Progression` aus B06 für das Level. Guava `Multimap` kommt über die Paper-API, die weiterhin
`compileOnly` und nur in `rpg-platform` liegt.

**Storage**: PostgreSQL, **eine neue Tabelle** `rpg.character_class_progress` (Migration `V7_1`) mit
zwei Nutzspalten. Die Klassenspalte selbst und die Prüfbedingung auf die drei Klassen bleiben in
`V3_1__player_characters.sql` unangetastet (ADR-019) — B07 legt **keine** Migration an, die das Enum
oder die Constraint berührt.

**Testing**: JUnit 5 + AssertJ serverfrei in `rpg-core` für Leiternormierung, Wertekurven,
Schemavalidierung und das Bindungsprädikat. Testcontainers mit echtem PostgreSQL für Repository und
Migration (Prinzip VII verbietet Mocks gegen die Datenbank). MockBukkit in `rpg-platform` für
Inventarsperre, Auswahl-GUI, Itemaufbau und die Neutralisierung der Attributmodifikatoren.
`FullBootstrapTest` in `rpg-plugin` beweist die Verdrahtung (ADR-012).

**Target Platform**: Linux-VPS, Paper-Server (Minecraft 26.2 / Java 25), API-Artefakt
`26.2.build.112-stable`.

**Project Type**: Regel-Engine-Block mit Persistenz- und Plattformanteil, innerhalb des
Multi-Modul-Gradle-Projekts aus B01.

**Performance Goals**: Genau **eine** Stat-Neuberechnung je Level- und je Stufenaufstieg (SC-009).
Weder Klassenwerte noch Bindungsprädikat lösen einen Datenbankzugriff je Spielereignis aus, geprüft
bei 10 000 Abfragen (SC-010). Das Bindungsprädikat liegt im Pfad **jedes** Inventarklicks und muss
allokationsfrei antworten.

**Constraints**: Klassendefinitionen liegen genau **einmal** im Speicher, nicht je Spieler — drei
unveränderliche Objekte für den ganzen Server. Die Auswahl-GUI und die Sperre laufen im Tick
(Prinzip I); das Nachladen der Auswahl nach einem Schließversuch geht über den entity-gebundenen
Scheduler, **nie** über den globalen Bukkit-Scheduler.

**Scale/Scope**: Drei Klassen, sechs Leitern mit 5 bis 7 Stufen, acht Attribute, sechs
Fähigkeitsbindungen je Klasse. 52 funktionale Anforderungen, 14 Erfolgskriterien.

## Constitution Check

*GATE: Muss vor Phase 0 bestehen. Nach Phase 1 erneut geprüft.*

| Prinzip | Bewertung | Begründung |
|---|---|---|
| **I. Nebenläufigkeit** | ✅ | Klassenconfig wird beim Start geladen, danach nur gelesen — unveränderlich, kein geteilter veränderlicher Zustand. Das Wiederöffnen der Auswahl nach einem Schließversuch läuft über den **entity-gebundenen** Scheduler aus B01, nie über den globalen. Repository-Zugriffe asynchron über den Puffer aus B02. |
| **II. Performance** | ✅ | Keine wiederkehrende Aufgabe je Spieler: die Auswahl reagiert auf Ereignisse, die Sperre ebenso. Keine Berechnung pro Tick — Neuberechnung nur bei Level- oder Stufenaufstieg. Kein Datenbankzugriff je Spielereignis. Das Bindungsprädikat prüft einen vorab aufgelösten Schlüssel im PersistentDataContainer, ohne Allokation und ohne Streams. |
| **III. Architektur** | ✅ | Regeln, Leiternormierung, Schemavalidierung und Bindungsdefinition in `rpg-core` ohne eine Bukkit-Referenz. Listener, GUI und Itemaufbau in `rpg-platform`. Verdrahtung in `rpg-plugin`. Richtung `plugin → platform → core` gewahrt. Die Warnung bei vollem Inventar läuft über die Nachrichtenschnittstelle, nicht über einen direkten Spieleraufruf — B13 kann sie später bedienen (ADR-005). |
| **IV. Datenhaltung** | ✅ | Schemaänderung nur über die versionierte Migration `V7_1`. Der Speicher-Cache ist während der Sitzung autoritativ; geschrieben wird über den Write-Behind-Puffer. Die Stufe ist versioniert über `data_version` des Charakters aus B03. **Kein Widerspruch zur Item-Regel**: die Klassenausrüstung trägt keine Werte, sie stellt einen Charakterstand dar. |
| **V. Datengetriebenes Design** | ✅ | Anzeigename, Basiswerte, Wachstum, beide Leitern, Materialien, Farben, Trims, Levelanforderungen und Fähigkeitsbindung liegen in `classes.yml`. Validierung beim Start mit Fail-Fast und benannter Ursache. Alle Spielertexte über Message-Schlüssel (`ClassMessageKeys`). |
| **VI. Korrektheit & Sicherheit** | ✅ | Der Server baut die gebundene Ausrüstung selbst; ein vom Client verändertes Item wird durch das Prädikat nicht als gebunden erkannt und beim Laden ersetzt. Eine Ausnahme im Listener bricht den Klick ab, nicht die Sitzung (FR-031). **Kein Reflection, kein NMS** — die Neutralisierung läuft vollständig über öffentliche API. |
| **VII. Tests** | ✅ | Alle Formeln und Regeln serverfrei in `rpg-core`. Persistenz gegen echtes PostgreSQL. B07 ist **kein** Block mit Lasttestpflicht (Prinzip VII nennt B05 und B10) — die Performancezusagen SC-009 und SC-010 sind trotzdem als Test formuliert. |
| **VIII. Sprache** | ✅ | Diese Dokumente auf Deutsch, Code und Config-Keys auf Englisch. |

**Ergebnis: keine Verstöße, keine Ausnahme nötig.** Die Complexity-Tracking-Tabelle bleibt leer.

Zwei Punkte, die knapp an einem Verstoß vorbeigehen und deshalb benannt sind:

- **Bewegungssperre vor der Klassenwahl.** Ein Abbruch von `PlayerMoveEvent` ist der einzige Weg ohne
  eigenen Tick-Task. Das Ereignis feuert häufig, aber die Prüfung ist ein Nullvergleich auf
  `activeCharacter()` und greift nur für Spieler ohne Charakter — im Normalbetrieb also für niemanden.
  Kein Verstoß gegen Prinzip II, aber der Grund gehört dokumentiert.
- **B07 bringt einen Bukkit-Listener mit, obwohl es Schicht 1 ist.** Genau wie B05 mit
  `VanillaDamageListener`. Die Regel liegt in `rpg-core`, nur der Listener in `rpg-platform`; Prinzip
  III ist damit gewahrt, weil die Schichtgrenze nicht der Blockgrenze folgen muss.

## Project Structure

### Documentation (this feature)

```text
specs/007-class-system/
├── plan.md              # Diese Datei
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── class-config.md  # Schema und Beispiel von classes.yml
│   └── class-api.md     # Öffentliche Schnittstelle von B07 für B08, B11, B13
├── checklists/
│   └── requirements.md  # aus /specify
└── tasks.md             # Phase 2, erst durch /speckit-tasks
```

### Source Code (repository root)

```text
rpg-core/src/main/java/rpg/core/classes/          # neues Paket - "class" ist reserviert
├── package-info.java                           # Blockgrenze und Zuständigkeit
├── CharacterClassDefinition.java               # unveränderliche Klassendefinition
├── ClassBaseStats.java                         # Basiswerte über acht Attribute
├── ClassGrowth.java                            # Zuwachs je Level, ersetzt LevelGrowth je Klasse
├── EquipmentLadder.java                        # variable Länge, normierte Kurve
├── EquipmentTier.java                          # eine Stufe: Werte, Aussehen, Level, cost
├── TierAppearance.java                         # Material + optional Färbung + optional Trim
├── LadderSlot.java                             # ARMOR | WEAPON
├── ClassProgress.java                          # erreichte Stufen je Charakter (Aggregat)
├── ClassProgressRepository.java                # Vertrag, Umsetzung in rpg-persistence
├── ClassStatContributor.java                   # BaseStatContributor - Basis + Stufenwerte
├── ClassRegistry.java                          # Auflösung Klasse -> Definition
├── ClassSelection.java                         # Ablauf der Erstwahl, Regeln
├── ClassSelectionResult.java                   # Annahme oder benannte Ablehnung
├── TierAdvance.java                            # Weiterschalten, Prüfung, Ergebnis
├── TierAdvanceRejection.java                   # Ursachen: Level, Endstufe, unbekannt
├── BoundEquipment.java                         # Bindungsprädikat und Sollzustand
├── ClassConfig.java                            # gebundene Konfiguration
├── ClassConfigSchema.java                      # Felddeklaration und Bindefunktionen
├── ClassMessageKeys.java                       # alle Spielertexte als Schlüssel
├── ClassChangedEvent.java                      # Wahl getroffen
└── TierAdvancedEvent.java                      # Stufe gestiegen

rpg-persistence/src/main/java/rpg/persistence/
└── classes/JdbcClassProgressRepository.java
rpg-persistence/src/main/resources/db/migration/
└── V7_1__character_class_progress.sql

rpg-platform/src/main/java/rpg/platform/classes/
├── ClassSelectionMenu.java                     # GUI aus Vanilla-Materialien
├── ClassSelectionListener.java                 # Klick, Schließversuch, Beitritt
├── NoCharacterGuardListener.java               # Bewegung ohne Charakter
├── EquipmentLockListener.java                  # Klick, Tausch, Offhand, Drop
├── BoundItemFactory.java                       # baut Stufe -> ItemStack, neutralisiert Modifikatoren
├── BoundItemTag.java                           # PersistentDataContainer-Schlüssel
└── ClassEquipmentApplier.java                  # setzt Sollzustand beim Laden und beim Aufstieg

rpg-plugin/src/main/java/rpg/plugin/
└── (Erweiterung der bestehenden Modulverdrahtung)
rpg-plugin/src/main/resources/
└── classes.yml

Tests spiegeln die Struktur in src/test/java der jeweiligen Module.
```

**Structure Decision**: Bestehendes Multi-Modul-Gradle-Projekt aus B01, unverändert. B07 fügt je
Modul ein Paket `classes` hinzu. `class` ist ein Java-Schlüsselwort und als Paketname unzulässig; der
Plural folgt dem bestehenden `stats` und `combat`, statt eine Sonderschreibweise wie `clazz`
einzuführen, für die es im Projekt keinen einzigen Präzedenzfall gibt. Kein neues Modul: B07 ist ein
Regelblock wie B04 bis B06 und passt in den bestehenden Schnitt.

## Constitution Check — erneut nach Phase 1

*Die Prüfung vor Phase 0 lief gegen die Absicht. Diese hier läuft gegen die entworfenen Artefakte.*

| Prinzip | Nach dem Entwurf | Was sich gegenüber der ersten Prüfung geändert hat |
|---|---|---|
| **I. Nebenläufigkeit** | ✅ | Bestätigt und konkretisiert: research.md R4 legt fest, dass das Wiederöffnen der Auswahl **einen Tick später** über den entity-gebundenen Scheduler läuft — im Ereignis selbst wäre es unzuverlässig. Der globale Scheduler kommt nirgends vor. |
| **II. Performance** | ✅ | Bestätigt, mit einem neuen Befund: das Bindungsprädikat liegt im Pfad **jedes** Inventarklicks. Es liest einen vorab aufgelösten Schlüssel aus dem PersistentDataContainer — kein Streams, kein Boxing, keine Datenbank. Die Bewegungssperre über `PlayerMoveEvent` ist benannt und begründet: ein Nullvergleich, der im Normalbetrieb auf niemanden trifft. |
| **III. Architektur** | ✅ | Der Entwurf hat die Schichtgrenze geschärft: `rpg-core` kennt den Bindungsschlüssel als **Zeichenkette**, nicht als Bukkit-Objekt (R6). Damit bleibt der Kern bukkit-frei, obwohl die Bindung an einem Bukkit-Konzept hängt. |
| **IV. Datenhaltung** | ✅ | Bestätigt. `V7_1` fasst `V3_1` nicht an (ADR-019). Die einzige Feinheit aus dem Entwurf: **keine** obere Prüfbedingung auf die Stufe im Schema, weil die Leiterlänge Konfiguration ist — geprüft wird beim Start (V19), nicht in der Datenbank. |
| **V. Datengetriebenes Design** | ✅ | Der Entwurf hat 19 Zusagen ausformuliert, die alle Fail-Fast sind. Ausdrücklich **nicht** geprüft: der Inhalt von `cost` und die Existenz einer Fähigkeits-ID — beides wäre eine Kopplung an noch nicht existierende Blöcke. |
| **VI. Korrektheit & Sicherheit** | ✅ | Verstärkt: die Charakter-ID im Bindungsschlüssel macht kopierte Gegenstände wertlos, und die einseitige Richtung „Stufe erzeugt Item" schließt aus, dass Itemmanipulation eine Stufe gewinnt. **Kein Reflection, kein NMS** — die Neutralisierung läuft über öffentliche API (R2). |
| **VII. Tests** | ✅ | Der Validierungsleitfaden hat elf Abschnitte; Abschnitt 9 läuft gegen echtes PostgreSQL, Abschnitt 11 gegen einen echten Server. Punkt 14 dort ist der einzige Nachweis, dass die Neutralisierung im laufenden Spiel greift — ohne ihn wäre R2 unbelegt. |
| **VIII. Sprache** | ✅ | Bestätigt. |

**Ergebnis: weiterhin keine Verstöße.** Ein Punkt hat sich durch den Entwurf **verschoben**, ohne ein
Verstoß zu werden: FR-009 sprach von der „Quelle Klasse", was nach `SourceKind.CLASS` klang. R1 hat
gezeigt, dass Modifikatoren dort rechnerisch falsch wären; die Anforderung ist entsprechend präzisiert
und um FR-010a ergänzt, das die Modifikatorquelle ausdrücklich frei hält.

## Complexity Tracking

Keine Verstöße gegen die Constitution, daher keine Rechtfertigung nötig.

## Was Phase 2 aufnehmen muss

Zwei Punkte aus der Recherche sind Aufgaben, keine Unklarheiten:

| Punkt | Herkunft |
|---|---|
| ~~Prüfen, dass `DefaultCombatPipeline` die Ablehnung am fehlenden aktiven Charakter festmacht~~ **erledigt (T055)**: die Ablehnung fällt an `NO_HOLDER`, weil ein Halter je Charakter entsteht. Keine Änderung an B05 nötig. | research.md R4 |
| Die Warrior-Werte aus `spec.md` in `classes.yml` übertragen und die Rogue- und Mage-Leitern vollständig ausschreiben — im Vertragsdokument sind sie gekürzt. | contracts/class-config.md |
