package rpg.core.performance;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded, thread-safe registry whose snapshots never expose mutable sample buffers. */
public final class DefaultPerformanceRegistry implements PerformanceRegistry {

    private static final MeasurementScope NOOP_SCOPE = () -> {};

    private final MonotonicClock clock;
    private final int capacity;
    private final boolean enabled;
    private final Map<SubsystemId, SourceState> sources = new ConcurrentHashMap<>();
    private final SourceState overall;

    public DefaultPerformanceRegistry(MonotonicClock clock, int capacity, boolean enabled) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.enabled = enabled;
        this.overall = new SourceState(capacity);
    }

    @Override
    public PerformanceBudget register(SubsystemId id, Duration budget, String owner) {
        PerformanceBudget definition = PerformanceBudget.of(id, budget, owner);
        SourceState previous = sources.putIfAbsent(id, new SourceState(capacity, definition));
        if (previous != null) {
            throw new DuplicateSubsystemException(id);
        }
        return definition;
    }

    @Override
    public MeasurementScope begin(SubsystemId id) {
        if (!enabled) {
            return NOOP_SCOPE;
        }
        source(id);
        long started = clock.nanoTime();
        return new MeasurementScope() {
            private boolean closed;

            @Override
            public void close() {
                if (!closed) {
                    closed = true;
                    record(id, clock.nanoTime() - started);
                }
            }
        };
    }

    @Override
    public void record(SubsystemId id, long durationNanos) {
        if (!enabled) {
            return;
        }
        if (durationNanos < 0) {
            throw new IllegalArgumentException("durationNanos must not be negative");
        }
        source(id).add(durationNanos);
    }

    @Override
    public void recordOverallTick(long durationNanos) {
        if (!enabled) {
            return;
        }
        if (durationNanos < 0) {
            throw new IllegalArgumentException("durationNanos must not be negative");
        }
        overall.add(durationNanos);
    }

    @Override
    public PerformanceSnapshot snapshot() {
        Map<SubsystemId, SourceState> ordered = new java.util.TreeMap<>(Comparator.comparing(SubsystemId::value));
        ordered.putAll(sources);
        Map<SubsystemId, PerformanceSnapshot.SubsystemSnapshot> snapshots = new LinkedHashMap<>();
        for (Map.Entry<SubsystemId, SourceState> entry : ordered.entrySet()) {
            SourceState state = entry.getValue();
            PerformanceBudget budget = state.budget;
            snapshots.put(
                    entry.getKey(),
                    new PerformanceSnapshot.SubsystemSnapshot(
                            budget.id(), budget.owner(), budget.budgetNanos(), state.window(enabled)));
        }
        return new PerformanceSnapshot(
                enabled, clock.nanoTime(), overall.window(enabled), snapshots);
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    private SourceState source(SubsystemId id) {
        Objects.requireNonNull(id, "id");
        SourceState state = sources.get(id);
        if (state == null) {
            throw new UnknownSubsystemException(id);
        }
        return state;
    }

    private final class SourceState {

        private final long[] samples;
        private PerformanceBudget budget;
        private int size;
        private int next;

        private SourceState(int capacity) {
            this.samples = new long[capacity];
        }

        private SourceState(int capacity, PerformanceBudget budget) {
            this.samples = new long[capacity];
            this.budget = Objects.requireNonNull(budget, "budget");
        }

        private synchronized void add(long durationNanos) {
            samples[next] = durationNanos;
            next = (next + 1) % samples.length;
            size = Math.min(size + 1, samples.length);
        }

        private synchronized PerformanceWindow window(boolean measurementEnabled) {
            if (!measurementEnabled) {
                return PerformanceWindow.disabled();
            }
            if (size == 0) {
                return PerformanceWindow.empty();
            }
            long[] copy = new long[size];
            int start = size == samples.length ? next : 0;
            for (int i = 0; i < size; i++) {
                copy[i] = samples[(start + i) % samples.length];
            }
            return PerformanceWindow.complete(copy);
        }
    }
}
