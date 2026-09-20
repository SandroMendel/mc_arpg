# B16 Phase 1 · T012 Domäneninvarianten und stabile IDs

Status: **implementiert und geprüft** (2026-09-13).

## Verantwortungsgrenze

T012 liefert in `rpg-content/src/main/java/rpg/content/validation/` nur immutable,
serverfreie Prüfprimitive. Die Primitive parsen kein YAML, kennen keine Minecraft-/Paper-Typen,
binden keine aktuellen B16-Modelle und veröffentlichen keinen Snapshot. T014 kann daraus
Referenzbeobachtungen aus seinen konkreten gebundenen Domänenmodellen erzeugen; T013 behält die
Besitzverantwortung für `ContentDocument`, `ContentReference`, `ContentRegistry` und
`ContentSnapshot`.

## Benannte Pflichtbeziehungen

`B16ContentInvariant` benennt und prüft jede erlaubte Querverbindung aus der Phase-0-Bestandsaufnahme
und dem Datenmodell:

| Invariante | Prüfung |
|---|---|
| `CLASSES_TO_ABILITIES` | Jede Klassen-Fähigkeits-ID existiert in der Ability-Registry. |
| `MOB_HORDES_TO_ZONES` | Jede Horde-Zonen-ID existiert in der Zonen-Registry. |
| `MOB_HORDES_TO_SPAWN_AREAS` | Jede Horde-Spawn-Area-ID existiert in der Spawn-Area-Registry. |
| `MOB_HORDES_TO_KINDS` | Jede Horde-/Boss-Mob-Kind-ID existiert in der Mob-Kind-Registry. |
| `ITEM_LOOT_TO_TEMPLATES` | Jeder Loot-Eintrag verweist auf eine vorhandene Item-Template-ID. |
| `ITEM_LOOT_TO_ZONES` | Jede zonenbasierte Loot-Registry verwendet eine vorhandene Zonen-ID. |
| `ITEM_LOOT_TO_KINDS` | Jede mob-kind-basierte Loot-Registry verwendet eine vorhandene Mob-Kind-ID. |
| `ITEM_VENDORS_TO_ZONES` | Jede Händler-Registry verwendet eine vorhandene Zonen-ID. |
| `ITEM_VENDORS_TO_TEMPLATES` | Jeder Händler-Eintrag verweist auf eine vorhandene Item-Template-ID. |

Die Meldung `MOB_KINDS_TO_MESSAGES` ist als benannter, aber `inScope=false` markierter Vertrag
enthalten. Text-/Übersetzungsdaten bleiben gemäß Phase 0 ausdrücklich außerhalb B16; ein Aufruf
dieser Regel bricht daher kontrolliert mit `IllegalStateException` statt eine nicht beschlossene
Messages-Beziehung zu simulieren.

Jede Referenz wird als `InvariantReference` mit Quell-Domain, Quell-ID, Ziel-Domain, Ziel-ID,
exaktem YAML-Pfad und Anforderungsbeschreibung übergeben. Für T014 sind zusätzlich optionale
Scope-/Capability-Anforderungen vorgesehen. `InvariantTarget` kann dazu Registry-Pfad, `KeyType`,
Scope (z. B. Zone eines Spawn-Areas) und Capabilities (z. B. `boss`) unveränderlich beschreiben.
Eine unbekannte Ziel-ID oder ein Vertrags-/Scope-/Capability-Verstoß erzeugt
`ContentInvariantViolation`, eine `ConfigValidationException`-kompatible Exception mit diesen
Feldern und dem stabilen Invariantennamen. Die Invarianten erzwingen ihre erwartete Quell-Domain,
Ziel-Domain, Ziel-KeyType und Registry-Identität.

Referenzen und Stable-ID-Kandidaten werden vor der Prüfung materialisiert und lexikografisch
sortiert: zuerst YAML-Pfad, danach Quell-/Ziel-Domain und IDs, bei Kandidaten zusätzlich Typ und
Registry-Pfad. Die Reihenfolge der Eingabe-Collections beeinflusst daher nicht den ersten Fehler.

## Stabile ID-Policy

`StableIdCandidate` bindet jeden Kandidaten an `ContentRegistryContract.KeyType`. Damit bleiben
dynamische Registry-Schlüssel inhaltlich frei, aber typisiert. `StableContentIdPolicy` prüft nur robuste Grundregeln:

- nicht-leere IDs ohne Whitespace oder Steuerzeichen;
- Eindeutigkeit innerhalb von Domain, Registry-Key-Typ und Registry-Identität;
- optional eine vom Caller gelieferte Allowlist für `VANILLA_MOB_TYPE`.

Registry-Inhalte bleiben fachlich frei; `KeyType` ist Typinformation und keine neue ID-Syntax.
Die Policy erzeugt keine IDs aus Anzeige-, Zeilen- oder Laufzeitnamen und kann diese Ableitung
nicht nachträglich aus einem String beweisen. Sie erfindet keine Vanilla-Liste und entscheidet
keine Balancewerte. Diagnosewerte werden für Exceptions steuerzeichen-sicher escaped.

## Bewusst nicht enthalten

Globale Cooldowns, Casting-Zeiten, Raritäten, Affixe und neue Mob-Balance werden weder als
Invariante noch als Default angelegt. Der vorhandene Bestand bleibt unverändert; eine spätere
Fachentscheidung und Test-/Mobwert-Basislinie sind Voraussetzung für eine mögliche Folgetask.

## Prüfung

```text
gradlew.bat --no-daemon :rpg-content:test --tests rpg.content.validation.ContentInvariantValidationTest
gradlew.bat --no-daemon :rpg-content:check
```

Beide Läufe waren am 2026-09-13 erfolgreich. Der fokussierte Test deckt Happy Path, unbekannte
Ziel-ID mit vollständigem Fehlerkontext, Domänen-/Registry-Verträge, Scope-/Capability-Kontext,
Registry-basierte Eindeutigkeit, deterministische Prüfung, steuerzeichen-sichere Diagnosen,
caller-supplied Vanilla-Allowlist sowie den expliziten Messages-/Balance-Scope ab.
