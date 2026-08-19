package rpg.core.stats;

import java.util.Optional;
import java.util.UUID;

/**
 * What a {@link BaseStatContributor} gets to see (FR-039).
 *
 * <p>Read-only on purpose. A contributor supplies numbers; it must not be able to reach into the
 * calculation that is asking it. Without that line, B06 or B07 could quietly become part of the
 * engine rather than a supplier to it.
 *
 * <p><b>Read-only but live.</b> This is a view of the holder itself, not a frozen copy - freezing
 * one would mean an allocation per holder per recalculation, in the path that promises not to
 * allocate. It is valid for the duration of the {@link BaseStatContributor#contribute} call.
 * Keeping the reference afterwards and reading {@link #previousSnapshot()} later returns whatever
 * is current then, which is almost certainly not what the caller meant. Read what you need while
 * you are called.
 */
public interface StatHolderView {

    /** Player UUID for a character, entity UUID otherwise. */
    UUID holderId();

    /** The character this holder belongs to, or empty for a holder without a player (a mob). */
    Optional<UUID> characterId();

    /** The previous result, or empty before the first calculation. */
    Optional<StatSnapshot> previousSnapshot();
}
