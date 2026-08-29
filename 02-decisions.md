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

**Nicht geändert:** Das Nicht-Ziel „kein Wirtschaftssystem" aus  meint **kein
Spieler-zu-Spieler-Handel und kein Crafting**. NPC-Verkauf gegen Coins ist davon gedeckt und war es
immer; die Formulierung wird bei  B11 präzisiert.

### Nachtrag (2026-08-22): die drei Folgen sind nachgezogen

B08b ist umgesetzt, und damit ist eingelöst, was oben als offen benannt war:

- **B07** löst den `cost`-Block aus, statt ihn undurchsichtig weiterzureichen — und zwar **ohne dass
  B07 angefasst wurde**. Die Auslegung geschieht in B08b (`CostSpec`, `EquipmentPurchase`), weil
  `ClassSourceInvariantsTest` die Vokabel in B07s Quellen verbietet. Der Test ist unverändert grün
  und beweist ab jetzt, dass die Auslegung am richtigen Ort geschieht.
- **B08s `RankResult`** hat sein `NOT_ENOUGH_COINS`, und das Javadoc, das „es gibt keine Währung"
  sagte, ist korrigiert. Die Zusicherung in `AbilityRankTest` wurde **umgedreht statt gelöscht**,
  damit die Änderung im Diff sichtbar bleibt. Die Preisprüfung hängt über eine Naht
  (`AbilityRuntime.RankCost`) darin — B08 zeigt nicht auf B08b.
- **B11** baut Verkauf und Reparatur darauf auf; `BookingReason` hält die drei Werte bereits vor.

---

## ADR-028: Ein Kommando und ein Fenster in einem Schicht-1-Block, befristet

**Status:** Angenommen · **Datum:** 2026-08-22 · **Blöcke:** B08b, später B14 und B13

**Kontext.** B08b braucht einen Aufrufweg für den Admin-Eingriff (FR-039 bis FR-046) und eine Anzeige
für Stand und Verlauf (FR-046a, FR-046b, FR-056). Kommandos, Rechtebaum und Tab-Completion gehören
B14, Anzeige gehört B13 — beide Schicht 3, beide hängen von allen anderen Blöcken ab. Im Projekt
existiert bislang **kein einziges** Kommando; `plugin.yml` hat keinen `commands`-Block.

**Entscheidung.** B08b legt beides an, **vorläufig**:

- **Ein** Kommando `/coins`. Ohne Argument öffnet es das Fenster für die eigenen Charaktere, mit
  Spielerargument für einen fremden. `set`, `add` und `remove` bleiben Kommandoargumente.
- **Ein Fenster** aus reinen Vanilla-Materialien (ADR-005): Charakterauswahl, danach der Verlauf des
  gewählten Charakters, seitenweise.

Die gesamte Logik liegt in `Currency`, `CurrencyAdmin` und `CoinLedger` in `rpg-core` — bukkitfrei
und serverfrei prüfbar. Kommando und Fenster sind Schalen darüber.

**Warum gelesen wird geklickt und geschrieben getippt.** Ein Betrag lässt sich in einem Inventar
nicht sinnvoll eintippen; ihn über Knöpfe zusammenzuklicken wäre eine Zahleneingabe, die wie eine
Oberfläche aussieht. Lesen dagegen ist genau das, wofür ein Fenster taugt — und ein Verlauf über
Hunderte Zeilen ist im Chat unlesbar.

**Warum der Charakter gewählt werden muss.** Ein Spieler hat bis zu drei, und jeder hat seinen
eigenen Stand (ADR-011). Ein zusammengeworfener Verlauf wäre nicht mehr charaktergebunden, und eine
Summe über drei Stände wäre eine Zahl, die es im Spiel nicht gibt.

**Warum das trotz Prinzip III gilt.** Eine Schnittstelle ohne Aufrufweg ist vorhanden und
unbenutzbar. Der Verstoss wird eingegrenzt statt vermieden: er betrifft zwei Dateien und einen
`plugin.yml`-Eintrag, nicht die Blockstruktur. Für das Fenster ist er ohnehin klein — B07 hält seine
Klassenauswahl bereits im eigenen Block, Oberfläche beim Block ist also schon gängige Praxis; B13
besitzt HUD und Mehrsprachigkeit, nicht jedes Inventar.

*Geprüfte Alternativen:*

- **Nur die Schnittstellen liefern, Aufruf B14 und B13 überlassen**: verworfen. Der Betrieb hat die
  Fähigkeit ausdrücklich angefordert, und bis B14 wäre kein Fehler in der Währung gutzumachen —
  Währung ist der Teil, bei dem sich Spieler beschweren.
- **Auf B14 warten**: verworfen aus demselben Grund; B14 ist mehrere Blöcke entfernt.
- **Über einen Konfigurationsschalter buchen**: verworfen — kein Verursacher, keine Berechtigung,
  kein Audit-Eintrag, und ein Neustart je Eingriff.
- **Verlauf als Chat-Ausgabe**: verworfen. Unlesbar ab der zweiten Seite und ohne Weg, den Charakter
  zu wählen.

**Auswirkung.** `plugin.yml` bekommt seinen ersten `commands`-Block. `CoinLedger` bekommt einen
Versatz statt nur eines Limits, weil Blättern sonst nicht ausdrückbar ist. Die Ablösung ist in
FR-046 als Anforderung festgehalten, nicht als Absicht: B14 ersetzt die Schale, B13 übernimmt die
Anzeige, und die Schnittstellen darunter bleiben stehen.

---

## ADR-029: Der Anteilsrechner wird aus `XpDistributor` herausgelöst

**Status:** Angenommen · **Datum:** 2026-08-22 · **Blöcke:** B06 (Eingriff), B08b (Nutzer)

**Kontext.** B08b muss Coins nach derselben Regel verteilen wie B06 die Erfahrung: Anteil am Schaden,
Gruppe als **ein** Beitragender, gleichmäßige Teilung auf die Mitglieder in Reichweite, Nähe-Bonus,
Abrunden mit Rest auf dem Tisch. Diese Regel steht in `XpDistributor` und war von aussen nicht
aufrufbar.

**Entscheidung.** Der Rechner wird als `ShareCalculator` herausgelöst und bleibt in **B06s Paket**.
`XpDistributor` benutzt ihn weiter und vergibt danach Erfahrung; `CoinDropPlanner` benutzt ihn und
baut danach Wurfpläne.

*Warum nicht nachbauen:* Zwei Umsetzungen derselben Regel bleiben genau so lange gleich, bis jemand
eine von beiden anfasst. Die Abweichung träfe dann zwei Spieler **derselben Gruppe**
unterschiedlich — sichtbar im Spiel und für niemanden erklärbar.

*Warum nicht umziehen:* Die Regel gehört B06. Etwas zieht nicht in ein anderes Paket, weil ein
zweiter Nutzer dazukommt.

*Warum kein Aufruf von `XpDistributor` aus B08b:* Der vergibt Erfahrung als Nebenwirkung. Ein Aufruf
für Coins hätte sie ein zweites Mal vergeben.

**Verhaltensneutral, und das ist geprüft.** Die Abnahmebedingung war, dass **sämtliche B06-Tests grün
bleiben, ohne dass einer angepasst wird** — `XpDistributorTest`, `DistributionRoundingTest` und
`PartyRegistryTest`. Sie sind es. Dazu kommt `ShareCalculatorTest`, der dieselbe Aufteilung ohne
Vergabe prüft.

**Nebenbefund, der eine Annahme des Auftraggebers korrigiert hat.** Bei der Klärung zu B08b stand die
Frage im Raum, ob eine Beteiligung von 20 % leer ausgeht — „so wie bei der XP". Das trifft nicht zu:
B06 teilt rein proportional (Szenario 2: 60 % ergeben 60 XP, 40 % ergeben 40 XP), und ein
Party-Mitglied ohne jeden Schaden erhält in Reichweite trotzdem seinen Anteil (Szenario 5). Weder
`progression.yml` noch die Anteilsbildung kennen einen Schwellenwert. Nach der Klarstellung wurde die
anteilige Verteilung für Coins bestätigt (FR-024a) — eine Schwelle nur für Coins wäre die erste
Stelle gewesen, an der zwei Regeln denselben Kill unterschiedlich bewerten.

**Auswirkung.** `XpDistributor` bietet `shareCalculator()` als Zugang. Der Rechner wird **im
Konstruktor gebaut, nicht hereingereicht**: wer eine andere Fassung übergeben könnte, könnte Coins
und Erfahrung wieder auseinanderlaufen lassen — genau das, was die Herauslösung verhindern soll.

---

## ADR-030: Ein Logout im Kampf wird wie ein Tod behandelt

**Status:** Angenommen · **Datum:** 2026-08-23 · **Blöcke:** B09 (Eigentümer), B05 (Eingriff)

**Kontext.** Wer mitten im Kampf den Server verlässt, entgeht dem Tod. Ohne Regel ist das die
verlässlichste Fluchtmöglichkeit im Spiel, und sie kostet nichts. Der Kampfzustand ist dafür schon
vorhanden: `CombatState` gilt acht Sekunden nach dem letzten gegebenen oder genommenen Treffer
(`combat-timeout-seconds` in `combat.yml`).

**Entscheidung.** Der Charakter stirbt. Beim nächsten Login steht er in der Safe-Zone seiner Region
und liest, warum. Der Todesgrund wird unterscheidbar: `DeathCause.LOGOUT`.

*Warum nicht härter.* Der Tod kostet in diesem Spiel bewusst wenig — kein XP-Verlust, kein
Item-Verlust, nur Ausrüstungsschaden. Wer fürs Fliehen mehr zahlt als fürs Sterben, bleibt stehen
und stirbt; die Strafe hätte dann das Gegenteil dessen bewirkt, wofür sie da ist. Gleichstand ist die
richtige Höhe: der Gewinn des Weglaufens ist, dem Tod zu entgehen — bringt Weglaufen genau den Tod,
ist der Gewinn null, und mehr braucht Abschreckung nicht.

*Warum kein Platzhalter-Wesen, das stehen bleibt und totgeschlagen werden kann.* Gegenüber dem Mob
wäre das am fairsten, verlangt aber ein Ersatzwesen, Schadenszuordnung an einen Charakter, der nicht
mehr da ist, und eine Kampfpipeline, die mit Offline-Haltern rechnet. Viel Maschinerie für einen
Randfall.

*Warum keine Coin-Strafe.* Sie bräuchte einen neuen Buchungsgrund in B08b — eine Änderung an einem
fertigen Block — und trifft ungleich: einen reichen Spieler kostet sie nichts, einen frischen alles.

*Warum kein Debuff beim nächsten Login.* Er erfindet befristete Zustände über Sitzungsgrenzen hinweg
und bestraft zu einem Zeitpunkt, an dem der Spieler den Zusammenhang nicht mehr sieht.

**Warum das überhaupt ein ADR ist.** `DeathCause` ist ausgeliefert und liegt in B05. Sein Javadoc
begründet ausdrücklich, dass die Aufzählung grob bleibt: B06, B11 und B12 brauchen den Grund, um
Fälle zu *unterscheiden*, nicht um etwas zu berechnen. Ein vierter Wert ist mit dieser Begründung
vereinbar — B12 will „gestorben" und „abgehauen" trennen können —, aber die Erweiterung eines
ausgelieferten Enums durch einen späteren Block ist genau der Vorgang, den ADR-027 für
ADR-pflichtig erklärt hat.

**Auswirkung.**

- `DeathCause` erhält `LOGOUT`. Wo heute über die Werte verzweigt wird, kommt ein vierter Fall dazu;
  der Compiler zeigt die Stellen.
- Die Regel steht als `combat-logout: death | none` in der Konfiguration und ist abschaltbar, ohne
  dass Code angefasst wird (Prinzip V).
- **Bis B11 existiert, ist die Strafe allein der Teleport in die Safe-Zone.** Der Ausrüstungsschaden,
  der einen normalen Tod ausmacht, braucht B11 — bis dahin bleibt die Regel spürbar mild, und das ist
  eine benannte Lücke (Regel 5), keine stille.
- Die acht Sekunden gehören B05. B09 **liest** sie und legt keine zweite Zahl daneben.

---

## ADR-031: Lasttests sind eine Phase am Ende, keine Bedingung je Block

**Status:** Angenommen · **Datum:** 2026-08-23 · **Blöcke:** alle (Constitution Prinzip VII), B15
(Eigentümer der Phase) · **Constitution:** 1.0.0 → 1.1.0 (MINOR)

**Kontext.** Prinzip VII nannte zwei Blöcke namentlich lasttestpflichtig — B05 und B10 —, und zwar
*bevor sie als fertig gelten*. Die Regel geriet gleich dreifach unter Druck:

- **B05 ist ausgeliefert, ohne Lasttest.** Nach dem Buchstaben der alten Regel hätte er nicht als
  fertig gelten dürfen. Die Abweichung war nie beschlossen, sie ist einfach passiert.
- **B08b setzt seit der Entscheidung „Coins fallen" ein Entity je Kill in die Welt.** Damit stand die
  Frage im Raum, ob er in die Liste gehört (T122, `research.md` R8 dort) — und mit ihr die Frage, wer
  die Liste künftig pflegt.
- **B09 benennt ein Leistungsziel** (Zonenzuordnung für 200 Spieler unter 0,5 ms), das mit einem
  Lasttest gar nichts zu tun hat: es ist mit einer Messung zu belegen, nicht mit 150 Spielern.

Die eigentliche Schwäche lag tiefer als die Liste. Ein Lasttest braucht Spieler, Mobs und Inhalt —
also genau das, was die *späteren* Blöcke erst liefern. Eine Regel, die einen Block auf einen Nachweis
warten lässt, den ein anderer Block erst möglich macht, ist nicht erfüllbar. Genau das hielt T133 für
B08b schon fest: der Nachweis für SC-006 braucht B10s Horden und ist bis dahin nicht zu erbringen.

**Entscheidung.** Lasttests sind **keine Bedingung dafür, dass ein Block fertig ist**. Sie laufen
gebündelt in einer eigenen Phase, wenn die inhaltlichen Blöcke stehen, und gehören **B15**, der den
Lasttest-Aufbau mit simulierten Spielern ohnehin besitzt. Kein Block wird wegen eines fehlenden
Lasttests offen gehalten.

Ein Leistungsziel, das ein Block für sich benennt, braucht dennoch einen Beleg — aber nur einen, der
**ohne Volllast** zu erbringen ist: eine wiederholbare Messung der eigenen Rechenarbeit. Was sich erst
unter 150 Spielern und 800 Mobs zeigt, wird in der Lasttestphase geprüft und vorher nicht behauptet.

*Warum nicht die Liste erweitern:* sie hätte bei jedem neuen Block wieder angefasst werden müssen,
ohne dass irgendwo stünde, woran man die Zugehörigkeit erkennt. Beim dritten Mal wäre die Frage
dieselbe gewesen wie beim ersten.

*Warum nicht die Liste durch ein Kriterium ersetzen:* das war der naheliegende Gegenvorschlag und
hätte die Pflege gelöst, aber nicht das eigentliche Problem — dass ein früher Block auf einen späteren
wartet. Ein Kriterium hätte B08b korrekt eingeordnet und ihn genauso lange offen gehalten.

*Warum nicht ganz darauf verzichten:* die Zielwerte in B15 bleiben verbindlich. Aufgegeben wird der
Zeitpunkt des Nachweises, nicht der Nachweis.

**Der Preis ist benannt und angenommen.** Ein Leistungsfehler zeigt sich später, und je später er
auffällt, desto teurer ist die Umkehr — vor allem, wenn er in einer Architekturentscheidung steckt.
Was dagegen steht, ist Prinzip II: Tickbudget, kein Datenbankzugriff je Spielereignis, keine
wiederkehrende Aufgabe je Spieler oder Entity, räumlicher Index statt linearer Suche. Diese Vorgaben
gelten für jeden Block **vor** dem Lasttest und sind je Block prüfbar. Der Lasttest bestätigt sie am
Ende; er ersetzt sie nicht.

**Auswirkung.**

- Prinzip VII Punkt 3 ist neu gefasst; die namentliche Pflicht für B05 und B10 entfällt. Constitution
  auf **1.1.0**, in beiden geführten Fassungen.
- **T122 aus B08b ist damit beantwortet** — aber anders als dort vorgesehen: B08b wird nicht in eine
  Liste aufgenommen, weil es keine Liste mehr gibt. Die zusätzliche Abnahmebedingung, die T122 für
  diesen Fall vorsah, entfällt.
- **B08bs SC-006 wartet nicht länger auf B10.** Der Block ist nach dem Serverdurchlauf (T132)
  abschliessbar; der Lastnachweis wanderte in die Phase.
- **B05 ist rückwirkend nicht mehr im Widerspruch** zur Constitution. Das war der stillste der drei
  Punkte und der Grund, aus dem die Regel überhaupt geprüft wurde.
- B15 trägt die Phase. Die Zeile „Lasttests sind Teil der Definition of Done für B05 und B10" in
  seinem Steckbrief entfällt und wird durch die Phase ersetzt.
- B09s SC-001 bleibt, wird aber ausdrücklich als **Messung** geführt, nicht als Lasttest.

---

## ADR-032: Wegpunkt-Kristalle in B09 — Eingabe, Fenster, Persistenz und ein neuer Buchungsgrund

**Status:** Angenommen · **Datum:** 2026-08-23 · **Blöcke:** B09 (Eigentümer), B02, B08b und B13
(Eingriffe) · **Befristet bis:** B13 (Fenster und Eingabe)

**Kontext.** Für das Reisen zwischen den sechs Regionen war am Morgen des 2026-08-23 entschieden:
Portale als Konfigurationsquader, ein Quader mit Zielkoordinate, kostenlos, ohne Bedienoberfläche.
Diese Fassung war so geschnitten, dass B09 seine Schicht nicht verlässt.

Bei `/clarify` hat der Auftraggeber das ersetzt: **Wegpunkt-Kristalle, wie in einem Hack'n'Slash.**
Ein Kristall wird per Rechtsklick zunächst freigeschaltet; ein weiterer Rechtsklick öffnet ein
Fenster mit allen Kristallen, in dem nur die freigeschalteten wählbar sind — die übrigen bleiben
sichtbar und melden beim Anklicken, dass sie noch nicht freigeschaltet sind. Eine Reise kostet Coins.

Das ist spielerisch das stärkere Modell, weil es die **erste Reise erhält**: jede Region muss einmal
zu Fuß erreicht worden sein, bevor sie ein Ziel wird. Sichtbare, aber gesperrte Ziele sind dabei
genau der Anreiz, sie zu suchen — ein verborgenes Ziel wäre keiner.

Architektonisch kostet es vier Dinge, die B09 nach seinem Zuschnitt nicht haben durfte:

| Was hinzukommt | Wem es gehört |
|---|---|
| Eine Eingabe (Rechtsklick) | B13 |
| Ein Auswahlfenster | B13 |
| Dauerhafter Zustand je Charakter (Freischaltungen) | B02 |
| Ein neuer Buchungsgrund für die Reise | B08b — **abgeschlossen** |

**Entscheidung.** Die Kristalle entstehen in B09, mit allen vier Eingriffen. Fenster und Eingabe sind
**befristet** und gehen an B13, sobald es existiert — dieselbe Anordnung wie bei ADR-028, wo B08b ein
Kommando und ein Fenster in einem Schicht-1-Block bekam, weil eine Schnittstelle ohne Aufrufweg für
den Betreiber unbenutzbar gewesen wäre. Hier gilt dasselbe Argument in der Spielerrichtung: ein
Wegpunktsystem ohne Auswahlmöglichkeit ist kein Wegpunktsystem.

Vier Festlegungen folgen aus bereits getroffenen Entscheidungen und wurden deshalb nicht neu
verhandelt:

1. **Der Preis steht in der Zonenkonfiguration**, bei dem, der ihn verlangt. ADR-027 verbietet einen
   zentralen Preiskatalog; `currency.yml` ist nicht der Ort für Reisepreise.
2. **Freischaltungen hängen am Charakter**, nicht am Account (ADR-011). Wer mit dem Warrior überall
   war, fängt mit dem Mage bei null an.
3. **Der Buchungsgrund ist eigenständig**, damit der Verlauf eine Reise von einem Einkauf und einer
   Reparatur trennt — dieselbe Zusage, die B08b für jede Buchung gibt.
4. **Der Kristall ist gebaut, nicht gesetzt.** Die Konfiguration beschreibt den Bereich um ein
   Bauwerk, in dem ein Rechtsklick zählt. B09 setzt keine Blöcke und erkennt keinen Blocktyp — sonst
   hinge das Reisen daran, dass niemand den Stein abbaut.

*Warum nicht auf B13 warten:* dann gäbe es bis dahin kein Reisen, und der Rückweg vom *Pale Wilds*
ins *Greenfields* führte jedes Mal über fünf Regionen. Das Reisen ist kein Beiwerk dieses Blocks,
sondern der Grund, warum sechs getrennte Regionen überhaupt bewohnbar sind.

*Warum die Coins und nicht kostenlos:* ausdrückliche Entscheidung des Auftraggebers. Die Empfehlung
lautete kostenlos — das Freischalten ist bereits ein Preis, und eine Gebühr trifft den frischen
Charakter am härtesten, der das Reisen am meisten braucht. Der Auftraggeber hat die Coin-Senke
vorgezogen; die Zahl ist konfigurierbar und damit jederzeit auf null stellbar, falls sich das im
Spiel als zu hart erweist.

**Die eine Stelle, die im Plan nicht schiefgehen darf.** Gebucht und nicht gereist ist ein Diebstahl;
gereist und nicht gebucht ein Freifahrtschein. Bei zu geringem Kontostand passiert **nichts** ausser
einer Meldung, die den fehlenden Betrag benennt — dieselbe Form, die B08b für den Rangaufstieg schon
hat.

**Nachtrag vom 2026-08-23, aus `/plan` Phase 0 (`specs/009-zones-regions/research.md` R1).** Die
Formulierung „Buchung und Versetzung gelten zusammen" ist **nicht zusagbar**. `debit` prüft und zieht
in einem Schritt ab und ist unteilbar gegenüber anderen Buchungen — aber die Versetzung ist ein
Paper-Aufruf und kein Teil derselben Buchung. B08bs Vertrag schliesst eine Reservierung sogar
ausdrücklich aus: „zwei Fähigkeiten im selben Tick würden sonst beide dasselbe Geld ausgeben."

Zusagbar ist die **Wirkung**, nicht die Unteilbarkeit: abbuchen, versetzen, und bei Fehlschlag
zurückbuchen — alles in derselben Tickphase, in der sich keine andere Buchung dazwischenschieben
kann. Daraus folgen **zwei** neue Buchungsgründe statt einem:

- `WAYPOINT_TRAVEL` (DEBIT) — die Reise.
- `WAYPOINT_REFUND` (CREDIT) — die Rückbuchung einer gescheiterten Reise.

Der zweite ist keine Zierde. Ohne ihn wäre eine Rückbuchung im Verlauf nicht von einer gewöhnlichen
Gutschrift zu unterscheiden, und niemand könnte nachsehen, wie oft der Fall überhaupt eintritt. Die
Anforderung FR-050b in der Spec ist entsprechend umformuliert und trägt die Begründung.

**Auswirkung.**

- B09 wächst um Eingabe, Fenster und einen persistierten Aggregattyp. Ob die Freischaltungen ein
  eigener Aggregattyp werden (ADR-015 Punkt 7: drei Eintragungen) oder am Charakter hängen, entscheidet
  `/plan` an B02s Mustern.
- **B08b bekommt zwei weitere Buchungsgründe** (siehe Nachtrag oben) — der zweite Eingriff in diesen
  abgeschlossenen Block nach ADR-030s Todesgrund in B05. Beide Male gilt dieselbe Regel: der Compiler
  zeigt die Stellen.
- B13 erbt Fenster und Eingabe. Der Steckbrief von B13 trägt diese Schuld ab jetzt, wie er schon
  ADR-028s Kontofenster trägt.
- Die Entscheidung „Portale als Config-Quader" vom Morgen des 2026-08-23 ist **überholt** und im
  Steckbrief als solche gekennzeichnet, nicht gelöscht.

**Nachtrag vom 2026-08-23, nach der Umsetzung.** Alle vier Eingriffe sind ausgeführt und geprüft;
`Adr032ConformanceTest` gleicht die Umsetzung gegen dieses ADR ab, statt sich darauf zu verlassen,
dass jemand es liest.

- **Die zwei Buchungsgründe stehen in B08b.** `WAYPOINT_TRAVEL` als DEBIT, `WAYPOINT_REFUND` als
  CREDIT, beide mit einem Kommentar, der B09 und dieses ADR nennt — wer die Datei in einem Jahr liest,
  soll ohne Archäologie sehen, warum zwei Werte eines fremden Blocks darin stehen.
- **Fenster und Eingabe sind im Quelltext als befristet gekennzeichnet.** `WaypointMenu`,
  `WaypointMenuListener` und `CrystalInteractListener` nennen jeweils dieses ADR, das Wort
  *temporary* und **B13** als späteren Eigentümer.
- **Die Persistenz hängt am Charakter**, nicht am Konto. `/plan` hat sich gegen einen eigenen
  Aggregattyp für die Freischaltungen entschieden: `CHARACTER_ZONE_STATE` trägt beide Tabellen, weil
  sie demselben Charakter gehören und im selben Moment geschrieben werden. Zwei Aggregattypen wären
  zwei Positionen in der Schreibreihenfolge für eine Sache gewesen.
- **Der Preis steht bei dem, der ihn verlangt** — je Kristall in `zones.yml`. `currency.yml` kennt
  kein Reisen, und einen zentralen Katalog gibt es nicht (ADR-027).

**Eine Zusage dieses ADRs war falsch, und sie steht oben noch:** „der Compiler zeigt die Stellen."
Das gilt für keinen der beiden Eingriffe. Ein neuer Enum-Wert bricht nur dort, wo ein `switch`
erschöpfend über das Enum geht — und weder über `DeathCause` noch über `BookingReason` gibt es einen.
Beide Werte konnten hinzugefügt werden, ohne dass irgendetwas rot wurde. Was die Stellen tatsächlich
zeigt, sind zwei geschriebene Zählungen: `DeathCauseLogoutTest` und `WaypointBookingReasonTest`. Wer
den nächsten Eingriff in ein ausgeliefertes Enum plant, sollte nicht auf den Compiler zählen.

---

## ADR-033: Vanilla-Unterdrückung in zwei Schichten — Spielregeln zuerst, ein Ereignis-Riegel dahinter

**Status:** Angenommen · **Datum:** 2026-08-24 · **Blöcke:** B10

**Kontext.** Der Steckbrief verlangt, dass natürliches Spawning „überall, auch nachts, auch in
Höhlen" aus ist — B10s Budget soll die einzige Quelle lebender Kreaturen sein (SC-010). Naheliegend
wäre ein einziger `CreatureSpawnEvent`-Zuhörer, der jeden nicht erlaubten `SpawnReason` abbricht.

**Entscheidung.** Zwei Schichten statt einer, in dieser Reihenfolge:

1. **Sieben Spielregeln je Welt** (`SPAWN_MOBS`, `SPAWN_MONSTERS`, `SPAWN_PATROLS`,
   `SPAWN_PHANTOMS`, `SPAWN_WANDERING_TRADERS`, `SPAWN_WARDENS`, `SPAWNER_BLOCKS_WORK`), gesetzt beim
   Start und bei jedem `WorldLoadEvent` erneut — dieselbe Bauart wie `VanillaRegenerationGuard`
   für die Regenerationsregel.
2. **Ein `CreatureSpawnEvent`-Riegel** auf `HIGHEST`, der alles abbricht, dessen `SpawnReason` nicht
   ausdrücklich erlaubt ist (`CUSTOM`, `COMMAND`, `SPAWNER_EGG`, `DISPENSE_EGG` — das absichtliche
   Setzen aus FR-018d).

**Begründung.** Die Spielregeln halten den Spawner-Durchlauf selbst an — kein Kandidat, kein
Ereignisobjekt, keine Zuweisung, die einzige Variante, die wirklich nichts kostet. Sie decken aber
nicht alle gut vierzig `SpawnReason`-Werte ab: Raids, Dorfverteidigung, Netherportale, Jockeys,
Silberfischblöcke, Verstärkung, Slime-Teilung, Versuchsspawner, Infektion, Ertrunkene hängen an
keiner Regel. Ohne den Riegel wäre „überall" schlicht falsch, und zwar an Stellen, die ein Test
nicht zufällig trifft. Der Riegel selbst ist billig, gerade weil die Regeln davor stehen: er sieht
nach Schicht 1 nur noch die Handvoll Fälle, die durchkommen.

**Verworfen.** Nur der Riegel (zahlt für jeden Kandidaten, den der Spawner erzeugt hätte — genau die
Last, die dieser Block senken soll). Nur die Regeln (löchrig, siehe oben). `spigot.yml`/`bukkit.yml`
von Hand (Serverkonfiguration statt Plugin-Konfiguration — eine Zusage, die davon abhängt, dass
jemand eine fremde Datei richtig ausgefüllt hat, ist keine Zusage). Aufräumen statt Verhindern
(FR-018f schließt das ausdrücklich aus: eine Kreatur, die erst erscheint und dann entfernt wird, hat
bereits einen Tick gekostet und war kurz sichtbar).

**Auswirkung.** `VanillaSpawnSuppressor` trägt beide Schichten. Kein Schalter, der die Unterdrückung
abschalten könnte (entschieden 2026-08-24, `NoSuppressionSwitchTest` hält das maschinell fest) — ein
Schalter wäre ein Weg, das Budget zu umgehen.

---

## ADR-034: `FOLLOW_RANGE` je Art statt eigener Pfadfindung — die billige Stellschraube vor der Wette

**Status:** Angenommen · **Datum:** 2026-08-26 · **Blöcke:** B10, B15 (späterer Lasttest-Nachweis)

**Kontext.** Der Steckbrief nennt „ggf. vereinfachte AI statt Vanilla-Pathfinding" als
Architekturvorgabe — mit „ggf.", also als Möglichkeit und nicht als Auftrag. FR-035 bis FR-037
verlangen trotzdem, dass die Zielsuche gedrosselt und in der Reichweite begrenzt ist.

**Entscheidung.** Kein Ersatz der Vanilla-KI. Stattdessen `Attribute.FOLLOW_RANGE` je Art, aus der
Konfiguration — der Radius, in dem eine Kreatur überhaupt nach einem Ziel sucht, und damit die
Stellschraube mit dem größten Hebel: die Suche ist quadratisch im Radius, und Vanillas Standard von
16 bis 48 Blöcken ist für eine Horde zu großzügig. Dazu, für die eine eigene Zielzuweisung, die
dieser Block kennt (der Klon aus US7), eine eigene Drosselung (`RetargetThrottle`), die höchstens im
konfigurierten Abstand wieder anfasst.

**Begründung.** Vanillas Pfadfindung ist bereits stark optimiert und in Server-Nähe gebaut; sie
durch eigenen Java-Code zu ersetzen ist eine Wette, die man nur eingeht, wenn eine Messung sie
verlangt. Diese Messung braucht 150 Spieler und 800 Kreaturen und gehört seit ADR-031 zu B15. Bis
dahin ist die ehrliche Reihenfolge: die billige Stellschraube ziehen, messen, und den Ersatz nur
bauen, wenn die Zahl ihn fordert. Das ist ausdrücklich **keine Vertagung der Zusage** — FR-035 bis
FR-037 werden erfüllt, nur mit dem kleinsten Mittel, das sie erfüllt.

**Verworfen.** Vanillas KI ganz abschalten (`setAI(false)`) und selbst steuern — eine Kreatur, die
nicht mehr fällt, nicht mehr schwimmt und nicht mehr um einen Block herumgeht, ist keine Ersparnis,
sondern ein anderes Spiel. `entity-activation-range` in `spigot.yml` — Serverkonfiguration, siehe
ADR-033.

**Auswirkung.** `HordeBudgetBenchmarkTest` misst die eigene Rechenarbeit ohne Volllast (Spawn- und
Aufräum-Durchlauf bei 130 Kreaturen: 1.300 ns, Budget 1 ms) — das ist die Grundlage, gegen die B15
später den Ersatz rechtfertigen müsste, sollte die echte Messung ihn verlangen.

---

## ADR-035: Aufräumen ortsgebunden statt entitätsgebunden einplanen

**Status:** Angenommen · **Datum:** 2026-08-28 · **Blöcke:** B10

**Kontext.** T112 (Abschnitt 3.3, Schritt 16) zeigte auf dem echten Server: eine im Kampf getroffene
Kreatur verschwand trotzdem, weit unter dem 8-Sekunden-Kampffenster aus `combat.yml`. Debug-Logging
zeigte den Kampfzustand als korrekt gesetzt (`inCombat=true`, `remaining=7.5s`) — die Kreatur wurde
trotzdem nicht mehr im Log gesehen, ohne dass `HordeSweep.cleanup()` sie je entfernt hätte. Ursache:
`HordeSweep.sweep()` plant sich über `scheduler.runAsyncDelayed(...)` selbst neu (R4) — `cleanup()`
läuft also immer auf einem Async-Thread. Der bisherige Code rief dort
`scheduler.runSyncOnEntity(...)` auf, und `PaperSchedulerAdapter.resolve(UUID)` liefert für eine
Nicht-Spieler-Entität nur dann etwas zurück, wenn `server.isPrimaryThread()` wahr ist — von einem
Async-Thread aus also **immer** `null`. Die Aufgabe wurde sofort verworfen, jede Runde aufs Neue,
ohne dass `registry.remove()` je lief. Unbemerkt blieb das, weil zur selben Zeit Vanillas eigener
Despawn (siehe ADR-036) dieselben Kreaturen unabhängig entfernte — beide Fehler haben sich
gegenseitig verdeckt.

**Entscheidung.** `HordeSweep.removeEntity(...)` plant jetzt über `scheduler.runSyncAtLocation(...)`
ein, mit einer groben Position aus dem Chunk-Mittelpunkt der Kreatur (`entry.chunkKey()`) und der
Weltkennung ihrer Zone. `server.getEntity(entityId)` wird erst **innerhalb** des Callbacks
aufgelöst, wenn der richtige Thread schon feststeht — dasselbe Muster, das `placeBossInTick` für das
Setzen einer Kreatur bereits vormacht.

**Begründung.** Ortsgebundenes Einplanen braucht kein vorher aufgelöstes Entitäts-Handle, nur eine
Welt und einen groben Ort — beides hat `cleanup()` bereits in der Hand, ohne die Entität selbst
anzufassen. Das umgeht die Thread-Prüfung strukturell, statt sie zu umgehen: der Callback läuft
tatsächlich auf dem richtigen (Region-)Thread, `server.getEntity(...)` ist dort ein normaler,
sicherer Aufruf.

**Verworfen.** `removeEntity` selbst synchron auf den Hauptthread springen lassen und von dort aus
`runSyncOnEntity` erneut versuchen — ein Umweg über zwei Sprünge für dasselbe Ergebnis, das ein
Sprung schon liefert. Die Thread-Prüfung in `PaperSchedulerAdapter.resolve` aufweichen oder
entfernen — sie schützt zu Recht davor, `server.getEntity(...)` von einem Thread aus zu rufen, der
dafür nicht vorgesehen ist; das Problem lag im Aufrufer, nicht in der Prüfung.

**Auswirkung.** Jeder andere Aufrufer von `scheduler.runSyncOnEntity(...)` im Projekt, der (auch nur
gelegentlich) aus einem async geplanten Durchlauf heraus aufgerufen wird, hat wahrscheinlich
denselben Fehler — noch nicht systematisch durchsucht. Vom Nutzer auf dem echten Server verifiziert
(T112, Abschnitt 3.3, Schritte 14–17 bestanden).

---

## ADR-036: Vanillas eigener Distanz-Despawn wird für eigene Kreaturen gesperrt

**Status:** Angenommen · **Datum:** 2026-08-28 · **Blöcke:** B10

**Kontext.** Noch während der Fehlersuche zu ADR-035 zeigte sich ein zweiter, unabhängiger Fehler:
selbst mit korrektem Kampfzustand verschwand eine getroffene Kreatur, sobald der Spieler weit genug
weg war. Grund: `PaperMobPlacer.place()` setzte nie `Mob#setRemoveWhenFarAway(false)`. Vanilla
löscht eine Kreatur nach eigenem Ermessen, sobald sie weit genug von jedem Spieler entfernt ist
(zufallsbasiert schon ab 32 Blöcken, garantiert ab 128) — komplett unabhängig von
`CleanupRule`s Kampf-Ausnahme (FR-022). Die Kreatur wurde also nie durch den eigenen Sweep entfernt,
sondern durch Vanilla selbst, ohne dass `CleanupRule` je gefragt wurde.

**Entscheidung.** `PaperMobPlacer.place()` ruft beim Setzen einer Kreatur zusätzlich
`suppressVanillaDespawn(entity, kind)` auf, das `Mob#setRemoveWhenFarAway(false)` in einem eigenen
`try`/`catch` setzt — genau wie `applyFollowRange` es für die Zielsuchreichweite schon tut.

**Begründung.** Der eigene `try`/`catch` ist kein Vorsichtsreflex: MockBukkit kennt
`setRemoveWhenFarAway` nicht (`UnimplementedOperationException`) und hätte sonst die gesamte
Platzierung mitgerissen, weil `place()` jede Laufzeitausnahme im Spawn-Consumer als Fehlschlag
wertet. Eine Kreatur ohne diese Sperre ist schlechter dran, aber nicht kaputt (FR-044, Prinzip VI).

**Verworfen.** Nichts — die einzige Alternative wäre gewesen, `CleanupRule` um eine
Vanilla-Distanzprüfung zu ergänzen, aber das Budget kennt seine eigene Reichweite bereits
(`cleanup-radius`); zwei Mechanismen für dieselbe Frage wären zwei Wahrheiten.

**Auswirkung.** Ohne diese Sperre hätte kein Test — auch kein neuer — den Unterschied zwischen
„Vanilla hat aufgeräumt" und „`CleanupRule` hat aufgeräumt" je bemerkt, weil beide von außen gleich
aussehen (die Kreatur ist weg, kein Tod, keine Belohnung). Nur der echte Server zeigt den
Unterschied, wenn man die Kampf-Ausnahme gezielt prüft (T112, Schritt 16).

---

## ADR-037: Klon-Aggro holt bereits kämpfende Kreaturen aktiv nach

**Status:** Angenommen · **Datum:** 2026-08-28 · **Blöcke:** B10 (Anschluss B08, US7)

**Kontext.** T112 (Abschnitt 3.6, Schritt 30) zeigte: stellt ein Spieler einen Klon erst, **nachdem**
Kreaturen ihn schon angegriffen haben — der wahrscheinlich häufigste Fall in der Praxis, eine
Ablenkung mitten im Kampf —, zog der Klon niemanden an. `CloneAggroListener` hing vollständig an
`EntityTargetLivingEntityEvent`, und Vanilla feuert dieses Ereignis nur bei einer **neuen**
Zielwahl, nicht mehr, solange das aktuelle Ziel (der Spieler) gültig bleibt. Eine bereits jagende
Kreatur hätte das Ereignis also nie wieder gesehen, unabhängig davon, wie lange der Klon stand — ein
echter Verstoß gegen FR-039 ("solange ein Klon steht", nicht nur "beim nächsten Zuschlagen").

**Entscheidung.** `registerClone(...)` holt beim Erscheinen des Klons einmalig alle eigenen
Kreaturen (`MobKindTag.isOurs`) in einem groben Umkreis (64 Blöcke) nach, deren aktuelles Ziel
(`Mob#getTarget()`) der Klonbesitzer ist, prüft ihre individuelle `FOLLOW_RANGE` gegen den Klon und
setzt das Ziel direkt um (`mob.setTarget(clone)`) — durch dieselbe Drosselung aus FR-041
(`RetargetThrottle`) wie jede andere Umlenkung. Ab dann übernimmt wieder das normale Ereignis für
alle künftigen Zielwahlen.

**Begründung.** Ein einmaliger Nachtrag im Moment des Erscheinens ist kein Anschreiben gegen
Vanillas Entscheidungsschleife (der Fehler, den `research.md` R9 für die Blockhaltung des Warriors
schon beschreibt) — er setzt das Ziel genau einmal, an der Stelle, an der Vanilla es selbst
akzeptiert hätte, wäre gerade neu gewählt worden. Die Drosselung aus FR-041 gilt mit, weil derselbe
`RetargetThrottle` je Kreatur benutzt wird wie beim ereignisgetriebenen Weg.

**Verworfen.** Eine wiederkehrende Aufgabe, die periodisch alle Kreaturen in Reichweite prüft
(Prinzip II: keine wiederkehrende Arbeit für einen seltenen Zustand — ein Klon ist die Ausnahme,
nicht die Regel, T096).

**Auswirkung.** `CloneAggroListener` braucht jetzt `Server` im Konstruktor, um Klon und Nachbarschaft
aufzulösen. Neuer Test `aCreatureAlreadyChasingThePlayerIsReclaimedWhenTheCloneAppears`. Vom Nutzer
auf dem echten Server verifiziert, inklusive der Abschieds-Explosion aus B08s Farewell-Effekt.

---

## ADR-038: Eigene Kreaturen sind gegen jede Entzündung gesperrt, nicht nur die Sonne

**Status:** Angenommen · **Datum:** 2026-08-28 · **Blöcke:** B10

**Kontext.** Beim Warten auf einen Boss-Respawn (T112, Abschnitt 3.5) zeigte der Server-Log: der
Boss war nicht getötet worden, sondern binnen weniger Minuten nach seinem Respawn in der Sonne
verbrannt — bevor ein Spieler ihn erreichen konnte. Ein Blick zurück durch den gesamten Log dieser
Sitzung zeigte: fast jeder „burned to death"-Eintrag über Stunden hinweg war eine eigene Kreatur.
`mobs.yml` deckt den Bestand fast vollständig mit `base: ZOMBIE`/`HUSK`/`SKELETON`/
`WITHER_SKELETON` ab — keine davon war gegen Sonnenlicht abgesichert. Das fraß unbemerkt laufend
ins Budget und die Dichte (US4) und traf jetzt gezielt eine Kreatur mit einem 30-Minuten-Timer, bevor
sie überhaupt für ihren Zweck (US5) zur Verfügung stand. Kein Prüfschritt in `quickstart.md` fragt
danach — nur der laufende Server über längere Zeit zeigte es.

**Entscheidung.** Neuer Zuhörer `DaylightBurnSuppressor`: bricht `EntityCombustEvent` für jede
eigene Kreatur ab (`MobKindTag.isOurs`).

**Begründung.** Ein erster Testlauf wollte nur die reine Sonnen-Entzündung abfangen und Lava
weiterhin schaden lassen — ein Testfehler zeigte, dass `EntityCombustByBlockEvent` (Lava,
Feuerblock) und `EntityCombustByEntityEvent` (eine andere Entität) dieselbe Handler-Liste wie die
Oberklasse `EntityCombustEvent` teilen; ein Zuhörer auf der Oberklasse fängt sie alle ab, eine
Unterscheidung nach Ursache ist mit Bordmitteln nicht möglich. Das ist hier auch keine Lücke: der
eigentliche Schaden läuft nie über die Entzündung selbst, sondern über B05s Pipeline
(`VanillaDamageListener` setzt jede Vanilla-Schadensursache einschließlich `FIRE_TICK` auf null und
leitet sie um) — das Unterdrücken der Entzündung nimmt nur die zusätzliche, unkontrollierte
Vanilla-Brenn-Animation samt eigenem Sekundenschaden weg, an der berechneten Schadenszahl ändert
sich nichts.

**Verworfen.** Jede Art einzeln mit Feuerresistenz ausstatten (`PotionEffectType.FIRE_RESISTANCE`) —
ein sichtbarer, unerklärter Effekt auf jeder Kreatur für ein rein internes Problem. Eine Unterklasse
gezielt filtern, um Lava weiterhin schaden zu lassen — mit Bordmitteln nicht sauber möglich (siehe
oben), und ohnehin wirkungslos, da der Schaden längst über B05 läuft.

**Auswirkung.** Betrifft die ganze Horde, nicht nur Bosse — die tatsächliche Populationsdichte (US4)
dürfte dadurch spürbar näher an die konfigurierte Zieldichte heranrücken als zuvor angenommen, da ein
bisher unsichtbarer Verlustkanal wegfällt. Neue Tests in `DaylightBurnSuppressorTest`. Vom Nutzer auf
dem echten Server verifiziert.

---

## ADR-039: Was die Klärungssitzung zu B11 entschieden hat — und wo der Steckbrief dem Code widersprach

**Status:** Angenommen · **Datum:** 2026-08-28 · **Blöcke:** B11, berührt B05, B07 und die
Constitution

**Kontext.** `/specify` für B11 stand an. Der Blocksteckbrief galt seit ADR-027 als „bereit für
`/specify`", und die vier dort benannten Fragen waren beantwortet. Beim Abgleich des Steckbriefs
gegen den **gebauten Code** stellte sich heraus, dass die Hälfte seines Umfangs bereits von
Nachbarblöcken erledigt ist — und dass an einer Stelle Steckbrief und Code einander widersprechen.
Elf Fragen an den Auftraggeber schlossen den Rest, in drei Runden. Dieser ADR hält fest, was dabei
entschieden wurde; die vollständige Fassung steht in `specs/011-items-loot-equipment/spec.md`.

**Die dritte Runde ist die lehrreichste.** Sie entstand aus der Frage des Auftraggebers, was in einer
Party mit der Beute passiert — und deckte auf, dass die ersten beiden Runden ein **gebautes
Party-System** übersehen hatten. Zwei fertige Blöcke wollten Unvereinbares, ohne dass es jemandem
aufgefallen wäre; siehe den Nachtrag zu Abschnitt 2.

### 1. Verschleiß ist ein Wert am Charakter, nicht Haltbarkeit am ItemStack

**Der Steckbrief hatte unrecht.** Er kündigt „Durability und Reparatur" an und nennt die Todesstrafe
tragfähig, „weil Haltbarkeitsverlust auf nicht ablegbarer Rüstung genauso funktioniert". B07 hat
aber genau das Gegenteil gebaut: `BoundItemFactory.makeIndestructible()` setzt Klassenausrüstung auf
`setUnbreakable(true)` — mit zwei Einwänden, die im Javadoc stehen. Erstens ließ eine zerbrochene
Waffe den Krieger waffenlos zurück, weil die Leiter die einzige Waffenquelle ist und Werfen wie
Herstellen verboten sind; nur ein Relogin brachte sie wieder. Zweitens: *„the tier carries the
numbers, and a damaged item would quietly weaken a character in a way no attribute reflects."* Und
dann der Satz, der die Lösung schon enthält: *„If wear is ever wanted as a mechanic, it belongs to
the tier, not to the item stack."*

**Entscheidung.** Verschleiß wird eingeführt — aber als **Zustandswert am Charakter**, je
Leiter-Slot einer (`ARMOR`, `WEAPON`). Der ItemStack bleibt unzerstörbar. Der Zustand mindert
ausschließlich den **Ausrüstungsbeitrag** des betroffenen Slots: oberhalb einer Schwelle voll,
darunter stetig fallend bis auf einen Restanteil. Vorgabe: Schwelle 50 %, Restanteil 20 %.

Die Quellen sind getrennt und damit spürbar: **erlittener** Schaden nutzt die **Rüstung** ab,
**ausgeteilter Schaden aus einem Autoattack** die **Waffe**, der **Tod** beides. `DamageOrigin`
trennt das bereits — `MELEE` und `PROJECTILE` sind der normale Angriff, `ABILITY` ist es nicht.
**Fähigkeitsschaden schont die Waffe**, weshalb ein Magier seltener repariert als ein Krieger.

**Begründung.** Beide Einwände von B07 sind damit ausgeräumt statt übergangen: nichts zerbricht, und
die Schwächung ist keine stille — sie geht durch die Werteberechnung und ist ablesbar. Der
Verschleiß liefert zugleich die laufende Coin-Senke, ohne die Coins nur hereinkommen und nie
abfließen.

**Der Tod muss schwerer wiegen als der Alltag, und das ist eine Regel, keine Zahlenwahl.** Vorgabe:
10 Zustandspunkte je Tod gegen 0,01 je Schadenspunkt — ein Sterben wiegt tausend Schadenspunkte auf.
Der **Start weist eine Konfiguration zurück**, in der diese Ordnung nicht mehr gilt. Ohne diese
Prüfung hätte ein späteres Balancing die Todesstrafe aus ADR-017 stillschweigend aushebeln können,
und niemand hätte es gemerkt.

**Verworfen.**
- **Zerbrechen zulassen** (die ursprüngliche Lesart des Steckbriefs): B07s erster Einwand steht
  unverändert — ein Spieler ohne Waffe und ohne Bezugsquelle ist handlungsunfähig.
- **Vanillas Haltbarkeitsbalken als Wahrheit**: hätte `setUnbreakable` aufheben müssen und damit
  denselben Einwand zurückgeholt. Der Balken bleibt als **abgeleitete Anzeige** erhalten, wie Name
  und Lore auch — Darstellung, nicht Autorität.
- **Ein Zustandswert für die ganze Ausrüstung**: einfacher, aber dann verschleißt die Waffe eines
  Magiers so schnell wie die eines Kriegers, und die Unterscheidung nach Kampfstil entfällt.

**Auswirkung.** B11 greift damit an genau **einer** Stelle in den Ausrüstungsbeitrag ein, den B07
besitzt. Die Naht ist benannt und in der Spec als solche festgeschrieben (FR-080); B07 selbst wird
nicht angefasst. `CombatDeathEvent.playerVictim` trägt im Javadoc bereits *„B11 applies equipment
damage only then"* — der Haken war vorgesehen.

### 2. Beute gehört einem Charakter allein — dem größten Beitragenden

**Entscheidung.** Gefallene Beute liegt auf dem Boden, ist aber **ausschließlich für ihren
Eigentümer sichtbar und aufsammelbar**. Eigentümer ist der **größte Beitragende** aus
`CombatDeathEvent.lootRecipient()`, und der Anspruch hängt am **Charakter**, nicht am Spieler
(ADR-011).

**Das ist eine begründete Abweichung von ADR-029.** Erfahrung und Coins teilen sich nach Anteil —
B06 und B08b benutzen denselben `ShareCalculator`, und `CoinDropPlanner` erzeugt einen Haufen je
Berechtigtem. Für Items geht das nicht, und B05 hat die Konsequenz bereits gezogen: *„XP is split by
share because XP divides, loot goes to the largest contributor because a sword does not."* B11
erfindet hier nichts, es benutzt die vorhandene Antwort.

**Begründung.** Der Auftraggeber wollte das vertraute Aufheben vom Boden behalten, ohne dass jemand
einem anderen die Beute wegschnappt. „Größter Beitragender statt letzter Treffer" ist zugleich die
Entscheidung gegen Kill-Stealing, die B05 ausdrücklich so getroffen hat.

**Die Mechanik existiert bereits und wird nicht zum zweiten Mal gebaut.** `rpg.platform.currency`
löst dasselbe Problem seit B08b für Coin-Haufen, und das `package-info` benennt jede Falle, in die
ein zweiter Anlauf sonst liefe:

- `showEntity` ist Zustand der **Verbindung**, nicht der Entität — nach einem Relogin ist der
  Gegenstand wieder unsichtbar, während beide Schlösser weiter passen. *„Unsichtbar aber aufsammelbar
  ist das Schlechteste von beidem."*
- `setOwner` kennt **Spieler**, ADR-011 kennt **Charaktere** — ohne die zweite Prüfung sammelt
  Charakter B ein, was Charakter A verdient hat.
- **Verschmelzen ist eine Gefahr, kein Merkmal**: Vanilla führt ähnliche Stapel zusammen, und damit
  wechselte Besitz durch bloße Nähe.
- **Unsichtbarkeit ist Darstellung und niemals die Autorität** (Prinzip VI) — das Aufsammelschloss
  bleibt zusätzlich bestehen.
- Vanillas Verfall räumt weg, was niemand holt: **keine wiederkehrende Aufgabe** je Gegenstand.

**Verworfen.**
- **Beute direkt ins Inventar**: technisch am einfachsten und konsistent mit ADR-018, aber es nimmt
  dem Spiel das Aufheben, das der Auftraggeber behalten wollte.
- **Beute für alle sichtbar wie in Vanilla**: schief, weil Spieler seit ADR-018 nichts werfen dürfen
  — man könnte fremde Beute einsammeln, aber nicht zurückgeben.
- **Anteilige Aufteilung wie bei Coins**: ein Gegenstand teilt sich nicht. Der einzige Ausweg wäre
  eine Würfelrunde gewesen, und die hätte den mit ADR-027 abgeschafften Zufall zurückgeholt.

#### Nachtrag: in einer Party wandert die Beute reihum

**Der erste Entwurf hatte das Party-System übersehen.** B06 besitzt eines — `Party`,
`PartyRegistry`, `ShareCalculator` — und behandelt eine Party ausdrücklich als **einen**
Beitragenden: ihr Anteil ist die Summe der Mitgliedsanteile, und er wird gleichmäßig auf die
Mitglieder **in Reichweite** verteilt, samt Nähe-Bonus, *„damit gemeinsames Spielen nicht schlechter
ist als allein zu spielen"*.

**Für Beute galt das nicht, und das war ein Widerspruch.** `DamageShare.topContributor` stammt aus
B05, und **B05 kennt keine Partys** — es teilt rohen Schaden je Angreifer-UUID auf. In einer festen
Gruppe wäre also jeder Gegenstand dauerhaft an denselben Spieler gegangen, während Erfahrung und
Coins sich teilen. Der Tank und der Unterstützer hätten nie etwas bekommen. B05s Regel ist gegen
Kill-Stealing zwischen Fremden gedacht; innerhalb einer Party wirkt sie gegen die Absicht von B06.

**Entscheidung.** Die Party gilt auch für Beute als **ein** Beitragender. Weil ein Gegenstand sich
nicht teilt, wandert er **reihum** unter den Mitgliedern in Reichweite.

**Gezählt werden die Gegenstände, nicht die Kills.** Beute ist wahrscheinlichkeitsbehaftet; eine
Runde je Kill ließe die Runde dessen verfallen, dessen Gegner nichts fallen lässt, und über einen
Abend gliche sich das nicht aus. Je Gegenstand gezählt ist die Verteilung exakt gleichmäßig, und
mehrere Gegenstände aus einem Tod gehen an aufeinanderfolgende Mitglieder.

Wer außer Reichweite steht, wird übersprungen und behält seine Position — gemessen wie in B06 zum
**gestorbenen Gegner**, dem einzigen gemeinsamen Bezugspunkt. Steht niemand in Reichweite, fällt der
Anspruch auf den größten Beitragenden zurück. Der Reihenfolgezeiger ist **Laufzeitzustand der
Party**: sie wird laut B06 nicht persistiert, und ein persistierter Zeiger wäre der einzige Teil von
ihr, der einen Neustart überlebte.

**Verworfen.** **Ein eigenes Exemplar je Mitglied** — niemand ginge leer aus, aber die Beute
vervielfachte sich mit der Partygröße und unterliefe Beutetabellen wie Preise. **Eine Würfelrunde
(Need/Greed)** — vertraut aus anderen Spielen, holt aber den mit ADR-027 abgeschafften Zufall
zurück und braucht eine eigene Oberfläche mit Zeitfenster.

### 3. Kosmetik erst auf der Höchststufe

**Kontext.** Stufe 60 ist die Höchststufe; danach fehlt ein Ziel. Der Auftraggeber wollte
Trimfarben als Kosmetik verkaufen, ausdrücklich **ohne** Levelbindung der einzelnen Farbe.

**Das kollidiert mit B07.** In der ausgelieferten `classes.yml` ist der Trim für zwei der drei
Klassen das **einzige** Unterscheidungsmerkmal: der Schurke trägt auf den Stufen 4, 5 und 6 dreimal
`CHAINMAIL` und unterscheidet sich nur durch `COPPER/RIB` → `AMETHYST/SILENCE` → `NETHERITE/VEX`;
der Krieger unterscheidet Stufe 5 von 6 nur durch das Vorhandensein des `GOLD/SENTRY`-Trims. B07s
FR-016 fordert, dass zwei Stufen derselben Leiter niemals gleich aussehen. Ein frei anwendbarer Trim
hätte einen Schurken auf Stufe 4 wie einen auf Stufe 6 aussehen lassen.

**Entscheidung.** Eine gekaufte Trimfarbe ist **erst auf der Höchststufe der Leiter anwendbar**.
Gekauft werden kann sie jederzeit; keine einzelne Farbe trägt eine eigene Levelhürde.

**Begründung.** Das trifft die genannte Absicht genau — Kosmetik ist der Grind **nach** Stufe 60 —
und hält die Stufenerkennbarkeit während der gesamten Progression intakt. Nur der Magier wäre
ohnehin unberührt gewesen, weil er über Farbe statt Trim unterscheidet.

**Verworfen.** **Trims auf jeder Stufe erlauben** und dafür Schurken- und Kriegerleiter ein zweites
Unterscheidungsmerkmal geben. Machbar, aber es ist eine Änderung an `classes.yml` und an B07s
Erscheinungsbildvalidierung — in einem Block, der B07 ausdrücklich nicht verändern soll. Wenn das
gewünscht wird, gehört es in einen eigenen ADR und nicht hier hinein.

### 3a. Verschleiß bemisst sich vor der Abwehr, nicht danach

**Entscheidung.** Der Rüstungsverschleiß richtet sich nach dem **ankommenden** Schaden, nicht nach
dem, was nach der Abwehr durchkommt.

**Begründung.** Am durchgekommenen Schaden gemessen wäre eine **Abwärtsspirale** entstanden:
verschlissene Rüstung mindert den Ausrüstungsbeitrag, also kommt mehr durch, also verschleißt sie
schneller, also kommt noch mehr durch. Genau die Rückkopplung, die ein Spieler nicht mehr aufhalten
kann, sobald sie einmal läuft. Zusätzlich wäre gute Rüstung doppelt belohnt worden — weniger Schaden
**und** langsamerer Verschleiß. Vor der Abwehr gemessen ist die Verschleißrate von der
Rüstungsgüte unabhängig, und die Rüstung nutzt sich an dem ab, was sie tatsächlich abfängt.

**Verworfen.** **Eine Pauschale je Treffer** wäre gegen beide Effekte ebenso immun und noch
einfacher, ließe aber einen Kratzer so viel kosten wie einen Bosstreffer.

**Ebenfalls entschieden:** ein **beschworener Klon** (B08 `SummonEffect`) nutzt nichts ab — weder
durch ausgeteilten noch durch eingesteckten Schaden. Er ist eine eigene Kreatur mit einer
Momentaufnahme der Werte und selbst eine Fähigkeit; dass Fähigkeiten die Ausrüstung schonen, gilt
für ihn wie für jede andere.

### 4. Was ohne Diskussion folgte

- **Aufstiegsmaterial entfällt als Kategorie.** Der Aufstieg kostet **Level und Coins**, und beides
  ist bereits gebaut: `EquipmentTier.requiredLevel` in B07, `EquipmentPurchase` in B08b. B11 liefert
  nur die Route dorthin — einen NPC — und ausdrücklich keinen zweiten Kaufmechanismus. Es bleiben
  zwei Kategorien: Verbrauchbares und Kosmetik.
- **Ein NPC je Region, sechs insgesamt**, im jeweiligen Safe-Core, mit je eigenem Verkaufsbestand.
  Er kauft an, verkauft, repariert und führt den Aufstieg durch. Ein zentraler Händler hätte einen
  Spieler in den Pale Wilds quer über die Karte geschickt, um einen Trank loszuwerden.

### 5. Prinzip IV der Constitution wurde nachgezogen (1.1.0 → 1.1.1)

Prinzip IV forderte wörtlich: *„Items speichern **Template-ID und gewürfelte Roll-Werte**"*. ADR-027
hat den Roll-Mechanismus am 2026-08-22 abgeschafft — die Constitution hat das sechs Tage lang nicht
nachvollzogen. Aufgefallen ist es erst, als B11 als erster Block diese Regel tatsächlich umsetzen
sollte: der Constitution Check von `/plan` hätte gegen einen überholten Wortlaut geprüft.

Der Satz heißt jetzt „Items speichern **die Template-ID**". Das ist ein **PATCH**, kein MINOR: die
geschützte Zusage — kein gerendertes Lore, keine berechneten Endwerte — ist unverändert, es entfällt
nur eine Erlaubnis, die niemand mehr nutzt. Wer der alten Fassung folgte, verstößt nicht gegen die
neue. **Die Zusage wird dadurch stärker**: ohne Roll ist die Vorlage die einzige Quelle, und eine
Balancing-Änderung wirkt auf jedes vorhandene Exemplar statt nur auf neue.

Nachgezogen wurden alle drei Fassungen: `.specify/memory/constitution.md` (die vom Werkzeug gelesene),
`constitution.md` und `minecraft-rpg-spec/minecraft-rpg-spec/constitution.md`. Eine stehengelassene
Quellfassung wäre die nächste Divergenz gewesen.

**Auswirkung insgesamt.** B11 ist deutlich kleiner als sein Steckbrief: kein Ausrüstungssystem,
keine Kontoführung, keine zweite Lagerung, kein zweiter Kaufmechanismus. Was bleibt, ist das Item
als Datenobjekt, zwei Kategorien, die Beute, die NPCs und der Verschleiß. Die Spec schreibt diese
Abgrenzung als prüfbare Anforderung fest (FR-079 bis FR-081), weil bei einem verkleinerten Block das
versehentliche Nachbauen vorhandener Nähte der wahrscheinlichste Fehler ist.

### Nachtrag aus der Umsetzung: was der Rückbau nebenbei gefunden hat

**Der Rückbau von `item_instance` (V11_1) hat eine Falle in `StateVersionMigrator` freigelegt.** Die
Methode `migrate` baute den `SessionBundle` mit einem kurzen Bequemlichkeitskonstruktor neu und ließ
dabei **classProgress, inventories, abilities, balances und zoneStates** fallen — sie wurden zu
`List.of()`.

**Ausgelöst hat das nie etwas, und genau das ist das Unangenehme daran.** Solange
`CURRENT_DATA_VERSION` auf 1 steht, kann kein Datensatz migrationsbedürftig sein: der Zweig ist
unerreichbar, und `migrate` gibt den Bundle über einen Früh-Rückgabepfad unverändert zurück. Es war
also kein Fehler im Betrieb, sondern **eine Falle für den Tag, an dem Version 2 eingeführt wird** —
und an dem Tag hätte ein Spieler mit altem Datensatz seine Ausrüstungsstufe, sein Inventar, seine
Fähigkeiten, seine Coins und seinen Zonenzustand für die ganze Sitzung als leer gesehen, weil
`DefaultSessionLifecycle` den migrierten Bundle behält (`loaded.put`) und an jedes
`SessionAttachment` reicht. Gesucht hätte man den Fehler in der Migration.

**Die Lehre ist allgemein und wird als Test festgehalten:** ein von Hand zusammengesetzter Record
verliert stillschweigend, was ein späterer Block hinzufügt. `StateVersionMigratorTest` liest den
Quelltext und prüft, dass der Konstruktoraufruf **jede** Record-Komponente von `SessionBundle`
nennt — die einzige Art, einen toten Zweig richtig zu halten.

**Aufgefallen ist es nur, weil der Rückbau diese Zeile ohnehin anfassen musste.** Das ist das zweite
Mal in diesem Projekt, dass eine Aufräumarbeit einen Fehler findet, den kein Test gesucht hätte —
beim ersten Mal war es der Klassenlader (B08b/T132).

---

## ADR-040: Ein zweiter Schreibweg auf `player_statistic_daily` — Maximum neben Summe

**Status:** Angenommen · **Datum:** 2026-08-29 · **Blöcke:** B12, erweitert B02

**Kontext.** `/specify` für B12 hat „höchster Schaden" in den Umfang aufgenommen. Der gesamte
Statistik-Schreibweg aus B02 hängt aber an einem einzigen Satz:
`ON CONFLICT (player_id, metric, day) DO UPDATE SET value = value + excluded.value`. Genau weil
die Aktualisierung **addiert**, kann ein Delta geschrieben werden, ohne den gespeicherten Wert
vorher zu lesen — das ist B02s FR-007 und der Grund, warum tausend Kills einen Schreibvorgang
kosten und nicht tausend. Ein Maximum ist kein Summand. Es lässt sich in diesem Weg nicht
ausdrücken.

**Entscheidung.** Die Tabelle bleibt, wie sie ist. Daneben entsteht ein **zweiter Schreibweg** auf
derselben Tabelle, der statt der Summe das Maximum bildet (`GREATEST`). Welchen Weg eine Metrik
nimmt, steht in ihrem Verzeichniseintrag als **Metrikart** (Summe, Maximum, Zustand) — nicht an
der Aufrufstelle. Auch der neue Weg liest **nicht** vor dem Schreiben.

**Begründung.** Die Eigenschaft, die B02 teuer erkauft hat, ist nicht „addieren", sondern „ohne
Lesen schreiben". `GREATEST(gespeichert, neu)` erhält diese Eigenschaft vollständig — es ist
dieselbe Art Aussage über den vorhandenen Wert, nur mit einem anderen Operator. Auch die
Zwischenspeicherung im Arbeitsspeicher überträgt sich: statt Deltas zu addieren, wird das
laufende Maximum gehalten und beim Flush einmal geschrieben.

**Verworfen.**
- **Vor dem Schreiben lesen**: verletzt B02s FR-007 und macht aus jedem Schadensereignis eine
  Datenbankabfrage. Genau das, was das Fundament verhindern sollte.
- **Eine eigene Tabelle für Maximum-Metriken**: eine zweite Haltung für dieselbe Sache, mit
  eigenem Schlüssel, eigenem Index, eigener Aufbewahrungsregel und einer zweiten Stelle, die bei
  der Anonymisierung umgezeigt werden muss. Der Nutzen wäre allein begriffliche Sauberkeit.
- **Auf die Metrik verzichten**: war die Alternative in der Klärungsfrage; der Auftraggeber hat
  sich ausdrücklich für den vollen Umfang entschieden.

**Auswirkung.** B12 ist der erste Block der dritten Schicht, der ein Fundament aus B02
**erweitert** statt es nur zu benutzen. Die Erweiterung ist additiv: bestehende Metriken ändern
ihr Verhalten nicht, und ein Aufrufer, der `increment` benutzt, merkt nichts davon. Der
Anonymisierungspfad (`REPOINT_STATISTICS`) bleibt unverändert gültig, weil die Zeilen dieselben
bleiben.

---

## ADR-041: Zustandswerte werden gelesen, nicht in die Statistik gespiegelt

**Status:** Angenommen · **Datum:** 2026-08-29 · **Blöcke:** B12, berührt B06 und B08b

**Kontext.** Die für B12 bestätigte Metrikliste enthält **Level, XP und Coins**. Die
Statistiktabelle aus B02 hält aber Tageswerte, die über Zeiträume summiert werden. Die Summe der
Tageslevel eines Spielers ist bedeutungslos, und ein täglich fortgeschriebener Coin-Stand wäre
eine zweite Fassung eines Wertes, der bereits in `rpg.character_balance` steht.

**Entscheidung.** Level, XP und Coins werden **nicht** in die Tagestabelle geschrieben. Ihre
Ranglisten lesen `rpg.character_progress` und `rpg.character_balance` — dort, wo die Wahrheit
ohnehin liegt. Sie erscheinen ausschließlich als **aktueller Stand** und bekommen keine Tages-,
Wochen- oder Saisonform. Da beide Tabellen am Charakter hängen, Ranglisten aber Konten
vergleichen, wird verdichtet: **Level** ist der höchste Charakter eines Kontos (bei Gleichstand
entscheidet die XP innerhalb des Levels), **Coins** die Summe aller Kontostände.

**Begründung.** Prinzip IV verlangt eine Wahrheit je Wert; ADR-039 hat mit dem Rückbau von
`item_instance` gerade erst gezeigt, was ein zweiter Speicherort ohne Schreiber anrichtet. Ein
gespiegelter Coin-Stand wäre schlimmer als das: er hätte einen Schreiber und würde trotzdem
abweichen, sobald ein Flush ausfällt.

**Verworfen.**
- **Tägliche Momentaufnahme des Standes**: hätte einen Verlauf ermöglicht („Coins über die Zeit"),
  aber jede Zeitraumsumme wäre eine sinnlose Zahl, und niemand hätte den Unterschied in der
  Anzeige gesehen.
- **Zustandswerte ganz aus B12 heraushalten**: hätte die vom Auftraggeber bestätigte Metrikliste
  beschnitten.

**Auswirkung.** Eine Rangliste in B12 hat **zwei mögliche Quellen** — die Statistiktabelle für
Zähler und Maxima, die Fachtabellen für Zustände. Der Preis ist benannt: die Zusage „das Öffnen
löst keine Datenbankabfrage aus" muss für beide Quellen über denselben Zwischenspeicher gehalten
werden, nicht nur für die eine.

---

## ADR-042: Ein Kill zählt für jeden Beteiligten — in der Party für jeden in Reichweite

**Status:** Angenommen · **Datum:** 2026-08-29 · **Blöcke:** B12, berührt B05, B06 und B11

**Kontext.** Außerhalb einer Party ist die Frage beantwortet: B05 stellt
`CombatDeathEvent.lootRecipient()` bereit, den größten Beitragenden, und B11 vergibt danach die
Beute — *„XP is split by share because XP divides, loot goes to the largest contributor because a
sword does not."* B12 übernimmt das für den Kill, damit es nicht zwei Antworten auf „wessen Kill
war das" gibt. **Innerhalb** einer Party trägt diese Regel nicht: B06 behandelt eine Party als
**einen** Beitragenden und verteilt Erfahrung und Coins an alle Mitglieder in Reichweite,
ausdrücklich damit gemeinsames Spielen nicht schlechter ist als allein zu spielen. Nach der
Beute-Regel allein hätte in einer festen Gruppe dauerhaft derselbe Spieler jeden Kill gezählt
bekommen. Das ist dieselbe Kollision, die bei B11 erst durch die Party-Frage sichtbar wurde
(ADR-039, Abschnitt 2) — nur an einer anderen Metrik.

**Entscheidung.** Innerhalb einer Party wird der Kill **jedem Mitglied in Reichweite** gezählt,
unabhängig von seinem Schadensanteil. Maßgeblich ist die Zusammensetzung zum Zeitpunkt des Todes,
und die Reichweitenprüfung ist **dieselbe**, die B06 für Erfahrung und Coins benutzt. ~~Außerhalb
einer Party bleibt es beim größten Beitragenden.~~ — **dieser Halbsatz ist noch am selben Tag
ersetzt worden; siehe den Nachtrag am Ende dieses ADR.** Die Beute rotiert weiterhin je
Gegenstand (B11); der Kill rotiert nicht.

**Begründung.** Eine Statistik ist kein knappes Gut. Beute rotiert, weil ein Schwert sich nicht
teilen lässt — eine Zahl lässt sich teilen, ohne kleiner zu werden. Damit gilt für sie B06s
Zusage und nicht B11s Ausnahme.

**Der Preis ist benannt und angenommen:** die Summe aller Kill-Zähler ist **größer** als die Zahl
der getöteten Kreaturen. Die Metrik bedeutet damit **Beteiligung an einem Kill**, nicht
„eigenhändig erledigt". Jede Anzeige muss sie so benennen — sonst zählt der Server etwas anderes,
als der Spieler liest, und die Rangliste wird als kaputt gemeldet, obwohl sie tut, was hier
entschieden wurde.

**Verworfen.**
- **Nur der größte eigene Anteil**: exakte Summe, aber in einer festen Gruppe sammelt dauerhaft
  derselbe Spieler. Genau die Falle, die B11 bei der Beute mit der Rotation umgangen hat.
- **Reihum wie die Beute**: Summe bliebe exakt, aber die eigene Kill-Zahl würde zur Lotterie und
  spiegelte nicht mehr, woran man beteiligt war.
- **Anteilig zählen (0,5 Kills)**: die Summe stimmte, aber gebrochene Kills sind keine Zahl, die
  ein Spieler in einer Rangliste lesen will.

**Auswirkung.** B12 liest die Party aus B06 und benutzt deren Reichweitenprüfung. Eine zweite,
eigene Reichweite für dasselbe Ereignis ist damit ausgeschlossen — sie wäre der wahrscheinlichste
stille Widerspruch zwischen „ich habe XP bekommen" und „mein Kill wurde nicht gezählt".

### Nachtrag vom 2026-08-29: auch außerhalb einer Party zählt der Kill für jeden Beteiligten

Der oben durchgestrichene Halbsatz hat den Fall **ohne** Party bei der alten Regel belassen — dem
größten Beitragenden. Die dritte Klärungsrunde desselben Tages hat ihn ersetzt, und der Anlass
war der **Regionsboss**.

**Der Widerspruch, den erst die Boss-Frage sichtbar gemacht hat:** B05 verteilt Erfahrung nach
Anteil an **alle** Beitragenden, nicht nur an den größten — dafür gibt es den `ShareCalculator`.
Legen zehn Spieler ohne Party einen Boss, bekommen alle zehn Erfahrung und Coins, aber nach der
alten Regel hätte genau **einer** einen Bosskill in seiner Statistik stehen gehabt. Neun Spieler
hätten denselben Kampf bestritten und wären in der Bosskill-Rangliste unsichtbar geblieben.
Genau dieselbe Art Lücke wie in ADR-039, Abschnitt 2 — zwei Blöcke, die für sich stimmen, und
eine Frage, die keiner von beiden gestellt bekommt.

**Ersetzte Entscheidung.** Ein Kill zählt für **jeden** Spieler, dessen Schadensanteil an der
getöteten Kreatur eine konfigurierte **Schwelle** erreicht (Vorgabe 5 %). Das gilt für jede Art,
Bosse eingeschlossen — der Boss war der Anlass für die Schwelle, nicht ihre Ausnahme. Die
Partyregel oben bleibt unverändert bestehen und ist jetzt die **Erweiterung** der Schwelle: ein
Mitglied in Reichweite zählt auch bei einem Anteil von null.

**Begründung.** Der Anteil, gegen den die Schwelle prüft, existiert bereits — B05 führt ihn
ohnehin, um Erfahrung zu verteilen. Es entsteht keine zweite Rechnung und keine zweite Wahrheit
darüber, wer wie viel beigetragen hat. Und die Schwelle beantwortet nebenbei eine Frage, die
sonst offen geblieben wäre: **Kill-Klau gibt es nicht mehr**, weil es nichts zu klauen gibt.

**Verworfen (in der Runde, die diesen Nachtrag ausgelöst hat).**
- **Eine Sonderregel nur für Bosse**: hätte den Widerspruch an der Stelle geheilt, an der er
  auffiel, und ihn bei gewöhnlichen Mobs stehen lassen. Zwei Regeln, wo eine reicht.
- **Beim größten Beitragenden bleiben**: exakteste Zählung, aber neun von zehn Bossteilnehmern
  hätten nach dem Kampf nichts vorzuweisen gehabt.

**Warum ein Nachtrag und keine stille Korrektur.** Die alte Fassung stand einen halben Tag lang
und ist nirgends implementiert. Sie hier durchzustreichen statt sie zu löschen, hält fest, dass
die Kill-Frage **zweimal** neu beantwortet werden musste, bevor sie stimmte — beim ersten Mal für
die Party, beim zweiten für den Boss. Wer den nächsten Block schreibt, sollte sehen, dass diese
Art Frage selten beim ersten Anlauf vollständig ist.

---

## ADR-043: Zwei Uhren für die Spielzeit, und drei Werte, die nur dem Spieler gehören

**Status:** Angenommen · **Datum:** 2026-08-29 · **Blöcke:** B12, berührt B09

**Kontext.** Spielzeit ist eine öffentliche Rangliste. Eine Rangliste über reine Onlinezeit
gewinnt, wer den Client nachts laufen lässt — sie misst dann Anwesenheit, nicht Spiel. Zugleich
wollte der Auftraggeber die Zeit **je Region** aufgeschlüsselt sehen und die untätige Zeit nicht
verlieren, sondern nur nicht öffentlich zeigen.

**Entscheidung.** Es gibt **zwei Uhren**: die **aktive Zeit**, die nach einer konfigurierten Dauer
ohne Aktivität anhält (Vorgabe fünf Minuten), und die **gesamte Onlinezeit**, die durchläuft.
Öffentlich gerankt wird ausschließlich die aktive Zeit. Die aktive Zeit wird zusätzlich **je Zone**
aufgeschlüsselt; die Summe über alle Zonen ist die aktive Gesamtzeit — es ist dieselbe Uhr, nur
anders aufgeteilt.

Damit hat dieser Block **drei private Werte** statt einem: die Tode nach Verursacher, die gesamte
Onlinezeit und die Aufteilung nach Zonen. Ein fremdes Profil zeigt die Gesamtzahl der Tode und die
aktive Spielzeit — und keinen der drei.

**Begründung.** Die Trennung kostet nichts, was der Spieler verliert: die untätige Zeit ist
erfasst und für ihn sichtbar, sie taucht nur nicht im Vergleich mit anderen auf. Und sie schützt
die einzige Metrik dieses Blocks, die sich ohne Spielen steigern lässt.

**Beide Uhren laufen ohne eine einzige neue wiederkehrende Aufgabe.** Die Untätigkeit ergibt sich
aus einem Zeitstempel der letzten Aktivität, der Zonenwechsel aus dem vorhandenen Ereignis in B09.
Das ist keine Feinheit, sondern Prinzip II: eine Aufgabe je Spieler wäre bei 150 Spielern genau
die Art wiederkehrender Last, die das Tick-Budget frisst.

**Verworfen.**
- **Nur Onlinezeit zählen**: einfachste Erfassung, aber die öffentliche Rangliste misst dann den
  Stromverbrauch des Spielers.
- **Zwei vollständig getrennte Metriken je Zone** (aktiv und online je Zone): doppelt so viele
  Zeilen und zwei Sichtbarkeitsregeln auf derselben Dimension, ohne dass jemand die zweite Zahl
  gebraucht hätte.
- **Zonenaufteilung öffentlich**: vom Auftraggeber ausdrücklich abgelehnt. Wo jemand seine Zeit
  verbringt, ist eine Auskunft über ihn, keine über sein Können.

**Auswirkung.** FR-037 („kein privater Wert in einer fremden Ansicht") ist die Anforderung dieses
Blocks mit der größten Wahrscheinlichkeit, beim Bauen still verloren zu gehen — es gibt vier
Ausgabewege (eigenes Fenster, fremdes Profil, Rangliste, Hologramm) und drei Werte, die auf keinem
davon außer dem ersten erscheinen dürfen. Sie wird über alle vier geprüft.

---

## ADR-044: Die Aufschlüsselung wird in Zeilen bezahlt, nicht durch Verdichten

**Status:** Angenommen · **Datum:** 2026-08-29 · **Blöcke:** B12, berührt B02

**Kontext.** Kills und Tode werden je Mob-Art aufgeschlüsselt, die Spielzeit je Zone. Aus einem
Metrikschlüssel wird damit eine Familie von Schlüsseln, und die Zahl der Tageszeilen
vervielfacht sich. B02s Baseline nennt eine Größenordnung von rund 73 000 Zeilen im Jahr bei 200
Spielern — diese Schätzung ging von einer Handvoll undimensionierter Metriken aus. Mit den
Dimensionen liegt die Größenordnung grob bei ein bis vier Millionen Zeilen im Jahr.

**Entscheidung.** Das wird unverändert hingenommen. Es wird **nicht** verdichtet, nicht rotiert
und nichts gelöscht.

**Begründung.** Zeilen entstehen nur für Arten und Zonen, die ein Spieler tatsächlich berührt hat
— die Obergrenze ist nicht das Produkt aus allen Arten und allen Spielern, sondern das, was
wirklich gespielt wurde. Wenige Millionen schmale Zeilen mit den beiden vorhandenen Indizes sind
für PostgreSQL klein, und die Aggregation läuft ohnehin über Materialized Views und nicht bei
jeder Anzeige.

**Der eigentliche Grund gegen das Verdichten ist aber kein Größenargument.** B02 sagt zu, dass
Statistik-Rohdaten unbegrenzt aufbewahrt und **niemals** bereinigt werden. Ältere Tage zu einer
Zeile je Art und Saison zusammenzufassen, hieße Rohdaten zu verändern — genau das, was diese
Zusage ausschließt. Eine Zusage, die beim ersten Wachstum aufgegeben wird, war keine.

**Verworfen.**
- **Ältere Tage je Art verdichten**: bricht B02s Aufbewahrungszusage, und die Tagesauflösung wäre
  rückwirkend nicht wiederherstellbar.
- **Nur eine konfigurierte Liste „relevanter" Arten aufschlüsseln, Rest als „Sonstige"**: spart am
  meisten und erzeugt eine Pflegeaufgabe, die niemand pflegen wird. Jede neue Mob-Art landete
  stillschweigend im Sammeltopf.

**Auswirkung.** Die Größenordnung ist bewusst gewählt und in der Spec als Annahme festgehalten,
damit ein späterer Blick in die Tabelle nicht wie ein Fehler aussieht. Sollte sie sich als falsch
erweisen, ist die Antwort ein zusätzlicher Index oder eine Partitionierung nach Tag — nicht das
Löschen von Rohdaten.

---

## ADR-045: Eine Saisonbelohnung ist ein Anspruch, und er verfällt nicht

**Status:** Angenommen · **Datum:** 2026-08-29 · **Blöcke:** B12, berührt B08b und B11

**Kontext.** Saisons schließen mit einer Belohnung ab. Zum Zeitpunkt des Abschlusses ist der
Empfänger in aller Regel nicht online — eine Saison endet an einem Datum, nicht dann, wenn alle
Beteiligten zusehen.

**Entscheidung.** Der Abschluss friert den Endstand ein und legt einen **Anspruch** an, statt
etwas auszuschütten. Der Anspruch gehört dem **Konto** und wird von dem Charakter eingelöst, mit
dem der Spieler ihn abholt: Coins landen auf dessen Kontostand, Items in dessen Inventar. Er ist
**genau einmal** einlösbar, auch bei einem Absturz zwischen Gutschrift und Vermerk, und er
**verfällt nicht**.

**Begründung.** Eine Gutschrift an einen offline Spieler müsste in einen Bestand schreiben, dessen
Cache-Autorität gerade niemand hält — Prinzip IV sagt, dass der Speicher-Cache autoritativ ist,
solange ein Spieler online ist, und über den umgekehrten Fall schweigt es aus gutem Grund. Ein
Anspruch verschiebt die Gutschrift auf einen Moment, in dem der Empfänger geladen ist und der
normale Weg gilt.

**Kein Verfall**, weil eine Frist einem Rückkehrer eine Belohnung nimmt, von der er nie erfahren
hat. Ein Anspruch ist eine Zeile; sie kostet nichts, und keine Uhr muss getestet, erklärt oder
später korrigiert werden.

**Verworfen.**
- **Beim Abschluss direkt gutschreiben**: hätte einen Schreibweg an der Sitzung vorbei gebraucht
  und für jeden belohnten Spieler einen geladenen Zustand, den es nicht gibt.
- **Verfall nach einer Saison oder einem Jahr**: begrenzt einen Bestand, der ohnehin klein ist,
  und bestraft genau den Spieler, den die Belohnung zurückholen sollte.

**Auswirkung.** Der eingefrorene Endstand und der Anspruch sind zwei neue, dauerhafte Bestände.
Sie entstehen selten — viermal im Jahr, nicht tausendmal am Tag — und sind damit der erste Fall in
diesem Projekt, für den der Write-Behind-Weg möglicherweise das falsche Werkzeug ist; die
Entscheidung darüber gehört in `/speckit-plan`, nicht hierher. Dass der Endstand eingefroren wird,
ist dagegen hier entschieden: eine Platzierung, die sich nach der Vergabe noch ändern kann, ist
keine.

---

## ADR-046: Die Saison kürt einen Spieler, nicht zwei Dutzend Ranglisten — eine gewichtete Gesamtwertung

**Status:** Angenommen · **Datum:** 2026-08-29 · **Blöcke:** B12

**Kontext.** B12 stellt für jede öffentliche Zählermetrik in jedem der vier Zeiträume eine
Rangliste bereit, dazu zwei Zustandsranglisten — zweiundzwanzig Listen (fünf Metriken in vier
Zeiträumen, plus Level und Coins). ADR-045 hat entschieden, dass eine Saison mit einer Belohnung
abschließt, aber nicht, **welche** dieser Listen belohnt wird.
Alle zweiundzwanzig zu belohnen hätte bedeutet, dass ein Spieler, der am letzten Saisontag zufällig
die Tagesrangliste anführt, dieselbe Auszeichnung bekommt wie einer mit drei Monaten Arbeit.

**Entscheidung.** Es gibt eine **Gesamtwertung**: eine Punktzahl je Konto über den
Saisonzeitraum, gebildet als Summe gewichteter Metrikwerte — je Metrik ein konfigurierbarer
Punktwert je Einheit. Nur sie wird belohnt. Die übrigen Ranglisten bleiben bestehen und sind Ehre
ohne Preis.

In die Punktzahl gehen **ausschließlich öffentliche Zählermetriken des Saisonzeitraums** ein.
**Zustandswerte sind ausgeschlossen** — Level, XP und Coins tragen den Fortschritt vergangener
Saisons in die laufende und würden alte Konten dauerhaft nach oben setzen, womit eine Saison
aufhörte, ein Neuanfang zu sein. **Private Werte sind ebenfalls ausgeschlossen**, weil eine
öffentliche Platzierung sonst aus Zahlen begründet wäre, die niemand nachsehen kann.

**Begründung.** Eine Summe gewichteter Werte ist die einzige Form, die ein Spieler im Fenster
nachrechnen kann. Deshalb ist auch gefordert, dass die Aufschlüsselung sichtbar ist: Wert,
Gewicht, Punkte, je beitragender Metrik. Eine Wertung, deren Zustandekommen man nicht sieht, wird
als Willkür gelesen — und bei einer Belohnung wird sie das lauter als anderswo.

**Der Endstand friert die Gewichtung mit ein.** Sonst ließe sich die Platzierung einer
abgeschlossenen Saison durch eine spätere Balancing-Änderung rückwirkend umsortieren, nachdem die
Belohnungen bereits vergeben sind.

**Verworfen.**
- **Alle zweiundzwanzig Ranglisten belohnen**: mehr Gewinner, aber ein Tagesstand am Stichtag ist
  Zufall, keine Leistung. Und es hätte über zweihundert Ansprüche je Saison erzeugt.
- **Nur die sechs Saisonwertungen je Metrik belohnen**: näher an der Leistung, aber es hätte
  sechs Spezialisten gekürt und keinen Spieler der Saison. Die Botschaft wäre unschärfer.
- **Eine normalisierte Punktzahl** (jede Metrik auf 0–100 skaliert): statistisch sauberer, aber
  niemand kann sie nachrechnen, und ein einzelner Ausreißer verschiebt die Skala aller anderen.

**Auswirkung.** Die Gesamtwertung ist die dreiundzwanzigste Rangliste und die einzige mit einer
Belohnung. Sie erzeugt eine neue Pflicht in der Konfigurationsprüfung: eine Gewichtung, die keine
bekannte Metrik nennt, ergäbe eine Rangliste aus Nullen und muss den Start scheitern lassen. Das
Balancing der Gewichte selbst gehört dem Betreiber — der Start prüft auf Gültigkeit, nicht auf
Geschmack.

---

## ADR-047: Der Klon leistet für den Spieler, kostet ihn aber nichts

**Status:** Angenommen · **Datum:** 2026-08-29 · **Blöcke:** B12, berührt B08 und B11

**Kontext.** B08 kennt beschworene Klone (`SummonEffect`). B11 hat für sie entschieden, dass sie
**keinen** Verschleiß verursachen (FR-041a): der Schaden, den ein Klon austeilt, nutzt die Waffe
des beschwörenden Spielers nicht ab. B12 muss dieselbe Kreatur ein zweites Mal einordnen — zählt
ihr Schaden für den **höchsten Schaden** des Spielers?

**Entscheidung.** Ja. Schaden eines Klons wird dem beschwörenden Spieler zugerechnet.

**Das ist bewusst nicht dieselbe Antwort wie in B11, und die Asymmetrie hat einen Grund.** Ein
Klon trägt **keine eigene Ausrüstung**, die sich abnutzen könnte — der Verschleiß hätte an
fremdem Gerät angesetzt, nämlich am Werkzeug des Spielers, das der Klon gar nicht führt. Sein
Schaden dagegen entsteht unmittelbar aus einer Fähigkeit, die der Spieler gewirkt und bezahlt
hat. Was der Klon **kostet**, kostet ihn; was er **leistet**, leistet der Spieler.

**Begründung.** Eine Beschwörung ist für die betroffenen Klassen kein Nebenweg, sondern der
Hauptweg, Schaden auszuteilen. Würde ihr Schaden nicht zählen, wäre die Schadensrangliste für
diese Klassen strukturell verschlossen — und zwar nicht, weil sie schwächer wären, sondern weil
die Statistik an der falschen Stelle nachsieht.

**Verworfen.**
- **Konsequent wie B11 behandeln** (Klonschaden zählt nicht): wäre die widerspruchsfreiere
  Regel auf dem Papier gewesen. Sie hätte aber eine ganze Spielweise aus einer öffentlichen
  Rangliste ausgeschlossen, ohne dass ein Spieler den Grund je erkennen könnte.

**Auswirkung.** Die Zuordnung „Schaden eines Klons gehört dem Beschwörer" gilt in B12 und
**nicht** rückwirkend in B11 — dort bleibt FR-041a unverändert. Wer die beiden Stellen
nebeneinander liest, muss die Asymmetrie erklärt bekommen; dieser ADR ist die Erklärung, und die
Spec verweist an beiden Enden darauf.

---

## ADR-048: B12 wohnt in eigenen Paketen — `statistics`, nicht `stats`

**Status:** Angenommen · **Datum:** 2026-08-29 · **Blöcke:** B12, berührt B04

**Kontext.** `plan.md` und `tasks.md` haben `rpg.core.stats`, `rpg.persistence.stats` und
`rpg.platform.stats` als **neue** Pakete für B12 geführt (`plan.md`, Verzeichnisbaum: „# neu").
Sie sind nicht neu. Alle drei gehören **B04**, der Attribut- und Stat-Engine: allein
`rpg.core.stats` hält 29 Klassen und ein `package-info.java`, das das Paket ausdrücklich für
sich beansprucht („What this block owns"). Der Irrtum ist bis in `quickstart.md` durchgeschlagen,
wo `--tests "rpg.core.stats.*"` B04s Tests mitgelaufen wäre und als B12-Beleg gezählt hätte.

**Entscheidung.** B12 zieht in eigene Pakete `rpg.core.statistics`,
`rpg.persistence.statistics` und `rpg.platform.statistics`. Die Klassen dieses Blocks tragen
durchgehend das Präfix `Statistics…` statt `Stats…`.

**Begründung.** Zwei Namenspaare hätten sonst nebeneinander gestanden, und beide sind von der
Sorte, die kein Test findet, weil jede Klasse für sich einwandfrei arbeitet — falsch ist nur,
welche jemand greift:

| B04 (vorhanden) | B12 (geplant) | |
|---|---|---|
| `StatConfig` | `StatsConfig` | ein Buchstabe, dasselbe Paket |
| `StatsModule` | `StatsModule` | derselbe Name, zwei Module |

Dieselbe Fehlerart hat dieses Projekt schon zweimal Zeit gekostet: die gemeinsame UUID für
Halter und Charakter (1614 Tests lang unsichtbar) und B10s zweite `bossStates`-Map neben der
des Moduls. Beide Male stimmte jede Hälfte für sich.

Dazu kommt: T001 verlangt ein `package-info`, das **die Grenze des Blocks nennt**. In einem
Paket, dessen Grenze bereits ein anderer Block gezogen hat, ist diese Aufgabe nicht erfüllbar —
sie wäre ein zweites Namensschild an derselben Tür.

**Verworfen.** *Einzug in B04s Pakete* — spart das Umschreiben der Aufgabenliste, verschiebt die
Kosten aber auf jede spätere Lesung. *`rpg.core.leaderboard`* — trennt ebenfalls sauber, trifft
aber nur US3 und US6; Erfassung (US1) und Profil (US2) sind der größere Teil des Blocks, und
`statistics.yml` heißt ohnehin schon so.

**Zeitpunkt.** Entschieden, bevor die erste Zeile B12-Code entstand. Betroffen waren nur
Pfadangaben in den Planungsunterlagen (123 Stellen, davon 113 in `tasks.md`); kein Quelltext.
