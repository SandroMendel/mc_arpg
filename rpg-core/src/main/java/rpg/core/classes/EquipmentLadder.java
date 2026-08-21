package rpg.core.classes;

import java.util.List;
import java.util.Objects;

import rpg.core.stats.Attribute;
import rpg.core.stats.BaseStatSink;

/**
 * The tiers of one slot, with a length that is <b>configuration, not code</b> (FR-013).
 *
 * <p>Warrior 5/6, rogue 6/6, mage 7/7 - armour and weapon of one class may differ in length. A fixed
 * number in the schema could not express that; it would have had to trim the material lists instead of
 * representing them. Every ladder reaches the same end value regardless of how many steps it takes,
 * which is what {@code SC-014} demands.
 *
 * <p>The invariants below are checked here rather than in the schema, so that a hand-built ladder in
 * a test cannot be less consistent than a loaded one.
 */
public final class EquipmentLadder {

    private final LadderSlot slot;
    private final List<EquipmentTier> tiers;

    private EquipmentLadder(LadderSlot slot, List<EquipmentTier> tiers) {
        this.slot = slot;
        this.tiers = tiers;
    }

    /**
     * @throws IllegalArgumentException if the ladder has fewer than two tiers, if a tier belongs to a
     *     different slot, if the indices are not 1..n, if any carried attribute is not strictly
     *     increasing, if the level requirements are not strictly increasing, or if two consecutive
     *     tiers look identical
     */
    public static EquipmentLadder of(LadderSlot slot, List<EquipmentTier> tiers) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(tiers, "tiers");
        // One tier is not a ladder - there would be nothing to advance to.
        if (tiers.size() < 2) {
            throw new IllegalArgumentException(
                    slot.configKey() + " needs at least 2 tiers, but had " + tiers.size());
        }
        for (int i = 0; i < tiers.size(); i++) {
            EquipmentTier tier = tiers.get(i);
            if (tier.slot() != slot) {
                throw new IllegalArgumentException(
                        slot.configKey() + " tier " + (i + 1) + " belongs to slot " + tier.slot());
            }
            if (tier.index() != i + 1) {
                throw new IllegalArgumentException(
                        slot.configKey()
                                + ": tier at position "
                                + (i + 1)
                                + " carries index "
                                + tier.index());
            }
            if (i == 0) {
                continue;
            }
            EquipmentTier previous = tiers.get(i - 1);
            // Strictly increasing in every carried attribute (FR-017). Equal is not "no change" here,
            // it is a tier a player can pay for and gain nothing from.
            for (Attribute carried : slot.carried()) {
                if (tier.valueOf(carried) <= previous.valueOf(carried)) {
                    throw new IllegalArgumentException(
                            slot.configKey()
                                    + " tier "
                                    + tier.index()
                                    + ": "
                                    + carried.key()
                                    + " must exceed tier "
                                    + previous.index()
                                    + " ("
                                    + tier.valueOf(carried)
                                    + " is not above "
                                    + previous.valueOf(carried)
                                    + ")");
                }
            }
            if (tier.requiredLevel() <= previous.requiredLevel()) {
                throw new IllegalArgumentException(
                        slot.configKey()
                                + " tier "
                                + tier.index()
                                + ": required-level "
                                + tier.requiredLevel()
                                + " must exceed tier "
                                + previous.index()
                                + " ("
                                + previous.requiredLevel()
                                + ")");
            }
            // An invisible step reduces the progression to a number (FR-016).
            if (tier.appearance().looksLike(previous.appearance())) {
                throw new IllegalArgumentException(
                        slot.configKey()
                                + " tier "
                                + tier.index()
                                + " is indistinguishable from tier "
                                + previous.index()
                                + " - same material, colour and trim ("
                                + tier.appearance().material()
                                + "). Set a colour or a trim");
            }
        }
        if (tiers.get(0).requiredLevel() != 1) {
            throw new IllegalArgumentException(
                    slot.configKey()
                            + " tier 1 must require level 1, but required "
                            + tiers.get(0).requiredLevel()
                            + " - a fresh character has to be able to wear it");
        }
        return new EquipmentLadder(slot, List.copyOf(tiers));
    }

    public LadderSlot slot() {
        return slot;
    }

    /** How many tiers this ladder has. Configuration, not a constant (FR-013). */
    public int length() {
        return tiers.size();
    }

    /**
     * @param index 1-based
     * @throws IllegalArgumentException if the index is outside 1..length()
     */
    public EquipmentTier tier(int index) {
        if (index < 1 || index > tiers.size()) {
            throw new IllegalArgumentException(
                    slot.configKey() + " has tiers 1.." + tiers.size() + ", but " + index + " was asked for");
        }
        return tiers.get(index - 1);
    }

    public EquipmentTier top() {
        return tiers.get(tiers.size() - 1);
    }

    public List<EquipmentTier> tiers() {
        return tiers;
    }

    public boolean isTop(int index) {
        return index == tiers.size();
    }

    /** Adds the values of the reached tier. Part of the single class contribution (FR-009). */
    public void contributeTo(int reachedTier, BaseStatSink sink) {
        EquipmentTier tier = tier(reachedTier);
        for (Attribute carried : slot.carried()) {
            double value = tier.valueOf(carried);
            if (value != 0.0) {
                sink.addBase(carried, value);
            }
        }
    }
}
