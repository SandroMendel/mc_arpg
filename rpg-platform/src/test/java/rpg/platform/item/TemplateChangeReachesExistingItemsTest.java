package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import rpg.core.item.ConsumableEffect;
import rpg.core.item.ItemCategory;
import rpg.core.item.ItemTemplate;
import rpg.core.item.Items;
import rpg.core.item.Rarity;
import rpg.core.item.WearCurve;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;

/**
 * <b>SC-001 — der Grund, aus dem dieser Block so gebaut ist, wie er gebaut ist.</b>
 *
 * <p>Ein Betreiber ändert einen Wert an einer Vorlage, lädt neu, und <em>jedes</em> vorhandene
 * Exemplar in <em>jedem</em> Inventar wirkt mit dem neuen Wert — ohne dass ein Inventar angefasst
 * wurde.
 *
 * <p>Das ist die Zusage aus ADR-004, und sie ist die einzige dieses Blocks, die sich nachträglich
 * nicht mehr einbauen ließe. Ein Item, das seine Endwerte gespeichert hat, ist nach dem Release
 * nicht mehr zu balancieren; der einzige Ausweg wäre eine Migration über jedes Spielerinventar, und
 * die schreibt niemand freiwillig.
 *
 * <p><b>Seit ADR-027 ist die Zusage stärker geworden.</b> Früher hieß es „Vorlagen-ID <em>und
 * gewürfelte Roll-Werte</em>" — ein geändertes Balancing erreichte damit nur die Vorlage, nicht den
 * Wurf. Ohne Roll ist die Vorlage die einzige Quelle, und die Änderung erreicht wirklich alles.
 */
class TemplateChangeReachesExistingItemsTest {

    private MutableItems items;
    private ItemStackFactory factory;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        items = new MutableItems(40.0);
        factory = new ItemStackFactory(items, messages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("SC-001 - eine geaenderte Vorlage erreicht ein Exemplar, das laengst existiert")
    void aChangedTemplateReachesAnExistingItem() {
        // Der Spieler hebt den Trank auf. Ab hier wird dieses Exemplar nicht mehr angefasst.
        ItemStack inTheBackpack = factory.create("potion.test", 1).orElseThrow();

        assertThat(effectBehind(inTheBackpack).heal()).isEqualTo(40.0);

        // Der Betreiber aendert items.yml und laedt neu.
        items.setHeal(80.0);

        // Dasselbe Exemplar. Kein Inventar wurde angefasst, kein Item neu erzeugt.
        assertThat(effectBehind(inTheBackpack).heal())
                .as("das vorhandene Exemplar wirkt mit dem neuen Betrag")
                .isEqualTo(80.0);
    }

    @Test
    @DisplayName("und auch der angezeigte Name folgt der Vorlage")
    void theRenderedNameFollowsTheTemplateToo() {
        ItemStack stack = factory.create("potion.test", 1).orElseThrow();
        String before = plain(stack);

        items.setName("Renamed Potion");
        factory.render(stack);

        assertThat(plain(stack)).isNotEqualTo(before).contains("Renamed Potion");
    }

    @Test
    @DisplayName("der Vermerk aendert sich dabei nicht - er ist die Kennung, nicht der Wert")
    void theTagItselfNeverChanges() {
        ItemStack stack = factory.create("potion.test", 1).orElseThrow();

        items.setHeal(999.0);
        factory.render(stack);

        assertThat(ItemTag.templateOf(stack)).contains("potion.test");
        assertThat(stack.getItemMeta().getPersistentDataContainer().getKeys()).hasSize(2);
    }

    private ConsumableEffect effectBehind(ItemStack stack) {
        return factory.templateOf(stack).orElseThrow().effect();
    }

    private static String plain(ItemStack stack) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(stack.getItemMeta().displayName());
    }

    private Messages messages() {
        return new MapMessages(
                Map.of(
                        "item.potion.test.name", "Test Potion",
                        "item.rarity.common.name", "Common"));
    }

    /**
     * Eine {@link Items}, deren Vorlage sich ändern lässt — das ist genau, was ein Reload tut.
     *
     * <p>Der Name kommt aus {@code messages.yml}; damit der zweite Test etwas zu sehen bekommt,
     * trägt diese Fassung ihn selbst und die Fabrik fällt auf den Schlüssel zurück.
     */
    private final class MutableItems implements Items {

        private double heal;
        private String name = "Test Potion";

        MutableItems(double heal) {
            this.heal = heal;
        }

        void setHeal(double value) {
            this.heal = value;
        }

        void setName(String value) {
            this.name = value;
            factory = new ItemStackFactory(this, new MapMessages(
                    Map.of(
                            "item.potion.test.name", value,
                            "item.rarity.common.name", "Common")));
        }

        @Override
        public Optional<ItemTemplate> template(String key) {
            if (!"potion.test".equals(key)) {
                return Optional.empty();
            }
            return Optional.of(
                    new ItemTemplate(
                            "potion.test",
                            ItemCategory.CONSUMABLE,
                            "POTION",
                            Rarity.COMMON,
                            null,
                            null,
                            3L,
                            null,
                            ConsumableEffect.healing(heal, Duration.ofSeconds(8)),
                            null));
        }

        @Override
        public Collection<String> templateKeys() {
            return List.of("potion.test");
        }

        @Override
        public OptionalLong sellPriceOf(String key) {
            return template(key).map(ItemTemplate::sellPriceOrNone).orElseGet(OptionalLong::empty);
        }

        @Override
        public WearCurve wear() {
            return WearCurve.defaults();
        }
    }
}
