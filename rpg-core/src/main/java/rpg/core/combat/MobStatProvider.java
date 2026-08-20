package rpg.core.combat;

import java.util.Optional;

import rpg.core.stats.ModifierSet;

/**
 * Supplies the attribute values a creature gets when it appears (FR-019c).
 *
 * <p>A bridge, and openly so. B10 owns mob definitions, but B10 does not exist yet - and without
 * values, nothing has a stat holder, so the whole pipeline would apply to nothing but players
 * (FR-018). The block would be finished, fully tested and invisible in the game: the same failure
 * class ADR-012 was written for.
 *
 * <p>B05 therefore ships an implementation that reads numbers from {@code combat.yml}. B10 replaces
 * it through {@link CombatPipeline#setMobStatProvider}. What a mob <i>is</i> - name, behaviour,
 * abilities, loot - was never in scope here and still is not.
 */
public interface MobStatProvider {

    /**
     * The stats for one creature type.
     *
     * @param mobTypeKey the platform's name for the type, upper case
     * @return the contributions, or empty if this type gets no stat holder at all - which is how
     *     peaceful creatures stay outside the combat system (FR-019e)
     */
    Optional<ModifierSet> statsFor(String mobTypeKey);
}
