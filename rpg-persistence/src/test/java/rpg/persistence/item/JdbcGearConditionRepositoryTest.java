package rpg.persistence.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.classes.LadderSlot;
import rpg.core.item.GearCondition;
import rpg.core.persistence.PersistenceConfig;
import rpg.persistence.ConnectionPools;
import rpg.persistence.SchemaMigrator;
import rpg.persistence.support.PostgresContainer;

/**
 * T104 — B11s Zustandstabelle gegen ein echtes PostgreSQL (Prinzip VII.2).
 *
 * <p><b>Gegen die echte Datenbank und nicht gegen ein Double</b>, weil jede Zusage hier dem Schema
 * gehört und nicht dem Code: der Kaskadenlöschung, der Wertebereichsprüfung und dem Unterschied
 * zwischen einer fehlenden Zeile und einer mit Vorgabewerten. Ein Double antwortete, was der Test ihm
 * beigebracht hat — und das Einzige, worauf es hier ankommt, wäre angenommen statt gezeigt.
 */
class JdbcGearConditionRepositoryTest {

    private static final Logger QUIET = Logger.getLogger("gear-condition-repository-test");

    @BeforeEach
    void freshSchema() throws Exception {
        PostgresContainer.resetSchema();
        migrate();
    }

    @Test
    @DisplayName("ein Charakter ohne Zeile liest LEER - und leer heisst voll, nicht kaputt")
    void acharacterWithoutARowReadsEmpty() throws Exception {
        UUID characterId = insertCharacter();

        try (Connection connection = PostgresContainer.openConnection()) {
            assertThat(JdbcGearConditionRepository.read(connection, characterId))
                    .as("wer noch nie gekaempft hat, hat keine Zeile - der Aufrufer setzt 100 ein")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("beide Leitern ueberleben getrennt einen Neustart")
    void bothLaddersSurviveARestartSeparately() throws Exception {
        UUID characterId = insertCharacter();
        writeCondition(characterId, 42.5, 91.25);

        // Eine neue Verbindung ist von hier aus, was ein Neustart ist: nichts im Speicher, alles
        // aus der Zeile zurueckgelesen.
        try (Connection connection = PostgresContainer.openConnection()) {
            GearCondition stored =
                    JdbcGearConditionRepository.read(connection, characterId).orElseThrow();

            assertThat(stored.of(LadderSlot.ARMOR)).isEqualTo(42.5);
            assertThat(stored.of(LadderSlot.WEAPON))
                    .as("getrennt gespeichert, weil sie getrennt verschleissen (FR-039)")
                    .isEqualTo(91.25);
        }
    }

    @Test
    @DisplayName("Bruchteile ueberleben - NUMERIC und nicht INTEGER, sonst gaebe es keinen Verschleiss")
    void fractionsSurvive() throws Exception {
        UUID characterId = insertCharacter();
        // Ein Treffer ueber 40 Punkte kostet bei 0,01 je Punkt 0,4 Zustandspunkte. Als Ganzzahl
        // gespeichert waere das null - und der Verschleiss existierte nur auf dem Papier.
        writeCondition(characterId, 99.6, 100.0);

        try (Connection connection = PostgresContainer.openConnection()) {
            assertThat(
                            JdbcGearConditionRepository.read(connection, characterId)
                                    .orElseThrow()
                                    .of(LadderSlot.ARMOR))
                    .isEqualTo(99.6);
        }
    }

    @Test
    @DisplayName("die Datenbank weist einen Zustand ausserhalb von [0, 100] ab")
    void thedatabaseRefusesAConditionOutsideItsRange() throws Exception {
        UUID characterId = insertCharacter();

        // Hier erzwungen und nicht der Anwendung geglaubt: ein negativer Zustand landete als Faktor
        // unterhalb des Restanteils - also als eine Ausruestung, die schlechter ist als keine.
        assertThatThrownBy(() -> writeCondition(characterId, -1.0, 100.0))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> writeCondition(characterId, 100.0, 101.0))
                .isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("zwei Charaktere eines Kontos haben getrennte Zustaende (ADR-011)")
    void twoCharactersAreSeparate() throws Exception {
        UUID first = insertCharacter();
        UUID second = insertCharacter();
        writeCondition(first, 10.0, 20.0);
        writeCondition(second, 100.0, 100.0);

        try (Connection connection = PostgresContainer.openConnection()) {
            assertThat(
                            JdbcGearConditionRepository.read(connection, second)
                                    .orElseThrow()
                                    .of(LadderSlot.ARMOR))
                    .as("innerhalb eines Kontos wird nichts vererbt")
                    .isEqualTo(100.0);
        }
    }

    @Test
    @DisplayName("ON DELETE CASCADE - ein geloeschter Charakter nimmt seinen Zustand mit")
    void deletingACharacterRemovesItsCondition() throws Exception {
        UUID characterId = insertCharacter();
        writeCondition(characterId, 55.0, 66.0);

        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "DELETE FROM rpg.character WHERE character_id = '" + characterId + "'");
        }

        try (Connection connection = PostgresContainer.openConnection()) {
            assertThat(JdbcGearConditionRepository.read(connection, characterId))
                    .as("B02s Loeschpfad muss von B11 nichts wissen - die Kaskade regelt es")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("readForPlayer holt alle Figuren eines Spielers in EINER Abfrage")
    void readForPlayerFetchesEveryCharacterAtOnce() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID first = insertCharacter(playerId, "WARRIOR");
        UUID second = insertCharacter(playerId, "ROGUE");
        UUID stranger = insertCharacter();
        writeCondition(first, 30.0, 40.0);
        writeCondition(second, 50.0, 60.0);
        writeCondition(stranger, 70.0, 80.0);

        try (Connection connection = PostgresContainer.openConnection()) {
            List<GearCondition> loaded =
                    JdbcGearConditionRepository.readForPlayer(connection, playerId);

            assertThat(loaded).hasSize(2);
            assertThat(loaded.stream().map(GearCondition::characterId))
                    .containsExactlyInAnyOrder(first, second);
        }
    }

    @Test
    @DisplayName("die Vorgabe einer frisch angelegten Zeile ist voll")
    void afreshRowDefaultsToFull() throws Exception {
        UUID characterId = insertCharacter();
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO rpg.character_gear_condition (character_id) VALUES ('"
                            + characterId
                            + "')");
        }

        try (Connection connection = PostgresContainer.openConnection()) {
            GearCondition stored =
                    JdbcGearConditionRepository.read(connection, characterId).orElseThrow();

            assertThat(stored.of(LadderSlot.ARMOR)).isEqualTo(100.0);
            assertThat(stored.of(LadderSlot.WEAPON)).isEqualTo(100.0);
        }
    }

    @Test
    @DisplayName("und die Migration hat die Tabelle wirklich angelegt")
    void themigrationCreatedTheTable() {
        assertThat(PostgresContainer.tableExists("character_gear_condition")).isTrue();
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

    /**
     * Ein Konto haelt HOECHSTENS EINE Figur je Klasse - {@code uq_character_player_class} aus B03.
     * Zwei Figuren eines Spielers brauchen deshalb zwei Klassen, und das ist keine Eigenart des
     * Tests, sondern die Regel des Spiels (ADR-011).
     */
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

    private static void writeCondition(UUID characterId, double armor, double weapon)
            throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "INSERT INTO rpg.character_gear_condition"
                                        + " (character_id, armor_condition, weapon_condition)"
                                        + " VALUES (?, ?, ?)"
                                        + " ON CONFLICT (character_id) DO UPDATE SET"
                                        + "   armor_condition = excluded.armor_condition,"
                                        + "   weapon_condition = excluded.weapon_condition")) {
            statement.setObject(1, characterId);
            statement.setDouble(2, armor);
            statement.setDouble(3, weapon);
            statement.executeUpdate();
        }
    }
}
