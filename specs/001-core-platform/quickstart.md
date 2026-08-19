# Quickstart: B01 · Core & Plattform validieren

Diese Anleitung prüft, ob die B01-Implementierung die Anforderungen aus `spec.md` erfüllt.
Details zu Schnittstellen: siehe `contracts/`. Details zu Entitäten: siehe `data-model.md`.

## Voraussetzungen

- JDK 25 installiert, Gradle-Wrapper im Projekt vorhanden.
- Für Plattform-Tests: MockBukkit-Abhängigkeit im `rpg-platform`-Testmodul.

## 1. Domänenschicht ohne Server testen (User Story 2, FR-005/006/007/015)

```bash
./gradlew :rpg-core:test
```

**Erwartet**: Alle Tests laufen ohne Bukkit-Klassen im Classpath durch (Nachweis für
Constitution III.1 / FR-015). Testabdeckung MUSS mindestens enthalten:
- Registrierung/Auflösung eines Diensts über `ModuleRegistry` (siehe `contracts/module-registry.md`)
- Zyklische Modulabhängigkeit → `CyclicDependencyException` mit den beteiligten Modul-IDs
- Doppelte Modul-ID → `DuplicateModuleIdException`
- Event-Bus: ein werfender Abonnent blockiert nicht die Zustellung an weitere Abonnenten
  (siehe `contracts/event-bus.md`)

## 2. Fail-Fast bei ungültiger Konfiguration (User Story 1, FR-002)

1. Testserver mit einer absichtlich unvollständigen Konfigurationsdatei starten
   (Pflichtfeld entfernt).
2. **Erwartet**: Der Start bricht ab; die Log-Meldung nennt Datei, Pfad und erwarteten
   Wert (siehe `contracts/config-loader.md`).
3. Fehler beheben, Server erneut starten.
4. **Erwartet**: Start läuft innerhalb von 30 Sekunden (SC-001) durch; jedes Modul meldet
   seinen Status im Log.

## 3. Hot-Reload im laufenden Betrieb (User Story 3, FR-003/FR-004)

1. Testserver mit gültiger Konfiguration starten, einen Testspieler verbinden lassen.
2. Einen Konfigurationswert ändern, Reload auslösen (z. B. `/rpg reload`).
3. **Erwartet**: Neuer Wert wirkt für alle Module gleichzeitig (globaler Reload); der
   verbundene Testspieler bleibt verbunden und verliert keine Daten.
4. Denselben Reload mit einer absichtlich fehlerhaften Konfiguration wiederholen.
5. **Erwartet**: Die zuvor gültige Konfiguration bleibt aktiv, der Server stürzt nicht ab,
   eine klare Fehlermeldung erscheint im Log.

## 4. Scheduler-Abstraktion prüfen (FR-007/FR-008, SC-005)

- Statische Analyse/Test: Es existiert kein Aufruf von `Bukkit.getScheduler()` oder
  gleichwertigen globalen Scheduler-APIs außerhalb der `rpg-platform`-internen
  Adapter-Implementierung (siehe `contracts/scheduler.md`).
- Unit-Test in `rpg-platform` (mit MockBukkit): `runSyncAtLocation`/`runSyncOnEntity`
  liefern ein `TaskHandle`, `cancel()` verhindert die Ausführung zuverlässig.

## 5. Shutdown-Zeitlimit (FR-012, SC-007)

1. Ein Testmodul registrieren, das beim Shutdown absichtlich hängt (nicht terminiert).
2. Server-Shutdown auslösen.
3. **Erwartet**: Der Gesamt-Shutdown terminiert spätestens nach 10 Sekunden für dieses
   Modul (Zwangsterminierung), eine Fehlermeldung wird protokolliert, der Prozess hängt
   nicht unbegrenzt.

## 6. Fehlerisolation zur Laufzeit (FR-009, SC-004)

1. Ein Testmodul registrieren, das bei einem bestimmten Ereignis absichtlich eine
   `RuntimeException` wirft.
2. Ereignis auslösen, während weitere, unabhängige Module aktiv sind.
3. **Erwartet**: Der Fehler wird protokolliert, der Server-Tick läuft weiter, andere
   Module bleiben unbeeinträchtigt.

## Abnahme

Diese Anleitung gilt als erfüllt, wenn alle sechs Abschnitte ohne Abweichung durchlaufen —
das deckt SC-001 bis SC-007 aus `spec.md` vollständig ab.
