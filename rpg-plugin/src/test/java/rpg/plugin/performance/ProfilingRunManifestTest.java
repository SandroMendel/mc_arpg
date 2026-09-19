package rpg.plugin.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class ProfilingRunManifestTest {

    @Test
    void recordsStartEndRunIdAndOptionalArtifact() {
        Instant start = Instant.parse("2026-09-11T10:00:00Z");
        ProfilingRunManifest manifest =
                ProfilingRunManifest.started("  run-15  ", start)
                        .finish(start.plusSeconds(600), " https://spark.example/report/15 ");

        assertThat(manifest.runId()).isEqualTo("run-15");
        assertThat(manifest.complete()).isTrue();
        assertThat(manifest.duration()).isEqualTo(Duration.ofMinutes(10));
        assertThat(manifest.artifactUrl()).isEqualTo("https://spark.example/report/15");
    }

    @Test
    void requiresAnOrderedRunAndAllowsACompletedRunWithoutAnArtifact() {
        Instant start = Instant.parse("2026-09-11T10:00:00Z");
        assertThat(ProfilingRunManifest.started("run-15", start).finish(start, " ").artifactUrl())
                .isNull();
        assertThatThrownBy(() -> ProfilingRunManifest.started("run-15", start).finish(start.minusSeconds(1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProfilingRunManifest.started(" ", start))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
