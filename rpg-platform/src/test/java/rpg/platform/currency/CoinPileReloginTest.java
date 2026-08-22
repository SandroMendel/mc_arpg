package rpg.platform.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.currency.CurrencyConfig;

/**
 * Ein Haufen bleibt nach dem Wiedereinloggen sichtbar (FR-027a).
 *
 * <p><b>Der Fehler, den dieser Test festhaelt, kam vom Server</b>, nicht aus einem Testlauf:
 * {@code Player.showEntity} ist Zustand auf der <b>Verbindung</b>, nicht auf dem Entity. Nach einem
 * Relogin war der Haufen wieder unsichtbar - waehrend Vanillas Besitzmarke und die Charakterpruefung
 * beide weiter griffen. Unsichtbar und trotzdem aufhebbar ist die schlechteste der moeglichen
 * Kombinationen: der Spieler laeuft ueber etwas, das er nicht sieht.
 *
 * <p>Serverfrei pruefbar ist, <b>dass und wem gegenueber</b> die Anzeige erneut verlangt wird -
 * MockBukkit implementiert {@code setVisibleByDefault} nicht.
 */
class CoinPileReloginTest {

    private static final Logger QUIET = Logger.getLogger("coin-pile-relogin-test");

    private ServerMock server;
    private World world;
    private RecordingDisplay display;
    private CoinPileRegistry registry;

    private final UUID warrior = UUID.randomUUID();
    private final UUID mage = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        display = new RecordingDisplay();
        registry =
                new CoinPileRegistry(
                        config(), (id, amount, reason) -> true, Clock.systemUTC(), QUIET, display);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("beim Eintritt werden die eigenen Haufen wieder gezeigt")
    void pilesAreShownAgainOnEntry() {
        Item first = pile(warrior, 100L);
        Item second = pile(warrior, 200L);
        PlayerMock player = server.addPlayer();

        registry.showPilesTo(player, warrior);

        assertThat(display.shown)
                .as("sonst laeuft der Spieler ueber etwas, das er nicht sieht")
                .containsExactlyInAnyOrder(
                        Map.entry(first, player.getUniqueId()),
                        Map.entry(second, player.getUniqueId()));
    }

    @Test
    @DisplayName("die Haufen eines ANDEREN Charakters werden nicht gezeigt - auch nicht die eigenen")
    void anotherCharactersPilesStayHidden() {
        pile(mage, 500L);
        Item mine = pile(warrior, 100L);
        PlayerMock player = server.addPlayer();

        registry.showPilesTo(player, warrior);

        assertThat(display.shown)
                .as("ein Haufen gehoert dem Charakter, der ihn verdient hat (ADR-011)")
                .containsExactly(Map.entry(mine, player.getUniqueId()));
    }

    @Test
    @DisplayName("ein bereits abgeraeumter Haufen wird nicht gezeigt")
    void agonePileIsNotShown() {
        Item gone = pile(warrior, 100L);
        gone.remove();
        PlayerMock player = server.addPlayer();

        registry.showPilesTo(player, warrior);

        assertThat(display.shown).isEmpty();
    }

    @Test
    @DisplayName("ohne eigene Haufen passiert nichts")
    void withoutPilesNothingHappens() {
        PlayerMock player = server.addPlayer();

        registry.showPilesTo(player, warrior);

        assertThat(display.shown).isEmpty();
    }

    // --- Hilfsmittel -----------------------------------------------------

    private Item pile(UUID characterId, long amount) {
        ItemStack stack = new ItemStack(Material.GOLD_NUGGET, 1);
        ItemMeta meta = stack.getItemMeta();
        CoinPileTag.write(meta, amount, characterId, System.currentTimeMillis());
        stack.setItemMeta(meta);
        Item pile = world.dropItem(new Location(world, 0.5, 64.0, 0.5), stack);
        registry.register(pile, characterId);
        return pile;
    }

    private static CurrencyConfig config() {
        return new CurrencyConfig(
                0L, 4L, Map.of(), Duration.ofSeconds(120), 3.0d, 400, Duration.ofDays(30), 45);
    }

    /** Haelt fest, wem welcher Haufen gezeigt wurde. */
    private static final class RecordingDisplay implements CoinPile.PilePlatform {

        final List<Map.Entry<Item, UUID>> shown = new ArrayList<>();

        @Override
        public void hideFromEveryone(Item pile) {}

        @Override
        public void showTo(Item pile, Player player) {
            shown.add(Map.entry(pile, player.getUniqueId()));
        }

        @Override
        public void harden(Item pile, UUID ownerId, int spawnTicksLived) {}
    }
}
