package rpg.core.combat;

/**
 * Why something died.
 *
 * <p>Deliberately coarser than {@link EnvironmentSource}: B06, B11 and B12 need the cause to tell
 * cases apart, not to compute anything. A finer breakdown would be detail nobody consumes.
 */
public enum DeathCause {

    /** Killed by a player or a mob. */
    COMBAT,

    /** Killed by the world - fall, fire, lava and the rest. */
    ENVIRONMENT,

    /** Fell into the void. Always lethal, whatever the health value. */
    VOID,

    /** {@code /kill}. An administration tool has to stay reliable. */
    ADMIN
}
