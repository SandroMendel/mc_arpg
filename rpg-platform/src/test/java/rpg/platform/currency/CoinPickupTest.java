package rpg.platform.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.currency.BookingReason;
import rpg.core.currency.BookingResult;
import rpg.core.currency.Currency;
import rpg.core.currency.CurrencyConfig;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.session.CharacterClass;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionRegistry;

/**
 * T060, T061 - das Aufheben bucht, und wer keinen Anspruch hat, hebt nicht auf.
 *
 * <p>Der vierte Test ist der, den Vanilla allein nicht leisten kann: {@code Item.setOwner} kennt
 * <b>Spieler</b>, dieser Block kennt <b>Charaktere</b> (ADR-011). Ein Spieler mit drei Charakteren
 * wuerde sonst mit Charakter B einsammeln, was Charakter A verdient hat - und zwar nur beim Wechsel
 * mitten in der Sitzung, also genau der Fehler, den niemand reproduziert.
 */
class CoinPickupTest {

    private static final Logger QUIET = Logger.getLogger("coin-pickup-test");

    private ServerMock server;
    private World world;
    private PlayerMock player;
    private CoinPickupListener listener;
    private RecordingCurrency currency;
    private CoinPileRegistry registry;
    private final Map<UUID, PlayerCharacter> activeCharacters = new HashMap<>();

    private PlayerCharacter warriorChar;
    private PlayerCharacter mageChar;
    private UUID warrior;
    private UUID mage;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        player = server.addPlayer();
        warriorChar = PlayerCharacter.create(player.getUniqueId(), CharacterClass.WARRIOR, java.time.Instant.EPOCH);
        mageChar = PlayerCharacter.create(player.getUniqueId(), CharacterClass.MAGE, java.time.Instant.EPOCH);
        warrior = warriorChar.characterId();
        mage = mageChar.characterId();
        activeCharacters.put(player.getUniqueId(), warriorChar);

        currency = new RecordingCurrency();
        registry =
                new CoinPileRegistry(
                        config(), (id, amount, reason) -> true, java.time.Clock.systemUTC(), QUIET);
        listener = new CoinPickupListener(currency, new TestSessions(), registry, messages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("der Berechtigte hebt auf: gutgeschrieben, Haufen weg, NICHTS im Inventar")
    void theEntitledPicksItUp() {
        Item pile = pile(warrior, 500L);

        PlayerAttemptPickupItemEvent event = attempt(pile);
        listener.onPickup(event);

        assertThat(event.isCancelled())
                .as("ein Coin-Haufen darf nie in einem Inventar landen (FR-033)")
                .isTrue();
        assertThat(currency.bookings)
                .containsExactly(
                        new RecordingCurrency.Booking(warrior, 500L, BookingReason.PILE_PICKED_UP));
        assertThat(pile.isValid()).isFalse();
        assertThat(player.getInventory().contains(Material.GOLD_NUGGET))
                .as("und ganz sicher nicht als Gegenstand")
                .isFalse();
    }

    @Test
    @DisplayName("ein gewoehnlicher Gegenstand wird gar nicht angefasst")
    void anOrdinaryItemIsLeftAlone() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD, 1);
        Item dropped = world.dropItem(new Location(world, 0.5, 64.0, 0.5), sword);

        PlayerAttemptPickupItemEvent event = attempt(dropped);
        listener.onPickup(event);

        assertThat(event.isCancelled())
                .as("fast jeder Gegenstand, den ein Spieler beruehrt, geht uns nichts an")
                .isFalse();
        assertThat(currency.bookings).isEmpty();
    }

    @Test
    @DisplayName("ein fremder Haufen wird nicht aufgehoben und bleibt liegen")
    void aStrangersPileStaysWhereItIs() {
        Item pile = pile(UUID.randomUUID(), 500L);

        PlayerAttemptPickupItemEvent event = attempt(pile);
        listener.onPickup(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(currency.bookings).isEmpty();
        assertThat(pile.isValid())
                .as("er gehoert weiterhin dem, der ihn verdient hat, und verfaellt mit seiner Frist")
                .isTrue();
    }

    @Test
    @DisplayName("DER CHARAKTERWECHSEL: derselbe Spieler, anderer Charakter - hebt NICHT auf")
    void switchingCharacterLosesTheClaim() {
        Item pile = pile(warrior, 500L);

        // Derselbe Spieler wechselt auf seinen Magier. Vanillas setOwner wuerde ihn weiterhin
        // durchlassen - es kennt nur den Spieler.
        activeCharacters.put(player.getUniqueId(), mageChar);

        listener.onPickup(attempt(pile));

        assertThat(currency.bookings).as("ADR-011: der Stand haengt am Charakter").isEmpty();
        assertThat(pile.isValid()).isTrue();

        // Zurueckwechseln - und der Anspruch ist wieder da.
        activeCharacters.put(player.getUniqueId(), warriorChar);
        listener.onPickup(attempt(pile));

        assertThat(currency.bookings).hasSize(1);
        assertThat(pile.isValid()).isFalse();
    }

    @Test
    @DisplayName("ohne aktiven Charakter wird nichts gebucht")
    void withoutAnActiveCharacterNothingIsBooked() {
        Item pile = pile(warrior, 500L);
        activeCharacters.remove(player.getUniqueId());

        listener.onPickup(attempt(pile));

        assertThat(currency.bookings).isEmpty();
        assertThat(pile.isValid()).isTrue();
    }

    @Test
    @DisplayName("scheitert die Buchung, bleibt der Haufen liegen - die Coins existieren noch")
    void aRefusedBookingLeavesThePile() {
        Item pile = pile(warrior, 500L);
        currency.refuse(BookingResult.WOULD_OVERFLOW);

        listener.onPickup(attempt(pile));

        assertThat(pile.isValid()).as("der Spieler kann spaeter wiederkommen").isTrue();
        assertThat(registry.size())
                .as("und er zaehlt weiter gegen die Deckelung, denn er liegt ja noch da")
                .isEqualTo(1);
        assertThat(currency.bookings).isEmpty();
    }

    @Test
    @DisplayName("ein Haufen ohne Betrag wird aus der Welt genommen, aber nicht gebucht")
    void amalformedPileIsRemovedWithoutBooking() {
        ItemStack stack = new ItemStack(Material.GOLD_NUGGET, 1);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer()
                .set(
                        CoinPileTag.CHARACTER,
                        org.bukkit.persistence.PersistentDataType.STRING,
                        warrior.toString());
        stack.setItemMeta(meta);
        Item pile = world.dropItem(new Location(world, 0.5, 64.0, 0.5), stack);

        listener.onPickup(attempt(pile));

        assertThat(currency.bookings).isEmpty();
        assertThat(pile.isValid())
                .as("etwas Unbeanspruchbares soll nicht liegen bleiben")
                .isFalse();
    }

    // --- Hilfsmittel -----------------------------------------------------

    private Item pile(UUID characterId, long amount) {
        ItemStack stack = new ItemStack(Material.GOLD_NUGGET, 1);
        ItemMeta meta = stack.getItemMeta();
        CoinPileTag.write(meta, amount, characterId, 1L);
        stack.setItemMeta(meta);
        Item pile = world.dropItem(new Location(world, 0.5, 64.0, 0.5), stack);
        registry.register(pile, characterId);
        return pile;
    }

    private PlayerAttemptPickupItemEvent attempt(Item pile) {
        return new PlayerAttemptPickupItemEvent(player, pile, 0);
    }

    private static Messages messages() {
        return new MapMessages(
                Map.of(
                        "currency.pile.picked-up", "Picked up {amount} coins.",
                        "currency.rejected.overflow", "Too much.",
                        "currency.rejected.not-enough", "Not enough.",
                        "currency.rejected.invalid-amount", "Invalid.",
                        "currency.rejected.no-character", "No character."));
    }

    private static CurrencyConfig config() {
        return new CurrencyConfig(
                0L, 4L, Map.of(), Duration.ofSeconds(120), 3.0d, 400, Duration.ofDays(30), 45);
    }

    /** Haelt fest, was gebucht wurde - und kann sich weigern. */
    private static final class RecordingCurrency implements Currency {

        record Booking(UUID characterId, long amount, BookingReason reason) {}

        final java.util.List<Booking> bookings = new java.util.ArrayList<>();
        private BookingResult refusal;

        void refuse(BookingResult result) {
            this.refusal = result;
        }

        @Override
        public OptionalLong balanceOf(UUID characterId) {
            return OptionalLong.of(0L);
        }

        @Override
        public long balanceOrZero(UUID characterId) {
            return 0L;
        }

        @Override
        public boolean canAfford(UUID characterId, long amount) {
            return true;
        }

        @Override
        public BookingResult credit(UUID characterId, long amount, BookingReason reason) {
            if (refusal != null) {
                return refusal;
            }
            bookings.add(new Booking(characterId, amount, reason));
            return BookingResult.OK;
        }

        @Override
        public BookingResult debit(UUID characterId, long amount, BookingReason reason) {
            throw new UnsupportedOperationException("not part of what this test is about");
        }
    }

    /** Sitzungen, deren aktiver Charakter der Test umschaltet. */
    private final class TestSessions implements SessionRegistry {

        @Override
        public Optional<PlayerSession> find(UUID playerId) {
            PlayerCharacter active = activeCharacters.get(playerId);
            if (active == null) {
                return Optional.empty();
            }
            return Optional.of(new PlayerSession(playerId, active, java.util.List.of(active)));
        }

        @Override
        public PlayerSession require(UUID playerId) {
            return find(playerId).orElseThrow();
        }

        @Override
        public boolean isReady(UUID playerId) {
            return activeCharacters.containsKey(playerId);
        }

        @Override
        public int activeSessionCount() {
            return activeCharacters.size();
        }
    }
}
