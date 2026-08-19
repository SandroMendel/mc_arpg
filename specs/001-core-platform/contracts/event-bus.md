# Contract: EventBus (`rpg-core`)

Interner Publish/Subscribe-Mechanismus zwischen Modulen, ohne dass Herausgeber und
Abonnent sich kennen müssen.

## Schnittstelle (Signaturvertrag, kein Implementierungscode)

```java
public interface EventBus {
    <E> void publish(E event);
    <E> Subscription subscribe(Class<E> eventType, EventHandler<E> handler);
}

public interface EventHandler<E> {
    void handle(E event);
}

public interface Subscription extends AutoCloseable {
    void close(); // deregistriert den Handler
}
```

## Verhaltensverträge

- `publish` MUSS alle registrierten Abonnenten des exakten Ereignistyps synchron im
  Aufruferkontext benachrichtigen (siehe research.md, Event-Bus-Dispatchstrategie).
- Wirft `EventHandler.handle` eine unerwartete `RuntimeException`, MUSS `publish` diese
  isoliert protokollieren (inkl. Ereignistyp und Abonnent) und die Zustellung an die
  übrigen Abonnenten desselben Ereignisses fortsetzen (FR-006a) — `publish` selbst darf
  dadurch nicht fehlschlagen.
- `subscribe` liefert ein `Subscription`-Handle; `close()` MUSS den Handler zuverlässig
  entfernen (relevant für den Shutdown-Pfad, verhindert Aufrufe auf bereits gestoppte
  Module).
- Die Reihenfolge, in der mehrere Abonnenten desselben Ereignistyps benachrichtigt werden,
  ist NICHT Teil dieses Contracts (keine Anforderung in `spec.md` verlangt eine bestimmte
  Reihenfolge).

## Nicht Teil dieses Contracts

- Eigene Nebenläufigkeit/Threading innerhalb des Event-Bus (siehe research.md — der
  Publisher entscheidet über Sync/Async via Scheduler-Abstraktion, nicht der Bus selbst).
