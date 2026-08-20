package rpg.core.combat;

/**
 * Attaches to one stage of the pipeline (FR-008).
 *
 * <p>This is how B08 (buffs) and B11 (item effects) influence damage without the pipeline growing a
 * special case per feature. Registered at startup, not during a fight.
 *
 * <p>An exception thrown from {@link #intercept} is caught, logged with this interceptor's id and
 * confined to the one event; the pipeline continues (FR-010) - the same barrier B01 uses for modules
 * and B04 for base stat contributors.
 *
 * <p>The {@link DamageView} handed in is valid <b>only for the duration of the call</b>. Read what
 * you need while you are called.
 */
public interface DamageInterceptor {

    /** Stable identifier, used in log messages when this interceptor misbehaves. */
    String id();

    /** Which stage this attaches to. */
    PipelineStage stage();

    /** Called for every damage event that reaches {@link #stage()} without being cancelled. */
    void intercept(DamageView damage);
}
