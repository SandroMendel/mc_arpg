# Phase 0 · Recherche — B12 · Statistiken & Leaderboards

**Datum**: 2026-08-29 · **Spec**: [spec.md](./spec.md) · **Entscheidungen**: ADR-040 bis ADR-047

Zehn Fragen, die vor dem Entwurf im **gebauten Code** zu beantworten waren. Zwei davon hat die Spec
ausdrücklich hierher überwiesen (R1, R2); die übrigen sind beim Nachsehen entstanden.

---

## R1 — Brauchen zweiundzwanzig Ranglisten zweiundzwanzig Materialized Views?

**Entscheidung: nein. Vier Sichten, eine je Zeitraum, jede über alle Metriken.**

`rpg.player_statistic_daily` trägt `(player_id, metric, day, value)`. Eine Rangliste ist eine
Aggregation über *einen* Zeitraum, gefiltert auf *eine* Metrik. Die Metrik gehört also nicht in die
Sicht, sondern in die Gruppierung:

```
mv_stat_alltime (metric, player_id, value)
mv_stat_season  (season_key, metric, player_id, value)
mv_stat_week    (iso_year, iso_week, metric, player_id, value)
mv_stat_day     (day, metric, player_id, value)
```

Die Metrik ist eine **Spalte**, kein Sichtname. Damit wächst die Zahl der Sichten nicht mit der Zahl
der Metriken — was FR-032a überhaupt erst tragbar macht: eine neue Metrik erscheint von allein auf
dem Brett, ohne dass jemand eine Sicht anlegt.

**Die Aggregation je Familie passiert in derselben Sicht.** Kills liegen als
`mob_kills.<art>` vor; die Rangliste rankt die Summe über die Familie. Das ist ein
`sum(value) ... GROUP BY split_part(metric, '.', 1)` in der Sichtdefinition, kein zweiter Bestand
— FR-013 verlangt genau das.

**Größenordnung.** Die Sicht hält Metrik × Spieler. Bei 200 Spielern und selbst 60 verschiedenen
Metrikschlüsseln sind das 12 000 Zeilen für die Allzeit-Sicht; Tag und Woche sind kleiner, weil nur
zählt, wer gespielt hat. Das lädt der Cache in **einer** Abfrage je Sicht vollständig in den
Speicher und sortiert dort. Vier Abfragen alle fünf Minuten — nicht zweiundzwanzig, und beim
Öffnen einer Ansicht keine einzige (FR-030).

**Verworfen.**
- **Eine Sicht je Rangliste**: zweiundzwanzig `REFRESH`-Aufrufe alle fünf Minuten und eine
  Migration für jede neue Metrik. Es wäre die kuratierte Liste, die FR-032a gerade vermeidet.
- **Gar keine Sicht, nur eine Abfrage beim Refresh**: möglich, aber die Aggregation über alle Tage
  läuft dann in jeder Auffrischung neu über die ganze Tabelle. Die Materialized View ist die
  Vorgabe des Blockdokuments und hält den Aufwand dort, wo er einmal anfällt.
- **`REFRESH MATERIALIZED VIEW` ohne `CONCURRENTLY`**: sperrt die Sicht für die Dauer des
  Refreshs. Da nur der Cache-Auffüller liest, wäre das verkraftbar — aber `CONCURRENTLY` kostet nur
  einen eindeutigen Index und nimmt die Frage ganz vom Tisch.

---

## R2 — Wo liegen Saisonendstand und Belohnungsanspruch, und schreibt sie das Write-Behind?

**Entscheidung: zwei neue Tabellen, geschrieben *ohne* Write-Behind — direkt und asynchron.**

Der Write-Behind-Weg aus B02 löst ein Problem, das hier nicht besteht: **viele Änderungen je
Sekunde zu einem Schreibvorgang bündeln**. Ein Saisonendstand entsteht **viermal im Jahr**, ein
Belohnungsanspruch ebenso oft je Gewinner. Ein Aggregattyp dafür bedeutete drei Registrierungen
(ADR-015: `AggregateType`, `FlushCycle.WRITE_ORDER`, Repository), eine Revisionsspalte und einen
`BatchWriter` — für einen Vorgang, der pro Quartal einmal läuft.

**Beide Tabellen bekommen trotzdem alles, was B02 verlangt**: versionierte Migration,
`data_version`, `updated_at`, und der Zugriff bleibt in `rpg-persistence`. Das ist keine Umgehung
der Kapselung, sondern nur der Verzicht auf die Bündelung.

```
V12_1__season_result.sql        (season_key, metric_weights, player_id, rank, score) — eingefroren
V12_2__season_reward_claim.sql  (season_key, player_id, reward, claimed_at NULL)     — Anspruch
```

**Der Anspruch trägt seinen eigenen Riegel.** FR-053 verlangt „genau einmal, auch bei einem
Absturz zwischen Gutschrift und Vermerk". Der Vermerk ist deshalb **Teil derselben Transaktion**
wie die Coin-Buchung nicht — die Coins gehen über B08b und dessen Bestand. Stattdessen wird
**zuerst** der Anspruch als eingelöst markiert (`claimed_at` mit Bedingung `claimed_at IS NULL`)
und **erst bei erfolgreicher Markierung** gutgeschrieben. Ein Absturz dazwischen kostet im
schlimmsten Fall eine Belohnung, verdoppelt aber keine — die Richtung, in die ein solcher Fehler
fallen muss.

**`NoDirectDatabaseAccessTest` ist zu beachten** (T061 aus B02): `java.sql`, `javax.sql`,
`DataSource` und `DriverManager` sind außerhalb von `rpg-persistence` verboten, und ein `DELETE`
gegen `player_statistic_daily` ist überall verboten. Beide Regeln hält dieser Entwurf ein; die
zweite ist zugleich der mechanische Beleg für ADR-044.

---

## R3 — Wie kommt B12 vom Charakter zum Konto?

**Fund: `stats.holderOf(characterId)` gibt es bereits, und B08 benutzt es an neun Stellen.**

Die Statistik ist mit `player_id` verschlüsselt (Fund 3 der Spec), die Ereignisse der Spielschicht
tragen aber Charakter-Kennungen: `CombatDeathEvent.victimCharacterId`, `ZoneChangedEvent.characterId`.
`AbilityRuntime` löst dasselbe Problem seit B08 über `holderOf(characterId)` aus der Statsschicht.

B12 benutzt denselben Weg und legt **keine eigene Zuordnung** an. Das ist wichtiger, als es klingt:
die Verwechslung von Halter- und Charakterkennung ist in diesem Projekt schon einmal durch 1614
Tests hindurchgerutscht, weil ein Testdouble für beide dieselbe UUID lieferte. Die Testdoubles
dieses Blocks müssen deshalb **unterschiedliche** Kennungen liefern.

---

## R4 — Woran erkennt B12 die Mob-Art eines Verursachers?

**Fund: die Art steht im PDC der Entität, gesetzt von B10 — und sie ist nur im Tick lesbar.**

`MobKind.key` ist ausdrücklich *„der Wert, der im Vermerk der Entitaet landet"*. `VendorNpc` zeigt
dasselbe Muster für seinen Zonenschlüssel (`entity.getPersistentDataContainer().set(ZONE, ...)`).

Daraus folgt FR-012 wörtlich: **die Auflösung passiert im Tick, im selben Moment**, in dem das
Todesereignis kommt. Ein asynchrones Nachschlagen ginge ins Leere, weil die Entität dann fort ist —
und ADR-035 hält bereits fest, dass entitätsgebundene Planung aus Async-Kontext still scheitert.

Für Bosse braucht es **keine** zweite Abfrage: das Boss-Kennzeichen hängt an der Art
(`MobKind.boss`), nicht an der Entität. Bosskills sind damit eine Auswertung über die Konfiguration,
kein eigener Zähler (FR-009).

---

## R5 — Woher kommt der Schadensanteil für die Beteiligungsschwelle?

**Fund: `DamageShare.shareOf(attackerId)` liefert genau das, und `shares()` nennt alle Beitragenden.**

`CombatDeathEvent` trägt die vollständige Aufteilung als `DamageShare(Map<UUID, Double> shares,
UUID topContributor, double totalDamage)`. Die Schwelle aus FR-007 prüft gegen `shareOf`, ohne dass
irgendetwas nachgerechnet oder mitgeschrieben werden müsste.

**Es entsteht also keine zweite Rechnung darüber, wer wie viel beigetragen hat** — genau die
Begründung, die ADR-042 im Nachtrag nennt. `topContributor` wird von B12 **nicht mehr** benutzt;
er bleibt B11s Antwort für die Beute.

---

## R6 — Wo wird die aktive Zeit festgeschrieben, ohne eine neue Aufgabe zu schaffen?

**Fund: der Inventar-Sweep aus B03/B11 läuft bereits im Autosave-Takt und über genau die richtige
Spielerliste.**

`RpgPlugin.startInventorySweep` läuft im Takt von `INVENTORY_SWEEP` — *„Same cadence as B02's
autosave, and for the same reason: a crash should cost one interval"* — und geht über
`inventoryModule.playersInPlay()`, ausdrücklich **nicht** über `getServer().getOnlinePlayers()`:
wer noch in der Charakterauswahl sitzt, hat nichts festzuschreiben.

Das ist exakt die Liste und exakt der Takt, den FR-015 verlangt. Die Zeitfortschreibung reitet auf
diesem Sweep mit und legt **keine eigene wiederkehrende Aufgabe** an — dasselbe Vorgehen, mit dem
B11 `ConsumableBuffs.expire()` auf den vorhandenen Fähigkeiten-Sweep gesetzt hat
(*„Riding this sweep adds no task"*).

**Zusätzliche Abschlusspunkte** ohne eigenen Takt: Sitzungsende, Zonenwechsel, Tagesgrenze.

---

## R7 — Was kostet der Aktivitätszeitstempel im Tick?

**Entscheidung: ein eigener `PlayerMoveEvent`-Handler auf `MONITOR`, der genau einen Wert setzt.**

Der Codebestand ist hier ungewöhnlich gut dokumentiert. `ZoneMovementListener` schreibt:
*„`PlayerMoveEvent` is one of the busiest events a server has — `DoubleJumpListener` says the same
thing for the same reason — so everything before the guard has to be free."* Vier Handler hängen
bereits an diesem Ereignis (Zonen, Doppelsprung, Landung, Zauberunterbrechung).

Ein fünfter ist vertretbar, **weil er weniger tut als jeder vorhandene**: eine Zuweisung in eine
vorbelegte Map, kein Nachschlagen, keine Allokation, keine Bedingung außer der Registrierung
selbst. Er läuft auf `MONITOR`, weil er nichts entscheidet.

**Verworfen.**
- **`ZoneMovementListener` einen Rückruf mitgeben**: hätte B09 an B12 gekoppelt, ohne Arbeit zu
  sparen — der Rückruf kostet denselben Aufruf.
- **Bewegung nicht als Aktivität werten**: widerspricht FR-014c1 und hätte Erkunden als Leerlauf
  gezählt.

**Zu beachten:** `FullBootstrapTest` zählt die Handler von `PlayerMoveEvent`. Die Zahl muss mit
dieser Änderung steigen — und die Lehre von `ZoneMovementListener` gilt weiter:
`PlayerTeleportEvent` hat eine **eigene** Handlerliste. Für die Aktivität ist ein Teleport
allerdings ohnehin durch die Aktion abgedeckt, die ihn ausgelöst hat.

---

## R8 — Wie wird das Hologramm gesetzt, ohne sich zu verdoppeln oder das Mob-Budget zu berühren?

**Fund: `VendorNpc` aus B11 löst dieselben vier Probleme und ist die Vorlage.**

- **Budget**: *„er wird nie in `HordeRegistry` eingetragen und zählt deshalb nicht gegen das
  Budget."* Kein Sonderfall im Budget nötig — es reicht, die Anzeige dort nicht einzutragen
  (FR-062).
- **Verdopplung**: vor dem Setzen wird im Umkreis entfernt (`nearby.remove()`), dann gesetzt
  (FR-061).
- **Härtung**: jede Einstellung einzeln in `apply("name", () -> ...)`, weil ein Testdouble, das
  eine Methode nicht kennt, sonst den **ganzen** Spawn scheitern lässt. Genau daran ist B11 einmal
  aufgelaufen.
- **Zonenmerkmal im PDC**, damit die Anzeige beim Start wiedererkannt wird.

Für die Anzeige selbst gilt zusätzlich FR-063: unverwundbar, unbeweglich, kein Aggro-Ziel. Ob das
über eine Anzeige-Entität oder ein unsichtbares Trägerobjekt geschieht, ist eine Frage der
Umsetzung; die Anforderungen sind es nicht.

---

## R9 — Wie sieht die Startprüfung der Konfiguration aus?

**Fund: B10 und B11 haben das Muster, und B11 hat es sogar für *schlüsselabhängige* Meldungen.**

`MobConfigSchema` und `ItemConfigSchema` prüfen beim Start gegen ein Schema und scheitern mit
Datei, Schlüssel und Grund. `ItemMessageKeys` führt zusätzlich eine Liste der benötigten
Nachrichtenschlüssel, und B11 hat sie um **slot-abhängige** Schlüssel erweitert, die erst zur
Laufzeit entstehen.

B12 braucht beides: ein Schema für `statistics.yml` (Metrikverzeichnis, Zeiträume, Saisons,
Gewichte, Belohnungen, Hologramm) und eine Schlüsselprüfung für die Fenstertexte. Neu ist eine
dritte Prüfung, die kein Nachbarblock hat: **die Gewichtung muss mindestens eine bekannte Metrik
nennen** (FR-050d) und die Saisons müssen lückenlos und überschneidungsfrei sein (FR-049).

---

## R10 — Was ist mit `AggregateType` und der Reihenfolge im Flush?

**Fund: `STATISTICS` steht bereits in `FlushCycle.WRITE_ORDER`, direkt vor `AUDIT_LOG`.**

Die dreifache Registrierung aus ADR-015 ist für die Statistik **vollständig erledigt**: der
Aggregattyp existiert, er steht in der Schreibreihenfolge, und `PersistenceModule` verdrahtet
`JdbcStatisticsRepository` und meldet es als Dienst an die Registry.

**Für B12 heißt das: kein neuer Aggregattyp.** Der Maximum-Schreibweg aus ADR-040 kommt in
denselben `BatchWriter` — er schreibt in dieselbe Tabelle, mit demselben Schlüssel, unter derselben
Markierung. Nur das SQL unterscheidet sich, und welches gilt, entscheidet die Metrikart aus dem
Verzeichnis.

Die beiden Saisontabellen aus R2 bekommen aus demselben Grund **keinen** Aggregattyp.
