package rpg.core.progression;

import java.util.List;
import java.util.UUID;

/**
 * Published on every change of membership (FR-036).
 *
 * <p>A leadership handover produces <b>two</b> events: first {@code LEFT} or {@code REMOVED} for the
 * departing member, then {@code LEADER_CHANGED} for the new leader. One combined event would carry
 * two meanings in one field, and a receiver that only maintains the member list would have to tell
 * them apart anyway.
 *
 * <p>On {@code DISBANDED} the member list is empty and {@code leader} is the last leader - B13 needs
 * it to close the display of the right party.
 *
 * @param partyId which party
 * @param change what happened
 * @param affectedPlayer who it happened to
 * @param leader the leader after the change, or the last one on DISBANDED
 * @param members the members after the change
 */
public record PartyChangedEvent(
        UUID partyId,
        PartyChangedEvent.PartyChange change,
        UUID affectedPlayer,
        UUID leader,
        List<UUID> members) {

    public enum PartyChange {
        CREATED,
        JOINED,
        LEFT,
        REMOVED,
        LEADER_CHANGED,
        DISBANDED
    }

    public PartyChangedEvent {
        members = List.copyOf(members);
    }
}
