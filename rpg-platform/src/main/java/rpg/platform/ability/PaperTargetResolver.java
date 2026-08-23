package rpg.platform.ability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import rpg.core.ability.TargetMode;
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
 * <p>All nine modes are here, and every one ends in the same four steps - box query, filter, nearest
 * first, cap. Getting any of those wrong is invisible until a fight goes strangely, and nine copies
 * would be nine chances to.
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
            case RADIUS -> around(casterId, spec);
            case CONE -> cone(casterId, spec);
            case LINE, LOOK_DIRECTION -> line(casterId, spec);
            case CURSOR, NEAREST -> first(casterId, spec);
            case CHAIN -> chain(casterId, spec);
            case GROUND_AREA -> groundArea(casterId, spec);
        };
    }

    /** Everything inside a cone in the view direction (FR-019). */
    private List<UUID> cone(UUID casterId, TargetSpec spec) {
        Entity caster = server.getEntity(casterId);
        if (caster == null) {
            return List.of();
        }
        Vector facing = caster.getLocation().getDirection().normalize();
        double halfAngle = Math.cos(Math.toRadians(spec.angle()));
        return pick(
                casterId,
                caster.getLocation(),
                spec,
                candidate -> {
                    Vector towards =
                            candidate
                                    .getLocation()
                                    .toVector()
                                    .subtract(caster.getLocation().toVector());
                    return towards.lengthSquared() > 0.0
                            && towards.normalize().dot(facing) >= halfAngle;
                });
    }

    /**
     * Everything along the view direction.
     *
     * <p>{@code LINE} and {@code LOOK_DIRECTION} share this: they differ only in how many they keep,
     * and the cap already says that - a single-target mode carries a cap of one.
     */
    private List<UUID> line(UUID casterId, TargetSpec spec) {
        Entity caster = server.getEntity(casterId);
        if (caster == null) {
            return List.of();
        }
        Location origin = caster.getLocation();
        Vector facing = origin.getDirection().normalize();
        // Half a block of tolerance around the ray. Tighter is unhittable at range, wider stops being
        // a line and becomes a narrow cone.
        double tolerance = 0.5;
        return pick(
                casterId,
                origin,
                spec,
                candidate -> {
                    Vector towards = candidate.getLocation().toVector().subtract(origin.toVector());
                    double along = towards.dot(facing);
                    if (along <= 0.0) {
                        return false;
                    }
                    Vector closest = facing.clone().multiply(along);
                    return towards.distance(closest) <= tolerance + candidate.getWidth() / 2.0;
                });
    }

    /** The single nearest eligible entity - {@code CURSOR} and {@code NEAREST} both land here. */
    private List<UUID> first(UUID casterId, TargetSpec spec) {
        Entity caster = server.getEntity(casterId);
        if (caster == null) {
            return List.of();
        }
        List<UUID> found = pick(casterId, caster.getLocation(), spec, candidate -> true);
        return found.isEmpty() ? List.of() : List.of(found.get(0));
    }

    /**
     * Hops from target to target - the mage's Lightning (FR-019a).
     *
     * <p><b>Each further target is looked for around the last one hit</b>, not around the caster, and
     * nothing is hit twice. That is what separates it from repeating {@code NEAREST}: the origin
     * moves, so a chain travels down a line of mobs that would otherwise all be out of reach.
     */
    private List<UUID> chain(UUID casterId, TargetSpec spec) {
        Entity caster = server.getEntity(casterId);
        if (caster == null) {
            return List.of();
        }
        Set<UUID> hit = new LinkedHashSet<>();
        Location origin = caster.getLocation();
        double firstReach = spec.range();

        for (int hop = 0; hop < spec.maxTargets(); hop++) {
            double reach = hop == 0 ? firstReach : spec.hopRange();
            UUID next = nearestFrom(casterId, origin, reach, hit);
            if (next == null) {
                break;
            }
            hit.add(next);
            Entity found = server.getEntity(next);
            if (found == null) {
                break;
            }
            origin = found.getLocation();
        }
        return List.copyOf(hit);
    }

    /**
     * A patch of ground picked by the crosshair - the mage's Lightning Storm (FR-019b).
     *
     * <p>It anchors and stays: the caster may walk away and the storm keeps raining where it was
     * called down. {@code range} is how far the anchor may be, {@code areaRadius} how wide it is.
     */
    private List<UUID> groundArea(UUID casterId, TargetSpec spec) {
        Entity caster = server.getEntity(casterId);
        if (caster == null) {
            return List.of();
        }
        Location eye =
                caster instanceof LivingEntity living ? living.getEyeLocation() : caster.getLocation();
        org.bukkit.util.Vector direction = eye.getDirection().normalize();

        // AUF DEN BODEN, auf den gezeigt wird - nicht blind die volle Reichweite geradeaus.
        //
        // Vorher wurde der Anker immer genau range Bloecke in Blickrichtung gesetzt. Wer geradeaus
        // sieht, traf damit einen Punkt in Augenhoehe sechzehn Bloecke weit weg; wer auf den Boden
        // vor sich sah, einen Punkt tief IM Boden. In beiden Faellen stand im Umkreis nichts, und
        // der Blitzsturm tat sichtbar gar nichts.
        org.bukkit.util.RayTraceResult hit =
                caster.getWorld()
                        .rayTraceBlocks(
                                eye,
                                direction,
                                spec.range(),
                                org.bukkit.FluidCollisionMode.NEVER,
                                true);
        Location anchor =
                hit == null || hit.getHitPosition() == null
                        // Freie Sicht: das Ende der Reichweite, so wie bisher.
                        ? eye.clone().add(direction.clone().multiply(spec.range()))
                        : hit.getHitPosition().toLocation(caster.getWorld());
        return pick(casterId, anchor, withRadius(spec), candidate -> true);
    }

    /** The same spec seen from the anchor: the area radius becomes the reach. */
    private static TargetSpec withRadius(TargetSpec spec) {
        return new TargetSpec(
                TargetMode.RADIUS, spec.areaRadius(), null, spec.maxTargets(), null, null);
    }

    private UUID nearestFrom(UUID casterId, Location origin, double reach, Set<UUID> exclude) {
        List<Entity> found =
                new ArrayList<>(origin.getWorld().getNearbyEntities(origin, reach, reach, reach));
        double reachSquared = reach * reach;
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity candidate : found) {
            if (candidate.getUniqueId().equals(casterId)
                    || exclude.contains(candidate.getUniqueId())
                    || !(candidate instanceof LivingEntity)
                    || !mayAttack.test(casterId, candidate.getUniqueId())) {
                continue;
            }
            double distance = candidate.getLocation().distanceSquared(origin);
            if (distance <= reachSquared && distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best == null ? null : best.getUniqueId();
    }

    /**
     * The shared body of every area mode: box query, filter, nearest first, cap.
     *
     * <p>Extracted because getting any one of those four wrong is invisible until a fight goes
     * strangely, and four copies would be four chances to.
     */
    private List<UUID> pick(
            UUID casterId,
            Location origin,
            TargetSpec spec,
            java.util.function.Predicate<Entity> extra) {
        double range = spec.range();
        List<Entity> candidates =
                new ArrayList<>(origin.getWorld().getNearbyEntities(origin, range, range, range));

        double rangeSquared = range * range;
        List<Entity> eligible = new ArrayList<>(candidates.size());
        for (Entity candidate : candidates) {
            if (candidate.getUniqueId().equals(casterId)
                    || !(candidate instanceof LivingEntity)
                    || candidate.getLocation().distanceSquared(origin) > rangeSquared
                    || !mayAttack.test(casterId, candidate.getUniqueId())
                    || !extra.test(candidate)) {
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

    /** Everything within reach of the caster. */
    private List<UUID> around(UUID casterId, TargetSpec spec) {
        Entity caster = server.getEntity(casterId);
        return caster == null
                ? List.of()
                : pick(casterId, caster.getLocation(), spec, candidate -> true);
    }
}
