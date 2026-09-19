package rpg.platform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class PerformanceScopeInstrumentationTest {

    private static final Path SOURCES = Path.of("src", "main", "java");

    @Test
    void everyTickBoundedB15HotpathUsesTheSameShortLivedScopeBoundary() throws IOException {
        List<String> files =
                List.of(
                        "rpg/platform/ui/HudTick.java",
                        "rpg/platform/mob/HordeSweep.java",
                        "rpg/platform/combat/VanillaDamageListener.java",
                        "rpg/platform/currency/CoinDropListener.java",
                        "rpg/platform/zone/ZoneMovementListener.java",
                        "rpg/platform/statistics/DamageStatListener.java");

        for (String file : files) {
            String source = Files.readString(SOURCES.resolve(file), StandardCharsets.UTF_8);
            assertThat(source).as(file).contains("Supplier<MeasurementScope> performanceScope");
            assertThat(source)
                    .as(file)
                    .contains("try (MeasurementScope ignored = performanceScope.get())");
        }
    }
}
