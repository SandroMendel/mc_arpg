# Feature Specification: B12 · Statistiken & Leaderboards

**Feature Branch**: `012-stats-leaderboards`

**Created**: 2026-08-28

**Status**: Draft

**Input**: B12 — Statistiken & Leaderboards. Erfassung von Spielerkennzahlen und deren
Aufbereitung zu Ranglisten. Quelle: `minecraft-rpg-spec/minecraft-rpg-spec/blocks/B12-stats-leaderboards.md`,
ergänzt um `01-architecture.md`, `02-decisions.md` und die Constitution.

---

## Ausgangslage

B12 ist der erste Block der dritten Schicht. Er erzeugt keinen neuen Spielinhalt: er zählt,
was die Blöcke darunter ohnehin tun, und macht daraus eine Rangliste. Abhängig von B02
(Persistenz), B05 (Kampf) und B06 (Progression); benötigt von B13 und B14.

Der Umfang aus dem Blockdokument ist unverändert gültig: Metrikerfassung im Gameplay-Pfad,
asynchrone Aggregation, Leaderboards über Materialized Views mit Cache, persönliche
Statistikansicht, Zeiträume allzeit und saisonal.

### Drei Funde, die die Spec geformt haben

**Erstens: das Rohr liegt seit B02 fertig da und hat nie einen Schreiber gesehen.**
`rpg.player_statistic_daily` existiert seit dem Baseline-Schema, mit Primärschlüssel
`(player_id, metric, day)`, zwei Indizes — einer davon trägt den Kommentar *„Leaderboards (B12)
sum per metric across days"* — und der Zusage, unbegrenzt aufbewahrt zu werden.
`StatisticsRepository` bietet `increment`, `sum` und `total`; `JdbcStatisticsRepository`
sammelt Deltas im Speicher, meldet sie dem Write-Behind-Koordinator und schreibt sie gebündelt.
Registriert ist das Ganze in `PersistenceModule` und im `FlushCycle`.

Produktionsaufrufe von `increment()`: **null**. Der einzige Aufrufer heute ist ein Test.
B12 baut dieses Fundament also nicht, sondern schließt es an — dieselbe Lage wie
`EquipmentPurchase` vor B11, das einen ganzen Block lang ungenutzt dastand. Was B12 wirklich
neu bauen muss, sind die Erfassungspunkte, die Ranglisten und die Anzeige.

**Zweitens: die Tabelle kann nur addieren — und zwei der gewünschten Metriken sind keine Summen.**
Der gesamte Schreibweg hängt an einem Satz:
`ON CONFLICT (player_id, metric, day) DO UPDATE SET value = value + excluded.value`.
Genau weil die Aktualisierung *addiert*, kann das Delta geschrieben werden, ohne den
gespeicherten Wert vorher zu lesen — das ist B02s FR-007 und der Grund, warum tausend Kills
einen Schreibvorgang kosten und nicht tausend.

Daran zerbrechen zwei Wünsche:

- **Höchster Schaden ist ein Maximum, kein Summand.** Er lässt sich in diesem Schreibweg nicht
  ausdrücken, ohne vorher zu lesen — was verboten ist — oder ohne einen zweiten Schreibweg
  (`GREATEST` statt `+`) auf derselben Tabelle.
- **Level, XP und Coins sind Zustände, keine Tageszähler.** Die Summe der Tageslevel eines
  Spielers ist bedeutungslos. Diese Ranglisten müssen aus `rpg.character_progress` und
  `rpg.character_balance` gelesen werden, wo die Wahrheit ohnehin schon steht. Sie in die
  Tagestabelle zu spiegeln, wäre eine zweite Wahrheit über denselben Wert.

**Drittens: die Statistik hängt am Konto, das Spiel seit ADR-011 am Charakter.**
`player_statistic_daily` ist mit `player_id` verschlüsselt. Ein Spieler mit drei Charakteren
hat eine Kill-Zahl, nicht drei. Für Zähler ist das die richtige Ebene — eine Rangliste vergleicht
Menschen, nicht Rollen. Für Zustände ist es die falsche: ein Level gehört einem Charakter.
Beide Ebenen müssen in derselben Ansicht nebeneinander bestehen, ohne so zu tun, als wären sie
dieselbe.

Dazu kommt die Anonymisierung: `JdbcPlayerStateRepository` zeigt die Statistikzeilen eines
gelöschten Kontos per `REPOINT_STATISTICS` auf eine anonyme Kennung um. Die Zahlen überleben,
der Name nicht. Eine öffentliche Rangliste braucht dafür eine ausdrückliche Regel, sonst steht
dort eine Zeile, deren Namen niemand mehr auflösen kann.

---

## Clarifications

### Session 2026-08-28 — die vier offenen Fragen des Blockdokuments

**Erfasste Metriken.** Level und XP, Coins, Mob-Kills **je Mob-Art**, Tode **je Verursacher-Art**,
Bosskills, Spielzeit, höchster Schaden. Jede davon in vier Zeiträumen: Tag, Woche, Saison, Allzeit.
Damit ist der volle Umfangstext gewählt — einschließlich des höchsten Schadens, der als
Maximum-Metrik einen zweiten Schreibweg im Fundament von B02 verlangt (Fund 2), und
einschließlich der Aufschlüsselung nach Mob-Art, die aus einem Metrikschlüssel eine Familie von
Schlüsseln macht.

**Öffentlich oder privat.** Alles ist öffentlich rankbar — mit **einer** Ausnahme: die
Aufschlüsselung *woran* ein Spieler gestorben ist, bleibt ihm allein vorbehalten. Ein fremdes
Profil zeigt die Gesamtzahl der Tode, nie die Verteilung auf die Verursacher.

**Saison.** Saison mit **Abschluss und Belohnung**, nicht nur als Zeitraum-Ausschnitt. Das zieht
B08b (Coins) und B11 (Items) in diesen Block hinein und verlangt einen Endstand, der eingefroren
wird, sowie eine Belohnung, die auch ein zum Saisonende abwesender Spieler nicht verliert.

**Anzeigeform.** GUI-Fenster über Commands **und** ein Hologramm im Hub. Die Fenster folgen dem
Muster, das `/coins` und das Händlerfenster aus B11 bereits etabliert haben; das Hologramm folgt
dem Muster des Händler-NPC aus B11 — vor dem Setzen entfernen, gegen das Mob-Budget unsichtbar,
gehärtet gegen alles, was eine Entität in dieser Welt sonst erwartet.

### Was sich daraus zwangsläufig ergibt

Wem ein Kill gehört, war zunächst als „dem größten Beitragenden" beantwortet — der Regel, nach
der B11 die Beute vergibt (`CombatDeathEvent.lootRecipient()` gegenüber `killer()`). Die zweite
und die dritte Runde haben diese Antwort in zwei Schritten ersetzt; **gültig ist, was unter
„dritte Runde" steht.** Der Kill folgt der Beute nicht mehr, und das ist kein Versehen: eine
Beute lässt sich nicht teilen, eine Zahl schon.

### Session 2026-08-29 — zweite Runde

- **Q**: Wem wird ein Kill zugerechnet, wenn die Töter in einer Party sind? → **A**: Jedem
  Mitglied in Reichweite.
- **Q**: Zählt Spielzeit auch, wenn der Spieler nichts tut? → **A**: Die Rangliste zählt die
  **aktive** Zeit mit Pause bei Untätigkeit. Die reine Onlinezeit wird zusätzlich erfasst, ist
  aber **nur für den Spieler selbst** einsehbar. Außerdem wird Spielzeit **je Region** erfasst.
- **Q**: Wie verhält sich die Spielzeit je Region zur Trennung zwischen öffentlich und privat?
  → **A**: Die Zonenaufteilung ist **komplett privat**; öffentlich ist allein die Gesamtsumme
  der aktiven Zeit.
- **Q**: Die Aufschlüsselung nach Mob-Art vervielfacht die Zeilen pro Spieler und Tag. Wie gehen
  wir damit um? → **A**: Unverändert hinnehmen.
- **Q**: Was passiert mit einer Saisonbelohnung, die nie abgeholt wird? → **A**: Sie bleibt
  unbegrenzt bestehen.

**Die Party-Antwort dreht eine Regel um, die eine Zeile weiter oben noch anders stand.**
Außerhalb einer Party bekommt der größte Beitragende den Kill — das bleibt. Innerhalb einer
Party bekommt ihn **jeder in Reichweite**, und zwar aus demselben Grund, aus dem B06 Erfahrung
und Coins an alle in Reichweite verteilt: gemeinsames Spielen darf nicht schlechter sein als
allein zu spielen. Der Preis ist benannt und angenommen: die Summe aller Kill-Zähler ist größer
als die Zahl der getöteten Kreaturen. Die Metrik bedeutet damit **Beteiligung an einem Kill**,
nicht „von mir erledigt" — und die Anzeige muss sie so benennen, sonst zählt der Server etwas
anderes, als der Spieler liest.

**Die Spielzeit zerfällt in zwei Uhren und eine Aufteilung.** Öffentlich gerankt wird die aktive
Zeit; ohne die Pause bei Untätigkeit gewinnt die Rangliste, wer den Client nachts laufen lässt.
Die Onlinezeit und die Aufteilung nach Zonen sind erfasst, aber privat — womit dieser Block
**drei** private Werte kennt statt einem, und die Sichtbarkeitsregel keine Ausnahme mehr ist,
sondern eine Liste.

### Session 2026-08-29 — dritte Runde

- **Q**: Zehn Spieler ohne Party legen einen Regionsboss. Wer bekommt den Bosskill gezählt?
  → **A**: Jeder mit nennenswertem Anteil.
- **Q**: Was hält die Aktiv-Uhr am Laufen? → **A**: Alles, was der Spieler auslöst — Bewegung,
  Kampf, Interaktion, Menüs, Commands.
- **Q**: Zählt Schaden eines beschworenen Klons für den höchsten Schaden des Spielers?
  → **A**: Ja. Der Klon ist die Fähigkeit des Spielers.
- **Q**: Welche Ranglisten gibt es? → **A**: Alle Kombinationen automatisch, keine kuratierte
  Liste.
- **Q**: Welche Ranglisten werden am Saisonende belohnt? → **A**: Eine einzige **Gesamtwertung**.

**Die erste Antwort ersetzt die Kill-Regel ein zweites Mal.** Sie ist keine Sonderregel für
Bosse, sondern die neue allgemeine: **jeder** Spieler, dessen Schadensanteil eine Schwelle
erreicht, bekommt den Kill gezählt. Der Anlass war der Boss — B05 verteilt Erfahrung nach Anteil
an **alle** Beitragenden, nicht nur an den größten, und nach der alten Regel hätten neun von zehn
Bossteilnehmern Erfahrung bekommen, aber keinen Eintrag in ihrer Statistik. Die Partyregel aus
der zweiten Runde bleibt daneben bestehen und ist jetzt der Sonderfall: ein Mitglied in
Reichweite zählt auch dann, wenn es **gar keinen** Schaden gemacht hat. Kill-Klau gibt es damit
nicht mehr — es gibt nichts zu klauen.

**Der Klonschaden zählt, und das weicht bewusst von B11 ab.** Dort nutzt ein beschworener Klon
nichts ab (FR-041a). Die Asymmetrie ist gewollt und hat einen Grund: der Klon trägt **keine
eigene Ausrüstung**, die sich abnutzen könnte — sein Schaden entsteht aber aus der Fähigkeit des
Spielers. Was er kostet, kostet ihn; was er leistet, leistet der Spieler.

**Die Gesamtwertung ist die größte Änderung dieser Runde.** Die Saison kürt einen Spieler, nicht
zweiundzwanzig Ranglisten. Das verlangt eine Punktzahl über mehrere Metriken, und damit eine
**Gewichtung**, die konfigurierbar, beim Start geprüft und für jeden Spieler nachvollziehbar sein
muss. Sie ist die dreiundzwanzigste Rangliste — die einzige, an der eine Belohnung hängt.

*(Zur Zahl: fünf öffentliche Zähler- und Maximumsmetriken — Mob-Kills, Bosskills, Tode, aktive
Spielzeit, höchster Schaden — in je vier Zeiträumen ergeben zwanzig, dazu Level und Coins als
Zustandsranglisten. Die erste Fassung dieses Absatzes rechnete mit achtzehn und hatte den höchsten
Schaden übersehen.)*

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Was ich tue, wird gezählt, und der Tick merkt es nicht (Priority: P1)

Ein Spieler tötet Kreaturen, stirbt, teilt Schaden aus und ist eine Weile online. All das wird
gezählt — je Mob-Art aufgeschlüsselt, tagesgenau abgelegt, über einen Neustart hinweg konsistent.
Im Kampfpfad kostet das Zählen nichts als einen Zähler im Speicher: kein Datenbankzugriff, keine
Berechnung, keine neue wiederkehrende Aufgabe.

**Why this priority**: Ohne Erfassung ist jede Rangliste eine leere Liste, und jeder Tag ohne
Erfassung ist ein Tag, dessen Zahlen niemals nachträglich entstehen können. Alles Weitere in
diesem Block ist Darstellung von etwas, das hier entsteht. Diese Story allein ist bereits ein
brauchbarer Zwischenstand: die Werte sammeln sich, während der Rest gebaut wird.

**Independent Test**: Eine Kreatur bekannter Art töten, an einer anderen sterben, Schaden
austeilen, ausloggen, Server neu starten — und die gespeicherten Tageswerte gegen das Erwartete
prüfen. Ohne jede Anzeige.

**Acceptance Scenarios**:

1. **Given** ein Spieler im Kampf, **When** er eine Kreatur der Art `zombie_warrior` tötet,
   **Then** steigt sein Tageswert für Kills dieser Art um eins — und nur dieser.
2. **Given** zwei Spieler ohne Party, die beide über der Schwelle liegen, **When** die Kreatur
   stirbt, **Then** zählt der Kill für **beide** — unabhängig davon, wer den letzten Treffer
   gelandet hat.
3. **Given** ein dritter Spieler, der einen einzigen Treffer unterhalb der Schwelle beigetragen
   hat, **When** die Kreatur stirbt, **Then** zählt der Kill für ihn **nicht**.
4. **Given** ein Regionsboss und zehn Spieler ohne Party, von denen acht über der Schwelle
   liegen, **When** der Boss stirbt, **Then** steigt der Bosskill-Zähler bei genau diesen acht.
5. **Given** eine Kreatur, die ohne Zutun eines Spielers stirbt, **When** sie stirbt, **Then**
   steigt kein Kill-Zähler.
6. **Given** ein Spieler, der von einer Kreatur getötet wird, **When** er stirbt, **Then** steigt
   sowohl seine Gesamtzahl der Tode als auch der Zähler für genau diese Verursacher-Art.
7. **Given** ein Spieler, der durch Sturz stirbt, **When** er stirbt, **Then** landet der Tod
   unter dem festgelegten Schlüssel für Umgebungstode, nicht unter einer Mob-Art.
8. **Given** ein Spieler mit tausend Kills an einem Tag, **When** der Flush läuft, **Then**
   entsteht ein Schreibvorgang für diese Metrik, nicht tausend.
9. **Given** ein Spieler mit drei Charakteren, **When** er mit jedem tötet, **Then** stehen alle
   Kills auf **einer** Zahl seines Kontos.
10. **Given** ein Serverneustart, **When** der Spieler zurückkehrt, **Then** sind seine Werte
    unverändert.
11. **Given** eine Party aus drei Spielern, von denen zwei in Reichweite sind, **When** die
    Kreatur stirbt, **Then** zählt der Kill für beide in Reichweite — auch für den, der keinen
    Schaden gemacht hat — und nicht für den dritten.
12. **Given** ein Spieler, der die konfigurierte Dauer lang nichts tut, **When** die Zeit läuft,
    **Then** steht seine aktive Zeit still, während seine Onlinezeit weiterläuft.
13. **Given** ein Spieler, der von einer Zone in eine andere wechselt, **When** er wechselt,
    **Then** endet der Abschnitt der ersten Zone und beginnt der der zweiten, und die Summe
    beider bleibt seine aktive Zeit.
14. **Given** ein beschworener Klon, der den härtesten Treffer der Sitzung landet, **When** der
    Treffer fällt, **Then** steht er als höchster Schaden beim beschwörenden Spieler.

---

### User Story 2 - Mein eigenes Profil, mit allem, was nur mich angeht (Priority: P2)

Ein Spieler öffnet seine eigene Statistik und sieht seine Werte in vier Zeiträumen — heute,
diese Woche, diese Saison, insgesamt —, dazu seine Platzierung in jeder öffentlichen Rangliste.
Und er sieht die drei Werte, die nur ihn etwas angehen: woran er gestorben ist, wie lange er
tatsächlich online war, und wo er seine Zeit verbracht hat.

**Why this priority**: Die erste Ansicht, die einem Wert einen Sinn gibt. Sie ist auch die
einzige Stelle, an der die private Metrik überhaupt sichtbar wird — ohne sie ist die
Aufschlüsselung nach Verursacher gezählt, aber für niemanden lesbar.

**Independent Test**: Mit einem Spieler Werte erzeugen, das eigene Fenster öffnen, die vier
Zeiträume durchschalten und gegen die gespeicherten Werte prüfen.

**Acceptance Scenarios**:

1. **Given** ein Spieler mit Werten aus mehreren Tagen, **When** er seine Statistik öffnet,
   **Then** zeigt jeder Zeitraum seine eigene Summe, und der Allzeit-Wert ist mindestens so groß
   wie jeder andere.
2. **Given** ein Spieler, der heute nichts getan hat, **When** er den Tageszeitraum wählt,
   **Then** sieht er Nullen und keine Fehlermeldung.
3. **Given** ein Spieler, der mehrfach an derselben Art gestorben ist, **When** er seine
   Statistik öffnet, **Then** sieht er diese Art mit ihrer Anzahl.
4. **Given** ein Spieler, der in mehreren Zonen war, **When** er seine Statistik öffnet,
   **Then** sieht er seine Zeit je Zone und seine gesamte Onlinezeit neben der aktiven.
5. **Given** ein Spieler außerhalb der ersten zehn Plätze, **When** er seine Statistik öffnet,
   **Then** sieht er trotzdem seinen eigenen Rang.
6. **Given** ein geöffnetes Statistikfenster, **When** der Spieler darin klickt, **Then** nimmt
   er nichts heraus und legt nichts hinein.

---

### User Story 3 - Die Rangliste, und sie kostet die Datenbank nichts (Priority: P2)

Ein Spieler öffnet die Rangliste, wählt eine Metrik und einen Zeitraum und sieht die ersten
Plätze samt seiner eigenen Position. Ob einer sie öffnet oder fünfzig gleichzeitig, macht für
die Datenbank keinen Unterschied: die Werte stehen bereits im Speicher, und wie alt sie sind,
sagt die Ansicht.

**Why this priority**: Der eigentliche Zweck des Blocks. Getrennt von US2, weil eine Rangliste
andere Fragen stellt als ein Profil — Sortierung, Gleichstand, Auffrischung, Sichtbarkeit —
und für sich prüfbar ist.

**Independent Test**: Werte für mehrere Spieler erzeugen, eine Auffrischung auslösen, die
Rangliste öffnen und die Reihenfolge prüfen; dann fünfzig Öffnungen auslösen und die
Datenbankabfragen dabei mitzählen.

**Acceptance Scenarios**:

1. **Given** eine aufgefrischte Rangliste, **When** fünfzig Spieler sie gleichzeitig öffnen,
   **Then** entsteht dabei keine einzige Datenbankabfrage.
2. **Given** frisch erzeugte Werte, **When** die Auffrischung noch nicht gelaufen ist, **Then**
   zeigt die Rangliste den älteren Stand samt seinem Alter — und fragt nicht nach.
3. **Given** ein Server, auf dem noch nie eine Auffrischung lief, **When** ein Spieler die
   Rangliste öffnet, **Then** sieht er eine Meldung statt einer leeren Liste.
4. **Given** zwei Spieler mit demselben Wert, **When** die Rangliste erscheint, **Then** tragen
   beide denselben Rang, und die Reihenfolge ist bei jeder Anzeige dieselbe.
5. **Given** ein anonymisiertes Konto mit hohen Werten, **When** die Rangliste erscheint,
   **Then** steht es nicht darin.

---

### User Story 4 - Ein fremdes Profil, aber nicht alles davon (Priority: P2)

Ein Spieler sieht sich die Statistik eines anderen an. Er sieht dessen Level, Coins, Kills,
Bosskills, **aktive** Spielzeit, höchsten Schaden und die **Gesamtzahl** seiner Tode. Er sieht
**nichts** von den drei privaten Werten: nicht, woran der andere gestorben ist, nicht dessen
Onlinezeit und nicht, wo er seine Zeit verbracht hat.

**Why this priority**: Die einzige Sichtbarkeitsentscheidung dieses Blocks, und die einzige
Regel, die man beim Bauen versehentlich verlieren kann. Als eigene Story ist sie eine eigene
Prüfung.

**Independent Test**: Zwei Spieler, einer stirbt mehrfach an einer bestimmten Art, der andere
öffnet dessen Profil und findet die Aufschlüsselung nicht — auf keinem Weg.

**Acceptance Scenarios**:

1. **Given** ein Spieler mit Toden an mehreren Arten, **When** ein anderer sein Profil öffnet,
   **Then** sieht dieser die Gesamtzahl der Tode und keine Verteilung.
2. **Given** dasselbe Profil, **When** der Spieler es selbst öffnet, **Then** sieht er die
   Verteilung.
3. **Given** ein Spieler mit viel Leerlauf, **When** ein anderer sein Profil öffnet, **Then**
   sieht dieser nur die aktive Zeit — die Onlinezeit und die Aufteilung nach Zonen bleiben
   verborgen.
4. **Given** ein Spielername, den es nie gab, **When** ein Spieler dessen Profil anfordert,
   **Then** bekommt er eine Meldung und kein leeres Fenster.

---

### User Story 5 - Die Saison endet, und sie belohnt — auch wen, der nicht da war (Priority: P3)

Eine Saison läuft ab. Über alle Metriken hinweg ergibt sich **eine** Punktzahl je Spieler, und
diese Gesamtwertung entscheidet, wer belohnt wird — nicht die zweiundzwanzig Einzelranglisten. Ihr
Endstand wird festgehalten und ändert sich danach nicht mehr, auch wenn die Rohdaten
weiterlaufen. Wer auf einem belohnten Platz steht, bekommt einen Anspruch; den löst er ein, wenn
er das nächste Mal spielt. Genau einmal.

**Why this priority**: Wertvoll, aber nicht Voraussetzung für irgendetwas anderes. Der Block ist
ohne diese Story vollständig benutzbar — mit ihr wird er wiederkehrend.

**Independent Test**: Eine Saison mit vergangenem Enddatum konfigurieren, den Abschluss auslösen,
den Endstand prüfen, den Anspruch einlösen und ein zweites Einlösen versuchen.

**Acceptance Scenarios**:

1. **Given** eine Saison, deren Enddatum überschritten ist, **When** der Abschluss läuft,
   **Then** steht der Endstand der Gesamtwertung fest und ändert sich durch spätere
   Auffrischungen nicht mehr.
2. **Given** eine abgeschlossene Saison, **When** jemand danach die Gewichte in der
   Konfiguration ändert, **Then** bleibt ihre Platzierung unverändert.
3. **Given** eine laufende Saison, **When** ein Spieler die Gesamtwertung öffnet, **Then** sieht
   er seinen Zwischenstand und aus welchen Metriken er sich zusammensetzt.
4. **Given** ein belohnter Platz und ein Spieler, der beim Abschluss offline war, **When** er das
   nächste Mal spielt, **Then** kann er seine Belohnung einlösen.
5. **Given** eine eingelöste Belohnung, **When** derselbe Spieler es erneut versucht, **Then**
   wird es abgelehnt und nichts gutgeschrieben.
6. **Given** ein Absturz zwischen Gutschrift und Vermerk, **When** der Server zurückkommt,
   **Then** ist die Belohnung genau einmal vorhanden — nicht zweimal und nicht keinmal.
7. **Given** ein volles Inventar und eine Belohnung, die ein Item enthält, **When** der Spieler
   einlöst, **Then** wird mit Begründung abgelehnt und der Anspruch bleibt bestehen.
8. **Given** ein Server, der über das Saisonende hinweg aus war, **When** er startet, **Then**
   wird der Abschluss nachgeholt, genau einmal.
9. **Given** ein Saisonwechsel, **When** die neue Saison beginnt, **Then** ist kein einziger
   Rohdatensatz gelöscht.

---

### User Story 6 - Das Hologramm im Hub (Priority: P3)

Im Hub steht eine dauerhafte Anzeige mit den ersten Plätzen einer konfigurierten Rangliste.
Sie liest denselben Speicherstand wie die Fenster, fragt selbst nichts ab, überlebt Neustarts,
ohne sich zu verdoppeln, und ist für das Mob-Budget aus B10 nicht vorhanden.

**Why this priority**: Sichtbarkeit ohne Eingabe — schön, aber nichts hängt daran. Zuletzt, weil
sie die Rangliste voraussetzt, die US3 baut.

**Independent Test**: Server starten, Anzeige prüfen, zweimal neu starten, zählen, wie viele
Anzeigen dastehen, und prüfen, dass das Mob-Budget sie nicht mitzählt.

**Acceptance Scenarios**:

1. **Given** ein konfiguriertes Hologramm, **When** der Server startet, **Then** steht genau eine
   Anzeige an der konfigurierten Stelle.
2. **Given** zwei weitere Neustarts, **When** der Server oben ist, **Then** steht dort immer noch
   genau eine.
3. **Given** eine Auffrischung der Rangliste, **When** sie durch ist, **Then** zeigt das Hologramm
   den neuen Stand, ohne selbst abgefragt zu haben.
4. **Given** ein Spieler, der auf die Anzeige einschlägt, **When** er es tut, **Then** passiert
   nichts: sie nimmt keinen Schaden, bewegt sich nicht und verschwindet nicht.
5. **Given** ein volles Mob-Budget der Region, **When** das Budget zählt, **Then** ist die Anzeige
   nicht darunter.

---

### Edge Cases

- **Ein Spielabend über Mitternacht.** Eine Sitzung, die um 23:40 beginnt und um 00:30 endet,
  darf nicht fünfzig Minuten Spielzeit auf den zweiten Tag legen. Die Zeit wird an der Tagesgrenze
  geteilt.
- **Ein Absturz mitten in der Sitzung.** Was seit dem letzten Autosave gespielt wurde, ist
  verloren — mehr nicht. Die Zusage aus Prinzip IV („kein Datenverlust über das Autosave-Intervall
  hinaus") gilt auch hier.
- **Eine Mob-Art, die es nicht mehr gibt.** Die Konfiguration streicht eine Art; die gezählten
  Kills bleiben. Der Wert wird angezeigt, auch wenn kein Anzeigename mehr auflösbar ist.
- **Eine Art, die zum Boss wird oder aufhört, einer zu sein.** Bosskills sind keine eigene
  Zählung, sondern die Summe über Arten mit Boss-Kennzeichen. Ändert sich das Kennzeichen, ändert
  sich die Vergangenheit — dieselbe Zusage, die B11 für Balancing gibt, und derselbe Preis.
- **Ein Verursacher, der beim Tod schon weg ist.** Die Art steht am Vermerk der Entität. Ist die
  Entität fort, bevor nachgesehen wird, ist die Art verloren. Nachgesehen wird deshalb im Tick,
  im selben Moment.
- **Ein Tod durch einen anderen Spieler.** Kein Mob-Schlüssel. Es braucht einen festgelegten
  eigenen Schlüssel, sonst landet ein Tod unter einer Art, die ihn nicht verursacht hat.
- **Höchster Schaden mit Nachkommastellen.** Der gespeicherte Wert ist ganzzahlig. Ein Schaden
  von 1249,7 darf nicht als 1250 abgelegt werden und damit einen echten Treffer über 1250
  einholen.
- **Zwei Kills in derselben Millisekunde.** Der Zähler im Speicher wird aus dem Tick bedient und
  aus dem Flush geleert; keine der beiden Seiten darf einen Wert verlieren.
- **Ein Spieler löscht sein Konto mitten in der Saison.** Seine Zahlen bleiben gezählt, sein Name
  ist fort. Er steht in keiner Rangliste und bekommt keinen Anspruch.
- **Eine Saison ohne einen einzigen Teilnehmer.** Der Abschluss läuft, der Endstand ist leer,
  es entstehen keine Ansprüche, und der Start der nächsten Saison scheitert daran nicht.
- **Eine Rangliste, deren Metrik in der Konfiguration nicht existiert.** Der Start weist das
  zurück, statt beim ersten Öffnen zu scheitern.
- **Der Hub existiert nicht.** Ist die konfigurierte Stelle nicht ladbar, entfällt die Anzeige
  mit einer Warnung — der Server startet trotzdem.
- **Untätigkeit innerhalb einer Zone.** Die Pause muss beide Uhren gleich behandeln: hält die
  aktive Gesamtzeit an, hält auch die Zonenzeit an. Täte sie es nicht, wäre die Summe der
  Zonenwerte größer als die aktive Gesamtzeit, und FR-014d wäre verletzt.
- **Ein Zonenwechsel während der Untätigkeit.** Der Abschnitt wechselt die Zone, aber keine der
  beiden Zonen bekommt aktive Zeit gutgeschrieben.
- **Ein Party-Mitglied, das keinen einzigen Treffer gelandet hat.** Es bekommt den Kill
  trotzdem, solange es in Reichweite ist. Das ist die Zusage aus FR-007a, kein Fehler — und der
  einzige Grund, warum die Metrik Beteiligung heißt und nicht Erledigung.
- **Ein Spieler verlässt die Party in dem Moment, in dem die Kreatur stirbt.** Maßgeblich ist die
  Zusammensetzung zum Zeitpunkt des Todes, dieselbe, die auch über Erfahrung und Coins
  entscheidet. Zwei verschiedene Zeitpunkte für dasselbe Ereignis gäbe es sonst.
- **Ein Spieler, der eine Stunde lang untätig in einer Zone steht.** Seine Onlinezeit wächst um
  eine Stunde, seine aktive Zeit um die konfigurierte Schwelle, seine Zonenzeit um denselben
  Betrag. In der öffentlichen Rangliste bewegt er sich fast nicht.
- **Ein Spieler, der genau auf der Schwelle liegt.** Die Grenze muss in eine Richtung
  entschieden sein und darf nicht davon abhängen, wie gerundet wurde. Erreichen zählt.
- **Ein Boss, an dem dreißig Spieler beteiligt waren.** Der Bosskill wird dreißigmal gezählt,
  wenn alle über der Schwelle lagen. Das ist die Zusage aus FR-007, nicht ein Zählfehler — und
  der Grund, warum die Bosskill-Rangliste Teilnahme misst und nicht Seltenheit.
- **Ein Spieler, der nur zusieht.** Ohne Schaden und ohne Party bekommt er nichts. Zusehen ist
  keine Beteiligung.
- **Eine Gesamtwertung, in der ein Spieler in jeder Metrik Zweiter ist.** Sie kann ihn vor einen
  Spieler setzen, der eine Metrik anführt und in den anderen fehlt. Das ist beabsichtigt: die
  Wertung sucht den vielseitigsten Spieler, nicht den besten Spezialisten.
- **Eine Gewichtung, die eine Metrik hundertfach überbewertet.** Die Wertung wird dadurch nicht
  falsch, nur einseitig. Der Start prüft die Gewichte auf Gültigkeit, nicht auf Geschmack — das
  Balancing ist eine Aufgabe des Betreibers, keine des Codes.
- **Eine Saison, deren Gewichte während des Laufs geändert werden.** Der Zwischenstand rechnet ab
  sofort neu; der Endstand einer bereits abgeschlossenen Saison bleibt unberührt.

---

## Requirements *(mandatory)*

### Erfassung im Spielpfad

- **FR-001**: Die Erfassung einer Metrik MUSS im Spielpfad ein reiner Zählerinkrement im Speicher
  sein. Kein Datenbankzugriff, keine Abfrage, keine Berechnung eines Endwertes im Tick.
- **FR-002**: Die Erfassung MUSS den vorhandenen Weg aus B02 benutzen (`StatisticsRepository`) und
  DARF **keinen zweiten Speicherort** für dieselben Zahlen anlegen.
- **FR-003**: Es DARF **keine neue wiederkehrende Aufgabe je Spieler oder je Entität** entstehen
  (Prinzip II). Zeitbasierte Werte werden aus Zeitstempeln abgeleitet.
- **FR-004**: Ein Fehler in der Erfassung DARF das auslösende Spielereignis nicht scheitern lassen
  (Prinzip VI). Ein nicht gezählter Kill ist hinnehmbar; ein verlorener Kill ist es nicht.
- **FR-005**: Zähler MÜSSEN am **Konto** hängen (`player_id`), nicht am Charakter. Ein Spieler mit
  mehreren Charakteren hat **eine** Zahl je Zählermetrik.

### Was gezählt wird

- **FR-006**: Mob-Kills MÜSSEN **je Mob-Art** gezählt werden, unter einem Schlüssel, der die Art
  trägt.
- **FR-007**: Ein Kill MUSS **jedem** Spieler gezählt werden, dessen Schadensanteil an der
  getöteten Kreatur eine konfigurierte **Schwelle** erreicht (Vorgabe 5 %). Weder der letzte
  Treffer noch der größte Anteil entscheidet allein (ADR-042).
- **FR-007a**: Innerhalb einer Party MUSS der Kill zusätzlich **jedem Mitglied in Reichweite**
  gezählt werden, **auch bei einem Anteil von null** — dieselbe Zusage, unter der B06 Erfahrung
  und Coins verteilt. Die Partyregel ist damit die Erweiterung der Schwelle, nicht ihr Ersatz. Die Beute rotiert dabei weiterhin (B11); der Kill rotiert nicht.
- **FR-007b**: Die Reichweitenprüfung MUSS dieselbe sein, die B06 für Erfahrung und Coins
  benutzt. Zwei verschiedene Reichweiten für dasselbe Ereignis wären ein Widerspruch, den kein
  Spieler auflösen kann.
- **FR-007c**: Als Folge von FR-007 und FR-007a ist die Summe aller Kill-Zähler **größer** als die
  Zahl der getöteten Kreaturen. Jede Anzeige MUSS die Metrik als **Beteiligung an einem Kill**
  benennen und DARF sie nicht als Zahl eigenhändig erledigter Kreaturen ausgeben.
- **FR-007d**: Die Schwelle MUSS konfigurierbar sein und beim Start geprüft werden. Eine Schwelle
  von null würde jedem einzelnen Treffer einen Kill gutschreiben und die Metrik wertlos machen;
  eine Schwelle über 100 % würde nie greifen. Beides MUSS der Start zurückweisen.
- **FR-007e**: Bosse MÜSSEN derselben Regel folgen wie jede andere Art. Eine Sonderregel für
  Bosse ist ausdrücklich **nicht** vorgesehen — der Boss war der Anlass für die Schwelle, nicht
  ihre Ausnahme.
- **FR-008**: Stirbt eine Kreatur ohne Beitrag eines Spielers, DARF kein Kill gezählt werden.
- **FR-009**: Bosskills DÜRFEN **nicht** als eigener Zähler geschrieben werden. Sie MÜSSEN aus den
  Kills der Arten mit Boss-Kennzeichen abgeleitet werden. Ein zweiter Zähler wäre eine zweite
  Wahrheit über dasselbe Ereignis.
- **FR-009a**: Die Rangliste **Mob-Kills** MUSS die Arten mit Boss-Kennzeichen **ausschließen**;
  die Rangliste **Bosskills** ist genau deren Gegenmenge. Beide zusammen ergeben alle Kills, und
  keine Kreatur zählt in beiden. Ohne diese Trennung wäre ein Bosskill in jeder Auswertung, die
  beide Größen benutzt, doppelt enthalten — in der Gesamtwertung mit doppeltem Gewicht.
- **FR-010**: Tode eines Spielers MÜSSEN doppelt erfasst werden: als Gesamtzahl und **je
  Verursacher-Art**.
- **FR-011**: Ein Tod ohne Mob-Verursacher MUSS unter einem festgelegten Schlüssel landen —
  getrennt nach Umgebung und nach Tod durch einen anderen Spieler. Kein solcher Tod DARF unter
  einer Mob-Art erscheinen.
- **FR-012**: Die Auflösung eines Verursachers zu seiner Art MUSS im Tick geschehen, solange die
  Entität existiert. Ein asynchrones Nachschlagen ist unzulässig — die Art steht am Vermerk der
  Entität, und die Entität ist danach fort.
- **FR-013**: Gesamtwerte über eine Familie dimensionierter Schlüssel (alle Kills, alle Tode,
  alle Bosskills, die aktive Zeit über alle Zonen) MÜSSEN durch Aggregation entstehen, nicht durch
  einen zusätzlichen Schreibvorgang.
- **FR-014**: Spielzeit MUSS in Sekunden erfasst werden und aus Zeitstempeln entstehen, nicht aus
  einem laufenden Zähler.
- **FR-014a**: Es MUSS **zwei** Zeitwerte geben: die **aktive Zeit** und die **gesamte
  Onlinezeit**. Die aktive Zeit ist immer kleiner oder gleich der Onlinezeit.
- **FR-014b**: Die aktive Zeit MUSS anhalten, sobald eine konfigurierte Dauer ohne Aktivität des
  Spielers vergangen ist, und mit der nächsten Aktivität weiterlaufen. Die Onlinezeit läuft
  unterdessen weiter.
- **FR-014c**: Die Untätigkeit MUSS über einen Zeitstempel der letzten Aktivität erkannt werden.
  Eine wiederkehrende Aufgabe je Spieler ist auch dafür unzulässig (FR-003).
- **FR-014c1**: Als Aktivität MUSS **alles gelten, was der Spieler auslöst**: Bewegung, Kampf,
  Interaktion mit Blöcken und Kreaturen, das Öffnen und Bedienen von Menüs sowie Commands. Die
  Liste ist bewusst weit — sie soll erkennen, ob jemand am Rechner sitzt, und nicht bewerten,
  womit er seine Zeit verbringt.
- **FR-014c2**: Das Erneuern des Zeitstempels DARF im Tick nichts kosten außer dem Setzen eines
  Wertes. Insbesondere DARF daraus **kein** Schreibvorgang und keine Neuberechnung folgen —
  Bewegung feuert im Sekundentakt.
- **FR-014d**: Die aktive Zeit MUSS zusätzlich **je Zone** aufgeschlüsselt werden. Die Summe
  über alle Zonen eines Tages MUSS der aktiven Gesamtzeit dieses Tages entsprechen — es ist
  dieselbe Uhr, nur anders aufgeteilt.
- **FR-014e**: Aufenthalt außerhalb jeder Zone MUSS unter einem festgelegten Schlüssel landen.
- **FR-014f**: Ein Zonenwechsel MUSS den laufenden Abschnitt schließen und einen neuen öffnen,
  ausgelöst durch das vorhandene Zonenwechsel-Ereignis aus B09. Eigenes periodisches Nachsehen,
  in welcher Zone ein Spieler steht, ist unzulässig.
- **FR-015**: Beide Zeitwerte und die Zonenaufteilung MÜSSEN an den vorhandenen Autosave-Punkten
  und beim Sitzungsende festgeschrieben werden. Ein Abschnitt über die Tagesgrenze MUSS an ihr
  geteilt werden.
- **FR-016**: Der höchste Schaden MUSS als **Maximum** erfasst werden: der größte Einzelwert eines
  Schadensereignisses, wie B05 es meldet — die Fenstersumme **nach** Abwehr. Er MUSS ganzzahlig
  abgelegt werden, und zwar **abgerundet**, damit ein aufgerundeter Wert keinen echten Treffer
  derselben Höhe einholt.
- **FR-016a**: Schaden eines **beschworenen Klons** (B08 `SummonEffect`) MUSS dem beschwörenden
  Spieler zugerechnet werden. Das weicht bewusst von B11 ab, wo derselbe Klon **keinen**
  Verschleiß verursacht (FR-041a): der Klon trägt keine eigene Ausrüstung, die sich abnutzen
  könnte, aber sein Schaden entsteht aus der Fähigkeit des Spielers. Was er kostet, kostet ihn;
  was er leistet, leistet der Spieler (ADR-047).
- **FR-017**: Für Maximum-Metriken MUSS ein zweiter Schreibweg auf derselben Tabelle entstehen
  (Maximum statt Summe, ADR-040). Das additive Verhalten der bestehenden Metriken DARF sich dadurch **nicht**
  ändern, und auch der neue Weg DARF vor dem Schreiben **nicht lesen**.
- **FR-018**: Jede Metrik MUSS in einem Verzeichnis geführt werden, das ihre Art (Summe, Maximum,
  Zustand), ihre Sichtbarkeit und ihre Dimensionierung trägt. Ein Metrikschlüssel DARF nicht als
  Literal über den Code verstreut sein.

### Zustandswerte statt Zähler

- **FR-019**: Level, XP und Coins DÜRFEN **nicht** in die Tagestabelle geschrieben werden. Sie sind
  Zustände; die Summe von Tageszuständen ist bedeutungslos (ADR-041).
- **FR-020**: Die Level-Rangliste MUSS aus dem Progressionsbestand gelesen werden und nach Level,
  bei Gleichstand nach XP innerhalb des Levels ordnen.
- **FR-021**: Das Level eines **Kontos** MUSS der höchste Wert seiner Charaktere sein, angezeigt
  mit dessen Klasse.
- **FR-022**: Die Coin-Rangliste MUSS die Kontostände aller Charaktere eines Kontos summieren.
- **FR-023**: Zustandswerte MÜSSEN ohne Tages-, Wochen- und Saisonform auskommen; sie erscheinen
  ausschließlich als aktueller Stand. Eine Ansicht DARF für sie keinen Zeitraum anbieten, den es
  nicht gibt.

### Zeiträume

- **FR-024**: Es MUSS vier Zeiträume geben: Tag, Woche, Saison, Allzeit.
- **FR-025**: Alle vier MÜSSEN aus dem vorhandenen Tagesfeld entstehen. Kein Zeitraum DARF eigenen
  Speicher bekommen.
- **FR-026**: Tages- und Wochengrenzen MÜSSEN derselben Zeitzone folgen wie die gespeicherte
  Tagesangabe (UTC). Die Woche MUSS die ISO-Woche sein, beginnend am Montag.
- **FR-027**: Saisongrenzen MÜSSEN tagesgenau sein. Ein Tag MUSS zu genau einer Saison gehören.

### Aggregation und Ranglisten

- **FR-028**: Die Aggregation MUSS asynchron und periodisch laufen, niemals bei jedem Ereignis.
- **FR-029**: Ranglisten MÜSSEN über Materialized Views mit periodischem Refresh bereitgestellt und
  zusätzlich im Speicher gehalten werden.
- **FR-030**: Das Öffnen einer Ansicht DARF **keine** Datenbankabfrage auslösen.
- **FR-031**: Der Refresh DARF den Tick nicht blockieren und DARF laufende Abfragen der Ansicht
  nicht sperren.
- **FR-032**: Das Refresh-Intervall MUSS konfigurierbar sein. Jede Ansicht MUSS das **Alter** der
  gezeigten Werte nennen.
- **FR-032a**: Es MUSS für **jede** öffentliche Zählermetrik in **jedem** der vier Zeiträume eine
  Rangliste geben, dazu die beiden Zustandsranglisten und die Saison-Gesamtwertung. Eine
  kuratierte Auswahl, die jemand pflegen müsste, ist ausdrücklich **nicht** vorgesehen: eine neue
  Metrik erscheint damit von allein auf dem Brett und fällt nicht durch, weil niemand eine Liste
  ergänzt hat (dieselbe Begründung wie ADR-044).
- **FR-033**: Eine Rangliste MUSS die ersten N Plätze zeigen (konfigurierbar) und zusätzlich die
  eigene Platzierung des Betrachters, auch wenn sie außerhalb der ersten N liegt.
- **FR-034**: Bei Gleichstand MÜSSEN Spieler denselben Rang tragen, und die Reihenfolge MUSS bei
  wiederholter Anzeige dieselbe sein.
- **FR-035**: Solange noch kein Refresh gelaufen ist, MUSS die Ansicht das mitteilen. Sie DARF
  weder eine leere Liste zeigen noch ersatzweise die Datenbank fragen.

### Sichtbarkeit

- **FR-036**: Alle erfassten Metriken MÜSSEN öffentlich rankbar sein — **außer** diesen dreien:
  der Aufschlüsselung der Tode nach Verursacher, der gesamten Onlinezeit und der Aufteilung der
  Spielzeit nach Zonen (ADR-043).
- **FR-037**: Ein fremdes Profil MUSS die **Gesamtzahl** der Tode und die **aktive**
  Gesamtspielzeit zeigen. Es DARF keinen der drei privaten Werte zeigen — über keinen Command,
  kein Menü, keine Rangliste und kein Hologramm.
- **FR-038**: Alle drei privaten Werte MÜSSEN im **eigenen** Profil sichtbar sein.
- **FR-038a**: Die öffentliche Spielzeit-Rangliste MUSS die **aktive** Zeit ranken. Die Onlinezeit
  DARF in keiner Rangliste erscheinen.
- **FR-039**: Ein anonymisiertes Konto DARF in **keiner** öffentlichen Rangliste erscheinen. Seine
  Zahlen bleiben gezählt; ohne auflösbaren Namen gibt es keinen Eintrag.
- **FR-040**: Die Auflösung eines Namens DARF den Tick nicht blockieren. Der Name MUSS Teil des im
  Speicher gehaltenen Ranglisteneintrags sein.

### Ansichten

- **FR-041**: Ein Command MUSS die eigene Statistik als Fenster öffnen, mit allen vier Zeiträumen.
- **FR-042**: Dasselbe Fenster MUSS je öffentlicher Metrik die eigene Platzierung zeigen.
- **FR-043**: Ein Command MUSS die Rangliste als Fenster öffnen, mit Umschaltung zwischen Metrik
  und Zeitraum im Fenster.
- **FR-044**: Ein Command MUSS das öffentliche Profil eines anderen Spielers öffnen. Ein
  unbekannter Name MUSS eine Meldung erzeugen, kein leeres Fenster.
- **FR-045**: Aus keinem dieser Fenster DARF ein Gegenstand entnommen oder hineingelegt werden
  können.
- **FR-046**: Die eigene Ansicht MUSS für jeden Spieler zugänglich sein; sie ist kein
  administrativer Akt (Muster: `rpg.currency.balance`).
- **FR-047**: Alle Texte MÜSSEN über Message-Schlüssel laufen (Prinzip V). Der Start MUSS eine
  Konfiguration zurückweisen, in der ein benötigter Schlüssel fehlt.

### Saison

- **FR-048**: Saisons MÜSSEN in versionierter Konfiguration liegen, mit Schlüssel, Start- und
  Enddatum.
- **FR-049**: Der Start MUSS überlappende oder lückenhafte Saisons zurückweisen (Fail-Fast).
- **FR-050**: Das Saisonende MUSS den Endstand der **Gesamtwertung** einfrieren und dauerhaft
  ablegen. Eine spätere Auffrischung DARF eine bereits vergebene Platzierung nicht mehr verändern.
  Eingefroren wird auch die **Gewichtung**, mit der gerechnet wurde: eine spätere Änderung der
  Gewichte DARF eine abgeschlossene Saison nicht rückwirkend umsortieren.
- **FR-050a**: Es MUSS eine **Gesamtwertung** der Saison geben: eine Punktzahl je Konto, gebildet
  aus mehreren Metriken. Sie ist die einzige Wertung, an der eine Belohnung hängt.
- **FR-050b**: Die Punktzahl MUSS aus **gewichteten** Metrikwerten des Saisonzeitraums entstehen:
  je Metrik ein konfigurierbarer Punktwert je Einheit, die Punktzahl ist deren Summe. Die
  Rechnung MUSS so einfach bleiben, dass ein Spieler sie im Fenster nachvollziehen kann.
- **FR-050c**: In die Punktzahl DÜRFEN **nur öffentliche Zählermetriken** des Saisonzeitraums
  eingehen. **Zustandswerte** — Level, XP, Coins — DÜRFEN **nicht** eingehen: sie tragen den
  Fortschritt vergangener Saisons in die laufende und würden alte Konten dauerhaft nach oben
  setzen. Private Werte DÜRFEN ebenfalls nicht eingehen, sonst wäre eine öffentliche Platzierung
  aus nicht einsehbaren Zahlen begründet.
- **FR-050d**: Die Gewichte MÜSSEN in der Konfiguration liegen. Der Start MUSS eine Konfiguration
  zurückweisen, die keine einzige Metrik gewichtet oder eine unbekannte Metrik nennt — eine
  Gesamtwertung ohne Beiträge wäre eine Rangliste aus Nullen.
- **FR-050e**: Die Gesamtwertung MUSS auch für die **laufende** Saison als Zwischenstand sichtbar
  sein. Eine Wertung, die man erst nach ihrem Ende sieht, kann niemanden zu etwas bewegen.
- **FR-050f**: Das Fenster MUSS zu einer Punktzahl aufschlüsseln, **woraus** sie entstanden ist —
  je beitragender Metrik der Wert, das Gewicht und die daraus folgenden Punkte.
- **FR-051**: Belohnungen MÜSSEN je **Platz der Gesamtwertung** konfigurierbar sein und MÜSSEN
  Coins, Items oder beides umfassen können. Die übrigen Ranglisten sind Ehre ohne Preis.
- **FR-052**: Eine Belohnung DARF beim Abschluss **nicht** ausgeschüttet werden, sondern MUSS als
  **Anspruch** entstehen — beim Saisonende ist der Empfänger in aller Regel nicht online
  (ADR-045).
- **FR-053**: Ein Anspruch MUSS **genau einmal** einlösbar sein, auch bei einem Absturz zwischen
  Gutschrift und Vermerk.
- **FR-053a**: Ein Anspruch DARF **nicht verfallen**. Wer nach einem Jahr zurückkehrt, findet ihn
  vor. Es gibt keine Frist, die ein Spieler versäumen könnte, ohne davon zu erfahren.
- **FR-054**: Eingelöst wird durch einen Charakter: Coins landen auf dessen Kontostand, Items in
  dessen Inventar. Der Anspruch selbst gehört dem **Konto**.
- **FR-055**: Fehlt der Platz im Inventar, MUSS die Einlösung mit Begründung abgelehnt werden, und
  der Anspruch MUSS bestehen bleiben (Muster B11).
- **FR-056**: Jede Einlösung MUSS protokolliert werden.
- **FR-057**: Ein Saisonwechsel DARF **keine** Rohdaten löschen (unbegrenzte Aufbewahrung, B02).
- **FR-058**: War der Server über das Saisonende hinweg aus, MUSS der Abschluss beim nächsten Start
  nachgeholt werden, genau einmal.

### Hologramm im Hub

- **FR-059**: Eine dauerhafte Anzeige im Hub MUSS die ersten N Plätze einer konfigurierten
  Rangliste zeigen.
- **FR-060**: Sie MUSS denselben Speicherstand lesen wie die Fenster und DARF **keine eigene**
  Abfrage auslösen.
- **FR-061**: Vor dem Setzen MUSS eine vorhandene Anzeige entfernt werden. Ein Neustart DARF keine
  zweite erzeugen (Muster: Händler-NPC aus B11).
- **FR-062**: Sie DARF nicht gegen das Mob-Budget aus B10 zählen.
- **FR-063**: Sie MUSS unverwundbar sein, sich nicht schieben lassen, kein Ziel für Aggro sein und
  nicht durch Entfernung verschwinden.
- **FR-064**: Position und angezeigte Rangliste MÜSSEN konfigurierbar sein. Eine ungültige
  Konfiguration MUSS der Start zurückweisen; eine nicht ladbare Stelle MUSS die Anzeige entfallen
  lassen, ohne den Start zu verhindern.

### Konfiguration

- **FR-065**: Alle Zahlen dieses Blocks MÜSSEN konfigurierbar sein (Prinzip V): Refresh-Intervall,
  Anzahl der Plätze, Saisonzeiträume, Belohnungen, Hologramm.
- **FR-066**: Die Konfiguration MUSS beim Start gegen ein Schema validiert werden; ein Fehler führt
  zu Fail-Fast mit klarer Meldung.
- **FR-067**: Eine **neue Rangliste über eine vorhandene Metrik** DARF keine Codeänderung
  erfordern. Eine neue Metrik braucht einen Erfassungspunkt und ist damit ausdrücklich eine
  Codeänderung.

### Abgrenzung

- **FR-068**: Dieser Block liefert **kein** HUD und **kein** Scoreboard. Dauerhafte Einblendungen
  sind B13.
- **FR-069**: Dieser Block liefert **keine** Werkzeuge zum Ändern oder Zurücksetzen von
  Statistikwerten. Administrative Eingriffe sind B14.
- **FR-070**: Dieser Block erbringt **keinen** Lasttestnachweis. Sein Leistungsziel wird als
  wiederholbare Messung ohne Volllast belegt (Prinzip VII); die Volllast gehört B15.

### Key Entities

- **Metrik**: Ein benannter Kennwert. Trägt seine Art (Summe, Maximum, Zustand), seine Sichtbarkeit
  (öffentlich, privat) und ob sie nach einer Dimension aufgeschlüsselt ist.
- **Metrikschlüssel**: Der abgelegte Bezeichner einer Metrik, bei dimensionierten Metriken
  einschließlich der Dimension — der Mob-Art oder der Zone.
- **Aktivitätszeitstempel**: Wann ein Spieler zuletzt etwas getan hat. Laufzeitwert, aus dem sich
  die Untätigkeit ergibt, ohne dass jemand periodisch nachsieht.
- **Zeitabschnitt**: Ein offener Aufenthalt in einer Zone, begrenzt durch Zonenwechsel,
  Tagesgrenze, Autosave oder Sitzungsende. Nie gespeichert, nur verrechnet.
- **Tageswert**: Ein Wert einer Metrik für ein Konto an einem Kalendertag. Die einzige gespeicherte
  Form eines Zählers.
- **Zeitraum**: Tag, Woche, Saison oder Allzeit — ein Ausschnitt über Tageswerte, kein eigener
  Bestand.
- **Rangliste**: Eine Metrik in einem Zeitraum, sortiert, mit Auffrischungszeitpunkt.
- **Ranglisteneintrag**: Rang, Konto, Anzeigename und Wert.
- **Saison**: Schlüssel, Start- und Enddatum, Belohnungstabelle.
- **Gesamtwertung**: Eine Punktzahl je Konto für einen Saisonzeitraum, gebildet aus gewichteten
  Zählermetriken. Die dreiundzwanzigste Rangliste und die einzige, an der eine Belohnung hängt.
- **Gewichtung**: Welche Metrik mit wie vielen Punkten je Einheit in die Gesamtwertung eingeht.
  Konfiguriert, beim Start geprüft, mit dem Saisonendstand eingefroren.
- **Saisonendstand**: Der eingefrorene Stand einer abgeschlossenen Saison samt der Gewichtung,
  mit der er entstanden ist. Ändert sich nie wieder.
- **Belohnungsanspruch**: Was ein Konto aus einer abgeschlossenen Saison zusteht, und ob es
  eingelöst wurde.
- **Hub-Anzeige**: Eine dauerhafte Darstellung einer Rangliste an einer festgelegten Stelle der Welt.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Die Erfassung eines Kills, eines Todes oder eines Schadensereignisses kostet im Tick
  keine messbare Zeit — belegt durch eine wiederholbare Messung der eigenen Rechenarbeit, ohne
  Volllast.
- **SC-002**: Tausend Kills eines Spielers an einem Tag erzeugen einen Schreibvorgang je Flush für
  diese Metrik, nicht tausend.
- **SC-003**: Fünfzig Spieler öffnen gleichzeitig eine Rangliste, und dabei entsteht keine einzige
  Datenbankabfrage.
- **SC-004**: Nach einem Serverneustart stimmen alle Werte mit dem Stand vor dem Neustart überein.
- **SC-005**: Kein angezeigter Wert ist älter als das konfigurierte Refresh-Intervall, und jede
  Ansicht nennt sein Alter.
- **SC-006**: Die Aufschlüsselung der Tode nach Verursacher erscheint in keiner Ansicht eines
  anderen Spielers — auf keinem Weg.
- **SC-007**: Ein anonymisiertes Konto erscheint in keiner öffentlichen Rangliste, während seine
  Zahlen in den Gesamtsummen erhalten bleiben.
- **SC-008**: Eine Saisonbelohnung wird genau einmal gutgeschrieben, auch wenn der Server zwischen
  Gutschrift und Vermerk abstürzt.
- **SC-009**: Ein Server, der über ein Saisonende hinweg aus war, schließt die Saison beim nächsten
  Start ab — genau einmal, mit demselben Endstand.
- **SC-010**: Drei aufeinanderfolgende Neustarts hinterlassen genau eine Hub-Anzeige.
- **SC-011**: Fällt der Statistikdienst aus, verändert das kein Spielereignis: Kills, Tode und
  Beute laufen unverändert weiter.
- **SC-012**: Eine zusätzliche Rangliste über eine bereits erfasste Metrik entsteht **von allein** —
  ohne eine Zeile Code **und ohne einen Eintrag in der Konfiguration** (FR-032a).
- **SC-013**: Eine Sitzung über Mitternacht verteilt ihre Spielzeit auf beide Tage, mit einer
  Abweichung von höchstens einem Autosave-Intervall.
- **SC-014**: Eine Party aus drei Spielern, von denen zwei in Reichweite sind, erzeugt bei einem
  Kill genau zwei erhöhte Zähler — und dieselbe Reichweite entscheidet wie bei Erfahrung und
  Coins.
- **SC-015**: Nach der konfigurierten Dauer ohne Aktivität wächst die aktive Zeit nicht weiter,
  während die Onlinezeit weiterläuft.
- **SC-016**: Die Summe aller Zonenzeiten eines Tages entspricht der aktiven Gesamtzeit dieses
  Tages, auf die Sekunde.
- **SC-017**: Keiner der drei privaten Werte erscheint in der Ansicht eines anderen Spielers,
  in einer Rangliste oder auf dem Hologramm.
- **SC-018**: Ein Anspruch aus einer abgeschlossenen Saison ist auch ein Jahr später noch
  einlösbar.
- **SC-019**: Von zehn Spielern ohne Party, die einen Boss legen, bekommen genau diejenigen den
  Bosskill gezählt, die die Schwelle erreicht haben — und ein Spieler mit einem einzigen Treffer
  darunter bekommt ihn nicht.
- **SC-020**: Die Punktzahl der Gesamtwertung lässt sich aus den im Fenster gezeigten Werten und
  Gewichten nachrechnen und stimmt mit der angezeigten überein.
- **SC-021**: Eine Änderung der Gewichte verändert den Endstand einer abgeschlossenen Saison
  nicht.
- **SC-022**: Ein Start mit einer Gewichtung, die keine einzige bekannte Metrik nennt, schlägt
  fehl — mit einer Meldung, die sagt, welche Metrik fehlt.
- **SC-023**: Eine neu erfasste Zählermetrik erscheint ohne Konfigurationsänderung in allen vier
  Zeiträumen als Rangliste.

---

## Assumptions

- **Refresh-Intervall**: Standard fünf Minuten, konfigurierbar. Der Wert war im Blockdokument offen;
  fünf Minuten sind kurz genug, dass eine Rangliste lebendig wirkt, und lang genug, dass der Refresh
  im Betrieb nicht auffällt.
- **Saisonlänge**: Standard drei Monate (Quartal), über Start- und Enddatum konfigurierbar. Die
  Länge war offen; ein Quartal passt zur Belohnungsentscheidung, ohne eine eigene Terminplanung zu
  verlangen.
- **Anzahl der Plätze**: Standard zehn, konfigurierbar.
- **Zeitzone**: Alles rechnet in UTC, weil die gespeicherte Tagesangabe es bereits tut. Eine zweite
  Zeitzone würde Tages- und Wochengrenzen von den Daten trennen.
- **Ebene der Ranglisten**: Ranglisten vergleichen **Konten**, nicht Charaktere. Zustandswerte
  werden dafür über die Charaktere eines Kontos verdichtet (Level: der höchste; Coins: die Summe).
- **Belohnungsempfänger**: Der Anspruch gehört dem Konto und wird von dem Charakter eingelöst, mit
  dem der Spieler ihn abholt.
- **Anzeigename**: Der Minecraft-Name des Kontos. Charakternamen erscheinen nur dort, wo ein
  Zustandswert von einem bestimmten Charakter stammt.
- **Dimension der Mob-Metriken**: die Art aus der Mob-Konfiguration von B10, nicht der Vanilla-Typ.
  Zwei Arten auf derselben Vanilla-Basis sind zwei verschiedene Einträge — dieselbe Unterscheidung,
  die B11 für Beutetabellen trifft.
- **Dimension der Zeitmetrik**: die Zone aus B09. Aufenthalt außerhalb jeder Zone bekommt einen
  eigenen festen Schlüssel, damit die Summe der Zonenzeiten vollständig bleibt.
- **Beteiligungsschwelle für einen Kill**: Vorgabe 5 % des Schadens an der getöteten Kreatur,
  konfigurierbar. Niedrig genug, dass ein ernsthafter Beitrag zählt; hoch genug, dass ein
  einzelner Treffer aus dem Vorbeilaufen keinen Bosskill einbringt. Gemessen wird am
  Schadensanteil, den B05 ohnehin für die Verteilung von Erfahrung führt — keine zweite Rechnung.
- **Gewichte der Gesamtwertung**: Vorgabe ein Punkt je Mob-Kill, ein deutlich höherer Wert je
  Bosskill, ein moderater je Stunde aktiver Zeit, Tode mit Gewicht null. Alles konfigurierbar;
  die konkreten Zahlen sind Balancing und gehören dem Betreiber, nicht dieser Spec.
- **Schwelle für Untätigkeit**: Vorgabe fünf Minuten, konfigurierbar. Lang genug, dass ein Blick
  in die Truhe oder ein Gespräch nicht als Leerlauf zählt; kurz genug, dass eine Nacht mit
  laufendem Client keine Rangliste gewinnt.
- **Datenmenge**: Die Aufschlüsselung nach Mob-Art und Zone vervielfacht die Tageszeilen. B02
  rechnete mit rund 73 000 Zeilen im Jahr bei 200 Spielern; mit den Dimensionen werden daraus
  grob ein bis vier Millionen. Das ist bewusst hingenommen: Zeilen entstehen nur für Arten und
  Zonen, die ein Spieler tatsächlich berührt hat, und die Zusage der unbegrenzten Aufbewahrung
  aus B02 bleibt dadurch unangetastet. Verdichten hätte sie gebrochen (ADR-044).
- **Bestand des Fundaments**: `player_statistic_daily`, `StatisticsRepository` und die
  Write-Behind-Registrierung aus B02 werden benutzt und nicht ersetzt. Neu entstehen die
  Erfassungspunkte, der Maximum-Schreibweg, die Ranglisten-Sichten, der Saisonbestand und die
  Anzeigen.
- **Kein PvP-Balancing**: Tod durch einen anderen Spieler wird gezählt, aber nicht bewertet. Ob es
  PvP überhaupt gibt, entscheidet dieser Block nicht.
