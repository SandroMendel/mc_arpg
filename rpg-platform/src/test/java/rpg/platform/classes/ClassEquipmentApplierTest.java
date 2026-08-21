package rpg.platform.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.classes.BoundEquipment;
import rpg.core.classes.ClassProgress;
import rpg.core.session.CharacterClass;

/**
 * T084 und T085 - der Sollzustand landet dort, wo er hingehört, und heilt sich selbst.
 */
class ClassEquipmentApplierTest {

    private static final Logger QUIET = Logger.getLogger("class-equipment-applier-test");

    private ServerMock server;
    private PlayerMock player;
    private ClassEquipmentApplier applier;
    private Map<UUID, CharacterClass> classes;
    private Map<UUID, ClassProgress> progress;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        player = server.addPlayer();
        classes = new HashMap<>();
        progress = new HashMap<>();
        BoundEquipment bound =
                new BoundEquipment(
                        PlatformClassFixture.config(),
                        id -> Optional.ofNullable(classes.get(id)),
                        id -> Optional.ofNullable(progress.get(id)));
        applier =
                new ClassEquipmentApplier(
                        bound, new BoundItemFactory(PlatformClassFixture.messages()), QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("T084: alle vier Rüstungsslots und der Waffenslot werden gesetzt")
    void theFullSetIsApplied() {
        UUID character = character(CharacterClass.WARRIOR, 1, 1);

        assertThat(applier.apply(player, character)).isTrue();

        for (EquipmentSlot slot :
                new EquipmentSlot[] {
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
                }) {
            assertThat(filled(player.getInventory().getItem(slot))).as("Slot %s", slot).isTrue();
        }
        assertThat(filled(player.getInventory().getItem(ClassEquipmentApplier.WEAPON_SLOT)))
                .isTrue();
    }

    @Test
    @DisplayName("T084: jedes angelegte Teil trägt die Bindungsmarke")
    void everyAppliedPieceIsTagged() {
        UUID character = character(CharacterClass.WARRIOR, 1, 1);

        applier.apply(player, character);

        assertThat(BoundItemTag.isTagged(player.getInventory().getItem(EquipmentSlot.CHEST)))
                .isTrue();
        assertThat(
                        BoundItemTag.isTagged(
                                player.getInventory().getItem(ClassEquipmentApplier.WEAPON_SLOT)))
                .isTrue();
    }

    @Test
    @DisplayName("T084: eine höhere Stufe ergibt anderes Material")
    void ahigherTierAppliesADifferentMaterial() {
        UUID first = character(CharacterClass.WARRIOR, 1, 1);
        applier.apply(player, first);
        Material atTierOne = player.getInventory().getItem(EquipmentSlot.CHEST).getType();

        UUID second = character(CharacterClass.WARRIOR, 2, 2);
        applier.apply(player, second);

        assertThat(player.getInventory().getItem(EquipmentSlot.CHEST).getType())
                .isNotEqualTo(atTierOne);
    }

    @Test
    @DisplayName("T085: ein fehlendes gebundenes Item ist beim nächsten Anlegen wieder da (FR-023)")
    void amissingBoundItemHealsItself() {
        UUID character = character(CharacterClass.WARRIOR, 1, 1);
        applier.apply(player, character);
        player.getInventory().setItem(EquipmentSlot.CHEST, null);
        assertThat(filled(player.getInventory().getItem(EquipmentSlot.CHEST))).isFalse();

        applier.apply(player, character);

        assertThat(filled(player.getInventory().getItem(EquipmentSlot.CHEST)))
                .as("die Stufe ist die Wahrheit, das Item ihre Darstellung")
                .isTrue();
    }

    @Test
    @DisplayName("ein Charakter ohne Klasse bekommt nichts - keine Vorgabe wird erfunden")
    void acharacterWithoutAClassGetsNothing() {
        assertThat(applier.apply(player, UUID.randomUUID())).isFalse();
        assertThat(filled(player.getInventory().getItem(EquipmentSlot.CHEST))).isFalse();
    }

    @Test
    @DisplayName("ein ungebundenes Item im Waffenslot wird verschoben, nicht vernichtet")
    void anUnboundItemInTheWeaponSlotIsMovedNotDestroyed() {
        UUID character = character(CharacterClass.WARRIOR, 1, 1);
        ItemStack loot = new ItemStack(Material.DIAMOND, 5);
        player.getInventory().setItem(ClassEquipmentApplier.WEAPON_SLOT, loot);

        assertThat(applier.apply(player, character)).isTrue();

        assertThat(BoundItemTag.isTagged(
                        player.getInventory().getItem(ClassEquipmentApplier.WEAPON_SLOT)))
                .as("die Waffe liegt jetzt dort")
                .isTrue();
        assertThat(countOf(player, Material.DIAMOND))
                .as("und die Beute ist noch im Inventar, nur woanders")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("bei vollem Inventar behält der Spieler sein Item und die Waffe wartet (ADR-018)")
    void withAFullInventoryThePlayersItemWins() {
        UUID character = character(CharacterClass.WARRIOR, 1, 1);
        ItemStack loot = new ItemStack(Material.DIAMOND, 5);
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.COBBLESTONE, 64));
        }
        player.getInventory().setItem(ClassEquipmentApplier.WEAPON_SLOT, loot);

        boolean applied = applier.apply(player, character);

        assertThat(applied).as("nicht vollständig angelegt - und das wird gemeldet").isFalse();
        assertThat(player.getInventory().getItem(ClassEquipmentApplier.WEAPON_SLOT))
                .as("nichts verloren: das Item des Spielers liegt unverändert da")
                .isEqualTo(loot);
    }

    /** MockBukkit meldet einen leeren Slot als AIR, nicht als null - beides ist leer. */
    private static boolean filled(ItemStack item) {
        return item != null && !item.getType().isAir();
    }

    private static int countOf(PlayerMock player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private UUID character(CharacterClass id, int armorTier, int weaponTier) {
        UUID characterId = UUID.randomUUID();
        classes.put(characterId, id);
        progress.put(
                characterId,
                new ClassProgress(
                        characterId, armorTier, weaponTier, ClassProgress.CURRENT_DATA_VERSION, 0L));
        return characterId;
    }
}
