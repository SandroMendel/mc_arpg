package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;
import rpg.core.progression.ProgressionConfig;
import rpg.core.progression.ProgressionConfigSchema;
import rpg.core.zone.LevelBand;
import rpg.core.zone.WorldResolver;
import rpg.core.zone.Zone;
import rpg.core.zone.ZoneConfig;
import rpg.core.zone.ZoneConfigSchema;

/**
 * T029, T030 - the {@code zones.yml} that actually ships must pass its own schema, and its level
 * bands must cover the experience curve (FR-065, FR-065d).
 *
 * <p>Lives here rather than in {@code rpg-core} because that is where both resources are on the
 * classpath - the dependency direction is {@code plugin -> core}, so a core test cannot reach them.
 * The task list put it in {@code rpg-core}; that was a mis-cut, and this file follows the home the
 * project already established with {@code ShippedProgressionConfigTest} and its siblings.
 *
 * <p>None of B09's schema tests would catch a broken shipped file: they all build their own document,
 * so a default configuration that violates the schema would sail past them and stop the server on
 * first start instead.
 */
class ShippedZoneConfigTest {

    /** The shipped file names one world; a test has no server, so the resolver is fixed. */
    private static final WorldResolver WORLDS =
            WorldResolver.of(Map.of("world", UUID.fromString("00000000-0000-4000-8000-00000000dead")));

    @Test
    @DisplayName("the shipped zones.yml passes the schema it was written for")
    void shippedConfigurationIsValid() throws Exception {
        ZoneConfig config = loadZones();

        assertThat(config.zones()).as("six regions").hasSize(6);
        assertThat(config.zones().stream().map(Zone::key))
                .containsExactly(
                        "greenfields",
                        "dustlands",
                        "safari-plains",
                        "terracotta-canyons",
                        "darkforest",
                        "pale-wilds");
        assertThat(config.startRegion().key()).isEqualTo("greenfields");
        assertThat(config.provisional())
                .as("the coordinates are placeholders until the map exists (FR-065)")
                .isTrue();
        assertThat(config.combatLogoutIsDeath()).as("ADR-030").isTrue();
    }

    @Test
    @DisplayName("every shipped region has a safe core, a crystal and spawn areas for B10")
    void everyRegionIsComplete() throws Exception {
        for (Zone zone : loadZones().zones()) {
            assertThat(zone.safeCore()).as(zone.key() + " safe core").isPresent();
            assertThat(zone.crystal()).as(zone.key() + " crystal").isPresent();
            assertThat(zone.spawnAreas())
                    .as(zone.key() + " spawn areas - B10 needs an anchor (FR-055a)")
                    .isNotEmpty();
            assertThat(zone.pvp()).as(zone.key() + " ships with PvP off (FR-031)").isFalse();
        }
    }

    @Test
    @DisplayName("the level bands cover 1 to the maximum level with no gap and no overlap (FR-065d)")
    void bandsCoverTheWholeCurve() throws Exception {
        int maxLevel = loadProgression().maxLevel();
        List<LevelBand> bands = new ArrayList<>();
        for (Zone zone : loadZones().zones()) {
            bands.add(zone.levelBand());
        }
        bands.sort((a, b) -> Integer.compare(a.min(), b.min()));

        assertThat(bands.get(0).min()).as("the first band starts at level 1").isEqualTo(1);
        assertThat(bands.get(bands.size() - 1).max())
                .as(
                        "the last band ends at the maximum level from progression.yml - extend the"
                                + " curve without adding a region and this is the test that says so")
                .isEqualTo(maxLevel);

        for (int i = 1; i < bands.size(); i++) {
            assertThat(bands.get(i).min())
                    .as("band " + i + " starts exactly one above the previous one - no gap, no overlap")
                    .isEqualTo(bands.get(i - 1).max() + 1);
        }
    }

    @Test
    @DisplayName("no region carries a display name - that belongs to messages.yml (FR-003a)")
    @SuppressWarnings("unchecked")
    void noDisplayNamesInZonesYaml() throws Exception {
        Map<String, Object> document = load("/zones.yml");
        Map<String, Object> zones = (Map<String, Object>) document.get("zones");

        for (Map.Entry<String, Object> entry : zones.entrySet()) {
            Map<String, Object> body = (Map<String, Object>) entry.getValue();
            assertThat(body)
                    .as(
                            entry.getKey()
                                    + ": a zone name is a player text and lives only in"
                                    + " messages.yml, otherwise renaming a region would break every"
                                    + " reference to it")
                    .doesNotContainKeys("name", "display-name", "title");
        }
    }

    private static ZoneConfig loadZones() throws Exception {
        ConfigSchema<ZoneConfig> schema = ZoneConfigSchema.schema(WORLDS);
        return schema.bind(SchemaValidator.validate(Path.of("zones.yml"), load("/zones.yml"), schema));
    }

    private static ProgressionConfig loadProgression() throws Exception {
        ConfigSchema<ProgressionConfig> schema = ProgressionConfigSchema.schema();
        return schema.bind(
                SchemaValidator.validate(
                        Path.of("progression.yml"), load("/progression.yml"), schema));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String resource) throws Exception {
        try (InputStream in = ShippedZoneConfigTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("resource on the classpath: " + resource).isNotNull();
            return (Map<String, Object>)
                    new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
