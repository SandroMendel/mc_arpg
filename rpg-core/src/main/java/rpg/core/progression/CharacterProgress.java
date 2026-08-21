package rpg.core.progression;

import java.util.Objects;
import java.util.UUID;

/**
 * The stored form of a character's progress (FR-053, FR-057).
 *
 * <p>Split from {@link ProgressState} the same way B04 splits {@code CharacterResources} from
 * {@code ResourcePool}: the record carries identity, data version and revision, which only the
 * database cares about, while the in-memory state carries the two numbers the rules work with. One
 * type doing both would drag a revision counter into every level-up calculation.
 *
 * @param characterId owner; progress belongs to the character, not the account (ADR-011)
 * @param level level reached
 * @param xpInLevel experience inside that level - never a running total (FR-053a)
 * @param dataVersion format of this record, so an old row can be migrated on load
 * @param revision incremented on every write, as in the other tables
 */
public record CharacterProgress(
        UUID characterId, int level, long xpInLevel, int dataVersion, long revision) {

    public static final int CURRENT_DATA_VERSION = 1;

    public CharacterProgress {
        Objects.requireNonNull(characterId, "characterId");
        if (level < 1) {
            throw new IllegalArgumentException("level must be at least 1, but was " + level);
        }
        if (xpInLevel < 0L) {
            throw new IllegalArgumentException(
                    "xpInLevel must not be negative, but was " + xpInLevel);
        }
        if (dataVersion < 1) {
            throw new IllegalArgumentException("dataVersion must be at least 1");
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must not be negative");
        }
    }

    /** A character that has never been stored (FR-058). */
    public static CharacterProgress initial(UUID characterId) {
        return new CharacterProgress(
                characterId,
                ProgressState.INITIAL.level(),
                ProgressState.INITIAL.xpInLevel(),
                CURRENT_DATA_VERSION,
                0L);
    }

    public ProgressState toState() {
        return new ProgressState(level, xpInLevel);
    }

    public boolean needsMigration() {
        return dataVersion < CURRENT_DATA_VERSION;
    }

    /** A row written by a newer version is refused rather than read wrongly (FR-057). */
    public boolean isFromFutureVersion() {
        return dataVersion > CURRENT_DATA_VERSION;
    }
}
