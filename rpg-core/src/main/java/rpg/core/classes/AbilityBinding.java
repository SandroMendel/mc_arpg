package rpg.core.classes;

import java.util.Objects;

/**
 * One entry of a class ability loadout (FR-041).
 *
 * <p><b>B07 names, B08 resolves.</b> The id travels as a plain string and is never looked up here.
 * What an ability does, which hotbar slot it takes and what it costs is B08 (FR-044, Workflow rule
 * 5). Checking that an id exists would have coupled this block to one that does not exist yet.
 *
 * @param abilityId opaque identifier, resolved by B08
 * @param kind active or passive
 * @param unique whether this is the Unique Class Ability - at most one per class, and it counts as
 *     one of the four actives
 * @param unlockLevel the level from which the ability is available; derived, never stored (FR-043)
 */
public record AbilityBinding(String abilityId, AbilityKind kind, boolean unique, int unlockLevel) {

    public AbilityBinding {
        Objects.requireNonNull(abilityId, "abilityId");
        Objects.requireNonNull(kind, "kind");
        if (abilityId.isBlank()) {
            throw new IllegalArgumentException("abilityId must not be blank");
        }
        if (unlockLevel < 1) {
            throw new IllegalArgumentException(
                    "unlock-level of " + abilityId + " must be at least 1, but was " + unlockLevel);
        }
        // A passive unique would contradict the three loadouts fixed in B08, and more importantly it
        // would make "four actives including the unique" uncountable.
        if (unique && kind != AbilityKind.ACTIVE) {
            throw new IllegalArgumentException(
                    "the unique class ability " + abilityId + " must be ACTIVE, but was " + kind);
        }
    }

    public boolean isActive() {
        return kind == AbilityKind.ACTIVE;
    }
}
