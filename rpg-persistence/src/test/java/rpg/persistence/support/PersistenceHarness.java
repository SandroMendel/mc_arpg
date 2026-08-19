package rpg.persistence.support;

import java.time.Clock;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.persistence.AggregateType;
import rpg.core.persistence.PersistenceConfig;
import rpg.core.persistence.WriteBehindBuffer;
import rpg.persistence.ConnectionPools;
import rpg.persistence.FlushCycle;
import rpg.persistence.OutageState;
import rpg.persistence.SchemaMigrator;
import rpg.persistence.jdbc.JdbcAuditLogRepository;
import rpg.persistence.jdbc.JdbcItemInstanceRepository;
import rpg.persistence.jdbc.JdbcPlayerStateRepository;
import rpg.persistence.jdbc.JdbcStatisticsRepository;

/**
 * A complete persistence stack wired against the test container.
 *
 * <p>Builds the same objects {@code PersistenceModule} builds, minus the B01 module plumbing, so
 * the integration tests exercise the shipping code rather than a parallel construction.
 */
public final class PersistenceHarness implements AutoCloseable {

    public final PersistenceConfig config;
    public final ConnectionPools pools;
    public final WriteBehindBuffer buffer;
    public final OutageState outageState;
    public final FlushCycle flushCycle;
    public final JdbcPlayerStateRepository playerStates;
    public final rpg.persistence.jdbc.JdbcCharacterRepository characters;
    public final JdbcItemInstanceRepository itemInstances;
    public final JdbcStatisticsRepository statistics;
    public final JdbcAuditLogRepository auditLog;
    public final DirectScheduler scheduler;

    public PersistenceHarness() {
        this(Clock.systemUTC(), 50_000);
    }

    public PersistenceHarness(Clock clock, int bufferCapacity) {
        Logger logger = Logger.getLogger(PersistenceHarness.class.getName());
        logger.setLevel(Level.OFF);

        config =
                new PersistenceConfig(
                        PostgresContainer.host(),
                        PostgresContainer.port(),
                        "vuntex_test",
                        PostgresContainer.username(),
                        PostgresContainer.password(),
                        4,
                        2,
                        Duration.ofSeconds(45),
                        bufferCapacity,
                        Duration.ofSeconds(8));

        pools = new ConnectionPools(config, logger);
        new SchemaMigrator(pools.writePool(), logger).migrateToLatest();

        buffer = new WriteBehindBuffer(bufferCapacity, clock);
        outageState = new OutageState(clock, logger);
        scheduler = new DirectScheduler();
        flushCycle =
                new FlushCycle(buffer, outageState, pools.writePool(), config, scheduler, logger, clock);

        playerStates =
                new JdbcPlayerStateRepository(pools.loginPool(), scheduler, flushCycle, logger, clock);
        characters =
                new rpg.persistence.jdbc.JdbcCharacterRepository(
                        pools.loginPool(), scheduler, flushCycle, clock);
        itemInstances = new JdbcItemInstanceRepository(pools.loginPool(), scheduler, flushCycle);
        statistics = new JdbcStatisticsRepository(pools.loginPool(), scheduler, flushCycle, clock);
        auditLog = new JdbcAuditLogRepository(pools.loginPool(), scheduler, flushCycle);

        flushCycle.register(AggregateType.PLAYER_STATE, playerStates);
        flushCycle.register(AggregateType.CHARACTER, characters);
        flushCycle.register(AggregateType.ITEM_INSTANCE, itemInstances);
        flushCycle.register(AggregateType.STATISTICS, statistics);
        flushCycle.register(AggregateType.AUDIT_LOG, auditLog);
        // Interval cycle deliberately NOT started: the tests trigger flushes explicitly, so a
        // background flush cannot make a result depend on timing.
    }

    @Override
    public void close() {
        flushCycle.stopIntervalCycle();
        pools.close();
    }
}
