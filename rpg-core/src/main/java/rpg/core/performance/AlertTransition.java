package rpg.core.performance;

/** One deduplicated transition in an alert episode. */
public record AlertTransition(
        AlertState state,
        long atNanos,
        long episode,
        long observedNanos,
        long limitNanos,
        long violationSinceNanos) {

    /** Compatibility constructor for callers that only need the transition identity. */
    public AlertTransition(AlertState state, long atNanos, long episode) {
        this(state, atNanos, episode, -1L, -1L, -1L);
    }
}
