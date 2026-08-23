package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;
import rpg.core.scheduler.WorldPosition;

/** T025 - the query: the region, never the core, and "no zone" as a valid answer (FR-007 to FR-011). */
class DefaultZonesTest {

    private final Zones zones = load();

    @Test
    @DisplayName("zoneAt returns the REGION for a position in the safe core, never the core")
    void safeCorePositionYieldsTheRegion() {
        WorldPosition inTheCore = at(0.5d, 65.0d, 0.5d);

        assertThat(zones.zoneAt(inTheCore)).isPresent();
        assertThat(zones.zoneAt(inTheCore).get().key()).isEqualTo("greenfields");
        assertThat(zones.zoneKeyAt(inTheCore)).isEqualTo("greenfields");
    }

    @Test
    @DisplayName("inSafeCore answers the core separately")
    void safeCoreIsAskedSeparately() {
        assertThat(zones.inSafeCore(at(0.5d, 65.0d, 0.5d))).as("in the core").isTrue();
        assertThat(zones.inSafeCore(at(300.5d, 65.0d, 0.5d))).as("danger zone").isFalse();
        assertThat(zones.inSafeCore(at(700.5d, 65.0d, 700.5d))).as("no zone at all").isFalse();
    }

    @Test
    @DisplayName("a position in no region yields empty, and that is a valid state (FR-007)")
    void noZoneIsValid() {
        WorldPosition wilderness = at(700.5d, 65.0d, 700.5d);

        assertThat(zones.zoneAt(wilderness)).isEmpty();
        assertThat(zones.zoneKeyAt(wilderness)).as("null, not an exception").isNull();
    }

    @Test
    @DisplayName("block containment floors the position: x = 500.9 is still block 500")
    void positionsAreFloored() {
        assertThat(zones.zoneKeyAt(at(500.9d, 65.0d, 0.5d))).isEqualTo("greenfields");
        assertThat(zones.zoneKeyAt(at(501.0d, 65.0d, 0.5d))).isNull();
    }

    @Test
    @DisplayName("the start point is the respawn point of the start region (FR-037b)")
    void startPointComesFromTheStartRegion() {
        assertThat(zones.startPoint()).isEqualTo(at(0.5d, 65.0d, 0.5d));
    }

    @Test
    @DisplayName("death, travel and the start of the game share one coordinate (FR-037d, SC-024)")
    void onePointServesThreePurposes() {
        WorldPosition respawn = zones.respawnPointOf("greenfields").orElseThrow();

        assertThat(respawn).as("death").isEqualTo(zones.startPoint());
        assertThat(zones.byKey("greenfields").orElseThrow().safeCore().orElseThrow().respawnPoint())
                .as("travel destination of this region's crystal")
                .isEqualTo(respawn);
    }

    @Test
    @DisplayName("the fallback point is configured, not derived (FR-034)")
    void fallbackPointIsConfigured() {
        assertThat(zones.fallbackPoint()).isEqualTo(at(0.5d, 64.0d, 0.5d));
    }

    @Test
    @DisplayName("spawn areas come back with key and geometry, and nothing else (FR-053a)")
    void spawnAreasCarryNothingElse() {
        assertThat(zones.spawnAreasOf("greenfields")).hasSize(2);
        assertThat(zones.spawnAreasOf("greenfields").stream().map(SpawnArea::key))
                .containsExactly("greenfields-east", "greenfields-west");
        assertThat(zones.spawnAreasOf("nope")).as("unknown zone, empty list").isEmpty();
    }

    @Test
    @DisplayName("a zone without a safe core has no respawn point")
    void zoneWithoutCoreHasNoRespawnPoint() {
        assertThat(zones.respawnPointOf("nope")).isEmpty();
    }

    @Test
    @DisplayName("a start region without a safe core is refused - it would have no arrival point")
    void startRegionNeedsACore() {
        Map<String, Object> document = ZoneFixture.document();
        Map<String, Object> start = ZoneFixture.zoneIn(document, "greenfields");
        start.remove("safe-core");
        start.remove("crystal");
        start.remove("spawn-areas");

        assertThatThrownBy(() -> new DefaultZones(bind(document)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start region")
                .hasMessageContaining("safe-core");
    }

    private static WorldPosition at(double x, double y, double z) {
        return new WorldPosition(ZoneFixture.WORLD, x, y, z);
    }

    private static Zones load() {
        try {
            return new DefaultZones(bind(ZoneFixture.document()));
        } catch (Exception failed) {
            throw new IllegalStateException(failed);
        }
    }

    private static ZoneConfig bind(Map<String, Object> document) throws Exception {
        ConfigSchema<ZoneConfig> schema = ZoneConfigSchema.schema(ZoneFixture.worlds());
        return schema.bind(SchemaValidator.validate(Path.of("zones.yml"), document, schema));
    }
}
