# B11 · Items, Ausrüstung & Loot

| | |
|---|---|
| **Schicht** | 2 — Welt & Content |
| **Status** | Entwurf — durch ADR-017 verkleinert, **durch ADR-027 neu zugeschnitten** *(2026-08-22)*. Die vier blockierenden Fragen sind beantwortet; bereit für `/specify`, sobald B08b steht |
| **Abhängig von** | B04, B07, B08b, B09, B10 |
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
> **ADR-027 hat den Neuzuschnitt vollzogen** *(2026-08-22)*: die Währung ist in einen eigenen
> Block gewandert (B08b), die Raritätsstufen bleiben als reines Etikett ohne Wertwirkung, der
> Roll-Mechanismus entfällt vollständig — **jedes Item hat feste Attributwerte** —, und der
> NPC-Händler gehört hierher.

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

## Zentrale Architekturvorgabe (ADR-004, geändert durch ADR-027)

> Ein Item speichert **die Vorlagen-ID** — niemals berechnete Endwerte und niemals
> gerendertes Lore.

Andernfalls ist nach dem Release kein Rebalancing mehr möglich, ohne alle Items
in allen Spielerinventaren anzufassen. Endwerte und Lore werden bei jedem Laden
aus der Vorlage neu abgeleitet.

> **Die Roll-Hälfte ist entfallen** (ADR-027). Ursprünglich hiess es „Vorlagen-ID
> **und gewürfelte Roll-Werte**". Da jedes Item feste Attributwerte hat, gibt es
> nichts zu würfeln. Die Zusage wird dadurch **stärker**: ohne Roll ist die
> Vorlage die einzige Quelle, und ein geändertes Balancing wirkt auf jedes
> vorhandene Exemplar.

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

### Beantwortet durch ADR-027 *(2026-08-22)*

- [x] **Was treibt den Stufenaufstieg?** Coins. Sie bekommen einen eigenen Block
      (**B08b · Währung & Konto**), weil B07 und B08 sie ebenfalls brauchen und
      Schicht 1 nicht von Schicht 2 abhängen darf. B11 legt die *Preise* für das
      aus, was B11 verkauft und repariert — den Kontostand führt B08b.
- [x] **Bleiben die acht Raritätsstufen bestehen?** Ja, aber **nur als Etikett**.
      Sie sagen, wie selten etwas ist, und beeinflussen keine Werte mehr. Rarität
      als Wertträger hätte genau die Wertebereiche zurückgebracht, die die
      nächste Antwort abschafft.
- [x] **Bleibt der Roll-Mechanismus bestehen?** Nein. **Jedes Item hat feste
      Attributwerte.** Kein Würfeln, keine Wertebereiche, keine Affixe. Zwei
      Tränke desselben Typs sind identisch. ADR-004 schrumpft entsprechend —
      siehe oben.
- [x] **Was fällt noch aus Mobs?** Mobs behalten ihre Loot-Table und lassen Items
      fallen wie geplant. ADR-018 sperrt nur den Spieler-Drop, nicht den Mob-Drop.
      Was genau in den Tabellen steht, ist Content und bei `/specify` B11
      auszuarbeiten — Ausrüstung ist es nicht mehr. *(2026-08-21)*
- [x] **Wem gehört der NPC-Händler?** B11. Er ist der Ort, an dem Items zu Coins
      werden; B10 liefert nur die Entity-Technik, die er mitbenutzt.
- [x] **Das Nicht-Ziel „kein Wirtschaftssystem"** meint **kein
      Spieler-zu-Spieler-Handel und kein Crafting**. NPC-Verkauf gegen Coins ist
      davon gedeckt und war es immer. Die Formulierung in `00-vision-scope.md`
      wird bei `/specify` B11 präzisiert.

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
- [x] **Zufällige Affixe/Suffixe**: nein. Mit dem Roll-Mechanismus entfällt auch
      ihre Grundlage (ADR-027). *(2026-08-22)*
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
