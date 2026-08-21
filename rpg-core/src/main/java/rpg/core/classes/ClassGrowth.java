package rpg.core.classes;

import java.util.Objects;

import rpg.core.stats.Attribute;
import rpg.core.stats.BaseStatSink;

/**
 * The per-level increase of a class over the eight attributes (FR-002).
 *
 * <p>This <b>replaces</b> the class-neutral {@code LevelGrowth} from B06 for a character with a
 * class; it does not add to it. B06 provided for exactly that in its FR-022. Adding would have
 * doubled the sum.
 *
 * <p>Zero is a legal rate and still a required field - the three percent attributes are zero by
 * design, because they come entirely from the ladder. A missing field would be indistinguishable from
 * a deliberate zero, which is the same argument B06 made for {@code level-growth}.
 */
public final class ClassGrowth {

    private final double[] perLevel;

    private ClassGrowth(double[] perLevel) {
        this.perLevel = perLevel;
    }

    public static ClassGrowth of(double[] perLevel) {
        Objects.requireNonNull(perLevel, "perLevel");
        if (perLevel.length != Attribute.count()) {
            throw new IllegalArgumentException(
                    "growth needs exactly " + Attribute.count() + " values, but got " + perLevel.length);
        }
        for (Attribute attribute : Attribute.all()) {
            double rate = perLevel[attribute.ordinal()];
            if (!Double.isFinite(rate)) {
                throw new IllegalArgumentException(
                        "growth." + attribute.key() + " must be finite, but was " + rate);
            }
            if (rate < 0.0) {
                throw new IllegalArgumentException(
                        "growth."
                                + attribute.key()
                                + " must not be negative, but was "
                                + rate
                                + " - a level-up never takes anything away");
            }
        }
        return new ClassGrowth(perLevel.clone());
    }

    public double perLevel(Attribute attribute) {
        return perLevel[attribute.ordinal()];
    }

    /**
     * Adds the growth accumulated up to {@code level}. Level 1 contributes nothing - the base values
     * already describe a level-1 character.
     */
    public void contributeTo(int level, BaseStatSink sink) {
        if (level <= 1) {
            return;
        }
        int steps = level - 1;
        for (Attribute attribute : Attribute.all()) {
            double rate = perLevel[attribute.ordinal()];
            if (rate != 0.0) {
                sink.addBase(attribute, rate * steps);
            }
        }
    }
}
