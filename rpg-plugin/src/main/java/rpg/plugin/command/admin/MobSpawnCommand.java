package rpg.plugin.command.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.mob.HordeRegistry;
import rpg.core.mob.MobConfig;
import rpg.core.mob.MobKind;
import rpg.core.mob.MobKinds;
import rpg.core.mob.NearbyChunks;
import rpg.core.zone.Zones;
import rpg.platform.mob.PaperMobPlacer;
import rpg.platform.zone.BukkitPositions;
import rpg.plugin.command.CommandMessageKeys;
import rpg.plugin.command.framework.AdminAudit;
import rpg.plugin.command.framework.Argument;
import rpg.plugin.command.framework.Arguments;
import rpg.plugin.command.framework.CommandContext;
import rpg.plugin.command.framework.RpgCommand;

/** {@code /rpg mob spawn <kind>} (US5, FR-018–FR-019b). */
public final class MobSpawnCommand {

    public static final String PERMISSION = "rpg.admin.mob.spawn";

    private final MobKinds kinds;
    private final HordeRegistry registry;
    private final Supplier<MobConfig> config;
    private final Supplier<Zones> zones;
    private final EntityPlacer placer;
    private final AdminAudit audit;
    private final Messages messages;
    private final Clock clock;

    /** Production constructor: the actual Paper placer remains the one spawn path. */
    public MobSpawnCommand(
            MobKinds kinds,
            HordeRegistry registry,
            Supplier<MobConfig> config,
            Supplier<Zones> zones,
            PaperMobPlacer placer,
            AdminAudit audit,
            Messages messages,
            Clock clock) {
        this(kinds, registry, config, zones, placer::place, audit, messages, clock);
    }

    /** Package-private seam for command tests; production still uses {@link PaperMobPlacer}. */
    MobSpawnCommand(
            MobKinds kinds,
            HordeRegistry registry,
            Supplier<MobConfig> config,
            Supplier<Zones> zones,
            EntityPlacer placer,
            AdminAudit audit,
            Messages messages,
            Clock clock) {
        this.kinds = Objects.requireNonNull(kinds, "kinds");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.config = Objects.requireNonNull(config, "config");
        this.zones = Objects.requireNonNull(zones, "zones");
        this.placer = Objects.requireNonNull(placer, "placer");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The {@code mob} branch that is attached below {@code /rpg}. */
    public RpgCommand definition() {
        return RpgCommand.branch(
                "mob", MobSpawnMessageKeys.DESCRIPTION, null, List.of(spawnDefinition()));
    }

    private RpgCommand spawnDefinition() {
        Argument<String> kind = Argument.required("kind", Arguments.mobKind(kinds));
        return RpgCommand.playerLeaf(
                "spawn",
                MobSpawnMessageKeys.SPAWN_DESCRIPTION,
                PERMISSION,
                List.of(kind),
                context -> spawn(context, kind));
    }

    private void spawn(CommandContext context, Argument<String> kindArgument) {
        Optional<Player> player = context.player();
        if (player.isEmpty()) {
            tell(context.sender(), CommandMessageKeys.NEEDS_PLAYER, Map.of());
            return;
        }

        String kindKey = context.get(kindArgument);
        Optional<MobKind> kind = kinds.find(kindKey);
        if (kind.isEmpty()) {
            // A reload may have removed a kind after Brigadier accepted the argument.
            tell(context.sender(), CommandMessageKeys.UNKNOWN_KEY, Map.of("key", kindKey));
            return;
        }

        Location location = player.get().getLocation();
        if (location.getWorld() == null) {
            tell(context.sender(), MobSpawnMessageKeys.NO_ZONE, Map.of());
            return;
        }
        String zoneKey = zones.get().zoneKeyAt(BukkitPositions.of(location));
        if (zoneKey == null || zoneKey.isBlank()) {
            tell(context.sender(), MobSpawnMessageKeys.NO_ZONE, Map.of());
            return;
        }

        int limit = config.get().adminSpawnLimit();
        if (registry.countAdmin() >= limit) {
            tell(context.sender(), MobSpawnMessageKeys.LIMIT, Map.of("limit", String.valueOf(limit)));
            return;
        }

        Optional<Entity> placed;
        try {
            placed = placer.place(kind.get(), location, zoneKey);
        } catch (RuntimeException failure) {
            placed = Optional.empty();
        }
        if (placed.isEmpty()) {
            tell(context.sender(), MobSpawnMessageKeys.FAILED, Map.of());
            return;
        }

        Entity entity = placed.get();
        registry.add(
                new HordeRegistry.Entry(
                        entity.getUniqueId(),
                        kind.get().key(),
                        zoneKey,
                        NearbyChunks.packBlock(location.getBlockX(), location.getBlockZ()),
                        Instant.now(clock),
                        HordeRegistry.Origin.ADMIN));
        audit.record(
                context.sender(),
                "mob_spawned",
                Map.of(
                        "entity", entity.getUniqueId().toString(),
                        "kind", kind.get().key(),
                        "zone", zoneKey,
                        "origin", HordeRegistry.Origin.ADMIN.name()));
        tell(
                context.sender(),
                MobSpawnMessageKeys.DONE,
                Map.of("kind", kind.get().key(), "zone", zoneKey));
    }

    private void tell(CommandSender sender, MessageKey key, Map<String, String> placeholders) {
        sender.sendMessage(messages.get(key, placeholders));
    }

    @FunctionalInterface
    interface EntityPlacer {
        Optional<Entity> place(MobKind kind, Location location, String zoneKey);
    }
}
