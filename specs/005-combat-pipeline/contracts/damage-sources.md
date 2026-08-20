# Vertrag: Vanilla-Schadensquellen

**Feature**: `specs/005-combat-pipeline` | **Datum**: 2026-08-20

ADR-003 verlangt für **jede** Vanilla-Schadensquelle eine ausdrückliche Entscheidung. Der
Blocksteckbrief nennt 17; Paper 26.2 kennt **33**. Die 16 fehlenden sind hier ergänzt und
entschieden — ohne das wären sie stillschweigend durchgelaufen.

Umgesetzt als **vollständiger Switch** über `DamageCause`, nicht als Liste: eine fehlende Konstante
meldet der Compiler, nicht der Betrieb.

## Die drei Behandlungen

| Behandlung | Bedeutung |
|---|---|
| **MAPPED** | Vanilla-Schaden auf null, eigener Schaden aus `combat.yml` angewandt. Verteidigung greift nicht (FR-012b). |
| **DISABLED** | Vanilla-Schaden auf null, kein eigener Schaden. Wirkungslos. |
| **LETHAL** | Ziel stirbt sofort, unabhängig vom Lebenswert. |

## Die Tabelle

### Kampf

| Ursache | Behandlung | Begründung |
|---|---|---|
| `ENTITY_ATTACK` | eigener Pfad | Nahkampf — geht durch die volle Pipeline, nicht durch die Umgebungszuordnung. |
| `PROJECTILE` | eigener Pfad | Pfeile und Wurfgeschosse (FR-024a). |
| `ENTITY_SWEEP_ATTACK` | DISABLED | Der Vanilla-Flächenschlag würde neben dem eigenen Nahkampf ein zweites Mal treffen. Flächenwirkung ist B08. |
| `THORNS` | DISABLED | Verzauberungsmechanik. Reflektierter Schaden gehört zu B11, wenn überhaupt. |

### Umgebung — abgebildet

| Ursache | Behandlung | Anmerkung |
|---|---|---|
| `FALL` | MAPPED | Wächst mit der Fallhöhe (FR-012c). |
| `FIRE` | MAPPED | In Feuer stehen. |
| `FIRE_TICK` | MAPPED | Brennen. |
| `LAVA` | MAPPED | |
| `HOT_FLOOR` | MAPPED | Magma-Block. |
| `CAMPFIRE` | MAPPED | Vom Blocksteckbrief nicht genannt. |
| `DROWNING` | MAPPED | |
| `SUFFOCATION` | MAPPED | Ersticken im Block. |
| `CONTACT` | MAPPED | Kaktus, Süßbeerenstrauch. |
| `BLOCK_EXPLOSION` | MAPPED | TNT, Betten. |
| `ENTITY_EXPLOSION` | MAPPED | Creeper. Trifft er andere Mobs, verfällt deren Schaden (FR-042a). |
| `LIGHTNING` | MAPPED | |
| `FALLING_BLOCK` | MAPPED | Amboss, Sand. Vom Blocksteckbrief nicht genannt. |
| `FLY_INTO_WALL` | MAPPED | Elytra gegen die Wand. Nicht genannt. |
| `FREEZE` | MAPPED | Pulverschnee. Nicht genannt. |
| `DRYOUT` | MAPPED | Wassertier an Land. Nicht genannt. |
| `DRAGON_BREATH` | MAPPED | Nicht genannt. |
| `SONIC_BOOM` | MAPPED | Warden. Nicht genannt. |
| `WORLD_BORDER` | MAPPED | Nicht genannt. |

### Statuseffekte und Vanilla-Systeme — abgeschaltet

| Ursache | Behandlung | Begründung |
|---|---|---|
| `MAGIC` | DISABLED | Instant Damage, Schadenstränke. Vanilla-Statuseffekt. |
| `POISON` | DISABLED | |
| `WITHER` | DISABLED | |
| `STARVATION` | DISABLED | Sättigung ist von B04 ohnehin fixiert (ADR-013). |
| `MELTING` | DISABLED | Schmelzender Schneegolem. Nicht genannt, für dieses Spiel bedeutungslos. |
| `CRAMMING` | DISABLED | Vanilla tötet zu dicht stehende Wesen. Auf einem Server mit 800 Mobs würde das ganze Horden auslöschen. Wie viele Mobs wo stehen dürfen, entscheidet B10 über Spawn-Grenzen — nicht der Schadenspfad. |
| `CUSTOM` | DISABLED | Der Kanal, über den ein anderes Plugin Schaden einspeisen würde. B05 benutzt ihn selbst nicht: eigener Schaden geht nie durch das Vanilla-Ereignis. |

### Tödlich

| Ursache | Behandlung | Begründung |
|---|---|---|
| `VOID` | LETHAL | ADR-003 und ausdrückliche Festlegung. |
| `KILL` | LETHAL | Administrationswerkzeug, muss verlässlich bleiben (FR-015). |
| `SUICIDE` | LETHAL | `/kill` auf sich selbst. |

### Der Standardfall

Jede Ursache, die hier nicht steht — weil ein Minecraft-Update sie hinzugefügt hat — wird
**DISABLED** behandelt und **einmal je Ursache** protokolliert:

```
[combat] unmapped damage cause FROBNICATION - neutralised. Decide its treatment in
         VanillaDamageMapping (ADR-003 requires an explicit decision per source).
```

Das dreht die Richtung des Risikos um: Ein Update kann keinen Schaden durchlassen, sondern erzeugt
eine Aufforderung zur Entscheidung. Die Alternative — unbekanntes durchlassen — wäre ein Spieler,
der aus unerfindlichem Grund stirbt.

## Nicht zu verwechseln: `DamageModifier`

Paper kennt zusätzlich `DamageModifier` mit den Werten `BASE`, `ARMOR`, `BLOCKING`, `RESISTANCE`,
`MAGIC`, `ABSORPTION`, `HARD_HAT`, `FREEZING`, `INVULNERABILITY_REDUCTION`. Das sind **keine
Quellen**, sondern Vanillas eigene Minderungsstufen.

B05 fasst sie nicht einzeln an: Da der Vanilla-Grundschaden auf null gesetzt wird, ist jede
Minderung darauf ebenfalls null. Das ist gleichzeitig die Umsetzung der Festlegung, dass
Vanilla-Rüstung und Vanilla-Schilde wirkungslos bleiben — sie sind Modifikatoren auf einem Wert, den
es nicht mehr gibt.

## Zusätzliche Vanilla-Abschaltungen

Über die Quellentabelle hinaus schaltet B05 drei Vanilla-Verhalten ab, die sonst am Schadenspfad
vorbei wirken würden:

| Was | Warum |
|---|---|
| `noDamageTicks` auf 0 | Vanilla macht ein Wesen nach jedem Treffer zehn Ticks unverwundbar — ein zweites, verstecktes Angriffszeitfenster, das die eigene `attackSpeed` stillschweigend bei zwei Treffern je Sekunde deckelt (research.md E6). |
| Gameregel `keep_inventory` auf `true` | Sonst fällt beim Tod das gesamte Inventar, und die festgelegte Todesstrafe (Ausrüstungsschaden durch B11) wäre daneben bedeutungslos (FR-029b). |
| Erfahrung und Beute beim Tod | `setDroppedExp(0)` und `getDrops().clear()` — zwei Fortschrittssysteme nebeneinander sind eines zu viel (FR-030a, FR-030b). |

Bereits von B04 erledigt und hier **nicht** erneut angefasst: natürliche Regeneration und
Sättigung (ADR-013).
