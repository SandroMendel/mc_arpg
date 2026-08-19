package rpg.core.module;

import rpg.core.config.ConfigLoader;
import rpg.core.event.EventBus;
import rpg.core.scheduler.Scheduler;

/**
 * Everything a module is allowed to reach during {@link Module#start(ModuleContext)}.
 *
 * <p>This is the internal extension boundary required by FR-014. A module never reaches for a
 * global singleton and never imports another module's classes; it receives exactly these four
 * capabilities and resolves everything else through the registry. Because the surface is this
 * narrow, it can later be published as a third-party API without restructuring existing modules.
 */
public interface ModuleContext {

    /** Identifier of the module this context was handed to. */
    String moduleId();

    /** Register services for, and resolve services from, other modules (FR-005). */
    ModuleRegistry registry();

    /** Publish and subscribe to internal events without referencing the other side (FR-006). */
    EventBus eventBus();

    /** Schedule work; offers location-/entity-bound sync and off-tick async only (FR-007/FR-008). */
    Scheduler scheduler();

    /** Load and validate configuration against a declared schema (FR-002). */
    ConfigLoader configLoader();
}
