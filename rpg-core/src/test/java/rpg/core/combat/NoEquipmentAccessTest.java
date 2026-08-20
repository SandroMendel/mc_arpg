package rpg.core.combat;


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
 * T066: B05 reports a death; it does not reach into equipment (FR-030).
 *
 * <p>The death penalty is equipment damage, and the temptation is obvious: the death is right here,
 * the victim is right here, and applying durability loss is a few lines away. What it costs is that
 * B11 - which owns items, durability and what "damaged" even means for a rolled item - would find
 * the decision already made somewhere else, by a block that knows none of that.
 *
 * <p>The death event carries the victim, the killer and the full damage split. That is everything
 * B11 needs, and it is where the decision belongs.
 *
 * <p>Same shape as B04's {@code NoDamageInterceptionTest}: source scanning, so it also catches a
 * block that does not exist yet.
 */
class NoEquipmentAccessTest {

    /** Ways to touch equipment or item state that B05 must not use. */
    private static final List<String> FORBIDDEN =
            List.of(
                    "getInventory",
                    "getEquipment",
                    "ItemStack",
                    "Damageable",
                    "setDurability",
                    "ItemMeta",
                    "getItemInHand",
                    "getItemInMainHand");

    private static final String B05_CORE = "/rpg/core/combat/";

    @Test
    void b05NeverTouchesEquipment() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path source : javaSources()) {
            String path = source.toString().replace('\\', '/');
            // Test sources are excluded: this rule is about what the production code does. A test
            // that constructs an ItemStack to check a listener is not the block touching equipment.
            if (!path.contains(B05_CORE) || path.contains("/test/")) {
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
                        "FR-030: the death penalty is equipment damage, applied by B11 on the"
                                + " strength of CombatDeathEvent. B05 supplies the event and stops"
                                + " there.")
                .isEmpty();
    }

    @Test
    void theDeathEventCarriesWhatB11Needs() {
        // The other half of the rule: not touching equipment is only acceptable because the event
        // carries enough for someone else to act on it.
        CombatFixture fixture = new CombatFixture();
        var victim = fixture.player(5.0, 0.0, 4.0);

        fixture.pipeline.kill(victim, DeathCause.ADMIN);

        assertThat(fixture.deaths).singleElement().satisfies(death -> {
            assertThat(death.playerVictim()).isTrue();
            assertThat(death.victimCharacterId()).isNotNull();
            assertThat(death.shares()).isNotNull();
            assertThat(death.cause()).isNotNull();
        });
    }

    @Test
    void theScanReachesTheCombatSourcesAtAll() throws IOException {
        long combatSources =
                javaSources().stream()
                        .filter(p -> p.toString().replace('\\', '/').contains(B05_CORE))
                        .count();

        assertThat(combatSources).isGreaterThanOrEqualTo(20);
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
