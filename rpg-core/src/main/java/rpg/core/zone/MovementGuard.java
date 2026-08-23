package rpg.core.zone;

import java.util.UUID;

/**
 * Decides whether a movement is worth evaluating at all (FR-019, FR-020).
 *
 * <p><b>This is the cheap half of the movement path.</b> {@code PlayerMoveEvent} is one of the
 * busiest events a server has - {@code DoubleJumpListener} says the same thing for the same reason -
 * so what runs on every single step has to be integer arithmetic on block coordinates: no
 * {@code Chunk} object, no allocation, no lookup. Only when this says yes does the index get touched.
 *
 * <p><b>The rule lives here and not in the listener</b> so it can be tested without a running server
 * (SC-014). The listener's job is to hand over four integers.
 *
 * <p><b>Why the boundary chunk matters.</b> FR-020 says movement inside one chunk must not be
 * re-evaluated; FR-016 says crossing the safe-core boundary must fire an event. A core boundary runs
 * through the middle of chunks, so the chunk rule alone would miss it until the player left the chunk
 * entirely. The index marks every chunk that more than one area touches, and inside those the cheap
 * rule steps aside (research.md R4).
 */
public final class MovementGuard {

    private MovementGuard() {}

    /**
     * Whether the tracker should look at this movement.
     *
     * @param zones the current query object
     * @param worldId the world moved in
     * @param fromBlockX block x before the step
     * @param fromBlockZ block z before the step
     * @param toBlockX block x after the step
     * @param toBlockZ block z after the step
     */
    public static boolean needsEvaluation(
            Zones zones,
            UUID worldId,
            int fromBlockX,
            int fromBlockZ,
            int toBlockX,
            int toBlockZ) {
        boolean sameChunk =
                (fromBlockX >> Cuboid.CHUNK_SHIFT) == (toBlockX >> Cuboid.CHUNK_SHIFT)
                        && (fromBlockZ >> Cuboid.CHUNK_SHIFT) == (toBlockZ >> Cuboid.CHUNK_SHIFT);
        if (!sameChunk) {
            return true;
        }
        // Same chunk: only worth a look if a boundary runs through it. This is the one lookup the
        // guard permits itself, and it is a table access - see ChunkTable for why that matters.
        return zones.isBoundaryChunk(worldId, toBlockX, toBlockZ);
    }
}
