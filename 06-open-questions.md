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

## B05 (Kampf- & Schadens-Pipeline) — abgeschlossen und implementiert (2026-08-20)

- [x] Sekundärmechaniken: **keine**. Kein Krit, kein Ausweichen, kein Blocken, keine
      Resistenztypen. Schaden ist physicalDamage bzw. magicDamage gegen defense, sonst nichts.
      Konsistent mit ADR-008, das Sekundärwerte aus B04 herausgehalten hat, damit sie später ohne
      Architekturänderung nachrückbar sind.
- [x] Schadensformel: Rohschaden aus dem Angreifer-Snapshot, dann `DamageMitigation.afterDefense`
      aus B04. Keine Reihenfolgefrage mehr, weil es keinen Krit gibt.
- [x] Angriffsgeschwindigkeit: eigenes Cooldown-Modell je Angriff, zeitstempelbasiert und lazy
      ausgewertet (Prinzip II). Der Vanilla-Waffencooldown wird abgeschaltet. Zu frühe Schläge
      werden verworfen, nicht abgeschwächt.
- [x] Loot und XP: **XP anteilig nach Schadensanteil, Loot an den höchsten Beitrag.** XP lässt sich
      teilen, ein Item nicht. Braucht das zeitlich verfallende Beitragsfenster je Mob aus dem
      Blocksteckbrief.
- [x] PvP: **aus**, aber die Pipeline hat die Verzweigung an genau einer Stelle. B09 füllt sie
      später mit einer Regel je Zone, ohne dass B05 angefasst wird.
- [x] Todesstrafe: **Ausrüstungsschaden.** B05 erkennt und meldet den Tod; was mit der Ausrüstung
      geschieht, entscheidet B11 über ein Todesereignis. B05 greift nicht auf Haltbarkeiten zu, die
      B11 noch nicht definiert hat.
- [x] Schadenszahlen: **ja, zusammengefasst.** Die Bündelung mehrerer Treffer zu einem Ereignis
      liegt in B05, weil sie Schadenslogik ist; das Zeichnen liegt in B13. B05 erzeugt selbst kein
      Text-Display — bei 150 Spielern gegen 800 Mobs wären das tausende kurzlebige Entitäten je
      Sekunde.
- [x] Vanilla-Schadensquellen: **Umgebungsschaden abbilden, Statuseffekte abschalten.** Fall,
      Feuer, Lava, Void, Ertrinken, Explosion, Kaktus, Ersticken, Blitz und Magma werden auf eigenen
      Schaden umgerechnet (Void bleibt tödlich). Verhungern, Wither, Poison, Instant Damage,
      Instant Health und Absorption werden abgeschaltet. Jede Quelle wird einzeln in einer Tabelle
      festgelegt, wie ADR-003 es verlangt.
- [x] Umgebungsschaden ist ein **fester, konfigurierbarer Betrag**, kein Anteil des maximalen
      Lebens: Gefahren sollen für Anfänger ernst und für ausgerüstete Spieler belanglos werden.
      Fallschaden wächst mit der Fallhöhe. Die Verteidigung greift bei Umgebungsschaden **nicht** —
      der Verlauf soll nur einen Wirkmechanismus haben, nämlich wachsendes Leben.

Bei zwei /clarify-Runden zusätzlich geklärt (siehe ADR-014):

- [x] Beim Tod fällt **kein Inventar** — sonst verdeckt der Verlust die gewählte Strafe
- [x] Der Vanilla-Todesbildschirm bleibt; Respawn füllt Leben und Mana auf
- [x] Fähigkeiten geben ihren Schaden als **Multiplikator auf `magicDamage`** an
- [x] **Projektile** gehören zu B05, sonst wäre ein Bogen ab Tag eins wirkungslos
- [x] **Mobs verletzen einander nicht**; die Erlaubnis fällt an genau einer Stelle
- [x] **B05 stattet Mobs mit Werten aus** (aus `combat.yml`), bis B10 das übernimmt — ohne das
      wirkt die gesamte Pipeline auf nichts außer Spieler
- [x] **B05 führt den Kampfzustand** und veröffentlicht ihn; B08, B12 und B13 fragen ihn ab
- [x] Vanilla-Erfahrungskugeln und Vanilla-Beute werden beim Mob-Tod unterdrückt

**Wichtig:** Die Liste der Vanilla-Schadensquellen im Blocksteckbrief war unvollständig — 17 statt
33. Die fehlenden sechzehn sind in `blocks/B05-combat-pipeline.md` und in
`specs/005-combat-pipeline/contracts/damage-sources.md` entschieden.

→ `naturalRegeneration` und Sättigung sind bereits in B04 erledigt (ADR-013).

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
