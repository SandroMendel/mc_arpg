package rpg.core.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * T025 / FR-005: a module resolves another module's service through the registry, without statically
 * referencing the providing module.
 *
 * <p>The test itself demonstrates the property it asserts: the consumer side only ever names the
 * interface, never the implementation class.
 */
class ModuleRegistryServiceTest {

    /** Public interface a module publishes - the only thing consumers are allowed to know. */
    interface DamageCalculator {
        double calculate(double base);
    }

    /** Implementation detail of the providing module; a consumer must never need this type. */
    private static final class ScalingDamageCalculator implements DamageCalculator {
        @Override
        public double calculate(double base) {
            return base * 1.5d;
        }
    }

    interface LootTable {}

    @Test
    void aConsumerResolvesAServiceKnowingOnlyItsInterface() {
        ModuleRegistry registry = new DefaultModuleRegistry();
        registry.registerService("combat", DamageCalculator.class, new ScalingDamageCalculator());

        DamageCalculator resolved = registry.getService(DamageCalculator.class);

        assertThat(resolved.calculate(10d)).isEqualTo(15d);
    }

    @Test
    void aMissingMandatoryServiceThrowsInsteadOfReturningNull() {
        ModuleRegistry registry = new DefaultModuleRegistry();

        assertThatThrownBy(() -> registry.getService(LootTable.class))
                .isInstanceOf(ServiceNotRegisteredException.class)
                .hasMessageContaining("LootTable");
    }

    @Test
    void aMissingOptionalServiceYieldsAnEmptyOptional() {
        ModuleRegistry registry = new DefaultModuleRegistry();

        Optional<LootTable> resolved = registry.findService(LootTable.class);

        assertThat(resolved).isEmpty();
    }

    @Test
    void aPresentOptionalServiceIsReturned() {
        ModuleRegistry registry = new DefaultModuleRegistry();
        LootTable table = new LootTable() {};
        registry.registerService("loot", LootTable.class, table);

        assertThat(registry.findService(LootTable.class)).containsSame(table);
    }

    @Test
    void theEntryRemembersWhichModuleProvidedTheService() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        registry.registerService("combat", DamageCalculator.class, new ScalingDamageCalculator());

        registry.deregisterServicesOf("zones");
        assertThat(registry.findService(DamageCalculator.class)).isPresent();

        registry.deregisterServicesOf("combat");
        assertThat(registry.findService(DamageCalculator.class)).isEmpty();
    }

    @Test
    void anImplementationNotSatisfyingTheInterfaceIsRejected() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();

        // reproduces what a raw-typed caller from another block could otherwise smuggle in
        @SuppressWarnings({"unchecked", "rawtypes"})
        Runnable attempt =
                () -> ((ModuleRegistry) registry).registerService("bad", (Class) LootTable.class, "not a loot table");

        assertThatThrownBy(attempt::run).isInstanceOf(IllegalArgumentException.class);
    }
}
