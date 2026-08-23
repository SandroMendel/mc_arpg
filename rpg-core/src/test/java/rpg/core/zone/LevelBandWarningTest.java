package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;
import rpg.core.message.MessageKey;

/**
 * T046, T047, T048 - the warning under the level band, and the absence of any gate (FR-021 to
 * FR-025, SC-007).
 */
class LevelBandWarningTest {

    private static final UUID CHARACTER = UUID.randomUUID();

    /** A clock the test moves by hand - the repeat block is timestamp-based, not scheduled. */
    private static final class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-08-23T12:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private record Sent(UUID characterId, MessageKey key, Map<String, String> placeholders) {}

    private final List<Sent> sent = new ArrayList<>();
    private final MovableClock clock = new MovableClock();
    private int level = 7;
    private LevelBandGuard guard;

    @BeforeEach
    void setUp() throws Exception {
        Zones zones = load();
        guard =
                new LevelBandGuard(
                        () -> zones,
                        characterId -> OptionalInt.of(level),
                        (characterId, key, placeholders) ->
                                sent.add(new Sent(characterId, key, placeholders)),
                        clock,
                        () -> Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("below the band: exactly one warning, naming the band (FR-021, SC-007)")
    void belowTheBandWarnsOnce() {
        level = 7;

        guard.onZoneChanged(entering("dustlands"));

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).key()).isEqualTo(ZoneMessageKeys.TOO_DANGEROUS);
        assertThat(sent.get(0).placeholders()).containsEntry("min", "11").containsEntry("max", "20");
    }

    @Test
    @DisplayName("inside the band: silence")
    void insideTheBandIsSilent() {
        level = 15;

        guard.onZoneChanged(entering("dustlands"));

        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("exactly on the lower bound: silence - the band includes it")
    void onTheLowerBoundIsSilent() {
        level = 11;

        guard.onZoneChanged(entering("dustlands"));

        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("above the band: silence - only the lower bound gates anything (FR-023)")
    void aboveTheBandIsSilent() {
        level = 60;

        guard.onZoneChanged(entering("greenfields"));

        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("walking the same border again is not warned again (FR-024)")
    void theSameBorderDoesNotRepeat() {
        level = 7;

        guard.onZoneChanged(entering("dustlands"));
        guard.onZoneChanged(leaving("dustlands"));
        guard.onZoneChanged(entering("dustlands"));
        guard.onZoneChanged(entering("dustlands"));

        assertThat(sent).as("once, however often the line is crossed").hasSize(1);
    }

    @Test
    @DisplayName("after the cooldown the same border warns again")
    void afterTheCooldownItWarnsAgain() {
        level = 7;
        guard.onZoneChanged(entering("dustlands"));

        clock.advance(Duration.ofSeconds(31));
        guard.onZoneChanged(entering("dustlands"));

        assertThat(sent).hasSize(2);
    }

    @Test
    @DisplayName("a different region warns immediately - the block is per border")
    void aDifferentRegionWarnsImmediately() {
        level = 7;

        guard.onZoneChanged(entering("dustlands"));
        guard.onZoneChanged(entering("pale-wilds"));

        assertThat(sent).hasSize(2);
        assertThat(sent.get(1).placeholders()).containsEntry("min", "51");
    }

    @Test
    @DisplayName("leaving a region warns about nothing")
    void leavingWarnsAboutNothing() {
        level = 7;

        guard.onZoneChanged(leaving("dustlands"));

        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("a character without a level is not warned - there is no situation yet")
    void withoutALevelNothingIsSent() throws Exception {
        Zones zones = load();
        LevelBandGuard noLevel =
                new LevelBandGuard(
                        () -> zones,
                        characterId -> OptionalInt.empty(),
                        (characterId, key, placeholders) ->
                                sent.add(new Sent(characterId, key, placeholders)),
                        clock,
                        () -> Duration.ofSeconds(30));

        noLevel.onZoneChanged(entering("dustlands"));

        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("the guard never blocks: it has no way to (FR-022)")
    void theGuardCannotBlock() {
        // A source-level fact expressed as a behavioural one: the only thing the guard can do is
        // hand a message to the warner. It takes no event to cancel, returns nothing, and holds no
        // reference to anything that could move a player. If it ever grows one, this test's name is
        // the place somebody has to argue with.
        level = 1;

        guard.onZoneChanged(entering("pale-wilds"));

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).characterId()).isEqualTo(CHARACTER);
    }

    @Test
    @DisplayName("forgetting a character clears its repeat block, so the map stays bounded")
    void forgettingClearsTheRepeatBlock() {
        level = 7;
        guard.onZoneChanged(entering("dustlands"));
        assertThat(sent).hasSize(1);

        // What the session end does: without it the block would hold entries for every character
        // that ever logged in, for the whole uptime of the server.
        guard.forget(CHARACTER);
        guard.onZoneChanged(entering("dustlands"));

        assertThat(sent).as("a returning character is warned again").hasSize(2);
    }

    @Test
    @DisplayName("forgetting one character leaves another's block alone")
    void forgettingIsPerCharacter() {
        UUID other = UUID.randomUUID();
        level = 7;
        guard.onZoneChanged(entering("dustlands"));
        guard.onZoneChanged(
                new ZoneChangedEvent(other, Optional.empty(), Optional.of("dustlands")));
        assertThat(sent).hasSize(2);

        guard.forget(CHARACTER);
        guard.onZoneChanged(
                new ZoneChangedEvent(other, Optional.empty(), Optional.of("dustlands")));

        assertThat(sent).as("the other one is still blocked").hasSize(2);
    }

    private static ZoneChangedEvent entering(String zoneKey) {
        return new ZoneChangedEvent(CHARACTER, Optional.empty(), Optional.of(zoneKey));
    }

    private static ZoneChangedEvent leaving(String zoneKey) {
        return new ZoneChangedEvent(CHARACTER, Optional.of(zoneKey), Optional.empty());
    }

    private static Zones load() throws Exception {
        Map<String, Object> document = ZoneFixture.document();
        ConfigSchema<ZoneConfig> schema = ZoneConfigSchema.schema(ZoneFixture.worlds());
        return new DefaultZones(
                schema.bind(SchemaValidator.validate(Path.of("zones.yml"), document, schema)));
    }
}
