package rpg.core.progression;

import java.util.UUID;

/**
 * Which party members are close enough to the dead creature to share its experience (FR-044).
 *
 * <p>Behind an interface because {@code rpg-core} cannot measure a distance - it has no Bukkit
 * dependency (Principle III). Without a registered implementation only the contributor itself counts
 * as in range, which makes the party split fall back to the no-party behaviour instead of either
 * handing experience to everyone or swallowing it (see research.md, decision 6).
 */
public interface ProximityCheck {

    /**
     * Filters {@code candidates} down to those within {@code range} of {@code origin} and in the
     * same world (FR-041a, FR-045).
     *
     * <p>Writes the hits into {@code out} and returns how many there are - no new array per call,
     * because this runs on every kill. {@code out} must have room for at least
     * {@code party.max-size} entries; the caller keeps one array and reuses it.
     *
     * @param origin where the creature died, read while that was still valid
     * @param candidates party members to test
     * @param candidateCount how many entries of {@code candidates} to look at
     * @param range maximum distance in blocks
     * @param out receives the members in range
     * @return number of entries written to {@code out}
     */
    int inRange(
            WorldPoint origin, UUID[] candidates, int candidateCount, double range, UUID[] out);
}
