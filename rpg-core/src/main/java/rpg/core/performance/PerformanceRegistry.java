package rpg.core.performance;

import java.time.Duration;

/** Public bukkit-free registration and measurement boundary for B15. */
public interface PerformanceRegistry {

    PerformanceBudget register(SubsystemId id, Duration budget, String owner);

    MeasurementScope begin(SubsystemId id);

    void record(SubsystemId id, long durationNanos);

    void recordOverallTick(long durationNanos);

    PerformanceSnapshot snapshot();

    boolean enabled();
}
