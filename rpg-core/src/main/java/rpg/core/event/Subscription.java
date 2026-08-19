package rpg.core.event;

/**
 * Handle for one registered {@link EventHandler}.
 *
 * <p>Closing it reliably removes the handler. This matters on the shutdown path: a stopped module
 * must not keep receiving events (see {@code contracts/event-bus.md}).
 */
public interface Subscription extends AutoCloseable {

    /** Deregisters the handler. Repeated calls are a no-op. */
    @Override
    void close();
}
