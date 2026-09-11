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

    /**
     * Wo das Reisefenster seit B13 liegt.
     *
     * <p>Es stand hier befristet (ADR-032) und ist eingelöst: {@code WaypointMenu} gehört jetzt zu
     * {@code rpg.platform.ui}. Die Zusage dieses Tests — <b>ein Tod kostet keine Gegenstände</b> —
     * gilt unverändert; nur das Fenster wohnt woanders.
     */
    private static final Path UI =
            repositoryRoot().resolve("rpg-platform/src/main/java/rpg/platform/ui");

    @Test
    @DisplayName("nothing in this block touches experience")
    void nothingTouchesExperience() throws IOException {
        assertThat(mentionsOf("Experience", "setExp", "giveExp", "setLevel", "XpSource"))
                .as(
                        "a death costs no experience (FR-036), and taking some would be this block"
                                + " growing a death penalty it was never given")
                .isEmpty();
    }

    /**
     * <b>The window is not what this test is about, and the exclusion says why.</b>
     *
     * <p>{@code WaypointMenu} builds a chest interface out of {@code ItemStack}s and puts them into
     * an inventory it created itself (ADR-032). The needles below caught it, and the honest reading
     * of that catch is that the needles were too wide, not that the window is a death penalty: what
     * FR-036 forbids is reaching into a <em>player's</em> inventory, and that is checked separately
     * below for the window too.
     */
    private static final String THE_WINDOW = "WaypointMenu.java";

    @Test
    @DisplayName("nothing in this block touches an inventory")
    void nothingTouchesAnInventory() throws IOException {
        assertThat(mentionsOf("getInventory", "setItem", "ItemStack", "clear()"))
                .as("a death costs no items (FR-036); the equipment damage from ADR-017 is B11's")
                .isEmpty();
    }

    @Test
    @DisplayName("the waypoint window fills its own chest and never a player's backpack")
    void thewindowNeverReachesIntoAPlayersInventory() throws IOException {
        String window = Files.readString(UI.resolve(THE_WINDOW));

        // It creates the inventory it fills. Anything that got at a player's own would show up as
        // one of these, and none of them is needed to draw six icons.
        //
        // SEIT B13 ueber MenuFrame.buildPlain statt direkt ueber Bukkit.createInventory - der
        // Rahmen erzeugt es, das Fenster fuellt es. Die Zusage ist dieselbe: es baut sein eigenes
        // und greift in keines.
        assertThat(window).contains("buildPlain");
        assertThat(window).doesNotContain("getInventory()");
        assertThat(window).doesNotContain("addItem");
        assertThat(window).doesNotContain("dropItem");
        assertThat(window).doesNotContain("Player");
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
                    .filter(path -> !path.getFileName().toString().equals(THE_WINDOW))
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
