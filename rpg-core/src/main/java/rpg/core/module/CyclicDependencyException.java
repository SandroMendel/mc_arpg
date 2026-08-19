package rpg.core.module;

import java.util.List;

/**
 * Thrown when the declared module dependency graph contains a cycle.
 *
 * <p>Per FR-011 the bootstrap aborts with a message naming the modules involved, instead of looping
 * forever or falling back to an arbitrary order.
 */
public class CyclicDependencyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial") // List.copyOf returns a serializable implementation in practice
    private final List<String> involvedModuleIds;

    public CyclicDependencyException(List<String> involvedModuleIds) {
        super("cyclic module dependency detected between: " + String.join(", ", involvedModuleIds));
        this.involvedModuleIds = List.copyOf(involvedModuleIds);
    }

    /** The identifiers of the modules that could not be ordered, sorted for determinism. */
    public List<String> involvedModuleIds() {
        return involvedModuleIds;
    }
}
