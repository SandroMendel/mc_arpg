# Phase 1 — Datenmodell: B06 · Progression

**Datum**: 2026-08-20 | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

Alle Typen liegen in `rpg.core.progression`, ausser den drei Persistenztypen. Nichts hier hat eine
Bukkit-Abhängigkeit.

---

## 1. Fortschrittsstand

### `ProgressState`

Der einzige veränderliche Zustand, den B06 persistiert.

| Feld | Typ | Regeln |
|---|---|---|
| `level` | `int` | 1 bis Maximallevel aus der Kurve. Nie 0, nie negativ. |
| `xpInLevel` | `long` | ≥ 0. Auf Maximallevel immer 0. Sonst normalerweise kleiner als die Schwelle des nächsten Levels — siehe Randbedingung unten. |

**Warum `long` und nicht `int`**: die Kurve ist frei konfigurierbar; eine Schwelle von über zwei
Milliarden ist unwahrscheinlich, aber ein Überlauf wäre ein stiller Rechenfehler statt eines
Startfehlers. `long` kostet vier Byte je Charakter.

**Warum keine Gesamt-XP**: FR-053a. Eine Gesamt-XP-Zahl macht das Level zur Funktion der aktuellen
Kurve; eine Kurvenänderung würde bestehende Charaktere rückwirkend im Level senken.

**Randbedingung — gesenkte Kurve**: Wird die Kurve nachträglich *gesenkt*, kann ein gespeicherter
`xpInLevel` über der neuen Schwelle liegen. Das ist kein ungültiger Zustand, sondern ein anstehender
Aufstieg: beim Laden wird derselbe Aufstiegscode angewendet, der auch ein normales XP-Ereignis
verarbeitet. Damit bleibt Steigen die einzige Richtung, ohne dass das Laden einen Sonderfall braucht.

**Startzustand**: `new ProgressState(1, 0)` für jeden Charakter ohne gespeicherte Zeile (FR-058).

### `ProgressView`

Was B13 und B14 lesen dürfen, ohne rechnen zu müssen (FR-028).

| Feld | Typ | Bedeutung |
|---|---|---|
| `level` | `int` | aktuelles Level |
| `xpInLevel` | `long` | XP innerhalb dieses Levels |
| `xpForNextLevel` | `long` | Schwelle des nächsten Levels; **0** auf Maximallevel |
| `atMaxLevel` | `boolean` | true auf Maximallevel — FR-051 verlangt „vollständig", nicht „0 %" |

`atMaxLevel` ist ein eigenes Feld und nicht aus `xpForNextLevel == 0` abgeleitet, damit ein
Empfänger die Unterscheidung nicht selbst erfinden muss.

---

## 2. Kurve und Wachstum

### `XpCurve`

Unveränderlich, beim Start aus der Konfiguration gebaut, danach nur gelesen.

| Feld | Typ | Regeln |
|---|---|---|
| `thresholds` | `long[]` | Index 0 = Schwelle für Level 2. Länge = Maximallevel − 1. |

**Validierung beim Bauen** (FR-002, FR-003) — bricht beim **ersten** Verstoss ab und nennt das Level:

1. Jedes Level von 2 bis zum höchsten Schlüssel ist vorhanden. Fehlt eines → Abbruch mit
   *„progression.xp-curve: level 37 is missing"*.
2. Jeder Wert ist ≥ 1. Verstoss → *„progression.xp-curve: level 12 must be positive, but was 0"*.
3. Die Folge ist streng monoton steigend. Verstoss → *„progression.xp-curve: level 20 must be
   greater than level 19 (450), but was 400"*.

**Warum ein Array und keine `Map<Integer, Long>`**: die Kurve wird bei jedem Aufstieg gelesen. Ein
Array ist ein Indexzugriff ohne Boxing; eine Karte wäre ein `Integer`-Objekt je Abfrage im
Kampfpfad. Die Karte existiert nur während der Validierung.

**`maxLevel()`** = `thresholds.length + 1`. Nicht als Konstante im Code (FR-004).

### `LevelGrowth`

Der Zuwachs je Level über alle acht Attribute (FR-022a).

| Feld | Typ | Regeln |
|---|---|---|
| `perLevel` | `double[]` | Länge = `Attribute.count()` = 8. Jeder Wert ≥ 0, **Null erlaubt**. |

Nicht endliche und negative Werte werden beim Laden abgelehnt. Die mitgelieferte Vorgabe setzt
`ATTACK_SPEED`, `MOVEMENT_SPEED` und `ABILITY_COOLDOWN` auf 0 (FR-022b).

**Caps**: Der Beitrag wird nicht selbst begrenzt — `StatCalculator.compute` klammert das Ergebnis
ohnehin gegen `definition.min()` und `definition.max()`. Ein Zuwachs läuft also gegen den Cap aus
B04 statt ihn zu überschreiten (FR-022c). Eine eigene Prüfung in B06 wäre eine zweite Wahrheit über
denselben Grenzwert.

**Der Beitrag an B04**: `LevelStatContributor` implementiert `BaseStatContributor` und trägt für
einen Charakter auf Level *L* je Attribut `perLevel[i] * (L - 1)` bei. Level 1 trägt nichts bei —
der Level-1-Wert **ist** `definition.base()` aus B04. Der Beitrag ist eine Multiplikation, keine
Summe über Level: eine Schleife über 59 Level je Neuberechnung wäre Rechenzeit ohne Ergebnisgewinn.

**Verhalten ohne Fortschrittsstand**: trägt nichts bei und wirft nicht. Ein `BaseStatContributor`,
der wirft, wird von B04 bereits abgefangen (`BaseStatContributorFaultTest`) — aber sich darauf zu
verlassen wäre schlechter Stil.

---

## 3. Vergabe und Aufstieg

### `XpSource`

| Wert | Geteilt? | Darf senken? |
|---|---|---|
| `MOB_KILL` | ja, über die Party | nein |
| `ZONE_OBJECTIVE` | ja | nein |
| `ADMIN` | nein | **ja** — die einzige Quelle (FR-024a) |

### Kein Vergabe-Objekt

B05 hat für den Schadensvorgang einen wiederverwendeten `DamageContext`. B06 bekommt **kein**
Gegenstück: die Vergabe ist ein Aufruf mit drei Werten (Charakter, Betrag, Quelle), kein mehrstufiger
Vorgang mit Zwischenzustand. Die Signatur nimmt die Werte direkt, damit je Ereignis überhaupt kein
Objekt entsteht — auch kein wiederverwendetes, das zurückgesetzt werden müsste (FR-062).

### `LevelUp`

Ergebnis eines XP-Ereignisses, das mindestens eine Schwelle überschreitet.

| Feld | Typ | Bedeutung |
|---|---|---|
| `previousLevel` | `int` | Level vor dem Ereignis |
| `newLevel` | `int` | Level danach; kann mehrere Stufen höher sein (FR-018) |
| `xpInLevel` | `long` | Überschuss im neuen Level (FR-019) |
| `discarded` | `long` | auf Maximallevel verfallene XP (FR-049); sonst 0 |

**Ablauf eines Aufstiegs** (Reihenfolge ist verbindlich, siehe research.md Entscheidung 8):

```
1. xpInLevel += betrag
2. solange xpInLevel >= schwelle(level+1) und level < maxLevel:
       xpInLevel -= schwelle(level+1); level++
3. falls level == maxLevel: discarded = xpInLevel; xpInLevel = 0
4. Charakter als änderungsbedürftig markieren            (FR-054)
5. falls level gestiegen:
       a) StatEngine.recalculateNow(holder)               — liest das neue Level
       b) StatEngine.restoreResources(holder, ResourcePool.full(maxHp, maxMana))   (FR-021a/b)
       c) offenes Fortschrittsbündel ausliefern           (FR-023c)
       d) LevelUpEvent veröffentlichen                    (FR-023)
   sonst:
       ProgressAggregator.record(...) — Bündel ggf. schliessen (FR-023a)
```

Schritt 5a und 5b laufen **genau einmal**, auch wenn Schritt 2 mehrere Level durchlaufen hat
(FR-021, SC-019).

---

## 4. Verteilung

### `WorldPoint`

Ein Ort ohne Bukkit-Abhängigkeit. Nötig, weil `rpg-core` keinen Ortstyp hat und `CombatDeathEvent`
aus B05 **keinen Ort trägt**.

| Feld | Typ | Regeln |
|---|---|---|
| `worldId` | `UUID` | nicht null; zwei Punkte in verschiedenen Welten sind nie in Reichweite |
| `x`, `y`, `z` | `double` | endlich |

**Warum nicht einfach die Id des gestorbenen Wesens durchgeben und den Ort in der Plattformschicht
nachschlagen**: Das Wesen ist tot. Ein Nachschlagen über `Bukkit.getEntity(id)` gelingt nur, solange
B05s `onEntityDeath` noch läuft und das Wesen nicht entfernt ist — eine Zeitbedingung, die an einem
öffentlichen Erweiterungspunkt niemand sieht und die beim ersten asynchronen Aufruf bricht. Der
Listener liest den Ort **im** Todesereignis, wo er sicher gültig ist, und gibt einen Wert weiter.

`distanceSquaredTo(WorldPoint other)` vergleicht ohne Wurzel; verschiedene Welten liefern
`Double.POSITIVE_INFINITY` statt einer Ausnahme, damit der Vergleich gegen die Reichweite die
Weltprüfung gleich mit erledigt (FR-045).

### `XpDistributor`

Setzt FR-039 in fünf Schritten um. Eingabe: `CombatDeathEvent` aus B05 plus der Ort des Wesens als
`WorldPoint`, den der Listener im Todesereignis gelesen hat.

```
1. betrag   = mobXpProvider.xpFor(mobTypeKey)            — Standardbetrag als Rückfall (FR-060)
2. anteile  = event.shares()                             — von B05, nie neu berechnet (FR-011)
3. gruppen  = anteile nach Party zusammenfassen           (FR-040)
4. je Gruppe:
       ohne Party  -> der Beitragende erhält round(betrag * anteil)          (FR-046)
       mit Party   -> inReichweite = proximityCheck.inRange(mobOrt, mitglieder)   (FR-041a)
                      partyAnteil  = round(betrag * summeAnteile)
                      mitBonus     = round(partyAnteil * (1 + bonus))        (FR-043)
                      je Mitglied in Reichweite: mitBonus / anzahl           (FR-041)
5. je Empfänger: progression.grant(...) — Sitzung bereit? sonst still verfallen (FR-014)
```

**Bonusberechnung**: `bonus = min(bonusPerMember * (inRangeCount - 1), bonusCap)`. Bei einem
Mitglied in Reichweite ist der Bonus 0 — eine Einzelparty verhält sich genau wie keine Party
(FR-035).

**Rundung** (FR-047): Beträge werden **abgerundet** (`Math.floorDiv`), und der Rest bleibt liegen.
Damit kann die Summe der vergebenen XP den Mobbetrag zuzüglich Bonus nie übersteigen (SC-013).
Aufrunden hätte bei einer Party aus fünf Mitgliedern bis zu vier XP je Kill erzeugt — bei 800 Mobs
eine sichtbare Inflation aus dem Nichts.

**Leere Aufteilung**: `shares.isEmpty()` → sofortige Rückkehr, kein Empfänger, kein Fehler (FR-012).

**Mitglieder ausserhalb der Reichweite** erhalten nichts — sie stehen nicht in `out` und tauchen in
Schritt 4 gar nicht auf (FR-042). Ihr Anteil ist bereits im Party-Anteil enthalten und wird auf die
Anwesenden verteilt.

**Spielertod**: `event.playerVictim()` → sofortige Rückkehr. Für den Tod eines Spielers gibt es keine
XP (FR-013); PvP ist ohnehin aus (B05).

**Empfänger auf Maximallevel**: Der Anteil wird zugeschrieben und dort still verworfen — er wird
**nicht** auf die übrigen Mitglieder umverteilt (FR-052). Eine Umverteilung hätte bedeutet, die
Verteilung nach der Vergabe noch einmal zu rechnen, und hätte eine Party mit einem Maximallevel-
Mitglied stärker gemacht als eine ohne.

**Zielcharakter**: Zugeschrieben wird immer dem im Moment des Ereignisses **aktiven** Charakter des
Spielers, nie dem Konto und nie einem inaktiven Charakter (FR-016). Die Zuordnung liefert
`StatEngine.characterIdOf` — dieselbe Quelle, die B05 für die Attribution benutzt.

---

## 5. Bündelung

### `ProgressAggregator`

Ein Eimer je Charakter, nach dem Muster von `DamageAggregator` (research.md Entscheidung 3).

| Feld je Eimer | Typ | Bedeutung |
|---|---|---|
| `openedAt` | `long` | Millisekunden aus der `Clock` |
| `sum` | `long` | aufsummierter Zuwachs im Fenster |

**Schliessende Ereignisse**: das nächste XP-Ereignis nach Ablauf der Fensterdauer, ein
Levelaufstieg, das Sitzungsende. Nie eine Aufgabe (FR-061).

**Beim Sitzungsende** wird das offene Bündel **verworfen**, nicht ausgeliefert: es ist reine Anzeige,
und der Empfänger ist bereits weg. Die XP selbst ist längst angerechnet und wird geschrieben.

**Speicher**: 24 Byte je offener Eimer. Bei 200 Spielern ~5 KB, und leere Eimer werden beim
Sitzungsende entfernt.

---

## 6. Party

### `Party`

Reiner Laufzeitzustand (FR-029). Höchstgrösse aus der Konfiguration.

| Feld | Typ | Regeln |
|---|---|---|
| `partyId` | `UUID` | nur zur Kennzeichnung in Ereignissen; nie persistiert |
| `leader` | `UUID` | genau einer, nie leer (FR-029a, FR-029c) |
| `members` | `UUID[]` | Länge ≤ `maxSize`; enthält den Anführer |
| `joinedAt` | `long[]` | Beitrittszeitpunkt je Mitglied, parallel zu `members` |

**`joinedAt` stand nicht in der Spezifikation** und wurde beim Entwurf nachgetragen: ohne ihn ist
„dienstältestes verbleibendes Mitglied" aus FR-029c nicht entscheidbar. Bei gleichem Zeitstempel
gewinnt der niedrigere Index — die Reihenfolge des Beitritts.

**Warum Arrays und keine `List`/`Map`**: die Mitgliederliste wird bei jedem Kill einer Party gelesen.
Feste Arrays in Höchstgrösse vermeiden Iteratoren und Boxing im Kampfpfad.

### Zustandsübergänge

| Von | Ereignis | Nach | Regel |
|---|---|---|---|
| keine Party | Gründung | Party mit 1 Mitglied | Gründer ist Anführer (FR-029a) |
| Party | Anführer lädt ein | Einladung offen | nur der Anführer (FR-029b) |
| Einladung offen | Annahme | Mitglied aufgenommen | nur wenn nicht in Party (FR-032) und nicht voll (FR-033) |
| Einladung offen | Ablehnung, Frist, Abmeldung | Einladung ungültig | lazy geprüft (FR-031) |
| Party | Mitglied verlässt | Party ohne dieses Mitglied | jedes Mitglied darf selbst (FR-029b) |
| Party | Anführer scheidet aus | Rolle an dienstältestes Mitglied | Party nie ohne Anführer (FR-029c) |
| Party mit 1 Mitglied | dieses verlässt | Party existiert nicht mehr | kein Restzustand (FR-035) |
| Party | Sitzung eines Mitglieds endet | wie „Mitglied verlässt" | (FR-034) |

Eine Party mit **einem** Mitglied ist zulässig und verhält sich in der Verteilung wie keine Party
(FR-035).

### `PartyRejection`

Warum ein Beitritt oder eine Aktion abgelehnt wurde — als Aufzählung statt als Text, damit B14 sie
auf Message-Schlüssel abbilden kann (FR-038).

`ALREADY_IN_PARTY`, `PARTY_FULL`, `NOT_LEADER`, `INVITE_EXPIRED`, `INVITE_UNKNOWN`,
`TARGET_NOT_READY`, `SELF_INVITE`, `NOT_A_MEMBER`.

---

## 7. Persistenz

### Tabelle `rpg.character_progress` (Migration `V6_1`)

| Spalte | Typ | Bedeutung |
|---|---|---|
| `character_id` | `UUID PRIMARY KEY` | Fremdschlüssel auf `rpg.character`, `ON DELETE CASCADE` |
| `level` | `INTEGER NOT NULL DEFAULT 1` | `CHECK (level >= 1)` |
| `xp_in_level` | `BIGINT NOT NULL DEFAULT 0` | `CHECK (xp_in_level >= 0)` |
| `data_version` | `INTEGER NOT NULL DEFAULT 1` | Format des Datensatzes, für Migration beim Laden |
| `revision` | `BIGINT NOT NULL DEFAULT 0` | bei jedem Schreiben erhöht, wie in den anderen Tabellen |
| `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | |

**`data_version`** trägt die von FR-057 verlangte Versionierung samt Migrationspfad. Ein Stand aus
einer künftigen Version wird abgelehnt statt falsch gedeutet — dasselbe Verhalten wie
`PlayerCharacter.isFromFutureVersion` in B03.

**Kein Maximallevel-Check in der Datenbank**: das Maximallevel folgt aus der Konfiguration und darf
sich ändern. Ein `CHECK (level <= 60)` würde eine Balancing-Entscheidung im Schema einfrieren und
wäre bei einer Erhöhung eine Migration.

**Die beiden Checks, die es gibt**, sind aus demselben Grund da wie in `character_stats`: ein
negatives Level ist keine Balancing-Entscheidung, sondern ein Fehler, und es soll unmöglich sein, ihn
zu speichern.

### `AggregateType`

B02s Aufzählung braucht einen neuen Wert `CHARACTER_PROGRESS`, damit `DirtyMark` und der
Flush-Zyklus den Fortschritt adressieren können. Additive Ergänzung, bricht keinen bestehenden
Vertrag — dieselbe Art Erweiterung wie `CHARACTER` für B03.

### Schreibweg

XP-Zuwachs → `WriteBehindBuffer.mark(CHARACTER_PROGRESS, characterId)` → Autosave oder
Sitzungsende-Flush. **Kein** Datenbankzugriff im Vergabepfad (FR-054). Beim Sitzungsende schreibt
`SessionHandover` aus B02, bevor die Sitzung als beendet gilt (FR-056).

---

## 8. Konfigurationstyp

### `ProgressionConfig`

| Feld | Typ | Aus |
|---|---|---|
| `curve` | `XpCurve` | `xp-curve` |
| `growth` | `LevelGrowth` | `level-growth.*` |
| `mobXpDefault` | `long` | `mob-xp.default` |
| `mobXpByType` | `Map<String, Long>` | `mob-xp.by-type` |
| `partyMaxSize` | `int` | `party.max-size`, ≥ 1 |
| `partyRange` | `double` | `party.range-blocks`, > 0 |
| `partyBonusPerMember` | `double` | `party.bonus-per-member`, ≥ 0 |
| `partyBonusCap` | `double` | `party.bonus-cap`, ≥ 0 |
| `inviteTimeout` | `Duration` | `party.invite-timeout-seconds`, > 0 |
| `progressWindow` | `Duration` | `progress-event.window-millis`, > 0 |

Aufbau und Schema im Detail: [contracts/progression-config.md](./contracts/progression-config.md).

---

## Speicherabschätzung

| Was | Je Einheit | Bei Volllast |
|---|---|---|
| `ProgressState` je geladener Charakter | 16 B | 200 Spieler → 3,2 KB |
| Offener Bündel-Eimer | 24 B | 200 → 4,8 KB |
| Party (5 Mitglieder) | ~150 B | 40 Partys → 6 KB |
| `XpCurve` | 8 B × 59 | 472 B, einmal |
| `LevelGrowth` | 8 B × 8 | 64 B, einmal |

Zusammen unter 15 KB. B06 ist kein Speicherproblem — die Zusagen, auf die es ankommt, sind die
Nullallokation im Vergabepfad und der ausbleibende Datenbankzugriff.
