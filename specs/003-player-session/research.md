# Phase 0 Research: B03 · Spieler-Session & Datenlebenszyklus

Alle offenen technischen Fragen aus dem Technical Context sind hier aufgelöst. Es bleibt keine
`NEEDS CLARIFICATION`-Markierung übrig.

Die Paper-API 26.2 wurde am 2026-08-19 gegen das tatsächliche Artefakt geprüft, nicht aus dem
Gedächtnis beschrieben.

## Wann geladen wird: vor dem Join statt beim Join

**Decision**: Der Zustand wird bereits in `AsyncPlayerPreLoginEvent` geladen — also **bevor** der
Spieler die Welt betritt — und beim `PlayerJoinEvent` nur noch abgeholt. Der sichere Zustand
(bewegungsgesperrt, schadensimmun) bleibt als **Rückfallebene** bestehen, greift im Normalfall aber
nie.

**Rationale**: Das war der wichtigste Befund dieser Phase. Die Spec beschreibt den Ablauf als „Join
→ Laden → sicherer Zustand → Freigabe", weil das der übliche Weg ist. Paper bietet aber einen
früheren Einstiegspunkt, und der ist an drei Stellen strikt besser:

1. **Der Fehlerpfad wird trivial.** `AsyncPlayerPreLoginEvent.disallow(...)` weist die Verbindung
   ab, bevor überhaupt ein Spielerobjekt existiert. Damit ist FR-011/FR-012 nicht nur erfüllt,
   sondern strukturell unmöglich zu verletzen: Es gibt zu diesem Zeitpunkt nichts, was ein leeres
   Profil überschreiben könnte. Beim Laden im `PlayerJoinEvent` müsste man den Spieler nachträglich
   hinauswerfen und dabei sicherstellen, dass in der Zwischenzeit nichts geschrieben wurde.
2. **Das Ereignis läuft ohnehin asynchron.** Ein blockierender Datenbankzugriff ist dort erlaubt und
   erwartet — er liegt außerhalb des Ticks (Constitution I.2). Der Ladepfad braucht damit keine
   eigene Nebenläufigkeitskonstruktion.
3. **Der sichere Zustand hat im Normalbetrieb die Länge null.** Ein Spieler, der die Welt betritt,
   hat seine Sitzung bereits. Die in FR-002 beschriebene Wartezeit ist dann nicht spürbar, weil sie
   nicht stattfindet.

**Warum der sichere Zustand trotzdem gebaut wird**: Er ist die Zusicherung, dass FR-002 und FR-004
**in jedem** Fall gelten, nicht nur im erwarteten. Wenn beim `PlayerJoinEvent` wider Erwarten keine
vorgeladene Sitzung vorliegt — weil ein anderes Plugin den Vorgang beeinflusst hat, weil die
Zwischenablage abgelaufen ist, oder weil eine künftige Änderung den Vorlade-Pfad umgeht — darf der
Spieler nicht mit Standardwerten losspielen. Dann greift der sichere Zustand und lädt nach. Eine
Rückfallebene, die im Normalbetrieb nie anspringt, ist genau das, was sie sein soll.

**Alternatives considered**:

- **Nur beim `PlayerJoinEvent` laden**: Der in der Spec skizzierte Weg. Verworfen, weil der
  Fehlerpfad dann einen bereits in der Welt befindlichen Spieler zurückabwickeln muss — genau die
  Stelle, an der laut Steckbrief RPG-Plugins Datenverlust produzieren.
- **Nur in `AsyncPlayerPreLoginEvent` laden, ohne Rückfallebene**: Verworfen, weil eine fehlende
  Zwischenablage dann stillschweigend zu einem Spieler ohne Sitzung führt.

## Sicherer Zustand: Umsetzung

**Decision**: Schadensimmunität über `Entity.setInvulnerable(true)`. Bewegungssperre über das
Abbrechen von `PlayerMoveEvent`, **aber nur bei einem Blockwechsel** und **nur solange überhaupt
eine Sitzung lädt**.

**Rationale**: Die Paper-API 26.2 bietet `setInvulnerable(boolean)` direkt — geprüft. Für Bewegung
gibt es keine entsprechende Methode; `setWalkSpeed(0)` stoppt weder Fall, Rückstoß noch Schwung,
taugt also nicht als Zusicherung.

`PlayerMoveEvent` ist eines der heißesten Ereignisse überhaupt und wird bei 150–200 Spielern
mehrfach je Tick je Spieler ausgelöst. Zwei Maßnahmen halten das im Rahmen von Constitution II:

- Der Handler prüft als **erstes** ein Feld, das im Normalbetrieb `false` ist („lädt gerade
  jemand?"). Ist es das, kehrt er sofort zurück — ein Feldzugriff, keine Allokation, keine
  Map-Abfrage.
- Erst danach wird auf Blockwechsel geprüft. Kopfdrehungen und Bewegungen innerhalb desselben
  Blocks lösen nichts aus.

Da der sichere Zustand durch das Vorladen im Normalbetrieb nie aktiv ist, steht das Flag praktisch
immer auf `false` und der Handler ist ein einzelner Vergleich.

**Alternatives considered**:

- **Trank-Effekt Langsamkeit 255**: Stoppt Gehen, nicht Fallen oder Rückstoß, und ist für den
  Spieler sichtbar. Verworfen.
- **Spielmodus Zuschauer während des Ladens**: Wirksam, verändert aber sichtbar den Spielzustand
  und muss beim Fehlschlag zurückgesetzt werden — ein zusätzlicher Zustand, der schiefgehen kann.
- **Handler dauerhaft abmelden, wenn niemand lädt**: An- und Abmelden von Listenern zur Laufzeit
  ist teurer und fehleranfälliger als der Feldvergleich.

## Charakter-Ebene ohne Umbau von B02

**Decision**: B03 bringt eine **eigene Flyway-Migration** mit, die die Tabelle `rpg.character`
anlegt. Die Migrationsdateien liegen im selben Klassenpfad-Verzeichnis `db/migration` wie die von
B02, aber in einem eigenen Versionsraum: **`V3_1__player_characters.sql`**.

**Rationale**: Flyway durchsucht den Klassenpfad, nicht ein einzelnes Modul. Legt jeder Block seine
Migrationen unter demselben Pfad im eigenen Modul ab, sieht Flyway sie alle — ohne dass B02
angefasst werden muss.

Der Versionsraum je Block (`V{Blocknummer}_{lfd}`) macht Versionskollisionen **strukturell**
unmöglich statt durch Absprache. Flyway liest `V3_1` als Version `3.1`; die Ordnung `1 < 3.1 < 4.1`
stimmt mit der Blockreihenfolge überein. B02 behält `V1__baseline.sql` unverändert.

**Konsequenz für das Datenmodell**: `rpg.player_state` bleibt die **Account**-Ebene (Identität,
Fassung, Revision, Anonymisierung). Die neue Tabelle `rpg.character` trägt den eigentlichen
Spielfortschritt und ist über `(player_id, class)` eindeutig — womit die Regel „höchstens ein
Charakter je Klasse" (FR-017) im Schlüssel steckt und nicht in Anwendungslogik.

**Alternatives considered**:

- **`player_state` um Charakterspalten erweitern**: Hätte drei Charaktere in eine Zeile gezwungen
  oder drei Spaltensätze erfordert. Verworfen.
- **Migrationen zentral in `rpg-persistence` sammeln**: Jeder neue Block müsste dann ein fremdes
  Modul anfassen. Verworfen — genau das soll die Blockgrenze verhindern.

## Wo der Datenzugriff liegt

**Decision**: Die Schnittstelle `CharacterRepository` liegt in `rpg-core`, ihre JDBC-Umsetzung in
`rpg-persistence`. Die Sitzungslogik selbst liegt in `rpg-core`, die Paper-Anbindung in
`rpg-platform`.

**Rationale**: B02 hat mit `NoDirectDatabaseAccessTest` eine statische Prüfung ausgeliefert, die
`java.sql` und `DataSource` **außerhalb** von `rpg-persistence` verbietet. Diese Regel gilt
ausdrücklich für alle Folgeblöcke — sie hier zu umgehen, indem B03 ein eigenes Modul mit eigenem
SQL bekäme, würde die Kapselung aufgeben, die B02 gerade erst mechanisch abgesichert hat.

`rpg-persistence` ist damit nicht „das Modul von B02", sondern die JDBC-Schicht des gesamten
Projekts. Das ist die Lesart, die zur Modulstruktur aus B01 passt.

## Sammelladen in wenigen Abfragen

**Decision**: Ein eigener Ladevorgang `SessionBundleLoader` in `rpg-persistence`, der Account,
Charaktere und Item-Instanzen in **einer** Datenbankrunde über drei Anweisungen in einer
Transaktion liest — nicht über drei getrennte Repository-Aufrufe.

**Rationale**: FR-005 verlangt möglichst wenige Abfragen. Drei einzelne Repository-Aufrufe wären
drei Verbindungsentnahmen und drei Roundtrips; bei 200 gleichzeitigen Anmeldungen (SC-005) ist das
der Unterschied zwischen einem knappen und einem entspannten Login-Pool.

Ein einzelner `JOIN` über alle drei Tabellen wurde geprüft und verworfen: Ein Spieler mit drei
Charakteren und fünfzig Items ergäbe 150 Zeilen mit vielfach wiederholten Account- und
Charakterdaten, die im Anwendungscode wieder entfaltet werden müssten. Drei Anweisungen auf einer
Verbindung sind schneller und deutlich einfacher zu lesen.

**Alternatives considered**:

- **Drei parallele `CompletableFuture`**: Nebenläufig, aber drei Verbindungen je Anmeldung — genau
  die Belastung des Login-Pools, die FR-008 aus B02 vermeiden soll.
- **Ein einziger `JOIN`**: Siehe oben; Zeilenvervielfachung ohne Gewinn.

## Sitzungs-Cache und Ausschluss von Speicherlecks

**Decision**: `ConcurrentHashMap` nach Spielerkennung, plus ein **periodischer Abgleich**, der
Sitzungen entfernt, deren Spieler nicht mehr verbunden ist.

**Rationale**: Sorgfalt allein reicht hier nicht. Ein Sitzungsobjekt kann auf Wegen liegenbleiben,
die man beim Schreiben des Entladepfads nicht bedacht hat — ein Plugin, das das Quit-Ereignis
abbricht, ein Fehler mitten im Aufräumen, eine künftige Änderung. FR-009 und SC-008 fordern aber
eine Zusicherung, keine Absicht.

Der Abgleich liefert genau das: Er vergleicht die Schlüssel des Caches gegen die tatsächlich
verbundenen Spieler und räumt die Differenz weg — unabhängig davon, warum sie entstanden ist. Er
läuft über `Scheduler.runAsyncDelayed` und plant sich selbst neu, also über dieselbe Mechanik wie
B02s Autosave und ohne zweiten Thread-Pool (Constitution I).

Derselbe Abgleich räumt auch die Zwischenablage der vorgeladenen Sitzungen: Wenn eine Anmeldung im
Vorlade-Ereignis erfolgreich war, der Spieler die Welt aber nie betreten hat, bliebe der Eintrag
sonst liegen.

**Alternatives considered**:

- **Nur auf den Entladepfad vertrauen**: Genau die Annahme, die SC-008 überprüfbar machen soll.
- **`WeakHashMap` mit Spielerobjekt als Schlüssel**: Der Zeitpunkt der Aufräumung wäre nicht
  bestimmbar, und der abschließende Schreibvorgang darf nicht davon abhängen, wann der
  Speicherbereiniger läuft.

## Erfassen aller Sitzungsenden

**Decision**: `PlayerQuitEvent` als einziger Auslöser für das Entladen.

**Rationale**: In Bukkit deckt `PlayerQuitEvent` alle drei in FR-007 genannten Fälle ab — reguläres
Verlassen, Kick und Verbindungsabbruch. Ein zusätzlicher Handler auf `PlayerKickEvent` wäre nicht
nur überflüssig, sondern schädlich: Er würde bei einem Kick ein zweites Entladen auslösen und
damit genau den doppelten Schreibvorgang erzeugen, den FR-014 ausschließt.

`PlayerConnectionCloseEvent` von Paper wurde geprüft. Es feuert auch für Verbindungen, die nie zu
einem Join geführt haben, und ist damit die richtige Stelle, um die Zwischenablage einer
vorgeladenen, aber nie abgeholten Sitzung zu räumen — nicht aber für das Entladen einer Sitzung.

## Teststrategie

**Decision**: Die Lebenszyklusregeln in `rpg-core` als serverfreie Unit-Tests; die Paper-Anbindung
(sicherer Zustand, Ereignisreihenfolge) über MockBukkit; das Sammelladen und die Charakter-Tabelle
über Testcontainers gegen echtes PostgreSQL — dieselbe Singleton-Container-Konstruktion wie in B02.

**Rationale**: Folgt der in B01 und B02 bewährten Aufteilung. Zwei Punkte aus der Erfahrung dieser
beiden Blöcke werden ausdrücklich mitgenommen:

- Bei MockBukkit- und Testcontainers-Tests ist die Zahl der **übersprungenen** Tests zu prüfen, nicht
  nur die der fehlgeschlagenen. In B01 hat MockBukkit drei Tests still als „skipped" gemeldet, was
  wie Abdeckung aussah.
- Das Zeitverhalten (5-Sekunden-Frist, Abgleichsintervall) wird über eine steuerbare Uhr geprüft,
  nicht über Wartezeiten im Test.
