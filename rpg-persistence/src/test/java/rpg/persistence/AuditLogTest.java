package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.AuditEntry;
import rpg.core.persistence.AuditLogRepository;
import rpg.core.persistence.FlushReason;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * T079 / FR-018: administrative actions are recorded, and the record only grows.
 *
 * <p>The append-only property is asserted at the interface level rather than in the database: the
 * repository exposes no update and no delete at all, so there is nothing a caller could reach for.
 */
class AuditLogTest {

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
    void entriesAreWrittenAndReadBack() throws Exception {
        UUID target = UUID.randomUUID();
        harness.auditLog.append(
                new AuditEntry(
                        Instant.now(), "admin", "item_granted", Optional.of(target),
                        Map.of("template", "sword.iron")));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        var entries =
                harness.auditLog
                        .between(Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plusSeconds(60))
                        .get();

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).action()).isEqualTo("item_granted");
        assertThat(entries.get(0).actor()).isEqualTo("admin");
        assertThat(entries.get(0).targetPlayerId()).contains(target);
        assertThat(entries.get(0).details()).containsEntry("template", "sword.iron");
    }

    @Test
    void everyActionIsItsOwnRowRatherThanBeingCoalesced() throws Exception {
        for (int i = 0; i < 5; i++) {
            harness.auditLog.append(
                    new AuditEntry(
                            Instant.now(), "admin", "player_banned", Optional.empty(), Map.of()));
        }
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        // Unlike the other aggregates, audit entries must NOT coalesce - "the last one wins" would
        // defeat the purpose of a trail.
        assertThat(countEntries()).isEqualTo(5L);
    }

    @Test
    void theRepositoryOffersNoWayToChangeOrRemoveAnEntry() {
        // The guarantee is the absence of those methods: an editable audit log proves nothing.
        assertThat(AuditLogRepository.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .containsExactlyInAnyOrder("append", "between");
    }

    private long countEntries() throws Exception {
        try (Connection connection = PostgresContainer.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*) FROM rpg.audit_log")) {
            return rows.next() ? rows.getLong(1) : 0L;
        }
    }
}
