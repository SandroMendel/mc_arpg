package rpg.core.module;

/**
 * Thrown when two modules register the same identifier.
 *
 * <p>Per FR-001a the bootstrap aborts instead of silently overwriting one of the two modules, so a
 * copy-pasted identifier can never make a module disappear at runtime.
 */
public class DuplicateModuleIdException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String moduleId;

    public DuplicateModuleIdException(String moduleId) {
        super("duplicate module id '" + moduleId + "': it is already registered");
        this.moduleId = moduleId;
    }

    /**
     * Variant naming both implementations. Used by the bootstrap, which - unlike the registry - knows
     * the classes behind the identifiers.
     */
    public DuplicateModuleIdException(String moduleId, String alreadyRegisteredBy, String rejected) {
        super(
                "duplicate module id '"
                        + moduleId
                        + "': already registered by "
                        + alreadyRegisteredBy
                        + ", rejected registration from "
                        + rejected);
        this.moduleId = moduleId;
    }

    /** The identifier that was registered twice. */
    public String moduleId() {
        return moduleId;
    }
}
