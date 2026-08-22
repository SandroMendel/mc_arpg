package rpg.core.currency;

import java.util.Optional;
import java.util.UUID;

/**
 * Which character a stat holder is playing right now.
 *
 * <p><b>A one-question interface rather than a dependency on B04.</b> B05 keys its damage shares by
 * stat holder - which is a player id - while this block owes everything to a <em>character</em>
 * (ADR-011). Somebody has to bridge the two, and {@code StatEngine} can answer it.
 *
 * <p>Taking the whole engine would have given B08b a dependency on B04 that ADR-027 never listed:
 * the block depends on B02, B03 and B06, and that short list is what lets it sit in layer 1 and
 * close B07 and B08 rather than queueing behind them. A dependency acquired by accident is still a
 * dependency.
 *
 * <p>The plugin binds this to {@code StatEngine::characterIdOf} at wiring time.
 */
@FunctionalInterface
public interface CharacterLookup {

    /** The character this holder is playing, or empty when there is none. */
    Optional<UUID> characterOf(UUID holderId);
}
