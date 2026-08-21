# Phase 0 · Recherche — B07 Klassen-System

Alle Aussagen über die Paper-API sind gegen das tatsächlich verwendete Artefakt
`io.papermc.paper:paper-api:26.2.build.112-stable` geprüft, nicht aus dem Gedächtnis notiert. Die
Prüfmethode steht bei jedem Punkt.

---

## R1 · Sind Klassenwerte Basiswerte oder Modifikatoren?

**Entscheidung**: Basiswerte über `BaseStatContributor`. `SourceKind.CLASS` bleibt von B07
**unbenutzt**.

**Begründung**: Das Modifikatorband aus B04 (`AttributeDefinition.bandFloor`, ±30 % bzw. ±50 %) wird
um den **effektiven** Basiswert gelegt. Kämen die Stufenwerte als FLAT-Modifikatoren, blieb das Band
am Level-1-Basiswert hängen. Zahlenbeispiel Warrior Health: Basis 40, Level-60-Zuwachs 572, Stufe 5
1400. Als Basiswert ist der effektive Basiswert 2012 und das Band spannt sich darum. Als Modifikator
wäre der Basiswert 40 geblieben, und ein Band von ±30 % um 40 hätte 1400 aus der Stufe niemals
zugelassen — der Wert wäre geklammert worden, und zwar unbemerkt.

ADR-013 hat diesen Weg beim Abschluss von B04 bereits vorgezeichnet („Basiswerte kommen über
`BaseStatContributor` (B06 Level, B07 Klasse)"), und B06 hat ihn für das Levelwachstum mit demselben
Argument gewählt. Bei B06 betraf der Fehler ein Drittel der Endpower; bei B07 wären es nach ADR-017
rund 70 %.

**Verworfene Alternative**: Stufenwerte als FLAT-Modifikatoren unter `SourceKind.CLASS`, weil FR-009
wörtlich von „Quelle Klasse" spricht. Verworfen, weil `SourceKind` die Summationsreihenfolge von
**Modifikatoren** ordnet und Basiswerte gar nicht ordnet — es gibt nichts zu sortieren, wenn es nur
einen Basisbeitrag je Attribut gibt.

**Rückwirkung auf die Spec**: FR-009 und FR-010 sind zu präzisieren. „Als eine Quelle Klasse liefern"
heißt technisch: **ein** `BaseStatContributor` mit der Kennung `class`, der Basiswerte, Levelwachstum
und beide Stufenbeiträge in einem Durchgang beisteuert. Die Zusage bleibt inhaltlich dieselbe — genau
eine Quelle —, aber sie liegt nicht bei `SourceKind`.

---

## R2 · Wie werden die Vanilla-Attributmodifikatoren eines Gegenstands neutralisiert?

Das ist der technisch unklarste Punkt der Spec (FR-046 bis FR-048) und war die eigentliche
Recherchefrage.

**Entscheidung**: `ItemMeta.setAttributeModifiers(ImmutableMultimap.of())` — ein **leerer, nicht-null**
Multimap.

**Nachweis**: `javap` auf `org/bukkit/inventory/meta/ItemMeta.class` im verwendeten Artefakt zeigt
`void setAttributeModifiers(Multimap<Attribute, AttributeModifier>)` sowie
`removeAttributeModifier(Attribute)`. Ein gesetzter Modifikatorsatz ersetzt die Vorgaben des Materials
vollständig, weil er als eigene Komponente am Gegenstand hängt und die materialeigene Vorgabe
verdrängt.

**Zwei Fallen, die beide ausdrücklich vermieden werden:**

1. **`setAttributeModifiers(null)` ist das Gegenteil von leer.** Null entfernt die *Überschreibung*
   und stellt damit die Vorgaben des Materials **wieder her**. Der Unterschied zwischen „keine
   Modifikatoren" und „keine Überschreibung" ist genau der Unterschied, der hier zählt — und es ist
   derselbe Fehlertyp, den ADR-016 für `Double.NaN` als Sentinel festgehalten hat: ein Wert, der
   „nichts" und „Vorgabe" nicht unterscheiden kann.
2. **`ItemFlag.HIDE_ATTRIBUTES` wirkt nur auf die Anzeige.** Der Modifikator bleibt aktiv, nur der
   Tooltip verschwindet. Wer die Flagge für Neutralisierung hält, baut einen Fehler, der im Test
   „sieht richtig aus" besteht und im Spiel falsch rechnet. Die Flagge wird trotzdem gesetzt — aber
   aus Darstellungsgründen, nachdem die Modifikatoren tatsächlich entfernt wurden.

**Verworfene Alternative A**: Gegenmodifikatoren addieren, die die Materialvorgabe aufheben. Verworfen,
weil die Vorgabewerte je Material und je Minecraft-Version verschieden sind — jede Vanilla-Änderung
hätte eine stille Verschiebung erzeugt.

**Verworfene Alternative B**: Waffentypen so wählen, dass ihre Vanilla-Modifikatoren zum Rollenprofil
passen. Verworfen, weil der Waffentyp dann eine unmodellierte neunte Wertquelle wäre und ADR-008 nur
acht Attribute kennt. Zudem hat der Nutzer Schwerter für Warrior **und** Rogue festgelegt, womit diese
Alternative ohnehin keine Unterscheidung mehr liefern würde.

**Prüfbar gemacht — mit einer Einschränkung, die erst bei der Umsetzung auffiel.** Der Bukkit-Vertrag
bestätigt den Mechanismus wörtlich, nachgelesen im Sources-Jar: *„To clear all custom attribute
modifiers, use `null`. To set no modifiers (**which will override the default modifiers**), use an
empty map."*

**MockBukkit unterscheidet die beiden Zustände jedoch nicht.** Nachgemessen: nach
`setAttributeModifiers(ImmutableMultimap.of())` liefert `hasAttributeModifiers()` false,
`getAttributeModifiers()` **null**, die Serialisierung ist identisch mit der eines rohen Items, und
die beiden Metas sind sogar `equals`. Für MockBukkit ist der Aufruf eine Nulloperation.

Damit ist ein Test auf „der Getter liefert leer" **wertlos** — er wäre auch grün, wenn der Aufruf ganz
fehlte. Geprüft wird deshalb am Quelltext, dass der richtige Aufruf dasteht und keine der beiden Fallen
benutzt wird. Der entscheidende Nachweis ist **T143** auf einem echten Server; dass er aussteht, steht
offen in der Aufgabenliste statt hinter einer grünen Zusicherung.

---

## R3 · Womit wird die Stufe sichtbar, wenn das Material gleich bleibt?

**Entscheidung**: Färbung über `LeatherArmorMeta.setColor(Color)` für den Mage, Trim über
`ArmorMeta` mit `ArmorTrim(TrimMaterial, TrimPattern)` für den Rogue ab Stufe 4.

**Nachweis**: Im verwendeten Artefakt vorhanden: `LeatherArmorMeta.getColor/setColor`,
`org.bukkit.inventory.meta.ArmorMeta`, `ArmorTrim`, `TrimMaterial` mit 11 Werten (`AMETHYST`, `COPPER`,
`DIAMOND`, `EMERALD`, `GOLD`, `IRON`, `LAPIS`, `NETHERITE`, `QUARTZ`, `REDSTONE`, `RESIN`) und
`TrimPattern` mit 18 Werten (`BOLT`, `COAST`, `DUNE`, `EYE`, `FLOW`, `HOST`, `RAISER`, `RIB`, `SENTRY`,
`SHAPER`, `SILENCE`, `SNOUT`, `SPIRE`, `TIDE`, `VEX`, `WARD`, `WAYFINDER`, `WILD`). Für drei
Rogue-Stufen reicht das mit großem Abstand.

**Nebenbedingung aus Vanilla**: Nur Leder ist färbbar. `LeatherArmorMeta` wird für Gold- oder
Kettenhemd-Gegenstände nicht geliefert. Deshalb ist FR-016b eine Schemaregel und keine Laufzeitprüfung:
eine Färbung auf einem nicht färbbaren Material bricht den Start ab, statt im Spiel stillschweigend
nichts zu tun.

**Verworfene Alternative**: Custom-Model-Data je Stufe. Verworfen, weil das ohne Resource Pack nichts
anzeigt und ADR-005 keine Client-Voraussetzung erlaubt. Das Feld bleibt trotzdem im Schema reserviert,
wie B11 es für Items tut.

---

## R4 · Wie wird die Auswahl unschließbar?

**Entscheidung**: `InventoryCloseEvent` abfangen und die Auswahl im **nächsten Tick** über den
entity-gebundenen Scheduler erneut öffnen.

**Begründung**: Ein Öffnen im Ereignis selbst ist unzulässig, weil der Client den Schließvorgang noch
abarbeitet — Paper verwirft das erneute Öffnen dann oder der Client bleibt in einem inkonsistenten
Zustand. Ein Tick Versatz ist die etablierte Lösung. Der Scheduler ist der aus B01, entity-gebunden,
nie der globale (Prinzip I, ADR-007).

**Warum keine Bewegungssperre über einen Tick-Task**: Prinzip II verbietet wiederkehrende Aufgaben je
Spieler. Stattdessen wird `PlayerMoveEvent` abgebrochen. Das Ereignis feuert häufig, aber die Prüfung
ist ein Test auf `activeCharacter().isEmpty()` und trifft im Normalbetrieb auf niemanden — jeder
Spieler mit Charakter fällt in der ersten Zeile heraus.

**Randfall, der aus B03 kommt und hier nur benutzt wird**: `PlayerSession.activeCharacter()` liefert
bereits `Optional`, und B05 kennt bereits `RejectReason.SESSION_NOT_READY`. Ein Spieler ohne Charakter
hat also weder Snapshot noch Schadenspfad. **B04 und B05 werden nicht angefasst** — das ist der ganze
Gewinn von ADR-020 gegenüber einem Tutorialbereich.

**In der Umsetzung geprüft (T055) — und die Antwort ist besser als die Frage.** Die Ablehnung fällt
nicht an `SESSION_NOT_READY`, sondern an **`NO_HOLDER`**: ein Stat-Halter entsteht über
`DefaultStatEngine.createForCharacter`, also **je Charakter**. Ein Spieler ohne Charakter hat deshalb
gar keinen Halter und ist nicht Teil des Kampfsystems — genauso wie ein gewöhnliches Tier. Die Prüfung
steht in `DefaultCombatPipeline` vor der Sitzungsprüfung.

Das ist die stärkere Zusage. `SESSION_NOT_READY` hängt am Sitzungszustand und wäre nach dem Laden der
Sitzung wieder `READY`, also wirkungslos; `NO_HOLDER` hängt daran, dass es nichts zu treffen gibt.
**B05 wird nicht angefasst**, und zwar nicht aus Zurückhaltung, sondern weil es nichts zu ändern gibt.
Festgehalten in `NoCharacterNoCombatTest` — inklusive der Gegenprobe, dass es mit Halter trifft.

---

## R5 · Wie wird eine Leiter variabler Länge normiert?

**Entscheidung**: Anteilskurve `s(i) = ((i-1)/(n-1))^1.3` über die Stufen `1..n` für absolute
Attribute, **linear** für die drei prozentualen.

**Begründung**: Der Exponent 1.3 macht die frühen Aufstiege kleiner und die späten größer, was zur
Levelanforderung passt. Für die prozentualen Attribute ist die Kurve linear, weil ihre Zielwerte klein
sind (0,05 bis 0,50) und ein Exponent bei zwei Dezimalstellen Stufen aufeinander fallen lässt. Genau
das ist beim Mage passiert: Ziel +5 % über sieben Stufen ergab zwei Stufen auf 0 — nicht streng
steigend und damit ein Startfehler nach FR-017. Der Zielwert wurde auf **+6 %** angehoben; das ist
der kleinste Wert, der sechs unterscheidbare Schritte über null zulässt.

**Verworfene Alternative**: drei Dezimalstellen für prozentuale Attribute. Verworfen, weil
`0,006` als Balancing-Zahl niemand liest und Prinzip V lesbare Konfiguration verlangt.

**Prüfbar gemacht**: SC-014 fordert den Nachweis für Leitern mit fünf, sechs und sieben Stufen. Die
Normierung ist damit kein Einzelfall, sondern eine Eigenschaft.

**Bei der Umsetzung gefunden — die Einheit war falsch.** Die Zielwerte der Spec waren als Prozent
notiert (+15 %, +50 %, +30 %), die Konfiguration addiert aber auf den **Basiswert** des Attributs.
Da `attackSpeed` den Basiswert 4,0 und `movementSpeed` den Basiswert 0,1 hat, hätte `0.15` nur
+3,75 % bedeutet und `0.30` sogar **+300 %**. Die Leiterwerte stehen jetzt in der Einheit des
Attributs (+50 % Angriffsgeschwindigkeit = `2.00`), und `classes.yml` trägt eine Tabelle im Kopf, die
die Umrechnung festhält. Das Band aus ADR-008 begrenzt diese Werte nicht — es begrenzt Modifikatoren
um den effektiven Basiswert, und die Klasse verschiebt den Basiswert selbst.

---

## R6 · Wie erkennt die Sperre ein gebundenes Item?

**Entscheidung**: Ein Schlüssel im `PersistentDataContainer` des Gegenstands, gesetzt beim Aufbau,
geprüft im Listener. Inhalt: Klassen-ID, Slot und Charakter-ID.

**Begründung**: Der Vergleich über das Material allein wäre falsch, sobald ein Spieler ein
gleichartiges, ungebundenes Item aus Beute besitzt — ein Netherite-Schwert aus einer Truhe wäre
plötzlich unbeweglich. Die Charakter-ID im Schlüssel macht das Item zudem nicht übertragbar: ein
kopierter Gegenstand gehört einem anderen Charakter und wird beim Laden ersetzt statt anerkannt.

`PersistentDataContainer` statt Lore-Parsing ist dieselbe Festlegung, die ADR-004 für Items getroffen
hat, und bleibt hier gültig, obwohl ADR-017 die Werte aus dem Item genommen hat.

**Wo die Grenze zwischen Kern und Plattform liegt**: `rpg-core` beantwortet „welche Stufe trägt dieser
Charakter, und wie sieht sie aus" — das ist der Sollzustand. `rpg-platform` setzt den Schlüssel und
liest ihn, weil `PersistentDataContainer` ein Bukkit-Typ ist. Prinzip III bleibt gewahrt: der Kern
kennt den Schlüssel als Zeichenkette, nicht als API-Objekt.

---

## R7 · Wohin geht die Warnung bei vollem Inventar?

**Entscheidung**: über die bestehende Nachrichtenschnittstelle mit einem Message-Schlüssel in
`ClassMessageKeys`, nicht über einen direkten Aufruf am Spieler.

**Begründung**: Title und Ton sind HUD-Ausgaben und gehören zu B13, das noch nicht existiert.
Prinzip V verbietet hartcodierte Spielertexte ohnehin. Die Ausgabe läuft vorläufig über den
vorhandenen Nachrichtenweg; B13 ersetzt später die Darstellung, ohne B07 anzufassen (ADR-005).

**Ausdrücklich nicht Teil von B07**: der Ton selbst, die Einblendungsdauer und die Frage, ob eine
Warnung gebündelt wird. Das sind Darstellungsentscheidungen.

---

## Zusammenfassung der offenen Punkte

Keiner der ursprünglichen Unbekannten bleibt offen. Zwei Dinge wandern als **Aufgaben** in Phase 2,
nicht als Unklarheiten:

| Punkt | Wohin |
|---|---|
| `DefaultCombatPipeline` prüft den fehlenden aktiven Charakter, nicht nur `READY` | Prüfaufgabe, R4 |
| FR-009 und FR-010 auf `BaseStatContributor` präzisieren | Spec-Nachtrag, R1 |
