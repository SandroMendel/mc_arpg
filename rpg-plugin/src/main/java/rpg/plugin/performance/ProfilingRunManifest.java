package rpg.plugin.performance;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Immutable association between one controlled Paper/Spark session and its B15 run. */
public record ProfilingRunManifest(
        String runId, Instant startedAt, Instant endedAt, String artifactUrl) {

    public ProfilingRunManifest {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(startedAt, "startedAt");
        runId = runId.trim();
        if (runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("endedAt must not be before startedAt");
        }
        if (artifactUrl != null) {
            artifactUrl = artifactUrl.trim();
            if (artifactUrl.isBlank()) {
                artifactUrl = null;
            }
        }
    }

    public static ProfilingRunManifest started(String runId, Instant startedAt) {
        return new ProfilingRunManifest(runId, startedAt, null, null);
    }

    public ProfilingRunManifest finish(Instant endedAt, String artifactUrl) {
        return new ProfilingRunManifest(runId, startedAt, endedAt, artifactUrl);
    }

    public boolean complete() {
        return endedAt != null;
    }

    public Duration duration() {
        if (!complete()) {
            throw new IllegalStateException("profiling run has not ended");
        }
        return Duration.between(startedAt, endedAt);
    }
}
