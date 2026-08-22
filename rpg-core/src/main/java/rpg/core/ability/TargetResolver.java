package rpg.core.ability;

import java.util.List;
import java.util.UUID;

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

    /** Selects nothing. The default until the platform installs the real one. */
    static TargetResolver none() {
        return (casterId, spec) -> List.of();
    }
}
