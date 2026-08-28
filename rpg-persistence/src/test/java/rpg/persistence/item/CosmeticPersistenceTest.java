package rpg.persistence.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.item.CosmeticUnlock;
import rpg.core.persistence.PersistenceConfig;
import rpg.persistence.ConnectionPools;
import rpg.persistence.SchemaMigrator;
import rpg.persistence.support.PostgresContainer;

/**
 * T120, T125 — B11s Kosmetiktabelle gegen ein echtes PostgreSQL (Prinzip VII.2).
 *
 * <p><b>Der Teilindex ist der Grund, dass dieser Test gegen die echte Datenbank läuft.</b> FR-071
 * sagt „höchstens eine getragene Farbe je Charakter". Das steht auch in {@code CosmeticApplication} —
 * dort, damit der Spieler eine Antwort bekommt. Hier steht es, damit es <em>stimmt</em>. Eine Regel,
 * die nur in der Anwendung lebt, gilt genau so lange, wie jeder Schreiber durch die Anwendung geht;
 * ein Betreiberbefehl oder ein Reparaturskript hält sich nicht daran.
 *
 * <p>Ein Testdouble könnte diese Zusage gar nicht prüfen — es würde antworten, was der Test ihm
 * beigebracht hat.
 */
class CosmeticPersistenceTest {

    private static final Logger QUIET = Logger.getLogger("cosmetic-persistence-test");

    @BeforeEach
    void freshSchema() throws Exception {
        PostgresContainer.resetSchema();
        migrate();
    }

    @Test
    @DisplayName("die Migration hat Tabelle und Teilindex angelegt")
    void themigrationCreatedTableAndIndex() throws Exception {
        assertThat(PostgresContainer.tableExists("character_cosmetic")).isTrue();

        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                var rows =
                        statement.executeQuery(
                                "SELECT indexdef FROM pg_indexes"
                                        + " WHERE schemaname = 'rpg'"
                                        + " AND indexname = 'uq_cosmetic_one_applied'")) {
            assertThat(rows.next()).as("ohne den Index waere FR-071 eine Absichtserklaerung").isTrue();
            assertThat(rows.getString(1)).contains("WHERE applied");
        }
    }

    @Test
    @DisplayName("ein Charakter ohne Zeile besitzt nichts - das ist kein Fehler")
    void acharacterWithoutRowsOwnsNothing() throws Exception {
        UUID characterId = insertCharacter();

        try (Connection connection = PostgresContainer.openConnection()) {
            assertThat(JdbcCosmeticRepository.read(connection, characterId)).isEmpty();
        }
    }

    @Test
    @DisplayName("mehrere Farben je Charakter sind der Normalfall")
    void severalColoursPerCharacterAreOrdinary() throws Exception {
        UUID characterId = insertCharacter();
        write(characterId, "trim.ember", false);
        write(characterId, "trim.frost", true);
        write(characterId, "trim.verdant", false);

        try (Connection connection = PostgresContainer.openConnection()) {
            List<CosmeticUnlock> owned = JdbcCosmeticRepository.read(connection, characterId);

            assertThat(owned).hasSize(3);
            assertThat(owned.stream().filter(CosmeticUnlock::applied).map(CosmeticUnlock::templateKey))
                    .containsExactly("trim.frost");
        }
    }

    @Test
    @DisplayName("FR-071 auf DATENBANKEBENE: eine zweite getragene Farbe wird abgewiesen")
    void asecondAppliedColourIsRefusedByTheDatabase() throws Exception {
        UUID characterId = insertCharacter();
        write(characterId, "trim.ember", true);

        assertThatThrownBy(() -> write(characterId, "trim.frost", true))
                .as("der Teilindex haelt die Regel, auch wenn niemand durch die Anwendung geht")
                .isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("aber beliebig viele NICHT getragene - der Besitz ist der Grind")
    void butAnyNumberOfUnwornOnes() throws Exception {
        UUID characterId = insertCharacter();

        write(characterId, "trim.ember", false);
        write(characterId, "trim.frost", false);
        write(characterId, "trim.verdant", false);

        try (Connection connection = PostgresContainer.openConnection()) {
            assertThat(JdbcCosmeticRepository.read(connection, characterId)).hasSize(3);
        }
    }

    @Test
    @DisplayName("zwei Charaktere tragen unabhaengig voneinander (ADR-011)")
    void twoCharactersAreIndependent() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID first = insertCharacter(playerId, "WARRIOR");
        UUID second = insertCharacter(playerId, "ROGUE");

        write(first, "trim.ember", true);
        write(second, "trim.ember", true);

        try (Connection connection = PostgresContainer.openConnection()) {
            // Derselbe Schluessel, beide getragen - das ist erlaubt, weil der Index je CHARAKTER
            // greift und nicht je Konto. Was der Krieger traegt, geht den Schurken nichts an.
            assertThat(JdbcCosmeticRepository.read(connection, first)).hasSize(1);
            assertThat(JdbcCosmeticRepository.read(connection, second)).hasSize(1);
        }
    }

    @Test
    @DisplayName("die Farbe uebersteht einen Neustart (FR-072)")
    void thecolourSurvivesARestart() throws Exception {
        UUID characterId = insertCharacter();
        write(characterId, "trim.verdant", true);

        // Eine neue Verbindung ist von hier aus, was ein Neustart ist.
        try (Connection connection = PostgresContainer.openConnection()) {
            CosmeticUnlock stored =
                    JdbcCosmeticRepository.read(connection, characterId).getFirst();

            assertThat(stored.templateKey()).isEqualTo("trim.verdant");
            assertThat(stored.applied()).isTrue();
        }
    }

    @Test
    @DisplayName("dieselbe Farbe wird nicht zweimal gekauft - der Primaerschluessel verhindert es")
    void thesameColourIsNotBoughtTwice() throws Exception {
        UUID characterId = insertCharacter();
        write(characterId, "trim.ember", false);

        assertThatThrownBy(() -> write(characterId, "trim.ember", false))
                .isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("ON DELETE CASCADE - ein geloeschter Charakter nimmt seine Farben mit")
    void deletingACharacterRemovesItsColours() throws Exception {
        UUID characterId = insertCharacter();
        write(characterId, "trim.ember", true);

        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM rpg.character WHERE character_id = '" + characterId + "'");
        }

        try (Connection connection = PostgresContainer.openConnection()) {
            assertThat(JdbcCosmeticRepository.read(connection, characterId)).isEmpty();
        }
    }

    @Test
    @DisplayName("readForPlayer holt alle Figuren eines Spielers in EINER Abfrage")
    void readForPlayerFetchesEveryCharacterAtOnce() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID first = insertCharacter(playerId, "WARRIOR");
        UUID second = insertCharacter(playerId, "MAGE");
        UUID stranger = insertCharacter();
        write(first, "trim.ember", true);
        write(second, "trim.frost", false);
        write(stranger, "trim.verdant", true);

        try (Connection connection = PostgresContainer.openConnection()) {
            List<CosmeticUnlock> loaded =
                    JdbcCosmeticRepository.readForPlayer(connection, playerId);

            assertThat(loaded).hasSize(2);
            assertThat(loaded.stream().map(CosmeticUnlock::characterId))
                    .containsExactlyInAnyOrder(first, second);
        }
    }

    // --- Hilfsmittel -----------------------------------------------------

    private static void migrate() {
        try (ConnectionPools pools = pools()) {
            new SchemaMigrator(pools.writePool(), QUIET).migrateToLatest();
        } catch (Exception failure) {
            throw new IllegalStateException("migration failed", failure);
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

    private static UUID insertCharacter() throws Exception {
        return insertCharacter(UUID.randomUUID(), "WARRIOR");
    }

    /** Ein Konto hält höchstens eine Figur je Klasse — {@code uq_character_player_class} aus B03. */
    private static UUID insertCharacter(UUID playerId, String characterClass) throws Exception {
        UUID characterId = UUID.randomUUID();
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO rpg.player_state (player_id) VALUES ('"
                            + playerId
                            + "') ON CONFLICT DO NOTHING");
            statement.execute(
                    "INSERT INTO rpg.character (character_id, player_id, character_class)"
                            + " VALUES ('"
                            + characterId
                            + "', '"
                            + playerId
                            + "', '"
                            + characterClass
                            + "')");
        }
        return characterId;
    }

    private static void write(UUID characterId, String templateKey, boolean applied)
            throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "INSERT INTO rpg.character_cosmetic"
                                        + " (character_id, template_key, applied, acquired_at)"
                                        + " VALUES (?, ?, ?, ?)")) {
            statement.setObject(1, characterId);
            statement.setString(2, templateKey);
            statement.setBoolean(3, applied);
            statement.setTimestamp(4, java.sql.Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }
}
