---

description: "Aufgabenliste B10 · Mobs & Horden-Spawning"
---

# Tasks: B10 · Mobs & Horden-Spawning

**Input**: Entwurfsunterlagen aus `/specs/010-mobs-spawning/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: **Pflicht, nicht optional.** Prinzip VII der Constitution verlangt für jede Formel und
jede Regel der Domänenschicht Unit-Tests ohne laufenden Server. Testaufgaben stehen deshalb je
Geschichte **vor** der Umsetzung. Persistenz gibt es in diesem Block nicht, also auch keinen
Testcontainer — der erste Block seit B03, für den das gilt.

**Organization**: gruppiert nach den acht User Stories der Spec, in Abhängigkeitsreihenfolge.

## Format: `[ID] [P?] [Story] Beschreibung`

- **[P]**: parallelisierbar — andere Datei, keine offene Abhängigkeit
- **[Story]**: US1 bis US7; Setup, Foundational und Polish tragen keine
- Jede Aufgabe nennt ihren Pfad und ihren Bezug (FR, SC, R oder Artefakt)

## Pfade

- `rpg-core/src/main/java/rpg/core/mob/` — Arten, Budget, Auswahl, Regeln, **ohne Bukkit**
- `rpg-platform/src/main/java/rpg/platform/mob/` — hier und nur hier wird Paper angefasst
- `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` — die Verdrahtung
- `rpg-plugin/src/main/resources/` — `mobs.yml` (neu), `messages.yml`
- Tests jeweils unter `src/test/java/` desselben Moduls

**Keine Persistenz.** Eine Kreatur überlebt keinen Neustart (FR-023) — kein Aggregattyp, keine
Migration, kein `rpg-persistence`-Anteil.

---

## Phase 1: Setup — Pakete und Gerüste

**Purpose**: die Blockgrenzen benennen, bevor etwas darin entsteht

- [X] T001 [P] Paket `rpg-core/src/main/java/rpg/core/mob/` mit `package-info.java` anlegen — die Blockgrenze benennen: Arten, Budget, Auswahl und Aufräumentscheidung gehören hierher, **Paper nirgends**, Beute über Coins hinaus ausdrücklich **nicht** (B11), nach dem Muster von `rpg/core/zone/package-info.java`
- [X] T002 [P] Paket `rpg-platform/src/main/java/rpg/platform/mob/` mit `package-info.java` anlegen — Kopfkommentar: hier und nur hier wird Paper angefasst; hier steht auch, warum es keine öffentliche `spawn(...)` gibt (contracts/mob-api.md §5)
- [X] T003 [P] Testpakete `rpg-core/src/test/java/rpg/core/mob/` und `rpg-platform/src/test/java/rpg/platform/mob/` anlegen
- [X] T004 `MobMessageKeys` in `rpg-core/src/main/java/rpg/core/mob/MobMessageKeys.java` mit `all()` anlegen — nach dem Muster von `CombatMessageKeys`, damit die Auflösungsprüfung im Plugin-Modul sie mitnimmt
- [X] T005 Leeres `mobs.yml` mit Kopfkommentar in `rpg-plugin/src/main/resources/mobs.yml` anlegen — Aufbau nach [contracts/mob-config.md](./contracts/mob-config.md), noch ohne Inhalte

---

## Phase 2: Foundational — Konfiguration, Budget, Bestand

**Purpose**: das Fundament, ohne das keine Geschichte anfangen kann

**⚠️ CRITICAL**: Vor Abschluss dieser Phase beginnt keine User Story

- [X] T006 [P] `MobKind` als Record in `rpg-core/src/main/java/rpg/core/mob/MobKind.java` — Felder nach [data-model.md](./data-model.md); kompakter Konstruktor prüft: `key` nicht leer, `level` ≥ 1, `followRange` > 0, `xp`/`coins` ≥ 0
- [X] T007 [P] `HordeSpec` und `HordeEntry` in `rpg-core/src/main/java/rpg/core/mob/HordeSpec.java` — welche Arten in welchem Bereich (FR-012); `entries` nicht leer, `weight` ≥ 1
- [X] T008 [P] `BossSpec` in `rpg-core/src/main/java/rpg/core/mob/BossSpec.java` — `areaKey` statt eigener Koordinaten (research.md R10), `respawn` > 0
- [X] T009 [P] `Budget` in `rpg-core/src/main/java/rpg/core/mob/Budget.java` — vier Grenzen (serverweit, Zone, Chunk, Spieler), dazu `boolean allows(...)`, das **die schärfste** entscheiden lässt; Javadoc sagt, dass es eine Grenze ist und kein Zielwert (FR-013)
- [X] T010 [P] Test `BudgetTest` in `rpg-core/src/test/java/rpg/core/mob/BudgetTest.java` — die schärfste Grenze gewinnt; ein volles Chunk-Budget verhindert das Setzen auch bei freier Zone
- [X] T011 `ChunkCount` in `rpg-core/src/main/java/rpg/core/mob/ChunkCount.java` — `long → int` ohne Boxing, **nur belegte Chunks**; Eintrag verschwindet bei null (research.md R3); Javadoc nennt ausdrücklich, warum B09s `ChunkTable` hier nicht wiederverwendet wird
- [X] T012 Test `ChunkCountTest` in `rpg-core/src/test/java/rpg/core/mob/ChunkCountTest.java` — hoch, runter, auf null; die Struktur schrumpft wirklich und wächst nicht monoton
- [X] T012a `NearbyChunks` in `rpg-core/src/main/java/rpg/core/mob/NearbyChunks.java` — **der räumliche Index, den FR-018 verlangt**: jeder Spieler stempelt die Chunks im Aufräumradius in eine wiederverwendete Long-Menge, danach ist die Frage je Kreatur ein Mengenzugriff statt einer Schleife über alle Spieler (research.md R3a, Prinzip II). Der Puffer wird zwischen Durchläufen wiederverwendet — eine neue Menge je Durchlauf wäre eine Zuweisung im Spawn-Pfad
- [X] T012b Test `NearbyChunksTest` in `rpg-core/src/test/java/rpg/core/mob/NearbyChunksTest.java` — Grenzfall am Radiusrand; zwei nah beieinanderstehende Spieler stempeln denselben Chunk nur einmal; und die Menge ist beim nächsten Durchlauf wirklich geleert
- [ ] T012c Test `CleanupCostIsFlatTest` in `rpg-core/src/test/java/rpg/core/mob/CleanupCostIsFlatTest.java` — die Aufräumentscheidung für 130 Kreaturen kostet bei gleicher Spielerzahl nicht messbar mehr als die für 13. **Der Test, der eine lineare Iteration auffliegen ließe** — ohne ihn wäre der Verstoß grün und fiele erst unter Last auf
- [X] T013 `HordeRegistry` in `rpg-core/src/main/java/rpg/core/mob/HordeRegistry.java` — Bestand plus die drei Zählungen (Zone, Chunk, gesamt) aus [data-model.md](./data-model.md)
- [X] T014 Test `HordeRegistryTest` in `rpg-core/src/test/java/rpg/core/mob/HordeRegistryTest.java` — Eintragen, Austragen, Zählungen stimmen nach jeder Folge; eine Kreatur bleibt der **Ursprungs**zone zugerechnet (FR-017)
- [X] T015 `MobConfig` in `rpg-core/src/main/java/rpg/core/mob/MobConfig.java` — der validierte Inhalt von `mobs.yml`: Budgets, Horden-Werte, Arten, Horden (FR-001). **Kein Schalter für die Unterdrückung** — siehe T057
- [X] T016 `MobConfigSchema` in `rpg-core/src/main/java/rpg/core/mob/MobConfigSchema.java` — Bindung und Prüfung nach der Regeltabelle in [contracts/mob-config.md](./contracts/mob-config.md); jede Meldung nennt **Datei, Schlüssel und Grund** (FR-002)
- [X] T017 Test `MobConfigSchemaTest` in `rpg-core/src/test/java/rpg/core/mob/MobConfigSchemaTest.java` — je ein Fall für jede Regel der Tabelle; geprüft wird nicht nur *dass* es scheitert, sondern **dass die Meldung den Schlüssel nennt**
- [X] T018 `MobModule` in `rpg-core/src/main/java/rpg/core/mob/MobModule.java` — Start, Konfiguration laden, Nachladen nach dem Muster von `ZoneModule`
- [ ] T019 Test `MobModuleTest` in `rpg-core/src/test/java/rpg/core/mob/MobModuleTest.java` — Start scheitert bei unbrauchbarer Konfiguration, Nachladen tauscht die Konfiguration im Ganzen
- [X] T020 `MobKinds` und `Hordes` als die zwei öffentlichen Abfragen in `rpg-core/src/main/java/rpg/core/mob/` — Signaturen und Zusagen nach [contracts/mob-api.md](./contracts/mob-api.md) §2 und §3
- [X] T021 Test `MobApiContractTest` in `rpg-core/src/test/java/rpg/core/mob/MobApiContractTest.java` — unbekannter Artschlüssel antwortet **leer**, unbekannter Zonenschlüssel antwortet **0**, beide werfen nicht

**Checkpoint**: Fundament steht — die Geschichten können beginnen

---

## Phase 3: User Story 1 — Eine Kreatur ist eine Art aus der Konfiguration (P1) 🎯 MVP

**Goal**: Werte, Erfahrung, Coins und Name folgen der **Art**, nicht dem Vanilla-Typ. Mehrere Arten
auf demselben Basis-Entity sind unterscheidbar.

**Independent Test**: Zwei Arten auf `ZOMBIE` mit unterschiedlichen Werten laden, je eine Kreatur
erzeugen, Werte und Beim-Tod-Beträge vergleichen. Ein ungekennzeichneter Zombie behält die
Standardwerte.

### Tests zuerst

- [ ] T022 [P] [US1] Test `MobKindLookupTest` in `rpg-core/src/test/java/rpg/core/mob/MobKindLookupTest.java` — zwei Arten auf derselben Basis liefern verschiedene Werte (FR-004)
- [ ] T023 [P] [US1] Test `MobKeyFallbackTest` in `rpg-core/src/test/java/rpg/core/mob/MobKeyFallbackTest.java` — ohne Vermerk fällt der Schlüssel auf den Vanilla-Typnamen zurück, und die vorhandenen `combat.yml`-Einträge greifen weiter (FR-009, research.md R5)
- [ ] T024 [P] [US1] Test `ConfigOnlyMobTest` in `rpg-core/src/test/java/rpg/core/mob/ConfigOnlyMobTest.java` — SC-001 und FR-003: eine neue Art rein aus Konfiguration, **und kein Bezeichner einer einzelnen Art irgendwo im Code**; nach dem Muster von `ConfigOnlyAbilityTest`
- [ ] T025 [P] [US1] Test `MobKindTagTest` in `rpg-platform/src/test/java/rpg/platform/mob/MobKindTagTest.java` — Schreiben und Lesen von `rpg:mob_kind` und `rpg:mob_zone`; ein fremder Gegenstand antwortet leer

### Umsetzung

- [ ] T026 [US1] `MobKindTag` in `rpg-platform/src/main/java/rpg/platform/mob/MobKindTag.java` — die zwei PDC-Schlüssel, nach dem Muster von `CoinPileTag`; Javadoc nennt, warum `rpg:mob_zone` dazugehört (FR-017, research.md R2)
- [ ] T027 [US1] `PaperMobPlacer.place(...)` in `rpg-platform/src/main/java/rpg/platform/mob/PaperMobPlacer.java` — Entität setzen, Vermerk schreiben, Attribute über B04 anlegen (FR-005, FR-008); Bukkit-Aufrufe ortsgebunden über B01s Scheduler
- [ ] T028 [US1] `kindKeyOf(entity)` als **die eine** Ableitung in `rpg-platform/src/main/java/rpg/platform/mob/MobKindTag.java` — PDC-Wert, sonst `entity.getType().name()` (research.md R5)
- [ ] T029 [US1] `PaperMobStats` in `rpg-platform/src/main/java/rpg/platform/mob/PaperMobStats.java` — ersetzt `PaperMobStatProvider`; **dieselbe Schnittstellenform**, Schlüssel ist jetzt die Art (FR-006)
- [ ] T030 [US1] `MobXpProvider` aus `mobs.yml` bedienen — B06s Übergangsanbieter im Plugin gegen den neuen tauschen (FR-007)
- [ ] T031 [US1] `MobCoinProvider` aus `mobs.yml` bedienen — B08bs Übergangsanbieter im Plugin gegen den neuen tauschen (FR-007)
- [ ] T032 [US1] `CoinDropListener` in `rpg-platform/src/main/java/rpg/platform/currency/CoinDropListener.java` auf `kindKeyOf(entity)` umstellen — **das ist die Zeile, an der vier Arten auf ZOMBIE bisher derselbe Schlüssel waren**
- [ ] T033 [US1] `ProgressionDeathListener` in `rpg-platform/src/main/java/rpg/platform/progression/ProgressionDeathListener.java:63` auf `kindKeyOf(entity)` umstellen — **hier wird die Erfahrung vergeben**; ohne diese Zeile geben alle acht Arten einer Region dieselbe (FR-007)
- [ ] T033a [US1] `MobEquipmentListener` in `rpg-platform/src/main/java/rpg/platform/combat/MobEquipmentListener.java:90` auf `kindKeyOf(entity)` umstellen — dort werden die Werte einer Kreatur gesetzt, und `statsFor(entity.getType().name())` würde vier Arten auf `ZOMBIE` gleich behandeln (FR-006)
- [ ] T033b [US1] Test `NoRawTypeNameLeftTest` in `rpg-platform/src/test/java/rpg/platform/mob/NoRawTypeNameLeftTest.java` — **kein Produktivcode führt `getType().name()` mehr in eine der drei Schnittstellen**; nach dem Muster von `ConfigOnlyAbilityTest`, das SC-001 genauso maschinell absichert. Ohne diesen Wächter setzt der nächste Listener wieder den Vanilla-Namen ein, und niemand merkt es
- [ ] T034 [US1] `MobNameplate` in `rpg-platform/src/main/java/rpg/platform/hud/MobNameplate.java` auf Art und Level umstellen — Text über Message-Schlüssel, keine zweite Anzeige (FR-010, research.md R11)
- [ ] T035 [P] [US1] Anzeigenamen der Arten in `rpg-plugin/src/main/resources/messages.yml` ergänzen — mit Kommentar, dass die Art den **Schlüssel** nennt und nie den Text
- [ ] T036 [P] [US1] Erste vollständige Region in `rpg-plugin/src/main/resources/mobs.yml`: acht Arten für *Greenfields*, mit Levelband 1–10 abgestimmt
- [ ] T037 [US1] Test `MobStatSeamTest` in `rpg-platform/src/test/java/rpg/platform/mob/MobStatSeamTest.java` — die drei übernommenen Schnittstellen antworten nach Art; **keine hat eine zweite Fassung bekommen** (SC-008)

**Checkpoint**: US1 steht für sich. Ein Betreiber kann Kreaturen von Hand setzen und sie verhalten
sich richtig — noch ohne Horde.

---

## Phase 4: User Story 2 — Die Horde steht da, wo B09 den Bereich benannt hat (P2)

**Goal**: Kreaturen entstehen in den Spawn-Bereichen, über Ticks verteilt, und das Budget hält unter
jeder Bedingung.

**Independent Test**: Mit einem Spieler in *Greenfields* zählen, wo und wie viele entstehen. Ein
künstlich kleines Budget setzen und prüfen, dass es nicht überschritten wird.

### Tests zuerst

- [ ] T038 [P] [US2] Test `SpawnPlannerTest` in `rpg-core/src/test/java/rpg/core/mob/SpawnPlannerTest.java` — nur innerhalb der Bereiche, Gewichte werden beachtet, leere Zone erzeugt nichts (FR-011, FR-015)
- [ ] T039 [P] [US2] Test `BudgetHoldsTest` in `rpg-core/src/test/java/rpg/core/mob/BudgetHoldsTest.java` — **SC-003**: bei plötzlichem Andrang wird keine Grenze überschritten; ausdrücklich auch, wenn die Zieldichte rechnerisch darüber liegt
- [ ] T040 [P] [US2] Test `UnknownZoneIsNormalTest` in `rpg-core/src/test/java/rpg/core/mob/UnknownZoneIsNormalTest.java` — unbekannter Zonenschlüssel und leere Bereichsliste erzeugen nichts und werfen nicht (FR-016)
- [ ] T039a [P] [US2] Test `ServerWideBudgetHoldsTest` in `rpg-core/src/test/java/rpg/core/mob/ServerWideBudgetHoldsTest.java` — sechs Zonen zu je 200 bei einem serverweiten Budget von 800: die siebte Zone bekommt nichts mehr (FR-013a, SC-011). Heute greift die Grenze nie — 6 × 130 sind 780 — und genau deshalb braucht sie einen Test, sonst fällt ihr Fehlen erst auf, wenn jemand eine Zone hochstellt
- [ ] T041 [P] [US2] Test `SpawnSpreadOverTicksTest` in `rpg-core/src/test/java/rpg/core/mob/SpawnSpreadOverTicksTest.java` — ein Durchlauf, der viele Kreaturen zu setzen hätte, verteilt sie (FR-014)
- [ ] T041a [P] [US2] Test `SupplyIsContinuousTest` in `rpg-core/src/test/java/rpg/core/mob/SupplyIsContinuousTest.java` — nach einem Kill kommt Ersatz nach der Nachschubfrist, **ohne dass die Zone erst geräumt sein muss**, und es gibt keinen Zonenzustand, den ein Test setzen könnte (FR-018a). Die verworfene Alternative — abgegrenzte Wellen — hätte genau diesen Zustand gebraucht

### Umsetzung

- [ ] T042 [US2] `SpawnPlanner` in `rpg-core/src/main/java/rpg/core/mob/SpawnPlanner.java` — entscheidet **was wo**: Budget prüfen, Bereich wählen, Art nach Gewicht würfeln; ohne Bukkit und ohne Zuweisung im Pfad
- [ ] T043 [US2] Obergrenze je Durchlauf in `SpawnPlanner` — die Arbeit wird über Ticks verteilt, nicht gebündelt (FR-014)
- [ ] T044 [US2] `HordeSweep` in `rpg-platform/src/main/java/rpg/platform/mob/HordeSweep.java` — der **selbst neu eingeplante Einmal-Durchlauf je bevölkerter Zone**; Javadoc begründet, warum das keine wiederkehrende Aufgabe je Spieler oder Entität ist (Prinzip II, research.md R4)
- [ ] T044a [US2] `HordeSweep` fängt Fehler **je Zone** in `rpg-platform/src/main/java/rpg/platform/mob/HordeSweep.java` — eine Zone, deren Konfiguration oder Welt Ärger macht, darf die anderen fünf nicht mitreißen und den Durchlauf nicht beenden: der plant sich sonst nie wieder ein und die Horde bleibt für immer stehen (FR-044, Prinzip VI). Geloggt mit Zonenschlüssel, **einmal je Vorfall und nicht je Durchlauf** — bei einem Durchlauf alle zwei Sekunden wären das sonst 1.800 Zeilen die Stunde
- [ ] T044b [US2] Test `SweepSurvivesABrokenZoneTest` in `rpg-platform/src/test/java/rpg/platform/mob/SweepSurvivesABrokenZoneTest.java` — eine Zone wirft, die anderen laufen trotzdem, und der nächste Durchlauf ist eingeplant
- [ ] T045 [US2] Der Durchlauf läuft nicht in einer Zone ohne Spieler, in `rpg-platform/src/main/java/rpg/platform/mob/HordeSweep.java` (FR-015) — geprüft **bevor** irgendetwas gerechnet wird
- [ ] T046 [US2] `PaperMobPlacer` an den Durchlauf hängen — Setzen ortsgebunden über B01s Scheduler, nie über den globalen (FR-042, Prinzip I)
- [ ] T047 [US2] Bestand nachführen beim Setzen in `rpg-platform/src/main/java/rpg/platform/mob/HordeSweep.java`: Zone, Chunk, gesamt (FR-013, FR-017)
- [ ] T048 [US2] `EntityRemoveEvent`-Zuhörer in `rpg-platform/src/main/java/rpg/platform/mob/HordeSweep.java` — hält den Bestand ehrlich, wenn eine Kreatur auf einem fremden Weg verschwindet (Tod, Betreiber, Weltentladung)
- [ ] T049 [P] [US2] Horden für alle sechs Regionen in `rpg-plugin/src/main/resources/mobs.yml` — 48 Arten, Bereichszuordnung, Gewichte (SC-002)
- [ ] T050 [US2] Test `HordeSweepTest` in `rpg-platform/src/test/java/rpg/platform/mob/HordeSweepTest.java` — mit MockBukkit: der Durchlauf plant sich neu, und beim Abschalten des Plugins hört er auf

**Checkpoint**: US1 und US2 stehen. Horden entstehen und das Budget hält.

---

## Phase 5: User Story 2b — Die Welt spawnt nichts mehr von selbst (P2)

**Goal**: Das natürliche Spawning ist aus — überall. Absichtliches Setzen bleibt möglich.

**Independent Test**: Eine Nacht abwarten und in eine Höhle sehen; danach ein Spawn-Ei benutzen.

### Tests zuerst

- [ ] T051 [P] [US2B] Test `SpawnReasonPolicyTest` in `rpg-core/src/test/java/rpg/core/mob/SpawnReasonPolicyTest.java` — die Entscheidung, **welcher Grund durchgelassen wird**, als reine Regel ohne Bukkit: erlaubt sind `CUSTOM`, `COMMAND`, `SPAWNER_EGG`, `DISPENSE_EGG`; alles andere nicht (FR-018d)
- [ ] T052 [P] [US2B] Test `VanillaSpawnSuppressorTest` in `rpg-platform/src/test/java/rpg/platform/mob/VanillaSpawnSuppressorTest.java` — mit MockBukkit: ein `NATURAL`-Ereignis wird abgebrochen, ein `SPAWNER_EGG` nicht, und **unsere eigenen werden nicht mitunterdrückt** (FR-018e)

### Umsetzung

- [ ] T053 [US2B] `SpawnReasonPolicy` in `rpg-core/src/main/java/rpg/core/mob/SpawnReasonPolicy.java` — die Liste der erlaubten Gründe als Regel, testbar ohne Server; sie nennt Bukkit-Konstanten als Strings, nicht als Typen
- [ ] T054 [US2B] `VanillaSpawnSuppressor` in `rpg-platform/src/main/java/rpg/platform/mob/VanillaSpawnSuppressor.java` — **Schicht 1**: die sieben Spielregeln je Welt (`SPAWN_MOBS`, `SPAWN_MONSTERS`, `SPAWN_PATROLS`, `SPAWN_PHANTOMS`, `SPAWN_WANDERING_TRADERS`, `SPAWN_WARDENS`, `SPAWNER_BLOCKS_WORK`) auf `false`
- [ ] T055 [US2B] Dieselben Regeln auf `WorldLoadEvent` — nach dem Muster von `VanillaRegenerationGuard`, damit eine später geladene Welt nicht ausgenommen ist
- [ ] T056 [US2B] **Schicht 2**: `CreatureSpawnEvent`-Riegel auf `HIGHEST` in `VanillaSpawnSuppressor` — bricht jeden nicht erlaubten Grund ab; Javadoc begründet, warum es **beide** Schichten braucht (research.md R1)
- [ ] T057 [US2B] Test `NoSuppressionSwitchTest` in `rpg-platform/src/test/java/rpg/platform/mob/NoSuppressionSwitchTest.java` — **es gibt keinen Schalter**, mit dem die Unterdrückung abgeschaltet werden kann (FR-018c, entschieden am 2026-08-24). Ein Schalter wäre ein Weg, das Budget zu umgehen: einmal auf `false` gestellt und vergessen, läuft der Server voll und niemand sieht die Ursache. Der Test hält die Entscheidung fest, damit sie nicht aus Bequemlichkeit zurückkommt
- [ ] T058 [US2B] Log-Zeile beim Start in `rpg-platform/src/main/java/rpg/platform/mob/VanillaSpawnSuppressor.java`: welche Regeln auf wie vielen Welten gesetzt wurden — damit auf dem echten Server nachvollziehbar ist, ob Schicht 1 überhaupt griff

**Checkpoint**: Das Budget ist jetzt die einzige Quelle lebender Kreaturen.

---

## Phase 6: User Story 3 — Was niemand sieht, ist weg (P3)

**Goal**: Kreaturen verlassener Zonen und weit entfernte Kreaturen werden **entfernt**, nicht
schlafen gelegt. Ohne Erfahrung, ohne Coins, ohne Tod.

**Independent Test**: Zone verlassen, warten, zählen. Danach muss null stehen.

### Tests zuerst

- [ ] T059 [P] [US3] Test `CleanupRuleTest` in `rpg-core/src/test/java/rpg/core/mob/CleanupRuleTest.java` — wer weg soll: Zone ohne Spieler nach Ablauf der Frist, und wer außerhalb der Reichweite steht (FR-019, FR-020)
- [ ] T060 [P] [US3] Test `CleanupSparesCombatTest` in `rpg-core/src/test/java/rpg/core/mob/CleanupSparesCombatTest.java` — eine Kreatur im Kampf bleibt (FR-022)
- [ ] T061 [P] [US3] Test `RemovalIsNotADeathTest` in `rpg-platform/src/test/java/rpg/platform/mob/RemovalIsNotADeathTest.java` — Entfernen löst weder Erfahrung noch Coins noch ein Todesereignis aus (FR-021); **das ist die Stelle, an der dieser Block etwas verschenken könnte**

### Umsetzung

- [ ] T062 [US3] `CleanupRule` in `rpg-core/src/main/java/rpg/core/mob/CleanupRule.java` — die Entscheidung, ohne Bukkit. Bekommt die **fertige Chunk-Menge aus `NearbyChunks`** und nicht die Spielerliste: was man nicht in der Hand hat, kann man nicht in einer Schleife durchgehen (FR-018, research.md R3a). Frist und Reichweite aus der Konfiguration
- [ ] T063 [US3] Kampfzustand über B05 abfragen statt selbst zu führen, in `rpg-core/src/main/java/rpg/core/mob/CleanupRule.java` (FR-022) — B05 rechnet ihn lazy aus Zeitstempeln, eine zweite Buchführung wäre eine zweite Wahrheit
- [ ] T064 [US3] Aufräumen in `rpg-platform/src/main/java/rpg/platform/mob/HordeSweep.java` einhängen — **derselbe Durchlauf** wie das Nachsetzen, nicht ein zweiter (research.md R4)
- [ ] T065 [US3] Entfernen entitätsgebunden über B01s Scheduler in `rpg-platform/src/main/java/rpg/platform/mob/HordeSweep.java`, und den Bestand dabei nachführen
- [ ] T066 [US3] Alle eigenen Kreaturen bei `onDisable` entfernen (FR-023) — sonst stünden sie beim nächsten Start als herrenloser Bestand da
- [ ] T067 [US3] Test `ShutdownLeavesNothingTest` in `rpg-platform/src/test/java/rpg/platform/mob/ShutdownLeavesNothingTest.java` — nach dem Abschalten hält der Bestand nichts mehr
- [ ] T067a [US3] Test `OneSweepPerZoneTest` in `rpg-platform/src/test/java/rpg/platform/mob/OneSweepPerZoneTest.java` — bei 130 Kreaturen in drei bevölkerten Zonen sind **drei** Aufgaben eingeplant, nicht 133; und in einer leeren Zone keine (FR-024, FR-015, Prinzip II). Nach dem Muster von B08s SC-005 — die Zusage „keine wiederkehrende Aufgabe je Entität" ist nur so viel wert, wie sie gezählt wird

**Checkpoint**: Der Server bleibt über Stunden stabil, statt vollzulaufen.

---

## Phase 7: User Story 4 — Mehr Spieler, dichtere Horde (P4)

**Goal**: Dichte und Nachschub folgen der Spielerzahl. Die Werte einer Kreatur nicht.

**Independent Test**: Dieselbe Region mit einem und mit fünf Spielern; Zahl steigt, Werte identisch.

### Tests zuerst

- [ ] T068 [P] [US4] Test `DensityScalingTest` in `rpg-core/src/test/java/rpg/core/mob/DensityScalingTest.java` — mehr Spieler, höhere Zieldichte und schnellerer Nachschub (FR-025)
- [ ] T069 [P] [US4] Test `ScalingNeverTouchesAttributesTest` in `rpg-core/src/test/java/rpg/core/mob/ScalingNeverTouchesAttributesTest.java` — **FR-026**: dieselbe Art hat bei einem und bei zwanzig Spielern exakt dieselben Werte
- [ ] T070 [P] [US4] Test `ScalingStopsAtTheBudgetTest` in `rpg-core/src/test/java/rpg/core/mob/ScalingStopsAtTheBudgetTest.java` — die Zieldichte wird am Budget gekappt, nicht umgekehrt (FR-027)

### Umsetzung

- [ ] T071 [US4] `DensityScaling` in `rpg-core/src/main/java/rpg/core/mob/DensityScaling.java` — `min(zieldichte, budget)`; Javadoc trennt die beiden Rollen ausdrücklich (data-model.md)
- [ ] T072 [US4] Nachschubrate ebenfalls skalieren, in `rpg-core/src/main/java/rpg/core/mob/DensityScaling.java`, aus `horde.respawn-interval-ms` und `density-per-player`
- [ ] T073 [US4] Spielerzahl je Zone über B09s Anwesenheit ermitteln, in `rpg-platform/src/main/java/rpg/platform/mob/HordeSweep.java` — nicht über eine eigene Zählung
- [ ] T074 [US4] Sinkende Spielerzahl in `rpg-core/src/main/java/rpg/core/mob/DensityScaling.java`: Zieldichte fällt, der Überhang wird über das Aufräumen abgebaut und **nicht sofort gelöscht** (FR-028)

**Checkpoint**: Eine Region fühlt sich zu zweit richtig an und zu zwanzig auch.

---

## Phase 8: User Story 5 — Der Boss der Region (P5)

**Goal**: Ein Boss je Region, höhere Attribute, Respawn-Timer. Keine Fähigkeiten, keine Phasen.

**Independent Test**: In jeder Region genau einer; nach dem Tod erst nach Ablauf des Timers wieder.

### Tests zuerst

- [ ] T075 [P] [US5] Test `OneBossPerRegionTest` in `rpg-core/src/test/java/rpg/core/mob/OneBossPerRegionTest.java` — höchstens einer lebt (FR-029)
- [ ] T076 [P] [US5] Test `BossRespawnTimerTest` in `rpg-core/src/test/java/rpg/core/mob/BossRespawnTimerTest.java` — vor Ablauf kein zweiter; der Timer wird **aus zwei Zeitstempeln gerechnet und ist keine laufende Aufgabe** (FR-031, FR-032)
- [ ] T077 [P] [US5] Test `CleanupDoesNotResetTheBossTimerTest` in `rpg-core/src/test/java/rpg/core/mob/CleanupDoesNotResetTheBossTimerTest.java` — aufgeräumt ist nicht gefallen (FR-034)
- [ ] T078 [P] [US5] Test `BossCountsAgainstTheBudgetTest` in `rpg-core/src/test/java/rpg/core/mob/BossCountsAgainstTheBudgetTest.java` — FR-033
- [ ] T078a [P] [US5] Test `BossHasNoAbilitiesTest` in `rpg-core/src/test/java/rpg/core/mob/BossHasNoAbilitiesTest.java` — keine Bossart trägt einen Fähigkeitsverweis (FR-034a). Der Wächter für eine Abgrenzung, die sonst still verfällt: der eigentliche Bosskampf kommt später als Dungeon-Boss und braucht Instanzen

### Umsetzung

- [ ] T079 [US5] `BossState` in `rpg-core/src/main/java/rpg/core/mob/BossState.java` — je Zone, mit `aliveEntityId` und `lastKilledAt`; `null` heißt „noch nie gefallen, darf sofort"
- [ ] T080 [US5] Bossplatzierung über den `SpawnArea`-Schlüssel plus Versatz in `rpg-core/src/main/java/rpg/core/mob/BossSpec.java` (FR-030, research.md R10) — **keine eigenen Weltkoordinaten**
- [ ] T081 [US5] Boss in `rpg-platform/src/main/java/rpg/platform/mob/HordeSweep.java` einhängen: erscheint, wenn keiner lebt, der Timer abgelaufen ist und Spieler da sind
- [ ] T082 [US5] Bosstod erkennen und `lastKilledAt` setzen — über dasselbe Todesereignis wie alles andere, keine Sonderbehandlung
- [ ] T083 [US5] Aufräumen behandelt den Boss wie jede Kreatur, ohne den Timer anzufassen, in `rpg-core/src/main/java/rpg/core/mob/CleanupRule.java` (FR-034)
- [ ] T084 [P] [US5] Sechs Bosse in `rpg-plugin/src/main/resources/mobs.yml` — je Region einer, mit Attributen deutlich über den acht gewöhnlichen Arten
- [ ] T085 [P] [US5] Boss-Anzeigenamen in `rpg-plugin/src/main/resources/messages.yml`

**Checkpoint**: Jede Region hat ihr Ziel.

---

## Phase 9: User Story 6 — Die Horde denkt nicht mehr, als sie darf (P6)

**Goal**: Zielsuche gedrosselt und in der Reichweite begrenzt. Und eine Messung, an der sich die
Entscheidung später überprüfen lässt.

**Independent Test**: Die Messung laufen lassen; über mehrere Läufe vergleichbare Zahlen.

### Tests zuerst

- [ ] T086 [P] [US6] Test `RetargetThrottleTest` in `rpg-core/src/test/java/rpg/core/mob/RetargetThrottleTest.java` — eine eigene Zielzuweisung fasst frühestens nach dem konfigurierten Abstand wieder an (FR-035)
- [ ] T087 [P] [US6] Test `NoTargetNoSearchTest` in `rpg-core/src/test/java/rpg/core/mob/NoTargetNoSearchTest.java` — ohne Spieler in Reichweite passiert nichts (FR-037)
- [ ] T088 [US6] Test `HordeBudgetBenchmarkTest` in `rpg-core/src/test/java/rpg/core/mob/HordeBudgetBenchmarkTest.java` — **FR-038, SC-007**: ein Spawn- und ein Aufräum-Durchlauf bei 130 Kreaturen, zusammen unter 1 ms; der Aufräumteil **mit der Chunk-Menge aus `NearbyChunks`**, sonst misst er eine Variante, die niemand baut. Kopfkommentar sagt ausdrücklich, dass dies **kein Lasttest** ist und der Nachweis unter Volllast zu B15 gehört (ADR-031). Vorbild: `ZoneLookupBenchmarkTest`

### Umsetzung

- [ ] T089 [US6] `follow-range` je Art über `Attribute.FOLLOW_RANGE` setzen, in `PaperMobPlacer` (FR-036, research.md R6)
- [ ] T090 [US6] `RetargetThrottle` in `rpg-core/src/main/java/rpg/core/mob/RetargetThrottle.java` — die Drosselung der eigenen Zielzuweisung als Regel, zeitstempelbasiert lazy und ohne laufende Aufgabe (FR-035, Prinzip II)
- [ ] T091 [US6] Javadoc in `rpg/platform/mob/package-info.java`: **warum Vanillas Pfadfindung stehen bleibt** — die vereinfachte AI ist eine Wette, die erst eine Messung rechtfertigt, und die gehört zu B15 (research.md R6)
- [ ] T092 [US6] Die gemessenen Zahlen in `specs/010-mobs-spawning/quickstart.md` Abschnitt 1 eintragen, damit ein späterer Vergleich einen Ausgangswert hat

**Checkpoint**: Die Kosten sind begrenzt und die Grenze ist belegt.

---

## Phase 10: User Story 7 — Der Klon zieht die Mobs auf sich (P7)

**Goal**: Die letzte offene Zusage aus B08 einlösen.

**Independent Test**: Klon in eine Gruppe setzen; sie schwenken um. Klon endet; sie schwenken zurück.

### Tests zuerst

- [ ] T093 [P] [US7] Test `CloneAggroTest` in `rpg-platform/src/test/java/rpg/platform/mob/CloneAggroTest.java` — bei stehendem Klon wird das Ziel umgelenkt (FR-039), nach seinem Ende nicht mehr (FR-040)
- [ ] T094 [P] [US7] Test `CloneAggroIsThrottledTest` in `rpg-platform/src/test/java/rpg/platform/mob/CloneAggroIsThrottledTest.java` — das Umlenken unterliegt derselben Drosselung (FR-041)

### Umsetzung

- [ ] T095 [US7] `CloneAggroListener` in `rpg-platform/src/main/java/rpg/platform/mob/CloneAggroListener.java` — `EntityTargetLivingEntityEvent` umlenken statt `setTarget` gegen Vanilla zu setzen; Javadoc nennt die Parallele zur Blockhaltung des Warriors: gegen Vanilla anzuschreiben verliert, am Ereignis anzusetzen gewinnt (research.md R9)
- [ ] T096 [US7] Erst fragen, ob überhaupt ein Klon steht, in `rpg-platform/src/main/java/rpg/platform/mob/CloneAggroListener.java` — eine Karte, die fast immer leer ist, und deshalb kostet der Zuhörer für alle anderen nichts
- [ ] T097 [US7] Die Zeile in der Roadmap schließen: `06-open-questions.md` und `blocks/B08-ability-framework.md` — „Clone zieht Mobs auf sich" ist nicht mehr blockiert (SC-009)

**Checkpoint**: Alle acht Geschichten stehen.

---

## Phase 11: Polish, Verdrahtung und Abschluss

**Purpose**: das, woran ein Block sonst scheitert, nachdem alle Module grün sind

- [ ] T098 `MobModule` in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` registrieren — Startreihenfolge nach B09, weil die Bereiche vorher stehen müssen
- [ ] T099 `VanillaSpawnSuppressor`, `HordeSweep`, `CloneAggroListener` und den `EntityRemoveEvent`-Zuhörer im Plugin registrieren
- [ ] T100 Die drei Anbieter im Plugin tauschen — `MobStatProvider`, `MobXpProvider`, `MobCoinProvider`; **die Übergangsanbieter aus B05/B06/B08b entfernen**, nicht danebenstellen
- [ ] T101 `MobMessageKeys.all()` in die Startprüfung der Message-Schlüssel aufnehmen, in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java`
- [ ] T102 `FullBootstrapTest` in `rpg-plugin/src/test/java/rpg/plugin/FullBootstrapTest.java` erweitern — das Modul ist verdrahtet, die Zuhörer sind registriert, die drei Anbieter kommen aus B10; **ein Modul, das nur Modultests bestanden hat, ist nicht fertig**
- [ ] T103 Test `NoCompetingMobProviderTest` in `rpg-plugin/src/test/java/rpg/plugin/NoCompetingMobProviderTest.java` — es gibt **je Schnittstelle genau einen** Anbieter; zwei wären der Fehler, den ADR-005ff. an anderer Stelle schon einmal gekostet hat
- [ ] T104 Nachladen prüfen: `mobs.yml` neu laden ändert Budgets und Raten, lässt laufende Kreaturen aber unberührt (contracts/mob-config.md)
- [ ] T105 [P] Startwerte aus [research.md](./research.md) R7 in `mobs.yml` eintragen und je Wert einen Satz Begründung als Kommentar
- [ ] T106 [P] `06-open-questions.md`: den offenen Punkt „Zielwert für gleichzeitig aktive Mobs" mit den tatsächlich gewählten Startwerten schließen
- [ ] T107 [P] `blocks/B10-mobs-spawning.md`: Status von *Entwurf* auf umgesetzt, und die Akzeptanzkriterien gegen die Erfolgskriterien der Spec abgleichen
- [ ] T108 [P] `02-decisions.md`: ADR für die zwei Entscheidungen, die von einer Vorgabe abweichen oder sie präzisieren — die zweischichtige Vanilla-Unterdrückung (R1) und `FOLLOW_RANGE` statt eigener AI (R6)
- [ ] T109 [P] `01-architecture.md`: B10 als umgesetzt markieren
- [ ] T110 `./gradlew spotlessApply` und der volle Testlauf — grün **und 0 übersprungen**; die Zahl wird geprüft, nicht angenommen
- [ ] T111 [quickstart.md](./quickstart.md) **Abschnitt 1 und 2** durchlaufen — ohne Server
- [ ] T112 [quickstart.md](./quickstart.md) **Abschnitt 3** auf einem echten Paper-Server — die **34 Prüfschritte**, besonders 7 (die Wege, die keine Spielregel abdeckt), 12 (das Budget bei zwanzig Spielern), 16 (die Kreatur im Kampf verschwindet nicht) und 19 (zwei Arten auf derselben Basis, unterschiedliche Beträge). Grüne Tests beweisen nichts über Papers Spawner und nichts über die Ladeordnung; nur der echte Start tut das

---

## Dependencies & Execution Order

### Phasenabhängigkeiten

- **Setup (Phase 1)**: keine Abhängigkeit
- **Foundational (Phase 2)**: nach Setup — **blockiert alle Geschichten**
- **US1 (Phase 3)**: nach Phase 2. Danach ist der Block für einen Betreiber schon nützlich
- **US2 (Phase 4)**: nach US1 — es braucht Arten, um Horden zu setzen
- **US2b (Phase 5)**: nach Phase 2, **unabhängig von US2**. Kann parallel zu US2 laufen; erst beide zusammen machen das Budget zur einzigen Quelle
- **US3 (Phase 6)**: nach US2 — es braucht einen Bestand, um ihn aufzuräumen
- **US4 (Phase 7)**: nach US2 und US3 — Skalierung ohne Aufräumen ließe den Überhang stehen
- **US5 (Phase 8)**: nach US2
- **US6 (Phase 9)**: nach US2; die Messung braucht Planer und Aufräumregel, also faktisch auch US3
- **US7 (Phase 10)**: nach US6, weil die Drosselung mitgilt
- **Polish (Phase 11)**: nach allen gewünschten Geschichten

### Innerhalb einer Geschichte

- Tests zuerst, und sie müssen fallen, bevor umgesetzt wird
- `rpg-core` vor `rpg-platform` — die Regel vor ihrer Anwendung
- Konfigurationsinhalte parallel zum Code, aber die Schemaprüfung zuerst

### Parallele Gelegenheiten

- T001–T003, T006–T009 laufen parallel — verschiedene Dateien
- Alle Testaufgaben einer Geschichte mit `[P]` laufen parallel
- **US2 und US2b sind der größte Hebel**: zwei Personen können sie gleichzeitig bauen, sie berühren sich nur im Plugin
- T105–T109 sind reine Dokumentation und laufen parallel zu allem

---

## Implementation Strategy

### MVP zuerst (nur US1)

1. Phase 1 und 2 abschließen
2. Phase 3 abschließen
3. **Anhalten und prüfen**: zwei Arten auf `ZOMBIE`, unterschiedliche Werte, unterschiedliche
   Beträge beim Tod. Damit ist die älteste offene Zusage dieses Projekts eingelöst — und die
   Verwechslung, die `/specify` gefunden hat, behoben

### Schrittweise Auslieferung

1. Setup + Foundational → Fundament
2. US1 → prüfen → **MVP**
3. US2 + US2b (parallel) → prüfen → das Budget ist die einzige Quelle
4. US3 → prüfen → der Server bleibt über Stunden stabil
5. US4, US5, US6, US7 → je einzeln prüfen

---

## Notes

- `[P]` heißt: andere Datei, keine offene Abhängigkeit
- Nach jeder Aufgabe oder Gruppe committen
- **Ein Modul, dessen Modultests grün sind, ist nicht fertig.** Fertig ist es, wenn es im Plugin
  verdrahtet ist und `FullBootstrapTest` grün bleibt (T102)
- **Übersprungene Tests prüfen.** MockBukkit meldet Nicht-Implementiertes als *übersprungen* und
  nicht als Fehler; ein grüner Lauf, der nichts angesehen hat, ist der schlechteste Ausgang (T110)
- **Und T112 nicht wegdrücken.** Zwei Fehler dieses Projekts sind erst auf dem echten Server
  aufgefallen, beide bei vollständig grünen Tests: die Blockhaltung des Warriors und die Ladeordnung
  des Plugins. Abschnitt 3 des Quickstarts existiert genau dafür
