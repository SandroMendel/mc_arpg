package rpg.core.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * T026 / FR-001a: two modules claiming the same identifier must abort the start with a message
 * naming the conflict - never silently overwrite one of them.
 */
class ModuleRegistryDuplicateTest {

    @Test
    void registeringAnIdentifierTwiceIsRejected() {
        ModuleRegistry registry = new DefaultModuleRegistry();
        registry.registerModule("stat-engine", List.of());

        DuplicateModuleIdException thrown =
                catchThrowableOfType(
                        DuplicateModuleIdException.class,
                        () -> registry.registerModule("stat-engine", List.of("zones")));

        assertThat(thrown).isNotNull();
        assertThat(thrown.moduleId()).isEqualTo("stat-engine");
        assertThat(thrown).hasMessageContaining("stat-engine");
    }

    @Test
    void theFirstRegistrationSurvivesTheRejectedOne() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        registry.registerModule("zones", List.of());
        registry.registerModule("mobs", List.of("zones"));

        catchThrowableOfType(
                DuplicateModuleIdException.class,
                // the rejected declaration would have introduced a cycle had it been applied
                () -> registry.registerModule("zones", List.of("mobs")));

        assertThat(registry.resolveStartOrder()).containsExactly("zones", "mobs");
    }

    @Test
    void aBlankIdentifierIsRejected() {
        ModuleRegistry registry = new DefaultModuleRegistry();

        assertThat(
                        catchThrowableOfType(
                                IllegalArgumentException.class,
                                () -> registry.registerModule("   ", List.of())))
                .isNotNull();
    }
}
