package rpg.core.session;

import java.util.UUID;

/**
 * Thrown when an account already has a character of the requested class (FR-020).
 *
 * <p>Raised from the database's unique key rather than from a preceding read: a read-then-write
 * check would leave a window in which two concurrent creations both pass.
 */
public class CharacterClassTakenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial")
    private final UUID playerId;

    private final CharacterClass characterClass;

    public CharacterClassTakenException(UUID playerId, CharacterClass characterClass) {
        super("account " + playerId + " already has a " + characterClass + " character");
        this.playerId = playerId;
        this.characterClass = characterClass;
    }

    public UUID playerId() {
        return playerId;
    }

    public CharacterClass characterClass() {
        return characterClass;
    }
}
