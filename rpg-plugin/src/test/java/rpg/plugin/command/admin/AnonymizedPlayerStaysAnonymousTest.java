package rpg.plugin.command.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

/** T096 — inspection never reconstructs a clear name after B02 anonymisation. */
class AnonymizedPlayerStaysAnonymousTest {

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
    @DisplayName("no inspection view restores an anonymised player's clear name")
    void anonymisedTargetStaysAnonymousForEveryView() {
        InspectCommand command =
                InspectionTestSupport.command(
                        server,
                        (view, playerId) ->
                                CompletableFuture.completedFuture(
                                        new InspectCommand.Report(
                                                true,
                                                List.of(
                                                        new InspectCommand.Line(
                                                                InspectMessageKeys.SHEET_LEVEL,
                                                                "42")))));

        for (RpgCommand view : command.definition(Duration.ZERO).children()) {
            OfflinePlayer targetPlayer = target;
            view.action()
                    .accept(
                            InspectionTestSupport.context(
                                    moderator, Map.of(view.arguments().get(0).name(), targetPlayer)));
            assertThat(moderator.nextMessage())
                    .contains("anonymous player")
                    .doesNotContain("Target");
            assertThat(moderator.nextMessage()).contains("Level: 42").doesNotContain("Target");
        }
    }
}
