package rpg.plugin.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import rpg.core.performance.AlertState;

class ExportFailureKeepsLocalReportingTest {

    @TempDir Path temporaryDirectory;

    @Test
    void keepsTheLastFileAndLocalCriticalReportWhenTheTargetCannotBeWritten() throws Exception {
        Path blockedParent = temporaryDirectory.resolve("blocked");
        Files.writeString(blockedParent, "not-a-directory");
        Path target = blockedParent.resolve("metrics.prom");
        PrometheusTextExporter exporter = new PrometheusTextExporter(target);
        var report = PerformanceTestReports.sample(AlertState.CRITICAL);

        PrometheusTextExporter.ExportResult result = exporter.export(report);
        String localLog = new PerformanceLogReporter().render(report);

        assertThat(result.success()).isFalse();
        assertThat(exporter.failureCount()).isEqualTo(1L);
        assertThat(localLog).contains("CRITICAL").contains("b05-combat");
    }
}
