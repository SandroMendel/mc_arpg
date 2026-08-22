# Quickstart · B08b · Währung & Konto validieren

Wie sich beweisen lässt, dass der Block tut, was die Spec zusagt. Zwei Teile: was ohne Server geht
(das meiste), und was einen laufenden Paper-Server braucht (die Haufen).

---

## Voraussetzungen

- JDK 25 (Toolchain aus B01, `gradlew` zieht sie)
- Docker läuft — Testcontainers startet ein echtes PostgreSQL (Prinzip VII)
- Für Abschnitt 3: ein Paper-Server 26.2 mit dem gebauten Plugin und einer erreichbaren Datenbank

---

## 1 · Der ganze Block, ohne Server

```bash
./gradlew test
```

Erwartung: **grün, 0 Fehler, 0 übersprungen**. Die Zahl der Tests im Projekt liegt danach über den
1416 aus B08.

Auf „übersprungen" ist zu achten, nicht nur auf „fehlgeschlagen": MockBukkit meldet nicht
Implementiertes als *skipped*, nicht als Fehler — ein übersprungener Test ist eine Lücke, kein
Erfolg.

### Die einzelnen Zusagen gezielt

```bash
# Kontoarithmetik, Ablehnungen, Unteilbarkeit (US1)
./gradlew :rpg-core:test --tests '*Currency*' --tests '*Booking*'

# Anteilsrechnung - muss fuer Erfahrung UNVERAENDERT gruen bleiben (R4)
./gradlew :rpg-core:test --tests '*XpDistributor*' --tests '*ShareCalculator*'

# Verlauf, Eingriff, Aufbewahrung (US3) - braucht Docker
./gradlew :rpg-persistence:test --tests '*CoinLedger*' --tests '*CharacterBalance*'

# Kostenauflösung und die beiden nachgezogenen Blöcke (US4, US5)
./gradlew :rpg-core:test --tests '*CostSpec*' --tests '*AbilityRank*' --tests '*EquipmentLadder*'

# Haufen: erzeugen, zusammenlegen, Besitz, aufheben (US2) - MockBukkit
./gradlew :rpg-platform:test --tests '*CoinPile*' --tests '*CoinDrop*' --tests '*CoinPickup*'

# Verdrahtung (ADR-012)
./gradlew :rpg-plugin:test --tests '*FullBootstrapTest*'
```

### Was jeder Lauf beweisen muss

| Zusage | Wo sie fällt, wenn sie bricht |
|---|---|
| SC-001 · kein negativer Stand unter Nebenläufigkeit | 1000 gleichzeitige `debit` auf ein Konto; die Summe der `OK` übersteigt den Ausgangsstand nicht |
| SC-003 · Charaktere sind getrennt | Buchung auf Charakter A lässt B unverändert |
| SC-004 · keine Buchung ohne Grund | Es gibt keine Signatur ohne `BookingReason`; der Quelltest prüft es zusätzlich |
| SC-005 · kein Datenbankzugriff je Spielereignis | `NoDatabaseAccessPerGameEventTest`, erweitert um beide neuen Aggregate |
| FR-015 · drei Eintragungen | `FullBootstrapTest` prüft Enum-Wert, `WRITE_ORDER` und Repository für **beide** Typen |
| FR-052 · Höchstrang kostet nichts | Rangaufstieg am Maximum lässt den Stand unverändert |

---

## 2 · Persistenz gegen echtes PostgreSQL

```bash
./gradlew :rpg-persistence:test
```

Beweist:

- **Beide Migrationen laufen** — `V8_2` und `V8_3` — und laufen auf eine bestehende Datenbank aus
  B08 auf, nicht nur auf eine leere.
- **`CHECK (balance >= 0)` greift**: ein direkter negativer `UPDATE` wird von der Datenbank abgelehnt,
  nicht nur vom Code (data-model.md §1).
- **Der Verlauf ist nur anfügend** und überlebt einen Neustart des Containers (SC-002).
- **Die Aufbewahrung räumt auf** — und lässt Einträge mit gesetztem `actor` stehen (FR-038).
- **Die Kaskade räumt mit**: nach dem Löschen eines Charakters ist keine Zeile in beiden Tabellen
  übrig (ADR-011s Nebenwirkung auf die Anonymisierung).

---

## 3 · Auf einem laufenden Paper-Server

Das Einzige, was serverfrei nicht zu beweisen ist. Der Klassenlader-Blindfleck gilt auch hier: grüne
Tests sagen nichts über Papers `libraries:`-Lader — nur der echte Start tut das.

```bash
./gradlew :rpg-plugin:shadowJar   # oder der im Projekt übliche Build-Task
# jar nach plugins/ kopieren, Server starten
```

### 3.1 Der Haufen

1. Eine konfigurierte Kreatur allein töten.
   **Erwartung:** ein Haufen liegt am Ort des Todes. Der Kontostand ist **unverändert** — der Kill
   schreibt nicht gut (FR-019).
2. Darüberlaufen.
   **Erwartung:** Betrag gutgeschrieben, Haufen weg, Meldung erscheint, **nichts im Inventar**
   (FR-033).
3. Einen zweiten Spieler in die Nähe eines fremden Haufens bringen.
   **Erwartung:** er **sieht ihn gar nicht** und hebt nichts auf; der Haufen bleibt liegen
   (FR-027, FR-027a).
3a. Einen Mob gemeinsam töten, sodass beide Anspruch haben.
   **Erwartung:** jeder sieht **nur seinen eigenen** Haufen, nicht den des anderen.
4. **Der Charakterwechsel** — die Prüfung, die Vanilla allein nicht leistet (R3): einen Haufen
   verdienen, ohne aufzuheben den Charakter wechseln, darüberlaufen.
   **Erwartung:** wird **nicht** aufgehoben. Zurückwechseln, erneut darüberlaufen: wird aufgehoben.
5. Zwei Kreaturen dicht nebeneinander töten.
   **Erwartung:** **ein** Haufen mit der Summe, nicht zwei (FR-028).
6. Einen Haufen liegen lassen, bis die Frist abläuft.
   **Erwartung:** er verschwindet, und **niemand** bekommt etwas (FR-029).

### 3.2 Die beiden nachgezogenen Blöcke

7. Mit zu wenig Coins eine Ausrüstungsstufe kaufen.
   **Erwartung:** Ablehnung mit Begründung, Stufe unverändert, Stand unverändert.
8. Genug Coins besorgen, erneut kaufen.
   **Erwartung:** Aufstieg gelingt, Betrag abgebucht.
9. Dasselbe für einen Fähigkeitsrang, und einmal am Höchstrang.
   **Erwartung:** am Höchstrang scheitert es am **Rang**, nicht am Geld, und es wird nichts abgebucht
   (FR-052).

### 3.3 Der Eingriff

10. `/coins add <spieler> <charakter> 1000`
    **Erwartung:** Stand steigt, Verlauf nennt den Eingriff **mit Verursacher**, Audit-Log ebenfalls.
11. `/coins remove` mit mehr, als vorhanden ist.
    **Erwartung:** Ablehnung. **Auch der Betreiber erzeugt keinen negativen Stand** (FR-003).
12. Denselben Eingriff auf einen **offline** Charakter, danach einloggen.
    **Erwartung:** der geänderte Stand steht da (FR-042).
13. Als Spieler ohne `rpg.currency.admin`.
    **Erwartung:** abgewiesen, nichts geändert (FR-045).
14. Ein Eingriff auf einen **online** Charakter, danach Abmelden und Wiederanmelden.
    **Erwartung:** der Eingriff hat überlebt — er wirkte im Cache, nicht nur in der Datenbank, und
    wurde nicht vom nächsten Flush überschrieben.

### 3.4 Das Fenster (ADR-028)

15. `/coins` als gewöhnlicher Spieler mit drei Charakteren.
    **Erwartung:** die Auswahl zeigt drei Charaktere mit **je eigenem Stand** — keine Summe. Kein
    Sonderrecht nötig.
16. Einen Charakter anklicken.
    **Erwartung:** sein Verlauf erscheint, neueste Buchung zuerst.
17. Bei mehr Buchungen als einer Seite blättern.
    **Erwartung:** Vor und Zurück funktionieren, an beiden Enden führt kein Knopf darüber hinaus, und
    **keine Buchung erscheint auf zwei Seiten**.
18. Versuchen, einen Eintrag aus dem Fenster zu nehmen.
    **Erwartung:** geht nicht. Die Einträge sind Anzeige, keine Gegenstände.
19. `/coins <fremder spieler>` ohne `rpg.currency.admin`.
    **Erwartung:** abgewiesen.
20. Einen Charakter ohne jede Buchung öffnen.
    **Erwartung:** eine Meldung „noch keine Buchung", kein leeres Fenster ohne Erklärung.

### 3.5 Neustart und Absturz

21. Coins verdienen, Server sauber neu starten.
    **Erwartung:** Stand unverändert, Verlauf vollständig, **liegende Haufen sind weg** — beabsichtigt
    (data-model.md §4).
22. Coins verdienen, Server **hart** beenden.
    **Erwartung:** Verlust höchstens im Umfang eines Autosave-Intervalls (SC-002).

---

## 4 · Last — noch nicht entschieden, ob Pflicht

Siehe [research.md](./research.md) R8: Prinzip VII nennt B05 und B10, nicht diesen Block. Mit einem
Entity je Kill gehört er der Grössenordnung nach dazu.

**Zu messen, sobald B10 Horden liefern kann:**

- 150 Spieler, 800 aktive Mobs, Kampflast.
- **p95 MSPT < 40 ms** — das Kriterium des Projekts.
- Anteil dieses Blocks am Tick-Budget **≤ 5 ms** (SC-006).
- Gleichzeitig liegende Haufen **unter `drops.max-piles`** — und wenn die Deckelung greift, muss das
  im Log stehen, nicht stillschweigend geschehen.

Bis dahin ist der Nachweis für SC-006 offen. Das ist der einzige Punkt dieses Quickstarts, der ohne
einen anderen Block nicht abzuhaken ist, und er gehört so in den Abschlussbericht.
