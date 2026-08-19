package rpg.core.module;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Confines a failure to the module that caused it (FR-009, Constitution VI).
 *
 * <p>Work that a module runs on behalf of the server - a scheduled task, an event handler, a tick
 * callback - is wrapped here. A {@link RuntimeException} escaping that work is logged with the owning
 * module's identifier and swallowed, so the server tick and every other module carry on unaffected
 * (SC-004).
 *
 * <p>{@link Error} is deliberately <em>not</em> swallowed: an {@code OutOfMemoryError} or
 * {@code StackOverflowError} says the JVM itself is unhealthy, and pretending otherwise would leave
 * players in the inconsistent state Constitution VI forbids.
 */
public final class ModuleFaultBarrier {

    private final String moduleId;
    private final Logger logger;

    public ModuleFaultBarrier(String moduleId, Logger logger) {
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Runs {@code work}, containing any {@link RuntimeException} it throws.
     *
     * @param description short description of the work, used in the log line
     * @return {@code true} if the work completed, {@code false} if it failed and was contained
     */
    public boolean run(String description, Runnable work) {
        try {
            work.run();
            return true;
        } catch (RuntimeException failure) {
            logFailure(description, failure);
            return false;
        }
    }

    /**
     * Like {@link #run(String, Runnable)} for work producing a value.
     *
     * @return the produced value, or {@link Optional#empty()} if the work failed and was contained
     */
    public <T> Optional<T> call(String description, Supplier<T> work) {
        try {
            return Optional.ofNullable(work.get());
        } catch (RuntimeException failure) {
            logFailure(description, failure);
            return Optional.empty();
        }
    }

    /** The module this barrier protects the rest of the server from. */
    public String moduleId() {
        return moduleId;
    }

    private void logFailure(String description, RuntimeException failure) {
        logger.log(
                Level.SEVERE,
                "[module] id="
                        + moduleId
                        + " phase=RUNTIME state=CONTAINED work="
                        + description
                        + " - the failure was contained, other modules and the server tick are"
                        + " unaffected",
                failure);
    }
}
