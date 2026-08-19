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

## Offene Fragen

- [ ] Aufteilung: Was gehört in Actionbar, was in Bossbar, was ins Scoreboard?
- [ ] Konkretes Layout der Skill-Leiste (welcher Slot, welche Fähigkeit)
- [ ] Wie werden Cooldowns ohne eigene Icons dargestellt?
- [ ] Welche GUIs werden zum Start benötigt?
- [ ] Werden Schadenszahlen angezeigt (siehe auch B05)?
- [ ] Aktualisierungsfrequenz des HUD?

## Akzeptanzkriterien (Entwurf)

- HUD-Aktualisierung für 200 Spieler bleibt unter 1 ms pro Tick.
- Kein Spielertext ist im Code hartcodiert (per Test oder Lint nachgewiesen).
- Die Herzleiste zeigt in allen Situationen den korrekten Prozentwert.
- Ein Wechsel des `HudRenderer` erfordert keine Änderung an B04, B05 oder B08.
