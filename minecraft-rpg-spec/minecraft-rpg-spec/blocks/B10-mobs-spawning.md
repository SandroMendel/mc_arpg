# B10 · Mobs & Horden-Spawning

| | |
|---|---|
| **Schicht** | 2 — Welt & Content |
| **Status** | Umgesetzt — **TPS-kritischster Block**, Lasttest-Nachweis bei 800/150 gehört seit ADR-031 zu B15 |
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

- [x] **Zielwert für gleichzeitig aktive Mobs: 800 serverweit, 130 je Zone,
      12 je Chunk, 25 je Spieler.** Ausgeliefert in `mobs.yml`, alle vier
      konfigurierbar. *(2026-08-26)*
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

## Akzeptanzkriterien — abgeglichen gegen `specs/010-mobs-spawning/spec.md` §Success Criteria *(2026-08-27)*

Alle elf, gegen die eigene Testsuite gehalten statt nur behauptet:

- **SC-001**: Eine neue Art entsteht durch Bearbeiten von `mobs.yml` und einen Neustart, ohne eine
  Zeile Code — `ConfigOnlyMobTest`.
- **SC-002**: Alle 48 Arten und 6 Bosse laden ohne Beanstandung — `ShippedMobConfigTest`.
- **SC-003**: Zonen-, Chunk- und Spielerbudget werden unter keiner Bedingung überschritten, auch
  nicht bei gleichzeitigem Andrang — `BudgetHoldsTest`.
- **SC-004**: Nach dem Verlassen einer Region durch alle Spieler ist binnen der konfigurierten Frist
  keine Kreatur der Region mehr da — `HordeSweepTest`.
- **SC-005**: Die Zahl lebender Kreaturen einer Region steigt mit der Spielerzahl, die Attributwerte
  einer Art bleiben unverändert — `DensityScalingTest`, `ScalingNeverTouchesAttributesTest`.
- **SC-006**: Höchstens ein Boss je Region, der Respawn-Timer hält die konfigurierte Mindestzeit ein
  — `OneBossPerRegionTest`, `BossRespawnTimerTest`.
- **SC-007**: Eine wiederholbare Messung ohne Volllast liegt vor — `HordeBudgetBenchmarkTest`, 130
  Kreaturen in 1.300 ns gemessen (Budget 1 ms). Der Lasttest-Nachweis bleibt B15 (ADR-031).
- **SC-008**: Die drei wartenden Schnittstellen aus B05, B06 und B08b werden bedient, keine hat eine
  zweite Fassung bekommen — `NoCompetingMobProviderTest`.
- **SC-009**: Der Klon des Rogue zieht Kreaturen auf sich, die Roadmap-Zeile ist geschlossen —
  `CloneAggroTest`, siehe `blocks/B08-ability-framework.md`.
- **SC-010**: Vanilla-Spawning ist vollständig aus, jede Kreatur lässt sich einer Zone dieses Blocks
  oder einem absichtlichen Setzen zuordnen — `VanillaSpawnSuppressorTest`.
- **SC-011**: Auch bei Zonenbudgets, deren Summe das serverweite Budget übersteigt, stehen nie mehr
  als serverweit konfiguriert — `ServerWideBudgetHoldsTest`.

**Was davon nur der echte Server beweist**: die 34 Prüfschritte in
`specs/010-mobs-spawning/quickstart.md` Abschnitt 3 — grüne Tests beweisen nichts über Papers
Spawner und nichts über die Ladeordnung.
