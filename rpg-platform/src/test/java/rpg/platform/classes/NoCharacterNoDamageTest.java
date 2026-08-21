package rpg.platform.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.combat.CombatConfig;
import rpg.core.combat.DefaultCombatPipeline;
import rpg.core.event.DefaultEventBus;
import rpg.core.stats.DefaultStatEngine;
import rpg.core.stats.StatConfig;
import rpg.platform.combat.VanillaDamageListener;
import rpg.platform.combat.VanillaDamageMapping;
import rpg.platform.scheduler.ImmediateScheduler;

/**
 * T054: ein Spieler ohne Charakter nimmt keinen Schaden - auch nicht den von Vanilla (US1.4, FR-034).
 *
 * <p>Die Regel selbst ist in {@code rpg-core} geprüft: die Pipeline lehnt an {@code NO_HOLDER} ab, weil
 * ein Halter je Charakter entsteht und ein Spieler in der Auswahl keinen hat. Hier geht es um das
 * Stück, das nur auf der Plattform sichtbar ist - dass die <em>Vanilla</em>-Zahl dabei auf null gesetzt
 * wird, statt am abgewiesenen Treffer vorbei durchzulaufen.
 *
 * <p>Das ist die Zusage, auf der ADR-020 ruht: weil der Zustand ohne Charakter nicht spielbar ist,
 * brauchen B04 und B05 keinen „kein Charakter"-Fall. Ginge Vanilla-Schaden durch, wäre genau dieser
 * Verzicht falsch.
 */
class NoCharacterNoDamageTest {

    private static final Logger QUIET = Logger.getLogger("no-character-no-damage-test");

    private ServerMock server;
    private WorldMock world;
    private DefaultStatEngine stats;
    private VanillaDamageListener listener;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        var eventBus = new DefaultEventBus(QUIET);
        stats =
                new DefaultStatEngine(
                        StatConfig.defaults(), new ImmediateScheduler(), eventBus, null, QUIET);
        DefaultCombatPipeline pipeline =
                new DefaultCombatPipeline(
                        CombatConfig.defaults(), stats, eventBus, null, Clock.systemUTC(), QUIET);
        listener = new VanillaDamageListener(pipeline, new VanillaDamageMapping(QUIET), QUIET);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Umgebungsschaden an einem Spieler ohne Charakter wird auf null gesetzt")
    void environmentDamageIsNeutralised() {
        PlayerMock player = server.addPlayer();
        assertThat(stats.findSnapshot(player.getUniqueId()))
                .as("in der Auswahl gibt es keinen Halter - das ist die Voraussetzung")
                .isEmpty();

        EntityDamageEvent event = damage(player, EntityDamageEvent.DamageCause.FALL, 12.0);

        assertThat(event.getDamage()).isZero();
    }

    @Test
    @DisplayName("auch Feuer und Ertrinken kommen nicht durch")
    void otherEnvironmentSourcesAreNeutralisedToo() {
        PlayerMock player = server.addPlayer();

        assertThat(damage(player, EntityDamageEvent.DamageCause.FIRE, 4.0).getDamage()).isZero();
        assertThat(damage(player, EntityDamageEvent.DamageCause.DROWNING, 3.0).getDamage()).isZero();
    }

    @Test
    @DisplayName("ohne Halter entsteht auch keiner - der Treffer legt keinen an")
    void arefusedHitCreatesNoHolder() {
        // Sonst wäre die Zusage von ADR-020 auf dem zweiten Treffer wieder verletzt.
        PlayerMock player = server.addPlayer();

        damage(player, EntityDamageEvent.DamageCause.FALL, 12.0);

        assertThat(stats.findSnapshot(player.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("eine Kreatur mit Halter nimmt weiterhin Schaden - der Verzicht gilt nur ohne Charakter")
    void acreatureWithAHolderStillTakesDamage() {
        // Der Gegentest: würde hier auch nichts ankommen, bewiesen die drei oben nichts über den
        // fehlenden Charakter, sondern nur, dass der Listener alles verschluckt.
        //
        // Feuer, nicht Fall: Fallschaden rechnet aus getFallDistance(), und die ist bei einer gerade
        // gesetzten Kreatur null. Mit FALL wäre dieser Gegentest selbst nichtssagend gewesen.
        LivingEntity zombie =
                (LivingEntity) world.spawnEntity(world.getSpawnLocation(), EntityType.ZOMBIE);
        stats.createForEntity(zombie.getUniqueId());
        var snapshot = stats.recalculateNow(zombie.getUniqueId());
        stats.restoreResources(
                zombie.getUniqueId(),
                rpg.core.stats.ResourcePool.full(
                        snapshot.get(rpg.core.stats.Attribute.HEALTH),
                        snapshot.get(rpg.core.stats.Attribute.MANA)));
        double before = stats.resources(zombie.getUniqueId()).currentHealth();

        damage(zombie, EntityDamageEvent.DamageCause.FIRE, 4.0);

        assertThat(stats.resources(zombie.getUniqueId()).currentHealth()).isLessThan(before);
    }

    @SuppressWarnings({"deprecation", "removal"})
    private EntityDamageEvent damage(
            LivingEntity target, EntityDamageEvent.DamageCause cause, double amount) {
        // Derselbe Konstruktor wie in VanillaDamageListenerTest. Er ist zum Entfernen markiert; der
        // Nachfolger nimmt eine DamageSource, die MockBukkit hier nicht bauen kann.
        EntityDamageEvent event =
                new EntityDamageEvent(
                        target,
                        cause,
                        new java.util.EnumMap<>(
                                java.util.Map.of(EntityDamageEvent.DamageModifier.BASE, amount)),
                        new java.util.EnumMap<>(
                                java.util.Map.of(
                                        EntityDamageEvent.DamageModifier.BASE,
                                        (com.google.common.base.Function<Double, Double>) d -> d)));
        listener.onDamage(event);
        return event;
    }
}
