package rpg.platform.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * T053: B04 mirrors the health bar, and does not touch damage (FR-030b, FR-042).
 *
 * <p>The failure this prevents does not look like a mistake when it is introduced. B04 already owns
 * the health bar and already has a listener in this package; making fall damage behave correctly is
 * two lines away and would work. What it costs is the block boundary: the combat pipeline is B05,
 * and once damage handling starts here it will not move back. B05 then has to either duplicate the
 * mapping or fight this one, and every vanilla damage source becomes a question of which of two
 * places decides.
 *
 * <p>Same shape and same reasoning as B03's {@code NoCompetingSessionListenersTest}: scanning
 * sources rather than classes, so it catches a block that does not exist yet.
 */
class NoDamageInterceptionTest {

    /** Events that decide damage. All of them belong to B05. */
    private static final List<String> COMBAT_EVENTS =
            List.of(
                    "EntityDamageEvent",
                    "EntityDamageByEntityEvent",
                    "EntityDamageByBlockEvent",
                    "EntityDeathEvent",
                    "PlayerDeathEvent");

    private static final Pattern HANDLER =
            Pattern.compile(
                    "@EventHandler[^)]*\\)?\\s*(?:public\\s+)?void\\s+\\w+\\s*\\(\\s*(\\w+)",
                    Pattern.DOTALL);

    /** The package this block owns on the Paper side. */
    private static final String B04_PACKAGE = "/rpg/platform/stats/";

    private static final String SELF = "NoDamageInterceptionTest.java";

    @Test
    void b04HandlesNoCombatEvent() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path source : javaSources()) {
            String path = source.toString().replace('\\', '/');
            if (!path.contains(B04_PACKAGE) || source.getFileName().toString().equals(SELF)) {
                continue;
            }
            Matcher matcher = HANDLER.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (matcher.find()) {
                if (COMBAT_EVENTS.contains(matcher.group(1))) {
                    violations.add(path + " handles " + matcher.group(1));
                }
            }
        }

        assertThat(violations)
                .as(
                        "FR-030b/FR-042: redirecting vanilla damage sources is B05. B04 supplies the"
                                + " mitigation function and the resource container, and mirrors the"
                                + " bar - nothing else.")
                .isEmpty();
    }

    @Test
    void theGuardDoesHandleRegenerationSoTheScanIsNotVacuouslyGreen() throws IOException {
        // Without this, deleting every listener in the package would make the test above pass.
        List<String> handled = new ArrayList<>();

        for (Path source : javaSources()) {
            String path = source.toString().replace('\\', '/');
            if (!path.contains(B04_PACKAGE) || path.contains("/test/")) {
                continue;
            }
            Matcher matcher = HANDLER.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (matcher.find()) {
                handled.add(matcher.group(1));
            }
        }

        assertThat(handled)
                .as("the regeneration guard is B04's only listener, and it is narrow on purpose")
                .contains("EntityRegainHealthEvent", "FoodLevelChangeEvent")
                .doesNotContainAnyElementsOf(COMBAT_EVENTS);
    }

    @Test
    void theScanWouldCatchAViolation() {
        String offending =
                """
                @EventHandler(ignoreCancelled = true)
                public void onDamage(EntityDamageEvent event) {
                    event.setDamage(0.0);
                }
                """;

        Matcher matcher = HANDLER.matcher(offending);

        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(1)).isEqualTo("EntityDamageEvent");
        assertThat(COMBAT_EVENTS).contains(matcher.group(1));
    }

    @Test
    void theScanActuallyReachesTheProjectSources() throws IOException {
        assertThat(javaSources()).hasSizeGreaterThan(20);
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
            throw new IllegalStateException("could not locate the repository root");
        }
        return candidate;
    }
}
