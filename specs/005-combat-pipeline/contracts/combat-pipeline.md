# Vertrag: `CombatPipeline` — die öffentliche Schnittstelle von B05

**Feature**: `specs/005-combat-pipeline` | **Datum**: 2026-08-20

Die einzige Schnittstelle, über die B06 bis B13 Schaden auslösen oder darauf reagieren. Zugriff auf
Interna (`DamageContext`, `AttributionWindow`, die Listener) ist unzulässig (Prinzip III).

Registriert beim Start durch `CombatModule` unter `CombatPipeline.class`, abrufbar über
`ModuleContext.registry()` — dasselbe Muster wie `SessionRegistry` und `StatEngine`.

---

## 1 · Schaden auslösen

```java
/** Ein Nahkampfschlag. Faktor 1,0, Typ PHYSICAL. */
DamageResult meleeAttack(UUID attackerId, UUID targetId);

/**
 * Schaden aus einer Fähigkeit (B08).
 *
 * @param factor Anteil des Basisattributs: 1,8 bedeutet 180 % (FR-002a)
 */
DamageResult abilityDamage(UUID attackerId, UUID targetId, DamageType type, double factor);

/** Umgebungsschaden. Fester Betrag, ohne Verteidigung (FR-012a, FR-012b). */
DamageResult environmentDamage(UUID targetId, EnvironmentSource source, double amount);

/** Sofortiger Tod ohne Formel und ohne Attribution - fuer /kill und den Void. */
void kill(UUID targetId, DeathCause cause);

record DamageResult(boolean applied, double finalDamage, boolean lethal, RejectReason reason) {}

enum RejectReason { NONE, NOT_PERMITTED, ATTACK_TOO_SOON, SESSION_NOT_READY, NO_HOLDER, ALREADY_DEAD }
```

**Verhalten**

- Der Wertestand des Angreifers wird **einmal** zu Beginn gezogen und bis zum Ende gehalten
  (FR-005). Wer denselben Vorgang mehrfach auslöst, bekommt mehrere Vorgänge — nicht einen längeren.
- Ein abgewiesener Vorgang liefert `applied == false` mit einem Grund und erzeugt weder Schaden noch
  Animation noch Beitrag (FR-009, FR-021, FR-043).
- `meleeAttack` prüft das Angriffszeitfenster, `abilityDamage` **nicht** — Fähigkeiten haben ihre
  eigenen Abklingzeiten in B08, und beides zu prüfen würde sie doppelt begrenzen.
- Alle Methoden laufen synchron im Tick des Ziels zu Ende. Kein Aufruf blockiert.

---

## 2 · An der Pipeline eingreifen (FR-008)

```java
void registerInterceptor(DamageInterceptor interceptor);

public interface DamageInterceptor {
    String id();
    PipelineStage stage();
    void intercept(DamageView damage);
}
```

**Verhalten**

- Eingriffspunkte werden beim Start registriert, nicht im laufenden Kampf.
- Mehrere an derselben Stufe laufen in Registrierungsreihenfolge.
- Eine Ausnahme wird abgefangen, mit `id()` protokolliert und auf diesen Vorgang begrenzt (FR-010).
- **`DamageView` ist nur während des Aufrufs gültig.** Der zugrunde liegende Vorgang wird
  wiederverwendet; wer die Sicht festhält, liest später fremde Daten. Was gebraucht wird, wird
  im Aufruf gelesen.

---

## 3 · Werte lesen

```java
/** Ob dieser Traeger gerade im Kampf ist (FR-030c). */
boolean isInCombat(UUID holderId);

/** Wie lange er es noch ist, oder leer wenn nicht im Kampf. */
Optional<Duration> remainingCombatTime(UUID holderId);

/** Ob ein Angriff jetzt zaehlen wuerde, ohne ihn auszufuehren. */
boolean canAttackNow(UUID attackerId);

/** Die aktuelle Schadensaufteilung eines Ziels - fuer Anzeige und Fehlersuche. */
Optional<DamageShare> currentShares(UUID targetId);
```

Alle vier sind reine Abfragen: sie rechnen nichts, planen nichts und lösen keine Neuberechnung aus.

---

## 4 · Mob-Ausstattung (FR-019c)

```java
/** Ersetzt die Werteversorgung. B10 ruft das beim Start auf. */
void setMobStatProvider(MobStatProvider provider);

public interface MobStatProvider {
    Optional<ModifierSet> statsFor(String mobTypeKey);
}
```

B05 bringt eine Umsetzung aus `combat.yml` mit. Sie ist eine **Überbrückung**: B05 liefert Zahlen,
damit gekämpft und lastgetestet werden kann. Was ein Mob *ist*, bleibt B10.

Ein leeres Ergebnis bedeutet „diese Art bekommt keinen Stat-Träger" — so bleiben friedliche Wesen
außerhalb des Kampfsystems (FR-019e).

---

## 5 · Rückmeldung (FR-037)

```java
public interface DamageFeedback {
    void playHurtAnimation(UUID targetId);
    void applyKnockback(UUID targetId, UUID sourceId, double strength);
}

void registerFeedback(DamageFeedback feedback);
```

In `rpg-core` als Schnittstelle, in `rpg-platform` umgesetzt. Ohne Registrierung wirkungslos —
genau das hält die gesamte Pipeline serverfrei prüfbar.

Nötig, weil ein auf null gesetztes Vanilla-Ereignis keine Trefferanimation mehr zeigt (ADR-003) und
keinen Rückstoß mehr erzeugt.

---

## 6 · Reine Funktionen

```java
public final class DamageFormula {
    /** raw = base * factor. */
    public static double rawDamage(double baseAttribute, double factor);

    /** Verteidigung anwenden - delegiert an B04s DamageMitigation. */
    public static double afterDefence(double raw, double defence);

    /** Fallschaden aus der Fallhoehe (FR-012c). */
    public static double fallDamage(double fallenBlocks, FallDamageConfig config);
}
```

Statisch, zustandslos, ohne Zufall. Das ist der Teil, den SC-012 mit dokumentierten
Beispielrechnungen verlangt.

---

## Kompatibilitätszusage

Diese Schnittstelle ist der Vertrag, gegen den B06 bis B13 entwickelt werden. Änderungen sind ab
hier ADR-pflichtig.

Zwei Zusagen, auf die sich spätere Blöcke ausdrücklich verlassen dürfen:

1. **Der Schadensfaktor ist die einzige Stellschraube einer Fähigkeit.** B08 gibt eine Zahl an und
   bekommt automatisches Skalieren mit Ausrüstung und Level.
2. **Der Kampfzustand ist hier und nirgends sonst.** B08, B12 und B13 fragen ihn ab, statt eigene
   Zähler zu führen.
