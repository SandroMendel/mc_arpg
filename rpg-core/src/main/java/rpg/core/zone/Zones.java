package rpg.core.zone;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import rpg.core.scheduler.WorldPosition;

/**
 * The public contract of B09 - the only way in (FR-060).
 *
 * <p>B10, B11 and B13 are built against this. Reaching past it - at the index, at {@code zones.yml},
 * at a listener - is not allowed (Constitution III), and {@code ZoneSourceInvariantsTest} guards it.
 *
 * <p><b>From now on a change here is ADR-bound</b> - the same promise {@code CombatPipeline},
 * {@code StatEngine}, {@code AbilityRegistry} and {@code Currency} made for themselves.
 *
 * <p><b>{@code zoneAt} always returns the region, never the safe core</b> (FR-011). The core is not a
 * zone; it is an area inside one. Whoever wants to know if somebody stands protected asks
 * {@link #inSafeCore}.
 *
 * <p><b>An empty result means "no zone", not "error"</b> (FR-007). A hand-built continent leaves gaps,
 * and in them the defaults apply: no PvP, no warning, the fallback point on death.
 *
 * <p><b>This block hands out places; it does not move anybody</b> (FR-037c). Character creation stays
 * with B07, joining with B03. Only this block's own platform layer teleports, and only for death, the
 * aftermath of a combat logout, and travel.
 */
public interface Zones {

    /** The zone at this position, or empty. */
    Optional<Zone> zoneAt(WorldPosition position);

    /**
     * The zone key at this position, or {@code null}.
     *
     * <p>Exists for the same reason as {@code balanceOrZero} in B08b and {@code levelOrZero} in B06:
     * this is called from paths that promise no allocation per call (Constitution II).
     */
    String zoneKeyAt(WorldPosition position);

    /** Whether this position lies in the safe core of its zone (FR-010). */
    boolean inSafeCore(WorldPosition position);

    /**
     * Whether a border of any kind runs through this chunk (research.md R4).
     *
     * <p>Behind the contract rather than exposing the index, because FR-060 says this interface is
     * the only way in. The movement guard needs exactly this one bit and nothing else about the
     * index's shape.
     */
    boolean isBoundaryChunk(UUID worldId, int blockX, int blockZ);

    /**
     * The crystal whose trigger area covers this position, or empty.
     *
     * <p>One table access - a right-click never walks the list of crystals (research.md R5).
     */
    Optional<CrystalPlacement> crystalAt(WorldPosition position);

    /** The crystal with this key, or empty - it may have left the configuration (FR-051b). */
    Optional<CrystalPlacement> crystalByKey(String crystalKey);

    /** Every crystal, in configuration order. For the selection window (FR-048). */
    List<CrystalPlacement> crystals();

    /** The zone with this key, or empty. */
    Optional<Zone> byKey(String zoneKey);

    /** All zones, in configuration order. */
    List<Zone> all();

    /** Where a new character belongs: the respawn point of the start region (FR-037b). */
    WorldPosition startPoint();

    /** Where a death in this zone leads; empty when the zone has no safe core. */
    Optional<WorldPosition> respawnPointOf(String zoneKey);

    /** The configured fallback for a death outside every zone (FR-034). */
    WorldPosition fallbackPoint();

    /**
     * The named spawn areas of this zone (FR-054).
     *
     * <p>Key and geometry, nothing else. Creature types, attributes, bosses and horde logic belong
     * to B10 (FR-053a); this block spawns nothing.
     */
    List<SpawnArea> spawnAreasOf(String zoneKey);
}
