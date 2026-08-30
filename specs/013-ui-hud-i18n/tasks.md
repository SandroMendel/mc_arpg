---

description: "Aufgabenliste B13 · UI, HUD & Texte"
---

# Tasks: B13 · UI, HUD & Texte

**Input**: Entwurfsunterlagen aus `/specs/013-ui-hud-i18n/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: **Pflicht, nicht optional.** Prinzip VII verlangt für jede Regel der Domänenschicht einen
Test ohne laufenden Server. Testaufgaben stehen deshalb je Geschichte **vor** der Umsetzung.
**Kein Testcontainers** — B13 persistiert nichts (FR-013b, SC-011), also gibt es nichts, wofür ein
echter Container die richtige Antwort wäre.

**Organization**: gruppiert nach den sechs User Stories der Spec, in deren Reihenfolge. Anders als
B12 braucht diese Liste keine Umsortierung: die sechs Geschichten teilen sich das Fundament und
sonst nichts.

**Stand**: **177 Aufgaben**. Die Liste war T001–T162, als `/speckit-analyze` sie gegen Spec, Plan und
Constitution geprüft hat; die zehn Befunde von dort haben **fünfzehn** Aufgaben ergänzt, jede mit
Buchstabenzusatz an der Stelle, an die sie gehört — so bleibt jeder vorhandene Verweis („macht T025
bis T028 grün") gültig:

| Befund | Ergänzt | Worum es geht |
|---|---|---|
| I1 | T047a, T050a, T050b | Der Fortschritt verlässt die Actionbar (FR-002a) — mit dem Test, der „entfernt" von „vergessen" unterscheidet |
| C1 | T056a–T056d, T064a | Die Sidebar bekommt Ereignispfade (FR-009); die Coin-Zeile bekommt bewusst keinen (FR-009a) |
| C2 | T044a, T044b | Die Bossbar wird beim Abmelden weggeräumt (FR-004c) |
| U2 | T061a, T061b | Nachladen erreicht den laufenden Takt (FR-013c) |
| X1 | T143a, T143b | Der ADR für die Ausnahme von Constitution III.4 (FR-024a) |
| C3 | T150a | Der Wächter für die vier „nicht anfassen"-Zusagen (FR-071a) |

Geändert statt ergänzt wurden T010 (A2), T018 und T080 (U1), T024, T034, T035 (D1), T047, T050,
T101 (A1) und T151.

## Format: `[ID] [P?] [Story] Beschreibung`

- **[P]**: parallelisierbar — andere Datei, keine offene Abhängigkeit
- **[Story]**: US1 bis US6; Setup, Foundational und Polish tragen keine
- Jede Aufgabe nennt ihren Pfad und ihren Bezug (FR, SC, R oder ADR)

## Pfade

- `rpg-core/src/main/java/rpg/core/ui/` — Flächenzuordnung, Rangfolge, Füllstandsrechnung,
  Sichtbarkeitsregeln, Schema, Schlüssel. **Ohne Bukkit** (Constitution III.1)
- `rpg-platform/src/main/java/rpg/platform/ui/` — hier und nur hier wird Paper angefasst: die zwei
  Nähte, die drei Flächen, die drei Fenster, die Schadensanzeigen, das Cooldown-Overlay
- `rpg-platform/src/main/java/rpg/platform/hud/StatusActionBar.java` — **geändert**, nicht ersetzt
- `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` — die Verdrahtung
- `rpg-plugin/src/main/java/rpg/plugin/command/CharacterSheetCommand.java` — neu, vorläufig
- `rpg-plugin/src/main/resources/ui.yml` — neu · `messages.yml`, `plugin.yml` — gewachsen
- Tests jeweils unter `src/test/java/` desselben Moduls

**Kein `rpg-persistence`.** B13 legt kein Schema, keine Tabelle und keine Migration an. Wer in
dieser Liste eine Flyway-Datei sucht, sucht richtig und findet keine — das ist SC-011.

**Fünf Dinge werden benutzt statt gebaut** ([research.md](./research.md)): der Sekundentakt (R1),
die Bossabfrage (R3), die Kanalisierungsdaten (R4), die Cooldown-Restzeit (R5) und die
Sprachprüfung (R8). Wer hier eine Aufgabe „Takt anlegen" oder „Restzeit rechnen" vermisst: sie
fehlt mit Absicht.

---

## Phase 1: Setup — Pakete und Grenzen

**Purpose**: die Blockgrenzen benennen, bevor etwas darin entsteht

- [X] T001 [P] Paket `rpg-core/src/main/java/rpg/core/ui/` mit `package-info.java` anlegen — Kopfkommentar nennt die Grenze: Zuordnung, Rangfolge, Füllstand, Sichtbarkeit, Schema und Schlüssel gehören hierher, **Paper nirgends**, und **keine Persistenz** (FR-013b)
- [X] T002 [P] Paket `rpg-platform/src/main/java/rpg/platform/ui/` mit `package-info.java` anlegen — hier und nur hier wird Paper angefasst; nennt die Regel aus R2: aus dem asynchronen Takt gehört `runSyncOnEntity` nur für **Spieler** hierher, für alles andere `runSyncAtLocation`
- [X] T003 [P] Testpakete unter `rpg-core/src/test/java/rpg/core/ui/` und `rpg-platform/src/test/java/rpg/platform/ui/` anlegen
- [X] T004 [P] Testhilfe `UiFixtures` in `rpg-core/src/test/java/rpg/core/ui/UiFixtures.java` — **Halter- und Charakterkennung sind hier grundsätzlich verschieden** ([[vuntexrpg-holder-vs-character-id]]): eine gemeinsame UUID hat in diesem Projekt schon einmal eine systematische Verwechslung für 1614 Tests unsichtbar gemacht. Dazu eine gestellte `Clock`, weil jede Füllstandsrechnung dieses Blocks gegen die Uhr geht
- [X] T005 [P] Testhilfe `RecordingHudRenderer` in `rpg-platform/src/test/java/rpg/platform/ui/RecordingHudRenderer.java` — ein `HudRenderer`, der Aufrufe sammelt statt zu senden. **Er ist die Messlatte für FR-013 und FR-013a**: „nichts gesendet" muss ein Test unterscheiden können von „etwas Leeres gesendet"

---

## Phase 2: Foundational — Konfiguration, die zwei Nähte, das Modul

**Purpose**: das, wogegen alle sechs Geschichten gebaut werden

**⚠️ CRITICAL**: Vor dieser Phase kann keine Geschichte beginnen

### Die Aufzählungen, die in den Signaturen stehen

- [X] T006 [P] `HudSurface` in `rpg-core/src/main/java/rpg/core/ui/HudSurface.java` — `ACTION_BAR`, `BOSS_BAR`, `SIDEBAR`, je mit Javadoc, welche Rolle die Fläche trägt (FR-001 bis FR-005). Die Zuordnung ist eine **Aufzählung und keine Konfiguration** — der Kommentar sagt warum: zur Wahl gestellt wäre die Frage wieder offen, die dieser Block gerade beantwortet hat
- [X] T007 [P] `BossBarOccasion` in `rpg-core/src/main/java/rpg/core/ui/BossBarOccasion.java` — `CHANNELLING`, `BOSS_FIGHT`, `ZONE_NAME` **in dieser Reihenfolge**. Javadoc hält fest: die Reihenfolge der Aufzählung *ist* die Rangfolge (FR-004), und `CHANNELLING` gewinnt, weil es den Spieler gerade festhält

### Die Konfiguration (`ui.yml`)

- [X] T008 [P] `SurfaceSetting` in `rpg-core/src/main/java/rpg/core/ui/SurfaceSetting.java` — `enabled`. Javadoc: **abgeschaltet heißt kostenlos, nicht unsichtbar** (FR-013a, SC-010)
- [X] T009 [P] `DamageNumberSetting` in `rpg-core/src/main/java/rpg/core/ui/DamageNumberSetting.java` — `enabled`, `lifetime` (positiv), `offset` (endlich); FR-043, FR-045
- [X] T010 `UiConfig` in `rpg-core/src/main/java/rpg/core/ui/UiConfig.java` — `language`, `tick`, `actionBar`, `bossBar`, `sidebar`, `zoneNoticeDuration`, `damageNumbers` nach [data-model.md](./data-model.md) §1 (hängt an T008, T009). **Modell und Datei haben nicht dieselbe Gestalt**, und das ist Absicht: `zoneNoticeDuration` ist eine `Duration`, in der Datei steht sie als `hud.boss-bar.zone-notice-seconds` (Ganzzahl, Einheit im Namen). `UiConfigSchema` (T013) ist die **einzige** Stelle, die beide Formen kennt und umrechnet — eine zweite hieße, zwei Vorstellungen von derselben Zahl zu pflegen
- [X] T011 Test `UiConfigSchemaTest` in `rpg-core/src/test/java/rpg/core/ui/UiConfigSchemaTest.java` — je ein Fall **für jede Zeile** der Regeltabelle aus [contracts/ui-config.md](./contracts/ui-config.md) §2. Geprüft wird nicht nur *dass* es scheitert, sondern **dass die Meldung Datei, Schlüssel und gelesenen Wert nennt** (Prinzip V)
- [X] T012 Test `UiConfigDefaultsTest` in `rpg-core/src/test/java/rpg/core/ui/UiConfigDefaultsTest.java` — die ausgelieferte `ui.yml` lädt ohne Fehler und ergibt genau die Werte aus contracts §1. Ein Schema, dessen eigene Standarddatei nicht durchkommt, ist beim ersten Start eines Betreibers ein Fehlstart
- [X] T013 `UiConfigSchema` in `rpg-core/src/main/java/rpg/core/ui/UiConfigSchema.java` — Bindung und Prüfung nach dem Muster von `StatisticsConfigSchema`; Fail-Fast mit Datei, Schlüssel und Grund (hängt an T010, macht T011/T012 grün)
- [X] T014 [P] `ui.yml` in `rpg-plugin/src/main/resources/ui.yml` anlegen — **wortgleich zu contracts §1, einschließlich der Kommentare**. Die Begründung, warum der Takt eine Sekunde ist, gehört in die Datei, die der Betreiber öffnet, nicht nur in die Spec

### Die Schlüssel

- [X] T015 [P] `UiMessageKeys` in `rpg-core/src/main/java/rpg/core/ui/UiMessageKeys.java` — nach dem Muster von `StatisticsMessageKeys`: Konstanten für Sidebar-Zeilen, Zonenhinweis, Bosskampf, Kanalisierung, Charakterübersicht (Titel, Attributzeile, Ausrüstungszeile, Zustandszeile) und die Abbruchmeldungen. `all()` ist der Grund, aus dem die Klasse existiert — die Startprüfung geht die Liste durch
- [X] T016 Test `UiMessageKeysTest` in `rpg-core/src/test/java/rpg/core/ui/UiMessageKeysTest.java` — `all()` enthält **jeden** deklarierten Schlüssel und keinen doppelt. Eine Konstante, die nicht in `all()` steht, ist genau der Schlüssel, den die Startprüfung übersieht

### Die zwei Nähte aus Constitution III.4

- [X] T017 [P] `HudRenderer` in `rpg-platform/src/main/java/rpg/platform/ui/HudRenderer.java` — die vier Methoden aus [contracts/hud-api.md](./contracts/hud-api.md) §1: `show`, `clear`, `bar`, `clearBar`. Javadoc trägt alle vier Zusagen, namentlich: **nie ein Text, immer ein `MessageKey`** (FR-014 — die Signatur lässt nichts anderes zu, das ist der Punkt), `clearBar` ist **nicht** `bar(..., 0.0)`, und `bar` entscheidet die Rangfolge **nicht selbst**
- [X] T018 [P] `ItemRenderer` in `rpg-platform/src/main/java/rpg/platform/ui/ItemRenderer.java` — `Optional<ItemStack> render(String templateKey, ItemRenderContext context)` plus `record ItemRenderContext(LadderSlot slot, double condition, int amount)` nach [contracts/hud-api.md](./contracts/hud-api.md) §2. **Die Naht spricht B11s Vokabular** (FR-021): `templateKey` wie `ItemStackFactory.create` ihn nimmt, der Zustand als `double` aus `GearCondition.of(slot)`. `ItemId` und `RenderContext` aus dem ersten Entwurf **entfallen** — beide Typen gibt es nirgends, und eine eigene Kennung wäre eine zweite Identität für denselben Gegenstand. Javadoc: was ein Gegenstand *ist*, entscheidet B11; diese Naht entscheidet nur, wie er aussieht, und schreibt in `Items` und `GearConditions` nichts zurück
- [X] T019 [P] In `rpg-platform/src/main/java/rpg/platform/ui/HudRenderer.java` und `ItemRenderer.java` festhalten, **warum beide Nähte in `rpg-platform` liegen und nicht in `rpg-core`** — contracts §2: `ItemRenderer` gibt einen Bukkit-Typ zurück und *kann* nicht tiefer; `HudRenderer` könnte, hätte dort aber weder Aufrufer noch Umsetzung. Ein Umzug nach unten bliebe eine Verschiebung ohne Signaturänderung

### Das Modul

- [X] T020 Test `UiModuleTest` in `rpg-core/src/test/java/rpg/core/ui/UiModuleTest.java` — Start lädt `ui.yml`, eine kaputte Datei bricht ab, `applyReloadedConfig` tauscht **im Ganzen** und die Sprache bleibt davon **unberührt** (contracts §5)
- [X] T021 `UiModule` in `rpg-core/src/main/java/rpg/core/ui/UiModule.java` — `implements Module`, `ID = "ui"`, Abhängigkeiten deklariert, `config()`, `onReload`, `applyReloadedConfig` nach dem Muster von `StatisticsModule` (macht T020 grün)
- [X] T022 `UiModule` in `RpgPlugin.modules()` eintragen (`rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java`) — an der einen Stelle, an der die Verdrahtung steht, nicht in einem statischen Initialisierer
- [X] T023 `UiMessageKeys.all()` in `RpgPlugin.loadMessages` zur `declared`-Liste hinzufügen (`rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` ~Zeile 460) — mit Kommentar, dass B13 die Ausnahme von B09/B10 **nicht** braucht: seine Schlüssel stehen vor dem ersten Lesen einer YAML fest

**Checkpoint**: Die Konfiguration scheitert laut und benennbar, die zwei Nähte stehen, das Modul
startet und lädt nach. Ab hier kann jede Geschichte beginnen.

---

## Phase 3: User Story 1 — Ich sehe jederzeit, wie es mir geht (P1) 🎯 MVP

**Goal**: Drei Flächen, drei Rollen. Die Actionbar trägt die laufenden Werte, die Bossbar das
Situative, das Scoreboard die Sidebar — und jeder Wert steht auf genau einer davon.

**Independent Test**: Anmelden, Werte verändern lassen (Schaden nehmen, Mana verbrauchen,
aufsteigen, Coins verdienen, Zone wechseln) und prüfen, dass jede Änderung auf **genau einer**
Fläche erscheint und auf keiner zweiten.

### Die Regeln, serverlos und zuerst

- [X] T024 [P] [US1] Test `HudSurfaceAssignmentTest` in `rpg-core/src/test/java/rpg/core/ui/HudSurfaceAssignmentTest.java` — der Test geht **jeden** anzeigbaren Wert durch und zählt die Flächen, auf denen er landet. Zwei wären eine doppelte Wahrheit auf dem Bildschirm (FR-001, FR-001c). Zwei Fälle gehören ausdrücklich dazu: **Fortschritt steht nicht mehr auf der Actionbar** (FR-002a) und **Level/Erfahrung stehen genau einmal**, nämlich auf der Sidebar. Die Vanilla-Erfahrungsleiste ist die eine zugelassene Ausnahme (FR-001a) und steht als solche **benannt** im Test — nicht als stillschweigend übergangener Fall, sonst deckt der Test später auch eine zweite
- [X] T025 [P] [US1] Test `BossBarPriorityTest` in `rpg-core/src/test/java/rpg/core/ui/BossBarPriorityTest.java` — alle drei Anlässe zugleich → `CHANNELLING` gewinnt; nur Bosskampf und Zonenname → Bosskampf; einzeln → jeder für sich (FR-004)
- [X] T026 [P] [US1] Test `BossBarPriorityReturnTest` in `rpg-core/src/test/java/rpg/core/ui/BossBarPriorityReturnTest.java` — Kanalisierung endet, Bosskampf besteht noch → **der Bosskampf ist wieder da, ohne dass ihn jemand neu gemeldet hat.** Das ist der Test, der belegt, dass keine Warteschlange nötig ist: der Zustand besteht fort, das Ereignis war nur verdeckt
- [X] T027 [P] [US1] Test `BossBarNoReplayTest` in `rpg-core/src/test/java/rpg/core/ui/BossBarNoReplayTest.java` — ein Zonenname, der während eines Bosskampfs anfällt, **entfällt und wird nicht nachgeholt** (FR-004a). Nachgereicht wäre er eine Meldung über etwas, das längst vorbei ist
- [X] T028 [P] [US1] Test `BossBarSingletonTest` in `rpg-core/src/test/java/rpg/core/ui/BossBarSingletonTest.java` — es entsteht **genau eine** Bossbar je Spieler, egal wie viele Anlässe eintreffen (FR-004b)
- [X] T029 [P] [US1] Test `BarProgressTest` in `rpg-core/src/test/java/rpg/core/ui/BarProgressTest.java` — Füllstand gegen eine gestellte Uhr bei `startedAt`, in der Mitte, bei `dueAt` **und darüber hinaus**. Nie außerhalb von `[0,1]`; `startedAt == dueAt` ergibt keine Division durch null
- [X] T030 [P] [US1] Test `SidebarLinesTest` in `rpg-core/src/test/java/rpg/core/ui/SidebarLinesTest.java` — jede Zeile ist ein `MessageKey` mit Platzhaltern und **nie ein Text**; am Höchstlevel trägt die Erfahrungszeile den eigenen Schlüssel, weil `4120/0` wie ein Fehler aussähe (`ProgressView.atMaxLevel`)
- [X] T031 [P] [US1] Test `HudVisibilityTest` in `rpg-core/src/test/java/rpg/core/ui/HudVisibilityTest.java` — ein Spieler **ohne gewählten Charakter** bekommt auf keiner der drei Flächen einen Wert (FR-008). Nullen, die wie echte Werte aussehen, sind schlimmer als nichts
- [X] T032 [US1] `BossBarPriority` in `rpg-core/src/main/java/rpg/core/ui/BossBarPriority.java` — die Rangfolge **an einer Stelle** (contracts §1, Zusage 4). Nimmt die anstehenden Anlässe, gibt den gewinnenden zurück; kein veränderlicher Zustand, keine Warteschlange (macht T025–T028 grün)
- [X] T033 [US1] `BarProgress` in `rpg-core/src/main/java/rpg/core/ui/BarProgress.java` — `startedAt`, `dueAt`, `fraction(now)` auf `[0,1]` begrenzt. **Eine Rechnung, keine Aufgabe** (Constitution II.2) — der Javadoc sagt das, damit niemand später einen Ticker danebenstellt (macht T029 grün)
- [X] T034 [US1] `SidebarLines` in `rpg-core/src/main/java/rpg/core/ui/SidebarLines.java` — welche Zeile welchen Wert trägt, nach [data-model.md](./data-model.md) §2 (macht T030 und zusammen mit T006 auch T024 grün)
- [X] T035 [US1] `HudVisibility` in `rpg-core/src/main/java/rpg/core/ui/HudVisibility.java` — die Regel, wann eine Fläche für einen Halter überhaupt etwas zeigt: Charakter gewählt **und** Fläche eingeschaltet (macht T031 grün). **Nicht T024** — die Flächenzuordnung kommt aus `HudSurface` (T006) und `SidebarLines` (T034); die Sichtbarkeitsregel sagt nur, *ob* gezeichnet wird, nicht *wo*

### Die Umsetzung: der eine Takt

- [X] T036 [US1] Test `HudTickIsOneTest` in `rpg-platform/src/test/java/rpg/platform/ui/HudTickIsOneTest.java` — über mehrere Durchläufe bleibt es bei **einer** eingeplanten Aufgabe, egal wie viele Spieler online sind (FR-012, Constitution II.2). Der Test zählt die Einplanungen, nicht die Pakete
- [X] T037 [US1] Test `HudTickSelfReschedulesTest` in `rpg-platform/src/test/java/rpg/platform/ui/HudTickSelfReschedulesTest.java` — der Takt bewaffnet sich am Ende jedes Durchlaufs neu (ADR-007), und **ein scheiternder Durchlauf hört auf, statt sich aufzustauen**
- [X] T038 [US1] `HudTick` in `rpg-platform/src/main/java/rpg/platform/ui/HudTick.java` — **die Erweiterung des Takts aus `StatusActionBar.startRefresh`, nicht ein zweiter daneben** (R1, contracts §3). Ein Durchlauf über alle Spieler, asynchron, `runAsyncDelayed` plus Selbsteinplanung; Intervall aus `UiConfig.tick` (macht T036, T037 grün)
- [X] T039 [US1] Im Javadoc von `rpg-platform/src/main/java/rpg/platform/ui/HudTick.java` die Regel aus R2 festhalten: aus diesem asynchronen Kontext ist `runSyncOnEntity` **nur für einen Spieler** richtig; für alles andere `runSyncAtLocation`. Mit dem Verweis auf T112 — der Fehler bleibt grün und zeichnet nur manchmal

### Die Umsetzung: die drei Flächen

- [X] T040 [US1] Test `PaperHudRendererTest` in `rpg-platform/src/test/java/rpg/platform/ui/PaperHudRendererTest.java` (MockBukkit) — jede der vier Methoden trifft die richtige Vanilla-Fläche; ein **abgemeldeter Spieler** ist ein normaler Ausgang und keine Ausnahme (contracts §1, Zusage 1)
- [X] T041 [US1] Test `HudRendererNeverThrowsTest` in `rpg-platform/src/test/java/rpg/platform/ui/HudRendererNeverThrowsTest.java` — ein Fehler beim Senden kostet **keinen Tick**: er wird lokal gefangen und protokolliert, wie `StatusActionBar` es heute schon tut (Constitution VI)
- [X] T042 [US1] `PaperHudRenderer` in `rpg-platform/src/main/java/rpg/platform/ui/PaperHudRenderer.java` — die Vanilla-Umsetzung der Naht; löst `MessageKey` plus Platzhalter über `Messages` auf und sendet (macht T040, T041 grün)
- [X] T043 [US1] Test `PaperBossBarTest` in `rpg-platform/src/test/java/rpg/platform/ui/PaperBossBarTest.java` (MockBukkit) — je Spieler entsteht **eine** Bossbar und sie wird wiederverwendet statt gestapelt; `clearBar` entfernt sie, `bar(..., 0.0)` lässt sie stehen (FR-004b, contracts §1 Zusage 3)
- [X] T044 [US1] `PaperBossBar` in `rpg-platform/src/main/java/rpg/platform/ui/PaperBossBar.java` — eine Bossbar je Spieler, an den Spieler gebunden und nicht global (Constitution I.6); Titel aus Schlüssel, Füllstand aus `BarProgress` beziehungsweise `CombatStatusSource.Status.fraction()` (macht T043 grün)
- [X] T044a [P] [US1] Test `BossBarClearedOnQuitTest` in `rpg-platform/src/test/java/rpg/platform/ui/BossBarClearedOnQuitTest.java` (MockBukkit) — ein Spieler mit stehender Bossbar meldet sich ab: die Leiste wird entfernt, und beim Wiederanmelden steht **keine alte** (FR-004c). Der Edge Case „Abmeldung mitten in einer Anzeige" nannte drei Dinge — Cooldown (T097) und Schadenszahl (T107) hatten je einen Test, die Bossbar keinen
- [X] T044b [US1] Abmelden in `rpg-platform/src/main/java/rpg/platform/ui/PaperBossBar.java` behandeln — die Leiste des ausscheidenden Spielers wird entfernt und ihr Eintrag aus der Zuordnung genommen (macht T044a grün). **Beides, nicht nur das erste**: eine entfernte Leiste, deren Eintrag stehen bleibt, ist ein Leck, das erst nach Stunden auffällt
- [X] T045 [US1] Test `PaperSidebarTest` in `rpg-platform/src/test/java/rpg/platform/ui/PaperSidebarTest.java` (MockBukkit) — die vier Zeilen stehen in der Reihenfolge aus `SidebarLines`; ändert sich **eine** Zeile, wird **nur** sie neu gesetzt (Akzeptanzszenario US1.3)
- [X] T046 [US1] `PaperSidebar` in `rpg-platform/src/main/java/rpg/platform/ui/PaperSidebar.java` — das Scoreboard als Sidebar mit Level, Erfahrung, Coins und Zone (FR-005; macht T045 grün)

### `StatusActionBar` zieht hinter die Naht

- [X] T047 [US1] Test `StatusActionBarKeepsEveryValueTest` in `rpg-platform/src/test/java/rpg/platform/hud/StatusActionBarKeepsEveryValueTest.java` — **vor** dem Umbau geschrieben: **Leben, Mana und Verteidigung** stehen nach dem Umzug noch genauso auf der Zeile wie vorher (FR-023). Der Test ist die Zusage, dass beim Umzug kein Wert **versehentlich** verschwindet
- [X] T047a [US1] Test `ActionBarHasNoProgressTest` in `rpg-platform/src/test/java/rpg/platform/hud/ActionBarHasNoProgressTest.java` — die Gegenprobe zu T047: der Fortschritt ist **absichtlich** weg (FR-002a). Die Actionbar-Zeile enthält weder Level noch Erfahrung noch die Schwelle. **Ohne diesen Test ist T047 die halbe Wahrheit** — er allein kann „vergessen" nicht von „entfernt" unterscheiden, und genau diese Unterscheidung ist die Entscheidung aus I1
- [X] T048 [US1] `StatusActionBar` in `rpg-platform/src/main/java/rpg/platform/hud/StatusActionBar.java` hinter `HudRenderer` ziehen — sie sendet nicht mehr selbst, sondern ruft `show(playerId, ACTION_BAR, key, values)`. **Ihr Javadoc kündigt genau diesen Schritt an**; der Kommentar wird von „B13 wird" auf „B13 hat" umgeschrieben und verweist auf FR-020/FR-023
- [X] T049 [US1] `startRefresh` in `rpg-platform/src/main/java/rpg/platform/hud/StatusActionBar.java` **entfällt** zugunsten von `HudTick` — der Takt zieht um, die Zeichnung bleibt. Im Javadoc festhalten, dass es weiterhin **ein** Takt ist und keiner dazugekommen ist (R1)
- [X] T050 [US1] `StatusActionBar.REFRESH` durch `UiConfig.tick` ersetzen — die Begründung der Konstante („eine Sekunde ist unter den ~2 s, nach denen Minecraft ausblendet") wandert in den Kommentar von `hud.tick-ms` in `ui.yml`, damit sie dort steht, wo jemand den Wert ändert. Der Wert wird **je Durchlauf** gelesen, nicht beim Start festgehalten (FR-013c) — siehe T061a
- [X] T050a [US1] `progressText` aus `rpg-platform/src/main/java/rpg/platform/hud/StatusActionBar.java` **entfernen** (heute ~Zeile 202) und den Platzhalter `{progress}` aus der Actionbar-Zeile in `messages.yml` nehmen — Level, Erfahrung und Schwelle trägt ab jetzt die Sidebar (FR-002a; macht T047a grün). **Das ist die eine Stelle, an der B13 einer Fläche etwas wegnimmt statt hinzuzufügen.** `ProgressView.atMaxLevel()` wird dabei nicht vergessen, sondern **wandert** nach `SidebarLines` (T034) — die Unterscheidung „am Höchstlevel keine Schwelle" ist derselbe Fall, nur eine Fläche weiter
- [X] T050b [US1] Die zwei vorhandenen Abonnements in `StatusActionBar` (`ProgressChangedEvent` ~Zeile 95, `LevelUpEvent` ~Zeile 98) nach `HudRefresh` (T056b) **verschieben, nicht kopieren** — sie zeichnen ab jetzt den ganzen HUD des Spielers und nicht mehr nur seine Actionbar. Zwei Abonnements auf dasselbe Ereignis nebeneinander hieße: zweimal zeichnen, und welcher Aufruf zuletzt kommt, hängt an der Registrierungsreihenfolge

### Woher die Bossbar ihre drei Anlässe bekommt

- [X] T051 [P] [US1] Test `ZoneNoticeSourceTest` in `rpg-platform/src/test/java/rpg/platform/ui/ZoneNoticeSourceTest.java` — ein `ZoneChangedEvent` mit `to` erzeugt den Anlass `ZONE_NAME`; ein Wechsel in die Wildnis (`to` leer) erzeugt den Wildnis-Schlüssel und keinen leeren Titel
- [X] T052 [P] [US1] Test `BossFightSourceTest` in `rpg-platform/src/test/java/rpg/platform/ui/BossFightSourceTest.java` — ein `DamageDealtEvent` auf ein Ziel, das `MobKinds.ofEntity` als `boss` meldet, erzeugt `BOSS_FIGHT`; auf eine gewöhnliche Kreatur erzeugt es **nichts** (R3)
- [X] T053 [P] [US1] Test `ChannellingSourceTest` in `rpg-platform/src/test/java/rpg/platform/ui/ChannellingSourceTest.java` — eine `RunningAbility` in `WINDING_UP` erzeugt `CHANNELLING` mit dem Füllstand aus `startedAt`/`dueAt`; in `RUNNING` ohne Kanalisierungsdauer erzeugt sie keinen (R4)
- [X] T054 [US1] `ZoneNoticeSource` in `rpg-platform/src/main/java/rpg/platform/ui/ZoneNoticeSource.java` — hört auf `ZoneChangedEvent`, hält den Anlass für `zoneNoticeDuration` und lässt ihn dann verfallen (FR-003; macht T051 grün)
- [X] T055 [US1] `BossFightSource` in `rpg-platform/src/main/java/rpg/platform/ui/BossFightSource.java` — `DamageDealtEvent.targetId` → `MobKinds.ofEntity` → `CombatStatusSource.statusOf`. **Keine neue Abfrage in B10** (R3, verworfene Alternative); das Ende des Kampfes beantwortet `CombatState.isInCombat` (macht T052 grün)
- [X] T056 [US1] `ChannellingSource` in `rpg-platform/src/main/java/rpg/platform/ui/ChannellingSource.java` — liest `RunningAbility` und rechnet den Balken über `BarProgress`. **Zeitstempelbasiert, ohne eigene Aufgabe** (R4; macht T053 grün)

### Woher die Sidebar ihre Änderungen bekommt (FR-009)

Der Sammeltakt allein erfüllt FR-009 **nicht**: „unmittelbar" und „bis zu eine Sekunde später" sind
zwei verschiedene Zusagen. Drei der vier Sidebar-Zeilen haben ein Ereignis; die vierte nicht.

- [X] T056a [P] [US1] Test `SidebarUpdatesOnEventTest` in `rpg-platform/src/test/java/rpg/platform/ui/SidebarUpdatesOnEventTest.java` (MockBukkit) — `ProgressChangedEvent`, `LevelUpEvent` und `ZoneChangedEvent` zeichnen die Sidebar neu, **ohne dass der Takt läuft** (FR-009). Der Test hält den Takt ausdrücklich an; sonst misst er ihn statt der Ereignisse und wäre auch dann grün, wenn es keinen einzigen Ereignispfad gäbe
- [X] T056b [US1] `HudRefresh` in `rpg-platform/src/main/java/rpg/platform/ui/HudRefresh.java` — **ein** Eingang „zeichne diesen Spieler jetzt neu", den sowohl `HudTick` als auch die Ereignisabonnements benutzen. Kein zweiter Zeichenweg neben dem Takt: derselbe Aufruf, nur ein anderer Anlass (macht T056a grün)
- [X] T056c [P] [US1] Test `CoinLineFollowsTickTest` in `rpg-platform/src/test/java/rpg/platform/ui/CoinLineFollowsTickTest.java` — **die Zusage, die dieser Block nicht gibt** (FR-009a): eine Coin-Buchung zeichnet **nicht** sofort, sondern beim nächsten Takt. Der Test hält die Grenze fest, statt sie als Fehler zu hinterlassen, den später jemand für einen Bug hält
- [X] T056d [US1] Im Javadoc von `rpg-platform/src/main/java/rpg/platform/ui/HudRefresh.java` festhalten, **warum die Coins fehlen**: `rpg.core.currency` führt keinen Ereignistyp — nur `CoinLedger`, `LedgerEntry` und `BookingResult`. Ein Ereignis dort nachzurüsten wäre der Eingriff in einen fremden Block, den FR-024, FR-070 und FR-071 an drei anderen Stellen ablehnen. **Wenn die Sekunde stört, gehört das Ereignis in B08b** — mit eigenem Namen und eigener Aufgabe, nicht als Nebenwirkung von B13

### Sparsamkeit: nur bei Änderung, und abgeschaltet ist kostenlos

- [X] T057 [US1] Test `SendsOnlyOnChangeTest` in `rpg-platform/src/test/java/rpg/platform/ui/SendsOnlyOnChangeTest.java` — zwei Durchläufe ohne Wertänderung senden **einmal** (für die Actionbar, die sonst ausblenden würde) und sonst **nichts** (FR-013). Gezählt wird mit `RecordingHudRenderer` aus T005
- [X] T058 [US1] Test `DisabledSurfaceCostsNothingTest` in `rpg-platform/src/test/java/rpg/platform/ui/DisabledSurfaceCostsNothingTest.java` — mit `sidebar.enabled: false` entstehen **keine** Scoreboard-Pakete. Der Test prüft ausdrücklich „gar keine", nicht „unsichtbare" (FR-013a, SC-010)
- [X] T059 [US1] Test `NoCharacterNoValuesTest` in `rpg-platform/src/test/java/rpg/platform/ui/NoCharacterNoValuesTest.java` (MockBukkit) — ein angemeldeter Spieler **vor** der Charakterwahl bekommt auf keiner Fläche Werte (FR-008, Akzeptanzszenario US1.5)
- [X] T060 [US1] Änderungserkennung je Spieler und Fläche in `rpg-platform/src/main/java/rpg/platform/ui/HudTick.java` — der zuletzt gesendete Inhalt wird verglichen, bevor gesendet wird; die Actionbar ist die eine Ausnahme, weil das Ausblenden es erzwingt (FR-011, FR-013; macht T057 grün)
- [X] T061 [US1] Abschaltung in `rpg-platform/src/main/java/rpg/platform/ui/HudTick.java` **vor** der Berechnung auswerten, nicht danach — eine abgeschaltete Fläche wird gar nicht erst zusammengesetzt (macht T058 grün). Der Javadoc nennt den Unterschied: nach der Berechnung zu verwerfen wäre unsichtbar, nicht kostenlos
- [X] T061a [US1] Test `ReloadReachesTickTest` in `rpg-platform/src/test/java/rpg/platform/ui/ReloadReachesTickTest.java` — nach `applyReloadedConfig` mit `sidebar.enabled: false` **und geändertem Takt** arbeitet der laufende `HudTick` beim nächsten Durchlauf mit den neuen Werten (FR-013c). Ein Takt, der `UiConfig` beim Start festhält, meldet Erfolg und ändert nichts — der Betreiber sieht dann eine Umstellung, die nicht stattfindet
- [X] T061b [US1] `HudTick` in `rpg-platform/src/main/java/rpg/platform/ui/HudTick.java` die Konfiguration **je Durchlauf** aus `UiModule.config()` holen statt sie im Feld zu halten (macht T061a grün). Im Javadoc: ein einmal gelesener Wert und ein nachladbarer Wert sehen im Code gleich aus und unterscheiden sich erst beim Betreiber
- [X] T062 [US1] `HudVisibility` in `rpg-platform/src/main/java/rpg/platform/ui/HudTick.java` anwenden (macht T059 grün)

### Zusammenhalten

- [X] T063 [US1] `PaperHudRenderer`, `HudTick`, `PaperBossBar` und `PaperSidebar` in `RpgPlugin` verdrahten (`rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java`) — **an derselben Stelle, an der `StatusActionBar` heute verdrahtet ist**, damit die eine Fläche einen Aufrufer behält und nicht zwei bekommt
- [X] T064 [US1] Die drei Anlassquellen in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` am `EventBus` anmelden und in `HudTick` einhängen
- [X] T064a [US1] `HudRefresh` in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` an `ProgressChangedEvent`, `LevelUpEvent` und `ZoneChangedEvent` anmelden (FR-009) — **an derselben Stelle wie T063**, und die zwei aus `StatusActionBar` übernommenen Abonnements (T050b) werden dabei dort abgemeldet, nicht doppelt geführt. Der Coin-Stand bekommt hier bewusst nichts (FR-009a)
- [X] T065 [US1] Die Sidebar-Schlüssel in `rpg-plugin/src/main/resources/messages.yml` ergänzen — Level, Erfahrung, Erfahrung am Höchstlevel, Coins, Zone, Wildnis; englisch (Prinzip VIII)
- [X] T066 [US1] Die Bossbar-Schlüssel in `messages.yml` ergänzen — Zonenhinweis, Bosskampf (Platzhalter: Name), Kanalisierung (Platzhalter: Fähigkeitsname)
- [X] T067 [US1] Test `HudSurfacesIntegrationTest` in `rpg-platform/src/test/java/rpg/platform/ui/HudSurfacesIntegrationTest.java` (MockBukkit) — ein Spieler durchläuft Schaden, Manaverbrauch, Aufstieg, Coingewinn und Zonenwechsel; jede Änderung landet auf **genau einer** Fläche. Das ist der *Independent Test* dieser Geschichte, als Test geschrieben
- [X] T068 [US1] Test `BossBarContentionTest` in `rpg-platform/src/test/java/rpg/platform/ui/BossBarContentionTest.java` (MockBukkit) — Zonenwechsel und Bosskampf treffen zusammen: der Bosskampf gewinnt und der Zonenname wird nicht dazwischengeschoben (Akzeptanzszenario US1.4). **Beide Eintreffreihenfolgen werden geprüft** — genau das ist der Fehler, den FR-004 ausschließt

**Checkpoint**: Die drei Flächen tragen, was ihnen gehört, und nichts doppelt — die Actionbar hat
den Fortschritt **abgegeben** (T050a), die Sidebar zeichnet auf Ereignis und nicht erst im Takt
(T056b), und eine abgemeldete Bossbar ist weg (T044b). Ab hier ist der Block für einen Spieler
sichtbar nützlich — das ist der MVP. **Die MVP-Grenze liegt jetzt bei T068**, unverändert; die
fünfzehn ergänzten Aufgaben liegen bis auf T143a/T143b und T150a alle davor.

---

## Phase 4: User Story 2 — Meine Charakterübersicht, an einer Stelle (P2)

**Goal**: Ein Fenster, das jedes Attribut, die Ausrüstung mit ihrem Zustand, Klasse, Level und
Coin-Stand des **aktiven** Charakters zeigt.

**Independent Test**: `/char` eingeben, Ausrüstung wechseln, erneut eingeben — die Werte haben sich
mitbewegt.

### Der Inhalt, serverlos

- [X] T069 [P] [US2] Test `CharacterSheetShowsEveryAttributeTest` in `rpg-core/src/test/java/rpg/core/ui/CharacterSheetShowsEveryAttributeTest.java` — die Übersicht zeigt **jedes** `Attribute`, ermittelt über `Attribute.values()` und **nicht** gegen eine festgeschriebene Zahl (R10, FR-050). Heute sind es zehn; ein elftes darf den Test nicht stillschweigend halbieren
- [X] T070 [P] [US2] Test `CharacterSheetIsOneCharacterTest` in `rpg-core/src/test/java/rpg/core/ui/CharacterSheetIsOneCharacterTest.java` — bei drei Charakteren erscheint der **aktive**, und keine Summe über drei (FR-053)
- [X] T071 [P] [US2] Test `CharacterSheetRebuildsOnRevisionTest` in `rpg-core/src/test/java/rpg/core/ui/CharacterSheetRebuildsOnRevisionTest.java` — bei gleicher `StatSnapshot.revision()` wird **nicht** neu aufgebaut, bei höherer schon (FR-054). Die Revision ist die Ungültigkeitsmarke; niemand erfindet eine zweite
- [X] T072 [P] [US2] Test `CharacterSheetPercentAttributeTest` in `rpg-core/src/test/java/rpg/core/ui/CharacterSheetPercentAttributeTest.java` — `ABILITY_COOLDOWN` ist das einzige prozentuale Attribut (`AttributeKind.PERCENT`) und wird als Prozentwert dargestellt, nicht als rohe Zahl
- [X] T073 [US2] `CharacterSheet` in `rpg-core/src/main/java/rpg/core/ui/CharacterSheet.java` — `characterId`, `attributes`, `revision`, `equipment`, `conditions`, `level`, `coins`, `classKey` nach [data-model.md](./data-model.md) §5 (macht T069–T072 grün)
- [X] T074 [US2] `CharacterSheets` in `rpg-core/src/main/java/rpg/core/ui/CharacterSheets.java` — baut die Sicht aus den fremden Nähten zusammen: `StatSnapshot.get` (B04), `Items` und `GearConditions` (B11), `ProgressView` (B06), `Currency` (B08b), Klasse (B07). **Liest nur, spiegelt nichts** (data-model §6)

### Der Rahmen und das Fenster

- [X] T075 [P] [US2] Test `MenuFrameTest` in `rpg-platform/src/test/java/rpg/platform/ui/MenuFrameTest.java` (MockBukkit) — Titel aus Schlüssel, Größe, Rahmenplätze, Schließen. Aus **reinen Vanilla-Materialien** (FR-056, ADR-005)
- [X] T076 [US2] `MenuFrame` in `rpg-platform/src/main/java/rpg/platform/ui/MenuFrame.java` — der gemeinsame Rahmen für **diese drei** Fenster. Javadoc hält R10 fest: die zwei fremden Fenster (`ClassSelectionMenu`, B12s Statistikfenster) bekommen ihn **nicht** aufgezwungen, sie bleiben unabhängig (FR-070, FR-071)
- [X] T077 [P] [US2] Test `CharacterSheetMenuTest` in `rpg-platform/src/test/java/rpg/platform/ui/CharacterSheetMenuTest.java` (MockBukkit) — die Übersicht zeigt zehn Attributzeilen, die angelegte Ausrüstung und je Stück den Zustand aus `GearConditions` (FR-050 bis FR-052)
- [X] T078 [P] [US2] Test `CharacterSheetCachesTest` in `rpg-platform/src/test/java/rpg/platform/ui/CharacterSheetCachesTest.java` (MockBukkit) — zweimal öffnen ohne Wertänderung baut den Inhalt **einmal** auf (FR-054)
- [X] T079 [US2] `CharacterSheetMenu` in `rpg-platform/src/main/java/rpg/platform/ui/CharacterSheetMenu.java` — auf `MenuFrame`, Inhalt aus `CharacterSheets`, Gegenstände über `ItemRenderer` (macht T077, T078 grün)
- [X] T080 [US2] `PaperItemRenderer` in `rpg-platform/src/main/java/rpg/platform/ui/PaperItemRenderer.java` — die Umsetzung der zweiten Naht. **Sie baut nichts zweit** (FR-021a): `ItemStackFactory.create(templateKey, amount)` liefert den Gegenstand, `GearConditionDisplay.paint(stack, condition)` den Zustandsbalken — beide stehen in `rpg-platform/src/main/java/rpg/platform/item/` und tun das bereits. Ein eigener Lore-Aufbau daneben wären zwei Renderer für denselben Gegenstand, und die driften auseinander, sobald einer angefasst wird. Ursprünglich stand hier: Anzeigename, Lore und Zustandsbalken aus `Items` und `GearConditions`, ohne in eines von beiden zu schreiben (contracts §2)
- [X] T081 [P] [US2] Test `PaperItemRendererTest` in `rpg-platform/src/test/java/rpg/platform/ui/PaperItemRendererTest.java` (MockBukkit) — Anzeigename und Lore kommen aus Schlüsseln, der Zustand aus B11; ein unbekannter Gegenstand ist ein normaler Ausgang und keine Ausnahme

### Der Charakterwechsel schließt

- [X] T082 [P] [US2] Test `CharacterSwitchClosesSheetTest` in `rpg-platform/src/test/java/rpg/platform/ui/CharacterSwitchClosesSheetTest.java` (MockBukkit) — bei offenem Fenster wechselt der Spieler den Charakter: das Fenster **schließt** und wird **nicht** neu aufgebaut (FR-055). Der Test prüft ausdrücklich beides, weil ein stiller Neuaufbau der plausiblere Fehler ist
- [X] T083 [US2] `CharacterSheetListener` in `rpg-platform/src/main/java/rpg/platform/ui/CharacterSheetListener.java` — Klicks abfangen (die Übersicht ist zum Lesen da) und beim Charakterwechsel schließen (macht T082 grün)

### Der Aufrufweg

- [X] T084 [US2] `CharacterSheetCommand` in `rpg-plugin/src/main/java/rpg/plugin/command/CharacterSheetCommand.java` — `/char` ohne Argument öffnet die Übersicht des **aktiven** Charakters (FR-057). Nach dem Muster von `CoinsCommand`: parst, prüft ein Recht, ruft — **keine Regel wächst hier** (FR-058)
- [X] T085 [US2] Im Javadoc von `CharacterSheetCommand` festhalten, dass es **vorläufig** ist — Rechtebaum, Tab-Completion und die einheitliche Kommandostruktur bleiben B14, wie bei `/coins` (ADR-028) und `/stats`/`/top` (B12)
- [X] T086 [US2] `char` in `rpg-plugin/src/main/resources/plugin.yml` eintragen — Beschreibung, Usage, Recht `rpg.ui.character`; das Recht mit `default: true`, weil die eigene Übersicht anzusehen kein administrativer Akt ist (nach dem Muster von `rpg.statistics.own`)
- [X] T087 [US2] Kommando in `RpgPlugin` registrieren (`rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java`)
- [X] T088 [US2] Die Schlüssel der Übersicht in `messages.yml` ergänzen — Titel, Attributzeile (Platzhalter: Etikett, Wert), Attributzeile prozentual, Ausrüstungszeile, Zustandszeile, leerer Ausrüstungsplatz, Klasse, Level, Coins
- [ ] T089 [P] [US2] Test `CharacterSheetCommandTest` in `rpg-plugin/src/test/java/rpg/plugin/command/CharacterSheetCommandTest.java` (MockBukkit) — `/char` ohne Argument öffnet; von der Konsole aufgerufen antwortet es mit einem Schlüssel statt zu werfen; ohne gewählten Charakter ebenso

**Checkpoint**: Ein Spieler sieht, was sein Charakter ist — zehn Attribute, die Ausrüstung, ihr
Zustand, Klasse, Level und Coins, an einer Stelle.

---

## Phase 5: User Story 3 — Ich sehe, wann ich wieder darf (P2)

**Goal**: Das Vanilla-Cooldown-Overlay läuft über dem Fähigkeits-Item im Slot und endet mit dem
tatsächlichen Cooldown aus B08.

**Independent Test**: Fähigkeit auslösen, Slot beobachten, nach Ablauf erneut auslösen.

### Die Startprüfung — die Regel, nicht die Zahlenwahl

- [X] T090 [P] [US3] Test `MaterialUniquenessTest` in `rpg-core/src/test/java/rpg/core/ui/MaterialUniquenessTest.java` — zwei **aktive** Fähigkeiten einer Klasse auf demselben `item()` brechen den Start ab; die Meldung nennt Klasse, **beide** Fähigkeiten und das geteilte Material (FR-032)
- [X] T091 [P] [US3] Test `MaterialUniquenessCoversMarkersTest` in `rpg-core/src/test/java/rpg/core/ui/MaterialUniquenessCoversMarkersTest.java` — **der Fall, ohne den der Test die Hälfte prüft** (R6): eine passive Fähigkeit mit *mehreren* Markierungsmaterialien (`items()`, Mehrzahl — der Magier belegt mit *Rise & Fall* zwei Slots aus einer Fähigkeit) kollidiert mit einer aktiven; die Prüfung geht über die **Vereinigung** aus `item()` und `items()`
- [X] T092 [P] [US3] Test `MaterialUniquenessIsPerClassTest` in `rpg-core/src/test/java/rpg/core/ui/MaterialUniquenessIsPerClassTest.java` — dasselbe Material in **zwei verschiedenen** Klassen ist in Ordnung und bricht **nicht** ab. Ein Spieler trägt nur ein Loadout; klassenübergreifend zu prüfen wäre eine erfundene Grenze
- [X] T093 [US3] `MaterialUniqueness` in `rpg-core/src/main/java/rpg/core/ui/MaterialUniqueness.java` — geht `AbilityRegistry.abilitiesOf(CharacterClass)` je Klasse durch und vergleicht die Vereinigung aus `item()` und `items()`; Fail-Fast mit Datei, Schlüssel und Grund (macht T090–T092 grün)
- [X] T094 [US3] Im Javadoc von `rpg-core/src/main/java/rpg/core/ui/MaterialUniqueness.java` festhalten, **warum beim Start und nicht zur Laufzeit** (R6, verworfene Alternative): im Spiel sähe der Fehler wie ein Balancing-Zufall aus, und die Doppelung zur Laufzeit zu erkennen und dann nichts anzuzeigen verschöbe einen Konfigurationsfehler in eine stille Anzeigelücke
- [X] T095 [US3] `MaterialUniqueness` in `UiModule.start` aufrufen (`rpg-core/src/main/java/rpg/core/ui/UiModule.java`) — mit der Begründung im Kommentar, dass die Prüfung nach dem Laden von `abilities.yml` und `classes.yml` laufen muss, weil sie beide braucht

### Das Overlay

- [X] T096 [P] [US3] Test `AbilityCooldownOverlayTest` in `rpg-platform/src/test/java/rpg/platform/ui/AbilityCooldownOverlayTest.java` (MockBukkit) — eine ausgelöste Fähigkeit setzt das Overlay auf **ihrem** Material und auf keinem zweiten (FR-030); die Dauer kommt aus `AbilityRegistry.remainingCooldown` und wird **nicht zweitgerechnet** (FR-031, R5)
- [X] T097 [P] [US3] Test `CooldownSurvivesRelogTest` in `rpg-platform/src/test/java/rpg/platform/ui/CooldownSurvivesRelogTest.java` (MockBukkit) — nach Ab- und Anmeldung steht die **verbleibende** Zeit da, nicht die volle und nicht keine (FR-033). Alle drei Fälle werden geprüft, weil „keine" der stille und „volle" der laute Fehler ist
- [X] T098 [US3] `AbilityCooldownOverlay` in `rpg-platform/src/main/java/rpg/platform/ui/AbilityCooldownOverlay.java` — liest die Restzeit über `AbilityRegistry.remainingCooldown(characterId, abilityId)` und setzt das Vanilla-Overlay auf dem Material; welcher Slot welche Fähigkeit ist, beantwortet `AbilityItemTag` (macht T096 grün)
- [X] T099 [US3] Wiederherstellung beim Anmelden in `rpg-platform/src/main/java/rpg/platform/ui/AbilityCooldownOverlay.java` — beim Betreten wird für jede belegte Fähigkeit die Restzeit einmal gesetzt (macht T097 grün). **Nicht im Takt**, sondern beim Anmelden: ein laufendes Overlay hält sich von selbst
- [X] T100 [US3] Overlay in `RpgPlugin` verdrahten (`rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java`) — **`AbilityHotbar` wird dabei nicht angefasst** (FR-024); das Overlay setzt auf, was B08 legt, und legt nichts selbst
- [X] T101 [US3] Test `MaterialUniquenessMessageTest` in `rpg-core/src/test/java/rpg/core/ui/MaterialUniquenessMessageTest.java` schreiben — er prüft, dass die Abbruchmeldung Klasse, **beide** Fähigkeiten und das Material nennt. Sie kommt **nicht** in `messages.yml`: Startmeldungen gehen ins Log und nicht an einen Spieler, und ein Message-Schlüssel für etwas, das nie beim Spieler ankommt, wäre ein Schlüssel, den der Wächter aus T137 später zu Recht nicht findet

**Checkpoint**: Ein Spieler sieht am Slot, wann er wieder darf — ohne Zahl und ohne Textzeile
(SC-007).

---

## Phase 6: User Story 4 — Ich sehe, was mein Treffer angerichtet hat (P2)

**Goal**: Eine kurzlebige Zahl am Trefferort, nur für den Verursacher, aus der **gebündelten**
Zahl von B05.

**Independent Test**: Eine Kreatur schlagen und die aufsteigende Zahl mit dem tatsächlich zugefügten
Schaden vergleichen.

> ⚠️ **Der vorsichtigste Teil des Blocks.** Dieselbe Technik wie B12s Hologramm, in drei Punkten
> genau umgekehrt (R7) — und die eine Stelle, an der die Falle aus R2 wirklich zuschlägt.

- [X] T102 [P] [US4] Test `DamageNumberTest` in `rpg-core/src/test/java/rpg/core/ui/DamageNumberTest.java` — `viewerId`, `position`, `amount`, `hitCount`, `lethal`, `expiresAt`; die Lebensdauer kommt aus der Konfiguration und ist positiv (FR-043)
- [X] T103 [P] [US4] Test `DamageNumberUsesAggregatedAmountTest` in `rpg-core/src/test/java/rpg/core/ui/DamageNumberUsesAggregatedAmountTest.java` — zwanzig Schläge ergeben **eine** Zahl mit `hitCount == 20` und der Summe aus `DamageDealtEvent.totalDamage`, **nicht** zwanzig Zahlen (FR-041). B13 baut keine eigene Bündelung
- [X] T104 [US4] `DamageNumber` in `rpg-core/src/main/java/rpg/core/ui/DamageNumber.java` — nach [data-model.md](./data-model.md) §3 (macht T102, T103 grün)
- [X] T105 [P] [US4] Test `DamageNumbersAreNotPersistentTest` in `rpg-platform/src/test/java/rpg/platform/ui/DamageNumbersAreNotPersistentTest.java` (MockBukkit) — **der Test, an dem SC-009 hängt**: das Persistenz-Flag der erzeugten Entity ist ausdrücklich `false`. Was der Server nicht speichert, kann ein harter Abbruch nicht zurücklassen (FR-044, R7)
- [X] T106 [P] [US4] Test `DamageNumbersOnlyForDealerTest` in `rpg-platform/src/test/java/rpg/platform/ui/DamageNumbersOnlyForDealerTest.java` (MockBukkit) — die Entity ist standardmäßig unsichtbar und wird **einem** Spieler gezeigt; ein zweiter Spieler daneben sieht sie nicht (FR-042)
- [X] T107 [P] [US4] Test `DamageNumbersExpireTest` in `rpg-platform/src/test/java/rpg/platform/ui/DamageNumbersExpireTest.java` (MockBukkit) — nach der konfigurierten Lebensdauer ist die Entity weg, und die Entfernung wurde **im selben Tick eingeplant**, in dem die Anzeige entstand (R2, R7)
- [X] T108 [P] [US4] Test `DamageNumbersDisabledCostsNothingTest` in `rpg-platform/src/test/java/rpg/platform/ui/DamageNumbersDisabledCostsNothingTest.java` — mit `damage-numbers.enabled: false` entsteht **keine** Entity und nichts anderes fällt aus (FR-045)
- [X] T109 [US4] `DamageNumbers` in `rpg-platform/src/main/java/rpg/platform/ui/DamageNumbers.java` — hört auf `DamageDealtEvent`, setzt ein `TextDisplay` am Trefferort plus `offset`, **nicht persistent**, nur dem Verursacher sichtbar (macht T105, T106, T108 grün)
- [X] T110 [US4] In `rpg-platform/src/main/java/rpg/platform/ui/DamageNumbers.java` die Entfernung **innerhalb des Ticks** einplanen, in dem die Anzeige entsteht — `runSyncOnEntityDelayed` greift dort sauber, weil die Entität aufgelöst und der Thread der richtige ist (macht T107 grün)
- [X] T111 [US4] Im Javadoc von `rpg-platform/src/main/java/rpg/platform/ui/DamageNumbers.java` die Falle aus R2 benennen und **warum sie hier nicht auftritt**: aus dem asynchronen Takt heraus wäre `runSyncOnEntity` still gescheitert — derselbe Fehler, der B10 bei T112 einen halben Tag gekostet hat ([[vuntexrpg-async-entity-resolution-gotcha]])
- [X] T112 [US4] Im Javadoc von `rpg-platform/src/main/java/rpg/platform/ui/DamageNumbers.java` die drei Umkehrungen gegenüber `LeaderboardHologram` als Tabelle festhalten (data-model §3) — Persistenz, Sichtbarkeit, Aufräumen. Wer später „warum räumt das nicht vorher auf wie das Hologramm" fragt, findet die Antwort an der Datei und nicht in einer Spec
- [X] T113 [US4] `DamageNumbers` in `RpgPlugin` am `EventBus` anmelden (`rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java`)
- [X] T114 [US4] Den Schlüssel der Schadenszahl in `messages.yml` ergänzen — Platzhalter: Betrag, Trefferzahl; ein eigener Schlüssel für den tödlichen Treffer (`lethal`)

**Checkpoint**: Ein Treffer sagt, was er wert war — und ein Absturz lässt keine einzige Zahl in der
Welt zurück.

---

## Phase 7: User Story 5 — Die geliehenen Fenster kommen heim (P3)

**Goal**: Für den Spieler ändert sich nichts. ADR-028 und ADR-032 sind danach geschlossen.

**Independent Test**: Beide Fenster wie bisher öffnen und bedienen; danach in B08b und B09 nach
Anzeigecode suchen und keinen finden.

> **Die Asymmetrie ist Absicht** (contracts §5): von B09 wandern Fenster **und** Eingabe, von B08b
> nur das Fenster. `/coins` bleibt stehen.

- [ ] T115 [P] [US5] Test `WaypointMenuBehaviourUnchangedTest` in `rpg-platform/src/test/java/rpg/platform/ui/WaypointMenuBehaviourUnchangedTest.java` — **vor** dem Umzug geschrieben, gegen den heutigen Pfad: welche Wegpunkte erscheinen, was ein Klick auslöst. Nach dem Umzug läuft derselbe Test gegen den neuen Pfad und muss unverändert grün sein (FR-060)
- [ ] T116 [P] [US5] Test `CurrencyMenuBehaviourUnchangedTest` in `rpg-platform/src/test/java/rpg/platform/ui/CurrencyMenuBehaviourUnchangedTest.java` — dito für Charakterwahl und seitenweisen Verlauf (FR-061)
- [ ] T117 [US5] `WaypointMenu` von `rpg-platform/src/main/java/rpg/platform/zone/WaypointMenu.java` nach `rpg-platform/src/main/java/rpg/platform/ui/WaypointMenu.java` verschieben — Paket angepasst, Verhalten unverändert
- [ ] T118 [US5] `WaypointMenuListener` von `rpg/platform/zone/` nach `rpg/platform/ui/` verschieben
- [ ] T119 [US5] `CrystalInteractListener` von `rpg/platform/zone/` nach `rpg/platform/ui/` verschieben — **ADR-032 nennt die Eingabe ausdrücklich mit** (FR-060); ein Fenster ohne seinen Listener wäre ein halber Umzug
- [ ] T120 [US5] `CurrencyMenu` von `rpg-platform/src/main/java/rpg/platform/currency/CurrencyMenu.java` nach `rpg/platform/ui/` verschieben
- [ ] T121 [US5] `CurrencyMenuListener` von `rpg/platform/currency/` nach `rpg/platform/ui/` verschieben
- [ ] T122 [US5] `rpg-platform/src/main/java/rpg/platform/ui/WaypointMenu.java` und `CurrencyMenu.java` auf `MenuFrame` umstellen — **derselbe Rahmen wie die Charakterübersicht**, weil sie jetzt derselben Hand gehören. Die zwei fremden Fenster bekommen ihn nicht (R10, FR-070, FR-071)
- [ ] T123 [US5] Importe in `rpg-plugin/src/main/java/rpg/plugin/command/CoinsCommand.java` auf `rpg.platform.ui` umstellen — **`/coins` selbst bleibt, wo es ist** (FR-061a). Im Javadoc ergänzen, dass die Anzeige nach B13 gewandert ist und die Kommandoschale hier auf ADR-028 wartet, das sie B14 zuweist
- [ ] T124 [US5] Verdrahtung in `RpgPlugin` auf die neuen Pfade umstellen (`rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` ~Zeile 767) — Registrierung von `waypointMenu` und `crystalInteract`
- [ ] T125 [US5] `package-info.java` von `rpg/platform/zone/` und `rpg/platform/currency/` fortschreiben — beide nennen jetzt ausdrücklich, dass die Anzeige **nicht mehr hier** liegt, und verweisen auf `rpg.platform.ui` (FR-062)
- [ ] T126 [US5] Test `NoDisplayCodeLeftInZoneTest` in `rpg-platform/src/test/java/rpg/platform/ui/NoDisplayCodeLeftInZoneTest.java` — durchsucht `rpg/platform/zone/` nach `Inventory`, `ItemStack`, `InventoryClickEvent` und `PlayerInteractEvent` und findet **nichts** (FR-062, SC-008). Nach dem Muster der vorhandenen Quellcode-Wächter, Kommentare vorher entfernt
- [ ] T127 [US5] Test `NoDisplayCodeLeftInCurrencyTest` in `rpg-platform/src/test/java/rpg/platform/ui/NoDisplayCodeLeftInCurrencyTest.java` — dito für `rpg/platform/currency/`, **mit einer benannten Ausnahme für die Coin-Piles**: `CoinPile` ist eine Weltentität und keine Anzeige. Die Ausnahmeliste prüft sich selbst mit (R9)
- [ ] T128 [P] [US5] Schließvermerk zu **ADR-028** in `02-decisions.md` ergänzen — die Anzeige ist bei B13, `/coins` wartet weiter auf B14
- [ ] T129 [P] [US5] Schließvermerk zu **ADR-032** in `02-decisions.md` ergänzen — Fenster **und** Eingabe sind bei B13, die Reiseregeln blieben bei B09 (FR-063)

**Checkpoint**: Zwei ADRs, die zehn Blöcke lang offen standen, sind geschlossen — und für den
Spieler hat sich nichts geändert.

---

## Phase 8: User Story 6 — Der Server könnte auch Deutsch sprechen (P3)

**Goal**: Eine zweite Sprachdatei, eine Zeile Konfiguration, ein Neustart — und jeder Text ist
anders. Ohne eine Zeile Code.

**Independent Test**: Zweite Sprachdatei anlegen, umstellen, starten. Dann einen Schlüssel daraus
entfernen und wieder starten.

> **Fast alles davon ist gebaut** (R8). Was fehlt, ist die **Auswahl der Datei** — und der Wächter,
> der verhindert, dass ab morgen wieder Text im Code landet.

- [ ] T130 [P] [US6] Test `LanguageSetTest` in `rpg-core/src/test/java/rpg/core/ui/LanguageSetTest.java` — `code` nicht leer, `file` muss existieren, Laden über `MapMessages.fromNested`; eine fehlende Datei bricht ab und die Meldung nennt, **welche Datei gesucht wurde** (contracts §2)
- [ ] T131 [US6] `LanguageSet` in `rpg-core/src/main/java/rpg/core/ui/LanguageSet.java` — ein Sprachsatz und die Datei, aus der er kommt (data-model §1; macht T130 grün)
- [ ] T132 [US6] `RpgPlugin.loadMessages` auf den konfigurierten Sprachsatz umstellen (`rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` ~Zeile 433) — statt fest `messages.yml` die Datei zu `ui.yml`s `language`. **`messages.yml` bleibt der englische Standardsatz** und wird weiter beim ersten Start herausgeschrieben
- [ ] T133 [US6] Prüfen und im Kommentar festhalten, dass die **zwei Sonderwege bestehen bleiben**: B09 prüft seine Zonennamen und B10 seine Artnamen selbst, weil deren Schlüssel erst nach `zones.yml` beziehungsweise `mobs.yml` feststehen. Ein Sprachwechsel darf sie **nicht** umgehen — sonst fällt eine unvollständige Übersetzung genau dort durch, wo die meisten Schlüssel liegen (R8, contracts §3.1)
- [ ] T134 [P] [US6] Test `LanguageSwitchCoversZoneAndMobNamesTest` in `rpg-plugin/src/test/java/rpg/plugin/LanguageSwitchCoversZoneAndMobNamesTest.java` — ein zweiter Sprachsatz **ohne** die Zonen- und Artnamen bricht den Start ab. Das ist der Test für T133, und ohne ihn ist der Kommentar nur eine Absicht
- [ ] T135 [P] [US6] Test `IncompleteLanguageSetFailsStartTest` in `rpg-plugin/src/test/java/rpg/plugin/IncompleteLanguageSetFailsStartTest.java` — ein Satz mit **drei** fehlenden Schlüsseln bricht ab und die Meldung listet **alle drei** auf einmal, nicht nur den ersten (FR-018, R8: *„an operator fixing a configuration file wants the whole list in one pass"*)
- [ ] T136 [P] [US6] Test `NoPlaceholderReachesPlayerTest` in `rpg-core/src/test/java/rpg/core/ui/NoPlaceholderReachesPlayerTest.java` — ein fehlender Text ergibt `MissingMessageException` und **nie** einen Platzhalter oder eine leere Zeile (FR-019)

### Der Wächter

- [ ] T137 [US6] Test `NoHardcodedPlayerTextTest` in `rpg-platform/src/test/java/rpg/platform/ui/NoHardcodedPlayerTextTest.java` — durchsucht den **Quellcode** nach Zeichenketten, die an eine Ausgabemethode gehen (`sendMessage`, `sendActionBar`, Titel, Anzeigenamen); erlaubt ist dort nur, was aus `Messages` kommt (FR-015, SC-002)
- [ ] T138 [US6] In `rpg-platform/src/test/java/rpg/platform/ui/NoHardcodedPlayerTextTest.java` Kommentare **vor** der Suche entfernen — nach dem Muster von `NoRawTypeNameLeftTest`: „eine Klasse zu erklären ist erlaubt, sie zu rufen nicht" (R9)
- [ ] T139 [US6] Die Ausnahmeliste in `rpg-platform/src/test/java/rpg/platform/ui/NoHardcodedPlayerTextTest.java` **selbst prüfen** — jeder Eintrag muss auf eine existierende Datei zeigen. Ein Wächter, dessen Ausnahmen ins Leere zeigen, bewacht nichts (R9)
- [ ] T140 [US6] Im Javadoc von `rpg-platform/src/test/java/rpg/platform/ui/NoHardcodedPlayerTextTest.java` festhalten, **was er nicht kann**: er sieht keinen Schlüssel, der benutzt, aber nie deklariert wird — den fängt erst die Startprüfung. **Beide zusammen ergeben FR-014 und FR-015; einer allein nicht** (quickstart §2)
- [ ] T141 [US6] Die vom Wächter gefundenen Fundstellen in `rpg-core/src/main/java/rpg/core/ui/` und `rpg-platform/src/main/java/rpg/platform/ui/` beseitigen — falls die Phasen 3 bis 7 welche hinterlassen haben. Der Wächter läuft gegen den **gesamten** Produktivcode, nicht nur gegen dieses Paket
- [ ] T142 [P] [US6] `language: en` in `ui.yml` dokumentieren — der Kommentar nennt, wo die Datei gesucht wird und dass sie **vollständig** sein muss, sonst startet der Server nicht

**Checkpoint**: Ein Betreiber kann den Server auf eine zweite Sprache umstellen, und ein
unvollständiger Satz sagt ihm beim Start, was fehlt — alles auf einmal.

---

## Phase 9: Polish & Querschnitt

**Purpose**: die Zusagen, die über den Geschichten liegen

- [ ] T143 ADR für `/char` in `02-decisions.md` schreiben — nach dem Muster von ADR-028: warum ein Schicht-3-Block ein Kommando anlegt, warum es **vorläufig** ist und dass es mit `/coins`, `/stats` und `/top` an B14 geht (FR-058, Complexity Tracking)
- [ ] T143a ADR für die **Ausnahme von Constitution III.4** in `02-decisions.md` schreiben — `AbilityHotbar` bleibt vor der Schnittstelle stehen (FR-024, FR-024a). Nach dem Muster von ADR-028: Begründung (sie läuft, sie ist abgenommen, ein Umbau an fremdem funktionierendem Code ist der teuerste Weg zu keinem sichtbaren Unterschied), Alternative (hinter `HudRenderer` ziehen) und **Auswirkung** (ein pack-fähiger Client müsste die Skill-Leiste später nachziehen; die Zusage aus SC-004 gilt für B04, B05 und B08 **ohne** die Hotbar). **Die Governance verlangt das**: eine Abweichung ohne ADR ist ein Fehler und kein Kompromiss — und ein Satz unter *Assumptions* ist genau die stille Neuinterpretation, die die Constitution ausschließt
- [ ] T143b [P] In `specs/013-ui-hud-i18n/plan.md` die Zeile für `AbilityHotbar` in **Complexity Tracking** aufnehmen — sie steht dort bisher ausdrücklich **nicht** drin („weil ausdrücklich nicht getan"). Nicht zu handeln ist hier aber selbst die Abweichung: die Tabelle führt, wovon der Plan abweicht, und nicht nur, was er baut
- [ ] T144 [P] Test `NoCompetingHudRendererTest` in `rpg-plugin/src/test/java/rpg/plugin/NoCompetingHudRendererTest.java` — nach dem Muster von `NoCompetingMobProviderTest`: **zwei Sender auf eine Fläche heißt, der letzte gewinnt, und welcher das ist, hängt an der Reihenfolge** (SC-004, quickstart §4)
- [ ] T145 [P] Test `HudRendererIsReplaceableTest` in `rpg-plugin/src/test/java/rpg/plugin/HudRendererIsReplaceableTest.java` — ein zweiter `HudRenderer` an derselben Stelle erfordert **keine** Änderung an B04, B05 oder B08 (FR-022, SC-004). Der Test tauscht die Umsetzung und startet
- [ ] T146 `FullBootstrapTest` in `rpg-plugin/src/test/java/rpg/plugin/FullBootstrapTest.java` um B13 erweitern — der Server startet mit `ui.yml` und dem englischen Sprachsatz, `UiModule` ist gestartet, das Kommando ist registriert. **Ohne diesen Schritt ist der Block nicht fertig** ([[vuntexrpg-block-done-criteria]])
- [ ] T147 [P] Test `UiPersistsNothingTest` in `rpg-plugin/src/test/java/rpg/plugin/UiPersistsNothingTest.java` — B13 legt **kein** Schema und **keine** Migration an; es gibt keine Flyway-Datei mit `V13_`, und `rpg.core.ui` und `rpg.platform.ui` verweisen auf kein Repository (FR-013b, SC-011)
- [ ] T148 Messung `HudTickCostBenchmark` in `rpg-platform/src/test/java/rpg/platform/ui/HudTickCostBenchmark.java` — **eine wiederholbare Messung ohne Volllast** (Prinzip VII.4): ein Durchlauf über 200 gestellte Spieler, gemessen wird die eigene Rechenarbeit des Takts, Zielwert < 1 ms (SC-001). Der Nachweis **unter** 150 echten Spielern bleibt B15 (ADR-031) und hält diesen Block nicht offen
- [ ] T149 [P] Im Javadoc von `rpg-platform/src/test/java/rpg/platform/ui/HudTickCostBenchmark.java` festhalten, was die Messung **nicht** misst: Paketkosten, Netzwerk und den Tick des Servers. Eine Messung, die mehr zu belegen scheint, als sie kann, ist schlimmer als keine
- [ ] T150 [P] `rpg-platform/src/main/java/rpg/platform/hud/MobNameplate.java` und die `PaperVanillaAttributeBridge` durchsehen — **beide bleiben unverändert**; im Javadoc von `MobNameplate` ergänzen, dass die Beschriftung bewusst **nicht** hinter `HudRenderer` liegt, weil sie an einer Kreatur hängt und nicht an einer Spielerfläche
- [ ] T150a [P] Test `UntouchedBlocksStayUntouchedTest` in `rpg-plugin/src/test/java/rpg/plugin/UntouchedBlocksStayUntouchedTest.java` — der Wächter für die vier „nicht anfassen"-Zusagen (FR-071a): `AbilityHotbar` (FR-024), `ClassSelectionMenu` (FR-070), `StatisticsMenu` und `LeaderboardMenu` (FR-071) tauchen in `rpg/platform/ui/` **nirgends** auf, und keine von ihnen ist hinter `HudRenderer` oder `MenuFrame` gezogen. Nach dem Muster von T126/T127. **B13 sichert jede andere Zusage per Test** — T150 und T151 sagen „durchsehen", und Augenschein hält keinem gut gemeinten Umbau in sechs Monaten stand
- [ ] T151 [P] `ExperienceBar` in `rpg-platform/src/main/java/rpg/platform/progression/` durchsehen — bleibt, was sie ist: Level und Erfahrung auf der Vanilla-XP-Leiste, **kein eigener Wert** (FR-006). Im Javadoc den Verweis auf B13 auflösen und festhalten, dass sie die **eine benannte Ausnahme** von FR-001 ist (FR-001a): sie zeigt dieselben Zahlen wie die Sidebar ein zweites Mal, und das ist zugelassen — eine **zweite** Ausnahme ist es nicht (FR-001b)
- [ ] T152 [P] Test `HeartBarPercentTest` in `rpg-platform/src/test/java/rpg/platform/ui/HeartBarPercentTest.java` — die Herzleiste zeigt in jeder geprüften Lage den korrekten Prozentwert (FR-007, SC-003, ADR-003). B13 ändert daran nichts, aber es sagt es zu, also prüft es das
- [ ] T153 `README`/Blockdokument `minecraft-rpg-spec/minecraft-rpg-spec/blocks/B13-ui-hud-i18n.md` fortschreiben — die sechs Antworten stehen dort schon; ergänzt werden die fünf Funde aus R1 bis R8 und die zwei widerlegten Annahmen aus R10
- [ ] T154 [P] Die über vierzig `B13`-Verweise im Baum durchgehen (`grep -rn "B13" rpg-*/src/main/java/`) und die auflösen, die dieser Block eingelöst hat — `AbilityRegistry`, `ClassNotice`, `DamageAggregator`, `Currency`, `GearConditions`, `MobKinds`, `PartyChangedEvent`, `StatusActionBar`. Ein Verweis auf einen fertigen Block ist ein Verweis ins Leere
- [ ] T155 [P] Die Verweise in `rpg-*/src/main/java/`, die **nicht** eingelöst wurden, stehen lassen und begründen — namentlich alles, was B14 (Kommandos, Rechtebaum) und B15 (Lasttest) zugewiesen ist
- [ ] T156 `./gradlew build` — alle Module grün, keine neue Warnung
- [ ] T157 Übersprungene Tests prüfen: **MockBukkit meldet Nicht-Implementiertes als „skipped", nicht als Fehler** ([[vuntexrpg-mockbukkit-skips]]). Jeder neue Skip unter `rpg-platform/src/test/java/rpg/platform/ui/` wird einzeln angesehen und entweder umgeschrieben oder mit Begründung vermerkt
- [ ] T158 Jar bauen und auf den Testserver deployen — **Bukkit überschreibt vorhandene Configs nicht** ([[vuntexrpg-server-deploy]]). Zwei Dateien von Hand: `ui.yml` (neu) und `messages.yml` (vorhanden, aber gewachsen). Vergleich mit `diff --strip-trailing-cr`, sonst meldet CRLF-vs-LF jede Datei als abweichend
- [ ] T159 Serverabnahme Schritte 1 bis 9 **einschließlich 1a, 2a und 9a** aus [quickstart.md](./quickstart.md) §5 — Anmeldung, Schaden, Zonenname, Kanalisierung, Rückkehr des Bossbalkens, Bosskampf, verdrängter Zonenname, Cooldown-Overlay, Cooldown nach Wiederanmeldung
- [ ] T160 Serverabnahme Schritte 10 bis 14 — Schadenszahl am Trefferort, `/char` mit allen Attributen, Charakterwechsel schließt das Fenster, Wegpunkt-Rechtsklick, `/coins`
- [ ] T161 Serverabnahme Schritte 15 bis 18 **einschließlich 15a** aus [quickstart.md](./quickstart.md) §5 — `sidebar.enabled: false` und **keine Spur im Log**, zweite Sprachdatei vollständig, zweite Sprachdatei lückenhaft bricht ab, **harter Abbruch mit Zahlen in der Luft und keine einzige übrig** (SC-009)
- [ ] T162 Serverabnahme Schritt 19 aus [quickstart.md](./quickstart.md) §5 — **braucht einen zweiten Spieler**: A schlägt eine Kreatur, B steht daneben und sieht As Zahl **nicht** (FR-042). Der einzige Schritt dieses Blocks, der nicht allein geht

---

## Dependencies & Execution Order

### Phasenabhängigkeiten

- **Setup (Phase 1)**: keine Abhängigkeit
- **Foundational (Phase 2)**: hängt an Phase 1 — **blockiert alle sechs Geschichten**
- **US1 (Phase 3)**: hängt an Phase 2. Der MVP
- **US2 (Phase 4)**: hängt an Phase 2. Braucht `MenuFrame` (T076) und `ItemRenderer` (T018)
- **US3 (Phase 5)**: hängt an Phase 2. Berührt US1 nicht — das Overlay liegt auf dem Slot, nicht auf einer der drei Flächen
- **US4 (Phase 6)**: hängt an Phase 2 **und** an `HudTick` (T038), weil die Regel aus R2 an dessen Javadoc hängt
- **Innerhalb US1** gibt es eine neue Kante: T050b (die zwei Abonnements ziehen um) hängt an T056b (`HudRefresh` existiert). Sie in der anderen Reihenfolge zu machen hieße, die Abonnements für die Dauer eines Zwischenstands abzumelden, ohne dass jemand sie hält — dann zeichnet ein Aufstieg vorübergehend gar nichts
- **US5 (Phase 7)**: hängt an `MenuFrame` (T076) aus US2
- **US6 (Phase 8)**: hängt an Phase 2. Der Wächter (T137) kann **jederzeit** geschrieben werden — je früher, desto weniger räumt T141 auf
- **Polish (Phase 9)**: hängt an allen gewünschten Geschichten

### Abhängigkeiten zwischen den Geschichten

Die sechs sind weitgehend unabhängig. Zwei Kanten gibt es:

- **US5 braucht `MenuFrame` aus US2** (T076). Wer US5 zuerst will, zieht T075/T076 vor — das sind zwei Aufgaben und keine halbe Geschichte
- **US4 braucht `HudTick`** aus US1 (T038), aber nur für den Javadoc-Verweis auf R2. Die Schadenszahlen selbst hängen am `EventBus`, nicht am Takt

Alles andere ist parallel: US1, US2, US3 und US6 berühren sich nicht.

### Parallelität

- Phase 1 ist komplett parallel (T001–T005)
- In Phase 2 laufen die Aufzählungen (T006, T007), die Konfigurationsdatensätze (T008, T009), die Schlüssel (T015) und die zwei Nähte (T017–T019) parallel; `UiConfig` (T010) und `UiConfigSchema` (T013) sind die Naht dazwischen
- In jeder Geschichte laufen die serverlosen Tests parallel; die Umsetzung folgt
- **Über Geschichten hinweg**: US1, US3 und US6 können von drei Personen gleichzeitig gebaut werden. US2 und US5 gehören in eine Hand, weil sie sich `MenuFrame` teilen

---

## Implementation Strategy

### MVP zuerst

1. Phase 1: Setup
2. Phase 2: Foundational — **blockiert alles**
3. Phase 3: US1 — die drei Flächen
4. **Anhalten und prüfen**: Anmelden, Werte verändern, sehen, dass jeder Wert auf genau einer Fläche steht
5. Ausliefern, wenn es steht

Das ist der Punkt, an dem zwei von vier Vanilla-Flächen aufhören brachzuliegen — und der, auf den
zwölf Blöcke gewartet haben.

### Schrittweise Auslieferung

1. Setup + Foundational → das Fundament
2. US1 → prüfen → ausliefern (**MVP**)
3. US2 → prüfen → ausliefern (die Charakterübersicht, das einzige wirklich fehlende Fenster)
4. US3 → prüfen → ausliefern (Cooldowns; **und die Startprüfung, die einen Konfigurationsfehler findet, den heute niemand sieht**)
5. US4 → prüfen → ausliefern (Schadenszahlen — der vorsichtigste Schritt)
6. US5 → prüfen → ausliefern (die zwei ADRs schließen)
7. US6 → prüfen → ausliefern (die zweite Sprache)

Jeder Schritt fügt etwas hinzu, ohne den vorigen zu brechen.

### Was diese Reihenfolge kostet, wenn man sie umdreht

US6 zuletzt zu bauen heißt, dass T141 die Fundstellen der Phasen 3 bis 7 aufräumt. **Den Wächter
(T137) früh zu schreiben und rot stehen zu lassen ist billiger** — dann fällt jede hartcodierte
Zeile in dem Moment auf, in dem sie entsteht, statt in einer Aufräumaufgabe am Ende.

---

## Notes

- `[P]` = andere Datei, keine offene Abhängigkeit
- Jede Aufgabe nennt ihren Pfad und ihren Bezug (FR, SC, R oder ADR)
- Nach jeder Aufgabe oder logischen Gruppe committen
- An jedem Checkpoint kann die Geschichte für sich geprüft werden
- **Ein Block ist nicht fertig, wenn seine Modultests grün sind**, sondern wenn er im Plugin verdrahtet ist und `FullBootstrapTest` grün ist ([[vuntexrpg-block-done-criteria]]) — deshalb steht T146 in Phase 9 und nicht als Nachgedanke
- **Grüne Tests beweisen nichts über Papers Klassenlader** ([[vuntexrpg-classloader-blind-spot]]) — nur der echte Serverstart tut das, also T158 vor T159
