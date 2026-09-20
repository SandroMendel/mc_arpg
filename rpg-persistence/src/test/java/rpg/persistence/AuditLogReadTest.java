package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.AuditEntry;
import rpg.core.persistence.FlushReason;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/** T102 / FR-036 — the audit read path is exercised against real PostgreSQL. */
class AuditLogReadTest {

    private static final Instant LOWER = Instant.parse("2026-09-11T10:00:00Z");
    private static final Instant MIDDLE = Instant.parse("2026-09-11T11:00:00Z");
    private static final Instant UPPER = Instant.parse("2026-09-11T12:00:00Z");

    private PersistenceHarness harness;

    @BeforeEach
    void setUp() {
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    void betweenIsInclusiveNewestFirstAndEmptyRangesAreEmpty() throws Exception {
        UUID target = UUID.fromString("22222222-2222-2222-2222-222222222222");
        harness.auditLog.append(entry(LOWER, "lower", target));
        harness.auditLog.append(entry(MIDDLE, "middle", target));
        harness.auditLog.append(entry(UPPER, "upper", target));
        harness.auditLog.append(entry(LOWER.minusSeconds(1), "outside-before", target));
        harness.auditLog.append(entry(UPPER.plusSeconds(1), "outside-after", target));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        List<AuditEntry> entries = harness.auditLog.between(LOWER, UPPER).get();

        assertThat(entries).extracting(AuditEntry::action)
                .containsExactly("upper", "middle", "lower");
        assertThat(entries).allSatisfy(read -> assertThat(read.targetPlayerId()).contains(target));
        assertThat(harness.auditLog.between(UPPER.plusSeconds(2), UPPER.plusSeconds(3)).get())
                .isEmpty();
    }

    private static AuditEntry entry(Instant occurredAt, String action, UUID target) {
        return new AuditEntry(
                occurredAt,
                "admin",
                action,
                Optional.of(target),
                Map.of("source", "test"));
    }
}
