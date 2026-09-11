package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.scheduler.WorldPosition;

/** T016: the index answers by chunk, and marks the chunks where a boundary runs (FR-005 to FR-007). */
class ChunkZoneIndexTest {

    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID OTHER_WORLD = UUID.randomUUID();

    private static Zone zone(String key, Cuboid area, SafeCore core) {
        return new Zone(
                key,
                WORLD,
                new Area(List.of(area)),
                new LevelBand(1, 10),
                Optional.ofNullable(core),
                List.of(),
                Optional.empty(),
                false,
                false);
    }

    @Test
    @DisplayName("a single candidate in the chunk still gets its exact box checked")
    void singleCandidateIsStillBoundsChecked() {
        // The zone covers 0..100, but chunk 0 reaches to block 15 only on the low side; block 200
        // sits in a chunk the index does not know at all.
        ChunkZoneIndex index = ChunkZoneIndex.build(List.of(zone("a", Cuboid.of(0, 0, 100, 100), null)));

        assertThat(index.zoneAt(WORLD, 50, 64, 50)).isNotNull();
        assertThat(index.zoneAt(WORLD, 200, 64, 200)).as("outside every chunk").isNull();
        assertThat(index.zoneAt(WORLD, 110, 64, 50))
                .as("inside a known chunk but outside the box")
                .isNull();
    }

    @Test
    @DisplayName("two zones sharing a chunk are resolved by the exact box")
    void twoCandidatesInOneChunk() {
        // Both zones touch chunk 0 (blocks 0..15) without overlapping: 0..7 and 8..15.
        Zone west = zone("west", Cuboid.of(0, 0, 7, 15), null);
        Zone east = zone("east", Cuboid.of(8, 0, 15, 15), null);
        ChunkZoneIndex index = ChunkZoneIndex.build(List.of(west, east));

        assertThat(index.zoneAt(WORLD, 3, 64, 3)).isEqualTo(west);
        assertThat(index.zoneAt(WORLD, 12, 64, 3)).isEqualTo(east);
        assertThat(index.isBoundaryChunk(WORLD, 3, 3))
                .as("two zones touch this chunk")
                .isTrue();
    }

    @Test
    @DisplayName("a chunk touched by a safe core is a boundary chunk, the open expanse is not")
    void safeCoreMarksBoundaryChunks() {
        SafeCore core =
                new SafeCore(
                        Area.of(Cuboid.of(-60, -60, 60, 60)),
                        new WorldPosition(WORLD, 0.5, 65.0, 0.5));
        ChunkZoneIndex index =
                ChunkZoneIndex.build(List.of(zone("a", Cuboid.of(-500, -500, 500, 500), core)));

        assertThat(index.isBoundaryChunk(WORLD, 0, 0)).as("inside the core").isTrue();
        assertThat(index.isBoundaryChunk(WORLD, 55, 55)).as("core edge").isTrue();
        assertThat(index.isBoundaryChunk(WORLD, 400, 400))
                .as("open expanse - movement here must stay free")
                .isFalse();
    }

    @Test
    @DisplayName("zones of another world are never returned")
    void worldsAreKeptApart() {
        ChunkZoneIndex index = ChunkZoneIndex.build(List.of(zone("a", Cuboid.of(0, 0, 100, 100), null)));

        assertThat(index.zoneAt(OTHER_WORLD, 50, 64, 50)).isNull();
        assertThat(index.isBoundaryChunk(OTHER_WORLD, 50, 50)).isFalse();
    }

    @Test
    @DisplayName("a crystal is found by its trigger area, and marks its chunk as boundary")
    void crystalLookup() {
        WaypointCrystal crystal =
                new WaypointCrystal("a-crystal", Area.of(Cuboid.of(-2, 64, -2, 2, 67, 2)), 25L);
        Zone withCrystal =
                new Zone(
                        "a",
                        WORLD,
                        Area.of(Cuboid.of(-500, -500, 500, 500)),
                        new LevelBand(1, 10),
                        Optional.of(
                                new SafeCore(
                                        Area.of(Cuboid.of(-60, -60, 60, 60)),
                                        new WorldPosition(WORLD, 0.5, 65.0, 0.5))),
                        List.of(),
                        Optional.of(crystal),
                        false,
                        true);
        ChunkZoneIndex index = ChunkZoneIndex.build(List.of(withCrystal));

        assertThat(index.crystalAt(WORLD, 0, 65, 0)).isNotNull();
        assertThat(index.crystalAt(WORLD, 0, 65, 0).zone()).isEqualTo(withCrystal);
        assertThat(index.crystalAt(WORLD, 0, 80, 0)).as("above the trigger box").isNull();
        assertThat(index.crystalAt(WORLD, 300, 65, 300)).as("far away").isNull();
        assertThat(index.crystalByKey("a-crystal")).isNotNull();
        assertThat(index.crystalByKey("nope")).isNull();
        assertThat(index.crystals()).hasSize(1);
    }

    @Test
    @DisplayName("the chunk key packs both coordinates, negatives included")
    void chunkKeyPacking() {
        assertThat(ChunkZoneIndex.key(-1, -1)).isNotEqualTo(ChunkZoneIndex.key(-1, 0));
        assertThat(ChunkZoneIndex.key(1, 2)).isNotEqualTo(ChunkZoneIndex.key(2, 1));
        assertThat(ChunkZoneIndex.keyForBlock(-17, 32)).isEqualTo(ChunkZoneIndex.key(-2, 2));
    }
}
