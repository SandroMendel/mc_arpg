# B11 · Items, Ausrüstung & Loot

| | |
|---|---|
| **Schicht** | 2 — Welt & Content |
| **Status** | Entwurf — Kernbestandteil laut ADR-004 |
| **Abhängig von** | B04, B09, B10 |
| **Benötigt von** | B12, B13 |

## Zweck

Items tragen Attributwerte und sind neben Klasse und Level die dritte
Stat-Quelle. Umfasst Item-Definition, Instanziierung, Ausrüstung und Beute.

## Umfang

- Item-Vorlagen (`ItemTemplate`) mit Wertebereichen je Attribut
- Instanziierung mit gewürfelten Werten (`roll`)
- Raritätsstufen und deren Auswirkung auf Wertebereiche
- Ausrüstungsslots und Bindung an B04
- Beutetabellen je Mob und Zone
- Item-Schema-Versionierung und Migration

## Zentrale Architekturvorgabe (ADR-004)

> Ein Item speichert **Template-ID und gewürfelte Roll-Werte** — niemals
> berechnete Endwerte und niemals gerendertes Lore.

Andernfalls ist nach dem Release kein Rebalancing mehr möglich, ohne alle Items
in allen Spielerinventaren anzufassen. Endwerte und Lore werden bei jedem Laden
aus Vorlage + Roll neu abgeleitet.

Weitere Vorgaben:
- Speicherung über **PersistentDataContainer**, nicht über Lore-Parsing
- Item-Schema ist versioniert mit definiertem Migrationspfad
- Im Schema wird bereits jetzt ein Feld für **Custom-Model-Data reserviert**,
  auch wenn es im Vanilla-Betrieb ungenutzt bleibt (ADR-005)
- Unterschiedliche Items unterscheiden sich über unterschiedliche
  **Vanilla-Materialien**, solange kein Resource Pack existiert
- Ausrüstungswechsel wird über einen günstigen Dirty-Check erkannt
  (Armor-Change, Held-Item-Change, Inventory-Click) — **nie** per Prüfung pro Tick

## Offene Fragen

- [x] **Ausrüstungsslots**: Nur Vanilla-Armor + Waffe, keine eigenen GUI-Slots.
      *(2026-08-19)*
- [x] **Raritätsstufen** (8 Stufen, Wertaufschlag je Stufe bei `/specify`
      auszuarbeiten): *(2026-08-19)*

  | Stufe | Farbe |
  |---|---|
  | Common (Gewöhnlich) | Weiß |
  | Uncommon (Ungewöhnlich) | Hellgrün |
  | Rare (Selten) | Blau |
  | Epic (Episch) | Lila |
  | Legendary (Legendär) | Orange |
  | Mythic (Mythisch) | Pink |
  | Divine (Göttlich) | Hellblau |
  | Special (Speziell, Seasons/Events) | Rot |

- [x] **Item-Level-Anforderung und Klassenbindung**: Beides — Items brauchen ein
      Mindestlevel und sind teilweise klassengebunden. *(2026-08-19)*
- [ ] Zufällige Affixe/Suffixe zusätzlich zu Basiswerten?
- [x] **Handel zwischen Spielern**: Nicht erlaubt (konsistent mit den
      Nicht-Zielen in `00-vision-scope.md`). *(2026-08-19)*
- [x] **Was passiert mit Items beim Tod**: Kein Item-/XP-Verlust, aber
      Ausrüstungsschaden (Durability-Verlust) als Todesstrafe. Reparatur-
      Mechanik (vermutlich gegen Coins) ist damit Teil von B11 und bei
      `/specify` auszuarbeiten. *(2026-08-19)*
- [ ] Lagerplatz: Vanilla-Inventar, Enderchest, eigene Bank?

## Akzeptanzkriterien (Entwurf)

- Änderung einer Item-Vorlage wirkt sich nach Reload auf bestehende Items aus,
  ohne dass Spielerinventare angefasst werden.
- Ein Item übersteht Relogin, Serverneustart und Schema-Migration verlustfrei.
- Ausrüstungswechsel löst genau eine Stat-Neuberechnung aus (siehe B04).
- Manipulierte Item-Daten aus dem Client werden serverseitig erkannt und
  abgelehnt.
