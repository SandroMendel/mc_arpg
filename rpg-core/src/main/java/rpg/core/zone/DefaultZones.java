package rpg.core.zone;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import rpg.core.scheduler.WorldPosition;

/**
 * {@link Zones} over a {@link ChunkZoneIndex}.
 *
 * <p><b>Holds no mutable state.</b> The index and the lookup table are built once and only read
 * afterwards (Constitution I). A reload builds a whole new instance and swaps it in - see
 * {@link ZoneModule}.
 *
 * <p>Positions arrive as {@link WorldPosition} with {@code double} coordinates, because that is what
 * a player position is. Block containment floors them: standing at {@code x = 100.9} is standing in
 * block 100, which is what a cuboid bound of {@code max-x: 100} is meant to include.
 */
public final class DefaultZones implements Zones {

    private final ChunkZoneIndex index;
    private final Map<String, Zone> byKey;
    private final WorldPosition fallbackPoint;
    private final WorldPosition startPoint;

    public DefaultZones(ZoneConfig config) {
        Objects.requireNonNull(config, "config");
        this.index = ChunkZoneIndex.build(config.zones());
        Map<String, Zone> keys = new LinkedHashMap<>();
        for (Zone zone : config.zones()) {
            keys.put(zone.key(), zone);
        }
        this.byKey = Map.copyOf(keys);
        this.fallbackPoint = config.fallbackPoint();
        Zone start = config.startRegion();
        this.startPoint =
                start.safeCore()
                        .map(SafeCore::respawnPoint)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "the start region '"
                                                        + start.key()
                                                        + "' needs a safe-core, because a new"
                                                        + " character appears at its respawn-point"
                                                        + " (FR-037b)"));
    }

    /** The index, for the platform layer's boundary guard and crystal lookup. Read-only. */
    public ChunkZoneIndex index() {
        return index;
    }

    @Override
    public Optional<Zone> zoneAt(WorldPosition position) {
        return Optional.ofNullable(lookup(position));
    }

    @Override
    public String zoneKeyAt(WorldPosition position) {
        Zone found = lookup(position);
        return found == null ? null : found.key();
    }

    @Override
    public boolean inSafeCore(WorldPosition position) {
        Zone found = lookup(position);
        return found != null
                && found.inSafeCore(floor(position.x()), floor(position.y()), floor(position.z()));
    }

    @Override
    public Optional<Zone> byKey(String zoneKey) {
        return Optional.ofNullable(byKey.get(zoneKey));
    }

    @Override
    public List<Zone> all() {
        return index.zones();
    }

    @Override
    public WorldPosition startPoint() {
        return startPoint;
    }

    @Override
    public Optional<WorldPosition> respawnPointOf(String zoneKey) {
        Zone zone = byKey.get(zoneKey);
        if (zone == null) {
            return Optional.empty();
        }
        return zone.safeCore().map(SafeCore::respawnPoint);
    }

    @Override
    public WorldPosition fallbackPoint() {
        return fallbackPoint;
    }

    @Override
    public List<SpawnArea> spawnAreasOf(String zoneKey) {
        Zone zone = byKey.get(zoneKey);
        return zone == null ? List.of() : zone.spawnAreas();
    }

    private Zone lookup(WorldPosition position) {
        return index.zoneAt(
                position.worldId(), floor(position.x()), floor(position.y()), floor(position.z()));
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
