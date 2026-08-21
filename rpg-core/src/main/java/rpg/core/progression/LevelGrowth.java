package rpg.core.progression;

import java.util.Objects;

import rpg.core.stats.Attribute;
import rpg.core.stats.BaseStatSink;

/**
 * What one level adds to each of the eight attributes (FR-022a).
 *
 * <p><b>All eight are configurable, zero included.</b> Which attributes grow is content, not code
 * (Principle V) - so B07 can tell a Rogue apart from a Warrior without B06 changing. The shipped
 * default grows health, mana, defence and both damage values and leaves attack speed, movement speed
 * and ability cooldown at zero (FR-022b): movement speed over sixty levels makes moving unplayable,
 * and attack speed runs into vanilla invulnerability, where extra speed simply evaporates.
 *
 * <p><b>Caps are not checked here.</b> {@code StatCalculator} already clamps the result against
 * {@code min} and {@code max} of each attribute, so growth runs into the cap from B04 rather than
 * over it (FR-022c). A second check in B06 would be a second truth about the same limit.
 */
public final class LevelGrowth {

    private final double[] perLevel;

    private LevelGrowth(double[] perLevel) {
        this.perLevel = perLevel;
    }

    /**
     * @param perLevel one entry per attribute in {@link Attribute} declaration order
     */
    public static LevelGrowth of(double[] perLevel) {
        Objects.requireNonNull(perLevel, "perLevel");
        if (perLevel.length != Attribute.count()) {
            throw new IllegalArgumentException(
                    "progression.level-growth: expected "
                            + Attribute.count()
                            + " values, but got "
                            + perLevel.length);
        }
        for (int i = 0; i < perLevel.length; i++) {
            double value = perLevel[i];
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException(
                        "progression.level-growth."
                                + Attribute.all()[i].key()
                                + " must be finite and not negative, but was "
                                + value);
            }
        }
        return new LevelGrowth(perLevel.clone());
    }

    /** Growth for one attribute per level; zero is a legitimate answer. */
    public double perLevel(Attribute attribute) {
        return perLevel[attribute.ordinal()];
    }

    /**
     * Contributes the accumulated growth of {@code level} to the base values.
     *
     * <p><b>A multiplication, not a loop over levels.</b> Level 1 contributes nothing - the level-1
     * value <em>is</em> {@code definition.base()} from B04. Summing across fifty-nine levels on every
     * recalculation would be arithmetic without a different answer.
     */
    public void contributeTo(int level, BaseStatSink sink) {
        int levelsGained = level - 1;
        if (levelsGained <= 0) {
            return;
        }
        Attribute[] attributes = Attribute.all();
        for (int i = 0; i < attributes.length; i++) {
            double growth = perLevel[i];
            if (growth != 0.0) {
                sink.addBase(attributes[i], growth * levelsGained);
            }
        }
    }

}
