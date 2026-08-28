package rpg.core.currency;

import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * The provider B08b ships until B10 exists (FR-032).
 *
 * <p>Reads {@code drops.by-type} from {@code currency.yml}. Same shape as
 * {@code ConfigMobXpProvider} in B06, down to the warning: a creature without an entry produces
 * <b>one</b> log line per type, never one per kill. At 800 mobs the second would be a log nobody can
 * read and a cost nobody budgeted for.
 */
public final class ConfigMobCoinProvider implements MobCoinProvider {

    private final CurrencyConfig config;
    private final Logger logger;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public ConfigMobCoinProvider(CurrencyConfig config, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public OptionalLong coinsFor(String mobTypeKey) {
        if (mobTypeKey == null || mobTypeKey.isBlank()) {
            return OptionalLong.empty();
        }
        String key = mobTypeKey.toUpperCase(Locale.ROOT);
        Long own = config.dropsByType().get(key);
        if (own != null) {
            return OptionalLong.of(own);
        }
        if (warned.add(key)) {
            logger.fine(
                    "[currency] no coin entry for "
                            + key
                            + " - using the default of "
                            + config.defaultDrop());
        }
        return OptionalLong.empty();
    }
}
