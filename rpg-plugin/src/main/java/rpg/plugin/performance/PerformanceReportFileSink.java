package rpg.plugin.performance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.function.Consumer;

import rpg.core.performance.PerformanceReport;

/** Appends structured reports from the asynchronous report cycle to a local artifact file. */
public final class PerformanceReportFileSink {

    private final Path target;
    private final PerformanceLogReporter reporter;
    private final Consumer<String> errorSink;

    public PerformanceReportFileSink(Path target, PerformanceLogReporter reporter, Consumer<String> errorSink) {
        this.target = Objects.requireNonNull(target, "target");
        this.reporter = Objects.requireNonNull(reporter, "reporter");
        this.errorSink = Objects.requireNonNull(errorSink, "errorSink");
    }

    /** Writes one complete line; failures are reported locally and never escape into the cycle. */
    public void write(PerformanceReport report) {
        Objects.requireNonNull(report, "report");
        try {
            Path absoluteTarget = target.toAbsolutePath();
            Path parent = absoluteTarget.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    absoluteTarget,
                    reporter.render(report) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException failure) {
            errorSink.accept(
                    "[performance] phase=REPORT_FILE state=FAILURE reason="
                            + (failure.getMessage() == null
                                    ? failure.getClass().getSimpleName()
                                    : failure.getMessage()));
        }
    }

    public Path target() {
        return target;
    }
}
