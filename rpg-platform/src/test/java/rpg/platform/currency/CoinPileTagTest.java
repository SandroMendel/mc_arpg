package rpg.platform.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * T058 - was ein Haufen traegt, und warum zwei davon nie aehnlich sind.
 *
 * <p>Der letzte Test ist der wichtigste des ganzen Blocks auf der Paper-Seite: waeren zwei Haufen
 * aehnlich, legte Vanilla sie zusammen und addierte dabei die <b>Stueckzahl</b>. Zwei Haufen à 500
 * ergaeben einen Stapel der Groesse 2 mit dem Wert 500 - der Spieler verloere die Haelfte, und nichts
 * saehe falsch aus.
 */
class CoinPileTagTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Betrag, Charakter und Zeitpunkt kommen unveraendert zurueck")
    void allThreeValuesRoundTrip() {
        UUID character = UUID.randomUUID();
        ItemStack stack = pile(1234L, character, 1_700_000_000_000L);

        assertThat(CoinPileTag.amountOf(stack)).hasValue(1234L);
        assertThat(CoinPileTag.characterOf(stack)).hasValue(character);
        assertThat(CoinPileTag.createdAtOf(stack)).hasValue(1_700_000_000_000L);
        assertThat(CoinPileTag.isCoinPile(stack)).isTrue();
    }

    @Test
    @DisplayName("ein gewoehnlicher Gegenstand ist kein Haufen und kostet keine Ausnahme")
    void anOrdinaryItemIsNotAPile() {
        ItemStack ordinary = new ItemStack(Material.DIAMOND_SWORD, 1);

        assertThat(CoinPileTag.isCoinPile(ordinary)).isFalse();
        assertThat(CoinPileTag.amountOf(ordinary)).isEmpty();
        assertThat(CoinPileTag.characterOf(ordinary)).isEmpty();
        assertThat(CoinPileTag.amountOf(null)).as("und null erst recht nicht").isEmpty();
    }

    @Test
    @DisplayName("ZWEI HAUFEN SIND NIE isSimilar - sonst legte Vanilla sie zusammen")
    void twoPilesAreNeverSimilar() {
        UUID character = UUID.randomUUID();

        // Gleicher Charakter, gleicher Betrag, gleicher Zeitpunkt: alles gleich ausser der
        // Haufenkennung, die genau dafuer existiert.
        ItemStack first = pile(500L, character, 1_700_000_000_000L);
        ItemStack second = pile(500L, character, 1_700_000_000_000L);

        assertThat(first.isSimilar(second))
                .as("waeren sie aehnlich, wuerde aus 500 + 500 ein Stapel der Groesse 2 mit 500")
                .isFalse();
    }

    @Test
    @DisplayName("der Betrag laesst sich erhoehen - das ist der Weg des Zusammenlegens")
    void theAmountCanBeRaised() {
        UUID character = UUID.randomUUID();
        ItemStack stack = pile(500L, character, 1L);

        ItemMeta meta = stack.getItemMeta();
        CoinPileTag.writeAmount(meta, 1300L);
        stack.setItemMeta(meta);

        assertThat(CoinPileTag.amountOf(stack)).hasValue(1300L);
        assertThat(CoinPileTag.characterOf(stack))
                .as("der Berechtigte aendert sich dabei nicht")
                .hasValue(character);
    }

    @Test
    @DisplayName("eine unlesbare Charakterkennung wirft nicht, sie macht den Haufen unbeanspruchbar")
    void amalformedCharacterIdIsNotAnException() {
        ItemStack stack = new ItemStack(Material.GOLD_NUGGET, 1);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer()
                .set(
                        CoinPileTag.CHARACTER,
                        org.bukkit.persistence.PersistentDataType.STRING,
                        "kein-uuid");
        stack.setItemMeta(meta);

        assertThat(CoinPileTag.characterOf(stack))
                .as("eine Ausnahme im Aufhebepfad duerfte keinen Spieler haengen lassen")
                .isEmpty();
    }

    private static ItemStack pile(long amount, UUID character, long createdAt) {
        ItemStack stack = new ItemStack(Material.GOLD_NUGGET, 1);
        ItemMeta meta = stack.getItemMeta();
        CoinPileTag.write(meta, amount, character, createdAt);
        stack.setItemMeta(meta);
        return stack;
    }
}
