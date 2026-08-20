package rpg.core.combat;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * The public interface of B05 - the only way in.
 *
 * <p>B06 to B13 are built against this. Reaching past it into {@link DamageContext},
 * {@link AttributionWindow} or the platform listeners is not allowed (Principle III), and changing
 * anything here is ADR-worthy from now on.
 *
 * <h2>Two promises later blocks may rely on</h2>
 *
 * <p><b>The damage factor is an ability's only lever.</b> B08 states a number - 1.8 for "180% of
 * magic damage" - and gets scaling with equipment and level for free, without reading a single
 * attribute itself.
 *
 * <p><b>The combat state is here and nowhere else.</b> B08, B12 and B13 ask, rather than each
 * keeping their own counter.
 */
public interface CombatPipeline {

    // ------------------------------------------------------------- dealing damage

    /** A melee swing: physical, factor 1.0, subject to the attack window (FR-021). */
    DamageResult meleeAttack(UUID attackerId, UUID targetId);

    /**
     * Damage from an ability (B08).
     *
     * <p><b>Not</b> subject to the attack window: abilities have their own cooldowns in B08, and
     * checking both would limit them twice.
     *
     * @param factor share of the base attribute; 1.8 means 180% (FR-002a)
     */
    DamageResult abilityDamage(UUID attackerId, UUID targetId, DamageType type, double factor);

    /**
     * Damage from a projectile, computed from the shooter's values <b>at launch</b> (FR-024b).
     *
     * @param rawDamage the amount worked out when the projectile was fired
     */
    DamageResult projectileDamage(UUID shooterId, UUID targetId, double rawDamage);

    /** Environmental damage: a fixed amount, and defence does not apply (FR-012a, FR-012b). */
    DamageResult environmentDamage(UUID targetId, EnvironmentSource source);

    /** Fall damage, worked out from the height fallen (FR-012c). */
    DamageResult fallDamage(UUID targetId, double fallenBlocks);

    /** Immediate death without formula and without attribution - for {@code /kill} and the void. */
    void kill(UUID targetId, DeathCause cause);

    // ------------------------------------------------------------- intercepting

    /** Attaches an interception point to one stage (FR-008). Registered at startup, not mid-fight. */
    void registerInterceptor(DamageInterceptor interceptor);

    // ------------------------------------------------------------- reading

    /** Whether this holder is in combat right now (FR-030c). */
    boolean isInCombat(UUID holderId);

    /** How much longer, or empty if not in combat. */
    Optional<Duration> remainingCombatTime(UUID holderId);

    /** Whether an attack would count right now, without performing it. */
    boolean canAttackNow(UUID attackerId);

    /** The current split for a target - for display and for debugging. Computes nothing. */
    Optional<DamageShare> currentShares(UUID targetId);

    // ------------------------------------------------------------- registration

    /** Replaces the mob stat supply. B10 calls this at startup (FR-019c). */
    void setMobStatProvider(MobStatProvider provider);

    /** Installs hurt animation and knockback. Without one, feedback simply does nothing (FR-037). */
    void registerFeedback(DamageFeedback feedback);

    /** Replaces the permission rule. B09 calls this to make it per-zone (FR-042). */
    void setPermission(DamagePermission permission);

    // ------------------------------------------------------------- lifecycle

    /** Drops everything held for a holder - on logout, or when a creature is removed (FR-036). */
    void forget(UUID holderId);

    /** Marks a target alive again after a respawn, so it can take damage once more. */
    void clearDeathMark(UUID targetId);
}
