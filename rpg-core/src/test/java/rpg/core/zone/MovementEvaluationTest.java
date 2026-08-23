package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;

/**
 * T035 - the guard in front of the movement path (FR-019, FR-020, research.md R4).
 *
 * <p>The two requirements it reconciles contradict each other on the surface: nothing may be
 * re-evaluated inside a chunk, but crossing the safe-core boundary must fire - and that boundary runs
 * through chunks. The tests below are the proof that both hold.
 */
class MovementEvaluationTest {

    private final Zones index = load();

    @Test
    @DisplayName("a step inside an ordinary chunk is skipped entirely")
    void insideAnOrdinaryChunkNothingHappens() {
        // Chunk 25 in the greenfields danger zone: one zone touches it, no core, no crystal.
        assertThat(needs(400, 400, 401, 401)).isFalse();
        assertThat(needs(400, 400, 415, 415)).as("still the same chunk").isFalse();
    }

    @Test
    @DisplayName("leaving the chunk always counts, boundary or not")
    void leavingTheChunkAlwaysCounts() {
        assertThat(needs(415, 400, 416, 400)).as("chunk 25 -> 26").isTrue();
        assertThat(needs(400, 415, 400, 416)).isTrue();
    }

    @Test
    @DisplayName("inside a chunk the safe-core boundary runs through, every step counts")
    void insideABoundaryChunkEveryStepCounts() {
        // The core reaches to block 60, so chunk 3 (blocks 48..63) carries its boundary.
        assertThat(index.isBoundaryChunk(ZoneFixture.WORLD, 55, 0)).as("precondition").isTrue();

        assertThat(needs(55, 0, 56, 0))
                .as("without this, entering the core would go unnoticed until the chunk was left")
                .isTrue();
    }

    @Test
    @DisplayName("the crystal chunk counts too - a right-click there has to be seen")
    void crystalChunkCounts() {
        assertThat(needs(1, 1, 2, 2)).isTrue();
    }

    @Test
    @DisplayName("a step in a world without zones is never evaluated")
    void unknownWorldIsNeverEvaluated() {
        assertThat(
                        MovementGuard.needsEvaluation(
                                index, ZoneFixture.OTHER_WORLD, 400, 400, 401, 401))
                .isFalse();
    }

    @Test
    @DisplayName("the guard is pure arithmetic on negative coordinates as well")
    void negativeCoordinates() {
        assertThat(needs(-400, -400, -399, -399)).as("same chunk, ordinary").isFalse();
        assertThat(needs(-401, -400, -400, -400)).as("chunk -26 -> -25").isTrue();
    }

    private boolean needs(int fromX, int fromZ, int toX, int toZ) {
        return MovementGuard.needsEvaluation(index, ZoneFixture.WORLD, fromX, fromZ, toX, toZ);
    }

    private static Zones load() {
        try {
            Map<String, Object> document = ZoneFixture.document();
            ConfigSchema<ZoneConfig> schema = ZoneConfigSchema.schema(ZoneFixture.worlds());
            ZoneConfig config =
                    schema.bind(SchemaValidator.validate(Path.of("zones.yml"), document, schema));
            return new DefaultZones(config);
        } catch (Exception failed) {
            throw new IllegalStateException(failed);
        }
    }
}
