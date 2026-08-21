# Vertrag: `Progression`

**Block**: B06 | **Paket**: `rpg.core.progression` | **Stand**: 2026-08-20

Die öffentliche Schnittstelle des Blocks. B07, B08, B09, B11, B12, B13 und B14 entwickeln gegen
diese und gegen [party.md](./party.md); auf Interna von B06 greift niemand zu (Prinzip III).

---

## Vergabe

```java
/** Schreibt einem Charakter Erfahrung zu. Der einzige Eingangspunkt (FR-007). */
XpResult grant(UUID characterId, long amount, XpSource source);
```

- `amount` ≤ 0 oder nicht endlich → abgelehnt und protokolliert, **niemals** als Abzug gedeutet
  (FR-015). Rückgabe trägt den Ablehnungsgrund.
- Charakter ohne bereite Sitzung → still verworfen (FR-014). Kein Fehler, keine Ausnahme.
- Auf Maximallevel → still verworfen, kein Ereignis, keine Protokollzeile je Vorgang (FR-049,
  FR-050).
- `source` bestimmt, ob der Betrag geteilt wird. `ADMIN` wird nie geteilt (FR-048).

```java
/** Ergebnis einer Vergabe. */
record XpResult(long granted, long discarded, LevelUp levelUp, XpRejection rejection) {
    boolean rejected();      // rejection != NONE
    boolean leveledUp();     // levelUp != null
}
```

`XpRejection`: `NONE`, `INVALID_AMOUNT`, `SESSION_NOT_READY`, `AT_MAX_LEVEL`, `UNKNOWN_CHARACTER`.

**Warum ein Ergebnis und keine Ausnahme**: die Vergabe läuft im Kampfpfad. Eine Ausnahme je
abgelehntem Betrag wäre eine Objekterzeugung samt Stacktrace in genau dem Pfad, der nichts erzeugen
darf (FR-062).

---

## Abfrage

```java
/** Erfüllt der Charakter ein gefordertes Mindestlevel? (FR-025) */
boolean meetsLevel(UUID characterId, int requiredLevel);

/** Aktueller Fortschritt zum Anzeigen — rechnet nichts nach (FR-028). */
Optional<ProgressView> progressOf(UUID characterId);

/** Nur das Level, wenn mehr nicht gebraucht wird. */
OptionalInt levelOf(UUID characterId);

/** Das Maximallevel aus der Kurve, nicht aus einer Konstante (FR-004). */
int maxLevel();
```

- Alle Abfragen antworten **ohne Datenbankzugriff und ohne Neuberechnung** (FR-026, SC-011).
- `meetsLevel` für einen Charakter ohne Fortschrittsstand → `false` plus Protokolleintrag, **keine**
  Ausnahme (FR-027). Das ist die Zusage, auf die sich B08, B09 und B11 verlassen: eine Abfrage
  bricht nie einen Ablauf ab.

---

## Verwaltung

```java
/**
 * Setzt Level und XP frei — senken eingeschlossen (FR-024a).
 * Die einzige Stelle, an der ein Fortschritt sinken kann.
 */
XpResult setProgress(UUID actorId, UUID characterId, int level, long xpInLevel);
```

- `actorId` ist der ausführende Betreiber und geht mit altem und neuem Stand ins Audit-Log aus B02
  (FR-024b). Ohne ihn wäre der Eingriff nicht zuzuordnen — deshalb ist er Pflichtparameter und nicht
  optional.
- Löst dieselbe Neuberechnung und dieselben Ereignisse aus wie ein natürlicher Aufstieg (FR-024c).
- **Füllt bei einem gesenkten Level nicht auf**; ein über dem neuen Maximum liegender Ressourcenwert
  wird darauf begrenzt (FR-024c).
- `level` ausserhalb von 1 bis `maxLevel()` → `INVALID_AMOUNT`, kein Eingriff.

---

## Erweiterungspunkte

```java
/** Ersetzt die XP-Beträge je Mob-Art. B10 ruft das beim Start auf (FR-009). */
void setMobXpProvider(MobXpProvider provider);

/** Installiert die Reichweitenmessung. Ohne sie gilt nur der Beitragende als nah (FR-044). */
void setProximityCheck(ProximityCheck check);
```

```java
public interface MobXpProvider {
    /** XP für einen Mob-Typschlüssel; leer, wenn unbekannt — dann greift der Standardbetrag. */
    OptionalLong xpFor(String mobTypeKey);
}

public interface ProximityCheck {
    /**
     * Welche der Kandidaten sind vom Ort des gestorbenen Gegners nicht weiter als {@code range}
     * entfernt und in derselben Welt? (FR-041a, FR-045)
     *
     * <p>Schreibt die Treffer in {@code out} und gibt deren Anzahl zurück — kein neues Feld je
     * Aufruf, weil das im Kampfpfad läuft. {@code out} MUSS mindestens {@code party.max-size}
     * Plätze haben; der Aufrufer hält ein Feld dieser Grösse vor.
     *
     * <p>Der Ort kommt als Wert herein, nicht als Id des Wesens. Das Wesen ist tot: ein
     * Nachschlagen über {@code Bukkit.getEntity} gelingt nur, solange B05s Todesbehandlung läuft.
     * Diese Zeitbedingung wäre an einem öffentlichen Erweiterungspunkt unsichtbar, also liest der
     * Listener den Ort dort, wo er sicher gültig ist, und gibt ihn weiter.
     */
    int inRange(WorldPoint origin, UUID[] candidates, int candidateCount, double range, UUID[] out);
}
```

```java
/** Ein Ort ohne Bukkit-Abhängigkeit (FR-041a, FR-045). */
record WorldPoint(UUID worldId, double x, double y, double z) {
    /** Ohne Wurzel; verschiedene Welten ergeben unendlich, nicht eine Ausnahme. */
    double distanceSquaredTo(WorldPoint other);
}
```

**Beide Registrierungen erfolgen beim Start, nicht mitten im Spiel** — dieselbe Regel wie bei den
Erweiterungspunkten in B05.

---

## Lebenszyklus

```java
/** Lädt den Stand eines Charakters in den Speicher. Von B03 beim Sitzungsstart gerufen. */
void load(UUID characterId, ProgressState state);

/** Gibt alles zu einem Charakter frei — Stand, offenes Bündel, Party-Mitgliedschaft. */
void release(UUID characterId);
```

`release` ist die Zusage gegen Lecks: nach dem Aufruf hält B06 nichts mehr zu diesem Charakter. Das
offene Bündel wird dabei verworfen, nicht ausgeliefert (siehe data-model.md Abschnitt 5).

---

## Zusagen, auf die sich andere Blöcke verlassen dürfen

| Zusage | Anforderung | Nachweis |
|---|---|---|
| Kein Datenbankzugriff je XP-Ereignis | FR-054 | SC-004 |
| Keine Objekterzeugung je XP-Ereignis | FR-062 | SC-005 |
| Level sinkt im Spielverlauf nie | FR-024 | Aufstiegstests |
| Abfragen brechen nie einen Ablauf ab | FR-027 | eigener Test je Abfrage |
| Keine wiederkehrende Aufgabe | FR-061 | SC-012 |
| Eine Ausnahme bleibt auf den Charakter begrenzt | FR-059 | Fehlerbarrierentest |
