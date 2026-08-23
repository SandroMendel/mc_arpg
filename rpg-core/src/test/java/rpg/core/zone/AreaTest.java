package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T010: several boxes make one area, and the load path that turns them into chunk keys (FR-004). */
class AreaTest {

    @Test
    @DisplayName("an area needs at least one box")
    void needsAtLeastOneBox() {
        assertThatThrownBy(() -> new Area(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");
    }

    @Test
    @DisplayName("a position in any part is in the area")
    void anyPartCounts() {
        Area area = Area.of(Cuboid.of(0, 0, 100, 100), Cuboid.of(500, 500, 600, 600));

        assertThat(area.contains(50, 64, 50)).as("first part").isTrue();
        assertThat(area.contains(550, 64, 550)).as("second part").isTrue();
        assertThat(area.contains(300, 64, 300)).as("the gap between them").isFalse();
    }

    @Test
    @DisplayName("touched chunks cover every part, negative coordinates included")
    void touchedChunksCoverEveryPart() {
        Area area = Area.of(Cuboid.of(-20, -20, -1, -1), Cuboid.of(0, 0, 15, 15));

        long[] chunks = area.touchedChunks();

        // -20..-1 spans chunks -2 and -1 on both axes (4), 0..15 is exactly chunk 0 (1).
        assertThat(chunks).hasSize(5);
        assertThat(chunks).contains(ChunkZoneIndex.key(-2, -2));
        assertThat(chunks).contains(ChunkZoneIndex.key(-1, -1));
        assertThat(chunks).contains(ChunkZoneIndex.key(0, 0));
    }

    @Test
    @DisplayName("containment is per box, and a core straddling two boxes is refused on purpose")
    void containmentIsPerBox() {
        Area region = Area.of(Cuboid.of(0, 0, 100, 100), Cuboid.of(101, 0, 200, 100));
        Area insideOne = Area.of(Cuboid.of(10, 10, 20, 20));
        Area acrossTheSeam = Area.of(Cuboid.of(90, 10, 110, 20));

        assertThat(insideOne.isInside(region)).isTrue();
        assertThat(acrossTheSeam.isInside(region))
                .as("covered only by the union of two boxes - refused, see Area#isInside")
                .isFalse();
    }

    @Test
    @DisplayName("intersection asks every box against every box")
    void intersectionAcrossParts() {
        Area left = Area.of(Cuboid.of(0, 0, 100, 100));
        Area right = Area.of(Cuboid.of(200, 200, 300, 300), Cuboid.of(50, 50, 150, 150));

        assertThat(left.intersects(right)).isTrue();
        assertThat(right.intersects(left)).isTrue();
    }

    @Test
    @DisplayName("an absurd span fails with a sentence instead of exhausting the heap")
    void absurdSpanIsRefused() {
        Area huge = Area.of(Cuboid.of(-30_000_000, -30_000_000, 30_000_000, 30_000_000));

        assertThatThrownBy(huge::touchedChunks)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunks");
    }
}
