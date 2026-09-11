package rpg.platform.mob;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import rpg.core.scheduler.WorldPosition;
import rpg.core.zone.CrystalPlacement;
import rpg.core.zone.SpawnArea;
import rpg.core.zone.Zone;
import rpg.core.zone.Zones;

/**
 * Zonen ohne echte Geometrieindex-Kosten fuer die B10-Plattformtests - nur was ein Test braucht,
 * sonst wirft sie. Geteilt statt je Testklasse neu geschrieben, weil {@link HordeSweep} nur
 * {@code zoneAt}, {@code zoneKeyAt} und {@code byKey} wirklich benutzt.
 */
final class FakeZones implements Zones {

    private final Map<String, Zone> byKey = new LinkedHashMap<>();

    FakeZones(Zone... zones) {
        for (Zone zone : zones) {
            byKey.put(zone.key(), zone);
        }
    }

    @Override
    public Optional<Zone> zoneAt(WorldPosition position) {
        for (Zone zone : byKey.values()) {
            if (zone.worldId().equals(position.worldId())
                    && zone.contains(
                            (int) Math.floor(position.x()),
                            (int) Math.floor(position.y()),
                            (int) Math.floor(position.z()))) {
                return Optional.of(zone);
            }
        }
        return Optional.empty();
    }

    @Override
    public String zoneKeyAt(WorldPosition position) {
        return zoneAt(position).map(Zone::key).orElse(null);
    }

    @Override
    public boolean inSafeCore(WorldPosition position) {
        throw new UnsupportedOperationException("dieser Test braucht das nicht");
    }

    @Override
    public boolean isBoundaryChunk(UUID worldId, int blockX, int blockZ) {
        throw new UnsupportedOperationException("dieser Test braucht das nicht");
    }

    @Override
    public Optional<CrystalPlacement> crystalAt(WorldPosition position) {
        throw new UnsupportedOperationException("dieser Test braucht das nicht");
    }

    @Override
    public Optional<CrystalPlacement> crystalByKey(String crystalKey) {
        throw new UnsupportedOperationException("dieser Test braucht das nicht");
    }

    @Override
    public List<CrystalPlacement> crystals() {
        throw new UnsupportedOperationException("dieser Test braucht das nicht");
    }

    @Override
    public Optional<Zone> byKey(String zoneKey) {
        return Optional.ofNullable(byKey.get(zoneKey));
    }

    @Override
    public List<Zone> all() {
        return List.copyOf(byKey.values());
    }

    @Override
    public WorldPosition startPoint() {
        throw new UnsupportedOperationException("dieser Test braucht das nicht");
    }

    @Override
    public Optional<WorldPosition> respawnPointOf(String zoneKey) {
        throw new UnsupportedOperationException("dieser Test braucht das nicht");
    }

    @Override
    public WorldPosition fallbackPoint() {
        throw new UnsupportedOperationException("dieser Test braucht das nicht");
    }

    @Override
    public List<SpawnArea> spawnAreasOf(String zoneKey) {
        return byKey(zoneKey).map(Zone::spawnAreas).orElse(List.of());
    }
}
