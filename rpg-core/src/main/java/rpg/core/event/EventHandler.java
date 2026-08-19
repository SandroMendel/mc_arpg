package rpg.core.event;

/**
 * Receives events of a single type from the {@link EventBus}.
 *
 * @param <E> the event type this handler was subscribed for
 */
@FunctionalInterface
public interface EventHandler<E> {

    /**
     * Handles one event.
     *
     * <p>A {@link RuntimeException} thrown here is caught, logged in isolation and does not stop
     * delivery to the remaining subscribers of the same event (FR-006a).
     */
    void handle(E event);
}
