package rpg.core.currency;

import java.util.OptionalLong;

/**
 * What a creature drops when it dies (FR-022).
 *
 * <p>B08b answers this from its own configuration until B10 exists, then B10 replaces the provider
 * through this same interface (FR-032) - exactly the arrangement B06 uses for experience and B05 for
 * mob attribute values.
 *
 * <p><b>An empty result means "no entry of its own"</b>, and the configured default applies. It does
 * <b>not</b> mean zero: a mob Mojang added last week should not be silently worthless. An explicit
 * zero in the configuration does mean zero - that is a choice, and the two have to stay
 * distinguishable.
 */
public interface MobCoinProvider {

    /** Coins for a mob type key, or empty to fall back to the configured default. */
    OptionalLong coinsFor(String mobTypeKey);
}
