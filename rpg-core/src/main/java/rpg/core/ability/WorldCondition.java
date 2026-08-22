package rpg.core.ability;

import java.util.UUID;

/**
 * Whether a character is in the open world rather than inside an instance (FR-052b).
 *
 * <p>Rogue's Second Life is written as working only out in the world - a dungeon or a boss room takes
 * it away. That distinction belongs to B09, which owns zones and world topology (ADR-006), and does
 * not exist yet.
 *
 * <p><b>The default says yes to everything, and that direction is deliberate.</b> Until B09 arrives,
 * Second Life also saves a rogue inside an instance - wrong, but harmless and visible. The opposite
 * default would disable the ability everywhere, and "the unique class ability does nothing" is the
 * kind of silence nobody traces back to a missing block.
 */
@FunctionalInterface
public interface WorldCondition {

    /** Whether this character is somewhere an open-world-only ability may take hold. */
    boolean isOpenWorld(UUID characterId);

    /** Everywhere counts as open world. The default until B09 installs the real one. */
    static WorldCondition everywhere() {
        return characterId -> true;
    }
}
