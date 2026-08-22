# Feature Specification: B08 · Fähigkeiten-Framework

**Feature Branch**: `008-ability-framework`

**Created**: 2026-08-22

**Status**: Draft

**Input**: Blocksteckbrief `blocks/B08-ability-framework.md` in der Fassung vom 2026-08-22 — passive und
aktive Fähigkeiten je Klasse, sechs je Klasse, darunter genau eine Unique Class Ability. Hängt ab von
B04 (StatSnapshot, Mana-Pool, Modifikatoren), B05 (Schaden aus Fähigkeiten läuft durch die reguläre
Pipeline, Kampfzustand) und B07 (`AbilityBinding`, `AbilityKind`, `ClassRegistry.abilitiesOf`,
`CharacterClassDefinition.unlockedAt`); wird benötigt von B13. Verbindlich: **ADR-022** (die Unique
zählt zu den sechs und darf passiv sein, kurzer globaler Cooldown, Casting-Zeiten mit Unterbrechung,
Lifesteal ist Kampf-Effekt statt neuntes Attribut), ADR-005 (Vanilla-Client, Auslösung nur über
Hotbar-Slot und Rechtsklick, keine eigenen Keybinds), ADR-008 (nur die acht Attribute),
ADR-018 (charaktergebundene Items sind unbeweglich), Prinzip II (kein Datenbankzugriff je
Spielereignis, keine wiederkehrende Aufgabe je Spieler — Cooldowns und Mana-Regeneration
zeitstempelbasiert lazy), Prinzip III (`rpg-core` ohne Bukkit), Prinzip V (alle Balancing-Zahlen in
validierter Konfiguration).

## Clarifications

### Session 2026-08-22 — vor `/specify`, festgehalten in ADR-022

- Q: Die Unique Class Ability ist im Steckbrief für Rogue und Mage passiv, `AbilityBinding` erzwingt
  aber `unique ⇒ ACTIVE`. Was gilt? → A: **Die Unique ist eine der sechs**, kein siebter Eintrag,
  keine eigene Kategorie und kein eigener Reiter. Sie ist eine gewöhnliche Bindung mit gesetztem
  `unique`-Flag; ihre Art hängt an der Klasse. Damit fällt die Invariante `unique ⇒ ACTIVE`. Die
  Alternative — Second Life und Magic Boost & Fall zu aktiven Fähigkeiten umzubauen — hätte eine
  bereits abgeschlossene Frage wieder geöffnet, um eine Zählregel zu retten, die auch ohne sie
  aufgeht.

- Q: Gibt es einen globalen Cooldown zwischen beliebigen Fähigkeiten? → A: **Ja, kurz.** Grund ist das
  Eingabeschema selbst: Slot-Wechsel plus Rechtsklick geht viermal im selben Tick, ohne Sperre wäre
  „alle vier sofort" immer die stärkste Eröffnung und der Cooldown je Fähigkeit nur eine Aussage über
  die Wiederholung, nicht über die Eröffnung. Er wird wie die Einzel-Cooldowns zeitstempelbasiert lazy
  gerechnet; der Wert ist Konfiguration.

- Q: Sind Casting-Zeiten und Unterbrechung vorgesehen? → A: **Ja.** Eine Fähigkeit darf eine Wirkzeit
  haben, ein laufender Cast ist unterbrechbar. Instant ist der Fall `cast-time: 0`, nicht die
  Abwesenheit der Mechanik. Wirkzeit nachträglich einzuziehen hätte jede vorhandene Fähigkeit, das HUD
  (B13) und die Eingabebehandlung gleichzeitig angefasst.

- Q: Wie wird Warriors passives Lifesteal umgesetzt, wo ADR-008 Sekundärwerte zurückstellt? → A: Als
  **Effekt im Kampf-Hook**, nicht als neuntes Attribut. Der Prozentsatz hängt an der Fähigkeitsstufe,
  nicht an einem Attribut. ADR-008 bleibt unangetastet; ein neuntes Attribut hätte Stat-Engine,
  Persistenz und HUD gleichzeitig geöffnet und mit ihm die Tür für Crit und Resistenzen.

### Abgeleitete Entscheidungen

Diese vier Antworten ziehen Folgen nach sich, die hier festgehalten werden, weil sie den Zuschnitt des
Blocks bestimmen:

- **Passive Fähigkeiten brauchen einen Auslöser, keinen Slot.** Sobald die Unique passiv sein darf,
  reicht „passiv heißt Modifikator" nicht mehr: Second Life wirkt beim Tod, Lifesteal beim Austeilen,
  Ausweichen beim Einstecken. Passive werden deshalb über einen **Trigger** definiert, und ein
  Dauermodifikator ist der Sonderfall `ALWAYS` — nicht umgekehrt.

- **Der globale Cooldown macht das Hotbar-Schema zur Pflicht in diesem Block.** B07 hat den festen
  Slot der gebundenen Waffe ausdrücklich an B08 abgegeben. Da vier aktive Fähigkeiten ebenfalls Slots
  belegen und passive Uniques Item-Marker tragen, wird die Belegung aller neun Slots hier festgelegt.

- **Der Cast-Zustand ist der erste Spielerzustand mit Ablaufzeit im Tick.** Cooldowns lassen sich rein
  lazy lesen — ein Cast muss zu einem bestimmten Zeitpunkt *wirken*. Das ist die einzige Stelle in
  diesem Block, an der eine geplante Ausführung nötig ist, und sie hängt am Cast, nicht am Spieler:
  ein Spieler ohne laufenden Cast hat keine Aufgabe.

- **Coins existieren im Projekt nicht.** Es gibt kein Guthaben, keine Währungstabelle und keinen
  Verdienstweg — B06 kennt nur Erfahrung und Level. Die Coin-Aufwertung wird deshalb wie B07s
  Kostenblock behandelt: B08 modelliert den **Rang je Fähigkeit** samt Wirkung und
  Weiterschalt-Schnittstelle, aber nicht, wer ihn bezahlt. Das bleibt B11/B16 (Workflow-Regel 5).

- **Summon entfällt vorerst aus der Primitive-Liste.** Beschworenes gehört zu B10, das es noch nicht
  gibt. Die Liste der Primitives ist erweiterbar angelegt; ein Nachtrag kostet ein Primitive und keine
  Architekturänderung.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ein Spieler löst eine Fähigkeit aus (Priority: P1)

Ein Spieler mit einer Klasse hat auf seiner Hotbar neben der gebundenen Waffe Gegenstände für seine
freigeschalteten aktiven Fähigkeiten. Er wählt einen dieser Slots und klickt rechts. Kostet die
Fähigkeit Mana und ist sie nicht auf Cooldown, wirkt sie: Schaden läuft durch die reguläre
Kampf-Pipeline, Heilung und Buffs treffen den vorgesehenen Zielbereich. Danach ist die Fähigkeit für
ihre Cooldown-Dauer gesperrt und alle anderen für die kurze globale Sperre.

**Why this priority**: Ohne diesen Weg gibt es keine Fähigkeiten. Alles andere in diesem Block ist
Ausbau eines Pfads, den es zuerst geben muss.

**Independent Test**: Ein Warrior auf Stufe 5 klickt mit dem Schild-Item rechts. Sein Mana sinkt um
die konfigurierten Kosten, der Schild-Effekt ist wirksam, ein zweiter Rechtsklick unmittelbar danach
wird abgewiesen.

**Acceptance Scenarios**:

1. **Given** ein Warrior auf Stufe 5 mit vollem Mana, **When** er im Schild-Slot rechtsklickt,
   **Then** wird das Mana um die Kosten reduziert, der Effekt ist wirksam und der Cooldown läuft.
2. **Given** derselbe Spieler unmittelbar nach dem Auslösen, **When** er erneut im selben Slot
   rechtsklickt, **Then** wird die Auslösung abgewiesen und die verbleibende Cooldown-Zeit gemeldet.
3. **Given** ein Spieler mit weniger Mana als die Kosten, **When** er auslöst, **Then** wird
   abgewiesen, kein Mana verbraucht und kein Cooldown gestartet.
4. **Given** ein Spieler, der eben eine andere Fähigkeit ausgelöst hat, **When** er innerhalb der
   globalen Sperre eine zweite auslöst, **Then** wird abgewiesen, ohne den Einzel-Cooldown der
   zweiten Fähigkeit zu starten.
5. **Given** ein Spieler mit einem Fähigkeits-Item im Slot, **When** er damit **links**klickt,
   **Then** löst das keine Fähigkeit und keinen Nahkampfangriff aus.
6. **Given** ein Spieler ohne aktiven Charakter (ADR-020/ADR-021), **When** irgendetwas eine Auslösung
   versucht, **Then** wird sie abgewiesen, bevor Kosten oder Cooldown berührt werden.

---

### User Story 2 - Passive Fähigkeiten wirken, ohne ausgelöst zu werden (Priority: P1)

Freigeschaltete passive Fähigkeiten wirken von selbst. Manche sind Dauermodifikatoren, andere hängen
an einem Ereignis: Warriors Lifesteal heilt beim Austeilen, Rogues Ausweichen greift beim Einstecken,
Rogues Second Life greift beim Tod, Mages Arkane Sammlung beim Töten. Sie kosten kein Mana, belegen
keinen Hotbar-Slot zum Auslösen und haben — wo die Wirkung nicht dauerhaft ist — einen eigenen
Cooldown.

**Why this priority**: Zwei der drei Unique Class Abilities sind passiv. Ohne diese Story hat die
Hälfte der Klassen ihr Markenzeichen nicht.

**Independent Test**: Ein Warrior auf Stufe 25 schlägt ein Ziel; seine Gesundheit steigt um den
konfigurierten Anteil des tatsächlich zugefügten Schadens, gedeckelt auf sein Maximum.

**Acceptance Scenarios**:

1. **Given** ein Warrior auf Stufe 25 mit verletzter Gesundheit, **When** er Schaden zufügt, **Then**
   heilt er den konfigurierten Anteil des **nach Mitigation tatsächlich zugefügten** Schadens.
2. **Given** derselbe Warrior auf voller Gesundheit, **When** er Schaden zufügt, **Then** verpufft die
   Heilung, ohne die Gesundheit über das Maximum zu heben und ohne einen Fehler.
3. **Given** ein Warrior auf Stufe 24, **When** er Schaden zufügt, **Then** heilt er nicht — die
   Fähigkeit ist noch nicht freigeschaltet.
4. **Given** ein Rogue auf Stufe 45 mit Second Life und einer Chance von 100 %, **When** er tödlichen
   Schaden nimmt, **Then** stirbt er nicht, sondern steht mit dem konfigurierten Anteil seiner
   Gesundheit wieder auf und die Fähigkeit geht auf ihren Cooldown.
5. **Given** derselbe Rogue innerhalb dieses Cooldowns, **When** er erneut tödlichen Schaden nimmt,
   **Then** stirbt er regulär.
6. **Given** ein Mage auf Stufe 45, **When** er in der Luft ein zweites Mal springt, **Then** wird der
   Sprung ausgeführt und der Fall verlangsamt; ein dritter Sprung vor Bodenkontakt wird nicht
   ausgeführt.

---

### User Story 3 - Cooldowns und Mana überleben das Ausloggen (Priority: P2)

Wer sich mitten im Cooldown ausloggt, findet ihn beim Wiedereinloggen im richtigen Stand vor — es
läuft weiter, während der Spieler weg ist, statt bei Null oder bei voller Dauer neu zu beginnen.
Dasselbe gilt für Mana: es regeneriert über die Abwesenheit hinweg, ohne dass dafür etwas gelaufen
wäre.

**Why this priority**: Ohne diese Story ist Ausloggen der schnellste Weg, jeden Cooldown zu löschen —
und ein langer Cooldown wie der der Unique wäre wertlos.

**Independent Test**: Ein Spieler löst eine Fähigkeit mit 60 s Cooldown aus, loggt aus, loggt nach 20 s
wieder ein und findet rund 40 s Restzeit vor.

**Acceptance Scenarios**:

1. **Given** ein Spieler mit 60 s Cooldown, **When** er sich abmeldet und nach 20 s wieder anmeldet,
   **Then** zeigt und erzwingt der Cooldown rund 40 s Restzeit.
2. **Given** derselbe Spieler, **When** er sich nach 90 s wieder anmeldet, **Then** ist die Fähigkeit
   verfügbar, und die abgelaufene Sperre belegt keinen Speicher mehr.
3. **Given** ein Spieler mit halbem Mana, **When** er sich abmeldet und nach ausreichend Zeit wieder
   anmeldet, **Then** ist sein Mana voll — ohne dass während der Abwesenheit etwas gerechnet wurde.
4. **Given** ein laufender Cast, **When** der Spieler die Verbindung verliert, **Then** wird der Cast
   verworfen, die Kosten erstattet und kein Effekt ausgelöst.

---

### User Story 4 - Eine Fähigkeit mit Wirkzeit lässt sich unterbrechen (Priority: P2)

Fähigkeiten mit einer Wirkzeit wirken nicht sofort. Während der Wirkzeit ist der Spieler im
Cast-Zustand; erhaltener Schaden bricht ihn ab, und je nach Fähigkeit tut das auch ein Slot-Wechsel
oder Bewegung. Ein abgebrochener Cast kostet nichts und startet keinen Cooldown — er hat schlicht
nicht stattgefunden.

**Why this priority**: Die Mechanik ist entschieden (ADR-022) und muss von Anfang an in der
Ability-Definition stehen. Sie später einzuziehen fasst jede Fähigkeit, das HUD und die
Eingabebehandlung gleichzeitig an.

**Independent Test**: Eine Fähigkeit mit 2 s Wirkzeit wird ausgelöst; nach 1 s nimmt der Spieler
Schaden. Kein Effekt tritt ein, das Mana ist unverändert, die Fähigkeit ist sofort wieder auslösbar.

**Acceptance Scenarios**:

1. **Given** eine Fähigkeit mit 2 s Wirkzeit, **When** der Spieler sie auslöst und ungestört bleibt,
   **Then** wirkt sie nach 2 s und der Cooldown beginnt in diesem Moment.
2. **Given** derselbe Cast, **When** der Spieler nach 1 s Schaden nimmt, **Then** bricht der Cast ab,
   das Mana ist unverändert und kein Cooldown läuft.
3. **Given** derselbe Cast, **When** der Spieler den Hotbar-Slot wechselt, **Then** bricht der Cast
   ab.
4. **Given** ein laufender Cast, **When** der Spieler eine zweite Fähigkeit auslösen will, **Then**
   wird abgewiesen — ein Spieler hat höchstens einen laufenden Cast.
5. **Given** eine Fähigkeit mit Wirkzeit 0, **When** sie ausgelöst wird, **Then** wirkt sie im selben
   Tick und es entsteht kein Cast-Zustand.

---

### User Story 5 - Der Betreiber baut eine neue Fähigkeit aus vorhandenen Bausteinen (Priority: P2)

Eine neue Fähigkeit entsteht als Eintrag in der Konfiguration: eine Kennung, Kosten, Cooldown,
optionale Wirkzeit, ein Auslöser oder ein Hotbar-Item, eine Zielbestimmung und eine Liste von
Effekten aus dem festen Vorrat an Primitives. Kein Java, kein Neustart des Projekts, keine neue
Klasse.

**Why this priority**: Das ist das erklärte Ziel des Blocks und das Akzeptanzkriterium des
Steckbriefs. Es ist P2, weil es die ersten beiden Stories voraussetzt: Bausteine, die es noch nicht
gibt, lassen sich nicht neu zusammensetzen.

**Independent Test**: Eine Fähigkeit „Feuerregen" wird als Konfigurationseintrag aus Radius-Ziel plus
Damage-Primitive angelegt, einer Klasse zugeordnet und ist nach dem Start auslösbar — ohne dass eine
Quelldatei angefasst wurde.

**Acceptance Scenarios**:

1. **Given** eine Konfiguration mit einer neuen Fähigkeit aus vorhandenen Primitives, **When** der
   Server startet, **Then** ist sie verfügbar und wirkt wie beschrieben.
2. **Given** eine Fähigkeit, die ein unbekanntes Primitive nennt, **When** der Server startet,
   **Then** verweigert er den Start mit einer Meldung, die Fähigkeit und Primitive benennt.
3. **Given** eine Klassenbindung, die eine Fähigkeits-ID nennt, die es nicht gibt, **When** der Server
   startet, **Then** verweigert er den Start und benennt Klasse und ID.
4. **Given** eine Fähigkeit mit einem Flächeneffekt ohne Zielobergrenze, **When** der Server startet,
   **Then** verweigert er den Start — die Obergrenze ist Pflicht, nicht Empfehlung.

---

### User Story 6 - Jede Klasse hat ihr vollständiges Loadout (Priority: P2)

Warrior, Rogue und Mage haben je sechs Fähigkeiten: vier aktive und zwei passive, darunter genau eine
Unique. Die Freischaltstufen liegen auf 1, 5, 15, 25, 35 und 45; die Unique ist die letzte. Damit sind
die in B07 leer gelassenen Bindungen gefüllt und die Prüfung „leer oder genau sechs" fällt zum ersten
Mal auf die zweite Seite.

**Why this priority**: Ohne Loadouts hat der Block eine Maschine, aber keinen Inhalt. Er ist erst
spielbar, wenn alle drei Klassen bestückt sind.

**Independent Test**: Für jede der drei Klassen liefert die Bindungsauskunft sechs Einträge, vier
davon aktiv, genau einer als Unique markiert; auf Stufe 1 ist genau einer freigeschaltet, auf Stufe 45
alle sechs.

**Acceptance Scenarios**:

1. **Given** die ausgelieferte Konfiguration, **When** der Server startet, **Then** hat jede der drei
   Klassen genau sechs Fähigkeiten, vier aktiv, zwei passiv, genau eine Unique.
2. **Given** ein Charakter auf Stufe 1, **When** die freigeschalteten Fähigkeiten abgefragt werden,
   **Then** ist es genau die passive Fähigkeit der Stufe 1.
3. **Given** ein Charakter, der Stufe 5 erreicht, **When** der Aufstieg verarbeitet ist, **Then**
   erscheint das Item der neu freigeschalteten Fähigkeit in seinem Slot und er wird darüber
   unterrichtet.
4. **Given** ein Charakter auf Stufe 45, **When** die Bindungsauskunft gelesen wird, **Then** sind
   alle sechs freigeschaltet.

---

### User Story 7 - Fähigkeiten haben einen Rang (Priority: P3)

Eine freigeschaltete Fähigkeit hat einen Rang, der ihre Zahlen skaliert — Schaden, Heilung,
Prozentsätze, Dauer. Der Rang gehört dem Charakter, nicht dem Konto, und überlebt den Neustart. Wer
den Aufstieg bezahlt, entscheidet dieser Block nicht.

**Why this priority**: Die Aufwertung ist entschieden, aber die Währung existiert im Projekt nicht.
P3, weil die Fähigkeit auf Rang 1 vollständig spielbar ist und der Rang ohne Zahlweg nur über die
Verwaltungs-Schnittstelle bewegt wird.

**Independent Test**: Der Rang einer Fähigkeit wird über die Verwaltungs-Schnittstelle erhöht; die
Wirkung der Fähigkeit steigt entsprechend der Rangkurve und bleibt nach einem Neustart erhalten.

**Acceptance Scenarios**:

1. **Given** eine Fähigkeit auf Rang 1, **When** ihr Rang erhöht wird, **Then** skalieren ihre Zahlen
   entlang der konfigurierten Kurve.
2. **Given** eine Fähigkeit auf Höchstrang, **When** eine weitere Erhöhung versucht wird, **Then**
   wird abgewiesen, ohne den Rang zu verändern.
3. **Given** ein Charakter mit erhöhten Rängen, **When** der Server neu startet, **Then** stehen die
   Ränge unverändert.
4. **Given** zwei Charaktere desselben Kontos, **When** einer einen Rang erhöht, **Then** bleibt der
   andere unberührt (ADR-011).

---

### User Story 8 - Der Betreiber justiert Fähigkeiten ohne Codeänderung (Priority: P3)

Kosten, Cooldowns, Wirkzeiten, Reichweiten, Zielobergrenzen, Rangkurven, die globale Sperre und die
Mana-Regeneration stehen in Konfiguration und werden beim Start gegen ein Schema geprüft. Ein Fehler
verhindert den Start mit einer Meldung, die Fähigkeit und Feld benennt.

**Why this priority**: Prinzip V. P3, weil Balancing erst zählt, wenn es etwas zu balancieren gibt.

**Independent Test**: Ein Cooldown wird in der Konfiguration halbiert; nach dem Neustart gilt der neue
Wert, ohne dass eine Quelldatei angefasst wurde.

**Acceptance Scenarios**:

1. **Given** eine geänderte Zahl in der Konfiguration, **When** der Server startet, **Then** gilt sie.
2. **Given** ein negativer Cooldown oder negative Kosten, **When** der Server startet, **Then**
   verweigert er den Start und benennt Fähigkeit und Feld.
3. **Given** eine Cooldown-Reduktion über dem harten Cap von 40 % (ADR-008), **When** sie sich aus
   Attributen ergibt, **Then** wird bei 40 % gekappt.

---

### Edge Cases

- **Der Spieler stirbt während eines Casts.** Der Cast wird verworfen, Kosten erstattet, kein Effekt.
- **Der Spieler wechselt den Charakter, während ein Cooldown läuft.** Cooldowns gehören dem Charakter,
  nicht dem Konto (ADR-011). Der zweite Charakter hat seine eigenen.
- **Eine Fähigkeit trifft ein Ziel, das im selben Tick stirbt.** Der Effekt läuft über die reguläre
  Pipeline, die den Todesfall bereits kennt; nichts wird doppelt angewandt.
- **Ein Flächeneffekt findet mehr Ziele als erlaubt.** Es werden bis zur Obergrenze die nächsten
  Ziele bedient, der Rest bleibt unberührt — nicht zufällig, sondern nach Abstand, damit dasselbe
  Bild dasselbe Ergebnis gibt.
- **Ein Flächeneffekt findet gar kein Ziel.** Kosten und Cooldown fallen trotzdem an; die Fähigkeit
  wurde ausgelöst. Nur eine abgewiesene Auslösung ist kostenlos.
- **Ein Projektil trifft, nachdem der Werfer gegangen ist.** Es trägt die Werte vom Abwurfzeitpunkt
  (wie B05s `projectileDamage`) und wirkt auch dann, wenn der Werfer nicht mehr da ist.
- **Der Spieler stirbt, während Second Life auf Cooldown ist.** Er stirbt regulär.
- **Second Life und tödlicher Schaden aus `kill`.** Die administrative Tötung ist nicht abfangbar.
- **Mana wird während eines Casts durch etwas anderes verbraucht.** Die Kosten sind beim Start
  abgebucht; ein zweiter Verbrauch kann nicht dieselben Punkte greifen.
- **Der Kampfzustand wechselt mitten in einem Regenerationsintervall.** Die Mana-Regeneration wird
  beim Wechsel abgerechnet, damit jedes Intervall genau eine Rate hat.
- **Ein Fähigkeits-Item wird bewegt, geworfen oder abgelegt.** Nicht möglich — es ist
  charaktergebunden wie die Klassenausrüstung (ADR-018).
- **Der Spieler erreicht ein Level, dessen Fähigkeit er schon hätte.** Beim Anmelden werden alle
  Slots gegen den Stand gesetzt; eine übersprungene Freischaltung gibt es nicht.
- **Ein Effekt wirft eine Ausnahme.** Sie wird abgefangen, mit der Kennung der Fähigkeit
  protokolliert und auf dieses eine Ereignis begrenzt (Prinzip VI); die übrigen Effekte derselben
  Fähigkeit laufen weiter.

## Requirements *(mandatory)*

### Functional Requirements — Ability-Definition und Konfiguration

- **FR-001**: Das System MUSS Fähigkeiten aus einer versionierten Konfigurationsdatei laden und beim
  Start gegen ein Schema prüfen. Ein Verstoß verhindert den Start mit einer Meldung, die Fähigkeit und
  Feld benennt (Prinzip V).
- **FR-002**: Eine Ability-Definition MUSS mindestens tragen: eindeutige Kennung, Art (aktiv oder
  passiv), Anzeigename als Message-Schlüssel, Mana-Kosten, Cooldown, Wirkzeit, Zielbestimmung, Liste
  von Effekten und Rangkurve.
- **FR-003**: Eine **aktive** Definition MUSS zusätzlich das Hotbar-Item tragen; eine **passive**
  MUSS einen Trigger tragen und DARF ein Marker-Item tragen.
- **FR-004**: Das System MUSS eine Definition abweisen, die ein unbekanntes Primitive, eine unbekannte
  Zielbestimmung oder einen unbekannten Trigger nennt.
- **FR-005**: Das System MUSS doppelte Fähigkeits-Kennungen abweisen.
- **FR-006**: Das System MUSS beim Start prüfen, dass jede in einer Klassenbindung genannte
  Fähigkeits-ID definiert ist, und andernfalls den Start verweigern. Damit erfüllt B08 die Zusage, die
  B07 offen gelassen hat: dort reist die ID als undurchsichtige Zeichenkette.
- **FR-007**: Das System MUSS beim Start prüfen, dass die Art in der Definition mit der Art in der
  Klassenbindung übereinstimmt, und andernfalls den Start verweigern.
- **FR-008**: Das System MUSS jede Zahl der Definitionen aus Konfiguration beziehen. Im Code steht
  keine Kosten-, Cooldown-, Reichweiten- oder Wirkungszahl.
- **FR-009**: Alle Spielertexte des Blocks MÜSSEN über Message-Schlüssel laufen (Prinzip V).

### Functional Requirements — Effect-Primitives

- **FR-010**: Das System MUSS die folgenden Primitives bereitstellen: **Damage**, **Heal**,
  **ManaRestore**, **Lifesteal**, **Shield**, **Buff**, **Debuff**, **StatusEffect**, **Dash**,
  **Knockback**, **Teleport**, **Projectile**.
- **FR-011**: Ein Primitive MUSS aus seiner Definition heraus konfigurierbar sein und ohne
  Codeänderung in beliebiger Kombination mit anderen in einer Fähigkeit stehen können.
- **FR-012**: Schaden aus Fähigkeiten MUSS über die reguläre Kampf-Pipeline laufen und dabei den
  Ursprung „Fähigkeit" tragen. Er unterliegt nicht dem Angriffsfenster, weil er eigene Cooldowns hat.
- **FR-013**: Das Damage-Primitive MUSS seinen Wert als **Faktor** auf das passende Schadensattribut
  angeben, nicht als absolute Zahl. Damit skaliert es mit Ausrüstung und Level, ohne selbst ein
  Attribut zu lesen.
- **FR-014**: Buff und Debuff MÜSSEN als zeitlich begrenzte Modifikatoren auf die acht Attribute
  wirken und über einen Zeitstempel ablaufen, nicht über eine mitlaufende Zählung.
- **FR-015**: Das Shield-Primitive MUSS eine Menge Schadens absorbieren, bevor Gesundheit sinkt, und
  nach Ablauf oder Verbrauch von selbst enden.
- **FR-016**: Das Lifesteal-Primitive MUSS einen Anteil des **nach Mitigation tatsächlich zugefügten**
  Schadens als Heilung zurückgeben und dafür KEIN neues Attribut einführen (ADR-008, ADR-022).
- **FR-017**: Ein Effekt, der eine Ausnahme wirft, MUSS abgefangen, mit der Kennung seiner Fähigkeit
  protokolliert und auf dieses eine Ereignis begrenzt werden; die übrigen Effekte laufen weiter.
- **FR-018**: Eine ausgelöste Fähigkeit MUSS mit dem Wertestand vom Auslösezeitpunkt arbeiten. Spätere
  Wertänderungen wirken nicht rückwirkend auf einen laufenden Effekt.

### Functional Requirements — Targeting

- **FR-019**: Das System MUSS die folgenden Zielbestimmungen bereitstellen: **Selbst**,
  **Blickrichtung**, **Cursor-Ziel**, **Radius um den Auslöser**, **Kegel in Blickrichtung**,
  **Linie in Blickrichtung**, **nächstes Ziel**.
- **FR-020**: Jede Zielbestimmung, die mehr als ein Ziel liefern kann, MUSS eine Obergrenze für die
  Zielanzahl tragen. Eine fehlende Obergrenze verhindert den Start (FR-001).
- **FR-021**: Bei mehr Kandidaten als erlaubt MUSS nach aufsteigendem Abstand ausgewählt werden, damit
  dieselbe Lage dasselbe Ergebnis gibt.
- **FR-022**: Die Zielsuche MUSS über einen räumlichen Index laufen, nicht über lineare Iteration
  aller Kandidaten (Prinzip II).
- **FR-023**: Eine Zielbestimmung MUSS die Schadensberechtigung respektieren, die B05 kennt — sie
  liefert kein Ziel, das nicht angegriffen werden darf.

### Functional Requirements — Runtime: Kosten, Cooldown, globale Sperre

- **FR-024**: Das System MUSS eine Auslösung abweisen, wenn das aktuelle Mana unter den Kosten liegt,
  und dabei weder Mana verbrauchen noch einen Cooldown starten.
- **FR-025**: Das System MUSS eine Auslösung abweisen, wenn die Fähigkeit auf Cooldown ist oder die
  globale Sperre läuft, mit derselben Kostenfreiheit.
- **FR-026**: Cooldowns MÜSSEN **zeitstempelbasiert lazy** ausgewertet werden. Es gibt keine
  wiederkehrende Aufgabe je Spieler und kein Herunterzählen je Tick (Prinzip II).
- **FR-027**: Der Cooldown einer Fähigkeit MUSS um die Cooldown-Reduktion des Auslösers verkürzt
  werden, hart gedeckelt bei 40 % (ADR-008).
- **FR-028**: Nach jeder **wirksam gewordenen** aktiven Fähigkeit MUSS für die konfigurierte globale
  Sperre jede andere aktive Fähigkeit desselben Spielers abgewiesen werden. Die Sperre ist ein
  Konfigurationswert.
- **FR-029**: Die globale Sperre MUSS beim **Beginn** einer Auslösung greifen, nicht erst bei ihrer
  Wirkung — sonst wäre sie durch Fähigkeiten mit Wirkzeit umgehbar.
- **FR-030**: Der Einzel-Cooldown MUSS in dem Moment beginnen, in dem die Fähigkeit **wirkt**. Eine
  abgebrochene Fähigkeit startet keinen Cooldown.
- **FR-031**: Cooldown-Zeitstempel MÜSSEN je Charakter gespeichert werden und einen Neustart und ein
  Wiederanmelden überstehen; ein abgelaufener Cooldown MUSS beim Laden verworfen statt geladen werden.
- **FR-032**: Das System DARF NICHT je Spielereignis auf die Datenbank zugreifen. Cooldown-Änderungen
  laufen über denselben gepufferten Schreibweg wie der übrige Charakterzustand (Prinzip II).

### Functional Requirements — Runtime: Mana

- **FR-033**: Mana MUSS mit einer konstanten Rate regenerieren, im Kampf mit einer reduzierten Rate.
  Beide Raten sind Konfiguration.
- **FR-034**: Die Regeneration MUSS zeitstempelbasiert lazy gerechnet werden: aus der verstrichenen
  Zeit seit der letzten Abrechnung, nicht aus einer periodischen Aufgabe.
- **FR-035**: Bei einem Wechsel des Kampfzustands MUSS die bis dahin aufgelaufene Regeneration
  abgerechnet werden, damit jedes Intervall genau eine Rate hat.
- **FR-036**: Ob ein Spieler im Kampf ist, MUSS aus dem Kampfzustand von B05 gelesen werden. Der Block
  führt keinen zweiten Zähler.
- **FR-037**: Mana MUSS bei jedem Lesen und vor jeder Kostenprüfung abgerechnet sein, damit die Antwort
  „zu wenig Mana" nie an einer ausstehenden Abrechnung liegt.
- **FR-038**: Die Regeneration MUSS über die Abwesenheit eines Spielers hinweg gelten, ohne dass
  währenddessen etwas gelaufen ist.

### Functional Requirements — Runtime: Casting und Unterbrechung

- **FR-039**: Eine Fähigkeit mit Wirkzeit größer null MUSS einen Cast-Zustand erzeugen; ihre Wirkung
  tritt am Ende der Wirkzeit ein.
- **FR-040**: Ein Spieler MUSS höchstens einen laufenden Cast haben. Eine zweite Auslösung während
  eines Casts wird abgewiesen.
- **FR-041**: Die Kosten MÜSSEN beim **Beginn** des Casts abgebucht und bei Abbruch **vollständig
  erstattet** werden. Ein abgebrochener Cast kostet nichts.
- **FR-042**: Ein laufender Cast MUSS abbrechen bei: erlittenem Schaden größer null nach Mitigation,
  Wechsel des Hotbar-Slots, Tod, Charakterwechsel und Verbindungsverlust.
- **FR-043**: Eine Definition MUSS angeben können, dass Bewegung ihren Cast abbricht; ohne Angabe
  bricht Bewegung nicht ab.
- **FR-044**: Wirkzeit 0 MUSS ohne Cast-Zustand im selben Tick wirken.
- **FR-045**: Der Cast-Zustand DARF NICHT an eine wiederkehrende Aufgabe je Spieler gebunden sein. Ein
  Spieler ohne laufenden Cast hat keine geplante Arbeit.

### Functional Requirements — Passive und Trigger

- **FR-046**: Das System MUSS die folgenden Trigger bereitstellen: **ALWAYS** (Dauerwirkung),
  **ON_DAMAGE_DEALT**, **ON_DAMAGE_TAKEN**, **ON_KILL**, **ON_DEATH**.
- **FR-047**: Eine passive Fähigkeit MUSS ohne Auslösung und ohne Mana-Kosten wirken.
- **FR-048**: Eine passive Fähigkeit MUSS einen eigenen Cooldown tragen können — geprüft wie bei
  aktiven, aber ohne Auslösung durch den Spieler.
- **FR-049**: Eine passive Fähigkeit MUSS eine Wahrscheinlichkeit tragen können; sie wird bei jedem
  Auftreten ihres Triggers geprüft.
- **FR-050**: Der Trigger **ON_DEATH** MUSS den tödlichen Schaden abfangen können, bevor der Tod
  eintritt, und dem Charakter stattdessen einen konfigurierten Anteil seiner Gesundheit geben.
- **FR-051**: **ON_DEATH** DARF NICHT gegen die administrative Tötung greifen.
- **FR-052**: Eine passive Fähigkeit mit Dauerwirkung MUSS ihren Beitrag als Modifikatorquelle
  anmelden und ihn bei Verlust der Freischaltung oder beim Charakterwechsel wieder entfernen.

### Functional Requirements — Eingabe und Hotbar

- **FR-053**: Aktive Fähigkeiten MÜSSEN ausschließlich über **Rechtsklick auf ihrem Hotbar-Slot**
  auslösbar sein. Es gibt keine eigenen Keybinds und keine Client-Voraussetzung (ADR-005).
- **FR-054**: Ein Linksklick mit einem Fähigkeits-Item DARF weder die Fähigkeit noch einen
  Nahkampfangriff auslösen.
- **FR-055**: Das System MUSS die Belegung der Hotbar festlegen: **Slot 0** trägt die gebundene Waffe
  aus B07, **Slots 1 bis 4** tragen die vier aktiven Fähigkeiten in der Reihenfolge ihrer
  Freischaltstufe, **Slots 5 aufwärts** tragen die Marker-Items passiver Fähigkeiten, sofern sie
  welche haben. Die übrigen Slots gehören dem Spieler.
- **FR-056**: Ein Slot einer noch nicht freigeschalteten Fähigkeit MUSS leer bleiben und darf sich
  nicht befüllen lassen.
- **FR-057**: Fähigkeits- und Marker-Items MÜSSEN charaktergebunden sein: nicht bewegbar, nicht
  ablegbar, nicht werfbar, nicht zerstörbar (ADR-018).
- **FR-058**: Fähigkeits-Items MÜSSEN reine Eingabemethode sein und keine Logik tragen. Wer ein
  solches Item auf anderem Weg erhielte, bekäme damit keine Fähigkeit.
- **FR-059**: Das System MUSS die Slots beim Aktivieren eines Charakters und bei jeder Freischaltung
  gegen den aktuellen Stand setzen.
- **FR-060**: Erreicht ein Charakter die Freischaltstufe einer Fähigkeit, MUSS er darüber unterrichtet
  werden.

### Functional Requirements — Freischaltung und Rang

- **FR-061**: Die Freischaltung MUSS allein aus dem Level abgeleitet werden. Es wird kein
  Freischaltzustand gespeichert (setzt B07s Ableitung fort) und es gibt keinen Skilltree.
- **FR-062**: Jede freigeschaltete Fähigkeit MUSS einen Rang haben, beginnend bei 1, bis zu einem
  konfigurierten Höchstrang.
- **FR-063**: Der Rang MUSS die Zahlen der Fähigkeit entlang einer konfigurierten Kurve skalieren.
- **FR-064**: Der Rang MUSS **je Charakter** gespeichert werden, nicht je Konto (ADR-011), und einen
  Neustart überstehen.
- **FR-065**: Das System MUSS eine Schnittstelle zum Weiterschalten eines Rangs anbieten, die den
  Höchstrang durchsetzt. **Wer den Aufstieg bezahlt, wird hier nicht entschieden** — es gibt im
  Projekt keine Währung. Die Schnittstelle bleibt zunächst Verwaltung und Tests vorbehalten
  (Workflow-Regel 5).

### Functional Requirements — Auskunft für andere Blöcke

- **FR-066**: Das System MUSS eine öffentliche Schnittstelle anbieten, über die andere Blöcke
  erfahren: welche Fähigkeiten ein Charakter freigeschaltet hat, deren Rang, deren Restcooldown und ob
  die globale Sperre läuft. B13 zeichnet daraus, B12 zählt daraus.
- **FR-067**: Die Auskunft DARF NICHT rechnen — sie liest, was ohnehin da ist.
- **FR-068**: Das System DARF NICHT an Interna anderer Blöcke vorbeigreifen und MUSS Schaden
  ausschließlich über die Schnittstelle von B05 erzeugen (Prinzip III).

### Functional Requirements — Leistung

- **FR-069**: Der Block MUSS im Normalbetrieb ein Tick-Budget von ≤ 5 ms einhalten (Prinzip II).
- **FR-070**: Es DARF keine wiederkehrende Aufgabe je Spieler oder je Entity geben. Zeitbasierte
  Werte laufen über Zeitstempel; der einzige geplante Ablauf ist die Wirkung eines laufenden Casts.
- **FR-071**: Der Block DARF im Hot Path nicht je Ereignis allokieren, wo es vermeidbar ist —
  insbesondere nicht in der Zielsuche.

### Key Entities

- **Ability**: die Definition einer Fähigkeit — Kennung, Art, Anzeigename, Kosten, Cooldown, Wirkzeit,
  optionaler Trigger, Zielbestimmung, Effektliste, Rangkurve, optionales Item. Unveränderlich und für
  den ganzen Server einmal vorhanden, wie die Klassendefinitionen aus B07.
- **EffectSpec**: ein Baustein innerhalb einer Ability — Art des Primitives plus dessen Parameter.
- **TargetSpec**: wie eine Ability ihre Ziele findet — Modus, Reichweite, Winkel oder Radius,
  Zielobergrenze.
- **AbilityTrigger**: das Ereignis, an dem eine passive Fähigkeit hängt.
- **AbilityState**: was einem Charakter je Fähigkeit gehört — Rang und Cooldown-Ablaufzeitpunkt.
  Gehört dem Charakter, nicht dem Konto.
- **CastState**: ein laufender Cast — Fähigkeit, Beginn, Wirkzeitpunkt, gebuchte Kosten,
  Unterbrechungsregeln. Existiert nur, solange gecastet wird.
- **AbilityConfig**: die geladene und geprüfte Gesamtkonfiguration — alle Abilities, die globale
  Sperre, die beiden Mana-Raten.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Eine neue Fähigkeit aus vorhandenen Primitives entsteht **rein per Konfiguration** —
  ohne dass eine Quelldatei angefasst wird. Nachgewiesen an einer Fähigkeit, die nur im Test
  existiert.
- **SC-002**: 100 gleichzeitig wirkende Flächenfähigkeiten bleiben im Tick-Budget von 5 ms.
- **SC-003**: Ein Spieler mit zu wenig Mana löst in 0 von 1000 Versuchen aus; ebenso ein Spieler auf
  Cooldown und ein Spieler in der globalen Sperre. Alle drei Regeln werden serverseitig durchgesetzt.
- **SC-004**: Nach Abmelden und Wiederanmelden weicht ein laufender Cooldown um höchstens eine Sekunde
  von der tatsächlich verstrichenen Zeit ab.
- **SC-005**: Über eine Stunde Spielbetrieb mit 150 Spielern läuft **keine** wiederkehrende Aufgabe je
  Spieler für Cooldowns oder Mana; die Zahl der geplanten Aufgaben entspricht der Zahl der gerade
  laufenden Casts.
- **SC-006**: Jede der drei Klassen hat genau sechs Fähigkeiten, vier aktiv, zwei passiv, genau eine
  Unique — durchgesetzt beim Start, nicht bloß dokumentiert.
- **SC-007**: Ein Flächeneffekt trifft nie mehr Ziele als seine Obergrenze, auch nicht bei 200
  Kandidaten im Radius.
- **SC-008**: Eine fehlerhafte Konfiguration verhindert den Start in 100 % der geprüften Fälle und
  nennt dabei Fähigkeit und Feld.
- **SC-009**: Ein unterbrochener Cast hinterlässt in 1000 Versuchen keine Manadifferenz und keinen
  Cooldown.
- **SC-010**: Eine Ausnahme in einem Effekt beendet weder das Ereignis der übrigen Effekte noch die
  Sitzung des Spielers.

## Ausgearbeiteter Inhalt

Die folgenden Zahlen sind Ausgangspunkt für das Balancing und jederzeit über Konfiguration änderbar
(Prinzip V). Die Rollenprofile stammen aus B07: Warrior ist Nahkampf und Zähigkeit, Rogue ist
Geschwindigkeit und Beweglichkeit, Mage ist Mana, Magieschaden und Cooldown-Reduktion.

### Freischaltstufen

Alle drei Klassen folgen demselben Raster: **1, 5, 15, 25, 35, 45**. Die Unique ist immer die letzte.
Damit steht das vollständige Loadout deutlich vor Maximallevel 60 zur Verfügung, und die
Ausrüstungsleitern aus B07 bleiben der Teil der Progression, der bis 55 weiterläuft.

### Warrior — festgelegt in B07

| Stufe | Fähigkeit | Art | Zielbestimmung | Kern |
|---|---|---|---|---|
| 1 | Wut | passiv, `ON_DAMAGE_TAKEN` | Selbst | Buff auf physischen Schaden, der mit sinkender Gesundheit steigt |
| 5 | Schild | aktiv | Selbst | Shield — absorbiert eine Menge Schaden für eine Dauer |
| 15 | Sprung | aktiv | Blickrichtung | Dash nach vorn, Knockback am Landepunkt |
| 25 | Lifesteal | passiv, `ON_DAMAGE_DEALT` | Selbst | Lifesteal — Anteil des zugefügten Schadens als Heilung |
| 35 | Wirbel | aktiv | Radius um den Auslöser | Damage physisch, Zielobergrenze, Knockback |
| 45 | **Call of the Berserker** | aktiv, **Unique** | Selbst | Buff auf physischen Schaden **und** Verteidigung für eine Dauer. Item: Goat Horn |

### Rogue — hier ausgearbeitet

Die Unique steht fest: Second Life, Totem, passiv, prozentuale Wiederbelebungschance. Die übrigen fünf
sind aus dem Rollenprofil abgeleitet — Beweglichkeit, Einzelziel, kein Flächenschaden.

| Stufe | Fähigkeit | Art | Zielbestimmung | Kern |
|---|---|---|---|---|
| 1 | Ausweichen | passiv, `ON_DAMAGE_TAKEN` | Selbst | Wahrscheinlichkeit, den Schaden vollständig zu vermeiden |
| 5 | Schattenschritt | aktiv | Blickrichtung | Teleport über eine kurze Strecke, Wirkzeit 0 |
| 15 | Wurfmesser | aktiv | Blickrichtung | Projectile mit physischem Damage auf Trefferziel |
| 25 | Schwächender Schnitt | aktiv | Cursor-Ziel | Debuff auf die Verteidigung des Ziels für eine Dauer |
| 35 | Hinterhalt | aktiv | Selbst | Buff auf physischen Schaden und Angriffsgeschwindigkeit, kurze Wirkzeit |
| 45 | **Second Life** | passiv, **Unique**, `ON_DEATH` | Selbst | Wahrscheinlichkeit, statt zu sterben mit einem Anteil der Gesundheit wieder aufzustehen. Eigener langer Cooldown. Marker: Totem |

### Mage — hier ausgearbeitet

Die Unique steht fest: Magic Boost & Fall, Wind Charge und Slow Fall Potion, passiv, Doppelsprung und
Slow Fall. Die übrigen fünf spielen das Rollenprofil aus: viel Mana, viel Magieschaden, hohe
Cooldown-Reduktion, Fläche statt Einzelziel.

| Stufe | Fähigkeit | Art | Zielbestimmung | Kern |
|---|---|---|---|---|
| 1 | Arkane Sammlung | passiv, `ON_KILL` | Selbst | ManaRestore bei jedem Tötungsbeitrag |
| 5 | Feuerball | aktiv | Blickrichtung | Projectile mit magischem Damage, kleiner Radius am Einschlag |
| 15 | Frostnova | aktiv | Radius um den Auslöser | Damage magisch plus Debuff auf Bewegungsgeschwindigkeit, Zielobergrenze |
| 25 | Kettenblitz | aktiv | Nächstes Ziel, wiederholt | Damage magisch, springt bis zur Zielobergrenze weiter, je Sprung abnehmend |
| 35 | Manaschild | aktiv | Selbst | Shield, dessen Absorption sich aus dem Mana speist, Wirkzeit größer null |
| 45 | **Magic Boost & Fall** | passiv, **Unique**, `ALWAYS` | Selbst | Zweiter Sprung in der Luft und verlangsamter Fall. Marker: Wind Charge und Slow Fall Potion |

### Hotbar-Belegung

| Slot | Inhalt |
|---|---|
| 0 | gebundene Waffe aus B07 |
| 1–4 | die vier aktiven Fähigkeiten, aufsteigend nach Freischaltstufe; leer, solange nicht freigeschaltet |
| 5 | erstes Marker-Item einer passiven Fähigkeit, sofern vorhanden — Rogue: Totem; Mage: Wind Charge |
| 6 | zweites Marker-Item — Mage: Slow Fall Potion |
| 7–8 | frei für den Spieler |

Der Warrior belegt damit fünf Slots, der Rogue sechs, der Mage sieben. Keine Klasse verliert ihre
Hotbar vollständig; die restlichen 27 Inventarplätze sind ohnehin unberührt.

### Globale Sperre und Mana-Raten — Ausgangswerte

| Wert | Vorschlag | Begründung |
|---|---|---|
| Globale Sperre | 0,75 s | Lang genug, dass „alle vier sofort" nicht funktioniert; kurz genug, dass eine geplante Abfolge aus zwei Fähigkeiten flüssig bleibt |
| Mana-Regeneration außerhalb des Kampfes | 4 % des Maximums je Sekunde | Volles Mana in rund 25 s Ruhe, unabhängig von der Klasse |
| Mana-Regeneration im Kampf | 1,5 % des Maximums je Sekunde | Deutlich spürbar reduziert, aber nicht null — sonst wäre ein langer Kampf allein durch Mana entschieden |

Die Rate ist bewusst **prozentual zum Maximum** und nicht absolut: der Mage hat mit 500 Mana das
Zweieinhalbfache des Warriors, und eine absolute Rate hätte ihm eine anteilig viel langsamere
Erholung gegeben, obwohl Mana sein Rollenprofil ist.

## Assumptions

- **Coins existieren im Projekt nicht** — kein Guthaben, keine Tabelle, kein Verdienstweg. Die
  Rang-Schnittstelle aus FR-065 ist deshalb zunächst nur für Verwaltung und Tests erreichbar, genau
  wie B07s Weiterschalten der Ausrüstungsstufe ohne B11. Das ist keine Lücke, sondern Workflow-Regel 5.
- **Der Rang ist die einzige Skalierungsachse der Fähigkeiten neben den Attributen.** Ein Damage-Wert
  ist ein Faktor auf ein Attribut (FR-013) und wächst dadurch schon mit Level und Ausrüstung; der Rang
  ist der zweite, bezahlte Hebel. Ein dritter wäre nicht mehr nachvollziehbar.
- **Passive Marker-Items sind Anzeige, keine Eingabe.** Das Totem des Rogue und die beiden Items des
  Mage zeigen, dass die Fähigkeit scharf ist. Ein Rechtsklick darauf tut nichts. Sie stehen in der
  Hotbar, weil das der einzige Ort ist, den ein Vanilla-Client ohne Resource Pack dauerhaft zeigt
  (ADR-005).
- **Der Doppelsprung des Mage wird über den Vanilla-Flugumschalter erkannt**, den der Client in der
  Luft sendet — die einzige Möglichkeit ohne eigenen Keybind. Slow Fall ist ein Vanilla-Statuseffekt.
- **„Wut" skaliert über die Gesundheit und wird bei erlittenem Schaden neu bewertet**, nicht laufend.
  Ein dauerhaft mitlaufender Wert hätte eine periodische Auswertung gebraucht und damit Prinzip II
  verletzt.
- **Ausweichen und Second Life sind Wahrscheinlichkeiten, keine Attribute.** Sie folgen derselben
  Begründung wie Lifesteal: als Fähigkeitseffekt brauchen sie kein Attribut, als Attribut hätten sie
  ADR-008 aufgemacht.
- **Der Kettenblitz springt entlang der Zielobergrenze**, nicht entlang einer Reichweite ohne Ende.
  Die Obergrenze ist derselbe Schutz wie bei jedem anderen Flächeneffekt (FR-020).
- **Debuffs wirken über B04-Modifikatoren mit Ablaufzeitpunkt**, nicht über Schaden über Zeit. Ein
  DoT hätte je betroffenem Ziel eine wiederkehrende Auswertung gebraucht; ein Modifikator mit
  Zeitstempel läuft lazy ab. Sollte ein DoT je gewollt sein, ist er ein Primitive mit **einer**
  gemeinsamen Auswertung für alle laufenden Instanzen — nie einer je Ziel.
- **Die Wirkzeiten der ausgelieferten Fähigkeiten sind überwiegend 0.** Die Mechanik ist gebaut
  (ADR-022), aber nur dort eingesetzt, wo sie das Spielgefühl trägt — Manaschild und Hinterhalt. Das
  ist bewusst: eine gebaute und ungenutzte Mechanik ist billiger als eine nachgerüstete.
- **Die Kosten werden bei Castbeginn abgebucht und bei Abbruch erstattet.** Die Alternative — erst bei
  Wirkung abbuchen — hätte erlaubt, einen Cast ohne ausreichendes Mana zu starten und auf Nachschub zu
  hoffen, und hätte die Prüfung zweimal gebraucht.
- **Die Mana-Regeneration liest den Kampfzustand von B05** und führt keinen zweiten. B05 sagt das
  ausdrücklich zu.
- **Das Angriffsfenster gilt nicht für Fähigkeiten** — B05 hat das bereits so entschieden und
  begründet: beides zu prüfen begrenzte sie doppelt.
- **Der Block liefert keine HUD-Darstellung.** Cooldown-Anzeige, Mana-Balken und Cast-Balken sind B13.
  B08 liefert die Auskunft (FR-066), aus der B13 zeichnet — so, wie B07 die Warnung bei vollem
  Inventar hinter einer Schnittstelle gelassen hat.
- **Summon fehlt in der Primitive-Liste**, weil B10 noch nicht existiert. Der Steckbrief nennt es;
  nachrüstbar ist es als weiteres Primitive ohne Architekturänderung.
- **Die Zahl der Slots reicht.** Sieben belegte Slots beim Mage sind das Maximum über alle drei
  Klassen; zwei bleiben frei, das übrige Inventar unberührt.

## Abhängigkeiten

- **B04** liefert den Wertestand, den Mana-Pool und die Modifikatoren, über die Buff und Debuff
  wirken. B08 fügt kein Attribut hinzu (ADR-008, ADR-022).
- **B05** nimmt jeden Schaden entgegen, liefert den Kampfzustand für die Mana-Rate und die
  Einhängepunkte, über die passive Trigger und Lifesteal arbeiten. B08 greift nicht daran vorbei.
- **B06** liefert das Level, aus dem die Freischaltung abgeleitet wird, und das Ereignis beim
  Aufstieg, an dem die Slots nachgezogen werden.
- **B07** liefert die Klassenbindung: Fähigkeits-ID, Art, Unique-Kennzeichen und Freischaltstufe. B08
  löst die IDs auf und füllt die heute leeren Bindungen. Die Invariante `unique ⇒ ACTIVE` in B07 wird
  im Zuge dieses Blocks entfernt (ADR-022).
- **B11** entscheidet, wer den Rangaufstieg bezahlt. B08 liefert nur die Schnittstelle.
- **B12** zählt Fähigkeitsnutzung, sobald es existiert. B08 liefert die Auskunft.
- **B13** zeichnet Cooldowns, Mana und den Cast-Fortschritt. B08 liefert die Auskunft.
