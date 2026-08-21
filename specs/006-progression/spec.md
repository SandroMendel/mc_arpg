# Feature Specification: B06 · Progression (Erfahrung & Level)

**Feature Branch**: `006-progression`

**Created**: 2026-08-20

**Status**: Draft

**Input**: Blocksteckbrief `blocks/B06-progression.md` — eigenes Erfahrungs- und Levelsystem,
unabhängig von Vanilla-XP. Hängt ab von B03 (Session), B04 (Stat-Engine) und B05 (Todesereignis mit
Schadensaufteilung); wird benötigt von B07, B08, B09, B11, B12, B13 und B14. Verbindlich:
Prinzip II (keine wiederkehrenden Aufgaben je Spieler, kein DB-Zugriff je Spielereignis),
Prinzip III (`rpg-core` ohne Bukkit-Abhängigkeit), Prinzip IV (Cache ist autoritativ während der
Sitzung), Prinzip V (alle Balancing-Zahlen in validierter Konfiguration), ADR-008 (nur die acht
Attribute), ADR-011 (Charakter, nicht Account, ist der Träger von Fortschritt).

## Clarifications

### Session 2026-08-20

- Q: Wie soll XP bei Levelunterschied zum Mob skalieren (Anti-Powerleveling)? → A: Keine Skalierung. Die XP eines Mobs hängt ausschliesslich am Mob, nie am Levelabstand. Bewusst in Kauf genommen: Powerleveling durch Mitnehmen in hohe Zonen ist möglich, Startzonen-Mobs bleiben unbegrenzt farmbar. Begrenzt wird allein über die Levelanforderungen der Zonen aus B09 — die XP-Rechnung selbst bleibt eine Zahl je Mob und damit prüfbar ohne Kenntnis des Spielerlevels.
- Q: Soll XP bei mehreren Beteiligten an einem Mob geteilt werden? → A: Echtes Party-System mit Einladung, geteilter XP und Nähe-Bonus. Das Modell und die Teilungsregeln liegen in B06, weil sie Fortschrittslogik sind; die Befehle liegen in B14 und die Anzeige in B13, wie bei jedem anderen Block. Die Party ist reiner Laufzeitzustand und wird nicht persistiert.
- Q: Was passiert nach Erreichen von Maximallevel 60? → A: Nichts. Level 60 ist das Ende der Levelprogression; XP darüber verfällt still. Weiteres Wachstum läuft über Coins (Fähigkeiten, B08) und Ausrüstung (B11). Paragon oder Prestige bleiben als eigener Block nachrüstbar, sind aber nicht Teil von B06 — eine zweite Progressionskurve jetzt zu bauen würde B06 verdoppeln, ohne dass ein Spieler sie in absehbarer Zeit erreicht.
- Q: Wie soll die XP-Kurve konfiguriert werden — Formel oder Tabelle? → A: Explizite Tabelle in `progression.yml`, eine Zeile je Level von 2 bis 60. Keine Formel. Bei 60 Leveln bleibt die Tabelle lesbar, jedes einzelne Level lässt sich nachjustieren, ohne alle anderen zu verschieben, und es gibt genau eine Quelle für jede Zahl.

### Session 2026-08-20 (Clarify)

- Q: Wird der Fortschritt als Level plus Rest-XP gespeichert oder als eine Gesamt-XP-Zahl, aus der das Level abgeleitet wird? → A: Level und XP innerhalb dieses Levels. Eine Gesamt-XP-Zahl würde bei einer später erhöhten Kurve bestehende Charaktere rückwirkend im Level senken — genau das verbietet FR-024. Ein Balancing-Eingriff in die Kurve bleibt damit von bestehenden Charakteren getrennt, dieselbe Eigenschaft, die ADR-004 für Items durchsetzt.

- Q: Ist der Nähe-Bonus ein Prozentaufschlag oder ein fester XP-Betrag je zusätzliches Mitglied? → A: Prozentaufschlag auf den Party-Anteil, mit konfigurierter Obergrenze. Ein Festbetrag wäre auf Level 1 riesig und auf Level 60 belanglos, weil die XP-Beträge der Mobs über die Progression stark steigen. Genau umgekehrt zum Umgebungsschaden in B05: dort ist der Festbetrag gewollt, weil die Gefahr verfallen *soll* — hier soll der Anreiz auf jedem Level gleich wirken.

- Q: Wovon wird „in Reichweite" gemessen — vom gestorbenen Gegner oder vom beitragenden Party-Mitglied? → A: Vom gestorbenen Gegner. Das ist der einzige Punkt, den alle Mitglieder gemeinsam haben, und er macht „dabei gewesen sein" zur Bedingung. Der Bezug auf den Beitragenden liesse sich zu Ketten von Spielern strecken und wäre bei mehreren beitragenden Mitgliedern mehrdeutig.

- Q: Hat eine Party einen Anführer mit besonderen Rechten? → A: Ja. Der Anführer darf einladen und entfernen; verlässt er die Party, geht die Rolle automatisch an das dienstälteste verbleibende Mitglied. Ohne Anführer gäbe es keine Antwort auf „wie werfe ich ein störendes Mitglied heraus" ausser der Neugründung durch alle anderen, und eine automatische Weitergabe verhindert eine führungslose Party, wenn der Anführer die Verbindung verliert.

- Q: Veröffentlicht B06 ein Ereignis je XP-Gewinn oder nur bei einem Levelaufstieg? → A: Ein gebündeltes Fortschrittsereignis. XP-Gewinne eines Charakters werden innerhalb eines konfigurierten Fensters zusammengefasst und als ein Ereignis gemeldet — dasselbe Muster, mit dem B05 die Schadenszahlen bündelt. Ein Ereignis je Gewinn würde FR-062 verletzen, nur Aufstiegsereignisse würden B13 zum periodischen Abfragen zwingen und damit Prinzip II verletzen. Die Bündelung liegt in B06, weil sie Fortschrittslogik ist; das Zeichnen liegt in B13.

### Session 2026-08-20 (Clarify, zweite Runde)

- Q: Kann ein Levelaufstieg alle acht Attribute anheben oder nur eine Teilmenge? → A: Das Schema erlaubt alle acht mit je eigener Zuwachsrate, Null eingeschlossen; die mitgelieferte Vorgabe lässt Leben, Mana, Verteidigung, physischen und magischen Schaden wachsen und setzt Angriffsgeschwindigkeit, Laufgeschwindigkeit und Fähigkeiten-Cooldown auf Null. Damit bleibt „welche Attribute wachsen" eine Inhaltsentscheidung in der Konfiguration (Prinzip V), und B07 kann Klassen unterscheiden, ohne dass B06 geändert werden muss (FR-022). Die Caps aus B04 begrenzen jeden Ausschlag.

- Q: Was passiert mit dem aktuellen Leben und Mana, wenn ein Level-Up das Maximum erhöht? → A: Vollständig auffüllen. Ein Aufstieg heilt Leben und Mana auf das neue Maximum. Bewusst in Kauf genommen: weil es keine Levelabstands-Skalierung gibt, kann ein Spieler den Aufstieg kurz vor der Schwelle absichtlich in einen Bosskampf hinein aufsparen und ihn als planbare Vollheilung nutzen. Das ist selbstbegrenzend — jedes Level steigt genau einmal, und auf Maximallevel entfällt es vollständig.

- Q: Was gibt ein Gegner ohne eingetragenen XP-Betrag? → A: Einen konfigurierten Standardbetrag, dazu eine einmalige Warnung je Mob-Art. Aufbau wie `mobs:` in `combat.yml`: ein `default`-Abschnitt und darunter `by-type`-Überschreibungen, geschlüsselt über den Bukkit-Typnamen. Null XP hätte bedeutet, dass jeder neu von Mojang hinzugefügte Mob stillschweigend wertlos ist, und hätte dieselbe Frage anders beantwortet als der Block daneben.

- Q: Darf die Verwaltung Level und XP direkt setzen, senken eingeschlossen? → A: Ja. FR-024 schützt Spieler vor Verlust im Spielverlauf, hindert aber nicht den Betreiber daran, einen Fehler zu beheben — sonst wäre ein durch einen Bug verschenktes Level nur noch von Hand in der Datenbank zu korrigieren, also am autoritativen Cache vorbei. Jeder Eingriff geht ins Audit-Log aus B02.

- Q: Was passiert mit einem offenen Fortschrittsbündel, wenn ein Levelaufstieg eintritt? → A: Das offene Bündel wird zuerst ausgeliefert, danach das Aufstiegsereignis, dann wird das Fenster zurückgesetzt. Andernfalls könnte der Fortschrittsbalken rückwärts springen, weil ein älteres Bündel nach dem Aufstieg eintrifft und noch vom vorherigen Level erzählt. Die Reihenfolge wird damit strukturell richtig, statt vom Zufall des Zeitfensters abzuhängen, und B13 braucht keine Erkennung veralteter Meldungen.

### Abgeleitete Entscheidungen

- Das Party-System setzt die B05-Entscheidung „XP anteilig nach Schadensanteil" **fort** und ersetzt
  sie nicht. Eine Party gilt als **ein** Beitragender; ihr Anteil ist die Summe der Anteile ihrer
  Mitglieder. Ohne diese Auslegung gäbe es zwei konkurrierende Verteilungsregeln für dieselbe XP.
- Die Reichweitenprüfung für den Nähe-Bonus liegt hinter einem Erweiterungspunkt. `rpg-core` darf
  keine Bukkit-Abhängigkeit haben (Prinzip III), also kann die Domänenschicht keine Entfernung
  zwischen zwei Spielern selbst messen.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ein Kill gibt Erfahrung, und irgendwann steigt das Level (Priority: P1)

Als Spieler erschlage ich einen Gegner und bekomme dafür Erfahrung. Sammle ich genug, steigt mein
Level, und meine Attribute wachsen mit — sichtbar, nachvollziehbar und ohne dass die
Vanilla-Erfahrungsleiste dabei eine Rolle spielt.

**Why this priority**: Ohne XP-Vergabe und Levelaufstieg gibt es keine Progression, und damit hätte
kein Folgeblock etwas, worauf er aufbauen könnte — B08 kann keine Fähigkeit per Level freischalten,
B09 keine Zone sperren, B11 kein Item an ein Level binden.

**Independent Test**: Ein Charakter auf Level 1 erhält den XP-Betrag der Schwelle für Level 2. Prüfbar
ohne laufenden Server: Level steigt auf 2, die überschüssige XP zählt weiter, und die acht Attribute
sind um genau das konfigurierte Wachstum eines Levels höher.

**Acceptance Scenarios**:

1. **Given** ein Charakter auf Level 1 mit 0 XP und einer Schwelle von 100 XP für Level 2,
   **When** er 100 XP erhält, **Then** ist er auf Level 2 mit 0 XP Überschuss.
2. **Given** derselbe Charakter, **When** er 250 XP erhält und die Schwellen 100 und 120 lauten,
   **Then** ist er auf Level 3 mit 30 XP Überschuss.
3. **Given** ein Mob mit 40 konfigurierten XP, **When** ein Spieler ihn allein erschlägt,
   **Then** erhält er genau 40 XP — unabhängig davon, ob er Level 1 oder Level 59 ist.
4. **Given** ein Levelaufstieg von 1 auf 3, **When** er verarbeitet wird, **Then** wird der
   Wertestand genau einmal neu berechnet und genau ein Aufstiegsereignis mit altem und neuem Level
   veröffentlicht.
5. **Given** ein Charakter stirbt, **When** der Tod verarbeitet ist, **Then** hat er weder XP noch
   Level verloren.

---

### User Story 2 - Die XP-Kurve ist Konfiguration, kein Code (Priority: P1)

Als Betreiber justiere ich die XP-Kurve, die XP je Mob-Art und alle Party-Zahlen in
`progression.yml` und starte neu. Ist die Datei fehlerhaft, sagt mir der Server beim Start genau,
welches Level oder welcher Schlüssel beanstandet wird — und startet nicht mit halb gültigem
Balancing.

**Why this priority**: Prinzip V verlangt datengetriebenes Design mit Fail-Fast-Validierung. Eine
Kurve mit einer Lücke oder einem nicht steigenden Wert würde Spieler dauerhaft auf einem Level
festhalten; das darf nicht erst im Spielbetrieb auffallen.

**Independent Test**: Eine Tabelle mit einer fehlenden Zeile, einer doppelten Zeile, einem
negativen Wert und einer nicht steigenden Folge — je ein Fall, je eine benennende Meldung, kein
Start.

**Acceptance Scenarios**:

1. **Given** eine Tabelle mit Level 2 bis 60 lückenlos und streng steigend, **When** der Server
   startet, **Then** startet er, und das Maximallevel ergibt sich als 60 aus der Tabelle.
2. **Given** eine Tabelle, in der Level 37 fehlt, **When** der Server startet, **Then** bricht der
   Start ab und die Meldung nennt Level 37.
3. **Given** eine Tabelle, in der die Schwelle für Level 20 kleiner ist als die für Level 19,
   **When** der Server startet, **Then** bricht der Start ab und die Meldung nennt Level 20.
4. **Given** eine Tabelle mit einem Wert von 0 oder kleiner, **When** der Server startet,
   **Then** bricht der Start ab und die Meldung nennt das betroffene Level.

---

### User Story 3 - Erfahrung sammeln belastet die Datenbank nicht (Priority: P1)

Als Betreiber sehe ich bei 150 Spielern im Dauerkampf keinen einzigen Datenbankzugriff, der durch
XP-Zuwachs ausgelöst wird. Der Stand liegt im Speicher, wird als änderungsbedürftig markiert und
gebündelt geschrieben.

**Why this priority**: Prinzip II verbietet Datenbankzugriffe je Spielereignis, und XP ist das
häufigste Fortschrittsereignis des Spiels. Ein Schreibvorgang je Kill wäre bei 800 aktiven Mobs
sofort der Engpass.

**Independent Test**: 1000 XP-Ereignisse in einer Sekunde gegen eine gezählte Repository-Attrappe:
Zugriffszähler bleibt bei null, Markierungen liegen vor.

**Acceptance Scenarios**:

1. **Given** ein Charakter mit laufender Sitzung, **When** er 1000 XP-Ereignisse in einer Sekunde
   erhält, **Then** erfolgt kein Datenbankzugriff und der Charakter ist als änderungsbedürftig
   markiert.
2. **Given** derselbe Charakter, **When** die Sitzung endet, **Then** ist der Stand geschrieben,
   bevor die Sitzung als beendet gilt.
3. **Given** eine laufende Sitzung, **When** Stand aus Speicher und Stand aus der Datenbank
   auseinanderlaufen, **Then** ist der Speicherstand der gültige.

---

### User Story 4 - Level öffnen und sperren Inhalte (Priority: P2)

Als Folgeblock (B08 Fähigkeiten, B09 Zonen, B11 Items) frage ich, ob ein Charakter ein bestimmtes
Level erreicht hat, und richte mein Verhalten danach — ohne den Fortschrittsstand selbst zu kennen
oder nachzurechnen.

**Why this priority**: Fünf Blöcke hängen an dieser Abfrage. Sie muss vor ihnen stehen, aber sie
ist wertlos ohne US1.

**Independent Test**: Ein Charakter auf Level 12 erfüllt die Anforderung 10 und 12, nicht 13.
Prüfbar ohne die anfragenden Blöcke.

**Acceptance Scenarios**:

1. **Given** ein Charakter auf Level 12, **When** Level 10 abgefragt wird, **Then** ist die
   Anforderung erfüllt.
2. **Given** derselbe Charakter, **When** Level 13 abgefragt wird, **Then** ist sie nicht erfüllt.
3. **Given** eine Abfrage für einen Charakter ohne Fortschrittsstand, **When** sie erfolgt,
   **Then** lautet die Antwort „nicht erfüllt" und der Vorfall wird protokolliert — ohne Ausnahme.
4. **Given** eine beliebige Anforderungsabfrage, **When** sie erfolgt, **Then** erzeugt sie keinen
   Datenbankzugriff und keine Neuberechnung.

---

### User Story 5 - Spieler können sich zu einer Party zusammentun (Priority: P2)

Als Spieler lade ich einen anderen ein, gemeinsam zu spielen. Nimmt er an, sind wir eine Party.
Verlässt jemand den Server, verlässt er die Party; geht der Letzte, existiert sie nicht mehr.

**Why this priority**: Voraussetzung für die geteilte XP aus US6. Für sich allein liefert die Party
noch keinen Fortschritt, deshalb hinter den P1-Geschichten.

**Independent Test**: Einladung, Annahme, Ablehnung, Verfall, Verlassen und Auflösung als reine
Zustandsübergänge ohne laufenden Server.

**Acceptance Scenarios**:

1. **Given** zwei Spieler mit bereiter Sitzung, **When** der eine den anderen einlädt und dieser
   annimmt, **Then** sind beide Mitglied derselben Party.
2. **Given** eine offene Einladung, **When** die konfigurierte Frist verstreicht, **Then** ist sie
   nicht mehr annehmbar, und dafür läuft keine wiederkehrende Aufgabe.
3. **Given** ein Spieler ist bereits in einer Party, **When** er eine zweite Einladung annimmt,
   **Then** wird die Annahme mit Begründung abgelehnt.
4. **Given** eine Party in maximaler Grösse, **When** ein weiterer Beitritt erfolgt, **Then** wird
   er mit Begründung abgelehnt.
5. **Given** eine Party aus zwei Mitgliedern, **When** ein Mitglied die Sitzung beendet, **Then**
   besteht die Party aus dem verbliebenen Mitglied weiter.
5a. **Given** eine Party aus drei Mitgliedern, **When** der Anführer die Verbindung verliert,
   **Then** ist das dienstälteste verbleibende Mitglied Anführer, und die Party ist zu keinem
   Zeitpunkt ohne Anführer.
5b. **Given** eine Party, **When** ein Mitglied ohne Anführerrolle jemanden einzuladen oder zu
   entfernen versucht, **Then** wird der Versuch mit Begründung abgelehnt.
6. **Given** eine Party aus einem Mitglied, **When** auch dieses sie verlässt, **Then** existiert
   die Party nicht mehr, und kein Zustand bleibt zurück.
7. **Given** ein Serverneustart, **When** Spieler sich wieder anmelden, **Then** gibt es keine
   Party mehr — sie wurde nie gespeichert.

---

### User Story 6 - Gemeinsam spielen lohnt sich (Priority: P2)

Als Party erschlagen wir gemeinsam Gegner. Die XP des Gegners kommt bei allen an, die nah genug
dabei sind — auch bei dem, der gerade heilt statt zu schlagen — und gemeinsam zu spielen ist nicht
schlechter, als allein zu spielen.

**Why this priority**: Das ist der eigentliche Zweck des Party-Systems. Ohne US5 nicht baubar,
ohne US1 nicht messbar.

**Independent Test**: Eine Schadensaufteilung mit drei Beteiligten, davon zwei in einer Party, gegen
einen Mob mit bekanntem XP-Betrag. Die verteilten Beträge sind vollständig vorhersagbar.

**Acceptance Scenarios**:

1. **Given** ein Mob mit 100 XP und ein einzelner Beitragender ohne Party mit 100 % Anteil,
   **When** der Mob stirbt, **Then** erhält der Beitragende 100 XP.
2. **Given** ein Mob mit 100 XP, Beitragende A (60 %) und B (40 %), beide ohne Party, **When** der
   Mob stirbt, **Then** erhält A 60 XP und B 40 XP.
3. **Given** ein Mob mit 100 XP, A und B in einer Party mit zusammen 60 % Anteil, beide in
   Reichweite, und C ohne Party mit 40 %, **When** der Mob stirbt, **Then** teilen A und B den
   Party-Anteil von 60 XP gleichmässig — je 30 XP plus Nähe-Bonus — und C erhält 40 XP.
4. **Given** dieselbe Party, **When** B ausserhalb der Reichweite ist, **Then** erhält A den
   gesamten Party-Anteil und B nichts; der Nähe-Bonus entfällt.
5. **Given** ein Party-Mitglied, das keinen Schaden gemacht hat, aber in Reichweite ist,
   **When** der Mob stirbt, **Then** erhält es seinen Anteil trotzdem.
6. **Given** ein Party-Mitglied in einer anderen Welt, **When** der Mob stirbt, **Then** gilt es
   nie als in Reichweite.
7. **Given** eine Party, in der ein Mitglied auf Maximallevel ist, **When** der Mob stirbt,
   **Then** verfällt der Anteil dieses Mitglieds still und wird nicht auf die anderen umverteilt.

---

### User Story 7 - Level 60 ist das Ende, und es fühlt sich nicht wie ein Fehler an (Priority: P3)

Als Spieler auf Maximallevel spiele ich weiter. Erfahrung bringt mir nichts mehr, aber nichts
bricht: keine Fehlermeldung, kein überlaufender Fortschritt, kein Spam im Protokoll.

**Why this priority**: Betrifft zunächst niemanden, weil noch kein Spieler Level 60 hat. Bricht aber
später hässlich, wenn es fehlt.

**Independent Test**: Ein Charakter auf Maximallevel erhält 10 000 XP in Folge. Level und XP-Stand
bleiben unverändert, es wird kein Aufstiegsereignis veröffentlicht, und das Protokoll wächst nicht
je Ereignis.

**Acceptance Scenarios**:

1. **Given** ein Charakter auf Level 60, **When** er XP erhält, **Then** bleibt sein Stand
   unverändert und es entsteht kein Fehler.
2. **Given** derselbe Charakter, **When** er 10 000 XP-Ereignisse erhält, **Then** wird kein
   Aufstiegsereignis veröffentlicht und keine Protokollzeile je Ereignis geschrieben.
3. **Given** derselbe Charakter, **When** sein Fortschritt abgefragt wird, **Then** wird er als
   vollständig gemeldet, nicht als „0 % zum nächsten Level".

---

### User Story 8 - Weitere Erfahrungsquellen kommen ohne neue Vergabelogik dazu (Priority: P3)

Als Folgeblock (B09 Zonen-Ziele) schreibe ich einem Charakter Erfahrung zu und nutze dafür denselben
Eingangspunkt wie der Mob-Kill. Es gibt keine zweite Stelle, an der XP entsteht.

**Why this priority**: Hält B06 offen für B09, ohne dass B09 dafür etwas in B06 ändern muss. Kein
heutiger Spielwert, deshalb P3.

**Independent Test**: Ein XP-Betrag mit einer anderen Quellenangabe als „Mob-Kill" durchläuft genau
dieselben Regeln — Party-Teilung eingeschlossen oder ausgeschlossen, je nach Quellenangabe.

**Acceptance Scenarios**:

1. **Given** eine XP-Quelle „Zonen-Ziel", **When** ein Charakter darüber XP erhält, **Then**
   gelten Kurve, Maximallevel und Persistenz genau wie beim Mob-Kill.
2. **Given** eine Quelle, die ausdrücklich nicht geteilt wird, **When** ein Charakter in einer Party
   darüber XP erhält, **Then** erhält nur er sie.

---

### Edge Cases

- **Ein einzelnes XP-Ereignis überspringt mehrere Level.** Jedes durchlaufene Level wirkt auf die
  Attribute, aber der Wertestand wird nur einmal neu berechnet und nur ein Ereignis veröffentlicht.
- **Ein Ereignis überschreitet das Maximallevel.** Der Stand endet exakt auf Maximallevel; der
  Rest verfällt. Kein Überlauf, keine negative Restanzeige.
- **Umgebungstod.** Die Schadensaufteilung aus B05 ist leer — niemand bekommt XP, und das ist kein
  Fehlerfall.
- **Ein Mob erschlägt einen anderen Mob.** Kein Spielerbezug, keine XP.
- **Ein Spieler stirbt.** Kein XP für den Verursacher (PvP ist aus, B05).
- **Der Beitragende hat keine bereite Sitzung mehr** (Abmeldung im Moment des Kills). Sein Anteil
  verfällt still; die Verteilung an die übrigen bleibt unverändert.
- **Ein Mob ohne eigenen XP-Eintrag stirbt.** Der Standardbetrag gilt, dazu eine einmalige Warnung
  je Mob-Art — nicht je Kill.
- **XP-Betrag ist 0, negativ oder nicht endlich.** Abgelehnt und protokolliert, niemals als Abzug
  gedeutet.
- **Die Party-Anteile summieren sich durch Rundung auf mehr als den Party-Anteil.** Die Verteilung
  darf über den XP-Betrag des Mobs hinaus nur den Nähe-Bonus hinzufügen, nie Rundungsgewinne.
- **Kein Reichweiten-Anbieter registriert** (B09 noch nicht gebaut). Dann gilt allein der
  Beitragende selbst als in Reichweite — die Party-Teilung fällt auf das Verhalten ohne Party
  zurück, statt XP zu verschenken oder zu verschlucken.
- **Eine Einladung an einen Spieler, der sich abmeldet.** Die Einladung ist nicht mehr annehmbar,
  ohne dass jemand sie aufräumen muss.
- **Ein Spieler wechselt den aktiven Charakter.** XP gilt immer dem Charakter, der im Moment des
  Ereignisses aktiv ist; der Stand des anderen bleibt unberührt.
- **Eine Ausnahme in der XP-Vergabe.** Der Kampfvorgang läuft weiter; der Fehler bleibt auf diesen
  einen Charakter begrenzt.
- **Das Bündelungsfenster läuft noch, als die Sitzung endet.** Die angesammelte Meldung verfällt
  ohne Spur — sie ist reine Anzeige. Die XP selbst ist längst angerechnet und wird geschrieben.
- **Der Anführer verliert die Verbindung.** Die Rolle geht an das dienstälteste verbleibende
  Mitglied; die Party ist zu keinem Zeitpunkt ohne Anführer.
- **Zwei Party-Mitglieder tragen zum selben Mob bei.** Ihre Anteile werden zu einem Party-Anteil
  addiert, nicht zweimal gezählt.

## Requirements *(mandatory)*

### Functional Requirements

#### XP-Kurve und Konfiguration

- **FR-001**: Das System MUSS die XP-Schwelle je Level als explizite Tabelle aus der Konfiguration
  lesen — eine Zeile je Level von 2 bis zum Maximallevel. Es DARF KEINE Kurvenformel im Code geben.
- **FR-002**: Das System MUSS die Tabelle beim Start vollständig prüfen: jedes Level von 2 bis zum
  höchsten vorhanden, kein Level doppelt, jeder Wert eine positive ganze Zahl, die Folge streng
  monoton steigend.
- **FR-003**: Das System MUSS den Start bei fehlgeschlagener Prüfung abbrechen und dabei das erste
  beanstandete Level und den Grund benennen (Fail-Fast, Prinzip V).
- **FR-004**: Das System MUSS das Maximallevel aus der Tabelle ableiten — das höchste dort
  aufgeführte Level. Es DARF NICHT als Konstante im Code stehen.
- **FR-005**: Das System MUSS alle Balancing-Zahlen in derselben Konfigurationsdatei halten:
  XP-Schwellen, XP je Mob-Art, Attributwachstum je Level, Party-Reichweite, Nähe-Bonus und dessen
  Obergrenze, maximale Partygrösse, Gültigkeitsdauer einer Einladung, Bündelungsfenster für das
  Fortschrittsereignis.
- **FR-006**: Das System MUSS neue Mob-Arten und neue XP-Beträge ohne Codeänderung aufnehmen
  können.

#### XP-Vergabe

- **FR-007**: Das System MUSS einen benannten Eingangspunkt bereitstellen, über den ein XP-Betrag
  mit einer Quellenangabe einem Charakter zugeschrieben wird. Alle Quellen MÜSSEN diesen einen
  Eingangspunkt verwenden.
- **FR-008**: Das System MUSS XP aus dem Todesereignis von B05 vergeben, sobald ein Wesen ohne
  Spielerbezug stirbt.
- **FR-009**: Das System MUSS die XP-Höhe eines Wesens aus der Konfiguration je Mob-Art bestimmen
  und einen austauschbaren Bezugspunkt dafür anbieten, den B10 später ersetzt — analog zur
  Wertversorgung der Mobs in B05.
- **FR-009a**: Das System MUSS die XP-Beträge in derselben Form ablegen wie `mobs:` in
  `combat.yml`: ein Standardbetrag und darunter Überschreibungen je Mob-Art, geschlüsselt über den
  Typnamen. Zwei Dateien nebeneinander sollen sich nicht unterschiedlich lesen.
- **FR-010**: Das System DARF die XP-Höhe NICHT nach dem Levelunterschied zwischen Spieler und Mob
  verändern. Die Höhe hängt ausschliesslich am Wesen.
- **FR-011**: Das System MUSS die Schadensaufteilung aus dem Todesereignis von B05 als Grundlage der
  Verteilung verwenden und DARF sie NICHT neu berechnen.
- **FR-012**: Das System DARF bei leerer Schadensaufteilung (Umgebungstod) niemandem XP zuschreiben,
  und das MUSS als regulärer Fall behandelt werden, nicht als Fehler.
- **FR-013**: Das System DARF für den Tod eines Spielers keine XP vergeben.
- **FR-014**: Das System DARF XP nur einem Charakter mit bereiter Sitzung zuschreiben (B03). Anteile
  von Beitragenden ohne bereite Sitzung MÜSSEN still verfallen.
- **FR-015**: Das System MUSS XP-Beträge ablehnen und protokollieren, die kleiner oder gleich null
  oder nicht endlich sind, statt sie als Abzug zu deuten.
- **FR-016**: Das System MUSS XP immer dem im Moment des Ereignisses aktiven Charakter zuschreiben
  — niemals dem Account und niemals einem inaktiven Charakter.

#### Levelaufstieg und Attributwirkung

- **FR-017**: Das System MUSS das Level erhöhen, sobald der XP-Stand die Schwelle des nächsten
  Levels erreicht.
- **FR-018**: Das System MUSS aus einem einzelnen XP-Ereignis mehrere Level auf einmal steigen
  können und die Wirkung jedes durchlaufenen Levels anwenden.
- **FR-019**: Das System MUSS überschüssige XP erhalten und auf das nächste Level anrechnen.
- **FR-020**: Das System MUSS die Levelwirkung auf die acht Attribute ausschliesslich über die
  offiziellen Erweiterungspunkte von B04 anwenden und DARF Attributwerte NICHT direkt schreiben.
- **FR-021**: Das System MUSS den Wertestand eines Charakters nach einem Aufstieg genau einmal neu
  berechnen — nicht je durchlaufenes Level und nicht je Attribut.
- **FR-021a**: Das System MUSS Leben und Mana nach einem Aufstieg auf das **neue Maximum**
  auffüllen, und zwar genau einmal je Aufstieg — auch wenn mehrere Level auf einmal durchlaufen
  wurden. Das Auffüllen MUSS über die Ressourcenverwaltung aus B04 laufen, nicht durch direktes
  Schreiben.
- **FR-021b**: Das Auffüllen MUSS **nach** der Neuberechnung des Wertestands erfolgen, damit es
  gegen das neue Maximum füllt und nicht gegen das alte.
- **FR-022**: Das System MUSS das Attributwachstum je Level aus der Konfiguration lesen. B06 MUSS
  eine klassenneutrale Vorgabe mitliefern, die B07 je Klasse ersetzen KANN.
- **FR-022a**: Das System MUSS für **alle acht** Attribute eine eigene Zuwachsrate zulassen, **Null
  eingeschlossen**. Es DARF nicht im Code festlegen, welche Attribute wachsen — das ist eine
  Inhaltsentscheidung (Prinzip V).
- **FR-022b**: Die mitgelieferte Vorgabe MUSS Leben, Mana, Verteidigung, physischen und magischen
  Schaden wachsen lassen und Angriffsgeschwindigkeit, Laufgeschwindigkeit sowie
  Fähigkeiten-Cooldown auf Null setzen. Begründung: Laufgeschwindigkeit über 60 Level macht die
  Bewegung unspielbar, und Angriffsgeschwindigkeit läuft laut B05 gegen die
  Vanilla-Unverwundbarkeit — Zuwachs darüber verpufft.
- **FR-022c**: Das System MUSS die Caps je Attribut aus B04 einhalten; ein Zuwachs DARF einen Cap
  nicht überschreiten, sondern läuft dagegen.
- **FR-023**: Das System MUSS je Aufstieg genau ein Ereignis auf dem Ereignisbus veröffentlichen,
  das altes und neues Level trägt, damit B08 freischalten, B12 zählen und B13 anzeigen kann.
- **FR-023a**: Das System MUSS XP-Gewinne eines Charakters innerhalb eines konfigurierten Fensters
  zu **einem** Fortschrittsereignis zusammenfassen, statt je Gewinn eines zu veröffentlichen. Das
  Ereignis trägt den zusammengefassten Zuwachs, das aktuelle Level und die Schwelle des nächsten
  Levels, damit B13 den Fortschritt ohne eigene Rechnung und ohne periodisches Abfragen darstellen
  kann.
- **FR-023b**: Das System DARF im Fortschrittsereignis keine Anzeige erzeugen — kein Text, kein
  Balken, keine Leiste. Das Zeichnen liegt ausschliesslich in B13.
- **FR-023c**: Tritt ein Levelaufstieg ein, während ein Fortschrittsbündel offen ist, MUSS das
  System das Bündel **vor** dem Aufstiegsereignis ausliefern und das Fenster danach zurücksetzen.
  Meldungen MÜSSEN in der Reihenfolge eintreffen, in der sie entstanden sind — sonst kann ein
  älteres Bündel den Fortschrittsbalken nach dem Aufstieg rückwärts springen lassen.
- **FR-024**: Das System DARF das Level **im Spielverlauf** niemals senken und DARF XP niemals
  abziehen — auch nicht beim Tod. Diese Zusage gilt gegenüber dem Spieler, nicht gegenüber dem
  Betreiber (FR-024a).
- **FR-024a**: Das System MUSS einen Verwaltungseingriff bereitstellen, der Level und XP eines
  Charakters frei setzt — **senken eingeschlossen** —, damit ein Betreiber einen Fehler beheben kann,
  ohne am autoritativen Speicherstand vorbei in die Datenbank zu greifen.
- **FR-024b**: Das System MUSS jeden Verwaltungseingriff im Audit-Log aus B02 festhalten, mit
  ausführendem Betreiber, betroffenem Charakter, altem und neuem Stand.
- **FR-024c**: Das System MUSS nach einem Verwaltungseingriff dieselbe Neuberechnung und dieselben
  Ereignisse auslösen wie nach einem natürlichen Aufstieg, damit B08, B12 und B13 nicht auf einem
  überholten Stand stehen bleiben. Ein **gesenktes** Level DARF Leben und Mana nicht auffüllen
  (FR-021a gilt nur für den Aufstieg); übersteigt der aktuelle Wert das neue Maximum, MUSS er darauf
  begrenzt werden.

#### Levelanforderungen

- **FR-025**: Das System MUSS eine Abfrage bereitstellen, ob ein Charakter ein gefordertes Level
  erreicht hat, damit B08, B09 und B11 Inhalte daran binden können.
- **FR-026**: Das System MUSS diese Abfrage ohne Datenbankzugriff und ohne Neuberechnung
  beantworten.
- **FR-027**: Das System MUSS eine Abfrage für einen Charakter ohne Fortschrittsstand mit „nicht
  erfüllt" beantworten und den Vorfall protokollieren, statt eine Ausnahme zu werfen.
- **FR-028**: Das System MUSS den aktuellen Fortschritt abfragbar machen — Level, XP im aktuellen
  Level, Schwelle des nächsten Levels — damit B13 ihn anzeigen kann, ohne selbst zu rechnen.

#### Party-Modell

- **FR-029**: Das System MUSS die Party als reinen Laufzeitzustand führen und DARF sie NICHT
  persistieren.
- **FR-029a**: Das System MUSS je Party genau einen Anführer führen. Der Gründer ist der erste
  Anführer.
- **FR-029b**: Das System DARF das Einladen und das Entfernen von Mitgliedern nur dem Anführer
  erlauben. Jedes Mitglied darf die Party jederzeit selbst verlassen.
- **FR-029c**: Das System MUSS die Anführerrolle beim Ausscheiden des Anführers automatisch an das
  dienstälteste verbleibende Mitglied übergeben — gleich, ob er die Party verlässt, entfernt wird
  oder seine Sitzung endet. Eine Party DARF niemals ohne Anführer bestehen.
- **FR-030**: Das System MUSS einen Beitritt an eine Einladung binden: der Anführer lädt einen
  Spieler mit bereiter Sitzung ein, und die Mitgliedschaft entsteht erst mit der Annahme.
- **FR-031**: Das System MUSS eine Einladung nach der konfigurierten Frist ungültig werden lassen,
  und das MUSS zeitstempelbasiert lazy geprüft werden — ohne wiederkehrende Aufgabe (Prinzip II).
- **FR-032**: Das System MUSS sicherstellen, dass ein Spieler zu jeder Zeit in höchstens einer Party
  ist, und eine Annahme sonst mit Begründung ablehnen.
- **FR-033**: Das System MUSS die maximale Partygrösse aus der Konfiguration durchsetzen und
  Beitritte darüber mit Begründung ablehnen.
- **FR-034**: Das System MUSS ein Mitglied aus der Party entfernen, wenn dessen Sitzung endet.
- **FR-035**: Das System MUSS eine Party auflösen, sobald ihr letztes Mitglied sie verlässt, und
  DARF danach keinen Zustand zu ihr behalten. Eine Party mit einem einzigen Mitglied ist zulässig
  und verhält sich in der XP-Verteilung wie kein Party-Mitglied.
- **FR-036**: Das System MUSS Änderungen der Mitgliedschaft als Ereignis veröffentlichen, damit B13
  sie anzeigen kann.
- **FR-037**: Das System DARF KEINE Befehle und KEINE Anzeige für Partys enthalten. Es stellt allein
  den Vertrag bereit, den B14 (Befehle) und B13 (Anzeige) benutzen.
- **FR-038**: Das System MUSS alle spielerseitigen Texte über Message-Schlüssel führen und DARF
  keinen Text hartcodieren (Prinzip V).

#### XP-Verteilung mit Party

- **FR-039**: Das System MUSS den XP-Betrag eines Wesens in folgender Reihenfolge verteilen:
  (1) Betrag aus der Konfiguration bestimmen, (2) Anteile aus der Schadensaufteilung von B05
  übernehmen, (3) Anteile von Mitgliedern derselben Party zu einem Party-Anteil addieren,
  (4) den Party-Anteil gleichmässig auf die Mitglieder in Reichweite verteilen, (5) den Nähe-Bonus
  aufschlagen.
- **FR-040**: Das System MUSS eine Party als genau einen Beitragenden behandeln, dessen Anteil die
  Summe der Anteile ihrer Mitglieder ist.
- **FR-041**: Das System MUSS den Party-Anteil gleichmässig auf alle Mitglieder in Reichweite
  verteilen — unabhängig davon, wer davon Schaden verursacht hat.
- **FR-041a**: Das System MUSS die Reichweite als Entfernung zum **gestorbenen Gegner** messen,
  nicht zum beitragenden Mitglied. Der Gegner ist der einzige Bezugspunkt, den alle Mitglieder
  gemeinsam haben; ein Bezug auf Beitragende wäre bei mehreren beitragenden Mitgliedern mehrdeutig
  und liesse sich zu Ketten strecken.
- **FR-042**: Das System DARF Mitgliedern ausserhalb der Reichweite keinen Anteil zuschreiben.
- **FR-043**: Das System MUSS auf den Party-Anteil einen Nähe-Bonus als **Prozentaufschlag** je
  zusätzliches Mitglied in Reichweite aufschlagen — nicht als festen XP-Betrag —, begrenzt durch eine
  konfigurierte Obergrenze, damit gemeinsames Spielen nicht schlechter ist als allein zu spielen und
  der Anreiz auf jedem Level gleich wirkt. Der Aufschlag erfolgt auf den Party-Anteil, bevor er
  geteilt wird.
- **FR-044**: Das System MUSS die Reichweitenprüfung über einen austauschbaren Erweiterungspunkt
  beziehen, weil die Domänenschicht keine Entfernung selbst messen darf (Prinzip III). Ist kein
  Anbieter registriert, MUSS allein der Beitragende selbst als in Reichweite gelten.
- **FR-045**: Das System DARF ein Mitglied in einer anderen Welt niemals als in Reichweite werten.
- **FR-046**: Das System MUSS den Anteil eines Beitragenden ohne Party unverändert an diesen
  vergeben.
- **FR-047**: Das System MUSS ganzzahlige XP-Beträge vergeben und DARF durch Rundung keine XP über
  den Betrag des Wesens hinaus erzeugen — der Nähe-Bonus ist der einzige zulässige Aufschlag.
- **FR-048**: Das System MUSS es einer XP-Quelle erlauben, ausdrücklich nicht geteilt zu werden;
  dann erhält allein der genannte Charakter die XP.

#### Maximallevel

- **FR-049**: Das System MUSS weitere XP auf Maximallevel verwerfen und den Stand exakt auf dem
  Maximallevel halten.
- **FR-050**: Das System DARF beim Verwerfen KEIN Aufstiegsereignis und KEIN Fortschrittsereignis
  veröffentlichen, KEINEN Fehler erzeugen und KEINE Protokollzeile je Ereignis schreiben — auf
  Maximallevel ändert sich nichts, also gibt es nichts zu melden.
- **FR-051**: Das System MUSS den Fortschritt eines Charakters auf Maximallevel als vollständig
  melden, nicht als Anfang eines weiteren Levels.
- **FR-052**: Das System MUSS den verfallenden Anteil eines Party-Mitglieds auf Maximallevel still
  verwerfen und DARF ihn NICHT auf die übrigen Mitglieder umverteilen.

#### Persistenz

- **FR-053**: Das System MUSS XP und Level am Charakter führen, nicht am Account (ADR-011).
- **FR-053a**: Das System MUSS den Fortschritt als **Level und XP innerhalb dieses Levels**
  persistieren und DARF das Level NICHT aus einer gespeicherten Gesamt-XP-Zahl ableiten. Eine
  nachträglich geänderte XP-Kurve DARF das Level eines bestehenden Charakters nicht verändern —
  sie wirkt allein auf den weiteren Aufstieg.
- **FR-054**: Das System DARF durch XP-Zuwachs KEINEN Datenbankzugriff auslösen; es MUSS den
  Charakter stattdessen als änderungsbedürftig markieren und die Write-Behind-Strategie aus B02
  nutzen.
- **FR-055**: Das System MUSS den Speicherstand während einer laufenden Sitzung als autoritativ
  behandeln (Prinzip IV).
- **FR-056**: Das System MUSS den Stand beim Sitzungsende schreiben, bevor die Sitzung als beendet
  gilt.
- **FR-057**: Das System MUSS den persistierten Stand versionieren und einen Migrationspfad
  bereitstellen.
- **FR-058**: Das System MUSS einen Charakter ohne gespeicherten Fortschritt auf Level 1 mit 0 XP
  beginnen lassen.

#### Robustheit und Performance

- **FR-059**: Das System MUSS eine Ausnahme in der XP-Vergabe auf den betroffenen Charakter
  begrenzen, protokollieren und den laufenden Kampfvorgang unangetastet lassen (Prinzip VI).
- **FR-060**: Das System MUSS ein Wesen ohne eigenen Eintrag mit dem konfigurierten Standardbetrag
  behandeln und dazu höchstens eine Warnung je Mob-Art schreiben — nicht je Kill. Ein unbekannter
  Mob ist damit brauchbar statt wertlos, und die Lücke fällt trotzdem auf.
- **FR-061**: Das System DARF KEINE wiederkehrende Aufgabe je Spieler, je Charakter oder je Party
  betreiben. Zeitbezogene Werte MÜSSEN zeitstempelbasiert lazy ausgewertet werden (Prinzip II).
- **FR-062**: Das System DARF im Kampfpfad keine vermeidbaren Objekte je XP-Ereignis erzeugen.
- **FR-063**: Das System DARF Vanilla-XP und die Vanilla-Erfahrungsleiste NICHT als
  Fortschrittsspeicher verwenden.

### Key Entities

- **Fortschrittsstand**: je Charakter das erreichte Level und die XP innerhalb dieses Levels. Der
  einzige veränderliche Zustand, den B06 persistiert.
- **XP-Tabelle**: die konfigurierte Zuordnung Level → benötigte XP, Level 2 bis Maximallevel.
  Bestimmt zugleich das Maximallevel.
- **Levelwachstum**: der konfigurierte Zuwachs je Level auf die acht Attribute. Klassenneutral in
  B06, je Klasse ersetzbar durch B07.
- **XP-Quelle**: woher ein Betrag stammt — Mob-Kill, Zonen-Ziel, Verwaltung. Bestimmt, ob der Betrag
  geteilt wird und ob er senken darf.
- **Verwaltungseingriff**: das freie Setzen von Level und XP durch einen Betreiber, mit altem und
  neuem Stand für das Audit-Log. Die einzige Quelle, die senken darf.
- **XP-Betrag je Mob-Art**: die konfigurierte Erfahrung, die ein Wesen hergibt. Von B10 später
  über denselben Bezugspunkt ersetzbar.
- **Levelaufstieg**: das Ergebnis eines XP-Ereignisses, das eine Schwelle überschreitet — altes
  Level, neues Level, verbleibender Überschuss.
- **Levelanforderung**: ein gefordertes Mindestlevel, gegen das B08, B09 und B11 prüfen.
- **Party**: eine Menge von Spielern mit bereiter Sitzung, die gemeinsam XP erhalten. Trägt genau
  einen Anführer und je Mitglied den Beitrittszeitpunkt, aus dem sich das Dienstalter für die
  Rollenübergabe ergibt. Reiner Laufzeitzustand ohne Speicherung.
- **Party-Einladung**: ein Angebot mit Anführer als Einladendem, Eingeladenem und Zeitpunkt, aus dem
  sich der Verfall ableitet.
- **Reichweitenprüfung**: der austauschbare Bezugspunkt, der beantwortet, welche Mitglieder nah
  genug am gestorbenen Gegner sind. Trägt dessen Ort als Bezug und die konfigurierte Reichweite.
- **XP-Verteilung**: das Ergebnis eines Todesereignisses — je Charakter der zugeschriebene Betrag,
  einschliesslich Nähe-Bonus und einschliesslich der still verfallenen Anteile.
- **Fortschrittsereignis**: der innerhalb eines Fensters zusammengefasste XP-Zuwachs eines
  Charakters, mit aktuellem Level und der Schwelle des nächsten Levels. Reine Meldung, keine
  Anzeige.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Bei einer Schwelle von 100 XP für Level 2 steigt ein Charakter mit genau 100 XP auf
  Level 2 und hat 0 XP Überschuss; mit 250 XP und den Schwellen 100 und 120 steht er auf Level 3
  mit 30 XP.
- **SC-002**: Ein Mob mit konfigurierten 40 XP gibt einem Spieler auf Level 1 und einem Spieler auf
  Level 59 denselben Betrag — nachweisbar über zwei Durchläufe mit identischem Ergebnis.
- **SC-003**: Eine XP-Tabelle mit Lücke, Dublette, nicht positivem Wert oder nicht steigender Folge
  verhindert den Start, und die Meldung nennt in allen vier Fällen das beanstandete Level.
- **SC-004**: 1000 XP-Ereignisse in einer Sekunde erzeugen null Datenbankzugriffe.
- **SC-005**: 10 000 aufeinanderfolgende XP-Ereignisse erzeugen kein vermeidbares Objekt je
  Ereignis.
- **SC-006**: Bei einem Mob mit 100 XP, einer Party aus A und B mit zusammen 60 % Anteil (beide in
  Reichweite), einem Nähe-Bonus von 10 % je zusätzliches Mitglied und einem Einzelnen C mit 40 %
  erhalten A und B je 33 XP (66 XP Party-Anteil, gleichmässig geteilt) und C genau 40 XP.
- **SC-007**: Ist B ausserhalb der Reichweite, erhält A 60 XP ohne Nähe-Bonus und B genau 0.
- **SC-008**: Ein Charakter auf Maximallevel bleibt nach 10 000 XP-Ereignissen unverändert, ohne
  ein einziges veröffentlichtes Aufstiegsereignis und ohne eine Protokollzeile je Ereignis.
- **SC-009**: Ein Aufstieg über drei Level erzeugt genau eine Neuberechnung des Wertestands und
  genau ein Aufstiegsereignis.
- **SC-010**: Nach einem Serverneustart existiert keine Party mehr, während Level und XP jedes
  Charakters unverändert sind.
- **SC-011**: Die Levelanforderungsabfrage antwortet ohne Datenbankzugriff — nachweisbar über einen
  Zugriffszähler, der bei null bleibt.
- **SC-012**: Es läuft keine wiederkehrende Aufgabe je Spieler, je Charakter oder je Party —
  nachweisbar über die Aufgabenanzahl des Schedulers, die unabhängig von der Spielerzahl konstant
  bleibt.
- **SC-013**: Die Summe aller vergebenen XP eines Kills übersteigt den XP-Betrag des Wesens
  höchstens um den konfigurierten Nähe-Bonus — nachweisbar über eine Party in jeder Grösse von 1
  bis zur Obergrenze.
- **SC-014**: Jede Formel und jede Regel dieses Blocks ist ohne laufenden Server unit-getestet
  (Prinzip VII).
- **SC-015**: Die Vanilla-Erfahrung eines Spielers ist nach 1000 eigenen XP-Ereignissen unverändert
  — B06 schreibt nachweisbar nicht in sie.
- **SC-016**: Ein Fortschrittsstand einer älteren Datenversion wird beim Laden ohne Verlust von
  Level oder XP migriert; ein Stand aus einer künftigen Version wird abgelehnt, statt falsch gedeutet
  zu werden.
- **SC-017**: Eine nachträglich verdoppelte XP-Kurve senkt bei keinem bestehenden Charakter das
  Level — nachweisbar über einen geladenen Stand vor und nach dem Kurventausch.
- **SC-018**: 100 XP-Gewinne eines Charakters innerhalb eines Bündelungsfensters erzeugen genau ein
  Fortschrittsereignis, dessen Zuwachs der Summe der 100 Gewinne entspricht.
- **SC-019**: Ein Charakter mit 12 von 100 Leben steht nach einem Aufstieg, der das Maximum auf 110
  hebt, bei 110 von 110 — und bei einem Aufstieg über drei Level auf einmal genau einmal aufgefüllt,
  nicht dreimal.
- **SC-020**: Kreuzt ein XP-Gewinn eine Levelschwelle bei offenem Bündel, treffen die Meldungen in
  genau dieser Reihenfolge ein: Fortschrittsbündel des alten Levels, dann Aufstiegsereignis. Kein
  Ereignis mit dem alten Level erreicht einen Empfänger nach dem Aufstieg.
- **SC-021**: Ein Verwaltungseingriff, der ein Level senkt, erscheint im Audit-Log mit altem und
  neuem Stand, füllt Leben und Mana nicht auf und begrenzt einen über dem neuen Maximum liegenden
  Wert darauf.

## Assumptions

- **Fortschritt gehört zum Charakter.** Bei drei Charakterslots je Account (einer je Klasse) hat
  jeder Charakter sein eigenes Level und seine eigene XP. Der Charakter ist bereits ein
  persistiertes Aggregat aus B02/B03, also braucht B06 kein neues.
- **Das Attributwachstum je Level ist in B06 klassenneutral.** Die klassenspezifischen Basiswerte
  und Wachstumskurven sind ausdrücklich B07 zugeordnet und dort noch offen. B06 liefert eine
  neutrale Vorgabe, damit der Block ohne B07 vollständig testbar ist, und den
  Konfigurationsschlüssel, über den B07 sie ersetzt.
- **Die XP-Beträge je Mob-Art liegen vorerst in B06s eigener Konfiguration.** B10 ersetzt sie
  später über einen austauschbaren Bezugspunkt — genau das Muster, mit dem B05 die Attributwerte
  der Mobs vorläufig selbst stellt.
- **Die Reichweite ist eine Entfernung in derselben Welt.** Die konkrete Zahl ist Balancing und
  steht in der Konfiguration; die Messung selbst liefert die Plattformschicht.
- **Party-Mitglieder müssen online und bereit sein.** Eine Party über Sitzungsgrenzen hinweg würde
  Speicherung erfordern, und die ist ausgeschlossen.
- **XP-Beträge sind ganze Zahlen.** Bruchteile von Erfahrung sind für Spieler nicht sichtbar und
  würden nur Rundungsfragen erzeugen.
- **Ein Levelverlust existiert nicht.** Die Todesstrafe ist Ausrüstungsschaden (B05/B11); eine
  zweite Strafe auf dem Fortschritt ist nicht vorgesehen.
- **Die Vanilla-Erfahrungsleiste bleibt unangetastet oder wird von B13 umgewidmet.** B06 schreibt
  nicht in sie; ob sie später den eigenen Fortschritt spiegelt, entscheidet B13.

## Dependencies

- **B01 Core & Plattform**: Ereignisbus für Aufstiegs- und Party-Ereignisse, Konfigurationsladen mit
  Schemaprüfung für `progression.yml`, Scheduler-Abstraktion, Modulvertrag für die Verdrahtung im
  Plugin (ADR-012).
- **B02 Persistenz**: Write-Behind-Puffer und Änderungsmarkierung, Charakter-Aggregat als Ablageort
  für Level und XP, versionierte Migrationen.
- **B03 Spieler-Session**: Zustand „bereit" als Bedingung für jede XP-Vergabe, aktiver Charakter je
  Sitzung, Sitzungsende als Auslöser für Party-Austritt und Schreibvorgang.
- **B04 Stat-Engine**: die acht Attribute, der Erweiterungspunkt für Grundwerte, die Neuberechnung
  des Wertestands, die Zuordnung Wertträger → Charakter.
- **B05 Kampf- & Schadens-Pipeline**: das Todesereignis mit Schadensaufteilung als Auslöser und
  Grundlage der Verteilung. Bereits entschieden und umgesetzt: XP anteilig nach Schadensanteil,
  Vanilla-XP-Kugeln unterdrückt.
- **ADR-008**: nur die acht Attribute — Grundlage dafür, dass ein Level-Up genau diese und keine
  Sekundärwerte anhebt.
- **ADR-011**: Fortschritt hängt am Charakter, nicht am Account.
- **Später abhängig von B06**: B07 (klassenspezifisches Wachstum), B08 (Freischaltung per Level),
  B09 (Levelanforderung je Zone, Zonen-Ziele als XP-Quelle, Reichweitenprüfung), B11
  (Levelanforderung je Item), B12 (Fortschrittsstatistik), B13 (Anzeige von Fortschritt und Party),
  B14 (Party-Befehle).
