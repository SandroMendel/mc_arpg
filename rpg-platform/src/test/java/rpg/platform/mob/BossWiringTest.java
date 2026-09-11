package rpg.platform.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.mob.BossSpec;
import rpg.core.mob.Budget;
import rpg.core.mob.HordeRegistry;
import rpg.core.mob.HordeSpec;
import rpg.core.mob.MobConfig;
import rpg.core.mob.MobKind;
import rpg.core.stats.Attribute;
import rpg.core.zone.Area;
import rpg.core.zone.Cuboid;
import rpg.core.zone.LevelBand;
import rpg.core.zone.SpawnArea;
import rpg.core.zone.Zone;
import rpg.core.zone.ZonePresence;

/**
 * US5 durchgehend verdrahtet: {@link HordeSweep} setzt einen Boss, wenn keiner lebt (FR-029),
 * merkt sich seinen Tod ueber dasselbe {@code EntityDeathEvent} wie jede andere Kreatur (T082),
 * und ein Aufraeumen ohne Tod laesst seinen Timer unberuehrt (T083, FR-034). Kein Task in
 * tasks.md verlangt diesen Test ausdruecklich - er sichert die Verdrahtung ab, die T081-T083
 * sonst ungeprueft liesse (vgl. "Wann ein Block fertig ist").
 */
class BossWiringTest {

    private ServerMock server;
    private WorldMock world;
    private FakeMobScheduler scheduler;
    private MutableClock clock;
    private HordeRegistry registry;
    private Map<String, rpg.core.mob.BossState> bossStates;
    private FakeZones zones;
    private MobConfig config;
    private HordeSweep sweep;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        clock = new MutableClock(Instant.parse("2026-08-26T20:00:00Z"));
        scheduler = new FakeMobScheduler(clock);
        registry = new HordeRegistry();
        bossStates = new java.util.HashMap<>();
        Zone zone = zoneFixture();
        zones = new FakeZones(zone);
        config = configFixture();
        PaperMobPlacer placer = new PaperMobPlacer(Logger.getLogger("test"));
        ZonePresence zonePresence = new FakeZonePresence(server, zones);
        sweep =
                new HordeSweep(
                        server,
                        scheduler,
                        () -> zones,
                        zonePresence,
                        () -> config,
                        registry,
                        bossStates,
                        holderId -> false,
                        placer,
                        clock,
                        Logger.getLogger("test"));
        server.getPluginManager().registerEvents(sweep, MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("ein Spieler in der Zone laesst den Boss erscheinen (FR-029, FR-030)")
    void aPlayerInTheZoneMakesTheBossAppear() {
        addPlayerInZone();

        sweep.ensureScheduledForPopulatedZones();

        List<HordeRegistry.Entry> bosses = bossEntries();
        assertThat(bosses).as("genau ein Boss steht").hasSize(1);
        assertThat(bosses.get(0).kindKey()).isEqualTo("greenfields.warden-of-the-field");
    }

    @Test
    @DisplayName(
            "Hordes.bossOf sieht denselben Boss wie HordeSweep - die geliehene Karte ist wirklich dieselbe (contracts/mob-api.md §3)")
    void hordesBossOfSeesTheSameBossHordeSweepPlaced() {
        // Genau die Verdrahtung, die RpgPlugin herstellt: MobModule.bosses() geht an HordeSweep UND
        // an Hordes.backedBy - hier nachgebaut mit derselben bossStates-Instanz aus setUp().
        rpg.core.mob.Hordes hordes = rpg.core.mob.Hordes.backedBy(registry, bossStates);
        addPlayerInZone();

        sweep.ensureScheduledForPopulatedZones();

        assertThat(hordes.bossOf("greenfields"))
                .as("mit einer eigenen statt der geliehenen Karte waere das immer leer geblieben")
                .isEqualTo(Optional.of(bossEntries().get(0).entityId()));
    }

    @Test
    @DisplayName("solange der Boss lebt, erscheint kein zweiter, egal wie viele Durchlaeufe folgen")
    void whileTheBossIsAliveNoSecondOneAppearsNoMatterHowManySweepsFollow() {
        addPlayerInZone();
        sweep.ensureScheduledForPopulatedZones();
        assertThat(bossEntries()).hasSize(1);

        for (int i = 0; i < 5; i++) {
            scheduler.runNextDelayed();
        }

        assertThat(bossEntries()).as("immer noch genau einer").hasSize(1);
    }

    @Test
    @DisplayName("nach dem Tod des Bosses erscheint vor Ablauf des Respawn-Timers kein neuer (FR-031)")
    void afterTheBossDiesNoNewOneAppearsBeforeTheRespawnTimerRunsOut() {
        addPlayerInZone();
        sweep.ensureScheduledForPopulatedZones();
        var bossEntity = server.getEntity(bossEntries().get(0).entityId());

        killBoss(bossEntity);
        assertThat(bossEntries()).as("er ist weg").isEmpty();

        for (int i = 0; i < 5; i++) {
            scheduler.runNextDelayed();
        }

        assertThat(bossEntries())
                .as("die Respawnzeit ist 30 Minuten, fuenf Durchlaeufe a 100ms reichen nicht")
                .isEmpty();
    }

    @Test
    @DisplayName("nach Ablauf des Respawn-Timers erscheint ein neuer Boss")
    void afterTheRespawnTimerRunsOutANewBossAppears() {
        addPlayerInZone();
        sweep.ensureScheduledForPopulatedZones();
        var bossEntity = server.getEntity(bossEntries().get(0).entityId());
        killBoss(bossEntity);

        clock.advance(Duration.ofMinutes(31));
        scheduler.runNextDelayed();

        assertThat(bossEntries()).as("die Wartezeit ist vorbei").hasSize(1);
    }

    @Test
    @DisplayName("aufgeraeumt ist nicht gefallen - nach einem Aufraeumen ohne Tod darf sofort ein neuer erscheinen (FR-034)")
    void cleanedUpIsNotKilledANewBossMayAppearImmediately() {
        PlayerMock player = addPlayerInZone();
        sweep.ensureScheduledForPopulatedZones();
        var placedId = bossEntries().get(0).entityId();

        // Der Spieler bleibt in der Zone, wandert aber weit weg vom Boss - weit ueber die
        // Aufraeumreichweite hinaus. Das ist Aufraeumen (FR-020), kein Tod.
        player.teleport(new org.bukkit.Location(world, 900, 70, 900));
        scheduler.runNextDelayed();

        assertThat(registry.find(placedId)).as("der alte Boss ist weg").isNull();
        List<HordeRegistry.Entry> bosses = bossEntries();
        assertThat(bosses)
                .as("kein Timer lief - ein neuer durfte im selben Durchlauf schon wieder erscheinen")
                .hasSize(1);
        assertThat(bosses.get(0).entityId()).isNotEqualTo(placedId);
    }

    private void killBoss(org.bukkit.entity.Entity bossEntity) {
        var damageSource =
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build();
        var event =
                new org.bukkit.event.entity.EntityDeathEvent(
                        (org.bukkit.entity.LivingEntity) bossEntity, damageSource, new ArrayList<>());
        server.getPluginManager().callEvent(event);
        var removeEvent =
                new org.bukkit.event.entity.EntityRemoveEvent(
                        bossEntity, org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH);
        server.getPluginManager().callEvent(removeEvent);
    }

    private List<HordeRegistry.Entry> bossEntries() {
        List<HordeRegistry.Entry> bosses = new ArrayList<>();
        for (HordeRegistry.Entry entry : registry.all()) {
            if (entry.kindKey().equals("greenfields.warden-of-the-field")) {
                bosses.add(entry);
            }
        }
        return bosses;
    }

    private PlayerMock addPlayerInZone() {
        PlayerMock player = server.addPlayer();
        player.teleport(new org.bukkit.Location(world, 200, 70, 0));
        return player;
    }

    private Zone zoneFixture() {
        SpawnArea east = new SpawnArea("greenfields-east", Area.of(Cuboid.of(100, -50, 300, 50)));
        return new Zone(
                "greenfields",
                world.getUID(),
                Area.of(Cuboid.of(-1000, -1000, 1000, 1000)),
                new LevelBand(1, 10),
                Optional.empty(),
                List.of(east),
                Optional.empty(),
                false,
                false);
    }

    private MobConfig configFixture() {
        MobKind rotling =
                new MobKind(
                        "greenfields.rotling",
                        "ZOMBIE",
                        3,
                        Map.of(Attribute.HEALTH, 40.0),
                        24.0,
                        rpg.core.message.MessageKey.of("mob.greenfields.rotling.name"),
                        12L,
                        4L,
                        false);
        MobKind boss =
                new MobKind(
                        "greenfields.warden-of-the-field",
                        "ZOMBIE",
                        10,
                        Map.of(Attribute.HEALTH, 600.0),
                        32.0,
                        rpg.core.message.MessageKey.of("mob.greenfields.warden.name"),
                        400L,
                        250L,
                        true);
        BossSpec bossSpec =
                new BossSpec(
                        "greenfields.warden-of-the-field",
                        "greenfields-east",
                        0.0,
                        0.0,
                        0.0,
                        Duration.ofMinutes(30));
        HordeSpec horde =
                new HordeSpec(
                        "greenfields",
                        List.of(new HordeSpec.Entry("greenfields-east", "greenfields.rotling", 1)),
                        bossSpec);
        return new MobConfig(
                new Budget(50, 20, 20, 20),
                Duration.ofMillis(100),
                0.0,
                Duration.ofMillis(250),
                96.0,
                Duration.ofMillis(500),
                Map.of(rotling.key(), rotling, boss.key(), boss),
                Map.of("greenfields", horde));
    }
}
