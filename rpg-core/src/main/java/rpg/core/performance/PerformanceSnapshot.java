package rpg.core.performance;

import java.util.Map;
import java.util.Objects;

/** Immutable hand-off from tick measurement to asynchronous consumers. */
public record PerformanceSnapshot(
        boolean enabled,
        long capturedAtNanos,
        PerformanceWindow overall,
        Map<SubsystemId, SubsystemSnapshot> subsystems) {

    public PerformanceSnapshot {
        Objects.requireNonNull(overall, "overall");
        Objects.requireNonNull(subsystems, "subsystems");
        subsystems = Map.copyOf(subsystems);
    }

    public record SubsystemSnapshot(
            SubsystemId id, String owner, long budgetNanos, PerformanceWindow window) {

        public SubsystemSnapshot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(window, "window");
        }
    }
}
