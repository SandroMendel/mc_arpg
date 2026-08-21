package rpg.core.classes;

import java.util.Objects;
import java.util.Optional;

import rpg.core.session.PlayerCharacter;

/**
 * The outcome of a class selection: either a character now exists, or a named reason why not.
 *
 * <p>Never an exception for the ordinary refusals. Two players of one account choosing the same class
 * at the same moment is a race the design expects, not a fault - and Constitution VI forbids letting a
 * gameplay exception put a player into an inconsistent state.
 */
public final class ClassSelectionResult {

    private final PlayerCharacter character;
    private final ClassSelectionRejection rejection;
    private final boolean created;

    private ClassSelectionResult(
            PlayerCharacter character, ClassSelectionRejection rejection, boolean created) {
        this.character = character;
        this.rejection = rejection;
        this.created = created;
    }

    /** A character that did not exist a moment ago. */
    public static ClassSelectionResult created(PlayerCharacter character) {
        return new ClassSelectionResult(Objects.requireNonNull(character, "character"), null, true);
    }

    /** A character the account already had, taken into play (US1.4). */
    public static ClassSelectionResult resumed(PlayerCharacter character) {
        return new ClassSelectionResult(Objects.requireNonNull(character, "character"), null, false);
    }

    public static ClassSelectionResult rejected(ClassSelectionRejection rejection) {
        return new ClassSelectionResult(null, Objects.requireNonNull(rejection, "rejection"), false);
    }

    public boolean accepted() {
        return character != null;
    }

    /**
     * Whether the character was created by this choice rather than resumed.
     *
     * <p>Matters to the caller, not to this block: a character starting its very first session gets a
     * clean inventory before its equipment is handed over, and a returning one keeps what it carried.
     */
    public boolean created() {
        return created;
    }

    /** The chosen character, present exactly when {@link #accepted()}. */
    public Optional<PlayerCharacter> character() {
        return Optional.ofNullable(character);
    }

    public Optional<ClassSelectionRejection> rejection() {
        return Optional.ofNullable(rejection);
    }

    @Override
    public String toString() {
        return accepted()
                ? "ClassSelectionResult[" + (created ? "created " : "resumed ") + character.characterClass() + "]"
                : "ClassSelectionResult[rejected " + rejection + "]";
    }
}
