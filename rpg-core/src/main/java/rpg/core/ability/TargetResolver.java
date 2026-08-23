package rpg.core.ability;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import rpg.core.scheduler.WorldPosition;

/**
 * Turns a {@link TargetSpec} into the holders an effect will act on (FR-019 to FR-023).
 *
 * <p><b>The rules are here, the lookup is not.</b> Which cone, which cap, which order is a rule and
 * belongs in {@code rpg-core}; asking the world who stands in that cone needs Paper. Same split as
 * {@code MobStatProvider} in B05, with the same benefit: angle, reach, cap and ordering are testable
 * without a server.
 *
 * <p>Two promises every implementation owes:
 *
 * <ul>
 *   <li><b>Never more than the cap</b>, and when more qualify, the nearest ones win - not a random
 *       pick, so the same situation produces the same result (FR-021).
 *   <li><b>Never a target that may not be attacked.</b> The permission rule lives in B05 and is asked,
 *       not reimplemented (FR-023).
 * </ul>
 *
 * <p>The lookup sits in the hot path of every area ability, so an implementation goes through a
 * spatial index rather than iterating every candidate in the world (FR-022, Principle II).
 */
@FunctionalInterface
public interface TargetResolver {

    /**
     * The holders this spec selects, in the order they should be served.
     *
     * @param casterId the holder triggering the ability
     * @param spec what to look for
     * @return at most {@code spec.maxTargets()} ids; empty is an ordinary outcome, not a failure -
     *     an area ability that finds nobody still costs mana and still goes on cooldown
     */
    List<UUID> resolve(UUID casterId, TargetSpec spec);

    /**
     * Where an anchored spec lands - the patch of ground the crosshair picked (FR-019b).
     *
     * <p><b>A place, remembered, so it can be asked again later.</b> Every other mode resolves once
     * and is done; an anchored one has to be answerable a second and a tenth time, because the thing
     * that lasts is the <em>area</em> and not the creatures that happened to stand in it. Lightning
     * Storm without this remembered the mobs instead of the spot: whoever walked out kept taking
     * damage and whoever walked in took none, which from the ground looks exactly like a storm that
     * follows people around.
     *
     * @return empty for a spec that does not anchor, and for a caster who is gone
     */
    default Optional<WorldPosition> anchorFor(UUID casterId, TargetSpec spec) {
        return Optional.empty();
    }

    /**
     * The same selection as {@link #resolve}, but around a place instead of around a caster.
     *
     * <p>Used by everything that acts late: the storm on every tick, the clone's farewell where it
     * stood, the leap's impact where it landed. The caster may be far away by then, or gone - and is
     * still named, because whose storm it is decides who it may hit (FR-023).
     */
    default List<UUID> resolveAt(UUID casterId, WorldPosition anchor, TargetSpec spec) {
        return List.of();
    }

    /** Where this entity is right now, if it still exists. For remembering a spot. */
    default Optional<WorldPosition> positionOf(UUID entityId) {
        return Optional.empty();
    }

    /** Selects nothing. The default until the platform installs the real one. */
    static TargetResolver none() {
        return (casterId, spec) -> List.of();
    }
}
