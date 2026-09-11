package rpg.plugin.command.framework;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.message.MessageKey;

/** T028 — ohne Recht wird die Ausführung <b>nicht berührt</b> (FR-013). */
class PermissionGateTest {

    private static final MessageKey ANY = MessageKey.of("command.rpg.description");

    private ServerMock server;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("ohne Recht: abgelehnt")
    void withoutThePermissionItIsRefused() {
        RpgCommand guarded =
                RpgCommand.leaf("reload", ANY, "rpg.admin.reload", List.of(), context -> {});

        assertThat(CommandPermissions.allows(player, guarded)).isFalse();
    }

    @Test
    @DisplayName("mit Recht: erlaubt")
    void withThePermissionItIsAllowed() {
        RpgCommand guarded =
                RpgCommand.leaf("reload", ANY, "rpg.admin.reload", List.of(), context -> {});
        player.addAttachment(MockBukkit.createMockPlugin(), "rpg.admin.reload", true);

        assertThat(CommandPermissions.allows(player, guarded)).isTrue();
    }

    @Test
    @DisplayName("ein Knoten OHNE Recht steht jedem offen")
    void anodeWithoutAPermissionIsOpen() {
        // Kein Schlupfloch, sondern die Regel fuer die Wurzel und die Spielerkommandos.
        RpgCommand open = RpgCommand.leaf("char", ANY, null, List.of(), context -> {});

        assertThat(CommandPermissions.allows(player, open)).isTrue();
        assertThat(CommandPermissions.allows(server.getConsoleSender(), open)).isTrue();
    }

    @Test
    @DisplayName("die Konsole darf alles - sonst waere sie von ihrem eigenen Server ausgesperrt")
    void theconsoleMayDoAnything() {
        RpgCommand guarded =
                RpgCommand.leaf("reload", ANY, "rpg.admin.reload", List.of(), context -> {});

        assertThat(CommandPermissions.allows(server.getConsoleSender(), guarded)).isTrue();
    }

    @Test
    @DisplayName("die Ausfuehrung wird ohne Recht NICHT beruehrt")
    void theactionIsNotTouchedWithoutThePermission() {
        // Der eigentliche Punkt von FR-013: nicht "es passiert nichts Schlimmes", sondern die
        // Ausfuehrung laeuft gar nicht erst an. Ein Kommando, das haelt anhand einer Pruefung in
        // seinem eigenen Rumpf, hat vorher schon irgendetwas getan.
        boolean[] ran = {false};
        RpgCommand guarded =
                RpgCommand.leaf(
                        "reload", ANY, "rpg.admin.reload", List.of(), context -> ran[0] = true);

        if (CommandPermissions.allows(player, guarded)) {
            guarded.action().accept(null);
        }

        assertThat(ran[0]).isFalse();
    }
}
