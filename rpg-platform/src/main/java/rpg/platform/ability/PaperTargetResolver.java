package rpg.platform.ability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import rpg.core.ability.TargetResolver;
import rpg.core.ability.TargetSpec;

/**
 * The Paper half of {@link TargetResolver} - the lookup, not the rules (FR-019 to FR-023).
 *
 * <p><b>Through the spatial index, never over every entity in the world.</b>
 * {@code World#getNearbyEntities} walks the chunk structure and touches only the sections the box
 * intersects; iterating the world's entity list would be the linear scan Principle II forbids, and at
 * 800 mobs it would cost more than the ability itself.
 *
 * <p><b>Nearest first, never at random</b> (FR-021). Where more candidates qualify than the cap
 * allows, the closest ones win - the same situation has to produce the same result, or the behaviour
 * is not testable.
 *
 * <p>US1 implements {@code SELF} and {@code RADIUS}; the other seven follow in US5. An unimplemented
 * mode returns nothing rather than throwing: an ability that finds no target is an ordinary outcome
 * the runtime already handles, and a throw here would take the whole trigger down.
 */
public final class PaperTargetResolver implements TargetResolver {

    private final Server server;

    /**
     * Whether the caster may attack that target - asked, never reimplemented (FR-023).
     *
     * <p>B05 owns the permission rule and B09 will make it per-zone. A copy here would be a second
     * answer to one question, and the two would drift.
     */
    private final BiPredicate<UUID, UUID> mayAttack;

    public PaperTargetResolver(Server server, BiPredicate<UUID, UUID> mayAttack) {
        this.server = Objects.requireNonNull(server, "server");
        this.mayAttack = Objects.requireNonNull(mayAttack, "mayAttack");
    }

    @Override
    public List<UUID> resolve(UUID casterId, TargetSpec spec) {
        Objects.requireNonNull(casterId, "casterId");
        Objects.requireNonNull(spec, "spec");

        return switch (spec.mode()) {
            case SELF -> List.of(casterId);
            case RADIUS -> nearby(casterId, spec);
            // US5 fills these in. Nothing rather than an exception: the runtime treats an empty
            // result as ordinary, and a throw would take down a trigger that already paid its cost.
            default -> List.of();
        };
    }

    private List<UUID> nearby(UUID casterId, TargetSpec spec) {
        Entity caster = server.getEntity(casterId);
        if (caster == null) {
            return List.of();
        }
        Location origin = caster.getLocation();
        double range = spec.range();

        // One box query against the chunk index, then a distance filter: the box is a superset of the
        // sphere, so the corners have to go.
        List<Entity> candidates =
                new ArrayList<>(caster.getWorld().getNearbyEntities(origin, range, range, range));

        double rangeSquared = range * range;
        List<Entity> eligible = new ArrayList<>(candidates.size());
        for (Entity candidate : candidates) {
            if (candidate.getUniqueId().equals(casterId)) {
                continue;
            }
            if (!(candidate instanceof LivingEntity)) {
                continue;
            }
            if (candidate.getLocation().distanceSquared(origin) > rangeSquared) {
                continue;
            }
            if (!mayAttack.test(casterId, candidate.getUniqueId())) {
                continue;
            }
            eligible.add(candidate);
        }

        eligible.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(origin)));

        int cap = Math.min(spec.maxTargets(), eligible.size());
        List<UUID> targets = new ArrayList<>(cap);
        for (int i = 0; i < cap; i++) {
            targets.add(eligible.get(i).getUniqueId());
        }
        return List.copyOf(targets);
    }
}
