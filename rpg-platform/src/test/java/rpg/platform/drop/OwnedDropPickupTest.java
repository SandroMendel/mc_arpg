package rpg.platform.drop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * <b>Das Schloss, das unabhängig vom Sehen gilt.</b>
 *
 * <p>Unsichtbarkeit ist die ehrliche Darstellung, aber niemals die Autorität (Constitution VI).
 * Dieser Test prüft, was passiert, wenn jemand es trotzdem versucht — und wenn Vanillas
 * Eigentumskennzeichen ihn durchgelassen hat, weil es nur Spieler kennt und nicht Charaktere.
 *
 * <p><b>Der wichtigste Fall ist der zweite Charakter desselben Spielers.</b> Für Vanilla ist er
 * derselbe Eigentümer; für ADR-011 ist er ein anderer. Ohne die Prüfung hier sammelte Charakter B
 * ein, was Charakter A verdient hat — und das fiele erst auf, wenn jemand sich beschwert.
 */
class OwnedDropPickupTest {

    private ServerMock server;
    private WorldMock world;
    private OwnedDropRegistry registry;
    private OwnedDropPickupListener listener;
    private final Map<UUID, UUID> activeCharacters = new HashMap<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        registry = new OwnedDropRegistry(new NoDisplay());
        listener =
                new OwnedDropPickupListener(
                        registry, playerId -> Optional.ofNullable(activeCharacters.get(playerId)));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("der Berechtigte hebt auf, und der Vermerk faellt weg")
    void theEntitledCharacterPicksUp() {
        Player owner = server.addPlayer();
        UUID characterId = UUID.randomUUID();
        activeCharacters.put(owner.getUniqueId(), characterId);
        Item drop = drop();
        registry.register(drop, characterId);

        PlayerAttemptPickupItemEvent event = attempt(owner, drop);
        listener.onPickup(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(registry.ownerOf(drop.getUniqueId()))
                .as("ab jetzt ein Inventarposten wie jeder andere")
                .isEmpty();
    }

    @Test
    @DisplayName("ein fremder Spieler hebt NICHTS auf")
    void aStrangerPicksUpNothing() {
        Player owner = server.addPlayer();
        Player stranger = server.addPlayer();
        UUID characterId = UUID.randomUUID();
        activeCharacters.put(owner.getUniqueId(), characterId);
        activeCharacters.put(stranger.getUniqueId(), UUID.randomUUID());
        Item drop = drop();
        registry.register(drop, characterId);

        PlayerAttemptPickupItemEvent event = attempt(stranger, drop);
        listener.onPickup(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(registry.ownerOf(drop.getUniqueId()))
                .as("der Gegenstand bleibt liegen und gehoert weiter dem, der ihn verdient hat")
                .contains(characterId);
    }

    @Test
    @DisplayName("der ZWEITE Charakter desselben Spielers auch nicht - der Fall, den Vanilla nicht sieht")
    void theSecondCharacterOfTheSamePlayerPicksUpNothing() {
        Player player = server.addPlayer();
        UUID earnedIt = UUID.randomUUID();
        UUID nowPlaying = UUID.randomUUID();
        activeCharacters.put(player.getUniqueId(), nowPlaying);
        Item drop = drop();
        registry.register(drop, earnedIt);

        PlayerAttemptPickupItemEvent event = attempt(player, drop);
        listener.onPickup(event);

        assertThat(event.isCancelled())
                .as(
                        "fuer Vanillas setOwner ist das derselbe Eigentuemer - fuer ADR-011 nicht,"
                                + " und diese Pruefung ist der ganze Grund, aus dem es sie gibt")
                .isTrue();
    }

    @Test
    @DisplayName("ein Spieler ohne Charakter hebt nichts auf")
    void aPlayerWithoutACharacterPicksUpNothing() {
        Player player = server.addPlayer();
        Item drop = drop();
        registry.register(drop, UUID.randomUUID());

        PlayerAttemptPickupItemEvent event = attempt(player, drop);
        listener.onPickup(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("ein gewoehnlicher Gegenstand wird nicht angefasst - der haeufigste Pfad")
    void anOrdinaryItemIsLeftAlone() {
        Player player = server.addPlayer();
        activeCharacters.put(player.getUniqueId(), UUID.randomUUID());
        Item vanilla = drop(); // nicht registriert

        PlayerAttemptPickupItemEvent event = attempt(player, vanilla);
        listener.onPickup(event);

        assertThat(event.isCancelled())
                .as("fast jeder Gegenstand, den ein Spieler beruehrt, geht hier durch")
                .isFalse();
    }

    private Item drop() {
        return world.dropItem(new Location(world, 0.5, 65.0, 0.5), new ItemStack(Material.POTION));
    }

    private static PlayerAttemptPickupItemEvent attempt(Player player, Item item) {
        return new PlayerAttemptPickupItemEvent(player, item, 0);
    }

    /** MockBukkit kann die Plattformaufrufe nicht - hier werden sie auch nicht gebraucht. */
    private static final class NoDisplay implements OwnedDropPlatform {
        @Override
        public void hideFromEveryone(Item drop) {}

        @Override
        public void showTo(Item drop, Player player) {}

        @Override
        public void harden(Item drop, UUID ownerId, int spawnTicksLived) {}
    }
}
