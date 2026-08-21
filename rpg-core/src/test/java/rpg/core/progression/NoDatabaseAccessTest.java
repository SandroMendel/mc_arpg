package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Gaining experience must not touch the database (FR-054, FR-055, FR-062, SC-004, SC-005, SC-011).
 *
 * <p>Experience is the most frequent progress event in the game. One write per kill would be the
 * bottleneck at 800 active mobs, so Principle II forbids a database access per game event outright.
 * These tests count - the number staying at zero <b>is</b> the promise.
 */
class NoDatabaseAccessTest {

    @Test
    @DisplayName("a thousand gains in a second produce zero database reads")
    void thousandGainsNoReads() {
        ProgressionFixture fixture =
                new ProgressionFixture(ProgressionFixture.config(CurveFixture.upTo60()));
        UUID character = fixture.character();

        for (int i = 0; i < 1_000; i++) {
            fixture.progression.grant(character, 12L, XpSource.MOB_KILL);
        }

        assertThat(fixture.repository.reads).as("SC-004: not one").isZero();
    }

    @Test
    @DisplayName("a thousand gains leave the character marked, which is the whole write path")
    void gainsMarkTheCharacter() {
        ProgressionFixture fixture =
                new ProgressionFixture(ProgressionFixture.config(CurveFixture.upTo60()));
        UUID character = fixture.character();

        for (int i = 0; i < 1_000; i++) {
            fixture.progression.grant(character, 12L, XpSource.MOB_KILL);
        }

        // A mark per gain is fine: the write-behind buffer from B02 collapses them into one write
        // per flush. What matters is that none of them IS a write.
        assertThat(fixture.repository.marksFor(character)).isEqualTo(1_000);
        assertThat(fixture.repository.reads).isZero();
    }

    @Test
    @DisplayName("a level requirement query produces no database access either")
    void levelQueryDoesNotRead() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();

        for (int i = 0; i < 500; i++) {
            fixture.progression.meetsLevel(character, 10);
            fixture.progression.levelOf(character);
            fixture.progression.progressOf(character);
        }

        // SC-011. Five blocks gate content on this query; if it read the database, every zone entry
        // and every ability check would be a round trip.
        assertThat(fixture.repository.reads).isZero();
    }

    @Test
    @DisplayName("the in-memory state is authoritative while the session lasts")
    void memoryIsAuthoritative() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();

        fixture.progression.grant(character, 100L, XpSource.MOB_KILL);

        // The repository has never been read and holds nothing; the answer comes from memory
        // regardless (Principle IV, FR-055).
        assertThat(fixture.repository.find(character).join()).isEmpty();
        assertThat(fixture.progression.levelOf(character)).hasValue(2);
    }

    @Test
    @DisplayName("the flush reads the live state, so there is no second copy to disagree")
    void flushReadsTheLiveState() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();

        fixture.progression.grant(character, 100L, XpSource.MOB_KILL);

        // This is what the JDBC writer calls at flush time instead of keeping its own copy.
        assertThat(fixture.progression.stateOf(character))
                .isPresent()
                .get()
                .extracting(ProgressState::level)
                .isEqualTo(2);
    }

    @Test
    @DisplayName("ten thousand gains run without a scheduled task")
    void noScheduledWork() {
        ProgressionFixture fixture =
                new ProgressionFixture(ProgressionFixture.config(CurveFixture.upTo60()));
        UUID character = fixture.character();

        for (int i = 0; i < 10_000; i++) {
            fixture.progression.grant(character, 5L, XpSource.MOB_KILL);
        }

        // FR-061: no recurring work per player, character or party. The counter is the assertion.
        assertThat(fixture.scheduler.scheduled).isZero();
    }

    @Test
    @DisplayName("granting creates no avoidable object per event")
    void noAllocationPerEvent() {
        // A direct allocation count is not available without instrumentation, so this asserts the
        // structural property instead: the entry point takes primitives, and the result of a gain
        // that does not level up is one record with no nested objects. What it really guards is
        // somebody adding a per-event context object later - the signature would have to change.
        ProgressionFixture fixture =
                new ProgressionFixture(ProgressionFixture.config(CurveFixture.upTo60()));
        UUID character = fixture.character();

        XpResult result = fixture.progression.grant(character, 5L, XpSource.MOB_KILL);

        assertThat(result.levelUp()).as("no LevelUp is built when nothing rose").isNull();
        assertThat(result.rejection()).isEqualTo(XpRejection.NONE);
        // And a rejection returns without building anything either.
        assertThat(fixture.progression.grant(character, 0L, XpSource.MOB_KILL).levelUp()).isNull();
    }
}
