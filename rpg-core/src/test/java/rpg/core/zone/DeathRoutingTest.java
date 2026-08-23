package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;
import rpg.core.scheduler.WorldPosition;

/**
 * T058, T059, T067 - where a death leads, and the one coordinate that serves three purposes
 * (FR-032 to FR-034, FR-037, FR-037d, SC-010, SC-024).
 */
class DeathRoutingTest {

    private static final UUID HOLDER = UUID.randomUUID();

    /** Placement told by hand - the tracker's job in production. */
    private static final class Placement implements ZonePresence {

        private final Map<UUID, String> zoneOf = new HashMap<>();

        @Override
        public boolean inSafeCore(UUID holderId) {
            return false;
        }

        @Override
        public String zoneKeyOf(UUID holderId) {
            return zoneOf.get(holderId);
        }

        Placement standing(UUID holderId, String zoneKey) {
            zoneOf.put(holderId, zoneKey);
            return this;
        }
    }

    @Test
    @DisplayName("a death in a region leads to that region's core (FR-033, SC-010)")
    void deathLeadsToItsOwnRegion() throws Exception {
        Zones zones = load(ZoneFixture.document());
        RespawnRouting routing =
                new RespawnRouting(new Placement().standing(HOLDER, "terracotta-canyons"), () -> zones);

        assertThat(routing.respawnFor(HOLDER))
                .isEqualTo(zones.respawnPointOf("terracotta-canyons").orElseThrow());
        assertThat(routing.insideOwnRegion(HOLDER)).isTrue();
    }

    @Test
    @DisplayName("every one of the six regions routes to its own core, not to a shared point")
    void eachRegionHasItsOwn() throws Exception {
        Zones zones = load(ZoneFixture.document());

        for (Zone zone : zones.all()) {
            RespawnRouting routing =
                    new RespawnRouting(new Placement().standing(HOLDER, zone.key()), () -> zones);

            assertThat(routing.respawnFor(HOLDER))
                    .as(zone.key())
                    .isEqualTo(zone.safeCore().orElseThrow().respawnPoint());
        }
    }

    @Test
    @DisplayName("a death outside every region leads to the fallback point (FR-034)")
    void deathInTheWildernessUsesTheFallback() throws Exception {
        Zones zones = load(ZoneFixture.document());
        RespawnRouting routing = new RespawnRouting(new Placement(), () -> zones);

        assertThat(routing.respawnFor(HOLDER)).isEqualTo(zones.fallbackPoint());
        assertThat(routing.insideOwnRegion(HOLDER)).isFalse();
    }

    @Test
    @DisplayName("a region that vanished between death and respawn falls back, it does not fail")
    void aVanishedRegionFallsBack() throws Exception {
        Zones zones = load(ZoneFixture.document());
        // The tracker still remembers a region the configuration no longer has - exactly what a
        // reload during a death looks like (FR-037).
        RespawnRouting routing =
                new RespawnRouting(new Placement().standing(HOLDER, "atlantis"), () -> zones);

        assertThat(routing.respawnFor(HOLDER))
                .as("never null, never an exception - a join must not fail over this")
                .isEqualTo(zones.fallbackPoint());
    }

    @Test
    @DisplayName("a region without a safe core falls back too")
    void aRegionWithoutACoreFallsBack() throws Exception {
        Map<String, Object> document = ZoneFixture.document();
        Map<String, Object> coreless = ZoneFixture.zoneIn(document, "pale-wilds");
        coreless.remove("safe-core");
        coreless.remove("crystal"); // a crystal needs a core (FR-051c)
        coreless.remove("spawn-areas"); // they were checked against the core
        Zones zones = load(document);
        RespawnRouting routing =
                new RespawnRouting(new Placement().standing(HOLDER, "pale-wilds"), () -> zones);

        assertThat(routing.respawnFor(HOLDER)).isEqualTo(zones.fallbackPoint());
    }

    @Test
    @DisplayName("start, death and travel share one coordinate per region (FR-037d, SC-024)")
    void onePointServesThreePurposes() throws Exception {
        Zones zones = load(ZoneFixture.document());
        RespawnRouting routing =
                new RespawnRouting(new Placement().standing(HOLDER, "greenfields"), () -> zones);

        WorldPosition death = routing.respawnFor(HOLDER);
        WorldPosition start = zones.startPoint();
        WorldPosition travel =
                zones.crystalByKey("greenfields-crystal").orElseThrow().core().respawnPoint();

        assertThat(death).as("death").isEqualTo(start);
        assertThat(travel).as("travel destination of this region's crystal").isEqualTo(start);
    }

    private static Zones load(Map<String, Object> document) throws Exception {
        ConfigSchema<ZoneConfig> schema = ZoneConfigSchema.schema(ZoneFixture.worlds());
        return new DefaultZones(
                schema.bind(SchemaValidator.validate(Path.of("zones.yml"), document, schema)));
    }
}
