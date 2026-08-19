package rpg.core.event;

/**
 * Base type for messages published on the internal {@link EventBus}.
 *
 * <p>The bus itself accepts any type (see {@code contracts/event-bus.md}); implementing this
 * interface adds the diagnostic metadata the data model requires: the concrete implementing type is
 * the event <em>type</em> subscribers register for, its own components are the <em>payload</em>, and
 * {@link #publishedByModuleId()} identifies the publisher for log output when a subscriber fails
 * (FR-006a).
 *
 * <p>Implementations should be records or otherwise immutable - a payload must not change after
 * publication, since every subscriber sees the same instance.
 */
public interface Event {

    /**
     * Identifier of the module that published this event.
     *
     * <p>Used purely for diagnostics; subscribers must not branch on it, otherwise publisher and
     * subscriber become coupled again (FR-006).
     */
    String publishedByModuleId();
}
