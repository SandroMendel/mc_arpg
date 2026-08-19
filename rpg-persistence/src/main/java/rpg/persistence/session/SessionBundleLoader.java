package rpg.persistence.session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import rpg.core.persistence.ItemInstance;
import rpg.core.persistence.PersistenceException;
import rpg.core.persistence.PlayerState;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.SessionBundle;
import rpg.core.stats.CharacterResources;
import rpg.persistence.stats.JdbcCharacterResourcesRepository;

/**
 * Reads everything a session needs in a single database round (FR-005).
 *
 * <p>Three statements on <strong>one</strong> connection inside one transaction, rather than three
 * repository calls. At 200 simultaneous logins (SC-005) that is the difference between a login pool
 * under pressure and one that is barely touched - three calls would mean three connection
 * check-outs per login, which is exactly the load B02's separate login pool exists to avoid.
 *
 * <p>A single {@code JOIN} across all three tables was considered and rejected: a player with three
 * characters and fifty items would produce 150 rows carrying the account and character data over
 * and over, which then has to be unpicked in code. Three statements on one connection are faster
 * and far easier to read.
 *
 * <p>Runs off the tick - it is called from the async pre-login event, where blocking is expected.
 */
public final class SessionBundleLoader {

    private static final String SELECT_ACCOUNT =
            "SELECT player_id, data_version, revision, last_seen_at, anonymized"
                    + " FROM rpg.player_state WHERE player_id = ?";

    // Items hang off the character since ADR-011, so this reaches them through it and still gets
    // every item of the account in one statement. rolled_values is deliberately not selected: it is
    // a JSONB blob per item that the owning block (B11) reads when it needs it, and transferring it
    // for every item of every login would be the single largest part of this query for data nobody
    // looks at here.
    private static final String SELECT_ITEMS =
            "SELECT i.instance_id, i.owner_character_id, i.template_id, i.revision"
                    + " FROM rpg.item_instance i"
                    + " JOIN rpg.character c ON c.character_id = i.owner_character_id"
                    + " WHERE c.player_id = ?";

    private final DataSource loginPool;
    private final rpg.persistence.jdbc.JdbcCharacterRepository characters;
    private final Logger logger;

    public SessionBundleLoader(
            DataSource loginPool,
            rpg.persistence.jdbc.JdbcCharacterRepository characters,
            Logger logger) {
        this.loginPool = Objects.requireNonNull(loginPool, "loginPool");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Loads account, characters and items for one player.
     *
     * @throws PersistenceException if anything could not be read. Deliberately not an empty bundle:
     *     "never seen before" and "could not be read" must stay distinguishable, because the first
     *     is a normal first login and the second must refuse the login (FR-005a, FR-011).
     */
    public SessionBundle load(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        try (Connection connection = loginPool.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<PlayerState> account = readAccount(connection, playerId);
                if (account.isEmpty()) {
                    // A first-time player: nothing else can exist either, so the remaining two
                    // statements are skipped rather than run against a key that cannot match.
                    connection.commit();
                    return SessionBundle.empty(playerId);
                }
                List<PlayerCharacter> loaded = characters.readByPlayer(connection, playerId);
                List<ItemInstance> items = readItems(connection, playerId);
                List<CharacterResources> resources = readResources(connection, loaded);
                connection.commit();
                return new SessionBundle(playerId, account, loaded, items, resources);
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            logger.log(Level.SEVERE, "[session] could not load bundle for " + playerId, failure);
            throw new PersistenceException("could not load session data for " + playerId, failure);
        }
    }

    private static Optional<PlayerState> readAccount(Connection connection, UUID playerId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ACCOUNT)) {
            statement.setObject(1, playerId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(
                        new PlayerState(
                                rows.getObject("player_id", UUID.class),
                                rows.getInt("data_version"),
                                rows.getLong("revision"),
                                rows.getTimestamp("last_seen_at").toInstant(),
                                rows.getBoolean("anonymized")));
            }
        }
    }

    private static List<ItemInstance> readItems(Connection connection, UUID playerId)
            throws SQLException {
        List<ItemInstance> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ITEMS)) {
            statement.setObject(1, playerId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    items.add(
                            new ItemInstance(
                                    rows.getObject("instance_id", UUID.class),
                                    rows.getObject("owner_character_id", UUID.class),
                                    rows.getString("template_id"),
                                    Map.of(), // rolled values are read by the owning block (B11)
                                    rows.getLong("revision")));
                }
            }
        }
        return List.copyOf(items);
    }

    /**
     * Reads the stored resources of every character in this bundle (B04, FR-028).
     *
     * <p>The fourth statement on the same connection and inside the same transaction, so the "one
     * load, one round" property this class exists for survives. A character with no row is normal -
     * it means new, and the load path starts it at its maxima rather than treating the absence as a
     * fault.
     */
    private static List<CharacterResources> readResources(
            Connection connection, List<PlayerCharacter> characters) throws SQLException {
        if (characters.isEmpty()) {
            return List.of();
        }
        List<CharacterResources> resources = new ArrayList<>(characters.size());
        for (PlayerCharacter character : characters) {
            JdbcCharacterResourcesRepository.read(connection, character.characterId())
                    .ifPresent(resources::add);
        }
        return List.copyOf(resources);
    }
}
