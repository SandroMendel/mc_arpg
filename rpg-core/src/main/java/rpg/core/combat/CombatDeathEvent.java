package rpg.core.combat;

import java.util.Optional;
import java.util.UUID;

/**
 * Published exactly once per death (FR-026 to FR-028).
 *
 * <p><b>Not named {@code EntityDeathEvent}</b> - that is Bukkit's own class, which this block's
 * listener imports to suppress vanilla loot and experience. Two types of the same name in one file
 * is a wrong import waiting to happen.
 *
 * <p>{@code killerId} is the last attacker; {@code shares.topContributor()} is the largest one.
 * <b>They are not the same</b>, and that difference is the decision against kill stealing: loot
 * follows the second, not the first.
 *
 * @param victimId who died
 * @param victimCharacterId their character, or {@code null} for a creature
 * @param killerId the last attacker, or {@code null} for an environmental death
 * @param cause why they died
 * @param shares the full split; never {@code null}, but may be empty
 * @param playerVictim whether the victim was a player - B11 applies equipment damage only then
 */
public record CombatDeathEvent(
        UUID victimId,
        UUID victimCharacterId,
        UUID killerId,
        DeathCause cause,
        DamageShare shares,
        boolean playerVictim) {

    public Optional<UUID> killer() {
        return Optional.ofNullable(killerId);
    }

    /** Who gets the loot: the largest contributor, not the last hit (FR-034). */
    public Optional<UUID> lootRecipient() {
        return shares.topContributorId();
    }
}
