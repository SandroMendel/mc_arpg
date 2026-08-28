# Vertrag · `items.yml`

**Spec:** [../spec.md](../spec.md) · **Datenmodell:** [../data-model.md](../data-model.md)

Alles, was ein Betreiber ändern können muss, steht hier — und **kein Bezeichner einer einzelnen
Vorlage steht im Code** (FR-013). Ein Test erzwingt das, wie `ConfigOnlyMobTest` es für Mob-Arten
tut.

Fehler beim Start führen zu **Fail-Fast mit Datei, Schlüssel und Grund** (FR-012). Ein Server, der
mit einer halb verstandenen Konfiguration startet, ist schlimmer als einer, der es nicht tut.

---

```yaml
# B11 - Items, Ausruestung & Loot.
#
# Was ein Gegenstand ist, steht vollstaendig hier. Eine neue Vorlage entsteht durch Bearbeiten
# dieser Datei und einen Neustart, ohne eine Zeile Java (SC-002).
#
# AUSRUESTUNG STEHT NICHT HIER. Ruestung und Waffe sind Klassenprogression und stehen in
# classes.yml (ADR-017). Eine Beutetabelle, die Ausruestung nennt, ist ein Startfehler.

# ---------------------------------------------------------------------------------------------
# Verschleiss. Der Tod wiegt schwerer als der Alltag - und das ist eine Startpruefung,
# keine Zahlenwahl (FR-044).
# ---------------------------------------------------------------------------------------------
wear:
  # Oberhalb dieses Zustands traegt die Ausruestung ihren vollen Wert. Leichter Verschleiss
  # kostet nichts.
  threshold: 50.0
  # Was bei Zustand 0 uebrig bleibt. 0.20 heisst: bis zu 80 % Wirkungsverlust (FR-047).
  # Ausruestung zerbricht NIE - B07 haelt sie unzerstoerbar (FR-038).
  floor: 0.20

  # Zustandspunkte je Schadenspunkt. Ruestung nach ERLITTENEM Schaden, gemessen VOR der Abwehr
  # (FR-040a): am durchgekommenen gemessen entstuende eine Abwaertsspirale.
  per-damage-taken: 0.01
  # Waffe nach AUSGETEILTEM Schaden - nur Autoattacks (MELEE, PROJECTILE). Faehigkeiten schonen
  # sie, und ein Klon nutzt gar nichts ab (FR-041, FR-041a).
  per-damage-dealt: 0.01
  # Der Tod, auf beide Werte. Zehn Tode fuehren von voll auf null.
  per-death: 10.0
  # Der Start weist eine Konfiguration zurueck, in der per-death kleiner ist als
  # per-damage-taken mal diesem Faktor. Ohne die Pruefung koennte ein spaeteres Balancing die
  # Todesstrafe aus ADR-017 stillschweigend aushebeln.
  death-factor-min: 100

  warn-at: [50.0, 25.0, 10.0]
  warn-cooldown-ms: 60000

# ---------------------------------------------------------------------------------------------
# Preise. Der Kontostand liegt in B08b, die Preise bei dem, der sie verlangt (ADR-027).
# ---------------------------------------------------------------------------------------------
repair:
  # Voller Preis je Stufe bei Zustand 0; er sinkt linear mit dem fehlenden Anteil (FR-053),
  # damit eine Reparatur bei 99 nicht dasselbe kostet wie eine bei 5.
  base-per-tier: [0, 40, 120, 400, 1200, 3000]

# ---------------------------------------------------------------------------------------------
# Vorlagen. Zwei Kategorien - mehr gibt es nicht (FR-017).
# "Aufstiegsmaterial" entfaellt: der Aufstieg kostet Level und Coins, beides steht in
# classes.yml und wird von B08b geprueft (ADR-039).
# ---------------------------------------------------------------------------------------------
templates:

  potion.minor-healing:
    category: CONSUMABLE
    material: 'POTION'
    # Reines Etikett. Ein epischer Trank heilt nicht mehr als ein gewoehnlicher; er ist
    # seltener (ADR-027).
    rarity: COMMON
    sell-price: 3
    effect:
      heal: 40.0
      cooldown-ms: 8000

  potion.lesser-mana:
    category: CONSUMABLE
    material: 'POTION'
    rarity: COMMON
    sell-price: 3
    min-level: 5
    effect:
      mana: 30.0
      cooldown-ms: 8000

  potion.stoneskin:
    category: CONSUMABLE
    material: 'SPLASH_POTION'
    rarity: RARE
    sell-price: 45
    min-level: 20
    effect:
      # Ueber SourceKind.BUFF, zeitstempelbasiert ausgewertet (FR-034).
      buff: { defense: 12.0 }
      duration-ms: 45000
      cooldown-ms: 120000

  trim.ember:
    category: COSMETIC
    material: 'NETHERITE_UPGRADE_SMITHING_TEMPLATE'
    rarity: LEGENDARY
    sell-price: 0
    # Eine "Trimfarbe" ist ein benanntes Paar, nicht zwei getrennt waehlbare Felder.
    # Anwendbar erst auf der Hoechststufe der Leiter (FR-069) - sonst waeren Schurken- und
    # Kriegerstufen optisch ununterscheidbar, was B07s FR-016 verbietet.
    appearance:
      trim-material: 'REDSTONE'
      trim-pattern: 'RAISER'

# ---------------------------------------------------------------------------------------------
# Beutetabellen. Je Art, sonst je Region; der Boss hat seine eigene (FR-018, FR-023).
#
# Vanillas eigene Drops sind bereits unterdrueckt - B05s CombatDeathListener raeumt getDrops()
# und setzt setDroppedExp(0) (research.md R3). Hier steht, was STATTDESSEN faellt.
#
# Die Stueckzahl ist der EINZIGE Zufall in diesem Block. Die Werte des gefallenen Gegenstands
# sind fest, weil sie aus der Vorlage kommen (FR-021, ADR-027).
# ---------------------------------------------------------------------------------------------
loot:
  by-zone:
    greenfields:
      - { template: 'potion.minor-healing', chance: 0.08, min: 1, max: 1 }
    dustlands:
      - { template: 'potion.minor-healing', chance: 0.10, min: 1, max: 2 }
      - { template: 'potion.lesser-mana',   chance: 0.06, min: 1, max: 1 }

  # Gewinnt gegen die Regionstabelle. Zwei Arten auf derselben Vanilla-Basis lassen
  # unterschiedliche Beute fallen - nach ART, nicht nach Basistyp (FR-020).
  by-kind:
    greenfields.rotling:
      - { template: 'potion.minor-healing', chance: 0.12, min: 1, max: 1 }

  # Getrennt von der Region: seltener und hoeherwertig (FR-023).
  by-boss:
    greenfields.warden-of-the-field:
      - { template: 'potion.stoneskin', chance: 0.50, min: 1, max: 2 }
      - { template: 'trim.ember',       chance: 0.02, min: 1, max: 1 }

# ---------------------------------------------------------------------------------------------
# Die sechs NPCs. Einer je Region im Safe-Core; sie kaufen an, verkaufen, reparieren und
# fuehren den Stufenaufstieg durch (FR-057).
#
# Hier steht nur, was ein NPC VERKAUFT. Was er ANKAUFT, steht als sell-price an der Vorlage -
# sonst gaebe es sechs Orte fuer denselben Betrag.
# ---------------------------------------------------------------------------------------------
vendors:
  greenfields:
    stock:
      - { template: 'potion.minor-healing', price: 12 }
  pale-wilds:
    stock:
      - { template: 'potion.stoneskin', price: 180 }
      - { template: 'trim.ember',       price: 25000 }
```

---

## Startprüfungen im Einzelnen

| Prüfung | Bei Verstoß | Anforderung |
|---|---|---|
| `material` existiert als Vanilla-Material | Fail-Fast | FR-009 |
| `rarity` ist eine der acht | Fail-Fast | FR-014 |
| `effect` genau bei `CONSUMABLE`, `appearance` genau bei `COSMETIC` | Fail-Fast | FR-017 |
| ein `CONSUMABLE` hat mindestens eine Wirkung | Fail-Fast | FR-036 |
| jede `template`-Kennung in `loot` und `vendors` existiert | Fail-Fast | FR-024 |
| `chance` in `(0, 1]`, `min ≤ max`, `min ≥ 1` | Fail-Fast | FR-021 |
| `per-death ≥ per-damage-taken × death-factor-min` | Fail-Fast | **FR-044** |
| `threshold` in `(0, 100]`, `floor` in `[0, 1)` | Fail-Fast | FR-047 |
| `base-per-tier` hat so viele Einträge wie die längste Leiter | Fail-Fast | FR-053 |
| jede Zone in `loot.by-zone` und `vendors` existiert in `zones.yml` | Fail-Fast | — |
| jede Art in `loot.by-kind` existiert in `mobs.yml` | Fail-Fast | — |

**Nachladen** tauscht die Konfiguration im Ganzen, wie `MobModule` es tut. Ein Exemplar in einem
Spielerinventar wird dabei **nicht angefasst** — es trägt nur die Vorlagen-ID, und die zeigt nach
dem Nachladen auf die neuen Werte (FR-003, SC-001).
