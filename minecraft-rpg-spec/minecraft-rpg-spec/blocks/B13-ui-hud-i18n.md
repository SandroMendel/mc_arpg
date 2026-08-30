# B13 · UI, HUD & Texte

| | |
|---|---|
| **Schicht** | 3 — Präsentation |
| **Status** | Entwurf |
| **Abhängig von** | B04, B08, B09 |
| **Benötigt von** | — |

## Zweck

Sämtliche Darstellung gegenüber dem Spieler — unter der Einschränkung eines
reinen Vanilla-Clients (ADR-005).

## Umfang

- HUD: Actionbar, Bossbar, Scoreboard, Title
- Anzeige von HP, Mana, Cooldowns, Zone, Level und XP-Fortschritt
- Skill-Leiste als Eingabemethode
- GUIs: Klassenwahl, Charakterübersicht, Statistiken, Leaderboard, ggf. Skilltree
- Zentrales Message-System mit Schlüsseln
- Herzleiste als Prozentanzeige (ADR-003)

## Einschränkungen durch Vanilla-Client (ADR-005)

| Bereich | Verfügbar | Nicht verfügbar |
|---|---|---|
| HUD | Actionbar, Bossbar, Scoreboard, Title | Eigene HUD-Elemente, freie Positionierung |
| Eingabe | Hotbar-Slots, Links-/Rechtsklick, Sneak-Kombination, Offhand-Swap | Eigene Keybinds |
| Item-Optik | Vanilla-Materialien | Custom-Model-Data ohne Pack |
| Mob-Optik | Vanilla-Entities, Display-Entities | Eigene Modelle |

## Architekturvorgaben

- Rendering liegt hinter Schnittstellen (`HudRenderer`, `ItemRenderer`), damit ein
  pack-fähiger Renderer später eingesetzt werden kann, ohne Gameplay-Code
  anzufassen.
- **Keine hartcodierten Spielertexte.** Alle Texte laufen über Message-Schlüssel;
  Englisch ist die Standardsprache, weitere Sprachen sind strukturell möglich.
- HUD-Aktualisierung erfolgt ereignisgesteuert bei Wertänderung, zusätzlich mit
  einem gedrosselten Takt für Zeitanzeigen (Cooldowns) — nicht pro Tick pro
  Spieler.
- GUI-Inhalte werden gecacht und nur bei Änderung neu aufgebaut.

## Offene Fragen — beantwortet am 2026-08-30

Die Roadmap verlangt, dass diese Liste vor `/specify` beantwortet ist: unbeantwortete
Fragen erzeugen erfundene Annahmen. Hier stehen die Antworten, mit denen die Spec
geschrieben wird.

- [x] **Aufteilung: Was gehört in Actionbar, was in Bossbar, was ins Scoreboard?**
      Drei Flächen, drei Rollen, nichts konkurriert.
      **Actionbar** trägt die laufenden Werte: HP, Mana, Verteidigung, Cooldown-Hinweise —
      das, was `StatusActionBar` heute schon sendet. **Bossbar** trägt das Situative:
      Zonenname beim Betreten, Bosskampf, kanalisierte Fähigkeiten; sie ist heute
      unbenutzt. **Scoreboard** trägt die Sidebar mit Level, XP, Coins und Zone; ebenfalls
      heute unbenutzt. Die Vanilla-XP-Leiste bleibt, was `ExperienceBar` aus ihr macht.

- [x] **Konkretes Layout der Skill-Leiste**
      Steht bereits und bleibt: `AbilityHotbar` aus B08 legt Slot 0 als B07s gebundene
      Waffe und ab Slot 1 je Fähigkeit ein Item; ein nicht freigeschalteter Slot bleibt
      leer. B13 baut das **nicht** um und zieht es auch nicht hinter `HudRenderer` —
      es läuft, es ist getestet, und ein Umbau an fremdem funktionierendem Code wäre der
      teuerste Weg zu keinem sichtbaren Unterschied. B13 legt nur die Cooldown-Darstellung
      darüber.

- [x] **Wie werden Cooldowns ohne eigene Icons dargestellt?**
      Über das **Vanilla-Cooldown-Overlay**: `player.setCooldown(Material, Ticks)` zeichnet
      die graue Sweep-Animation direkt über dem Fähigkeits-Item im Slot. Keine eigene
      Anzeige, kein Pack, und die Geste kennt jeder Spieler von der Enderperle.
      **Bekannte Grenze:** die Anzeige gilt je *Material*, nicht je Slot — zwei Fähigkeiten
      auf demselben Item teilten sich eine Anzeige. Die Spec muss das entweder ausschließen
      (Materialien je Klasse eindeutig) oder benennen.

- [x] **Welche GUIs werden zum Start benötigt?**
      Vier: **Klassenwahl** (löst B07s heutige Befehlsfassung ab), **Charakterübersicht**
      (Attribute, Ausrüstung, `GearConditions` aus B11, Coins aus B08b),
      **Statistiken & Leaderboard** (B12 liefert die Daten; heute gibt es nur das
      Hologramm) und das **Reisefenster** (löst B09s nach ADR-032 ausdrücklich befristetes
      Provisorium ab). Kein Skilltree zum Start.

- [x] **Werden Schadenszahlen angezeigt?**
      Ja, als **kurzlebige Display-Entities** am Trefferort — dieselbe Technik, die B12s
      Hologramm auf echtem Paper bereits belegt hat. B05s `DamageAggregator` bündelt schon;
      B13 bekommt also eine Zahl je Treffer und keinen Strom.

- [x] **Aktualisierungsfrequenz des HUD?**
      **Ereignisgesteuert plus ein Sammeltakt pro Sekunde** für alle Spieler in einem
      Durchlauf. Wertänderungen zeichnen sofort; der Sekundentakt trägt die Actionbar
      (Minecraft blendet sie nach etwa zwei Sekunden aus, ein dauerhafter Wert heißt also
      Nachsenden) und die Zeitanzeigen. Das ist, was `StatusActionBar` heute tut, und es
      passt zum Akzeptanzkriterium „unter 1 ms pro Tick bei 200 Spielern".

## Akzeptanzkriterien (Entwurf)

- HUD-Aktualisierung für 200 Spieler bleibt unter 1 ms pro Tick.
- Kein Spielertext ist im Code hartcodiert (per Test oder Lint nachgewiesen).
- Die Herzleiste zeigt in allen Situationen den korrekten Prozentwert.
- Ein Wechsel des `HudRenderer` erfordert keine Änderung an B04, B05 oder B08.

---

## Gebaut am 2026-08-30

**168 von 177 Aufgaben.** Offen sind nur noch Deploy und die Serverabnahme (19 Schritte).
`clean build`: **3103 Tests, 0 Fehler, 0 übersprungen.**

### Die fünf Dinge, die benutzt statt gebaut wurden

Die Recherche gegen den fertigen Code (research.md) hat fünf Stellen gefunden, an denen B13 nichts
Neues brauchte. Alle fünf haben gehalten:

1. **Der Sekundentakt existierte.** `StatusActionBar.startRefresh` war bereits eine Sekunde lang,
   aus genau dem Grund, den FR-011 nennt, und plante sich nach ADR-007 selbst neu ein. `HudTick`
   ist seine **Erweiterung** und kein zweiter daneben; `startRefresh` ist dafür entfallen.
2. **Die Bossabfrage brauchte nichts Neues.** `DamageDealtEvent.targetId` → `MobKinds.ofEntity` →
   `CombatStatusSource.statusOf`. Der dritte Punkt ist der, den man übersieht: `CombatStatusSource`
   sieht nach einer Spielerschnittstelle aus, antwortet aber auch für Kreaturen — beide gehen durch
   dieselbe Engine.
3. **Die Kanalisierung lag fertig in `RunningAbility`** (`startedAt`, `dueAt`, `phase`).
4. **Die Cooldown-Restzeit kommt aus `AbilityRegistry.remainingCooldown`** — dieselbe Methode, die
   `AbilityRuntime` selbst benutzt.
5. **Die Sprachprüfung war gebaut.** `MessageKeyValidator` meldet seit B01 alle fehlenden Schlüssel
   auf einmal. B13 ändert nur, welche Datei gelesen wird.

### Was die Spec nicht wusste

Fünf Annahmen hat der Code widerlegt, alle erst beim Bauen:

| Annahme | Wirklichkeit |
|---|---|
| Es sind acht Attribute | **Zehn** — die Zahl stammte aus einem Roadmap-Ziel, nicht aus einer Aufzählung |
| `ItemId` und `RenderContext` | Gibt es nicht. B11 führt `templateKey` und `GearCondition` |
| Ausrüstung ist B11s | **B07s** — `items.yml` kennt nur sieben Tränke und drei Trims, keine Rüstung |
| Zustand läuft in `[0,1]` | **`[0,100]`** — `WearCurve.FULL` ist 100.0 |
| Fortschritt gehört der Actionbar | Level und Erfahrung stehen auf der **Sidebar** (FR-002a) |

Die letzte war eine echte Entscheidung: FR-002 gab der Actionbar den Fortschritt, FR-005 der Sidebar
Level und Erfahrung — **dieselben Zahlen**. `StatusActionBar.progressText` ist entfallen.

### Drei Zusagen, die grün waren und nichts bewiesen

Der Block hat dreimal denselben Fehlermodus produziert, und alle drei sind gefunden worden:

- **FR-004c war nie verdrahtet.** `PaperBossBar.forget` existierte, aber niemand rief es. Der Test
  dazu prüfte die Klasse direkt und hat die fehlende Verdrahtung nie gesehen. Gefunden nur als
  Nebeneffekt, weil `FullBootstrapTest` einen unerlaubten `PlayerQuitEvent`-Handler fand.
- **FR-042 war nicht geprüft.** `isVisibleByDefault()` ist in MockBukkit nicht implementiert und
  wird als **„skipped"** gemeldet, nicht als Fehler. Gefunden nur durch die Skip-Zählung.
- **`ItemStack` als Wächter-Marker** schlug bei `CoinPickupListener` an — Coin-Haufen am Boden sind
  B08bs Mechanik, kein Fenster.

### Zwei ADRs geschlossen, zwei geschrieben

- **ADR-032 vollständig eingelöst**: `WaypointMenu`, sein Listener **und** `CrystalInteractListener`
  liegen in `rpg.platform.ui`.
- **ADR-028 zur Hälfte**: das Kontofenster ist umgezogen, `/coins` wartet weiter auf B14.
- **ADR-051** (neu): `/char`, befristet, nach dem Muster von ADR-028.
- **ADR-052** (neu): `AbilityHotbar` bleibt vor der Schnittstelle — eine benannte Ausnahme von
  Constitution III.4. Sie stand vorher nur unter *Assumptions*, und das ist die stille
  Neuinterpretation, die die Governance ausschließt.

### Was B13 ausdrücklich nicht getan hat

`AbilityHotbar` (ADR-052), `ClassSelectionMenu` (B07), `StatisticsMenu` und `LeaderboardMenu` (B12),
`MobNameplate`, `PaperVanillaAttributeBridge`, `ExperienceBar`, `PaperClassNotice`,
`GearConditionDisplay`, `ItemText`. Bewacht von `UntouchedBlocksStayUntouchedTest`.

Und es **persistiert nichts**: kein Schema, keine Tabelle, keine Migration (`UiPersistsNothingTest`).
