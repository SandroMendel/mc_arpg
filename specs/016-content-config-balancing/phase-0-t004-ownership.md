# B16 Phase 0 — T004 Besitz- und Lebenszyklusvertrag

**Prüfdatum:** 2026-09-12
**Repository:** `C:\Users\Ticoo\Desktop\VuntexRPG\b15-performance-observability`
**Status:** Phase-0-Nachweis; keine Java-/Kotlin-/YAML-Implementierung

Dieser Nachweis trennt den nachgewiesenen Ist-Stand vom geplanten B16-Vertrag. Er ist eine
Dokumentation für T004 und ändert weder Build-, Architektur- noch Laufzeitdateien.

## Ist-Stand versus geplanter Vertrag

| Bereich | Ist-Stand (2026-09-12) | Geplanter B16-Vertrag |
|---|---|---|
| `rpg-content` | **Platzhalter / noch nicht implementiert.** `rpg-content/build.gradle.kts` enthält derzeit ausschließlich `api(project(":rpg-core"))`; die B16-Definitionen, Defaults, Schemas, Ressourcen und Cross-Domain-Prüfungen existieren dort noch nicht. | `rpg-content` besitzt die B16-Definitionen, Default-Ressourcen, domänenspezifischen Schemas und serverfreien Cross-Domain-Prüfungen. Es kennt weder Bukkit noch Paper und bleibt von `rpg-core` abhängig. |
| `rpg-core` | Enthält die generischen Config-Verträge und serverfreien Validierungsprimitive. `ConfigLoader`, `ConfigSchema`, `ConfigHandle`, `FieldDefinition`, `SchemaValidator` und `AbstractConfigLoader` sind vorhanden; ein B16-`ContentSnapshot` ist nicht vorhanden. | Bleibt Owner der generischen Config-Verträge, Validierungsprimitive und neutralen Handles. B16-spezifische Datei- und Registry-Schemas gehören nicht in diesen Layer. |
| `rpg-platform` | `rpg-platform/src/main/java/rpg/platform/config/YamlConfigLoader.java` ist der vorhandene YAML-Parser; Duplicate Keys werden dort abgelehnt. | Trägt für B16 ausschließlich YAML-Parsing und die daraus gelieferten Dokumentdaten bei. Keine Content-Definitionen, Defaults, Cross-Domain-Logik oder zweiter Parser. |
| `rpg-plugin` | Lädt beim Bootstrap die Dateien aus `rpg-plugin/src/main/resources/`, kopiert Defaults in den Plugin-Datenordner und verdrahtet den bestehenden Reload-/Admin-Pfad. Die vorhandene Reload-Semantik staged registrierte Quellen und veröffentlicht danach Handles; ein gemeinsamer B16-Snapshot ist noch nicht implementiert. | Bleibt Owner von Bootstrap, Ressourcenkopie, Laufzeitladen, Validierungsaufruf, Publish/Reload-Orchestrierung und dem bestehenden Admin-Rechtepfad `rpg.admin.reload` über `/rpg reload`. |
| `rpg-persistence` | Bestehende Integrations- und Consumer-Rolle: Module unter `rpg-persistence/src/main/java/rpg/persistence/{classes,ability,progression,currency,stats}` konsumieren fachliche Konfiguration bzw. Registries. `rpg-persistence` ist dadurch kein B16-Owner. | Bleibt Consumer/Integrationsrolle. Es liefert keine B16-Definitionen, Defaults, Ressourcen oder Parser und wird vom Plugin mit dem veröffentlichten gültigen Zustand versorgt. |
| Laufzeitressourcen | Die neun B16-Kandidaten liegen aktuell unter `rpg-plugin/src/main/resources/`; der geplante `rpg-content/src/main/resources/`-Pfad fehlt im Ist-Stand. | Defaults kommen aus dem `rpg-content`-JAR. Der Plugin-Pfad kopiert sie bei Bedarf in den Datenordner, überschreibt vorhandene Betreiberdateien nicht und lädt anschließend ausschließlich die Datenordner-Dateien. |

## Azyklischer Dependency- und Ownership-Vertrag

Der B16-Vertrag ist fachlich und als Modulgraph azyklisch:

```text
rpg-content  ───────▶ rpg-core
rpg-platform ───────▶ rpg-core
rpg-plugin   ───────▶ rpg-content
rpg-plugin   ───────▶ rpg-platform
rpg-plugin   ───────▶ rpg-persistence
rpg-plugin   ───────▶ rpg-core
```

Verbindliche Richtung und Zuständigkeit:

- **`rpg-core` — generische Verträge:** `ConfigLoader`, `ConfigSchema`, `ConfigHandle`,
  `FieldDefinition`, `SchemaValidator` und `AbstractConfigLoader` bilden den neutralen Vertrag.
  Dieser Layer enthält keine Bukkit-/Paper-Abhängigkeit.
- **`rpg-content` — B16-Fachbesitz:** Definitionen, Defaults, Ressourcen, gebündelte
  Dokument-Schemas, typisierte Registries und Cross-Domain-Validierung. Abhängigkeit nur zu
  `rpg-core`; insbesondere kein Zugriff auf Bukkit, Paper oder den YAML-Parser.
- **`rpg-platform` — YAML-Parser:** `YamlConfigLoader` liest YAML und weist Duplicate Keys zurück.
  Für B16 werden dort keine fachlichen Schemas oder Content-Defaults verankert.
- **`rpg-plugin` — Laufzeit- und Integrationspfad:** Bootstrap, Kopieren der Defaults,
  Datenordner-Auswahl, Aufruf von Laden/Validieren/Publish, Reload-Orchestrierung sowie der
  bestehende Admin-Rechtepfad.
- **`rpg-persistence` — bestehender Consumer:** Die vorhandenen Persistence-Module bleiben
  Integrationsverbraucher. Diese Rolle darf nicht mit dem geplanten Content-Owner verwechselt
  werden.

Insbesondere gilt: `rpg-content` darf weder von `rpg-platform`, `rpg-plugin` noch
`rpg-persistence` abhängen; `rpg-core` darf keinen dieser höheren Layer referenzieren. Ein
Zyklus über den Parser oder über Persistence ist damit ausgeschlossen.

## B16-Dateien und genau ein geplanter Owner

Die Laufzeitnamen bleiben bei der ersten Migration unverändert. Jede Zeile hat genau einen
geplanten Owner: `rpg-content`.

| Aktuelle Quelle / B16-Scope | Unveränderter Laufzeitname im Plugin-Datenordner | Geplanter Owner |
|---|---|---|
| `rpg-plugin/src/main/resources/classes.yml` | `classes.yml` | `rpg-content` |
| `rpg-plugin/src/main/resources/abilities.yml` | `abilities.yml` | `rpg-content` |
| `rpg-plugin/src/main/resources/progression.yml` | `progression.yml` | `rpg-content` |
| `rpg-plugin/src/main/resources/combat.yml` | `combat.yml` | `rpg-content` |
| `rpg-plugin/src/main/resources/zones.yml` | `zones.yml` | `rpg-content` |
| `rpg-plugin/src/main/resources/mobs.yml` | `mobs.yml` | `rpg-content` |
| `rpg-plugin/src/main/resources/items.yml` | `items.yml` | `rpg-content` |
| `rpg-plugin/src/main/resources/currency.yml` | `currency.yml` | `rpg-content` |
| `rpg-plugin/src/main/resources/stats.yml` — nur der ausdrücklich zugelassene B16-Teilbereich `attributes` | `stats.yml` | `rpg-content` |

`ui.yml`, `commands.yml`, `messages.yml`, `messages_de.yml`, `performance.yml`,
`persistence.yml`, `session.yml` sowie `statistics.yml` sind damit nicht als zusätzliche
B16-Dateien gemeint. Bestehende `readDocument()`-Ausnahmen für blockfremde Dateien bleiben
separat und dürfen nicht als B16-Schemaweg dienen.

## Ressourcen-, Bootstrap- und Snapshot-Lifecycle

Der folgende Ablauf ist **geplant**, soweit nicht ausdrücklich als Ist-Stand markiert:

1. **Default-Quelle:** Das `rpg-content`-JAR liefert die neun versionierten B16-Defaults aus
   seinen Ressourcen.
2. **Erstkopie:** `rpg-plugin` kopiert jede fehlende Default-Datei mit unverändertem
   Laufzeitnamen in den Plugin-Datenordner. Eine vorhandene Betreiberdatei wird nicht
   überschrieben und bleibt byte-identisch.
3. **Laden:** Der Plugin-Laufzeitpfad liest die Dateien aus dem Datenordner über den vorhandenen
   YAML-Parser in `rpg-platform`.
4. **Validieren:** `rpg-content` wendet die gebündelten B16-Schemas, Registry-Regeln und
   Cross-Domain-Prüfungen auf alle B16-Quellen an. Alle Quellen müssen erfolgreich sein, bevor
   ein neuer Zustand veröffentlicht wird.
5. **Publish:** Der Plugin-Pfad veröffentlicht den vollständigen gültigen B16-Zustand und
   erreicht die bestehenden Modul-Hooks über die vorhandene `ConfigHandle`-/`reloadAll()`-
   Verdrahtung. Bei Fehlern bleibt der vollständige vorherige Zustand aktiv.

Eine **gemeinsame Snapshot-Funktion** für den unveränderlichen, domänenübergreifenden
`ContentSnapshot` ist **planned / not implemented**. Die vorhandene Staging-/Handle-Semantik
ist kein Nachweis, dass dieser B16-Snapshot bereits existiert. Ein erfolgreicher Reload darf
keinen halb neuen Content-Zustand veröffentlichen.

## Explizite Nicht-Ziele

- Kein Bukkit- oder Paper-Code in `rpg-core` oder `rpg-content`.
- Kein zweiter YAML-Parser und kein Umgehen fester B16-Schemas über `readDocument()`.
- Kein Spreadsheet-Runtime-Import; das Analysewerkzeug bleibt server-, netzwerk- und
  spreadsheetfrei.
- Keine stille Migration oder stille Rebalance. Eine v0→v1-Migration muss explizit aufgerufen,
  versioniert und verhaltensneutral sein; vorhandene Zahlen, IDs und Einheiten werden nicht
  nebenbei geändert.
- Keine Verlagerung der bestehenden Persistence-Consumer in den Content-Owner.

## Offene Gates und Phase 1

Die folgenden Entscheidungen bleiben offen und werden in Phase 1 nicht durch Platzhalterwerte
entschieden:

- `mobs.yml:admin-spawn-limit` — **confirmed** als serverweite Admin-/Schutzgrenze;
  `mobs.yml` besitzt den Wert `20`, der Java-Default bleibt Kompatibilitäts-Fallback.
- `stats.yml:attributes.*.min/max` — **open**; die Abgrenzung zwischen veränderbaren B16-Feldern
  und unveränderlichen Schutzgrenzen ist noch nicht beschlossen.

Phase 1 muss deshalb mindestens die Build-Abhängigkeiten für den geplanten Content-Inhalt, die
gebündelten Dokument-Schemas einschließlich `schemaVersion` und den gemeinsamen
`ContentSnapshot` implementieren. Dazu gehören die in den Tasks beschriebenen Schritte T005
bis T008; erst danach können die nachgelagerten Schema-, Validierungs- und Reload-Gates belastbar
abgenommen werden. Die offenen Gates dürfen nicht durch erfundene aktuelle Klassen oder APIs
geschlossen werden.

## Reproduzierbare Read-only-Prüfungen

Alle folgenden Befehle sind aus dem Repository-Root dieses Dokuments in PowerShell ausführbar.
Sie sind Diagnosevorschläge und wurden für diesen T004-Nachweis nicht ausgeführt.

```powershell
# 1 — aktuelle Bootstrap-Dateien, readDocument-Ausnahmen und Consumer-Pakete
rg -n "DEFAULT_CONFIG_FILES|messages\.yml|messages_de\.yml|readDocument" -- rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java
rg -n "class (ClassesModule|AbilityModule|ProgressionModule|CurrencyModule|StatsModule)" -- rpg-persistence/src/main/java
Get-ChildItem -LiteralPath rpg-plugin/src/main/resources -File -Filter '*.yml' | Select-Object -ExpandProperty FullName
```

**Erwartete Kernaussage:** `RpgPlugin.java` enthält die aktuelle operative Dateiliste und
`readDocument()`-Ausnahmen; die Persistence-Consumer liegen unter `rpg.persistence`; die neun
B16-Ressourcen liegen aktuell im Plugin-Ressourcenpfad. `rg` Exit 0 bedeutet Treffer, Exit 1
keine Evidenz und Exit 2 einen Kommando-/Argumentfehler. `Get-ChildItem` liefert die vorhandenen
Dateinamen; ein leerer Treffer wäre ein Ist-Stand-Widerspruch und nicht automatisch ein
Implementierungsfehler dieses Dokuments.

```powershell
# 2 — geplanter Content-Ressourcenpfad: im aktuellen Snapshot erwartungsgemäß noch nicht vorhanden
if (Test-Path -LiteralPath rpg-content/src/main/resources) {
    Get-ChildItem -LiteralPath rpg-content/src/main/resources -Recurse
} else {
    'MISSING: rpg-content/src/main/resources'
}
```

**Erwartete Kernaussage:** `MISSING: rpg-content/src/main/resources` ist das erwartete
Ist-Ergebnis für den noch nicht implementierten `rpg-content`-Ressourcen-Owner. Der
`Test-Path`-False-Zweig ist kein Kommandoabbruch.

```powershell
# 3 — vorhandene generische Loader-Signaturen und Staging/Publish
rg -n -- "loadAndValidate|register|reloadAll" rpg-core/src/main/java/rpg/core/config/ConfigLoader.java
rg -n -- "staged|publish|AtomicReference" rpg-core/src/main/java/rpg/core/config/AbstractConfigLoader.java
```

**Erwartete Kernaussage:** Die generischen Lade-/Registrierungs- und bestehende
Staging-/Publish-Belege sind vorhanden; daraus darf nicht auf eine bereits vorhandene
B16-spezifische Snapshot-Funktion geschlossen werden. Für beide `rg`-Aufrufe gelten Exit 0,
Exit 1 und Exit 2 wie oben.

```powershell
# 4 — Parsergrenze und Duplicate-Key-Verhalten
$yaml = rg -n -- "setAllowDuplicateKeys\(false\)" rpg-platform/src/main/java/rpg/platform/config/YamlConfigLoader.java
if ($LASTEXITCODE -ne 0) { throw "Duplicate-Key-Parserkonfiguration fehlt." }
$test = rg -n -- "aDuplicatedKeyIsRejectedRatherThanSilentlyMerged" rpg-platform/src/test/java/rpg/platform/config/YamlConfigLoaderTest.java
if ($LASTEXITCODE -ne 0) { throw "Duplicate-Key-Test fehlt." }
$yaml
$test
```

**Erwartete Kernaussage:** Der vorhandene `YamlConfigLoader` lehnt Duplicate Keys ab und der
konkrete Test belegt dieses Verhalten. Exit 0 beider Suchläufe bestätigt den Ist-Vertrag; ein
Fehler beendet die Prüfung und zeigt eine fehlende aktuelle Evidenz an, nicht eine zu
implementierende B16-API.

```powershell
# 5 — aktueller Modulvertrag im Build, ohne Build auszuführen
Get-Content -Raw rpg-content/build.gradle.kts
Get-Content -Raw rpg-core/build.gradle.kts
Get-Content -Raw rpg-platform/build.gradle.kts
Get-Content -Raw rpg-plugin/build.gradle.kts
```

**Erwartete Kernaussage:** `rpg-content` hängt aktuell nur von `rpg-core` ab,
`rpg-platform` von `rpg-core`, und `rpg-plugin` verdrahtet Content, Platform, Persistence und
Core. Die Ausgabe ist eine Bestandsaufnahme; dieser Befehl führt keinen Build aus.

## Verweise

- T001: [Phase-0-Bestandsinventar](./phase-0-t001-inventory-v1.md) — neun Dateien,
  Laufzeitpfade, Consumer und geplante Owner.
- T002: [Java-Literale und Cross-Domain-Prüfungen](./phase-0-t002-java-literals-cross-domain-v1.md)
  — bestehende Querverbindungen, offene Schutz-/Content-Grenzen und Coverage-Limit.
- T003: [bestehender Config-Vertrag](./phase-0-t003-config-contract.md) — vorhandene
  generische APIs, Parsergrenze, Duplicate-Key-Verhalten und readDocument-Ausnahmen.
- [Einheiten- und Terminologie-Glossar](./phase-0-units-glossary.md) — Sekunden,
  Millisekunden, Ticks, Fraction, Condition-% sowie bestätigte `admin-spawn-limit`-/offene
  Min-/Max-Semantik.
- [B16-Spezifikation](./spec.md), [Recherche](./research.md) und
  [Datenmodell](./data-model.md) — verbindliche B16-Begriffe und Ownership-Zielbild.
- [Phase-1-Tasks](./tasks.md#phase-1-gemeinsame-b16-struktur-und-ausgelieferte-defaults),
  insbesondere T005–T008 — Dokumentverträge, Default-Überführung, Schemas und
  Nicht-Überschreiben beim Bootstrap.
