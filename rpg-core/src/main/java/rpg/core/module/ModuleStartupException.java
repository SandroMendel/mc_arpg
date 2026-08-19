package rpg.core.module;

/**
 * Thrown when a module fails during {@link Module#start(ModuleContext)}.
 *
 * <p>Aborts the bootstrap: per FR-013 a failed module must keep the system out of an operational
 * state rather than letting the server come up half-initialised and misbehave silently later.
 */
public class ModuleStartupException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String moduleId;

    public ModuleStartupException(String moduleId, Throwable cause) {
        super("module '" + moduleId + "' failed to start: " + cause, cause);
        this.moduleId = moduleId;
    }

    /** The module that failed. */
    public String moduleId() {
        return moduleId;
    }
}
