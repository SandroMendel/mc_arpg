package rpg.platform.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
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
 * FR-039, FR-040: solange ein Klon des Rogue steht, wenden sich eigene Kreaturen in seiner
 * Reichweite ihm zu; nach seinem Ende wieder dem Spieler.
 */
class CloneAggroTest {

    private ServerMock server;
    private WorldMock world;
    private CloneAggroListener listener;
    private PlayerMock owner;
    private LivingEntity mob;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        listener = new CloneAggroListener(server, () -> configFixture(), Clock.systemUTC());
        server.getPluginManager().registerEvents(listener, MockBukkit.createMockPlugin("CloneAggroProbe"));

        owner = server.addPlayer();
        owner.teleport(new Location(world, 0, 70, 0));

        Entity zombie = world.spawnEntity(new Location(world, 5, 70, 0), EntityType.ZOMBIE);
        MobKindTag.mark(zombie, "greenfields.rotling", "greenfields");
        mob = (LivingEntity) zombie;
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("ohne Klon bleibt das Ziel unveraendert - der Spieler")
    void withoutACloneTheTargetStaysThePlayer() {
        var event = targetEvent(owner);

        server.getPluginManager().callEvent(event);

        assertThat(event.getTarget()).isEqualTo(owner);
    }

    @Test
    @DisplayName("mit einem Klon in Reichweite wird das Ziel auf ihn umgelenkt (FR-039)")
    void withACloneInRangeTheTargetIsRedirectedToHim() {
        LivingEntity clone = placeClone(new Location(world, 5, 70, 2));
        listener.registerClone(owner.getUniqueId(), clone.getUniqueId());

        var event = targetEvent(owner);
        server.getPluginManager().callEvent(event);

        assertThat(event.getTarget()).isEqualTo(clone);
    }

    @Test
    @DisplayName("ein Klon ausserhalb der Zielsuchreichweite lenkt nichts um")
    void aCloneOutsideTheFollowRangeRedirectsNothing() {
        LivingEntity clone = placeClone(new Location(world, 9000, 70, 9000));
        listener.registerClone(owner.getUniqueId(), clone.getUniqueId());

        var event = targetEvent(owner);
        server.getPluginManager().callEvent(event);

        assertThat(event.getTarget()).isEqualTo(owner);
    }

    @Test
    @DisplayName("nach dem Ende des Klons (entfernt) waehlt die naechste Wahl wieder den Spieler (FR-040)")
    void afterTheCloneEndsTheNextChoicePicksThePlayerAgain() {
        LivingEntity clone = placeClone(new Location(world, 5, 70, 2));
        listener.registerClone(owner.getUniqueId(), clone.getUniqueId());
        clone.remove();
        server.getPluginManager()
                .callEvent(
                        new org.bukkit.event.entity.EntityRemoveEvent(
                                clone, org.bukkit.event.entity.EntityRemoveEvent.Cause.PLUGIN));

        var event = targetEvent(owner);
        server.getPluginManager().callEvent(event);

        assertThat(event.getTarget()).isEqualTo(owner);
    }

    @Test
    @DisplayName(
            "eine Kreatur, die den Spieler schon VOR dem Klon jagte, wird beim Erscheinen sofort"
                    + " umgelenkt (FR-039)")
    void aCreatureAlreadyChasingThePlayerIsReclaimedWhenTheCloneAppears() {
        // Vanilla feuert EntityTargetLivingEntityEvent nur bei einer NEUEN Zielwahl - eine
        // Kreatur, die den Spieler schon jagt, bekaeme es sonst nie (der Praxisfall: erst
        // angreifen, dann den Klon als Ablenkung stellen).
        ((org.bukkit.entity.Mob) mob).setTarget(owner);
        LivingEntity clone = placeClone(new Location(world, 5, 70, 2));

        listener.registerClone(owner.getUniqueId(), clone.getUniqueId());

        assertThat(((org.bukkit.entity.Mob) mob).getTarget()).isEqualTo(clone);
    }

    @Test
    @DisplayName("eine Vanilla-Kreatur ohne Vermerk wird nicht umgelenkt")
    void aVanillaCreatureWithoutTheTagIsNotRedirected() {
        Entity plainZombie = world.spawnEntity(new Location(world, 5, 70, 5), EntityType.ZOMBIE);
        LivingEntity clone = placeClone(new Location(world, 5, 70, 5));
        listener.registerClone(owner.getUniqueId(), clone.getUniqueId());

        var event =
                new EntityTargetLivingEntityEvent(
                        plainZombie, owner, EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
        server.getPluginManager().callEvent(event);

        assertThat(event.getTarget()).isEqualTo(owner);
    }

    private LivingEntity placeClone(Location where) {
        Entity clone = world.spawnEntity(where, EntityType.ARMOR_STAND);
        return (LivingEntity) clone;
    }

    private EntityTargetLivingEntityEvent targetEvent(PlayerMock target) {
        return new EntityTargetLivingEntityEvent(
                mob, target, EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
    }

    private static MobConfig configFixture() {
        return new MobConfig(
                new Budget(800, 130, 12, 25),
                Duration.ofMillis(2000),
                0.20,
                Duration.ofSeconds(60),
                96.0,
                Duration.ofMillis(500),
                Map.of(),
                Map.of());
    }
}
