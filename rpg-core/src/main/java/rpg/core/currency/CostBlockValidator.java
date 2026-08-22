package rpg.core.currency;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import rpg.core.classes.ClassConfig;
import rpg.core.classes.EquipmentLadder;
import rpg.core.classes.LadderSlot;
import rpg.core.session.CharacterClass;

/**
 * Checks every configured price at startup (FR-050, Constitution V).
 *
 * <p><b>Here, not in B07.</b> B07 validates that the {@code cost} block is a map and nothing else -
 * it knows no currency and its own invariant test forbids the vocabulary. This block knows which
 * keys exist, so it is the one that can say a key is wrong.
 *
 * <p><b>Every ladder, every tier, at startup.</b> A price with a key nobody charges would never be
 * paid, and the tier would be quietly free - a balancing decision that silently did not happen. That
 * is worse than a server that refuses to start, so the start refuses.
 *
 * <p>All faults are collected before throwing. An operator editing six ladders should be told about
 * every mistake at once, not sent through six restarts.
 */
public final class CostBlockValidator {

    private CostBlockValidator() {}

    /**
     * Validates the cost block of every tier of every ladder of every class.
     *
     * @throws IllegalStateException listing every fault found
     */
    public static void validateClasses(ClassConfig config) {
        Objects.requireNonNull(config, "config");
        List<String> faults = new ArrayList<>();

        for (CharacterClass id : CharacterClass.values()) {
            for (LadderSlot slot : LadderSlot.values()) {
                EquipmentLadder ladder = config.definition(id).ladder(slot);
                for (int tier = 1; tier <= ladder.length(); tier++) {
                    Map<String, Object> block = ladder.tier(tier).cost();
                    try {
                        CostSpec.parse(block, where(id, slot, tier));
                    } catch (IllegalArgumentException fault) {
                        faults.add(fault.getMessage());
                    }
                }
            }
        }

        if (!faults.isEmpty()) {
            throw new IllegalStateException(
                    "classes.yml has "
                            + faults.size()
                            + " price(s) nobody could charge:\n  - "
                            + String.join("\n  - ", faults));
        }
    }

    private static String where(CharacterClass id, LadderSlot slot, int tier) {
        return "classes."
                + id.name().toLowerCase(Locale.ROOT)
                + "."
                + slot.name().toLowerCase(Locale.ROOT)
                + ".tier "
                + tier;
    }
}
