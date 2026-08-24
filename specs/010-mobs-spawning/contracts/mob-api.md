# Vertrag · Die öffentliche Schnittstelle von B10

Was andere Blöcke von B10 sehen dürfen. Alles andere in `rpg.core.mob` ist paketprivat oder
ausdrücklich als intern gekennzeichnet (Prinzip III: kein Zugriff auf Interna anderer Blöcke).

---

## 1 · Die drei Schnittstellen, die B10 **übernimmt**

Keine davon ist neu. Alle drei existieren, werden heute von einem Übergangsanbieter bedient, und B10
ersetzt den Anbieter — **ohne die Form zu ändern**. Das war die Zusage in `02-decisions.md`
Abschnitt 5 und im Javadoc von `MobXpProvider` und `MobCoinProvider`.

| Schnittstelle | Block | Form | Heute bedient von | Ab B10 |
|---|---|---|---|---|
| `MobStatProvider.statsFor` | B05 | `String → Optional<ModifierSet>` | `PaperMobStatProvider` aus `combat.yml` | B10 aus `mobs.yml` |
| `MobXpProvider.xpFor` | B06 | `String → OptionalLong` | B06s eigene Konfiguration | B10 aus `mobs.yml` |
| `MobCoinProvider.coinsFor` | B08b | `String → OptionalLong` | B08bs eigene Konfiguration | B10 aus `mobs.yml` |

**Was sich ändert, ist die Bedeutung des Strings, nicht sein Typ.** Bis heute ist er der
Vanilla-Typname (`creature.getType().name()`). Ab B10 ist er der Schlüssel der Mob-Art — und für
eine Kreatur ohne Art fällt er auf den Vanilla-Typnamen zurück (research.md R5). Damit gelten die
vorhandenen `combat.yml`-Einträge für ungekennzeichnete Kreaturen unverändert weiter.

**Ein leeres Ergebnis heißt weiterhin „kein eigener Eintrag" und niemals Null** (FR-007). Ein
ausdrückliches `0` in der Konfiguration heißt Null — die beiden bleiben unterscheidbar.

---

## 2 · `MobKinds` — was eine Kreatur ist

```
Optional<MobKind>  find(String kindKey)
Optional<MobKind>  ofEntity(UUID entityId)
List<MobKind>      all()
```

**Für wen.** B11 (Beute je Art), B12 (Bosskills zählen), B13 (Anzeige).

**Zusagen.**

- `find` mit einem unbekannten Schlüssel antwortet **leer und wirft nicht**. Dieselbe Regel, die B06
  für unbekannte Charaktere aufstellt: kein Aufrufer wird von einer Abfrage abgebrochen, und fünf
  Blöcke hängen an diesen Antworten.
- `ofEntity` antwortet leer für alles, was dieser Block nicht gesetzt hat — also für fast jede
  Entität der Welt.
- `all()` ist die Liste aus der Konfiguration, unveränderlich, nach dem Laden stabil.

---

## 3 · `Hordes` — was in einer Zone steht

```
int             countIn(String zoneKey)
int             total()
Optional<UUID>  bossOf(String zoneKey)
```

**Für wen.** B12 (Statistik), B14 (ein Betreiber will wissen, was los ist), B15 (Messung).

**Zusagen.**

- `countIn` mit einem unbekannten Zonenschlüssel antwortet **0**, nicht mit einer Ausnahme —
  dieselbe Entscheidung, die B09 für `spawnAreasOf` getroffen hat und aus demselben Grund: B10 fragt
  das aus einem Spawn-Ereignis heraus, und „außerhalb jeder Region" ist dort ein normaler Zustand.
- `bossOf` antwortet leer, wenn gerade keiner lebt — auch während des Respawn-Timers. Ob er
  *kommen darf*, ist nicht Teil dieses Vertrags; das ist Interna dieses Blocks.

---

## 4 · Was B10 **von anderen** verlangt

| Was | Von wem | Zustand |
|---|---|---|
| `Zones.spawnAreasOf(zoneKey)` | B09 | **vorhanden** — sechs Regionen, je zwei Bereiche |
| `Zones.zoneAt(position)` / Anwesenheit | B09 | vorhanden |
| Attributwerte setzen | B04 | vorhanden |
| Kampfzustand einer Kreatur (für FR-022) | B05 | vorhanden, lazy aus Zeitstempeln |
| Todesereignis | B05 | vorhanden |
| `grant(characterId, amount, XpSource)` | B06 | vorhanden |
| Coin-Gutschrift beim Kill | B08b | vorhanden |
| Einmalaufgabe, orts- oder entitätsgebunden | B01 | vorhanden (ADR-007, ADR-024) |

**Nichts davon ist neu zu bauen.** Das ist der Grund, warum dieser Block trotz seiner Größe keine
neue Naht in einen anderen Block schlägt.

---

## 5 · Was B10 **nicht** herausgibt

- **Keinen Zugriff auf den Bestand als veränderliche Struktur.** `Hordes` gibt Zahlen und eine Id,
  nie die Sammlung. Ein anderer Block, der eine Kreatur entfernen könnte, wäre eine zweite Stelle,
  an der das Budget auseinanderläuft.
- **Keine Methode, die eine Kreatur setzt.** Wer eine will, setzt sie über Bukkit; sie bekommt dann
  die Standardwerte (FR-018d, FR-009). Eine öffentliche `spawn(...)` wäre eine Umgehung des Budgets
  mit unserem eigenen Segen.
- **Keine Loot-Table.** B11.
