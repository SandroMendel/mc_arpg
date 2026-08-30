# Phase 0 · Research — B13 · UI, HUD & Texte

Zehn Fragen an den **gebauten Code**, nicht an die Literatur. B13 ist ein Brownfield-Block: über
vierzig Stellen im Baum verweisen auf ihn, vier Anzeigen stehen schon, zwei Fenster sind geliehen.
Was hier zu klären war, stand deshalb fast immer in einer vorhandenen Datei — und zweimal stand
dort etwas anderes, als die Spec angenommen hatte.

---

## R1 · Gibt es einen Takt, auf dem der Sammeltakt mitreiten kann?

**Entscheidung:** Ja — und es ist der von `StatusActionBar.startRefresh`. B13 legt **keinen zweiten**
an, sondern erweitert diesen einen zum HUD-Takt.

**Begründung.** `StatusActionBar.REFRESH` ist bereits eine Sekunde, mit genau der Begründung, die
FR-010 und FR-011 verlangen: *„A second is under the roughly two seconds Minecraft takes to fade the
bar, so it never visibly blinks, and it is the longest interval for which that is true."* Der Takt
existiert also, er hat die richtige Länge, und er hat sie aus dem richtigen Grund.

Wichtiger noch ist, **wie** er gebaut ist. Der `Scheduler` dieses Projekts hat *keine*
wiederkehrende Aufgabe — bewusst, nach ADR-007 und Constitution I.5. `startRefresh` plant sich
deshalb am Ende jedes Durchlaufs selbst neu ein (`runAsyncDelayed` plus Wiederbewaffnung). Das ist
das einzige Muster, in dem ein Takt in diesem Projekt überhaupt ausgedrückt werden kann, und es hat
zwei Eigenschaften, die hier zählen: jede Intervallentscheidung steht an der Aufrufstelle, und ein
scheiternder Takt hört auf, statt sich aufzustauen.

Constitution II.2 verbietet wiederkehrende Aufgaben **je Spieler oder je Entität**; eine einzelne
serverweite Systemaufgabe ist ausdrücklich keine. Ein zweiter serverweiter Takt wäre zwar erlaubt,
aber er wäre die falsche Antwort: zwei Takte ergeben zwei Alter, und dann muss jemand erklären,
welches gemeint ist. Genau diese Begründung steht schon in B12s `LeaderboardHologram`.

**Alternative verworfen:** ein eigener B13-Takt. B12 hat dieselbe Frage mit demselben Ergebnis
beantwortet und ist auf den vorhandenen Inventar-Sweep aufgesessen (`PlaytimeAccrual`), B11 vorher
auf den Fähigkeiten-Sweep (`ConsumableBuffs.expire`). Drei Blöcke, dieselbe Antwort — das ist ein
Muster und keine Sparmaßnahme.

---

## R2 · Darf der Sammeltakt Bukkit anfassen? Die Falle aus T112

**Entscheidung:** Der Takt läuft **asynchron**. Für einen **Spieler** ist `runSyncOnEntity` von dort
aus in Ordnung; für alles andere — namentlich die Schadensanzeigen — ist es **`runSyncAtLocation`**.

**Begründung.** `PaperSchedulerAdapter.resolve(UUID)` gibt außerhalb des Primary-Threads für alles
außer einem Spieler `null` zurück, und `runSyncOnEntity` bricht dann mit einem bereits abgebrochenen
`TaskHandle` ab — **kein Fehler, keine Ausnahme, nur eine leise verworfene Aufgabe.** Genau das hat
B10 bei T112 einen halben Tag gekostet: `HordeSweep.removeEntity` sah funktionsfähig aus, solange
Vanillas eigener Despawn zufällig dieselbe Wirkung erzielte.

Für `StatusActionBar` ist das heute unkritisch — ein Halter, der die Actionbar erreicht, ist ein
Spieler, und `server.getPlayer` geht auch async. Der Takt ist also nicht kaputt. **Aber die Regel
gilt für alles Neue, das B13 in denselben Takt hängt**, und der erste Kandidat ist genau der
gefährliche Fall: eine Schadensanzeige ist keine Spielerentität.

**Wie es aussieht, wenn man es falsch macht:** der Code läuft, die Tests bleiben grün, und die
Anzeige erscheint nur manchmal — nämlich dann, wenn der Weg zufällig doch über den Tick kam.

**Alternative verworfen:** den Takt synchron machen. Das verstößt gegen Constitution I.3 und würde
die Zeichenarbeit für 200 Spieler in den Tick legen — genau das, was SC-001 verhindern soll.

---

## R3 · Woher weiß die Bossbar, dass ein Bosskampf läuft?

**Entscheidung:** Aus zwei vorhandenen Nähten, ohne eine dritte: `DamageDealtEvent.targetId` sagt,
**wen** der Spieler schlägt; `MobKinds.ofEntity(targetId)` beantwortet, ob es ein Boss ist
(`MobKind.boss`); und `CombatStatusSource.statusOf(targetId)` liefert Leben, Höchstleben und
Prozentwert — also genau den Balkenfüllstand.

**Begründung.** Alle drei existieren und antworten leer statt zu werfen. `CombatStatusSource` ist
ausdrücklich nicht spielerspezifisch: `StatusActionBar` erklärt, dass Kreaturen „durch dieselbe
Engine gehen und dieselben Ereignisse auslösen" — die Statusabfrage für einen Mob ist also
beantwortet, es fehlte nur die Fläche, sie zu zeigen.

**Warum nicht `CombatState`.** `CombatState.isInCombat(holderId)` sagt *dass* jemand kämpft, nicht
*gegen wen*. Für die Bossbar reicht das nicht: sie zeigt das Leben eines bestimmten Gegners.
`CombatState` bleibt trotzdem nützlich — es beantwortet, wann die Leiste wieder verschwindet.

**Alternative verworfen:** B10 um eine „laufender Bosskampf"-Abfrage erweitern. Das wäre ein
Eingriff in einen fertigen Block für eine Information, die aus zwei vorhandenen Antworten folgt.

---

## R4 · Und dass eine Fähigkeit gerade kanalisiert wird?

**Entscheidung:** `RunningAbility` trägt alles Nötige: `phase` (`WINDING_UP` / `RUNNING`),
`startedAt` und `dueAt`. Balkenfüllstand ist die Strecke zwischen beiden, gegen die Uhr — eine
Rechnung, keine Aufgabe.

**Begründung.** Der Datensatz existiert seit ADR-025 und hält beide Phasen bewusst in *einem*
Objekt: *„the same ability is first the one and then the other."* B13 muss deshalb nichts
zusammensetzen und keinen zweiten Zustand führen. Und weil `dueAt` ein Zeitstempel ist, wird der
Balken im Sekundentakt neu gerechnet statt von einer Aufgabe getrieben — Constitution II.2, ohne
Zutun.

**Warum die Kanalisierung die Rangfolge gewinnt** (FR-004): sie dauert Sekunden, sie hält den
Spieler gerade fest, und ohne Balken weiß er nicht, wie lange noch. Ein Bosskampf dauert Minuten
und kommt danach von selbst zurück — der Zustand ist ja nicht vorbei, nur verdeckt gewesen.

---

## R5 · Wie kommt die Restzeit eines Cooldowns auf den Slot?

**Entscheidung:** `AbilityRegistry.remainingCooldown(characterId, abilityId)` liefert
`Optional<Duration>`. Daraus wird das Vanilla-Overlay gesetzt. **Keine zweite Rechnung** (FR-031).

**Begründung.** Die Restzeit wird in B08 zeitstempelbasiert *auf Anfrage* ausgewertet
(`AbilityState.runningCooldown`) — genau das Muster, das Constitution II.2 verlangt, und
`AbilityRuntime` benutzt dieselbe Methode bereits selbst. Eine eigene Zählung in B13 wäre eine
zweite Wahrheit über dieselbe Zahl.

**Welcher Slot welche Fähigkeit ist**, beantwortet `AbilityItemTag` — B08 markiert jedes gelegte
Item. B13 muss nichts raten und nichts spiegeln.

---

## R6 · Die Materialgrenze des Cooldown-Overlays — wie groß ist sie wirklich?

**Entscheidung:** Größer als die Spec annahm. Die Startprüfung aus FR-032 muss **alle** Materialien
einer Klasse vergleichen, nicht nur die der aktiven Fähigkeiten.

**Begründung.** `AbilityHotbar.layOut` legt zweierlei: für jede **aktive** Fähigkeit ein Item
(`ability.item()`), und für jede **passive mit Markierung** *je Material eines*
(`ability.items()` — Mehrzahl). Der Kommentar nennt den Fall ausdrücklich: *„the mage's Rise & Fall
shows two, one for the jump and one for the fall."* Eine Klasse belegt damit bis zu sieben von neun
Slots.

Das Vanilla-Overlay hängt am **Material des Spielers**, nicht am Slot. Zwei gleiche Materialien in
einem Loadout hießen also: ein Cooldown läuft, zwei Slots werden grau — und einer davon lügt.

Die Prüfung gehört deshalb über die Vereinigung aus `item()` und `items()` **je Klasse**, beim
Start, mit Datei, Schlüssel und Grund (Prinzip V, Fail-Fast). Sie ist billig und sie ist die
einzige Stelle, an der der Fehler noch sichtbar ist: im Spiel sähe er wie ein Balancing-Zufall aus.

**Alternative verworfen:** die Doppelung zur Laufzeit erkennen und dann nichts anzeigen. Das
verschiebt einen Konfigurationsfehler in eine stille Anzeigelücke.

---

## R7 · Schadenszahlen: was das Hologramm vormacht — und was man umdrehen muss

**Entscheidung:** Dieselbe Technik wie `LeaderboardHologram` (`TextDisplay` auf echtem Paper
belegt), aber in **drei Punkten genau umgekehrt**: nicht persistent, nicht für alle sichtbar, und
mit geplanter Entfernung statt Aufräumen beim nächsten Start.

**Begründung.** Das Hologramm ist persistent, *und es muss es sein* — sonst wäre es nach dem ersten
Chunk-Entladen weg. Genau daraus folgt aber sein Aufräumproblem: es überlebt auch einen Absturz, und
deshalb räumt es **vor** dem Setzen auf, nicht beim Herunterfahren („ein Absturz hat kein
Herunterfahren").

Eine Schadenszahl hat die entgegengesetzte Lebensdauer. Sie soll Sekunden leben, nicht Monate.
Persistent gesetzt wäre jede einzelne ein Stück Müll, das einen Neustart überlebt — bei 150 Spielern
in Minuten Tausende. **Nicht persistent zu setzen ist deshalb nicht Feinschliff, sondern das, was
SC-009 überhaupt erfüllbar macht:** was der Server nicht speichert, kann ein harter Abbruch nicht
zurücklassen.

**Entfernt wird über eine eingeplante Einzelaufgabe**, gesetzt in demselben Tick, in dem die Anzeige
entsteht. Dort ist die Entität aufgelöst und der Thread der richtige, also greift
`runSyncOnEntityDelayed` sauber — die Falle aus R2 tritt genau dann nicht auf, wenn man innerhalb
des Ticks plant statt aus dem Takt heraus.

**Nur der Verursacher sieht sie** (FR-042): die Entität wird standardmäßig unsichtbar gesetzt und
dem einen Spieler gezeigt. Das ist Paper-API und gehört damit in `rpg-platform`.

**Warum die Menge beherrschbar bleibt:** `DamageAggregator` liefert *ein* Ereignis je
Angreifer-Ziel-Paar je Fenster, nicht je Treffer — `DamageDealtEvent.hitCount` sagt, wie viele
Schläge darin waren. Die Bündelung liegt bewusst in B05, weil „was ein Schlag ist" eine
Kampffrage ist: *„B13 should get a number to draw, not a firehose to filter."*

---

## R8 · Wie viel Arbeit ist die zweite Sprache wirklich?

**Entscheidung:** Fast keine. Die Maschinerie steht vollständig; es fehlt die **Auswahl der Datei**.

**Begründung.** `RpgPlugin.loadMessages` sammelt heute schon die Schlüssel *aller* Module in einer
Liste und übergibt sie an `MessageKeyValidator.verifyAllPresent`, das **alle** fehlenden auf einmal
meldet — *„an operator fixing a configuration file wants the whole list in one pass."* Das ist
genau FR-018, und es ist gebaut.

Was B13 ändert, ist eine Zeile: statt `messages.yml` wird die Datei der konfigurierten Sprache
gelesen. Alles andere — Ladeweg (`MapMessages.fromNested`), Platzhalterersetzung, das laute
Scheitern bei fehlendem Text (`MissingMessageException`, nie ein Platzhalter) — bleibt.

**Ein Muster, das erhalten bleiben muss:** B09 und B10 prüfen ihre Schlüssel **selbst**, weil deren
Namen erst nach dem Lesen von `zones.yml` beziehungsweise `mobs.yml` feststehen. Ein Sprachwechsel
darf diese zwei Sonderwege nicht umgehen, sonst fällt eine unvollständige Übersetzung genau dort
durch, wo die meisten Schlüssel liegen.

**Alternative verworfen:** Sprache je Spieler aus dem Client-Locale. Ein Vanilla-Client meldet sie
zwar, aber jeder Text müsste dann mehrfach gehalten und je Empfänger aufgelöst werden — auf einer
Zeile, die jede Sekunde für 200 Spieler entsteht. Das ist ein eigener Block, kein Nebeneffekt.

---

## R9 · Der Wächter gegen hartcodierte Texte — gibt es ein Muster?

**Entscheidung:** Ja, gleich vier. Der neue Test folgt `ConfigOnlyAbilityTest`,
`ConfigOnlyItemTest`, `ConfigOnlyMobTest` und `NoRawTypeNameLeftTest`.

**Begründung.** Das Projekt sichert solche Zusagen konsequent **maschinell an der Quelle** und nicht
durch Disziplin. `NoRawTypeNameLeftTest` zeigt dabei auch die Bauart im Detail: Kommentare werden
vor der Suche entfernt („eine Klasse zu erklären ist erlaubt, sie zu rufen nicht"), und eine
Ausnahmeliste wird **selbst geprüft** — ein Wächter, dessen Ausnahmen ins Leere zeigen, bewacht
nichts.

Für FR-015 heißt das: gesucht wird nach Zeichenketten, die an eine Ausgabemethode gehen
(`sendMessage`, `sendActionBar`, Titel, Anzeigenamen), und erlaubt ist dort nur, was aus `Messages`
kommt.

**Was der Wächter nicht kann** und was deshalb dazugehört: er sieht keine Schlüssel, die zwar
existieren, aber nie deklariert werden. Deshalb bleibt die Deklarationsliste aus R8 die zweite
Hälfte der Zusage.

---

## R10 · Zwei Annahmen der Spec, die der Code widerlegt hat

**Erstens: es sind nicht acht Attribute, sondern zehn.** `Attribute` führt `HEALTH`,
`HEALTH_REGEN`, `DEFENSE`, `MANA`, `MANA_REGEN`, `PHYSICAL_DAMAGE`, `MAGIC_DAMAGE`, `ATTACK_SPEED`,
`MOVEMENT_SPEED` und `ABILITY_COOLDOWN` — das letzte als einziges prozentual (`AttributeKind`).
Die Zahl „acht" stammt aus dem Meilenstein-Ziel M2 der Roadmap und war dort nie als Aufzählung
gemeint. Die Spec nennt jetzt die Quelle statt einer Zahl: *jedes Attribut aus `Attribute`*. Eine
festgeschriebene Zahl wäre beim elften Attribut still falsch geworden.

**Zweitens: die vorhandenen Fenster teilen sich nichts.** `CurrencyMenu`, `WaypointMenu` und
`StatisticsMenu` sind drei unabhängige `final class` ohne gemeinsame Basis, jede mit eigenem
Listener. Der Umzug aus FR-060 und FR-061 ist deshalb ein echter Umzug und keine
Vererbungsänderung — und B13 baut den gemeinsamen Rahmen für **seine drei** (die zwei übernommenen
plus die Charakterübersicht), nicht für alle fünf. Die zwei, die bleiben, bleiben unberührt
(FR-070, FR-071).
