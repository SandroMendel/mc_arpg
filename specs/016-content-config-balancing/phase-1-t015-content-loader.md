# B16 Phase 3 · T015 Gemeinsamer Content-Ladeablauf

Status: **implementiert und geprüft** (2026-09-16).

## Ladegrenze

`B16ContentLoader` erhält einen injizierten `DocumentReader`. Damit bleibt der Parser in
`rpg-platform` und `rpg-content` kennt weder SnakeYAML noch Paper. Der Ablauf ist fest:

1. Alle neun Quellen werden in der kanonischen Reihenfolge gelesen und geparst.
2. Erst danach validieren die neun Content-Schemas Version, feste Struktur, Typen und Bereiche und
   binden die bestehenden Core-Wertobjekte.
3. Danach prüft `B16ContentReferenceValidator` die Querverbindungen und Invarianten.
4. Erst nach einem vollständigen Erfolg kann `loadSnapshot` die unveränderliche Snapshot-Generation
   zusammensetzen.

`YamlConfigLoader.readDocument` bleibt die einzelne Parser-Methode; `readDocuments` ergänzt eine
parserseitige, schemafreie Reihenfolge-Schnittstelle. Beide Methoden publizieren keinen Content und
führen keine Snapshot- oder Runtime-Änderung aus.

## Kein Teilzustand

Bei einem Parserfehler endet der Lauf in der Read-Phase. Bei einem Schema- oder Referenzfehler wird
kein Bundle beziehungsweise Snapshot zurückgegeben. Der Loader hält keinen aktiven Zustand; die
Entscheidung, ob eine vollständig geprüfte Generation veröffentlicht wird, liegt bei T016 im
Config-Loader.

## Prüfung

```text
gradlew.bat --no-daemon :rpg-content:test
gradlew.bat --no-daemon :rpg-platform:test --tests rpg.platform.config.YamlConfigLoaderTest
gradlew.bat --no-daemon :rpg-plugin:test --tests rpg.plugin.B16ContentSnapshotTest
```

Die serverfreien Orchestrator-Tests zeigen sowohl Abbruch in der Parser-Reihenfolge als auch, dass
alle neun Quellen vor dem ersten Schemafehler gelesen wurden. Der reale SnakeYAML-Lauf über alle
ausgelieferten B16-Defaults erzeugt neun Dokumente und Referenzmetadaten erfolgreich.
