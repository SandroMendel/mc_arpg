package rpg.core.performance;

/** Monotonic time source; tests inject a deterministic implementation. */
@FunctionalInterface
public interface MonotonicClock {

    long nanoTime();

    static MonotonicClock system() {
        return System::nanoTime;
    }
}
