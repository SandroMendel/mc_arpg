# Feature Specification: B04 · Attribut- & Stat-Engine

**Feature Branch**: `004-stat-engine`

**Created**: 2026-08-19

**Status**: Draft

**Input**: Blocksteckbrief `blocks/B04-stat-engine.md` — das zentrale Vertragswerk des Spiels:
wie aus Klasse, Level, Ausrüstung und Effekten die konkreten Werte eines Spielers oder Mobs
entstehen. Baut auf B01 und B03 auf, wird von B05, B06, B07, B08, B10, B11 und B13 benötigt.
Verbindlich: ADR-003 (eigenes HP-System), ADR-004 (Ausrüstung ist Stat-Quelle), ADR-008
(Stat-Engine-Grundformeln).

## Clarifications

### Session 2026-08-20

- Q: Wo gehören der aktuelle Lebens- und Manastand hin, und werden sie über eine Sitzung hinaus gespeichert? → A: B04 führt den Behälter und sichert ihn über den Write-Behind-Pfad aus B02/B03; dafür entsteht eine eigene Migration mit zwei Feldern am Charakter.
- Q: Wer schaltet die Vanilla-Gesundheitsmechanik ab? → A: B04 schaltet natürliche Regeneration ab und fixiert die Sättigung, damit nur die Engine die Herzleiste verändert. Das Umlenken echter Vanilla-Schadensquellen (Fall, Feuer, Lava, Void) bleibt bei B05.
- Q: Wie offen soll der Satz der Attribute sein? → A: Geschlossener, benannter Satz von acht Attributen. Ein neuntes hinzuzufügen ist eine Ergänzung an genau einer Stelle plus Konfigurationseintrag — ohne Änderung an Berechnung, Modifier-Modell oder Schnappschuss. Keine Laufzeit-Registratur.
- Q: Sind die Grenzwerte aus ADR-008 (Abklingzeit-Cap 40 %, attackSpeed ±50 %, movementSpeed ±30 %) fest oder konfigurierbar? → A: Konfigurierbar, aber als Pflichtfelder im Schema. Fehlt eine Angabe oder ist sie unplausibel (Untergrenze ≥ Obergrenze, Prozentwert ausserhalb des erlaubten Bereichs), bricht der Start ab — ein unbegrenzter Wert entsteht nie.
- Q: Wie wird sichergestellt, dass ein Ausrüstungswechsel über mehrere Slots genau eine Neuberechnung auslöst? → A: Automatisch. Eine Änderung setzt nur eine Vormerkung am Träger; die erste Änderung plant genau eine Aufgabe für diesen Träger, jede weitere findet die Vormerkung bereits gesetzt. Kein aufrufender Block muss die Bündelung selbst klammern. Maßgeblich sind Ladepfad, Respawn, Levelaufstieg und gleichzeitig ablaufende Buffs — nicht der Tausch eines einzelnen Teils im laufenden Spiel. *(Umsetzungsdetail nachgetragen 2026-08-20: trägergebundene Einmalaufgabe statt eines serverweiten Durchlaufs am Tick-Ende — ein globaler, wiederkehrender Durchlauf verstieße gegen Prinzip I und II; siehe research.md E4.)*

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ein Spieler hat acht nachvollziehbare Werte (Priority: P1)

Als Spieler habe ich acht Attribute — Leben, Verteidigung, Mana, physischer Schaden, magischer
Schaden, Angriffsgeschwindigkeit, Bewegungsgeschwindigkeit und Abklingzeitreduktion. Jeder Wert
entsteht aus einem Basiswert und allen Beiträgen, die gerade auf mich wirken, nach einer einzigen,
für alle acht Attribute identischen Regel.

**Why this priority**: Ohne das generische Attributmodell und die Berechnungsregel hat kein anderer
Block eine Grundlage. B05 braucht Schadens- und Verteidigungswerte, B06 einen Angriffspunkt für
Levelzuwachs, B07 für Klassenbasiswerte, B08 für Mana und Abklingzeit, B11 für Ausrüstung, B13 für
die Anzeige. Dies ist der minimal lauffähige Kern des Blocks.

**Independent Test**: Einen Stat-Träger mit Basiswerten anlegen, einzelne Beiträge hinzufügen und
prüfen, dass jedes der acht Attribute exakt nach `(Basis + Summe Flat) × (1 + Summe Prozent)`
berechnet und anschließend auf seinen erlaubten Bereich begrenzt wird — vollständig ohne laufenden
Server.

**Acceptance Scenarios**:

1. **Given** ein Stat-Träger ohne jeden Beitrag, **When** seine Werte abgefragt werden, **Then**
   entspricht jedes der acht Attribute exakt seinem konfigurierten Basiswert.
2. **Given** ein Attribut mit Basis 100, **When** ein Flat-Beitrag von +50 und ein Prozent-Beitrag
   von +20 % wirken, **Then** ist der Endwert 180.
3. **Given** ein Attribut mit zwei Prozent-Beiträgen von je +50 %, **When** der Wert berechnet wird,
   **Then** werden die Prozentwerte aufsummiert und einmal angewandt (× 2,0), nicht sequenziell
   verkettet (× 2,25).
4. **Given** ein Attribut mit einer konfigurierten Obergrenze, **When** die Beiträge diese
   überschreiten, **Then** liefert die Abfrage die Obergrenze und nicht den Rohwert.
5. **Given** negative Beiträge, deren Prozentsumme unter −100 % liegt, **When** der Wert berechnet
   wird, **Then** liefert die Abfrage die konfigurierte Untergrenze des Attributs und niemals einen
   negativen Wert.
6. **Given** die Abklingzeitreduktion, **When** die Beiträge zusammen mehr als 40 % ergeben,
   **Then** ist der wirksame Wert genau 40 %.
7. **Given** eine Verteidigung von 300, **When** die Schadensminderung ermittelt wird, **Then**
   ergibt sie exakt 75 % — passend zum Divisor-Modell `100/(100+def)`.

---

### User Story 2 - Ausrüstung an- und ablegen verändert Werte verlustfrei (Priority: P1)

Als Spieler lege ich ein Ausrüstungsteil an und meine Werte steigen. Lege ich es wieder ab, stehe
ich exakt dort, wo ich vorher war — kein Rest bleibt hängen, kein Wert driftet über viele Wechsel
hinweg davon.

**Why this priority**: Gleichrangig mit User Story 1. Ein Modifikatormodell ohne verlässliches
Entfernen erzeugt schleichend falsche Spielerwerte, die weder auffallen noch reparierbar sind.
ADR-004 macht Ausrüstung zur dominanten Stat-Quelle — Fehler hier wirken sich auf jeden Kampf aus.

**Independent Test**: Beiträge einer Quelle hinzufügen, den Endwert prüfen, die Quelle wieder
entfernen und prüfen, dass der Ausgangswert exakt wiederhergestellt ist; das Ganze tausendfach
wiederholt ohne Abweichung.

**Acceptance Scenarios**:

1. **Given** ein Stat-Träger im Ausgangszustand, **When** eine Quelle mit mehreren Beiträgen
   hinzugefügt und wieder entfernt wird, **Then** entsprechen alle acht Attribute exakt dem
   Ausgangszustand.
2. **Given** derselbe Rundlauf, **When** er 1000-mal hintereinander ausgeführt wird, **Then** ist
   der Endwert weiterhin exakt der Ausgangswert, ohne aufsummierte Rundungsdrift.
3. **Given** mehrere Quellen wirken gleichzeitig, **When** genau eine davon entfernt wird, **Then**
   verschwinden ausschließlich deren Beiträge; alle anderen bleiben unverändert wirksam.
4. **Given** ein vollständiger Ausrüstungswechsel über mehrere Slots, **When** er als ein Vorgang
   ausgeführt wird, **Then** findet genau **eine** Neuberechnung statt, nicht eine je Slot.
5. **Given** eine Quelle, die gar nicht wirkt, **When** ihr Entfernen angefordert wird, **Then**
   passiert nichts und es wird keine Neuberechnung ausgelöst.
6. **Given** eine bereits registrierte Quellen-ID, **When** dieselbe ID erneut mit anderen Werten
   registriert wird, **Then** ersetzt der neue Beitragssatz den alten vollständig, statt sich zu
   addieren.

---

### User Story 3 - Die Werte sind sofort verfügbar und blockieren nie den Server (Priority: P1)

Als Betreiber erwarte ich, dass die Stat-Engine bei 200 gleichzeitigen Spielern im Leerlauf messbar
keine Tick-Zeit verbraucht und dass ein laufender Vorgang — etwa ein fliegendes Projektil — mit den
Werten von seinem Auslösezeitpunkt rechnet, nicht mit zwischenzeitlich geänderten.

**Why this priority**: Prinzip II verbietet periodische Arbeit je Spieler; ohne diese Eigenschaft
ist der Block auf einem vollen Server unbrauchbar. Der unveränderliche Schnappschuss ist zugleich
die Voraussetzung dafür, dass B05 ohne Sperren mit Werten arbeiten kann.

**Independent Test**: 200 Träger mit je ~20 Beiträgen anlegen, über viele Ticks keine Änderung
auslösen und nachweisen, dass keine Neuberechnung und keine wiederkehrende Aufgabe stattfindet;
zusätzlich einen Schnappschuss ziehen, danach Beiträge ändern und prüfen, dass der gezogene
Schnappschuss unverändert bleibt.

**Acceptance Scenarios**:

1. **Given** 200 Träger mit je acht Attributen und je ~20 Beiträgen, **When** über 1200 Ticks keine
   Quelle sich ändert, **Then** findet keine einzige Neuberechnung statt.
2. **Given** ein gezogener Schnappschuss, **When** anschließend Beiträge hinzugefügt oder entfernt
   werden, **Then** liefert der bereits gezogene Schnappschuss weiterhin die alten Werte.
3. **Given** ein Schnappschuss, **When** ein Aufrufer versucht, ihn zu verändern, **Then** ist das
   nicht möglich — der Schnappschuss ist unveränderlich.
4. **Given** 100 Träger ändern im selben Tick ihre Quellen, **When** alle neu berechnet werden,
   **Then** bleibt der Zeitverbrauch dieses Ticks unter dem Subsystembudget von 5 ms.
5. **Given** ein Wert wird abgefragt, **When** sich seit der letzten Änderung nichts geändert hat,
   **Then** wird kein Rechenaufwand betrieben, sondern der zwischengespeicherte Schnappschuss
   geliefert.

---

### User Story 4 - Die Herzleiste zeigt zuverlässig den Gesundheitsanteil (Priority: P2)

Als Spieler sehe ich an der Vanilla-Herzleiste jederzeit, wie viel Prozent meines Lebens ich noch
habe — unabhängig davon, ob mein Maximum 100 oder 2000 beträgt. Meine Laufgeschwindigkeit und
Angriffsgeschwindigkeit fühlen sich genau so an, wie meine Werte es sagen.

**Why this priority**: ADR-003 und ADR-008 verlangen die Spiegelung, und ohne sie ist das eigene
HP-System für den Spieler unsichtbar. Sie ist jedoch erst sinnvoll, wenn Modell und Berechnung
(US1, US2) stehen.

**Independent Test**: Einen Träger mit bekanntem Maximum und bekanntem aktuellem Leben aufsetzen
und prüfen, dass der angezeigte Vanilla-Wert `aktuell / maximal × 20` entspricht; anschließend
Bewegungs- und Angriffsgeschwindigkeit ändern und die Spiegelung zum jeweiligen Vanilla-Attribut
prüfen.

**Acceptance Scenarios**:

1. **Given** ein Spieler mit maximal 1000 und aktuell 500 Leben, **When** die Anzeige ermittelt
   wird, **Then** zeigt die Herzleiste 10 von 20 Punkten.
2. **Given** ein beliebiger Spieler, **When** sein maximales Leben sich ändert, **Then** bleibt das
   Vanilla-Maximum unverändert bei 20 Punkten.
3. **Given** ein Spieler mit sehr wenig verbleibendem Leben, **When** die Anzeige rechnerisch auf
   null Punkte fiele, **Then** wird stattdessen der kleinstmögliche Wert oberhalb von null
   angezeigt, solange der Spieler lebt.
4. **Given** ein Spieler mit null aktuellem Leben, **When** die Anzeige ermittelt wird, **Then**
   zeigt sie null Punkte.
5. **Given** eine Änderung an Bewegungs- oder Angriffsgeschwindigkeit, **When** die Neuberechnung
   erfolgt, **Then** wird im selben Vorgang das zugehörige Vanilla-Attribut gesetzt und die Anzeige
   benachrichtigt — nicht in einem separaten, späteren Durchlauf.
6. **Given** eine Neuberechnung findet außerhalb des Server-Ticks statt, **When** Vanilla-Attribute
   gesetzt werden sollen, **Then** geschieht das ausschließlich im Tick des betroffenen Spielers.

---

### User Story 5 - Leben und Mana sind belastbare Ressourcen (Priority: P2)

Als Spieler habe ich neben dem Maximum auch einen aktuellen Stand an Leben und Mana. Steigt mein
Maximum durch ein Ausrüstungsteil, verliere ich nichts; sinkt es, bleibe ich am neuen Maximum und
sterbe nicht durch den Wechsel. Beim nächsten Anmelden finde ich meinen Stand wieder vor.

**Why this priority**: B05 (Schaden) und B08 (Fähigkeitskosten) brauchen einen definierten
Ressourcenbehälter; ohne ihn hat die Herzleiste aus US4 keinen Zähler. B04 stellt den Behälter und
die Klemmregeln, nicht die Regeln, wann sich etwas verbraucht.

**Independent Test**: Ressourcenstand setzen, Maximum nach oben und nach unten verändern und
prüfen, dass der aktuelle Stand nach den festgelegten Regeln geklemmt wird; Sitzung beenden, neu
laden und prüfen, dass der Stand erhalten bleibt.

**Acceptance Scenarios**:

1. **Given** ein Spieler mit 500 von 1000 Leben, **When** sein Maximum auf 1200 steigt, **Then**
   bleibt sein aktuelles Leben bei 500.
2. **Given** ein Spieler mit 900 von 1000 Leben, **When** sein Maximum auf 800 sinkt, **Then**
   beträgt sein aktuelles Leben 800 und er stirbt nicht durch diesen Vorgang.
3. **Given** ein Spieler mit einem Ressourcenstand, **When** ein Verbrauch ihn unter null bringen
   würde, **Then** ist der Stand null und nicht negativ.
4. **Given** ein neu angelegter Charakter, **When** er erstmals betreten wird, **Then** sind Leben
   und Mana auf ihrem jeweiligen Maximum.
5. **Given** ein Spieler mit einem Ressourcenstand, **When** er den Server verlässt und später
   zurückkehrt, **Then** findet er denselben Stand vor, höchstens um ein Autosave-Intervall
   veraltet.
6. **Given** ein Spieler, dessen Sitzung noch nicht bereit ist, **When** seine Werte oder
   Ressourcen abgefragt werden, **Then** erhält der Aufrufer die Auskunft "Sitzung noch nicht
   bereit" und keine Standardwerte.

---

### User Story 6 - Dieselbe Engine trägt auch Mobs (Priority: P3)

Als Entwickler von B10 lege ich einen Mob mit denselben acht Attributen an, ohne dass dafür ein
zweites Wertesystem existiert. Ein Mob hat keine Sitzung, keine Ausrüstungsslots und keine
Anzeige — aber dieselbe Berechnung.

**Why this priority**: Verhindert, dass B10 später ein paralleles Zweitsystem baut. Erst sinnvoll
umsetzbar, wenn das Modell (US1, US2) steht; für B04 selbst noch nicht spielentscheidend.

**Independent Test**: Einen Stat-Träger ohne Spielerbezug anlegen, Beiträge hinzufügen und
denselben Berechnungsweg mit denselben Ergebnissen nachweisen wie beim Spieler.

**Acceptance Scenarios**:

1. **Given** ein Stat-Träger ohne Spielerbezug, **When** Basiswerte und Beiträge gesetzt werden,
   **Then** gelten dieselben Formeln, Grenzen und Schnappschussregeln wie beim Spieler.
2. **Given** ein solcher Träger, **When** er entfernt wird, **Then** werden alle zugehörigen
   Beiträge und Schnappschüsse mit entfernt und hinterlassen keinen Speicherrest.
3. **Given** 800 gleichzeitig aktive Träger ohne Spielerbezug, **When** keine Quelle sich ändert,
   **Then** entsteht dadurch keine wiederkehrende Arbeit je Träger.

---

### User Story 7 - Balancing ohne Codeänderung (Priority: P3)

Als Betreiber ändere ich Basiswerte, Ober- und Untergrenzen je Attribut in einer
Konfigurationsdatei. Ist die Datei fehlerhaft, sagt mir der Server beim Start klar, was falsch ist,
statt mit erfundenen Werten weiterzulaufen.

**Why this priority**: Prinzip V verlangt datengetriebenes Balancing. Der Block funktioniert auch
mit fest hinterlegten Ausgangswerten, aber ohne Konfigurierbarkeit erzeugt jede Balancing-Runde
eine Codeänderung.

**Independent Test**: Konfiguration mit veränderten Grenzwerten laden und prüfen, dass die
Berechnung sie verwendet; anschließend eine ungültige Konfiguration laden und prüfen, dass der
Start mit klarer Meldung abgebrochen wird.

**Acceptance Scenarios**:

1. **Given** eine gültige Attributkonfiguration, **When** der Server startet, **Then** verwenden
   alle Berechnungen die konfigurierten Basiswerte und Grenzen.
2. **Given** eine Konfiguration mit einer Untergrenze oberhalb der Obergrenze, **When** der Server
   startet, **Then** bricht der Start mit einer Meldung ab, die Attribut und Feld benennt.
3. **Given** eine Konfiguration, in der eines der acht Attribute fehlt, **When** der Server startet,
   **Then** bricht der Start mit einer Meldung ab, die das fehlende Attribut benennt.
4. **Given** ein laufender Server, **When** eine ungültige Konfiguration nachgeladen wird, **Then**
   bleibt der zuletzt gültige Stand wirksam und der Fehler wird gemeldet.

---

### Edge Cases

- **Prozentsumme unter −100 %**: Der Endwert wird auf die Untergrenze des Attributs geklemmt, nie
  negativ. Ein Wert, der als Divisor dient, hat eine Untergrenze größer null.
- **Maximales Leben würde null**: Untergrenze für maximales Leben ist 1; ein Träger kann weder
  durch Konfiguration noch durch Modifikatoren ein Maximum von null erhalten.
- **Aktuelles Leben oberhalb des neuen Maximums**: wird auf das Maximum geklemmt, ohne Todesfolge.
- **Doppelte Quellen-ID**: Ein erneutes Registrieren derselben Quelle ersetzt deren Beiträge
  vollständig; es entsteht kein Doppelbeitrag.
- **Beitrag auf ein unbekanntes Attribut**: wird abgelehnt und protokolliert, statt still verworfen
  oder als neues Attribut angelegt zu werden.
- **Entfernen einer nicht vorhandenen Quelle**: kein Fehler, keine Neuberechnung.
- **Neuberechnung während laufender Kampfhandlung**: der zuvor gezogene Schnappschuss bleibt
  gültig; das Ergebnis der laufenden Handlung ändert sich nicht rückwirkend.
- **Sitzung nicht bereit oder bereits beendet**: Abfragen werden mit "nicht bereit" beantwortet,
  Beitragsänderungen werden abgelehnt statt ins Leere geschrieben.
- **Träger wird entfernt, während eine Vormerkung aussteht**: die Vormerkung verfällt folgenlos; die
  bereits geplante Aufgabe stellt fest, dass es den Träger nicht mehr gibt, und rechnet nichts.
- **Abfrage zwischen Änderung und Neuberechnung**: der Aufrufer erhält den zuletzt gültigen
  Schnappschuss. Ein Wert kann damit höchstens einen Tick alt sein — dasselbe Zugeständnis, das
  FR-021 für laufende Vorgänge ohnehin macht. Vor der Freigabe eines Spielers gilt das nicht
  (FR-019b).
- **Rundung an der Anzeige**: Interne Werte werden nicht gerundet; gerundet wird ausschließlich bei
  der Darstellung, damit sich kein Rundungsfehler über Rundläufe aufsummiert.
- **Angriffs- und Bewegungsgeschwindigkeit außerhalb des erlaubten Bandes**: Beiträge über ±50 %
  bzw. ±30 % hinaus werden auf das Band geklemmt, nicht abgelehnt.
- **Ausnahme in einem Beitragslieferanten**: betrifft nur diesen Träger; andere Träger und der Tick
  laufen weiter (Prinzip VI).

## Requirements *(mandatory)*

### Functional Requirements

#### Attributmodell

- **FR-001**: Das System MUSS genau acht Attribute führen — `health`, `defense`, `mana`,
  `physicalDamage`, `magicDamage`, `attackSpeed`, `movementSpeed`, `abilityCooldown` — und sie über
  ein einziges gemeinsames Modell behandeln, nicht als acht Sonderfälle.
- **FR-002**: Jedes Attribut MUSS eine Definition besitzen, die Basiswert, Untergrenze, Obergrenze
  und die Art des Wertes (absoluter Wert oder Prozentwert) festlegt.
- **FR-003**: Das System MUSS Attributdefinitionen aus einer versionierten Konfiguration lesen und
  beim Start gegen ein Schema prüfen; ein Fehler MUSS zum Startabbruch mit klarer Meldung führen.
- **FR-004**: Der Satz der Attribute MUSS geschlossen und namentlich bekannt sein; ein weiteres
  Attribut wird an genau einer Stelle ergänzt und um einen Konfigurationseintrag erweitert, ohne
  dass Berechnung, Modifikatormodell oder Schnappschuss dafür geändert werden müssen. Eine
  Anmeldung neuer Attribute zur Laufzeit ist ausdrücklich nicht vorgesehen.
- **FR-004a**: Das System MUSS einen Beitrag oder Konfigurationseintrag für einen unbekannten
  Attributnamen beim Start als Fehler melden, statt ihn stillschweigend anzulegen.

#### Modifikatoren und Quellen

- **FR-005**: Ein Beitrag MUSS aus Zielattribut, Rechenart (`FLAT` oder `PERCENT`), Wert und einer
  Quellen-ID bestehen.
- **FR-006**: Das System MUSS Quellenarten unterscheiden können — mindestens Klasse, Level,
  Ausrüstung, Buff, Aura und Zone — damit Herkunft nachvollziehbar und gezielt entfernbar ist.
- **FR-007**: Das System MUSS alle Beiträge einer Quelle über deren ID in einem Vorgang entfernen
  können, ohne die Beiträge anderer Quellen zu berühren und ohne den Zustand neu aufzubauen.
- **FR-008**: Das System MUSS das erneute Registrieren einer bereits bekannten Quellen-ID als
  vollständiges Ersetzen behandeln.
- **FR-009**: Das System MUSS einen Beitrag auf ein unbekanntes Attribut ablehnen und den Vorfall
  protokollieren.
- **FR-010**: Das System MUSS abfragbar machen, welche Quellen mit welchem Beitrag an einem
  konkreten Attributwert beteiligt sind, damit Admin-Werkzeuge (B14) und Fehlersuche ohne
  Neuberechnung auskommen.

#### Berechnung

- **FR-011**: Das System MUSS jeden Attributwert als `(Basis + Summe aller Flat-Beiträge) ×
  (1 + Summe aller Prozent-Beiträge)` berechnen; Prozentwerte werden aufsummiert und einmal
  angewandt, niemals sequenziell verkettet.
- **FR-012**: Das System MUSS das Ergebnis anschließend auf das konfigurierte Intervall
  [Untergrenze, Obergrenze] des Attributs begrenzen.
- **FR-013**: Das System MUSS für `abilityCooldown` einen harten Cap durchsetzen; sein Wert stammt
  aus der Konfiguration und beträgt im Auslieferungszustand 40 %.
- **FR-014**: Das System MUSS die wirksame Veränderung von `attackSpeed` und `movementSpeed`
  gegenüber dem jeweiligen Basiswert auf ein konfiguriertes Band begrenzen; im
  Auslieferungszustand ±50 % bzw. ±30 %.
- **FR-014a**: Alle Grenzwerte aus FR-012 bis FR-014 MÜSSEN im Konfigurationsschema Pflichtfelder
  sein. Ein fehlender Wert, eine Untergrenze größer oder gleich der Obergrenze oder ein
  Prozentwert außerhalb des erlaubten Bereichs MUSS den Start abbrechen; ein unbegrenzt wirkendes
  Attribut darf nicht entstehen können.
- **FR-015**: Das System MUSS eine reine Funktion zur Schadensminderung bereitstellen, die aus
  Rohschaden und Verteidigung `Rohschaden × 100/(100 + Verteidigung)` ermittelt; sie MUSS bei
  Verteidigung 0 den Rohschaden unverändert lassen und für negative Verteidigung definiert bleiben.
- **FR-016**: Das System MUSS bei gleicher Eingabe stets dasselbe Ergebnis liefern, unabhängig von
  der Reihenfolge, in der Beiträge hinzugefügt wurden.
- **FR-017**: Das System MUSS einen Rundlauf aus Hinzufügen und Entfernen derselben Quelle exakt auf
  den Ausgangswert zurückführen, auch nach beliebig vielen Wiederholungen.

#### Neuberechnung und Schnappschuss

- **FR-018**: Das System MUSS ausschließlich bei Änderung einer Quelle, eines Basiswertes oder der
  Konfiguration neu berechnen und DARF KEINE wiederkehrende Arbeit je Spieler oder je Träger
  anlegen. Ein Tick, in dem sich nichts geändert hat, DARF KEINE Arbeit auslösen — insbesondere
  keinen serverweiten Durchlauf über alle Träger und keine Aufgabe, die nur nachsieht, ob es etwas
  zu tun gibt.
- **FR-019**: Das System MUSS mehrere Änderungen, die im selben Tick an einem Träger auftreten
  (z. B. ein vollständiger Ausrüstungswechsel, das Anlegen des Satzes beim Anmelden, ein Respawn
  oder mehrere gleichzeitig ablaufende Buffs), zu genau einer Neuberechnung zusammenfassen. Eine
  Änderung setzt lediglich eine Vormerkung am betroffenen Träger; die erste Änderung plant genau
  eine Aufgabe für diesen einen Träger, jede weitere findet die Vormerkung bereits gesetzt und
  plant nichts.
- **FR-019a**: Die Bündelung MUSS ohne Zutun des aufrufenden Blocks wirken; kein späterer Block
  darf gezwungen sein, seine Änderungen selbst zu klammern, damit die Zusammenfassung greift.
- **FR-019b**: Das System MUSS gewährleisten, dass ein Träger vor seiner Freigabe an den Spieler
  (Zustand „bereit" aus B03) mindestens einmal berechnet wurde; ein Spieler wird nie mit einer
  ausstehenden Vormerkung freigegeben. Dafür MUSS ein sofortiger, die Bündelung überspringender
  Berechnungsweg zur Verfügung stehen — er dient dem Ladepfad und Trägern ohne Entität, nicht dem
  laufenden Spielbetrieb.
- **FR-020**: Das Ergebnis einer Neuberechnung MUSS ein unveränderlicher Schnappschuss aller acht
  Werte sein, der nach seiner Erzeugung nicht mehr verändert werden kann.
- **FR-021**: Ein bereits gezogener Schnappschuss MUSS gültig bleiben, auch wenn der Träger danach
  neu berechnet wird; laufende Vorgänge rechnen mit dem Stand ihres Auslösezeitpunkts.
- **FR-022**: Das System MUSS eine Abfrage ohne zwischenzeitliche Änderung ohne erneute Berechnung
  beantworten.
- **FR-023**: Das System MUSS bei jeder Neuberechnung ein Ereignis veröffentlichen, das den Träger,
  den vorherigen und den neuen Schnappschuss trägt, damit Anzeige (B13) und weitere Blöcke ohne
  Abfrageschleife reagieren können.
- **FR-024**: Das System MUSS eine Neuberechnung auch dann korrekt abschließen, wenn sie außerhalb
  des Server-Ticks angestoßen wird; Übergaben in den Tick erfolgen ausschließlich über die
  Scheduler-Abstraktion aus B01.

#### Ressourcen

- **FR-025**: Das System MUSS je Träger einen aktuellen Stand für Leben und Mana führen, jeweils
  begrenzt auf [0, aktuelles Maximum].
- **FR-026**: Das System MUSS bei steigendem Maximum den aktuellen Stand unverändert lassen und bei
  sinkendem Maximum auf das neue Maximum klemmen, ohne dadurch einen Tod auszulösen.
- **FR-027**: Das System MUSS für neu angelegte Charaktere Leben und Mana auf ihr jeweiliges Maximum
  setzen.
- **FR-028**: Das System MUSS die aktuellen Ressourcenstände über den Schreibpfad aus B02/B03
  dauerhaft sichern und beim Laden einer Sitzung wiederherstellen; es DARF KEINEN eigenen
  Datenbankzugriff je Spielereignis erzeugen.
- **FR-029**: Das System MUSS Änderungen an Ressourcenständen als Ereignis veröffentlichen, damit
  Anzeige und Folgeblöcke ohne Abfrageschleife reagieren.

#### Brücke zu Vanilla

- **FR-030**: Das System MUSS `GENERIC_MAX_HEALTH` fest auf 20 halten und die angezeigte
  Vanilla-Gesundheit als `aktuelles Leben / maximales Leben × 20` setzen.
- **FR-030a**: Das System MUSS die natürliche Vanilla-Regeneration abschalten und die Sättigung
  fixieren, damit ausschließlich die Engine den angezeigten Gesundheitswert bestimmt.
- **FR-030b**: Das System DARF Vanilla-Schadensquellen (Fall, Feuer, Lava, Void, Explosion und
  weitere) NICHT umlenken oder neutralisieren — das gehört zu B05 und ist hier ausdrücklich
  ausgeschlossen.
- **FR-031**: Das System MUSS verhindern, dass ein lebender Träger null Anzeigepunkte zeigt; solange
  das aktuelle Leben größer null ist, wird der kleinste darstellbare Wert oberhalb von null
  verwendet.
- **FR-032**: Das System MUSS Änderungen an `movementSpeed`, `attackSpeed` und maximalem Leben im
  selben Vorgang zum jeweiligen Vanilla-Attribut und zur Anzeige spiegeln.
- **FR-033**: Das System MUSS jeden Zugriff auf Vanilla-Attribute ausschließlich im Tick des
  betroffenen Trägers ausführen.
- **FR-034**: Das System MUSS die Spiegelung hinter einer Schnittstelle kapseln, sodass die
  Berechnung selbst ohne laufenden Server prüfbar bleibt.

#### Träger und Lebenszyklus

- **FR-035**: Das System MUSS Stat-Träger sowohl für Spielercharaktere als auch für Wesen ohne
  Spielerbezug (Mobs, B10) bereitstellen, mit identischer Berechnung.
- **FR-036**: Das System MUSS beim Ende einer Sitzung oder beim Entfernen eines Trägers alle
  zugehörigen Beiträge und Schnappschüsse freigeben, ohne Speicherrest.
- **FR-037**: Das System MUSS Abfragen für einen Spieler ohne bereite Sitzung mit der Auskunft
  "Sitzung noch nicht bereit" beantworten und DARF KEINE Standardwerte liefern.
- **FR-038**: Das System MUSS eine Ausnahme in einem Beitragslieferanten auf den betroffenen Träger
  begrenzen; andere Träger und der Tick laufen weiter.

#### Schnittstellen zu späteren Blöcken (ohne Vorgriff)

- **FR-039**: Das System MUSS eine Schnittstelle bereitstellen, über die spätere Blöcke Basiswerte
  beisteuern (B06 Level, B07 Klasse), ohne dass B04 deren Inhalte kennt.
- **FR-040**: Das System MUSS eine Schnittstelle bereitstellen, über die spätere Blöcke Beiträge
  registrieren und entfernen (B08 Buffs, B09 Zonen, B11 Ausrüstung).
- **FR-041**: Das System MUSS den Schnappschuss so bereitstellen, dass B05 (Kampf) und B13 (Anzeige)
  ihn ohne Kenntnis der internen Struktur lesen können.
- **FR-042**: Das System DARF KEINE Inhalte umsetzen, die späteren Blöcken gehören — keine
  Schadensanwendung, keine XP- oder Levelregeln, keine Klassendefinitionen, keine
  Fähigkeiten-Cooldownverwaltung, keine Itemdefinitionen und keine Sekundärwerte wie Krit oder
  Lebensraub.

### Key Entities

- **Attribut**: eines der acht Werteschemata. Trägt einen sprachneutralen Bezeichner und die Angabe,
  ob es sich um einen absoluten Wert oder einen Prozentwert handelt.
- **Attributdefinition**: Basiswert, Untergrenze, Obergrenze und zulässige Bandbreite je Attribut;
  stammt aus der Konfiguration und ist beim Start validiert.
- **Beitrag (Modifier)**: eine einzelne Veränderung eines Attributs — Zielattribut, Rechenart
  (`FLAT`/`PERCENT`), Wert, Quellen-ID, Quellenart.
- **Quelle**: die Herkunft eines Beitragssatzes — Klasse, Level, Ausrüstungsteil, Buff, Aura, Zone.
  Identifiziert durch eine ID, über die alle ihre Beiträge gemeinsam entfernbar sind.
- **Stat-Träger**: alles, was Werte hat — ein Spielercharakter oder ein Wesen ohne Spielerbezug.
  Hält seine Quellen, seinen zuletzt berechneten Schnappschuss und seine Ressourcenstände.
- **Schnappschuss (StatSnapshot)**: unveränderliches Ergebnis einer Neuberechnung mit allen acht
  Endwerten und einer fortlaufenden Kennung, an der Verbraucher Änderungen erkennen.
- **Ressourcenstand**: aktueller Wert für Leben und Mana, begrenzt auf [0, Maximum]; Teil des
  dauerhaften Charakterzustands.
- **Attributkonfiguration**: die versionierte Datei, aus der Definitionen und Grenzen stammen.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Ein vollständiger Ausrüstungswechsel über alle Slots löst genau **eine** Neuberechnung
  aus — nachweisbar über die Zählung der Neuberechnungen. Dasselbe gilt für das Anlegen des
  gesamten Satzes beim Anmelden: 200 gleichzeitige Anmeldungen erzeugen höchstens 200
  Neuberechnungen, nicht eine je Ausrüstungsteil.
- **SC-002**: 200 Träger mit je acht Attributen und je 20 Beiträgen erzeugen im Leerlauf über
  1200 Ticks null Neuberechnungen und keine messbare Tick-Zeit (< 0,1 ms je Tick).
- **SC-003**: 100 gleichzeitige Neuberechnungen in einem Tick bleiben unter dem Subsystembudget von
  5 ms.
- **SC-004**: Ein Rundlauf aus Anlegen und Ablegen derselben Quelle endet nach 1000 Wiederholungen
  exakt beim Ausgangswert, ohne jede Abweichung.
- **SC-005**: Alle Berechnungswege sind ohne laufenden Server geprüft, einschließlich Wert null,
  negativer Beiträge, Prozentsumme unter −100 % und Überschreiten jeder Ober- und Untergrenze.
- **SC-006**: Bei Verteidigung 300 beträgt die Schadensminderung exakt 75 %, bei Verteidigung 0
  exakt 0 %.
- **SC-007**: Die Abklingzeitreduktion übersteigt in keinem Fall 40 %, unabhängig von Anzahl und
  Höhe der Beiträge.
- **SC-008**: Die angezeigte Herzleiste entspricht in jedem geprüften Fall dem tatsächlichen
  Gesundheitsanteil mit einer Abweichung von höchstens einem halben Herzen, und ein lebender Spieler
  zeigt nie null Herzen.
- **SC-009**: Eine Änderung an Basiswerten oder Grenzen ist ohne Codeänderung wirksam; eine
  fehlerhafte Konfiguration führt zum Startabbruch mit einer Meldung, die Attribut und Feld benennt.
- **SC-010**: Nach dem Beenden von 200 Sitzungen sind keine Beiträge, Schnappschüsse oder
  Trägerobjekte dieser Sitzungen mehr vorhanden.
- **SC-011**: Ein Spieler findet nach Verlassen und Zurückkehren seine Leben- und Manastände wieder
  vor, höchstens um ein Autosave-Intervall veraltet.
- **SC-012**: Kein Spielereignis erzeugt einen eigenen Datenbankzugriff der Stat-Engine —
  nachweisbar über die Zählung der Zugriffe je Sitzung.

## Assumptions

- **Ressourcenbehälter gehören zu B04** (geklärt, siehe Clarifications): Der aktuelle Stand von
  Leben und Mana wird hier geführt und persistiert, weil die Anzeigeformel aus ADR-003
  (`aktuell / maximal × 20`) beides braucht. Die Regeln, *wann* sich ein Stand ändert (Schaden,
  Heilung, Fähigkeitskosten, Regeneration), gehören zu B05 und B08 und sind hier ausdrücklich
  nicht enthalten.
- **Basiswerte kommen zunächst aus der Konfiguration**: Solange B06 (Level) und B07 (Klassen) nicht
  existieren, liefert die Attributkonfiguration die Basiswerte. Die Schnittstelle für spätere
  Beitragslieferanten wird jetzt definiert, aber nicht mit Inhalten gefüllt.
- **Ausgangswerte für das Balancing** entsprechen der Tabelle in `blocks/B04-stat-engine.md`
  (Leben 100 → 2000, Verteidigung 0 → 300, Mana 50 → 500, physischer und magischer Schaden je
  5 → 150, Angriffsgeschwindigkeit ±50 %, Bewegungsgeschwindigkeit ±30 %, Abklingzeit 0 → 40 %) und
  sind über die Konfiguration jederzeit änderbar.
- **Interne Rechengenauigkeit** ist Gleitkomma mit doppelter Genauigkeit; gerundet wird
  ausschließlich an der Darstellung. Für den driftfreien Rundlauf (SC-004) wird beim Entfernen einer
  Quelle aus den verbliebenen Quellen neu berechnet, statt den Beitrag rückwärts herauszurechnen.
- **Angriffs- und Bewegungsgeschwindigkeit** verwenden den jeweiligen Vanilla-Basiswert als Basis;
  B04 steuert sie ausschließlich über Beiträge innerhalb der festgelegten Bandbreite.
- **Zugriffsschutz**: Für Spieler gilt die Sitzungsregel aus B03 — vor der Freigabe sind keine Werte
  abrufbar. Träger ohne Spielerbezug haben diese Einschränkung nicht.
- **Persistenz** nutzt den Write-Behind-Pfad aus B02 und den Sitzungsschreibpfad aus B03; B04 legt
  dafür eigene Felder in einer versionierten Migration an, greift aber nie selbst zur Datenbank.
- **Sekundärwerte** (Krit-Chance, Krit-Schaden, Lebensraub, Resistenzen) sind laut ADR-008 nicht
  Teil dieses Blocks; das generische Modell muss sie später ohne Architekturänderung aufnehmen
  können.
- **Anzeige** beschränkt sich auf die Spiegelung zu Vanilla-Attributen und ein Ereignis für B13; ein
  HUD wird hier nicht gebaut.

## Dependencies

- **B01 Core & Plattform**: Modul-Bootstrap, Ereignisbus, Scheduler-Abstraktion,
  Konfigurationsladen mit Schemaprüfung, Message-Keys.
- **B02 Persistenz-Layer**: Write-Behind-Puffer und Migrationsmechanik für die Ressourcenfelder.
- **B03 Spieler-Session**: Sitzungsregistratur, Lade- und Entladepfad, Zustand "bereit", an den
  Träger und Schnappschuss gebunden sind.
- **ADR-003**, **ADR-004**, **ADR-008**: verbindliche Vorgaben für HP-System, Ausrüstung als
  Stat-Quelle und die Grundformeln.
