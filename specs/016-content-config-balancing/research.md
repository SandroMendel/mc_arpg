# Phase 0 · Recherche: B16 · Content-Konfiguration & Balancing

Diese Recherche trennt die bereits vorhandenen Verträge von den neuen B16-Anforderungen. Sie ist
die Quellenbasis für `spec.md`; neue APIs werden erst in Tasks und Coding eingeführt, wenn sie hier
gegen die bestehende Architektur abgeglichen sind.

## 1. Bereits vorhandene generische Config-API

### Erlaubte APIs

| API | Quelle | Bedeutung für B16 |
|---|---|---|
| `ConfigLoader.loadAndValidate(Path, ConfigSchema<T>)` | `rpg-core/src/main/java/rpg/core/config/ConfigLoader.java:21` | Einmaliges Laden einer Datei mit Schema und typed binding. |
| `ConfigLoader.register(Path, ConfigSchema<T>)` | `ConfigLoader.java:34` | Lädt initial und hält die Quelle für `reloadAll()` registriert. |
| `ConfigLoader.reloadAll()` | `ConfigLoader.java:46` | Globaler Reload; kein selektiver Modul-Reload. |
| `ConfigHandle<T>.get()` | `rpg-core/src/main/java/rpg/core/config/ConfigHandle.java` | Liefert nach Erfolg den neuen, nach Fehlern den alten gültigen Wert. |
| `ConfigSchema.builder(int)` | `rpg-core/src/main/java/rpg/core/config/ConfigSchema.java:34` | Schema-Version und Binder werden deklarativ gebaut. |
| `FieldDefinition.required/optional/withRange` | `rpg-core/src/main/java/rpg/core/config/FieldDefinition.java:41-64` | Pflicht-/Optionalfelder und numerische inklusive Bereiche. |
| `SchemaValidator.validate(Path, Map<String,Object>, ConfigSchema<?>)` | `rpg-core/src/main/java/rpg/core/config/SchemaValidator.java:25` | Serverfreie Typ-/Pflicht-/Bereichsprüfung und `ConfigView`. |
| `YamlConfigLoader(Path)` | `rpg-platform/src/main/java/rpg/platform/config/YamlConfigLoader.java:41` | Relative Quellen werden gegen den Plugin-Datenordner aufgelöst. |
| `YamlConfigLoader.readDocument(Path)` | `YamlConfigLoader.java:53` | Unvalidiertes Dokument; aktuell absichtlich für offene `messages.yml`-Schlüssel. |

### Copy-ready Muster

Für ein festes Schema ist das Muster aus `rpg-plugin/src/main/java/rpg/plugin/performance/PerformanceConfigSchema.java:20-48`
zu kopieren: `ConfigSchema.builder(1)`, `FieldDefinition`/`.required()`/`.withRange()`, danach
`.boundTo(...)` und `.build()`.

Für ein Modul mit Reload ist das Muster aus
`rpg-core/src/main/java/rpg/core/mob/MobModule.java:236-241` und
`rpg-core/src/main/java/rpg/core/zone/ZoneModule.java:152-156` maßgeblich: `register(Path.of(...),
Schema.schema())`, Fehler in eine modulbezogene Startup-Exception überführen, Handle halten.

## 2. Atomarer Reload und Bootstrap

`rpg-core/src/main/java/rpg/core/config/AbstractConfigLoader.java:49-56` validiert alle registrierten
Quellen zuerst und veröffentlicht danach. `ConfigHandle` hält den vorherigen Wert, bis die
Veröffentlichung erfolgt. Das ist die bestehende Grundlage für FR-013/FR-014.

`rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java:128-145` führt die Default-Dateien auf. In
`RpgPlugin.java:350-359` werden sie mit `saveResource(file, false)` in den Plugin-Datenordner
kopiert, ohne vorhandene Betreiberdateien zu überschreiben.

`RpgPlugin.java:543-571` ruft `configLoader.reloadAll()` auf und benachrichtigt anschließend die
Module über `applyReloadedConfig()`. B16 muss den gemeinsamen Content-Snapshot vor dieser
Veröffentlichung beziehungsweise innerhalb der vorhandenen Staging-Grenze absichern; ein später
geworfener Querverbindungsfehler darf keinen bereits veröffentlichten Teilzustand zurücklassen.

## 3. YAML-Verhalten und Sicherheitsgrenzen

`YamlConfigLoader.java:58-88` akzeptiert nur ein Mapping am YAML-Dokumentwurzelknoten, unterscheidet
fehlende/unlesbare Dateien und meldet Parserfehler über `ConfigValidationException`.

`YamlConfigLoader.java:90-94` verwendet `SafeConstructor` und `setAllowDuplicateKeys(false)`. B16
übernimmt diese Sicherheitsentscheidung und ergänzt die fachliche Prüfung unbekannter fester
Schlüssel. `readDocument()` ist kein allgemeiner Weg um feste Content-Schemas herum; der Javadoc
bezeichnet `messages.yml` ausdrücklich als Ausnahme für offene Nachrichtenschlüssel.

## 4. Existierende Domänen-Schemas und Ressourcen

Bereits vorhandene Schema-/Config-Muster liegen unter:

- `rpg-core/src/main/java/rpg/core/classes/ClassConfig.java` und `ClassConfigSchema.java`;
- `rpg-core/src/main/java/rpg/core/ability/AbilityConfig.java` und `AbilityConfigSchema.java`;
- `rpg-core/src/main/java/rpg/core/mob/MobConfig.java` und `MobConfigSchema.java`;
- `rpg-core/src/main/java/rpg/core/item/ItemConfig.java` und `ItemConfigSchema.java`;
- `rpg-core/src/main/java/rpg/core/currency/CurrencyConfig.java` und `CurrencyConfigSchema.java`;
- `rpg-core/src/main/java/rpg/core/combat/CombatConfig.java` und `CombatConfigSchema.java`;
- `rpg-core/src/main/java/rpg/core/zone/ZoneConfig.java` und `ZoneConfigSchema.java`.

Die ausgelieferten YAML-Dateien liegen aktuell in
`rpg-plugin/src/main/resources/`: `classes.yml`, `abilities.yml`, `progression.yml`, `combat.yml`,
`zones.yml`, `mobs.yml`, `items.yml`, `currency.yml` sowie weitere blockfremde Dateien. B16s
Clarify verschiebt die Ownership der B16-Defaults nach `rpg-content`; die erste Laufzeitmigration
behält die relativen Dateinamen bei.

Konkrete, bereits sichtbare Gruppierungen sind zum Beispiel:

- `progression.yml`: `xp-curve`, `level-growth`, `mob-xp`, `party`;
- `mobs.yml`: `budget`, `horde`, `kinds`, `hordes`;
- `items.yml`: `inventory`, `wear`, `repair`, `templates`, `loot`, `vendors`;
- `zones.yml`: `fallback-point`, `zones`;
- `abilities.yml`: `runtime` und `abilities`.

Diese Gruppierungen werden nicht ohne Specify-Grund auseinandergerissen. B16 validiert und
versioniert sie zunächst als gebündelte Typ-Dateien.

## 5. Modulgrenzen: Dokumentation gegen Ist-Stand

`minecraft-rpg-spec/minecraft-rpg-spec/docs/01-architecture.md` beschreibt `rpg-content` als
„Konfigurations-Ladelogik + Schema-Validierung“. Im Ist-Stand ist die Verantwortlichkeit geteilt:

- generische Verträge/Schema-Validator: `rpg-core`;
- YAML-Parser: `rpg-platform`;
- Bootstrap, Default-Kopien und Reload-Hooks: `rpg-plugin`;
- `rpg-content/build.gradle.kts` ist derzeit ein Platzhalter mit ausschließlich
  `api(project(":rpg-core"))`.

B16 konkretisiert die Dokumentation: `rpg-content` besitzt künftig Content-Definitionen, Default-
Ressourcen, domänenspezifische Schemas und Cross-Domain-Prüfungen, aber nicht Paper-Zugriff und
nicht den YAML-Parser. Die Architektur-Dokumentation muss im Task-Block entsprechend angepasst
werden, damit sie nicht eine nicht vorhandene `YamlConfigLoader`-Implementierung in `rpg-content`
verspricht.

## 6. Migration und Versionierung

`ConfigSchema` führt bereits eine Java-seitige `schemaVersion` und `ConfigView.schemaVersion()`;
`SchemaValidator` schreibt diese Version in `MapConfigView`, prüft aber aktuell nicht zwingend ein
gleichnamiges Feld im YAML-Dokument. B16 muss deshalb den Dateivertrag ergänzen: `schemaVersion` ist
ein reservierter, erforderlicher Root-Schlüssel und wird gegen `ConfigSchema.schemaVersion()` geprüft.

Die bestehende `StateVersionMigrator`-Idee in
`rpg-core/src/main/java/rpg/core/session/StateVersionMigrator.java:37` ist als Muster für explizite,
registrierte Migrationsschritte brauchbar; sie darf nicht ungeprüft als Config-API angenommen werden.
Für B16 gilt: Legacy-v0-Dateien werden über einen dokumentierten Migrationsweg nach v1 überführt,
normales Starten schreibt keine Quelldatei um.

## 7. Anti-Patterns, die B16 vermeiden muss

- Keine neue YAML-/TOML-/HOCON-Bibliothek und kein zweiter Parser neben
  `rpg-platform`/`YamlConfigLoader`.
- Keine Nutzung von `readDocument()` als Umgehung für feste Content-Schemas.
- Keine direkte Bukkit-/Paper-Abhängigkeit in `rpg-core` oder `rpg-content`.
- Keine Speicherung von Content-Zahlen als Java-Fallback, der bei fehlenden Pflichtdaten heimlich
  greift.
- Keine Änderung von Spielerzuständen während eines Content-Reloads.
- Keine stillschreibende Konfigurationsmigration beim Start.
- Keine Spreadsheet-/Netzwerk-/Serverpflicht für das Analysewerkzeug.
- Keine neue Balancezahl nur deshalb, weil ein Schema einen Platzhalter braucht; bis zur späteren
  Balancing-Runde gilt der vorhandene Bestand.

## 8. Recherche-Lücken vor Coding

Vor dem Task- und Coding-Schritt müssen noch direkt im Quellcode verifiziert werden:

1. welche konkreten B16-Werte derzeit noch als Java-Literale außerhalb der YAML-Schemas existieren;
2. welche Querverweise bereits in `MobModule`, `ItemModule`, `AbilityModule`, `ZoneModule` und
   `RpgPlugin` geprüft werden;
3. wie der gemeinsame Content-Snapshot in die aktuelle `reloadAll()`-Staging-Grenze integriert
   wird, ohne bestehende B01–B15-Tests zu schwächen;
4. welche Ressourcen aus `rpg-plugin` nach `rpg-content` verschoben werden können, ohne
   `saveResource`/Jar-Merging zu brechen.
