package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.session.PlayerSession;
import rpg.core.session.SessionNotReadyException;
import rpg.core.session.SessionRegistry;

/**
 * T063, T063a: B03's readiness rule, and the fact that it does not apply to mobs
 * (FR-035, FR-037).
 *
 * <p>The rule itself is B03's, and it exists because a player who reads values before their session
 * has loaded gets defaults - and then plays on with values that are not theirs. B04 has to honour
 * it, and just as importantly must not extend it to holders that have no session at all.
 */
class SessionNotReadyTest {

    /** Minimal registry: a set of player ids that count as ready. */
    private static final class StubRegistry implements SessionRegistry {
        private final Set<UUID> ready = new HashSet<>();

        @Override
        public Optional<PlayerSession> find(UUID playerId) {
            return Optional.empty();
        }

        @Override
        public PlayerSession require(UUID playerId) {
            throw new IllegalStateException("not needed for this test");
        }

        @Override
        public boolean isReady(UUID playerId) {
            return ready.contains(playerId);
        }

        @Override
        public int activeSessionCount() {
            return ready.size();
        }
    }

    @Test
    @DisplayName("a player whose session is not ready gets told so, not a default value")
    void queriesAreRefused() {
        StubRegistry registry = new StubRegistry();
        EngineFixture fixture = new EngineFixture(StatConfig.defaults(), registry);

        UUID playerId = UUID.randomUUID();
        fixture.engine.createForCharacter(playerId, UUID.randomUUID(), new ResourcePool(0.0, 0.0));

        assertThatThrownBy(() -> fixture.engine.snapshot(playerId))
                .isInstanceOf(SessionNotReadyException.class);
        assertThatThrownBy(() -> fixture.engine.value(playerId, Attribute.HEALTH))
                .isInstanceOf(SessionNotReadyException.class);
        assertThatThrownBy(() -> fixture.engine.resources(playerId))
                .isInstanceOf(SessionNotReadyException.class);
        assertThatThrownBy(() -> fixture.engine.contributions(playerId, Attribute.HEALTH))
                .isInstanceOf(SessionNotReadyException.class);
    }

    @Test
    @DisplayName("contribution changes are refused too, rather than written into the void")
    void changesAreRefused() {
        StubRegistry registry = new StubRegistry();
        EngineFixture fixture = new EngineFixture(StatConfig.defaults(), registry);

        UUID playerId = UUID.randomUUID();
        fixture.engine.createForCharacter(playerId, UUID.randomUUID(), new ResourcePool(0.0, 0.0));

        assertThatThrownBy(
                        () ->
                                fixture.engine.apply(
                                        playerId,
                                        EngineFixture.buff(
                                                "might",
                                                StatModifier.flat(Attribute.HEALTH, 1.0))))
                .isInstanceOf(SessionNotReadyException.class);
        assertThatThrownBy(() -> fixture.engine.changeHealth(playerId, -1.0))
                .isInstanceOf(SessionNotReadyException.class);
    }

    @Test
    @DisplayName("once the session is ready everything works normally")
    void readySessionWorks() {
        StubRegistry registry = new StubRegistry();
        EngineFixture fixture = new EngineFixture(StatConfig.defaults(), registry);

        UUID playerId = UUID.randomUUID();
        fixture.engine.createForCharacter(playerId, UUID.randomUUID(), new ResourcePool(0.0, 0.0));
        registry.ready.add(playerId);

        assertThat(fixture.engine.value(playerId, Attribute.HEALTH)).isEqualTo(100.0);
    }

    @Test
    @DisplayName("the load path can calculate before the session is ready - that is the point")
    void loadPathIsNotBlocked() {
        StubRegistry registry = new StubRegistry();
        EngineFixture fixture = new EngineFixture(StatConfig.defaults(), registry);

        UUID playerId = UUID.randomUUID();
        fixture.engine.createForCharacter(playerId, UUID.randomUUID(), new ResourcePool(0.0, 0.0));

        // recalculateNow and restoreResources run while the session is still loading, which is
        // exactly what FR-019b requires: a calculated holder before the player is released.
        assertThatCode(
                        () -> {
                            StatSnapshot snapshot = fixture.engine.recalculateNow(playerId);
                            fixture.engine.restoreResources(
                                    playerId,
                                    ResourcePool.full(
                                            snapshot.get(Attribute.HEALTH),
                                            snapshot.get(Attribute.MANA)));
                        })
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a mob has no session and must not fail on one")
    void entityHolderBypassesTheRule() {
        StubRegistry registry = new StubRegistry();
        EngineFixture fixture = new EngineFixture(StatConfig.defaults(), registry);

        UUID mob = UUID.randomUUID();
        fixture.engine.createForEntity(mob);

        assertThat(fixture.engine.value(mob, Attribute.HEALTH)).isEqualTo(100.0);
        assertThatCode(
                        () -> {
                            fixture.engine.apply(
                                    mob,
                                    ModifierSet.of(
                                            SourceId.of(SourceKind.CLASS, "zombie"),
                                            StatModifier.flat(Attribute.HEALTH, 50.0)));
                            fixture.engine.changeHealth(mob, -10.0);
                            fixture.engine.resources(mob);
                            fixture.engine.contributions(mob, Attribute.HEALTH);
                        })
                .doesNotThrowAnyException();
        assertThat(registry.activeSessionCount()).isZero();
        assertThat(List.of(fixture.engine.holderCount())).containsExactly(1);
    }
}
