package rpg.core.combat;

import java.util.UUID;

/**
 * The one place that decides who may damage whom (FR-042, FR-042a).
 *
 * <p>One place on purpose. B09 will later replace this with a rule per zone, and if the decision
 * were spread across the pipeline that replacement would touch every stage instead of one class.
 *
 * <table border="1">
 *   <caption>The shipped rule</caption>
 *   <tr><th>attacker</th><th>target</th><th>allowed</th></tr>
 *   <tr><td>player</td><td>mob</td><td>yes</td></tr>
 *   <tr><td>mob</td><td>player</td><td>yes</td></tr>
 *   <tr><td>player</td><td>player</td><td><b>no</b> (FR-041)</td></tr>
 *   <tr><td>mob</td><td>mob</td><td><b>no</b> (FR-042a)</td></tr>
 *   <tr><td>anyone</td><td>themselves</td><td>yes, but without attribution (FR-035)</td></tr>
 *   <tr><td>environment</td><td>anyone</td><td>yes</td></tr>
 * </table>
 *
 * <p>Mob against mob is refused so that a creeper does not clear the horde around it. The cost is
 * chain reactions; the gain is that the hottest path in the plugin has exactly one permission check
 * and never has to carry a chain of causes across several damage events.
 */
@FunctionalInterface
public interface DamagePermission {

    /**
     * @param attackerId the attacker, or {@code null} for environmental damage
     * @param attackerIsPlayer whether the attacker is a player character
     * @param targetId the target
     * @param targetIsPlayer whether the target is a player character
     */
    boolean isAllowed(UUID attackerId, boolean attackerIsPlayer, UUID targetId, boolean targetIsPlayer);

    /** The shipped rule: no PvP, no mob against mob. */
    static DamagePermission defaultRule() {
        return (attackerId, attackerIsPlayer, targetId, targetIsPlayer) -> {
            if (attackerId == null) {
                return true; // environment hurts everyone
            }
            if (attackerId.equals(targetId)) {
                return true; // self damage is allowed; attribution skips it
            }
            if (attackerIsPlayer && targetIsPlayer) {
                return false; // FR-041 - B09 replaces this line with a per-zone rule
            }
            return attackerIsPlayer || targetIsPlayer; // FR-042a - not mob against mob
        };
    }
}
