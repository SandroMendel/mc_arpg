package rpg.core.performance;

import java.util.concurrent.atomic.AtomicLong;

/** Shared deterministic clock fixture for B15 core tests. */
final class PerformanceTestSupport {

    private PerformanceTestSupport() {}

    static TestClock clock() {
        return new TestClock();
    }

    static final class TestClock implements MonotonicClock {

        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long nanoTime() {
            return nanos.get();
        }

        void set(long value) {
            nanos.set(value);
        }

        void advance(long value) {
            nanos.addAndGet(value);
        }
    }
}
