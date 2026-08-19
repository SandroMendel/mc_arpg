package rpg.core.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * T015 / FR-011: a cyclic dependency must abort the start with a message naming the modules
 * involved - never an endless loop and never an arbitrary order.
 */
class ModuleRegistryCycleTest {

    @Test
    void aTwoModuleCycleIsRejectedAndBothModulesAreNamed() {
        ModuleRegistry registry = new DefaultModuleRegistry();
        registry.registerModule("a", List.of("b"));
        registry.registerModule("b", List.of("a"));

        CyclicDependencyException thrown =
                catchThrowableOfType(CyclicDependencyException.class, registry::resolveStartOrder);

        assertThat(thrown).isNotNull();
        assertThat(thrown.involvedModuleIds()).containsExactly("a", "b");
        assertThat(thrown).hasMessageContaining("a").hasMessageContaining("b");
    }

    @Test
    void aLongerCycleIsRejectedAndEveryParticipantIsNamed() {
        ModuleRegistry registry = new DefaultModuleRegistry();
        registry.registerModule("zones", List.of("mobs"));
        registry.registerModule("mobs", List.of("loot"));
        registry.registerModule("loot", List.of("zones"));

        CyclicDependencyException thrown =
                catchThrowableOfType(CyclicDependencyException.class, registry::resolveStartOrder);

        assertThat(thrown.involvedModuleIds()).containsExactly("loot", "mobs", "zones");
    }

    @Test
    void modulesOutsideTheCycleAreNotBlamed() {
        ModuleRegistry registry = new DefaultModuleRegistry();
        registry.registerModule("clean", List.of());
        registry.registerModule("a", List.of("b"));
        registry.registerModule("b", List.of("a"));

        CyclicDependencyException thrown =
                catchThrowableOfType(CyclicDependencyException.class, registry::resolveStartOrder);

        assertThat(thrown.involvedModuleIds()).containsExactly("a", "b").doesNotContain("clean");
    }

    @Test
    void aSelfDependencyIsACycleToo() {
        ModuleRegistry registry = new DefaultModuleRegistry();
        registry.registerModule("lonely", List.of("lonely"));

        assertThatThrownBy(registry::resolveStartOrder)
                .isInstanceOf(CyclicDependencyException.class)
                .hasMessageContaining("lonely");
    }
}
