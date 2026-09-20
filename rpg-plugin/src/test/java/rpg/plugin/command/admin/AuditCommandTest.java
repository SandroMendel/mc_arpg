package rpg.plugin.command.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.persistence.AuditEntry;
import rpg.plugin.command.framework.RpgCommand;

/** T098–T101 — the audit reader is asynchronous, throttled and capped at ten entries per page. */
class AuditCommandTest {

    private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private ServerMock server;
    private WorldMock world;
    private PlayerMock moderator;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        moderator = server.addPlayer("Moderator");
        moderator.teleport(new Location(world, 0.5, 65, 0.5));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("audit reads the selected range asynchronously and renders only one page")
    void readsRangeAndCapsPage() {
        AtomicReference<Instant> from = new AtomicReference<>();
        AtomicReference<Instant> to = new AtomicReference<>();
        List<AuditEntry> entries = entries(11);
        AuditCommand command =
                InspectionTestSupport.auditCommand(
                        server,
                        CLOCK,
                        (start, end) -> {
                            from.set(start);
                            to.set(end);
                            return CompletableFuture.completedFuture(entries);
                        });
        RpgCommand audit = command.definition(Duration.ofSeconds(10));

        assertThat(audit.name()).isEqualTo("audit");
        assertThat(audit.permission()).isEqualTo(AuditCommand.PERMISSION);
        assertThat(audit.arguments()).extracting(rpg.plugin.command.framework.Argument::name)
                .containsExactly("duration");
        assertThat(audit.rateLimit()).isEqualTo(Duration.ofSeconds(10));

        audit.action()
                .accept(
                        InspectionTestSupport.context(
                                moderator, Map.of("duration", Duration.ofHours(2))));

        assertThat(from).hasValue(NOW.minus(Duration.ofHours(2)));
        assertThat(to).hasValue(NOW);
        assertThat(moderator.nextMessage()).contains("page 1/2").contains("11 entries");
        for (int index = 0; index < AuditCommand.PAGE_SIZE; index++) {
            assertThat(moderator.nextMessage()).contains("action=action-" + index);
        }
        assertThat(moderator.nextMessage()).contains("1 more entries");
        assertThat(moderator.nextMessage()).isNull();
    }

    @Test
    @DisplayName("audit uses the default range and explains empty or failed reads")
    void defaultEmptyAndFailureAreVisible() {
        AtomicReference<Instant> from = new AtomicReference<>();
        AuditCommand empty =
                InspectionTestSupport.auditCommand(
                        server,
                        CLOCK,
                        (start, end) -> {
                            from.set(start);
                            return CompletableFuture.completedFuture(List.of());
                        });
        RpgCommand audit = empty.definition(Duration.ZERO);
        audit.action().accept(InspectionTestSupport.context(moderator, Map.of()));
        assertThat(from).hasValue(NOW.minus(AuditCommand.DEFAULT_RANGE));
        assertThat(moderator.nextMessage()).isEqualTo("No audit entries");

        CompletableFuture<List<AuditEntry>> failed = new CompletableFuture<>();
        AuditCommand failure =
                InspectionTestSupport.auditCommand(server, CLOCK, (start, end) -> failed);
        failure.definition(Duration.ZERO)
                .action()
                .accept(InspectionTestSupport.context(moderator, Map.of()));
        failed.completeExceptionally(new IllegalStateException("database unavailable"));
        assertThat(moderator.nextMessage()).isEqualTo("Audit failed");
    }

    private static List<AuditEntry> entries(int count) {
        List<AuditEntry> entries = new ArrayList<>();
        UUID target = UUID.fromString("11111111-1111-1111-1111-111111111111");
        for (int index = 0; index < count; index++) {
            entries.add(
                    new AuditEntry(
                            NOW.minusSeconds(index),
                            "actor-" + index,
                            "action-" + index,
                            Optional.of(target),
                            Map.of("index", index)));
        }
        return entries;
    }
}
