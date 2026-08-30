package rpg.persistence.statistics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import rpg.core.persistence.PersistenceException;
import rpg.core.statistics.RewardClaim;
import rpg.core.statistics.StatisticsConfig;
import rpg.persistence.jdbc.JsonValues;

/**
 * Belohnungsansprüche — <b>und der Riegel, der genau einmal greift</b> (R2, FR-053).
 *
 * <h2>Das bedingte Update</h2>
 *
 * <pre>UPDATE ... SET claimed_at = ?, claimed_by_character = ?
 * WHERE season_key = ? AND player_id = ? AND claimed_at IS NULL</pre>
 *
 * <p>Zwei gleichzeitige Einlösungen sehen beide die offene Zeile. Beide setzen das Update ab —
 * aber die Datenbank führt sie nacheinander aus, und die zweite findet {@code claimed_at} nicht
 * mehr {@code NULL}. Sie bekommt <b>null betroffene Zeilen</b> zurück und weiß damit, dass sie zu
 * spät war.
 *
 * <p>Ein vorheriges {@code SELECT} („ist der Anspruch noch offen?") wäre die naheliegende
 * Umsetzung und die falsche: zwischen dem Lesen und dem Schreiben liegt ein Moment, in dem die
 * andere Einlösung dazwischenkommt. Bei einem einzelnen Spieler passiert das nie, bei einem
 * Doppelklick schon.
 *
 * <h2>Erst markieren, dann gutschreiben</h2>
 *
 * <p>Die Reihenfolge ist die ganze Entscheidung. Ein Absturz zwischen beidem kostet <b>höchstens
 * eine</b> Belohnung und verdoppelt <b>keine</b>. Andersherum — erst gutschreiben, dann markieren
 * — verdoppelte sie beim nächsten Versuch, und das ist der teurere Fehler: eine verlorene
 * Belohnung kann ein Betreiber nachtragen, eine verdoppelte muss er zurücknehmen.
 *
 * <p><b>Ohne Write-Behind</b>, aus demselben Grund wie beim Endstand: eine Einlösung geschieht
 * einmal je Spieler und Saison, und sie darf nicht verloren gehen.
 */
public final class JdbcRewardClaimRepository {

    private static final String INSERT =
            "INSERT INTO rpg.season_reward_claim"
                    + " (season_key, player_id, rank, reward) VALUES (?, ?, ?, ?::jsonb)"
                    + " ON CONFLICT (season_key, player_id) DO NOTHING";

    /** Der Riegel. Siehe Klassenkommentar. */
    private static final String MARK_CLAIMED =
            "UPDATE rpg.season_reward_claim"
                    + "   SET claimed_at = ?, claimed_by_character = ?"
                    + " WHERE season_key = ? AND player_id = ? AND claimed_at IS NULL";

    private static final String OPEN_FOR_PLAYER =
            "SELECT season_key, player_id, rank, reward, claimed_at, claimed_by_character"
                    + "  FROM rpg.season_reward_claim"
                    + " WHERE player_id = ? AND claimed_at IS NULL"
                    + " ORDER BY season_key";

    private static final String ONE =
            "SELECT season_key, player_id, rank, reward, claimed_at, claimed_by_character"
                    + "  FROM rpg.season_reward_claim WHERE season_key = ? AND player_id = ?";

    private final DataSource dataSource;

    public JdbcRewardClaimRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /** Legt die Ansprüche einer abgeschlossenen Saison an — in einer Transaktion. */
    public void create(List<RewardClaim> claims) {
        if (claims.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                for (RewardClaim claim : claims) {
                    statement.setString(1, claim.seasonKey());
                    statement.setObject(2, claim.playerId());
                    statement.setInt(3, claim.rank());
                    statement.setString(4, rewardJson(claim));
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new PersistenceException("creating reward claims failed", failure);
        }
    }

    /**
     * Markiert einen Anspruch als eingelöst.
     *
     * @return {@code true}, wenn <b>diese</b> Einlösung gewonnen hat — nur dann darf
     *     gutgeschrieben werden
     */
    public boolean markClaimed(String seasonKey, UUID playerId, UUID character, Instant at) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(MARK_CLAIMED)) {
            statement.setTimestamp(1, Timestamp.from(at));
            statement.setObject(2, character);
            statement.setString(3, seasonKey);
            statement.setObject(4, playerId);
            int affected = statement.executeUpdate();
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
            return affected == 1;
        } catch (SQLException failure) {
            throw new PersistenceException("claiming the season reward failed", failure);
        }
    }

    /** Die offenen Ansprüche eines Kontos — die Frage beim Anmelden (FR-052). */
    public List<RewardClaim> openFor(UUID playerId) {
        List<RewardClaim> claims = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(OPEN_FOR_PLAYER)) {
            statement.setObject(1, playerId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    claims.add(read(rows));
                }
            }
        } catch (SQLException failure) {
            throw new PersistenceException("reading open reward claims failed", failure);
        }
        return claims;
    }

    /** Ein einzelner Anspruch, offen oder nicht. */
    public Optional<RewardClaim> find(String seasonKey, UUID playerId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(ONE)) {
            statement.setString(1, seasonKey);
            statement.setObject(2, playerId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(read(rows)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw new PersistenceException("reading a reward claim failed", failure);
        }
    }

    private static RewardClaim read(ResultSet rows) throws SQLException {
        Map<String, Object> reward = JsonValues.fromJson(rows.getString(4));

        long coins = 0;
        List<StatisticsConfig.ItemGrant> items = new ArrayList<>();
        for (Map.Entry<String, Object> entry : reward.entrySet()) {
            if ("coins".equals(entry.getKey())) {
                coins = ((Number) entry.getValue()).longValue();
            } else if (entry.getKey().startsWith(ITEM_PREFIX)) {
                items.add(
                        new StatisticsConfig.ItemGrant(
                                entry.getKey().substring(ITEM_PREFIX.length()),
                                ((Number) entry.getValue()).intValue()));
            }
        }

        Timestamp claimedAt = rows.getTimestamp(5);
        UUID claimedBy = rows.getObject(6, UUID.class);

        return new RewardClaim(
                rows.getString(1),
                rows.getObject(2, UUID.class),
                rows.getInt(3),
                coins,
                items,
                Optional.ofNullable(claimedAt).map(Timestamp::toInstant),
                Optional.ofNullable(claimedBy));
    }

    /**
     * Der Inhalt als flaches JSON.
     *
     * <p>{@code {"coins": 5000, "item.trim.ember": 1}} — die Vorlagen werden zu Schlüsseln mit
     * einem festen Präfix, statt in eine verschachtelte Liste zu wandern. Der Grund ist
     * {@code JsonValues}: es kodiert flache Abbildungen, und sein Javadoc sagt ausdrücklich, dass
     * ein Block mit reicheren Dokumenten eine echte Bibliothek deklarieren soll, statt es zu
     * erweitern. Ein Präfix ist die kleinere Antwort — und eine Vorlagen-ID enthält nie einen
     * Doppelpunkt, an dem die Zerlegung mehrdeutig würde.
     */
    private static String rewardJson(RewardClaim claim) {
        Map<String, Object> flat = new LinkedHashMap<>();
        flat.put("coins", claim.coins());
        claim.items().forEach(item -> flat.put(ITEM_PREFIX + item.template(), item.amount()));
        return JsonValues.toJson(flat);
    }

    private static final String ITEM_PREFIX = "item.";
}
