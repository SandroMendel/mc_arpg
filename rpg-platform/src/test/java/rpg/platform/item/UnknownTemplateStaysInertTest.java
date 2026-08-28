package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
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
 * FR-007 — <b>eine Vorlage verschwindet, und das Exemplar bleibt.</b>
 *
 * <p>Der Fall ist nicht ausgedacht: ein Betreiber streicht einen Trank aus {@code items.yml}, weil
 * er ihn nicht mehr will. In dem Moment liegen Exemplare davon in Inventaren, in Enderchests und
 * auf dem Boden.
 *
 * <p>Drei Verhaltensweisen wären möglich, und zwei davon sind falsch:
 *
 * <ul>
 *   <li><b>Still löschen</b> — Datenverlust ohne Ansage. Der Spieler merkt es, wenn er nachsieht,
 *       und niemand kann ihm sagen, was passiert ist.
 *   <li><b>Den Start abbrechen</b> — die Strafe für den Betreiber statt für den Fehler. Ein Server,
 *       der nicht mehr hochkommt, weil irgendwo noch ein alter Trank liegt, ist schlimmer als der
 *       alte Trank.
 *   <li><b>Inert lassen</b> — tragbar und vernichtbar, nicht benutzbar und nicht verkäuflich. Der
 *       Gegenstand ist sichtbar, hat einen Namen, den man wiederfindet, und niemand verliert etwas.
 * </ul>
 */
class UnknownTemplateStaysInertTest {

    private ItemStackFactory factory;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        factory = new ItemStackFactory(emptyItems(), new MapMessages(Map.of()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("ein Exemplar ohne Vorlage bleibt bestehen und traegt seinen Schluessel")
    void anOrphanedItemKeepsItsKey() {
        ItemStack orphan = new ItemStack(Material.POTION);
        ItemTag.mark(orphan, "potion.removed", 1);

        factory.render(orphan);

        assertThat(orphan.getType()).isEqualTo(Material.POTION);
        assertThat(ItemTag.templateOf(orphan))
                .as("der Schluessel bleibt - sonst faende der Betreiber ihn nicht wieder")
                .contains("potion.removed");
        assertThat(plain(orphan)).contains("potion.removed");
    }

    @Test
    @DisplayName("es ist nicht benutzbar und nicht verkaeuflich")
    void itIsNeitherUsableNorSellable() {
        ItemStack orphan = new ItemStack(Material.POTION);
        ItemTag.mark(orphan, "potion.removed", 1);

        assertThat(factory.templateOf(orphan))
                .as("keine Vorlage - also keine Wirkung und kein Erloes (FR-007, FR-016)")
                .isEmpty();
    }

    @Test
    @DisplayName("und nichts wirft dabei")
    void nothingThrows() {
        ItemStack orphan = new ItemStack(Material.POTION);
        ItemTag.mark(orphan, "potion.removed", 1);

        assertThatCode(() -> factory.render(orphan)).doesNotThrowAnyException();
        assertThatCode(() -> factory.create("potion.removed", 1)).doesNotThrowAnyException();
        assertThat(factory.create("potion.removed", 1)).isEmpty();
    }

    private static String plain(ItemStack stack) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(stack.getItemMeta().displayName());
    }

    private static Items emptyItems() {
        return new Items() {
            @Override
            public Optional<ItemTemplate> template(String key) {
                return Optional.empty();
            }

            @Override
            public Collection<String> templateKeys() {
                return List.of();
            }

            @Override
            public OptionalLong sellPriceOf(String key) {
                return OptionalLong.empty();
            }

            @Override
            public WearCurve wear() {
                return WearCurve.defaults();
            }
        };
    }
}
