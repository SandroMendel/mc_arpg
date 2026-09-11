package rpg.core.item;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import rpg.core.classes.LadderSlot;

/**
 * Der Zustand der beiden Ausrüstungsleitern eines Charakters (data-model.md §3).
 *
 * <p><b>Zwei Zahlen, und mehr wird es nicht.</b> Nicht je Rüstungsteil, sondern je Leiter (FR-039):
 * B07 vergibt Helm, Brust, Hose und Schuhe als <em>eine</em> Stufe, und vier getrennte Zustände
 * wären vier Reparaturrechnungen für eine Entscheidung, die der Spieler einmal getroffen hat.
 *
 * <p><b>Unveränderlich.</b> Jede Änderung gibt einen neuen Wert zurück, wie {@code ClassProgress} in
 * B07. Der Verschleiß läuft im Kampfpfad, und ein gemeinsam veränderter Zustand dort wäre genau die
 * Art Fehler, die erst unter Last auftritt (Prinzip I).
 *
 * @param characterId wessen Ausrüstung — der Charakter, nicht der Spieler (ADR-011)
 * @param conditions je Leiter ein Wert in {@code [0, 100]}
 * @param dataVersion Migrationspfad (Prinzip IV)
 */
public record GearCondition(UUID characterId, Map<LadderSlot, Double> conditions, int dataVersion) {

    /** Die Fassung, in der dieser Block schreibt. */
    public static final int CURRENT_DATA_VERSION = 1;

    public GearCondition {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(conditions, "conditions");

        EnumMap<LadderSlot, Double> copy = new EnumMap<>(LadderSlot.class);
        for (LadderSlot slot : LadderSlot.values()) {
            double value = conditions.getOrDefault(slot, WearCurve.FULL);
            if (!Double.isFinite(value) || value < 0.0 || value > WearCurve.FULL) {
                throw new IllegalArgumentException(
                        slot + " condition must be in [0, 100], but was " + value);
            }
            copy.put(slot, value);
        }
        conditions = java.util.Collections.unmodifiableMap(copy);
    }

    /** Ein frischer Charakter: beide Leitern voll. */
    public static GearCondition full(UUID characterId) {
        return new GearCondition(characterId, Map.of(), CURRENT_DATA_VERSION);
    }

    public double of(LadderSlot slot) {
        return conditions.get(slot);
    }

    /** Derselbe Charakter mit einem neuen Wert in einer Leiter. */
    public GearCondition with(LadderSlot slot, double condition) {
        EnumMap<LadderSlot, Double> next = new EnumMap<>(conditions);
        next.put(slot, Math.min(Math.max(condition, 0.0), WearCurve.FULL));
        return new GearCondition(characterId, next, dataVersion);
    }

    /** Ob überhaupt etwas verschlissen ist — die Frage, die eine Reparatur ablehnt (FR-054). */
    public boolean isWorn(LadderSlot slot) {
        return of(slot) < WearCurve.FULL;
    }
}
