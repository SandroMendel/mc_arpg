package rpg.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * T034 / FR-003: a reload is global - every module's configuration is reloaded at once, not
 * selectively per module (clarification 2026-08-19).
 */
class ConfigLoaderReloadTest {

    private static final Path COMBAT = Path.of("config", "combat.yml");
    private static final Path ZONES = Path.of("config", "zones.yml");
    private static final Path MOBS = Path.of("config", "mobs.yml");

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
    void everyRegisteredSourceIsReloadedByASingleCall() throws Exception {
        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(COMBAT, document("max-targets", 5));
        loader.put(ZONES, document("radius", 100));
        loader.put(MOBS, document("cap", 800));

        ConfigHandle<Integer> combat = loader.register(COMBAT, intSchema("max-targets"));
        ConfigHandle<Integer> zones = loader.register(ZONES, intSchema("radius"));
        ConfigHandle<Integer> mobs = loader.register(MOBS, intSchema("cap"));

        assertThat(combat.get()).isEqualTo(5);
        assertThat(zones.get()).isEqualTo(100);
        assertThat(mobs.get()).isEqualTo(800);

        // the operator edits all three files, then triggers one reload
        loader.put(COMBAT, document("max-targets", 9));
        loader.put(ZONES, document("radius", 250));
        loader.put(MOBS, document("cap", 1200));

        loader.reloadAll();

        // all three take effect together - no module is left on the old value
        assertThat(combat.get()).isEqualTo(9);
        assertThat(zones.get()).isEqualTo(250);
        assertThat(mobs.get()).isEqualTo(1200);
    }

    @Test
    void aHandleReflectsTheNewValueWithoutBeingReRegistered() throws Exception {
        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(COMBAT, document("max-targets", 1));
        ConfigHandle<Integer> handle = loader.register(COMBAT, intSchema("max-targets"));

        loader.put(COMBAT, document("max-targets", 2));
        loader.reloadAll();
        assertThat(handle.get()).isEqualTo(2);

        loader.put(COMBAT, document("max-targets", 3));
        loader.reloadAll();
        assertThat(handle.get()).isEqualTo(3);
    }

    @Test
    void aOneOffLoadDoesNotTakePartInReloads() throws Exception {
        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(COMBAT, document("max-targets", 5));

        // loadAndValidate reads once; only register() enrols a source in reloadAll()
        assertThat(loader.loadAndValidate(COMBAT, intSchema("max-targets"))).isEqualTo(5);
        assertThat(loader.registeredSources()).isEmpty();

        loader.register(COMBAT, intSchema("max-targets"));
        assertThat(loader.registeredSources()).containsExactly(COMBAT);
    }

    @Test
    void reloadingWithoutAnyRegisteredSourceIsHarmless() throws Exception {
        InMemoryConfigLoader loader = new InMemoryConfigLoader();

        loader.reloadAll();

        assertThat(loader.registeredSources()).isEmpty();
    }

    @Test
    void anUnchangedSourceKeepsItsValueAcrossAReload() throws Exception {
        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(COMBAT, document("max-targets", 5));
        loader.put(ZONES, document("radius", 100));
        ConfigHandle<Integer> combat = loader.register(COMBAT, intSchema("max-targets"));
        ConfigHandle<Integer> zones = loader.register(ZONES, intSchema("radius"));

        loader.put(ZONES, document("radius", 300));
        loader.reloadAll();

        assertThat(combat.get()).isEqualTo(5);
        assertThat(zones.get()).isEqualTo(300);
    }
}
