package rpg.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * T035 / FR-004: a reload that fails validation must leave the previously valid configuration active
 * for <em>every</em> module - never a mix of old and new - and must not crash the server.
 */
class ConfigLoaderReloadFailureTest {

    private static final Path COMBAT = Path.of("config", "combat.yml");
    private static final Path ZONES = Path.of("config", "zones.yml");

    private static ConfigSchema<Integer> intSchema(String path) {
        return ConfigSchema.<Integer>builder(1)
                .required(path, FieldType.INTEGER)
                .boundTo(view -> view.getInt(path))
                .build();
    }

    private static Map<String, Object> document(String key, Object value) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put(key, value);
        return document;
    }

    @Test
    void aRejectedDocumentKeepsThePreviouslyValidValue() throws Exception {
        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(COMBAT, document("max-targets", 5));
        ConfigHandle<Integer> combat = loader.register(COMBAT, intSchema("max-targets"));

        loader.put(COMBAT, document("max-targets", "not a number"));

        ConfigValidationException rejected =
                catchThrowableOfType(ConfigValidationException.class, loader::reloadAll);

        assertThat(rejected).isNotNull();
        assertThat(rejected.documentPath()).isEqualTo("max-targets");
        assertThat(combat.get()).isEqualTo(5);
    }

    @Test
    void oneBadSourceRollsBackTheWholeReload() throws Exception {
        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(COMBAT, document("max-targets", 5));
        loader.put(ZONES, document("radius", 100));
        ConfigHandle<Integer> combat = loader.register(COMBAT, intSchema("max-targets"));
        ConfigHandle<Integer> zones = loader.register(ZONES, intSchema("radius"));

        // combat.yml is fine, zones.yml is broken
        loader.put(COMBAT, document("max-targets", 9));
        loader.put(ZONES, document("radius", "wide"));

        catchThrowableOfType(ConfigValidationException.class, loader::reloadAll);

        // the valid file must NOT be applied either - a mixed old/new state is exactly what FR-004
        // forbids
        assertThat(combat.get()).isEqualTo(5);
        assertThat(zones.get()).isEqualTo(100);
    }

    @Test
    void theOrderOfTheBrokenSourceDoesNotMatter() throws Exception {
        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(COMBAT, document("max-targets", 5));
        loader.put(ZONES, document("radius", 100));
        // register the broken-to-be source FIRST this time
        ConfigHandle<Integer> zones = loader.register(ZONES, intSchema("radius"));
        ConfigHandle<Integer> combat = loader.register(COMBAT, intSchema("max-targets"));

        loader.put(ZONES, document("radius", "wide"));
        loader.put(COMBAT, document("max-targets", 9));

        catchThrowableOfType(ConfigValidationException.class, loader::reloadAll);

        assertThat(zones.get()).isEqualTo(100);
        assertThat(combat.get()).isEqualTo(5);
    }

    @Test
    void aMissingRequiredFieldOnReloadIsAlsoRolledBack() throws Exception {
        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(COMBAT, document("max-targets", 5));
        ConfigHandle<Integer> combat = loader.register(COMBAT, intSchema("max-targets"));

        loader.put(COMBAT, new LinkedHashMap<>()); // operator deleted the key

        catchThrowableOfType(ConfigValidationException.class, loader::reloadAll);

        assertThat(combat.get()).isEqualTo(5);
    }

    @Test
    void anUnparsableFileOnReloadIsAlsoRolledBack() throws Exception {
        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(COMBAT, document("max-targets", 5));
        ConfigHandle<Integer> combat = loader.register(COMBAT, intSchema("max-targets"));

        loader.failParsing(COMBAT, new IllegalStateException("mapping values are not allowed here"));

        catchThrowableOfType(ConfigValidationException.class, loader::reloadAll);

        assertThat(combat.get()).isEqualTo(5);
    }

    @Test
    void aFailedReloadCanBeFollowedByASuccessfulOne() throws Exception {
        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(COMBAT, document("max-targets", 5));
        ConfigHandle<Integer> combat = loader.register(COMBAT, intSchema("max-targets"));

        loader.put(COMBAT, document("max-targets", "broken"));
        catchThrowableOfType(ConfigValidationException.class, loader::reloadAll);
        assertThat(combat.get()).isEqualTo(5);

        // the operator fixes the file and reloads again
        loader.put(COMBAT, document("max-targets", 12));
        assertThatCode(loader::reloadAll).doesNotThrowAnyException();
        assertThat(combat.get()).isEqualTo(12);
    }
}
