package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Start und Nachladen von {@link StatisticsModule} — nach dem Muster von
 * {@code ProvisionalWarningTest} und {@code MobModuleReloadTest}.
 *
 * <h2>Was hier bewiesen wird</h2>
 *
 * <ul>
 *   <li><b>Eine unbrauchbare Konfiguration verhindert den Start</b>, statt später aufzufallen.
 *   <li><b>Nachladen tauscht das Ganze aus</b> — die neue Schwelle gilt sofort.
 *   <li><b>Eine Belohnung mit unbekannter Vorlage bricht ab</b>, und zwar beim Start und beim
 *       Nachladen gleichermaßen. Diese Prüfung kann nicht im Schema stehen: dort ist B11 nicht
 *       bekannt.
 * </ul>
 *
 * <p><b>Was das Nachladen nicht anfassen darf</b> — ein eingefrorener Saisonendstand und ein
 * bereits angelegter Anspruch —, lässt sich hier noch nicht prüfen: beide entstehen erst in
 * Phase 7 (US5). Die Zusage steht so lange im Klassenkommentar von {@link StatisticsModule}, und
 * der Test dazu gehört zu den Aufgaben dieser Geschichte, nicht hierher. <b>Diese Lücke ist
 * benannt, damit sie nicht als erledigt gilt.</b>
 */
class StatisticsModuleReloadTest {

    private static final Set<String> TEMPLATES = Set.of("trim.ember", "potion.healing");

    @Test
    @DisplayName("der Start laedt die Konfiguration und meldet sie")
    void startLoadsTheConfiguration() {
        StubLoader loader = new StubLoader(document());
        StatisticsModule module = new StatisticsModule(Logger.getLogger("test"), () -> TEMPLATES);

        module.start(new StubContext(loader));

        assertThat(module.config().capture().killCreditShare()).isEqualTo(0.05);
        assertThat(module.config().seasons().all()).hasSize(1);
    }

    @Test
    @DisplayName("eine unbrauchbare Schwelle verhindert den Start")
    void anUnusableThresholdPreventsTheStart() {
        Map<String, Object> broken = document();
        capture(broken).put("kill-credit-share", 0.0);

        StubLoader loader = new StubLoader(broken);
        StatisticsModule module = new StatisticsModule(Logger.getLogger("test"), () -> TEMPLATES);

        assertThatThrownBy(() -> module.start(new StubContext(loader)))
                .hasMessageContaining("kill-credit-share");
    }

    @Test
    @DisplayName("eine unbekannte Belohnungsvorlage bricht den Start ab")
    void anUnknownRewardTemplateAbortsTheStart() {
        Map<String, Object> broken = document();
        rewards(broken).put(1, Map.of("items", List.of(Map.of("template", "trim.nonexistent"))));

        StubLoader loader = new StubLoader(broken);
        StatisticsModule module = new StatisticsModule(Logger.getLogger("test"), () -> TEMPLATES);

        assertThatThrownBy(() -> module.start(new StubContext(loader)))
                .hasMessageContaining("trim.nonexistent")
                .hasMessageContaining("rewards.1");
    }

    @Test
    @DisplayName("ohne geladene Vorlagen prueft der Start nicht stillschweigend nichts")
    void withoutLoadedTemplatesTheCheckDoesNotSilentlyPass() {
        // Eine Pruefung, die bei leerer Vorlagenliste einfach durchwinkt, prueft ab dem ersten
        // Verdrahtungsfehler gar nichts mehr - und niemand merkt es, weil sie gruen bleibt.
        StubLoader loader = new StubLoader(document());
        StatisticsModule module = new StatisticsModule(Logger.getLogger("test"), Set::of);

        assertThatThrownBy(() -> module.start(new StubContext(loader)))
                .hasMessageContaining("no item templates loaded");
    }

    @Test
    @DisplayName("Nachladen tauscht die Konfiguration im Ganzen und meldet es den Zuhoerern")
    void reloadingSwapsTheWholeConfiguration() {
        StubLoader loader = new StubLoader(document());
        StatisticsModule module = new StatisticsModule(Logger.getLogger("test"), () -> TEMPLATES);
        module.start(new StubContext(loader));

        AtomicInteger notified = new AtomicInteger();
        module.onReload(notified::incrementAndGet);

        Map<String, Object> changed = document();
        capture(changed).put("kill-credit-share", 0.25);
        loader.replace(changed);
        module.applyReloadedConfig();

        assertThat(module.config().capture().killCreditShare()).isEqualTo(0.25);
        assertThat(notified).hasValue(1);
    }

    @Test
    @DisplayName("Nachladen prueft die Belohnungsvorlagen erneut")
    void reloadingChecksRewardTemplatesAgain() {
        StubLoader loader = new StubLoader(document());
        StatisticsModule module = new StatisticsModule(Logger.getLogger("test"), () -> TEMPLATES);
        module.start(new StubContext(loader));

        Map<String, Object> changed = document();
        rewards(changed).put(1, Map.of("items", List.of(Map.of("template", "trim.gone"))));
        loader.replace(changed);

        assertThatThrownBy(module::applyReloadedConfig).hasMessageContaining("trim.gone");
    }

    @Test
    @DisplayName("das Modul haelt keinen eigenen Bestand - stop() raeumt nichts weg")
    void theModuleKeepsNoStoreOfItsOwn() {
        // FR-002. Was gezaehlt wurde, liegt in player_statistic_daily und gehoert B02; ein
        // eigener Bestand hier waere die zweite Wahrheit, gegen die dieser Block gebaut ist.
        StubLoader loader = new StubLoader(document());
        StatisticsModule module = new StatisticsModule(Logger.getLogger("test"), () -> TEMPLATES);
        module.start(new StubContext(loader));

        module.stop();

        assertThat(module.config().seasons().all()).hasSize(1);
    }

    // ------------------------------------------------------------------ Gerüst

    private static Map<String, Object> document() {
        Map<String, Object> doc = new LinkedHashMap<>();

        Map<String, Object> capture = new LinkedHashMap<>();
        capture.put("kill-credit-share", 0.05);
        capture.put("idle-after-seconds", 300);
        doc.put("capture", capture);

        Map<String, Object> leaderboards = new LinkedHashMap<>();
        leaderboards.put("refresh-interval-seconds", 300);
        leaderboards.put("places", 10);
        doc.put("leaderboards", leaderboards);

        List<Object> seasons = new ArrayList<>();
        Map<String, Object> season = new LinkedHashMap<>();
        season.put("key", "2026-q3");
        season.put("from", LocalDate.of(2026, 7, 1));
        season.put("to", LocalDate.of(2026, 9, 30));
        seasons.add(season);
        doc.put("seasons", seasons);

        Map<String, Object> weights = new LinkedHashMap<>();
        weights.put(Aggregation.MOB_KILLS.key(), 1);
        Map<String, Object> score = new LinkedHashMap<>();
        score.put("weights", weights);
        doc.put("score", score);

        Map<Object, Object> rewards = new LinkedHashMap<>();
        rewards.put(1, new LinkedHashMap<>(Map.of("coins", 1000)));
        doc.put("rewards", rewards);

        return doc;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> capture(Map<String, Object> doc) {
        return (Map<String, Object>) doc.get("capture");
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> rewards(Map<String, Object> doc) {
        return (Map<Object, Object>) doc.get("rewards");
    }

    /** Ein Loader, dessen Dokument sich austauschen lässt — mehr braucht dieses Modul nicht. */
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
            // Der Austausch geschieht hier ueber replace(...); ein echtes Neulesen von der Platte
            // hat in einem Modultest nichts zu suchen.
        }
    }

    private record StubContext(ConfigLoader loader) implements ModuleContext {

        @Override
        public String moduleId() {
            return StatisticsModule.ID;
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
