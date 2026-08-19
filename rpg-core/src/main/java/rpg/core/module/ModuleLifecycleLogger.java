package rpg.core.module;

import java.time.Duration;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Structured status logging for every module at start, reload and shutdown (FR-010).
 *
 * <p>"Structured" here means a fixed, greppable line shape rather than free prose:
 *
 * <pre>
 *   [module] id=stat-engine phase=START state=ACTIVE took=142ms
 *   [module] id=zones phase=SHUTDOWN state=STOPPED took=10004ms detail=forced after timeout
 * </pre>
 *
 * <p>An operator reading a start log can therefore see at a glance which module failed and how long
 * each one took - which is what makes the 30 second bootstrap budget (SC-001) and the 10 second
 * shutdown budget (SC-007) verifiable from the log alone.
 *
 * <p>Uses {@code java.util.logging}, which every Bukkit plugin logger already funnels into, so
 * {@code rpg-core} needs no logging dependency and stays free of platform types.
 */
public final class ModuleLifecycleLogger {

    /** The lifecycle phase a log line belongs to. */
    public enum Phase {
        START,
        RELOAD,
        SHUTDOWN
    }

    private final Logger logger;

    public ModuleLifecycleLogger(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Logs a successful transition. */
    public void logSuccess(
            Phase phase, String moduleId, Module.LifecycleState state, Duration took) {
        logger.info(line(phase, moduleId, state, took, null));
    }

    /** Logs a transition that needs the operator's attention but is not a failure. */
    public void logWarning(
            Phase phase,
            String moduleId,
            Module.LifecycleState state,
            Duration took,
            String detail) {
        logger.warning(line(phase, moduleId, state, took, detail));
    }

    /** Logs a failed transition together with the causing throwable. */
    public void logFailure(
            Phase phase,
            String moduleId,
            Module.LifecycleState state,
            Duration took,
            Throwable cause) {
        logger.log(
                Level.SEVERE,
                line(phase, moduleId, state, took, cause.toString()),
                cause);
    }

    /** Logs the resolved start order so the bootstrap is reproducible from the log (FR-001). */
    public void logStartOrder(java.util.List<String> order) {
        logger.info("[module] phase=START order=" + String.join(" -> ", order));
    }

    /** Logs a summary line for the whole phase. */
    public void logPhaseSummary(Phase phase, int moduleCount, Duration took) {
        logger.info(
                "[module] phase="
                        + phase
                        + " modules="
                        + moduleCount
                        + " took="
                        + took.toMillis()
                        + "ms");
    }

    private static String line(
            Phase phase,
            String moduleId,
            Module.LifecycleState state,
            Duration took,
            String detail) {
        StringBuilder sb =
                new StringBuilder("[module] id=")
                        .append(moduleId)
                        .append(" phase=")
                        .append(phase)
                        .append(" state=")
                        .append(state)
                        .append(" took=")
                        .append(took.toMillis())
                        .append("ms");
        if (detail != null) {
            sb.append(" detail=").append(detail);
        }
        return sb.toString();
    }
}
