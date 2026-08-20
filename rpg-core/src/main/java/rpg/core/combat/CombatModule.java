package rpg.core.combat;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.config.ConfigHandle;
import rpg.core.config.ConfigValidationException;
import rpg.core.module.Module;
import rpg.core.module.ModuleContext;
import rpg.core.session.SessionRegistry;
import rpg.core.stats.StatEngine;

/**
 * Wires B05 into the server (B01's module contract).
 *
 * <p><b>Lives in {@code rpg-core}, unlike B02, B03 and B04.</b> Those put their modules in
 * {@code rpg-persistence} because they had to build a repository. B05 has no database at all;
 * putting its module there would advertise a dependency that does not exist, and the next person
 * reading the module graph would believe it.
 *
 * <p>The Paper side - damage listeners, mob equipping, feedback - is constructed by the plugin and
 * handed in, exactly as B03 and B04 do with theirs.
 */
public final class CombatModule implements Module {

    /** Stable identifier, independent of this class's name (B01/FR-001a). */
    public static final String ID = "combat";

    private static final String CONFIG_FILE = "combat.yml";

    /** B04's module id. B05 needs the stat engine, and nothing else from any other block. */
    private static final String STATS_MODULE_ID = "stats";

    private final Logger logger;
    private final Clock clock;
    private final SessionRegistry sessions;

    private DefaultCombatPipeline pipeline;
    private ConfigHandle<CombatConfig> configHandle;

    public CombatModule(SessionRegistry sessions, Logger logger, Clock clock) {
        this.sessions = sessions;
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> dependencies() {
        return List.of(STATS_MODULE_ID);
    }

    @Override
    public void start(ModuleContext context) throws Exception {
        configHandle = loadConfig(context);
        CombatConfig config = configHandle.get();

        StatEngine stats = context.registry().getService(StatEngine.class);

        pipeline =
                new DefaultCombatPipeline(
                        config, stats, context.eventBus(), sessions, clock, logger);

        context.registry().registerService(ID, CombatPipeline.class, pipeline);

        logger.info(
                "[combat] pipeline ready - combat lasts "
                        + config.combatTimeout().toSeconds()
                        + "s, "
                        + config.maxAttackers()
                        + " attackers tracked per target, no scheduled work at idle");
    }

    @Override
    public void stop() throws Exception {
        pipeline = null;
    }

    /** The pipeline, for the plugin's wiring. Other blocks use the registered interface. */
    public DefaultCombatPipeline pipeline() {
        return pipeline;
    }

    /** The current configuration - the platform listeners need the environment amounts. */
    public CombatConfig config() {
        return configHandle == null ? CombatConfig.defaults() : configHandle.get();
    }

    /** Picks up a reloaded {@code combat.yml}. A rejected reload never reaches here. */
    public void applyReloadedConfig() {
        if (pipeline != null && configHandle != null) {
            pipeline.reload(configHandle.get());
            logger.info("[combat] configuration reloaded");
        }
    }

    private ConfigHandle<CombatConfig> loadConfig(ModuleContext context) {
        try {
            return context.configLoader().register(Path.of(CONFIG_FILE), CombatConfigSchema.schema());
        } catch (ConfigValidationException invalid) {
            logger.log(Level.SEVERE, "[combat] configuration rejected", invalid);
            throw new IllegalStateException(
                    "combat configuration is invalid: " + invalid.getMessage(), invalid);
        }
    }
}
