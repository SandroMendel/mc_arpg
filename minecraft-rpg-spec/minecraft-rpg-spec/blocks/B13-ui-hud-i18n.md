# B13 · UI, HUD & Texte

| | |
|---|---|
| **Schicht** | 3 — Präsentation |
| **Status** | Entwurf |
| **Abhängig von** | B04, B08, B09 |
| **Benötigt von** | — |

## Zweck

Sämtliche Darstellung gegenüber dem Spieler — unter der Einschränkung eines
reinen Vanilla-Clients (ADR-005).

## Umfang

- HUD: Actionbar, Bossbar, Scoreboard, Title
- Anzeige von HP, Mana, Cooldowns, Zone, Level und XP-Fortschritt
- Skill-Leiste als Eingabemethode
- GUIs: Klassenwahl, Charakterübersicht, Statistiken, Leaderboard, ggf. Skilltree
- Zentrales Message-System mit Schlüsseln
- Herzleiste als Prozentanzeige (ADR-003)

## Einschränkungen durch Vanilla-Client (ADR-005)

| Bereich | Verfügbar | Nicht verfügbar |
|---|---|---|
| HUD | Actionbar, Bossbar, Scoreboard, Title | Eigene HUD-Elemente, freie Positionierung |
| Eingabe | Hotbar-Slots, Links-/Rechtsklick, Sneak-Kombination, Offhand-Swap | Eigene Keybinds |
| Item-Optik | Vanilla-Materialien | Custom-Model-Data ohne Pack |
| Mob-Optik | Vanilla-Entities, Display-Entities | Eigene Modelle |

## Architekturvorgaben

- Rendering liegt hinter Schnittstellen (`HudRenderer`, `ItemRenderer`), damit ein
  pack-fähiger Renderer später eingesetzt werden kann, ohne Gameplay-Code
  anzufassen.
- **Keine hartcodierten Spielertexte.** Alle Texte laufen über Message-Schlüssel;
  Englisch ist die Standardsprache, weitere Sprachen sind strukturell möglich.
- HUD-Aktualisierung erfolgt ereignisgesteuert bei Wertänderung, zusätzlich mit
  einem gedrosselten Takt für Zeitanzeigen (Cooldowns) — nicht pro Tick pro
  Spieler.
- GUI-Inhalte werden gecacht und nur bei Änderung neu aufgebaut.

## Offene Fragen — beantwortet am 2026-08-30

Die Roadmap verlangt, dass diese Liste vor `/specify` beantwortet ist: unbeantwortete
Fragen erzeugen erfundene Annahmen. Hier stehen die Antworten, mit denen die Spec
geschrieben wird.

- [x] **Aufteilung: Was gehört in Actionbar, was in Bossbar, was ins Scoreboard?**
      Drei Flächen, drei Rollen, nichts konkurriert.
      **Actionbar** trägt die laufenden Werte: HP, Mana, Verteidigung, Cooldown-Hinweise —
      das, was `StatusActionBar` heute schon sendet. **Bossbar** trägt das Situative:
      Zonenname beim Betreten, Bosskampf, kanalisierte Fähigkeiten; sie ist heute
      unbenutzt. **Scoreboard** trägt die Sidebar mit Level, XP, Coins und Zone; ebenfalls
      heute unbenutzt. Die Vanilla-XP-Leiste bleibt, was `ExperienceBar` aus ihr macht.

- [x] **Konkretes Layout der Skill-Leiste**
      Steht bereits und bleibt: `AbilityHotbar` aus B08 legt Slot 0 als B07s gebundene
      Waffe und ab Slot 1 je Fähigkeit ein Item; ein nicht freigeschalteter Slot bleibt
      leer. B13 baut das **nicht** um und zieht es auch nicht hinter `HudRenderer` —
      es läuft, es ist getestet, und ein Umbau an fremdem funktionierendem Code wäre der
      teuerste Weg zu keinem sichtbaren Unterschied. B13 legt nur die Cooldown-Darstellung
      darüber.

- [x] **Wie werden Cooldowns ohne eigene Icons dargestellt?**
      Über das **Vanilla-Cooldown-Overlay**: `player.setCooldown(Material, Ticks)` zeichnet
      die graue Sweep-Animation direkt über dem Fähigkeits-Item im Slot. Keine eigene
      Anzeige, kein Pack, und die Geste kennt jeder Spieler von der Enderperle.
      **Bekannte Grenze:** die Anzeige gilt je *Material*, nicht je Slot — zwei Fähigkeiten
      auf demselben Item teilten sich eine Anzeige. Die Spec muss das entweder ausschließen
      (Materialien je Klasse eindeutig) oder benennen.

- [x] **Welche GUIs werden zum Start benötigt?**
      Vier: **Klassenwahl** (löst B07s heutige Befehlsfassung ab), **Charakterübersicht**
      (Attribute, Ausrüstung, `GearConditions` aus B11, Coins aus B08b),
      **Statistiken & Leaderboard** (B12 liefert die Daten; heute gibt es nur das
      Hologramm) und das **Reisefenster** (löst B09s nach ADR-032 ausdrücklich befristetes
      Provisorium ab). Kein Skilltree zum Start.

- [x] **Werden Schadenszahlen angezeigt?**
      Ja, als **kurzlebige Display-Entities** am Trefferort — dieselbe Technik, die B12s
      Hologramm auf echtem Paper bereits belegt hat. B05s `DamageAggregator` bündelt schon;
      B13 bekommt also eine Zahl je Treffer und keinen Strom.

- [x] **Aktualisierungsfrequenz des HUD?**
      **Ereignisgesteuert plus ein Sammeltakt pro Sekunde** für alle Spieler in einem
      Durchlauf. Wertänderungen zeichnen sofort; der Sekundentakt trägt die Actionbar
      (Minecraft blendet sie nach etwa zwei Sekunden aus, ein dauerhafter Wert heißt also
      Nachsenden) und die Zeitanzeigen. Das ist, was `StatusActionBar` heute tut, und es
      passt zum Akzeptanzkriterium „unter 1 ms pro Tick bei 200 Spielern".

## Akzeptanzkriterien (Entwurf)

- HUD-Aktualisierung für 200 Spieler bleibt unter 1 ms pro Tick.
- Kein Spielertext ist im Code hartcodiert (per Test oder Lint nachgewiesen).
- Die Herzleiste zeigt in allen Situationen den korrekten Prozentwert.
- Ein Wechsel des `HudRenderer` erfordert keine Änderung an B04, B05 oder B08.
