# Vertrag · `classes.yml`

Aufbau wie `progression.yml` aus B06 und `combat.yml` aus B05: Felddeklaration über den
Schema-Builder, Bindefunktionen prüfen die Zusagen, der erste Verstoß bricht den Start mit benannter
Ursache ab (Prinzip V, Fail-Fast).

## Beispiel — gekürzt auf eine vollständige Klasse

```yaml
# Genau die drei bekannten Klassen-IDs. Eine unbekannte bricht den Start ab, eine
# fehlende ebenso (FR-005). Die Menge steht im Code, der Inhalt hier (ADR-019).
classes:

  WARRIOR:
    display-name-key: class.warrior.name      # "Berserker" steht in der Nachrichtendatei
    menu-material: NETHERITE_SWORD

    # Alle acht Pflicht, auch die drei mit Null. Ein fehlendes Feld ist ein Startfehler,
    # kein stilles Null - dasselbe Argument wie bei level-growth in B06.
    base-stats:
      health: 40.0
      defense: 4.0
      mana: 12.0
      physicalDamage: 2.0
      magicDamage: 0.5
      attackSpeed: 0.0
      movementSpeed: 0.0
      abilityCooldown: 0.0

    # Ersetzt level-growth aus progression.yml fuer diese Klasse, ergaenzt es nicht.
    growth:
      health: 9.7
      defense: 1.5
      mana: 0.9
      physicalDamage: 0.7
      magicDamage: 0.1
      attackSpeed: 0.0
      movementSpeed: 0.0
      abilityCooldown: 0.0

    # Liste, nicht Feld je Stufe: nur so lassen sich 5, 6 und 7 Stufen ausdruecken.
    # Traegt die vier defensiven Attribute.
    # movement-speed steht in ABSOLUTEN Einheiten auf Basis 0.1, nicht in Prozent.
    armor-ladder:
      - material: LEATHER
        required-level: 1
        values: { health: 60.0, defense: 6.0, mana: 18.0, movementSpeed: 0.000 }
        cost: {}                               # Stufe 1 ist der Start, kostet nichts
      - material: COPPER
        required-level: 15
        values: { health: 280.0, defense: 40.0, mana: 35.0, movementSpeed: 0.001 }
        cost: { coins: 500 }                   # von B07 NICHT ausgelegt (FR-021)
      - material: IRON
        required-level: 29
        values: { health: 600.0, defense: 90.0, mana: 65.0, movementSpeed: 0.002 }
        cost: { coins: 2500 }
      - material: DIAMOND
        required-level: 42
        values: { health: 975.0, defense: 150.0, mana: 95.0, movementSpeed: 0.004 }
        cost: { coins: 10000 }
      - material: NETHERITE
        required-level: 55
        values: { health: 1385.0, defense: 205.0, mana: 130.0, movementSpeed: 0.005 }
        cost: { coins: 40000 }

    # Traegt die vier offensiven Attribute. Sechs Stufen - eine mehr als die Ruestung.
    # attack-speed steht in ABSOLUTEN Einheiten auf Basis 4.0: 0.60 sind +15 %.
    weapon-ladder:
      - material: WOODEN_SWORD
        required-level: 1
        values: { physicalDamage: 3.0, magicDamage: 0.5, attackSpeed: 0.00, abilityCooldown: 0.00 }
        cost: {}
      - material: STONE_SWORD
        required-level: 13
        values: { physicalDamage: 16.0, magicDamage: 3.0, attackSpeed: 0.12, abilityCooldown: 0.04 }
        cost: { coins: 400 }
      - material: COPPER_SWORD
        required-level: 24
        values: { physicalDamage: 35.0, magicDamage: 6.0, attackSpeed: 0.24, abilityCooldown: 0.08 }
        cost: { coins: 1800 }
      - material: IRON_SWORD
        required-level: 34
        values: { physicalDamage: 60.0, magicDamage: 10.0, attackSpeed: 0.36, abilityCooldown: 0.12 }
        cost: { coins: 6000 }
      - material: DIAMOND_SWORD
        required-level: 45
        values: { physicalDamage: 85.0, magicDamage: 14.0, attackSpeed: 0.48, abilityCooldown: 0.16 }
        cost: { coins: 20000 }
      - material: NETHERITE_SWORD
        required-level: 55
        values: { physicalDamage: 105.0, magicDamage: 19.0, attackSpeed: 0.60, abilityCooldown: 0.20 }
        cost: { coins: 60000 }

    # Genau vier ACTIVE und zwei PASSIVE; die Unique zaehlt als eine der Aktiven.
    # B07 benennt nur - B08 loest die IDs auf (FR-044).
    abilities:
      - { id: warrior.rage,      kind: PASSIVE, unlock-level: 1 }
      - { id: warrior.shield,    kind: ACTIVE,  unlock-level: 5 }
      - { id: warrior.leap,      kind: ACTIVE,  unlock-level: 15 }
      - { id: warrior.lifesteal, kind: PASSIVE, unlock-level: 25 }
      - { id: warrior.whirl,     kind: ACTIVE,  unlock-level: 35 }
      - { id: warrior.call_of_the_berserker, kind: ACTIVE, unique: true, unlock-level: 45 }

  ROGUE:
    display-name-key: class.rogue.name
    menu-material: GOLDEN_SWORD
    # ... base-stats und growth wie in spec.md
    armor-ladder:
      - { material: LEATHER,   required-level: 1,  values: {...}, cost: {} }
      - { material: GOLDEN,    required-level: 13, values: {...}, cost: {...} }
      - { material: CHAINMAIL, required-level: 24, values: {...}, cost: {...} }
      # Ab hier bleibt das Material gleich - der Trim traegt die Stufe (FR-016a).
      # Gold und Kettenhemd sind nicht faerbbar, deshalb Trim und nicht Farbe.
      - { material: CHAINMAIL, required-level: 34, trim-material: COPPER,    trim-pattern: RIB,     values: {...}, cost: {...} }
      - { material: CHAINMAIL, required-level: 45, trim-material: AMETHYST,  trim-pattern: SILENCE, values: {...}, cost: {...} }
      - { material: CHAINMAIL, required-level: 55, trim-material: NETHERITE, trim-pattern: VEX,     values: {...}, cost: {...} }
    weapon-ladder:
      # Auch ein Schwert - unterschieden nur durch GOLDEN auf Stufe 3 statt COPPER.
      - { material: WOODEN_SWORD, required-level: 1, values: {...}, cost: {} }
      # ...
    abilities: []      # leer erlaubt bis B08; teilweise gefuellt NICHT (FR-045)

  MAGE:
    display-name-key: class.mage.name
    menu-material: NETHERITE_SPEAR
    armor-ladder:
      # Durchgehend Leder - die FARBE traegt die Stufe (FR-016a). Sieben Stufen.
      - { material: LEATHER, required-level: 1,  color: 0x4a4a52, values: {...}, cost: {} }
      - { material: LEATHER, required-level: 11, color: 0x1f3a93, values: {...}, cost: {...} }
      - { material: LEATHER, required-level: 20, color: 0x6b3fa0, values: {...}, cost: {...} }
      - { material: LEATHER, required-level: 29, color: 0xb5299b, values: {...}, cost: {...} }
      - { material: LEATHER, required-level: 38, color: 0xe8952f, values: {...}, cost: {...} }
      - { material: LEATHER, required-level: 46, color: 0x21d4c4, values: {...}, cost: {...} }
      - { material: LEATHER, required-level: 55, color: 0xf5f2e8, values: {...}, cost: {...} }
    weapon-ladder:
      - { material: WOODEN_SPEAR, required-level: 1, values: {...}, cost: {} }
      # ... sieben Stufen bis NETHERITE_SPEAR
    abilities: []
```

## Einheiten

Jeder Leiterwert wird auf den **Basiswert** seines Attributs addiert und steht in dessen eigener
Einheit, nicht in Prozent. `attackSpeed` hat Basiswert 4,0 und `movementSpeed` 0,1 - ein Wert von
`0.30` bedeutet dort +7,5 % beziehungsweise +300 %, nicht +30 %. Die Umrechnungstabelle steht im Kopf
von `classes.yml` und in `spec.md`.

## Felddeklaration

| Schlüssel | Typ | Regel |
|---|---|---|
| `classes` | `MAP` | Schlüssel müssen genau `WARRIOR`, `MAGE`, `ROGUE` sein |
| `classes.<ID>.display-name-key` | `STRING` | nicht leer, muss in der Nachrichtendatei existieren |
| `classes.<ID>.menu-material` | `STRING` | gültiges Vanilla-Material |
| `classes.<ID>.base-stats.<attr>` | `DOUBLE` | alle acht Pflicht, endlich |
| `classes.<ID>.growth.<attr>` | `DOUBLE` | alle acht Pflicht, endlich, ≥ 0 |
| `classes.<ID>.armor-ladder` | `LIST` | ≥ 2 Einträge |
| `classes.<ID>.weapon-ladder` | `LIST` | ≥ 2 Einträge |
| `classes.<ID>.abilities` | `LIST` | leer oder genau 6 |

## Zusagen, die die Bindefunktion prüft

Jede bricht den Start ab und nennt Klasse, Leiter und Stufe. Keine wird stillschweigend geheilt.

| Nr. | Zusage | Anforderung |
|---|---|---|
| V1 | Genau die drei bekannten Klassen-IDs, keine fehlt, keine ist unbekannt | FR-005 |
| V2 | Alle acht Basiswerte und alle acht Zuwachsraten vorhanden und endlich | FR-001, FR-002 |
| V3 | Jede Leiter hat mindestens zwei Stufen | FR-013 |
| V4 | Die vier Attribute des Slots sind je Stufe vollständig | FR-015 |
| V5 | Jedes getragene Attribut ist über die Stufen **streng steigend** | FR-017 |
| V6 | Levelanforderungen streng steigend, erste = 1, letzte ≤ Maximallevel | FR-018 |
| V7 | Erscheinungsbild jeder Stufe unterscheidet sich von der Vorstufe im Tripel Material/Farbe/Trim | FR-016 |
| V8 | Wo das Material der Vorstufe gleicht, ist Farbe oder Trim gesetzt | FR-016a |
| V9 | `color` nur auf färbbarem Material; `LEATHER` ja, `GOLDEN` und `CHAINMAIL` nein | FR-016b |
| V10 | `trim-material` und `trim-pattern` gemeinsam gesetzt oder gemeinsam leer | FR-016 |
| V11 | Ein Rüstungssatz außer dem Einstiegsmaterial erscheint in höchstens einer Klasse | FR-016c |
| V12 | Materialien existieren in der laufenden Server-Version | Prinzip V |
| V13 | Der effektive Wert auf Level 60 Endstufe liegt zwischen `min` und `max` des Attributs aus `stats.yml`. **Nicht** gegen das Modifikatorband geprueft: das begrenzt Modifikatoren um den effektiven Basiswert, und die Klasse verschiebt den Basiswert selbst | FR-008 |
| V14 | Endwert je Attribut innerhalb der Caps aus ADR-008 | FR-008 |
| V15 | `abilities` leer oder genau vier `ACTIVE` und zwei `PASSIVE` | FR-041, FR-045 |
| V16 | Höchstens eine `unique`, und sie ist `ACTIVE` | FR-041 |
| V17 | Jede `unlock-level` ≥ 1 und ≤ Maximallevel | FR-042 |
| V18 | `cost` ist eine Karte — Inhalt wird **nicht** geprüft | FR-021 |
| V19 | Keine gespeicherte Stufe eines bestehenden Charakters liegt über der konfigurierten Länge | FR-024 |

V19 ist die einzige Zusage, die die Datenbank braucht. Sie läuft beim Start nach der Migration und vor
dem ersten Spielerbeitritt — die Reihenfolge ist Teil der Zusage, sonst wäre ein Charakter schon
geladen, wenn der Fehler auffällt.

## Was ausdrücklich **nicht** geprüft wird

- **Der Inhalt von `cost`.** B07 kennt keine Coins und keine Preise (Workflow-Regel 5). Eine Prüfung
  hätte eine Kopplung an B11 erzeugt, die es noch nicht geben darf.
- **Ob eine Fähigkeits-ID existiert.** B08 löst sie auf. B07 prüft nur Anzahl, Art und Stufe.
- **Ob die Farben unterscheidbar *aussehen*.** Zwei ähnliche Grautöne sind eine Balancing-Frage, kein
  Startfehler. Geprüft wird Ungleichheit, nicht Wahrnehmbarkeit.
