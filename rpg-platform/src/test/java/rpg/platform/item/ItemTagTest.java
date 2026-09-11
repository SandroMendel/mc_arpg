package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Der Vermerk am Gegenstand — zwei Werte, und was passiert, wenn keiner da ist.
 *
 * <p>Nach dem Muster von {@code MobKindTagTest}, und mit derselben zweiten Hälfte: ein Stapel
 * <b>ohne</b> Vermerk ist kein Fehler, sondern der Normalfall. Im Pfad jedes Inventarklicks liegen
 * überwiegend Vanilla-Gegenstände und leere Slots.
 */
class ItemTagTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Vorlage und Schema-Version lassen sich schreiben und wieder lesen")
    void templateAndSchemaAreWrittenAndReadBack() {
        ItemStack stack = new ItemStack(Material.POTION);

        ItemTag.mark(stack, "potion.minor-healing", 1);

        assertThat(ItemTag.templateOf(stack)).contains("potion.minor-healing");
        assertThat(ItemTag.schemaOf(stack)).hasValue(1);
        assertThat(ItemTag.isOurs(stack)).isTrue();
    }

    @Test
    @DisplayName("ein zweites Schreiben ersetzt, statt sich zu verdoppeln")
    void markingTwiceReplacesInsteadOfDuplicating() {
        ItemStack stack = new ItemStack(Material.POTION);

        ItemTag.mark(stack, "potion.minor-healing", 1);
        ItemTag.mark(stack, "potion.healing", 1);

        assertThat(ItemTag.templateOf(stack)).contains("potion.healing");
    }

    @Test
    @DisplayName("ein Vanilla-Gegenstand antwortet leer - nicht mit null")
    void aVanillaItemAnswersEmpty() {
        ItemStack plain = new ItemStack(Material.STONE);

        assertThat(ItemTag.templateOf(plain)).isEmpty();
        assertThat(ItemTag.schemaOf(plain)).isEmpty();
        assertThat(ItemTag.isOurs(plain)).isFalse();
    }

    @Test
    @DisplayName("null und Luft sind kein Fehler - ein leerer Slot ist der Normalfall")
    void nullAndAirAreNotErrors() {
        assertThat(ItemTag.templateOf(null)).isEmpty();
        assertThat(ItemTag.templateOf(new ItemStack(Material.AIR))).isEmpty();
        assertThat(ItemTag.isOurs(null)).isFalse();
    }

    @Test
    @DisplayName("FR-006 - das Feld fuer Custom-Model-Data bleibt im Vanilla-Betrieb ungesetzt")
    void customModelDataStaysUnsetInVanilla() {
        // ADR-005 verlangt das Feld reserviert, nicht benutzt. Ein gesetzter Wert ohne Resource
        // Pack waere ein unsichtbarer Gegenstand - schlimmer als gar kein Feld.
        ItemStack stack = new ItemStack(Material.POTION);
        ItemTag.mark(stack, "potion.minor-healing", 1);

        assertThat(stack.getItemMeta().hasCustomModelData())
                .as("ohne Vorlage mit model-data bleibt das Feld leer")
                .isFalse();
    }
}
