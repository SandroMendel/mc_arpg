package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * SC-003, die Charakterhälfte — <b>ein Gegenstand gehört dem Inventar, in dem er liegt.</b>
 *
 * <p>Dass der Trank des ersten Charakters beim zweiten nicht auftaucht, garantiert <b>B03</b>:
 * {@code CharacterInventory} führt Rucksack und Enderchest je Charakter, nicht je Spieler
 * (ADR-011). B11 muss dafür nichts bauen — es muss nur nichts <em>dazwischenbauen</em>.
 *
 * <p>Genau das prüft dieser Test. Trüge der Vermerk eine Spieler- oder Charakterkennung, gäbe es
 * eine zweite Wahrheit darüber, wem ein Gegenstand gehört — und zwei Wahrheiten stimmen genau bis
 * zum ersten Charakterwechsel überein. Der Vermerk trägt deshalb <b>keine Identität</b>, nur die
 * Vorlage und die Schema-Version.
 *
 * <p><b>Die eine Stelle, an der Eigentum doch vorkommt</b>, ist gefallene Beute — und sie liegt
 * bewusst woanders: {@code rpg.platform.drop} hält den Anspruch an der <em>Entität</em>, solange
 * sie in der Welt liegt, und er endet, sobald jemand sie aufhebt. Am Gegenstand im Inventar hängt
 * er nie.
 */
class ItemCarriesNoOwnerTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("der Vermerk traegt keine Spieler- und keine Charakterkennung")
    void theTagCarriesNoIdentity() {
        ItemStack stack = new ItemStack(Material.POTION);
        ItemTag.mark(stack, "potion.test", 1);

        List<String> keys = new ArrayList<>();
        for (NamespacedKey key : stack.getItemMeta().getPersistentDataContainer().getKeys()) {
            keys.add(key.toString());
        }

        assertThat(keys)
                .as(
                        "eine Kennung hier waere eine zweite Wahrheit darueber, wem ein Gegenstand"
                                + " gehoert - und B03 haelt die erste (ADR-011)")
                .containsExactlyInAnyOrder("rpg:item_template", "rpg:item_schema");
        assertThat(keys).noneMatch(key -> key.contains("owner") || key.contains("player")
                || key.contains("character"));
    }

    @Test
    @DisplayName("derselbe Gegenstand ist fuer jeden Charakter derselbe - er kennt keinen")
    void theSameItemIsTheSameForEveryone() {
        ItemStack stack = new ItemStack(Material.POTION);
        ItemTag.mark(stack, "potion.test", 1);

        UUID characterA = UUID.randomUUID();
        UUID characterB = UUID.randomUUID();

        // Es gibt keine Abfrage, die den Charakter entgegennimmt - und das ist die Aussage.
        // Welcher Charakter den Trank sieht, entscheidet allein, wessen Inventar geladen ist.
        assertThat(ItemTag.templateOf(stack)).contains("potion.test");
        assertThat(characterA).isNotEqualTo(characterB);
    }

    @Test
    @DisplayName("und nirgends in diesem Paket haengt eine Identitaet AM GEGENSTAND")
    void nothingInThisPackageTiesAnIdentityToAnItem() throws IOException {
        // Die zweite Art, dieselbe Zusage zu brechen: nicht im Vermerk, sondern in einer Tabelle
        // daneben. Ein `Map<ItemStack, UUID>` waere dasselbe Problem mit mehr Schritten.
        //
        // Gesucht wird deshalb nach genau dieser Form - nicht nach dem Wort "ownerCharacterId".
        // Der erste Anlauf tat das und schlug bei LootDropListener an, der eine Charakterkennung
        // an OwnedDrops WEITERREICHT, ohne sie irgendwo am Gegenstand festzumachen. Das ist
        // genau, was passieren soll, und eine Ausnahmeliste haette die Pruefung stumpf gemacht.
        List<String> offenders = new ArrayList<>();

        for (Path source : itemSources()) {
            String code = Files.readString(source);
            for (String shape :
                    List.of(
                            "Map<ItemStack,",
                            "Map<Item,",
                            "Map<org.bukkit.inventory.ItemStack,",
                            "Map<org.bukkit.entity.Item,")) {
                if (code.contains(shape)) {
                    offenders.add(source.getFileName() + " keys identity by " + shape);
                }
            }
        }

        assertThat(offenders)
                .as(
                        "Eigentum gehoert zu LIEGENDER Beute (rpg.platform.drop, an der Entitaet)"
                                + " und endet beim Aufheben - niemals an einem Inventarposten")
                .isEmpty();
    }

    private static List<Path> itemSources() throws IOException {
        Path root = Path.of("src", "main", "java", "rpg", "platform", "item");
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}
