package rpg.core.module;

import java.util.List;

/**
 * A registered building block of the plugin (for example {@code "stat-engine"} or
 * {@code "zones"}).
 *
 * <p>A module is identified by a stable, human-readable string identifier that is independent of
 * the implementing class name (FR-001a). Renaming or moving the implementation class therefore
 * never changes the module identity used by the registry, by dependency declarations or by log
 * output.
 *
 * <p>Implementations live in the individual architecture blocks (B02-B17). This interface is part
 * of the internal extension boundary described by FR-014: it is deliberately kept small so it can
 * later be exposed as a public third-party API without restructuring existing modules.
 */
public interface Module {

    /**
     * The stable, unique identifier of this module.
     *
     * <p>Must not be blank and must stay unique across the whole bootstrap. Two modules registering
     * the same identifier abort the start with a {@link DuplicateModuleIdException}.
     */
    String id();

    /**
     * Identifiers of the modules that must be started before this one.
     *
     * <p>The declared graph must be acyclic; a cycle aborts the start with a
     * {@link CyclicDependencyException} naming the modules involved (FR-011).
     */
    default List<String> dependencies() {
        return List.of();
    }

    /**
     * Initialises the module. Called exactly once during bootstrap, in the deterministic order
     * derived from {@link #dependencies()}.
     *
     * <p>Any exception thrown here marks the module {@link LifecycleState#FAILED} and aborts the
     * bootstrap (fail-fast, FR-013).
     */
    void start(ModuleContext context) throws Exception;

    /**
     * Releases the resources held by this module. Called during shutdown in reverse start order and
     * bounded by a per-module timeout of 10 seconds (FR-012).
     */
    default void stop() throws Exception {
        // no-op by default: not every module holds resources
    }

    /**
     * Lifecycle states a module passes through.
     *
     * <p>Permitted transitions (see research.md, "Modul-Lifecycle-Zustände"):
     *
     * <pre>
     *   INITIALIZING -&gt; ACTIVE
     *   INITIALIZING -&gt; FAILED
     *   ACTIVE       -&gt; STOPPING -&gt; STOPPED
     * </pre>
     */
    enum LifecycleState {
        /** Registered and currently being started. */
        INITIALIZING,
        /** Successfully started and in service. */
        ACTIVE,
        /** Start failed; blocks the system from becoming operational (FR-013). */
        FAILED,
        /** Shutdown requested, termination in progress. */
        STOPPING,
        /** Terminated - either cleanly or by force after the 10 second timeout. */
        STOPPED
    }
}
