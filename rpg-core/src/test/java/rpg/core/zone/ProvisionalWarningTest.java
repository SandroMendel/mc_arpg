package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigHandle;
import rpg.core.config.ConfigLoader;
import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigValidationException;
import rpg.core.config.SchemaValidator;
import rpg.core.event.EventBus;
import rpg.core.module.ModuleContext;
import rpg.core.module.ModuleRegistry;
import rpg.core.scheduler.Scheduler;

/**
 * T028 - the one notice about placeholder coordinates (FR-065a to FR-065c, SC-016).
 *
 * <p>The whole point of this requirement is that the notice <b>actually appears</b>, so the test goes
 * through {@link ZoneModule#start} rather than poking at a method. A header comment did not reach
 * anybody on B08b's T103; this asserts the replacement is where people look.
 */
class ProvisionalWarningTest {

    @Test
    @DisplayName("provisional: true warns once at start")
    void warnsAtStart() throws Exception {
        Capture log = new Capture();
        ZoneModule module = new ZoneModule(log.logger(), ZoneFixture.worlds(), ZoneFixture.allMessages());

        module.start(contextFor(ZoneFixture.document()));

        assertThat(log.warnings()).hasSize(1);
        assertThat(log.warnings().get(0))
                .contains("PROVISIONAL")
                .contains("PLACEHOLDERS")
                .contains("zones.yml");
    }

    @Test
    @DisplayName("a reload does NOT repeat it - the notice would stop being a signal")
    void reloadDoesNotRepeatIt() throws Exception {
        Capture log = new Capture();
        ZoneModule module = new ZoneModule(log.logger(), ZoneFixture.worlds(), ZoneFixture.allMessages());
        module.start(contextFor(ZoneFixture.document()));

        module.applyReloadedConfig();
        module.applyReloadedConfig();

        assertThat(log.warnings()).as("still exactly the one from start").hasSize(1);
    }

    @Test
    @DisplayName("without the flag nothing changes except the warning (FR-065c)")
    void withoutTheFlagOnlyTheWarningIsGone() throws Exception {
        Map<String, Object> document = ZoneFixture.document();
        document.put("provisional", Boolean.FALSE);
        Capture log = new Capture();
        ZoneModule module = new ZoneModule(log.logger(), ZoneFixture.worlds(), ZoneFixture.allMessages());

        module.start(contextFor(document));

        assertThat(log.warnings()).isEmpty();
        assertThat(module.zones().all()).as("same six regions").hasSize(6);
        assertThat(module.zones().startPoint()).isNotNull();
        assertThat(module.config().provisional()).isFalse();
    }

    @Test
    @DisplayName("a reload rebuilds the index and tells whoever asked to be told")
    void reloadRebuildsAndNotifies() throws Exception {
        Capture log = new Capture();
        ZoneModule module = new ZoneModule(log.logger(), ZoneFixture.worlds(), ZoneFixture.allMessages());
        module.start(contextFor(ZoneFixture.document()));
        List<String> told = new ArrayList<>();
        module.onReload(() -> told.add("re-evaluated"));

        Zones before = module.zones();
        module.applyReloadedConfig();

        assertThat(module.zones()).as("a whole new instance, swapped in").isNotSameAs(before);
        assertThat(told).containsExactly("re-evaluated");
    }

    @Test
    @DisplayName("a region without a name in messages.yml refuses the start (FR-003c)")
    void regionWithoutANameRefusesTheStart() {
        Capture log = new Capture();
        ZoneModule module =
                new ZoneModule(
                        log.logger(),
                        ZoneFixture.worlds(),
                        ZoneFixture.messagesMissing("dustlands"));

        assertThatThrownBy(() -> module.start(contextFor(ZoneFixture.document())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zone.dustlands.name")
                .hasMessageContaining("FR-003c");
    }

    // ------------------------------------------------------------------ fixture

    /** A context that offers nothing but the configuration loader - all this module uses. */
    private static ModuleContext contextFor(Map<String, Object> document) {
        ConfigLoader loader = new FixedLoader(document);
        return new ModuleContext() {
            @Override
            public String moduleId() {
                return ZoneModule.ID;
            }

            @Override
            public ModuleRegistry registry() {
                throw new UnsupportedOperationException("this module needs no registry");
            }

            @Override
            public EventBus eventBus() {
                throw new UnsupportedOperationException("this module needs no event bus");
            }

            @Override
            public Scheduler scheduler() {
                throw new UnsupportedOperationException(
                        "this block registers no task at all - Constitution II");
            }

            @Override
            public ConfigLoader configLoader() {
                return loader;
            }
        };
    }

    /** Binds one fixed document, so a reload returns the same valid configuration again. */
    private record FixedLoader(Map<String, Object> document) implements ConfigLoader {

        @Override
        public <T> T loadAndValidate(Path source, ConfigSchema<T> schema)
                throws ConfigValidationException {
            return schema.bind(SchemaValidator.validate(source, document, schema));
        }

        @Override
        public <T> ConfigHandle<T> register(Path source, ConfigSchema<T> schema)
                throws ConfigValidationException {
            T value = loadAndValidate(source, schema);
            return new ConfigHandle<>() {
                @Override
                public T get() {
                    return value;
                }

                @Override
                public Path source() {
                    return source;
                }
            };
        }

        @Override
        public void reloadAll() {
            // nothing to do: the document does not change in this test
        }
    }

    /** Collects warnings so the test can assert the notice exists and appears once. */
    private static final class Capture extends Handler {

        private final List<String> warnings = new ArrayList<>();
        private final Logger logger = Logger.getLogger("zone-test-" + System.identityHashCode(this));

        Capture() {
            logger.setUseParentHandlers(false);
            logger.addHandler(this);
        }

        Logger logger() {
            return logger;
        }

        List<String> warnings() {
            return warnings;
        }

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                warnings.add(record.getMessage());
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }
}
