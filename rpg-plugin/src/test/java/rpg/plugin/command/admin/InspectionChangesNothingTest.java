package rpg.plugin.command.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.plugin.command.framework.RpgCommand;

/** T095 — reading every view leaves the target snapshot exactly as it was. */
class InspectionChangesNothingTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock moderator;
    private PlayerMock target;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        moderator = server.addPlayer("Moderator");
        target = server.addPlayer("Target");
        moderator.teleport(new Location(world, 0.5, 65, 0.5));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("every inspection view preserves the target values")
    void everyViewLeavesTargetUntouched() {
        Map<String, String> before =
                new LinkedHashMap<>(
                        Map.of(
                                "level", "35",
                                "xp", "812",
                                "coins", "2400",
                                "inventory", "backpack=3;ender=1"));
        AtomicReference<Map<String, String>> targetState = new AtomicReference<>(before);
        AtomicReference<UUID> requested = new AtomicReference<>();

        InspectCommand command =
                InspectionTestSupport.command(
                        server,
                        (view, playerId) -> {
                            requested.set(playerId);
                            Map<String, String> snapshot = targetState.get();
                            return CompletableFuture.completedFuture(
                                    new InspectCommand.Report(
                                            false,
                                            List.of(
                                                    new InspectCommand.Line(
                                                            InspectMessageKeys.SHEET_LEVEL,
                                                            snapshot.get("level")))));
                        });

        for (RpgCommand view : command.definition(Duration.ZERO).children()) {
            OfflinePlayer targetPlayer = target;
            view.action()
                    .accept(
                            InspectionTestSupport.context(
                                    moderator, Map.of(view.arguments().get(0).name(), targetPlayer)));
            assertThat(moderator.nextMessage()).isNotNull();
            assertThat(moderator.nextMessage()).contains("Level: 35");
            assertThat(targetState.get()).isEqualTo(before);
        }

        assertThat(requested).hasValue(target.getUniqueId());
        assertThat(targetState.get()).isEqualTo(before);
    }
}
