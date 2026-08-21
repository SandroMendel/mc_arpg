package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;
import rpg.core.progression.ProgressionConfig;
import rpg.core.progression.ProgressionConfigSchema;
import rpg.core.stats.Attribute;

/**
 * The {@code progression.yml} that actually ships must pass its own schema.
 *
 * <p>Lives here rather than in {@code rpg-core} because that is where the resource is on the
 * classpath. None of the schema tests in B06 would catch this: they all build their own map, so a
 * default configuration that violates the schema would sail past them and stop the server on first
 * start instead.
 */
class ShippedProgressionConfigTest {

    @Test
    @DisplayName("the shipped progression.yml passes the schema it was written for")
    @SuppressWarnings("unchecked")
    void shippedConfigurationIsValid() throws Exception {
        Map<String, Object> document = load("/progression.yml");

        ConfigSchema<ProgressionConfig> schema = ProgressionConfigSchema.schema();
        ProgressionConfig config =
                schema.bind(SchemaValidator.validate(Path.of("progression.yml"), document, schema));

        assertThat(config.maxLevel()).as("the curve reaches level 60").isEqualTo(60);
        assertThat(config.mobXpDefault()).isPositive();
        assertThat(config.mobXpFor("ZOMBIE")).isPresent();

        // The three that must stay at zero (FR-022b): movement speed over sixty levels is
        // unplayable, attack speed evaporates against vanilla invulnerability, and ability cooldown
        // is already sold for coins in B08.
        assertThat(config.growth().perLevel(Attribute.MOVEMENT_SPEED)).isZero();
        assertThat(config.growth().perLevel(Attribute.ATTACK_SPEED)).isZero();
        assertThat(config.growth().perLevel(Attribute.ABILITY_COOLDOWN)).isZero();

        // And the five that must grow, or a level-up would do nothing at all.
        assertThat(config.growth().perLevel(Attribute.HEALTH)).isPositive();
        assertThat(config.growth().perLevel(Attribute.MANA)).isPositive();
        assertThat(config.growth().perLevel(Attribute.DEFENSE)).isPositive();
        assertThat(config.growth().perLevel(Attribute.PHYSICAL_DAMAGE)).isPositive();
        assertThat(config.growth().perLevel(Attribute.MAGIC_DAMAGE)).isPositive();
    }

    @Test
    @DisplayName("the shipped curve is strictly increasing across all 59 steps")
    void shippedCurveRises() throws Exception {
        Map<String, Object> document = load("/progression.yml");
        ConfigSchema<ProgressionConfig> schema = ProgressionConfigSchema.schema();
        ProgressionConfig config =
                schema.bind(SchemaValidator.validate(Path.of("progression.yml"), document, schema));

        long previous = 0L;
        for (int level = 2; level <= config.maxLevel(); level++) {
            long threshold = config.curve().thresholdFor(level);
            assertThat(threshold)
                    .as("level " + level + " must cost more than level " + (level - 1))
                    .isGreaterThan(previous);
            previous = threshold;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String resource) throws Exception {
        try (InputStream stream = ShippedProgressionConfigTest.class.getResourceAsStream(resource)) {
            assertThat(stream).as(resource + " must be on the classpath").isNotNull();
            return (Map<String, Object>)
                    new Yaml().load(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
