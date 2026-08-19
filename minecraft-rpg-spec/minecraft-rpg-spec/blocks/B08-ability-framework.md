# B08 · Fähigkeiten-Framework

| | |
|---|---|
| **Schicht** | 1 — Regel-Engine |
| **Status** | Entwurf — umfangreichster Block |
| **Abhängig von** | B04, B05, B07 |
| **Benötigt von** | B13 |

## Zweck

Passive und aktive Fähigkeiten je Klasse sowie je Klasse eine **Unique Class
Ability**. Ziel ist ein Framework, in dem neue Fähigkeiten durch Konfiguration
entstehen — nicht durch eine neue Java-Klasse je Fähigkeit.

## Vier klar getrennte Ebenen

1. **Ability-Definition** — deklarativ in Konfiguration: Kosten, Cooldown,
   Anforderungen, Zusammensetzung aus Effekten
2. **Effect-Primitives** — wiederverwendbare Bausteine: Damage, Heal, Dash,
   Knockback, AoE, Projectile, Buff, Debuff, Summon, Teleport, Shield
3. **Targeting** — Selbst, Blickrichtung, Cursor-Ziel, Radius, Kegel, Linie,
   nächstes Ziel
4. **Runtime** — Mana-Kosten, Cooldown, Casting/Channeling, Unterbrechung,
   globale Auslösesperre

## Fähigkeitstypen

| Typ | Umsetzung |
|---|---|
| Passiv | Registriert Modifikatoren (B04) oder reagiert auf Pipeline-Stufen (B05) |
| Aktiv | Wird ausgelöst, kostet Mana, hat Cooldown |
| Unique Class Ability | Aktive Fähigkeit mit Sonderregeln — z. B. langer Cooldown, Ressourcenaufbau, einmalig je Kampf |

## Festgelegte Anforderungen

- Jede der drei Klassen hat passive **und** aktive Fähigkeiten
- Jede Klasse hat genau eine Unique Class Ability
- Vanilla-Client (ADR-005): Auslösung nur über Hotbar-Slots, Links-/Rechtsklick,
  Sneak-Kombination und Offhand-Swap — **keine eigenen Keybinds**

## Architekturvorgaben

- **Cooldowns und Mana-Regeneration werden zeitstempelbasiert lazy berechnet.**
  Keine wiederkehrenden Tasks pro Spieler, kein Herunterzählen pro Tick.
- Eine ausgelöste Fähigkeit arbeitet mit dem `StatSnapshot` vom Auslösezeitpunkt;
  spätere Wertänderungen wirken nicht rückwirkend auf laufende Effekte.
- Schaden aus Fähigkeiten läuft durch die reguläre Pipeline (B05), nicht daran
  vorbei.
- Skill-Leisten-Items sind reine Eingabemethode und tragen keine Logik.
- Flächeneffekte begrenzen die Zielanzahl hart, damit eine Fähigkeit in einer
  Horde nicht den Tick sprengt.

## Offene Fragen (blockierend) — geklärt (2026-08-19)

- [x] **Anzahl Fähigkeiten je Klasse**: 4 aktiv + 2 passiv (ohne Unique Class
      Ability).
- [x] **Eingabeschema**: Hotbar-Slot-Wechsel + Rechtsklick — jede aktive
      Fähigkeit liegt auf einem eigenen Hotbar-Slot (Custom-Item), Rechtsklick
      löst sie aus.
- [x] **Freischaltung**: Fest per Level, **kein Skilltree**. Freigeschaltete
      Fähigkeiten werden zusätzlich mit Coins levelbar/verbesserbar (siehe B06).
- [x] Skilltree-Struktur: entfällt, siehe oben.
- [x] **Mana-Regeneration**: Konstant, im Kampf leicht reduziert
      (zeitstempelbasiert lazy).
- [x] **Unique Class Abilities** (alle zusätzlich mit Coins levelbar):
  - **Warrior — „Call of the Berserker"**: Item = Goat Horn, Rechtsklick →
    aktiver, zeitlich begrenzter Damage- & Defense-Buff (nur Selbstbuff).
  - **Rogue — „Second Life"**: Item = Totem, passiv → prozentuale Chance, beim
    Tod wiederbelebt zu werden.
  - **Mage — „Magic Boost & Fall"**: Items = Wind Charge & Slow Fall Potion,
    passiv → Doppelsprung + Slow-Fall-Effekt.
- [ ] Gibt es einen globalen Cooldown zwischen beliebigen Fähigkeiten?
- [ ] Sind Casting-Zeiten und Unterbrechung vorgesehen?

## Akzeptanzkriterien (Entwurf)

- Eine neue Fähigkeit aus vorhandenen Primitives entsteht rein per Konfiguration.
- 100 gleichzeitig aktive Fähigkeiten mit Flächeneffekt bleiben im Tick-Budget.
- Ein Spieler mit 0 Mana kann keine kostenpflichtige Fähigkeit auslösen; ein
  Spieler auf Cooldown ebenso wenig — beides serverseitig durchgesetzt.
- Cooldown-Anzeige bleibt bei Relogin korrekt (Zeitstempel persistiert).
