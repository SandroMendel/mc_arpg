package rpg.core.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T012: every invariant of {@code combat.yml}, checked through the message (Principle V).
 *
 * <p>The message is the product. An operator editing balancing numbers who gets
 * "IllegalArgumentException" learns that something is wrong; one who gets
 * "combat.attribution.max-attackers must be between 1 and 64" learns what to change.
 */
class CombatConfigTest {

    private static CombatConfig with(
            Duration combatTimeout,
            int maxAttackers,
            Duration attributionTimeout,
            Duration aggregation,
            double knockback) {
        CombatConfig shipped = CombatConfig.defaults();
        return new CombatConfig(
                combatTimeout,
                maxAttackers,
                attributionTimeout,
                aggregation,
                knockback,
                shipped.fallDamage(),
                shipped.environmentDamage(),
                shipped.mobStats(),
                shipped.defaultMobStats());
    }

    @Test
    @DisplayName("the shipped values are themselves valid")
    void shippedValuesAreValid() {
        CombatConfig config = CombatConfig.defaults();

        assertThat(config.combatTimeout()).isEqualTo(Duration.ofSeconds(8));
        assertThat(config.maxAttackers()).isEqualTo(16);
        assertThat(config.attributionTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.aggregationWindow()).isEqualTo(Duration.ofMillis(500));
        assertThat(config.environmentDamageOf(EnvironmentSource.LAVA)).isEqualTo(8.0);
    }

    @Test
    @DisplayName("the combat timeout must be positive")
    void combatTimeout() {
        assertThatThrownBy(
                        () ->
                                with(
                                        Duration.ZERO,
                                        16,
                                        Duration.ofSeconds(30),
                                        Duration.ofMillis(500),
                                        0.4))
                .hasMessageContaining("combat-timeout-seconds");
    }

    @Test
    @DisplayName("max-attackers is bounded, and the message says why")
    void maxAttackersBounded() {
        assertThatThrownBy(
                        () ->
                                with(
                                        Duration.ofSeconds(8),
                                        0,
                                        Duration.ofSeconds(30),
                                        Duration.ofMillis(500),
                                        0.4))
                .hasMessageContaining("max-attackers");

        assertThatThrownBy(
                        () ->
                                with(
                                        Duration.ofSeconds(8),
                                        1000,
                                        Duration.ofSeconds(30),
                                        Duration.ofMillis(500),
                                        0.4))
                .hasMessageContaining("max-attackers")
                .hasMessageContaining("800 mobs"); // the reason, not just the bound
    }

    @Test
    @DisplayName("the aggregation window has an upper bound")
    void aggregationWindowBounded() {
        assertThatThrownBy(
                        () ->
                                with(
                                        Duration.ofSeconds(8),
                                        16,
                                        Duration.ofSeconds(30),
                                        Duration.ofSeconds(30),
                                        0.4))
                .hasMessageContaining("aggregation-window-millis");
    }

    @Test
    @DisplayName("knockback strength cannot be negative")
    void knockbackNotNegative() {
        assertThatThrownBy(
                        () ->
                                with(
                                        Duration.ofSeconds(8),
                                        16,
                                        Duration.ofSeconds(30),
                                        Duration.ofMillis(500),
                                        -1.0))
                .hasMessageContaining("knockback-strength");
    }

    @Test
    @DisplayName("a missing environment hazard is named - ADR-003 wants a decision per source")
    void missingHazardIsNamed() {
        CombatConfig shipped = CombatConfig.defaults();
        EnumMap<EnvironmentSource, Double> incomplete =
                new EnumMap<>(shipped.environmentDamage());
        incomplete.remove(EnvironmentSource.LAVA);

        assertThatThrownBy(
                        () ->
                                new CombatConfig(
                                        shipped.combatTimeout(),
                                        shipped.maxAttackers(),
                                        shipped.attributionTimeout(),
                                        shipped.aggregationWindow(),
                                        shipped.knockbackStrength(),
                                        shipped.fallDamage(),
                                        incomplete,
                                        shipped.mobStats(),
                                        shipped.defaultMobStats()))
                .hasMessageContaining("lava")
                .hasMessageContaining("ADR-003");
    }

    @Test
    @DisplayName("the fall needs no flat amount - it is computed from the height")
    void fallIsComputed() {
        assertThat(EnvironmentSource.FALL.isComputed()).isTrue();
        assertThat(CombatConfig.defaults().environmentDamage())
                .doesNotContainKey(EnvironmentSource.FALL);
    }

    @Test
    @DisplayName("a negative hazard amount is refused")
    void negativeHazard() {
        CombatConfig shipped = CombatConfig.defaults();
        EnumMap<EnvironmentSource, Double> broken = new EnumMap<>(shipped.environmentDamage());
        broken.put(EnvironmentSource.LAVA, -5.0);

        assertThatThrownBy(
                        () ->
                                new CombatConfig(
                                        shipped.combatTimeout(),
                                        shipped.maxAttackers(),
                                        shipped.attributionTimeout(),
                                        shipped.aggregationWindow(),
                                        shipped.knockbackStrength(),
                                        shipped.fallDamage(),
                                        broken,
                                        shipped.mobStats(),
                                        shipped.defaultMobStats()))
                .hasMessageContaining("lava");
    }

    @Test
    @DisplayName("mob stats are refused if they are nonsensical")
    void mobStatsInvariants() {
        assertThatThrownBy(() -> new CombatConfig.MobStats(0.0, 0.0, 5.0))
                .hasMessageContaining("health");
        assertThatThrownBy(() -> new CombatConfig.MobStats(50.0, -1.0, 5.0))
                .hasMessageContaining("defense");
        assertThatThrownBy(() -> new CombatConfig.MobStats(50.0, 0.0, Double.NaN))
                .hasMessageContaining("physical-damage");
    }

    @Test
    @DisplayName("an unknown creature type falls back to the default set")
    void unknownMobFallsBack() {
        CombatConfig config = CombatConfig.defaults();

        assertThat(config.mobStatsOf("ZOMBIE").health()).isEqualTo(80.0);
        assertThat(config.mobStatsOf("SOMETHING_NEW")).isEqualTo(config.defaultMobStats());
    }

    @Test
    @DisplayName("changing only numbers is accepted - balancing needs no code change")
    void rebalancingNeedsNoCode() {
        CombatConfig shipped = CombatConfig.defaults();
        Map<String, CombatConfig.MobStats> tougher =
                Map.of("ZOMBIE", new CombatConfig.MobStats(500.0, 50.0, 40.0));

        CombatConfig rebalanced =
                new CombatConfig(
                        Duration.ofSeconds(20),
                        32,
                        Duration.ofSeconds(60),
                        Duration.ofMillis(250),
                        1.0,
                        new FallDamageConfig(1.0, 10.0, 500.0),
                        shipped.environmentDamage(),
                        tougher,
                        shipped.defaultMobStats());

        assertThat(rebalanced.mobStatsOf("ZOMBIE").health()).isEqualTo(500.0);
        assertThat(rebalanced.maxAttackers()).isEqualTo(32);
    }
}
