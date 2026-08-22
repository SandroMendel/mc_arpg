package rpg.core.ability;

import java.util.Collection;

/**
 * An ability id that nothing defines (FR-004, FR-006).
 *
 * <p>The message lists the ids that do exist. An operator editing eighteen abilities across two files
 * needs to know which one is meant - "unknown ability" without the list sends them through all
 * eighteen, and the most likely way to get here is a typo in a class binding.
 *
 * <p>This is also the exception that makes B07's promise good: there, an ability id travels as an
 * opaque string because B08 did not exist yet. Here both files are loaded, so the id can finally be
 * checked.
 */
public class UnknownAbilityException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial") // String is serializable
    private final String abilityId;

    public UnknownAbilityException(String abilityId, Collection<String> known) {
        super("unknown ability '" + abilityId + "' - the defined ids are: " + String.join(", ", known));
        this.abilityId = abilityId;
    }

    /** The id that could not be resolved. */
    public String abilityId() {
        return abilityId;
    }
}
