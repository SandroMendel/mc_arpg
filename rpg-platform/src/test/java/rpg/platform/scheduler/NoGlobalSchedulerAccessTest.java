package rpg.platform.scheduler;

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
 * T033 / T042 / SC-005: proves by static inspection that no unbound, global scheduling call exists
 * anywhere in the project.
 *
 * <p>ADR-007 and Constitution I.5 forbid the global Bukkit scheduler outright. A code review cannot
 * guarantee that for every future block (B02-B17), so the rule is asserted mechanically here: the
 * test scans every Java source file in the repository and fails on any forbidden call, no matter
 * which module introduces it.
 *
 * <p>{@link PaperSchedulerAdapter} is the single sanctioned place that talks to a Paper scheduler,
 * and even it uses only the region, entity and async schedulers - never the global one.
 */
class NoGlobalSchedulerAccessTest {

    /** Calls that would schedule tick work without a location or entity binding. */
    private static final List<Pattern> FORBIDDEN_CALLS =
            List.of(
                    Pattern.compile("Bukkit\\s*\\.\\s*getScheduler\\s*\\("),
                    Pattern.compile("getServer\\s*\\(\\s*\\)\\s*\\.\\s*getScheduler\\s*\\("),
                    Pattern.compile("\\bBukkitScheduler\\b"),
                    Pattern.compile("\\bBukkitRunnable\\b"),
                    Pattern.compile("getGlobalRegionScheduler\\s*\\("),
                    Pattern.compile("\\bGlobalRegionScheduler\\b"));

    /** This test names the very calls it forbids, so it must not scan itself. */
    private static final String SELF = "NoGlobalSchedulerAccessTest.java";

    @Test
    void noSourceFileUsesAnUnboundGlobalScheduler() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path source : javaSources()) {
            if (source.getFileName().toString().equals(SELF)) {
                continue;
            }
            String code = stripComments(Files.readString(source, StandardCharsets.UTF_8));
            for (Pattern forbidden : FORBIDDEN_CALLS) {
                if (forbidden.matcher(code).find()) {
                    violations.add(source + " matches " + forbidden.pattern());
                }
            }
        }

        assertThat(violations)
                .as(
                        "ADR-007: tick work must be location- or entity-bound. Use the Scheduler"
                                + " abstraction instead of a global scheduler.")
                .isEmpty();
    }

    @Test
    void theScanActuallyReachesTheProjectSources() throws IOException {
        // Guards against the scan silently passing because it found nothing to look at - a green
        // test that inspected zero files would be worse than no test at all.
        List<Path> sources = javaSources();

        assertThat(sources).hasSizeGreaterThan(20);
        assertThat(sources)
                .anyMatch(path -> path.getFileName().toString().equals("PaperSchedulerAdapter.java"))
                .anyMatch(path -> path.getFileName().toString().equals("Scheduler.java"))
                .anyMatch(path -> path.getFileName().toString().equals("RpgPlugin.java"));
    }

    @Test
    void theScanWouldCatchAViolation() {
        // Proves the patterns match what they claim to, without committing a real violation.
        String offending = "void tick() { Bukkit.getScheduler().runTask(plugin, this::work); }";
        assertThat(stripComments(offending)).isEqualTo(offending);

        assertThat(FORBIDDEN_CALLS).anyMatch(pattern -> pattern.matcher(offending).find());
    }

    /**
     * Removes block and line comments.
     *
     * <p>Documentation that names a forbidden call in order to explain why it is forbidden - as the
     * adapter's own class comment does - is not a violation. Only real code counts.
     */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    /** Every {@code .java} file in the repository, across all five modules. */
    private static List<Path> javaSources() throws IOException {
        Path repositoryRoot = repositoryRoot();
        try (Stream<Path> paths = Files.walk(repositoryRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/build/"))
                    .toList();
        }
    }

    /** Walks up from the module directory to the directory holding {@code settings.gradle.kts}. */
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
