package rpg.core.combat;

/**
 * The six stages every damage event passes through (FR-007).
 *
 * <p>Named stages rather than one long method so that later blocks attach at defined places
 * ({@link DamageInterceptor}) instead of the pipeline growing a special case per feature.
 */
public enum PipelineStage {

    /** Permission, attack window, session readiness. The first thing that can reject an event. */
    SOURCE,

    /** Base attribute times factor, or the configured environment amount. */
    RAW_DAMAGE,

    /** Additive and multiplicative interference from buffs and item effects. */
    MODIFIERS,

    /** B04's divisor model. Skipped entirely for environment damage (FR-012b). */
    DEFENCE,

    /** Health is deducted, combat state set, contribution recorded. */
    APPLICATION,

    /** Hurt animation, knockback, display aggregation, death. */
    AFTERMATH
}
