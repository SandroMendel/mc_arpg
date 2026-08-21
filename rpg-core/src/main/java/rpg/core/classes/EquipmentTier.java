package rpg.core.classes;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import rpg.core.stats.Attribute;

/**
 * One step of a ladder: fixed values, an appearance, a level requirement and an opaque cost.
 *
 * <p><b>Fixed values, never rolled.</b> That is the whole difference to a looted item and the reason
 * two characters of the same class on the same tier are value-equal (ADR-017).
 *
 * <p><b>The cost block is not interpreted here.</b> B07 knows nothing about coins, materials or
 * prices - it checks that the block is a map and passes it through. Interpreting it would couple this
 * block to B11, which does not exist yet (Workflow rule 5, FR-021).
 */
public final class EquipmentTier {

    private final int index;
    private final LadderSlot slot;
    private final Map<Attribute, Double> values;
    private final TierAppearance appearance;
    private final int requiredLevel;
    private final Map<String, Object> cost;

    private EquipmentTier(
            int index,
            LadderSlot slot,
            Map<Attribute, Double> values,
            TierAppearance appearance,
            int requiredLevel,
            Map<String, Object> cost) {
        this.index = index;
        this.slot = slot;
        this.values = values;
        this.appearance = appearance;
        this.requiredLevel = requiredLevel;
        this.cost = cost;
    }

    /**
     * @throws IllegalArgumentException if an attribute of the slot is missing or a foreign one is
     *     present - a tier defines exactly the four attributes its slot carries (FR-015)
     */
    public static EquipmentTier of(
            int index,
            LadderSlot slot,
            Map<Attribute, Double> values,
            TierAppearance appearance,
            int requiredLevel,
            Map<String, Object> cost) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(cost, "cost");
        if (index < 1) {
            throw new IllegalArgumentException("tier index is 1-based, but was " + index);
        }
        if (requiredLevel < 1) {
            throw new IllegalArgumentException(
                    "tier " + index + ": required-level must be at least 1, but was " + requiredLevel);
        }
        // Foreign attributes first: it is the more specific mistake, and reporting a missing carried
        // attribute instead would name a symptom rather than the cause.
        for (Attribute present : values.keySet()) {
            if (!slot.carried().contains(present)) {
                throw new IllegalArgumentException(
                        "tier "
                                + index
                                + ": "
                                + present.key()
                                + " does not belong to slot "
                                + slot
                                + " - each attribute has exactly one ladder as its source");
            }
        }
        Map<Attribute, Double> copy = new EnumMap<>(Attribute.class);
        for (Attribute carried : slot.carried()) {
            Double value = values.get(carried);
            if (value == null) {
                throw new IllegalArgumentException(
                        "tier " + index + ": missing value for " + carried.key());
            }
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "tier " + index + ": " + carried.key() + " must be finite, but was " + value);
            }
            copy.put(carried, value);
        }
        return new EquipmentTier(
                index, slot, Map.copyOf(copy), appearance, requiredLevel, Map.copyOf(cost));
    }

    public int index() {
        return index;
    }

    public LadderSlot slot() {
        return slot;
    }

    /** The value this tier contributes for {@code attribute}, or zero if the slot does not carry it. */
    public double valueOf(Attribute attribute) {
        return values.getOrDefault(attribute, 0.0);
    }

    public Map<Attribute, Double> values() {
        return values;
    }

    public TierAppearance appearance() {
        return appearance;
    }

    public int requiredLevel() {
        return requiredLevel;
    }

    /** The cost block, exactly as configured. B07 does not interpret it (FR-021). */
    public Map<String, Object> cost() {
        return cost;
    }

    @Override
    public String toString() {
        return "EquipmentTier[" + slot + " " + index + " " + appearance.material() + "]";
    }
}
