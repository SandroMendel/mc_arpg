# Feature Specification: B02 · Persistenz-Layer

**Feature Branch**: `002-persistence-layer`

**Created**: 2026-08-19

**Status**: Draft

**Input**: Blocksteckbrief `blocks/B02-persistence.md` — sämtliche dauerhafte Datenhaltung in
PostgreSQL, asynchron, gebatcht und ohne Datenverlust. Baut auf B01 (Core & Plattform) auf und
wird von B03, B06, B11 und B12 benötigt.

## Clarifications

### Session 2026-08-19

- Q: Was passiert, wenn der Speicherpuffer bei einem Datenbankausfall seine Obergrenze erreicht? → A: Alle Spieler werden kontrolliert mit klarer Meldung getrennt, neue Sitzungen werden abgelehnt, bis der Puffer geschrieben ist — kein stilles Verwerfen.
- Q: Wie lange darf der abschließende Flush beim Herunterfahren maximal dauern? → A: 8 Sekunden, innerhalb des von B01 je Modul gewährten 10-Sekunden-Budgets; die Reserve dient dem Protokollieren. B01 bleibt unverändert.
- Q: Soll B02 einen Weg vorsehen, die Daten eines einzelnen Spielers zu löschen oder zu anonymisieren? → A: Ja, Anonymisierung — Spielerzustand wird gelöscht, Statistik- und Prüfprotokolleinträge verlieren den Personenbezug durch ein anonymes Ersatzkennzeichen; Aggregate und Allzeit-Bestenlisten bleiben erhalten.
- Q: In welcher Auflösung sollen Statistikdaten gespeichert werden? → A: Verdichtet je Spieler, Kennzahl und Kalendertag; Einzelereignisse werden im Speicher aufaddiert und nicht dauerhaft gespeichert. Trägt Allzeit- und Zeitraum-Auswertungen bei rund 73.000 Zeilen pro Jahr.
- Q: Wie soll verhindert werden, dass eine alte und eine neue Sitzung desselben Spielers sich gegenseitig überschreiben? → A: Der Login wartet auf den abgeschlossenen Flush der Vorsitzung (mit Obergrenze), zusätzlich Versionsprüfung beim Schreiben als Sicherheitsnetz. Kein Überschreiben, kein Verlust.
- Q: Was soll passieren, wenn sich ein Spieler anmeldet, während die Datenbank nicht erreichbar ist? → A: Anmeldung mit klarer Meldung ablehnen; niemals ein leerer Standardzustand. Bereits verbundene Spieler spielen unbeeinträchtigt weiter, da ihr Zustand im Speicher autoritativ ist.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Spielerfortschritt überlebt jeden Serverneustart (Priority: P1)

Als Spieler verlasse ich den Server und komme später zurück. Mein Level, meine Ausrüstung,
meine Statistiken und mein sonstiger Fortschritt sind exakt so, wie ich sie verlassen habe —
unabhängig davon, ob der Server sauber heruntergefahren oder abgestürzt ist.

**Why this priority**: Ohne verlässliche Persistenz ist das gesamte Spiel wertlos — ein RPG,
das Fortschritt verliert, verliert seine Spieler sofort und dauerhaft. Jeder weitere Block, der
Zustand hält (B03 Sitzungen, B06 Progression, B11 Items, B12 Statistiken), setzt das voraus.

**Independent Test**: Einen Spieler mit verändertem Zustand verbinden, den Server neu starten
und den Zustand nach dem erneuten Verbinden vergleichen — einmal nach sauberem Shutdown, einmal
nach hartem Prozessabbruch.

**Acceptance Scenarios**:

1. **Given** ein Spieler hat Fortschritt erzielt, **When** er den Server verlässt,
   **Then** ist sein vollständiger Zustand dauerhaft gespeichert, bevor die Sitzung endet.
2. **Given** ein Spieler ist verbunden und hat seit dem letzten Autosave Fortschritt erzielt,
   **When** der Serverprozess hart abbricht, **Then** geht höchstens der Fortschritt seit dem
   letzten Autosave-Intervall verloren, niemals mehr.
3. **Given** der Server wird regulär heruntergefahren, **When** noch ungeschriebene Änderungen
   existieren, **Then** werden alle Änderungen vollständig geschrieben, bevor der Prozess endet.
4. **Given** ein Spieler verbindet sich, **When** seine gespeicherten Daten geladen werden,
   **Then** erhält er exakt den zuletzt gespeicherten Zustand und niemals den eines anderen
   Spielers oder einen teilweise geladenen Zustand.

---

### User Story 2 - Datenhaltung belastet den Spielablauf nicht (Priority: P1)

Als Spieler kämpfe ich mit 150 anderen Spielern gleichzeitig. Der Server bleibt flüssig, egal
wie viele Kills, Schadensereignisse und Statistikänderungen dabei anfallen — die Datenhaltung
ist für mich unsichtbar.

**Why this priority**: Gleichrangig mit User Story 1. Eine Persistenz, die den Tick blockiert,
verletzt Constitution I und II und macht den in ADR-002 festgelegten Zielbetrieb (100–200
Spieler auf einer Instanz) unmöglich. Korrektheit ohne Performance ist hier kein tragfähiges
Zwischenergebnis, deshalb ebenfalls P1.

**Independent Test**: Lasttest mit 200 simulierten Sitzungen unter Kampflast; Tick-Zeiten und
Wartezeiten auf Datenbankverbindungen messen.

**Acceptance Scenarios**:

1. **Given** ein Spielereignis verändert Spielerzustand, **When** das Ereignis eintritt,
   **Then** erfolgt dabei kein Datenbankzugriff — die Änderung wird lediglich vorgemerkt.
2. **Given** viele Spieler erzeugen gleichzeitig Änderungen, **When** das Autosave-Intervall
   erreicht wird, **Then** werden die Änderungen gesammelt geschrieben, ohne den Server-Tick
   zu blockieren.
3. **Given** 200 Spieler sind gleichzeitig aktiv, **When** sich ein weiterer Spieler verbindet,
   **Then** wartet der Anmeldevorgang nicht auf eine freie Datenbankverbindung.
4. **Given** ein Datenbankschreibvorgang dauert ungewöhnlich lange, **When** dies geschieht,
   **Then** bleibt der Server-Tick unbeeinträchtigt und kein Spieler bemerkt eine Verzögerung.

---

### User Story 3 - Ein Datenbankausfall kostet keinen Fortschritt (Priority: P2)

Als Server-Betreiber erlebe ich einen vorübergehenden Ausfall der Datenbank. Der Server läuft
weiter, die Spieler spielen weiter, und sobald die Datenbank zurück ist, werden alle in der
Zwischenzeit angefallenen Änderungen nachgeschrieben — ohne dass ich eingreifen muss.

**Why this priority**: Wichtig für den realen Betrieb, aber der Server ist auch ohne
Wiederanlauflogik grundsätzlich spielbar. Ein Ausfall ist zudem seltener als der Normalbetrieb
aus US1/US2, weshalb dies nach den beiden P1-Stories kommt.

**Independent Test**: Datenbank im laufenden Betrieb für 60 Sekunden abschalten, wieder
einschalten und prüfen, dass keine Änderung verloren ging und die Tick-Zeiten stabil blieben.

**Acceptance Scenarios**:

1. **Given** die Datenbank ist nicht erreichbar, **When** ein Schreibvorgang fällig wird,
   **Then** bleiben die Änderungen im Speicher gepuffert und der Versuch wird wiederholt.
2. **Given** die Datenbank ist während eines Ausfalls nicht erreichbar, **When** Spieler
   weiterspielen, **Then** bleibt der Server-Tick unbeeinträchtigt und es erscheint eine klare
   Meldung im Log.
3. **Given** die Datenbank ist nach einem Ausfall wieder erreichbar, **When** der nächste
   Schreibversuch läuft, **Then** werden alle zwischenzeitlich angefallenen Änderungen
   vollständig nachgeschrieben.
4. **Given** die Datenbank ist beim Serverstart nicht erreichbar, **When** der Server startet,
   **Then** bricht der Start mit einer klaren Meldung ab, statt Spieler ohne Datenhaltung
   zuzulassen.
5. **Given** ein Ausfall dauert so lange, dass der Speicherpuffer seine Obergrenze erreicht,
   **When** die Grenze erreicht wird, **Then** werden alle Spieler mit einer den Grund
   benennenden Meldung getrennt und neue Sitzungen abgelehnt — gepufferte Änderungen werden
   nicht verworfen.
6. **Given** die Datenbank ist während des Betriebs nicht erreichbar, **When** sich ein neuer
   Spieler anmeldet, **Then** wird die Anmeldung mit klarer Meldung abgelehnt und er erhält
   keinen leeren Standardzustand, während bereits verbundene Spieler weiterspielen.
7. **Given** der Puffer nähert sich seiner Obergrenze, **When** dies geschieht, **Then**
   erscheint rechtzeitig vorher eine Warnung im Log, sodass der Betreiber noch eingreifen kann.

---

### User Story 4 - Schemaänderungen sind nachvollziehbar und wiederholbar (Priority: P3)

Als Betreiber spiele ich eine neue Plugin-Version ein, die zusätzliche Daten speichert. Das
Datenbankschema wird beim Start automatisch und nachvollziehbar auf den passenden Stand
gebracht — auf einer leeren wie auf einer bereits befüllten Datenbank.

**Why this priority**: Erst ab der zweiten Auslieferung relevant; für den ersten Start genügt
ein einmalig erzeugtes Schema. Trotzdem von Anfang an nötig, weil nachträglich eingeführte
Migrationen bestehende Spielerdaten gefährden würden.

**Independent Test**: Migrationen gegen eine leere und gegen eine befüllte Datenbank laufen
lassen und in beiden Fällen den erwarteten Schemastand sowie unveränderte Bestandsdaten prüfen.

**Acceptance Scenarios**:

1. **Given** eine leere Datenbank, **When** der Server startet, **Then** wird das vollständige
   Schema angelegt und der Start läuft durch.
2. **Given** eine Datenbank auf einem älteren Schemastand mit Spielerdaten, **When** der Server
   startet, **Then** werden nur die fehlenden Migrationsschritte ausgeführt und bestehende
   Daten bleiben unverändert erhalten.
3. **Given** eine Datenbank auf aktuellem Schemastand, **When** der Server erneut startet,
   **Then** wird keine Migration erneut ausgeführt.
4. **Given** eine Migration schlägt fehl, **When** dies beim Start passiert, **Then** bricht
   der Start mit einer klaren Meldung ab, statt gegen ein unvollständiges Schema zu arbeiten.

---

### User Story 5 - Daten eines Spielers lassen sich auf Anfrage vom Personenbezug lösen (Priority: P3)

Als Betreiber erhalte ich die Anfrage eines Spielers, seine Daten zu löschen. Ich löse den Vorgang
aus, danach ist aus keinem gespeicherten Datensatz mehr auf ihn zu schließen — die Bestenlisten
und ihre Allzeit-Summen bleiben davon unberührt.

**Why this priority**: Rechtlich und betrieblich wichtig, aber der Server ist ohne diese Funktion
uneingeschränkt spielbar, und Anfragen sind selten. Entscheidend ist, dass der Weg **jetzt** im
Datenmodell angelegt wird: Nachträglich müsste er durch jede Tabelle gezogen werden, die B06, B11
und B12 bis dahin ergänzt haben.

**Independent Test**: Einen Spieler mit Zustand, Statistiken und Item-Instanzen anlegen,
anonymisieren, danach alle Tabellen auf die ursprüngliche Kennung durchsuchen und die
Allzeit-Summen vergleichen (siehe `quickstart.md`, Abschnitt 6).

**Acceptance Scenarios**:

1. **Given** ein Spieler mit Zustand, Statistiken und Item-Instanzen, **When** die Anonymisierung
   ausgelöst wird, **Then** enthält keine Tabelle mehr seine ursprüngliche Kennung.
2. **Given** derselbe Vorgang, **When** er abgeschlossen ist, **Then** sind die Allzeit-Summen der
   Statistik unverändert.
3. **Given** ein Fehler tritt mitten im Vorgang auf, **When** dies geschieht, **Then** bleibt kein
   teilweise anonymisierter Zustand zurück.
4. **Given** ein administrativer Eingriff findet statt, **When** er ausgeführt wird, **Then** ist
   er im Prüfprotokoll nachvollziehbar festgehalten — die Anonymisierung selbst eingeschlossen,
   jedoch ohne die anonymisierte Kennung.

### Edge Cases

- Was passiert, wenn ein Spieler den Server verlässt und sich sofort wieder verbindet, bevor
  seine Daten fertig geschrieben sind? → Der Ladevorgang der neuen Sitzung wartet, bis der
  Flush der Vorsitzung abgeschlossen ist (FR-019a); überschreitet das Warten die Obergrenze,
  wird die Anmeldung mit klarer Meldung abgelehnt (FR-019c).
- Was passiert, wenn derselbe Spieler-Account zweimal gleichzeitig verbunden ist (etwa nach
  einem hängenden Verbindungsabbau)? → Nur ein Zustand ist autoritativ; die Versionsprüfung
  beim Schreiben (FR-019b) lehnt den Schreibvorgang der veralteten Sitzung ab und protokolliert
  ihn.
- Was passiert, wenn ein Aggregat während des laufenden Batch-Schreibvorgangs erneut verändert
  wird? → Die neue Änderung darf nicht verloren gehen, auch wenn sie nach dem Einsammeln der
  Schreibmenge erfolgt.
- Was passiert, wenn beim Shutdown-Flush die Datenbank nicht erreichbar ist? → Es muss eine
  klar begrenzte Frist geben, nach der der Shutdown fortgesetzt wird; der Datenverlust ist zu
  protokollieren, statt den Prozess unbegrenzt zu blockieren.
- Was passiert, wenn gespeicherte Daten nicht mehr zum erwarteten Aufbau passen (etwa nach
  einem manuellen Eingriff in der Datenbank)? → Der betroffene Spieler darf keinen defekten
  Zustand erhalten; der Fall ist zu protokollieren und lokal zu begrenzen.
- Was passiert, wenn der Speicherpuffer durch einen langen Datenbankausfall unbegrenzt wächst?
  → Beim Erreichen der konfigurierten Obergrenze werden alle Spieler kontrolliert getrennt und
  neue Sitzungen abgelehnt; gepufferte Änderungen bleiben erhalten (Klärung 2026-08-19,
  FR-009a bis FR-009c). Zuvor warnt das System, damit ein Eingriff noch möglich ist.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUSS sämtliche Lese- und Schreibzugriffe auf die Datenhaltung außerhalb
  des Server-Ticks ausführen. Einzige Ausnahme ist der abschließende Flush beim Shutdown
  (FR-011).
- **FR-002**: System DARF bei einem einzelnen Spielereignis (z. B. Kill, Erfahrungsgewinn,
  Schaden) KEINEN Datenbankzugriff auslösen; die Änderung wird ausschließlich als
  änderungsbedürftig vorgemerkt.
- **FR-003**: System MUSS vorgemerkte Änderungen in einem konfigurierbaren Intervall zwischen
  30 und 60 Sekunden gesammelt schreiben. Der Standardwert ist 45 Sekunden.
- **FR-004**: System MUSS zusätzlich zum Intervall alle Änderungen einer Sitzung schreiben,
  sobald der zugehörige Spieler den Server verlässt.
- **FR-005**: System MUSS beim Verbinden eines Spielers dessen gespeicherten Zustand
  vollständig laden, bevor die Sitzung als nutzbar gilt.
- **FR-005a**: System MUSS eine Anmeldung mit einer klaren, den Grund benennenden Meldung
  ablehnen, wenn der gespeicherte Zustand nicht geladen werden kann. Ein Spieler DARF unter
  keinen Umständen mit einem leeren oder erfundenen Standardzustand in eine Sitzung gelangen
  (Klärung 2026-08-19).
- **FR-005b**: Eine Ablehnung nach FR-005a DARF bereits verbundene Spieler nicht beeinträchtigen
  — deren Zustand liegt im Speicher und bleibt autoritativ (Constitution IV).
- **FR-006**: System MUSS sicherstellen, dass bei einem Prozessabbruch höchstens die seit dem
  letzten abgeschlossenen Schreibvorgang angefallenen Änderungen verloren gehen.
- **FR-007**: System MUSS Schreibvorgänge desselben Datensatzes so ausführen, dass ein
  bestehender Datensatz aktualisiert und ein fehlender angelegt wird, ohne dass ein
  vorheriger Leseschritt nötig ist.
- **FR-008**: System MUSS einen Verbindungspool bereitstellen, der so dimensioniert ist, dass
  der Anmeldepfad bei 200 gleichzeitigen Sitzungen nicht auf eine freie Verbindung wartet.
- **FR-009**: System MUSS bei nicht erreichbarer Datenhaltung die vorgemerkten Änderungen im
  Speicher halten und den Schreibvorgang wiederholen, ohne den Server-Tick zu blockieren und
  ohne Änderungen still zu verwerfen.
- **FR-009a**: System MUSS für den Speicherpuffer aus FR-009 eine konfigurierbare Obergrenze
  durchsetzen, statt unbegrenzt Speicher zu belegen.
- **FR-009b**: System MUSS beim Erreichen der Puffergrenze alle verbundenen Spieler mit einer
  klaren, den Grund benennenden Meldung vom Server trennen und keine neuen Sitzungen zulassen,
  bis die Datenhaltung wieder erreichbar ist und der Puffer geschrieben wurde. Ein stilles
  Verwerfen gepufferter Änderungen ist unzulässig (Klärung 2026-08-19).
- **FR-009c**: System MUSS beim Annähern an die Puffergrenze eine Warnung protokollieren,
  bevor die Trennung nach FR-009b greift, damit ein Betreiber noch eingreifen kann.
- **FR-010**: System MUSS beim Wiederherstellen der Verbindung alle zwischenzeitlich
  angefallenen Änderungen vollständig nachschreiben.
- **FR-011**: System MUSS beim Herunterfahren alle ausstehenden Änderungen vollständig
  schreiben. Dieser Vorgang darf blockieren, läuft außerhalb des Ticks und MUSS nach
  **8 Sekunden** abbrechen, wobei die Zahl der dabei nicht geschriebenen Änderungen und die
  betroffenen Spieler zu protokollieren sind (Klärung 2026-08-19).
- **FR-011a**: Die Flush-Frist aus FR-011 MUSS innerhalb des Shutdown-Zeitlimits bleiben, das
  B01 jedem Modul einräumt (10 Sekunden je Modul, B01/FR-012). Die verbleibende Reserve dient
  ausschließlich dem Protokollieren des Ergebnisses, bevor B01 das Modul zwangsterminiert.
- **FR-012**: System MUSS Schemaänderungen ausschließlich über versionierte, in fester
  Reihenfolge anwendbare Migrationsschritte vornehmen.
- **FR-013**: System MUSS beim Start den Schemastand prüfen, fehlende Migrationsschritte
  anwenden und bereits angewendete Schritte nicht erneut ausführen.
- **FR-014**: System MUSS den Start abbrechen, wenn die Datenhaltung nicht erreichbar ist oder
  eine Migration fehlschlägt — mit einer Meldung, die die Ursache benennt.
- **FR-015**: System MUSS je fachlichem Aggregat (mindestens Spielerzustand, Statistikdaten,
  Item-Instanz) eine eigene Zugriffsschnittstelle bereitstellen, über die andere Blöcke
  ausschließlich zugreifen.
- **FR-016**: System MUSS Statistik-/Zeitreihendaten getrennt vom Spielerzustand modellieren,
  da sie sich in Schreibhäufigkeit und Aufbewahrung unterscheiden.
- **FR-016a**: System MUSS Statistikdaten je Spieler, Kennzahl und Kalendertag verdichtet
  ablegen. Einzelereignisse werden im Speicher auf den Tageswert aufaddiert und nicht einzeln
  dauerhaft gespeichert (Klärung 2026-08-19).
- **FR-016b**: System MUSS aus den Tageswerten sowohl Allzeit-Summen als auch Summen über
  beliebige Zeiträume ermitteln können, ohne Einzelereignisse vorzuhalten.
- **FR-016c**: System MUSS beim Tageswechsel den laufenden Tageswert abschließen und einen
  neuen beginnen, ohne dass dabei Zählwerte verloren gehen oder doppelt erfasst werden.
- **FR-017**: System MUSS Statistik-Rohdaten dauerhaft und unbegrenzt aufbewahren.
- **FR-017a**: System MUSS eine Operation bereitstellen, die auf Anfrage sämtliche Daten eines
  einzelnen Spielers vom Personenbezug löst: Der Spielerzustand wird gelöscht, in
  Statistikeinträgen und Prüfprotokoll wird die Spielerkennung durch ein anonymes
  Ersatzkennzeichen ersetzt. Aggregierte Werte und Allzeit-Bestenlisten bleiben dadurch
  erhalten (Klärung 2026-08-19).
- **FR-017b**: Nach einer Anonymisierung nach FR-017a DARF aus keinem gespeicherten Datensatz
  mehr auf die ursprüngliche Spielerkennung oder den Spielernamen geschlossen werden können.
- **FR-017c**: Jede Anonymisierung MUSS selbst im Prüfprotokoll (FR-018) festgehalten werden —
  mit Zeitpunkt und auslösender Stelle, aber ohne die anonymisierte Kennung.
- **FR-018**: System MUSS administrative Eingriffe (z. B. Item vergeben, Bann, Änderung von
  Balancing-Werten) in einem eigenen, dauerhaften Prüfprotokoll festhalten.
- **FR-019**: System MUSS ausschließen, dass zwei gleichzeitige Sitzungen desselben Spielers
  den gespeicherten Zustand gegenseitig überschreiben.
- **FR-019a**: System MUSS den Ladevorgang einer neuen Sitzung zurückstellen, bis alle
  ausstehenden Änderungen einer vorherigen Sitzung desselben Spielers geschrieben sind. Erst
  danach wird der Zustand gelesen (Klärung 2026-08-19).
- **FR-019b**: System MUSS jeden Spielerzustand mit einer bei jedem Schreibvorgang erhöhten
  Versionsnummer versehen und einen Schreibvorgang ablehnen, dessen Version nicht mehr dem
  gespeicherten Stand entspricht. Ein abgelehnter Schreibvorgang MUSS protokolliert werden.
- **FR-019c**: Das Zurückstellen nach FR-019a MUSS eine Obergrenze haben; wird sie
  überschritten, MUSS die Anmeldung mit einer klaren Meldung abgelehnt werden, statt den
  Spieler unbegrenzt warten zu lassen.
- **FR-020**: System MUSS einen nicht lesbaren oder nicht zum erwarteten Aufbau passenden
  gespeicherten Zustand lokal begrenzt behandeln und protokollieren, ohne den Server oder
  andere Spieler zu beeinträchtigen.
- **FR-021**: System MUSS gespeicherte Spielerdaten versioniert ablegen, sodass ein späterer
  Migrationspfad für Datensätze möglich bleibt.
- **FR-022**: System MUSS die Zugangsdaten und Verbindungsparameter der Datenhaltung über
  Konfiguration beziehen und beim Start gegen ein Schema validieren.
- **FR-023**: Jeder spielersichtbare Text, den dieser Block erzeugt (Ablehnungen nach FR-005a,
  Trennungsmeldungen nach FR-009b), MUSS über einen Message-Schlüssel aufgelöst werden; der
  Wortlaut liegt in einer Konfigurationsquelle, nicht im Code (Constitution V).
- **FR-023a**: System MUSS beim Start prüfen, dass zu jedem verwendeten Message-Schlüssel ein
  Text hinterlegt ist, und den Start andernfalls mit Nennung des fehlenden Schlüssels abbrechen —
  ein fehlender Text darf niemals erst beim Spieler als leere Meldung auffallen.

### Key Entities *(include if feature involves data)*

- **Anonymes Ersatzkennzeichen**: Ein Platzhalter, der nach einer Anonymisierung (FR-017a) an
  die Stelle der Spielerkennung tritt; trägt keinerlei Rückschluss auf die ursprüngliche Person.
- **Spielerzustand**: Der dauerhafte Zustand eines Spielers (Kennung, Fortschritt, zugehörige
  Werte), eindeutig über die Spielerkennung identifiziert, versioniert und autoritativ im
  Speicher, solange der Spieler verbunden ist.
- **Statistikdatensatz**: Der verdichtete Wert einer Kennzahl für einen Spieler an einem
  Kalendertag (Spielerkennung, Kennzahl, Tag, Wert), getrennt
  vom Spielerzustand gehalten und unbegrenzt aufbewahrt. Eindeutig über die Kombination aus
  Spielerkennung, Kennzahl und Tag.
- **Item-Instanz**: Ein konkretes Exemplar eines Gegenstands im Besitz eines Spielers, das
  Vorlagenkennung und gewürfelte Werte speichert, niemals berechnete Endwerte (ADR-004).
- **Prüfprotokolleintrag**: Eine dauerhaft festgehaltene administrative Handlung mit
  Zeitpunkt, handelnder Person, betroffenem Ziel und Art des Eingriffs.
- **Änderungsvormerkung**: Der Vermerk, dass ein Aggregat seit dem letzten Schreibvorgang
  verändert wurde und beim nächsten Sammelschreiben zu berücksichtigen ist.
- **Migrationsschritt**: Eine versionierte, genau einmal anzuwendende Schemaänderung mit
  fester Position in der Reihenfolge.
- **Zugriffsschnittstelle je Aggregat**: Die fachliche Schnittstelle, über die andere Blöcke
  Daten eines Aggregats laden und ändern, ohne die Ablageform zu kennen.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Nach einem harten Prozessabbruch geht in 100 % der Testfälle höchstens der
  Fortschritt seit dem letzten Autosave-Intervall verloren — nie mehr.
- **SC-002**: Nach einem regulären Shutdown geht in 100 % der Testfälle kein Fortschritt
  verloren.
- **SC-003**: Bei 200 gleichzeitigen Sitzungen unter Last wartet kein Anmeldevorgang auf eine
  freie Datenbankverbindung.
- **SC-004**: Ein Ausfall der Datenhaltung von 60 Sekunden führt zu keinem Datenverlust und zu
  keiner messbaren Verschlechterung der Server-Tickrate.
- **SC-005**: Im Normalbetrieb erzeugt kein einzelnes Spielereignis einen Datenbankzugriff;
  dies ist über eine Messung der Zugriffe pro Zeiteinheit gegen die Zahl der Spielereignisse
  nachweisbar.
- **SC-006**: Migrationen laufen sowohl auf einer leeren als auch auf einer befüllten
  Datenbank in 100 % der Testfälle fehlerfrei durch, ohne Bestandsdaten zu verändern.
- **SC-007**: Ein Spieler, der den Server verlässt und sich sofort wieder verbindet, erhält in
  100 % der Testfälle seinen aktuellen und nicht einen veralteten Zustand.
- **SC-008**: Die Datenhaltung ist gegen eine echte Datenbankinstanz automatisiert getestet,
  nicht gegen Ersatzobjekte.
- **SC-009**: Während eines Datenbankausfalls erhält in 100 % der Testfälle kein neu
  anmeldender Spieler einen leeren Standardzustand; bereits verbundene Spieler verlieren
  nichts.
- **SC-010**: Nach einer Anonymisierung lässt sich in 100 % der Testfälle aus keinem
  verbleibenden Datensatz die ursprüngliche Spielerkennung rekonstruieren, während die
  Allzeit-Summen der Bestenlisten unverändert bleiben.
- **SC-011**: Der abschließende Flush beim Herunterfahren ist in 100 % der Testfälle nach
  spätestens 8 Sekunden beendet — entweder vollständig geschrieben oder mit protokolliertem
  Ergebnis abgebrochen.
- **SC-012**: Kein spielersichtbarer Text dieses Blocks steht im Code; ein fehlender
  Message-Schlüssel verhindert den Start in 100 % der Testfälle.

## Assumptions

- Die Datenhaltung läuft auf derselben Maschine wie der Spielserver (geklärt 2026-08-19,
  `06-open-questions.md`, Abschnitt „Betrieb"), sodass Netzwerklatenz zwischen beiden
  vernachlässigbar ist.
- Das konkrete Zugriffsverfahren auf die Datenbank (direkter Zugriff, Abfragegenerator oder
  leichtgewichtige Abbildung) ist bewusst nicht Teil dieser Spec und wird bei `/plan`
  entschieden.
- Der Standardwert für das Autosave-Intervall wird auf 45 Sekunden gesetzt — die Mitte des in
  den nichtfunktionalen Anforderungen vorgegebenen Bereichs von 30–60 Sekunden.
- B01 ist fertiggestellt und liefert Modul-Registry, Event-Bus, Scheduler-Abstraktion und
  Konfigurations-Loader; B02 wird als Modul gegen diese Schnittstellen entwickelt.
- Solange ein Spieler verbunden ist, ist der Zustand im Speicher autoritativ, nicht der in der
  Datenbank (Constitution IV).
- Die Zahl gleichzeitiger Spieler liegt bei 100–200 auf einer einzelnen Instanz (ADR-002).
- Verschlüsselung der Daten im Ruhezustand und Sicherungskopien der Datenbank sind Aufgabe des
  Betriebs und nicht Teil dieses Blocks.
- Die Bereitstellung einer lauffähigen Datenbankumgebung für automatisierte Tests ist zum
  Zeitpunkt dieser Spec auf der Entwicklungsmaschine noch nicht gegeben und vor Beginn der
  Umsetzung zu klären (siehe `plan.md`, sobald erstellt).
