package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T009: two corners, inclusive bounds, and the vertical bounds that may be absent (FR-004). */
class CuboidTest {

    @Test
    @DisplayName("corners may be written in any order")
    void cornersAreNormalised() {
        Cuboid written = Cuboid.of(500, 500, -500, -500);
        Cuboid other = Cuboid.of(-500, -500, 500, 500);

        assertThat(written).isEqualTo(other);
        assertThat(written.minX()).isEqualTo(-500);
        assertThat(written.maxX()).isEqualTo(500);
    }

    @Test
    @DisplayName("all six bounds are inclusive")
    void boundsAreInclusive() {
        Cuboid box = Cuboid.of(0, 10, 0, 10, 20, 10);

        assertThat(box.contains(0, 10, 0)).as("lower corner").isTrue();
        assertThat(box.contains(10, 20, 10)).as("upper corner").isTrue();
        assertThat(box.contains(-1, 15, 5)).as("one block west").isFalse();
        assertThat(box.contains(11, 15, 5)).as("one block east").isFalse();
    }

    @Test
    @DisplayName("without vertical bounds a cuboid covers cave and sky alike")
    void withoutVerticalBoundsEverythingVertical() {
        Cuboid box = Cuboid.of(-100, -100, 100, 100);

        assertThat(box.boundedVertically()).isFalse();
        assertThat(box.contains(0, -2000, 0)).as("deep below").isTrue();
        assertThat(box.contains(0, 5000, 0)).as("far above").isTrue();
        assertThat(box.contains(0, 64, 0)).as("surface").isTrue();
    }

    @Test
    @DisplayName("with vertical bounds a position above it is OUTSIDE - that is the point")
    void withVerticalBoundsAboveIsOutside() {
        Cuboid box = Cuboid.of(-100, 60, -100, 100, 70, 100);

        assertThat(box.boundedVertically()).isTrue();
        assertThat(box.contains(0, 65, 0)).as("inside the band").isTrue();
        assertThat(box.contains(0, 71, 0)).as("one above").isFalse();
        assertThat(box.contains(0, 59, 0)).as("one below").isFalse();
    }

    @Test
    @DisplayName("intersection and containment, both inclusive")
    void intersectionAndContainment() {
        Cuboid region = Cuboid.of(-500, -500, 500, 500);
        Cuboid core = Cuboid.of(-60, -60, 60, 60);
        Cuboid outside = Cuboid.of(600, 600, 700, 700);
        Cuboid touching = Cuboid.of(500, 500, 600, 600);

        assertThat(core.isInside(region)).isTrue();
        assertThat(region.isInside(core)).isFalse();
        assertThat(region.intersects(core)).isTrue();
        assertThat(region.intersects(outside)).isFalse();
        assertThat(region.intersects(touching)).as("sharing one block counts").isTrue();
    }

    @Test
    @DisplayName("the canonical constructor refuses un-normalised corners instead of guessing")
    void canonicalConstructorRefusesUnnormalised() {
        assertThatThrownBy(() -> new Cuboid(10, 0, 0, 0, 10, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("normalised");
    }

    @Test
    @DisplayName("chunk bounds follow the block bounds, negative coordinates included")
    void chunkBounds() {
        Cuboid box = Cuboid.of(-17, -17, 32, 32);

        assertThat(box.minChunkX()).as("-17 >> 4").isEqualTo(-2);
        assertThat(box.maxChunkX()).as("32 >> 4").isEqualTo(2);
        assertThat(box.minChunkZ()).isEqualTo(-2);
        assertThat(box.maxChunkZ()).isEqualTo(2);
    }
}
