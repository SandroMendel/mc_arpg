# Contract: Scheduler-Abstraktion (`rpg-core` Interfaces, `rpg-platform` Implementierung)

Ersetzt jeden direkten Zugriff auf den globalen Bukkit-Scheduler (ADR-007). Die
öffentliche Schnittstelle bietet **ausschließlich** location- oder entity-gebundene
Terminierung an.

## Schnittstelle (Signaturvertrag, kein Implementierungscode)

```java
public interface Scheduler {
    TaskHandle runSyncAtLocation(Location location, Runnable task);
    TaskHandle runSyncOnEntity(Entity entity, Runnable task);
    TaskHandle runAsync(Runnable task); // async ist absichtlich nicht ortsgebunden
}

public interface TaskHandle {
    void cancel();
    boolean isCancelled();
}
```

## Verhaltensverträge

- Es MUSS keine Methode existieren, die eine synchrone Aufgabe ohne `Location`- oder
  `Entity`-Bindung entgegennimmt (FR-008) — das ist eine harte Typsystem-Grenze, kein
  Laufzeit-Check.
- `runSyncAtLocation`/`runSyncOnEntity` MÜSSEN die Aufgabe im Server-Tick-Kontext
  ausführen; `runAsync` MUSS sie außerhalb des Server-Tick-Threads ausführen. Diese
  Unterscheidung ist bereits am Rückgabe-/Parametertyp erkennbar (FR-007), nicht nur per
  Namenskonvention.
- `TaskHandle.cancel()` MUSS eine noch nicht ausgeführte Aufgabe zuverlässig verhindern;
  wiederholtes `cancel()` ist ein No-Op.
- Die konkrete Implementierung in `rpg-platform` bindet `runSyncAtLocation`/
  `runSyncOnEntity` intern an die Paper-API (z. B. Region-/Entity-Scheduler), bleibt aber
  hinter diesem Contract verborgen — `rpg-core` kennt keine Paper-Typen.

## Nicht Teil dieses Contracts

- Wiederkehrende (periodische) Aufgaben — laut Constitution II.2 werden zeitbasierte Werte
  zeitstempelbasiert lazy ausgewertet statt über periodisches Scheduling; ein
  `runRepeating(...)` gehört bewusst nicht in diesen Contract.
