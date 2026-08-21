package rpg.core.progression;

import java.util.OptionalLong;

/**
 * How much experience a creature is worth (FR-009).
 *
 * <p>B06 answers this from its own configuration until B10 exists, then B10 replaces the provider
 * through this same interface - exactly the arrangement B05 uses for mob attribute values.
 *
 * <p>An empty result means "no entry of its own", and the configured default amount applies
 * (FR-060). It does <b>not</b> mean zero: a mob Mojang added last week should not be silently
 * worthless.
 */
public interface MobXpProvider {

    /** Experience for a mob type key, or empty to fall back to the configured default. */
    OptionalLong xpFor(String mobTypeKey);
}
