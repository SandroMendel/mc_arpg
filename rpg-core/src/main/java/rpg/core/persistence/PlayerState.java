package rpg.core.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The durable state of one player, as B02 owns it.
 *
 * <p>Only the frame lives here - identity, versioning, timestamps. The domain fields (level, class,
 * zone progress) belong to the blocks that own them and arrive through their own columns and
 * migrations. Keeping that line means B02 does not need touching every time another block adds
 * content.
 *
 * @param playerId the player's unique id
 * @param dataVersion version of the record format, for a future data migration path (FR-021)
 * @param revision incremented on every write; a write based on a stale value is refused (FR-019b)
 * @param lastSeenAt when this player was last connected
 * @param anonymized whether the record has been stripped of its personal reference (FR-017a)
 */
public record PlayerState(
        UUID playerId, int dataVersion, long revision, Instant lastSeenAt, boolean anonymized) {

    /** Current record format version written by this build. */
    public static final int CURRENT_DATA_VERSION = 1;

    public PlayerState {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
    }

    /** A fresh record for a player connecting for the first time. */
    public static PlayerState initial(UUID playerId, Instant now) {
        return new PlayerState(playerId, CURRENT_DATA_VERSION, 0L, now, false);
    }

    /** The same state one revision further along, as written by a flush. */
    public PlayerState nextRevision(Instant now) {
        return new PlayerState(playerId, dataVersion, revision + 1, now, anonymized);
    }
}
