package rpg.core.module;

import java.util.List;
import java.util.Optional;

/**
 * The registry through which modules (B02-B17) register and resolve services, and through which the
 * bootstrap derives a deterministic start order.
 *
 * <p>No Bukkit reference at all: fully usable and testable without a running server (FR-015).
 *
 * <p>See {@code contracts/module-registry.md} for the behavioural contract. All methods are
 * thread-safe, but only meaningful during or after a completed bootstrap.
 */
public interface ModuleRegistry {

    /**
     * Registers a module under its identifier together with the identifiers it depends on.
     *
     * @throws DuplicateModuleIdException if the identifier is already taken - the existing
     *     registration is never silently overwritten (FR-001a)
     */
    void registerModule(String moduleId, List<String> dependencyModuleIds);

    /**
     * Returns the start order derived from the declared dependencies.
     *
     * <p>Deterministic: modules that are ready at the same time are ordered by their identifier, so
     * the result never depends on hash iteration order (FR-001).
     *
     * @throws CyclicDependencyException if the dependency graph contains a cycle; the message names
     *     the modules involved (FR-011)
     */
    List<String> resolveStartOrder();

    /**
     * Publishes {@code implementation} under {@code serviceInterface} on behalf of {@code moduleId}.
     *
     * <p>Consumers only ever see the interface, never the implementation type (FR-005).
     */
    <T> void registerService(String moduleId, Class<T> serviceInterface, T implementation);

    /**
     * Resolves a mandatory service.
     *
     * @throws ServiceNotRegisteredException if no module provides it - never returns {@code null}
     */
    <T> T getService(Class<T> serviceInterface);

    /** Resolves an optional service, returning {@link Optional#empty()} when it is absent. */
    <T> Optional<T> findService(Class<T> serviceInterface);
}
