package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * FR-005 und SC-003 — der Migrationspfad, <b>bevor er gebraucht wird</b>.
 *
 * <p>Solange {@code CURRENT} auf 1 steht, gibt es nichts anzuheben. Der Test prüft trotzdem: dass
 * ein Exemplar der aktuellen Fassung <em>nicht</em> angefasst wird, dass ein Exemplar einer älteren
 * angehoben würde, und dass ein Exemplar aus einer <em>neueren</em> Fassung in Ruhe gelassen statt
 * geraten wird.
 *
 * <p><b>Warum das jetzt schon dasteht.</b> Ein Migrationspfad lässt sich nachträglich nicht
 * erfinden. Wer Version 2 einführt und dann merkt, dass Version-1-Exemplare keinen Weg nach oben
 * haben, steht vor der Wahl zwischen Datenverlust und einer Sonderbehandlung, die für immer bleibt.
 * Constitution IV verlangt den Pfad deshalb im Voraus.
 */
class ItemSchemaMigrationTest {

    private ItemSchemaMigration migration;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        migration = new ItemSchemaMigration(Logger.getLogger(getClass().getName()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("ein Exemplar der aktuellen Fassung wird nicht angefasst")
    void aCurrentItemIsLeftAlone() {
        ItemStack stack = new ItemStack(Material.POTION);
        ItemTag.mark(stack, "potion.test", ItemSchemaMigration.CURRENT);

        assertThat(migration.migrate(stack)).isFalse();
        assertThat(ItemTag.schemaOf(stack)).hasValue(ItemSchemaMigration.CURRENT);
    }

    @Test
    @DisplayName("ein Exemplar einer aelteren Fassung wird angehoben, verlustfrei")
    void anOlderItemIsLifted() {
        ItemStack stack = new ItemStack(Material.POTION);
        writeSchema(stack, "potion.test", 0);

        assertThat(migration.migrate(stack)).isTrue();
        assertThat(ItemTag.schemaOf(stack)).hasValue(ItemSchemaMigration.CURRENT);
        assertThat(ItemTag.templateOf(stack))
                .as("verlustfrei - die Vorlage ueberlebt die Migration")
                .contains("potion.test");
    }

    @Test
    @DisplayName("ein Exemplar aus einer NEUEREN Fassung bleibt unangetastet")
    void aNewerItemIsNotDowngraded() {
        // Kommt vor, wenn ein Server zurueckgebaut wird. Herunterzurechnen waere geraten, und
        // Raten an Spielerdaten ist die schlechteste aller Antworten (Constitution VI).
        ItemStack stack = new ItemStack(Material.POTION);
        writeSchema(stack, "potion.test", ItemSchemaMigration.CURRENT + 5);

        assertThat(migration.migrate(stack)).isFalse();
        assertThat(ItemTag.schemaOf(stack)).hasValue(ItemSchemaMigration.CURRENT + 5);
    }

    @Test
    @DisplayName("ein fremder Gegenstand wird nicht adoptiert")
    void aForeignItemIsNotAdopted() {
        ItemStack vanilla = new ItemStack(Material.STONE);

        assertThat(migration.migrate(vanilla)).isFalse();
        assertThat(ItemTag.schemaOf(vanilla))
                .as("ihm eine Version zu geben hiesse, ihn zu uebernehmen")
                .isEmpty();
    }

    /** Schreibt eine beliebige Fassung - auch eine, die {@link ItemTag} selbst nie schreiben würde. */
    private static void writeSchema(ItemStack stack, String templateKey, int version) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer()
                .set(
                        NamespacedKey.fromString("rpg:item_template"),
                        PersistentDataType.STRING,
                        templateKey);
        meta.getPersistentDataContainer()
                .set(NamespacedKey.fromString("rpg:item_schema"), PersistentDataType.INTEGER, version);
        stack.setItemMeta(meta);
    }
}
