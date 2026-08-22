package rpg.core.classes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;
import rpg.core.session.CharacterClass;
import rpg.core.stats.Attribute;
import rpg.core.stats.DefaultStatEngine;
import rpg.core.stats.ResourcePool;
import rpg.core.stats.StatConfig;
import rpg.core.stats.StatsRecalculatedEvent;

/**
 * A real {@link DefaultStatEngine} with the class contributor registered.
 *
 * <p>Deliberately the real engine, not a stub: the promises under test here - the cap applies, and a
 * change costs exactly one recalculation - are properties of the engine reacting to this block. A stub
 * would have tested the stub.
 */
final class ClassEngineFixture {

    private static final Logger QUIET = quietLogger();

    final EventBus eventBus = new DefaultEventBus(QUIET);
    final DefaultStatEngine stats;
    final List<StatsRecalculatedEvent> recalculations = new ArrayList<>();

    private final ClassConfig config;
    private final Map<UUID, CharacterClass> classes = new HashMap<>();
    private final Map<UUID, Integer> levels = new HashMap<>();
    private final Map<UUID, ClassProgress> progress = new HashMap<>();

    ClassEngineFixture() throws Exception {
        this.config = ClassConfigFixture.bind(ClassConfigFixture.valid());
        this.stats =
                new DefaultStatEngine(
                        StatConfig.defaults(), new ImmediateScheduler(), eventBus, null, QUIET);
        this.stats.registerBaseStatContributor(
                new ClassStatContributor(
                        config,
                        id -> Optional.ofNullable(classes.get(id)),
                        id -> levels.getOrDefault(id, 1),
                        id -> Optional.ofNullable(progress.get(id))));
        eventBus.subscribe(StatsRecalculatedEvent.class, recalculations::add);
    }

    /** A character of the given class on level 1, tier 1 of both ladders. */
    UUID character(CharacterClass id) {
        UUID characterId = UUID.randomUUID();
        UUID holderId = UUID.randomUUID();
        classes.put(characterId, id);
        levels.put(characterId, 1);
        progress.put(characterId, ClassProgress.initial(characterId));
        stats.createForCharacter(holderId, characterId, new ResourcePool(0.0, 0.0));
        holders.put(characterId, holderId);
        return characterId;
    }

    private final Map<UUID, UUID> holders = new HashMap<>();

    UUID holderOf(UUID characterId) {
        return holders.get(characterId);
    }

    double attribute(UUID characterId, Attribute attribute) {
        return stats.value(holders.get(characterId), attribute);
    }

    /** Raises the level and triggers the single recalculation that a level-up owes. */
    void levelTo(UUID characterId, int level) {
        levels.put(characterId, level);
        stats.recalculateNow(holders.get(characterId));
    }

    /** Advances one ladder and triggers the single recalculation that a tier advance owes. */
    void advance(UUID characterId, LadderSlot slot) {
        progress.compute(
                characterId,
                (id, current) ->
                        (current == null ? ClassProgress.initial(id) : current).advanced(slot));
        stats.recalculateNow(holders.get(characterId));
    }

    void setTier(UUID characterId, int armorTier, int weaponTier) {
        progress.put(
                characterId,
                new ClassProgress(
                        characterId,
                        armorTier,
                        weaponTier,
                        ClassProgress.CURRENT_DATA_VERSION,
                        0L));
        stats.recalculateNow(holders.get(characterId));
    }

    void clearRecorded() {
        recalculations.clear();
    }

    ClassConfig config() {
        return config;
    }

    /** Runs everything inline, so a test never waits and the counting stays exact. */
    private static final class ImmediateScheduler implements Scheduler {
        @Override
        public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
            task.run();
            return handle();
        }

        /** ADR-024: verzoegert, aber im Test genauso behandelt wie sofort. */
        @Override
        public TaskHandle runSyncOnEntityDelayed(EntityRef entity, Duration delay, Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runAsync(Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runAsyncDelayed(java.time.Duration delay, Runnable task) {
            task.run();
            return handle();
        }

        private static TaskHandle handle() {
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

    private static Logger quietLogger() {
        Logger logger = Logger.getLogger(ClassEngineFixture.class.getName());
        logger.setLevel(Level.OFF);
        return logger;
    }
}
