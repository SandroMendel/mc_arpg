package rpg.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/** T016: a complete multi-source value shares the same committed generation as ordinary handles. */
class ConfigLoaderBatchAtomicityTest {

    private static final Path ORDINARY = Path.of("config", "ordinary.yml");
    private static final Path FIRST = Path.of("config", "first.yml");
    private static final Path SECOND = Path.of("config", "second.yml");

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
    void batchAndOrdinaryHandlesCommitAsOneGeneration() throws Exception {
        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(ORDINARY, document("value", 5));

        ConfigHandle<Integer> ordinary = loader.register(ORDINARY, intSchema("value"));
        AtomicReference<String> batchValue = new AtomicReference<>("old");
        ConfigHandle<String> batch =
                loader.registerBatch(List.of(FIRST, SECOND), batchValue::get);

        loader.put(ORDINARY, document("value", 9));
        batchValue.set("new");
        loader.reloadAll();

        assertThat(ordinary.get()).isEqualTo(9);
        assertThat(batch.get()).isEqualTo("new");
        assertThat(batch.source()).isEqualTo(FIRST);
        assertThat(batch.sources()).containsExactly(FIRST, SECOND);
    }

    @Test
    void aFailedBatchLeavesEveryPreviousHandleAndGenerationUntouched() throws Exception {
        InMemoryConfigLoader loader = new InMemoryConfigLoader();
        loader.put(ORDINARY, document("value", 5));

        ConfigHandle<Integer> ordinary = loader.register(ORDINARY, intSchema("value"));
        AtomicBoolean reject = new AtomicBoolean();
        AtomicReference<String> batchValue = new AtomicReference<>("old");
        ConfigHandle<String> batch =
                loader.registerBatch(
                        List.of(FIRST, SECOND),
                        () -> {
                            if (reject.get()) {
                                throw new ConfigValidationException(
                                        SECOND,
                                        "<document>",
                                        "a complete B16 generation",
                                        "reference rejected");
                            }
                            return batchValue.get();
                        });

        loader.put(ORDINARY, document("value", 9));
        batchValue.set("new");
        loader.reloadAll();
        assertThat(ordinary.get()).isEqualTo(9);
        assertThat(batch.get()).isEqualTo("new");

        loader.put(ORDINARY, document("value", 12));
        reject.set(true);

        assertThatThrownBy(loader::reloadAll).isInstanceOf(ConfigValidationException.class);
        assertThat(ordinary.get()).isEqualTo(9);
        assertThat(batch.get()).isEqualTo("new");
    }
}
