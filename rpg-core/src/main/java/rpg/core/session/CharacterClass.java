package rpg.core.session;

/**
 * The three playable classes (B07).
 *
 * <p>An account holds at most one character per class, which is what bounds it to three
 * (FR-017). The rule lives in the database key, not here - this enum only names the values.
 */
public enum CharacterClass {
    WARRIOR,
    MAGE,
    ROGUE
}
