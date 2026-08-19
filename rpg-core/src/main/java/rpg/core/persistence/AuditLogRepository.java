package rpg.core.persistence;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Append-only record of administrative actions (FR-018).
 *
 * <p>There is no update and no delete, by design. An audit log that can be edited proves nothing,
 * so the absence of those methods is the guarantee rather than a convention.
 */
public interface AuditLogRepository {

    /** Records an action. */
    void append(AuditEntry entry);

    /** Entries within an inclusive time range, newest first. */
    CompletableFuture<List<AuditEntry>> between(Instant from, Instant to);
}
