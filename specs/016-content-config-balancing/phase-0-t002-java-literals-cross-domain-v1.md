# B16 Phase 0 — T002 Java-Literale und Cross-Domain-Prüfungen (v1)

**Prüfdatum:** 2026-09-12
**Repository:** `C:\Users\Ticoo\Desktop\VuntexRPG\b15-performance-observability`
**Status:** geprüfte Bestandsaufnahme; keine Java-/YAML-Implementierung

## Scope und Klassifikation

Der Scan erfasst gezielt spielbestimmende Java-Defaults, Validierungsgrenzen und technische
Konstanten. Jede Fundstelle erhält genau eine vorläufige Kategorie aus der B16-Taxonomie:
`CONTENT`, `PROTECTION_BOUNDARY`, `ALGORITHM_CONSTANT`, `UNIT_DEFINITION`, `PLATFORM_PHYSICS`
oder `UNKNOWN_CANDIDATE`. Offene Fachfragen werden nicht durch eine Mehrfachklassifikation gelöst,
sondern mit einem separaten `decision=open`- beziehungsweise `Gap`-Feld dokumentiert.

Der Scan ist heuristisch und kein Vollständigkeitsbeweis; insbesondere wird keine vollständige
Entfernung aller spielbestimmenden Literale behauptet.

## Vollständiger nachweisbarer Default-Block `CombatConfig.defaults()`

`CombatConfig.java:143-177` enthält alle in diesem gezielten Block nachweisbaren spielbestimmenden
Defaults. Der abschließende Default-Mob wird separat an `CombatConfig.java:181` erfasst. Die Tabelle
ist vollständig für diesen gezielten Bereich plus die Fall-Damage-Defaults, nicht für den gesamten
Java-Code.

| Java-Fundstelle | Ist-Wert | Einheit | Kategorie | YAML-Evidence |
|---|---:|---|---|---|
| `CombatConfig.java:145` | `FIRE 2.0` | Schadenseinheiten | **CONTENT** | `combat.yml:environment.fire` |
| `CombatConfig.java:146` | `FIRE_TICK 1.0` | Schadenseinheiten | **CONTENT** | `combat.yml:environment.fire-tick` |
| `CombatConfig.java:147` | `LAVA 8.0` | Schadenseinheiten | **CONTENT** | `combat.yml:environment.lava` |
| `CombatConfig.java:148-152` | `HOT_FLOOR 2.0`, `CAMPFIRE 2.0`, `DROWNING 3.0`, `SUFFOCATION 3.0`, `CONTACT 1.0` | Schadenseinheiten | **CONTENT** | `combat.yml:environment.*` |
| `CombatConfig.java:153-157` | `BLOCK_EXPLOSION 25.0`, `ENTITY_EXPLOSION 25.0`, `LIGHTNING 30.0`, `FALLING_BLOCK 20.0`, `FLY_INTO_WALL 6.0` | Schadenseinheiten | **CONTENT** | `combat.yml:environment.*` |
| `CombatConfig.java:158-162` | `FREEZE 2.0`, `DRYOUT 2.0`, `DRAGON_BREATH 6.0`, `SONIC_BOOM 20.0`, `WORLD_BORDER 2.0` | Schadenseinheiten | **CONTENT** | `combat.yml:environment.*` |
| `CombatConfig.java:166-170` | `ZOMBIE (80.0,10.0,10.0)`, `SKELETON (60.0,0.0,9.0)`, `CREEPER (50.0,0.0,0.0)`, `SPIDER (55.0,5.0,7.0)`, `ENDERMAN (200.0,20.0,25.0)` | Health/Defense/Physical-Damage | **CONTENT** | `combat.yml:mobs.by-type.*` |
| `CombatConfig.java:173` | `Duration.ofSeconds(8)` | seconds | **CONTENT** | `combat.yml:combat.combat-timeout-seconds` |
| `CombatConfig.java:174` | `max attackers 16` | Entities/Target | **CONTENT** | `combat.yml:combat.attribution.max-attackers` |
| `CombatConfig.java:175` | `Duration.ofSeconds(30)` | seconds | **CONTENT** | `combat.yml:combat.attribution.timeout-seconds` |
| `CombatConfig.java:176` | `Duration.ofMillis(500)` | milliseconds | **CONTENT** | `combat.yml:combat.feedback.aggregation-window-millis` |
| `CombatConfig.java:177` | `knockback 0.4` | dimensionsloser Strength-Faktor | **CONTENT** | `combat.yml:combat.feedback.knockback-strength` |
| `FallDamageConfig.defaults():38-40` | `3.0, 4.0, 200.0` | safe height in blocks, damage/block, max damage | **CONTENT** | `combat.yml:environment.fall.*` |
| `CombatConfig.java:181` | abschließender Konstruktor-Default `MobStats(60.0,0.0,8.0)` | Health/Defense/Physical-Damage | **CONTENT** | `combat.yml:mobs.default` |

## Weitere Literale und Grenzen

| Fundstelle | Wert / Einheit | Kategorie | decision / Gap |
|---|---:|---|---|
| `AbilityRuntime.java:30` | `MAX_COOLDOWN_REDUCTION=0.40` Fraction | **PROTECTION_BOUNDARY** | confirmed: Cap, nicht als freier Balancewert übernehmen. |
| `EffectSpec.java:60` | `METER_MAXIMUM=100.0` Meter | **PROTECTION_BOUNDARY** | confirmed: harte Obergrenze; kein belegter YAML-Default. |
| `BehindTargetCheck.java:25` | `DEFAULT_ANGLE=90.0` Grad | **ALGORITHM_CONSTANT** | `confirmed`: globale `rpg-core`-Regel für die hintere Hemisphäre; genau seitlich zählt nicht. Kein YAML-Owner vorerst. |
| `ProjectileEffect.java:51` | `DEFAULT_SPEED=1.6` blocks/tick | **PLATFORM_PHYSICS** | `confirmed`: technische `rpg-core`-/Plattform-Physik-Konstante. Kein YAML-Owner vorerst; `abilities.yml` enthält aktuell keinen `PROJECTILE`-Eintrag. |
| `ItemConfig.java:60-61` | `15 s` Inventory-full cooldown | **CONTENT** | confirmed: Duplikat von `items.yml:inventory.full-warning-cooldown-ms=15000`. |
| `WearCurve.java:60` | `100.0` Condition-% | **UNIT_DEFINITION** | confirmed: Ist-Skala des Wear-Zustands; `items.yml:wear` bleibt in `[0,100]`. |
| `CurrencyConfig.java:94` | `16.0` blocks Merge-Radius | **PROTECTION_BOUNDARY** | confirmed: Java-Obergrenze; `currency.yml:drops.merge-radius=3.0` ist **CONTENT**. |
| `CurrencyConfig.java:100-103` | `6000 ticks / 20 = 300 s` | **ALGORITHM_CONSTANT** | confirmed: Plattform-/Vanilla-Umrechnung; `currency.yml:drops.despawn-seconds=120` ist **CONTENT**. |
| `CombatConfig.java:45,79-82` | `MAX_ATTACKERS_CEILING=64` | **PROTECTION_BOUNDARY** | confirmed: Obergrenze, nicht der Default `16`. |
| `AttributeDefinition.java:26,66-71` | Health-Minimum `1.0` | **PROTECTION_BOUNDARY** | `decision=open`: Verhältnis zu `stats.yml:attributes.health.min=1.0` im B16-Schema festlegen. |
| `DamageMitigation.java:16` | Divisor `100.0` | **ALGORITHM_CONSTANT** | confirmed: Formelumrechnung; keine YAML-Quelle. |
| `DamageMitigation.java:26` | Mindestdivisor `1.0` | **PROTECTION_BOUNDARY** | confirmed: Guard; keine YAML-Quelle. |
| `Area.java:124` | `4_000_000` Chunks | **PROTECTION_BOUNDARY** | confirmed: Speicher-/DoS-Grenze außerhalb B16-Content. |
| `PaperMovementEffects.java:60,126,153` | `0.35` Y, `20.0/0.5/1.5` blocks | **PLATFORM_PHYSICS** | confirmed: Plattform-/Physikverhalten außerhalb B16. |
| `CloneAggroListener.java:48` | `64.0` blocks | **PLATFORM_PHYSICS** | confirmed: Plattform-/Physikgrenze außerhalb B16. |
| `RpgPlugin.java:310` | `2.0` blocks Vendor-Offset | **ALGORITHM_CONSTANT** | confirmed: technische Positionierung außerhalb B16-Content. |

Admin-Limits und Stats-Grenzen werden nicht pauschal als Balancewerte erklärt. `admin-spawn-limit`
ist als serverweite `PROTECTION_BOUNDARY` bestätigt; eine harte Stats-Unter- oder -Obergrenze ist
ebenfalls `PROTECTION_BOUNDARY`, während ein separat verwendeter Attribut-Default `CONTENT` ist.

Die Fraction-Skala im Glossar beschreibt die Einheit. Der konkrete Wert `0.40` aus
`AbilityRuntime.MAX_COOLDOWN_REDUCTION` ist davon getrennt als `PROTECTION_BOUNDARY` klassifiziert;
die übrigen Fraction-Beispiele bleiben feldbezogene Content- oder Einheitenskalierungen.

## Bestehende Cross-Domain-Prüfungen

Die Tabelle enthält Relationen statt eigener Zahlenwerte. Für die Phase-0-Konsistenz erhält auch
jede Relation genau eine vorläufige Kategorie; `decision`/Scope bleibt in der Statusspalte.

| Prüfung | Fundstelle | Kategorie | Results/Evidence am 2026-09-12 | B16-Status |
|---|---|---|---|---|
| Klassen → Ability | `RpgPlugin.java:868-879` | **CONTENT** | Klassenmodul vor Ability-Modul; IDs werden aufgelöst. | Einzelne Lade-/Validierungsprüfung vorhanden; B16-Snapshot planned / not implemented |
| Mob-Horde → Zone/Spawn-Area | `MobModule.java:64-75,106-127` | **CONTENT** | bekannte Zonen/Spawn-Areas werden geprüft. | Ist-Prüfung vorhanden; gemeinsamer Fehlervertrag geplant |
| Mob-Horde/Boss → Mob-Kind | `MobConfigSchema.java:163-229` | **CONTENT** | Kind-IDs und `boss=true` werden geprüft. | Ist-Prüfung vorhanden |
| Item → Template/Zone/Mob | `ItemConfigSchema.java:405-416`; `ItemModule.java:124-186` | **CONTENT** | Templates, Zonen und Mob-Kinds werden geprüft. | Ist-Prüfung vorhanden; Publish-Grenze offen |
| Mob-Kind → Messages | `MobModule.java:143-156` | **UNKNOWN_CANDIDATE** | Message-Key wird in `messages.yml` geprüft. | `decision=out-of-scope`: Text-/Übersetzungsdaten bleiben außerhalb B16 |
| Reload | `AbstractConfigLoader.java:49-57`; `RpgPlugin.java:543-571` | **UNKNOWN_CANDIDATE** | Quellen werden zuerst geladen/validiert, danach Handles veröffentlicht. | `decision=integration-contract`: vorhandener Handle-Reload; B16-Snapshot planned / not implemented |

## Reproduzierbare Prüfcommands

Die folgenden Nachweise sind für den Workspace-Snapshot am 2026-09-12 formuliert. Der verwendete
Scan ist heuristisch: Literale in Konstruktoren, Ressourcen und indirekten Formeln können fehlen.

```powershell
rg -n "environment\.put|new MobStats|Duration\.ofSeconds|Duration\.ofMillis|new CombatConfig" -- rpg-core/src/main/java/rpg/core/combat/CombatConfig.java
rg -n "static .*defaults|new FallDamageConfig" -- rpg-core/src/main/java/rpg/core/combat/FallDamageConfig.java
rg -n "MAX_|MIN_|DEFAULT_|Duration\.of|6000|4_000_000|64\.0" --glob '*.java' rpg-core/src/main/java rpg-platform/src/main/java rpg-plugin/src/main/java
rg -n "environment:|mobs:|timeout-seconds|aggregation-window-millis|knockback-strength|fall:" -- rpg-plugin/src/main/resources/combat.yml
rg -n "reloadAll|staged|publish|AtomicReference" -- rpg-core/src/main/java/rpg/core/config/AbstractConfigLoader.java
```

**Erwartete Kernaussage:** Die ersten beiden Kommandos zeigen den gezielt geprüften Combat-/Fall-
Damage-Block, der dritte liefert bewusst nur eine heuristische Trefferliste, das vierte die
zugehörigen YAML-Abschnitte und das fünfte Staging-/Publish-Stellen. Exit 0 bedeutet Treffer,
Exit 1 kein Treffer und Exit 2 Kommando-/Argumentfehler; ein Treffer ist kein AST-
Vollständigkeitsnachweis.

## Coverage-Grenze und Phase 1

Der Literal-Scan deckt nur die angegebenen Java-Pfade, Muster und den gezielt gelesenen
`CombatConfig.defaults()`-Block ab. Er beweist weder ein vollständiges maschinenlesbares
Literalinventar noch die semantische Zuordnung indirekter Formeln, Konstruktorargumente oder
Ressourcenwerte. Ein vollständiger maschinenlesbarer Inventar- und AST-Semantik-Scan bleibt
Phase-1-Arbeit; ebenso die abschließende Entscheidung für `UNKNOWN_CANDIDATE`-Fälle.

## Copy-ready locations

- Combat-Defaults: `rpg-core/src/main/java/rpg/core/combat/CombatConfig.java:143-177`
- Fall-Damage-Defaults: `rpg-core/src/main/java/rpg/core/combat/FallDamageConfig.java:38-40`
- Guards: `rpg-core/src/main/java/rpg/core/combat/CombatConfig.java:45,79-82`
- Referenzprüfungen: `rpg-core/src/main/java/rpg/core/mob/MobModule.java:106-127` und `rpg-core/src/main/java/rpg/core/item/ItemModule.java:124-186`
- Reload: `rpg-core/src/main/java/rpg/core/config/AbstractConfigLoader.java:49-57`

## Confidence + gaps

**Confidence: hoch** für den gezielt gelesenen CombatConfig.defaults()-Block, Fall-Damage-Defaults,
Guard-Literale und vorhandene Referenz-/Reload-Stellen. **Gaps:** Der repoweite Literal-Scan ist
heuristisch; Literale in Konstruktoren, Ressourcen und indirekten Formeln können fehlen. AST-/
Semantik-Vollständigkeitsprüfung, gemeinsame Snapshot-API und Laufzeitnachweise fehlen.

## Sources consulted

`specs/016-content-config-balancing/{spec,research,data-model,tasks}.md`, CombatConfig.java,
FallDamageConfig.java, gezielte `rpg-core`-/`rpg-platform`-/`rpg-plugin`-Scans, relevante
Config-Schemas, Module und `AbstractConfigLoader.java`.

## Geänderte Dateien

Diese Phase-0-Remediation ändert ausschließlich Dokumente:

- `specs/016-content-config-balancing/phase-0-t001-inventory-v1.md`
- `specs/016-content-config-balancing/phase-0-t002-java-literals-cross-domain-v1.md`
- `specs/016-content-config-balancing/phase-0-t003-config-contract.md`
- `specs/016-content-config-balancing/phase-0-t004-ownership.md`
- `specs/016-content-config-balancing/phase-0-units-glossary.md`
- `specs/016-content-config-balancing/tasks.md`

Kein Java-/YAML-Code, kein Commit und kein Push.
