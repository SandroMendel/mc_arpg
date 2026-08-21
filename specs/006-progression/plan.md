# Implementation Plan: B06 · Progression (Erfahrung & Level)

**Branch**: `006-progression` | **Date**: 2026-08-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-progression/spec.md`

## Summary

B06 ist der erste Block, der zwei Dinge gleichzeitig tut: er hängt im heissesten Pfad des Projekts
(jeder Mob-Tod aus B05) **und** er persistiert. Fünf Entscheidungen prägen die Umsetzung:

1. **Das Levelwachstum läuft über `BaseStatContributor`, nicht über einen Modifikator.** Das ist
   keine neue Entscheidung — ADR-013 hat sie beim Abschluss von B04 vorweggenommen („Basiswerte
   kommen über `BaseStatContributor` (B06 Level, B07 Klasse)"), und `AttributeDefinition.bandFloor`
   nimmt den Basiswert genau deshalb als Parameter. Der Grund ist rechnerisch: das Modifikatorband
   („plus/minus 30 %") wird um den **effektiven** Basiswert gelegt. Käme das Levelwachstum als
   FLAT-Modifikator, blieb das Band am Level-1-Basiswert hängen und würde mit jedem Level enger,
   statt mitzuwachsen. Ausrüstung aus B11 wäre auf Level 60 messbar falsch geklammert.

2. **Die XP-Tabelle ist ein Kartenfeld, kein Feld je Level.** 59 einzelne Pflichtfelder im Schema
   wären nicht nur hässlich, sie könnten die eigentlichen Zusagen — Lückenlosigkeit und strenge
   Monotonie — gar nicht ausdrücken. Also eine Karte wie `mobs.by-type` in B05, deren Bindefunktion
   die drei Regeln prüft und mit dem ersten beanstandeten Level abbricht.

3. **Die Fortschrittsbündelung ist das Muster von `DamageAggregator`, nicht ein zweites.** Ein
   Zeitstempel je Charakter, geschlossen vom **nächsten** Ereignis, von einem Levelaufstieg oder vom
   Sitzungsende — niemals von einer Aufgabe. B05 hat dieselbe Frage für Schadenszahlen schon
   beantwortet; eine zweite Antwort wäre eine zweite Sache, die kaputtgehen kann.

4. **Fortschritt bekommt eine eigene Tabelle mit eigenem Schreiber.** `V6_1__character_progress`
   nach demselben Argument, mit dem B04 `character_stats` nicht in `character` gelegt hat: eine
   gemeinsame Zeile heisst ein gemeinsamer Schreiber und ein gemeinsamer Revisionszähler, und die
   Blockgrenze aus Prinzip III stünde nur noch auf dem Papier. Gespeichert werden ausschliesslich
   Level und XP **im** Level (FR-053a) — keine Gesamt-XP, aus der ein Level abgeleitet würde.

5. **Die Party ist Laufzeitzustand ohne jede Speicherung.** Keine Tabelle, kein Repository, keine
   Migration. Ein Serverneustart löscht sie, und das ist die Zusage, nicht der Nebeneffekt.

Was wie ein Widerspruch zu B05 aussieht und keiner ist: B05 hat entschieden „XP anteilig nach
Schadensanteil". Das Party-System ersetzt das nicht, es klammert es — eine Party ist **ein**
Beitragender, dessen Anteil die Summe der Anteile ihrer Mitglieder ist. Ohne diese Klammer gäbe es
zwei Verteilungsregeln für dieselbe XP.

## Technical Context

**Language/Version**: Java 25 (ADR-001), Toolchain aus B01 unverändert.

**Primary Dependencies**: Keine neuen. B06 nutzt, was B02 bis B05 bereits mitbringen: den
Write-Behind-Puffer, die Sitzungsverwaltung, `StatEngine` und `CombatDeathEvent`. Paper-API bleibt
`compileOnly` und nur in `rpg-platform`.

**Storage**: PostgreSQL, **eine neue Tabelle** `rpg.character_progress` (Migration `V6_1`), über den
Write-Behind-Puffer aus B02 geschrieben. Zwei Spalten mit Nutzdaten: Level und XP im Level. Die Party
wird ausdrücklich **nicht** persistiert.

**Testing**: JUnit 5 + AssertJ für Kurve, Aufstieg, Verteilung, Party-Zustandsübergänge und
Bündelung — vollständig serverfrei in `rpg-core` mit gesteuerter Uhr. Testcontainers mit echtem
PostgreSQL für Repository, Migration und Datenversionswechsel (Prinzip VII verbietet Mocks gegen die
Datenbank). MockBukkit in `rpg-platform` für die Reichweitenprüfung und die Anbindung an das
Todesereignis. `FullBootstrapTest` in `rpg-plugin` beweist die Verdrahtung (ADR-012).

**Target Platform**: Linux-VPS, Paper-Server (Minecraft 26.2 / Java 25).

**Project Type**: Regel-Engine-Block mit Persistenzanteil, innerhalb des Multi-Modul-Gradle-Projekts
aus B01.

**Performance Goals**: 1000 XP-Ereignisse je Sekunde ohne einen Datenbankzugriff (SC-004). 10 000
XP-Ereignisse ohne vermeidbare Objekterzeugung (SC-005). Anzahl geplanter Aufgaben unabhängig von
der Spielerzahl konstant (SC-012). Levelanforderungsabfrage ohne Datenbankzugriff (SC-011).

**Constraints**: Kein Bukkit-Import in `rpg-core` — durch die Gradle-Modulgrenze erzwungen. Keine
wiederkehrende Aufgabe je Spieler, Charakter oder Party (FR-061). Kein Datenbankzugriff je
XP-Ereignis (FR-054). Speicherstand ist während der Sitzung autoritativ (FR-055). Die
Reichweitenmessung ist nur über einen Erweiterungspunkt erreichbar (FR-044).

**Scale/Scope**: 100–200 gleichzeitige Spieler (ADR-002), bis zu 800 aktive Mobs, bis zu drei
Charaktere je Konto. Konsumenten sind B07, B08, B09, B11, B12, B13 und B14.

### Abgrenzung — was B06 ausdrücklich nicht tut

| Gehört zu | Was B06 stattdessen liefert |
|---|---|
| B07 Klassen | Klassenspezifische Basiswerte und Wachstumskurven. B06 liefert den Mechanismus und eine klassenneutrale Vorgabe, die B07 je Klasse ersetzt. |
| B08 Fähigkeiten | Freischaltung, Coin-Aufwertung, Mana. B06 liefert das Aufstiegsereignis und die Levelanforderungsabfrage. |
| B09 Zonen | Zonengeometrie, Zugangsregeln, Zonen-Ziele. B06 liefert die Levelabfrage und den Eingangspunkt für weitere XP-Quellen. |
| B10 Mobs | Was ein Mob *ist*. B06 überbrückt nur den XP-Betrag je Mob-Art, hinter einer austauschbaren Schnittstelle. |
| B11 Items | Itemdefinitionen und Levelanforderungen je Item. B06 liefert die Abfrage. |
| B12 Statistiken | Auswertung und Leaderboards. B06 veröffentlicht die Ereignisse. |
| B13 UI | Fortschrittsbalken, Party-Anzeige, Aufstiegsmeldung. B06 liefert gebündelte Ereignisse, aber kein Anzeigeobjekt. |
| B14 Befehle | `/party invite`, `/party kick`, `/xp set`. B06 liefert die Verträge, die diese Befehle aufrufen. |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Prinzip | Prüfung | Status |
|---|---|---|
| I. Nebenläufigkeit | XP entsteht im Tick, in dem das Todesereignis eintrifft, und wird dort synchron verrechnet. Der einzige I/O — das Schreiben des Stands — läuft über den Write-Behind-Puffer aus B02 und damit asynchron, ohne dass B06 selbst einen Thread kennt. Kein `join()`, kein direkter Bukkit-Scheduler. Der Fortschrittsstand hängt am Charakter, die Party am Registry-Eintrag; kein globaler veränderlicher Zustand im Gameplay-Pfad. | PASS |
| II. Performance | Keine wiederkehrende Aufgabe: der Einladungsverfall und das Bündelungsfenster sind Zeitstempel, die erst bei Zugriff ausgewertet werden — dasselbe Muster wie `AttackWindow` und `DamageAggregator` in B05. Die Verteilung rechnet auf `double` und `long` ohne Boxing und ohne Streams; die Party ist ein Array fester Höchstgrösse. Kein Datenbankzugriff je XP-Ereignis, nur eine Änderungsmarkierung. Die Levelabfrage liest ein `int` aus dem Speicherstand. | PASS |
| III. Architektur | Kurve, Aufstieg, Verteilung, Party und Bündelung liegen vollständig in `rpg-core` und sind serverfrei prüfbar. Die Reichweitenmessung — das Einzige, was Bukkit braucht — liegt hinter `ProximityCheck` in `rpg-platform`. Zugriff auf B04 nur über `StatEngine`, auf B02 nur über die Repository-Schnittstellen, auf B05 nur über das Todesereignis. Richtung `plugin → persistence → core` bleibt durch den Gradle-Graphen erzwungen. | PASS |
| IV. Datenhaltung | Neue Tabelle über eine versionierte Migration (`V6_1`), Format über `data_version` migrierbar, Revisionszähler wie in `player_state`, `character` und `character_stats`. Während der Sitzung ist der Speicherstand autoritativ. Gespeichert werden nur Rohwerte — Level und XP im Level, keine Gesamt-XP und keine berechneten Maxima. Kein Datenverlust über das Autosave-Intervall hinaus, weil das Sitzungsende erzwungen schreibt. | PASS |
| V. Datengetriebenes Design | XP-Kurve, XP je Mob-Art, Attributwachstum je Level, Party-Reichweite, Nähe-Bonus samt Obergrenze, maximale Partygrösse, Einladungsfrist und Bündelungsfenster stehen in `progression.yml` und werden beim Start gegen ein Schema geprüft; ein Fehler bricht den Start ab und nennt das beanstandete Level. Kein Spielertext im Code — alles über Message-Schlüssel. | PASS |
| VI. Korrektheit & Sicherheit | Der Server ist alleinige Autorität; kein XP-Betrag stammt aus einer Client-Eingabe. Nicht endliche, negative und Null-Beträge werden abgelehnt und protokolliert, nie als Abzug gedeutet. Eine Ausnahme in der Vergabe bleibt auf den Charakter begrenzt und lässt den Kampfvorgang laufen (FR-059), nach dem Muster von B01s `ModuleFaultBarrier`. Der Verwaltungseingriff ist die einzige senkende Quelle und landet vollständig im Audit-Log. Kein Reflection, kein NMS. | PASS |
| VII. Tests | Kurve, Aufstieg, Verteilung, Party und Bündelung serverfrei unit-getestet mit ausgerechneten Beispielen (SC-001, SC-006, SC-007). Persistenz gegen echtes PostgreSQL via Testcontainers, einschliesslich Datenversionswechsel (SC-016). B06 ist **nicht** lasttestpflichtig — Prinzip VII nennt B05 und B10 beim Namen, nicht B06. Die Nullallokations- und Null-DB-Zusagen werden trotzdem gezählt geprüft (SC-004, SC-005). | PASS |
| VIII. Sprache | Planung und Spec-Artefakte auf Deutsch; Pakete, Typen, Felder, Config-Keys und Message-Schlüssel auf Englisch. | PASS |

**Ergebnis: 8/8 PASS.** Drei Entwurfsentscheidungen berühren fremde Blöcke oder weichen von einem
etablierten Muster ab und sind unter *Complexity Tracking* begründet.

### Nachprüfung nach Phase 1

Nach Ausarbeitung von Datenmodell, Verträgen und Validierungsleitfaden erneut geprüft: unverändert
8/8 PASS. Drei Punkte wurden im Entwurf nachgeschärft:

- **Reihenfolge beim Auffüllen.** FR-021a füllt Leben und Mana auf das neue Maximum. Beim
  Ausarbeiten des Datenmodells zeigte sich, dass die Reihenfolge nicht beliebig ist: erst
  `BaseStatContributor` neu einlesen und den Wertestand neu berechnen, **dann** auffüllen. Umgekehrt
  wäre gegen das alte Maximum gefüllt worden — ein Fehler, der nur bei jedem Aufstieg um wenige
  Prozent daneben liegt und deshalb lange unentdeckt geblieben wäre. Jetzt in FR-021b festgehalten.
- **Widerspruch in B04 gefunden und aufgelöst.** `SourceKind.LEVEL` ist dokumentiert als „The
  character's level (B06)", während ADR-013 für dasselbe den `BaseStatContributor` vorschreibt. B06
  folgt ADR-013 und lässt `SourceKind.LEVEL` unbenutzt; der Grund steht in
  [research.md](./research.md) unter Entscheidung 1. Das ist bewusst und nicht versehentlich.
- **Die Party braucht einen Beitrittszeitpunkt**, den die Spezifikation zunächst nicht nannte. Ohne
  ihn ist „dienstältestes verbleibendes Mitglied" aus FR-029c nicht entscheidbar. Als Feld im
  Datenmodell nachgetragen.

## Project Structure

### Documentation (this feature)

```text
specs/006-progression/
├── plan.md              # Diese Datei
├── research.md          # Phase 0 — Entwurfsentscheidungen mit Alternativen
├── data-model.md        # Phase 1 — Typen, Regeln, Tabelle, Zustandsübergänge
├── quickstart.md        # Phase 1 — Validierungsleitfaden
├── contracts/           # Phase 1 — öffentliche Schnittstellen des Blocks
│   ├── progression.md
│   ├── party.md
│   ├── progression-config.md
│   └── events.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 — erzeugt von /speckit-tasks
```

### Source Code (repository root)

```text
rpg-core/src/main/java/rpg/core/progression/
├── XpCurve.java                    # Tabelle Level -> Schwelle, validiert (FR-001 bis FR-004)
├── XpSource.java                   # MOB_KILL, ZONE_OBJECTIVE, ADMIN (FR-007, FR-048)
├── ProgressState.java              # Level + XP im Level, der persistierte Rohwert (FR-053a)
├── LevelUp.java                    # altes Level, neues Level, Überschuss (FR-017 bis FR-019)
├── LevelGrowth.java                # Zuwachs je Level über alle acht Attribute (FR-022a)
├── LevelStatContributor.java       # der BaseStatContributor aus ADR-013 (FR-020)
├── Progression.java                # öffentliche Schnittstelle des Blocks
├── DefaultProgression.java         # Vergabe, Aufstieg, Maximallevel (FR-007 bis FR-024c)
├── XpDistributor.java              # das Fünf-Schritt-Verfahren (FR-039 bis FR-047)
├── MobXpProvider.java              # Schnittstelle, die B10 übernimmt (FR-009)
├── WorldPoint.java                 # bukkitfreier Ort: Welt-Id und drei Koordinaten (FR-041a)
├── ProximityCheck.java             # Erweiterungspunkt für die Reichweite (FR-044)
├── ProgressAggregator.java         # Bündelung nach dem Muster aus B05 (FR-023a, FR-023c)
├── Party.java                      # Mitglieder, Anführer, Beitrittszeitpunkte (FR-029a bis FR-035)
├── PartyInvite.java                # Einladung mit Zeitstempel, lazy verfallend (FR-031)
├── PartyRegistry.java              # Laufzeitverwaltung, nichts persistiert (FR-029)
├── PartyRejection.java             # ALREADY_IN_PARTY, PARTY_FULL, NOT_LEADER, ... (FR-032, FR-033)
├── ProgressionConfig.java          # validierte progression.yml
├── ProgressionConfigSchema.java    # Schema samt Kurvenprüfung (FR-002, FR-003)
├── ProgressionMessageKeys.java     # alle Spielertexte als Schlüssel (FR-038)
├── LevelUpEvent.java               # ein Ereignis je Aufstieg (FR-023)
├── ProgressChangedEvent.java       # gebündelter Zuwachs (FR-023a)
└── PartyChangedEvent.java          # Beitritt, Austritt, Rollenwechsel, Auflösung (FR-036)

├── CharacterProgress.java          # gespeicherte Form: Kennung, Level, XP, Datenversion, Revision
└── CharacterProgressRepository.java# Schnittstelle. Liegt in core, NICHT in rpg-persistence:
                                    # DefaultProgression braucht sie, und die Richtung
                                    # plugin -> persistence -> core erlaubt nichts anderes.
                                    # Im BLOCKPAKET, wie CharacterResourcesRepository in
                                    # rpg/core/stats/ (B04) — rpg/core/persistence/ gehört B02

rpg-persistence/src/main/java/rpg/persistence/progression/
├── ProgressionModule.java          # Modulverdrahtung nach B01-Vertrag
├── ProgressSessionAttachment.java  # laden bei Sitzungsstart, freigeben bei Sitzungsende
│                                   # (FR-034, FR-058) — Muster von StatsModule aus B04
└── JdbcCharacterProgressRepository.java  # Schreiber, im Flush-Zyklus registriert (FR-054)

rpg-persistence/src/main/resources/db/migration/
└── V6_1__character_progress.sql    # Level und XP im Level, eigene Tabelle (FR-053)

rpg-platform/src/main/java/rpg/platform/progression/
├── PaperProximityCheck.java        # Entfernung zum gestorbenen Gegner (FR-041a, FR-045)
└── ProgressionDeathListener.java   # hängt am Todesereignis aus B05 (FR-008)

rpg-plugin/src/main/resources/
└── progression.yml

rpg-core/src/test/java/rpg/core/progression/          # der Grossteil, serverfrei
rpg-persistence/src/test/java/rpg/persistence/progression/  # Testcontainers, echtes PostgreSQL
rpg-platform/src/test/java/rpg/platform/progression/  # Reichweite und Listener, MockBukkit
rpg-plugin/src/test/java/rpg/plugin/                  # FullBootstrapTest erweitert
```

**Structure Decision**: Regeln in `rpg-core`, Persistenz in `rpg-persistence`, Paper-Anbindung in
`rpg-platform`, Zusammenbau in `rpg-plugin`. **`ProgressionModule` liegt in `rpg-persistence`**, wie
`SessionModule` und `StatsModule` — anders als `CombatModule`, das in `rpg-core` liegt. Der
Unterschied hat einen Grund: B05 hat keine Datenbank, B06 hat eine. Das Modul dort abzulegen, wo sein
Repository steht, macht die Abhängigkeit sichtbar statt sie zu verstecken.

## Complexity Tracking

| Abweichung | Warum nötig | Verworfene einfachere Alternative |
|---|---|---|
| B06 liefert XP-Beträge je Mob-Art (B10-Gebiet) | Ohne Beträge gibt jeder Kill null XP, und der gesamte Block wäre fertig, grün getestet und im Spiel wirkungslos. Dasselbe Argument, mit dem B05 Mobs vorläufig Attributwerte gibt — und dieselbe Lösung, damit B10 später nur eine Schnittstelle ersetzt statt Code zu ändern. | Nur die Schnittstelle liefern und auf B10 warten. Verworfen: B06 wäre nicht abnehmbar, weil SC-002 einen konkreten Betrag verlangt. |
| B06 besitzt das Party-Modell, obwohl Party nach einem eigenen Block klingt | Der Zweck der Party in diesem Projekt *ist* die XP-Teilung; ein eigener Block hätte keinen anderen Inhalt. Die Blockliste B01–B17 kennt keinen Party-Block, also wäre die Alternative ein neuer Block mit einer einzigen Aufgabe. Befehle und Anzeige liegen ohnehin bei B14 und B13 — genau wie B06 auch sein HUD nicht besitzt. | Einen Block B18 Party aufmachen. Verworfen, weil er nichts enthielte, was nicht Fortschrittslogik ist. |
| Klassenneutrales Attributwachstum in B06, obwohl B07 die Klassen besitzt | Ohne mitgelieferte Vorgabe wäre kein einziges Erfolgskriterium zum Aufstieg prüfbar, und B06 hinge an B07. Die Vorgabe ist Konfiguration, kein Code — B07 ersetzt Zahlen, nicht Verhalten (FR-022). | Das Wachstum leer lassen und B07 abwarten. Verworfen: SC-001 und SC-019 wären bis B07 nicht nachweisbar. |

## Phasen

### Phase 0 — Recherche

Abgeschlossen. Entwurfsentscheidungen mit Alternativen in [research.md](./research.md). Keine
offenen `NEEDS CLARIFICATION`: die drei Blockfragen und zehn weitere aus zwei `/clarify`-Runden sind
in der Spezifikation verankert. Der bei `/clarify` zurückgestellte Punkt — welcher B04-Erweiterungs\
punkt das Levelwachstum trägt — ist keine offene Frage, sondern durch ADR-013 bereits entschieden;
Entscheidung 1 in `research.md` belegt das.

### Phase 1 — Entwurf & Verträge

Abgeschlossen:

- [data-model.md](./data-model.md) — Typen, Validierungsregeln, Tabellenaufbau,
  Zustandsübergänge der Party, Speicherabschätzung.
- [contracts/progression.md](./contracts/progression.md) — die Schnittstelle, gegen die B07 bis B14
  entwickeln.
- [contracts/party.md](./contracts/party.md) — Party-Verwaltung und die Verträge, die B14 aufruft.
- [contracts/progression-config.md](./contracts/progression-config.md) — Aufbau und Schema von
  `progression.yml` samt der drei Kurvenregeln.
- [contracts/events.md](./contracts/events.md) — die drei veröffentlichten Ereignisse und ihre
  Reihenfolgezusage.
- [quickstart.md](./quickstart.md) — Validierungsabschnitte.

### Phase 2 — Aufgaben

Nicht Teil dieses Befehls. Erzeugt durch `/speckit-tasks`.
