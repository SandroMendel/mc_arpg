package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigHandle;
import rpg.core.message.MapMessages;

/**
 * Die Zusagen aus {@code contracts/item-api.md} §1: <b>nichts wirft, und nichts antwortet mit
 * {@code null}.</b>
 *
 * <p>Der Fall ist nicht theoretisch. Eine Vorlage kann zwischen zwei Reloads verschwinden, während
 * ein Exemplar davon noch in einem Inventar liegt — FR-007 verlangt ausdrücklich, dass es dann
 * bestehen bleibt. Jede Abfrage danach trifft auf eine unbekannte Kennung, und zwar im Pfad eines
 * Inventarklicks. Eine Ausnahme an dieser Stelle wäre ein Spieler, dem beim Aufräumen seines
 * Rucksacks das Inventar einfriert.
 */
class ItemApiContractTest {

    private static final Logger LOGGER = Logger.getLogger(ItemApiContractTest.class.getName());

    private final Items items = module();

    @Test
    @DisplayName("eine unbekannte Kennung antwortet LEER - nicht mit null, und ohne zu werfen")
    void anUnknownKeyAnswersEmpty() {
        assertThat(items.template("potion.does-not-exist")).isEmpty();
        assertThat(items.sellPriceOf("potion.does-not-exist")).isEmpty();
        assertThatCode(() -> items.template("potion.does-not-exist")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null als Kennung ist kein Fehler")
    void nullIsNotAnError() {
        assertThat(items.template(null)).isEmpty();
        assertThatCode(() -> items.template(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("eine bekannte Kennung liefert ihre Vorlage und ihren Erloes")
    void aKnownKeyAnswersWithItsTemplate() {
        assertThat(items.template("potion.test")).isPresent();
        assertThat(items.sellPriceOf("potion.test")).hasValue(3L);
    }

    @Test
    @DisplayName("eine unverkaeufliche Vorlage antwortet LEER - nicht mit 0")
    void anUnsellableTemplateAnswersEmpty() {
        // Der Unterschied ist tragend: 0 hiesse "der Haendler zahlt nichts dafuer", leer heisst
        // "er nimmt es nicht an" (FR-016). Aus Sicht des Spielers sind das zwei verschiedene
        // Meldungen.
        assertThat(items.sellPriceOf("trim.test")).isEmpty();
    }

    @Test
    @DisplayName("die Kennungen kommen in Konfigurationsreihenfolge")
    void keysComeInConfigurationOrder() {
        assertThat(items.templateKeys()).containsExactly("potion.test", "trim.test");
    }

    @Test
    @DisplayName("die Verschleisskurve ist von aussen lesbar - B12 und B13 brauchen sie")
    void theWearCurveIsReadable() {
        assertThat(items.wear().factorFor(WearCurve.FULL)).isEqualTo(1.0);
        assertThat(items.wear().factorFor(0.0)).isEqualTo(items.wear().floor());
    }

    private static Items module() {
        Map<String, ItemTemplate> templates = new java.util.LinkedHashMap<>();
        templates.put(
                "potion.test",
                new ItemTemplate(
                        "potion.test",
                        ItemCategory.CONSUMABLE,
                        "POTION",
                        Rarity.COMMON,
                        null,
                        null,
                        3L,
                        null,
                        ConsumableEffect.healing(40.0, Duration.ofSeconds(8)),
                        null));
        templates.put(
                "trim.test",
                new ItemTemplate(
                        "trim.test",
                        ItemCategory.COSMETIC,
                        "NETHERITE_UPGRADE_SMITHING_TEMPLATE",
                        Rarity.LEGENDARY,
                        null,
                        null,
                        null, // unverkaeuflich
                        null,
                        null,
                        new CosmeticAppearance("REDSTONE", "RAISER")));

        ItemConfig config =
                new ItemConfig(
                        templates,
                        WearCurve.defaults(),
                        new RepairPricing(List.of(0L, 40L, 120L, 400L, 1200L, 3000L)),
                        LootTables.empty(),
                        Map.of());

        return ItemModule.withConfig(
                LOGGER,
                new MapMessages(Map.of()),
                FakeZones::new,
                Set::of,
                new ConfigHandle<ItemConfig>() {
                    @Override
                    public ItemConfig get() {
                        return config;
                    }

                    @Override
                    public Path source() {
                        return Path.of("items.yml");
                    }
                });
    }
}
