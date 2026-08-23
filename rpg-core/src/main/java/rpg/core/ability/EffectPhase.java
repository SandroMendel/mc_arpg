package rpg.core.ability;

/**
 * <b>When</b> an effect happens - not what it does and not to whom (FR-016c, FR-045d).
 *
 * <p>Almost everything happens at the moment of the cast, and that stayed the default so that no
 * existing definition has to say anything. The two other values exist because two abilities describe
 * something the engine had no way to express:
 *
 * <ul>
 *   <li>The rogue's clone <b>goes off when it runs out</b> - ten seconds after the cast, where the
 *       clone stands, which is not where the rogue is by then. That is the whole point of it.
 *   <li>The warrior's leap <b>lands</b> - and what a leap does, it does at the landing site. Applied
 *       at the cast it hit whatever stood in front of him before he jumped, and pushed it away from
 *       the spot he took off from.
 * </ul>
 *
 * <p><b>Why a phase and not a delay in milliseconds.</b> A delay would have needed a number that
 * happens to match the summon's lifetime, in a second place, kept in step by hand. And no number at
 * all describes a landing: how long a jump takes depends on where it goes.
 */
public enum EffectPhase {

    /** At the trigger, on whatever the targeting found. The default and the overwhelming majority. */
    CAST,

    /** When a summoned creature's time is up, around the place it stood. */
    SUMMON_END,

    /** When the caster touches the ground again after being thrown, around where they came down. */
    LANDING;

    /** Whether this one waits for something rather than happening straight away. */
    public boolean deferred() {
        return this != CAST;
    }
}
