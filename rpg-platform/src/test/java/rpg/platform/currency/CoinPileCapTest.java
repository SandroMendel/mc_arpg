package rpg.platform.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import rpg.core.currency.BookingReason;
import rpg.core.currency.CurrencyConfig;

/**
 * T063 - die Deckelung raeumt ab und schreibt dabei gut (US2 Szenario 7a, FR-030a bis FR-030d).
 *
 * <p><b>Die Zusage, um die es geht:</b> beim Abraeumen geht <b>keine Coin verloren</b>. Der
 * Unterschied zum Verfall ist beabsichtigt und steht deshalb auch als Test da: laeuft die
 * <em>Frist</em> ab, bekommt niemand etwas; raeumt der <em>Server</em> ab, bekommt der Besitzer sein
 * Geld. Eigene Versaeumnisse kosten, Serverlast nicht.
 */
class CoinPileCapTest {

    private static final Logger QUIET = Logger.getLogger("coin-pile-cap-test");

    private ServerMock server;
    private World world;
    private MovableClock clock;
    private RecordingPayout payout;
    private CoinPileRegistry registry;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        clock = new MovableClock(Instant.parse("2026-08-22T12:00:00Z"));
        payout = new RecordingPayout();
        registry = new CoinPileRegistry(config(3), payout, clock, QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("unterhalb der Deckelung wird nichts abgeraeumt und nichts gutgeschrieben")
    void belowTheCapNothingHappens() {
        register(alice, 100L);
        register(alice, 200L);

        assertThat(registry.makeRoom()).isTrue();
        assertThat(payout.credited).isEmpty();
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("bei erreichter Deckelung wird der AELTESTE Haufen gutgeschrieben und abgeraeumt")
    void theOldestPileIsCashedIn() {
        Item oldest = register(alice, 100L);
        clock.advance(Duration.ofSeconds(5));
        register(bob, 200L);
        clock.advance(Duration.ofSeconds(5));
        register(alice, 300L);

        assertThat(registry.makeRoom()).as("Platz geschaffen").isTrue();

        assertThat(payout.credited)
                .as("der aelteste ist der am ehesten vergessene")
                .containsExactly(new RecordingPayout.Credit(alice, 100L, BookingReason.PILE_CASHED_IN));
        assertThat(oldest.isValid()).as("und er ist aus der Welt").isFalse();
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("es geht dabei KEINE Coin verloren (FR-030b)")
    void noCoinIsLost() {
        register(alice, 111L);
        clock.advance(Duration.ofSeconds(1));
        register(alice, 222L);
        clock.advance(Duration.ofSeconds(1));
        register(alice, 333L);

        registry.makeRoom();

        long stillLying =
                registry.size() > 0 ? 222L + 333L : 0L;
        long creditedTotal =
                payout.credited.stream().mapToLong(RecordingPayout.Credit::amount).sum();

        assertThat(stillLying + creditedTotal)
                .as("die Summe aus Welt und Konten bleibt gleich")
                .isEqualTo(111L + 222L + 333L);
    }

    @Test
    @DisplayName("was schon weg ist, wird zuerst vergessen - erst dann wird abgeraeumt")
    void goneEntriesAreForgottenFirst() {
        Item gone = register(alice, 100L);
        register(alice, 200L);
        register(alice, 300L);

        gone.remove();

        assertThat(registry.makeRoom()).isTrue();
        assertThat(payout.credited)
                .as("es war Platz da, also musste niemand eingeloest werden")
                .isEmpty();
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("ein Haufen ueber seine Frist hinaus zaehlt nicht mehr mit")
    void anExpiredPileNoLongerCounts() {
        register(alice, 100L);
        register(alice, 200L);
        register(alice, 300L);

        // Weit ueber die konfigurierten 120 Sekunden: Vanilla haette sie laengst abgeraeumt.
        clock.advance(Duration.ofSeconds(200));

        assertThat(registry.makeRoom()).isTrue();
        assertThat(payout.credited)
                .as("verfallen heisst NIEMANDEM gutschreiben (FR-029) - das ist nicht dasselbe")
                .isEmpty();
        assertThat(registry.size()).isZero();
    }

    @Test
    @DisplayName("laesst sich der Besitzer nicht gutschreiben, bleibt sein Haufen liegen")
    void anUncreditablePileIsLeftAlone() {
        Item stubborn = register(alice, 100L);
        clock.advance(Duration.ofSeconds(1));
        Item second = register(bob, 200L);
        clock.advance(Duration.ofSeconds(1));
        register(bob, 300L);

        payout.refuse(alice);

        assertThat(registry.makeRoom()).isTrue();
        assertThat(stubborn.isValid())
                .as("ihn zu verwerfen naehme Coins von jemandem, der am Kill gar nicht beteiligt war")
                .isTrue();
        assertThat(second.isValid()).as("stattdessen der naechstaeltere").isFalse();
        assertThat(payout.credited)
                .containsExactly(new RecordingPayout.Credit(bob, 200L, BookingReason.PILE_CASHED_IN));
    }

    @Test
    @DisplayName("kann niemand gutgeschrieben werden, meldet die Deckelung ehrlich 'kein Platz'")
    void whenNobodyCanBeCreditedThereIsNoRoom() {
        register(alice, 100L);
        register(bob, 200L);
        register(alice, 300L);
        payout.refuseEverything();

        assertThat(registry.makeRoom())
                .as("der Aufrufer schreibt dann direkt gut, statt dass etwas verschwindet")
                .isFalse();
    }

    @Test
    @DisplayName("ein aufgehobener Haufen wird vergessen")
    void aPickedUpPileIsForgotten() {
        Item pile = register(alice, 100L);

        registry.forget(pile);

        assertThat(registry.size()).isZero();
    }

    // --- Hilfsmittel -----------------------------------------------------

    private Item register(UUID characterId, long amount) {
        ItemStack stack = new ItemStack(Material.GOLD_NUGGET, 1);
        ItemMeta meta = stack.getItemMeta();
        CoinPileTag.write(meta, amount, characterId, clock.millis());
        stack.setItemMeta(meta);

        Item pile = world.dropItem(new Location(world, 0.5, 64.0, 0.5), stack);
        registry.register(pile, characterId);
        return pile;
    }

    private static CurrencyConfig config(int maxPiles) {
        return new CurrencyConfig(
                0L,
                4L,
                Map.of(),
                Duration.ofSeconds(120),
                3.0d,
                maxPiles,
                Duration.ofDays(30),
                45);
    }

    /** Haelt fest, wer wieviel bekommen hat - und kann sich weigern. */
    private static final class RecordingPayout implements CoinPileRegistry.Payout {

        record Credit(UUID characterId, long amount, BookingReason reason) {}

        final List<Credit> credited = new ArrayList<>();
        private final List<UUID> refused = new ArrayList<>();
        private boolean refuseAll;

        void refuse(UUID characterId) {
            refused.add(characterId);
        }

        void refuseEverything() {
            refuseAll = true;
        }

        @Override
        public boolean credit(UUID characterId, long amount, BookingReason reason) {
            if (refuseAll || refused.contains(characterId)) {
                return false;
            }
            credited.add(new Credit(characterId, amount, reason));
            return true;
        }
    }

    /** Eine Uhr, die der Test vorstellt - damit „aelter" pruefbar ist, ohne zu warten. */
    private static final class MovableClock extends java.time.Clock {

        private Instant now;

        MovableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
