# 03 · Glossar

Verbindliche Begriffe. **Die englische Spalte ist der Bezeichner im Code, in
Config-Keys und in Spielertexten.** Die deutsche Spalte dient nur der internen
Verständigung.

## Attribute

| Deutsch | Englisch (Code) | Bedeutung |
|---|---|---|
| Leben | `health` / HP | Eigener Lebenswert, Vanilla-Health nur als Anzeige |
| Verteidigung | `defense` | Mindert eingehenden Schaden |
| Mana | `mana` | Ressource für aktive Fähigkeiten |
| Physischer Schaden | `physicalDamage` | Basis für Waffen-/Nahkampfschaden |
| Magischer Schaden | `magicDamage` | Basis für Fähigkeitsschaden |
| Angriffsgeschwindigkeit | `attackSpeed` | Angriffe pro Zeiteinheit |
| Bewegungsgeschwindigkeit | `movementSpeed` | Laufgeschwindigkeit |
| Abklingzeit | `abilityCooldown` | Reduktion der Fähigkeiten-Cooldowns |

## Domänenbegriffe

| Deutsch | Englisch (Code) | Bedeutung |
|---|---|---|
| Klasse | `PlayerClass` | Eine der drei wählbaren Spielerklassen |
| Fähigkeit | `Ability` | Passive oder aktive Fertigkeit |
| Aktive Fähigkeit | `ActiveAbility` | Vom Spieler ausgelöst, kostet Mana, hat Cooldown |
| Passive Fähigkeit | `PassiveAbility` | Dauerhaft wirksam, liefert Modifier oder Trigger |
| Unique Class Ability | `UniqueAbility` | Signaturfähigkeit, genau eine je Klasse |
| Zone / Gebiet | `Zone` | Räumlich definierter Bereich mit eigenen Regeln |
| Welt | `World` | Bukkit-Welt; **nicht** gleichbedeutend mit Zone |
| Erfahrungslevel | `level` | Eigenes Levelsystem, unabhängig von Vanilla-XP |
| Erfahrung | `experience` / XP | Eigener XP-Wert, unabhängig von Vanilla-XP |
| Stat-Modifikator | `StatModifier` | Beitrag einer Quelle zu einem Attribut |
| Stat-Momentaufnahme | `StatSnapshot` | Berechnetes, unveränderliches Attributset |
| Item-Vorlage | `ItemTemplate` | Definition eines Items inkl. Wertebereiche |
| Gewürfelter Wert | `roll` | Konkreter Wert einer Instanz innerhalb der Vorlage |
| Beutetabelle | `LootTable` | Drop-Wahrscheinlichkeiten je Mob/Zone |
| Horde | `horde` / `wave` | Zusammenhängende Gruppe gleichzeitig gespawnter Mobs |
| Spawn-Budget | `spawnBudget` | Obergrenze aktiver Mobs je Zone/Chunk/Spieler |
| Sitzung | `PlayerSession` | Im Speicher gehaltener Zustand eines Online-Spielers |

## Abgrenzungen, die durchgehalten werden müssen

- **HP ≠ Vanilla-Health.** Vanilla-Health ist reine Darstellung (siehe ADR-003).
- **XP ≠ Vanilla-XP.** Die Vanilla-Erfahrungsleiste wird nicht als
  Fortschrittsspeicher benutzt.
- **Zone ≠ World.** Siehe ADR-006.
- **Ability ≠ Item.** Skill-Leisten-Items sind nur Eingabemethode, nicht Träger
  der Fähigkeitslogik.
