# Phase 0 · Research — B10 · Mobs & Horden-Spawning

Die fünf Fragen, die `/specify` an den Plan übergeben hat, plus vier, die beim Nachsehen dazukamen.
Jede mit dem, was entschieden wurde, warum, und was verworfen wurde.

Geprüft wurde gegen die tatsächlich ausgelieferte `paper-api-26.2.build.112-stable.jar`, nicht gegen
Erinnerung an eine ältere Bukkit-Version. Wo unten ein Typ steht, existiert er dort.

---

## R1 · Wie die Vanilla-Unterdrückung ansetzt — **Spielregeln zuerst, Ereignis als Riegel**

**Entscheidung.** Zwei Schichten, und die Reihenfolge ist der Punkt.

1. **Spielregeln je Welt**, gesetzt beim Start und bei jedem `WorldLoadEvent`:
   `SPAWN_MOBS`, `SPAWN_MONSTERS`, `SPAWN_PATROLS`, `SPAWN_PHANTOMS`, `SPAWN_WANDERING_TRADERS`,
   `SPAWN_WARDENS`, `SPAWNER_BLOCKS_WORK` — alle auf `false`.
2. **Ein `CreatureSpawnEvent`-Riegel** auf `HIGHEST`, der alles abbricht, dessen `SpawnReason` nicht
   ausdrücklich erlaubt ist. Erlaubt sind `CUSTOM` (das sind unsere), `COMMAND`, `SPAWNER_EGG` und
   `DISPENSE_EGG` — also genau das absichtliche Setzen aus FR-018d.

**Begründung.** FR-018f verlangt, dass die Unterdrückung *am Entstehen* ansetzt und nicht am
Aufräumen danach. Beide Schichten tun das, aber unterschiedlich teuer, und deshalb braucht es beide:

- Die Spielregeln halten den **Spawner-Durchlauf selbst** an. Er läuft dann gar nicht erst, es
  entsteht kein Kandidat, kein Ereignisobjekt, keine Zuweisung. Das ist die einzige Variante, die
  wirklich nichts kostet — und bei einer Welt, in der pro Tick sonst hunderte Kandidaten geprüft
  würden, ist das der ganze Gewinn.
- Die Regeln decken aber **nicht alles ab**. `SpawnReason` kennt gut vierzig Gründe, und viele davon
  hängen an keiner Regel: `RAID`, `VILLAGE_DEFENSE`, `NETHER_PORTAL`, `JOCKEY`, `SILVERFISH_BLOCK`,
  `REINFORCEMENTS`, `SLIME_SPLIT`, `TRIAL_SPAWNER`, `INFECTION`, `DROWNED`. Ohne den Riegel wäre die
  Zusage „überall, auch nachts, auch in Höhlen" schlicht falsch, und zwar an Stellen, die man beim
  Testen nicht zufällig trifft.

Der Riegel ist dabei **billig, gerade weil die Regeln davor stehen**: er sieht nur noch die Handvoll
Fälle, die die Regeln durchlassen. Ein Ereignis je Spawn-Versuch ist teuer, wenn es tausende
Versuche gibt — es gibt sie nach Schicht 1 nicht mehr.

**Verworfen.**

- *Nur der Ereignis-Riegel.* Ehrlicher Umfang, aber er zahlt für jeden Kandidaten, den der
  Vanilla-Spawner erzeugt hätte. Das ist genau die Last, die dieser Block senken soll.
- *Nur die Spielregeln.* Billiger, aber löchrig — siehe die Liste oben.
- *`spigot.yml` / `bukkit.yml` von Hand.* Serverkonfiguration, nicht Plugin-Konfiguration. Ein Block,
  dessen Zusage davon abhängt, dass jemand eine fremde Datei richtig ausgefüllt hat, hat die Zusage
  nicht. Die Spielregel steht dagegen unter unserer Kontrolle und wird bei jedem Weltladen erneuert.
- *Aufräumen statt Verhindern.* Von FR-018f ausdrücklich ausgeschlossen: eine Kreatur, die erst
  erscheint und dann entfernt wird, hat bereits einen Tick gekostet und war kurz sichtbar.

**Vorbild im Haus.** `VanillaRegenerationGuard` macht genau das schon für die Regenerationsregel:
beim Start auf alle geladenen Welten, danach auf jedes `WorldLoadEvent`. Dieselbe Form, ein anderer
Satz Regeln.

---

## R2 · Wie eine Kreatur ihre Art trägt — **im Persistent Data Container der Entität**

**Entscheidung.** Ein Schlüssel `rpg:mob_kind` mit der Kennung der Art, geschrieben beim Setzen. Dazu
`rpg:mob_zone` mit dem Zonenschlüssel, aus dem sie stammt.

**Begründung.** Der PDC überlebt Chunk-Entladen und Serverneustart und ist die Stelle, an der dieses
Projekt so etwas schon ablegt: `CoinPileTag` tut es für den Haufen, `AbilityItemTag` für den
Gegenstand, `BoundItemTag` in B07. Der Nachsatz aus jenen Klassen gilt hier genauso — es ist kein
Anzeigetext, den ein Client verdrehen kann.

**Warum `rpg:mob_zone` dazugehört.** FR-017 verlangt, dass eine Kreatur dem Budget ihrer
*Ursprungs*zone zugerechnet bleibt, auch wenn sie sich hinausbewegt. Aus der Position ist das nicht
mehr zu erfahren; aus dem Vermerk schon. Ohne ihn ließe sich das Budget umgehen, indem man Kreaturen
über die Grenze schiebt.

**Verworfen.**

- *Eine Karte im Speicher, `UUID → MobKind`.* Schneller im Zugriff, aber sie überlebt keinen
  Neustart und keinen `/reload`, und dann steht eine Kreatur da, deren Art niemand mehr kennt —
  mit vollen Werten im Kampf und ohne Erfahrung beim Tod.
- *Der Anzeigename als Träger.* Er ist Anzeige, er ist übersetzbar, und er ist genau das, was
  `CoinPileTag` mit derselben Begründung ablehnt.
- *Scoreboard-Tags.* Funktionieren, sind aber ein globaler Namensraum, den jedes andere Plugin
  ebenfalls beschreibt.

**Anschlussfrage, hier beantwortet:** Muss der Vermerk einen Chunk-Unload überleben, wo FR-019 die
Kreatur ohnehin entfernt, sobald niemand da ist? Ja — die beiden Zeitpunkte fallen nicht zusammen.
Ein Chunk kann entladen sein, während in der Zone noch Spieler stehen, und die Aufräumfrist läuft
erst danach.

---

## R3 · Das Chunk-Budget ohne Zählung je Chunk — **zählen, was belegt ist, nicht, was existiert**

**Entscheidung.** Der Bestand wird ohnehin geführt (R4). Das Chunk-Budget liest daraus: eine
`long → int`-Zählung, die **nur Chunks enthält, in denen wirklich eine unserer Kreaturen steht**. Der
Schlüssel ist derselbe gepackte Chunk-Schlüssel, den B09s `ChunkZoneIndex` schon benutzt.

**Begründung.** Die Frage im Auftrag war, wie man das Budget führt, „ohne je Chunk eine Zählung
mitzuschleppen" — und die Antwort ist, dass die Zahl der *möglichen* Chunks irrelevant ist. Eine
Region von 10.000×10.000 Blöcken hat rund 390.000 Chunks; darin stehen im Betrieb höchstens ein paar
hundert Kreaturen, also höchstens ein paar hundert belegte Chunks. Die Zählung wächst mit dem
Bestand, nicht mit der Welt.

Der Eintrag entsteht beim Setzen und verschwindet, wenn er auf null fällt. Damit gibt es keine
Struktur, die nur wächst.

**Warum `ChunkTable` hier nicht wiederverwendet wird.** Sie ist beim Laden gebaut und danach
ausschließlich gelesen — genau darum ist sie zuweisungsfrei. Das Chunk-Budget ist das Gegenteil: es
ändert sich bei jedem Setzen und jedem Tod. Dieselbe Klasse für beides hieße, die eine Zusage
aufzugeben, die die andere trägt. Der gepackte Schlüssel wird übernommen, die Struktur nicht.

**Verworfen.**

- *Eine `HashMap<Long, Integer>`.* Sie boxt bei jedem Zugriff, und der Zugriff liegt im Spawn-Pfad.
  Dieselbe Begründung, aus der `ChunkTable` überhaupt existiert.
- *Beim Bedarf zählen, über `chunk.getEntities()`.* Bukkit gibt dann alle Entitäten des Chunks
  heraus, auch Gegenstände und Rahmen, und wir müssten jede prüfen. Eine Zahl, die wir selbst führen,
  ist billiger und genauer.

---

## R4 · Der Bestand und das Aufräumen — **ein Durchlauf je bevölkerter Zone, keiner je Kreatur**

**Entscheidung.** Ein `HordeRegistry` hält, was dieser Block gesetzt hat: Entitäts-Id, Art, Zone,
Zeitpunkt. Ein selbst neu eingeplanter Einmal-Durchlauf je Zone-mit-Spielern macht in einem Zug
beides — nachsetzen (US2, US4) und aufräumen (US3).

**Begründung.** Prinzip II verbietet wiederkehrende Aufgaben *je Spieler oder je Entität*. Ein
Durchlauf je Zone ist keines von beidem: bei sechs Regionen sind das höchstens sechs, und in einer
leeren Zone läuft er gar nicht (FR-015). Spawnen ist von Natur aus periodisch — es lässt sich nicht
ereignisgesteuert auslösen, weil das Ereignis „hier fehlt eine Kreatur" nicht existiert. Was sich
vermeiden lässt, ist die Periodizität *je Kreatur*, und das tut dieser Zuschnitt.

Die Form ist im Haus etabliert: `StatusActionBar.startRefresh` und der Schadensfenster-Sweep planen
sich beide selbst neu, weil der Scheduler nach ADR-007 absichtlich keine wiederkehrende Aufgabe
kennt. Endet das Plugin, gibt der Scheduler ein abgebrochenes Handle zurück und der Rumpf läuft nie
wieder — der Durchlauf hört von selbst auf.

**Warum Nachsetzen und Aufräumen im selben Durchlauf.** Beide brauchen dieselbe Antwort — wer steht
gerade in dieser Zone — und beide brauchen den Bestand. Zwei Durchläufe hießen, dieselbe Frage zweimal
zu stellen und dabei zwei Zustände zu haben, die auseinanderlaufen können.

**Die Ausnahme in FR-022** (eine Kreatur im Kampf wird nicht entfernt) wird über B05 beantwortet, das
den Kampfzustand ohnehin führt und lazy aus Zeitstempeln rechnet. Keine eigene Buchführung.

**Verworfen.**

- *Vanillas eigenes Despawnen.* Es legt schlafen statt zu entfernen und hängt an
  `entity-activation-range` — Serverkonfiguration, siehe R1.
- *Ein `EntityRemoveEvent`-getriebener Bestand ohne eigenen Durchlauf.* Hält den Bestand sauber,
  räumt aber nichts weg. Das Ereignis wird trotzdem mitgehört, damit ein Tod oder ein fremder
  Eingriff den Bestand nicht verfälscht.

---

## R5 · Der Schlüssel der drei wartenden Schnittstellen — **Art, sonst Vanilla-Typ**

**Entscheidung.** Eine einzige Ableitung, an genau einer Stelle:

```
kindKeyOf(entity) = PDC-Wert rpg:mob_kind, und wenn der fehlt: entity.getType().name()
```

Damit werden `MobStatProvider`, `MobXpProvider.xpFor` und `MobCoinProvider.coinsFor` bedient.

**Begründung.** Es löst zwei Dinge auf einmal und ändert keine Schnittstelle.

Erstens die Verwechslung, die `/specify` gefunden hat: heute füttert `CoinDropListener` die
Schnittstellen mit `creature.getType().name()`, und vier Arten auf `ZOMBIE` wären vier Mal derselbe
Schlüssel. Mit dem PDC-Wert sind sie vier verschiedene.

Zweitens FR-009 und die Rückwärtsverträglichkeit: eine Kreatur ohne Vermerk antwortet weiterhin mit
ihrem Vanilla-Typnamen, also greifen die vorhandenen Einträge in `combat.yml` (`mobs.by-type`) und
die Standardwerte unverändert. Die Übergangsregelung aus B05 hört nicht auf zu funktionieren — sie
wird nur noch von dem bedient, was sie immer gemeint hat.

Die Form der Schnittstellen (`String → OptionalLong`, `String → Optional<ModifierSet>`) bleibt
unverändert. Damit ist die Zusage aus `02-decisions.md` Abschnitt 5 wörtlich eingehalten: ersetzt
unter demselben Schlüssel, ohne einen zweiten einzuführen.

**Verworfen.**

- *Eine zweite Schnittstelle je Art.* Genau das, was die Zusage ausschließt.
- *Ein zusammengesetzter Schlüssel wie `ZOMBIE:greenfields.rotling`.* Zwei Bedeutungen in einem
  String, und jeder Leser muss ihn wieder auseinandernehmen.

---

## R6 · Wo die Zielsuche gedrosselt wird — **erst das Attribut, die eigene AI nur wenn nötig**

**Entscheidung für jetzt.** Kein Ersatz der Vanilla-AI. Stattdessen:

- **`Attribute.FOLLOW_RANGE` je Art**, aus der Konfiguration. Das ist der Radius, in dem eine
  Kreatur überhaupt nach einem Ziel sucht, und damit die Stellschraube mit dem größten Hebel: die
  Suche ist quadratisch im Radius. Vanillas Standard von 16 bis 48 Blöcken ist für eine Horde zu
  großzügig.
- **Eigene Zielzuweisungen gedrosselt** — der Klon aus R9 ist die einzige, die es heute gibt, und
  sie fasst höchstens im konfigurierten Abstand an.

**Begründung.** Die Architekturvorgabe nennt eine vereinfachte AI mit „ggf." — als Möglichkeit, nicht
als Auftrag. Vanillas Pfadfindung ist bereits stark optimiert und in C++-Nähe gebaut; sie durch
eigenen Java-Code zu ersetzen ist eine Wette, die man nur eingehen sollte, wenn eine Messung sie
verlangt. Diese Messung gehört nach ADR-031 zu B15. Bis dahin ist die ehrliche Reihenfolge: die
billige Stellschraube ziehen, messen (R8), und den Ersatz nur bauen, wenn die Zahl ihn fordert.

Das ist ausdrücklich **keine Vertagung der Zusage** — FR-035 bis FR-037 werden erfüllt. Es ist die
Entscheidung, sie mit dem kleinsten Mittel zu erfüllen, das sie erfüllt.

**Verworfen.**

- *Vanilla-AI ganz abschalten (`setAI(false)`) und selbst steuern.* Eine Kreatur, die nicht mehr
  fällt, nicht mehr schwimmt und nicht mehr um einen Block herumgeht, ist keine Ersparnis, sondern
  ein anderes Spiel.
- *`entity-activation-range` in `spigot.yml`.* Serverkonfiguration, siehe R1.

**Was das für den Plan heißt:** FR-038s Messung muss die Zielsuchkosten *mitmessen*, sonst hat die
Entscheidung oben keine Grundlage, an der sie später überprüft werden kann.

---

## R7 · Die Startwerte

Alle konfigurierbar; das hier sind die Ausgangswerte, keine Zusagen.

| Wert | Start | Woher |
|---|---:|---|
| Budget je Zone | 130 | 800 serverweit aus dem M4-Nachweis, auf sechs Regionen |
| Budget serverweit | 800 | M4-Nachweis, Vision & Scope |
| Budget je Chunk | 12 | Eine volle Horde auf 16×16 Blöcken, ohne dass sie ineinander steht |
| Budget je Spieler | 25 | Was ein Spieler in einer Hack'n'Slash-Runde noch überblickt |
| Aufräumfrist nach dem letzten Spieler | 60 s | Lang genug für einen kurzen Rückweg, kurz genug für einen Zonenwechsel |
| Aufräumreichweite | 96 Blöcke | Deutlich über der Sichtweite, unter der Chunk-Ladeweite |
| Zielsuchreichweite (`FOLLOW_RANGE`) | 24 Blöcke | Unter Vanillas Standard, über der Nahkampfreichweite |
| Abstand der Zielsuche | 500 ms | Zehn Ticks; darunter merkt niemand den Unterschied |
| Nachschub | 1 Kreatur je 2 s je Zone | Eine geräumte Horde füllt sich in gut vier Minuten |
| Boss-Respawn | 30 min | Lange genug, dass er ein Ereignis bleibt |
| Skalierung je Spieler | +20 % Dichte, gedeckelt am Budget | Zu fünft doppelt so voll wie allein |

**Zur Ableitung der 130:** 800 ÷ 6 ist 133. Die runde Zahl darunter ist ehrlicher, weil 800 selbst
ein Zielwert und keine Messung ist.

---

## R8 · Die Messung ohne Volllast — **wie `ZoneLookupBenchmarkTest`, mit zwei Zahlen**

**Entscheidung.** Ein Test im `rpg-core`-Modul, der zwei Dinge misst und beide gegen eine Obergrenze
prüft:

1. **Ein Spawn-Durchlauf**: wie lange die Entscheidung dauert, *was* wo gesetzt werden soll — Budget
   prüfen, Bereich wählen, Art würfeln, Chunk-Zählung nachführen. Ohne Bukkit, ohne Entität.
2. **Ein Aufräum-Durchlauf** über einen Bestand fester Größe: wer ist zu weit weg, wessen Zone ist
   leer.

**Begründung.** B09 hat für SC-001 genau das gebaut (`ZoneLookupBenchmarkTest`), und die Form hat
sich bewährt: sie läuft im normalen Testlauf mit, braucht keinen Server, und sie misst die eigene
Rechenarbeit statt das Zusammenspiel. Prinzip VII in der Fassung von ADR-031 verlangt exakt das —
„eine wiederholbare Messung der eigenen Rechenarbeit", und ausdrücklich nicht mehr.

**Was sie nicht misst und auch nicht messen soll:** das Setzen der Entität selbst, die Pfadfindung,
das Entity-Ticking. Das sind Paper-Kosten, sie brauchen einen Server und 800 Kreaturen, und sie
gehören zu B15. Der Test wird das in seinem Kopf sagen, damit niemand ihn später für einen Lasttest
hält.

---

## R9 · Wie der Klon Kreaturen auf sich zieht — **umlenken, wenn Vanilla neu wählt**

**Entscheidung.** Ein `EntityTargetLivingEntityEvent`-Zuhörer: wählt eine unserer Kreaturen einen
Spieler, für den gerade ein Klon in Reichweite steht, wird das Ziel auf den Klon umgesetzt. Endet der
Klon, wird nichts weiter getan — die nächste Wahl trifft wieder den Spieler.

**Begründung.** `Mob.setTarget` allein reicht nicht: Vanilla wählt gleich wieder neu, und die Wahl
gewinnt. Das ist derselbe Fehler, an dem die Blockhaltung des Warriors gescheitert war — der Server
setzte einen Zustand, den der nächste Vanilla-Durchlauf zurücknahm. Die Lösung ist dieselbe: nicht
dagegen anschreiben, sondern an dem Ereignis ansetzen, an dem Vanilla die Entscheidung trifft.

Der Zuhörer kostet nichts für Kreaturen ohne Klon in der Nähe — er fragt zuerst, ob überhaupt ein
Klon steht, und das ist ein Blick in eine Karte, die fast immer leer ist.

**Anschluss an FR-041:** Die Drosselung aus FR-035 gilt hier mit, weil das Umlenken an derselben
Zielwahl hängt, die gedrosselt wird — es entsteht keine zweite, ungedrosselte Zielsuche.

---

## R10 · Wo der Boss steht — **eine Kennung aus `zones.yml`, gefüllt in `mobs.yml`**

**Entscheidung.** Die Bosskonfiguration nennt den `SpawnArea`-Schlüssel, in dem der Boss steht, plus
einen Versatz darin. Sie nennt keine eigenen Koordinaten.

**Begründung.** B09 liefert je Region benannte Bereiche und weigert sich ausdrücklich, einen davon
als Bossbereich zu kennzeichnen — „ein Bossbereich unterscheidet sich geometrisch von keinem
anderen; er unterscheidet sich in dem, was darin steht". Genau dieses „was darin steht" ist die
Konfiguration dieses Blocks. Über den Bereichsschlüssel zu gehen statt über Koordinaten hält die
Geometrie an einer Stelle: verschiebt jemand den Bereich in `zones.yml`, zieht der Boss mit.

**Verworfen.** *Eigene Koordinaten in `mobs.yml`.* Zwei Orte, die dieselbe Geometrie beschreiben,
und einer davon wird beim nächsten Verschieben vergessen.

---

## R11 · Der Anzeigename über dem Kopf — **B05s Namensschild, keine zweite Anzeige**

**Entscheidung.** `MobNameplate` aus B05 bleibt, wie es ist, und bekommt seinen Namen aus der Art
statt aus dem Vanilla-Typ.

**Begründung.** Die Klasse ist bereits die eine Zeile über einer Kreatur und begründet ausführlich,
warum es genau eine ist (ADR-005, Prinzip II: keine zweite Entität je Mob). Ein Level oder ein
Boss-Kennzeichen dazuzuschreiben ist eine Änderung an ihrem Text, nicht an ihrer Bauart.

**Offene Kleinigkeit für `/tasks`:** Der Textschlüssel liegt in `messages.yml`, der Name der Art in
`mobs.yml`. Ob die Art den Schlüssel nennt oder den Namen direkt, ist eine Frage von Prinzip V
(keine hartcodierten Spielertexte) — sie nennt den **Schlüssel**, wie jede andere Anzeige in diesem
Projekt.
