package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * T005: the shipped plugin jar must contain this project's classes and nothing else.
 *
 * <p>B01 established that property deliberately, to stay clear of classloader conflicts in a
 * shared Bukkit process. B02 is where it would quietly break: driver, pool and Flyway are real
 * third-party libraries, and the usual answer - shading them - would put a second JDBC driver into
 * a classloader other plugins share. They are declared in the {@code libraries:} section of
 * plugin.yml instead (ADR-010), and this test is what keeps it that way.
 *
 * <p>Skipped when no jar has been built yet, so a plain {@code test} run does not fail; the
 * {@code build} task produces the jar before tests of the full pipeline run.
 */
class JarContainsNoThirdPartyClassesTest {

    @Test
    void theBuiltJarContainsOnlyThisProjectsClasses() throws IOException {
        Optional<Path> jar = newestPluginJar();
        if (jar.isEmpty()) {
            // Nothing built yet - the assertion below has nothing to inspect. Reported rather than
            // silently passing, because a green test that looked at nothing is worse than none.
            System.out.println("[T005] no plugin jar present yet - run `gradlew build` first");
            return;
        }

        List<String> foreign = new ArrayList<>();
        try (JarFile file = new JarFile(jar.get().toFile())) {
            file.stream()
                    .map(entry -> entry.getName())
                    .filter(name -> name.endsWith(".class"))
                    .filter(name -> !name.startsWith("rpg/"))
                    .forEach(foreign::add);
        }

        assertThat(foreign)
                .as(
                        "ADR-010: third-party libraries belong in the `libraries:` section of"
                                + " plugin.yml, not inside the jar")
                .isEmpty();
    }

    @Test
    void theJarStillCarriesTheProjectsOwnClasses() throws IOException {
        Optional<Path> jar = newestPluginJar();
        if (jar.isEmpty()) {
            return;
        }

        List<String> own = new ArrayList<>();
        try (JarFile file = new JarFile(jar.get().toFile())) {
            file.stream()
                    .map(entry -> entry.getName())
                    .filter(name -> name.startsWith("rpg/") && name.endsWith(".class"))
                    .forEach(own::add);
        }

        // Guards against the first test passing because the jar is empty: an empty jar has no
        // foreign classes either.
        assertThat(own).hasSizeGreaterThan(20);
        assertThat(own).anyMatch(name -> name.startsWith("rpg/core/"));
        assertThat(own).anyMatch(name -> name.startsWith("rpg/plugin/"));
    }

    private static Optional<Path> newestPluginJar() throws IOException {
        Path libs = repositoryRoot().resolve("rpg-plugin/build/libs");
        if (!Files.isDirectory(libs)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(libs)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified()));
        }
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle.kts"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("could not locate the repository root");
        }
        return candidate;
    }
}
