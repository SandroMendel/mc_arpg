package rpg.core.zone;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.config.ConfigHandle;
import rpg.core.config.ConfigValidationException;
import rpg.core.message.Messages;
import rpg.core.module.Module;
import rpg.core.module.ModuleContext;

/**
 * Wires B09 into the server (B01's module contract).
 *
 * <p>Lives in {@code rpg-core} for the same reason B05's module does: the zone logic itself has no
 * database. The waypoint unlocks do, and their repository is built in {@code rpg-persistence} and
 * handed in - the same arrangement B03 and B04 use for their platform pieces.
 *
 * <p><b>The reload path is the pattern B04 already established.</b>
 * {@link #applyReloadedConfig()} is called from the plugin's reload entry point right next to
 * {@code statsModule.applyReloadedConfig()}. It rebuilds the index and re-evaluates everyone present
 * <b>once</b>. A single pass is not a recurring task, which is what Constitution II forbids - and the
 * proof is that this block registers nothing with the {@code Scheduler} at all.
 *
 * <p><b>The provisional warning lives in {@link #start(ModuleContext)} and not in the reload path</b>
 * (research.md R7). FR-065b asks for it on every start; in the shared load path it would repeat on
 * every {@code /rpg reload}, which is harmless but waters down the one signal it is supposed to be.
 */
public final class ZoneModule implements Module {

    /** Stable identifier, independent of this class's name (B01/FR-001a). */
    public static final String ID = "zone";

    private static final String CONFIG_FILE = "zones.yml";

    private final Logger logger;
    private final WorldResolver worlds;
    private final Messages messages;
    private final List<Runnable> reloadListeners = new ArrayList<>();

    private ConfigHandle<ZoneConfig> configHandle;
    private volatile DefaultZones zones;

    public ZoneModule(Logger logger, WorldResolver worlds, Messages messages) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void start(ModuleContext context) {
        this.configHandle = loadConfig(context);
        ZoneConfig config = configHandle.get();
        verifyZoneNamesExist(config);
        this.zones = new DefaultZones(config);
        logger.info(
                "[zone] phase=START state=LOADED - "
                        + config.zones().size()
                        + " regions, start region '"
                        + config.startRegion().key()
                        + "'");
        warnIfProvisional(config);
    }

    /**
     * Every configured zone must have a name behind its message key (FR-003c).
     *
     * <p>This cannot live with the other key checks in the plugin: those run before any module
     * starts, and the zone keys are only known once {@code zones.yml} has been read. A zone whose
     * name shows up in game as {@code zone.dustlands.name} is a mistake that should stop the start,
     * not stand in front of a player.
     */
    private void verifyZoneNamesExist(ZoneConfig config) {
        List<String> missing = new ArrayList<>();
        for (Zone zone : config.zones()) {
            if (!messages.contains(ZoneMessageKeys.nameOf(zone.key()))) {
                missing.add(ZoneMessageKeys.nameOf(zone.key()).value());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "zones.yml declares regions without a name in messages.yml: "
                            + missing
                            + " (FR-003c). A zone name is a player text and lives only there.");
        }
    }

    /**
     * The one warning about placeholder coordinates (FR-065a, FR-065b).
     *
     * <p>A header comment did not reach anybody on B08b's T103. This is the same notice put where
     * people actually look, and removing {@code provisional} changes nothing but this line
     * (FR-065c).
     */
    private void warnIfProvisional(ZoneConfig config) {
        if (!config.provisional()) {
            return;
        }
        logger.warning(
                "[zone] phase=START state=PROVISIONAL - the zone coordinates in "
                        + CONFIG_FILE
                        + " are PLACEHOLDERS on a test world, not the hand-built map (ADR-006)."
                        + " Region borders, safe cores and crystals are positioned so they can be"
                        + " walked, nothing more. Remove 'provisional: true' once the real"
                        + " coordinates are in - that changes nothing except this warning.");
    }

    /** The current query object. Never {@code null} after {@link #start}. */
    public Zones zones() {
        return zones;
    }

    /** The current configuration - the platform listeners need the cooldown and the logout switch. */
    public ZoneConfig config() {
        return configHandle.get();
    }

    /**
     * Something that has to be told when the zones changed - the tracker re-evaluates everyone
     * present, once.
     */
    public void onReload(Runnable listener) {
        reloadListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Picks up a reloaded {@code zones.yml}. A rejected reload never reaches here (FR-014). */
    public void applyReloadedConfig() {
        if (configHandle == null) {
            return;
        }
        ZoneConfig config = configHandle.get();
        this.zones = new DefaultZones(config);
        logger.info(
                "[zone] phase=RELOAD state=APPLIED - "
                        + config.zones().size()
                        + " regions, index rebuilt");
        for (Runnable listener : reloadListeners) {
            listener.run();
        }
    }

    private ConfigHandle<ZoneConfig> loadConfig(ModuleContext context) {
        try {
            return context.configLoader()
                    .register(Path.of(CONFIG_FILE), ZoneConfigSchema.schema(worlds));
        } catch (ConfigValidationException invalid) {
            logger.log(Level.SEVERE, "[zone] configuration rejected", invalid);
            throw new IllegalStateException(
                    "zone configuration is invalid: " + invalid.getMessage(), invalid);
        }
    }
}
