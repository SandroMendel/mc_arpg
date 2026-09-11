# Phase 1 · Datenmodell — B09 · Zonen & Regionen

Zwei Arten von Daten, streng getrennt: **Konfiguration**, die beim Start gelesen und beim Neuladen
ersetzt wird, und **Charakterzustand**, der einen Neustart übersteht. Nur das Zweite berührt die
Datenbank.

---

## 1 · Werte in `rpg-core/zone` — ohne Bukkit

### `Cuboid`

```java
record Cuboid(int minX, Integer minY, int minZ, int maxX, Integer maxY, int maxZ)
```

Zwei Ecken, normalisiert (min ≤ max je Achse). **`minY`/`maxY` dürfen leer sein** — dann gilt der
Quader über die ganze Welthöhe (FR-004). Ein Quader ohne Y-Grenzen erfasst Höhle wie Himmel; einer mit
Y-Grenzen erfasst einen Spieler darüber **nicht**, und das ist die Grundlage dafür, dass später eine
Zone über einer anderen liegen kann.

`contains(int x, int y, int z)` ist reine Ganzzahlarithmetik: sechs Vergleiche, keine Allokation.

### `Area`

```java
record Area(List<Cuboid> parts)
```

Ein Bereich ist eine Liste von Quadern — mindestens einer. **Dieselbe Form trägt Region, Schutzkern,
Spawn-Bereich und Kristall-Auslösebereich** (FR-004). Ein Geometriesystem, nicht vier.

`touchedChunks()` liefert die Chunk-Schlüssel, die die Teile überdecken — nur beim Laden gebraucht.

### `Zone`

```java
record Zone(
    String key,               // technische Kennung, eindeutig je Welt (FR-003)
    UUID worldId,             // eine Zone ist NIEMALS eine World (ADR-006)
    Area area,
    LevelBand levelBand,      // einschliessende Unter- und Obergrenze
    Optional<SafeCore> safeCore,
    List<SpawnArea> spawnAreas,
    Optional<WaypointCrystal> crystal,
    boolean pvp,              // Vorgabe false (FR-027)
    boolean startRegion)      // genau eine im ganzen Satz (FR-037a)
```

**Kein `displayName`.** Der sichtbare Name kommt aus `messages.yml` unter `zone.<key>.name`
(FR-003a). Die Zone trägt keinen Spielertext.

**Kein `difficultyModifier`, keine `lootTable`.** Bei `/clarify` herausgenommen (FR-056, FR-057) —
ein Feld, dessen Bedeutung dieser Block nicht kennt, kann er nicht prüfen.

### `SafeCore`

```java
record SafeCore(Area area, WorldPosition respawnPoint)
```

Der **eine Ankunftsort je Region** (FR-037d). Er dient dreimal:

| Zweck | Anforderung |
|---|---|
| Spielbeginn — nur in der Startregion | FR-037b |
| Tod in dieser Region | FR-033 |
| Reiseziel des Kristalls dieser Region | FR-045a |

`WorldPosition` ist der bereits vorhandene Typ aus `rpg-core/scheduler` (research.md R2) — kein
zweiter Ortstyp im Projekt.

### `SpawnArea`

```java
record SpawnArea(String key, Area area)
```

Kennung und Geometrie, **und nichts sonst** (FR-053a). Keine Rolle, keine Art, keine Mobliste, keine
Zahl. Die Kennung ist der Anknüpfungspunkt, den B10 belegt.

### `WaypointCrystal`

```java
record WaypointCrystal(String key, Area triggerArea, long price)
```

**Kein Ziel.** Gereist wird an den Respawn-Punkt der Zone, zu der der Kristall gehört (FR-045a). Der
Preis steht hier, bei dem, der ihn verlangt — nicht in `currency.yml` und nicht in einem zentralen
Katalog (ADR-027, FR-050c).

### `ZoneRules` — was ausgewertet wird

Levelband, PvP-Schalter, Respawn-Punkt, Startregion-Markierung. **Jede dieser vier wird in diesem
Block auch benutzt.** Das ist der Prüfstein, der Schwierigkeitsmodifikator und Loot-Zuordnung
ausgeschlossen hat.

---

## 2 · Der Index

### `ChunkZoneIndex`

Je Welt eine Abbildung von gepacktem Chunk-Schlüssel auf Kandidaten:

```
key  = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL)
wert = Zone[] kandidaten  +  boolean grenzchunk
```

- Beim Laden gebaut, zur Laufzeit **nur gelesen** — kein veränderlicher globaler Zustand
  (Prinzip I).
- `long`-Schlüssel, kein `HashMap<Long, …>`: kein Boxing im heissen Pfad (Prinzip II).
- Ein Kandidat → keine weitere Prüfung. Mehrere → exakter Quadertest in Reihenfolge.
- **Grenzchunk** ist markiert, wenn mehr als ein Bereich ihn berührt (auch Schutzkern oder Kristall).
  Er entscheidet, ob eine Bewegung innerhalb eines Chunks ausgewertet wird (research.md R4).

Ein zweiter, gleich gebauter Index bildet Chunk → Kristalle ab (research.md R5).

**Lineare Iteration über alle Zonen ist an keiner Stelle zulässig** (FR-005, Prinzip II).

---

## 3 · Ereignisse

```java
record ZoneChangedEvent(UUID characterId, Optional<String> from, Optional<String> to)
record SafeAreaCrossedEvent(UUID characterId, String zoneKey, boolean entered)
```

Zwei Typen und nicht einer (research.md R9): der Schutzkernwechsel ist **kein** Zonenwechsel
(FR-016). Beim Zonenwechsel darf eine Seite leer sein — Betreten aus der Wildnis, Verlassen in die
Wildnis.

Genau ein Ereignis je tatsächlicher Änderung (FR-015, FR-018), auch bei Teleport, Anmeldung,
Charakterwechsel und nach einem Neuladen (FR-017).

---

## 4 · Charakterzustand in der Datenbank

Ein Aggregattyp, zwei Tabellen (research.md R8). Migration `V9_1__character_zone_state.sql`.

### `rpg.character_waypoints`

| Spalte | Art | Bemerkung |
|---|---|---|
| `character_id` | UUID | `REFERENCES rpg.character (character_id) ON DELETE CASCADE` |
| `crystal_key` | text | die Kennung aus der Konfiguration |
| `unlocked_at` | timestamptz | wann freigeschaltet |

Primärschlüssel `(character_id, crystal_key)`. **Wächst nur** — keine Zeile wird je gelöscht ausser
mit dem Charakter (FR-051b2).

Die Tabelle kennt **keinen** Fremdschlüssel auf eine Zone: die Kennung eines Kristalls kann
vorübergehend aus der Konfiguration verschwinden, und die Freischaltung soll das überleben
(FR-051b). Eine verwaiste Zeile ist hier ein gültiger Zustand, kein Datenfehler.

### `rpg.character_zone_state`

| Spalte | Art | Bemerkung |
|---|---|---|
| `character_id` | UUID | Primärschlüssel, `ON DELETE CASCADE` |
| `pending_respawn_zone` | text, nullable | gesetzt beim Kampf-Logout (research.md R11) |

Eine Zeile je Charakter, nur wenn etwas aussteht. Beim nächsten Anmelden wird sie gelesen, angewandt
und geleert.

### Die drei Eintragungen (ADR-015 Punkt 7)

1. `AggregateType.CHARACTER_ZONE_STATE` als Konstante.
2. Ihr Platz in der Schreibreihenfolge — **nach** `CHARACTER`, weil der Fremdschlüssel darauf zeigt.
3. `persistence.flushCycle().register(AggregateType.CHARACTER_ZONE_STATE, repository)`.

Fehlt die dritte, werden die Marken bei jedem Flush als fehlgeschlagen gezählt — das steht als
Warnung schon im Javadoc von `AggregateType`.

---

## 5 · Konfiguration — `zones.yml`

Vollständig konfigurationsdefiniert, beim Start gegen ein Schema geprüft, Fail-Fast bei jedem Fehler
(FR-001, FR-013, Prinzip V). Aufbau in [contracts/zone-config.md](./contracts/zone-config.md).

Was den Start **verhindert**:

| Fehler | Anforderung |
|---|---|
| Eine Zone verweist auf eine **unbekannte Welt** | FR-002a |
| Zwei Zonen mit überlappenden Quadern in derselben Welt | FR-012 |
| Doppelte Zonenkennung | FR-003 |
| Schutzkern nicht vollständig in seiner Zone | FR-009 |
| Spawn-Bereich ausserhalb seiner Zone oder im Schutzkern | FR-055 |
| Doppelte Spawn-Bereich-Kennung in derselben Zone | FR-055 |
| Kristall in einer Zone ohne Schutzkern | FR-051c |
| Kristall-Auslösebereich ausserhalb seiner Zone | FR-051d |
| Doppelte Kristallkennung | FR-051d |
| Fehlender Message-Schlüssel zu einer Zone | FR-003c |
| Keine oder mehr als eine Startregion | FR-037a |

Kein Vorrangregelwerk zur Laufzeit: was nicht vorkommen darf, wird beim Start abgewiesen, nicht
stillschweigend aufgelöst.

---

## 6 · Was dieser Block **nicht** speichert

- **Zonen selbst.** Sie stehen in der Konfiguration. Die Zuordnung eines Spielers wird zur Laufzeit
  ermittelt, nie geschrieben.
- **Die aktuelle Zone eines Charakters.** Sie ist aus seiner Position ableitbar; sie zu speichern
  hiesse, zwei Wahrheiten zu pflegen.
- **Spielerpositionen.** Die gehören Minecraft.
- **Coin-Buchungen.** Die gehören B08b; dieser Block ruft sie auf.
