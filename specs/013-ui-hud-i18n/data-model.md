# Phase 1 · Data Model — B13 · UI, HUD & Texte

**Nichts hiervon wird persistiert.** B13 legt kein Schema, keine Tabelle und keine Migration an
(FR-013b, SC-011). Was hier steht, lebt entweder in der Konfiguration, für die Dauer einer Sitzung
im Speicher, oder für Sekunden in der Welt. Der Block lässt sich entfernen, ohne dass Spielerdaten
fehlen — und das ist eine Zusage, kein Zufall: Anzeigen sind nur serverweit abschaltbar, also gibt
es keine persönliche Einstellung, die jemand aufheben müsste.

Alle Werte, die angezeigt werden, gehören anderen Blöcken. B13 hält **keine zweite Fassung** von
Leben, Mana, Level, Coins, Zone oder Ausrüstungszustand — es liest sie bei Bedarf über die
öffentlichen Nähte und zeichnet sie.

---

## 1 · Konfiguration (`ui.yml`, gelesen beim Start)

### `UiConfig`

Der geprüfte Inhalt von `ui.yml`. Schema und Regeln in
[contracts/ui-config.md](./contracts/ui-config.md).

| Feld | Typ | Regel |
|---|---|---|
| `language` | `String` | nicht leer; benennt den Sprachsatz (R8) |
| `tick` | `Duration` | positiv; Standard 1 s (FR-010, FR-011) |
| `actionBar` | `SurfaceSetting` | — |
| `bossBar` | `SurfaceSetting` | — |
| `sidebar` | `SurfaceSetting` | — |
| `zoneNoticeDuration` | `Duration` | positiv; wie lange der Zonenname steht. **Gelesen aus `hud.boss-bar.zone-notice-seconds`** (Ganzzahl Sekunden) und dort in eine `Duration` umgerechnet — das Modell führt eine Dauer, die Datei eine Zahl mit Einheit im Namen. `UiConfigSchema` ist die einzige Stelle, die beides kennt |
| `damageNumbers` | `DamageNumberSetting` | — |

### `SurfaceSetting`

| Feld | Typ | Regel |
|---|---|---|
| `enabled` | `boolean` | abgeschaltet heißt **kostenlos**, nicht unsichtbar (FR-013a) |

### `DamageNumberSetting`

| Feld | Typ | Regel |
|---|---|---|
| `enabled` | `boolean` | FR-045 |
| `lifetime` | `Duration` | positiv; wie lange eine Zahl steht (FR-043) |
| `offset` | `double` | Höhe über dem Trefferpunkt, endlich |

### `LanguageSet`

Ein Sprachsatz und die Datei, aus der er kommt. **Vollständig oder der Start bricht ab** (FR-018) —
geprüft von `MessageKeyValidator.verifyAllPresent`, das bereits **alle** fehlenden Schlüssel auf
einmal meldet (R8).

| Feld | Typ | Regel |
|---|---|---|
| `code` | `String` | nicht leer, z. B. `en` |
| `file` | `Path` | muss existieren |
| `messages` | `Messages` | über `MapMessages.fromNested` |

> **Die zwei Sonderwege bleiben.** B09 prüft seine Zonennamen und B10 seine Artnamen selbst, weil
> deren Schlüssel erst nach dem Lesen von `zones.yml` beziehungsweise `mobs.yml` feststehen. Ein
> Sprachwechsel darf sie nicht umgehen, sonst fällt eine unvollständige Übersetzung genau dort
> durch, wo die meisten Schlüssel liegen.

---

## 2 · Die drei Flächen

### `HudSurface` (Aufzählung)

| Wert | Rolle | Was darauf steht |
|---|---|---|
| `ACTION_BAR` | die laufenden Werte | Leben, Mana, Verteidigung |
| `BOSS_BAR` | das Situative | Zonenname, Bosskampf, Kanalisierung |
| `SIDEBAR` | die Übersicht | Level, XP, Coins, Zone |

**Jeder Wert gehört genau einer Fläche** (FR-001). Die Zuordnung ist eine Aufzählung und keine
Konfiguration: sie zur Wahl zu stellen hieße, jedem Betreiber die Frage zu überlassen, die dieser
Block gerade beantwortet hat — und zwei Flächen, die denselben Wert tragen, sind kein Layout,
sondern eine doppelte Wahrheit auf dem Bildschirm.

> **Die Actionbar hat den Fortschritt verloren** (FR-002a). `StatusActionBar.progressText` rendert
> heute `level`, `xp` und `xpNext` — genau die zwei Sidebar-Zeilen. Beim Umzug hinter `HudRenderer`
> entfällt dieser Teil. Wer die alte Zeile sucht: sie steht jetzt links, nicht mehr unten.
>
> **Eine benannte Ausnahme, und nur eine** (FR-001a): die Vanilla-Erfahrungsleiste zeigt Level und
> Erfahrung ein zweites Mal. Sie ist keine Zuordnungsentscheidung dieses Blocks, sondern eine
> Fläche, die B06 bespielt — `ExperienceBar` bleibt unverändert. Eine **zweite** Ausnahme ist
> ausgeschlossen (FR-001b), sonst prüft der Wächter aus FR-001c nur noch, was übrig blieb.

### `BossBarOccasion` (Aufzählung, **geordnet**)

Es gibt **genau eine** Bossbar je Spieler (FR-004b). Die Reihenfolge der Aufzählung *ist* die
Rangfolge (FR-004):

| Rang | Wert | Dauer | Warum hier |
|---|---|---|---|
| 1 | `CHANNELLING` | Sekunden | hält den Spieler gerade fest; ohne Balken weiß er nicht, wie lange noch |
| 2 | `BOSS_FIGHT` | Minuten | kommt nach der Kanalisierung von selbst zurück — der Zustand war nicht vorbei, nur verdeckt |
| 3 | `ZONE_NAME` | Sekunden, einmalig | reine Information |

**Ein verdrängter Anlass wird nicht nachgeholt** (FR-004a). Ein Zonenname, der während eines
Bosskampfs anfiele, entfällt: nachgereicht wäre er eine Meldung über etwas, das längst vorbei ist.

Nur `BOSS_FIGHT` und `CHANNELLING` kehren zurück, und zwar nicht durch eine Warteschlange, sondern
weil ihr Zustand beim nächsten Takt noch besteht. Das ist der Unterschied zwischen einem Zustand und
einem Ereignis, und er macht die Warteschlange überflüssig.

### `BarProgress`

Der Füllstand eines Balkens, aus **zwei Zeitstempeln gegen die Uhr** — keine Aufgabe, kein Ticker
(Constitution II.2).

| Feld | Typ | Quelle |
|---|---|---|
| `startedAt` | `Instant` | `RunningAbility.startedAt` (R4) |
| `dueAt` | `Instant` | `RunningAbility.dueAt` |
| `fraction(now)` | `double` | auf `[0,1]` begrenzt |

Für `BOSS_FIGHT` kommt der Füllstand stattdessen aus `CombatStatusSource.Status.fraction()` — die
Methode existiert und antwortet für eine Kreatur genauso wie für einen Spieler (R3).

### `SidebarLines`

Welche Zeile welchen Wert trägt. Jede Zeile ist ein Message-Schlüssel mit Platzhaltern, nie ein
Text (Prinzip V).

| Zeile | Wert | Gelesen bei | Neu gezeichnet durch |
|---|---|---|---|
| Level | `ProgressView.level` | B06 | `LevelUpEvent`, `ProgressChangedEvent` |
| Erfahrung | `ProgressView.xpInLevel` / `xpForNextLevel` | B06 | `ProgressChangedEvent` |
| Coins | `Currency` | B08b | **nur den Sammeltakt** (FR-009a) |
| Zone | `ZoneChangedEvent.to`, sonst „Wildnis" | B09 | `ZoneChangedEvent` |

> **Drei von vier Zeilen haben ein Ereignis, die vierte nicht.** In `rpg.core.currency` gibt es
> keinen Ereignistyp — nur `CoinLedger`, `LedgerEntry` und `BookingResult`. Die Coin-Zeile folgt
> deshalb dem Takt und steht bis zu eine Sekunde später (FR-009a). **Nachgerüstet wird nichts:** ein
> Ereignis in B08b wäre der Eingriff in einen fremden Block, den dieser Block bei `AbilityHotbar`,
> `ClassSelectionMenu` und B12s Fenstern ausdrücklich ablehnt. Wenn die Sekunde später stört, gehört
> das Ereignis in B08b — als eigene Aufgabe, mit eigenem Namen.
>
> `StatusActionBar` hört auf `ProgressChangedEvent` und `LevelUpEvent` bereits (Zeilen 95 und 98).
> Diese zwei Abonnements **wandern** nach `HudRefresh` — sie werden nicht ein zweites Mal daneben
> angelegt.

> `ProgressView.atMaxLevel()` ist ein eigenes Feld und keine abgeleitete Regel — am Maximum ist die
> Schwelle 0, und `4120/0` sähe aus wie ein Fehler. `StatusActionBar` nutzt das bereits so; die
> Sidebar folgt derselben Unterscheidung, statt sie ein zweites Mal leicht anders zu erfinden.

---

## 3 · Die Schadensanzeige

### `DamageNumber`

Eine kurzlebige Anzeige in der Welt. **Der einzige Zustand, den dieser Block überhaupt in die Welt
schreibt — und er schreibt ihn bewusst flüchtig** (R7).

| Feld | Typ | Regel |
|---|---|---|
| `viewerId` | `UUID` | **nur der Verursacher sieht sie** (FR-042) |
| `position` | `WorldPosition` | der Trefferort |
| `amount` | `double` | aus `DamageDealtEvent.totalDamage` — **nach** Verteidigung |
| `hitCount` | `int` | wie viele Schläge im Fenster waren |
| `lethal` | `boolean` | ob das Fenster mit dem Tod endete |
| `expiresAt` | `Instant` | `lifetime` aus der Konfiguration |

**Drei Eigenschaften, in denen sie das genaue Gegenteil von B12s Hologramm ist:**

| | `LeaderboardHologram` | `DamageNumber` |
|---|---|---|
| Persistenz | **persistent** — sonst nach dem ersten Chunk-Entladen weg | **nicht persistent** — sonst überlebt jede einzelne einen Neustart |
| Sichtbarkeit | für alle | nur für den Verursacher |
| Aufräumen | **vor** dem Setzen, weil ein Absturz kein Herunterfahren hat | eingeplante Entfernung im selben Tick, in dem sie entsteht |

Die dritte Zeile ist zugleich die Antwort auf die Falle aus R2: **innerhalb** des Ticks geplant ist
die Entität aufgelöst und der Thread der richtige. Aus dem asynchronen Takt heraus wäre
`runSyncOnEntity` still gescheitert — derselbe Fehler, der B10 bei T112 einen halben Tag gekostet
hat.

**Warum die Menge beherrschbar bleibt:** `DamageAggregator` liefert *ein* Ereignis je
Angreifer-Ziel-Paar je Fenster, nicht je Treffer. Zwanzig Schläge sind eine Zahl mit
`hitCount == 20`.

---

## 4 · Die Cooldown-Anzeige

### `CooldownOverlay`

Kein eigener Zustand — eine Abbildung von B08s Antwort auf das Vanilla-Overlay.

| Was | Woher |
|---|---|
| Restzeit | `AbilityRegistry.remainingCooldown(characterId, abilityId)` (R5) |
| Welcher Slot welche Fähigkeit ist | `AbilityItemTag` (B08 markiert jedes gelegte Item) |
| Welches Material | `Ability.item()` für Aktive, `Ability.items()` für Passivmarker |

### `MaterialUniqueness` — die Startprüfung

**Die Anzeige hängt am Material des Spielers, nicht am Slot.** Zwei Fähigkeiten einer Klasse auf
demselben Material hießen: ein Cooldown läuft, zwei Slots werden grau, und einer davon lügt.

Geprüft wird über die **Vereinigung aus `item()` und `items()` je Klasse** (FR-032). Dass `items()`
Mehrzahl ist, ist kein Detail: der Magier belegt mit *Rise & Fall* zwei Slots aus einer Fähigkeit
(R6), und eine Klasse kommt auf bis zu sieben von neun Slots.

Bricht ab mit Datei, Schlüssel und Grund (Prinzip V). Im Spiel sähe der Fehler wie ein
Balancing-Zufall aus — das ist der Grund, warum er beim Start auffallen muss und nicht später.

---

## 5 · Die Charakterübersicht

### `CharacterSheet`

Ein zwischengespeicherter Fensterinhalt, gebunden an **einen** Charakter (FR-054).

| Feld | Typ | Quelle |
|---|---|---|
| `characterId` | `UUID` | der **aktive** Charakter (FR-053) |
| `attributes` | `Map<Attribute, Double>` | `StatSnapshot.get` über **jedes** `Attribute` (B04) |
| `revision` | `long` | `StatSnapshot.revision()` — **die Ungültigkeitsmarke** |
| `equipment` | `List<EquippedItem>` | `Items` (B11) |
| `conditions` | `Map<Slot, Condition>` | `GearConditions` (B11) |
| `level` | `int` | `ProgressView` (B06) |
| `coins` | `long` | `Currency` (B08b) |
| `classKey` | `String` | B07 |

> **Es sind zehn Attribute, nicht acht** (R10). `Attribute` führt `HEALTH`, `HEALTH_REGEN`,
> `DEFENSE`, `MANA`, `MANA_REGEN`, `PHYSICAL_DAMAGE`, `MAGIC_DAMAGE`, `ATTACK_SPEED`,
> `MOVEMENT_SPEED` und `ABILITY_COOLDOWN` — das letzte als einziges prozentual. Die Zahl „acht"
> stammt aus dem Meilensteinziel M2 der Roadmap und war dort nie als Aufzählung gemeint. Die
> Übersicht zeigt deshalb *jedes Attribut aus `Attribute`* und nicht eine festgeschriebene Zahl:
> die wäre beim elften still falsch geworden.

**Neu aufgebaut wird nur bei Änderung** (FR-054). `StatSnapshot.isNewerThan` beantwortet das
bereits — es gibt eine Revision, also braucht niemand eine eigene Ungültigkeitsmarke zu erfinden.

**Beim Charakterwechsel wird geschlossen, nicht neu aufgebaut** (FR-055). Ein stiller Neuaufbau mit
anderen Zahlen sieht aus wie ein Fehler, und der Spieler klickt weiter, ohne zu merken, dass er
woanders ist.

---

## 6 · Was B13 ausdrücklich **nicht** modelliert

| Nicht hier | Warum |
|---|---|
| Anzeigeeinstellung je Spieler | Nur serverweit abschaltbar (FR-013b) — kein dauerhafter Zustand, kein B02-Schema |
| Eine zweite Fassung von Leben, Level, Coins, Zone | Gelesen, nicht gespiegelt. Zwei Fassungen sind zwei Antworten |
| Das Skill-Leisten-Layout | Bleibt bei `AbilityHotbar` (FR-024) |
| Regeln hinter den übernommenen Fenstern | Welche Wegpunkte offen sind und was eine Reise kostet, bleibt B09 und B08b (FR-063) |
| Die Bündelung der Treffer | Liegt in B05, weil „was ein Schlag ist" eine Kampffrage ist |
