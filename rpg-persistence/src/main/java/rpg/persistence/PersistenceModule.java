package rpg.persistence;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.config.ConfigValidationException;
import rpg.core.message.MessageKey;
import rpg.core.module.Module;
import rpg.core.module.ModuleContext;
import rpg.core.persistence.AggregateType;
import rpg.core.persistence.AuditLogRepository;
import rpg.core.persistence.ItemInstanceRepository;
import rpg.core.persistence.PersistenceConfig;
import rpg.core.persistence.PersistenceStartupException;
import rpg.core.persistence.PlayerStateRepository;
import rpg.core.persistence.StatisticsRepository;
import rpg.core.persistence.WriteBehindBuffer;
import rpg.core.persistence.WriteBehindCoordinator;
import rpg.persistence.jdbc.JdbcAuditLogRepository;
import rpg.persistence.jdbc.JdbcItemInstanceRepository;
import rpg.persistence.jdbc.JdbcPlayerStateRepository;
import rpg.persistence.jdbc.JdbcStatisticsRepository;

/**
 * B02 as a module under the B01 contract.
 *
 * <p>Start order matters and is not arbitrary:
 *
 * <ol>
 *   <li>load and validate configuration - fail fast on a bad value (FR-022)
 *   <li>open the pools - fail fast if storage is unreachable (FR-014)
 *   <li>migrate the schema - fail fast if a migration fails (FR-014)
 *   <li>publish the repositories, so other blocks can resolve them
 *   <li>start the flush cycle
 * </ol>
 *
 * <p>Anything failing in {@code start} marks this module {@code FAILED} and aborts the bootstrap
 * (B01/FR-013). That is the wanted behaviour rather than an inconvenience: a server without working
 * persistence must not accept players, because every session it granted would lose its progress.
 *
 * <p>Registered as the first module by the plugin, and therefore stopped last - by then every other
 * block has handed its final changes to the buffer.
 */
public final class PersistenceModule implements Module {

    /** Stable identifier; independent of this class's name (B01/FR-001a). */
    public static final String ID = "persistence";

    private static final String CONFIG_FILE = "persistence.yml";

    private final Logger logger;
    private final Clock clock;

    private PersistenceConfig config;
    private ConnectionPools pools;
    private WriteBehindBuffer buffer;
    private FlushCycle flushCycle;
    private OutageState outageState;
    private JdbcPlayerStateRepository playerStates;
    private JdbcItemInstanceRepository itemInstances;
    private JdbcStatisticsRepository statistics;
    private JdbcAuditLogRepository auditLog;
    private SessionHandover sessionHandover;

    public PersistenceModule(Logger logger, Clock clock) {
        this.logger = logger;
        this.clock = clock;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> dependencies() {
        // Nothing: persistence is the foundation everything else builds on.
        return List.of();
    }

    @Override
    public void start(ModuleContext context) throws Exception {
        config = loadConfig(context);
        logger.info("[persistence] configuration accepted - " + config.describeWithoutSecrets());

        pools = new ConnectionPools(config, logger);

        // Before any repository is published: no block may touch a database whose schema state is
        // unconfirmed.
        SchemaMigrator migrator = new SchemaMigrator(pools.writePool(), logger);
        migrator.migrateToLatest();

        buffer = new WriteBehindBuffer(config.bufferCapacity(), clock);
        outageState = new OutageState(clock, logger);
        flushCycle =
                new FlushCycle(
                        buffer,
                        outageState,
                        pools.writePool(),
                        config,
                        context.scheduler(),
                        logger,
                        clock);

        // Repositories: each is both the block-facing interface and the writer for its table.
        playerStates =
                new JdbcPlayerStateRepository(
                        pools.loginPool(), context.scheduler(), flushCycle, logger, clock);
        itemInstances =
                new JdbcItemInstanceRepository(pools.loginPool(), context.scheduler(), flushCycle);
        statistics =
                new JdbcStatisticsRepository(
                        pools.loginPool(), context.scheduler(), flushCycle, clock);
        auditLog = new JdbcAuditLogRepository(pools.loginPool(), context.scheduler(), flushCycle);

        flushCycle.register(AggregateType.PLAYER_STATE, playerStates);
        flushCycle.register(AggregateType.ITEM_INSTANCE, itemInstances);
        flushCycle.register(AggregateType.STATISTICS, statistics);
        flushCycle.register(AggregateType.AUDIT_LOG, auditLog);

        sessionHandover =
                new SessionHandover(
                        playerStates, flushCycle, SessionHandover.DEFAULT_WAIT, logger);

        // Published through the registry so other blocks reach them by interface only (FR-015).
        context.registry().registerService(ID, PlayerStateRepository.class, playerStates);
        context.registry().registerService(ID, ItemInstanceRepository.class, itemInstances);
        context.registry().registerService(ID, StatisticsRepository.class, statistics);
        context.registry().registerService(ID, AuditLogRepository.class, auditLog);
        context.registry().registerService(ID, WriteBehindCoordinator.class, flushCycle);

        flushCycle.startIntervalCycle();
        logger.info(
                "[persistence] ready - autosave every "
                        + config.autosave().toSeconds()
                        + "s, buffer capacity "
                        + config.bufferCapacity());
    }

    @Override
    public void stop() throws Exception {
        // Order is essential: flush first, close the pools afterwards. Closing first would make the
        // flush impossible and silently lose everything still pending.
        if (flushCycle != null) {
            flushCycle.shutdownFlush();
        }
        if (pools != null) {
            pools.close();
        }
    }

    /** Whether a player may be granted a session right now (FR-005a, FR-009b). */
    public boolean acceptsLogins() {
        return outageState != null && outageState.isReachable() && !buffer.isOverCapacity();
    }

    /** Why logins are currently refused, as a message key; empty when they are accepted. */
    public java.util.Optional<MessageKey> loginRefusalReason() {
        if (outageState != null && !outageState.isReachable()) {
            return java.util.Optional.of(PersistenceMessageKeys.KICK_DATABASE_UNAVAILABLE);
        }
        if (buffer != null && buffer.isOverCapacity()) {
            return java.util.Optional.of(PersistenceMessageKeys.KICK_BUFFER_EXHAUSTED);
        }
        return java.util.Optional.empty();
    }

    /** Loads and stores state across a session boundary (FR-019a). */
    public SessionHandover sessionHandover() {
        return sessionHandover;
    }

    /** The write-behind buffer; other blocks reach it only through their repository. */
    public WriteBehindBuffer buffer() {
        return buffer;
    }

    /**
     * The connection pools.
     *
     * <p>Exposed for the session module's bundled read (B03/FR-005), which needs the login pool to
     * put three statements on one connection. Still confined to {@code rpg-persistence} - B02's
     * {@code NoDirectDatabaseAccessTest} keeps {@code DataSource} out of every other module.
     */
    public ConnectionPools pools() {
        return pools;
    }

    /** The flush cycle, for the session-end trigger. */
    public FlushCycle flushCycle() {
        return flushCycle;
    }

    private PersistenceConfig loadConfig(ModuleContext context) {
        try {
            return context.configLoader()
                    .register(Path.of(CONFIG_FILE), PersistenceConfig.schema())
                    .get();
        } catch (ConfigValidationException invalid) {
            // Fail fast with the file, path and expected value already in the message (B01/FR-002).
            logger.log(Level.SEVERE, "[persistence] configuration rejected", invalid);
            throw new PersistenceStartupException(
                    "persistence configuration is invalid: " + invalid.getMessage(), invalid);
        }
    }
}
