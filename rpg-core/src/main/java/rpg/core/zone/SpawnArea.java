package rpg.core.zone;

import java.util.Objects;

/**
 * A named place inside a region's danger zone where B10 will later put its hordes (FR-053).
 *
 * <p><b>A key and a geometry, and nothing else</b> (FR-053a). No role, no type, no creature list, no
 * number, no boss flag. Geometry is this block's trade; role is B10's. A boss area differs from any
 * other area in nothing geometric - it differs in what stands inside it, and that does not live here.
 * Shipping a "boss" kind would have claimed there is exactly one boss per region, which is a
 * statement about creatures this block does not own.
 *
 * <p>Areas <em>are</em> shipped for all six regions, though - empty regions would leave nobody able
 * to demonstrate that the query returns anything (FR-055a). An area without a role is still
 * checkable: it lies inside its region and outside its safe core, and that is verified at start
 * (FR-055).
 *
 * @param key identifier, unique within its zone; the anchor B10 fills
 * @param area where creatures may be placed
 */
public record SpawnArea(String key, Area area) {

    public SpawnArea {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(area, "area");
        if (key.isBlank()) {
            throw new IllegalArgumentException("a spawn area key must not be blank");
        }
    }
}
