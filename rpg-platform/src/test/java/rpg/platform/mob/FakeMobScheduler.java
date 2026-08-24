package rpg.platform.mob;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;

/**
 * Sofortige und synchrone Aufgaben laufen sofort, wie in Wirklichkeit im selben Tick. Verzoegerte
 * asynchrone Aufgaben - die einzige Sorte, mit der {@link HordeSweep} sich selbst neu einplant -
 * werden aufgehoben, damit ein Test die Schleife Schritt fuer Schritt antreiben kann, statt in eine
 * Endlosrekursion zu laufen.
 *
 * <p>{@link #clock}, falls gesetzt, ruecht bei jeder abgearbeiteten Verzoegerung genau um deren
 * Dauer vor - so simuliert {@link #runNextDelayed()} echten Zeitablauf, ohne dass ein Test hunderte
 * Runden drehen muesste, um eine Kulanzfrist wie {@code cleanup-after-seconds} zu ueberschreiten.
 */
final class FakeMobScheduler implements Scheduler {

    int asyncKickoffs;
    int syncPlacements;
    final Deque<Runnable> delayedQueue = new ArrayDeque<>();
    final List<Duration> delays = new ArrayList<>();

    private final Deque<Duration> pendingDelays = new ArrayDeque<>();
    private final MutableClock clock;

    FakeMobScheduler() {
        this(null);
    }

    FakeMobScheduler(MutableClock clock) {
        this.clock = clock;
    }

    /** Fuehrt genau eine anstehende verzoegerte Aufgabe aus, nachdem die Uhr um ihre Frist vorruecte. */
    void runNextDelayed() {
        Runnable next = delayedQueue.poll();
        Duration delay = pendingDelays.poll();
        if (next == null) {
            return;
        }
        if (clock != null && delay != null) {
            clock.advance(delay);
        }
        next.run();
    }

    @Override
    public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
        syncPlacements++;
        task.run();
        return handle();
    }

    @Override
    public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
        task.run();
        return handle();
    }

    @Override
    public TaskHandle runSyncOnEntityDelayed(EntityRef entity, Duration delay, Runnable task) {
        throw new UnsupportedOperationException("HordeSweep benutzt das nicht");
    }

    @Override
    public TaskHandle runAsync(Runnable task) {
        asyncKickoffs++;
        task.run();
        return handle();
    }

    @Override
    public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
        delays.add(delay);
        delayedQueue.add(task);
        pendingDelays.add(delay);
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
