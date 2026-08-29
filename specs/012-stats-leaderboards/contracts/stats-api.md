# Vertrag · Was B12 nach außen anbietet

**Spec**: [../spec.md](../spec.md) · **Datenmodell**: [../data-model.md](../data-model.md)

B12 ist ein Block der dritten Schicht: er wird von **B13** (Anzeige) und **B14** (Commands, Admin)
gebraucht. Was hier steht, ist die Naht, auf die sich diese beiden verlassen dürfen — und alles
andere ist Interna (Prinzip III).

---

## 1 · Zählen (`rpg.core.statistics`)

```java
public interface Statistics {

    /** Erhöht einen Zähler. Aus dem Tick aufrufbar, kein Datenbankzugriff. */
    void count(UUID playerId, Metric metric, long delta);

    /** Meldet einen Wert für eine Maximum-Metrik. Kein Lesen vor dem Schreiben (ADR-040). */
    void reportMax(UUID playerId, Metric metric, long value);
}
```

**Zusicherungen.**

- Beide Methoden sind **im Tick sicher** und kosten dort nichts als einen Zugriff auf eine Map.
- Beide sind **fehlertolerant**: eine Ausnahme darf das auslösende Spielereignis nicht scheitern
  lassen (FR-004). Ein nicht gezählter Kill ist hinnehmbar, ein verlorener Kill nicht.
- `metric` kommt aus dem `MetricRegistry`. **Ein Metrikschlüssel als Literal an der Aufrufstelle
  ist ein Vertragsbruch** (FR-018) — die Art (SUM/MAX/STATE) hängt am Verzeichniseintrag, und ein
  Literal umgeht sie.
- `count` auf einer MAX-Metrik und `reportMax` auf einer SUM-Metrik sind Programmierfehler und
  werden abgewiesen, nicht stillschweigend umgedeutet.

---

## 2 · Lesen (`rpg.core.statistics`)

```java
public interface StatisticsView {

    /** Ein Wert eines Kontos für eine Metrik in einem Zeitraum. */
    CompletableFuture<Long> value(UUID playerId, Metric metric, Period period);

    /** Die Aufschlüsselung einer dimensionierten Metrik — nur für den Betrachter selbst. */
    CompletableFuture<Map<String, Long>> breakdown(UUID playerId, Metric family, Period period);
}
```

**Ein Zeitraum ist nicht für jede Metrik zulässig.** Zustandswerte (Level, XP, Coins) haben keine
Tages-, Wochen- oder Saisonform (FR-023). `value` mit einer Zustandsmetrik und einem anderen
Zeitraum als `ALL_TIME` wird **abgewiesen**, nicht auf den aktuellen Stand umgedeutet — und eine
Ansicht darf für sie erst gar keinen Zeitraum anbieten.

**`breakdown` ist die einzige Stelle, an der ein privater Wert das Modul verlässt.** Der Aufrufer
muss belegen, dass der Betrachter das Konto selbst ist; B13 und B14 bekommen dafür **keine**
Umgehung (FR-037). Ein Admin-Weg ist ausdrücklich nicht Teil dieses Vertrags.

---

## 3 · Ranglisten (`rpg.core.statistics`)

```java
public interface Leaderboards {

    /** Der aktuelle Stand aus dem Speicher. Löst NIEMALS eine Datenbankabfrage aus (FR-030). */
    Optional<Leaderboard> board(Aggregation board, Period period, String periodKey);

    /** Der Rang eines Kontos, auch außerhalb der ersten N (FR-033). */
    OptionalInt rankOf(Aggregation board, Period period, UUID playerId);

    /** Wann zuletzt aufgefrischt wurde — jede Ansicht muss das Alter nennen (FR-032). */
    Optional<Instant> refreshedAt();
}
```

**Zusicherungen.**

- `board` ist **synchron und tickfrei**: der Wert steht im Speicher. Ein leeres `Optional`
  bedeutet „noch nie aufgefrischt" und ist von der Ansicht als Meldung darzustellen, nicht als
  leere Liste (FR-035).
- Ein **anonymisiertes Konto** ist in keinem zurückgegebenen `Leaderboard` enthalten (FR-039).
- Gleichstand: gleicher Rang, **stabile** Reihenfolge über wiederholte Aufrufe (FR-034).

---

## 4 · Saison (`rpg.core.statistics`)

```java
public interface Seasons {

    /** Die laufende Saison, falls eine läuft. */
    Optional<Season> current(Instant now);

    /** Der Zwischenstand der Gesamtwertung, aufgeschlüsselt nach Metrik (FR-050e, FR-050f). */
    Optional<ScoreBreakdown> scoreOf(UUID playerId);

    /** Offene Ansprüche eines Kontos. Verfallen nie (FR-053a). */
    CompletableFuture<List<RewardClaim>> openClaims(UUID playerId);

    /**
     * Löst einen Anspruch ein. Markiert zuerst, schreibt dann gut — ein Absturz dazwischen
     * verliert höchstens, verdoppelt nie (FR-053).
     */
    CompletableFuture<ClaimOutcome> claim(UUID playerId, UUID characterId, String seasonKey);
}
```

`ClaimOutcome` unterscheidet **eingelöst**, **kein Anspruch**, **bereits eingelöst** und
**Inventar voll** — Letzteres lässt den Anspruch offen (FR-055).

---

## 5 · Was B12 *nicht* anbietet

- **Kein Schreibweg für Zustandswerte.** Level, XP und Coins gehören B06 und B08b; B12 liest sie
  und spiegelt sie nicht (ADR-041).
- **Kein Weg, einen Statistikwert zu setzen, zu korrigieren oder zurückzusetzen.** Administrative
  Eingriffe sind B14 (FR-069), und sie brauchen einen eigenen, geprüften Weg — nicht diesen.
- **Kein Zugriff auf fremde private Werte**, für keinen Aufrufer (FR-037).
- **Kein HUD, kein Scoreboard, keine dauerhafte Einblendung.** Das ist B13 (FR-068). Das Hologramm
  aus FR-059 ist eine Weltanzeige dieses Blocks, keine Spieler-Einblendung.

---

## 6 · Ereignisse, auf die B12 hört

| Ereignis | Quelle | Wofür |
|---|---|---|
| `CombatDeathEvent` | B05 | Kills je Art, Bosskills, Tode je Verursacher |
| `DamageDealtEvent` | B05 | höchster Schaden, Klonschaden eingeschlossen (FR-016a) |
| `ZoneChangedEvent` | B09 | Zeitabschnitt wechseln |
| `PlayerMoveEvent` u. a. | Paper | Aktivitätszeitstempel (R7) |
| Sitzungsende | B03 | letzten Abschnitt schließen |

**B12 hört zu und ruft nicht zurück.** Kein Nachbarblock bekommt eine Abhängigkeit auf B12, und
kein Block wird für B12 geändert.
