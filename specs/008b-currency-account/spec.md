# Feature Specification: B08b · Währung & Konto

**Feature Branch**: `008b-currency-account`

**Created**: 2026-08-22

**Status**: Draft

**Input**: Blocksteckbrief `blocks/B08b-currency-account.md` in der Fassung vom 2026-08-22 — ein
Kontostand je Charakter, Buchungen darauf, und eine Kostenprüfung, die andere Blöcke aufrufen.
Eingeschoben durch **ADR-027**, der zugleich die Folgen benennt, die nachzuziehen sind, sobald der
Block steht: B07 löst den `cost`-Block auf, statt ihn undurchsichtig weiterzureichen, B08s
`advanceRank` bekommt eine Kostenprüfung davor, und B11 baut später Verkauf und Reparatur darauf auf.
Hängt ab von B02 (Schreib-Puffer, Migrationen, Aggregattypen, Audit-Log), B03 (`Character`,
`SessionBundle`) und B06 (der Verteilungspfad beim Tod einer Kreatur); mittelbar von B05, weil der Tod
über `CombatDeathEvent` kommt und der Schadensanteil von dort stammt. Wird benötigt von B07, B08, B11
und B12. Verbindlich: **ADR-027** (eigener Block, Schicht 1, Preise bleiben bei dem, der sie
verlangt), **ADR-011** (alles hängt am Charakter, nicht am Account), **ADR-015 Punkt 7** (ein neuer
Aggregattyp braucht drei Eintragungen), **ADR-015 Punkt 6** (ein Ort wird als Wert weitergegeben, nie
als Id eines toten Wesens), ADR-017 (Ausrüstungsstufen sind Klassenprogression, ihr `cost`-Block war
bislang undurchsichtig), Prinzip I (Scheduling nur über die projekteigene Abstraktion), Prinzip II
(kein Datenbankzugriff je Spielereignis, keine wiederkehrende Aufgabe je Entity), Prinzip III
(`rpg-core` ohne Bukkit), Prinzip V (alle Zahlen in validierter Konfiguration, keine hartcodierten
Spielertexte), Prinzip VI (der Server ist alleinige Autorität).

## Clarifications

### Session 2026-08-22 — vor `/specify`, festgehalten in ADR-027

- Q: Wohin gehört die Währung — in den Item-Block B11 oder in einen eigenen? → A: **Eigener Block,
  Schicht 1.** Läge sie in B11, müssten B07 und B08 auf B11 zeigen, also Schicht 1 auf Schicht 2. Ein
  Kontostand hat mit Items nichts zu tun; er hat mit dem Charakter zu tun, wie Level und Erfahrung.

- Q: Gehört ein zentraler Preiskatalog dazu? → A: **Nein.** Preise stehen bei dem, der sie verlangt:
  Stufenkosten in `classes.yml`, Rangkosten in `abilities.yml`, Reparatur in B11. Ein zentraler
  Katalog wäre ein zweiter Ort für Zahlen, die schon einen haben.

- Q: Ist NPC-Verkauf gegen Coins vom Nicht-Ziel „kein Wirtschaftssystem" verboten? → A: **Nein.** Das
  Nicht-Ziel meint Spieler-zu-Spieler-Handel und Crafting. Dieser Block bucht ohnehin nur; der
  Händler selbst gehört zu B11.

### Session 2026-08-22 — bei `/specify`

- Q: Startguthaben bei Charaktererstellung — null oder ein Betrag? → A: **Konfigurierbar, Standard
  null.** Die Zahl ist Balancing und gehört nach Prinzip V in die Konfiguration; der Standard trifft
  den heutigen Zustand, in dem die erste Ausrüstungsstufe ohne Coins erreichbar ist (`cost: {}` auf
  Stufe 1). Ein festes Null ohne Stellschraube hätte eine Balancing-Zahl im Code festgeschrieben.

- Q: Verliert ein Charakter beim Tod Coins? → A: **Nein.** Das zieht die Linie von ADR-017 gerade
  durch: der Tod kostet Haltbarkeit und Zeit, nicht Fortschritt. Der Block braucht damit keinen
  Todes-Hook für Strafen, keine Buchungsart „Todesstrafe" und keine Tests dafür. Nachrüsten ist später
  leichter als Zurücknehmen.

- Q: Wie kommen Coins aus einem Kill zum Spieler, und was ist mit der Historie? → A: **Zwei
  Entscheidungen, die den Zuschnitt spürbar ändern.**

  **Erstens: Coins fallen.** Eine getötete Kreatur schreibt nicht direkt gut, sondern **lässt Coins
  fallen**, die der Spieler aufheben muss. Erst das Aufheben bucht. Das macht den Erwerb im Spiel
  sichtbar und ist die vertraute Geste aus dem Genre.

  *Was daran teuer ist und deshalb in den Anforderungen steht:* das Erfolgskriterium des Projekts
  nennt 800 aktive Mobs bei 150 Spielern. Ein Wurfobjekt je Kill ist der mit Abstand teuerste Teil
  dieses Blocks — das Buchen selbst ist eine Addition. Deckelung, Zusammenlegen benachbarter Haufen
  und eine Verfallszeit sind deshalb als Anforderungen formuliert (FR-026 bis FR-030) und nicht als
  spätere Optimierung. Ohne sie wäre der Block der zweite TPS-kritische neben B10.

  *Was daran ebenfalls neu ist:* nicht abgeholte Coins sind weg. Der Kill garantiert nicht mehr die
  Gutschrift, sondern nur noch die Gelegenheit dazu.

  **Zweitens: eine dauerhafte Historie und Admin-Eingriffe.** Der Betreiber braucht einen Verlauf,
  der einen Neustart übersteht, und die Möglichkeit, einen Stand zu **setzen, zu erhöhen und zu
  senken**. Damit fällt die zuvor erwogene Variante „Grund an der Buchung plus Logeintrag" weg.

  *Die eine Stelle, an der der Block über seine Schicht hinausgreift:* Ein Admin-Eingriff braucht
  einen Aufrufweg, und Kommandos gehören B14 — Schicht 3, hängt von allen ab, und im Projekt existiert
  bislang **kein einziges** Kommando. Eine reine Schnittstelle ohne Aufrufweg wäre für den Betreiber
  unbenutzbar. Deshalb entsteht hier ein **vorläufiges** Kommando, das B14 später in sein
  Brigadier-Framework überführt — dieselbe Anordnung wie beim Kreatur-Ertrag, den B10 später
  übernimmt. Diese Ausnahme gehört als ADR festgehalten (siehe *Offene Punkte für `/plan`*).

### Session 2026-08-22 — bei `/clarify`

- Q: Bekommen Charaktere ohne jede Buchung rückwirkend das neue Startguthaben, wenn der
  konfigurierte Wert später steigt? → A: **Nein — das Startguthaben wird eine gewöhnliche Gutschrift
  bei der Charaktererstellung**, mit eigenem Grund und eigenem Verlaufseintrag. Ein Charakter ohne
  Kontozeile hat **0**, nicht „was gerade in der Konfiguration steht".

  **Warum das mehr als eine Feinheit ist:** Der Block sagt zu, dass jede Änderung ihren Grund nennt
  (FR-005). Ein Startguthaben, das beim Lesen aus der Konfiguration entsteht, wäre der einzige
  Betrag ohne Buchung — und eine spätere Änderung der Zahl hätte jeden noch unbebuchten Charakter
  über Nacht reicher gemacht, ohne Verlaufseintrag und ohne dass es jemandem auffällt. Genau die
  Fehlbuchung, die der Verlauf auffindbar machen soll.

  Ist der Wert **null**, wird nichts gebucht — dann kann auch nichts driften. Eine spätere Änderung
  trifft nur noch neu erstellte Charaktere.

- Q: Was passiert, wenn die Deckelung greift und kein Haufen mehr entstehen kann? → A: **Der
  weltweit älteste Haufen wird seinem Besitzer gutgeschrieben und abgeräumt**, damit der neue
  entstehen kann. Es geht dabei **keine Coin verloren**.

  **Warum weltweit und nicht „der eigene älteste":** Die Deckelung ist ein **globales** Budget an
  Objekten in der Welt. Wer selbst nichts liegen hat, käme sonst nicht an einen Platz heran, weil der
  Deckel mit fremden Haufen voll ist — und stünde wieder ohne Coins da.

  **Warum gutgeschrieben und nicht verworfen:** Ein verworfener Haufen träfe einen **Unbeteiligten** —
  jemanden, der nur zufällig etwas liegen hatte. Das wäre schlechter als jede andere Variante, weil
  das Opfer nicht einmal am auslösenden Kill beteiligt war.

  **Das ist kein Widerspruch zu FR-029**, und der Unterschied ist beabsichtigt: Ein Haufen, dessen
  **Frist abläuft**, wird niemandem gutgeschrieben — der Spieler hatte Zeit und hat sie verstreichen
  lassen. Ein Haufen, den **der Server abräumt**, wird gutgeschrieben — dafür kann der Spieler nichts.
  Eigene Versäumnisse kosten, Serverlast nicht.

  *Zur Grössenordnung:* Bei 400 Haufen und 120 s Frist greift die Regel erst ab dauerhaft rund
  3,3 neuen Haufen je Sekunde — Hordenlast, nicht Alltag.

- Q: Gibt es eine Mindestbeteiligung, unterhalb derer ein Beitragender keine Coins bekommt? → A:
  **Nein — rein anteilig, genau wie die Erfahrung.** 20 % Schaden ergeben 20 % der Coins.

  **Die Annahme, die XP-Regel kenne eine Schwelle, trifft nicht zu.** B06 teilt rein proportional
  (Szenario 2: A mit 60 % erhält 60 XP, B mit 40 % erhält 40 XP), und ein Party-Mitglied ohne jeden
  Schaden erhält in Reichweite trotzdem seinen Anteil (Szenario 5). Weder `progression.yml` noch die
  Anteilsbildung kennen einen Schwellenwert.

  Eine Schwelle **nur für Coins** wäre die erste Stelle, an der zwei Regeln denselben Kill
  unterschiedlich bewerten — und genau das sollte die geteilte Anteilsrechnung verhindern. Eine
  Schwelle **für beide** hätte B06 geändert, einen ausgelieferten Block.

- Q: Wer **sieht** einen Coin-Haufen? → A: **Nur der Berechtigte.** Wer am Kill nicht beteiligt war,
  sieht überhaupt keine Coins liegen — nicht einen Haufen, den er nicht aufheben kann.

  **Warum das mehr ist als Kosmetik:** Bisher war nur das *Aufheben* gesperrt (FR-027). Ein sichtbarer,
  aber unaufhebbarer Haufen sieht für den Spieler aus wie ein Fehler — er läuft darüber, nichts
  passiert, und niemand sagt ihm warum. Unsichtbar ist die ehrlichere Sperre: was einem nicht gehört,
  ist nicht da. Nebenbei verschwindet damit auch der Anreiz, fremden Haufen hinterherzulaufen.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ein Charakter besitzt ein Konto, und es überlebt (Priority: P1)

Ein Charakter hat einen Kontostand. Etwas bucht darauf — gutschreiben oder abbuchen —, und jede
Buchung nennt ihren Grund. Der Stand kann nicht unter null fallen: eine Buchung, die darunter ginge,
wird abgelehnt, nicht gekappt. Der Stand übersteht Abmeldung, Wiederanmeldung und Serverneustart, und
zwei Charaktere desselben Spielers halten getrennte Stände.

**Why this priority**: Ohne das Konto gibt es nichts, worauf die anderen Geschichten buchen könnten.
Es ist die einzige Geschichte, die für sich allein einen Wert hat — ein Konto, das zuverlässig zählt,
ist der ganze Zweck des Blocks; alles Weitere sind Quellen und Abnehmer.

**Independent Test**: Vollständig prüfbar, indem auf einen Charakter gebucht, abgemeldet, der Server
neu gestartet und der Stand erneut gelesen wird — ohne dass irgendein Mob, eine Ausrüstungsstufe oder
ein Rang existiert.

**Acceptance Scenarios**:

1. **Given** ein Charakter mit Stand 0, **When** 500 mit Grund gutgeschrieben werden, **Then** ist der
   Stand 500 und die Buchung trägt ihren Grund.
2. **Given** ein Charakter mit Stand 100, **When** 500 abgebucht werden sollen, **Then** wird die
   Buchung abgelehnt und der Stand bleibt 100 — er wird nicht auf 0 gekappt.
3. **Given** ein Charakter mit Stand 500, **When** zwei Buchungen über je 400 im selben Tick
   eintreffen, **Then** gelingt genau eine und der Stand ist 100.
4. **Given** ein Charakter mit Stand 500, **When** der Spieler sich abmeldet und der Server neu
   startet, **Then** ist der Stand nach der Wiederanmeldung 500.
5. **Given** ein Spieler mit zwei Charakteren, **When** auf den ersten 500 gebucht werden, **Then**
   bleibt der Stand des zweiten unverändert.
6. **Given** eine Buchung ohne Grund, **When** sie versucht wird, **Then** kommt sie nicht zustande.
7. **Given** ein Startguthaben von null, **When** ein Charakter erstellt wird, **Then** ist sein Stand
   null, und es entsteht **keine** Buchung.
7a. **Given** ein konfiguriertes Startguthaben von 500, **When** ein Charakter erstellt wird, **Then**
   ist sein Stand 500, und der Verlauf zeigt eine Gutschrift mit dem Grund „Startguthaben".
7b. **Given** ein Charakter, der nie eine Buchung hatte, **When** das konfigurierte Startguthaben
   danach auf 500 erhöht wird, **Then** bleibt sein Stand unverändert — die Änderung trifft nur neu
   erstellte Charaktere.
8. **Given** ein Charakter, der stirbt, **When** er wieder einsteigt, **Then** ist sein Stand
   unverändert; der Tod kostet keine Coins.

---

### User Story 2 - Der Kill lässt Coins fallen, das Aufheben bucht (Priority: P2)

Ein Charakter tötet eine Kreatur. Am Ort des Todes fallen Coins zu Boden. Der Spieler läuft darüber,
hebt sie auf, und erst dieses Aufheben schreibt sie seinem Konto gut. Wieviel eine Kreatur fallen
lässt, steht in Konfiguration — je Kreaturtyp, mit einem Standardbetrag für alles, was keinen eigenen
Eintrag hat. Wer Anspruch hat und wieviel, folgt derselben Regel wie die Erfahrung: Anteil am Schaden,
Gruppe als ein Beitragender, Rest bleibt liegen. Wer seinen Haufen liegen lässt, verliert ihn, wenn
die Verfallszeit abläuft.

**Why this priority**: Ohne Quelle steht jeder Stand für immer auf dem Startguthaben, und keine
Kostenprüfung wäre im Spiel je zu erleben. Es ist die erste Geschichte, die den Block sichtbar macht —
und die einzige, die Objekte in die Welt setzt.

**Independent Test**: Prüfbar, indem eine Kreatur getötet, der Haufen aufgehoben und der Stand vorher
und nachher gelesen wird — unabhängig davon, ob es etwas zu kaufen gibt.

**Acceptance Scenarios**:

1. **Given** ein Charakter mit Stand 0, **When** er eine konfigurierte Kreatur allein tötet, **Then**
   liegt am Ort des Todes ein Coin-Haufen über den konfigurierten Betrag, und sein Stand ist **noch
   immer 0**.
2. **Given** dieser Haufen, **When** der Charakter ihn aufhebt, **Then** ist der Betrag seinem Konto
   gutgeschrieben und der Haufen verschwunden.
3. **Given** eine Kreatur ohne eigenen Eintrag, **When** sie getötet wird, **Then** fällt der
   Standardbetrag — nicht null.
4. **Given** zwei Spieler einer Gruppe in Reichweite, **When** sie gemeinsam töten, **Then** entsteht
   je Anspruchsberechtigtem **ein** Haufen über seinen Anteil, nach derselben Regel geteilt wie die
   Erfahrung und abgerundet; der Rest bleibt liegen.
5. **Given** ein Haufen, der einem anderen Charakter zusteht, **When** ein Spieler ohne Anspruch in
   der Nähe ist, **Then** **sieht er ihn gar nicht**, hebt ihn nicht auf, und sein Stand ändert sich
   nicht.
5a. **Given** ein Kill, an dem zwei Charaktere beteiligt waren, **When** beide Haufen entstehen,
   **Then** sieht jeder **nur seinen eigenen** — nicht den des anderen.
6. **Given** ein Haufen, den niemand aufhebt, **When** die konfigurierte Verfallszeit abläuft,
   **Then** verschwindet er, und **niemandem** wird etwas gutgeschrieben.
7. **Given** ein Anspruchsberechtigter, der sich abmeldet, bevor er aufhebt, **When** die Verfallszeit
   abläuft, **Then** ist der Betrag verloren.
7a. **Given** eine erreichte Deckelung, **When** ein weiterer Haufen entstehen soll, **Then** wird der
   weltweit älteste seinem Besitzer **gutgeschrieben** und abgeräumt, der neue entsteht, und die Zahl
   liegender Haufen bleibt bei der Deckelung.
7b. **Given** derselbe Fall, **When** der Besitzer des abgeräumten Haufens gerade **abgemeldet** ist,
   **Then** wirkt die Gutschrift trotzdem und steht beim nächsten Einstieg auf seinem Konto.
8. **Given** mehrere Haufen desselben Charakters dicht beieinander, **When** sie entstehen, **Then**
   werden sie zu einem zusammengelegt, statt einzeln liegen zu bleiben.
9. **Given** 800 Kills in kurzer Folge, **When** alle Haufen entstehen und aufgehoben werden,
   **Then** entsteht dabei kein Datenbankzugriff je Kill und keine wiederkehrende Aufgabe je Haufen.

---

### User Story 3 - Der Betreiber greift ein, und alles bleibt nachvollziehbar (Priority: P3)

Jede Buchung landet in einem dauerhaften Verlauf, der einen Neustart übersteht: wer, wann, wieviel,
warum, und wie der Stand davor und danach war. Ein Betreiber kann den Stand eines Charakters
**setzen, erhöhen und senken** — und genau diese Eingriffe sind im Verlauf besonders erkennbar, mit
dem Namen dessen, der sie ausgelöst hat.

**Why this priority**: Währung ist der Teil, bei dem Spieler sich beschweren, und eine Beschwerde ohne
Verlauf ist nicht zu klären. Der Eingriff wiederum ist die einzige Möglichkeit, einen Fehler
gutzumachen, den der Verlauf gerade sichtbar gemacht hat — beides gehört zusammen. Vor die beiden
Anbindungen gesetzt, weil sie beide auf diesen Verlauf buchen werden.

**Independent Test**: Prüfbar, indem gebucht, der Verlauf gelesen, der Server neu gestartet und der
Verlauf erneut gelesen wird; danach ein Eingriff, der im Verlauf mit dem Verursacher erscheint.

**Acceptance Scenarios**:

1. **Given** ein Charakter mit mehreren Buchungen, **When** der Verlauf gelesen wird, **Then**
   enthält er jede Buchung mit Zeitpunkt, Betrag, Grund sowie Stand davor und danach.
1a. **Given** ein Spieler mit drei Charakteren, **When** ein Betreiber sein Fenster öffnet, **Then**
   sieht er zuerst die drei Charaktere mit je eigenem Stand — **keine Summe** — und wählt einen aus.
1b. **Given** ein Charakter mit mehr Buchungen, als auf eine Seite passen, **When** der Verlauf
   geöffnet wird, **Then** erscheint die neueste Seite, und Vor- und Zurück-Knopf blättern; an beiden
   Enden führt kein Knopf darüber hinaus, und keine Buchung erscheint auf zwei Seiten.
2. **Given** derselbe Verlauf, **When** der Server neu startet, **Then** ist er unverändert lesbar.
3. **Given** ein Betreiber, **When** er den Stand eines Charakters auf einen Wert **setzt**, **Then**
   ist der Stand genau dieser Wert, und der Verlauf nennt den Eingriff samt Verursacher.
4. **Given** ein Betreiber, **When** er Coins **hinzufügt** oder **entfernt**, **Then** ändert sich
   der Stand um genau diesen Betrag, und der Verlauf nennt den Eingriff samt Verursacher.
5. **Given** ein Betreiber, der mehr entfernen will als vorhanden ist, **When** er es versucht,
   **Then** wird der Eingriff abgelehnt — auch ein Betreiber erzeugt keinen negativen Stand.
6. **Given** ein Charakter, der gerade **nicht** online ist, **When** ein Eingriff auf ihn erfolgt,
   **Then** wirkt er, und der Stand ist beim nächsten Einstieg der geänderte.
7. **Given** ein Spieler ohne Berechtigung, **When** er den Eingriff versucht, **Then** wird er
   abgewiesen und nichts ändert sich.
8. **Given** ein Eingriff an Spielerdaten, **When** er erfolgt, **Then** erscheint er zusätzlich im
   Audit-Log, das B02 dafür bereits vorhält.

---

### User Story 4 - Der Ausrüstungsaufstieg kostet Coins (Priority: P4)

B07 hält die erreichte Rüstungs- und Waffenstufe und trägt an jeder Stufe einen `cost`-Block, den es
bis heute undurchsichtig weiterreicht („B07 knows nothing about coins"). Dieser Block löst ihn auf:
Wer aufsteigen will, zahlt; wer nicht genug hat, steigt nicht auf und erfährt warum.

**Why this priority**: Schliesst einen bereits ausgelieferten Block ab. Die Zahlen stehen seit B07 in
`classes.yml` und werden bislang von niemandem gelesen — die Lücke wurde benannt statt gefüllt
(Workflow-Regel 5), und hier wird sie gefüllt.

**Independent Test**: Prüfbar, indem ein Charakter mit zu wenig Coins einen Aufstieg versucht, dann
genug bekommt und ihn erneut versucht.

**Acceptance Scenarios**:

1. **Given** eine Stufe mit `cost: { coins: 500 }` und ein Charakter mit 499, **When** er aufsteigen
   will, **Then** scheitert der Aufstieg wegen fehlender Coins, sein Stand bleibt 499, und seine Stufe
   bleibt unverändert.
2. **Given** derselbe Charakter mit 500, **When** er aufsteigt, **Then** gelingt der Aufstieg und sein
   Stand ist 0.
3. **Given** eine Stufe mit leerem `cost`-Block, **When** ein Charakter ohne Coins aufsteigt, **Then**
   gelingt der Aufstieg — die erste Stufe bleibt ohne Coins erreichbar.
4. **Given** ein Aufstieg, der aus einem anderen Grund als den Kosten scheitert, **When** er versucht
   wird, **Then** wurde nichts abgebucht.

---

### User Story 5 - Der Rangaufstieg kostet Coins (Priority: P5)

B08s `advanceRank` gelingt heute immer, weil es nichts gibt, woran es scheitern könnte; sein Ergebnis
kennt kein „zu teuer". Dieser Block setzt eine Kostenprüfung davor und ergänzt das fehlende Ergebnis.

**Why this priority**: Schliesst den zweiten ausgelieferten Block ab. Nachrangig zu US4 nur, weil die
Stufenkosten bereits in der Konfiguration stehen und die Rangkosten dort erst entstehen.

**Independent Test**: Prüfbar, indem ein Rangaufstieg mit zu wenig und danach mit genug Coins versucht
wird.

**Acceptance Scenarios**:

1. **Given** ein Rang mit konfigurierten Kosten und ein Charakter mit zu wenig Coins, **When** er den
   Rang steigern will, **Then** scheitert es an den Kosten, der Rang bleibt stehen und nichts wird
   abgebucht.
2. **Given** derselbe Charakter mit genug Coins, **When** er den Rang steigert, **Then** steigt der
   Rang und der Betrag ist abgebucht.
3. **Given** ein Charakter am Höchstrang, **When** er steigern will, **Then** scheitert es am
   Höchstrang und **nicht** an den Kosten — es wird nichts abgebucht.
4. **Given** eine Fähigkeit ohne konfigurierte Rangkosten, **When** der Rang steigt, **Then** kostet er
   nichts.

---

### User Story 6 - Der Spieler sieht seinen Stand (Priority: P6)

Ein Spieler kann seinen aktuellen Stand erfragen, und eine abgelehnte Buchung sagt ihm, was passiert
ist. Die Anzeige im HUD gehört zu B13; hier entsteht die Schnittstelle dafür und der Text hinter einem
Message-Schlüssel.

**Why this priority**: Ohne sie funktioniert alles, aber der Spieler erfährt es nicht. Zuletzt, weil
der Ort der Anzeige einem anderen Block gehört und hier nur die Schnittstelle entstehen soll.

**Independent Test**: Prüfbar, indem der Stand abgefragt wird und der zurückgegebene Wert mit dem
gebuchten übereinstimmt.

**Acceptance Scenarios**:

1. **Given** ein Charakter mit Stand 500, **When** der Stand abgefragt wird, **Then** wird 500
   geliefert.
2. **Given** eine abgelehnte Buchung, **When** sie versucht wird, **Then** erhält der Spieler eine
   Meldung, die über einen Message-Schlüssel läuft und nicht im Code steht.
3. **Given** ein Spieler mit drei Charakteren, **When** er sein eigenes Fenster öffnet, **Then**
   sieht er drei Stände nebeneinander und keinen zusammengezählten.
4. **Given** ein Spieler ohne Sonderrecht, **When** er das Fenster eines **anderen** Spielers öffnen
   will, **Then** wird er abgewiesen.

---

### Edge Cases

**Konto**

- **Zwei Buchungen im selben Tick auf denselben Stand.** Prüfen und Abziehen dürfen nicht zwei
  Aufrufe sein, zwischen denen etwas passieren kann — sonst geben zwei Fähigkeiten dasselbe Geld aus.
- **Eine Buchung, die unter null ginge.** Sie wird abgelehnt, nicht gekappt. Eine stille Kappung wäre
  ein Geschenk, das niemand bemerkt. Das gilt auch für den Betreiber.
- **Betrag null oder negativ.** Keine Buchung, sondern ein Aufruffehler, und wird als solcher
  behandelt.
- **Ein Stand, der über die darstellbare Grenze wachsen würde.** Eine Gutschrift, die überliefe, wird
  abgelehnt statt umzulaufen.
- **Ein Charakter, für den noch nie gebucht wurde.** Er hat **null**, keinen Fehler — und nicht den
  gerade konfigurierten Startwert (FR-011b).
- **Das Startguthaben wird geändert, während Charaktere existieren.** Bestehende Stände bleiben
  unberührt; die neue Zahl gilt nur für neu erstellte Charaktere.

**Coin-Haufen**

- **Der Ort des Todes.** Er muss als Wert festgehalten werden, solange er sicher gültig ist — nicht
  über die Id des toten Wesens nachgeschlagen, die nur gilt, solange die Todesbehandlung noch läuft
  (ADR-015 Punkt 6).
- **Ein Haufen entsteht in einem Bereich, der gleich darauf entladen wird.** Er darf nicht als
  Dauerlast zurückbleiben; spätestens die Verfallszeit räumt ihn ab.
- **Der Anspruchsberechtigte stirbt neben seinem eigenen Haufen.** Kein Verlust, kein Sonderfall — der
  Haufen bleibt seiner bis zum Verfall.
- **Ein Haufen wird aufgehoben, während der Kontostand gerade freigegeben wird.** Entweder die Buchung
  gelingt vollständig, oder der Haufen bleibt liegen — eine halb erfolgte Abholung darf es nicht
  geben.
- **Sehr viele Kills an derselben Stelle.** Haufen desselben Anspruchsberechtigten werden
  zusammengelegt; die Zahl gleichzeitig liegender Haufen ist gedeckelt.
- **Die Deckelung greift.** Der weltweit älteste Haufen wird gutgeschrieben und abgeräumt, damit der
  neue entstehen kann — auch wenn sein Besitzer gerade abgemeldet ist. Keine Coin geht verloren.
- **Der abgeräumte Haufen gehört dem, der gerade getötet hat.** Kein Sonderfall: er wird
  gutgeschrieben wie jeder andere, und danach entsteht sein neuer Haufen.
- **Ein Kill ohne Anspruchsberechtigten** — etwa eine Kreatur, die an Umgebungsschaden stirbt. Dann
  fällt nichts.

**Persistenz und Eingriff**

- **Absturz zwischen Buchung und Flush.** Der Verlust ist auf ein Autosave-Intervall begrenzt, wie
  bei jedem anderen Aggregat; darüber hinaus geht nichts verloren.
- **Ein Kill wird gutgeschrieben, während die Sitzung gerade endet.** Der letzte Wert muss vor der
  Freigabe beiseitegelegt werden, sonst liest der asynchrone Flush ins Leere (ADR-015 Punkt 7).
- **Ein Eingriff auf einen Charakter, der gerade online ist.** Er muss im maßgeblichen
  Zwischenspeicher wirken, nicht nur in der Datenbank — sonst überschreibt der nächste Flush ihn.
- **Ein Eingriff auf einen Charakter, den es nicht gibt.** Klare Ablehnung, keine stille Anlage.
- **Der Verlauf wächst unbegrenzt.** Bei 800 Mobs ist das binnen Wochen die grösste Tabelle des
  Projekts; die Aufbewahrungsdauer ist konfigurierbar, und Eingriffe des Betreibers werden davon
  nicht erfasst.

**Konfiguration**

- **Ein `cost`-Block nennt einen Schlüssel, den niemand kennt** — etwa `shards`. Das ist ein Preis,
  den niemand verlangen kann, und damit ein Startfehler, kein stilles Übergehen.

## Requirements *(mandatory)*

### Functional Requirements — das Konto (US1)

- **FR-001**: Das System MUSS je Charakter genau einen Kontostand führen, nicht je Account (ADR-011).
- **FR-002**: Der Kontostand MUSS ganzzahlig sein. Bruchteile einer Münze sind eine Rundungsquelle
  ohne Nutzen.
- **FR-003**: Der Kontostand DARF NIEMALS negativ werden — auch nicht durch einen Eingriff des
  Betreibers.
- **FR-004**: Eine Abbuchung, die den Stand unter null brächte, MUSS abgelehnt werden — nicht auf null
  gekappt.
- **FR-005**: Jede Buchung MUSS einen Grund tragen. Eine Buchung ohne Grund MUSS unmöglich sein.
- **FR-006**: Prüfung und Abbuchung MÜSSEN gegenüber anderen Buchungen desselben Kontos unteilbar
  sein: zwei Buchungen im selben Tick dürfen nicht beide dasselbe Geld ausgeben.
- **FR-007**: Das System MUSS eine Kostenprüfung als Schnittstelle anbieten, die zwei Fragen
  beantwortet — „kann dieser Charakter X zahlen" und „ziehe X ab, unteilbar".
- **FR-008**: Eine abgelehnte Buchung MUSS den Stand unverändert lassen und dem Aufrufer erkennbar
  machen, warum sie abgelehnt wurde.
- **FR-009**: Ein Betrag von null oder ein negativer Betrag MUSS als Aufruffehler zurückgewiesen
  werden; die Richtung ergibt sich aus Gutschrift beziehungsweise Abbuchung, nie aus dem Vorzeichen
  des Betrags.
- **FR-010**: Eine Gutschrift, die den darstellbaren Bereich überschritte, MUSS abgelehnt werden statt
  umzulaufen.
- **FR-011**: Das Startguthaben bei Charaktererstellung MUSS konfigurierbar sein und im
  Auslieferungszustand **null** betragen.
- **FR-011a**: Ein Startguthaben über null MUSS als **Gutschrift bei der Charaktererstellung**
  gebucht werden, mit eigenem Grund und eigenem Verlaufseintrag. Es entsteht nicht beim Lesen.
- **FR-011b**: Ein Charakter ohne Kontozeile MUSS den Stand **null** melden — **nicht** den gerade
  konfigurierten Wert. Sonst änderte eine spätere Anpassung der Zahl rückwirkend die Stände aller
  noch unbebuchten Charaktere, ohne Buchung und ohne Spur.
- **FR-011c**: Ist das Startguthaben null, DARF keine Buchung entstehen. Eine Gutschrift über null
  ist nach FR-009 ohnehin unzulässig.
- **FR-012**: Der Tod eines Charakters DARF den Kontostand NICHT verändern. Es entsteht keine
  Buchungsart für einen Todesverlust.

### Functional Requirements — Persistenz (US1)

- **FR-013**: Während der Sitzung MUSS der zwischengespeicherte Stand maßgeblich sein, nicht die
  Datenbank (Prinzip IV).
- **FR-014**: Keine Buchung DARF einen Datenbankzugriff im Spielereignis auslösen (Prinzip II);
  Schreibvorgänge laufen über den Schreib-Puffer.
- **FR-015**: Jeder neue Aggregattyp MUSS **drei** Eintragungen erhalten — den Enum-Wert, den Platz in
  der Schreibreihenfolge und ein verdrahtetes Repository (ADR-015 Punkt 7). Ein Typ ohne Platz in der
  Schreibreihenfolge zählt bei jedem Flush als fehlgeschlagen und wird nie geschrieben.
- **FR-016**: Am Sitzungsende MUSS die Reihenfolge „beiseitelegen, markieren, freigeben" gelten, damit
  der asynchrone Flush den letzten Stand noch lesen kann.
- **FR-017**: Der Stand MUSS beim Login in derselben Runde geladen werden wie die übrigen
  Charakterdaten — keine zweite Datenbankrunde im Anmeldepfad (ADR-015 Punkt 3).
- **FR-018**: Alle Schemaänderungen MÜSSEN über versionierte Migrationen entstehen.

### Functional Requirements — Coins fallen und werden aufgehoben (US2)

- **FR-019**: Der Tod einer Kreatur MUSS Coins **am Ort des Todes fallen lassen**; er DARF NICHT
  direkt gutschreiben.
- **FR-020**: Der Ort MUSS als Wert festgehalten werden, solange er sicher gültig ist, und DARF NICHT
  über die Id des toten Wesens nachgeschlagen werden (ADR-015 Punkt 6).
- **FR-021**: Erst das Aufheben MUSS buchen, mit einem eigenen Buchungsgrund.
- **FR-022**: Wieviel eine Kreatur fallen lässt, MUSS aus Konfiguration kommen — je Kreaturtyp, mit
  einem Standardbetrag für Typen ohne eigenen Eintrag (Prinzip V).
- **FR-023**: Ein fehlender Eintrag MUSS den Standardbetrag bedeuten, **nicht** null: eine Kreatur, die
  Mojang letzte Woche ergänzt hat, soll nicht stillschweigend wertlos sein.
- **FR-024**: Wer Anspruch hat und wieviel, MUSS derselben Regel folgen wie die Erfahrung — Anteil am
  Schaden, eine Gruppe als ein Beitragender, gleichmäßige Teilung auf die Mitglieder in Reichweite.
- **FR-024a**: Es gibt **keine Mindestbeteiligung**. Die Verteilung ist rein anteilig; auch ein
  kleiner Schadensanteil ergibt einen Anspruch. Eine Schwelle nur für Coins bewertete denselben Kill
  anders als die Erfahrung.
- **FR-025**: Abgerundet wird nach unten; der Rest bleibt liegen.
- **FR-026**: Je Anspruchsberechtigtem MUSS **ein** Haufen entstehen, niemals ein Objekt je Coin.
- **FR-027**: Ein Haufen MUSS nur von seinem Anspruchsberechtigten aufgehoben werden können.
- **FR-027a**: Ein Haufen MUSS nur für seinen Anspruchsberechtigten **sichtbar** sein. Wer nicht
  berechtigt ist, sieht ihn überhaupt nicht. Ein sichtbarer, aber unaufhebbarer Haufen sähe für den
  Spieler aus wie ein Fehler.
- **FR-028**: Haufen desselben Anspruchsberechtigten in unmittelbarer Nähe MÜSSEN zu einem
  zusammengelegt werden.
- **FR-029**: Ein Haufen MUSS nach einer konfigurierbaren Verfallszeit verschwinden. Ein verfallener
  Haufen wird **niemandem** gutgeschrieben.
- **FR-030**: Die Zahl gleichzeitig liegender Haufen MUSS gedeckelt sein, und es DARF keine
  wiederkehrende Aufgabe je Haufen geben (Prinzip II). Scheduling läuft ausschliesslich über die
  projekteigene Abstraktion (Prinzip I).
- **FR-030a**: Ist die Deckelung erreicht und soll ein Haufen entstehen, MUSS der **weltweit älteste**
  Haufen seinem Besitzer **gutgeschrieben** und abgeräumt werden; danach entsteht der neue. Die
  Deckelung ist ein globales Budget, deshalb weltweit und nicht je Spieler.
- **FR-030b**: Bei dieser Abräumung DARF **keine Coin verloren gehen**. Die Gutschrift trägt einen
  eigenen Buchungsgrund und erscheint im Verlauf.
- **FR-030c**: Ist der Besitzer des abgeräumten Haufens **nicht angemeldet**, MUSS die Gutschrift
  trotzdem wirken — über denselben Weg wie ein Eingriff auf einen abgemeldeten Charakter (FR-042).
- **FR-030d**: Abräumen und Verfallen sind **verschiedene Vorgänge**: ein Haufen, dessen Frist abläuft,
  wird niemandem gutgeschrieben (FR-029); ein Haufen, den der Server abräumt, schon. Eigene
  Versäumnisse kosten, Serverlast nicht.
- **FR-031**: Ein Kill ohne Anspruchsberechtigten MUSS nichts fallen lassen.
- **FR-032**: Die Herkunft der Beträge MUSS austauschbar sein, damit B10 sie später über dieselbe
  Schnittstelle übernimmt, ohne dass dieser Block sich ändert.
- **FR-033**: Ein Coin-Haufen ist **kein** RPG-Item im Sinne von B11: er trägt keine Vorlage, gehört
  keinem Inventar an und wird nicht persistiert.

### Functional Requirements — Verlauf und Eingriff (US3)

- **FR-034**: Jede Buchung MUSS in einem dauerhaften, nur anfügenden Verlauf festgehalten werden.
- **FR-035**: Ein Verlaufseintrag MUSS Zeitpunkt, Charakter, Betrag, Richtung, Grund sowie Stand davor
  und danach enthalten.
- **FR-036**: Der Verlauf MUSS einen Serverneustart überstehen und je Charakter und Zeitraum lesbar
  sein.
- **FR-037**: Der Verlauf MUSS über den Schreib-Puffer geschrieben werden; sein Entstehen DARF kein
  Spielereignis verzögern.
- **FR-038**: Die Aufbewahrungsdauer für Buchungen aus dem Spielgeschehen MUSS konfigurierbar sein.
  Eingriffe des Betreibers MÜSSEN davon ausgenommen bleiben.
- **FR-039**: Ein Betreiber MUSS den Stand eines Charakters **setzen**, **erhöhen** und **senken**
  können.
- **FR-040**: Jeder Eingriff MUSS im Verlauf als solcher erkennbar sein und den Verursacher nennen.
- **FR-041**: Jeder Eingriff MUSS zusätzlich im Audit-Log erscheinen, das B02 für Eingriffe an
  Spielerdaten bereits vorhält.
- **FR-042**: Ein Eingriff MUSS auch auf einen Charakter wirken, der gerade nicht online ist.
- **FR-043**: Ein Eingriff auf einen Charakter, der gerade online ist, MUSS im maßgeblichen
  Zwischenspeicher wirken, damit der nächste Flush ihn nicht überschreibt.
- **FR-044**: Ein Eingriff auf einen nicht existierenden Charakter MUSS abgelehnt werden — ohne
  stillschweigend einen anzulegen.
- **FR-045**: Der Eingriff MUSS an eine Berechtigung gebunden sein; ohne sie wird er abgewiesen.
- **FR-046**: Der Aufrufweg für den Eingriff ist **vorläufig** und MUSS so gebaut sein, dass B14 ihn
  ohne Änderung der darunterliegenden Schnittstelle in sein Kommando-Framework überführen kann.
- **FR-046a**: Ein Betreiber MUSS den Verlauf eines Charakters **im Spiel lesen** können. Die Anzeige
  erfolgt **seitenweise**; eine unbegrenzte Abfrage ist unzulässig, weil diese Tabelle die grösste des
  Projekts wird.
- **FR-046b**: Der Verlauf wird für **einen** Charakter angezeigt. Hat ein Spieler mehrere, geht der
  Anzeige eine **Auswahl des Charakters** voraus. Ein zusammengeworfener Verlauf wäre nicht mehr
  charaktergebunden, und eine Summe über drei Stände wäre eine Zahl, die es im Spiel nicht gibt
  (ADR-011).

### Functional Requirements — Abnehmer (US4, US5)

- **FR-047**: Der `cost`-Block einer Ausrüstungsstufe MUSS ausgelesen und vor dem Aufstieg geprüft
  werden.
- **FR-048**: Ein Aufstieg mit zu wenig Coins MUSS scheitern, ohne Stufe und Stand zu verändern, und
  ein eigenes, von den übrigen unterscheidbares Ergebnis liefern.
- **FR-049**: Ein leerer `cost`-Block MUSS „kostenlos" bedeuten.
- **FR-050**: Ein `cost`-Block mit einem unbekannten Schlüssel MUSS ein Startfehler sein (Fail-Fast),
  kein stilles Übergehen.
- **FR-051**: Der Rangaufstieg einer Fähigkeit MUSS eine Kostenprüfung vor sich bekommen, und sein
  Ergebnis MUSS um „nicht genug Coins" erweitert werden.
- **FR-052**: Scheitert ein Aufstieg aus einem anderen Grund als den Kosten — Höchstrang erreicht,
  nicht freigeschaltet —, DARF nichts abgebucht worden sein; die Kostenprüfung läuft erst, wenn alle
  übrigen Bedingungen erfüllt sind.
- **FR-053**: Rangkosten MÜSSEN in der Fähigkeitskonfiguration stehen, Stufenkosten in der
  Klassenkonfiguration. Ein zentraler Preiskatalog DARF NICHT entstehen (ADR-027).
- **FR-054**: Eine Fähigkeit ohne konfigurierte Rangkosten MUSS kostenlos aufsteigen.
- **FR-055**: Die Aussage in B08s Dokumentation, es gebe keine Währung, MUSS korrigiert werden; sie ist
  mit diesem Block falsch.

### Functional Requirements — Sichtbarkeit und Rahmen (US6)

- **FR-056**: Das System MUSS den aktuellen Stand eines Charakters abfragbar machen, damit B13 ihn
  anzeigen kann.
- **FR-057**: Alle Spielertexte dieses Blocks MÜSSEN über Message-Schlüssel laufen; hartcodierte Texte
  sind unzulässig (Prinzip V).
- **FR-058**: Die Konfiguration dieses Blocks MUSS beim Start gegen ein Schema validiert werden;
  Fehler führen zu Fail-Fast mit klarer Meldung.
- **FR-059**: Die Regellogik MUSS ohne laufenden Server prüfbar sein und KEINE Bukkit-Abhängigkeit
  tragen (Prinzip III).

### Key Entities

- **Kontostand**: was ein Charakter besitzt. Gehört genau einem Charakter, ist ganzzahlig und niemals
  negativ. Während der Sitzung im Speicher maßgeblich, über den Schreib-Puffer nachgeführt.
- **Buchung**: eine Änderung des Kontostands. Trägt Richtung, Betrag und **Grund**. Ohne Grund
  existiert sie nicht.
- **Buchungsgrund**: woher eine Änderung kommt — Startguthaben, Aufheben eines Haufens, Verkauf,
  Kauf, Reparatur, Ausrüstungsstufe, Fähigkeitsrang, Eingriff des Betreibers. Ein abgeschlossener
  Vorrat, damit eine Fehlbuchung auffindbar bleibt.
- **Verlaufseintrag**: eine festgehaltene Buchung mit Zeitpunkt, Stand davor und danach und — bei
  einem Eingriff — dem Verursacher. Nur anfügend.
- **Kostenprüfung**: die Schnittstelle, die andere Blöcke rufen. Beantwortet „kann er zahlen" und
  „zieh ab, unteilbar" — nicht als zwei Aufrufe, zwischen denen etwas passieren kann.
- **Coin-Haufen**: ein Betrag, der in der Welt liegt und einem Charakter zusteht. Kennt Betrag,
  Anspruchsberechtigten, Ort und Verfallszeitpunkt. Kein RPG-Item, nicht persistiert.
- **Kreatur-Ertrag**: wieviel der Tod einer Kreatur fallen lässt. Konfiguration je Typ plus
  Standardwert, austauschbar durch B10.
- **Buchungsergebnis**: was aus einer Buchung wurde — gelungen, zu wenig Guthaben, ungültiger Betrag —
  jeweils mit dem Text, den der Spieler erfährt.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Bei 1000 gleichzeitigen Abbuchungsversuchen auf dasselbe Konto entsteht in **null**
  Fällen ein negativer Stand, und die Summe der gelungenen Abbuchungen übersteigt den Ausgangsstand
  nicht.
- **SC-002**: Ein Stand übersteht Abmeldung, Wiederanmeldung und Serverneustart ohne Abweichung;
  Verlust tritt höchstens im Umfang eines Autosave-Intervalls auf, auch bei Absturz.
- **SC-003**: Zwei Charaktere desselben Spielers halten in 100 % der Fälle getrennte Stände.
- **SC-004**: 100 % aller Standänderungen sind im Verlauf einem Grund zuzuordnen; es gibt keinen Pfad,
  der den Stand ohne Eintrag ändert.
- **SC-005**: 800 Kills in Folge erzeugen **null** Datenbankzugriffe im Spielereignis und **null**
  wiederkehrende Aufgaben je Haufen.
- **SC-006**: Bei 150 Spielern und 800 aktiven Mobs bleibt der Anteil dieses Blocks am Tick-Budget bei
  ≤ 5 ms, und die Zahl gleichzeitig liegender Haufen überschreitet die konfigurierte Deckelung nicht.
- **SC-007**: Ein neuer Spieler erreicht die erste Ausrüstungsstufe, ohne jemals eine Coin
  aufgehoben zu haben.
- **SC-008**: Ein Spieler mit zu wenig Coins erfährt bei jedem gescheiterten Kauf, dass es am Geld
  lag, und findet danach seinen Stand unverändert vor.
- **SC-009**: Eine geänderte Preis- oder Ertragszahl in der Konfiguration wirkt nach einem Neustart,
  ohne dass Code geändert wurde.
- **SC-010**: Ein Betreiber klärt eine Beschwerde über einen verschwundenen Betrag allein aus dem
  Verlauf, ohne ins Log zu sehen.
- **SC-011**: Jeder Eingriff eines Betreibers ist im Nachhinein seinem Verursacher zuzuordnen — in
  100 % der Fälle.
- **SC-012**: Die Regeln des Blocks sind vollständig ohne laufenden Server prüfbar.

## Assumptions

- **Es gibt genau eine Währung**, die Coins heisst. ADR-027 spricht durchgehend von „einer Währung";
  dass der `cost`-Block eine Map ist, macht ihn erweiterbar, nicht mehrwährungsfähig. Ein zweiter
  Schlüssel darin ist deshalb ein Startfehler (FR-050) und keine zweite Geldsorte.
- **Ein Haufen ist dem Anspruchsberechtigten vorbehalten und nur für ihn sichtbar.** Sonst wäre die
  Schadensanteilsregel aus FR-024 im selben Atemzug wieder aufgehoben — der Schnellste nähme alles,
  und die Gruppenteilung, die für die Erfahrung sorgfältig gebaut ist, wäre für Coins bedeutungslos.
  *Bei `/clarify` bestätigt und um die Sichtbarkeit erweitert (FR-027a); keine Annahme mehr, sondern
  eine Entscheidung.*
- **Der Kreatur-Ertrag wird hier konfiguriert, bis B10 existiert**, und dann über dieselbe
  Schnittstelle abgelöst — genau die Anordnung, die B06 für die Erfahrung und B05 für Mob-Attribute
  bereits benutzt. Die konkreten Beträge sind Balancing und gehören zu B10/B16; hier zählt, dass es
  eine Quelle gibt.
- **Sichtbarkeit heisst hier Schnittstelle, nicht Anzeige.** Wo der Spieler seinen Stand sieht — HUD,
  Scoreboard, Chat — entscheidet B13. Dieser Block liefert die Abfrage und die Message-Schlüssel.
- **Der Aufrufweg des Eingriffs ist ein Provisorium.** B14 besitzt Kommandos, Rechtebaum und
  Tab-Completion; bis dahin genügt der kleinste Weg, der die Fähigkeit benutzbar macht. Die
  Schnittstelle darunter ist das Bleibende, das Kommando darüber das Wegwerfbare.
- **Kein Handel zwischen Spielern**, ausgeschlossen durch `00-vision-scope.md`. NPC-Verkauf ist davon
  gedeckt, der Händler selbst gehört zu B11.
- **Kein zentraler Preiskatalog.** Preise bleiben bei dem, der sie verlangt.
- **Die beiden ausgelieferten Blöcke werden nachgezogen, nicht umgebaut.** B07 bekommt die Auflösung
  des `cost`-Blocks, B08 die Kostenprüfung vor dem Rangaufstieg; im Übrigen behalten beide ihre
  bestehende Schnittstelle.
- **Der Todes-Pfad steht bereits.** Der Ertrag hängt sich an denselben Pfad, über den B06 heute
  Erfahrung verteilt. Neu ist allein, was danach passiert: fallen lassen statt gutschreiben.

## Offene Punkte für `/plan`

Keine davon blockiert die Spezifikation; alle drei sind bei der Planung zu entscheiden und als ADR
festzuhalten.

1. **Der Schichtbruch beim Admin-Kommando.** Ein Kommando in einem Schicht-1-Block ist eine Abweichung
   von der Architekturvorgabe und braucht nach der Governance-Regel der Constitution eine
   ausdrückliche, begründete Ausnahme — nicht nur einen Satz in dieser Spec.
2. **Ob der Coin-Haufen den Block lasttestpflichtig macht.** Prinzip VII nennt B05 und B10 namentlich.
   Mit einem Wurfobjekt je Kill bei 800 Mobs erfüllt dieser Block dasselbe Kriterium, ohne genannt zu
   sein. Zu entscheiden, ob die Liste erweitert wird.
3. **Die Aufbewahrungsdauer des Verlaufs.** FR-038 fordert, dass sie konfigurierbar ist, nennt aber
   keinen Standardwert. Der ist Betriebssache und gehört zu `/plan` — mit Blick darauf, dass diese
   Tabelle bei 800 Mobs binnen Wochen die grösste des Projekts wird.

## Dependencies

- **B02 · Persistenz** — Schreib-Puffer, versionierte Migrationen, Aggregattypen, Schreibreihenfolge
  und das bestehende Audit-Log für Eingriffe an Spielerdaten.
- **B03 · Spieler & Sitzung** — der Charakter, an dem der Stand hängt, und das Sitzungsbündel, in dem
  er beim Login mitgeladen wird.
- **B06 · Progression** — der Verteilungspfad beim Tod einer Kreatur, dessen Anspruchsregel gespiegelt
  wird.
- **B05 · Kampf-Pipeline** *(mittelbar)* — der Tod, aus dem der Ertrag entsteht, und der Schadensanteil,
  nach dem verteilt wird.
- **B07 · Klassensystem** *(Abnehmer)* — der `cost`-Block je Ausrüstungsstufe.
- **B08 · Fähigkeiten** *(Abnehmer)* — der Rangaufstieg.
- **B14 · Commands & Admin** *(späterer Übernehmer)* — überführt das vorläufige Kommando.
- **B11, B12** *(spätere Abnehmer)* — Verkauf, Reparatur, Statistik.
