package rpg.platform.hud;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import rpg.core.combat.CombatMessageKeys;
import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;

/**
 * The doubles both readout tests need.
 *
 * <p>Small because {@link CombatStatusSource} is small - that was the point of not handing the displays
 * the whole stat engine.
 */
final class HudFixture {

    private HudFixture() {}

    /** A status source backed by a map, so a test states exactly the numbers it is about. */
    static final class Statuses implements CombatStatusSource {

        private final Map<UUID, Status> byHolder = new HashMap<>();

        /**
         * A holder without mana - a mob.
         *
         * <p>Kept as the short form because most tests here are about health and defence, and a
         * maximum of zero is what the readout uses to leave the mana part out.
         */
        void give(UUID holderId, double health, double maxHealth, double defense) {
            byHolder.put(holderId, new Status(health, maxHealth, 0.0, 0.0, defense));
        }

        /** A player: health, mana and defence. */
        void give(
                UUID holderId,
                double health,
                double maxHealth,
                double mana,
                double maxMana,
                double defense) {
            byHolder.put(holderId, new Status(health, maxHealth, mana, maxMana, defense));
        }

        @Override
        public Optional<Status> statusOf(UUID holderId) {
            return Optional.ofNullable(byHolder.get(holderId));
        }
    }

    /** Runs entity-bound work at once; holds delayed work until a test asks for it. */
    static final class RecordingScheduler implements Scheduler {

        int entityTasks;
        private final List<Runnable> delayed = new ArrayList<>();

        /**
         * Runs the pending delayed task once.
         *
         * <p>Taken out of the list first: the tasks here re-schedule themselves, and running straight
         * from the list would recurse until the stack gave up.
         */
        void runDelayedOnce() {
            List<Runnable> pending = new ArrayList<>(delayed);
            delayed.clear();
            pending.forEach(Runnable::run);
        }

        @Override
        public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
            throw new UnsupportedOperationException("no readout uses this");
        }

        @Override
        public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
            entityTasks++;
            task.run();
            return handle();
        }

        /** ADR-024: verzoegert, aber im Test genauso behandelt wie sofort. */
        @Override
        public TaskHandle runSyncOnEntityDelayed(EntityRef entity, Duration delay, Runnable task) {
            entityTasks++;
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runAsync(Runnable task) {
            task.run();
            return handle();
        }

        @Override
        public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
            delayed.add(task);
            return handle();
        }

        private static TaskHandle handle() {
            return new TaskHandle() {
                @Override
                public void cancel() {
                    // nothing to cancel
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }
    }

    /** The shipped wording, so a test reads what a player would see. */
    static Messages messages() {
        Map<String, String> texts = new HashMap<>();
        for (MessageKey key : CombatMessageKeys.all()) {
            texts.put(key.value(), key.value());
        }
        texts.put(
                CombatMessageKeys.STATUS_ACTION_BAR.value(),
                "{health}/{max} HP ({percent}%) {mana}/{maxMana} MP DEF {defense}");
        texts.put(
                CombatMessageKeys.STATUS_ACTION_BAR_NO_MANA.value(),
                "{health}/{max} HP ({percent}%) DEF {defense}");
        texts.put(
                CombatMessageKeys.TARGET_REPORT.value(),
                "{target} {health}/{max} ({percent}%) DEF {defense} -{damage}");
        texts.put(CombatMessageKeys.TARGET_SLAIN.value(), "{target} slain -{damage}");
        return new MapMessages(texts);
    }
}
