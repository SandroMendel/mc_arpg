package rpg.core.module;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * T014 / FR-001: the start order must be derived from the declared dependencies and must be
 * deterministic.
 *
 * <p>Runs without a server (Constitution VII.1, FR-015) - nothing here touches Bukkit.
 */
class ModuleRegistryStartOrderTest {

    @Test
    void dependenciesStartBeforeTheModulesThatDeclareThem() {
        ModuleRegistry registry = new DefaultModuleRegistry();
        registry.registerModule("combat", List.of("stat-engine"));
        registry.registerModule("stat-engine", List.of("persistence"));
        registry.registerModule("persistence", List.of());

        List<String> order = registry.resolveStartOrder();

        assertThat(order).containsExactly("persistence", "stat-engine", "combat");
    }

    @Test
    void independentModulesAreOrderedByIdSoTheResultIsDeterministic() {
        // Registration order is deliberately not alphabetical: if the implementation leaked hash or
        // insertion order, this would come out as zones/abilities/... instead.
        ModuleRegistry registry = new DefaultModuleRegistry();
        registry.registerModule("zones", List.of());
        registry.registerModule("abilities", List.of());
        registry.registerModule("mobs", List.of());
        registry.registerModule("items", List.of());

        assertThat(registry.resolveStartOrder())
                .containsExactly("abilities", "items", "mobs", "zones");
    }

    @Test
    void repeatedResolutionYieldsTheSameOrder() {
        ModuleRegistry registry = new DefaultModuleRegistry();
        registry.registerModule("c", List.of("a"));
        registry.registerModule("b", List.of("a"));
        registry.registerModule("a", List.of());
        registry.registerModule("d", List.of("b", "c"));

        List<String> first = registry.resolveStartOrder();
        List<String> second = registry.resolveStartOrder();

        assertThat(first).containsExactly("a", "b", "c", "d");
        assertThat(second).isEqualTo(first);
    }

    @Test
    void aDependencyOnAnUnregisteredModuleIsReported() {
        ModuleRegistry registry = new DefaultModuleRegistry();
        registry.registerModule("combat", List.of("stat-engine"));

        assertThatThrownByResolving(registry);
    }

    private static void assertThatThrownByResolving(ModuleRegistry registry) {
        org.assertj.core.api.Assertions.assertThatThrownBy(registry::resolveStartOrder)
                .isInstanceOf(UnknownModuleDependencyException.class)
                .hasMessageContaining("combat")
                .hasMessageContaining("stat-engine");
    }
}
