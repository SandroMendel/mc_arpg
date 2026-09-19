package rpg.plugin.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import rpg.core.performance.AlertState;
import rpg.core.performance.DefaultPerformanceRegistry;
import rpg.core.performance.PerformanceReport;
import rpg.core.performance.SubsystemId;

class PrometheusTextExporterTest {

    @TempDir Path temporaryDirectory;

    @Test
    void rendersStableMetricNamesBoundedLabelsSecondsAndSnapshotTimestamp() {
        PerformanceReport report = PerformanceTestReports.sample(AlertState.WARNING);
        PrometheusTextExporter exporter =
                new PrometheusTextExporter(temporaryDirectory.resolve("metrics.prom"));

        String text = exporter.render(report);

        assertThat(text).contains("rpg_tick_tps{window=\"current\"}");
        assertThat(text).contains("rpg_tick_mspt{quantile=\"p95\"} 25.000000");
        assertThat(text).contains("rpg_tick_duration_seconds{quantile=\"p95\"} 0.025000");
        assertThat(text).contains("rpg_subsystem_budget_seconds{subsystem=\"b05-combat\",owner=\"B05\"}");
        assertThat(text).contains("rpg_subsystem_duration_seconds{subsystem=\"b05-combat\",quantile=\"p95\"}");
        assertThat(text).contains("rpg_subsystem_budget_ratio{subsystem=\"b05-combat\"}");
        assertThat(text).contains("rpg_subsystem_alert_state{subsystem=\"b05-combat\"} 1");
        assertThat(text).contains("rpg_active_custom_mobs 800");
        assertThat(text).contains("rpg_performance_snapshot_timestamp_seconds 1700000000");
        assertThat(text).doesNotContain("player_name").doesNotContain("uuid");
        assertThat(text).contains("rpg_target_tps_min 19.500000");
        assertThat(text).endsWith("\n");
    }

    @Test
    void newlyRegisteredSourcesAndMissingSourcesRemainVisibleInTheExport() {
        SubsystemId id = new SubsystemId("b15-new-source");
        SubsystemId missing = new SubsystemId("b15-missing-source");
        DefaultPerformanceRegistry registry = new DefaultPerformanceRegistry(() -> 3_000_000_000L, 8, true);
        registry.register(id, Duration.ofMillis(5), "B15");
        registry.register(missing, Duration.ofMillis(5), "B15");
        registry.record(id, 2_000_000L);
        PerformanceReport report =
                new PerformanceReport(
                        Instant.ofEpochSecond(1_700_000_000L),
                        registry.snapshot(),
                        19.5d,
                        40.0d,
                        50.0d,
                        150L,
                        800L,
                        Map.of(id, AlertState.WARNING, missing, AlertState.NORMAL),
                        List.of(),
                        1L,
                        0L,
                        0L,
                        Set.of(missing),
                        Map.of());

        String text = new PrometheusTextExporter(temporaryDirectory.resolve("dynamic.prom")).render(report);

        assertThat(text).contains("subsystem=\"b15-new-source\"");
        assertThat(text).contains("rpg_subsystem_missing{subsystem=\"b15-missing-source\",owner=\"B15\"} 1");
        assertThat(text).contains("rpg_subsystem_duration_seconds{subsystem=\"b15-missing-source\",quantile=\"p95\"} NaN");
    }

    @Test
    void replacesTheTargetOnlyAfterACompleteFileWasRendered() throws Exception {
        Path target = temporaryDirectory.resolve("metrics.prom");
        Files.writeString(target, "old-complete-file\n");
        PrometheusTextExporter exporter = new PrometheusTextExporter(target);

        PrometheusTextExporter.ExportResult result =
                exporter.export(PerformanceTestReports.sample(AlertState.NORMAL));

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(target)).startsWith("# HELP rpg_tick_tps");
        assertThat(Files.readString(target)).doesNotContain("old-complete-file");
    }
}
