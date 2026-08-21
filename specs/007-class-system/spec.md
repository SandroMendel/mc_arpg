# Feature Specification: B07 · Klassen-System

**Feature Branch**: `007-class-system`

**Created**: 2026-08-21

**Status**: Draft

**Input**: Blocksteckbrief `blocks/B07-class-system.md` — drei wählbare Spielerklassen mit eigenem
Rollenprofil, Basiswerten, Wachstumskurven, Ausrüstungsleitern und Fähigkeits-Loadout. Hängt ab von
B03 (Sitzung, Charakterrahmen), B04 (Stat-Engine) und B06 (Level); wird benötigt von B08, B11 und
B13. Verbindlich: Prinzip II (kein Datenbankzugriff je Spielereignis, keine wiederkehrende Aufgabe je
Spieler), Prinzip III (`rpg-core` ohne Bukkit-Abhängigkeit), Prinzip IV (Cache ist autoritativ
während der Sitzung), Prinzip V (alle Balancing-Zahlen in validierter Konfiguration), ADR-005
(Vanilla-Materialien, keine Client-Voraussetzung), ADR-008 (nur die acht Attribute, Caps),
ADR-011 (der Charakter trägt Fortschritt, nicht das Konto), **ADR-017** (Rüstung und Waffe sind
Klassenprogression), **ADR-018** (charaktergebundene Items sind unbeweglich), **ADR-019** (drei
Klassen im Code, Inhalt in Konfiguration), **ADR-020** (vor der Klassenwahl kein Spielzustand).

## Clarifications

### Session 2026-08-21

- Q: Wie löst B07 die Forderung „vierte Klasse rein per Konfiguration"? → A: Die **Menge** der
  Klassen bleibt im Code, der **Inhalt** ist Konfiguration (ADR-019). `CharacterClass` und die
  Prüfbedingung aus B03 bleiben unangetastet; eine vierte Klasse ist ein späteres Upgrade und kostet
  einen Enum-Wert plus Migration. Das ursprüngliche Akzeptanzkriterium des Steckbriefs war damit
  falsch und wurde korrigiert: getestet wird die Gegenrichtung — der Klassenlader weist eine
  unbekannte Klassen-ID ausdrücklich ab, womit belegt ist, dass keine dritte Stelle über die
  Klassenmenge mitentscheidet.

- Q: Waffen- und Rüstungsbeschränkungen je Klasse? → A: Kein Filter, sondern ein **fester Pfad**. Je
  Klasse genau eine Rüstungs- und eine Waffenleiter mit festen Werten; die Länge je Leiter ist
  Konfiguration, keine feste Zahl. Das ersetzt
  Beute-Ausrüstung und revidiert ADR-004 für diese beiden Slots (ADR-017). Ein Filter hätte die Frage
  „welches der vielen Items darf ich tragen" beantwortet; es gibt aber nur ein Item je Slot und
  Stufe, also ist die Frage gegenstandslos.

- Q: Wer besitzt die Sperre gegen Ablegen und Droppen? → A: B07, nicht B11 (ADR-018). Die Bindung ist
  eine Klassenregel: die Rüstung gehört der Klasse, nicht dem Fundstück. Ein Item kann nicht wissen,
  ob es gebunden ist — die Klasse weiß es. Zudem braucht die Sperre kein Item-System und ist damit ab
  M3 wirksam statt erst ab M4.

- Q: Was passiert vor der Klassenwahl? → A: Kein Spielzustand (ADR-020). Die Auswahl öffnet sich nach
  dem Laden der Sitzung und lässt sich nicht schließen; bis zur Wahl kein Stat-Snapshot, kein
  Schaden, keine Bewegung. Dadurch brauchen B04 und B05 **keinen** „kein Charakter"-Fall — der
  Zustand ist per Konstruktion nicht spielbar, statt an jeder Stelle abgefragt zu werden. Die
  Tutorialwelt aus ADR-006 bleibt davor nachrüstbar, ohne B07 anzufassen.

- Q: Ist die Klasse Warrior oder Berserker? → A: Der Enum-Wert bleibt `WARRIOR`; „Berserker" ist der
  Anzeigename aus der Konfiguration. Das deckt sich mit B08, wo die Unique Ability des Warrior
  „Call of the Berserker" heißt.

- Q: Wie viele Fähigkeiten hat eine Klasse? → A: Vier aktive und zwei passive, insgesamt **sechs**;
  die Unique Class Ability zählt als eine der vier aktiven. Die frühere Fassung von B08 sagte „ohne
  Unique Class Ability" und ergab damit sieben, was auf keinen der drei festgelegten Loadouts passte.

- Q: Warrior und Rogue tragen beide ein Schwert und sehen auf fünf von sechs Stufen identisch aus —
  soll der Rogue stattdessen eine Axt bekommen? → A: Nein, **Schwerter bleiben für beide**. Die
  sichtbare Klassenidentität trägt damit die Rüstung, nicht die Waffe: Warrior schwere Rüstung, Rogue
  Gold und Kettenhemd, Mage gefärbtes Leder. Die Waffe zeigt nur die Stufe. FR-016 bleibt erfüllt,
  weil es Unterscheidbarkeit innerhalb einer Leiter fordert, nicht zwischen Klassen.

### Abgeleitete Entscheidungen

Diese Punkte sind nicht gefragt worden, folgen aber zwingend aus dem Obigen und werden hier
festgehalten, damit sie nicht später als Annahme erfunden werden:

- **Die Ausrüstungsstufe ist Charakterstand, nicht Itemzustand.** Weil es je Slot und Stufe genau ein
  Item gibt, ist „welche Stufe trage ich" eine Zahl am Charakter und keine Eigenschaft eines
  Gegenstands. Zwei Charaktere derselben Klasse auf derselben Stufe sind wertgleich — das ist die
  Eigenschaft, die Beute-Ausrüstung gerade nicht hat.
- **Zwei Leitern, nicht eine.** Die Rüstung trägt die vier defensiven Attribute, die Waffe die vier
  offensiven. Jedes Attribut hat damit genau eine Leiter als Quelle. Läge alles auf einer Leiter,
  wäre die zweite Bedeutungslos.
- **Der Anteil der Leiter am Wachstum liegt bei etwa 70 %.** ADR-008 verlangt „Ausrüstung dominant";
  ADR-017 wechselt nur die Quelle, nicht die Höhe. Das Levelwachstum trägt die restlichen 30 %.
- **Das klassenneutrale Levelwachstum aus B06 wird je Klasse ersetzt, nicht ergänzt.** B06 hat diese
  Ersetzbarkeit ausdrücklich vorgesehen (FR-022). Ein Ergänzen hätte die Summe verdoppelt.
- **Die Leiterlänge ist Konfiguration, nicht Code.** Warrior 5/6, Rogue 6/6, Mage 7/7 — Rüstung und
  Waffe einer Klasse dürfen unterschiedlich lang sein. Alle erreichen denselben Endwert; nur die
  Schrittweite unterscheidet sich. Eine feste Zahl im Code hätte die Materiallisten beschneiden
  müssen, statt sie abzubilden.
- **Sichtbarkeit ist eine Anforderung, nicht Kosmetik.** Der Mage bleibt durchgehend auf Leder, der
  Rogue ab Stufe 4 auf Kettenhemd. Ohne Färbung beziehungsweise Trim wäre ihre Progression eine reine
  Zahl. Damit wandern Farbe und Trim aus dem Addon in die Pflicht — für zwei der drei Klassen.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ein neuer Spieler wählt seine Klasse (Priority: P1)

Als neuer Spieler betrete ich den Server. Es öffnet sich eine Auswahl mit drei Klassen, die ich nicht
wegklicken kann. Ich sehe je Klasse Namen, Rollenprofil und Startwerte. Nach der Wahl existiert mein
Charakter, und ich stehe im Spiel.

**Why this priority**: Ohne Klasse gibt es keinen Charakter, und ohne Charakter greift kein anderer
Block. Dies ist die einzige Geschichte, die ohne jede andere einen Nutzen liefert.

**Independent Test**: Vollständig prüfbar durch einen Beitritt ohne bestehenden Charakter: die Auswahl
erscheint, jeder Versuch sie zu verlassen führt zurück, und nach der Wahl existiert ein Charakter der
gewählten Klasse.

**Acceptance Scenarios**:

1. **Given** ein Spieler ohne Charakter, **When** seine Sitzung geladen ist, **Then** ist die
   Klassenauswahl geöffnet und zeigt genau drei Klassen.
2. **Given** die geöffnete Auswahl, **When** der Spieler sie zu schließen versucht — Escape,
   Inventartaste, Befehl, Weltwechsel —, **Then** ist sie danach weiterhin geöffnet.
3. **Given** die geöffnete Auswahl, **When** der Spieler eine Klasse wählt, **Then** existiert ein
   Charakter dieser Klasse, die Auswahl ist geschlossen, und der Spielzustand ist betreten.
4. **Given** ein Spieler ohne Charakter, **When** eine Schadensquelle ihn treffen würde, **Then**
   nimmt er keinen Schaden.
5. **Given** ein Spieler ohne Charakter, **When** er sich zu bewegen versucht, **Then** bleibt er an
   seiner Position.
6. **Given** ein Spieler mit bereits gewählter Klasse, **When** er beitritt, **Then** erscheint keine
   Auswahl, und er betritt unmittelbar den Spielzustand.
7. **Given** ein Spieler, der die Verbindung während der geöffneten Auswahl verliert, **When** er
   erneut beitritt, **Then** ist die Auswahl wieder geöffnet und kein Charakter angelegt.

---

### User Story 2 - Die Klasse bestimmt die Werte des Charakters (Priority: P1)

Als Spieler unterscheiden sich meine acht Attribute nach meiner Klasse. Ein Warrior hat mehr Leben
und Verteidigung, ein Rogue schlägt schneller und läuft schneller, ein Mage hat mehr Mana und
magischen Schaden. Mit jedem Level wachsen meine Werte nach der Kurve meiner Klasse.

**Why this priority**: Ohne unterschiedliche Werte ist die Klassenwahl eine Beschriftung. Dies ist
der Punkt, an dem die Wahl spürbar wird.

**Independent Test**: Prüfbar ohne Ausrüstung und ohne Fähigkeiten: drei Charaktere je Klasse auf
Level 1 haben die dokumentierten Basiswerte; nach einem Levelaufstieg haben sie den dokumentierten
Zuwachs.

**Acceptance Scenarios**:

1. **Given** ein frisch angelegter Charakter je Klasse, **When** seine Werte gelesen werden, **Then**
   entsprechen sie den Basiswerten seiner Klasse aus der Konfiguration.
2. **Given** ein Charakter auf Level 1, **When** er Level 2 erreicht, **Then** sind seine Werte um den
   Zuwachs seiner Klasse gestiegen, nicht um den klassenneutralen Zuwachs aus B06.
3. **Given** drei Charaktere derselben Stufe und desselben Levels in verschiedenen Klassen, **When**
   ihre Werte verglichen werden, **Then** hat der Warrior das höchste Leben, der Rogue die höchste
   Angriffsgeschwindigkeit und der Mage das höchste Mana.
4. **Given** einen Charakter auf Maximallevel 60 auf der Endstufe beider Leitern, **When** seine Werte gelesen werden,
   **Then** liegt jedes Attribut innerhalb der Wertebereiche und Caps aus ADR-008.
5. **Given** einen Beitrag der Klasse zur Stat-Engine, **When** die Quellen aufgeschlüsselt werden,
   **Then** trägt er die Quelle „Klasse" und keine andere.
6. **Given** einen Charakter, dessen Klassenwerte einen Cap überschreiten würden, **When** die Werte
   berechnet werden, **Then** greift der Cap aus B04, und der Startvorgang schlägt nicht fehl.

---

### User Story 3 - Die Ausrüstung steigt entlang der Klassenleiter auf (Priority: P2)

Als Spieler trage ich von Anfang an die Rüstung und Waffe meiner Klasse. Sie wird nicht gefunden,
sondern aufgewertet, und jede Stufe ist sichtbar — an Material, Färbung oder Trim. Meine erreichte
Stufe bleibt über Relogin und Serverneustart erhalten.

**Why this priority**: Trägt nach ADR-017 den größten Teil der Endpower. Ohne die Leitern ist ein
Charakter auf Level 60 kaum stärker als auf Level 1.

**Independent Test**: Prüfbar ohne Beute und ohne Kosten: eine Stufe wird über die Schnittstelle
weitergeschaltet, die Werte steigen um den dokumentierten Betrag, das Material wechselt, und die
Stufe übersteht einen Neustart.

**Acceptance Scenarios**:

1. **Given** ein frisch angelegter Charakter, **When** seine Ausrüstung betrachtet wird, **Then**
   trägt er Rüstung und Waffe der Stufe 1 seiner Klasse.
2. **Given** ein Charakter auf Rüstungsstufe 2, **When** die Stufe weitergeschaltet wird, **Then**
   ist er auf Stufe 3, seine defensiven Werte sind um den Unterschied gestiegen, und das Material
   entspricht Stufe 3.
3. **Given** ein Charakter auf der höchsten Stufe, **When** ein weiteres Weiterschalten versucht wird,
   **Then** wird es abgewiesen, und die Stufe bleibt unverändert.
4. **Given** ein Charakter, der die Levelanforderung der nächsten Stufe nicht erfüllt, **When** ein
   Weiterschalten versucht wird, **Then** wird es abgewiesen und begründet.
5. **Given** ein Charakter auf Waffenstufe 4, **When** der Server neu startet und der Spieler erneut
   beitritt, **Then** ist er weiterhin auf Waffenstufe 4.
6. **Given** die Rüstungs- und die Waffenstufe eines Charakters, **When** eine von beiden
   weitergeschaltet wird, **Then** bleibt die andere unverändert.
7. **Given** ein Weiterschalten der Stufe, **When** die Werte neu berechnet werden, **Then** löst es
   **genau eine** Neuberechnung aus.

---

### User Story 4 - Gebundene Ausrüstung lässt sich nicht verlieren (Priority: P2)

Als Spieler kann ich meine Klassenrüstung und -waffe nicht ablegen, verschieben, verkaufen oder
wegwerfen — sie sind Bestandteil meines Charakters. Alles andere in meinem Inventar kann ich frei
bewegen. Werfen kann ich nichts; wenn mein Inventar voll ist, werde ich gewarnt und schaffe selbst
Platz.

**Why this priority**: Schützt die Progression aus US3 gegen Verlust und Umgehung. Ohne diese
Geschichte ließe sich die Leiter durch Ablegen aushebeln.

**Independent Test**: Prüfbar mit zwei Items im Inventar — einem gebundenen und einem ungebundenen:
jede Bewegungsroute wird für das gebundene abgewiesen und für das ungebundene erlaubt.

**Acceptance Scenarios**:

1. **Given** ein Charakter mit gebundener Rüstung, **When** er einen Rüstungsslot anklickt, **Then**
   bleibt die Rüstung, wo sie ist.
2. **Given** ein Charakter mit gebundener Waffe, **When** er sie per Slot-Tausch, Shift-Klick,
   Hotbar-Tausch oder Offhand-Tausch zu verschieben versucht, **Then** bleibt sie in ihrem Slot.
3. **Given** ein Charakter mit einem **ungebundenen** Item, **When** er es innerhalb des Inventars
   verschiebt, **Then** gelingt es.
4. **Given** ein Charakter mit einem beliebigen Item, **When** er die Wurf-Aktion ausführt, **Then**
   landet kein Item in der Welt.
5. **Given** einen sterbenden Mob, **When** seine Beutetabelle greift, **Then** fallen Items wie
   vorgesehen — die Sperre gilt nur für Spieler.
6. **Given** ein Charakter mit vollem Inventar, **When** weitere Beute anfällt, **Then** erhält er
   eine Warnung, und es wird nichts stillschweigend verworfen.
7. **Given** eine Abfrage, ob ein Item Bestandteil des Charakters ist, **When** sie für die
   Klassenrüstung, die Klassenwaffe und ein beliebiges anderes Item gestellt wird, **Then** lautet
   die Antwort ja, ja, nein.

---

### User Story 5 - Die Klasse benennt ihre Fähigkeiten (Priority: P3)

Als Spieler gehören zu meiner Klasse sechs Fähigkeiten — vier aktive einschließlich der Unique
Ability und zwei passive —, die mit steigendem Level freigeschaltet werden. Welche das sind, hängt an
meiner Klasse.

**Why this priority**: B07 liefert nur die Bindung; das Verhalten kommt mit B08. Für sich allein
sichtbar nur als Liste, aber ohne diese Bindung kann B08 nicht anfangen.

**Independent Test**: Prüfbar ohne jede Fähigkeitslogik: die Klasse nennt genau sechs
Fähigkeits-IDs mit Art und Freischaltstufe, und eine Klasse ohne eingetragene Fähigkeiten wird beim
Laden abgewiesen.

**Acceptance Scenarios**:

1. **Given** die Klassenkonfiguration des Warrior, **When** seine Fähigkeitsbindung gelesen wird,
   **Then** nennt sie sechs IDs: vier aktive einschließlich der Unique und zwei passive.
2. **Given** eine Klasse mit fünf oder sieben eingetragenen Fähigkeiten, **When** die Konfiguration
   geladen wird, **Then** wird sie mit benannter Ursache abgewiesen.
3. **Given** eine Fähigkeit mit Freischaltstufe 20, **When** ein Charakter auf Level 19 seine
   freigeschalteten Fähigkeiten liest, **Then** ist sie nicht darunter; auf Level 20 ist sie es.
4. **Given** eine Freischaltstufe über dem Maximallevel, **When** die Konfiguration geladen wird,
   **Then** wird sie abgewiesen — eine nie erreichbare Fähigkeit ist ein Konfigurationsfehler.

---

### User Story 6 - Der Betreiber justiert Klassen ohne Codeänderung (Priority: P3)

Als Betreiber ändere ich Basiswerte, Wachstumskurven, Stufenwerte, Materialien, Anzeigenamen und
Fähigkeitsbindungen in der Konfiguration. Ein Neustart genügt. Eine unbekannte Klasse in der Datei
bekomme ich als Fehler gemeldet, nicht als stille Auslassung.

**Why this priority**: Prinzip V und das korrigierte Akzeptanzkriterium aus ADR-019. Für Spieler
unsichtbar, aber es entscheidet, ob Balancing nach dem Release möglich ist.

**Independent Test**: Prüfbar ohne laufenden Server: eine geänderte Konfiguration wird geladen und
liefert geänderte Werte; eine Konfiguration mit einer vierten Klassen-ID wird abgewiesen.

**Acceptance Scenarios**:

1. **Given** eine geänderte Basiswertzahl in der Konfiguration, **When** der Server startet, **Then**
   haben neue und bestehende Charaktere dieser Klasse den geänderten Wert.
2. **Given** eine Konfiguration mit einer unbekannten Klassen-ID, **When** sie geladen wird, **Then**
   schlägt der Start mit Nennung der unbekannten ID fehl.
3. **Given** eine Konfiguration, in der eine der drei bekannten Klassen fehlt, **When** sie geladen
   wird, **Then** schlägt der Start mit Nennung der fehlenden Klasse fehl.
4. **Given** eine Konfiguration mit weniger Stufen als ein bestehender Charakter erreicht hat,
   **When** sie geladen wird, **Then** schlägt der Start fehl, statt den Charakter herabzustufen.
5. **Given** einen geänderten Anzeigenamen, **When** ein Spieler die Auswahl öffnet, **Then** sieht er
   den geänderten Namen.

---

### Edge Cases

- **Ein Spieler hat schon drei Charaktere und wählt erneut.** Alle drei Klassen sind belegt; die
  Auswahl darf keine vierte Wahl anbieten. B03 erzwingt „höchstens ein Charakter je Klasse" bereits
  beim Anlegen, aber die Auswahl muss es *vorher* anzeigen, statt erst beim Anlegen zu scheitern.
- **Zwei Beitritte desselben Kontos gleichzeitig**, beide in der Auswahl, beide wählen dieselbe
  Klasse. Genau einer darf gewinnen; der andere bekommt eine benannte Ablehnung, keinen Absturz.
- **Ein Charakter existiert, aber seine Klasse steht nicht mehr in der Konfiguration.** Darf nicht
  vorkommen, weil der Start dann fehlschlägt (US6.3) — aber die Reihenfolge muss stimmen: die
  Konfiguration wird geprüft, bevor der erste Charakter geladen wird.
- **Die Levelanforderung einer Stufe wird nachträglich erhöht** über das Level eines Charakters, der
  diese Stufe schon trägt. Der Charakter behält seine Stufe; die Anforderung gilt nur beim
  Weiterschalten.
- **Ein gebundenes Item verschwindet trotzdem** — durch einen Vanilla-Mechanismus, den niemand
  vorhergesehen hat, oder durch einen Administratoreingriff. Der Zustand „Stufe erreicht, aber Item
  fehlt" muss sich beim nächsten Laden selbst heilen, weil die Stufe der Charakterstand ist und das
  Item daraus abgeleitet wird — nicht umgekehrt.
- **Ein Attribut überschreitet durch Klassenwerte plus Levelwachstum plus Stufe seinen Cap.** Der Cap
  aus B04 greift; das ist kein Fehler, aber es macht weitere Stufen wirkungslos, und genau das muss
  die Wertübersicht sichtbar machen.
- **Die Klassenwaffe bringt eigene Vanilla-Modifikatoren mit.** Schwert, Axt und Speer verändern in
  Vanilla die Angriffsgeschwindigkeit unterschiedlich stark. Ohne Neutralisierung wäre der Rogue auf
  der Axt trotz +50 % die langsamste Klasse. Der Fall muss je Waffentyp geprüft werden, nicht nur
  einmal (FR-046, FR-047).
- **Ein klassenexklusiver Rüstungssatz taucht in zwei Leitern auf.** Muss beim Laden auffallen, nicht
  im Spiel — sonst tragen zwei Klassen dasselbe und die visuelle Unterscheidung ist still verloren
  (FR-016a).
- **Die Auswahl ist geöffnet, und der Server fährt herunter.** Kein halb angelegter Charakter darf
  zurückbleiben.
- **Angriffsgeschwindigkeit außerhalb des Modifier-Bands.** B05 rechnet sein Angriffsfenster gegen
  den *geklemmten* Wert; eine Klassenkonfiguration, die das Band verlässt, würde Anzeige und
  Serverrate auseinanderlaufen lassen. Die Prüfung gehört in die Konfigurationsvalidierung.

## Requirements *(mandatory)*

### Functional Requirements — Klassendefinition und Konfiguration

- **FR-001**: Das System MUSS je Klasse Basiswerte für **alle acht** Attribute führen; ein fehlender
  Wert ist ein Startfehler, kein stilles Null.
- **FR-002**: Das System MUSS je Klasse und Attribut eine eigene Zuwachsrate je Level zulassen, Null
  eingeschlossen; alle acht sind Pflichtfelder.
- **FR-003**: Das System MUSS das klassenneutrale Levelwachstum aus B06 durch die klassenspezifische
  Rate **ersetzen**, nicht ergänzen.
- **FR-004**: Das System MUSS je Klasse einen Anzeigenamen und ein Vanilla-Material für die Auswahl
  aus der Konfiguration lesen.
- **FR-005**: Das System MUSS genau die drei bekannten Klassen-IDs erwarten. Eine unbekannte ID MUSS
  den Start mit Nennung der ID abbrechen; eine fehlende bekannte Klasse ebenso.
- **FR-006**: Das System DARF für keine Klasse einen Sonderfall in der Logik enthalten; jede
  Unterscheidung MUSS aus Daten kommen.
- **FR-007**: Das System MUSS die Konfiguration vollständig prüfen, **bevor** der erste Charakter
  geladen wird.
- **FR-008**: Das System MUSS Klassenwerte gegen die Wertebereiche und Caps aus ADR-008 prüfen und
  eine Angriffsgeschwindigkeit außerhalb des Modifier-Bands als Startfehler melden.

### Functional Requirements — Klasse als Stat-Quelle

- **FR-009**: Das System MUSS Basiswerte, Levelwachstum und die Werte der erreichten Stufen als
  **eine** Quelle „Klasse" an die Stat-Engine liefern, und zwar als **Basiswerte**, nicht als
  Modifikatoren. *(präzisiert in der Planungsphase, siehe research.md R1: das Modifikatorband aus B04
  wird um den effektiven Basiswert gelegt. Bei rund 70 % Leiteranteil bliebe ein Band, das am
  Level-1-Wert hängt, auf der Endstufe grob falsch und würde Werte unbemerkt klammern.)*
- **FR-010**: Das System DARF die Werte der Klasse nicht selbst verrechnen; Stapelung, Reihenfolge
  und Caps liegen bei B04.
- **FR-010a**: Das System DARF die Modifikatorquelle „Klasse" NICHT belegen; sie bleibt für spätere,
  tatsächlich modifikatorförmige Klasseneffekte frei. Es gibt genau **einen** Basisbeitrag der Klasse
  je Attribut, also nichts zu sortieren.
- **FR-011**: Das System MUSS bei einem Levelaufstieg und bei einem Stufenaufstieg **genau eine**
  Neuberechnung auslösen.
- **FR-012**: Das System DARF durch die Klassenwerte KEINEN Datenbankzugriff je Spielereignis
  auslösen; die Klassendefinition MUSS im Speicher liegen.

### Functional Requirements — Ausrüstungsleitern

- **FR-013**: Das System MUSS je Klasse **genau eine** Rüstungsleiter und **genau eine** Waffenleiter
  führen. Die **Anzahl der Stufen ist je Leiter konfiguriert** und DARF sich zwischen Klassen und
  zwischen den beiden Leitern einer Klasse unterscheiden. Eine Leiter MUSS mindestens zwei Stufen
  haben — mit einer Stufe gäbe es keinen Aufstieg.
- **FR-013a**: Das System MUSS die Wertekurve und die Levelanforderungen auf die **eigene Länge** der
  jeweiligen Leiter normieren, sodass die Endstufe jeder Leiter ihren Zielwert erreicht, unabhängig
  davon, über wie viele Stufen sie dorthin führt.
- **FR-014**: Das System MUSS je Stufe **feste** Attributwerte führen; es DARF keine Zufallskomponente
  geben.
- **FR-015**: Das System MUSS die Rüstungsleiter Leben, Verteidigung, Mana und Laufgeschwindigkeit
  tragen lassen und die Waffenleiter physischen Schaden, magischen Schaden, Angriffsgeschwindigkeit
  und Fähigkeiten-Cooldown. Jedes Attribut hat damit genau eine Leiter als Quelle.
- **FR-016**: Das System MUSS je Stufe ein **unterscheidbares Erscheinungsbild** führen. Unterschieden
  werden DARF über Material, Färbung oder Trim — aber zwei Stufen derselben Leiter DÜRFEN NICHT in
  allen drei übereinstimmen. Ein Verstoß ist ein Startfehler, weil eine unsichtbare Stufe die
  Progression auf eine Zahl reduziert.
- **FR-016a**: Das System MUSS für Stufen, deren Material sich nicht von der Vorstufe unterscheidet,
  eine Färbung oder einen Trim als **Pflichtfeld** verlangen. Das betrifft alle Lederstufen des Mage
  und die Kettenhemdstufen des Rogue.
- **FR-016b**: Das System MUSS eine Färbung nur für färbbare Materialien zulassen. Leder ist färbbar,
  Gold und Kettenhemd sind es nicht; eine Färbung auf einem nicht färbbaren Material ist ein
  Startfehler, keine stille Auslassung.
- **FR-016c**: Das System MUSS die Rüstungsfamilien der Klassen getrennt halten: ein Rüstungssatz
  DARF außer dem gemeinsamen Einstiegsmaterial in der Leiter höchstens einer Klasse auftauchen. Ein
  Verstoß ist ein Startfehler. Für **Waffen** gilt diese Trennung ausdrücklich NICHT — sie werden über
  den Waffentyp und die Materialfolge unterschieden.
- **FR-017**: Das System MUSS die Attributwerte einer Leiter über die Stufen streng steigend
  erwarten; eine nicht steigende Leiter ist ein Startfehler.
- **FR-018**: Das System MUSS je Stufe eine Levelanforderung führen und ein Weiterschalten unterhalb
  dieser Anforderung mit benannter Ursache abweisen.
- **FR-019**: Das System MUSS die erreichte Rüstungs- und Waffenstufe je Charakter persistent halten,
  getrennt voneinander.
- **FR-020**: Das System MUSS ein Weiterschalten über die höchste Stufe hinaus abweisen.
- **FR-021**: Das System MUSS je Stufe einen **undurchsichtigen** Kostenblock durchreichen, ohne ihn
  auszulegen. Wer den Aufstieg bezahlt, entscheiden B11 und B16.
- **FR-022**: Das System MUSS je Stufe Felder für Färbung und Trim führen. Sie sind **kein
  reservierter Platzhalter für später**: wo das Material allein die Stufe nicht sichtbar macht, sind
  sie nach FR-016a verbindlich. Nur beim Warrior bleiben sie durchgehend optional.
- **FR-023**: Das System MUSS die getragenen Gegenstände aus der erreichten Stufe **ableiten**; die
  Stufe ist die Quelle der Wahrheit, nicht das Item. Ein fehlendes gebundenes Item MUSS beim Laden
  wiederhergestellt werden.
- **FR-024**: Das System MUSS beim Laden einer Konfiguration mit weniger Stufen, als ein bestehender
  Charakter erreicht hat, den Start abbrechen, statt den Charakter herabzustufen.

### Functional Requirements — Bindung und Inventarsperre

- **FR-025**: Das System MUSS die Frage „ist dieses Item Bestandteil des Charakters?" beantworten und
  diese Antwort anderen Blöcken zur Verfügung stellen.
- **FR-026**: Das System MUSS jede Bewegung eines gebundenen Items abweisen: Klick auf einen
  Rüstungsslot, Slot-Tausch, Shift-Klick, Hotbar-Tausch, Offhand-Tausch und Wurf-Aktion.
- **FR-027**: Das System MUSS die Wurf-Aktion für **alle** Items eines Spielers abweisen, auch für
  ungebundene.
- **FR-028**: Das System DARF die Bewegung **ungebundener** Items innerhalb des Inventars NICHT
  behindern.
- **FR-029**: Das System DARF Beute von Mobs NICHT behindern; die Sperre ist ausschließlich
  spielerseitig.
- **FR-030**: Das System MUSS bei vollem Inventar und anfallender Beute eine Warnung ausgeben und DARF
  nichts stillschweigend verwerfen, automatisch einlagern oder aufräumen.
- **FR-031**: Das System MUSS jede Abweisung so protokollieren, dass eine fehlende Route auffindbar
  ist, ohne den Spieler mit Meldungen zu überschütten.

### Functional Requirements — Klassenwahl

- **FR-032**: Das System MUSS die Klassenauswahl öffnen, sobald die Sitzung eines Spielers ohne
  Charakter geladen ist.
- **FR-033**: Das System MUSS jeden Weg aus der geöffneten Auswahl heraus zurück in die Auswahl
  führen.
- **FR-034**: Das System DARF für einen Spieler ohne Charakter KEINEN Stat-Snapshot führen, KEINEN
  Schaden zulassen und KEINE Bewegung zulassen.
- **FR-035**: Das System MUSS in der Auswahl nur Klassen anbieten, für die das Konto noch keinen
  Charakter hat.
- **FR-036**: Das System MUSS die Wahl so anlegen, dass bei gleichzeitigen Beitritten desselben Kontos
  genau einer gewinnt und der andere eine benannte Ablehnung erhält.
- **FR-037**: Das System DARF bei einem Verbindungsabbruch während der Auswahl KEINEN halb angelegten
  Charakter zurücklassen.
- **FR-038**: Das System MUSS die getroffene Wahl persistent halten; sie MUSS Relogin und
  Serverneustart überstehen.
- **FR-039**: Das System DARF einen Klassenwechsel NICHT zulassen; die Klasse ist permanent.
- **FR-040**: Das System MUSS die Auswahl mit Vanilla-Materialien darstellen und DARF keine
  Client-Voraussetzung schaffen.

### Functional Requirements — Fähigkeitsbindung

- **FR-041**: Das System MUSS je Klasse **genau sechs** Fähigkeits-IDs führen: vier aktive
  einschließlich der Unique Class Ability und zwei passive. Eine andere Anzahl ist ein Startfehler.
- **FR-042**: Das System MUSS je Fähigkeit eine Freischaltstufe führen und eine Stufe über dem
  Maximallevel als Startfehler melden.
- **FR-043**: Das System MUSS die freigeschalteten Fähigkeiten eines Charakters aus seinem Level
  ableiten.
- **FR-044**: Das System DARF das Verhalten einer Fähigkeit NICHT umsetzen; B07 benennt nur die
  Bindung.
- **FR-045**: Das System MUSS eine leere Bindung für eine Klasse zulassen, solange B08 fehlt — aber
  eine **teilweise** gefüllte MUSS abgewiesen werden.

### Functional Requirements — Keine unmodellierte Wertquelle

- **FR-046**: Das System MUSS die **eigenen Attributmodifikatoren** der Klassenwaffe und der
  Klassenrüstung neutralisieren, sodass ausschließlich die acht Attribute aus B04 die Werte des
  Charakters bestimmen (ADR-008). Insbesondere DARF der Waffentyp die Angriffsgeschwindigkeit nicht
  verändern.
- **FR-047**: Das System MUSS sicherstellen, dass die effektive Angriffsgeschwindigkeit eines
  Charakters bei gleichem Attributwert **unabhängig vom Waffentyp** ist. Andernfalls rechnet B05 sein
  Angriffsfenster gegen einen anderen Wert, als die Anzeige zeigt.
- **FR-048**: Das System DARF sich für die Werte eines gebundenen Gegenstands NICHT auf dessen
  Vanilla-Eigenschaften stützen — Materialstufe und Werte sind unabhängig konfiguriert. Eine
  Netherite-Stufe ist stark, weil die Konfiguration es sagt, nicht weil Netherite es ist.

### Key Entities

- **Klassendefinition**: der vollständige Inhalt einer Klasse — Anzeigename, Auswahlmaterial,
  Basiswerte für acht Attribute, Zuwachsraten für acht Attribute, zwei Ausrüstungsleitern und die
  Fähigkeitsbindung. Wird aus der Konfiguration geladen, ist unveränderlich und liegt genau einmal im
  Speicher, nicht je Spieler.
- **Ausrüstungsleiter**: die Stufen eines Slots; ihre Anzahl ist je Leiter konfiguriert. Je Stufe:
  Vanilla-Material, optional Färbung und Trim, feste Attributwerte,
  Levelanforderung, undurchsichtiger Kostenblock, Kosmetikfeld.
- **Klassenstand des Charakters**: erreichte Rüstungsstufe und erreichte Waffenstufe. Der einzige
  veränderliche und persistente Teil; hängt am Charakter, nicht am Konto (ADR-011).
- **Fähigkeitsbindung**: sechs Einträge aus Fähigkeits-ID, Art (aktiv oder passiv), Merkmal
  „Unique" und Freischaltstufe. B07 hält sie, B08 löst die IDs auf.
- **Bindungsauskunft**: die Antwort auf „ist dieses Item Bestandteil des Charakters?". Wird von B11
  bei jeder Bewegungs-, Verkaufs- und Wegwerfroute abgefragt.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Ein neuer Spieler hat innerhalb von 30 Sekunden nach dem Beitritt einen spielbaren
  Charakter, ohne eine Anleitung zu lesen.
- **SC-002**: In 100 von 100 Versuchen führt kein Weg aus der Klassenauswahl heraus in den
  Spielzustand ohne getroffene Wahl.
- **SC-003**: In 100 von 100 Versuchen bleibt ein gebundenes Item nach jeder geprüften
  Bewegungsroute in seinem Slot, und ein ungebundenes lässt sich in allen Fällen bewegen.
- **SC-004**: Jedes der acht Attribute liegt für jede Klasse auf Level 1 mit Stufe 1 und auf Level 60
  auf der Endstufe innerhalb der Wertebereiche aus ADR-008, mit einer Abweichung unter 3 %.
- **SC-005**: Für die fünf Attribute mit Levelwachstum tragen die Ausrüstungsleitern je Klasse
  zwischen 60 % und 80 % des Wertzuwachses von Level 1 Stufe 1 bis Level 60 Endstufe — nachweisbar
  dominant gegenüber dem Level. Die drei prozentualen Attribute kommen vollständig aus der Leiter und
  sind von dieser Spanne ausgenommen.
- **SC-006**: Die Klassenwahl und beide erreichten Stufen überstehen Relogin und Serverneustart in
  100 von 100 Fällen verlustfrei.
- **SC-007**: Basiswerte, Wachstumskurven, Stufenwerte, Materialien und Anzeigenamen aller drei
  Klassen sind ohne eine einzige Codeänderung austauschbar; ein Test weist es für jede der fünf
  Kategorien nach.
- **SC-008**: Eine Konfiguration mit einer unbekannten Klassen-ID bricht den Start in 100 von 100
  Fällen ab und nennt die ID.
- **SC-009**: Ein Levelaufstieg und ein Stufenaufstieg lösen je **genau eine** Neuberechnung aus,
  gemessen über alle acht Attribute.
- **SC-010**: Weder die Klassenwerte noch die Bindungsauskunft lösen einen Datenbankzugriff je
  Spielereignis aus, nachweisbar bei 10 000 Abfragen.
- **SC-011**: Zwei Charaktere mit identischem Angriffsgeschwindigkeitswert, aber unterschiedlichem
  Waffentyp schlagen mit derselben Rate zu — Abweichung 0 %. Der Waffentyp ist damit nachweisbar rein
  thematisch.
- **SC-012**: Jeder Rüstungssatz außer dem gemeinsamen Einstiegsmaterial erscheint in höchstens einer
  der drei Leitern; eine Konfiguration, die das verletzt, bricht den Start in 100 von 100 Fällen ab.
- **SC-013**: Jede Stufe jeder Leiter ist von ihrer Vorstufe **sichtbar** unterscheidbar — an
  Material, Färbung oder Trim. Über alle sechs Leitern hinweg gibt es keine zwei aufeinanderfolgenden
  Stufen mit identischem Erscheinungsbild.
- **SC-014**: Die Endstufe jeder Leiter erreicht ihren Zielwert unabhängig von der Stufenanzahl:
  geprüft für eine Leiter mit fünf, sechs und sieben Stufen, Abweichung jeweils unter 3 %.

## Ausgearbeiteter Inhalt

Die folgenden Zahlen sind Ausgangspunkt für das Balancing und jederzeit über Konfiguration änderbar
(Prinzip V). Sie sind so gewählt, dass jedes Attribut auf Level 1 Stufe 1 und auf Level 60 Endstufe
die Wertebereiche aus ADR-008 trifft und die Leiter etwa 70 % des Zuwachses trägt.

### Rollenprofile und Zielwerte bei Level 60 auf der Endstufe

| Attribut | Warrior | Rogue | Mage | Cap nach ADR-008 |
|---|---|---|---|---|
| Health | **1997** | 1510 | 1259 | 2000 |
| Defense | **298** | 185 | 159 | 300 (75 % Reduktion) |
| Mana | 200 | 320 | **500** | 500 |
| Physical Damage | **148** | 122 | 45 | 150 |
| Magic Damage | 25 | 45 | **148** | 150 |
| Attack Speed | +15 % | **+50 %** | +6 % | ±50 % |
| Movement Speed | +5 % | **+30 %** | +15 % | ±30 % |
| Ability Cooldown | 20 % | 30 % | **40 %** | 40 % (hart) |

Jede Klasse erreicht in ihrem Rollenprofil den Cap und bleibt in den anderen darunter. Kein Attribut
wird von zwei Klassen ausgereizt.

**Einheiten — die Prozentangaben oben sind Rollenziele, keine Konfigurationswerte.** Jeder Wert der
Leitern wird auf den **Basiswert** seines Attributs addiert und steht deshalb in dessen eigener
Einheit. Drei Attribute haben einen Basiswert ungleich Null, und dort liegt die Falle:

| Attribut | Basiswert in `stats.yml` | ein Leiterwert von `0.30` bedeutet |
|---|---|---|
| Health, Defense, Mana, Physical/Magic Damage | 0 | +0,30 — so wie es aussieht |
| Attack Speed | **4.0** | +0,30 auf 4,0, also **+7,5 %** — nicht +30 % |
| Movement Speed | **0.1** | +0,30 auf 0,1, also **+300 %** — fast sicher ein Fehler |
| Ability Cooldown | 0.0 | 30 % Cooldown-Reduktion, hart gedeckelt bei 0,40 |

Die Rollenziele übersetzen sich damit so:

- **Attack Speed** (Basis 4,0): +15 % → `0.60`, +50 % → `2.00`, +6 % → `0.24`
- **Movement Speed** (Basis 0,1): +5 % → `0.005`, +30 % → `0.030`, +15 % → `0.015`

Das Modifikatorband aus ADR-008 begrenzt diese Werte **nicht**: es begrenzt Modifikatoren um den
effektiven Basiswert, und die Klasse verschiebt den Basiswert selbst. `AttributeDefinition` sagt das
ausdrücklich — das Band nimmt den Basiswert als Parameter, damit es mit ihm mitwandert.

### Basiswerte und Levelwachstum

Basiswert = Wert des nackten Charakters auf Level 1. Zuwachs = Betrag je Level, angewandt auf die
Level 2 bis 60.

| Attribut | Warrior Basis | Warrior /Lvl | Rogue Basis | Rogue /Lvl | Mage Basis | Mage /Lvl |
|---|---|---|---|---|---|---|
| Health | 40 | 9.7 | 35 | 7.2 | 30 | 6.0 |
| Defense | 4 | 1.5 | 2 | 0.9 | 2 | 0.8 |
| Mana | 12 | 0.9 | 16 | 1.4 | 20 | 2.3 |
| Physical Damage | 2 | 0.7 | 2 | 0.6 | 1 | 0.2 |
| Magic Damage | 0.5 | 0.1 | 1 | 0.2 | 2 | 0.7 |
| Attack Speed | 0 | 0 | 0 | 0 | 0 | 0 |
| Movement Speed | 0 | 0 | 0 | 0 | 0 | 0 |
| Ability Cooldown | 0 | 0 | 0 | 0 | 0 | 0 |

Die drei prozentualen Attribute wachsen **nicht** mit dem Level — sie kommen vollständig aus der
Leiter. Das entspricht der Vorgabe aus B06, die sie auf Null gesetzt hat, und hält sie innerhalb ihrer
Bänder, ohne dass 59 Level einzeln geprüft werden müssen.

### Rüstungsleitern

**Jede Leiter hat ihre eigene Länge.** Sie ist keine feste Zahl im Code, sondern ergibt sich aus der
Konfiguration:

| Klasse | Rüstungsstufen | Waffenstufen |
|---|---|---|
| Warrior | 5 | 6 |
| Rogue | 6 | 6 |
| Mage | 7 | 7 |

Alle drei Klassen erreichen denselben Endwert; nur die Schrittweite unterscheidet sich. Der Mage
steigt häufiger in kleineren Sprüngen auf, der Warrior seltener in größeren.

Jede Klasse hat ihre **eigene Rüstungsfamilie**, und sie überschneidet sich mit keiner anderen —
außer im Einstieg, den alle drei mit Leder teilen:

| Klasse | Familie | Wie die Stufe sichtbar wird |
|---|---|---|
| Warrior | Schwere Rüstung: Kupfer, Eisen, Diamant, Netherite | Material |
| Rogue | Gold und Kettenhemd | Material, ab Stufe 4 Trim |
| Mage | Durchgehend Leder | **Färbung** |

| Stufe | Warrior | Rogue | Mage |
|---|---|---|---|
| 1 | Leder · Lvl 1 | Leder · Lvl 1 | Leder Schiefergrau `#4a4a52` · Lvl 1 |
| 2 | Kupfer · Lvl 15 | Gold · Lvl 13 | Leder Tiefblau `#1f3a93` · Lvl 11 |
| 3 | Eisen · Lvl 29 | Kettenhemd · Lvl 24 | Leder Violett `#6b3fa0` · Lvl 20 |
| 4 | Diamant · Lvl 42 | Kettenhemd + Trim `RIB`/Kupfer · Lvl 34 | Leder Magenta `#b5299b` · Lvl 29 |
| 5 | Netherite · Lvl 55 | Kettenhemd + Trim `SILENCE`/Amethyst · Lvl 45 | Leder Bernstein `#e8952f` · Lvl 38 |
| 6 | — | Kettenhemd + Trim `VEX`/Netherite · Lvl 55 | Leder Türkis `#21d4c4` · Lvl 46 |
| 7 | — | — | Leder Weißglühend `#f5f2e8` · Lvl 55 |

**Farbe und Trim sind für zwei der drei Klassen Pflicht, nicht Kosmetik.** Der Mage bleibt
durchgehend auf Leder — ohne Farbe je Stufe wäre seine Progression unsichtbar und nur noch eine Zahl.
Der Rogue hat mit Gold und Kettenhemd nur zwei Materialien über dem Leder, und **beide sind in Vanilla
nicht färbbar**; ab Stufe 4 ist der Trim deshalb der einzige verfügbare Marker. Nur beim Warrior
trägt das Material allein die volle Leiter. Das Kosmetikfeld im Stufen-Schema ist damit kein
reservierter Platzhalter mehr, sondern für Mage und Rogue verbindlich.

Werte je Stufe — die Rüstung trägt Leben, Verteidigung, Mana und Laufgeschwindigkeit:

**Warrior** (5 Stufen)

| Stufe | Health | Defense | Mana | Movement Speed (abs.) |
|---|---|---|---|---|
| 1 | 60 | 6 | 18 | 0 |
| 2 | 280 | 40 | 35 | 0.001 |
| 3 | 600 | 90 | 65 | 0.002 |
| 4 | 975 | 150 | 95 | 0.004 |
| 5 | 1385 | 205 | 130 | 0.005 |

**Rogue** (6 Stufen)

| Stufe | Health | Defense | Mana | Movement Speed (abs.) |
|---|---|---|---|---|
| 1 | 50 | 3 | 24 | 0 |
| 2 | 170 | 19 | 50 | 0.006 |
| 3 | 350 | 40 | 85 | 0.012 |
| 4 | 575 | 70 | 120 | 0.018 |
| 5 | 800 | 100 | 170 | 0.024 |
| 6 | 1050 | 130 | 220 | 0.030 |

**Mage** (7 Stufen)

| Stufe | Health | Defense | Mana | Movement Speed (abs.) |
|---|---|---|---|---|
| 1 | 40 | 3 | 30 | 0 |
| 2 | 120 | 13 | 60 | 0.002 |
| 3 | 240 | 30 | 100 | 0.005 |
| 4 | 380 | 45 | 160 | 0.007 |
| 5 | 525 | 65 | 210 | 0.010 |
| 6 | 700 | 85 | 270 | 0.012 |
| 7 | 875 | 110 | 340 | 0.015 |

### Waffenleitern

| Klasse | Waffentyp | Materialstufen |
|---|---|---|
| Warrior | Schwert | Holz → Stein → Kupfer → Eisen → Diamant → Netherite |
| Rogue | Schwert | Holz → Stein → **Gold** → Eisen → Diamant → Netherite |
| Mage | **Speer** | Holz → Stein → Kupfer → **Gold** → Eisen → Diamant → Netherite |

Levelanforderungen: Warrior und Rogue 1, 13, 24, 34, 45, 55 — Mage 1, 11, 20, 29, 38, 46, 55.

**Warrior und Rogue tragen beide ein Schwert — das ist entschieden, kein Versehen** *(2026-08-21)*.
Sie unterscheiden sich nur auf Stufe 3, wo der Rogue Gold statt Kupfer führt; auf den übrigen fünf
Stufen sind die Waffen gleich. Eine Trennung über den Waffentyp — etwa eine Axt für den Rogue — wurde
angeboten und **abgelehnt**. Wer später eine Klassenunterscheidung an der Waffe braucht, ergänzt sie
über Trims oder Anzeigenamen, nicht über den Typ.

Damit trägt die **Rüstung** die sichtbare Klassenidentität, die Waffe nur die Stufe. Das ist
verträglich mit FR-016, denn die Regel fordert Unterscheidbarkeit *innerhalb* einer Leiter von Stufe
zu Stufe — nicht *zwischen* Klassen. Der Speer trennt den Mage ohnehin schon durch die Form.

Mace und Trident scheiden für alle drei aus, weil sie in Paper 26.2 nur als einzelnes Material ohne
Varianten existieren und deshalb keine Leiter tragen können. Kettenhemd scheidet als Waffenmaterial
aus, weil es nur als Rüstung existiert.

**Der Waffentyp DARF die Werte nicht beeinflussen.** Vanilla-Waffen tragen eigene Modifikatoren auf
die Angriffsgeschwindigkeit, und die Brücke zu den Vanilla-Attributen setzt nur den *Basiswert*. Ohne
Neutralisierung wäre der Waffentyp eine unmodellierte neunte Wertquelle. Siehe FR-046 bis FR-048.

Werte je Stufe — die Waffe trägt physischen Schaden, magischen Schaden, Angriffsgeschwindigkeit und
Fähigkeiten-Cooldown:

**Warrior** (6 Stufen)

| Stufe | Physical Damage | Magic Damage | Attack Speed (abs.) | Ability Cooldown |
|---|---|---|---|---|
| 1 | 3 | 0.5 | 0 % | 0 % |
| 2 | 16 | 3 | 0.12 | 4 % |
| 3 | 35 | 6 | 0.24 | 8 % |
| 4 | 60 | 10 | 0.36 | 12 % |
| 5 | 85 | 14 | 0.48 | 16 % |
| 6 | 105 | 19 | 0.60 | 20 % |

**Rogue** (6 Stufen)

| Stufe | Physical Damage | Magic Damage | Attack Speed (abs.) | Ability Cooldown |
|---|---|---|---|---|
| 1 | 3 | 1 | 0 % | 0 % |
| 2 | 13 | 5 | 0.40 | 6 % |
| 3 | 30 | 10 | 0.80 | 12 % |
| 4 | 45 | 17 | 1.20 | 18 % |
| 5 | 65 | 25 | 1.60 | 24 % |
| 6 | 85 | 32 | 2.00 | 30 % |

**Mage** (7 Stufen)

| Stufe | Physical Damage | Magic Damage | Attack Speed (abs.) | Ability Cooldown |
|---|---|---|---|---|
| 1 | 2 | 3 | 0 % | 0 % |
| 2 | 5 | 13 | 0.04 | 7 % |
| 3 | 9 | 30 | 0.08 | 13 % |
| 4 | 14 | 45 | 0.12 | 20 % |
| 5 | 20 | 65 | 0.16 | 27 % |
| 6 | 25 | 85 | 0.20 | 33 % |
| 7 | 32 | 105 | 0.24 | 40 % |


### Fähigkeitsbindung

Nur der Warrior ist festgelegt; Mage und Rogue arbeitet B08 aus. B07 modelliert die Bindung so, dass
sie ohne Codeänderung gefüllt werden kann.

| Fähigkeit | Art | Freischaltstufe |
|---|---|---|
| Wut | passiv | 1 |
| Schild | aktiv | 5 |
| Sprung | aktiv | 15 |
| Lifesteal | passiv | 25 |
| Wirbel | aktiv | 35 |
| Call of the Berserker | aktiv, Unique | 45 |

## Assumptions

- **Verifiziert, keine Annahme mehr:** Kupfer-Rüstung, Kettenhemd-Rüstung und der Speer in allen
  Materialstufen existieren in Paper 26.2 — geprüft gegen `org.bukkit.Material` im
  API-Artefakt `26.2.build.112-stable`. Der Speer führt sogar sieben Stufen (Holz, Stein, Kupfer,
  Gold, Eisen, Diamant, Netherite); benötigt werden fünf. Kettenhemd existiert **nur** als Rüstung,
  nicht als Werkzeug — es kann deshalb keine Waffenstufe belegen.
- **Mace und Trident scheiden als Klassenwaffe aus.** Beide existieren nur als einzelnes Material ohne
  Varianten und können damit keine fünfstufige Leiter tragen. Dieselbe Prüfung schließt sie für alle
  drei Klassen aus, nicht nur für den Mage.
- **Die gebundene Waffe liegt auf einem festen Hotbar-Slot.** Welcher es ist, legt B08 mit dem übrigen
  Hotbar-Schema fest — vier aktive Fähigkeiten belegen ebenfalls Slots. B07 fordert nur, dass der
  Slot fest ist und sich nicht tauschen lässt.
- **Gebundene Ausrüstung existiert als echter Gegenstand im Slot**, nicht als reiner Zustand mit
  abgeleiteter Darstellung. ADR-018 setzt Ereignissperren voraus, und ein virtuelles Modell hätte
  jede Vanilla-Interaktion mit der Rüstung neu erfinden müssen.
- **Die Levelanforderungen sind Inhalt, keine Architektur.** Jede Leiter beginnt auf Level 1 und
  endet auf Level 55; die Zwischenstufen sind über ihre eigene Länge verteilt. Damit ist die Endstufe
  erreichbar, bevor die Levelprogression bei 60 endet, und zwar für eine Leiter mit fünf Stufen
  genauso wie für eine mit sieben.
- **Die Angriffsgeschwindigkeit des Mage wurde von +5 % auf +6 % angehoben.** Bei sieben Stufen und
  zwei Dezimalstellen wären zwei aufeinanderfolgende Stufen sonst beide auf 0 gelandet — eine Leiter,
  die nicht streng steigt, und damit ein Startfehler nach FR-017. +6 % ergibt genau sechs
  unterscheidbare Schritte über null.
- **Die Warnung bei vollem Inventar nutzt vorläufig eine einfache Ausgabe**, gekapselt hinter der
  Schnittstelle, die B13 später bedient. Title und Sound sind HUD-Ausgaben, und B13 existiert noch
  nicht.
- **Der Kostenblock je Stufe bleibt in B07 uninterpretiert.** Ohne B11 gibt es keinen Weg, eine Stufe
  regulär zu bezahlen; die Schnittstelle zum Weiterschalten ist deshalb zunächst nur für
  Verwaltung und Tests erreichbar.
- **Mage und Rogue starten mit leerer Fähigkeitsbindung.** B08 füllt sie. Eine teilweise gefüllte
  Bindung wird abgewiesen, weil sie sonst eine vergessene Zeile von einer bewussten Auslassung nicht
  unterscheidbar macht.
- **Der Anzeigename „Berserker" gilt für den Warrior.** Klassen-ID und Anzeigename sind getrennt; das
  ist derselbe Grund, aus dem Items Vorlagen-ID und dargestellten Namen trennen.

## Abhängigkeiten

- **B03** liefert den Charakterrahmen samt Klassenspalte und dem Schlüssel, der drei Charaktere je
  Konto erzwingt. B07 legt keine neue Tabelle für die Klasse an, sondern nur für den Klassenstand.
- **B04** verrechnet die Klassenbeiträge. B07 liefert Werte und Quellenkennung, nichts sonst.
- **B06** liefert Level und Maximallevel und hat die Ersetzbarkeit des Wachstums je Klasse
  vorgesehen.
- **B05** weist Schaden für einen Spieler ohne Charakter ab — das ist die Voraussetzung dafür, dass
  ADR-020 ohne Sonderfall in der Kampf-Pipeline funktioniert.
- **B08** löst die Fähigkeits-IDs auf und legt das Hotbar-Schema fest.
- **B11** fragt die Bindungsauskunft und legt die Aufstiegskosten aus.
- **B13** zeichnet die Warnung bei vollem Inventar und die Klassenanzeige.
