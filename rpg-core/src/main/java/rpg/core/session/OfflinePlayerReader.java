package rpg.core.session;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * How B12 (leaderboards) and B14 (admin tools) read player data without opening a session.
 *
 * <p>See {@code contracts/offline-access.md}. Without this path a tool would have to either open a
 * session - changing the very state it only meant to read - or reach past the persistence layer and
 * break the encapsulation B02 enforces mechanically. Neither is acceptable, so the third way exists.
 *
 * <p>There is no writing method. A tool that must change something goes through the block that owns
 * the data, not past the session layer.
 */
public interface OfflinePlayerReader {

    /**
     * Reads a player.
     *
     * <p>For a connected player this returns the <strong>live session</strong> state, not the
     * stored one (FR-024). A leaderboard entry missing the last 45 seconds of progress is visibly
     * wrong to the player it belongs to.
     */
    CompletableFuture<Optional<PlayerSnapshot>> read(UUID playerId);

    /** The characters of a player, from the session if connected, otherwise from storage. */
    CompletableFuture<List<PlayerCharacter>> charactersOf(UUID playerId);
}
