package rpg.core.zone;

import java.util.Objects;
import java.util.UUID;

import rpg.core.event.Event;

/**
 * A character entered or left the protected core of a region (FR-016).
 *
 * <p><b>Its own event type, and deliberately not a field on {@link ZoneChangedEvent}.</b> Crossing
 * the core boundary does not change the zone - the character is in the same region before and after.
 * Squeezing it into the zone event would mean publishing "the zone changed" when it did not, and
 * every consumer of zone changes would need a condition to filter those out. Two types cost one file
 * and save every consumer that condition.
 *
 * <p>The core is where nothing spawns, no PvP works, and no damage lands at all (FR-028a). What it
 * does <b>not</b> switch off is the combat state: whoever was hit outside and ran in still counts as
 * in combat for the remaining seconds. So this event says where somebody is, not whether they are
 * safe from the consequences of what they were doing.
 *
 * @param characterId the character that crossed
 * @param zoneKey the region whose core it was - the zone did not change
 * @param entered {@code true} on entering the core, {@code false} on leaving it
 */
public record SafeAreaCrossedEvent(UUID characterId, String zoneKey, boolean entered)
        implements Event {

    public SafeAreaCrossedEvent {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(zoneKey, "zoneKey");
    }

    @Override
    public String publishedByModuleId() {
        return ZoneModule.ID;
    }
}
