package rpg.core.item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import rpg.core.scheduler.WorldPosition;
import rpg.core.zone.Area;
import rpg.core.zone.CrystalPlacement;
import rpg.core.zone.Cuboid;
import rpg.core.zone.LevelBand;
import rpg.core.zone.SpawnArea;
import rpg.core.zone.Zone;
import rpg.core.zone.Zones;

/**
 * Eine Zonenabfrage, die genau eine Region kennt.
 *
 * <p>Mehr braucht B11 von B09 nicht: die einzige Frage, die dieser Block stellt, ist <em>„gibt es
 * diese Region?"</em> — für die Startprüfung von Beutetabellen und Händlerbeständen. Alles andere
 * an {@link Zones} beantwortet dieses Double bewusst leer, damit ein Test, der versehentlich mehr
 * benutzt, sofort auffällt statt auf einem erfundenen Wert weiterzurechnen.
 */
final class FakeZones implements Zones {

    private static final UUID WORLD = UUID.nameUUIDFromBytes("item-test-world".getBytes());

    private final Zone greenfields =
            new Zone(
                    "greenfields",
                    WORLD,
                    new Area(List.of(new Cuboid(0, 0, 0, 999, 255, 999))),
                    new LevelBand(1, 10),
                    Optional.empty(),
                    List.of(),
                    Optional.empty(),
                    false,
                    true);

    @Override
    public Optional<Zone> zoneAt(WorldPosition position) {
        return Optional.empty();
    }

    @Override
    public String zoneKeyAt(WorldPosition position) {
        return "greenfields";
    }

    @Override
    public boolean inSafeCore(WorldPosition position) {
        return false;
    }

    @Override
    public boolean isBoundaryChunk(UUID worldId, int blockX, int blockZ) {
        return false;
    }

    @Override
    public Optional<CrystalPlacement> crystalAt(WorldPosition position) {
        return Optional.empty();
    }

    @Override
    public Optional<CrystalPlacement> crystalByKey(String crystalKey) {
        return Optional.empty();
    }

    @Override
    public List<CrystalPlacement> crystals() {
        return List.of();
    }

    @Override
    public Optional<Zone> byKey(String zoneKey) {
        return "greenfields".equals(zoneKey) ? Optional.of(greenfields) : Optional.empty();
    }

    @Override
    public List<Zone> all() {
        return List.of(greenfields);
    }

    @Override
    public WorldPosition startPoint() {
        return new WorldPosition(WORLD, 0.5, 65.0, 0.5);
    }

    @Override
    public Optional<WorldPosition> respawnPointOf(String zoneKey) {
        return Optional.empty();
    }

    @Override
    public WorldPosition fallbackPoint() {
        return startPoint();
    }

    @Override
    public List<SpawnArea> spawnAreasOf(String zoneKey) {
        return List.of();
    }
}
