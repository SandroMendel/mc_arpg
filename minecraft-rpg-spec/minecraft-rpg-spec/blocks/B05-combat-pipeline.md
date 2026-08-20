# B05 · Kampf- & Schadens-Pipeline

| | |
|---|---|
| **Schicht** | 1 — Regel-Engine |
| **Status** | Implementiert (2026-08-20) — 120 Aufgaben, 570 Tests grün; Lasttest steht aus |
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

## Offene Fragen — geklärt (2026-08-20)

- [x] **Sekundärmechaniken**: keine. Kein Krit, kein Ausweichen, kein Blocken, keine
      Resistenztypen — B05 rechnet ausschliesslich mit den acht Attributen aus B04.
      Konsistent mit ADR-008.
- [x] **Schadensformel**: Rohschaden aus dem Angreifer-Snapshot, danach
      `DamageMitigation.afterDefense` aus B04. Die Reihenfolgefrage entfällt mit dem Krit.
- [x] **Angriffsgeschwindigkeit**: eigenes Cooldown-Modell je Angriff, zeitstempelbasiert
      und lazy ausgewertet. Zu frühe Schläge werden verworfen, nicht abgeschwächt.
      Vanilla-Waffencooldown wird abgeschaltet.
- [x] **Loot und XP**: XP anteilig nach Schadensanteil, Loot an den höchsten Beitrag.
      XP lässt sich teilen, ein Item nicht.
- [x] **PvP**: aus. Die Verzweigung existiert an genau einer Stelle; B09 füllt sie später
      mit einer Regel je Zone.
- [x] **Todesstrafe**: Ausrüstungsschaden. B05 meldet den Tod, B11 entscheidet über die
      Ausrüstung — kein Vorgriff auf Haltbarkeiten, die es noch nicht gibt.
- [x] **Schadenszahlen**: ja, aber zusammengefasst. Die Bündelung liegt in B05, das Zeichnen
      in B13. B05 erzeugt selbst keine Text-Displays.
- [x] **Vanilla-Schadensquellen**: Umgebungsschaden abbilden (Fall, Feuer, Lava, Void,
      Ertrinken, Explosion, Kaktus, Ersticken, Blitz, Magma), Statuseffekte abschalten
      (Verhungern, Wither, Poison, Instant Damage/Health, Absorption). Void bleibt tödlich.

## Akzeptanzkriterien (Entwurf)

- Kein Vanilla-Schaden erreicht jemals ungefiltert einen Spieler oder Custom-Mob.
- Die Herzleiste zeigt bei jedem Schadensereignis den korrekten Prozentwert.
- Lasttest: 150 Spieler gegen 800 Mobs im Dauerkampf halten p95 MSPT < 40 ms.
- Schadensberechnung ist unit-getestet mit dokumentierten Beispielrechnungen.

## Umsetzung (2026-08-20)

Spezifikation, Plan, Verträge und Aufgaben: `specs/005-combat-pipeline/`.
Umsetzungsentscheidungen: **ADR-014** in `02-decisions.md`.

### Die Liste der Schadensquellen oben war unvollständig

Dieser Steckbrief nennt 17 Vanilla-Schadensursachen. **Paper 26.2 kennt 33.** Die folgenden
sechzehn fehlten und sind bei der Umsetzung entschieden worden:

| Ursache | Behandlung |
|---|---|
| `CAMPFIRE`, `FALLING_BLOCK`, `FLY_INTO_WALL`, `FREEZE`, `DRYOUT`, `DRAGON_BREATH`, `SONIC_BOOM`, `WORLD_BORDER` | auf eigenen Schaden abgebildet |
| `ENTITY_SWEEP_ATTACK`, `THORNS`, `MELTING`, `CRAMMING`, `CUSTOM` | abgeschaltet |
| `SUICIDE` | tödlich, wie `KILL` |
| `CONTACT` | ist Kaktus und Süßbeerenstrauch |
| `HOT_FLOOR` | ist der Magma-Block |

Die vollständige Tabelle steht in `specs/005-combat-pipeline/contracts/damage-sources.md`. Umgesetzt
als erschöpfender Switch mit Verweigerungs-Standardfall: Eine künftig hinzukommende Ursache kann
keinen Schaden durchlassen, sondern erzeugt eine Protokollzeile.

### Nachträglich geklärt und hier festgehalten

- Umgebungsschaden ist ein **fester Betrag**, kein Anteil des maximalen Lebens, und Verteidigung
  greift dabei nicht. Gefahren sollen für Anfänger ernst und für Ausgerüstete belanglos werden.
- Beim Tod fällt **kein Inventar** — sonst wäre die gewählte Strafe (Ausrüstungsschaden durch B11)
  daneben bedeutungslos. Der Vanilla-Todesbildschirm bleibt.
- **Projektile** gehören zu B05. Ohne sie wäre ein Bogen ab Tag eins wirkungslos, weil der
  Vanilla-Pfeilschaden ohnehin neutralisiert wird.
- **Mobs bekommen ihre Werte von B05**, aus `combat.yml`, hinter einer Schnittstelle, die B10
  übernimmt. Ohne das wirkt die gesamte Pipeline auf nichts außer Spieler.
- B05 führt den **Kampfzustand** und veröffentlicht ihn — B08 braucht ihn bereits.
- Vanilla-Erfahrungskugeln und Vanilla-Beute werden beim Mob-Tod unterdrückt.
- Mobs verletzen einander nicht; die Erlaubnis fällt an genau einer Stelle, die B09 ersetzt.

**Offen:** Der Lasttest (150 Spieler gegen 800 Mobs, p95 MSPT < 40 ms). Prinzip VII nennt B05
ausdrücklich als lasttestpflichtig — bis dahin gilt der Block nicht als abgenommen.
