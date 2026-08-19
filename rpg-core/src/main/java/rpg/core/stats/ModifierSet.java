package rpg.core.stats;

import java.util.List;
import java.util.Objects;

/**
 * Everything one source contributes, as a unit (FR-007).
 *
 * <p>The unit matters more than it looks. Removing a chest plate means removing this set, not
 * hunting for the individual contributions it added - which is what makes "take the item off and
 * you are exactly where you were" a structural property rather than careful bookkeeping.
 *
 * <p>An empty set is legal and means "this source currently contributes nothing". That saves every
 * contributing block a case distinction between "no set" and "an empty one".
 *
 * @param source who contributes
 * @param modifiers what they contribute; copied, so the caller cannot change it afterwards
 */
public record ModifierSet(SourceId source, List<StatModifier> modifiers) {

    public ModifierSet {
        Objects.requireNonNull(source, "source");
        modifiers = List.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
    }

    /** A set built from individual contributions. */
    public static ModifierSet of(SourceId source, StatModifier... modifiers) {
        return new ModifierSet(source, List.of(modifiers));
    }

    /** A source that currently contributes nothing. */
    public static ModifierSet empty(SourceId source) {
        return new ModifierSet(source, List.of());
    }

    public boolean isEmpty() {
        return modifiers.isEmpty();
    }
}
