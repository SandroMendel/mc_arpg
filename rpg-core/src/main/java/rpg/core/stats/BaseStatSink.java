package rpg.core.stats;

/**
 * Where a {@link BaseStatContributor} puts its numbers (FR-039).
 *
 * <p>A sink instead of a return value so a contributor can add to several attributes without
 * allocating a collection per holder per recalculation.
 */
public interface BaseStatSink {

    /**
     * Adds to the base value of one attribute.
     *
     * @param attribute which attribute
     * @param amount how much to add; must be finite
     */
    void addBase(Attribute attribute, double amount);
}
