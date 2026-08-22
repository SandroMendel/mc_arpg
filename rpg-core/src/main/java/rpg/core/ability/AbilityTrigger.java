package rpg.core.ability;

/**
 * The event a passive ability hangs on (FR-046).
 *
 * <p>Passives needed triggers the moment the unique was allowed to be passive (ADR-022): "passive
 * means a permanent modifier" does not describe Second Life, which fires on death, nor Lifesteal,
 * which fires on dealing damage. So the trigger is the rule and the permanent modifier is the
 * special case {@link #ALWAYS} - not the other way round.
 *
 * <p><b>B05 is not extended for any of these.</b> Every hook already exists; which stage each one
 * uses is not interchangeable and is recorded below.
 */
public enum AbilityTrigger {

    /**
     * No event at all. Registers a modifier set once and is done; removed when the ability is lost or
     * the character changes (FR-052).
     */
    ALWAYS,

    /**
     * The holder dealt damage. Hangs on the <b>application</b> stage, where the amount that actually
     * landed is known - Lifesteal needs the mitigated number, not the raw one.
     */
    ON_DAMAGE_DEALT,

    /**
     * The holder took damage. Hangs on the <b>modifiers</b> stage, where the damage can still be
     * refused - evasion has to prevent it, and by application it has already landed.
     */
    ON_DAMAGE_TAKEN,

    /** The holder contributed to a kill. Hangs on the death event B05 publishes. */
    ON_KILL,

    /**
     * The holder would die. Hangs on the application stage, before death takes effect, so the ability
     * can put health back instead (FR-050).
     *
     * <p><b>Not reachable by {@code CombatPipeline.kill}</b>, which runs without formula and without
     * attribution and never touches an interceptor. That makes "Second Life does not save you from
     * {@code /kill}" a property of the existing path rather than a special rule (FR-051).
     */
    ON_DEATH
}
