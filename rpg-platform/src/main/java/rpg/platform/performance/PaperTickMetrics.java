package rpg.platform.performance;

import java.util.Objects;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;

/** Immutable Paper-side representation of one completed server tick. */
public record PaperTickMetrics(int tickNumber, long durationNanos) {

    public PaperTickMetrics {
        if (tickNumber < 0) {
            throw new IllegalArgumentException("tickNumber must not be negative");
        }
        if (durationNanos < 0) {
            throw new IllegalArgumentException("durationNanos must not be negative");
        }
    }

    public static PaperTickMetrics from(ServerTickEndEvent event) {
        Objects.requireNonNull(event, "event");
        return new PaperTickMetrics(event.getTickNumber(), toNanos(event.getTickDuration()));
    }

    private static long toNanos(double durationMillis) {
        if (!Double.isFinite(durationMillis) || durationMillis < 0.0d) {
            throw new IllegalArgumentException("tick duration must be finite and non-negative");
        }
        double durationNanos = durationMillis * 1_000_000.0d;
        if (durationNanos >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, Math.round(durationNanos));
    }
}
