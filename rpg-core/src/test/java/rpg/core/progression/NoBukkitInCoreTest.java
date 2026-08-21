package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rules of B06 must stay free of Bukkit (Principle III).
 *
 * <p>The Gradle module boundary already enforces this - {@code rpg-core} has no Paper dependency at
 * all, so a Bukkit import would not compile. This scan exists for the case that somebody adds the
 * dependency to make one thing work: then the boundary is gone and nothing else would say so.
 *
 * <p>The distance measurement is the one thing here that genuinely needs Bukkit, and it lives behind
 * {@link ProximityCheck} in {@code rpg-platform} for exactly that reason.
 */
class NoBukkitInCoreTest {

    private static final List<String> FORBIDDEN =
            List.of("org.bukkit", "io.papermc", "net.minecraft", "org.spigotmc");

    private static final String B06_CORE = "/rpg/core/progression/";

    @Test
    @DisplayName("no source of this block mentions Bukkit, Paper or NMS")
    void coreStaysBukkitFree() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSources()) {
            String path = source.toString().replace('\\', '/');
            // Test sources are excluded on principle: this rule is about the production code. It
            // happens that the tests are clean too, but that is not what is being promised.
            if (!path.contains(B06_CORE) || path.contains("/test/")) {
                continue;
            }
            String content = Files.readString(source, StandardCharsets.UTF_8);
            for (String forbidden : FORBIDDEN) {
                if (content.contains(forbidden)) {
                    violations.add(path + " mentions " + forbidden);
                }
            }
        }

        assertThat(violations)
                .as(
                        "Principle III: the rules must be verifiable without a running server."
                                + " The distance measurement is behind ProximityCheck for this"
                                + " reason.")
                .isEmpty();
    }

    @Test
    @DisplayName("and the scan really looked at this block")
    void scanCoversTheBlock() throws IOException {
        long sources =
                javaSources().stream()
                        .map(path -> path.toString().replace('\\', '/'))
                        .filter(path -> path.contains(B06_CORE) && !path.contains("/test/"))
                        .count();

        assertThat(sources).isGreaterThanOrEqualTo(20);
    }

    @Test
    @DisplayName("no source of this block reaches for equipment or items either")
    void noEquipmentAccess() throws IOException {
        // B11 owns items. B06 gates them by level and stops there - the same boundary B05 draws.
        List<String> forbidden = List.of("ItemStack", "getInventory", "getEquipment", "ItemMeta");
        List<String> violations = new ArrayList<>();
        for (Path source : javaSources()) {
            String path = source.toString().replace('\\', '/');
            if (!path.contains(B06_CORE) || path.contains("/test/")) {
                continue;
            }
            String content = Files.readString(source, StandardCharsets.UTF_8);
            for (String word : forbidden) {
                if (content.contains(word)) {
                    violations.add(path + " uses " + word);
                }
            }
        }

        assertThat(violations).isEmpty();
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
