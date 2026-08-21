package rpg.core.classes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import rpg.core.stats.Attribute;

/**
 * Builds ladders for tests without going through the configuration.
 *
 * <p>The point is that a hand-built ladder is subject to exactly the same invariants as a loaded one -
 * if this fixture can build something the schema would reject, one of the two is wrong.
 */
final class LadderFixture {

    private LadderFixture() {}

    /**
     * A ladder of {@code length} tiers whose carried attributes rise from {@code first} to
     * {@code last} along the normalising curve, with a distinct material per tier.
     */
    static EquipmentLadder rising(LadderSlot slot, int length, double first, double last) {
        return EquipmentLadder.of(slot, risingTiers(slot, length, first, last));
    }

    static List<EquipmentTier> risingTiers(LadderSlot slot, int length, double first, double last) {
        List<EquipmentTier> tiers = new ArrayList<>();
        for (int i = 1; i <= length; i++) {
            double share = Math.pow((i - 1.0) / (length - 1.0), 1.3);
            double value = first + (last - first) * share;
            Map<Attribute, Double> values = new EnumMap<>(Attribute.class);
            for (Attribute carried : slot.carried()) {
                values.put(carried, value);
            }
            int requiredLevel = i == 1 ? 1 : (int) Math.round(1 + 54.0 * (i - 1.0) / (length - 1.0));
            tiers.add(
                    EquipmentTier.of(
                            i,
                            slot,
                            values,
                            TierAppearance.ofMaterial("MATERIAL_" + i),
                            requiredLevel,
                            Map.of()));
        }
        return tiers;
    }

    /** One tier whose every carried attribute holds {@code value}. */
    static EquipmentTier tier(
            int index, LadderSlot slot, double value, TierAppearance appearance, int requiredLevel) {
        Map<Attribute, Double> values = new EnumMap<>(Attribute.class);
        for (Attribute carried : slot.carried()) {
            values.put(carried, value);
        }
        return EquipmentTier.of(index, slot, values, appearance, requiredLevel, Map.of());
    }
}
