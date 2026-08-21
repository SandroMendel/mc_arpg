package rpg.core.progression;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Experience amounts out of {@code progression.yml}, until B10 owns mob definitions (FR-009).
 *
 * <p>This is the <b>default</b> provider, installed at construction. When B10 replaces it through
 * {@link Progression#setMobXpProvider}, this table stops being consulted - which is correct, because
 * from then on B10 owns what a creature is.
 *
 * <p>A type without an entry of its own produces exactly <b>one</b> warning, not one per kill
 * (FR-060). At 800 active mobs a warning per kill would bury the log within minutes, and the thing
 * an operator needs to know - "this type has no entry" - is said once.
 */
public final class ConfigMobXpProvider implements MobXpProvider {

    private final ProgressionConfig config;
    private final Logger logger;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public ConfigMobXpProvider(ProgressionConfig config, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public OptionalLong xpFor(String mobTypeKey) {
        OptionalLong configured = config.mobXpFor(mobTypeKey);
        if (configured.isEmpty() && warned.add(mobTypeKey)) {
            logger.info(
                    "[progression] no experience configured for mob type "
                            + mobTypeKey
                            + " - using the default of "
                            + config.mobXpDefault()
                            + ". Add it under mob-xp.by-type to give it its own value.");
        }
        return configured;
    }

    /** How many types have been warned about. For the test that the warning happens once. */
    public int warnedTypeCount() {
        return warned.size();
    }
}
