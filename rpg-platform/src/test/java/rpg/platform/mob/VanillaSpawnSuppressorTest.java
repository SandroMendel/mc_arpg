package rpg.platform.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Logger;

import org.bukkit.GameRules;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.EntityMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Zwei Schichten fuer die Vanilla-Unterdrueckung (research.md R1, FR-018c, FR-018d, FR-018e).
 */
class VanillaSpawnSuppressorTest {

    private ServerMock server;
    private WorldMock world;
    private VanillaSpawnSuppressor suppressor;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        suppressor = new VanillaSpawnSuppressor(Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Schicht 1: alle sieben Spielregeln werden auf jeder geladenen Welt auf false gesetzt")
    void allSevenGameRulesAreSetOnEveryLoadedWorld() {
        suppressor.applyTo(server);

        assertThat(world.getGameRuleValue(GameRules.SPAWN_MOBS)).isFalse();
        assertThat(world.getGameRuleValue(GameRules.SPAWN_MONSTERS)).isFalse();
        assertThat(world.getGameRuleValue(GameRules.SPAWN_PATROLS)).isFalse();
        assertThat(world.getGameRuleValue(GameRules.SPAWN_PHANTOMS)).isFalse();
        assertThat(world.getGameRuleValue(GameRules.SPAWN_WANDERING_TRADERS)).isFalse();
        assertThat(world.getGameRuleValue(GameRules.SPAWN_WARDENS)).isFalse();
        assertThat(world.getGameRuleValue(GameRules.SPAWNER_BLOCKS_WORK)).isFalse();
    }

    @Test
    @DisplayName("eine spaeter geladene Welt bekommt dieselben Regeln (WorldLoadEvent)")
    void aLaterLoadedWorldGetsTheSameRules() {
        WorldMock later = new WorldMock();

        suppressor.onWorldLoad(new org.bukkit.event.world.WorldLoadEvent(later));

        assertThat(later.getGameRuleValue(GameRules.SPAWN_MOBS)).isFalse();
        assertThat(later.getGameRuleValue(GameRules.SPAWN_WARDENS)).isFalse();
    }

    @Test
    @DisplayName("Schicht 2: ein NATURAL-Ereignis wird abgebrochen")
    void aNaturalEventIsCancelled() {
        EntityMock entity = new org.mockbukkit.mockbukkit.entity.ZombieMock(server, java.util.UUID.randomUUID());
        CreatureSpawnEvent event =
                new CreatureSpawnEvent(
                        (org.bukkit.entity.LivingEntity) entity,
                        CreatureSpawnEvent.SpawnReason.NATURAL);

        suppressor.onCreatureSpawn(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Schicht 2: ein SPAWNER_EGG-Ereignis wird NICHT abgebrochen (FR-018d)")
    void aSpawnerEggEventIsNotCancelled() {
        EntityMock entity = new org.mockbukkit.mockbukkit.entity.ZombieMock(server, java.util.UUID.randomUUID());
        CreatureSpawnEvent event =
                new CreatureSpawnEvent(
                        (org.bukkit.entity.LivingEntity) entity,
                        CreatureSpawnEvent.SpawnReason.SPAWNER_EGG);

        suppressor.onCreatureSpawn(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("Schicht 2: die eigenen Kreaturen (CUSTOM) werden NICHT mitunterdrueckt (FR-018e)")
    void ownCreaturesAreNotSuppressed() {
        EntityMock entity = new org.mockbukkit.mockbukkit.entity.ZombieMock(server, java.util.UUID.randomUUID());
        CreatureSpawnEvent event =
                new CreatureSpawnEvent(
                        (org.bukkit.entity.LivingEntity) entity, CreatureSpawnEvent.SpawnReason.CUSTOM);

        suppressor.onCreatureSpawn(event);

        assertThat(event.isCancelled()).isFalse();
    }
}
