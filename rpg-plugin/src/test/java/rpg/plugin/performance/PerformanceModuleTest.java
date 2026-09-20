package rpg.plugin.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import rpg.core.performance.AlertState;

class PerformanceModuleTest {

    @TempDir Path temporaryDirectory;

    @Test
    void shipsTheModuleConfigurationWithTheRequiredExportAndTargetKeys() throws Exception {
        String yaml = Files.readString(Path.of("src", "main", "resources", "performance.yml"));

        assertThat(yaml)
                .contains("measurement:")
                .contains("alerts:")
                .contains("report:")
                .contains("export:")
                .contains("targets:")
                .contains("mean-tps-min: 19.5");
    }

    @Test
    void wiresCompleteMetricReplacementAndStructuredReportArtifacts() throws Exception {
        var report = PerformanceTestReports.sample(AlertState.WARNING);
        Path metrics = temporaryDirectory.resolve("performance/metrics.prom");
        Path reportFile = temporaryDirectory.resolve("performance/performance-report.log");

        PrometheusTextExporter.ExportResult export =
                new PrometheusTextExporter(metrics).export(report);
        new PerformanceReportFileSink(reportFile, new PerformanceLogReporter(), line -> {})
                .write(report);

        assertThat(export.success()).isTrue();
        assertThat(Files.readString(metrics)).contains("rpg_tick_tps");
        assertThat(Files.readString(reportFile)).contains("phase=REPORT");
    }
}
