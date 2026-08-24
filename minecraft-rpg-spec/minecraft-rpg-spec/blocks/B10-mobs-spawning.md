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

- [ ] Zielwert für gleichzeitig aktive Mobs (serverweit und je Zone) —
      Ausgangspunkt 800 serverweit, rund 130 je Zone; Startwerte legt `/plan` fest
- [x] **Skalierung nach Spieleranzahl**: Ja — mehr Spieler in einer Zone führen
      zu höherer Mob-Spawnrate und schnellerem Respawn (Dichte-/Respawn-
      Skalierung, nicht Mob-Stärke). *(2026-08-19)*
- [x] **Welche Vanilla-Entities als Basis dienen, ist Inhalt und keine Spec.**
      Die Wahl steht je Mob-Art in der Konfiguration. Verlangt wird nur, dass sie
      konfigurierbar ist und dass mehrere Arten auf demselben Vanilla-Entity
      unterscheidbar bleiben. *(2026-08-24)*
- [x] **Kontinuierlicher Nachschub, keine Wellen.** Getötete Kreaturen werden
      laufend ersetzt; kein Zonenzustand „Welle läuft / geräumt / Pause".
      *(2026-08-24)*
- [x] **Der Boss ist eine Mob-Art mit höheren Attributen und einem
      Respawn-Timer — mehr nicht.** Keine eigenen Fähigkeiten, keine Phasen.
      Elite- und Champion-Varianten entfallen; der eigentliche Bosskampf kommt
      später als **Dungeon-Boss** mit Instanzen und Fähigkeiten und gehört nicht
      zu diesem Block. *(2026-08-24)*
- [x] **Vanilla-Mobs werden vollständig unterdrückt.** Natürliches Spawning ist
      aus — überall. Damit ist das Budget dieses Blocks die einzige Quelle
      lebender Kreaturen. Absichtlich gesetzte Kreaturen bleiben möglich und
      bekommen Standardwerte. Folge: Wolle, Leder und Fleisch aus natürlichem
      Tier-Spawning entfallen, das ist eine Inhaltsfrage für B11/B16.
      *(2026-08-24)*

## Akzeptanzkriterien (Entwurf)

- Lasttest: 800 aktive Custom-Mobs bei 150 Spielern halten p95 MSPT < 40 ms. Der
  **Zielwert bleibt verbindlich**, aber der Nachweis gehört seit ADR-031 (2026-08-23)
  in B15s Lasttestphase und ist keine Bedingung dafür, dass dieser Block fertig ist.
  Zuvor nannte Prinzip VII B10 namentlich als lasttestpflichtig.
- Das Spawn-Budget wird unter keiner Bedingung überschritten, auch nicht bei
  plötzlichem Spielerandrang.
- Nach Verlassen einer Zone durch alle Spieler sind deren Mobs binnen definierter
  Zeit entfernt.
- Ein neuer Mob-Typ entsteht rein per Konfiguration.
