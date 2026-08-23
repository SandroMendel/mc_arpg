package rpg.core.zone;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;
import rpg.core.currency.BookingReason;
import rpg.core.currency.BookingResult;
import rpg.core.currency.Currency;
import rpg.core.scheduler.WorldPosition;

/**
 * What the travel tests share: a purse that remembers every booking, a teleporter that can be told
 * to fail, and the real {@link ZoneStateStore} for the unlocks.
 *
 * <p>The store is real rather than faked on purpose - "unlocked" is the check that decides whether
 * coins move, and a hand-written yes/no would have tested the test.
 */
final class TravelFixture {

    private TravelFixture() {}

    static final UUID HOLDER = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final UUID CHARACTER = UUID.fromString("00000000-0000-4000-8000-0000000000a2");

    static Zones load(Map<String, Object> document) throws Exception {
        ConfigSchema<ZoneConfig> schema = ZoneConfigSchema.schema(ZoneFixture.worlds());
        return new DefaultZones(
                schema.bind(SchemaValidator.validate(Path.of("zones.yml"), document, schema)));
    }

    /**
     * Ein Java-Quelltext ohne seine Kommentare.
     *
     * <p>Quelltexttests, die die <em>Abwesenheit</em> von etwas pruefen, stolpern sonst ueber genau
     * die Prosa, die erklaert, warum es das nicht gibt: {@code Waypoints} schreibt auf, dass es
     * absichtlich keine Methode zum Entziehen gibt, und ein naiver Textvergleich findet dort das
     * Wort und meldet einen Verstoss. Gesucht wird ausfuehrbarer Bezug, nicht die Begruendung -
     * dieselbe Unterscheidung, die {@code DeathDoesNotCostCoinsTest} in B08b trifft.
     *
     * <p>Kein Parser, sondern zwei Ersetzungen. In diesem Paket kommt keine Zeichenkette vor, die
     * {@code //} oder einen Kommentaranfang enthaelt; sollte das je passieren, faellt es hier auf
     * und nicht schleichend.
     */
    static String codeOnly(String java) {
        return java.replaceAll("(?s)/[*].*?[*]/", "").replaceAll("(?m)//.*$", "");
    }

    /** A loaded store with the given crystals already discovered. */
    static ZoneStateStore storeWith(String... unlockedCrystals) {
        ZoneStateStore store = new ZoneStateStore(new SilentRepository());
        store.load(CHARACTER, Optional.of(ZoneCharacterState.empty(CHARACTER)));
        for (String crystalKey : unlockedCrystals) {
            store.unlock(CHARACTER, crystalKey);
        }
        return store;
    }

    /** Accepts everything and remembers nothing - persistence has its own tests. */
    static final class SilentRepository implements ZoneStateRepository {

        @Override
        public CompletableFuture<Optional<ZoneCharacterState>> find(UUID characterId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public void markSeen(UUID characterId) {}

        @Override
        public void unlock(UUID characterId, String crystalKey) {}

        @Override
        public void setPendingRespawn(UUID characterId, String zoneKey) {}
    }

    /** One line of the history a test can read. */
    record Booking(UUID characterId, long amount, BookingReason reason, boolean credit) {}

    /**
     * A purse with a real balance and a real history.
     *
     * <p>Enough of {@link Currency} to be honest about the two things these tests care about: a
     * debit that would go below zero is refused and changes nothing, and every booking leaves a
     * trace naming its reason.
     */
    static final class RecordingCurrency implements Currency {

        final List<Booking> bookings = new ArrayList<>();
        private long balance;

        RecordingCurrency(long balance) {
            this.balance = balance;
        }

        long balance() {
            return balance;
        }

        @Override
        public OptionalLong balanceOf(UUID characterId) {
            return OptionalLong.of(balance);
        }

        @Override
        public long balanceOrZero(UUID characterId) {
            return balance;
        }

        @Override
        public boolean canAfford(UUID characterId, long amount) {
            return balance >= amount;
        }

        @Override
        public BookingResult credit(UUID characterId, long amount, BookingReason reason) {
            if (amount <= 0L) {
                return BookingResult.INVALID_AMOUNT;
            }
            balance += amount;
            bookings.add(new Booking(characterId, amount, reason, true));
            return BookingResult.OK;
        }

        @Override
        public BookingResult debit(UUID characterId, long amount, BookingReason reason) {
            if (amount <= 0L) {
                return BookingResult.INVALID_AMOUNT;
            }
            if (balance < amount) {
                return BookingResult.NOT_ENOUGH;
            }
            balance -= amount;
            bookings.add(new Booking(characterId, amount, reason, false));
            return BookingResult.OK;
        }

        List<BookingReason> reasons() {
            return bookings.stream().map(Booking::reason).toList();
        }
    }

    /** A teleporter that records where it was asked to go, and can be told to refuse. */
    static final class RecordingTeleporter implements Teleporter {

        final List<WorldPosition> destinations = new ArrayList<>();
        boolean succeeds = true;

        @Override
        public boolean teleport(UUID holderId, WorldPosition destination) {
            destinations.add(destination);
            return succeeds;
        }
    }
}
