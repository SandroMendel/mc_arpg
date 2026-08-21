package rpg.core.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * There is exactly <b>one</b> place that decides whether damage is allowed (FR-042).
 *
 * <p>A structure test, not a behavioural one. {@code DamagePermissionTest} already proves the rule
 * behaves correctly; what it cannot prove is that nobody added a <em>second</em> place with the same
 * decision. And that is the risk worth guarding: B09 replaces this rule with a per-zone one, and a
 * second copy would leave half the pipeline on the old behaviour - a bug that shows up as "PvP works
 * in some situations".
 *
 * <p>Two independent angles, because either alone is easy to fool: the sources are scanned for a
 * repeated player-versus-player decision, and the installed rule is counted at runtime to show every
 * damage source funnels through it.
 */
class SinglePermissionPointTest {

    private static final String B05_CORE = "/rpg/core/combat/";
    private static final String B05_PLATFORM = "/rpg/platform/combat/";

    /** The shape of the decision: both sides being players compared in one condition. */
    private static final Pattern PVP_DECISION =
            Pattern.compile("(?i)(attackerIsPlayer|isPlayer\\([^)]*\\))\\s*&&\\s*(targetIsPlayer|isPlayer\\([^)]*\\))");

    @Test
    @DisplayName("the player-versus-player decision appears in exactly one file")
    void pvpDecisionLivesInOnePlace() throws IOException {
        List<String> files = new ArrayList<>();
        for (Path source : javaSources()) {
            String path = source.toString().replace('\\', '/');
            if (!isProductionCombatSource(path)) {
                continue;
            }
            String content = Files.readString(source, StandardCharsets.UTF_8);
            if (PVP_DECISION.matcher(content).find()) {
                files.add(path);
            }
        }

        assertThat(files)
                .as(
                        "FR-042: B09 replaces this one line with a per-zone rule. A second copy"
                                + " would leave part of the pipeline on the old behaviour.")
                .hasSize(1);
        assertThat(files.get(0)).endsWith("DamagePermission.java");
    }

    @Test
    @DisplayName("nothing else in the block calls the permission rule")
    void onlyThePipelineAsks() throws IOException {
        List<String> callers = new ArrayList<>();
        for (Path source : javaSources()) {
            String path = source.toString().replace('\\', '/');
            if (!isProductionCombatSource(path) || path.endsWith("DamagePermission.java")) {
                continue;
            }
            String content = Files.readString(source, StandardCharsets.UTF_8);
            if (content.contains("isAllowed(")) {
                callers.add(path);
            }
        }

        // One asker, one rule. A listener that pre-filtered would be a second decision under a
        // different name.
        assertThat(callers).hasSize(1);
        assertThat(callers.get(0)).endsWith("DefaultCombatPipeline.java");
    }

    @Test
    @DisplayName("every damage source funnels through the installed rule")
    void everySourceAsksTheRule() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        // Enough health to survive all three hits: a target that dies on the second one would send
        // the third back with ALREADY_DEAD before it ever reaches the permission check, and the test
        // would look like a missing decision point.
        UUID target = fixture.mob(1_000.0, 0.0, 5.0);

        AtomicInteger asked = new AtomicInteger();
        fixture.pipeline.setPermission(
                (attackerId, attackerIsPlayer, targetId, targetIsPlayer) -> {
                    asked.incrementAndGet();
                    return true;
                });

        fixture.pipeline.meleeAttack(attacker, target);
        int afterMelee = asked.get();

        fixture.clock.advanceMillis(1_000);
        fixture.pipeline.abilityDamage(attacker, target, DamageType.MAGIC, 1.0);
        int afterAbility = asked.get();

        fixture.pipeline.projectileDamage(attacker, target, 10.0);
        int afterProjectile = asked.get();

        assertThat(afterMelee).as("melee asks").isPositive();
        assertThat(afterAbility).as("an ability asks too").isGreaterThan(afterMelee);
        assertThat(afterProjectile).as("and a projectile as well").isGreaterThan(afterAbility);
    }

    @Test
    @DisplayName("a replacement rule reaches every source, not just melee")
    void replacementRuleAppliesEverywhere() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        UUID target = fixture.mob(100.0, 0.0, 5.0);

        // Exactly what B09 will do: swap the rule. If any source kept its own copy of the decision,
        // one of these three would still land.
        fixture.pipeline.setPermission((a, aIsPlayer, t, tIsPlayer) -> false);

        assertThat(fixture.pipeline.meleeAttack(attacker, target).reason())
                .isEqualTo(RejectReason.NOT_PERMITTED);
        fixture.clock.advanceMillis(1_000);
        assertThat(fixture.pipeline.abilityDamage(attacker, target, DamageType.MAGIC, 1.0).reason())
                .isEqualTo(RejectReason.NOT_PERMITTED);
        assertThat(fixture.pipeline.projectileDamage(attacker, target, 10.0).reason())
                .isEqualTo(RejectReason.NOT_PERMITTED);

        assertThat(fixture.health(target)).as("nothing got through").isEqualTo(100.0);
    }

    @Test
    @DisplayName("the scan really covered the block")
    void scanCoversTheBlock() throws IOException {
        long sources =
                javaSources().stream()
                        .map(path -> path.toString().replace('\\', '/'))
                        .filter(SinglePermissionPointTest::isProductionCombatSource)
                        .count();

        assertThat(sources).isGreaterThanOrEqualTo(25);
    }

    @Test
    @DisplayName("the pattern would actually notice a second decision")
    void patternDetectsADuplicate() {
        // Without this the scan could pass because the regex matches nothing at all.
        Matcher matcher =
                PVP_DECISION.matcher("if (attackerIsPlayer && targetIsPlayer) { return false; }");

        assertThat(matcher.find()).isTrue();
    }

    private static boolean isProductionCombatSource(String path) {
        return (path.contains(B05_CORE) || path.contains(B05_PLATFORM)) && !path.contains("/test/");
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
