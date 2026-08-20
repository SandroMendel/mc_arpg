package rpg.core.combat;

import java.util.UUID;

/**
 * A holder entered or left combat (FR-030e).
 *
 * <p>Leaving is noticed at the next evaluation rather than announced by a timer (FR-030d), so it can
 * arrive a moment after the actual expiry. For the one known consumer - B08's reduced mana
 * regeneration - that is immaterial, because it computes from timestamps itself.
 *
 * @param holderId the holder
 * @param characterId their character, or {@code null} for a creature
 * @param inCombat {@code true} on entering, {@code false} on leaving
 */
public record CombatStateChangedEvent(UUID holderId, UUID characterId, boolean inCombat) {}
