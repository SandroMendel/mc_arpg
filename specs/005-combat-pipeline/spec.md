# Feature Specification: B05 · Kampf- & Schadens-Pipeline

**Feature Branch**: `005-combat-pipeline`

**Created**: 2026-08-20

**Status**: Draft

**Input**: Blocksteckbrief `blocks/B05-combat-pipeline.md` — ersetzt das Vanilla-Kampfsystem
vollständig durch ein eigenes, das auf den acht Attributen aus B04 aufbaut. Hängt ab von B04, wird
benötigt von B08, B10 und B12. **Performancekritisch**: der am häufigsten durchlaufene Codepfad des
gesamten Plugins. Verbindlich: ADR-003 (eigenes HP-System, Umgang mit jeder Vanilla-Schadensquelle),
ADR-008 (keine Sekundärwerte), ADR-013 (was B04 bereits erledigt).

## Clarifications

### Session 2026-08-20

- Q: Gibt es Sekundärmechaniken (Krit, Ausweichen, Blocken, Resistenztypen)? → A: Nein. B05 rechnet ausschließlich mit den acht Attributen aus B04. Damit entfällt auch die Frage, ob Krit vor oder nach der Verteidigung greift.
- Q: Wer bekommt Loot und XP bei mehreren Angreifern? → A: XP anteilig nach Schadensanteil an alle Beteiligten, Loot an den höchsten Beitrag. XP lässt sich teilen, ein Item nicht.
- Q: Ist PvP aktiv? → A: Aus. Die Verzweigung „Spieler greift Spieler an" existiert aber an genau einer Entscheidungsstelle, die B09 später mit einer Regel je Zone füllt.
- Q: Wie wirkt Angriffsgeschwindigkeit? → A: Eigenes Cooldown-Modell je Angriff, zeitstempelbasiert und lazy ausgewertet. Zu frühe Schläge werden verworfen, nicht abgeschwächt. Der Vanilla-Waffencooldown wird abgeschaltet.
- Q: Was passiert einem Spieler beim Tod? → A: Ausrüstungsschaden. B05 erkennt und meldet den Tod mit Verursacher; was mit der Ausrüstung geschieht, entscheidet B11 — B05 greift nicht auf Haltbarkeiten zu, die noch niemand definiert hat.
- Q: Sollen Schadenszahlen angezeigt werden? → A: Ja, aber zusammengefasst. Die Bündelung mehrerer Treffer zu einem Ereignis liegt in B05, weil sie Schadenslogik ist; das Zeichnen liegt in B13. B05 erzeugt selbst keine Text-Displays.
- Q: Wie wird mit den Vanilla-Schadensquellen umgegangen? → A: Umgebungsschaden wird auf eigenen Schaden abgebildet (Fall, Feuer, Lava, Void, Ertrinken, Explosion, Kaktus, Ersticken, Blitz, Magma) — Void bleibt tödlich. Statuseffekte werden abgeschaltet (Verhungern, Wither, Poison, Instant Damage/Health, Absorption).
- Q: Fester Betrag oder Anteil des maximalen Lebens beim Umgebungsschaden? → A: Fester, konfigurierbarer Betrag. Umgebungsgefahren sollen für Anfänger ernst und für ausgerüstete Spieler belanglos werden — ein prozentualer Schaden bliebe über die gesamte Progression gleich gefährlich und wäre damit das Gegenteil. Verteidigung greift dabei nicht, damit der Verlauf nur einen Wirkmechanismus hat.

### Session 2026-08-20 (Clarify)

- Q: Was passiert beim Tod mit dem Inventar? → A: Nichts fällt. Vanilla lässt standardmäßig alles fallen; damit wäre die gewählte Todesstrafe „Ausrüstungsschaden" bedeutungslos, weil daneben der Verlust des gesamten Inventars steht. Ausrüstungsschaden soll die Strafe sein, also gibt es nur diese eine.
- Q: Wie gibt eine Fähigkeit ihren Schaden an die Pipeline weiter? → A: Als Multiplikator auf `magicDamage` („180 % des magischen Schadens"). Damit skaliert jede Fähigkeit automatisch mit Ausrüstung und Level, B08 muss keine Attributwerte selbst lesen, und `magicDamage` bleibt ein Attribut, das etwas bewirkt.
- Q: Behandelt B05 auch Projektile? → A: Ja, als eigene Schadensquelle. Ohne das entstünde eine Lücke: FR-016 setzt jeden Vanilla-Schaden auf null, also auch Pfeilschaden — ein Bogen wäre ab Tag eins sichtbar wirkungslos. Das Projektil hält den Schnappschuss des Schützen vom Abschuss.
- Q: Wie stirbt ein Spieler sichtbar? → A: Der Vanilla-Todesbildschirm bleibt. B05 füllt beim Wiedererscheinen Leben und Mana auf; ein eigener Ablauf wird nicht gebaut.
- Q: Können Mobs einander Schaden zufügen? → A: Nein. Schaden zwischen zwei Trägern ohne Spielerbezug wird verworfen — dieselbe Entscheidungsstelle, an der auch die PvP-Regel sitzt.

### Session 2026-08-20 (Clarify, zweite Runde)

- Q: Woher bekommen Mobs ihre Attribute, solange B10 fehlt? → A: B05 legt beim Erscheinen eines feindlichen Mobs einen Stat-Träger mit Werten aus der eigenen Konfiguration an, grob nach Mob-Art gestaffelt. Ohne das wäre das gesamte Kampfsystem wirkungslos: FR-018 lässt Wesen ohne Träger unangetastet, und heute gibt niemand Mobs einen. B10 ersetzt die Werte später über dieselbe Schnittstelle.
- Q: Führt B05 einen Kampfzustand? → A: Ja. Ein Zeitstempel je Träger, lazy ausgewertet wie das Angriffszeitfenster. B08 braucht ihn bereits (Mana-Regeneration im Kampf reduziert), B12 und B13 später ebenfalls — und nur B05 sieht jeden Treffer.
- Q: Was passiert beim Mob-Tod mit Vanilla-XP-Kugeln und Vanilla-Drops? → A: Beides wird unterdrückt. Sonst liefen zwei Fortschrittssysteme sichtbar nebeneinander und Vanilla-Drops lägen quer zu den Loot-Tabellen aus B11.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ein Schlag trifft, und der Schaden stimmt (Priority: P1)

Als Spieler schlage ich auf einen Gegner ein. Der Schaden, den er nimmt, ergibt sich aus meinem
physischen Schaden und seiner Verteidigung — nachvollziehbar, jedes Mal gleich, ohne verstecktes
Vanilla-Verhalten dazwischen.

**Why this priority**: Ohne die Schadensberechnung gibt es kein Kampfsystem, und ohne Kampfsystem
haben B08 (Fähigkeiten), B10 (Mobs) und B12 (Statistiken) keine Grundlage. Dies ist der minimal
lauffähige Kern des Blocks.

**Independent Test**: Zwei Stat-Träger mit bekannten Werten aufsetzen, einen Treffer auslösen und
prüfen, dass der abgezogene Lebensbetrag exakt der dokumentierten Beispielrechnung entspricht —
ohne Server, ohne Datenbank.

**Acceptance Scenarios**:

1. **Given** ein Angreifer mit 50 physischem Schaden und ein Ziel mit 100 Verteidigung, **When** ein
   Treffer erfolgt, **Then** verliert das Ziel exakt 25 Leben.
2. **Given** ein Ziel mit 300 Verteidigung, **When** ein Treffer mit 100 Rohschaden erfolgt,
   **Then** verliert es exakt 25 Leben — 75 % Minderung.
3. **Given** ein Ziel mit 0 Verteidigung, **When** ein Treffer erfolgt, **Then** verliert es exakt
   den Rohschaden.
4. **Given** ein Angriff mit magischem Schaden, **When** er berechnet wird, **Then** wird
   `magicDamage` als Basis verwendet und nicht `physicalDamage`.
5. **Given** dieselben Eingaben, **When** die Berechnung mehrfach ausgeführt wird, **Then** liefert
   sie jedes Mal bitgleich dasselbe Ergebnis — es gibt keinen Zufall im Schaden.
6. **Given** ein laufender Angriff, **When** sich die Werte des Angreifers mitten im Vorgang ändern,
   **Then** rechnet der Angriff mit den Werten seines Auslösezeitpunkts zu Ende.

---

### User Story 2 - Kein Vanilla-Schaden kommt jemals durch (Priority: P1)

Als Spieler nehme ich niemals Schaden, den nicht die Engine berechnet hat. Falle ich, brenne ich
oder ertrinke ich, wird das auf meinen eigenen Lebenswert umgerechnet — die Herzleiste zeigt danach
weiterhin meinen tatsächlichen Anteil.

**Why this priority**: Gleichrangig mit User Story 1. ADR-003 verlangt es ausdrücklich, und eine
einzige durchgelassene Vanilla-Quelle macht das gesamte HP-System unglaubwürdig: ein Spieler mit
2000 Leben stirbt sonst an einem Sturz aus fünf Blöcken.

**Independent Test**: Für jede Vanilla-Schadensquelle aus der Tabelle ein Ereignis auslösen und
prüfen, dass entweder gar nichts passiert (abgeschaltet) oder exakt der vorgesehene eigene Schaden
angewandt wird — und dass der Vanilla-Lebenswert dabei nie direkt verändert wird.

**Acceptance Scenarios**:

1. **Given** ein Spieler mit 1000 von 2000 Leben, **When** er fünf Blöcke tief fällt, **Then** wird
   Fallschaden als eigener Schaden angewandt und die Herzleiste zeigt weiterhin den korrekten
   Anteil.
2. **Given** ein beliebiger Vanilla-Schaden, **When** er eintrifft, **Then** wird der
   Vanilla-Schadenswert auf null gesetzt, bevor die Engine ihren eigenen anwendet.
3. **Given** ein neutralisiertes Vanilla-Ereignis, **When** die Engine Schaden anwendet, **Then**
   wird die Trefferanimation ausdrücklich ausgelöst — ein auf null gesetztes Ereignis zeigt von
   sich aus keine.
4. **Given** ein Spieler fällt in den Void, **When** das Ereignis eintrifft, **Then** stirbt er,
   unabhängig von seinem Lebenswert.
5. **Given** ein Statuseffekt aus der Abschaltliste (Verhungern, Wither, Poison, Instant Damage,
   Instant Health, Absorption), **When** er eintrifft, **Then** hat er keinerlei Wirkung.
6. **Given** ein Custom-Mob, **When** er Umgebungsschaden nimmt, **Then** gelten für ihn dieselben
   Regeln wie für einen Spieler.
7. **Given** ein Administrator führt `/kill` aus, **When** das Ereignis eintrifft, **Then** stirbt
   das Ziel sofort — ein Werkzeug muss verlässlich bleiben.

---

### User Story 3 - Der Server hält den Dauerkampf aus (Priority: P1)

Als Betreiber erwarte ich, dass 150 Spieler gegen 800 Mobs kämpfen können, ohne dass die Tickrate
einbricht. Der Kampfpfad ist der meistdurchlaufene Code des Plugins — er muss der sparsamste sein.

**Why this priority**: Erfolgskriterium des Projekts (M4-Nachweis). Ein korrektes Kampfsystem, das
den Server auf 10 TPS zieht, ist kein Kampfsystem.

**Independent Test**: Eine hohe Zahl an Treffern in kurzer Folge auslösen und nachweisen, dass je
Treffer keine vermeidbare Objekterzeugung stattfindet und die Gesamtzeit innerhalb des Budgets
bleibt.

**Acceptance Scenarios**:

1. **Given** 10 000 Treffer in Folge, **When** sie verarbeitet werden, **Then** entsteht kein
   Objekt je Treffer, das sich hätte wiederverwenden lassen.
2. **Given** 150 Angreifer gegen 800 Ziele im Dauerkampf, **When** gemessen wird, **Then** bleibt
   die 95.-Perzentil-Tickzeit unter 40 Millisekunden.
3. **Given** ein Kampf läuft, **When** kein Treffer stattfindet, **Then** verbraucht die Pipeline
   keine Rechenzeit — es gibt keine wiederkehrende Aufgabe je Spieler oder je Mob.
4. **Given** ein Ziel wird von vielen Angreifern getroffen, **When** die Beitragsliste geführt wird,
   **Then** wächst sie nicht unbegrenzt, sondern ist in Anzahl und Alter begrenzt.

---

### User Story 4 - Angriffsgeschwindigkeit begrenzt die Schlagfolge (Priority: P2)

Als Spieler kann ich nicht beliebig schnell zuschlagen. Mein Attribut `attackSpeed` bestimmt, wie
oft ein Schlag zählt; zu frühe Schläge werden verworfen. Schnelleres Klicken bringt nichts.

**Why this priority**: Ohne Begrenzung ist `attackSpeed` als Attribut wertlos und Klick-Spam die
dominante Strategie. Erst nach der Schadensberechnung sinnvoll umsetzbar.

**Independent Test**: Mit einem festen Zeitgeber mehrere Schläge in kurzer Folge auslösen und
prüfen, dass genau die zugelassenen zählen und die übrigen folgenlos verworfen werden.

**Acceptance Scenarios**:

1. **Given** ein Spieler mit einer Angriffsgeschwindigkeit von 4 Angriffen je Sekunde, **When** er
   zehnmal innerhalb einer Sekunde zuschlägt, **Then** zählen vier Schläge und sechs werden
   verworfen.
2. **Given** ein verworfener Schlag, **When** er verworfen wird, **Then** entsteht kein Schaden,
   keine Trefferanimation und kein Beitrag zur Schadensattribution.
3. **Given** ein Spieler wartet lange genug, **When** er wieder zuschlägt, **Then** zählt der
   Schlag.
4. **Given** die Angriffsgeschwindigkeit ändert sich, **When** der nächste Schlag erfolgt, **Then**
   gilt sofort das neue Zeitfenster, ohne dass etwas neu geplant werden muss.
5. **Given** ein Spieler ist verbunden, **When** er nicht kämpft, **Then** entsteht für sein
   Zeitfenster keine wiederkehrende Aufgabe — es wird nur beim Zuschlagen ausgewertet.
6. **Given** der Vanilla-Waffencooldown, **When** ein Spieler zuschlägt, **Then** hat er keinen
   Einfluss auf den Schaden.

---

### User Story 5 - Ein Tod hat einen Verursacher und Folgen (Priority: P2)

Als Spieler sterbe ich, wenn mein Leben null erreicht. Als Angreifer bekomme ich den Abschuss
zugeschrieben. Beides wird gemeldet, damit Fortschritt, Beute und Statistiken darauf aufbauen
können.

**Why this priority**: Ohne Todesbehandlung fällt kein Mob je um und kein Fortschritt entsteht.
Setzt die Schadensberechnung voraus.

**Independent Test**: Ein Ziel bis auf null Leben bringen und prüfen, dass genau ein Todesereignis
mit dem richtigen Verursacher entsteht und dass weiterer Schaden auf ein bereits totes Ziel
folgenlos bleibt.

**Acceptance Scenarios**:

1. **Given** ein Ziel mit wenig Leben, **When** ein Treffer es auf null bringt, **Then** stirbt es
   und ein Todesereignis nennt den Verursacher.
2. **Given** ein bereits totes Ziel, **When** weiterer Schaden eintrifft, **Then** passiert nichts
   und es entsteht kein zweites Todesereignis.
3. **Given** ein Spieler stirbt, **When** das Todesereignis veröffentlicht wird, **Then** enthält es
   genug Angaben, dass B11 daraus über den Ausrüstungsschaden entscheiden kann — B05 fasst die
   Ausrüstung selbst nicht an.
4. **Given** ein Spieler stirbt, **When** er wieder erscheint, **Then** hat er sein volles Leben und
   sein volles Mana.
5. **Given** ein Mob stirbt, **When** das Todesereignis veröffentlicht wird, **Then** enthält es die
   vollständige Schadensaufteilung, damit B06 XP und B10/B11 Beute daraus ableiten können.
6. **Given** ein Ziel stirbt an Umgebungsschaden ohne Angreifer, **When** das Todesereignis
   entsteht, **Then** ist der Verursacher leer und das wird als gültiger Fall behandelt.

---

### User Story 6 - Bei einer Horde bekommt jeder seinen Anteil (Priority: P2)

Als Spieler, der gemeinsam mit anderen eine Horde bekämpft, bekomme ich XP entsprechend meinem
Anteil am Schaden. Die Beute geht an den, der am meisten beigetragen hat — nicht an den, der
zufällig zuletzt zugeschlagen hat.

**Why this priority**: Kern des Horden-Contents aus B10. Ohne diese Regel ist gemeinsames Spielen
an einer Horde unattraktiv oder wird zum Wettlauf um den letzten Schlag.

**Independent Test**: Mehrere Angreifer mit unterschiedlichen Schadensanteilen auf ein Ziel
loslassen, es töten und die berechneten Anteile gegen die erwartete Aufteilung prüfen.

**Acceptance Scenarios**:

1. **Given** drei Angreifer mit 60 %, 30 % und 10 % des Schadens, **When** das Ziel stirbt,
   **Then** weist die Aufteilung genau diese Anteile aus.
2. **Given** dieselbe Situation, **When** die Beute zugeteilt wird, **Then** geht sie an den
   Angreifer mit 60 %.
3. **Given** ein Angreifer, dessen letzter Beitrag zu lange her ist, **When** das Ziel stirbt,
   **Then** ist er nicht mehr beteiligt.
4. **Given** sehr viele Angreifer auf ein Ziel, **When** die Beitragsliste geführt wird, **Then**
   bleibt sie auf eine feste Höchstzahl begrenzt; der kleinste Beitrag weicht.
5. **Given** ein Ziel stirbt ohne jeden Spielerbeitrag, **When** die Aufteilung ermittelt wird,
   **Then** ist sie leer und kein Spieler bekommt etwas.
6. **Given** ein Ziel wird nicht getötet, **When** es sich lange genug regeneriert oder unbehelligt
   bleibt, **Then** verfällt seine Beitragsliste vollständig.

---

### User Story 7 - Treffer sind sichtbar, ohne den Server zu belasten (Priority: P3)

Als Spieler sehe ich, dass ein Treffer gesessen hat, und wie viel Schaden er gemacht hat. Bei einem
Dauerfeuer auf dieselbe Kreatur sehe ich eine zusammengefasste Zahl statt eines Zahlenregens.

**Why this priority**: Rückmeldung ist wichtig für das Spielgefühl, aber nachrangig gegenüber
Korrektheit und Tickrate. Die eigentliche Darstellung gehört ohnehin zu B13.

**Independent Test**: Viele Treffer in kurzer Folge auf dasselbe Ziel auslösen und prüfen, dass die
Zahl der veröffentlichten Anzeigeereignisse deutlich unter der Zahl der Treffer liegt und die
summierten Beträge stimmen.

**Acceptance Scenarios**:

1. **Given** ein Treffer, **When** er angewandt wird, **Then** wird eine Trefferanimation ausgelöst
   und ein Rückstoß angewandt.
2. **Given** zwanzig Treffer desselben Angreifers auf dasselbe Ziel innerhalb eines kurzen Fensters,
   **When** die Anzeige ermittelt wird, **Then** entsteht ein Ereignis mit der Summe, nicht zwanzig
   Ereignisse.
3. **Given** ein zusammengefasstes Anzeigeereignis, **When** es veröffentlicht wird, **Then**
   erzeugt B05 dabei selbst keine Anzeigeobjekte in der Welt.
4. **Given** ein Treffer, der null Schaden macht, **When** die Anzeige ermittelt wird, **Then**
   entsteht kein Anzeigeereignis.

---

### User Story 8 - Spieler können einander nicht verletzen (Priority: P3)

Als Spieler kann ich einen anderen Spieler nicht angreifen. Meine Schläge und meine Fähigkeiten
treffen ihn nicht.

**Why this priority**: Eine einzelne Entscheidungsstelle, aber sie muss von Anfang an an der
richtigen Stelle sitzen — nachträglich eingezogen wäre sie über die ganze Pipeline verstreut.

**Independent Test**: Einen Spieler auf einen anderen schlagen lassen und prüfen, dass kein Schaden,
keine Animation und kein Beitrag entsteht.

**Acceptance Scenarios**:

1. **Given** zwei Spieler, **When** einer den anderen angreift, **Then** entsteht kein Schaden.
2. **Given** derselbe Fall, **When** der Angriff abgewiesen wird, **Then** entsteht auch kein
   Beitrag zur Schadensattribution und keine Trefferanimation.
3. **Given** ein Spieler greift einen Mob an, **When** die Entscheidung fällt, **Then** ist sie
   erlaubt — die Regel betrifft ausschließlich Spieler gegen Spieler.
4. **Given** die Regel wird später zonenabhängig, **When** ein späterer Block sie ersetzt, **Then**
   geschieht das an genau einer Stelle, ohne die übrige Pipeline zu berühren.

---

### Edge Cases

- **Angreifer verschwindet mitten im Vorgang**: Ein Projektil, dessen Schütze sich abmeldet, wirkt
  weiter mit dem Schnappschuss vom Abschuss; der Beitrag wird dem abgemeldeten Spieler zugeschrieben
  und verfällt mit dem Fenster.
- **Ziel verschwindet vor der Anwendung**: Der Vorgang endet folgenlos, ohne Ausnahme.
- **Selbstschaden**: Ein Träger, der sich selbst trifft (eigene Explosion), nimmt Schaden, erzeugt
  aber keinen Beitrag zur Attribution — sonst bekäme er Beute für seinen eigenen Tod.
- **Explosion eines Mobs trifft andere Mobs**: Der Schaden auf die anderen Mobs wird verworfen
  (FR-042a); der Schaden auf getroffene Spieler wird ganz normal angewandt.
- **Projektil trifft, nachdem der Schütze gestorben ist**: Der Treffer wirkt mit dem Schnappschuss
  vom Abschuss; der Beitrag wird dem toten Schützen zugeschrieben und verfällt mit dem Fenster.
- **Spieler stirbt mit vollem Inventar**: Es fällt nichts, auch nicht im Void oder in Lava — die
  Regel gilt unabhängig von der Todesursache.
- **Schaden auf ein Ziel mit null Leben**: folgenlos, kein zweites Todesereignis.
- **Negativer oder nicht endlicher Rohschaden**: wird abgelehnt und protokolliert, nicht in eine
  Heilung umgedeutet.
- **Sitzung nicht bereit**: Ein Spieler ohne freigegebene Sitzung kann weder Schaden nehmen noch
  austeilen; der Vorgang wird abgewiesen statt mit Standardwerten gerechnet.
- **Zwei tödliche Treffer im selben Tick**: Nur der erste erzeugt den Tod; der zweite läuft ins
  Leere.
- **Vanilla-Ereignis auf ein Wesen ohne Stat-Träger** (ein gewöhnliches Tier): B05 lässt es
  unangetastet — es gehört nicht zum eigenen System.
- **Sehr hohe Verteidigung**: nähert sich 100 % Minderung, erreicht sie nie; ein Treffer macht
  immer mehr als null Schaden.
- **Beitragsfenster läuft ab, während der Kampf noch läuft**: Ein Angreifer, der lange pausiert und
  dann den Todesstoß setzt, ist wieder beteiligt — mit dem Beitrag ab seiner Rückkehr.
- **Rückstoß auf ein unbewegliches Ziel**: wird angewandt, ohne dass ein Fehler entsteht.

## Requirements *(mandatory)*

### Functional Requirements

#### Schadensberechnung

- **FR-001**: Das System MUSS Schaden als reine Funktion berechnen: aus einem Schadenstyp
  (physisch oder magisch), dem Wert des Angreifers und der Verteidigung des Ziels.
- **FR-002**: Das System MUSS für physischen Schaden `physicalDamage` und für magischen Schaden
  `magicDamage` als Basis verwenden.
- **FR-002a**: Das System MUSS einen Angriff erlauben, seinen Rohschaden als **Anteil** des
  Basisattributs anzugeben (ein Faktor von 1,8 bedeutet 180 % des magischen Schadens). Ein
  Nahkampfschlag verwendet den Faktor 1,0. Damit skaliert jede spätere Fähigkeit (B08) mit
  Ausrüstung und Level, ohne selbst Attributwerte zu lesen.
- **FR-003**: Das System MUSS die Verteidigung über das Divisor-Modell aus B04 anwenden; bei
  Verteidigung 300 beträgt die Minderung exakt 75 %.
- **FR-004**: Das System MUSS bei gleicher Eingabe stets dasselbe Ergebnis liefern. Es gibt keinen
  Zufall im Schaden — kein Krit, kein Ausweichen, kein Blocken, keine Resistenztypen.
- **FR-005**: Das System MUSS den Wertestand des Angreifers **einmal** zu Beginn eines Vorgangs
  festhalten und bis zu dessen Ende damit rechnen.
- **FR-006**: Das System MUSS nicht endliche oder negative Rohschadenswerte ablehnen und
  protokollieren, statt sie als Heilung zu deuten.

#### Pipeline-Stufen

- **FR-007**: Das System MUSS jeden Schadensvorgang in klar getrennten Stufen abarbeiten: Quelle →
  Rohschaden → Modifikatoren → Verteidigung → Anwendung → Nachwirkung.
- **FR-008**: Das System MUSS an jeder Stufe einen benannten Eingriffspunkt bereitstellen, über den
  spätere Blöcke (B08 Fähigkeiten, B11 Item-Effekte) eingreifen, ohne dass die Pipeline dafür
  Sonderfälle bekommt.
- **FR-009**: Das System MUSS einen Vorgang an jeder Stufe abbrechen können; ein abgebrochener
  Vorgang erzeugt weder Schaden noch Animation noch Beitrag.
- **FR-010**: Eine Ausnahme in einem Eingriffspunkt MUSS auf diesen Vorgang begrenzt bleiben und
  darf weder den Tick noch andere Kämpfe beeinträchtigen.

#### Vanilla-Schaden

- **FR-011**: Das System MUSS jede Vanilla-Schadensquelle einzeln behandeln, nach einer
  dokumentierten Tabelle mit genau zwei möglichen Behandlungen: abgeschaltet oder auf eigenen
  Schaden abgebildet.
- **FR-012**: Das System MUSS folgende Quellen auf eigenen Schaden abbilden: Fall, Feuer, Lava,
  Ertrinken, Explosion, Kaktus, Ersticken, Blitz, Magma-Block.
- **FR-012a**: Umgebungsschaden MUSS als **fester, konfigurierbarer Betrag** angewandt werden, nicht
  als Anteil des maximalen Lebens. Damit wird eine Umgebungsgefahr für Anfänger ernst und für
  ausgerüstete Spieler belanglos — das ist die beabsichtigte Wirkung, kein Nebeneffekt.
- **FR-012b**: Die Verteidigung des Ziels DARF bei Umgebungsschaden NICHT angewandt werden; sie
  gilt ausschließlich für physischen und magischen Schaden.
- **FR-012c**: Fallschaden MUSS mit der Fallhöhe wachsen; die Umrechnung von Höhe auf Schaden ist
  konfigurierbar.
- **FR-013**: Das System MUSS folgende Quellen wirkungslos machen: Verhungern, Wither, Poison,
  Instant Damage, Instant Health, Absorption.
- **FR-014**: Das System MUSS den Void tödlich halten, unabhängig vom Lebenswert des Ziels.
- **FR-015**: Das System MUSS `/kill` sofort tödlich halten — ein Administrationswerkzeug muss
  verlässlich bleiben.
- **FR-016**: Das System MUSS den Vanilla-Schadenswert jedes abgefangenen Ereignisses auf null
  setzen, bevor es eigenen Schaden anwendet.
- **FR-017**: Das System MUSS die Trefferanimation ausdrücklich auslösen, weil ein auf null
  gesetztes Vanilla-Ereignis von sich aus keine zeigt.
- **FR-018**: Das System MUSS Wesen ohne eigenen Stat-Träger unangetastet lassen; sie gehören nicht
  zum eigenen Kampfsystem.
- **FR-019**: Die Regeln MÜSSEN für Spieler und Custom-Mobs gleichermaßen gelten.

#### Mobs mit Werten versorgen (Übergangsregelung bis B10)

- **FR-019a**: Das System MUSS jedem feindlichen Wesen beim Erscheinen einen Stat-Träger geben.
  Ohne das wirkt die gesamte Pipeline auf nichts außer Spieler — FR-018 lässt Wesen ohne Träger
  unangetastet, und heute vergibt kein Block welche.
- **FR-019b**: Die Werte MÜSSEN aus der Konfiguration stammen, nach Mob-Art gestaffelt, mit einem
  Standardsatz für nicht eigens genannte Arten (Prinzip V).
- **FR-019c**: Das System MUSS die Vergabe hinter einer Schnittstelle kapseln, die B10 später
  übernimmt, ohne dass die Pipeline angefasst wird. B05 liefert Zahlen zur Überbrückung, keine
  Mob-Definitionen.
- **FR-019d**: Das System MUSS den Stat-Träger eines Wesens freigeben, wenn es stirbt oder entladen
  wird; bei 800 gleichzeitigen Wesen darf kein Träger überleben, dessen Wesen fort ist.
- **FR-019e**: Das System MUSS friedliche Wesen ohne Träger lassen — ein Schaf gehört nicht zum
  Kampfsystem.

#### Angriffsgeschwindigkeit

- **FR-020**: Das System MUSS aus dem Attribut `attackSpeed` ein Mindestzeitfenster zwischen zwei
  zählenden Angriffen ableiten.
- **FR-021**: Das System MUSS einen Angriff innerhalb des Zeitfensters **verwerfen** — nicht
  abschwächen. Ein verworfener Angriff erzeugt weder Schaden noch Animation noch Beitrag.
- **FR-022**: Das System MUSS das Zeitfenster zeitstempelbasiert und erst bei einem Angriff
  auswerten; es DARF KEINE wiederkehrende Aufgabe je Spieler oder je Mob anlegen.
- **FR-023**: Eine Änderung der Angriffsgeschwindigkeit MUSS ohne Neuplanung sofort für den
  nächsten Angriff gelten.
- **FR-024**: Das System MUSS den Vanilla-Waffencooldown wirkungslos machen, damit
  Angriffsgeschwindigkeit nur aus dem Attribut kommt.

#### Tod

- **FR-024a**: Das System MUSS Schaden aus Projektilen — Pfeilen, Wurfgeschossen und späteren
  Fähigkeitsprojektilen — als eigene Schadensquelle behandeln. Ohne das wäre ein Bogen wirkungslos,
  weil FR-016 den Vanilla-Pfeilschaden neutralisiert.
- **FR-024b**: Ein Projektil MUSS mit dem Wertestand rechnen, den sein Schütze beim **Abschuss**
  hatte, nicht beim Einschlag; Attribution und Angriffszeitfenster gelten wie im Nahkampf.
- **FR-025**: Das System MUSS ein Ziel als tot behandeln, sobald sein Leben null erreicht.
- **FR-026**: Das System MUSS je Tod genau **ein** Todesereignis veröffentlichen; weiterer Schaden
  auf ein totes Ziel bleibt folgenlos.
- **FR-027**: Das Todesereignis MUSS den Verursacher nennen, sofern es einen gibt; ein Tod durch
  Umgebungsschaden ohne Angreifer ist ein gültiger Fall mit leerem Verursacher.
- **FR-028**: Das Todesereignis MUSS die vollständige Schadensaufteilung tragen, damit B06 daraus XP
  und B10/B11 daraus Beute ableiten können.
- **FR-029**: Das System MUSS einen wiedererschienenen Spieler mit vollem Leben und vollem Mana
  ausstatten.
- **FR-029a**: Das System MUSS den Vanilla-Todesablauf beibehalten — Todesbildschirm und
  Wiedererscheinen auf Bestätigung des Spielers. Ein eigener Ablauf wird nicht gebaut.
- **FR-029b**: Das System MUSS verhindern, dass ein Spieler beim Tod sein Inventar verliert. Sonst
  stünde neben der festgelegten Strafe (Ausrüstungsschaden durch B11) eine zweite, ungleich
  härtere, die sie vollständig verdeckt.
- **FR-030**: Das System DARF NICHT auf Ausrüstung, Haltbarkeiten oder Itemwerte zugreifen. Die
  Todesstrafe „Ausrüstungsschaden" wird von B11 auf Grundlage des Todesereignisses umgesetzt.
- **FR-030a**: Das System MUSS beim Tod eines Wesens die Vanilla-Erfahrungskugeln unterdrücken.
  Fortschritt kommt aus B06; zwei sichtbare Erfahrungssysteme nebeneinander sind eines zu viel.
- **FR-030b**: Das System MUSS beim Tod eines Wesens die Vanilla-Beute unterdrücken. Loot-Tabellen
  gehören B11; bis dahin fällt nichts, statt dass Vanilla-Drops quer dazu liegen.

#### Kampfzustand

- **FR-030c**: Das System MUSS je Träger festhalten, wann er zuletzt Schaden gegeben oder genommen
  hat, und daraus die Auskunft „im Kampf" ableiten.
- **FR-030d**: Der Kampfzustand MUSS zeitstempelbasiert und erst bei Abfrage ausgewertet werden; er
  DARF KEINE wiederkehrende Aufgabe je Träger anlegen (Prinzip II).
- **FR-030e**: Das System MUSS den Wechsel in den Kampf und heraus als Ereignis veröffentlichen,
  damit B08 (verringerte Mana-Regeneration im Kampf), B12 und B13 darauf aufbauen können, ohne
  eigene Zähler zu führen.
- **FR-030f**: Die Dauer, nach der ein Träger wieder als „nicht im Kampf" gilt, MUSS konfigurierbar
  sein.

#### Schadensattribution

- **FR-031**: Das System MUSS je Ziel festhalten, welcher Angreifer wie viel Schaden beigetragen
  hat.
- **FR-032**: Das System MUSS die Beitragsliste je Ziel in der Anzahl begrenzen; ist sie voll, weicht
  der kleinste Beitrag.
- **FR-033**: Das System MUSS Beiträge nach einer konfigurierten Zeit verfallen lassen; ein
  Angreifer, dessen letzter Beitrag zu lange her ist, ist nicht mehr beteiligt.
- **FR-034**: Das System MUSS beim Tod eine Aufteilung nach Schadensanteil liefern und daraus den
  größten Beitragenden benennen.
- **FR-035**: Das System MUSS für Selbstschaden keinen Beitrag erzeugen.
- **FR-036**: Das System MUSS die Beitragsliste eines Ziels vollständig freigeben, wenn das Ziel
  stirbt oder verschwindet.

#### Rückmeldung

- **FR-037**: Das System MUSS bei jedem angewandten Treffer eine Trefferanimation und einen Rückstoß
  auslösen.
- **FR-038**: Das System MUSS Treffer desselben Angreifers auf dasselbe Ziel innerhalb eines
  konfigurierten Fensters zu **einem** Anzeigeereignis mit der Summe zusammenfassen.
- **FR-039**: Das System DARF KEINE Anzeigeobjekte in der Welt erzeugen — weder Text-Displays noch
  Holograms. Die Darstellung gehört zu B13.
- **FR-040**: Ein Treffer ohne Schadenswirkung DARF KEIN Anzeigeereignis erzeugen.

#### Spieler gegen Spieler

- **FR-041**: Das System MUSS Schaden zwischen zwei Spielern verhindern.
- **FR-042**: Die Entscheidung MUSS an genau **einer** Stelle der Pipeline fallen, sodass B09 sie
  später durch eine Regel je Zone ersetzen kann, ohne die übrige Pipeline zu berühren.
- **FR-042a**: Dieselbe Stelle MUSS auch Schaden zwischen zwei Trägern ohne Spielerbezug verhindern
  — ein Mob verletzt keinen anderen Mob. Damit gibt es genau eine Stelle, an der entschieden wird,
  wer wen überhaupt angreifen darf.
- **FR-043**: Ein abgewiesener Angriff DARF weder Schaden noch Animation noch Beitrag erzeugen.

#### Performance und Grenzen

- **FR-044**: Das System DARF KEINE wiederkehrende Arbeit je Spieler oder je Mob anlegen; ohne
  Treffer kostet die Pipeline nichts.
- **FR-045**: Das System MUSS je Treffer ohne vermeidbare Objekterzeugung auskommen.
- **FR-046**: Das System MUSS Abfragen für einen Spieler ohne bereite Sitzung abweisen, statt mit
  Standardwerten zu rechnen.
- **FR-047**: Das System DARF KEINE Inhalte umsetzen, die späteren Blöcken gehören — keine
  XP-Kurve (B06), keine Fähigkeiten oder Manakosten (B08), keine Zonenregeln (B09), keine
  Mob-Definitionen (B10), keine Itemdefinitionen oder Loot-Tabellen (B11), keine Statistiken (B12)
  und keine Darstellung (B13).

### Key Entities

- **Schadensvorgang**: ein einzelner Schadensfall von der Quelle bis zur Nachwirkung. Trägt
  Angreifer (kann fehlen), Ziel, Schadensart, Rohschaden, den festgehaltenen Wertestand und den
  aktuellen Bearbeitungsstand.
- **Schadensart**: physisch, magisch oder Umgebung. Bestimmt, welches Attribut die Basis liefert und
  ob Verteidigung greift.
- **Schadensquelle**: woher der Vorgang stammt — Nahkampf, Projektil, Fähigkeit, Umgebung,
  Verwaltung. Für die Vanilla-Abbildung und für die Attribution.
- **Schadensfaktor**: der Anteil des Basisattributs, den ein Angriff als Rohschaden ansetzt. 1,0 im
  Nahkampf, frei wählbar für Fähigkeiten (B08).
- **Mob-Wertesatz**: die konfigurierten Attributwerte für eine Mob-Art, mit denen B05 einen
  erscheinenden Mob ausstattet, bis B10 das übernimmt.
- **Kampfzustand**: je Träger der Zeitpunkt der letzten Kampfhandlung, aus dem sich „im Kampf"
  ableitet.
- **Pipeline-Stufe**: ein benannter Abschnitt mit einem Eingriffspunkt.
- **Angriffszeitfenster**: je Angreifer der Zeitpunkt des letzten zählenden Angriffs, gegen den der
  nächste geprüft wird.
- **Beitragsfenster**: je Ziel eine begrenzte, alternde Liste von Angreifern mit ihrem
  Schadensanteil.
- **Schadensaufteilung**: das Ergebnis daraus beim Tod — Anteile je Angreifer plus der größte
  Beitragende.
- **Todesereignis**: Ziel, Verursacher (kann fehlen), Schadensaufteilung, Todesursache.
- **Anzeigeereignis**: zusammengefasster Schaden eines Angreifers auf ein Ziel innerhalb eines
  Fensters.
- **Vanilla-Quellenzuordnung**: die Tabelle, die jede Vanilla-Schadensquelle genau einer Behandlung
  zuordnet.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Kein Vanilla-Schaden erreicht jemals ungefiltert einen Spieler oder Custom-Mob —
  nachweisbar über einen Test je Quelle aus der Tabelle.
- **SC-002**: Bei 50 physischem Schaden gegen 100 Verteidigung verliert das Ziel exakt 25 Leben;
  bei 300 Verteidigung exakt 75 % weniger als der Rohschaden.
- **SC-003**: Die Herzleiste zeigt nach jedem Schadensereignis den korrekten Gesundheitsanteil.
- **SC-004**: 150 Angreifer gegen 800 Ziele im Dauerkampf halten die 95.-Perzentil-Tickzeit unter
  40 Millisekunden.
- **SC-005**: 10 000 aufeinanderfolgende Treffer erzeugen kein vermeidbares Objekt je Treffer.
- **SC-006**: Ein Spieler mit einer Angriffsgeschwindigkeit von 4 je Sekunde erzeugt bei zehn
  Schlägen in einer Sekunde genau vier zählende Treffer.
- **SC-007**: Bei drei Angreifern mit 60 %, 30 % und 10 % Schadensanteil weist die Aufteilung genau
  diese Anteile aus und benennt den ersten als größten Beitragenden.
- **SC-008**: Die Beitragsliste eines Ziels überschreitet ihre Höchstzahl nie, unabhängig davon,
  wie viele Angreifer beteiligt sind.
- **SC-009**: Zwanzig Treffer innerhalb des Anzeigefensters erzeugen genau ein Anzeigeereignis mit
  der korrekten Summe.
- **SC-010**: Ein Spieler kann einem anderen Spieler unter keinen Umständen Schaden zufügen, und
  ein Mob keinem anderen Mob.
- **SC-010a**: Ein Bogenschuss macht Schaden — nachweisbar über einen Treffer aus der Ferne, der
  denselben Rechenweg nimmt wie ein Nahkampfschlag.
- **SC-010b**: Ein Spieler verliert beim Tod kein einziges Item aus seinem Inventar.
- **SC-010c**: Ein feindliches Wesen ist unmittelbar nach seinem Erscheinen angreifbar und hat
  Werte aus der Konfiguration; ein friedliches Wesen bleibt unangetastet.
- **SC-010d**: Nach 800 erschienenen und wieder gestorbenen Wesen existiert kein Stat-Träger mehr,
  dessen Wesen fort ist.
- **SC-010e**: Ein Träger gilt unmittelbar nach einem Treffer als „im Kampf" und nach Ablauf der
  konfigurierten Dauer wieder als nicht im Kampf — ohne dass dafür eine Aufgabe geplant wurde.
- **SC-010f**: Beim Tod eines Wesens erscheinen weder Vanilla-Erfahrungskugeln noch Vanilla-Beute.
- **SC-011**: Je Tod entsteht genau ein Todesereignis, auch bei zwei tödlichen Treffern im selben
  Tick.
- **SC-012**: Die gesamte Schadensberechnung ist ohne laufenden Server geprüft, mit dokumentierten
  Beispielrechnungen für jede Schadensart.
- **SC-012a**: Derselbe Sturz kostet einen Träger mit 100 maximalem Leben einen deutlich größeren
  Anteil als einen mit 2000 — nachweisbar, indem beide denselben Fall erleiden und die verlorenen
  Anteile verglichen werden. Der absolute Betrag ist in beiden Fällen gleich.
- **SC-013**: Im Leerlauf — verbundene Spieler, aber kein Kampf — verbraucht die Pipeline keine
  messbare Tickzeit und legt keine Aufgabe an.

## Assumptions

- **Rohschaden aus dem Attribut**: Ein Nahkampfschlag verwendet `physicalDamage` mit dem Faktor 1,0
  als Rohschaden. Waffenspezifische Zuschläge sind Sache von B11 und kommen über die dortigen
  Stat-Beiträge, nicht über eine eigene Waffenlogik hier. Fähigkeiten geben ihren eigenen Faktor an
  (FR-002a).
- **Selbstschaden bleibt möglich, Mob gegen Mob nicht**: Ein Träger kann sich selbst treffen (eigene
  Explosion); zwei Träger ohne Spielerbezug können einander nicht treffen. Kettenreaktionen in einer
  Horde entfallen damit — der Preis dafür, dass der heißeste Pfad des Plugins genau eine
  Erlaubnisprüfung hat und keine Verursacherkette über mehrere Schadensvorgänge mitführen muss.
- **Umgebungsschaden ist ein fester Betrag** (entschieden 2026-08-20): Fall-, Feuer- und ähnlicher
  Schaden wird als konfigurierbarer absoluter Wert gerechnet, nicht als Anteil des maximalen
  Lebens. Das ist eine ausdrückliche Designentscheidung, keine Vereinfachung: Umgebungsgefahren
  sollen für Anfänger ernst und für ausgerüstete Spieler belanglos werden. Ein Sturz, der einen
  Anfänger mit 100 Leben ein Drittel kostet, kostet einen Spieler mit 2000 Leben unter zwei
  Prozent — genau der beabsichtigte Verlauf. Ein prozentualer Schaden wäre das Gegenteil: über die
  gesamte Progression gleich gefährlich.
- **Verteidigung greift bei Umgebungsschaden nicht.** Sonst wäre Verteidigung ein zweiter Schutz
  gegen Stürze, und der Verlauf oben käme doppelt zustande — einmal über wachsendes Leben, einmal
  über wachsende Verteidigung. Ein Wirkmechanismus reicht.
- **Der Rückstoß bleibt der Vanilla-Rückstoß.** Ein eigenes Rückstoßmodell gehört nicht zum
  Umfang; B05 löst ihn nur ausdrücklich aus, weil ein neutralisiertes Ereignis ihn nicht mehr von
  sich aus erzeugt.
- **Beitragsfenster**: Höchstzahl und Verfallszeit sind konfigurierbar; Ausgangswerte sind 16
  Angreifer je Ziel und 30 Sekunden. Beides ist über die Konfiguration änderbar, ohne Codeänderung.
- **Anzeigefenster**: Ausgangswert eine halbe Sekunde je Angreifer-Ziel-Paar.
- **Zugriffsschutz**: Für Spieler gilt die Sitzungsregel aus B03 und B04 — vor der Freigabe kann
  weder Schaden genommen noch ausgeteilt werden.
- **Respawn**: Volles Leben und volles Mana. Ein Wiedererscheinen mit Teilwerten wäre eine
  Todesstrafe, und die ist als Ausrüstungsschaden festgelegt, nicht als Lebensverlust.
- **Selbstschaden ist möglich** (eigene Explosion), erzeugt aber keinen Attributionsbeitrag.
- **Vanilla-Schilde bleiben wirkungslos.** Blocken war Teil der Sekundärmechaniken, die
  ausgeschlossen wurden; ein Schild mindert daher nichts. Er zu ignorieren ist ohnehin die Folge
  davon, dass eigener Schaden auf eigenes Leben angewandt wird.
- **Vanilla-Rüstungswerte bleiben wirkungslos.** Sie mindern Vanilla-Schaden, den es hier nicht mehr
  gibt. Rüstung wirkt ausschließlich über die Stat-Beiträge aus B11 (ADR-004).
- **Mob-Werte sind eine Überbrückung, keine Mob-Definition.** Die Staffelung nach Mob-Art in
  `combat.yml` existiert, damit gekämpft und lastgetestet werden kann. Was ein Mob *ist* — Name,
  Verhalten, Fähigkeiten, Beute — bleibt vollständig B10.
- **Als feindlich gilt**, was Vanilla als feindlich einstuft. Eine eigene Einteilung wäre bereits
  eine Mob-Definition und damit B10.

## Dependencies

- **B04 Stat-Engine**: Wertestand je Träger, Minderungsfunktion, Ressourcenbehälter für Leben und
  Mana, Spiegelung der Herzleiste. Bereits erledigt: abgeschaltete natürliche Regeneration und
  fixierte Sättigung (ADR-013).
- **B01 Core & Plattform**: Ereignisbus, Scheduler-Abstraktion, Konfigurationsladen mit
  Schemaprüfung.
- **B03 Spieler-Session**: Zustand „bereit", an den die Teilnahme am Kampf gebunden ist.
- **ADR-003**: verbindliche Vorgabe, dass jede Vanilla-Schadensquelle einzeln festgelegt wird.
- **ADR-008**: keine Sekundärwerte — die Grundlage dafür, dass hier kein Krit existiert.
