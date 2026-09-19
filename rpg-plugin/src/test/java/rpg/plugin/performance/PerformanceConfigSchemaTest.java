package rpg.plugin.performance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import rpg.core.config.ConfigValidationException;
import rpg.platform.config.YamlConfigLoader;

class PerformanceConfigSchemaTest {

    @TempDir Path temporaryDirectory;

    @Test
    void rejectsAnInvalidWindowSize() throws Exception {
        assertRejected(
                "measurement:\n  enabled: true\n  window-samples: 0\n"
                        + "alerts:\n  warning-ratio: 0.9\n  critical-after-seconds: 60\n"
                        + "report:\n  interval-seconds: 60\n"
                        + "export:\n  enabled: true\n  path: performance/metrics.prom\n");
    }

    @Test
    void rejectsAnAlertThresholdOutsideTheConfiguredRange() throws Exception {
        assertRejected(
                "measurement:\n  enabled: true\n  window-samples: 256\n"
                        + "alerts:\n  warning-ratio: 1.2\n  critical-after-seconds: 60\n"
                        + "report:\n  interval-seconds: 60\n"
                        + "export:\n  enabled: true\n  path: performance/metrics.prom\n");
    }

    @Test
    void rejectsNonPositiveReportIntervalsAndBlankExportPaths() throws Exception {
        assertRejected(
                "measurement:\n  enabled: true\n  window-samples: 256\n"
                        + "alerts:\n  warning-ratio: 0.9\n  critical-after-seconds: 60\n"
                        + "report:\n  interval-seconds: 0\n"
                        + "export:\n  enabled: true\n  path: '   '\n");
    }

    private void assertRejected(String document) throws Exception {
        Path file = temporaryDirectory.resolve("performance.yml");
        Files.writeString(file, document);
        YamlConfigLoader loader = new YamlConfigLoader(temporaryDirectory);

        assertThatThrownBy(
                        () ->
                                loader.loadAndValidate(
                                        Path.of("performance.yml"), PerformanceConfigSchema.schema()))
                .isInstanceOf(ConfigValidationException.class);
    }
}
