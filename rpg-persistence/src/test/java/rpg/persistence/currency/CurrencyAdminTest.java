package rpg.persistence.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.currency.BookingReason;
import rpg.core.currency.BookingResult;
import rpg.core.currency.CharacterBalance;
import rpg.core.currency.CurrencyConfig;
import rpg.core.currency.DefaultCurrency;
import rpg.core.currency.LedgerEntry;
import rpg.core.persistence.AuditEntry;
import rpg.core.persistence.AuditLogRepository;
import rpg.core.persistence.PersistenceConfig;
import rpg.persistence.ConnectionPools;
import rpg.persistence.SchemaMigrator;
import rpg.persistence.support.DirectScheduler;
import rpg.persistence.support.PostgresContainer;

/**
 * T088 bis T092 - der Eingriff des Betreibers (US3 Szenarien 3 bis 8).
 *
 * <p>Die beiden Tests, um die es geht, sind {@code offlineCharacterIsChanged} und
 * {@code anOnlineChangeSurvivesTheNextFlush}: der eine beweist, dass der Betreiber einen
 * abgemeldeten Charakter erreicht (FR-042), der andere, dass er einen <em>angemeldeten</em> nicht
 * am Cache vorbei aendert - sonst schriebe der naechste Flush den alten Wert zurueck (FR-043).
 */
class CurrencyAdminTest {

    private static final Logger QUIET = Logger.getLogger("currency-admin-test");

    private ConnectionPools pools;
    private DefaultCurrency currency;
    private JdbcCoinLedgerRepository ledger;
    private JdbcCharacterBalanceRepository balances;
    private JdbcCurrencyAdmin admin;
    private RecordingAuditLog auditLog;
    private CoinLedgerTest.TestCoordinator coordinator;
    private CoinLedgerTest.MovableClock clock;
    private UUID character;

    @BeforeEach
    void setUp() throws Exception {
        QUIET.setLevel(Level.OFF);
        PostgresContainer.resetSchema();
        pools = pools();
        new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest();

        coordinator = new CoinLedgerTest.TestCoordinator();
        clock = new CoinLedgerTest.MovableClock(Instant.parse("2026-08-22T12:00:00Z"));
        ledger =
                new JdbcCoinLedgerRepository(
                        pools.loginPool(),
                        new DirectScheduler(),
                        coordinator,
                        Duration.ofDays(30),
                        clock);
        balances =
                new JdbcCharacterBalanceRepository(
                        pools.loginPool(), new DirectScheduler(), coordinator, clock);
        currency = new DefaultCurrency(config(), balances, ledger, clock, QUIET);
        balances.setLiveSource(currency::liveOrLastKnown);
        auditLog = new RecordingAuditLog();
        admin =
                new JdbcCurrencyAdmin(
                        currency, pools.writePool(), ledger, auditLog, clock, QUIET);

        character = insertCharacter();
    }

    @Test
    @DisplayName("add erhoeht einen ANGEMELDETEN Charakter im Cache")
    void addRaisesALoadedCharacter() {
        currency.onCharacterLoaded(character, Optional.empty());

        assertThat(admin.add(character, 1000L, "Sandro")).isEqualTo(BookingResult.OK);

        assertThat(currency.balanceOf(character)).hasValue(1000L);
    }

    @Test
    @DisplayName("ein Eingriff auf einen ANGEMELDETEN Charakter ueberlebt den naechsten Flush")
    void anOnlineChangeSurvivesTheNextFlush() throws Exception {
        currency.onCharacterLoaded(
                character, Optional.of(new CharacterBalance(character, 100L, 1, 1L)));

        admin.add(character, 900L, "Sandro");

        // Der Flush liest ueber die liveSource. Haette der Eingriff an der Regelschicht vorbei
        // direkt in die Tabelle geschrieben, schriebe dieser Flush die 100 zurueck.
        flushBalances();

        try (Connection connection = pools.writePool().getConnection()) {
            assertThat(JdbcCharacterBalanceRepository.read(connection, character))
                    .isPresent()
                    .get()
                    .extracting(CharacterBalance::balance)
                    .isEqualTo(1000L);
        }
    }

    @Test
    @DisplayName("ein Eingriff auf einen ABGEMELDETEN Charakter wirkt trotzdem (FR-042)")
    void offlineCharacterIsChanged() throws Exception {
        assertThat(currency.balanceOf(character)).as("nicht geladen").isEmpty();

        assertThat(admin.add(character, 500L, "Sandro")).isEqualTo(BookingResult.OK);

        try (Connection connection = pools.writePool().getConnection()) {
            assertThat(JdbcCharacterBalanceRepository.read(connection, character))
                    .isPresent()
                    .get()
                    .extracting(CharacterBalance::balance)
                    .isEqualTo(500L);
        }
    }

    @Test
    @DisplayName("und steht beim naechsten Einstieg auf dem Konto")
    void andIsThereOnTheNextLogin() throws Exception {
        admin.add(character, 500L, "Sandro");

        try (Connection connection = pools.writePool().getConnection()) {
            currency.onCharacterLoaded(
                    character, JdbcCharacterBalanceRepository.read(connection, character));
        }

        assertThat(currency.balanceOf(character)).hasValue(500L);
    }

    @Test
    @DisplayName("set trifft den Wert genau, gleich ob darueber oder darunter")
    void setHitsTheValueExactly() {
        currency.onCharacterLoaded(
                character, Optional.of(new CharacterBalance(character, 300L, 1, 1L)));

        assertThat(admin.set(character, 1000L, "Sandro")).isEqualTo(BookingResult.OK);
        assertThat(currency.balanceOf(character)).hasValue(1000L);

        assertThat(admin.set(character, 50L, "Sandro")).isEqualTo(BookingResult.OK);
        assertThat(currency.balanceOf(character)).hasValue(50L);
    }

    @Test
    @DisplayName("set auf denselben Wert schreibt nichts - das waere Rauschen im Verlauf")
    void settingTheSameValueWritesNothing() throws Exception {
        currency.onCharacterLoaded(
                character, Optional.of(new CharacterBalance(character, 300L, 1, 1L)));

        assertThat(admin.set(character, 300L, "Sandro")).isEqualTo(BookingResult.OK);

        flushLedger();
        assertThat(ledger.historyOf(character, 0, 10).get()).isEmpty();
    }

    @Test
    @DisplayName("AUCH DER BETREIBER erzeugt keinen negativen Stand (FR-003)")
    void notEvenTheOperatorGoesNegative() {
        currency.onCharacterLoaded(
                character, Optional.of(new CharacterBalance(character, 100L, 1, 1L)));

        assertThat(admin.remove(character, 500L, "Sandro"))
                .as("die Zusage gilt der Zahl, nicht der Person, die fragt")
                .isEqualTo(BookingResult.NOT_ENOUGH);
        assertThat(currency.balanceOf(character)).hasValue(100L);
    }

    @Test
    @DisplayName("dasselbe auf einem abgemeldeten Charakter")
    void notEvenOfflineGoesNegative() throws Exception {
        try (Connection connection = pools.writePool().getConnection()) {
            JdbcCharacterBalanceRepository.write(connection, character, 100L, clock);
            connection.commit();
        }

        assertThat(admin.remove(character, 500L, "Sandro")).isEqualTo(BookingResult.NOT_ENOUGH);

        try (Connection connection = pools.writePool().getConnection()) {
            assertThat(JdbcCharacterBalanceRepository.read(connection, character))
                    .get()
                    .extracting(CharacterBalance::balance)
                    .isEqualTo(100L);
        }
    }

    @Test
    @DisplayName("ein Eingriff auf einen unbekannten Charakter legt keinen an (FR-044)")
    void anUnknownCharacterIsRefusedAndNotCreated() throws Exception {
        UUID stranger = UUID.randomUUID();

        assertThat(admin.add(stranger, 100L, "Sandro"))
                .isEqualTo(BookingResult.NO_SUCH_CHARACTER);

        try (Connection connection = pools.writePool().getConnection()) {
            assertThat(JdbcCharacterBalanceRepository.read(connection, stranger)).isEmpty();
        }
    }

    @Test
    @DisplayName("jeder Eingriff steht mit seinem Verursacher im Verlauf (SC-011)")
    void everyInterventionNamesItsActor() throws Exception {
        currency.onCharacterLoaded(character, Optional.empty());

        admin.add(character, 100L, "Sandro");
        admin.remove(character, 40L, "Jonas");
        flushLedger();

        List<LedgerEntry> history = ledger.historyOf(character, 0, 10).get();

        assertThat(history)
                .hasSize(2)
                .allSatisfy(entry -> assertThat(entry.actor()).isPresent())
                .extracting(entry -> entry.actor().orElseThrow())
                .containsExactlyInAnyOrder("Sandro", "Jonas");
        assertThat(history)
                .extracting(LedgerEntry::reason)
                .containsExactlyInAnyOrder(BookingReason.ADMIN_ADD, BookingReason.ADMIN_REMOVE);
    }

    @Test
    @DisplayName("jeder Eingriff erscheint zusaetzlich im Audit-Log (FR-041)")
    void everyInterventionReachesTheAuditLog() {
        currency.onCharacterLoaded(character, Optional.empty());

        admin.add(character, 100L, "Sandro");

        assertThat(auditLog.entries)
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.actor()).isEqualTo("Sandro");
                            assertThat(entry.action()).isEqualTo("currency_admin_add");
                            assertThat(entry.details())
                                    .containsEntry("character_id", character.toString())
                                    .containsEntry("amount", "100");
                        });
    }

    @Test
    @DisplayName("ein Eingriff ohne Verursacher ist unmoeglich")
    void anInterventionWithoutAnActorIsImpossible() {
        currency.onCharacterLoaded(character, Optional.empty());

        assertThatThrownBy(() -> admin.add(character, 100L, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("who caused it");
        assertThatThrownBy(() -> admin.add(character, 100L, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("ein Betrag von null oder negativ wird zurueckgewiesen")
    void invalidAmountsAreRefused() {
        currency.onCharacterLoaded(character, Optional.empty());

        assertThat(admin.add(character, 0L, "Sandro")).isEqualTo(BookingResult.INVALID_AMOUNT);
        assertThat(admin.remove(character, -5L, "Sandro")).isEqualTo(BookingResult.INVALID_AMOUNT);
        assertThat(admin.set(character, -1L, "Sandro")).isEqualTo(BookingResult.INVALID_AMOUNT);
    }

    // --- Hilfsmittel -----------------------------------------------------

    private void flushLedger() {
        ledger.write(pools.writePool(), coordinator.drain());
    }

    /**
     * Schreibt die vorgemerkten Kontostaende - genau das, was der Flush-Zyklus tut.
     *
     * <p>Ueber <b>dasselbe</b> Repository wie im Aufbau, denn nur das traegt die lebende Quelle. Ein
     * zweites zu bauen hiesse, an der Regelschicht vorbei zu schreiben - also gerade den Fehler zu
     * begehen, den dieser Test ausschliessen soll.
     */
    private void flushBalances() {
        List<rpg.core.persistence.DirtyMark> marks =
                coordinator.drain().stream()
                        .filter(
                                mark ->
                                        mark.aggregateType()
                                                == rpg.core.persistence.AggregateType
                                                        .CHARACTER_BALANCE)
                        .toList();
        balances.write(pools.writePool(), marks);
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
                            + "', 'MAGE')");
        }
        return characterId;
    }

    private static CurrencyConfig config() {
        return new CurrencyConfig(
                0L, 4L, java.util.Map.of(), Duration.ofSeconds(120), 3.0d, 400,
                Duration.ofDays(30), 45);
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

    /** Haelt fest, was ins Audit-Log ging. */
    private static final class RecordingAuditLog implements AuditLogRepository {

        final List<AuditEntry> entries = new ArrayList<>();

        @Override
        public void append(AuditEntry entry) {
            entries.add(entry);
        }

        @Override
        public CompletableFuture<List<AuditEntry>> between(Instant from, Instant to) {
            return CompletableFuture.completedFuture(List.copyOf(entries));
        }
    }
}
