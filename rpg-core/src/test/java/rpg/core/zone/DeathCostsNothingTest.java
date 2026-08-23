package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T060 - a death costs no experience and no items (FR-036), and B09 is not the block that could
 * change that.
 *
 * <p><b>Why this is a source test rather than a behavioural one.</b> The guarantee is not B09's to
 * give: the inventory survives because B05 sets {@code keepInventory} in every world - proven by
 * {@code FullBootstrapTest.inventoryIsKeptOnDeathInEveryWorld} - and experience survives because
 * nothing takes it away. A test here that killed a character and asserted their inventory would be
 * re-testing B05 through B09's door and would go green for reasons that have nothing to do with this
 * block.
 *
 * <p>What <em>is</em> B09's to give is the promise not to become the exception. This block moves a
 * player on death and does nothing else, and the only way to keep that true over time is to say so
 * where a change would be noticed.
 */
class DeathCostsNothingTest {

    private static final Path PACKAGE =
            repositoryRoot().resolve("rpg-core/src/main/java/rpg/core/zone");

    private static final Path PLATFORM =
            repositoryRoot().resolve("rpg-platform/src/main/java/rpg/platform/zone");

    @Test
    @DisplayName("nothing in this block touches experience")
    void nothingTouchesExperience() throws IOException {
        assertThat(mentionsOf("Experience", "setExp", "giveExp", "setLevel", "XpSource"))
                .as(
                        "a death costs no experience (FR-036), and taking some would be this block"
                                + " growing a death penalty it was never given")
                .isEmpty();
    }

    @Test
    @DisplayName("nothing in this block touches an inventory")
    void nothingTouchesAnInventory() throws IOException {
        assertThat(mentionsOf("getInventory", "setItem", "ItemStack", "clear()"))
                .as("a death costs no items (FR-036); the equipment damage from ADR-017 is B11's")
                .isEmpty();
    }

    @Test
    @DisplayName("the respawn routing hands out a place and nothing else (FR-037c)")
    void routingOnlyHandsOutAPlace() throws IOException {
        String source = Files.readString(PACKAGE.resolve("RespawnRouting.java"));

        // Its whole surface is two methods returning a position and a boolean. If it ever grows a
        // side effect, this is the line somebody has to argue with.
        assertThat(source).doesNotContain("void ");
        assertThat(source).contains("WorldPosition respawnFor");
    }

    private static List<String> mentionsOf(String... needles) throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path directory : List.of(PACKAGE, PLATFORM)) {
            for (Path source : sources(directory)) {
                String text = Files.readString(source);
                for (String needle : needles) {
                    if (text.contains(needle)) {
                        offenders.add(source.getFileName() + " mentions " + needle);
                    }
                }
            }
        }
        return offenders;
    }

    /**
     * The code of a package, without its documentation.
     *
     * <p>{@code package-info.java} is excluded on purpose, and the first run of this test is why: it
     * names {@code XpSource} in a sentence explaining that the source stays <em>unfilled</em>. A scan
     * that cannot tell a mention from a use turns the honest note into a failure and teaches the next
     * person to delete the note rather than keep the promise.
     */
    private static List<Path> sources(Path directory) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .toList();
        }
    }

    private static Path repositoryRoot() {
        Path at = Path.of("").toAbsolutePath();
        while (at != null && !Files.exists(at.resolve("settings.gradle.kts"))) {
            at = at.getParent();
        }
        if (at == null) {
            throw new IllegalStateException("repository root not found");
        }
        return at;
    }
}
