# Contract: Sitzungs-Lebenszyklus (`rpg-core` Regeln, `rpg-platform` Auslöser)

Bestimmt, **wann** eine Sitzung entsteht, bereit wird und verschwindet.

## Schnittstelle

```java
public interface SessionLifecycle {
    CompletableFuture<PlayerSession> beginLoad(UUID playerId, Duration timeout);
    void markReady(UUID playerId);
    CompletableFuture<Void> endSession(UUID playerId, SessionEndReason reason);
    void abandonLoad(UUID playerId);
}

public enum SessionEndReason { QUIT, KICK, TIMEOUT, SHUTDOWN, RECONCILED }
```

## Verhaltensverträge

### Laden

- `beginLoad` läuft **außerhalb des Server-Ticks** (FR-001) und wird im asynchronen
  Vorlade-Ereignis aufgerufen, also bevor ein Spielerobjekt existiert.
- Es lädt Account, Charaktere und Item-Instanzen in **einer** Datenbankrunde (FR-005), nicht über
  getrennte Aufrufe je Aggregat.
- Vor dem Lesen wird B02s vorhandenes Zurückstellen genutzt, bis ausstehende Schreibvorgänge einer
  Vorsitzung abgeschlossen sind (FR-013). **Diese Mechanik wird nicht nachgebaut.**
- Nach Ablauf von `timeout` (5 Sekunden, FR-006) wird das `Future` ausnahmsweise abgeschlossen und
  die Anmeldung abgewiesen. Die Frist ist eine Notbremse, keine erwartete Ladezeit (FR-006a) — der
  Zielwert bleibt 500 ms aus SC-001.
- Scheitert das Laden aus irgendeinem Grund, endet die Sitzung in `FAILED` und es wird
  **nichts geschrieben** (FR-011, FR-012).

### Bereitstellen

- `markReady` schaltet von `LOADING` auf `READY` und gibt den Spieler frei (FR-003).
- Vor `markReady` liefert die Registry für diesen Spieler nichts (FR-004).

### Beenden

- `endSession` wird bei **jedem** Sitzungsende aufgerufen — `QUIT`, `KICK` und `TIMEOUT` sind
  fachlich derselbe Fall (FR-007) und werden über einen einzigen Auslöser erfasst, damit kein
  Sitzungsende zweimal verarbeitet wird.
- Es stößt B02s vorhandenes sofortiges Schreiben an; es führt **keine** eigene Schreiblogik aus.
- Erst nach Abschluss des Schreibvorgangs wird die Sitzung entfernt (FR-008).
- `SHUTDOWN` beendet alle aktiven Sitzungen (FR-010); der eigentliche Flush geschieht durch B02s
  Modul-Shutdown, der ohnehin abläuft.
- `RECONCILED` ist der Weg, über den der Abgleich eine liegengebliebene Sitzung entfernt.

### Abbrechen

- `abandonLoad` verwirft einen laufenden Ladevorgang, wenn der Spieler die Verbindung vorher trennt
  (FR-015). Es entsteht **keine** Sitzung und **kein** Schreibvorgang — der Spieler hat nie einen
  Zustand erhalten, der geschrieben werden könnte.

### Eindeutigkeit

- Zu keinem Zeitpunkt existiert mehr als eine Sitzung je Spieler (FR-014). Ein zweiter
  `beginLoad` für einen Spieler mit bestehender Sitzung wird abgelehnt, nicht überschrieben.

## Nicht Teil dieses Contracts

- Das Write-Behind, das Autosave-Intervall und der Shutdown-Flush — die gehören B02 und laufen
  unverändert.
- Die Auswahl des aktiven Charakters — die trifft der Aufrufer beim Verbinden, und sie ist danach
  unveränderlich (FR-021a/FR-021b).
