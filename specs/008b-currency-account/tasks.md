---

description: "Aufgabenliste B08b · Währung & Konto"
---

# Tasks: B08b · Währung & Konto

**Input**: Design-Dokumente aus `/specs/008b-currency-account/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Enthalten. Prinzip VII der Constitution verlangt Unit-Tests ohne laufenden Server für jede
Formel und jede Regel sowie echte PostgreSQL-Instanzen für die Persistenz — Tests sind hier keine
Option, sondern Vorgabe.

**Organisation**: Nach User Story gruppiert, damit jede für sich umsetzbar und prüfbar ist.

## Format: `[ID] [P?] [Story] Beschreibung`

- **[P]**: parallelisierbar — andere Datei, keine offene Abhängigkeit
- **[Story]**: die User Story aus [spec.md](./spec.md)
- Jede Aufgabe nennt ihren Pfad

## Pfade

Vier Module aus B01, unverändert: `rpg-core` (Regeln, bukkitfrei), `rpg-persistence`,
`rpg-platform` (Paper), `rpg-plugin` (Verdrahtung und Konfiguration).

---

## Phase 1: Setup

**Zweck**: Die Pakete und Dateien anlegen, in denen alles Weitere entsteht.

- [X] T00# [P] Paket `rpg-core/src/main/java/rpg/core/currency/` mit `package-info.java` anlegen — die Blockgrenze benennen: Konto und Buchung gehören hierher, **Preise nicht** (ADR-027), nach dem Muster von `rpg/core/classes/package-info.java`
- [X] T00# [P] Paket `rpg-persistence/src/main/java/rpg/persistence/currency/` anlegen
- [X] T00# [P] Paket `rpg-platform/src/main/java/rpg/platform/currency/` mit `package-info.java` anlegen — hier und nur hier wird Paper angefasst
- [X] T00# [P] Paket `rpg-plugin/src/main/java/rpg/plugin/command/` mit `package-info.java` anlegen — Kopfkommentar: **Provisorium**, B14 übernimmt (FR-046, research.md R7)
- [X] T00# [P] `rpg-plugin/src/main/resources/currency.yml` als Gerüst anlegen mit `account`, `drops`, `ledger` nach [contracts/currency-config.md](./contracts/currency-config.md), Kopfkommentar mit dem Verweis auf ADR-027 und dem Satz „prices are NOT here"
- [X] T00# [P] `CurrencyMessageKeys` in `rpg-core/src/main/java/rpg/core/currency/CurrencyMessageKeys.java` anlegen — die neun Schlüssel aus dem Konfigurationsvertrag, plus `all()` nach dem Muster von `AbilityMessageKeys`
- [X] T00# Die neun Texte in `rpg-plugin/src/main/resources/messages.yml` eintragen, damit `MessageKeyValidator` beim Start nicht fehlschlägt

---

## Phase 2: Foundational (blockierende Voraussetzungen)

**Zweck**: Die Werte und die Konfiguration, die **jede** Story braucht. **⚠️ Vor dieser Phase kann keine Story beginnen.**

### Die Werte des Blocks

- [X] T008 [P] `BookingReason` in `rpg-core/src/main/java/rpg/core/currency/BookingReason.java` — der abgeschlossene Vorrat aus [data-model.md](./data-model.md) §3, einschliesslich `STARTING_BALANCE`, `PILE_CASHED_IN` und der drei B11-Werte. Javadoc: warum abgeschlossen und nicht frei (FR-005)
- [X] T009 [P] `BookingResult` in `rpg-core/src/main/java/rpg/core/currency/BookingResult.java` — fünf Werte, je mit `MessageKey`, `isSuccess()` und `messageKey()` nach dem Muster von `RankResult`
- [X] T010 [P] `CharacterBalance` in `rpg-core/src/main/java/rpg/core/currency/CharacterBalance.java` — unveränderlicher Record: Charakterkennung, Betrag, Revision
- [X] T011 [P] `LedgerEntry` in `rpg-core/src/main/java/rpg/core/currency/LedgerEntry.java` — Record nach [data-model.md](./data-model.md) §2, `actor` als `Optional<String>`
- [X] T012 [P] `Currency` in `rpg-core/src/main/java/rpg/core/currency/Currency.java` — die Schnittstelle aus [contracts/currency-api.md](./contracts/currency-api.md), noch ohne Umsetzung. Javadoc an `canAfford`: **keine Reservierung**, wer zahlen will ruft `debit`

### Konfiguration

- [X] T013 `CurrencyConfig` in `rpg-core/src/main/java/rpg/core/currency/CurrencyConfig.java` — Record über Startguthaben, Drop-Einstellungen und Aufbewahrungsdauer
- [X] T014 `CurrencyConfigSchema` in `rpg-core/src/main/java/rpg/core/currency/CurrencyConfigSchema.java` — die Prüftabelle aus dem Konfigurationsvertrag, Fail-Fast mit klarer Meldung (FR-058), nach dem Muster von `AbilityConfigSchema`
- [X] T015 [P] `CurrencyConfigValidationTest` in `rpg-core/src/test/java/rpg/core/currency/` — je Regel ein abgelehnter und ein angenommener Fall; negatives Startguthaben, `merge-radius` über 16, `max-piles` gleich null, fehlende Dauer
- [X] T016 `currency.yml` in `RpgPlugin` laden und validieren, nach dem Muster der übrigen Blockkonfigurationen — Startfehler bricht den Start ab

**Checkpoint**: Werte und Konfiguration stehen; die Stories können beginnen.

---

## Phase 3: User Story 1 — Ein Charakter besitzt ein Konto, und es überlebt (P1) 🎯 MVP

**Goal**: Ein Kontostand je Charakter, Buchungen mit Grund, niemals negativ, übersteht Neustart.

**Independent Test**: Buchen, abmelden, Server neu starten, Stand erneut lesen — ohne dass ein Mob,
eine Ausrüstungsstufe oder ein Rang existiert.

### Tests zuerst

- [X] T017 [P] [US1] `DefaultCurrencyTest` in `rpg-core/src/test/java/rpg/core/currency/` — Gutschrift erhöht, Abbuchung senkt, jede Buchung trägt ihren Grund (Szenarien 1 und 6)
- [X] T018 [P] [US1] `BalanceNeverNegativeTest` in `rpg-core/src/test/java/rpg/core/currency/` — eine zu grosse Abbuchung wird **abgelehnt**, der Stand bleibt unverändert und wird **nicht** auf null gekappt (Szenario 2, FR-004)
- [X] T019 [P] [US1] `BookingAtomicityTest` in `rpg-core/src/test/java/rpg/core/currency/` — 1000 nebenläufige `debit` auf ein Konto; die Summe der `OK` übersteigt den Ausgangsstand nicht, kein Stand wird negativ (Szenario 3, SC-001)
- [X] T020 [P] [US1] `InvalidAmountTest` in `rpg-core/src/test/java/rpg/core/currency/` — Betrag null und negativ ergeben `INVALID_AMOUNT`; eine Gutschrift am Rand von `Long.MAX_VALUE` ergibt `WOULD_OVERFLOW` statt umzulaufen (FR-009, FR-010)
- [X] T021 [P] [US1] `StartingBalanceTest` in `rpg-core/src/test/java/rpg/core/currency/` — bei Startguthaben null entsteht **keine** Buchung; bei einem Wert darüber eine Gutschrift mit dem Grund `STARTING_BALANCE`, die im Verlauf steht (FR-011a, Szenarien 7 und 7a)
- [X] T021a [P] [US1] `NoRetroactiveStartingBalanceTest` in `rpg-core/src/test/java/rpg/core/currency/` — ein Charakter ohne Kontozeile meldet **null**, nicht den konfigurierten Wert; eine spätere Erhöhung des Startguthabens lässt bestehende Stände unverändert (FR-011b, Szenario 7b)

### Regelschicht

- [X] T022 [US1] `DefaultCurrency` in `rpg-core/src/main/java/rpg/core/currency/DefaultCurrency.java` — Stände im Speicher, **eine Sperre je Konto**; Prüfen und Ändern in einem Schritt (FR-006). Javadoc: warum es keine Fassung gibt, die nur prüft und eine Zusage zurückgibt
- [X] T023 [US1] Die Ablehnungspfade in `DefaultCurrency` vollständig: `NOT_ENOUGH`, `INVALID_AMOUNT`, `WOULD_OVERFLOW`, `NO_SUCH_CHARACTER` — jeder lässt den Stand nachweislich unverändert (FR-008)
- [X] T024 [US1] `CharacterBalanceRepository` in `rpg-core/src/main/java/rpg/core/currency/CharacterBalanceRepository.java` — im **Blockpaket**, nicht in `rpg/core/persistence/` (ADR-015 Punkt 4)

### Persistenz — die drei Eintragungen (ADR-015 Punkt 7)

- [X] T025 [US1] Migration `rpg-persistence/src/main/resources/db/migration/V8_2__character_balance.sql` — Tabelle nach [data-model.md](./data-model.md) §1 **mit `CHECK (balance >= 0)`** und `ON DELETE CASCADE`. Kommentar im SQL: warum die Prüfung auch in der Datenbank steht
- [X] T026 [US1] **Eintragung 1 von 3**: `CHARACTER_BALANCE` in `rpg-core/src/main/java/rpg/core/persistence/AggregateType.java` ergänzen, mit dem Javadoc-Hinweis auf die beiden anderen Eintragungen, wie ihn `CHARACTER_ABILITIES` trägt
- [X] T027 [US1] **Eintragung 2 von 3**: `CHARACTER_BALANCE` in `FlushCycle.WRITE_ORDER` in `rpg-persistence/src/main/java/rpg/persistence/FlushCycle.java` **nach `CHARACTER`** einsortieren, mit Begründungskommentar wie bei den Nachbarn
- [X] T028 [US1] `JdbcCharacterBalanceRepository` in `rpg-persistence/src/main/java/rpg/persistence/currency/` — Laden je Spieler, Dirty-Mark je Charakter
- [X] T029 [US1] `CharacterBalanceBatchWriter` in `rpg-persistence/src/main/java/rpg/persistence/currency/` — Upsert im Batch, nach dem Muster des Fortschritt-Writers aus B06
- [X] T030 [US1] **Eintragung 3 von 3**: `CurrencyModule` in `rpg-persistence/src/main/java/rpg/persistence/currency/CurrencyModule.java` — Repository und Writer bei `FlushCycle` registrieren, mit dem Kommentar „Eintragung 3 von 3 (ADR-015)"
- [X] T031 [US1] `CharacterBalanceRepositoryTest` in `rpg-persistence/src/test/java/rpg/persistence/currency/` gegen Testcontainers — Schreiben, Lesen, Neustart des Containers, und ein **direkter negativer `UPDATE`, den die Datenbank ablehnt**
- [X] T032 [US1] `CurrencyMigrationTest` in `rpg-persistence/src/test/java/rpg/persistence/currency/` — `V8_2` läuft auf eine **bestehende** Datenbank aus B08 auf, nicht nur auf eine leere

### Sitzung

- [X] T033 [US1] `SessionBundle` in `rpg-core/src/main/java/rpg/core/session/SessionBundle.java` um `List<CharacterBalance> balances` als zehnte Komponente erweitern — plus einen Kurzkonstruktor ohne sie, wie ihn B08 für `abilities` angelegt hat
- [X] T034 [US1] `SessionBundleLoader` in `rpg-core/src/main/java/rpg/core/session/SessionBundleLoader.java` um die Ladeanweisung erweitern — **eine** Runde, keine zweite Abfrage im Anmeldepfad (ADR-015 Punkt 3, FR-017)
- [X] T035 [US1] Alle Konstruktionsstellen von `SessionBundle` nachziehen; der Compiler findet sie
- [X] T036 [US1] Sitzungsende in der Währungsanbindung: **beiseitelegen, markieren, freigeben** — die `lastKnown`-Karte vor dem Entfernen füllen, sonst liest der asynchrone Flush ins Leere (ADR-015 Punkt 7, FR-016)
- [X] T037 [P] [US1] `CurrencySessionEndTest` in `rpg-core/src/test/java/rpg/core/currency/` — eine Buchung unmittelbar vor der Freigabe wird noch geschrieben
- [X] T038 [P] [US1] `SeparateBalancesTest` in `rpg-core/src/test/java/rpg/core/currency/` — zwei Charaktere desselben Spielers, eine Buchung auf den ersten lässt den zweiten unverändert (Szenario 5, SC-003, ADR-011)
- [X] T039 [P] [US1] `DeathDoesNotCostCoinsTest` in `rpg-core/src/test/java/rpg/core/currency/` — der Tod verändert den Stand nicht, und es existiert **keine** Buchungsart für einen Todesverlust (FR-012, Szenario 8)
- [X] T040 [US1] `DefaultCurrency` und `CurrencyModule` in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` verdrahten

**Checkpoint**: Das Konto steht vollständig und ist ohne Server, mit Datenbank und über einen Neustart geprüft. **Das ist der MVP.**

---

## Phase 4: User Story 2 — Der Kill lässt Coins fallen, das Aufheben bucht (P2)

**Goal**: Am Ort des Todes liegen Coins, die nur der Berechtigte aufheben kann; erst das Aufheben bucht.

**Independent Test**: Kreatur töten, Haufen aufheben, Stand vorher und nachher lesen.

### Die Herauslösung in B06 (ADR-pflichtig, research.md R4)

- [X] T041 [US2] `ShareCalculator` in `rpg-core/src/main/java/rpg/core/progression/ShareCalculator.java` anlegen — die fünf Schritte aus `XpDistributor` unverändert: Schadensanteile aus B05 (nie neu berechnet), Gruppe als **ein** Beitragender, Teilung auf die Mitglieder in Reichweite, Bonus je zusätzlichem Mitglied, **Abrunden mit Rest auf dem Tisch**
- [X] T042 [US2] `XpDistributor` in `rpg-core/src/main/java/rpg/core/progression/XpDistributor.java` auf `ShareCalculator` umstellen, ohne sein Verhalten zu ändern
- [X] T043 [US2] **Abnahmebedingung der Herauslösung**: `./gradlew :rpg-core:test --tests '*XpDistributor*'` bleibt **unverändert grün**. Kein Test wird angepasst — wird einer rot, ist die Herauslösung nicht verhaltensneutral
- [X] T044 [P] [US2] `ShareCalculatorTest` in `rpg-core/src/test/java/rpg/core/progression/` — Einzelspieler, Gruppe, ausser Reichweite, Rundungsrest bleibt liegen

### Ertrag und Planung — bukkitfrei

- [X] T045 [P] [US2] `MobCoinProvider` in `rpg-core/src/main/java/rpg/core/currency/MobCoinProvider.java` — `OptionalLong coinsFor(String)`. Javadoc wörtlich wie `MobXpProvider`: leer heisst „kein eigener Eintrag", **nicht** null (FR-023)
- [X] T046 [P] [US2] `ConfigMobCoinProvider` in `rpg-core/src/main/java/rpg/core/currency/ConfigMobCoinProvider.java` — liest `drops.by-type` und `drops.default`; wird später von B10 abgelöst (FR-032)
- [X] T047 [P] [US2] `CoinDropPlan` in `rpg-core/src/main/java/rpg/core/currency/CoinDropPlan.java` — Record: berechtigter Charakter, Betrag, `WorldPoint`. Bukkitfrei
- [X] T048 [US2] `CoinDropPlanner` in `rpg-core/src/main/java/rpg/core/currency/CoinDropPlanner.java` — Kill plus Schadensanteile ergibt eine Liste von `CoinDropPlan`, über `ShareCalculator`. **Ein Kill ohne Berechtigten ergibt eine leere Liste** (FR-031)
- [X] T049 [P] [US2] `CoinDropPlannerTest` in `rpg-core/src/test/java/rpg/core/currency/` — Einzelkill, Gruppenkill mit einem Plan je Berechtigtem, unbekannter Kreaturtyp bekommt den Standardbetrag, Umgebungstod ergibt nichts

### Der Haufen — Paper

- [X] T050 [US2] **Zuerst prüfen, nicht raten** (research.md R1c): gegen das Artefakt `26.2.build.112-stable` feststellen, wie sich die Verfallsfrist eines `Item` je Entity setzen lässt. Ergebnis in `research.md` R1c nachtragen und die Schemaregel für `drops.despawn-seconds` daran ausrichten
- [X] T051 [US2] `CoinPileTag` in `rpg-platform/src/main/java/rpg/platform/currency/CoinPileTag.java` — Betrag, **Charakterkennung** und **eindeutige Haufenkennung** im `PersistentDataContainer`, nach dem Muster von `BoundItemTag`. Javadoc: die Haufenkennung existiert, damit **Vanilla die Haufen nie zusammenlegt** (research.md R2)
- [X] T052 [US2] `CoinPile` in `rpg-platform/src/main/java/rpg/platform/currency/CoinPile.java` — Erzeugen eines `Item`-Entity, `setOwner` mit der **Spieler**kennung als grober Vorfilter, Container schreiben, Verfallsfrist nach T050
- [X] T053 [US2] Zusammenlegen **vor** dem Erzeugen (FR-028): Umkreisabfrage über die chunk-gebundene Methode des Servers im konfigurierten `merge-radius`; ein Haufen desselben **Charakters** wächst im Container, statt dass ein zweiter entsteht. Keine lineare Iteration aller Entities (Prinzip II)
- [X] T054 [US2] Deckelung (FR-030a bis FR-030d): liegt die Zahl der Haufen bei `max-piles`, wird der **weltweit älteste** Haufen seinem Besitzer **gutgeschrieben** (`PILE_CASHED_IN`) und abgeräumt, danach entsteht der neue. **Keine Coin geht verloren.** Ist der Besitzer abgemeldet, wirkt die Gutschrift über den Repository-Pfad aus T078
- [X] T054a [US2] Der Entstehungszeitpunkt kommt in den Datencontainer (T051), damit „der älteste" überhaupt bestimmbar ist
- [X] T054b [US2] Javadoc an der Abräumung: **Abräumen und Verfallen enden verschieden, und das ist beabsichtigt** — Frist abgelaufen heisst niemandem gutschreiben (FR-029), vom Server abgeräumt heisst gutschreiben. Eigene Versäumnisse kosten, Serverlast nicht. Wer das später vereinheitlicht, nimmt einer Seite ihre Begründung
- [X] T054c [US2] Nur der Berechtigte **sieht** seinen Haufen (FR-027a) — die Sperre gegen das Aufheben bleibt trotzdem bestehen: Unsichtbarkeit ist Darstellung, und Darstellung ist nie die Autorität (Prinzip VI). **Zuerst prüfen**, welchen Weg das Paper-Artefakt dafür anbietet; im Projekt ist Sichtbarkeit je Spieler bislang nirgends benutzt
- [X] T055 [US2] `CoinDropListener` in `rpg-platform/src/main/java/rpg/platform/currency/CoinDropListener.java` — hängt an `CombatDeathEvent` auf dem Kern-Bus, **nicht** an einem Bukkit-Listener, nach dem Muster von `ProgressionDeathListener`. **Der Ort wird hier als Wert gelesen**, nie später über die Id des toten Wesens (ADR-015 Punkt 6, FR-020)
- [X] T056 [US2] `CoinPickupListener` in `rpg-platform/src/main/java/rpg/platform/currency/CoinPickupListener.java` — Aufhebeversuch **abbrechen**, Charakterkennung aus dem Container gegen den aktiven Charakter prüfen, buchen mit `PILE_PICKED_UP`, Entity entfernen. Kein Wert aus dem Ereignis wird übernommen (Prinzip VI)
- [X] T057 [US2] Passt der Charakter nicht, wird abgebrochen und der Haufen **bleibt liegen** — nicht entfernt, nicht gutgeschrieben (research.md R3)

### Tests

- [X] T058 [P] [US2] `CoinPileTagTest` in `rpg-platform/src/test/java/rpg/platform/currency/` mit MockBukkit — Schreiben und Lesen der drei Werte; **zwei Haufen sind nie `isSimilar`**
- [X] T059 [P] [US2] `CoinPileMergeTest` in `rpg-platform/src/test/java/rpg/platform/currency/` — zwei Kills dicht beieinander ergeben **einen** Haufen mit der Summe (Szenario 8); zwei Kills verschiedener Charaktere ergeben zwei
- [X] T060 [P] [US2] `CoinPickupTest` in `rpg-platform/src/test/java/rpg/platform/currency/` — der Berechtigte hebt auf und wird gutgeschrieben; **nichts landet im Inventar** (FR-033); ein Fremder hebt nicht auf (Szenario 5)
- [X] T061 [P] [US2] `CoinPileCharacterSwitchTest` in `rpg-platform/src/test/java/rpg/platform/currency/` — **die Prüfung, die Vanilla allein nicht leistet**: derselbe Spieler mit einem anderen Charakter hebt nicht auf, nach dem Zurückwechseln schon (ADR-011, research.md R3)
- [X] T062 [P] [US2] `CoinPileExpiryTest` in `rpg-platform/src/test/java/rpg/platform/currency/` — ein verfallener Haufen wird **niemandem** gutgeschrieben (Szenario 6, FR-029)
- [X] T063 [P] [US2] `CoinPileCapTest` in `rpg-platform/src/test/java/rpg/platform/currency/` — bei erreichter Deckelung wird der älteste Haufen gutgeschrieben und abgeräumt, der neue entsteht, die Zahl bleibt bei der Deckelung, und **die Summe aller Coins in Welt und Konten bleibt gleich** (Szenario 7a, FR-030b)
- [X] T063a [P] [US2] **blockiert durch T078 (Offline-Pfad)** — `CoinPileCapOfflineOwnerTest` in `rpg-persistence/src/test/java/rpg/persistence/currency/` — der abgeräumte Haufen eines **abgemeldeten** Besitzers wird trotzdem gutgeschrieben (Szenario 7b, FR-030c)
- [X] T063b [P] [US2] `CoinPileVisibilityTest` in `rpg-platform/src/test/java/rpg/platform/currency/` — der Berechtigte sieht seinen Haufen, ein Unbeteiligter sieht **gar nichts**, und bei einem Kill mit zwei Berechtigten sieht jeder nur seinen eigenen (Szenarien 5 und 5a, FR-027a)
- [X] T064 [US2] `CoinDropListener` und `CoinPickupListener` in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` verdrahten

**Checkpoint**: Coins entstehen im Spiel und landen über das Aufheben auf dem Konto.

---

## Phase 5: User Story 3 — Der Betreiber greift ein, und alles bleibt nachvollziehbar (P3)

**Goal**: Ein dauerhafter Verlauf jeder Buchung, und setzen/hinzufügen/entfernen mit Verursacher.

**Independent Test**: Buchen, Verlauf lesen, Server neu starten, Verlauf erneut lesen; danach ein
Eingriff, der im Verlauf mit Verursacher erscheint.

### Der Verlauf

- [X] T065 [US3] Migration `rpg-persistence/src/main/resources/db/migration/V8_3__coin_ledger.sql` — Tabelle nach [data-model.md](./data-model.md) §2 mit `CHECK (amount > 0)`, `direction` als Spalte statt Vorzeichen, Index auf `(character_id, occurred_at DESC)`, `ON DELETE CASCADE`
- [X] T066 [US3] **Eintragung 1 von 3**: `COIN_LEDGER` in `rpg-core/src/main/java/rpg/core/persistence/AggregateType.java` ergänzen — Javadoc: nur anfügend, viele Zeilen, Warteschlangen-Muster wie `AUDIT_LOG`
- [X] T067 [US3] **Eintragung 2 von 3**: `COIN_LEDGER` in `rpg-persistence/src/main/java/rpg/persistence/FlushCycle.java` in `WRITE_ORDER` nach `CHARACTER` einsortieren
- [X] T068 [US3] `CoinLedger` in `rpg-core/src/main/java/rpg/core/currency/CoinLedger.java` — die Lesemethoden aus dem Vertrag (Seite über Versatz und Limit, Gesamtzahl, Zeitraum). Javadoc: **sie lesen die Datenbank** und gehören nicht in einen Spielereignis-Pfad; **es gibt keine unbegrenzte Abfrage**
- [X] T069 [US3] `JdbcCoinLedgerRepository` in `rpg-persistence/src/main/java/rpg/persistence/currency/` — `append` reiht ein und markiert **eine synthetische Warteschlangen-Kennung**, nach dem Muster von `JdbcAuditLogRepository`
- [X] T070 [US3] `CoinLedgerBatchWriter` in `rpg-persistence/src/main/java/rpg/persistence/currency/` — leert die Warteschlange im Batch; kein Eintrag wird zweimal geschrieben
- [X] T071 [US3] **Eintragung 3 von 3**: Repository und Writer in `CurrencyModule` registrieren
- [X] T072 [US3] `DefaultCurrency` schreibt den Verlaufseintrag **innerhalb der Sperre**, damit `balance_before` und `balance_after` zum Nachbarn passen — eingereiht, nicht geschrieben, also ohne Kosten im Spielereignis (FR-037)
- [X] T073 [US3] Aufbewahrung (FR-038): Buchungen aus dem Spielgeschehen werden nach `ledger.retention-days` aufgeräumt, **Einträge mit gesetztem `actor` nie**. Läuft im Autosave-Zyklus mit, nicht als eigene Aufgabe
- [X] T074 Den Standardwert für `ledger.retention-days` in `currency.yml` setzen und im Kopfkommentar begründen — bei 800 Mobs wird diese Tabelle die grösste des Projekts (plan.md, offener Punkt 3)

### Der Eingriff

- [X] T075 [US3] `CurrencyAdmin` in `rpg-core/src/main/java/rpg/core/currency/CurrencyAdmin.java` — `set`, `add`, `remove`, jeweils mit `actor`. **`actor` ist Pflicht und darf nicht leer sein**
- [X] T076 [US3] `remove` lehnt ab, was den Stand negativ machte — **auch der Betreiber erzeugt keinen negativen Stand** (FR-003, Szenario 5)
- [X] T077 [US3] Online-Pfad in `rpg-core/src/main/java/rpg/core/currency/CurrencyAdmin.java`: ist der Charakter angemeldet, wirkt der Eingriff im maßgeblichen Cache, sonst überschreibt ihn der nächste Flush (Prinzip IV, FR-043)
- [X] T078 [US3] Offline-Pfad: ist er nicht angemeldet, wirkt der Eingriff auf dem gespeicherten Stand (FR-042). Javadoc: warum der Datenbankzugriff hier zulässig ist — ein Admin-Kommando ist **kein Spielereignis** (research.md R7)
- [X] T079 [US3] Ein Eingriff auf einen nicht existierenden Charakter ergibt `NO_SUCH_CHARACTER` — **ohne stillschweigend einen anzulegen** (FR-044)
- [X] T080 [US3] Jeder Eingriff schreibt **zweimal**: in den Verlauf mit `ADMIN_SET`/`ADMIN_ADD`/`ADMIN_REMOVE` (FR-040) und als `AuditEntry` in das Audit-Log aus B02 (FR-041)

### Das vorläufige Kommando (research.md R7)

- [X] T081 [US3] `CoinsCommand` in `rpg-plugin/src/main/java/rpg/plugin/command/CoinsCommand.java` — **dünne Schale**: Argumente parsen, Berechtigung prüfen, `CurrencyAdmin` rufen oder das Fenster öffnen. Sonst nichts. Klassen-Javadoc: Provisorium nach ADR-028, B14 ersetzt die Schale und B13 übernimmt die Anzeige; die Schnittstellen bleiben stehen (FR-046)
- [X] T082 [US3] `plugin.yml` um den **ersten `commands`-Block des Projekts** erweitern: `coins` mit den vier Unterbefehlen und den Rechteknoten `rpg.currency.balance` und `rpg.currency.admin`
- [X] T083 [US3] Der Charakter muss bei `set`, `add` und `remove` genannt werden — ein Spieler hat bis zu drei, und ein Eingriff ohne Angabe wäre mehrdeutig (Konfigurationsvertrag)
- [X] T084 [US3] Ohne `rpg.currency.admin` wird abgewiesen und nichts geändert (FR-045)

### Das Fenster (ADR-028)

- [X] T084a [US3] `historyOf(UUID, int offset, int limit)` und `historyCount(UUID)` in `rpg-core/src/main/java/rpg/core/currency/CoinLedger.java` — **ein Versatz, nicht nur ein Limit**; ohne ihn ist Blättern nicht ausdrückbar. Es gibt **keine** unbegrenzte Abfrage (FR-046a)
- [X] T084b [US3] `history.page-size` in `rpg-plugin/src/main/resources/currency.yml` und in `CurrencyConfigSchema` ergänzen — `> 0` und `<= 45`, weil die unterste Reihe die Navigation trägt
- [X] T084c [US3] `CurrencyMenu` in `rpg-platform/src/main/java/rpg/platform/currency/CurrencyMenu.java` — zwei Ebenen: Charakterauswahl und Verlaufsseite, aus **reinen Vanilla-Materialien** (ADR-005), alle Texte über `Messages`, nach dem Muster von `rpg-platform/src/main/java/rpg/platform/classes/ClassSelectionMenu.java`
- [X] T084d [US3] Die Charakterauswahl zeigt je Charakter Name, Klasse und **eigenen Stand** — **nie eine Summe**: drei Charaktere sind drei Stände (FR-046b, ADR-011)
- [X] T084e [US3] Seitenblätterung im Verlaufsfenster: `page-size` Einträge je Seite, Vor- und Zurück-Knopf in der untersten Reihe, an beiden Enden **kein** Knopf darüber hinaus
- [X] T084f [US3] `CurrencyMenuListener` in `rpg-platform/src/main/java/rpg/platform/currency/CurrencyMenuListener.java` — Klicks behandeln und das Fenster gegen das Herausnehmen sperren; die Einträge sind Anzeige, keine Gegenstände. Nach dem Muster von `ClassSelectionListener`
- [X] T084g [US3] `/coins` und `/coins <player>` in `rpg-plugin/src/main/java/rpg/plugin/command/CoinsCommand.java` öffnen das Fenster; `plugin.yml` um beide Formen erweitern

### Tests

- [X] T085 [P] [US3] `CoinLedgerTest` in `rpg-persistence/src/test/java/rpg/persistence/currency/` gegen Testcontainers — jede Buchung erscheint mit Zeitpunkt, Betrag, Richtung, Grund, Stand davor und danach (Szenario 1)
- [X] T086 [P] [US3] `CoinLedgerPersistsRestartTest` — der Verlauf ist nach einem Neustart des Containers unverändert lesbar (Szenario 2, SC-002)
- [X] T087 [P] [US3] `CoinLedgerRetentionTest` — alte Spielbuchungen verschwinden, Einträge mit `actor` bleiben (FR-038)
- [X] T088 [P] [US3] `CurrencyAdminTest` in `rpg-core/src/test/java/rpg/core/currency/` — setzen, hinzufügen, entfernen; jeder Eingriff nennt seinen Verursacher (Szenarien 3 und 4, SC-011)
- [X] T089 [P] [US3] `AdminCannotGoNegativeTest` in `rpg-core/src/test/java/rpg/core/currency/` — mehr entfernen als vorhanden wird abgelehnt (Szenario 5)
- [X] T090 [P] [US3] `AdminOfflineCharacterTest` in `rpg-persistence/src/test/java/rpg/persistence/currency/` — der Eingriff auf einen offline Charakter steht beim nächsten Laden da (Szenario 6)
- [X] T091 [P] [US3] `AdminOnlineNotOverwrittenTest` — ein Eingriff auf einen angemeldeten Charakter überlebt das nächste Flush und die Abmeldung
- [X] T092 [P] [US3] `AdminAuditTrailTest` — jeder Eingriff erscheint zusätzlich im Audit-Log (Szenario 8)
- [X] T092a [P] [US3] `CoinLedgerPagingTest` in `rpg-persistence/src/test/java/rpg/persistence/currency/` — neueste zuerst, Versatz und Limit greifen, `historyCount` stimmt, **keine Buchung erscheint auf zwei Seiten** (Szenario 1b, SC-010)
- [X] T092b [P] [US3] `CurrencyMenuTest` in `rpg-platform/src/test/java/rpg/platform/currency/` mit MockBukkit — die Auswahl zeigt drei Stände und **keine Summe** (Szenario 1a), die Wahl führt zum richtigen Verlauf, Blättern hält an beiden Enden, und **nichts lässt sich aus dem Fenster nehmen**
- [X] T093 [US3] `CoinsCommand`, `CurrencyMenu`, `CurrencyMenuListener`, `CurrencyAdmin` und `CoinLedger` in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java` verdrahten

**Checkpoint**: Der Betrieb kann eine Beschwerde klären und einen Fehler gutmachen.

---

## Phase 6: User Story 4 — Der Ausrüstungsaufstieg kostet Coins (P4)

**Goal**: B07s undurchsichtiger `cost`-Block wird ausgelesen und vor dem Aufstieg geprüft.

**Independent Test**: Aufstieg mit zu wenig Coins versuchen, dann mit genug.

- [X] T094 [P] [US4] `CostSpec` in `rpg-core/src/main/java/rpg/core/currency/CostSpec.java` — `parse(Map<String,Object>)`, `coins()`, `isFree()`. Eine leere Map ergibt „kostenlos" (FR-049)
- [X] T095 [US4] Startprüfung über **alle** Ausrüstungsstufen aller Klassen: ein unbekannter Schlüssel im `cost`-Block ist ein **Startfehler**, dessen Meldung Schlüssel, Klasse und Stufe nennt (FR-050)
- [X] T096 [US4] `EquipmentPurchase` in `rpg-core/src/main/java/rpg/core/currency/EquipmentPurchase.java` — die Kostenprüfung vor den Stufenaufstieg, in **diesem** Block, nicht in B07 (research.md R6)
- [X] T097 [US4] In `rpg-core/src/main/java/rpg/core/currency/EquipmentPurchase.java`: ein Aufstieg mit zu wenig Coins scheitert mit einem **eigenen, unterscheidbaren** Ergebnis, ohne Stufe und Stand zu verändern (FR-048, Szenario 1)
- [X] T098 [US4] In `rpg-core/src/main/java/rpg/core/currency/EquipmentPurchase.java`: scheitert ein Aufstieg aus einem anderen Grund, ist **nichts abgebucht** (Szenario 4)
- [X] T099 [P] [US4] `CostSpecTest` in `rpg-core/src/test/java/rpg/core/currency/` — `{coins: 500}`, `{}`, und `{coins: 500, shards: 7}` als Startfehler
- [X] T100 [P] [US4] `EquipmentTierCostTest` in `rpg-core/src/test/java/rpg/core/currency/` — die vier Szenarien aus US4, einschliesslich Stufe 1 ohne Coins (SC-007)
- [X] T101 [US4] **Abnahmebedingung**: `ClassSourceInvariantsTest` bleibt **unverändert grün**. B07 wird nicht angefasst; der Test beweist ab jetzt, dass die Auflösung am richtigen Ort geschieht (research.md R6)

**Checkpoint**: Der erste ausgelieferte Block ist abgeschlossen.

---

## Phase 7: User Story 5 — Der Rangaufstieg kostet Coins (P5)

**Goal**: `advanceRank` bekommt eine Kostenprüfung, `RankResult` das fehlende Ergebnis.

**Independent Test**: Rangaufstieg mit zu wenig und danach mit genug Coins.

- [X] T102 [US5] `rank-cost` im Fähigkeitsschema ergänzen: `AbilityConfigSchema` in `rpg-core/src/main/java/rpg/core/ability/AbilityConfigSchema.java` — optionaler Block je Fähigkeit, fehlt er, kostet der Rang nichts (FR-054)
- [ ] T103 [US5] `rank-cost` für die achtzehn Fähigkeiten in `rpg-plugin/src/main/resources/abilities.yml` eintragen
- [X] T104 [US5] `NOT_ENOUGH_COINS` in `rpg-core/src/main/java/rpg/core/ability/RankResult.java` ergänzen, mit `MessageKey`
- [X] T105 [US5] Das Javadoc von `rpg-core/src/main/java/rpg/core/ability/RankResult.java` korrigieren — der Absatz „there are no coins anywhere in this project" ist ab diesem Block falsch (FR-055). Stattdessen: was die Kostenprüfung tut und wo sie sitzt
- [X] T106 [US5] Die Kostenprüfung in `rpg-core/src/main/java/rpg/core/ability/AbilityRuntime.java` vor `advanceRank` setzen — **zuletzt**: erst Freischaltung, erst Höchstrang, **dann** das Geld (FR-052)
- [X] T107 [US5] **Den Test umdrehen, nicht löschen**: `AbilityRankTest` in `rpg-core/src/test/java/rpg/core/ability/AbilityRankTest.java` sichert heute in den Zeilen um 131–136 zu, dass `RankResult` **kein** `NOT_ENOUGH_COINS` enthält. Die Zusicherung wird umgekehrt und der Begründungskommentar auf ADR-027 umgeschrieben, damit die Änderung im Diff sichtbar bleibt
- [X] T108 [P] [US5] `AbilityRankCostTest` in `rpg-core/src/test/java/rpg/core/currency/` — die vier Szenarien aus US5, darunter der **Höchstrang, an dem nichts abgebucht wird** (Szenario 3)
- [X] T109 [US5] **Abnahmebedingung**: `rpg-core/src/test/java/rpg/core/ability/AbilitySourceInvariantsTest.java` bleibt grün — es verbietet nur Bukkit-Pakete und steht dem nicht im Weg

**Checkpoint**: Der zweite ausgelieferte Block ist abgeschlossen. Der Block hat seinen Zweck erfüllt.

---

## Phase 8: User Story 6 — Der Spieler sieht seinen Stand (P6)

**Goal**: Die Abfrage und die Meldungen, auf die B13 später aufsetzt.

**Independent Test**: Stand abfragen, gegen den gebuchten Wert vergleichen.

- [X] T110 [US6] `balanceOf` in `rpg-core/src/main/java/rpg/core/currency/Currency.java` antwortet aus dem Cache und löst **keinen Datenbankzugriff** aus — der Weg für den Spielpfad und für B13 (FR-056)
- [X] T111 [US6] Die Charakterauswahl in `rpg-platform/src/main/java/rpg/platform/currency/CurrencyMenu.java` (T084c) zeigt je Charakter seinen Stand — für angemeldete über `balanceOf`, für abgemeldete über den Repository-Pfad aus T078 (Szenario 3)
- [X] T111a [US6] Das eigene Fenster öffnet ohne Sonderrecht (`rpg.currency.balance`, standardmässig für alle); ein **fremdes** verlangt `rpg.currency.admin` (Szenario 4)
- [X] T112 [US6] Jede Ablehnung liefert ihre Meldung über den `MessageKey`; kein Text steht im Code (FR-057)
- [X] T113 [P] [US6] `CurrencyMessageKeyResolutionTest` in `rpg-core/src/test/java/rpg/core/currency/` — hinter jedem Schlüssel aus `CurrencyMessageKeys.all()` steht ein Text in `messages.yml`, nach dem Muster von `AbilityMessageKeyResolutionTest`

**Checkpoint**: Alle sechs Stories sind für sich lauffähig.

---

## Phase 9: Polish & Querschnitt

**Zweck**: Die Zusagen, die über eine einzelne Story hinausgehen, und die Dokumentation.

### Invarianten und Verdrahtung

- [X] T114 [P] `CurrencySourceInvariantsTest` in `rpg-core/src/test/java/rpg/core/currency/` — kein `org.bukkit`, `io.papermc`, `net.minecraft` oder `org.spigotmc` in `rpg/core/currency/` (Prinzip III), nach dem Muster von `AbilitySourceInvariantsTest`
- [X] T115 [P] `NoBookingWithoutReasonTest` in `rpg-core/src/test/java/rpg/core/currency/` — **keine** Signatur in `Currency` oder `CurrencyAdmin` ändert einen Stand ohne Grund beziehungsweise Verursacher (SC-004)
- [X] T116 `NoDatabaseAccessPerGameEventTest` in `rpg-persistence/src/test/java/` um `CHARACTER_BALANCE` und `COIN_LEDGER` erweitern — beide Typen haben Enum-Wert, Platz in `WRITE_ORDER` und Repository (SC-005, FR-015)
- [X] T117 `FullBootstrapTest` in `rpg-plugin/src/test/java/` erweitern: beide Aggregattypen registriert, beide Repositories verdrahtet, beide Listener angemeldet, das Kommando registriert (ADR-012)
- [X] T118 [P] `CurrencyImmutabilityTest` in `rpg-core/src/test/java/rpg/core/currency/` — `CharacterBalance`, `LedgerEntry`, `CoinDropPlan` und `CostSpec` sind unveränderlich
- [X] T119 [P] `NoCoinPilePersistenceTest` in `rpg-platform/src/test/java/rpg/platform/currency/` — ein Haufen wird nirgends gespeichert und ist nach einem Neustart weg; beabsichtigt (FR-033, data-model.md §4)
- [X] T119a [P] `CurrencyConfigEffectTest` in `rpg-core/src/test/java/rpg/core/currency/` — eine geänderte `drops.default`, eine geänderte `rank-cost` und ein geänderter `cost`-Block wirken nach dem Neuladen der Konfiguration, **ohne dass Code geändert wurde** (SC-009, Prinzip V), nach dem Muster von `AbilityConfigReloadTest`

### Die beiden ADRs

- [X] T120 **ADR-028** in `02-decisions.md`: Kommando **und Fenster** in einem Schicht-1-Block. Angenommen am 2026-08-22, vor Beginn der Umsetzung — die Governance-Regel der Constitution verlangt für eine Abweichung von Prinzip III eine ausdrückliche, begründete Ausnahme, und die liegt vor
- [X] T121 **ADR-029** in `02-decisions.md`: die Herauslösung des Anteilsrechners aus `XpDistributor`. Verhaltensneutral, abgesichert durch T043; die Regel bleibt in B06s Paket, weil ein Besitzer nicht umzieht, nur weil ein zweiter Nutzer dazukommt
- [ ] T122 Die Entscheidung zu **research.md R8** einholen und festhalten: wird B08b lasttestpflichtig? Bei Ja eine MINOR-Änderung von Prinzip VII in `.specify/memory/constitution.md` und `constitution.md`, plus eine Abnahmebedingung mehr für diesen Block

### Dokumentation

- [X] T123 [P] `package-info.java` in `rpg/core/currency/` ausformulieren — der Vertrag aus [contracts/currency-api.md](./contracts/currency-api.md), die Zusage „ab jetzt ADR-pflichtig", und was ausdrücklich **nicht** hierher gehört
- [X] T124 [P] Steckbrief `minecraft-rpg-spec/minecraft-rpg-spec/blocks/B08b-currency-account.md` auf **Implementiert** setzen, mit Aufgabenzahl, Testzahl und den offen gebliebenen Punkten
- [X] T125 [P] Die vier offenen Fragen in `minecraft-rpg-spec/minecraft-rpg-spec/blocks/B08b-currency-account.md` schliessen — Startguthaben, Todesverlust, Mob-Ertrag, Sichtbarkeit — jeweils mit der getroffenen Antwort
- [X] T126 [P] `docs/05-roadmap-speckit-workflow.md`: „Empfohlener nächster Schritt" auf **B11** umstellen; B08b ist erledigt und hat B07 und B08 abgeschlossen
- [X] T127 [P] `docs/06-open-questions.md`: den B08b-Abschnitt schliessen
- [X] T128 [P] ADR-027 in `02-decisions.md` um eine Nachbemerkung ergänzen: die drei benannten Folgen sind nachgezogen, `RankResult` hat sein `NOT_ENOUGH_COINS`, B07s `cost`-Block wird gelesen
- [X] T129 [P] Die B08-Steckbriefe und `docs/00-vision-scope.md` dort nachziehen, wo sie „es gibt keine Währung" behaupten

### Abschluss

- [X] T130 `./gradlew test` vollständig — **0 Fehler, 0 übersprungen**. Auf übersprungene Tests achten: MockBukkit meldet Nicht-Implementiertes als *skipped*, nicht als Fehler
- [X] T131 [quickstart.md](./quickstart.md) Abschnitt 1 und 2 durchlaufen und die Ergebnisse festhalten
- [ ] T132 [quickstart.md](./quickstart.md) Abschnitt 3 auf einem echten Paper-Server durchlaufen — die zweiundzwanzig Prüfschritte, besonders 4 (Charakterwechsel), 12 (offline Eingriff), 14 (Eingriff überlebt Flush) und 17 (Blättern ohne doppelte Buchung). Grüne Tests beweisen nichts über Papers `libraries:`-Klassenlader; nur der echte Start tut das
- [X] T133 Abschnitt 4 (Last) als **offen** in `minecraft-rpg-spec/minecraft-rpg-spec/blocks/B08b-currency-account.md` festhalten — der Nachweis für SC-006 braucht B10s Horden und ist bis dahin nicht zu erbringen

---

## Dependencies & Execution Order

### Phasen

- **Phase 1 (Setup)**: keine Abhängigkeit, sofort startbar
- **Phase 2 (Foundational)**: nach Phase 1 — **blockiert alle Stories**
- **Phase 3–8 (Stories)**: alle nach Phase 2
- **Phase 9 (Polish)**: nach den gewünschten Stories

### Zwischen den Stories

- **US1 (P1)**: nach Phase 2, keine Abhängigkeit auf eine andere Story. **Der MVP.**
- **US2 (P2)**: braucht US1 — es gibt nichts zu buchen ohne Konto. Die Herauslösung T041–T044 ist davon unabhängig und kann vorgezogen werden
- **US3 (P3)**: braucht US1. Unabhängig von US2 — der Verlauf zeichnet jede Buchung auf, gleich woher sie kommt
- **US4 (P4)** und **US5 (P5)**: brauchen US1, sind untereinander unabhängig und können parallel laufen
- **US6 (P6)**: braucht US1; `/coins balance` braucht zusätzlich T081/T082 aus US3

### Innerhalb einer Story

Tests zuerst schreiben und **scheitern sehen**, dann Werte, dann Regel, dann Persistenz, dann
Plattform, zuletzt Verdrahtung.

### Parallelität

- Phase 1 vollständig parallel (T001–T006)
- Phase 2: T008–T012 parallel, danach T013–T016 in Folge
- In US1: die fünf Tests T017–T021 parallel; T025–T032 in Folge, weil sie aufeinander aufbauen
- In US2: T045–T047 parallel, die sechs Tests T058–T063 parallel
- In US3: die zehn Tests T085–T092b parallel. Das Fenster T084a–T084g läuft **in Folge** — Versatz, Konfiguration, Menü, Auswahl, Blättern, Klicks, Kommando bauen aufeinander auf
- **US4 und US5 vollständig parallel zueinander** — verschiedene Dateien, kein gemeinsamer Berührungspunkt
- In Phase 9: T114–T119 parallel, T123–T129 parallel

---

## Parallel Example: User Story 1

```bash
# Die fünf Tests der Story zusammen anlegen:
Task: "DefaultCurrencyTest in rpg-core/src/test/java/rpg/core/currency/"
Task: "BalanceNeverNegativeTest in rpg-core/src/test/java/rpg/core/currency/"
Task: "BookingAtomicityTest in rpg-core/src/test/java/rpg/core/currency/"
Task: "InvalidAmountTest in rpg-core/src/test/java/rpg/core/currency/"
Task: "StartingBalanceTest in rpg-core/src/test/java/rpg/core/currency/"
```

---

## Implementation Strategy

### MVP zuerst (nur US1)

1. Phase 1 (Setup)
2. Phase 2 (Foundational) — **blockiert alles**
3. Phase 3 (US1)
4. **Anhalten und prüfen**: Buchen, abmelden, neu starten, Stand lesen
5. Ab hier existiert eine Währung, auf die andere Blöcke bauen können — auch wenn noch niemand welche verdient

### Schrittweise

1. Setup + Foundational → Grundlage steht
2. US1 → **MVP**, das Konto trägt
3. US2 → Coins entstehen im Spiel
4. US3 → der Betrieb wird handlungsfähig
5. US4 + US5 → **die beiden ausgelieferten Blöcke sind abgeschlossen; das ist der Zweck des Blocks**
6. US6 → der Spieler sieht es

### Die riskanteste Reihenfolge zuerst erkennen

**T050 zieht sich durch die ganze Story 2.** Ob sich die Verfallsfrist je Entity setzen lässt,
entscheidet über eine Schemaregel und über einen Teil des Vertrags. Die Aufgabe steht deshalb **vor**
`CoinPile` und nicht nebenher.

**T041–T044 sind der einzige Eingriff in einen ausgelieferten Block.** Sie lassen sich vorziehen und
für sich abnehmen — grüne B06-Tests ohne eine einzige Anpassung sind die Abnahme. Wer sie erst mitten
in US2 anfasst, vermischt einen Refactor mit neuem Verhalten und kann beides nicht mehr trennen.

---

## Notes

- [P] heisst andere Datei und keine offene Abhängigkeit
- Jede Story ist für sich abschliessbar und prüfbar
- Tests scheitern sehen, bevor implementiert wird
- Nach jeder Aufgabe oder Gruppe committen
- An jedem Checkpoint kann angehalten und die Story für sich abgenommen werden
- **Ein Block ist erst fertig, wenn er im Plugin verdrahtet und `FullBootstrapTest` grün ist** —
  Modultests allein genügen nicht
