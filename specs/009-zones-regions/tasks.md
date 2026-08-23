---

description: "Aufgabenliste B09 · Zonen & Regionen"
---

# Tasks: B09 · Zonen & Regionen

**Input**: Entwurfsunterlagen aus `/specs/009-zones-regions/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: **Pflicht, nicht optional.** Prinzip VII der Constitution verlangt für jede Formel und
jede Regel der Domänenschicht Unit-Tests ohne laufenden Server, und für Persistenz Testcontainers
gegen eine echte PostgreSQL-Instanz. Testaufgaben stehen deshalb je Geschichte **vor** der Umsetzung.

**Organization**: gruppiert nach den sieben User Stories der Spec, in Abhängigkeitsreihenfolge.

## Format: `[ID] [P?] [Story] Beschreibung`

- **[P]**: parallelisierbar — andere Datei, keine offene Abhängigkeit
- **[Story]**: US1 bis US7; Setup, Foundational und Polish tragen keine
- Jede Aufgabe nennt ihren Pfad und ihren Bezug (FR, SC oder Artefakt)

## Pfade

- `rpg-core/src/main/java/rpg/core/zone/` — Geometrie und Regeln, **ohne Bukkit**
- `rpg-platform/src/main/java/rpg/platform/zone/` — hier und nur hier wird Paper angefasst
- `rpg-persistence/src/main/java/rpg/persistence/zone/` — Freischaltungen und Merker
- `rpg-plugin/src/main/resources/` — `zones.yml`, `messages.yml`
- Tests jeweils unter `src/test/java/` desselben Moduls

---

## Phase 1: Setup — Pakete und Gerüste

**Purpose**: die Blockgrenzen benennen, bevor etwas darin entsteht

- [X] T001 [P] Paket `rpg-core/src/main/java/rpg/core/zone/` mit `package-info.java` anlegen — die Blockgrenze benennen: Geometrie, Index und Regeln gehören hierher, **Paper nirgends**, und Schwierigkeitsmodifikator wie Loot-Zuordnung ausdrücklich **nicht** (FR-056, FR-057), nach dem Muster von `rpg/core/currency/package-info.java`
- [X] T002 [P] Paket `rpg-platform/src/main/java/rpg/platform/zone/` mit `package-info.java` anlegen — Kopfkommentar: hier und nur hier wird Paper angefasst
- [X] T003 [P] Paket `rpg-persistence/src/main/java/rpg/persistence/zone/` mit `package-info.java` anlegen
- [X] T004 [P] `rpg-plugin/src/main/resources/zones.yml` als Gerüst anlegen nach [contracts/zone-config.md](./contracts/zone-config.md) — mit `provisional: true`, `fallback-point` und leerem `zones`-Baum, Kopfkommentar mit ADR-006 („eine Zone ist niemals eine World") und ADR-027 („prices live here, there is no central catalogue")
- [X] T005 [P] `rpg-plugin/src/main/resources/messages.yml` um den `zone.*`-Baum erweitern — alle Schlüssel aus [contracts/zone-config.md](./contracts/zone-config.md), zunächst mit den sechs Zonennamen und den acht Meldungen (FR-025, FR-035, FR-051e, FR-061)

---

## Phase 2: Foundational — Geometrie, Index, Konfiguration

**Purpose**: die Grundlage, auf der **alle** sieben Geschichten aufsetzen

**⚠️ CRITICAL**: keine Geschichte kann vor Abschluss dieser Phase beginnen

### Werte und Geometrie

- [X] T006 [P] `Cuboid` in `rpg-core/src/main/java/rpg/core/zone/Cuboid.java` — zwei Ecken, normalisiert, `minY`/`maxY` optional; `contains(x,y,z)` als sechs Ganzzahlvergleiche ohne Allokation (FR-004, data-model.md §1)
- [X] T007 [P] `LevelBand` in `rpg-core/.../zone/LevelBand.java` — `min` und `max`, beide **einschliessend** (FR-003)
- [X] T008 `Area` in `rpg-core/.../zone/Area.java` — Liste von Quadern, mindestens einer, plus `touchedChunks()` **nur für den Ladepfad** (FR-004). Hängt an T006
- [X] T009 [P] `CuboidTest` in `rpg-core/src/test/java/rpg/core/zone/` — Normalisierung, Y offen gegen Y begrenzt, Grenzen einschliessend, ein Punkt über einem Y-begrenzten Quader liegt **draussen** (FR-004, Randfall „Y-Achse")
- [X] T010 [P] `AreaTest` in `rpg-core/src/test/java/rpg/core/zone/` — mehrere Teile, `touchedChunks()` deckt alle berührten Chunks ab, auch bei negativen Koordinaten
- [X] T011 [P] `SafeCore` in `rpg-core/.../zone/SafeCore.java` — Bereich plus **der eine** `respawnPoint` als `WorldPosition` aus `rpg.core.scheduler` (**nicht** neu erfinden, research.md R2; FR-032, FR-037d)
- [X] T012 [P] `SpawnArea` in `rpg-core/.../zone/SpawnArea.java` — Kennung und Geometrie, **und nichts sonst**; keine Rolle, keine Art, keine Zahl (FR-053a)
- [X] T013 [P] `WaypointCrystal` in `rpg-core/.../zone/WaypointCrystal.java` — Kennung, Auslösebereich, Preis; **kein Ziel** (FR-045, FR-045a)
- [X] T014 `Zone` in `rpg-core/.../zone/Zone.java` — Kennung, `worldId`, Area, Levelband, optionaler Schutzkern, Spawn-Bereiche, optionaler Kristall, `pvp`, `startRegion`. **Kein `displayName`** (FR-003a). Hängt an T006–T013

### Der Index

- [X] T015 `ChunkZoneIndex` in `rpg-core/.../zone/ChunkZoneIndex.java` — gepackter `long`-Schlüssel `(chunkX << 32) | (chunkZ & 0xFFFFFFFFL)`, Kandidatenfelder, **Grenzchunk-Marker**; beim Laden gebaut, zur Laufzeit nur gelesen. Kein Boxing (FR-005, FR-006, Prinzip II, research.md R3)
- [X] T016 `ChunkZoneIndexTest` in `rpg-core/src/test/java/rpg/core/zone/` — ein Kandidat braucht **keinen** Quadertest; zwei Kandidaten im selben Chunk werden exakt aufgelöst; ein von Zone und Schutzkern berührter Chunk ist als **Grenzchunk** markiert; ein Chunk ohne Zone liefert leer (FR-007)
- [X] T017 `ZoneIndexNoIterationTest` in `rpg-core/src/test/java/rpg/core/zone/` — ein Quelltest, der belegt, dass keine Zonenabfrage über alle Zonen iteriert; nach dem Muster von `SinglePermissionPointTest` (FR-005, Prinzip II)

### Konfiguration

- [X] T018 `ZoneConfig` in `rpg-core/.../zone/ZoneConfig.java` — der ausgelesene Stand: Zonen, Ausweichpunkt, `provisional`-Kennzeichen
- [X] T019 `ZoneConfigSchema` in `rpg-core/.../zone/ZoneConfigSchema.java` — Auslesen und Prüfen nach [contracts/zone-config.md](./contracts/zone-config.md), Fail-Fast mit einer Meldung, die die verantwortliche Stelle benennt (FR-013, Prinzip V)
- [X] T020 `ZoneConfigSchemaTest` in `rpg-core/src/test/java/rpg/core/zone/` — **je Startverweigerung ein Fall**, alle elf aus [data-model.md §5](./data-model.md): **unbekannter Weltname (FR-002a)**, überlappende Zonen (FR-012), doppelte Zonenkennung (FR-003), Schutzkern ausserhalb seiner Zone (FR-009), Spawn-Bereich ausserhalb der Zone oder im Kern (FR-055), doppelte Spawn-Kennung, Kristall ohne Schutzkern (FR-051c), Kristall-Auslösebereich ausserhalb seiner Zone (FR-051d), doppelte Kristallkennung, fehlender Message-Schlüssel (FR-003c), keine oder zwei Startregionen (FR-037a)
- [X] T021 `ZoneMessageKeys` in `rpg-core/.../zone/ZoneMessageKeys.java` — alle Schlüssel dieses Blocks als Konstanten; der Zonenname wird als `zone.<key>.name` gebildet (FR-003a)
- [X] T022 **Verschoben nach `rpg-plugin/src/test/java/rpg/plugin/ZoneMessageKeyResolutionTest.java`** (die ausgelieferten `zones.yml` und `messages.yml` liegen in `rpg-plugin`, und die Abhängigkeitsrichtung `plugin → core` lässt einen Kerntest nicht an sie heran; Namenskonvention wie `AbilityMessageKeyResolutionTest`). Zusätzlich prüft `ProvisionalWarningTest` in `rpg-core` die Verweigerung selbst — zu jeder konfigurierten Zone existiert ein Message-Schlüssel, und **kein** Anzeigename steht in `zones.yml` (FR-003a, FR-003c)

### Abfrage und Modul

- [X] T023 `Zones` in `rpg-core/.../zone/Zones.java` — die Schnittstelle aus [contracts/zone-api.md](./contracts/zone-api.md): `zoneAt`, `zoneKeyAt`, `inSafeCore`, `byKey`, `all`, `startPoint`, `respawnPointOf`, `fallbackPoint`, `spawnAreasOf`. Javadoc mit der Zusage „ab jetzt ADR-pflichtig"
- [X] T024 `DefaultZones` in `rpg-core/.../zone/DefaultZones.java` — Umsetzung über den Index. Hängt an T015, T023
- [X] T025 `DefaultZonesTest` in `rpg-core/src/test/java/rpg/core/zone/` — `zoneAt` gibt für eine Position im Schutzkern die **Region** zurück, nie den Kern (FR-011); `inSafeCore` beantwortet den Kern getrennt (FR-010); Position ohne Zone liefert leer (FR-007); `zoneKeyAt` allokiert nicht (Prinzip II)
- [X] T026 `ZoneModule` in `rpg-core/.../zone/ZoneModule.java` — `start()` und `applyReloadedConfig()` nach dem Muster von `StatsModule` und `CombatModule` (research.md R6)
- [X] T027 Die **Startwarnung** über die vorläufigen Koordinaten in `ZoneModule.start()` — **nicht** in `applyReloadedConfig()`, damit sie bei `/rpg reload` nicht erneut läuft; Protokollform `[config] phase=START ...` (FR-065a, FR-065b, research.md R7)
- [X] T028 `ProvisionalWarningTest` in `rpg-core/src/test/java/rpg/core/zone/` — bei `provisional: true` warnt der Start **einmal**, ein Neuladen **nicht**; ohne das Kennzeichen ändert sich nichts ausser der Warnung (FR-065b, FR-065c)
- [X] T029 Die **sechs Regionen** in `zones.yml` ausformulieren — Kennungen `greenfields`, `dustlands`, `safari-plains`, `terracotta-canyons`, `darkforest`, `pale-wilds`, Levelbänder 1–10 bis 51–60 lückenlos, `greenfields` als `start-region`, alle `pvp: false`, je ein Schutzkern mit Respawn-Punkt, je ein Kristall, je mehrere Spawn-Bereiche. Koordinaten **vorläufig** auf einer Testwelt (FR-065, FR-055a)
- [X] T030 **Verschoben nach `rpg-plugin/src/test/java/rpg/plugin/ShippedZoneConfigTest.java`** (dieselbe Begründung wie T022; Namenskonvention wie `ShippedProgressionConfigTest`) — die Levelbänder der ausgelieferten Zonen decken **1 bis zur Maximalstufe aus `progression.yml` lückenlos und ohne Überlappung** ab (FR-065d). Bricht absichtlich, wenn jemand die XP-Kurve verlängert, ohne eine Region zu ergänzen
- [X] T031 [P] `BukkitPositions` in `rpg-platform/src/main/java/rpg/platform/zone/BukkitPositions.java` — `org.bukkit.Location` → `WorldPosition` und zurück. Die **einzige** Stelle, an der ein Paper-Ort in einen Kernwert übersetzt wird (Prinzip III, research.md R2)
- [X] T032 `ZoneModule` im Plugin verdrahten — `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java`: Konfiguration registrieren, Modul starten, und `zoneModule.applyReloadedConfig()` in `reloadConfiguration()` **neben** dem vorhandenen `statsModule.applyReloadedConfig()` (ADR-012, research.md R6)

**Checkpoint**: Zonen sind konfigurierbar, abfragbar und indiziert. Die Geschichten können beginnen.

---

## Phase 3: User Story 1 — Der Spieler steht in einer Region, und das System weiß es (P1) 🎯 MVP

**Goal**: die Zuordnung ist jederzeit bekannt, schnell, und ein Grenzübertritt erzeugt genau ein
Ereignis.

**Independent Test**: sechs Regionen laden, eine Grenze überschreiten (genau ein Zonenwechsel), den
Spawn verlassen (genau ein Kernereignis, kein Zonenwechsel), und die Zeit für 200 Abfragen messen.

### Tests für US1

- [X] T033 **Zusammengelegt in `ZoneTrackerTest`** (T033, T034 und T036 prüfen dasselbe Objekt mit demselben Aufbau; drei Klassen hätten den Aufbau dreimal gebaut) — genau ein Ereignis je tatsächlicher Änderung, alte und neue Zone belegt, eine Seite darf leer sein; **kein** zweites Ereignis für unveränderte Zuordnung (FR-015, FR-018)
- [X] T034 **In `ZoneTrackerTest`**, siehe T033 — das Verlassen des Kerns erzeugt genau ein Kernereignis und **keinen** Zonenwechsel (FR-016, SC-006)
- [X] T035 [P] [US1] `MovementEvaluationTest` in `rpg-core/src/test/java/rpg/core/zone/` — Bewegung innerhalb eines gewöhnlichen Chunks löst **keine** Neubewertung aus; Bewegung innerhalb eines **Grenzchunks** löst sie aus (FR-019, FR-020, research.md R4)
- [X] T036 **In `ZoneTrackerTest`**, siehe T033 — nach einem Neuladen werden alle Anwesenden neu bewertet, Ereignisse feuern **nur** für tatsächliche Änderungen, und **keine wiederkehrende Aufgabe** entsteht (FR-014, FR-018, Prinzip II)
- [X] T037 [P] [US1] `ZoneLookupBenchmarkTest` in `rpg-core/src/test/java/rpg/core/zone/` — die Zuordnung für **200 Positionen** unter **0,5 ms**, wiederholbar und **ohne Server**. Eine Messung, kein Lasttest (SC-001, SC-002, ADR-031)

### Umsetzung US1

- [X] T038 [P] [US1] `ZoneChangedEvent` in `rpg-core/.../zone/ZoneChangedEvent.java` — Charakter, alte und neue Kennung, je optional (FR-015)
- [X] T039 [P] [US1] `SafeAreaCrossedEvent` in `rpg-core/.../zone/SafeAreaCrossedEvent.java` — Charakter, Zonenkennung, betreten oder verlassen. **Ein eigener Typ**, kein Feld am Zonenwechsel (FR-016, research.md R9)
- [X] T040 [US1] `ZoneTracker` in `rpg-core/.../zone/ZoneTracker.java` — hält die letzte bekannte Zuordnung je Charakter, vergleicht und veröffentlicht. Der Zustand hängt am Charakter, nicht global (Prinzip I)
- [X] T041 [US1] `ZoneMovementListener` in `rpg-platform/.../zone/ZoneMovementListener.java` — `PlayerMoveEvent`; **zuerst** der Grenzchunk-Wächter als reine Ganzzahlarithmetik auf `getBlockX() >> 4`, ohne `Chunk`-Objekt, danach erst der Index. Kopfkommentar nach dem Muster von `DoubleJumpListener` („one of the busiest events a server has") (FR-019, FR-020)
- [X] T042 **Geändert: kein Bukkit-Listener, sondern `SessionObserver`.** B03 besitzt den Sitzungslebenszyklus und erlaubt genau einen Join-Handler (FR-007); `NoCompetingSessionListenersTest` erzwingt das, und der erste Entwurf hat ihn rot gemacht. Die Platzierung hängt jetzt am Beobachter in `RpgPlugin` (`placeInZone`), der Tracker hält die Halter→Charakter-Übersetzung für `forgetHolder` selbst. **Folge für T084:** der Kampf-Logout darf ebenfalls nicht auf `PlayerQuitEvent` hören — Zuordnung bei Anmeldung und bei Charakterwechsel, ohne dass sich der Spieler bewegen muss (FR-017, Randfall „Charakterwechsel")
- [X] T043 [US1] Teleport abdecken in `rpg-platform/src/main/java/rpg/platform/zone/ZoneMovementListener.java` — der Wechsel feuert auch bei einer Versetzung, nicht nur beim Laufen (FR-017, SC-005)
- [X] T044 [US1] Ausnahmebarriere um die Zonenauswertung in `rpg-core/src/main/java/rpg/core/zone/ZoneTracker.java` — ein Fehler wird örtlich begrenzt und protokolliert, kein Spieler landet in einem unklaren Zustand (FR-064, Prinzip VI)
- [X] T045 [US1] Beide Listener in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` anmelden (ADR-012)

**Checkpoint**: US1 ist allein lieferbar und vorführbar. Sechs Regionen, zwei Ereignisse, gemessene
Abfragezeit.

---

## Phase 4: User Story 2 — Die Warnung, dass es hier zu gefährlich ist (P2)

**Goal**: unter dem Levelband genau eine Warnung beim Betreten, ohne jede Sperre.

**Independent Test**: Stufe 7 betritt *The Dustlands* → eine Meldung. Stufe 15 dieselbe Grenze →
Stille.

### Tests für US2

- [X] T046 **Zusammengelegt in `LevelBandWarningTest`** (T046, T047 und T048 prüfen dasselbe Objekt; zwölf Fälle in einer Klasse statt drei Klassen mit demselben Aufbau) — unter `min` genau eine Warnung; innerhalb des Bandes keine; **über** `max` keine (FR-021, FR-023); Stufe genau auf `min` löst keine aus (Bandgrenze schliesst ein)
- [X] T047 **In `LevelBandWarningTest`**, siehe T046 — mehrfaches Überschreiten derselben Grenze in kurzer Folge wiederholt die Warnung nicht; zeitstempelbasiert und lazy, **keine** wiederkehrende Aufgabe (FR-024, Prinzip II)
- [X] T048 **In `LevelBandWarningTest`**, siehe T046 — der Zutritt wird nie verweigert, verzögert oder zurückgesetzt (FR-022)

### Umsetzung US2

- [X] T049 [US2] `LevelBandGuard` in `rpg-core/.../zone/LevelBandGuard.java` — hört auf `ZoneChangedEvent`, fragt B06s Levelabfrage, entscheidet über die Warnung. Hängt an T038
- [X] T050 [US2] Die Wiederholungssperre je Charakter und Zone — zeitstempelbasiert, Dauer konfigurierbar in `zones.yml` (FR-024, FR-062)
- [X] T051 [US2] In `rpg-core/src/main/java/rpg/core/zone/LevelBandGuard.java` die Warnung über den Message-Schlüssel `zone.too-dangerous` mit `{min}` und `{max}` ausgeben (FR-025, FR-061)

**Checkpoint**: US1 und US2 laufen unabhängig.

---

## Phase 5: User Story 3 — PvP wird zur Regel der Zone (P3)

**Goal**: `DamagePermission` ist **ersetzt**, nicht kopiert; der Schutzkern ist vollständig
schadensfrei.

**Independent Test**: mit ausgelieferter Konfiguration verhält sich alles wie vorher; eine Region auf
`pvp: true` erlaubt Schaden dort und nirgends sonst.

### Tests für US3

- [X] T052 [US3] **`SinglePermissionPointTest` und `DamagePermissionTest` aus B05 unverändert laufen lassen** — `./gradlew test --tests '*SinglePermissionPointTest*' --tests '*DamagePermissionTest*'`. Beide **dürfen nicht angepasst werden**; ein angepasster Test wäre kein Nachweis, sondern dessen Umgehung (SC-008)
- [X] T053 **Zusammengelegt mit T054 in `ZoneDamagePermissionTest`** (drei `@Nested`-Blöcke: ausgeliefertes Verhalten, Schutzkern, PvP-Schalter) — alle sechs Fälle der ausgelieferten Regel bleiben **ausserhalb der Kerne** unverändert (FR-030); `pvp: true` erlaubt Spieler gegen Spieler nur in der Gefahrenzone dieser Zone (SC-009); ausserhalb aller Zonen bleibt PvP aus (FR-029)
- [X] T054 **In `ZoneDamagePermissionTest`**, Block „the safe core refuses everything" — Ziel im Kern: abgelehnt; **Angreifer** im Kern: abgelehnt; Umweltschaden am Ziel im Kern: abgelehnt; und das auch, wenn die Region `pvp: true` trägt (FR-028a, FR-028b, SC-012)

### Umsetzung US3

- [X] T055 [US3] `ZoneDamagePermission` in `rpg-core/.../zone/ZoneDamagePermission.java` — **umschliesst** `DamagePermission.defaultRule()` und ergänzt Kern- und Zonenregel in der Reihenfolge aus research.md R10. Die vier Fälle, die B09 nichts angehen, kommen weiterhin aus B05s eigener Methode (FR-026, FR-030)
- [X] T056 [US3] Einsetzen über `CombatPipeline.setPermission` in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` — **ersetzen**, keine zweite Kopie der Entscheidung anlegen (FR-026, ADR-012)
- [X] T057 [US3] `pvp: false` für alle sechs Regionen in `rpg-plugin/src/main/resources/zones.yml` sicherstellen — am Spielgeschehen ändert dieser Block nichts, er beweist die Austauschbarkeit (FR-031, SC-008)

**Checkpoint**: die Schadenserlaubnis ist eine Zonenregel, und B05s Tests sind unangetastet grün.

---

## Phase 6: User Story 4 — Der Tod führt nach Hause (P4)

**Goal**: Tod in einer Region → Schutzkern dieser Region, mit Meldung; ohne Region → Ausweichpunkt.

**Independent Test**: in jeder der sechs Regionen sterben und prüfen, wo man erscheint; einmal in der
Wildnis sterben.

### Tests für US4

- [ ] T058 [P] [US4] `RespawnPointTest` in `rpg-core/src/test/java/rpg/core/zone/` — der Respawn-Punkt einer Zone liegt in ihrem Schutzkern; eine Zone ohne Kern hat keinen (FR-032, FR-034)
- [ ] T059 [P] [US4] `DeathRoutingTest` in `rpg-core/src/test/java/rpg/core/zone/` — Tod in einer Zone führt an deren Punkt (FR-033); ausserhalb aller Zonen an den Ausweichpunkt (FR-034); verschwindet die Zone zwischen Tod und Erscheinen, greift der Ausweichpunkt und die Anmeldung scheitert **nicht** (FR-037)
- [ ] T060 [P] [US4] `DeathCostsNothingTest` in `rpg-core/src/test/java/rpg/core/zone/` — Erfahrung und Inventar sind nach dem Tod unverändert (FR-036)

### Umsetzung US4

- [ ] T061 [P] [US4] `Teleporter` in `rpg-core/.../zone/Teleporter.java` — Schnittstelle; die Domäne liefert Orte und versetzt nicht selbst (FR-037c, Prinzip III)
- [ ] T062 [P] [US4] `BukkitTeleporter` in `rpg-platform/.../zone/BukkitTeleporter.java` — die Umsetzung, im Tick, mit einem ehrlichen Rückgabewert für einen gescheiterten Teleport (Vorbereitung für FR-050f)
- [ ] T063 [US4] `respawnPointOf` und `fallbackPoint` in `rpg-core/src/main/java/rpg/core/zone/DefaultZones.java` (FR-032, FR-034)
- [ ] T064 [US4] `ZoneRespawnListener` in `rpg-platform/.../zone/ZoneRespawnListener.java` — Tod → Schutzkern der Zone, in der gestorben wurde, plus Meldung `zone.died` (FR-033, FR-035)
- [ ] T065 [US4] **Die Startregion**: `startPoint()` in `rpg-core/src/main/java/rpg/core/zone/DefaultZones.java`, und die Abfrage aus der Charaktererstellung heraus bedienen — dieser Block **liefert** den Ort, er setzt den Charakter nicht (FR-037b, FR-037c, SC-025)
- [ ] T066 [US4] `StartRegionTest` in `rpg-core/src/test/java/rpg/core/zone/` — genau eine Startregion ist Pflicht (FR-037a); ein neuer Charakter erhält deren Respawn-Punkt, **unabhängig vom Weltspawn** (FR-037b)
- [ ] T067 [US4] `OnePointThreePurposesTest` in `rpg-core/src/test/java/rpg/core/zone/` — Spielbeginn, Tod und Reiseziel derselben Region liefern **dieselbe** Koordinate; es gibt je Region nur einen Ankunftsort in der Konfiguration (FR-037d, SC-024)
- [ ] T068 [US4] `ZoneRespawnListener` in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` anmelden (ADR-012)

**Checkpoint**: der Tod hat ein Ziel, und die Startregion auch.

---

## Phase 7: User Story 5 — Wer im Kampf verschwindet, stirbt (P5)

**Goal**: Logout innerhalb der Kampfsekunden = Tod; beim nächsten Anmelden Schutzkern plus Meldung.

**Independent Test**: im Kampf ausloggen, wieder anmelden → Schutzkern und Meldung. Acht Sekunden
später ausloggen → nichts.

> **Diese Geschichte legt die Persistenz an, die US6 mitbenutzt.** Der Aggregattyp, die Migration und
> die drei Eintragungen entstehen hier, weil der Merker für den ausstehenden Respawn der erste
> Verbraucher ist (data-model.md §4, research.md R8, R11).

### Eingriff in einen abgeschlossenen Block

- [ ] T069 [US5] **`DeathCause.LOGOUT`** in `rpg-core/src/main/java/rpg/core/combat/DeathCause.java` ergänzen — **Eingriff in B05, gedeckt durch ADR-030**. Das Javadoc der Aufzählung begründet, dass sie Fälle *unterscheiden* soll; der vierte Wert passt dazu. Danach jede Stelle behandeln, die über die Werte verzweigt — **der Compiler zeigt sie**
- [ ] T070 [US5] `DeathCauseLogoutTest` in `rpg-core/src/test/java/rpg/core/combat/` — der Logout-Tod ist von einem gewöhnlichen Kampftod unterscheidbar (FR-040, SC-011)

### Persistenz (Grundlage auch für US6)

- [ ] T071 [US5] Migration `V9_1__character_zone_state.sql` in `rpg-persistence/src/main/resources/db/migration/` — `rpg.character_waypoints` (Primärschlüssel `character_id, crystal_key`) und `rpg.character_zone_state` (`character_id` als Primärschlüssel, `pending_respawn_zone` nullable), beide `REFERENCES rpg.character (character_id) ON DELETE CASCADE`. Kommentar wie in `V4_1` und `V6_1`: dieselbe Zeile erledigt die Anonymisierung (data-model.md §4)
- [ ] T072 [US5] **Eintragung 1 von 3** (ADR-015 Punkt 7): `AggregateType.CHARACTER_ZONE_STATE` in `rpg-core/src/main/java/rpg/core/persistence/AggregateType.java` — mit Javadoc nach dem Muster von `CHARACTER_BALANCE`
- [ ] T073 [US5] **Eintragung 2 von 3**: der Platz in `FlushCycle.WRITE_ORDER` in `rpg-persistence/src/main/java/rpg/persistence/FlushCycle.java` — nach `CHARACTER`, weil der Fremdschlüssel darauf zeigt. Eine fehlende Eintragung lässt die Marken bei jedem Flush als fehlgeschlagen zählen; das steht als Warnung im Javadoc von `AggregateType`
- [ ] T074 [US5] **Eintragung 3 von 3**: `persistence.flushCycle().register(AggregateType.CHARACTER_ZONE_STATE, repository)` in `rpg-persistence/.../zone/ZonePersistenceModule.java`, nach dem Muster von `AbilityModule` und `ClassesModule`
- [ ] T075 [US5] `PendingRespawn` in `rpg-core/.../zone/PendingRespawn.java` und `PendingRespawnRepository` als Schnittstelle
- [ ] T076 [US5] `JdbcPendingRespawnRepository` in `rpg-persistence/src/main/java/rpg/persistence/zone/JdbcPendingRespawnRepository.java` — Schreiben über B02s Puffer, kein Datenbankzugriff im Spielereignis (FR-063)
- [ ] T077 [US5] `ZoneStateAggregateTest` in `rpg-persistence/src/test/java/rpg/persistence/zone/` — **Testcontainers gegen echtes PostgreSQL** (Prinzip VII.2): Merker übersteht einen Neustart; `ON DELETE CASCADE` räumt beim Löschen des Charakters ab
- [ ] T078 [US5] `AggregateRegistrationTest` in `rpg-persistence/src/test/java/rpg/persistence/zone/` — alle drei Eintragungen vorhanden; fehlt eine, schlägt der Test fehl statt der Flush (ADR-015 Punkt 7)

### Tests für US5

- [ ] T079 [P] [US5] `CombatLogoutDeathTest` in `rpg-core/src/test/java/rpg/core/zone/` — im Kampf ausloggen → Tod mit `LOGOUT`, Merker gesetzt (FR-038); ausserhalb der Kampfsekunden → nichts (FR-039, SC-011)
- [ ] T080 [P] [US5] `CombatTimeoutIsReadNotCopiedTest` in `rpg-core/src/test/java/rpg/core/zone/` — die Dauer kommt aus B05s `CombatState`/`combat.yml`; **es gibt keine zweite Zeitangabe** für denselben Zweck (FR-039)
- [ ] T081 [P] [US5] `LogoutSwitchTest` in `rpg-core/src/test/java/rpg/core/zone/` — `combat-logout: none` schaltet die Regel ab, ohne dass Code geändert wird (FR-042)
- [ ] T082 [P] [US5] `ConnectionLossTest` in `rpg-core/src/test/java/rpg/core/zone/` — ein Verbindungsabbruch wird wie ein absichtliches Verlassen behandelt; es gibt **keine** Unterscheidung, die ein Client herbeiführen kann (FR-043, Prinzip VI)

### Umsetzung US5

- [ ] T083 [US5] `combat-logout: death | none` in `zones.yml` mit Schema-Prüfung (FR-042, FR-062)
- [ ] T084 [US5] `ZoneQuitListener` in `rpg-platform/.../zone/ZoneQuitListener.java` — `PlayerQuitEvent`, `isInCombat` fragen, Merker setzen und `CombatDeathEvent` mit `DeathCause.LOGOUT` veröffentlichen (`killerId` leer, `playerVictim` wahr) — research.md R11
- [ ] T085 [US5] Den Merker beim nächsten Anmelden anwenden, in `rpg-platform/src/main/java/rpg/platform/zone/ZoneJoinListener.java` — Versetzung in den Schutzkern, Meldung `zone.died-logout`, Merker löschen. Erweitert `ZoneJoinListener` aus T042 (FR-041)
- [ ] T086 [US5] Merker ohne Zone abfangen, ebenfalls in `rpg-platform/src/main/java/rpg/platform/zone/ZoneJoinListener.java` — verschwindet die Zone zwischen Logout und Anmeldung, greift der Ausweichpunkt; die Anmeldung scheitert **nicht** (FR-037, Randfall)
- [ ] T087 [US5] `ZoneQuitListener` in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` anmelden (ADR-012)

**Checkpoint**: Fliehen durch Ausloggen gewinnt nichts mehr.

---

## Phase 8: User Story 6 — Wegpunkt-Kristalle (P6)

**Goal**: freischalten per Rechtsklick, Auswahlfenster mit allen Kristallen, Reise gegen Coins.

**Independent Test**: frischer Charakter, Kristall freischalten, gesperrtes Ziel anklicken (Meldung),
hinlaufen, dort freischalten, zurückreisen — Coins gesunken, Freischaltung übersteht den Neustart.

> **Die aufwendigste Geschichte des Blocks.** Sie trägt drei der vier Grenzüberschreitungen aus dem
> *Complexity Tracking* und setzt auf der Persistenz aus US5 auf.

### Eingriff in einen abgeschlossenen Block

- [ ] T088 [US6] **`WAYPOINT_TRAVEL(Direction.DEBIT)` und `WAYPOINT_REFUND(Direction.CREDIT)`** in `rpg-core/src/main/java/rpg/core/currency/BookingReason.java` ergänzen — **Eingriff in B08b, gedeckt durch ADR-032**. Zwei Gründe, nicht einer: ohne den zweiten wäre eine Rückbuchung im Verlauf nicht von einer Gutschrift zu unterscheiden (FR-050d, research.md R1)
- [ ] T089 [US6] `WaypointBookingReasonTest` in `rpg-core/src/test/java/rpg/core/currency/` — der Verlauf trennt eine Reise von einem Einkauf und von einer Reparatur, und eine Rückbuchung von einer Gutschrift (SC-022)

### Freischaltungen

- [ ] T090 [P] [US6] `Waypoints` in `rpg-core/.../zone/Waypoints.java` — `isUnlocked`, `unlock` (idempotent), `unlockedBy` nach [contracts/zone-api.md](./contracts/zone-api.md)
- [ ] T091 [P] [US6] `WaypointUnlockRepository` als Schnittstelle in `rpg-core/src/main/java/rpg/core/zone/WaypointUnlockRepository.java`
- [ ] T092 [US6] `JdbcWaypointUnlockRepository` in `rpg-persistence/src/main/java/rpg/persistence/zone/JdbcWaypointUnlockRepository.java` — Schreiben über B02s Puffer, Tabelle aus T071. **Kein Fremdschlüssel auf eine Zone**: eine verwaiste Zeile ist ein gültiger Zustand (FR-051b, data-model.md §4)
- [ ] T093 [P] [US6] `WaypointUnlockTest` in `rpg-core/src/test/java/rpg/core/zone/` — je Charakter, nie je Account (FR-051a); ein zweiter `unlock` ändert nichts; nie entzogen ausser mit dem Charakter (FR-051b2)
- [ ] T094 [P] [US6] `WaypointPersistenceTest` in `rpg-persistence/src/test/java/rpg/persistence/zone/` — **Testcontainers**: Freischaltung übersteht Neustart (SC-019); ein zweiter Charakter desselben Accounts erbt nichts (SC-019); Löschen des Charakters räumt ab (SC-027); ein Kristall, der aus der Konfiguration verschwindet und zurückkommt, wirkt wieder (FR-051b)

### Reisen

- [ ] T095 [P] [US6] `Travel` und `TravelResult` in `rpg-core/.../zone/Travel.java` — die sieben Ausgänge aus [contracts/zone-api.md](./contracts/zone-api.md), Rückgabe statt Ausnahme
- [ ] T096 [US6] `DefaultTravel` in `rpg-core/.../zone/DefaultTravel.java` — **die Reihenfolge ist Vertrag**: freigeschaltet prüfen → Kampf prüfen → `debit(WAYPOINT_TRAVEL)` → versetzen → bei Fehlschlag `credit(WAYPOINT_REFUND)`. Alles in derselben Tickphase (FR-050, FR-050e, FR-050f, research.md R1)
- [ ] T097 [P] [US6] `TravelOrderTest` in `rpg-core/src/test/java/rpg/core/zone/` — gesperrtes Ziel: keine Buchung, keine Versetzung (FR-049); im Kampf: keine Buchung, keine Versetzung (FR-051f, SC-023); zu wenig Coins: Stand und Ort unverändert (FR-050a, SC-020)
- [ ] T098 [P] [US6] **`TravelRefundTest`** in `rpg-core/src/test/java/rpg/core/zone/` — **die wichtigste Zusicherung dieser Geschichte**: scheitert die Versetzung nach erfolgter Abbuchung, steht der Stand danach wieder auf dem Ausgangswert, und der Verlauf zeigt **beide** Buchungen mit unterschiedlichem Grund (FR-050b, FR-050f, SC-020a)
- [ ] T099 [P] [US6] `TravelInCombatReadsB05Test` in `rpg-core/src/test/java/rpg/core/zone/` — die Kampfprüfung liest **denselben** Zustand wie FR-039, kein zweites Zeitfenster (FR-051g)
- [ ] T100 [P] [US6] `TravelTargetIsZoneRespawnTest` in `rpg-core/src/test/java/rpg/core/zone/` — gereist wird an den Respawn-Punkt der Zone des Kristalls; ein Kristall trägt **kein** eigenes Ziel (FR-045a)
- [ ] T101 [US6] `zone-travel`-Preise in `zones.yml` je Kristall — **in der Zonenkonfiguration, nicht in `currency.yml`** und nicht in einem zentralen Katalog (FR-050c, ADR-027)
- [ ] T102 [P] [US6] `PriceLivesWithTheZoneTest` in `rpg-core/src/test/java/rpg/core/zone/` — `currency.yml` kennt keinen Reisepreis, und es gibt keinen zentralen Katalog (SC-021, ADR-027)

### Eingabe und Fenster — die befristete Ausnahme

- [ ] T103 [US6] Kristall-Index in `ChunkZoneIndex` ergänzen — eine zweite, gleich gebaute Abbildung Chunk → Kristalle, damit ein Rechtsklick **nicht** alle Kristalle prüft (Prinzip II, research.md R5)
- [ ] T104 [P] [US6] `CrystalIndexTest` in `rpg-core/src/test/java/rpg/core/zone/` — der Klick findet seinen Kristall über einen Lookup; ein Klick ohne Kristall kostet nichts
- [ ] T105 [US6] `CrystalInteractListener` in `rpg-platform/.../zone/CrystalInteractListener.java` — `PlayerInteractEvent`, Rechtsklick auf einen Block, Index-Lookup. **Kein Blocktyp-Vergleich**: der Kristall ist ein Bauwerk, und das Reisen darf nicht daran hängen, dass niemand den Stein abbaut. Kopfkommentar mit der Falle aus `AbilityTriggerListener` (Interact auf Luft ist von Geburt an abgebrochen) — FR-046, research.md R5
- [ ] T106 [US6] Ratensperre für das Öffnen — zeitstempelbasiert je Spieler, lazy ausgewertet, **keine** wiederkehrende Aufgabe. Ohne sie wäre gedrückt gehaltene rechte Maustaste ein Fenster je Tick (Prinzip VI, research.md R5)
- [ ] T107 [P] [US6] `InteractRateLimitTest` in `rpg-platform/src/test/java/rpg/platform/zone/` — mehrere Rechtsklicks in Folge öffnen das Fenster nicht mehrfach
- [ ] T108 [US6] In `rpg-platform/src/main/java/rpg/platform/zone/CrystalInteractListener.java`: der **erste** Rechtsklick schaltet frei und öffnet **kein** Fenster; jeder weitere öffnet es (FR-047, FR-048)
- [ ] T109 [US6] `WaypointMenu` in `rpg-platform/.../zone/WaypointMenu.java` — **befristete Ausnahme nach ADR-032**, nach dem Muster von `CurrencyMenu` aus ADR-028. Zeigt **alle** Kristalle: freigeschaltete wählbar, übrige sichtbar und gesperrt (FR-048)
- [ ] T110 [US6] In `rpg-platform/src/main/java/rpg/platform/zone/WaypointMenu.java`: gesperrte Einträge tragen **Name und Levelband** ihrer Region, aufgelöst über `zone.<key>.name` — **kein zweiter Ort für Zonennamen** (FR-048a, FR-048b, SC-026)
- [ ] T111 [US6] `WaypointMenuListener` in `rpg-platform/.../zone/WaypointMenuListener.java` — Klick auf gesperrt → Meldung, sonst `Travel.travelTo`. Prüfung beim **Klicken**, nicht beim Öffnen: ein Ziel kann zwischen Öffnen und Klick weggefallen sein (Randfall, `NO_SUCH_CRYSTAL`)
- [ ] T112 [P] [US6] `WaypointMenuTest` in `rpg-platform/src/test/java/rpg/platform/zone/` — alle sechs erscheinen; die gesperrten sind gesperrt und tragen Name und Band; ein Klick darauf bucht nichts (FR-049, SC-026)
- [ ] T113 [US6] In `rpg-platform/src/main/java/rpg/platform/zone/WaypointMenu.java`: der eigene Standort erscheint als gewählt, nicht als Ziel — niemand zahlt für nichts (Randfall)
- [ ] T114 [US6] Alle Meldungen dieser Geschichte in `rpg-plugin/src/main/resources/messages.yml` und `rpg-core/src/main/java/rpg/core/zone/ZoneMessageKeys.java` eintragen: Freischaltung, Sperre, fehlende Coins, Kampf, ausgefallenes Ziel, Fenstertitel, Reise, Rückbuchung (FR-051e)
- [ ] T115 [US6] Je Region **ein** Kristall in `zones.yml`, in ihrem Schutzkern (FR-051)
- [ ] T116 [US6] `CrystalInteractListener` und `WaypointMenuListener` in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` anmelden (ADR-012)
- [ ] T117 [US6] **Übereinstimmung mit ADR-032 in `02-decisions.md` prüfen** — die Umsetzung entspricht dem, was das ADR zugesagt hat: Fenster und Eingabe sind als befristet gekennzeichnet und im Quelltext mit dem Verweis auf B13 versehen, die zwei Buchungsgründe sind da, die Persistenz hängt am Charakter. **Das ADR ist bereits geschrieben** — hier wird nur abgeglichen

**Checkpoint**: Reisen funktioniert, kostet, und verliert kein Geld.

---

## Phase 9: User Story 7 — Die wartenden Schnittstellen antworten (P7)

**Goal**: einlösen, was ausgelieferte Blöcke bereithalten — und ehrlich benennen, was weiter wartet.

**Independent Test**: `isOpenWorld` sagt in allen sechs Regionen ja; die Spawn-Bereiche jeder Region
sind namentlich abfragbar.

### Tests für US7

- [ ] T118 [P] [US7] `ZoneWorldConditionTest` in `rpg-core/src/test/java/rpg/core/zone/` — ohne Instanzen lautet die Antwort überall ja, **als Entscheidung**; ein Test, der die Begründung im Javadoc mitprüft, wäre zu viel, aber der Kommentar muss sie nennen (FR-052)
- [ ] T119 [P] [US7] `SpawnAreaQueryTest` in `rpg-core/src/test/java/rpg/core/zone/` — jede der sechs Regionen liefert mehrere Bereiche mit Kennung und Geometrie **und nichts sonst**; dieser Block spawnt nichts darin (FR-053a, FR-054)

### Umsetzung US7

- [ ] T120 [US7] `ZoneWorldCondition` in `rpg-core/.../zone/ZoneWorldCondition.java` — löst B08s `WorldCondition.isOpenWorld` ein. Javadoc: die Antwort ist eine **Entscheidung**, keine unfertige Umsetzung, und sie ändert sich, sobald eine Instanzwelt dazukommt (FR-052)
- [ ] T121 [US7] Einsetzen in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` an der Stelle, an der bisher `WorldCondition.everywhere()` steht (ADR-012)
- [ ] T122 [US7] `spawnAreasOf` in `rpg-core/src/main/java/rpg/core/zone/DefaultZones.java` (FR-054)
- [ ] T122a [P] [US7] `ZoneDisplayHandoffTest` in `rpg-core/src/test/java/rpg/core/zone/` — was B13 zum Anzeigen bekommt, ist **Kennung und Ereignis**, nie ein fertiger Anzeigetext; kein Rückgabewert dieses Blocks trägt einen aufgelösten Zonennamen (FR-058, FR-003a)
- [ ] T123 [US7] Im `package-info.java` festhalten, dass `XpSource.ZONE_OBJECTIVE` und `SourceKind` für zonengebundene Effekte **unbefüllt bleiben** — der erste braucht Zonenziele, der zweite den herausgenommenen Schwierigkeitsmodifikator. Zwei der vier wartenden Schnittstellen sind eingelöst, zwei warten weiter, und das steht da (research.md R12)

**Checkpoint**: alle sieben Geschichten laufen.

---

## Phase 10: Polish, Verdrahtung und Abschluss

### Querschnitt

- [ ] T124 [P] `ZoneImmutabilityTest` in `rpg-core/src/test/java/rpg/core/zone/` — `Cuboid`, `Area`, `Zone`, `SafeCore`, `SpawnArea`, `WaypointCrystal` und beide Ereignisse sind unveränderlich
- [ ] T125 [P] `ZoneConfigEffectTest` in `rpg-core/src/test/java/rpg/core/zone/` — eine geänderte Zonengeometrie, ein geändertes Levelband, ein geänderter `pvp`-Schalter und ein geänderter Reisepreis wirken nach dem Neuladen, **ohne dass Code geändert wurde** (SC-003, Prinzip V), nach dem Muster von `AbilityConfigReloadTest`
- [ ] T126 [P] `SeventhRegionTest` in `rpg-core/src/test/java/rpg/core/zone/` — eine siebte Region entsteht durch Konfiguration allein (SC-003)
- [ ] T127 [P] `ZoneInOwnWorldTest` in `rpg-core/src/test/java/rpg/core/zone/` — die `world:`-Zeile verschiebt eine Zone in eine eigene Welt, ohne Codeänderung (SC-004, ADR-006)
- [ ] T128 [P] `NoBukkitInCoreTest` in `rpg-core/src/test/java/rpg/core/zone/` — kein Typ aus `org.bukkit` im Zonenpaket der Domänenschicht (FR-059, Prinzip III), nach dem Muster der vorhandenen Quelltests
- [ ] T128a [P] `ZoneSourceInvariantsTest` in `rpg-core/src/test/java/rpg/core/zone/` — kein Block greift am Vertrag vorbei: `ChunkZoneIndex`, `ZoneConfig` und `DefaultZones` werden ausserhalb von `rpg.core.zone` nicht benutzt, und `zones.yml` wird nur von diesem Paket gelesen (FR-060, Prinzip III). Nach dem Muster von `ClassSourceInvariantsTest`
- [ ] T129 [P] `NoDatabaseInGameplayPathTest` in `rpg-core/src/test/java/rpg/core/zone/` — keine Bewegung, kein Zonenwechsel und keine Warnung erzeugt einen Datenbankzugriff (FR-063)
- [ ] T130 `FullBootstrapTest` in `rpg-plugin/src/test/java/` erweitern (ADR-012) — Modul registriert, **alle sechs Listener** angemeldet, `setPermission` gesetzt, `ZoneWorldCondition` eingesetzt, `applyReloadedConfig` aufgerufen, der Aggregattyp verdrahtet

### Abschluss

- [ ] T131 `./gradlew test` vollständig — **0 Fehler, 0 übersprungen**. Auf übersprungene Tests achten: MockBukkit meldet Nicht-Implementiertes als *skipped*, nicht als Fehler, und ein „skipped" ist hier ein Befund
- [ ] T132 [quickstart.md](./quickstart.md) **Abschnitt 1** durchlaufen und die Ergebnisse festhalten — Tests, die Messung zu SC-001, und die beiden B05-Tests, die unverändert grün bleiben müssen
- [ ] T133 [quickstart.md](./quickstart.md) **Abschnitt 2** durchlaufen — die elf Konfigurationsproben, davon acht verweigerte Starts
- [ ] T134 [quickstart.md](./quickstart.md) **Abschnitt 3** auf einem echten Paper-Server — die **47 Prüfschritte**, besonders 13–15 (der Kern ist wirklich schadensfrei), 22 (Kampf-Logout), 32 (dieselbe Koordinate für Tod und Reise), 34 (Reise im Kampf) und 39 (gelöschter Charakter erbt nichts). Grüne Tests beweisen nichts über Papers `libraries:`-Klassenlader; nur der echte Start tut das
- [ ] T135 Abschnitt 4 (Last) **nicht** als Aufgabe führen, sondern als Vermerk in `minecraft-rpg-spec/minecraft-rpg-spec/blocks/B09-zones-regions.md`: seit **ADR-031** gehört der Lastnachweis in B15s Phase und hält diesen Block nicht offen. Im Steckbrief festhalten

### Dokumentation

- [ ] T136 [P] `package-info.java` in `rpg/core/zone/` ausformulieren — der Vertrag aus [contracts/zone-api.md](./contracts/zone-api.md), die Zusage „ab jetzt ADR-pflichtig", und was ausdrücklich **nicht** hierher gehört (Mobs, Loot, Anzeige, Zonenziele)
- [ ] T137 [P] Steckbrief `minecraft-rpg-spec/minecraft-rpg-spec/blocks/B09-zones-regions.md` auf **Implementiert** setzen, mit Aufgabenzahl, Testzahl und den offen gebliebenen Punkten
- [ ] T138 [P] `docs/05-roadmap-speckit-workflow.md`: „Empfohlener nächster Schritt" auf **B10** umstellen; B09 hat drei wartende Schnittstellen eingelöst und zwei benannt
- [ ] T139 [P] `06-open-questions.md` (im **Projektstamm**, nicht die eingefrorene Kopie): den B09-Abschnitt schliessen und im B10-Abschnitt vermerken, dass die Spawn-Bereiche jetzt bereitstehen
- [ ] T140 [P] `02-decisions.md`: ADR-032 um eine Nachbemerkung ergänzen — die vier Eingriffe sind ausgeführt, die zwei Buchungsgründe stehen in B08b, und Fenster und Eingabe sind als befristet gekennzeichnet
- [ ] T141 [P] `minecraft-rpg-spec/minecraft-rpg-spec/blocks/B05-combat-pipeline.md` und `.../B08b-currency-account.md` dort nachziehen, wo sie durch die zwei Eingriffe berührt sind: `DeathCause` hat einen vierten Wert, `BookingReason` zwei weitere

---

## Dependencies & Execution Order

### Phasenabhängigkeiten

- **Phase 1 (Setup)**: keine Abhängigkeit, kann sofort beginnen
- **Phase 2 (Foundational)**: hängt an Phase 1 — **blockiert alle sieben Geschichten**
- **Phase 3–9 (Geschichten)**: hängen an Phase 2. Danach:
  - **US1, US2, US3, US4, US7** sind untereinander unabhängig und könnten parallel laufen
  - **US5** legt die Persistenz an (T071–T078) und muss **vor US6** stehen
  - **US6** hängt an US5 (Persistenz) und an US4 (Respawn-Punkt als Reiseziel, `Teleporter`)
- **Phase 10 (Abschluss)**: hängt an allen gewünschten Geschichten

### Abhängigkeiten innerhalb der Geschichten

- **US1**: T038, T039 vor T040; T040 vor T041–T043
- **US2**: T038 (aus US1) vor T049 — die Warnung hört auf das Zonenereignis
- **US3**: T052 **zuerst**, als Ausgangsmessung; T055 vor T056
- **US4**: T061 vor T062; T063 vor T064; T065 vor T067
- **US5**: T069 vor T070; T071 vor T072–T074; T072–T074 vor T076; T083 vor T084
- **US6**: T088 vor T096; T090–T092 vor T096; T095 vor T096; T103 vor T105; T105 vor T108, T109; T109 vor T111
- **US7**: T120 vor T121

### Die drei Eingriffe in fremde Blöcke

Sie stehen absichtlich sichtbar und einzeln, nicht in einer Sammelaufgabe:

| Aufgabe | Fremder Block | Deckung |
|---|---|---|
| **T069** `DeathCause.LOGOUT` | B05 (abgeschlossen) | ADR-030 |
| **T088** `WAYPOINT_TRAVEL`, `WAYPOINT_REFUND` | B08b (abgeschlossen) | ADR-032 |
| **T109** Auswahlfenster, **T105** Rechtsklick | B13-Gebiet | ADR-032, befristet |

**T117** gleicht die Umsetzung mit ADR-032 ab. Beide ADRs sind **bereits geschrieben** — es gibt
keine Schreibaufgabe mehr, nur diesen Abgleich.

### Parallele Gelegenheiten

- Phase 1: T001–T005 alle parallel
- Phase 2: T006, T007, T009–T013 parallel; T031 parallel zu allem in `rpg-core`
- Je Geschichte: die Testaufgaben sind untereinander parallel
- US6 ist die einzige Geschichte, in der die Reihenfolge wirklich eng ist — Buchung, Index, Fenster
  und Reise hängen aneinander

### Empfohlene Reihenfolge

```
Setup → Foundational → US1 (MVP) → US2 → US3 → US4 → US5 → US6 → US7 → Abschluss
```

**MVP ist US1 allein**: sechs Regionen, zwei Ereignisse, gemessene Abfragezeit. Das ist vorführbar
und nützt drei ausgelieferten Blöcken sofort etwas, auch wenn keine einzige weitere Geschichte
folgt.

### Was nach US4 lieferbar wäre, wenn die Zeit knapp wird

US1 bis US4 sind zusammen ein vollständiger, spielbarer Zustand: Regionen, Warnungen, PvP als
Zonenregel, Tod mit Ziel. **US5 und US6 sind die teuren**, und US6 trägt allein drei
Grenzüberschreitungen. Sie zurückzustellen wäre eine vertretbare Entscheidung — sie würde nur
bedeuten, dass der Rückweg vom *Pale Wilds* zu Fuss geht.

---

## Notes

- `[P]` heisst: andere Datei, keine offene Abhängigkeit
- Tests sind hier **nicht optional** (Prinzip VII); Persistenztests laufen gegen echtes PostgreSQL,
  nicht gegen Mocks
- Nach jeder Aufgabe oder jeder zusammengehörenden Gruppe committen
- An jedem Checkpoint kann angehalten und die Geschichte einzeln geprüft werden
- **Übersprungene Tests sind ein Befund, kein Erfolg** — MockBukkit meldet Nicht-Implementiertes als
  *skipped*
- Der Lastnachweis gehört seit ADR-031 **nicht** in diesen Block
