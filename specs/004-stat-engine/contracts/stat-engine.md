# Vertrag: `StatEngine` — die öffentliche Schnittstelle von B04

**Feature**: `specs/004-stat-engine` | **Datum**: 2026-08-20

Dies ist die einzige Schnittstelle, über die B05 bis B13 auf Werte zugreifen. Zugriff auf Interna
(`StatHolder`, `DefaultStatEngine`, die Quellenkarte) ist unzulässig (Prinzip III).

Registriert wird sie beim Start durch `StatsModule` unter dem Dienstschlüssel `StatEngine.class`,
abrufbar über `ModuleContext.registry()` — dasselbe Muster wie `SessionRegistry` in B03.

---

## 1 · Werte lesen

```java
/** Der aktuelle Schnappschuss eines Trägers. */
StatSnapshot snapshot(UUID holderId);

/** Wie snapshot, aber leer statt Ausnahme, wenn der Träger unbekannt ist. */
Optional<StatSnapshot> findSnapshot(UUID holderId);

/** Bequemer Einzelwert; entspricht snapshot(holderId).get(attribute). */
double value(UUID holderId, Attribute attribute);
```

**Verhalten**

- Kein Rechenaufwand, wenn sich seit der letzten Berechnung nichts geändert hat (FR-022) — der
  gespeicherte Schnappschuss wird unverändert zurückgegeben.
- Steht eine Vormerkung aus, wird der zuletzt gültige Schnappschuss geliefert, nicht neu gerechnet.
  Ein Wert ist damit höchstens einen Tick alt (Edge Case in der Spec, gedeckt durch FR-021).
- Für einen Spieler, dessen Sitzung noch nicht bereit ist, wirft `snapshot` eine
  `SessionNotReadyException` — derselbe Typ, den B03 bereits verwendet. **Keine** Standardwerte
  (FR-037).
- Für einen unbekannten Träger wirft `snapshot` `NoSuchElementException`; `findSnapshot` liefert
  `Optional.empty()`.

**Der wichtigste Nutzungshinweis für B05**: Ein Schnappschuss wird **einmal** zu Beginn eines
Vorgangs gezogen und dann festgehalten. Ein fliegendes Projektil, eine laufende Fähigkeit, eine
mehrstufige Kampfhandlung rechnen bis zum Ende mit dem Stand ihres Auslösezeitpunkts (FR-021).
Wiederholtes Abfragen mitten im Vorgang ist ein Fehler, kein Feature.

---

## 2 · Beiträge setzen und entfernen

```java
/** Setzt die Beiträge einer Quelle; ein vorhandener Satz derselben ID wird ersetzt (FR-008). */
void apply(UUID holderId, ModifierSet set);

/** Setzt mehrere Quellen in einem Zug; erzeugt trotzdem nur eine Vormerkung. */
void applyAll(UUID holderId, Collection<ModifierSet> sets);

/** Entfernt alle Beiträge dieser Quelle (FR-007). Unbekannte Quelle: wirkungslos (FR-018). */
void remove(UUID holderId, SourceId source);

/** Entfernt alle Quellen einer Art — etwa alle Ausrüstungsbeiträge auf einmal. */
void removeKind(UUID holderId, SourceKind kind);
```

**Verhalten**

- Jeder dieser Aufrufe vermerkt den Träger zur Neuberechnung und plant, falls noch keine
  Vormerkung stand, genau eine entitätsgebundene Aufgabe (FR-019). Mehrere Aufrufe im selben Tick
  ergeben eine Neuberechnung.
- Kein Aufrufer muss seine Änderungen klammern (FR-019a). `applyAll` ist Bequemlichkeit, keine
  Voraussetzung für die Bündelung.
- Ein Beitrag auf ein unbekanntes Attribut ist im Typsystem ausgeschlossen; ein unbekannter
  **Schlüssel** aus der Konfiguration wirft `UnknownAttributeException` (FR-009, FR-004a).
- `NaN` oder `Infinity` als Wert werden mit `IllegalArgumentException` abgelehnt, nicht still
  weitergereicht.
- Für einen Spieler ohne bereite Sitzung werden Änderungen abgelehnt statt ins Leere geschrieben
  (FR-037).

---

## 3 · Herkunft nachvollziehen (FR-010)

```java
/** Alle Quellen, die zu diesem Attribut beitragen, mit ihrem jeweiligen Beitrag. */
List<AttributeContribution> contributions(UUID holderId, Attribute attribute);

record AttributeContribution(SourceId source, ModifierOperation operation, double value) {}
```

Liest ausschließlich die vorhandene Quellenkarte und löst **keine** Neuberechnung aus. Gedacht für
`/stats`-Werkzeuge in B14 und für die Fehlersuche.

---

## 4 · Träger verwalten

```java
/** Legt einen Träger für einen Spielercharakter an; liefert dessen Kennung zurück. */
UUID createForCharacter(UUID playerId, UUID characterId, ResourcePool initial);

/** Legt einen Träger ohne Spielerbezug an — für Mobs (B10, FR-035). */
UUID createForEntity(UUID entityId);

/** Entfernt einen Träger samt Quellen und Schnappschuss (FR-036). */
void remove(UUID holderId);

/** Rechnet sofort und synchron; umgeht die Bündelung (FR-019b). */
StatSnapshot recalculateNow(UUID holderId);
```

**Verhalten**

- `createForCharacter` gibt die Spieler-UUID zurück, `createForEntity` die Entity-UUID — dieselbe
  Kennung, die alle übrigen Methoden als `holderId` erwarten. Ein eigener Handle-Typ wäre eine
  zweite Kennung für dieselbe Sache und damit eine Gelegenheit, die falsche zu verwenden.
- `remove` ist mehrfach aufrufbar und wirft nicht. Eine noch ausstehende Vormerkung verfällt
  folgenlos.
- `recalculateNow` ist für zwei Fälle da: den Ladepfad, der vor der Freigabe des Spielers ein
  Ergebnis braucht, und Träger ohne Entität, für die kein entitätsgebundener Scheduler existiert.
  Im normalen Spielbetrieb wird es nicht aufgerufen.
- Nach `remove` liefert `findSnapshot` `Optional.empty()`; bereits herausgegebene Schnappschüsse
  bleiben gültig und lesbar.

---

## 5 · Ressourcen (FR-025 bis FR-029)

```java
/** Der aktuelle Stand. */
ResourceView resources(UUID holderId);

/** Verändert das aktuelle Leben um delta; klemmt auf [0, maxHealth]. */
double changeHealth(UUID holderId, double delta);

/** Verändert das aktuelle Mana um delta; klemmt auf [0, maxMana]. */
double changeMana(UUID holderId, double delta);

record ResourceView(double currentHealth, double maxHealth,
                    double currentMana, double maxMana) {}
```

**Verhalten**

- Rückgabewert ist der Stand **nach** dem Klemmen — der Aufrufer erfährt damit, wie viel
  tatsächlich verbraucht oder gutgeschrieben wurde.
- Jede Änderung veröffentlicht ein `ResourceChangedEvent` (siehe [events.md](./events.md)) und
  vermerkt den Charakter zum Schreiben über B02s Write-Behind. Für Träger ohne `characterId`
  entfällt der Schreibvermerk.
- Erreicht das Leben null, wird das im Ereignis kenntlich gemacht. **B04 löst keinen Tod aus** —
  das ist B05.
- Ein Aufruf mit `NaN` wird abgelehnt.

---

## 6 · Beitragslieferanten für Basiswerte (FR-039)

```java
public interface BaseStatContributor {
    String id();
    /** Trägt Basiswertanteile bei; wird bei jeder Neuberechnung befragt. */
    void contribute(StatHolderView holder, BaseStatSink sink);
}

/** Registrierung beim Start, vor der ersten Neuberechnung. */
void registerBaseStatContributor(BaseStatContributor contributor);
```

Gedacht für B06 (Levelzuwachs) und B07 (Klassenbasiswerte). B04 kennt deren Inhalte nicht.
`StatHolderView` gibt nur lesenden Zugriff auf Kennung, Charakter-ID und den vorigen Schnappschuss
— ein Lieferant kann die Berechnung also nicht von innen umschreiben.

Eine Ausnahme aus `contribute` wird abgefangen, mit Lieferanten-ID protokolliert und auf den
betroffenen Träger begrenzt; die Neuberechnung läuft mit den übrigen Lieferanten weiter (FR-038).

---

## 7 · Vanilla-Spiegelung (FR-030 bis FR-034)

```java
public interface VanillaAttributeBridge {
    /** Setzt GENERIC_MAX_HEALTH fest auf 20 und die angezeigte Gesundheit anteilig. */
    void mirrorHealth(UUID holderId, double currentHealth, double maxHealth);
    /** Setzt GENERIC_ATTACK_SPEED. */
    void mirrorAttackSpeed(UUID holderId, double value);
    /** Setzt GENERIC_MOVEMENT_SPEED. */
    void mirrorMovementSpeed(UUID holderId, double value);
}

void registerVanillaBridge(VanillaAttributeBridge bridge);
```

**Verhalten**

- Ohne registrierte Brücke ist die Spiegelung wirkungslos. Das ist der Grund, warum die gesamte
  Rechenlogik ohne Server prüfbar bleibt (FR-034).
- Die Engine ruft die Brücke im selben Vorgang wie die Neuberechnung auf, nicht in einem späteren
  Durchlauf (FR-032).
- Die Paper-Umsetzung führt jeden Aufruf über `Scheduler.runSyncOnEntity` im Tick des betroffenen
  Trägers aus (FR-033, Prinzip I). Läuft die Neuberechnung bereits in diesem Tick, entfällt der
  Umweg.
- Anzeigeformel: `angezeigt = max(0.5, currentHealth / maxHealth × 20)` solange
  `currentHealth > 0`; bei `currentHealth == 0` ist die Anzeige `0` (FR-030, FR-031).

---

## 8 · Reine Funktion für B05 (FR-015)

```java
public final class DamageMitigation {
    /** Divisor-Modell aus ADR-008: raw × 100/(100 + defense). */
    public static double afterDefense(double raw, double defense);

    /** Der reine Minderungsanteil in [0,1) — für Anzeige und Tests. */
    public static double reductionFactor(double defense);
}
```

Statisch, zustandslos, ohne Abhängigkeiten. Zusicherungen:

| Eingabe | Ergebnis |
|---|---|
| `defense == 0` | `raw` unverändert, Minderung 0 % |
| `defense == 300` | `raw × 0.25`, Minderung exakt 75 % (SC-006) |
| `defense → ∞` | Minderung nähert sich 100 %, erreicht sie nie |
| `defense < 0` | Verstärkung statt Minderung; der Nenner wird bei `1.0` geklemmt, damit kein Vorzeichenwechsel und keine Division durch null entsteht |
| `raw == 0` | `0` |

---

## Kompatibilitätszusage

Diese Schnittstelle ist der Vertrag, gegen den B05 bis B13 entwickelt werden. Änderungen daran sind
ab hier ADR-pflichtig (Governance-Abschnitt der Constitution). Erweiterungen um neue Attribute
berühren keine Signatur — genau dafür ist `Attribute` ein Parameter und kein Methodenname.
