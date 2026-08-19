package rpg.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import rpg.core.persistence.PlayerState;

/**
 * Maps a {@code player_state} row onto the domain record.
 *
 * <p>Hand-written rather than generated: a {@code ResultSet} is a positional cursor, not a bean, so
 * MapStruct has nothing to bind against here. It comes into its own where both sides are typed -
 * the DTO-to-domain mappings the later blocks will add.
 *
 * <p>Instantiated as a constant rather than looked up: a {@code Mappers.getMapper} style lookup is
 * the one API that would pull the MapStruct runtime jar into the shipped artifact, and B01
 * deliberately keeps that jar out (ADR-010).
 */
public final class PlayerStateRowMapper {

    public static final PlayerStateRowMapper INSTANCE = new PlayerStateRowMapper();

    private PlayerStateRowMapper() {}

    /** Reads the current row. The caller has already positioned the cursor. */
    public PlayerState fromRow(ResultSet row) throws SQLException {
        return new PlayerState(
                row.getObject("player_id", UUID.class),
                row.getInt("data_version"),
                row.getLong("revision"),
                row.getTimestamp("last_seen_at").toInstant(),
                row.getBoolean("anonymized"));
    }
}
