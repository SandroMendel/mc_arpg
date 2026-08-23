package rpg.core.zone;

import java.util.Objects;

/**
 * A waypoint crystal together with the region it stands in.
 *
 * <p>The two always travel together, because a crystal carries no destination of its own: travelling
 * leads to the respawn point of <em>this</em> zone (FR-045a). Handing out the crystal alone would
 * force every caller to look its zone up again, and that lookup is exactly where a second source of
 * truth would creep in.
 *
 * @param crystal the crystal
 * @param zone the region it belongs to - the source of the travel destination
 */
public record CrystalPlacement(WaypointCrystal crystal, Zone zone) {

    public CrystalPlacement {
        Objects.requireNonNull(crystal, "crystal");
        Objects.requireNonNull(zone, "zone");
    }

    /** Where a journey to this crystal ends. Present because a crystal requires a safe core. */
    public SafeCore core() {
        return zone.safeCore()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "crystal '"
                                                + crystal.key()
                                                + "' has no safe core in zone '"
                                                + zone.key()
                                                + "' - the schema should have refused that"
                                                + " (FR-051c)"));
    }
}
