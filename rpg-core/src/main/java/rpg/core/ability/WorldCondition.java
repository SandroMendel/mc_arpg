package rpg.core.ability;

import java.util.UUID;

/**
 * Whether a holder is in the open world rather than inside an instance (FR-052b).
 *
 * <p>Rogue's Second Life is written as working only out in the world - a dungeon or a boss room takes
 * it away. That distinction belongs to B09, which owns zones and world topology (ADR-006).
 *
 * <p><b>The default says yes to everything, and that direction was deliberate.</b> Before B09 it also
 * saved a rogue inside an instance - wrong, but harmless and visible. The opposite default would have
 * disabled the ability everywhere, and "the unique class ability does nothing" is the kind of silence
 * nobody traces back to a missing block.
 *
 * <p>B09 has since installed {@code ZoneWorldCondition}. The answer is still yes everywhere, but now
 * because the release ships no instances rather than because nobody has looked.
 */
@FunctionalInterface
public interface WorldCondition {

    /**
     * Whether this holder is somewhere an open-world-only ability may take hold.
     *
     * <p><b>The holder, not the character</b> - renamed by B09 when it wrote the first real
     * implementation. {@code PassiveDispatcher} has always passed the id it fires on, which is the
     * player; only the parameter said otherwise. An implementation that believed the old name would
     * look a character id up in a holder-keyed map, find nothing, and quietly switch every
     * open-world-only ability off - the exact silence {@link #everywhere()} was chosen to avoid.
     */
    boolean isOpenWorld(UUID holderId);

    /**
     * Everywhere counts as open world.
     *
     * <p>The default before B09 existed. B09 ships {@code ZoneWorldCondition}, which gives the same
     * answer as a decision rather than as a placeholder - the release has no instances.
     */
    static WorldCondition everywhere() {
        return holderId -> true;
    }
}
