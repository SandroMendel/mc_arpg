# Vertrag · Die öffentliche Schnittstelle von B09

Der einzige Weg hinein. B10, B11 und B13 werden dagegen gebaut; ein Griff am Index, an `zones.yml`
oder an den Listenern vorbei ist unzulässig (Prinzip III, FR-060).

**Ab jetzt ist eine Änderung hier ADR-pflichtig** — dieselbe Regel, die `CombatPipeline`,
`StatEngine`, `AbilityRegistry` und `Currency` für sich festgehalten haben.

---

## `Zones` — fragen, wo etwas ist

```java
/** Die Zone an diesem Ort, oder leer. "Keine Zone" ist ein gültiger Zustand (FR-007). */
Optional<Zone> zoneAt(WorldPosition position);

/** Dasselbe für den heissen Pfad: die Kennung oder null, ohne Optional-Allokation. */
String zoneKeyAt(WorldPosition position);

/** Liegt dieser Ort im Schutzkern seiner Zone? (FR-010) */
boolean inSafeCore(WorldPosition position);

/** Die Zone zu einer Kennung, oder leer. */
Optional<Zone> byKey(String zoneKey);

/** Alle Zonen, in der Reihenfolge der Konfiguration. Für Fenster und Übersichten. */
List<Zone> all();
```

**`zoneAt` gibt immer die Region zurück, nie den Schutzkern** (FR-011). Der Kern ist keine Zone; er
ist ein Bereich *innerhalb* einer. Wer wissen will, ob jemand geschützt steht, fragt `inSafeCore`.

**Leer heisst „keine Zone", nicht „Fehler."** Auf einem handgebauten Kontinent bleiben Zwischenräume,
und dort gelten die Vorgaben: kein PvP, keine Warnung, Ausweichpunkt beim Tod.

`zoneKeyAt` existiert aus demselben Grund wie `balanceOrZero` in B08b und `levelOrZero` in B06: der
Wert wird in Pfaden gebraucht, die keine Allokation je Aufruf versprechen (Prinzip II).

## `Zones` — Orte, die andere Blöcke brauchen

```java
/** Wohin ein neuer Charakter gehört: der Respawn-Punkt der Startregion (FR-037b). */
WorldPosition startPoint();

/** Wohin ein Tod in dieser Zone führt; leer, wenn die Zone keinen Schutzkern hat. */
Optional<WorldPosition> respawnPointOf(String zoneKey);

/** Der konfigurierte Ausweichpunkt für Tod ausserhalb aller Zonen (FR-034). */
WorldPosition fallbackPoint();
```

**Dieser Block liefert Orte, er versetzt niemanden** (FR-037c). Die Charaktererstellung bleibt bei
B07, die Anmeldung bei B03. Wer versetzt, ist die Plattformschicht dieses Blocks — und nur für Tod,
Logout-Nachwirkung und Reise.

## `Zones` — für B10

```java
/** Die benannten Spawn-Bereiche dieser Zone. Dieser Block spawnt nichts darin (FR-054). */
List<SpawnArea> spawnAreasOf(String zoneKey);
```

Kennung und Geometrie, nichts weiter. Mobarten, Attribute, Bosse und Hordenlogik gehören B10
(FR-053a).

## `WorldCondition` — eingelöst

B08s vorhandene Schnittstelle wird von diesem Block bedient (FR-052):

```java
boolean isOpenWorld(UUID characterId);   // solange es keine Instanzen gibt: überall true
```

**Die Antwort ist eine Entscheidung, keine unfertige Umsetzung.** Die Bosse stehen in ihrer Region,
Instanzwelten gibt es zum Start nicht. Der bisherige Standard `WorldCondition.everywhere()` bleibt
damit inhaltlich richtig — B09 setzt eine eigene Fassung ein, die dieselbe Antwort mit einer
Begründung gibt und sich ändern lässt, sobald eine Instanzwelt dazukommt.

## Ereignisse

```java
record ZoneChangedEvent(UUID characterId, Optional<String> from, Optional<String> to)
record SafeAreaCrossedEvent(UUID characterId, String zoneKey, boolean entered)
```

Genau ein Ereignis je tatsächlicher Änderung (FR-015, FR-018). Der Schutzkern hat sein eigenes, weil
er **kein** Zonenwechsel ist (FR-016).

## `Waypoints` — Freischaltungen

```java
/** Hat dieser Charakter diesen Kristall freigeschaltet? */
boolean isUnlocked(UUID characterId, String crystalKey);

/** Schaltet frei. Idempotent: ein zweiter Aufruf ändert nichts und meldet false. */
boolean unlock(UUID characterId, String crystalKey);

/** Alle Freischaltungen dieses Charakters. Für das Fenster. */
Set<String> unlockedBy(UUID characterId);
```

**Je Charakter, nie je Account** (ADR-011, FR-051a). Freischaltungen werden nie entzogen ausser mit
dem Charakter selbst (FR-051b1, FR-051b2).

## `Travel` — reisen

```java
TravelResult travelTo(UUID characterId, String crystalKey);

enum TravelResult {
    OK,
    NOT_UNLOCKED,      // sichtbar, aber gesperrt (FR-049)
    IN_COMBAT,         // FR-051f
    NOT_ENOUGH_COINS,  // FR-050a
    NO_SUCH_CRYSTAL,   // zwischen Öffnen und Klicken weggefallen
    TELEPORT_FAILED;   // abgebucht und zurückgebucht (FR-050f)

    boolean isSuccess();
    MessageKey messageKey();   // null bei OK
}
```

**Rückgabe statt Ausnahme** — dieselbe Form, die `BookingResult` in B08b hat: zu wenig Guthaben und
ein gesperrtes Ziel sind normale Spielausgänge, keine Fehler.

`TELEPORT_FAILED` ist der Ausgang aus `research.md` R1: abgebucht, Versetzung gescheitert,
zurückgebucht. Er ist ein eigener Wert, damit der Fall zählbar ist und nicht als
`NO_SUCH_CRYSTAL` durchgeht.

**Die Reihenfolge ist Teil des Vertrags** (FR-050e): freigeschaltet prüfen → Kampf prüfen → abbuchen →
versetzen → bei Fehlschlag zurückbuchen. Alles in derselben Tickphase.

---

## Was ausdrücklich **nicht** in diesem Vertrag steht

- **Kein `difficultyOf`, kein `lootTableOf`.** Bei `/clarify` herausgenommen (FR-056, FR-057). B10 und
  B11 ergänzen sie, wenn sie die Form kennen, die sie brauchen.
- **Kein `teleport`.** Versetzen ist Plattformsache; die Domäne liefert Orte.
- **Kein Anzeigename.** Verbraucher erhalten die Kennung und lösen den Text über
  `zone.<key>.name` auf (FR-003a, FR-058).
- **Kein Zonenziel.** `XpSource.ZONE_OBJECTIVE` bleibt unbefüllt — dieser Block definiert Zonen, keine
  Ziele (`research.md` R12).
