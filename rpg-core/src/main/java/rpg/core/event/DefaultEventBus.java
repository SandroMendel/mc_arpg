package rpg.core.event;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Synchronous in-process {@link EventBus}.
 *
 * <p>Dispatch happens directly in the publisher's context and the bus owns no threads of its own: a
 * second, hidden source of concurrency next to the {@link rpg.core.scheduler.Scheduler} abstraction
 * is exactly what Constitution I is designed to prevent (research.md, "Event-Bus-Dispatchstrategie").
 *
 * <p>Handlers are held in a {@link CopyOnWriteArrayList}, so a handler may subscribe or unsubscribe
 * while an event is being delivered without a {@code ConcurrentModificationException}. Publishing
 * iterates a snapshot, which keeps the read path allocation-free (Constitution II).
 */
public final class DefaultEventBus implements EventBus {

    private final Map<Class<?>, CopyOnWriteArrayList<EventHandler<?>>> handlers =
            new ConcurrentHashMap<>();
    private final Logger logger;

    public DefaultEventBus(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public <E> void publish(E event) {
        Objects.requireNonNull(event, "event");
        List<EventHandler<?>> subscribers = handlers.get(event.getClass());
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        for (EventHandler<?> subscriber : subscribers) {
            @SuppressWarnings("unchecked") // only ever registered under its own event type
            EventHandler<E> typed = (EventHandler<E>) subscriber;
            try {
                typed.handle(event);
            } catch (RuntimeException failure) {
                // FR-006a: one bad subscriber must not deprive the others of the event.
                logFailure(event, subscriber, failure);
            }
        }
    }

    @Override
    public <E> Subscription subscribe(Class<E> eventType, EventHandler<E> handler) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(handler, "handler");
        handlers.computeIfAbsent(eventType, type -> new CopyOnWriteArrayList<>()).add(handler);
        return new HandlerSubscription(eventType, handler);
    }

    /** Number of handlers currently registered for {@code eventType}; for diagnostics and tests. */
    public int subscriberCount(Class<?> eventType) {
        CopyOnWriteArrayList<EventHandler<?>> subscribers = handlers.get(eventType);
        return subscribers == null ? 0 : subscribers.size();
    }

    private void logFailure(Object event, EventHandler<?> subscriber, RuntimeException failure) {
        String publisher =
                event instanceof Event typed ? typed.publishedByModuleId() : "<unknown module>";
        logger.log(
                Level.SEVERE,
                "[event] type="
                        + event.getClass().getName()
                        + " publishedBy="
                        + publisher
                        + " subscriber="
                        + subscriber.getClass().getName()
                        + " state=CONTAINED - delivery continues to the remaining subscribers",
                failure);
    }

    /** Removes exactly the handler it was created for; idempotent. */
    private final class HandlerSubscription implements Subscription {

        private final Class<?> eventType;
        private final EventHandler<?> handler;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        HandlerSubscription(Class<?> eventType, EventHandler<?> handler) {
            this.eventType = eventType;
            this.handler = handler;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            CopyOnWriteArrayList<EventHandler<?>> subscribers = handlers.get(eventType);
            if (subscribers != null) {
                subscribers.remove(handler);
            }
        }
    }
}
