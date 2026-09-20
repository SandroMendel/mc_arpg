package rpg.plugin.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class NoRuntimeProfilerDependencyTest {

    @Test
    void packagedPluginContainsNoSparkOrProfilerImplementationClasses() throws IOException {
        Path libs = Path.of("build", "libs");
        Path jar;
        try (Stream<Path> entries = Files.list(libs)) {
            jar =
                    entries
                            .filter(path -> path.getFileName().toString().endsWith(".jar"))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("plugin jar was not built"));
        }

        try (JarFile contents = new JarFile(jar.toFile())) {
            assertThat(contents.stream().map(entry -> entry.getName().toLowerCase(Locale.ROOT)))
                    .noneMatch(
                            name ->
                                    name.contains("/spark/")
                                            || name.contains("/profiler/")
                                            || name.endsWith("spark.class"));
        }
    }
}
