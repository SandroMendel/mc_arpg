package rpg.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T071, T073: migrating old records, and refusing records from the future.
 *
 * <p>The mechanism carries no steps today, because version 1 is the first format. That is exactly
 * why it is tested now: a migration path added in two years cannot reach records that were written
 * without one, so the machinery has to exist and work before the first record needs it.
 *
 * <p>The asymmetry is deliberate. An <em>older</em> record is migrated forward. A <em>newer</em> one
 * is refused, because there is no way to guess what a future format meant and interpreting it with
 * today's rules corrupts it silently - the failure would surface as scrambled progress, long after
 * the login that caused it.
 *
 * <p><strong>What this test does not cover.</strong> The step-application loop itself cannot be
 * exercised yet: a record needing migration must have a version below the current one, and with
 * version 1 as the first format no such record can be constructed. The guards around the loop are
 * covered here; the loop gets its test in the block that introduces version 2, which is also the
 * first moment it can fail.
 */
class StateVersionMigratorTest {

    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");
    private static final Logger QUIET = Logger.getLogger("state-version-migrator-test");

    private StateVersionMigrator migrator;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        migrator = new StateVersionMigrator(QUIET);
    }

    @Test
    void aBundleAlreadyInTheCurrentFormatIsReturnedUnchanged() {
        SessionBundle bundle = bundleWith(current());

        SessionBundle migrated = migrator.migrate(bundle);

        assertThat(migrated).isSameAs(bundle);
        assertThat(migrator.migratedAnything(bundle, migrated)).isFalse();
    }

    @Test
    void onlyAStepBelowTheCurrentVersionMayBeRegistered() {
        // A step registered at or above the current version can never run, and one below 1 refers
        // to a format that never existed. Both are mistakes worth catching at registration rather
        // than as a migration that quietly does nothing.
        assertThatThrownBy(() -> migrator.register(PlayerCharacter.CURRENT_DATA_VERSION, c -> c))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> migrator.register(0, c -> c))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noRecordCanClaimAVersionBelowTheFirstFormat() {
        // Version 1 is the first format, so "older than 1" is not an old save - it is a corrupt or
        // hand-edited one, and it is refused at construction rather than migrated from nowhere.
        assertThatThrownBy(() -> withVersion(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withVersion(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRecordFromANewerBuildIsRefusedBeforeItReachesTheMigrator() {
        SessionBundle fromFuture = bundleWith(withVersion(PlayerCharacter.CURRENT_DATA_VERSION + 1));

        assertThat(fromFuture.anyFromFutureVersion()).isPresent();

        // The load path checks this before migrating, so an unknown format never gets interpreted.
        DefaultSessionRegistry registry = new DefaultSessionRegistry();
        DefaultSessionLifecycle lifecycle =
                new DefaultSessionLifecycle(
                        registry,
                        playerId -> fromFuture,
                        new NoWrites(),
                        migrator,
                        Runnable::run,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        QUIET);

        assertThatThrownBy(
                        () ->
                                lifecycle
                                        .beginLoad(fromFuture.playerId(), Duration.ofSeconds(5))
                                        .join())
                .hasCauseInstanceOf(UnknownDataVersionException.class);
        assertThat(registry.peek(fromFuture.playerId())).isEmpty();
    }

    @Test
    void theRefusalNamesBothVersionsSoTheOperatorKnowsWhichBuildToRun() {
        UnknownDataVersionException failure = new UnknownDataVersionException(7, 1);

        assertThat(failure.getMessage()).contains("7").contains("1");
        assertThat(failure.foundVersion()).isEqualTo(7);
        assertThat(failure.supportedVersion()).isEqualTo(1);
    }

    @Test
    void migratedAnythingReportsChangeRatherThanIntent() {
        // The load path writes the record back only when this says something actually changed
        // (FR-026). If it reported "a migration ran" instead of "the data differs", every login of
        // every player would mark their characters dirty and the write-behind buffer would carry a
        // full server's worth of no-op writes every autosave interval.
        SessionBundle bundle = bundleWith(current());

        assertThat(migrator.migratedAnything(bundle, migrator.migrate(bundle))).isFalse();

        PlayerCharacter changed =
                new PlayerCharacter(
                        bundle.characters().getFirst().characterId(),
                        bundle.playerId(),
                        CharacterClass.ROGUE,
                        PlayerCharacter.CURRENT_DATA_VERSION,
                        1L,
                        NOW,
                        NOW);
        SessionBundle after =
                new SessionBundle(
                        bundle.playerId(), bundle.accountState(), List.of(changed), List.of());

        assertThat(migrator.migratedAnything(bundle, after)).isTrue();
    }

    private static SessionBundle bundleWith(PlayerCharacter character) {
        return new SessionBundle(
                character.playerId(),
                Optional.of(rpg.core.persistence.PlayerState.initial(character.playerId(), NOW)),
                List.of(character),
                List.of());
    }

    private static PlayerCharacter current() {
        return PlayerCharacter.create(UUID.randomUUID(), CharacterClass.WARRIOR, NOW);
    }

    private static PlayerCharacter withVersion(int dataVersion) {
        UUID playerId = UUID.randomUUID();
        return new PlayerCharacter(
                UUID.randomUUID(), playerId, CharacterClass.MAGE, dataVersion, 0L, NOW, NOW);
    }

    private static final class NoWrites implements DefaultSessionLifecycle.SessionWriter {

        @Override
        public CompletableFuture<Void> writeAndAwait(UUID playerId) {
            throw new AssertionError("a refused load must never write");
        }

        @Override
        public void markCharactersDirty(List<PlayerCharacter> characters) {
            throw new AssertionError("a refused load must never mark anything dirty");
        }
    }
}
