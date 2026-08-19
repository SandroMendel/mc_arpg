package rpg.core.persistence;

import java.util.Objects;
import java.util.UUID;

/**
 * The substitute that replaces a player's identifier after anonymisation (FR-017a).
 *
 * <p>Generated at random, never derived from the original identifier - no hash, no encryption, no
 * encoding. A derived value would leave the personal reference reconstructible by comparison, which
 * is exactly what FR-017b rules out: anyone holding the original id could hash it the same way and
 * find the rows again.
 *
 * <p>For the same reason the mapping "old id to substitute" is stored nowhere. That is what makes
 * the operation irreversible rather than merely obscured.
 *
 * @param value a fresh random identifier
 */
public record AnonymizedId(UUID value) {

    public AnonymizedId {
        Objects.requireNonNull(value, "value");
    }

    /** A new substitute, unrelated to any existing identifier. */
    public static AnonymizedId random() {
        return new AnonymizedId(UUID.randomUUID());
    }
}
