# B16 Phase 0 — T001 Bestandsinventar (v1)

**Prüfdatum:** 2026-09-12
**Repository:** `C:\Users\Ticoo\Desktop\VuntexRPG\b15-performance-observability`
**Status:** geprüfte Bestandsaufnahme; keine Code-/YAML-Implementierung

## Scope und verbindliche Klassifikation

Im Scope sind die spielbestimmenden Content- und Balancing-Werte aus `classes.yml`,
`abilities.yml`, `progression.yml`, `combat.yml`, `zones.yml`, `mobs.yml`, `items.yml`,
`currency.yml` sowie der B16-Teilbereich `stats.yml` (Attribute). Ausgenommen bleiben
B12/B13/B14/B15/B02/B03-Dateien und Text-/Übersetzungsdaten.

Jeder inventarisierte Wert erhält genau eine vorläufige Kategorie aus der B16-Taxonomie. Die
Kategorie steht nie für mehrere Alternativen; offene Fachentscheidungen stehen ausschließlich in
einem separaten `decision=open`- beziehungsweise `Gap`-Feld.

| Kategorie | Bedeutung |
|---|---|
| **CONTENT** | Spielregel, Balancewert, ID oder fachlicher Default; grundsätzlich möglicher B16-Content. |
| **PROTECTION_BOUNDARY** | Sicherheits-, Speicher-, DoS-, Eingabe- oder unveränderliche Domänengrenze; kein automatisch rebalancierbarer Default. |
| **ALGORITHM_CONSTANT** | Form-, Umrechnungs- oder sonstige technische Konstante ohne nachgewiesene fachliche Content-Semantik. |
| **UNIT_DEFINITION** | Einheit- oder Skalierungsdefinition eines Feldes; keine eigene Balanceentscheidung. |
| **PLATFORM_PHYSICS** | Plattform-, Server- oder Physikverhalten außerhalb des B16-Content-Vertrags. |
| **UNKNOWN_CANDIDATE** | Kandidat, dessen fachliche Zuordnung aus dem Ist-Code nicht sicher hervorgeht. |

`rpg-content/src/main/resources` existiert am Prüfdatum noch nicht. Es ist der geplante Ziel-Owner
(**planned / not implemented**), keine Ist-Quelle.

## Aktuell geladene B16-Kandidaten

Jede Datei ist hier genau einmal gelistet. Die Kategorie der Dateizeile beschreibt die überwiegend
fachliche Content-Menge der gebündelten Datei und ist keine Mehrfachklassifikation einzelner
Felder. Feldgenaue Ausnahmen werden in der folgenden separaten Tabelle jeweils genau einer
Kategorie zugeordnet; `decision=open` steht ausschließlich in der Gap-Spalte.

| Vollständiger YAML-Pfad | Ist-Bestand / Einheit | Kategorie | decision / Gap | Ist-Consumer; geplanter Owner |
|---|---|---|---|---|
| `rpg-plugin/src/main/resources/classes.yml` | `classes.*`: Klassen-IDs, `base-stats`, `growth`, `armor-ladder`, `weapon-ladder`, `abilities`; Attribute in numerischen Stat-Punkten, Level-/Coin-Werte ganzzahlig. Genaue Feldsemantik siehe Glossar. | **CONTENT** | — | `rpg.core.classes`-Fachtypen; `rpg.persistence.classes.ClassesModule`. Owner `rpg-content` planned / not implemented. |
| `rpg-plugin/src/main/resources/abilities.yml` | `runtime.global-cooldown-ms=750`, Regenerationsfaktoren `0.20/0.35`, 18 Ability-IDs; Zeiten ms, Reichweiten Blöcke, Chancen/Faktoren dimensionslos, Mana/Schaden in Domäneneinheit. | **CONTENT** | — | `rpg.core.ability`-Fachtypen; `rpg.persistence.ability.AbilityModule`. Owner `rpg-content` planned / not implemented. |
| `rpg-plugin/src/main/resources/progression.yml` | `xp-curve` Level 2–60, `party.max-size=5`, `party.range-blocks=50.0`, Boni/Cap als Fraction, Invite `60 s`, Progress-Fenster `500 ms`. | **CONTENT** | — | `rpg.core.progression`-Fachtypen; `rpg.persistence.progression.ProgressionModule`. Owner `rpg-content` planned / not implemented. |
| `rpg-plugin/src/main/resources/combat.yml` | Combat-Timeout `8 s`, Attribution `max-attackers=16`, Attribution-Timeout `30 s`, Feedback-Fenster `500 ms`, Knockback `0.4`, Environment-/Mob-Defaults. | **CONTENT** | — | `rpg.core.combat` (`CombatModule`, Formeln). Owner `rpg-content` planned / not implemented. |
| `rpg-plugin/src/main/resources/zones.yml` | `provisional`, Fallback-Punkt, Warning-Cooldown `30 s`, Combat-Logout, sechs Zonen; Koordinaten/Areas in Blöcken, Preise Coins. | **CONTENT** | — | `rpg.core.zone`-Fachtypen. Owner `rpg-content` planned / not implemented. |
| `rpg-plugin/src/main/resources/mobs.yml` | Budgets `800/130/12/25` (server/zone/chunk/player), Horde `2000 ms`, Density Fraction `0.20`, Cleanup `60 s/96 blocks`, Retarget `500 ms`, Mob-/Horde-/Boss-IDs. `admin-spawn-limit=20` ist als serverweite `PROTECTION_BOUNDARY` für gleichzeitig registrierte `Origin.ADMIN`-Mobs inventarisiert. | **CONTENT** | confirmed | `rpg.core.mob`-Fachtypen. Owner `rpg-content` planned / not implemented; `mobs.yml` owns the admin limit, Java `20` is only a fallback. |
| `rpg-plugin/src/main/resources/items.yml` | Inventory-Warning `15000 ms`; Wear: Threshold `50 %`, Floor `0.20 Fraction`, Damage `0.01`, Death `10`, Min-Factor `100`, Warnings `50/25/10 %`, Cooldown `60000 ms`; Reparaturpreise, Templates, Loot, Vendors. | **CONTENT** | — | `rpg.core.item`-Fachtypen. Owner `rpg-content` planned / not implemented. |
| `rpg-plugin/src/main/resources/currency.yml` | Starting-Balance `0 Coins`; Drops default `4 Coins`, by-type, Despawn `120 s`, Merge-Radius `3.0 blocks`, Max-Piles `400`; Ledger-Retention `30 days`, History-Page `45`. | **CONTENT** | — | `rpg.core.currency`-Fachtypen; `rpg.persistence.currency.CurrencyModule`. Owner `rpg-content` planned / not implemented. |
| `rpg-plugin/src/main/resources/stats.yml` (`attributes` only) | Attribut-Basiswerte Health `100`, Regen `0`, Defense `0`, Mana `50`, Damage `5`, Attack-Speed `4`, Movement-Speed `0.1` sowie die jeweiligen Bänder/Cooldown-Fraction. `attributes.*.min/max` ist als feldweise Ausnahme unten inventarisiert. | **CONTENT** | — | `rpg.core.stats.StatConfig`, `rpg.core.stats.StatEngine`; `rpg.persistence.stats.StatsModule`. Owner `rpg-content` planned / not implemented. |

### Feldgenaue Ausnahmen mit eigener Kategorie

Diese Einträge gehören zu den oben aggregiert aufgeführten Dateien, erhalten aber wegen ihrer
anderen Semantik eine eigene, eindeutige Kategorie. Die fachliche Entscheidung bleibt offen,
ohne die Kategorie mit einer Alternative zu vermischen.

| Vollständiger YAML-Pfad | Ist-Wert / Einheit | Kategorie | decision / Gap | Hinweis |
|---|---:|---|---|---|
| `rpg-plugin/src/main/resources/mobs.yml:admin-spawn-limit` | `20` gleichzeitig registrierte `Origin.ADMIN`-Entities serverweit | **PROTECTION_BOUNDARY** | `decision=confirmed` | Jeder `/rpg mob spawn`-Aufruf setzt höchstens eine Kreatur; `mobs.yml` ist Owner, Java `20` bleibt Kompatibilitäts-Fallback. |
| `rpg-plugin/src/main/resources/stats.yml:attributes.*.min/max` | z. B. Health `1..2000`, Regen `0..40`, Defense `0..300`, Mana `0..500`, Damage `0..150`; übrige Grenzen siehe Datei | **PROTECTION_BOUNDARY** | `decision=open` | Ob diese Grenzen als veränderbare B16-Felder oder unveränderliche Schutzgrenzen geführt werden, wird vor Schema/Migration festgelegt. |

## Bewusst außerhalb B16

Die folgenden Dateien sind Ist-Betriebs-, UI-, Admin-, Text-, Persistenz-, Session- oder
Performance-Konfiguration und werden hier nicht als B16-Content inventarisiert:

`statistics.yml`, `ui.yml`, `commands.yml`, `performance.yml`, `persistence.yml`, `session.yml`,
`messages.yml`, `messages_de.yml`.

Dabei sind drei bestehende `readDocument()`-Ausnahmen ausdrücklich festzuhalten:

- `commands.yml` wird in `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java:3259-3272` als offenes
  Dokument gelesen und an `RateLimitConfig.from(...)` übergeben.
- `ui.yml` wird in `RpgPlugin.java:794-800` als offenes Dokument gelesen, um `language` früh zu
  bestimmen. `UiConfigSchema` prüft die Datei später im UI-Modul; der frühe `readDocument()`-Pfad
  ist selbst keine B16-Schema-Validierung.
- `messages.yml` beziehungsweise die gewählte Sprachdatei wird in `RpgPlugin.java:729` als
  offenes Dokument gelesen. Sie bleibt als Text-/Übersetzungsdaten außerhalb B16. Keine dieser
  drei Nutzungen belegt B16-Schema-Validierung.

## Evidence und Prüfgrenze

Aus dem Repository-Root ausführbar:

```powershell
rg -n "DEFAULT_CONFIG_FILES|messages\.yml|messages_de\.yml|readDocument" -- rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java
rg -n "class (ClassesModule|AbilityModule|ProgressionModule|CurrencyModule|StatsModule)" -- rpg-persistence/src/main/java
rg -n "^package |class (StatConfig|StatEngine)|interface StatEngine" -- rpg-core/src/main/java/rpg/core/stats
Get-ChildItem -LiteralPath rpg-plugin/src/main/resources -File -Filter '*.yml' | Select-Object -ExpandProperty FullName
if (Test-Path -LiteralPath rpg-content/src/main/resources) { Get-ChildItem -LiteralPath rpg-content/src/main/resources -Recurse } else { 'MISSING: rpg-content/src/main/resources' }
```

**Erwartete Kernaussage:** `DEFAULT_CONFIG_FILES` enthält die operativen Dateien einschließlich
`ui.yml` und `commands.yml`, nicht `messages*.yml`; die neun B16-Dateien liegen unter den genannten
Laufzeitpfaden; die Consumer-Packages liegen unter `rpg.core` beziehungsweise `rpg.persistence`;
der geplante `rpg-content`-Ressourcenpfad fehlt.

**Exit-/Ergebnisinterpretation:** `rg` Exit 0 bedeutet, dass das Suchmuster gefunden wurde, Exit 1
fehlende Evidenz und Exit 2 einen Kommando-/Argumentfehler. Der `Test-Path`-Zweig ist mit
`MISSING: ...` ein erwartetes Ist-Ergebnis, kein Fehler. Der Nachweis ist ein gezielter
Workspace-Snapshot, kein Vollständigkeitsbeweis.

## Coverage-Grenze und Phase 1

Das Inventar ist eine heuristische, pfad- und consumerbezogene Phase-0-Aufnahme. Sie beweist weder
ein vollständiges maschinenlesbares Feldinventar noch die vollständige AST-Semantik aller Java-
Literale, YAML-Pfade oder indirekten Verbraucher. Ein vollständiger maschinenlesbarer Inventar-
und AST-Semantik-Scan bleibt Phase-1-Arbeit; bis dahin gelten offene Fälle als `decision=open`.

## Copy-ready locations

- Default-Liste: `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java:128-145`
- `commands.yml` via `readDocument()`: `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java:3259-3272`
- `ui.yml` via `readDocument()`: `rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java:794-800`
- B16-Dateien: `rpg-plugin/src/main/resources/{classes,abilities,progression,combat,zones,mobs,items,currency,stats}.yml`
- Consumer-Packages: `rpg-persistence/src/main/java/rpg/persistence/{classes,ability,progression,currency,stats}`
- Stats-Typen: `rpg-core/src/main/java/rpg/core/stats/StatConfig.java`, `StatEngine.java`
- Geplanter Owner: `specs/016-content-config-balancing/data-model.md:34-49`

## Confidence + gaps

**Confidence: hoch** für Pfade, Package-Zuordnung, Default-Liste, die drei bestehenden
`readDocument()`-Pfade und die jetzt entschiedene Semantik von `admin-spawn-limit`. Offen bleiben
vollständige schemaweite Feldinventare, die Snapshot-Grenze und die spätere Entscheidung, welche
Stats-Minima/-Maxima konfigurierbar bleiben.

## Sources consulted

`specs/016-content-config-balancing/{spec,research,data-model,tasks}.md`,
`rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java`,
`rpg-plugin/src/main/java/rpg/plugin/command/framework/RateLimitConfig.java`, die neun B16-YAML-
Dateien, `rpg-core`-Config-Schemas/Core-Typen sowie `rpg-persistence`-Module.

## Geänderte Dateien

Diese Phase-0-Remediation ändert ausschließlich Dokumente:

- `specs/016-content-config-balancing/phase-0-t001-inventory-v1.md`
- `specs/016-content-config-balancing/phase-0-t002-java-literals-cross-domain-v1.md`
- `specs/016-content-config-balancing/phase-0-t003-config-contract.md`
- `specs/016-content-config-balancing/phase-0-t004-ownership.md`
- `specs/016-content-config-balancing/phase-0-units-glossary.md`
- `specs/016-content-config-balancing/tasks.md`

Kein Java-/YAML-Code, kein Commit und kein Push.
