package rpg.core.zone;

import java.util.UUID;

/**
 * Where a holder stands, answered without resolving a position.
 *
 * <p><b>This interface exists because {@code DamagePermission} is handed ids and nothing else.</b> B05
 * asks "may this hurt that" with an attacker id, a target id and two flags - no world, no coordinates.
 * The safe-core rule (FR-028a) is about <em>location</em>, so something has to bridge the two, and the
 * obvious bridge is the wrong one: looking an entity up to get its position would put a lookup on the
 * hottest path in the plugin, once per damage event, with 800 mobs in play.
 *
 * <p><b>The cheap bridge is that the answer is already known.</b> {@link ZoneTracker} keeps the zone
 * and the core flag per character, and updates them whenever they <em>could</em> change - the movement
 * guard only skips chunks in which no border runs, so membership cannot go stale unnoticed. Reading it
 * is two map lookups: holder to character, character to placement. No allocation, no Bukkit, no
 * coordinates.
 *
 * <p><b>What this narrows, and it is worth saying plainly.</b> Only <em>characters</em> have a
 * placement. A mob standing inside a safe core is not covered, so a mob in the core could still hit a
 * player outside it. Refusing that would need the mob's position, which is the lookup this interface
 * exists to avoid - and keeping mobs out of the core is B10's job anyway (FR-028b). The rule as built
 * protects characters in the core and stops them striking out of it, which is what the requirement is
 * for.
 */
public interface ZonePresence {

    /** Whether the character this holder is playing stands in a safe core. */
    boolean inSafeCore(UUID holderId);

    /** The zone key the character this holder is playing was last seen in, or {@code null}. */
    String zoneKeyOf(UUID holderId);
}
