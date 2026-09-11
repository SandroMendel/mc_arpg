package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;

/**
 * T066 - the start region: exactly one, and it is where a new character appears (FR-037a, FR-037b,
 * SC-025).
 *
 * <p><b>What this file does not test, and why.</b> FR-037b says a <em>newly created</em> character
 * appears here. Answering "which character is new" needs a record that says they have been seen
 * before, and B09's own persistence - the one that carries the waypoint unlocks and the pending
 * respawn - arrives with US5. B08b infers the same thing the same way, from an absent balance row.
 * So the query below is US4's work and the wiring that calls it belongs to US5; putting it in now
 * would mean guessing at newness, and the plausible guesses are all wrong. A returning player
 * teleported home on every login is worse than a feature that lands one story later.
 */
class StartRegionTest {

    @Test
    @DisplayName("the start point is the respawn point of the region marked as start")
    void startPointComesFromTheMarkedRegion() throws Exception {
        Zones zones = load(ZoneFixture.document());

        assertThat(zones.startPoint())
                .isEqualTo(zones.respawnPointOf("greenfields").orElseThrow());
    }

    @Test
    @DisplayName("moving the mark moves the start point, without a code change (SC-025)")
    void movingTheMarkMovesTheStart() throws Exception {
        Map<String, Object> document = ZoneFixture.document();
        ZoneFixture.zoneIn(document, "greenfields").remove("start-region");
        ZoneFixture.zoneIn(document, "darkforest").put("start-region", Boolean.TRUE);

        Zones zones = load(document);

        assertThat(zones.startPoint()).isEqualTo(zones.respawnPointOf("darkforest").orElseThrow());
    }

    @Test
    @DisplayName("the start point does not depend on the Minecraft world spawn at all")
    void independentOfTheWorldSpawn() throws Exception {
        Zones zones = load(ZoneFixture.document());

        // Nothing in this block reads a world spawn - the value comes from the configured core, and
        // that is the whole point of FR-037b: the most important place in the game should live in a
        // file that is under version control, not in a server setting nobody versions.
        assertThat(zones.startPoint().worldId()).isEqualTo(ZoneFixture.WORLD);
        assertThat(zones.startPoint().y()).isEqualTo(65.0d);
    }

    @Test
    @DisplayName("no start region refuses the configuration (FR-037a)")
    void noneIsRefused() {
        Map<String, Object> document = ZoneFixture.document();
        ZoneFixture.zoneIn(document, "greenfields").remove("start-region");

        assertThatThrownBy(() -> load(document)).hasMessageContaining("exactly one zone");
    }

    @Test
    @DisplayName("two start regions refuse the configuration (FR-037a)")
    void twoAreRefused() {
        Map<String, Object> document = ZoneFixture.document();
        ZoneFixture.zoneIn(document, "darkforest").put("start-region", Boolean.TRUE);

        assertThatThrownBy(() -> load(document)).hasMessageContaining("exactly one zone");
    }

    private static Zones load(Map<String, Object> document) throws Exception {
        ConfigSchema<ZoneConfig> schema = ZoneConfigSchema.schema(ZoneFixture.worlds());
        return new DefaultZones(
                schema.bind(SchemaValidator.validate(Path.of("zones.yml"), document, schema)));
    }
}
