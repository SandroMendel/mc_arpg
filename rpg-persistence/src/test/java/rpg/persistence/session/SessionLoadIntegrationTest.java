package rpg.persistence.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.ItemInstance;
import rpg.core.persistence.PersistenceException;
import rpg.core.persistence.PlayerState;
import rpg.core.session.CharacterClass;
import rpg.core.session.CharacterClassTakenException;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.SessionBundle;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * T022, T050, T057, T058: the load path against a real database.
 *
 * <p>The connection-counting test is the one that justifies this class existing rather than being
 * three repository tests. At 200 simultaneous logins the difference between one check-out per login
 * and three is the difference between a login pool under pressure and one that is barely touched -
 * and nothing about three separate repository calls looks wrong when read on its own.
 */
class SessionLoadIntegrationTest {

    private static final Logger QUIET = Logger.getLogger("session-load-integration-test");

    private PersistenceHarness harness;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    // === T022: the bundled read ===

    @Test
    void loadingAWholeSessionTakesExactlyOneConnection() throws Exception {
        UUID playerId = storedPlayerWithCharacterAndItems();
        CountingDataSource counting = new CountingDataSource(harness.pools.loginPool());
        SessionBundleLoader loader =
                new SessionBundleLoader(counting, harness.characters, QUIET);

        SessionBundle bundle = loader.load(playerId);

        assertThat(counting.checkOuts()).isEqualTo(1);
        assertThat(bundle.characters()).hasSize(1);
        assertThat(bundle.items()).hasSize(2);
        assertThat(bundle.accountState()).isPresent();
    }

    @Test
    void aFirstTimePlayerIsReportedAsNewWithoutRunningTheRemainingQueries() {
        CountingDataSource counting = new CountingDataSource(harness.pools.loginPool());
        SessionBundleLoader loader =
                new SessionBundleLoader(counting, harness.characters, QUIET);

        SessionBundle bundle = loader.load(UUID.randomUUID());

        assertThat(bundle.isNewAccount()).isTrue();
        assertThat(bundle.characters()).isEmpty();
        assertThat(bundle.items()).isEmpty();
        assertThat(counting.checkOuts()).isEqualTo(1);
    }

    @Test
    void anUnreadableStoreFailsRatherThanReturningAnEmptyBundle() {
        // FR-005a. "Never seen before" and "could not be read" must stay distinguishable: the first
        // is a normal first login, the second must refuse the login. Collapsing them is precisely
        // how an empty profile ends up overwriting a real one.
        SessionBundleLoader loader =
                new SessionBundleLoader(new FailingDataSource(), harness.characters, QUIET);

        assertThatThrownBy(() -> loader.load(UUID.randomUUID()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void theBundleCarriesEveryCharacterOfTheAccountAndTheirItems() throws Exception {
        UUID playerId = UUID.randomUUID();
        storeAccount(playerId);
        PlayerCharacter warrior = harness.characters.create(playerId, CharacterClass.WARRIOR).get();
        PlayerCharacter mage = harness.characters.create(playerId, CharacterClass.MAGE).get();
        harness.itemInstances.create(
                new ItemInstance(UUID.randomUUID(), warrior.characterId(), "sword.iron", Map.of(), 0L));
        harness.itemInstances.create(
                new ItemInstance(UUID.randomUUID(), mage.characterId(), "staff.ash", Map.of(), 0L));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        SessionBundle bundle =
                new SessionBundleLoader(harness.pools.loginPool(), harness.characters, QUIET)
                        .load(playerId);

        assertThat(bundle.characters()).hasSize(2);
        assertThat(bundle.items()).hasSize(2);
        // The bundle keeps ownership visible; it does not merge the two inventories (ADR-011).
        assertThat(bundle.items())
                .extracting(ItemInstance::ownerCharacterId)
                .containsExactlyInAnyOrder(warrior.characterId(), mage.characterId());
    }

    // === T057: one character per class, enforced by the database ===

    @Test
    void aSecondCharacterOfTheSameClassIsRejected() throws Exception {
        UUID playerId = UUID.randomUUID();
        storeAccount(playerId);
        harness.characters.create(playerId, CharacterClass.WARRIOR).get();

        assertThatThrownBy(() -> harness.characters.create(playerId, CharacterClass.WARRIOR).get())
                .hasCauseInstanceOf(CharacterClassTakenException.class);

        assertThat(harness.characters.findByPlayer(playerId).get()).hasSize(1);
    }

    @Test
    void allThreeClassesTogetherAreAcceptedAndThatIsTheCap() throws Exception {
        // The three-slot rule is not counted anywhere - it follows from there being three classes
        // and one slot per class. Nothing has to stay in sync with anything.
        UUID playerId = UUID.randomUUID();
        storeAccount(playerId);
        for (CharacterClass characterClass : CharacterClass.values()) {
            harness.characters.create(playerId, characterClass).get();
        }

        assertThat(harness.characters.findByPlayer(playerId).get()).hasSize(3);
        assertThat(CharacterClass.values()).hasSize(3);
    }

    @Test
    void twoAccountsMayEachHaveTheSameClass() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        storeAccount(first);
        storeAccount(second);

        harness.characters.create(first, CharacterClass.MAGE).get();
        harness.characters.create(second, CharacterClass.MAGE).get();

        assertThat(harness.characters.findByPlayer(first).get()).hasSize(1);
        assertThat(harness.characters.findByPlayer(second).get()).hasSize(1);
    }

    // === T058: characters do not share progress ===

    @Test
    void writingOneCharacterLeavesTheOthersUntouched() throws Exception {
        UUID playerId = UUID.randomUUID();
        storeAccount(playerId);
        PlayerCharacter warrior = harness.characters.create(playerId, CharacterClass.WARRIOR).get();
        PlayerCharacter mage = harness.characters.create(playerId, CharacterClass.MAGE).get();

        harness.characters.put(warrior);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        Map<CharacterClass, PlayerCharacter> after =
                harness.characters.findByPlayer(playerId).get().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        PlayerCharacter::characterClass, c -> c));

        assertThat(after.get(CharacterClass.WARRIOR).revision()).isEqualTo(warrior.revision() + 1);
        assertThat(after.get(CharacterClass.MAGE).revision()).isEqualTo(mage.revision());
    }

    // === T050: a refused login must leave the stored record exactly as it was ===

    @Test
    void aRefusedLoginLeavesTheStoredRevisionUntouched() throws Exception {
        UUID playerId = storedPlayerWithCharacterAndItems();
        long revisionBefore = storedRevision(playerId);
        List<PlayerCharacter> before = harness.characters.findByPlayer(playerId).get();

        // The load fails; nothing may reach storage as a result.
        SessionBundleLoader loader =
                new SessionBundleLoader(new FailingDataSource(), harness.characters, QUIET);
        assertThatThrownBy(() -> loader.load(playerId)).isInstanceOf(PersistenceException.class);
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        assertThat(storedRevision(playerId)).isEqualTo(revisionBefore);
        assertThat(harness.characters.findByPlayer(playerId).get())
                .as("a refused login must not touch a single character record")
                .isEqualTo(before);
    }

    // --- fixtures ---

    private UUID storedPlayerWithCharacterAndItems() throws Exception {
        UUID playerId = UUID.randomUUID();
        storeAccount(playerId);
        PlayerCharacter character =
                harness.characters.create(playerId, CharacterClass.WARRIOR).get();
        harness.itemInstances.create(
                new ItemInstance(
                        UUID.randomUUID(), character.characterId(), "sword.iron", Map.of(), 0L));
        harness.itemInstances.create(
                new ItemInstance(
                        UUID.randomUUID(), character.characterId(), "potion.health", Map.of(), 0L));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
        return playerId;
    }

    private void storeAccount(UUID playerId) throws Exception {
        harness.playerStates.put(PlayerState.initial(playerId, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
    }

    private static long storedRevision(UUID playerId) throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                java.sql.ResultSet rows =
                        statement.executeQuery(
                                "SELECT revision FROM rpg.player_state WHERE player_id = '"
                                        + playerId
                                        + "'")) {
            return rows.next() ? rows.getLong(1) : -1L;
        }
    }

    /** Counts how often a connection was taken - the whole point of the bundled read. */
    private static final class CountingDataSource implements DataSource {

        private final DataSource delegate;
        private final AtomicInteger checkOuts = new AtomicInteger();

        CountingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        int checkOuts() {
            return checkOuts.get();
        }

        @Override
        public Connection getConnection() throws SQLException {
            checkOuts.incrementAndGet();
            return delegate.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            checkOuts.incrementAndGet();
            return delegate.getConnection(username, password);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return QUIET;
        }

        @Override
        public <T> T unwrap(Class<T> type) throws SQLException {
            return delegate.unwrap(type);
        }

        @Override
        public boolean isWrapperFor(Class<?> type) throws SQLException {
            return delegate.isWrapperFor(type);
        }
    }

    /** A store that cannot be reached, for the failure the block exists to survive. */
    private static final class FailingDataSource implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("storage unreachable");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("storage unreachable");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            // nothing to configure on a source that never connects
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // nothing to configure on a source that never connects
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return QUIET;
        }

        @Override
        public <T> T unwrap(Class<T> type) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> type) {
            return false;
        }
    }
}
