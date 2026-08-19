package rpg.core.persistence;

/**
 * A snapshot of how full the write-behind buffer is.
 *
 * @param pending marks currently waiting to be written
 * @param capacity configured upper bound (FR-009a)
 * @param overCapacity capacity reached - players must be disconnected (FR-009b)
 * @param warning warn threshold reached but capacity not yet hit (FR-009c)
 */
public record BufferStatus(int pending, int capacity, boolean overCapacity, boolean warning) {

    /** How full the buffer is, between 0 and 1. */
    public double fillRatio() {
        return capacity == 0 ? 1.0d : (double) pending / capacity;
    }
}
