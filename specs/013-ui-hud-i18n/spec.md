# Feature Specification: B13 · UI, HUD & Texte

**Feature Branch**: `013-ui-hud-i18n`

**Created**: 2026-08-30

**Status**: Draft

**Input**: Blockdokument `minecraft-rpg-spec/minecraft-rpg-spec/blocks/B13-ui-hud-i18n.md`,
einschließlich der am 2026-08-30 beantworteten sechs offenen Fragen.

## Ausgangslage

B13 ist der Block, auf den zwölf andere verwiesen haben. Wer im Baum nach `B13` sucht, findet
über vierzig Stellen — in `AbilityRegistry`, `ClassNotice`, `DamageAggregator`, `Currency`,
`GearConditions`, `MobKinds`, `PartyChangedEvent` und weiter. Fast alle sagen dasselbe: *dieser
Block liefert die Zahl, B13 zeichnet sie.* Die Daten liegen also bereits vor. Was fehlt, ist die
Fläche.

### Was heute schon auf dem Bildschirm steht

Vier Anzeigen sind über die letzten Blöcke entstanden, jede für sich sinnvoll, keine miteinander
abgestimmt:

- **`StatusActionBar`** (B05/B06) schreibt HP, Mana, Verteidigung und Fortschritt in die Actionbar.
  Sie ist ausdrücklich *nicht* `HudRenderer` genannt — ihr Javadoc sagt, der Name bleibe B13
  reserviert, weil ein größerer Name B13 gezwungen hätte, zwei Abstraktionen zu versöhnen statt
  eine zu erweitern. Sie zeichnet ereignisgesteuert und dazu einmal je Sekunde, weil Minecraft die
  Actionbar nach etwa zwei Sekunden ausblendet.
- **`ExperienceBar`** (B06) legt Level und Erfahrung auf die Vanilla-XP-Leiste. Vanilla-Erfahrung
  ist dabei kein eigener Wert, sondern nur eine Anzeige dessen, was B06 je Charakter führt.
- **`PaperVanillaAttributeBridge`** (B04) hält die Herzleiste als Prozentanzeige (ADR-003).
- **`MobNameplate`** (B10) beschriftet Kreaturen mit Art und Level.

**Bossbar und Scoreboard sind unbenutzt.** Zwei von vier Vanilla-Flächen liegen brach, während die
Actionbar alles trägt.

### Drei Fenster, zwei davon geliehen

- **`CurrencyMenu`** (B08b) zeigt Stand und Verlauf. **ADR-028 nennt es ausdrücklich befristet:**
  „Anzeige gehört B13" — B08b hat es nur gebaut, weil eine Schnittstelle ohne Aufrufweg für den
  Betreiber unbenutzbar gewesen wäre.
- **`WaypointMenu`** (B09) wählt das Reiseziel. **ADR-032 nennt es ebenso befristet:** „Fenster und
  Eingabe sind befristet und gehen an B13, sobald es existiert."
- **`ClassSelectionMenu`** (B07) und **`StatisticsMenu`/`LeaderboardMenu`** (B12) stehen ebenfalls,
  sind aber **nicht** befristet. Sie bleiben, wo sie sind.

### Was das für den Zuschnitt heißt

B13 ist damit weniger „alles bauen" als **aufräumen und ergänzen**: die vorhandenen Anzeigen unter
eine gemeinsame Ordnung stellen, die zwei brachliegenden Flächen in Dienst nehmen, die zwei
ausdrücklich geliehenen Fenster heimholen, das eine fehlende Fenster bauen — und die Texte so
legen, dass eine zweite Sprache möglich wird, ohne eine Zeile Code anzufassen.

---

## Clarifications

### Session 2026-08-30 — die sechs offenen Fragen des Blockdokuments

Die Roadmap verlangt, dass diese Liste vor `/specify` beantwortet ist: unbeantwortete Fragen
erzeugen erfundene Annahmen. Alle sechs sind beantwortet und im Blockdokument festgehalten.

1. **Aufteilung der HUD-Flächen** → Drei Flächen, drei Rollen. **Actionbar** trägt die laufenden
   Werte (HP, Mana, Verteidigung, Cooldown-Hinweise). **Bossbar** trägt das Situative (Zonenname
   beim Betreten, Bosskampf, kanalisierte Fähigkeit). **Scoreboard** trägt die Sidebar (Level, XP,
   Coins, Zone). Die Vanilla-XP-Leiste bleibt, was `ExperienceBar` aus ihr macht.
2. **Layout der Skill-Leiste** → Bleibt, wie B08 sie legt: Slot 0 ist B07s gebundene Waffe, ab
   Slot 1 je Fähigkeit ein Item, ein nicht freigeschalteter Slot bleibt leer. **B13 baut sie nicht
   um und zieht sie nicht hinter `HudRenderer`.** Sie läuft, sie ist getestet, und ein Umbau an
   fremdem funktionierendem Code wäre der teuerste Weg zu keinem sichtbaren Unterschied.
3. **Cooldown-Darstellung** → Über das **Vanilla-Cooldown-Overlay**: die graue Sweep-Animation
   direkt über dem Fähigkeits-Item im Slot. Keine eigene Anzeige, kein Pack, und die Geste kennt
   jeder Spieler von der Enderperle. **Bekannte Grenze: die Anzeige gilt je Material, nicht je
   Slot** — siehe FR-030 bis FR-032.
4. **GUIs zum Start** → Neu gebaut wird die **Charakterübersicht**. Heimgeholt werden die zwei
   ausdrücklich befristeten Fenster: **Reisefenster** (ADR-032) und **Kontofenster** (ADR-028).
   `ClassSelectionMenu` und B12s Statistikfenster bleiben unangetastet — sie sind nicht befristet,
   und ein Umbau an laufendem Code wäre derselbe Vorgriff, der bei der Skill-Leiste abgelehnt
   wurde. Kein Skilltree.
5. **Schadenszahlen** → Ja, als **kurzlebige Display-Entities** am Trefferort. B05s
   `DamageAggregator` bündelt bereits; B13 bekommt eine Zahl je Treffer und keinen Strom.
6. **Aktualisierungsfrequenz** → **Ereignisgesteuert plus ein Sammeltakt je Sekunde** für alle
   Spieler in einem Durchlauf.

### Session 2026-08-30 — Nachklärung an der geschriebenen Spec

- Q: Wie öffnet ein Spieler die Charakterübersicht? → A: Über ein Kommando `/char` — demselben Weg,
  den ADR-028 für `/coins` und B12 für `/stats` und `/top` gegangen sind. Vorläufig, wie die
  anderen: Rechtebaum und Tab-Completion bleiben B14.
- Q: Welche Rangfolge gilt, wenn sich mehrere Anlässe die eine Bossbar teilen? → A: Kanalisierte
  Fähigkeit vor Bosskampf vor Zonenname. Der verdrängte Anlass wird **nicht** nachgeholt.
- Q: Wer darf Anzeigen abschalten? → A: Nur der Betreiber, serverweit über die Konfiguration.
  **B13 führt damit keinen dauerhaften Spielerzustand** und hängt nicht an B02.
- Q: Was passiert mit einem offenen Fenster beim Charakterwechsel? → A: Es wird geschlossen, nicht
  neu aufgebaut.
- Q: Wie weit wandern die zwei befristeten Fenster? → A: Genau so weit, wie ihre ADRs es sagen. Von
  B09 wandern **Fenster und Rechtsklick-Eingabe** (ADR-032 nennt beide), von B08b **nur das
  Fenster** — `/coins` bleibt stehen, bis B14 die Kommandos einsammelt. Eine Eingabegeste ist
  Präsentation, ein Kommando mit Rechtebaum und Tab-Completion ist es nicht.

### Korrektur am selben Tag

Die Frage nach den GUIs war ursprünglich mit „für Statistiken gibt es heute nur das Hologramm"
gestellt worden. Das war falsch: B12 hat `StatisticsMenu`, `LeaderboardMenu` und
`StatisticsMenuListener` gebaut und auf echtem Paper abgenommen. Nach dieser Korrektur wurde der
GUI-Umfang auf „nur die Befristeten holen" festgelegt — siehe Punkt 4.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ich sehe jederzeit, wie es mir geht (Priority: P1)

Ein Spieler läuft durch die Welt, kämpft, steigt auf, wechselt die Zone. Ohne den Blick vom
Geschehen zu nehmen, weiß er: wie viel Leben und Mana er hat, wie es um seine Verteidigung steht,
welches Level er trägt, wie weit der nächste Aufstieg ist, wie viele Coins er hat und in welcher
Zone er steht. Betritt er eine neue Zone, sagt es ihm eine Leiste am oberen Rand, die nach ein paar
Sekunden wieder verschwindet. Steht er im Bosskampf, zeigt dieselbe Leiste das Leben des Bosses.

**Why this priority**: Das ist der Block. Ohne diese Aufteilung trägt die Actionbar weiterhin alles
und zwei von vier Vanilla-Flächen bleiben brach — und die zwölf Blöcke, die auf B13 verweisen,
warten weiter.

**Independent Test**: Anmelden, Werte verändern lassen (Schaden nehmen, Mana verbrauchen, aufsteigen,
Coins verdienen, Zone wechseln) und prüfen, dass jede Änderung auf genau einer Fläche erscheint und
auf keiner zweiten.

**Acceptance Scenarios**:

1. **Given** ein angemeldeter Charakter, **When** er Schaden nimmt, **Then** ändert sich der Wert in
   der Actionbar unmittelbar und die Herzleiste zeigt weiterhin den richtigen Prozentwert.
2. **Given** ein Charakter außerhalb jedes Kampfes, **When** er eine Zone betritt, **Then** nennt die
   Bossbar den Zonennamen und blendet nach der konfigurierten Dauer wieder aus.
3. **Given** ein Charakter in der Sidebar-Ansicht, **When** sich Level, XP, Coins oder Zone ändern,
   **Then** ändert sich genau die betroffene Zeile und keine andere.
4. **Given** zwei gleichzeitige Anlässe für die Bossbar — Zonenwechsel und Bosskampf —, **When**
   beide anstehen, **Then** gewinnt der Bosskampf, und der Zonenname wird nicht dazwischengeschoben.
5. **Given** ein Spieler ohne gewählten Charakter, **When** er sich anmeldet, **Then** zeigt keine der
   drei Flächen Werte, die es für ihn noch nicht gibt.

---

### User Story 2 - Meine Charakterübersicht, an einer Stelle (Priority: P2)

Ein Spieler will wissen, was sein Charakter eigentlich ist: jedes Attribut, das B04 führt, mit seinem aktuellen
Werten, was er trägt, in welchem Zustand die Ausrüstung ist, welche Klasse, welches Level, wie viele
Coins. Heute steht das über mehrere Blöcke verteilt und teils nirgends.

**Why this priority**: Das einzige wirklich fehlende Fenster. Vier Blöcke — B04, B07, B08b, B11 —
haben Daten, die nirgendwo zusammen zu sehen sind.

**Independent Test**: `/char` eingeben, Ausrüstung wechseln, erneut eingeben und prüfen, dass die
Werte sich mitbewegt haben.

**Acceptance Scenarios**:

0. **Given** ein angemeldeter Charakter, **When** er `/char` ohne Argument eingibt, **Then** öffnet
   sich die Übersicht seines **aktiven** Charakters.
1. **Given** ein Charakter mit angelegter Ausrüstung, **When** er die Übersicht öffnet, **Then** zeigt
   sie jedes Attribut aus `Attribute` mit dem Wert, den B04 führt — heute zehn.
2. **Given** ein Ausrüstungsstück mit abgenutztem Zustand, **When** die Übersicht offen ist, **Then**
   ist der Zustand ablesbar, wie B11 ihn führt.
3. **Given** eine offene Übersicht, **When** sich ein angezeigter Wert ändert, **Then** wird der
   Inhalt neu aufgebaut — und nur dann.
4. **Given** ein Spieler mit drei Charakteren, **When** er die Übersicht öffnet, **Then** sieht er den
   **aktiven** Charakter und keine Summe über drei.

---

### User Story 3 - Ich sehe, wann ich wieder darf (Priority: P2)

Ein Spieler benutzt eine Fähigkeit aus seiner Skill-Leiste. Das Item im Slot bekommt sofort die
vertraute graue Sweep-Animation und ist wieder frei, sobald sie durchgelaufen ist. Er muss nicht
mitzählen und nicht in eine Textzeile schauen.

**Why this priority**: Ohne Rückmeldung drückt ein Spieler wiederholt auf eine Fähigkeit, die noch
nicht bereit ist — und hält das für einen Fehler des Servers.

**Independent Test**: Fähigkeit auslösen, Slot beobachten, nach Ablauf erneut auslösen.

**Acceptance Scenarios**:

1. **Given** eine bereite Fähigkeit im Slot, **When** sie ausgelöst wird, **Then** läuft die
   Cooldown-Anzeige über genau diesem Slot und endet mit dem tatsächlichen Cooldown.
2. **Given** ein laufender Cooldown, **When** der Spieler sich abmeldet und wieder anmeldet, **Then**
   zeigt die Anzeige den **verbleibenden** Cooldown, nicht den vollen und nicht keinen.
3. **Given** eine Klasse, in der zwei Fähigkeiten dasselbe Material tragen würden, **When** der Server
   startet, **Then** bricht er mit einer Meldung ab, die Datei, Schlüssel und Grund nennt.

---

### User Story 4 - Ich sehe, was mein Treffer angerichtet hat (Priority: P2)

Trifft ein Spieler eine Kreatur, steigt für einen Moment eine Zahl am Trefferort auf und verschwindet
wieder. Trifft er mehrfach schnell hintereinander, sieht er nicht jeden Einzelschlag, sondern das,
was B05 als *einen* Treffer bündelt.

**Why this priority**: Ohne Zahlen ist Ausrüstung nicht bewertbar — ein Spieler kann nicht sehen, ob
das neue Schwert etwas gebracht hat. B05 bündelt seit Monaten für genau diesen Zweck.

**Independent Test**: Eine Kreatur schlagen und die aufsteigende Zahl mit dem tatsächlich zugefügten
Schaden vergleichen.

**Acceptance Scenarios**:

1. **Given** ein Spieler im Kampf, **When** er trifft, **Then** erscheint am Trefferort die gebündelte
   Zahl und verschwindet nach der konfigurierten Dauer wieder.
2. **Given** mehrere Spieler, die dieselbe Kreatur schlagen, **When** alle treffen, **Then** sieht
   jeder seine eigene Zahl.
3. **Given** ein Serverneustart mitten im Kampf, **When** der Server wieder oben ist, **Then** ist
   keine Zahl in der Welt übrig geblieben.

---

### User Story 5 - Die geliehenen Fenster kommen heim (Priority: P3)

Für den Spieler ändert sich nichts Sichtbares: Rechtsklick auf einen Wegpunkt-Kristall öffnet
weiterhin die Reiseauswahl, `/coins` weiterhin Stand und Verlauf. Was sich ändert, ist, wem sie
gehören — und dass ADR-028 und ADR-032 damit geschlossen sind.

**Why this priority**: Zwei ADRs sagen ausdrücklich „befristet, geht an B13, sobald es existiert".
B13 existiert jetzt. Es ist die Sorte Schuld, die niemand einfordert und die deshalb zehn Blöcke
lang liegen bleibt.

**Independent Test**: Beide Fenster wie bisher öffnen und bedienen; danach prüfen, dass in B08b und
B09 keine Anzeige mehr liegt.

**Acceptance Scenarios**:

1. **Given** ein Spieler an einem freigeschalteten Wegpunkt, **When** er rechtsklickt, **Then** öffnet
   sich die Auswahl mit demselben Verhalten wie zuvor.
2. **Given** `/coins` ohne Argument, **When** der Spieler es eingibt, **Then** sieht er Charakterwahl
   und danach den Verlauf, seitenweise wie zuvor.
3. **Given** die umgezogenen Fenster, **When** man in B09 nach Anzeige- oder Eingabecode sucht,
   **Then** findet man keinen mehr; in B08b bleibt allein die `/coins`-Schale stehen, die auf das
   übernommene Fenster verweist.

---

### User Story 6 - Der Server könnte auch Deutsch sprechen (Priority: P3)

Ein Betreiber will die Texte austauschen — vollständig, nicht stückweise, und ohne eine Zeile Code
anzufassen. Er legt eine zweite Sprachdatei an, stellt die Standardsprache um und startet neu.

**Why this priority**: Das Blockdokument nennt „weitere Sprachen strukturell möglich" als Vorgabe.
Heute kennt `Messages` keine Sprache — es gibt genau eine Datei. Die Struktur zu ändern ist später
teurer, weil bis dahin jeder Block Schlüssel dazugelegt hat.

**Independent Test**: Eine zweite Sprachdatei anlegen, umstellen, starten — jeder Text erscheint in
der neuen Sprache, und ein fehlender Schlüssel bricht den Start ab.

**Acceptance Scenarios**:

1. **Given** eine zweite, vollständige Sprachdatei, **When** der Server damit startet, **Then**
   erscheint jeder Spielertext in dieser Sprache.
2. **Given** eine zweite Sprachdatei, in der ein Schlüssel fehlt, **When** der Server startet,
   **Then** bricht er ab und nennt Datei, Schlüssel und Grund.
3. **Given** den gesamten Produktivcode, **When** ein Test nach Spielertexten sucht, **Then** findet
   er keinen einzigen hartcodierten.

---

### Edge Cases

- **Zwei Anlässe, eine Bossbar.** Zonenwechsel und Bosskampf treffen zusammen. Es gibt genau eine
  Bossbar je Spieler; wer gewinnt, muss festgelegt sein und nicht davon abhängen, welches Ereignis
  zufällig zuerst eintrifft.
- **Spieler ohne Charakter.** Zwischen Anmeldung und Charakterwahl gibt es keine Werte. Keine Fläche
  darf Nullen zeigen, die wie echte Werte aussehen.
- **Charakterwechsel bei offenem Fenster.** Ein Spieler wechselt den Charakter, während die
  Übersicht offen ist. Der Inhalt gehörte dann einem Charakter, den er nicht mehr spielt — das
  Fenster wird geschlossen (FR-055).
- **Abmeldung mitten in einer Anzeige.** Cooldown läuft, Schadenszahl schwebt, Bossbar steht. Nichts
  davon darf den Spieler überleben oder beim nächsten Anmelden falsch wieder auftauchen.
- **Serverneustart mit Display-Entities in der Welt.** Schadenszahlen sind Entities. Ein harter
  Abbruch darf keine übrig lassen, die niemand mehr aufräumt — dieselbe Klasse Fehler, die B10s
  Aufräumen und B12s Hologramm bereits einmal getroffen hat.
- **Zwei Fähigkeiten auf demselben Material.** Das Vanilla-Cooldown-Overlay gilt je Material. Zwei
  Fähigkeiten derselben Klasse auf demselben Item teilten sich eine Anzeige — beide grau, obwohl nur
  eine läuft.
- **Fehlender Text.** Ein Schlüssel ohne Text darf nie als Platzhalter oder leere Zeile beim Spieler
  ankommen.
- **Viele Spieler zugleich.** 200 Spieler, alle mit Actionbar, Scoreboard und teils Bossbar. Der
  Sammeltakt muss ein Durchlauf bleiben und darf nicht zu 200 werden.
- **Eine abgeschaltete Fläche.** Schaltet der Betreiber das Scoreboard ab, darf der Server nicht
  weiter Aktualisierungen berechnen und senden, die niemand sieht. Abgeschaltet heißt kostenlos,
  nicht unsichtbar.

---

## Requirements *(mandatory)*

### Die drei Flächen

- **FR-001**: Das System MUSS die Spieleranzeige auf genau drei Vanilla-Flächen verteilen:
  Actionbar, Bossbar und Scoreboard. Jeder Wert gehört genau einer Fläche.
- **FR-002**: Das System MUSS die **Actionbar** für die laufenden Werte benutzen: Leben, Mana,
  Verteidigung und Fortschritt.
- **FR-003**: Das System MUSS die **Bossbar** für das Situative benutzen: Zonenname beim Betreten,
  Bosskampf und kanalisierte Fähigkeit.
- **FR-004**: Das System MUSS bei mehreren gleichzeitigen Anlässen für die Bossbar die Rangfolge
  **kanalisierte Fähigkeit vor Bosskampf vor Zonenname** anwenden und DARF nicht von der
  Eintreffreihenfolge abhängen. Die Kanalisierung gewinnt, weil sie Sekunden dauert und den Spieler
  gerade festhält; der Bosskampf dauert Minuten und kommt danach von selbst zurück.
- **FR-004a**: Ein verdrängter Anlass wird **nicht** nachgeholt. Ein Zonenname, der während eines
  Bosskampfs anfiele, entfällt — nachgereicht wäre er eine Meldung über etwas, das längst vorbei
  ist.
- **FR-004b**: Es gibt **genau eine** Bossbar je Spieler. Mehrere stapeln sich am oberen Bildrand
  und machen die Fläche unlesbar.
- **FR-005**: Das System MUSS das **Scoreboard** als Sidebar mit Level, Erfahrung, Coins und Zone
  benutzen.
- **FR-006**: Das System MUSS die Vanilla-Erfahrungsleiste weiterhin als Anzeige von Level und
  Erfahrung des aktiven Charakters führen und DARF sie nicht als eigenen Wert behandeln.
- **FR-007**: Das System MUSS die Herzleiste in jeder Lage als korrekten Prozentwert des eigenen
  Lebens zeigen (ADR-003).
- **FR-008**: Das System DARF einem Spieler ohne gewählten Charakter keine Werte zeigen, die es für
  ihn noch nicht gibt.

### Aktualisierung

- **FR-009**: Das System MUSS eine Anzeige bei jeder Wertänderung unmittelbar neu zeichnen.
- **FR-010**: Das System MUSS zusätzlich **einen** Sammeltakt je Sekunde führen, der alle Spieler in
  **einem** Durchlauf bedient.
- **FR-011**: Das System MUSS die Actionbar in diesem Takt erneut senden, weil Minecraft sie sonst
  ausblendet.
- **FR-012**: Das System DARF je Spieler und Tick keine eigene geplante Arbeit erzeugen.
- **FR-013**: Das System MUSS eine Anzeige nur dann senden, wenn sich ihr Inhalt geändert hat oder
  das Ausblenden es erzwingt.
- **FR-013a**: Das System MUSS jede der drei Flächen und die Schadenszahlen über die Konfiguration
  **serverweit** abschaltbar machen. Eine abgeschaltete Fläche erzeugt keine Arbeit mehr.
- **FR-013b**: Das System DARF **keinen dauerhaften Zustand je Spieler oder Charakter** führen. Es
  gibt keine persönliche Anzeigeeinstellung, also auch kein Schema in B02 — ein Block, der nur
  zeichnet, speichert nichts.

### Texte und Sprachen

- **FR-014**: Das System DARF keinen Spielertext im Code führen. Jeder Text läuft über einen
  Message-Schlüssel.
- **FR-015**: Das System MUSS diese Zusage **maschinell** absichern — ein Test, der Produktivcode
  nach Spielertexten durchsucht, nach dem Muster von `ConfigOnlyAbilityTest` und
  `NoRawTypeNameLeftTest`.
- **FR-016**: Das System MUSS Texte nach **Sprache** getrennt ablegen können, mit Englisch als
  Standardsprache.
- **FR-017**: Das System MUSS die verwendete Sprache konfigurierbar machen.
- **FR-018**: Das System MUSS beim Start prüfen, dass die gewählte Sprache **jeden** benutzten
  Schlüssel kennt, und bei einer Lücke mit einer Meldung abbrechen, die Datei, Schlüssel und Grund
  nennt.
- **FR-019**: Das System DARF einen fehlenden Text niemals als Platzhalter oder leere Zeile an einen
  Spieler ausgeben.

### Schnittstellen

- **FR-020**: Das System MUSS die Ausgabe hinter einer Schnittstelle `HudRenderer` führen, sodass
  ein pack-fähiger Renderer später eingesetzt werden kann, ohne Spiellogik anzufassen (ADR-005).
- **FR-021**: Das System MUSS die Darstellung von Gegenständen hinter einer Schnittstelle
  `ItemRenderer` führen.
- **FR-022**: Ein Wechsel des `HudRenderer` DARF keine Änderung an B04, B05 oder B08 erfordern.
- **FR-023**: Das System MUSS `StatusActionBar` in diese Ordnung überführen, ohne dass ein
  vorhandener Wert dabei verschwindet.
- **FR-024**: Das System DARF `AbilityHotbar` aus B08 **nicht** umbauen und **nicht** hinter
  `HudRenderer` ziehen.

### Cooldowns

- **FR-030**: Das System MUSS einen laufenden Cooldown über dem Fähigkeits-Item im Slot anzeigen und
  DARF dafür keine eigene Fläche belegen.
- **FR-031**: Das System MUSS die angezeigte Restzeit aus dem tatsächlichen Cooldown des Blocks B08
  ableiten und DARF sie nicht zweitrechnen.
- **FR-032**: Das System MUSS beim Start sicherstellen, dass keine zwei Fähigkeiten **derselben
  Klasse** dasselbe Material tragen, und andernfalls mit einer Meldung abbrechen, die Datei,
  Schlüssel und Grund nennt. Andernfalls teilten sie sich eine Anzeige.
- **FR-033**: Das System MUSS nach einer Wiederanmeldung den **verbleibenden** Cooldown zeigen.

### Schadenszahlen

- **FR-040**: Das System MUSS zugefügten Schaden als kurzlebige Anzeige am Trefferort zeigen.
- **FR-041**: Das System MUSS dafür die von B05 **gebündelte** Zahl verwenden und DARF nicht je
  Einzelschlag zeichnen.
- **FR-042**: Das System MUSS die Anzeige nur dem Verursacher zeigen.
- **FR-043**: Das System MUSS die Lebensdauer der Anzeige konfigurierbar machen.
- **FR-044**: Das System MUSS jede erzeugte Anzeige wieder entfernen — auch nach einem Neustart
  dürfen keine übrig bleiben.
- **FR-045**: Das System MUSS die Schadenszahlen über die Konfiguration abschaltbar machen, ohne
  dass etwas anderes ausfällt.

### Charakterübersicht

- **FR-050**: Das System MUSS ein Fenster bereitstellen, das **jedes** Attribut aus `Attribute` für den **aktiven**
  Charakters mit ihren aktuellen Werten zeigt.
- **FR-051**: Das System MUSS darin die angelegte Ausrüstung und deren Zustand zeigen, wie B11 ihn
  führt.
- **FR-052**: Das System MUSS darin Klasse, Level und Coin-Stand zeigen.
- **FR-053**: Das System DARF keine Summe über mehrere Charaktere bilden.
- **FR-054**: Das System MUSS den Fensterinhalt zwischenspeichern und nur bei Änderung neu aufbauen.
- **FR-055**: Das System MUSS ein offenes Fenster **schließen**, wenn der Spieler den Charakter
  wechselt. Ein stiller Neuaufbau mit anderen Zahlen sieht aus wie ein Fehler, und der Spieler
  klickt weiter, ohne zu merken, dass er woanders ist.
- **FR-056**: Das System MUSS das Fenster aus reinen Vanilla-Materialien bauen (ADR-005).
- **FR-057**: Das System MUSS einen **Aufrufweg** für die Übersicht bereitstellen: ein Kommando
  `/char` ohne Argument, das die Übersicht des aktiven Charakters öffnet. Ein Fenster ohne Aufrufweg
  ist für den Spieler nicht vorhanden — dasselbe Argument, mit dem ADR-028 B08b ein Kommando
  zugestanden hat.
- **FR-058**: Das Kommando ist **vorläufig**. Rechtebaum, Tab-Completion und die einheitliche
  Kommandostruktur bleiben B14; B13 legt nur den einen Eintrag an, wie B08b und B12 es vor ihm
  getan haben.

### Die übernommenen Fenster

- **FR-060**: Das System MUSS aus B09 **das Reisefenster und die Rechtsklick-Eingabe am Kristall**
  übernehmen — ADR-032 nennt beide ausdrücklich als befristet. Verhalten für den Spieler
  unverändert.
- **FR-061**: Das System MUSS aus B08b **das Kontofenster** übernehmen (ADR-028). Verhalten für den
  Spieler unverändert.
- **FR-061a**: Das Kommando `/coins` bleibt, wo es ist. ADR-028 weist Kommandos B14 zu, und ein
  Kommando mit Rechtebaum und Tab-Completion ist keine Präsentation. B13 sammelt keine Kommandos
  ein — es legt nur das eine an, das sein eigenes Fenster braucht (FR-057).
- **FR-062**: Nach der Übernahme DARF in B08b und B09 kein Anzeigecode mehr liegen — in B08b außer
  der Kommandoschale, die auf das übernommene Fenster verweist.
- **FR-063**: Die Regeln hinter beiden Fenstern — welche Wegpunkte offen sind, was ein Klick kostet,
  was der Verlauf enthält — MÜSSEN in ihren Blöcken bleiben. B13 übernimmt die Anzeige, nicht die
  Entscheidung.

### Abgrenzung

- **FR-070**: Das System DARF `ClassSelectionMenu` aus B07 nicht umbauen.
- **FR-071**: Das System DARF `StatisticsMenu` und `LeaderboardMenu` aus B12 nicht umbauen.
- **FR-072**: Das System DARF keinen Skilltree bauen.
- **FR-073**: Das System DARF kein Resource Pack voraussetzen (ADR-005).
- **FR-074**: Das System DARF keine eigene Spiellogik einführen. Was gezeichnet wird, entscheiden die
  Blöcke, die die Daten führen.

### Key Entities

- **HUD-Fläche**: Eine der drei Vanilla-Flächen mit einer festen Rolle und je Spieler einem
  aktuellen Inhalt.
- **Anzeigeinhalt**: Was auf einer Fläche steht — abgeleitet aus fremden Werten, nirgends zweitens
  gespeichert.
- **Sprachsatz**: Alle Message-Schlüssel einer Sprache. Vollständig oder der Start bricht ab.
- **Schadensanzeige**: Eine kurzlebige Anzeige mit Zahl, Ort, Empfänger und Lebensdauer.
- **Fensteransicht**: Ein Fensterinhalt, zwischengespeichert und an einen Charakter gebunden.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Die HUD-Aktualisierung bleibt für 200 Spieler unter 1 ms je Tick.
- **SC-002**: Kein Spielertext ist im Code hartcodiert, maschinell nachgewiesen.
- **SC-003**: Die Herzleiste zeigt in allen geprüften Situationen den korrekten Prozentwert.
- **SC-004**: Ein Wechsel des `HudRenderer` erfordert keine Änderung an B04, B05 oder B08.
- **SC-005**: Ein Spieler erkennt an einer einzigen Bildschirmansicht Leben, Mana, Level, Zone und
  Coin-Stand, ohne ein Fenster zu öffnen.
- **SC-006**: Ein vollständiger Sprachsatz lässt sich ohne Codeänderung austauschen; ein
  unvollständiger bricht den Start ab und nennt die Lücke.
- **SC-007**: Ein laufender Cooldown ist ohne Zahl und ohne Textzeile am Slot erkennbar.
- **SC-008**: ADR-028 und ADR-032 sind geschlossen: in B08b und B09 liegt kein Anzeigecode mehr.
- **SC-009**: Nach einem harten Serverabbruch mitten im Kampf bleibt keine Schadensanzeige in der
  Welt zurück.
- **SC-010**: Eine in der Konfiguration abgeschaltete Fläche erzeugt für keinen Spieler noch Arbeit
  — nachweisbar daran, dass mit abgeschaltetem Scoreboard keine Scoreboard-Pakete mehr entstehen.
- **SC-011**: B13 legt kein Schema und keine Migration an. Der Block lässt sich vollständig
  entfernen, ohne dass Spielerdaten fehlen.

---

## Assumptions

- **Die Antworten der sechs offenen Fragen sind bindend** und wurden nicht neu verhandelt. Sie
  stehen im Blockdokument und oben unter *Clarifications*.
- **B08s Skill-Leiste bleibt unangetastet.** Die Vorgabe „Rendering liegt hinter Schnittstellen"
  gilt für das, was B13 baut, nicht rückwirkend für alles, was schon zeichnet.
- **B12s und B07s Fenster bleiben unangetastet.** Nur die zwei per ADR ausdrücklich befristeten
  Fenster wandern.
- **Das Kommando `/char` ist vorläufig**, wie `/coins` (ADR-028) und wie `/stats` und `/top` aus
  B12. Es braucht einen eigenen ADR nach demselben Muster, und es geht mit den anderen an B14.
- **Die Sprachumstellung wirkt beim Start**, nicht je Spieler zur Laufzeit. Ein Vanilla-Client meldet
  seine Sprache zwar, aber eine Anzeige je Spielersprache hieße, jeden Text mehrfach zu halten —
  das ist Umfang für einen späteren Block, nicht für diesen.
- **Schadenszahlen sind Display-Entities**, dieselbe Technik, die B12s Hologramm auf echtem Paper
  bereits belegt hat. Ihre Kosten sind damit bekannt und nicht geraten.
- **B05 bündelt bereits.** B13 verlässt sich darauf und baut keine eigene Bündelung.
- **Konfiguration** folgt dem Muster der anderen Blöcke: eine eigene Datei mit Schema-Prüfung beim
  Start, jede Meldung nennt Datei, Schlüssel und Grund.
