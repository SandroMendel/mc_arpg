package rpg.persistence;

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

import org.junit.jupiter.api.Test;

/**
 * T061: proves by static inspection that the database stays encapsulated in this module, and that
 * nothing deletes retained statistics.
 *
 * <p>Same reasoning as {@code NoGlobalSchedulerAccessTest} in B01: a rule that only lives in a
 * review is a rule that a future block will break. Asserting it mechanically means B03 through B17
 * inherit it without anyone having to remember.
 *
 * <p>Two rules are checked:
 *
 * <ul>
 *   <li>No {@code java.sql} or {@code DataSource} usage outside {@code rpg-persistence} - otherwise
 *       the separate login pool (FR-008) would be pointless, since any block could take its own
 *       connections.
 *   <li>No {@code DELETE} against {@code player_statistic_daily} anywhere - FR-017 promises
 *       indefinite retention, and nothing should be able to quietly introduce a cleanup job.
 * </ul>
 */
class NoDirectDatabaseAccessTest {

    private static final String SELF = "NoDirectDatabaseAccessTest.java";

    private static final List<Pattern> FORBIDDEN_OUTSIDE_PERSISTENCE =
            List.of(
                    Pattern.compile("\\bjava\\.sql\\."),
                    Pattern.compile("\\bjavax\\.sql\\."),
                    Pattern.compile("\\bDataSource\\b"),
                    Pattern.compile("\\bDriverManager\\b"));

    private static final Pattern DELETES_STATISTICS =
            Pattern.compile("(?i)DELETE\\s+FROM\\s+(rpg\\.)?player_statistic_daily");

    @Test
    void noModuleOutsidePersistenceTouchesTheDatabaseDirectly() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path source : javaSources()) {
            String path = source.toString().replace('\\', '/');
            if (path.contains("/rpg-persistence/") || source.getFileName().toString().equals(SELF)) {
                continue;
            }
            String code = stripComments(Files.readString(source, StandardCharsets.UTF_8));
            for (Pattern forbidden : FORBIDDEN_OUTSIDE_PERSISTENCE) {
                if (forbidden.matcher(code).find()) {
                    violations.add(path + " matches " + forbidden.pattern());
                }
            }
        }

        assertThat(violations)
                .as(
                        "FR-015: blocks reach data through the repository interfaces only. Direct"
                                + " JDBC would also defeat the separate login pool of FR-008.")
                .isEmpty();
    }

    @Test
    void nothingDeletesRetainedStatistics() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path source : javaSources()) {
            if (source.getFileName().toString().equals(SELF)) {
                continue;
            }
            String code = stripComments(Files.readString(source, StandardCharsets.UTF_8));
            if (DELETES_STATISTICS.matcher(code).find()) {
                violations.add(source.toString());
            }
        }

        assertThat(violations)
                .as("FR-017: statistics are retained indefinitely - no cleanup job may appear")
                .isEmpty();
    }

    @Test
    void theScanActuallyReachesTheProjectSources() throws IOException {
        // A green scan that inspected nothing would be worse than no scan.
        List<Path> sources = javaSources();

        assertThat(sources).hasSizeGreaterThan(40);
        assertThat(sources)
                .anyMatch(p -> p.getFileName().toString().equals("JdbcPlayerStateRepository.java"))
                .anyMatch(p -> p.getFileName().toString().equals("RpgPlugin.java"))
                .anyMatch(p -> p.getFileName().toString().equals("Repository.java"));
    }

    @Test
    void theScanWouldCatchAViolation() {
        String offending = "Connection c = java.sql.DriverManager.getConnection(url);";
        String deleting = "DELETE FROM rpg.player_statistic_daily WHERE day < ?";

        assertThat(FORBIDDEN_OUTSIDE_PERSISTENCE).anyMatch(p -> p.matcher(offending).find());
        assertThat(DELETES_STATISTICS.matcher(deleting).find()).isTrue();
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> paths = Files.walk(repositoryRoot())) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/build/"))
                    .toList();
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
