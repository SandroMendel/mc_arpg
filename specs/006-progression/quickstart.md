# Validierungsleitfaden: B06 · Progression

**Datum**: 2026-08-20 | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

Elf Abschnitte. Jeder prüft eine Zusage aus der Spezifikation und nennt das Erfolgskriterium, das er
belegt. Abschnitte 1 bis 8 laufen ohne Server, 9 braucht Docker, 10 und 11 einen echten
Paper-Server.

**Voraussetzungen**: Java 25, Docker für Abschnitt 9, ein Paper-26.2-Server für 10 und 11.

---

## 1. Kurve und Aufstieg

```bash
./gradlew :rpg-core:test --tests "rpg.core.progression.XpCurveTest" \
                         --tests "rpg.core.progression.LevelUpTest"
```

**Erwartet**:

| Eingabe | Ergebnis |
|---|---|
| Level 1, 0 XP, Schwelle 100 → +100 XP | Level 2, 0 XP Überschuss |
| Level 1, 0 XP, Schwellen 100/120 → +250 XP | Level 3, 30 XP Überschuss |
| Aufstieg 1 → 3 | **eine** Neuberechnung, **ein** `LevelUpEvent(1, 3)` |
| Tod eines Charakters | Level und XP unverändert |

Belegt SC-001, SC-009. Die Uhr ist gesteuert — kein `Thread.sleep` in diesen Tests.

---

## 2. Kurvenvalidierung bricht den Start ab

```bash
./gradlew :rpg-core:test --tests "rpg.core.progression.ProgressionConfigSchemaTest"
```

**Erwartet** — vier Fehlerfälle, vier benennende Meldungen, jeweils Startabbruch:

| Datei | Meldung nennt |
|---|---|
| Level 37 fehlt | `level 37 is missing` |
| Level 12 auf 0 | `level 12 must be positive` |
| Level 20 kleiner als 19 | `level 20 must be greater than level 19` |
| Kein Level 2 | `must define at least level 2` |

Zusätzlich: `bonus-cap` unter `bonus-per-member` → Abbruch. Ein fehlendes Wachstumsfeld → Abbruch,
**nicht** stillschweigend Null.

Belegt SC-003.

---

## 3. Keine XP-Skalierung nach Levelabstand

```bash
./gradlew :rpg-core:test --tests "rpg.core.progression.XpAmountTest"
```

**Erwartet**: Ein Mob mit 40 konfigurierten XP gibt einem Charakter auf Level 1 und einem auf Level
59 genau 40 — zwei Durchläufe, identisches Ergebnis. Ein Mob ohne eigenen Eintrag gibt den
Standardbetrag und erzeugt **eine** Warnung je Art, nicht je Kill.

Belegt SC-002.

---

## 4. Verteilung mit und ohne Party

```bash
./gradlew :rpg-core:test --tests "rpg.core.progression.XpDistributorTest"
```

**Erwartet** — Mob mit 100 XP, Nähe-Bonus 10 % je zusätzliches Mitglied:

| Aufstellung | Ergebnis |
|---|---|
| A allein, 100 % Anteil | A: 100 |
| A 60 %, B 40 %, keine Party | A: 60, B: 40 |
| A+B in Party (zusammen 60 %, beide in Reichweite), C allein 40 % | A: 33, B: 33, C: 40 |
| dieselbe Party, B ausserhalb der Reichweite | A: 60, B: 0, kein Bonus |
| Party-Mitglied ohne Schadensanteil, in Reichweite | erhält seinen Anteil |
| Party-Mitglied in anderer Welt | 0, nie in Reichweite |
| Party-Mitglied auf Maximallevel | Anteil verfällt still, **keine** Umverteilung |
| Umgebungstod, leere Aufteilung | niemand erhält XP, kein Fehler |
| Party jeder Grösse von 1 bis Obergrenze | Summe ≤ Mobbetrag + Bonus, nie mehr |

Belegt SC-006, SC-007, SC-013.

---

## 5. Party-Zustandsübergänge

```bash
./gradlew :rpg-core:test --tests "rpg.core.progression.PartyRegistryTest"
```

**Erwartet**:

| Handlung | Ergebnis |
|---|---|
| Einladung + Annahme | beide in derselben Party |
| Annahme nach Ablauf der Frist | `INVITE_EXPIRED`, **keine** laufende Aufgabe beteiligt |
| Annahme bei bestehender Mitgliedschaft | `ALREADY_IN_PARTY` |
| Beitritt in volle Party | `PARTY_FULL` |
| Einladen ohne Anführerrolle | `NOT_LEADER` |
| Anführer verliert Verbindung (3 Mitglieder) | dienstältestes Mitglied ist Anführer, nie führungslos |
| Letztes Mitglied verlässt | `partyCount() == 0`, kein Restzustand |
| Rollenübergabe | **zwei** Ereignisse: `LEFT`, dann `LEADER_CHANGED` |

---

## 6. Bündelung und Reihenfolge

```bash
./gradlew :rpg-core:test --tests "rpg.core.progression.ProgressAggregatorTest"
```

**Erwartet**:

- 100 Gewinne innerhalb eines Fensters → **ein** `ProgressChangedEvent`, `gained` = Summe der 100.
- Kreuzt ein Gewinn bei offenem Bündel eine Schwelle → erst `ProgressChangedEvent` mit dem **alten**
  Level, dann `LevelUpEvent`. Kein Ereignis mit dem alten Level erreicht einen Empfänger danach.
- Sitzungsende bei offenem Bündel → Bündel verworfen, kein Ereignis, XP dennoch angerechnet.
- Nach `release` ist die Zahl offener Eimer 0 — kein Leck.

Belegt SC-018, SC-020.

---

## 7. Maximallevel

```bash
./gradlew :rpg-core:test --tests "rpg.core.progression.MaxLevelTest"
```

**Erwartet**: Ein Charakter auf Maximallevel bleibt nach 10 000 XP-Ereignissen unverändert — kein
`LevelUpEvent`, kein `ProgressChangedEvent`, keine Protokollzeile je Ereignis. `progressOf` meldet
`atMaxLevel = true` und `xpForNextLevel = 0`, nicht „0 % zum nächsten Level".

Belegt SC-008.

---

## 8. Ressourcen und Verwaltungseingriff

```bash
./gradlew :rpg-core:test --tests "rpg.core.progression.LevelUpResourcesTest" \
                         --tests "rpg.core.progression.AdminProgressTest"
```

**Erwartet**:

| Fall | Ergebnis |
|---|---|
| 12 von 100 Leben, Aufstieg hebt Maximum auf 110 | 110 von 110 |
| Aufstieg über drei Level | **einmal** aufgefüllt, nicht dreimal |
| Auffüllen vor Neuberechnung | darf nicht vorkommen — Test prüft die Reihenfolge |
| Verwaltungseingriff senkt Level | Audit-Eintrag mit altem und neuem Stand, **kein** Auffüllen |
| gesenktes Maximum unter aktuellem Wert | Wert wird auf das neue Maximum begrenzt |
| `level` ausserhalb 1..maxLevel | `INVALID_AMOUNT`, kein Eingriff |

Belegt SC-019, SC-021.

---

## 9. Persistenz gegen echtes PostgreSQL

```bash
./gradlew :rpg-persistence:test --tests "rpg.persistence.progression.*"
```

Braucht Docker — Prinzip VII verbietet Mocks gegen die Datenbank.

**Erwartet**:

- 1000 XP-Ereignisse in einer Sekunde → Zugriffszähler des Repositories bleibt bei **0**, Charakter
  ist als änderungsbedürftig markiert. *(SC-004)*
- Sitzungsende → Stand geschrieben, **bevor** die Sitzung als beendet gilt.
- Charakter ohne Zeile → Level 1, 0 XP. *(FR-058)*
- Stand einer älteren `data_version` → migriert ohne Verlust; Stand aus einer künftigen Version →
  abgelehnt, nicht falsch gedeutet. *(SC-016)*
- Gelöschter Charakter → Fortschrittszeile verschwindet mit (`ON DELETE CASCADE`).
- Nachträglich verdoppelte Kurve → **kein** bestehender Charakter sinkt im Level. *(SC-017)*
- Nachträglich gesenkte Kurve, `xp_in_level` über der neuen Schwelle → wird beim Laden regulär in
  Aufstiege umgesetzt, nicht als Fehler behandelt.

---

## 10. Nullallokation und keine Aufgaben

```bash
./gradlew :rpg-core:test --tests "rpg.core.progression.NoAllocationTest" \
                         --tests "rpg.core.progression.NoScheduledTaskTest"
```

**Erwartet**:

- 10 000 aufeinanderfolgende XP-Ereignisse erzeugen kein vermeidbares Objekt je Ereignis. *(SC-005)*
- Die Aufgabenanzahl des Schedulers bleibt konstant, unabhängig von der Spielerzahl — 1, 50 und 200
  Spieler ergeben dieselbe Zahl. *(SC-012)*
- Eine Levelanforderungsabfrage erzeugt keinen Datenbankzugriff. *(SC-011)*

---

## 11. Verdrahtung und echter Server

```bash
./gradlew :rpg-plugin:test --tests "rpg.plugin.FullBootstrapTest"
./gradlew build
```

**Erwartet**: `ProgressionModule` steht in `RpgPlugin.modules()`, `progression.yml` wird beim Start
geschrieben, `Progression` und `PartyRegistry` sind über `plugin.registry().findService(...)`
auffindbar, und der Bootstrap bleibt im Zeitbudget (ADR-012).

Dann auf einem echten Paper-Server:

| Prüfung | Erwartet |
|---|---|
| Server startet mit gültiger `progression.yml` | Meldung „progression ready", Maximallevel aus der Kurve |
| Zeile 37 aus der Kurve entfernen, Start | Abbruch, Meldung nennt Level 37 |
| Mob erschlagen | XP kommt an, Fortschrittsbalken bewegt sich |
| Vanilla-Erfahrung des Spielers nach 1000 eigenen Ereignissen | unverändert — B06 schreibt nie in die Vanilla-Erfahrungsleiste *(FR-063, SC-015)* |
| Serverneustart | Level und XP erhalten, **keine** Party mehr *(SC-010)* |
| Party bilden, gemeinsam kämpfen | beide erhalten XP, Bonus greift |
| Ein Mitglied entfernt sich weit vom Kampf | erhält nichts |

**B06 ist nicht lasttestpflichtig** — Prinzip VII nennt B05 und B10 beim Namen, nicht B06. Die
Zusagen, auf die es hier ankommt, sind gezählt geprüft (Abschnitt 10) und nicht gemessen.

---

## Abnahme

Der Block gilt als fertig, wenn Abschnitte 1 bis 11 durchlaufen, `./gradlew build` grün ist, die
Zahl **übersprungener** Tests 0 ist (nicht nur die der fehlgeschlagenen) und die Verdrahtung im
`FullBootstrapTest` belegt ist. Abschnitte 1 bis 8 laufen dabei vollständig ohne Server — das ist
SC-014 und Prinzip VII: jede Formel und jede Regel der Domänenschicht ist serverfrei geprüft.

Zur Zahl der übersprungenen Tests: MockBukkit meldet Nichtimplementiertes als „skipped" statt als
Fehler, und Testcontainers ohne Docker ebenso. Ein grüner Lauf mit übersprungenen Tests ist kein
grüner Lauf.
