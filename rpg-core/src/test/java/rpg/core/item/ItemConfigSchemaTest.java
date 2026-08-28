package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;

/**
 * Je ein Fall für jede Regel der Tabelle in {@code contracts/item-config.md} (FR-012).
 *
 * <p><b>Geprüft wird nicht nur, <em>dass</em> es scheitert, sondern dass die Meldung den Schlüssel
 * nennt.</b> Ein Betreiber, der sechs YAML-Dateien pflegt, braucht Datei, Schlüssel und Grund —
 * „invalid configuration" schickt ihn auf die Suche, und beim nächsten Mal schaltet er die
 * Validierung ab.
 */
class ItemConfigSchemaTest {

    @Test
    @DisplayName("ein vollstaendiges Dokument bindet")
    void aCompleteDocumentBinds() {
        ItemConfig config = bind(document());

        assertThat(config.templates()).containsKey("potion.test");
        assertThat(config.wear().threshold()).isEqualTo(50.0);
        assertThat(config.loot().byZone()).containsKey("greenfields");
        assertThat(config.vendorOf("greenfields").carries("potion.test")).isTrue();
    }

    @Nested
    @DisplayName("Vorlagen")
    class Templates {

        @Test
        @DisplayName("eine unbekannte Raritaetsstufe nennt die acht erlaubten")
        void unknownRarityListsTheEight() {
            Map<String, Object> doc = document();
            template(doc).put("rarity", "SUPREME");

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("items.yml.templates.potion.test.rarity")
                    .hasMessageContaining("SUPREME")
                    .hasMessageContaining("COMMON");
        }

        @Test
        @DisplayName("eine unbekannte Kategorie erklaert, warum es nur zwei gibt")
        void unknownCategoryExplainsWhy() {
            Map<String, Object> doc = document();
            template(doc).put("category", "EQUIPMENT");

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("category")
                    .hasMessageContaining("EQUIPMENT")
                    // Der haeufigste Irrtum verdient die Erklaerung an Ort und Stelle.
                    .hasMessageContaining("ADR-017");
        }

        @Test
        @DisplayName("ein Verbrauchbares ohne Wirkung startet nicht")
        void aConsumableNeedsAnEffect() {
            Map<String, Object> doc = document();
            template(doc).remove("effect");

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("potion.test")
                    .hasMessageContaining("effect");
        }

        @Test
        @DisplayName("Wirkung an einer Kosmetik ist ein Fehler, kein Zusatz")
        void aCosmeticMustNotCarryAnEffect() {
            Map<String, Object> doc = document();
            template(doc).put("category", "COSMETIC");
            template(doc).put("appearance", Map.of("trim-material", "REDSTONE", "trim-pattern", "RAISER"));

            assertThatThrownBy(() -> bind(doc)).hasMessageContaining("CONSUMABLE");
        }

        @Test
        @DisplayName("ein fehlendes Material nennt den Schluessel")
        void missingMaterialNamesTheKey() {
            Map<String, Object> doc = document();
            template(doc).remove("material");

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("items.yml.templates.potion.test.material")
                    .hasMessageContaining("missing");
        }

        @Test
        @DisplayName("ein Mindestlevel ausserhalb 1..60 startet nicht")
        void minLevelOutOfRange() {
            Map<String, Object> doc = document();
            template(doc).put("min-level", 61);

            assertThatThrownBy(() -> bind(doc)).hasMessageContaining("min-level");
        }
    }

    @Nested
    @DisplayName("Beute")
    class Loot {

        @Test
        @DisplayName("eine Chance ueber 1 startet nicht")
        void chanceAboveOne() {
            Map<String, Object> doc = document();
            firstLootEntry(doc).put("chance", 1.5);

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("items.yml.loot.by-zone.greenfields")
                    .hasMessageContaining("chance");
        }

        @Test
        @DisplayName("max unter min startet nicht")
        void maxBelowMin() {
            Map<String, Object> doc = document();
            firstLootEntry(doc).put("min", 3);
            firstLootEntry(doc).put("max", 1);

            assertThatThrownBy(() -> bind(doc)).hasMessageContaining("max");
        }

        @Test
        @DisplayName("eine Tabelle, die Ausruestung nennt, landet in der Vorlagenpruefung")
        void namingEquipmentLandsInTheTemplateCheck() {
            // Es gibt keine Ausruestungsvorlagen - also ist jede Ausruestungskennung eine
            // unbekannte. Eine eigene Regel dafuer waere eine zweite Wahrheit darueber, was
            // Ausruestung ist (FR-024, ADR-017).
            Map<String, Object> doc = document();
            firstLootEntry(doc).put("template", "warrior.armor.tier6");

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("warrior.armor.tier6")
                    .hasMessageContaining("do not exist")
                    .hasMessageContaining("ADR-017");
        }
    }

    @Nested
    @DisplayName("Haendler")
    class Vendors {

        @Test
        @DisplayName("ein Bestand mit unbekannter Vorlage startet nicht")
        void unknownTemplateInStock() {
            Map<String, Object> doc = document();
            firstStockEntry(doc).put("template", "potion.nope");

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("vendors.greenfields")
                    .hasMessageContaining("potion.nope");
        }
    }

    @Nested
    @DisplayName("Verschleiss und Reparatur")
    class Wear {

        @Test
        @DisplayName("ein Restanteil ausserhalb [0,1) startet nicht")
        void floorOutOfRange() {
            Map<String, Object> doc = document();
            wear(doc).put("floor", 1.0);

            assertThatThrownBy(() -> bind(doc)).hasMessageContaining("floor");
        }

        @Test
        @DisplayName("eine Schwelle ueber 100 startet nicht")
        void thresholdAboveHundred() {
            Map<String, Object> doc = document();
            wear(doc).put("threshold", 120.0);

            assertThatThrownBy(() -> bind(doc)).hasMessageContaining("threshold");
        }

        @Test
        @DisplayName("ein negativer Reparaturpreis startet nicht")
        void negativeRepairPrice() {
            Map<String, Object> doc = document();
            repairPrices(doc).set(1, -5);

            assertThatThrownBy(() -> bind(doc)).hasMessageContaining("base-per-tier");
        }

        @Test
        @DisplayName("FR-044: ein Tod, der NICHT schwerer wiegt als ein Kampf, startet nicht")
        void adeathThatWeighsNoMoreThanAFightAborts() {
            // Der Fall, den ein Balancing-Durchgang aus Versehen erzeugt: die Schadensraten
            // hochgedreht, den Todesbetrag vergessen. Von aussen bliebe er unsichtbar - alles
            // liefe weiter, nur der Tod taete nicht mehr weh, und damit waere die Todesstrafe aus
            // ADR-017 stillschweigend abgeschaltet.
            //
            // Geprueft wird hier ueber den LADEWEG und nicht ueber validate() direkt: ein Betreiber
            // ruft keine Methode auf, er bearbeitet eine Datei.
            Map<String, Object> doc = document();
            wear(doc).put("per-damage-taken", 0.5);
            wear(doc).put("per-death", 1.0);

            assertThatThrownBy(() -> bind(doc))
                    .hasMessageContaining("per-death")
                    .hasMessageContaining("death-factor-min");
        }
    }

    @Nested
    @DisplayName("Inventar")
    class InventorySection {

        @Test
        @DisplayName("der Abschnitt darf FEHLEN - dann gilt die Vorgabe (FR-076)")
        void thesectionMayBeMissing() {
            // Eine Pflichtangabe daraus zu machen hiesse, jede vorhandene items.yml beim Aufspielen
            // abzuweisen - fuer eine Zahl, die vorher gar nicht konfigurierbar war.
            Map<String, Object> doc = document();
            doc.remove("inventory");

            assertThat(bind(doc).inventoryFullCooldown())
                    .isEqualTo(ItemConfig.DEFAULT_INVENTORY_FULL_COOLDOWN);
        }

        @Test
        @DisplayName("und eine eigene Ruhezeit wird uebernommen")
        void anowncooldownIsTaken() {
            Map<String, Object> doc = document();
            doc.put("inventory", new java.util.LinkedHashMap<>(Map.of("full-warning-cooldown-ms", 3000)));

            assertThat(bind(doc).inventoryFullCooldown())
                    .isEqualTo(java.time.Duration.ofMillis(3000));
        }
    }

    // -----------------------------------------------------------------------------------
    // Dokument und Zugriffe
    // -----------------------------------------------------------------------------------

    private static ItemConfig bind(Map<String, Object> document) {
        ConfigSchema<ItemConfig> schema = ItemConfigSchema.schema();
        return schema.bind(new MapView(document, ItemConfigSchema.SCHEMA_VERSION));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> template(Map<String, Object> doc) {
        return (Map<String, Object>)
                ((Map<String, Object>) doc.get("templates")).get("potion.test");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> wear(Map<String, Object> doc) {
        return (Map<String, Object>) doc.get("wear");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> repairPrices(Map<String, Object> doc) {
        return (List<Object>) ((Map<String, Object>) doc.get("repair")).get("base-per-tier");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstLootEntry(Map<String, Object> doc) {
        Map<String, Object> loot = (Map<String, Object>) doc.get("loot");
        Map<String, Object> byZone = (Map<String, Object>) loot.get("by-zone");
        List<Object> entries = (List<Object>) byZone.get("greenfields");
        return (Map<String, Object>) entries.get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstStockEntry(Map<String, Object> doc) {
        Map<String, Object> vendors = (Map<String, Object>) doc.get("vendors");
        Map<String, Object> greenfields = (Map<String, Object>) vendors.get("greenfields");
        List<Object> stock = (List<Object>) greenfields.get("stock");
        return (Map<String, Object>) stock.get(0);
    }

    private static Map<String, Object> document() {
        Map<String, Object> effect = new LinkedHashMap<>();
        effect.put("heal", 40.0);
        effect.put("cooldown-ms", 8000);

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("category", "CONSUMABLE");
        template.put("material", "POTION");
        template.put("rarity", "COMMON");
        template.put("sell-price", 3);
        template.put("effect", effect);

        Map<String, Object> templates = new LinkedHashMap<>();
        templates.put("potion.test", template);

        Map<String, Object> wear = new LinkedHashMap<>();
        wear.put("threshold", 50.0);
        wear.put("floor", 0.20);
        wear.put("per-damage-taken", 0.01);
        wear.put("per-damage-dealt", 0.01);
        wear.put("per-death", 10.0);
        wear.put("death-factor-min", 100.0);
        wear.put("warn-at", new ArrayList<>(List.of(50.0, 25.0, 10.0)));
        wear.put("warn-cooldown-ms", 60000);

        Map<String, Object> repair = new LinkedHashMap<>();
        repair.put("base-per-tier", new ArrayList<Object>(List.of(0, 40, 120, 400, 1200, 3000)));

        Map<String, Object> lootEntry = new LinkedHashMap<>();
        lootEntry.put("template", "potion.test");
        lootEntry.put("chance", 0.08);
        lootEntry.put("min", 1);
        lootEntry.put("max", 1);

        Map<String, Object> byZone = new LinkedHashMap<>();
        byZone.put("greenfields", new ArrayList<Object>(List.of(lootEntry)));

        Map<String, Object> loot = new LinkedHashMap<>();
        loot.put("by-zone", byZone);

        Map<String, Object> stockEntry = new LinkedHashMap<>();
        stockEntry.put("template", "potion.test");
        stockEntry.put("price", 12);

        Map<String, Object> greenfieldsVendor = new LinkedHashMap<>();
        greenfieldsVendor.put("stock", new ArrayList<Object>(List.of(stockEntry)));

        Map<String, Object> vendors = new LinkedHashMap<>();
        vendors.put("greenfields", greenfieldsVendor);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("wear", wear);
        document.put("repair", repair);
        document.put("templates", templates);
        document.put("loot", loot);
        document.put("vendors", vendors);
        return document;
    }

    /** Eine {@link ConfigView} auf eine einfache Abbildung - genug fuer den Binder. */
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
