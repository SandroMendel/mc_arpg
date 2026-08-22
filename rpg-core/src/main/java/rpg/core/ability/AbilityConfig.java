package rpg.core.ability;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The validated ability configuration - every definition plus the two things this block owns itself.
 *
 * <p><b>The regeneration rates are not here.</b> They are the attributes {@code healthRegen} and
 * {@code manaRegen} and belong to the character, so they live in {@code classes.yml} (ADR-023). What
 * B08 owns is the global lock and the two factors that reduce those rates during combat.
 *
 * @param abilities every definition, keyed by id
 * @param globalCooldown how long every other active ability is locked after one is triggered
 * @param healthCombatFactor multiplied onto {@code healthRegen} while in combat, within {@code [0,1]}
 * @param manaCombatFactor the same for {@code manaRegen}
 */
public record AbilityConfig(
        Map<String, Ability> abilities,
        Duration globalCooldown,
        double healthCombatFactor,
        double manaCombatFactor) {

    /** Schema version of {@code abilities.yml}; raised when its shape changes incompatibly. */
    public static final int SCHEMA_VERSION = 1;

    public AbilityConfig {
        Objects.requireNonNull(abilities, "abilities");
        Objects.requireNonNull(globalCooldown, "globalCooldown");

        // V1
        if (globalCooldown.isNegative()) {
            throw new IllegalArgumentException(
                    "global-cooldown-ms must not be negative, but was " + globalCooldown.toMillis());
        }
        // V2
        requireFactor("health-combat-factor", healthCombatFactor);
        requireFactor("mana-combat-factor", manaCombatFactor);

        Map<String, Ability> copy = new LinkedHashMap<>();
        abilities.forEach(
                (id, ability) -> {
                    Objects.requireNonNull(id, "ability id");
                    Objects.requireNonNull(ability, "ability " + id);
                    if (!id.equals(ability.id())) {
                        throw new IllegalArgumentException(
                                "definition filed under '" + id + "' calls itself '" + ability.id() + "'");
                    }
                    copy.put(id, ability);
                });
        abilities = Map.copyOf(copy);
    }

    private static void requireFactor(String field, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    field + " must lie within [0, 1], but was " + value);
        }
    }

    /** The definition behind an id, or empty. Looks up; computes nothing (FR-067). */
    public Optional<Ability> find(String abilityId) {
        return Optional.ofNullable(abilities.get(abilityId));
    }

    /**
     * The definition behind an id.
     *
     * @throws UnknownAbilityException if nothing carries that id - never {@code null}, and never a
     *     silently invented ability
     */
    public Ability require(String abilityId) {
        Ability ability = abilities.get(abilityId);
        if (ability == null) {
            throw new UnknownAbilityException(abilityId, abilities.keySet());
        }
        return ability;
    }

    /** How many abilities are defined. For diagnostics and the startup log. */
    public int size() {
        return abilities.size();
    }
}
