package rpg.core.zone;

import java.util.Set;
import java.util.UUID;

/**
 * Which waypoint crystals a character may travel to (FR-051a).
 *
 * <p><b>Per character, never per account</b> (ADR-011). Whoever walked the continent with the warrior
 * starts again with the mage - the same rule that gives them separate levels, separate ability ranks
 * and separate purses.
 *
 * <p><b>Unlocks grow and are never taken back</b> (FR-051b2). Not by death, not by a reconfiguration,
 * not by time. The only thing that ends one is the character themselves (FR-051b1), and that happens
 * in the database through {@code ON DELETE CASCADE} rather than through a method here - there is
 * deliberately no way to revoke one from code.
 */
public interface Waypoints {

    /** Whether this character may travel to that crystal. */
    boolean isUnlocked(UUID characterId, String crystalKey);

    /**
     * Records a discovered crystal.
     *
     * @return {@code true} when this was new, {@code false} when it was already known - so the
     *     caller can tell a discovery worth announcing from a second right-click
     */
    boolean unlock(UUID characterId, String crystalKey);

    /** Everything this character has discovered. For the selection window. */
    Set<String> unlockedBy(UUID characterId);
}
