package rpg.platform.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.mob.Budget;
import rpg.core.mob.MobConfig;

/**
 * FR-041: das Umschwenken auf den Klon unterliegt derselben Drosselung wie eine gewoehnliche
 * Zielzuweisung (FR-035) - kein zweiter, ungedrosselter Weg, das Ziel zu wechseln.
 */
class CloneAggroIsThrottledTest {

    private static final Duration RETARGET_INTERVAL = Duration.ofMillis(500);

    private ServerMock server;
    private WorldMock world;
    private MutableClock clock;
    private CloneAggroListener listener;
    private PlayerMock owner;
    private LivingEntity mob;
    private LivingEntity clone;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        clock = new MutableClock(Instant.parse("2026-08-26T20:00:00Z"));
        listener = new CloneAggroListener(server, CloneAggroIsThrottledTest::configFixture, clock);
        server.getPluginManager()
                .registerEvents(listener, MockBukkit.createMockPlugin("CloneAggroThrottleProbe"));

        owner = server.addPlayer();
        owner.teleport(new Location(world, 0, 70, 0));

        Entity zombie = world.spawnEntity(new Location(world, 5, 70, 0), EntityType.ZOMBIE);
        MobKindTag.mark(zombie, "greenfields.rotling", "greenfields");
        mob = (LivingEntity) zombie;

        Entity armorStand = world.spawnEntity(new Location(world, 5, 70, 2), EntityType.ARMOR_STAND);
        clone = (LivingEntity) armorStand;
        listener.registerClone(owner.getUniqueId(), clone.getUniqueId());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("die erste Umlenkung gelingt sofort - es gab noch keine vorherige Zuweisung")
    void theFirstRedirectSucceedsImmediately() {
        var event = targetEvent();
        server.getPluginManager().callEvent(event);

        assertThat(event.getTarget()).isEqualTo(clone);
    }

    @Test
    @DisplayName("eine zweite Umlenkung innerhalb des Abstands bleibt aus (FR-041)")
    void aSecondRedirectWithinTheIntervalDoesNotHappen() {
        server.getPluginManager().callEvent(targetEvent());

        // Vanilla waehlt gleich wieder den Spieler - ohne Drosselung wuerde der Zuhoerer sofort
        // wieder umlenken. Mit ihr bleibt das Ziel diesmal beim Spieler.
        clock.advance(Duration.ofMillis(100));
        var secondEvent = targetEvent();
        server.getPluginManager().callEvent(secondEvent);

        assertThat(secondEvent.getTarget()).isEqualTo(owner);
    }

    @Test
    @DisplayName("nach Ablauf des Abstands gelingt die Umlenkung wieder")
    void afterTheIntervalPassesTheRedirectSucceedsAgain() {
        server.getPluginManager().callEvent(targetEvent());

        clock.advance(RETARGET_INTERVAL);
        var laterEvent = targetEvent();
        server.getPluginManager().callEvent(laterEvent);

        assertThat(laterEvent.getTarget()).isEqualTo(clone);
    }

    private EntityTargetLivingEntityEvent targetEvent() {
        return new EntityTargetLivingEntityEvent(
                mob, owner, EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
    }

    private static MobConfig configFixture() {
        return new MobConfig(
                new Budget(800, 130, 12, 25),
                Duration.ofMillis(2000),
                0.20,
                Duration.ofSeconds(60),
                96.0,
                RETARGET_INTERVAL,
                Map.of(),
                Map.of());
    }
}
