package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import rpg.core.item.ItemTemplate;
import rpg.core.item.Items;
import rpg.core.item.WearCurve;
import rpg.core.message.MapMessages;

/**
 * FR-008 und Constitution VI — <b>der Server ist die Autorität, nicht das, was am Item steht.</b>
 *
 * <p>Ein manipulierter Vermerk kann nur zwei Formen haben, und beide werden hier geprüft:
 *
 * <ul>
 *   <li>eine <b>erfundene Vorlagen-ID</b>. Sie löst nichts auf — das Item ist inert, nicht mächtig.
 *       Das ist der eigentliche Schutz und er ist Bauart, nicht Prüfung: weil ein Exemplar nur die
 *       Kennung trägt und alle Werte aus der Konfiguration kommen, gibt es keinen Wert, den man
 *       hineinschreiben könnte.
 *   <li>ein <b>falsch typisierter Wert</b>. Ein Leser, der hier eine {@code ClassCastException}
 *       wirft, ist ein Server, den ein einzelner Gegenstand anhalten kann — und der Gegenstand
 *       liegt im Pfad jedes Inventarklicks.
 * </ul>
 *
 * <p>Beide Fälle enden gleich: das Item bleibt liegen, tut nichts, und niemand stürzt ab.
 */
class TamperedItemIsRejectedTest {

    private ItemStackFactory factory;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        factory = new ItemStackFactory(oneTemplate(), new MapMessages(Map.of()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("eine erfundene Vorlagen-ID macht das Item nicht maechtig, sondern inert")
    void aForgedTemplateIdYieldsNothing() {
        ItemStack forged = new ItemStack(Material.POTION);
        ItemTag.mark(forged, "potion.i-made-this-up", 1);

        assertThat(factory.templateOf(forged))
                .as("der Server kennt die Vorlage nicht - also gibt es keine Werte")
                .isEmpty();
        assertThatCode(() -> factory.render(forged)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ein falsch typisierter Vermerk haelt den Server nicht an")
    void aWronglyTypedTagDoesNotStopTheServer() {
        // Die Schema-Version als Zeichenkette statt als Zahl. Ein Leser ohne Typpruefung wuerfe
        // hier - und zwar im Pfad jedes Inventarklicks.
        ItemStack broken = new ItemStack(Material.POTION);
        ItemMeta meta = broken.getItemMeta();
        meta.getPersistentDataContainer()
                .set(NamespacedKey.fromString("rpg:item_template"), PersistentDataType.STRING, "potion.test");
        meta.getPersistentDataContainer()
                .set(NamespacedKey.fromString("rpg:item_schema"), PersistentDataType.STRING, "not-a-number");
        broken.setItemMeta(broken.getItemMeta() == null ? meta : meta);

        assertThatCode(() -> ItemTag.schemaOf(broken)).doesNotThrowAnyException();
        assertThatCode(() -> factory.render(broken)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("die Werte kommen aus der Konfiguration, nie vom Item - deshalb gibt es nichts zu faelschen")
    void thereIsNothingWorthForging() {
        // Der Kern von ADR-004, hier als Sicherheitsaussage gelesen: wer den Vermerk aendert,
        // aendert die KENNUNG, nicht den Wert. Eine hoehere Zahl laesst sich nicht hineinschreiben,
        // weil keine Zahl darin steht.
        ItemStack legit = factory.create("potion.test", 1).orElseThrow();

        assertThat(legit.getItemMeta().getPersistentDataContainer().getKeys())
                .as("zwei Schluessel, beide Kennung - kein Wert, den ein Client hochsetzen koennte")
                .hasSize(2);
    }

    private static Items oneTemplate() {
        return new Items() {
            @Override
            public Optional<ItemTemplate> template(String key) {
                if (!"potion.test".equals(key)) {
                    return Optional.empty();
                }
                return Optional.of(
                        new ItemTemplate(
                                "potion.test",
                                rpg.core.item.ItemCategory.CONSUMABLE,
                                "POTION",
                                rpg.core.item.Rarity.COMMON,
                                null,
                                null,
                                3L,
                                null,
                                rpg.core.item.ConsumableEffect.healing(
                                        40.0, java.time.Duration.ofSeconds(8)),
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
        };
    }
}
