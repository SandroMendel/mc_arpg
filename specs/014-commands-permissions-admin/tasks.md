---

description: "Aufgabenliste B14 · Commands, Permissions & Admin-Tools"
---

# Tasks: B14 · Commands, Permissions & Admin-Tools

**Input**: Entwurfsunterlagen aus `specs/014-commands-permissions-admin/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: Testaufgaben sind enthalten und **nicht optional** — Constitution VII verlangt sie, und
die Spec fordert vier davon namentlich (FR-031a, FR-034, FR-035, FR-036).

**Organization**: Nach User Story gruppiert, damit jede für sich umsetzbar und prüfbar bleibt.

## Format: `[ID] [P?] [Story] Beschreibung`

- **[P]**: parallel möglich (andere Datei, keine offene Abhängigkeit)
- **[Story]**: zugehörige User Story (US1…US8)
- Jede Aufgabe nennt ihren Dateipfad

## Pfade

- Domäne (bukkit-frei): `rpg-core/src/main/java/rpg/core/`
- Paper-Anbindung: `rpg-platform/src/main/java/rpg/platform/`
- Persistenz: `rpg-persistence/src/main/java/rpg/persistence/`
- Plugin und **Kommandos**: `rpg-plugin/src/main/java/rpg/plugin/`
- Tests spiegeln die Hauptbäume unter `src/test/java/`

> **`rpg-core` bekommt kein neues Interface und keine neue Abhängigkeit.** Genau zwei Aufgaben
> ändern dort etwas Bestehendes — T004 (Umbenennung) und T071/T072 (Herkunft am Eintrag, ADR-1).

---

## Phase 1: Setup (gemeinsame Grundlage)

**Zweck**: Ablage, Umzüge und die zwei Aufräumarbeiten, die den Rest sauber halten.

- [X] T001 [P] Paket `rpg-plugin/src/main/java/rpg/plugin/command/framework/` anlegen mit `package-info.java`, das den Vertrag aus `contracts/command-framework.md` in einem Absatz zusammenfasst
- [X] T002 [P] Paket `rpg-plugin/src/main/java/rpg/plugin/command/admin/` anlegen mit `package-info.java`
- [X] T003 `TrashCommand` von `rpg-platform/src/main/java/rpg/platform/item/TrashCommand.java` nach `rpg-plugin/src/main/java/rpg/plugin/command/TrashCommand.java` verschieben; Verdrahtung in `RpgPlugin.java` und `TrashCommandTest` nachziehen — es war das einzige Kommando außerhalb von `rpg.plugin.command`. **Der Test heißt `TrashRefusesBoundEquipmentTest`, nicht `TrashCommandTest`**, und er musste mit umziehen: `rpg-platform` darf nicht auf `rpg-plugin` zeigen. Dazu kam ein Import für `ItemText`, das vorher im selben Paket lag
- [X] T004 `ItemModule.notifyReloaded()` in `rpg-core/src/main/java/rpg/core/item/ItemModule.java` zu `applyReloadedConfig()` umbenennen, damit alle sechs Module gleich heißen; `ItemModuleReloadTest` nachziehen (research.md §5 — die Abweichung ist eine Falle, kein Merkmal)
- [X] T005 [P] `CommandMessageKeys` in `rpg-plugin/src/main/java/rpg/plugin/command/CommandMessageKeys.java` anlegen — B14s Schlüssel wohnen im Plugin, weil `MessageKeyValidator.verifyAllPresent` die Liste vom Aufrufer bekommt und `rpg-core` unberührt bleibt. Acht Schlüssel aus FR-004, FR-008, FR-013 und FR-032, Texte in `messages.yml` ergänzt. **`all()` ist noch nicht in `RpgPlugin.loadMessages` angemeldet** — das gehört ans Gerüst (T013/T015), sonst bricht der Start an Schlüsseln, die noch niemand benutzt
- [X] T006 [P] Abschnitt `commands:` in `rpg-plugin/src/main/resources/config.yml` für die Sperrzeiten vorsehen (Voreinstellung je Kommando, Prinzip V) — **abweichend umgesetzt: `commands.yml`.** Ein `config.yml` gibt es in diesem Projekt nicht; jeder Block bringt seine eigene YAML mit (`abilities.yml`, `items.yml`, `ui.yml` …), und eine Sammeldatei wäre die einzige Ausnahme. In `DEFAULT_CONFIG_FILES` eingetragen

### Zwei Funde aus Phase 1

**Der Umzug von `TrashCommand` hätte einen Wächter still entwertet.** `TrashRefusesBoundEquipmentTest`
scannt den Quelltext auf `class_bound` und `BoundEquipment.tagFor(` — die Zusage, dass **kein**
Entsorgungsweg die Bindung selbst entscheidet (FR-079). Der Scan lief über
`rpg/platform/item`; nach dem Umzug hätte er `TrashCommand` **nicht mehr gefunden** und wäre grün
geblieben, weil dort nichts mehr ist. Der Test deckt jetzt beide Wurzeln ab und **prüft mit
`assertThat(root).exists()`, dass es die Orte gibt** — ein Quellscan auf einen falschen Pfad ist
sonst ein Test, der nur noch behauptet, geprüft zu haben.

**Und er hat einen toten Filter hinterlassen.** `InventoryFullWarningTest` nahm `TrashCommand.java`
ausdrücklich von seinem Scan aus. Diese Ausnahme filtert jetzt auf eine Datei, die es in dem Paket
nicht mehr gibt. Entfernt, mit einer Zeile, die sagt wohin die Klasse gegangen ist.

---

## Phase 2: Foundational (blockierende Vorarbeiten)

**Zweck**: Der Beweis auf dem echten Server, das Gerüst und die Wurzel `/rpg`. **Ohne diese Phase
kann keine User Story beginnen.**

### Zuerst der Beweis, den kein Test führen kann

> **T007 bis T010 kommen vor jeder Zeile Umbau.** Ob ein `plugin.yml`-Eintrag und eine
> Brigadier-Registrierung desselben Namens sich vertragen oder überschreiben, ist mit keinem Test zu
> klären (research.md §1). Vier Fehler dieses Projekts kamen daher, dass ein grüner Test etwas über
> die Wirklichkeit behauptete.

- [X] T007 Wegwerf-Kommando `/b14probe` **nur** über `LifecycleEventManager` und `Commands` registrieren, ohne `plugin.yml`-Eintrag, in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java`; bauen, deployen, auf dem echten Server prüfen, dass es existiert und vervollständigt (quickstart Schritt 0) — **belegt 2026-09-05**: `b14probe` → `B14PROBE: BRIGADIER (kein Argument)`, `b14probe beta` → `… (kind=beta)`. Ohne `plugin.yml`-Eintrag. Die Vervollständigung **beim Client** ist damit nicht belegt (Konsole, kein Spieler) und bleibt als quickstart-Schritt 0 offen
- [X] T008 Dasselbe Kommando **zusätzlich** in `rpg-plugin/src/main/resources/plugin.yml` eintragen und festhalten, welche Fassung gewinnt (quickstart Schritt 0a) — **Brigadier gewinnt.** Und nicht, weil der andere Weg fehlschlug: der Start meldete `[b14probe] plugin.yml-Fassung verdrahtet`, die Fassung war also da und wurde **nie erreicht**
- [X] T009 Startlog auf Warnungen und `ClassNotFoundException` zu `com.mojang.brigadier` prüfen — belegt, dass die transitive Auflösung zur Laufzeit trägt und **kein** `libraries:`-Eintrag nötig ist (quickstart Schritt 0b, [[vuntexrpg-classloader-blind-spot]]) — **keine** `ClassNotFoundException`, kein `NoClassDefFoundError`, keine Brigadier-Warnung
- [X] T010 Ergebnis aus T007–T009 in `research.md` §1 unter „Der eine offene Punkt" eintragen und **entscheiden**, ob der `commands:`-Block in `plugin.yml` bleibt oder entfällt. Wegwerf-Kommando danach entfernen — **Entscheidung: der Block entfällt** je Kommando beim Umzug. Zwei Dinge müssen dabei mitwandern, sonst gehen sie still verloren: `permission:` nach `requires(...)` am Knoten, `description:`/`usage:` an den Knoten und ihre Texte nach `messages.yml`. Sonde restlos entfernt (`grep b14probe` über `rpg-*/src/` findet nichts, `git status` zeigt keine Änderung an `RpgPlugin.java` oder `plugin.yml`)

**Checkpoint**: Die Registrierungsfrage ist beantwortet. Erst jetzt wird gebaut.

### Das Gerüst

- [X] T011 [P] `Argument` in `rpg-plugin/.../command/framework/Argument.java` — Name, Typ, Pflichtangabe, Vorschlagsquelle; **Vorschlag und Prüfung lesen dasselbe Feld** (FR-002, data-model.md). **Abweichend: kein eigenes Feld `suggestions`.** Das Datenmodell nennt es „dieselbe Quelle, die auch prüft" — genau deshalb ist es keins: zwei Felder, die dasselbe sein *müssen*, sind zwei Felder, die auseinanderlaufen *können*. `ArgumentType` hat `parse` und `suggest` als zwei Methoden **eines** Objekts, und ein Kommando bekommt nur dieses eine. Die Zusage steht damit in der Bauform statt in einer Verabredung
- [X] T012 [P] `RpgCommand` in `rpg-plugin/.../command/framework/RpgCommand.java` — Name, Beschreibungsschlüssel, Recht, Unterkommandos, Argumente, `requiresPlayer`, Sperrzeit. Dazu `CommandContext` als das, was ein Blatt bei der Ausführung bekommt: **schon geprüfte** Werte, damit kein Kommando ein zweites Mal zerlegt
- [X] T013 `CommandTree` in `rpg-plugin/.../command/framework/CommandTree.java` — baut aus `RpgCommand` einen Brigadier-Baum und registriert ihn über den `LifecycleEventManager` (hängt an T011, T012). **Jedes Argument geht als `word()` durch, Brigadiers eigene Typen werden nicht benutzt** — mit `integer()` prüfte Brigadier und `ArgumentType` schlüge vor, also wieder zwei Quellen. Der Preis ist Brigadiers clientseitige Vorabprüfung; dafür kann nie etwas vorgeschlagen werden, was hinterher abgelehnt wird. Ablehnungen gehen über die Naht `CommandTree.Rejections` hinaus, die T023 füllt — eine `CommandSyntaxException` wäre roher, unübersetzter Text und verstieße gegen FR-004 und FR-009
- [X] T014 Regel durchsetzen, dass ein Knoten mit Unterkommandos keine eigene Ausführung hat und ein Blatt keine Unterkommandos — in `CommandTree.java`, damit es keinen Zustand „halb ausgeführt" gibt. **Abweichend im Konstruktor von `RpgCommand` statt in `CommandTree`:** eine Prüfung beim Bauen des Baums fängt einen falschen Knoten erst, wenn jemand ihn zu registrieren versucht — im Konstruktor lässt er sich **gar nicht erst herstellen**. Der Zustand ist damit unmöglich statt unwahrscheinlich, und `CommandTree` muss die Regel nicht kennen. Mitgenommen: ein Pflichtargument hinter einem optionalen ist nicht erreichbar und wird ebenso abgewiesen
- [X] T015 Wurzelkommando `/rpg` in `rpg-plugin/.../command/admin/RpgRootCommand.java` anlegen — **gehört ins Fundament, nicht in eine Story**: US4 bis US8 hängen alle daran, und eine Wurzel in US3 hätte fünf Stories von einer sechsten abhängig gemacht. **Ohne Unterkommandos wird sie gar nicht registriert** (`Optional.empty()`): ein `/rpg`, unter dem nichts hängt, stünde in der Vervollständigung, nähme Platz und antwortete auf jede Eingabe „unvollständig". **Und sie trägt kein eigenes Recht** — sichtbar ist sie genau dann, wenn ein Kind sichtbar ist; ein Wurzelrecht wäre eine zweite Hürde vor derselben Tür, und wer `rpg.admin.reload` hätte, aber das Wurzelrecht nicht, bekäme ein Recht, das nichts nützt
- [X] T016 [P] `Arguments` in `rpg-plugin/.../command/framework/Arguments.java` mit den Typen `AMOUNT`, `LEVEL` (1..60), `PERIOD`, `DURATION`. **Die 60 ist nicht festgeschrieben:** `Progression.maxLevel()` kommt über `XpCurve` aus `progression.yml`. `level(IntSupplier)` fragt bei jedem Aufruf, damit ein `/rpg reload` sofort durchschlägt — eine feste 60 wäre nach der ersten Änderung an der Kurve **still falsch**: die Vervollständigung nähme Stufe 61 nicht an, das Spiel schon
- [X] T017 [P] Argumenttyp `PLAYER` in `Arguments.java` — online **und offline**, über `getOfflinePlayer(name)` plus `hasPlayedBefore() || isOnline()`; das Muster steht heute einmal in `RpgPlugin` und einmal gar nicht (`CoinsCommand` nutzt nur `getPlayerExact`), research.md §9
- [X] T018 [P] Argumenttypen `ITEM_TEMPLATE` (aus `Items`), `MOB_KIND` (aus `MobKinds`), `CHARACTER_CLASS` in `Arguments.java`
- [X] T019 `CommandPermissions` in `rpg-plugin/.../command/framework/CommandPermissions.java` — **die eine Prüfstelle**; ein Kommando kann seine Prüfung nicht vergessen, weil es sie nicht durchführt (FR-003, FR-013)
- [X] T020 Vorschläge nach Recht filtern in `CommandTree.java` — ohne Recht erscheint ein Unterkommando gar nicht (FR-014)
- [X] T021 [P] `RateLimits` in `rpg-plugin/.../command/framework/RateLimits.java` — je Absender und Kommando, **zeitstempelbasiert lazy**, kein wiederkehrender Task (Prinzip II, FR-032); Konsole ausgenommen
- [X] T022 [P] `AdminAudit` in `rpg-plugin/.../command/framework/AdminAudit.java` — die eine Stelle, die `AuditLogRepository.append` ruft; Konsole als feste Null-UUID, wie `XpCommand` sie schon benutzt (FR-028, FR-031, data-model.md)
- [X] T023 Fehlermeldungen in `rpg-plugin/.../command/framework/CommandErrors.java` — nennen Argument und Wertebereich, **nicht** die Nutzungszeile; alle über `CommandMessageKeys` (FR-004, FR-009)
- [X] T024 Konsolenfall in `CommandTree.java` — ein Blatt mit `requiresPlayer` bricht mit erklärender Meldung ab, statt eine Ausnahme zu werfen (FR-008)
- [X] T025 Rückgabe asynchroner Ergebnisse in den Tick über die projekteigene Scheduler-Abstraktion in `rpg-plugin/.../command/framework/TickReturn.java` — **kein `join()`** (Prinzip I); [[vuntexrpg-async-entity-resolution-gotcha]] beachten

### Tests des Gerüsts

- [X] T026 [P] `ArgumentValidationTest` in `rpg-plugin/src/test/java/rpg/plugin/command/framework/ArgumentValidationTest.java` — jeder Typ, einschließlich der Grenzen. 17 Tests; die Grenzen sind einzeln geprüft, weil der um eins verfehlte Rand im Spiel erst auffällt, wenn jemand genau den Randwert tippt
- [X] T027 [P] `SuggestionsMatchValidationTest` in `rpg-plugin/src/test/java/rpg/plugin/command/framework/SuggestionsMatchValidationTest.java` — **jeder vorgeschlagene Wert besteht die Prüfung, für jeden Typ**. Das ist FR-002 und SC-001 als Test statt als Absicht. **Der Test prüft die Eigenschaft, nicht die Werte**: er kennt keine Liste auswendig, sondern schickt zurück, was ein Typ vorschlägt. Dazu `everyTypeInTheFactoryIsCovered()`, das über Reflection sicherstellt, dass **jede** öffentliche Fabrikmethode in einer der beiden Listen steht — sonst wäre der Test nur so viel wert wie die Sorgfalt dessen, der den nächsten Typ hinzufügt
- [X] T014a [P] `CommandShapeTest` in `rpg-plugin/src/test/java/rpg/plugin/command/framework/CommandShapeTest.java` — **nicht in der ursprünglichen Liste**, aber T014 ist eine Zusage und hatte keinen Prüfer: Verzweigung mit Ausführung, Verzweigung mit Argumenten, Blatt ohne Ausführung, Pflicht hinter Optional, dazu die zwei Regeln der Wurzel. 8 Tests
- [X] T028 [P] `PermissionGateTest` in `rpg-plugin/src/test/java/rpg/plugin/command/framework/PermissionGateTest.java` — ohne Recht wird die Ausführung nicht berührt (FR-013)
- [X] T029 [P] `RateLimitsTest` in `rpg-plugin/src/test/java/rpg/plugin/command/framework/RateLimitsTest.java` — rechnet aus Zeitstempeln, lässt nach Ablauf wieder durch, Konsole nie gesperrt
- [X] T030 [P] `ConsoleSenderTest` in `rpg-plugin/src/test/java/rpg/plugin/command/framework/ConsoleSenderTest.java` — Blatt ohne Spielerbezug läuft, Blatt mit Spielerbezug bricht mit Meldung ab
- [X] T031 [P] `PlayerArgumentTest` in `rpg-plugin/src/test/java/rpg/plugin/command/framework/PlayerArgumentTest.java` — offline auflösbar, Tippfehler wird abgelehnt statt als leeres Profil durchgereicht
- [X] T032 [P] `NoExternalPermissionPluginTest` in `rpg-plugin/src/test/java/rpg/plugin/NoExternalPermissionPluginTest.java` — **FR-011 als Test**: die Abhängigkeiten von `rpg-plugin` und der `libraries:`-Block enthalten kein Rechte-Plugin. Der Rechtebaum muss von jedem Plugin bedienbar bleiben, das Bukkit-Rechte vergibt, ohne dass wir eines davon voraussetzen

### Anmerkungen zu T017–T032 (2026-09-05)

**Reihenfolge in `CommandTree.mayRun` — Recht, Absender, Sperrzeit.** Nicht beliebig: wer nicht
darf, soll nicht erfahren, dass er zu schnell war; die Konsole unterliegt keiner Sperre; und die
Sperre kommt zuletzt, weil sie als einzige etwas **vermerkt**. Ein Vermerk für einen Aufruf, der
danach am Recht scheitert, wäre eine Sperre gegen nichts — der nächste, berechtigte Aufruf träfe auf
einen Zeitstempel, den ein abgewiesener Versuch gesetzt hat. `ConsoleSenderTest` prüft genau das.

**`RateLimits.check` prüft und vermerkt in einem Zug.** Zwei Methoden — „darf er?" und „merk es
dir" — hätten eine Lücke dazwischen und einen Aufrufer, der die zweite vergisst. Ein vergessenes
Vermerken fällt nie auf: die Sperre wirkt einfach nicht.

**`TickReturn` nimmt `runSyncAtLocation`, nicht `runSyncOnEntity`** ([[vuntexrpg-async-entity-resolution-gotcha]]).
Aus asynchronem Zusammenhang scheitert die Entitätsvariante **still** — und genau das ist hier der
Normalfall, weil die Antwort Sekunden später kommt und der Spieler bis dahin weg sein kann. Also
wird die Position beim Absetzen festgehalten. Die Konsole bekommt die Ausgangswelt; der Rückruf
schreibt in eine Konsole und fasst nichts in der Welt an.

**Der Abdeckungstest aus T027 hat beim ersten Lauf angeschlagen** — vier neue Fabrikmethoden,
keine davon in seinen Listen. Genau dafür war er gebaut. `SuggestionsMatchValidationTest` braucht
seither MockBukkit, weil `PLAYER` einen Server braucht: ohne ihn müsste der Typ fehlen, und dann
wäre die Abdeckungsprüfung eine Lüge.

**T032 prüft eine Abwesenheit — und deshalb zusätzlich eine Anwesenheit.** Die vier Prüfungen auf
„kein Rechte-Plugin" wären auch dann grün, wenn es gar keine Rechte gäbe.
`thepermissionTreeIsActuallyThere()` schließt die Lücke: „wir hängen an keinem Rechte-Plugin" ist
nur dann eine Zusage, wenn der eigene Baum steht (FR-010).

**Offen geblieben, bewusst:** `CommandMessageKeys.all()` ist weiterhin **nicht** in
`RpgPlugin.loadMessages` angemeldet, und weder `CommandTree` noch `/rpg` sind verdrahtet — es hängt
noch kein Kommando im Baum. Das gehört an US1 (T033 ff.), wo das erste Kommando umzieht.

**Checkpoint**: Das Gerüst trägt, `/rpg` steht. User Stories können beginnen — und sind ab hier
wirklich unabhängig.

---

## Phase 3: User Story 1 — Ein Kommando verhält sich wie jedes andere (Priority: P1) 🎯 MVP

**Goal**: Die sechs vorhandenen Kommandos ziehen auf das Gerüst um, ohne dass ein Spieler etwas
anderes sieht als vorher — plus Tab-Completion, die nicht von der Prüfung abweichen kann.

**Independent Test**: Die sechs Kommandos wie vor dem Umzug benutzen (quickstart 1–7). Ergebnis
identisch, Vervollständigung neu. Kein Abnahmeschritt aus B08b, B11, B12 oder B13 muss angepasst
werden.

- [X] T033 [US1] `/trash` als erstes umziehen in `rpg-plugin/.../command/TrashCommand.java` — kein Argument, deckt trotzdem Registrierung, Rechteprüfung und Konsolenfall ab. **Auf dem echten Server belegt (2026-09-06):** `[command] 1 registriert ueber Brigadier: [trash]`, und `trash` von der Konsole antwortet „That command needs a player." — **vorher schwieg es.** Das alte `onCommand` gab für die Konsole `true` zurück und sagte nichts; der Umzug hat FR-008 nebenbei eingelöst, ohne dass die Klasse etwas davon weiß

### Der Fund aus T033: eine Beschreibung, die als Schlüssel vor Augen kam

`help trash` zeigte `Description: command.trash.description`. `CommandTree` reichte den
`MessageKey` an Brigadier durch — mit dem Vermerk im Code, das sei „Betreibertext im Log". **Das war
schlicht falsch:** es steht in der Serverhilfe, und die liest ein Mensch. Derselbe Fehler wie ein
Zonenname, der im Spiel als `zone.greenfields.name` auftaucht; B09 bricht dafür den Start ab.

Behoben (`CommandTree` bekommt `Messages` und löst auf) und mit `CommandDescriptionsResolveTest`
abgesichert — der **sucht die Schlüssel selbst** in `messages.yml`, statt eine Liste zu führen, die
beim übernächsten Kommando unvollständig wäre. Gegengeprüft auf dem Server: die Hilfe zeigt jetzt
den Text.

**Bemerkenswert daran:** kein Test hätte das gefunden. MockBukkit bildet den
`LifecycleEventManager` nicht ab, also kann keine Testumgebung sagen, was Brigadier mit der
Beschreibung macht. Das ist genau die Lücke, für die T007–T010 den Serverbeweis eingeführt haben —
und sie gilt für jedes weitere Kommando dieser Story.
- [X] T034 [US1] `/char` umziehen in `rpg-plugin/.../command/CharacterSheetCommand.java`; handgeschriebene `onTabComplete` entfernen. Sie gab **absichtlich eine leere Liste statt `null`** zurück, um Bukkits Rückfall auf Spielernamen zu verhindern — eine Vorkehrung gegen ein Verhalten, das der Baum nicht kennt. Konsolenfall mit repariert: dort kam bisher „Pick a character first.", was den Betreiber auf eine Wahl verwies, die er nie treffen wird
- [X] T035 [US1] `/stats` umziehen in `rpg-plugin/.../command/StatisticsCommand.java`; `onTabComplete` entfernen, Zeiträume als `PERIOD`-Argument. **Das erste Argument ist mehrdeutig** (Zeitraum *oder* Spielername, B12-FR-044) — dafür `Arguments.periodOrPlayer`: als Typ steht die Regel „ein Wort, das ein Zeitraum ist, ist ein Zeitraum" an einer Stelle, als zwei Geschwisterknoten wäre sie eine Reihenfolge im Baum, der niemand ansieht, dass sie eine Regel ist. **Die alte `onTabComplete` schlug nur Zeiträume vor, nie Spielernamen** — die Vervollständigung war schon vorher unvollständig, es ist nur niemandem aufgefallen. Sperrzeit aus `commands.yml` (T042 für dieses Kommando erledigt)
- [X] T036 [US1] `/top` umziehen in `rpg-plugin/.../command/TopCommand.java`; `onTabComplete` entfernen. Erstes Argument ist Tafel **oder** `score` → `Arguments.leaderboard()`. **Bewusste Verhaltensänderung:** `boardOf` fiel bisher für einen unbekannten *und* für einen privaten Schlüssel still auf `MOB_KILLS` zurück — wer `/top playtime_online` versuchte, bekam die Kill-Tafel und hätte ihre Zahlen für die angefragten gehalten. Jetzt abgelehnt und benannt (FR-004); der argumentlose Aufruf öffnet weiter `MOB_KILLS`, das ist eine Voreinstellung und keine stille Ersetzung. `TopCommand.SEASON_SCORE` zeigt jetzt auf `Arguments.SEASON_SCORE` — zwei Konstanten desselben Wortes wären zwei Wahrheiten. **Serverlauf steht noch aus**, Sandro war auf dem Server
- [X] T037 [US1] `/coins` umziehen in `rpg-plugin/.../command/CoinsCommand.java`; `onTabComplete` entfernen, `getPlayerExact` durch den `PLAYER`-Argumenttyp ersetzen
- [X] T038 [US1] `/xp` umziehen in `rpg-plugin/.../command/XpCommand.java`; `onTabComplete` entfernen, die Null-UUID-Konstante an `AdminAudit` abgeben
- [X] T039 [US1] Die acht `getCommand(...).setExecutor(...)` / `.setTabCompleter(...)` in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` entfernen und durch **eine** Registrierung über `CommandTree` ersetzen
- [X] T040 [US1] `commands:`-Block in `rpg-plugin/src/main/resources/plugin.yml` nach der Entscheidung aus T010 anpassen
- [X] T041 [US1] Neue Meldungsschlüssel in `rpg-plugin/src/main/resources/messages.yml` ergänzen und in `CommandMessageKeys` deklarieren
- [X] T042 [US1] Sperrzeiten auf `/stats` und `/top` setzen — die beiden Kommandos **dieser** Story, die die Datenbank befragen (FR-032). Wer US8 weglässt, hätte sie sonst nicht
- [X] T043 [P] [US1] `NoManualArgumentParsingTest` in `rpg-plugin/src/test/java/rpg/plugin/command/NoManualArgumentParsingTest.java` — **kein `args.length`, kein `switch (args[0])` im Produktivcode** (FR-001, SC-002). Sonst fängt das nächste Kommando wieder von vorn an
- [X] T044 [P] [US1] `MigratedCommandsBehaveTheSameTest` in `rpg-plugin/src/test/java/rpg/plugin/command/MigratedCommandsBehaveTheSameTest.java` — Name, Syntax und Ausgabe je Kommando unverändert, die aufgerufenen Blockschnittstellen ebenso (FR-005, FR-006, SC-008)
- [X] T045 [P] [US1] `CommandsUsePublicInterfacesOnlyTest` in `rpg-plugin/src/test/java/rpg/plugin/command/CommandsUsePublicInterfacesOnlyTest.java` — Naht-Test nach dem Muster von B13s `UiSeamGuardTest` (FR-007, Prinzip III)
- [ ] T046 [US1] Bauen, deployen, quickstart 1–7 auf dem echten Server durchgehen. **Von der Konsole aus am 2026-09-06 belegt:** `[command] 6 registriert ueber Brigadier: [char, coins, stats, top, trash, xp]`; alle Beschreibungen lösen zu echtem Text auf; `xp set Ticoo 999` → „level must be between 1 and 60 - you gave 999."; `xp give Ticoo 0` → „The value 0 is not a valid amount - expected 1+."; `xp give GibtsNichtXY 5` → „No player named GibtsNichtXY."; `coins set Ticoo warrior 100` (Spieler abgemeldet) → „No such character for that player." — **die letzten zwei zusammen sind der Beweis für T017**: Tippfehler und abgemeldeter Spieler sind endlich zwei verschiedene Meldungen. **Offen bleibt, was nur ein Spieler sieht**: die Vervollständigung beim Client (quickstart 1, 2, 8) und die Fenster von `/char`, `/coins`, `/top`, `/stats`

### Der Fund aus dem Serverlauf: eine Obergrenze, die es nicht gibt

`xp give Ticoo 0` antwortete zuerst *„amount must be between 1 and 9223372036854775807"*. Die Zahl
ist richtig und vollkommen nutzlos — sie zwingt den Betreiber, sie zu lesen, um festzustellen, dass
sie nichts bedeutet. FR-004 will den **erlaubten Bereich** genannt haben, und der ist hier „ab 1".

Behoben mit `Arguments.atLeast(name, min)`: ein eigener Typ statt `amount(1, Long.MAX_VALUE)`, weil
eine Obergrenze, die es nicht gibt, auch nicht dastehen soll. Jetzt: *„The value 0 is not a valid
amount - expected 1+."* Auf dem Server gegengeprüft.

**Kein Test hätte das gefunden.** Die Meldung war formal korrekt, und ein Test hätte genau die
Zeichenkette erwartet, die dastand. Sichtbar wurde es erst, als sie jemand las.

### Warum T044 in `FullBootstrapTest` steht und nicht in einer eigenen Klasse

Der erste Anlauf baute die sechs Kommandos mit null-Mitarbeitern nach, um ihre Bäume zu prüfen.
Das scheiterte an `requireNonNull` in den Konstruktoren — **zu Recht**, und der Nachbau hätte
ohnehin nur bewiesen, dass der Nachbau stimmt. Die Zusicherungen sitzen jetzt dort, wo der **echte**
Baum entsteht.

**Checkpoint**: 🎯 **MVP.** Der Server hat eine einheitliche Bedienoberfläche, auch ohne ein
einziges neues Admin-Werkzeug.

---

## Phase 4: User Story 2 — Ohne Recht kein Eingriff (Priority: P1)

**Goal**: Ein vollständiger, deklarierter Rechtebaum mit drei Stufen; ohne Recht ist ein
Admin-Kommando weder sichtbar noch ausführbar.

**Independent Test**: Für jedes Kommando mit einem Absender ohne das zugehörige Recht versuchen
(quickstart 8–10).

- [X] T047 [US2] Rechtebaum aus `contracts/permissions.md` in `rpg-plugin/src/main/resources/plugin.yml` eintragen — jeder Knoten mit Beschreibung und ausdrücklichem `default` (FR-010, FR-012)
- [X] T048 [US2] Moderator-Knoten `rpg.admin.inspect.sheet`, `.statistics`, `.inventory`, `.session` anlegen (`default: op`, zum Weitergeben gedacht) (FR-012)
- [X] T049 [US2] Admin-Knoten `rpg.admin.item.give`, `rpg.admin.mob.spawn`, `rpg.admin.set.class`, `rpg.admin.reload`, `rpg.admin.audit` anlegen (FR-012)
- [X] T050 [US2] Die fünf vorhandenen Spielerrechte unverändert lassen und im Kommentar begründen, warum `rpg.currency.admin` und `rpg.progression.admin` von der Namensregel abweichen dürfen (`contracts/permissions.md`, Abschnitt Namensregel)
- [X] T051 [P] [US2] `DeclaredPermissionsGuardTest` in `rpg-plugin/src/test/java/rpg/plugin/command/DeclaredPermissionsGuardTest.java` — ein im Code benutztes Recht ohne `plugin.yml`-Eintrag lässt den Test **rot** werden (FR-010, FR-034)
- [X] T052 [P] [US2] `CommandsRegisteredThroughFrameworkTest` in `rpg-plugin/src/test/java/rpg/plugin/command/CommandsRegisteredThroughFrameworkTest.java` — ein am Gerüst vorbei registriertes Kommando lässt den Test **rot** werden (FR-034)
- [X] T053 [P] [US2] `PermissionTierTest` in `rpg-plugin/src/test/java/rpg/plugin/command/PermissionTierTest.java` — frischer Server ohne Rechte-Plugin: fünf Spielerkommandos gehen, kein Admin-Kommando geht (FR-012, FR-013, SC-007)
- [X] T054 [US2] `FullBootstrapTest` in `rpg-plugin/src/test/java/rpg/plugin/FullBootstrapTest.java` um **die sechs umgezogenen Kommandos und ihre Rechte** erweitern. Die Vollständigkeitsprüfung über *alle* Kommandos folgt in T116, wenn die Admin-Werkzeuge existieren (FR-035)
- [ ] T055 [US2] quickstart 8–10 auf dem echten Server

**Checkpoint**: US1 + US2 stehen. Ab hier sind US3–US8 voneinander unabhängig.

---

## Phase 5: User Story 3 — Ein Gegenstand entsteht auf Ansage (Priority: P2)

**Goal**: `/rpg item give` schließt die Lücke, die nachweislich die B11-Abnahme behindert hat.

**Independent Test**: Jede Vorlage aus `items.yml` vergeben und mit einem erbeuteten Exemplar
vergleichen (quickstart 11–15).

- [X] T056 [US3] `ItemGiveCommand` in `rpg-plugin/.../command/admin/ItemGiveCommand.java` — `/rpg item give <Spieler> <Vorlage> [Menge]` über `ItemStackFactory.create(templateKey, amount)` (FR-015)
- [X] T057 [US3] Volles Inventar über B11s vorhandene Regel behandeln (B11 US7) statt eine eigene zu erfinden (FR-017)
- [X] T058 [US3] Unbekannte Vorlage: leeres `Optional` aus `create` in eine Meldung übersetzen, die den Schlüssel nennt (FR-015)
- [X] T059 [US3] Audit-Eintrag `item_granted` über `AdminAudit` schreiben (FR-028)
- [X] T060 [P] [US3] `ItemGiveTest` in `rpg-plugin/src/test/java/rpg/plugin/command/admin/ItemGiveTest.java` — vergebener Gegenstand ist von einem erbeuteten nicht zu unterscheiden (FR-016); volles Inventar verliert nichts; unbekannter Schlüssel bricht ab
- [ ] T061 [US3] quickstart 11–15 auf dem echten Server — dabei die Zeit stoppen: ein beliebiger Gegenstand muss in **unter zehn Sekunden** in der Hand eines Spielers sein, ohne Kampf, Händler oder Neustart (SC-003)

---

## Phase 6: User Story 4 — Die Konfiguration ändert sich ohne Neustart (Priority: P2)

**Goal**: `/rpg reload` ist der erste Produktivaufruf eines Pfades, der seit B01 gebaut, getestet
und nie benutzt wurde.

**Independent Test**: Eine gültige und eine ungültige Änderung, jeweils mit Beobachtung vor und
nach dem Kommando (quickstart 16–20).

- [X] T062 [US4] Einstiegspunkt in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java`: `ConfigLoader.reloadAll()` rufen und danach `applyReloadedConfig()` an **allen sechs** Modulen — `CombatModule`, `ItemModule`, `MobModule`, `StatisticsModule`, `UiModule`, `ZoneModule` (FR-020, FR-023)
- [X] T063 [US4] `ReloadCommand` in `rpg-plugin/.../command/admin/ReloadCommand.java` — `/rpg reload`, genau der Name, den `ZoneModule`s Javadoc bereits nennt
- [X] T064 [US4] Abgelehntes Neuladen: `ConfigValidationException` in eine Meldung mit Datei, Dokumentpfad und Grund übersetzen; Server läuft weiter (FR-022)
- [X] T065 [US4] **Logzeile, die die Module beim Namen nennt**, deren Haken gelaufen ist — ohne sie ist FR-023 nicht prüfbar, weil ein „ok" nichts darüber sagt, ob sechs Module erreicht wurden oder eines (FR-023a)
- [X] T066 [US4] Audit-Eintrag `config_reloaded` schreiben (FR-028)
- [X] T067 [P] [US4] `ReloadReachesAllModulesTest` in `rpg-plugin/src/test/java/rpg/plugin/command/admin/ReloadReachesAllModulesTest.java` — nach einem erfolgreichen Neuladen hat **jedes** der sechs Module seinen Haken ausgeführt; fehlt eines, wird der Test rot (FR-023)
- [X] T068 [P] [US4] `RejectedReloadTouchesNothingTest` in `rpg-plugin/src/test/java/rpg/plugin/command/admin/RejectedReloadTouchesNothingTest.java` — eine abgelehnte Quelle lässt **alle** Module auf der vorherigen Fassung und führt **keinen** Haken aus (FR-021, SC-005)
- [ ] T069 [US4] quickstart 16–20 auf dem echten Server, einschließlich 17a und 19a — eine Balancing-Änderung ist damit **ohne Serverneustart** wirksam, also in Sekunden statt einer vollen Startzeit (SC-004)

> **Schritt 19, 19a und 20 sind die eigentliche Prüfung dieses Blocks.** Alles davor testet ein
> Kommando; diese drei testen eine Zusage, die seit B01 unbelegt im Raum steht.

---

## Phase 7: User Story 5 — Eine Kreatur entsteht auf Ansage (Priority: P3)

**Goal**: Kreaturen auf Zuruf setzen, ohne das Spawn-Budget zu verfälschen.

**Independent Test**: Jede Art setzen, über Chunk-Neuladen und Serverneustart beobachten
(quickstart 21–25).

- [X] T070 [US5] **ADR-1 schreiben** in `02-decisions.md`: `HordeRegistry.Entry` bekommt eine Herkunft. Begründung, Alternative (zweiter Bestand) und Auswirkung nach dem Muster der übrigen ADRs — **vor** der Umsetzung
- [X] T071 [US5] `Origin`-Feld (`BUDGET` / `ADMIN`) an `HordeRegistry.Entry` in `rpg-core/src/main/java/rpg/core/mob/HordeRegistry.java` ergänzen
- [X] T072 [US5] `total()` und `countIn(zoneKey)` in `HordeRegistry.java` auf `BUDGET` einschränken, `countInChunk()` **unverändert alle** zählen (Chunkdichte ist eine Lastgrenze, keine Budgetfrage), `countAdmin()` ergänzen (data-model.md)
- [X] T073 [US5] Alle vorhandenen Aufrufer von `HordeRegistry.Entry` in `rpg-core` und `rpg-platform` auf den neuen Konstruktor nachziehen — der Spawnplaner setzt `BUDGET`
- [X] T074 [US5] Obergrenze für handgesetzte Kreaturen in `rpg-plugin/src/main/resources/mobs.yml` und ihrem Schema ergänzen, **Voreinstellung 20** (Prinzip V) — hoch genug für jede Abnahme, niedrig genug, dass die Umgehung beziffert bleibt
- [X] T075 [US5] `MobSpawnCommand` in `rpg-plugin/.../command/admin/MobSpawnCommand.java` — `/rpg mob spawn <Art>` über `PaperMobPlacer.place(kind, location, zoneKey)`, danach Eintrag mit `Origin.ADMIN` (FR-018, FR-019)
- [X] T076 [US5] Erreichte Obergrenze: Abbruch mit Meldung, die die Grenze nennt (FR-019a)
- [X] T077 [US5] Audit-Eintrag `mob_spawned` schreiben (FR-028)
- [X] T078 [P] [US5] `AdminSpawnDoesNotConsumeBudgetTest` in `rpg-core/src/test/java/rpg/core/mob/AdminSpawnDoesNotConsumeBudgetTest.java` — `total()` und `countIn()` bleiben unverändert, `countAdmin()` steigt, `countInChunk()` zählt beide (FR-019a, SC-010)
- [X] T079 [P] [US5] `AdminSpawnSurvivesChunkReloadTest` in `rpg-plugin/src/test/java/rpg/plugin/command/admin/AdminSpawnSurvivesChunkReloadTest.java` — ADR-050 entfernt sie nicht, weil sie ordnungsgemäß in der Registry steht (FR-019)
- [X] T080 [P] [US5] `NoPersistentAdminMarkerTest` in `rpg-plugin/src/test/java/rpg/plugin/command/admin/NoPersistentAdminMarkerTest.java` — **FR-019b als Test**: die Herkunft wird **nicht** in den `PersistentDataContainer` geschrieben, und nach einem Neustart lebt keine handgesetzte Kreatur mehr, weil die Registry nicht wiederhergestellt wird und ADR-050 aufräumt. Beide Zählungen stehen dann wieder auf null
- [ ] T081 [US5] quickstart 21–25 auf dem echten Server, einschließlich 23a

---

## Phase 8: User Story 6 — Stufe und Klasse lassen sich richtigstellen (Priority: P3)

**Goal**: Support-Eingriffe über die vorhandenen öffentlichen Wege, mit Spur im Audit-Log.

**Independent Test**: Setzen und Zurücksetzen an einem Testcharakter, danach Kontrolle des
Audit-Logs (quickstart 26–28a).

- [X] T082 [US6] **ADR-2 schreiben** in `02-decisions.md`: kein Setzen einzelner Attribute (FR-024a). Begründung: `StatEngine` hat bewusst keinen Setzer, `SourceKind` ist geschlossen und seine Deklarationsreihenfolge *ist* die Summationsreihenfolge; ein Admin-Wert bräuchte einen siebten Eintrag darin und wäre nach dem nächsten Anmelden trotzdem weg
- [X] T083 [US6] `SetCommand` in `rpg-plugin/.../command/admin/SetCommand.java` — `/rpg set level|xp <Spieler> <Wert>` über `Progression.setProgress(actorId, characterId, level, xpInLevel)` (FR-024)
- [X] T084 [US6] `/rpg set class <Spieler> <Klasse>` über `ClassSelection.choose(...)` — derselbe öffentliche Weg wie eine Klassenwahl durch den Spieler; `CompletableFuture` über `TickReturn` zurückführen, **kein `join()`** (FR-024, Prinzip I)
- [X] T085 [US6] **Halter-ID ist nicht Spieler-ID**: die Charakter-ID über `StatEngine.characterIdOf` bzw. die Sitzung auflösen, nicht die Spieler-UUID durchreichen ([[vuntexrpg-holder-vs-character-id]] — dieser Fehler hat B08 und B11 je einmal erwischt und scheitert **still**)
- [X] T086 [US6] Werte außerhalb des Bereichs ablehnen, ohne etwas zu verändern (FR-025)
- [X] T087 [US6] Audit-Einträge `progress_set` und `class_changed` schreiben — B06 schreibt den ersten bereits, der zweite kommt hinzu (FR-028)
- [X] T088 [P] [US6] `SetCommandTest` in `rpg-plugin/src/test/java/rpg/plugin/command/admin/SetCommandTest.java` — Stufe wirkt sofort, Log führt alten und neuen Stand, Wert außerhalb des Bereichs verändert nichts (FR-025)
- [X] T089 [P] [US6] `NoAttributeSetterTest` in `rpg-plugin/src/test/java/rpg/plugin/command/admin/NoAttributeSetterTest.java` — es gibt kein Kommando, das ein einzelnes Attribut setzt, und das ist Absicht (FR-024a)
- [ ] T090 [US6] quickstart 26–28a auf dem echten Server

---

## Phase 9: User Story 7 — Ein Betreiber sieht nach, ohne anzufassen (Priority: P3)

**Goal**: Lesende Einsicht in fremde Spielerdaten, auch offline.

**Independent Test**: Jede Ansicht für einen zweiten Spieler öffnen und danach prüfen, dass dessen
Daten unverändert sind (quickstart 29–31a).

- [X] T091 [US7] `InspectCommand` in `rpg-plugin/.../command/admin/InspectCommand.java` — `/rpg inspect sheet|statistics|inventory|session <Spieler>`, je Unterkommando ein eigenes Recht (FR-026)
- [X] T092 [US7] Offline-Spieler über den `PLAYER`-Argumenttyp auflösen; ein Tippfehler bricht ab und nennt den Namen, statt als leeres Profil durchzugehen (FR-026, research.md §9)
- [X] T093 [US7] Sicherstellen, dass **kein** Pfad dieser Ansichten schreibt — keine Sitzung anlegen, keinen Cache füllen, keinen Zustand berühren (FR-027)
- [X] T094 [US7] **Kein** Audit-Eintrag für lesende Zugriffe (FR-029)
- [X] T095 [P] [US7] `InspectionChangesNothingTest` in `rpg-plugin/src/test/java/rpg/plugin/command/admin/InspectionChangesNothingTest.java` — Werte des Betroffenen vor und nach jeder Ansicht identisch (FR-027). **Dass kein Audit-Eintrag entsteht, beweist nur, dass nichts protokolliert wurde, nicht dass nichts passiert ist**
- [X] T096 [P] [US7] `AnonymizedPlayerStaysAnonymousTest` in `rpg-plugin/src/test/java/rpg/plugin/command/admin/AnonymizedPlayerStaysAnonymousTest.java` — B02s Anonymisierung gilt weiter, B14 macht keinen Klarnamen wieder sichtbar
- [ ] T097 [US7] quickstart 29–31a auf dem echten Server

---

## Phase 10: User Story 8 — Das Audit-Log lesen können (Priority: P4)

**Goal**: Die Leseseite eines Logs, das seit B02 nur beschrieben wurde.

**Independent Test**: Je einen Eingriff aus jeder Gruppe ausführen und über die Abfrage
wiederfinden (quickstart 32–35).

- [X] T098 [US8] `AuditCommand` in `rpg-plugin/.../command/admin/AuditCommand.java` — `/rpg audit [Zeitraum]` über `AuditLogRepository.between(from, to)`, neueste zuerst (FR-030)
- [X] T099 [US8] Ergebnis über `TickReturn` in den Tick zurückführen — `between()` liefert ein `CompletableFuture`, **kein `join()`** (Prinzip I)
- [X] T100 [US8] Sperrzeit auf `/rpg audit` setzen (FR-032) — `/stats` und `/top` haben ihre bereits aus T042
- [X] T101 [US8] Ausgabe seitenweise, **zehn Einträge je Seite**, statt eine unbegrenzte Liste in den Chat zu schreiben
- [X] T102 [P] [US8] `AuditLogReadTest` in `rpg-persistence/src/test/java/rpg/persistence/AuditLogReadTest.java` — **gegen eine echte PostgreSQL-Instanz (Testcontainers)**: mehrere Einträge über einen Zeitraum, neueste zuerst, Fenstergrenzen beidseitig einschließend, leerer Zeitraum liefert eine leere Liste statt eines Fehlers (FR-036, Constitution VII)
- [X] T103 [P] [US8] `AuditLogStaysAppendOnlyTest` in `rpg-plugin/src/test/java/rpg/plugin/command/admin/AuditLogStaysAppendOnlyTest.java` — Architekturtest: aus B14 heraus erreicht kein anderer Schreibweg als `append` das Log (FR-031, FR-031a)
- [X] T104 [P] [US8] `EveryMutationIsAuditedTest` in `rpg-plugin/src/test/java/rpg/plugin/command/admin/EveryMutationIsAuditedTest.java` — je ein Eingriff aus jeder Werkzeuggruppe erzeugt genau einen Eintrag; lesende Kommandos erzeugen keinen (FR-028, FR-029, SC-006)
- [ ] T105 [US8] quickstart 32–35 auf dem echten Server

---

## Phase 11: Polish & Querschnitt

- [X] T106 [P] Tab-Completion-Listen begrenzen und am bereits Getippten filtern — nie die vollständige Menge senden (FR-033, Prinzip II)
- [X] T107 [P] Wiederholbare Messung in `rpg-plugin/src/test/java/rpg/plugin/command/CommandTickCostBenchmark.java` nach dem Muster von B13s `HudTickCostBenchmark`: Registrierung und Vervollständigung bleiben **unter dem Tick-Budget von 5 ms** (Prinzip II). **Kein** Lasttest — der gehört B15 (Constitution VII, ADR-031)
- [X] T108 [P] Zweite Sprachdatei aus B13 um alle B14-Schlüssel ergänzen (`messages.yml` selbst ist mit T041 schon gewachsen — diese Aufgabe fasst sie nicht noch einmal an)
- [X] T109 [P] `01-architecture.md` um B14s Kommandogerüst ergänzen
- [X] T110 [P] Blockdokument `minecraft-rpg-spec/minecraft-rpg-spec/blocks/B14-commands-permissions-admin.md` auf **Status: umgesetzt** setzen und die beiden ADR-Nummern eintragen
- [X] T111 [P] `06-open-questions.md` prüfen: hat B14 eine offene Frage beantwortet oder eine neue aufgeworfen?
- [X] T112 Die vier eingelösten ADR-Zusagen schließen — ADR-028 (`/coins`), ADR-051 (`/char`) und B12s beide (`/stats`, `/top`) sind mit dem Umzug erfüllt; in `02-decisions.md` vermerken (SC-009)
- [X] T113 Prüfen, dass **kein** Block mehr ein eigenes vorläufiges Kommando führt (SC-009)
- [X] T114 `MessageKeyValidator` muss beim Start schweigen — alle B14-Schlüssel in beiden Sprachdateien vorhanden (FR-009)
- [X] T115 Vollständiger Testlauf `./gradlew test` — alle grün, **0 übersprungen**; ein übersprungener Test ist bei MockBukkit kein Erfolg, sondern ein Nicht-Ergebnis ([[vuntexrpg-mockbukkit-skips]])
- [X] T116 `FullBootstrapTest` auf **Vollständigkeit** erweitern: jedes Kommando und jedes Recht, jetzt einschließlich der Admin-Werkzeuge aus US3–US8 (FR-035, [[vuntexrpg-block-done-criteria]])
- [ ] T117 Deploy nach [[vuntexrpg-server-deploy]] — Jar **und** geänderte YAMLs; die Frage ist „welche Datei unterscheidet sich", nicht „welche ist neu". `messages.yml` ist gewachsen und **muss** kopiert werden, sonst bricht der Start am Schlüsselprüfer ab, bevor irgendeine B14-Prüfung läuft
- [ ] T118 **Serverabnahme**: `quickstart.md` Abschnitt 3 vollständig — 40 Schritte plus die drei Vorabschritte. **Alle allein machbar**, kein zweiter Spieler nötig
- [X] T119 `/speckit-analyze` erneut laufen lassen und die Abdeckung gegen den umgesetzten Stand messen

---

## Dependencies & Execution Order

### Phasenabhängigkeiten

- **Phase 1 (Setup)**: keine Abhängigkeit
- **Phase 2 (Foundational)**: nach Setup. **Blockiert alle User Stories.** Innerhalb der Phase gilt: T007–T010 vor allem anderen
- **Phase 3 (US1)** und **Phase 4 (US2)**: nach Foundational, **gehören in eine Hand** — die Rechteprüfung ist Teil des Gerüsts, kein Aufsatz darauf
- **Phasen 5–10 (US3–US8)**: nach US1+US2, **untereinander unabhängig**. Das gilt seit T015: die Wurzel `/rpg` steht im Fundament, nicht in einer Story
- **Phase 11 (Polish)**: nach allen gewünschten Stories. T116 setzt voraus, dass die Admin-Werkzeuge existieren

### Reihenfolge nach Nutzen

US3 (Items) und US4 (Neuladen) zuerst — beide beschleunigen jede weitere Abnahme. Danach US5, US6,
US7, US8 in beliebiger Reihenfolge.

### Innerhalb einer Story

Erst das ADR, wo eines fällig ist (T070, T082) — **vor** der Umsetzung, nicht danach. Dann Modell,
dann Kommando, dann Audit, dann Test, zuletzt die Serverprüfung.

### Parallele Möglichkeiten

- T001, T002, T005, T006 parallel
- T011, T012 parallel; T016, T017, T018 parallel; T021, T022 parallel
- Die sieben Gerüsttests T026–T032 alle parallel
- Ab dem Checkpoint nach Phase 4 können US3–US8 vollständig parallel laufen
- Alle mit [P] markierten Tests innerhalb einer Story parallel

## Parallel Example: Phase 2, die Gerüsttests

```bash
Task: "ArgumentValidationTest in rpg-plugin/src/test/java/rpg/plugin/command/framework/ArgumentValidationTest.java"
Task: "SuggestionsMatchValidationTest in .../SuggestionsMatchValidationTest.java"
Task: "PermissionGateTest in .../PermissionGateTest.java"
Task: "RateLimitsTest in .../RateLimitsTest.java"
Task: "ConsoleSenderTest in .../ConsoleSenderTest.java"
Task: "PlayerArgumentTest in .../PlayerArgumentTest.java"
Task: "NoExternalPermissionPluginTest in rpg-plugin/src/test/java/rpg/plugin/NoExternalPermissionPluginTest.java"
```

---

## Implementation Strategy

### MVP zuerst (US1 + US2)

1. Phase 1: Setup
2. Phase 2: Foundational — **T007–T010 zuerst**, die Registrierungsfrage entscheidet den Rest
3. Phase 3 und 4: US1 und US2
4. **Anhalten und prüfen**: quickstart 1–10
5. Deployen — der Server hat jetzt eine einheitliche Bedienoberfläche

### Danach in Stufen

Jede weitere Story ist ein eigener, für sich prüfbarer Zuwachs. US3 und US4 lohnen sich zuerst,
weil sie jede folgende Abnahme verkürzen.

---

## Notes

- **T007 bis T010 sind keine Formalie.** Viermal in diesem Projekt war ein Test grün an genau der
  Stelle, wo es brach. Die Registrierungsfrage bekommt deshalb einen Serverstart und keinen Test.
- **Drei Aufgaben ändern etwas in `rpg-core`** (T004, T071, T072) — die erste eine Umbenennung, die
  anderen beiden durch ADR-1 gedeckt. Ein neues Interface oder eine neue Abhängigkeit entsteht dort
  nicht.
- **Ein Block ist erst fertig, wenn er verdrahtet ist** und `FullBootstrapTest` grün ist, nicht
  wenn die Modultests grün sind ([[vuntexrpg-block-done-criteria]]).
- Nach jeder Aufgabe oder Gruppe committen.
- An jedem Checkpoint kann angehalten und geprüft werden.
