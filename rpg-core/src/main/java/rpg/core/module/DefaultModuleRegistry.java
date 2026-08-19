package rpg.core.module;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection-free {@link ModuleRegistry} implementation.
 *
 * <p>Deliberately not a DI framework (research.md, "Dependency Injection / Service-Registry"): with
 * at most 17 modules the object graph is small, while a reflection-based container would add
 * bootstrap cost and shading risk inside the shared Bukkit classloader - and would hide exactly the
 * explicit dependency resolution FR-001/FR-011 require to be visible.
 *
 * <p>Lookups are plain map reads, so nothing here allocates or reflects on the hot path
 * (Constitution II).
 */
public final class DefaultModuleRegistry implements ModuleRegistry {

    /** Declared dependencies per module id. Sorted so iteration is deterministic (FR-001). */
    private final NavigableMapOfDependencies modules = new NavigableMapOfDependencies();

    private final Map<Class<?>, ServiceRegistryEntry<?>> services = new ConcurrentHashMap<>();

    @Override
    public void registerModule(String moduleId, List<String> dependencyModuleIds) {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(dependencyModuleIds, "dependencyModuleIds");
        if (moduleId.isBlank()) {
            throw new IllegalArgumentException("moduleId must not be blank");
        }
        modules.put(moduleId, List.copyOf(dependencyModuleIds));
    }

    @Override
    public List<String> resolveStartOrder() {
        Map<String, List<String>> graph = modules.snapshot();
        verifyAllDependenciesAreKnown(graph);

        // Kahn's algorithm. The ready set is a TreeMap-backed queue seeded and refilled in
        // identifier order, so when several modules become ready at the same time the result is
        // still fully deterministic instead of following hash iteration order.
        Map<String, Integer> remainingDependencies = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : graph.entrySet()) {
            remainingDependencies.put(entry.getKey(), entry.getValue().size());
            for (String dependency : entry.getValue()) {
                dependents.computeIfAbsent(dependency, key -> new ArrayList<>()).add(entry.getKey());
            }
        }

        Deque<String> ready = new ArrayDeque<>();
        new TreeMap<>(remainingDependencies)
                .forEach(
                        (moduleId, count) -> {
                            if (count == 0) {
                                ready.add(moduleId);
                            }
                        });

        List<String> order = new ArrayList<>(graph.size());
        while (!ready.isEmpty()) {
            String current = ready.poll();
            order.add(current);

            List<String> waiting = dependents.getOrDefault(current, List.of());
            List<String> becameReady = new ArrayList<>();
            for (String dependent : waiting) {
                if (remainingDependencies.merge(dependent, -1, Integer::sum) == 0) {
                    becameReady.add(dependent);
                }
            }
            // sort the newly ready modules before enqueuing them, keeping the order stable
            becameReady.sort(String::compareTo);
            ready.addAll(becameReady);
        }

        if (order.size() != graph.size()) {
            throw new CyclicDependencyException(remainingCycleParticipants(order, graph));
        }
        return List.copyOf(order);
    }

    private static void verifyAllDependenciesAreKnown(Map<String, List<String>> graph) {
        for (Map.Entry<String, List<String>> entry : graph.entrySet()) {
            for (String dependency : entry.getValue()) {
                if (!graph.containsKey(dependency)) {
                    throw new UnknownModuleDependencyException(entry.getKey(), dependency);
                }
            }
        }
    }

    /** Every module that could not be ordered, i.e. the cycle plus whatever hangs off it. */
    private static List<String> remainingCycleParticipants(
            Collection<String> ordered, Map<String, List<String>> graph) {
        Set<String> unresolved = new LinkedHashSet<>(graph.keySet());
        unresolved.removeAll(Set.copyOf(ordered));
        List<String> named = new ArrayList<>(unresolved);
        named.sort(String::compareTo);
        return named;
    }

    @Override
    public <T> void registerService(String moduleId, Class<T> serviceInterface, T implementation) {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(serviceInterface, "serviceInterface");
        Objects.requireNonNull(implementation, "implementation");
        services.put(
                serviceInterface,
                new ServiceRegistryEntry<>(serviceInterface, implementation, moduleId));
    }

    @Override
    public <T> T getService(Class<T> serviceInterface) {
        return this.<T>findService(serviceInterface)
                .orElseThrow(() -> new ServiceNotRegisteredException(serviceInterface));
    }

    @Override
    public <T> Optional<T> findService(Class<T> serviceInterface) {
        Objects.requireNonNull(serviceInterface, "serviceInterface");
        ServiceRegistryEntry<?> entry = services.get(serviceInterface);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(serviceInterface.cast(entry.implementation()));
    }

    /** Removes every service published by {@code moduleId}. Used on the shutdown path. */
    public void deregisterServicesOf(String moduleId) {
        Objects.requireNonNull(moduleId, "moduleId");
        services.entrySet().removeIf(entry -> entry.getValue().owningModuleId().equals(moduleId));
    }

    /** The registered module ids, in identifier order. */
    public List<String> registeredModuleIds() {
        return List.copyOf(modules.snapshot().keySet());
    }

    /**
     * Small guarded holder keeping registration thread-safe while rejecting duplicates.
     *
     * <p>Not a {@code ConcurrentHashMap} alone, because "reject if present" plus "iterate in
     * identifier order" has to be atomic against a concurrent registration.
     */
    private static final class NavigableMapOfDependencies {

        private final TreeMap<String, List<String>> byId = new TreeMap<>();

        synchronized void put(String moduleId, List<String> dependencies) {
            if (byId.containsKey(moduleId)) {
                throw new DuplicateModuleIdException(moduleId);
            }
            byId.put(moduleId, dependencies);
        }

        synchronized Map<String, List<String>> snapshot() {
            return new TreeMap<>(byId);
        }
    }
}
