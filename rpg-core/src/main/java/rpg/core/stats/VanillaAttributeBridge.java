package rpg.core.stats;

import java.util.UUID;

/**
 * The one place where this block touches the game world (FR-034).
 *
 * <p>An interface in {@code rpg-core} with its implementation in {@code rpg-platform} is what keeps
 * every rule in this block testable without a running server. Without a bridge registered, mirroring
 * simply does nothing - which is exactly what a unit test wants.
 *
 * <p>The Paper implementation runs each call on the tick of the holder it concerns (FR-033,
 * Principle I). Callers here do not need to think about threads.
 */
public interface VanillaAttributeBridge {

    /**
     * Mirrors health to the vanilla bar (ADR-003, FR-030, FR-031).
     *
     * <p>{@code GENERIC_MAX_HEALTH} stays pinned at 20 and the displayed value becomes
     * {@code current / max * 20}. A living holder never shows zero: the smallest displayable step
     * is used instead, because someone at 0.4% health seeing an empty bar believes they are dead.
     */
    void mirrorHealth(UUID holderId, double currentHealth, double maxHealth);

    /** Mirrors the computed attack speed to the vanilla attribute. */
    void mirrorAttackSpeed(UUID holderId, double value);

    /** Mirrors the computed movement speed to the vanilla attribute. */
    void mirrorMovementSpeed(UUID holderId, double value);
}
