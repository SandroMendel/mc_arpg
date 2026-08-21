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

    private ClassSelectionResult(PlayerCharacter character, ClassSelectionRejection rejection) {
        this.character = character;
        this.rejection = rejection;
    }

    public static ClassSelectionResult accepted(PlayerCharacter character) {
        return new ClassSelectionResult(Objects.requireNonNull(character, "character"), null);
    }

    public static ClassSelectionResult rejected(ClassSelectionRejection rejection) {
        return new ClassSelectionResult(null, Objects.requireNonNull(rejection, "rejection"));
    }

    public boolean accepted() {
        return character != null;
    }

    /** The freshly created character, present exactly when {@link #accepted()}. */
    public Optional<PlayerCharacter> character() {
        return Optional.ofNullable(character);
    }

    public Optional<ClassSelectionRejection> rejection() {
        return Optional.ofNullable(rejection);
    }

    @Override
    public String toString() {
        return accepted()
                ? "ClassSelectionResult[accepted " + character.characterClass() + "]"
                : "ClassSelectionResult[rejected " + rejection + "]";
    }
}
