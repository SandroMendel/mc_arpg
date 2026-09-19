# B16 Phase 1 · T007 Schlüssel- und Registry-Policy

Status: **Dokumentations- und Datenvertrag für T007** (2026-09-12). Diese Datei leitet
Schlüsselgrenzen aus T001–T004, `data-model.md`, `spec.md`, den aktuellen
`rpg-content`-Defaults, den neun entsprechenden Legacy-YAMLs und den aktuellen
Config-Schemas/-Modulen ab. Sie entscheidet keine alten Balancingfragen und ergänzt
keine neuen Werte oder Mechaniken.

## Geltungsbereich und Statussprache

B16 umfasst genau diese neun gebündelten YAML-Laufzeitnamen:

| Laufzeitname | stabiler Dokumenttyp / Domäne | aktuelle versionierte Quelle | entsprechende Legacy-Quelle |
|---|---|---|---|
| `classes.yml` | Klassen-Dokument / `classes` | `rpg-content/src/main/resources/classes.yml` | `rpg-plugin/src/main/resources/classes.yml` |
| `abilities.yml` | Fähigkeiten-Dokument / `abilities` | `rpg-content/src/main/resources/abilities.yml` | `rpg-plugin/src/main/resources/abilities.yml` |
| `progression.yml` | Progressions-Dokument / `progression` | `rpg-content/src/main/resources/progression.yml` | `rpg-plugin/src/main/resources/progression.yml` |
| `combat.yml` | Kampf-/Formel-Dokument / `combat` | `rpg-content/src/main/resources/combat.yml` | `rpg-plugin/src/main/resources/combat.yml` |
| `zones.yml` | Zonen-Dokument / `zones` | `rpg-content/src/main/resources/zones.yml` | `rpg-plugin/src/main/resources/zones.yml` |
| `mobs.yml` | Mob-Dokument / `mobs` | `rpg-content/src/main/resources/mobs.yml` | `rpg-plugin/src/main/resources/mobs.yml` |
| `items.yml` | Item-Dokument / `items` | `rpg-content/src/main/resources/items.yml` | `rpg-plugin/src/main/resources/items.yml` |
| `currency.yml` | Währungs-Dokument / `currency` | `rpg-content/src/main/resources/currency.yml` | `rpg-plugin/src/main/resources/currency.yml` |
| `stats.yml` | Stats-Dokument, nur B16-Teilbereich / `stats.attributes` | `rpg-content/src/main/resources/stats.yml` | `rpg-plugin/src/main/resources/stats.yml` |

Der strukturelle Vergleich der neun versionierten Dateien mit ihren Legacy-Dateien
ist außerhalb der zusätzlichen Root-Zeile `schemaVersion: 1` identisch. Die
Legacy-Dateien bleiben unversionierter v0-Bestand; diese Policy behauptet nicht,
dass sie bereits die neue Hülle laden.

Die übrigen Betriebs-, UI-, Text- und Übersetzungsdateien
(`statistics.yml`, `ui.yml`, `messages*.yml`, `commands.yml`, `performance.yml`,
`persistence.yml`, `session.yml`) sind kein B16-Dokument. Die bestehenden relativen
Laufzeitnamen im Plugin-Datenordner bleiben erhalten.

Die Kennzeichnungen bedeuten:

- **fixed**: Der Schlüssel ist Teil des Dokumentvertrags. Ein unbekannter Schlüssel
  an diesem Pfad ist ein Validierungsfehler.
- **dynamic registry ID**: Ein dynamischer Schlüssel oder ID-Wert ist nur am
  ausdrücklich genannten Registry-Pfad frei. Außerhalb dieser Registry ist derselbe
  Text ein unbekannter fester Schlüssel und damit ein Fehler.
- **planned / not implemented**: Zielregel aus T007 oder einer Folgeaufgabe, die im
  gelesenen B16-Ist-Code noch nicht abgesichert ist.
- **decision=open**: Eine fachliche Entscheidung ist nicht getroffen. Diese Policy
  wählt dafür keinen Wert und keine Mechanik.
- **implemented**: Nur dort verwendet, wo die konkrete Parser-/Schema-Eigenschaft
  im Ist-Code belegt ist; daraus wird keine vollständige B16-Implementierung
  abgeleitet.

## Gemeinsame Dokumenthülle

Jede der neun Dateien ist genau ein Dokumenttyp und hat eine einzige Root-Map:

```yaml
schemaVersion: 1
<feste fachliche Root-Abschnitte>:
  <Registry- oder Abschnittsinhalt>
```

| Root-Regel | Policy | Status |
|---|---|---|
| `schemaVersion` | **fixed**, reservierter Root-Schlüssel, erforderlich und genau einmal am Root; die Zielversion ist `1`. | `rpg-content`: vorhanden; gemeinsame Runtime-Prüfung `planned / not implemented`; `ConfigSchema.schemaVersion()` existiert generisch. |
| Dokumenttyp | Wird aus dem festen Laufzeitnamen und der festen Domänenzuordnung abgeleitet. Es wird kein zusätzlicher YAML-Schlüssel `documentType` erfunden. | `planned / not implemented` |
| Root-Form | Nur eine Map; Root-Abschnitte stehen ausschließlich in der jeweiligen Tabelle unten. | `planned / not implemented` |
| Root-Unbekannte | Jeder Root-Schlüssel außer `schemaVersion` und dem jeweiligen festen Abschnitt ist ein Fehler. | `planned / not implemented` |
| Legacy ohne Version | Normaler Start/Reload lehnt die unversionierte Datei ab. Nur ein expliziter v0→v1-Migrationsweg darf Hülle und Version ergänzen; die Quelle bleibt unangetastet. | `planned / not implemented` |

Der serverfreie Vertrag in `rpg-content/src/main/java/rpg/content/` macht für alle
neun Dateien `schemaVersion`, feste Root-/Strukturpfade und dynamische Registry-
Pfade typisiert und unveränderlich abrufbar. Er lädt kein YAML und verdrahtet keinen
Runtime-Loader.

## Feste Schlüssel und dynamische Registries je Datei

Die folgenden Root-, Abschnitts- und Eintragsnamen sind **fixed**. Unbekannte Werte
an diesen geschlossenen Pfaden sind Fehler. Wo ein Abschnitt dynamische IDs enthält,
ist das ausschließlich in der Registry-Spalte angegeben. Die Listen nennen nur
Felder, die in den aktuellen YAMLs oder den aktuellen fachlichen Config-Schemas/
Bindern belegt sind; sie legen keine Pflicht-/Optional-Semantik, Typen, Bereiche
oder Einheiten für T009–T011 vorweg.

### `classes.yml`

| Pfad | Klassifikation |
|---|---|
| `schemaVersion`, `classes` | fixed Root-Schlüssel |
| `classes.<classId>` | dynamic registry ID: `ClassId`; das aktuelle Schema kennt den code-seitig festgelegten Klassensatz |
| `classes.<classId>.display-name-key`, `.menu-material`, `.base-stats`, `.growth`, `.armor-ladder`, `.weapon-ladder`, `.abilities` | fixed Eintragsfelder |
| `base-stats` und `growth` mit `health`, `healthRegen`, `defense`, `mana`, `manaRegen`, `physicalDamage`, `magicDamage`, `attackSpeed`, `movementSpeed`, `abilityCooldown` | fixed Feldschlüssel |
| `armor-ladder[]` und `weapon-ladder[]` mit `material`, `required-level`, `values`, `cost` | fixed Eintragsfelder |
| Leiter-Appearance mit `color`, `trim-material`, `trim-pattern` | fixed optionale Feldschlüssel; im aktuellen Klassenbestand belegt |
| Leiter-Appearance `model-data` | fixed optionaler Schema-Feldschlüssel; im aktuellen `rpg-content`-Bestand nicht belegt, aber im Binder deklariert |
| `armor-ladder[].values` mit `health`, `defense`, `mana`, `movementSpeed`; `weapon-ladder[].values` mit `physicalDamage`, `magicDamage`, `attackSpeed`, `abilityCooldown` | fixed aktuelle Wertefelder |
| `armor-ladder[].cost`, `weapon-ladder[].cost` | fixed Kosten-Map; `coins` ist als aktueller verschachtelter Bestand beobachtet, weitere Kosten-Schlüssel werden vom aktuellen Binder absichtlich opak behandelt (`planned / not implemented` für eine B16-Schließung) |
| `abilities[]` mit `id`, `kind`, `unique`, `unlock-level` | fixed Eintragsfelder; `unique` ist optional, `id` dort Referenzwert und kein Mapping-Schlüssel |

### `abilities.yml`

| Pfad | Klassifikation |
|---|---|
| `schemaVersion`, `runtime`, `abilities` | fixed Root-/Abschnittsschlüssel |
| `runtime.global-cooldown-ms`, `runtime.regeneration`, `runtime.regeneration.health-combat-factor`, `.mana-combat-factor` | fixed Abschnitts-/Feldschlüssel |
| `abilities.<abilityId>` | dynamic registry ID: `AbilityId` |
| `abilities.<abilityId>.kind`, `.display-name-key`, `.description-key`, `.item`, `.trigger`, `.player-toggle`, `.cooldown-ms`, `.cast-time-ms`, `.sustained`, `.exclusive`, `.duration-ms`, `.max-rank`, `.rank-cost`, `.target`, `.effects`, `.mana-cost`, `.charges`, `.charge-window-ms`, `.requires-behind-target`, `.open-world-only`, `.interrupt-on-move`, `.chance` | fixed Eintragsfelder; mehrere davon sind optionale Binder-Felder |
| `item` und `trigger` | fixed Feldschlüssel; der aktuelle Binder akzeptiert jeweils Einzelwert oder Liste |
| `target.mode` | fixed Feldschlüssel |
| `target.range`, `.angle`, `.max-targets`, `.hop-range`, `.area-radius`, `.height` | fixed optionale Target-Felder; `angle` ist im aktuellen Binder deklariert, aber nicht im ausgelieferten Content belegt |
| `effects[]` mit `type`, `amount`, `per-rank`, `duration-ms`, `interval-ms`, `max-stacks`, `stack-cap`, `attribute`, `damage-type`, `origins`, `status-effect`, `build-per-hit`, `idle-before-ms`, `decay-per-second`, `as-fraction`, `when` | fixed Varianteneinträge gemäß aktuellem Binder; unbekannte Variantenschlüssel sind Fehler, eine vollständige fachliche Varianten-/Pflichttabelle bleibt T009/T010 |
| `rank-cost` | fixed Kosten-Map; `coins` ist im aktuellen Content beobachtet, der Binder behandelt innere Schlüssel absichtlich opak; keine weiteren Nested-Keys werden erfunden |

### `progression.yml`

| Pfad | Klassifikation |
|---|---|
| `schemaVersion`, `xp-curve`, `level-growth`, `mob-xp`, `party`, `progress-event` | fixed Root-/Abschnittsschlüssel |
| `xp-curve.<levelId>` | dynamic registry ID: numerischer `LevelId`, ausschließlich unter `xp-curve` |
| `level-growth` mit `health`, `healthRegen`, `defense`, `mana`, `manaRegen`, `physicalDamage`, `magicDamage`, `attackSpeed`, `movementSpeed`, `abilityCooldown` | fixed Feldschlüssel |
| `mob-xp.default`, `mob-xp.by-type` | fixed Feld-/Abschnittsschlüssel |
| `mob-xp.by-type.<vanillaMobType>` | dynamic type-map ID, ausschließlich in dieser Registry; vorhandener Zielraum ist der Vanilla-Mobtyp, keine B16-`MobKindId` |
| `party.max-size`, `.range-blocks`, `.bonus-per-member`, `.bonus-cap`, `.invite-timeout-seconds`; `progress-event.window-millis` | fixed Feldschlüssel |

### `combat.yml`

| Pfad | Klassifikation |
|---|---|
| `schemaVersion`, `combat`, `environment`, `mobs` | fixed Root-/Abschnittsschlüssel |
| `combat.combat-timeout-seconds`, `.attribution`, `.feedback` | fixed Feldschlüssel/Abschnitte |
| `combat.attribution.max-attackers`, `.timeout-seconds`; `combat.feedback.aggregation-window-millis`, `.knockback-strength` | fixed Feldschlüssel |
| `environment.fall`, `.fire`, `.fire-tick`, `.lava`, `.hot-floor`, `.campfire`, `.drowning`, `.suffocation`, `.contact`, `.block-explosion`, `.entity-explosion`, `.lightning`, `.falling-block`, `.fly-into-wall`, `.freeze`, `.dryout`, `.dragon-breath`, `.sonic-boom`, `.world-border` | fixed Umgebungs-Schlüssel |
| `environment.fall.safe-blocks`, `.damage-per-block`, `.max-damage` | fixed Feldschlüssel |
| `mobs.default`, `mobs.by-type` | fixed Feld-/Abschnittsschlüssel |
| `mobs.default.health`, `.defense`, `.physical-damage`; `mobs.by-type.<vanillaMobType>.health`, `.defense`, `.physical-damage` | fixed Mob-Felder; der `<vanillaMobType>`-Teil ist nur in der folgenden Registry frei |
| `mobs.by-type.<vanillaMobType>` | dynamic type-map ID, ausschließlich unter `mobs.by-type`; nicht mit `mobs.yml`-`MobKindId` gleichsetzen |

### `zones.yml`

| Pfad | Klassifikation |
|---|---|
| `schemaVersion`, `provisional`, `fallback-point`, `warning-cooldown-seconds`, `combat-logout`, `zones` | fixed Root-/Abschnittsschlüssel |
| `fallback-point.world`, `.x`, `.y`, `.z` | fixed Feldschlüssel |
| `zones.<zoneId>` | dynamic registry ID: `ZoneId` |
| `zones.<zoneId>.world`, `.start-region`, `.level-band`, `.pvp`, `.area`, `.safe-core`, `.crystal`, `.spawn-areas` | fixed Zonenfelder |
| `zones.<zoneId>.level-band.min`, `.max` | fixed Feldschlüssel |
| `zones.<zoneId>.area[]` und `.safe-core.area[]` mit `min-x`, `min-z`, `max-x`, `max-z` | fixed Geometriefelder |
| `zones.<zoneId>.safe-core.respawn-point.x`, `.y`, `.z` | fixed Punktfelder |
| `zones.<zoneId>.crystal.key`, `.price`, `.trigger-area[]` | fixed Feld-/Abschnittsschlüssel; `crystal.key` ist kein automatisch erfundener B16-Registry-Verweis |
| `zones.<zoneId>.crystal.trigger-area[]` mit `min-x`, `min-y`, `min-z`, `max-x`, `max-y`, `max-z` | fixed Geometriefelder |
| `zones.<zoneId>.spawn-areas[]` mit `key`, `area`; `spawn-areas[].area[]` mit `min-x`, `min-z`, `max-x`, `max-z` | fixed Listen-/Geometriefelder |
| `zones.<zoneId>.spawn-areas[].key` | dynamic `SpawnAreaId`-Wert innerhalb der Spawn-Area-Registry dieser Zone; die umgebenden Listen-/Geometriefelder bleiben fixed |

### `mobs.yml`

| Pfad | Klassifikation |
|---|---|
| `schemaVersion`, `admin-spawn-limit`, `budget`, `horde`, `kinds`, `hordes` | fixed Root-/Abschnittsschlüssel; `admin-spawn-limit` ist als serverweite `PROTECTION_BOUNDARY` entschieden |
| `budget.server-wide`, `.per-zone`, `.per-chunk`, `.per-player`; `horde.respawn-interval-ms`, `.density-per-player`, `.cleanup-after-seconds`, `.cleanup-radius`, `.retarget-interval-ms` | fixed Feldschlüssel |
| `kinds.<mobKindId>` | dynamic registry ID: `MobKindId` |
| `kinds.<mobKindId>.base`, `.level`, `.attributes`, `.follow-range`, `.xp`, `.coins`, `.boss` | fixed Mob-Kind-Felder; `boss` ist ein optionales Feld des aktuellen Schemas/Contents |
| `kinds.<mobKindId>.attributes` mit `health`, `defense`, `physicalDamage` | fixed aktuelle Attributschlüssel; weitere Attribute werden nicht als neue Nested-Regel erfunden |
| `hordes.<zoneId>` | dynamic registry ID: `ZoneId`, ausschließlich unter `hordes` |
| `hordes.<zoneId>.areas` | fixed Abschnittsschlüssel |
| `hordes.<zoneId>.areas.<spawnAreaId>` | dynamic `SpawnAreaId`, ausschließlich als benannter Area-Schlüssel innerhalb dieser Horde-Registry |
| `hordes.<zoneId>.areas.<spawnAreaId>[]` mit `kind`, `.weight` | fixed Eintragsfelder; `kind` ist ein Referenzfeld |
| `hordes.<zoneId>.boss` mit `kind`, `.area`, `.offset`, `.respawn-minutes` | fixed optionale Boss-Abschnittsfelder |
| `hordes.<zoneId>.boss.offset` mit `x`, `y`, `z` | fixed Punktfelder; die Werte sind im Binder optional und erhalten den belegten Default |

### `items.yml`

| Pfad | Klassifikation |
|---|---|
| `schemaVersion`, `inventory`, `wear`, `repair`, `templates`, `loot`, `vendors` | fixed Root-/Abschnittsschlüssel |
| `inventory.full-warning-cooldown-ms` | fixed optionaler Feldschlüssel gemäß aktuellem Item-Schema |
| `wear.threshold`, `.floor`, `.per-damage-taken`, `.per-damage-dealt`, `.per-death`, `.death-factor-min`, `.warn-at`, `.warn-cooldown-ms` | fixed Feldschlüssel; `warn-at[]` bleibt eine feste Zahlenliste |
| `repair.base-per-tier` | fixed Feldschlüssel; der Wert bleibt eine feste Zahlenliste |
| `templates.<itemTemplateId>` | dynamic registry ID: `ItemTemplateId` |
| `templates.<itemTemplateId>.category`, `.material`, `.rarity`, `.min-level`, `.bound-class`, `.sell-price`, `.model-data`, `.effect`, `.appearance` | fixed Item-Template-Felder; `min-level`, `bound-class`, `sell-price`, `model-data`, `effect` und `appearance` sind optionale Binder-Felder |
| `templates.<itemTemplateId>.effect` mit `heal`, `mana`, `buff`, `duration-ms`, `cooldown-ms` | fixed Effektfelder |
| `templates.<itemTemplateId>.effect.buff.<attributeKey>` | fixed `buff`-Container; aktuelle Einträge belegen `defense`, `physical_damage` und `attack_speed`. Die zulässige Attribute-Auflösung liegt im aktuellen Binder; keine zusätzliche freie Registry wird hier erfunden |
| `templates.<itemTemplateId>.appearance.trim-material`, `.trim-pattern` | fixed Appearance-Felder; aktuelle Item-Templates belegen beide |
| `loot.by-zone`, `.by-kind`, `.by-boss` | fixed Loot-Abschnitte |
| `loot.by-zone.<zoneId>` | dynamic `ZoneId`, ausschließlich innerhalb `loot.by-zone` |
| `loot.by-kind.<mobKindId>` | dynamic `MobKindId`, ausschließlich innerhalb `loot.by-kind` |
| `loot.by-boss.<mobKindId>` | dynamic `MobKindId`, ausschließlich innerhalb `loot.by-boss` |
| Loot-Eintrag `template`, `chance`, `min`, `max`; `vendors.<zoneId>.stock[].template`, `.price` | fixed Eintragsfelder; die ID-Werte in `template` sind Referenzen, keine freien Abschnittsschlüssel |
| `vendors.<zoneId>` | dynamic `ZoneId`, ausschließlich innerhalb `vendors`; `stock` ist fixed |

### `currency.yml`

| Pfad | Klassifikation |
|---|---|
| `schemaVersion`, `account`, `drops`, `ledger`, `history` | fixed Root-/Abschnittsschlüssel |
| `account.starting-balance`; `drops.default`, `.by-type`, `.despawn-seconds`, `.merge-radius`, `.max-piles`; `ledger.retention-days`; `history.page-size` | fixed Feldschlüssel |
| `drops.by-type.<vanillaMobType>` | dynamic type-map ID, ausschließlich in dieser Registry; Vanilla-Mobtyp, keine `MobKindId` |

### `stats.yml`

| Pfad | Klassifikation |
|---|---|
| `schemaVersion`, `attributes` | fixed Root-/B16-Teilbereich |
| `attributes.<attributeId>` | dynamic registry ID: `AttributeId`, ausschließlich innerhalb `attributes` |
| `attributes.<attributeId>.base`, `.min`, `.max` | fixed Eintragsfelder; Schutzgrenzen und veränderbare Content-Felder bleiben gemäß T001 `decision=open` |
| `attributes.<attributeId>.modifier-band` | fixed optionaler Eintragsfeldschlüssel; im aktuellen Content für `attackSpeed` und `movementSpeed` belegt |

Weitere Root-Abschnitte von `stats.yml` werden durch diese B16-Teilbereichs-Policy
nicht freigegeben. Dasselbe gilt für nicht in den Tabellen genannte freie Maps.

## Unknown-Key-, Registry- und Duplicate-Key-Regeln

1. **Fixed-Key-Regel:** Root-, Abschnitts- und Eintragsfelder aus den Tabellen sind
   geschlossen. Ein unbekannter Schlüssel wird mit Datei und vollständigem
   YAML-Pfad abgelehnt; er wird weder ignoriert noch in eine Rest-Map übernommen.
2. **Registry-Ausnahme:** Ein freier Mapping-Schlüssel oder dynamischer ID-Wert ist
   ausschließlich an einem als `dynamic registry ID` oder `dynamic type-map ID`
   markierten Pfad erlaubt. Ein `zoneId` unter `classes` oder ein `mobKindId` unter
   einem festen Abschnitt ist deshalb kein zulässiger dynamischer Schlüssel.
3. **Referenzwert ist kein Mapping-Schlüssel:** Ein `id`, `kind`, `area`, `key` oder
   `template` innerhalb eines festen Listen-/Eintragsfeldes ist ein Referenz- oder
   Wertfeld. Es erweitert nicht den erlaubten Schlüsselraum seines Containers.
4. **Keine ID-Ableitung:** Registry-IDs bleiben stabile Referenzen. Sie werden nicht
   aus Anzeige-, Nachrichten- oder Vanilla-Materialwerten, Zeilennummern oder
   Reihenfolge abgeleitet. Eine konkrete neue ID oder ein neues Regexformat wird
   durch diese Policy nicht festgelegt.
5. **YAML-Duplicate-Key:** Derselbe Mapping-Schlüssel darf innerhalb eines
   YAML-Mappings nicht zweimal vorkommen. Es gibt kein Merge-, Last-write-wins-
   oder stilles Überschreibungsverhalten. `YamlConfigLoader` setzt hierfür bereits
   SnakeYAML `allowDuplicateKeys=false`; diese Parsergrenze ist **implemented**.
6. **Doppelte Listen-IDs:** Zwei Einträge mit demselben fachlichen `id`-/`key`-
   Wert in einer listenförmigen Registry müssen als Duplicate-ID abgelehnt werden.
   Das ist eine semantische Registry-Regel und nicht automatisch ein YAML-
   Duplicate-Key; die B16-weite Umsetzung ist **planned / not implemented**.
7. **Fehlerpfad:** Die spätere B16-Validierung nennt mindestens Datei, exakten
   YAML-Pfad, Erwartung und Istwert. Die generische `SchemaValidator`-Schicht ist
   vorhanden; die geschlossene B16-Unknown-Key-Prüfung ist **planned / not implemented**.

## Vorgesehene Cross-Domain-Referenzfelder

Die folgenden Felder sind Referenzbeziehungen aus dem aktuellen Bestand und den
T002-Prüfungen. Ein unbekannter Zielwert ist ein Fehler; es gibt keinen leeren
Optional-Fallback. Wo die Zielprüfung noch nicht als gemeinsamer B16-Snapshot-
vertrag existiert, ist das ausdrücklich markiert.

| Quellfeld | Ziel-Registry / Zielpfad | Regel und Status |
|---|---|---|
| `classes.<classId>.abilities[].id` | `abilities.<abilityId>` | Ability muss existieren. Einzelne Auflösung im Plugin ist belegt; gemeinsamer B16-Snapshot ist `planned / not implemented`. |
| `mobs.hordes.<zoneId>.areas.<spawnAreaId>[].kind` | `mobs.kinds.<mobKindId>` | Mob-Kind muss existieren. Bestehende Mob-Prüfung ist belegt; gemeinsamer Fehler-/Publish-Vertrag ist `planned / not implemented`. |
| `mobs.hordes.<zoneId>.boss.kind` | `mobs.kinds.<mobKindId>` | Boss-Kind muss existieren und die vorhandene Boss-Semantik erfüllen; gemeinsame Snapshot-Prüfung `planned / not implemented`. |
| `mobs.hordes.<zoneId>.areas.<spawnAreaId>` und `.boss.area` | `zones.<zoneId>.spawn-areas[].key` | Zone und Spawn-Area müssen zusammenpassen. Der Zielpfad ist die `zones`-Registry direkt unter dem Root; kein `zones.zones.<zoneId>`. Gemeinsamer B16-Publishvertrag `planned / not implemented`. |
| `items.loot.by-zone.<zoneId>` | `zones.<zoneId>` | Loot-Quellschlüssel muss eine Zone referenzieren; gemeinsame B16-Validierung `planned / not implemented`. |
| `items.loot.by-kind.<mobKindId>` | `mobs.kinds.<mobKindId>` | Loot-Quellschlüssel muss ein Mob-Kind referenzieren; gemeinsame B16-Validierung `planned / not implemented`. |
| `items.loot.by-boss.<mobKindId>` | `mobs.kinds.<mobKindId>` mit Boss-Eigenschaft | Boss-Loot muss ein vorhandenes Boss-Mob-Kind referenzieren; gemeinsame B16-Validierung `planned / not implemented`. |
| `items.loot.*[].template` und `items.vendors.<zoneId>.stock[].template` | `items.templates.<itemTemplateId>` | Template muss existieren. Bestehende Item-Prüfung ist belegt; gemeinsamer B16-Publishvertrag `planned / not implemented`. |
| `items.vendors.<zoneId>` | `zones.<zoneId>` | Händlerbereich muss eine vorhandene Zone referenzieren; gemeinsame B16-Validierung `planned / not implemented`. |
| `*.display-name-key`, `*.description-key` in Content-Einträgen | Schlüsselraum von `messages.yml` | Textreferenz, aber `messages.yml` bleibt außerhalb B16. Kein B16-Registry-Schlüssel und keine neue B16-Text-API. |

`material`, `item`, `menu-material`, `base` und `trim-material` in den aktuellen
Dateien sind vorhandene Vanilla-/Plattformattribute, nicht implizit erzeugte
B16-IDs. Ebenso sind `vanillaMobType`-Schlüssel in den ausdrücklich genannten
Type-Maps keine `MobKindId`-Registry; sie bleiben an ihren jeweiligen Type-Map-
Pfad gebunden.

## Implementierungsgrenzen, API und bewusst offene Regeln

- `ContentKeyPolicy.defaultPolicy()` beschreibt exakt die neun Dateinamen. Die
  unveränderlichen `ContentDocumentContract`-Daten stellen reservierte Root-
  Schlüssel, feste Root-/Strukturpfade und `ContentRegistryContract`-Einträge
  bereit; `document(fileName)` ist ein exakter Dateinamen-Lookup.
- Die API validiert nicht-null/nicht-leere Dateinamen und Pfade, lehnt Duplikate ab
  und verlangt `schemaVersion` in `reservedRootKeys`. Sie importiert weder Bukkit,
  Paper, SnakeYAML noch Plugin-Code und lädt keine Ressource.
- T005/T006 liefern die versionierten Defaults wert- und verhaltensneutral. Die
  YAMLs bleiben Laufzeitquelle; diese T007-API ist kein zweiter Parser und keine
  Runtime-Verdrahtung.
- Die Policy baut auf den vorhandenen neutralen `rpg-core`-Verträgen
  (`ConfigSchema`, `FieldDefinition`, `SchemaValidator`, `ConfigHandle`,
  `AbstractConfigLoader`) und dem YAML-Parser in `rpg-platform` auf. Sie erfindet
  keine zusätzliche Parser- oder Loader-API.
- Fachbesitz, typisierte Registries, geschlossene Unknown-Key-Validierung und
  Cross-Domain-Snapshot-Publish liegen im Zielbild bei `rpg-content`; Bootstrap,
  Laufzeitnamen und Reload bleiben beim Plugin. Diese Runtime-Struktur ist
  **planned / not implemented**, soweit oben nicht ausdrücklich `implemented` ausgewiesen.
- Bewusst offen bleiben Schutzgrenzen in `stats.yml`, globale Cooldowns, Casting-Zeiten,
  Raritäten, Affixe, eine vollständige Ability-
  Variantentabelle, die Schließung opaker Kosten-/Buff-Maps und neue
  Balancing-Ziele. Sie werden hier weder durch Platzhalter noch durch neue Werte
  entschieden; sie bleiben Bestand oder `decision=open` gemäß T001–T004.
