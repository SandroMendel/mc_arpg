package rpg.persistence.statistics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.sql.DataSource;

import rpg.core.persistence.PersistenceException;

/**
 * Die beiden Zustandsranglisten — <b>gelesen, nicht gespiegelt</b> (ADR-041, FR-019).
 *
 * <h2>Warum sie nicht aus der Statistiktabelle kommen</h2>
 *
 * <p>Level und Coins sind Zustände. Sie in die Tagestabelle zu schreiben wäre naheliegend, weil
 * dann alle Ranglisten aus einer Quelle kämen — und falsch, weil ein Zustand keine Tagesform hat:
 * die Summe von sieben Tagen Level 12 ist nicht Level 84. Was entstünde, wäre eine zweite Wahrheit
 * über Zahlen, die B06 und B08b bereits führen, und sie würde ab dem ersten Ausfall des
 * Schreibwegs von der ersten abweichen.
 *
 * <p><b>Der Preis dieser Entscheidung steht ausdrücklich da:</b> der Speicherstand hat damit zwei
 * Quellen und zwei Wege, unvollständig zu sein. Der Gewinn ist, dass es keine zweite Wahrheit gibt
 * — und dass die Zusage „das Öffnen kostet keine Abfrage" auch für diese beiden Listen gilt,
 * statt eine Ausnahme zu haben, die niemand im Fenster erkennt.
 *
 * <h2>Zwei Verdichtungen, und sie sind verschieden</h2>
 *
 * <ul>
 *   <li><b>Level: das Maximum</b> über die Charaktere eines Kontos (FR-021). Wer eine Figur auf 60
 *       gespielt hat, ist Stufe 60 — dass daneben zwei Anfänger stehen, macht ihn nicht
 *       schwächer. Bei Gleichstand entscheidet die XP <em>innerhalb</em> dieses Levels (FR-020);
 *       {@code xp_in_level} ist ausdrücklich kein Gesamtwert, weshalb ein Vergleich über
 *       Levelgrenzen hinweg bedeutungslos wäre.
 *   <li><b>Coins: die Summe</b> über die Charaktere (FR-022). Geld ist teilbar und liegt verteilt;
 *       das Maximum zu nehmen hieße, den Rest zu verschweigen.
 * </ul>
 *
 * <p>Zwei verschiedene Verdichtungen für zwei Zustände, die beide „je Charakter" gespeichert sind
 * — genau die Sorte Unterschied, die man beim Kopieren einer Abfrage übersieht.
 */
public final class JdbcStateLeaderboardSource {

    /**
     * Level je Konto: der höchste, bei Gleichstand die höchste XP <em>in</em> diesem Level.
     *
     * <p>{@code DISTINCT ON} nimmt je Konto die erste Zeile der Sortierung — der Postgres-Weg,
     * „die beste Zeile je Gruppe" ohne Unterabfrage zu bekommen. Die Sortierung ist genau die
     * Ordnung aus FR-020.
     */
    private static final String LEVELS =
            "SELECT DISTINCT ON (c.player_id)"
                    + "       c.player_id, p.level, p.xp_in_level, c.character_class"
                    + "  FROM rpg.character_progress p"
                    + "  JOIN rpg.character c    ON c.character_id = p.character_id"
                    + "  JOIN rpg.player_state s ON s.player_id = c.player_id"
                    + "                         AND s.anonymized = FALSE"
                    + " ORDER BY c.player_id, p.level DESC, p.xp_in_level DESC";

    private static final String COINS =
            "SELECT c.player_id, SUM(b.balance) AS total"
                    + "  FROM rpg.character_balance b"
                    + "  JOIN rpg.character c    ON c.character_id = b.character_id"
                    + "  JOIN rpg.player_state s ON s.player_id = c.player_id"
                    + "                         AND s.anonymized = FALSE"
                    + " GROUP BY c.player_id";

    /**
     * Der Levelstand eines Kontos samt der Klasse, die ihn hält.
     *
     * @param level der höchste Level
     * @param xpInLevel die XP innerhalb dieses Levels — der Gleichstandsentscheid
     * @param characterClass die Klasse <b>genau dieses</b> Charakters, nicht irgendeine des Kontos
     */
    public record LevelStanding(int level, long xpInLevel, String characterClass) {}

    private final DataSource dataSource;

    public JdbcStateLeaderboardSource(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /** Der Levelstand je Konto, in der Ordnung aus FR-020. */
    public Map<UUID, LevelStanding> levels() {
        Map<UUID, LevelStanding> byAccount = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(LEVELS);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                byAccount.put(
                        rows.getObject(1, UUID.class),
                        new LevelStanding(rows.getInt(2), rows.getLong(3), rows.getString(4)));
            }
        } catch (SQLException failure) {
            throw new PersistenceException("level leaderboard query failed", failure);
        }
        return byAccount;
    }

    /** Die Coins je Konto — die Summe über seine Charaktere (FR-022). */
    public Map<UUID, Long> coins() {
        Map<UUID, Long> byAccount = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(COINS);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                byAccount.put(rows.getObject(1, UUID.class), rows.getLong(2));
            }
        } catch (SQLException failure) {
            throw new PersistenceException("coin leaderboard query failed", failure);
        }
        return byAccount;
    }
}
