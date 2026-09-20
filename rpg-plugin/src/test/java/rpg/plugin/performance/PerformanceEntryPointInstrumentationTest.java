package rpg.plugin.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PerformanceEntryPointInstrumentationTest {

    @Test
    void loginAndAdminExecutionHaveSeparateShortLivedMeasurementBoundaries() throws IOException {
        Map<Path, String> expectations =
                Map.of(
                        Path.of(
                                "..",
                                "rpg-platform",
                                "src",
                                "main",
                                "java",
                                "rpg",
                                "platform",
                                "session",
                                "SessionPreLoadListener.java"),
                        "not a server-tick metric",
                        Path.of(
                                "src",
                                "main",
                                "java",
                                "rpg",
                                "plugin",
                                "command",
                                "framework",
                                "CommandTree.java"),
                        "try (MeasurementScope ignored = performanceScope.get())");

        for (Map.Entry<Path, String> entry : expectations.entrySet()) {
            String source = Files.readString(entry.getKey(), StandardCharsets.UTF_8);
            assertThat(source)
                    .as(entry.getKey().toString())
                    .contains("Supplier<MeasurementScope> performanceScope")
                    .contains(entry.getValue());
        }
    }
}
