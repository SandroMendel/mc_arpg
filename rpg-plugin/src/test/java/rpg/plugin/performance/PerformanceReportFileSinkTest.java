package rpg.plugin.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import rpg.core.performance.AlertState;

class PerformanceReportFileSinkTest {

    @TempDir Path temporaryDirectory;

    @Test
    void appendsACompleteStructuredReportFromTheAsyncCycle() throws Exception {
        Path target = temporaryDirectory.resolve("performance/performance-report.log");
        PerformanceReportFileSink sink =
                new PerformanceReportFileSink(target, new PerformanceLogReporter(), line -> {});

        sink.write(PerformanceTestReports.sample(AlertState.CRITICAL));

        assertThat(Files.readString(target)).contains("phase=REPORT").contains("CRITICAL");
    }

    @Test
    void keepsTheCycleAliveWhenTheReportFileCannotBeWritten() throws Exception {
        Path blocked = temporaryDirectory.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        List<String> errors = new ArrayList<>();
        PerformanceReportFileSink sink =
                new PerformanceReportFileSink(
                        blocked.resolve("report.log"), new PerformanceLogReporter(), errors::add);

        sink.write(PerformanceTestReports.sample(AlertState.NORMAL));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("REPORT_FILE");
    }
}
