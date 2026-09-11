package rpg.core.zone;

import java.util.Objects;

import rpg.core.scheduler.WorldPosition;

/**
 * The protected core of a region - the area around its spawn (FR-008).
 *
 * <p><b>Not a zone.</b> It is an area <em>inside</em> one, overriding single rules. {@code zoneAt}
 * always returns the region and never the core (FR-011); crossing the core boundary fires its own,
 * lighter event and is not a zone change (FR-016). Modelling it as a second zone would have made the
 * zone change fire twelve times instead of six, and every consumer would have had to climb to a
 * parent to find the name and the level band.
 *
 * <p><b>What the core overrides: all damage.</b> Any damage whose target stands here is refused, and
 * any damage whose attacker stands here as well - environmental damage included (FR-028a). Nobody
 * dies in a safe core and nobody strikes out of one, which is why the core is a refuge and not a
 * firing line. What it does <b>not</b> override is the combat state: whoever was hit outside and ran
 * in still counts as in combat for the remaining seconds, with all that follows for regeneration and
 * for ADR-030. Otherwise the safe core would be exactly the place where fleeing pays again.
 *
 * <p><b>The respawn point is the one arrival point of the region</b> and serves three purposes
 * (FR-037d): the start of the game in the start region, death in this region, and the destination of
 * this region's waypoint crystal. One value, one truth - two coordinates in the same core would have
 * drifted apart, and it would only have shown when somebody stood somewhere else after dying than
 * after travelling.
 *
 * @param area where the core is
 * @param respawnPoint the single arrival point of this region; lies inside {@code area}
 */
public record SafeCore(Area area, WorldPosition respawnPoint) {

    public SafeCore {
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(respawnPoint, "respawnPoint");
    }

    /** Whether the respawn point actually lies in the core. Checked at load time. */
    boolean respawnPointIsInside() {
        return area.contains(
                (int) Math.floor(respawnPoint.x()),
                (int) Math.floor(respawnPoint.y()),
                (int) Math.floor(respawnPoint.z()));
    }
}
