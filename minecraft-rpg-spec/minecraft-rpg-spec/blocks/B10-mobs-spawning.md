# B10 · Mobs & Horden-Spawning

| | |
|---|---|
| **Schicht** | 2 — Welt & Content |
| **Status** | Entwurf — **TPS-kritischster Block** |
| **Abhängig von** | B04, B05, B09 |
| **Benötigt von** | B11, B12 |

## Zweck

Custom-Mobs mit eigenen Attributwerten und ein Spawn-System, das dichte
Hack'n'Slash-Horden erzeugt, ohne den Server-Tick zu überlasten.

## Umfang

- Mob-Definitionen: Basis-Entity, Attributwerte (über B04), Verhalten, Loot
- Spawn-Engine je Zone: Punkte, Dichte, Wellen, Nachschub
- Skalierung nach Spielerzahl vor Ort
- Aggressives Despawnen und Aufräumen
- AI-Kostenkontrolle
- Elite-/Champion-/Boss-Varianten

## Architekturvorgaben

- **Budget statt Anzahl**: Obergrenzen für aktive Mobs je Zone, je Chunk und je
  Spieler. Das Budget ist die harte Grenze, nicht ein Zielwert.
- Mobs außerhalb der Spielerreichweite werden entfernt, nicht nur schlafen
  gelegt.
- Pathfinding ist die teuerste Einzelkomponente: Zielsuche gedrosselt, Reichweite
  begrenzt, ggf. vereinfachte AI statt Vanilla-Pathfinding.
- Mobs verwenden dasselbe Attribut- und Kampfmodell wie Spieler (B04/B05), keine
  Parallelimplementierung.
- Spawn-Berechnung wird über Ticks verteilt, nicht in einem Tick gebündelt.

> **Einordnung:** Bei 100–200 Spielern im Hack'n'Slash ist nicht die Datenbank
> der Engpass, sondern Entity-Ticking und Pathfinding. Dieser Block entscheidet
> über die TPS des Servers.

## Offene Fragen

- [ ] Zielwert für gleichzeitig aktive Mobs (serverweit und je Zone)
- [x] **Skalierung nach Spieleranzahl**: Ja — mehr Spieler in einer Zone führen
      zu höherer Mob-Spawnrate und schnellerem Respawn (Dichte-/Respawn-
      Skalierung, nicht Mob-Stärke). *(2026-08-19)*
- [ ] Welche Vanilla-Entities dienen als Basis (Vanilla-Client, ADR-005)?
- [ ] Wellenlogik: kontinuierlicher Nachschub oder abgegrenzte Wellen mit Pause?
- [ ] Gibt es Elite-/Boss-Mobs mit eigenen Mechaniken? Respawn-Timer?
- [ ] Sollen Vanilla-Mobs vollständig unterdrückt werden?

## Akzeptanzkriterien (Entwurf)

- Lasttest: 800 aktive Custom-Mobs bei 150 Spielern halten p95 MSPT < 40 ms.
- Das Spawn-Budget wird unter keiner Bedingung überschritten, auch nicht bei
  plötzlichem Spielerandrang.
- Nach Verlassen einer Zone durch alle Spieler sind deren Mobs binnen definierter
  Zeit entfernt.
- Ein neuer Mob-Typ entsteht rein per Konfiguration.
