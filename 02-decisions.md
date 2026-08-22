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

**Status:** Für Rüstung und Waffe revidiert durch ADR-017; die Roll-Hälfte gestrichen durch
ADR-027; im Übrigen in Kraft

Spielerwerte setzen sich zusammen aus Klasse + Level + **Ausrüstung** (später
zusätzlich Buffs/Auren).

**Konsequenzen:**
- B11 (Items/Ausrüstung/Loot) ist Kernbestandteil, nicht optional, und wird
  parallel zu B04 spezifiziert.
- Items speichern die **Template-ID**, niemals berechnete Endwerte oder
  gerendertes Lore. Nur so ist späteres Rebalancing möglich, ohne bestehende
  Spieleritems anzufassen.
  > Ursprünglich stand hier „Template-ID **und gewürfelte Roll-Werte**". ADR-027
  > hat den Roll-Mechanismus gestrichen: jedes Item hat feste Attributwerte. Die
  > Zusage wird dadurch stärker — ohne Roll ist die Vorlage die einzige Quelle.
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
  *Seit ADR-017 gilt: die Höhe der Endpower und alle Wertebereiche bleiben
  unverändert, aber ihre Quelle ist die klassengebundene Ausrüstungsleiter
  (`SourceKind.CLASS`) statt erbeuteter Items (`EQUIPMENT`).*
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

---

## ADR-016 · Zwei Fehler, die die nachgeholten B05-Tests gefunden haben

**Status:** Behoben (2026-08-21)

Die elf Testlücken aus B05 waren als „der Code existiert, nur die Tests fehlen" eingetragen. Beim
Schreiben stellte sich heraus, dass zwei davon echte Fehler verdeckten — beide in der Behandlung von
Schaden, der keine brauchbare Zahl ist (FR-006).

**1. Ein `double`-Sentinel kann keine Abwesenheit ausdrücken**

`DefaultCombatPipeline.attack` unterschied „kein vorgegebener Rohschaden" von „ein vorgegebener
Rohschaden" über `Double.isNaN(presetRaw)`. Damit waren „kein Wert" und „ein kaputter Wert" dasselbe:
ein Projektil, das NaN trug — etwa weil das Schreiben des Werts fehlschlug —, bekam **vollen, aus den
Angreiferattributen gerechneten Schaden** statt neutralisiert zu werden. Genau das, was T109
ausschliessen sollte.

*Behoben* durch ein eigenes `boolean hasPreset`. Verbindlich: es gibt keinen `double`, der nicht als
Datum ankommen kann, also kann kein `double` ein Sentinel sein.

**2. Eine Ausnahme aus dem Kampfpfad ist keine Ablehnung**

`abilityDamage` mit negativem oder nicht endlichem Faktor liess `DamageFormula.rawDamage` eine
`IllegalArgumentException` aus der Pipeline werfen. FR-006 verlangt ablehnen und protokollieren, und
FR-010 verlangt, dass ein Fehler lokal bleibt — eine Ausnahme hätte die aufrufende Fähigkeit aus B08
mitgenommen. Der Faktor wird jetzt am Eingang geprüft und mit `INVALID_DAMAGE` abgelehnt.

**Warum das kein Zufall war:** Beide Fälle liegen an Eingängen, die heute niemand aufruft — B08 gibt
es noch nicht, und Projektile ohne Wert entstehen nur durch Spender oder Fehler. Solche Pfade sind
genau die, die eine fehlende Testdatei jahrelang verdeckt.

**Nebenbefund, als Invariante festgehalten:** `NoDatabaseAccessPerGameEventTest` prüft jetzt, dass
jeder Wert von `AggregateType` in `FlushCycle.WRITE_ORDER` steht und jedes Kind nach seinem Elternteil
kommt. Der erste Teil hätte den B06-Fehler aus ADR-015 Punkt 7 sofort gezeigt.

---

## ADR-017 · Rüstung und Waffe sind Klassenprogression, nicht Beute

**Status:** Entschieden *(2026-08-21)*

**Revidiert:** ADR-004 für die Slots Rüstung und Waffe.

Jede Klasse besitzt genau **einen** Rüstungs- und **einen** Waffenpfad. Beide sind feste Leitern
entlang der Vanilla-Materialien. Die Werte je Stufe stehen in der Config und sind **fest, nicht
gewürfelt**.

**Die Länge ist je Leiter konfiguriert, nicht global fünf** *(präzisiert 2026-08-21)*. Rüstung und
Waffe einer Klasse dürfen unterschiedlich lang sein:

| Klasse | Rüstung | Waffe |
|---|---|---|
| Warrior | 5 Stufen — Leder, Kupfer, Eisen, Diamant, Netherite | 6 Stufen — Schwert, Holz bis Netherite |
| Rogue | 6 Stufen — Leder, Gold, Kettenhemd, darüber Trims | 6 Stufen — Schwert, mit Gold statt Kupfer |
| Mage | 7 Stufen — durchgehend Leder, je Stufe eine Farbe | 7 Stufen — Speer, Holz bis Netherite |

Alle Leitern erreichen denselben Endwert; nur die Schrittweite unterscheidet sich. Wertekurve und
Levelanforderungen werden auf die eigene Länge der Leiter normiert.

**Sichtbarkeit ist Teil der Entscheidung, nicht Kosmetik.** Weil der Mage durchgehend Leder trägt und
der Rogue ab Stufe 4 durchgehend Kettenhemd, tragen dort **Färbung** beziehungsweise **Trim** die
Stufe — nicht das Material. Damit sind Farbe und Trim für zwei der drei Klassen Pflichtfelder und
nicht das ursprünglich vorgesehene Addon. Nebenbedingung aus Vanilla: Leder ist färbbar, Gold und
Kettenhemd sind es nicht, weshalb der Rogue oberhalb des Kettenhemds nur den Trim als Marker hat.

**Warum überhaupt eine Revision:** ADR-004 machte Ausrüstung zur dominanten Stat-Quelle und stützte
darauf gewürfelte Roll-Werte, acht Raritätsstufen und Beutetabellen je Mob und Zone. Wenn Rüstung und
Waffe klassenfest und linear sind — und B11 als Ausrüstungsslots ausdrücklich „nur Vanilla-Armor +
Waffe" festlegt —, dann hat dieser Apparat nichts mehr, woran er hängen könnte. Beide Modelle
gleichzeitig zu behaupten hätte eine Spec erzeugt, die sich selbst widerspricht.

**Konsequenzen:**

- **Die dominante Stat-Quelle ist `SourceKind.CLASS`**, nicht mehr `EQUIPMENT`. Die Klasse trägt
  Basiswerte, Rüstungsstufe und Waffenstufe. Die Summationsreihenfolge in `SourceKind` bleibt
  unverändert — `EQUIPMENT` verliert nur seine Beiträge, nicht seinen Platz.
- **Die Wertebereiche und Caps je Attribut aus ADR-008 bleiben gültig.** Nur die Quelle der Endpower
  wechselt, nicht ihre Höhe. B04 braucht keine Codeänderung.
- **B11 verliert** gewürfelte Roll-Werte, die acht Raritätsstufen und Beutetabellen für Ausrüstung.
  **B11 behält** Aufstiegsmaterial, Verbrauchbares, Durability und Reparatur sowie Kosmetik. Der
  Steckbrief B11 ist entsprechend zu korrigieren, bevor B11 spezifiziert wird.
- **B07 hält die erreichte Stufe je Charakter persistent** (Migration `V7_1`) und liefert die
  Schnittstelle zum Weiterschalten. **Wer den Aufstieg bezahlt** — Coins, Level, Material — gehört zu
  B11/B16 und bleibt hier offen; das Stufen-Schema trägt dafür einen undurchsichtigen `cost`-Block,
  den B07 nicht auslegt (Workflow-Regel 5).
- **Kosmetik ist für zwei Klassen Pflicht, nicht Addon** *(korrigiert 2026-08-21)*. Ursprünglich war
  vorgesehen, Trims und Färbung als reines Addon später nachzurüsten und im Schema nur ein Feld
  vorzuhalten. Das gilt nur noch für den Warrior. Beim Mage und beim Rogue trägt Färbung
  beziehungsweise Trim die Stufe, weil das Material sie nicht mehr unterscheidet — ohne sie wäre
  deren Progression unsichtbar. Das Feld ist dort Pflicht, und eine Konfiguration ohne es ist ein
  Startfehler.

---

## ADR-018 · Charaktergebundene Items sind unbeweglich, alle anderen nicht

**Status:** Entschieden *(2026-08-21)*

Zwei getrennte Regeln, die nicht verwechselt werden dürfen:

1. **Charaktergebundene Items** — die Klassenrüstung und die Klassenwaffe aus ADR-017 — können
   **nie** abgelegt, verschoben, verkauft, weggeworfen oder auf sonstige Weise vom Charakter gelöst
   werden. Sie sind Bestandteil des Charakters, nicht Inhalt seines Inventars.
2. **Die Spieler-Drop-Aktion ist abgeschaltet.** Kein Spieler kann Items in die Welt werfen — auch
   keine ungebundenen. Entsorgung läuft ausschließlich über die drei erlaubten Wege unten.

**Was ausdrücklich *nicht* gesperrt ist:** ungebundene Items sind frei beweglich, und **Mob-Loot ist
unberührt**. Mobs lassen über ihre Loot-Table Items fallen wie geplant (B10, B11); die Sperre ist
rein spielerseitig. Kämpfen bleibt damit Beutequelle, nicht nur XP-Quelle.

**Warum die Bindung eine Klassenregel ist und keine Item-Eigenschaft:** Die Rüstung gehört nach
ADR-017 der Klasse, nicht dem Fundstück. Ein Item kann nicht wissen, ob es gebunden ist — die Klasse
weiß es. Deshalb liegt das Prädikat bei B07.

**Konsequenzen:**

- **B07 besitzt das Prädikat, B11 erzwingt es.** B07 beantwortet „ist dieses Item Bestandteil des
  Charakters?" — es kennt die Stufenleitern. Jede Bewegungs-, Verkaufs- und Wegwerfroute in B11 fragt
  dieses Prädikat, statt eigene Annahmen über Materialien zu treffen.
- **Die Sperre kommt mit B07, nicht erst mit B11.** Sie braucht kein Item-System, nur Event-Abbruch,
  und ist damit ab M3 wirksam statt ab M4. Die Alternative hätte bedeutet, dass zwischen M3 und M4
  jeder Test dazu rot oder übersprungen ist.
- **Modulschnitt wie bei B05:** die Regel liegt in `rpg-core`, der Listener in `rpg-platform` — genau
  das Muster von `VanillaDamageListener`. B07 bleibt damit Schicht 1, obwohl es einen Bukkit-Listener
  mitbringt.
- **Abzuweisende Ereignisse:** Klick auf einen Rüstungsslot, Slot-Tausch und Offhand-Swap für
  gebundene Items; die Drop-Aktion für **alle** Items. Die Liste ist in der B07-Spec vollständig
  aufzuführen und je Ereignis zu testen — eine vergessene Route ist ein Loch in einer Regel, die als
  absolut gilt.
- **Volles Inventar ist Sache des Spielers.** Fällt Beute an und es ist kein Platz, bekommt er eine
  Warnung als Title plus Sound. Es gibt **kein** automatisches Aufräumen, keine Bank im Hintergrund
  und kein stilles Verwerfen — der Spieler schafft selbst Platz. Drei erlaubte Wege:

  | Weg | Zweck | Zuständig |
  |---|---|---|
  | Enderchest | Lagern | B11 (Lagerplatz) |
  | Verkauf an NPC | Coins gewinnen | offen, siehe unten |
  | Mülleimer-Befehl | Endgültig vernichten | B14 (Befehle) |

  Alle drei prüfen das Bindungsprädikat und weisen gebundene Items ab.
- **Der Warnhinweis braucht B13.** Title und Sound sind HUD-Ausgaben; bis B13 existiert, ist eine
  vorläufige Ausgabe zulässig, aber hinter der B13-Schnittstelle zu kapseln (ADR-005).
- **B11s Todesstrafe bleibt tragfähig.** „Kein Item-Verlust, aber Durability-Verlust" funktioniert auf
  gebundener Rüstung unverändert.
- **Admin-Ausnahmen sind hier nicht vorgesehen.** Falls Admins die Sperre umgehen sollen, gehört das
  als Permission zu B14 und ist dort zu ergänzen.

**Offen, weil kein Block ihn besitzt:** der **NPC-Händler**. Kein Blocksteckbrief B01–B17 deckt NPCs
ab — B10 beschreibt Mobs und Spawning, nicht Händler. Zudem führt `00-vision-scope.md` „kein
Crafting-/Wirtschafts-/Handelssystem" als Nicht-Ziel, während Coins seit dem 19.08. als Währung
feststehen und der NPC-Verkauf eine Coin-Quelle wäre. Beides ist vor `/specify` B11 zu klären: das
Nicht-Ziel ist zu präzisieren, und der Händler braucht einen Block.

---

## ADR-019 · Drei Klassen sind im Code festgeschrieben, ihr Inhalt ist Config

**Status:** Entschieden *(2026-08-21)*

Die **Menge** der Klassen bleibt im Code: `CharacterClass` und die Constraint
`CHECK (character_class IN ('WARRIOR','MAGE','ROGUE'))` aus `V3_1__player_characters.sql` bleiben
unangetastet. Der **Inhalt** jeder Klasse ist vollständig datengetrieben — Basiswerte,
Wachstumskurven, Ausrüstungsleitern, GUI-Material, Anzeigename und Fähigkeitsbindung kommen je
Klassen-ID aus der Config. Kein Warrior-Sonderfall im Code.

Eine vierte Klasse ist ein späteres Upgrade und kostet dann genau zwei Zeilen an zwei bekannten
Stellen: einen Enum-Wert und eine Migration.

**Konsequenzen:**

- **Das Akzeptanzkriterium in `blocks/B07-class-system.md` war falsch.** Dort stand „eine vierte
  Klasse lässt sich rein über Konfiguration ergänzen; der Test weist das nach". Das ist mit dieser
  Entscheidung nicht mehr wahr und wird im Steckbrief korrigiert, statt einen Test zu bauen, der
  etwas anderes behauptet.
- **Getestet wird stattdessen die Gegenrichtung:** der Klassenlader weist eine unbekannte Klassen-ID
  ausdrücklich ab, statt sie stillschweigend zu überspringen. Damit ist belegt, dass keine dritte
  Stelle über die Klassenmenge mitentscheidet — der Upgradepfad bleibt auf zwei Stellen begrenzt.
- **„Berserker" ist Anzeigename, nicht Klassen-ID.** Der Enum-Wert bleibt `WARRIOR`; der im Spiel
  gezeigte Name kommt wie jeder andere Klasseninhalt aus der Config. Das deckt sich mit B08, wo die
  Unique Ability des Warrior „Call of the Berserker" heißt.

---

## ADR-020 · Vor der Klassenwahl gibt es keinen Spielzustand

**Status:** Entschieden *(2026-08-21)* — **erweitert durch ADR-021:** die Auswahl erscheint bei
*jedem* Beitritt, nicht nur beim ersten. Der Kern bleibt: kein Spielzustand vor der Wahl.

Ein Spieler ohne Charakter ist **nicht spielbar**. Nach dem Laden der Sitzung (B03) öffnet sich die
Klassenauswahl und lässt sich nicht schließen; bis zur Wahl gibt es keinen Stat-Snapshot, keinen
Schaden und keine Bewegung.

```
Join
  -> Sitzung laden (B03)
  -> hat Charakter?  ja   -> normaler Spielzustand
                     nein -> GUI offen, nicht schliessbar
                             kein Stat-Snapshot
                             kein Schaden (B05 weist ab)
                             Bewegung eingefroren
  -> Wahl getroffen -> Charakter anlegen -> Spielzustand
```

**Warum nicht der Tutorialbereich:** ADR-006 sieht eine separate Tutorial-/Startwelt vor, sie wäre
also architektonisch gedeckt. Sie hätte aber einen **spielbaren Zustand ohne Charakter** verlangt —
also temporäre Stats quer durch B04, B05 und B06. Der Aufwand entsteht in jedem Block, und der Nutzen
liegt in Weltinhalten, die erst B09 liefert.

**Konsequenzen:**

- **B04 und B05 brauchen keinen „kein Charakter"-Fall.** Der Zustand ist per Konstruktion nicht
  spielbar, statt an jeder Stelle abgefragt zu werden. Das ist der eigentliche Gewinn dieser
  Entscheidung.
- **Die Tutorialwelt bleibt nachrüstbar.** Sie kann später vor die Auswahl gesetzt werden, ohne B07
  anzufassen — die Auswahl bleibt der Übergang in den Spielzustand, egal wo der Spieler vorher stand.
- **Kein Spieler kann ohne Charakter online verweilen.** Damit entfällt die Frage, ob ein solcher
  Spieler gegen das Spielerlimit aus ADR-002 zählt.
- **Zu testen ist die Nichtschließbarkeit selbst**, nicht nur der glückliche Pfad: jeder Weg aus der
  GUI heraus — Escape, Inventarwechsel, Befehl, Weltwechsel — muss zurück in die GUI führen. Dieselbe
  Vollständigkeitspflicht wie bei der Inventarsperre in ADR-018.

---

## ADR-021 · Die Auswahl ist der Eintritt in den Spielzustand — bei jedem Beitritt

**Status:** Entschieden *(2026-08-21)*

Fünf Entscheidungen aus der Umsetzung von B07, die zusammengehören, weil sie alle an derselben Stelle
hängen: dem Übergang von „verbunden" zu „im Spiel".

**1. Die Auswahl erscheint bei jedem Beitritt.** Nicht nur beim ersten. Sie ist damit auch der Weg, mit
dem ein Konto zwischen seinen bis zu drei Charakteren wechselt — eine Funktion, für die es sonst einen
eigenen Befehl oder ein zweites Menü gebraucht hätte. Ein Slot, den das Konto schon bespielt, wird
fortgesetzt statt neu angelegt; die Lore nennt Level, beide Ausrüstungsstufen und wann zuletzt gespielt
wurde. `CLASS_ALREADY_TAKEN` bleibt für das Rennen zweier gleichzeitiger Beitritte, das der
Unique-Index aus B03 entscheidet.

**2. Die Sitzung wählt keinen Charakter mehr selbst.** `PlayerSession` startet immer ohne aktiven
Charakter; `preferredCharacter()` wird nicht mehr gelesen. Das ist die Voraussetzung für Punkt 1 — eine
im Voraus getroffene Wahl müsste das Menü zurücknehmen, nachdem vier Blöcke schon Zustand dafür gebaut
haben.

**Folge, die den Ausschlag gab:** Damit verschiebt sich der Aufbau des Charakterzustands von
`SessionAttachment.onSessionOpened` nach `onCharacterActivated`. Der Rückruf bekommt deshalb das
`SessionBundle` mit, das der Login ohnehin gelesen hat, und die Sitzung hält es bis zu ihrem Ende.
Sonst müsste B04, B06 und B07 je eine zweite Abfrage stellen — auf dem Tick, im Moment des
Welteintritts.

**3. Zulieferer laufen vor der Rechnung** (`SessionAttachment.order()`). Die Modulstartreihenfolge ist
hier die falsche: B04 startet vor B06 und B07, weil beide von ihm abhängen — aber B04 *rechnet*, und
die anderen liefern, woraus. Lief B04 zuerst, klemmte `restoreResources` die gespeicherte Gesundheit
gegen einen Snapshot ohne Level und ohne Klasse. Seit ADR-017 die Klasse zur dominanten Quelle gemacht
hat, wäre das nicht ein Rundungsfehler, sondern der größte Teil des Charakters gewesen.

**4. Die Auswahl läuft ab.** Warnung nach einer Minute (Chat und Ton), Trennung nach zwei. Ein Spieler
im Menü hält eine Sitzung, den geladenen Zustand und einen Platz auf dem Server, ohne ansprechbar,
verwundbar oder beweglich zu sein — ein über Nacht offen gelassener Client hielte all das. Die Frist
verlängert sich beim Wiederöffnen nicht, sonst wäre sie durch Escape beliebig hinauszuschieben. Für den
Spieler zu wählen wäre die Alternative gewesen und ist schlechter: sie setzt jemanden in die Welt, der
nicht am Rechner sitzt.

**5. Klassenausrüstung ist unzerstörbar.** Sie ist Bestandteil des Charakters, kein Besitz (ADR-017,
ADR-018). Ein zerbrochenes Schwert ließ den Warrior waffenlos zurück, denn die Leiter ist die einzige
Quelle und Aufheben, Herstellen und Ablegen sind gesperrt — nur ein Relogin half. Der zweite Grund
wiegt schwerer: die Werte hängen an der Stufe, ein beschädigtes Item würde einen Charakter still
schwächen, ohne dass ein Attribut das abbildet. Verschleiß als Mechanik gehört, falls je gewollt, an
die Stufe.

**Konsequenzen:**

- **Der Halt im Menü ist vollständig.** Vorher wurde nur ein Wechsel des *Blocks* abgewiesen, was
  innerhalb eines Blocks freie Bewegung ließ und zum Herunterfallen reichte. Die Kamera bleibt frei:
  sie ist clientseitig, und der einzige Hebel dagegen wäre ein Teleport pro Tick, der wie eine kaputte
  Verbindung aussieht.
- **`onSessionOpened` baut keinen Charakterzustand mehr auf.** Der frühe Ausstieg bei fehlendem
  Charakter greift jetzt immer; die Arbeit steht in `onCharacterActivated`.
- **Die Frist ist eine Konstante, keine Konfiguration** — wie die Meldungssperre in
  `InventoryFullNoticeListener`. Sollte sich das als falsch erweisen, wandert sie nach `classes.yml`,
  was eine Schemaänderung ist und auf Verdacht nicht lohnt.

---

## ADR-022 · Die vier blockierenden Vorentscheidungen zu B08

**Status:** Entschieden *(2026-08-22)* — vor `/specify` B08, gemäß Workflow-Regel 2.

**1. Die Unique Class Ability ist eine der sechs, nicht die siebte.** Sie bekommt keine eigene
Kategorie, keinen eigenen Reiter und keinen Sonderplatz in der Leiste. Sie ist eine gewöhnliche
Bindung mit gesetztem `unique`-Flag; das Flag sagt nur, dass diese Fähigkeit das Markenzeichen der
Klasse ist. Damit bleibt es bei **4 aktiv + 2 passiv je Klasse**.

**Folge im Code:** Die Invariante `unique ⇒ ACTIVE` in `AbilityBinding` fällt. Sie war aus der
Annahme entstanden, „vier Aktive inklusive der Unique" bedeute, die Unique sei zwingend aktiv — das
gilt aber nur für den Warrior. Rogues „Second Life" und Mages „Magic Boost & Fall" sind laut
festgelegtem Entwurf passiv, und sie umzubauen hätte eine bereits abgeschlossene Frage wieder
geöffnet, um eine Zählregel zu retten, die auch ohne sie aufgeht. Die Klassenprüfung in
`CharacterClassDefinition` zählt weiter vier aktive und zwei passive Fähigkeiten und höchstens eine
Unique — nur der Zusammenhang zwischen `unique` und `kind` entfällt.

**2. Es gibt einen kurzen globalen Cooldown.** Nach jeder ausgelösten aktiven Fähigkeit sind für eine
kurze Spanne alle anderen gesperrt. Grund ist das festgelegte Eingabeschema: Hotbar-Slot-Wechsel plus
Rechtsklick lässt sich in einem einzigen Tick viermal ausführen, und ohne globale Sperre wäre die
Reihenfolge „alle vier Aktiven sofort" immer die stärkste Eröffnung. Der GCD wird wie die
Einzel-Cooldowns **zeitstempelbasiert lazy** gerechnet — kein Herunterzählen, keine Aufgabe je
Spieler. Der Zahlenwert ist Konfiguration (Prinzip V).

**3. Casting-Zeiten und Unterbrechung sind vorgesehen.** Eine Fähigkeit darf eine Wirkzeit haben, und
ein laufender Cast ist unterbrechbar. Das kostet einen Cast-Zustand je Spieler und Regeln dafür, was
unterbricht und was mit den Kosten geschieht — es ist die teurere der beiden Möglichkeiten und
bewusst so gewählt, weil Wirkzeit nachträglich einzuziehen jede vorhandene Fähigkeit, das HUD (B13)
und die Eingabebehandlung gleichzeitig anfasst. Welche Fähigkeit welche Wirkzeit hat, ist
Konfiguration; instant ist der Sonderfall `cast-time: 0`, nicht die Abwesenheit der Mechanik.

**4. Lifesteal ist ein Effekt in der Kampf-Pipeline, kein Attribut.** ADR-008 bleibt unangetastet:
die acht Attribute bleiben acht, Sekundärwerte bleiben zurückgestellt. Warriors passives Lifesteal
ist ein Effekt-Primitive, das sich in B05 einhängt und einen Anteil des ausgeteilten Schadens als
Heilung zurückgibt. Der Prozentsatz hängt an der Fähigkeitsstufe (Coin-Aufwertung), nicht an einem
Attribut. Ein neuntes Attribut hätte Stat-Engine, Persistenz und HUD gleichzeitig geöffnet — und mit
ihm die Tür für Crit-Chance und Resistenzen, die dieselbe Zurückstellung teilen.

---

## ADR-023 · Zwei Regenerationsraten als neuntes und zehntes Attribut

**Status:** Entschieden *(2026-08-22)* — ergänzt ADR-008, hebt es nicht auf.

`healthRegen` und `manaRegen`, beide in Punkten je Sekunde ausserhalb des Kampfes, im Kampf um einen
konfigurierten Faktor reduziert. Damit sind es zehn Attribute statt acht.

**1. Warum es überhaupt fehlte.** ADR-013 hat `NATURAL_HEALTH_REGENERATION` abgeschaltet und
`VanillaRegenerationGuard` bricht `REGEN`, `SATIATED`, `EATING` und `MAGIC_REGEN` ab, damit
ausschliesslich die Engine die Herzleiste schreibt. Das war richtig und hat eine Lücke hinterlassen,
die bis jetzt niemand geschlossen hat: **ein verletzter Spieler heilt nicht.** `healthRegen` ist die
Rückseite von ADR-013, kein Zusatzwunsch.

**2. Warum ein Attribut und keine Konstante.** Eine Regenerationsrate ist genau das, was ADR-008 unter
einem Attribut versteht: eine Zahl je Charakter, die aus Basis, Level und später Ausrüstung und Buffs
entsteht und einen Cap hat. Als Konfigurationskonstante hätte sie für alle drei Klassen gleich sein
müssen oder eine zweite, klassenabhängige Tabelle neben `classes.yml` gebraucht — eine zweite Stelle
für dieselbe Art von Zahl. Als Attribut nimmt sie den vorhandenen Modifikatorpfad mit: ein Buff „+50 %
Heilung" ist ein gewöhnlicher `ModifierSet` und braucht keine Sonderregel.

**Dies ist keine Rücknahme der zurückgestellten Sekundärwerte.** Crit-Chance, Crit-Schaden, Lifesteal
und Resistenzen bleiben zurückgestellt und bleiben Fähigkeitseffekte (ADR-022). Der Unterschied ist,
dass jene Werte *im Schadensereignis* wirken, wo B05 bereits einen Einhängepunkt hat, während eine
Regenerationsrate über die Zeit wirkt und ausser dem Attribut nirgends hingehört.

**3. Punkte je Sekunde, nicht Anteil am Maximum.** Der Anteil wäre bequemer gewesen — eine Zahl für
alle Klassen, obwohl der Mage 500 Mana hat und der Warrior 200. Er hätte aber ein Attribut geschaffen,
dessen Wirkung von einem *anderen* Attribut abhängt, und ein Modifikator darauf hätte je nach Klasse
etwas anderes bedeutet. Stattdessen unterscheiden sich die Zahlen je Klasse so, dass jede Klasse
dieselbe Zeit braucht: **50 Sekunden auf volle Gesundheit, 25 Sekunden auf volles Mana**, auf Level 1
wie auf Level 60. Dass der Warrior am schnellsten regeneriert, ist die Folge davon, dass er das
grösste Gefäss füllt — nicht eine Bevorzugung.

**4. Basiswert null, und das ist der wichtige Teil.** In `stats.yml` steht bei beiden `base: 0.0`,
anders als bei Gesundheit (100) und Mana (50). Ein Träger ohne Klassenbeitrag ist ein Monster
(`createForEntity`), und ein Basiswert ungleich null hätte still jedes Monster der Welt sich selbst
heilen lassen. Der Wert auf Level 1 kommt aus `classes.yml`, das kein Monster hat.

**5. Beide kommen aus dem Levelwachstum, nicht von einer Leiter.** Damit bleibt `LadderSlot` bei vier
Attributen je Leiter, keine der 37 Stufen bekommt ein Feld, und die Prüfung „ein getragenes Attribut
steigt streng über die Stufen" bleibt unberührt. Das ist der Grund, warum diese Änderung klein war:
die teure Hälfte von B07 wurde nicht angefasst. Der Preis ist, dass `T067` — die Leiter trägt 60 bis
80 % des Zuwachses — für diese beiden ausdrücklich nicht gilt; die Ausnahme steht im Test, damit sie
gelesen wird und nicht als Lücke durchgeht.

**6. Die Caps liegen auf dem Rollenziel, nicht darüber.** `healthRegen` 40, `manaRegen` 20 — der
Warrior erreicht 39,97, der Mage 19,91. Das ist dasselbe Muster wie bei den anderen acht: je Attribut
reizt genau eine Klasse den Cap aus, die anderen bleiben darunter, und `T066` prüft es.

**7. Angewandt wird beides von B08, nicht von B04.** Die Regeneration braucht den Kampfzustand, und
den kennt B05. B04 dürfte ihn nicht lesen, ohne die Abhängigkeitsrichtung umzudrehen (Prinzip III).
B08 liegt über beiden, baut die zeitstempelbasierte Abrechnung ohnehin für Mana und rechnet die
Gesundheit mit derselben Maschine ab. Der Bestand sagt das schon: `CombatState` und `ResourcePool`
benennen B08 namentlich als den Ort, an dem Mana-Regeneration stattfindet.

**Folge:** Bis B08 umgesetzt ist, werden beide Werte berechnet, geführt und angezeigt — aber von
niemandem verbraucht. Das ist derselbe Zustand, in dem B07 die Fähigkeitsbindungen hinterlassen hat.

---

## ADR-024 · Der Scheduler bekommt ein verzögertes synchrones Einzelstück

**Status:** Entschieden *(2026-08-22)* — bei der Planung von B08. Erweitert ADR-010, das
`runAsyncDelayed` ergänzt hat, um den synchronen Gegenpart.

`Scheduler` bekommt `runSyncOnEntityDelayed(EntityRef, Duration, Runnable)`.

**Der Anlass.** B08s Fähigkeiten dürfen eine Wirkzeit haben (ADR-022). Eine Wirkzeit ist einmalige
Arbeit **im Tick**, zu einem bestimmten späteren Zeitpunkt, mit Berührung der Paper-API. Keine der
vier vorhandenen Methoden drückt das aus: `runSyncAtLocation` und `runSyncOnEntity` laufen sofort,
`runAsync` und `runAsyncDelayed` dürfen die API nicht berühren.

**Warum das die Abstraktion nicht aufweicht.** Ihr Javadoc verbietet zwei Dinge namentlich:
synchrone Arbeit ohne Orts- oder Entity-Bindung, und *wiederkehrende* Aufgaben. Die neue Methode ist
entity-gebunden und einmalig und verletzt keines von beiden. Sie öffnet keinen Weg zu einer
periodischen Aufgabe je Spieler, und sie hält den Folia-Pfad offen (ADR-007), weil sie wie ihre
Geschwister an eine Entity gebunden ist. `EntityScheduler.runDelayed` gibt es in Paper nativ — die
Methode bildet ab, was die Plattform ohnehin kann, statt es nachzubauen.

**Verworfen: `runAsyncDelayed`, das am Ende `runSyncOnEntity` aufruft.** Läuft mit der heutigen
Schnittstelle und war der erste Entwurf. Er kostet einen Threadwechsel für Arbeit, die den Tick nie
verlässt, macht die Wirkzeit um bis zu einen weiteren Tick ungenau und braucht zwei
Scheduler-Aufrufe je Cast. Der Ausschlag gab der dritte Punkt: `runSyncOnEntity` darf einen bereits
abgebrochenen Handle zurückgeben, wenn die Entity gerade nicht auflösbar ist — der Cast müsste diesen
Fall dann *nach* dem Warten behandeln statt vorher, also zu einem Zeitpunkt, zu dem das Mana längst
gebucht ist.

**Verworfen: den Cast lazy auswerten wie einen Cooldown.** Der Unterschied ist grundsätzlich und
lohnt, festgehalten zu werden, weil er bei jeder künftigen „warum nicht auch das lazy"-Frage
wiederkommt: ein Cooldown wird ausgewertet, **wenn jemand fragt**. Ein Cast muss wirken, **auch wenn
niemand fragt**. Zeitstempelarithmetik beantwortet Fragen; sie löst keine Handlungen aus.

**Folge:** Prinzip II bleibt erfüllt und wird messbar geprüft (B08 SC-005). Ein Spieler, der nichts
tut, hat keine Aufgabe.

### Nachtrag nach der Umsetzung *(2026-08-22, Workflow-Regel 4)*

Der Satz „die Zahl der geplanten Aufgaben entspricht der Zahl der laufenden Casts **und sonst
nichts**" stand hier und stimmt so nicht mehr. Er war zum Zeitpunkt der Entscheidung richtig; ADR-025
hat danach haltende Fähigkeiten, den Klon und die Unsichtbarkeit ergänzt, und alle drei enden zu
einem Zeitpunkt. Der umgesetzte Stand:

| Was | Aufgaben | Art |
|---|---|---|
| laufender Cast | eine | Einzelstück, entity-gebunden |
| haltende Fähigkeit | eine | dasselbe - das vorzeitige Ende bricht sie ab |
| Klon, Unsichtbarkeit | je eine | dasselbe, für Ablauf und Rückkehr |
| **alle** Intervall-Effekte, ablaufende Buffs, verlorene Geschosse | **eine, serverweit** | selbst nachplanend, wie B05s Sweep |
| Cooldowns, globale Sperre, Ladungen, Wut, beide Regenerationen | **keine** | Zeitstempelarithmetik |

Die Zusage, um die es ADR-024 ging, ist unverändert: **keine wiederkehrende Aufgabe je Spieler und
keine je Ziel.** Zweihundert gleichzeitig laufende Gifte teilen sich eine Auswertung, und ohne ein
einziges laufendes ist sie ein leerer Scan. Was dazugekommen ist, sind Einzelstücke, die existieren,
solange etwas läuft, und mit ihm verschwinden - genau die Form, die ADR-024 erlaubt hat.

---

## ADR-025 · Die ausgearbeiteten Loadouts und was sie am Framework ändern

**Status:** Entschieden *(2026-08-22)* — nach der detaillierten Beschreibung aller achtzehn
Fähigkeiten durch den Auftraggeber. Ergänzt ADR-022.

**1. Die Aufteilung aktiv/passiv wird Inhalt, nicht Struktur.** Bisher galt „vier aktiv, zwei passiv"
als harte Startprüfung. Der ausgearbeitete Rogue ist **drei und drei** — Vergiftete Klinge,
Hinterhältiger Angriff und Zweites Leben sind alle passiv. Künftig prüft der Start nur noch: genau
sechs Fähigkeiten je Klasse, höchstens eine Unique.

Die Alternative wäre gewesen, eine der drei Rogue-Passiven zu einer aktiven umzubauen. Das hätte
einen durchdachten Entwurf verbogen, um eine Zahl zu retten, die nie ein Ziel war, sondern eine
frühe Schätzung. Und sie passt zum Rollenprofil: ein Assassine lebt von Zuständen — Gift, Position,
ein zweites Leben — nicht von Knopfdrücken. `CharacterClassDefinition.ACTIVE_ABILITIES` und
`PASSIVE_ABILITIES` entfallen; `TOTAL_ABILITIES` bleibt.

**2. Haltende Fähigkeiten sind ein dritter Laufzeitzustand.** Sieben der achtzehn wirken über eine
Dauer und enden per zweitem Rechtsklick: Wutschrei, Sprung, Wirbel, Block, Unsichtbarkeit, Magisches
Schild, Manatrank. Das ist keine Randerscheinung, sondern das häufigste Bedienmuster des Blocks —
neben Cooldown und Cast braucht es einen Zustand „wirkt gerade und lässt sich beenden".

**Der Abbruch ist zweiphasig, und das ist die eigentliche Entscheidung.** Ein Abbruch in der
*Vorbereitung* erstattet die Kosten und startet keinen Cooldown; das vorzeitige *Beenden einer
bereits laufenden Wirkung* behält beides. Ohne diese Trennung wäre Sofort-Abbrechen ein kostenloses
Werkzeug: ein Wirbel liesse sich beliebig oft für Sekundenbruchteile zünden. Mit ihr ist ein
Fehlklick beim Sprung folgenlos und ein taktisch früh beendeter Wirbel trotzdem bezahlt.

**3. Wirkung je Sekunde entsteht aus einem Intervallfeld, nicht aus neuen Primitives.** Wirbel,
Vergiftete Klinge, Blitzsturm und Manatrank brauchen alle „X je Sekunde über Y Sekunden". Statt vier
Primitives bekommt ein Effekt ein optionales Intervall — `DAMAGE` mit Intervall ist ein DoT,
`MANA_RESTORE` mit Intervall ist der Manatrank.

**Das nimmt die frühere Ablehnung von Schaden über Zeit zurück, aber nur zur Hälfte.** Abgelehnt war
die *Umsetzung* mit einer Auswertung je Ziel, die Prinzip II verletzt hätte. **Alle** laufenden
Intervall-Effekte laufen deshalb über **eine gemeinsame Auswertung** — ein serverweiter Durchlauf,
keine Aufgabe je Entity. Debuffs ohne Intervall bleiben ablaufende Modifikatoren wie bisher.

**4. Vier Primitives kommen dazu: Evade, Meter, Summon, Invisibility.** Die ersten beiden sind
gewöhnlich. Die beiden anderen sind es nicht:

- **Meter** ist Warriors Wut: ein Zähler von 0 bis 100, der bei Schaden steigt und nach einer
  Ruhefrist fällt, und aus dessen Stand sich eine Attributskalierung ergibt. Er sieht aus wie eine
  dritte Ressource neben Gesundheit und Mana, ist aber keine: er wird nicht gespeichert, überlebt das
  Abmelden nicht und ist aus dem letzten Stand plus verstrichener Zeit **lazy** rechenbar. Deshalb
  kostet er keine Aufgabe und keine Tabelle.
- **Summon** war ausdrücklich auf B10 vertagt und kommt durch den Klon zurück. Es wird hier gebaut,
  **aber ohne Aggro-Umlenkung** — die braucht Mob-KI.

**5. Drei Mechaniken bekommen jetzt ihre Schnittstelle und später ihr Verhalten.** Der Klon zieht
keine Mobs, die Unsichtbarkeit hält Mobs nicht ab und macht keine Ausnahme für Bosse, und Zweites
Leben prüft nicht, ob der Spieler in einer Instanz steht. Alle drei brauchen B10 beziehungsweise B09.
B08 definiert die Einhängepunkte und benutzt eine Vanilla-Näherung, wo eine existiert — der
Unsichtbarkeitseffekt und die Unverwundbarkeit wirken sofort.

Das ist dasselbe Muster, mit dem B07 die Fähigkeits-IDs an B08 abgegeben hat: **benennen, was ein
späterer Block auflöst, statt ihn vorwegzunehmen** (Workflow-Regel 5). Der Preis ist, dass drei
Fähigkeiten bis B10 unvollständig wirken — und das ist bewusst dokumentiert statt stillschweigend.

**6. Zwei Zielbestimmungen kommen dazu.** Die **Kette** springt vom zuletzt getroffenen Ziel weiter,
nicht vom Auslöser — das ist Mages Blitz und lässt sich mit „nächstes Ziel" nicht ausdrücken. Die
**Bodenfläche** verankert sich an einem Punkt und bleibt dort, auch wenn der Auslöser weggeht — das
ist der Blitzsturm.

**7. Ladungen.** Rogues Teleport hat zwei, und der Cooldown beginnt erst nach der zweiten; wird sie
nicht binnen zehn Sekunden benutzt, springt der Vorrat zurück. Zeitstempelarithmetik wie alles andere.

**Nicht geändert:** ADR-008 bleibt bei zehn Attributen. Evade und Meter sind Fähigkeitseffekte, keine
Sekundärwerte — dieselbe Grenze, die ADR-022 für Lifesteal gezogen hat.

---

## ADR-026: `trigger` und `item` dürfen mehrere nennen

**Status:** Angenommen · **Datum:** 2026-08-22 · **Block:** B08

**Kontext.** Die achtzehn Fähigkeiten wurden bewusst als Letztes geschrieben, nach der Maschine — weil
SC-001 („eine neue Fähigkeit entsteht aus Konfiguration") nur dann etwas beweist, wenn der Code
vorher fertig war. Sechzehn der achtzehn entstanden genau so. Zwei nicht:

- **Warriors Wut** baut sich bei aus- *und* eingeteiltem Schaden auf. `trigger` war ein Einzelwert.
- **Mages Aufstieg & Fall** zeigt zwei Marker: Wind Charge für den Sprung, Trank für den Fall. Bei
  einer dreistufigen Einstellung — an, aus, nur Sprung — sind diese beiden das, was der Spieler
  liest. `item` war ein Einzelwert.

**Entscheidung.** Beide Felder nehmen einen Wert **oder** eine Liste. Die Einzelschreibweise bleibt
gültig und ist bei sechzehn von achtzehn Fähigkeiten die richtige.

**Warum nicht die Liste überall erzwingen.** Sechzehn Definitionen schlechter lesbar machen, damit
zwei schreibbar werden, ist der falsche Tausch. Eine einelementige Liste an einer Stelle, an der es
strukturell nur eines geben kann, ist Rauschen.

**Warum nicht als Java-Sonderfall.** Genau das wäre der Bruch von SC-001 gewesen: „Wut ist speziell"
in `PassiveDispatcher` und „Aufstieg & Fall zeigt zwei Items" in `AbilityHotbar` hätten die achtzehn
zum Laufen gebracht und die neunzehnte wieder unmöglich gemacht.

**Grenzen.** Eine **aktive** Fähigkeit nennt weiterhin genau ein Item — es ist der Slot, den der
Spieler anklickt, und zwei wären zwei Wege, dasselbe auszulösen. Mehrere Items sind ausschließlich
Marker einer passiven Fähigkeit. Eine leere Liste bricht ab: sie liest sich wie eine Entscheidung,
ist aber keine — wer keinen Trigger will, lässt die Zeile weg.

**Folgen.** `Ability.triggers()` ist ein `Set`, `Ability.items()` eine `List`; `firesOn(trigger)` und
`item()` sind die beiden Leser. Zwei Aufrufstellen im Code, beide angepasst. Der Test
`ConfigOnlyAbilityTest` bleibt unberührt — er belegte die Zusage für die Bausteine, und die Zusage
hielt: was fehlte, war Vokabular in der Konfiguration, keine Klasse im Code.

---

## ADR-027: Der Neuzuschnitt von B11, und eine Währung bekommt einen eigenen Block

**Status:** Angenommen · **Datum:** 2026-08-22 · **Blöcke:** B08b (neu), B11, rückwirkend B07 und B08

**Kontext.** ADR-017 hat Rüstung und Waffe zu Klassenprogression gemacht. Damit verlor B11 seinen
dominanten Inhalt, und vier Fragen blieben offen, die vor `/specify` zu klären waren. Sie sind es
jetzt.

### 1. Die Währung bekommt einen eigenen Block: **B08b · Währung & Konto**

**Sie war unterwegs verlorengegangen.** Coins stehen seit dem 19.08. in der Vision. `classes.yml`
schreibt heute `cost: { coins: 500 }` an jede Ausrüstungsstufe — und B07 liest die Zahl bewusst nicht
aus („B07 knows nothing about coins"). B08s Rangaufstieg kostet aus demselben Grund nichts;
`RankResult` kennt kein `NOT_ENOUGH_COINS`, weil es nichts gäbe, woran es scheitern könnte. Drei
Blöcke setzen eine Währung voraus, und keiner besitzt sie.

**Warum kein Unterbringen in B11.** Der naheliegende Weg wäre, sie zum Item-Block zu schlagen — dort
fliesst ohnehin Geld. Dagegen spricht die Abhängigkeitsrichtung: B07 und B08 bräuchten dann eine
Abhängigkeit auf B11, und das sind Schicht 1 auf Schicht 2. Ein Kontostand hat mit Items nichts zu
tun; er hat mit dem Charakter zu tun, wie Level und Erfahrung.

**Nummerierung.** B01–B17 ist fest, also wird eingeschoben statt umnummeriert: **B08b**, Schicht 1,
direkt hinter dem Fähigkeitsblock. Das ist zugleich die Reihenfolgeaussage — B08b hängt von B02, B03
und B06 ab, aber **nicht** von B09, B10 oder B11 und ist damit sofort umsetzbar.

**Was er umfasst:** Kontostand je Charakter (nicht je Konto, wie alles andere auch — ADR-011),
Buchung mit Grund, Kostenprüfung als Schnittstelle für andere Blöcke, und eine Historie, soweit B12
sie braucht. **Was er nicht umfasst:** wofür etwas kostet. Preise stehen bei dem, der sie verlangt —
die Stufenkosten in `classes.yml`, die Rangkosten in `abilities.yml`, die Reparatur in B11.

**Folgen, die nachzuziehen sind, sobald B08b steht:**

- B07 löst den `cost`-Block aus, statt ihn undurchsichtig weiterzureichen
- B08s `advanceRank` bekommt eine Kostenprüfung davor und `RankResult` ein `NOT_ENOUGH_COINS`; das
  Javadoc, das heute „es gibt keine Währung" sagt, ist dann falsch und gehört korrigiert
- B11 baut NPC-Verkauf und Reparatur darauf auf

### 2. Raritätsstufen bleiben — als Etikett, ohne Wertwirkung

Die acht Stufen von Common bis Special bleiben für Verbrauchbares, Material und Kosmetik. Sie sagen,
**wie selten** etwas ist, und sonst nichts. Ein epischer Trank heilt nicht mehr als ein gewöhnlicher;
er ist seltener.

Der Grund für die Trennung: Rarität als Wertträger hätte Wertebereiche zurückgebracht, die
Entscheidung 3 gerade abschafft. Als reine Farbe kostet die Skala fast nichts und ist schon
entworfen.

### 3. **Jedes Item hat feste Attributwerte.** Der Roll-Mechanismus entfällt

Kein Würfeln, keine Wertebereiche, keine Affixe. Zwei Tränke desselben Typs sind identisch.

**Was das mit ADR-004 macht.** ADR-004 sagt: ein Item speichert Vorlagen-ID und gewürfelte
Roll-Werte, **niemals** berechnete Endwerte und niemals gerendertes Lore. Die zweite Hälfte bleibt
vollständig gültig und ist der eigentliche Kern — sie ist der Grund, aus dem Rebalancing nach dem
Release möglich bleibt, ohne jedes Spielerinventar anzufassen. Die erste Hälfte schrumpft: gespeichert
wird die **Vorlagen-ID allein**. Endwerte und Lore werden weiterhin bei jedem Laden neu abgeleitet,
nur eben aus der Vorlage statt aus Vorlage plus Roll.

Das macht die Zusage stärker, nicht schwächer: ohne Roll ist die Vorlage die einzige Quelle, und ein
geändertes Balancing wirkt auf jedes vorhandene Exemplar.

### 4. Der NPC-Händler gehört zu B11

Er ist der Ort, an dem Items zu Coins werden. B10 liefert die Entity-Technik, die er mitbenutzt; das
macht ihn nicht zu einem Mob.

**Nicht geändert:** Das Nicht-Ziel „kein Wirtschaftssystem" aus `00-vision-scope.md` meint **kein
Spieler-zu-Spieler-Handel und kein Crafting**. NPC-Verkauf gegen Coins ist davon gedeckt und war es
immer; die Formulierung wird bei `/specify` B11 präzisiert.
