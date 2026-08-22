---

description: "Aufgabenliste B08 · Fähigkeiten-Framework"
---

# Tasks: B08 · Fähigkeiten-Framework

**Input**: Design-Dokumente aus `/specs/008-ability-framework/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Enthalten. Prinzip VII der Constitution verlangt Unit-Tests ohne laufenden Server für jede
Formel und jede Regel sowie echte PostgreSQL-Instanzen für die Persistenz — Tests sind hier keine
Option, sondern Vorgabe.

**Organisation**: Nach User Story gruppiert, damit jede für sich umsetzbar und prüfbar ist.

## Format: `[ID] [P?] [Story] Beschreibung`

- **[P]**: parallelisierbar — andere Datei, keine offene Abhängigkeit
- **[Story]**: die User Story aus [spec.md](./spec.md)
- Jede Aufgabe nennt ihren Pfad

## Pfade

Vier Module aus B01, unverändert: `rpg-core` (Regeln, bukkitfrei), `rpg-persistence`,
`rpg-platform` (Paper), `rpg-plugin` (Verdrahtung und Konfiguration).

---

## Phase 1: Setup

**Zweck**: Die Pakete anlegen, in denen alles Weitere entsteht.

- [X] T001 [P] Paket `rpg-core/src/main/java/rpg/core/ability/` mit `package-info.java` anlegen — die vier Ebenen benennen (Definition, Primitives, Targeting, Runtime) und die Blockgrenze zu B04/B05/B07 beschreiben, nach dem Muster von `rpg/core/classes/package-info.java`
- [X] T002 [P] Paket `rpg-core/src/main/java/rpg/core/ability/effect/` mit `package-info.java` anlegen — je Primitive eine zustandslose Anwendung, keine Fähigkeitslogik
- [X] T003 [P] Paket `rpg-platform/src/main/java/rpg/platform/ability/` mit `package-info.java` anlegen
- [X] T004 [P] Paket `rpg-persistence/src/main/java/rpg/persistence/ability/` anlegen
- [X] T005 [P] `rpg-plugin/src/main/resources/abilities.yml` als Gerüst anlegen: `runtime.global-cooldown-ms`, `runtime.regeneration.health-combat-factor`, `runtime.regeneration.mana-combat-factor`, leerer `abilities:`-Block, Kopfkommentar mit dem Verweis auf ADR-022 und ADR-023
- [X] T006 [P] `AbilityMessageKeys` in `rpg-core/src/main/java/rpg/core/ability/AbilityMessageKeys.java` anlegen und die Schlüssel in `rpg-plugin/src/main/resources/messages.yml` eintragen — Ablehnungsgründe, Freischaltmeldung, Cooldown-Restzeit (FR-009)

---

## Phase 2: Foundational (blockierende Voraussetzungen)

**Zweck**: Alles, was jede User Story braucht. **⚠️ Vor dieser Phase kann keine Story beginnen.**

### Die Scheduler-Erweiterung (ADR-024)

- [X] T007 `runSyncOnEntityDelayed(EntityRef, Duration, Runnable)` in `rpg-core/src/main/java/rpg/core/scheduler/Scheduler.java` ergänzen — Javadoc mit der Begründung aus ADR-024: entity-gebunden und einmalig, kein Weg zu einer wiederkehrenden Aufgabe, Folia-Pfad bleibt offen
- [X] T008 Umsetzung in der Paper-Anbindung `rpg-platform/src/main/java/rpg/platform/scheduler/` über `EntityScheduler.runDelayed` — dieselbe Behandlung der nicht auflösbaren Entity wie in `runSyncOnEntity` (bereits abgebrochener Handle)
- [X] T009 [P] Die Testscheduler in `rpg-core/src/test/.../support/` und `rpg-persistence/src/test/java/rpg/persistence/support/DirectScheduler.java` um die neue Methode erweitern — mit steuerbarer Zeit, damit ein Cast im Test ohne Warten fällig wird
- [X] T010 [P] `SchedulerContractTest` in `rpg-core/src/test/java/rpg/core/scheduler/` um die neue Methode erweitern: läuft nach der Verzögerung, läuft nicht vorher, ein abgebrochener Handle verhindert den Lauf

### Der Widerruf der B07-Invariante (ADR-022)

- [X] T011 Die Prüfung `unique && kind != ACTIVE` in `rpg-core/src/main/java/rpg/core/classes/AbilityBinding.java` entfernen und das Javadoc auf ADR-022 umschreiben — die Unique ist eine der sechs, ihre Art hängt an der Klasse
- [X] T012 `validateAbilities` in `rpg-core/src/main/java/rpg/core/classes/CharacterClassDefinition.java` entkoppeln: **kein** Zusammenhang mehr zwischen `unique` und `kind` (T013a nimmt anschliessend die Zählung nach Art heraus)
- [X] T013 [P] `AbilityBindingTest` in `rpg-core/src/test/java/rpg/core/classes/` anpassen: der Test „die Unique ist aktiv" wird zu „die Unique darf passiv sein"; ein Loadout mit passiver Unique lädt
- [X] T013a `ACTIVE_ABILITIES` und `PASSIVE_ABILITIES` aus `rpg-core/src/main/java/rpg/core/classes/CharacterClassDefinition.java` **entfernen** — geprüft wird nur noch „genau sechs, höchstens eine Unique, keine doppelte ID". Die Aufteilung aktiv/passiv ist Inhalt: Warrior und Mage sind 4+2, der Rogue 3+3 (ADR-025)
- [X] T013b [P] `AbilityBindingTest` und `ClassConfigValidationTest`: ein Loadout mit drei aktiven und drei passiven lädt; eines mit fünf oder sieben Einträgen wird weiterhin abgewiesen

### Wertetypen der Definition

- [X] T014 [P] `EffectType` in `rpg-core/src/main/java/rpg/core/ability/EffectType.java` — die **sechzehn** Primitives aus [data-model.md](./data-model.md), je mit Javadoc; bei `SUMMON` und `INVISIBILITY` steht dabei, was bis B10 unwirksam bleibt
- [X] T015 [P] `TargetMode` in `rpg-core/src/main/java/rpg/core/ability/TargetMode.java` — die **neun** Zielbestimmungen, je mit dem Hinweis, ob sie mehr als ein Ziel liefern kann
- [X] T016 [P] `AbilityTrigger` in `rpg-core/src/main/java/rpg/core/ability/AbilityTrigger.java` — die fünf Trigger mit ihrem Einhängepunkt im Javadoc (research.md R6)
- [X] T017 [P] `EffectSpec` als Record in `rpg-core/src/main/java/rpg/core/ability/EffectSpec.java` mit Prüfungen V15 bis V19 und **V37 bis V42** im Konstruktor und `valueAtRank(int)` — inklusive `interval`, `maxStacks`, `stackCap`, Typfilter und den drei `METER`-Feldern
- [X] T018 [P] `TargetSpec` als Record in `rpg-core/src/main/java/rpg/core/ability/TargetSpec.java` mit Prüfungen V21 bis V24 — `maxTargets` ist **Pflichtfeld** für jeden Mehrfachmodus, kein Vorgabewert; dazu `hopRange` für `CHAIN` und `areaRadius` für `GROUND_AREA`
- [X] T018a [P] `ToggleState` in `rpg-core/src/main/java/rpg/core/ability/ToggleState.java` — `ON`, `OFF` und der fähigkeitseigene Zwischenwert für Rise & Fall (FR-052d)
- [X] T019 `Ability` als Record in `rpg-core/src/main/java/rpg/core/ability/Ability.java` mit Prüfungen V5 bis V13 und **V31 bis V36**; Listen werden kopiert, nicht übernommen (hängt an T014 bis T018a). Neue Felder: `sustained`, `duration`, `charges`, `chargeWindow`, `requiresBehindTarget`, `openWorldOnly`, `playerToggle`
- [X] T020 `AbilityConfig` in `rpg-core/src/main/java/rpg/core/ability/AbilityConfig.java` — alle Definitionen, globale Sperre, beide Kampf-Faktoren; Nachschlagen über eine unveränderliche Karte

### Konfigurationsschema

- [X] T021 `AbilityConfigSchema` in `rpg-core/src/main/java/rpg/core/ability/AbilityConfigSchema.java` — Bindung von `abilities.yml` über B01s `ConfigSchema`; die Fähigkeiten als Kartenfeld, wie B06 seine XP-Kurve gebunden hat
- [X] T022 Prüfungen V1 bis V4 sowie V31 bis V42 in `AbilityConfigSchema` — globale Sperre, Kampf-Faktoren, doppelte IDs, Pflichtfelder; jede Meldung nennt Fähigkeit und Feld
- [X] T023 [P] `AbilityConfigValidationTest` in `rpg-core/src/test/java/rpg/core/ability/` — je Prüfung V1 bis V24 und V31 bis V42 ein Fall, geprüft wird die **Meldung**, nicht der Ausnahmetyp
- [X] T024 [P] `AbilityConfigFixture` in `rpg-core/src/test/java/rpg/core/ability/` — baut die rohe verschachtelte Karte, die `abilities.yml` erzeugt, nach dem Muster von `ClassConfigFixture`

### Persistenz — die drei Registrierungen (ADR-015)

- [X] T025 Migration `rpg-persistence/src/main/resources/db/migration/V8_1__character_abilities.sql` — Tabelle nach [data-model.md](./data-model.md), Primärschlüssel `(character_id, ability_id)`, Fremdschlüssel kaskadierend
- [X] T026 [P] `AbilityState` als Record in `rpg-core/src/main/java/rpg/core/ability/AbilityState.java` mit Prüfungen im Konstruktor, nach dem Muster von `CharacterProgress`
- [X] T027 [P] `AbilityStateRepository` als Schnittstelle in `rpg-core/src/main/java/rpg/core/ability/AbilityStateRepository.java`
- [X] T028 `JdbcAbilityStateRepository` in `rpg-persistence/src/main/java/rpg/persistence/ability/JdbcAbilityStateRepository.java` — Laden je Charakter, Schreiben über den Write-Behind-Puffer, JDBC-Treiber ausdrücklich benannt
- [X] T029 **Registrierung 1 von 3**: `AggregateType.CHARACTER_ABILITIES` in `rpg-core/src/main/java/rpg/core/persistence/AggregateType.java`
- [X] T030 **Registrierung 2 von 3**: Position in `FlushCycle.WRITE_ORDER` — **nach** `CHARACTER`, wie jedes Kind
- [ ] T031 **Registrierung 3 von 3**: `AbilityModule` in `rpg-persistence/src/main/java/rpg/persistence/ability/AbilityModule.java` und Verdrahtung des Repositories
- [ ] T032 `NoDatabaseAccessPerGameEventTest` in `rpg-persistence/src/test/` läuft gegen den neuen Aggregattyp — der Test, der T029 bis T031 als Gruppe absichert. **Gehört unmittelbar hinter sie, nicht in die Polish-Phase** (ADR-015 ist aus genau diesem Vergessen entstanden)
- [ ] T033 [P] `ClassAbilityMigrationTest` in `rpg-persistence/src/test/java/rpg/persistence/ability/` — Testcontainers, die Migration legt die Tabelle mit den erwarteten Spalten und Bedingungen an

### Auskunft und Verdrahtung

- [X] T034 `AbilityRegistry` in `rpg-core/src/main/java/rpg/core/ability/AbilityRegistry.java` — die Lesemethoden aus [contracts/ability-api.md](./contracts/ability-api.md); keine davon rechnet (FR-067)
- [X] T035 `TargetResolver` als Schnittstelle in `rpg-core/src/main/java/rpg/core/ability/TargetResolver.java` — nimmt `TargetSpec` und Auslöser, liefert Ziel-IDs; die Auswahlregeln bleiben in `rpg-core`, das Nachschlagen in der Welt nicht
- [ ] T036 Modul `AbilityModule` in `rpg-core` anlegen und in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` starten — Laden und Prüfen von `abilities.yml` **nach** `classes.yml`, weil die Abgleichprüfungen beide brauchen
- [ ] T037 `AbilitySessionAttachment` in `rpg-core/src/main/java/rpg/core/ability/` — hängt sich an B04s `SessionAttachment`-Naht; Ränge laden in `onCharacterActivated`, aufräumen in `onSessionClosing`

**Checkpoint**: Das Fundament steht. Ab hier laufen die Stories.

---

## Phase 3: User Story 1 — Ein Spieler löst eine Fähigkeit aus (P1) 🎯 MVP

**Ziel**: Rechtsklick auf einem Hotbar-Slot löst eine aktive Fähigkeit aus, kostet Mana und startet
Cooldown und globale Sperre.

**Independent Test**: Ein Warrior mit einer Testfähigkeit klickt rechts; das Mana sinkt, der Effekt
wirkt, ein zweiter Klick wird abgewiesen.

> **Bewusst mit *einem* Primitive und *einer* Zielbestimmung.** Wer zuerst zwölf Primitives baut,
> prüft zwölfmal denselben ungeprüften Rahmen (plan.md, Phase-2-Hinweis 1). Die Fähigkeit für diese
> Story existiert nur im Test — die ausgelieferten Loadouts kommen in US6.

### Tests für User Story 1 ⚠️

> Diese Tests werden **zuerst** geschrieben und müssen fehlschlagen, bevor implementiert wird.

- [ ] T038 [P] [US1] `AbilityRuntimeTest` in `rpg-core/src/test/java/rpg/core/ability/` — die sechs Akzeptanzfälle aus US1: Auslösung wirkt, zweite Auslösung abgewiesen, zu wenig Mana, globale Sperre, Linksklick wirkungslos, kein aktiver Charakter
- [ ] T039 [P] [US1] `AbilityRuntimeTest`: SC-003 — drei Ablehnungsgründe mal 1000 Versuche, null Durchbrüche, und **kein** Verbrauch bei Ablehnung
- [ ] T040 [P] [US1] `TargetResolutionTest` in `rpg-core/src/test/java/rpg/core/ability/` — `SELF` liefert genau den Auslöser; `RADIUS` respektiert die Obergrenze und wählt nach aufsteigendem Abstand (FR-021)
- [ ] T041 [P] [US1] `AbilityTriggerListenerTest` in `rpg-platform/src/test/java/rpg/platform/ability/` mit MockBukkit — Rechtsklick löst aus, Linksklick löst weder Fähigkeit noch Nahkampf aus

### Umsetzung für User Story 1

- [ ] T042 [P] [US1] `AbilityResult` in `rpg-core/src/main/java/rpg/core/ability/AbilityResult.java` — die acht Ergebnisse aus [contracts/ability-api.md](./contracts/ability-api.md), jedes mit seinem Message-Schlüssel; ausgeworfen wird nur, was ein Programmfehler ist
- [ ] T043 [US1] `AbilityRuntime` in `rpg-core/src/main/java/rpg/core/ability/AbilityRuntime.java` mit `trigger` — Prüfreihenfolge: Charakter aktiv, freigeschaltet, kein laufender Cast, globale Sperre, Einzel-Cooldown, Mana (FR-024, FR-025)
- [ ] T044 [US1] Cooldown-Arithmetik in `AbilityRuntime`: Vergleich zweier Zeitstempel, **kein** Herunterzählen (FR-026); Verkürzung um die Cooldown-Reduktion des Auslösers, hart gedeckelt bei 40 % (FR-027)
- [ ] T045 [US1] Globale Sperre in `AbilityRuntime` — ein Zeitstempel je Charakter, gesetzt beim **Beginn** der Auslösung (FR-029)
- [ ] T046 [P] [US1] `DamageEffect` in `rpg-core/src/main/java/rpg/core/ability/effect/DamageEffect.java` — über `CombatPipeline.abilityDamage`, `amount` als **Faktor** auf das Schadensattribut (FR-012, FR-013)
- [ ] T047 [US1] Fehlerbarriere je Effekt in `rpg-core/src/main/java/rpg/core/ability/effect/EffectDispatcher.java` — eine Ausnahme wird abgefangen, mit der Kennung der Fähigkeit protokolliert und auf das eine Ereignis begrenzt; die übrigen Effekte laufen weiter (FR-017)
- [ ] T048 [US1] Der Wertestand wird **einmal beim Auslösen** gezogen und bis zum Ende der Handlung gehalten (FR-018) — dieselbe Regel, die ADR-013 für `StatSnapshot` festgehalten hat
- [ ] T049 [P] [US1] `PaperTargetResolver` in `rpg-platform/src/main/java/rpg/platform/ability/PaperTargetResolver.java` mit `SELF` und `RADIUS` über `World.getNearbyEntities` — räumlicher Index statt linearer Iteration (FR-022)
- [ ] T050 [US1] Zielberechtigung im Resolver: kein Ziel, das nach B05s Regel nicht angegriffen werden darf (FR-023)
- [ ] T051 [P] [US1] `AbilityItemTag` in `rpg-platform/src/main/java/rpg/platform/ability/AbilityItemTag.java` — zweite Marke im `PersistentDataContainer` mit der Fähigkeits-ID, neben B07s `BoundItemTag`; die Fähigkeit wird **nie** aus dem Material abgeleitet (FR-058)
- [ ] T052 [US1] `AbilityHotbar` in `rpg-platform/src/main/java/rpg/platform/ability/AbilityHotbar.java` — Belegung nach FR-055: Slot 0 Waffe, 1 bis 4 aktive Fähigkeiten nach Freischaltstufe, 5 aufwärts Marker; nicht freigeschaltete Slots bleiben leer (FR-056)
- [ ] T053 [US1] Die Fähigkeits-Items tragen `BoundItemTag` und erben damit die Sperre aus B07s `EquipmentLockListener` (FR-057) — **kein neuer Sperrcode**, nachgewiesen durch Test statt behauptet
- [ ] T054 [US1] `AbilityTriggerListener` in `rpg-platform/src/main/java/rpg/platform/ability/AbilityTriggerListener.java` — `PlayerInteractEvent`, Auslösung bei `RIGHT_CLICK_AIR` und `RIGHT_CLICK_BLOCK`
- [ ] T055 [US1] Linksklick-Sperre, **beide Wege**: `PlayerInteractEvent` bei `LEFT_CLICK_*` abbrechen **und** den Schlag auf eine Entity abweisen, bevor B05s `VanillaDamageListener` ihn als Nahkampf nimmt (FR-054, research.md R4)
- [ ] T056 [US1] Verdrahtung in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` — Listener registrieren, `AbilityRuntime` in die Registry

**Checkpoint**: Eine Fähigkeit lässt sich auslösen. Kosten, Cooldown und Sperre greifen serverseitig.

---

## Phase 4: User Story 2 — Passive Fähigkeiten wirken von selbst (P1)

**Ziel**: Die fünf Trigger arbeiten, damit Lifesteal, Ausweichen, Second Life und Arkane Sammlung
möglich sind.

**Independent Test**: Ein Warrior mit Lifesteal heilt beim Austeilen den konfigurierten Anteil des
tatsächlich zugefügten Schadens.

### Tests für User Story 2 ⚠️

- [ ] T057 [P] [US2] `PassiveTriggerTest` in `rpg-core/src/test/java/rpg/core/ability/` — je Trigger ein Fall; Lifesteal heilt den **nach Mitigation** zugefügten Betrag, nicht den rohen
- [ ] T058 [P] [US2] `PassiveTriggerTest`: Heilung über das Maximum verpufft ohne Fehler; eine noch nicht freigeschaltete Passive wirkt nicht
- [ ] T059 [P] [US2] `OnDeathTriggerTest` in `rpg-core/src/test/java/rpg/core/ability/` — Wiederbelebung bei Chance 1.0, danach Cooldown; innerhalb des Cooldowns regulärer Tod; **`kill` ist nicht abfangbar** (FR-051)
- [ ] T060 [P] [US2] `DoubleJumpListenerTest` in `rpg-platform/src/test/java/rpg/platform/ability/` mit MockBukkit — zweiter Sprung trägt, dritter vor Bodenkontakt nicht

### Umsetzung für User Story 2

- [ ] T061 [US2] `PassiveDispatcher` in `rpg-core/src/main/java/rpg/core/ability/PassiveDispatcher.java` — löst die passiven Fähigkeiten eines Charakters nach Trigger auf, ohne je Ereignis zu allokieren
- [ ] T062 [US2] Interceptor auf `PipelineStage.MODIFIERS` für `ON_DAMAGE_TAKEN` — dort ist der Schaden noch abweisbar, was Ausweichen braucht (research.md R6)
- [ ] T063 [US2] Interceptor auf `PipelineStage.APPLICATION` für `ON_DAMAGE_DEALT` und `ON_DEATH` — dort steht der tatsächlich zugefügte Betrag, was Lifesteal braucht
- [ ] T064 [US2] `ON_KILL` an B05s Todesereignis hängen
- [ ] T065 [US2] `ALWAYS` als Dauerwirkung: meldet einen `ModifierSet` über `StatEngine.apply` an und entfernt ihn beim Charakterwechsel oder Verlust der Freischaltung (FR-052)
- [ ] T066 [US2] Wahrscheinlichkeit je Passive, geprüft bei **jedem** Auftreten des Triggers (FR-049); der Zufallsgenerator ist einspeisbar, damit der Test ihn festnageln kann
- [ ] T067 [US2] Eigener Cooldown für Passive — geprüft wie bei aktiven, aber ohne Auslösung durch den Spieler (FR-048)
- [ ] T068 [P] [US2] `LifestealEffect` in `rpg-core/src/main/java/rpg/core/ability/effect/LifestealEffect.java` — Anteil des zugefügten Schadens als Heilung, **kein neues Attribut** (FR-016, ADR-022)
- [ ] T069 [P] [US2] `HealEffect` und `ManaRestoreEffect` in `rpg-core/src/main/java/rpg/core/ability/effect/` — beide klemmen am Maximum
- [ ] T070 [US2] `ON_DEATH` fängt den tödlichen Schaden ab und setzt stattdessen einen Anteil der Maximalgesundheit (FR-050)
- [ ] T071 [P] [US2] `DoubleJumpListener` in `rpg-platform/src/main/java/rpg/platform/ability/DoubleJumpListener.java` — `PlayerToggleFlightEvent` abbrechen, Aufwärtsimpuls geben, `allowFlight` beim Bodenkontakt zurücksetzen (research.md R7)
- [ ] T072 [P] [US2] `StatusEffectEffect` in `rpg-core/src/main/java/rpg/core/ability/effect/` — trägt Slow Fall und Verlangsamung über Vanilla-Statuseffekte
- [ ] T072a [US2] **Positionsbedingung** in `PassiveDispatcher` — eine Passive kann verlangen, dass der Treffer den Gegner von hinten traf; die Wirkung hält danach für eine Frist und wird von jedem weiteren Rückentreffer erneuert (FR-052a). Das ist Rogues Hinterhältiger Angriff
- [ ] T072b [P] [US2] `BackstabConditionTest` in `rpg-core/src/test/java/rpg/core/ability/` — der Winkel zwischen Blickrichtung des Ziels und Angriffsrichtung entscheidet; frontal wirkt nichts
- [ ] T072c [US2] **Weltbedingung** als Schnittstelle in `rpg-core/src/main/java/rpg/core/ability/WorldCondition.java` — `openWorldOnly` wird gelesen, aber **bis B09 nicht durchgesetzt**; der Start meldet das einmal als Hinweis, nicht als Fehler (FR-052b, V36)
- [ ] T072d [US2] Second Life versetzt an die **letzte Position vor dem Tod** statt zu respawnen, mit Titel und Ton (FR-052c) — kein Todesbildschirm
- [ ] T072e [US2] **Spielereinstellung** für abschaltbare Passive: `setToggle`, `toggleOf` und die Spalte `toggle_state`; Rise & Fall kennt an, aus und nur-Sprung (FR-052d)
- [ ] T072f [P] [US2] `AbilityToggleTest` in `rpg-persistence/src/test/java/rpg/persistence/ability/` — die Einstellung überlebt den Neustart und gehört dem Charakter, nicht dem Konto
- [ ] T073 [US2] Verdrahtung der Interceptoren und des Doppelsprung-Listeners in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java`

**Checkpoint**: Passive wirken. Zwei der drei Uniques sind damit möglich.

---

## Phase 5: User Story 3 — Cooldowns und Regeneration überleben das Ausloggen (P2)

**Ziel**: Cooldowns laufen über die Abwesenheit weiter, beide Ressourcen regenerieren zeitstempelbasiert.

**Independent Test**: Auslösen, ausloggen, nach 20 s einloggen — rund 40 s Restzeit auf einem
60-s-Cooldown.

> **Hier heilt ein Spieler zum ersten Mal überhaupt.** ADR-013 hatte die Vanilla-Regeneration
> abgeschaltet, ohne Ersatz; ADR-023 hat das Attribut nachgeliefert, und dieser Phase gehört seine
> Anwendung.

### Tests für User Story 3 ⚠️

- [ ] T074 [P] [US3] `ResourceRegenerationTest` in `rpg-core/src/test/java/rpg/core/ability/` — die Zerlegung eines Intervalls in Kampf- und Ruheanteil ist **exakt**, mit steuerbarer Uhr
- [ ] T075 [P] [US3] `ResourceRegenerationTest`: die Zerlegung stimmt auch, wenn zwischen zwei Abrechnungen der Kampf endete und **kein Ereignis** eintraf (research.md R3)
- [ ] T076 [P] [US3] `ResourceRegenerationTest`: beide Ressourcen klemmen am Maximum (FR-038a); ein toter Charakter und ein Halter ohne Klassenbeitrag regenerieren nicht (FR-038b)
- [ ] T077 [P] [US3] `NoTaskPerPlayerTest` in `rpg-core/src/test/java/rpg/core/ability/` — SC-005: mit einem zählenden Scheduler ist die Zahl geplanter Aufgaben null, solange kein Cast läuft. Aufbau wie `AttackWindowTest` in B05
- [ ] T078 [P] [US3] `AbilityStatePersistenceTest` in `rpg-persistence/src/test/java/rpg/persistence/ability/` mit Testcontainers — SC-004: Restzeit nach Neuladen; ein abgelaufener Cooldown wird **verworfen**, nicht geladen; eine Zeile auf Rang 1 ohne Cooldown wird gelöscht

### Umsetzung für User Story 3

- [ ] T079 [US3] `ResourceRegeneration` in `rpg-core/src/main/java/rpg/core/ability/ResourceRegeneration.java` mit `settle` und `forget` — je Charakter `lastSettledAt` und `combatEndsAt`, sonst nichts (data-model.md)
- [ ] T080 [US3] `combatEndsAt` bei jeder Abrechnung aus `CombatPipeline.remainingCombatTime` nachführen, solange der Halter im Kampf ist — **kein zweiter Kampfzähler** (FR-036)
- [ ] T081 [US3] Beide Raten aus den Attributen `healthRegen` und `manaRegen` lesen und im Kampf mit dem jeweiligen Faktor multiplizieren (FR-033, FR-033a, ADR-023)
- [ ] T082 [US3] `settle` an den Stellen aufrufen, die FR-037 nennt: vor jeder Kostenprüfung, vor jeder Schadensanwendung, vor jedem Lesen einer Ressource — **nicht** periodisch
- [ ] T083 [US3] Regeneration über die Abwesenheit hinweg: der Ladepfad rechnet einmal aus dem gespeicherten Abmeldezeitpunkt, den B03 ohnehin führt (FR-038)
- [ ] T084 [US3] Cooldown-Zeitstempel je Charakter persistieren, über den Write-Behind-Puffer und **nicht** je Auslösung (FR-031, FR-032)
- [ ] T085 [US3] Beim Laden abgelaufene Cooldowns verwerfen und Zeilen ohne Nutzinhalt löschen, damit die Tabelle nicht mit jedem Kampf wächst
- [ ] T086 [US3] `ResourceRegeneration` und das Laden der Ränge in `AbilitySessionAttachment` einhängen (T037)

**Checkpoint**: Ein verletzter Spieler heilt. Cooldowns überstehen einen Neustart.

---

## Phase 6: User Story 4 — Eine Fähigkeit mit Wirkzeit lässt sich unterbrechen (P2)

**Ziel**: Casts wirken verzögert und brechen sauber ab.

**Independent Test**: Fähigkeit mit 2 s Wirkzeit auslösen, nach 1 s Schaden nehmen — kein Effekt,
Mana unverändert, sofort wieder auslösbar.

### Tests für User Story 4 ⚠️

- [ ] T087 [P] [US4] `CastStateTest` in `rpg-core/src/test/java/rpg/core/ability/` — Wirkzeit 0 wirkt im selben Tick ohne Cast-Zustand (FR-044); Wirkzeit > 0 erzeugt einen
- [ ] T088 [P] [US4] `CastInterruptionTest` in `rpg-core/src/test/java/rpg/core/ability/` — SC-009: alle sechs Abbruchgründe, 1000 Versuche, keine Manadifferenz und kein Cooldown
- [ ] T089 [P] [US4] `CastStateTest`: eine zweite Auslösung während eines Casts wird abgewiesen (FR-040); die globale Sperre greift beim **Beginn**, der Einzel-Cooldown bei der **Wirkung**

### Umsetzung für User Story 4

- [ ] T090 [US4] `CastState` in `rpg-core/src/main/java/rpg/core/ability/CastState.java` nach [data-model.md](./data-model.md) — höchstens einer je Spieler
- [ ] T091 [US4] Cast planen über `runSyncOnEntityDelayed` (T007) — **nicht** über `runAsyncDelayed` mit Rücksprung; der Handle wird im `CastState` gehalten, damit ein Abbruch ihn stornieren kann
- [ ] T092 [US4] Kosten beim **Beginn** buchen und bei Abbruch **vollständig** erstatten (FR-041)
- [ ] T093 [US4] Einzel-Cooldown erst bei der Wirkung starten; ein abgebrochener Cast startet keinen (FR-030)
- [ ] T094 [P] [US4] `CastInterruptListener` in `rpg-platform/src/main/java/rpg/platform/ability/CastInterruptListener.java` — Schaden > 0 nach Mitigation, Slotwechsel, Tod, Charakterwechsel, Verbindungsverlust (FR-042)
- [ ] T095 [US4] `interruptOnMove` je Definition auswerten; ohne Angabe bricht Bewegung **nicht** ab (FR-043)
- [ ] T096 [US4] `CastView` als Lesesicht in `rpg-core/src/main/java/rpg/core/ability/CastView.java` — Fähigkeits-ID, Beginn, Wirkzeitpunkt, Fortschritt; `TaskHandle` und gebuchtes Mana bleiben draußen
- [ ] T097 [US4] `SHIELD` in `rpg-core/src/main/java/rpg/core/ability/effect/ShieldEffect.java` — absorbiert vor der Gesundheit, endet bei Ablauf oder Verbrauch (FR-015), mit optionalem **Schadenstyp-Filter**: Warriors Block nimmt nur physischen, Mages Magisches Schild jeden (FR-015a)

### Haltende Fähigkeiten und Ladungen *(ADR-025)*

- [ ] T097a [P] [US4] `SustainedStateTest` in `rpg-core/src/test/java/rpg/core/ability/` — höchstens eine haltende Fähigkeit je Spieler (FR-045b); ein zweiter Rechtsklick auf demselben Slot beendet sie (FR-045c)
- [ ] T097b [P] [US4] `SustainedStateTest`: die **zweiphasige Abbruchregel** — Abbruch in der Vorbereitung erstattet und startet keinen Cooldown, Beenden einer laufenden Wirkung behält beides (FR-045d, FR-045e). Der wichtigste Test dieser Phase
- [ ] T097c [P] [US4] `SustainedStateTest`: ab der Wirkung gibt es keinen Weg zurück in die Vorbereitung (FR-045f) — der Sprung ist ab dem Absprung unabbrechbar
- [ ] T097d [US4] `SustainedState` in `rpg-core/src/main/java/rpg/core/ability/SustainedState.java` nach [data-model.md](./data-model.md); das Ende wird über `runSyncOnEntityDelayed` geplant, der Handle im Zustand gehalten
- [ ] T097e [US4] `end(characterId, cause)` in `AbilityRuntime` — **der Zustand entscheidet über Erstattung und Cooldown, nicht der Aufrufer** (contracts/ability-api.md)
- [ ] T097f [US4] Die Auslösung eines besetzten Slots im `AbilityTriggerListener` auf `end` umleiten statt auf `trigger` (FR-055a) — derselbe Slot, dieselbe Taste
- [ ] T097g [US4] Haltende Fähigkeiten enden bei Tod, Charakterwechsel und Verbindungsverlust; Manatrank zusätzlich bei erlittenem, Unsichtbarkeit bei ausgeteiltem Schaden (FR-045g)
- [ ] T097h [P] [US4] `ChargeStateTest` in `rpg-core/src/test/java/rpg/core/ability/` — zwei Ladungen, Cooldown erst nach der zweiten; ungenutzte zweite Ladung setzt den Vorrat nach dem Fenster zurück, ohne dass ein Cooldown lief (FR-045j)
- [ ] T097i [US4] `ChargeState` und die Ladungsprüfung in `AbilityRuntime` — zeitstempelbasiert lazy wie alles andere (FR-045k)

**Checkpoint**: Wirkzeit und Unterbrechung stehen.

---

## Phase 7: User Story 5 — Eine neue Fähigkeit aus vorhandenen Bausteinen (P2)

**Ziel**: Der vollständige Vorrat an Primitives und Zielbestimmungen, damit eine neue Fähigkeit rein
per Konfiguration entsteht.

**Independent Test**: Eine Fähigkeit, die **nur im Test existiert**, wird angelegt und wirkt — ohne
dass eine Quelldatei angefasst wurde.

### Tests für User Story 5 ⚠️

- [ ] T098 [P] [US5] `ConfigOnlyAbilityTest` in `rpg-core/src/test/java/rpg/core/ability/` — SC-001, das Akzeptanzkriterium des Steckbriefs; die Fähigkeit steht ausschließlich in der Testkonfiguration
- [ ] T099 [P] [US5] `TargetResolutionTest` um die übrigen fünf Modi erweitern — `LOOK_DIRECTION`, `CURSOR`, `CONE`, `LINE`, `NEAREST`, je mit Obergrenze und Abstandsreihenfolge
- [ ] T100 [P] [US5] `AbilityBudgetTest` in `rpg-core/src/test/java/rpg/core/ability/` — SC-002: 100 gleichzeitig wirkende Flächenfähigkeiten im Tick-Budget; Aufbau wie `CombatBudgetTest` in B05
- [ ] T101 [P] [US5] `AbilityBudgetTest`: SC-007 — ein Flächeneffekt trifft nie mehr Ziele als seine Obergrenze, auch nicht bei 200 Kandidaten
- [ ] T102 [P] [US5] `EffectDispatcherTest` in `rpg-core/src/test/java/rpg/core/ability/effect/` — SC-010: eine Ausnahme in einem Effekt beendet weder die übrigen Effekte noch die Sitzung

### Umsetzung für User Story 5

- [ ] T103 [P] [US5] `BuffEffect` in `rpg-core/src/main/java/rpg/core/ability/effect/BuffEffect.java` — zeitlich begrenzter Modifikator auf eines der zehn Attribute, Ablauf über Zeitstempel (FR-014)
- [ ] T104 [P] [US5] `DebuffEffect` in `rpg-core/src/main/java/rpg/core/ability/effect/DebuffEffect.java` — dasselbe auf ein feindliches Ziel; **kein Schaden über Zeit**, siehe die Begründung in spec.md
- [ ] T105 [P] [US5] `DashEffect` in `rpg-core/src/main/java/rpg/core/ability/effect/DashEffect.java`
- [ ] T106 [P] [US5] `KnockbackEffect` in `rpg-core/src/main/java/rpg/core/ability/effect/KnockbackEffect.java`
- [ ] T107 [P] [US5] `TeleportEffect` in `rpg-core/src/main/java/rpg/core/ability/effect/TeleportEffect.java` — Reichweite aus `TargetSpec`, kein Versetzen in Blöcke
- [ ] T108 [US5] `ProjectileEffect` in `rpg-core/src/main/java/rpg/core/ability/effect/ProjectileEffect.java` und `AbilityProjectile` in `rpg-platform/src/main/java/rpg/platform/ability/AbilityProjectile.java` — trägt die Werte **vom Abwurf**, wie B05s `projectileDamage`, und wirkt auch, wenn der Werfer nicht mehr da ist
- [ ] T109 [US5] `PaperTargetResolver` um `LOOK_DIRECTION`, `CURSOR`, `CONE`, `LINE` und `NEAREST` erweitern — Nachfiltern einer bereits durch den räumlichen Index verkleinerten Menge
- [ ] T110 [US5] Auswahl nach aufsteigendem Abstand bei mehr Kandidaten als erlaubt, damit dieselbe Lage dasselbe Ergebnis liefert (FR-021)
- [ ] T111 [US5] Kosten und Cooldown fallen auch an, wenn ein Flächeneffekt **kein** Ziel findet — nur eine abgewiesene Auslösung ist kostenlos (Edge Case in spec.md)

### Intervall-Wirkung, die vier neuen Primitives und zwei Zielmodi *(ADR-025)*

- [ ] T111a [P] [US5] `IntervalEffectTest` in `rpg-core/src/test/java/rpg/core/ability/effect/` — ein Effekt mit `interval` wirkt über seine Dauer wiederholt; ohne Intervall genau einmal (FR-010a)
- [ ] T111b [P] [US5] `IntervalEffectTest`: **alle** laufenden Intervall-Effekte teilen sich **eine** Auswertung. Mit einem zählenden Scheduler ist die Zahl der Aufgaben eins, egal ob ein Effekt läuft oder zweihundert (FR-010b) — die Zusage, deren Bruch Prinzip II verletzt
- [ ] T111c [P] [US5] `IntervalEffectTest`: Stapeln bis zur Höchstzahl; ein weiterer Stapel auf dem Höchststand erneuert die Laufzeit, erhöht die Wirkung nicht; die Gesamtwirkung je Intervall bleibt unter dem Deckel (FR-010c)
- [ ] T111d [US5] `IntervalEffectRunner` in `rpg-core/src/main/java/rpg/core/ability/effect/IntervalEffectRunner.java` — **ein** serverweiter Durchlauf über alle laufenden Intervall-Effekte, selbst nachplanend wie B05s Sweep; niemals eine Aufgabe je Ziel oder je Effekt
- [ ] T111e [P] [US5] `EvadeEffect` in `rpg-core/src/main/java/rpg/core/ability/effect/EvadeEffect.java` — Wahrscheinlichkeit, eingehenden Schaden vollständig zu vermeiden, mit Schadenstyp-Filter (FR-016a); hängt auf `PipelineStage.MODIFIERS`, wo der Schaden noch abweisbar ist
- [ ] T111f [US5] `MeterEffect` und `MeterState` in `rpg-core/src/main/java/rpg/core/ability/` — Zähler 0 bis 100, Aufbau bei Schaden, Zerfall nach Ruhefrist, **lazy aus letztem Stand plus verstrichener Zeit** (FR-016b). Der Attributbeitrag wird bei jedem Schadensereignis neu gesetzt, nicht periodisch
- [ ] T111g [P] [US5] `MeterStateTest` in `rpg-core/src/test/java/rpg/core/ability/` — Aufbau, Ruhefrist, Zerfall bis 0, Klemmen bei 100; mit steuerbarer Uhr und **null geplanten Aufgaben**; nach dem Abmelden beginnt er wieder bei 0
- [ ] T111h [US5] `SummonEffect` in `rpg-core/src/main/java/rpg/core/ability/effect/SummonEffect.java` und die Paper-Seite in `rpg-platform/src/main/java/rpg/platform/ability/` — Wesen mit den Werten des Auslösers, greift nicht an, löst bei Ablauf oder null Gesundheit einen Effekt aus. **Die Aggro-Umlenkung bleibt eine leere Schnittstelle bis B10** und das steht im Javadoc (FR-016c)
- [ ] T111i [US5] `InvisibilityEffect` — Vanilla-Unsichtbarkeit plus Unverwundbarkeit für eine Dauer, endet bei ausgeteiltem Schaden. **Mob-Abwendung und Boss-Ausnahme bleiben bis B10 offen**; das Void bleibt tödlich (FR-016d)
- [ ] T111j [P] [US5] `PaperTargetResolver` um `CHAIN` erweitern — jedes weitere Ziel im Umkreis des **zuletzt getroffenen**, keines zweimal, bis zur Obergrenze (FR-019a)
- [ ] T111k [P] [US5] `PaperTargetResolver` um `GROUND_AREA` erweitern — an einem Cursor-Punkt verankert, eigener Radius, eigene Höchstentfernung; bleibt am Ort, auch wenn der Auslöser weggeht (FR-019b)

**Checkpoint**: Die Maschine ist vollständig. SC-001 ist bewiesen, nicht behauptet.

---

## Phase 8: User Story 6 — Jede Klasse hat ihr vollständiges Loadout (P2)

**Ziel**: Achtzehn Fähigkeiten, drei gefüllte Klassenbindungen.

**Independent Test**: Je Klasse sechs Einträge, vier aktiv, genau eine Unique; auf Stufe 1 ist genau
eine freigeschaltet, auf Stufe 45 alle sechs.

> **Setzt T011 bis T013 voraus.** Second Life ist passiv und unique — ohne den Widerruf der
> Invariante weist `AbilityBinding` das Rogue-Loadout ab.

### Tests für User Story 6 ⚠️

- [ ] T112 [P] [US6] `ShippedAbilityConfigTest` in `rpg-plugin/src/test/java/rpg/plugin/` — gegen die **ausgelieferten** `abilities.yml` und `classes.yml`, nicht gegen eine Fixtur: SC-006, je Klasse genau sechs Fähigkeiten und genau eine Unique. Warrior und Mage sind 4+2, der Rogue 3+3 (ADR-025)
- [ ] T113 [P] [US6] `ShippedAbilityConfigTest`: die Freischaltstufen sind 1, 5, 15, 25, 35, 45 und die Unique ist die letzte; auf Stufe 1 ist genau eine Fähigkeit verfügbar
- [ ] T114 [P] [US6] `AbilityConfigValidationTest` um V25 bis V30 erweitern — unbekannte ID in einer Bindung, Artenkonflikt, falsche Zahl aktiver Fähigkeiten, Slotkonflikt
- [ ] T115 [P] [US6] `AbilityHotbarTest` in `rpg-platform/src/test/java/rpg/platform/ability/` — Warrior fünf Slots (1–4 aktiv), Rogue fünf (1–3 aktiv, 4 Totem), Mage sieben (1–4 aktiv, 5–6 Marker); Slot 0 trägt immer die Waffe

### Umsetzung für User Story 6

- [ ] T116 [US6] Abgleichprüfungen V25 bis V28 in `AbilityConfigSchema` — jede in einer Bindung genannte ID ist definiert, die Arten stimmen überein, **genau sechs** Fähigkeiten je Klasse, höchstens eine Unique **ohne** Einschränkung ihrer Art. Die Aufteilung aktiv/passiv wird **nicht** geprüft (FR-006a, FR-007, ADR-025)
- [ ] T117 [US6] Slotprüfungen V29 und V30 in `AbilityConfigSchema`
- [ ] T118 [P] [US6] Warrior-Loadout in `rpg-plugin/src/main/resources/abilities.yml` — Wut (Meter), Block (Shield physisch), Sprung, Lebensraub, Wirbel (Intervall), Wutschrei nach der Tabelle in [spec.md](./spec.md)
- [ ] T119 [P] [US6] Rogue-Loadout in `abilities.yml` — Vergiftete Klinge (Intervall, stapelbar), Teleport (zwei Ladungen), Hinterhältiger Angriff (von hinten), Unsichtbarkeit, Klon, Zweites Leben. **Drei aktiv, drei passiv** — setzt T013a voraus
- [ ] T120 [P] [US6] Mage-Loadout in `abilities.yml` — Magisches Leben (Evade magisch), Blitz (Kette), Magisches Schild, Manatrank (Intervall), Blitzsturm (Bodenfläche, Intervall), Aufstieg & Fall (abschaltbar)
- [ ] T121 [US6] Die drei `abilities:`-Blöcke in `rpg-plugin/src/main/resources/classes.yml` füllen — ID, Art, Unique-Kennzeichen und Freischaltstufe je Fähigkeit; damit fällt B07s FR-045 auf die zweite Seite
- [ ] T122 [P] [US6] Alle Anzeigenamen und Beschreibungen in `rpg-plugin/src/main/resources/messages.yml` eintragen
- [ ] T123 [US6] Slots bei einem Levelaufstieg nachziehen und den Spieler unterrichten — an B06s `LevelUpEvent` (FR-059, FR-060)
- [ ] T124 [US6] Slots beim Aktivieren eines Charakters gegen den Stand setzen, damit es keine übersprungene Freischaltung gibt

**Checkpoint**: Alle drei Klassen sind spielbar. B08 ist ab hier vorzeigbar.

---

## Phase 9: User Story 7 — Fähigkeiten haben einen Rang (P3)

**Ziel**: Der Rang skaliert die Zahlen und überlebt den Neustart.

**Independent Test**: Rang erhöhen, Wirkung steigt entlang der Kurve, bleibt nach Neustart erhalten.

### Tests für User Story 7 ⚠️

- [ ] T125 [P] [US7] `AbilityRankTest` in `rpg-core/src/test/java/rpg/core/ability/` — `valueAtRank` folgt `amount + perRank × (r − 1)`; Höchstrang wird durchgesetzt
- [ ] T126 [P] [US7] `AbilityRankTest`: der Rang gehört dem **Charakter**, nicht dem Konto — zwei Charaktere desselben Kontos bleiben unabhängig (ADR-011)

### Umsetzung für User Story 7

- [ ] T127 [US7] `advanceRank` in `AbilityRuntime` — erhöht um eins, setzt den Höchstrang durch, schreibt über den Puffer (FR-062, FR-065)
- [ ] T128 [US7] Rangskalierung in allen Effekt-Anwendungen anwenden: eine Multiplikation beim Auslesen, kein zweiter Satz Definitionen (FR-063)
- [ ] T129 [US7] `RankResult` in `rpg-core/src/main/java/rpg/core/ability/RankResult.java` — Erfolg oder Höchstrang erreicht; im Javadoc steht ausdrücklich, dass **niemand** den Aufstieg bezahlt, weil es im Projekt keine Währung gibt (Workflow-Regel 5)

---

## Phase 10: User Story 8 — Balancing ohne Codeänderung (P3)

**Ziel**: Jede Zahl des Blocks steht in Konfiguration und wird beim Start geprüft.

**Independent Test**: Einen Cooldown in `abilities.yml` halbieren; nach dem Neustart gilt er.

### Tests für User Story 8 ⚠️

- [ ] T130 [P] [US8] `AbilityConfigReloadTest` in `rpg-core/src/test/java/rpg/core/ability/` — je eine Kategorie geänderter Zahl wirkt: Kosten, Cooldown, Wirkzeit, Reichweite, Obergrenze, Rangkurve, globale Sperre, Kampf-Faktor
- [ ] T131 [P] [US8] `AbilityConfigValidationTest`: SC-008 — jede fehlerhafte Konfiguration verhindert den Start **und** nennt Fähigkeit und Feld
- [ ] T132 [P] [US8] `AbilityCooldownCapTest` in `rpg-core/src/test/java/rpg/core/ability/` — eine Cooldown-Reduktion über 40 % wird gekappt (ADR-008)

### Umsetzung für User Story 8

- [ ] T133 [US8] Prüfen, dass keine Kosten-, Cooldown-, Reichweiten- oder Wirkungszahl im Code steht (FR-008) — als Quellentest nach dem Muster von `ClassSourceInvariantsTest` in B07
- [ ] T134 [US8] `AbilityMessageKeyResolutionTest` in `rpg-plugin/src/test/java/rpg/plugin/` — jeder Schlüssel des Blocks löst zu nicht-leerem Text auf und führt die Platzhalter, die der Code füllt

---

## Phase 11: Polish und Querschnitt

**Zweck**: Verdrahtung beweisen, Dokumentation nachziehen, offene Serverläufe benennen.

- [ ] T135 `FullBootstrapTest` in `rpg-plugin/src/test/java/rpg/plugin/` erweitern — `AbilityRuntime` und `AbilityRegistry` sind über die Registry auflösbar, alle Listener sind registriert, die Interceptoren hängen (ADR-012). **Keine Formalie**: ein Modul mit grünen eigenen Tests, das nicht verdrahtet ist, ist wirkungslos
- [ ] T136 [P] `FullBootstrapTest`: `abilities.yml` wird **nach** `classes.yml` geladen, damit die Abgleichprüfungen beide sehen (T036)
- [ ] T137 [P] `NoAbilityDamageBypassTest` in `rpg-core/src/test/java/rpg/core/ability/` — ein Quellentest, dass B08 Schaden ausschließlich über `CombatPipeline` erzeugt und nirgends daran vorbei (FR-068, Prinzip III)
- [ ] T138 [P] `AbilityImmutabilityTest` in `rpg-core/src/test/java/rpg/core/ability/` — `Ability`, `EffectSpec` und `TargetSpec` sind unveränderlich; Listen werden kopiert, nicht übernommen
- [ ] T139 [P] Javadoc-Durchgang über `rpg/core/ability/package-info.java` — die vier Ebenen, die Blockgrenzen und die Zusage „ab jetzt ist eine Änderung an `AbilityRegistry` ADR-pflichtig"
- [ ] T140 [P] `blocks/B08-ability-framework.md` auf **Implementiert** setzen, mit Aufgabenzahl, Testzahl und den offenen Serverpunkten
- [ ] T141 [P] `docs/05-roadmap-speckit-workflow.md`: „Empfohlener nächster Schritt" auf B11 stellen — der Block ist laut ADR-017 vor der Spezifikation neu zuzuschneiden
- [ ] T142 [P] `06-open-questions.md`: den B08-Abschnitt schließen und die Loadout-Zeile abhaken
- [ ] T143 Prüfen, dass ADR-024 den umgesetzten Stand beschreibt, und Abweichungen nachtragen (Workflow-Regel 4)
- [ ] T144 Vollständiger Durchlauf `./gradlew test` — grün, keine übersprungenen Tests (die Erinnerung aus B02/B05: MockBukkit meldet Nicht-Implementiertes als „skipped", nicht als Fehler)

### Validierungen am laufenden Paper-Server

Diese fünf zeigt kein Test, weil sie einen echten Client brauchen. Sie bleiben offen, bis ein Server
läuft — nach dem Muster, mit dem B07 seine vier Serverpunkte offengelassen hat.

- [ ] T145 Rechtsklick löst aus; **Linksklick auf ein Monster mit einem Fähigkeits-Item macht keinen Nahkampfschaden** (FR-054)
- [ ] T146 Die Hotbar sieht richtig aus: Waffe auf 0, freigeschaltete Fähigkeiten auf 1 bis 4, Marker ab 5, nicht freigeschaltete Slots leer und nicht befüllbar
- [ ] T147 Der Doppelsprung des Mage trägt zweimal, nicht dreimal, und der Fall ist verlangsamt
- [ ] T148 Die Regeneration ist spürbar und im Kampf schwächer — rund 50 s bis volle Gesundheit außerhalb des Kampfes. **Zugleich der erste Beweis überhaupt, dass ein Spieler heilt** (vor ADR-023 heilte er nicht)
- [ ] T149 Ein unterbrochener Cast lässt das Mana unverändert und fühlt sich richtig an

---

## Abhängigkeiten und Ausführungsreihenfolge

### Phasen

- **Phase 1 Setup**: keine Abhängigkeit
- **Phase 2 Foundational**: nach Setup — **blockiert alle Stories**
- **Phase 3 bis 10**: nach Phase 2, in Prioritätsreihenfolge
- **Phase 11 Polish**: nach den gewünschten Stories

### Zwischen den Stories

- **US1 (P1)**: direkt nach Phase 2. Keine Abhängigkeit zu anderen Stories
- **US2 (P1)**: nach Phase 2. Nutzt den Effekt-Dispatcher aus US1 (T047), ist sonst unabhängig
- **US3 (P2)**: nach Phase 2. Unabhängig von US1 und US2
- **US4 (P2)**: **nach T007 bis T010** — ohne die Scheduler-Erweiterung entsteht ein Provisorium über `runAsyncDelayed`, also genau die im Plan verworfene Alternative
- **US5 (P2)**: nach US1, weil sie dessen Rahmen erweitert
- **US6 (P2)**: **nach T011 bis T013 und nach US5** — siehe unten
- **US7 (P3)**: nach US5, weil der Rang alle Effekte skaliert
- **US8 (P3)**: nach US6, weil es ausgelieferte Zahlen zu justieren geben muss

### Reihenfolge, von der abzuweichen teuer wird

**US1 mit genau einem Primitive.** Wer zuerst alle zwölf baut, prüft zwölfmal denselben ungeprüften
Rahmen. Die Testfähigkeit aus US1 reicht, um Kosten, Cooldown, Sperre, Auslösung und Auskunft
durchgängig zu beweisen.

**T029 bis T032 gemeinsam.** ADR-015 ist aus dem Vergessen einer der drei Registrierungen entstanden.
T032 ist der Test, der es zeigt — er gehört unmittelbar dahinter, nicht in die Polish-Phase.

**T011 bis T013 vor T119.** Second Life ist passiv und unique. Ohne den Widerruf der Invariante weist
`AbilityBinding` das Rogue-Loadout ab, und der Fehler sieht wie ein Konfigurationsfehler aus.

**T007 bis T010 vor T091.** Ohne die Scheduler-Erweiterung entsteht der Cast über `runAsyncDelayed`
mit Rücksprung — die Alternative, die ADR-024 ausdrücklich verworfen hat. Ein Provisorium hier wieder
herauszuziehen fasst jeden Cast erneut an.

**US6 nach US5.** Das ist der wichtigste Punkt der ganzen Liste. Entstehen die achtzehn Fähigkeiten
**vor** der fertigen Maschine, beweist T098 nichts — SC-001 verlangt, dass eine neue Fähigkeit ohne
Codeänderung entsteht, und das ist nur wahr, wenn der Code vorher fertig war.

### Parallelisierbar

- Phase 1 ist vollständig parallel
- In Phase 2: T014 bis T018 (Wertetypen), T026/T027 und T033 laufen nebeneinander
- Nach Phase 2 können US1, US2 und US3 von drei Leuten gleichzeitig bearbeitet werden
- In US5 sind T103 bis T107 fünf unabhängige Dateien
- In US6 sind die drei Loadouts T118 bis T120 unabhängig
- Die Dokumentationsaufgaben T140 bis T142 sind unabhängig

---

## Umsetzungsstrategie

### MVP zuerst

1. Phase 1 Setup
2. Phase 2 Foundational — **blockiert alles**, besonders die drei Registrierungen T029 bis T031
3. Phase 3 US1
4. **Anhalten und prüfen**: eine Fähigkeit lässt sich auslösen, kostet Mana und geht auf Cooldown

Danach ist der Block belastbar, auch ohne Inhalt: die Maschine läuft, es fehlen nur Fähigkeiten.

### Inkrementelle Lieferung

1. Setup + Foundational → Fundament steht
2. US1 → eine Fähigkeit wirkt → **MVP**
3. US2 → passive Fähigkeiten wirken; zwei der drei Uniques werden möglich
4. US3 → **ein verletzter Spieler heilt zum ersten Mal**, und Cooldowns überleben das Ausloggen
5. US4 → Wirkzeit und Unterbrechung
6. US5 → der Vorrat ist vollständig, SC-001 ist beweisbar
7. US6 → alle drei Klassen sind spielbar; ab hier ist B08 vorzeigbar
8. US7 → der Rang, ohne Zahlweg
9. US8 → Balancing ohne Codeänderung

---

## Notes

- `[P]` = andere Datei, keine offene Abhängigkeit
- **Buchstaben-Suffixe** (`T013a`, `T097b`, …) sind Aufgaben, die mit ADR-025 nachträglich in eine
  bestehende Phase eingefügt wurden. Sie stehen an ihrer sachlich richtigen Stelle statt am Ende;
  durchnummerieren hätte jeden Querverweis der Datei gebrochen
- `[Story]` verweist auf die User Story aus [spec.md](./spec.md)
- Jede User Story ist für sich prüfbar; die Checkpoints sind die Haltepunkte
- Tests schlagen fehl, bevor implementiert wird
- Nach jeder Aufgabe oder Gruppe committen
- **Ein Block ist erst fertig, wenn er im Plugin verdrahtet und `FullBootstrapTest` grün ist**
  (ADR-012) — T135 und T136 sind keine Formalie
- Übersprungene Tests sind zu prüfen, nicht zu übergehen: MockBukkit meldet Nicht-Implementiertes als
  „skipped", nicht als Fehler
