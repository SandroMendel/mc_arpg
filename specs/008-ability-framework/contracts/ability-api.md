# Vertrag · Die öffentliche Schnittstelle von B08

Der einzige Weg hinein. B12 und B13 werden dagegen gebaut; ein Griff an `AbilityRuntime`,
`CastState` oder die Effekt-Anwendungen vorbei ist unzulässig (Prinzip III, FR-068).

**Ab jetzt ist eine Änderung hier ADR-pflichtig** — dieselbe Regel, die `CombatPipeline` und
`StatEngine` für sich festgehalten haben.

---

## `AbilityRegistry` — lesen

```java
/** Die Fähigkeiten dieser Klasse, unabhängig vom Level. */
List<Ability> abilitiesOf(CharacterClass id);

/** Die Definition zu einer ID, oder leer. Löst nie eine Berechnung aus. */
Optional<Ability> find(String abilityId);

/** Was dieser Charakter gerade freigeschaltet hat - abgeleitet aus dem Level (FR-061). */
List<Ability> unlockedFor(UUID characterId);

/** Der Rang, den dieser Charakter auf dieser Fähigkeit hat. 1, wenn nie erhöht. */
int rankOf(UUID characterId, String abilityId);

/** Restlicher Cooldown, oder leer, wenn die Fähigkeit bereit ist. */
Optional<Duration> remainingCooldown(UUID characterId, String abilityId);

/** Restliche globale Sperre, oder leer. */
Optional<Duration> remainingGlobalLock(UUID characterId);

/** Der laufende Cast, oder leer. Für den Cast-Balken in B13. */
Optional<CastView> activeCast(UUID characterId);

/** Die laufende haltende Fähigkeit, oder leer. Höchstens eine (FR-045b). */
Optional<SustainedView> activeSustained(UUID characterId);

/** Verfügbare Ladungen, für Fähigkeiten mit mehr als einer (FR-045i). */
int chargesAvailable(UUID characterId, String abilityId);

/** Der Stand von Warriors Wut in [0, 100] - lazy gerechnet, nicht gespeichert (FR-016b). */
double meterValue(UUID characterId, String abilityId);

/** Die Spielereinstellung einer abschaltbaren Fähigkeit (FR-052d). */
ToggleState toggleOf(UUID characterId, String abilityId);
```

**Keine dieser Methoden rechnet** (FR-067). Sie lesen, was ohnehin da ist, oder vergleichen zwei
Zeitstempel. Das ist die Zusage, auf die B13 sich verlässt: das HUD fragt oft, und eine Frage darf
nichts kosten.

`CastView` ist eine Lesesicht auf `CastState` — Fähigkeits-ID, Beginn, Wirkzeitpunkt, Fortschritt als
Bruchteil. Der `TaskHandle` und das gebuchte Mana bleiben draußen; ein HUD hat damit nichts zu tun.

---

## `AbilityRuntime` — auslösen

```java
/**
 * Löst eine Fähigkeit aus.
 *
 * <p>Prüft in dieser Reihenfolge: Charakter aktiv, Fähigkeit freigeschaltet, kein laufender Cast,
 * globale Sperre frei, Einzel-Cooldown frei, Mana ausreichend. Die erste verletzte Bedingung
 * entscheidet, und keine davon verbraucht etwas (FR-024, FR-025).
 */
AbilityResult trigger(UUID characterId, String abilityId);

/** Bricht einen laufenden Cast ab. Mana wird vollständig erstattet (FR-041). */
void interrupt(UUID characterId, InterruptCause cause);

/**
 * Beendet eine laufende haltende Fähigkeit (ADR-025).
 *
 * <p><b>Zweiphasig.</b> Ist die Fähigkeit noch in der Vorbereitung, wird das Mana erstattet und kein
 * Cooldown gestartet. Wirkt sie bereits, bleiben die Kosten verbraucht und der Cooldown beginnt in
 * diesem Moment (FR-045d, FR-045e). Der Aufrufer entscheidet das nicht - der Zustand tut es.
 */
AbilityResult end(UUID characterId, EndCause cause);

/** Setzt die Spielereinstellung einer abschaltbaren passiven Fähigkeit (FR-052d). */
void setToggle(UUID characterId, String abilityId, ToggleState state);

/** Erhöht den Rang um eins und setzt den Höchstrang durch. */
RankResult advanceRank(UUID characterId, String abilityId);
```

`AbilityResult` ist ein Ergebnis, kein Wurf: `TRIGGERED`, `CASTING`, `ON_COOLDOWN`,
`GLOBAL_LOCK`, `NOT_ENOUGH_MANA`, `NOT_UNLOCKED`, `ALREADY_CASTING`, `NO_CHARACTER`. Jedes trägt den
Message-Schlüssel, mit dem der Spieler unterrichtet wird — ausgeworfen wird nur, was ein Programmfehler
ist.

`InterruptCause`: `DAMAGE_TAKEN`, `SLOT_CHANGED`, `MOVED`, `DIED`, `CHARACTER_SWITCHED`,
`DISCONNECTED`.

### Zu `advanceRank`

**Wer den Aufstieg bezahlt, entscheidet dieser Block nicht.** Es gibt im Projekt keine Währung — kein
Guthaben, keine Tabelle, keinen Verdienstweg. Die Methode setzt den Höchstrang durch und schreibt den
neuen Stand; ein Zahlweg kommt aus B11/B16 und ruft sie auf. Bis dahin ist sie über Verwaltung und
Tests erreichbar (FR-065, Workflow-Regel 5).

Das ist dieselbe Bauform, mit der B07 seinen `cost`-Block durchreicht, ohne ihn auszulegen.

---

## `ResourceRegeneration` — abrechnen

```java
/**
 * Rechnet auf, was seit der letzten Abrechnung aufgelaufen ist, und schreibt es in den Pool.
 *
 * <p>Zerlegt das Intervall exakt in Kampf- und Ruheanteil. Aufzurufen, bevor Mana geprüft, Schaden
 * angewandt oder eine Ressource gelesen wird (FR-037) - nicht periodisch.
 */
void settle(UUID characterId);

/** Vergisst einen Charakter. Beim Abmelden und beim Charakterwechsel. */
void forget(UUID characterId);
```

**Es gibt keine `tick()`-Methode und keine Aufgabe.** Wer das vermisst, hat den Punkt verfehlt: die
Regeneration passiert nicht zu einem Zeitpunkt, sie wird zu einem Zeitpunkt *festgestellt*.

---

## Was B08 bei anderen registriert

| Bei wem | Was | Wofür |
|---|---|---|
| `StatEngine.apply` | `ModifierSet` mit `SourceKind.BUFF` | `BUFF`, `DEBUFF` und passive Dauerwirkungen |
| `CombatPipeline.registerInterceptor` | je einer auf `MODIFIERS` und `APPLICATION` | die vier ereignisgebundenen Trigger (research.md R6) |
| `SessionAttachment` | ein Anhang | Hotbar aufbauen, Ränge laden, Regeneration anmelden |
| `EventBus` | `LevelUpEvent` | Slots bei Freischaltung nachziehen (FR-059, FR-060) |

**B05 wird nicht erweitert.** Alle vier Einhängepunkte existieren.

---

## Was B08 **nicht** anbietet

- **Keine Darstellung.** Cooldown-Anzeige, Mana- und Gesundheitsbalken, Cast-Balken sind B13. B08
  liefert die Auskunft, aus der gezeichnet wird — so, wie B07 die Warnung bei vollem Inventar hinter
  einer Schnittstelle gelassen hat.
- **Kein Zugriff auf Effekte von außen.** Ein anderer Block kann keine Effekt-Anwendung aufrufen. Wer
  Schaden will, nimmt `CombatPipeline`; wer einen Modifikator will, nimmt `StatEngine`. Die
  Primitives sind die *innere* Zusammensetzung einer Fähigkeit und keine Werkzeugkiste.
- **Keine Fähigkeit zur Laufzeit.** Definitionen werden beim Start geladen und sind danach
  unveränderlich. Ein Nachladen wäre möglich, ist aber nicht gefordert und würde die Zusage
  „unveränderlich, einmal je Server" aufweichen.
