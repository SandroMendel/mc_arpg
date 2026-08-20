package rpg.core.combat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;
import rpg.core.stats.Attribute;
import rpg.core.stats.DefaultStatEngine;
import rpg.core.stats.ModifierSet;
import rpg.core.stats.ResourcePool;
import rpg.core.stats.SourceId;
import rpg.core.stats.SourceKind;
import rpg.core.stats.StatConfig;
import rpg.core.stats.StatModifier;

/**
 * Shared setup for the combat tests: a controlled clock, a counting scheduler and a real stat
 * engine.
 *
 * <p>The clock is not convenience. Three rules of this block are time-based - attack window, combat
 * state, contribution age - and testing them with real waits would make the suite both slow and
 * flaky. Advancing a clock by hand is exact and instant.
 */
final class CombatFixture {

    /** A clock a test moves by hand. */
    static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-08-20T12:00:00Z");

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

        void advance(Duration by) {
            now = now.plus(by);
        }

        void advanceMillis(long millis) {
            advance(Duration.ofMillis(millis));
        }
    }

    /** Counts what was scheduled. In this block the expected answer is always zero. */
    static final class CountingScheduler implements Scheduler {
        int scheduled;

        @Override
        public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
            return record(task);
        }

        @Override
        public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
            return record(task);
        }

        @Override
        public TaskHandle runAsync(Runnable task) {
            return record(task);
        }

        @Override
        public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
            return record(task);
        }

        private TaskHandle record(Runnable task) {
            scheduled++;
            task.run();
            return new TaskHandle() {
                @Override
                public void cancel() {}

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }
    }

    final TestClock clock = new TestClock();
    final CountingScheduler scheduler = new CountingScheduler();
    final EventBus eventBus;
    final DefaultStatEngine stats;
    final DefaultCombatPipeline pipeline;

    final List<CombatDeathEvent> deaths = new ArrayList<>();
    final List<DamageDealtEvent> damageEvents = new ArrayList<>();
    final List<CombatStateChangedEvent> combatStates = new ArrayList<>();

    CombatFixture() {
        this(CombatConfig.defaults());
    }

    CombatFixture(CombatConfig config) {
        Logger logger = Logger.getLogger(CombatFixture.class.getName());
        logger.setLevel(Level.OFF);

        this.eventBus = new DefaultEventBus(logger);
        this.stats =
                new DefaultStatEngine(StatConfig.defaults(), scheduler, eventBus, null, logger);
        this.pipeline =
                new DefaultCombatPipeline(config, stats, eventBus, null, clock, logger);

        eventBus.subscribe(CombatDeathEvent.class, deaths::add);
        eventBus.subscribe(DamageDealtEvent.class, damageEvents::add);
        eventBus.subscribe(CombatStateChangedEvent.class, combatStates::add);
    }

    /** A player character with the given physical damage, defence and attack speed. */
    UUID player(double physicalDamage, double defence, double attackSpeed) {
        UUID playerId = UUID.randomUUID();
        stats.createForCharacter(playerId, UUID.randomUUID(), new ResourcePool(0.0, 0.0));
        applyStats(playerId, "gear", physicalDamage, defence, attackSpeed);
        return playerId;
    }

    /** A creature with the given health, defence and physical damage. */
    UUID mob(double health, double defence, double physicalDamage) {
        UUID mobId = UUID.randomUUID();
        stats.createForEntity(mobId);
        stats.apply(
                mobId,
                ModifierSet.of(
                        SourceId.of(SourceKind.CLASS, "mob:TEST"),
                        StatModifier.flat(Attribute.HEALTH, health - 100.0),
                        StatModifier.flat(Attribute.DEFENSE, defence),
                        StatModifier.flat(Attribute.PHYSICAL_DAMAGE, physicalDamage - 5.0)));
        stats.recalculateNow(mobId);
        fillToMax(mobId);
        return mobId;
    }

    private void applyStats(
            UUID holderId, String key, double physicalDamage, double defence, double attackSpeed) {
        stats.apply(
                holderId,
                ModifierSet.of(
                        SourceId.of(SourceKind.EQUIPMENT, key),
                        StatModifier.flat(Attribute.PHYSICAL_DAMAGE, physicalDamage - 5.0),
                        StatModifier.flat(Attribute.MAGIC_DAMAGE, physicalDamage - 5.0),
                        StatModifier.flat(Attribute.DEFENSE, defence),
                        StatModifier.percent(
                                Attribute.ATTACK_SPEED, (attackSpeed / 4.0) - 1.0)));
        stats.recalculateNow(holderId);
        fillToMax(holderId);
    }

    /** Starts a holder at full health and mana, as the load path would. */
    void fillToMax(UUID holderId) {
        var snapshot = stats.snapshot(holderId);
        stats.restoreResources(
                holderId,
                ResourcePool.full(
                        snapshot.get(Attribute.HEALTH), snapshot.get(Attribute.MANA)));
    }

    double health(UUID holderId) {
        return stats.resources(holderId).currentHealth();
    }

    void clearRecorded() {
        deaths.clear();
        damageEvents.clear();
        combatStates.clear();
        scheduler.scheduled = 0;
    }
}
