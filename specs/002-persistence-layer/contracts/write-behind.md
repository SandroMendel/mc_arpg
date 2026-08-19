# Contract: Write-Behind-Zyklus (`rpg-core` Regeln, `rpg-persistence` Ausführung)

Bestimmt, **wann** geschrieben wird. Die Repositories bestimmen nur, **was**.

## Schnittstelle

```java
public interface WriteBehindCoordinator {
    void markDirty(AggregateType type, String aggregateId);
    CompletableFuture<FlushResult> flushNow(FlushReason reason);
    BufferStatus bufferStatus();
}

public enum FlushReason { INTERVAL, SESSION_END, SHUTDOWN, RECOVERY }

public record FlushResult(int written, int failed, Duration took) {}

public record BufferStatus(int pending, int capacity, boolean overCapacity, boolean warning) {}
```

## Verhaltensverträge

### Auslöser

- **`INTERVAL`**: alle 45 Sekunden (konfigurierbar 30–60 s, FR-003). Der Zyklus plant sich nach
  jedem Durchlauf über `Scheduler.runAsyncDelayed` selbst neu — es gibt **keine** wiederkehrende
  Aufgabe je Spieler (Constitution II.2) und keinen eigenen Thread-Pool in B02.
- **`SESSION_END`**: sobald ein Spieler den Server verlässt, für dessen Aggregate (FR-004).
- **`SHUTDOWN`**: einmalig beim Herunterfahren, für alles Ausstehende. Darf blockieren, MUSS nach
  **8 Sekunden** abbrechen (FR-011/FR-011a). Der Abbruch protokolliert Zahl und Zuordnung der
  nicht geschriebenen Änderungen — stilles Verschlucken ist unzulässig.
- **`RECOVERY`**: nach einem überstandenen Ausfall, sobald die Datenhaltung wieder erreichbar ist
  (FR-010).

### Ablauf eines Flush

1. Die aktuell offenen Vormerkungen werden **als Momentaufnahme** entnommen.
2. Je Aggregattyp wird ein Batch mit Prepared Statements und `ON CONFLICT` geschrieben.
3. Nur die Vormerkungen aus der Momentaufnahme werden entfernt — und nur die erfolgreich
   geschriebenen.

Schritt 1 und 3 zusammen lösen den Edge Case „Aggregat wird während des laufenden
Batch-Schreibvorgangs erneut verändert": Die neue Änderung legt eine neue Vormerkung an, die der
laufende Flush nicht kennt und daher auch nicht entfernt. Sie geht damit nicht verloren, sondern
wird beim nächsten Durchlauf geschrieben.

### Fehlverhalten und Ausfall

- Schlägt ein Batch fehl, bleiben **alle** seine Vormerkungen bestehen; der Ausfallzustand wird
  gesetzt (FR-009). Kein stilles Verwerfen.
- `flushNow` schlägt **nie** nach außen fehl. Ein Fehler erscheint im `FlushResult` und im Log;
  der Aufrufer — insbesondere der Zyklus selbst — darf dadurch nicht abbrechen, sonst stünde nach
  dem ersten Ausfall die gesamte Persistenz still.
- Erreicht `pending` die Kapazität, MUSS `overCapacity` gesetzt werden und FR-009b greifen: alle
  Spieler trennen, neue Sitzungen ablehnen. Vormerkungen bleiben erhalten.
- Ab 80 % der Kapazität MUSS `warning` gesetzt und einmalig gewarnt werden (FR-009c) — einmalig,
  nicht bei jedem Durchlauf, sonst ertrinkt das Log genau dann, wenn es gebraucht wird.

### Nebenläufigkeit

- `markDirty` ist aus dem Tick aufrufbar und MUSS ohne Blockade auskommen.
- Es läuft zu keinem Zeitpunkt mehr als **ein** Flush gleichzeitig. Ein `flushNow` während eines
  laufenden Flush liefert das `Future` des laufenden zurück, statt einen zweiten zu starten.
- Kein Flush läuft im Tick-Thread — Ausnahme ist ausschließlich `SHUTDOWN`, und auch der liegt
  außerhalb des Ticks (Constitution I.2).

## Nicht Teil dieses Contracts

- Wie ein einzelnes Aggregat auf Spalten abgebildet wird — das gehört zum jeweiligen Repository.
- Die Reihenfolge, in der Aggregattypen geschrieben werden. Sie ist nicht zugesichert, weil kein
  Aggregat auf einem anderen aufbaut.
