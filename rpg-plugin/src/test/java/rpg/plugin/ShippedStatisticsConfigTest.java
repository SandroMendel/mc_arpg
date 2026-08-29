package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;
import rpg.core.item.ItemConfig;
import rpg.core.item.ItemConfigSchema;
import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.statistics.Aggregation;
import rpg.core.statistics.SeasonCalendar;
import rpg.core.statistics.StatisticsConfig;
import rpg.core.statistics.StatisticsConfigSchema;
import rpg.core.statistics.StatisticsMessageKeys;

/**
 * Die {@code statistics.yml}, die tatsächlich ausgeliefert wird, muss ihr eigenes Schema bestehen.
 *
 * <p><b>Warum das hier liegt und nicht in {@code rpg-core}</b>, wo die Aufgabenliste es vorgesehen
 * hatte: die ausgelieferte Ressource steht nur auf <em>diesem</em> Klassenpfad. Dieselbe
 * Begründung, aus der {@code ShippedItemConfigTest}, {@code ShippedMobConfigTest} und
 * {@code ShippedZoneConfigTest} hier liegen. Kein Schematest in {@code rpg-core} baut die echte
 * Datei — sie konstruieren alle ihr eigenes Dokument, und eine kaputte ausgelieferte Datei segelte
 * an jedem einzelnen vorbei, um erst beim Serverstart aufzufallen.
 *
 * <p><b>Drei Prüfungen, die das Schema allein nicht leisten kann</b>, weil ihnen dort das Wissen
 * fehlt: dass die Saisons den heutigen Tag überhaupt abdecken, dass jede Belohnungsvorlage in
 * {@code items.yml} existiert, und dass jeder Textschlüssel in {@code messages.yml} auflöst.
 */
class ShippedStatisticsConfigTest {

    @Test
    @DisplayName("die ausgelieferte statistics.yml besteht das Schema, fuer das sie geschrieben ist")
    void shippedConfigurationIsValid() throws Exception {
        StatisticsConfig config = loadStatistics();

        assertThat(config.capture().killCreditShare()).isStrictlyBetween(0.0, 1.0);
        assertThat(config.leaderboards().places()).isPositive();
        assertThat(config.seasons().all()).as("mindestens eine Saison").isNotEmpty();
        assertThat(config.score().weights()).as("mindestens ein Gewicht").isNotEmpty();
    }

    @Test
    @DisplayName("der Saisonkalender deckt den heutigen Tag ab")
    void theSeasonCalendarCoversToday() throws Exception {
        // Ohne diese Pruefung waere ein lueckenloser, ueberschneidungsfreier Kalender denkbar,
        // der irgendwo in der Vergangenheit endet - das Schema haette nichts dagegen, und der
        // Server liefe mit einer Saisonwertung, die stumm bliebe.
        SeasonCalendar calendar = loadStatistics().seasons();

        assertThat(calendar.seasonOf(LocalDate.now(java.time.ZoneOffset.UTC)))
                .as("der heutige Tag gehoert zu einer Saison - sonst rankt die Saisonwertung nichts")
                .isPresent();
    }

    @Test
    @DisplayName("jede Belohnungsvorlage existiert in items.yml")
    void everyRewardTemplateExistsInItems() throws Exception {
        StatisticsConfig statistics = loadStatistics();
        ItemConfig items = loadItems();

        List<String> missing = new ArrayList<>();
        statistics
                .rewards()
                .forEach(
                        (place, reward) -> {
                            for (StatisticsConfig.ItemGrant grant : reward.items()) {
                                if (!items.templates().containsKey(grant.template())) {
                                    missing.add("rewards." + place + ": " + grant.template());
                                }
                            }
                        });

        assertThat(missing)
                .as(
                        "eine Vorlage mit Tippfehler waere ein Anspruch, der erst am Saisonende ins"
                                + " Leere greift - beim Spieler, der drei Monate dafuer gespielt hat")
                .isEmpty();
    }

    @Test
    @DisplayName("jeder Textschluessel dieses Blocks loest in messages.yml auf")
    void everyMessageKeyResolves() throws Exception {
        Messages messages = loadMessages();

        List<String> missing = new ArrayList<>();
        for (MessageKey key : StatisticsMessageKeys.all()) {
            if (!messages.contains(key)) {
                missing.add(key.value());
            }
        }

        assertThat(missing)
                .as("ein fehlender Text erscheint dem Spieler als roher Schluessel im Fenster")
                .isEmpty();
    }

    @Test
    @DisplayName("jede gewichtete Rangliste ist oeffentlich")
    void everyWeightedBoardIsPublic() throws Exception {
        // ADR-046. Das Schema prueft es bereits; hier steht, dass die AUSGELIEFERTEN Gewichte
        // gemeint sind und nicht irgendwelche.
        assertThat(loadStatistics().score().weights().keySet())
                .allMatch(Aggregation::scoreable, "oeffentlich und damit bepunktbar");
    }

    // -----------------------------------------------------------------------------------

    private static StatisticsConfig loadStatistics() throws Exception {
        Map<String, Object> document = loadYaml("statistics.yml");
        ConfigSchema<StatisticsConfig> schema = StatisticsConfigSchema.schema();
        return schema.bind(new MapView(document, StatisticsConfigSchema.SCHEMA_VERSION));
    }

    private static ItemConfig loadItems() throws Exception {
        Map<String, Object> document = loadYaml("items.yml");
        ConfigSchema<ItemConfig> schema = ItemConfigSchema.schema();
        return schema.bind(new MapView(document, ItemConfigSchema.SCHEMA_VERSION));
    }

    private static Messages loadMessages() throws Exception {
        Map<String, Object> document = loadYaml("messages.yml");
        Map<String, String> flat = new LinkedHashMap<>();
        flatten("", document, flat);
        return new MapMessages(flat);
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> body, Map<String, String> out) {
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> nested) {
                flatten(path, (Map<String, Object>) nested, out);
            } else if (entry.getValue() != null) {
                out.put(path, String.valueOf(entry.getValue()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(String resource) throws Exception {
        try (InputStream stream =
                ShippedStatisticsConfigTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).as(resource + " liegt auf dem Klassenpfad").isNotNull();
            return (Map<String, Object>)
                    new Yaml().load(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
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
