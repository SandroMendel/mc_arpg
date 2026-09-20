package rpg.core.performance;

/** Raised when a sample is reported for a source that was never registered. */
public final class UnknownSubsystemException extends IllegalArgumentException {

    public UnknownSubsystemException(SubsystemId id) {
        super("performance subsystem is not registered: " + id);
    }
}
