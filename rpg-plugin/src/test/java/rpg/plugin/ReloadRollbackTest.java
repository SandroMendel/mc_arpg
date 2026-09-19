package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import rpg.core.config.AbstractConfigLoader;
import rpg.core.config.ConfigHandle;
import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigValidationException;
import rpg.core.config.FieldType;

/** Server-free T029 regression: a hook failure cannot publish a partial reload. */
class ReloadRollbackTest {

    private static final Path SOURCE = Path.of("config", "reload.yml");

    @Test
    void pluginReloadOrchestrationRestoresTheOldGenerationBeforeCompensation() throws Exception {
        TestLoader loader = new TestLoader();
        loader.put(5);
        ConfigHandle<Integer> handle = loader.register(SOURCE, schema());
        AtomicReference<String> derivedState = new AtomicReference<>("old");

        loader.put(9);

        assertThatThrownBy(
                        () ->
                                RpgPlugin.reloadWithTransactionalHooks(
                                        loader,
                                        () -> {
                                            derivedState.set("new-" + handle.get());
                                            throw new IllegalStateException("simulated hook failure");
                                        },
                                        () -> derivedState.set("restored-" + handle.get())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated hook failure");

        assertThat(handle.get()).isEqualTo(5);
        assertThat(derivedState).hasValue("restored-5");
    }

    private static ConfigSchema<Integer> schema() {
        return ConfigSchema.<Integer>builder(1)
                .required("value", FieldType.INTEGER)
                .boundTo(view -> view.getInt("value"))
                .build();
    }

    private static final class TestLoader extends AbstractConfigLoader {

        private final Map<Path, Map<String, Object>> documents = new LinkedHashMap<>();

        void put(int value) {
            documents.put(SOURCE, Map.of("value", value));
        }

        @Override
        protected Map<String, Object> parse(Path source) throws ConfigValidationException {
            Map<String, Object> document = documents.get(source);
            if (document == null) {
                throw new ConfigValidationException(
                        source, "<document>", "an existing configuration file", "missing");
            }
            return document;
        }
    }
}
