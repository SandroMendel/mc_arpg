package rpg.platform.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** T053 - ein Spieler ohne Charakter bewegt sich nicht (US1.5, FR-034). */
class NoCharacterGuardTest {

    private static final Logger QUIET = Logger.getLogger("no-character-guard-test");

    private ServerMock server;
    private NoCharacterGuardListener guard;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        guard = new NoCharacterGuardListener(QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("ein gehaltener Spieler kann seinen Block nicht verlassen")
    void heldPlayerCannotLeaveTheirBlock() {
        PlayerMock player = server.addPlayer();

        guard.hold(player);
        PlayerMoveEvent move = moveOneBlock(player);
        guard.onMove(move);

        assertThat(guard.isHeld(player.getUniqueId())).isTrue();
        assertThat(move.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("umsehen bleibt erlaubt - nur das Verlassen des Blocks wird abgewiesen")
    void lookingAroundStaysFree() {
        PlayerMock player = server.addPlayer();
        guard.hold(player);

        Location from = player.getLocation();
        Location to = from.clone();
        to.setYaw(from.getYaw() + 90.0f);
        PlayerMoveEvent move = new PlayerMoveEvent(player, from, to);
        guard.onMove(move);

        assertThat(move.isCancelled())
                .as("eine eingefrorene Kamera liest sich als kaputter Client")
                .isFalse();
    }

    @Test
    @DisplayName("nach der Freigabe bewegt sich der Spieler wieder")
    void releasedPlayerMovesAgain() {
        PlayerMock player = server.addPlayer();
        guard.hold(player);
        guard.release(player.getUniqueId());

        PlayerMoveEvent move = moveOneBlock(player);
        guard.onMove(move);

        assertThat(guard.isHeld(player.getUniqueId())).isFalse();
        assertThat(move.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("ein nicht gehaltener Spieler wird nie behindert")
    void unheldPlayerIsNeverBlocked() {
        PlayerMock player = server.addPlayer();

        PlayerMoveEvent move = moveOneBlock(player);
        guard.onMove(move);

        assertThat(move.isCancelled()).isFalse();
        assertThat(guard.heldCount()).isZero();
    }

    @Test
    @DisplayName("der Schnellpfad ist im Normalbetrieb ein int-Vergleich (Prinzip II)")
    void hotPathIsAnIntComparison() {
        PlayerMock player = server.addPlayer();

        // Kein Spieler gehalten: heldCount ist null, und die Menge wird nie befragt. Das ist der
        // Zustand im Normalbetrieb, und PlayerMoveEvent feuert mehrmals je Sekunde je Spieler.
        assertThat(guard.heldCount()).isZero();

        for (int i = 0; i < 10_000; i++) {
            guard.onMove(moveOneBlock(player));
        }

        assertThat(guard.heldCount()).isZero();
    }

    @Test
    @DisplayName("doppeltes hold und release zählt nicht doppelt")
    void holdIsIdempotent() {
        PlayerMock player = server.addPlayer();

        guard.hold(player);
        guard.hold(player);
        assertThat(guard.heldCount()).isEqualTo(1);

        guard.release(player.getUniqueId());
        guard.release(player.getUniqueId());
        assertThat(guard.heldCount()).isZero();
    }

    private static PlayerMoveEvent moveOneBlock(PlayerMock player) {
        Location from = player.getLocation();
        Location to = from.clone().add(1.0, 0.0, 0.0);
        return new PlayerMoveEvent(player, from, to);
    }
}
