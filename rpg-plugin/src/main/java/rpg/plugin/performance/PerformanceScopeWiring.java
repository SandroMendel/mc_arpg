package rpg.plugin.performance;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import rpg.core.performance.MeasurementScope;
import rpg.core.performance.PerformanceRegistry;
import rpg.core.performance.PerformanceSnapshot;
import rpg.core.performance.SubsystemId;

/** Explicit B15 coverage matrix and the one adapter used by existing hot paths. */
public final class PerformanceScopeWiring {

    private static final List<Definition> DEFAULT_DEFINITIONS =
            List.of(
                    new Definition("b03-session-load", "B03", Duration.ofMillis(250)),
                    new Definition("b05-combat", "B05", Duration.ofMillis(5)),
                    new Definition("b08b-coin-drops", "B08b", Duration.ofMillis(2)),
                    new Definition("b09-zone-movement", "B09", Duration.ofMillis(2)),
                    new Definition("b10-hordes", "B10", Duration.ofMillis(5)),
                    new Definition("b12-statistics", "B12", Duration.ofMillis(2)),
                    new Definition("b13-hud", "B13", Duration.ofMillis(3)),
                    new Definition("b14-admin", "B14", Duration.ofMillis(1)));

    private final PerformanceRegistry registry;
    private final Map<SubsystemId, Definition> definitions = new LinkedHashMap<>();
    private final Map<SubsystemId, PerformanceConfig.ConfiguredBudget> configuredBudgets;

    public PerformanceScopeWiring(PerformanceRegistry registry) {
        this(registry, Map.of());
    }

    public PerformanceScopeWiring(
            PerformanceRegistry registry,
            Map<SubsystemId, PerformanceConfig.ConfiguredBudget> configuredBudgets) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.configuredBudgets = Map.copyOf(Objects.requireNonNull(configuredBudgets, "configuredBudgets"));
        for (Definition definition : DEFAULT_DEFINITIONS) {
            definitions.put(new SubsystemId(definition.id()), definition);
        }
    }

    public void registerExpected() {
        for (Definition definition : definitions.values()) {
            SubsystemId id = new SubsystemId(definition.id());
            PerformanceConfig.ConfiguredBudget configured = configuredBudgets.get(id);
            Duration budget = configured == null ? definition.budget() : configured.budget();
            String owner = configured == null ? definition.owner() : configured.owner();
            registry.register(id, budget, owner);
        }
    }

    public MeasurementScope begin(String id) {
        return registry.begin(new SubsystemId(id));
    }

    public Set<SubsystemId> expectedIds() {
        return Set.copyOf(definitions.keySet());
    }

    public void verifyExpected(PerformanceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Set<SubsystemId> missing =
                definitions.keySet().stream()
                        .filter(id -> !snapshot.subsystems().containsKey(id))
                        .collect(
                                Collectors.toCollection(
                                        () ->
                                                new java.util.TreeSet<>(
                                                        java.util.Comparator.comparing(
                                                                SubsystemId::value))));
        if (!missing.isEmpty()) {
            throw new IllegalStateException("missing B15 performance sources: " + missing);
        }
    }

    public record Definition(String id, String owner, Duration budget) {

        public Definition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(budget, "budget");
        }
    }
}
