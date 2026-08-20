package rpg.platform.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * T086: B05 aggregates damage numbers; it does not draw them (FR-039).
 *
 * <p>The failure this prevents looks like a feature when it is introduced. The aggregation is here,
 * the target entity is right there, and spawning a text display is three lines away. What it costs
 * is the tick: at 150 players against 800 mobs that is thousands of short-lived entities per second,
 * every one of them replicated to every nearby client - and it would land in the block whose own
 * acceptance criterion is p95 MSPT under 40 ms.
 *
 * <p>Drawing belongs to B13, which can decide to batch, to throttle, or not to draw at all.
 *
 * <p>Same shape as B04's {@code NoDamageInterceptionTest}: scanning sources rather than classes, so
 * it also catches a block that does not exist yet.
 */
class NoDisplayEntityTest {

    /** Ways to put something on screen that B05 must not use. */
    private static final List<String> FORBIDDEN =
            List.of(
                    "TextDisplay",
                    "ArmorStand",
                    "EntityType.TEXT_DISPLAY",
                    "EntityType.ARMOR_STAND",
                    "spawnEntity",
                    "sendActionBar",
                    "showTitle",
                    "createHologram");

    private static final String B05_PACKAGE = "/rpg/platform/combat/";

    @Test
    void b05CreatesNoDisplayObjects() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path source : javaSources()) {
            String path = source.toString().replace('\\', '/');
            // Test sources are excluded: this rule is about what the production code does. A test
            // that spawns a zombie to check the equipping listener is not a display object.
            if (!path.contains(B05_PACKAGE) || path.contains("/test/")) {
                continue;
            }
            String content = Files.readString(source, StandardCharsets.UTF_8);
            for (String forbidden : FORBIDDEN) {
                if (content.contains(forbidden)) {
                    violations.add(path + " uses " + forbidden);
                }
            }
        }

        assertThat(violations)
                .as(
                        "FR-039: B05 publishes an aggregated DamageDealtEvent. Drawing it is B13's,"
                                + " which can decide how - and whether - to render it.")
                .isEmpty();
    }

    @Test
    void theScanReachesTheCombatSourcesAtAll() throws IOException {
        long combatSources =
                javaSources().stream()
                        .filter(p -> p.toString().replace('\\', '/').contains(B05_PACKAGE))
                        .count();

        // Without this, deleting the package would make the test above pass.
        assertThat(combatSources).isGreaterThanOrEqualTo(6);
    }

    @Test
    void theScanWouldCatchAViolation() {
        String offending =
                """
                TextDisplay display = world.spawn(location, TextDisplay.class);
                display.text(Component.text(damage));
                """;

        assertThat(FORBIDDEN.stream().anyMatch(offending::contains)).isTrue();
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
