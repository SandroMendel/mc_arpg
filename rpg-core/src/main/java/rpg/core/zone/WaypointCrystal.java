package rpg.core.zone;

import java.util.Objects;

/**
 * A waypoint crystal - unlocked by a right-click, then a window onto every other crystal (FR-045).
 *
 * <p><b>It carries no destination.</b> Travelling leads to the respawn point of the zone the crystal
 * belongs to (FR-045a). Two coordinates in the same safe core would have meant the same thing twice,
 * and the drift would only have surfaced when somebody stood somewhere else after dying than after
 * travelling.
 *
 * <p><b>The price lives here</b>, with whoever demands it - not in {@code currency.yml} and not in a
 * central catalogue (ADR-027, FR-050c). {@code 0} is allowed and makes travel free, which is the
 * stellschraube if the fee turns out too harsh in play.
 *
 * <p><b>The crystal is built, not placed by this block.</b> It stands as a structure on the map; the
 * configuration only describes the box in which a right-click counts. No block type is compared - if
 * the type carried the detection, travel would depend on nobody mining the stone (research.md R5).
 *
 * @param key identifier, unique across the whole document; player unlocks reference it
 * @param triggerArea where a right-click counts; lies inside the crystal's own zone
 * @param price coins per journey, {@code >= 0}
 */
public record WaypointCrystal(String key, Area triggerArea, long price) {

    public WaypointCrystal {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(triggerArea, "triggerArea");
        if (key.isBlank()) {
            throw new IllegalArgumentException("a crystal key must not be blank");
        }
        if (price < 0L) {
            throw new IllegalArgumentException(
                    "crystal '" + key + "': price must be >= 0, but was " + price);
        }
    }
}
