package rpg.core.zone;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import rpg.core.scheduler.WorldPosition;

/**
 * The validated contents of {@code zones.yml}.
 *
 * <p>Zones themselves are <b>not</b> persisted - they live here, in configuration, and a character's
 * membership is worked out from their position at runtime and never written down (data-model.md §6).
 * Storing the current zone would have meant maintaining two truths.
 *
 * @param provisional whether the coordinates are placeholders; drives the start warning (FR-065a)
 * @param fallbackPoint where a death outside every zone leads (FR-034)
 * @param zones the regions, in configuration order
 * @param warningCooldown how long the same border stays quiet after warning once (FR-024)
 * @param combatLogoutIsDeath whether logging out in combat kills the character (FR-042, ADR-030)
 */
public record ZoneConfig(
        boolean provisional,
        WorldPosition fallbackPoint,
        List<Zone> zones,
        Duration warningCooldown,
        boolean combatLogoutIsDeath) {

    public ZoneConfig {
        Objects.requireNonNull(fallbackPoint, "fallbackPoint");
        Objects.requireNonNull(zones, "zones");
        Objects.requireNonNull(warningCooldown, "warningCooldown");
        zones = List.copyOf(zones);
    }

    /** The region new characters appear in (FR-037b). Exactly one is guaranteed by the schema. */
    public Zone startRegion() {
        for (Zone zone : zones) {
            if (zone.startRegion()) {
                return zone;
            }
        }
        throw new IllegalStateException(
                "no start region - the schema should have refused this document (FR-037a)");
    }
}
