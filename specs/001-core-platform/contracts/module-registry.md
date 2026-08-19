# Contract: ModuleRegistry (`rpg-core`)

Interne Schnittstelle, über die Module (B02–B17) Dienste registrieren und beziehen. Kein
Bukkit-Bezug, vollständig ohne laufenden Server nutzbar/testbar.

## Schnittstelle (Signaturvertrag, kein Implementierungscode)

```java
public interface ModuleRegistry {
    <T> void registerService(String moduleId, Class<T> serviceInterface, T implementation);
    <T> T getService(Class<T> serviceInterface);
    <T> Optional<T> findService(Class<T> serviceInterface);
    void registerModule(String moduleId, List<String> dependencyModuleIds);
    List<String> resolveStartOrder(); // wirft CyclicDependencyException bei Zyklus
}
```

## Verhaltensverträge

- `registerModule` mit einer bereits vergebenen `moduleId` MUSS eine
  `DuplicateModuleIdException` werfen (nicht still überschreiben) — siehe Edge Case
  „doppelter String-Bezeichner" in `spec.md`.
- `resolveStartOrder()` MUSS eine deterministische, aus den deklarierten Abhängigkeiten
  abgeleitete Reihenfolge liefern (FR-001) und bei einem Zyklus mit
  `CyclicDependencyException` abbrechen, deren Meldung die beteiligten Modul-Bezeichner
  benennt (FR-011).
- `getService` MUSS eine `ServiceNotRegisteredException` werfen, wenn kein Modul die
  angeforderte Schnittstelle bereitstellt — kein stiller `null`-Rückgabewert.
- `findService` liefert `Optional.empty()` für optionale Abhängigkeiten, ohne eine
  Ausnahme zu werfen.
- Alle Methoden dieser Schnittstelle sind threadsicher, dürfen aber nur während bzw. nach
  abgeschlossenem Bootstrap sinnvoll aufgerufen werden.

## Nicht Teil dieses Contracts

- Deregistrierung einzelner Dienste zur Laufzeit außerhalb des Shutdown-Pfads (nicht durch
  eine Anforderung in `spec.md` gedeckt).
