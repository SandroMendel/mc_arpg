package rpg.core.stats;

import java.util.UUID;

/**
 * Published whenever a resource value actually moved (FR-029).
 *
 * <p>A change of zero - spending mana that is already empty - publishes nothing. Subscribers should
 * not have to filter out events that carry no information.
 *
 * <p>{@code current == 0} with {@code kind == HEALTH} means the holder has no health left. B04
 * reports that and does nothing about it; dying is B05's decision (FR-042).
 *
 * @param holderId the holder
 * @param characterId its character, or {@code null} for a holder without a player
 * @param kind which resource
 * @param previous the value before
 * @param current the value after clamping
 * @param max the maximum from the snapshot in force at the time
 * @param cause why it moved
 */
public record ResourceChangedEvent(
        UUID holderId,
        UUID characterId,
        ResourceKind kind,
        double previous,
        double current,
        double max,
        ChangeCause cause) {

    /** Whether this holder just ran out of health. What follows is B05's call. */
    public boolean isDepleted() {
        return kind == ResourceKind.HEALTH && current == 0.0;
    }
}
