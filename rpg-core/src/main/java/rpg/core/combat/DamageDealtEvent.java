package rpg.core.combat;

import java.util.Optional;
import java.util.UUID;

/**
 * Aggregated damage for display (FR-038, FR-040).
 *
 * <p>One event per attacker-target pair per window, not one per hit. Twenty hits inside the window
 * arrive as a single event with {@code hitCount == 20}; at 150 players against 800 mobs the
 * unaggregated alternative would be thousands of events per second for B13 to draw.
 *
 * <p>The aggregation covers the <em>number</em> only. The hurt animation fires per hit (FR-037) -
 * combat would feel broken otherwise.
 *
 * @param attackerId the attacker, or {@code null} for environmental damage
 * @param targetId the target
 * @param type which kind of damage
 * @param totalDamage sum over the window, <b>after</b> defence
 * @param hitCount how many hits went into it
 * @param lethal whether the window ended because the target died
 */
public record DamageDealtEvent(
        UUID attackerId,
        UUID targetId,
        DamageType type,
        double totalDamage,
        int hitCount,
        boolean lethal) {

    public Optional<UUID> attacker() {
        return Optional.ofNullable(attackerId);
    }
}
