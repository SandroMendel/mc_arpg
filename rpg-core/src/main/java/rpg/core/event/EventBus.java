package rpg.core.event;

/**
 * Internal publish/subscribe mechanism between modules; publisher and subscriber never need to know
 * each other (FR-006).
 *
 * <p>Dispatch is synchronous and happens in the caller's context. The bus deliberately owns no
 * thread pool: whether an event is raised on the tick or off it is decided by the publisher through
 * the {@link rpg.core.scheduler.Scheduler} abstraction, so there is exactly one source of
 * concurrency in the system (research.md, "Event-Bus-Dispatchstrategie").
 *
 * <p>See {@code contracts/event-bus.md} for the behavioural contract.
 */
public interface EventBus {

    /**
     * Delivers {@code event} to every subscriber registered for its exact runtime type.
     *
     * <p>Never fails because of a subscriber: if a handler throws, the error is logged in isolation
     * together with event type and subscriber, and delivery continues with the remaining subscribers
     * (FR-006a).
     */
    <E> void publish(E event);

    /**
     * Registers {@code handler} for events of exactly {@code eventType}.
     *
     * <p>The notification order among several subscribers of the same type is not part of the
     * contract and must not be relied upon.
     *
     * @return a handle whose {@link Subscription#close()} removes the handler again
     */
    <E> Subscription subscribe(Class<E> eventType, EventHandler<E> handler);
}
