package rpg.core.performance;

/** A short-lived synchronous measurement scope. Closing it records one duration. */
public interface MeasurementScope extends AutoCloseable {

    @Override
    void close();
}
