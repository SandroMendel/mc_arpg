package rpg.core.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigHandle;
import rpg.core.config.ConfigLoader;
import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;
import rpg.core.event.EventBus;
import rpg.core.module.ModuleContext;
import rpg.core.module.ModuleRegistry;
import rpg.core.scheduler.Scheduler;

/**
 * Start und Nachladen von {@link UiModule} — nach dem Muster von
 * {@code StatisticsModuleReloadTest} und {@code MobModuleReloadTest}.
 *
 * <h2>Was hier bewiesen wird</h2>
 *
 * <ul>
 *   <li><b>Eine unbrauchbare Konfiguration verhindert den Start</b>, statt später aufzufallen.
 *   <li><b>Nachladen tauscht das Ganze aus</b> — und {@link UiModule#config()} gibt danach die
 *       <em>neuen</em> Werte zurück. Das ist die Hälfte von FR-013c; die andere Hälfte — dass der
 *       laufende Takt sie auch abholt — gehört zu {@code ReloadReachesTickTest} in
 *       {@code rpg-platform}, weil erst dort ein Takt existiert.
 *   <li><b>Der Block hält keinen Bestand.</b> {@code stop()} hat nichts zu tun, und das ist eine
 *       Zusage (FR-013b).
 * </ul>
 */
class UiModuleTest {

    @Test
    @DisplayName("der Start laedt die Konfiguration und meldet sie")
    void startLoadsTheConfiguration() {
        StubLoader loader = new StubLoader(document());
        UiModule module = new UiModule(Logger.getLogger("test"), noAbilities());

        module.start(new StubContext(loader));

        assertThat(module.config().language()).isEqualTo("en");
        assertThat(module.config().tick().toMillis()).isEqualTo(1000);
        assertThat(module.config().isEnabled(HudSurface.SIDEBAR)).isTrue();
    }

    @Test
    @DisplayName("eine kaputte Datei bricht den Start ab und die Meldung nennt den Schluessel")
    void abrokenFileAbortsTheStart() {
        Map<String, Object> broken = document();
        hud(broken).put("tick-ms", 0);

        StubLoader loader = new StubLoader(broken);
        UiModule module = new UiModule(Logger.getLogger("test"), noAbilities());

        assertThatThrownBy(() -> module.start(new StubContext(loader)))
                .hasMessageContaining("hud.tick-ms");
    }

    @Test
    @DisplayName("config() vor dem Start wirft, statt still null zu liefern")
    void configBeforeStartThrows() {
        // Ein null hier waere ein NullPointerException irgendwo im Takt, eine Sekunde spaeter und
        // ohne Bezug zur Ursache.
        assertThatThrownBy(() -> new UiModule(Logger.getLogger("test"), noAbilities()).config())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not started");
    }

    @Test
    @DisplayName("Nachladen tauscht die Konfiguration im Ganzen")
    void reloadSwapsTheWholeConfiguration() {
        StubLoader loader = new StubLoader(document());
        UiModule module = new UiModule(Logger.getLogger("test"), noAbilities());
        module.start(new StubContext(loader));

        Map<String, Object> next = document();
        hud(next).put("tick-ms", 2000);
        sidebar(next).put("enabled", false);
        damageNumbers(next).put("enabled", false);
        loader.replace(next);

        module.applyReloadedConfig();

        assertThat(module.config().tick().toMillis()).isEqualTo(2000);
        assertThat(module.config().isEnabled(HudSurface.SIDEBAR)).isFalse();
        assertThat(module.config().damageNumbers().enabled()).isFalse();
        // Und was NICHT mitwandert, wandert auch wirklich nicht mit:
        assertThat(module.config().isEnabled(HudSurface.ACTION_BAR)).isTrue();
    }

    @Test
    @DisplayName("Nachladen benachrichtigt die angemeldeten Zuhoerer")
    void reloadNotifiesListeners() {
        StubLoader loader = new StubLoader(document());
        UiModule module = new UiModule(Logger.getLogger("test"), noAbilities());
        module.start(new StubContext(loader));

        AtomicInteger calls = new AtomicInteger();
        module.onReload(calls::incrementAndGet);

        module.applyReloadedConfig();
        module.applyReloadedConfig();

        assertThat(calls).hasValue(2);
    }

    @Test
    @DisplayName("Nachladen vor dem Start tut nichts, statt zu werfen")
    void reloadBeforeStartIsQuiet() {
        // Das Nachladen laeuft ueber alle Module; eines, das noch nicht dran war, darf den Durchlauf
        // fuer die anderen nicht abbrechen.
        UiModule module = new UiModule(Logger.getLogger("test"), noAbilities());

        module.applyReloadedConfig();
    }

    @Test
    @DisplayName("stop() haelt nichts fest - der Block laesst sich rueckstandslos entfernen")
    void stopHoldsNothing() {
        // SC-011 in seiner kleinsten Form: kein Bestand, also nichts zu leeren. Der Test steht hier,
        // damit ein spaeterer Zustand im Modul auffaellt, statt sich einzuschleichen.
        StubLoader loader = new StubLoader(document());
        UiModule module = new UiModule(Logger.getLogger("test"), noAbilities());
        module.start(new StubContext(loader));

        module.stop();

        assertThat(module.config()).isNotNull();
    }

    @Test
    @DisplayName("T095: eine Materialdoppelung bricht den Start ab")
    void amaterialClashAbortsTheStart() {
        // FR-032, und sie laeuft beim START und nicht zur Laufzeit: im Spiel waere es zu spaet -
        // der Spieler hat die Klasse schon gewaehlt, die Leiste liegt schon, und eine Meldung an
        // ihn hilft ihm nicht, weil er die Datei nicht aendern kann.
        StubLoader loader = new StubLoader(document());
        UiModule module =
                new UiModule(
                        Logger.getLogger("test"),
                        () ->
                                characterClass ->
                                        characterClass == rpg.core.session.CharacterClass.WARRIOR
                                                ? List.of(
                                                        new MaterialUniqueness.SlotUse(
                                                                "cleave", List.of("IRON_SWORD")),
                                                        new MaterialUniqueness.SlotUse(
                                                                "bash", List.of("IRON_SWORD")))
                                                : List.of());

        assertThatThrownBy(() -> module.start(new StubContext(loader)))
                .hasMessageContaining("IRON_SWORD")
                .hasMessageContaining("FR-032");
    }

    @Test
    @DisplayName("T095: die Pruefung laeuft NACH dem Laden der Konfiguration")
    void thecheckRunsAfterTheConfigurationIsLoaded() {
        // Andersherum wuerde eine kaputte ui.yml von einer Materialdoppelung verdeckt - und der
        // Betreiber bekaeme die zweite Meldung erst, nachdem er die erste behoben hat.
        Map<String, Object> broken = document();
        hud(broken).put("tick-ms", 0);
        StubLoader loader = new StubLoader(broken);
        UiModule module =
                new UiModule(
                        Logger.getLogger("test"),
                        () -> {
                            throw new AssertionError(
                                    "die Materialpruefung darf gar nicht erst laufen, wenn die"
                                            + " Konfiguration schon abgelehnt ist");
                        });

        assertThatThrownBy(() -> module.start(new StubContext(loader)))
                .hasMessageContaining("hud.tick-ms");
    }

    // --- Aufbau -------------------------------------------------------------

    /**
     * Keine Fähigkeiten — für die Tests, in denen die Materialprüfung nicht das Prüfobjekt ist.
     *
     * <p>Eine leere Liste je Klasse ist hier <b>gültig</b> und nicht der Nullfall: eine Klasse ohne
     * Fähigkeiten kann sich keine Slots teilen. Dass die Prüfung dabei nichts findet, ist richtig
     * und nicht stillschweigend übergangen.
     */
    private static java.util.function.Supplier<
                    java.util.function.Function<
                            rpg.core.session.CharacterClass,
                            List<MaterialUniqueness.SlotUse>>>
            noAbilities() {
        return () -> characterClass -> List.of();
    }

    private static Map<String, Object> document() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("language", "en");

        Map<String, Object> hud = new LinkedHashMap<>();
        hud.put("tick-ms", 1000);
        hud.put("action-bar", new LinkedHashMap<>(Map.of("enabled", true)));
        Map<String, Object> bossBar = new LinkedHashMap<>();
        bossBar.put("enabled", true);
        bossBar.put("zone-notice-seconds", 4);
        hud.put("boss-bar", bossBar);
        hud.put("sidebar", new LinkedHashMap<>(Map.of("enabled", true)));
        doc.put("hud", hud);

        Map<String, Object> damageNumbers = new LinkedHashMap<>();
        damageNumbers.put("enabled", true);
        damageNumbers.put("lifetime-ms", 1200);
        damageNumbers.put("offset", 1.4);
        doc.put("damage-numbers", damageNumbers);

        return doc;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> hud(Map<String, Object> doc) {
        return (Map<String, Object>) doc.get("hud");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sidebar(Map<String, Object> doc) {
        return (Map<String, Object>) hud(doc).get("sidebar");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> damageNumbers(Map<String, Object> doc) {
        return (Map<String, Object>) doc.get("damage-numbers");
    }

    private static final class StubLoader implements ConfigLoader {

        private Map<String, Object> document;

        StubLoader(Map<String, Object> document) {
            this.document = document;
        }

        void replace(Map<String, Object> next) {
            this.document = next;
        }

        @Override
        public <T> T loadAndValidate(Path path, ConfigSchema<T> schema) {
            return schema.bind(new MapView(document, schema.schemaVersion()));
        }

        @Override
        public <T> ConfigHandle<T> register(Path path, ConfigSchema<T> schema) {
            return new ConfigHandle<T>() {

                @Override
                public T get() {
                    return schema.bind(new MapView(document, schema.schemaVersion()));
                }

                @Override
                public Path source() {
                    return path;
                }
            };
        }

        @Override
        public void reloadAll() {
            // Der Austausch geschieht ueber replace(...); ein echtes Neulesen von der Platte hat in
            // einem Modultest nichts zu suchen.
        }
    }

    private record StubContext(ConfigLoader loader) implements ModuleContext {

        @Override
        public String moduleId() {
            return UiModule.ID;
        }

        @Override
        public ModuleRegistry registry() {
            throw new UnsupportedOperationException("nicht benoetigt");
        }

        @Override
        public EventBus eventBus() {
            throw new UnsupportedOperationException("nicht benoetigt");
        }

        @Override
        public Scheduler scheduler() {
            throw new UnsupportedOperationException("nicht benoetigt");
        }

        @Override
        public ConfigLoader configLoader() {
            return loader;
        }
    }

    private record MapView(Map<String, Object> body, int schemaVersion) implements ConfigView {

        @Override
        public String getString(String path) {
            return String.valueOf(body.get(path));
        }

        @Override
        public boolean getBoolean(String path) {
            return Boolean.TRUE.equals(body.get(path));
        }

        @Override
        public int getInt(String path) {
            return ((Number) body.get(path)).intValue();
        }

        @Override
        public long getLong(String path) {
            return ((Number) body.get(path)).longValue();
        }

        @Override
        public double getDouble(String path) {
            return ((Number) body.get(path)).doubleValue();
        }

        @Override
        public List<?> getList(String path) {
            return (List<?>) body.get(path);
        }

        @Override
        public Map<?, ?> getMap(String path) {
            Object value = body.get(path);
            return value == null ? Map.of() : (Map<?, ?>) value;
        }
    }
}
