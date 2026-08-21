package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The maximum level must come from the curve, never from the code (FR-004).
 *
 * <p>A source scan rather than a behavioural test, because the failure mode is a well-meant
 * shortcut: somebody writes {@code if (level >= 60)} once, and from then on raising the ceiling is a
 * code change instead of a line of configuration. A behavioural test would still pass, because 60
 * happens to be the configured value today.
 *
 * <p>Same shape as {@code NoEquipmentAccessTest} in B05, and test sources are excluded for the same
 * reason: a test that writes 60 to build a fixture is not the block hardcoding a ceiling.
 */
class NoHardcodedMaxLevelTest {

    private static final String B06_CORE = "/rpg/core/progression/";

    /** A bare 60 next to something level-shaped. Narrow on purpose - a broad scan cries wolf. */
    private static final Pattern SUSPICIOUS =
            Pattern.compile("(?i)(max_?level|level)\\s*(=|==|>=|<=|>|<)\\s*60\\b|\\b60\\s*(==|>=|<=)");

    @Test
    @DisplayName("no source in this block mentions 60 as a level boundary")
    void noHardcodedCeiling() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSources()) {
            String path = source.toString().replace('\\', '/');
            if (!path.contains(B06_CORE) || path.contains("/test/")) {
                continue;
            }
            String content = Files.readString(source, StandardCharsets.UTF_8);
            if (SUSPICIOUS.matcher(content).find()) {
                violations.add(path);
            }
        }

        assertThat(violations)
                .as(
                        "FR-004: the ceiling is the highest key of xp-curve. Raising it must stay one"
                                + " more line in progression.yml, not an edit here.")
                .isEmpty();
    }

    @Test
    @DisplayName("the only source of the maximum level is the curve itself")
    void maxLevelIsDerived() {
        // Two different curves, two different ceilings, no code change in between.
        assertThat(XpCurve.of(CurveFixture.twoLevels()).maxLevel()).isEqualTo(3);
        assertThat(XpCurve.of(CurveFixture.valid()).maxLevel()).isEqualTo(10);
        assertThat(XpCurve.of(CurveFixture.upTo60()).maxLevel()).isEqualTo(60);
    }

    @Test
    @DisplayName("the scan actually looks at this block, so a pass is not an empty pass")
    void scanCoversTheBlock() throws IOException {
        // Without this, deleting the package would make the test above pass triumphantly.
        long sources =
                javaSources().stream()
                        .map(path -> path.toString().replace('\\', '/'))
                        .filter(path -> path.contains(B06_CORE) && !path.contains("/test/"))
                        .count();

        assertThat(sources).isGreaterThanOrEqualTo(15);
    }

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> paths = Files.walk(repositoryRoot())) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/build/"))
                    .toList();
        }
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle.kts"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("could not find the repository root");
        }
        return candidate;
    }
}
