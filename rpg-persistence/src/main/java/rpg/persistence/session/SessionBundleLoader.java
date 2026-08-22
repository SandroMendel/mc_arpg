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

import rpg.core.classes.ClassProgress;
import rpg.core.ability.AbilityState;
import rpg.core.inventory.CharacterInventory;
import rpg.core.persistence.ItemInstance;
import rpg.core.persistence.PersistenceException;
import rpg.core.persistence.PlayerState;
import rpg.core.progression.CharacterProgress;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.SessionBundle;
import rpg.core.stats.CharacterResources;
import rpg.persistence.classes.JdbcClassProgressRepository;
import rpg.persistence.ability.JdbcAbilityStateRepository;
import rpg.persistence.inventory.JdbcCharacterInventoryRepository;
import rpg.persistence.progression.JdbcCharacterProgressRepository;
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
    private final rpg.persistence.jdbc.JdbcPlayerStateRepository playerStates;
    private final java.time.Clock clock;
    private final Logger logger;

    public SessionBundleLoader(
            DataSource loginPool,
            rpg.persistence.jdbc.JdbcCharacterRepository characters,
            rpg.persistence.jdbc.JdbcPlayerStateRepository playerStates,
            java.time.Clock clock,
            Logger logger) {
        this.loginPool = Objects.requireNonNull(loginPool, "loginPool");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.playerStates = Objects.requireNonNull(playerStates, "playerStates");
        this.clock = Objects.requireNonNull(clock, "clock");
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
                    // A first-time player: nothing else can exist either, so the remaining
                    // statements are skipped rather than run against a key that cannot match.
                    //
                    // The row itself is written now rather than left to the first flush. Everything a
                    // character hangs off references it - rpg.character.player_id is a foreign key -
                    // and B07 creates a character within seconds of the login, long before the
                    // autosave. Leaving it to the write-behind meant the very first class selection
                    // of every new player failed on that key.
                    PlayerState created = PlayerState.initial(playerId, clock.instant());
                    playerStates.insertInitial(connection, created);
                    connection.commit();
                    // Still the empty bundle: it describes what the login *found*, and it found
                    // nothing. The row that was just written is the account's existence, not its
                    // content - there is no state to hand to anyone.
                    return SessionBundle.empty(playerId);
                }
                List<PlayerCharacter> loaded = characters.readByPlayer(connection, playerId);
                List<ItemInstance> items = readItems(connection, playerId);
                List<CharacterResources> resources = readResources(connection, loaded);
                List<CharacterProgress> progress = readProgress(connection, loaded);
                List<ClassProgress> classProgress = readClassProgress(connection, loaded);
                List<CharacterInventory> inventories = readInventories(connection, loaded);
                List<AbilityState> abilities = readAbilities(connection, loaded);
                connection.commit();
                return new SessionBundle(
                        playerId,
                        account,
                        loaded,
                        items,
                        resources,
                        progress,
                        classProgress,
                        inventories,
                        abilities);
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

    /**
     * Reads what every character owns per ability (B08).
     *
     * <p>All characters, for the same reason as the inventories below: which one will be played is
     * decided by the selection afterwards (ADR-021), and asking again later would be a query on the
     * player's tick. An empty result is the ordinary case - a row exists only once something differs
     * from the default.
     */
    private static List<AbilityState> readAbilities(
            Connection connection, List<PlayerCharacter> characters) throws SQLException {
        if (characters.isEmpty()) {
            return List.of();
        }
        java.time.Instant now = java.time.Instant.now();
        List<AbilityState> abilities = new ArrayList<>();
        for (PlayerCharacter character : characters) {
            abilities.addAll(JdbcAbilityStateRepository.read(connection, character.characterId(), now));
        }
        return List.copyOf(abilities);
    }

    /**
     * Reads the stored inventory of every character in this bundle.
     *
     * <p>All of them, not only the one that will be played: which character that is has not been
     * decided at load time - the selection does that afterwards (ADR-021) - and asking again later
     * would be a query on the player's tick.
     */
    private static List<CharacterInventory> readInventories(
            Connection connection, List<PlayerCharacter> characters) throws SQLException {
        if (characters.isEmpty()) {
            return List.of();
        }
        List<CharacterInventory> inventories = new ArrayList<>(characters.size());
        for (PlayerCharacter character : characters) {
            JdbcCharacterInventoryRepository.read(connection, character.characterId())
                    .ifPresent(inventories::add);
        }
        return List.copyOf(inventories);
    }

    /**
     * The reached tiers of every character (B07).
     *
     * <p>On the same connection as everything else. B07 could have fetched it later through its
     * repository, but the class contributes tier values to the base stats - a tier that arrived after
     * the session was declared ready would make the character compute with tier 1 for a moment and
     * then visibly correct itself.
     */
    private static List<ClassProgress> readClassProgress(
            Connection connection, List<PlayerCharacter> characters) throws SQLException {
        if (characters.isEmpty()) {
            return List.of();
        }
        List<ClassProgress> progress = new ArrayList<>(characters.size());
        for (PlayerCharacter character : characters) {
            JdbcClassProgressRepository.read(connection, character.characterId())
                    .ifPresent(progress::add);
        }
        return List.copyOf(progress);
    }

    /**
     * Reads the stored progress of every character in this bundle (B06, FR-058).
     *
     * <p>On the same connection and inside the same transaction, for the same reason as the resources
     * above: the login path must not need a second round trip. A character with no row is normal - it
     * means level 1 with no experience, not a fault.
     */
    private static List<CharacterProgress> readProgress(
            Connection connection, List<PlayerCharacter> characters) throws SQLException {
        if (characters.isEmpty()) {
            return List.of();
        }
        List<CharacterProgress> progress = new ArrayList<>(characters.size());
        for (PlayerCharacter character : characters) {
            JdbcCharacterProgressRepository.read(connection, character.characterId())
                    .ifPresent(progress::add);
        }
        return List.copyOf(progress);
    }
}
