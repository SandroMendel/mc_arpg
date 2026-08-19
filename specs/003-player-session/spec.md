# Feature Specification: B03 · Spieler-Session & Datenlebenszyklus

**Feature Branch**: `003-player-session`

**Created**: 2026-08-19

**Status**: Draft

**Input**: Blocksteckbrief `blocks/B03-player-session.md` — verwaltet den Übergang zwischen
Datenbank und Speicher: Laden beim Join, Halten während der Sitzung, Schreiben beim Verlassen.
Baut auf B01 und B02 auf, wird von B04, B06, B07, B08, B11 und B12 benötigt.

## Clarifications

### Session 2026-08-19

- Q: Wie lange darf ein Spieler höchstens im gesperrten Zustand warten, bevor die Anmeldung abgebrochen wird? → A: 5 Sekunden — das Zehnfache des 500-ms-Zielwerts, als Notbremse und nicht als erwartete Ladezeit.
- Q: Darf der aktive Charakter im laufenden Betrieb gewechselt werden? → A: Nein, er wird beim Verbinden festgelegt. Ein Wechsel erfordert Ausloggen und erneutes Verbinden; der Lade-/Entladepfad existiert damit genau einmal.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Beim Betreten des Servers ist der Fortschritt sofort da (Priority: P1)

Als Spieler betrete ich den Server. Für einen kurzen Moment kann ich mich nicht bewegen und nehme
keinen Schaden — dann bin ich frei und finde meinen Charakter genau so vor, wie ich ihn verlassen
habe. Ich spiele nie mit falschen Werten weiter.

**Why this priority**: Dies ist der Kernablauf des Blocks. Ohne ihn kann kein Spieler eine gültige
Sitzung erhalten, und alle Blöcke, die auf Spielerzustand aufbauen (B04, B06, B07, B08, B11, B12),
haben keine Grundlage.

**Independent Test**: Einen Spieler mit gespeichertem Fortschritt verbinden lassen und prüfen, dass
er während des Ladens bewegungsgesperrt und schadensimmun ist, danach freigegeben wird und exakt
seinen gespeicherten Zustand vorfindet.

**Acceptance Scenarios**:

1. **Given** ein Spieler mit gespeichertem Fortschritt, **When** er den Server betritt, **Then**
   ist er zunächst bewegungsgesperrt und schadensimmun, bis seine Sitzung vollständig geladen ist.
2. **Given** die Sitzung ist fertig geladen, **When** die Freigabe erfolgt, **Then** kann sich der
   Spieler bewegen, nimmt wieder Schaden und alle seine Werte entsprechen dem gespeicherten Stand.
3. **Given** ein Spieler verbindet sich, **When** der Ladevorgang läuft, **Then** ist er in
   höchstens 500 Millisekunden freigegeben.
4. **Given** ein Spieler ist noch nicht freigegeben, **When** ein anderes System seine Werte
   abfragt, **Then** erhält es die Auskunft „Sitzung noch nicht bereit" und keine Standardwerte.
5. **Given** ein Spieler betritt den Server zum ersten Mal, **When** kein gespeicherter Zustand
   existiert, **Then** wird eine neue Sitzung mit einem klar als „neu" gekennzeichneten Zustand
   angelegt, und das Anlegen eines Charakters bleibt möglich.
6. **Given** der Ladevorgang hängt, **When** 5 Sekunden vergangen sind, **Then** wird die
   Anmeldung mit klarer Meldung abgebrochen, statt den Spieler weiter bewegungsunfähig zu lassen.

---

### User Story 2 - Kein Verlassen kostet Fortschritt, egal wie es geschieht (Priority: P1)

Als Spieler verlasse ich den Server — geplant, durch einen Kick, oder weil meine Verbindung
abbricht. In allen drei Fällen ist mein Fortschritt gespeichert, wenn ich das nächste Mal
zurückkomme.

**Why this priority**: Gleichrangig mit User Story 1. Ein Ladepfad ohne verlässlichen Entladepfad
verliert genau den Fortschritt, den er zuvor korrekt geladen hat. Der Steckbrief nennt diesen Block
ausdrücklich als denjenigen, an dem RPG-Plugins erfahrungsgemäß Datenverlust produzieren.

**Independent Test**: Fortschritt erzeugen und die Sitzung auf allen drei Wegen beenden (Quit,
Kick, Verbindungsabbruch); nach jedem Weg prüfen, dass der Fortschritt gespeichert und das
Sitzungsobjekt entfernt ist.

**Acceptance Scenarios**:

1. **Given** ein Spieler mit ungespeichertem Fortschritt, **When** er den Server regulär verlässt,
   **Then** wird sein Zustand sofort geschrieben, ohne auf das Autosave-Intervall zu warten.
2. **Given** dieselbe Ausgangslage, **When** der Spieler gekickt wird oder seine Verbindung
   abbricht, **Then** wird sein Zustand genauso sofort geschrieben.
3. **Given** eine Sitzung wurde beendet, **When** der Schreibvorgang abgeschlossen ist, **Then**
   ist das Sitzungsobjekt entfernt und belegt keinen Speicher mehr.
4. **Given** der Server läuft über Tage mit vielen Verbindungen, **When** man den Speicherverbrauch
   beobachtet, **Then** wächst die Zahl der Sitzungsobjekte nicht über die Zahl der tatsächlich
   verbundenen Spieler hinaus.
5. **Given** der Server wird heruntergefahren, **When** noch Sitzungen aktiv sind, **Then** wird
   der Zustand aller aktiven Sitzungen geschrieben, bevor der Prozess endet.

---

### User Story 3 - Ein Ladefehler kostet niemals das bestehende Profil (Priority: P1)

Als Spieler versuche ich zu verbinden, während ein Fehler auftritt. Ich werde mit einer klaren
Meldung abgewiesen — und wenn ich es später erneut versuche, ist mein Fortschritt unverändert da.

**Why this priority**: Der schwerwiegendste denkbare Fehler dieses Blocks. Ein Spieler, der mit
leerem Profil hereingelassen wird, überschreibt beim nächsten Speichern seinen echten Fortschritt.
Das fällt erst Stunden später auf und ist dann nicht mehr behebbar. Deshalb P1, obwohl es ein
Fehlerpfad ist.

**Independent Test**: Einen Ladefehler erzwingen und prüfen, dass der Spieler abgewiesen wird und
der gespeicherte Datensatz danach unverändert ist.

**Acceptance Scenarios**:

1. **Given** der gespeicherte Zustand eines Spielers kann nicht gelesen werden, **When** er
   verbindet, **Then** wird er mit einer klaren, den Grund benennenden Meldung abgewiesen.
2. **Given** derselbe Fall, **When** die Abweisung erfolgt ist, **Then** wurde kein Datensatz
   angelegt, verändert oder überschrieben.
3. **Given** ein Spieler verlässt den Server und verbindet sich innerhalb einer Sekunde erneut,
   **When** der Schreibvorgang der alten Sitzung noch läuft, **Then** wartet der neue Ladevorgang
   dessen Abschluss ab und liefert danach den aktuellen Stand.
4. **Given** derselbe schnelle Wiederverbindungsfall, **When** die neue Sitzung entsteht, **Then**
   existiert zu keinem Zeitpunkt mehr als eine Sitzung für denselben Spieler.
5. **Given** das Warten auf den Abschluss der Vorsitzung überschreitet die zulässige Frist,
   **When** dies eintritt, **Then** wird die Anmeldung abgewiesen statt unbegrenzt zu warten.

---

### User Story 4 - Drei Charaktere je Account, einer je Klasse (Priority: P2)

Als Spieler habe ich bis zu drei Charaktere auf meinem Account — je einen für Warrior, Mage und
Rogue. Beim Betreten des Servers spiele ich mit einem davon, und die Fortschritte der anderen
bleiben davon unberührt.

**Why this priority**: Für den Betrieb wichtig und im Datenmodell von Anfang an nötig, weil sich
die Trennung nachträglich nur mit einer Datenmigration einführen ließe. Der Server ist aber auch
mit einem einzigen Charakter je Account grundsätzlich spielbar, daher nach den P1-Stories.

**Independent Test**: Für einen Account zwei Charaktere unterschiedlicher Klassen anlegen, mit
einem Fortschritt erzeugen und prüfen, dass der andere unverändert bleibt.

**Acceptance Scenarios**:

1. **Given** ein Account, **When** Charaktere angelegt werden, **Then** ist höchstens einer je
   Klasse möglich und damit höchstens drei insgesamt.
2. **Given** ein Account mit mehreren Charakteren, **When** mit einem davon gespielt wird, **Then**
   bleibt der Fortschritt der übrigen unverändert.
3. **Given** ein Account, **When** eine Sitzung besteht, **Then** ist genau ein Charakter aktiv.
4. **Given** ein Versuch, einen zweiten Charakter derselben Klasse anzulegen, **When** dies
   geschieht, **Then** wird er mit einer klaren Meldung abgelehnt.

---

### User Story 5 - Spielerdaten sind auch ohne Sitzung lesbar (Priority: P2)

Als Betreiber möchte ich Bestenlisten anzeigen und Verwaltungswerkzeuge nutzen, die Daten von
Spielern lesen, die gerade nicht online sind — ohne dafür eine Sitzung zu erzeugen.

**Why this priority**: B12 (Bestenlisten) und B14 (Verwaltung) hängen davon ab. Ohne diesen Weg
müsste jedes Werkzeug entweder eine Sitzung erzeugen, was Zustand verfälscht, oder an der
Datenhaltung vorbeigreifen, was die Kapselung bricht.

**Independent Test**: Daten eines nicht verbundenen Spielers lesen und prüfen, dass keine Sitzung
entsteht und der gespeicherte Zustand unverändert bleibt.

**Acceptance Scenarios**:

1. **Given** ein Spieler ist nicht verbunden, **When** seine Daten gelesen werden, **Then** wird
   der gespeicherte Zustand geliefert, ohne dass eine Sitzung entsteht.
2. **Given** derselbe Lesevorgang, **When** er abgeschlossen ist, **Then** wurde nichts geschrieben
   und der Zustand ist unverändert.
3. **Given** ein Spieler ist verbunden, **When** derselbe Lesepfad genutzt wird, **Then** liefert
   er den aktuellen Sitzungszustand und nicht einen veralteten gespeicherten Stand.

---

### User Story 6 - Alte Spielerstände funktionieren nach einem Update weiter (Priority: P3)

Als Betreiber spiele ich eine neue Version ein, die Spielerdaten anders ablegt. Bestehende Spieler
verbinden sich und finden ihren Fortschritt vor, ohne dass ich eingreifen muss.

**Why this priority**: Erst ab der zweiten Auslieferung relevant. Der Mechanismus muss aber von
Anfang an vorhanden sein, weil ein nachträglich eingeführter Migrationspfad die bereits
gespeicherten Stände nicht mehr erreicht.

**Independent Test**: Einen Datensatz in einer älteren Fassung ablegen, den Ladepfad ausführen und
prüfen, dass er korrekt überführt wird und der Fortschritt erhalten bleibt.

**Acceptance Scenarios**:

1. **Given** ein Spielerstand in einer älteren Fassung, **When** der Spieler verbindet, **Then**
   wird der Stand in die aktuelle Fassung überführt und der Fortschritt bleibt erhalten.
2. **Given** derselbe Fall, **When** die Überführung abgeschlossen ist, **Then** wird der Stand in
   der aktuellen Fassung gespeichert und beim nächsten Laden nicht erneut überführt.
3. **Given** ein Spielerstand in einer Fassung, die diese Version nicht kennt, **When** der Spieler
   verbindet, **Then** wird er abgewiesen, statt den Stand fehlerhaft zu interpretieren.

### Edge Cases

- Was passiert, wenn ein Spieler die Verbindung trennt, während seine Sitzung noch geladen wird?
  → Der Ladevorgang wird abgebrochen oder sein Ergebnis verworfen; es entsteht keine verwaiste
  Sitzung und kein Schreibvorgang für einen Zustand, den der Spieler nie erhalten hat.
- Was passiert, wenn ein Spieler während des sicheren Zustands stirbt oder in Gefahr gerät?
  → Er ist schadensimmun, solange die Sitzung nicht bereit ist; ein Tod in dieser Phase ist damit
  ausgeschlossen.
- Was passiert, wenn der Ladevorgang ungewöhnlich lange dauert? → Nach einer festgelegten Frist
  wird der Spieler mit klarer Meldung abgewiesen, statt unbegrenzt bewegungsgesperrt zu bleiben.
- Was passiert, wenn derselbe Account gleichzeitig zweimal verbunden ist? → Es existiert immer nur
  eine Sitzung; die zweite Verbindung wird abgewiesen oder die erste zuvor sauber beendet.
- Was passiert, wenn der Server heruntergefahren wird, während eine Sitzung noch lädt? → Der
  Ladevorgang wird abgebrochen; nur vollständig geladene Sitzungen werden geschrieben.
- Was passiert, wenn ein Spieler keinen Charakter besitzt? → Er erhält eine Sitzung ohne aktiven
  Charakter und kann einen anlegen; er spielt nicht mit einem stillschweigend erzeugten Charakter.

## Requirements *(mandatory)*

### Functional Requirements

#### Ladeablauf

- **FR-001**: System MUSS den Zustand eines Spielers beim Verbinden außerhalb des Server-Ticks
  laden.
- **FR-002**: System MUSS einen Spieler ab dem Betreten des Servers bewegungsgesperrt und
  schadensimmun halten, bis seine Sitzung vollständig geladen ist.
- **FR-003**: System MUSS den Spieler freigeben, sobald die Sitzung vollständig geladen ist, und
  dabei Bewegungssperre und Schadensimmunität wieder aufheben.
- **FR-004**: System MUSS auf eine Abfrage von Spielerwerten, deren Sitzung noch nicht bereit ist,
  mit einer erkennbaren „noch nicht bereit"-Auskunft antworten und DARF keine Standardwerte
  liefern.
- **FR-005**: System MUSS alle für eine Sitzung benötigten Daten in möglichst wenigen Abfragen
  laden, statt je Datenart eine eigene Abfrage auszuführen.
- **FR-006**: System MUSS den Ladevorgang nach **5 Sekunden** abbrechen und den Spieler mit klarer
  Meldung abweisen, statt ihn unbegrenzt im gesperrten Zustand zu belassen (Klärung 2026-08-19).
- **FR-006a**: Die Frist aus FR-006 ist bewusst das Zehnfache des Zielwerts aus SC-001. Sie ist
  keine erwartete Ladezeit, sondern eine Notbremse: Wer abgewiesen wird, verbindet sich erneut —
  das ist harmloser, als bewegungsunfähig auf dem Server zu stehen.

#### Entladeablauf

- **FR-007**: System MUSS bei **jedem** Ende einer Sitzung — reguläres Verlassen, Kick,
  Verbindungsabbruch — den Zustand sofort schreiben, ohne auf das Autosave-Intervall zu warten.
- **FR-008**: System MUSS das Sitzungsobjekt entfernen, sobald der abschließende Schreibvorgang
  abgeschlossen ist.
- **FR-009**: System MUSS sicherstellen, dass die Zahl gehaltener Sitzungsobjekte die Zahl der
  tatsächlich verbundenen Spieler nicht dauerhaft übersteigt.
- **FR-010**: System MUSS beim Herunterfahren des Servers den Zustand aller aktiven Sitzungen
  schreiben.

#### Fehlerpfad und Sequenzierung

- **FR-011**: System MUSS einen Spieler abweisen, dessen Zustand nicht geladen werden kann, und
  DARF ihn unter keinen Umständen mit einem leeren oder erfundenen Zustand in eine Sitzung lassen.
- **FR-012**: Eine Abweisung nach FR-011 DARF den gespeicherten Datensatz nicht anlegen, verändern
  oder überschreiben.
- **FR-013**: System MUSS einen Ladevorgang zurückstellen, solange für denselben Spieler noch ein
  Schreibvorgang einer vorherigen Sitzung aussteht, und erst danach lesen.
- **FR-014**: System MUSS ausschließen, dass zu einem Spieler gleichzeitig mehr als eine Sitzung
  existiert.
- **FR-015**: System MUSS einen abgebrochenen Ladevorgang — etwa weil der Spieler die Verbindung
  vorher trennt — so behandeln, dass weder eine verwaiste Sitzung noch ein Schreibvorgang für einen
  nie ausgelieferten Zustand entsteht.

#### Sitzung und Charaktere

- **FR-016**: System MUSS den Sitzungszustand eines verbundenen Spielers als maßgeblich behandeln;
  der gespeicherte Stand ist währenddessen nachrangig.
- **FR-017**: System MUSS je Account höchstens einen Charakter je Klasse zulassen und damit
  höchstens drei insgesamt.
- **FR-018**: System MUSS zu jeder Sitzung genau einen aktiven Charakter führen.
- **FR-019**: System MUSS den Fortschritt eines Charakters unabhängig von den übrigen Charakteren
  desselben Accounts halten.
- **FR-020**: System MUSS den Versuch ablehnen, einen zweiten Charakter derselben Klasse anzulegen.
- **FR-021**: System MUSS einem Spieler ohne Charakter eine Sitzung ohne aktiven Charakter
  bereitstellen und DARF keinen Charakter stillschweigend erzeugen.
- **FR-021a**: Der aktive Charakter wird **beim Verbinden festgelegt** und ändert sich innerhalb
  einer Sitzung nicht. Ein Wechsel erfordert das Beenden der Sitzung und ein erneutes Verbinden
  (Klärung 2026-08-19).
- **FR-021b**: System DARF keine Möglichkeit anbieten, den aktiven Charakter einer bestehenden
  Sitzung zu wechseln. Der Lade- und Entladepfad existiert damit genau einmal, statt ein zweites
  Mal für einen verbundenen Spieler nachgebildet werden zu müssen.

#### Lesen ohne Sitzung

- **FR-022**: System MUSS einen Lesepfad bereitstellen, der Spielerdaten liefert, ohne eine Sitzung
  zu erzeugen.
- **FR-023**: Ein Zugriff über den Lesepfad nach FR-022 DARF nichts schreiben und den gespeicherten
  Zustand nicht verändern.
- **FR-024**: Der Lesepfad nach FR-022 MUSS für einen verbundenen Spieler den aktuellen
  Sitzungszustand liefern und nicht einen veralteten gespeicherten Stand.

#### Datenversionierung

- **FR-025**: System MUSS die Fassung eines gespeicherten Spielerstands erkennen und einen Stand
  einer älteren Fassung beim Laden in die aktuelle überführen.
- **FR-026**: System MUSS einen überführten Stand in der aktuellen Fassung speichern, sodass er
  beim nächsten Laden nicht erneut überführt wird.
- **FR-027**: System MUSS einen Stand in einer unbekannten Fassung abweisen, statt ihn fehlerhaft
  zu interpretieren.

### Key Entities *(include if feature involves data)*

- **Sitzung**: Der im Speicher gehaltene, maßgebliche Zustand eines verbundenen Spielers.
  Existiert von der Freigabe bis zum Ende der Verbindung, führt genau einen aktiven Charakter und
  kennt einen Bereitschaftszustand (lädt, bereit, wird entladen).
- **Charakter**: Ein Spielstand eines Accounts, gebunden an genau eine Klasse. Höchstens einer je
  Klasse und Account. Träger des eigentlichen Spielfortschritts.
- **Account**: Die Identität eines Spielers über alle Charaktere hinweg, entspricht der eindeutigen
  Spielerkennung.
- **Bereitschaftszustand**: Der Punkt im Lebenszyklus, an dem eine Sitzung steht; entscheidet
  darüber, ob ein Spieler freigegeben ist und ob andere Blöcke seine Werte abfragen dürfen.
- **Standfassung**: Die Version, in der ein Spielerstand abgelegt ist; Grundlage für die Überführung
  älterer Stände.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Ein Spieler ist nach dem Betreten des Servers in 95 % der Fälle in unter 500
  Millisekunden freigegeben.
- **SC-002**: Kein Spieler spielt zu irgendeinem Zeitpunkt mit Werten, die nicht aus seinem
  gespeicherten Zustand stammen — in 100 % der Testfälle.
- **SC-003**: Nach einem harten Prozessabbruch geht in 100 % der Testfälle höchstens der
  Fortschritt seit dem letzten Autosave-Intervall verloren.
- **SC-004**: Fortschritt geht bei keinem der drei Sitzungsenden (Verlassen, Kick,
  Verbindungsabbruch) verloren — in 100 % der Testfälle.
- **SC-005**: 200 gleichzeitige Verbindungsversuche führen zu keinem Zeitüberschreitungsfehler und
  zu keiner messbaren Verschlechterung der Server-Tickrate.
- **SC-006**: Ein Verlassen und erneutes Verbinden innerhalb einer Sekunde führt in 100 % der
  Testfälle weder zu Datenverlust noch zu einer zweiten gleichzeitigen Sitzung.
- **SC-007**: Ein Fehler beim Laden führt in 100 % der Testfälle zu einer Abweisung mit klarer
  Meldung und lässt den gespeicherten Datensatz unverändert.
- **SC-008**: Nach 10.000 aufeinanderfolgenden Verbindungen und Trennungen entspricht die Zahl
  gehaltener Sitzungsobjekte der Zahl der verbundenen Spieler.
- **SC-009**: Ein Spielerstand einer älteren Fassung wird in 100 % der Testfälle verlustfrei
  überführt.
- **SC-010**: Ein Lesezugriff ohne Sitzung verändert in 100 % der Testfälle nichts am
  gespeicherten Zustand.

## Assumptions

- B01 ist fertiggestellt und liefert Modul-Registry, Event-Bus, Scheduler-Abstraktion,
  Konfigurations-Loader und die Message-Schlüssel-Ablage; B03 wird als Modul dagegen entwickelt.
- B02 ist fertiggestellt und liefert bereits das Laden und Schreiben von Spielerzustand, das
  Zurückstellen eines Ladevorgangs bis zum Abschluss der Vorsitzung, die Versionsprüfung beim
  Schreiben, das Write-Behind mit einem Autosave-Intervall von 45 Sekunden sowie den
  Shutdown-Flush. **B03 nutzt diese Mechanik und bildet sie nicht erneut nach**; der Beitrag von
  B03 ist der Lebenszyklus darüber — sicherer Zustand, Freigabe, Charakterbezug, Aufräumen.
- Der in B02 vorhandene Datensatz je Spieler beschreibt die **Account**-Ebene. Der eigentliche
  Spielfortschritt hängt am **Charakter**, was mit den drei Klassen-Slots aus B07 zusammenpasst.
  B03 führt die Charakter-Ebene ein; B02 hatte diese Trennung nicht vorweggenommen und muss dafür
  nicht geändert werden, da es die fachlichen Spalten ausdrücklich den besitzenden Blöcken
  überlässt.
- Die Auswahloberfläche für Charaktere und der Ablauf der erstmaligen Klassenwahl gehören zu B07
  bzw. B13. B03 stellt nur den Mechanismus bereit, mit dem ein Charakter zur Sitzung gehört.
- Das Autosave-Intervall wird von B02 vorgegeben (45 Sekunden) und in B03 nicht erneut festgelegt.
- Die Zahl gleichzeitiger Spieler liegt bei 100–200 auf einer einzelnen Instanz (ADR-002).
- Bewegungssperre und Schadensimmunität sind Mittel, um einen Spieler ohne gültige Werte am Spielen
  zu hindern; die konkrete Umsetzung ist nicht Teil dieser Spec.
- Ein Wechsel des aktiven Charakters erfordert, falls überhaupt zugelassen, dasselbe Entladen und
  Laden wie ein Sitzungsende und -beginn.
