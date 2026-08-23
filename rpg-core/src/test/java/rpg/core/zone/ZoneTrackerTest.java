package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;
import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;
import rpg.core.scheduler.WorldPosition;

/**
 * T033, T034, T036 - one event per actual change, and the safe core keeps its own (FR-015 to FR-018,
 * SC-005, SC-006).
 */
class ZoneTrackerTest {

    private static final UUID CHARACTER = UUID.randomUUID();

    private final List<Object> published = new ArrayList<>();
    private Zones zones;
    private ZoneTracker tracker;

    @BeforeEach
    void setUp() throws Exception {
        zones = load(ZoneFixture.document());
        EventBus bus = new DefaultEventBus(Logger.getLogger("zone-tracker-test"));
        bus.subscribe(ZoneChangedEvent.class, published::add);
        bus.subscribe(SafeAreaCrossedEvent.class, published::add);
        tracker = new ZoneTracker(() -> zones, bus, Logger.getLogger("zone-tracker-test"));
    }

    @Test
    @DisplayName("the first sighting announces the region, once")
    void firstSightingAnnouncesTheRegion() {
        tracker.evaluate(CHARACTER, at(300.5d, 65.0d, 0.5d));

        assertThat(zoneChanges()).hasSize(1);
        assertThat(zoneChanges().get(0).from()).isEmpty();
        assertThat(zoneChanges().get(0).to()).contains("greenfields");
        assertThat(coreCrossings()).as("the danger zone is not a core crossing").isEmpty();
    }

    @Test
    @DisplayName("crossing a border fires exactly one zone change (SC-005)")
    void crossingABorderFiresExactlyOne() {
        tracker.evaluate(CHARACTER, at(300.5d, 65.0d, 0.5d)); // greenfields
        published.clear();

        tracker.evaluate(CHARACTER, at(1200.5d, 65.0d, 0.5d)); // dustlands

        assertThat(zoneChanges()).hasSize(1);
        assertThat(zoneChanges().get(0).from()).contains("greenfields");
        assertThat(zoneChanges().get(0).to()).contains("dustlands");
    }

    @Test
    @DisplayName("standing still publishes nothing at all (FR-018)")
    void standingStillPublishesNothing() {
        tracker.evaluate(CHARACTER, at(300.5d, 65.0d, 0.5d));
        published.clear();

        tracker.evaluate(CHARACTER, at(300.5d, 65.0d, 0.5d));
        tracker.evaluate(CHARACTER, at(301.5d, 65.0d, 1.5d));

        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("leaving the core fires a core event and NO zone change (FR-016, SC-006)")
    void leavingTheCoreIsNotAZoneChange() {
        tracker.evaluate(CHARACTER, at(0.5d, 65.0d, 0.5d)); // in the core
        published.clear();

        tracker.evaluate(CHARACTER, at(300.5d, 65.0d, 0.5d)); // danger zone, same region

        assertThat(zoneChanges()).as("same region, so no zone change").isEmpty();
        assertThat(coreCrossings()).hasSize(1);
        assertThat(coreCrossings().get(0).entered()).isFalse();
        assertThat(coreCrossings().get(0).zoneKey()).isEqualTo("greenfields");
    }

    @Test
    @DisplayName("entering a core fires the core event, entering its region fires both")
    void enteringACoreFromAnotherRegionFiresBoth() {
        tracker.evaluate(CHARACTER, at(1200.5d, 65.0d, 0.5d)); // dustlands danger zone
        published.clear();

        tracker.evaluate(CHARACTER, at(0.5d, 65.0d, 0.5d)); // greenfields core

        assertThat(zoneChanges()).hasSize(1);
        assertThat(zoneChanges().get(0).to()).contains("greenfields");
        assertThat(coreCrossings()).hasSize(1);
        assertThat(coreCrossings().get(0).entered()).isTrue();
        assertThat(coreCrossings().get(0).zoneKey()).isEqualTo("greenfields");
    }

    @Test
    @DisplayName("walking into the wilderness is a zone change with an empty destination (FR-007)")
    void walkingIntoTheWilderness() {
        tracker.evaluate(CHARACTER, at(300.5d, 65.0d, 0.5d));
        published.clear();

        tracker.evaluate(CHARACTER, at(700.5d, 65.0d, 700.5d));

        assertThat(zoneChanges()).hasSize(1);
        assertThat(zoneChanges().get(0).from()).contains("greenfields");
        assertThat(zoneChanges().get(0).to()).isEmpty();
    }

    @Test
    @DisplayName("a teleport is not special - the change fires the same way (FR-017, SC-005)")
    void teleportIsNotSpecial() {
        tracker.evaluate(CHARACTER, at(300.5d, 65.0d, 0.5d));
        published.clear();

        // No intermediate positions: exactly what a teleport looks like from here.
        tracker.evaluate(CHARACTER, at(3000.5d, 65.0d, 1500.5d)); // pale-wilds core

        assertThat(zoneChanges()).hasSize(1);
        assertThat(zoneChanges().get(0).to()).contains("pale-wilds");
        assertThat(coreCrossings()).hasSize(1);
    }

    @Test
    @DisplayName("a character that left is forgotten, and comes back as a first sighting")
    void leavingAndReturning() {
        tracker.evaluate(CHARACTER, at(300.5d, 65.0d, 0.5d));
        tracker.forget(CHARACTER);
        published.clear();

        assertThat(tracker.tracked()).isZero();
        tracker.evaluate(CHARACTER, at(300.5d, 65.0d, 0.5d));

        assertThat(zoneChanges()).hasSize(1);
        assertThat(zoneChanges().get(0).from()).as("a fresh sighting has no previous side").isEmpty();
    }

    @Test
    @DisplayName("a reload re-evaluates everyone in one pass, and only real changes fire (FR-014)")
    void reloadReevaluatesEveryoneOnce() throws Exception {
        UUID other = UUID.randomUUID();
        tracker.evaluate(CHARACTER, at(300.5d, 65.0d, 0.5d)); // greenfields
        tracker.evaluate(other, at(1200.5d, 65.0d, 0.5d)); // dustlands
        published.clear();

        // The greenfields shrink so the first character ends up outside every region; the dustlands
        // are untouched, so the second must notice nothing.
        Map<String, Object> changed = ZoneFixture.document();
        Map<String, Object> shrunk = ZoneFixture.zoneIn(changed, "greenfields");
        shrunk.put("area", ZoneFixture.area(ZoneFixture.box(-100, -100, 100, 100)));
        // The spawn areas sat at 120..300, so shrinking the region leaves them outside it - which
        // the schema refuses, correctly. They are irrelevant to this test.
        shrunk.remove("spawn-areas");
        zones = load(changed);

        tracker.reevaluateAll(
                List.of(
                        new ZoneTracker.Presence(CHARACTER, at(300.5d, 65.0d, 0.5d)),
                        new ZoneTracker.Presence(other, at(1200.5d, 65.0d, 0.5d))));

        assertThat(zoneChanges()).as("only the one that actually moved out").hasSize(1);
        assertThat(zoneChanges().get(0).characterId()).isEqualTo(CHARACTER);
        assertThat(zoneChanges().get(0).to()).isEmpty();
    }

    @Test
    @DisplayName("a fault while evaluating is contained, not propagated (FR-064)")
    void faultsAreContained() {
        ZoneTracker broken =
                new ZoneTracker(
                        () -> {
                            throw new IllegalStateException("index unavailable");
                        },
                        new DefaultEventBus(Logger.getLogger("zone-tracker-test")),
                        Logger.getLogger("zone-tracker-test"));

        // No exception escapes: a player must not be left in an unclear state by a zone lookup.
        broken.evaluate(CHARACTER, at(0.5d, 65.0d, 0.5d));

        assertThat(broken.lastKnownZoneOf(CHARACTER))
                .as("nothing was recorded, so the next evaluation tries again")
                .isNull();
    }

    private List<ZoneChangedEvent> zoneChanges() {
        return published.stream().filter(ZoneChangedEvent.class::isInstance).map(ZoneChangedEvent.class::cast).toList();
    }

    private List<SafeAreaCrossedEvent> coreCrossings() {
        return published.stream()
                .filter(SafeAreaCrossedEvent.class::isInstance)
                .map(SafeAreaCrossedEvent.class::cast)
                .toList();
    }

    private static WorldPosition at(double x, double y, double z) {
        return new WorldPosition(ZoneFixture.WORLD, x, y, z);
    }

    private static Zones load(Map<String, Object> document) throws Exception {
        ConfigSchema<ZoneConfig> schema = ZoneConfigSchema.schema(ZoneFixture.worlds());
        return new DefaultZones(
                schema.bind(SchemaValidator.validate(Path.of("zones.yml"), document, schema)));
    }
}
