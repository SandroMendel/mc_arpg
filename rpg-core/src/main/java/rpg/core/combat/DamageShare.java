package rpg.core.combat;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Who contributed how much, worked out at the moment of death (FR-034).
 *
 * <p>Shares sum to 1.0. The top contributor is <b>not</b> necessarily the one who landed the last
 * hit - that distinction is the whole decision against kill stealing: XP is split by share because
 * XP divides, loot goes to the largest contributor because a sword does not.
 *
 * @param shares fraction per attacker; may be empty when nothing a player did was involved
 * @param topContributor largest contributor, or {@code null} for an empty split
 * @param totalDamage the damage the shares were computed from
 */
public record DamageShare(Map<UUID, Double> shares, UUID topContributor, double totalDamage) {

    private static final DamageShare EMPTY = new DamageShare(Map.of(), null, 0.0);

    public DamageShare {
        shares = Map.copyOf(Objects.requireNonNull(shares, "shares"));
    }

    /** Nobody contributed - a creature that burned to death in lava, for instance. */
    public static DamageShare empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return shares.isEmpty();
    }

    /** The largest contributor, if there is one. Loot goes here (FR-034). */
    public Optional<UUID> topContributorId() {
        return Optional.ofNullable(topContributor);
    }

    /** One attacker's share, or zero if they were not involved. */
    public double shareOf(UUID attackerId) {
        return shares.getOrDefault(attackerId, 0.0);
    }
}
