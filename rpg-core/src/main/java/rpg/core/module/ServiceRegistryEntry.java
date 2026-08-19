package rpg.core.module;

import java.util.Objects;

/**
 * Links a service interface to the implementation currently providing it.
 *
 * <p>Consumers only ever see {@link #serviceInterface()}; the concrete implementation type stays
 * hidden (Constitution III.3 - no access to another module's internals).
 *
 * @param serviceInterface the public interface other modules resolve against
 * @param implementation the concrete instance, always referenced through {@code serviceInterface}
 * @param owningModuleId identifier of the module providing this service; used for diagnostics and
 *     for deregistration during shutdown
 * @param <T> the service type
 */
public record ServiceRegistryEntry<T>(
        Class<T> serviceInterface, T implementation, String owningModuleId) {

    public ServiceRegistryEntry {
        Objects.requireNonNull(serviceInterface, "serviceInterface");
        Objects.requireNonNull(implementation, "implementation");
        Objects.requireNonNull(owningModuleId, "owningModuleId");
        if (owningModuleId.isBlank()) {
            throw new IllegalArgumentException("owningModuleId must not be blank");
        }
        if (!serviceInterface.isInstance(implementation)) {
            throw new IllegalArgumentException(
                    "implementation "
                            + implementation.getClass().getName()
                            + " does not implement "
                            + serviceInterface.getName());
        }
    }
}
