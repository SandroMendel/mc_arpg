# Contract: Repository-Schnittstellen (`rpg-core`, Implementierung in `rpg-persistence`)

Die einzige Art, wie B03–B17 an dauerhafte Daten kommen. Kein Block kennt Tabellen, SQL oder den
Verbindungspool.

## Grundmuster

```java
public interface Repository<ID, T> {
    CompletableFuture<Optional<T>> load(ID id);
    void markDirty(ID id);
}
```

## Verhaltensverträge

- **`load` ist immer asynchron.** Es gibt bewusst keine synchrone Variante — sie wäre die
  bequemste Möglichkeit, Constitution I.1 zu verletzen, und darf deshalb gar nicht erst existieren.
  Der Rückgabetyp macht das im Typsystem sichtbar, nicht per Konvention.
- **`markDirty` schreibt nicht.** Es vermerkt ausschließlich, dass das Aggregat beim nächsten
  gesammelten Schreibvorgang zu berücksichtigen ist (FR-002). Der Aufruf ist im Tick zulässig und
  MUSS ohne Datenbankzugriff, ohne Sperre und ohne nennenswerte Allokation auskommen.
- **Mehrfaches `markDirty` für dieselbe Kennung erzeugt genau eine Vormerkung** (Koaleszieren,
  siehe `data-model.md`). Tausend Änderungen in einem Tick kosten dadurch genau einen
  Schreibvorgang.
- Es gibt **keine** `save`-Methode in der öffentlichen Schnittstelle. Wann geschrieben wird,
  entscheidet der Flush-Zyklus nach FR-003/FR-004/FR-011 — nicht der aufrufende Block. Andernfalls
  könnte jeder Block das Write-Behind-Verhalten umgehen und damit SC-005 aushebeln.
- `load` liefert `Optional.empty()` für einen unbekannten Schlüssel; das ist kein Fehler, sondern
  der Normalfall beim ersten Verbinden eines neuen Spielers.
- Schlägt `load` fehl (Datenbank nicht erreichbar, Datensatz nicht lesbar), wird das `Future`
  ausnahmsweise abgeschlossen. Der Aufrufer im Anmeldepfad MUSS daraus eine Ablehnung machen
  (FR-005a) und DARF niemals einen Standardzustand einsetzen.

## Aggregatspezifische Ergänzungen

```java
public interface PlayerStateRepository extends Repository<UUID, PlayerState> {
    CompletableFuture<Void> awaitPendingWrites(UUID playerId, Duration timeout);
    CompletableFuture<Void> anonymize(UUID playerId);
}

public interface StatisticsRepository {
    void increment(UUID playerId, String metric, long delta);
    CompletableFuture<Long> sum(UUID playerId, String metric, LocalDate from, LocalDate to);
}

public interface AuditLogRepository {
    void append(AuditEntry entry);
}
```

- `awaitPendingWrites` bedient FR-019a: Der Anmeldepfad wartet damit auf den abgeschlossenen Flush
  einer Vorsitzung. Läuft `timeout` ab, wird das `Future` ausnahmsweise abgeschlossen und die
  Anmeldung nach FR-019c abgelehnt.
- `anonymize` bedient FR-017a bis FR-017c in **einer** Transaktion: Spielerzustand löschen,
  Kennungen in Statistik und Prüfprotokoll durch das Ersatzkennzeichen ersetzen, Vorgang selbst
  protokollieren. Eine teilweise durchgeführte Anonymisierung wäre schlimmer als gar keine.
- `increment` ist bewusst ein Delta, kein Setzen: Nur so lässt sich der Wert per
  `ON CONFLICT DO UPDATE SET value = value + excluded.value` schreiben, ohne vorher zu lesen
  (FR-007).
- `append` erzeugt immer einen neuen Eintrag; das Prüfprotokoll kennt kein Ändern und kein Löschen.

## Nicht Teil dieses Contracts

- Die fachlichen Felder der Aggregate (Level, Klasse, konkrete Kennzahlen) — die gehören den
  Blöcken, die das jeweilige Aggregat besitzen.
- Beliebige Abfragen über mehrere Aggregate hinweg. Braucht ein Block eine Auswertung, bekommt sie
  eine benannte Methode auf dem zuständigen Repository — keine offene Abfrageschnittstelle, die
  die Tabellenstruktur nach außen sichtbar machen würde.
