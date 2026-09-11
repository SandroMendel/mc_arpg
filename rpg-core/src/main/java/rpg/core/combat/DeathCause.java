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
    ADMIN,

    /**
     * Left the server while in combat (ADR-030, B09/FR-038).
     *
     * <p><b>Added by a later block, and that is why it needed an ADR.</b> The enum is B05's and it is
     * shipped; extending it from B09 is the vocabulary change ADR-027 made ADR-bound. It fits the
     * reason this enum is coarse in the first place - B06, B11 and B12 need the cause to tell cases
     * apart, and "died" and "ran away" are exactly two cases somebody will want to count separately.
     *
     * <p><b>Not {@link #COMBAT}, deliberately.</b> Folding it in would make the statistics untrue and
     * the rule untraceable: nobody looking at a death count could tell how often the rule fires, and
     * the rule is the kind that gets argued about.
     */
    LOGOUT
}
