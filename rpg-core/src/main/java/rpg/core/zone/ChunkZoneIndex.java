package rpg.core.zone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The spatial index: from a packed chunk key to the zones that touch that chunk (FR-005, FR-006).
 *
 * <p><b>Never iterate over all zones.</b> That is the rule this class exists to keep (Constitution
 * II). A lookup is one table access; only when a chunk carries more than one candidate does an exact
 * cuboid test follow, and most chunks carry exactly one.
 *
 * <p><b>Built at load, read at runtime.</b> Every cuboid stamps the chunks it covers. Nothing mutates
 * afterwards, so there is no shared mutable state in the gameplay path (Constitution I). A reload
 * builds a new index and swaps it in whole.
 *
 * <p><b>The boundary marker resolves a contradiction the spec carries on purpose.</b> FR-020 says
 * movement inside one chunk must not be re-evaluated; FR-016 says crossing the safe-core boundary
 * must fire. But a core boundary runs through the middle of chunks, so the chunk rule alone would
 * miss it until the player left the chunk entirely. A chunk is therefore marked as a
 * <em>boundary chunk</em> when more than one area touches it - two zones, or a zone and its safe
 * core, or a crystal trigger area. Movement is re-evaluated inside those chunks and skipped in all
 * the others (research.md R4). In the open expanse of a region, movement costs two integer
 * comparisons; only the handful of chunks along a border pay for a lookup.
 */
public final class ChunkZoneIndex {

    /** What one chunk holds. Allocated at load time, read-only afterwards. */
    record Bucket(Zone[] zones, boolean boundary) {}



    private final Map<UUID, ChunkTable> zonesByWorld;
    private final Map<UUID, ChunkTable> crystalsByWorld;
    private final Map<String, CrystalPlacement> crystalsByKey;
    private final List<Zone> zones;

    private ChunkZoneIndex(
            Map<UUID, ChunkTable> zonesByWorld,
            Map<UUID, ChunkTable> crystalsByWorld,
            Map<String, CrystalPlacement> crystalsByKey,
            List<Zone> zones) {
        this.zonesByWorld = zonesByWorld;
        this.crystalsByWorld = crystalsByWorld;
        this.crystalsByKey = crystalsByKey;
        this.zones = List.copyOf(zones);
    }

    /** Packs chunk coordinates into one long. Same packing everywhere in this block. */
    public static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /** The chunk key for a block position. */
    public static long keyForBlock(int blockX, int blockZ) {
        return key(blockX >> Cuboid.CHUNK_SHIFT, blockZ >> Cuboid.CHUNK_SHIFT);
    }

    /**
     * Builds the index. Load path only.
     *
     * <p>Boxing in this method is deliberate and harmless - it runs once at start and once per
     * reload. The primitive tables are what the tick path sees.
     */
    public static ChunkZoneIndex build(List<Zone> zones) {
        Map<UUID, Map<Long, List<Zone>>> staged = new LinkedHashMap<>();
        Map<UUID, Set<Long>> boundaries = new LinkedHashMap<>();
        Map<UUID, Map<Long, List<CrystalPlacement>>> crystalStaged = new LinkedHashMap<>();
        Map<String, CrystalPlacement> byKey = new LinkedHashMap<>();

        for (Zone zone : zones) {
            Map<Long, List<Zone>> perWorld =
                    staged.computeIfAbsent(zone.worldId(), ignored -> new HashMap<>());
            for (long chunk : zone.area().touchedChunks()) {
                perWorld.computeIfAbsent(chunk, ignored -> new ArrayList<>(1)).add(zone);
            }
            Set<Long> worldBoundaries =
                    boundaries.computeIfAbsent(zone.worldId(), ignored -> new LinkedHashSet<>());
            // A safe core boundary runs through chunks, so every chunk it touches has to be
            // re-evaluated on movement - otherwise entering the core would go unnoticed (FR-016).
            zone.safeCore()
                    .ifPresent(
                            core -> {
                                for (long chunk : core.area().touchedChunks()) {
                                    worldBoundaries.add(chunk);
                                }
                            });
            zone.crystal()
                    .ifPresent(
                            crystal -> {
                                CrystalPlacement at = new CrystalPlacement(crystal, zone);
                                byKey.put(crystal.key(), at);
                                Map<Long, List<CrystalPlacement>> perWorldCrystals =
                                        crystalStaged.computeIfAbsent(
                                                zone.worldId(), ignored -> new HashMap<>());
                                for (long chunk : crystal.triggerArea().touchedChunks()) {
                                    perWorldCrystals
                                            .computeIfAbsent(chunk, ignored -> new ArrayList<>(1))
                                            .add(at);
                                    worldBoundaries.add(chunk);
                                }
                            });
        }

        Map<UUID, ChunkTable> zoneTables = new LinkedHashMap<>();
        staged.forEach(
                (worldId, perWorld) -> {
                    ChunkTable table = new ChunkTable(perWorld.size());
                    Set<Long> worldBoundaries =
                            boundaries.getOrDefault(worldId, Set.of());
                    perWorld.forEach(
                            (chunk, candidates) -> {
                                boolean boundary =
                                        candidates.size() > 1 || worldBoundaries.contains(chunk);
                                table.put(
                                        chunk,
                                        new Bucket(candidates.toArray(new Zone[0]), boundary));
                            });
                    zoneTables.put(worldId, table);
                });

        Map<UUID, ChunkTable> crystalTables = new LinkedHashMap<>();
        crystalStaged.forEach(
                (worldId, perWorld) -> {
                    ChunkTable table = new ChunkTable(perWorld.size());
                    perWorld.forEach(
                            (chunk, found) -> table.put(chunk, found.toArray(new CrystalPlacement[0])));
                    crystalTables.put(worldId, table);
                });

        return new ChunkZoneIndex(zoneTables, crystalTables, byKey, zones);
    }

    /** All zones, in configuration order. */
    public List<Zone> zones() {
        return zones;
    }

    /**
     * The zone at this position, or {@code null}.
     *
     * <p>One table access. The exact cuboid test only runs when the chunk carries more than one
     * candidate, and it stops at the first hit - overlapping zones are refused at start (FR-012), so
     * at most one can match.
     */
    public Zone zoneAt(UUID worldId, int x, int y, int z) {
        ChunkTable table = zonesByWorld.get(worldId);
        if (table == null) {
            return null;
        }
        Object found = table.get(keyForBlock(x, z));
        if (found == null) {
            return null;
        }
        Zone[] candidates = ((Bucket) found).zones();
        if (candidates.length == 1) {
            Zone only = candidates[0];
            return only.contains(x, y, z) ? only : null;
        }
        for (Zone candidate : candidates) {
            if (candidate.contains(x, y, z)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Whether movement inside this chunk has to be re-evaluated at all (FR-020).
     *
     * <p>False for the vast majority of chunks. See the class comment for why this exists.
     */
    public boolean isBoundaryChunk(UUID worldId, int blockX, int blockZ) {
        ChunkTable table = zonesByWorld.get(worldId);
        if (table == null) {
            return false;
        }
        Object found = table.get(keyForBlock(blockX, blockZ));
        return found != null && ((Bucket) found).boundary();
    }

    /** The crystal whose trigger area covers this position, or {@code null} (research.md R5). */
    public CrystalPlacement crystalAt(UUID worldId, int x, int y, int z) {
        ChunkTable table = crystalsByWorld.get(worldId);
        if (table == null) {
            return null;
        }
        Object found = table.get(keyForBlock(x, z));
        if (found == null) {
            return null;
        }
        for (CrystalPlacement candidate : (CrystalPlacement[]) found) {
            if (candidate.crystal().triggerArea().contains(x, y, z)) {
                return candidate;
            }
        }
        return null;
    }

    /** The crystal with this key, or {@code null} - it may have left the configuration. */
    public CrystalPlacement crystalByKey(String crystalKey) {
        return crystalsByKey.get(crystalKey);
    }

    /** Every crystal, in configuration order. For the selection window. */
    public List<CrystalPlacement> crystals() {
        return List.copyOf(crystalsByKey.values());
    }
}
