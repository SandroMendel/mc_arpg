package rpg.platform.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.classes.ClassMessageKeys;

/** Die Ausgabe hinter {@code ClassNotice}: Titel und Ton, und niemandem ins Wort fallen. */
class PaperClassNoticeTest {

    private ServerMock server;
    private PaperClassNotice notice;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        notice = new PaperClassNotice(server, PlatformClassFixture.messages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("die Warnung kommt als Ton, nicht nur als Zeile im Chat (US4.6)")
    void theNoticeIsAudible() {
        PlayerMock player = server.addPlayer();

        notice.show(player.getUniqueId(), ClassMessageKeys.INVENTORY_FULL);

        // Der Ton ist der Teil, der ankommt, während der Spieler in die Welt schaut statt auf die
        // Bildschirmmitte. Der Titel wird von MockBukkit für den Adventure-Weg nicht mitgeschrieben;
        // dass er gesetzt wird, bleibt ein Punkt für den echten Server.
        assertThat(player.getHeardSounds())
                .hasSize(1)
                .allSatisfy(heard -> assertThat(heard.getSound()).contains("note_block"));
    }

    @Test
    @DisplayName("ein Spieler, der schon weg ist, wirft nicht - eine Meldung ist das nicht wert")
    void anAbsentPlayerIsNoError() {
        assertThatCode(
                        () -> notice.show(UUID.randomUUID(), ClassMessageKeys.INVENTORY_FULL))
                .doesNotThrowAnyException();
    }
}
