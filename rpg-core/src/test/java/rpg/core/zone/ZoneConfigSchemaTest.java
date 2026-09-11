package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;

/**
 * T020 - the schema of {@code zones.yml}: one case per start refusal (FR-013, Principle V).
 *
 * <p>Every failing case asserts the <b>message</b>. An operator who mistypes a coordinate has to
 * learn which one; "invalid configuration" sends them through the whole file.
 *
 * <p>Every run goes through {@code SchemaValidator} and then the binder - the loader's actual path.
 * Testing only the binder would miss the "field is missing" case, which is the one an operator hits
 * most often.
 *
 * <p><b>One refusal from the task list is missing here, and on purpose.</b> A duplicate <em>zone</em>
 * key cannot be built: {@code zones} is a YAML mapping, so the parser guarantees distinct keys before
 * anything here runs. Uniqueness is enforced by the format rather than by a check, and writing a test
 * for an unreachable state would only pretend otherwise. Duplicate <em>crystal</em> and
 * <em>spawn area</em> keys are reachable - both are covered below.
 */
class ZoneConfigSchemaTest {

    private static final Path SOURCE = Path.of("zones.yml");

    @Test
    @DisplayName("the shipped configuration loads: six regions, bands 1-10 to 51-60")
    void shippedConfigurationLoads() throws Exception {
        ZoneConfig config = load(ZoneFixture.document());

        assertThat(config.zones()).hasSize(6);
        assertThat(config.provisional()).as("placeholder coordinates").isTrue();
        assertThat(config.warningCooldown().toSeconds()).isEqualTo(30L);
        assertThat(config.combatLogoutIsDeath()).isTrue();
        assertThat(config.startRegion().key()).isEqualTo("greenfields");
        assertThat(config.zones().stream().map(Zone::key))
                .containsExactly(
                        "greenfields",
                        "dustlands",
                        "safari-plains",
                        "terracotta-canyons",
                        "darkforest",
                        "pale-wilds");
        assertThat(config.zones().get(0).levelBand()).isEqualTo(new LevelBand(1, 10));
        assertThat(config.zones().get(5).levelBand()).isEqualTo(new LevelBand(51, 60));
        assertThat(config.zones().get(0).spawnAreas()).hasSize(2);
        assertThat(config.zones().get(0).crystal()).isPresent();
        assertThat(config.zones().get(0).crystal().get().price()).isEqualTo(25L);
    }

    // ------------------------------------------------------------------ the refusals

    @Test
    @DisplayName("1: an unknown world name refuses the start (FR-002a)")
    void unknownWorld() {
        Map<String, Object> document = ZoneFixture.document();
        ZoneFixture.zoneIn(document, "dustlands").put("world", "atlantis");

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("zones.dustlands.world")
                .hasMessageContaining("atlantis")
                .hasMessageContaining("FR-002a");
    }

    @Test
    @DisplayName("2: overlapping regions in the same world refuse the start (FR-012)")
    void overlappingRegions() {
        Map<String, Object> document = ZoneFixture.document();
        // Stretch the greenfields east until it reaches into the dustlands. Stretching rather than
        // sliding on purpose: a slid region would leave its own safe core outside itself, and the
        // core check would fire first - a test that passes for the wrong reason.
        ZoneFixture.zoneIn(document, "greenfields")
                .put("area", ZoneFixture.area(ZoneFixture.box(-500, -500, 1450, 500)));

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("overlap")
                .hasMessageContaining("greenfields")
                .hasMessageContaining("dustlands");
    }

    @Test
    @DisplayName("3: a safe core outside its own zone refuses the start (FR-009)")
    void safeCoreOutsideItsZone() {
        Map<String, Object> document = ZoneFixture.document();
        @SuppressWarnings("unchecked")
        Map<String, Object> core =
                (Map<String, Object>) ZoneFixture.zoneIn(document, "darkforest").get("safe-core");
        core.put("area", ZoneFixture.area(ZoneFixture.box(9000, 9000, 9100, 9100)));

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("zones.darkforest.safe-core.area")
                .hasMessageContaining("inside its zone");
    }

    @Test
    @DisplayName("4a: a spawn area outside its zone refuses the start (FR-055)")
    void spawnAreaOutsideItsZone() {
        Map<String, Object> document = ZoneFixture.document();
        List<Object> areas = spawnAreasOf(document, "greenfields");
        areas.set(0, ZoneFixture.spawnArea("stray", 9000, 9000, 9100, 9100));

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("zones.greenfields.spawn-areas[0].area")
                .hasMessageContaining("inside its own zone");
    }

    @Test
    @DisplayName("4b: a spawn area reaching into the safe core refuses the start (FR-055)")
    void spawnAreaInsideTheCore() {
        Map<String, Object> document = ZoneFixture.document();
        List<Object> areas = spawnAreasOf(document, "greenfields");
        areas.set(0, ZoneFixture.spawnArea("in-the-core", -30, -30, 30, 30));

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("safe core")
                .hasMessageContaining("nothing spawns there");
    }

    @Test
    @DisplayName("5: two spawn areas with the same key in one zone refuse the start (FR-055)")
    void duplicateSpawnAreaKey() {
        Map<String, Object> document = ZoneFixture.document();
        List<Object> areas = spawnAreasOf(document, "greenfields");
        areas.set(1, ZoneFixture.spawnArea("greenfields-east", -300, -80, -120, 80));

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("greenfields-east")
                .hasMessageContaining("twice");
    }

    @Test
    @DisplayName("6: a crystal in a zone without a safe core refuses the start (FR-051c)")
    void crystalWithoutSafeCore() {
        Map<String, Object> document = ZoneFixture.document();
        Map<String, Object> zone = ZoneFixture.zoneIn(document, "pale-wilds");
        zone.remove("safe-core");
        zone.remove("spawn-areas"); // they were checked against the core

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("zones.pale-wilds.crystal")
                .hasMessageContaining("needs a safe-core")
                .hasMessageContaining("FR-051c");
    }

    @Test
    @DisplayName("7: a crystal trigger area outside its own zone refuses the start (FR-051d)")
    void crystalTriggerAreaOutsideItsZone() {
        Map<String, Object> document = ZoneFixture.document();
        @SuppressWarnings("unchecked")
        Map<String, Object> crystal =
                (Map<String, Object>) ZoneFixture.zoneIn(document, "darkforest").get("crystal");
        crystal.put("trigger-area", ZoneFixture.area(ZoneFixture.box(-2, 64, -2, 2, 67, 2)));

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("zones.darkforest.crystal.trigger-area")
                .hasMessageContaining("inside its own zone");
    }

    @Test
    @DisplayName("8: two crystals sharing a key refuse the start - unlocks reference it (FR-051d)")
    void duplicateCrystalKey() {
        Map<String, Object> document = ZoneFixture.document();
        @SuppressWarnings("unchecked")
        Map<String, Object> crystal =
                (Map<String, Object>) ZoneFixture.zoneIn(document, "dustlands").get("crystal");
        crystal.put("key", "greenfields-crystal");

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("greenfields-crystal")
                .hasMessageContaining("share unlocks");
    }

    @Test
    @DisplayName("9a: no start region refuses the start (FR-037a)")
    void noStartRegion() {
        Map<String, Object> document = ZoneFixture.document();
        ZoneFixture.zoneIn(document, "greenfields").remove("start-region");

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("exactly one zone must carry start-region")
                .hasMessageContaining("none does");
    }

    @Test
    @DisplayName("9b: two start regions refuse the start (FR-037a)")
    void twoStartRegions() {
        Map<String, Object> document = ZoneFixture.document();
        ZoneFixture.zoneIn(document, "darkforest").put("start-region", Boolean.TRUE);

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("exactly one zone must carry start-region")
                .hasMessageContaining("darkforest");
    }

    @Test
    @DisplayName("10: a missing required field names itself (FR-013)")
    void missingFieldNamesItself() {
        Map<String, Object> document = ZoneFixture.document();
        document.remove("fallback-point");

        assertThatThrownBy(() -> load(document)).hasMessageContaining("fallback-point");
    }

    // ------------------------------------------------------------------ other guards

    @Test
    @DisplayName("min-y without max-y is refused rather than guessed (FR-004)")
    void halfVerticalBoundsAreRefused() {
        Map<String, Object> document = ZoneFixture.document();
        Map<String, Object> half = ZoneFixture.box(-500, -500, 500, 500);
        half.put("min-y", 60);
        ZoneFixture.zoneIn(document, "greenfields").put("area", ZoneFixture.area(half));

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("min-y and max-y come as a pair");
    }

    @Test
    @DisplayName("a respawn point outside its own core is refused")
    void respawnPointOutsideItsCore() {
        Map<String, Object> document = ZoneFixture.document();
        @SuppressWarnings("unchecked")
        Map<String, Object> core =
                (Map<String, Object>) ZoneFixture.zoneIn(document, "greenfields").get("safe-core");
        core.put("respawn-point", ZoneFixture.point(400.5d, 65.0d, 400.5d));

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("respawn-point")
                .hasMessageContaining("inside the core");
    }

    @Test
    @DisplayName("combat-logout accepts death and none, and nothing else (FR-042)")
    void combatLogoutModes() throws Exception {
        Map<String, Object> off = ZoneFixture.document();
        off.put("combat-logout", "none");
        assertThat(load(off).combatLogoutIsDeath()).isFalse();

        Map<String, Object> nonsense = ZoneFixture.document();
        nonsense.put("combat-logout", "maybe");
        assertThatThrownBy(() -> load(nonsense))
                .hasMessageContaining("combat-logout must be 'death' or 'none'");
    }

    @Test
    @DisplayName("an empty zones section is refused - a world with no region is not a world")
    void emptyZonesSection() {
        Map<String, Object> document = ZoneFixture.document();
        document.put("zones", new LinkedHashMap<String, Object>());

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("at least one region is required");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> spawnAreasOf(Map<String, Object> document, String zoneKey) {
        return (List<Object>) ZoneFixture.zoneIn(document, zoneKey).get("spawn-areas");
    }

    private static ZoneConfig load(Map<String, Object> document) throws Exception {
        ConfigSchema<ZoneConfig> schema = ZoneConfigSchema.schema(ZoneFixture.worlds());
        return schema.bind(SchemaValidator.validate(SOURCE, document, schema));
    }
}
