# B08 · Fähigkeiten-Framework

| | |
|---|---|
| **Schicht** | 1 — Regel-Engine |
| **Status** | Implementiert (2026-08-22) — 178 Aufgaben, davon 173 erledigt; 0 Fehler, 0 übersprungen. Offen allein: fünf Punkte, die einen laufenden Paper-Server brauchen. Spec unter `specs/008-ability-framework/`. **Durch B08b abgeschlossen** *(2026-08-22)*: der Rangaufstieg kostet jetzt Coins, `RankResult` hat sein `NOT_ENOUGH_COINS`, und die Preise stehen in `abilities.yml` unter `rank-cost` — **als Platzhalter auf je einen Coin** (2026-08-23), damit die Prüfung auslösbar ist, ohne ein nie besprochenes Preisgefüge zu behaupten |
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

- [x] **Anzahl Fähigkeiten je Klasse**: 4 aktiv + 2 passiv, also **sechs
      Fähigkeiten je Klasse**. Die Unique Class Ability zählt als eine der sechs
      — sie ist kein siebter Eintrag, keine eigene Kategorie und kein eigener
      Reiter, sondern eine gewöhnliche Bindung mit gesetztem `unique`-Flag.
      Welcher Art sie ist, hängt an der Klasse: beim Warrior aktiv, bei Rogue
      und Mage passiv. *(korrigiert 2026-08-21 von „sieben" auf „sechs";
      präzisiert 2026-08-22, ADR-022 — die frühere Formulierung „zählt als eine
      der vier aktiven" stimmte nur für den Warrior und widersprach den
      festgelegten Uniques von Rogue und Mage)*.
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
- [x] **Globaler Cooldown**: ja, kurz. Nach jeder ausgelösten aktiven Fähigkeit
      sind für eine kurze Spanne alle anderen gesperrt. Grund ist das
      Eingabeschema selbst: Slot-Wechsel plus Rechtsklick geht viermal im selben
      Tick, ohne Sperre wäre „alle vier sofort" immer die stärkste Eröffnung. Er
      wird wie die Einzel-Cooldowns zeitstempelbasiert lazy gerechnet, der Wert
      ist Konfiguration (ADR-022). *(2026-08-22)*
- [x] **Casting-Zeiten und Unterbrechung**: vorgesehen. Eine Fähigkeit darf eine
      Wirkzeit haben, ein laufender Cast ist unterbrechbar. Instant ist der Fall
      `cast-time: 0`, nicht die Abwesenheit der Mechanik — Wirkzeit nachträglich
      einzuziehen hätte jede vorhandene Fähigkeit, das HUD (B13) und die
      Eingabebehandlung gleichzeitig angefasst (ADR-022). *(2026-08-22)*

### Loadouts je Klasse *(2026-08-21)*

B07 bindet die Fähigkeits-IDs und Freischaltstufen an die Klasse, B08
implementiert das Verhalten (Workflow-Regel 5).

**Warrior** (Anzeigename „Berserker", siehe ADR-019):

| Fähigkeit | Art |
|---|---|
| Wut | passiv |
| Lifesteal | passiv |
| Schild | aktiv |
| Sprung | aktiv |
| Wirbel | aktiv |
| Call of the Berserker | aktiv, Unique Class Ability |

Der Loadout ist damit **vollständig**: vier aktive Fähigkeiten inklusive der
Unique, zwei passive. *(2026-08-21)*

- [x] **Lifesteal ist kein B04-Attribut** und wird auch keins. ADR-008 bleibt
      unangetastet: Lifesteal ist ein Effekt-Primitive im Kampf-Hook („heile X %
      des ausgeteilten Schadens"), der Prozentsatz hängt an der Fähigkeitsstufe
      (Coin-Aufwertung), nicht an einem Attribut. Ein neuntes Attribut hätte
      Stat-Engine, Persistenz und HUD gleichzeitig geöffnet — und mit ihm die
      Tür für Crit und Resistenzen (ADR-022). *(2026-08-22)*
- [x] Loadouts für Mage und Rogue ausgearbeitet und ausgeliefert. Der Rogue ist **3+3**
      statt 4+2 - die Zählregel wurde dafür gelockert (ADR-025): ein Assassine lebt mehr
      von Zuständen als von Knopfdrücken. Beim Mage ist die Unique passiv. *(2026-08-22)*

## Akzeptanzkriterien (Entwurf)

- Eine neue Fähigkeit aus vorhandenen Primitives entsteht rein per Konfiguration.
- 100 gleichzeitig aktive Fähigkeiten mit Flächeneffekt bleiben im Tick-Budget.
- Ein Spieler mit 0 Mana kann keine kostenpflichtige Fähigkeit auslösen; ein
  Spieler auf Cooldown ebenso wenig — beides serverseitig durchgesetzt.
- Cooldown-Anzeige bleibt bei Relogin korrekt (Zeitstempel persistiert).

## Was die Umsetzung ergeben hat *(2026-08-22)*

**Das Akzeptanzkriterium hält, mit einer benannten Einschränkung.** Sechzehn der
achtzehn Fähigkeiten entstanden ohne eine Zeile Java. Zwei brauchten Vokabular,
das die Konfiguration nicht hatte: Warriors Wut baut sich bei aus- *und*
eingeteiltem Schaden auf, Mages Aufstieg & Fall zeigt zwei Marker-Items. Beide
Felder nehmen jetzt einen Wert **oder** eine Liste (ADR-026). Die Grenze, die
dabei gehalten wurde: eine fehlende Vokabel in der Datei ist eine
Schema-Änderung, ein Sonderfall im Dispatcher wäre das Ende der Zusage gewesen.

**Zwei Attribute kamen dazu** (ADR-023): `healthRegen` und `manaRegen`. Sie waren
keine Erweiterung, sondern das fehlende Gegenstück zu ADR-013 — seit die
Vanilla-Regeneration abgeschaltet ist, heilte ein verwundeter Spieler
buchstäblich nie wieder.

**Drei Mechaniken bleiben bis B09/B10 unvollständig**, und das steht im Javadoc
statt still zu sein: der Klon zieht keine Aggro, die Unsichtbarkeit wendet keine
Mobs ab, und Zweites Leben prüft nicht auf Instanzen.

### Offen bis ein Server läuft

- Rechtsklick löst aus; **Linksklick mit einem Fähigkeits-Item macht keinen
  Nahkampfschaden**
- Die Hotbar sieht richtig aus: Waffe auf 0, Fähigkeiten ab 1, Marker danach,
  nicht Freigeschaltetes leer
- Der Doppelsprung trägt zweimal, nicht dreimal, und der Fall ist verlangsamt
- Die Regeneration ist spürbar und im Kampf schwächer — **zugleich der erste
  Beweis überhaupt, dass ein Spieler heilt**
- Ein unterbrochener Cast lässt das Mana unverändert
