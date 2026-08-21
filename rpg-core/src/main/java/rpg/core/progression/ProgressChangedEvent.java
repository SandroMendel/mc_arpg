package rpg.core.progression;

import java.util.UUID;

/**
 * The experience gained inside one window, summed into a single event (FR-023a).
 *
 * <p>{@code gained} is the sum of every gain in the window, not the last one.
 *
 * <p>The event carries level and both experience values so B13 can draw the bar <b>without asking
 * back</b> and without arithmetic of its own. An event carrying only {@code gained} would have forced
 * a query per message - at a thousand gains per second exactly what the bundling avoids.
 *
 * @param characterId whose progress moved
 * @param playerId the player behind that character
 * @param gained the summed gain of the window
 * @param level level at the end of the window
 * @param xpInLevel experience inside that level
 * @param xpForNextLevel threshold of the next level, 0 at the maximum
 */
public record ProgressChangedEvent(
        UUID characterId,
        UUID playerId,
        long gained,
        int level,
        long xpInLevel,
        long xpForNextLevel) {}
