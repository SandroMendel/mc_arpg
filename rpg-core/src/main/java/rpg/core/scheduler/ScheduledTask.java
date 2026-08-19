package rpg.core.scheduler;

import java.util.Objects;
import java.util.Optional;

/**
 * Descriptor of a unit of work submitted through the {@link Scheduler} abstraction.
 *
 * <p>The descriptor exists for diagnostics and for asserting the scheduling rules of ADR-007 in
 * tests. The rules themselves are enforced by the {@link Scheduler} interface, which offers no
 * method accepting a synchronous task without a location or entity binding.
 *
 * @param executionMode whether the task runs on the server tick or off it
 * @param binding the tick binding; present for {@link ExecutionMode#SYNC}, empty for
 *     {@link ExecutionMode#ASYNC}, which is deliberately not location-bound
 * @param submittedByModuleId identifier of the submitting module, for diagnostics
 */
public record ScheduledTask(
        ExecutionMode executionMode, Optional<Binding> binding, String submittedByModuleId) {

    public ScheduledTask {
        Objects.requireNonNull(executionMode, "executionMode");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(submittedByModuleId, "submittedByModuleId");
        if (executionMode == ExecutionMode.SYNC && binding.isEmpty()) {
            throw new IllegalArgumentException(
                    "a SYNC task must declare a LOCATION or ENTITY binding (ADR-007)");
        }
        if (executionMode == ExecutionMode.ASYNC && binding.isPresent()) {
            throw new IllegalArgumentException("an ASYNC task must not declare a tick binding");
        }
    }

    public static ScheduledTask sync(Binding binding, String submittedByModuleId) {
        return new ScheduledTask(
                ExecutionMode.SYNC, Optional.of(binding), submittedByModuleId);
    }

    public static ScheduledTask async(String submittedByModuleId) {
        return new ScheduledTask(ExecutionMode.ASYNC, Optional.empty(), submittedByModuleId);
    }

    /**
     * Distinguishes tick-bound from off-tick work.
     *
     * <p>Per FR-007 this distinction is visible in the type system: the {@link Scheduler} methods
     * differ by name and parameter type, not by a boolean flag.
     */
    public enum ExecutionMode {
        /** Runs inside the server tick; may touch the Paper/Bukkit API. */
        SYNC,
        /** Runs off the server tick; must never touch the Paper/Bukkit API (Constitution I.1). */
        ASYNC
    }

    /**
     * How a synchronous task is bound to the tick.
     *
     * <p>There is deliberately no third, unbound/global value: the public {@link Scheduler} surface
     * does not offer it syntactically, which is what keeps the Folia migration path open (ADR-007).
     */
    public enum Binding {
        /** Bound to a world position, i.e. to the region owning that position. */
        LOCATION,
        /** Bound to a specific entity, and thus to whichever region currently owns it. */
        ENTITY
    }
}
