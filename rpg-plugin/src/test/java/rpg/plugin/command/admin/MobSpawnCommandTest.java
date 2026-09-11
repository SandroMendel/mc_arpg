package rpg.plugin.command.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import rpg.core.mob.Budget;
import rpg.core.mob.HordeRegistry;
import rpg.core.mob.MobConfig;
import rpg.core.mob.MobKind;
import rpg.core.mob.MobKinds;
import rpg.core.mob.NearbyChunks;
import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.persistence.AuditEntry;
import rpg.core.persistence.AuditLogRepository;
import rpg.core.stats.Attribute;
import rpg.core.zone.Zones;
import rpg.plugin.command.framework.AdminAudit;
import rpg.plugin.command.framework.CommandContext;
import rpg.plugin.command.framework.RpgCommand;

/** T075–T077 — the admin spawn leaf resolves a kind, records the entity and audits the action. */
class MobSpawnCommandTest {

    private static final Instant WHEN = Instant.parse("2026-09-11T12:00:00Z");

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private HordeRegistry registry;
    private MobKind kind;
    private RecordingAuditLog auditLog;
    private MobConfig config;
    private boolean placerCalled;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Ticoo");
        player.teleport(new Location(world, 32.5, 65, 48.5));
        registry = new HordeRegistry();
        kind = kind();
        auditLog = new RecordingAuditLog();
        config = config(20);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("/rpg mob spawn setzt eine Art als ADMIN in den gemeinsamen Bestand")
    void spawnUsesAdminOriginAndWritesAudit() {
        MobSpawnCommand command = command("greenfields", 20);
        RpgCommand spawn = command.definition().children().get(0);

        spawn.action().accept(context(player, Map.of("kind", kind.key())));

        HordeRegistry.Entry entry = registry.find(player.getUniqueId());
        assertThat(entry).isNotNull();
        assertThat(entry.origin()).isEqualTo(HordeRegistry.Origin.ADMIN);
        assertThat(entry.kindKey()).isEqualTo(kind.key());
        assertThat(entry.zoneKey()).isEqualTo("greenfields");
        assertThat(registry.total()).isZero();
        assertThat(registry.countAdmin()).isEqualTo(1);
        assertThat(registry.countInChunk(NearbyChunks.packBlock(32, 48))).isEqualTo(1);
        assertThat(placerCalled).isTrue();
        assertThat(player.nextMessage()).contains("Spawned").contains(kind.key()).contains("greenfields");

        assertThat(auditLog.entries).singleElement().satisfies(entryAudit -> {
            assertThat(entryAudit.action()).isEqualTo("mob_spawned");
            assertThat(entryAudit.targetPlayerId()).isEmpty();
            assertThat(entryAudit.details())
                    .containsEntry("kind", kind.key())
                    .containsEntry("zone", "greenfields")
                    .containsEntry("origin", "ADMIN");
        });
    }

    @Test
    @DisplayName("die Admin-Grenze bricht vor dem eigentlichen Spawn ab")
    void theAdminLimitStopsTheSpawn() {
        registry.add(
                new HordeRegistry.Entry(
                        UUID.randomUUID(),
                        kind.key(),
                        "greenfields",
                        NearbyChunks.packBlock(32, 48),
                        WHEN,
                        HordeRegistry.Origin.ADMIN));
        MobSpawnCommand command = command("greenfields", 1);
        RpgCommand spawn = command.definition().children().get(0);

        spawn.action().accept(context(player, Map.of("kind", kind.key())));

        assertThat(placerCalled).isFalse();
        assertThat(registry.countAdmin()).isEqualTo(1);
        assertThat(player.nextMessage()).contains("limit").contains("1");
        assertThat(auditLog.entries).isEmpty();
    }

    @Test
    @DisplayName("ausserhalb einer Zone entsteht keine Kreatur")
    void spawningOutsideAZoneIsRejected() {
        MobSpawnCommand command = command(null, 20);
        RpgCommand spawn = command.definition().children().get(0);

        spawn.action().accept(context(player, Map.of("kind", kind.key())));

        assertThat(placerCalled).isFalse();
        assertThat(registry.all()).isEmpty();
        assertThat(player.nextMessage()).contains("zone");
        assertThat(auditLog.entries).isEmpty();
    }

    @Test
    @DisplayName("die Baumform enthält genau die spielergebundene Spawn-Form")
    void theDefinitionHasTheExpectedShape() {
        RpgCommand mob = command("greenfields", 20).definition();
        RpgCommand spawn = mob.children().get(0);

        assertThat(mob.name()).isEqualTo("mob");
        assertThat(mob.permissionOrNone()).isEmpty();
        assertThat(spawn.name()).isEqualTo("spawn");
        assertThat(spawn.permissionOrNone()).contains(MobSpawnCommand.PERMISSION);
        assertThat(spawn.requiresPlayer()).isTrue();
        assertThat(spawn.arguments()).extracting(rpg.plugin.command.framework.Argument::name)
                .containsExactly("kind");
    }

    private MobSpawnCommand command(String zoneKey, int limit) {
        config = config(limit);
        return new MobSpawnCommand(
                mobKinds(),
                registry,
                () -> config,
                () -> zones(zoneKey),
                (selected, location, originZone) -> {
                    placerCalled = true;
                    return Optional.of(player);
                },
                new AdminAudit(
                        auditLog,
                        Clock.fixed(WHEN, ZoneOffset.UTC)),
                messages(),
                Clock.fixed(WHEN, ZoneOffset.UTC));
    }

    private MobKinds mobKinds() {
        return new MobKinds() {
            @Override
            public Optional<MobKind> find(String kindKey) {
                return kind.key().equals(kindKey) ? Optional.of(kind) : Optional.empty();
            }

            @Override
            public Optional<MobKind> ofEntity(UUID entityId) {
                return Optional.empty();
            }

            @Override
            public List<MobKind> all() {
                return List.of(kind);
            }
        };
    }

    private MobConfig config(int adminLimit) {
        return new MobConfig(
                new Budget(50, 20, 10, 5),
                Duration.ofSeconds(5),
                0.2,
                Duration.ofSeconds(60),
                96.0,
                Duration.ofMillis(500),
                Map.of(kind.key(), kind),
                Map.of(),
                adminLimit);
    }

    private static MobKind kind() {
        return new MobKind(
                "greenfields.rotling",
                "ZOMBIE",
                3,
                Map.of(Attribute.HEALTH, 40.0),
                24.0,
                MessageKey.of("mob.greenfields.rotling.name"),
                12L,
                4L,
                false);
    }

    private static Zones zones(String zoneKey) {
        return (Zones)
                Proxy.newProxyInstance(
                        Zones.class.getClassLoader(),
                        new Class<?>[] {Zones.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("zoneKeyAt")) {
                                return zoneKey;
                            }
                            if (method.getReturnType() == boolean.class) {
                                return false;
                            }
                            if (method.getReturnType() == Optional.class) {
                                return Optional.empty();
                            }
                            if (method.getReturnType() == List.class) {
                                return List.of();
                            }
                            if (method.getName().equals("toString")) {
                                return "test-zones";
                            }
                            return null;
                        });
    }

    private static Messages messages() {
        return new MapMessages(
                Map.of(
                        "command.mob.description", "Mob administration",
                        "command.mob.spawn.description", "Spawn a mob",
                        "command.mob.spawn.done", "Spawned {kind} in {zone}.",
                        "command.mob.spawn.no-zone", "No configured zone here.",
                        "command.mob.spawn.limit", "Admin mob limit reached: {limit}.",
                        "command.mob.spawn.failed", "Mob could not be spawned.",
                        "command.error.unknown-key", "Unknown key {key}.",
                        "command.error.needs-player", "Players only."));
    }

    private static CommandContext context(CommandSender sender, Map<String, Object> values) {
        try {
            Constructor<CommandContext> constructor =
                    CommandContext.class.getDeclaredConstructor(CommandSender.class, Map.class);
            constructor.setAccessible(true);
            return constructor.newInstance(sender, values);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("test context could not be built", failure);
        }
    }

    private static final class RecordingAuditLog implements AuditLogRepository {

        private final List<AuditEntry> entries = new ArrayList<>();

        @Override
        public void append(AuditEntry entry) {
            entries.add(entry);
        }

        @Override
        public CompletableFuture<List<AuditEntry>> between(Instant from, Instant to) {
            return CompletableFuture.completedFuture(List.copyOf(entries));
        }
    }
}
