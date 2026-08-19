package rpg.core.module;

import java.util.Objects;

import rpg.core.config.ConfigLoader;
import rpg.core.event.EventBus;
import rpg.core.scheduler.Scheduler;

/**
 * The {@link ModuleContext} handed to one specific module during {@link Module#start(ModuleContext)}.
 *
 * <p>Every module gets its own instance so {@link #moduleId()} is correct without the module having
 * to repeat its own identifier; the four capabilities behind it are shared.
 */
public final class DefaultModuleContext implements ModuleContext {

    private final String moduleId;
    private final ModuleRegistry registry;
    private final EventBus eventBus;
    private final Scheduler scheduler;
    private final ConfigLoader configLoader;

    public DefaultModuleContext(
            String moduleId,
            ModuleRegistry registry,
            EventBus eventBus,
            Scheduler scheduler,
            ConfigLoader configLoader) {
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.configLoader = Objects.requireNonNull(configLoader, "configLoader");
    }

    @Override
    public String moduleId() {
        return moduleId;
    }

    @Override
    public ModuleRegistry registry() {
        return registry;
    }

    @Override
    public EventBus eventBus() {
        return eventBus;
    }

    @Override
    public Scheduler scheduler() {
        return scheduler;
    }

    @Override
    public ConfigLoader configLoader() {
        return configLoader;
    }
}
