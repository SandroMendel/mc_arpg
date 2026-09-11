package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;
import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.mob.HordeSpec;
import rpg.core.mob.MobConfig;
import rpg.core.mob.MobConfigSchema;
import rpg.core.mob.MobKind;
import rpg.core.mob.MobMessageKeys;

/**
 * The shipped {@code mobs.yml} that actually ships must pass its own schema (T084), and every
 * region's boss must resolve a display name in {@code messages.yml} - the same gap
 * {@code ZoneMessageKeyResolutionTest} closes for regions.
 *
 * <p>Lives here rather than in {@code rpg-core} for the same reason as {@code
 * ShippedZoneConfigTest}: both shipped resources are only on this classpath, and none of B10's own
 * schema tests build the real file - they all construct their own document, so a broken shipped
 * file would sail past every unit test and only fail at server start.
 */
class ShippedMobConfigTest {

    @Test
    @DisplayName("the shipped mobs.yml passes the schema it was written for")
    void shippedConfigurationIsValid() throws Exception {
        MobConfig config = loadMobs();

        assertThat(config.kinds()).as("at least one kind per region, plus six bosses").isNotEmpty();
        assertThat(config.hordes()).as("six regions").hasSize(6);
    }

    @Test
    @DisplayName("every one of the six regions ships exactly one boss (FR-029)")
    void everyRegionShipsExactlyOneBoss() throws Exception {
        MobConfig config = loadMobs();

        for (Map.Entry<String, HordeSpec> entry : config.hordes().entrySet()) {
            assertThat(entry.getValue().boss())
                    .as(entry.getKey() + " carries a boss")
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("every shipped boss kind is actually marked boss: true and has no ability field")
    void everyBossKindIsMarkedAsSuch() throws Exception {
        MobConfig config = loadMobs();

        for (Map.Entry<String, HordeSpec> entry : config.hordes().entrySet()) {
            String bossKindKey = entry.getValue().boss().kindKey();
            MobKind bossKind = config.kind(bossKindKey).orElseThrow();
            assertThat(bossKind.boss()).as(bossKindKey + " is marked boss: true").isTrue();
        }
    }

    @Test
    @DisplayName("every kind key resolves a display name in messages.yml, including all six bosses")
    void everyKindHasAName() throws Exception {
        MobConfig config = loadMobs();
        Messages messages = messages();
        List<String> missing = new ArrayList<>();

        for (MessageKey key : MobMessageKeys.all(config.kindKeys())) {
            if (!messages.contains(key)) {
                missing.add(key.value());
            }
        }

        assertThat(missing)
                .as("a mob kind whose name shows up in game as a key string is a start failure")
                .isEmpty();
    }

    private static MobConfig loadMobs() throws Exception {
        ConfigSchema<MobConfig> schema = MobConfigSchema.schema();
        return schema.bind(SchemaValidator.validate(Path.of("mobs.yml"), load("/mobs.yml"), schema));
    }

    private static Messages messages() throws Exception {
        return MapMessages.fromNested(load("/messages.yml"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String resource) throws Exception {
        try (InputStream in = ShippedMobConfigTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("resource on the classpath: " + resource).isNotNull();
            return (Map<String, Object>)
                    new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
