# B16 Phase 1 · T013 Unveränderliche Content-Verträge und atomarer Snapshot

Status: **implementiert und geprüft** (2026-09-14).

## Verantwortungsgrenze

T013 definiert in `rpg-content/src/main/java/rpg/content/` ausschließlich unveränderliche,
serverfreie In-Memory-Verträge. Die Typen parsen kein YAML, publizieren keinen aktiven
Laufzeitzustand und verändern keine `ConfigHandle`-Werte. Cross-Domain-Prüfungen, Staging und die
Anbindung an den bestehenden Reload-Pfad bleiben T014–T016 vorbehalten.

## Verträge

- `ContentReference` hält Quell-/Ziel-Domäne, beide stabilen IDs, den exakten YAML-Pfad und die
  Existenz-/Kompatibilitätsanforderung zusammen. `yamlPath()` ist ein Alias für die bestehende
  T012-Validierungssprache; eine Auflösung findet in T013 nicht statt.
- `ContentRegistry` bündelt die bereits typisierten `TypedContentRegistry`-Werte einer Domäne,
  übernimmt Listen und Indizes defensiv und identifiziert jede Teilregistry über ihren
  dokumentierten `ContentRegistryContract`-Pfad.
- `ContentDocument<T>` hält den validierten typisierten Inhalt, die Registry und alle aus genau
  dieser Dokumentversion gewonnenen Referenzen als eine unveränderliche Einheit. Registry- und
  Referenz-Quelldomäne müssen zur Dokumentdomäne passen.
- `ContentSnapshot` übernimmt alle Dokumente in einem Zug, lehnt doppelte Quellnamen oder Domänen
  ab und leitet seine Referenzsicht ausschließlich aus diesen exakten Dokumentinstanzen ab. Es
  gibt keinen Konstruktor für eine separat austauschbare Referenzliste; neue Dokumente können
  daher nicht versehentlich mit Referenzen einer älteren Generation kombiniert werden.

Die enthaltenen B16-Core-Wertobjekte folgen wie die vorhandenen `TypedContentRegistry`-Werte dem
Vertrag, bereits unveränderlich gebunden zu sein. Alle von T013 selbst besessenen Listen, Maps,
Indizes, Registry-Einträge und Referenzmetadaten werden defensiv übernommen und nur als
unveränderliche Ansichten veröffentlicht.

## Prüfung

```text
gradlew.bat --no-daemon :rpg-content:test --tests rpg.content.ContentSnapshotTest
gradlew.bat --no-daemon :rpg-content:compileJava --rerun-tasks
gradlew.bat --no-daemon :rpg-content:check
```

Alle Läufe waren am 2026-09-14 erfolgreich. `ContentSnapshotTest` umfasst sechs serverfreie Tests
für defensive Übernahme und unveränderliche Sichten, Registry-Lookup und Objektidentität,
vollständige Referenzmetadaten, Dokument-/Registry-Domänenidentität, getrennte alte/neue
Snapshot-Generationen sowie die Ablehnung kollidierender Dokument- und Registry-Identitäten. Der
abschließende Modulcheck führte Spotless und alle 33 `rpg-content`-Tests erfolgreich aus.
