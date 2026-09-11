package rpg.persistence.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PlayerState;
import rpg.core.session.CharacterClass;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * FR-022, FR-020, FR-021 — <b>zwei Zustände, zwei verschiedene Verdichtungen.</b>
 *
 * <p>Beide sind je Charakter gespeichert, und beide gehören auf ein Konto gerankt. Trotzdem wird
 * unterschiedlich zusammengefasst:
 *
 * <ul>
 *   <li><b>Coins: die Summe.</b> Geld ist teilbar und liegt verteilt; das Maximum zu nehmen hieße,
 *       den Rest zu verschweigen — wer auf drei Figuren je 1000 hat, hätte in der Rangliste 1000
 *       statt 3000.
 *   <li><b>Level: das Maximum.</b> Fortschritt ist nicht teilbar; die Summe machte aus einem
 *       Anfänger mit vier Figuren einen Veteranen.
 * </ul>
 *
 * <p>Genau die Sorte Unterschied, die beim Kopieren einer Abfrage untergeht — beide Abfragen
 * sehen sich zum Verwechseln ähnlich, und beide Ergebnisse sähen plausibel aus.
 *
 * <p>Läuft gegen eine echte PostgreSQL: die Verdichtung <em>ist</em> die Abfrage.
 */
class AccountCoinsAreTheSumTest {

    private PersistenceHarness harness;
    private UUID account;

    @BeforeEach
    void setUp() throws Exception {
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
        account = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(account, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("FR-022 - die Coins eines Kontos sind die SUMME seiner Charaktere")
    void coinsAreTheSum() throws Exception {
        character(CharacterClass.WARRIOR, 1, 0, 1_000);
        character(CharacterClass.MAGE, 1, 0, 2_500);
        character(CharacterClass.ROGUE, 1, 0, 500);

        assertThat(source().coins()).containsEntry(account, 4_000L);
    }

    @Test
    @DisplayName("FR-021 - der Level eines Kontos ist das MAXIMUM seiner Charaktere")
    void thelevelIsTheMaximum() throws Exception {
        character(CharacterClass.WARRIOR, 3, 10, 0);
        character(CharacterClass.MAGE, 60, 4_200, 0);
        character(CharacterClass.ROGUE, 12, 800, 0);

        Map<UUID, JdbcStateLeaderboardSource.LevelStanding> levels = source().levels();

        assertThat(levels.get(account).level()).as("nicht 75, nicht 25").isEqualTo(60);
        assertThat(levels.get(account).xpInLevel()).isEqualTo(4_200L);
        assertThat(levels.get(account).characterClass())
                .as("die Klasse GENAU dieses Charakters")
                .isEqualTo("MAGE");
    }

    @Test
    @DisplayName("FR-020 - bei gleichem Level gewinnt die hoehere XP innerhalb des Levels")
    void atequalLevelTheHigherXpInsideTheLevelWins() throws Exception {
        character(CharacterClass.WARRIOR, 42, 100, 0);
        character(CharacterClass.MAGE, 42, 9_000, 0);

        assertThat(source().levels().get(account).characterClass()).isEqualTo("MAGE");
        assertThat(source().levels().get(account).xpInLevel()).isEqualTo(9_000L);
    }

    @Test
    @DisplayName("ein anonymisiertes Konto steht in keiner der beiden Zustandsranglisten")
    void ananonymisedAccountIsInNeitherStateBoard() throws Exception {
        character(CharacterClass.WARRIOR, 60, 0, 5_000);

        assertThat(source().coins()).containsKey(account);

        harness.playerStates.anonymize(account).get();

        // Der Filter sitzt in denselben Abfragen - haette er hier gefehlt, waeren ausgerechnet
        // die beiden Ranglisten durchlaessig gewesen, die NICHT aus den Sichten kommen.
        assertThat(source().coins()).doesNotContainKey(account);
        assertThat(source().levels()).doesNotContainKey(account);
    }

    private JdbcStateLeaderboardSource source() {
        return new JdbcStateLeaderboardSource(harness.pools.loginPool());
    }

    private void character(CharacterClass characterClass, int level, long xp, long coins)
            throws Exception {
        UUID characterId = UUID.randomUUID();
        try (Connection connection = harness.pools.loginPool().getConnection()) {
            try (PreparedStatement statement =
                    connection.prepareStatement(
                            "INSERT INTO rpg.character (character_id, player_id, character_class)"
                                    + " VALUES (?, ?, ?)")) {
                statement.setObject(1, characterId);
                statement.setObject(2, account);
                statement.setString(3, characterClass.name());
                statement.executeUpdate();
            }
            try (PreparedStatement statement =
                    connection.prepareStatement(
                            "INSERT INTO rpg.character_progress (character_id, level, xp_in_level)"
                                    + " VALUES (?, ?, ?)")) {
                statement.setObject(1, characterId);
                statement.setInt(2, level);
                statement.setLong(3, xp);
                statement.executeUpdate();
            }
            try (PreparedStatement statement =
                    connection.prepareStatement(
                            "INSERT INTO rpg.character_balance (character_id, balance)"
                                    + " VALUES (?, ?)")) {
                statement.setObject(1, characterId);
                statement.setLong(2, coins);
                statement.executeUpdate();
            }
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        }
    }
}
