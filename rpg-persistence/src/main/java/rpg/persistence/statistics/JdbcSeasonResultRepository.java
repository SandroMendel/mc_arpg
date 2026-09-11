package rpg.persistence.statistics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.sql.DataSource;

import rpg.core.persistence.PersistenceException;
import rpg.core.statistics.Aggregation;
import rpg.core.statistics.ScoreWeights;
import rpg.core.statistics.SeasonStanding;
import rpg.persistence.jdbc.JsonValues;

/**
 * Der eingefrorene Saisonendstand — <b>ohne Write-Behind, und das ist eine Entscheidung</b> (R2).
 *
 * <h2>Warum hier direkt geschrieben wird</h2>
 *
 * <p>B02s Write-Behind existiert für einen bestimmten Fall: <em>viele</em> Änderungen je Sekunde,
 * die gebündelt werden müssen, damit der Tick sie nicht bezahlt. Ein Saisonendstand entsteht
 * <b>viermal im Jahr</b>. Ihn durch die Bündelung zu schicken hieße, für einen Vorgang, der nie
 * wieder kommt, eine Maschinerie zu benutzen, die für den Dauerbetrieb gebaut ist — mit allen
 * ihren Zuständen: Schmutzmarkierungen, Wiederholungen, ein Puffer, der volllaufen kann.
 *
 * <p>Und der entscheidende Unterschied: <b>ein Endstand darf nicht verloren gehen.</b> Beim
 * Write-Behind kostet ein Absturz höchstens ein Autosave-Intervall — bei den Tageszahlen ist das
 * die richtige Abwägung, bei einer vergebenen Platzierung nicht. Hier wird geschrieben und
 * committet, bevor irgendjemand ein Ergebnis zu sehen bekommt.
 *
 * <h2>Die Gewichtung geht mit</h2>
 *
 * <p>Als {@code JSONB} in derselben Zeile (FR-050, SC-021). Der Endstand trägt damit seine eigene
 * Rechenvorschrift bei sich und bleibt nachrechenbar, auch wenn {@code statistics.yml} inzwischen
 * etwas anderes sagt.
 */
public final class JdbcSeasonResultRepository {

    private static final String INSERT =
            "INSERT INTO rpg.season_result"
                    + " (season_key, player_id, rank, score, weights, frozen_at)"
                    + " VALUES (?, ?, ?, ?, ?::jsonb, ?)"
                    // Der Abschluss laeuft genau einmal (FR-058). Faellt er mitten im Schreiben
                    // aus und wird beim naechsten Start nachgeholt, sollen die bereits
                    // geschriebenen Zeilen kein Hindernis sein - aber auch nicht ueberschrieben
                    // werden: was einmal eingefroren ist, bleibt es.
                    + " ON CONFLICT (season_key, player_id) DO NOTHING";

    private static final String COUNT_FOR_SEASON =
            "SELECT COUNT(*) FROM rpg.season_result WHERE season_key = ?";

    private static final String STANDINGS =
            "SELECT season_key, player_id, rank, score, frozen_at FROM rpg.season_result"
                    + " WHERE season_key = ? ORDER BY rank, player_id";

    private static final String WEIGHTS =
            "SELECT weights FROM rpg.season_result WHERE season_key = ? LIMIT 1";

    private final DataSource dataSource;

    public JdbcSeasonResultRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /**
     * Friert einen Endstand ein.
     *
     * <p>In <b>einer</b> Transaktion: entweder steht die ganze Saison da oder keine Zeile von ihr.
     * Ein halber Endstand wäre schlimmer als keiner — er sähe vollständig aus, und die fehlenden
     * Konten hätten einfach keinen Platz bekommen.
     */
    public void freeze(List<SeasonStanding> standings, ScoreWeights weights) {
        if (standings.isEmpty()) {
            return;
        }
        String weightsJson = toJson(weights);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                for (SeasonStanding standing : standings) {
                    statement.setString(1, standing.seasonKey());
                    statement.setObject(2, standing.playerId());
                    statement.setInt(3, standing.rank());
                    statement.setLong(4, standing.score());
                    statement.setString(5, weightsJson);
                    statement.setTimestamp(6, Timestamp.from(standing.frozenAt()));
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new PersistenceException("freezing the season result failed", failure);
        }
    }

    /**
     * Ob diese Saison bereits abgeschlossen ist.
     *
     * <p><b>Das Vorhandensein von Zeilen ist der Beleg</b> (FR-058). Eine eigene
     * „abgeschlossen"-Spalte wäre eine zweite Wahrheit über dieselbe Tatsache — und die beiden
     * liefen beim ersten Abbruch mitten im Abschluss auseinander.
     */
    public boolean isClosed(String seasonKey) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(COUNT_FOR_SEASON)) {
            statement.setString(1, seasonKey);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getLong(1) > 0;
            }
        } catch (SQLException failure) {
            throw new PersistenceException("checking the season result failed", failure);
        }
    }

    /** Der eingefrorene Endstand einer Saison, nach Platz geordnet. */
    public List<SeasonStanding> standingsOf(String seasonKey) {
        List<SeasonStanding> standings = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(STANDINGS)) {
            statement.setString(1, seasonKey);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    standings.add(
                            new SeasonStanding(
                                    rows.getString(1),
                                    rows.getObject(2, UUID.class),
                                    rows.getInt(3),
                                    rows.getLong(4),
                                    rows.getTimestamp(5).toInstant()));
                }
            }
        } catch (SQLException failure) {
            throw new PersistenceException("reading the season result failed", failure);
        }
        return standings;
    }

    /**
     * Die Gewichtung, mit der dieser Endstand gerechnet wurde.
     *
     * <p>Aus der Zeile, nicht aus der Konfiguration — das ist der ganze Zweck der Spalte.
     */
    public ScoreWeights weightsOf(String seasonKey) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(WEIGHTS)) {
            statement.setString(1, seasonKey);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? fromJson(rows.getString(1)) : new ScoreWeights(Map.of());
            }
        } catch (SQLException failure) {
            throw new PersistenceException("reading the season weights failed", failure);
        }
    }

    private static String toJson(ScoreWeights weights) {
        Map<String, Object> flat = new LinkedHashMap<>();
        weights.byBoard().forEach((board, weight) -> flat.put(board.key(), weight));
        return JsonValues.toJson(flat);
    }

    private static ScoreWeights fromJson(String json) {
        Map<Aggregation, Double> byBoard = new LinkedHashMap<>();
        JsonValues.fromJson(json)
                .forEach(
                        (key, value) ->
                                Aggregation.byKey(key)
                                        .ifPresent(
                                                board ->
                                                        byBoard.put(
                                                                board,
                                                                ((Number) value).doubleValue())));
        return new ScoreWeights(byBoard);
    }
}
