package rpg.core.classes;

import java.util.Objects;
import java.util.UUID;

/**
 * One ladder of one character moved up a step.
 *
 * <p>Published on the event bus from B01, not as a Bukkit event - {@code rpg-core} has no Bukkit
 * dependency (Constitution III.1).
 *
 * <p>This event is also how the recalculation is triggered: {@code TierAdvance} does not know holder
 * ids, so the module subscribes and asks B04 to recalculate. That keeps the rule free of the mapping
 * between characters and holders, and it is why exactly one recalculation follows one advance
 * (SC-009).
 */
public record TierAdvancedEvent(
        UUID characterId, LadderSlot slot, int fromTier, int toTier) {

    public TierAdvancedEvent {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(slot, "slot");
        if (toTier != fromTier + 1) {
            throw new IllegalArgumentException(
                    "an advance moves exactly one step, but went from "
                            + fromTier
                            + " to "
                            + toTier
                            + " - a jump could not be told from a bug");
        }
    }
}
