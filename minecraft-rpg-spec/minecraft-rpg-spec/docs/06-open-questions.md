# 06 · Offene Fragen (zentrale Sammlung)

> **VERALTET — nicht diese Fassung lesen.** Die gelebte Datei ist `06-open-questions.md` im
> Projektstamm. Diese Kopie ist seit dem ersten Commit eingefroren; ihr fehlen alle
> Einträge ab ADR-013. Sie steht noch hier, weil das Löschen einer Datei aus dem
> Spec-Bündel eine Entscheidung ist, die nicht nebenbei getroffen werden sollte.

Diese Punkte müssen vor der jeweiligen Spec-Erstellung geklärt werden. Details
stehen im jeweiligen Blocksteckbrief. Stand: 2026-08-19 — Klärungsrunde mit dem
Auftraggeber abgeschlossen, Ergebnisse sind in den Blocksteckbriefen und in
`02-decisions.md` (ADR-006, ADR-008) nachgetragen.

## B04 (Stat-Engine) — abgeschlossen

- [x] Stacking-Reihenfolge: `(Base + Flat) × (1 + ΣPercent)`
- [x] Defense-Formel: Divisor-Modell `dmg × 100/(100+def)`, kein separater Cap
- [x] Skalierungsverhältnis Level vs. Ausrüstung: Ausrüstung dominant
- [x] Attackspeed und Movement Speed: beide über Vanilla-Attribute
- [x] Ability Cooldown: prozentuale Reduktion, harter Cap bei 40%
- [x] Wertebereiche und Caps je Attribut: siehe Tabelle in
      `blocks/B04-stat-engine.md`
- [x] Sekundärwerte (Crit-Chance, Crit-Schaden, Lifesteal, Resistenzen):
      vorerst nicht Teil von B04

→ Details und Begründung: `02-decisions.md` ADR-008, `blocks/B04-stat-engine.md`

## B06/B07 (Progression & Klassen) — abgeschlossen, Basiswerte offen

- [x] Maximallevel: 60, moderat ansteigende Kurve
- [x] Level-Up: kleine feste Wertsteigerung; Fähigkeiten separat per Level
      freigeschaltet und mit Coins aufwertbar (kein Skillpunkt-System)
- [x] Die drei Klassen: Warrior, Mage, Rogue (Rollenprofile siehe
      `blocks/B07-class-system.md`)
- [x] Klassenwechsel: nicht möglich, Klasse ist permanent
- [x] Charakter-Slots: 3 pro Account (ein Slot je Klasse)
- [x] Vierte Klasse: drei Klassen bleiben im Code festgeschrieben, ihr Inhalt ist
      Config; eine vierte ist späteres Upgrade (ADR-019). „Berserker" ist
      Anzeigename des Warrior, keine eigene Klasse. *(2026-08-21)*
- [x] Waffen-/Rüstungsbeschränkungen: kein Filter, sondern je Klasse ein fester
      Rüstungs- und Waffenpfad mit fünf Stufen und festen Werten. Ersetzt
      Beute-Ausrüstung und revidiert ADR-004 (ADR-017). *(2026-08-21)*
- [x] Ablegen und Droppen: Rüstung nie ablegbar, keine Items dropbar; die Sperre
      kommt mit B07 (ADR-018). *(2026-08-21)*
- [ ] Basiswerte und Wachstumskurven je Klasse für alle acht Attribute →
      bei `/specify` B07 auszuarbeiten (Content, keine Architekturfrage)
- [x] Vor der Klassenwahl: kein Spielzustand, GUI nicht schließbar, keine Stats,
      kein Schaden, keine Bewegung. Die Tutorialwelt aus ADR-006 bleibt
      nachrüstbar (ADR-020). *(2026-08-21)*
- [x] Wer bezahlt den Stufenaufstieg der Klassenausrüstung? **Coins.** Beantwortet
      und umgesetzt in B08b: der `cost`-Block wird dort ausgelegt, nicht in B07 —
      dessen Invariantentest verbietet die Vokabel und gab damit vor, wo die
      Auslegung hingehört. *(2026-08-22, ADR-027)*

In B06s `/clarify` beantwortet und umgesetzt:

- [x] XP-Skalierung bei Levelunterschied zum Mob: keine. Die XP eines Mobs hängt
      ausschliesslich am Mob, nie am Level des Tötenden. *(B06)*
- [x] Gruppen-/Party-XP-Teilung: echtes Party-System mit Einladung, geteilter XP
      und Nähe-Bonus als Prozentaufschlag. Eine Party gilt als **ein**
      Beitragender und setzt B05s „XP anteilig nach Schadensanteil" fort. *(B06)*
- [x] Fortsetzung nach Maximallevel 60: keine. XP darüber verfällt still;
      weiteres Wachstum läuft über Ausrüstung und Fähigkeiten. *(B06)*

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

## B11 (Items) — durch ADR-017 neu aufgeworfen

ADR-017 macht Rüstung und Waffe zu Klassenprogression statt Beute. Damit sind
mehrere hier als abgeschlossen geführte Punkte wieder offen — der Steckbrief
`blocks/B11-items-loot-equipment.md` trägt den Neuzuschnitt.

- [x] Ausrüstungsslots: nur Vanilla-Armor + Waffe — nach ADR-018 zusätzlich
      gesperrt (nicht ablegbar)
- [x] Handel zwischen Spielern: nicht erlaubt
- [ ] **Bleiben die acht Raritätsstufen bestehen?** Ihr einziger Träger war
      Ausrüstung. Übertragen auf Verbrauchbares und Kosmetik, oder streichen?
- [ ] **Bleibt der Roll-Mechanismus bestehen?** Ohne gewürfelte Ausrüstung ist
      offen, ob überhaupt noch etwas Wertebereiche braucht — davon hängt ab, wie
      viel von ADR-004 für den Rest von B11 übrig bleibt.
- [x] Was noch aus Mobs fällt: Mobs behalten ihre Loot-Table. ADR-018 sperrt nur
      den Spieler-Drop, nicht den Mob-Drop. Kämpfen bleibt Beutequelle; nur
      Ausrüstung fällt nicht mehr. *(2026-08-21)*
- [x] Lagerplatz: Vanilla-Inventar plus Enderchest, keine eigene Bank. Bei vollem
      Inventar Warnung als Title plus Sound; der Spieler schafft selbst Platz über
      Enderchest, NPC-Verkauf oder Mülleimer-Befehl (ADR-018). *(2026-08-21)*
- [ ] Wer bezahlt den Stufenaufstieg der Klassenleitern aus B07?
- [ ] Zufällige Affixe/Suffixe zusätzlich zu Basiswerten?
- [ ] **Wem gehört der NPC-Händler?** Kein Steckbrief B01–B17 deckt NPCs ab.
      Entweder Umfang von B11 oder ein eigener Block.
- [x] **Nicht-Ziel „kein Wirtschaftssystem" präzisiert.** Gemeint ist **kein
      Spieler-zu-Spieler-Handel und kein Crafting**. NPC-Verkauf gegen Coins war
      davon immer gedeckt (ADR-027). B08b setzt das um: es gibt keinen Weg,
      Coins zwischen Spielern zu bewegen, und keinen zentralen Preiskatalog —
      ein Quelltest hält beides fest. *(2026-08-22)*

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
