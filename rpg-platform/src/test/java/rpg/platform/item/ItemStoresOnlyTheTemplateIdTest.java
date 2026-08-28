package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
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
 * <b>Die Kernzusage aus ADR-004, nachgezählt.</b>
 *
 * <p>Ein Item speichert die Vorlagen-ID und die Schema-Version — <em>und sonst nichts</em>. Kein
 * berechneter Heilbetrag, kein Verkaufserlös, kein gerendertes Lore.
 *
 * <p><b>Warum das ein eigener Test ist und nicht nur ein Kommentar.</b> Der Verstoß ist billig und
 * verlockend: einen Wert in den Container zu schreiben spart einen Nachschlag, und es funktioniert
 * — bis zum ersten Balancing. Dann steht in tausend Inventaren eine Zahl, die niemand mehr
 * erreicht, und der einzige Ausweg ist, jedes Spielerinventar anzufassen. Genau das sollte ADR-004
 * verhindern.
 *
 * <p>Deshalb wird hier nicht geprüft, ob die Ableitung <em>funktioniert</em>, sondern ob im
 * Container <b>nichts anderes</b> liegt. Der Test zählt die Schlüssel.
 */
class ItemStoresOnlyTheTemplateIdTest {

    private ItemStackFactory factory;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        factory = new ItemStackFactory(items(), messages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("der Container traegt genau zwei Schluessel")
    void exactlyTwoKeys() {
        ItemStack stack = factory.create("potion.test", 1).orElseThrow();

        PersistentDataContainer container = stack.getItemMeta().getPersistentDataContainer();

        List<String> keys = new ArrayList<>();
        for (NamespacedKey key : container.getKeys()) {
            keys.add(key.toString());
        }

        assertThat(keys)
                .as(
                        "steht hier ein dritter Wert, ist er entweder ein berechneter Endwert oder"
                                + " gerendertes Lore - und damit ist Rebalancing nach dem Release"
                                + " nicht mehr moeglich, ohne jedes Inventar anzufassen (ADR-004)")
                .containsExactlyInAnyOrder("rpg:item_template", "rpg:item_schema");
    }

    @Test
    @DisplayName("kein berechneter Wert steht im Container")
    void noComputedValueIsStored() {
        ItemStack stack = factory.create("potion.test", 1).orElseThrow();
        PersistentDataContainer container = stack.getItemMeta().getPersistentDataContainer();

        // Die Vorlage heilt 40 und ist fuer 3 verkaeuflich. Beides darf nirgends im Container
        // stehen - weder unter diesem noch unter irgendeinem anderen Schluessel.
        for (NamespacedKey key : container.getKeys()) {
            String asString = String.valueOf(container.get(key, org.bukkit.persistence.PersistentDataType.STRING));
            assertThat(asString).doesNotContain("40").doesNotContain("heal");
        }
    }

    @Test
    @DisplayName("die Lore wird abgeleitet, nicht gespeichert")
    void loreIsDerivedRatherThanStored() {
        ItemStack stack = factory.create("potion.test", 1).orElseThrow();

        // Die Lore steht in der Anzeige - dort gehoert sie hin. Was zaehlt, ist, dass sie NICHT
        // im Container liegt: die Anzeige wird bei jedem Laden neu gebaut, der Container nicht.
        assertThat(stack.getItemMeta().lore()).isNotEmpty();
        assertThat(stack.getItemMeta().getPersistentDataContainer().getKeys())
                .hasSize(2);
    }

    private static Items items() {
        ItemTemplate template =
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
                        null);
        return new Items() {
            @Override
            public Optional<ItemTemplate> template(String key) {
                return "potion.test".equals(key) ? Optional.of(template) : Optional.empty();
            }

            @Override
            public java.util.Collection<String> templateKeys() {
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
        };
    }

    private static Messages messages() {
        return new MapMessages(
                Map.of(
                        "item.potion.test.name", "Test Potion",
                        "item.potion.test.lore", "For testing.",
                        "item.rarity.common.name", "Common"));
    }
}
