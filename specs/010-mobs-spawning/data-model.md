# Phase 1 · Datenmodell — B10 · Mobs & Horden-Spawning

Alles hier liegt in `rpg-core` und sieht kein Bukkit (Prinzip III). Was eine Entität ist, wie sie
gesetzt wird und wie sie verschwindet, steht in `rpg-platform`; dieses Modell beschreibt nur, *was*
gesetzt werden soll und *wer wen zählt*.

---

## `MobKind` — eine Mob-Art

Die Einheit, nach der die drei wartenden Schnittstellen fragen. Aus `mobs.yml`, beim Start gegen ein
Schema geprüft (FR-001, FR-002).

| Feld | Typ | Regel |
|---|---|---|
| `key` | `String` | Nicht leer, eindeutig über alle Arten. Der Wert, der im PDC landet. |
| `base` | `String` | Name eines Vanilla-Entity-Typs. Unbekannt → Start bricht ab (FR-002). |
| `level` | `int` | ≥ 1. Was die Anzeige nennt und woran ein Levelband sich messen lässt. |
| `attributes` | `Map<Attribute, Double>` | Über B04 gesetzt, nicht selbst gerechnet (FR-008). |
| `followRange` | `double` | Zielsuchreichweite in Blöcken, > 0 (FR-036, R6). |
| `displayNameKey` | `MessageKey` | Nie ein Text (FR-010, Prinzip V). |
| `xp` | `long` | ≥ 0. Was `MobXpProvider` für diese Art antwortet. |
| `coins` | `long` | ≥ 0. Was `MobCoinProvider` für diese Art antwortet. |
| `boss` | `boolean` | Nur ein Etikett — ein Boss ist eine Art mit höheren Zahlen (FR-034a). |

**Was hier absichtlich fehlt:** Fähigkeiten und Phasen. Ein Boss dieses Blocks hat keine (FR-034a);
der Dungeon-Boss kommt später und braucht Instanzen.

---

## `HordeSpec` — was in einer Zone steht

Je Zonenschlüssel. Verbindet B09s Bereiche mit den Arten dieses Blocks (FR-012).

| Feld | Typ | Regel |
|---|---|---|
| `zoneKey` | `String` | Muss eine Zone aus `zones.yml` sein. |
| `entries` | `List<HordeEntry>` | Nicht leer, sonst wäre die Zone still leer statt sichtbar falsch. |
| `boss` | `BossSpec` \| `null` | Höchstens einer je Zone (FR-029). |

### `HordeEntry`

| Feld | Typ | Regel |
|---|---|---|
| `areaKey` | `String` | Muss ein `SpawnArea`-Schlüssel dieser Zone sein (FR-011). |
| `kindKey` | `String` | Muss eine `MobKind` sein. |
| `weight` | `int` | ≥ 1. Relativer Anteil an der Dichte dieses Bereichs. |

### `BossSpec`

| Feld | Typ | Regel |
|---|---|---|
| `kindKey` | `String` | Eine `MobKind` mit `boss: true`. |
| `areaKey` | `String` | Ein Bereichsschlüssel derselben Zone (R10). |
| `offset` | `Vec3` | Versatz im Bereich. Nie eigene Weltkoordinaten. |
| `respawn` | `Duration` | > 0 (FR-031). |

---

## `Budget` — die vier harten Grenzen

Kein Zielwert, sondern eine Obergrenze, die unter keiner Bedingung überschritten wird (FR-013).

| Feld | Typ |
|---|---|
| `perZone` | `int` |
| `perChunk` | `int` |
| `perPlayer` | `int` |
| `serverWide` | `int` |

**Regel bei mehreren Grenzen:** Die schärfste entscheidet. Ein Chunk, der voll ist, nimmt nichts
mehr, auch wenn die Zone noch Platz hat — und umgekehrt.

**`serverWide` gilt auch gegen die Summe der Zonen** (FR-013a). Sechs Regionen zu je 130 sind 780
und bleiben darunter, aber sechs zu je 200 wären 1.200. Die serverweite Grenze ist der Ort, an dem
der Zielwert aus dem M4-Nachweis wirklich hängt; ohne sie steht er in der Vision und wird nirgends
eingehalten.

**Verhältnis zur Skalierung:** Die Skalierung aus FR-025 berechnet eine *Zieldichte*. Das Budget
kappt sie (FR-027). Beide Zahlen existieren nebeneinander, und ihre Rollen dürfen nicht
verschwimmen: `min(zieldichte, budget)`.

---

## `HordeRegistry` — der Bestand

Was dieser Block gerade in der Welt hält. Lebt im Speicher, nicht in der Datenbank: eine Kreatur
überlebt keinen Neustart (FR-023), also gibt es nichts zu persistieren.

| Feld | Typ | Wozu |
|---|---|---|
| `entityId` | `UUID` | Der Schlüssel. |
| `kindKey` | `String` | Für Werte, Erfahrung, Coins. |
| `zoneKey` | `String` | Für das Zonenbudget — die **Ursprungs**zone (FR-017). |
| `chunkKey` | `long` | Gepackt, wie in B09s Index. Für das Chunk-Budget (R3). |
| `spawnedAt` | `Instant` | Für Auswertung; die Aufräumfrist hängt am letzten Spieler, nicht hieran. |

**Abgeleitete Zählungen**, mitgeführt statt bei Bedarf gezählt (R3):

- `countByZone`: `Map<String, Integer>`
- `countByChunk`: `long → int`, nur belegte Chunks
- `total`: `int`

Ein Eintrag verschwindet, wenn seine Zählung auf null fällt. Nichts wächst dauerhaft.

---

## `BossState` — je Region

| Feld | Typ | Regel |
|---|---|---|
| `zoneKey` | `String` | |
| `aliveEntityId` | `UUID` \| `null` | Höchstens einer (FR-029). |
| `lastKilledAt` | `Instant` \| `null` | `null` heißt: noch nie gefallen, darf sofort erscheinen. |

**Der Timer wird nie als Aufgabe geführt** (FR-032). Ob der Boss darf, ist eine Frage an zwei
Zeitstempel — dieselbe Bauart wie Cooldowns in B08 und Regeneration in B04.

**Bemerkenswert:** `lastKilledAt` überlebt das Aufräumen (FR-034). Wird der Boss entfernt, weil
niemand mehr da ist, ist er nicht gefallen — der Timer läuft davon unberührt weiter, und er steht
wieder da, sobald jemand zurückkommt.

---

## Zustandsübergänge einer Kreatur

```
                  Budget frei + Bereich + Spieler in der Zone
   (nichts)  ──────────────────────────────────────────────►  lebend
                                                                │  │
                       getötet ─────────────────────────────────┘  │
                       (XP + Coins an B06/B08b, Bestand frei)      │
                                                                   │
                       aufgeraeumt ────────────────────────────────┘
                       (kein XP, keine Coins, kein Tod - FR-021)
```

Der Unterschied zwischen den beiden Ausgängen ist die eine Stelle, an der dieser Block etwas
verschenken oder etwas erfinden könnte, und deshalb ist er hier gezeichnet statt beschrieben.

---

## Was dieser Block **nicht** modelliert

- **Beute über Coins hinaus** — B11.
- **Statistiken über Kills** — B12 liest das Todesereignis, das B05 ohnehin veröffentlicht.
- **Eine eigene Kampfrechnung** — B04 und B05, ohne Parallelimplementierung (FR-008).
- **Eine Persistenz für Kreaturen** — es gibt keine über einen Neustart hinweg (FR-023).
