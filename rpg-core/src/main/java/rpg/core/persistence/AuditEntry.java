package rpg.core.persistence;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One administrative action (FR-018).
 *
 * @param occurredAt when it happened
 * @param actor who acted; after an anonymisation this is the substitute identifier
 * @param action what was done, e.g. {@code item_granted}, {@code player_banned}
 * @param targetPlayerId the affected player, if any
 * @param details anything else worth recording
 */
public record AuditEntry(
        Instant occurredAt,
        String actor,
        String action,
        Optional<UUID> targetPlayerId,
        Map<String, Object> details) {

    public AuditEntry {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(targetPlayerId, "targetPlayerId");
        details = Map.copyOf(Objects.requireNonNull(details, "details"));
        if (actor.isBlank() || action.isBlank()) {
            throw new IllegalArgumentException("actor and action must not be blank");
        }
    }
}
