# 06 · Offene Fragen (zentrale Sammlung)

Diese Punkte müssen vor der jeweiligen Spec-Erstellung geklärt werden. Details
stehen im jeweiligen Blocksteckbrief. Stand: 2026-08-19 — Klärungsrunde mit dem
Auftraggeber abgeschlossen, Ergebnisse sind in den Blocksteckbriefen und in
`02-decisions.md` (ADR-006, ADR-008) nachgetragen.

## B04 (Stat-Engine) — abgeschlossen und implementiert (2026-08-20)

- [x] Stacking-Reihenfolge: `(Base + Flat) × (1 + ΣPercent)`
- [x] Defense-Formel: Divisor-Modell `dmg × 100/(100+def)`, kein separater Cap
- [x] Skalierungsverhältnis Level vs. Ausrüstung: Ausrüstung dominant
- [x] Attackspeed und Movement Speed: beide über Vanilla-Attribute
- [x] Ability Cooldown: prozentuale Reduktion, harter Cap bei 40%
- [x] Wertebereiche und Caps je Attribut: siehe Tabelle in
      `blocks/B04-stat-engine.md`
- [x] Sekundärwerte (Crit-Chance, Crit-Schaden, Lifesteal, Resistenzen):
      vorerst nicht Teil von B04

Bei der Umsetzung zusätzlich geklärt (siehe ADR-013):

- [x] Ressourcenbehälter (aktuelles Leben/Mana) gehören zu B04 und werden persistiert
- [x] Vanilla-Regeneration abschalten: B04; Vanilla-Schadensquellen umlenken: B05
- [x] Attributsatz geschlossen, keine Laufzeit-Registratur
- [x] Caps konfigurierbar, aber Pflichtfelder mit Fail-Fast
- [x] Bündelung automatisch über eine trägergebundene Einmalaufgabe

→ Details und Begründung: `02-decisions.md` ADR-008 und ADR-013,
  `blocks/B04-stat-engine.md`, `specs/004-stat-engine/`

## B06/B07 (Progression & Klassen) — abgeschlossen, Basiswerte offen

- [x] Maximallevel: 60, moderat ansteigende Kurve
- [x] Level-Up: kleine feste Wertsteigerung; Fähigkeiten separat per Level
      freigeschaltet und mit Coins aufwertbar (kein Skillpunkt-System)
- [x] Die drei Klassen: Warrior, Mage, Rogue (Rollenprofile siehe
      `blocks/B07-class-system.md`)
- [x] Klassenwechsel: nicht möglich, Klasse ist permanent
- [x] Charakter-Slots: 3 pro Account (ein Slot je Klasse)
- [ ] Basiswerte und Wachstumskurven je Klasse für alle acht Attribute →
      bei `/specify` B07 auszuarbeiten (Content, keine Architekturfrage)
- [ ] Waffen-/Rüstungsbeschränkungen je Klasse?
- [ ] Was passiert vor der Klassenwahl (Tutorialbereich)?
- [ ] XP-Skalierung bei Levelunterschied zum Mob (Anti-Powerleveling)?
- [ ] Gruppen-/Party-XP-Teilung vorgesehen?
- [ ] Fortsetzung nach Maximallevel (Paragon/Prestige)?

## B08 (Fähigkeiten) — abgeschlossen

- [x] Anzahl Fähigkeiten je Klasse: 4 aktiv + 2 passiv
- [x] Eingabeschema: Hotbar-Slot-Wechsel + Rechtsklick
- [x] Freischaltung: fest per Level, kein Skilltree; zusätzlich Coin-Aufwertung
- [x] Mana-Regeneration: konstant, im Kampf leicht reduziert
- [x] Unique Class Abilities (alle Coin-levelbar):
  - Warrior — „Call of the Berserker" (Goat Horn, aktiv: Damage-/Defense-Buff)
  - Rogue — „Second Life" (Totem, passiv: %-Chance auf Wiederbelebung)
  - Mage — „Magic Boost & Fall" (Wind Charge + Slow Fall Potion, passiv:
    Doppelsprung + Slow Fall)
- [ ] Globaler Cooldown zwischen beliebigen Fähigkeiten?
- [ ] Casting-Zeiten und Unterbrechung vorgesehen?

## B09/B10 (Welt & Mobs) — abgeschlossen, Details offen

- [x] **ADR-006 bestätigt**: eine handgebaute Kontinent-Welt für offene Zonen,
      separate Instanzwelten nur für Dungeons/Bossräume/Tutorial
- [x] Anzahl Zonen zum Start: 4–5 Zonen, aufsteigend gestaffelt
- [x] Kartenbau: handgebaut
- [x] Zonenschwierigkeit skaliert mit Spieleranzahl vor Ort: mehr Spieler →
      höhere Mob-Spawnrate + schnelleres Respawn (Dichte/Respawn, nicht Stärke)
- [x] Respawn-Regeln und Todesstrafe: kein XP-/Item-Verlust, aber
      Ausrüstungsschaden (Durability-Verlust) — Reparatur-Mechanik (vermutlich
      Coins) folgt bei `/specify` B11
- [ ] Zielwert für gleichzeitig aktive Mobs (serverweit und je Zone)
- [ ] Welche Vanilla-Entities dienen als Basis?
- [ ] Wellenlogik: kontinuierlicher Nachschub oder abgegrenzte Wellen?
- [ ] Elite-/Boss-Mobs mit eigenen Mechaniken? Respawn-Timer?
- [ ] Zonengeometrie: Quader, Polygon oder Chunk-Menge?
- [ ] Reisesystem: Laufen, Portale, Wegpunkte, Teleport-Kosten?

## B11 (Items) — abgeschlossen, Details offen

- [x] Ausrüstungsslots: nur Vanilla-Armor + Waffe
- [x] Raritätsstufen (8, mit Farben): Common (Weiß) → Uncommon (Hellgrün) →
      Rare (Blau) → Epic (Lila) → Legendary (Orange) → Mythic (Pink) →
      Divine (Hellblau) → Special (Rot, Seasons/Events)
- [x] Item-Level-Anforderung und Klassenbindung: beides
- [x] Handel zwischen Spielern: nicht erlaubt
- [ ] Zufällige Affixe/Suffixe zusätzlich zu Basiswerten?
- [ ] Lagerplatz: Vanilla-Inventar, Enderchest, eigene Bank?

## B12 (Statistiken) — abgeschlossen, Details offen

- [x] Erfasste Metriken: Level, Coins, Mob-Kills, Tode, Spielzeit, Bosskills
- [x] Zeiträume: allzeit + saisonal
- [ ] Welche Metriken sind öffentlich als Leaderboard sichtbar (vs. nur privat)?
- [ ] Refresh-Intervall der Leaderboards?
- [ ] Anzeigeform: Chat, GUI, Hologramm im Hub?

## Betrieb

- [x] **PostgreSQL-Standort**: gleiche Maschine wie der Server.
- [ ] **Hardware-Zielprofil**: Noch offen — ein passender VPS wird erst gekauft
      und im Betrieb beobachtet. Empfehlung als Ausgangspunkt (TODO: mit realen
      Benchmark-Werten nach VPS-Kauf abgleichen):
      - **CPU**: dedizierte (nicht geteilte) vCPUs, hohe Single-Core-Taktrate
        (≥ 4,0 GHz Boost) — der Server-Tick läuft auf einem einzigen Kern
        (ADR-002/ADR-007), Minecraft ist extrem single-core-empfindlich.
        Mindestens 6–8 dedizierte Kerne insgesamt für Netty, GC, async I/O
        und die auf derselben Maschine laufende PostgreSQL-Instanz.
      - **RAM**: mindestens 16 GB, empfohlen 24–32 GB (8–12 GB JVM-Heap für
        Paper, Rest für OS-Diskcache und PostgreSQL `shared_buffers`).
      - **Storage**: NVMe SSD zwingend (Chunk-I/O und DB-Latenz).
      - **Netzwerk**: ≥ 1 Gbit/s Uplink für 100–200 gleichzeitige Spieler.
      - Anbieter mit garantiert dedizierten Kernen bevorzugen, keine
        Burst-/Shared-vCPU-Billig-Angebote.
