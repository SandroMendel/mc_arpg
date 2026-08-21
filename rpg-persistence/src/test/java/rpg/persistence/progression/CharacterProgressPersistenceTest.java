package rpg.persistence.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.PersistenceConfig;
import rpg.core.progression.CharacterProgress;
import rpg.persistence.ConnectionPools;
import rpg.persistence.SchemaMigrator;
import rpg.persistence.support.PostgresContainer;

/**
 * The progress table against a real PostgreSQL (FR-053, FR-057, FR-058, SC-016, SC-017).
 *
 * <p>Testcontainers rather than a mock, because Principle VII forbids mocks against the database:
 * "Mocks gegen die Datenbank hätten in der Vergangenheit divergierendes Verhalten zwischen Test und
 * Produktion verdeckt."
 */
class CharacterProgressPersistenceTest {

    private static final Logger QUIET = Logger.getLogger("character-progress-test");

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        PostgresContainer.resetSchema();
        migrate();
    }

    @Test
    @DisplayName("the table exists with its key, a cascading foreign key and both checks")
    void tableShape() throws Exception {
        assertThat(tableExists("character_progress")).isTrue();
        assertThat(constraintType("character_progress_pkey")).isEqualTo("p");
        assertThat(constraintType("chk_character_progress_level")).isEqualTo("c");
        assertThat(constraintType("chk_character_progress_xp")).isEqualTo("c");

        // The cascade is what makes anonymisation work without B02 knowing that B06 exists.
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT confdeltype FROM pg_constraint"
                                        + " WHERE conrelid = 'rpg.character_progress'::regclass"
                                        + " AND contype = 'f'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("c");
        }
    }

    @Test
    @DisplayName("there is deliberately NO upper bound on the level in the schema")
    void noCeilingInTheSchema() throws Exception {
        UUID characterId = insertCharacter();

        // The maximum level follows from progression.yml and is allowed to change. A
        // CHECK (level <= 60) would freeze a balancing decision into the schema and turn raising the
        // ceiling into a migration.
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO rpg.character_progress (character_id, level, xp_in_level)"
                            + " VALUES ('"
                            + characterId
                            + "', 250, 0)");
        }
        assertThat(levelOf(characterId)).isEqualTo(250);
    }

    @Test
    @DisplayName("a level below 1 or negative experience cannot be stored at all")
    void impossibleValuesAreRefused() throws Exception {
        UUID characterId = insertCharacter();
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            assertThatThrownBy(
                            () ->
                                    statement.execute(
                                            "INSERT INTO rpg.character_progress"
                                                    + " (character_id, level, xp_in_level)"
                                                    + " VALUES ('"
                                                    + characterId
                                                    + "', 0, 0)"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(
                            () ->
                                    statement.execute(
                                            "INSERT INTO rpg.character_progress"
                                                    + " (character_id, level, xp_in_level)"
                                                    + " VALUES ('"
                                                    + characterId
                                                    + "', 1, -1)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    @DisplayName("a character with no row starts at level 1 with no experience")
    void missingRowMeansInitial() throws Exception {
        UUID characterId = insertCharacter();

        try (ConnectionPools pools = pools();
                Connection connection = pools.loginPool().getConnection()) {
            assertThat(JdbcCharacterProgressRepository.read(connection, characterId)).isEmpty();
        }

        // FR-058: absence is "new", not a fault.
        assertThat(CharacterProgress.initial(characterId).toState().level()).isEqualTo(1);
        assertThat(CharacterProgress.initial(characterId).toState().xpInLevel()).isZero();
    }

    @Test
    @DisplayName("a stored row round-trips through the repository")
    void roundTrip() throws Exception {
        UUID characterId = insertCharacter();
        insertProgress(characterId, 12, 340L);

        try (ConnectionPools pools = pools();
                Connection connection = pools.loginPool().getConnection()) {
            CharacterProgress loaded =
                    JdbcCharacterProgressRepository.read(connection, characterId).orElseThrow();

            assertThat(loaded.characterId()).isEqualTo(characterId);
            assertThat(loaded.level()).isEqualTo(12);
            assertThat(loaded.xpInLevel()).isEqualTo(340L);
            assertThat(loaded.dataVersion()).isEqualTo(CharacterProgress.CURRENT_DATA_VERSION);
            assertThat(loaded.needsMigration()).isFalse();
            assertThat(loaded.isFromFutureVersion()).isFalse();
        }
    }

    @Test
    @DisplayName("a row from a newer version is recognisable and refused rather than misread")
    void futureVersionIsRecognised() throws Exception {
        UUID characterId = insertCharacter();
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO rpg.character_progress"
                            + " (character_id, level, xp_in_level, data_version)"
                            + " VALUES ('"
                            + characterId
                            + "', 5, 10, 99)");
        }

        try (ConnectionPools pools = pools();
                Connection connection = pools.loginPool().getConnection()) {
            CharacterProgress loaded =
                    JdbcCharacterProgressRepository.read(connection, characterId).orElseThrow();

            // SC-016. Same behaviour as PlayerCharacter.isFromFutureVersion in B03: a row written by
            // a newer build must be refused, not interpreted with today's rules.
            assertThat(loaded.isFromFutureVersion()).isTrue();
        }
    }

    @Test
    @DisplayName("deleting the character takes its progress row with it")
    void cascade() throws Exception {
        UUID characterId = insertCharacter();
        insertProgress(characterId, 3, 40L);
        assertThat(rowExists(characterId)).isTrue();

        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "DELETE FROM rpg.character WHERE character_id = '" + characterId + "'");
        }

        assertThat(rowExists(characterId)).isFalse();
    }

    @Test
    @DisplayName("only level and experience are stored - never a computed total")
    void onlyRawValues() throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT column_name FROM information_schema.columns"
                                        + " WHERE table_schema = 'rpg'"
                                        + " AND table_name = 'character_progress'"
                                        + " ORDER BY column_name")) {
            java.util.List<String> columns = new java.util.ArrayList<>();
            while (rows.next()) {
                columns.add(rows.getString(1));
            }
            // No total_xp, no max_level, no anything derived. FR-053a and the same rule ADR-004 sets
            // for items: store what was earned, never what was computed from it.
            assertThat(columns)
                    .containsExactly(
                            "character_id",
                            "data_version",
                            "level",
                            "revision",
                            "updated_at",
                            "xp_in_level");
        }
    }

    @Test
    @DisplayName("raising the curve later cannot lower a stored level")
    void curveChangeDoesNotTouchStoredLevels() throws Exception {
        UUID characterId = insertCharacter();
        insertProgress(characterId, 12, 340L);

        // SC-017. There is nothing to do here and that is exactly the point: the level is stored, not
        // derived, so no curve can reach back and change it. If progress were one running total, this
        // test would need a curve at all - and would fail.
        assertThat(levelOf(characterId)).isEqualTo(12);
        assertThat(xpOf(characterId)).isEqualTo(340L);
    }

    // --- helpers ---------------------------------------------------------

    private static void migrate() {
        try (ConnectionPools pools = pools()) {
            new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest();
        } catch (Exception failure) {
            throw new IllegalStateException("migration failed", failure);
        }
    }

    private static UUID insertCharacter() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO rpg.player_state (player_id) VALUES ('" + playerId + "')");
            statement.execute(
                    "INSERT INTO rpg.character (character_id, player_id, character_class)"
                            + " VALUES ('"
                            + characterId
                            + "', '"
                            + playerId
                            + "', 'WARRIOR')");
        }
        return characterId;
    }

    private static void insertProgress(UUID characterId, int level, long xp) throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO rpg.character_progress (character_id, level, xp_in_level)"
                            + " VALUES ('"
                            + characterId
                            + "', "
                            + level
                            + ", "
                            + xp
                            + ")");
        }
    }

    private static int levelOf(UUID characterId) throws Exception {
        return (int) scalar(characterId, "level");
    }

    private static long xpOf(UUID characterId) throws Exception {
        return scalar(characterId, "xp_in_level");
    }

    private static long scalar(UUID characterId, String column) throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT "
                                        + column
                                        + " FROM rpg.character_progress WHERE character_id = '"
                                        + characterId
                                        + "'")) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static boolean rowExists(UUID characterId) throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT count(*) FROM rpg.character_progress"
                                        + " WHERE character_id = '"
                                        + characterId
                                        + "'")) {
            rows.next();
            return rows.getInt(1) > 0;
        }
    }

    private static boolean tableExists(String table) throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT to_regclass('rpg." + table + "') IS NOT NULL")) {
            rows.next();
            return rows.getBoolean(1);
        }
    }

    private static String constraintType(String name) throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT contype FROM pg_constraint WHERE conname = '"
                                        + name
                                        + "'")) {
            return rows.next() ? rows.getString(1) : null;
        }
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
}
