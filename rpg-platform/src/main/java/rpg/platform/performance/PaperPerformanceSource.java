package rpg.platform.performance;

import java.util.Objects;

import rpg.core.performance.PerformanceRegistry;

/** Pairs Paper's tick events before forwarding one overall tick sample to Core. */
public final class PaperPerformanceSource {

    private final PerformanceRegistry registry;
    private int openTickNumber = -1;

    public PaperPerformanceSource(PerformanceRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public void onTickStart(int tickNumber) {
        if (tickNumber < 0) {
            throw new IllegalArgumentException("tickNumber must not be negative");
        }
        openTickNumber = tickNumber;
    }

    public void onTickEnd(PaperTickMetrics metrics) {
        Objects.requireNonNull(metrics, "metrics");
        onTickEnd(metrics.tickNumber(), metrics.durationNanos());
    }

    public void onTickEnd(int tickNumber, long durationNanos) {
        if (openTickNumber != tickNumber) {
            return;
        }
        openTickNumber = -1;
        registry.recordOverallTick(durationNanos);
    }
}
