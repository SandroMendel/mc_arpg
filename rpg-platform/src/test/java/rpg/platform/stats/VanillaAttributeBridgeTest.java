package rpg.platform.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.attribute.Attribute;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.platform.scheduler.ImmediateScheduler;

/**
 * T049-T051: the vanilla mirror (FR-030 to FR-033, SC-008).
 *
 * <p>MockBukkit note, same as everywhere in this module: an unimplemented operation is reported as
 * an <em>aborted</em> test, not a failing one. A run showing "0 failures" while quietly skipping
 * three tests looks exactly like a green run, so the skipped count is checked separately after
 * every run.
 */
class VanillaAttributeBridgeTest {

    private static final Logger QUIET = Logger.getLogger("vanilla-bridge-test");

    private ServerMock server;
    private PaperVanillaAttributeBridge bridge;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        bridge = new PaperVanillaAttributeBridge(server, new ImmediateScheduler(), QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("500 of 1000 health shows ten points, with the vanilla maximum pinned at twenty")
    void halfHealthShowsTenPoints() {
        PlayerMock player = server.addPlayer();

        bridge.mirrorHealth(player.getUniqueId(), 500.0, 1000.0);

        assertThat(player.getAttribute(Attribute.MAX_HEALTH).getBaseValue()).isEqualTo(20.0);
        assertThat(player.getHealth()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("a maximum of 2000 leaves the vanilla maximum at twenty")
    void vanillaMaximumStaysPinned() {
        PlayerMock player = server.addPlayer();

        bridge.mirrorHealth(player.getUniqueId(), 2000.0, 2000.0);

        assertThat(player.getAttribute(Attribute.MAX_HEALTH).getBaseValue()).isEqualTo(20.0);
        assertThat(player.getHealth()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("a living player never shows zero hearts")
    void aLivingPlayerNeverShowsEmpty() {
        PlayerMock player = server.addPlayer();

        bridge.mirrorHealth(player.getUniqueId(), 0.4, 1000.0);

        // 0.4 of 1000 scales to 0.008 points, which would round to an empty bar. Someone who
        // believes they are dead plays differently from someone who knows they are nearly dead.
        assertThat(player.getHealth()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("zero health shows zero")
    void deadShowsEmpty() {
        PlayerMock player = server.addPlayer();

        bridge.mirrorHealth(player.getUniqueId(), 0.0, 1000.0);

        assertThat(player.getHealth()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("movement and attack speed reach their vanilla attributes where the mock has them")
    void speedsAreMirrored() {
        PlayerMock player = server.addPlayer();

        bridge.mirrorMovementSpeed(player.getUniqueId(), 0.13);
        bridge.mirrorAttackSpeed(player.getUniqueId(), 5.5);

        // MockBukkit's PlayerMock only backs a subset of the attributes; the two speeds return
        // null here while MAX_HEALTH does not. Asserting on them unconditionally would fail for a
        // property of the test double rather than of the bridge. What is asserted instead: the
        // bridge tolerates the absence rather than throwing (the same thing that happens to a mob
        // type without a speed attribute), and where the mock does back an attribute, the value
        // lands - which the health tests above cover on the real path.
        if (player.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            assertThat(player.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue()).isEqualTo(0.13);
        }
        if (player.getAttribute(Attribute.ATTACK_SPEED) != null) {
            assertThat(player.getAttribute(Attribute.ATTACK_SPEED).getBaseValue()).isEqualTo(5.5);
        }
        // The bar is still correct afterwards, so the two calls did not leave anything broken.
        bridge.mirrorHealth(player.getUniqueId(), 250.0, 1000.0);
        assertThat(player.getHealth()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("an absent holder is skipped rather than treated as a fault")
    void absentHolderIsSkipped() {
        // Normal during a logout: the recalculation was already scheduled when the player left.
        bridge.mirrorHealth(java.util.UUID.randomUUID(), 10.0, 100.0);
        bridge.mirrorAttackSpeed(java.util.UUID.randomUUID(), 4.0);
    }

    @Test
    @DisplayName("the display rule holds across the whole range, without a server")
    void displayRuleIsPure() {
        assertThat(PaperVanillaAttributeBridge.displayedHealth(1000.0, 1000.0)).isEqualTo(20.0);
        assertThat(PaperVanillaAttributeBridge.displayedHealth(750.0, 1000.0)).isEqualTo(15.0);
        // 1 of 100 scales to 0.2, below the half-heart the client can actually draw. The floor
        // applies: the bar is deliberately non-linear at the very bottom, because the alternative
        // is an empty bar on a living player.
        assertThat(PaperVanillaAttributeBridge.displayedHealth(1.0, 100.0)).isEqualTo(0.5);
        assertThat(PaperVanillaAttributeBridge.displayedHealth(5.0, 100.0)).isEqualTo(1.0);
        assertThat(PaperVanillaAttributeBridge.displayedHealth(0.0, 100.0)).isEqualTo(0.0);
        // Never above the vanilla ceiling, even if a caller passes something inconsistent.
        assertThat(PaperVanillaAttributeBridge.displayedHealth(200.0, 100.0)).isEqualTo(20.0);
    }
}
