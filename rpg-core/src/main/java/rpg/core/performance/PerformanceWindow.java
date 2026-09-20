package rpg.core.performance;

import java.util.Arrays;

/** Immutable statistics for one bounded measurement window. */
public final class PerformanceWindow {

    public enum Status {
        DISABLED,
        EMPTY,
        COMPLETE,
        INCOMPLETE
    }

    private final Status status;
    private final long sampleCount;
    private final long minimumNanos;
    private final long maximumNanos;
    private final double meanNanos;
    private final long p50Nanos;
    private final long p95Nanos;
    private final long p99Nanos;

    private PerformanceWindow(
            Status status,
            long sampleCount,
            long minimumNanos,
            long maximumNanos,
            double meanNanos,
            long p50Nanos,
            long p95Nanos,
            long p99Nanos) {
        this.status = status;
        this.sampleCount = sampleCount;
        this.minimumNanos = minimumNanos;
        this.maximumNanos = maximumNanos;
        this.meanNanos = meanNanos;
        this.p50Nanos = p50Nanos;
        this.p95Nanos = p95Nanos;
        this.p99Nanos = p99Nanos;
    }

    public static PerformanceWindow disabled() {
        return empty(Status.DISABLED);
    }

    public static PerformanceWindow empty() {
        return empty(Status.EMPTY);
    }

    public static PerformanceWindow incomplete(long[] samples) {
        return from(Status.INCOMPLETE, samples);
    }

    public static PerformanceWindow complete(long[] samples) {
        return from(Status.COMPLETE, samples);
    }

    private static PerformanceWindow empty(Status status) {
        return new PerformanceWindow(status, 0, 0, 0, 0.0d, 0, 0, 0);
    }

    private static PerformanceWindow from(Status status, long[] samples) {
        if (samples.length == 0) {
            return empty(status == Status.INCOMPLETE ? Status.INCOMPLETE : Status.EMPTY);
        }
        long[] sorted = Arrays.copyOf(samples, samples.length);
        Arrays.sort(sorted);
        double sum = 0.0d;
        for (long sample : sorted) {
            sum += sample;
        }
        return new PerformanceWindow(
                status,
                sorted.length,
                sorted[0],
                sorted[sorted.length - 1],
                sum / sorted.length,
                percentile(sorted, 0.50d),
                percentile(sorted, 0.95d),
                percentile(sorted, 0.99d));
    }

    private static long percentile(long[] sorted, double quantile) {
        int rank = (int) Math.ceil(quantile * sorted.length);
        return sorted[Math.max(0, rank - 1)];
    }

    public Status status() {
        return status;
    }

    public long sampleCount() {
        return sampleCount;
    }

    public long minimumNanos() {
        return minimumNanos;
    }

    public long maximumNanos() {
        return maximumNanos;
    }

    public double meanNanos() {
        return meanNanos;
    }

    public long p50Nanos() {
        return p50Nanos;
    }

    public long p95Nanos() {
        return p95Nanos;
    }

    public long p99Nanos() {
        return p99Nanos;
    }
}
