package rpg.core.performance;

/** Raised when a source ID is registered more than once. */
public final class DuplicateSubsystemException extends IllegalArgumentException {

    public DuplicateSubsystemException(SubsystemId id) {
        super("performance subsystem already registered: " + id);
    }
}
