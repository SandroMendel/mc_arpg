# Implementation Plan: B08b · Währung & Konto

**Branch**: `008b-currency-account` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008b-currency-account/spec.md`

## Summary

Der Block hat **zwei sehr ungleiche Hälften**. Die eine ist eine Addition mit einer Sperre davor —
sie ist in einem Tag geschrieben und in einem halben getestet. Die andere setzt zum ersten Mal in
diesem Projekt **Objekte in die Welt** und hängt damit unmittelbar am Tick-Budget. Neun
Entscheidungen prägen die Umsetzung.

1. **Der Coin-Haufen wird nicht erfunden, sondern geerbt.** Er ist ein gewöhnliches
   Vanilla-`Item`-Entity. Das ist keine Bequemlichkeit, sondern die einzige Bauform, in der FR-027,
   FR-029 und FR-030 **ohne eine Zeile eigenen Laufzeitcode** erfüllt sind: `Item.setOwner` beschränkt
   das Aufheben auf genau einen Spieler, der Vanilla-Verfall räumt liegengebliebene Haufen ab, und die
   Darstellung samt Physik gibt es dazu. Eine eigene Haufen-Verwaltung hätte einen Sweep gebraucht —
   und der einzige vorhandene läuft **asynchron** (`startAbilitySweep`, alle 500 ms) und darf die
   Bukkit-API nicht anfassen. Siehe [research.md](./research.md) R1.

2. **Das Zusammenlegen ist umgekehrt gefährlich, nicht schwierig.** Vanilla legt ähnliche Stapel
   zusammen, indem es **Stückzahlen addiert** — bei einem Betrag im Datencontainer wäre das ein
   stiller Wertverlust: zwei Haufen à 500 würden zu einem Stapel der Grösse 2 mit dem Wert 500. Der
   Betrag steht deshalb im Container **zusammen mit einer eindeutigen Kennung je Haufen**, womit keine
   zwei Haufen `isSimilar` sind und Vanilla sie nie anfasst. FR-028 wird stattdessen **vor** dem
   Erzeugen erfüllt: ein Blick in den kleinen Umkreis, und ein vorhandener Haufen desselben
   Berechtigten wächst, statt dass ein zweiter entsteht. R2.

3. **`Item.setOwner` kennt Spieler, dieser Block kennt Charaktere.** Die Vanilla-Sperre allein
   genügt nicht: ein Spieler kann zwischen seinen Charakteren wechseln, und dann höbe Charakter B
   auf, was Charakter A verdient hat — ein direkter Verstoss gegen ADR-011. Der Datencontainer trägt
   deshalb **beides**, und beim Aufheben wird die Charakterkennung geprüft. Vanilla filtert grob und
   billig, der Block prüft genau. R3.

4. **Die Anspruchsregel wird geteilt, nicht kopiert.** Wer wieviel bekommt, steht heute in
   `XpDistributor` — Schadensanteil, Gruppe als ein Beitragender, Teilung auf die Mitglieder in
   Reichweite, Abrunden mit Rest auf dem Tisch. Eine zweite Umsetzung für Coins wäre zwei Wahrheiten
   über denselben Kill, die genau so lange gleich bleiben, bis jemand eine von beiden anfasst. Der
   Anteilsrechner wird deshalb aus `XpDistributor` **herausgelöst** und von beiden benutzt. Das ist
   ein Eingriff in einen ausgelieferten Block und **ADR-pflichtig**. R4.

5. **Zwei Aggregate, nicht eins — und sie schreiben grundverschieden.** Der Kontostand ist eine Zeile
   je Charakter und verhält sich wie `CHARACTER_PROGRESS`: Dirty-Mark, beim Flush den lebenden Wert
   lesen. Der Verlauf ist nur anfügend und verhält sich wie `AUDIT_LOG`, das genau dafür schon ein
   Muster hat — eine synthetische Warteschlangen-Kennung, die markiert wird, und ein Writer, der die
   Warteschlange leert. Beide brauchen die **drei Eintragungen** aus ADR-015 Punkt 7. R5.

6. **Die Migrationen bleiben im Zahlenraum von B08.** Flyway-Versionen sind numerisch; `V8b_1` wäre
   kein gültiger Name. Also `V8_2__character_balance.sql` und `V8_3__coin_ledger.sql` — der Block
   steht hinter B08 und schreibt in dessen Raum weiter, womit B09 seinen eigenen behält.

7. **Der `cost`-Block wird ausgelesen, ohne B07 anzufassen.** `ClassSourceInvariantsTest` verbietet
   die Wörter `coins` und `price` in B07s Quellen — und dieses Verbot ist keine Hürde, sondern die
   Bauanleitung: die Auflösung geschieht in **diesem** Block, der die undurchsichtige Map liest.
   B07 bleibt Zeile für Zeile unverändert, sein Invariantentest bleibt grün, und FR-050 (unbekannter
   Schlüssel ist ein Startfehler) entsteht als eigene Startprüfung hier. R6.

8. **Bei B08 ist genau ein Test umzudrehen.** `AbilityRankTest` behauptet heute ausdrücklich, dass
   `RankResult` **kein** `NOT_ENOUGH_COINS` enthält. Das war richtig und ist es ab diesem Block nicht
   mehr; die Zusicherung wird umgedreht statt gelöscht, damit die Änderung im Test sichtbar bleibt.
   `AbilitySourceInvariantsTest` verbietet nur Bukkit-Pakete und steht dem nicht im Weg.

9. **Kommando und Fenster sind die einzigen Stellen, an denen der Block seine Schicht verlässt.** Im
   gesamten Projekt existiert bislang **kein einziges** Kommando; Kommandos, Rechtebaum und
   Tab-Completion gehören B14 (Schicht 3). Ein Schicht-1-Block, der ein Kommando registriert, ist
   eine Abweichung von Prinzip III — festgehalten und **angenommen in ADR-028**. Beim Fenster ist die
   Abweichung kleiner, als sie aussieht: **B07 hält seine Klassenauswahl bereits im eigenen Block**,
   und `CurrencyMenu` folgt genau diesem Muster. Neu ist allein die Seitenblätterung, für die es im
   Projekt kein Vorbild gibt — deshalb bekommt `CoinLedger` einen **Versatz** statt nur eines Limits.
   Siehe [Complexity Tracking](#complexity-tracking) und R7.

**Was den Block gefährlich macht, ist nicht das Buchen, sondern das Fallenlassen.** Die Arithmetik ist
trivial und vollständig serverfrei prüfbar. Die Haufen dagegen liegen im selben Budget wie B10 und
sind der Grund, warum dieser Block wahrscheinlich lasttestpflichtig ist, ohne in Prinzip VII genannt
zu sein. Die Reihenfolge in Phase 2 muss das ausnutzen: erst das Konto vollständig und serverfrei
fertigstellen, dann den Haufen als einzelnes, isoliert messbares Stück.

## Technical Context

**Language/Version**: Java 25 (ADR-001), Toolchain aus B01 unverändert.

**Primary Dependencies**: Keine neuen externen. Der Block nutzt den Write-Behind-Puffer,
`AggregateType`, `FlushCycle` und `AuditLogRepository` aus B02, `SessionBundle` und den
Sitzungslebenszyklus aus B03, `CombatDeathEvent` und den Schadensanteil aus B05, `XpDistributor` und
`WorldPoint` aus B06 — **mit einer Herauslösung**, siehe R4 —, `EquipmentTier.cost()` aus B07,
`AbilityRuntime.advanceRank` und `RankResult` aus B08, `MessageKey` und `MessageKeyValidator` aus B01.
`Scheduler` wird **nicht** erweitert.

**Storage**: PostgreSQL, **zwei neue Tabellen**. `rpg.character_balance` (Migration `V8_2`) mit einer
Zeile je Charakter. `rpg.coin_ledger` (Migration `V8_3`), nur anfügend, viele Zeilen je Charakter, mit
Aufbewahrungsdauer nach Buchungsart.

**Testing**: JUnit 5 + AssertJ serverfrei in `rpg-core` für Buchungsarithmetik, Unteilbarkeit,
Ablehnungsgründe, Anteilsberechnung, Kostenauflösung und Konfigurationsbindung. Testcontainers mit
echtem PostgreSQL für beide Repositories, beide Migrationen und die Aufbewahrung (Prinzip VII).
MockBukkit in `rpg-platform` für Erzeugen, Zusammenlegen, Besitzsperre und Aufheben eines Haufens.
`FullBootstrapTest` in `rpg-plugin` für die Verdrahtung (ADR-012), erweitert um beide Aggregattypen,
beide Repositories und das Kommando.

**Target Platform**: Linux-VPS, Paper-Server (Minecraft 26.2 / Java 25), API-Artefakt
`26.2.build.112-stable`.

**Project Type**: Regel-Engine-Block mit Persistenz-, Plattform- und (vorläufig) Kommandoanteil,
innerhalb des Multi-Modul-Gradle-Projekts aus B01.

**Performance Goals**: Tick-Budget ≤ 5 ms (Prinzip II). Bei 150 Spielern und 800 aktiven Mobs bleibt
die Zahl gleichzeitig liegender Haufen unter der konfigurierten Deckelung (SC-006). Eine Buchung ist
eine Addition unter einer Sperre je Konto und allokationsfrei. **Null** geplante Aufgaben: weder
Konto noch Verlauf noch Haufen erzeugen eine — der Verfall gehört Vanilla.

**Constraints**: Kein Datenbankzugriff je Spielereignis; beide Aggregate gehen über den
Write-Behind-Puffer. Der Kontostand ist während der Sitzung im Cache maßgeblich. Der Umkreisblick
beim Zusammenlegen läuft über die Chunk-gebundene Abfrage des Servers, nie über lineare Iteration
aller Entities. `rpg-core` bleibt bukkitfrei.

**Scale/Scope**: 6 Nutzergeschichten, 59 funktionale Anforderungen, 12 Erfolgskriterien, 2 neue
Tabellen, 2 neue Aggregattypen, 1 Herauslösung in B06, 2 nachzuziehende Blöcke.

## Constitution Check

*GATE: Muss vor Phase 0 bestehen. Nach Phase 1 erneut geprüft.*

| Prinzip | Bewertung | Begründung |
|---|---|---|
| **I. Nebenläufigkeit** | ✅ | Kein direkter Bukkit-Scheduler. Der Block plant **gar keine** Aufgabe: der Verfall gehört Vanilla, der Verlauf reitet auf dem vorhandenen Flush-Zyklus. Erzeugen und Aufheben eines Haufens laufen im Tick, der den Ort besitzt. Der Kontostand hängt am Charakter, nicht an einem globalen Feld. |
| **II. Performance** | ⚠️ → ✅ | Keine wiederkehrende Aufgabe je Spieler oder Entity. Kein Datenbankzugriff je Spielereignis. **Der Prüfpunkt ist der Haufen**, nicht die Buchung: ein Entity je Kill ist echte Tick-Last. Beantwortet durch die Deckelung (FR-030), das Zusammenlegen (FR-028) und die Verfallszeit (FR-029) — alle drei als Anforderung, nicht als Optimierung. Der Umkreisblick nutzt die Chunk-Abfrage des Servers, keine lineare Iteration. |
| **III. Architektur** | ❌ → siehe Complexity Tracking | `rpg-core` bleibt bukkitfrei, die Richtung `plugin → platform → core` bleibt gewahrt, und der Block greift auf keine Interna anderer Blöcke zu. **Eine Verletzung bleibt**: das Admin-Kommando gehört B14 (Schicht 3), entsteht aber hier (Schicht 1). Begründet und eingegrenzt unten. |
| **IV. Datenhaltung** | ✅ | Zwei versionierte Migrationen. Der Cache ist während der Sitzung maßgeblich. Nichts Berechnetes wird gespeichert — der Verlauf hält Stand davor und danach als Tatsachen, nicht als Ableitung. Kein Datenverlust über das Autosave-Intervall hinaus. |
| **V. Datengetriebenes Design** | ✅ | Startguthaben, Kreatur-Erträge, Verfallszeit, Deckelung, Aufbewahrungsdauer und alle Preise stehen in Konfiguration und werden beim Start gegen ein Schema geprüft (Fail-Fast). Jeder Spielertext läuft über `MessageKey`; `MessageKeyValidator` beweist beim Start, dass hinter jedem Schlüssel ein Text steht. |
| **VI. Korrektheit & Sicherheit** | ✅ | Der Server ist alleinige Autorität über jeden Betrag; der Client sagt nur, dass er etwas aufheben möchte. Das Kommando ist berechtigungspflichtig und validiert seine Eingaben. Eine fehlgeschlagene Buchung lässt den Stand unverändert — kein inkonsistenter Zwischenzustand. Kein Reflection-, kein NMS-Zugriff. |
| **VII. Tests** | ⚠️ | Jede Regel ist serverfrei prüfbar; die Persistenz läuft gegen echtes PostgreSQL. **Offen**: ob der Block lasttestpflichtig wird. Prinzip VII nennt B05 und B10 namentlich; mit einem Entity je Kill bei 800 Mobs erfüllt dieser Block dasselbe Kriterium, ohne genannt zu sein. Zu entscheiden — siehe R8. |
| **VIII. Sprache** | ✅ | Dokumentation deutsch, Code, Bezeichner, Config-Schlüssel und Spielertexte englisch. |

**Ergebnis**: Ein begründungspflichtiger Verstoss (III), ein offener Punkt (VII). Beide sind unten
beziehungsweise in `research.md` festgehalten; keiner blockiert Phase 0.

### Erneute Prüfung nach Phase 1

Der Entwurf hat drei Dinge geändert, die die Bewertung berühren — zwei zum Besseren, einer ist neu.

- **Prinzip II ist von ⚠️ auf ✅ gewandert, und zwar aus einem konkreten Grund**, nicht durch
  Zusicherung: weil der Haufen ein Vanilla-`Item` ist (R1), plant der Block **null** Aufgaben. Es gibt
  keinen zweiten Sweep, keinen Verfalls-Timer, keine Aufgabe je Haufen. Die drei Stellschrauben
  Deckelung, Zusammenlegen und Frist stehen als Anforderungen im Vertrag und als Prüfungen im Schema.
- **Prinzip IV hat eine Zusage dazubekommen.** `CHECK (balance >= 0)` in der Migration macht FR-003 zu
  einer Eigenschaft der Datenbank statt zu einer Verabredung im Code. Damit gilt sie auch für einen
  späteren Schreibweg, den es heute noch nicht gibt.
- **Neu und beim ersten Durchlauf übersehen: Prinzip VI hat eine zweite Stelle.** Nicht nur das
  Kommando validiert Eingaben — auch das **Aufheben** ist eine Client-Eingabe. Der Spieler sagt „ich
  möchte diesen Gegenstand aufnehmen", und der Betrag steht in einem Datencontainer an einem Entity in
  der Welt. Die Prüfung liegt deshalb vollständig serverseitig: Charakterkennung aus dem Container
  gegen den aktiven Charakter, Betrag aus dem Container, Entity danach entfernt. Kein Wert aus dem
  Ereignis selbst wird übernommen. ✅

Die Verletzung von Prinzip III bleibt unverändert bestehen und ist unten begründet. Der offene Punkt
zu Prinzip VII (R8) ist durch Phase 1 **wahrscheinlicher geworden**, nicht kleiner: der Entwurf
bestätigt, dass ein Entity je Kill entsteht.

**Ergebnis nach Phase 1**: unverändert ein begründungspflichtiger Verstoss, unverändert ein offener
Punkt. Kein neuer.

## Project Structure

### Documentation (this feature)

```text
specs/008b-currency-account/
├── plan.md              # Diese Datei
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── currency-api.md  # Die öffentliche Schnittstelle des Blocks
│   └── currency-config.md # Konfigurationsvertrag
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2, von /speckit-tasks
```

### Source Code (repository root)

```text
rpg-core/src/main/java/rpg/core/currency/
├── package-info.java              # Vertrag und Zusagen des Blocks
├── Currency.java                  # Die öffentliche Schnittstelle (Lesen, Buchen, Kostenprüfung)
├── DefaultCurrency.java           # Kontostände im Speicher, Sperre je Konto
├── Booking.java                   # Richtung, Betrag, Grund
├── BookingReason.java             # Abgeschlossener Vorrat an Gründen
├── BookingResult.java             # Gelungen / zu wenig / ungültig, je mit MessageKey
├── CoinLedger.java                # Der nur anfügende Verlauf
├── LedgerEntry.java               # Ein Verlaufseintrag
├── CurrencyAdmin.java             # setzen / hinzufügen / entfernen, mit Verursacher
├── CostSpec.java                  # Auflösung des undurchsichtigen cost-Blocks aus B07
├── EquipmentPurchase.java         # Kostenprüfung vor dem Stufenaufstieg (B07 bleibt unberührt)
├── MobCoinProvider.java           # Was eine Kreatur fallen lässt (B10 löst ab)
├── ConfigMobCoinProvider.java
├── CoinDropPlan.java              # Wer bekommt welchen Haufen, wo - bukkitfrei
├── CoinDropPlanner.java           # Kill -> Liste von Haufen, nutzt den Anteilsrechner
├── CurrencyConfig.java
├── CurrencyConfigSchema.java
├── CurrencyMessageKeys.java
├── CharacterBalance.java          # Aggregat: eine Zeile je Charakter
└── CharacterBalanceRepository.java

rpg-core/src/main/java/rpg/core/progression/
└── ShareCalculator.java           # HERAUSGELÖST aus XpDistributor (R4, ADR-pflichtig)

rpg-persistence/src/main/java/rpg/persistence/currency/
├── CurrencyModule.java            # Die drei Eintragungen (ADR-015 Punkt 7)
├── JdbcCharacterBalanceRepository.java
├── JdbcCoinLedgerRepository.java
├── CharacterBalanceBatchWriter.java
└── CoinLedgerBatchWriter.java     # Warteschlangen-Muster wie JdbcAuditLogRepository

rpg-persistence/src/main/resources/db/migration/
├── V8_2__character_balance.sql
└── V8_3__coin_ledger.sql

rpg-platform/src/main/java/rpg/platform/currency/
├── CoinPile.java                  # Erzeugen, Zusammenlegen, Datencontainer lesen/schreiben
├── CoinPileTag.java               # Betrag, Charakter, Haufenkennung - Muster von BoundItemTag
├── CoinDropListener.java          # CombatDeathEvent -> Haufen, Ort als Wert (ADR-015 Punkt 6)
├── CoinPickupListener.java        # PlayerAttemptPickupItemEvent -> Buchung, Entity entfernen
├── CurrencyMenu.java              # Charakterauswahl + Verlauf, Muster von ClassSelectionMenu (ADR-028)
└── CurrencyMenuListener.java      # Klicks, Sperre gegen Herausnehmen

rpg-plugin/src/main/java/rpg/plugin/command/
└── CoinsCommand.java              # VORLÄUFIG, B14 übernimmt (R7)

rpg-plugin/src/main/resources/
├── currency.yml                   # Neu
├── plugin.yml                     # Erweitert um den commands-Block
└── messages.yml                   # Erweitert

Tests spiegeln diese Struktur in src/test/java der jeweiligen Module.
```

**Structure Decision**: Die gewachsene Aufteilung des Projekts aus B01 wird fortgesetzt — ein Paket
je Block, in `rpg-core` die bukkitfreie Regel, in `rpg-persistence` die drei Eintragungen und die
Migration, in `rpg-platform` alles, was Paper anfasst. Zwei Abweichungen vom Muster der Vorgänger,
beide bewusst: `ShareCalculator` entsteht in **B06s** Paket, weil die Regel dort ihren Besitzer hat und
nicht umzieht, nur weil ein zweiter Nutzer dazukommt — und `CoinsCommand` liegt in `rpg-plugin`, wo
B14 es finden und ersetzen wird.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| **Ein Kommando und ein Fenster in einem Schicht-1-Block** (Prinzip III: Kommandos gehören B14, Anzeige B13 — beide Schicht 3) | FR-039 bis FR-046b verlangen, dass ein Betreiber Stände setzen, erhöhen und senken **und den Verlauf lesen** kann. Es existiert im gesamten Projekt kein Kommando und kein Rechtebaum; B14 hängt von **allen** Blöcken ab und ist damit der letzte, nicht der nächste. Ohne Aufrufweg wäre die Fähigkeit vorhanden und unbenutzbar. | **Nur die Schnittstellen liefern, Aufruf B14 und B13 überlassen**: verworfen, weil der Auftraggeber die Fähigkeit ausdrücklich für den Betrieb angefordert hat und B14 mehrere Blöcke entfernt liegt. **Auf B14 warten**: verworfen aus demselben Grund. **Über einen Konfigurationsschalter buchen**: verworfen — kein Verursacher, keine Berechtigung, kein Audit-Eintrag, und ein Neustart je Eingriff. **Verlauf als Chat-Ausgabe**: verworfen — ab der zweiten Seite unlesbar und ohne Weg, den Charakter zu wählen. Eingegrenzt durch FR-046: Kommando und Fenster sind dünne Schalen über `CurrencyAdmin` und `CoinLedger`; B14 ersetzt die eine, B13 übernimmt die andere, die Schnittstellen bleiben. Für das Fenster ist die Abweichung ohnehin klein — **B07 hält seine Klassenauswahl bereits im eigenen Block**. → **ADR-028, angenommen 2026-08-22.** |
| **Herauslösung des Anteilsrechners aus `XpDistributor`** (Eingriff in einen ausgelieferten Block) | FR-024 verlangt für Coins dieselbe Regel wie für Erfahrung. Die Regel ist heute in `XpDistributor` eingebaut und von aussen nicht aufrufbar. | **Die Regel für Coins nachbauen**: verworfen — zwei Umsetzungen derselben Regel bleiben genau so lange gleich, bis jemand eine anfasst, und die Abweichung wäre für Spieler sichtbar und für niemanden erklärbar. **`XpDistributor` von B08b aus aufrufen**: verworfen, es vergibt Erfahrung als Nebenwirkung. Die Herauslösung ist verhaltensneutral und durch B06s vorhandene Tests abgesichert. **ADR-pflichtig.** |
