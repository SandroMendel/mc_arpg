# 05 · Roadmap & Spec-Kit-Workflow

## Reihenfolge

Die Blöcke werden nicht nach Attraktivität, sondern nach Abhängigkeit
abgearbeitet. B04 und B05 sind die Verträge, gegen die fast alles andere
entwickelt wird — dort steckt die meiste Spezifikationsarbeit.

```
M1 Fundament        B01 → B02 → B03
M2 Regelkern        B04 → B05
M3 Charakter        B06 → B07 → B08 → B08b
M4 Welt & Content   B09 → B10 → B11
M5 Meta             B12 → B13 → B14
quer                B15, B16, B17 ab M1 mitlaufend
```

## Meilensteine

### M1 — Fundament
**Ziel:** Server startet, Spielerdaten werden geladen und gespeichert.
**Nachweis:** 200 simulierte Joins ohne Fehler; nach `kill -9` gehen höchstens
ein Autosave-Intervall an Daten verloren.

### M2 — Regelkern
**Ziel:** Ein Spieler hat acht Attribute und kann mit eigenem Schadensmodell
Schaden nehmen und austeilen; die Herzleiste ist Prozentanzeige.
**Nachweis:** Alle Formeln unit-getestet; kein Vanilla-Schaden gelangt
ungefiltert durch; erster Lasttest.

### M3 — Charakter
**Ziel:** Klassenwahl, Level-Aufstieg, aktive und passive Fähigkeiten inkl.
Unique Class Ability sind spielbar.
**Nachweis:** Eine neue Fähigkeit entsteht rein per Konfiguration.

### M4 — Welt & Content
**Ziel:** Mehrere Zonen mit Monsterhorden und Loot; Ausrüstung wirkt auf
Attribute.
**Nachweis:** 800 aktive Mobs bei 150 Spielern halten p95 MSPT < 40 ms.

### M5 — Meta
**Ziel:** Statistiken, Leaderboards, vollständiges HUD, Admin-Werkzeuge.
**Nachweis:** Leaderboard-Aufruf durch 50 Spieler erzeugt höchstens eine
Datenbankabfrage.

## Spec-Kit-Ablauf

### Einmalig

```bash
specify init --here --ai claude
```

Anschließend `/constitution` mit dem Inhalt von `constitution.md` ausführen.

### Je Block

```
/specify    Eingabe: Inhalt von blocks/BXX-*.md
            plus die Antworten auf dessen offene Fragen
/clarify    offene Punkte auflösen, bevor geplant wird
/plan       technischer Plan; Constitution wird geprüft
/tasks      Aufgabenliste
/implement  Umsetzung
```

### Regeln für die Arbeit mit Claude Code

1. **Ein Block = eine Spec = ein Branch.** Keine blockübergreifenden Specs.
2. **Offene Fragen zuerst.** Die Liste „Offene Fragen" im Blocksteckbrief muss
   beantwortet sein, bevor `/specify` läuft. Unbeantwortete Fragen erzeugen
   erfundene Annahmen.
3. **Constitution vor Plan.** Verstößt ein Plan gegen die Constitution, wird der
   Plan geändert — nicht die Constitution.
4. **Entscheidungen wandern zurück.** Jede in der Umsetzung getroffene
   Architekturentscheidung wird als ADR in `docs/02-decisions.md` ergänzt.
5. **Kein Vorgriff.** Ein Block implementiert nichts, was zu einem späteren Block
   gehört — stattdessen wird die Schnittstelle definiert.

## Empfohlener nächster Schritt

*(Stand 2026-08-23)* B01 bis **B09** sind implementiert und verdrahtet. Offen
sind dort nur noch Validierungsläufe auf einem echten Paper-Server — kein Code.

**Die Lasttests sind aus den Blöcken herausgelöst** *(ADR-031, 2026-08-23)*. Sie
laufen gebündelt in **B15s Lasttestphase**, wenn die inhaltlichen Blöcke stehen,
und sind keine Bedingung mehr dafür, dass ein Block fertig ist. Grund: ein
Lasttest braucht Spieler, Mobs und Inhalt — also gerade das, was die späteren
Blöcke erst liefern. Die alte Regel hätte B08b auf einen Nachweis warten lassen,
den B10 erst möglich macht, und sie war für B05 faktisch schon gebrochen.

**B08b hat zwei ausgelieferte Blöcke abgeschlossen**, und das war sein Zweck:
B07 reichte `cost: { coins: 500 }` undurchsichtig durch, B08s Rangaufstieg
konnte an nichts scheitern. Beide hatten die Lücke benannt statt gefüllt
(Regel 5). Jetzt liest B07s Kostenblock jemand — **ohne dass B07 angefasst
wurde**, weil `ClassSourceInvariantsTest` die Vokabel dort verbietet und damit
vorgab, wo die Auslegung hingehört. `RankResult` hat sein `NOT_ENOUGH_COINS`,
und die Zusicherung im Test, dass es das *nicht* gibt, wurde umgedreht statt
gelöscht.

Zwei ADRs sind dabei entstanden: **ADR-028** (ein Kommando und ein Fenster in
einem Schicht-1-Block, befristet bis B14 und B13) und **ADR-029** (Herauslösung
des Anteilsrechners aus `XpDistributor`, damit Coins und Erfahrung denselben
Kill nicht unterschiedlich bewerten).

Als nächstes **`/specify` für B10 (Mobs & Hordenlogik)**. B09 ist seit dem
2026-08-23 implementiert; B10 ist der Block, auf den es jetzt zuläuft, und es
findet die benannten Spawn-Bereiche bereits vor.

**Warum nicht B11:** die Abhängigkeitstabelle in `01-architecture.md` führt B11
auf B04, **B09 und B10** zurück. B11 ist spezifikationsreif, aber nicht
umsetzbar — Loot braucht Mobs, und Mobs brauchen Zonen. Ein `/specify` für B11
wäre nicht falsch, es führte nur in eine Warteschleife.

**Warum B09:** es ist der einzige noch offene Block der Schicht 2, der allein
von B01 abhängt, und drei ausgelieferte Blöcke warten mit verdrahteten
Schnittstellen auf ihn: `WorldCondition.isOpenWorld` (B08, FR-052b),
`DamagePermission` samt der Zeile „B09 replaces this line with a per-zone rule"
(B05, FR-042) und `XpSource.ZONE_OBJECTIVE` (B06). Alle drei tragen heute eine
absichtlich freundliche Vorgabe — Regel 5 in der Praxis.

Der Steckbrief `blocks/B09-zones-regions.md` ist seit dem 2026-08-23
**vollständig beantwortet**: sechs benannte Regionen über die Levelbänder 1–60,
Region als Zone mit Schutzkern, Quader-Geometrie mit Chunk-Index, Portale in den
Safe-Zones, Warnung statt Sperre unter dem Levelband, PvP je Zone schaltbar mit
Vorgabe aus, und der Kampf-Logout als Tod. Ein ADR ist dabei entstanden und
bereits angenommen: **ADR-030** für `DeathCause.LOGOUT`, weil das ein
ausgeliefertes Enum in B05 anfasst.


### Was B09 eingelöst und was es benannt hat *(2026-08-23)*

Vier ausgelieferte Blöcke hatten eine Schnittstelle auf B09 warten. **Zwei sind
eingelöst**: `WorldCondition.isOpenWorld` (B08) beantwortet `ZoneWorldCondition`,
und `DamagePermission` (B05) ist durch `ZoneDamagePermission` **ersetzt** statt
kopiert — `SinglePermissionPointTest` und `DamagePermissionTest` sind dabei
unverändert geblieben, nachweisbar per `git log`.

**Zwei warten weiter, und das ist eine Aussage statt eines Versäumnisses:**
`XpSource.ZONE_OBJECTIVE` (B06) braucht Ziele innerhalb einer Zone, also Inhalt
statt Geometrie. `SourceKind` für zonengebundene Effekte (B04) brauchte den
Schwierigkeitsmodifikator, den `/clarify` aus dem Umfang genommen hat. Sie zu
füllen hiesse Werte zu erzeugen, die niemand liest.

B09 hat seinerseits zwei Eingriffe in abgeschlossene Blöcke gemacht, beide vorab
per ADR gedeckt: `DeathCause.LOGOUT` in B05 (ADR-030) und die Buchungsgründe
`WAYPOINT_TRAVEL` und `WAYPOINT_REFUND` in B08b (ADR-032). Fenster und
Rechtsklick liegen befristet in B09 und gehen an **B13**.

**Was B10 vorfindet:** benannte Spawn-Bereiche je Region, abfragbar über
`Zones.spawnAreasOf(zoneKey)` — Kennung und Geometrie, keine Rolle und keine
Kreaturenliste. Ein Bossbereich unterscheidet sich geometrisch von keinem
anderen; er unterscheidet sich in dem, was darin steht, und das gehört B10.

**Danach B10, dann B11.** B11s Neuzuschnitt ist mit ADR-027 abgeschlossen und
braucht keine Klärung mehr: Raritätsstufen bleiben als reines Etikett, der
Roll-Mechanismus entfällt — jedes Item hat feste Attributwerte —, und der
NPC-Händler gehört hierher. Die Buchungsgründe `VENDOR_SALE`,
`VENDOR_PURCHASE` und `REPAIR` stehen in B08b bereits bereit; B11 muss dafür
kein fremdes Enum anfassen. Seine Spezifikation kann jederzeit parallel zur
Umsetzung von B09 entstehen — blockiert ist die Umsetzung, nicht die
Beschreibung.

**B09/B10 schulden B08 drei Verhaltensweisen** (Aggro auf den Klon, Mobs wenden
sich von Unsichtbaren ab, Zonen für Zweites Leben). Die Schnittstellen stehen
und werden gerufen; sie antworten heute mit „nichts passiert".

*Nachtrag 2026-08-23:* Die dritte davon ist keine Schuld mehr, sondern eine
Entscheidung. Weil die Bosse in ihrer Region stehen und es zum Start **keine
Instanzen** gibt, bleibt `WorldCondition.isOpenWorld` überall bei ja — das
Zweitleben des Rogue wirkt überall, und das ist so gewollt. Die Schnittstelle
bleibt stehen, weil eine Instanzwelt nach ADR-006 jederzeit dazukommen kann.
Offen bleiben damit zwei Verhaltensweisen, und beide gehören B10.
