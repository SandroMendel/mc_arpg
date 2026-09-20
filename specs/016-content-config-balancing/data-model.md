# Phase 1 · Datenmodell: B16 · Content-Konfiguration & Balancing

Dieses Modell beschreibt die Zielstruktur, ohne die noch nicht vorhandenen Implementierungsklassen
vorwegzunehmen. Die Namen dienen als stabile Fachbegriffe für Tasks, Tests und spätere Java-APIs.

## 1. Einheitliches Dokumentformat

Jede B16-Datei bleibt pro Content-Typ gebündelt und erhält denselben reservierten Root-Schlüssel:

```yaml
schemaVersion: 1

<bestehende fachliche Abschnitte des Content-Typs>:
  <stabile-id>:
    <fachliche Werte>
```

`schemaVersion` ist kein optionaler Fallback. Fehlt er, wird die Datei im normalen Serverstart als
Legacy-Datei abgelehnt; nur der explizite Migrationsweg darf daraus eine neue Datei erzeugen.

### Regeln

| Regel | Bedeutung |
|---|---|
| Eine Datei = ein Content-Typ | Keine Datei pro einzelner Klasse, Fähigkeit oder Mob-Art. |
| `schemaVersion` am Root | Versioniert die gesamte gebündelte Datei, nicht jede Entität einzeln. |
| Stabile IDs | IDs sind Referenzen und werden nicht aus Anzeigenamen oder Zeilennummern abgeleitet. |
| Einheiten im Feldnamen | Bestehende Konventionen wie `cooldown-ms`, `respawn-interval-ms` und `range-blocks` bleiben erhalten. |
| Keine stillen Defaults für Pflichtdaten | Optionalität muss im Schema mit einem fachlich begründeten Default stehen. |
| Freie Schlüssel nur in Registries | `warrior`, `greenfields` oder `zombie` sind dynamische IDs nur dort, wo das jeweilige Schema sie registriert. |

## 2. B16-Dokumente und Ownership

Die erste Migration behält die Laufzeitnamen im Plugin-Datenordner. Die versionierten Default-
Ressourcen werden in `rpg-content/src/main/resources/` ausgeliefert.

| Datei | Content-Bereiche | Typische aktuelle Abschnitte | Zuständiger Consumer |
|---|---|---|---|
| `classes.yml` | Klassen-IDs, Klassenpfade, klassenbezogene Content-Zuordnung | `classes` | B07 / `rpg.core.classes` |
| `abilities.yml` | Fähigkeiten, Laufzeitparameter, Effekte und Referenzen | `runtime`, `abilities` | B08 / `rpg.core.ability` |
| `progression.yml` | XP-Kurve, Wachstumswerte, Mob-XP und verwandte Fortschrittswerte | `xp-curve`, `level-growth`, `mob-xp`, `party` | B06–B08 |
| `combat.yml` | konfigurierbare Kampf-/Formelparameter | bestehende Kampfabschnitte | B05 / `rpg.core.combat` |
| `zones.yml` | Zonen-IDs, Geometrie-/Punktdaten und zonenbezogene Content-Werte | `fallback-point`, `zones` | B09 / `rpg.core.zone` |
| `mobs.yml` | Mob-Budgets, Horde-Werte, Mob-Arten und Zonen-Horden | `budget`, `horde`, `kinds`, `hordes` | B10 / `rpg.core.mob` |
| `items.yml` | Vorlagen, Reparatur-/Wear-Werte, Loot und Händler-Content | `inventory`, `wear`, `repair`, `templates`, `loot`, `vendors` | B11 / `rpg.core.item` |
| `currency.yml` | Startbestand, Drop-Werte und Währungsgrenzen | `account`, `drops`, `ledger`, `history` | B08b / `rpg.core.currency` |
| `stats.yml` (Teilbereiche) | nur Werte, die B07–B11 als Spielgefühl bestimmen | `attributes` | B04 als Consumer, B16 als Content-Eigentümer |

`statistics.yml`, `ui.yml`, `messages*.yml`, `commands.yml`, `performance.yml`,
`persistence.yml` und `session.yml` werden nicht pauschal zu B16-Content erklärt. Ihre
balancingähnlichen Werte bleiben beim jeweiligen Block, bis ein eigener Clarify-Beschluss sie
überführt.

## 3. Fachliche Entitäten

### `ContentDocument`

| Feld | Bedeutung |
|---|---|
| `source` | relativer Laufzeitname, z. B. `mobs.yml` |
| `domain` | feste B16-Domäne, z. B. `mobs` |
| `schemaVersion` | Root-Version der Datei |
| `registry`/`sections` | validierter, typisierter Inhalt des Dokuments |
| `sourceHash` (Analyse) | Hash der Eingabedatei für reproduzierbare Berichte; kein Gameplay-Feld |

### `ContentRegistry`

Eine Registry ordnet stabile IDs zu validierten Einträgen. Beispiele:

- `ClassId → ClassDefinition`;
- `AbilityId → AbilityDefinition`;
- `MobKindId → MobDefinition`;
- `ZoneId → ZoneDefinition`;
- `ItemTemplateId → ItemTemplate`.

Anzeigenamen, Nachrichtenschlüssel und Vanilla-Materialien sind Referenzen beziehungsweise
Attribute. Sie dürfen nicht als implizite ID-Quelle verwendet werden.

### `ContentReference`

| Feld | Bedeutung |
|---|---|
| `sourceDomain` / `sourceId` | der Eintrag, der die Referenz enthält |
| `targetDomain` / `targetId` | der erwartete Ziel-Eintrag |
| `path` | exakter YAML-Dokumentpfad der Referenz |
| `requirement` | erforderliche Existenz-/Kompatibilitätsregel |

Ein unbekannter `targetId` ist ein Validierungsfehler, kein leerer Optionalwert. Die Fehlermeldung
nennt Quelle und Ziel.

### `ContentSnapshot`

Der Snapshot ist nach erfolgreicher Validierung unveränderlich und enthält alle B16-Dokumente,
die gemeinsam veröffentlicht werden. Module lesen daraus beziehungsweise aus darauf basierenden
`ConfigHandle`-Werten. Ein Snapshot darf nie nur einzelne neue Dateien mit alten Querverweisen
kombinieren.

Lebenszyklus:

```text
Dateien lesen
    ↓
YAML parsen
    ↓
schemaVersion + feste Struktur prüfen
    ↓
Typen/Bereiche binden
    ↓
Querverweise und Domäneninvarianten prüfen
    ↓ Erfolg                         ↓ Fehler
ContentSnapshot veröffentlichen       alten Snapshot behalten
    ↓
Module anwenden + Audit-Ergebnis
```

## 4. Migration

### Legacy-Version 0

Die heutigen Dateien ohne Root-`schemaVersion` werden als bekannte Legacy-Version 0 behandelt,
aber nur im expliziten Migrationswerkzeug beziehungsweise in einem expliziten Migrationsschritt.
Der normale Serverstart darf nicht raten, ob eine unversionierte Datei v0 oder eine beschädigte v1
ist.

### Migration v0 → v1

Der erste Migrationsschritt:

1. liest die bestehende Datei;
2. prüft die bekannte Legacy-Struktur;
3. ergänzt ausschließlich `schemaVersion: 1` und nötige strukturelle Hüllen;
4. behält IDs, Zahlen, Einheiten und Reihenfolge soweit YAML das zulässt;
5. schreibt eine neue Zieldatei oder ein explizites Migrationsartefakt;
6. lässt die Quelldatei unangetastet, bis der Betreiber die neue Datei übernimmt.

Keine Migrationsstufe darf neue Balancing-Zahlen erfinden. Ein nicht eindeutig überführbarer Wert
bricht die Migration mit Datei und Pfad ab.

## 5. Baseline und Analyse

Die `BalanceBaseline` ist ein Nachweis des aktuellen Bestands, kein Zielwertkatalog. Sie enthält:

- Quellen- und Schema-Versionen;
- stabile Szenario-ID;
- die verwendeten Content-IDs;
- aktuelle Zahlen und Einheiten;
- reproduzierbare Fixtures;
- Ergebnisdateien in CSV, JSON und/oder Markdown.

Das Werkzeug unter `tools/b16-balance/` liest YAML und Fixtures. Die Ausgaben dürfen für Tabellen
geöffnet werden, aber die Tabelle wird nicht zurück in die Runtime importiert. Spätere bewusst
geänderte Werte erhalten einen neuen Baseline-/Szenario-Identifier.

## 6. Was nicht in das Modell gehört

- Spieler- oder Charakterpersistenz;
- Audit-Einträge selbst;
- Übersetzungstexte;
- Paper-/Bukkit-Objekte;
- ein eigener Rollen- oder Editorzustand;
- neue Balanceziele, die vor den Tests nicht fachlich bestätigt wurden.
