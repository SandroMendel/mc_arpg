package rpg.core.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionRegistry;
import rpg.core.stats.Attribute;
import rpg.core.stats.DefaultStatEngine;
import rpg.core.stats.ModifierSet;
import rpg.core.stats.ResourcePool;
import rpg.core.stats.SourceId;
import rpg.core.stats.SourceKind;
import rpg.core.stats.StatConfig;
import rpg.core.stats.StatModifier;

/**
 * T045a: B03's readiness rule, in combat (FR-046).
 *
 * <p>The rule itself is B03's, and it exists because a player who acts before their session has
 * loaded acts with values that are not theirs. In combat that is worse than elsewhere: they would
 * take damage against a defence of zero.
 *
 * <p>Equally important is the other half - a creature has no session and must not fail on one. Left
 * unqualified, the readiness check would make mobs unattackable, and the failure would look like a
 * combat bug rather than a session one.
 */
class SessionNotReadyCombatTest {

    /** Minimal registry: a set of player ids counted as ready. */
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

    private final StubRegistry registry = new StubRegistry();
    private final CombatFixture.TestClock clock = new CombatFixture.TestClock();
    private final DefaultStatEngine stats;
    private final DefaultCombatPipeline pipeline;

    SessionNotReadyCombatTest() {
        Logger logger = Logger.getLogger(SessionNotReadyCombatTest.class.getName());
        logger.setLevel(Level.OFF);
        EventBus eventBus = new DefaultEventBus(logger);
        // The stat engine gets no registry: this test is about the combat pipeline's check, and
        // routing through B04's would make it fail for a different reason.
        this.stats = new DefaultStatEngine(StatConfig.defaults(), noScheduler(), eventBus, null, logger);
        this.pipeline =
                new DefaultCombatPipeline(
                        CombatConfig.defaults(), stats, eventBus, registry, Clock.systemUTC(), logger);
    }

    private static rpg.core.scheduler.Scheduler noScheduler() {
        return new CombatFixture.CountingScheduler();
    }

    private UUID player(double physicalDamage) {
        UUID playerId = UUID.randomUUID();
        stats.createForCharacter(playerId, UUID.randomUUID(), new ResourcePool(0.0, 0.0));
        stats.apply(
                playerId,
                ModifierSet.of(
                        SourceId.of(SourceKind.EQUIPMENT, "gear"),
                        StatModifier.flat(Attribute.PHYSICAL_DAMAGE, physicalDamage - 5.0)));
        var snapshot = stats.recalculateNow(playerId);
        stats.restoreResources(
                playerId,
                ResourcePool.full(snapshot.get(Attribute.HEALTH), snapshot.get(Attribute.MANA)));
        return playerId;
    }

    private UUID mob() {
        UUID mobId = UUID.randomUUID();
        stats.createForEntity(mobId);
        return mobId;
    }

    @Test
    @DisplayName("a player whose session is not ready cannot deal damage")
    void cannotDealDamage() {
        UUID attacker = player(50.0);
        UUID target = mob();

        DamageResult result = pipeline.meleeAttack(attacker, target);

        assertThat(result.applied()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectReason.SESSION_NOT_READY);
    }

    @Test
    @DisplayName("a player whose session is not ready cannot take damage either")
    void cannotTakeDamage() {
        UUID victim = player(5.0);
        UUID attacker = mob();

        assertThat(pipeline.meleeAttack(attacker, victim).reason())
                .isEqualTo(RejectReason.SESSION_NOT_READY);
        assertThat(pipeline.environmentDamage(victim, EnvironmentSource.LAVA).reason())
                .isEqualTo(RejectReason.SESSION_NOT_READY);
        assertThat(pipeline.fallDamage(victim, 20.0).reason())
                .isEqualTo(RejectReason.SESSION_NOT_READY);
    }

    @Test
    @DisplayName("once the session is ready everything works normally")
    void readySessionWorks() {
        UUID attacker = player(50.0);
        UUID target = mob();
        registry.ready.add(attacker);

        assertThat(pipeline.meleeAttack(attacker, target).applied()).isTrue();
    }

    @Test
    @DisplayName("a creature has no session and must not fail on one")
    void mobsAreUnaffected() {
        UUID attacker = mob();
        UUID target = mob();
        // Mob against mob is refused by the permission rule, not by the readiness rule - which is
        // exactly the distinction that matters here.
        assertThat(pipeline.meleeAttack(attacker, target).reason())
                .isEqualTo(RejectReason.NOT_PERMITTED);

        assertThat(pipeline.environmentDamage(target, EnvironmentSource.LAVA).applied()).isTrue();
    }

    @Test
    @DisplayName("nothing is left behind by a refused event")
    void refusedEventLeavesNothing() {
        UUID attacker = player(50.0);
        UUID target = mob();

        pipeline.meleeAttack(attacker, target);

        assertThat(pipeline.currentShares(target)).isEmpty();
        assertThat(pipeline.isInCombat(attacker)).isFalse();
        assertThat(pipeline.isInCombat(target)).isFalse();
    }
}
