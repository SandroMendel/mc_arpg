# Phase 0 · T003: Bestehender Config-Vertrag und B16-Erweiterungspunkte

**Prüfdatum:** 2026-09-12
**Repository:** C:\Users\Ticoo\Desktop\VuntexRPG\b15-performance-observability
**Status:** Ist-Vertrag und geplante Erweiterungen; keine Implementierung

## Scope und verbindliche Klassifikation

Dieses Dokument beschreibt die vorhandene generische Config-API sowie klar abgegrenzte B16-
Erweiterungspunkte. Inventare verwenden dafür genau eine Kategorie aus **CONTENT**,
**PROTECTION_BOUNDARY**, **ALGORITHM_CONSTANT**, **UNIT_DEFINITION**, **PLATFORM_PHYSICS** oder
**UNKNOWN_CANDIDATE**. Admin-Limits und Stats-Minima/-Maxima werden nicht automatisch als
Balance-Content behandelt; die offene Zuordnung bleibt im Inventar als separate Gap-Angabe erhalten.

**Ist-Verhalten** und **geplantes Verhalten** sind ausdrücklich getrennt. Der gemeinsame
ContentSnapshot und Snapshot-Owner rpg-content sind **planned / not implemented**. Der Pfad
rpg-content/src/main/resources existiert am Prüfdatum noch nicht.

## Sources consulted

- rpg-core/src/main/java/rpg/core/config/ConfigLoader.java
- rpg-core/src/main/java/rpg/core/config/ConfigSchema.java
- rpg-core/src/main/java/rpg/core/config/ConfigHandle.java
- rpg-core/src/main/java/rpg/core/config/FieldDefinition.java
- rpg-core/src/main/java/rpg/core/config/SchemaValidator.java
- rpg-core/src/main/java/rpg/core/config/AbstractConfigLoader.java
- rpg-platform/src/main/java/rpg/platform/config/YamlConfigLoader.java
- rpg-platform/src/test/java/rpg/platform/config/YamlConfigLoaderTest.java
- rpg-core/src/test/java/rpg/core/config/ConfigLoaderValidationTest.java
- rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java
- specs/001-core-platform/contracts/config-loader.md
- specs/016-content-config-balancing/{spec,research,data-model,tasks}.md

## Vorhandener Ist-Vertrag

### Laden, Validieren, Registrieren

ConfigLoader.java:21 definiert loadAndValidate(source, schema), :34 register(source, schema) und
:46 reloadAll(). loadAndValidate lädt und validiert einmalig. register lädt initial, registriert
die Quelle für den globalen Reload und liefert einen Handle. reloadAll ist global.

AbstractConfigLoader.java:31-37 führt Parse → SchemaValidator.validate → schema.bind aus.
SchemaValidator.java:29-64 iteriert nur über schema.fields(), löst Punktpfade auf, setzt optionale
Defaults, coerce-t Typen und prüft inklusive Bereiche. Unbekannte Dokument-Schlüssel werden im
Ist-Stand weder abgelehnt noch in die validierte Map übernommen. Es gibt keine Root-Prüfung von
schemaVersion und keine UnknownKeyPolicy-/Registry-API.

### Handle und Reload

ConfigHandle.java:15-22 bietet nur get() und source(). AbstractConfigLoader.java:49-57 validiert
alle registrierten Quellen zuerst in einer Staging-Map. Erst danach wird `publish()` für die
einzelnen Handles aufgerufen. Die aktuelle Referenz nutzt pro Handle eine eigene
`AtomicReference` (AbstractConfigLoader.java:73-78,91-99).

Damit gilt: **geladen/validiert** ist die Parse-/Schema-/Bind-Phase. Die anschließende
Veröffentlichung ist pro Handle thread-sicher, aber sequenziell; sie bildet keine gemeinsame
atomare Transaktion und garantiert keinen atomaren Sichtzustand über mehrere Handles. Bei einem
Fehler vor dem Publish bleiben die alten Handle-Werte erhalten; sobald einzelne Handles
veröffentlicht sind, gibt es für die verbleibenden Handles keinen gemeinsamen Rollback-Mechanismus.
Ein B16-spezifischer, unveränderlicher Multi-Domain-ContentSnapshot und dessen geplanter Owner
`rpg-content` sind noch nicht implementiert.

Der öffentliche Javadoc von `ConfigLoader.java:40` beschreibt den Reload dagegen als atomar und
ohne gemischten Zustand. Das ist eine dokumentierte Ist-Vertragsdivergenz zur beobachteten
Implementierung in `AbstractConfigLoader.java:49-57`; Phase 1 muss Javadoc und Verhalten entweder
angleichen oder den Geltungsbereich des atomaren Versprechens ausdrücklich auf einen späteren
B16-Snapshot begrenzen. Der Javadoc darf bis dahin nicht als Nachweis eines bestehenden
Multi-Handle-Snapshots verwendet werden.

### YAML und Duplicate Keys

YamlConfigLoader.readDocument(Path) in YamlConfigLoader.java:45-55 führt nur parse aus; es
validiert kein Schema und bindet nicht. YamlConfigLoader.java:90-93 setzt Duplicate Keys auf
false; YamlConfigLoaderTest.java:104-110 bestätigt die Ablehnung.

## Verbindliche Grenze von readDocument()

RpgPlugin.java:794-800 verwendet loader.readDocument(Path.of("ui.yml")).get("language") in
configuredLanguage(). Das feste ui.yml wird damit ebenfalls über readDocument() gelesen. Dieser
Ist-Pfad gehört zu B13/UI und liegt außerhalb B16. Er ist kein Beleg dafür, dass feste B16-Dateien
ohne Schema gelesen werden dürfen.

Separat liest RpgPlugin.java:729 die gewählte messages-Datei als offenes Dokument; messages.yml
und messages_de.yml sind Text-/Übersetzungsdaten und außerhalb B16. Zusätzlich liest
RpgPlugin.java:3261-3262 `commands.yml` offen und übergibt es an `RateLimitConfig.from(...)`.
Diese drei vorhandenen offenen Dokumentpfade sind ausdrücklich von festen B16-Content-Dateien zu
trennen. Sie sind keine Präzedenz dafür, dass B16-Dateien ohne Schema gelesen werden dürfen.

Für feste B16-Dateien ist geplant: loadAndValidate/register/reloadAll plus B16-Schema, Root-
schemaVersion, Unknown-Key-Regeln und Querverbindungsprüfungen. Diese Erweiterung ist
**planned / not implemented**; readDocument() darf sie nicht umgehen.

## Geplanter B16-Vertrag

| Bereich | Vorhandener Ist-Stand | Geplante B16-Erweiterung |
|---|---|---|
| Datei-Owner | Laufzeitressourcen liegen unter rpg-plugin/src/main/resources. | Defaults/Schemas/Content unter rpg-content; Zielpfad fehlt noch (**planned / not implemented**). |
| Version | Java-Schema-Version wird in ConfigView getragen; YAML-Root-Version wird nicht verglichen. | positive Root-schemaVersion exakt gegen unterstützte Version prüfen. |
| Schlüssel | Unbekannte Dokument-Schlüssel werden ignoriert. | feste Schlüssel ablehnen; dynamische IDs nur in deklarierter Registry. |
| Referenzen | Einzelne Module/Schemas prüfen bereits IDs. | alle B16-Domänen vor Publish gemeinsam prüfen. |
| Reload | Staging und globaler Handle-Publish sind vorhanden. | vollständige B16-Quellen laden/validieren, dann einen gemeinsamen Snapshot atomar veröffentlichen; bei Fehler alten Snapshot aktiv lassen. |
| Klassifikation | Keine generische Policy für Content/Guard/Algorithmus/Out-of-scope. | Klassifikation in Inventar, Schema und Tests verbindlich führen. |

## Reproduzierbare Prüfcommands und konkrete Results

Die folgenden Einzelprüfungen sind aus dem Repository-Root in PowerShell ausführbar. Jede Prüfung
nennt die erwartete Kernaussage; Exit 0 bedeutet Treffer, Exit 1 kein Treffer und Exit 2 einen
`rg`-Argument-/Kommandofehler. Die Ergebnisse gelten für den Workspace-Snapshot vom 2026-09-12.

```powershell
# Prüfung 1 — generische API-Signaturen: alle drei Treffer müssen erscheinen.
rg -n -- "loadAndValidate|register|reloadAll" rpg-core/src/main/java/rpg/core/config/ConfigLoader.java

# Prüfung 2 — Staging und sequenzielle Handle-Publikation, kein gemeinsamer Snapshot.
rg -n -- "staged|publish|AtomicReference" rpg-core/src/main/java/rpg/core/config/AbstractConfigLoader.java

# Prüfung 3 — Ist-Validator iteriert deklarierte Felder; Root-Version/Unknown-Key-Policy fehlen.
rg -n -- "schema\.fields|unknown|allowUnknown|schemaVersion" rpg-core/src/main/java/rpg/core/config/SchemaValidator.java rpg-core/src/main/java/rpg/core/config/ConfigSchema.java

# Prüfung 4 — alle bestehenden offenen readDocument()-Ausnahmen.
rg -n -- "readDocument|commands\.yml|ui\.yml|messages\.yml|messages_de\.yml" rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java rpg-platform/src/main/java/rpg/platform/config/YamlConfigLoader.java

# Prüfung 5 — Parser-Konfiguration UND der konkrete Duplicate-Key-Test müssen gefunden werden.
$yaml = rg -n -- "setAllowDuplicateKeys\(false\)" rpg-platform/src/main/java/rpg/platform/config/YamlConfigLoader.java
if ($LASTEXITCODE -ne 0) { throw "Duplicate-Key-Parserkonfiguration fehlt." }
$test = rg -n -- "aDuplicatedKeyIsRejectedRatherThanSilentlyMerged" rpg-platform/src/test/java/rpg/platform/config/YamlConfigLoaderTest.java
if ($LASTEXITCODE -ne 0) { throw "Duplicate-Key-Test fehlt." }
$yaml
$test

# Prüfung 6 — geplanter Content-Ressourcenpfad; MISSING ist hier das erwartete Ist-Ergebnis.
if (Test-Path -LiteralPath rpg-content/src/main/resources) {
    Get-ChildItem -LiteralPath rpg-content/src/main/resources -Recurse
} else {
    'MISSING: rpg-content/src/main/resources'
}

# Prüfung 7 — reproduzierbarer Fingerprint des Prüfkontexts (nicht automatisch ein Commitstand).
$repoRoot = (Resolve-Path .).Path
git -c "safe.directory=$repoRoot" rev-parse --show-toplevel
git -c "safe.directory=$repoRoot" rev-parse --short HEAD
$PSVersionTable.PSVersion.ToString()
(rg --version | Select-Object -First 1)
```

**Results/Evidence am 2026-09-12:** Prüfungen 1-2 zeigen die vorhandenen Lade-/Staging-/Publish-
Signaturen. Prüfung 3 zeigt reine `schema.fields()`-Iteration ohne unbekannte-Schlüssel-Policy oder
Root-Versionsvergleich. Prüfung 4 zeigt offene Nutzung für messages, commands und ui; Prüfung 5
belegt sowohl `setAllowDuplicateKeys(false)` als auch den konkreten Testnamen. Prüfung 6 liefert
`MISSING`; der Snapshot-Owner `rpg-content` ist geplant, nicht implementiert. Prüfung 7 beschreibt
Shell-/Tool-Version und den aktuellen Workspace-Fingerprint; wegen der bestehenden uncommitteten
B15-/B16-Änderungen ist dies kein unveränderlicher Repository-Revisionstand.

## Copy-ready locations

- Loader-Signaturen: rpg-core/src/main/java/rpg/core/config/ConfigLoader.java:13-46
- Validierung/Unknown Keys: rpg-core/src/main/java/rpg/core/config/SchemaValidator.java:25-79
- Staging/Publish: rpg-core/src/main/java/rpg/core/config/AbstractConfigLoader.java:31-66,91-99
- Duplicate Keys: rpg-platform/src/main/java/rpg/platform/config/YamlConfigLoader.java:81-93
- readDocument für messages: rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java:691-709,729
- readDocument für feste ui.yml außerhalb B16: rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java:794-800
- Geplanter Root-/Owner-Vertrag: specs/016-content-config-balancing/data-model.md:8-30,34-49,118-137

## Confidence + gaps

**Confidence: hoch** für Signaturen, readDocument()-Verwendungen, Duplicate-Key-Verhalten,
Staging/Publish-Reihenfolge und das Fehlen des rpg-content-Ressourcenpfads. **Gaps:** Es gibt
keinen generischen Root-schemaVersion-Test, keine Unknown-Key-Policy, keinen B16-Multi-Domain-
Snapshot und keinen Laufzeitnachweis für die geplante Erweiterung. Die Aussage über atomare
Veröffentlichung bezieht sich daher nur auf die vorhandene registrierte Loader-Liste, nicht auf
einen bereits existierenden B16-Snapshot.

## Geänderte Dateien

- specs/016-content-config-balancing/phase-0-t001-inventory-v1.md
- specs/016-content-config-balancing/phase-0-t002-java-literals-cross-domain-v1.md
- specs/016-content-config-balancing/phase-0-t003-config-contract.md
- specs/016-content-config-balancing/phase-0-t004-ownership.md
- specs/016-content-config-balancing/phase-0-units-glossary.md
- specs/016-content-config-balancing/tasks.md

Keine Code-/YAML-Implementierung und keine Änderungen an spec.md, data-model.md oder research.md;
kein Commit und kein Push.
