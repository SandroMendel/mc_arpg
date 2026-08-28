# Phase 0 · Research — B11 · Items, Ausrüstung & Loot

**Datum:** 2026-08-28 · **Spec:** [spec.md](./spec.md) · **Entscheidungen:** ADR-039

Die Spec hat keine offenen Marker hinterlassen. Alle Punkte hier sind beim Nachsehen im **gebauten
Code** entstanden — und drei davon ändern den Zuschnitt des Blocks noch einmal.

---

## R1 · Wie erreicht der Verschleiß den Ausrüstungsbeitrag?

**Die Frage.** FR-047 verlangt, dass der Beitrag einer Ausrüstung mit sinkendem Zustand abnimmt.
FR-080 verlangt, dass das über eine **benannte Naht** geschieht statt über einen Eingriff in B07.
Wo genau sitzt diese Naht?

**Was nachgesehen wurde.** `ClassStatContributor` liefert die Stufenwerte, und zwar als
`BaseStatContributor` — **Grundwerte, nicht Modifikatoren**. Das Javadoc begründet das ausführlich:

> *„Base values, not modifiers. The modifier band from B04 is laid around the **effective** base
> value […] Were the tier values FLAT modifiers, the band would stay pinned at the level-1 base: a
> band of ±30 % around 40 health would never admit the 1385 a top-tier warrior carries, and the
> value would be clamped unnoticed."*

Folge: `SourceKind.CLASS` und `SourceKind.EQUIPMENT` sind **unbenutzt** — es gibt nichts zu sortieren,
wenn es je Attribut einen Grundbeitrag gibt.

**Entscheidung.** Der Verschleiß wird **nicht** als negativer Modifikator beigetragen, sondern
skaliert den **Grundbeitrag der Stufe**. `ClassStatContributor` bekommt eine zusätzliche, optionale
Naht — eine funktionale Schnittstelle `GearConditionFactor` mit
`double factorFor(UUID characterId, LadderSlot slot)`, die ohne B11 konstant `1.0` liefert.
`contributeTier` reicht den Faktor an `EquipmentLadder.contributeTo` weiter.

**Begründung.** Ein negativer `EQUIPMENT`-Modifikator wäre an genau der Klammer gescheitert, die das
Javadoc beschreibt: er läge im Modifikatorband um einen Grundwert, der den vollen Stufenwert schon
enthält, und würde bei 80 % Abzug stillschweigend abgeschnitten. Genau der Fehler, den B07 mit dem
Grundwert-Ansatz vermieden hat.

**Dass B07 dafür eine Naht bekommt, ist das Hausmuster und kein Bruch von FR-080.** ADR-027 hat es
für B08 genauso gemacht: *„Die Preisprüfung hängt über eine Naht (`AbilityRuntime.RankCost`) darin —
B08 zeigt nicht auf B08b."* Der ältere Block bekommt eine Schnittstelle, der jüngere füllt sie, und
die Abhängigkeitsrichtung bleibt `plugin → platform → core` und B11 → B07. Die Stufenwerte bleiben,
wo sie hingehören.

**Verworfen.**
- **Negativer Beitrag über `SourceKind.EQUIPMENT`** — an der Bandklammer gescheitert, siehe oben.
- **Ein zweiter `BaseStatContributor` aus B11, der den fehlenden Anteil negativ beisteuert** — er
  müsste die Stufenwerte selbst nachschlagen und damit B07s Leiterlogik nachbauen. Zwei Orte, an
  denen dieselbe Zahl entsteht.
- **Ein Skalierungshaken in B04s `StatEngine`** — träfe jeden Beitragenden und wäre eine
  Verallgemeinerung für einen einzigen Anwendungsfall.

**Zur Allokation.** Ein Lambda je `contributeTier`-Aufruf wäre eine Zuweisung im
Neuberechnungspfad. Stattdessen bekommt `contributeTo` den Faktor als `double`-Parameter — die
Multiplikation geschieht dort, wo der Wert ohnehin gelesen wird, und es entsteht kein Objekt
(Prinzip II).

---

## R2 · `item_instance` existiert, wird geladen — und ist der falsche Speicherort

**Der Fund.** Das Baseline-Schema aus B02 führt eine Tabelle `rpg.item_instance`, B03 hat sie mit
`V3_2` auf den Charakter umgehängt (ADR-011), und es gibt dazu `ItemInstance`,
`ItemInstanceRepository` und `JdbcItemInstanceRepository`. **`SessionBundle` lädt die Items eines
Kontos bei jedem Sitzungsstart mit.** Nichts im Spielgeschehen liest sie — die Tabelle wartet seit
B02 auf B11.

**Zwei Wahrheiten, wenn man sie benutzt.** B03 speichert Rucksack und Enderchest als **undurchsichtige
Bukkit-Blobs** (`CharacterInventory.contents`, `.enderChest`). Ein Item liegt also bereits in diesem
Blob. Eine zweite Zeile je Item in `item_instance` müsste mit dem Blob synchron gehalten werden —
bei jedem Aufheben, Ablegen, Verbrauchen, Verkaufen, Vernichten. Zwei Darstellungen desselben
Gegenstands, die genau bis zum ersten Fehler übereinstimmen.

**Dazu kommt: `rolled_values` ist seit ADR-027 gegenstandslos.** Die Spalte trägt den Kommentar
*„template id + rolls only"* — die Formulierung von ADR-004 **vor** dem Neuzuschnitt. Sie ist
`NOT NULL DEFAULT '{}'`, und `JdbcItemInstanceRepository` liest und schreibt sie.

**Entscheidung.** B11 benutzt `item_instance` **nicht**. Das Item lebt im PDC innerhalb von B03s
Inventar-Blob, wie FR-004 es verlangt. Die Tabelle, das Repository, der Record und das Feld in
`SessionBundle` werden **zurückgebaut** — mit einer Migration, die die Tabelle entfernt.

**Begründung.** Eine Tabelle, die geladen wird und die niemand schreibt, ist eine Falle für den
nächsten Block: irgendwann schreibt jemand hinein, und dann gibt es zwei Wahrheiten über dasselbe
Inventar. Genau die Sorte Drift, die Prinzip IV der Constitution sechs Tage lang mit einem überholten
Wortlaut überlebt hat. Der Rückbau kostet heute eine Migration; nach B12 kostet er einen Umbau von
zwei Blöcken — dasselbe Argument, mit dem `V3_2` seinerzeit begründet wurde.

**Verworfen.**
- **Liegen lassen und ignorieren** — der Ladepfad kostet je Sitzungsstart eine Abfrage für nichts,
  und der nächste Block findet eine Tabelle, die aussieht, als solle man sie benutzen.
- **`item_instance` als Speicher benutzen und den Blob aufgeben** — hieße, B03s Inventarhaltung
  zu ersetzen und die Serialisierung von Vanilla-Gegenständen selbst zu übernehmen: Materialien,
  Komponenten, Verzauberungen. Verstößt gegen Prinzip III.1 aus genau dem Grund, den
  `CharacterInventory` im Javadoc nennt.
- **Nur `rolled_values` streichen und den Rest behalten** — löst die halbe Frage und lässt die
  zweite Wahrheit stehen.

**Offen für den Auftraggeber, aber nicht blockierend:** der Rückbau berührt B02 und B03. Er wird als
eigene Aufgabengruppe geführt und ist von den übrigen Aufgaben unabhängig.

---

## R3 · Vanillas Beute ist bereits unterdrückt

**Die Frage.** FR-027 verlangt, dass Vanillas eigene Drops unterdrückt werden.

**Der Fund.** `CombatDeathListener` (B05) tut das bereits:

```java
event.setDroppedExp(0);
event.getDrops().clear();
```

**Entscheidung.** FR-027 ist ohne eine Zeile Code erfüllt. B11 baut nichts und **prüft es mit einem
Test ab**, damit die Zusage nicht unbemerkt wegfällt, wenn jemand B05 anfasst.

---

## R4 · Beute hängt am Kern-Ereignisbus, nicht an Bukkit

**Der Fund.** `CoinDropListener` ist **kein Bukkit-Listener**: er abonniert `CombatDeathEvent` auf
dem Kern-Ereignisbus (`events.subscribe(CombatDeathEvent.class, this::onDeath)`), und
`CoinDropPlanner` in `rpg-core` entscheidet bukkit-frei, wer worauf Anspruch hat. Die Plattform
macht aus jedem Plan eine Entität.

**Entscheidung.** B11 folgt derselben Zweiteilung: ein `LootPlanner` in `rpg-core` entscheidet **was
fällt und wem es gehört** — vollständig ohne Server testbar —, ein Zuhörer in `rpg-platform` setzt
die Gegenstände. Kein Bukkit-`EntityDeathEvent` in B11.

---

## R5 · Die Eigentumsmechanik wird geteilt, nicht kopiert

**Die Frage.** FR-028 bis FR-032 verlangen genau das, was `CoinPile` für Coin-Haufen bereits leistet.
FR-079 verbietet eine zweite Fassung davon. Kopieren scheidet also aus — aber wie teilt man sie?

**Was `rpg.platform.currency` löst, ausweislich seines `package-info`:**

| Falle | Wie sie gelöst ist |
|---|---|
| Sichtbarkeit je Spieler | `setVisibleByDefault(false)` plus `showEntity` für den Berechtigten |
| Sichtbarkeit überlebt keinen Relogin | `CoinPileRegistry.showPilesTo` beim Betreten |
| `setOwner` kennt Spieler, ADR-011 kennt Charaktere | zweite Prüfung im `PlayerAttemptPickupItemEvent` |
| Vanilla verschmilzt ähnliche Stapel | eindeutige Kennung je Haufen macht keine zwei ähnlich |
| Aufräumen liegender Gegenstände | Vanillas Verfall, **keine eigene wiederkehrende Aufgabe** |
| Unsichtbarkeit als einziges Schloss | ausdrücklich abgelehnt — Darstellung ist nie die Autorität |

**Entscheidung.** Die Mechanik wird nach `rpg.platform.drop` **herausgezogen** und von beiden
benutzt: `OwnedDrop` (setzen, sichtbar machen, sperren), `OwnedDropRegistry` (Wiederherstellen nach
Relogin und Charakterwechsel). B08b ruft sie danach auf, statt seine eigene Fassung zu halten.

**Begründung.** Das ist dasselbe Vorgehen, das ADR-029 für `ShareCalculator` gewählt hat: *„Extracted
from `XpDistributor`, not written anew […] a second implementation would have stayed identical only
until somebody touched one of them."* Für die Eigentumsmechanik gilt es stärker, weil sie sechs
Fallen enthält, von denen fünf erst im Betrieb auffallen.

**Was das Verschmelzen für B11 bedeutet.** Bei Coins ist Verschmelzen gefährlich, weil der Betrag im
Datencontainer steht und zwei Haufen zu 500 zu einem Stapel von zwei würden. Bei B11 ist die Gefahr
eine **andere**: zwei gleiche Tränke verschiedener Eigentümer dürfen nicht zusammenfließen, sonst
wechselt Besitz durch bloße Nähe (FR-031). Dieselbe Lösung — eine eigene Kennung je gefallenem
Gegenstand — deckt beide Fälle.

**Verworfen.** **`CoinPile` unverändert lassen und in B11 nachbauen** — genau der Verstoß gegen
FR-079, den die Spec benennt. **Die Mechanik in `rpg-core` ziehen** — sie ist durch und durch
Bukkit (`showEntity`, `setOwner`, `Item`) und gehört in die Plattformschicht.

---

## R6 · Der Reihum-Zeiger der Party

**Die Frage.** FR-026b verlangt, dass die Beute in einer Party reihum geht, FR-026d, dass der Zeiger
Laufzeitzustand der Party ist.

**Der Fund.** `PartyRegistry` führt Partys als reinen Laufzeitzustand und bietet
`partyOf(playerId) → Optional<PartyView>` sowie `membersOf(playerId, UUID[] out)` — eine
Kopiermethode in ein vorgegebenes Feld, also ohne Zuweisung. `Party` selbst hat
`copyMembersInto(UUID[] out)`.

**Entscheidung.** Der Zeiger lebt **in B11**, in einer kleinen Tabelle `partyId → Index`, und wird
über `PartyChangedEvent` aufgeräumt. B06 wird **nicht** angefasst.

**Begründung.** Der Zeiger ist B11s Begriff, nicht B06s: er sagt etwas über Beute, und B06 kennt
keine Beute. Ihn in `Party` zu legen hieße, B06 ein Feld zu geben, das nur ein anderer Block liest —
und es wäre der einzige Teil einer Party, den ein Block außerhalb von B06 schreibt.

**Zur Reichweite.** Gemessen wird zum **gestorbenen Gegner**, wie in B06 (`ShareCalculator`,
Schritt 4). Dieselbe Zahl aus `progression.yml` wird gelesen, nicht eine zweite in `items.yml`
eingeführt — sonst wären Erfahrung und Beute unterschiedlich weit verteilt, und niemand könnte
erklären warum.

---

## R7 · Der NPC und B10s Budget

**Die Frage.** FR-052 verlangt, dass der NPC nicht gegen das Mob-Budget aus B10 zählt.

**Der Fund.** `HordeRegistry` zählt **nur, was es selbst gesetzt hat** — jeder Eintrag trägt
`entityId`, `kindKey`, `zoneKey`, `chunkKey`, `spawnedAt`. Eine Entität, die nicht durch B10s
Platzierung entstanden ist, taucht dort nicht auf.

**Entscheidung.** Es ist nichts zu tun. Der NPC entsteht über eine eigene Platzierung und wird nie
in `HordeRegistry` eingetragen; damit zählt er nicht. Ein Test hält das fest.

**Zur Vanilla-Unterdrückung.** B10 sperrt natürliches Spawnen über Spielregeln **und** einen
Ereignis-Riegel (ADR-033). Der NPC wird nicht *gespawnt*, sondern gesetzt — derselbe Weg, den B10
für seine Kreaturen benutzt, und der `SpawnReason` ist entsprechend `CUSTOM`.

---

## R8 · Wo der Zustand und die Kosmetik liegen

**Entscheidung.** Zwei neue Tabellen, beide am **Charakter** (ADR-011), beide mit dem Muster von
`V7_1__character_class_progress.sql`:

- `character_gear_condition` — zwei Werte je Charakter, einer je Slot
- `character_cosmetic` — welche Trimfarben gekauft sind und welche angewandt ist

**Begründung.** Beides ist Charakterfortschritt und gehört in dieselbe Form wie Level, Stufen und
Kontostand. Beides ist klein, wird bei Sitzungsstart geladen und über den Write-Behind-Puffer
geschrieben — kein Datenbankzugriff je Spielereignis (Prinzip II).

**Warum nicht in `character_class_progress` mit hinein.** Die Stufe ist B07s Begriff, der Zustand
ist B11s. Eine Spalte eines fremden Blocks in einer fremden Tabelle ist der Anfang davon, dass
niemand mehr weiß, wem sie gehört.

---

## R9 · Verbrauchbares hängt an B08s Buff-Naht

**Der Fund.** `SourceKind.BUFF` ist B08s Weg in die Werteberechnung, und B08 wertet Laufzeiten
zeitstempelbasiert aus (Prinzip II).

**Entscheidung.** Ein Trank mit zeitlicher Wirkung trägt über dieselbe Naht bei wie eine Fähigkeit.
B11 führt **keine** eigene Buff-Verwaltung und **keine** eigene Ablaufprüfung ein.

**Offene Feinheit, in Phase 1 entschieden:** die Abklingzeit eines Verbrauchbaren (FR-035) ist
**nicht** dieselbe wie eine Fähigkeits-Abklingzeit — sie hängt an der Vorlage, nicht an einem Rang.
Sie wird wie alles Zeitliche in diesem Projekt aus zwei Zeitstempeln gerechnet, nie als laufende
Aufgabe.

---

## R10 · Reparatur, Amboss und die Coin-Route

**Die Frage.** FR-056 verlangt, dass Vanillas Amboss- und Verzauberungswege gesperrt bleiben.

**Der Fund.** Klassenausrüstung ist `setUnbreakable(true)` und trägt
`ItemFlag.HIDE_UNBREAKABLE`; `EquipmentLockListener` bricht bereits Klick, Drag, Hand-Tausch und
Wurf ab. Ein Amboss käme über ein Inventar vom Typ `ANVIL` — ein Weg, den der vorhandene Zuhörer
nicht abdeckt, weil er auf `InventoryClickEvent` ohne Typprüfung reagiert.

**Entscheidung.** Ein eigener kleiner Zuhörer sperrt das Öffnen von Amboss, Zauberpult und
Schleifstein für gebundene Ausrüstung. Er gehört zu B11, weil er die Reparaturroute schützt, die
B11 einführt.

---

## Zusammenfassung: was sich am Zuschnitt noch geändert hat

| Fund | Wirkung auf den Umfang |
|---|---|
| R1 | B07 bekommt eine additive Naht — kleiner Eingriff, aber ein Eingriff. Muss im Constitution Check auftauchen |
| R2 | **Rückbau statt Aufbau**: `item_instance`, Repository, Record und das Feld in `SessionBundle` fallen weg |
| R3 | FR-027 ist bereits erfüllt — nur noch abzusichern |
| R5 | Die Eigentumsmechanik wird aus B08b **herausgezogen**, nicht in B11 nachgebaut |
| R7 | Für das Budget ist nichts zu tun |
