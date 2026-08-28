package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;
import rpg.core.item.ItemCategory;
import rpg.core.item.ItemConfig;
import rpg.core.item.ItemConfigSchema;
import rpg.core.item.ItemMessageKeys;
import rpg.core.item.ItemTemplate;
import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;

/**
 * Die {@code items.yml}, die tatsächlich ausgeliefert wird, muss ihr eigenes Schema bestehen — und
 * jede Vorlage muss einen Namen in {@code messages.yml} auflösen.
 *
 * <p><b>Warum das hier liegt und nicht in {@code rpg-core}</b>: dieselbe Begründung wie bei
 * {@code ShippedMobConfigTest} und {@code ShippedZoneConfigTest}. Beide ausgelieferten Ressourcen
 * stehen nur auf diesem Klassenpfad, und <b>keiner</b> der Schematests von B11 baut die echte
 * Datei — sie konstruieren alle ihr eigenes Dokument. Eine kaputte ausgelieferte Datei segelte
 * damit an jedem Modultest vorbei und fiele erst beim Serverstart auf.
 */
class ShippedItemConfigTest {

    @Test
    @DisplayName("die ausgelieferte items.yml besteht das Schema, fuer das sie geschrieben wurde")
    void shippedConfigurationIsValid() throws Exception {
        ItemConfig config = loadItems();

        assertThat(config.templates()).as("mindestens eine Vorlage").isNotEmpty();
        assertThat(config.vendors()).as("sechs Regionen, sechs Haendler").hasSize(6);
    }

    @Test
    @DisplayName("jede Vorlage loest einen Namen in messages.yml auf")
    void everyTemplateResolvesADisplayName() throws Exception {
        ItemConfig config = loadItems();
        Messages messages = loadMessages();

        List<String> missing = new ArrayList<>();
        for (MessageKey key : ItemMessageKeys.all(config.templates().keySet())) {
            if (!messages.contains(key)) {
                missing.add(key.value());
            }
        }

        assertThat(missing)
                .as(
                        "eine Vorlage ohne Namen liefe als item.<key>.name durchs Inventar - und"
                                + " ItemModule.start weist den Start deshalb ab")
                .isEmpty();
    }

    @Test
    @DisplayName("jede Region fuehrt einen Haendler, und die Bestaende unterscheiden sich")
    void everyRegionHasAVendorAndTheyDiffer() throws Exception {
        ItemConfig config = loadItems();

        for (String zoneKey :
                List.of(
                        "greenfields",
                        "dustlands",
                        "safari-plains",
                        "terracotta-canyons",
                        "darkforest",
                        "pale-wilds")) {
            assertThat(config.vendorOf(zoneKey).templateKeys())
                    .as(zoneKey + " fuehrt etwas")
                    .isNotEmpty();
        }

        // Q6: das Angebot skaliert mit der Region. Waeren alle sechs gleich, waere die
        // Entscheidung fuer sechs eigene Bestaende folgenlos geblieben.
        assertThat(config.vendorOf("greenfields").templateKeys())
                .isNotEqualTo(config.vendorOf("pale-wilds").templateKeys());
    }

    @Test
    @DisplayName("keine Beutetabelle und kein Bestand nennt Ausruestung")
    void nothingNamesEquipment() throws Exception {
        // Die Pruefung ist im Schema schon eingebaut (FR-024), aber sie greift nur, weil es keine
        // Ausruestungsvorlagen GIBT. Dieser Test haelt die zweite Haelfte fest: es gibt wirklich
        // keine - weder als CONSUMABLE getarnt noch als COSMETIC.
        ItemConfig config = loadItems();

        for (ItemTemplate template : config.templates().values()) {
            assertThat(template.category())
                    .as(template.key() + " ist Verbrauchbares oder Kosmetik, nichts anderes")
                    .isIn(ItemCategory.CONSUMABLE, ItemCategory.COSMETIC);
        }
    }

    @Test
    @DisplayName("die ausgelieferten Verschleisswerte erfuellen die Todes-Ordnung")
    void theShippedWearValuesSatisfyTheDeathOrdering() throws Exception {
        // FR-044. Die Bindung wirft schon, wenn es nicht stimmt - dieser Test macht sichtbar,
        // dass die AUSGELIEFERTEN Werte gemeint sind und nicht irgendwelche.
        ItemConfig config = loadItems();

        double largestDamageRate =
                Math.max(config.wear().perDamageTaken(), config.wear().perDamageDealt());
        assertThat(config.wear().perDeath())
                .isGreaterThanOrEqualTo(largestDamageRate * config.wear().deathFactorMin());
    }

    // -----------------------------------------------------------------------------------

    private static ItemConfig loadItems() throws Exception {
        Map<String, Object> document = loadYaml("items.yml");
        ConfigSchema<ItemConfig> schema = ItemConfigSchema.schema();
        return schema.bind(new MapView(document, ItemConfigSchema.SCHEMA_VERSION));
    }

    private static Messages loadMessages() throws Exception {
        Map<String, Object> document = loadYaml("messages.yml");
        Map<String, String> flat = new java.util.LinkedHashMap<>();
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
                ShippedItemConfigTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).as(resource + " is on the classpath").isNotNull();
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
