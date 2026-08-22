package rpg.persistence.currency;

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
import rpg.persistence.ConnectionPools;
import rpg.persistence.SchemaMigrator;
import rpg.persistence.support.PostgresContainer;

/**
 * T032: die Migration {@code V8_2}, gegen ein echtes PostgreSQL statt gegen einen Mock (Prinzip VII).
 *
 * <p>Der Lauf geht ueber <b>alle</b> Migrationen von V1 an, nicht ueber eine leere Datenbank mit nur
 * dieser einen. Genau daran haette sich gezeigt, wenn {@code V8_2} auf einem Stand aus B08 nicht
 * aufsetzt.
 */
class CurrencyMigrationTest {

    private static final Logger QUIET = Logger.getLogger("currency-migration-test");

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        PostgresContainer.resetSchema();
        migrate();
    }

    @Test
    @DisplayName("V8_2 legt rpg.character_balance mit Schluessel, Fremdschluessel und Check an")
    void tableShape() throws Exception {
        assertThat(tableExists("character_balance")).isTrue();

        assertThat(constraintType("character_balance_pkey")).isEqualTo("p");
        assertThat(constraintType("chk_character_balance_not_negative")).isEqualTo("c");

        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT confdeltype FROM pg_constraint"
                                        + " WHERE conrelid = 'rpg.character_balance'::regclass"
                                        + " AND contype = 'f'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).as("ON DELETE CASCADE").isEqualTo("c");
        }
    }

    @Test
    @DisplayName("die Datenbank lehnt einen negativen Stand ab, was immer die Anwendung glaubt")
    void negativeBalanceIsRefusedByTheDatabase() throws Exception {
        UUID characterId = insertCharacter();

        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO rpg.character_balance (character_id, balance) VALUES ('"
                            + characterId
                            + "', 100)");

            assertThatThrownBy(
                            () ->
                                    statement.execute(
                                            "UPDATE rpg.character_balance SET balance = -1"
                                                    + " WHERE character_id = '"
                                                    + characterId
                                                    + "'"))
                    .as("die Zusage gilt auch fuer einen Schreibweg, den es heute noch nicht gibt")
                    .isInstanceOf(SQLException.class);

            assertThat(balanceOf(characterId)).isEqualTo(100L);
        }
    }

    @Test
    @DisplayName("ein negativer Anfangswert laesst sich gar nicht erst einfuegen")
    void negativeInsertIsRefused() throws Exception {
        UUID characterId = insertCharacter();

        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            assertThatThrownBy(
                            () ->
                                    statement.execute(
                                            "INSERT INTO rpg.character_balance"
                                                    + " (character_id, balance) VALUES ('"
                                                    + characterId
                                                    + "', -5)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    @DisplayName("ein Charakter ohne Zeile ist zulaessig - das ist der Normalfall")
    void noRowIsTheOrdinaryCase() throws Exception {
        UUID characterId = insertCharacter();

        assertThat(countBalances(characterId))
                .as("die Zeile entsteht bei der ersten Buchung, nicht bei der Erstellung")
                .isZero();
    }

    @Test
    @DisplayName("das Loeschen eines Charakters nimmt seinen Kontostand mit")
    void cascadeOnDelete() throws Exception {
        UUID characterId = insertCharacter();

        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO rpg.character_balance (character_id, balance) VALUES ('"
                            + characterId
                            + "', 500)");
            assertThat(countBalances(characterId)).isEqualTo(1);

            statement.execute(
                    "DELETE FROM rpg.character WHERE character_id = '" + characterId + "'");

            assertThat(countBalances(characterId))
                    .as("die Anonymisierung aus B02 raeumt mit, ohne von B08b zu wissen")
                    .isZero();
        }
    }

    @Test
    @DisplayName("es kann keine verwaiste Zeile geben")
    void foreignKeyIsEnforced() throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            assertThatThrownBy(
                            () ->
                                    statement.execute(
                                            "INSERT INTO rpg.character_balance"
                                                    + " (character_id, balance) VALUES ('"
                                                    + UUID.randomUUID()
                                                    + "', 1)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    @DisplayName("es gibt KEINE obere Schranke - was ein Spieler ansammeln darf, ist Balancing")
    void thereIsNoUpperBound() throws Exception {
        UUID characterId = insertCharacter();

        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO rpg.character_balance (character_id, balance) VALUES ('"
                            + characterId
                            + "', "
                            + Long.MAX_VALUE
                            + ")");
        }

        assertThat(balanceOf(characterId))
                .as("eine Schranke hier machte aus einer Balancing-Aenderung eine Migration")
                .isEqualTo(Long.MAX_VALUE);
    }

    // --- Hilfsmittel -----------------------------------------------------

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
            statement.execute(
                    "INSERT INTO rpg.player_state (player_id) VALUES ('" + playerId + "')");
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

    private static long balanceOf(UUID characterId) throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT balance FROM rpg.character_balance WHERE character_id = '"
                                        + characterId
                                        + "'")) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static int countBalances(UUID characterId) throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT count(*) FROM rpg.character_balance WHERE character_id = '"
                                        + characterId
                                        + "'")) {
            rows.next();
            return rows.getInt(1);
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
