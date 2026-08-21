# B11 · Items, Ausrüstung & Loot

| | |
|---|---|
| **Schicht** | 2 — Welt & Content |
| **Status** | Entwurf — **durch ADR-017 stark verkleinert**, Neuzuschnitt vor `/specify` nötig |
| **Abhängig von** | B04, B07, B09, B10 |
| **Benötigt von** | B12, B13 |

## Zweck

> **ADR-017 hat diesen Block umgeschnitten.** Rüstung und Waffe sind seit dieser
> Entscheidung **Klassenprogression, nicht Beute**: je Klasse eine feste Leiter
> mit fünf Stufen und festen Werten, geliefert von B07 über `SourceKind.CLASS`.
> Damit ist B11 **nicht mehr** die dominante Stat-Quelle, und der Apparat aus
> gewürfelten Roll-Werten, acht Raritätsstufen und Beutetabellen für Ausrüstung
> hat keine Slots mehr, an denen er hängen könnte — B11 legt als Slots
> ausdrücklich „nur Vanilla-Armor + Waffe" fest, und beide sind jetzt
> klassenfest.
>
> Was unten durchgestrichen wirkt, ist noch nicht überarbeitet. Der Abschnitt
> „Offene Fragen" hält fest, was zu klären ist, bevor B11 spezifiziert wird.

Items außerhalb der Klassenausrüstung: Aufstiegsmaterial, Verbrauchbares,
Kosmetik. Umfasst Item-Definition, Instanziierung, Haltbarkeit und Beute.

## Umfang

**Weiterhin in B11:**

- Aufstiegsmaterial für die Klassenleitern aus B07 — inklusive der Frage, wer den
  Aufstieg bezahlt (Coins, Level, Material). B07 liefert dafür nur einen
  undurchsichtigen `cost`-Block und legt ihn nicht aus.
- Verbrauchbares (Tränke, Nahrung) und dessen Wirkung über `SourceKind.BUFF`
- Durability und Reparatur — die Todesstrafe nach ADR-017 unverändert tragfähig,
  weil Haltbarkeitsverlust auf nicht ablegbarer Rüstung genauso funktioniert
- Kosmetik: Netherite-Templates (Trims) und Färbung der Klassenrüstung. Das
  Stufen-Schema in B07 führt dafür ein reserviertes Feld.
- Beutetabellen je Mob und Zone — für die oben genannten Kategorien, **nicht**
  für Ausrüstung. Mob-Loot ist von ADR-018 **unberührt**: Mobs lassen Items
  fallen wie geplant, nur Spieler können nichts in die Welt werfen.
- **Lagerplatz und Entsorgung** (neu durch ADR-018, weil das Droppen entfällt):
  Enderchest zum Lagern, Verkauf an NPC gegen Coins, Mülleimer-Befehl zum
  endgültigen Vernichten. Jeder dieser drei Wege fragt das Bindungsprädikat aus
  B07 und weist Klassenausrüstung ab.
- **Warnung bei vollem Inventar** als Title plus Sound. Kein automatisches
  Aufräumen, keine Hintergrundbank, kein stilles Verwerfen — der Spieler schafft
  selbst Platz. Die Ausgabe läuft hinter der B13-Schnittstelle (ADR-005).
- Item-Schema-Versionierung und Migration

**Durch ADR-017 entfallen:**

- ~~Item-Vorlagen mit Wertebereichen je Attribut~~ — Ausrüstungswerte sind fest
  und stehen in der B07-Klassenconfig
- ~~Instanziierung mit gewürfelten Werten (`roll`)~~ für Ausrüstung
- ~~Raritätsstufen und deren Auswirkung auf Wertebereiche~~ — die acht Stufen
  hatten nur Ausrüstung als Träger
- ~~Ausrüstungsslots und Bindung an B04~~ — die Slots sind durch ADR-018 gesperrt,
  die Bindung läuft über B07

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
  (Armor-Change, Held-Item-Change, Inventory-Click) — **nie** per Prüfung pro Tick.
  Nach ADR-018 ist dieser Pfad stark entlastet: Rüstung wechselt nur noch beim
  Stufenaufstieg, nicht durch Spielerhandlung.

## Offene Fragen

### Neu durch ADR-017 — zu klären, bevor B11 spezifiziert wird

- [ ] **Was treibt den Stufenaufstieg?** Coins, Level, Material oder eine
      Kombination. B07 hält die erreichte Stufe und ruft eine Schnittstelle;
      die Kosten legt B11 aus.
- [ ] **Bleiben die acht Raritätsstufen überhaupt bestehen?** Ihr einziger Träger
      war Ausrüstung. Denkbar wäre eine Übertragung auf Verbrauchbares und
      Kosmetik — oder das Streichen des Konzepts.
- [ ] **Bleibt der Roll-Mechanismus bestehen?** Ohne gewürfelte Ausrüstung ist
      offen, ob Verbrauchbares und Material überhaupt Wertebereiche brauchen.
      Falls nein, entfällt der Kern von ADR-004 auch für den Rest von B11.
- [x] **Was fällt noch aus Mobs?** Mobs behalten ihre Loot-Table und lassen Items
      fallen wie geplant. ADR-018 sperrt nur den Spieler-Drop, nicht den Mob-Drop.
      Was genau in den Tabellen steht, ist Content und bei `/specify` B11
      auszuarbeiten — Ausrüstung ist es nicht mehr. *(2026-08-21)*
- [ ] **Wem gehört der NPC-Händler?** Kein Steckbrief B01–B17 deckt NPCs ab; B10
      beschreibt Mobs und Spawning, nicht Händler. Entweder wandert er in den
      Umfang von B11, oder es entsteht ein eigener Block.
- [ ] **Das Nicht-Ziel „kein Wirtschaftssystem" ist zu präzisieren.**
      `00-vision-scope.md` schließt Crafting, Wirtschaft und Handel aus, während
      Coins seit dem 19.08. Währung sind und der NPC-Verkauf eine Coin-Quelle
      wäre. Vermutlich gemeint: kein **Spieler-zu-Spieler**-Handel und kein
      Crafting. So formuliert wäre der NPC-Verkauf gedeckt.

### Bestand

- [x] **Ausrüstungsslots**: Nur Vanilla-Armor + Waffe, keine eigenen GUI-Slots.
      *(2026-08-19)* — nach ADR-018 zusätzlich gesperrt.
- [x] **Raritätsstufen** (8 Stufen, Wertaufschlag je `/specify` auszuarbeiten) —
      **durch ADR-017 infrage gestellt, siehe oben**: *(2026-08-19)*

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
      Mindestlevel und sind teilweise klassengebunden. *(2026-08-19)* Für
      Ausrüstung ist die Klassenbindung durch ADR-017 total statt teilweise: es
      gibt je Klasse genau einen Pfad.
- [ ] Zufällige Affixe/Suffixe zusätzlich zu Basiswerten? — hängt daran, ob der
      Roll-Mechanismus überhaupt bestehen bleibt.
- [x] **Volles Inventar**: Warnung als Title plus Sound; der Spieler schafft
      selbst Platz über Enderchest, NPC-Verkauf oder Mülleimer-Befehl. Kein
      automatisches Aufräumen und kein stilles Verwerfen (ADR-018).
      *(2026-08-21)*
- [x] **Lagerplatz**: Vanilla-Inventar plus Enderchest. Keine eigene Bank.
      *(2026-08-21)*
- [x] **Handel zwischen Spielern**: Nicht erlaubt (konsistent mit den
      Nicht-Zielen in `00-vision-scope.md`). *(2026-08-19)*
- [x] **Was passiert mit Items beim Tod**: Kein Item-/XP-Verlust, aber
      Ausrüstungsschaden (Durability-Verlust) als Todesstrafe. Reparatur-
      Mechanik (vermutlich gegen Coins) ist damit Teil von B11 und bei
      `/specify` auszuarbeiten. *(2026-08-19)*

## Akzeptanzkriterien (Entwurf)

- Änderung einer Item-Vorlage wirkt sich nach Reload auf bestehende Items aus,
  ohne dass Spielerinventare angefasst werden.
- Ein Item übersteht Relogin, Serverneustart und Schema-Migration verlustfrei.
- Ausrüstungswechsel löst genau eine Stat-Neuberechnung aus (siehe B04).
- Manipulierte Item-Daten aus dem Client werden serverseitig erkannt und
  abgelehnt.
