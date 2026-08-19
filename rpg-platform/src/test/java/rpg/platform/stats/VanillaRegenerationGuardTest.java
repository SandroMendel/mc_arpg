package rpg.platform.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.GameRules;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** T052: nothing but the engine writes the health bar (FR-030a). */
class VanillaRegenerationGuardTest {

    private static final Logger QUIET = Logger.getLogger("regen-guard-test");

    private ServerMock server;
    private VanillaRegenerationGuard guard;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        guard = new VanillaRegenerationGuard(QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("natural regeneration is off in every loaded world")
    void gameRuleIsDisabled() {
        server.getWorlds().forEach(w -> w.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, true));

        guard.applyTo(server);

        assertThat(server.getWorlds())
                .allSatisfy(
                        world ->
                                assertThat(world.getGameRuleValue(GameRules.NATURAL_HEALTH_REGENERATION))
                                        .isFalse());
    }

    @Test
    @DisplayName("vanilla regeneration is cancelled, so the bar only moves when the engine moves it")
    void regenerationIsCancelled() {
        PlayerMock player = server.addPlayer();

        for (EntityRegainHealthEvent.RegainReason reason :
                new EntityRegainHealthEvent.RegainReason[] {
                    EntityRegainHealthEvent.RegainReason.REGEN,
                    EntityRegainHealthEvent.RegainReason.SATIATED,
                    EntityRegainHealthEvent.RegainReason.EATING,
                    EntityRegainHealthEvent.RegainReason.MAGIC_REGEN
                }) {
            EntityRegainHealthEvent event = new EntityRegainHealthEvent(player, 1.0, reason);
            guard.onRegainHealth(event);
            assertThat(event.isCancelled()).as(reason.name()).isTrue();
        }
    }

    @Test
    @DisplayName("a custom heal is left alone - that is how the engine itself would raise health")
    void customHealIsNotCancelled() {
        PlayerMock player = server.addPlayer();

        EntityRegainHealthEvent event =
                new EntityRegainHealthEvent(
                        player, 1.0, EntityRegainHealthEvent.RegainReason.CUSTOM);
        guard.onRegainHealth(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("the food level is held steady so hunger never becomes a second health system")
    void foodLevelIsPinned() {
        PlayerMock player = server.addPlayer();

        FoodLevelChangeEvent event = new FoodLevelChangeEvent(player, 7, null);
        guard.onFoodLevelChange(event);

        assertThat(event.getFoodLevel()).isEqualTo(VanillaRegenerationGuard.PINNED_FOOD_LEVEL);
    }
}
