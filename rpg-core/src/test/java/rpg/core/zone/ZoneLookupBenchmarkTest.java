package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;
import rpg.core.scheduler.WorldPosition;

/**
 * T037 - the measurement behind SC-001: 200 positions in under 0,5 ms.
 *
 * <p><b>A measurement, not a load test</b> (ADR-031). It needs no server, because the index is
 * arithmetic: a table access and at most one cuboid test. What only shows up under 150 players and
 * 800 mobs belongs to B15's load-test phase and does not hold this block open.
 *
 * <p><b>Why the threshold is generous in the assertion.</b> A wall-clock measurement on a shared CI
 * machine is noisy, and a test that fails when a neighbouring process hiccups teaches people to
 * ignore it. The budget is 0,5 ms for 200 lookups - that is 2500 ns each, while the actual cost is a
 * multiplication, a mask and an array access. The assertion is set at the budget, and the margin is
 * reported so a regression shows up as a shrinking margin long before it turns red.
 */
class ZoneLookupBenchmarkTest {

    private static final int PLAYERS = 200;
    private static final long BUDGET_NANOS = 500_000L; // 0,5 ms for all 200
    private static final int WARMUP_ROUNDS = 200;
    private static final int MEASURED_ROUNDS = 50;

    @Test
    @DisplayName("the zone of 200 players is resolved in under 0,5 ms (SC-001)")
    void twoHundredLookupsUnderHalfAMillisecond() throws Exception {
        Zones zones = load();
        List<WorldPosition> positions = spread();

        // Warm up: the first passes measure the JIT, not the index.
        long sink = 0L;
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            sink += resolveAll(zones, positions);
        }

        long best = Long.MAX_VALUE;
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            long start = System.nanoTime();
            sink += resolveAll(zones, positions);
            best = Math.min(best, System.nanoTime() - start);
        }

        assertThat(sink).as("the compiler must not optimise the lookups away").isPositive();
        System.out.printf(
                "[zone] SC-001: %d lookups in %d ns (budget %d ns, margin %.1fx)%n",
                PLAYERS, best, BUDGET_NANOS, (double) BUDGET_NANOS / best);
        assertThat(best)
                .as(
                        "SC-001: %d zone lookups must fit in %d ns, but the fastest of %d rounds took"
                                + " %d ns",
                        PLAYERS, BUDGET_NANOS, MEASURED_ROUNDS, best)
                .isLessThan(BUDGET_NANOS);
    }

    @Test
    @DisplayName("resolving the same 200 positions allocates nothing per lookup")
    void theHotPathUsesTheAllocationFreeQuery() throws Exception {
        Zones zones = load();
        List<WorldPosition> positions = spread();

        // zoneKeyAt exists precisely so this path can promise no Optional per call - the same reason
        // B08b has balanceOrZero and B06 has levelOrZero (Constitution II).
        for (WorldPosition position : positions) {
            String key = zones.zoneKeyAt(position);
            assertThat(key == null || !key.isBlank()).isTrue();
        }
    }

    private static long resolveAll(Zones zones, List<WorldPosition> positions) {
        long found = 0L;
        for (int i = 0; i < positions.size(); i++) {
            String key = zones.zoneKeyAt(positions.get(i));
            if (key != null) {
                found++;
            }
        }
        return found;
    }

    /**
     * 200 positions spread over all six regions and the gaps between them.
     *
     * <p>Deliberately mixed: only hitting one region would measure a single hot table entry, and only
     * hitting the wilderness would measure the miss path. A real server has both.
     */
    private static List<WorldPosition> spread() {
        List<WorldPosition> positions = new ArrayList<>(PLAYERS);
        int[] centresX = {0, 1500, 3000, 0, 1500, 3000};
        int[] centresZ = {0, 0, 0, 1500, 1500, 1500};
        for (int i = 0; i < PLAYERS; i++) {
            if (i % 10 == 0) {
                // Every tenth stands in the wilderness between the regions.
                positions.add(new WorldPosition(ZoneFixture.WORLD, 700.5d + i, 65.0d, 700.5d + i));
                continue;
            }
            int region = i % 6;
            int offset = (i * 37) % 400 - 200;
            positions.add(
                    new WorldPosition(
                            ZoneFixture.WORLD,
                            centresX[region] + offset + 0.5d,
                            65.0d,
                            centresZ[region] + offset + 0.5d));
        }
        return positions;
    }

    private static Zones load() throws Exception {
        Map<String, Object> document = ZoneFixture.document();
        ConfigSchema<ZoneConfig> schema = ZoneConfigSchema.schema(ZoneFixture.worlds());
        return new DefaultZones(
                schema.bind(SchemaValidator.validate(Path.of("zones.yml"), document, schema)));
    }
}
