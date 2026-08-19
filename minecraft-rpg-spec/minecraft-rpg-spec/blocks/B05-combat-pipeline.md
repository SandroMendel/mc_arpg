# B05 · Kampf- & Schadens-Pipeline

| | |
|---|---|
| **Schicht** | 1 — Regel-Engine |
| **Status** | Entwurf — performancekritisch |
| **Abhängig von** | B04 |
| **Benötigt von** | B08, B10, B12 |

## Zweck

Ersetzt das Vanilla-Kampfsystem vollständig durch ein eigenes, das auf den acht
Attributen aufbaut. Der am häufigsten durchlaufene Codepfad des gesamten Plugins.

## Umfang

- Abfangen und Neutralisieren des Vanilla-Schadens
- Eigene Schadensberechnung: Physical und Magic getrennt, Defense-Anwendung
- Angriffsgeschwindigkeit (eigenes Cooldown-Modell statt Vanilla-Waffencooldown)
- Todesbehandlung für Spieler und Mobs
- Damage-Attribution: Wer bekommt XP und Loot bei vielen Angreifern auf eine
  Horde?
- Knockback, Trefferanimation, Schadensanzeige
- Vanilla-Schadensquellen: Mapping oder Abschaltung

## Festgelegte Anforderungen (ADR-003)

Für **jede** Vanilla-Schadensquelle ist explizit festzulegen, ob sie deaktiviert
oder auf eigenen Schaden abgebildet wird:

Fall · Ertrinken · Feuer · Lava · Void · Kaktus · Explosion · Verhungern ·
Wither · Poison · Instant Damage · Instant Health · Absorption · `/kill` ·
Ersticken · Blitz · Magma-Block

Weiterhin:
- `naturalRegeneration` deaktiviert, Sättigung fixiert
- Vanilla-Schadensereignisse auf 0 gesetzt lösen **keine Trefferanimation** aus —
  sie muss explizit ausgelöst werden
- Gilt gleichermaßen für Custom-Mobs

## Architekturvorgaben

- Die Pipeline ist in klar getrennte Stufen gegliedert (Quelle → Rohschaden →
  Modifikatoren → Verteidigung → Anwendung → Nachwirkung), damit Fähigkeiten und
  Passives an definierten Punkten eingreifen können statt über Sonderfälle.
- Die Schadensformel selbst ist eine reine Funktion in `rpg-core`.
- Kein Objektaufbau pro Treffer, wo vermeidbar; Wiederverwendung im Hot Path.
- Damage-Attribution nutzt ein begrenztes, zeitlich verfallendes Beitragsfenster
  je Mob — keine unbegrenzt wachsende Angreiferliste.

## Offene Fragen

- [ ] Vollständige Schadensformel inkl. Reihenfolge (Crit vor oder nach Defense?)
- [ ] Gibt es Crit, Ausweichen, Blocken, Resistenztypen?
- [ ] Angriffsgeschwindigkeit: Cooldown pro Angriff oder Schadensskalierung?
- [ ] Loot-/XP-Verteilung: letzter Treffer, meister Schaden, oder alle Beteiligten
      anteilig?
- [ ] Todesstrafe für Spieler (XP-Verlust, Ausrüstungsschaden, nichts)?
- [ ] Ist PvP grundsätzlich aktiv, zonenabhängig oder deaktiviert?
- [ ] Schadenszahlen anzeigen (Holograms/Text-Displays) — Performancekosten?

## Akzeptanzkriterien (Entwurf)

- Kein Vanilla-Schaden erreicht jemals ungefiltert einen Spieler oder Custom-Mob.
- Die Herzleiste zeigt bei jedem Schadensereignis den korrekten Prozentwert.
- Lasttest: 150 Spieler gegen 800 Mobs im Dauerkampf halten p95 MSPT < 40 ms.
- Schadensberechnung ist unit-getestet mit dokumentierten Beispielrechnungen.
