# Feature Specification: B14 · Commands, Permissions & Admin-Tools

**Feature Branch**: `014-commands-permissions-admin`

**Created**: 2026-09-03

**Status**: Draft

**Input**: Blockdokument `minecraft-rpg-spec/minecraft-rpg-spec/blocks/B14-commands-permissions-admin.md`,
einschließlich der am 2026-09-03 beantworteten vier offenen Fragen.

## Ausgangslage

B14 ist der letzte Block von Meilenstein M5 und der einzige, der von *allen* anderen abhängt.
Er ist auch der Block, den dreizehn andere bereits namentlich beim Namen genannt haben — meist
in derselben Formulierung: *dieses Kommando ist vorläufig, B14 ersetzt die Hülle.*

Wie bei B13 gilt: **wer den Entwurf des Blockdokuments für den Umfang hält, baut ein zweites Mal,
was schon steht.** Die folgende Bestandsaufnahme stammt aus dem Quelltext, nicht aus der Planung.

### Sechs Kommandos gibt es bereits — alle ausdrücklich befristet

`plugin.yml` sagt es in der eigenen Kopfzeile: *„The first commands block in this project
(ADR-028). Commands belong to B14 … B14 replaces the shell and leaves the interface standing
(FR-046)."* — das zitierte `FR-046` ist **B08b**s Nummer, nicht B14s; siehe die Regel unten.

> **Schreibweise für Anforderungsnummern.** Eine nackte `FR-###` in dieser Unterlage ist immer eine
> Nummer **dieses** Blocks. Anforderungen anderer Blöcke tragen den Block davor — `B01-FR-004`,
> `B08b-FR-046`. Das ist keine Förmlichkeit: B14 hat selbst FR-003, FR-004, FR-016, FR-018 und
> FR-024, und genau diese Nummern kommen in fremden Blöcken ebenfalls vor. Wörtliche Zitate bleiben
> unangetastet und werden wie oben danebengestellt.

| Kommando | Recht | Herkunft | Zeilen |
|---|---|---|---|
| `/char` | `rpg.ui.character` (`default: true`) | B13, ADR-051 | 110 |
| `/coins` | `rpg.currency.balance` / `rpg.currency.admin` | B08b, ADR-028 | 248 |
| `/trash` | `rpg.item.trash` (`default: true`) | B11 | 154 |
| `/xp` | `rpg.progression.admin` (`default: op`) | B06 | 243 |
| `/top` | `rpg.statistics.top` (`default: true`) | B12 | 119 |
| `/stats` | `rpg.statistics.own` (`default: true`) | B12 | 146 |

Zusammen **1042 Zeilen**, fünf davon in `rpg/plugin/command/`, `TrashCommand` abweichend in
`rpg/platform/item/`. Dazu ein achtes Recht ohne Kommando: `rpg.admin.no-class` (Betreten der Welt
ohne Klassenwahl, für Moderation).

**Tab-Completion gibt es — fünfmal, jede für sich gebaut.** `CharacterSheetCommand`, `TopCommand`,
`StatisticsCommand`, `CoinsCommand` und `XpCommand` implementieren jeweils `TabCompleter` mit einer
eigenen `onTabComplete`; `TrashCommand` hat keine und braucht auch keine, weil es kein Argument
nimmt. Das Problem ist also nicht die Abwesenheit, sondern die **Fünffachheit**: fünf handgeschriebene
Vorschlagslisten, die jede für sich neben der Argumentprüfung desselben Kommandos stehen und mit ihr
auseinanderlaufen können.

**Brigadier kommt im gesamten Baum null mal vor.** Jedes Kommando zerlegt seine Argumente über
`args.length` und `switch` selbst — genau das, was die Architekturvorgabe des Blocks untersagt.
Registriert wird klassisch über `plugin.yml` plus `getCommand(...).setExecutor(...)` in `RpgPlugin`.

### Das Audit-Log ist fertig gebaut — und wird von zwei Blöcken benutzt

B02 hat es unter **B02**-FR-018 geliefert und es ist vollständig:

- `AuditEntry` — Zeitpunkt, Handelnder, Aktion, betroffener Spieler, freie Details.
  Sein Javadoc nennt als Beispielaktion ausgerechnet `item_granted`.
- `AuditLogRepository` — `append(entry)` und `between(from, to)`. **Kein Update, kein Delete**, und
  das ist die Zusage, nicht eine Konvention: *„An audit log that can be edited proves nothing."*
- `JdbcAuditLogRepository`, Tabelle in `V1__baseline.sql`, `AuditLogTest`, `AnonymizationTest`.

Benutzt wird es heute von **B08b** (`CurrencyAdmin` — „the ledger answers what happened to this
account, the audit log answers what has this operator been doing") und **B06** (`Progression`
nimmt eine `actorId` entgegen und schreibt alten und neuen Stand).

B14 muss das Log also **nicht bauen**. B14 muss es *lückenlos benutzen* — und ist der erste Block,
der es auch **lesbar** macht: `between()` hat bis heute keinen Aufrufer außerhalb der Tests.

### Das Neuladen der Konfiguration ist fertig gebaut — und wurde nie aufgerufen

Das ist der schwerste Fund dieser Bestandsaufnahme.

`ConfigLoader.reloadAll()` existiert seit B01 (**B01**-FR-003 / **B01**-FR-004), mit einer starken
Zusage: **atomar** — schlägt eine einzige Quelle fehl, wird kein Handle aktualisiert und für *alle*
Module
bleibt die vorherige Konfiguration aktiv; einen gemischten Zustand gibt es nie. `ConfigHandle`
sorgt dafür, dass ein Neuladen die Module ohne weitere Verdrahtung erreicht, weil sie den Handle
halten und nicht den Wert.

Sechs Module halten einen `ConfigHandle` und haben einen Nachladehaken:
`CombatModule`, `ItemModule`, `MobModule`, `StatisticsModule`, `UiModule`, `ZoneModule`.
`ItemModule.notifyReloaded()` prüft beim Nachladen sogar noch einmal, dass alle Zonen, Kreaturenarten
und Namen existieren.

**Und trotzdem ist der ganze Pfad tot.** `reloadAll()` wird von keiner Produktivzeile aufgerufen,
nur von Tests. `notifyReloaded()` hat genau einen Aufrufer: seinen eigenen Test. `ZoneModule`
beschreibt in seinem Javadoc einen Einstiegspunkt, den es nicht gibt — es spricht von *„the
plugin's reload entry point"* und von *„every `/rpg reload`"*, als gäbe es dieses Kommando schon.

Das ist wörtlich dasselbe Muster, das B13 viermal getroffen hat und das
`vuntexrpg-block-done-criteria` festhält: **Modultests grün, Verdrahtung fehlt.** B14 ist der Block,
der diese Verdrahtung nachholt — und damit auch der Block, der herausfindet, ob die Zusage
„ein Neuladen erreicht alle Module" in der Wirklichkeit trägt.

### Was noch fehlt

- **Kein Weg, ein Item zu geben.** Das hat die B11-Serverabnahme behindert: Tränke waren
  ausschließlich über den Händler oder als Beute zu bekommen. Ein provisorisches `/rpgitem` wurde
  damals angeboten und nicht bestellt. `ItemStackFactory` und `Items` liegen bereit.
- **Kein Weg, eine Kreatur zu setzen.** `PaperMobPlacer` und `HordeRegistry` existieren, aber nur
  der Spawnplaner ruft sie.
- **Kein Weg, fremde Spielerdaten anzusehen.** `/coins <player>` ist die einzige Ausnahme.
- **Keine Sperrzeiten** auf den Kommandos, die die Datenbank befragen (`/stats`, `/top`).

## Die vier bindenden Antworten

Am 2026-09-03 entschieden; sie ersetzen die offenen Fragen im Blockdokument.

1. **Rechtemodell: nur Bukkit-Rechte.** Der Rechtebaum steht in `plugin.yml`, die Vergabe übernimmt
   Bukkits eigenes System oder ein beliebiges Plugin, das es bedient. **LuckPerms funktioniert damit
   automatisch mit, ohne dass wir eine Abhängigkeit aufnehmen** — keine neue Bibliothek, kein
   zusätzlicher Klassenlader-Blindfleck (siehe `vuntexrpg-classloader-blind-spot`). Die Rollenstufen
   Spieler / Moderator / Admin werden als *Rechtebaum* ausgedrückt, nicht als eigenes Rollenmodell.
2. **Alle sechs Kommandos ziehen auf ein einheitliches Brigadier-Framework um.** Das ist die
   Einlösung von **B08b**-FR-046 sowie ADR-028 und ADR-051. Die Schnittstellen bleiben unverändert
   stehen — ersetzt wird nur die Hülle.
3. **Alle vier Admin-Werkzeuggruppen sind im Umfang**: Items geben; Kreaturen spawnen und
   Konfiguration/Zonen neu laden; Stufe, Erfahrung und Klasse setzen; Spielerdaten lesend
   inspizieren. **Nachträglich eingeschränkt am 2026-09-03:** einzelne **Attribute** direkt zu
   setzen ist raus — siehe FR-024a.
4. **Nichts nach außen.** Keine Weboberfläche, keine Werkzeuge außerhalb des Spiels.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ein Kommando verhält sich wie jedes andere (Priority: P1)

Ein Spieler tippt `/stats ` und drückt Tab. Er bekommt die gültigen Zeiträume vorgeschlagen, statt
raten zu müssen. Er tippt einen Unsinn und bekommt eine Meldung, die sagt, welches Argument falsch
war und was dort erlaubt ist — dieselbe Bauart wie bei `/top`, `/coins` und jedem anderen Kommando.
Ein Betreiber, der ein neues Kommando anlegt, schreibt keine Argumentzerlegung mehr.

**Why this priority**: Das ist der Block. Ohne das gemeinsame Gerüst sind alle weiteren Kommandos
nur ein siebtes und achtes Einzelstück, und die vier ADR-Zusagen bleiben offen. Dies ist die
MVP-Grenze: ist sie erreicht, hat der Server eine einheitliche Bedienoberfläche, auch wenn noch kein
einziges neues Admin-Werkzeug existiert.

**Independent Test**: Vollständig prüfbar, indem die sechs vorhandenen Kommandos umgezogen werden
und ihr sichtbares Verhalten unverändert bleibt — bei zusätzlicher Tab-Completion und einheitlichen
Fehlermeldungen. Liefert Wert ohne jedes neue Werkzeug.

**Acceptance Scenarios**:

1. **Given** ein Spieler mit `rpg.statistics.own`, **When** er `/stats ` tippt und Tab drückt,
   **Then** erscheinen genau die gültigen Zeiträume und nichts sonst.
2. **Given** ein Spieler, **When** er `/xp give` ohne weitere Argumente absendet, **Then** nennt die
   Meldung das fehlende Argument und seinen erlaubten Wertebereich, statt eine Nutzungszeile zu
   wiederholen.
3. **Given** die sechs umgezogenen Kommandos, **When** ein Spieler sie wie vor dem Umzug benutzt,
   **Then** ist das Ergebnis identisch — gleiche Namen, gleiche Syntax, gleiche Ausgabe.
4. **Given** ein Argument, das einen Spielernamen erwartet, **When** der genannte Spieler nie auf
   dem Server war, **Then** bricht das Kommando mit einer Meldung ab, die den Namen nennt, und
   verändert nichts.

---

### User Story 2 - Ohne Recht kein Eingriff (Priority: P1)

Ein Moderator soll Spielerdaten ansehen dürfen, aber keine Coins verschieben. Ein Admin darf beides.
Ein Spieler ohne besonderes Recht sieht die Admin-Kommandos in der Tab-Completion gar nicht erst.

**Why this priority**: Das erste Akzeptanzkriterium des Blockdokuments. Ein Admin-Werkzeug ohne
Rechteprüfung ist schlimmer als keines, und die Prüfung gehört an *eine* Stelle im Gerüst, nicht in
jedes Kommando.

**Independent Test**: Prüfbar, indem für jedes Kommando ein Absender ohne das zugehörige Recht die
Ausführung versucht.

**Acceptance Scenarios**:

1. **Given** ein Spieler ohne `rpg.admin.item.give`, **When** er `/rpg item give …` absendet,
   **Then** wird nichts ausgeführt und er erhält eine Ablehnung.
2. **Given** derselbe Spieler, **When** er `/rpg ` tippt und Tab drückt, **Then** erscheinen nur
   Unterkommandos, für die er das Recht besitzt.
3. **Given** ein frisch aufgesetzter Server ohne jedes Rechte-Plugin, **When** ein Spieler beitritt,
   **Then** kann er `/char`, `/stats`, `/top`, `/coins` und `/trash` benutzen und kein einziges
   Admin-Kommando.
4. **Given** jedes im Block angelegte Recht, **When** der Server startet, **Then** ist es in
   `plugin.yml` mit Beschreibung und ausdrücklichem `default` deklariert — es gibt kein Recht, das
   nur im Quelltext existiert.

---

### User Story 3 - Ein Gegenstand entsteht auf Ansage (Priority: P2)

Ein Betreiber braucht für eine Abnahme fünf Manatränke. Heute muss er einen Händler suchen oder
Kreaturen töten, bis die Beute fällt. Mit B14 tippt er `/rpg item give <Spieler> <Vorlage> [Menge]`,
bekommt die Vorlagennamen per Tab vorgeschlagen, und der Eingriff steht im Audit-Log.

**Why this priority**: Die konkreteste Lücke aus der Praxis — sie hat nachweislich eine Abnahme
behindert. Klein im Umfang, weil `ItemStackFactory` und `Items` bereitliegen.

**Independent Test**: Prüfbar durch Vergabe jeder in `items.yml` definierten Vorlage an einen
Spieler und den Vergleich des Ergebnisses mit einem als Beute erhaltenen Exemplar.

**Acceptance Scenarios**:

1. **Given** ein Betreiber mit dem Recht, **When** er eine gültige Vorlage vergibt, **Then** trägt
   der Spieler denselben Gegenstand, den auch die Beute erzeugt hätte — gleiche Lore, gleicher
   Zustand, gleiche Kennzeichnung.
2. **Given** ein Spieler mit vollem Inventar, **When** ihm ein Gegenstand gegeben wird, **Then**
   greift dieselbe Regel wie bei Beute auf volles Inventar (B11 US7) und nichts geht verloren.
3. **Given** eine Vergabe, **When** sie ausgeführt wurde, **Then** steht im Audit-Log wer, was, wann
   und an wem.
4. **Given** ein Vorlagenname, den `items.yml` nicht kennt, **When** er abgesendet wird, **Then**
   bricht das Kommando ab und nennt den unbekannten Namen.

---

### User Story 4 - Die Konfiguration ändert sich ohne Neustart (Priority: P2)

Ein Betreiber ändert eine Zahl in `mobs.yml` und tippt `/rpg reload`. Ist die Datei gültig, gilt sie
sofort in allen Modulen. Ist sie es nicht, ändert sich **nichts** — auch nicht in den Dateien, die
gültig gewesen wären — und die Meldung nennt Datei, Pfad und erwarteten Wert.

**Why this priority**: Spart bei jeder künftigen Abnahme einen Serverneustart und ist der erste
Produktivaufruf eines Pfades, der seit B01 gebaut, getestet und nie benutzt wurde. Höher als die
übrigen Werkzeuge, weil er alle folgenden Abnahmen beschleunigt.

**Independent Test**: Prüfbar auf dem laufenden Server durch eine gültige und eine ungültige
Änderung, jeweils mit Beobachtung des Verhaltens *vor* und *nach* dem Kommando.

**Acceptance Scenarios**:

1. **Given** ein laufender Server, **When** eine gültige Änderung an `mobs.yml` nachgeladen wird,
   **Then** wirkt sie sich ohne Neustart aus und alle sechs Module arbeiten mit der neuen Fassung.
2. **Given** eine Änderung, die das Schema verletzt, **When** nachgeladen wird, **Then** bleibt für
   *jedes* Modul die vorherige Konfiguration aktiv und die Meldung nennt Datei, Dokumentpfad und
   erwarteten Wert.
3. **Given** eine Änderung an `zones.yml`, die eine Welt nennt, die es nicht gibt, **When**
   nachgeladen wird, **Then** wird sie abgelehnt und der Server läuft unverändert weiter — er stürzt
   nicht ab und startet nicht neu.
4. **Given** ein erfolgreiches Neuladen, **When** es abgeschlossen ist, **Then** nennt eine Logzeile
   **alle sechs** Module, deren Nachladehaken gelaufen ist — und keines fehlt (FR-023, FR-023a).
5. **Given** eine Änderung an `items.yml`, die auf eine unbekannte Zone zeigt, **When** nachgeladen
   wird, **Then** wird sie abgelehnt — `ItemModule`s Haken prüft Zonen, Arten und Namen, und dass
   die Ablehnung kommt, ist der Beweis, dass der Haken wirklich läuft.

---

### User Story 5 - Eine Kreatur entsteht auf Ansage (Priority: P3)

Ein Betreiber braucht für eine Abnahme genau die Kreatur, die er prüfen will, an genau der Stelle,
an der er steht — statt zu warten, bis der Spawnplaner sie zufällig setzt.

**Why this priority**: Beschleunigt Abnahmen deutlich, ist aber kein Ersatz für eine fehlende
Fähigkeit des Spiels. Nachrangig gegenüber Items, weil Kreaturen wenigstens von selbst erscheinen.

**Independent Test**: Prüfbar durch Setzen jeder in `mobs.yml` definierten Art und anschließende
Beobachtung über einen Neustart hinweg.

**Acceptance Scenarios**:

1. **Given** ein Betreiber mit dem Recht, **When** er eine Art aus dem Verzeichnis setzt, **Then**
   erscheint sie mit denselben Werten, die der Spawnplaner ihr gegeben hätte.
2. **Given** eine so gesetzte Kreatur, **When** der Chunk entladen und wieder geladen wird,
   **Then** wird sie **nicht** von der Aufräumregel aus ADR-050 entfernt — sie steht ordnungsgemäß
   in der `HordeRegistry`.
3. **Given** eine Art, die `mobs.yml` nicht kennt, **When** sie gesetzt werden soll, **Then** bricht
   das Kommando ab und nennt den unbekannten Schlüssel.
4. **Given** eine Zone, deren Spawn-Budget bereits voll ist, **When** ein Betreiber dort eine
   Kreatur setzt, **Then** gelingt es trotzdem — handgesetzte Kreaturen zählen gegen ihre eigene
   Obergrenze, nicht gegen das Budget.
5. **Given** die eigene Obergrenze für Handgesetztes ist erreicht, **When** eine weitere gesetzt
   werden soll, **Then** bricht das Kommando ab und nennt die Grenze.

---

### User Story 6 - Stufe und Klasse lassen sich richtigstellen (Priority: P3)

Nach einem Fehler steht ein Charakter auf der falschen Stufe oder in der falschen Klasse. Ein
Betreiber stellt das richtig, und jeder Eingriff hinterlässt eine Spur.

**Why this priority**: Support-Fall, kein Alltag. Beide Wege existieren bereits als öffentliche
Schnittstelle (`Progression.setProgress`, `ClassSelection.choose`); B14 baut nur die Hülle darum.
**Das Setzen einzelner Attribute gehört ausdrücklich nicht dazu** — siehe FR-024a.

**Independent Test**: Prüfbar durch Setzen und Zurücksetzen jedes Wertes an einem Testcharakter mit
anschließender Kontrolle des Audit-Logs.

**Acceptance Scenarios**:

1. **Given** ein Charakter auf Stufe 20, **When** ein Betreiber ihn auf Stufe 35 setzt, **Then**
   gelten alle abgeleiteten Werte sofort und der Eingriff steht mit altem und neuem Stand im
   Audit-Log.
2. **Given** ein Charakter der Klasse Krieger, **When** ein Betreiber ihn zum Schurken macht,
   **Then** läuft der Wechsel über denselben öffentlichen Weg wie eine Klassenwahl durch den Spieler
   und hinterlässt keinen Zustand, den dieser Weg nicht auch hinterlassen würde.
3. **Given** ein Wert außerhalb des erlaubten Bereichs, **When** er gesetzt werden soll, **Then**
   wird der Eingriff abgelehnt und nichts verändert.

---

### User Story 7 - Ein Betreiber sieht nach, ohne anzufassen (Priority: P3)

Ein Spieler meldet, seine Werte stimmten nicht. Ein Moderator sieht sich dessen Charakterblatt,
Statistiken, Inventar und Sitzungszustand an — **lesend**, ohne etwas zu verändern.

**Why this priority**: Macht die übrigen Werkzeuge erst brauchbar, weil man ohne Blick auf den
Zustand nicht weiß, was richtigzustellen ist. Nachrangig, weil rein diagnostisch.

**Independent Test**: Prüfbar, indem für einen zweiten Spieler jede Ansicht geöffnet und danach
geprüft wird, dass sich an dessen Daten nichts geändert hat.

**Acceptance Scenarios**:

1. **Given** ein Moderator mit dem Leserecht, **When** er das Charakterblatt eines anderen Spielers
   öffnet, **Then** sieht er dessen Werte und der Zustand des Spielers bleibt unverändert.
2. **Given** ein Spieler, der gerade nicht online ist, **When** ein Moderator dessen Daten ansieht,
   **Then** funktioniert das ebenso.
3. **Given** ein rein lesender Zugriff, **When** er stattgefunden hat, **Then** verändert er keine
   Spielerdaten.

---

### User Story 8 - Das Audit-Log lesen können (die Schreibseite ist überall Voraussetzung) (Priority: P4)

Ein Betreiber will wissen, wer in der letzten Woche Coins vergeben hat. Er fragt das Audit-Log ab
und bekommt die Einträge, neueste zuerst.

**Why this priority**: Die Schreibseite ist Voraussetzung jeder datenverändernden Story und wird dort
mitgeprüft; eigenständig neu ist nur die **Leseseite**. Deshalb zuletzt — aber nicht optional.

**Independent Test**: Prüfbar, indem nacheinander je ein Eingriff aus jeder Werkzeuggruppe
ausgeführt und anschließend über die Abfrage wiedergefunden wird.

**Acceptance Scenarios**:

1. **Given** je ein datenverändernder Eingriff aus jeder Gruppe, **When** das Log über den Zeitraum
   abgefragt wird, **Then** erscheint jeder einzelne mit Handelndem, Aktion, Betroffenem und
   Zeitpunkt.
2. **Given** ein rein lesender Zugriff aus User Story 7, **When** das Log abgefragt wird, **Then**
   erscheint er **nicht** — protokolliert wird, was verändert, nicht was angesehen wird.

---

### Edge Cases

- **Ein Kommando von der Serverkonsole**, das einen Spielerbezug braucht (`/char`, `/trash`): es
  bricht mit einer Meldung ab, die sagt, dass es einen Spieler braucht — es wirft keine Ausnahme in
  die Konsole.
- **Ein Neuladen, während ein Spieler mitten in einem Kampf steht**: Die Zusage ist Atomarität der
  *Konfiguration*, nicht des Spielzustands. Was bereits läuft, läuft mit den Werten zu Ende, mit
  denen es begonnen hat.
- **Ein Neuladen, das eine Zone entfernt, in der gerade jemand steht.** Muss ausdrücklich entschieden
  und geprüft werden, statt still zu geschehen.
- **Zwei Betreiber ändern denselben Wert gleichzeitig**: Beide Eingriffe stehen im Audit-Log, der
  zweite gewinnt; das Log macht die Reihenfolge nachvollziehbar.
- **Ein Kommando, das die Datenbank befragt, in schneller Folge abgesetzt**: das Rate-Limit greift
  und die Ablehnung sagt, wann es wieder geht.
- **Ein Spielername, der zu mehreren Konten passt oder nie auf dem Server war**: Abbruch mit klarer
  Meldung, kein stiller Griff ins Leere.
- **Ein anonymisierter Spieler** (B02): Das Audit-Log führt ihn mit dem Ersatzkennzeichen; ein
  Eingriff an ihm bleibt möglich, sein Klarname erscheint nirgends.
- **Tab-Completion mit tausenden Vorlagen oder Spielern**: die Liste wird begrenzt und gefiltert,
  nicht vollständig gesendet.

## Requirements *(mandatory)*

### Functional Requirements

#### Das Gerüst

- **FR-001**: Das System MUSS ein einheitliches Kommandogerüst bereitstellen, in dem Kommandos
  deklarativ aus benannten, typisierten Argumenten zusammengesetzt werden; manuelle Zerlegung einer
  Argumentzeichenkette MUSS im gesamten Produktivcode verschwinden.
- **FR-002**: Das Gerüst MUSS Tab-Completion aus derselben Deklaration ableiten, aus der es die
  Argumente prüft — eine Vorschlagsliste, die von der Prüfung abweichen kann, ist ausgeschlossen.
- **FR-003**: Das Gerüst MUSS die Rechteprüfung an genau einer Stelle vornehmen; ein einzelnes
  Kommando MUSS sie nicht selbst durchführen können.
- **FR-004**: Ein Argumentfehler MUSS eine Meldung erzeugen, die das betroffene Argument und den
  erlaubten Wertebereich nennt.
- **FR-005**: Alle sechs vorhandenen Kommandos (`/char`, `/coins`, `/trash`, `/xp`, `/top`,
  `/stats`) MÜSSEN auf das Gerüst umziehen, **ohne** ihren Namen, ihre Syntax oder ihre Ausgabe zu
  verändern.
- **FR-006**: Die öffentlichen Schnittstellen der Blöcke, die diese Kommandos aufrufen, MÜSSEN
  unverändert bleiben. B14 ersetzt die Hülle (ADR-028, ADR-051, **B08b**-FR-046).
- **FR-007**: Kommandos MÜSSEN ausschließlich öffentliche Blockschnittstellen aufrufen, nie Interna.
- **FR-008**: Jedes Kommando MUSS von der Serverkonsole aus aufrufbar sein; braucht es einen
  Spielerbezug, MUSS es mit einer erklärenden Meldung abbrechen.
- **FR-009**: Alle Texte MÜSSEN über B13s Nachrichtenschlüssel laufen; fest verdrahteter,
  spielersichtbarer Text ist ausgeschlossen.

#### Rechte

- **FR-010**: Jedes Recht MUSS in `plugin.yml` mit Beschreibung und ausdrücklichem `default`
  deklariert sein. Ein Recht, das nur im Quelltext vorkommt, MUSS beim Start auffallen.
- **FR-011**: Das System DARF KEINE Abhängigkeit zu einem externen Rechte-Plugin aufnehmen. Der
  Rechtebaum MUSS so aufgebaut sein, dass ein beliebiges Plugin, das Bukkit-Rechte vergibt
  (darunter LuckPerms), ihn ohne Zutun bedienen kann.
- **FR-012**: Der Rechtebaum MUSS die drei Stufen Spieler, Moderator und Admin abbilden — als
  Rechteknoten, nicht als eigenes Rollenmodell.
- **FR-013**: Ohne passendes Recht MUSS ein Admin-Kommando wirkungslos abbrechen.
- **FR-014**: Tab-Completion DARF nur Unterkommandos und Werte vorschlagen, für die der Absender das
  Recht besitzt.

#### Admin-Werkzeuge

- **FR-015**: Ein Betreiber MUSS jede in `items.yml` definierte Vorlage an einen Spieler vergeben
  können, in wählbarer Menge.
- **FR-016**: Ein so vergebener Gegenstand MUSS von einem als Beute erhaltenen nicht zu
  unterscheiden sein.
- **FR-017**: Ein volles Inventar MUSS bei der Vergabe dieselbe Regel auslösen wie bei Beute; es
  DARF nichts verloren gehen.
- **FR-018**: Ein Betreiber MUSS jede in `mobs.yml` definierte Art an seiner Position setzen können.
- **FR-019**: Eine so gesetzte Kreatur MUSS ordnungsgemäß in der `HordeRegistry` geführt werden, so
  dass die Aufräumregel aus ADR-050 sie nicht entfernt.
- **FR-019a**: Handgesetzte Kreaturen MÜSSEN getrennt gezählt werden: nicht gegen das Zonenbudget,
  sondern gegen eine eigene, in der Konfiguration festgelegte Obergrenze. Ist diese erreicht, MUSS
  das Kommando abbrechen und die Grenze nennen. Das Zonenbudget MUSS dadurch unangetastet bleiben.
- **FR-019b**: Handgesetzte Kreaturen MÜSSEN denselben Aufräum- und Despawn-Regeln unterliegen wie
  jede andere Kreatur. Die Unterscheidung lebt ausschließlich im Speicher: Nach einem Neustart wird
  die Registry nicht wiederhergestellt und ADR-050 entfernt jede getaggte Kreatur, die nicht darin
  steht — es lebt dann keine handgesetzte mehr und beide Zählungen stehen wieder auf null. Es DARF
  daher **keine** Kennzeichnung im `PersistentDataContainer` und keine Persistenz dafür geben.
- **FR-020**: Ein Betreiber MUSS die gesamte Konfiguration im laufenden Betrieb neu laden können.
- **FR-021**: Ein Neuladen MUSS atomar sein: Wird eine Quelle abgelehnt, MUSS für **alle** Module
  die vorherige Konfiguration aktiv bleiben (**B01**-FR-004).
- **FR-022**: Ein abgelehntes Neuladen MUSS Datei, Dokumentpfad und erwarteten Wert nennen und den
  Server weiterlaufen lassen.
- **FR-023**: Nach einem erfolgreichen Neuladen MÜSSEN **alle sechs** Module mit Nachladehaken
  diesen ausgeführt haben — `CombatModule`, `ItemModule`, `MobModule`, `StatisticsModule`,
  `UiModule`, `ZoneModule`. Der Pfad DARF nicht länger nur in Tests bestehen.
- **FR-023a**: Ein erfolgreiches Neuladen MUSS **nachweisbar machen, welche Haken gelaufen sind** —
  eine Logzeile, die die Module beim Namen nennt. Ohne sie ist FR-023 nicht prüfbar: dass ein
  Kommando „ok" meldet, sagt nichts darüber, ob sechs Module oder eines erreicht wurden. Diese Zeile
  ist der einzige Weg, den seit B01 unbelegten Pfad ohne sechs einzelne Spielprüfungen zu belegen.
- **FR-024**: Ein Betreiber MUSS Stufe, Erfahrung und Klasse eines Charakters setzen können. Ein
  Klassenwechsel MUSS über denselben öffentlichen Weg laufen wie eine Klassenwahl durch den Spieler.
- **FR-024a**: Das direkte Setzen einzelner **Attribute** ist ausdrücklich **nicht** im Umfang
  (entschieden am 2026-09-03, ADR fällig). `StatEngine` hat bewusst keinen Setzer: Werte sind aus
  `ModifierSet`s je Quelle abgeleitet, und `SourceKind` ist ein geschlossener Aufzählungstyp, dessen
  Deklarationsreihenfolge zugleich die Summationsreihenfolge ist — die Grundlage der Zusage „gleiche
  Quellen, gleiche Zahlen". Ein Admin-Wert bräuchte einen siebten Eintrag darin und wäre trotzdem
  nach dem nächsten Anmelden verschwunden, weil B04 Beiträge nicht persistiert. Ein Werkzeug, das
  Dauerhaftigkeit verspricht und sie nicht liefert, ist schlechter als keines.
- **FR-025**: Ein Wert außerhalb des erlaubten Bereichs MUSS abgelehnt werden, ohne etwas zu
  verändern.
- **FR-026**: Ein Betreiber MUSS Charakterblatt, Statistiken, Inventar und Sitzungszustand eines
  beliebigen Spielers ansehen können — auch eines, der gerade nicht online ist.
- **FR-027**: Diese Ansichten MÜSSEN rein lesend sein.

#### Audit und Sperrzeiten

- **FR-028**: Jeder Eingriff, der Spielerdaten verändert, MUSS einen Eintrag im vorhandenen
  Audit-Log erzeugen: wer, was, wann, an wem — bei einer Wertänderung mit altem und neuem Stand.
- **FR-029**: Ein rein lesender Zugriff DARF KEINEN Audit-Eintrag erzeugen.
- **FR-030**: Ein Betreiber MUSS das Audit-Log über einen Zeitraum abfragen können, neueste zuerst.
- **FR-031**: Das Audit-Log MUSS anfügend bleiben — B14 DARF keinen Weg schaffen, einen Eintrag zu
  ändern oder zu löschen.
- **FR-031a**: Diese Zusage MUSS von einem Test getragen werden, nicht von der Absicht. Ein
  Architekturtest MUSS fehlschlagen, sobald aus B14 heraus ein anderer Schreibweg auf das Audit-Log
  entsteht als `append`. Eine negative Zusage, die niemand prüft, ist eine Absichtserklärung.
- **FR-032**: Kommandos, die eine Datenbankabfrage auslösen, MÜSSEN einer Sperrzeit je
  Absender unterliegen; die Ablehnung MUSS sagen, wann es wieder geht.
- **FR-033**: Tab-Completion MUSS ihre Vorschlagsliste begrenzen und am bereits Getippten filtern.

#### Absicherung

- **FR-034**: Ein Test MUSS beim Bauen fehlschlagen, wenn ein Kommando am Gerüst vorbei registriert
  wird oder ein Recht ohne Deklaration in `plugin.yml` benutzt wird.
- **FR-035**: `FullBootstrapTest` MUSS jedes Kommando und jedes Recht auf einem echten Serverstart
  abdecken (siehe `vuntexrpg-block-done-criteria` und `vuntexrpg-classloader-blind-spot`).
- **FR-036**: Der **Lesepfad** des Audit-Logs MUSS gegen eine echte PostgreSQL-Instanz geprüft
  werden (Testcontainers, Constitution VII). `AuditLogRepository.between` hat außerhalb von Tests
  bis heute keinen Aufrufer und ist nie gegen eine Datenbank gelaufen — dieselbe Lage wie
  `reloadAll()`. Ein Block, der genau dieses Muster aufräumt, DARF es nicht selbst hinterlassen.

### Key Entities

- **Kommando**: Ein benannter Einstiegspunkt mit Beschreibung, benötigtem Recht und einem Baum aus
  Unterkommandos und Argumenten.
- **Argument**: Ein benannter, typisierter Platz mit Wertebereich, Vorschlagsquelle und der Angabe,
  ob er verpflichtend ist.
- **Rechteknoten**: Ein Schlüssel mit Beschreibung und Voreinstellung, der einen Zugriff freigibt.
- **Audit-Eintrag**: Zeitpunkt, Handelnder, Aktion, betroffener Spieler, Details. Vorhanden
  (`AuditEntry`), unverändert übernommen.
- **Sperrzeit**: Je Absender und Kommando die Erinnerung, wann zuletzt ausgeführt wurde.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Alle Kommandos des Servers beziehen ihre Tab-Completion aus derselben Deklaration, aus
  der sie ihre Argumente prüfen. Heute sind es fünf handgeschriebene `onTabComplete`, die neben der
  Prüfung stehen; danach ist es keine.
- **SC-002**: Kein Produktivcode zerlegt Kommandoargumente noch selbst — messbar als null Vorkommen
  im Baum.
- **SC-003**: Ein Betreiber kann einen beliebigen Gegenstand aus der Konfiguration in unter zehn
  Sekunden in der Hand eines Spielers erscheinen lassen, ohne Kampf, Händler oder Neustart.
- **SC-004**: Eine Balancing-Änderung ist ohne Serverneustart wirksam; die Abnahme eines
  Konfigurationswertes dauert Sekunden statt einer vollen Startzeit.
- **SC-005**: Eine ungültige Konfiguration lässt in 100 % der Fälle **alle** Module auf ihrer
  vorherigen Fassung und den Server laufen.
- **SC-006**: Jeder datenverändernde Eingriff ist im Audit-Log wiederauffindbar — geprüft über je
  einen Eingriff aus jeder Werkzeuggruppe, ohne Ausnahme.
- **SC-007**: Kein Admin-Kommando lässt sich ohne das zugehörige Recht ausführen — geprüft für
  jedes Kommando einzeln.
- **SC-008**: Die sechs umgezogenen Kommandos verhalten sich für den Spieler unverändert; kein
  Abnahmeschritt aus B08b, B11, B12 oder B13 muss angepasst werden.
- **SC-009**: Nach dem Block gibt es keinen Block mehr, der ein eigenes vorläufiges Kommando führt —
  die vier offenen ADR-Zusagen (ADR-028, ADR-051 und die beiden aus B12) sind geschlossen.
- **SC-010**: Das Zonen-Spawnbudget führt nach beliebig vielen Admin-Spawns dieselbe Zahl wie ohne
  sie; die Umgehung ist auf die konfigurierte Obergrenze beziffert und nachweisbar begrenzt.

## Assumptions

- **Kommandostruktur**: Die sechs Spielerkommandos behalten ihre kurzen Namen auf oberster Ebene —
  sie sind für Spieler gedacht und ihre Namen stehen bereits in Abnahmeschritten. Die neuen
  Admin-Werkzeuge liegen unter einem gemeinsamen Wurzelkommando `/rpg <Unterkommando>`. Das folgt
  dem Namen, den `ZoneModule` in seinem Javadoc bereits benutzt (`/rpg reload`).
- **Brigadier über Paper**: Die Argumentbäume entstehen über die von Paper 26.2 bereitgestellte
  Brigadier-Anbindung; es wird keine eigene Bibliothek aufgenommen. Ob dieser Weg mit Papers
  `libraries:`-Klassenlader trägt, beweist erst der echte Serverstart
  (`vuntexrpg-classloader-blind-spot`).
- **Rollenstufen**: „Moderator" ist kein Sonderbegriff des Systems, sondern die Menge der Rechte mit
  lesendem Zugriff plus der bereits vorhandenen `rpg.admin.no-class`. Bukkit kennt als
  Voreinstellung nur *alle* oder *Operator*; alles dazwischen vergibt der Betreiber mit dem
  Rechte-Plugin seiner Wahl.
- **Sperrzeit**: Voreinstellung ist eine kurze Sperrzeit je Spieler und Kommando, in der
  Konfiguration einstellbar. Sie gilt nicht für die Konsole.
- **Audit-Log**: Wird unverändert übernommen. B14 fügt keine Spalte hinzu; alles Zusätzliche geht in
  das vorhandene Detailfeld.
- **Anonymisierung**: Das in B02 gebaute Verhalten gilt unverändert weiter; B14 schafft keinen Weg,
  einen anonymisierten Klarnamen wieder sichtbar zu machen.
- **Kein neues Schema**: B14 persistiert nichts Eigenes. Keine Migration, kein Testcontainers über
  das hinaus, was das Audit-Log schon mitbringt.
- **`/rpgitem` als Name**: In der Vorabsprache genannt, hier als `/rpg item give` umgesetzt, damit
  alle Admin-Werkzeuge unter einer Wurzel liegen. Die Fähigkeit ist dieselbe.

## Die eine geklärte Frage

**Zählt eine vom Betreiber gesetzte Kreatur gegen das Spawn-Budget?**
Entschieden am 2026-09-03: **Sie bekommt einen eigenen, begrenzten Bestand.**

Die Frage war echt, weil ADR-050 beide Antworten stützt. Jede getaggte Kreatur muss in der
`HordeRegistry` stehen, sonst räumt die Regel sie beim nächsten Chunk-Laden weg (FR-019). Die
Registry *ist* aber zugleich das Budget — sie mitzuzählen hieße, dass ein voller Bestand das Setzen
verweigert, ausgerechnet dann, wenn man die Kreatur zum Prüfen braucht.

Handgesetzte Kreaturen werden daher **geführt, aber getrennt gezählt**: nicht gegen das Zonenbudget,
sondern gegen eine eigene Obergrenze (FR-019a). Damit bleibt das Budget die Wahrheit über die
gespawnte Population, das Werkzeug bleibt auch bei vollem Bestand benutzbar, und die Umgehung ist
**beziffert statt unbegrenzt** — was ADR-050 als Bedingung formuliert hat: der Unterschied zwischen
„gehört nicht dazu" und „wurde vergessen" muss im Code stehen.
