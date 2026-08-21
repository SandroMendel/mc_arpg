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

*Nachtrag 2026-08-20 — der isolierte Klassenlader hat einen Preis, der beim ersten echten
Serverstart sichtbar wurde:* Der Start scheiterte mit `No suitable driver` für
`jdbc:postgresql://localhost:5432/vuntex`, obwohl der Treiber ordnungsgemäß in `libraries:` steht
und HikariCP aus demselben Klassenlader geladen hatte. Ursache ist nicht die Auslieferung, sondern
`java.sql.DriverManager`: der scannt genau **einmal** über den System-Klassenlader nach Treibern,
und zwar lange bevor Papers Bibliotheks-Klassenlader überhaupt existiert. Ein Treiber, der nur dort
liegt, registriert sich nie — und Hikaris Rückfallweg ist `DriverManager.getDriver(url)`.

*Konsequenz, verbindlich für jede weitere Bibliothek hinter einem globalen Registry-Mechanismus:*
Die Treiberklasse wird **ausdrücklich benannt** (`HikariConfig.setDriverClassName`), damit Hikari
sie über den eigenen Klassenlader lädt, statt eine Registry zu befragen, die sie nicht sehen kann.
Verworfen wurde `Class.forName` vor dem Poolbau: das registriert den Treiber prozessweit in
`DriverManager` und hält beim Neuladen des Plugins den alten Klassenlader fest — genau der Leak, den
die Isolation vermeiden soll. Ebenfalls verworfen wurde das Schatten des Treibers ins Jar, weil das
die Eigenschaft aus B01 aufgeben würde.

*Lehre für die Teststrategie:* Alle 601 Tests waren grün, `FullBootstrapTest` eingeschlossen. Auf
dem Testklassenpfad liegt der Treiber auf dem System-Klassenlader, wo `DriverManager` ihn von selbst
findet — die Fehlerursache ist im Test **strukturell nicht erreichbar**. Geprüft wird deshalb jetzt
die Pool-Konfiguration statt der Verbindung (`DriverRegistrationTest`, drei Tests). Für Fehler
dieser Art bleibt der echte Serverstart der einzige Nachweis; das gilt auch für die noch offenen
T117/T118 aus B05.

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

---

## ADR-014 · Umsetzungsentscheidungen B05 (Kampf- & Schadens-Pipeline)

**Status:** Entschieden (2026-08-20, bei der Implementierung von B05)

Sechs Entscheidungen aus der Umsetzung, die über B05 hinaus gelten.

### 1. Die Vanilla-Quellentabelle ist erschöpfend, nicht handgepflegt

Der Blocksteckbrief nennt 17 Schadensursachen. **Paper 26.2 kennt 33.** Sechzehn hatte niemand
entschieden — darunter `FREEZE`, `CRAMMING`, `DRYOUT`, `SONIC_BOOM`, `WORLD_BORDER`,
`FALLING_BLOCK`, `DRAGON_BREATH`, `CAMPFIRE`, `THORNS` und `FLY_INTO_WALL`.

Die Zuordnung ist deshalb ein **vollständiger Switch über den Aufzählungstyp** mit einem
**Verweigerungs-Standardfall**: Eine fehlende Konstante meldet der Compiler, eine künftig
hinzukommende wird neutralisiert und einmal protokolliert. Ein Minecraft-Update kann damit keinen
Schaden durchlassen — es erzeugt eine Aufforderung zur Entscheidung.

*Für die folgenden Blöcke:* Wo ein Blocksteckbrief eine Liste von Vanilla-Konstanten aufzählt, ist
die Liste zu prüfen, nicht zu übernehmen.

### 2. Der Schadensvorgang wird wiederverwendet, die Lesesicht verfällt

`DamageContext` ist ein Objekt je Tick-Thread, das zwischen Treffern zurückgesetzt wird. Bei 150
Spielern gegen 800 Mobs sind das tausende Vorgänge je Sekunde; ein Objekt je Treffer wäre Müll, den
der Tick bezahlt.

Der Preis der Wiederverwendung ist eine Falle: Eine Stufe, die den Vorgang über sein Ende hinaus
festhält, liest später fremde Daten. Deshalb bekommen Stufen `DamageView`, und **jeder Zugriff nach
Ende des Vorgangs wirft** statt zu antworten. Der Fehler landet an der Zeile, die ihn verursacht.

### 3. Projektile tragen ihren Rohschaden, nicht ihren Schnappschuss

Beim Abschuss wird der Rohschaden berechnet und als einzelne Zahl im PersistentDataContainer des
Projektils hinterlegt. Die naheliegende Alternative — eine Karte von Projektil auf Schnappschuss —
ist ein Leck mit Ansage: ein Pfeil, der in einem entladenen Chunk verschwindet, räumt seinen Eintrag
nie auf, und das Aufräumen bräuchte genau die wiederkehrende Aufgabe, die Prinzip II vermeidet.

### 4. Vanilla-Invulnerabilitätsticks werden abgeschaltet

Vanilla macht ein Wesen nach jedem Treffer zehn Ticks unverwundbar — ein zweites, verstecktes
Angriffszeitfenster. Es hätte `attackSpeed` stillschweigend bei zwei Treffern je Sekunde gedeckelt,
und niemand wäre darauf gekommen, warum das Attribut nur zur Hälfte wirkt.

Kein Widerspruch zu B04s Spiegelung: Der Vanilla-Waffencooldown skaliert nur *Vanilla-Schaden*, den
B05 ohnehin auf null setzt. Die Spiegelung treibt damit nur noch die Cooldown-Anzeige im Client —
und die zeigt dank derselben Zahl genau die Schlagfolge, die B05 durchsetzt.

### 5. B05 stattet Mobs mit Werten aus, hinter einer Schnittstelle für B10

FR-018 lässt Wesen ohne Stat-Träger unangetastet, und kein Block vergab welche. Die vollständige
Pipeline hätte auf nichts außer Spieler gewirkt — fertig, grün getestet, im Spiel unsichtbar.
Dieselbe Fehlerklasse, für die ADR-012 geschrieben wurde. Zusätzlich wäre der lasttestpflichtige
Nachweis (150 gegen 800) bis B10 nicht durchführbar gewesen.

`MobStatProvider` liefert Zahlen aus `combat.yml` unter der Quelle `(CLASS, "mob:<TYPE>")` —
demselben Schlüssel, den B10 später ersetzt statt einen zweiten einzuführen. Was ein Mob *ist*,
bleibt vollständig B10.

### 6. `CombatModule` liegt in `rpg-core`

B02, B03 und B04 haben ihre Module in `rpg-persistence`, weil sie ein Repository aufbauen mussten.
B05 hat keine Datenbank. Das Modul dort abzulegen hätte eine Abhängigkeit vorgetäuscht, die nicht
existiert.

**Zwei Ergänzungen an B04**, die B05 gebraucht hat und die dort ohnehin fehlten:
`StatEngine.characterIdOf` (die Sitzungsregistratur beantwortet „ist eine Sitzung geladen", nicht
„ist das ein Charakter") und `StatEngine.restoreResources` in der Schnittstelle statt nur auf der
konkreten Klasse — B03s Ladepfad und B05s Mob-Ausstattung brauchen beide dasselbe.

**Zwei Namenskollisionen, die auffielen und behoben wurden:** Das Todesereignis heißt
`CombatDeathEvent`, nicht `EntityDeathEvent` — so heißt Bukkits eigene Klasse, die derselbe Listener
importiert. Und B01s Reload-Test benutzte `combat.yml` als Platzhalternamen; er heißt jetzt
`example-block.yml`, weil B05 den echten Namen belegt.

**Ein Bukkit-Detail für spätere Blöcke:** `ProjectileLaunchEvent` erbt von `EntitySpawnEvent` und
teilt sich dessen `HandlerList` mit `CreatureSpawnEvent`. Handler lassen sich für diese beiden
Ereignisse nicht getrennt zählen.

**Offen:** Der Lasttest (150 Spieler gegen 800 Mobs, p95 MSPT < 40 ms) steht noch aus. Prinzip VII
nennt B05 ausdrücklich als lasttestpflichtig — der Block gilt bis dahin nicht als abgenommen.

---

## ADR-015 · Umsetzungsentscheidungen B06 (Progression)

**Status:** Entschieden (2026-08-20, bei der Implementierung von B06)

Sechs Entscheidungen aus der Umsetzung, die über B06 hinaus gelten.

**1. `SourceKind.LEVEL` bleibt unbenutzt — und das ist kein Versehen**

B04 enthält zwei Aussagen darüber, wie ein Level auf Attribute wirkt. `SourceKind.LEVEL` ist
dokumentiert als „The character's level (B06)", während ADR-013 dasselbe dem `BaseStatContributor`
zuweist. B06 folgt ADR-013, und die Arithmetik lässt keine Wahl: `StatCalculator` legt das
Modifikatorband um den **effektiven** Basiswert, also um `definition.base()` plus Basisbeitrag. Als
FLAT-Modifikator landete das Levelwachstum in `flat[]` — innerhalb einer Klammer, die am
unveränderten Level-1-Wert hängt. Das Band „plus/minus 30 %" würde mit jedem Level relativ enger,
und die Ausrüstungsbeiträge aus B11 wären auf Level 60 messbar falsch geklammert. Die Javadoc von
`AttributeDefinition.bandFloor` nennt B06 und B07 genau dafür beim Namen.

`SourceKind.LEVEL` behält seine Berechtigung für einen *Modifikator*, der aus dem Level folgt, ohne
den Basiswert zu heben — etwa einen Meilensteinbonus alle zehn Level. B06 braucht so etwas nicht.
Der Wert wird deshalb nicht entfernt, sondern bleibt reserviert. Belegt durch
`LevelStatContributorTest.growthMovesTheBand`: dasselbe Ausrüstungsstück ist auf Level 4 mehr wert
als auf Level 1.

**2. Die Reihenfolge im Levelaufstieg ist Teil der Entscheidung, nicht Geschmack**

Erst Fortschritt setzen, dann `recalculateNow`, **dann** Leben und Mana auffüllen. Umgekehrt füllte
`ResourcePool.full` gegen das alte Maximum — ein Fehler, der bei jedem Aufstieg nur um wenige Prozent
daneben liegt und deshalb sehr lange unentdeckt geblieben wäre. Als FR-021b in der Spezifikation
verankert und in `LevelUpResourcesTest` geprüft.

*Für die folgenden Blöcke:* Wer einen Attributbeitrag ändert und danach Ressourcen anfasst, muss
zwischen beidem neu berechnen. Das gilt für B07 (Klassenwachstum) und B11 (Ausrüstung) genauso.

**3. `SessionBundle` bekommt je Block eine Liste, nicht je Block eine Abfrage**

B06 braucht den Fortschritt beim Login. Ein eigener Repository-Aufruf in `onSessionOpened` wäre eine
zweite Datenbankrunde im Anmeldepfad, und B02 sichert ausdrücklich zu, dass der Login nie auf eine
zweite Runde wartet. Also eine sechste Komponente `progress` in `SessionBundle` plus eine fünfte
Anweisung in `SessionBundleLoader` — dieselbe Erweiterung, die B04 für `resources` bekommen hat.

*Auswirkung:* Jeder weitere Block mit charakterbezogenen Daten macht es genauso. Der Record wächst
dabei, aber die Zusage „ein Laden, eine Runde" bleibt. Neun Konstruktionsstellen mussten nachgezogen
werden; der Compiler findet sie alle.

**4. Repository-Schnittstellen liegen im Blockpaket, nicht in `rpg/core/persistence/`**

`CharacterProgressRepository` liegt in `rpg/core/progression/`, wie `CharacterResourcesRepository` in
`rpg/core/stats/` (B04). In `rpg/core/persistence/` liegen die Aggregate, die B02 selbst besitzt.
Entscheidend ist beides: **in `rpg-core`**, weil die Regelschicht die Schnittstelle braucht und die
Richtung `plugin → persistence → core` nichts anderes erlaubt — und **im Blockpaket**, weil der
Besitzer daran ablesbar sein soll.

**5. Ein Fehler in der Vergabe verliert die Markierung, nicht den Zustand**

`grant` setzt den Zustand und markiert danach. Schlägt die Markierung fehl, steht der Aufstieg im
Speicher, ist aber nicht zum Schreiben vorgemerkt. Bewusst so: `onSessionClosing` markiert erneut,
bevor es freigibt, also ist der Verlust auf ein Autosave-Intervall begrenzt. Die umgekehrte
Reihenfolge hätte ein Flush zwischen Markierung und Zustandsänderung den alten Wert schreiben und die
Markierung löschen lassen — derselbe Verlust, nur ohne den Rückfall am Sitzungsende.

**6. Ein Ort wird als Wert weitergegeben, nie als Id eines toten Wesens**

`ProximityCheck` nimmt einen `WorldPoint`, nicht die Id des gestorbenen Gegners. `CombatDeathEvent`
trägt keinen Ort, und `rpg-core` hat keinen Ortstyp — die naheliegende Lösung wäre gewesen, in der
Plattformschicht `Bukkit.getEntity(id).getLocation()` aufzurufen. Das gelingt aber nur, solange B05s
Todesbehandlung noch läuft: eine Zeitbedingung, die an einem öffentlichen Erweiterungspunkt niemand
sieht und die beim ersten asynchronen Aufruf bricht. Der Listener liest den Ort dort, wo er sicher
gültig ist.

*Für B09:* `WorldPoint` mit `distanceSquaredTo` (verschiedene Welten ergeben unendlich, keine
Ausnahme) ist der bukkitfreie Ortstyp, den Zonengeometrie ebenfalls brauchen wird.

**7. Ein neuer Aggregattyp braucht drei Eintragungen, nicht eine**

Beim Schreiben des Sitzungsende-Tests (T139) fielen zwei Fehler auf, die beide zum vollständigen
Verlust des letzten Fortschritts einer Sitzung geführt hätten — und die **kein** Unit-Test der
Regelschicht sehen kann, weil sie erst an der Naht zwischen Freigabe und Flush auftreten:

1. **`FlushCycle.WRITE_ORDER` muss den Typ auflisten.** Ein Wert in `AggregateType` allein genügt
   nicht: ein fehlender Typ lässt seine Markierungen bei jedem Flush als *failed* zählen und
   niemals schreiben. Das sieht aus wie ein Datenbankproblem und ist keins. Die Liste trägt jetzt
   einen Hinweis darauf.
2. **Der letzte Wert muss vor der Freigabe beiseitegelegt werden.** Der Flush liest über die
   `liveSource`; er läuft asynchron und damit normalerweise **nach** `release`, wo nichts Lebendiges
   mehr zu lesen ist. B04 hält dafür eine `lastKnown`-Karte, die vor dem Entfernen gefüllt und beim
   Lesen geleert wird. B06 hatte sie zunächst nicht.

*Verbindlich für jeden Block mit eigenem Aggregat:* Enum-Wert **und** `WRITE_ORDER` **und**
Stash-vor-Freigabe. Die Reihenfolge im Sitzungsende ist: beiseitelegen, markieren, freigeben.

**Nebenbefund zur Teststrategie:** Sechs Fehler wurden von den eigenen Tests gefunden, nicht beim
Lesen — darunter ein echter Implementierungsfehler (`ConfigMobXpProvider` fehlte, alle Mobs gaben den
Standardbetrag) und zweimal falsche Testdaten, die eine Zusage verdeckt hätten. Die Kurvenvalidierung
hat zweimal die eigenen Testkurven abgelehnt, weil sie die strenge Monotonie verletzten. Das ist der
Nachweis, dass die Prüfung greift.

**Offen:** Der Durchlauf auf einem echten Paper-Server (Abschnitt 11 des Validierungsleitfadens).
B06 ist **nicht** lasttestpflichtig — Prinzip VII nennt B05 und B10, nicht B06.
