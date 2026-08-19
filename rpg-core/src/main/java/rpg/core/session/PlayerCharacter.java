package rpg.core.session;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One character of an account, bound to exactly one class.
 *
 * <p>Carries only what B03 owns: identity, class, versioning, timestamps. The actual progress
 * (level, attributes, abilities) belongs to the blocks that own it (B04, B06, B07) and arrives
 * through their own columns - the same boundary B02 drew for the account record.
 *
 * @param characterId identity of this character
 * @param playerId the account it belongs to
 * @param characterClass its class; at most one per class per account (FR-017)
 * @param dataVersion version of the record format, for the migration path (FR-025 to FR-027)
 * @param revision incremented on every write, as in B02
 * @param createdAt when the character was created
 * @param lastPlayedAt when it was last played
 */
public record PlayerCharacter(
        UUID characterId,
        UUID playerId,
        CharacterClass characterClass,
        int dataVersion,
        long revision,
        Instant createdAt,
        Instant lastPlayedAt) {

    /** Current record format version written by this build. */
    public static final int CURRENT_DATA_VERSION = 1;

    public PlayerCharacter {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(characterClass, "characterClass");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lastPlayedAt, "lastPlayedAt");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        if (dataVersion < 1) {
            throw new IllegalArgumentException("dataVersion must be at least 1");
        }
    }

    /** A freshly created character. */
    public static PlayerCharacter create(UUID playerId, CharacterClass characterClass, Instant now) {
        return new PlayerCharacter(
                UUID.randomUUID(), playerId, characterClass, CURRENT_DATA_VERSION, 0L, now, now);
    }

    /** Whether this record was written by a build older than the current one. */
    public boolean needsMigration() {
        return dataVersion < CURRENT_DATA_VERSION;
    }

    /** Whether this record came from a build newer than this one - it cannot be interpreted. */
    public boolean isFromFutureVersion() {
        return dataVersion > CURRENT_DATA_VERSION;
    }
}
