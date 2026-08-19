package rpg.core.stats;

/**
 * Supplies base value contributions that do not come from configuration (FR-039).
 *
 * <p>This is how B06 (level) and B07 (class) reach the calculation without B04 knowing anything
 * about levels or classes. Contributors are registered at startup and asked on every
 * recalculation.
 *
 * <p>Base contributions are not modifiers. They move the base the percentage factor multiplies,
 * and they move the band that attack and movement speed are limited to. A level-up therefore
 * raises the ceiling rather than eating into the room equipment has.
 *
 * <p>An exception thrown from {@link #contribute} is caught, logged with this contributor's id and
 * confined to the affected holder; the calculation continues with the remaining contributors
 * (FR-038).
 */
public interface BaseStatContributor {

    /** Stable identifier, used in log messages when this contributor misbehaves. */
    String id();

    /** Called on every recalculation of {@code holder}. */
    void contribute(StatHolderView holder, BaseStatSink sink);
}
