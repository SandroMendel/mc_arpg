# Contract · `hud-api` — was B13 anbietet und was es erwartet

B13 ist der letzte Block der Kette: **niemand hängt an ihm** (Blocksteckbrief, „Benötigt von: —").
Seine öffentliche Fläche ist deshalb klein und zeigt in die andere Richtung — sie besteht vor allem
aus dem, was er von zehn anderen Blöcken *liest*.

Was er trotzdem nach außen anbietet, ist die eine Sache, für die Constitution III.4 ihn überhaupt
verlangt: **eine Naht, hinter der ein pack-fähiger Renderer später an die Stelle des heutigen
treten kann, ohne dass Gameplay-Code sich ändert.**

---

## §1 · `HudRenderer` — die Naht aus ADR-005

```
void show(UUID playerId, HudSurface surface, MessageKey key, Map<String,String> values);
void clear(UUID playerId, HudSurface surface);
void bar(UUID playerId, BossBarOccasion occasion, MessageKey key,
         Map<String,String> values, double fraction);
void clearBar(UUID playerId, BossBarOccasion occasion);
```

**Zusagen:**

1. **Kein Aufruf bricht einen Aufrufer ab.** Ein Spieler, der gerade gegangen ist, eine Fläche, die
   abgeschaltet ist, ein Fehler beim Senden — alles davon ist ein normaler Ausgang und keine
   Ausnahme. Dieselbe Regel, die `MobKinds` und `Hordes` in B10 aufstellen, und aus demselben
   Grund: diese Methoden liegen in Pfaden, die jedes Ereignis berühren.
2. **Nie ein Text, immer ein Schlüssel** (FR-014). Die Signatur lässt gar nichts anderes zu — das
   ist der Punkt. Ein `String`-Parameter wäre die Einladung, die FR-015 dann maschinell wieder
   einsammeln müsste.
3. **`clearBar` ist nicht dasselbe wie `bar(..., 0.0)`.** Ein leerer Balken steht noch da; eine
   geräumte Bossbar ist weg. Zwei verschiedene Dinge brauchen zwei Methoden, sonst muss der Aufrufer
   eine Konvention kennen.
4. **`bar` entscheidet die Rangfolge nicht selbst.** Der Aufrufer meldet den *Anlass*; welcher
   gewinnt, entscheidet `BossBarPriority` an einer Stelle (FR-004). Sonst kennt jeder Aufrufer die
   Rangfolge ein bisschen anders.

**Was ausdrücklich nicht in dieser Naht liegt:** die Skill-Leiste. `AbilityHotbar` aus B08 bleibt,
wo sie ist, und wird nicht hinter `HudRenderer` gezogen (FR-024).

---

## §2 · `ItemRenderer`

```
ItemStack render(ItemId item, RenderContext context);
```

Die zweite Naht aus Constitution III.4, für die Darstellung von Gegenständen — Anzeigename, Lore,
Zustandsbalken. Sie liegt hier, weil ein Resource Pack genau an dieser Stelle etwas anderes tun
würde.

**Zusage:** Was ein Gegenstand *ist*, entscheidet B11. Diese Naht entscheidet nur, wie er aussieht.
Sie liest `Items` und `GearConditions` und schreibt in keines von beiden.

### Warum beide Nähte in `rpg-platform` liegen

`ItemRenderer` gibt einen Bukkit-`ItemStack` zurück und **kann** deshalb nicht in `rpg-core` liegen
(Constitution III.1). `HudRenderer` dagegen führt keinen einzigen Paper-Typ und ginge nach oben —
aber niemand in `rpg-core` will zeichnen, und eine Schnittstelle dort hätte weder Aufrufer noch
Implementierung in derselben Schicht. Sie steht deshalb bei ihrer einzigen Umsetzung. Sollte je ein
Kernmodul zeichnen wollen, ist der Umzug nach unten eine Verschiebung ohne Signaturänderung — die
Richtung, in der Constitution III.2 ihn erlaubt.

---

## §3 · Der Sammeltakt

**Es gibt genau einen** (FR-010, [research.md](../research.md) R1). Er ist die Erweiterung des
Takts, den `StatusActionBar.startRefresh` bereits führt, und kein zweiter daneben.

| Eigenschaft | Wert | Warum |
|---|---|---|
| Intervall | 1 s, konfigurierbar | unter den ~2 s, nach denen Minecraft die Actionbar ausblendet |
| Ausführung | **asynchron** | Constitution I.3 — 200 Spieler zu zeichnen gehört nicht in den Tick |
| Wiederholung | Selbsteinplanung | Der `Scheduler` hat keine wiederkehrende Aufgabe (ADR-007) |
| Umfang | **ein** Durchlauf über alle Spieler | Constitution II.2 verbietet eine Aufgabe je Spieler |

**Die Regel für alles, was in diesen Takt gehängt wird** (R2): aus einem asynchronen Kontext liefert
`runSyncOnEntity` für alles außer einem **Spieler** einen bereits abgebrochenen Handle — still, ohne
Fehler. Für Spieler ist er richtig; für alles andere gehört `runSyncAtLocation` dorthin. Wer eine
Entität erst im Tick auflöst, ist auf der sicheren Seite.

---

## §4 · Was B13 von anderen Blöcken liest

Alles über deren **öffentliche** Nähte. B13 fasst keinen fremden Block an — mit der einen benannten
Ausnahme `StatusActionBar`, die ihren eigenen Umzug im Javadoc ankündigt.

| Block | Gelesen | Wofür |
|---|---|---|
| B04 | `StatSnapshot.get`, `Attribute`, `revision()` | Charakterübersicht; die Revision ist die Ungültigkeitsmarke |
| B05 | `CombatStatusSource.statusOf`, `CombatState.isInCombat` | Actionbar, Bossbar-Füllstand, Ende des Bosskampfs |
| B05 | `DamageDealtEvent` | Schadenszahlen — **gebündelt**, nicht je Treffer |
| B06 | `ProgressView`, `ProgressChangedEvent`, `LevelUpEvent` | Sidebar, Actionbar |
| B07 | Klasse des Charakters | Charakterübersicht |
| B08 | `AbilityRegistry.remainingCooldown`, `RunningAbility`, `AbilityItemTag` | Cooldown-Overlay, Kanalisierungsbalken |
| B08b | `Currency` | Sidebar, Charakterübersicht, Kontofenster |
| B09 | `ZoneChangedEvent`, `Zones` | Zonenname auf Bossbar und Sidebar, Reisefenster |
| B10 | `MobKinds.ofEntity`, `MobKind.boss` | ob das Ziel ein Boss ist |
| B11 | `Items`, `GearConditions` | Ausrüstung und ihr Zustand in der Übersicht |
| B01 | `Messages`, `MessageKeyValidator`, `Scheduler` | Texte, Startprüfung, Takt |

**Nicht gelesen:** B02. B13 persistiert nichts (FR-013b).

---

## §5 · Die zwei übernommenen Fenster

Sie ziehen um, ihr Verhalten ändert sich nicht (FR-060, FR-061).

| Was | Von | Kommt mit | Bleibt |
|---|---|---|---|
| Reisefenster | B09 (ADR-032) | `WaypointMenu`, sein Listener **und** `CrystalInteractListener` | welche Wegpunkte offen sind, was eine Reise kostet |
| Kontofenster | B08b (ADR-028) | `CurrencyMenu` und sein Listener | `/coins`, der Verlauf, die Buchungsregeln |

**Die Asymmetrie ist Absicht.** ADR-032 nennt für B09 Fenster *und* Eingabe als befristet; ADR-028
nennt für B08b nur die Anzeige und weist Kommandos B14 zu. Eine Eingabegeste ist Präsentation, ein
Kommando mit Rechtebaum und Tab-Completion ist es nicht.

**Nach dem Umzug:** in B09 liegt kein Anzeige- oder Eingabecode mehr, in B08b allein die
`/coins`-Schale, die auf das übernommene Fenster verweist (FR-062). Beide ADRs bekommen einen
Schließvermerk.
