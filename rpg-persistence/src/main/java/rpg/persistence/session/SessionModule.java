package rpg.persistence.session;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.config.ConfigValidationException;
import rpg.core.module.Module;
import rpg.core.module.ModuleContext;
import rpg.core.persistence.AggregateType;
import rpg.core.persistence.PersistenceStartupException;
import rpg.core.session.CharacterRepository;
import rpg.core.session.DefaultOfflinePlayerReader;
import rpg.core.session.DefaultSessionLifecycle;
import rpg.core.session.DefaultSessionRegistry;
import rpg.core.session.OfflinePlayerReader;
import rpg.core.session.SessionConfig;
import rpg.core.session.SessionEndReason;
import rpg.core.session.SessionReconciler;
import rpg.core.session.SessionRegistry;
import rpg.core.session.StateVersionMigrator;
import rpg.persistence.PersistenceModule;
import rpg.persistence.jdbc.JdbcCharacterRepository;

/**
 * B03 as a module under the B01 contract.
 *
 * <p><strong>Why this lives in {@code rpg-persistence} and not in {@code rpg-platform}</strong>:
 * it wires {@link JdbcCharacterRepository} and {@link SessionBundleLoader}, both of which live
 * here. Putting the module in {@code rpg-platform} would require that module to depend on
 * {@code rpg-persistence} - reversing the {@code plugin → platform → core} direction Constitution
 * III.2 fixes. B02's {@code PersistenceModule} resolves the same situation the same way.
 *
 * <p>The Paper-facing listeners stay in {@code rpg-platform}, know only interfaces from
 * {@code rpg-core}, and are assembled by {@code rpg-plugin} - the one module that sees both sides.
 *
 * <p>Depends on {@code persistence}, so B01's start order guarantees B02 is up first: the session
 * layer needs its repositories and its flush cycle.
 */
public final class SessionModule implements Module {

    /** Stable identifier; independent of this class's name (B01/FR-001a). */
    public static final String ID = "session";

    private static final String CONFIG_FILE = "session.yml";

    private final PersistenceModule persistence;
    private final Logger logger;
    private final Clock clock;

    private SessionConfig config;
    private DefaultSessionRegistry registry;
    private JdbcCharacterRepository characters;
    private SessionBundleLoader bundleLoader;
    private DefaultSessionLifecycle lifecycle;
    private DefaultOfflinePlayerReader offlineReader;
    private SessionReconciler reconciler;
    private rpg.core.scheduler.Scheduler scheduler;
    private volatile boolean stopped;

    public SessionModule(PersistenceModule persistence, Logger logger, Clock clock) {
        this.persistence = persistence;
        this.logger = logger;
        this.clock = clock;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> dependencies() {
        return List.of(PersistenceModule.ID);
    }

    @Override
    public void start(ModuleContext context) throws Exception {
        config = loadConfig(context);
        logger.info(
                "[session] configuration accepted - loadTimeout="
                        + config.loadTimeout().toSeconds()
                        + "s reconcileInterval="
                        + config.reconcileInterval().toSeconds()
                        + "s");

        registry = new DefaultSessionRegistry();
        characters =
                new JdbcCharacterRepository(
                        persistence.pools().loginPool(),
                        context.scheduler(),
                        persistence.flushCycle(),
                        clock);
        persistence.flushCycle().register(AggregateType.CHARACTER, characters);

        bundleLoader =
                new SessionBundleLoader(persistence.pools().loginPool(), characters, logger);
        scheduler = context.scheduler();

        lifecycle =
                new DefaultSessionLifecycle(
                        registry,
                        this::loadBundleAfterHandover,
                        new PersistenceSessionWriter(persistence, characters),
                        new StateVersionMigrator(logger),
                        // Every load runs off the tick. The pre-login event is already async, and
                        // blocking there is what keeps the world thread out of the database.
                        scheduler::runAsync,
                        clock,
                        logger);

        offlineReader = new DefaultOfflinePlayerReader(registry, characters);

        // Published so other blocks reach sessions by interface only. Note what is NOT published:
        // the lifecycle stays internal, because a block that could open a session could open a
        // second one (FR-014).
        context.registry().registerService(ID, SessionRegistry.class, registry);
        context.registry().registerService(ID, CharacterRepository.class, characters);
        context.registry().registerService(ID, OfflinePlayerReader.class, offlineReader);

        logger.info("[session] ready");
    }

    /**
     * The load, in the one order that cannot lose progress (FR-019a).
     *
     * <p>First B02 drains whatever a previous session still owes, then the bundle is read. The
     * reverse order is the ghost-session bug: a player drops with unwritten changes, reconnects
     * immediately, the new session reads the older stored state, and the previous session's flush
     * then lands on top of it - progress rolled back or items duplicated.
     *
     * <p>The account row is read twice as a result: once by B02's handover and once by the bundle.
     * That is a single primary-key lookup next to a flush that just completed, and the alternative -
     * reimplementing the sequencing here - is exactly the duplication this block must not create.
     */
    private rpg.core.session.SessionBundle loadBundleAfterHandover(java.util.UUID playerId) {
        persistence.sessionHandover().loadForNewSession(playerId).join();
        return bundleLoader.load(playerId);
    }

    /**
     * Starts the reconciliation sweep and returns it.
     *
     * <p>Called by the plugin once the Paper side exists, because the sweep needs to know who is
     * actually connected - which is a question only the server can answer. Everything it does with
     * that answer lives in {@code rpg-core}.
     */
    public SessionReconciler startReconciliation(
            java.util.function.Supplier<java.util.Collection<java.util.UUID>> connectedPlayers,
            Runnable stashSweep) {
        reconciler =
                new SessionReconciler(registry, connectedPlayers, lifecycle, stashSweep, logger);
        scheduleNextSweep();
        return reconciler;
    }

    /**
     * Schedules the next sweep, which schedules the one after it.
     *
     * <p>A self-rescheduling delayed task rather than a repeating one: a sweep that runs long
     * cannot overlap with the next, and stopping is simply a matter of not scheduling again.
     */
    private void scheduleNextSweep() {
        if (stopped || reconciler == null) {
            return;
        }
        scheduler.runAsyncDelayed(
                config.reconcileInterval(),
                () -> {
                    try {
                        reconciler.reconcileOnce();
                    } catch (RuntimeException failure) {
                        // A failed sweep must not end the sweeping - that would turn a transient
                        // problem into a permanent leak.
                        logger.log(Level.WARNING, "[session] reconciliation sweep failed", failure);
                    } finally {
                        scheduleNextSweep();
                    }
                });
    }

    @Override
    public void stop() throws Exception {
        stopped = true; // no further sweep is scheduled
        if (registry == null) {
            return;
        }

        List<rpg.core.session.PlayerSession> open = registry.all();
        logger.info("[session] shutting down with " + open.size() + " session(s)");

        // Every open session is ended so its changes are marked before B02 flushes. B01 stops
        // modules in reverse start order, so B02's shutdown flush - with its 8 second budget -
        // runs after this and is what actually writes them. Ending them here without that ordering
        // would mark changes nobody then persists.
        for (rpg.core.session.PlayerSession session : open) {
            try {
                lifecycle.endSession(session.playerId(), SessionEndReason.SHUTDOWN);
            } catch (RuntimeException failure) {
                logger.log(
                        Level.WARNING,
                        "[session] could not end the session of " + session.playerId()
                                + " during shutdown",
                        failure);
            }
        }
    }

    /** The lifecycle; the plugin hands it to the Paper listeners. */
    public DefaultSessionLifecycle lifecycle() {
        return lifecycle;
    }

    /** The session cache; the lifecycle and the reconciliation need write access to it. */
    public DefaultSessionRegistry registry() {
        return registry;
    }

    /** Character access, for the lifecycle and for blocks resolving it through B01's registry. */
    public JdbcCharacterRepository characters() {
        return characters;
    }

    /** Bundled session load - account, characters and items in one database round. */
    public SessionBundleLoader bundleLoader() {
        return bundleLoader;
    }

    public SessionConfig config() {
        return config;
    }

    private SessionConfig loadConfig(ModuleContext context) {
        try {
            return context.configLoader()
                    .register(Path.of(CONFIG_FILE), SessionConfig.schema())
                    .get();
        } catch (ConfigValidationException invalid) {
            logger.log(Level.SEVERE, "[session] configuration rejected", invalid);
            throw new PersistenceStartupException(
                    "session configuration is invalid: " + invalid.getMessage(), invalid);
        }
    }
}
