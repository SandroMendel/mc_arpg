package rpg.core.zone;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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

    /** The index. Package-private on purpose: the contract is {@link Zones}, not this (FR-060). */
    ChunkZoneIndex index() {
        return index;
    }

    @Override
    public boolean isBoundaryChunk(UUID worldId, int blockX, int blockZ) {
        return index.isBoundaryChunk(worldId, blockX, blockZ);
    }

    @Override
    public Optional<CrystalPlacement> crystalAt(WorldPosition position) {
        return Optional.ofNullable(
                index.crystalAt(
                        position.worldId(),
                        floor(position.x()),
                        floor(position.y()),
                        floor(position.z())));
    }

    @Override
    public Optional<CrystalPlacement> crystalByKey(String crystalKey) {
        return Optional.ofNullable(index.crystalByKey(crystalKey));
    }

    @Override
    public List<CrystalPlacement> crystals() {
        return index.crystals();
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
        return Optional.ofNullable(zone(zoneKey));
    }

    /**
     * One lookup for every by-key query, and it tolerates a {@code null} key.
     *
     * <p>The map is immutable, and an immutable map does not merely miss a null key - it refuses it
     * with a {@link NullPointerException}. Every caller here got its key from somewhere that answers
     * {@code null} for "outside every region": the tracker for a holder in the wilderness, a pending
     * respawn that was never set. Those are ordinary states rather than faults, and each of them
     * would otherwise have needed a guard of its own - until somebody added a fourth query and forgot
     * one. {@code SpawnAreaQueryTest} found the first of them.
     */
    private Zone zone(String zoneKey) {
        return zoneKey == null ? null : byKey.get(zoneKey);
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
        Zone zone = zone(zoneKey);
        if (zone == null) {
            return Optional.empty();
        }
        return zone.safeCore().map(SafeCore::respawnPoint);
    }

    @Override
    public WorldPosition fallbackPoint() {
        return fallbackPoint;
    }

    /**
     * <b>An unknown or absent region answers empty rather than throwing.</b> B10 will call this from
     * a spawn event, with a key that came from asking where somebody is - and that answers
     * {@code null} for anybody standing between two regions. An exception there would be a fault in
     * the wrong block, caused by a player walking.
     */
    @Override
    public List<SpawnArea> spawnAreasOf(String zoneKey) {
        Zone zone = zone(zoneKey);
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
