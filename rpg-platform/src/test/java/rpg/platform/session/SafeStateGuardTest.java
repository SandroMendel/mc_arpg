package rpg.platform.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * T025, T032: the safe state, and above all what it costs when nobody is in it.
 *
 * <p>{@link PlayerMoveEvent} fires several times per tick per player. At 200 players this handler
 * runs more often than anything else in the plugin, so the test that matters most here is not that
 * holding works - it is that <em>not</em> holding costs a single {@code int} read.
 *
 * <p>Note on MockBukkit: an unimplemented operation is reported as an <em>aborted</em> test, not a
 * failed one. A run that says "0 failures" while quietly skipping three tests looks identical to a
 * green run, which is why every run of this module checks the skipped count as well.
 */
class SafeStateGuardTest {

    private static final Logger QUIET = Logger.getLogger("safe-state-guard-test");

    private ServerMock server;
    private SafeStateGuard guard;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        guard = new SafeStateGuard(QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aHeldPlayerIsImmuneAndCannotLeaveTheirBlock() {
        PlayerMock player = server.addPlayer();

        guard.hold(player);

        assertThat(player.isInvulnerable()).isTrue();
        assertThat(guard.isHeld(player.getUniqueId())).isTrue();

        PlayerMoveEvent move = moveOneBlock(player);
        guard.onMove(move);

        assertThat(move.isCancelled()).isTrue();
    }

    @Test
    void aHeldPlayerMayStillLookAround() {
        // Freezing the view too would make the wait feel like a crash rather than a wait, and it
        // buys nothing: looking around cannot change any value the session owns.
        PlayerMock player = server.addPlayer();
        guard.hold(player);

        Location from = player.getLocation();
        Location to = from.clone();
        to.setYaw(from.getYaw() + 90f);
        PlayerMoveEvent move = new PlayerMoveEvent(player, from, to);

        guard.onMove(move);

        assertThat(move.isCancelled()).isFalse();
    }

    @Test
    void releasingRestoresBothMovementAndVulnerability() {
        PlayerMock player = server.addPlayer();
        guard.hold(player);

        guard.release(player);

        assertThat(player.isInvulnerable()).isFalse();
        assertThat(guard.isHeld(player.getUniqueId())).isFalse();

        PlayerMoveEvent move = moveOneBlock(player);
        guard.onMove(move);
        assertThat(move.isCancelled()).isFalse();
    }

    @Test
    void anUnheldPlayerMovesFreelyWhileSomeoneElseIsHeld() {
        // The interesting case: the fast path is off, so the handler really does consult the set -
        // and must still let everyone else through.
        PlayerMock held = server.addPlayer();
        PlayerMock free = server.addPlayer();
        guard.hold(held);

        PlayerMoveEvent move = moveOneBlock(free);
        guard.onMove(move);

        assertThat(move.isCancelled()).isFalse();
    }

    @Test
    void holdingTwiceCountsOnce() {
        PlayerMock player = server.addPlayer();

        guard.hold(player);
        guard.hold(player);
        assertThat(guard.heldCount()).isEqualTo(1);

        guard.release(player);
        assertThat(guard.heldCount()).isZero();

        // A release for someone who was never held must not push the counter below zero - the hot
        // path reads that counter and would then never take its fast exit again.
        guard.release(UUID.randomUUID());
        assertThat(guard.heldCount()).isZero();
    }

    @Test
    void inNormalOperationNobodyIsHeldAndTheHandlerExitsOnAnIntRead() {
        // The hot path (FR-002, Constitution II). heldCount() is what the handler reads first; while
        // it is zero the handler returns without touching the map. Asserting the counter is the
        // closest a test can get to asserting "this costs nothing".
        PlayerMock player = server.addPlayer();

        assertThat(guard.heldCount()).isZero();

        for (int i = 0; i < 1_000; i++) {
            PlayerMoveEvent move = moveOneBlock(player);
            guard.onMove(move);
            assertThat(move.isCancelled()).isFalse();
        }

        assertThat(guard.heldCount()).isZero();
    }

    @Test
    void aMoveWithoutADestinationIsIgnored() {
        // Paper allows a null target; treating that as "left the block" would cancel movement for
        // held players on an event that describes no movement at all.
        PlayerMock player = server.addPlayer();
        guard.hold(player);

        PlayerMoveEvent move = new PlayerMoveEvent(player, player.getLocation(), null);
        guard.onMove(move);

        assertThat(move.isCancelled()).isFalse();
    }

    private static PlayerMoveEvent moveOneBlock(Player player) {
        Location from = player.getLocation();
        Location to = from.clone().add(1.0d, 0.0d, 0.0d);
        return new PlayerMoveEvent(player, from, to);
    }
}
