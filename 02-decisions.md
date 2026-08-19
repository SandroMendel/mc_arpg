# 02 · Entscheidungs-Log (ADR)

Verbindliche Festlegungen. Änderungen hier sind Architekturänderungen und
brauchen eine Anpassung der betroffenen Blocksteckbriefe.

---

## ADR-001 · Serverplattform Paper 26.2 auf Java 25

**Status:** Entschieden

Paper statt Spigot wegen Async-Chunk-System, besserer Event-API und
Performance-Konfiguration. Zielversion Minecraft 26.2, dafür wird Java 25
benötigt. Es wird gegen die Paper-API entwickelt, nicht gegen die generische
Bukkit-API.

---

## ADR-002 · Alles auf einem einzelnen Server

**Status:** Entschieden

Kein Proxy-Netzwerk. 100–200 Spieler laufen auf einer Instanz.

**Konsequenz:** Der Server-Tick ist die knappste Ressource des gesamten Projekts.
Jede Designentscheidung wird gegen das Tick-Budget geprüft (siehe B15).

---

## ADR-003 · Eigenes HP-System, Vanilla-Health als prozentuale Anzeige

**Status:** Entschieden

Spieler und Mobs führen eigene HP-Werte in beliebiger Größenordnung.
`GENERIC_MAX_HEALTH` wird fix auf 20 gesetzt; die angezeigte Vanilla-Health
entspricht `currentHP / maxHP * 20`. Die Herzleiste ist damit dauerhaft eine
Prozentanzeige.

**Konsequenzen:**
- Für **jede** Vanilla-Schadensquelle wird explizit festgelegt, ob sie
  abgeschaltet oder auf eigenen Schaden gemappt wird (Fall, Ertrinken, Feuer,
  Lava, Void, Kaktus, Explosion, Verhungern, Wither, Poison, Instant Damage,
  Instant Health, Absorption, `/kill`).
- Gamerule `naturalRegeneration` wird deaktiviert; Sättigung wird fixiert.
- Vanilla-Schadensereignisse werden auf 0 gesetzt — dann entfällt jedoch die
  Trefferanimation. Sie muss explizit ausgelöst werden.
- Gilt gleichermaßen für Custom-Mobs.

---

## ADR-004 · Ausrüstung ist Stat-Quelle

**Status:** Entschieden

Spielerwerte setzen sich zusammen aus Klasse + Level + **Ausrüstung** (später
zusätzlich Buffs/Auren).

**Konsequenzen:**
- B11 (Items/Ausrüstung/Loot) ist Kernbestandteil, nicht optional, und wird
  parallel zu B04 spezifiziert.
- Items speichern **Template-ID und gewürfelte Roll-Werte**, niemals berechnete
  Endwerte oder gerendertes Lore. Nur so ist späteres Rebalancing möglich, ohne
  bestehende Spieleritems anzufassen.
- Speicherung über PersistentDataContainer, nicht über Lore-Parsing.
- Item-Schema wird versioniert; Migrationspfad ist Teil der Spec.

---

## ADR-005 · Vanilla-Client zum Start, Resource Pack architektonisch offen

**Status:** Entschieden

Keine Client-Voraussetzungen. Ein späterer Wechsel auf ein Resource Pack soll
ohne Umbau möglich sein.

**Konsequenzen:**
- Fähigkeitseingabe nur über Hotbar-Slots, Links-/Rechtsklick, Sneak-Kombination
  und Offhand-Swap. Keine eigenen Keybinds.
- HUD beschränkt auf Actionbar, Bossbar, Scoreboard und Title.
- Unterschiedliche Items müssen sich in unterschiedlichen **Vanilla-Materialien**
  unterscheiden.
- Rendering liegt hinter Schnittstellen (`HudRenderer`, `ItemRenderer`), damit ein
  pack-fähiger Renderer später eingesetzt werden kann.
- Im Item-Schema wird bereits jetzt ein Feld für Custom-Model-Data reserviert,
  auch wenn es ungenutzt bleibt.

---

## ADR-006 · Welt-Topologie: Hybrid aus Hauptwelt und Instanzwelten

**Status:** Entschieden (bestätigt 2026-08-19)

**Sachlage:** Paper tickt weiterhin alle Welten in einem einzigen Main-Thread.
Mehrere Welten bringen daher **keine** CPU-Parallelität. Der vermutete
Performancevorteil von Multi-World existiert nicht.

**Entscheidung:**
- Eine große, handgebaute und vorgenerierte **Kontinent-Welt** mit hartem World
  Border für alle offenen Level- und Sozialzonen. Richtgröße 6.000×6.000 bis
  10.000×10.000 Blöcke. Keine Vanilla-Weltgenerierung zur Laufzeit.
- **Separate Welten** nur für Instanzierbares und Isoliertes: Dungeons,
  Bossräume, Tutorial-/Startgebiet.
- Simulation-Distance niedrig (4–6), View-Distance höher (8–10).
- Spielerverteilung ist Designziel: mehrere gleichwertige Zonen je Levelbereich,
  kein einzelner Mega-Hub.

**Zentrale Architekturkonsequenz:** Eine `Zone` ist **niemals** eine `World`.
Zone wird definiert als `(worldId, Geometrie)`. Damit ist die Zuordnung einer
Zone zu einer eigenen Welt eine Konfigurationsänderung, kein Umbau.

---

## ADR-007 · Paper jetzt, Folia-Pfad offenhalten

**Status:** Entschieden

Entwicklung auf Paper. Folia (regionalisiertes Multithreading) hat für 26.2 nur
experimentelle Builds; Gameplay- und Concurrency-Bugs gleichzeitig zu debuggen
ist nicht tragbar.

**Konsequenzen zur Offenhaltung:**
- Der Scheduler wird gekapselt. Intern werden ausschließlich **location- oder
  entity-gebundene** Scheduling-Aufrufe verwendet, nie der globale
  Bukkit-Scheduler.
- Kein globaler veränderlicher Zustand im Gameplay-Pfad. Spielerdaten hängen am
  Spieler, Zonendaten an der Zone.

---

## ADR-008 · Stat-Engine-Grundformeln (B04)

**Status:** Entschieden (2026-08-19)

Die blockierenden Designfragen aus B04 sind geklärt und gelten als
verbindlicher Vertrag für B04–B08, B10, B11, B13.

**Entscheidungen:**
- **Stacking-Reihenfolge:** `(Base + Flat) × (1 + ΣPercent)`. Flat-Modifikatoren
  addieren sich zur Basis, Prozent-Modifikatoren werden aufsummiert und einmal
  multipliziert — keine sequenzielle Verkettung mehrerer Prozentwerte.
- **Defense-Formel:** Divisor-Modell `dmg × 100/(100+def)`. Kein separater
  harter Cap, die Kurve nähert sich asymptotisch 100% Reduktion.
- **Skalierungsverhältnis Level vs. Ausrüstung:** Ausrüstung dominant. Level
  liefert nur einen kleinen festen Stat-Zuwachs pro Level (siehe B06); der
  Großteil der Endpower kommt aus Ausrüstung, konsistent mit ADR-004.
- **attackSpeed / movementSpeed:** Beide laufen über Vanilla-Attribute
  (`GENERIC_ATTACK_SPEED`, `GENERIC_MOVEMENT_SPEED`), gesteuert durch
  B04-Modifikatoren.
- **abilityCooldown:** Prozentuale Reduktion mit hartem Cap bei 40%.
- **Sekundärwerte** (Crit-Chance, Crit-Schaden, Lifesteal, Resistenzen):
  vorerst nicht Teil von B04. Das generische Modifier-Modell trägt eine
  spätere Erweiterung ohne Architekturänderung.

**Konsequenz:** Wertebereiche/Caps je Attribut (Health, Defense, Mana,
Physical/Magic Damage, Attackspeed, Movement Speed, Ability Cooldown) sind als
grober Ausgangspunkt in `blocks/B04-stat-engine.md` dokumentiert und über
Content-Config jederzeit änderbar (Prinzip V).

---

## ADR-009 · Umsetzungsentscheidungen B01 (Core & Plattform)

**Status:** Entschieden (2026-08-19, bei der Implementierung von B01)

Sechs Entscheidungen, die während der Umsetzung von B01 getroffen wurden und von
`spec.md`/`contracts/` abweichen bzw. darüber hinausgehen. Alle sind im Code an der
jeweiligen Stelle begründet.

**1. Scheduler-Bindung über eigene Werttypen statt Bukkit-Typen**

`contracts/scheduler.md` skizziert `runSyncAtLocation(Location, …)` /
`runSyncOnEntity(Entity, …)`, fordert im selben Dokument aber, dass `rpg-core` keine
Paper-Typen kennt. Beides ist nicht gleichzeitig erfüllbar. Umgesetzt wurde
`WorldPosition(worldId, x, y, z)` und `EntityRef(entityId)` in `rpg-core`; der
`PaperSchedulerAdapter` bildet sie auf `Location`/`Entity` ab.

*Alternative:* `Scheduler` generisch über `<L, E>` parametrisieren — verworfen, weil das
die Typsignaturen für jeden nutzenden Block verkompliziert, ohne etwas zu gewinnen.

*Auswirkung:* Constitution III.1 bleibt hart erfüllt — `rpg-core` hat keine einzige
Bukkit-Referenz. `EntityRef` hält zusätzlich nur die UUID, nie das Entity-Objekt, was
Constitution I.1 stützt.

**2. `ConfigLoader` um `register()`/`ConfigHandle` erweitert**

Der Contract nennt nur `loadAndValidate` und `reloadAll`. `reloadAll()` muss aber wissen,
welche Quellen existieren, und die Module müssen den neuen Wert sehen. Ergänzt wurde
`register(Path, ConfigSchema)` mit Rückgabe eines `ConfigHandle<T>`, dessen `get()` immer
die aktuell gültige Konfiguration liefert. Der ungenutzte Typparameter an `reloadAll()`
wurde gestrichen.

*Auswirkung:* FR-003/FR-004 sind ohne Callback-Registry umsetzbar. Der Reload läuft
zweiphasig (erst alles validieren, dann veröffentlichen), womit ein gemischter alt/neu-
Zustand strukturell ausgeschlossen ist.

**3. Orchestrierungs- und Validierungslogik liegt in `rpg-core`, nicht in der Plugin-Klasse**

`tasks.md` verortet Bootstrap/Shutdown in `RpgPlugin` und den Rollback in
`YamlConfigLoader`. Umgesetzt als `ModuleBootstrap` bzw. `AbstractConfigLoader` in
`rpg-core`; `RpgPlugin` und `YamlConfigLoader` steuern sie nur an.

*Auswirkung:* Das 10-Sekunden-Shutdown-Limit (FR-012) und der Konfigurations-Rollback
(FR-004) sind ohne laufenden Server testbar — Constitution VII.1. Die getestete Logik ist
exakt die ausgelieferte Logik.

**4. Kein `paperweight-userdev`, nur die öffentliche Paper-API**

B01 benutzt keinerlei NMS. `paperweight-userdev` existiert für Mojang-Mapping-Remapping
beim NMS-Zugriff, den Constitution VI auf dokumentierte Einzelfälle beschränkt.

*Auswirkung:* Deutlich leichterer Build ohne Dev-Bundle-Toolchain. Wird eingeführt, wenn
ein Block NMS tatsächlich braucht — dort, gekapselt, mit Begründung.

**5. Paketierung: ein selbsttragendes Jar ohne Fremdcode**

Ein Paper-Plugin wird als **ein** Jar geladen. Das Standard-Jar enthielt nur
`rpg-plugin` (3,4 KB) und wäre nicht ladbar gewesen. Das `jar`-Task bündelt jetzt
`rpg-core`, `rpg-platform`, `rpg-persistence` und `rpg-content`. SnakeYAML ist
`compileOnly`, weil Paper es selbst mitbringt.

*Alternative:* Shadow-Plugin mit Relocation — nicht nötig, da keine einzige
Fremdbibliothek im Jar landet. Damit entfällt das in `research.md` benannte
Klassenlader-Konfliktrisiko vollständig.

**6. Maschinelle Durchsetzung der Scheduler-Regel statt Code-Review**

`NoGlobalSchedulerAccessTest` scannt **alle** `.java`-Dateien des Repositories (Kommentare
zuvor entfernt) auf `Bukkit.getScheduler()`, `BukkitScheduler`, `BukkitRunnable` und
`GlobalRegionScheduler` und schlägt fehl, sobald ein Treffer auftaucht.

*Auswirkung:* ADR-007 gilt automatisch auch für B02–B17, ohne dass ein Reviewer daran
denken muss. Der Test prüft zusätzlich, dass er überhaupt Dateien erreicht hat — ein
grüner Test, der nichts angesehen hat, wäre schlimmer als kein Test.

**Nebenbefund (kein Architekturentscheid, aber merkenswert):** MockBukkit implementiert
Papers Task-Cancel nicht und wirft `TestAbortedException`. Betroffene Tests erscheinen
dadurch als *skipped*, nicht als *failed* — eine Lücke, die wie Abdeckung aussieht. Bei
künftigen MockBukkit-Tests ist die Skipped-Zahl mitzuprüfen, nicht nur die Failure-Zahl.

---

## ADR-010 · Scheduler-Erweiterung und Bibliotheksauslieferung (B02-Planung)

**Status:** Entschieden (2026-08-19, bei der Planung von B02)

Zwei Entscheidungen aus der B02-Planung, die **bestehende, bereits ausgelieferte Artefakte
berühren** und deshalb hier festgehalten werden. Die übrigen B02-Technologieentscheidungen
(direktes JDBC, Flyway, zwei getrennte Pools, Spalten statt Blob, Puffergrenze in Aggregaten,
Singleton-Testcontainer) stehen mit voller Herleitung in
`specs/002-persistence-layer/research.md`.

**1. `Scheduler` von B01 wird um `runAsyncDelayed(Duration, Runnable)` erweitert**

Der Autosave-Zyklus von B02 braucht einen zeitgesteuerten Auslöser. B01s Scheduler bietet
`runSyncAtLocation`, `runSyncOnEntity` und `runAsync` — und schließt `runRepeating` ausdrücklich
aus (ADR-007, Constitution II.2).

*Geprüfte Alternativen:*

- **Rein zeitstempelbasiert-lazy** (Flush prüfen, wann immer eine Änderung vorgemerkt wird):
  verworfen. Bleibt die letzte Änderung die letzte, wird nie wieder geprüft und sie liegt beliebig
  lange ungeschrieben. Ein Absturz danach verletzt die Zusage, dass höchstens das Autosave-Intervall
  verloren geht.
- **Eigener `ScheduledExecutorService` in B02**: verworfen. Schafft eine zweite, verdeckte
  Nebenläufigkeitsquelle neben der Scheduler-Abstraktion — dieselbe Begründung, mit der B01 dem
  Event-Bus eigenes Threading verweigert hat.

*Auswirkung:* Die Ergänzung ist additiv und bricht keinen bestehenden Vertrag. Sie führt **kein**
`runRepeating` ein — ausgeschlossen war wiederkehrendes, nicht verzögertes Scheduling. Constitution
II.2 bleibt gewahrt, weil sie wiederkehrende Aufgaben **pro Spieler oder pro Entity** verbietet;
hier handelt es sich um genau eine Systemaufgabe für den gesamten Server. Paper liefert die
Grundlage bereits mit (`AsyncScheduler.runDelayed`), der Adapter in `rpg-platform` ist eine kleine
Ergänzung. B02 kann ohne diese Änderung nicht begonnen werden.

*Umgesetzt am 2026-08-19, Abgleich mit dieser Entscheidung:* Signatur
`TaskHandle runAsyncDelayed(Duration delay, Runnable task)`. Zwei Details kamen bei der Umsetzung
hinzu, die hier nicht vorgezeichnet waren: Eine **negative** Verzögerung wird abgelehnt, und eine
Verzögerung von **null** wird auf `runAsync` umgeleitet, weil Papers `runDelayed` sie
zurückweist — andernfalls wäre sie stillschweigend auf einen Tick aufgerundet worden. Fünfzehn
Tests in `PaperSchedulerAdapterTest` decken das ab, darunter ausdrücklich ein sich selbst neu
planender Zyklus als Nachweis, dass B02 ohne `runRepeating` und ohne eigenen Thread-Pool auskommt.

*Bekannte Grenze des Test-Doubles:* MockBukkits `waitAsyncTasksFinished()` führt verzögerte
Async-Aufgaben unabhängig von ihrer Restverzögerung aus. Der Test, der prüft, dass eine Aufgabe
**vor** Ablauf nicht läuft, verzichtet deshalb bewusst auf diesen Aufruf. Die Verzögerung selbst
ist damit gegen MockBukkit nur eingeschränkt belegbar; auf einem echten Server greift sie.

**2. Fremdbibliotheken werden über Papers `libraries:` geladen, nicht ins Jar geschattet**

B02 bringt erstmals Fremdbibliotheken mit (PostgreSQL-Treiber, HikariCP, Flyway). B01 hatte als
ausdrückliche Eigenschaft erreicht, dass das Plugin-Jar keine einzige Fremdklasse enthält — wegen
des Klassenlader-Konfliktrisikos in einem geteilten Bukkit-Prozess.

*Entscheidung:* Deklaration im `libraries:`-Abschnitt der `plugin.yml`; Paper löst sie beim Start
aus Maven Central auf und lädt sie in einen isolierten Klassenlader. Verfügbarkeit in der
Paper-API 26.2 über `PluginDescriptionFile.getLibraries()` geprüft.

*Alternative:* Shadow-Plugin mit Relocation — funktioniert, verlagert das Konfliktrisiko aber in
eine Relocation-Konfiguration, die bei jeder neuen Abhängigkeit gepflegt werden muss. Beim
JDBC-Treiber ist der Konflikt real, weil andere Plugins denselben Treiber mitbringen.

*Auswirkung:* Die Jar-Eigenschaft aus B01 bleibt erhalten. Der **erste** Serverstart benötigt
Netzwerkzugriff auf Maven Central; danach sind die Artefakte lokal zwischengespeichert. Als
Rückfallweg bleibt Papers `PluginLoader` mit `MavenLibraryResolver` dokumentiert, falls später
eine Abhängigkeit aus einem anderen Repository nötig wird.

---

## ADR-011 · Item-Instanzen gehören dem Charakter, nicht dem Account

**Status:** Entschieden (2026-08-19, bei der Analyse von B03)

**Kontext:** B02 hat `rpg.item_instance` mit `owner_player_id` ausgeliefert — Gegenstände hängen
dort am **Account**. Die in B03 geklärten drei Charakter-Slots je Account (einer je Klasse) machen
das mehrdeutig: Wem gehört ein Gegenstand, wenn ein Account drei Charaktere hat? Die Frage war in
keinem Blocksteckbrief entschieden und fiel erst beim Abgleich von B02s Datenmodell mit B03s
Charakter-Ebene auf.

**Entscheidung:** Ein Gegenstand gehört genau **einem Charakter**. `rpg.item_instance` erhält
`character_id` als Fremdschlüssel auf `rpg.character` und verliert `owner_player_id`.

*Begründung:* Klassengebundene Ausrüstung ist in B11 vorgesehen. Ein gemeinsamer Account-Bestand
hätte bedeutet, dass ein Warrior-Schwert im Bestand eines Spielers liegt, der gerade als Mage
spielt — erklärungsbedürftig für den Spieler und schwierig für jede Anzeige. Die Bindung an den
Charakter ist der übliche Entwurf für klassenbasierte RPGs und stimmig zu ADR-004.

*Geprüfte Alternativen:*

- **Geteiltes Account-Lager**: B02 bliebe unverändert. Verworfen wegen der Kollision mit
  klassengebundenen Items.
- **Beides — Ausrüstung am Charakter plus geteiltes Lager**: Spielerisch reichhaltiger, aber zwei
  Besitzarten mit je eigenen Regeln. Für B11 deutlich mehr Umfang, ohne dass eine Anforderung ihn
  verlangt. Bleibt später ergänzbar, weil die Charakter-Bindung die restriktivere Variante ist.
- **Auf B11 vertagen**: Verworfen. Bis dahin entstünden Gegenstände unter der Account-Bindung, und
  die Umstellung bräuchte eine Migration echter Spieleritems statt eines Spaltenwechsels auf einer
  leeren Tabelle.

**Auswirkung:** Eine Migration im Versionsraum von B03 (`V3_2`) stellt die Spalte um. Betroffen
sind `ItemInstance`, `ItemInstanceRepository.loadByOwner`, `JdbcItemInstanceRepository` und der
Löschpfad der Anonymisierung in `JdbcPlayerStateRepository`. Der Aufwand ist heute gering, weil
außerhalb der Tests noch keine Gegenstände existieren — genau deshalb wurde jetzt entschieden und
nicht erst in B11.

*Nebenwirkung auf die Anonymisierung (B02/FR-017a):* Der Löschpfad läuft künftig über
`character_id`; die Kaskade `player_state → character → item_instance` erledigt das ohnehin. Der
bestehende Integrationstest prüft, dass nach einer Anonymisierung keine Tabelle die ursprüngliche
Kennung mehr enthält, und deckt die neue Struktur damit weiterhin ab.

---

## ADR-012 — Module und Listener werden im Plugin tatsächlich verdrahtet, und das wird getestet

**Datum:** 2026-08-19
**Status:** angenommen
**Betrifft:** B01, B02, B03

**Kontext:** Beim Abschluss von B03 fiel auf, dass `RpgPlugin.modules()` eine leere Liste
zurückgab. Weder B02s `PersistenceModule` noch B03s `SessionModule` waren registriert, kein Listener
war angemeldet, und `persistence.yml` und `session.yml` wurden nirgends ausgeliefert. Beide Blöcke
waren vollständig implementiert und vollständig getestet — und auf einem echten Server hätte das
Plugin nichts davon getan. Sämtliche 300 Modultests waren grün, weil kein einziger die Frage
stellte, ob das Geschriebene auch angeschlossen ist.

**Entscheidung:** Das Plugin verdrahtet beide Module und alle fünf Listener, liefert für jedes
Modul eine Standardkonfiguration mit, und ein Test startet das komplette Plugin unter MockBukkit
gegen eine echte PostgreSQL-Instanz.

*Begründung:* Diese Fehlerklasse ist unsichtbar für Modultests und sichtbar für jeden Spieler.
Sie entsteht auch nicht durch Unachtsamkeit, sondern durch die Arbeitsteilung selbst: Jeder Block
testet, was er baut, und niemand testet die Naht dazwischen. Der einzige Ort, an dem sie auffallen
kann, ist ein Test, der dieselbe Reihenfolge durchläuft wie ein startender Server.

*Geprüfte Alternativen:*

- **Verdrahtung erst im letzten Block**: Verworfen. Jeder weitere Block hätte auf ungeprüfter
  Verdrahtung aufgebaut, und der Fehler wäre um 14 Blöcke größer geworden.
- **Nur manuelle Serverprüfung (T077–T080)**: Notwendig, aber nicht hinreichend — sie läuft nicht
  bei jeder Änderung und hätte den Zustand zwischen B02 und B03 nicht verhindert.
- **Testdatenbank nachbilden statt starten**: Verworfen. Genau die Migrationen und Pools, die im
  Start fehlschlagen können, wären dann nicht beteiligt.

**Auswirkung:** `rpg-persistence` liefert `PostgresContainer` als Test-Fixture aus, damit
`rpg-plugin` dieselbe Datenbank verwendet statt eine zweite Container-Einrichtung zu pflegen.
`BootstrapState.markShuttingDown()` überschreibt einen `FAILED`-Zustand nicht mehr: Da auf ein
gescheitertes Hochfahren sofort das Herunterfahren folgt, wurde bis dahin bei jedem Fehlstart die
Ursache durch die Folge ersetzt. Spieler werden in beiden Phasen abgewiesen, es geht also nichts
verloren — außer der Auskunft, warum.

**Für die folgenden Blöcke:** Ein Block gilt erst als fertig, wenn sein Modul in
`RpgPlugin.modules()` steht, seine Standardkonfiguration ausgeliefert wird und `FullBootstrapTest`
mit ihm grün ist.

---

## ADR-013 · Umsetzungsentscheidungen B04 (Attribut- & Stat-Engine)

**Status:** Entschieden (2026-08-20, bei der Implementierung von B04)

Fünf Entscheidungen aus der Umsetzung, die über B04 hinaus gelten.

### 1. Bündelung über eine trägergebundene Einmalaufgabe, nicht über einen Tick-Ende-Durchlauf

Eine Änderung setzt eine Vormerkung am betroffenen Träger; wer sie setzt, plant über
`Scheduler.runSyncOnEntity` genau eine Aufgabe für diesen Träger. Jede weitere Änderung davor
findet die Vormerkung gesetzt und plant nichts.

*Verworfen:* der naheliegende serverweite Durchlauf am Tick-Ende. Er bräuchte eine globale,
wiederkehrende Aufgabe — Prinzip I verbietet den globalen Scheduler, ADR-007 will den Folia-Pfad
offenhalten — und liefe in jedem Tick an, auch wenn nichts zu tun ist (Prinzip II). Die
trägergebundene Variante erreicht dasselbe Ergebnis und kostet in einem Tick ohne Änderung nichts,
weil keine Aufgabe existiert.

*Preis:* das Ergebnis liegt zu Beginn des Folgeticks vor, nicht am Ende des laufenden. FR-021 räumt
das ohnehin ein; für den einen Fall, in dem es nicht reicht — die Freigabe nach dem Anmelden —
gibt es `recalculateNow`.

### 2. Immer vollständig neu summieren, nie einen entfernten Beitrag zurückrechnen

Gleitkomma-Addition ist nicht assoziativ: `(a + b) − b` ist nicht verlässlich `a`. Inkrementelles
Fortschreiben hinterließe bei jedem Ablegen eines Ausrüstungsteils einen Rest, der sich über eine
Spielsitzung aufsummiert und in keinem Einzeltest auffällt. Vollständiges Neusummieren macht den
driftfreien Rundlauf strukturell wahr statt geprüft — und erfüllt die Reihenfolgeunabhängigkeit
gleich mit, weil Quellen in einer sortierten Karte liegen.

### 3. Eigene Tabelle `rpg.character_stats` statt zweier Spalten an `rpg.character`

Zwei Spalten anzuhängen sieht kleiner aus, koppelt aber zwei Blöcke an derselben Zeile: B03s
`JdbcCharacterRepository` müsste B04s Felder mitschreiben, und beide teilten sich einen
Revisionszähler. Eine eigene Tabelle mit eigenem Aggregattyp, eigenem Writer und eigener Position
in der Schreibreihenfolge hält die Blockgrenze aus Prinzip III.

Gespeichert werden ausschließlich die beiden Rohwerte. Maxima und Endwerte sind abgeleitet und
entstehen beim Laden neu — dieselbe Regel, die ADR-004 für Items zieht, damit Rebalancing kein
Datenmigrationsproblem wird.

### 4. `SessionAttachment` — die Naht, an der spätere Blöcke am Sitzungslebenszyklus hängen

FR-019b verlangt einen berechneten Träger **vor** der Freigabe des Spielers. Der einzige Zeitpunkt,
der früh genug liegt, ist B03s Ladevorgang selbst, der im asynchronen Vorlade-Ereignis läuft. Ein
Ereignis „Sitzung bereit" käme zu spät: der Spieler stünde für mindestens eine Runde mit falschen
Werten in der Welt.

Statt B04 in den Lebenszyklus hineinzuschreiben, bekommt B03 eine benannte Schnittstelle:
`onSessionOpened(session, bundle)` läuft nach dem Laden und vor der Freigabe,
`onSessionClosing(playerId)` vor dem Abschlussschreiben. Ausnahmen werden je Anhang abgefangen und
begrenzt. **B06, B07 und B11 benutzen dieselbe Naht** statt jeweils eigene Eingriffe.

`SessionBundle` trägt zusätzlich `CharacterResources` — analog zu den `ItemInstance`-Daten von B11,
die dort bereits liegen. Der Bündellader ist *der eine* Ladepfad, nicht B03-Privatbesitz.

### 5. Regenerationsschutz gehört zu B04, Schadensumlenkung zu B05

B04 schaltet `natural_health_regeneration` ab und hält die Sättigung fest, damit ausschließlich die
Engine die Herzleiste schreibt. Ohne das heilt Vanilla die gerade gesetzte Anzeige sichtbar wieder
hoch, und die Herzleiste ist ab dem ersten Tag falsch.

Die Grenze ist eng gezogen und wird durchgesetzt: `NoDamageInterceptionTest` scannt die Quellen und
schlägt fehl, sobald B04 einen Handler auf `EntityDamageEvent` und Verwandte registriert. Fall,
Feuer, Lava und Void gehören zu B05.

**Nebenbefund zur Paper-API:** Die Attributkonstanten heißen seit Minecraft 1.21.3 `MAX_HEALTH`,
`ATTACK_SPEED` und `MOVEMENT_SPEED` — das `GENERIC_`-Präfix aus ADR-003 und den Blocksteckbriefen
ist entfallen. Ebenso ist `GameRule.NATURAL_REGENERATION` zugunsten von
`GameRules.NATURAL_HEALTH_REGENERATION` zur Entfernung markiert. Gleiche Attribute, aktuelle Namen.

**Für die folgenden Blöcke:** Basiswerte kommen über `BaseStatContributor` (B06 Level, B07 Klasse),
Beiträge über `StatEngine.apply` mit einer `SourceId` (B08 Buffs, B09 Zonen, B11 Ausrüstung), Werte
über `StatSnapshot` — einmal zu Beginn einer Handlung gezogen und bis zu deren Ende gehalten.
