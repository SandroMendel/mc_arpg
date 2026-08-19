package rpg.core.stats;

import java.util.Objects;
import java.util.UUID;

/**
 * The persisted form of a character's resources (FR-028).
 *
 * <p>Separate from {@link ResourcePool} on purpose: the pool in memory is measured against maxima
 * that move, while this is a plain stored value with a revision, exactly like every other record
 * B02 writes.
 *
 * <p><b>Only raw values are stored.</b> No maxima, no computed totals, no snapshot. All of that is
 * derived and is rebuilt on load from configuration and sources. It is the same rule ADR-004 sets
 * for items, for the same reason: rebalancing must not turn into a data migration.
 *
 * @param characterId the character this belongs to
 * @param currentHealth stored health; not negative
 * @param currentMana stored mana; not negative
 * @param dataVersion version of the record format, for a future migration path
 * @param revision incremented on every write, as in B02
 */
public record CharacterResources(
        UUID characterId, double currentHealth, double currentMana, int dataVersion, long revision) {

    /** Current record format version written by this build. */
    public static final int CURRENT_DATA_VERSION = 1;

    public CharacterResources {
        Objects.requireNonNull(characterId, "characterId");
        if (!Double.isFinite(currentHealth) || currentHealth < 0.0) {
            throw new IllegalArgumentException(
                    "currentHealth must be finite and not negative, but was " + currentHealth);
        }
        if (!Double.isFinite(currentMana) || currentMana < 0.0) {
            throw new IllegalArgumentException(
                    "currentMana must be finite and not negative, but was " + currentMana);
        }
        if (dataVersion < 1) {
            throw new IllegalArgumentException("dataVersion must be at least 1");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
    }

    /** A fresh record for a character that has none yet. */
    public static CharacterResources initial(UUID characterId, ResourcePool pool) {
        return new CharacterResources(
                characterId, pool.currentHealth(), pool.currentMana(), CURRENT_DATA_VERSION, 0L);
    }

    /** The in-memory form of this record. */
    public ResourcePool toPool() {
        return new ResourcePool(currentHealth, currentMana);
    }

    /** Whether this record was written by a build older than the current one. */
    public boolean needsMigration() {
        return dataVersion < CURRENT_DATA_VERSION;
    }
}
