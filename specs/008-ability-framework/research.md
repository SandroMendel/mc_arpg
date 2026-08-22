# Phase 0 · Recherche B08 · Fähigkeiten-Framework

Sieben Unbekannte, alle gegen den Bestand geprüft statt vermutet. Drei davon haben den Plan geändert.

---

## R1 · Wo liegt die Regeneration, und warum nicht in B04?

**Entscheidung:** In B08, als eigener Baustein `ResourceRegeneration`, der beide Ressourcen abrechnet.

**Begründung:** Die Rate steht in einem Attribut und gehört damit B04 — aber die *Anwendung* braucht
den Kampfzustand, und der liegt in B05. B04 dürfte ihn nicht lesen: die Abhängigkeitsrichtung ist
`plugin → platform → core` mit `B05 → B04` innerhalb von `rpg-core`, und die Gegenrichtung wäre ein
Verstoß gegen Prinzip III. B08 liegt über beiden.

**Der Bestand sagt es bereits.** Drei Stellen benennen B08 namentlich als den Ort:

- `ResourcePool`: *„damage and healing are B05, ability costs and mana regeneration are B08"*
- `CombatState`: *„B08 already decided that mana regeneration is reduced during combat"*
- `rpg.core.stats.package-info`: *„B08 Abilities — mana regeneration, cooldown bookkeeping, buff
  durations"*

Neu gegenüber diesen Zusagen ist allein, dass es jetzt **zwei** Ressourcen sind (ADR-023).

**Verworfen:** *Regeneration in B04 mit einem eingehängten „ist im Kampf"-Prädikat.* B04 nimmt bereits
Registrierungen von späteren Blöcken entgegen (`registerBaseStatContributor`, `registerVanillaBridge`),
die Bauform wäre also nicht fremd. Sie hätte aber einen Zustand je Halter — den Zeitpunkt der letzten
Abrechnung — in einen Block gelegt, dessen erklärte Aufgabe das *Rechnen aus Quellen* ist, und die
Regeneration wäre die einzige Stelle in B04 gewesen, die von sich aus Werte verändert.

---

## R2 · Wie wirkt eine Fähigkeit am Ende ihrer Wirkzeit?

**Entscheidung:** `Scheduler` wird um `runSyncOnEntityDelayed(EntityRef, Duration, Runnable)`
erweitert. ADR-024 hält das fest.

**Der Befund:** `Scheduler` bietet heute genau vier Methoden — `runSyncAtLocation`,
`runSyncOnEntity`, `runAsync`, `runAsyncDelayed`. Es gibt **kein verzögertes synchrones Einzelstück**.
Eine Wirkzeit ist aber genau das: einmalige Arbeit im Tick, zu einem bestimmten späteren Zeitpunkt,
mit Berührung der Paper-API.

**Warum das keine Umgehung der Abstraktion ist.** Das Javadoc der Schnittstelle verbietet zwei Dinge
ausdrücklich: unbeschränkte synchrone Arbeit ohne Orts- oder Entity-Bindung, und *wiederkehrende*
Aufgaben. Die Erweiterung ist entity-gebunden und einmalig, verletzt also keines von beiden. Sie folgt
demselben Muster wie `runAsyncDelayed`, das ADR-010 für B02s Autosave ergänzt hat — dort steht sogar
die Begründung schon: *„A delayed one-shot is a different thing and is offered."*

**Verworfen, Alternative 1:** *`runAsyncDelayed`, das am Ende `runSyncOnEntity` aufruft.* Läuft mit der
heutigen Schnittstelle und war der erste Entwurf. Drei Nachteile: ein Threadwechsel für Arbeit, die
den Tick nie verlässt; eine Ungenauigkeit von bis zu einem Tick zusätzlich zur ohnehin vorhandenen;
und zwei Scheduler-Aufrufe je Cast statt einem. Der letzte wiegt am schwersten, weil `runSyncOnEntity`
einen bereits abgebrochenen Handle zurückgeben darf, wenn die Entity gerade nicht auflösbar ist — der
Cast müsste diesen Fall dann *nach* dem Warten behandeln, statt vorher.

**Verworfen, Alternative 2:** *Den Cast lazy auswerten wie einen Cooldown.* Unmöglich, und der
Unterschied ist grundsätzlich: ein Cooldown wird ausgewertet, **wenn jemand fragt**. Ein Cast muss
wirken, **auch wenn niemand fragt**. Zeitstempelarithmetik kann eine Frage beantworten, aber keine
Handlung auslösen.

**Papers Seite:** `EntityScheduler` kennt `runDelayed(Plugin, Consumer, Runnable, long)` nativ. Die
Erweiterung bildet also 1:1 auf eine vorhandene Plattformfähigkeit ab, statt eine nachzubauen — und
das ist der zusätzliche Grund, sie in die Abstraktion aufzunehmen statt daran vorbei zu arbeiten.

---

## R3 · Wie erkennt die Regeneration den Wechsel des Kampfzustands?

**Entscheidung:** Gar nicht über ein Ereignis. `ResourceRegeneration` hält je Charakter zwei
Zeitstempel — den der letzten Abrechnung und den, zu dem der zuletzt beobachtete Kampf endet — und
zerlegt jedes Intervall selbst.

**Der Fund, der dazu geführt hat:** `DefaultCombatPipeline.publishExpiredCombatStates()` existiert,
ist dokumentiert und wird **in der Produktion von niemandem aufgerufen**. Die einzigen Aufrufer stehen
in `CombatStateTest`. Damit wird die *verlassende* Flanke des Kampfzustands heute nie veröffentlicht.

Das ist genau der Fehler, der für die Schadensfenster schon einmal auftrat und dort mit
`startDamageWindowSweep` behoben wurde — dieselbe Behandlung hat der Kampfzustand nie bekommen.
`isInCombat()` antwortet weiterhin richtig, weil es lazy rechnet; nur das Ereignis fehlt.

**Warum B08 das nicht zu seinem Problem macht.** Die Zwei-Zeitstempel-Lösung braucht das Ereignis
nicht und ist ihm überlegen:

- Sie ist **exakt**. `remainingCombatTime` liefert, solange der Halter im Kampf ist, den genauen
  Endzeitpunkt. Wird er gespeichert, lässt sich das Intervall `[letzte Abrechnung, jetzt]` später
  präzise an diesem Punkt teilen — auch wenn zwischendurch nichts gelaufen ist.
- Sie funktioniert **über eine Abwesenheit hinweg** (FR-038), wo ein Ereignis niemanden erreicht
  hätte.
- Sie erfüllt Prinzip II strenger, weil sie auch die Ereignisverarbeitung einspart.

**Cross-Block-Fund, gehört B05 und nicht hierher:** Dass die verlassende Flanke nicht ausgelöst wird,
bleibt ein Defekt — B12 und B13 werden sie erwarten, weil B05 sie zusagt. Er wird in den offenen
Punkten von B05 vermerkt, nicht in B08 nebenbei behoben (Workflow-Regel 5). Die Behebung ist
absehbar klein: ein Aufruf im vorhandenen Sweep, der ohnehin schon läuft.

---

## R4 · Wie löst ein Rechtsklick aus, und wie bleibt der Linksklick wirkungslos?

**Entscheidung:** Ein Listener auf `PlayerInteractEvent`. Auslösung bei `RIGHT_CLICK_AIR` und
`RIGHT_CLICK_BLOCK`, Abbruch bei `LEFT_CLICK_*`, wenn das gehaltene Item eine Fähigkeitsmarke trägt.

**Begründung:** ADR-005 lässt nur Vanilla-Eingaben zu, und das entschiedene Eingabeschema ist
Hotbar-Slot plus Rechtsklick. `PlayerInteractEvent` ist die einzige Stelle, an der beides zugleich
sichtbar ist: welcher Slot gehalten wird und welche Taste gedrückt wurde.

**Der Linksklick braucht zwei Griffe, nicht einen.** `PlayerInteractEvent` abzubrechen verhindert die
Blockinteraktion, aber ein Schlag auf eine *Entity* kommt als `EntityDamageByEntityEvent` und läuft
damit in B05s `VanillaDamageListener`. Beide Wege müssen abgewiesen werden, sonst schlägt ein Spieler
mit dem Ziegenhorn zu und richtet Waffenschaden an.

**Kein neuer Sperrmechanismus für die Items.** `BoundItemTag` markiert seit B07 charaktergebundene
Gegenstände, und `EquipmentLockListener` bricht für markierte Items bereits `InventoryClickEvent`,
`InventoryDragEvent`, `PlayerDropItemEvent` und `PlayerSwapHandItemsEvent` ab. Tragen die
Fähigkeits-Items dieselbe Marke, ist FR-057 ohne eine Zeile neuen Sperrcode erfüllt. Für die Zuordnung
„welcher Slot trägt welche Fähigkeit" kommt eine **zweite** Marke dazu, die die Fähigkeits-ID trägt —
die Bindungsmarke sagt nur, dass das Item unbeweglich ist.

**Verworfen:** *Die Fähigkeit aus dem Material ableiten.* Hätte zwei Fähigkeiten mit demselben Material
unmöglich gemacht und ein herbeigeschafftes Ziegenhorn zu einer Fähigkeit gemacht — beides verletzt
FR-058.

---

## R5 · Wie findet eine Flächenfähigkeit ihre Ziele, ohne den Tick zu sprengen?

**Entscheidung:** `TargetResolver` als Schnittstelle in `rpg-core`, Paper-Umsetzung in `rpg-platform`
über `World.getNearbyEntities` mit anschließender Filterung nach Modus.

**Begründung:** Prinzip II verlangt einen räumlichen Index statt linearer Iteration über alle
Kandidaten. `getNearbyEntities` ist genau das — Paper geht über die Chunk-Struktur und berührt nur die
Abschnitte, die die Box schneidet, nicht die Entity-Liste der Welt. Kegel, Linie und „nächstes Ziel"
entstehen daraus durch Nachfiltern einer bereits kleinen Menge.

**Die Obergrenze ist Pflichtfeld, nicht Vorgabewert** (FR-020). Ein Standardwert hätte eine vergessene
Zeile von einer bewussten Entscheidung ununterscheidbar gemacht — dieselbe Begründung, mit der B07
alle Attributfelder verlangt, auch die mit Null. Bei mehr Kandidaten als erlaubt wird **nach
aufsteigendem Abstand** gewählt (FR-021), damit dieselbe Lage dasselbe Ergebnis liefert; eine zufällige
Auswahl wäre nicht reproduzierbar und damit nicht prüfbar.

**Die Trennung ist dieselbe wie bei `MobStatProvider` in B05** und hat denselben Nutzen: Winkel,
Reichweite, Obergrenze und Auswahlreihenfolge sind Regeln und werden serverfrei geprüft; nur das
Nachschlagen in der Welt braucht Paper.

---

## R6 · Wo hängen die passiven Trigger?

**Entscheidung:** An den Einhängepunkten, die B05 bereits hat. **B05 wird nicht erweitert.**

| Trigger | Einhängepunkt |
|---|---|
| `ALWAYS` | keiner — meldet einen `ModifierSet` über `StatEngine.apply` an und ist fertig |
| `ON_DAMAGE_DEALT` | `DamageInterceptor` auf `PipelineStage.APPLICATION`, Sicht des Angreifers |
| `ON_DAMAGE_TAKEN` | `DamageInterceptor` auf `PipelineStage.MODIFIERS`, Sicht des Ziels |
| `ON_KILL` | das Todesereignis, das B05 veröffentlicht |
| `ON_DEATH` | `DamageInterceptor` auf `PipelineStage.APPLICATION`, bevor der Tod eintritt |

**Warum `ON_DAMAGE_TAKEN` auf `MODIFIERS` liegt und nicht auf `APPLICATION`:** Ausweichen muss den
Schaden *verhindern*, nicht nachträglich heilen. Auf `MODIFIERS` ist er noch abweisbar; auf
`APPLICATION` wäre er schon angewandt.

**Warum `ON_DAMAGE_DEALT` umgekehrt auf `APPLICATION` liegt:** Lifesteal heilt einen Anteil des
**tatsächlich zugefügten** Schadens (FR-016). Vor der Mitigation stünde ein Betrag, den das Ziel nie
bekommen hat, und ein Warrior gegen ein gepanzertes Ziel heilte mehr, als er austeilt.

**`ON_DEATH` und die administrative Tötung.** `CombatPipeline.kill` läuft ausdrücklich *ohne Formel und
ohne Attribution* und erreicht die Interceptoren nicht. Damit ist FR-051 — Second Life greift nicht
gegen `/kill` — keine Sonderregel, sondern eine Eigenschaft des vorhandenen Pfads.

**Der Interceptor-Vertrag deckt die Fehlerbegrenzung schon ab:** *„An exception thrown from intercept
is caught, logged with this interceptor's id and confined to the one event."* FR-017 braucht in diesem
Zweig also keine eigene Barriere.

---

## R7 · Doppelsprung ohne eigenen Keybind

**Entscheidung:** `PlayerToggleFlightEvent` abfangen, abbrechen, und dem Spieler stattdessen einen
Aufwärtsimpuls geben.

**Begründung:** Ein Vanilla-Client sendet beim doppelten Druck auf die Sprungtaste in der Luft eine
Flugumschaltung. Wird dem Spieler `allowFlight` gesetzt, ohne ihn fliegen zu lassen, ist dieses
Ereignis die einzige Stelle, an der ein zweiter Sprung ohne Client-Voraussetzung erkennbar wird
(ADR-005). Das Ereignis wird abgebrochen, damit der Spieler nicht tatsächlich fliegt.

**Der Zustand hängt am Boden, nicht an einer Zeit.** `allowFlight` wird beim Bodenkontakt wieder
gesetzt und beim Auslösen genommen; damit ist „ein dritter Sprung vor Bodenkontakt wird nicht
ausgeführt" eine Eigenschaft des Zustands und keine geprüfte Regel.

**Slow Fall ist ein Vanilla-Statuseffekt** und braucht nichts Eigenes — er ist die `StatusEffect`-
Primitive mit dem Vanilla-Effekt als Parameter.

**Verworfen:** *Die Sprünge über `PlayerMoveEvent` zählen.* Hätte in einem der am häufigsten feuernden
Ereignisse des Servers Zustand je Spieler geführt, um etwas zu erkennen, das Paper ohnehin meldet.

---

## Zusammenfassung der Planänderungen

Drei Funde haben den Plan gegenüber dem ersten Entwurf verändert:

1. **R2** — die `Scheduler`-Abstraktion braucht eine fünfte Methode. Das ist der einzige Eintrag in
   der Complexity-Tracking-Tabelle und wird ADR-024.
2. **R3** — die Regeneration hängt an keinem Ereignis. Nebenbei: der Kampfzustand veröffentlicht seine
   verlassende Flanke in der Produktion überhaupt nicht, ein Defekt in B05.
3. **R6** — B05 muss nicht angefasst werden. Der erste Entwurf hatte einen zusätzlichen
   Einhängepunkt für Lifesteal vorgesehen; `PipelineStage.APPLICATION` leistet das bereits.
