# Feature Specification: B11 · Items, Ausrüstung & Loot

**Feature Branch**: `011-items-loot-equipment`

**Created**: 2026-08-28

**Status**: Draft

**Input**: Blocksteckbrief `blocks/B11-items-loot-equipment.md`, die Entscheidungen ADR-004,
ADR-017, ADR-018 und ADR-027 in `02-decisions.md`, der Abschnitt B11 in `06-open-questions.md`,
sowie die Klärungssitzung vom 2026-08-28 (zwei Teile).

---

## Ausgangslage

**Dieser Block ist kleiner, als sein Name verspricht — und das ist kein Versehen.** ADR-017 hat
Rüstung und Waffe zu Klassenprogression gemacht, ADR-027 hat den Neuzuschnitt vollzogen. Was von
B11 übrig ist, muss deshalb zuerst gegen den bereits gebauten Bestand gehalten werden, sonst
spezifiziert dieser Block Dinge, die es schon gibt.

**Was andere Blöcke bereits geliefert haben** (im Code nachgesehen, nicht aus den Dokumenten
abgeschrieben):

| Aus dem Steckbrief B11 | Tatsächlicher Stand |
|---|---|
| „wer den Aufstieg bezahlt" | **Erledigt in B08b.** `EquipmentPurchase` kauft eine Stufe, `CostSpec` liest den `cost`-Block aus `classes.yml`, `BookingReason.EQUIPMENT_TIER` bucht ihn |
| Levelvoraussetzung für eine Stufe | **Erledigt in B07.** `EquipmentTier.requiredLevel` steht je Stufe in `classes.yml` (Krieger: 1, 10, 20, 35, 50, 60) |
| Kosmetik: Trims und Färbung der Klassenrüstung | **Erledigt in B07.** `TierAppearance` trägt Material, Farbe, Trim-Material und Trim-Muster je Stufe; `modelData` ist für ein späteres Resource Pack reserviert (ADR-005) |
| Lagerplatz: Enderchest | **Erledigt in B03.** `CharacterInventory` führt Rucksack **und** Enderchest je Charakter, nicht je Spieler |
| Klassenausrüstung ist unablegbar | **Erledigt in B07/ADR-018.** `EquipmentLockListener` bricht Klick, Drag, Hand-Tausch und Wurf ab; `BoundEquipment.isBoundTo` ist das Prädikat |
| Nicht-Ziel „kein Wirtschaftssystem" präzisieren | **Erledigt.** `00-vision-scope.md` sagt seit ADR-018 ausdrücklich „kein Spieler-zu-Spieler-Handel" |
| `BookingReason` für Verkauf und Reparatur | **Steht bereit.** `VENDOR_SALE`, `VENDOR_PURCHASE` und `REPAIR` existieren, bislang ungenutzt |
| Wem gehört gefallene Beute? | **Erledigt in B05.** `CombatDeathEvent.lootRecipient()` gibt den **größten Beitragenden** heraus — ausdrücklich *nicht* den letzten Treffer. Siehe unten |
| Beute nur für ihren Eigentümer sichtbar | **Vorgebaut in B08b.** `CoinPile` löst genau das für Coins, samt aller Fallen. Siehe unten |
| Ausrüstungsschaden beim Tod | **Naht vorhanden.** `CombatDeathEvent.playerVictim` trägt im Javadoc: *„B11 applies equipment damage only then"* |
| „Durability und Reparatur" | **Widerspricht dem Bestand.** `BoundItemFactory.makeIndestructible()` setzt Klassenausrüstung auf `setUnbreakable(true)`. Siehe unten |

### Drei Funde, die die Spec geformt haben

**1. Der Steckbrief widerspricht dem Code, und der Code hat recht.**

Der Steckbrief kündigt „Durability und Reparatur" an und nennt die Todesstrafe tragfähig, „weil
Haltbarkeitsverlust auf nicht ablegbarer Rüstung genauso funktioniert". Der gebaute Code sagt das
Gegenteil, und er begründet es:

> *„Takes wear out of the equation: class equipment does not degrade. […] A sword that broke left
> the warrior unarmed with no way to replace it — the ladder is the only source of weapons and
> dropping or crafting one is refused. […] Durability is also the wrong lever here for a second
> reason: the tier carries the numbers, and a damaged item would quietly weaken a character in a way
> no attribute reflects. **If wear is ever wanted as a mechanic, it belongs to the tier, not to the
> item stack.**"*
> — `BoundItemFactory.makeIndestructible()`

Diese Spec folgt dem Code: **Verschleiß ist ein Wert am Charakter**, der ItemStack bleibt
unzerstörbar. Beide Einwände von B07 sind damit ausgeräumt — nichts zerbricht, und die Schwächung
ist keine stille, weil sie durch die Werteberechnung geht und ablesbar ist.

**2. Beute teilt sich nicht wie Erfahrung und Coins — B05 hat das entschieden.**

Erfahrung und Coins werden **nach Anteil geteilt**: B06 und B08b benutzen dafür denselben
`ShareCalculator` (ADR-029), und `CoinDropPlanner` erzeugt „one pile per entitled character".
**Items können das nicht.** B05 hat daraus die Konsequenz gezogen:

> *„The top contributor is **not** necessarily the one who landed the last hit — that distinction is
> the whole decision against kill stealing: XP is split by share because XP divides, **loot goes to
> the largest contributor because a sword does not**."*
> — `DamageShare`

`CombatDeathEvent.lootRecipient()` gibt genau diesen Spieler heraus. B11 entscheidet hier nichts
neu; es benutzt die vorhandene Antwort.

**3. Die schwierigste Anforderung dieses Blocks ist bereits einmal gelöst worden.**

„Beute nur für ihren Eigentümer sichtbar und aufsammelbar" klingt nach Neubau. Es ist keiner:
`rpg.platform.currency` macht das seit B08b für Coin-Haufen, und das `package-info` benennt jede
Falle, in die ein zweiter Anlauf sonst liefe:

- `setVisibleByDefault(false)` plus `showEntity` je Spieler — **Unsichtbarkeit ist das ehrliche
  Schloss**, weil ein sichtbarer Haufen, den man nicht aufheben kann, wie ein Fehler aussieht
- **aber Sichtbarkeit ist nie die Autorität** (Prinzip VI): `Item.setOwner` und eine eigene Prüfung
  bleiben als zweites Schloss bestehen
- `showEntity` ist **Zustand der Verbindung**, nicht der Entität — nach einem Relogin ist der
  Gegenstand wieder unsichtbar, während beide Schlösser weiter passen. „Unsichtbar aber aufsammelbar
  ist das Schlechteste von beidem." `CoinPileRegistry.showPilesTo` holt das nach
- `setOwner` kennt **Spieler**, ADR-011 kennt **Charaktere** — ein Spieler hat bis zu drei, und ohne
  die zweite Prüfung sammelt Charakter B ein, was Charakter A verdient hat
- **Verschmelzen ist eine Gefahr, kein Merkmal**: Vanilla führt ähnliche Stapel zusammen
- Vanillas Verfall räumt weg, was niemand holt — **dieser Block plant nichts** und braucht keine
  wiederkehrende Aufgabe

B11 erbt diese Liste, statt sie neu zu lernen.

**Was daraus folgt:** B11 baut kein Ausrüstungssystem. B11 baut **das Item als Datenobjekt**, die
**Kategorien außerhalb der Klassenleiter** (Verbrauchbares, Kosmetik), die **Beute**, aus der sie
stammen, die **NPCs**, an denen Items zu Coins werden und Stufen gekauft werden, und den
**Verschleiß**, der dem Tod seine Strafe gibt. Die vorhandenen Nähte werden benutzt, nicht
verdoppelt.

**Die zentrale Zusage** steht seit ADR-004 und ist durch ADR-027 schärfer geworden: ein Item
speichert **die Vorlagen-ID und sonst nichts** — niemals berechnete Endwerte, niemals gerendertes
Lore. Ohne Roll-Werte ist die Vorlage die einzige Quelle, und eine Balancing-Änderung wirkt nach
einem Reload auf jedes vorhandene Exemplar in jedem Inventar, ohne dass ein Inventar angefasst wird.

---

## Clarifications

### Session 2026-08-28, Teil 1 — Umfang

- **Q1 · Der Aufstieg läuft über einen NPC, und Material entfällt.** Der NPC ist Händler und
  Upgrader zugleich: er kauft an, verkauft, repariert und führt den Stufenaufstieg durch. Der
  Aufstieg verlangt **ein Mindestlevel und Coins** — beides steht bereits in `classes.yml` und wird
  bereits von `EquipmentPurchase` geprüft. Eine eigene Kategorie „Aufstiegsmaterial" gibt es
  **nicht**; B11 liefert die Route zum vorhandenen Kauf, nicht einen zweiten Kaufmechanismus.
- **Q2 · Kosmetische Trimfarben als Endgame-Grind.** Weil Stufe 60 die Höchststufe ist, braucht das
  Spiel danach ein Ziel. Verschiedene Trimfarben werden als Kosmetik **verkauft**; die einzelne
  Trimfarbe hat **keine eigene Levelvoraussetzung**. Zur Kollision mit der Stufenerkennbarkeit siehe
  unten.
- **Q3 · Verschleiß zerstört nichts.** Ausrüstung geht **nie** kaputt. Stattdessen sinkt ihre
  Wirkung mit dem Verschleiß — bei Zustand null um volle **80 %**, und schon bei 10/100 spürbar. Der
  Spieler bekommt eine Warnung mit der Aufforderung, zu reparieren.

### Session 2026-08-28, Teil 2 — die Fragen vor dem Plan

- **Q4 + Q5 · Zwei Verschleißwerte mit je eigener Quelle.** Getrennt nach den beiden Slots der Leiter
  (`LadderSlot.ARMOR` / `WEAPON`), damit der Spieler gezielt reparieren kann:
  - **Erlittener Schaden** nutzt die **Rüstung** ab.
  - **Ausgeteilter Schaden aus einem Autoattack** nutzt die **Waffe** ab —
    **Fähigkeitsschaden nicht**. B05 unterscheidet das bereits über `DamageOrigin`: `MELEE` und
    `PROJECTILE` sind der normale Angriff, `ABILITY` ist es nicht.
  - **Der Tod** nutzt **beides** ab, und zwar **deutlich stärker** als gewöhnlicher Kampf.

  Dass Fähigkeiten die Waffe schonen, macht den Unterschied zwischen den beiden Werten überhaupt
  spürbar: ein Magier, der über Fähigkeiten kämpft, repariert seltener als ein Krieger, der
  zuschlägt.
- **Q6 · Ein NPC je Region, mit skalierendem Angebot.** Sechs Safe-Cores, sechs NPCs. Greenfields
  verkauft anderes als die Pale Wilds — Reisen lohnt sich, und niemand muss quer über die Karte, um
  einen Trank loszuwerden.
- **Q7 · Beute fällt auf den Boden, aber jeder sieht nur seine eigene.** Fremde Beute ist **weder
  sichtbar noch aufsammelbar**. Eigentümer ist derselbe, den B05 bereits über
  `CombatDeathEvent.lootRecipient()` benennt — der **größte Beitragende**, nicht der letzte Treffer.

### Session 2026-08-28, Teil 3 — was die Party-Frage aufdeckte

Beim Nachfassen fiel auf, dass **B06 ein gebautes Party-System besitzt** (`Party`, `PartyRegistry`,
`ShareCalculator`) und dass Teil 2 es vollständig übersehen hatte. B06 behandelt eine Party
ausdrücklich als **einen** Beitragenden und verteilt Erfahrung und Coins gleichmäßig auf die
Mitglieder in Reichweite, samt Nähe-Bonus — *„damit gemeinsames Spielen nicht schlechter ist als
allein zu spielen"*. Für Beute galt das nicht: `DamageShare.topContributor` stammt aus B05, und **B05
kennt keine Partys**. In einer festen Gruppe wäre jedes Item dauerhaft an denselben Spieler
gegangen, während sich Erfahrung und Coins teilen — der Tank hätte nie etwas bekommen.

- **Q8 · Die Party gilt auch für Beute als ein Beitragender, und der Gegenstand wandert reihum.**
  Gezählt werden die **Gegenstände**, nicht die Kills: Beute ist wahrscheinlichkeitsbehaftet, und
  eine Runde je Kill ließe die Runde dessen verfallen, dessen Gegner nichts fallen lässt. Nur
  Mitglieder **in Reichweite** sind in der Runde, gemessen wie in B06 zum gestorbenen Gegner. Der
  Reihenfolgezeiger ist Laufzeitzustand und vergeht mit der Party.
- **Q9 · Verschleiß bemisst sich am ankommenden Schaden, vor Abwehr.** Am durchgekommenen gemessen
  wäre eine Abwärtsspirale entstanden — verschlissene Rüstung lässt mehr durch, das nutzt sie
  schneller ab — und gute Rüstung wäre doppelt belohnt worden.
- **Q10 · Der Klon nutzt nichts ab**, weder durch ausgeteilten noch durch eingesteckten Schaden. Er
  ist selbst eine Fähigkeit, und Fähigkeiten schonen die Ausrüstung.
- **Q11 · Volles Inventar ändert nichts an der Beute.** Sie fällt, der Spieler wird gewarnt, sie
  verfällt nach Vanillas Frist. Genau der Fall, für den ADR-018 die Warnung vorgesehen hat.

#### Die eine Kollision, und wie sie aufgelöst wird

**Frei kaufbare Trims machen Ausrüstungsstufen optisch ununterscheidbar.** In der ausgelieferten
`classes.yml` ist der Trim für zwei der drei Klassen das **einzige** Unterscheidungsmerkmal:

| Klasse | Stufen | Unterschied |
|---|---|---|
| Schurke | 4, 5, 6 | dreimal `CHAINMAIL` — nur `COPPER/RIB` → `AMETHYST/SILENCE` → `NETHERITE/VEX` |
| Krieger | 5, 6 | zweimal `NETHERITE` — nur „ohne Trim" → `GOLD/SENTRY` |
| Magier | 1–6 | `LEATHER` mit unterschiedlicher **Farbe** — vom Trim unberührt |

B07 fordert in seiner FR-016, dass zwei Stufen derselben Leiter niemals gleich aussehen. Ein Trim,
den jeder jederzeit kaufen kann, hebelt das für Schurke und Krieger aus: ein Schurke auf Stufe 4
sähe aus wie einer auf Stufe 6.

**Aufgelöst so:** eine gekaufte Trimfarbe ist **erst auf der Höchststufe der Leiter anwendbar**
(Stufe 6, Level 60). Das entspricht der genannten Absicht — Kosmetik ist der Grind **nach** der
Höchststufe —, und es hält die Erkennbarkeit während der Progression vollständig intakt. „Nicht an
ein Level gebunden" gilt weiter für die Trimfarben untereinander: keine einzelne Farbe hat eine
eigene Levelhürde, sie kosten Coins und sonst nichts.

**Was die Alternative gekostet hätte:** Trims auf jeder Stufe erlauben hieße, Schurken- und
Kriegerleiter ein zweites Unterscheidungsmerkmal zu geben — also `classes.yml` und B07s
Erscheinungsbildvalidierung anzufassen, in einem Block, der B07 ausdrücklich nicht verändern soll
(FR-079). Das ist machbar, aber es ist eine Änderung an B07, nicht an B11.

### Entschieden durch ADR-027 *(2026-08-22)* — vor `/specify`

- **Raritätsstufen bleiben, aber nur als Etikett.** Acht Stufen von Common bis Special. Sie sagen,
  wie selten etwas ist, und beeinflussen keinen einzigen Wert. Ein epischer Trank heilt nicht mehr
  als ein gewöhnlicher; er ist seltener.
- **Der Roll-Mechanismus entfällt vollständig.** Kein Würfeln, keine Wertebereiche, keine Affixe.
  Zwei Tränke desselben Typs sind identisch.
- **Der NPC-Händler gehört zu B11.** B10 liefert die Entity-Technik, die er mitbenutzt; das macht
  ihn nicht zu einem Mob.
- **Preise stehen bei dem, der sie verlangt.** B08b führt den Kontostand, nicht den Katalog.
  Stufenkosten in `classes.yml`, Rangkosten in `abilities.yml`, Verkaufserlös, Reparatur- und
  Kosmetikpreis hier.

### Entschieden durch ADR-018 *(2026-08-21)*

- **Der Spieler kann nichts in die Welt werfen.** Mob-Drops sind davon unberührt.
- **Drei Wege, Dinge loszuwerden:** Enderchest zum Lagern, Verkauf an den NPC gegen Coins,
  Mülleimer-Befehl zum endgültigen Vernichten. Jeder dieser Wege fragt das Bindungsprädikat und
  weist Klassenausrüstung ab.
- **Volles Inventar:** Warnung als Title plus Sound. Kein automatisches Aufräumen, keine
  Hintergrundbank, kein stilles Verwerfen — der Spieler schafft selbst Platz.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ein Item übersteht alles, und Balancing wirkt rückwirkend (Priority: P1)

Ein Spieler hebt einen Trank auf, loggt aus, der Server startet neu, der Betreiber ändert die
Heilwirkung in der Konfiguration — und der Trank im Inventar heilt danach den neuen Betrag. Nichts
am Item wurde angefasst.

**Why this priority**: Das ist die Zusage aus ADR-004, und sie ist die einzige, die nachträglich
nicht mehr einzubauen wäre. Ein Item, das seine Endwerte gespeichert hat, ist nach dem Release nicht
mehr zu balancieren, ohne jedes Spielerinventar zu migrieren. Jede weitere Geschichte dieses Blocks
setzt auf dem Item als Datenobjekt auf.

**Independent Test**: Vollständig prüfbar, indem ein Item aus einer Vorlage erzeugt, gespeichert,
neu geladen und die Vorlage danach geändert wird — ohne dass ein NPC, eine Beutetabelle oder ein
Verbrauch existiert. Der Nutzen steht für sich: Inhalte sind ab hier balancierbar.

**Acceptance Scenarios**:

1. **Given** ein Item aus der Vorlage `potion.minor-healing`, **When** der Spieler sich neu anmeldet,
   **Then** trägt das Item unverändert die Vorlagen-ID und zeigt Namen, Lore und Werte, die aus der
   aktuellen Vorlage abgeleitet sind
2. **Given** ein Exemplar dieser Vorlage in einem Spielerinventar, **When** der Betreiber den
   Heilbetrag der Vorlage ändert und neu lädt, **Then** wirkt das vorhandene Exemplar mit dem neuen
   Betrag, ohne dass das Inventar angefasst wurde
3. **Given** ein Item, dessen gespeicherte Daten manipuliert wurden, **When** es geladen wird,
   **Then** wird die Manipulation erkannt und das Item abgelehnt statt übernommen
4. **Given** ein Item, dessen Vorlagen-ID in der Konfiguration nicht mehr existiert, **When** es
   geladen wird, **Then** bleibt das Item erhalten und inert, statt still zu verschwinden oder den
   Ladevorgang abzubrechen
5. **Given** eine Item-Schema-Version älter als die aktuelle, **When** ein solches Item geladen wird,
   **Then** wird es über den definierten Migrationspfad angehoben, verlustfrei

---

### User Story 2 - Aus Mobs fällt etwas, und es gehört mir allein (Priority: P2)

Ein Spieler tötet eine Kreatur in den Greenfields. Neben Erfahrung und Coins fällt gelegentlich ein
Trank vor seine Füße. Der Spieler neben ihm sieht davon nichts — und könnte auch nichts nehmen.

**Why this priority**: Beute ist der Grund, aus dem Items überhaupt in Spielerhand kommen. Ohne sie
ist US1 ein leeres Datenmodell. Die Eigentumsbindung gehört von Anfang an dazu: sie nachträglich
einzuziehen hieße, jede Beuteroute noch einmal anzufassen.

**Independent Test**: Prüfbar, indem zwei Spieler dieselbe Horde bekämpfen und jeder nur seine
eigene Ausbeute sieht — ohne NPC und ohne Verbrauchswirkung.

**Acceptance Scenarios**:

1. **Given** eine Art mit konfigurierter Beutetabelle, **When** ein Spieler sie tötet, **Then** fällt
   der konfigurierte Eintrag mit der konfigurierten Wahrscheinlichkeit und Stückzahl
2. **Given** zwei Spieler **ohne Party**, die dieselbe Kreatur bekämpft haben, **When** sie stirbt,
   **Then** gehört die Beute dem **größten Beitragenden** — auch wenn der andere den letzten Treffer
   gelandet hat
2a. **Given** eine Party aus drei Mitgliedern in Reichweite, **When** nacheinander drei Gegenstände
    fallen, **Then** bekommt jedes Mitglied genau einen — unabhängig davon, wer den Schaden gemacht
    hat
2b. **Given** dieselbe Party, **When** zwanzig Gegner sterben und dabei nur vier Gegenstände fallen,
    **Then** verteilen sich diese **vier** reihum — die Kills ohne Beute verbrauchen keine Runde
2c. **Given** eine Party, deren drittes Mitglied außer Reichweite steht, **When** ein Gegenstand
    fällt, **Then** geht er an das nächste Mitglied **in** Reichweite, und der Abwesende behält
    seine Position für den nächsten Gegenstand
2d. **Given** ein Mitglied, das die Party mitten im Kampf verlässt, **When** danach ein Gegenstand
    fällt, **Then** ist es nicht mehr in der Runde, und die übrigen rücken auf
3. **Given** dieselbe Lage, **When** der andere Spieler hinsieht, **Then** ist die Beute für ihn
   **nicht sichtbar**
4. **Given** dieselbe Lage, **When** der andere Spieler über die Stelle läuft, **Then** hebt er
   nichts auf — auch nicht zufällig, auch nicht durch Timing
5. **Given** ein Spieler mit Beute am Boden, **When** er sich neu anmeldet, **Then** sieht er sie
   wieder und kann sie aufheben — **unsichtbar aber aufsammelbar** ist ausgeschlossen
6. **Given** ein Spieler mit drei Charakteren und Beute am Boden, **When** er den Charakter wechselt,
   **Then** sieht und bekommt der neue Charakter sie **nicht** (ADR-011)
7. **Given** zwei Spieler mit gleichartiger Beute nebeneinander, **When** die Gegenstände sich
   berühren, **Then** verschmelzen sie **nicht** — niemand verliert seine an den anderen
8. **Given** eine Kreatur, **When** sie durch etwas anderes als einen Spieler stirbt — Sturz,
   Sonnenlicht, eine andere Kreatur —, **Then** fällt keine Beute
9. **Given** zwei Arten auf derselben Vanilla-Basis mit verschiedenen Tabellen, **When** beide
   getötet werden, **Then** unterscheidet sich die Ausbeute nach Art, nicht nach Basistyp
10. **Given** eine Art ohne eigene Beutetabelle, **When** sie getötet wird, **Then** greift die
    Tabelle ihrer Region, und kein Vanilla-Drop
11. **Given** ein Boss, **When** er getötet wird, **Then** fällt seine eigene Tabelle, deren Einträge
    seltener und höherwertig sind als die der Horde derselben Region
12. **Given** gefallene Beute, **When** ihr Eigentümer ausloggt oder die Verfallszeit abläuft,
    **Then** verschwindet sie, ohne für jemand anderen sichtbar zu werden

---

### User Story 3 - Verbrauchbares wirkt und ist begrenzt (Priority: P2)

Ein Spieler trinkt einen Heiltrank im Kampf. Er wirkt sofort, er ist danach verbraucht, und ein
zweiter Trank direkt hinterher ist nicht der Weg, jeden Kampf zu gewinnen.

**Why this priority**: Verbrauchbares ist die einzige Kategorie in diesem Block, die aktiv ins
Spielgeschehen eingreift. Sie hängt an B04 über `SourceKind.BUFF` und ist damit auch der Beweis,
dass die vorhandene Naht trägt.

**Independent Test**: Prüfbar über Trinken und Messen des Effekts, ohne dass die Beute existiert —
das Item lässt sich per Befehl vergeben.

**Acceptance Scenarios**:

1. **Given** ein Heiltrank, **When** der Spieler ihn benutzt, **Then** steigt seine Lebensenergie um
   den in der Vorlage stehenden Betrag, gedeckelt auf sein Maximum, und ein Exemplar ist verbraucht
2. **Given** ein Trank mit zeitlicher Wirkung, **When** der Spieler ihn benutzt, **Then** trägt der
   Effekt über `SourceKind.BUFF` bei und endet nach der konfigurierten Dauer — zeitstempelbasiert
   ausgewertet, nicht über eine wiederkehrende Aufgabe (Prinzip II)
3. **Given** ein Trank mit Abklingzeit, **When** der Spieler innerhalb der Abklingzeit einen zweiten
   benutzt, **Then** wird die Benutzung abgelehnt und nichts verbraucht
4. **Given** ein Verbrauchbares mit Mindestlevel oder Klassenbindung, **When** ein Charakter es
   benutzt, der die Bedingung nicht erfüllt, **Then** wird die Benutzung abgelehnt und nichts
   verbraucht
5. **Given** ein Spieler mit vollem Leben, **When** er einen reinen Heiltrank benutzt, **Then** wird
   die Benutzung abgelehnt, statt ihn wirkungslos zu verbrauchen

---

### User Story 4 - Der NPC macht aus Beute Coins und aus Coins die nächste Stufe (Priority: P2)

Ein Spieler geht im Safe-Core seiner Region zum NPC. Dort verkauft er, was er nicht braucht, kauft,
was die Region führt, lässt seine Ausrüstung reparieren — und kauft die nächste Ausrüstungsstufe,
sobald sein Level reicht und er die Coins hat. Seine Klassenrüstung kann er ihm nicht andrehen.

**Why this priority**: Der NPC schließt den Kreislauf, den B08b geöffnet hat: Beute wird zu Coins,
Coins bezahlen die nächste Stufe. Er ist der **Ort**, an dem das Vorhandene zusammenkommt — der Kauf
selbst existiert bereits in `EquipmentPurchase`, ihm fehlt nur der Weg dorthin.

**Independent Test**: Prüfbar über Verkaufen, Kaufen und Aufsteigen gegen einen bekannten
Kontostand, ohne dass Beute existiert — Items lassen sich per Befehl vergeben.

**Acceptance Scenarios**:

1. **Given** ein Spieler mit einem verkäuflichen Item, **When** er es dem NPC verkauft, **Then**
   verschwindet das Item und der Kontostand steigt um den konfigurierten Erlös, gebucht unter
   `VENDOR_SALE`
2. **Given** ein Spieler, der seine Klassenrüstung oder -waffe verkaufen will, **When** er es
   versucht, **Then** wird der Vorgang abgelehnt, das Item bleibt am Charakter, und der Kontostand
   bleibt unverändert
3. **Given** die NPCs zweier Regionen, **When** ein Spieler beide besucht, **Then** unterscheidet
   sich ihr Verkaufsbestand — jede Region führt ihren eigenen
4. **Given** ein Spieler mit ausreichendem Level und ausreichenden Coins, **When** er beim NPC die
   nächste Ausrüstungsstufe kauft, **Then** steigt seine Leiter um genau eine Stufe, die Coins sind
   gebucht, und er trägt sofort das neue Aussehen
5. **Given** ein Spieler, dessen **Level** für die nächste Stufe nicht reicht, **When** er sie kaufen
   will, **Then** wird der Kauf abgelehnt und **keine** Coins werden gebucht — die Reihenfolge ist
   die Anforderung, nicht der Nebeneffekt
6. **Given** ein Spieler mit zu wenig Coins, **When** er etwas kaufen oder aufsteigen will, **Then**
   wird der Vorgang abgelehnt, und weder Kontostand noch Leiter ändern sich
7. **Given** ein Spieler mit vollem Inventar, **When** er ein Item kauft, **Then** wird der Kauf
   abgelehnt, **bevor** eine Buchung stattfindet — ein bezahltes Item, das nirgends hinpasst, ist
   der schlechteste mögliche Ausgang
8. **Given** ein Spieler mit geöffnetem NPC-Fenster, **When** er die Zone verlässt, ausloggt oder der
   Server herunterfährt, **Then** schließt das Fenster, ohne dass ein halb abgeschlossener Vorgang
   zurückbleibt

---

### User Story 5 - Ausrüstung nutzt sich ab und wird schwächer, nicht kaputt (Priority: P2)

Ein Spieler kämpft. Jeder Treffer, den er einsteckt, nagt an seiner Rüstung; jeder Zuschlag nagt an
seiner Waffe — Fähigkeiten dagegen schonen sie. Ein Tod kostet ein Vielfaches davon, auf beiden
Werten. Je weiter der Verschleiß fortschreitet, desto weniger trägt die Ausrüstung bei — bei null
bleibt ein Fünftel ihrer Wirkung übrig. Zerbrechen kann sie nie. Repariert wird beim NPC, je Slot
einzeln.

**Why this priority**: Die Todesstrafe ist seit ADR-017 die einzige, die dieses Projekt kennt, und
sie hängt an diesem Block. Der Verschleiß ist zugleich die laufende Coin-Senke, ohne die Coins nur
hereinkommen und nie abfließen.

**Independent Test**: Prüfbar über Schaden nehmen, Schaden austeilen, Sterben und Messen der Werte,
dann Reparieren gegen einen bekannten Kontostand.

**Acceptance Scenarios**:

1. **Given** ein Spieler mit unverschlissener Ausrüstung, **When** er Schaden erleidet, **Then**
   sinkt der Zustand **seiner Rüstung** um den konfigurierten Anteil, der seiner Waffe nicht
2. **Given** derselbe Spieler, **When** er mit einem **Autoattack** Schaden austeilt, **Then** sinkt
   der Zustand **seiner Waffe**, der seiner Rüstung nicht
3. **Given** derselbe Spieler, **When** er Schaden über eine **Fähigkeit** austeilt, **Then** ändert
   sich **kein** Zustandswert — Fähigkeiten schonen die Waffe
3a. **Given** ein Spieler mit beschworenem Klon, **When** der Klon Schaden austeilt oder einsteckt,
    **Then** ändert sich **kein** Zustandswert des Beschwörers
3b. **Given** zwei Spieler mit unterschiedlich guter Rüstung, **When** beide denselben Treffer
    einstecken, **Then** verschleißt ihre Rüstung **gleich stark** — gemessen wird der ankommende
    Schaden, nicht der durchgekommene
4. **Given** ein Spieler, **When** er stirbt, **Then** verliert er kein Item und keine Erfahrung, und
   **beide** Zustandswerte sinken um den Todesbetrag
5. **Given** dieselbe Konfiguration, **When** man den Todesbetrag gegen den Verschleiß eines ganzen
   Kampfes hält, **Then** ist der Tod **um ein Vielfaches teurer**, und der Start weist eine
   Konfiguration zurück, in der das nicht mehr gilt
6. **Given** Ausrüstung oberhalb der Verschleißschwelle, **When** die Werte berechnet werden,
   **Then** trägt sie ihren **vollen** Stufenwert bei — leichter Verschleiß kostet nichts
7. **Given** Ausrüstung bei **Zustand 0**, **When** die Werte berechnet werden, **Then** trägt sie
   noch **20 %** ihres Stufenwerts bei, und sie ist **nicht** zerstört
8. **Given** Ausrüstung zwischen Schwelle und null, **When** die Werte berechnet werden, **Then**
   liegt ihr Beitrag zwischen diesen beiden Enden und sinkt stetig mit dem Zustand
9. **Given** ein Spieler, dessen Zustand eine Warnschwelle unterschreitet, **When** das geschieht,
   **Then** bekommt er eine Meldung mit der Aufforderung, zu reparieren — höchstens einmal je
   Schwelle und Ruhezeit, nicht bei jedem Treffer
10. **Given** ein Spieler mit verschlissener Rüstung und heiler Waffe, **When** er beim NPC nur die
    Rüstung repariert, **Then** ist deren voller Beitrag zurück, die Waffe unverändert, und gebucht
    wurde nur der Rüstungspreis unter `REPAIR`
11. **Given** ein Spieler mit zu wenig Coins für die Reparatur, **When** er es versucht, **Then**
    wird sie abgelehnt, und weder Zustand noch Kontostand ändern sich

---

### User Story 6 - Auf Stufe 60 gibt es noch etwas zu holen (Priority: P3)

Ein Spieler hat Level 60 und die höchste Ausrüstungsstufe erreicht. Ab hier sammelt er Coins für
Trimfarben — rein optisch, ohne Wertwirkung, und jede sieht anders aus als die nächste.

**Why this priority**: Ohne diese Geschichte endet das Spiel an dem Tag, an dem die Leiter oben
ankommt. Sie ist bewusst nach dem Verschleiß einsortiert, weil sie nichts trägt außer sich selbst:
fällt sie aus, funktioniert alles andere unverändert.

**Independent Test**: Prüfbar über Kaufen und Anlegen einer Trimfarbe auf der Höchststufe, ohne dass
Beute oder Verbrauchbares existieren.

**Acceptance Scenarios**:

1. **Given** ein Spieler auf der Höchststufe seiner Leiter, **When** er eine Trimfarbe kauft und
   anwendet, **Then** ändert sich das Aussehen seiner Rüstung, und **kein** Wert ändert sich
2. **Given** derselbe Spieler, **When** er eine andere gekaufte Trimfarbe anwendet, **Then** ersetzt
   sie die vorige — es sammelt sich keine zweite an
3. **Given** ein Spieler **unterhalb** der Höchststufe, **When** er eine Trimfarbe anwenden will,
   **Then** wird es abgelehnt mit dem Hinweis auf die Höchststufe; gekauft hat er sie trotzdem
   behalten
4. **Given** ein Spieler mit angewandter Trimfarbe, **When** er ausloggt und sich neu anmeldet,
   **Then** trägt er sie unverändert
5. **Given** ein Spieler mit angewandter Trimfarbe, **When** er die Klasse wechselt oder der
   Charakter gelöscht wird, **Then** gilt die Wahl nicht für den anderen Charakter — Kosmetik hängt
   am Charakter wie alles andere (ADR-011)

---

### User Story 7 - Etwas loswerden, ohne es in die Welt zu werfen (Priority: P3)

Ein Spieler hat ein volles Inventar. Er bekommt eine sichtbare Warnung, und er hat drei Wege, Platz
zu schaffen: einlagern, verkaufen, vernichten.

**Why this priority**: Ohne Wurfmöglichkeit (ADR-018) ist ein volles Inventar eine Sackgasse. Die
Warnung ist billig, der Mülleimer ist der letzte Ausweg. Beides hängt an NPC und Enderchest und
kommt deshalb hier.

**Independent Test**: Prüfbar über Auffüllen eines Inventars und Beobachten von Warnung und den drei
Wegen.

**Acceptance Scenarios**:

1. **Given** ein Spieler, dessen Inventar voll ist, **When** ihm Beute zufallen würde, **Then**
   erscheint eine Warnung als Title plus Sound, und nichts wird still verworfen
2. **Given** dieselbe Lage, **When** die Warnung bereits erschienen ist, **Then** wiederholt sie sich
   nicht bei jedem weiteren Aufsammelversuch, sondern frühestens nach einer konfigurierten Ruhezeit
3. **Given** ein Spieler mit einem Item, **When** er den Mülleimer-Befehl darauf anwendet, **Then**
   wird es nach einer Bestätigung endgültig vernichtet
4. **Given** ein Spieler mit Klassenausrüstung, **When** er sie in die Enderchest legen oder in den
   Mülleimer geben will, **Then** wird der Vorgang abgelehnt
5. **Given** ein Spieler mit drei Charakteren, **When** er den Charakter wechselt, **Then** sieht er
   die Enderchest des neuen Charakters und nicht die des vorigen

---

### Edge Cases

- **Eine Vorlage verschwindet aus der Konfiguration, während Exemplare existieren.** Das Item bleibt
  erhalten und inert — es lässt sich tragen und vernichten, aber nicht benutzen und nicht verkaufen.
  Stilles Löschen wäre Datenverlust ohne Ansage; ein Startabbruch wäre die Strafe für den Betreiber
  statt für den Fehler.
- **Ein Item wird gestapelt, dessen Vorlage sich zwischen den beiden Exemplaren geändert hat.**
  Beide tragen dieselbe Vorlagen-ID, also stapeln sie und leiten dieselben Werte ab.
- **Niemand hat der Kreatur nennenswert geschadet** — sie stirbt an Fallschaden nach einem einzigen
  Treffer. `DamageShare` kann leer sein; dann gibt es keinen Eigentümer und keine Beute.
- **Der größte Beitragende hat den Server verlassen, bevor die Kreatur stirbt.** Die Beute fällt
  ihm zu und verschwindet ungesehen — sie geht nicht an den Zweitplatzierten über.
- **Die Party löst sich auf, während Beute am Boden liegt.** Der Anspruch ist beim Fallen vergeben
  worden und bleibt bestehen; die Auflösung nimmt niemandem, was ihm schon gehört.
- **Eine Party, deren Mitglieder alle außer Reichweite stehen.** Der Anspruch fällt auf den größten
  Beitragenden zurück (FR-026c) — dieselbe Regel wie ohne Party, statt die Beute verfallen zu lassen.
- **Ein Spieler ohne Party neben einer Party.** Er ist ein eigener Beitragender und konkurriert mit
  der Party als Ganzem, genau wie bei Erfahrung und Coins.
- **Das Inventar des Empfängers ist voll, wenn die Beute fällt.** Sie liegt trotzdem da, er wird
  gewarnt, und sie verfällt nach Vanillas Frist wie jede andere. Das ist genau der Fall, für den
  ADR-018 die Warnung vorgesehen hat: der Spieler schafft selbst Platz, und niemand hält ihm
  stillschweigend etwas zurück.
- **Ein Mob stirbt, während die Zone gerade aufgeräumt wird.** Ein Entfernen ist kein Tod (B10,
  `RemovalIsNotADeathTest`) — es fällt nichts.
- **Beute fällt in einen Bereich, den der Eigentümer nicht mehr sieht** — etwa hinter einer
  Zonengrenze. Sie unterliegt derselben Verfallszeit und verschwindet ungesehen.
- **Ein Item liegt in der Welt, wenn der Server herunterfährt.** Es unterliegt Vanillas Regeln. B11
  führt keine eigene Buchhaltung über liegende Items.
- **Der NPC wird angegriffen oder stirbt.** Er ist unverwundbar und zählt nicht gegen das
  Mob-Budget aus B10.
- **Zwei Fenster desselben Spielers.** Ein Spieler hat höchstens ein NPC-Fenster offen; ein zweites
  ersetzt das erste, statt danebenzustehen.
- **Der Kontostand ändert sich, während das Fenster offen ist** — etwa durch einen Mob-Drop. Der
  Preis wird beim Abschluss geprüft, nicht beim Öffnen.
- **Ein Spieler steigt auf, während sein Fenster offen ist.** Der Stufenkauf prüft Level und Coins
  beim Abschluss; ein beim Öffnen angezeigter Preis ist eine Anzeige, keine Zusage.
- **Verschleiß im Safe-Core.** Dort nimmt niemand Schaden (B09), also entsteht dort auch kein
  Verschleiß — es braucht keine eigene Ausnahme.
- **Ein Spieler stirbt durch `/kill`.** Kein Verschleiß: `DeathCause.ADMIN` ist ausgenommen, sonst
  wäre ein Administratoreingriff eine Strafe für den Spieler.
- **Reparatur eines Slots, der nicht verschlissen ist.** Wird abgelehnt, ohne zu buchen.
- **Aufstieg auf der Höchststufe.** Wird abgelehnt, ohne zu buchen — das prüft `TierAdvance` bereits.
- **Ein Stufenaufstieg bei verschlissener Ausrüstung.** Der Zustand des Slots bleibt, wie er war —
  eine neue Stufe ist keine Reparatur, sonst wäre der Aufstieg der billigere Weg zur Instandsetzung.
- **Eine gekaufte Trimfarbe, die die Konfiguration nicht mehr kennt.** Die Rüstung fällt auf das
  Aussehen ihrer Stufe zurück; der Besitz bleibt vermerkt, falls die Farbe zurückkehrt.
- **Ein Charakter wird gelöscht.** Seine Items, seine Enderchest, sein Zustand und seine Kosmetik
  verschwinden mit ihm und erben sich nicht auf einen neuen Charakter desselben Spielers.

---

## Requirements *(mandatory)*

### Das Item als Datenobjekt

- **FR-001**: Ein Item MUSS ausschließlich seine **Vorlagen-ID** und die **Schema-Version**
  gespeichert tragen. Berechnete Endwerte und gerendertes Lore werden NIEMALS gespeichert.
- **FR-002**: Name, Lore, Attributbeiträge und Wirkung eines Items MÜSSEN bei jedem Laden aus der
  aktuellen Vorlage neu abgeleitet werden.
- **FR-003**: Eine Änderung an einer Vorlage MUSS nach einem Reload auf jedes vorhandene Exemplar
  wirken, ohne dass ein Spielerinventar angefasst wird.
- **FR-004**: Die Speicherung MUSS über den **PersistentDataContainer** erfolgen. Lore-Parsing als
  Datenquelle ist unzulässig.
- **FR-005**: Das Item-Schema MUSS versioniert sein und einen definierten Migrationspfad besitzen;
  eine ältere Version wird beim Laden verlustfrei angehoben.
- **FR-006**: Das Schema MUSS ein Feld für **Custom-Model-Data** vorhalten, auch wenn es im
  Vanilla-Betrieb ungenutzt bleibt (ADR-005).
- **FR-007**: Ein Item mit unbekannter Vorlagen-ID MUSS erhalten bleiben und inert sein — tragbar und
  vernichtbar, nicht benutzbar und nicht verkäuflich. Es wird weder still gelöscht noch bricht es
  den Ladevorgang ab.
- **FR-008**: Manipulierte Item-Daten aus dem Client MÜSSEN serverseitig erkannt und abgelehnt
  werden (Prinzip VI).
- **FR-009**: Unterschiedliche Items MÜSSEN sich über unterschiedliche **Vanilla-Materialien**
  unterscheiden, solange kein Resource Pack existiert.
- **FR-010**: Jedes Item MUSS **feste** Attributwerte aus seiner Vorlage tragen. Es gibt keine
  Wertebereiche, kein Würfeln und keine Affixe (ADR-027).

### Vorlagen und Kategorien

- **FR-011**: Item-Vorlagen MÜSSEN vollständig in versionierter Konfiguration liegen. Eine neue
  Vorlage derselben Kategorie DARF KEINE Codeänderung erfordern (Prinzip V).
- **FR-012**: Die Konfiguration MUSS beim Start gegen ein Schema validiert werden; ein Fehler führt
  zu **Fail-Fast** mit einer Meldung, die die fehlerhafte Stelle benennt.
- **FR-013**: Kein Bezeichner einer einzelnen Vorlage DARF im Code vorkommen — dieselbe Zusage, die
  B10 für Mob-Arten gibt.
- **FR-014**: Eine Vorlage MUSS eine **Raritätsstufe** aus den acht festgelegten tragen. Rarität ist
  ausschließlich ein Etikett: Farbe im Namen und Sortierhinweis, **ohne jede Wertwirkung**.
- **FR-015**: Eine Vorlage KANN ein **Mindestlevel** und eine **Klassenbindung** fordern. Eine
  Benutzung durch einen Charakter, der die Bedingung nicht erfüllt, wird abgelehnt.
- **FR-016**: Eine Vorlage MUSS einen **Verkaufserlös** tragen. Ein Item ohne Erlös ist unverkäuflich
  und wird vom NPC abgewiesen.
- **FR-017**: Es gibt **zwei** Kategorien: **Verbrauchbares** und **Kosmetik**. Eine Kategorie
  „Aufstiegsmaterial" existiert NICHT — der Aufstieg wird mit Level und Coins bezahlt (Q1).

### Beute: was fällt

- **FR-018**: Beutetabellen MÜSSEN **je Mob-Art** konfigurierbar sein; eine Art ohne eigene Tabelle
  erbt die Tabelle **ihrer Region**.
- **FR-019**: Beute DARF NUR fallen, wenn die Kreatur **durch einen Spieler** getötet wurde. Tod
  durch Umgebung, durch eine andere Kreatur oder durch das Aufräumen einer Zone lässt nichts fallen.
- **FR-020**: Zwei Arten auf derselben Vanilla-Basis MÜSSEN nach **Art** unterschiedliche Beute
  lassen, nicht nach Basistyp — dieselbe Zusage wie FR-004 in B10.
- **FR-021**: Ein Tabelleneintrag MUSS eine Wahrscheinlichkeit und eine Stückzahlspanne tragen. Die
  Stückzahl ist der **einzige** Zufall in diesem Block; die Werte des gefallenen Items sind fest.
- **FR-022**: Vanillas eigene Drops MÜSSEN unterdrückt werden. Was eine Kreatur fallen lässt, steht
  vollständig in ihrer Tabelle — dieselbe Begründung, aus der B10 Vanillas Spawner unterdrückt.
- **FR-023**: Bosse MÜSSEN eine **eigene** Tabelle tragen, getrennt von der ihrer Region.
- **FR-024**: Beute DARF NIEMALS Klassenausrüstung enthalten (ADR-017). Eine Tabelle, die es
  versucht, ist ein Konfigurationsfehler und führt zu Fail-Fast.
- **FR-025**: Das Fallenlassen von Beute DARF das Mob-Budget aus B10 nicht berühren.

### Beute: wem sie gehört

- **FR-026**: Gefallene Beute MUSS **genau einem** Empfänger gehören. **Außerhalb einer Party** ist
  das der **größte Beitragende** aus `CombatDeathEvent.lootRecipient()` — **nicht** der letzte
  Treffer und **nicht** anteilig verteilt. Erfahrung und Coins teilen sich nach Anteil, weil sie
  teilbar sind; ein Gegenstand ist es nicht (B05, ADR-029).
- **FR-026a**: Ist der größte Beitragende Mitglied einer **Party**, MUSS die Party als **ein**
  Beitragender gelten — dieselbe Regel, die B06 für Erfahrung und Coins anwendet
  (`ShareCalculator`, Schritt 3). Der Anspruch fällt dann der Party zu, nicht dem einzelnen
  Mitglied.
- **FR-026b**: Der Anspruch einer Party MUSS **reihum je gefallenem Gegenstand** an ihre Mitglieder
  **in Reichweite** gehen — nicht je getötetem Gegner. Beute ist wahrscheinlichkeitsbehaftet
  (FR-021); eine Runde je Kill ließe die Runde dessen verfallen, dessen Gegner nichts fallen lässt.
  Fallen bei einem Tod mehrere Gegenstände, gehen sie an aufeinanderfolgende Mitglieder.
- **FR-026c**: Ein Mitglied **außerhalb der Reichweite** MUSS übersprungen werden, ohne seine
  Position in der Reihenfolge zu verlieren — gemessen wie in B06 zum **gestorbenen Gegner**, dem
  einzigen gemeinsamen Bezugspunkt. Ist kein Mitglied in Reichweite, fällt der Anspruch auf den
  größten Beitragenden aus FR-026 zurück.
- **FR-026d**: Der Reihenfolgezeiger MUSS **Laufzeitzustand der Party** sein und mit ihr vergehen.
  Die Party wird laut B06 nicht persistiert; ein persistierter Zeiger wäre der einzige Teil von ihr,
  der einen Neustart überlebt.
- **FR-027**: Der Anspruch MUSS am **Charakter** hängen, nicht am Spieler (ADR-011). Ein Spieler mit
  drei Charakteren bekommt mit dem zweiten nicht, was der erste erbeutet hat.
- **FR-028**: Gefallene Beute DARF **ausschließlich für ihren Eigentümer sichtbar** sein. Für jeden
  anderen Spieler existiert sie nicht — ein sichtbarer Gegenstand, den man nicht aufheben kann,
  sieht aus wie ein Fehler.
- **FR-029**: Gefallene Beute DARF **ausschließlich von ihrem Eigentümer** aufgehoben werden, und
  zwar durch ein **vom Sehen unabhängiges Schloss**. Unsichtbarkeit ist Darstellung, und Darstellung
  ist niemals die Autorität (Prinzip VI).
- **FR-030**: Nach einem Relogin oder Charakterwechsel MUSS die Sichtbarkeit der eigenen Beute
  **wiederhergestellt** werden. Sichtbarkeit hängt an der Verbindung, die Schlösser nicht —
  „unsichtbar aber aufsammelbar" ist der schlechteste Zustand von beiden.
- **FR-031**: Gefallene Beute zweier verschiedener Eigentümer DARF **niemals verschmelzen**, auch
  nicht bei gleicher Vorlage. Sonst wechselt Besitz durch bloße Nähe.
- **FR-032**: Beute MUSS über Vanillas Verfall verschwinden. Dieser Block DARF dafür **keine
  wiederkehrende Aufgabe** einrichten — weder je Gegenstand noch als Sammelvorgang (Prinzip II).

### Verbrauchbares

- **FR-033**: Ein Verbrauchbares MUSS bei Benutzung genau ein Exemplar verbrauchen und seine Wirkung
  sofort anwenden.
- **FR-034**: Eine zeitliche Wirkung MUSS über `SourceKind.BUFF` in die Werteberechnung eingehen und
  **zeitstempelbasiert lazy** auslaufen — keine wiederkehrende Aufgabe je Spieler (Prinzip II).
- **FR-035**: Ein Verbrauchbares KANN eine **Abklingzeit** tragen. Eine Benutzung innerhalb der
  Abklingzeit wird abgelehnt, ohne zu verbrauchen.
- **FR-036**: Eine Benutzung, die **nichts bewirken würde** — Heilung bei vollem Leben —, MUSS
  abgelehnt werden, statt das Exemplar wirkungslos zu verbrauchen.
- **FR-037**: Jede abgelehnte Benutzung MUSS dem Spieler mit einer Begründung über einen
  Message-Schlüssel gemeldet werden (Prinzip V).

### Verschleiß: woher er kommt

- **FR-038**: Klassenausrüstung MUSS **unzerstörbar** bleiben. Der Verschleiß ist ein **Wert am
  Charakter**, niemals die Haltbarkeit des ItemStacks — B07 hat sie ausdrücklich auf
  `setUnbreakable(true)` gesetzt, und diese Spec hebt das nicht auf.
- **FR-039**: Es MUSS **zwei** Zustandswerte je Charakter geben, getrennt nach `LadderSlot.ARMOR`
  und `LadderSlot.WEAPON` (Q5).
- **FR-040**: Der Zustand der **Rüstung** MUSS durch **erlittenen** Schaden sinken — anteilig zum
  **ankommenden Schaden vor Abwehr**, nicht zu dem, was danach übrig bleibt. Herkunft egal, mit
  Ausnahme von `DamageOrigin.ADMIN`.
- **FR-040a**: Die Bemessung vor Abwehr ist **die Anforderung, nicht eine Feinheit**. Am
  durchgekommenen Schaden gemessen entstünde eine Abwärtsspirale — verschlissene Rüstung lässt mehr
  durch, das nutzt sie schneller ab, das lässt noch mehr durch — und gute Rüstung wäre doppelt
  belohnt. Vor Abwehr bleibt die Verschleißrate von der Rüstungsgüte unabhängig.
- **FR-041**: Der Zustand der **Waffe** MUSS durch **ausgeteilten Schaden aus einem Autoattack**
  sinken — `DamageOrigin.MELEE` und `PROJECTILE`. Schaden aus `DamageOrigin.ABILITY` DARF **keinen**
  Zustandswert verändern.
- **FR-041a**: Ein **beschworener Klon** (B08 `SummonEffect`) DARF **keinen** Zustandswert seines
  Beschwörers verändern — weder durch den Schaden, den er austeilt, noch durch den, den er
  einsteckt. Er ist eine eigene Kreatur mit einer Momentaufnahme der Werte, und er ist selbst eine
  Fähigkeit; dass Fähigkeiten die Ausrüstung schonen, gilt für ihn wie für jede andere.
- **FR-042**: Der **Tod** MUSS **beide** Zustandswerte um einen eigenen, festen Betrag senken. Ein
  Tod durch `DeathCause.ADMIN` ist ausgenommen.
- **FR-043**: Der Todesbetrag MUSS **um ein Vielfaches über** dem Verschleiß eines gewöhnlichen
  Kampfes liegen. Vorgabe: **10 Zustandspunkte je Tod** gegen **0,01 Punkte je Schadenspunkt** — ein
  Sterben wiegt damit tausend Schadenspunkte auf, und zehn Tode führen von voll auf null.
- **FR-044**: Der Start MUSS eine Konfiguration **zurückweisen**, in der der Todesbetrag nicht
  wenigstens um den konfigurierten Faktor über den Schadensraten liegt (Fail-Fast, Prinzip V). „Ein
  Tod wiegt schwerer als viele Treffer" ist eine Regel, keine Zahlenwahl, und darf sich nicht durch
  eine Konfigurationsänderung stillschweigend umkehren.
- **FR-045**: Alle Raten, Beträge und Schwellen MÜSSEN konfigurierbar sein (Prinzip V).
- **FR-046**: Der Tod DARF **kein Item und keine Erfahrung** kosten (ADR-017).

### Verschleiß: was er bewirkt, und wie man ihn los wird

- **FR-047**: Der **Beitrag** einer Ausrüstung MUSS mit sinkendem Zustand abnehmen: oberhalb einer
  konfigurierten **Verschleißschwelle** voll, darunter fallend bis auf einen konfigurierten
  **Restanteil** bei Zustand null. Vorgabewerte: Schwelle **50 %**, Restanteil **20 %** — also bis
  zu **80 % Wirkungsverlust** (Q3).
- **FR-048**: Die Abnahme MUSS **stetig** sein, nicht sprunghaft: zwei Zustandswerte zwischen
  Schwelle und null ergeben nie denselben Beitrag.
- **FR-049**: Der Verschleiß DARF ausschließlich den **Ausrüstungsbeitrag des betroffenen Slots**
  mindern — niemals Grundwerte der Klasse, Levelwachstum, Buffs oder Zonenwirkungen, und niemals den
  jeweils anderen Slot.
- **FR-050**: Der Zustand MUSS für den Spieler **ablesbar** sein. Die Anzeige wird wie Name und Lore
  bei jedem Laden **abgeleitet** (FR-002) und ändert nichts an der Unzerstörbarkeit.
- **FR-051**: Beim Unterschreiten einer konfigurierten **Warnschwelle** MUSS der Spieler eine Meldung
  mit der Aufforderung zur Reparatur erhalten — höchstens einmal je Schwelle und Ruhezeit.
- **FR-052**: Reparatur MUSS **je Slot** gegen Coins möglich sein, gebucht unter `REPAIR`, und den
  vollen Beitrag dieses Slots wiederherstellen.
- **FR-053**: Der Reparaturpreis MUSS mit dem **fehlenden Anteil** steigen, damit eine Reparatur bei
  99 % nicht dasselbe kostet wie eine bei 5 %.
- **FR-054**: Eine Reparatur, die abgelehnt wird — zu wenig Coins, nichts zu reparieren —, DARF
  weder Zustand noch Kontostand verändern.
- **FR-055**: Ein Stufenaufstieg DARF den Zustand eines Slots **nicht** zurücksetzen. Sonst wäre der
  Aufstieg der billigere Weg zur Reparatur.
- **FR-056**: Vanillas Amboss- und Verzauberungswege MÜSSEN gesperrt bleiben, damit die Coin-Route
  die einzige Instandsetzung ist.

### Der NPC: Händler, Upgrader und Werkstatt

- **FR-057**: Es MUSS **je Region einen** NPC im **Safe-Core** geben — sechs insgesamt —, der
  ankauft, verkauft, repariert und den Stufenaufstieg durchführt (Q6).
- **FR-058**: Der **Verkaufsbestand** MUSS je Region konfigurierbar sein und sich zwischen den
  Regionen unterscheiden dürfen. Ankaufserlöse folgen weiterhin der Vorlage (FR-016).
- **FR-059**: Der NPC MUSS unverwundbar sein und DARF **nicht** gegen das Mob-Budget aus B10 zählen.
- **FR-060**: Verkauf MUSS den konfigurierten Erlös unter `VENDOR_SALE` gutschreiben und das
  Item entfernen.
- **FR-061**: Der Stufenaufstieg am NPC MUSS **Mindestlevel und Coins** prüfen und dafür die
  vorhandene Kaufroute aus B08b (`EquipmentPurchase`) benutzen. Ein zweiter Kaufmechanismus ist
  unzulässig.
- **FR-062**: Ein abgelehnter Aufstieg — Level zu niedrig, Höchststufe erreicht, zu wenig Coins —
  DARF **keine** Coins bewegen und die Leiter nicht verändern.
- **FR-063**: Jeder Verkaufs-, Lager- und Vernichtungsweg MUSS das Bindungsprädikat aus B07
  (`BoundEquipment`) fragen und **Klassenausrüstung abweisen** (ADR-018).
- **FR-064**: Kauf MUSS **vor jeder Buchung** prüfen, ob Kontostand **und** Platz im Inventar
  reichen. Eine Buchung ohne Warenübergabe ist unzulässig.
- **FR-065**: Ein abgebrochener Vorgang — Logout, Zonenwechsel, Serverstopp, geschlossenes Fenster —
  MUSS entweder vollständig oder gar nicht gebucht sein.
- **FR-066**: Ein Spieler MUSS höchstens **ein** NPC-Fenster offen haben.
- **FR-067**: Der NPC DARF **keinen** Spieler-zu-Spieler-Handel ermöglichen (Nicht-Ziel,
  `00-vision-scope.md`).

### Kosmetik

- **FR-068**: Trimfarben MÜSSEN beim NPC gegen Coins käuflich sein. Eine einzelne Trimfarbe hat
  **keine eigene Levelvoraussetzung** (Q2).
- **FR-069**: Eine gekaufte Trimfarbe DARF **erst auf der Höchststufe der Leiter** angewandt werden.
  Unterhalb bleibt das Aussehen der Stufe maßgeblich, damit B07s FR-016 — zwei Stufen sehen nie
  gleich aus — für Schurke und Krieger nicht ausgehebelt wird.
- **FR-070**: Eine angewandte Trimfarbe DARF **keinen** Wert verändern. Sie ist ausschließlich
  Aussehen.
- **FR-071**: Ein Charakter MUSS höchstens **eine** Trimfarbe angewandt haben; eine weitere ersetzt
  die vorige.
- **FR-072**: Besitz und Anwendung von Kosmetik MÜSSEN am **Charakter** hängen, nicht am Spieler
  (ADR-011), und einen Neustart überstehen.
- **FR-073**: Eine angewandte Trimfarbe, die die Konfiguration nicht mehr kennt, MUSS auf das
  Aussehen der Stufe zurückfallen, ohne den Besitzvermerk zu verlieren.

### Lagern, Warnen, Vernichten

- **FR-074**: Die Enderchest MUSS je **Charakter** geführt werden, nicht je Spieler — die vorhandene
  Naht aus B03 (`CharacterInventory`) wird benutzt, keine zweite eingeführt.
- **FR-075**: Ein volles Inventar MUSS eine Warnung als **Title plus Sound** auslösen. Kein
  automatisches Aufräumen, keine Hintergrundbank, kein stilles Verwerfen (ADR-018).
- **FR-076**: Die Warnung MUSS eine konfigurierbare Ruhezeit haben, damit sie sich nicht bei jedem
  Aufsammelversuch wiederholt.
- **FR-077**: Die Ausgabe MUSS hinter der B13-Schnittstelle laufen (ADR-005) und alle Texte über
  Message-Schlüssel beziehen.
- **FR-078**: Ein **Mülleimer-Befehl** MUSS ein Item nach einer Bestätigung endgültig vernichten.
  Ohne Bestätigung geschieht nichts.

### Abgrenzung

- **FR-079**: Dieser Block DARF die Klassenleitern aus B07 nicht verändern, keine zweite Fassung von
  `BoundEquipment`, `TierAppearance`, `CharacterInventory`, `EquipmentPurchase` oder der
  Eigentumsmechanik aus `rpg.platform.currency` einführen und keine eigene Kontoführung betreiben.
- **FR-080**: Der Verschleiß aus FR-047 MUSS die **einzige** Stelle sein, an der B11 den
  Ausrüstungsbeitrag beeinflusst, und er läuft über eine benannte Naht statt über einen Eingriff in
  B07.
- **FR-081**: Dieser Block DARF **keine** Ausrüstung als Beute, als Ware und als Vorlage kennen.
  Rüstung und Waffe entstehen ausschließlich über die Klassenleiter (ADR-017).

### Key Entities

- **Item-Vorlage (`ItemTemplate`)**: Was eine Art von Gegenstand ist. Trägt Kennung, Kategorie,
  Vanilla-Material, Raritätsstufe, feste Attributwerte oder Wirkung, Mindestlevel, Klassenbindung,
  Verkaufserlös, reserviertes Custom-Model-Data. Liegt vollständig in Konfiguration.
- **Item-Exemplar**: Ein Gegenstand in der Welt oder in einem Inventar. Trägt **nur** Vorlagen-ID
  und Schema-Version. Alles andere wird abgeleitet.
- **Kategorie**: Verbrauchbares, Kosmetik. **Nicht** Ausrüstung, **nicht** Aufstiegsmaterial.
- **Raritätsstufe**: Common, Uncommon, Rare, Epic, Legendary, Mythic, Divine, Special. Etikett und
  Farbe, keine Wertwirkung.
- **Beutetabelle (`LootTable`)**: Eine Liste aus Vorlagen-ID, Wahrscheinlichkeit und
  Stückzahlspanne. Hängt an einer Mob-Art, an einer Region oder an einem Boss.
- **Beuteanspruch (`LootClaim`)**: Welchem **Charakter** ein gefallener Gegenstand gehört. Steuert
  Sichtbarkeit und Aufsammeln und endet mit Verfall oder Logout. Nachbau der Eigentumsmechanik, die
  `CoinPile` für Coins bereits trägt.
- **NPC-Bestand (`VendorStock`)**: Was der NPC **einer Region** verkauft, mit Preis. Was er ankauft,
  steht als Erlös an der Vorlage, nicht hier.
- **Ausrüstungszustand (`GearCondition`)**: Zwei Werte je Charakter, einer je Leiter-Slot. Die
  Wahrheit über den Verschleiß; der Anzeigewert am Item wird daraus abgeleitet.
- **Verschleißkurve (`WearCurve`)**: Verschleißschwelle, Restanteil, Warnschwellen und die drei
  Raten. Übersetzt einen Zustand in einen Faktor auf den Ausrüstungsbeitrag.
- **Kosmetikbesitz (`CosmeticUnlock`)**: Welche Trimfarben ein **Charakter** gekauft hat und welche
  davon angewandt ist.

---

## Success Criteria *(mandatory)*

- **SC-001**: Ein Betreiber ändert einen Wert an einer Item-Vorlage, lädt neu, und **jedes**
  vorhandene Exemplar in **jedem** Inventar wirkt mit dem neuen Wert — ohne dass ein Inventar
  angefasst wurde.
- **SC-002**: Eine neue Vorlage beider Kategorien entsteht durch Bearbeiten der Konfiguration und
  einen Neustart, **ohne eine Zeile Java**.
- **SC-003**: Ein Item übersteht Relogin, Serverneustart, Charakterwechsel und Schema-Migration
  verlustfrei — nachweisbar an einer Migration von Version 1 auf Version 2.
- **SC-004**: Kein Bezeichner einer einzelnen Vorlage kommt im Code vor; ein Test weist das nach.
- **SC-005**: Ein zweiter Spieler sieht die Beute eines ersten **nicht** und hebt sie **nicht** auf —
  weder durch Zufall, noch durch Timing, noch nach einem Relogin, noch durch einen Charakterwechsel.
- **SC-006**: Die Beute geht an den **größten Beitragenden**, nachweisbar an einem Kampf, in dem ein
  anderer Spieler den letzten Treffer landet.
- **SC-006a**: In einer Party aus drei Mitgliedern in Reichweite bekommt über **neun** gefallene
  Gegenstände **jedes Mitglied genau drei** — unabhängig davon, wer den Schaden gemacht hat. Beute
  ist damit so egalitär wie Erfahrung und Coins, und der Tank geht nicht leer aus.
- **SC-007**: Ein Spieler kann Beute in Coins verwandeln und daraus beim NPC eine Ausrüstungsstufe
  kaufen — der Kreislauf aus B10, B11, B08b und B07 schließt sich ohne Eingriff des Betreibers.
- **SC-008**: Klassenausrüstung ist über **keinen** der Wege Verkauf, Enderchest, Mülleimer, Wurf
  oder Ablegen aus dem Charakter zu lösen; jeder Weg wird einzeln nachgewiesen.
- **SC-009**: Kein Kauf-, Verkaufs-, Aufstiegs- oder Reparaturvorgang hinterlässt einen Zustand, in
  dem gebucht und nicht geliefert wurde — auch nicht bei Logout oder Serverstopp mitten im Vorgang.
- **SC-010**: Beim Tod verliert ein Spieler kein Item und keine Erfahrung, messbar über Inventar und
  Erfahrungsstand vor und nach dem Tod.
- **SC-011**: Ausrüstung ist durch kein Maß an Verschleiß zu zerstören, und ihr Beitrag sinkt bei
  Zustand null auf genau den konfigurierten Restanteil — nachweisbar über die ganze Kurve, nicht nur
  an den Enden.
- **SC-012**: Erlittener Schaden nutzt **nur** die Rüstung ab, Autoattacks **nur** die Waffe, und
  Fähigkeitsschaden **keines von beidem**; eine Reparatur des einen Slots lässt den anderen
  unberührt.
- **SC-013**: Ein Tod kostet **mehr Verschleiß als ein ganzer gewöhnlicher Kampf**, und eine
  Konfiguration, die das umkehrt, startet nicht.
- **SC-014**: Ein Spieler auf Stufe 60 kann sein Aussehen ändern, ohne dass sich ein einziger Wert
  ändert; unterhalb der Höchststufe bleibt jede Stufe von jeder anderen unterscheidbar.
- **SC-015**: Ein Spieler mit vollem Inventar wird gewarnt und verliert nichts still — über
  mindestens zwanzig aufeinanderfolgende Aufsammelversuche.
- **SC-016**: Die Item-Verarbeitung hält das Tick-Budget von **≤ 5 ms** ein; eine wiederholbare
  Messung der eigenen Rechenarbeit belegt das **ohne Volllast** (Prinzip VII, ADR-031). Das
  Bindungsprädikat sitzt im Pfad jedes Inventarklicks und allokiert nichts.
- **SC-017**: Es entsteht **keine** wiederkehrende Aufgabe je Spieler, je Item oder je liegendem
  Gegenstand; Buff-Laufzeiten und Verschleiß werden bei Bedarf ausgewertet, nicht periodisch.

---

## Assumptions

Die folgenden Punkte hat der Auftraggeber nicht ausdrücklich entschieden. Sie sind so gewählt, dass
sie sich ohne Umbau ändern lassen, und jeder ist eine Konfigurationsfrage.

- **Die Verschleißkurve ist Konfiguration, keine Konstante** (Prinzip V). Schwelle 50 %, Restanteil
  20 % erfüllen die Ansage „bei Zustand 0 volle 80 %, bei 10/100 schon schwächer": bei Zustand 10
  bleiben 36 % des Beitrags.
- **Die Verschleißraten** — 0,01 Punkte je Schadenspunkt, 10 Punkte je Tod — sind Vorgabewerte, die
  die Ordnung aus FR-043 erfüllen. Zehn Tode führen von voll auf null, fünf bis zur Schwelle, ab der
  es weh tut.
- **Der Zustand wird auf den Haltbarkeitsbalken des Items abgebildet** — als reine Anzeige, bei
  jedem Laden abgeleitet. Das Item bleibt unzerstörbar; der Balken zeigt einen Wert, der anderswo
  geführt wird. Eine Anzeige in der Lore oder in der Actionbar wäre gleichwertig.
- **Der Reparaturpreis steigt linear mit dem fehlenden Anteil**, skaliert über einen Preis je Stufe.
- **Ein Verbrauchbares kennt drei Wirkungsarten**: sofortige Heilung, sofortige Mana-Wiederherstellung
  und ein zeitlich begrenzter Attributbeitrag. Das ist die Form des Konfigurationsschemas; welche
  Tränke es gibt, ist Inhalt.
- **Eine „Trimfarbe" ist ein benanntes Paar aus Trim-Material und Trim-Muster**, das der Spieler als
  Ganzes kauft — nicht zwei getrennt wählbare Felder.
- **Der Mülleimer-Befehl wirkt auf den gehaltenen Gegenstand** und verlangt eine Bestätigung.
- **Ein NPC je Region, vier Funktionen.** An- und Verkauf, Reparatur, Aufstieg und Kosmetik laufen
  über denselben NPC. Eine Aufteilung auf mehrere NPCs wäre reine Konfiguration.
- **Kosmetik erst auf der Höchststufe** (Q2, aufgelöste Kollision). Falls Trims auf jeder Stufe
  gewünscht sind, brauchen die Schurken- und die Kriegerleiter in `classes.yml` ein zweites
  Unterscheidungsmerkmal — das wäre eine Änderung an **B07** und gehört in einen eigenen ADR.
- **Beutetabellen folgen den sechs Levelbändern aus `zones.yml`** (Greenfields 1–10 bis Pale Wilds
  51–60). Rarität steigt mit dem Band, Werte folgen aus der Vorlage, nicht aus der Rarität.
- **Die Vanilla-Item-Serialisierung bleibt das Speicherformat** der Inventare (`CharacterInventory`
  trägt undurchsichtige Blobs). Das Item-Schema aus FR-005 versioniert die **Vorlagen-Zuordnung im
  PDC**, nicht Bukkits Blob-Format.

---

## Dependencies

| Block | Was B11 daraus benutzt |
|---|---|
| **B03** | `CharacterInventory` — Rucksack und Enderchest je Charakter |
| **B04** | `StatEngine.apply` mit `SourceKind.BUFF`; der Verschleißfaktor auf den Ausrüstungsbeitrag |
| **B05** | `CombatDeathEvent.lootRecipient()` als Eigentümer der Beute, `playerVictim` als Auslöser des Todesverschleißes, `DamageOrigin` zur Trennung von Autoattack und Fähigkeit, der ankommende Schaden vor Abwehr als Verschleißmaß |
| **B06** | `PartyRegistry` für die Mitgliedschaft, `ShareCalculator`s Reichweitenbegriff und die Regel „eine Party ist ein Beitragender" |
| **B08** | `SummonEffect` — der Klon ist vom Verschleiß ausgenommen |
| **B07** | `BoundEquipment`, `TierAppearance`, `LadderSlot`, `EquipmentTier.requiredLevel`, die Unzerstörbarkeit aus `BoundItemFactory` |
| **B08b** | `Currency`, `EquipmentPurchase`, `BookingReason.VENDOR_SALE`, `.VENDOR_PURCHASE` und `.REPAIR`; die Eigentumsmechanik aus `rpg.platform.currency` als Vorlage für den Beuteanspruch |
| **B09** | Die sechs Safe-Cores als Standorte der NPCs, Regionen als Anker der Beutetabellen |
| **B10** | Mob-Arten als Anker der Beutetabellen, Entity-Technik für die NPCs |
| **B13** | Die Ausgabeschnittstelle für Title und Sound (ADR-005) |

---

## Anmerkung zur Constitution

**Prinzip IV ist gegenüber ADR-027 veraltet.** Es fordert wörtlich: *„Items speichern Template-ID
und gewürfelte Roll-Werte"*. ADR-027 hat den Roll-Mechanismus abgeschafft; gespeichert wird die
Vorlagen-ID **allein**. FR-001 folgt ADR-027, nicht dem Wortlaut der Constitution.

Das ist eine Verschärfung, keine Abweichung — was die Constitution schützen will (kein gerendertes
Lore, keine berechneten Endwerte), gilt strenger als zuvor. Trotzdem gehört der Wortlaut angepasst,
bevor `/speckit-plan` seinen Constitution Check gegen ihn laufen lässt: **Prinzip IV, Satz 4, und die
zugehörige Rationale**, als PATCH auf Version 1.1.1. Das ist vor dem Plan zu erledigen, nicht
danach.
