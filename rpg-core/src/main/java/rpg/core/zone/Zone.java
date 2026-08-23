package rpg.core.zone;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One region: a named area of the world with its own rules (FR-002, FR-003).
 *
 * <p><b>A zone is never a world</b> (ADR-006). It is {@code (worldId, geometry)}, which is what makes
 * "this zone lives in its own world" a configuration line rather than a rebuild (SC-004). Nothing
 * here hands out a world; the platform layer resolves the id when it has to act.
 *
 * <p><b>No display name.</b> The zone carries a technical {@code key}; what a player reads comes from
 * {@code messages.yml} under {@code zone.<key>.name} (FR-003a). Every reference - a spawn area, a
 * crystal, a later block - goes through the key and never through the text (FR-003b). Otherwise
 * renaming "The Darkforest" would have broken every one of them.
 *
 * <p><b>No difficulty modifier and no loot assignment.</b> Both were taken out of this block on
 * purpose (FR-056, FR-057): a field whose meaning this block does not know is one it cannot
 * validate, and a typo in it would surface months later in whichever block finally read it. B10 and
 * B11 add them with a shape they can check. What remains - level band, PvP switch, respawn point,
 * start-region mark - is evaluated *here*, and that is the test that kept the other two out
 * (FR-057a).
 *
 * @param key technical identifier, unique per world
 * @param worldId the world this zone lies in
 * @param area the geometry
 * @param levelBand the level range this region is meant for
 * @param safeCore the protected core around the spawn, if this region has one
 * @param spawnAreas named places for B10 to fill; may be empty
 * @param crystal this region's waypoint crystal, if it has one
 * @param pvp whether players may damage each other in the danger zone; the core always refuses
 * @param startRegion whether new characters appear here; exactly one zone carries this
 */
public record Zone(
        String key,
        UUID worldId,
        Area area,
        LevelBand levelBand,
        Optional<SafeCore> safeCore,
        List<SpawnArea> spawnAreas,
        Optional<WaypointCrystal> crystal,
        boolean pvp,
        boolean startRegion) {

    public Zone {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(levelBand, "levelBand");
        Objects.requireNonNull(safeCore, "safeCore");
        Objects.requireNonNull(crystal, "crystal");
        Objects.requireNonNull(spawnAreas, "spawnAreas");
        if (key.isBlank()) {
            throw new IllegalArgumentException("a zone key must not be blank");
        }
        spawnAreas = List.copyOf(spawnAreas);
    }

    /** Whether this position lies in this zone at all. */
    public boolean contains(int x, int y, int z) {
        return area.contains(x, y, z);
    }

    /** Whether this position lies in this zone's protected core. False when there is none. */
    public boolean inSafeCore(int x, int y, int z) {
        return safeCore.isPresent() && safeCore.get().area().contains(x, y, z);
    }

    /** The message key for this zone's player-facing name (FR-003a). */
    public String nameKey() {
        return "zone." + key + ".name";
    }
}
