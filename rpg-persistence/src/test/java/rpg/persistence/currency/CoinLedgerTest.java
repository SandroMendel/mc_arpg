package rpg.persistence.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.currency.BookingReason;
import rpg.core.currency.LedgerEntry;
import rpg.core.persistence.AggregateType;
import rpg.core.persistence.DirtyMark;
import rpg.core.persistence.PersistenceConfig;
import rpg.persistence.ConnectionPools;
import rpg.persistence.SchemaMigrator;
import rpg.persistence.support.DirectScheduler;
import rpg.persistence.support.PostgresContainer;

/**
 * T085 bis T087 - der Verlauf gegen ein echtes PostgreSQL (US3 Szenarien 1 und 2, SC-002, SC-010).
 *
 * <p>Der zweite Test ist der, um den es geht: der Verlauf muss einen Neustart ueberstehen. Alles
 * andere kann gruen sein und die Beschwerde eines Spielers trotzdem unklaerbar bleiben.
 */
class CoinLedgerTest {

    private static final Logger QUIET = Logger.getLogger("coin-ledger-test");

    private ConnectionPools pools;
    private JdbcCoinLedgerRepository ledger;
    private TestCoordinator coordinator;
    private MovableClock clock;
    private UUID character;

    @BeforeEach
    void setUp() throws Exception {
        QUIET.setLevel(Level.OFF);
        PostgresContainer.resetSchema();
        pools = pools();
        new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest();
        coordinator = new TestCoordinator();
        clock = new MovableClock(Instant.parse("2026-08-22T12:00:00Z"));
        ledger =
                new JdbcCoinLedgerRepository(
                        pools.loginPool(),
                        new DirectScheduler(),
                        coordinator,
                        Duration.ofDays(30),
                        clock);
        character = insertCharacter();
    }

    @Test
    @DisplayName("jede Buchung erscheint mit Zeitpunkt, Betrag, Richtung, Grund und beiden Staenden")
    void everyBookingIsRecordedInFull() throws Exception {
        ledger.append(entry(500L, LedgerEntry.Direction.CREDIT, BookingReason.PILE_PICKED_UP, 0L, 500L));
        flush();

        List<LedgerEntry> page = ledger.historyOf(character, 0, 10).get();

        assertThat(page)
                .singleElement()
                .satisfies(
                        e -> {
                            assertThat(e.characterId()).isEqualTo(character);
                            assertThat(e.amount()).isEqualTo(500L);
                            assertThat(e.direction()).isEqualTo(LedgerEntry.Direction.CREDIT);
                            assertThat(e.reason()).isEqualTo(BookingReason.PILE_PICKED_UP);
                            assertThat(e.balanceBefore()).isZero();
                            assertThat(e.balanceAfter()).isEqualTo(500L);
                            assertThat(e.actor()).isEmpty();
                            assertThat(e.id()).as("von der Datenbank vergeben").isPositive();
                        });
    }

    @Test
    @DisplayName("der Verlauf uebersteht einen Neustart unveraendert (SC-002)")
    void theLedgerSurvivesARestart() throws Exception {
        ledger.append(entry(100L, LedgerEntry.Direction.CREDIT, BookingReason.PILE_PICKED_UP, 0L, 100L));
        ledger.append(entry(40L, LedgerEntry.Direction.DEBIT, BookingReason.EQUIPMENT_TIER, 100L, 60L));
        flush();

        // Alles wegwerfen, was im Speicher stand - genau das tut ein Neustart.
        JdbcCoinLedgerRepository afterRestart =
                new JdbcCoinLedgerRepository(
                        pools.loginPool(),
                        new DirectScheduler(),
                        new TestCoordinator(),
                        Duration.ofDays(30),
                        clock);

        assertThat(afterRestart.historyOf(character, 0, 10).get())
                .as("eine Beschwerde ohne Verlauf ist nicht zu klaeren")
                .hasSize(2);
        assertThat(afterRestart.historyCount(character).get()).isEqualTo(2L);
    }

    @Test
    @DisplayName("neueste zuerst, und Versatz plus Limit blaettern ohne Doppelung")
    void pagingIsNewestFirstAndWithoutOverlap() throws Exception {
        for (int i = 1; i <= 5; i++) {
            ledger.append(
                    entry(i, LedgerEntry.Direction.CREDIT, BookingReason.PILE_PICKED_UP, 0L, i));
            clock.advance(Duration.ofSeconds(1));
        }
        flush();

        List<LedgerEntry> first = ledger.historyOf(character, 0, 2).get();
        List<LedgerEntry> second = ledger.historyOf(character, 2, 2).get();
        List<LedgerEntry> third = ledger.historyOf(character, 4, 2).get();

        assertThat(first).extracting(LedgerEntry::amount).containsExactly(5L, 4L);
        assertThat(second).extracting(LedgerEntry::amount).containsExactly(3L, 2L);
        assertThat(third).extracting(LedgerEntry::amount).containsExactly(1L);

        assertThat(first).as("keine Buchung auf zwei Seiten").doesNotContainAnyElementsOf(second);
        assertThat(ledger.historyCount(character).get()).isEqualTo(5L);
    }

    @Test
    @DisplayName("es gibt KEINE unbegrenzte Abfrage - ein Limit von null wird abgelehnt")
    void thereIsNoUnboundedRead() {
        assertThatThrownBy(() -> ledger.historyOf(character, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no unbounded read");
        assertThatThrownBy(() -> ledger.historyOf(character, -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("eine Zeitraumabfrage liefert nur, was hineinfaellt")
    void aRangeQueryIsBounded() throws Exception {
        Instant start = clock.instant();
        ledger.append(entry(10L, LedgerEntry.Direction.CREDIT, BookingReason.PILE_PICKED_UP, 0L, 10L));
        clock.advance(Duration.ofHours(2));
        ledger.append(entry(20L, LedgerEntry.Direction.CREDIT, BookingReason.PILE_PICKED_UP, 10L, 30L));
        flush();

        List<LedgerEntry> early =
                ledger.historyOf(character, start.minusSeconds(1), start.plusSeconds(1), 10).get();

        assertThat(early).extracting(LedgerEntry::amount).containsExactly(10L);
    }

    @Test
    @DisplayName("die Aufbewahrung raeumt Spielbuchungen ab und laesst Eingriffe stehen (FR-038)")
    void retentionSparesOperatorEntries() throws Exception {
        ledger.append(entry(10L, LedgerEntry.Direction.CREDIT, BookingReason.PILE_PICKED_UP, 0L, 10L));
        ledger.append(
                new LedgerEntry(
                        0L,
                        character,
                        clock.instant(),
                        99L,
                        LedgerEntry.Direction.CREDIT,
                        BookingReason.ADMIN_ADD,
                        10L,
                        109L,
                        Optional.of("Sandro")));
        flush();

        clock.advance(Duration.ofDays(40));
        ledger.pruneNow(pools.writePool());

        List<LedgerEntry> left = ledger.historyOf(character, 0, 10).get();
        assertThat(left)
                .as("die Eingriffe sind die, nach denen jemand ein Jahr spaeter fragt")
                .singleElement()
                .satisfies(
                        e -> {
                            assertThat(e.reason()).isEqualTo(BookingReason.ADMIN_ADD);
                            assertThat(e.actor()).hasValue("Sandro");
                        });
    }

    @Test
    @DisplayName("eine junge Spielbuchung ueberlebt die Aufbewahrung")
    void aRecentGameplayEntrySurvives() throws Exception {
        ledger.append(entry(10L, LedgerEntry.Direction.CREDIT, BookingReason.PILE_PICKED_UP, 0L, 10L));
        flush();

        clock.advance(Duration.ofDays(3));
        ledger.pruneNow(pools.writePool());

        assertThat(ledger.historyOf(character, 0, 10).get()).hasSize(1);
    }

    @Test
    @DisplayName("die ganze Warteschlange haengt an EINER Markierung, nicht an einer je Eintrag")
    void theWholeQueueHangsBehindOneMark() {
        for (int i = 0; i < 50; i++) {
            ledger.append(
                    entry(1L, LedgerEntry.Direction.CREDIT, BookingReason.PILE_PICKED_UP, 0L, 1L));
        }

        assertThat(coordinator.distinctIds())
                .as("bei 800 Mobs waere eine Markierung je Kill der Fehler")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("das Loeschen eines Charakters nimmt seinen Verlauf mit")
    void cascadeOnDelete() throws Exception {
        ledger.append(entry(10L, LedgerEntry.Direction.CREDIT, BookingReason.PILE_PICKED_UP, 0L, 10L));
        flush();

        // Ueber eine autocommit-Verbindung, nicht ueber den Pool: der gibt sie mit autoCommit=false
        // heraus, und ein DELETE ohne commit sieht aus, als haette es gewirkt.
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM rpg.character WHERE character_id = '" + character + "'");
        }

        assertThat(ledger.historyCount(character).get()).isZero();
    }

    // --- Hilfsmittel -----------------------------------------------------

    private void flush() {
        ledger.write(pools.writePool(), coordinator.drain());
    }

    private LedgerEntry entry(
            long amount,
            LedgerEntry.Direction direction,
            BookingReason reason,
            long before,
            long after) {
        return LedgerEntry.pending(
                character, clock.instant(), amount, direction, reason, before, after, Optional.empty());
    }

    private UUID insertCharacter() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO rpg.player_state (player_id) VALUES ('" + playerId + "')");
            statement.execute(
                    "INSERT INTO rpg.character (character_id, player_id, character_class)"
                            + " VALUES ('"
                            + characterId
                            + "', '"
                            + playerId
                            + "', 'ROGUE')");
        }
        return characterId;
    }

    private static ConnectionPools pools() {
        return new ConnectionPools(
                new PersistenceConfig(
                        PostgresContainer.host(),
                        PostgresContainer.port(),
                        "vuntex_test",
                        PostgresContainer.username(),
                        PostgresContainer.password(),
                        2,
                        1,
                        Duration.ofSeconds(45),
                        1_000,
                        Duration.ofSeconds(8)),
                QUIET);
    }

    /** Sammelt Markierungen und zaehlt, wie viele verschiedene Kennungen vorkommen. */
    static final class TestCoordinator implements rpg.core.persistence.WriteBehindCoordinator {

        private final List<DirtyMark> marks = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void markDirty(AggregateType type, String aggregateId) {
            marks.add(new DirtyMark(type, aggregateId, Instant.EPOCH));
        }

        @Override
        public java.util.concurrent.CompletableFuture<rpg.core.persistence.FlushResult> flushNow(
                rpg.core.persistence.FlushReason reason) {
            throw new UnsupportedOperationException("the test drives the writer itself");
        }

        @Override
        public rpg.core.persistence.BufferStatus bufferStatus() {
            throw new UnsupportedOperationException("not part of what this test is about");
        }

        int distinctIds() {
            return (int) marks.stream().map(DirtyMark::aggregateId).distinct().count();
        }

        List<DirtyMark> drain() {
            List<DirtyMark> snapshot = List.copyOf(marks);
            marks.clear();
            return snapshot;
        }
    }

    /** Eine Uhr, die der Test vorstellt. */
    static final class MovableClock extends java.time.Clock {

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
