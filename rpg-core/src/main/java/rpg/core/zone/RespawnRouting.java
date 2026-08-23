package rpg.core.zone;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import rpg.core.scheduler.WorldPosition;

/**
 * Where a death leads (FR-033, FR-034, FR-037).
 *
 * <p><b>The region the character was last in decides</b>, read from {@link ZonePresence} rather than
 * from a position. By the time a respawn is being routed the player has already been moved off the
 * spot where they died, so asking where they are now would answer the wrong question. The tracker's
 * last placement is the right one, and it is exactly as current as it needs to be - the movement
 * guard only skips chunks in which no border runs.
 *
 * <p><b>Three ways to end up at the fallback point</b>, and all three are ordinary rather than
 * exceptional: dying outside every region, dying in a region that has no safe core, and dying in a
 * region that disappeared from the configuration before the respawn (FR-037). None of them may fail a
 * join, which is why this returns a position in every case instead of an empty optional.
 *
 * <p><b>This hands out a place; it moves nobody</b> (FR-037c). The platform layer sets it on the
 * respawn event.
 */
public final class RespawnRouting {

    private final ZonePresence presence;
    private final Supplier<Zones> zones;

    public RespawnRouting(ZonePresence presence, Supplier<Zones> zones) {
        this.presence = Objects.requireNonNull(presence, "presence");
        this.zones = Objects.requireNonNull(zones, "zones");
    }

    /** Where this holder's character comes back. Never {@code null}. */
    public WorldPosition respawnFor(UUID holderId) {
        Zones current = zones.get();
        String zoneKey = presence.zoneKeyOf(holderId);
        if (zoneKey == null) {
            return current.fallbackPoint();
        }
        return current.respawnPointOf(zoneKey).orElseGet(current::fallbackPoint);
    }

    /**
     * Whether that death happened inside a region with a core of its own.
     *
     * <p>Only used to tell the two messages apart in the log; the player reads the same line either
     * way, because "you died" is true in both cases.
     */
    public boolean insideOwnRegion(UUID holderId) {
        String zoneKey = presence.zoneKeyOf(holderId);
        return zoneKey != null && zones.get().respawnPointOf(zoneKey).isPresent();
    }
}
