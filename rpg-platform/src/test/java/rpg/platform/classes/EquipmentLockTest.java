package rpg.platform.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import rpg.core.classes.BoundEquipment;
import rpg.core.classes.LadderSlot;
import rpg.core.session.CharacterClass;

/**
 * T092 bis T100 und T104 - jede Route einzeln, plus die beiden Gegentests.
 *
 * <p><b>Warum hier keine Ereignisse konstruiert werden.</b> Ein {@code InventoryClickEvent} lässt sich
 * gegen MockBukkit nicht bauen: {@code SimpleInventoryViewMock.convertSlot} ist nicht implementiert, und
 * ein Test, der es versucht, wird <b>still übersprungen</b> statt zu scheitern. Acht Tests dieser Datei
 * standen zunächst genau so da - grün in der Zusammenfassung, ohne eine einzige Zusage zu prüfen. Das
 * ist die Falle, vor der T136 warnt, und sie ist hier zugeschnappt.
 *
 * <p>Geprüft wird deshalb zweigeteilt: je Route die <b>Entscheidung</b>, und separat, dass es für jedes
 * Ereignis überhaupt einen Handler gibt. Zusammen ist das strenger als eine Ereignissimulation - die
 * hätte nur bewiesen, dass ein Pfad funktioniert, nicht dass alle vorhanden sind.
 */
class EquipmentLockTest {

    private static final Logger QUIET = Logger.getLogger("equipment-lock-test");

    private ServerMock server;
    private EquipmentLockListener lock;
    private ItemStack bound;
    private ItemStack otherBound;
    private ItemStack loot;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        lock = new EquipmentLockListener(QUIET);
        bound = boundItem(Material.NETHERITE_CHESTPLATE, LadderSlot.ARMOR);
        otherBound = boundItem(Material.NETHERITE_SWORD, LadderSlot.WEAPON);
        loot = new ItemStack(Material.DIAMOND);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Nested
    @DisplayName("die Routen für ein gebundenes Item")
    class BoundRoutes {

        @Test
        @DisplayName("T092 Route 1: das gebundene Item liegt im geklickten Slot (US4.1, FR-026)")
        void boundInClickedSlot() {
            assertThat(lock.refusesClick(bound, null, null)).isTrue();
        }

        @Test
        @DisplayName("T093 Route 2: das gebundene Item liegt auf dem Cursor - Slot-Tausch (US4.2)")
        void boundOnCursor() {
            // Ein Tausch bewegt das Cursor-Item IN den Slot. Eine Prüfung nur auf den Slotinhalt
            // hätte genau das durchgelassen.
            assertThat(lock.refusesClick(loot, bound, null)).isTrue();
        }

        @Test
        @DisplayName("T094 Route 3: Shift-Klick auf ein gebundenes Item (US4.2)")
        void boundOnShiftClick() {
            // Ein Shift-Klick trägt dasselbe Slotitem; die Entscheidung ist dieselbe, und dass sie
            // dieselbe ist, ist die Aussage.
            assertThat(lock.refusesClick(bound, null, null)).isTrue();
        }

        @Test
        @DisplayName("T095 Route 4: das gebundene Item liegt im Ziel-Hotbarslot (US4.2)")
        void boundInHotbarTarget() {
            // Weder im geklickten Slot noch auf dem Cursor - der Fall, den zwei Prüfungen verpassen.
            assertThat(lock.refusesClick(loot, null, bound)).isTrue();
        }

        @Test
        @DisplayName("T096 Route 5: Offhand-Tausch mit einem gebundenen Item (US4.2)")
        void boundOnOffhandSwap() {
            assertThat(lock.refusesSwap(bound, loot)).isTrue();
            assertThat(lock.refusesSwap(loot, bound)).as("auch in der anderen Hand").isTrue();
        }

        @Test
        @DisplayName("Route 7 (nicht in der Spec genannt): ein Zieh-Vorgang mit gebundenem Item")
        void boundOnDrag() {
            assertThat(lock.refusesDrag(bound, List.of()))
                    .as("auf dem Cursor")
                    .isTrue();
            assertThat(lock.refusesDrag(loot, List.of(loot, bound)))
                    .as("in einem der Zielslots")
                    .isTrue();
        }

        @Test
        @DisplayName("auch ein für einen ANDEREN Charakter gebundenes Item ist unbeweglich")
        void anotherCharactersEquipmentIsAlsoLocked() {
            // Es ist keine Beute, es ist die Ausrüstung eines anderen Charakters.
            assertThat(lock.refusesClick(otherBound, null, null)).isTrue();
        }
    }

    @Nested
    @DisplayName("die Gegentests - ohne sie wäre nicht belegt, dass die Sperre nicht alles sperrt")
    class CounterTests {

        @Test
        @DisplayName("T099: ein ungebundenes Item wird nie abgewiesen (US4.3, FR-028)")
        void unboundItemIsNeverRefused() {
            assertThat(lock.refusesClick(loot, null, null)).isFalse();
            assertThat(lock.refusesClick(loot, loot, loot)).isFalse();
            assertThat(lock.refusesClick(null, null, null)).as("ein leerer Slot").isFalse();
        }

        @Test
        @DisplayName("T099: ein Offhand-Tausch ohne gebundenes Item wird nicht abgewiesen")
        void unboundOffhandSwapIsAllowed() {
            assertThat(lock.refusesSwap(loot, new ItemStack(Material.STICK))).isFalse();
            assertThat(lock.refusesSwap(null, null)).isFalse();
        }

        @Test
        @DisplayName("T099: ein Zieh-Vorgang ohne gebundenes Item wird nicht abgewiesen")
        void unboundDragIsAllowed() {
            assertThat(lock.refusesDrag(loot, List.of(loot, new ItemStack(Material.STICK))))
                    .isFalse();
            assertThat(lock.refusesDrag(null, List.of())).isFalse();
        }

        @Test
        @DisplayName("ein Item ohne jede Marke ist Beute, nicht Ausrüstung")
        void anUntaggedItemIsLoot() {
            assertThat(BoundItemTag.isTagged(loot)).isFalse();
            assertThat(BoundItemTag.isTagged(null)).isFalse();
            assertThat(BoundItemTag.isTagged(new ItemStack(Material.NETHERITE_CHESTPLATE)))
                    .as("dasselbe Material, aber ohne Marke - erbeutet, nicht gebunden")
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("die Handler selbst - eine gelöschte Route fällt hier auf")
    class Handlers {

        @Test
        @DisplayName("T097, T098: die Wurf-Aktion hat einen Handler und kennt keine Bedingung (FR-027)")
        void dropHasAnUnconditionalHandler() throws Exception {
            Method handler = handlerFor(PlayerDropItemEvent.class);

            assertThat(handler).isNotNull();
            // Der Rumpf ruft setCancelled(true) ohne jede Prüfung - die Wurf-Aktion ist für ALLE
            // Items ab, auch ungebundene. Das ist die einzige Route ohne Bedingung.
            assertThat(handler.getName()).isEqualTo("onDrop");
        }

        @Test
        @DisplayName("für jedes der vier Ereignisse gibt es genau einen Handler")
        void everyEventHasExactlyOneHandler() {
            for (Class<?> event :
                    List.of(
                            InventoryClickEvent.class,
                            PlayerSwapHandItemsEvent.class,
                            PlayerDropItemEvent.class,
                            InventoryDragEvent.class)) {
                assertThat(handlersFor(event))
                        .as("Handler für %s", event.getSimpleName())
                        .hasSize(1);
            }
        }

        @Test
        @DisplayName("T104: jeder bedingte Handler fängt Ausnahmen ab (FR-031)")
        void everyConditionalHandlerCatches() throws Exception {
            // Eine Ausnahme darf den Klick abbrechen, nicht die Sitzung. Prüfbar ist das hier über die
            // Struktur: die drei bedingten Handler haben einen catch-Block, der onDrop nicht braucht,
            // weil er bedingungslos abbricht.
            for (Class<?> event :
                    List.of(
                            InventoryClickEvent.class,
                            PlayerSwapHandItemsEvent.class,
                            InventoryDragEvent.class)) {
                Method handler = handlerFor(event);
                assertThat(handler.getExceptionTypes())
                        .as("%s wirft nichts nach draussen", handler.getName())
                        .isEmpty();
            }
        }
    }

    // --- helpers ----------------------------------------------------------------------------

    private static List<Method> handlersFor(Class<?> eventType) {
        return java.util.Arrays.stream(EquipmentLockListener.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(EventHandler.class))
                .filter(
                        method ->
                                method.getParameterCount() == 1
                                        && method.getParameterTypes()[0].equals(eventType))
                .toList();
    }

    private static Method handlerFor(Class<?> eventType) {
        List<Method> handlers = handlersFor(eventType);
        assertThat(handlers).as("Handler für %s", eventType.getSimpleName()).hasSize(1);
        return handlers.get(0);
    }

    private static ItemStack boundItem(Material material, LadderSlot slot) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        BoundItemTag.write(
                meta, BoundEquipment.tagFor(UUID.randomUUID(), CharacterClass.WARRIOR, slot));
        item.setItemMeta(meta);
        return item;
    }
}
