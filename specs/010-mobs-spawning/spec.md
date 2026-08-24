# Feature Specification: B10 · Mobs & Horden-Spawning

**Feature Branch**: `010-mobs-spawning`

**Created**: 2026-08-24

**Status**: Draft

**Input**: Blocksteckbrief `blocks/B10-mobs-spawning.md`, die entschiedenen Punkte in
`06-open-questions.md` (Abschnitt B10), sowie die drei Schnittstellen, die B05, B06 und B08b für
diesen Block bereits offen gelassen haben.

---

## Ausgangslage

Drei Blöcke warten auf diesen hier, und zwar an einer benannten Stelle.

**B05** stattet Mobs heute übergangsweise aus `combat.yml` mit Attributwerten aus, hinter
`MobStatProvider` — mit der ausdrücklichen Zusage, dass B10 den Anbieter unter demselben Schlüssel
ersetzt, statt einen zweiten einzuführen (ADR, `02-decisions.md` Abschnitt 5). **B06** beantwortet
`MobXpProvider.xpFor` aus eigener Konfiguration, **B08b** `MobCoinProvider.coinsFor` — beide mit
demselben Satz im Javadoc: *„until B10 exists, then B10 replaces the provider through this same
interface."*

**B09** hat den Anker bereits geliefert: `Zones.spawnAreasOf(zoneKey)` gibt je Region mehrere
Bereiche mit Kennung und Geometrie heraus — und sonst nichts. Keine Rolle, keine Art, keine
Kreaturenliste, kein Boss-Kennzeichen. Ein Bossbereich unterscheidet sich geometrisch von keinem
anderen; er unterscheidet sich in dem, was darin steht, und das gehört hierher. Ausgeliefert sind
sechs Regionen mit je zwei Bereichen, also zwölf.

Was dieser Block liefert, ist demnach nicht „auch noch Mobs", sondern die Antwort auf drei offene
Fragen und die Füllung eines leeren Ankers.

---

## Clarifications

### Session 2026-08-23 — vor `/specify`, festgehalten in `06-open-questions.md`

- **Acht Mob-Arten je Region, dazu ein Boss je Region** mit höheren Attributen. Bei sechs Regionen
  sind das 48 Arten und 6 Bosse.
- **Attribute, Level und Art jedes Mobs sind konfigurationsdefiniert** und ohne Codeänderung
  austauschbar (Prinzip V). Ein neuer Mob-Typ entsteht rein per Konfiguration.
- **Gespawnt wird an ausgewählten Stellen der Gefahrenzone.** Die Bereiche liefert B09 benannt, die
  Horden füllt B10.
- **Skalierung nach Spieleranzahl**: mehr Spieler in einer Zone führen zu höherer Spawnrate und
  schnellerem Respawn — **Dichte- und Respawn-Skalierung, nicht Mob-Stärke**.

### Session 2026-08-23 — ADR-031

- **Der Lasttest ist keine Bedingung dafür, dass dieser Block fertig ist.** Der Zielwert (800 aktive
  Custom-Mobs bei 150 Spielern, p95 MSPT < 40 ms) bleibt verbindlich, der Nachweis gehört in B15s
  Lasttestphase. Zuvor nannte Prinzip VII B10 namentlich lasttestpflichtig. Was dieser Block
  belegen muss, ist die eigene Rechenarbeit ohne Volllast — eine wiederholbare Messung, keine
  Behauptung.

### Session 2026-08-24 — bei `/specify`

- **Der Schlüssel der drei wartenden Schnittstellen ändert seine Bedeutung, nicht seine Form.**
  Heute liefert `CoinDropListener` `creature.getType().name()` — den Vanilla-Typnamen. Mit acht
  Arten je Region auf wenigen Vanilla-Entities kann dieser Name nicht mehr unterscheiden, welche
  Kreatur gefallen ist: vier Arten auf `ZOMBIE` wären vier Mal derselbe Schlüssel. Der Schlüssel
  wird deshalb die **Mob-Art**, nicht der Vanilla-Typ. Die Schnittstellen (`String → OptionalLong`
  beziehungsweise `String → Optional<ModifierSet>`) bleiben unverändert — das war die Zusage, und
  sie wird eingehalten.
- **Eine Kreatur ohne Kennzeichnung bleibt ein Vanilla-Mob** und fällt auf den konfigurierten
  Standardwert zurück. Leeres Ergebnis heißt weiterhin „kein eigener Eintrag" und niemals Null.
- **Elite- und Champion-Varianten sind nicht Teil dieses Blocks.** Der Steckbrief nannte sie im
  Umfang, die Entscheidung vom 2026-08-23 nennt acht Arten und einen Boss je Region. Eine dritte
  Stufe dazwischen ist weder entschieden noch nötig, um den Block spielbar zu machen.

### Session 2026-08-24 — die drei Zuschnittsfragen, beantwortet

- **Vanilla-Mobs werden vollständig unterdrückt.** Das natürliche Spawning ist aus — überall, auch
  nachts, auch in Höhlen. Nur dieser Block erzeugt Kreaturen. Damit ist das Budget aus FR-013 die
  einzige Quelle lebender Kreaturen und die harte Grenze gilt wirklich für alles, was in der Welt
  steht; das ist die Voraussetzung dafür, dass die TPS-Zusage des Projekts überhaupt haltbar ist.
  Absichtlich gesetzte Kreaturen — Spawn-Ei, Betreiber-Kommando — bleiben möglich und bekommen die
  Standardwerte (FR-009). Unterdrückt wird das *natürliche* Spawning, nicht die Fähigkeit, eine
  Kreatur zu setzen.
- **Nachschub ist kontinuierlich, nicht in Wellen.** Getötete Kreaturen werden laufend ersetzt, die
  Dichte pendelt sich um den Zielwert ein. Eine Region fühlt sich damit immer gleich an, und es gibt
  keinen Zonenzustand „Welle läuft / Welle geräumt / Pause", der die Frage aufwerfen würde, was mit
  einer Welle passiert, die niemand zu Ende räumt.
- **Ein Boss ist eine Mob-Art mit deutlich höheren Attributen und einem Respawn-Timer — mehr
  nicht.** Keine eigenen Fähigkeiten, keine Phasen. Das ist ehrlich benannt: es ist ein dicker Mob
  und kein Kampf mit Wendungen. **Der eigentliche Bosskampf kommt später als Dungeon-Boss**, mit
  Instanzen und Fähigkeiten; er ist damit ausdrücklich kein Teil dieses Blocks und hängt an einer
  Instanzwelt, die es nach ADR-006 noch nicht gibt. Der Vorteil hier: der Boss braucht keine Zeile
  neuen Code, nur Konfiguration.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Eine Kreatur ist eine Art aus der Konfiguration (Priority: P1)

Ein Betreiber trägt in `mobs.yml` eine Art ein: eine Kennung, ein Vanilla-Entity als Basis, ein
Level, Attributwerte, ein Anzeigename, Erfahrung und Coins. Beim Start wird die Datei gegen ein
Schema geprüft; ein Fehler bricht den Start ab, statt eine halb gültige Kreatur ins Spiel zu lassen.
Steht eine so definierte Kreatur in der Welt, dann gelten für sie diese Werte — im Kampf, beim Tod,
auf dem Namensschild.

**Why this priority**: Ohne sie hat kein anderer Teil dieses Blocks eine Grundlage. Die Spawn-Engine
braucht etwas zu setzen, der Boss ist ein Sonderfall davon, und die drei wartenden Schnittstellen
können erst antworten, wenn es Arten gibt, nach denen sie gefragt werden können. Sie ist außerdem
die einzige Geschichte, die für sich allein schon einen Nutzen hat: mit ihr allein kann ein Betreiber
Kreaturen von Hand setzen und sie verhalten sich richtig.

**Independent Test**: Eine Konfiguration mit mehreren Arten auf demselben Vanilla-Entity laden, je
eine Kreatur erzeugen und prüfen, dass Attributwerte, Erfahrung und Coins der **Art** folgen und
nicht dem Vanilla-Typ. Eine ungekennzeichnete Kreatur derselben Vanilla-Art muss dabei weiterhin die
Standardwerte bekommen.

**Acceptance Scenarios**:

1. **Given** zwei Arten `greenfields.rotling` und `greenfields.brute`, beide auf Basis `ZOMBIE`, mit
   unterschiedlichen Werten, **When** je eine Kreatur erzeugt wird, **Then** hat jede die Werte ihrer
   Art, und die beiden unterscheiden sich.
2. **Given** dieselbe Konfiguration, **When** ein gewöhnlicher Zombie ohne Kennzeichnung erscheint,
   **Then** gelten für ihn die konfigurierten Standardwerte und nicht die einer der beiden Arten.
3. **Given** eine Art mit einem unbekannten Basis-Entity, **When** der Server startet, **Then**
   bricht der Start mit einer Meldung ab, die Datei, Schlüssel und Grund nennt.
4. **Given** eine gekennzeichnete Kreatur, **When** sie stirbt, **Then** melden Erfahrung und Coins
   den Schlüssel ihrer Art an B06 und B08b, und beide Blöcke buchen die für diese Art konfigurierten
   Beträge.
5. **Given** eine gekennzeichnete Kreatur, **When** ein Spieler sie ansieht, **Then** trägt sie den
   Anzeigenamen ihrer Art und ihr Level, nicht den Vanilla-Namen.

---

### User Story 2 - Die Horde steht da, wo B09 den Bereich benannt hat (Priority: P2)

In einer Region halten sich Spieler auf. In den Spawn-Bereichen dieser Region erscheinen Kreaturen
der dort konfigurierten Arten — verteilt über mehrere Ticks, nie alle auf einmal — bis das Budget
der Zone erreicht ist. Danach erscheint keine weitere, egal wie viele Spieler dazukommen.

**Why this priority**: Es ist der eigentliche Zweck des Blocks und der erste im Spiel sichtbare
Effekt. Es ist außerdem die Geschichte, an der die harte Grenze hängt: ein Budget, das erst später
eingebaut wird, ist ein Budget, das einmal überschritten wurde.

**Independent Test**: Mit einem Spieler in *Greenfields* stehen und zählen, wie viele Kreaturen
entstehen und wo. Sie müssen innerhalb der beiden Bereiche liegen und die Zonengrenze einhalten. Ein
künstlich kleines Budget setzen und prüfen, dass es nicht überschritten wird.

**Acceptance Scenarios**:

1. **Given** ein Spieler in einer Region mit zwei Spawn-Bereichen, **When** die Spawn-Engine läuft,
   **Then** entstehen Kreaturen nur innerhalb dieser Bereiche und keine außerhalb.
2. **Given** ein Zonenbudget von N, **When** die Engine wiederholt läuft, **Then** ist die Zahl
   lebender Kreaturen dieser Zone zu keinem Zeitpunkt größer als N.
3. **Given** ein volles Budget, **When** zehn weitere Spieler die Zone betreten, **Then** entsteht
   keine einzige Kreatur über das Budget hinaus — die Skalierung aus US4 verschiebt die Dichte
   innerhalb des Budgets, nicht das Budget.
4. **Given** eine Zone ohne anwesende Spieler, **When** die Engine läuft, **Then** entsteht dort
   nichts.
5. **Given** eine Region, deren Spawn-Bereiche leer sind oder deren Zonenschlüssel unbekannt ist,
   **When** die Engine sie betrachtet, **Then** entsteht dort nichts, und der Lauf bricht nicht ab.
6. **Given** einen Durchlauf, der viele Kreaturen zu setzen hätte, **When** er läuft, **Then**
   verteilt er die Arbeit über mehrere Ticks, statt sie in einem zu bündeln.

---

### User Story 2b - Die Welt spawnt nichts mehr von selbst (Priority: P2)

Das natürliche Spawning von Vanilla-Kreaturen ist aus. Nachts auf dem Feld, tief in einer Höhle,
in einem dunklen Winkel des Hubs entsteht nichts. Was lebt, hat dieser Block gesetzt — oder jemand
hat es absichtlich gesetzt.

**Why this priority**: Sie gehört mit US2 zusammen und trägt dieselbe Priorität, denn sie ist die
Bedingung dafür, dass das Budget aus US2 überhaupt etwas bedeutet. Ein Budget, das nur die eigenen
Kreaturen zählt, während die Welt daneben unbegrenzt weiterspawnt, ist keine Grenze, sondern eine
Buchhaltung über einen Teil der Last.

**Independent Test**: Eine Nacht in einer Region ohne Spawn-Bereich abwarten und in einer Höhle
nachsehen. Es darf nichts entstanden sein. Danach ein Spawn-Ei benutzen — die Kreatur muss
erscheinen und Standardwerte haben.

**Acceptance Scenarios**:

1. **Given** eine Welt mit diesem Block, **When** es Nacht wird, **Then** entsteht auf der
   Oberfläche keine Kreatur von selbst.
2. **Given** einen unbeleuchteten Bereich, **When** Zeit vergeht, **Then** entsteht dort keine
   Kreatur von selbst.
3. **Given** ein Spawn-Ei oder ein Betreiber-Kommando, **When** damit eine Kreatur gesetzt wird,
   **Then** erscheint sie und bekommt die konfigurierten Standardwerte — unterdrückt ist das
   natürliche Spawning, nicht das Setzen.
4. **Given** die Unterdrückung, **When** dieser Block selbst eine Kreatur setzt, **Then** wird sie
   davon nicht mitunterdrückt.

---

### User Story 3 - Was niemand sieht, ist weg (Priority: P3)

Verlassen die Spieler eine Region, verschwinden deren Kreaturen binnen einer festgelegten Zeit — sie
werden **entfernt**, nicht schlafen gelegt. Auch einzelne Kreaturen, die sich weit von jedem Spieler
entfernt haben, werden aufgeräumt.

**Why this priority**: Ohne sie wächst die Welt monoton voll, und das Budget aus US2 schützt nur die
Neuentstehung, nicht den Bestand. Sie ist von US2 unabhängig prüfbar und liefert für sich allein den
Unterschied zwischen einem Server, der über Stunden stabil bleibt, und einem, der es nicht tut.

**Independent Test**: Kreaturen in einer Region entstehen lassen, alle Spieler herausbewegen, warten
und zählen. Nach der festgelegten Zeit muss die Zahl null sein.

**Acceptance Scenarios**:

1. **Given** Kreaturen in einer Region und keinen Spieler mehr darin, **When** die festgelegte Zeit
   vergangen ist, **Then** ist keine dieser Kreaturen mehr in der Welt.
2. **Given** eine Kreatur, die weiter als die konfigurierte Reichweite von jedem Spieler entfernt
   ist, **When** aufgeräumt wird, **Then** wird sie entfernt, auch wenn in ihrer Zone noch Spieler
   stehen.
3. **Given** eine entfernte Kreatur, **When** sie verschwindet, **Then** fallen dabei weder Coins
   noch Erfahrung an — Entfernen ist kein Tod, und wer nicht getötet hat, bekommt nichts.
4. **Given** eine Kreatur, die gerade mit einem Spieler kämpft, **When** aufgeräumt wird, **Then**
   bleibt sie — eine Kreatur, die einem Spieler unter den Händen verschwindet, ist schlimmer als
   eine, die einen Tick zu lange lebt.
5. **Given** einen Serverstopp, **When** er läuft, **Then** hinterlässt dieser Block keine
   Kreaturen, die beim nächsten Start als herrenloser Bestand wieder auftauchen.

---

### User Story 4 - Mehr Spieler, dichtere Horde (Priority: P4)

Je mehr Spieler in einer Region stehen, desto dichter füllen sich ihre Spawn-Bereiche und desto
schneller kommt Nachschub. Die Kreaturen werden davon **nicht stärker** — es sind mehr, nicht
härtere.

**Why this priority**: Es ist eine ausdrücklich getroffene Entscheidung und der Unterschied zwischen
einer Region, die sich zu zweit richtig anfühlt, und einer, die zu zwanzig leer wirkt. Sie setzt US2
voraus und ist ohne sie nicht prüfbar, deshalb steht sie dahinter.

**Independent Test**: Dieselbe Region mit einem, dann mit fünf Spielern beobachten. Die Zahl
lebender Kreaturen und die Nachschubrate steigen; die Werte einer einzelnen Kreatur sind in beiden
Fällen identisch.

**Acceptance Scenarios**:

1. **Given** eine Region mit einem Spieler, **When** vier weitere dazukommen, **Then** steigt die
   Zahl der Kreaturen und die Nachschubgeschwindigkeit.
2. **Given** dieselbe Art in beiden Situationen, **When** ihre Werte gelesen werden, **Then** sind
   sie identisch — Skalierung berührt Dichte und Respawn, niemals Attribute.
3. **Given** eine Skalierung, die rechnerisch über das Zonenbudget hinausführen würde, **When** sie
   angewendet wird, **Then** endet sie am Budget. Das Budget ist die harte Grenze, der skalierte
   Wert ein Ziel.
4. **Given** Spieler, die die Region verlassen, **When** die Zahl sinkt, **Then** sinkt die Dichte
   entsprechend, und der Überhang wird über US3 abgebaut statt sofort gelöscht.

---

### User Story 5 - Der Boss der Region (Priority: P5)

Jede Region hat einen Boss: dieselbe Mechanik wie jede andere Art, aber mit deutlich höheren
Attributen, einem eigenen Platz und einem Respawn-Timer. Er zählt gegen dasselbe Budget wie alles
andere.

**Why this priority**: Er ist das Ziel, auf das eine Region hinausläuft, aber sie ist ohne ihn
spielbar. Er ist ein Sonderfall von US1 und US2 und braucht beide.

**Was er ausdrücklich nicht ist**: ein Kampf mit eigenen Fähigkeiten oder Phasen. Der Boss dieses
Blocks ist eine Mob-Art mit höheren Zahlen und einem Timer, und er entsteht rein aus Konfiguration.
Der eigentliche Bosskampf kommt später als **Dungeon-Boss** mit Instanzen und Fähigkeiten und
gehört nicht hierher.

**Independent Test**: In jeder der sechs Regionen prüfen, dass genau ein Boss existiert, dass seine
Werte über denen der acht gewöhnlichen Arten liegen, und dass er nach dem Tod erst nach Ablauf
seines Timers wiederkommt.

**Acceptance Scenarios**:

1. **Given** eine Region mit Spielern, **When** die Engine läuft, **Then** existiert höchstens ein
   Boss dieser Region gleichzeitig.
2. **Given** einen getöteten Boss, **When** die konfigurierte Zeit noch nicht abgelaufen ist,
   **Then** erscheint kein zweiter.
3. **Given** einen getöteten Boss, **When** die Zeit abgelaufen ist und Spieler anwesend sind,
   **Then** erscheint er wieder an seinem Platz.
4. **Given** einen Boss, **When** das Zonenbudget betrachtet wird, **Then** zählt er darin mit wie
   jede andere Kreatur.
5. **Given** einen Boss und keine Spieler in der Region, **When** aufgeräumt wird, **Then** wird
   auch er entfernt, und sein Respawn-Timer läuft davon unberührt weiter.

---

### User Story 6 - Die Horde denkt nicht mehr, als sie darf (Priority: P6)

Kreaturen suchen ihr Ziel gedrosselt statt in jedem Tick, und nur innerhalb einer begrenzten
Reichweite. Wer kein Ziel in Reichweite hat, kostet fast nichts.

**Why this priority**: Pathfinding ist laut Steckbrief die teuerste Einzelkomponente des ganzen
Projekts, und dieser Block entscheidet über die TPS des Servers. Sie steht dennoch hinten, weil sie
eine Optimierung an vorhandenem Verhalten ist: US2 bis US5 liefern die Kreaturen, deren Kosten hier
begrenzt werden. Ohne sie ist der Block spielbar, aber nicht tragfähig.

**Independent Test**: Eine wiederholbare Messung der eigenen Rechenarbeit ohne Volllast — die
Kosten eines Spawn- und eines Zielsuchdurchlaufs bei fester Kreaturenzahl, mit und ohne Drosselung.
Der Nachweis unter 150 Spielern und 800 Mobs gehört nach ADR-031 in B15.

**Acceptance Scenarios**:

1. **Given** eine Kreatur ohne Spieler in Reichweite, **When** ein Tick vergeht, **Then** führt sie
   keine Zielsuche durch.
2. **Given** eine Kreatur mit einem Ziel, **When** mehrere Ticks vergehen, **Then** sucht sie ihr
   Ziel höchstens im konfigurierten Abstand neu und nicht in jedem Tick.
3. **Given** eine Messung der Spawn- und Zielsuchkosten, **When** sie wiederholt wird, **Then**
   liefert sie vergleichbare Werte, und die Zahlen liegen dokumentiert vor.
4. **Given** die konfigurierte Zielsuchreichweite, **When** ein Spieler sie überschreitet, **Then**
   verliert die Kreatur ihr Ziel, statt ihm über die halbe Region zu folgen.

---

### User Story 7 - Der Klon zieht die Mobs auf sich (Priority: P7)

Der Klon des Rogue aus B08 soll Kreaturen auf sich ziehen. Das steht seit B08 in der Roadmap und war
bis hierher blockiert, weil es keine Kreaturen gab, die man hätte ziehen können.

**Why this priority**: Anschlussarbeit ohne eigenen Nutzen für diesen Block, aber eine benannte
offene Zusage. Sie braucht US1 bis US6 und steht deshalb ganz hinten.

**Independent Test**: Einen Klon in eine Gruppe von Kreaturen setzen und prüfen, dass ihre Zielsuche
ihn wählt, solange er steht — und dass sie nach seinem Ende wieder den Spieler wählen.

**Acceptance Scenarios**:

1. **Given** einen Klon in Reichweite einer Kreatur, **When** sie ihr Ziel wählt, **Then** wählt sie
   den Klon.
2. **Given** einen abgelaufenen Klon, **When** die Kreatur ihr Ziel neu wählt, **Then** wählt sie
   wieder den Spieler.
3. **Given** einen Klon, **When** die Drosselung aus US6 gilt, **Then** gilt sie auch hier — das
   Umschwenken auf den Klon ist eine Zielsuche und keine Ausnahme davon.

---

### Edge Cases

- **Ein Spieler meldet sich mitten in der Horde ab.** Das Budget muss die Kreaturen weiterhin
  zählen, und die Aufräumzeit aus US3 beginnt erst, wenn wirklich niemand mehr da ist.
- **Ein Spieler reist per Wegpunkt in eine leere Region.** Dort steht noch nichts; die Engine muss
  die Region füllen, ohne alles in einem Tick zu setzen.
- **Eine Kreatur wird über die Zonengrenze gedrängt.** Sie gehört weiterhin zum Budget ihrer
  Ursprungszone; sonst ließe sich das Budget umgehen, indem man Kreaturen hinausschiebt.
- **Zwei Regionen liegen dicht beieinander und ihre Chunks überlappen.** Das Chunk-Budget gilt
  unabhängig vom Zonenbudget, und die schärfere der beiden Grenzen entscheidet.
- **Ein Konfigurations-Reload während des Betriebs.** Bereits stehende Kreaturen behalten die Werte,
  mit denen sie erzeugt wurden; neue bekommen die neuen. Alles andere hieße, jede lebende Kreatur
  mitten im Kampf umzurechnen.
- **Ein Boss wird über seinen Bereich hinausgelockt und dort getötet.** Der Respawn-Timer läuft
  trotzdem, und er kommt an seinem Platz zurück, nicht dort, wo er gefallen ist.
- **Eine Kreatur ohne Art steht in der Welt** — ein Betreiber hat sie gesetzt, oder sie stammt aus
  der Zeit vor der Unterdrückung. Sie bekommt die Standardwerte; sie ist nicht kaputt, nur
  gewöhnlich.
- **Die Welt enthält nach der Unterdrückung keine Tiere mehr, die von selbst nachwachsen.** Wolle,
  Leder und Fleisch aus natürlichem Spawning entfallen damit. Das ist eine Folge der Entscheidung
  und keine Lücke dieses Blocks — woher ein Spieler solche Dinge bekommt, ist eine Inhaltsfrage für
  B11 und B16 und wird dort beantwortet, nicht hier stillschweigend durch eine Ausnahme.
- **Die Spawn-Bereiche einer Region sind so klein, dass das Zonenbudget geometrisch nicht
  hineinpasst.** Die Engine füllt, was passt, und meldet die Diskrepanz einmalig statt in jedem
  Durchlauf.

---

## Requirements *(mandatory)*

### Functional Requirements — Mob-Arten (US1)

- **FR-001**: Das System MUSS Mob-Arten aus einer versionierten Konfigurationsdatei laden. Eine Art
  trägt mindestens: Kennung, Basis-Entity, Level, Attributwerte, Anzeigename-Schlüssel, Erfahrung
  und Coins.
- **FR-002**: Das System MUSS die Konfiguration beim Start gegen ein Schema prüfen und bei einem
  Fehler mit einer Meldung abbrechen, die Datei, Schlüssel und Grund nennt (Prinzip V, Fail-Fast).
- **FR-003**: Eine neue Mob-Art MUSS ohne Codeänderung entstehen können. Kein Bezeichner einer
  einzelnen Art darf im Code vorkommen.
- **FR-004**: Das System MUSS mehrere Arten auf demselben Vanilla-Entity zulassen und sie
  auseinanderhalten können.
- **FR-005**: Das System MUSS eine erzeugte Kreatur dauerhaft mit ihrer Art kennzeichnen, sodass die
  Art aus der Kreatur wieder gelesen werden kann, solange sie lebt.
- **FR-006**: Das System MUSS `MobStatProvider` so ersetzen, dass er nach der **Art** antwortet.
  Die Schnittstellenform bleibt unverändert; es wird keine zweite eingeführt.
- **FR-007**: Das System MUSS `MobXpProvider.xpFor` und `MobCoinProvider.coinsFor` ebenso ersetzen.
  Ein leeres Ergebnis bedeutet weiterhin „kein eigener Eintrag" und niemals Null.
- **FR-008**: Das System MUSS die Attributwerte einer Art über B04 setzen, nicht über eine eigene
  Berechnung. Kreaturen und Spieler benutzen dasselbe Attribut- und Kampfmodell (Prinzip III).
- **FR-009**: Eine Kreatur ohne Kennzeichnung MUSS die konfigurierten Standardwerte bekommen und
  darf keinen Fehler auslösen.
- **FR-010**: Das System MUSS den Anzeigenamen einer Art über einen Message-Schlüssel führen. Es gibt
  keine hartcodierten Spielertexte (Prinzip V).

### Functional Requirements — Spawn-Engine (US2)

- **FR-011**: Das System MUSS Kreaturen ausschließlich innerhalb der von `Zones.spawnAreasOf`
  gelieferten Bereiche erzeugen.
- **FR-012**: Das System MUSS je Zone konfigurieren, welche Arten in welchem Bereich vorkommen.
- **FR-013**: Das System MUSS ein Budget für gleichzeitig aktive Kreaturen **je Zone, je Chunk und
  je Spieler** führen. Jedes dieser Budgets ist eine harte Obergrenze und wird unter keiner
  Bedingung überschritten.
- **FR-014**: Das System MUSS die Spawn-Berechnung über mehrere Ticks verteilen und darf sie nicht
  in einem Tick bündeln.
- **FR-015**: Das System DARF in einer Zone ohne anwesende Spieler nichts erzeugen.
- **FR-016**: Das System MUSS einen unbekannten Zonenschlüssel und eine leere Bereichsliste als
  normalen Zustand behandeln — kein Fehler, keine Ausnahme, nichts erzeugt.
- **FR-017**: Das System MUSS eine Kreatur dem Budget ihrer Ursprungszone zurechnen, auch wenn sie
  sich später außerhalb bewegt.
- **FR-018**: Räumliche Abfragen der Engine MÜSSEN über einen räumlichen Index laufen, niemals über
  lineare Iteration aller Kandidaten (Prinzip II).
- **FR-018a**: Der Nachschub MUSS kontinuierlich sein: getötete Kreaturen werden laufend ersetzt,
  bis die Zieldichte wieder erreicht ist. Es gibt keinen Wellenzustand je Zone.
- **FR-018b**: Die Nachschubrate MUSS konfigurierbar sein und DARF das Budget aus FR-013 nicht
  überschreiten.

### Functional Requirements — Vanilla-Unterdrückung (US2b)

- **FR-018c**: Das System MUSS das natürliche Spawning von Vanilla-Kreaturen unterdrücken —
  unabhängig von Tageszeit, Lichtwert und Ort.
- **FR-018d**: Das System DARF absichtlich gesetzte Kreaturen nicht unterdrücken. Ein Spawn-Ei und
  ein Betreiber-Kommando funktionieren weiterhin; die so gesetzte Kreatur bekommt die Standardwerte
  aus FR-009.
- **FR-018e**: Das System DARF die eigenen Kreaturen nicht mitunterdrücken.
- **FR-018f**: Die Unterdrückung MUSS am Entstehen ansetzen und nicht am Aufräumen danach. Eine
  Kreatur, die erst erscheint und dann entfernt wird, hat bereits einen Tick gekostet und ist
  kurzzeitig sichtbar gewesen.

### Functional Requirements — Aufräumen (US3)

- **FR-019**: Das System MUSS Kreaturen entfernen, deren Zone keine Spieler mehr enthält, und zwar
  binnen einer konfigurierten Zeit.
- **FR-020**: Das System MUSS Kreaturen entfernen, die weiter als eine konfigurierte Reichweite von
  jedem Spieler entfernt sind. Entfernen, nicht schlafen legen.
- **FR-021**: Ein Entfernen DARF weder Erfahrung noch Coins noch einen Todesfall auslösen.
- **FR-022**: Das System DARF eine Kreatur nicht entfernen, solange sie im Kampf mit einem Spieler
  steht.
- **FR-023**: Das System MUSS beim Herunterfahren seine Kreaturen entfernen, sodass beim nächsten
  Start kein herrenloser Bestand steht.
- **FR-024**: Das Aufräumen DARF keine wiederkehrende Aufgabe je Kreatur benutzen (Prinzip II).

### Functional Requirements — Skalierung (US4)

- **FR-025**: Das System MUSS Dichte und Nachschubrate einer Zone an der Zahl der dort anwesenden
  Spieler ausrichten.
- **FR-026**: Die Skalierung DARF Attributwerte, Level oder Stärke einer Kreatur nicht verändern.
- **FR-027**: Die Skalierung MUSS am Budget aus FR-013 enden. Der skalierte Wert ist ein Ziel, das
  Budget die Grenze.
- **FR-028**: Sinkt die Spielerzahl, MUSS die Zieldichte sinken; der Überhang wird über das
  Aufräumen abgebaut und nicht sofort gelöscht.

### Functional Requirements — Boss (US5)

- **FR-029**: Das System MUSS je Region höchstens einen lebenden Boss zulassen.
- **FR-030**: Das System MUSS den Platz des Bosses konfigurierbar machen. B09 kennzeichnet keinen
  Bereich als Bossbereich; die Zuordnung gehört in diese Konfiguration.
- **FR-031**: Das System MUSS nach dem Tod eines Bosses einen konfigurierten Respawn-Timer führen
  und vor dessen Ablauf keinen zweiten erzeugen.
- **FR-032**: Der Respawn-Timer MUSS zeitstempelbasiert lazy ausgewertet werden, nicht über eine
  laufende Aufgabe (Prinzip II).
- **FR-033**: Ein Boss MUSS im Budget seiner Zone mitzählen.
- **FR-034**: Ein Boss MUSS beim Aufräumen wie jede andere Kreatur behandelt werden; sein
  Respawn-Timer bleibt davon unberührt.
- **FR-034a**: Ein Boss MUSS sich von einer gewöhnlichen Art ausschließlich durch seine
  Konfiguration unterscheiden — höhere Attributwerte, ein eigener Platz, ein Respawn-Timer. Er
  bekommt **keine** eigenen Fähigkeiten und **keine** Phasenwechsel. Beides gehört zum späteren
  Dungeon-Boss und setzt eine Instanzwelt voraus, die es nach ADR-006 noch nicht gibt.

### Functional Requirements — AI-Kosten (US6)

- **FR-035**: Das System MUSS die Zielsuche drosseln, sodass sie höchstens in einem konfigurierten
  Abstand stattfindet und nicht in jedem Tick.
- **FR-036**: Das System MUSS die Zielsuchreichweite begrenzen. Außerhalb davon verliert eine
  Kreatur ihr Ziel.
- **FR-037**: Eine Kreatur ohne Ziel in Reichweite DARF keine Zielsuche durchführen.
- **FR-038**: Der Block MUSS eine wiederholbare Messung seiner eigenen Rechenarbeit ohne Volllast
  vorlegen (Prinzip VII in der Fassung von ADR-031).

### Functional Requirements — Anschluss B08 (US7)

- **FR-039**: Solange ein Klon des Rogue steht, MÜSSEN Kreaturen in seiner Reichweite ihn als Ziel
  wählen.
- **FR-040**: Nach dem Ende des Klons MÜSSEN sie wieder den Spieler wählen.
- **FR-041**: Das Umschwenken MUSS derselben Drosselung aus FR-035 unterliegen.

### Functional Requirements — Rahmen

- **FR-042**: Kein Bukkit-Aufruf dieses Blocks außerhalb des Server-Ticks; alles Scheduling läuft
  über die projekteigene Abstraktion mit location- oder entity-gebundenen Aufrufen (Prinzip I).
- **FR-043**: Der Block DARF keinen Datenbankzugriff je Spielereignis verursachen (Prinzip II).
- **FR-044**: Eine Ausnahme im Spawn- oder Aufräumpfad DARF keinen Spieler in einen inkonsistenten
  Zustand versetzen; Fehler werden lokal begrenzt und protokolliert (Prinzip VI).
- **FR-045**: Der Block DARF kein Resource Pack und keine Client-Anforderung einführen (ADR-005).

---

### Key Entities

- **Mob-Art (`MobKind`)**: Eine Kennung, ein Vanilla-Basis-Entity, ein Level, Attributwerte, ein
  Anzeigename-Schlüssel, Erfahrung, Coins. Die Einheit, nach der die drei wartenden Schnittstellen
  fragen.
- **Horden-Definition je Zone**: Welche Arten in welchem Spawn-Bereich vorkommen, mit welchem
  Gewicht, und wo der Boss steht.
- **Budget**: Drei Obergrenzen — je Zone, je Chunk, je Spieler. Keine Zielwerte.
- **Bestand**: Welche Kreaturen dieser Block gerade in der Welt hält, welcher Zone und welcher Art
  sie gehören, und seit wann.
- **Boss-Zustand je Region**: Ob einer lebt, und wann der letzte gefallen ist.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Eine neue Mob-Art entsteht durch das Bearbeiten einer Konfigurationsdatei und einen
  Neustart — ohne eine Zeile Code.
- **SC-002**: Alle 48 Arten und 6 Bosse sind konfiguriert und werden beim Start ohne Beanstandung
  geladen.
- **SC-003**: Das Zonen-, Chunk- und Spielerbudget wird in keinem Testlauf überschritten, auch nicht
  beim gleichzeitigen Eintreffen mehrerer Spieler.
- **SC-004**: Nach dem Verlassen einer Region durch alle Spieler ist binnen der konfigurierten Zeit
  keine Kreatur dieser Region mehr in der Welt.
- **SC-005**: Die Zahl lebender Kreaturen einer Region steigt messbar mit der Zahl anwesender
  Spieler, während die Attributwerte einer einzelnen Art unverändert bleiben.
- **SC-006**: Je Region existiert zu keinem Zeitpunkt mehr als ein Boss, und nach seinem Tod
  vergeht mindestens die konfigurierte Zeit bis zum nächsten.
- **SC-007**: Eine wiederholbare Messung der Spawn- und Zielsuchkosten liegt vor und liefert über
  mehrere Läufe vergleichbare Werte. Der Nachweis unter Volllast (800 Kreaturen, 150 Spieler,
  p95 MSPT < 40 ms) bleibt verbindlich, gehört aber nach ADR-031 zu B15.
- **SC-008**: Die drei wartenden Schnittstellen aus B05, B06 und B08b werden von diesem Block
  bedient; keine von ihnen hat eine zweite Fassung bekommen.
- **SC-009**: Der Klon des Rogue zieht Kreaturen auf sich, und die entsprechende Zeile in der
  Roadmap ist geschlossen.
- **SC-010**: Über eine volle Nacht und in einer unbeleuchteten Höhle entsteht keine einzige
  Kreatur von selbst; jede lebende Kreatur der Welt lässt sich einer Zone dieses Blocks oder einem
  absichtlichen Setzen zuordnen.

---

## Assumptions

- **Die konkreten 48 Arten und 6 Bosse sind Inhalt, nicht Spezifikation.** Welche Vanilla-Entities
  als Basis dienen, wird in der Konfiguration entschieden und kann sich ändern, ohne dass diese
  Spec sich ändert. Verlangt wird hier nur, dass die Wahl konfigurierbar ist.
- **Zielwert für gleichzeitig aktive Kreaturen**: Als Ausgangspunkt dient die Zahl aus dem
  M4-Nachweis — 800 serverweit. Auf sechs Regionen verteilt sind das rund 130 je Zone. Beide Werte
  sind konfigurierbar; die hier genannten sind Startwerte, keine Zusagen.
- **Wieviel Coins eine Art abwirft**, ist Inhalt und steht in der Konfiguration derselben Art. Die
  Mechanik dafür steht seit B08b.
- **Elite- und Champion-Varianten sind nicht Teil dieses Blocks** (siehe Clarifications).
- **Der Dungeon-Boss ist nicht Teil dieses Blocks.** Er braucht Instanzen und eigene Fähigkeiten,
  beides steht noch aus. Der Boss hier ist eine Mob-Art mit höheren Zahlen.
- **Nahrung, Wolle und Leder aus natürlichem Tier-Spawning entfallen** mit der Unterdrückung. Wie
  ein Spieler daran kommt, ist eine Inhaltsfrage für B11 und B16.
- **Loot über Coins hinaus gehört zu B11.** Dieser Block liefert die Entity-Technik, die der
  NPC-Händler aus B11 mitbenutzt; der Händler selbst gehört dorthin.
- **Die Aufräumzeit und die Reichweiten sind konfigurierbar** und werden im Plan mit Startwerten
  belegt.
- **Der Konfigurations-Reload folgt dem Muster der vorhandenen Blöcke**: Neues gilt für Neues,
  Laufendes behält, womit es gestartet ist.

---

## Offene Punkte für `/plan`

Die drei Zuschnittsfragen sind am 2026-08-24 beantwortet (siehe Clarifications). Was für den Plan
offenbleibt, sind Zahlen und Wege, keine Entscheidungen über den Umfang:

1. **Wie die Vanilla-Unterdrückung technisch ansetzt**, sodass sie am Entstehen greift und nicht am
   Aufräumen danach (FR-018f). Es gibt mehrere Wege, und sie unterscheiden sich in den Kosten je
   verhindertem Spawn.
2. **Wie eine Kreatur ihre Art trägt** (FR-005), sodass sie über einen Chunk-Unload hinweg lesbar
   bleibt — und ob sie das überhaupt muss, wenn FR-019 sie ohnehin entfernt, sobald niemand da ist.
3. **Wie das Chunk-Budget geführt wird**, ohne je Chunk eine Zählung mitzuschleppen.
4. **Startwerte**: Zonenbudget, Chunk- und Spielerobergrenze, Aufräumzeit, Aufräumreichweite,
   Zielsuchabstand, Zielsuchreichweite, Nachschubrate, Boss-Respawn-Timer.
5. **Wie die Messung aus FR-038 aussieht**, sodass sie ohne Volllast wiederholbar ist.

---

## Dependencies

**Benötigt (alle vorhanden):**

- **B04 Stat-Engine** — Attributwerte für Kreaturen, dasselbe Modell wie für Spieler.
- **B05 Kampf-Pipeline** — `MobStatProvider`, den dieser Block ersetzt.
- **B06 Progression** — `MobXpProvider`, den dieser Block ersetzt.
- **B08 Fähigkeiten** — der Klon des Rogue (US7).
- **B08b Währung** — `MobCoinProvider`, den dieser Block ersetzt.
- **B09 Zonen** — `Zones.spawnAreasOf`, sechs Regionen mit je zwei Bereichen.
- **B01 Scheduler** — location- und entity-gebundene Einmalaufgaben (ADR-007, ADR-024).

**Wird benötigt von:**

- **B11 Items & Loot** — Beute aus Kreaturen; die Entity-Technik des NPC-Händlers.
- **B12 Statistiken** — Mob-Kills und Bosskills als erfasste Metriken.
- **B15 Performance** — der Lasttest, der den Zielwert dieses Blocks unter Volllast nachweist.
