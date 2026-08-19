package rpg.core.module;

/**
 * Thrown when a module declares a dependency on an identifier no module registered.
 *
 * <p>Fail-fast at bootstrap rather than silently ignoring the declaration: a typo in a dependency id
 * would otherwise degrade into a start order that happens to work by accident (FR-001).
 */
public class UnknownModuleDependencyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String moduleId;
    private final String unknownDependencyId;

    public UnknownModuleDependencyException(String moduleId, String unknownDependencyId) {
        super(
                "module '"
                        + moduleId
                        + "' declares a dependency on '"
                        + unknownDependencyId
                        + "', but no module registered that identifier");
        this.moduleId = moduleId;
        this.unknownDependencyId = unknownDependencyId;
    }

    /** The module carrying the bad declaration. */
    public String moduleId() {
        return moduleId;
    }

    /** The identifier that could not be resolved. */
    public String unknownDependencyId() {
        return unknownDependencyId;
    }
}
