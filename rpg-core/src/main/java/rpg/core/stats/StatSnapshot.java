package rpg.core.stats;

import java.util.Objects;

/**
 * The immutable result of one recalculation (FR-020, FR-021).
 *
 * <p>The value array is private and never handed out. {@link #get(Attribute)} is the only way in.
 * That is the difference between promising immutability and having it: an exposed array is a
 * promise any caller can break by accident, and the resulting bug would look like a balancing
 * problem rather than a mutation.
 *
 * <p>A snapshot is not bound to its holder. It is meant to be taken once at the start of an action
 * - a projectile leaving a bow, an ability starting to cast - and held until that action finishes,
 * even if the holder is recalculated or removed meanwhile.
 */
public final class StatSnapshot {

    private final double[] values;
    private final long revision;

    /**
     * @param values one entry per attribute, indexed by ordinal; copied on the way in
     * @param revision strictly increasing per holder
     */
    public StatSnapshot(double[] values, long revision) {
        Objects.requireNonNull(values, "values");
        if (values.length != Attribute.count()) {
            throw new IllegalArgumentException(
                    "expected " + Attribute.count() + " values, got " + values.length);
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        this.values = values.clone();
        this.revision = revision;
    }

    /** The final value of one attribute. */
    public double get(Attribute attribute) {
        return values[Objects.requireNonNull(attribute, "attribute").ordinal()];
    }

    /** Strictly increasing per holder; lets a consumer detect change without comparing values. */
    public long revision() {
        return revision;
    }

    /** Whether this snapshot is more recent than another one of the same holder. */
    public boolean isNewerThan(StatSnapshot other) {
        return other == null || revision > other.revision;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("StatSnapshot[rev=").append(revision);
        for (Attribute attribute : Attribute.all()) {
            sb.append(", ").append(attribute.key()).append('=').append(get(attribute));
        }
        return sb.append(']').toString();
    }
}
