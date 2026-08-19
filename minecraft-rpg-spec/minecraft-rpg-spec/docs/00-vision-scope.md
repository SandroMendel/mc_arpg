# 00 · Vision & Scope

## Produktvision

Ein Hack'n'Slash-RPG-Server für Minecraft, auf dem Spieler eine von drei Klassen
wählen und in thematisch getrennten Gebieten Monsterhorden bekämpfen, dabei
Erfahrung sammeln, Ausrüstung erbeuten und ihren Charakter über ein eigenes
Attributsystem entwickeln.

Vorbild für Spielgefühl und Weltaufbau: klassische Action-RPGs kombiniert mit
einer zusammenhängenden Spielwelt im Stil von MMORPGs.

## Zielplattform

| Aspekt | Festlegung |
|---|---|
| Server-Software | Paper (Paper-API, nicht Bukkit-generisch) |
| Minecraft-Version | 26.2 |
| Java-Version | 25 |
| Datenbank | PostgreSQL |
| Topologie | Ein einzelner Server, kein Netzwerk/Proxy-Verbund |
| Zielspielerzahl | 100–200 gleichzeitig |
| Sprache im Spiel | Englisch (internationaler Server) |
| Client | Vanilla, ohne Resource Pack |

## Kern-Features (in Scope)

1. **Klassensystem** — drei wählbare Klassen mit je eigenen Basiswerten
2. **Fähigkeiten** — passive und aktive Fähigkeiten je Klasse, dazu je Klasse
   eine **Unique Class Ability**
3. **Attributsystem** — acht Spielerwerte:
   - Health (HP)
   - Defense
   - Mana
   - Physical Damage
   - Magic Damage
   - Attackspeed
   - Movement Speed
   - Ability Cooldown
4. **Erfahrungslevel** — eigenes Levelsystem, unabhängig von Vanilla-XP
5. **Gebiete / Regionen** — mehrere Zonen mit unterschiedlichen Levelbereichen
6. **Monsterhorden** — Custom-Mobs mit eigenen Werten, dichtes Spawning
7. **Ausrüstung & Loot** — Items tragen Attributwerte und sind Stat-Quelle
8. **Statistiken & Leaderboards**
9. **Persistenz** — alle Spielerdaten asynchron in PostgreSQL

## Explizite Nicht-Ziele (vorerst)

- Kein Proxy-/Multi-Server-Netzwerk (Velocity, BungeeCord)
- Kein Resource Pack, keine Client-Mods (Option bleibt architektonisch offen)
- Kein Crafting-/Wirtschafts-/Handelssystem
- Kein PvP als Kernmechanik
- Keine Mehrsprachigkeit zum Start (Struktur wird aber vorbereitet)
- Kein Bauen/Terraforming durch Spieler

## Erfolgskriterien

- Stabile 20 TPS bei 150 gleichzeitigen Spielern unter Kampflast
- Kein Spielerdatenverlust bei Crash oder unsauberem Shutdown
- Neue Zonen, Mobs, Items und Fähigkeiten ohne Codeänderung ergänzbar
