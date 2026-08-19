package rpg.core.stats;

import java.util.Objects;

/**
 * Identifies one set of contributions so it can be removed again (FR-007).
 *
 * <p>The key belongs to the contributing block, never to B04: {@code (EQUIPMENT, "slot:CHEST")},
 * {@code (BUFF, "berserker:a1f3...")}, {@code (LEVEL, "level")}. B04 only needs it to be stable and
 * comparable.
 *
 * <p>Comparable so a holder can keep its sources in a sorted map. That gives a deterministic
 * summation order for free, without sorting on every recalculation - see {@link SourceKind}.
 *
 * @param kind which category this source belongs to
 * @param key identifier within that category; must not be blank
 */
public record SourceId(SourceKind kind, String key) implements Comparable<SourceId> {

    public SourceId {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("source key must not be blank (kind " + kind + ")");
        }
    }

    public static SourceId of(SourceKind kind, String key) {
        return new SourceId(kind, key);
    }

    @Override
    public int compareTo(SourceId other) {
        int byKind = Integer.compare(kind.ordinal(), other.kind.ordinal());
        return byKind != 0 ? byKind : key.compareTo(other.key);
    }

    @Override
    public String toString() {
        return kind + ":" + key;
    }
}
